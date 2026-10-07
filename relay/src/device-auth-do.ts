/**
 * DeviceAuthDO — Durable Object：WS 房间 + 信封转发 + 限流 + TTL + Long Polling
 * 每个 GitHub 账号一个 DO 实例（有状态隔离）
 */

interface Envelope {
  sessionId: string;      // UUIDv7 / 客户端生成
  type: "challenge" | "approve" | "deny";
  from: string;           // 发送方设备 DID 公钥
  to?: string;            // 目标设备公钥 (可选)
  ciphertext: string;     // AES-256-GCM 密文（base64）
  nonce: string;          // GCM IV (base64)
  sig: string;            // Ed25519 签名 (base64)
  ts: number;
}

interface Session {
  sessionId: string;
  envelope: Envelope | null;   // 挑战信封（等待批准）
  response: Envelope | null;   // 批准/拒绝信封
  createdAt: number;
  pollWaiters: ((env: Envelope | null) => void)[];
}

export class DeviceAuthDO {
  state: DurableObjectState;
  sessions = new Map<string, Session>();
  // 限流：每账号 20 挑战/小时
  challengeTimestamps: number[] = [];
  // WS 客户端池
  sockets = new Set<WebSocket>();

  constructor(state: DurableObjectState, _env: unknown) {
    this.state = state;
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    // ---- WS 升级 ----
    if (request.headers.get("Upgrade") === "websocket") {
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);
      server.accept();
      this.sockets.add(server);
      server.addEventListener("close", () => this.sockets.delete(server));
      // 心跳与保活
      server.addEventListener("message", (evt) => {
        try {
          const data = JSON.parse(evt.data as string);
          if (data.type === "ping") {
            server.send(JSON.stringify({ type: "pong", ts: Date.now() }));
          }
        } catch { /* ignore */ }
      });
      return new Response(null, { status: 101, webSocket: client });
    }

    // ---- 信封端点 ----
    switch (url.pathname) {
      case "/auth/challenge": {
        const now = Date.now();
        // 1 小时限流清理与判断
        this.challengeTimestamps = this.challengeTimestamps.filter(t => now - t < 3600_000);
        if (this.challengeTimestamps.length >= 20) {
          return json({ error: "rate_limited", message: "每小时挑战次数已达上限" }, 429);
        }
        this.challengeTimestamps.push(now);

        const envelope = await request.json<Envelope>();
        if (envelope.type !== "challenge") return json({ error: "bad_type" }, 400);

        const session: Session = {
          sessionId: envelope.sessionId,
          envelope,
          response: null,
          createdAt: now,
          pollWaiters: []
        };
        this.sessions.set(envelope.sessionId, session);

        // WS 实时广播至已在线设备
        this.broadcast({ kind: "challenge", envelope });
        return json({ ok: true, sessionId: envelope.sessionId });
      }

      case "/auth/approve":
      case "/auth/deny": {
        const envelope = await request.json<Envelope>();
        const session = this.sessions.get(envelope.sessionId);
        if (!session) return json({ error: "no_session", message: "会话已过期或不存在" }, 404);
        if (session.response) return json({ error: "already_answered", message: "该请求已处理" }, 409);

        session.response = envelope;

        // 唤醒所有正在长轮询该 sessionId 的 HTTP 请求
        session.pollWaiters.forEach(waiter => waiter(envelope));
        session.pollWaiters = [];

        // WS 广播响应
        this.broadcast({ kind: envelope.type, envelope });
        return json({ ok: true });
      }

      case "/auth/poll": {
        const sessionId = url.searchParams.get("sessionId");
        if (!sessionId) return json({ error: "missing_session_id" }, 400);

        const wait = url.searchParams.get("wait") === "true";
        const session = this.sessions.get(sessionId);
        if (!session) return json({ status: "unknown" });

        if (session.response) {
          return json({ status: session.response.type, envelope: session.response });
        }

        // 支持最长 25 秒的 Long Polling（挂起等待手机回复）
        if (wait) {
          const envelope = await new Promise<Envelope | null>((resolve) => {
            const timeout = setTimeout(() => {
              const idx = session.pollWaiters.indexOf(resolve);
              if (idx !== -1) session.pollWaiters.splice(idx, 1);
              resolve(null);
            }, 25_000);

            session.pollWaiters.push((resp) => {
              clearTimeout(timeout);
              resolve(resp);
            });
          });

          if (envelope) {
            return json({ status: envelope.type, envelope });
          }
        }

        return json({ status: "pending" });
      }

      case "/trust": {
        // 公钥信任圈注册
        const body = await request.json<{ accountId: string; devicePubKey: string; grant?: string }>();
        await this.state.storage.put(`trust:${body.devicePubKey}`, {
          pubKey: body.devicePubKey,
          grant: body.grant ?? null,
          at: Date.now(),
        });
        return json({ ok: true });
      }

      default:
        return json({ error: "not_found" }, 404);
    }
  }

  private broadcast(msg: unknown) {
    const text = JSON.stringify(msg);
    for (const ws of this.sockets) {
      try {
        ws.send(text);
      } catch {
        this.sockets.delete(ws);
      }
    }
  }
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });
}
