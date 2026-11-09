<?php
/**
 * Dashboard.
 *
 * The numbers here were materially wrong, which matters more than the styling:
 *
 *  - "Active devices in the last 7 days" filtered on created_at, which is only
 *    written on a player's FIRST insert. A daily player who joined months ago
 *    was counted as churn. Retention was reporting acquisition.
 *  - The daily chart counted created_at too, so it plotted signups, not play.
 *  - "Top 10" rendered LIMIT 20.
 *  - CTR divided by banner impressions only, and the app never emits a 'click'
 *    event at all — so the number was structurally pinned at 0.00%.
 *  - The doughnut tooltip appended '%' to a raw count, rendering a bucket of
 *    500 players as "500%".
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();

$q = function (string $sql, array $params = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($params);
    return $st;
};

// ── headline ──────────────────────────────────────────────────────────────
// The panel used to counted the leaderboard table and called it the player
// base. The leaderboard is a leaderboard: it only holds players who ever
// scored. The server now tracks every registered player, so the headline
// numbers are accounts joined to their installs — not a score table.
$totalUsers   = (int) $q("SELECT COUNT(*) c FROM accounts WHERE is_test = 0")->fetch()['c'];
$bannedUsers  = (int) $q(
    "SELECT COUNT(DISTINCT a.id) c
       FROM accounts a
       JOIN player_installs i ON i.account_id = a.id
       LEFT JOIN app_bans ab ON ab.device_id = i.device_id
      WHERE a.is_test = 0 AND ab.is_active = 1"
)->fetch()['c'];
$premiumUsers = (int) $q(
    "SELECT COUNT(DISTINCT i.account_id) c
       FROM player_installs i
       JOIN leaderboard l ON l.device_id = i.device_id
      WHERE l.is_premium = 1 AND l.banned = 0
        AND i.account_id IN (SELECT id FROM accounts WHERE is_test = 0)"
)->fetch()['c'];
$activeAccounts = (int) $q(
    "SELECT COUNT(DISTINCT a.id) c
       FROM accounts a
       JOIN player_installs i ON i.account_id = a.id
      WHERE a.is_test = 0"
)->fetch()['c'];
$totalScore   = (int) $q("SELECT COALESCE(SUM(last_score),0) c FROM accounts WHERE is_test = 0")->fetch()['c'];
$avgLevel     = round((float) $q("SELECT COALESCE(AVG(last_level),0) c FROM accounts WHERE is_test = 0")->fetch()['c'], 1);

// ── activity: daily rows, not created_at ──────────────────────────────────
// A daily player who joined months ago used to be counted as churn because we
// filtered activity on created_at. The server's daily bucket is the real
// "played today" signal, and it is written even without a leaderboard score.
$ret = $q("
    SELECT
        SUM(IF(d.last_day >= CURDATE(), 1, 0))                                        AS d1,
        SUM(IF(d.last_day >= DATE_SUB(CURDATE(), INTERVAL 6 DAY), 1, 0))            AS d7,
        SUM(IF(d.last_day >= DATE_SUB(CURDATE(), INTERVAL 29 DAY), 1, 0))           AS d30,
        COUNT(DISTINCT IF(d.last_day >= DATE_SUB(CURDATE(), INTERVAL 6 DAY), a.id, NULL)) AS ips7,
        COUNT(DISTINCT IF(d.last_day >= CURDATE(), a.id, NULL))                   AS ips1
    FROM accounts a
    JOIN player_installs i ON i.account_id = a.id
    LEFT JOIN (
        SELECT account_id, MAX(day) AS last_day
          FROM player_daily
         GROUP BY account_id
    ) d ON d.account_id = a.id
    WHERE a.is_test = 0
")->fetch();

$todayActive = (int) ($ret['d1'] ?? 0);
$weekActive  = (int) ($ret['d7'] ?? 0);
$monthActive = (int) ($ret['d30'] ?? 0);
$weekIPs     = (int) ($ret['ips7'] ?? 0);
$todayIPs    = (int) ($ret['ips1'] ?? 0);

// Signed up vs played today — the gap between them is the honest "new vs
// returning" split, and it needs both timestamps.
$newToday = (int) $q("SELECT COUNT(*) c FROM leaderboard WHERE DATE(created_at) = CURDATE()")->fetch()['c'];

// DAU / MAU stickiness: the number that actually predicts whether this app is
// working. MAU > 0 guard so a fresh install doesn't show a fake 0%.
$stickiness = $monthActive > 0 ? round($todayActive / $monthActive * 100, 1) : null;

// ── daily activity series (played, not signed up) ─────────────────────────
$dailyStats = [];
for ($i = 13; $i >= 0; $i--) {
    $date = date('Y-m-d', strtotime("-$i days"));
    $dailyStats[] = [
        'date'  => $date,
        'played' => (int) $q("SELECT COUNT(*) c FROM player_daily WHERE day = ?", [$date])->fetch()['c'],
        'new'    => (int) $q("SELECT COUNT(*) c FROM accounts WHERE DATE(created_at) = ?", [$date])->fetch()['c'],
    ];
}

// ── ads today ─────────────────────────────────────────────────────────────
$adRows = $q("
    SELECT event_type, COUNT(*) c
      FROM ad_events
     WHERE created_at >= CURDATE()
     GROUP BY event_type
")->fetchAll();
$adToday = [];
foreach ($adRows as $r) $adToday[$r['event_type']] = (int) $r['c'];

$adImpressions = $adToday['impression'] ?? 0;
$adClicks      = $adToday['click'] ?? 0;
$adRewarded    = ($adToday['rewarded_shown'] ?? 0);
$adRewardedOk  = ($adToday['rewarded_complete'] ?? 0);
$gameStarts    = $adToday['game_start'] ?? 0;
$gameCompletes = $adToday['game_complete'] ?? 0;

// A rewarded view is a real ad view, so it belongs in the denominator too.
$adViews = $adImpressions + $adRewarded;
$ctr = $adViews > 0 ? round($adClicks / $adViews * 100, 2) : null;

// ── top players ───────────────────────────────────────────────────────────
$topUsers = $q("
    SELECT name, level, score, is_premium, updated_at, device_id
      FROM leaderboard WHERE banned = 0
     ORDER BY level DESC, score DESC LIMIT 10
")->fetchAll();

// ── level distribution ────────────────────────────────────────────────────
$buckets = $q("
    SELECT
        SUM(IF(last_level BETWEEN 1 AND 10, 1, 0))   AS b1,
        SUM(IF(last_level BETWEEN 11 AND 50, 1, 0)) AS b2,
        SUM(IF(last_level BETWEEN 51 AND 100, 1, 0)) AS b3,
        SUM(IF(last_level > 100, 1, 0))              AS b4
    FROM accounts WHERE is_test = 0
")->fetch();
$deviceStats = [
    ['name' => 'لول ۱ تا ۱۰',   'count' => (int) ($buckets['b1'] ?? 0), 'color' => '#10B981'],
    ['name' => 'لول ۱۱ تا ۵۰', 'count' => (int) ($buckets['b2'] ?? 0), 'color' => '#0EA5E9'],
    ['name' => 'لول ۵۱ تا ۱۰۰','count' => (int) ($buckets['b3'] ?? 0), 'color' => '#6366F1'],
    ['name' => 'بالای ۱۰۰',     'count' => (int) ($buckets['b4'] ?? 0), 'color' => '#F59E0B'],
];

// ── anti-cheat pulse ──────────────────────────────────────────────────────
$flagToday = (int) $q("SELECT COUNT(*) c FROM cheat_flags WHERE created_at >= CURDATE()")->fetch()['c'];
$flagWeek  = (int) $q("SELECT COUNT(*) c FROM cheat_flags WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)")->fetch()['c'];

$labels    = json_encode(array_map(fn($d) => date('d/m', strtotime($d['date'])), $dailyStats), JSON_UNESCAPED_UNICODE);
$playedJs  = json_encode(array_column($dailyStats, 'played'));
$newJs     = json_encode(array_column($dailyStats, 'new'));
$devLabels = json_encode(array_map(fn($d) => $d['name'], $deviceStats), JSON_UNESCAPED_UNICODE);
$devData   = json_encode(array_column($deviceStats, 'count'));
$devColors = json_encode(array_column($deviceStats, 'color'));

renderHead('داشبورد', 'dashboard', true);
renderPageHeader('داشبورد', 'نمای کلی بازی، بازیکنان و درآمد', $flagWeek > 0
    ? '<a href="integrity.php" class="px-4 py-2.5 bg-rose-50 text-rose-700 rounded-xl font-medium text-sm hover:bg-rose-100 transition-all">'
        . '<i class="fas fa-shield-alt ml-1"></i>' . faNum($flagWeek) . ' هشدار ضدتقلب</a>'
    : '');
?>

<!-- KPI row -->
<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
    <?php
    statCard('بازیکنان کل', faNum($totalUsers), 'fas fa-users', 'emerald');
    statCard('بازیکنان فعال امروز', faNum($todayActive), 'fas fa-bolt', 'sky');
    statCard('کاربران پریمیوم', faNum($premiumUsers), 'fas fa-crown', 'amber');
    statCard('میانگین سطح', faNum($avgLevel), 'fas fa-layer-group', 'violet');
    ?>
</div>

<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
    <?php
    statCard('۷ روز اخیر', faNum($weekActive), 'fas fa-calendar-week', 'emerald');
    statCard('۳۰ روز اخیر', faNum($monthActive), 'fas fa-calendar-days', 'sky');
    statCard('جدید امروز', faNum($newToday), 'fas fa-user-plus', 'violet');
    statCard(
        $stickiness === null ? 'ماندگاری (DAU/MAU)' : 'ماندگاری: ' . faNum($stickiness) . '٪',
        $stickiness === null ? '—' : '',
        'fas fa-heart',
        'rose'
    );
    ?>
</div>

<div class="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-8">
    <!-- Activity: played vs new -->
    <div class="lg:col-span-2 bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <div class="flex items-center justify-between mb-4">
            <h3 class="font-black text-emerald-900">فعالیت ۱۴ روز اخیر</h3>
            <div class="flex gap-4 text-xs text-emerald-700">
                <span><i class="fas fa-circle text-emerald-500 ml-1"></i>بازی کردند</span>
                <span><i class="fas fa-circle text-sky-400 ml-1"></i>جدید</span>
            </div>
        </div>
        <?php chartBox('activityChart', 260, 'نمودار فعالیت ۱۴ روز اخیر'); ?>
    </div>

    <!-- Level distribution -->
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-4">توزیع سطح بازیکنان</h3>
        <?php chartBox('levelChart', 260, 'نمودار توزیع سطح'); ?>
    </div>
</div>

<!-- Ads -->
<div class="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
    <?php
    statCard('نمایش تبلیغ امروز', faNum($adImpressions), 'fas fa-eye', 'sky');
    statCard('تبلیغ پاداش‌داده', faNum($adRewarded), 'fas fa-gift', 'emerald');
    statCard('پاداش تکمیل‌شده', faNum($adRewardedOk), 'fas fa-circle-check', 'emerald');
    statCard('شروع بازی', faNum($gameStarts), 'fas fa-play', 'violet');
    // NULL, not 0: no click events are emitted yet, so a 0.00% here reads as
    // "nobody clicked" when it actually means "we are not measuring it".
    statCard('نرخ کلیک', $ctr === null ? '—' : faNum($ctr) . '٪', 'fas fa-hand-pointer', 'amber');
    ?>
</div>

<?php if ($ctr === null && $adViews > 0): ?>
<div class="mb-8 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl p-4 text-sm">
    <i class="fas fa-circle-info ml-2"></i>
    رویداد <code>click</code> از سمت اپ ارسال نمی‌شود، پس نرخ کلیک قابل اندازه‌گیری نیست.
    برای فعال شدنش باید در <code>AdManager</code> رویداد کلیک روی تبلیغ گزارش شود.
</div>
<?php endif; ?>

<!-- Top players -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="px-6 py-4 border-b border-emerald-100 flex items-center justify-between">
        <h3 class="font-black text-emerald-900">۱۰ بازیکن برتر</h3>
        <a href="leaderboard.php" class="text-sm text-emerald-600 hover:text-emerald-800 font-medium">مشاهدهٔ کامل <i class="fas fa-arrow-left ml-1"></i></a>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => '#'],
                ['label' => 'نام', 'align' => 'right'],
                ['label' => 'سطح', 'align' => 'center'],
                ['label' => 'امتیاز', 'align' => 'center'],
                ['label' => 'آخرین بازی', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($topUsers as $i => $u): ?>
                <tr class="hover:bg-emerald-50/40">
                    <td class="px-4 py-3 text-center">
                        <span class="inline-flex w-6 h-6 items-center justify-center rounded-lg text-xs font-black
                            <?= $i === 0 ? 'bg-amber-100 text-amber-700' : ($i === 1 ? 'bg-slate-100 text-slate-600' : ($i === 2 ? 'bg-orange-100 text-orange-700' : 'text-emerald-600')) ?>">
                            <?= faNum($i + 1) ?>
                        </span>
                    </td>
                    <td class="px-4 py-3 text-sm font-bold text-emerald-900">
                        <?= e($u['name']) ?><?= (int)$u['is_premium'] === 1 ? ' <span title="پریمیوم">👑</span>' : '' ?>
                    </td>
                    <td class="px-4 py-3 text-center text-sm"><?= faNum($u['level']) ?></td>
                    <td class="px-4 py-3 text-center text-sm font-bold"><?= faNum(number_format((int)$u['score'])) ?></td>
                    <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e($u['updated_at']) ?></td>
                </tr>
            <?php endforeach; ?>
            <?php if (!$topUsers): emptyRow(5, 'هنوز بازیکنی ثبت نشده'); endif; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- Network + anti-cheat summary -->
<div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-3">شبکه</h3>
        <dl class="space-y-2 text-sm">
            <div class="flex justify-between"><dt class="text-emerald-600">آی‌پی یکتا امروز</dt><dd class="font-black"><?= faNum($todayIPs) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">آی‌پی یکتا در ۷ روز</dt><dd class="font-black"><?= faNum($weekIPs) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">بازیکنان مسدود</dt><dd class="font-black text-rose-600"><?= faNum($bannedUsers) ?></dd></div>
        </dl>
        <p class="text-[11px] text-emerald-400 mt-3 leading-5">
            آی‌پی‌ها فقط وقتی قابل اتکا هستند که <code>TRUSTED_PROXY_HEADER</code> در کانفیگ تنظیم شده باشد.
        </p>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-3">ضدتقلب</h3>
        <dl class="space-y-2 text-sm">
            <div class="flex justify-between"><dt class="text-emerald-600">رد شدن امروز</dt><dd class="font-black"><?= faNum($flagToday) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">رد شدن ۷ روز</dt><dd class="font-black"><?= faNum($flagWeek) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">امتیاز کل بازیکنان</dt><dd class="font-black"><?= faNum(compactNum($totalScore)) ?></dd></div>
        </dl>
        <a href="integrity.php" class="mt-4 inline-block text-sm font-medium text-emerald-600 hover:text-emerald-800">
            بررسی جزئیات <i class="fas fa-arrow-left ml-1"></i>
        </a>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-3">سلامت داده‌ها</h3>
        <dl class="space-y-2 text-sm">
            <div class="flex justify-between"><dt class="text-emerald-600">بازیکنان فعال (بدون مسدودی)</dt><dd class="font-black"><?= faNum($activeAccounts) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">تکمیل بازی / شروع</dt>
                <dd class="font-black"><?= $gameStarts > 0 ? faNum(round($gameCompletes / $gameStarts * 100)) . '٪' : '—' ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">تکمیل پاداش / نمایش</dt>
                <dd class="font-black"><?= $adRewarded > 0 ? faNum(round($adRewardedOk / $adRewarded * 100)) . '٪' : '—' ?></dd></div>
        </dl>
    </div>
</div>

<script>
new Chart(document.getElementById('activityChart'), {
    type: 'line',
    data: {
        labels: <?= $labels ?>,
        datasets: [
            { label: 'بازی کردند', data: <?= $playedJs ?>, borderColor: '#10B981',
              backgroundColor: 'rgba(16,185,129,0.12)', fill: true, tension: 0.35,
              pointRadius: 0, borderWidth: 2 },
            { label: 'جدید', data: <?= $newJs ?>, borderColor: '#38BDF8',
              backgroundColor: 'transparent', fill: false, tension: 0.35,
              pointRadius: 0, borderWidth: 2, borderDash: [4, 4] }
        ]
    },
    options: {
        responsive: true, maintainAspectRatio: false, resizeDelay: 120,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
    }
});

new Chart(document.getElementById('levelChart'), {
    type: 'doughnut',
    data: {
        labels: <?= $devLabels ?>,
        datasets: [{ data: <?= $devData ?>, backgroundColor: <?= $devColors ?>, borderWidth: 0 }]
    },
    options: {
        responsive: true, maintainAspectRatio: false, resizeDelay: 120, cutout: '62%',
        plugins: {
            legend: { position: 'bottom', labels: { font: { size: 11 } } },
            // ctx.raw is the COUNT fed in below, not a percentage — appending
            // '%' to it rendered "500%" for a bucket of 500 players.
            tooltip: {
                callbacks: {
                    label: (ctx) => {
                        const total = ctx.dataset.data.reduce((a, b) => a + b, 0);
                        const pct = total > 0 ? Math.round((ctx.raw / total) * 100) : 0;
                        return ctx.label + ': ' + ctx.raw.toLocaleString('fa-IR') + ' (' + pct + '٪)';
                    }
                }
            }
        }
    }
});
</script>
<?php renderFoot(); ?>
