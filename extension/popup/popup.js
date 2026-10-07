import { CryptoHelper } from '../crypto/crypto_helper.js';

document.addEventListener("DOMContentLoaded", async () => {
  const accountInput = document.getElementById("account-input");
  const relayInput = document.getElementById("relay-input");
  const phoneIpInput = document.getElementById("phone-ip-input");
  const saveBtn = document.getElementById("save-btn");
  const resetBtn = document.getElementById("reset-btn");
  const pairedConfirmBtn = document.getElementById("paired-confirm-btn");
  const testCallBtn = document.getElementById("test-call-btn");
  const statusBadge = document.getElementById("status-badge");
  const qrDiv = document.getElementById("qrcode");

  // 读取已保存设置
  const data = await chrome.storage.local.get([
    "github_account",
    "relay_url",
    "phone_ip",
    "device_did",
    "device_secret",
    "is_paired"
  ]);

  let account = data.github_account || "angusdevgo";
  let relayUrl = data.relay_url || "https://mygithub-relay.workers.dev";
  let phoneIp = data.phone_ip || "";
  let deviceDid = data.device_did;
  let deviceSecret = data.device_secret;

  accountInput.value = account;
  relayInput.value = relayUrl;
  phoneIpInput.value = phoneIp;

  function updateStatus(isPaired) {
    if (isPaired) {
      statusBadge.textContent = "已就绪";
      statusBadge.className = "badge";
      pairedConfirmBtn.textContent = "✓ 已标记配对 (点击可重新配对)";
      pairedConfirmBtn.className = "secondary";
    } else {
      statusBadge.textContent = "未配对";
      statusBadge.className = "badge disconnected";
      pairedConfirmBtn.textContent = "手机已扫码？点击确认已配对";
      pairedConfirmBtn.className = "primary";
    }
  }

  updateStatus(data.is_paired);

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

  // 1. 确认配对按钮
  pairedConfirmBtn.addEventListener("click", async () => {
    const nextState = !data.is_paired;
    data.is_paired = nextState;
    await chrome.storage.local.set({ is_paired: nextState });
    updateStatus(nextState);
    if (nextState) {
      alert("已标记为已配对！现在你可以点击下方【测试向手机发送验证弹窗】验证通信。");
    }
  });

  // 2. 测试呼叫手机
  testCallBtn.addEventListener("click", () => {
    const testDigits = String(Math.floor(10 + Math.random() * 90));
    testCallBtn.disabled = true;
    testCallBtn.textContent = `⏳ 正在呼叫手机 (数字: ${testDigits})...`;

    chrome.runtime.sendMessage(
      {
        type: "REQUEST_2FA_APPROVAL",
        challengeDigits: testDigits,
        account: account
      },
      (res) => {
        testCallBtn.disabled = false;
        testCallBtn.textContent = "🚀 测试向手机发送验证弹窗";
        if (res && res.success) {
          alert(`🎉 手机已成功批准！回传的动态码: ${res.totpCode}`);
        } else {
          alert(`呼叫结果: ${res ? (res.error || res.status) : "无响应，请确认手机已打开 MyGitHub 并在前台"}`);
        }
      }
    );
  });

  // 3. 保存设置
  saveBtn.addEventListener("click", async () => {
    account = accountInput.value.trim() || "angusdevgo";
    relayUrl = relayInput.value.trim() || "https://mygithub-relay.workers.dev";
    phoneIp = phoneIpInput.value.trim();
    await chrome.storage.local.set({
      github_account: account,
      relay_url: relayUrl,
      phone_ip: phoneIp
    });
    await renderQR();
    alert("设置已保存！");
  });

  // 4. 重置密钥
  resetBtn.addEventListener("click", async () => {
    if (confirm("确定重新生成设备密钥？需要手机重新扫码。")) {
      deviceDid = null;
      deviceSecret = null;
      await chrome.storage.local.remove(["device_did", "device_secret", "is_paired"]);
      data.is_paired = false;
      updateStatus(false);
      await renderQR();
    }
  });
});
