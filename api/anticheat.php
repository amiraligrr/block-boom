<?php
/**
 * Block Boom — server-authoritative anti-cheat & score validation.
 *
 * This file is a LIBRARY. It must never be reachable over HTTP
 * (api/.htaccess denies it, see that file).
 *
 * ── Why there is no shared secret in the app ──────────────────────────────
 * The old design signed payloads with HMAC_SECRET, which was compiled into
 * the APK (BuildConfig.BLOCKBOOM_HMAC_SECRET). Any attacker who unzips the
 * APK extracts the key and forges arbitrary scores. A secret that ships to
 * the client is not a secret.
 *
 * Instead the server now issues short-lived, signed *run tokens*:
 *   1. App calls start_run.php  -> server mints token = HMAC(SERVER_SECRET,
 *      payload) where payload pins device + level + start time.
 *   2. The app plays, accumulating a hash chain over its score events and
 *      a real elapsed time it cannot back-date.
 *   3. App calls submit_score.php with the token. The server verifies the
 *      signature (SERVER_SECRET never leaves the server), checks the run was
 *      not already consumed (replay protection), and checks the score is
 *      physically reachable given the engine's real scoring curve and the
 *      wall-clock time the run actually took.
 *
 * A forger can still patch the app to lie about its own score, but cannot
 * mint a token, cannot replay one, and cannot submit a score faster than the
 * game can physically produce it. That raises the cost of cheating from
 * "unzip the APK" to "write a bot that plays in real time".
 *
 * ── The plausibility model mirrors the real engine ───────────────────────
 * GameEngine.kt: levelConfig() / generateBlockHP() / spawnBlockAt() /
 * destroyBlock() / trySplitBlock(). Keep the two in sync — if you retune
 * scoring in the app, bump the headroom factor in HEADROOM.
 */

// Submission policy lives in policy.php so the admin panel can display the
// number it is actually enforcing. See that file for why the two must not be
// declared separately.
require_once __DIR__ . '/policy.php';

// ── Tunables ──────────────────────────────────────────────────────────────
define('BB_MAX_LEVEL', 9999);

/** Multiplier applied to the theoretical engine maximum. >1 so a generous
 *  but legitimate player is never rejected. Raise only if real players hit
 *  "score not reachable" — never lower below 1.0. */
define('BB_HEADROOM', 1.35);

/** Worst-case HP variance the engine can roll: 0.9 + rand*0.2 (GameEngine.kt:337). */
define('BB_HP_VARIANCE', 1.1);

/** Points per second of real play that we are willing to believe. The engine
 *  cannot exceed roughly 2,600/s even on a maxed late level, so 3,500 is a
 *  ~35% margin. A bot must therefore actually play, in real time. */
define('BB_SCORE_RATE_CAP', 3500);

/** Relaxed rate cap used only while run tokens are not yet in use (see
 *  bb_legacy_mode_ceiling). Removes any chance of locking out real players
 *  during a rollout. */
define('BB_LEGACY_RATE_CAP', 100000.0);

/**
 * Hard ceiling, independent of everything else.
 *
 * This is a sanity bound for absurd values, NOT the anti-cheat — the real work
 * is bb_score_ceiling(), which scales with level and server-observed play time.
 *
 * Set to 2e9 because that is exactly the client's own hard cap
 * (LeaderboardManager.kt: `totalScore.coerceAtMost(2_000_000_000L)`), so it can
 * never reject a value a real client is even able to send.
 *
 * It was 2.5e12, justified as "1.4x above the true maximum" — but that maximum,
 * ~1.76e12, was computed from the *uncapped* model above, in which the HP curve
 * and the HP multiplier both grew forever. Against the real engine the lifetime
 * maximum is ~8.97e8, so the constant was ~2,800x above anything the game can
 * produce, and it was doing nothing at all: a forged level-9999 baseline had
 * already passed it before the multiplier was corrected. Anything at or above
 * 2e9 is unreachable from the app by construction, so the bound now matches the
 * client instead of exceeding it.
 */
