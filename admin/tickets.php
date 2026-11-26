<?php
/**
 * Support tickets inbox.
 *
 * Writes straight to the database rather than going through the API: the
 * panel and the API are the same server and the same database, so an HTTP
 * round trip would only add a way to fail.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();
$notice = null;
$error = null;

$statusFilter = (string)($_GET['status'] ?? 'open');
if (!in_array($statusFilter, ['open', 'answered', 'closed', 'all'], true)) $statusFilter = 'open';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action   = (string)($_POST['action'] ?? '');
    $ticketId = (int)($_POST['ticket_id'] ?? 0);
    $body     = mb_substr(trim((string)($_POST['body'] ?? '')), 0, 2000, 'UTF-8');

    if ($action === 'reply' && $ticketId > 0 && $body !== '') {
        $pdo->beginTransaction();
        try {
            $pdo->prepare("INSERT INTO ticket_messages (ticket_id, from_admin, body) VALUES (?,1,?)")
                ->execute([$ticketId, $body]);
            $pdo->prepare("UPDATE tickets SET status = 'answered', last_admin_reply_at = NOW() WHERE id = ?")
                ->execute([$ticketId]);
            $pdo->commit();
            $notice = 'پاسخ ارسال شد — بازیکن آن را در اپ می‌بیند.';
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            $error = 'ارسال ناموفق بود';
        }
    } elseif ($action === 'close' && $ticketId > 0) {
        $pdo->prepare("UPDATE tickets SET status = 'closed' WHERE id = ?")->execute([$ticketId]);
        $notice = 'تیکت بسته شد.';
    } elseif ($action === 'reopen' && $ticketId > 0) {
        $pdo->prepare("UPDATE tickets SET status = 'open' WHERE id = ?")->execute([$ticketId]);
        $notice = 'تیکت دوباره باز شد.';
    } elseif ($action === 'delete' && $ticketId > 0) {
        $pdo->prepare("DELETE FROM tickets WHERE id = ?")->execute([$ticketId]);
        $notice = 'تیکت حذف شد.';
    } elseif ($action === 'ban' && $ticketId > 0) {
        // Banning straight from the ticket is the flow you actually want: a
        // player reports a cheater, you read the thread, you act without
        // leaving the page.
        $device = bb_admin_normalize_device((string)($_POST['device_id'] ?? ''));
        $reason = mb_substr(trim((string)($_POST['reason'] ?? 'فعالیت غیرمجاز')), 0, 255, 'UTF-8');
        if ($device !== '') {
            $pdo->prepare(
                "INSERT INTO app_bans (device_id, reason, banned_by) VALUES (?,?,?)
                 ON DUPLICATE KEY UPDATE reason = VALUES(reason), is_active = 1,
                                         banned_by = VALUES(banned_by), lifted_at = NULL"
            )->execute([$device, $reason, $_SESSION['admin_user'] ?? 'admin']);
            $notice = 'دستگاه مسدود شد و از اپ قفل می‌شود.';
        } else {
            $error = 'شناسهٔ دستگاه نامعتبر است';
        }
    }
}

$q = function (string $sql, array $p = []) use ($pdo) {
    $st = $pdo->prepare($sql);
    $st->execute($p);
    return $st;
};

$counts = [
    'open'     => (int) $q("SELECT COUNT(*) c FROM tickets WHERE status='open'")->fetch()['c'],
    'answered' => (int) $q("SELECT COUNT(*) c FROM tickets WHERE status='answered'")->fetch()['c'],
    'closed'   => (int) $q("SELECT COUNT(*) c FROM tickets WHERE status='closed'")->fetch()['c'],
    'all'      => (int) $q("SELECT COUNT(*) c FROM tickets")->fetch()['c'],
];

$where = $statusFilter === 'all' ? '' : " WHERE t.status = ?";
$args  = $statusFilter === 'all' ? [] : [$statusFilter];

$tickets = $q(
    "SELECT t.*,
            (SELECT body FROM ticket_messages m WHERE m.ticket_id = t.id ORDER BY m.id DESC LIMIT 1) AS last_body
       FROM tickets t $where
      ORDER BY FIELD(t.status,'open','answered','closed'), t.updated_at DESC
      LIMIT 100",
    $args
)->fetchAll();

$selected = null;
$messages = [];
if (isset($_GET['t'])) {
    $tid = (int) $_GET['t'];
    $st = $q("SELECT * FROM tickets WHERE id = ?", [$tid]);
    $selected = $st->fetch();
    if ($selected) {
        $st = $q("SELECT id, from_admin, body, created_at FROM ticket_messages WHERE ticket_id = ? ORDER BY id ASC", [$tid]);
        $messages = $st->fetchAll();
    }
}

$catLabels = [
    'bug' => 'باگ', 'payment' => 'پرداخت', 'account' => 'حساب',
    'cheat' => 'تقلب', 'other' => 'سایر',
];
$statusLabels = ['open' => 'باز', 'answered' => 'پاسخ داده شده', 'closed' => 'بسته شده'];

renderHead('تیکت‌ها', 'tickets');
renderPageHeader('تیکت‌های پشتیبانی', 'پیام‌های بازیکنان و پاسخ شما');
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>
<?php if ($error): ?>
<div class="mb-6 bg-rose-50 border border-rose-200 text-rose-700 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-exclamation ml-2"></i><?= e($error) ?>
</div>
<?php endif; ?>

<div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
    <!-- list -->
    <div class="lg:col-span-1">
        <div class="flex flex-wrap gap-2 mb-4">
            <?php foreach ($statusLabels + ['all' => 'همه'] as $k => $label): ?>
                <a href="?status=<?= e($k) ?>"
                   class="px-3.5 py-2 rounded-lg text-xs font-bold transition-all
                          <?= $statusFilter === $k ? 'bg-emerald-500 text-white' : 'bg-white/90 text-emerald-700 border border-emerald-100 hover:bg-emerald-50' ?>">
                    <?= e($label) ?> <span class="opacity-70"><?= faNum($counts[$k]) ?></span>
                </a>
            <?php endforeach; ?>
        </div>

        <div class="space-y-2">
        <?php foreach ($tickets as $t): ?>
            <a href="?status=<?= e($statusFilter) ?>&t=<?= (int)$t['id'] ?>"
               class="block bg-white/90 rounded-xl border p-4 transition-all hover:border-emerald-300
                      <?= (isset($_GET['t']) && (int)$_GET['t'] === (int)$t['id']) ? 'border-emerald-400 ring-2 ring-emerald-200' : 'border-emerald-100' ?>">
                <div class="flex items-start justify-between gap-2 mb-1">
                    <p class="text-sm font-bold text-emerald-900 truncate"><?= e($t['subject']) ?></p>
                    <span class="badge shrink-0 text-[10px] <?= $t['status'] === 'open' ? 'bg-amber-100 text-amber-700' : ($t['status'] === 'answered' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500') ?>">
                        <?= e($statusLabels[$t['status']] ?? $t['status']) ?>
                    </span>
                </div>
                <p class="text-xs text-emerald-500 truncate mb-1"><?= e($catLabels[$t['category']] ?? $t['category']) ?> • <?= e($t['player_name'] ?: 'بدون نام') ?></p>
                <p class="text-xs text-emerald-400 line-clamp-2"><?= e($t['last_body']) ?></p>
                <p class="text-[10px] text-emerald-300 mt-1"><?= e($t['updated_at']) ?></p>
            </a>
        <?php endforeach; ?>
        <?php if (!$tickets): ?>
            <p class="text-center text-emerald-400 py-10 bg-white/90 rounded-xl border border-emerald-100">تیکتی در این وضعیت نیست</p>
        <?php endif; ?>
        </div>
    </div>

    <!-- thread -->
    <div class="lg:col-span-2">
    <?php if (!$selected): ?>
        <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-16 text-center">
            <i class="fas fa-life-ring text-4xl text-emerald-200"></i>
            <p class="text-emerald-500 mt-4">یک تیکت را انتخاب کن تا گفتگو را ببینی</p>
        </div>
    <?php else: ?>
        <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
            <div class="px-6 py-4 border-b border-emerald-100">
                <div class="flex items-start justify-between gap-3 flex-wrap">
                    <div class="min-w-0">
                        <h3 class="font-black text-emerald-900"><?= e($selected['subject']) ?></h3>
                        <p class="text-xs text-emerald-500 mt-1">
                            <?= e($catLabels[$selected['category']] ?? $selected['category']) ?>
                            • بازیکن: <?= e($selected['player_name'] ?: '—') ?>
                            • <?= e($selected['created_at']) ?>
                        </p>
                        <p class="text-[11px] font-mono text-emerald-400 mt-1" dir="ltr"><?= e($selected['device_id']) ?></p>
                    </div>
                    <div class="flex gap-2">
                        <form method="post">
                            <?= csrfField() ?>
                            <input type="hidden" name="action" value="<?= $selected['status'] === 'closed' ? 'reopen' : 'close' ?>">
                            <input type="hidden" name="ticket_id" value="<?= (int)$selected['id'] ?>">
                            <button class="px-3 py-1.5 bg-slate-100 text-slate-600 rounded-lg text-xs font-bold hover:bg-slate-200">
                                <?= $selected['status'] === 'closed' ? 'باز کردن' : 'بستن' ?>
                            </button>
                        </form>
                        <form method="post" onsubmit="return confirm('حذف شود؟')">
                            <?= csrfField() ?>
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="ticket_id" value="<?= (int)$selected['id'] ?>">
                            <button class="px-3 py-1.5 bg-rose-100 text-rose-700 rounded-lg text-xs font-bold hover:bg-rose-200">
                                <i class="fas fa-trash"></i>
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <div class="p-6 space-y-3 max-h-[28rem] overflow-y-auto">
                <?php foreach ($messages as $m): ?>
                    <div class="flex <?= (int)$m['from_admin'] === 1 ? 'justify-start' : 'justify-end' ?>">
                        <div class="max-w-[80%] rounded-2xl px-4 py-3 <?= (int)$m['from_admin'] === 1 ? 'bg-emerald-50 rounded-tl-sm' : 'bg-slate-100 rounded-tl-sm' ?>">
                            <p class="text-[10px] font-bold <?= (int)$m['from_admin'] === 1 ? 'text-emerald-600' : 'text-slate-500' ?> mb-1">
                                <?= (int)$m['from_admin'] === 1 ? 'پشتیبانی' : ($selected['player_name'] ?: 'بازیکن') ?>
                                • <?= e($m['created_at']) ?>
                            </p>
                            <p class="text-sm text-emerald-900 whitespace-pre-wrap leading-6"><?= e($m['body']) ?></p>
                        </div>
                    </div>
                <?php endforeach; ?>
                <?php if (!$messages): ?>
                    <p class="text-center text-emerald-400 py-6">پیامی نیست</p>
                <?php endif; ?>
            </div>

            <?php if ($selected['status'] !== 'closed'): ?>
            <div class="px-6 py-4 border-t border-emerald-100 bg-emerald-50/30">
                <form method="post" class="space-y-3">
                    <?= csrfField() ?>
                    <input type="hidden" name="action" value="reply">
                    <input type="hidden" name="ticket_id" value="<?= (int)$selected['id'] ?>">
                    <textarea name="body" rows="4" required maxlength="2000"
                              placeholder="پاسخ شما به بازیکن…"
                              class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500"></textarea>
                    <div class="flex flex-wrap gap-2">
                        <button class="px-5 py-2.5 bg-emerald-500 text-white rounded-xl font-bold text-sm hover:bg-emerald-600">
                            <i class="fas fa-paper-plane ml-1"></i>ارسال پاسخ
                        </button>
                    </div>
                </form>

                <form method="post" class="mt-4 pt-4 border-t border-emerald-100" onsubmit="return confirm('این دستگاه مسدود شود؟')">
                    <?= csrfField() ?>
                    <input type="hidden" name="action" value="ban">
                    <input type="hidden" name="device_id" value="<?= e($selected['device_id']) ?>">
                    <input type="hidden" name="reason" value="فعالیت غیرمجاز — گزارش تیکت #<?= (int)$selected['id'] ?>">
                    <button class="px-4 py-2 bg-rose-500 text-white rounded-xl font-bold text-xs hover:bg-rose-600">
                        <i class="fas fa-user-slash ml-1"></i>مسدود کردن این بازیکن
                    </button>
                </form>
            </div>
            <?php endif; ?>
        </div>
    <?php endif; ?>
    </div>
</div>
<?php renderFoot(); ?>
