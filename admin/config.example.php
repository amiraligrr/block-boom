<?php
/**
 * Block Boom Admin Panel — example configuration.
 *
 * Copy this file to config.php and fill in real values, or set the BB_*
 * environment variables on the host. Never commit the real config.php.
 */
require_once __DIR__ . '/api_bootstrap.php';

define('DB_HOST', getenv('BB_DB_HOST') ?: 'localhost');
define('DB_NAME', getenv('BB_DB_NAME') ?: 'CHANGE_ME');
define('DB_USER', getenv('BB_DB_USER') ?: 'CHANGE_ME');
define('DB_PASS', getenv('BB_DB_PASS') ?: 'CHANGE_ME');
define('DB_CHARSET', 'utf8mb4');

define('ADMIN_USER', getenv('BB_ADMIN_USER') ?: 'admin');

/** Prefer a password_hash() value here after the first login. */
define('ADMIN_PASS_HASH', getenv('BB_ADMIN_PASS_HASH') ?: '');

/** Bootstrap password for the very first login only. */
define('ADMIN_PASS', getenv('BB_ADMIN_PASS') ?: 'CHANGE_ME');

/** Myket RSA public key (public by design). */
define('MYKET_PUBLIC_KEY', 'PASTE_MYKET_PUBLIC_KEY_HERE');

define('API_BASE', getenv('BB_API_BASE') ?: 'https://api.example.com');

/** Must match BB_ADMIN_API_KEY in the API config. */
define('ADMIN_API_KEY', getenv('BB_ADMIN_API_KEY') ?: '');

define('TRUSTED_PROXY_HEADER', getenv('BB_TRUSTED_PROXY_HEADER') ?: '');

if (session_status() === PHP_SESSION_NONE) {
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    ini_set('session.gc_maxlifetime', '7200');

    session_set_cookie_params([
        'httponly' => true,
        'samesite' => 'Strict',
        'secure' => true,
    ]);
    session_start();
}
