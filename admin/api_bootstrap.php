<?php
/**
 * Small shared bootstrap for the admin panel.
 *
 * db.php used to define getClientIP(), and api/anticheat.php has a stricter
 * version of the same function. Both files now resolve to one implementation
 * so the panel and the API can never disagree about what an IP is.
 */
function bb_admin_client_ip(): string {
    $remote = $_SERVER['REMOTE_ADDR'] ?? '';
    if (TRUSTED_PROXY_HEADER !== '' && !empty($_SERVER[TRUSTED_PROXY_HEADER])) {
        $v = trim(explode(',', (string) $_SERVER[TRUSTED_PROXY_HEADER])[0]);
        if ($v !== '') return $v;
    }
    return $remote !== '' ? $remote : '0.0.0.0';
}

/**
 * Compose a rate_limit key that fits the `ip` VARCHAR(64) column.
 *
 * Mirrors bb_throttle_key() in api/anticheat.php, which the panel cannot load
 * (it is on a different host and is denied over HTTP). Keeping the two in step
 * matters because they write to the same table: an over-long key truncated by
 * one side and not the other would let two different identities share a
 * budget, or blow the column up entirely.
 */
function bb_admin_throttle_key(string $prefix, string $value): string {
    $key = $prefix . $value;
    if (strlen($key) <= 64) return $key;
    return substr($key, 0, 24) . ':' . substr(sha1($key), 0, 38);
}
