-- Block Boom 1.2 — record-only leaderboard.
--
-- Existing rows are campaign totals. They must not appear on the new record
-- board, so they are left untouched with is_record = 0. The first record-mode
-- submission from a device replaces that device's row with the record attempt.
--
-- Safe to re-run: duplicate-column errors are ignored by the admin migration
-- runner, and the index is guarded by IF NOT EXISTS where MySQL supports it.

ALTER TABLE leaderboard ADD COLUMN is_record TINYINT(1) NOT NULL DEFAULT 0 AFTER ban_reason;
ALTER TABLE leaderboard ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'stage' AFTER is_record;
ALTER TABLE leaderboard ADD KEY idx_record_level (is_record, level DESC, score DESC);
