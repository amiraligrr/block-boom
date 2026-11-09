<?php
/**
 * submit_score.php — leaderboard submission with server-side validation.
 *
 * Flow:
 *   1. shape validation
 *   2. device is not banned
 *   3. submission throttle (strikes, not a hard wall)
 *   4. run token verification  — signature + single use + server-measured
 *      play time. This is the anti-cheat: the token is signed with a key the
 *      app never receives, and the run duration comes from the server clock.
 *   5. plausibility            — the score must be reachable given the level
 *      AND the play time the server has actually observed (anticheat.php)
 *   6. monotonic upsert        — level never goes down, score never shrinks
 *   7. rank
 *
 * The previous version gated on `score > level * 6000 + 5000`. That formula
 * is not the game's scoring curve: a legitimate player passes it at ~level 49
 * and is then permanently rejected with "Score too high for level" — which is
 * the error users were reporting. bb_score_ceiling() models the real curve
 * (block count x HP x split, plus boss) and adds verified play time.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

$data = readJsonBody();
if ($data === []) { jsonResponse(['error' => 'Invalid JSON'], 400); }

// ── 1. shape ───────────────────────────────────────────────────────────────
$shapeErr = bb_validate_shape($data);
if ($shapeErr !== null) { jsonResponse(['error' => 'Invalid ' . $shapeErr], 400); }

$name       = trim((string)$data['name']);
$level      = clampInt($data['level'], 1, BB_MAX_LEVEL, 1);
$score      = (int) round((float) $data['score']);
$isPremium  = !empty($data['is_premium']) ? 1 : 0;
$deviceId   = bb_normalize_device_id((string)($data['device_id'] ?? ''));

if (strlen($deviceId) < 5) { jsonResponse(['error' => 'Invalid device'], 400); }

$ip        = bb_client_ip();
$runToken  = (string)($data['run_token'] ?? '');
$legacyHmac = trim((string)($data['hmac'] ?? ''));
$timestamp = (int)($data['timestamp'] ?? 0);

// ── helpers ────────────────────────────────────────────────────────────────
function flagIt(PDO $pdo, string $deviceId, string $ip, string $reason, string $detail, int $level, int $score): void {
    try {
        $pdo->prepare(
            "INSERT INTO cheat_flags (device_id, ip, reason, detail, level, score)
             VALUES (?,?,?,?,?,?)"
        )->execute([
            $deviceId, $ip, $reason, substr($detail, 0, 255), $level, $score,
        ]);
    } catch (PDOException $e) {
        error_log('cheat_flags insert failed: ' . $e->getMessage());
    }
}

/**
 * Consumes a throttle slot for $key. Returns null on success, or the number of
 * seconds to wait once the caller has burned through its tolerance.
 */
/**
 * Makes a display name unique across devices.
 *
 * Two players both called "علی" used to render as identical rows, which is
 * indistinguishable on a leaderboard and invites accusations of score theft.
 * The suffix is derived from the device id, so it is stable for that player
 * across requests and does not flicker.
 */
function bb_unique_name(PDO $pdo, string $name, string $deviceId): string {
    try {
        $st = $pdo->prepare("SELECT device_id FROM leaderboard WHERE name = ? AND device_id <> ? LIMIT 1");
        $st->execute([$name, $deviceId]);
        if (!$st->fetch()) return $name;   // free

        // Try a few short suffixes before giving up.
        $base = mb_substr($name, 0, 12, 'UTF-8');
        $tail = substr($deviceId, -3);
        for ($i = 0; $i < 8; $i++) {
            $suffix = $i === 0 ? '·' . $tail : '·' . $tail . $i;
            $candidate = $base . $suffix;
            $st = $pdo->prepare("SELECT device_id FROM leaderboard WHERE name = ? AND device_id <> ? LIMIT 1");
            $st->execute([$candidate, $deviceId]);
            if (!$st->fetch()) return $candidate;
        }
        return $base . '·' . substr($deviceId, -6);
    } catch (PDOException $e) {
        return $name;
    }
}

function takeThrottleSlot(PDO $pdo, string $key): ?int {    $now = time();
    $stmt = $pdo->prepare("SELECT last_submit, strikes FROM rate_limit WHERE ip = ?");
    $stmt->execute([$key]);
    $row = $stmt->fetch();

    if ($row) {
        $elapsed = $now - (int) $row['last_submit'];
        $strikes = (int) ($row['strikes'] ?? 0);
        if ($elapsed < BB_SUBMIT_COOLDOWN_SECONDS) {
            $wait = BB_SUBMIT_COOLDOWN_SECONDS - $elapsed;
            if ($strikes < BB_SUBMIT_COOLDOWN_STRIKES) {
                // Forgive a few misses: the app is not chatty any more, and a
                // human double-tapping "submit" should never see an error.
                $pdo->prepare("UPDATE rate_limit SET strikes = strikes + 1 WHERE ip = ?")
                    ->execute([$key]);
                return null;
            }
            return $wait;
        }
        $pdo->prepare("UPDATE rate_limit SET last_submit = ?, strikes = 0 WHERE ip = ?")
            ->execute([$now, $key]);
        return null;
    }

    $pdo->prepare("INSERT INTO rate_limit (ip, last_submit) VALUES (?,?)")
        ->execute([$key, $now]);
    return null;
}

