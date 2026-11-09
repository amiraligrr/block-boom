<?php
/**
 * Revenue & purchases.
 *
 * The old page derived every number from `leaderboard.is_premium` and never
 * read the `purchases` table at all, which led to three fabricated figures:
 *  - Revenue was `premiumCount * 590000` — an assumed constant, not real money.
 *  - Conversion was `premium / (premium + 100)`. The `+ 100` was a hardcoded
 *    stand-in for the user total, so the "rate" was a pure function of the
 *    premium count that asymptoted to 50% no matter the real user base. A real
 *    0.05% conversion displayed as 4.8% — off by ~100x.
 *  - The "premium users" heading counted a LIMIT 50 array while a stat card 55
 *    lines above showed the true total, so the same page printed two different
 *    numbers for one quantity.
 *
 * Revenue now comes from purchases.price_rial, and conversion uses the real
 * player count.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();
$notice = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action = (string)($_POST['action'] ?? '');
    if ($action === 'grant_premium') {
        $device = bb_admin_normalize_device((string)($_POST['device_id'] ?? ''));
        if ($device !== '') {
            // Shared with leaderboard.php. This page used to carry its own copy
            // of the one-liner that only set leaderboard.is_premium, which is a
            // crown in the scores table — so the button here did nothing to the
            // player's game either. See bb_admin_set_premium().
            $notice = bb_admin_try_premium(fn() => bb_admin_set_premium($pdo, $device, (int)($_POST['days'] ?? 30)));
        }
    } elseif ($action === 'revoke_premium') {
        $device = bb_admin_normalize_device((string)($_POST['device_id'] ?? ''));
        if ($device !== '') {
            $notice = bb_admin_try_premium(fn() => bb_admin_revoke_premium($pdo, $device));
        }
    } elseif ($action === 'grant_coins') {
        $device = bb_admin_normalize_device((string)($_POST['device_id'] ?? ''));
        $coins = max(1, min(1000000, (int)($_POST['coins'] ?? 0)));
        if ($device !== '') {
            $pdo->prepare(
                "INSERT INTO coin_grants (device_id, coins, reason, ref_code) VALUES (?,?,?,?)"
            )->execute([$device, $coins, 'admin', null]);
            $notice = faNum($coins) . ' سکه برای بازیکن ثبت شد (کلاینت در ورود بعدی می‌گیرد).';
        }
    }
}

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

$totalUsers   = (int) $q("SELECT COUNT(*) c FROM leaderboard")->fetch()['c'];
$premiumUsers = (int) $q("SELECT COUNT(*) c FROM leaderboard WHERE is_premium = 1 AND banned = 0")->fetch()['c'];

// Real revenue from the ledger, not an assumed price.
$rev = $q("
    SELECT
        COALESCE(SUM(price_rial),0)                  AS total,
        COALESCE(SUM(CASE WHEN created_at >= CURDATE() THEN price_rial ELSE 0 END),0)          AS today,
        COALESCE(SUM(CASE WHEN created_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY) THEN price_rial ELSE 0 END),0) AS month,
        COUNT(*)                                      AS orders
      FROM purchases WHERE verified = 1
")->fetch();

$purchasesToday = (int) $q("SELECT COUNT(*) c FROM purchases WHERE verified = 1 AND created_at >= CURDATE()")->fetch()['c'];
$orders = (int) $rev['orders'];
$revenueTotal = (int) $rev['total'];
$revenueToday = (int) $rev['today'];
$revenueMonth = (int) $rev['month'];

// Real conversion: buyers over all players. NULL when there is no data yet,
// because "0.0%" would read as "nobody converts" rather than "not measured".
$conversion = $totalUsers > 0 && $orders > 0 ? round($orders / $totalUsers * 100, 2) : null;
$premiumRate = $totalUsers > 0 ? round($premiumUsers / $totalUsers * 100, 2) : null;

$recent = $q("
    SELECT product_id, coins, is_premium, price_rial, device_id, created_at
      FROM purchases WHERE verified = 1
     ORDER BY created_at DESC LIMIT 40
")->fetchAll();

// 14-day revenue series.
$series = [];
for ($i = 13; $i >= 0; $i--) {
    $d = date('Y-m-d', strtotime("-$i days"));
    $series[] = [
        'date'  => $d,
        'total' => (int) $q("SELECT COALESCE(SUM(price_rial),0) c FROM purchases WHERE verified = 1 AND DATE(created_at) = ?", [$d])->fetch()['c'],
        'orders'=> (int) $q("SELECT COUNT(*) c FROM purchases WHERE verified = 1 AND DATE(created_at) = ?", [$d])->fetch()['c'],
    ];
}

$labels = json_encode(array_map(fn($d) => date('d/m', strtotime($d['date'])), $series), JSON_UNESCAPED_UNICODE);
$revJs  = json_encode(array_column($series, 'total'));
$ordJs  = json_encode(array_column($series, 'orders'));

$productTitles = [
    'blockboom_premium_59'    => 'پریمیوم',
    'offer'                   => 'پیشنهاد ویژه',
    'blockboom_coins_small'   => '۵٬۰۰۰ سکه',
    'blockboom_coins_medium'  => '۱۲٬۵۰۰ سکه',
    'blockboom_coins_large'   => '۲۵٬۰۰۰ سکه',
    'blockboom_coins_very_larg' => '۴۰٬۰۰۰ سکه',
];

renderHead('خریدها', 'purchases', true);
renderPageHeader('خریدها و درآمد', 'بر اساس دفتر واقعی خریدها (purchases)');
?>

<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
    <?php
    statCard('درآمد کل (ریال)', faNum(number_format($revenueTotal)), 'fas fa-sack-dollar', 'emerald');
    statCard('درآمد ۳۰ روز', faNum(number_format($revenueMonth)), 'fas fa-calendar-days', 'sky');
    statCard('سفارش‌های امروز', faNum($purchasesToday), 'fas fa-receipt', 'violet');
    statCard(
        $conversion === null ? 'نرخ تبدیل' : 'نرخ تبدیل: ' . faNum($conversion) . '٪',
        '',
        'fas fa-percent',
        'amber'
    );
    ?>
</div>

<?php if ($conversion === null): ?>
<div class="mb-8 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl p-4 text-sm">
    <i class="fas fa-circle-info ml-2"></i>
    نرخ تبدیل وقتی محاسبه می‌شود که حداقل یک خرید تاییدشده در دفتر خرید باشد.
    این عدد از <code>count(purchases) ÷ count(leaderboard)</code> می‌آید، نه از تخمین.
</div>
<?php endif; ?>

<div class="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-8">
    <div class="lg:col-span-2 bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-1">درآمد ۱۴ روز اخیر</h3>
        <p class="text-xs text-emerald-500 mb-4">محور چپ: ریال — محور راست: تعداد سفارش</p>
        <?php chartBox('revenueChart', 280, 'نمودار درآمد ۱۴ روز اخیر'); ?>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-4">خلاصه</h3>
        <dl class="space-y-3 text-sm">
            <div class="flex justify-between"><dt class="text-emerald-600">کل بازیکنان</dt><dd class="font-black"><?= faNum($totalUsers) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">پریمیوم</dt><dd class="font-black"><?= faNum($premiumUsers) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">سهم پریمیوم</dt>
                <dd class="font-black"><?= $premiumRate === null ? '—' : faNum($premiumRate) . '٪' ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">کل سفارش‌ها</dt><dd class="font-black"><?= faNum($orders) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">میانگین هر سفارش</dt>
                <dd class="font-black"><?= $orders > 0 ? faNum(number_format((int)($revenueTotal / $orders))) : '—' ?></dd></div>
        </dl>
        <p class="text-[11px] text-emerald-400 mt-4 leading-5">
            اگر دفتر خرید خالی است ولی کاربر پریمیوم دارد، یعنی خریدها از سمت اپ تأیید می‌شوند
            و از این دفتر عبور نکرده‌اند.
        </p>
    </div>
</div>

<!-- Recent purchases -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">۴۰ خرید آخر</h3>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'محصول', 'align' => 'right'],
                ['label' => 'مبلغ (ریال)', 'align' => 'center'],
                ['label' => 'سکه', 'align' => 'center'],
                ['label' => 'دستگاه', 'align' => 'left'],
                ['label' => 'تاریخ', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($recent as $r): ?>
                <tr class="hover:bg-emerald-50/40">
                    <td class="px-4 py-3 text-sm font-bold text-emerald-900">
                        <?= e($productTitles[$r['product_id']] ?? $r['product_id']) ?>
                    </td>
                    <td class="px-4 py-3 text-center text-sm font-bold"><?= faNum(number_format((int)$r['price_rial'])) ?></td>
                    <td class="px-4 py-3 text-center text-sm"><?= (int)$r['coins'] > 0 ? faNum(number_format((int)$r['coins'])) : '—' ?></td>
                    <td class="px-4 py-3 text-xs font-mono text-emerald-500" dir="ltr">
                        <?= $r['device_id'] ? e(substr($r['device_id'], 0, 16)) : '<span class="text-amber-600">نامشخص</span>' ?>
                    </td>
                    <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e($r['created_at']) ?></td>
                </tr>
            <?php endforeach; ?>
            <?php if (!$recent): emptyRow(5, 'هنوز خریدی ثبت نشده — از بخش «تایید خرید» استفاده کن'); endif; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- Manual grants -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
    <h3 class="font-black text-emerald-900 mb-1">اعطای دستی</h3>
    <p class="text-xs text-emerald-500 mb-5">
        سکه به‌صورت «حق برداشت» ثبت می‌شود و کلاینت در ورود بعدی یک‌بار آن را می‌گیرد.
    </p>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <form method="post" class="space-y-3">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="grant_coins">
            <input type="text" name="device_id" required placeholder="شناسهٔ دستگاه"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500" dir="ltr">
            <input type="number" name="coins" required min="1" max="1000000" value="1000" placeholder="تعداد سکه"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
            <button class="px-5 py-2.5 bg-emerald-500 text-white rounded-xl font-bold text-sm hover:bg-emerald-600">
                <i class="fas fa-coins ml-1"></i>اعطای سکه
            </button>
        </form>
        <form method="post" class="space-y-3">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="grant_premium">
            <input type="text" name="device_id" required placeholder="شناسهٔ دستگاه"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500" dir="ltr">
            <div class="flex gap-2">
                <select name="days"
                        class="px-3 py-3 bg-amber-50 border border-amber-200 rounded-xl text-sm font-bold text-amber-800">
                    <option value="7">۷ روز</option>
                    <option value="30" selected>۳۰ روز</option>
                    <option value="90">۹۰ روز</option>
                    <option value="365">۱ سال</option>
                    <option value="0">همیشگی</option>
                </select>
                <button class="flex-1 px-5 py-3 bg-amber-500 text-white rounded-xl font-bold text-sm hover:bg-amber-600">
                    <i class="fas fa-crown ml-1"></i>فعال کردن پریمیوم
                </button>
            </div>
            <p class="text-xs text-amber-700 leading-6">
                بازیکن با باز کردن دوبارهٔ بازی پریمیوم را دریافت می‌کند. برای برداشتن،
                از صفحهٔ بازیکنان دکمهٔ «حذف پریمیوم» را بزن.
            </p>
        </form>
    </div>
</div>

<script>
new Chart(document.getElementById('revenueChart'), {
    data: {
        labels: <?= $labels ?>,
        datasets: [
            { type: 'bar', label: 'ریال', data: <?= $revJs ?>, backgroundColor: 'rgba(16,185,129,0.55)',
              borderRadius: 6, yAxisID: 'y' },
            { type: 'line', label: 'سفارش', data: <?= $ordJs ?>, borderColor: '#F59E0B',
              backgroundColor: 'transparent', tension: 0.35, pointRadius: 0, borderWidth: 2, yAxisID: 'y1' }
        ]
    },
    options: {
        responsive: true, maintainAspectRatio: false, resizeDelay: 120,
        plugins: { legend: { labels: { font: { size: 11 } } } },
        scales: {
            y: { beginAtZero: true, position: 'right', ticks: { callback: (v) => v.toLocaleString('fa-IR') } },
            y1: { beginAtZero: true, position: 'left', grid: { drawOnChartArea: false }, ticks: { precision: 0 } }
        }
    }
});
</script>
<?php renderFoot(); ?>
