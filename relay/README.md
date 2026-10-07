# MyGitHub Relay — 跨设备 2FA 盲眼中继

## 这是什么？

MyGitHub App 的可选跨设备登录验证中继。**默认不需要部署**——只有当你想启用「新设备登录需老设备数字匹配批准」功能时才需要。

## 安全模型（为什么它被攻破也不影响你）

- **盲眼中继**：Worker 只转发 AES-256-GCM 加密信封，无法解密内容
- **端侧签名**：批准动作用 Ed25519 私钥签名（私钥在手机 StrongBox 硬件安全区），服务器无法伪造
- **数字匹配**：两位数挑战的校验发生在新设备本地，中继不知道正确答案
- **零密钥存储**：Worker 不存任何 token、密钥或明文

## 部署（约 2 分钟，免费）

1. 注册/登录 [Cloudflare](https://dash.cloudflare.com)（免费版即可）
2. 本目录执行：
   ```bash
   npm install
   npx wrangler login     # 浏览器授权
   npx wrangler deploy
   ```
3. 部署完成后获得地址：`https://mygithub-relay.<你的子域>.workers.dev`
4. 打开 MyGitHub App → 我的 → 安全中心 → 跨设备验证 → 填入该地址

## 免费额度

- 100,000 请求/天。每次跨设备验证仅消耗 2-3 个请求，个人使用绰绰有余。
- WebSocket 长连接由 Durable Objects 承接（免费版支持）。

## 审计

全部逻辑仅两个文件（`src/index.ts` + `src/device-auth-do.ts`，约 200 行），欢迎社区审计。