define('BB_ABSOLUTE_MAX_SCORE', 2000000000);

/** A run may not be older/newer than this when submitted. */
define('BB_RUN_MAX_AGE_SECONDS', 21600);   // 6h
define('BB_RUN_MIN_SECONDS', 3);
define('BB_RUN_FUTURE_SKEW_SECONDS', 120);

/** start_run.php is unauthenticated and does a DELETE + INSERT per call, so it
 *  needs its own budget. A real player opens roughly one run per level — one
 *  every 30-90 seconds — so 20/min per device is far above genuine use. The
 *  per-IP cap is the one that actually matters, since device_id is
 *  client-supplied and rotating it defeats the per-device limit entirely. */
define('BB_START_RUN_PER_MINUTE', 20);
define('BB_START_RUN_PER_IP_MINUTE', 120);

/**
 * Build a rate_limit key that is guaranteed to fit the `ip` VARCHAR(64) column.
 *
 * The obvious construction — 'dev:' . $deviceId — silently overflows: a
 * normalised device_id may be 64 characters, so the composed key is 68. Under
 * STRICT_TRANS_TABLES that is a hard "Data too long" error and the endpoint
 * 500s; under a lax sql_mode the key is truncated instead, which is worse
 * because distinct keys collide and share a budget.
 *
 * Short keys pass through untouched so existing rows keep working; long ones
 * are hashed, which keeps them unique, bounded and index-friendly.
 */
function bb_throttle_key(string $prefix, string $value): string {
    $key = $prefix . $value;
    if (strlen($key) <= 64) return $key;
    return substr($key, 0, 24) . ':' . substr(sha1($key), 0, 38);
}

/**
 * Fixed-window request budget backed by the shared `rate_limit` table.
 *
 * The table is a generic throttle store keyed by `ip` (which is really "any
 * opaque string"); every caller namespaces its own keys so the four users —
 * score submissions ('sub:'), run openings ('run:'/'iprun:') and ad events
 * ('dev:'/'ip:') — cannot collide. New users must go through
 * bb_throttle_key() or they will overflow the column.
 *
 * `last_submit` is overloaded as "window start"; the column name is historical.
 *
 * @return bool true when the request is within budget.
 */
function bb_take_throttle_slot(PDO $pdo, string $key, int $windowSeconds, int $maxPerWindow): bool {
    try {
        $stmt = $pdo->prepare("SELECT last_submit, strikes FROM rate_limit WHERE ip = ?");
        $stmt->execute([$key]);
        $row = $stmt->fetch();
        $now = time();

        if ($row) {
            $elapsed = $now - (int) $row['last_submit'];
            $used    = (int) ($row['strikes'] ?? 0);
            if ($elapsed < $windowSeconds && $used >= $maxPerWindow) {
                return false;   // window still open, budget spent
            }
            if ($elapsed >= $windowSeconds) {
                // New window: reset the counter and slide the window forward.
                $pdo->prepare("UPDATE rate_limit SET last_submit = ?, strikes = 1 WHERE ip = ?")
                    ->execute([$now, $key]);
            } else {
                $pdo->prepare("UPDATE rate_limit SET strikes = strikes + 1 WHERE ip = ?")
                    ->execute([$key]);
            }
            return true;
        }

        $pdo->prepare("INSERT INTO rate_limit (ip, last_submit, strikes) VALUES (?, ?, 1)")
            ->execute([$key, $now]);
        return true;
    } catch (PDOException $e) {
        // Fail open. A throttle that breaks gameplay when the table is missing
        // (migration not run yet) is worse than a throttle that lets one extra
        // request through; the per-device open-run check still applies.
        error_log('bb_take_throttle_slot: ' . $e->getMessage());
        return true;
    }
}


