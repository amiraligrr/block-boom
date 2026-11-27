-- Next-generation schema repairs (2026-11-26)
--
-- This file is NOT executed automatically. Review the target database first,
-- take a backup, and run it during a maintenance window. It is additive and
-- backward-compatible; rollback statements are at the bottom.
--
-- Defects it fixes:
--   1. api/tickets.php writes tickets.account_id, but the column is absent
--      from the active schema. Account-linked players got HTTP 500.
--   2. api/submit_score.php writes leaderboard.account_id best-effort, but the
--      column is absent, so the account link was silently dropped.
--   3. api/referral.php overloads used_by as both "owner" and "redeemer";
--      generate() writes the owner there, so every code is already used.
--      owner_device/redeemed_by separate the two roles.

ALTER TABLE tickets
    ADD COLUMN account_id INT NULL AFTER category,
    ADD INDEX idx_tickets_account (account_id);

ALTER TABLE leaderboard
    ADD COLUMN account_id INT NULL AFTER device_id,
    ADD INDEX idx_leaderboard_account (account_id);

ALTER TABLE referrals
    ADD COLUMN owner_device VARCHAR(64) NULL AFTER code,
    ADD COLUMN redeemed_by VARCHAR(64) NULL AFTER owner_device,
    ADD COLUMN redeemed_at DATETIME NULL AFTER redeemed_by,
    ADD INDEX idx_referrals_owner (owner_device),
    ADD INDEX idx_referrals_redeemer_day (redeemed_by, redeemed_at);

-- Legacy rows: the only owner information that exists is the old used_by
-- value. Preserve it as the owner so a player can at least see their old code.
-- Old codes remain non-redeemable because redeemed_by stays NULL only for
-- codes that were never used; the application treats a NULL owner as invalid.
UPDATE referrals
   SET owner_device = used_by
 WHERE owner_device IS NULL
   AND used_by IS NOT NULL;

-- Rollback (run only if this migration must be undone):
-- ALTER TABLE referrals DROP INDEX idx_referrals_redeemer_day;
-- ALTER TABLE referrals DROP INDEX idx_referrals_owner;
-- ALTER TABLE referrals DROP COLUMN redeemed_at;
-- ALTER TABLE referrals DROP COLUMN redeemed_by;
-- ALTER TABLE referrals DROP COLUMN owner_device;
-- ALTER TABLE leaderboard DROP INDEX idx_leaderboard_account;
-- ALTER TABLE leaderboard DROP COLUMN account_id;
-- ALTER TABLE tickets DROP INDEX idx_tickets_account;
-- ALTER TABLE tickets DROP COLUMN account_id;
