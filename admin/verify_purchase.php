<?php
/**
 * Myket purchase verification endpoint.
 *
 * Fixes here:
 *  - Prices now match StoreConfig.kt. The ledger recorded 49,000/99,000/
 *    149,000/249,000 while the app charged 129,000/249,000/449,000/649,000
 *    after the price step-down, so every coin purchase in the revenue report was
 *    understated by 3-4x. Coin amounts were also stale (5,000/12,500/25,000/
 *    40,000) and are now the same 7,000/16,000/33,000/53,000 the app delivers.
 *  - device_id is recorded. The purchases table had no way to know WHICH
 *    player a verified purchase belonged to, so verification granted nothing.
 *  - Replay: a token that was already verified is reported as such instead of
 *    being silently re-accepted, so a leaked token cannot re-trigger an
 *    entitlement forever.
 *  - originalJson is required. The old "backward compatibility" branch signed
 *    `productId|purchaseToken` with SHA256, which can never match a real Myket
 *    signature (SHA1withRSA over the JSON).
 *
 * requirePost() was missing here while every other mutating page in the panel
 * had it. This endpoint inserts into `purchases`, grants premium and grants
 * coins, and accepted any HTTP method while reading $_POST whenever the
 * content type was not JSON — so an auto-submitting cross-site form could
 * trigger it from any page an authenticated admin visited. SameSite=Strict on
 * the session cookie blunts that, but that is defence in depth, not a control.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
requireAuth();
requirePost();   // POST-only + CSRF token, like every other mutating page

$raw = file_get_contents('php://input');
$ctype = $_SERVER['CONTENT_TYPE'] ?? '';
$data = (strpos($ctype, 'application/json') !== false)
    ? json_decode($raw, true)
    : $_POST;

if (!is_array($data)) {
    jsonResponse(['error' => 'Invalid body'], 400);
}

$productId     = trim((string)($data['productId'] ?? ''));
$purchaseToken = trim((string)($data['purchaseToken'] ?? ''));
$signature     = trim((string)($data['signature'] ?? ''));
$originalJson  = trim((string)($data['originalJson'] ?? ''));
$deviceId      = bb_admin_normalize_device((string)($data['device_id'] ?? ''));

if ($productId === '' || $purchaseToken === '' || $signature === '') {
    jsonResponse(['error' => 'پارامترهای ناقص'], 400);
}

// Keep in sync with MyketConfig in StoreConfig.kt
$products = [
    'blockboom_premium_59'    => ['coins' => 0,     'premium' => 1, 'price' => 389000, 'title' => 'پریمیوم'],
    'offer'                   => ['coins' => 18000, 'premium' => 0, 'price' => 189000, 'title' => 'پیشنهاد ویژه'],
    'blockboom_coins_small'   => ['coins' => 7000,  'premium' => 0, 'price' => 129000, 'title' => '۷٬۰۰۰ سکه'],
    'blockboom_coins_medium'  => ['coins' => 16000, 'premium' => 0, 'price' => 249000, 'title' => '۱۶٬۰۰۰ سکه'],
    'blockboom_coins_large'   => ['coins' => 33000, 'premium' => 0, 'price' => 449000, 'title' => '۳۳٬۰۰۰ سکه'],
    'blockboom_coins_very_larg' => ['coins' => 53000, 'premium' => 0, 'price' => 649000, 'title' => '۵۳٬۰۰۰ سکه'],
];

if (!isset($products[$productId])) {
    // Echoing the submitted product id straight back was the reflected-XSS
    // vector in purchases.php's innerHTML sink. Keep it generic.
    jsonResponse(['error' => 'محصول ناشناخته'], 400);
}

if ($originalJson === '') {
    jsonResponse(['error' => 'originalJson الزامی است'], 400);
}

if (!verifyMyketPurchase($productId, $purchaseToken, $signature, $originalJson)) {
    jsonResponse(['error' => 'امضا نامعتبر است یا محصول با خرید مطابقت ندارد — تایید نشد'], 400);
}

$pdo = getPDO();

// Replay check: report, do not re-apply.
$stmt = $pdo->prepare("SELECT id, device_id FROM purchases WHERE purchase_token = ?");
$stmt->execute([$purchaseToken]);
$existing = $stmt->fetch();
if ($existing) {
    jsonResponse([
        'success'  => true,
        'replay'   => true,
        'message'  => 'این خرید قبلاً تایید شده بود — دوباره اعمال نشد',
        'product'  => $productId,
        'device'   => $existing['device_id'] ?: null,
    ]);
}

$p = $products[$productId];
$stmt = $pdo->prepare(
    "INSERT INTO purchases (product_id, purchase_token, signature, original_json, device_id, coins, is_premium, price_rial, ip, verified)
     VALUES (?,?,?,?,?,?,?,?,?,1)"
);
$stmt->execute([
    $productId,
    $purchaseToken,
    $signature,
    $originalJson,
    $deviceId !== '' ? $deviceId : null,
    $p['coins'],
    $p['premium'],
    $p['price'],
    getClientIP(),
]);

// Grant the entitlement when we know who bought it.
$granted = false;
if ($deviceId !== '') {
    if ($p['premium'] === 1) {
        $pdo->prepare("UPDATE leaderboard SET is_premium = 1 WHERE device_id = ?")->execute([$deviceId]);
        $granted = true;
    } else {
        $pdo->prepare(
            "INSERT INTO coin_grants (device_id, coins, reason, ref_code) VALUES (?,?,?,?)"
        )->execute([$deviceId, $p['coins'], 'purchase', $productId]);
        $granted = true;
    }
}

$message = $p['premium'] === 1
    ? 'خرید پریمیوم تایید شد'
    : 'خرید ' . number_format($p['coins']) . ' سکه تایید شد';

jsonResponse([
    'success'  => true,
    'message'  => $message,
    'product'  => $productId,
    'device'   => $deviceId !== '' ? $deviceId : null,
    'granted'  => $granted,
    'hint'     => $granted ? null : 'دستگاه خریدار ارسال نشده بود، پس حقی به کسی داده نشد',
]);
