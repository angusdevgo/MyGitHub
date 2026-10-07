# Changelog

All notable changes to MyGitHub are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.0.1] — 2026-10-07

**Initial public release.**

### Added

- **Repository browser** — personal repository list with filters (all / public / private / fork), star and fork counts, language indicators, and last-updated timestamps.
- **Repository detail** — theme-aware README rendering via WebView with injected CSS for dark and light modes, releases list, issue list, and star / unstar actions.
- **Issue detail** — full thread view, comment posting, and `Close` / `Reopen` state transitions.
- **Personalized discovery feed** — on-device recommendation engine that infers dominant language and topics from your starred repositories and surfaces adjacent projects.
- **Trending rankings** — daily, weekly, and monthly leaderboards with graded star thresholds and language filtering.
- **Notification center** — full GitHub notification inbox with reason filters (all / unread / mention / assign), accurate live issue-state reconciliation, optimistic read-marking, and 30-day retention.
- **Received activity feed** — aggregated stargazers, forks, and inbound issues across your repositories, exposing activity GitHub's own events API does not provide.
- **Built-in 2FA authenticator** — RFC 6238 TOTP engine (HMAC-SHA1, 30 s, 6 digits) with QR import via CameraX + ML Kit, manual Base32 / `otpauth://` URI import, live countdown ring, and one-tap copy.
- **Dual-theme design system** — Nord-inspired deep-space dark theme with a Cobalt accent, plus a matching light theme, switchable at runtime.
- **Floating capsule navigation** — overlay bottom bar with a GPU-driven spring-animated indicator that does not trigger recomposition.
- **Custom pull-to-refresh indicator** — explicit refresh state with a continuously animated indicator.
- **In-app self-updater with SHA-256 integrity verification** — automatically checks GitHub Releases on launch, parses SemVer tags, streams the APK to cache with live progress, computes and validates the SHA-256 digest against the release notes to prevent tampering, and invokes the system package installer via `FileProvider`.

### Authentication

- **GitHub OAuth Device Flow (RFC 8628)** — credential-less authorization requiring only a public client ID. No `client_secret` is shipped in the APK.
- **Personal Access Token login** — fallback path for restricted networks, with a one-tap link that pre-fills the `repo`, `user`, and `read:org` scopes.
- **Encrypted token vault** — AES-256-GCM via `EncryptedSharedPreferences` with an Android Keystore–backed master key.

### Security

- Release signing credentials are read from `local.properties` or environment variables; **no secrets are committed to the repository**.
- The `keystore/` directory, `local.properties`, and all build outputs are gitignored.
- All network traffic goes directly from the device to `api.github.com` over TLS. No proxy, relay, or telemetry endpoint is involved.

### Performance

- Cache-first `Flow` architecture with dual emission (Room, then network) so lists render instantly and refresh silently.
- Stable keys on all lazy list items to enable composition reuse during scroll.
- Memoized time formatting and `derivedStateOf` for filtered lists.
- Localized recomposition for the 1 Hz TOTP countdown.
- Global Coil image cache (50 MB memory + 100 MB disk) with `crossfade(false)` to eliminate decode spikes.
- Explicit `LazyListState` so pull-to-refresh and tab switches reset scroll deterministically.

Measured on a Xiaomi 2206123SC (Android 13):

| Screen | Total frames | Janky frames | 99th percentile |
|---|---|---|---|
| Notifications feed | 445 | 3 (0.67%) | 10 ms |
| Profile screen | 577 | 2 (0.35%) | 7 ms |

### Optional

- **Cloudflare Worker relay** (`relay/`) — blind relay for cross-device 2FA approval. Not required for any core feature; stores no tokens, keys, or plaintext.

### Notes

- **Minimum Android version:** 8.0 (API 26)
- **Target SDK:** 34
- **Package:** `com.mygithub.lab`
