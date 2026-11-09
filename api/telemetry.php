<?php
/**
 * telemetry.php — ingest for the gameplay monitoring panel (admin/monitor.php).
 *
 * ── What this is, and what it is not ───────────────────────────────────────
 * This is the pipeline that answers "is the game actually being played right
 * now, and how". The client batches (see Telemetry.kt) and posts one packet
 * per flush; this writes it to three tables with three different retention
 * profiles.
 *
 * It is NOT an authentication surface and nothing here is treated as one. The
 * device id is client-supplied, the counters are client-supplied, and any of it
 * can be forged. So:
 *
 *  - It never affects score, coins, lives or leaderboard standing. Those go
 *    through server-signed run tokens (start_run.php / anticheat.php).
 *  - The panel labels these numbers as directional.
 *  - What it DOES do is refuse to be used as a write amplifier, because that is
 *    the one way unauthenticated telemetry hurts a shared host: a flood here
 *    fills the database and gets the whole account suspended.
 *
 * ── Why the rate limit is shaped the way it is ────────────────────────────
 * The client legitimately posts about once every 12 seconds while playing, and
 * a background flush on every level boundary. So the floor has to be well below
 * that: 4 seconds gives a 3x burst allowance for level transitions without
 * letting a single install turn the endpoint into a write loop.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

/** Ceilings. The client sends far less; these exist so one packet cannot be huge. */
const TELEMETRY_MAX_EVENTS  = 60;
const TELEMETRY_MAX_BYTES   = 32 * 1024;

/** Anti-amplifier limits. */
const TELEMETRY_MIN_GAP_SECONDS = 4;
/** Per device, per rolling 24h. Roughly 40x what a heavy player needs. */
const TELEMETRY_MAX_PACKETS_DAY = 4000;
const TELEMETRY_MAX_EVENTS_DAY  = 24000;

/** Retention, mirrored in init_db.sql. */
const TELEMETRY_EVENT_KEEP_DAYS = 7;
const TELEMETRY_ROLLUP_KEEP_DAYS = 90;

/** How many packets between opportunistic prunes. */
const TELEMETRY_PRUNE_EVERY = 50;

$data = readJsonBody();
if ($data === []) { jsonResponse(['error' => 'Invalid JSON'], 400); }

$raw = file_get_contents('php://input');
if ($raw !== false && strlen($raw) > TELEMETRY_MAX_BYTES) {
    jsonResponse(['error' => 'too_large'], 413);
}

$deviceId = bb_normalize_device_id((string)($data['device_id'] ?? ''));
if ($deviceId === '') { jsonResponse(['error' => 'device_id required'], 400); }

$ip  = bb_client_ip();
$now = time();
// Bucket on the local (Asia/Tehran) hour, not the UTC hour. The panel groups
// and labels these rows with date(), so a UTC bucket rendered as 15:30 on the
// chart. strtotime() uses the timezone set in db.php.
$hour = strtotime(date('Y-m-d H:00:00', $now));

$appVersion = substr(preg_replace('/[^0-9A-Za-z.\-+]/', '', (string)($data['app_version'] ?? '')) ?? '', 0, 24);
$versionCode = clampInt($data['version_code'] ?? 0, 0, 100000, 0);

// ── normalise the aggregates ────────────────────────────────────────────────
// Everything is clamped to a sane non-negative range. A negative or absurd
// value would not error on insert, it would poison every SUM() and AVG() the
// panel runs, so the floor is enforced here rather than trusted.
$aggIn = is_array($data['agg'] ?? null) ? $data['agg'] : [];
$agg = [
    'runs'      => clampInt($aggIn['runs'] ?? 0, 0, 10000, 0),
    'levels'    => clampInt($aggIn['levels'] ?? 0, 0, 10000, 0),
    'blocks'    => clampInt($aggIn['blocks'] ?? 0, 0, 2000000, 0),
    'coins'     => clampInt($aggIn['coins'] ?? 0, 0, 1000000, 0),
    'shots'     => clampInt($aggIn['shots'] ?? 0, 0, 2000000, 0),
    'combos'    => clampInt($aggIn['combos'] ?? 0, 0, 2000000, 0),
    'max_combo' => clampInt($aggIn['max_combo'] ?? 0, 0, 65535, 0),
    'active_ms' => clampInt($aggIn['active_ms'] ?? 0, 0, 86400000, 0),
];

