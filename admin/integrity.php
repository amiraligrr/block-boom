<?php
/**
 * Anti-cheat console.
 *
 * The old version of this page could not detect anything:
 *  - Its headline query used `score > (level*6000+5000)`, byte-for-byte the
 *    same predicate the API enforces at write time, so the result set was
 *    always empty and the card was pinned at 0.
 *  - It counted `count()` of arrays that had a LIMIT 50 / LIMIT 20 applied, so
 *    the stat froze at the cap and under-reported once it was exceeded.
 *  - The "high-effort IPs" table listed device_ids from rate_limit.ip, sorted
 *    by recency. Rate-limit *violations* were never recorded at all — the API
 *    just returned 429 — so the table structurally could not show abuse.
 *
 * Everything below is driven by cheat_flags and run_tokens, which the new
 * submit_score.php actually writes to.
 */
require_once __DIR__ . '/config.php';
// RATE_LIMIT_SECONDS is displayed on this page but lives in the API's policy,
// not in admin/config.php. Without this the constant is undefined and PHP 8
// fatals, silently truncating the page (display_errors is off on the server).
// The local tree and the deployed tree disagree about where the API lives:
// tools/deploy.sh stages api/*.php into the SITE ROOT, so on the server
// policy.php sits at /policy.php while locally it is at /api/policy.php.
// A single hardcoded path 500s on one layout or the other, so try both.
//
// The guard afterwards is the point: previously, if neither path existed the
// require_once silently did nothing and line 382 hit an undefined constant
// again -- recreating, invisibly, the exact bug this include was added to fix.
// Now a missing policy file degrades to a visible warning instead.
$bb_policy_loaded = false;
foreach ([__DIR__ . '/../api/policy.php', __DIR__ . '/../policy.php'] as $_policy) {
    if (is_file($_policy)) {
        require_once $_policy;
        $bb_policy_loaded = defined('RATE_LIMIT_SECONDS');
        if ($bb_policy_loaded) break;
    }
}
unset($_policy);
if (!$bb_policy_loaded) {
    $notice = 'فایل policy.php در سرور پیدا نشد؛ مقادیر سیاست ارسال نمایش داده نمی‌شوند. '
            . 'ابتدا api/policy.php را دیپلوی کنید.';
}
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();
$notice = null;

// ── actions (POST + CSRF) ─────────────────────────────────────────────────
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action = (string)($_POST['action'] ?? '');
    $device = bb_admin_normalize_device((string)($_POST['device_id'] ?? ''));

    if ($action === 'ban' && $device !== '') {
        $reason = mb_substr(trim((string)($_POST['reason'] ?? 'دستی')), 0, 64);
        $pdo->prepare("UPDATE leaderboard SET banned = 1, ban_reason = ? WHERE device_id = ?")
            ->execute([$reason, $device]);
        $notice = 'دستگاه مسدود شد.';
    } elseif ($action === 'unban' && $device !== '') {
        $pdo->prepare("UPDATE leaderboard SET banned = 0, ban_reason = '' WHERE device_id = ?")
            ->execute([$device]);
        $notice = 'مسدودی برداشته شد.';
    } elseif ($action === 'reset_score' && $device !== '') {
        // Resets the stored best back to zero so the player can re-earn it
        // legitimately. Leaves the audit trail in place.
        $pdo->prepare("UPDATE leaderboard SET score = 0, level = 1 WHERE device_id = ?")
            ->execute([$device]);
        $notice = 'امتیاز و سطح بازنشانی شد.';
    } elseif ($action === 'unban_all') {
        $n = $pdo->exec("UPDATE leaderboard SET banned = 0, ban_reason = '' WHERE banned = 1");
        $notice = faNum($n) . ' مسدودی برداشته شد.';
    } elseif ($action === 'clear_rate_limit') {
        // Was a GET link with no CSRF token: any page an admin visited could
        // silently wipe this table.
        $n = $pdo->exec("DELETE FROM rate_limit WHERE last_submit < UNIX_TIMESTAMP() - 3600");
        $notice = faNum($n) . ' رکورد قدیمی پاک شد.';
    } elseif ($action === 'purge_runs') {
        $n = $pdo->exec("DELETE FROM run_tokens WHERE expires_at < UNIX_TIMESTAMP() - INTERVAL 7 DAY");
        $notice = faNum($n) . ' توکن منقضی پاک شد.';
    }
}

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