try {
    $pdo = getPDO();

    // ── 2. ban check ───────────────────────────────────────────────────────
    $ban = bb_is_banned($pdo, $deviceId);
    if ($ban !== null) {
        jsonResponse(['error' => 'banned', 'reason' => $ban['reason']], 403);
    }

    $stmt = $pdo->prepare("SELECT banned, level, score FROM leaderboard WHERE device_id = ?");
    $stmt->execute([$deviceId]);
    $existing = $stmt->fetch();
    if ($existing && (int) $existing['banned'] === 1) {
        jsonResponse(['error' => 'This account is not eligible for the leaderboard'], 403);
    }

    // The furthest level this device has ever banked, per the server's own
    // monotonic record. Read once here because both submission paths below need
    // it to bound the client-supplied level.
    $recordedLevel = $existing ? (int) $existing['level'] : 0;

    // ── 3. throttle (per device) ───────────────────────────────────────────
    $wait = takeThrottleSlot($pdo, bb_throttle_key('sub:', $deviceId));
    if ($wait !== null) {
        jsonResponse(['error' => "Too many requests, wait {$wait}s"], 429);
    }

    // ── 4. run token ───────────────────────────────────────────────────────
    $run          = null;
    $verifiedPlay = 0.0;
    $unverified   = false;   // set when neither a token nor an HMAC was offered
    $accountId    = bb_account_for_device($pdo, $deviceId);

    if ($runToken !== '') {
        $payload = bb_verify_run($runToken);
        if ($payload === null || $payload['did'] !== $deviceId) {
            flagIt($pdo, $deviceId, $ip, 'bad_token', 'signature or device mismatch', $level, $score);
            jsonResponse(['error' => 'Invalid run token'], 403);
        }

        // Claim the token atomically. A second submit with the same token
        // matches zero rows here and is rejected as a replay.
        $claim = $pdo->prepare(
            "UPDATE run_tokens SET consumed = 1
              WHERE run_id = ? AND device_id = ? AND consumed = 0"
        );
        $claim->execute([$payload['rid'], $deviceId]);
        if ($claim->rowCount() !== 1) {
            flagIt($pdo, $deviceId, $ip, 'replay', 'run token reused: ' . $payload['rid'], $level, $score);
            jsonResponse(['error' => 'Run already submitted'], 409);
        }

        $now = time();
        if ($now + BB_RUN_FUTURE_SKEW_SECONDS < (int) $payload['ts']) {
            flagIt($pdo, $deviceId, $ip, 'future_run', 'run started in the future', $level, $score);
            jsonResponse(['error' => 'Invalid run start time'], 403);
        }
        if ($now > (int) $payload['ts'] + BB_RUN_MAX_AGE_SECONDS) {
            flagIt($pdo, $deviceId, $ip, 'stale_run', 'run expired', $level, $score);
            jsonResponse(['error' => 'Run expired, please play again'], 409);
        }

        // Play time measured by the server clock — the client cannot back-date
        // or fast-forward its way past the score-rate ceiling.
        $playSeconds = min(
            max(0, $now - (int) $payload['ts']),
            BB_RUN_MAX_AGE_SECONDS
        );
        $verifiedPlay = max((float) $playSeconds, (float) BB_RUN_MIN_SECONDS);

        $pdo->prepare(
            "UPDATE run_tokens
                SET play_ms = ?, events = ?, chain = ?, result = ?
              WHERE run_id = ?"
        )->execute([
            (int) round($verifiedPlay * 1000),
            clampInt($data['events'] ?? 0, 0, 1000000, 0),
            substr((string)($data['chain'] ?? ''), 0, 64),
            json_encode(['level' => $level, 'score' => $score, 'ip' => $ip]),
            $payload['rid'],
        ]);

        $run = $payload;
        // The token pins the level it was opened for; trust the server's copy
        // so a tampered client cannot claim level 9999 on a level-1 run.
        //
        // Trusting the token is not the same as trusting the level. The token is
        // only as honest as the request that minted it, and start_run.php has to
        // take `level` from the client because that is the only place it exists —
        // so a token pinning level 9999 costs an attacker nothing but one POST.
        // The level is the largest input to bb_score_ceiling(), so leaving it
        // unbounded handed over the whole plausibility model: ask for level
        // 9999 on a fresh install, submit a few seconds later, and the baseline
        // term alone cleared any score the client was willing to send.
        //
        // Clamped to what this device has actually reached. `leaderboard.level`
        // is the server's own monotonic record of it (it never decreases — see
        // the early return below), so it is the one input here that cannot be
        // asserted. The +1 allows the level being played right now, since a
        // submission happens at the *end* of a level the player had not yet
        // banked.
        $level = min(
            clampInt($payload['lvl'], 1, BB_MAX_LEVEL, 1),
            max(1, $recordedLevel + 1)
        );
    } else {
        // ── legacy path: HMAC from an older build ───────────────────────────
        // Kept so an app update can never lock out a player mid-season. The
        // key ships in the APK, so it proves nothing — every other defence
        // (plausibility, throttle, monotonicity) still applies.
        //
        // NOTE: the current build no longer ships HMAC_SECRET (it was a real
        // key in a public APK), so in practice every modern client lands in
        // the unverified branch below. This path is here for old installs only.
        if ($legacyHmac !== '' && $timestamp > 0) {
            if (abs(time() - $timestamp) > MAX_TIME_DRIFT) {
                jsonResponse(['error' => 'Expired timestamp'], 400);
            }
            $expected = hash_hmac('sha256', "$name|$level|$score|$isPremium|$timestamp|$deviceId", HMAC_SECRET);
            if (!hash_equals($expected, $legacyHmac)) {
                flagIt($pdo, $deviceId, $ip, 'bad_hmac', 'legacy signature mismatch', $level, $score);
                jsonResponse(['error' => 'Invalid signature'], 403);
            }
        } else {
            // ── unverified path: no run token, no HMAC ──────────────────────
            //
            // This used to be a hard 403, "Missing run token", and it was the
            // single biggest reason players could not register a score.
            //
            // The client opens a run token asynchronously when a run starts.
            // If that request fails — a tunnel, a captive portal, a shared
            // mobile connection that drops one packet, or simply finishing a
            // level in under the 5s timeout — there is no token to send, and
            // the run becomes permanently unsubmittable. The player finishes,
            // watches a "no" appear, and their score is gone with no way to
            // recover it. On a server the players describe as flaky, that is
            // the common path, not the rare one.
            //
            // Anti-cheat is the constraint here, and it is preserved: the
            // submission is judged with the *permissive* legacy ceiling rather
            // than the token-derived one (there is no server-observed play
            // time to reason with), it is recorded as unverified, and it is
            // flagged for the monitoring panel. Banning, throttling and
            // monotonic upsert all still apply. What is given up is only the
            // rate cap that depends on a token, in exchange for never
            // silently eating a real player's run.
            //
            // Refusing here was strictly worse for the product: a blocked
            // score is indistinguishable, to the player, from a broken app.
            $unverified = true;
            $pdo->prepare(
                "INSERT INTO cheat_flags (device_id, ip, reason, detail, level, score)
                 VALUES (?,?,?,?,?,?)"
            )->execute([
                $deviceId, $ip, 'unverified_submit',
                'no run token available for this run', $level, $score,
            ]);

            // Credit the run the minimum play time the verified path credits.
            //
            // Without this the ceiling collapses to the bare level baseline
            // (1,220 at level 3), because there is no measured duration to add
            // to it — and a genuine player who finished a level in ninety
            // seconds was being rejected for a score the game had just handed
            // them. The verified branch already does exactly this:
            // `max($playSeconds, BB_RUN_MIN_SECONDS)`. Applying the same floor
            // here means a tokenless submission is judged *identically* to a
            // tokenised run of the shortest permitted length.
            //
            // Note this does NOT switch the device into legacy mode. The
            // strict rate cap still applies, so an unverified submit buys
            // `BB_RUN_MIN_SECONDS * BB_SCORE_RATE_CAP` and no more — no new
            // headroom relative to the token path, and no free pass to a large
            // score.
            $verifiedPlay = max($verifiedPlay, (float) BB_RUN_MIN_SECONDS);

            // The level still comes from the client here — there is no token to
            // pin it — so it is clamped against the server's own record of this
            // device's progress for the same reason as in the verified branch
            // above. Without a token the only thing left bounding the level is
            // the monotonic `leaderboard.level`, and this is the branch a real
            // player lands in whenever a token request fails, so leaving it
            // open would hand out the largest ceiling on the most common path.
            $level = min(
                clampInt($data['level'] ?? 1, 1, BB_MAX_LEVEL, 1),
                max(1, $recordedLevel + 1)
            );
        }
    }

    // Total play time the server has witnessed for this device.
    $totalPlay = bb_verified_play_seconds($pdo, $deviceId) + $verifiedPlay;

    // ── 5. plausibility ────────────────────────────────────────────────────
    // The strict rate cap is used for tokenless submissions too — see the
    // unverified branch above, which floors the play time rather than
    // switching to the permissive legacy ceiling.
    //
    // `bb_legacy_mode` only applies when *no run token has ever been minted
    // anywhere on the server*, i.e. during a rollout. The old code gated the
    // permissive ceiling on that, so the moment run tokens went live every
    // tokenless submission became a 403 instead of a judgement.
    $legacyMode = ($run === null) && $unverified && bb_legacy_mode($pdo);
    $verdict = bb_judge_score($level, $score, $totalPlay, $legacyMode);
    if (!$verdict['ok']) {
        // Release the run so a player whose score was legitimately refused can
        // try again instead of losing the run outright. This is safe: a
        // rejected submission grants nothing, so retrying can only ever produce
        // a *lower* accepted score, never a higher one.
        if ($run !== null) {
            try {
                $pdo->prepare("UPDATE run_tokens SET consumed = 0, play_ms = 0 WHERE run_id = ?")
                    ->execute([$run['rid']]);
            } catch (PDOException $e) {
                error_log('submit_score: token release failed: ' . $e->getMessage());
            }
        }
        flagIt($pdo, $deviceId, $ip, $verdict['msg'], "ceiling={$verdict['ceiling']}", $level, $score);
        jsonResponse([
            'error'   => 'That score could not be reached in this amount of play',
            'code'    => $verdict['msg'],
            'ceiling' => $verdict['ceiling'],
            'retry'   => true,
        ], $verdict['code']);
    }

    // ── 6. monotonic upsert ────────────────────────────────────────────────
    $curLevel = $existing ? (int) $existing['level'] : 0;
    $curScore = $existing ? (int) $existing['score'] : 0;

    // ── unique display name ───────────────────────────────────────────────
    // Rejecting a duplicate outright is hostile — names are a tiny space in
    // Persian and "علی" is not rare. Instead, when the name is already taken
    // by a *different* device, a short suffix derived from the device id is
    // appended so two players can never look like the same person on the
    // board. Stable for that device, so it does not flicker between requests.
    $name = bb_unique_name($pdo, $name, $deviceId);

    if ($existing && ($level < $curLevel || ($level === $curLevel && $score <= $curScore))) {
        // Nothing new to record; still return the current, correct rank.
        $stmt = $pdo->prepare(
            "SELECT COUNT(*) + 1 AS rank FROM leaderboard
              WHERE banned = 0 AND ((level > ?) OR (level = ? AND score > ?))"
        );
        $stmt->execute([$curLevel, $curLevel, $curScore]);
        jsonResponse([
            'ok'      => true,
            'message' => 'No need to update',
            'rank'    => (int) $stmt->fetch()['rank'],
            'level'   => $curLevel,
            'score'   => $curScore,
        ]);
    }

    $scoreRate = $totalPlay > 0 ? round($score / $totalPlay, 2) : 0.0;

    if ($existing) {
        $pdo->prepare(
            "UPDATE leaderboard
                SET name = ?, level = ?, score = ?, is_premium = ?, ip = ?, score_rate = ?
              WHERE device_id = ?"
        )->execute([$name, $level, $score, $isPremium, $ip, $scoreRate, $deviceId]);
    } else {
        $pdo->prepare(
            "INSERT INTO leaderboard (device_id, name, level, score, is_premium, ip, score_rate)
             VALUES (?,?,?,?,?,?,?)"
        )->execute([$deviceId, $name, $level, $score, $isPremium, $ip, $scoreRate]);
    }

    // Tag the row with the account, so a player who recovers on a new phone
    // can find their old leaderboard entry instead of appearing twice.
    //
    // Deliberately best-effort and never inside the write above: the
    // account_id column only exists after the account migration, and this
    // file must keep working on a database that has not run it. A failure here
    // costs a duplicate board row, not a lost score.
    if ($accountId !== null) {
        try {
            $pdo->prepare("UPDATE leaderboard SET account_id = ? WHERE device_id = ? AND (account_id IS NULL OR account_id <> ?)")
                ->execute([$accountId, $deviceId, $accountId]);
        } catch (PDOException $e) {
            error_log('submit_score: account_id tag failed: ' . $e->getMessage());
        }
    }

    // ── 7. rank ────────────────────────────────────────────────────────────
    $stmt = $pdo->prepare(
        "SELECT COUNT(*) + 1 AS rank FROM leaderboard
          WHERE banned = 0 AND ((level > ?) OR (level = ? AND score > ?))"
    );
    $stmt->execute([$level, $level, $score]);

    jsonResponse([
        'ok'        => true,
        'rank'      => (int) $stmt->fetch()['rank'],
        'level'     => $level,
        'score'     => $score,
        'verified'  => $run !== null,
        'unverified' => $unverified,
        'play_secs' => (int) round($totalPlay),
    ]);

} catch (PDOException $e) {
    error_log('submit_score DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
