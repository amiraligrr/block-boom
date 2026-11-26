-- Block Boom — server-side players, installs, daily snapshots, draft versions.
--
-- Run once against the live database. Safe to re-run: every statement is
-- guarded by IF NOT EXISTS or by the admin migration runner, which catches
-- duplicate-column errors.

ALTER TABLE accounts ADD COLUMN install_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE accounts ADD COLUMN app_version VARCHAR(24) NOT NULL DEFAULT '';
ALTER TABLE accounts ADD COLUMN install_count INT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN last_ip VARCHAR(45) NOT NULL DEFAULT '';
ALTER TABLE accounts ADD COLUMN last_level INT NOT NULL DEFAULT 1;
ALTER TABLE accounts ADD COLUMN last_score BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN is_test TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD KEY idx_install (install_id);
ALTER TABLE accounts ADD KEY idx_app_version (app_version);

CREATE TABLE IF NOT EXISTS player_installs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NOT NULL,
    install_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    app_version VARCHAR(24) NOT NULL DEFAULT '',
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_install (install_id),
    KEY idx_account (account_id),
    KEY idx_device (device_id),
    CONSTRAINT fk_install_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_daily (
    account_id INT NOT NULL,
    day DATE NOT NULL,
    level INT NOT NULL DEFAULT 1,
    total_score BIGINT NOT NULL DEFAULT 0,
    coins BIGINT NOT NULL DEFAULT 0,
    runs INT UNSIGNED NOT NULL DEFAULT 0,
    levels_cleared INT UNSIGNED NOT NULL DEFAULT 0,
    blocks INT UNSIGNED NOT NULL DEFAULT 0,
    shots INT UNSIGNED NOT NULL DEFAULT 0,
    best_combo SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    play_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    coins_earned BIGINT NOT NULL DEFAULT 0,
    app_version VARCHAR(24) NOT NULL DEFAULT '',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, day),
    KEY idx_day (day),
    KEY idx_day_level (day, level),
    CONSTRAINT fk_daily_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Draft/published state. Existing rows become published so no live install
-- is blocked or hidden by this migration.
ALTER TABLE app_versions ADD COLUMN is_published TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE app_versions ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL;
