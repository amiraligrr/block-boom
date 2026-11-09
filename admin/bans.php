<?php
/**
 * Ban management.
 *
 * app_bans is what the app actually obeys: app_config.php reports it, and
 * start_run.php / submit_score.php re-check it so a patched client gets
 * nothing. `leaderboard.banned` is a separate, older flag that only hides the
 * player from the board; this page manages the one that locks the app.
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
    $reason = mb_substr(trim((string)($_POST['reason'] ?? '')), 0, 255, 'UTF-8');

    if ($device === '') {
        $notice = 'شناسهٔ دستگاه نامعتبر است';
    } elseif ($action === 'ban') {
        $pdo->prepare(
            "INSERT INTO app_bans (device_id, reason, banned_by) VALUES (?,?,?)
             ON DUPLICATE KEY UPDATE reason = VALUES(reason), is_active = 1,
                                     banned_by = VALUES(banned_by), lifted_at = NULL"
        )->execute([$device, $reason !== '' ? $reason : 'فعالیت غیرمجاز', $_SESSION['admin_user'] ?? 'admin']);
        $notice = 'دستگاه مسدود شد — از اپ قفل می‌شود و دیگر امتیاز ثبت نمی‌کند.';
    } elseif ($action === 'unban') {
        $pdo->prepare("UPDATE app_bans SET is_active = 0, lifted_at = NOW() WHERE device_id = ?")->execute([$device]);
        $notice = 'مسدودی برداشته شد.';
    } elseif ($action === 'unban_all') {
        $n = $pdo->exec("UPDATE app_bans SET is_active = 0, lifted_at = NOW() WHERE is_active = 1");
        $notice = faNum($n) . ' مسدودی برداشته شد.';
    }
}

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

$activeCount = (int) $q("SELECT COUNT(*) c FROM app_bans WHERE is_active = 1")->fetch()['c'];
$totalCount  = (int) $q("SELECT COUNT(*) c FROM app_bans")->fetch()['c'];

// Show a cheat flag next to a ban where we can — the reason a ban exists.
$bans = $q("
    SELECT b.*, l.name, l.level, l.score,
           (SELECT reason FROM cheat_flags f WHERE f.device_id = b.device_id
             ORDER BY f.created_at DESC LIMIT 1) AS flag_reason
      FROM app_bans b
      LEFT JOIN leaderboard l ON l.device_id = b.device_id
     ORDER BY b.is_active DESC, b.banned_at DESC
     LIMIT 100
")->fetchAll();

$flagged = $q("
    SELECT f.device_id, COUNT(*) c, MAX(f.created_at) last_seen,
           SUBSTRING_INDEX(GROUP_CONCAT(f.reason ORDER BY f.created_at DESC), ',', 1) AS reason
      FROM cheat_flags f
     WHERE f.created_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY)
     GROUP BY f.device_id
    HAVING c >= 1
     ORDER BY c DESC, last_seen DESC
     LIMIT 25
")->fetchAll();

renderHead('مسدودی‌ها', 'bans');
renderPageHeader('مسدود کردن کاربر', 'قفل کردن اپ برای یک دستگاه و نمایش پیام به او');
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>

<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
    <?php
    statCard('مسدود فعال', faNum($activeCount), 'fas fa-user-slash', 'rose');
    statCard('کل رکوردها', faNum($totalCount), 'fas fa-list', 'emerald');
    statCard('هشدار ضدتقلب ۳۰ روز', faNum(count($flagged)), 'fas fa-triangle-exclamation', 'amber');
    ?>
</div>

<!-- ban form -->
<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6 mb-8">
    <h3 class="font-black text-emerald-900 mb-1">مسدود کردن یک دستگاه</h3>
    <p class="text-xs text-emerald-500 mb-5 leading-6">
        دستگاه مسدود می‌شود: اپ برایش قفل می‌شود، پیام شما را می‌بیند، و دیگر نمی‌تواند
        امتیاز ثبت کند یا توکن اجرا بگیرد — حتی اگر خودِ برنامه را دستکاری کند.
    </p>
    <form method="post" class="grid grid-cols-1 md:grid-cols-3 gap-4 max-w-4xl">
        <?= csrfField() ?>
        <input type="hidden" name="action" value="ban">
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">شناسهٔ دستگاه</label>
            <input type="text" name="device_id" required dir="ltr"
                placeholder="a1b2c3d4…"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div class="md:col-span-2">
            <label class="block text-sm font-semibold text-emerald-800 mb-2">دلیل (به خود کاربر نشان داده می‌شود)</label>
            <input type="text" name="reason" maxlength="255"
                placeholder="با توجه به فعالیت‌های غیرمجاز اخیر، حساب شما مسدود شده است."
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div class="md:col-span-3">
            <button class="px-6 py-3 bg-rose-500 text-white rounded-xl font-bold hover:bg-rose-600">
                <i class="fas fa-user-slash ml-1"></i>مسدود کردن
            </button>
        </div>
    </form>
</div>

<div class="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-8">
    <!-- ban list -->
    <div class="lg:col-span-2 bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
        <div class="px-6 py-4 border-b border-emerald-100 flex items-center justify-between">
            <h3 class="font-black text-emerald-900">فهرست مسدودی‌ها</h3>
            <?php if ($activeCount > 0): ?>
            <form method="post" onsubmit="return confirm('همهٔ مسدودی‌ها برداشته شود؟')">
                <?= csrfField() ?>
                <input type="hidden" name="action" value="unban_all">
                <button class="px-3.5 py-2 bg-amber-50 text-amber-700 rounded-lg text-xs font-bold hover:bg-amber-100">
                    رفع مسدودی همه
                </button>
            </form>
            <?php endif; ?>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full">
                <?php tableHeader([
                    ['label' => 'دستگاه', 'align' => 'left'],
                    ['label' => 'نام', 'align' => 'right'],
                    ['label' => 'دلیل', 'align' => 'right'],
                    ['label' => 'وضعیت', 'align' => 'center'],
                    ['label' => 'تاریخ', 'align' => 'center'],
                    ['label' => '', 'align' => 'center'],
                ]); ?>
                <tbody class="divide-y divide-emerald-50">
                <?php foreach ($bans as $b): $on = (int)$b['is_active'] === 1; ?>
                    <tr class="<?= $on ? 'bg-rose-50/40' : '' ?>">
                        <td class="px-4 py-3 text-xs font-mono" dir="ltr"><?= e(substr($b['device_id'], 0, 20)) ?></td>
                        <td class="px-4 py-3 text-sm text-emerald-900"><?= e($b['name'] ?: '—') ?>
                            <?php if ($b['level']): ?><p class="text-[11px] text-emerald-400">لول <?= faNum($b['level']) ?></p><?php endif; ?>
                        </td>
                        <td class="px-4 py-3 text-xs text-emerald-700 max-w-xs"><?= e($b['reason']) ?></td>
                        <td class="px-4 py-3 text-center">
                            <span class="badge <?= $on ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700' ?>">
                                <?= $on ? 'مسدود' : 'آزاد' ?>
                            </span>
                        </td>
                        <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e($b['banned_at']) ?></td>
                        <td class="px-4 py-3 text-center">
                            <form method="post" class="inline">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="<?= $on ? 'unban' : 'ban' ?>">
                                <input type="hidden" name="device_id" value="<?= e($b['device_id']) ?>">
                                <input type="hidden" name="reason" value="<?= e($b['reason']) ?>">
                                <button class="px-2.5 py-1.5 rounded-lg text-xs font-bold <?= $on ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200' : 'bg-rose-100 text-rose-700 hover:bg-rose-200' ?>">
                                    <?= $on ? 'رفع مسدودی' : 'مسدود کن' ?>
                                </button>
                            </form>
                        </td>
                    </tr>
                <?php endforeach; ?>
                <?php if (!$bans): emptyRow(6, 'هنوز کسی مسدود نشده'); endif; ?>
                </tbody>
            </table>
        </div>
    </div>

    <!-- cheat flags as ban candidates -->
    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
        <div class="px-6 py-4 border-b border-emerald-100">
            <h3 class="font-black text-emerald-900">مظنون‌ها (۳۰ روز اخیر)</h3>
            <p class="text-xs text-emerald-500 mt-1">از روی لاگ ضدتقلب — یک کلیک تا مسدود شدن</p>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full">
                <?php tableHeader([
                    ['label' => 'دستگاه', 'align' => 'left'],
                    ['label' => 'دلیل', 'align' => 'center'],
                    ['label' => 'تعداد', 'align' => 'center'],
                ]); ?>
                <tbody class="divide-y divide-emerald-50">
                <?php foreach ($flagged as $f): ?>
                    <tr>
                        <td class="px-3 py-3 text-xs font-mono" dir="ltr"><?= e(substr($f['device_id'], 0, 18)) ?></td>
                        <td class="px-3 py-3 text-center">
                            <span class="badge bg-amber-100 text-amber-700 text-[10px]"><?= e($f['reason']) ?></span>
                        </td>
                        <td class="px-3 py-3 text-center">
                            <form method="post" class="inline">
                                <?= csrfField() ?>
                                <input type="hidden" name="action" value="ban">
                                <input type="hidden" name="device_id" value="<?= e($f['device_id']) ?>">
                                <input type="hidden" name="reason" value="با توجه به فعالیت‌های غیرمجاز اخیر، حساب شما مسدود شده است.">
                                <button class="px-2 py-1.5 bg-rose-500 text-white rounded-lg text-[10px] font-bold hover:bg-rose-600">
                                    مسدود
                                </button>
                            </form>
                            <p class="text-[10px] text-emerald-400 mt-1"><?= faNum($f['c']) ?> بار</p>
                        </td>
                    </tr>
                <?php endforeach; ?>
                <?php if (!$flagged): emptyRow(3, 'هشداری ثبت نشده'); endif; ?>
                </tbody>
            </table>
        </div>
    </div>
</div>
<?php renderFoot(); ?>
