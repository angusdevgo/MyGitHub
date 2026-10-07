<p align="center">
  <img src="docs/images/app_icon.png" alt="MyGitHub Logo" width="120" height="120">
</p>

<h1 align="center">MyGitHub</h1>

<p align="center">
  <strong>零服务器 · 纯端侧 · 原生 Android GitHub 工作台</strong>
</p>

<p align="center">
  <a href="https://github.com/angusdevgo/MyGitHub/releases"><img src="https://img.shields.io/badge/Release-v0.0.1-brightgreen.svg?style=for-the-badge&logo=github" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin" alt="Kotlin">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/%E5%90%8E%E7%AB%AF-%E9%9B%B6%E6%9C%8D%E5%8A%A1%E5%99%A8-success?style=for-the-badge" alt="Zero Server">
  <img src="https://img.shields.io/badge/2FA-%E5%86%85%E7%BD%AE%20TOTP-orange?style=for-the-badge" alt="2FA">
  <a href="https://linux.do/"><img src="https://img.shields.io/badge/%E7%A4%BE%E5%8C%BA-LINUX%20DO-23272A?style=for-the-badge&logo=discourse" alt="LINUX DO"></a>
</p>

<p align="center">
  [ <a href="README.md">English</a> | <strong>中文文档</strong> ]
</p>

<p align="center">
  <a href="#-项目简介">项目简介</a> •
  <a href="#-为什么要再造一个-github-客户端">设计动机</a> •
  <a href="#-核心能力">核心能力</a> •
  <a href="#-架构设计">架构设计</a> •
  <a href="#-快速开始">快速开始</a> •
  <a href="#-认证模型">认证模型</a> •
  <a href="#-项目结构">项目结构</a> •
  <a href="#-路线图">路线图</a> •
  <a href="#-免责声明">免责声明</a>
</p>

---

