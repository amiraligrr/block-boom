# Block Boom — Dual Store Release

This document describes the Cafe Bazaar and Myket builds from one shared
Android source tree. It deliberately does **not** include passwords, private
signing keys, or the Bazaar/Myket RSA key material.

## Architecture

```
app/src/main/      shared game, UI, API, Tapsell ads, catalog facts
app/src/myket/     Myket billing manager + Myket StoreConfig
app/src/bazaar/    Cafe Bazaar billing manager + Bazaar StoreConfig
app/src/test/      shared unit/Robolectric tests
```

`BillingProvider` is the store-neutral contract. Each flavor provides a
`BillingManager` with the same constructor and UI-facing state, so
`BlockBoomApp`, `ShopScreen`, and the rest of the game compile against one API.
`StoreConfig` is also provided per flavor; it holds that store's SKU mapping and
public verification key. `ProductCatalog` in `main` holds the shared coin
amounts, prices, and product display facts.

## Application IDs

| Flavor | applicationId | Notes |
| --- | --- | --- |
| myket | `ir.amiraligrr.blockboom` | Existing published Myket package. Do not change. |
| bazaar | `ir.amiraligrr.blockboom.bazaar` | Separate install; safe to publish independently. |

`versionCode` is `25` and `versionName` is `1.2.0` for both. The live server
currently has the version-24 row; a version-25 row must be registered before
either store build is uploaded. `app_config.php` allows a newer client by
default, so local testing is not gated by the missing row.

## Product catalog

| Product key | SKU (both flavors currently) | Type | Coins | Price (IRR) |
| --- | --- | --- | --- | --- |
| PREMIUM | `blockboom_premium_59` | 30-day subscription | — | 389,000 |
| OFFER | `offer` | one-time | 18,000 | 189,000 |
| COINS_SMALL | `blockboom_coins_small` | one-time | 7,000 | 129,000 |
| COINS_MEDIUM | `blockboom_coins_medium` | one-time | 16,000 | 249,000 |
| COINS_LARGE | `blockboom_coins_large` | one-time | 33,000 | 449,000 |
| COINS_VERY_LARGE | `blockboom_coins_very_larg` | one-time | 53,000 | 649,000 |

The Myket panel is the source of truth for the existing Myket IDs. The Bazaar
panel must have the same IDs for the current configuration to work. If Bazaar
uses different IDs, edit only:

`app/src/bazaar/java/ir/amiraligrr/blockboom/data/StoreConfig.kt`

Do not silently change the Myket file.

## Billing provider selection

The flavor source set selects the SDK at compile time:

- `app/src/myket/.../BillingManager.kt` uses the existing Myket
  `IabHelper` integration.
- `app/src/bazaar/.../BillingManager.kt` uses
  `com.github.farasource:billing-client:1.4.0`, a Bazaar/Myket IAB client
  compatible with the Bazaar AIDL service.

Bazaar needs an `ActivityResultRegistry`; `MainActivity` attaches it. Myket's
provider ignores the attach call.

The Bazaar build does not package the Myket SDK, and the Myket build does not
package the Bazaar SDK. Shared Tapsell ads stay in `main`.

## RSA public keys

- Myket public key: `StoreConfig.IAB_PUBLIC_KEY` in
  `app/src/myket/.../StoreConfig.kt` (preserved from the existing app).
- Bazaar public key: `StoreConfig.IAB_PUBLIC_KEY` in
  `app/src/bazaar/.../StoreConfig.kt`. It was supplied by the project owner and
  the trailing web-page whitespace/text was removed.

These are public purchase-verification keys, not APK signing keys. Do not paste
private keystore material into either file.

## Signing

The existing release signing config is preserved:

- keystore: `app/blockboom-release.jks` (not committed)
- file/password/alias: `BLOCKBOOM_STORE_FILE`, `BLOCKBOOM_STORE_PASSWORD`,
  `BLOCKBOOM_KEY_ALIAS`, `BLOCKBOOM_KEY_PASSWORD` via Gradle properties or
  environment variables.

