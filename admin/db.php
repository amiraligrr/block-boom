<?php
// The admin panel must show Iran time, not the UTC clock of the shared host.
// This has to run before config.php because that file and the pages that
// include it call date()/strtotime() while rendering.
date_default_timezone_set('Asia/Tehran');

require_once __DIR__ . '/config.php';

/**
 * Admin password check.
 *
 * ADMIN_PASS_HASH holds a password_hash() output. ADMIN_PASS is only a
 * bootstrap fallback for the very first login, after which settings.php writes
 * a real hash and the plaintext is ignored. Storing the password in plaintext
 * meant a single file-read (LFI, a stray .bak, a leaked repo) handed over the
 * whole panel.
 */
function verifyAdminPassword(string $input): bool {
    $hash = ADMIN_PASS_HASH;
    if (!empty($hash) && $hash !== '') {
        return password_verify($input, $hash);
    }
    // Bootstrap path. Constant-time, though comparing two plaintexts with
    // hash_equals is only ever a stopgap.
    return hash_equals(ADMIN_PASS, $input);
}

function makeAdminPasswordHash(string $plain): string {
    return password_hash($plain, PASSWORD_DEFAULT);
}

function getPDO(): PDO {
    static $pdo = null;
    if ($pdo !== null) return $pdo;
    $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=' . DB_CHARSET;
    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        // LIMIT/OFFSET are inlined as validated integers throughout, because
        // native prepares bind every parameter as a string and MySQL rejects
        // `LIMIT '20'`.
        PDO::ATTR_EMULATE_PREPARES => false,
    ];
    $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
    $pdo->exec("SET NAMES utf8mb4");
    // PHP computes the date buckets, MySQL computes CURDATE(); without this
    // the two can disagree and every daily chart shifts by a day.
    $pdo->exec("SET time_zone = '" . date('P') . "'");
    return $pdo;
}

function jsonResponse($data, int $code = 200): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Allow-Headers: ' . ($_SERVER['HTTP_X_ADMIN_KEY'] ?? 'Content-Type'));
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** Redirects to the login page unless a live, non-idle session exists. */
function requireAuth(): void {
    $ok = !empty($_SESSION['admin_logged_in']) && $_SESSION['admin_logged_in'] === true;

    // Idle timeout. Sessions previously lived until the browser dropped the
    // cookie, with no server-side expiry at all.
    if ($ok) {
        $idleLimit = 3600;
        $last = (int)($_SESSION['last_activity'] ?? 0);
        if ($last > 0 && (time() - $last) > $idleLimit) {
            $ok = false;
            session_unset();
            session_destroy();
            session_start();
            $_SESSION['login_error'] = 'به‌خاطر عدم فعالیت از حساب خارج شدید';
        } else {
            $_SESSION['last_activity'] = time();
        }
    }

    if ($ok) return;

    $wantsJson = strpos($_SERVER['REQUEST_URI'] ?? '', 'verify_') !== false
        || ($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '') === 'XMLHttpRequest';
    if ($wantsJson) {
        jsonResponse(['error' => 'Unauthorized'], 401);
    }
    header('Location: login.php');
    exit;
}

/**
 * Myket purchase verification.
 *
 * The previous version verified the signature but never checked that the
 * productId inside originalJson matched the $productId argument, so a buyer of
 * the cheapest coin pack could replay their genuine signature while changing
 * the SKU field to the premium product. It also had a "backward compatibility"
 * branch signing `productId|purchaseToken` with SHA256, which can never match a
 * real Myket signature (Myket uses SHA1withRSA over the JSON) and only
 * produced false negatives.
 */
function verifyMyketPurchase(string $productId, string $purchaseToken, string $signature, string $originalJson = ''): bool {
    if ($originalJson === '') return false;

    $sigBytes = base64_decode($signature, true);
    if ($sigBytes === false || $sigBytes === '') return false;

    $decoded = json_decode($originalJson, true);
    if (!is_array($decoded)) return false;

    // Bind the purchase to the SKU we are about to credit.
    $jsonProduct = (string)($decoded['productId'] ?? $decoded['product_id'] ?? '');
    if ($jsonProduct === '' || !hash_equals($jsonProduct, $productId)) return false;

    // Reject anything that is not actually paid for.
    $state = (int)($decoded['purchaseState'] ?? 0);
    if ($state !== 0) return false;

    $publicKey = "-----BEGIN PUBLIC KEY-----\n"
        . chunk_split(MYKET_PUBLIC_KEY, 64, "\n")
        . "-----END PUBLIC KEY-----";

    return openssl_verify($originalJson, $sigBytes, $publicKey, OPENSSL_ALGO_SHA1) === 1;
}

function getClientIP(): string {
    return bb_admin_client_ip();
}