> 🔗 **社区**：
> - 社区支持与技术讨论：[**LINUX DO**](https://linux.do/)。

---

## 🌟 项目简介

MyGitHub 是一个**完全自包含、无需任何服务端的 Android GitHub 客户端**，使用 Kotlin 与 Jetpack Compose Material 3 构建。

它只为回答一个朴素的问题：*为什么查看自己的仓库、Issue 和通知，非得装一个臃肿的 App、养一台服务器、或者接受一堆埋点 SDK？*

MyGitHub 的一切都**在设备本地运行**。没有需要部署的后端，没有需要续费的订阅，没有统计 SDK，没有遥测。你的 GitHub 令牌永远不会离开手机——它由 Android Keystore 提供的 AES-256-GCM 加密后存放在 `EncryptedSharedPreferences` 中。

除了常规客户端功能，MyGitHub 还内置了一套 **RFC 6238 TOTP 引擎**，可直接作为 GitHub 双重验证器使用，彻底取代 Google Authenticator。

### 它解决的五个问题

| 痛点 | MyGitHub 的答案 |
|---|---|
| 💸 **第三方客户端要付费后端** | 零服务端架构。基于 OkHttp 直连 GitHub REST API，你的令牌直接完成鉴权——无中继、无代理、零成本。 |
| 📱 **官方 App 臃肿且不可定制** | 27 个 Kotlin 文件、约 7000 行代码。纯 Material 3，无广告 SDK、无追踪、无冗余。 |
| 🔐 **2FA 还得再装一个 App** | 内置 RFC 6238 TOTP 引擎，支持扫码导入、手动 Base32 导入、实时倒计时与一键复制。 |
| 🐌 **列表卡顿、反复重载** | Room 缓存优先 + Flow 双次发射。冷启动瞬间从磁盘渲染，网络在背后静默刷新。实测卡顿率 0.35%。 |
| 🌐 **某些网络下 `github.com` 不通** | Device Flow 可借任意代理完成；PAT 登录只访问 `api.github.com`。内置受限网络引导。 |

---

## ⚙️ 为什么要再造一个 GitHub 客户端

市面上大多数「开源 GitHub 客户端」都掉进了三个坑之一：

1. **偷偷需要一台服务器。** OAuth Web Flow 必须在后端持有 `client_secret`。把它打进 APK，任何人反编译都能提取。MyGitHub 从根本上绕开这个问题——只支持**无需客户端密钥的认证流程**，详见[认证模型](#-认证模型)。
2. **是 Electron 或 WebView 套壳。** 又慢又费电，主题也没法好好适配。MyGitHub 是 100% 原生 Compose。
3. **把你的令牌送往代理服务器。** 每一次请求都从你的设备经 TLS 直达 `api.github.com`，中间没有任何一环。

**完整的数据链路是：你的手机 → GitHub。** 这，就是全部架构。

---

## ⚡ 核心能力

- 🏠 **个性化推荐流** — 端侧推荐引擎读取你的 Star 历史，推断主要语言与 Topic 偏好，推荐相邻项目。全程无服务端画像。
- 🏆 **趋势排行榜** — 日榜 / 周榜 / 月榜，采用分级星标阈值，Room 缓存支持离线浏览。
- 🔔 **通知中心** — 完整的 GitHub 通知收件箱，支持按原因筛选（提及 / 指派 / 评审等），**Issue 状态精准还原**（`Open` / `Closed` 与真实 API 对账，而非从通知文本猜测），乐观标记已读，保留 30 天。
- 📡 **收到的动态** — 由于 GitHub 没有「谁 Star 了我的仓库」API，MyGitHub 聚合各仓库的 Star、Fork 与外部 Issue，汇成统一动态流，且全面并行化（7 个并发请求替代 33 个串行请求）。
- 📦 **仓库浏览** — 仓库列表、带 `Open`/`Closed` 状态徽章的 Issue 追踪、主题感知 CSS 注入的完整 README 渲染、Release 列表、Star / 取消 Star。
- 💬 **Issue 详情与交互** — 阅读完整讨论串、发表评论、`Close` / `Reopen` Issue，全程无需跳出 App。
- 🔐 **内置 2FA 验证器** — 完全离线计算的 RFC 6238 TOTP（HMAC-SHA1，30 秒，6 位）。支持 CameraX + ML Kit 扫码导入、手动 Base32 / `otpauth://` URI 导入、实时倒计时环、一键复制，并在个人页提供速览卡片。
- 🎨 **双主题设计系统** — 深空暗色主题（Nord 血统，Cobalt 强调色）与亮色主题，运行时切换无需重启。
- 🫧 **悬浮胶囊导航** — 真正的叠加式底栏：内容从其**下方**滑过以形成纵深感，GPU 驱动的弹簧指示器不触发任何重新布局。
- ⚡ **性能纪律** — 懒加载列表全量稳定 key、时间格式化记忆化、1 Hz TOTP 心跳局部化重组、Coil 全局内存 + 磁盘缓存、显式 `listState` 保证刷新后精准回顶。

---

## 🏗 架构设计

```
┌──────────────────────────────────────────────────────────────┐
│                     UI 层  (Jetpack Compose)                  │
│                                                              │
│   HomeScreen   RankingsScreen   NotificationsScreen          │
│   ReposScreen  ProfileScreen    RepoDetailScreen             │
│   IssueDetailScreen   SecurityScreen   LoginScreen           │
│                                                              │
│   设计系统：  KomiSurface · KomiChip · KomiRepoCard           │
│               KomiFloatingBottomBar                          │
│               KomiPullRefreshIndicator                       │
└───────────────────────────┬──────────────────────────────────┘
                            │  StateFlow / Flow<Result<T>>
┌───────────────────────────▼──────────────────────────────────┐
│                     数据层  (Repository)                      │
│                                                              │
│   GitHubRepository                                           │
│     ├─ 内存缓存    (用户名、Issue 状态)                        │
│     ├─ 缓存优先 Flow (推荐、榜单……)                            │
│     └─ 并行加载器  (supervisorScope + async)                  │
└──────────┬────────────────────────────┬──────────────────────┘
           │                            │
┌──────────▼───────────┐   ┌────────────▼─────────────────────┐
│  远程  (OkHttp)      │   │  本地  (Room + Keystore)         │
│                      │   │                                  │
│   Retrofit +         │   │   repo_cache      (列表缓存)      │
│   kotlinx.           │   │   notifications   (30 天收件箱)   │
│   serialization      │   │   star_tags       (自定义标签)    │
│                      │   │   recent_views    (浏览历史)      │
│   api.github.com     │   │   EncryptedSharedPreferences     │
│   (TLS 直连)         │   │   AES-256-GCM 令牌保险库          │
└──────────────────────┘   └──────────────────────────────────┘
                            │
                ┌───────────▼─────────────┐
                │   安全模块  (端侧)       │
                │   RFC 6238 TOTP 引擎     │
                │   Base32 编解码          │
                │   CameraX + ML Kit 扫码  │
                └─────────────────────────┘
```

### 值得了解的设计决策

| 决策 | 理由 |
|---|---|
| **缓存优先 `Flow` 而非 `suspend`** | 每个列表发射两次：一次来自 Room（`fromCache = true`），一次来自网络。用户在 100 ms 内看到内容；刷新静默落地，且仅在 ID 集合真正变化时才二次发射——列表永不跳动。 |
| **底栏指示器用 `graphicsLayer { translationX }`** | 用 `animateDpAsState` 驱动偏移会在每一帧触发布局。在 `graphicsLayer` 内驱动 `translationX`，动画全程停留在 GPU，零重组。 |
| **TOTP 心跳局部化** | 把 1 Hz 定时器放在页面根部，会让整页每分钟重组 60 次。心跳被下放到独立的 `TotpQuickCard` 组件内，只有该卡片重组。 |
| **状态探测替代文本猜测** | GitHub 通知负载不包含 Issue 状态。MyGitHub 不去解析标题，而是对可见通知查询真实 Issue 接口，并通过内存状态缓存合并结果。 |
| **聚合用 `supervisorScope` + `launch`** | 动态流会跨仓库、跨接口扇出；`supervisorScope` 隔离失败，单个仓库不可达不会导致整个动态流空白。 |
| **TOTP 密钥使用普通 `MODE_PRIVATE` 存储** | 早期基于 `EncryptedSharedPreferences` 的实现，在部分 OEM 系统 Keystore 重置后会静默返回 `null`，导致用户丢失 2FA 绑定。现在密钥存放在标准的应用私有文件中，并采用同步 `commit()` 写入——应用私有存储本身已由系统沙箱隔离。 |

---

## 🚀 快速开始

### 环境要求

| 要求 | 版本 |
|---|---|
| **JDK** | 17 |
| **Android SDK** | API 34（编译） / API 26+（设备） |
| **Android Studio** | Ladybug 或更新（可选） |

### 1. 克隆仓库

```bash
git clone https://github.com/angusdevgo/MyGitHub.git
cd MyGitHub
```

### 2. 配置 SDK 路径

```properties
# local.properties （自行创建，已被 gitignore）
sdk.dir=/你的/Android/Sdk/路径
```

### 3. 构建 Debug APK

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### 4. 构建签名 Release APK（可选）

Release 签名凭据**绝不**提交到本仓库。将其写入本地 `local.properties`：

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=你的_keystore_口令
RELEASE_KEY_ALIAS=你的_别名
RELEASE_KEY_PASSWORD=你的_密钥口令
```

然后生成密钥库并构建：

```bash
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias your_key_alias -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

> 若未提供签名凭据，Gradle 会产出**未签名**的 release APK 而非直接失败——任何人都能在干净的机器上编译本项目。

### 5. 安装到设备

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🔐 认证模型

MyGitHub 只支持两条登录路径。二者都确保**任何密钥都不会被打进 APK**。

### 路径 A — GitHub OAuth Device Flow（推荐）

```
  MyGitHub App                      GitHub                        你的浏览器
       │                               │                               │
       │  POST /login/device/code      │                               │
       │  （仅 client_id）             │                               │
       ├──────────────────────────────►│                               │
       │                               │                               │
       │◄──────────────────────────────┤                               │
       │  user_code: 9D6F-56E7         │                               │
       │                               │                               │
       │  ┌─ 展示验证码 ──────────────────────────────────────────────►│
       │  │                            │        用户输入 9D6F-56E7     │
       │  │                            │◄──────────────────────────────┤
       │  │                            │        用户点击「授权」        │
       │  │                            │                               │
       │  └─ 每 5 秒轮询 ─────────────►│                               │
       │     POST /login/oauth/access_token                           │
       │     （client_id + device_code）                               │
       │◄──────────────────────────────┤                               │
       │     access_token              │                               │
       ▼                               │                               │
  EncryptedSharedPreferences           │                               │
```

**为什么用这个流程？** Device Flow 只需要一个公开的 `client_id`——无需 `client_secret`。用户在 GitHub 自己的网页中完成认证，因此 App 全程看不到密码，反编译 APK 也提取不到任何可用凭据。

**为什么不用 OAuth Web Flow？** Web Flow 在换取令牌时强制要求 `client_secret`。要安全保管该密钥就必须有一台后端服务器。而 MyGitHub 刻意没有后端。

### 路径 B — Personal Access Token

对于 `github.com`（而非 `api.github.com`）被阻断的网络环境，PAT 登录是务实的后备方案。App 提供内联引导与一键链接，打开 GitHub 令牌创建页时**已预填 `repo`、`user`、`read:org` 三个权限范围**。

令牌存放于 `EncryptedSharedPreferences`，加密密钥由 Android Keystore 保管（可用时走硬件级加密）。

### 内置 2FA 验证器

当你的 GitHub 账号启用了 2FA 后，MyGitHub 可直接充当验证器应用：

1. 进入 **我的 → 安全中心**
2. 通过**扫描 GitHub 显示的二维码**或**粘贴 Base32 设置密钥**导入密钥
3. App 立即渲染实时 6 位动态码与倒计时环

动态码完全在本地按 RFC 6238 使用 HMAC-SHA1 计算，并已与独立参考实现交叉验证。验证器密钥存放于应用私有存储，**退出登录后依然保留**——退出只清除 GitHub 会话令牌，绝不触碰你的 2FA 绑定。

---

## 📋 项目结构

<details>
<summary>📁 展开完整目录树</summary>

```
MyGitHub/
├── app/
│   ├── build.gradle.kts                ← 模块配置（namespace、依赖、release 签名）
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mygithub/lab/
│       │   ├── MainActivity.kt              ← 入口 + 全局 Coil 图片加载器
│       │   │
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   └── GitHubApi.kt         ← Retrofit 接口 + 全部 DTO
│       │   │   ├── auth/
│       │   │   │   ├── DeviceFlowAuth.kt    ← RFC 8628 设备流 + 令牌轮询
│       │   │   │   └── TokenStore.kt        ← AES-256-GCM 令牌保险库
│       │   │   ├── local/
│       │   │   │   └── AppDatabase.kt       ← Room 实体 + DAO
│       │   │   └── repo/
│       │   │       └── GitHubRepository.kt  ← 缓存优先 Flow、并行加载器、
│       │   │                                   推荐引擎、状态缓存
│       │   │
│       │   ├── security/totp/
│       │   │   ├── TotpEngine.kt            ← RFC 6238 TOTP + RFC 4648 Base32
│       │   │   └── TotpStore.kt             ← 验证器密钥持久化保险库
│       │   │
│       │   └── ui/
│       │       ├── MyGitHubApp.kt           ← NavHost + 叠加式脚手架
│       │       ├── components/
│       │       │   ├── KomiComponents.kt           ← 卡片 / 芯片 / 仓库卡片基元
│       │       │   ├── KomiFloatingBottomBar.kt    ← GPU 动画悬浮胶囊导航
│       │       │   ├── KomiPullRefreshIndicator.kt ← 自定义下拉刷新指示器
│       │       │   └── UrlLauncher.kt              ← 安全外部链接路由
│       │       ├── screens/
│       │       │   ├── home/            HomeScreen.kt
│       │       │   ├── rankings/        RankingsScreen.kt
│       │       │   ├── notifications/   NotificationsScreen.kt
│       │       │   ├── repos/           ReposScreen.kt · RepoDetailScreen.kt
│       │       │   ├── issues/          IssueDetailScreen.kt
│       │       │   ├── stars/           StarsScreen.kt
│       │       │   ├── search/          SearchScreen.kt
│       │       │   ├── security/        SecurityScreen.kt · QrScannerScreen.kt
│       │       │   ├── login/           LoginScreen.kt
│       │       │   └── profile/         ProfileScreen.kt
│       │       └── theme/
│       │           ├── Theme.kt             ← Nord 深色 / 亮色配色方案
│       │           └── Type.kt              ← 排版层级
│       │
│       └── res/                             ← 图标、颜色、字符串
│
├── relay/                               ← 【可选】Cloudflare Worker 中继
│   ├── src/                             ← （跨设备 2FA 批准，非必需）
│   ├── package.json
│   └── wrangler.toml
│
├── docs/images/                         ← 截图与示意图
├── keystore/                            ← ⚠️ 已 gitignore —— 你的签名密钥放这里
├── gradle/                              ← Gradle wrapper
│
├── build.gradle.kts                     ← 根构建脚本
├── settings.gradle.kts
├── gradle.properties
├── local.properties                     ← ⚠️ 已 gitignore —— SDK 路径 + 签名凭据
│
├── LICENSE                              ← GPL-3.0
├── VERSION                              ← 当前版本号
├── CHANGELOG.md
├── README.md                            ← 英文文档
└── README.zh.md                         ← 你正在阅读（中文）
```

</details>

---

## 🧩 可选：Cloudflare 中继

`relay/` 目录包含一个**可选**的 Cloudflare Worker，实现跨设备 2FA 批准的盲眼中继（在已受信任的旧设备上匹配两位数挑战，从而批准新设备登录）。

**上文描述的任何功能都不依赖它。** MyGitHub 在没有它的情况下也能完整运行。

该中继被设计为*盲眼*中间人：只转发它无法解密的 AES-256-GCM 信封；批准动作由存放在设备安全元件中的 Ed25519 私钥签名；挑战校验发生在请求方设备本地。Worker 不存储任何令牌、密钥或明文。

部署（约 2 分钟，免费额度足够）：

```bash
cd relay
npm install
npx wrangler login
npx wrangler deploy
```

完整安全模型见 [`relay/README.md`](relay/README.md)。

---

## ⚡ 性能实测

在小米 2206123SC（Android 13）上通过 `dumpsys gfxinfo` 实测：

| 页面 | 总帧数 | 卡顿帧 | 99 分位 |
|---|---|---|---|
| 通知信息流 | 445 | **3（0.67%）** | 10 ms |
| 个人页 | 577 | **2（0.35%）** | 7 ms |

采用的优化手段：

- **懒加载列表全量稳定 key** —— Compose 复用组合结果，滚动时不重建行。
- **派生状态记忆化** —— `remember(updatedAt) { relativeTimeFromIso(updatedAt) }` 避免每次重组都重新解析 ISO 时间戳。
- **`derivedStateOf` 处理筛选列表** —— 仅在输入变化时重算筛选，而非依赖父级每次重组。
- **TOTP 计时器局部化重组** —— 1 Hz 时钟被限制在叶子组件内。
- **Coil 全局缓存** —— 50 MB 内存 + 100 MB 磁盘，配合 `crossfade(false)` 与显式 `memoryCacheKey`，消除快速滚动时的解码尖刺。
- **显式 `LazyListState`** —— 下拉刷新与 Tab 切换后滚动位置确定性重置。
- **显式 `PullToRefreshState`** —— 刷新动画由自有状态对象驱动，指示器永不卡滞。

---

## 🗺 路线图

- [x] 仓库浏览与 README 渲染
- [x] Issue 详情、评论、关闭 / 重开
- [x] 端侧推理的个性化推荐流
- [x] 趋势排行榜（日 / 周 / 月）
- [x] 带精准 Issue 状态的通知中心
- [x] 聚合式「收到的动态」流
- [x] 内置 RFC 6238 TOTP 验证器与扫码导入
- [x] GPU 动画指示器的悬浮胶囊导航
- [ ] 代码浏览与语法高亮
- [ ] Pull Request 评审与行内评论
- [ ] 多账号支持
- [ ] 通过内置中继实现跨设备 2FA 批准
- [ ] Material You 动态取色

---

## 🤝 参与贡献

欢迎提交 Issue 与 Pull Request。

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/amazing-feature`）
3. 提交改动（`git commit -m 'Add amazing feature'`）
4. 推送分支（`git push origin feature/amazing-feature`）
5. 发起 Pull Request

**提交前请确认**：

- `./gradlew assembleRelease` 构建通过且无新增警告
- 差异中不包含任何凭据、令牌、密钥库或个人信息
- 新增 UI 同时适配深色与亮色主题

---

## ⚠️ 免责声明

本项目定位于**个人使用与学习研究**。

- 本项目**与 GitHub, Inc. 无任何隶属、背书或赞助关系**
- 仅使用**公开文档化的 GitHub REST API** 接口
- **你有责任**遵守 [GitHub 服务条款](https://docs.github.com/zh/site-policy/github-terms/github-terms-of-service) 与 [可接受使用政策](https://docs.github.com/zh/site-policy/acceptable-use-policies/github-acceptable-use-policies)
- 你的凭据与令牌**仅存储在你的设备上**，绝不会传输至本项目控制的任何服务器
- API 速率限制与配额消耗完全由你自行承担

请仅在你拥有或获得授权的账号上使用。

---

## 📜 开源协议

本项目采用 **GNU General Public License v3.0** 协议 —— 详见 [LICENSE](LICENSE)。

本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证条款（第 3 版或你选择的任何更新版本）重新分发和/或修改它。

---

<p align="center">
  <sub>基于 Kotlin · Jetpack Compose · Material 3 · Room · Retrofit · CameraX 构建</sub>
</p>

<p align="center">
  <sub>⭐ 如果 MyGitHub 对你有帮助，欢迎点个 Star。</sub>
</p>
