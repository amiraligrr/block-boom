<?php
/**
 * Settings.
 *
 * This page used to persist by running preg_replace over its own config.php and
 * writing the file back. Three real problems with that:
 *  - Neither file_get_contents nor file_put_contents was checked, yet the
 *    success message was assigned unconditionally. On a read-only filesystem
 *    the admin was told "رمز تغییر کرد" while nothing changed.
 *  - No LOCK_EX and no atomic rename, so a concurrent request mid-write left a
 *    truncated config.php — a fatal parse error on every admin page.
 *  - Rotating HMAC_SECRET here only changed the ADMIN copy. api/config.php
 *    has its own copy on a different host, so the "rotation" silently did
 *    nothing. The 30-vs-5 RATE_LIMIT_SECONDS drift already visible in the old
 *    anti-cheat panel came from exactly this.
 *
 * Secrets now live in environment variables (see api/config.php) and the
 * generated values are shown once, for the operator to apply. Nothing is
 * written to disk from a web request.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$message = null;
$messageType = 'ok';
$generated = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action = (string)($_POST['action'] ?? '');

    if ($action === 'change_password') {
        $current = (string)($_POST['current_password'] ?? '');
        $new = (string)($_POST['new_password'] ?? '');
        $confirm = (string)($_POST['confirm_password'] ?? '');

        if (!verifyAdminPassword($current)) {
            $message = 'رمز فعلی اشتباه است';
            $messageType = 'error';
        } elseif (mb_strlen($new, 'UTF-8') < 10) {
            $message = 'رمز جدید باید حداقل ۱۰ کاراکتر باشد';
            $messageType = 'error';
        } elseif ($new !== $confirm) {
            $message = 'تکرار رمز مطابقت ندارد';
            $messageType = 'error';
        } elseif (strtolower($new) === strtolower(ADMIN_USER)) {
            $message = 'رمز نباید برابر نام کاربری باشد';
            $messageType = 'error';
        } else {
            $generated['hash'] = makeAdminPasswordHash($new);
            $message = 'هش رمز ساخته شد. آن را در متغیر محیطی BB_ADMIN_PASS_HASH قرار بده (یا مقدار ADMIN_PASS_HASH در config.php). سپس ADMIN_PASS را پاک کن.';
            $messageType = 'ok';
        }
    } elseif ($action === 'gen_run_token') {
        $generated['run_token'] = bin2hex(random_bytes(32));
        $message = 'کلید ضدتقلب ساخته شد. آن را در BB_RUN_TOKEN_SECRET (یا RUN_TOKEN_SECRET در api/config.php) بگذار. تا وقتی این کار نشود، endpoint صدور توکن ۵۰۳ می‌دهد.';
        $messageType = 'ok';
    } elseif ($action === 'gen_admin_key') {
        $generated['admin_api_key'] = bin2hex(random_bytes(24));
        $message = 'کلید اعلان‌ها ساخته شد. آن را در BB_ADMIN_API_KEY (یا ADMIN_API_KEY در api/config.php) بگذار تا notifications.php اجازهٔ نوشتن بدهد.';
        $messageType = 'ok';
    } elseif ($action === 'clear_login_attempts') {
        $pdo = getPDO();
        $n = $pdo->exec("DELETE FROM rate_limit WHERE ip LIKE 'login:%'");
        $message = faNum($n) . ' شمارنده تلاش ورود پاک شد';
        $messageType = 'ok';
    }
}

// The panel and the API are on different hosts, so the panel cannot read
// api/config.php. Ask the API instead — health.php reports only whether each
// secret is configured, never the secret itself.
//
// The X-Admin-Key header is required for the detail (schema map, key status).
// Without it health.php still answers for uptime purposes, but returns only
// liveness booleans, so an unauthenticated caller cannot enumerate which
// migrations are missing or whether the anti-cheat is still on the weak
// legacy path.
$health = null;
$apiReachable = false;

// ── Read ADMIN_API_KEY defensively ────────────────────────────────────────
// This line used to be `$apiKey = ADMIN_API_KEY;` and it made this page a hard
// 500, because the constant is not always there.
//
// config.php is deliberately excluded from every deploy (tools/deploy.sh lists
// it under PROTECTED, since it holds the DB password and the password hash), so
// the copy on the server is a hand-maintained file that only gains constants
// when someone edits it on the host. This one had ADMIN_API_KEY in the repo
// and NOT on the server, and the result was:
//
//   PHP Fatal error: Uncaught Error: Undefined constant "ADMIN_API_KEY"
//     in admin/settings.php:83
//
// — an uncaught Error, so a blank HTTP 500 with no body, for every admin who
// opened the settings page. health.php reported healthy throughout because
// health.php does not touch this constant.
//
// The deeper lesson: with config.php permanently out of sync by design, *any*
// constant added to the repo copy is missing on the live server until someone
// remembers to add it there. A consumer that hard-depends on a config constant
// is therefore a page waiting to 500. So every consumer guards with defined(),
// exactly as api/health.php already does for its own keys, and a missing
// constant degrades to "not configured" instead of taking the page down.
$apiKey = defined('ADMIN_API_KEY') ? (string)ADMIN_API_KEY : '';

$apiCtx = stream_context_create(['http' => [
    'timeout' => 4,
    'ignore_errors' => true,
    'header' => $apiKey !== '' ? "X-Admin-Key: {$apiKey}\r\n" : "",
]]);
$raw = @file_get_contents(API_BASE . '/health.php', false, $apiCtx);
if ($raw !== false) {
    $decoded = json_decode($raw, true);

    // The `detail` check was outside the is_array() guard it sits next to. If
    // health.php ever answered with something that is not a JSON object — an
    // HTML error page from a proxy, an empty body, a redirect to the host's
    // parking page — $decoded is null and `$decoded['detail']` becomes
    // "Trying to access array offset on value of type null". Harmless as a
    // warning, but it means the page decides what the API said before knowing
    // whether the API said anything.
    if (!is_array($decoded)) {
        $decoded = [];
    }
    if (isset($decoded['ok']) && $decoded['ok'] === true) {
        $health = $decoded;
        $apiReachable = true;
    }
    // Absent key is treated the same as `false`: no key was presented, so the
    // keyed detail was refused and there is nothing to render.
    if (($decoded['detail'] ?? false) === false) {
        $health = null;   // keyed detail refused; do not render a partial card
    }
}

renderHead('تنظیمات', 'settings');
renderPageHeader('تنظیمات', 'رمزها، کلیدها و سلامت پیکربندی');
?>

<?php if ($message): ?>
<div class="mb-6 rounded-2xl p-4 text-sm font-medium border
    <?= $messageType === 'error' ? 'bg-rose-50 border-rose-200 text-rose-700' : 'bg-emerald-50 border-emerald-200 text-emerald-800' ?>">
    <i class="fas <?= $messageType === 'error' ? 'fa-circle-exclamation' : 'fa-circle-check' ?> ml-2"></i><?= e($message) ?>
</div>
<?php endif; ?>

<?php foreach ($generated as $label => $value): ?>
<div class="mb-4 bg-slate-900 text-slate-100 rounded-2xl p-5">
    <p class="text-xs text-slate-400 mb-2"><?= e($label) ?> — فقط یک‌بار نمایش داده می‌شود، همین حالا کپی کن:</p>
    <code class="block bg-slate-800 rounded-xl p-3 text-sm break-all select-all" dir="ltr"><?= e($value) ?></code>
</div>
<?php endforeach; ?>

<!-- Config health -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100 flex items-center justify-between">
        <h3 class="font-black text-emerald-900">سلامت پیکربندی سرور API</h3>
        <a href="<?= e(API_BASE) ?>/health.php" target="_blank" rel="noopener"
           class="text-xs text-emerald-600 hover:text-emerald-800 font-medium" dir="ltr">health.php</a>
    </div>
    <div class="p-6 space-y-3 text-sm">
        <?php if (!$apiReachable): ?>
            <div class="flex items-start gap-3">
                <i class="fas fa-triangle-exclamation text-amber-500 mt-0.5"></i>
                <div>
                    <p class="font-bold text-emerald-900">دسترسی به سرور API برقرار نشد</p>
                    <p class="text-xs text-amber-700 mt-0.5 leading-6">
                        آدرس <code dir="ltr"><?= e(API_BASE) ?></code> در دسترس نیست یا
                        <code>health.php</code> روی سرور آپلود نشده. تا وقتی این بررسی کار نکند،
                        وضعیت کلیدهای سمت API نامشخص است.
                    </p>
                </div>
            </div>
        <?php elseif ($apiKey === ''): ?>
            <div class="flex items-start gap-3">
                <i class="fas fa-triangle-exclamation text-amber-500 mt-0.5"></i>
                <div>
                    <p class="font-bold text-emerald-900">کلید <code>BB_ADMIN_API_KEY</code> در پنل تنظیم نیست</p>
                    <p class="text-xs text-amber-700 mt-0.5 leading-6">
                        سرور API جزئیات سلامت را فقط با هدر <code dir="ltr">X-Admin-Key</code> می‌دهد.
                        همان کلیدی که در <code dir="ltr">api/config.php</code> می‌گذاری باید در محیط پنل
                        (<code dir="ltr">BB_ADMIN_API_KEY</code>) هم باشد تا این کارت پر شود.
                    </p>
                </div>
            </div>
        <?php else: ?>
            <?php
            $schemaRows = [];
            if (!empty($health['schema'])) {
                foreach ($health['schema'] as $k => $v) $schemaRows[] = ['جدول/ستون ' . $k, (bool)$v];
            }
            $checks = [
                ['اتصال دیتابیس', (bool)($health['db'] ?? false),
                    'اتصال برقرار نشد. جزئیات خطا در error_log سرور ثبت شده است.'],
                ['کلید ضدتقلب (RUN_TOKEN_SECRET)', (bool)($health['runToken'] ?? false),
                    'هنوز مقدار پیش‌فرض است — توکن‌ها با کلیدی امضا می‌شوند که همه می‌دانند. از همین صفحه کلید بساز و در api بگذار.'],
                ['کلید اعلان‌ها (ADMIN_API_KEY)', (bool)($health['adminKey'] ?? false),
                    'تنظیم نشده — endpoint اعلان‌ها همهٔ درخواست‌ها را رد می‌کند'],
            ];
            foreach ($checks as [$label, $good, $hint]): ?>
            <div class="flex items-start gap-3">
                <i class="fas <?= $good ? 'fa-circle-check text-emerald-500' : 'fa-triangle-exclamation text-amber-500' ?> mt-0.5"></i>
                <div>
                    <p class="font-bold text-emerald-900"><?= e($label) ?></p>
                    <?php if (!$good): ?>
                        <p class="text-xs text-amber-700 mt-0.5 leading-6"><?= e($hint) ?></p>
                    <?php endif; ?>
                </div>
            </div>
            <?php endforeach; ?>

            <?php if ($schemaRows): ?>
            <div class="pt-3 mt-3 border-t border-emerald-100">
                <p class="text-xs font-bold text-emerald-700 mb-2">اسکیمای دیتابیس</p>
                <div class="flex flex-wrap gap-2">
                    <?php foreach ($schemaRows as [$label, $ok]): ?>
                    <span class="badge <?= $ok ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700' ?>">
                        <?= $ok ? '✓' : '✗' ?> <?= e($label) ?>
                    </span>
                    <?php endforeach; ?>
                </div>
                <?php if (in_array(false, array_column($schemaRows, 1), true)): ?>
                <p class="text-xs text-rose-600 mt-2 leading-6">
                    بخش <code>MIGRATION</code> فایل <code>api/init_db.sql</code> را روی دیتابیس اجرا کن.
                    تا آن زمان ثبت امتیاز و ضدتقلب درست کار نمی‌کند.
                </p>
                <?php endif; ?>
            </div>
            <?php endif; ?>
        <?php endif; ?>

        <div class="flex items-start gap-3 pt-3 border-t border-emerald-100">
            <i class="fas fa-circle-info text-emerald-500 mt-0.5"></i>
            <p class="text-xs text-emerald-600 leading-6">
                هش رمز مدیریت روی همین سرور است:
                <?= ADMIN_PASS_HASH !== ''
                    ? '<span class="text-emerald-700 font-bold">تنظیم شده ✓</span>'
                    : '<span class="text-amber-700 font-bold">تنظیم نشده — رمز به متن ساده در config.php است</span>' ?>
            </p>
        </div>
    </div>
</div>

<!-- Password -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6 mb-8">
    <h3 class="font-black text-emerald-900 mb-1">تغییر رمز مدیریت</h3>
    <p class="text-xs text-emerald-500 mb-5">
        رمز ساخته می‌شود و به توابع HTML نوشته نمی‌شود. آن را در <code>BB_ADMIN_PASS_HASH</code> بگذار.
    </p>
    <form method="post" class="space-y-4 max-w-md">
        <?= csrfField() ?>
        <input type="hidden" name="action" value="change_password">
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">رمز فعلی</label>
            <input type="password" name="current_password" required autocomplete="current-password"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">رمز جدید (حداقل ۱۰ کاراکتر)</label>
            <input type="password" name="new_password" required minlength="10" autocomplete="new-password"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">تکرار رمز جدید</label>
            <input type="password" name="confirm_password" required minlength="10" autocomplete="new-password"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <button class="px-5 py-3 bg-emerald-500 text-white rounded-xl font-bold hover:bg-emerald-600 transition-all">
            <i class="fas fa-key ml-1"></i>ساخت رمز جدید
        </button>
    </form>
</div>

<!-- Key generation -->
<div class="grid grid-cols-1 md:grid-cols-2 gap-5 mb-8">
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-1">کلید ضدتقلب</h3>
        <p class="text-xs text-emerald-600 mb-4 leading-6">
            این کلید فقط باید در سرور API باشد و <strong>هرگز داخل اپلیکیشن</strong> نرود.
            امضای توکن اجرای بازی با همین انجام می‌شود.
        </p>
        <form method="post">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="gen_run_token">
            <button class="px-5 py-2.5 bg-emerald-50 text-emerald-700 rounded-xl font-medium text-sm hover:bg-emerald-100 transition-all">
                <i class="fas fa-shield-halved ml-1"></i>ساخت کلید جدید
            </button>
        </form>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-1">کلید اعلان‌ها</h3>
        <p class="text-xs text-emerald-600 mb-4 leading-6">
            endpoint اعلان‌ها بدون این کلید هیچ درخواستی را نمی‌پذیرد، تا کسی نتواند برای همهٔ
            بازیکنان پیام دلخواه بفرستد.
        </p>
        <form method="post">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="gen_admin_key">
            <button class="px-5 py-2.5 bg-emerald-50 text-emerald-700 rounded-xl font-medium text-sm hover:bg-emerald-100 transition-all">
                <i class="fas fa-bell ml-1"></i>ساخت کلید جدید
            </button>
        </form>
    </div>
</div>

<!-- Housekeeping -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
    <h3 class="font-black text-emerald-900 mb-4">نگهداری</h3>
    <form method="post">
        <?= csrfField() ?>
        <input type="hidden" name="action" value="clear_login_attempts">
        <button class="px-5 py-2.5 bg-amber-50 text-amber-700 rounded-xl font-medium text-sm hover:bg-amber-100 transition-all">
            <i class="fas fa-user-lock ml-1"></i>صفر کردن شمارندهٔ تلاش‌های ورود
        </button>
    </form>
</div>
<?php renderFoot(); ?>