// ─────────────────────────────────────────────────────────────────────────
//  Operator-granted premium
// ─────────────────────────────────────────────────────────────────────────

/**
 * Grants (or revokes) premium for a device, in the place that actually reaches
 * the player's phone.
 *
 * ── What was broken ──────────────────────────────────────────────────────
 * Both the leaderboard page and the purchases page had their own one-liner:
 *
 *     UPDATE leaderboard SET is_premium = 1 WHERE device_id = ?
 *
 * `leaderboard.is_premium` is a *display* column — it draws the crown beside a
 * name in the scores table and feeds the panel's "premium" filter. Nothing ever
 * read it on the device. So pressing "premium" changed a table that the game
 * does not consult, and pressing "revoke" did nothing at all. That is why it
 * looked like the button did nothing.
 *
 * ── Where the real state lives ───────────────────────────────────────────
 * `premium_grants`, read by api/app_config.php via bb_premium_for_device().
 * The app already calls app_config.php on every launch and on every return to
 * the foreground, so a grant or a revoke lands on the device within a minute
 * without adding a poll or a new endpoint.
 *
 * Two rows are written deliberately: premium_grants is the truth, and
 * leaderboard.is_premium is the mirror the panel's own UI reads. Keeping them in
 * step here means there is one place to change, instead of the two pages
 * drifting apart the way they did.
 *
 * ── Account linking ──────────────────────────────────────────────────────
 * The grant is linked to the player's account when one can be found, so it
 * survives changing phones. device_links may not exist on an un-migrated
 * database, so that lookup is allowed to fail.
 *
 * @param days 0 for "never expires", otherwise clamped to 1..3650.
 * @return string a message for the operator, Persian, ready to display.
 */
function bb_admin_set_premium(PDO $pdo, string $deviceId, int $days, string $note = ''): string {
    $by = (string)($_SESSION['admin_user'] ?? 'admin');

    // 0 is meaningful ("never expires"); anything else is a length in days, so
    // a mistyped or absent value cannot mint a century-long premium.
    $untilTs = ($days === 0) ? 0 : time() + max(1, min(3650, $days)) * 86400;

    $accountId = null;
    try {
        $st = $pdo->prepare("SELECT account_id FROM device_links WHERE device_id = ? LIMIT 1");
        $st->execute([$deviceId]);
        $row = $st->fetch();
        if ($row) $accountId = (int) $row['account_id'];
    } catch (PDOException $e) {
        error_log('bb_admin_set_premium: device_links unavailable, granting to device only');
    }

    $pdo->prepare(
        "INSERT INTO premium_grants
             (device_id, account_id, is_premium, until_ts, note, granted_by)
         VALUES (?,?,1,?,?,?)
         ON DUPLICATE KEY UPDATE
             account_id = VALUES(account_id),
             is_premium = 1,
             until_ts  = VALUES(until_ts),
             note       = VALUES(note),
             granted_by = VALUES(granted_by)"
    )->execute([$deviceId, $accountId, $untilTs, mb_substr($note, 0, 255, 'UTF-8'), $by]);

    $pdo->prepare("UPDATE leaderboard SET is_premium = 1 WHERE device_id = ?")->execute([$deviceId]);

    return $days === 0
        ? 'پریمیوم دائمی فعال شد. بازیکن با باز کردن دوبارهٔ بازی آن را دریافت می‌کند.'
        : 'پریمیوم ' . faNum(max(1, min(3650, $days))) . ' روزه فعال شد. بازیکن با باز کردن دوبارهٔ بازی آن را دریافت می‌کند.';
}

/**
 * Takes an operator gift away.
 *
 * The row is kept with is_premium = 0 rather than deleted, so there is a record
 * of what was given and taken back.
 */
function bb_admin_revoke_premium(PDO $pdo, string $deviceId): string {
    $pdo->prepare("UPDATE premium_grants SET is_premium = 0, until_ts = 0 WHERE device_id = ?")
        ->execute([$deviceId]);
    $pdo->prepare("UPDATE leaderboard SET is_premium = 0 WHERE device_id = ?")->execute([$deviceId]);
    return 'پریمیوم برداشته شد. بازیکن با باز کردن دوبارهٔ بازی آن را از دست می‌دهد.';
}

/**
 * Wraps a premium change so a missing `premium_grants` table produces an
 * instruction instead of an uncaught PDOException, which on this panel is a
 * blank 500 mid-page.
 */
function bb_admin_try_premium(callable $fn): string {
    try {
        return $fn();
    } catch (PDOException $e) {
        error_log('admin premium action: ' . $e->getMessage());
        return 'خطا: جدول premium_grants روی دیتابیس نیست. بخش MIGRATION فایل api/init_db.sql را یک‌بار روی دیتابیس اجرا کن.';
    }
}
