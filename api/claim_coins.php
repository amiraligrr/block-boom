<?php
/**
 * claim_coins.php — hand the client everything it has earned but not yet
 * collected (referral rewards, admin-issued bonuses).
 *
 * Marking claimed and paying out happen in one transaction, so a retry after
 * a network timeout can never pay the same grant twice.
 *
 * POST { "device_id": "..." }
 *   -> { "ok": true, "coins": 350, "count": 1 }
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

$data = readJsonBody();
$deviceId = bb_normalize_device_id((string)($data['device_id'] ?? ''));
if (strlen($deviceId) < 5) { jsonResponse(['error' => 'Invalid device'], 400); }

try {
    $pdo = getPDO();

    $pdo->beginTransaction();
    try {
        // FOR UPDATE so two concurrent retries cannot both read the same rows.
        $stmt = $pdo->prepare(
            "SELECT id, coins FROM coin_grants
              WHERE device_id = ? AND claimed_at IS NULL
              ORDER BY id ASC LIMIT 50 FOR UPDATE"
        );
        $stmt->execute([$deviceId]);
        $rows = $stmt->fetchAll();

        if (!$rows) {
            $pdo->commit();
            jsonResponse(['ok' => true, 'coins' => 0, 'count' => 0]);
        }

        $total = 0;
        $ids = [];
        foreach ($rows as $r) {
            $total += (int) $r['coins'];
            $ids[] = (int) $r['id'];
        }

        $in = implode(',', array_fill(0, count($ids), '?'));
        $pdo->prepare("UPDATE coin_grants SET claimed_at = NOW() WHERE id IN ($in)")->execute($ids);

        $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    jsonResponse(['ok' => true, 'coins' => $total, 'count' => count($ids)]);
} catch (PDOException $e) {
    error_log('claim_coins DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
