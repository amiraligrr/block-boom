<?php
/**
 * Ad analytics.
 *
 * The old page had two measurement bugs that made the numbers actively
 * misleading:
 *  - The placement table computed CTR as clicks / COUNT(*), where COUNT(*)
 *    counts every event type, not impressions. A placement that emits
 *    game_start + game_complete under "game" therefore showed twice as many
 *    "نمایش" as actual views and a fabricated 0.00% CTR.
 *  - A rewarded view is a real ad view, but the global CTR denominator counted
 *    only banner 'impression' events, excluding it entirely.
 *  - 'click' was never emitted by the app at all, so every CTR figure on the
 *    page was structurally pinned at 0.00%.
 *
 * Impressions and views are now counted per type, and CTR is NULL (rendered as
 * "—") rather than 0 when no click events exist, so "not measured" is
 * distinguishable from "nobody clicked".
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action = (string)($_POST['action'] ?? '');
    if ($action === 'prune' && ($_POST['days'] ?? '') !== '') {
        $days = max(7, min(3650, (int)$_POST['days']));
        $n = $pdo->exec("DELETE FROM ad_events WHERE created_at < NOW() - INTERVAL $days DAY");
        $notice = faNum($n) . ' رویداد قدیمی حذف شد';
    } elseif ($action === 'clear_errors') {
        // Only ad_error rows — these are diagnostics, not revenue events, and
        // they are meant to be cleared between tests so a stale failure from
        // yesterday is not read as a live one. The retention prune cannot do
        // this: its floor is 7 days.
        $n = $pdo->exec("DELETE FROM ad_events WHERE event_type = 'ad_error'");
        $notice = faNum($n) . ' خطای تبلیغ پاک شد';
    }
}
$notice = $notice ?? null;

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

$days = max(1, min(365, (int)($_GET['days'] ?? 30)));
$since = "created_at >= DATE_SUB(CURDATE(), INTERVAL $days DAY)";

$totals = $q("
    SELECT
        COALESCE(SUM(event_type = 'ad_request'), 0)        AS requests,
        COALESCE(SUM(event_type = 'ad_loaded'), 0)         AS loads,
        COALESCE(SUM(event_type = 'impression'), 0)        AS impressions,
        COALESCE(SUM(event_type = 'click'), 0)             AS clicks,
        COALESCE(SUM(event_type = 'rewarded_shown'), 0)    AS rewarded_shown,
        COALESCE(SUM(event_type = 'rewarded_complete'), 0) AS rewarded_done,
        COALESCE(SUM(event_type = 'game_start'), 0)        AS game_starts,
        COALESCE(SUM(event_type = 'game_complete'), 0)     AS game_completes,
        COUNT(*)                                           AS events
      FROM ad_events WHERE $since
")->fetch();

$requests    = (int) $totals['requests'];
$loads       = (int) $totals['loads'];
$impressions = (int) $totals['impressions'];
$clicks      = (int) $totals['clicks'];
$rewarded    = (int) $totals['rewarded_shown'];
$rewardedOk  = (int) $totals['rewarded_done'];
$gameStarts  = (int) $totals['game_starts'];
$gameDone    = (int) $totals['game_completes'];

// Banner + rewarded are both ad views.
$views    = $impressions + $rewarded;
$ctr      = $views > 0 ? round($clicks / $views * 100, 2) : null;
$rewardOk = $rewarded > 0 ? round($rewardedOk / $rewarded * 100, 1) : null;
$finishRate = $gameStarts > 0 ? round($gameDone / $gameStarts * 100, 1) : null;

// Fill rate is the number that was missing entirely. "No impressions" could
// mean nobody saw the banner, the SDK never asked for one, or the request
// failed. Requests/loads separate those three cases.
$fillRate = $requests > 0 ? round($loads / $requests * 100, 1) : null;
$showRate = $loads > 0 ? round($impressions / $loads * 100, 1) : null;

$uniq = $q("
    SELECT COUNT(DISTINCT device_id) devices, COUNT(DISTINCT ip) ips
      FROM ad_events WHERE $since
")->fetch();

// Per placement, with impressions counted per type rather than COUNT(*).
//
// The outer SELECT is needed because MariaDB refuses to order by an
// expression over group-function aliases ("Reference 'impressions' not
// supported"). Ordering by `impressions DESC, rewarded DESC` is close enough
// and portable.
$placements = $q("
    SELECT * FROM (
        SELECT placement,
               COALESCE(SUM(event_type = 'ad_request'), 0)        AS requests,
               COALESCE(SUM(event_type = 'ad_loaded'), 0)         AS loads,
               COALESCE(SUM(event_type = 'impression'), 0)        AS impressions,
               COALESCE(SUM(event_type = 'click'), 0)             AS clicks,
               COALESCE(SUM(event_type = 'rewarded_shown'), 0)    AS rewarded,
               COUNT(DISTINCT device_id)                          AS devices
          FROM ad_events WHERE $since
         GROUP BY placement
    ) t
    ORDER BY impressions DESC, rewarded DESC, requests DESC
")->fetchAll();

// Devices that appear under many IPs — a cheap multi-account signal.
$multiIp = $q("
    SELECT * FROM (
        SELECT device_id, COUNT(DISTINCT ip) ips, COUNT(*) events
          FROM ad_events WHERE $since AND device_id <> ''
         GROUP BY device_id
    ) t WHERE ips > 3
    ORDER BY ips DESC LIMIT 15
")->fetchAll();

// Daily series.
$series = [];
for ($i = min($days, 30) - 1; $i >= 0; $i--) {
    $d = date('Y-m-d', strtotime("-$i days"));
    $series[] = [
        'date' => $d,
        'imp'  => (int) $q("SELECT COUNT(*) c FROM ad_events WHERE DATE(created_at)=? AND event_type='impression'", [$d])->fetch()['c'],
        'rev'  => (int) $q("SELECT COUNT(*) c FROM ad_events WHERE DATE(created_at)=? AND event_type='rewarded_shown'", [$d])->fetch()['c'],
    ];
}
$labels = json_encode(array_map(fn($d) => date('d/m', strtotime($d['date'])), $series), JSON_UNESCAPED_UNICODE);
$impJs  = json_encode(array_column($series, 'imp'));
$revJs  = json_encode(array_column($series, 'rev'));

$rangeLinks = [7 => '۷ روز', 30 => '۳۰ روز', 90 => '۹۰ روز'];

// Recent no-fill / error reasons reported by the app. Without this the panel
// only showed the absence of impressions, which is indistinguishable between
// "no network", "unapproved account" and "placement id typo".
$adErrors = $pdo->query(
    "SELECT placement, COUNT(*) n, MAX(created_at) last_seen
       FROM ad_events
      WHERE event_type = 'ad_error' AND $since
      GROUP BY placement
      ORDER BY n DESC
      LIMIT 12"
);
$adErrorRows = $adErrors->fetchAll();

renderHead('آمار تبلیغات', 'ads', true);
renderPageHeader('آمار تبلیغات', 'بازدید، کلیک و نرخ تکمیل');
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>

<div class="flex gap-2 mb-6">
    <?php foreach ($rangeLinks as $d => $label): ?>
        <a href="?days=<?= $d ?>"
           class="px-4 py-2 rounded-lg text-xs font-bold transition-all
                  <?= $days === $d ? 'bg-emerald-500 text-white' : 'bg-white/90 text-emerald-700 hover:bg-emerald-100 border border-emerald-100' ?>">
            <?= e($label) ?>
        </a>
    <?php endforeach; ?>
    <form method="post" class="mr-auto flex items-center gap-2">
        <?= csrfField() ?>
        <input type="hidden" name="action" value="prune">
        <input type="number" name="days" value="90" min="7" max="3650"
            class="w-24 px-3 py-2 border border-emerald-200 rounded-lg text-xs outline-none">
        <button class="px-4 py-2 bg-emerald-50 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-100">
            <i class="fas fa-broom ml-1"></i>پاک‌سازی
        </button>
    </form>
</div>

<div class="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4 mb-8">
    <?php
    statCard('درخواست بنر', faNum(number_format($requests)), 'fas fa-paper-plane', 'sky');
    statCard('بارگذاری بنر', faNum(number_format($loads)), 'fas fa-download', 'sky');
    statCard('نرخ پر شدن', $fillRate === null ? '—' : faNum($fillRate) . '٪', 'fas fa-chart-simple', $fillRate !== null && $fillRate < 50 ? 'amber' : 'emerald');
    statCard('نمایش بنر', faNum(number_format($impressions)), 'fas fa-eye', 'sky');
    statCard('نمایش پاداش‌دار', faNum(number_format($rewarded)), 'fas fa-gift', 'emerald');
    statCard('نرخ کلیک', $ctr === null ? '—' : faNum($ctr) . '٪', 'fas fa-hand-pointer', 'amber');
    ?>
</div>

<?php if ($requests > 0 && $loads === 0): ?>
<div class="mb-8 bg-rose-50 border border-rose-200 text-rose-900 rounded-2xl p-4 text-sm leading-6">
    <i class="fas fa-triangle-exclamation ml-2"></i>
    <b>بنر درخواست می‌شود اما هیچ‌وقت پر نمی‌شود.</b>
    این یعنی مشکل در سمت Adivery یا جایگاه (Placement) است، نه در شمارش پنل.
    دلایل رایج: جایگاه بنر در پنل Adivery تأیید نشده، شناسهٔ جایگاه با نوع بنر
    نمی‌خواند، یا حساب ناشر هنوز فعال نشده. دلیل دقیق هر دستگاه در جدول
    «خطای تبلیغ» پایین‌تر آمده است.
</div>
<?php elseif ($requests > 0): ?>
<div class="mb-8 bg-emerald-50 border border-emerald-200 text-emerald-900 rounded-2xl p-4 text-sm">
    <i class="fas fa-circle-info ml-2"></i>
    از هر <?= faNum(number_format($requests)) ?> درخواست بنر،
    <?= faNum(number_format($loads)) ?> بار تبلیغ دریافت شده
    (<?= faNum($fillRate ?? 0) ?>٪). نرخ نمایش روی تبلیغ‌های دریافت‌شده:
    <?= $showRate === null ? '—' : faNum($showRate) . '٪' ?>.
</div>
<?php endif; ?>

<?php if ($ctr === null && $views > 0): ?>
<div class="mb-8 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl p-4 text-sm">
    <i class="fas fa-circle-info ml-2"></i>
    رویداد <code>click</code> از سمت اپ ارسال نمی‌شود، پس نرخ کلیک قابل محاسبه نیست و
    عمداً به‌جای <code>0.00٪</code> خط تیره نشان داده می‌شود.
    برای فعال شدنش، در <code>AdManager</code> رویداد کلیک روی تبلیغ را گزارش کنید.
</div>
<?php endif; ?>

<div class="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-8">
    <div class="lg:col-span-2 bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-4">روند نمایش (۳۰ روز اخیر)</h3>
        <?php chartBox('adChart', 280, 'نمودار نمایش تبلیغات'); ?>
    </div>
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-4">خلاصه</h3>
        <dl class="space-y-3 text-sm">
            <div class="flex justify-between"><dt class="text-emerald-600">کل رویدادها</dt><dd class="font-black"><?= faNum(number_format((int)$totals['events'])) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">دستگاه یکتا</dt><dd class="font-black"><?= faNum((int)$uniq['devices']) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">آی‌پی یکتا</dt><dd class="font-black"><?= faNum((int)$uniq['ips']) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">شروع بازی</dt><dd class="font-black"><?= faNum(number_format($gameStarts)) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">تکمیل بازی</dt>
                <dd class="font-black"><?= $finishRate === null ? '—' : faNum($finishRate) . '٪' ?></dd></div>
        </dl>
    </div>
</div>

<?php if ($adErrorRows): ?>
<!-- Ad errors -->
<div class="bg-amber-50 rounded-2xl shadow-lg border border-amber-200 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-amber-200">
        <h3 class="font-black text-amber-900">خطای تبلیغ (بدون پرشدن)</h3>
        <p class="text-xs text-amber-700 mt-1">
            دلیل خطا مستقیماً از دستگاه ارسال شده. پرتکرارترین دلیل‌ها:
            <span class="font-mono" dir="ltr">no_fill</span> یعنی حساب/جایگاه در پنل
            Adivery تأیید نشده یا جایگاه اشتباه است.
        </p>
        <form method="POST" class="mt-3">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="clear_errors">
            <button class="px-3 py-1.5 rounded-lg bg-amber-200 hover:bg-amber-300 text-amber-900 text-xs font-bold">
                پاک کردن خطاها
            </button>
        </form>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                // Shape must be ['label' => ..., 'align' => ...]. Passing
                // ['label' => 'css-class'] makes tableHeader read a string
                // offset, which is a fatal in PHP 8 — and because display_errors
                // is off on the server that silently truncated this page
                // halfway down instead of showing an error.
                ['label' => 'جایگاه / دلیل', 'align' => 'right'],
                ['label' => 'تعداد',          'align' => 'center'],
                ['label' => 'آخرین بار',      'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-amber-100">
            <?php foreach ($adErrorRows as $er): ?>
            <tr class="border-t border-amber-100">
                <td class="px-4 py-3 text-amber-900 font-mono text-xs" dir="ltr"><?= e($er['placement']) ?></td>
                <td class="px-4 py-3 font-black text-amber-900"><?= faNum(number_format((int)$er['n'])) ?></td>
                <td class="px-4 py-3 text-amber-700 text-xs"><?= e($er['last_seen']) ?></td>
            </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
<?php endif; ?>

<!-- Placements -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">به تفکیک جایگاه</h3>
        <p class="text-xs text-emerald-500 mt-1">نمایش = impression، پاداش = rewarded_shown</p>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'جایگاه', 'align' => 'right'],
                ['label' => 'درخواست', 'align' => 'center'],
                ['label' => 'بارگذاری', 'align' => 'center'],
                ['label' => 'نرخ پر شدن', 'align' => 'center'],
                ['label' => 'نمایش', 'align' => 'center'],
                ['label' => 'پاداش', 'align' => 'center'],
                ['label' => 'کلیک', 'align' => 'center'],
                ['label' => 'نرخ کلیک', 'align' => 'center'],
                ['label' => 'دستگاه', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($placements as $p):
                $pv = (int)$p['impressions'] + (int)$p['rewarded'];
                $pCtr = $pv > 0 ? round((int)$p['clicks'] / $pv * 100, 2) : null;
                $pReq = (int)$p['requests'];
                $pLoad = (int)$p['loads'];
                $pFill = $pReq > 0 ? round($pLoad / $pReq * 100, 1) : null;
            ?>
            <tr class="hover:bg-emerald-50/40">
                <td class="px-4 py-3 text-sm font-mono" dir="ltr"><?= e($p['placement'] ?: '—') ?></td>
                <td class="px-4 py-3 text-center text-sm"><?= faNum(number_format($pReq)) ?></td>
                <td class="px-4 py-3 text-center text-sm"><?= faNum(number_format($pLoad)) ?></td>
                <td class="px-4 py-3 text-center text-sm font-bold <?= $pFill !== null && $pFill < 50 ? 'text-rose-600' : 'text-emerald-700' ?>"><?= $pFill === null ? '—' : faNum($pFill) . '٪' ?></td>
                <td class="px-4 py-3 text-center text-sm"><?= faNum(number_format((int)$p['impressions'])) ?></td>
                <td class="px-4 py-3 text-center text-sm"><?= faNum(number_format((int)$p['rewarded'])) ?></td>
                <td class="px-4 py-3 text-center text-sm"><?= faNum(number_format((int)$p['clicks'])) ?></td>
                <td class="px-4 py-3 text-center text-sm font-bold"><?= $pCtr === null ? '—' : faNum($pCtr) . '٪' ?></td>
                <td class="px-4 py-3 text-center text-sm text-emerald-600"><?= faNum((int)$p['devices']) ?></td>
            </tr>
            <?php endforeach; ?>
            <?php if (!$placements): emptyRow(9, 'رویدادی ثبت نشده'); endif; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- Multi-IP devices -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">دستگاه‌هایی با چند آی‌پی</h3>
        <p class="text-xs text-emerald-500 mt-1">می‌تواند نشانهٔ چندحسابی باشد — یا تغییر شبکه.</p>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'دستگاه', 'align' => 'left'],
                ['label' => 'تعداد آی‌پی', 'align' => 'center'],
                ['label' => 'رویداد', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($multiIp as $r): ?>
                <tr>
                    <td class="px-4 py-3 text-xs font-mono" dir="ltr"><?= e(substr($r['device_id'], 0, 22)) ?></td>
                    <td class="px-4 py-3 text-center"><span class="badge bg-amber-100 text-amber-700 font-black"><?= faNum($r['ips']) ?></span></td>
                    <td class="px-4 py-3 text-center text-sm"><?= faNum((int)$r['events']) ?></td>
                </tr>
            <?php endforeach; ?>
            <?php if (!$multiIp): emptyRow(3, 'موردی نیست'); endif; ?>
            </tbody>
        </table>
    </div>
</div>

<script>
new Chart(document.getElementById('adChart'), {
    type: 'bar',
    data: {
        labels: <?= $labels ?>,
        datasets: [
            { label: 'نمایش بنر', data: <?= $impJs ?>, backgroundColor: 'rgba(56,189,248,0.6)', borderRadius: 5 },
            { label: 'نمایش پاداش‌دار', data: <?= $revJs ?>, backgroundColor: 'rgba(16,185,129,0.6)', borderRadius: 5 }
        ]
    },
    options: {
        responsive: true, maintainAspectRatio: false, resizeDelay: 120,
        plugins: { legend: { labels: { font: { size: 11 } } } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
    }
});
</script>
<?php renderFoot(); ?>
