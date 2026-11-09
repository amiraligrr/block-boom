<?php
/**
 * Leaderboard manager — search, inspect, and act on individual players.
 *
 * users.php previously did `LIMIT ? OFFSET ?` through execute(), which binds as
 * a string and is a MySQL syntax error with ATTR_EMULATE_PREPARES = false, so
 * the player list 500'd. Limits are inlined validated integers here.
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
    } elseif ($action === 'set_name' && $device !== '') {
        $name = mb_substr(trim((string)($_POST['name'] ?? '')), 0, 16);
        if (mb_strlen($name, 'UTF-8') < 2) {
            $notice = 'نام باید حداقل ۲ کاراکتر باشد';
        } else {
            $pdo->prepare("UPDATE leaderboard SET name = ? WHERE device_id = ?")->execute([$name, $device]);
            $notice = 'نام تغییر کرد.';
        }
    } elseif ($action === 'reset_score' && $device !== '') {
        $pdo->prepare("UPDATE leaderboard SET score = 0, level = 1 WHERE device_id = ?")->execute([$device]);
        $notice = 'امتیاز و سطح بازنشانی شد.';
    } elseif ($action === 'app_ban' && $device !== '') {
        $reason = mb_substr(trim((string)($_POST['reason'] ?? '')), 0, 255, 'UTF-8');
        $pdo->prepare(
            "INSERT INTO app_bans (device_id, reason, banned_by) VALUES (?,?,?)
             ON DUPLICATE KEY UPDATE reason = VALUES(reason), is_active = 1,
                                     banned_by = VALUES(banned_by), lifted_at = NULL"
        )->execute([$device, $reason !== '' ? $reason : 'فعالیت غیرمجاز', $_SESSION['admin_user'] ?? 'admin']);
        $notice = 'دستگاه مسدود شد و اپ برایش قفل می‌شود.';
    } elseif ($action === 'app_unban' && $device !== '') {
        $pdo->prepare("UPDATE app_bans SET is_active = 0, lifted_at = NOW() WHERE device_id = ?")->execute([$device]);
        $notice = 'مسدودی اپ برداشته شد.';
    } elseif ($action === 'grant_premium' && $device !== '') {
        // See bb_admin_set_premium() in db.php for why this is not simply
        // `UPDATE leaderboard SET is_premium = 1` — that column is a crown in a
        // table, and the player's phone never read it.
        $notice = bb_admin_try_premium(fn() => bb_admin_set_premium(
            $pdo,
            $device,
            (int)($_POST['days'] ?? 30),
            (string)($_POST['note'] ?? '')
        ));
    } elseif ($action === 'revoke_premium' && $device !== '') {
        $notice = bb_admin_try_premium(fn() => bb_admin_revoke_premium($pdo, $device));
    }
}

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

$search   = trim((string)($_GET['q'] ?? ''));
$filter   = (string)($_GET['filter'] ?? 'all');   // all | premium | banned | suspicious
$page     = max(1, min(10000, (int)($_GET['page'] ?? 1)));
$perPage  = 50;
$offset   = ($page - 1) * $perPage;

// Every column is qualified with `l.` because the query below LEFT JOINs
// app_bans, and device_id exists in BOTH tables — an unqualified reference
// makes MySQL raise "Column 'device_id' in where clause is ambiguous" and the
// whole players list 500s the moment someone types in the search box.
$where = 'WHERE 1=1';
$params = [];
if ($search !== '') {
    $where .= ' AND (l.name LIKE ? OR l.device_id LIKE ?)';
    $like = '%' . $search . '%';
    $params[] = $like;
    $params[] = $like;
}
if ($filter === 'premium')   $where .= ' AND l.is_premium = 1 AND l.banned = 0';
if ($filter === 'banned')    $where .= ' AND l.banned = 1';
if ($filter === 'suspicious') $where .= ' AND l.score_rate > 2500';

// COUNT for the real total, so the pager is right — the old page counted a
// LIMIT-capped array and froze the number at the cap.
$totalRow = $q("SELECT COUNT(*) c FROM leaderboard l $where", $params)->fetch();
$total = (int) $totalRow['c'];
$totalPages = max(1, (int) ceil($total / $perPage));
if ($offset >= $total && $total > 0) {
    $page = $totalPages;
    $offset = ($page - 1) * $perPage;
}

// $perPage/$offset are clamped integers, never bound — see the header note.
//
// The LEFT JOIN on app_bans is load-bearing, not cosmetic. The template reads
// $r['app_banned'] and $r['app_ban_reason'] in five places (the "app locked"
// badge, the "normal" badge, and the unlock button), but this query never
// selected those keys and no such columns exist on `leaderboard` — the data
// lives in the separate app_bans table. PHP emitted "Warning: Undefined array
// key" for every row, the value evaluated to null, and (int)null === 0, so the
// locked badge never rendered, the "normal" badge always did, and the unlock
// button was permanently invisible. An operator could not see who was
// app-locked or unban them from this page. COALESCE keeps the outer join
// optional, which matters because app_bans only holds banned devices.
$rows = $q(
    "SELECT l.name, l.level, l.score, l.is_premium, l.banned, l.ban_reason, l.score_rate,
            l.updated_at, l.created_at, l.device_id, l.ip,
            COALESCE(ab.is_active, 0) AS app_banned,
            ab.reason AS app_ban_reason,
            ab.banned_by AS app_banned_by
       FROM leaderboard l
       LEFT JOIN app_bans ab ON ab.device_id = l.device_id
       $where
      ORDER BY l.level DESC, l.score DESC
      LIMIT $perPage OFFSET $offset",
    $params
)->fetchAll();

// Filter chips with real counts.
$counts = [
    'all'        => (int) $q("SELECT COUNT(*) c FROM leaderboard")->fetch()['c'],
    'premium'    => (int) $q("SELECT COUNT(*) c FROM leaderboard WHERE is_premium = 1 AND banned = 0")->fetch()['c'],
    'banned'     => (int) $q("SELECT COUNT(*) c FROM leaderboard WHERE banned = 1")->fetch()['c'],
    'suspicious' => (int) $q("SELECT COUNT(*) c FROM leaderboard WHERE score_rate > 2500")->fetch()['c'],
];

$filterLabels = ['all' => 'همه', 'premium' => 'پریمیوم', 'banned' => 'مسدود', 'suspicious' => 'مشکوک'];

renderHead('لیدربرد', 'leaderboard');
renderPageHeader('لیدربرد', 'جستجو، بازرسی و مدیریت بازیکنان');
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>

<!-- Search + filters -->
<form method="get" class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-5 mb-6">
    <div class="flex flex-col md:flex-row gap-3">
        <div class="relative flex-1">
            <i class="fas fa-search absolute right-4 top-1/2 -translate-y-1/2 text-emerald-400"></i>
            <input type="text" name="q" value="<?= e($search) ?>" placeholder="جستجو با نام یا شناسهٔ دستگاه…"
                class="w-full pr-11 pl-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <input type="hidden" name="filter" value="<?= e($filter) ?>">
        <button class="px-6 py-3 bg-emerald-500 text-white rounded-xl font-bold hover:bg-emerald-600 transition-all">
            <i class="fas fa-search ml-1"></i>جستجو
        </button>
    </div>
    <div class="flex flex-wrap gap-2 mt-4">
        <?php foreach ($filterLabels as $k => $label): ?>
            <a href="?filter=<?= e($k) ?>&q=<?= urlencode($search) ?>"
               class="px-3.5 py-2 rounded-lg text-xs font-bold transition-all
                      <?= $filter === $k ? 'bg-emerald-500 text-white' : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100' ?>">
                <?= e($label) ?> <span class="opacity-70"><?= faNum($counts[$k]) ?></span>
            </a>
        <?php endforeach; ?>
    </div>
</form>

<!-- Table -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-6">
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => '#', 'align' => 'center'],
                ['label' => 'نام', 'align' => 'right'],
                ['label' => 'سطح', 'align' => 'center'],
                ['label' => 'امتیاز', 'align' => 'center'],
                ['label' => 'نرخ/ثانیه', 'align' => 'center'],
                ['label' => 'وضعیت', 'align' => 'center'],
                ['label' => 'آخرین بازی', 'align' => 'center'],
                ['label' => 'دستگاه', 'align' => 'left'],
                ['label' => 'عملیات', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($rows as $i => $r):
                $rank = $offset + $i + 1;
                $rate = (float)$r['score_rate'];
            ?>
            <tr class="hover:bg-emerald-50/40">
                <td class="px-3 py-3 text-center text-xs text-emerald-500"><?= faNum($rank) ?></td>
                <td class="px-3 py-3">
                    <form method="post" class="flex items-center gap-1.5">
                        <?= csrfField() ?>
                        <input type="hidden" name="action" value="set_name">
                        <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                        <input type="text" name="name" value="<?= e($r['name']) ?>" maxlength="16"
                            class="w-28 px-2 py-1.5 text-sm border border-emerald-200 rounded-lg outline-none focus:ring-2 focus:ring-emerald-400">
                        <button class="text-emerald-500 hover:text-emerald-700" title="ذخیرهٔ نام">
                            <i class="fas fa-check"></i>
                        </button>
                    </form>
                </td>
                <td class="px-3 py-3 text-center text-sm font-bold"><?= faNum($r['level']) ?></td>
                <td class="px-3 py-3 text-center text-sm font-bold"><?= faNum(number_format((int)$r['score'])) ?></td>
                <td class="px-3 py-3 text-center">
                    <span class="badge <?= $rate > 2500 ? 'bg-rose-100 text-rose-700 font-black' : 'text-emerald-600' ?>">
                        <?= $rate > 0 ? faNum(number_format($rate)) : '—' ?>
                    </span>
                </td>
                <td class="px-3 py-3 text-center">
                    <?php if ((int)$r['app_banned'] === 1): ?>
                        <?php
                        // Surface who locked the device and why. banned_by used
                        // to be a permanently useless column because
                        // $_SESSION['admin_user'] was never assigned anywhere in
                        // the panel, so every ban was recorded as the literal
                        // string 'admin'. login.php now sets it; without showing
                        // it here the audit trail still had no reader.
                        $banBy  = trim((string)($r['app_banned_by'] ?? ''));
                        $banWhy = trim((string)($r['app_ban_reason'] ?? ''));
                        if ($banWhy === '') $banWhy = 'بدون دلیل ثبت‌شده';
                        if ($banBy !== '')   $banWhy .= ' — توسط ' . $banBy;
                        ?>
                        <span class="badge bg-rose-600 text-white" title="<?= e($banWhy) ?>">اپ قفل</span>
                    <?php endif; ?>
                    <?php if ((int)$r['banned'] === 1): ?>
                        <span class="badge bg-rose-100 text-rose-700" title="<?= e($r['ban_reason']) ?>">از لیدربرد حذف</span>
                    <?php elseif ((int)$r['is_premium'] === 1): ?>
                        <span class="badge bg-amber-100 text-amber-700">👑 پریمیوم</span>
                    <?php else: ?>
                        <?php if ((int)$r['app_banned'] !== 1): ?>
                            <span class="badge bg-emerald-50 text-emerald-600">عادی</span>
                        <?php endif; ?>
                    <?php endif; ?>
                </td>
                <td class="px-3 py-3 text-center text-xs text-emerald-500"><?= e($r['updated_at']) ?></td>
                <td class="px-3 py-3 text-xs font-mono text-emerald-500" dir="ltr"><?= e(substr($r['device_id'], 0, 14)) ?></td>
                <td class="px-3 py-3">
                    <div class="flex items-center justify-center gap-1 flex-wrap">
                        <?php if ((int)$r['banned'] === 1): ?>
                            <form method="post">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="unban">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <button class="px-2 py-1.5 bg-emerald-100 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-200">رفع مسدودی</button>
                            </form>
                        <?php else: ?>
                            <form method="post" onsubmit="return confirm('مسدود شود؟')">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="ban">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <input type="hidden" name="reason" value="مسدودی دستی">
                                <button class="px-2 py-1.5 bg-rose-500 text-white rounded-lg text-xs font-bold hover:bg-rose-600">مسدود</button>
                            </form>
                        <?php endif; ?>

                        <form method="post" onsubmit="return confirm('امتیاز و سطح صفر شود؟')">
                            <?= csrfField() ?>
                            <input type="hidden" name="action" value="reset_score">
                            <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                            <button class="px-2 py-1.5 bg-amber-100 text-amber-700 rounded-lg text-xs font-bold hover:bg-amber-200">بازنشانی</button>
                        </form>

                        <?php /* App-lock is a different thing from hiding them
                                 from the board. */ ?>
                        <?php if ((int)$r['app_banned'] === 1): ?>
                            <form method="post" class="inline">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="app_unban">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <button class="px-2 py-1.5 bg-emerald-600 text-white rounded-lg text-xs font-bold hover:bg-emerald-700">رفع قفل اپ</button>
                            </form>
                        <?php else: ?>
                            <form method="post" onsubmit="return confirm('اپ برای این کاربر قفل شود؟')">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="app_ban">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <input type="hidden" name="reason" value="با توجه به فعالیت‌های غیرمجاز اخیر، حساب شما مسدود شده است.">
                                <button class="px-2 py-1.5 bg-rose-600 text-white rounded-lg text-xs font-bold hover:bg-rose-700">قفل اپ</button>
                            </form>
                        <?php endif; ?>

                        <?php if ((int)$r['is_premium'] === 1): ?>
                            <form method="post" onsubmit="return confirm('پریمیوم این بازیکن برداشته شود؟')">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="revoke_premium">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <button class="px-2 py-1.5 bg-slate-100 text-slate-600 rounded-lg text-xs font-bold hover:bg-slate-200">حذف پریمیوم</button>
                            </form>
                        <?php else: ?>
                            <!--
                                A duration, not a bare button. The grant is stored
                                with an expiry on the server, so "premium" without a
                                length has to mean something: 0 is the explicit
                                "never expires" option rather than an accident of a
                                missing field.
                            -->
                            <form method="post" class="inline-flex items-center gap-1">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="grant_premium">
                                <input type="hidden" name="device_id" value="<?= e($r['device_id']) ?>">
                                <select name="days"
                                        class="px-1.5 py-1 bg-amber-50 border border-amber-200 rounded-lg text-xs font-bold text-amber-800">
                                    <option value="7">۷ روز</option>
                                    <option value="30" selected>۳۰ روز</option>
                                    <option value="90">۹۰ روز</option>
                                    <option value="365">۱ سال</option>
                                    <option value="0">همیشگی</option>
                                </select>
                                <button class="px-2 py-1.5 bg-amber-100 text-amber-700 rounded-lg text-xs font-bold hover:bg-amber-200">پریمیوم</button>
                            </form>
                        <?php endif; ?>
                    </div>
                </td>
            </tr>
            <?php endforeach; ?>
            <?php if (!$rows): emptyRow(9, 'بازیکنی با این فیلتر پیدا نشد'); endif; ?>
            </tbody>
        </table>
    </div>

    <?php if ($totalPages > 1): ?>
    <div class="px-6 py-4 border-t border-emerald-100 flex items-center justify-between">
        <span class="text-xs text-emerald-500">
            صفحهٔ <?= faNum($page) ?> از <?= faNum($totalPages) ?> — <?= faNum($total) ?> بازیکن
        </span>
        <div class="flex gap-2">
            <?php if ($page > 1): ?>
                <a href="?filter=<?= e($filter) ?>&q=<?= urlencode($search) ?>&page=<?= $page - 1 ?>"
                   class="px-4 py-2 bg-emerald-50 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-100">قبلی</a>
            <?php endif; ?>
            <?php if ($page < $totalPages): ?>
                <a href="?filter=<?= e($filter) ?>&q=<?= urlencode($search) ?>&page=<?= $page + 1 ?>"
                   class="px-4 py-2 bg-emerald-50 text-emerald-700 rounded-lg text-xs font-bold hover:bg-emerald-100">بعدی</a>
            <?php endif; ?>
        </div>
    </div>
    <?php endif; ?>
</div>
<?php renderFoot(); ?>