// ───────────────────────────────────────────────────────────────────────────
//  Scoring model — must mirror GameEngine.kt
// ───────────────────────────────────────────────────────────────────────────

/** GameEngine.generateBlockHP() base curve, before variance and the HP cap. */
function bb_hp_curve(int $level): float {
    if ($level <= 5)  return 4 + ($level - 1) * 2;
    if ($level <= 15) return 12 + ($level - 5) * 3;
    if ($level <= 30) return 42 + ($level - 15) * 4;
    if ($level <= 50) return 102 + ($level - 30) * 5;
    return 222 + ($level - 50) * 16;
}

/**
 * GameEngine.MAX_BLOCK_HP — the hard ceiling generateBlockHP() clamps to.
 *
 * This single constant was missing here, and it is what made the model diverge
 * without bound. GameEngine.kt:
 *
 *     return (baseHP * variance).toInt().coerceIn(1, MAX_BLOCK_HP)   // 240
 *
 * The curve above keeps climbing forever (222 + 16/level), so a PHP copy of the
 * curve with no clamp believes a level-9999 block has ~8.8M HP. The engine
 * cannot produce more than 240 — ever — because that is what the clamp does.
 * The model was over-granting by three orders of magnitude.
 */
define('BB_MAX_BLOCK_HP', 240);

/**
 * GameEngine.MAX_BLOCKS_PER_LEVEL. Was 64, then 96, and now 72 after the board
 * retune that kept a level's length inside a readable fight rather than turning
 * the queue into a wall.
 */
define('BB_MAX_BLOCKS_PER_LEVEL', 72);

/** GameEngine.BOSS_EVERY_N_LEVELS. Was 5 here; the engine spawns a boss every 10. */
define('BB_BOSS_EVERY_N_LEVELS', 10);

/** GameEngine.HP_MULTIPLIER_CEILING — the multiplier saturates here and stops. */
define('BB_HP_MULTIPLIER_CEILING', 2.3);

/**
 * The largest HP the engine can actually put on one ordinary block.
 *
 * `generateBlockHP()` clamps at BB_MAX_BLOCK_HP *before* the level multiplier
 * is applied (GameEngine.kt:1296 multiplies the already-capped result), so the
 * cap and the multiplier compose — they are not alternatives, and taking the
 * cap *after* the multiply would under-count.
 */
function bb_effective_block_hp(int $level): float {
    $base = max(1.0, min((float) BB_MAX_BLOCK_HP,
        floor(bb_hp_curve($level) * BB_HP_VARIANCE)));
    return floor($base * bb_hp_multiplier($level));
}

/**
 * GameEngine.levelConfig(): hpMultiplier = 1 + extra*0.0085 + tier*0.009,
 * saturated at BB_HP_MULTIPLIER_CEILING.
 *
 * THIS FUNCTION WAS MISSING, and its absence was the first version of the worst
 * bug in this file. GameEngine applies the multiplier to every non-boss block.
 *
 * Three separate things had drifted out of it, and only the first was ever
 * right:
 *
 *   1. the whole function was absent, so the ceiling under-estimated a real
 *      player by 1.05x at level 10 and 11.7x at level 1000;
 *   2. the coefficients were 0.16 / 0.012 against the engine's 0.09 / 0.011;
 *   3. and there was no ceiling on it at all — GameEngine.kt clamps with
 *      `coerceAtMost(HP_MULTIPLIER_CEILING)`, so the multiplier stops at 2.3,
 *      while this function kept climbing by 0.16 every ten levels forever.
 *
 * (3) is the one that mattered. Unbounded, the multiplier reached ~161 at level
 * 9999, and combined with the missing BB_MAX_BLOCK_HP clamp it let a device
 * claim a ceiling of 2.38 *trillion* against an engine whose true lifetime
 * maximum is 897 million.
 *
 * The engine has since been corrected again — it now ramps off `extra` rather
 * than `inTier`, because `inTier` resets 9 -> 0 at each tier boundary and took
 * the tier step down with it — so the coefficients below are 0.0085 / 0.009. A
 * multiplier that is not monotonic cannot describe a game that is.
 *
 * Do not "optimise" this away: the verify tool re-implements it to prove the
 * two files agree.
 */
