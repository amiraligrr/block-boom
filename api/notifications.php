<?php
/**
 * notifications.php — admin-only composer for in-app announcements.
 *
 * This endpoint was completely unauthenticated: anyone could POST
 * {target_type:"all", title:"...", message:"..."} and push arbitrary text to
 * every player — a ready-made phishing/defacement channel. It now requires the
 * admin session key, which lives only in admin/.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

// Shared secret between the admin panel and this endpoint. Generate with:
//   php -r "echo bin2hex(random_bytes(24)), PHP_EOL;"
if (!defined('ADMIN_API_KEY') || ADMIN_API_KEY === '' || strpos(ADMIN_API_KEY, 'CHANGE_ME') === 0) {
    error_log('notifications: ADMIN_API_KEY not configured; refusing writes');
    jsonResponse(['error' => 'not configured'], 503);
}

$key = (string)($_SERVER['HTTP_X_ADMIN_KEY'] ?? ($_POST['admin_key'] ?? ''));
if (!hash_equals(ADMIN_API_KEY, $key)) {
    jsonResponse(['error' => 'Unauthorized'], 401);
}

$data = readJsonBody();
$title      = trim((string)($data['title'] ?? ''));
$message    = trim((string)($data['message'] ?? ''));
$targetType = trim((string)($data['target_type'] ?? 'all'));
$targetId   = bb_normalize_device_id((string)($data['target_id'] ?? ''));

if ($title === '' || $message === '') { jsonResponse(['error' => 'Title and message required'], 400); }
if (!in_array($targetType, ['all', 'specific'], true)) { jsonResponse(['error' => 'Invalid target type'], 400); }
if ($targetType === 'specific' && $targetId === '') { jsonResponse(['error' => 'Target ID required'], 400); }

// title is VARCHAR(128) in the schema: clamp here rather than let MySQL throw
// a "Data too long" that surfaces as an opaque 500.
$title = mb_substr($title, 0, 128, 'UTF-8');
$message = mb_substr($message, 0, 2000, 'UTF-8');

try {
    $pdo = getPDO();

    if ($targetType === 'specific') {
        $stmt = $pdo->prepare(
            "SELECT id FROM notifications
              WHERE target_type = 'specific' AND target_id = ? AND is_read = 0 LIMIT 1"
        );
        $stmt->execute([$targetId]);
        if ($stmt->fetch()) {
            jsonResponse(['ok' => true, 'message' => 'Notification already sent to this device']);
        }
    }

    $stmt = $pdo->prepare(
        "INSERT INTO notifications (title, message, target_type, target_id) VALUES (?, ?, ?, ?)"
    );
    $stmt->execute([$title, $message, $targetType, $targetType === 'specific' ? $targetId : null]);

    jsonResponse(['ok' => true, 'id' => (int) $pdo->lastInsertId()]);
} catch (PDOException $e) {
    error_log('notification DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
