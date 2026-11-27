# Block Boom — Deep Overhaul Audit (Phase 1)

**Date:** 2026-11-27
**Branch:** `next-generation`
**Status:** Phase 1 in progress; no destructive/database changes made.

## 1. Current product state

- Android: Kotlin + Compose, single activity, Compose Canvas renderer.
- Modes in the menu: Classic (Stage), Infinity (Record), Time Attack.
- Boss Fight was removed from the menu in the previous task; the engine still
  contains the MOTHER boss used inside Stage.
- Coins, upgrades, skins, missions, achievements, daily reward, XP, cloud sync,
  record leaderboard, announcements, rewarded/banner ads, Myket/Bazaar billing.
- Gems were removed from the UI and local save in the previous task.
- Cinematic cold-start intro is implemented with the real launcher icon and the
  exact `made by Amiraligrr for everyone, everywhere` line.
- Backend is still the old production code; security fixes exist only in the
  working branch and have not been deployed.

## 2. Gameplay / engine

| Area | Current reality | Gap vs Deep Overhaul |
|---|---|---|
| Main projectile | One auto-firing cannon at the bottom, horizontal only | Needs a ball-on-wheels character with movement/shoot/hit animations; no second “cannon” system |
| Mini Orb | Not found | Must be a side power-up with own count/positions/death rules |
| Stage | Implemented; saved campaign, chests, perfect clears, MOTHER every 10 levels | Needs more varied levels, real multi-phase bosses, independent curve |
| Ranked (Record) | Implemented as endless from level 1; record board | Needs adaptive starting difficulty from build/performance; independent record |
| Time Attack | 60s prototype added in this branch | Deep Overhaul says exactly two canonical modes; must be retired or made an event, not a third progression path |
| Boss Fight menu | Removed | Correct for now; bosses remain in Stage |
| Ball physics | Straight movement, swept collision, piercing, ghost | Keep; add visual ball identity without changing hitbox |
| Blocks | HP/armor/ice/boulder/lucky/splitting/MOTHER | Needs silhouette families, clear damage states, spacing/perf pass |
| Power-ups | FREEZE, DOUBLE_DAMAGE, RAPID_FIRE, COIN_RUSH, GHOST, SHIELD, NUKE | Missing Mini Orb, Overdrive, Shockwave, Magnet, Multi Shot, Time Warp |
| Difficulty | Level-curve plus build-based armour feedback | Needs adaptive Ranked difficulty and a separate Stage curve; no long easy opening for strong builds |
| Upgrades | 6 tracks, caps, premium discount | Economy needs longer progression, more meaningful late levels, gem decision |
| Backgrounds | Purchased backdrop skins; fixed selection | Needs free rotation with no immediate repeats; premium selection should persist |
| Effects | Particles, shake, explosions, combo popups | Needs controlled feedback pass; avoid excess |

## 3. Progression / economy

- Coins: implemented faucets/sinks; upgrade prices and rewards are tuned.
- XP: implemented in this branch (run-based, cloud meta); no level-up rewards yet.
- Gems: removed; if reintroduced, need a real source/sink and migration.
- Missions: local 3-task daily.
- Achievements: 13, coin rewards.
- Daily reward/chest: implemented.
- Weekly league: card/button only; no league buckets/claims.
- Lucky Wheel: not found.
- Skins: implemented, mostly color/material variants; Deep Overhaul requires real silhouette differences.
- Premium: local 30-day entitlement; server purchase verification is source-fixed but not deployed.

## 4. Online / server

- AppConfig gate: ban/version check on launch/foreground; fails open when the
  server is unreachable.
- PlayerSync: register/sync/recover; XP now mirrored.
- Record leaderboard: run-token protected, score-first.
- Referral, tickets, purchases: source fixes exist; schema migration is additive
  and not executed.
- Deep Overhaul requires a real online gate (internet + API + version + account +
  sync) before entering the game; the current app allows offline play and only
  fails open. This is a deliberate product change with a real offline screen and
  retry, not a silent block.
- News system and modal notification queue are not implemented; announcements
  are fetched in-app only.

## 5. Ads / monetization