function bb_hp_multiplier(int $level): float {
    $extra = max(0, $level - 1);
    $tier  = intdiv($extra, 10);
    return min(1.0 + $extra * 0.0085 + $tier * 0.009, BB_HP_MULTIPLIER_CEILING);
}

/** GameEngine.levelConfig(): blockCount */
function bb_block_count(int $level): int {
    $extra = max(0, $level - 1);
    return min((int) min(8 + $extra * 2, 40) + intdiv($extra, 5) * 3,
               BB_MAX_BLOCKS_PER_LEVEL);
}

/**
 * Upper bound on the score one ordinary block can contribute, fragments
 * included.
 *
 * destroyBlock() adds scoreValue (= maxHp) for the parent, and trySplitBlock()
 * then produces `pieces` fragments whose scoreValues (maxHp / pieces each) sum
 * back to maxHp. Both halves are real, so 2x is a genuine upper bound -- not a
 * guess. Splitting is conditional (HP, shape and size thresholds), which only
 * ever makes the real score smaller.
 */
function bb_normal_block_score(int $level): float {
    return floor(bb_effective_block_hp($level)) * 2.0;
}

/**
 * Upper bound on the score one boss block can contribute.
 *
 * GameEngine.kt:1296 passes bossHp(level) for the final spawn of a boss level,
 * and bossHp() = generateBlockHP(level) * 2 — so the HP cap applies here too,
 * and, as on an ordinary block, this branch does NOT apply levelConfig's
 * hpMultiplier. spawnBlockAt() then multiplies by the boss-type multiplier
 * (max 4.0 for TITAN) and sets scoreValue = maxHp.
 *
 * destroyBlock() adds scoreValue, then adds scoreValue * 2 for being a boss
 * (3x total). Fragments inherit isBoss = false, so they do NOT get the 2x --
 * they contribute about 1x more. Hence 4x, not the 5x this file used to
 * assume.
 */
function bb_boss_block_score(int $level): float {
    $base  = max(1.0, min((float) BB_MAX_BLOCK_HP,
        floor(bb_hp_curve($level) * BB_HP_VARIANCE)));
    $maxHp = max(1.0, floor($base * 2.0 * 4.0));
    return $maxHp * 4.0;
}

/**
 * Maximum score obtainable in a single run of [level], assuming every block
 * rolls the best HP variance and every block splits.
 */
function bb_max_level_score(int $level): float {
    $level   = max(1, min(BB_MAX_LEVEL, $level));
    $n       = bb_block_count($level);
    $isBoss  = ($level % BB_BOSS_EVERY_N_LEVELS === 0);

    // The boss occupies the final spawn slot (isFinal), so on a boss level only
    // n-1 ordinary blocks accompany it.
    $score = ($isBoss ? $n - 1 : $n) * bb_normal_block_score($level);
    if ($isBoss) $score += bb_boss_block_score($level);

    // Combo bonus slack: destroyBlock() adds comboCount*5 per multi-kill.
    $score += $n * 25;

    return $score;
}

/**
 * Cumulative lifetime score that is physically reachable for someone who has
 * reached [level] playing each level roughly once (no farming).
 *
 * Used only as the *floor* of what we accept: real players farm by replaying
 * levels, so the true allowance is this plus the score they could earn in
 * their accumulated verified play time. See bb_score_ceiling().
 */
function bb_max_cumulative_score(int $level): float {
    $level = max(1, min(BB_MAX_LEVEL, $level));
    static $cache = [];
    if (isset($cache[$level])) return $cache[$level];

    $sum = 0.0;
    for ($i = 1; $i <= $level; $i++) {
        $sum += bb_max_level_score($i);
    }
    $cache[$level] = $sum;
    return $sum;
}

