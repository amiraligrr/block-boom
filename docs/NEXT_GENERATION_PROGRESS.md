# Block Boom — Next Generation Progress

**Session started:** 2026-11-26
**Working tree:** `/home/amiraligrr/Desktop/desktop/block boom`
**Repository note:** Git root is the home directory, not this project. The project itself is untracked; do not run destructive git commands.

## Initial state

- Version in `app/build.gradle.kts`: `versionCode 25`, `versionName 1.2.0`.
- Installed/live baseline: 1.2.0 (versionCode 24 on the server); v25 was registered as a draft for device testing.
- Existing uncommitted work from this session:
  - New V2 home: `ui/v2/HomeV2.kt`
  - New in-game HUD: `ui/v2/GameHudV2.kt`
  - New secondary screens: `ui/v2/SimpleScreens.kt`
  - New routes: Missions / Achievements / Free Coins
  - `GameMode` now includes `BOSS` and `TIME_ATTACK`
  - Top ad slot moved above the NavHost; hidden on Home/Game/Upgrade/Onboarding
  - Support now renders above the ban gate
  - Renderer no longer copies entity lists every frame
- Build status at session start: `:app:assembleMyketRelease` succeeds.

## Constraints observed

- No production deployment, no store upload, no database reset, no destructive migration.
- Backend fixes are source-only; deployment is left to the owner.
- No secrets are reproduced in docs.

## Work log

### 2026-11-26 — mode lifecycle fixes
- Boss Fight retry now restarts the boss that was just attempted instead of recomputing from the campaign checkpoint.
- BOSS/TIME_ATTACK no longer open unused server run sessions.
- Game-over mode label now distinguishes Boss/Time Attack from Stage.
- (continuing)

## Known blockers

- Production purchase verification and referral defects require backend deployment and/or schema migration; source fixes only in this session.
- Full data reset is intentionally not executed.
- Low-end performance profiling requires the physical test device.

---

## Final status — 2026-11-27

- Branch `next-generation` pushed to `origin`.
- Working tree clean.
- Release build: PASS (`:app:assembleMyketRelease`).
- Unit/UI suite: 74/74 PASS.
- PHP structural syntax: 37 files, 0 problems.
- On-device install: blocked — `adb devices` empty.
- Production deployment/migration/data reset: deliberately not executed.
- Added: account XP + cloud mirror, adaptive no-scroll Home, low-RAM static
  backdrop, mode-aware results, backend security fixes, additive schema
  migration, economy design, Robolectric harness repair.
- Next: device verification, mode result/reward completion, secondary screen
  reskin, staging deployment of backend fixes.

## 2026-11-27 — premium visual task + Deep Overhaul queue

- Cinematic cold-start intro wired (real launcher icon, exact creator line,
  process-scoped, skippable, reduced-motion/low-RAM variants).
- HomeV2 is theme-aware; light mode no longer uses the dark background.
- Home/nav icons use the vector AppIcon system; bottom nav is a rounded
  floating iOS/Telegram-style bar.
- Boss Fight removed from the menu; gems removed from UI and local save.
- Game Over is compact and scroll-free; heart is visible immediately.
- In-game header restored to the simpler preferred style.
- Added HomeV2 light/dark smoke tests.
- Full suite: 76/76 PASS. Release APK rebuilt.
- Deep Overhaul audit (`docs/DEEP_OVERHAUL_AUDIT.md`) and phase queue
  (`docs/DEEP_OVERHAUL_QUEUE.md`) created. Phase 2 is queued; no destructive
  changes, migrations, or deployments performed.

## 2026-11-27 — device verification + content loop

- Installed the latest build on the Xiaomi test device (versionCode 25).
- Verified the cold-start intro on-device: real launcher icon, BLOCK BOOM,
  exact creator line, transition into Home.
- Verified the new Home on-device: 3 modes, vector nav, rounded bottom bar,
  no Boss, no gems, top banner/house fallback.
