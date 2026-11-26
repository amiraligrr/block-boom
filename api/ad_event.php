<?php
/**
 * ad_event.php — ad impression / click / reward reporting.
 *
 * These events used to require an HMAC signed with HMAC_SECRET, which was
 * compiled into the APK. That is not authentication: anyone who unzipped the
 * APK recovered the key and could mint impressions at will, which is why the
 * panel's numbers could be inflated without bound. The check has been removed
 * instead of being left in place for the appearance of protection.
 *
 * What genuinely limits abuse now:
 *  - a per-device and per-IP rate limit enforced here,
 *  - a per-event-type daily cap, so a single device cannot flood the table,
 *  - a client-side cooldown in AdAnalytics.kt.
 *
 * None of this makes the numbers trustworthy against a determined forger. Read
 * them as directional. The leaderboard path, by contrast, is authenticated with
 * a server-only signing key (see start_run.php).
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

$data = readJsonBody();
if ($data === []) { jsonResponse(['error' => 'Invalid JSON'], 400); }

$eventType = trim((string)($data['event_type'] ?? ''));
// 'ad_error' is not a revenue event — it is how a no-fill is reported from the
// device so the reason can be read in admin/ads.php. The reason text travels
// in `placement` as "<format>:<reason>" (that column is capped at 64 chars),
// which avoids needing a schema migration for a diagnostics column.
//
// 'ad_request' and 'ad_loaded' are the fill funnel. Without them the panel
// could only show impressions, so "no banner revenue" was indistinguishable
// from "no banner request", "request rejected" and "no fill". They are
// diagnostics, not billable events, and the panel labels them as such.
if (!in_array($eventType, [
    'ad_request', 'ad_loaded',
    'impression', 'click',
    'rewarded_shown', 'rewarded_complete',
    'game_start', 'game_complete',
    'interstitial_closed', 'ad_error',
], true)) {
    jsonResponse(['error' => 'Invalid event_type'], 400);
}

$placement = substr(trim((string)($data['placement'] ?? '')), 0, 64);
$deviceId  = bb_normalize_device_id((string)($data['device_id'] ?? ''));
$ip        = bb_client_ip();
$now       = time();

// ── rate limit ────────────────────────────────────────────────────────────
// Keyed per device AND per IP: a device id is client-supplied and trivially
// rotated, but it still stops one install from flooding, and the IP stops a
// single host from rotating device ids.
const AD_MIN_GAP_SECONDS = 2;
const AD_MAX_PER_DAY    = 2000;

try {
    $pdo = getPDO();

    // bb_throttle_key() is required here, not cosmetic: a normalised device_id
    // can be 64 characters, so 'dev:' . $deviceId is 68 and overflows the
    // rate_limit.ip VARCHAR(64) column. That is either a 500 under strict SQL
    // mode or a silent truncation that makes two devices share one budget.
    foreach ([bb_throttle_key('dev:', $deviceId), bb_throttle_key('ip:', $ip)] as $key) {
        if ($key === 'dev:' || $key === 'ip:') continue;

        $stmt = $pdo->prepare("SELECT last_submit, strikes FROM rate_limit WHERE ip = ?");
        $stmt->execute([$key]);
        $row = $stmt->fetch();

        if ($row) {
            $elapsed = $now - (int) $row['last_submit'];
            $strikes = (int) ($row['strikes'] ?? 0);
            if ($elapsed < AD_MIN_GAP_SECONDS) {
                // Tolerate a burst: the app legitimately logs shown+complete
                // back to back, and a banner view can fire twice on a rotation.
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

    // Daily cap.
    if ($deviceId !== '') {
        $stmt = $pdo->prepare(
            "SELECT COUNT(*) AS c FROM ad_events WHERE device_id = ? AND created_at >= CURDATE()"
        );
        $stmt->execute([$deviceId]);
        if ((int) $stmt->fetch()['c'] >= AD_MAX_PER_DAY) {
            jsonResponse(['error' => 'daily_cap'], 429);
        }
    }

    $stmt = $pdo->prepare(
        "INSERT INTO ad_events (event_type, placement, device_id, ip) VALUES (?, ?, ?, ?)"
    );
    $stmt->execute([$eventType, $placement, $deviceId, $ip]);

    jsonResponse(['ok' => true]);
} catch (PDOException $e) {
    error_log('ad_event DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
