# Sift App — Agent Guide

## Tech Stack

| Layer | Stack |
|-------|-------|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 (single activity, state-based nav) |
| Build | Gradle 8.x Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |
| SDK | minSdk 26, target/compileSdk 34, JDK 17 |
| Networking | Retrofit 2 + OkHttp + kotlinx.serialization |
| Storage | DataStore (`sift_token`); bundled `assets/` catalog, `drawable-nodpi/` logos |
| DI | Manual `AppContainer` (no Hilt/Koin — revisit past ~6 bindings) |
| Backend API | Cloudflare Worker (`siftapi.blackmesa.workers.dev`) |
| Tests | JUnit4 local unit tests (`app/src/test`) |

## Commands

```bash
./gradlew assembleDebug   # debug APK
./gradlew test            # local JVM unit tests (parser, stores)
./gradlew connectedCheck   # on-device/emulator tests (needs emulator)
./gradlew lint            # Android lint
```

**No test framework beyond JUnit4.** `ShareParser` and `Stores` are pure
Kotlin (no Android APIs) so they run on the local JVM. Compose screens are
manual-tested until screenshot tests earn their keep.

## Verify before committing

CI runs: **assemble → test → lint**. Match it locally:

```bash
./gradlew assembleDebug test lint
```

If any fails, the commit will break CI.

## Repository structure

```
app/src/main/java/com/sift/app/
  MainActivity.kt     Single activity — ACTION_SEND entry, state-based routing
  SiftApp.kt          Application — owns the AppContainer
  AppContainer.kt     Manual DI — OkHttp, Retrofit, AuthStore, repositories
  data/               Worker contract mirror — Models.kt, SiftApi.kt,
                      AuthStore.kt, WatchlistRepository.kt
  lib/                Pure Kotlin — Stores.kt (11 stores, host map),
                      ShareParser.kt (Phase 0, zero-dep, no network)
  ui/                 Compose — LoginScreen, ShareFlowScreen, HomeScreen, StoreMark
app/src/main/assets/          uk-*.json catalog (copied from Sift/src/data/)
app/src/main/res/drawable-nodpi/  11 store logos (copied from Sift/public/*.png)
app/src/test/             JVM unit tests (ShareParserTest, StoresTest)
```

- App entry: `SiftApp` → `MainActivity` (login → share/confirm → home).
- API entry: `data/SiftApi.kt` — paths mirror `Sift/workers/index.js`.
- Phase 0 parser: `lib/ShareParser.kt` — no network, ever.

## Key gotchas

- **No scraping.** Never fetch the shared URL server- or client-side. Parse
  locally, match on-device, inherit from Sift's own DB via `resolve`.
  WebView DOM reading is rejected (bot-blocking + per-layout maintenance).
- **Worker is plain JS, no shared schema.** `data/Models.kt` mirrors field
  names manually — keep in sync with `Sift/workers/index.js` on change.
- **Never send a guessed `category`.** Phone pins send
  `category_signals: {title, brand, store}` only (title-first scoring
  server-side), same path as the worker's existing title-first flow.
- **Pin identity is client-derived:** `m_<hash(store|slug-or-name)>`
  (`productIdFor`). Same URL → same id (dedup); same product shared
  differently → separate rows (accepted).
- **Store logos are bundled, never fetched.** Local asset wins, inherited
  `store_logo` is the fallback, hide when both are empty (`StoreMark`).
  M&S (`mands`) is black-on-transparent — light chip under dark theme.
- **JWT lives in DataStore** under key `sift_token` (same key as the
  extension's `chrome.storage.local`). Never log it — the logging
  interceptor stays at BASIC/NONE and redacts Authorization.
- **Trial gating** — 24h / 5 watchlist items, enforced server-side. 403s with
  `trial_expired` / `watchlist_limit` surface the upgrade nudge, never a retry.
- **`local.properties` is gitignored.** `sift.apiBase` overrides the prod
  Worker default for dev (emulator host loopback: `http://10.0.2.2:8787`).
  Never commit keystores (also gitignored).
- **Assets are copies.** Logos/catalog JSON are copied from the `Sift` repo
  (`public/*.png`, `src/data/uk-*.json`). Re-copy when the source changes;
  there is no shared build across repos.
- **Positioning** — Sift is a UK supermarket grocery tracker (search →
  watchlist → shopping list), not an offer-only tool. Never write copy that
  assumes a discount.

## Discovering recent changes

```bash
rtk git log -n 5 --stat           # last 5 commits with file stats
rtk git status                    # uncommitted changes
rtk git diff                      # unstaged changes
```

## API

Base: `BuildConfig.SIFT_API_BASE` (prod Worker; `local.properties`
`sift.apiBase` overrides locally). No CORS change needed for native.
Login is username/password JWT first; Google OAuth needs an Android client
ID — deferred. Future `POST /api/import/resolve` is stubbed in `SiftApi.kt`
until the worker ships it.

## UI Guidelines

Visual source of truth is `DESIGN.md`
(`/home/wsl/Repositories/markdowns/sift-markdowns/DESIGN.md`) — tokens, type
scale, motion, breakpoints. Reuse documented values; `DESIGN.md` wins on
conflict. Share → confirm must stay a 2-tap flow (confirm screen IS the pin).

## Session Lifecycle Rules

### Multi-Doc Conclusion Protocol
Whenever the user says **"lets finish up and update the docs"**, perform:

1. **Update MEMORY.md:** reverse-chronological entry under `## Session History`.
   Location: `/home/wsl/Repositories/markdowns/sift-markdowns/app/MEMORY.md`
   (create `app/` docs dir if absent).
   Format:
   `### 📝 [DD-MM-YYYY] @ [UK HH:MM 24-hr] | [Short Session Title]`
   * **Changes:** [one sentence]. * **Impacted Files:** `[f1]`, `[f2]`.
   * **Left Off At:** [one sentence].
2. **Update ARCHITECTURE.md:** reflect exact end-of-session state, ~200 lines.
   Location: `/home/wsl/Repositories/markdowns/sift-markdowns/app/ARCHITECTURE.md`
3. **Update README.md** (`/home/wsl/Repositories/sift-app/README.md`) only if
   features, config keys, or build commands changed.
4. **Update AGENTS.md** only for sweeping architectural shifts or core stack
   changes. Keep under ~200 lines.
5. **Commit Message:** suggest one (`feat:`, `polish:`, etc.).