- Tapsell banner + rewarded; banner at top, hidden on Game/Upgrade/Onboarding.
- Rewarded: lives, free upgrade, double coins, revive, free coins.
- Interstitial: not found in current Tapsell setup.
- Purchase verification: server endpoint fixed in source; real receipt test
  still needed.
- Rewarded callbacks are guarded against double reward in `AdManager`, but
  server-side grant idempotency still needs the purchase/claim audit.

## 6. Admin / security

- Admin panel exists: dashboard, monitor, players, leaderboard, purchases,
  versions, ads, integrity, tickets, notifications, settings, bans.
- Source fixes in this branch: purchase endpoint include + token binding,
  referral owner/redeemer split, ticket schema fallback, admin XSS escaping,
  server-verified leaderboard premium.
- Pending: additive migration, staging deployment, real store test, device-ID
  authorization hardening, complete admin role model, news CMS, notification
  queue, lucky-wheel config.

## 7. UI / motion state after the visual task

- HomeV2: theme-aware light/dark, no-scroll adaptive scale, rounded floating
  bottom nav, vector icons, 3 mode cards, top banner (house fallback when no
  fill), no gems, no Boss card.
- Game: restored simple header (user preference), progress, power-up/combo
  strip, Time Attack timer, compact pause target.
- Game Over: no scroll-to-buttons, real heart, compact stats.
- Intro: cold-start only, process-scoped, skippable, reduced-motion and low-RAM
  aware, real launcher icon, exact creator line.
- Remaining visual inconsistency: Shop/Settings/Stats/Leaderboard/Support/
  Upgrade still use the older design language; deep reskin is a later phase.

## 8. Phase dependency map (execution order)

1. **Audit + decisions** (this document). Decide Time Attack fate; decide gem
   fate; define online-gate behavior.
2. **Online gate + two-mode consolidation** — requires a real connectivity/API
   check, an offline screen, and a single Stage/Ranked entry. No schema change.
3. **Main ball redesign + Mini Orb** — engine hitbox unchanged; visual/skin
   refactor and power-up system.
4. **Adaptive Ranked difficulty** — build/DPS/performance inputs; separate
   Stage curve; test with fixed builds.
5. **Blocks/backgrounds** — families, damage states, free rotation.
6. **Power-ups** — Overdrive/Shockwave/Magnet/Multi Shot/Time Warp; reuse
   existing timers where possible.
7. **Upgrade/economy** — extend caps/costs, XP rewards, decide gems, test
   sinks/sources.
8. **Boss fights** — phases, telegraphs, unique rewards.
9. **Lucky Wheel** — server last-spin + grant idempotency (needs schema).
10. **News + modal notifications + admin CMS** — reuse notifications/read
    receipts; add additive schema.
11. **Admin overhaul + security hardening** — roles, audit log, monitoring.
12. **Ads pass** — frequency config, rewarded integrity, no duplicate grants.
13. **Startup boot experience** — real statuses, online/sync progress, intro.
14. **Integration/regression/performance/release** — 1.2, versionCode 26.

## 9. First safe action list (no owner approval needed)

1. Write the online-gate state machine and offline screen as a **feature-flagged**
   path; keep the current fail-open path until the owner verifies the UX.
2. Extract a `GameMode` decision document: Stage/Ranked canonical, Time Attack
   as event-only or removed. Do not delete engine code yet.
3. Add an adaptive-difficulty model as pure functions + unit tests before
   wiring it into `levelConfig`.
4. Audit ball rendering and skins; propose silhouette-safe changes without
   changing the hitbox.
5. Add a background rotation helper with a no-repeat test, not a UI rewrite.
6. Keep all schema/network changes behind additive migrations and local tests.

## 10. Explicit non-goals for this phase

- No data deletion, no production migration, no store upload.
- No engine rewrite.
- No removal of existing player progression.
- No fake API/mock as final implementation.

## 11. Blockers / owner decisions

- Physical test device is currently disconnected; on-device light/dark and intro
  verification is pending.
- Decide whether Time Attack remains a third mode or becomes an event.
- Decide whether gems return with a real sink or stay removed.
- Online-gate requirement conflicts with the current offline-tolerant design;
  needs a deliberate offline UX and a decision about existing offline players.
- Store receipt credentials are needed for real purchase verification testing.
