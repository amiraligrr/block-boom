<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

// Logout is a state change, so it is POST + CSRF. It used to be a plain GET
// link: any page could log the admin out with an <img> tag.
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    requirePost();
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $p = session_get_cookie_params();
        setcookie(session_name(), '', time() - 42000, $p['path'], $p['domain'], $p['secure'], $p['httponly']);
    }
    session_destroy();
}
header('Location: login.php');
exit;
