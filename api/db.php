<?php
// All player-facing dates and daily buckets are Iran time. The host clock is
// UTC, so without this PHP's date()/strtotime() and the MySQL session below
// disagreed with the times the panel and players see. Keep this before any
// date() call, including the ones in config.php's includes.
date_default_timezone_set('Asia/Tehran');

require_once __DIR__ . '/config.php';

function getPDO(): PDO {
    static $pdo = null;
    if ($pdo !== null) return $pdo;
    $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=' . DB_CHARSET;
    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        // MySQL native prepares bind every parameter as a string, which makes
        // `LIMIT ?` a syntax error. Every LIMIT/OFFSET in this codebase is
        // inlined as a validated integer instead of bound — do not turn this
        // back on without also switching those to PDO::PARAM_INT.
        PDO::ATTR_EMULATE_PREPARES => false,
    ];
    $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
    $pdo->exec("SET NAMES utf8mb4");
    // Buckets are computed in PHP (date()) and in MySQL (CURDATE()); without
    // this the two can disagree and every daily chart is shifted a day.
    $pdo->exec("SET time_zone = '" . date('P') . "'");
    return $pdo;
}

function jsonResponse($data, int $code = 200): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    bb_send_cors_headers();
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, X-Requested-With');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/**
 * Emits CORS headers only for a real, specific origin.
 *
 * ── Why the check lives here and not in .htaccess ──────────────────────────
 * The obvious fix is `Header unset Access-Control-Allow-Origin` in .htaccess.
 * That does not work, and quietly: mod_headers runs during response
 * *preparation*, but PHP's header() writes into the same table *afterwards*,
 * so PHP's value is the one that survives. A header "unset" in .htaccess is
 * therefore silently ineffective against a header set in PHP, which is why the
 * wildcard survived every attempt to remove it from the config file.
 *
 * This API is called by a native Android client and by the admin panel, both
 * same-origin from the browser's point of view. Neither needs CORS at all. The
 * wildcard was pure liability: it let any site a player visited drive
 * ad_event.php, tickets.php and submit_score.php from their browser and use
 * this host as a spam proxy, while stripping the origin from the access log.
 *
 * So: no wildcard, ever. Set BB_ALLOWED_ORIGIN to an exact origin if a real
 * browser client ever needs one.
 */
function bb_send_cors_headers(): void {
    $origin = ALLOWED_ORIGIN;
    if ($origin === '' || $origin === '*') return;   // no CORS at all
    header('Access-Control-Allow-Origin: ' . $origin);
    header('Vary: Origin');
}

/** Reads and JSON-decodes the request body. */
function readJsonBody(): array {
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') return [];
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

/**
 * A validated, inlined integer for use in LIMIT/OFFSET.
 *
 * Clamped rather than escaped: these values are never user-supplied
 * untrusted strings by the time they get here, and inlining an int is the
 * only thing that works with native prepares.
 */
function clampInt($value, int $min, int $max, int $default): int {
    if (!is_numeric($value)) return $default;
    return max($min, min($max, (int) $value));
}

/** @deprecated Use bb_client_ip() from anticheat.php — this trusts client headers. */
function getClientIP(): string {
    return bb_client_ip();
}

/**
 * The account this device is linked to, or null.
 *
 * Lives here rather than in account.php because account.php is an *endpoint*:
 * requiring it from another endpoint would run its top-level dispatch. Every
 * endpoint that writes something a player would miss if they switched phones
 * (a leaderboard row, a ticket) needs this, so it belongs with the other
 * shared helpers.
 *
 * Returns null rather than throwing when the accounts tables are missing, so a
 * deployment that has not run the account migration yet keeps working — the
 * feature degrades to the old device-scoped behaviour instead of taking score
 * submission down with it.
 */
function bb_account_for_device(PDO $pdo, string $deviceId): ?int {
    static $schemaOk = null;
    if ($schemaOk === null) {
        try {
            $pdo->query("SELECT 1 FROM device_links LIMIT 1");
            $schemaOk = true;
        } catch (PDOException $e) {
            $schemaOk = false;
            error_log('bb_account_for_device: device_links missing, run the account migration');
        }
    }
    if (!$schemaOk || $deviceId === '') return null;
    try {
        $st = $pdo->prepare("SELECT account_id FROM device_links WHERE device_id = ?");
        $st->execute([$deviceId]);
        $row = $st->fetch();
        return $row ? (int) $row['account_id'] : null;
    } catch (PDOException $e) {
        return null;
    }
}