- Added a real “رکورد من” card to Home (level, score, best combo, leaderboard).
- Added a server announcement modal queue, held back during gameplay/upgrade.
- Full suite: 76/76 PASS after the UI changes.
- Device disconnected again before the record-card build could be installed;
  the APK is built locally.
- Deep Overhaul: Phase 1 audit complete, Phase 2 queued.

## 2026-11-27 — Phase 2 slice: online startup gate

- Added `OnlineGate` + `OnlineGateScreen`: real API health check before Home,
  explicit offline vs server-error screens, exact Persian copy and retry.
- Deliberately does not require `NET_CAPABILITY_VALIDATED` (international
  validation can be blocked in Iran); only validated by our domestic API.
- Added `OnlineGateTest` (5 cases).
- Home: blocks now fall; typography centered/enlarged; quick actions moved
  below modes; all three modes visible.
- Sound: layered destruction/coin/boss/combo, stereo width, haptic additions.
- Full suite: 81/81 PASS.
- Ads decision recorded: keep standard Tapsell; no Tapsell Plus migration.
  Current code has banner + rewarded; no interstitial zone is configured yet.
- Latest build installed on the test device; visual re-check blocked by the
  device locking/sleeping.
- Phase 2 remains partially complete: account-sync gating and Stage/Ranked
  consolidation are still pending. No DB/migration/deploy changes.

## 2026-11-28 — two-mode Infinity / large-action pass

- Time Attack removed from enum, engine, HUD and result flow.
- Infinity now auto-advances without wave numbers or a wave-complete card.
- Added build/time pressure to Infinity (strong builds are challenged early;
  normal run targets roughly 90 seconds; not yet tuned on-device).
- Home: Play button removed; two large Classic/Infinity buttons.
- Achievements moved into the former weekly-league slot; leaderboard moved to
  the bottom navigation; quick actions and bottom nav enlarged.
- Tapsell decision recorded: standard SDK only, no Tapsell Plus. Banner and
  rewarded remain; no interstitial zone is configured, so none was faked.
- Full suite: 81/81 PASS. Release APK rebuilt with --rerun-tasks.
- Device disconnected; the final two-mode build could not be installed.

## 2026-11-28 — real news system + final one-screen Home

- Added a real game-news system:
  - `api/news.php` returns only published items, with `?id=` for one item.
  - `admin/news.php` is the only writer: create, edit, publish/unpublish,
    delete, with CSRF, length limits and escaped output.
  - `admin/layout.php` now links the news page.
  - Additive migration `api/migrations/2026_11_28_news.sql`; it was **not**
    executed by this session and can be rolled back with `DROP TABLE news`.
  - App: `NewsManager` fetches, `NewsScreen` swipes between items with page
    dots, scrolls long bodies and loads an optional remote image. The Home
    news card opens the reader; drafts never reach the app.
- Support: `from_admin` now accepts Boolean, 0/1 and yes/no; both speaker
  labels render. Added parser tests so an admin reply cannot regress into a
  player bubble.
- Home final layout:
  - One 820dp composition including quick actions, news and bottom nav,
    scaled exactly to the NavHost so there is no dead band and no scroll.
  - Top-aligned overflow keeps the profile row below the 60dp ad banner on
    short screens (AnimatedVisibility was centring the oversized content).
  - Mode cards 168dp with subtitles; quick actions 104dp; event cards 88dp;
    larger labels; Home clipped to its own bounds so no falling cube can
    cover the ad.
  - Removed the detached crown base line that rendered as a stray stroke.
  - Light/dark smoke tests now pin the same RTL layout direction the app uses.
- Online gate: added an explicit “ادامه با اطلاعات محلی” path; sync and the
  leaderboard stay unavailable until the domestic API answers.
- Full suite: 91/91 PASS. Release APK rebuilt and installed on the Xiaomi
  test device; final Home verified with a live top banner.
- Not verified on-device: Infinity run, support thread with a real admin
  reply, News reader via UI (touch injection is blocked by MIUI). Their
  logic is covered by tests.
