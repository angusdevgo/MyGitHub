// 页面内容拦截器：捕获 GitHub 2FA 页面并注入无缝数字确认弹窗

(function () {
  console.log("[MyGitHub] 2FA 拦截脚本已就绪");

  // 检测是否存在 TOTP 输入框
  function findTotpInput() {
    return document.querySelector("input#app_totp") ||
           document.querySelector("input#otp") ||
           document.querySelector("input[name='app_totp']") ||
           document.querySelector("input[name='otp']") ||
           document.querySelector("input[autocomplete='one-time-code']");
  }

  function initInterceptor() {
    const totpInput = findTotpInput();
    if (!totpInput) return;

    // 避免重复挂载
    if (document.getElementById("mygithub-2fa-card")) return;

    // 提取当前登录用户名 (若页面有的话)
    let accountName = "GitHub User";
    const userElem = document.querySelector(".header-nav-current-user strong") ||
                     document.querySelector("[data-login]");
    if (userElem) accountName = userElem.textContent.trim();

    // 随机生成 2 位数字确认码 (10 - 99)
    const challengeDigits = String(Math.floor(10 + Math.random() * 90));

    // 创建注入浮层
    injectModal(challengeDigits, accountName, totpInput);
  }

  function injectModal(digits, account, inputElement) {
    const card = document.createElement("div");
    card.id = "mygithub-2fa-card";
    card.innerHTML = `
      <div class="mygithub-card-inner">
        <div class="mygithub-badge">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
            <path d="M4 4a4 4 0 0 1 8 0v2h.25c.966 0 1.75.784 1.75 1.75v5.5A1.75 1.75 0 0 1 12.25 15h-8.5A1.75 1.75 0 0 1 2 13.25v-5.5C2 6.784 2.784 6 3.75 6H4V4zm2 2h4V4a2 2 0 1 0-4 0v2z"/>
          </svg>
          MyGitHub 跨设备安全验证
        </div>
        <div class="mygithub-title">已向你的主力手机发送确认请求</div>
        <div class="mygithub-sub">在手机 MyGitHub 弹窗中输入下方数字以完成批准：</div>
        <div class="mygithub-digits">${digits}</div>
        <div class="mygithub-status" id="mygithub-status-text">
          <span class="mygithub-spinner"></span> 正在等待手机端输入并批准...
        </div>
        <div class="mygithub-fallback">
          <a href="javascript:void(0)" id="mygithub-manual-toggle">切换为手动输入验证码</a>
        </div>
      </div>
    `;

    document.body.appendChild(card);

    document.getElementById("mygithub-manual-toggle").addEventListener("click", () => {
      card.remove();
      inputElement.focus();
    });

    // 发送消息到 Background 触发跨端通知
    chrome.runtime.sendMessage(
      {
        type: "REQUEST_2FA_APPROVAL",
        challengeDigits: digits,
        account: account
      },
      (response) => {
        const statusText = document.getElementById("mygithub-status-text");
        if (response && response.success && response.totpCode) {
          if (statusText) {
            statusText.innerHTML = `<span style="color:#2ea043;font-weight:bold;">✓ 手机已批准！正在自动登录...</span>`;
          }
          // 自动填充动态码并提交
          inputElement.value = response.totpCode;
          inputElement.dispatchEvent(new Event("input", { bubbles: true }));
          inputElement.dispatchEvent(new Event("change", { bubbles: true }));

          // 自动提交表单
          setTimeout(() => {
            const form = inputElement.closest("form");
            if (form) {
              const submitBtn = form.querySelector("button[type='submit']") || form.querySelector("input[type='submit']");
              if (submitBtn) submitBtn.click();
              else form.submit();
            }
          }, 300);
        } else {
          if (statusText) {
            statusText.innerHTML = `<span style="color:#da3633;">✕ ${response ? response.error : "验证失败或已拒绝"}</span>`;
          }
        }
      }
    );
  }

  // 观察页面 DOM 变化 (适配 PJAX / Turbo)
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initInterceptor);
  } else {
    initInterceptor();
  }

  const observer = new MutationObserver(() => {
    initInterceptor();
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
