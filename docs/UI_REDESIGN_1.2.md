# Block Boom 1.2 — UI/UX redesign (Nebula)

This document records what the redesign changed, how it was validated, and what
is still blocked. It is a release-engineering note, not a store listing.

## Implemented

- **Design system** in `ui/theme` and `ui/components/Nebula.kt`: navy/violet
  palette, lavender light palette, full Vazirmatn type scale, shared top bars,
  avatar, pills, notices, progress bars, empty states, bottom sheets and dock
  items.
- **Home**: identity header, hero panel with real hearts/coins/level, mode
  selector, level/record preview, daily missions, premium strip, one primary
  Play action and a four-item dock. Missions open in a bottom sheet; upgrade,
  stats, support and invite live in the “More” sheet.
- **Gameplay UI**: themed HUD, readable progress label, pause card, revive
  offer, record-wave card and level-complete overlay. Gameplay physics,
  collision and scoring were not changed.
- **Game over**: themed result card, mode badge, primary retry action and
  existing score-submission, ad-double, share and life flows preserved.
- **Leaderboard**: record-only tabs, avatars, profile row, ranked rows, empty
  and error states. The artificial “تیم بلاک بوم” seed is hidden from display
  and remaining rows are re-ranked client-side; the database was not touched.
- **Shop, settings, stats/profile, support, invite, upgrade and onboarding**
  were migrated to the shared visual system. Settings now exposes the existing
  support and stats routes, and its recovery field uses the shared text field.
- **Store split preserved**: `myket` keeps its applicationId and billing
  client; `bazaar` keeps its separate applicationId and billing client.
  Tapsell remains shared in `main`.
- **Version** is `1.2.0` / `versionCode 25`.

## Validation

- Robolectric screen audit rendered 22 light/dark variants and wrote 44
  screenshots to `build/qa`. It found no text below 4.5:1 contrast and no
  unresponsive click targets in the measured screens.
- Myket: 70/70 unit tests passed (including screen audit).
- Bazaar: billing/catalog/scoring/simulation tests passed, and the Bazaar
  screen audit passed (23/23).
- Both release APKs and AABs build successfully and are signed with the
  existing BlockBoom identity (SHA-256
  `4276fd1738a4130dcd03af42689b9ff3597fa96931c7fa280f08b83ccdd9a3f4`).
- `Builds/Myket/BlockBoom-Myket-1.2.0-25.{apk,aab}` and
  `Builds/Bazaar/BlockBoom-Bazaar-1.2.0-25.{apk,aab}` are the local artifacts.

## Blocked / not implemented

- **Production version row**: the live server has version 24, not 25.
  `app_config.php` blocks any build without its own enabled row, so the
  version-25 artifacts cannot be installed for live testing until an
  `app_versions` row is registered. No server or database change was made.
- **Real purchase QA** (Myket and Bazaar) needs a device with the stores and
  sandbox accounts. No purchase, restore, refund or expiry test was claimed.
- **Bazaar live configuration** still needs the live public key, product IDs,
  the `purchases.store` migration applied, and signing/store decisions.
- **Server-side purchase enforcement** remains a product decision because of
  existing Myket purchases.
- **Privacy policy**: the project has no policy URL or text, so no fake
  “privacy” screen was added. The settings screen shows the real app version
  and support route instead.
- **Missions** are the local daily missions already implemented; there is no
  server missions API. The new mission sheet therefore shows only real local
  progress and makes no network claim.
- **Graphics quality** is not exposed by the current renderer, so no
  non-functional quality toggle was added.
- **English UI** is still not shipped; the app remains Persian-only.
- Real-device screenshot/QA was not completed in this pass because the
  available emulator connection did not survive the environment restarts.
