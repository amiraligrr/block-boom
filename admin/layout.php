<?php
/**
 * Shared admin layout + security helpers.
 *
 * The sidebar and <head> were copy-pasted into all six pages (~240 duplicated
 * lines that could silently drift — the tailwind config was already in two
 * different forms). They now live here, with the active page passed in.
 *
 * This file also adds the two things the panel had none of: CSRF protection
 * and a real password hash. Every state-changing action is a POST carrying a
 * token; the destructive GET links (clearing the rate-limit table, logging
 * out) are gone.
 */
require_once __DIR__ . '/config.php';

const ADMIN_NAV = [
    'dashboard'   => ['dashboard.php',  'fas fa-tachometer-alt', 'داشبورد'],    // user management lives in leaderboard.php — users.php 500'd on every
    // request and was deleted, but this nav entry outlived it and kept
    // sending people to a 404.
    'monitor'     => ['monitor.php',    'fas fa-heartbeat',     'مانیتورینگ زنده'],
    'leaderboard' => ['leaderboard.php','fas fa-users',         'کاربران'],
    'players'     => ['players.php',   'fas fa-user-astronaut', 'بازیکنان'],
    'purchases'   => ['purchases.php',  'fas fa-shopping-cart', 'خریدها'],
    'ads'         => ['ads.php',        'fas fa-chart-bar',    'آمار تبلیغات'],
    'tickets'     => ['tickets.php',    'fas fa-life-ring',     'تیکت‌ها'],
    'notifications' => ['notifications.php', 'fas fa-bell',      'اعلان‌ها'],
    // Named integrity.php, not anticheat.php: the API's root .htaccess blocks
    // any file whose basename is "anticheat.php" (it's a library there), and
    // <FilesMatch> matches the basename at every directory depth — so the
    // admin page was 403-ing under its own name.
    'integrity'   => ['integrity.php',  'fas fa-shield-alt',    'ضدتقلب'],
    'versions'    => ['versions.php',   'fas fa-code-branch',   'نسخه‌ها'],
    'bans'        => ['bans.php',        'fas fa-user-slash',    'مسدودی‌ها'],
    'settings'    => ['settings.php',   'fas fa-cog',           'تنظیمات'],
];

// ── CSRF ──────────────────────────────────────────────────────────────────
function csrfToken(): string {
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function csrfField(): string {
    return '<input type="hidden" name="csrf" value="' . htmlspecialchars(csrfToken(), ENT_QUOTES, 'UTF-8') . '">';
}

/** Aborts with 403 unless the request carries a valid token. */
function requireCsrf(): void {
    $sent = (string)($_POST['csrf'] ?? ($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ''));
    if ($sent === '' || empty($_SESSION['csrf']) || !hash_equals($_SESSION['csrf'], $sent)) {
        http_response_code(403);
        exit('Invalid CSRF token');
    }
}

function requirePost(): void {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        http_response_code(405);
        exit('Method not allowed');
    }
    requireCsrf();
}

