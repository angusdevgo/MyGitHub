<p align="center">
  <img src="docs/images/app_icon.png" alt="MyGitHub Logo" width="120" height="120">
</p>

<h1 align="center">MyGitHub</h1>

<p align="center">
  <strong>Zero-Server · Pure Client-Side · Native Android GitHub Workbench</strong>
</p>

<p align="center">
  <a href="https://github.com/angusdevgo/MyGitHub/releases"><img src="https://img.shields.io/badge/Release-v0.0.1-brightgreen.svg?style=for-the-badge&logo=github" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin" alt="Kotlin">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/Backend-Zero%20Server-success?style=for-the-badge" alt="Zero Server">
  <img src="https://img.shields.io/badge/2FA-TOTP%20Built--in-orange?style=for-the-badge" alt="2FA">
  <a href="https://linux.do/"><img src="https://img.shields.io/badge/Community-LINUX%20DO-23272A?style=for-the-badge&logo=discourse" alt="LINUX DO"></a>
</p>

<p align="center">
  [ <strong>English</strong> | <a href="README.zh.md">中文文档</a> ]
</p>

<p align="center">
  <a href="#-project-overview">Overview</a> •
  <a href="#-why-another-github-client">Why</a> •
  <a href="#-core-capabilities">Capabilities</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-authentication-model">Auth Model</a> •
  <a href="#-project-structure">Structure</a> •
  <a href="#-roadmap">Roadmap</a> •
  <a href="#-disclaimer">Disclaimer</a>
</p>

---

