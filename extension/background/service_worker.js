import { CryptoHelper } from '../crypto/crypto_helper.js';

// 后台服务：处理挑战发送、CF Worker 长轮询与局域网竞速
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === "REQUEST_2FA_APPROVAL") {
    handle2FAApproval(message.challengeDigits, message.account)
      .then(result => sendResponse(result))
      .catch(err => sendResponse({ success: false, error: err.message }));
    return true; // 异步响应
  }
});

async function handle2FAApproval(challengeDigits, accountHint) {
  const data = await chrome.storage.local.get([
    "github_account",
    "relay_url",
    "device_did",
    "device_secret"
  ]);

  const account = accountHint || data.github_account || "angusdevgo";
  const relayUrl = (data.relay_url || "https://mygithub-relay.workers.dev").replace(/\/$/, "");
  const deviceDid = data.device_did;
  const deviceSecret = data.device_secret;

  if (!deviceSecret || !deviceDid) {
    throw new Error("浏览器扩展尚未配对，请先点击扩展图标扫码配对！");
  }

  // 1. 生成唯一的会话 Session ID (UUID)
  const sessionId = "sess_" + Date.now() + "_" + Math.floor(Math.random() * 100000);

  // 2. 加密挑战内容 (AES-256-GCM)
  const plainPayload = JSON.stringify({
    digits: challengeDigits,
    client: "Chrome Extension",
    action: "login_or_sudo",
    time: Date.now()
  });

  const encrypted = await CryptoHelper.encrypt(plainPayload, deviceSecret);

  const envelope = {
    sessionId: sessionId,
    type: "challenge",
    from: deviceDid,
    ciphertext: encrypted.ciphertext,
    nonce: encrypted.nonce,
    sig: "dev_sig",
    ts: Date.now()
  };

  // 3. 【双轨并发竞速】
  // 轨道 A: 局域网探测尝试 (UDP/HTTP，超时300ms)
  const lanPromise = sendLanChallenge(envelope).catch(() => null);

  // 轨道 B: Cloudflare Worker 中继 (主要信道)
  const relayPromise = sendRelayChallenge(relayUrl, account, envelope);

  // 并发触发两个通道
  await Promise.race([
    relayPromise,
    new Promise(r => setTimeout(r, 200)) // 极速继续
  ]);

  // 4. 等待手机端批准回传 (通过 Relay Long Polling + WS 轮询)
  const approvalResponse = await waitForApproval(relayUrl, account, sessionId, deviceSecret);
  return approvalResponse;
}

async function sendRelayChallenge(relayUrl, account, envelope) {
  const url = `${relayUrl}/auth/challenge?account=${encodeURIComponent(account)}`;
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(envelope)
  });
  if (!resp.ok) {
    const errText = await resp.text();
    console.warn("Relay challenge notice:", resp.status, errText);
  }
}

async function sendLanChallenge(envelope) {
  // 本地局域网快速探针 (默认尝试本地常见端口 18337)
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 300);
  try {
    await fetch("http://127.0.0.1:18337/challenge", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(envelope),
      signal: controller.signal
    });
  } finally {
    clearTimeout(timeoutId);
  }
}

async function waitForApproval(relayUrl, account, sessionId, deviceSecret) {
  const startTime = Date.now();
  const maxWaitMs = 60 * 1000; // 最多等待 60 秒

  while (Date.now() - startTime < maxWaitMs) {
    try {
      const pollUrl = `${relayUrl}/auth/poll?account=${encodeURIComponent(account)}&sessionId=${encodeURIComponent(sessionId)}&wait=true`;
      const resp = await fetch(pollUrl);
      if (resp.ok) {
        const json = await resp.json();
        if (json.status === "approve" && json.envelope) {
          // 解密手机端发回的信封，拿到真实的 6 位 TOTP 动态码！
          const decryptedJson = await CryptoHelper.decrypt(
            json.envelope.ciphertext,
            json.envelope.nonce,
            deviceSecret
          );
          const approvedData = JSON.parse(decryptedJson);
          return {
            success: true,
            status: "approved",
            totpCode: approvedData.totpCode
          };
        } else if (json.status === "deny") {
          return {
            success: false,
            status: "denied",
            error: "用户在手机上拒绝了本次身份验证请求"
          };
        }
      }
    } catch (e) {
      console.warn("Poll exception, retrying...", e);
    }
    // 短暂避让
    await new Promise(r => setTimeout(r, 1200));
  }

  return { success: false, error: "等待手机确认超时，请重试或手动输入验证码" };
}
