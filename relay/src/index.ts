/**
 * MyGitHub 跨设备 2FA 盲眼中继
 * Worker 职责（严格限定）：
 *   1. WS 频道管理（按 accountId 分房间）
 *   2. 加密信封（envelope）转发 — 服务器无法解密、无法伪造（Ed25519 签名在端侧验证）
 *   3. 限流防滥用（每账号 15 挑战/小时）
 *   4. 会话 TTL 5 分钟自动清理
 *   5. 全局 CORS 支持（允许浏览器扩展与任何前端发起跨端调用）
 */

import { DeviceAuthDO } from "./device-auth-do";

export { DeviceAuthDO };

export interface Env {
  DEVICE_AUTH: DurableObjectNamespace;
}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: CORS_HEADERS,
      });
    }

    const url = new URL(request.url);

    // 健康检查/根探针
    if (url.pathname === "/" || url.pathname === "/health") {
      return json({
        service: "MyGitHub Blind Relay",
        status: "ok",
        version: "1.0.0",
        timestamp: Date.now()
      });
    }

    // WS 升级：/ws?account=xxx
    if (request.headers.get("Upgrade") === "websocket") {
      const account = url.searchParams.get("account");
      if (!account) return new Response("missing account parameter", { status: 400 });
      const id = env.DEVICE_AUTH.idFromName(account);
      const stub = env.DEVICE_AUTH.get(id);
      return stub.fetch(request);
    }

    const account = url.searchParams.get("account");
    if (!account) return json({ error: "missing account parameter" }, 400);
    const stub = env.DEVICE_AUTH.get(env.DEVICE_AUTH.idFromName(account));

    // 信封转发端点（转由 DO 处理）
    const response = await stub.fetch(request);
    // 注入 CORS 头部
    const newHeaders = new Headers(response.headers);
    Object.entries(CORS_HEADERS).forEach(([k, v]) => newHeaders.set(k, v));
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: newHeaders,
    });
  },
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json",
      ...CORS_HEADERS,
    },
  });
}