/**
 * Total score a device may legitimately hold.
 *
 *   ceiling = (score for clearing every level up to `level` once)
 *           + (points they could earn per second of *server-observed* play)
 *
 * The play-time term is the load-bearing one: it only advances while a verified
 * run token is open, so inflating the score means actually playing, in real
 * time, at the engine's real speed. The two terms add — a player keeps their
 * baseline whether or not they farm.
 *
 * Note there is deliberately NO free grace credit for a new device. An earlier
 * draft granted `NEW_DEVICE_GRACE_SECONDS * RATE_CAP` to anyone with no history,
 * which meant a freshly installed app could immediately claim 3.1M points —
 * precisely the "install, then post a huge score" attack this model exists to
 * stop. A brand-new player's first run fits comfortably under the baseline for
 * their level, and from the second run onward the server has real play time.
 */
function bb_score_ceiling(int $level, float $verifiedPlaySeconds): float {
    $baseline = bb_max_cumulative_score($level) * BB_HEADROOM;
    $farming  = max(0.0, $verifiedPlaySeconds) * BB_SCORE_RATE_CAP;
    return min((float) BB_ABSOLUTE_MAX_SCORE, $baseline + $farming);
}


// ───────────────────────────────────────────────────────────────────────────
//  Run tokens (server-signed; the signing key never ships in the app)
// ───────────────────────────────────────────────────────────────────────────

function bb_server_secret(): string {
    // HMAC_SECRET is retained for the legacy payload signature only.
    // Prefer a dedicated, never-shared server key for run tokens.
    if (defined('RUN_TOKEN_SECRET') && RUN_TOKEN_SECRET !== '') return RUN_TOKEN_SECRET;
    return HMAC_SECRET;
}

/** @return array{0:string,1:array} [token, payload] */
function bb_sign_run(array $payload): array {
    ksort($payload);
    $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
    $sig  = hash_hmac('sha256', $body, bb_server_secret());
    return [base64_encode($body . '.' . $sig), $payload];
}

/** @return array|null payload, or null when the token is malformed/forged. */
function bb_verify_run(?string $token): ?array {
    if ($token === null || $token === '' || strlen($token) > 4096) return null;
    $raw = base64_decode($token, true);
    if ($raw === false) return null;
    $dot = strrpos($raw, '.');
    if ($dot === false) return null;

    $body = substr($raw, 0, $dot);
    $sig  = substr($raw, $dot + 1);
    $expected = hash_hmac('sha256', $body, bb_server_secret());
    if (!hash_equals($expected, $sig)) return null;

    $payload = json_decode($body, true);
    if (!is_array($payload) || !isset($payload['rid'], $payload['did'], $payload['lvl'], $payload['ts'])) {
        return null;
    }
    return $payload;
}


// ───────────────────────────────────────────────────────────────────────────
//  Client fingerprint (observability, not authentication)
// ───────────────────────────────────────────────────────────────────────────

/**
 * Only honour forwarding headers when a proxy we trust actually set them.
 * X-Forwarded-For is client-controlled, so preferring it by default let anyone
 * forge their own IP and defeat every IP-based anti-fraud signal.
 */
function bb_client_ip(): string {
    $remote = $_SERVER['REMOTE_ADDR'] ?? '';
    if (TRUSTED_PROXY_HEADER !== '' && !empty($_SERVER[TRUSTED_PROXY_HEADER])) {
        $v = trim(explode(',', (string) $_SERVER[TRUSTED_PROXY_HEADER])[0]);
        if ($v !== '') return $v;
    }
    return $remote !== '' ? $remote : '0.0.0.0';
}

function bb_normalize_device_id(string $id): string {
    return substr(preg_replace('/[^A-Za-z0-9_\-]/', '', $id) ?? '', 0, 64);
}

