<?php
/**
 * policy.php — server policy values that BOTH the API and the admin panel need.
 *
 * These live here, not in anticheat.php, for two reasons.
 *
 * 1. The admin panel has to display them. admin/integrity.php never loads
 *    api/config.php, so a constant defined only there was undefined at display
 *    time — a fatal Error on PHP 8, and with display_errors off the page just
 *    stopped rendering halfway down instead of showing an error.
 *    tools/check_pages_render.py is what caught that.
 *
 * 2. They must be defined in a file the deploy actually ships. tools/deploy.sh
 *    deliberately never overwrites api/config.php (it holds the live database
 *    credentials), so a fix that only edited api/config.php would never reach
 *    the server. A separate file does get deployed.
 *
 * ── Drift, and why that mattered ──────────────────────────────────────────
 * This file used to define RATE_LIMIT_SECONDS = 10 purely for the panel's
 * benefit, while the value actually enforced was a separate
 * BB_SUBMIT_COOLDOWN_SECONDS in anticheat.php. They happened to be equal, and
 * nothing kept them equal — so the panel showed an "active rule" that the
 * server was not enforcing, which is exactly the class of bug that produced a
 * past "30 vs 5 RATE_LIMIT_SECONDS drift" on this project.
 *
 * The fix is to make one value the source of truth. policy.php now owns the
 * enforced numbers, anticheat.php require_once's this file and uses them, and
 * RATE_LIMIT_SECONDS is derived rather than declared independently.
 *
 * Every constant is guarded, because a deployment may still carry an older
 * api/config.php or anticheat.php that defines some of these itself — a second
 * define() would emit a warning and, on a strict error handler, abort.
 */
if (!defined('BB_SUBMIT_COOLDOWN_SECONDS')) {
    /** Minimum gap between two score submissions from one device. */
    define('BB_SUBMIT_COOLDOWN_SECONDS', 10);
}
if (!defined('BB_SUBMIT_COOLDOWN_STRIKES')) {
    /** Misses tolerated inside the gap before a 429 is returned. */
    define('BB_SUBMIT_COOLDOWN_STRIKES', 6);
}
if (!defined('RATE_LIMIT_SECONDS')) {
    /**
     * Display alias for the panel. Derived, never hand-written: a value that
     * can disagree with the enforced one is exactly the bug this file exists
     * to prevent.
     */
    define('RATE_LIMIT_SECONDS', BB_SUBMIT_COOLDOWN_SECONDS);
}
