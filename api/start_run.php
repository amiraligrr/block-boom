<?php
/**
 * start_run.php — opens a verified play session.
 *
 * The server mints a signed token pinning (device, level, start time). The
 * signing key never ships in the app, so a token cannot be forged; each token
 * is single-use, so it cannot be replayed. submit_score.php only trusts a
 * score that is backed by one of these.
 *
 * POST { "device_id": "...", "level": 12 }
 *   -> { "ok": true, "run_token": "...", "started_at": 1730000000, "expires_at": ... }
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

// Fail closed rather than signing tokens with a publicly known placeholder.
if (RUN_TOKEN_SECRET === '' || strpos(RUN_TOKEN_SECRET, 'CHANGE_ME') === 0) {
    error_log('start_run: RUN_TOKEN_SECRET is still the placeholder; refusing to issue tokens');
    jsonResponse(['error' => 'anti-cheat not configured'], 503);
}

$data = readJsonBody();
$deviceId = bb_normalize_device_id((string)($data['device_id'] ?? ''));
$level    = clampInt($data['level'] ?? 1, 1, BB_MAX_LEVEL, 1);

if (strlen($deviceId) < 5) {
    jsonResponse(['error' => 'Invalid device'], 400);
}

try {
    $pdo = getPDO();

    // Banned devices cannot open runs. This is checked here as well as in
    // app_config.php: a patched client simply skips the gate, so anything
    // that hands out value has to re-check.
    $ban = bb_is_banned($pdo, $deviceId);
    if ($ban !== null) {
        jsonResponse(['error' => 'banned', 'reason' => $ban['reason']], 403);
    }

    $now = time();

    // Throttle per device AND per IP before doing any work.
    //
    // Without this, start_run.php was the cheapest write in the whole API:
    // no auth, no rate limit, and a client-supplied device_id that a forger
    // simply rotates. The "one open run per device" check below is defeated by
    // that rotation, so it never bounded anything — it only meant each new id
    // got a fresh INSERT plus a full-table DELETE on every single call.
    // The rate_limit table is shared with the submit and ad-event throttles,
    // which namespace their keys the same way ('run:' / 'dev:' / 'ip:').
    $ip = bb_client_ip();
    foreach ([
        [bb_throttle_key('run:', $deviceId), BB_START_RUN_PER_MINUTE],
        [bb_throttle_key('iprun:', $ip),     BB_START_RUN_PER_IP_MINUTE],
    ] as $limit) {
        if (!bb_take_throttle_slot($pdo, $limit[0], 60, $limit[1])) {
            jsonResponse(['error' => 'rate_limited', 'retry_after' => 10], 429);
        }
    }

    // Housekeeping: drop runs that can no longer be submitted.
    $pdo->prepare("DELETE FROM run_tokens WHERE expires_at < ?")->execute([$now - 604800]);

    // One open run per device keeps the play-time accounting honest and stops
    // a forger from opening thousands of tokens to inflate their credit.
    $stmt = $pdo->prepare(
        "SELECT run_id FROM run_tokens
          WHERE device_id = ? AND consumed = 0 AND expires_at > ?
          ORDER BY started_at DESC LIMIT 1"
    );
    $stmt->execute([$deviceId, $now]);
    $open = $stmt->fetch();
    if ($open) {
        jsonResponse(['error' => 'run already open'], 409);
    }

    $runId = bin2hex(random_bytes(16));
    $expires = $now + BB_RUN_MAX_AGE_SECONDS;

    [$token, ] = bb_sign_run([
        'rid' => $runId,
        'did' => $deviceId,
        'lvl' => $level,
        'ts'  => $now,
    ]);

    $stmt = $pdo->prepare(
        "INSERT INTO run_tokens (run_id, device_id, level, started_at, expires_at)
         VALUES (?, ?, ?, ?, ?)"
    );
    $stmt->execute([$runId, $deviceId, $level, $now, $expires]);

    jsonResponse([
        'ok'         => true,
        'run_token'  => $token,
        'run_id'     => $runId,
        'started_at' => $now,
        'expires_at' => $expires,
    ]);
} catch (PDOException $e) {
    error_log('start_run DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