/**
 * The operator-granted premium for a device: [is_premium, until_ts].
 *
 * ── Why this exists ──────────────────────────────────────────────────────
 * The admin panel's "premium" button wrote `leaderboard.is_premium`, which is
 * a display flag — it draws a crown next to a name in the scores table. It was
 * never sent to the phone, so granting premium did nothing to the player's
 * actual game, and neither did revoking it.
 *
 * This is the read side of the real thing. app_config.php already runs on every
 * launch and every return to the foreground, so the grant reaches the device
 * over a request that was being made anyway, rather than adding a new poll.
 *
 * ── Why it returns a flag and not just a row ──────────────────────────────
 * `premium_grants` may not exist yet on a database that never ran the
 * migration. If that threw, app_config.php's whole try/catch would swallow it
 * and answer with `blocked: false, premium: <nothing>` — and the client's
 * response to "no premium" is to *clear* local premium. So a missing table
 * would silently strip premium from every paying customer. It degrades to
 * "table absent, say nothing" instead, and the client leaves local state alone.
 *
 * @return array{0: bool, 1: int} [is it active now, until_ts (0 = no expiry)]
 */
function bb_premium_for_device(PDO $pdo, string $deviceId): array {
    if ($deviceId === '') return [false, 0];

    static $schemaOk = null;
    if ($schemaOk === null) {
        try {
            $pdo->query("SELECT 1 FROM premium_grants LIMIT 1");
            $schemaOk = true;
        } catch (PDOException $e) {
            $schemaOk = false;
            error_log('bb_premium_for_device: premium_grants missing, run the MIGRATION block in init_db.sql');
        }
    }
    if (!$schemaOk) return [false, -1];   // -1 = "do not touch the client's state"

    try {
        $st = $pdo->prepare(
            "SELECT is_premium, until_ts FROM premium_grants WHERE device_id = ? LIMIT 1"
        );
        $st->execute([$deviceId]);
        $row = $st->fetch();
    } catch (PDOException $e) {
        error_log('bb_premium_for_device: ' . $e->getMessage());
        return [false, -1];
    }
    if (!$row) return [false, 0];

    $until = (int) $row['until_ts'];
    // until_ts 0 means "no expiry" — an operator gift that does not lapse.
    if ($until > 0 && $until <= time()) return [false, 0];

    return [(int) $row['is_premium'] === 1, $until];
}


// ───────────────────────────────────────────────────────────────────────────
//  Validation
// ───────────────────────────────────────────────────────────────────────────

/** Cheap syntactic validation. @return string|null error message or null */
function bb_validate_shape(array $d): ?string {
    $name = trim((string)($d['name'] ?? ''));
    $len  = mb_strlen($name, 'UTF-8');
    if ($len < 2 || $len > 16) return 'name';
    if (!preg_match('/^[\p{L}\p{N} _\-\.]+$/u', $name)) return 'name';
    if (!isset($d['level']) || !is_numeric($d['level'])) return 'level';
    if (!isset($d['score']) || !is_numeric($d['score'])) return 'score';
    return null;
}

/**
 * Whether the server is still in legacy mode, i.e. no run token has ever been
 * issued.
 *
 * In that state there is no server-observed play time to reason about, so the
 * strict rate cap would judge every returning player against a bare baseline
 * and start rejecting legitimate scores the moment they updated. The rollout
 * guard: until the first token is minted, fall back to a ceiling generous
 * enough that nothing legitimate is refused. The moment the feature is live,
 * the real rule applies.
 */
function bb_legacy_mode(PDO $pdo): bool {
    static $cached = null;
    if ($cached !== null) return $cached;
    try {
        $st = $pdo->query("SELECT 1 FROM run_tokens LIMIT 1");
        $cached = !$st || $st->fetch() === false;
    } catch (PDOException $e) {
        $cached = true;
    }
    return $cached;
}

/**
 * Ceiling to actually enforce, accounting for rollout state.
 */
