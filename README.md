<h1 align="center">MyGitHub</h1>

<div align="center">

[English](README.md) | [中文](README.zh.md)

<img src="docs/images/app_icon.png" alt="MyGitHub" width="150" />

<p><strong>A GitHub client that runs entirely on your phone</strong></p>
<p>Android · Zero-Server · Local Vault · TOTP · Jetpack Compose</p>

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

MyGitHub is a native Android client for GitHub, written in Kotlin with Jetpack Compose.

It does the things you actually open GitHub for on a phone — browse your repos, read and reply to issues, fork projects, watch what's happening on your own repositories — and it does them **without ever talking to a server that isn't GitHub's**.

About **10,000 lines of Kotlin across 37 files**. Small enough to read in an afternoon, and to know exactly what it does with your credentials.

---

## What this is

That last part is the whole point, so let's be concrete:

- **There is no backend.** Not a free tier, not a serverless function, not a proxy. Nothing to deploy, nothing to pay for, nothing that can go down.
- **Your token stays on your device.** Encrypted with AES-256-GCM, key held in the Android Keystore.
- **Every request goes straight to `api.github.com` over TLS.** Verify it by reading the source, or by watching your own network.
- **It comes with a 2FA authenticator.** RFC 6238 TOTP, computed offline. You can delete Google Authenticator.

---

## Why you might want it

**You don't want to pay for a client.** Most third-party GitHub clients need a backend because OAuth's Web Flow requires a `client_secret` at token exchange. Shipping that secret in an APK means anyone who decompiles it can impersonate your app. So those projects run a server — and you pay for it.

MyGitHub sidesteps this by not supporting Web Flow at all. It only does **Device Flow** (which needs no secret) and **PAT login** (which needs nothing at all). That single constraint removes the entire backend requirement.

**You want an authenticator that won't lose your keys.** The built-in TOTP engine imports the same secret Google Authenticator uses. It survives logout, app updates, and process death — because it's stored in a plain app-private file with synchronous writes, not in a fragile Keystore-wrapped store that can silently reset on some OEM ROMs.

**You're tired of stuttering lists.** Cache-first architecture: every list renders from local storage first, then refreshes silently behind you. Measured jank on a Xiaomi 2206123SC: **0.35%** on the profile screen, **0.67%** on notifications. Zero missed vsyncs in the notification feed.

**You want to read the code before you trust it.** 37 files. No obfuscated SDKs, no analytics, no network calls you didn't ask for. Search for `api.github.com` — that's every outbound request in the app.

---

## What it does

<table>
<tr><td width="50%" valign="top">

**Browse**

- Personalized discovery feed — inferred from your stars, computed on-device
- Trending rankings (daily / weekly / monthly) with language filters
- Your repositories with stars, forks, language, last activity
- Search across repositories and issues

</td><td valign="top">

**Interact**

- Read full issue threads with comments
- Post comments
- Close and reopen issues
- **Star / unstar with instant optimistic UI** — no page reload, no waiting
- **Fork in-app** — pick your own repo name, optionally copy only the default branch
- Read READMEs with proper dark/light theming

</td></tr>
<tr><td valign="top">

**Stay informed**

- **Issues tab** — every Issue and PR across your own repositories, filterable by `All` / `Open` / `Closed`
- **Activity tab** — real Star / Fork / Issue / PR events on your repos, with an aggregate overview (total stars, forks, open issues) and "since you last looked: +N stars" delta
- No notification-inbox guesswork: state comes straight from the real API

</td><td valign="top">

**Stay secure**

- Built-in RFC 6238 TOTP authenticator
- Import by QR scan (CameraX + ML Kit) or manual Base32 / `otpauth://` paste
- Live countdown, one-tap copy
- Survives logout — your 2FA enrollment is never cleared
- **In-app self-updater with SHA-256 integrity verification** — checks GitHub Releases on launch, verifies the checksum before install
- **Network acceleration & proxy engine** — Direct, public GitHub CDN mirrors, and custom HTTP/SOCKS5 proxies, with a live latency probe and automatic fallback to direct when a proxy tunnel fails

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

The version comes from the `VERSION` file at the repo root — `versionName` and `versionCode` are both derived from it at build time.

### Run the tests

```bash
./gradlew testReleaseUnitTest
```

---

## Signing in

MyGitHub gives you two ways in. Both are designed so that **no secret ever ships in the APK**.

### Device Flow — recommended

This is the same mechanism `gh auth login` and smart TVs use.

```
1.  Tap "Sign in with GitHub"
2.  The app shows you a code:  9D6F-56E7
3.  The code is copied to your clipboard and the browser opens
4.  Enter the code, tap Authorize
5.  The app notices and logs you in
```

You type your password on GitHub's own website. The app never sees it. The only credential involved is a **public client ID** — decompiling the APK gets an attacker nothing.