> 🔗 **Attribution & Reference Sources**:
> - UI design language, personality theme system & component architecture referenced from: [**komi-store/komi-store**](https://github.com/komi-store/komi-store) (GPL-3.0 License).
> - Engineering discipline, agent execution contracts & documentation conventions referenced from: [**angusdevgo/Seep-Reverse-Lab**](https://github.com/angusdevgo/Seep-Reverse-Lab) (GPL-3.0 License).
> - Community support & technical discussions: [**LINUX DO**](https://linux.do/).

---

## 🌟 Project Overview

MyGitHub is a **fully self-contained, serverless GitHub client for Android**, built with Kotlin and Jetpack Compose Material 3.

It exists to answer a simple question: *why does reading your own repositories, issues, and notifications require a bloated app, a paid backend, or a data-mining SDK?*

Everything in MyGitHub runs **entirely on-device**. There is no backend service to deploy, no subscription to maintain, no analytics SDK, and no telemetry. Your GitHub token never leaves your phone — it is encrypted with AES-256-GCM via the Android Keystore and stored in `EncryptedSharedPreferences`.

Beyond a conventional client, MyGitHub ships a **built-in RFC 6238 TOTP engine**, letting the app double as a fully functional GitHub two-factor authenticator — replacing Google Authenticator entirely.

### Five Problems It Solves

| Problem | MyGitHub's Answer |
|---|---|
| 💸 **Third-party clients need a paid backend** | Zero-server architecture. Direct GitHub REST API over OkHttp. Your token authenticates every call — no relay, no proxy, no cost. |
| 📱 **Official app is heavy and opinionated** | 27 Kotlin files, ~7,000 lines. Pure Material 3, no ad SDKs, no tracking, no bloat. |
| 🔐 **2FA requires yet another app** | Built-in RFC 6238 TOTP engine with QR import, manual Base32 import, live countdown, and one-tap copy. |
| 🐌 **Lists stutter and reload endlessly** | Cache-first Room architecture with dual-emit Flows. Cold start renders from disk instantly; the network silently refreshes behind. Measured 0.35% janky frames. |
| 🌐 **`github.com` is unreachable in some networks** | Device Flow works through any proxy; PAT login only ever touches `api.github.com`. Built-in guidance for restricted networks. |

---

## ⚙️ Why Another GitHub Client

Most "open-source GitHub clients" fall into one of three traps:

1. **They secretly need a server.** OAuth Web Flow requires a `client_secret` on a backend. Ship that in an APK and anyone can decompile it out. MyGitHub avoids this entirely by supporting only **credential-less auth flows** — see [Authentication Model](#-authentication-model).
2. **They are Electron or WebView wrappers.** Slow, battery-hungry, and impossible to theme properly. MyGitHub is 100% native Compose.
3. **They ship your token to a proxy.** Every request goes straight from your device to `api.github.com` over TLS. Nothing in between.

**The entire data path is: your phone → GitHub.** That is the whole architecture.

---

## ⚡ Core Capabilities

- 🏠 **Personalized Discovery Feed** — On-device recommendation engine that reads your starred repositories, infers your dominant language and topics, and surfaces adjacent projects. No server-side profiling.
- 🏆 **Trending Rankings** — Daily / weekly / monthly leaderboards with graded star thresholds, cached in Room for offline reading.
- 🔔 **Notifications Center** — Full GitHub notification inbox with reason filtering (mention / assign / review / etc.), **accurate live issue state** (`Open` vs `Closed` reconciled against the real API, not guessed from notification text), optimistic read-marking, and 30-day retention.
- 📡 **Received Activity Feed** — Because GitHub has no "who starred my repos" API, MyGitHub aggregates stargazers, forks, and inbound issues across your repositories into a unified activity stream, fully parallelized (7 concurrent requests instead of 33 serial ones).
- 📦 **Repository Browser** — Repository list, issue tracker with `Open`/`Closed` state chips, full README rendering with theme-aware CSS injection, releases, and star/unstar.
- 💬 **Issue Detail & Interaction** — Read the full thread, post comments, and `Close`/`Reopen` issues without leaving the app.
- 🔐 **Built-in 2FA Authenticator** — RFC 6238 TOTP (HMAC-SHA1, 30 s, 6 digits) computed entirely offline. QR import via CameraX + ML Kit, manual Base32 / `otpauth://` URI import, live countdown ring, one-tap copy, and a summary card on the profile screen.
- 🎨 **Dual-Theme Design System** — Deep-space dark theme (Nord-inspired, Cobalt accent) plus a light theme, switchable at runtime without restart.
- 🫧 **Floating Capsule Navigation** — A true overlay bottom bar: content scrolls *underneath* it for depth, with a GPU-driven spring-animated indicator that never triggers re-layout.
- ⚡ **Performance Discipline** — Lazy list keys everywhere, memoized time formatting, localized recomposition for the 1 Hz TOTP tick, global Coil memory + disk cache, and an explicit `listState` so refreshes snap back to the top.

---

## 🏗 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    UI Layer  (Jetpack Compose)                │
│                                                              │
│   HomeScreen   RankingsScreen   NotificationsScreen          │
│   ReposScreen  ProfileScreen    RepoDetailScreen             │
│   IssueDetailScreen   SecurityScreen   LoginScreen           │
│                                                              │
│   Design System:  KomiSurface · KomiChip · KomiRepoCard      │
│                   KomiFloatingBottomBar                      │
│                   KomiPullRefreshIndicator                   │
└───────────────────────────┬──────────────────────────────────┘
                            │  StateFlow / Flow<Result<T>>
┌───────────────────────────▼──────────────────────────────────┐
│                    Data Layer  (Repository)                   │
│                                                              │
│   GitHubRepository                                           │
│     ├─ In-memory caches  (username, issue state)             │
│     ├─ Cache-first Flows (recommendations, trending, ...)    │
│     └─ Parallel loaders  (supervisorScope + async)           │
└──────────┬────────────────────────────┬──────────────────────┘
           │                            │
┌──────────▼───────────┐   ┌────────────▼─────────────────────┐
│   Remote (OkHttp)    │   │   Local (Room + Keystore)        │
│                      │   │                                  │
│   Retrofit +         │   │   repo_cache      (list caches)  │
│   kotlinx.           │   │   notifications   (30-day inbox) │
│   serialization      │   │   star_tags       (user labels)  │
│                      │   │   recent_views    (history)      │
│   api.github.com     │   │   EncryptedSharedPreferences     │
│   (TLS, direct)      │   │   AES-256-GCM token vault        │
└──────────────────────┘   └──────────────────────────────────┘
                            │
                ┌───────────▼─────────────┐
                │  Security (on-device)   │
                │  RFC 6238 TOTP Engine   │
                │  Base32 codec           │
                │  CameraX + ML Kit scan  │
                └─────────────────────────┘
```

### Design Decisions Worth Knowing

| Decision | Rationale |
|---|---|
| **Cache-first `Flow` instead of `suspend`** | Every list emits twice: once from Room (`fromCache = true`) and once from network. The user sees content in <100 ms; the refresh lands silently and only re-emits if the ID set actually changed — so lists never jump. |
| **`graphicsLayer { translationX }` for the bottom bar indicator** | Animating offset with `animateDpAsState` triggers layout on every frame. Driving `translationX` inside `graphicsLayer` keeps the animation entirely on the GPU with zero recomposition. |
| **Localized TOTP tick** | A 1 Hz timer inside the screen root recomposes the whole page 60 times per minute. The tick lives inside a dedicated `TotpQuickCard` composable instead, so only that card recomposes. |
| **State probing over text guessing** | The GitHub notifications payload does not include issue state. Rather than parsing titles, MyGitHub queries the real issue endpoint for visible notifications and merges results through an in-memory state cache. |
| **`supervisorScope` + `launch` for aggregation** | The activity feed fans out across repositories and endpoints; `supervisorScope` isolates failures so one unreachable repo cannot blank the whole feed. |
| **Plain `MODE_PRIVATE` prefs for the TOTP secret** | The earlier `EncryptedSharedPreferences`-based store silently returned `null` after Keystore resets on some OEM ROMs, causing users to lose 2FA enrollment. The secret is now kept in a standard app-private file with synchronous `commit()` — app-private storage is already sandboxed by the OS. |

---

## 🚀 Quick Start

### Prerequisites

| Requirement | Version |
|---|---|
| **JDK** | 17 |
| **Android SDK** | API 34 (compile) / API 26+ (device) |
| **Android Studio** | Ladybug or newer (optional) |

### 1. Clone

```bash
git clone https://github.com/angusdevgo/MyGitHub.git
cd MyGitHub
```

### 2. Configure the SDK Path

```properties
# local.properties  (create this file, it is gitignored)
sdk.dir=/path/to/your/Android/Sdk
```

### 3. Build a Debug APK

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### 4. Build a Signed Release APK (Optional)

Release signing credentials are **never** committed to this repository. Add them to your local `local.properties`:

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

Then generate a keystore and build:

```bash
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias your_key_alias -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

> If no signing credentials are supplied, Gradle produces an **unsigned** release APK instead of failing — so anyone can compile this project on a clean machine.

### 5. Install on a Device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🔐 Authentication Model

MyGitHub supports exactly two login paths. Both are designed so that **no secret ever ships inside the APK**.

### Path A — GitHub OAuth Device Flow (Recommended)

```
  MyGitHub App                      GitHub                        Your Browser
       │                               │                               │
       │  POST /login/device/code      │                               │
       │  (client_id only)             │                               │
       ├──────────────────────────────►│                               │
       │                               │                               │
       │◄──────────────────────────────┤                               │
       │  user_code: 9D6F-56E7         │                               │
       │                               │                               │
       │  ┌─ Display the code ────────────────────────────────────────►│
       │  │                            │        User enters 9D6F-56E7  │
       │  │                            │◄──────────────────────────────┤
       │  │                            │        User clicks Authorize  │
       │  │                            │                               │
       │  └─ Poll every 5 s ──────────►│                               │
       │     POST /login/oauth/access_token                           │
       │     (client_id + device_code) │                               │
       │◄──────────────────────────────┤                               │
       │     access_token              │                               │
       ▼                               │                               │
  EncryptedSharedPreferences           │                               │
```

**Why this flow?** Device Flow requires only a public `client_id` — no `client_secret`. The user authenticates inside GitHub's own web page, so the app never sees a password, and a decompiled APK leaks nothing usable.

**Why not OAuth Web Flow?** Web Flow mandates a `client_secret` at token exchange. Keeping that secret safe requires a backend server. MyGitHub deliberately has no backend.

### Path B — Personal Access Token

For networks where `github.com` (but not `api.github.com`) is blocked, PAT login is the pragmatic fallback. The app provides an inline guide and a one-tap link that opens GitHub's token creation page **with `repo`, `user`, and `read:org` scopes pre-filled**.

The token is stored in `EncryptedSharedPreferences`, encrypted with an AES-256-GCM key held in the Android Keystore (hardware-backed where available).

### Built-in 2FA Authenticator

Once GitHub 2FA is enabled on your account, MyGitHub can serve as the authenticator app:

1. Open **Profile → Security Center**
2. Import the secret by **scanning the QR code** GitHub displays, or by **pasting the Base32 setup key**
3. The app immediately renders the live 6-digit code with a countdown ring

The code is computed locally with HMAC-SHA1 per RFC 6238 and validated against an independent reference implementation. Your authenticator secret is stored in app-private storage and **survives logout** — logging out only clears the GitHub session token, never your 2FA enrollment.

---

## 📋 Project Structure

<details>
<summary>📁 Expand full directory tree</summary>

```
MyGitHub/
├── app/
│   ├── build.gradle.kts                ← Module config (namespace, deps, release signing)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mygithub/lab/
│       │   ├── MainActivity.kt              ← Entry point + global Coil image loader
│       │   │
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   └── GitHubApi.kt         ← Retrofit interface + all DTOs
│       │   │   ├── auth/
│       │   │   │   ├── DeviceFlowAuth.kt    ← RFC 8628 device flow + token polling
│       │   │   │   └── TokenStore.kt        ← AES-256-GCM token vault
│       │   │   ├── local/
│       │   │   │   └── AppDatabase.kt       ← Room entities + DAOs
│       │   │   └── repo/
│       │   │       └── GitHubRepository.kt  ← Cache-first Flows, parallel loaders,
│       │   │                                   recommendation engine, state cache
│       │   │
│       │   ├── security/totp/
│       │   │   ├── TotpEngine.kt            ← RFC 6238 TOTP + RFC 4648 Base32
│       │   │   └── TotpStore.kt             ← Persistent authenticator secret vault
│       │   │
│       │   └── ui/
│       │       ├── MyGitHubApp.kt           ← NavHost + overlay scaffold
│       │       ├── components/
│       │       │   ├── KomiComponents.kt           ← Card / chip / repo-card primitives
│       │       │   ├── KomiFloatingBottomBar.kt    ← GPU-animated capsule nav bar
│       │       │   ├── KomiPullRefreshIndicator.kt ← Custom refresh indicator
│       │       │   └── UrlLauncher.kt              ← Safe external-link routing
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
│       │           ├── Theme.kt             ← Nord dark / light color schemes
│       │           └── Type.kt              ← Typography scale
│       │
│       └── res/                             ← Icons, colors, strings
│
├── relay/                               ← OPTIONAL Cloudflare Worker relay
│   ├── src/                             ← (2FA cross-device approval, not required)
│   ├── package.json
│   └── wrangler.toml
│
├── docs/images/                         ← Screenshots and diagrams
├── keystore/                            ← ⚠️ GITIGNORED — your signing key lives here
├── gradle/                              ← Gradle wrapper
│
├── build.gradle.kts                     ← Root build script
├── settings.gradle.kts
├── gradle.properties
├── local.properties                     ← ⚠️ GITIGNORED — SDK path + signing secrets
│
├── LICENSE                              ← GPL-3.0
├── VERSION                              ← Current version string
├── CHANGELOG.md
├── README.md                            ← You are here (English)
└── README.zh.md                         ← 中文文档
```

</details>

---

## 🧩 Optional: Cloudflare Relay

The `relay/` directory contains an **optional** Cloudflare Worker implementing a blind relay for cross-device 2FA approval (approve a login on a new device by matching a two-digit challenge on an already-trusted device).

**It is not required for any feature described above.** MyGitHub works fully without it.

The relay is designed as a *blind* intermediary: it forwards AES-256-GCM envelopes it cannot decrypt, approval actions are signed with an Ed25519 key held in the device's secure element, and the challenge is verified locally on the requesting device. The Worker stores no tokens, keys, or plaintext.

Deployment (≈2 minutes, free tier):

```bash
cd relay
npm install
npx wrangler login
npx wrangler deploy
```

See [`relay/README.md`](relay/README.md) for the full security model.

---

## ⚡ Performance Notes

Measured on a Xiaomi 2206123SC (Android 13) via `dumpsys gfxinfo`:

| Screen | Total Frames | Janky Frames | 99th Percentile |
|---|---|---|---|
| Notifications feed | 445 | **3 (0.67%)** | 10 ms |
| Profile screen | 577 | **2 (0.35%)** | 7 ms |

Techniques applied:

- **Stable keys on every lazy item** — Compose reuses composition instead of rebuilding rows on scroll.
- **Memoized derived state** — `remember(updatedAt) { relativeTimeFromIso(updatedAt) }` prevents re-parsing ISO timestamps on every recomposition.
- **`derivedStateOf` for filtered lists** — filtering is recomputed only when inputs change, not on every parent recomposition.
- **Localized recomposition for the TOTP timer** — a 1 Hz clock confined to a leaf composable.
- **Global Coil cache** — 50 MB memory + 100 MB disk, `crossfade(false)` with explicit `memoryCacheKey` to eliminate decode spikes during fast scrolling.
- **Explicit `LazyListState`** — pull-to-refresh and tab switches reset scroll position deterministically.
- **Explicit `PullToRefreshState`** — refresh animation is driven by our own state object, so the indicator never stalls.

---

## 🗺 Roadmap

- [x] Repository browser with README rendering
- [x] Issue detail with comments, close / reopen
- [x] Personalized discovery feed with on-device inference
- [x] Trending rankings (daily / weekly / monthly)
- [x] Notification center with accurate issue state
- [x] Aggregated received-activity feed
- [x] Built-in RFC 6238 TOTP authenticator with QR import
- [x] Floating capsule navigation with GPU-animated indicator
- [ ] Code browsing with syntax highlighting
- [ ] Pull request review and inline comments
- [ ] Multi-account support
- [ ] Optional cross-device 2FA approval via the bundled relay
- [ ] Material You dynamic color support

---

## 🤝 Contributing

Issues and pull requests are welcome.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Before submitting**, please make sure:

- `./gradlew assembleRelease` completes without new warnings
- No credentials, tokens, keystores, or personal identifiers are included in the diff
- New UI respects both dark and light themes

---

## ⚠️ Disclaimer

This project is intended for **personal use and educational purposes**.

- It is **not affiliated with, endorsed by, or sponsored by GitHub, Inc.**
- It uses only **publicly documented GitHub REST API** endpoints
- **You are responsible** for complying with the [GitHub Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) and [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies)
- Your credentials and tokens are stored **only on your device** and are never transmitted to any server controlled by this project
- Rate limits and API quota consumption are entirely yours

Use it on accounts you own or are authorized to access.

---

## 📜 License

Licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

---

<p align="center">
  <sub>Built with Kotlin · Jetpack Compose · Material 3 · Room · Retrofit · CameraX</sub>
</p>

<p align="center">
  <sub>⭐ If MyGitHub is useful to you, consider giving it a star.</sub>
</p>