function bb_effective_ceiling(int $level, float $verifiedPlaySeconds, bool $legacyMode): float {
    $baseline = bb_max_cumulative_score($level) * BB_HEADROOM;
    if ($legacyMode) {
        // No run tokens yet: be permissive rather than lock anyone out.
        return min((float) BB_ABSOLUTE_MAX_SCORE, $baseline + $verifiedPlaySeconds * BB_LEGACY_RATE_CAP);
    }
    return bb_score_ceiling($level, $verifiedPlaySeconds);
}

/**
 * Decide whether [score] is believable for [level] given how much real play
 * time the server has verified for this device.
 *
 * @return array{ok:bool, code:int, msg:string, ceiling:int}
 */
function bb_judge_score(int $level, int $score, float $verifiedPlaySeconds, bool $legacyMode = false): array {
    $ceiling = (int) floor(bb_effective_ceiling($level, $verifiedPlaySeconds, $legacyMode));

    if ($score < 0) {
        return ['ok'=>false, 'code'=>400, 'msg'=>'score_negative', 'ceiling'=>$ceiling];
    }
    if ($score > BB_ABSOLUTE_MAX_SCORE) {
        return ['ok'=>false, 'code'=>400, 'msg'=>'score_absurd', 'ceiling'=>$ceiling];
    }
    if ($score > $ceiling) {
        return ['ok'=>false, 'code'=>400, 'msg'=>'score_unreachable', 'ceiling'=>$ceiling];
    }
    return ['ok'=>true, 'code'=>200, 'msg'=>'ok', 'ceiling'=>$ceiling];
}

/**
 * Server-observed play time for a device, in seconds.
 *
 * Sum of every run that was opened and then submitted/expired, plus a
 * migration allowance for devices that predate run tokens so existing
 * players are not punished on the day they update.
 */
/**
 * Whether the device is currently banned from the app.
 *
 * Checked independently by app_config.php, start_run.php and submit_score.php.
 * The client can be patched to ignore a "you are banned" response, so the
 * check has to be repeated wherever something of value is handed out — not
 * just at the gate the client is told about.
 */
function bb_is_banned(PDO $pdo, string $deviceId): ?array {
    try {
        $st = $pdo->prepare(
            "SELECT reason FROM app_bans WHERE device_id = ? AND is_active = 1 LIMIT 1"
        );
        $st->execute([$deviceId]);
        $row = $st->fetch();
        if (!$row) return null;
        return [
            'reason' => $row['reason'] !== ''
                ? $row['reason']
                : 'با توجه به فعالیت‌های غیرمجاز اخیر، حساب شما مسدود شده است.',
        ];
    } catch (PDOException $e) {
        // If the table is missing (migration not run yet) do not ban anyone.
        error_log('bb_is_banned: ' . $e->getMessage());
        return null;
    }
}

function bb_verified_play_seconds(PDO $pdo, string $deviceId): float {
    static $legacySeeded = [];

    $stmt = $pdo->prepare("SELECT COALESCE(SUM(play_ms),0) AS ms FROM run_tokens WHERE device_id = ?");
    $stmt->execute([$deviceId]);
    $ms = (float) ($stmt->fetch()['ms'] ?? 0);

    if ($ms <= 0.0 && !isset($legacySeeded[$deviceId])) {
        // Devices that predate run tokens: grant credit from account age so a
        // long-time player is never suddenly told their score is impossible.
        $stmt = $pdo->prepare("SELECT UNIX_TIMESTAMP(created_at) AS c FROM leaderboard WHERE device_id = ?");
        $stmt->execute([$deviceId]);
        $row = $stmt->fetch();
        if ($row && !empty($row['c'])) {
            $age = max(0, time() - (int) $row['c']);
            $ms = min($age, 86400 * 30) * 1000.0;
        }
        $legacySeeded[$deviceId] = true;
    }
    return $ms / 1000.0;
}