Both flavors currently use the existing release identity. A separate Bazaar
keystore is recommended but was not supplied; do not generate or publish a
replacement key without the owner's approval. If no release keystore is
available, build the debug variants and report release signing as pending.

## Build commands

Debug variants:

```bash
./gradlew :app:assembleMyketDebug :app:assembleBazaarDebug
```

Release APK + AAB:

```bash
./gradlew :app:assembleMyketRelease :app:assembleBazaarRelease \
          :app:bundleMyketRelease :app:bundleBazaarRelease
```

Convenience script:

```bash
./scripts/build-all-stores.sh
```

## Outputs

The script collects:

```
Builds/
  Bazaar/
    BlockBoom-Bazaar.apk
    BlockBoom-Bazaar.aab
  Myket/
    BlockBoom-Myket.apk
    BlockBoom-Myket.aab
```

Gradle's raw outputs are under `app/build/outputs/apk/<flavor>/release/` and
`app/build/outputs/bundle/<flavor>Release/`.

## Backend / purchase verification

The current app has two verification layers:

1. The store SDK/client checks the platform purchase flow and, for Bazaar, the
   RSA signature with the supplied public key.
2. The admin panel's `admin/verify_purchase.php` verifies the Myket signature
   and records the purchase in `purchases`.

The local source now contains:

- `admin/db.php` → `verifyBazaarPurchase()` (SHA1withRSA over `originalJson`)
  alongside the existing `verifyMyketPurchase()`,
- `admin/verify_purchase.php` → accepts `store = bazaar|myket` and uses the
  matching verifier,
- `purchases.store` in `api/init_db.sql` plus a marked migration block,
- a backward-compatible insert fallback so the panel still records a sale if
  the live database has not received the `store` column yet.

Before publishing Bazaar, the owner must still:

- add `BAZAAR_PUBLIC_KEY` to the **live** `admin/config.php` (the source template
  has it, but `config.php` is intentionally not deployed),
- apply the `purchases.store` migration,
- review whether the app should call server-side verification on the purchase
  path; the current Myket flow delivers locally after the SDK callback and the
  Bazaar flow delivers after its local signature check. Automatic server
  verification before delivery remains a follow-up hardening step.

The migration is intentionally not applied here. The current production
database must not be changed as part of this local build task.

## Store release procedure

1. Confirm the product IDs exist in the target store panel.
2. Confirm the Bazaar public key is the one registered for the Bazaar package.
3. Build the release variant with the correct signing identity.
4. Upload the AAB/APK and store metadata separately.
5. Verify one sandbox purchase and one restore on a test account.
6. Publish only after server-side verification is deployed.
7. Preserve the Myket package and signing identity; never overwrite it with the
   Bazaar artifact.

## Testing

```bash
./gradlew :app:testMyketDebugUnitTest :app:testBazaarDebugUnitTest
```

Verified in this local build:

- both debug variants compile and assemble;
- both release APKs and AABs build and are signed with the existing release
  identity;
- 69 unit/Robolectric tests pass per flavor, 0 failures;
- the Myket APK contains the Myket billing classes/key and no Bazaar classes/key;
- the Bazaar APK contains the Bazaar billing classes/key and no Myket classes/key;
- the manifest market ids/bind addresses are correct for each flavor;
- the Myket release certificate matches the previously published release
  certificate.

Not verified here: a real Cafe Bazaar sandbox purchase, a real Myket purchase,
refund/expiry behavior against the stores, and live server-side purchase
verification. Those require store accounts/test tracks and the production
database migration.

Manual checks:

- Myket build opens the Myket shop and does not reference Bazaar classes.
- Bazaar build opens the Bazaar shop and does not reference Myket classes.
- Both builds share leaderboard/account/Tapsell behavior.
- A canceled purchase shows a cancellation message.
- A repeated token does not credit coins twice.
- A premium token grants 30 days once; a restore after expiry does not extend it.
- The debug builds remain ad-free, matching the existing debug policy.
