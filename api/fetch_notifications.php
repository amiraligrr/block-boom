<?php
/**
 * fetch_notifications.php — in-app announcements for one device.
 *
 * This used to SELECT every unread row with no device scoping, so a
 * notification written with target_type='specific' (addressed to one player)
 * was handed to every caller. It now filters by device_id, and can mark what
 * it returns as read so an announcement does not reappear on every launch.
 *
 * ── Why receipts are per-device, not the notifications.is_read column ─────
 * `is_read` is a single flag on the row, so for a broadcast (target_id IS
 * NULL) the FIRST device to call this endpoint with mark_read=1 marked it read
 * for the entire playerbase: everyone else never saw it, and the panel then
 * reported the announcement as "read" globally. Targeting worked only by
 * accident — a targeted row can match exactly one device.
 *
 * So read state now lives in `notification_reads`, keyed (notification_id,
 * device_id). `notifications.is_read` is kept and still maintained, but as a
 * derived "everyone has read it" flag: a row is only flagged once no player
 * could still be missing it. That keeps the panel's existing counts working
 * while fixing the delivery bug.
 *
 * GET ?device_id=...&mark_read=1
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'GET') { jsonResponse(['error' => 'Method not allowed'], 405); }

$deviceId = bb_normalize_device_id((string)($_GET['device_id'] ?? ''));
if (strlen($deviceId) < 5) { jsonResponse(['error' => 'Invalid device'], 400); }

$markRead = ($_GET['mark_read'] ?? '0') === '1';

try {
    $pdo = getPDO();

    // Broadcasts (target_id IS NULL) plus anything aimed at this device, minus
    // what THIS device already acknowledged. NOT EXISTS against the receipts
    // table is the whole fix: it is per-device by construction, so there is no
    // flag for one player to set on another's notification.
    $stmt = $pdo->prepare(
        "SELECT n.id, n.title, n.message, n.created_at, n.target_type, n.target_id
           FROM notifications n
          WHERE (n.target_id IS NULL OR n.target_id = ?)
            AND NOT EXISTS (
                SELECT 1 FROM notification_reads r
                 WHERE r.notification_id = n.id AND r.device_id = ?
            )
          ORDER BY n.created_at DESC
          LIMIT 10"
    );
    $stmt->execute([$deviceId, $deviceId]);
    $notifications = [];
    $ids = [];
    foreach ($stmt->fetchAll() as $row) {
        $notifications[] = [
            'id'         => (int) $row['id'],
            'title'      => $row['title'],
            'message'    => $row['message'],
            'created_at' => $row['created_at'],
            'targeted'   => $row['target_id'] !== null,
        ];
        $ids[] = (int) $row['id'];
    }

    if ($markRead && $ids) {
        // INSERT IGNORE makes this idempotent: a retry after a timeout inserts
        // nothing extra instead of failing on the duplicate primary key.
        $values = implode(',', array_fill(0, count($ids), '(?, ?, NOW())'));
        $args = [];
        foreach ($ids as $id) { $args[] = (int) $id; $args[] = $deviceId; }
        $pdo->prepare(
            "INSERT IGNORE INTO notification_reads (notification_id, device_id, read_at)
             VALUES $values"
        )->execute($args);

        // `notifications.is_read` stays meaningful for TARGETED rows only: there
        // the audience is exactly this device, so this device reading it does
        // mean the row is fully read.
        //
        // For a broadcast there is no correct moment to set it — "read" would
        // have to mean "read by every player who has ever installed the app",
        // including players who have not seen the announcement yet and may
        // uninstall first. Rather than pretend, broadcasts leave is_read = 0 and
        // the panel counts distinct readers from notification_reads instead
        // (see admin/notifications.php). Per-device delivery is what matters,
        // and that is now driven entirely by the receipts table.
        $pdo->prepare(
            "UPDATE notifications SET is_read = 1
              WHERE target_id IS NOT NULL
                AND id IN (" . implode(',', array_fill(0, count($ids), '?')) . ")"
        )->execute($ids);
    }

    jsonResponse(['ok' => true, 'notifications' => $notifications]);
} catch (PDOException $e) {
    error_log('notifications fetch error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
