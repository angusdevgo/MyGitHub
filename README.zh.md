<h1 align="center">MyGitHub</h1>

<div align="center">

**中文** | [English](README.md)

<img src="docs/images/theme.png" alt="MyGitHub" width="500" />

<p><strong>一个完全跑在你手机上的 GitHub 客户端</strong></p>
<p>Android · 零服务器 · 本地优先 · TOTP · Jetpack Compose</p>

[![Version](https://img.shields.io/badge/version-v0.0.1-3B5BDB?style=flat-square)](https://github.com/angusdevgo/MyGitHub/releases)
[![Last Commit](https://img.shields.io/github/last-commit/angusdevgo/MyGitHub?style=flat-square)](https://github.com/angusdevgo/MyGitHub/commits)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)
[![Stars](https://img.shields.io/github/stars/angusdevgo/MyGitHub?style=flat-square&color=EBCB8B)](https://github.com/angusdevgo/MyGitHub/stargazers)

[![Platform](https://img.shields.io/badge/android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/jetpack%20compose-material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![社区](https://img.shields.io/badge/%E7%A4%BE%E5%8C%BA-LINUX%20DO-23272A?style=flat-square)](https://linux.do/)

</div>

---

MyGitHub 是一个原生 Android 的 GitHub 客户端，使用 Kotlin 与 Jetpack Compose 编写。
它做的是你在手机上真正会打开 GitHub 去做的那些事——浏览仓库、看 Issue、查通知、追趋势——而且它做这些事时，**除了 GitHub 自己的服务器，不会和任何其他服务器通信**。

整个项目约 7000 行 Kotlin，27 个文件。小到你可以用一个下午读完，确切知道它拿你的凭据做了什么。

---

## 这是什么

最后那句话是重点，所以讲具体一点：

- **没有后端。** 不是免费额度，不是 Serverless，不是反向代理。没有东西要部署，没有东西要付费，没有东西会挂掉。
- **你的令牌留在设备上。** AES-256-GCM 加密，密钥由 Android Keystore 保管。
- **每一次请求都经 TLS 直达 `api.github.com`。** 你可以读源码验证，也可以抓自己的网络包验证。
- **它自带 2FA 验证器。** RFC 6238 TOTP，离线计算。你可以把 Google Authenticator 删了。

整个项目约 7000 行 Kotlin，27 个文件。小到你可以用一个下午读完，确切知道它拿你的凭据做了什么。

---

## 为什么你可能会想要它

**你不想为客户端付费。** 大多数第三方 GitHub 客户端都需要一个后端，因为 OAuth 的 Web Flow 在换取令牌时要求 `client_secret`。把这个密钥打进 APK，任何反编译的人都能冒充你的应用。所以那些项目选择跑一台服务器——然后由你来买单。

MyGitHub 的做法是：**根本不支持 Web Flow**。它只做 **Device Flow**（不需要任何密钥）和 **PAT 登录**（什么都不需要）。这一个约束直接消灭了整个后端需求。

**你想要一个不会弄丢密钥的验证器。** 内置的 TOTP 引擎可以导入与 Google Authenticator 相同的密钥。它能扛过退出登录、应用更新和进程被杀——因为它存在普通的应用私有文件里并采用同步写入，而不是存在某些 OEM 系统上会静默复位的脆弱 Keystore 封装存储里。

**你受够了卡顿的列表。** 缓存优先架构：每个列表先从本地渲染，再在你背后静默刷新。在小米 2206123SC 上实测卡顿率：个人页 **0.35%**，通知页 **0.67%**。通知流零次 vsync 丢失。

**你想先读代码再决定是否信任。** 27 个文件。没有混淆的 SDK，没有统计埋点，没有你没要求的网络请求。搜一下 `api.github.com`——那就是这个应用全部的对外请求。

---

## 它能做什么

<table>
<tr><td width="50%" valign="top">

**浏览**

- 个性化推荐流——基于你的 Star 在端侧推断
- 趋势排行榜（日 / 周 / 月）与语言筛选
- 你的仓库列表，含 Star、Fork、语言与最近活跃
- 仓库与 Issue 搜索

</td><td width="50%" valign="top">

**交互**

- 阅读完整 Issue 讨论串与评论
- 发表评论
- 关闭与重新打开 Issue
- Star 与取消 Star
- 带明暗主题适配的 README 渲染

</td></tr>
<tr><td valign="top">

**保持同步**

- 完整的通知收件箱与原因筛选
- **准确的 Issue 状态**——从真实 API 查询，而非从通知文本猜测
- 聚合动态：谁 Star、Fork 或在你仓库开了 Issue
- 标记已读并同步到 GitHub
- 保留 30 天

</td><td valign="top">

**保持安全**

- 内置 RFC 6238 TOTP 验证器
- 支持扫码导入（CameraX + ML Kit）或手动粘贴 Base32 / `otpauth://`
- 实时倒计时，一键复制
- 退出登录不受影响——2FA 绑定永不被清除

</td></tr>
</table>

---

## 快速开始

### 环境要求

- JDK 17
- Android SDK（API 34）
- Android 8.0+ 设备（API 26）

### 构建

```bash
git clone https://github.com/angusdevgo/MyGitHub.git
cd MyGitHub

# 指向你的 SDK
echo "sdk.dir=/你的/Android/Sdk/路径" > local.properties

# 构建
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 构建签名 Release

签名凭据**绝不**提交到本仓库。写入 `local.properties`：

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=你的口令
RELEASE_KEY_ALIAS=你的别名
RELEASE_KEY_PASSWORD=你的密钥口令
```

没有密钥库就先生成一个，然后构建：

```bash
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias your_alias -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease
```

> 完全跳过这一步也可以——Gradle 会产出**未签名**的 release APK 而不是直接失败。任何人都能在干净的机器上编译本项目。

---

## 登录

MyGitHub 提供两条登录路径。二者都确保**任何密钥都不会被打进 APK**。

### Device Flow —— 推荐

这与 `gh auth login` 和智能电视用的是同一套机制。

```
1.  点击「使用 GitHub 账号登录」
2.  应用显示一串验证码：9D6F-56E7
3.  用任意浏览器打开 github.com/login/device
4.  输入验证码，点击授权
5.  应用自动检测并完成登录
```

你的密码输入在 GitHub 自己的网页上，应用全程看不到。整个流程唯一涉及的凭据是一个**公开的客户端 ID**——反编译 APK 也拿不到任何可用东西。

### Personal Access Token —— 后备方案

某些网络封锁了 `github.com` 但放行 `api.github.com`，Device Flow 在那里无法工作，因此提供 PAT 登录作为后备。

应用会提供引导，并打开 GitHub 令牌页且**已预选 `repo`、`user`、`read:org` 三项权限**。

### 启用 2FA

登录之后，MyGitHub 还能充当你的验证器：

1. 进入 **我的 → 安全中心**
2. 扫描 GitHub 显示的二维码，或粘贴 Base32 设置密钥
3. 6 位动态码立即出现，带实时倒计时环

动态码在本地按 RFC 6238 用 HMAC-SHA1 计算。密钥存放于应用私有存储，**退出登录时不会被动到**——退出只清除会话令牌。

---

## 技术实现

```
┌─────────────────────────────────────────────────────┐
│  Compose UI                                         │
│  首页 · 排行榜 · 信息 · 仓库 · 我的                   │
│  仓库详情 · Issue 详情 · 安全中心 · 登录              │
└────────────────────────┬────────────────────────────┘
                         │  Flow<Result<T>>
┌────────────────────────▼────────────────────────────┐
│  GitHubRepository                                   │
│  · 缓存优先 Flow（先 Room，再网络）                   │
│  · 并行加载器（supervisorScope + async）             │
│  · 内存缓存（用户名、Issue 状态）                     │
└───────┬──────────────────────────────┬──────────────┘
        │                              │
┌───────▼──────────┐      ┌────────────▼──────────────┐
│  Retrofit/OkHttp │      │  Room + Keystore          │
│  → api.github.com│      │  repo_cache · notifications│
│    经 TLS 直连    │      │  star_tags · recent_views  │
└──────────────────┘      │  EncryptedSharedPreferences│
                          └───────────────────────────┘
        ┌──────────────────────────────────┐
        │  TOTP（端侧离线）                 │
        │  RFC 6238 · Base32 · ML Kit 扫码  │
        └──────────────────────────────────┘
```

### 值得解释的设计决策

**缓存优先 Flow，而非 suspend 函数。**
每个列表发射两次：一次来自 Room（`fromCache = true`），一次来自网络。你在 100 毫秒内看到内容。刷新静默落地，且只在 ID 集合真正变化时才二次发射——列表永不跳动。

**底栏指示器用 `graphicsLayer { translationX }`。**
用 `animateDpAsState` 驱动偏移会在每一帧触发布局。在 `graphicsLayer` 内驱动 `translationX`，动画全程停留在 GPU，零重组。

**TOTP 时钟放在叶子组件里。**
把 1 Hz 定时器放在页面根部，会让整页每分钟重组 60 次。放进 `TotpQuickCard` 内部，就只有那张卡片重组。

**Issue 状态是查出来的，不是猜出来的。**
GitHub 的通知负载不包含 Issue 状态。应用不去解析标题里有没有 "closed" 这个词，而是对可见通知查询真实 Issue 接口，并通过内存缓存合并结果。你的通知列表显示的 `Open` / `Closed` 是准确的——包括你五分钟前刚关掉的那个。

**2FA 密钥使用普通应用私有存储。**
早期版本用了 `EncryptedSharedPreferences`。在某些 OEM 系统上，Keystore 复位会导致它静默返回 `null`——用户因此丢失 2FA 绑定并被锁在账号外。现在密钥存放在标准的应用私有文件中，使用 `commit()` 同步写入。应用私有存储本身已被系统沙箱隔离，额外的加密层只增加了脆弱性，没有带来实质性保护。

**聚合使用 `supervisorScope`。**
动态流会跨仓库、跨接口扇出。`supervisorScope` 隔离失败，单个仓库不可达不会导致整个动态流空白。

**滚动顺畅，是因为严格节制了重组。**
每个懒加载项都有稳定 key，Compose 复用组合结果而非重建行。时间戳走 `remember(updatedAt) { relativeTimeFromIso(updatedAt) }`，而不是每次重组都重新解析 ISO 字符串。筛选列表使用 `derivedStateOf`，只在其输入变化时重算。1 Hz 的 TOTP 倒计时被限制在叶子组件内，而不是让整页跟着跳动。Coil 全局缓存图片（50 MB 内存 + 100 MB 磁盘），配合 `crossfade(false)` 与显式 `memoryCacheKey`，消除滚动途中的解码尖刺。

---

## 项目结构

```
MyGitHub/
├── app/src/main/java/com/mygithub/lab/
│   ├── MainActivity.kt              入口
│   ├── data/
│   │   ├── api/GitHubApi.kt         Retrofit 接口 + DTO
│   │   ├── auth/                    Device Flow、加密令牌保险库
│   │   ├── local/AppDatabase.kt     Room 实体与 DAO
│   │   └── repo/GitHubRepository.kt 缓存优先 Flow、并行加载器
│   ├── security/totp/               RFC 6238 引擎、密钥持久化
│   └── ui/
│       ├── MyGitHubApp.kt           NavHost + 叠加式脚手架
│       ├── components/              设计系统基元
│       ├── screens/                 每个页面一个包
│       └── theme/                   源自 Nord 的配色方案
│
├── relay/                           可选的 Cloudflare Worker（见下）
└── docs/images/                     项目素材
```

### 可选的中继服务

`relay/` 里有一个 Cloudflare Worker，实现**盲眼**跨设备 2FA 批准——你在一台已受信任的设备上点一下两位数，即可批准另一台设备的登录。

**应用里没有任何功能依赖它。** 它是一次尝试：在没有可信服务器的前提下完成 2FA 批准。

- Worker 转发它自己无法解密的 AES-256-GCM 信封
- 批准动作由设备安全元件中的 Ed25519 密钥签名
- 挑战在请求方设备本地校验
- Worker 不存储任何令牌、密钥或明文

感兴趣的话两分钟就能部署——见 [`relay/README.md`](relay/README.md)。

---

## 路线图

- [x] 仓库浏览与主题化 README 渲染
- [x] Issue 详情、评论、关闭 / 重开
- [x] 端侧个性化推荐
- [x] 趋势排行榜
- [x] 带准确 Issue 状态的通知
- [x] 聚合式「收到的动态」
- [x] 内置 2FA 验证器与扫码导入
- [x] 悬浮胶囊导航
- [ ] 代码浏览与语法高亮
- [ ] Pull Request 评审
- [ ] 多账号支持
- [ ] Material You 动态取色

---

## 常见问题

**需要任何服务器或订阅吗？**
不需要。没有后端。你的令牌直接向 GitHub 鉴权每一次请求。

**我的令牌安全吗？**
它由 Android Keystore 中的密钥以 AES-256-GCM 加密，存放于 `EncryptedSharedPreferences`，除了 `api.github.com` 之外不发送到任何地方。

**不开代理能用吗？**
只要能访问 `github.com`，Device Flow 就能用。如果你的网络封锁了 `github.com`，请用 PAT 登录——它只需要 `api.github.com`。连接失败时应用会提示该走哪条路。

**退出登录会丢 2FA 动态码吗？**
不会。验证器密钥与你的会话分开存储，只有你在安全中心主动解绑时才会被清除。

**为什么不用 OAuth Web Flow？**
它在换取令牌时要求 `client_secret`，这意味着要么把密钥打进 APK（不安全），要么跑一个后端（要花钱）。Device Flow 和 PAT 登录同时避开了这两个问题。

**能用于 GitHub Enterprise 吗？**
目前不能。API 根地址硬编码为 `api.github.com`。改动量不大——欢迎 PR。

---

## 参与贡献

欢迎提交 Issue 与 Pull Request。

```bash
git checkout -b feature/your-feature
# 修改代码
./gradlew assembleRelease    # 应当干净构建
git commit -m "feat: your feature"
git push origin feature/your-feature
```

提交 PR 前请确认：

- `./gradlew assembleRelease` 构建通过且无新增警告
- 差异中不包含任何凭据、令牌、密钥库或个人信息
- 新增 UI 同时适配深色与亮色主题

---

## 免责声明

本项目供**个人使用与学习**。

- 与 GitHub, Inc. 无任何隶属、背书或赞助关系
- 仅使用公开文档化的 GitHub REST API 接口
- 你有责任遵守 [GitHub 服务条款](https://docs.github.com/zh/site-policy/github-terms/github-terms-of-service) 与 [可接受使用政策](https://docs.github.com/zh/site-policy/acceptable-use-policies/github-acceptable-use-policies)
- 你的凭据留在你设备上，绝不会发送到本项目控制的任何服务器
- API 速率限制由你自行管理

请仅在你拥有或获得授权的账号上使用。

---

## 开源协议

**GPL-3.0** —— 详见 [LICENSE](LICENSE)。

本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证条款（第 3 版或你选择的任何更新版本）重新分发和/或修改它。

---

<div align="center">

**Kotlin · Jetpack Compose · Material 3 · Room · Retrofit · CameraX**

⭐ 如果它对你有用，点个 Star 能帮更多人发现它。

</div>
