<?php
/**
 * news.php — published game news.
 *
 * GET ?id=123  returns one published item.
 * GET           returns the newest published items (max 30).
 *
 * Drafts are invisible to the app. There is no write path here; the admin
 * panel writes the table directly, exactly like the other content pages.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'GET') { jsonResponse(['error' => 'Method not allowed'], 405); }

try {
    $pdo = getPDO();
    $id = (int)($_GET['id'] ?? 0);

    if ($id > 0) {
        $stmt = $pdo->prepare(
            "SELECT id, title, summary, body, image_url, published_at
               FROM news
              WHERE id = ? AND is_published = 1
              LIMIT 1"
        );
        $stmt->execute([$id]);
        $row = $stmt->fetch();
        if (!$row) jsonResponse(['error' => 'خبر پیدا نشد'], 404);
        jsonResponse(['ok' => true, 'news' => $row]);
    }

    $stmt = $pdo->query(
        "SELECT id, title, summary, body, image_url, published_at
           FROM news
          WHERE is_published = 1
          ORDER BY COALESCE(published_at, created_at) DESC
          LIMIT 30"
    );
    jsonResponse(['ok' => true, 'news' => $stmt->fetchAll()]);
} catch (Throwable $e) {
    error_log('news error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
