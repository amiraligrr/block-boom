<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';

if (!empty($_SESSION['admin_logged_in'])) {
    header('Location: dashboard.php');
    exit;
}

$error = $_SESSION['login_error'] ?? '';
unset($_SESSION['login_error']);

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();   // CSRF + POST-only

    $username = (string)($_POST['username'] ?? '');
    $password = (string)($_POST['password'] ?? '');
    $ip = bb_admin_client_ip();

    // Brute-force protection keyed on the SOURCE IP in the database.
    //
    // The old counter lived in $_SESSION, i.e. in the visitor's own cookie:
    // dropping the cookie reset it to zero, so the 5-attempts-in-5-minutes
    // limit was defeated by a single header change and offered no protection
    // at all against parallel guessing.
    $pdo = getPDO();
    $loginKey = bb_admin_throttle_key('login:', $ip);
    $stmt = $pdo->prepare("SELECT last_submit, strikes FROM rate_limit WHERE ip = ?");
    $stmt->execute([$loginKey]);
    $row = $stmt->fetch();
    $strikes = (int)($row['strikes'] ?? 0);
    $lastTry = (int)($row['last_submit'] ?? 0);

    if ($strikes >= 8 && (time() - $lastTry) < 600) {
        $wait = 600 - (time() - $lastTry);
        $error = "تلاش‌های ناموفق زیاد. $wait ثانیه دیگر تلاش کنید.";
    } elseif ($username === ADMIN_USER && verifyAdminPassword($password)) {
        session_regenerate_id(true);
        unset($_SESSION['login_attempts'], $_SESSION['login_last_try']);
        $_SESSION['admin_logged_in'] = true;
        $_SESSION['last_activity'] = time();

        // admin_user was never assigned anywhere in the panel, so every row in
        // app_bans.banned_by and tickets.banned_by recorded the literal string
        // 'admin'. For a punitive action like a ban, "who did this" is the one
        // column you actually need, so stamp it once here and let the writers
        // read it instead of hardcoding a fallback.
        $_SESSION['admin_user'] = ADMIN_USER;

        $pdo->prepare("DELETE FROM rate_limit WHERE ip = ?")->execute([$loginKey]);
        header('Location: dashboard.php');
        exit;
    } else {
        $pdo->prepare(
            "INSERT INTO rate_limit (ip, last_submit, strikes) VALUES (?,?,1)
             ON DUPLICATE KEY UPDATE last_submit = VALUES(last_submit), strikes = strikes + 1"
        )->execute([$loginKey, time()]);
        $error = 'نام کاربری یا رمز عبور اشتباه است';
    }
}

$csrf = csrfToken();
?>
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <meta name="theme-color" content="#03100b">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="robots" content="noindex, nofollow">
    <title>ورود — Block Boom</title>
    <link href="https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="assets/admin.css">
</head>
<body class="bb-admin bb-login-body">
    <div class="bb-login-wrap">
        <section class="bb-login-art">
            <div class="bb-login-hero">
                <a class="bb-brand" href="login.php" aria-label="Block Boom Admin">
                    <span class="bb-brand-mark"><i class="fas fa-crown"></i></span>
                    <span class="bb-brand-copy">
                        <strong>Block Boom</strong>
                        <span>CONTROL ROOM</span>
                    </span>
                </a>
                <h1>پنل مدیریت<br><span>Block Boom</span></h1>
                <p>یک اتاق فرمان تاریک، سریع و موبایل‌پسند برای دیدن بازیکنان، رکوردها، تبلیغات و سلامت بازی.</p>
                <ul class="bb-login-list">
                    <li><i class="fas fa-chart-line"></i> آمار زنده و داشبورد عملیاتی</li>
                    <li><i class="fas fa-shield-halved"></i> ورود امن با محافظت از تلاش مکرر</li>
                    <li><i class="fas fa-mobile-screen-button"></i> کاملاً واکنش‌گرا روی iPhone و Android</li>
                </ul>
            </div>
            <div class="bb-login-foot">
                <i class="fas fa-lock"></i>
                <span>نشست رمزنگاری‌شده · Block Boom Admin</span>
            </div>
        </section>

        <section class="bb-login-card">
            <div class="bb-login-kicker">SECURE ACCESS</div>
            <h2>ورود به پنل</h2>
            <p>نام کاربری و رمز مدیریت را وارد کن. اطلاعات ورود روی این دستگاه ذخیره نمی‌شود.</p>

            <?php if ($error): ?>
                <div class="bb-login-error" role="alert">
                    <i class="fas fa-triangle-exclamation"></i>
                    <span><?= e($error) ?></span>
                </div>
            <?php endif; ?>

            <form method="POST">
                <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
                <div class="bb-field">
                    <label for="admin-username">نام کاربری</label>
                    <div class="bb-input-wrap">
                        <input id="admin-username" type="text" name="username" required autocomplete="username"
                            inputmode="text" placeholder="نام کاربری ادمین">
                    </div>
                </div>
                <div class="bb-field">
                    <label for="admin-password">رمز عبور</label>
                    <div class="bb-input-wrap has-toggle">
                        <input id="admin-password" type="password" name="password" required autocomplete="current-password"
                            placeholder="رمز عبور">
                        <button type="button" class="bb-password-toggle" data-password-toggle="admin-password" aria-label="نمایش رمز">
                            <i class="fas fa-eye"></i>
                        </button>
                    </div>
                </div>
                <button type="submit" class="bb-login-btn">
                    <i class="fas fa-sign-in-alt"></i>
                    <span>ورود امن به پنل</span>
                </button>
            </form>

            <div class="bb-login-note">
                <i class="fas fa-shield-halved"></i>
                سیستم مدیریت Block Boom · نسخه ۱.۲
            </div>
        </section>
    </div>
    <script src="assets/admin.js"></script>
</body>
</html>