The code card shows a **live countdown** and a **Cancel** button, so you're never stuck waiting: abort at any point and pick a different login method. If the browser can't be launched automatically on your ROM, the card also gives you copy buttons and manual steps.

### Personal Access Token — fallback

Some networks block `github.com` but allow `api.github.com`. Device Flow can't work there, so PAT login exists as a fallback.

The app walks you through it and offers a link with **`repo`, `user`, and `read:org` already selected**.

### Enabling 2FA

Once you've signed in, MyGitHub can also be your authenticator:

1. Go to **Profile → Two-Factor Auth**
2. Scan the QR code GitHub shows you, or paste the Base32 setup key
3. The 6-digit code appears with a live countdown ring

The code is computed locally with HMAC-SHA1 per RFC 6238. Your secret is stored in app-private storage and **is not touched when you log out** — logging out only clears the session token.

---

## App structure

Five bottom tabs, plus full-screen secondary pages.

| Tab | What's in it |
|---|---|
| **Home** | Personalized recommendations, category chips, search entry |
| **Rankings** | Daily / weekly / monthly trending with language filters |
| **Info** | `Issues` (all your repos, All/Open/Closed) and `Activity` (star/fork/issue events) |
| **Repos** | Your repositories with public / private / fork filters |
| **Profile** | Identity card, stars, recent views, two-factor auth, app settings, about |

**Profile → Settings** holds appearance theme, account details, and the network/proxy engine.
**Profile → About** holds the app icon, version, update check, open-source license, source and issue links.

---

## How it's built

```
┌─────────────────────────────────────────────────────┐
│  Compose UI                                         │
│  Home · Rankings · Info · Repos · Profile           │
│  RepoDetail · IssueDetail · Settings · About · …    │
└────────────────────────┬────────────────────────────┘
                         │  Flow<Result<T>>
┌────────────────────────▼────────────────────────────┐
│  GitHubRepository                                   │
│  · Cache-first flows (Room, then network)           │
│  · Parallel loaders (supervisorScope + async)       │
│  · Bounded fan-out (Semaphore) over owned repos     │
└───────┬──────────────────────────────┬──────────────┘
        │                              │
┌───────▼──────────┐      ┌────────────▼──────────────┐
│  Retrofit/OkHttp │      │  Room + Keystore          │
│  → api.github.com│      │  repo_cache · star_tags   │
│    over TLS      │      │  recent_views             │
│  · proxy engine  │      │  EncryptedSharedPreferences│
│  · auto fallback │      └───────────────────────────┘
└──────────────────┘
        ┌──────────────────────────────────┐
        │  TOTP (on-device, offline)       │
        │  RFC 6238 · Base32 · ML Kit scan │
        └──────────────────────────────────┘
```

### Decisions worth explaining

**Cache-first flows instead of suspend functions.**
Every list emits twice: once from Room (`fromCache = true`), once from the network. You see content in under 100 ms. The refresh lands silently and only re-emits if the set of IDs actually changed — so lists never jump under your thumb.

**Star toggling is optimistic, not synchronous.**
The obvious implementation is: tap → await the network → refetch the README → rebuild the whole page → reload the WebView. That's two round trips and a full re-render for a one-bit change, and it feels exactly as slow as it sounds. Instead, the tap immediately runs injected JavaScript to flip the button's highlight, label and counter, then commits the request in the background and rolls back (with a toast) only if it fails.

**Issues come from the API, not the inbox.**
GitHub's `/notifications` endpoint returns *your subscription inbox*, not "all issues in your repos" — an issue you opened yourself doesn't generate a notification. So the Info tab queries `/repos/{owner}/{repo}/issues` across your own repositories with bounded concurrency, and merges PRs (which GitHub reports through the same endpoint) using the `pull_request` marker.

**Activity uses the repo event stream, not the stargazer list.**
`/repos/{o}/{r}/stargazers` has no sort parameter and returns oldest-first, so "who starred me recently" is effectively unanswerable from page 1. The repo events feed gives real actor-level Star/Fork/Issue/PR events in reverse-chronological order, and an aggregate pass over your repo objects supplies exact totals plus a "since last look" delta.

**The proxy can't brick the app.**
On a phone that already runs a system-wide VPN, stacking an app-level proxy on top is redundant — and if that proxy returns something like HTTP 402 on `CONNECT`, every request dies. A fallback interceptor detects proxy-tunnel failures and retries once over a dedicated direct client.

**The `Accept` header is only ever a default.**
Setting `Accept` unconditionally in an interceptor silently overwrote the call-site values that `readme` and `contents` rely on, so those endpoints returned JSON metadata instead of rendered HTML and every repository page broke. The interceptor now only fills in a value when the caller didn't specify one.