// ── counts: COUNT(*) with the same predicate, never count() of a LIMIT ────
$flagToday = (int) $q("SELECT COUNT(*) c FROM cheat_flags WHERE created_at >= CURDATE()")->fetch()['c'];
$flagWeek  = (int) $q("SELECT COUNT(*) c FROM cheat_flags WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)")->fetch()['c'];
$banned    = (int) $q("SELECT COUNT(*) c FROM leaderboard WHERE banned = 1")->fetch()['c'];
$rateRows  = (int) $q("SELECT COUNT(*) c FROM rate_limit")->fetch()['c'];
$openRuns  = (int) $q("SELECT COUNT(*) c FROM run_tokens WHERE consumed = 0 AND expires_at > UNIX_TIMESTAMP()")->fetch()['c'];

// ── breakdowns by reason ──────────────────────────────────────────────────
$reasons = $q("
    SELECT reason, COUNT(*) c, MAX(created_at) last_seen
      FROM cheat_flags WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
     GROUP BY reason ORDER BY c DESC
")->fetchAll();

$reasonLabels = [
    'score_unreachable' => ['امتیاز غیرقابل دستیابی', 'rose'],
    'bad_token'         => ['توکن جعلی', 'rose'],
    'replay'            => ['تلاش برای استفاده دوباره از توکن', 'rose'],
    'bad_hmac'          => ['امضای نامعتبر', 'amber'],
    'stale_run'         => ['توکن منقضی', 'amber'],
    'future_run'        => ['زمان شروع در آینده', 'amber'],
    'score_negative'    => ['امتیاز منفی', 'rose'],
    'score_absurd'      => ['امتیاز غیرمنطقی', 'rose'],
];

// ── worst offenders ───────────────────────────────────────────────────────
$flagged = $q("
    SELECT f.device_id, f.reason, f.detail, f.level, f.score, f.created_at, f.ip,
           l.name, l.banned, l.score_rate, l.level AS cur_level
      FROM cheat_flags f
      LEFT JOIN leaderboard l ON l.device_id = f.device_id
     WHERE f.created_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY)
     ORDER BY f.created_at DESC
     LIMIT 60
")->fetchAll();

// Aggregate repeat offenders: one device tripping the guard many times.
$repeat = $q("
    SELECT device_id, COUNT(*) c, MAX(created_at) last_seen
      FROM cheat_flags
     WHERE device_id <> '' AND created_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY)
     GROUP BY device_id HAVING c >= 3
     ORDER BY c DESC LIMIT 20
")->fetchAll();

// ── suspicious scores, judged by the real engine curve ────────────────────
// The API already rejects anything above the ceiling, so this table is really
// "what got through that is close to the line" — high score_rate is the tell.
$suspicious = $q("
    SELECT name, level, score, device_id, score_rate, updated_at
      FROM leaderboard
     WHERE banned = 0 AND score_rate > 2500
     ORDER BY score_rate DESC LIMIT 25
")->fetchAll();

// ── throttled devices (strikes recorded) ──────────────────────────────────
$throttled = $q("
    SELECT ip AS device_id, last_submit, strikes
      FROM rate_limit WHERE strikes > 0
     ORDER BY strikes DESC, last_submit DESC LIMIT 25
")->fetchAll();

renderHead('ضدتقلب', 'integrity');
renderPageHeader('ضدتقلب', 'کنترل امتیاز، توکن اجرا و کاربران مشکوک');
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>

<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
    <?php
    statCard('رد شدن امروز', faNum($flagToday), 'fas fa-ban', 'rose');
    statCard('رد شدن ۷ روز', faNum($flagWeek), 'fas fa-triangle-exclamation', 'amber');
    statCard('دستگاه مسدود', faNum($banned), 'fas fa-user-slash', 'rose');
    statCard('اجرای باز (باز)', faNum($openRuns), 'fas fa-hourglass-half', 'sky');
    ?>
</div>

<!-- Maintenance actions -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6 mb-8">
    <h3 class="font-black text-emerald-900 mb-1">عملیات نگهداری</h3>
    <p class="text-xs text-emerald-500 mb-4">همه با POST و توکن CSRF اجرا می‌شوند.</p>
    <div class="flex flex-wrap gap-2">
        <form method="post">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="clear_rate_limit">
            <button class="px-4 py-2.5 bg-emerald-50 text-emerald-700 rounded-xl font-medium text-sm hover:bg-emerald-100 transition-all">
                <i class="fas fa-broom ml-1"></i>پاک‌سازی محدودیت‌های قدیمی
            </button>
        </form>
        <form method="post">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="purge_runs">
            <button class="px-4 py-2.5 bg-emerald-50 text-emerald-700 rounded-xl font-medium text-sm hover:bg-emerald-100 transition-all">
                <i class="fas fa-trash-can ml-1"></i>پاک‌سازی توکن‌های منقضی
            </button>
        </form>
        <?php if ($banned > 0): ?>
        <form method="post" onsubmit="return confirm('مسدودی همهٔ دستگاه‌ها برداشته شود؟')">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="unban_all">
            <button class="px-4 py-2.5 bg-amber-50 text-amber-700 rounded-xl font-medium text-sm hover:bg-amber-100 transition-all">
                <i class="fas fa-unlock ml-1"></i>رفع مسدودی همه (<?= faNum($banned) ?>)
            </button>
        </form>
        <?php endif; ?>
    </div>
</div>

<!-- Reason breakdown -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">دلایل رد شدن — ۷ روز اخیر</h3>
    </div>
    <div class="p-6 grid grid-cols-2 md:grid-cols-4 gap-4">
        <?php if (!$reasons): ?>
            <p class="col-span-full text-center text-emerald-400 py-6">مورد مشکوکی ثبت نشده — عالیه!</p>
        <?php endif; ?>
        <?php foreach ($reasons as $r):
            [$label, $tone] = $reasonLabels[$r['reason']] ?? [$r['reason'], 'emerald'];
            $bg = $tone === 'rose' ? 'bg-rose-50 text-rose-700' : ($tone === 'amber' ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700');
        ?>
        <div class="<?= $bg ?> rounded-xl p-4">
            <p class="text-2xl font-black"><?= faNum($r['c']) ?></p>
            <p class="text-xs mt-1 leading-5"><?= e($label) ?></p>
            <p class="text-[10px] opacity-70 mt-1">آخرین: <?= e($r['last_seen']) ?></p>
        </div>
        <?php endforeach; ?>
    </div>
</div>

<div class="grid grid-cols-1 lg:grid-cols-2 gap-5 mb-8">
    <!-- Repeat offenders -->
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
        <div class="px-6 py-4 border-b border-emerald-100">
            <h3 class="font-black text-emerald-900">متخلفین تکراری (۳+ بار در ۳۰ روز)</h3>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full">
                <?php tableHeader([
                    ['label' => 'دستگاه', 'align' => 'left'],
                    ['label' => 'تعداد', 'align' => 'center'],
                    ['label' => 'آخرین', 'align' => 'center'],
                    ['label' => 'عملیات', 'align' => 'center'],
                ]); ?>
                <tbody class="divide-y divide-emerald-50">
                <?php foreach ($repeat as $r): ?>
                    <tr>
                        <td class="px-4 py-3 text-xs font-mono" dir="ltr"><?= e(substr($r['device_id'], 0, 20)) ?></td>
                        <td class="px-4 py-3 text-center"><span class="badge bg-rose-100 text-rose-700"><?= faNum($r['c']) ?></span></td>
                        <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e($r['last_seen']) ?></td>
                        <td class="px-4 py-3 text-center">
                            <form method="post" class="inline">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="ban">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <input type="hidden" name="reason" value="تخلف تکراری">
                                <button class="px-3 py-1.5 bg-rose-500 text-white rounded-lg text-xs font-bold hover:bg-rose-600">مسدود</button>
                            </form>
                        </td>
                    </tr>
                <?php endforeach; ?>
                <?php if (!$repeat): emptyRow(4, 'متخلف تکراری نیست'); endif; ?>
                </tbody>
            </table>
        </div>
    </div>

    <!-- Throttled -->
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
        <div class="px-6 py-4 border-b border-emerald-100">
            <h3 class="font-black text-emerald-900">دستگاه‌های محدودشده (strike)</h3>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full">
                <?php tableHeader([
                    ['label' => 'دستگاه', 'align' => 'left'],
                    ['label' => 'تخلف', 'align' => 'center'],
                    ['label' => 'آخرین ارسال', 'align' => 'center'],
                ]); ?>
                <tbody class="divide-y divide-emerald-50">
                <?php foreach ($throttled as $r): ?>
                    <tr>
                        <td class="px-4 py-3 text-xs font-mono" dir="ltr"><?= e(substr($r['device_id'], 0, 20)) ?></td>
                        <td class="px-4 py-3 text-center"><span class="badge bg-amber-100 text-amber-700"><?= faNum($r['strikes']) ?></span></td>
                        <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e(date('Y/m/d H:i', (int)$r['last_submit'])) ?></td>
                    </tr>
                <?php endforeach; ?>
                <?php if (!$throttled): emptyRow(3, 'محدودیتی ثبت نشده'); endif; ?>
                </tbody>
            </table>
        </div>
    </div>
</div>

<!-- High score-rate: close to the physical limit -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">امتیازهای نزدیک به سقف فیزیکی</h3>
        <p class="text-xs text-emerald-500 mt-1">
            نرخ امتیاز بر ثانیه بالای ۲۵۰۰ یعنی بازیکن تقریباً در سقف سرعت موتور بازی است — بازبینی کنید.
        </p>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'نام', 'align' => 'right'],
                ['label' => 'سطح', 'align' => 'center'],
                ['label' => 'امتیاز', 'align' => 'center'],
                ['label' => 'امتیاز/ثانیه', 'align' => 'center'],
                ['label' => 'دستگاه', 'align' => 'left'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($suspicious as $r): ?>
                <tr class="hover:bg-rose-50/30">
                    <td class="px-4 py-3 text-sm font-bold text-emerald-900"><?= e($r['name']) ?></td>
                    <td class="px-4 py-3 text-center text-sm"><?= faNum($r['level']) ?></td>
                    <td class="px-4 py-3 text-center text-sm font-bold"><?= faNum(number_format((int)$r['score'])) ?></td>
                    <td class="px-4 py-3 text-center"><span class="badge bg-rose-100 text-rose-700 font-black"><?= faNum(number_format((float)$r['score_rate'])) ?></span></td>
                    <td class="px-4 py-3 text-xs font-mono text-emerald-500" dir="ltr"><?= e(substr($r['device_id'], 0, 16)) ?></td>
                </tr>
            <?php endforeach; ?>
            <?php if (!$suspicious): emptyRow(5, 'همه در محدودهٔ منطقی هستند'); endif; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- Full flag log with per-device actions -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">لاگ کامل رد شدن‌ها (۳۰ روز)</h3>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'دلیل', 'align' => 'right'],
                ['label' => 'نام', 'align' => 'right'],
                ['label' => 'سطح', 'align' => 'center'],
                ['label' => 'امتیاز', 'align' => 'center'],
                ['label' => 'سقف مجاز', 'align' => 'center'],
                ['label' => 'زمان', 'align' => 'center'],
                ['label' => 'عملیات', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($flagged as $r): ?>
                <tr class="hover:bg-emerald-50/40">
                    <td class="px-4 py-3 text-xs">
                        <span class="badge bg-rose-100 text-rose-700"><?= e($reasonLabels[$r['reason']][0] ?? $r['reason']) ?></span>
                    </td>
                    <td class="px-4 py-3 text-sm text-emerald-900"><?= e($r['name'] ?? '—') ?></td>
                    <td class="px-4 py-3 text-center text-xs"><?= faNum($r['level']) ?></td>
                    <td class="px-4 py-3 text-center text-xs font-bold text-rose-600"><?= faNum(number_format((int)$r['score'])) ?></td>
                    <td class="px-4 py-3 text-center text-xs text-emerald-500 font-mono"><?= e($r['detail'] ?: '—') ?></td>
                    <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e($r['created_at']) ?></td>
                    <td class="px-4 py-3 text-center whitespace-nowrap">
                        <?php if ($r['device_id'] !== ''): ?>
                            <?php if ((int)($r['banned'] ?? 0) === 1): ?>
                                <form method="post" class="inline">
                                    <?= csrfField() ?>
                                    <input type="hidden" name="action" value="unban">
                                    <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                    <button class="px-2.5 py-1.5 bg-emerald-100 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-200">رفع مسدودی</button>
                                </form>
                            <?php else: ?>
                                <form method="post" class="inline">
                                    <?= csrfField() ?>
                                    <input type="hidden" name="action" value="reset_score">
                                    <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                    <button class="px-2.5 py-1.5 bg-amber-100 text-amber-700 rounded-lg text-xs font-bold hover:bg-amber-200">بازنشانی</button>
                                </form>
                                <form method="post" class="inline" onsubmit="return confirm('این دستگاه مسدود شود؟')">
                                    <?= csrfField() ?>
                                    <input type="hidden" name="action" value="ban">
                                    <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                    <input type="hidden" name="reason" value="<?= e($reasonLabels[$r['reason']][0] ?? $r['reason']) ?>">
                                    <button class="px-2.5 py-1.5 bg-rose-500 text-white rounded-lg text-xs font-bold hover:bg-rose-600">مسدود</button>
                                </form>
                            <?php endif; ?>
                        <?php else: ?>
                            <span class="text-emerald-300 text-xs">—</span>
                        <?php endif; ?>
                    </td>
                </tr>
            <?php endforeach; ?>
            <?php if (!$flagged): emptyRow(7, 'لاگی وجود ندارد'); endif; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- Rules -->
<div class="bg-emerald-900 text-emerald-100 rounded-2xl p-6">
    <h3 class="font-black text-white mb-3"><i class="fas fa-shield-alt ml-2"></i>قواعد فعال ضدتقلب</h3>
    <ul class="text-sm space-y-2 list-disc pr-5 leading-6">
        <li>هر اجرای بازی یک <strong>توکن امضاشدهٔ سرور</strong> می‌گیرد؛ کلید امضا داخل اپ نیست، پس جعل توکن ممکن نیست.</li>
        <li>هر توکن <strong>یک‌بارمصرف</strong> است — ارسال دوباره با خطای replay رد می‌شود.</li>
        <li>مدت بازی را <strong>ساعت سرور</strong> اندازه می‌گیرد، نه ساعت کلاینت.</li>
        <li>سقف امتیاز از <strong>فرمول واقعی موتور بازی</strong> حساب می‌شود: منحنی HP گیم‌اِنجین × ضریب سختی لول (tier) × شکستن بلاک، به‌علاوهٔ باس.</li>
        <li>علاوه بر کفِ «بدون فارم»، امتیازِ قابل قبول با <strong>زمان بازی تأییدشده</strong> جمع می‌شود (سقف نرخ: ۳۵۰۰ امتیاز بر ثانیه).</li>
        <li>سطح هرگز کم و امتیاز هرگز کم نمی‌شود (ضد دانگرید).</li>
        <?php if ($bb_policy_loaded): ?>
        <li>حداقل فاصلهٔ ارسال: <?= faNum(RATE_LIMIT_SECONDS) ?> ثانیه (با <?= faNum(BB_SUBMIT_COOLDOWN_STRIKES) ?> خطای بخشیده‌شده).</li>
        <?php else: ?>
        <li class="text-amber-300">مقادیر سیاست ارسال در دسترس نیست — <code>policy.php</code> دیپلوی نشده است.</li>
        <?php endif; ?>
        <li>نکته: HMAC نسخه‌های قدیمی داخل APK است و <strong>هیچ امنیتی نمی‌دهد</strong> — فقط برای سازگاری نگه داشته شده.</li>
    </ul>
    <p class="text-xs text-emerald-300 mt-4 leading-6">
        اگر <code>RUN_TOKEN_SECRET</code> هنوز مقدار پیش‌فرض باشد، endpoint صدور توکن با خطای 503 بسته می‌شود
        و همه‌چیز از مسیر قدیمی می‌رود. کلید را از <code>settings.php</code> بگیر و در <code>api/config.php</code> بگذار.
    </p>
    <p class="text-xs text-emerald-300/70 mt-2 leading-6">
        زنجیرهٔ هش رویدادها (<code>events</code> / <code>chain</code>) ثبت می‌شود اما <strong>هنوز راستی‌آزمایی
        نمی‌شود</strong> — سرور آن را نگه می‌دارد و با چیزی مقایسه نمی‌کند. پس آن را لایهٔ فعال ضدتقلب حساب نکنید؛
        دفاع‌های مؤثر توکن سرور، یک‌بارمصرفی و سقف نرخ هستند.
    </p>
</div>
<?php renderFoot(); ?>
