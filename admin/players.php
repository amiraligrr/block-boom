<?php
/**
 * Server players — real accounts, installs and daily activity.
 *
 * The public leaderboard is a table of scores. The server is a table of
 * players. This page exists because the two drifted apart: a player who played
 * every day and never got a leaderboard row used to be invisible here, and the
 * admin panel then concluded "nobody plays this game" while the ad revenue
 * said otherwise.
 *
 * It reads the same three sources the client now writes through:
 *   accounts        — one row per real player
 *   player_installs — the install history of an account (factory reset/new phone)
 *   player_daily    — the daily bucket, written even without a leaderboard score
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();
$notice = null;
$error = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action = (string)($_POST['action'] ?? '');
    $device = bb_admin_normalize_device((string)($_POST['device_id'] ?? ''));

    if ($action === 'app_unban' && $device !== '') {
        $pdo->prepare("UPDATE app_bans SET is_active = 0, lifted_at = NOW() WHERE device_id = ?")->execute([$device]);
        $notice = 'قفل اپ برداشته شد.';
    } elseif ($action === 'app_ban' && $device !== '') {
        $reason = mb_substr(trim((string)($_POST['reason'] ?? '')), 0, 255, 'UTF-8');
        $pdo->prepare(
            "INSERT INTO app_bans (device_id, reason, banned_by) VALUES (?,?,?)
             ON DUPLICATE KEY UPDATE reason = VALUES(reason), is_active = 1,
                                     banned_by = VALUES(banned_by), lifted_at = NULL"
        )->execute([$device, $reason !== '' ? $reason : 'فعالیت غیرمجاز', $_SESSION['admin_user'] ?? 'admin']);
        $notice = 'دستگاه مسدود شد و اپ برایش قفل می‌شود.';
    }
}

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

$search = trim((string)($_GET['q'] ?? ''));
$page = max(1, min(10000, (int)($_GET['page'] ?? 1)));
$perPage = 50;
$offset = ($page - 1) * $perPage;
$where = 'a.is_test = 0';
$params = [];
if ($search !== '') {
    $where .= ' AND (a.player_name LIKE ? OR a.install_id LIKE ? OR i.device_id LIKE ?)';
    $like = '%' . $search . '%';
    $params[] = $like;
    $params[] = $like;
    $params[] = $like;
}

$totalAccounts = (int) $q('SELECT COUNT(*) c FROM accounts a WHERE a.is_test = 0')->fetch()['c'];
$totalInstalls = (int) $q('SELECT COUNT(*) c FROM player_installs')->fetch()['c'];
$todayAccounts = (int) $q(
    'SELECT COUNT(DISTINCT p.account_id) c
       FROM player_daily p
       JOIN accounts a ON a.id = p.account_id
      WHERE p.day = CURDATE() AND a.is_test = 0'
)->fetch()['c'];
$accountsWithoutDaily = (int) $q(
    'SELECT COUNT(*) c
       FROM accounts a
      WHERE a.is_test = 0 AND NOT EXISTS (
          SELECT 1 FROM player_daily p WHERE p.account_id = a.id
      )'
)->fetch()['c'];

// The count query used to be `FROM accounts a $where` while the search clause
// referenced `i.device_id`. As soon as the admin typed anything into the search
// box, that produced "Unknown column 'i.device_id' in 'where clause'" and the
// page returned HTTP 500. Keep the same LEFT JOIN shape as the row query so the
// count and the rows can never disagree.
$totalRow = $q(
    "SELECT COUNT(DISTINCT a.id) c
       FROM accounts a
       LEFT JOIN player_installs i ON i.id = (
           SELECT i2.id FROM player_installs i2
            WHERE i2.account_id = a.id
            ORDER BY i2.last_seen_at DESC, i2.id DESC
            LIMIT 1
       )
      WHERE $where",
    $params
)->fetch();
$total = (int) $totalRow['c'];
$totalPages = max(1, (int) ceil($total / $perPage));
if ($offset >= $total && $total > 0) {
    $page = $totalPages;
    $offset = ($page - 1) * $perPage;
}

// One row per player. player_installs can hold several installs for the same
// account (old phone + new phone), so the subquery picks the account's most
// recently seen install. The count query above uses the same join and counts
// DISTINCT account ids, so the pager and the rows can never disagree.
$rows = $q(
    "SELECT a.id, a.player_name, a.install_id, a.app_version, a.install_count,
            a.last_ip, a.last_level, a.last_score,
            i.device_id, i.first_seen_at, i.last_seen_at,
            d.day AS last_day, d.total_score AS last_daily_score,
            d.runs AS last_daily_runs, d.play_seconds AS last_daily_play_seconds,
            COALESCE(ab.is_active,0) AS app_banned, ab.reason AS app_ban_reason
       FROM accounts a
       LEFT JOIN player_installs i ON i.id = (
           SELECT i2.id FROM player_installs i2
            WHERE i2.account_id = a.id
            ORDER BY i2.last_seen_at DESC, i2.id DESC
            LIMIT 1
       )
       LEFT JOIN (
           SELECT account_id, MAX(day) AS day FROM player_daily GROUP BY account_id
       ) ld ON ld.account_id = a.id
       LEFT JOIN player_daily d
              ON d.account_id = a.id AND d.day = ld.day
       LEFT JOIN app_bans ab ON ab.device_id = i.device_id
      WHERE $where
      ORDER BY a.last_seen_at DESC, a.id DESC
      LIMIT $perPage OFFSET $offset",
    $params
)->fetchAll();

renderHead('بازیکنان', 'players');
renderPageHeader(
    'بازیکنان',
    'حقایق سرور: حساب‌ها، نصب‌ها و دادهٔ روزانهٔ بازی',
    '<a href="leaderboard.php" class="px-4 py-2 bg-emerald-50 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-100">
        <i class="fas fa-arrow-left ml-1"></i>مشاهدهٔ لیدربرد
    </a>'
);
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>

<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
    <?php statCard('کل حساب‌ها', faNum($totalAccounts), 'fas fa-user-astronaut', 'emerald'); ?>
    <?php statCard('کل نصب‌ها', faNum($totalInstalls), 'fas fa-download', 'sky'); ?>
    <?php statCard('بازیکن‌های فعال امروز', faNum($todayAccounts), 'fas fa-heartbeat', 'rose'); ?>
    <?php statCard('حساب‌ها بدون دادهٔ امروز', faNum($accountsWithoutDaily), 'fas fa-triangle-exclamation', 'amber'); ?>
</div>

<form method="get" class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-5 mb-6">
    <div class="flex flex-col md:flex-row gap-3">
        <div class="relative flex-1">
            <i class="fas fa-search absolute right-4 top-1/2 -translate-y-1/2 text-emerald-400"></i>
            <input type="text" name="q" value="<?= e($search) ?>" placeholder="جستجو با نام، شناسهٔ نصب یا دستگاه…"
                class="w-full pr-11 pl-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <button class="px-6 py-3 bg-emerald-500 text-white rounded-xl font-bold hover:bg-emerald-600 transition-all">
            <i class="fas fa-search ml-1"></i>جستجو
        </button>
    </div>
</form>

<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-6">
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => '#', 'align' => 'center'],
                ['label' => 'حساب', 'align' => 'right'],
                ['label' => 'نصب', 'align' => 'center'],
                ['label' => 'آخرین سطح', 'align' => 'center'],
                ['label' => 'آخرین امتیاز', 'align' => 'center'],
                ['label' => 'فعال امروز', 'align' => 'center'],
                ['label' => 'آخرین بازی', 'align' => 'center'],
                ['label' => 'دستگاه', 'align' => 'left'],
                ['label' => 'وضعیت', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($rows as $i => $r):
                $rank = $offset + $i + 1;
            ?>
            <tr class="hover:bg-emerald-50/40">
                <td class="px-3 py-3 text-center text-xs text-emerald-500"><?= faNum($rank) ?></td>
                <td class="px-3 py-3 text-right">
                    <div class="text-sm font-bold text-emerald-900"><?= e($r['player_name'] ?: 'بازیکن') ?></div>
                    <div class="text-[11px] text-emerald-500">ID: <?= faNum((int)$r['id']) ?></div>
                </td>
                <td class="px-3 py-3 text-center">
                    <div class="text-xs font-mono text-emerald-600"><?= e($r['install_id']) ?></div>
                    <div class="text-[11px] text-emerald-500">نسخه <?= e($r['app_version']) ?></div>
                </td>
                <td class="px-3 py-3 text-center text-sm font-bold"><?= faNum((int)$r['last_level']) ?></td>
                <td class="px-3 py-3 text-center text-sm font-bold"><?= faNum(number_format((int)$r['last_score'])) ?></td>
                <td class="px-3 py-3 text-center text-sm"><?= $r['last_day'] ? e($r['last_day']) : '—' ?></td>
                <td class="px-3 py-3 text-center text-xs text-emerald-500"><?= $r['last_seen_at'] ? e($r['last_seen_at']) : '—' ?></td>
                <td class="px-3 py-3 text-xs font-mono text-emerald-500" dir="ltr"><?= e($r['device_id']) ?></td>
                <td class="px-3 py-3 text-center">
                    <?php if ((int)$r['app_banned'] === 1): ?>
                        <span class="badge bg-rose-600 text-white">اپ قفل</span>
                    <?php else: ?>
                        <span class="badge bg-emerald-50 text-emerald-600">عادی</span>
                    <?php endif; ?>
                </td>
            </tr>
            <?php endforeach; ?>
            <?php if (!$rows): emptyRow(9, 'بازیکنی با این فیلتر پیدا نشد'); endif; ?>
            </tbody>
        </table>
    </div>

    <?php if ($totalPages > 1): ?>
    <div class="px-6 py-4 border-t border-emerald-100 flex items-center justify-between">
        <span class="text-xs text-emerald-500">صفحهٔ <?= faNum($page) ?> از <?= faNum($totalPages) ?> — <?= faNum($total) ?> ردیف</span>
        <div class="flex gap-2">
            <?php if ($page > 1): ?>
                <a href="?q=<?= urlencode($search) ?>&page=<?= $page - 1 ?>"
                   class="px-4 py-2 bg-emerald-50 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-100">قبلی</a>
            <?php endif; ?>
            <?php if ($page < $totalPages): ?>
                <a href="?q=<?= urlencode($search) ?>&page=<?= $page + 1 ?>"
                   class="px-4 py-2 bg-emerald-50 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-100">بعدی</a>
            <?php endif; ?>
        </div>
    </div>
    <?php endif; ?>
</div>

<?php renderFoot(); ?>
