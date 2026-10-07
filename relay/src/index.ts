/**
 * MyGitHub 跨设备 2FA 盲眼中继
 * Worker 职责（严格限定）：
 *   1. WS 频道管理（按 accountId 分房间）
 *   2. 加密信封（envelope）转发 — 服务器无法解密、无法伪造（Ed25519 签名在端侧验证）
 *   3. 限流防滥用（每账号 10 挑战/小时）
 *   4. 会话 TTL 5 分钟自动清理
 * Worker 不做：任何业务逻辑、密文解析、token 存储
 */

import { DeviceAuthDO } from "./device-auth-do";

export { DeviceAuthDO };

export interface Env {
  DEVICE_AUTH: DurableObjectNamespace;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // WS 升级：/ws?account=xxx
    if (request.headers.get("Upgrade") === "websocket") {
      const account = url.searchParams.get("account");
      if (!account) return new Response("missing account", { status: 400 });
      const id = env.DEVICE_AUTH.idFromName(account);
      const stub = env.DEVICE_AUTH.get(id);
      return stub.fetch(request);
    }

    const account = url.searchParams.get("account");
    if (!account) return json({ error: "missing account" }, 400);
    const stub = env.DEVICE_AUTH.get(env.DEVICE_AUTH.idFromName(account));

    // 信封转发端点（全部直接转给 DO 处理，Worker 零逻辑）
    return stub.fetch(request);
  },
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });
}
