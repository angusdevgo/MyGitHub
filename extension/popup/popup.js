import { CryptoHelper } from '../crypto/crypto_helper.js';

document.addEventListener("DOMContentLoaded", async () => {
  const accountInput = document.getElementById("account-input");
  const relayInput = document.getElementById("relay-input");
  const saveBtn = document.getElementById("save-btn");
  const resetBtn = document.getElementById("reset-btn");
  const statusBadge = document.getElementById("status-badge");
  const qrDiv = document.getElementById("qrcode");

  // 读取已保存设置
  const data = await chrome.storage.local.get([
    "github_account",
    "relay_url",
    "device_did",
    "device_secret",
    "is_paired"
  ]);

  let account = data.github_account || "angusdevgo";
  let relayUrl = data.relay_url || "https://mygithub-relay.workers.dev";
  let deviceDid = data.device_did;
  let deviceSecret = data.device_secret;

  accountInput.value = account;
  relayInput.value = relayUrl;

  if (data.is_paired) {
    statusBadge.textContent = "已就绪";
    statusBadge.className = "badge";
  }

  // 若没有密钥则自动生成
  async function ensureKeys() {
    if (!deviceDid || !deviceSecret) {
      deviceDid = await CryptoHelper.generateDeviceDID();
      const secretBytes = crypto.getRandomValues(new Uint8Array(24));
      deviceSecret = CryptoHelper.bufferToBase64(secretBytes);
      await chrome.storage.local.set({
        device_did: deviceDid,
        device_secret: deviceSecret
      });
    }
  }

  async function renderQR() {
    await ensureKeys();
    // 构造跨设备配对 URI (包含 DID, 密钥口令与中继地址)
    const pairPayload = {
      type: "mygithub_2fa_pair",
      v: 1,
      account: account,
      did: deviceDid,
      secret: deviceSecret,
      relay: relayUrl,
      clientName: "Chrome on PC"
    };

    const qrText = JSON.stringify(pairPayload);
    qrDiv.innerHTML = "";
    if (window.QRCode) {
      new QRCode(qrDiv, {
        text: qrText,
        width: 200,
        height: 200,
        colorDark: "#000000",
        colorLight: "#ffffff",
        correctLevel: QRCode.CorrectLevel.M
      });
    }
  }

  await renderQR();

  saveBtn.addEventListener("click", async () => {
    account = accountInput.value.trim() || "angusdevgo";
    relayUrl = relayInput.value.trim() || "https://mygithub-relay.workers.dev";
    await chrome.storage.local.set({
      github_account: account,
      relay_url: relayUrl
    });
    await renderQR();
    alert("配置已更新！请用手机重新扫描二维码配对。");
  });

  resetBtn.addEventListener("click", async () => {
    if (confirm("确定重新生成设备密钥？重新生成后需要手机重新扫码配对。")) {
      deviceDid = null;
      deviceSecret = null;
      await chrome.storage.local.remove(["device_did", "device_secret", "is_paired"]);
      statusBadge.textContent = "未配对";
      statusBadge.className = "badge disconnected";
      await renderQR();
    }
  });
});
