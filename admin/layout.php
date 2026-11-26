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

/**
 * One nav list, shared by the desktop sidebar and the mobile pill rail.
 *
 * A stale entry is skipped when its target file does not exist; otherwise a
 * removed page leaves a permanent 404 in the menu.
 */
function renderNavLinks(string $active): void {
    foreach (ADMIN_NAV as $key => [$href, $icon, $label]) {
        if (!is_file(__DIR__ . '/' . $href)) continue;
        $isActive = $active === $key;
        ?>
        <a href="<?= e($href) ?>"
           class="bb-nav-link<?= $isActive ? ' is-active' : '' ?>"
           <?= $isActive ? 'aria-current="page"' : '' ?>>
            <span class="bb-nav-icon"><i class="<?= e($icon) ?>"></i></span>
            <span class="bb-nav-label"><?= e($label) ?></span>
        </a>
        <?php
    }
}

function renderHead(string $title, string $active, bool $charts = false): void {
    $needsCharts = $charts;
    ?>
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <meta name="theme-color" content="#03100b">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="Block Boom">
    <meta name="robots" content="noindex, nofollow">
    <title><?= e($title) ?> — Block Boom</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="assets/admin.css">
    <script>
        tailwind.config = { theme: { extend: { colors: { primary: '#10B981', secondary: '#0EA5E9', dark: '#064E3B', light: '#ECFDF5' } } } }
    </script>
    <?php if ($needsCharts): ?>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
    <?php endif; ?>
</head>
<body class="bb-admin">
<?php /* The document scrolls normally: a chart can grow, and a phone never
        gets a 256px sidebar pushed over the content. */ ?>
<div class="bb-shell">
    <?php renderSidebar($active); ?>
    <div class="bb-mobile-head">
        <div class="bb-mobile-top">
            <a class="bb-brand" href="dashboard.php" aria-label="Block Boom Admin">
                <span class="bb-brand-mark"><i class="fas fa-crown"></i></span>
                <span class="bb-brand-copy">
                    <strong>Block Boom</strong>
                    <span>CONTROL ROOM</span>
                </span>
            </a>
            <div class="bb-mobile-actions">
                <form method="post" action="logout.php">
                    <?= csrfField() ?>
                    <button type="submit" class="bb-logout" aria-label="خروج">
                        <i class="fas fa-arrow-right-from-bracket"></i>
                        <span>خروج</span>
                    </button>
                </form>
            </div>
        </div>
        <nav class="bb-mobile-nav" aria-label="منوی مدیریت">
            <?php renderNavLinks($active); ?>
        </nav>
    </div>
    <main class="bb-main">
    <?php
}

function renderSidebar(string $active): void {
    $adminName = (string)($_SESSION['admin_user'] ?? ADMIN_USER);
    $initial = mb_strtoupper(mb_substr($adminName !== '' ? $adminName : 'A', 0, 1, 'UTF-8'), 'UTF-8');
    ?>
    <aside class="bb-sidebar">
        <a class="bb-brand" href="dashboard.php">
            <span class="bb-brand-mark"><i class="fas fa-crown"></i></span>
            <span class="bb-brand-copy">
                <strong>Block Boom</strong>
                <span>CONTROL ROOM</span>
            </span>
        </a>
        <nav class="bb-nav" aria-label="منوی مدیریت">
            <?php renderNavLinks($active); ?>
        </nav>
        <div class="bb-sidebar-foot">
            <div class="bb-admin-chip">
                <span class="bb-avatar"><?= e($initial) ?></span>
                <span>
                    <small>مدیر وارد شده</small>
                    <strong><?= e($adminName) ?></strong>
                </span>
            </div>
            <form method="post" action="logout.php">
                <?= csrfField() ?>
                <button type="submit" class="bb-logout">
                    <i class="fas fa-arrow-right-from-bracket"></i>
                    <span>خروج از پنل</span>
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
<script src="assets/admin.js"></script>
</body>
</html>
    <?php
}

/** Page title + optional action buttons, used at the top of every page. */
function renderPageHeader(string $title, string $subtitle, string $actionsHtml = ''): void {
    ?>
    <div class="bb-page-head">
        <div>
            <div class="bb-page-eyebrow">Block Boom · Admin</div>
            <h1 class="bb-page-title"><?= e($title) ?></h1>
            <p class="bb-page-sub"><?= e($subtitle) ?></p>
        </div>
        <?php if ($actionsHtml !== ''): ?>
        <div class="bb-actions"><?= $actionsHtml ?></div>
        <?php endif; ?>
    </div>
    <?php
}

/** A stat tile. */
function statCard(string $label, $value, string $icon = 'fas fa-chart-line', string $tone = 'emerald'): void {
    $tones = ['emerald', 'amber', 'rose', 'sky', 'violet'];
    $tone = in_array($tone, $tones, true) ? $tone : 'emerald';
    ?>
    <div class="bb-stat is-<?= e($tone) ?>">
        <div class="bb-stat-top">
            <div class="min-w-0">
                <p class="bb-stat-label"><?= e($label) ?></p>
                <p class="bb-stat-value"><?= e($value) ?></p>
            </div>
            <div class="bb-stat-icon">
                <i class="<?= e($icon) ?>"></i>
            </div>
        </div>
    </div>
    <?php
}

function tableHeader(array $cols): void {
    echo '<thead><tr>';
    foreach ($cols as $c) {
        $align = $c['align'] ?? 'right';
        $cls = $align === 'center' ? 'text-center' : ($align === 'left' ? 'text-left' : 'text-right');
        echo '<th class="' . $cls . '">' . e($c['label']) . '</th>';
    }
    echo '</tr></thead>';
}

function emptyRow(int $cols, string $text): void {
    echo '<tr><td colspan="' . $cols . '"><div class="bb-empty">' . e($text) . '</div></td></tr>';
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
        '<div class="bb-chart" style="height:%dpx">%s<canvas id="%s" role="img" aria-label="%s"></canvas></div>',
        $height,
        $label !== '' ? '<p class="sr-only">' . e($label) . '</p>' : '',
        e($id),
        e($label)
    );
}

/** Normalises a device id the same way the API does. */
function bb_admin_normalize_device(string $id): string {
    return substr(preg_replace('/[^A-Za-z0-9_\-]/', '', $id) ?? '', 0, 64);
}