// ── Output ────────────────────────────────────────────────────────────────
function e($v): string {
    return htmlspecialchars((string) $v, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function faNum($n): string {
    return str_replace(
        ['0','1','2','3','4','5','6','7','8','9'],
        ['۰','۱','۲','۳','۴','۵','۶','۷','۸','۹'],
        (string) $n
    );
}

/** Compact number for stat tiles: 12345 -> "12.3K" */
function compactNum($n): string {
    $n = (int) $n;
    if ($n < 1000) return (string) $n;
    if ($n < 1000000) return round($n / 1000, 1) . 'K';
    if ($n < 1000000000) return round($n / 1000000, 1) . 'M';
    return round($n / 1000000000, 2) . 'B';
}

function renderHead(string $title, string $active, bool $charts = false): void {
    $needsCharts = $charts;
    ?>
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= e($title) ?> — Block Boom</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.4.0/css/all.min.css" rel="stylesheet">
    <script>
        tailwind.config = { theme: { extend: { colors: { primary: '#10B981', secondary: '#0EA5E9', dark: '#064E3B', light: '#ECFDF5' } } } }
    </script>
    <?php if ($needsCharts): ?>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
    <?php endif; ?>
</head>
<body class="min-h-screen bg-gradient-to-br from-emerald-50 via-teal-50 to-emerald-100">
<?php /* No h-screen/overflow-hidden here. Trapping the whole document in a
        fixed-height flex box meant a chart that grew could never be scrolled
        back to, and on a phone the 256px sidebar pushed the content off the
        screen entirely. The document now scrolls normally. */ ?>
<div class="flex min-h-screen flex-col md:flex-row">
    <?php renderSidebar($active); ?>
    <main class="flex-1 min-w-0 p-4 sm:p-6 md:p-8">
    <?php
}

function renderSidebar(string $active): void {
    ?>
    <aside class="w-full md:w-64 shrink-0 bg-white/90 backdrop-blur-sm md:border-l border-emerald-100 flex flex-col">
        <div class="p-4 md:p-6 border-b border-emerald-100">
            <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center">
                    <i class="fas fa-crown text-xl text-white"></i>
                </div>
                <div>
                    <h1 class="text-xl font-black text-emerald-900">Block Boom</h1>
                    <p class="text-xs text-emerald-500">پنل مدیریت</p>
                </div>
            </div>
        </div>
        <nav class="flex-1 p-2 md:p-4 md:space-y-2 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-1 gap-1 md:gap-2">
            <?php foreach (ADMIN_NAV as $key => [$href, $icon, $label]): ?>
                <?php
                // Only render a link if its target actually exists. A stale
                // entry in this list is what produced the 404 the user hit
                // when "کاربران" pointed at a users.php that had been deleted.
                if (!is_file(__DIR__ . '/' . $href)) continue;
                ?>
                <a href="<?= e($href) ?>"
                   class="flex items-center gap-2 md:gap-3 px-3 md:px-4 py-2.5 md:py-3 rounded-xl transition-all whitespace-nowrap <?= $active === $key ? 'bg-emerald-50 text-emerald-700 font-semibold' : 'text-emerald-700 hover:bg-emerald-50 font-medium' ?>">
                    <i class="<?= e($icon) ?> w-5 shrink-0"></i> <span class="text-sm md:text-base"><?= e($label) ?></span>
                </a>
            <?php endforeach; ?>
        </nav>
        <div class="p-2 md:p-4 border-t border-emerald-100">
            <form method="post" action="logout.php">
                <?= csrfField() ?>
                <button type="submit" class="flex items-center gap-2 md:gap-3 px-3 md:px-4 py-2.5 md:py-3 text-red-500 hover:bg-red-50 rounded-xl font-medium transition-all w-full">
                    <i class="fas fa-sign-out-alt w-5 shrink-0"></i> خروج
                </button>
            </form>
        </div>
    </aside>
    <?php
}

function renderFoot(): void {
    ?>
    </main>
</div>
</body>
</html>
    <?php
}

/** Page title + optional action buttons, used at the top of every page. */
function renderPageHeader(string $title, string $subtitle, string $actionsHtml = ''): void {
    ?>
    <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-8">
        <div>
            <h1 class="text-2xl md:text-3xl font-black text-emerald-900"><?= e($title) ?></h1>
            <p class="text-emerald-600 mt-1"><?= e($subtitle) ?></p>
        </div>
        <?php if ($actionsHtml !== ''): ?>
        <div class="flex flex-wrap gap-2"><?= $actionsHtml ?></div>
        <?php endif; ?>
    </div>
    <?php
}

/** A stat tile. */
function statCard(string $label, $value, string $icon = 'fas fa-chart-line', string $tone = 'emerald'): void {
    $tones = [
        'emerald' => 'from-emerald-500 to-teal-600',
        'amber'   => 'from-amber-500 to-orange-500',
        'rose'    => 'from-rose-500 to-pink-600',
        'sky'     => 'from-sky-500 to-cyan-600',
        'violet'  => 'from-violet-500 to-purple-600',
    ];
    $grad = $tones[$tone] ?? $tones['emerald'];
    ?>
    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
                <p class="text-emerald-600 text-xs font-medium truncate"><?= e($label) ?></p>
                <p class="text-2xl md:text-3xl font-black text-emerald-900 mt-1 truncate"><?= e($value) ?></p>
            </div>
            <div class="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br <?= e($grad) ?> flex items-center justify-center">
                <i class="<?= e($icon) ?> text-white text-sm"></i>
            </div>
        </div>
    </div>
    <?php
}

function tableHeader(array $cols): void {
    echo '<thead class="bg-emerald-50"><tr>';
    foreach ($cols as $c) {
        $align = $c['align'] ?? 'right';
        $cls = $align === 'center' ? 'text-center' : ($align === 'left' ? 'text-left' : 'text-right');
        echo '<th class="px-4 py-3 ' . $cls . ' text-xs font-bold text-emerald-600">' . e($c['label']) . '</th>';
    }
    echo '</tr></thead>';
}

function emptyRow(int $cols, string $text): void {
    echo '<tr><td colspan="' . $cols . '" class="px-4 py-10 text-center text-emerald-400">' . e($text) . '</td></tr>';
}

/**
 * A chart container with a BOUNDED height.
 *
 * Every canvas here is configured `responsive: true, maintainAspectRatio: false`,
 * which tells Chart.js to ignore the canvas' own height attribute and stretch
 * to fill its parent instead. With an unbounded parent that is a feedback
 * loop: the canvas grows -> the document grows -> Chart.js' ResizeObserver
 * fires -> the canvas grows again. The page then scrolls down forever and
 * eventually chokes the tab.
 *
 * The fix is the one line this helper exists for: a wrapper with an explicit
 * pixel height, so the canvas has a ceiling to fill up to.
 */
function chartBox(string $id, int $height = 260, string $label = ''): void {
    printf(
        '<div style="position:relative;height:%dpx;width:100%%">%s<canvas id="%s"></canvas></div>',
        $height,
        $label !== '' ? '<p class="sr-only">' . e($label) . '</p>' : '',
        e($id)
    );
}

/** Normalises a device id the same way the API does. */
function bb_admin_normalize_device(string $id): string {
    return substr(preg_replace('/[^A-Za-z0-9_\-]/', '', $id) ?? '', 0, 64);
}
