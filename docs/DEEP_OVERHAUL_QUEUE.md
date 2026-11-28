# Block Boom — Deep Overhaul Task Queue

**Status:** QUEUED — must not start until the active visual/intro task is
validated and committed.
**Branch:** `next-generation`
**Version target:** 1.2 (versionCode 26 next; do not reuse 24).
**Safety:** no data deletion, no production migration, no store deployment
without owner approval.

## Current task gate

The active task is the premium visual pass:

- HomeV2 theme-aware in light/dark.
- Rounded iOS/Telegram-style bottom navigation with vector icons.
- Emoji UI icons replaced by `AppIconView` vectors.
- Cinematic cold-start intro with the real launcher icon and the exact
  `made by Amiraligrr for everyone, everywhere` line.
- Compact Game Over without scroll-to-button; real heart.
- Boss Fight removed from the menu.
- Gems removed.
- Banner top on Home and secondary screens.

It is complete when:
- Release build succeeds.
- Unit/UI suite passes.
- The intro does not replay on activity recreation/navigation.
- Light and dark Home both render (smoke tests).

## Queued phases

| Phase | Scope | Depends on | Status |
|---|---|---|---|
| 1 | Deep audit: engine, modes, economy, backend, admin, ads, content | — | QUEUED |
| 2 | Foundational architecture: two canonical modes (Stage/Ranked), remove duplicate mode paths, online gate | 1 | PARTIAL — online gate done; Time Attack retired; Classic+Infinity only |
| 3 | Main ball redesign: body/wheels/skins/hitbox-safe animation | 2 | QUEUED |
| 4 | Stage + Ranked completion, independent records | 2 | PARTIAL — Infinity is continuous and auto-advancing; record board still legacy |
| 5 | Adaptive Ranked difficulty from DPS/build/performance | 4 | PARTIAL — build/time pressure wired; needs on-device tuning |
| 6 | Blocks and environment: silhouettes, damage, backgrounds, random non-repeating free backgrounds | 3 | QUEUED |
| 7 | Power-ups: Mini Orb, Overdrive, Shockwave, Magnet, Shield, Multi Shot, Time Warp | 3 | QUEUED |
| 8 | Upgrade/economy rebalance + XP/Gems sources and sinks | 4 | QUEUED |
| 9 | Real boss fights with phases/mechanics/rewards | 3,4 | QUEUED |
| 10 | Lucky Wheel with server-side last-spin protection | 8,11 | QUEUED |
| 11 | Online infrastructure: real connectivity check, account sync, idempotent scores/purchases | 1 | QUEUED |
| 12 | News system + admin CRUD/scheduling/pages | 11,14 | PARTIAL — API/admin CRUD, in-app swipe reader and Home card done; scheduling/pagination not yet |
| 13 | Modal notification queue + read receipts + targeting | 11,14 | QUEUED |
| 14 | Admin panel overhaul: content, game, economy, players, system, security | 11 | QUEUED |
| 15 | Ads/monetization audit: standard Tapsell only, no Plus, remembered caps | 8,11 | PARTIAL — banner+rewarded preserved; no interstitial zone configured |
| 16 | Startup animation/boot sequence + real status messages | 11,17 | PARTIAL — cold-start intro + online gate done |
| 17 | Integration/regression tests, low-end performance, release APK | all | QUEUED |
| 18 | Final release report, migration verification, versionCode 26 decision | 17 | QUEUED |

## Canonical decisions to verify during Phase 1

1. The Deep Overhaul specification says **two player-facing modes: Stage and
   Ranked**. The current menu also exposes Time Attack. Phase 2 must either
   retire Time Attack or reclassify it as a limited event; it must not remain a
   third canonical progression path without an owner decision.
2. Boss Fight is already removed from the menu. Boss encounters remain inside
   Stage, which matches the specification.
3. Gems are removed from the current UI. Phase 8 decides whether to reintroduce
   them only with a real source and sink; otherwise they stay removed.
4. Existing player data must be preserved. Any schema change is additive and
   must ship with a backup/rollback plan. The previous “full reset” instruction
   is not executed by this queue.

## Required checks before Phase 1 starts

- Commit the active visual task.
- Confirm release build and tests.
- Record any unverified UI on a physical device.
- Do not begin schema, economy, or content migrations in Phase 1.

## Progress log

- 2026-11-27: queue created; visual task completed in working tree and release
  build generated. Deep Overhaul not started yet.
- 2026-11-28: Phase 12 basic news loop shipped (published-only API, admin
  create/edit/publish/delete, swipe reader, Home card). Migration written but
  not executed. Home rebuilt as one scaled composition; 90/90 tests pass.
