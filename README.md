<h1 align="center">MyGitHub</h1>

<div align="center">

[English](README.md) | [中文](README.zh.md)

<img src="docs/images/theme.png" alt="MyGitHub" width="500" />

<p><strong>A GitHub client that runs entirely on your phone</strong></p>
<p>Android · Zero-Server · Local Vault · TOTP · Jetpack Compose</p>

[![Release](https://img.shields.io/github/v/release/angusdevgo/MyGitHub?style=flat-square&color=3B5BDB)](https://github.com/angusdevgo/MyGitHub/releases)
[![Downloads](https://img.shields.io/github/downloads/angusdevgo/MyGitHub/total?style=flat-square)](https://github.com/angusdevgo/MyGitHub/releases)
[![Last Commit](https://img.shields.io/github/last-commit/angusdevgo/MyGitHub?style=flat-square)](https://github.com/angusdevgo/MyGitHub/commits)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)

[![Platform](https://img.shields.io/badge/android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/jetpack%20compose-material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Community](https://img.shields.io/badge/community-LINUX%20DO-23272A?style=flat-square)](https://linux.do/)

</div>

---

MyGitHub is a native Android client for GitHub, written in Kotlin with Jetpack Compose.
It does the things you actually open GitHub for on a phone — browse your repos, read issues, check notifications, spot trends — and it does them **without ever talking to a server that isn't GitHub's**.

It's about 7,000 lines of Kotlin across 27 files. Small enough that you can read all of it in an afternoon and know exactly what it does with your credentials.

---

## What this is

That last part is the whole point, so let's be concrete:

- **There is no backend.** Not a free tier, not a serverless function, not a proxy. Nothing to deploy, nothing to pay for, nothing that can go down.
- **Your token stays on your device.** Encrypted with AES-256-GCM, key held in the Android Keystore.
- **Every request goes straight to `api.github.com` over TLS.** You can verify this by reading the source, or by watching your own network.
- **It comes with a 2FA authenticator.** RFC 6238 TOTP, computed offline. You can delete Google Authenticator.

It's about 7,000 lines of Kotlin across 27 files. Small enough that you can read all of it in an afternoon and know exactly what it does with your credentials.

---

## Why you might want it

**You don't want to pay for a client.** Most third-party GitHub clients need a backend because OAuth's Web Flow requires a `client_secret` at token exchange. Shipping that secret in an APK means anyone who decompiles it can impersonate your app. So those projects run a server — and you pay for it.

MyGitHub sidesteps this by not supporting Web Flow at all. It only does **Device Flow** (which needs no secret) and **PAT login** (which needs nothing at all). That single constraint removes the entire backend requirement.

**You want an authenticator that won't lose your keys.** The built-in TOTP engine imports the same secret Google Authenticator uses. It survives logout, app updates, and process death — because it's stored in a plain app-private file with synchronous writes, not in a fragile Keystore-wrapped store that can silently reset on some OEM ROMs.

**You're tired of stuttering lists.** Cache-first architecture: every list renders from local storage first, then refreshes silently behind you. Measured jank on a Xiaomi 2206123SC: **0.35%** on the profile screen, **0.67%** on notifications. Zero missed vsyncs in the notification feed.

**You want to read the code before you trust it.** 27 files. No obfuscated SDKs, no analytics, no network calls you didn't ask for. Search for `api.github.com` — that's every outbound request in the app.

---

## What it does

<table>
<tr><td width="50%" valign="top">

**Browse**

- Personalized discovery feed — inferred from your stars, computed on-device
- Trending rankings (daily / weekly / monthly) with language filters
- Your repositories with stars, forks, language, last activity
- Search across repositories and issues

</td><td width="50%" valign="top">

**Interact**

- Read full issue threads with comments
- Post comments
- Close and reopen issues
- Star and unstar repositories
- Read READMEs with proper dark/light theming

</td></tr>
<tr><td valign="top">

**Stay informed**

- Full notification inbox with reason filters
- **Accurate issue state** — queried from the real API, not guessed from notification text
- Aggregated activity: who starred, forked, or opened issues on your repos
- Mark as read, synced to GitHub
- 30-day retention

</td><td valign="top">

**Stay secure**

- Built-in RFC 6238 TOTP authenticator
- Import by QR scan (CameraX + ML Kit) or manual Base32 / `otpauth://` paste
- Live countdown, one-tap copy
- Survives logout — your 2FA enrollment is never cleared

</td></tr>
</table>

---

## Getting started

### Requirements

- JDK 17
- Android SDK with API 34
- An Android 8.0+ device (API 26)

### Build it

```bash
git clone https://github.com/angusdevgo/MyGitHub.git
cd MyGitHub

# Point Gradle at your SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# Build
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build a signed release

Signing credentials are **never** committed to this repo. Put them in `local.properties`:

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_password
```

Create a keystore if you don't have one, then build:

```bash
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias your_alias -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease
```

> Skip this entirely and Gradle produces an *unsigned* release APK instead of failing. Anyone can compile this project on a clean machine.

---

## Signing in

MyGitHub gives you two ways in. Both are designed so that **no secret ever ships in the APK**.

### Device Flow — recommended

This is the same mechanism `gh auth login` and smart TVs use.

```
1.  Tap "Sign in with GitHub"
2.  The app shows you a code:  9D6F-56E7
3.  Open github.com/login/device in any browser
4.  Enter the code, tap Authorize
5.  The app notices and logs you in
```

You type your password on GitHub's own website. The app never sees it. The only credential involved is a **public client ID** — decompiling the APK gets an attacker nothing.

### Personal Access Token — fallback

Some networks block `github.com` but allow `api.github.com`. Device Flow can't work there, so PAT login exists as a fallback.

The app walks you through it and opens GitHub's token page with **`repo`, `user`, and `read:org` already selected**.

### Enabling 2FA

Once you've signed in, MyGitHub can also be your authenticator:

1. Go to **Profile → Security Center**
2. Scan the QR code GitHub shows you, or paste the Base32 setup key
3. The 6-digit code appears with a live countdown ring

The code is computed locally with HMAC-SHA1 per RFC 6238. Your secret is stored in app-private storage and **is not touched when you log out** — logging out only clears the session token.

---

## How it's built

```
┌─────────────────────────────────────────────────────┐
│  Compose UI                                         │
│  Home · Rankings · Notifications · Repos · Profile  │
│  RepoDetail · IssueDetail · Security · Login        │
└────────────────────────┬────────────────────────────┘
                         │  Flow<Result<T>>
┌────────────────────────▼────────────────────────────┐
│  GitHubRepository                                   │
│  · Cache-first flows (Room, then network)           │
│  · Parallel loaders (supervisorScope + async)       │
│  · In-memory caches (username, issue state)         │
└───────┬──────────────────────────────┬──────────────┘
        │                              │
┌───────▼──────────┐      ┌────────────▼──────────────┐
│  Retrofit/OkHttp │      │  Room + Keystore          │
│  → api.github.com│      │  repo_cache · notifications│
│    over TLS      │      │  star_tags · recent_views  │
└──────────────────┘      │  EncryptedSharedPreferences│
                          └───────────────────────────┘
        ┌──────────────────────────────────┐
        │  TOTP (on-device, offline)       │
        │  RFC 6238 · Base32 · ML Kit scan │
        └──────────────────────────────────┘
```

### Decisions worth explaining

**Cache-first flows instead of suspend functions.**
Every list emits twice: once from Room (`fromCache = true`), once from the network. You see content in under 100 ms. The refresh lands silently and only re-emits if the set of IDs actually changed — so lists never jump under your thumb.

**`graphicsLayer { translationX }` for the bottom bar indicator.**
Animating an offset with `animateDpAsState` triggers layout every frame. Driving `translationX` inside `graphicsLayer` keeps the whole animation on the GPU, with zero recomposition.

**The TOTP clock lives in a leaf composable.**
A 1 Hz timer in the screen root recomposes the entire page 60 times a minute. Putting it inside `TotpQuickCard` means only that card recomposes.

**Issue state is queried, not guessed.**
GitHub's notification payload doesn't include issue state. Instead of parsing titles for the word "closed", the app queries the real issue endpoint for visible notifications and merges results through an in-memory cache. Your notification list shows `Open` or `Closed` correctly — including for issues you closed five minutes ago.

**The 2FA secret uses plain app-private storage.**
An earlier version used `EncryptedSharedPreferences`. On some OEM ROMs, a Keystore reset causes it to silently return `null` — which means the user loses their 2FA enrollment and gets locked out. The secret now lives in a standard app-private file written with `commit()`. App-private storage is already sandboxed by the OS; the extra encryption layer added fragility without adding real protection.

**Aggregation uses `supervisorScope`.**
The activity feed fans out across repositories and endpoints. `supervisorScope` isolates failures so one unreachable repo can't blank the entire feed.

**Scrolling stays smooth because recomposition is rationed.**
Every lazy list item has a stable key, so Compose reuses composition instead of rebuilding rows. Timestamps go through `remember(updatedAt) { relativeTimeFromIso(updatedAt) }` rather than re-parsing ISO strings on each pass. Filtered lists use `derivedStateOf`, so filtering runs only when its inputs change. The 1 Hz TOTP countdown is confined to a leaf composable instead of ticking the whole screen. Coil caches images globally (50 MB memory, 100 MB disk) with `crossfade(false)` and an explicit `memoryCacheKey` to eliminate decode spikes mid-scroll.

---

## Project layout

```
MyGitHub/
├── app/src/main/java/com/mygithub/lab/
│   ├── MainActivity.kt              entry point
│   ├── data/
│   │   ├── api/GitHubApi.kt         Retrofit interface + DTOs
│   │   ├── auth/                    Device Flow, encrypted token vault
│   │   ├── local/AppDatabase.kt     Room entities and DAOs
│   │   └── repo/GitHubRepository.kt cache-first flows, parallel loaders
│   ├── security/totp/               RFC 6238 engine, persistent secret store
│   └── ui/
│       ├── MyGitHubApp.kt           NavHost + overlay scaffold
│       ├── components/              design system primitives
│       ├── screens/                 one package per screen
│       └── theme/                   Nord-derived color schemes
│
├── relay/                           optional Cloudflare Worker (see below)
└── docs/images/                     project assets
```

### The optional relay

`relay/` contains a Cloudflare Worker that implements **blind** cross-device 2FA approval — you tap a two-digit number on an already-trusted device to approve a login elsewhere.

**Nothing in the app requires it.** It's an experiment in doing 2FA approval without a trusted server:

- The Worker forwards AES-256-GCM envelopes it cannot decrypt
- Approval actions are signed with an Ed25519 key in the device's secure element
- The challenge is verified locally on the requesting device
- The Worker stores no tokens, keys, or plaintext

Deploy it in about two minutes if you're curious — see [`relay/README.md`](relay/README.md).

---

## Roadmap

- [x] Repository browsing with themed README rendering
- [x] Issue detail, comments, close / reopen
- [x] On-device personalized recommendations
- [x] Trending rankings
- [x] Notifications with accurate issue state
- [x] Aggregated received-activity feed
- [x] Built-in 2FA authenticator with QR import
- [x] Floating capsule navigation
- [ ] Code browsing with syntax highlighting
- [ ] Pull request review
- [ ] Multi-account support
- [ ] Material You dynamic color

---

## FAQ

**Does this need any server or subscription?**
No. There is no backend. Your token authenticates every request directly against GitHub.

**Is my token safe?**
It's encrypted with AES-256-GCM under a key held in the Android Keystore, stored in `EncryptedSharedPreferences`, and never transmitted anywhere except `api.github.com`.

**Does it work without a proxy?**
Device Flow works anywhere `github.com` is reachable. If your network blocks `github.com`, use PAT login — it only needs `api.github.com`. The app tells you which path to take when a connection fails.

**Will I lose my 2FA codes if I log out?**
No. The authenticator secret is stored separately from your session and is only cleared if you explicitly unbind it in Security Center.

**Why not OAuth Web Flow?**
It requires a `client_secret` at token exchange, which means either shipping that secret in the APK (unsafe) or running a backend (costs money). Device Flow and PAT login avoid both problems.

**Can I use this with GitHub Enterprise?**
Not currently. The API base URL is hardcoded to `api.github.com`. It would be a small change — patches welcome.

---

## Contributing

Issues and pull requests are welcome.

```bash
git checkout -b feature/your-feature
# make your changes
./gradlew assembleRelease    # should build cleanly
git commit -m "feat: your feature"
git push origin feature/your-feature
```

Before opening a PR, please check:

- `./gradlew assembleRelease` completes without new warnings
- No credentials, tokens, keystores, or personal identifiers in the diff
- New UI works in both dark and light themes

---

## Disclaimer

This project is for **personal use and learning**.

- Not affiliated with, endorsed by, or sponsored by GitHub, Inc.
- Uses only publicly documented GitHub REST API endpoints
- You are responsible for complying with the [GitHub Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) and [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies)
- Your credentials stay on your device and are never sent to any server controlled by this project
- Rate limits are yours to manage

Use it on accounts you own or are authorized to access.

---

## License

**GPL-3.0** — see [LICENSE](LICENSE).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

---

<div align="center">

**Kotlin · Jetpack Compose · Material 3 · Room · Retrofit · CameraX**

⭐ If this is useful to you, a star helps others find it.

</div>