/**
 * Event types the panel knows how to render. An unknown type is dropped rather
 * than stored, so an old build of the app cannot fill the table with rows the
 * dashboard has no column for.
 */
const TELEMETRY_EVENT_TYPES = [
    'run_start', 'run_end', 'level_complete', 'combo', 'powerup', 'session_start',
];

$eventsIn = is_array($data['events'] ?? null) ? $data['events'] : [];
$events = [];
foreach (array_slice($eventsIn, 0, TELEMETRY_MAX_EVENTS) as $ev) {
    if (!is_array($ev)) continue;
    $type = (string)($ev['t'] ?? '');
    if (!in_array($type, TELEMETRY_EVENT_TYPES, true)) continue;
    $events[] = [
        'type'    => $type,
        'level'   => clampInt($ev['l'] ?? 0, 0, 9999, 0),
        'value'   => clampInt($ev['v'] ?? 0, -2000000000, 2000000000, 0),
        'dur'     => clampInt($ev['d'] ?? 0, 0, 86400000, 0),
        'meta'    => substr((string)($ev['m'] ?? ''), 0, 191),
        'client'  => (int)($ev['ts'] ?? 0),
    ];
}

$dayStart = $now - 86400;

try {
    $pdo = getPDO();

    // ── rate limit ─────────────────────────────────────────────────────────
    // Per device and per IP. A device id is client-supplied and trivially
    // rotated, so on its own it stops nothing; the IP is what actually stops a
    // single host from cycling ids. Both share one table with ad_event.php, and
    // bb_throttle_key() keeps the key inside rate_limit.ip's VARCHAR(64).
    foreach ([bb_throttle_key('tel:', $deviceId), bb_throttle_key('telp:', $ip)] as $key) {
        if ($key === 'tel:' || $key === 'telp:') continue;

        $stmt = $pdo->prepare("SELECT last_submit, strikes FROM rate_limit WHERE ip = ?");
        $stmt->execute([$key]);
        $row = $stmt->fetch();

        if ($row) {
            $elapsed = $now - (int)$row['last_submit'];
            $strikes = (int)($row['strikes'] ?? 0);
            if ($elapsed < TELEMETRY_MIN_GAP_SECONDS) {
                if ($strikes < 3) {
                    $pdo->prepare("UPDATE rate_limit SET strikes = strikes + 1 WHERE ip = ?")->execute([$key]);
                } else {
                    jsonResponse(['error' => 'rate_limited'], 429);
                }
            } else {
                $pdo->prepare("UPDATE rate_limit SET last_submit = ?, strikes = 0 WHERE ip = ?")
                    ->execute([$now, $key]);
            }
        } else {
            $pdo->prepare("INSERT INTO rate_limit (ip, last_submit) VALUES (?,?)")->execute([$key, $now]);
        }
    }

    // ── daily caps ─────────────────────────────────────────────────────────
    // Cheap because telemetry_presence.packets is already indexed by its
    // PRIMARY KEY device_id — no scan of the event log to answer this.
    $stmt = $pdo->prepare("SELECT packets FROM telemetry_presence WHERE device_id = ?");
    $stmt->execute([$deviceId]);
    $presence = $stmt->fetch();
    $packetsSoFar = (int)($presence['packets'] ?? 0);
    if ($packetsSoFar >= TELEMETRY_MAX_PACKETS_DAY) {
        jsonResponse(['error' => 'daily_cap'], 429);
    }

    $pdo->beginTransaction();

    // ── presence (this is also what "online now" means) ───────────────────
    $pdo->prepare(
        "INSERT INTO telemetry_presence (device_id, last_seen, last_level, app_version, packets)
         VALUES (?, ?, ?, ?, 1)
         ON DUPLICATE KEY UPDATE
            last_seen = VALUES(last_seen),
            last_level = GREATEST(last_level, VALUES(last_level)),
            app_version = IF(VALUES(app_version) = '', app_version, VALUES(app_version)),
            packets = packets + 1"
    )->execute([$deviceId, $now, $agg['runs'] > 0 ? max(0, $events[0]['level'] ?? 0) : 0, $appVersion]);

    // ── hourly rollup ──────────────────────────────────────────────────────
    // Only written when something happened. An idle player producing a row every
    // 12 seconds of pure nothing is how a monitoring table becomes the largest
    // table in the database.
    $aggNonZero = $agg['runs'] || $agg['levels'] || $agg['blocks'] || $agg['coins']
               || $agg['shots'] || $agg['combos'] || $agg['active_ms'];
    if ($aggNonZero) {
        $pdo->prepare(
            "INSERT INTO telemetry_rollups
                (bucket_ts, device_id, runs, levels, blocks, coins, shots, combos, max_combo, active_ms)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE
                runs      = runs + VALUES(runs),
                levels    = levels + VALUES(levels),
                blocks    = blocks + VALUES(blocks),
                coins     = coins + VALUES(coins),
                shots     = shots + VALUES(shots),
                combos    = combos + VALUES(combos),
                active_ms = active_ms + VALUES(active_ms),
                -- max_combo is a high-water mark, not a sum, so it must not add.
                max_combo = GREATEST(max_combo, VALUES(max_combo))"
        )->execute([
            $hour, $deviceId,
            $agg['runs'], $agg['levels'], $agg['blocks'], $agg['coins'],
            $agg['shots'], $agg['combos'], $agg['max_combo'], $agg['active_ms'],
        ]);
    }

    // ── point events ───────────────────────────────────────────────────────
    if ($events) {
        $ins = $pdo->prepare(
            "INSERT INTO telemetry_events
                (device_id, event_type, level, value, duration_ms, meta, at_ts, client_ts)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        );
        $highestLevel = 0;
        foreach ($events as $e) {
            $ins->execute([$deviceId, $e['type'], $e['level'], $e['value'],
                           $e['dur'], $e['meta'], $now, max(0, $e['client'])]);
            if ($e['level'] > $highestLevel) $highestLevel = $e['level'];
        }

        // Keep the "current level" the panel shows in the live feed honest even
        // when this packet carried no aggregate work at all.
        if ($highestLevel > 0) {
            $pdo->prepare(
                "UPDATE telemetry_presence SET last_level = GREATEST(last_level, ?) WHERE device_id = ?"
            )->execute([$highestLevel, $deviceId]);
        }
    }

    $pdo->commit();

    // ── opportunistic prune ───────────────────────────────────────────────
    // Safety net, not the retention strategy: init_db.sql tells you to run
    // these from cron. Done here at a 1-in-50 rate so a host with no cron does
    // not accumulate rows forever, and at that rate it costs nothing.
    if (random_int(1, TELEMETRY_PRUNE_EVERY) === 1) {
        try {
            $pdo->exec("DELETE FROM telemetry_events WHERE at_ts < " . ($now - TELEMETRY_EVENT_KEEP_DAYS * 86400));
            $pdo->exec("DELETE FROM telemetry_rollups WHERE bucket_ts < " . ($now - TELEMETRY_ROLLUP_KEEP_DAYS * 86400));
            $pdo->exec("DELETE FROM rate_limit WHERE ip LIKE 'tel:%'   AND last_submit < " . ($now - 86400));
            $pdo->exec("DELETE FROM rate_limit WHERE ip LIKE 'telp:%' AND last_submit < " . ($now - 86400));
        } catch (PDOException $pe) {
            // A failed prune must never fail the ingest.
            error_log('telemetry prune: ' . $pe->getMessage());
        }
    }

    jsonResponse(['ok' => true, 'server_time' => $now]);
} catch (PDOException $e) {
    error_log('telemetry DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