**`graphicsLayer { translationX }` for the bottom bar indicator.**
Animating an offset with `animateDpAsState` triggers layout every frame. Driving `translationX` inside `graphicsLayer` keeps the whole animation on the GPU, with zero recomposition.

**The TOTP clock lives in a leaf composable.**
A 1 Hz timer in the screen root recomposes the entire page 60 times a minute. Putting it inside `TotpQuickCard` means only that card recomposes.

**The 2FA secret uses plain app-private storage.**
An earlier version used `EncryptedSharedPreferences`. On some OEM ROMs, a Keystore reset causes it to silently return `null` — which means the user loses their 2FA enrollment and gets locked out. The secret now lives in a standard app-private file written with `commit()`. App-private storage is already sandboxed by the OS; the extra encryption layer added fragility without adding real protection.

**In-app updates verify cryptographic checksums before invoking the installer.**
Downloading an arbitrary binary and handing it to the package manager without validation invites tampering. MyGitHub extracts the author's published SHA-256 digest from the GitHub Release body, hashes the incoming byte stream on the fly, and purges any payload that fails comparison before touching `FileProvider` or the system installer.

**Scrolling stays smooth because recomposition is rationed.**
Every lazy list item has a stable key, so Compose reuses composition instead of rebuilding rows. Timestamps go through `remember(updatedAt) { relativeTimeFromIso(updatedAt) }` rather than re-parsing ISO strings on each pass. Filtered lists use `derivedStateOf`. The 1 Hz TOTP countdown is confined to a leaf composable. Coil caches images globally (50 MB memory, 100 MB disk) with an explicit `memoryCacheKey` to eliminate decode spikes mid-scroll.

---

## Project layout

```
MyGitHub/
├── VERSION                          single source of truth for the version
├── scripts/release.py               one-command version bump + release pipeline
├── app/src/main/java/com/mygithub/lab/
│   ├── MainActivity.kt              entry point
│   ├── data/
│   │   ├── api/GitHubApi.kt         Retrofit interface + DTOs
│   │   ├── auth/                    Device Flow, token vault
│   │   ├── local/AppDatabase.kt     Room entities and DAOs
│   │   ├── model/                   UI-facing models
│   │   ├── network/ProxyManager.kt  proxy modes, mirrors, latency probe
│   │   ├── repo/GitHubRepository.kt cache-first flows, parallel loaders
│   │   ├── update/                  version comparison, APK download + hashing
│   │   └── util/                    pure helpers (all unit-tested)
│   ├── security/totp/               RFC 6238 engine, persistent secret store
│   └── ui/
│       ├── MyGitHubApp.kt           NavHost + overlay scaffold
│       ├── components/              design system primitives
│       ├── screens/                 one package per screen
│       └── theme/                   Nord-derived color schemes
│
├── app/src/test/                    JVM unit tests
└── docs/images/                     project assets
```

---

## Roadmap

- [x] Repository browsing with themed README rendering
- [x] Issue detail, comments, close / reopen
- [x] On-device personalized recommendations
- [x] Trending rankings
- [x] Issues and activity tabs driven by real API state
- [x] Built-in 2FA authenticator with QR import
- [x] Floating capsule navigation
- [x] Instant star toggle and in-app forking
- [x] Network proxy engine with mirrors and latency probe
- [x] In-app self-update with SHA-256 verification
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
Device Flow works anywhere `github.com` is reachable. If your network blocks it, use PAT login — that only needs `api.github.com`. The app tells you which path to take when a connection fails, and the proxy engine can also route through public CDN mirrors or your own HTTP/SOCKS5 proxy.

**Will I lose my 2FA codes if I log out?**
No. The authenticator secret is stored separately from your session and is only cleared if you explicitly unbind it in Two-Factor Auth.

**Can I cancel a login that's waiting for authorization?**
Yes. The Device Flow card has a Cancel button and a countdown; the polling job is torn down immediately and when you leave the screen.

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
./gradlew testReleaseUnitTest  # unit tests must pass
./gradlew assembleRelease      # should build cleanly
git commit -m "feat: your feature"
git push origin feature/your-feature
```

Before opening a PR, please check:

- `./gradlew assembleRelease` completes without new warnings
- `./gradlew testReleaseUnitTest` passes
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

## Community

Questions, feedback, and release announcements live in the [**LINUX DO**](https://linux.do/) community — a Chinese-speaking forum for developers and tech enthusiasts.

---

<div align="center">

**Kotlin · Jetpack Compose · Material 3 · Room · Retrofit · CameraX**

⭐ If this is useful to you, a star helps others find it.

<a href="https://linux.do/">
  <img src="https://img.shields.io/badge/Join%20us%20on-LINUX%20DO-23272A?style=for-the-badge" alt="LINUX DO" />
</a>

</div>
