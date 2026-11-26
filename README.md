# Block Boom

A fair, medium-difficulty block-breaker for Android, with a fully playable web/PWA version and a small PHP backend for accounts, leaderboards, tickets and notifications.

**Current public version:** `1.1.0` (`versionCode 22`)

- Landing / APK: <https://blockboomapi.amiraligrr.ir/>
- Play in the browser: <https://blockboomapi.amiraligrr.ir/play/>
- Made by **Amiraligrr, for everyone, everywhere.**

## What is in 1.1

- Rebalanced difficulty: medium, fair, and survivable with a few mistakes.
- Boss fight every 10 levels, with a visible progress preview.
- Lucky blocks and power-ups: nuke, shield, freeze, double damage, rapid fire, ghost and magnet.
- Light/dark theme selection and a clearer, higher-contrast UI.
- Live leaderboard, account sync, daily snapshots and progress restore after reinstalling.
- In-game support tickets and player notifications.
- Web/PWA build that runs the same game in a browser and works offline after the first load.
- Admin panel for players, versions, ads, tickets, notifications, integrity checks and leaderboard moderation.
- Server-side run tokens and score validation instead of shipping a signing secret in the APK.

## Repository layout

| Path | Purpose |
|---|---|
| `app/` | Android app (Kotlin + Jetpack Compose) |
| `api/` | PHP JSON API and database schema |
| `admin/` | PHP admin panel |
| `web-next/` | Next.js static-export landing page and browser game |
| `tools/` | Read-only API, UI and anti-cheat checks |
| `gradle/` | Gradle wrapper |

## Build the Android app

Requirements: JDK 17, Android SDK with API 36 build tools.

```bash
cp gradle.properties.example gradle.properties
# Put your release keystore at app/blockboom-release.jks and fill in the
# BLOCKBOOM_* signing values in gradle.properties.
./gradlew assembleRelease
```

The release APK is written to `app/build/outputs/apk/release/`.

The debug build uses the normal Android debug signing and does not need the
release keystore.

## Build the web app

Requirements: Node.js 20+.

```bash
cd web-next
npm install
npm run build
```

`next.config.mjs` uses `output: 'export'`, so the static site is written to
`web-next/out/`. Serve that directory at the web root, and put the built APK at
`download/BlockBoom-1.1.0.apk` if you want the landing download button to work.

## Set up the API

1. Create a MySQL/MariaDB database.
2. Import `api/init_db.sql`. Apply the statements in its migration section if
   you are upgrading an existing database.
3. Copy `api/config.example.php` to `api/config.php` and fill in the database
   credentials and secrets, or define the `BB_*` environment variables.
4. Make sure `api/config.php` is not web-accessible. The included `.htaccess`
   denies it, but the file should also live outside a public backup path.
5. Check `GET /health.php`; it should report `"healthy": true`.

The API sets the PHP and MySQL session timezone to `Asia/Tehran`. The host clock
itself can stay UTC; all player-facing dates and daily buckets are Iran time.

## Set up the admin panel

Copy `admin/config.example.php` to `admin/config.php`, fill in the database
details, set `API_BASE` to the API URL, set `ADMIN_API_KEY` to the same value as
the API, and set a real `ADMIN_PASS_HASH` (or a bootstrap `ADMIN_PASS` for the
first login). Change the bootstrap password after the first login.

## Secrets

This repository intentionally contains **no** live credentials:

- `api/config.php` and `admin/config.php` are ignored.
- `gradle.properties`, `local.properties`, the release keystore, `.env*`,
  deployment scripts and FTP credentials are ignored.
- Use the `.example` files as templates.
- Never put an FTP password, database password, signing password or private
  key in a commit, issue, screenshot or log.

## Verification

```bash
python3 tools/check_api_endpoints.py
python3 tools/verify_anticheat.py
cd web-next && npm run build
```

The API check is read-only against production except for throwaway smoke-test
accounts, which it deletes afterwards.
**made by Amiraligrr , for everyone , everywhere**
