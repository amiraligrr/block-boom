<?php
/**
 * News manager.
 *
 * The app's news screen reads api/news.php; this page is the only writer.
 * Drafts stay invisible until "انتشار" is pressed. Images are URLs for now
 * (the app has no image loader in this slice); text is always available.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

$pdo = getPDO();
$notice = null;
$error = null;
$editing = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $action = (string)($_POST['action'] ?? '');
    $id = (int)($_POST['id'] ?? 0);

    if ($action === 'save') {
        $title = mb_substr(trim((string)($_POST['title'] ?? '')), 0, 160, 'UTF-8');
        $summary = mb_substr(trim((string)($_POST['summary'] ?? '')), 0, 320, 'UTF-8');
        $body = trim((string)($_POST['body'] ?? ''));
        $image = mb_substr(trim((string)($_POST['image_url'] ?? '')), 0, 255, 'UTF-8');
        $publish = ($_POST['publish'] ?? '0') === '1';

        if ($title === '' || $body === '') {
            $error = 'عنوان و متن خبر الزامی است';
        } elseif ($id > 0) {
            $pdo->prepare(
                "UPDATE news
                    SET title = ?, summary = ?, body = ?, image_url = ?,
                        is_published = ?,
                        published_at = CASE
                            WHEN ? = 1 AND published_at IS NULL THEN NOW()
                            WHEN ? = 0 THEN NULL
                            ELSE published_at
                        END
                  WHERE id = ?"
            )->execute([$title, $summary, $body, $image ?: null, $publish ? 1 : 0,
                        $publish ? 1 : 0, $publish ? 1 : 0, $id]);
            $notice = 'خبر ویرایش شد';
        } else {
            $pdo->prepare(
                "INSERT INTO news (title, summary, body, image_url, is_published, published_at)
                 VALUES (?,?,?,?,?,?)"
            )->execute([$title, $summary, $body, $image ?: null, $publish ? 1 : 0,
                        $publish ? date('Y-m-d H:i:s') : null]);
            $notice = 'خبر ساخته شد';
        }
    } elseif ($action === 'delete' && $id > 0) {
        $pdo->prepare("DELETE FROM news WHERE id = ?")->execute([$id]);
        $notice = 'خبر حذف شد';
    } elseif ($action === 'toggle' && $id > 0) {
        $stmt = $pdo->prepare("SELECT is_published FROM news WHERE id = ?");
        $stmt->execute([$id]);
        $cur = $stmt->fetchColumn();
        if ($cur !== false) {
            $next = ((int)$cur) === 1 ? 0 : 1;
            $pdo->prepare(
                "UPDATE news SET is_published = ?,
                    published_at = CASE WHEN ? = 1 THEN COALESCE(published_at, NOW()) ELSE NULL END
                  WHERE id = ?"
            )->execute([$next, $next, $id]);
            $notice = $next ? 'خبر منتشر شد' : 'خبر از انتشار خارج شد';
        }
    }
}

$editId = (int)($_GET['edit'] ?? 0);
if ($editId > 0) {
    $stmt = $pdo->prepare("SELECT * FROM news WHERE id = ?");
    $stmt->execute([$editId]);
    $editing = $stmt->fetch();
}
$rows = $pdo->query("SELECT * FROM news ORDER BY created_at DESC LIMIT 100")->fetchAll();

renderHead('اخبار', 'news');
?>
<?php renderPageHeader('اخبار بازی', 'ساخت، ویرایش و انتشار خبرهای داخل بازی'); ?>

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
        <h3 class="font-black text-emerald-900 mb-4"><?= $editing ? 'ویرایش خبر' : 'خبر جدید' ?></h3>
        <form method="post" class="space-y-4">
            <?= csrfField() ?>
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="id" value="<?= $editing ? (int)$editing['id'] : 0 ?>">
            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">عنوان</label>
                <input type="text" name="title" required maxlength="160"
                    value="<?= e((string)($editing['title'] ?? '')) ?>"
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
            </div>
            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">خلاصه</label>
                <input type="text" name="summary" maxlength="320"
                    value="<?= e((string)($editing['summary'] ?? '')) ?>"
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
            </div>
            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">متن کامل</label>
                <textarea name="body" rows="8" required
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500"><?= e((string)($editing['body'] ?? '')) ?></textarea>
            </div>
            <div>
                <label class="block text-sm font-semibold text-emerald-800 mb-2">آدرس تصویر (اختیاری)</label>
                <input type="url" name="image_url" dir="ltr" maxlength="255"
                    value="<?= e((string)($editing['image_url'] ?? '')) ?>"
                    placeholder="https://…"
                    class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
            </div>
            <label class="flex items-center gap-2 text-sm font-semibold text-emerald-800">
                <input type="checkbox" name="publish" value="1" <?= (int)($editing['is_published'] ?? 0) === 1 ? 'checked' : '' ?>>
                انتشار همین حالا
            </label>
            <button class="px-5 py-3 bg-emerald-600 text-white rounded-xl font-bold hover:bg-emerald-700">
                <i class="fas fa-floppy-disk ml-2"></i>ذخیره
            </button>
            <?php if ($editing): ?>
                <a href="news.php" class="inline-block px-4 py-3 text-emerald-600 font-bold">انصراف</a>
            <?php endif; ?>
        </form>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
        <h3 class="font-black text-emerald-900 mb-2">راهنما</h3>
        <p class="text-xs leading-6 text-emerald-600">
            خبرهای «پیش‌نویس» در اپ دیده نمی‌شوند. با انتشار، در کارت «اخبار بازی» صفحهٔ اصلی
            و صفحهٔ ورق‌زدن اخبار نمایش داده می‌شوند. متن‌ها کامل و راست‌به‌چپ ذخیره می‌شوند.
        </p>
    </div>
</div>

<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
    <table class="w-full text-sm">
        <thead class="bg-emerald-50 text-emerald-700">
        <tr>
            <th class="px-4 py-3 text-right">عنوان</th>
            <th class="px-4 py-3 text-center">وضعیت</th>
            <th class="px-4 py-3 text-center">تاریخ</th>
            <th class="px-4 py-3 text-center">عملیات</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-emerald-50">
        <?php foreach ($rows as $r): ?>
            <tr class="hover:bg-emerald-50/40">
                <td class="px-4 py-3 font-bold text-emerald-900"><?= e($r['title']) ?></td>
                <td class="px-4 py-3 text-center">
                    <?php if ((int)$r['is_published'] === 1): ?>
                        <span class="badge bg-emerald-100 text-emerald-700">منتشرشده</span>
                    <?php else: ?>
                        <span class="badge bg-amber-100 text-amber-700">پیش‌نویس</span>
                    <?php endif; ?>
                </td>
                <td class="px-4 py-3 text-center text-xs text-emerald-500">
                    <?= e((string)($r['published_at'] ?? $r['created_at'])) ?>
                </td>
                <td class="px-4 py-3 text-center">
                    <div class="flex items-center justify-center gap-1">
                        <a href="?edit=<?= (int)$r['id'] ?>"
                           class="px-2.5 py-1.5 bg-sky-100 text-sky-700 rounded-lg text-xs font-bold">ویرایش</a>
                        <form method="post">
                            <?= csrfField() ?>
                            <input type="hidden" name="action" value="toggle">
                            <input type="hidden" name="id" value="<?= (int)$r['id'] ?>">
                            <button class="px-2.5 py-1.5 bg-emerald-100 text-emerald-700 rounded-lg text-xs font-bold">
                                <?= (int)$r['is_published'] === 1 ? 'لغو انتشار' : 'انتشار' ?>
                            </button>
                        </form>
                        <form method="post" onsubmit="return confirm('این خبر حذف شود؟')">
                            <?= csrfField() ?>
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="id" value="<?= (int)$r['id'] ?>">
                            <button class="px-2.5 py-1.5 bg-rose-100 text-rose-700 rounded-lg text-xs font-bold">
                                <i class="fas fa-trash"></i>
                            </button>
                        </form>
                    </div>
                </td>
            </tr>
        <?php endforeach; ?>
        <?php if (!$rows): ?>
            <tr><td colspan="4" class="px-4 py-10 text-center text-emerald-400">خبری ثبت نشده</td></tr>
        <?php endif; ?>
        </tbody>
    </table>
</div>
<?php renderFoot(); ?>
