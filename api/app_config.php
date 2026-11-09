<?php
/**
 * app_config.php — the one call the app makes before it will let anyone play.
 *
 * Answers two questions the client cannot decide for itself:
 *   1. Is this device banned?
 *   2. Is this version of the app still allowed to run?
 *
 * It also carries the store link, which the client uses for two things: where to
 * send a blocked build to update from, and where to send a player who accepted
 * the in-app rating prompt. One field, one place for the operator to set it.
 *
 * ── Fails open on purpose ────────────────────────────────────────────────
 * If the request times out or the server errors, the app treats the answer as
 * "allowed". Locking every player out because of a network blip would be far
 * more damaging than the one cheater who gets a free run during an outage.
 * Ban enforcement therefore has a second line of defence: submit_score.php and
 * start_run.php independently check the ban.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'GET') { jsonResponse(['error' => 'Method not allowed'], 405); }

$deviceId = bb_normalize_device_id((string)($_GET['device_id'] ?? $_POST['device_id'] ?? ''));
$versionCode = (int)($_GET['version_code'] ?? 0);

// Defaults are permissive: if nothing is configured, nothing is blocked.
$response = [
    'ok'          => true,
    'banned'      => false,
    'ban_reason'  => null,
    'blocked'     => false,
    'block_reason'=> null,
    'block_message'=> null,
    'min_version' => 0,
    'store_url'   => '',
    // Where the rating prompt sends a player. Same value as store_url today;
    // sent under its own key so the two can be pointed apart later without a
    // breaking change to the client.
    'rate_url'    => '',
    'latest'      => null,
    // null means "the server has no opinion" — distinct from false, which means
    // "this player is not premium". See bb_premium_for_device() for why that
    // distinction has to survive to the client.
    'premium'        => null,
    'premium_until'  => 0,
    'server_time'    => time(),
    // Public, non-sensitive diagnostics: lets the app and the panel agree on
    // the operator's clock without trusting the device clock.
    'server_time_zone'  => date_default_timezone_get(),
    'server_time_local' => date('Y-m-d H:i:s P'),
];

if ($versionCode <= 0) {
    // Very old client that does not send it. Still worth answering with the
    // store link, because the rating prompt works on any build.
    jsonResponse($response);
}

try {
    $pdo = getPDO();

    // ── 1. ban ─────────────────────────────────────────────────────────────
    $st = $pdo->prepare(
        "SELECT reason, banned_at FROM app_bans WHERE device_id = ? AND is_active = 1 LIMIT 1"
    );
    $st->execute([$deviceId]);
    $ban = $st->fetch();

    if ($ban) {
        $response['banned'] = true;
        $response['ban_reason'] = $ban['reason'] !== ''
            ? $ban['reason']
            : 'با توجه به فعالیت‌های غیرمجاز اخیر، حساب شما مسدود شده است.';
    }

    // ── 2. version gate ───────────────────────────────────────────────────
    try {
        $st = $pdo->query(
            "SELECT version_code, version_name, is_enabled, is_latest, is_published,
                    force_message, store_url
               FROM app_versions ORDER BY version_code DESC"
        );
    } catch (PDOException $e) {
        // Deploy-order safety: this endpoint is called by every running
        // client, so it must keep working for the few seconds between the
        // code upload and the schema migration. Without the column every row
        // is treated as published, which is the pre-migration behaviour.
        error_log('app_config: is_published missing, using fallback');
        $st = $pdo->query(
            "SELECT version_code, version_name, is_enabled, is_latest,
                    1 AS is_published, force_message, store_url
               FROM app_versions ORDER BY version_code DESC"
        );
    }
    $versions = $st->fetchAll();

    $minVersion = 0;
    $latestRow = null;
    $thisRow = null;
    $enabledCodes = [];
    $publishedEnabledCodes = [];

    foreach ($versions as $v) {
        $code = (int) $v['version_code'];
        $published = (int) ($v['is_published'] ?? 1) === 1;
        // `latest` and `min_version` are public promises. A draft row must
        // never move either one, otherwise saving a work-in-progress build
        // would prompt or block every player before the operator publishes it.
        if ($published && (int) $v['is_latest'] === 1) $latestRow = $v;
        if ((int) $v['is_enabled'] === 1) {
            $enabledCodes[] = $code;
            if ($published) $publishedEnabledCodes[] = $code;
            if ($published && ($minVersion === 0 || $code < $minVersion)) $minVersion = $code;
        }
        // The current row is looked up across published and draft rows on
        // purpose: a developer running a draft build should not be locked out
        // of their own test build.
        if ($code === $versionCode) $thisRow = $v;
    }

    $response['min_version'] = $minVersion;
    $response['latest'] = $latestRow ? [
        'version_code' => (int) $latestRow['version_code'],
        'version_name' => $latestRow['version_name'],
        'store_url'    => $latestRow['store_url'],
    ] : null;
    // Prefer the latest row's link, falling back to the newest configured one.
    // An operator who has just added a version and not filled its store_url in
    // should not silently switch the rating prompt off for everyone.
    $storeUrl = trim((string)($latestRow['store_url'] ?? ''));
    if ($storeUrl === '' && !empty($versions)) {
        foreach ($versions as $v) {
            // A draft's store link is not public yet.
            if ((int) ($v['is_published'] ?? 1) !== 1) continue;
            if (trim((string)$v['store_url']) !== '') { $storeUrl = trim((string)$v['store_url']); break; }
        }
    }
    $response['store_url'] = $storeUrl;
    $response['rate_url'] = $storeUrl;

    if (count($enabledCodes) === 0) {
        // No version is explicitly enabled. Rather than locking everyone out,
        // treat the build the app just reported as acceptable and log nothing.
        jsonResponse($response);
    }

    $thisEnabled = $thisRow !== null && (int) $thisRow['is_enabled'] === 1;
    $tooOld = $versionCode < $minVersion;

    if (!$thisEnabled || $tooOld) {
        $response['blocked'] = true;
        $response['block_reason'] = $tooOld ? 'version_too_old' : 'version_disabled';
        $response['min_version'] = $minVersion;
        $response['block_message'] = $thisRow && $thisRow['force_message'] !== null && $thisRow['force_message'] !== ''
            ? $thisRow['force_message']
            : ($tooOld
                ? 'برای ادامهٔ بازی باید به آخرین نسخه بروید.'
                : 'این نسخه از بازی غیرفعال شده است.');
    }

    // ── 3. operator-granted premium ──────────────────────────────────────
    //
    // Deliberately computed even when the player is banned or blocked: a
    // revoked grant should be reflected in the stored state either way, and the
    // client applies this field independently of the access decision.
    [$premiumActive, $premiumUntil] = bb_premium_for_device($pdo, $deviceId);
    $response['premium']       = $premiumUntil >= 0 ? $premiumActive : null;
    $response['premium_until'] = $premiumUntil > 0 ? $premiumUntil : 0;

    jsonResponse($response);
} catch (PDOException $e) {
    error_log('app_config error: ' . $e->getMessage());
    // Fail open: a database hiccup must not brick the game for everyone.
    jsonResponse($response);
}
