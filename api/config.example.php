<?php
/**
 * Block Boom API — example configuration.
 *
 * Copy this file to config.php and fill in real values, or set the BB_*
 * environment variables on the host. Never commit the real config.php.
 */
define('DB_HOST', getenv('BB_DB_HOST') ?: 'localhost');
define('DB_NAME', getenv('BB_DB_NAME') ?: 'CHANGE_ME');
define('DB_USER', getenv('BB_DB_USER') ?: 'CHANGE_ME');
define('DB_PASS', getenv('BB_DB_PASS') ?: 'CHANGE_ME');
define('DB_CHARSET', 'utf8mb4');

/** Server-only secret used to sign one-run tokens. */
define('RUN_TOKEN_SECRET', getenv('BB_RUN_TOKEN_SECRET') ?: 'CHANGE_ME');

/** Legacy message-signing key. Kept only for old clients/events. */
define('HMAC_SECRET', getenv('BB_HMAC_SECRET') ?: 'CHANGE_ME');

/** Shared key for the notification writer. */
define('ADMIN_API_KEY', getenv('BB_ADMIN_API_KEY') ?: 'CHANGE_ME');

/** Exact browser origin allowed to call the API; empty means no CORS. */
define('ALLOWED_ORIGIN', getenv('BB_ALLOWED_ORIGIN') ?: '');

/** Trusted proxy header, for example HTTP_CF_CONNECTING_IP. Empty by default. */
define('TRUSTED_PROXY_HEADER', getenv('BB_TRUSTED_PROXY_HEADER') ?: '');

/** Maximum tolerated client clock drift, in seconds. */
define('MAX_TIME_DRIFT', 300);

require_once __DIR__ . '/policy.php';
require_once __DIR__ . '/anticheat.php';
