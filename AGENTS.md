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

All commands run via the ops CLI (`node bin/sift-app.mjs <cmd>`).
Never call `./gradlew` directly — the CLI resolves the toolchain
(JDK/SDK) and forwards extra args to Gradle.

```bash
node bin/sift-app.mjs assemble        # debug APK (assembleDebug)
node bin/sift-app.mjs test            # local JVM unit tests (parser, stores)
node bin/sift-app.mjs lint            # Android lint
node bin/sift-app.mjs connectedCheck  # on-device/emulator tests (needs emulator)
node bin/sift-app.mjs gate            # CI mirror: assemble + test + lint
node bin/sift-app.mjs doctor          # toolchain, SDK packages, wrapper, env
node bin/sift-app.mjs sync-assets     # re-copy logos + catalog from Sift repo
node bin/sift-app.mjs devices         # attached devices/emulators (adb)
```

**No test framework beyond JUnit4.** `ShareParser` and `Stores` are pure
Kotlin (no Android APIs) so they run on the local JVM. Compose screens are
manual-tested until screenshot tests earn their keep.

## Ops CLI

Zero-dep node CLI (`bin/sift-app.mjs`, mirrors `Sift/bin/sift.mjs`).
Never deploys, never signs release builds.

```bash
node bin/sift-app.mjs doctor        # toolchain, SDK packages, wrapper, env
node bin/sift-app.mjs gate          # CI mirror: assemble + test + lint
node bin/sift-app.mjs sync-assets   # re-copy logos + catalog from Sift repo
node bin/sift-app.mjs devices       # attached devices/emulators (adb)
```

`sync-assets` copies from `../Sift` by default (`--sift-dir` overrides).
Run it when `Sift/public/*.png` or `Sift/src/data/uk-*.json` change.

`doctor`/`gate` resolve the toolchain without shell exports: java via
`JAVA_HOME` → `PATH` → `~/tooling/jdk17` → repo `tooling/jdk17`
(gitignored portable fallback); SDK via `ANDROID_HOME`/`ANDROID_SDK_ROOT`
→ `local.properties sdk.dir`. On failure `doctor` prints fresh-machine
re-download notes (repo-local `tooling/` is deleted with the repo).

## Verify before committing

CI runs: **assemble → test → lint**. Match it locally:

```bash
node bin/sift-app.mjs gate
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
bin/sift-app.mjs          Ops CLI — doctor, gate, sync-assets, devices
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
Whenever the user says **"lets finish up and update the docs"**, you MUST perform the following documentation updates before stopping:

1. **Update MEMORY.md:**
   * Insert a reverse-chronological entry directly under the `## Session History` header.
   * Location: `/home/wsl/Repositories/markdowns/sift-markdowns/app/MEMORY.md`
   (create `app/` docs dir if absent).

   ### **Format:**
      ### 📝 [DD-MM-YYYY] @ [UK HH:MM 24-hr] | [Short Session Title]
      * **Changes:** [One-sentence summary of what was accomplished].
      * **Impacted Files:** `[file_1.ext]`, `[file_2.ext]`.
      * **Left Off At:** [One-sentence summary of outstanding next steps].

2. **Update ARCHITECTURE.md:**
   * Review the current architectural state, tech stack details, or data flows.
   * Update any outdated sections to reflect the exact state of the codebase at the end of this session. Keep it under ~200 lines.
   * Location: `/home/wsl/Repositories/markdowns/sift-markdowns/app/ARCHITECTURE.md`

3. **Update README.md:**
   * Review `README.md`. If the session introduced new features, configuration keys (`local.properties`), or changed installation/build commands, update those specific sections. Do not alter stable project descriptions unless explicitly relevant.
   * Location: `/home/wsl/Repositories/sift-app/README.md`

4. **Update AGENTS.md:**
   * Updates to this file are strictly reserved for critical, sweeping architectural shifts, fundamental changes to the core tech stack, or major global project rules. Do not modify it for routine features, refactors, or bug fixes. Keep it under ~200 lines
   * Location: `/home/wsl/Repositories/sift-app/AGENTS.md`

5. **Commit Message**
   * Once docs are upto date suggest a quick commit message with either `feat:`, `polish:` etc
