<?php
/**
 * monitor_data.php — the JSON behind admin/monitor.php.
 *
 * Split out from the page so the page can poll it every few seconds and update
 * numbers and charts in place. Rendering the whole PHP page that often would
 * re-send the nav, the charts config and every table row, and on a shared host
 * that is the difference between a live dashboard and a dashboard that gets
 * your account throttled.
 *
 * ── Cost control ──────────────────────────────────────────────────────────
 * Every query here is bounded and hits an index. The two that could have been
 * expensive are the hourly series (idx_bucket) and the combo distribution
 * (idx_type_at). The live feed is ordered by the primary key. Nothing scans
 * telemetry_events without an at_ts bound.
 *
 * ── Honest by construction ────────────────────────────────────────────────
 * All of this is client-reported and therefore forgeable. It is labelled as
 * directional on the page. Nothing here feeds back into gameplay.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

// Reads only, but requireAuth() answers a JSON 401 for XHR — which is why the
// check above it is not cosmetic.
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$now  = time();
// Local (Asia/Tehran) hour bucket. Using the UTC epoch boundary made the
// 24-hour chart label every point at :30 instead of the top of the hour.
$hour = strtotime(date('Y-m-d H:00:00', $now));
$from24 = $hour - 23 * 3600;      // 24 hourly buckets, inclusive
$dayStart = $now - 86400;
$liveCutoff = $now - 300;         // "online now" == seen in the last 5 min

try {
    $pdo = getPDO();

    /**
     * Runs a query, returning [] when the telemetry tables are not installed yet.
     *
     * The panel is deployed before the MIGRATION block in init_db.sql has been
     * run more often than anyone would like to admit, and a page of raw PDO
     * exceptions is useless in that state. This degrades to zeros instead.
     */
    $safe = function (string $sql, array $params = []) use ($pdo): array {
        try {
            $st = $pdo->prepare($sql);
            $st->execute($params);
            return $st->fetchAll();
        } catch (PDOException $e) {
            error_log('monitor_data: ' . $e->getMessage());
            return [];
        }
    };

    // ── presence ───────────────────────────────────────────────────────────
    $presence = $safe("SELECT
            SUM(last_seen >= ?)      AS live,
            SUM(last_seen >= ?)      AS live15,
            COUNT(*)                 AS total,
            AVG(packets)             AS avg_packets
        FROM telemetry_presence", [$liveCutoff, $now - 900]);

    $p = $presence[0] ?? [];
    $liveNow    = (int)($p['live'] ?? 0);
    $live15     = (int)($p['live15'] ?? 0);
    $everSeen   = (int)($p['total'] ?? 0);

    // ── hourly series ──────────────────────────────────────────────────────
    $series = $safe("
        SELECT bucket_ts,
               COUNT(DISTINCT device_id) AS players,
               COALESCE(SUM(runs),0)      AS runs,
               COALESCE(SUM(levels),0)    AS levels,
               COALESCE(SUM(blocks),0)    AS blocks,
               COALESCE(SUM(shots),0)     AS shots,
               COALESCE(SUM(coins),0)     AS coins,
               COALESCE(SUM(combos),0)    AS combos,
               COALESCE(SUM(active_ms),0) AS active_ms
        FROM telemetry_rollups
        WHERE bucket_ts >= ?
        GROUP BY bucket_ts
        ORDER BY bucket_ts", [$from24]);

    // Fill the gaps. A chart that skips empty hours renders a straight line
    // across a quiet night and reads as "steady", which is the one thing a
    // monitoring chart must never do.
    $byBucket = [];
    foreach ($series as $r) { $byBucket[(int)$r['bucket_ts']] = $r; }

    $labels = []; $players = []; $runs = []; $levels = [];
    $blocks = []; $shots = []; $coins = []; $activeMin = [];
    for ($i = 23; $i >= 0; $i--) {
        $b = $hour - $i * 3600;
        $r = $byBucket[$b] ?? null;
        $labels[]   = date('H:i', $b);
        $players[]  = (int)($r['players'] ?? 0);
        $runs[]     = (int)($r['runs'] ?? 0);
        $levels[]   = (int)($r['levels'] ?? 0);
        $blocks[]   = (int)($r['blocks'] ?? 0);
        $shots[]    = (int)($r['shots'] ?? 0);
        $coins[]    = (int)($r['coins'] ?? 0);
        $activeMin[]= round(((int)($r['active_ms'] ?? 0)) / 60000, 1);
    }

    // ── today ──────────────────────────────────────────────────────────────
    $today = $safe("
        SELECT
            COALESCE(SUM(runs),0)      AS runs,
            COALESCE(SUM(levels),0)    AS levels,
            COALESCE(SUM(blocks),0)    AS blocks,
            COALESCE(SUM(coins),0)     AS coins,
            COALESCE(SUM(shots),0)     AS shots,
            COALESCE(SUM(active_ms),0) AS active_ms,
            COALESCE(MAX(max_combo),0) AS max_combo
        FROM telemetry_rollups WHERE bucket_ts >= ?", [$hour - 23 * 3600]);
    $t = $today[0] ?? [];

    // ── run durations & outcomes, from the point events ────────────────────
    $durations = $safe("
        SELECT duration_ms FROM telemetry_events
        WHERE event_type = 'run_end' AND at_ts >= ? AND duration_ms > 0", [$dayStart]);
    $durValues = array_map(fn($r) => (int)$r['duration_ms'], $durations);
    sort($durValues);
    $avgRunMs = $durValues ? (int)round(array_sum($durValues) / count($durValues)) : 0;
    $medianRunMs = $durValues ? (int)$durValues[(int)floor(count($durValues) / 2)] : 0;

    $outcomes = $safe("
        SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(meta, ',', 1), '=', -1) AS outcome,
               COUNT(*) AS c
        FROM telemetry_events
        WHERE event_type = 'run_end' AND at_ts >= ?
        GROUP BY outcome", [$dayStart]);

    // `meta` for run_end is 'outcome=hit,blocks=204,coins=27,combo=14', so the
    // outcome is the value of the FIRST key. Taking the last one (what
    // SUBSTRING_INDEX(meta,'=',-1) does) reads 'combo=14' and returns '14',
    // which then rendered as a run outcome labelled "14" on the panel.
    //
    // This is coupled to the client's format for this one event type. If the
    // format ever changes, this is the line that breaks — a dedicated
    // `outcome` column would be the real fix and is not worth a migration for
    // one categorical field.

    // ── combo distribution ─────────────────────────────────────────────────
    // This is the chart that answers "is the combo mechanic actually landing".
    $comboRows = $safe("
        SELECT value, COUNT(*) AS c
        FROM telemetry_events
        WHERE event_type = 'combo' AND at_ts >= ?
        GROUP BY value ORDER BY value", [$now - 7 * 86400]);

    // ── live feed ──────────────────────────────────────────────────────────
    $feed = $safe("
        SELECT device_id, event_type, level, value, duration_ms, meta, at_ts
        FROM telemetry_events
        ORDER BY id DESC LIMIT 25");

    // ── version spread ─────────────────────────────────────────────────────
    // A build that suddenly stops sending telemetry is a build that is crashing
    // before BlockBoomApp.onCreate finishes. This is how that shows up.
    $versions = $safe("
        SELECT app_version, COUNT(*) AS devices, MAX(last_seen) AS last_seen
        FROM telemetry_presence
        WHERE last_seen >= ? AND app_version <> ''
        GROUP BY app_version ORDER BY devices DESC LIMIT 8", [$now - 7 * 86400]);

    // ── deepest levels reached ─────────────────────────────────────────────
    // Includes level_complete, not just run_start/run_end: a player deep in a
    // run has not ended it yet, and excluding the completion events under-
    // reported exactly the players this chart exists to find.
    $topLevels = $safe("
        SELECT level, COUNT(DISTINCT device_id) AS players
        FROM telemetry_events
        WHERE event_type IN ('run_start', 'run_end', 'level_complete')
          AND at_ts >= ? AND level > 0
        GROUP BY level ORDER BY level DESC LIMIT 10", [$now - 7 * 86400]);

    // ── self health ────────────────────────────────────────────────────────
    $t0 = microtime(true);
    $safe("SELECT 1");
    $queryMs = round((microtime(true) - $t0) * 1000, 1);

    // Ingest rate: how much the app is actually talking to us right now. A drop
    // to zero while players are online means the clients are failing to reach
    // the endpoint — which is exactly the kind of thing this page exists to
    // notice, and which no other counter here would surface.
    $ingest = $safe("
        SELECT COUNT(*) AS packets, COUNT(DISTINCT device_id) AS devices
        FROM telemetry_events WHERE at_ts >= ?", [$now - 3600]);

    echo json_encode([
        'ok' => true,
        'server_time' => $now,
        'health' => [
            'query_ms' => $queryMs,
            'tables' => !empty($series) || $everSeen > 0,
            'packets_1h' => (int)($ingest[0]['packets'] ?? 0),
            'devices_1h' => (int)($ingest[0]['devices'] ?? 0),
        ],
        'live' => [
            'online_now' => $liveNow,
            'online_15m' => $live15,
            'ever_seen'  => $everSeen,
        ],
        'today' => [
            'runs'        => (int)($t['runs'] ?? 0),
            'levels'      => (int)($t['levels'] ?? 0),
            'blocks'      => (int)($t['blocks'] ?? 0),
            'coins'       => (int)($t['coins'] ?? 0),
            'shots'       => (int)($t['shots'] ?? 0),
            'active_min'  => round(((int)($t['active_ms'] ?? 0)) / 60000, 1),
            'max_combo'   => (int)($t['max_combo'] ?? 0),
            'avg_run_ms'  => $avgRunMs,
            'median_run_ms' => $medianRunMs,
        ],
        'series' => [
            'labels' => $labels,
            'players' => $players,
            'runs' => $runs,
            'levels' => $levels,
            'blocks' => $blocks,
            'shots' => $shots,
            'coins' => $coins,
            'active_min' => $activeMin,
        ],
        'combos' => array_map(
            fn($r) => ['value' => (int)$r['value'], 'count' => (int)$r['c']],
            $comboRows
        ),
        'outcomes' => array_map(
            fn($r) => ['outcome' => (string)($r['outcome'] ?: 'unknown'), 'count' => (int)$r['c']],
            $outcomes
        ),
        'feed' => array_map(fn($r) => [
            'device'    => (string)$r['device_id'],
            'type'      => (string)$r['event_type'],
            'level'     => (int)$r['level'],
            'value'     => (int)$r['value'],
            'duration'  => (int)$r['duration_ms'],
            'meta'      => (string)$r['meta'],
            'at'        => (int)$r['at_ts'],
        ], $feed),
        'versions' => array_map(fn($r) => [
            'version' => (string)$r['app_version'],
            'devices' => (int)$r['devices'],
            'last_seen' => (int)$r['last_seen'],
        ], $versions),
        'top_levels' => array_map(fn($r) => [
            'level' => (int)$r['level'],
            'players' => (int)$r['players'],
        ], $topLevels),
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);

} catch (PDOException $e) {
    error_log('monitor_data fatal: ' . $e->getMessage());
    http_response_code(500);
    echo json_encode(['ok' => false, 'error' => 'Server error'], JSON_UNESCAPED_UNICODE);
}
