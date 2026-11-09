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
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ورود — Block Boom</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.4.0/css/all.min.css" rel="stylesheet">
    <script>
        tailwind.config = {
            theme: {
                extend: {
                    colors: {
                        primary: '#10B981', secondary: '#0EA5E9', dark: '#064E3B', light: '#ECFDF5'
                    }
                }
            }
        }
    </script>
</head>
<body class="min-h-screen bg-gradient-to-br from-emerald-50 via-teal-50 to-emerald-100 flex items-center justify-center p-4">
    <div class="w-full max-w-md">
        <div class="text-center mb-8">
            <div class="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-600 shadow-xl mb-4">
                <i class="fas fa-crown text-3xl text-white"></i>
            </div>
            <h1 class="text-3xl font-black text-emerald-900">Block Boom</h1>
            <p class="text-emerald-600 mt-1">پنل مدیریت</p>
        </div>

        <div class="bg-white/90 backdrop-blur-sm rounded-2xl shadow-2xl p-8 border border-emerald-100">
            <?php if ($error): ?>
                <div class="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 flex items-center gap-2">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span><?= e($error) ?></span>
                </div>
            <?php endif; ?>

            <form method="POST" class="space-y-5">
                <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
                <div>
                    <label class="block text-sm font-semibold text-emerald-800 mb-2">نام کاربری</label>
                    <input type="text" name="username" required autocomplete="username"
                        class="w-full px-4 py-3.5 border border-emerald-200 rounded-xl focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all"
                        placeholder="نام کاربری ادمین">
                </div>
                <div>
                    <label class="block text-sm font-semibold text-emerald-800 mb-2">رمز عبور</label>
                    <input type="password" name="password" required autocomplete="current-password"
                        class="w-full px-4 py-3.5 border border-emerald-200 rounded-xl focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all"
                        placeholder="رمز عبور">
                </div>
                <button type="submit"
                    class="w-full py-3.5 bg-gradient-to-r from-emerald-500 to-teal-600 text-white font-black rounded-xl hover:from-emerald-600 hover:to-teal-700 transition-all duration-200 shadow-lg hover:shadow-xl flex items-center justify-center gap-2">
                    <i class="fas fa-sign-in-alt"></i>
                    <span>ورود به پنل</span>
                </button>
            </form>

            <div class="mt-6 text-center text-sm text-emerald-500">
                <i class="fas fa-shield-alt mr-1"></i>
                سیستم مدیریت Block Boom
            </div>
        </div>
    </div>
</body>
</html>
