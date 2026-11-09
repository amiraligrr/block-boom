<?php
/**
 * Announcement composer.
 *
 * The notifications table and its API existed but nothing in the panel could
 * reach them — and api/notifications.php had no authentication at all, so
 * anyone could POST a message to every player. That endpoint now requires
 * ADMIN_API_KEY; this page sends it.
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

    if ($action === 'delete') {
        $id = (int)($_POST['id'] ?? 0);
        $pdo->prepare("DELETE FROM notifications WHERE id = ?")->execute([$id]);
        $notice = 'اعلان حذف شد';
    } elseif ($action === 'send') {
        $title = mb_substr(trim((string)($_POST['title'] ?? '')), 0, 128);
        $message = mb_substr(trim((string)($_POST['message'] ?? '')), 0, 2000);
        $targetType = ($_POST['target_type'] ?? 'all') === 'specific' ? 'specific' : 'all';
        $targetId = bb_admin_normalize_device((string)($_POST['target_id'] ?? ''));

        if ($title === '' || $message === '') {
            $error = 'عنوان و متن الزامی است';
        } elseif ($targetType === 'specific' && $targetId === '') {
            $error = 'برای ارسال به یک بازیکن، شناسهٔ دستگاه لازم است';
        } else {
            // The API is the single writer for notifications so the dedupe
            // check in notifications.php always applies.
            //
            // Same reason as the guard in settings.php: config.php is never
            // deployed, so a constant added to the repo copy is absent on the
            // server until someone edits it there. Reading it unguarded made
            // this page a blank 500 too — on the one screen whose entire job is
            // sending an operator's message to players.
            $key = getenv('BB_ADMIN_API_KEY')
                ?: (defined('ADMIN_API_KEY') ? (string)ADMIN_API_KEY : '');
            $body = json_encode([
                'title' => $title,
                'message' => $message,
                'target_type' => $targetType,
                'target_id' => $targetType === 'specific' ? $targetId : '',
            ], JSON_UNESCAPED_UNICODE);

            $ctx = stream_context_create(['http' => [
                'method' => 'POST',
                'header' => "Content-Type: application/json\r\nX-Admin-Key: $key\r\n",
                'content' => $body,
                'timeout' => 6,
                'ignore_errors' => true,
            ]]);
            $res = @file_get_contents(API_BASE . '/notifications.php', false, $ctx);
            $json = $res !== false ? json_decode($res, true) : null;

            if (is_array($json) && !empty($json['ok'])) {
                $notice = 'اعلان ارسال شد';
            } elseif (is_array($json) && isset($json['error'])) {
                $error = 'سرور API گفت: ' . $json['error']
                    . ' — اگر «not configured» بود، کلید را در BB_ADMIN_API_KEY روی سرور API بگذار.';
            } else {
                $error = 'ارتباط با سرور API برقرار نشد';
            }
        }
    }
}

// Reader counts come from notification_reads, not from notifications.is_read.
//
// is_read is a single flag on the row, so for a broadcast the FIRST player to
// open the app would set it and the panel would then report the announcement as
// read by everyone — while in fact nobody else had ever seen it. Per-device
// receipts make the number honest: "12 بازیکن دیده‌اند" is a fact, whereas
// "خوانده شده" derived from one global bit was not.
$rows = $pdo->query("
    SELECT n.id, n.title, n.message, n.target_type, n.target_id, n.is_read, n.created_at,
           (SELECT COUNT(*) FROM notification_reads r WHERE r.notification_id = n.id) AS readers
      FROM notifications n ORDER BY n.created_at DESC LIMIT 50
")->fetchAll();

// "Not yet delivered" means: nobody acknowledged it at all. For a broadcast
// that is the honest signal; the previous COUNT(is_read = 0) also counted
// broadcasts that every player had already seen.
$unread = (int) $pdo->query(
    "SELECT COUNT(*) c FROM notifications n
      WHERE n.is_read = 0
        AND NOT EXISTS (SELECT 1 FROM notification_reads r WHERE r.notification_id = n.id)"
)->fetch()['c'];

renderHead('اعلان‌ها', 'notifications');
renderPageHeader('اعلان‌ها', 'ارسال پیام به همهٔ بازیکنان یا یک بازیکن خاص');
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

<div class="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-8">
    <div class="lg:col-span-2 bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-1">ارسال اعلان جدید</h3>
        <p class="text-xs text-emerald-500 mb-5">
            اعلان‌های عمومی برای همه نمایش داده می‌شوند. اگر بازیکنی اعلان را ببیند، در
            <code>fetch_notifications.php</code> خوانده‌شده علامت می‌خورد.
        </p>
        <form method="post" class="space-y-4">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="send">

            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">مخاطب</label>
                <select name="target_type"
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
                    <option value="all">همهٔ بازیکنان</option>
                    <option value="specific">یک بازیکن خاص</option>
                </select>
            </div>

            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">شناسهٔ دستگاه (فقط برای بازیکن خاص)</label>
                <input type="text" name="target_id" placeholder="مثلاً a1b2c3…"
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500" dir="ltr">
            </div>

            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">عنوان (حداکثر ۱۲۸ کاراکتر)</label>
                <input type="text" name="title" maxlength="128" required
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
            </div>

            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">متن پیام</label>
                <textarea name="message" rows="4" maxlength="2000" required
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500"></textarea>
            </div>

            <button class="px-6 py-3 bg-emerald-500 text-white rounded-xl font-bold hover:bg-emerald-600 transition-all">
                <i class="fas fa-paper-plane ml-1"></i>ارسال
            </button>
        </form>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6 h-fit">
        <h3 class="font-black text-emerald-900 mb-4">وضعیت</h3>
        <dl class="space-y-3 text-sm">
            <div class="flex justify-between"><dt class="text-emerald-600">اعلان‌های خوانده‌نشده</dt>
                <dd class="font-black <?= $unread > 0 ? 'text-amber-600' : '' ?>"><?= faNum($unread) ?></dd></div>
            <div class="flex justify-between"><dt class="text-emerald-600">کل اعلان‌ها</dt>
                <dd class="font-black"><?= faNum(count($rows)) ?></dd></div>
        </dl>
        <p class="text-[11px] text-emerald-400 mt-4 leading-5">
            اگر ارسال با خطای <code>not configured</code> رد شد، یعنی
            <code>ADMIN_API_KEY</code> روی سرور API تنظیم نشده. کلید را از صفحهٔ تنظیمات بساز.
        </p>
    </div>
</div>

<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
    <div class="px-6 py-4 border-b border-emerald-100">
        <h3 class="font-black text-emerald-900">۵۰ اعلان آخر</h3>
    </div>
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'عنوان', 'align' => 'right'],
                ['label' => 'مخاطب', 'align' => 'center'],
                ['label' => 'وضعیت', 'align' => 'center'],
                ['label' => 'تاریخ', 'align' => 'center'],
                ['label' => '', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($rows as $r): ?>
                <tr class="hover:bg-emerald-50/40">
                    <td class="px-4 py-3">
                        <p class="text-sm font-bold text-emerald-900"><?= e($r['title']) ?></p>
                        <p class="text-xs text-emerald-500 mt-0.5 line-clamp-2"><?= e($r['message']) ?></p>
                    </td>
                    <td class="px-4 py-3 text-center">
                        <span class="badge <?= $r['target_type'] === 'all' ? 'bg-emerald-100 text-emerald-700' : 'bg-sky-100 text-sky-700' ?>">
                            <?= $r['target_type'] === 'all' ? 'همه' : e(substr((string)$r['target_id'], 0, 10)) ?>
                        </span>
                    </td>
                    <td class="px-4 py-3 text-center">
                        <?php
                        $readers = (int)($r['readers'] ?? 0);
                        $targeted = $r['target_type'] !== 'all';
                        ?>
                        <?php if ($readers > 0): ?>
                            <span class="badge bg-slate-100 text-slate-600">
                                <?= faNum($readers) ?> بازیکن دیده‌اند
                            </span>
                            <?php if ($targeted && (int)$r['is_read'] === 1): ?>
                                <p class="text-[10px] text-emerald-500 mt-1">مخاطب دیده است</p>
                            <?php endif; ?>
                        <?php else: ?>
                            <span class="badge bg-amber-100 text-amber-700">هنوز کسی ندیده</span>
                        <?php endif; ?>
                    </td>
                    <td class="px-4 py-3 text-center text-xs text-emerald-500"><?= e($r['created_at']) ?></td>
                    <td class="px-4 py-3 text-center">
                        <form method="post" onsubmit="return confirm('این اعلان حذف شود؟')">
                            <?= csrfField() ?>
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="id" value="<?= (int)$r['id'] ?>">
                            <button class="px-2.5 py-1.5 bg-rose-100 text-rose-700 rounded-lg text-xs font-bold hover:bg-rose-200">
                                <i class="fas fa-trash"></i>
                            </button>
                        </form>
                    </td>
                </tr>
            <?php endforeach; ?>
            <?php if (!$rows): emptyRow(5, 'اعلانی ثبت نشده'); endif; ?>
            </tbody>
        </table>
    </div>
</div>
<?php renderFoot(); ?>
