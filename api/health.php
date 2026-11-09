<?php
/**
 * health.php — configuration status for the admin panel.
 *
 * The panel and the API run on different hosts, so the panel cannot read
 * api/config.php to check whether the anti-cheat secret is still the
 * placeholder. This reports the answer instead.
 *
 * ── Why this needs a key ──────────────────────────────────────────────────
 * It used to be fully public, which turned it into a deployment-readiness
 * report for anyone on the internet, with no rate limit: it disclosed whether
 * the anti-cheat signing key was still the CHANGE_ME placeholder (i.e. whether
 * submissions were taking the weak legacy path), which migrations were
 * missing, and the raw PDO error text on failure. That is a large amount of
 * free reconnaissance for one GET.
 *
 * So the detail now requires X-Admin-Key. Without it the endpoint still
 * answers — uptime checks must keep working — but only with the liveness
 * booleans. No key material, no schema map, no driver errors.
 *
 * The DB error message is never returned even to an authenticated caller: it
 * goes to error_log instead, and the response carries a stable code. Driver
 * text routinely contains the hostname, the schema name and sometimes the
 * rejected fragment of a query, none of which belong in a response body.
 */
require_once __DIR__ . '/db.php';

/**
 * Does a table exist in this schema?
 *
 * information_schema rather than `SHOW TABLES LIKE ?`: MariaDB does not accept
 * a bound parameter in a SHOW statement, so the obvious version throws a
 * PDOException and takes the whole health check down with it.
 */
function tableExists(PDO $pdo, string $table): bool {
    $st = $pdo->prepare(
        "SELECT 1 FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? LIMIT 1"
    );
    $st->execute([$table]);
    return (bool)$st->fetch();
}

/** Column names of a table, or [] if the table does not exist. */
function columnNames(PDO $pdo, string $table): array {
    $st = $pdo->prepare(
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
    );
    $st->execute([$table]);
    return array_column($st->fetchAll(), 'COLUMN_NAME');
}

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'GET') { jsonResponse(['error' => 'Method not allowed'], 405); }

$runTokenOk  = defined('RUN_TOKEN_SECRET')
    && RUN_TOKEN_SECRET !== ''
    && strpos(RUN_TOKEN_SECRET, 'CHANGE_ME') !== 0;
$adminKeyOk  = defined('ADMIN_API_KEY')
    && ADMIN_API_KEY !== ''
    && strpos(ADMIN_API_KEY, 'CHANGE_ME') !== 0;
$proxyOk     = TRUSTED_PROXY_HEADER !== '' && TRUSTED_PROXY_HEADER !== null;

// Constant-time compare, and fail closed: an unset key must not authenticate.
$presented = (string)($_SERVER['HTTP_X_ADMIN_KEY'] ?? '');
$isPanel   = $adminKeyOk && $presented !== '' && hash_equals(ADMIN_API_KEY, $presented);

$dbOk = true;
$schemaOk = true;
$schema = [];
if ($isPanel) {
    try {
        $pdo = getPDO();
        // The columns submit_score.php depends on. If the migration was not run,
        // every score submission fails at the UPDATE and the panel looks fine.
        //
        // information_schema, not `SHOW TABLES LIKE ?`: MariaDB does not accept a
        // bound parameter in a SHOW statement, so the obvious version throws a
        // PDOException and reports the database as broken.
        foreach (['run_tokens', 'cheat_flags', 'coin_grants', 'referrals', 'notifications',
                  'notification_reads', 'tickets', 'premium_grants',
                  'telemetry_presence', 'telemetry_rollups', 'telemetry_events'] as $t) {
            $schema[$t] = tableExists($pdo, $t);
        }
        $cols = columnNames($pdo, 'leaderboard');
        $schema['leaderboard.score'] = in_array('score', $cols, true);
        $schema['leaderboard.banned'] = in_array('banned', $cols, true);
        $schema['leaderboard.score_rate'] = in_array('score_rate', $cols, true);

        // Columns the ticket endpoints read.
        //
        // These are here because their absence shipped as a real outage: the
        // MIGRATION block at the bottom of init_db.sql had never been run
        // against the live database, so `notification_reads` did not exist and
        // `tickets.last_read_at` was not a column. tickets.php and
        // fetch_notifications.php returned 500 for every player — no ticket,
        // no notifications — while this very endpoint kept reporting
        // healthy: true, because nothing here looked for the two.
        if (tableExists($pdo, 'tickets')) {
            $tcols = columnNames($pdo, 'tickets');
            $schema['tickets.last_read_at'] = in_array('last_read_at', $tcols, true);
            $schema['tickets.last_admin_reply_at'] = in_array('last_admin_reply_at', $tcols, true);
        } else {
            $schema['tickets.last_read_at'] = false;
            $schema['tickets.last_admin_reply_at'] = false;
        }

        // A missing column anywhere the code reads is a broken endpoint, so it
        // has to make `healthy` false rather than being informational. This
        // lives in its own flag because $allGood is composed further down.
        foreach ($schema as $present) {
            if (!$present) $schemaOk = false;
        }
    } catch (PDOException $e) {
        $dbOk = false;
        error_log('health.php db check: ' . $e->getMessage());
    }
} else {
    // Unauthenticated: probe liveness only, and never let a database outage
    // turn into an unauthenticated 503 that looks like the API is down when it
    // is merely not reachable from here.
    try {
        $pdo = getPDO();
        $pdo->query('SELECT 1');
    } catch (PDOException $e) {
        $dbOk = false;
    }
}

$allGood = $dbOk && $schemaOk && $runTokenOk && $adminKeyOk;

$response = [
    'ok'      => true,
    'healthy' => $allGood,
    'db'      => $dbOk,
    'detail'  => $isPanel,
];

if ($isPanel) {
    $response['runToken']   = $runTokenOk;
    $response['adminKey']   = $adminKeyOk;
    $response['trustedProxyHeader'] = $proxyOk;
    $response['schema']     = $schema;
    $response['serverTime'] = time();
    // Visible only to the panel. Makes a host clock/timezone regression
    // diagnosable without FTP access.
    $response['serverTimeZone'] = date_default_timezone_get();
    $response['serverTimeLocal'] = date('Y-m-d H:i:s P');
} else {
    // A caller without the key still learns whether the anti-cheat is live —
    // that much is already public behaviour via app_config.php's gate, and it
    // is what makes a broken deployment visible from outside. Nothing else.
    $response['antiCheatConfigured'] = $runTokenOk;
}

jsonResponse($response, $allGood ? 200 : 503);
