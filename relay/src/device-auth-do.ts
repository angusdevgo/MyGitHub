/**
 * DeviceAuthDO — Durable Object：WS 房间 + 信封转发 + 限流 + TTL
 * 每个 GitHub 账号一个 DO 实例（有状态隔离）
 */

interface Envelope {
  sessionId: string;      // UUIDv7（客户端生成）
  type: "challenge" | "approve" | "deny";
  from: string;           // 发送方设备 DID 公钥
  ciphertext: string;     // AES-256-GCM 密文（base64）
  nonce: string;          // GCM IV
  sig: string;            // Ed25519 签名
  ts: number;
}

interface Session {
  sessionId: string;
  envelope: Envelope | null;   // 挑战信封（等待批准）
  response: Envelope | null;   // 批准/拒绝信封
  createdAt: number;
}

export class DeviceAuthDO {
  state: DurableObjectState;
  sessions = new Map<string, Session>();
  // 限流：每账号 10 挑战/小时
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
      // 保活
      server.addEventListener("message", (evt) => {
        try {
          const data = JSON.parse(evt.data as string);
          if (data.type === "ping") server.send(JSON.stringify({ type: "pong" }));
        } catch { /* ignore */ }
      });
      return new Response(null, { status: 101, webSocket: client });
    }

    // ---- 信封端点 ----
    switch (url.pathname) {
      case "/auth/challenge": {
        // 限流检查
        const now = Date.now();
        this.challengeTimestamps = this.challengeTimestamps.filter(t => now - t < 3600_000);
        if (this.challengeTimestamps.length >= 10) {
          return json({ error: "rate_limited" }, 429);
        }
        this.challengeTimestamps.push(now);

        const envelope = await request.json<Envelope>();
        if (envelope.type !== "challenge") return json({ error: "bad_type" }, 400);
        const session: Session = { sessionId: envelope.sessionId, envelope, response: null, createdAt: now };
        this.sessions.set(envelope.sessionId, session);

        // WS 实时推送所有已连接设备（含信封原文，密文端侧解）
        this.broadcast({ kind: "challenge", envelope });
        return json({ ok: true, sessionId: envelope.sessionId });
      }

      case "/auth/approve": {
        const envelope = await request.json<Envelope>();
        const session = this.sessions.get(envelope.sessionId);
        if (!session) return json({ error: "no_session" }, 404);
        if (session.response) return json({ error: "already_answered" }, 409);

        session.response = envelope;
        this.broadcast({ kind: envelope.type, envelope });
        return json({ ok: true });
      }

      case "/auth/poll": {
        const sessionId = url.searchParams.get("sessionId")!;
        const session = this.sessions.get(sessionId);
        if (!session) return json({ status: "unknown" });
        if (session.response) {
          return json({ status: session.response.type, envelope: session.response });
        }
        return json({ status: "pending" });
      }

      case "/trust": {
        // 公钥信任圈注册（仅公钥，无私密数据）
        const body = await request.json<{ accountId: string; devicePubKey: string; grant?: string }>();
        // 存 DO storage（简化版；生产建议 D1）
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
      try { ws.send(text); } catch { this.sockets.delete(ws); }
    }
  }
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });
}
