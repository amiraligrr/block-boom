<?php
/**
 * Version manager.
 *
 * Lets you switch off a build and have the app refuse to play, with a message
 * you type. The client cannot override this: app_config.php decides, and the
 * ban/leaderboard paths re-check independently.
 *
 * A version is only blocked if at least one row has is_enabled = 1. If every
 * row is disabled, nothing blocks — turning everything off by accident should
 * not brick the game for every player.
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
    $code   = (int)($_POST['version_code'] ?? 0);

    if ($action === 'toggle' && $code > 0) {
        $pdo->prepare("UPDATE app_versions SET is_enabled = 1 - is_enabled WHERE version_code = ?")->execute([$code]);
        $notice = 'وضعیت نسخه تغییر کرد.';
    } elseif ($action === 'set_latest' && $code > 0) {
        $st = $pdo->prepare("SELECT is_published FROM app_versions WHERE version_code = ?");
        $st->execute([$code]);
        $published = (int) ($st->fetchColumn() ?: 0) === 1;
        if (!$published) {
            $error = 'این نسخه پیش‌نویس است. اول منتشرش کن، بعد می‌تواند «آخرین نسخه» شود.';
        } else {
            $pdo->beginTransaction();
            try {
                $pdo->exec("UPDATE app_versions SET is_latest = 0");
                $pdo->prepare("UPDATE app_versions SET is_latest = 1 WHERE version_code = ?")->execute([$code]);
                $pdo->commit();
                $notice = 'آخرین نسخه تعیین شد.';
            } catch (Throwable $e) {
                if ($pdo->inTransaction()) $pdo->rollBack();
                $error = 'تعیین آخرین نسخه ناموفق بود.';
            }
        }
    } elseif ($action === 'save') {
        $name    = mb_substr(trim((string)($_POST['version_name'] ?? '')), 0, 32, 'UTF-8');
        $message = trim((string)($_POST['force_message'] ?? ''));
        $url     = mb_substr(trim((string)($_POST['store_url'] ?? '')), 0, 255, 'UTF-8');
        $note    = mb_substr(trim((string)($_POST['note'] ?? '')), 0, 255, 'UTF-8');
        if ($code > 0) {
            // New rows are always drafts. Existing rows keep whatever
            // publication state they already had — editing a published
            // version must not silently unpublish it, and editing a draft
            // must never publish it as a side effect of pressing Save.
            $pdo->prepare(
                "INSERT INTO app_versions
                    (version_code, version_name, force_message, store_url, note, is_published)
                 VALUES (?,?,?,?,?,0)
                 ON DUPLICATE KEY UPDATE version_name = VALUES(version_name),
                                         force_message = VALUES(force_message),
                                         store_url = VALUES(store_url),
                                         note = VALUES(note)"
            )->execute([$code, $name, $message !== '' ? $message : null, $url, $note]);
            $notice = 'ذخیره شد. نسخهٔ جدید به‌صورت پیش‌نویس است تا وقتی خودت منتشرش کنی.';
        }
    } elseif ($action === 'publish' && $code > 0) {
        $notify = ($_POST['notify'] ?? '1') === '1';
        $makeLatest = ($_POST['make_latest'] ?? '1') === '1';
        $st = $pdo->prepare("SELECT version_code, version_name, note FROM app_versions WHERE version_code = ?");
        $st->execute([$code]);
        $row = $st->fetch();
        if (!$row) {
            $error = 'نسخه پیدا نشد.';
        } else {
            $pdo->beginTransaction();
            try {
                $pdo->prepare(
                    "UPDATE app_versions
                        SET is_published = 1, published_at = NOW(), is_enabled = 1
                      WHERE version_code = ?"
                )->execute([$code]);
                if ($makeLatest) {
                    $pdo->exec("UPDATE app_versions SET is_latest = 0");
                    $pdo->prepare("UPDATE app_versions SET is_latest = 1 WHERE version_code = ?")->execute([$code]);
                }
                if ($notify) {
                    // Direct insert is intentional here: this is a one-shot
                    // release notice tied to a publish action, not a
                    // broadcast composer. The API's dedupe path would make a
                    // legitimate re-publish look like a duplicate.
                    $label = trim((string) $row['version_name']) !== ''
                        ? (string) $row['version_name']
                        : (string) $code;
                    $pdo->prepare(
                        "INSERT INTO notifications (title, message, target_type)
                         VALUES (?,?, 'all')"
                    )->execute([
                        'نسخهٔ جدید Block Boom',
                        'نسخهٔ ' . $label . ' منتشر شد. بازی را به‌روزرسانی کن تا بهترین تجربه را داشته باشی.',
                    ]);
                }
                $pdo->commit();
                $notice = 'نسخه منتشر شد' . ($notify ? ' و اعلان برای بازیکنان ثبت شد.' : '.');
            } catch (Throwable $e) {
                if ($pdo->inTransaction()) $pdo->rollBack();
                $error = 'انتشار ناموفق بود: ' . $e->getMessage();
            }
        }
    } elseif ($action === 'unpublish' && $code > 0) {
        $pdo->prepare(
            "UPDATE app_versions SET is_published = 0, is_latest = 0 WHERE version_code = ?"
        )->execute([$code]);
        $notice = 'نسخه به پیش‌نویس برگشت. بازیکنان دیگر آن را به‌عنوان نسخهٔ جدید نمی‌بینند.';
    } elseif ($action === 'delete' && $code > 0) {
        $pdo->prepare("DELETE FROM app_versions WHERE version_code = ?")->execute([$code]);
        $notice = 'نسخه حذف شد.';
    }
}

$rows = $pdo->query("SELECT * FROM app_versions ORDER BY version_code DESC")->fetchAll();

$enabledCount = 0;
$publishedCount = 0;
foreach ($rows as $r) {
    if ((int) $r['is_enabled'] === 1) $enabledCount++;
    if ((int) ($r['is_published'] ?? 1) === 1) $publishedCount++;
}

$editCode = (int)($_GET['edit'] ?? 0);
$editing = null;
foreach ($rows as $r) if ((int) $r['version_code'] === $editCode) $editing = $r;

renderHead('نسخه‌ها', 'versions');
renderPageHeader('مدیریت نسخه‌ها', 'غیرفعال کردن نسخه‌های قدیمی و نوشتن پیام به کاربران');
?>

<?php if ($notice): ?>
<div class="mb-6 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-check ml-2"></i><?= e($notice) ?>
</div>
<?php endif; ?>

<?php if ($error): ?>
<div class="mb-6 bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl p-4 text-sm font-medium">
    <i class="fas fa-circle-exclamation ml-2"></i><?= e($error) ?>
</div>
<?php endif; ?>

<div class="mb-6 bg-sky-50 border border-sky-200 text-sky-900 rounded-2xl p-4 text-sm leading-6">
    <i class="fas fa-drafting-compass ml-2"></i>
    <b>پیش‌نویس یعنی هیچ بازیکنی آن را نمی‌بیند.</b>
    نسخهٔ در حال توسعه را ذخیره کن، تست کن، و فقط وقتی آماده شد دکمهٔ «انتشار» را بزن.
    تا قبل از انتشار، نه اعلانی ارسال می‌شود و نه <code>app_config</code> آن را به‌عنوان
    «آخرین نسخه» یا «حداقل نسخه» به بازیکنان نشان می‌دهد.
</div>

<?php if ($enabledCount === 0): ?>
<div class="mb-6 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl p-4 text-sm">
    <i class="fas fa-triangle-exclamation ml-2"></i>
    هیچ نسخه‌ای فعال نیست. در این حالت هیچ نسخه‌ای مسدود نمی‌شود (عمدی است تا با یک اشتباه
    کل بازی برای همه قفل نشود). حداقل یک نسخه را فعال کن.
</div>
<?php endif; ?>

<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden mb-8">
    <div class="overflow-x-auto">
        <table class="w-full">
            <?php tableHeader([
                ['label' => 'کد نسخه', 'align' => 'center'],
                ['label' => 'نام', 'align' => 'right'],
                ['label' => 'انتشار', 'align' => 'center'],
                ['label' => 'فعال؟', 'align' => 'center'],
                ['label' => 'آخرین؟', 'align' => 'center'],
                ['label' => 'پیام به کاربر', 'align' => 'right'],
                ['label' => 'عملیات', 'align' => 'center'],
            ]); ?>
            <tbody class="divide-y divide-emerald-50">
            <?php foreach ($rows as $r):
                $enabled = (int) $r['is_enabled'] === 1;
                $published = (int) ($r['is_published'] ?? 1) === 1; ?>
            <tr class="hover:bg-emerald-50/40 <?= $enabled ? '' : 'bg-rose-50/40' ?>">
                <td class="px-4 py-3 text-center font-mono font-bold"><?= faNum($r['version_code']) ?></td>
                <td class="px-4 py-3 text-sm"><?= e($r['version_name'] ?: '—') ?>
                    <?php if ($r['note']): ?><p class="text-[11px] text-emerald-400 mt-0.5"><?= e($r['note']) ?></p><?php endif; ?>
                </td>
                <td class="px-4 py-3 text-center">
                    <?php if ($published): ?>
                        <span class="px-2.5 py-1.5 rounded-lg text-xs font-bold bg-emerald-100 text-emerald-700">
                            <i class="fas fa-circle-check ml-1"></i>منتشرشده
                        </span>
                    <?php else: ?>
                        <span class="px-2.5 py-1.5 rounded-lg text-xs font-bold bg-amber-100 text-amber-800">
                            <i class="fas fa-pen-ruler ml-1"></i>پیش‌نویس
                        </span>
                    <?php endif; ?>
                </td>
                <td class="px-4 py-3 text-center">
                    <form method="post" class="inline">
                        <?= csrfField() ?>
                        <input type="hidden" name="action" value="toggle">
                        <input type="hidden" name="version_code" value="<?= (int)$r['version_code'] ?>">
                        <button class="px-3 py-1.5 rounded-lg text-xs font-bold <?= $enabled ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200' : 'bg-rose-100 text-rose-700 hover:bg-rose-200' ?>">
                            <?= $enabled ? 'فعال' : 'غیرفعال' ?>
                        </button>
                    </form>
                </td>
                <td class="px-4 py-3 text-center">
                    <form method="post" class="inline">
                        <?= csrfField() ?>
                        <input type="hidden" name="action" value="set_latest">
                        <input type="hidden" name="version_code" value="<?= (int)$r['version_code'] ?>">
                        <button <?= $published ? '' : 'disabled title="اول منتشر کن"' ?>
                            class="px-2.5 py-1.5 rounded-lg text-xs font-bold <?= (int)$r['is_latest'] === 1 ? 'bg-amber-400 text-white' : ($published ? 'bg-slate-100 text-slate-600 hover:bg-slate-200' : 'bg-slate-100 text-slate-300 cursor-not-allowed') ?>">
                            <?= (int)$r['is_latest'] === 1 ? 'آخرین' : '─' ?>
                        </button>
                    </form>
                </td>
                <td class="px-4 py-3 text-xs text-emerald-700 max-w-xs">
                    <?= $r['force_message'] ? e($r['force_message']) : '<span class="text-emerald-300">—</span>' ?>
                </td>
                <td class="px-4 py-3 text-center whitespace-nowrap">
                    <?php if (!$published): ?>
                    <form method="post" class="inline" onsubmit="return confirm('نسخه منتشر شود و اعلان برای همهٔ بازیکنان ثبت شود؟')">
                        <?= csrfField() ?>
                        <input type="hidden" name="action" value="publish">
                        <input type="hidden" name="version_code" value="<?= (int)$r['version_code'] ?>">
                        <input type="hidden" name="notify" value="1">
                        <input type="hidden" name="make_latest" value="1">
                        <button class="px-2.5 py-1.5 bg-emerald-500 text-white rounded-lg text-xs font-bold hover:bg-emerald-600">
                            <i class="fas fa-rocket ml-1"></i>انتشار
                        </button>
                    </form>
                    <?php else: ?>
                    <form method="post" class="inline" onsubmit="return confirm('از انتشار خارج شود؟')">
                        <?= csrfField() ?>
                        <input type="hidden" name="action" value="unpublish">
                        <input type="hidden" name="version_code" value="<?= (int)$r['version_code'] ?>">
                        <button class="px-2.5 py-1.5 bg-amber-100 text-amber-800 rounded-lg text-xs font-bold hover:bg-amber-200">
                            <i class="fas fa-eye-slash"></i>
                        </button>
                    </form>
                    <?php endif; ?>
                    <a href="?edit=<?= (int)$r['version_code'] ?>" class="px-2.5 py-1.5 bg-sky-100 text-sky-700 rounded-lg text-xs font-bold hover:bg-sky-200">
                        <i class="fas fa-pen"></i>
                    </a>
                    <form method="post" class="inline" onsubmit="return confirm('حذف شود؟')">
                        <?= csrfField() ?>
                        <input type="hidden" name="action" value="delete">
                        <input type="hidden" name="version_code" value="<?= (int)$r['version_code'] ?>">
                        <button class="px-2.5 py-1.5 bg-rose-100 text-rose-700 rounded-lg text-xs font-bold hover:bg-rose-200">
                            <i class="fas fa-trash"></i>
                        </button>
                    </form>
                </td>
            </tr>
            <?php endforeach; ?>
            <?php if (!$rows): ?>
            <tr><td colspan="7" class="px-4 py-10 text-center text-emerald-400">
                هنوز نسخه‌ای ثبت نشده. کد نسخهٔ اپ فعلی را پایین اضافه کن.
            </td></tr>
            <?php endif; ?>
            </tbody>
        </table>
    </div>
</div>

<div class="bg-white/90 rounded-2xl shadow-lg border border-emerald-100 p-6">
    <h3 class="font-black text-emerald-900 mb-1">
        <?= $editing ? 'ویرایش نسخهٔ ' . faNum($editing['version_code']) : 'افزودن / ویرایش نسخه' ?>
    </h3>
    <p class="text-xs text-emerald-500 mb-5">
        «کد نسخه» همان <code>versionCode</code> در <code>app/build.gradle.kts</code> است.
        نسخهٔ جدید همیشه به‌صورت <b>پیش‌نویس</b> ذخیره می‌شود؛ انتشار و ارسال اعلان یک
        تصمیم جداگانه است.
    </p>
    <form method="post" class="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-3xl">
        <?= csrfField() ?>
        <input type="hidden" name="action" value="save">
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">کد نسخه (عدد)</label>
            <input type="number" name="version_code" required value="<?= $editing ? (int)$editing['version_code'] : '' ?>"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">نام نسخه</label>
            <input type="text" name="version_name" maxlength="32" value="<?= e($editing['version_name'] ?? '') ?>" placeholder="۱.۰.۱۰"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div class="md:col-span-2">
            <label class="block text-sm font-semibold text-emerald-800 mb-2">پیامی که به کاربر این نسخه نشان داده می‌شود</label>
            <textarea name="force_message" rows="2" maxlength="500"
                placeholder="مثال: برای ادامهٔ بازی باید به نسخهٔ جدید بروید."
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500"><?= e($editing['force_message'] ?? '') ?></textarea>
        </div>
        <div>
            <!--
                Two consumers, one field.

                It has always driven the version gate ("this build is too old,
                here is where the new one is"). It is also the link the in-app
                rating prompt opens, so a player who taps "rate us" lands on the
                same page a blocked build is sent to update from — which is the
                only page that can hold a rating.

                Keeping them as one value is deliberate: two links for one store
                is two links that can disagree, and the failure mode is a player
                sent to a dead URL by the prompt that is supposed to be asking
                them for a favour.
            -->
            <label class="block text-sm font-semibold text-emerald-800 mb-2">لینک فروشگاه (برای به‌روزرسانی و درخواست امتیاز)</label>
            <input type="url" name="store_url" dir="ltr" value="<?= e($editing['store_url'] ?? '') ?>"
                placeholder="https://myket.ir/app/…"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
            <p class="mt-1 text-xs text-emerald-600">اگر خالی باشد، درخواست امتیاز به کاربر نشان داده نمی‌شود.</p>
        </div>
        <div>
            <label class="block text-sm font-semibold text-emerald-800 mb-2">یادداشت (فقط برای خودت)</label>
            <input type="text" name="note" maxlength="255" value="<?= e($editing['note'] ?? '') ?>"
                class="w-full px-4 py-3 border border-emerald-200 rounded-xl outline-none focus:ring-2 focus:ring-emerald-500">
        </div>
        <div class="md:col-span-2">
            <button class="px-6 py-3 bg-emerald-500 text-white rounded-xl font-bold hover:bg-emerald-600">
                <i class="fas fa-save ml-1"></i>ذخیره
            </button>
        </div>
    </form>
</div>
<?php renderFoot(); ?>
