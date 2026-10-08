<h1 align="center">MyGitHub</h1>

<div align="center">

[English](README.md) | [中文](README.zh.md)

<img src="docs/images/app_icon.png" alt="MyGitHub" width="150" />

<p><strong>一个完全跑在你手机上的 GitHub 客户端</strong></p>
<p>Android · 零服务器 · 本地保险箱 · TOTP · Jetpack Compose</p>

[![Version](https://img.shields.io/badge/version-v0.0.1-3B5BDB?style=flat-square)](https://github.com/angusdevgo/MyGitHub/releases)
[![Last Commit](https://img.shields.io/github/last-commit/angusdevgo/MyGitHub?style=flat-square)](https://github.com/angusdevgo/MyGitHub/commits)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)
[![Stars](https://img.shields.io/github/stars/angusdevgo/MyGitHub?style=flat-square&color=EBCB8B)](https://github.com/angusdevgo/MyGitHub/stargazers)

[![Platform](https://img.shields.io/badge/android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/jetpack%20compose-material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Community](https://img.shields.io/badge/community-LINUX%20DO-23272A?style=flat-square)](https://linux.do/)

</div>

---

MyGitHub 是一个用 Kotlin + Jetpack Compose 写的 GitHub 原生 Android 客户端。

它做的就是你手机上真正会打开 GitHub 去做的事——翻自己的仓库、看和回议题、Fork 项目、盯着自己仓库上发生的事——而且**全程只和 GitHub 自己的服务器通信，不碰任何第三方后端**。

约 **10,000 行 Kotlin，37 个文件**。一个下午能读完，也能确认它到底拿你的凭据做了什么。

---

## 这是什么

最后那句话是整件事的重点，所以说具体一点：

- **没有后端。** 不是免费额度，不是 Serverless 函数，也不是反代。没有东西要部署，没有东西要付费，没有东西会挂掉。
- **你的令牌只留在你设备上。** AES-256-GCM 加密，密钥由 Android Keystore 保管。
- **每个请求都经 TLS 直连 `api.github.com`。** 你可以读源码验证，也可以自己抓包看。
- **它自带 2FA 验证器。** RFC 6238 TOTP，离线计算。你可以把 Google Authenticator 卸了。

---

## 为什么你可能会想要它

**你不想为客户端付费。** 大多数第三方 GitHub 客户端都需要后端，因为 OAuth 的 Web Flow 在换 token 时必须携带 `client_secret`。把这个 secret 打进 APK，任何人反编译都能冒充你的应用；于是这些项目选择自建服务器——而费用由你承担。

MyGitHub 绕开的办法是根本不支持 Web Flow。它只做 **Device Flow**（不需要 secret）和 **PAT 登录**（什么都不需要）。仅这一条约束，就把整个后端需求消掉了。

**你想要一个不会丢密钥的验证器。** 内置 TOTP 引擎导入的是 Google Authenticator 用的同一份密钥。它能在退出登录、应用升级、进程被杀之后依然存活——因为它存在普通的应用私有文件里并同步落盘，而不是存在某些 OEM ROM 上可能被静默复位的、Keystore 包装的脆弱存储里。

**你受够了列表卡顿。** 缓存优先架构：每个列表先渲染本地数据，再在你背后静默刷新。在小米 2206123SC 上实测掉帧率：个人页 **0.35%**，通知页 **0.67%**，通知流零丢帧。

**你想先读代码再决定信不信。** 37 个文件。没有混淆的 SDK，没有统计埋点，没有你没要求的网络请求。搜一下 `api.github.com`——那就是这个应用全部的外发请求。

---

## 它能做什么

<table>
<tr><td width="50%" valign="top">

**浏览**

- 个性化推荐流——从你的星标推断，全部在端侧计算
- 排行榜（日 / 周 / 月），支持语言筛选
- 你的仓库，含星标数、Fork 数、语言、最近更新
- 仓库与议题搜索

</td><td valign="top">

**交互**

- 阅读完整议题长帖与评论
- 发表评论
- 关闭 / 重新打开议题
- **标星 / 取消标星为乐观更新**——不重载页面，不等网络
- **应用内 Fork**——可自定义仓库名，可只复制默认分支
- README 渲染，深色 / 浅色主题正确适配

</td></tr>
<tr><td valign="top">

**掌握动态**

- **议题 Tab**——你自己所有仓库里的每一条 Issue 与 PR，可按 `All` / `Open` / `Closed` 筛选
- **动态 Tab**——你仓库上真实的 Star / Fork / Issue / PR 事件流，附总量概览（总 Stars、总 Forks、公开议题）与「自上次查看 +N Stars」增量
- 不靠通知收件箱猜状态：所有状态都来自真实 API

</td><td valign="top">

**安全**

- 内置 RFC 6238 TOTP 验证器
- 扫码导入（CameraX + ML Kit）或手动粘贴 Base32 / `otpauth://`
- 实时倒计时，一键复制
- 退出登录不受影响——2FA 绑定永不被清除
- **应用内自更新 + SHA-256 完整性校验**——启动检测新版，安装前校验哈希
- **网络加速与代理引擎**——直连、公共 GitHub CDN 镜像、自定义 HTTP/SOCKS5 代理，附实时延迟测速；代理隧道失败时自动回退直连

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
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 构建
./gradlew assembleDebug
```

APK 产出在 `app/build/outputs/apk/debug/app-debug.apk`：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 构建签名 Release

签名凭据**绝不**入库。放在 `local.properties` 里：

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_password
```

没有 keystore 就先生成一个，然后构建：

```bash
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias your_alias -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease
```

> 完全跳过这一步也不会失败——Gradle 会产出一个**未签名**的 Release APK。任何人都能在干净机器上编译本项目。

版本号来自仓库根目录的 `VERSION` 文件，`versionName` 与 `versionCode` 都在构建时由它推导。

### 跑测试

```bash
./gradlew testReleaseUnitTest
```

---

## 登录

MyGitHub 提供两种登录方式。两者都保证**APK 里不含任何密钥**。

### Device Flow —— 推荐

这就是 `gh auth login` 和智能电视用的同一套机制。

```
1.  点「使用 GitHub 账号登录」
2.  应用展示一个验证码：  9D6F-56E7
3.  验证码自动复制到剪贴板，并尝试打开浏览器
4.  输入验证码，点 Authorize
5.  应用自动感知并完成登录
```

你的密码输在 GitHub 自己的网站上，应用永远看不到。整个流程唯一涉及的凭据是一个**公开的 client ID**——反编译 APK 什么也拿不到。

验证码卡片带**实时倒计时**和**取消按钮**，所以你永远不会被困在等待里：随时中断，换一种登录方式即可。如果你的 ROM 无法自动唤起浏览器，卡片同样提供复制按钮和手动步骤指引。

### Personal Access Token —— 后备方案

有些网络屏蔽 `github.com` 但放行 `api.github.com`，Device Flow 在那里用不了，所以保留了 PAT 登录。

应用会一步步引导你，并提供一条**已预勾选 `repo`、`user`、`read:org`** 的创建链接。

### 启用 2FA

登录之后，MyGitHub 也可以当你的验证器用：

1. 进入 **个人 → 双重验证**
2. 扫描 GitHub 展示的二维码，或粘贴 Base32 设置密钥
3. 6 位动态码出现，附实时倒计时圆环

动态码按 RFC 6238 用 HMAC-SHA1 在本地计算。密钥存在应用私有存储里，**退出登录不会清它**——退出只清会话令牌。

---

## 应用结构

五个底部 Tab，外加全屏二级页面。

| Tab | 内容 |
|---|---|
| **首页** | 个性化推荐、分类芯片、搜索入口 |
| **排行榜** | 日 / 周 / 月趋势榜，附语言筛选 |
| **信息** | `议题`（你全部仓库的 Issue/PR，All/Open/Closed）与 `动态`（Star/Fork/Issue 事件流） |
| **仓库** | 你的仓库，可按公开 / 私有 / Fork 筛选 |
| **个人** | 身份卡片、星标、最近浏览、双重验证、应用设置、关于 |

**个人 → 应用设置** 内含外观主题、账号信息与网络代理引擎。
**个人 → 关于** 内含应用图标、版本号、检查更新、开源许可、源码与反馈入口。

---

## 技术实现

```
┌─────────────────────────────────────────────────────┐
│  Compose UI                                         │
│  首页 · 排行榜 · 信息 · 仓库 · 个人                  │
│  仓库详情 · 议题详情 · 应用设置 · 关于 · …          │
└────────────────────────┬────────────────────────────┘
                         │  Flow<Result<T>>
┌────────────────────────▼────────────────────────────┐
│  GitHubRepository                                   │
│  · 缓存优先 Flow（先 Room，后网络）                 │
│  · 并行加载（supervisorScope + async）              │
│  · 自有仓库扇出限流（Semaphore）                    │
└───────┬──────────────────────────────┬──────────────┘
        │                              │
┌───────▼──────────┐      ┌────────────▼──────────────┐
│  Retrofit/OkHttp │      │  Room + Keystore          │
│  → api.github.com│      │  repo_cache · star_tags   │
│    经 TLS 直连    │      │  recent_views             │
│  · 代理引擎      │      │  EncryptedSharedPreferences│
│  · 自动回退直连   │      └───────────────────────────┘
└──────────────────┘
        ┌──────────────────────────────────┐
        │  TOTP（端侧、离线）              │
        │  RFC 6238 · Base32 · ML Kit 扫码 │
        └──────────────────────────────────┘
```

### 值得解释的设计决策

**用缓存优先 Flow 而不是 suspend 函数。**
每个列表都 emit 两次：一次来自 Room（`fromCache = true`），一次来自网络。内容 100ms 内就能看到。刷新静默落地，且只在 ID 集合真的变化时才二次 emit——所以列表永远不会在你手指底下跳动。

**标星是乐观更新，不是同步等待。**
显而易见的做法是：点击 → 等网络 → 重新拉 README → 重建整页 → 重载 WebView。为了一个 bit 的变化付出两个网络往返加一次整页渲染，手感也确实如你所料的慢。现在点击会立刻执行注入的 JavaScript 翻转按钮高亮、文字与计数，然后在后台提交请求，只有失败时才回滚（并弹提示）。

**议题数据来自 API，不来自收件箱。**
GitHub 的 `/notifications` 返回的是**你的订阅收件箱**，不是「你仓库里的所有 issue」——你自己开的 issue 不会给自己产生通知。所以信息 Tab 直接对自有仓库并发查询 `/repos/{owner}/{repo}/issues`，并通过 `pull_request` 标记合并 PR（GitHub 在同一个端点里返回它们）。

**动态走仓库事件流，不走 stargazer 列表。**
`/repos/{o}/{r}/stargazers` 没有排序参数且按最旧优先返回，所以「最近谁 star 了我」从第一页基本问不出答案。仓库事件流按时间倒序给出带真实操作者的 Star / Fork / Issue / PR 事件，再对仓库对象做一次汇总就得到精确总量和「自上次查看」的增量。

**代理不会把应用搞废。**
在已经跑了系统级 VPN 的手机上再叠一层应用代理是多余的——而且如果那个代理对 `CONNECT` 返回类似 HTTP 402 的东西，所有请求都会死。回退拦截器会识别代理隧道失败，并用一个专用直连客户端重试一次。

**`Accept` 头只能作为默认值。**
在拦截器里无条件设置 `Accept`，会静默覆盖 `readme` 与 `contents` 依赖的调用点取值，导致这些接口返回 JSON 元数据而非渲染好的 HTML，所有仓库页因此崩坏。现在拦截器只在调用方没指定时才补默认值。

**底栏指示器用 `graphicsLayer { translationX }`。**
用 `animateDpAsState` 动 offset 会每帧触发布局。把 `translationX` 放进 `graphicsLayer` 里驱动，整个动画留在 GPU 上，零重组。

**TOTP 时钟放在叶子组件里。**
在页面根部放一个 1Hz 定时器，一分钟要重组整页 60 次。把它塞进 `TotpQuickCard` 里，就只有那张卡片会重组。

**2FA 密钥用普通的应用私有存储。**
早期版本用了 `EncryptedSharedPreferences`。在某些 OEM ROM 上，Keystore 复位会让它静默返回 `null`——意味着用户丢失 2FA 绑定并被锁在账号外。现在密钥存在标准的应用私有文件里，用 `commit()` 写入。应用私有存储本身已被系统沙箱隔离，额外那层加密只增加了脆弱性，没带来实质保护。

**应用更新在拉起安装器前校验密码学摘要。**
下载一个任意二进制不经验证就交给包管理器，等于给篡改开门。MyGitHub 会从 GitHub Release 正文里解析作者公布的 SHA-256 摘要，边下载边对流式字节计算哈希，任何比对失败的文件都会在触碰 `FileProvider` 或系统安装器之前被清除。

**滚动流畅靠的是节流重组。**
每个懒加载项都有稳定 key，Compose 得以复用组合而不是重建行。时间戳走 `remember(updatedAt) { relativeTimeFromIso(updatedAt) }`，而不是每次经过都重新解析 ISO 字符串。筛选列表用 `derivedStateOf`。1Hz 的 TOTP 倒计时被限制在叶子组件里。Coil 全局缓存图片（内存 50MB、磁盘 100MB），并指定 `memoryCacheKey`，消除滚动中段的解码抖动。

---

## 项目结构

```
MyGitHub/
├── VERSION                          版本号唯一事实源
├── scripts/release.py               一条命令完成版本自增与发布流水线
├── app/src/main/java/com/mygithub/lab/
│   ├── MainActivity.kt              入口
│   ├── data/
│   │   ├── api/GitHubApi.kt         Retrofit 接口 + DTO
│   │   ├── auth/                    Device Flow、令牌保险箱
│   │   ├── local/AppDatabase.kt     Room 实体与 DAO
│   │   ├── model/                   面向 UI 的模型
│   │   ├── network/ProxyManager.kt  代理模式、镜像、延迟测速
│   │   ├── repo/GitHubRepository.kt 缓存优先 Flow、并行加载
│   │   ├── update/                  版本比对、APK 下载与哈希校验
│   │   └── util/                    纯函数（均有单元测试覆盖）
│   ├── security/totp/               RFC 6238 引擎、持久化密钥存储
│   └── ui/
│       ├── MyGitHubApp.kt           NavHost + 悬浮脚手架
│       ├── components/              设计系统基础组件
│       ├── screens/                 每个页面一个包
│       └── theme/                   由 Nord 派生的配色方案
│
├── app/src/test/                    JVM 单元测试
└── docs/images/                     项目素材
```

---

## 路线图

- [x] 仓库浏览与主题化 README 渲染
- [x] 议题详情、评论、关闭 / 重开
- [x] 端侧个性化推荐
- [x] 趋势排行榜
- [x] 由真实 API 状态驱动的议题与动态 Tab
- [x] 带扫码导入的内置 2FA 验证器
- [x] 悬浮胶囊底栏
- [x] 秒级标星与应用内 Fork
- [x] 网络代理引擎（镜像 + 延迟测速）
- [x] 应用内自更新 + SHA-256 校验
- [ ] 带语法高亮的代码浏览
- [ ] Pull Request 评审
- [ ] 多账号支持
- [ ] Material You 动态取色

---

## 常见问题

**需要任何服务器或订阅吗？**
不需要。没有后端。你的令牌直接对 GitHub 完成每次请求的认证。

**我的令牌安全吗？**
它以 AES-256-GCM 加密，密钥存于 Android Keystore，落盘在 `EncryptedSharedPreferences`，除了 `api.github.com` 之外不会发往任何地方。

**没有代理能用吗？**
只要能连通 `github.com`，Device Flow 就能用。如果你的网络屏蔽了它，改用 PAT 登录——那只依赖 `api.github.com`。连接失败时应用会提示该走哪条路，代理引擎也可以改走公共 CDN 镜像或你自己的 HTTP/SOCKS5 代理。

**退出登录会丢 2FA 吗？**
不会。验证器密钥与会话分开存储，只有你在「双重验证」里主动解绑才会清除。

**正在等待授权的登录可以取消吗？**
可以。Device Flow 卡片带取消按钮和倒计时；轮询任务会立刻终止，离开页面时也会自动清理。

**为什么不做 OAuth Web Flow？**
它在换 token 时必须带 `client_secret`，意味着要么把这个 secret 打进 APK（不安全），要么自建后端（要花钱）。Device Flow 和 PAT 登录把两个问题都避开了。

**能用于 GitHub Enterprise 吗？**
目前不能。API base URL 硬编码为 `api.github.com`。改动量很小——欢迎提 PR。

---

## 参与贡献

欢迎提 Issue 和 Pull Request。

```bash
git checkout -b feature/your-feature
# 修改代码
./gradlew testReleaseUnitTest  # 单元测试必须通过
./gradlew assembleRelease      # 应能干净构建
git commit -m "feat: your feature"
git push origin feature/your-feature
```

提 PR 前请确认：

- `./gradlew assembleRelease` 通过且无新增警告
- `./gradlew testReleaseUnitTest` 通过
- diff 中没有凭据、令牌、keystore 或个人信息
- 新 UI 在深色与浅色主题下都正常

---

## 免责声明

本项目用于**个人使用与学习**。

- 与 GitHub, Inc. 无关联，未获其背书或赞助
- 仅使用公开文档化的 GitHub REST API 端点
- 你有责任遵守 [GitHub 服务条款](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service)与[可接受使用政策](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies)
- 你的凭据留在本机，永远不会发往本项目控制的任何服务器
- 速率限制由你自己管理

请在你拥有或已获授权的账号上使用。

---

## 开源协议

**GPL-3.0** —— 见 [LICENSE](LICENSE)。

本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证（第 3 版或更高版本）条款重新分发和/或修改它。

---

## 社区

问题、反馈与版本发布公告都在 [**LINUX DO**](https://linux.do/) 社区——一个中文开发者与技术爱好者的论坛。

---

<div align="center">

**Kotlin · Jetpack Compose · Material 3 · Room · Retrofit · CameraX**

⭐ 如果它对你有用，一个 Star 能帮更多人发现它。

<a href="https://linux.do/">
  <img src="https://img.shields.io/badge/Join%20us%20on-LINUX%20DO-23272A?style=for-the-badge" alt="LINUX DO" />
</a>

</div>
