<p align="center"><strong>Sift App</strong></p>

<p align="center"><strong>All your groceries. One place — in your pocket.</strong></p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=android&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/minSdk-26-3DDC84" alt="minSdk 26" />
  <img src="https://img.shields.io/badge/License-MIT-16A34A" alt="MIT license" />
</p>

Native Android companion to [Sift](https://github.com/Alex-Projects-Master/sift)
(the UK supermarket grocery tracker) and its
[browser extension](https://github.com/Alex-Projects-Master/sift-extension).
Share a product page from any store app → confirm → pinned to your watchlist.

The extension stays the acquisition layer; the phone consumes it. No scraping:
shared text is parsed on-device, matched against the bundled catalog, and
enriched from Sift's own DB (extension-fed rows, anonymized). You type the
shelf price at pin time.

## Features

- **Share-sheet pinning** (`ACTION_SEND` text/plain) — 2-tap flow, confirm
  screen IS the pin
- **On-device parse** — price/promo/quantity fragments stripped, store
  inferred from URL host (11 stores), slug-to-title fallback
- **11-store support** — Tesco, Sainsbury's, ASDA, Morrisons, M&S, Aldi,
  Lidl, Co-op, Waitrose, Iceland, Ocado (bundled logos, dark-theme M&S chip)
- **JWT login** — username/password first; Google OAuth deferred
- **Trial aware** — 24h / 5-item limits surface the upgrade nudge on 403s

## Quickstart

Prerequisites: Android Studio (Hedgehog+), JDK 17.

```bash
# Clone, open in Android Studio, run on emulator or device.
./gradlew assembleDebug
./gradlew test
```

Local API override (gitignored — see `local.properties.example`):

```properties
# Emulator talking to a local worker:
sift.apiBase=http://10.0.2.2:8787
```

Without it, the app points at the production Worker.

## Commands

| Command | What it does |
|---------|--------------|
| `./gradlew assembleDebug` | Debug APK |
| `./gradlew test` | Local JVM unit tests (parser, stores) |
| `./gradlew connectedCheck` | On-device tests (needs emulator) |
| `./gradlew lint` | Android lint |
| `node bin/sift-app.mjs doctor` | Toolchain/SDK/env health check |
| `node bin/sift-app.mjs gate` | CI mirror (assemble + test + lint) |
| `node bin/sift-app.mjs sync-assets` | Re-copy logos + catalog from `../Sift` |

## Project Structure

```
app/src/main/java/com/sift/app/
  MainActivity.kt     Single activity — ACTION_SEND entry, state-based routing
  SiftApp.kt / AppContainer.kt   Application + manual DI
  data/               Worker contract mirror (Models, SiftApi, AuthStore, repo)
  lib/                Pure Kotlin (Stores, ShareParser — JVM-tested)
  ui/                 Compose screens (Login, ShareFlow, Home, StoreMark)
app/src/main/assets/          Bundled uk-*.json catalog (copied from Sift repo)
app/src/main/res/drawable-nodpi/  Bundled store logos (copied from Sift repo)
app/src/test/             ShareParserTest, StoresTest
bin/sift-app.mjs          Ops CLI (doctor, gate, sync-assets, devices)
tooling/                  Gitignored portable JDK (fallback for doctor/gate)
```

## API

Same Cloudflare Worker as web + extension. Pins go to
`POST /api/watchlist` (all fields optional — no worker changes needed);
`POST /api/import/resolve` (inherited facts) is stubbed until the worker
ships it. Phone pins send `category_signals: {title, brand, store}` only —
the worker owns the category taxonomy.

## License

MIT
