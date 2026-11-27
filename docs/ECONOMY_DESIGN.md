# Block Boom — Economy Design (Next Generation)

Status: design + first implementation slice. No database reset or production
migration is performed by this document.

## Principles

1. Every currency has a named source and a named sink.
2. No currency is purchasable before it has something worth buying.
3. The free path must reach the same content as the paid path, slower.
4. Rewards are granted once and are idempotent at the run/claim boundary.
5. The server never trusts a client balance; the client is the display and the
   offline mirror.

## Currencies

| Currency | Role | Source | Sink |
|---|---|---|---|
| Coins | Soft currency / upgrade fuel | Block drops, level chests, perfect clears, missions, achievements, ads, referrals | Permanent upgrades, skins, revive |
| Gems | Premium currency | Account level-ups, boss first-clears, weekly league placement, rare achievements | Exclusive skins, cosmetic bundles, extra revive slot (future) |
| Hearts | Attempt budget | +5 every 90 min, rewarded ads, premium | Starting a run |
| XP | Account progression | Finished runs (score/blocks/levels/coins), mode first-clears | Nothing (it is a progress track, not a wallet) |

## XP and account level

- XP is client-local today and mirrored in `PlayerSyncManager` meta as `xp`.
- A run awards:
  `score/25 + blocks/3 + levelsCleared*15 + coins/10`, capped at 5,000 per run.
- Level N → N+1 requires `100 + (N-1)*75` XP.
- Level-ups grant 1 gem (future implementation; not wired yet).
- XP cannot be bought and cannot go down.
- `SaveManager.addRunXp` is called from `recordRun`, which `GameViewModel`
  latches once per run, so the same run cannot pay XP twice.

## Gems (design; not yet spendable)

- Earn:
  - 1 per account level-up.
  - 3 for the first clear of a boss tier (10, 20, 30…).
  - 10/6/3 for weekly league 1st/2nd/3rd.
  - 5 for the “reach level 100” achievement.
- Spend:
  - 60–150 for exclusive ball/cannon skins.
  - 40 for a cosmetic background bundle.
  - 20 for a single paid revive when the player has no ad available.
- Gems must not buy raw power. Upgrades stay coin-only.
- Until the gem sink UI exists, do not grant gems retroactively; a dead
  currency is worse than no currency.

## Weekly league

- Seven-day bucket based on the record board.
- Score = best record run submitted during the bucket.
- Rewards: coins for top 50, gems for top 3, a badge for the top 10.
- One claim per device per bucket; server stores `league_claims` with a unique
  `(device_id, bucket)` key.
- A league is a display/filter over the record board today; it must not create
  a second source of truth for scores.

## Pricing and faucet guardrails

- Upgrade pricing remains `base * level^1.5 * 1.10`, premium 40% off.
- Coin income per level is roughly 1,275 at a full board; do not add a second
  passive coin faucet without retuning the upgrade curve.
- Rewarded ads: max 5 coin watches/day, 2 free upgrade levels/day.
- Referral: 100 coins per side, max 5 redemptions/day/device, one payout per
  code, both sides paid in one transaction.
- Revive: 400 + 20×level coins (cap 6,000) or one rewarded ad; once per level.

## Server authority

- Purchases: server verifies the store receipt and writes the entitlement/coin
  grant. The client must never be the only record of a paid product.
- Referrals: the code owner and redeemer are separate columns; redemption is
  one transaction with `FOR UPDATE`.
- Leaderboards: server-signed run tokens; `is_premium` is no longer trusted
  from the request.
- Coin grants: `claim_coins.php` selects unclaimed rows `FOR UPDATE`, marks
  them claimed, and pays once. Lost responses are a known limitation; a future
  delivery-ack table will close it.

## Migration/rollout

1. Land schema: `tickets.account_id`, `leaderboard.account_id`,
   `referrals.owner_device/redeemed_by/redeemed_at`.
2. Back up the database and verify the target.
3. Reset game data only after the new client is ready (owner-approved).
4. Start fresh at level 1, 0 XP, 0 coins, 0 gems, no legacy records.
5. Keep infrastructure credentials and external store/ad accounts.

## Explicit non-goals for this slice

- No pay-to-win gem upgrades.
- No loot boxes.
- No gem purchase before gem sinks ship.
- No destructive migration executed by the development session.
