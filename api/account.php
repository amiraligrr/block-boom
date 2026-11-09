<?php
/**
 * account.php — real player accounts, and the thing that makes a factory
 * reset survivable.
 *
 * ── The problem this solves ───────────────────────────────────────────────
 * Identity used to be `device_id`, which is ANDROID_ID. ANDROID_ID changes on
 * a factory reset, on a new phone, and on some reinstalls. So a player who
 * reset their phone came back to a blank game: their leaderboard row was
 * stranded on the old id, their coins and level were gone, and their support
 * tickets had become unreachable — because tickets.php scoped every read by
 * device_id too. They could not even file a ticket *about* it.
 *
 * So identity now lives in `accounts`, keyed by a recovery code the player
 * keeps. device_id is demoted to "a device that is currently linked", which is
 * what it always really was.
 *
 * ── Actions ───────────────────────────────────────────────────────────────
 *   register  first launch: create the account, or return the existing one
 *             for this device (idempotent, so a retry is always safe)
 *   recover   new phone: exchange a recovery code for the account + progress
 *   sync      push local progress up, get the merged progress back
 *   progress  read only, for the settings screen
 *   unlink    detach a device (does NOT delete the account or its progress)
 *
 * ── What the recovery code is and is not ──────────────────────────────────
 * It is a 10-character code from an unambiguous alphabet, stored only as
 * SHA-256. It is a *bearer* secret: whoever holds it holds the account, which
 * is the same trust model as a password reset code. It is not an
 * authentication credential for anything else — score authenticity still comes
 * from the server-signed run token in anticheat.php, and a recovered account
 * still has every one of its submissions judged. Someone who recovers an
 * account to inflate a leaderboard score is still bounded by
 * bb_judge_score(); recovery is a continuity feature, not a cheat vector.
 *
 * ── The merge is deliberately boring ──────────────────────────────────────
 * Everything is merged with max(). Two devices on one account can then race
 * harmlessly: the union of their progress wins, and neither can lower the
 * other. The alternative — last-write-wins — means a restore onto a device
 * with stale data quietly deletes a real run.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

$data = readJsonBody();
$action = (string)($data['action'] ?? '');

/** Upper bounds on everything a client can put in a progress blob. */
const ACC_MAX_LEVEL = 100000;
const ACC_MAX_SCORE = 2000000000;
const ACC_MAX_COINS = 2000000000;
const ACC_MAX_JSON   = 8192;

/**
 * Alphabet for recovery codes.
 *
 * Crockford-ish: no 0/O, no 1/I, no 5/S, no 2/Z, no 8/B. Those are the pairs
 * a human transcribes wrongly when reading a code off a phone screen and
 * typing it on the new one, and this code is *always* transcribed by a human.
 */
const RECOVERY_ALPHABET = '34679ACDEFGHJKLMNPQRTUVWXY';

function acc_new_code(): string {
    $out = '';
    $max = strlen(RECOVERY_ALPHABET) - 1;
    for ($i = 0; $i < 10; $i++) {
        $out .= RECOVERY_ALPHABET[random_int(0, $max)];
    }
    return $out;
}

/**
 * Cleans up a code a human typed.
 *
 * Only two things are tolerated, and both are unambiguous:
 *
 *  1. **Case.** People type in whatever case their keyboard is on.
 *  2. **Separators and punctuation.** "qw 67 an-gl vc", "QW67ANGLVC" and
 *     "qw67anglvc" are the same code. The code is *always* transcribed by
 *     hand off a phone screen, so this is not a nicety.
 *
 * ── Why there is no "confusable character" substitution ───────────────────
 * There was one, mapping O→0, I→1, L→1, S→5, Z→2, B→8, Q→0 on the theory
 * that people mistake letters for digits. It destroys the code.
 *
 * RECOVERY_ALPHABET contains no 0, 1, 2, 5 or 8 precisely so those
 * substitutions are unambiguous — but it *does* contain L and Q. Mapping
 * L→1 and Q→0 therefore deleted two perfectly valid characters from the code,
 * and since the result was then filtered against the alphabet, the recovery
 * silently returned "this code is not valid" for codes the server had itself
 * just issued. Every code containing an L or a Q was unrecoverable.
 *
 * The one substitution worth keeping runs the other way, digit → letter, and
 * only where the letter is genuinely in the alphabet: a player reading a
 * capital L on a phone screen may well type 1. Nothing else is guessed, because
 * a wrong guess silently sends the player to a code that does not exist.
 */
function acc_normalize_code(string $raw): string {
    $s = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', $raw) ?? '');

    // '1' is the one genuine confusion: it reads as a capital I or L, and L is
    // in the alphabet. Nothing in RECOVERY_ALPHABET is confusable with 0/2/5/8.
    $s = strtr($s, ['1' => 'L']);

    // Drop anything still outside the alphabet rather than guessing at it.
    $s = preg_replace('/[^' . RECOVERY_ALPHABET . ']/', '', $s) ?? '';
    return substr($s, 0, 10);
}

function acc_hash(string $code): string {
    return hash('sha256', 'bb-account-v1:' . $code);
}

function acc_device_id(array $d): string {
    $id = bb_normalize_device_id((string)($d['device_id'] ?? ''));
    if (strlen($id) < 5) jsonResponse(['error' => 'Invalid device'], 400);
    return $id;
}

/**
 * Reads a JSON value that may already be decoded, or a raw JSON string.
 *
 * ── Why this takes mixed, not ?string ─────────────────────────────────────
 * It was typed `?string`, and that was a hard 500 on every single progress
 * sync. This function has two callers with two different input shapes:
 *
 *   - acc_progress_out() reads the `upgrades`/`meta` COLUMNS, which arrive as
 *     JSON text, so ?string was right there.
 *   - acc_progress_in() reads the same two keys out of the *request body*,
 *     which readJsonBody() has already run through json_decode(…, true). Those
 *     are therefore already PHP arrays.
 *
 * The array caller hit the `?string` type declaration and PHP threw
 * TypeError: acc_decode(): Argument #1 ($json) must be of type ?string, array
 * given — an uncaught fatal, so `action=sync` returned an empty HTTP 500 for
 * every player, on every sync, from the moment the client started sending
 * those objects. Account cloud-save was silently dead while every health check
 * stayed green, because nothing else in the endpoint was reached.
 *
 * The lesson is that the request path and the column path have different
 * shapes and must not share a narrower signature than the widest one.
 */
function acc_decode($json): array {
    // Already decoded — the request-body path.
    if (is_array($json)) return $json;
    // A JSON scalar/object can also come back as a decoded scalar, which is
    // not a map and must not be treated as one.
    if ($json === null || $json === '' || $json === false) return [];
    if (!is_string($json)) return [];
    $v = json_decode($json, true);
    return is_array($v) ? $v : [];
}

/** Clamps and sanitises the numeric half of a progress blob. */
function acc_progress_in(array $in): array {
    return [
        'level'       => clampInt($in['level']       ?? 1, 1, ACC_MAX_LEVEL, 1),
        'total_score' => clampInt($in['total_score'] ?? 0, 0, ACC_MAX_SCORE, 0),
        'coins'       => clampInt($in['coins']       ?? 0, 0, ACC_MAX_COINS, 0),
        'upgrades'    => acc_decode($in['upgrades'] ?? null),
        'meta'        => acc_decode($in['meta'] ?? null),
    ];
}

/**
 * Upserts a device's link to an account and returns the account row.
 */
function acc_link_device(PDO $pdo, int $accountId, string $deviceId): void {
    $pdo->prepare(
        "INSERT INTO device_links (account_id, device_id) VALUES (?,?)
         ON DUPLICATE KEY UPDATE account_id = VALUES(account_id), last_sync_at = NOW()"
    )->execute([$accountId, $deviceId]);
}

/** The account id this device is linked to, or null. */
function acc_account_for_device(PDO $pdo, string $deviceId): ?int {
    return bb_account_for_device($pdo, $deviceId);
}

/** Serialises the stored progress for a response body. */
function acc_progress_out(PDO $pdo, int $accountId): array {
    $st = $pdo->prepare(
        "SELECT level, total_score, coins, upgrades, meta, updated_at
           FROM account_progress WHERE account_id = ?"
    );
    $st->execute([$accountId]);
    $r = $st->fetch();
    if (!$r) {
        return [
            'level' => 1, 'total_score' => 0, 'coins' => 0,
            'upgrades' => [], 'meta' => [], 'updated_at' => null,
        ];
    }
    return [
        'level'       => (int) $r['level'],
        'total_score' => (int) $r['total_score'],
        'coins'       => (int) $r['coins'],
        'upgrades'    => acc_decode($r['upgrades']),
        'meta'        => acc_decode($r['meta']),
        'updated_at'  => $r['updated_at'],
    ];
}

/**
 * max()-merges two progress blobs, element-wise for the nested maps.
 *
 * Element-wise matters for upgrades: a player who bought damage on the old
 * phone and fire-rate on the new one must end up with both, not with whichever
 * blob happened to be written last.
 */
function acc_merge(array $a, array $b): array {
    $up = [];
    foreach (($a['upgrades'] ?? []) as $k => $v) {
        $up[(string) $k] = is_numeric($v) ? $v : 0;
    }
    foreach (($b['upgrades'] ?? []) as $k => $v) {
        $k = (string) $k;
        if (!isset($up[$k]) || (!is_numeric($up[$k]) || $v > $up[$k])) {
            $up[$k] = is_numeric($v) ? $v : 0;
        }
    }

    // `meta` holds things that are *not* accumulators (which skin is selected,
    // whether onboarding was seen). Those are last-write-wins on purpose: they
    // are preferences, and max() on a skin id would be nonsense.
    //
    // The local side ($a) wins. The reverse order let a stale server snapshot
    // overwrite the device the player is actually holding: changing a skin
    // offline and then syncing would silently flip it back.
    $meta = array_merge(
        is_array($b['meta'] ?? null) ? $b['meta'] : [],
        is_array($a['meta'] ?? null) ? $a['meta'] : []
    );

    return [
        'level'       => max((int) ($a['level'] ?? 1), (int) ($b['level'] ?? 1)),
        'total_score' => max((int) ($a['total_score'] ?? 0), (int) ($b['total_score'] ?? 0)),
        'coins'       => max((int) ($a['coins'] ?? 0), (int) ($b['coins'] ?? 0)),
        'upgrades'    => $up,
        'meta'        => $meta,
    ];
}

function acc_store_progress(PDO $pdo, int $accountId, array $p): void {
    $st = $pdo->prepare(
        "INSERT INTO account_progress
            (account_id, level, total_score, coins, upgrades, meta)
         VALUES (?,?,?,?,?,?)
         ON DUPLICATE KEY UPDATE
            level       = VALUES(level),
            total_score = VALUES(total_score),
            coins       = VALUES(coins),
            upgrades    = VALUES(upgrades),
            meta        = VALUES(meta)"
    );
    $st->execute([
        $accountId,
        $p['level'],
        $p['total_score'],
        $p['coins'],
        json_encode($p['upgrades'], JSON_UNESCAPED_UNICODE),
        json_encode($p['meta'],    JSON_UNESCAPED_UNICODE),
    ]);
}

/**
 * Daily snapshot input.
 *
 * The client sends cumulative counters for its local day, so every write is a
 * GREATEST() upsert. A retry, a duplicate request or a process restart can
 * therefore never lower a row, and a player who plays offline for an hour does
 * not lose the hour when the connection returns.
 *
 * The row is bucketed by the SERVER's date, not the client's. A phone with a
 * wrong clock (or simply a different timezone) must not be able to write a
 * "today" row into next week and make the admin charts jump.
 */
function acc_daily_in(array $d): ?array {
    if (!is_array($d) || $d === []) return null;
    $clamp = static function ($v, int $max = 2000000000): int {
        return max(0, min($max, (int) $v));
    };
    return [
        'level'          => max(1, min(ACC_MAX_LEVEL, (int) ($d['level'] ?? 1))),
        'total_score'    => $clamp($d['total_score'] ?? 0, ACC_MAX_SCORE),
        'coins'          => $clamp($d['coins'] ?? 0, ACC_MAX_COINS),
        'runs'           => $clamp($d['runs'] ?? 0),
        'levels_cleared' => $clamp($d['levels_cleared'] ?? 0),
        'blocks'         => $clamp($d['blocks'] ?? 0),
        'shots'          => $clamp($d['shots'] ?? 0),
        'best_combo'     => $clamp($d['best_combo'] ?? 0, 65535),
        'play_seconds'   => $clamp($d['play_seconds'] ?? 0),
        'coins_earned'   => $clamp($d['coins_earned'] ?? 0, ACC_MAX_COINS),
    ];
}

function acc_store_daily(PDO $pdo, int $accountId, ?array $daily, string $appVersion): void {
    if ($daily === null) return;
    $st = $pdo->prepare(
        "INSERT INTO player_daily
            (account_id, day, level, total_score, coins, runs, levels_cleared,
             blocks, shots, best_combo, play_seconds, coins_earned, app_version)
         VALUES (?, CURDATE(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
            level          = GREATEST(level,          VALUES(level)),
            total_score    = GREATEST(total_score,    VALUES(total_score)),
            coins          = GREATEST(coins,          VALUES(coins)),
            runs           = GREATEST(runs,           VALUES(runs)),
            levels_cleared = GREATEST(levels_cleared, VALUES(levels_cleared)),
            blocks         = GREATEST(blocks,         VALUES(blocks)),
            shots          = GREATEST(shots,          VALUES(shots)),
            best_combo     = GREATEST(best_combo,     VALUES(best_combo)),
            play_seconds   = GREATEST(play_seconds,   VALUES(play_seconds)),
            coins_earned   = GREATEST(coins_earned,   VALUES(coins_earned)),
            app_version    = VALUES(app_version)"
    );
    $st->execute([
        $accountId,
        $daily['level'],
        $daily['total_score'],
        $daily['coins'],
        $daily['runs'],
        $daily['levels_cleared'],
        $daily['blocks'],
        $daily['shots'],
        $daily['best_combo'],
        $daily['play_seconds'],
        $daily['coins_earned'],
        $appVersion,
    ]);
}

function acc_clean_install_id($raw): string {
    $s = preg_replace('/[^A-Za-z0-9_-]/', '', (string) $raw) ?? '';
    return substr($s, 0, 64);
}

function acc_clean_version($raw): string {
    $s = preg_replace('/[^A-Za-z0-9._-]/', '', (string) $raw) ?? '';
    return substr($s, 0, 24);
}

function acc_client_ip(): string {
    $ip = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
    return substr($ip, 0, 45);
}

/**
 * Keeps the accounts row's quick-read columns current.
 *
 * The players page needs level/score/last-seen without joining the JSON
 * progress table for every row. These are denormalised on purpose.
 */
function acc_touch_account(
    PDO $pdo, int $accountId, string $installId, string $appVersion,
    int $level, int $score
): void {
    $pdo->prepare(
        "UPDATE accounts
            SET last_seen_at = NOW(),
                install_id = ?,
                app_version = ?,
                last_ip = ?,
                last_level = ?,
                last_score = ?
          WHERE id = ?"
    )->execute([
        $installId, $appVersion, acc_client_ip(), $level, $score, $accountId
    ]);
}

/**
 * Records one install of the game.
 *
 * ANDROID_ID survives an ordinary reinstall, so the account is found before
 * this runs. The install_id is what makes "same player reinstalled" visible
 * even when the device key is unchanged. A new install_id increments
 * install_count; a repeated sync does not.
 */
function acc_track_install(
    PDO $pdo, int $accountId, string $deviceId,
    string $installId, string $appVersion
): void {
    if ($installId === '') return;
    $st = $pdo->prepare(
        "INSERT INTO player_installs (account_id, install_id, device_id, app_version)
         VALUES (?,?,?,?)
         ON DUPLICATE KEY UPDATE
            account_id = VALUES(account_id),
            device_id  = VALUES(device_id),
            app_version = VALUES(app_version),
            last_seen_at = NOW()"
    );
    $st->execute([$accountId, $installId, $deviceId, $appVersion]);
    if ($st->rowCount() === 1) {
        $pdo->prepare("UPDATE accounts SET install_count = install_count + 1 WHERE id = ?")
            ->execute([$accountId]);
    }
}

/** Issues a fresh recovery code for an existing account. */
function acc_rotate_code(PDO $pdo, int $accountId): string {
    for ($attempt = 0; $attempt < 8; $attempt++) {
        $code = acc_new_code();
        try {
            $pdo->prepare(
                "UPDATE accounts SET recovery_hash = ?, recovery_hint = ? WHERE id = ?"
            )->execute([acc_hash($code), substr($code, -4), $accountId]);
            return $code;
        } catch (Throwable $e) {
            if (str_contains($e->getMessage(), 'uniq_recovery')) continue;
            throw $e;
        }
    }
    throw new RuntimeException('Could not allocate a recovery code');
}

try {
    $pdo = getPDO();
    $deviceId = acc_device_id($data);
    $installId = acc_clean_install_id($data['install_id'] ?? '');
    $appVersion = acc_clean_version($data['app_version'] ?? '');
    $daily = acc_daily_in($data['daily'] ?? []);

    // ── register ─────────────────────────────────────────────────────────
    // Idempotent by design: the client calls this on every launch, and a
    // dropped response must never mint a second account for the same player.
    if ($action === 'register') {
        $existingId = acc_account_for_device($pdo, $deviceId);
        if ($existingId !== null) {
            $st = $pdo->prepare(
                "SELECT a.id, a.recovery_hint, a.player_name, a.created_at
                   FROM accounts a WHERE a.id = ?"
            );
            $st->execute([$existingId]);
            $acc = $st->fetch();
            if ($acc) {
                $existingProgress = acc_progress_out($pdo, $existingId);
                $name = mb_substr(trim((string)($data['player_name'] ?? '')), 0, 32, 'UTF-8');
                if ($name !== '' && (string) $acc['player_name'] === '') {
                    $pdo->prepare("UPDATE accounts SET player_name = ? WHERE id = ?")
                        ->execute([$name, $existingId]);
                    $acc['player_name'] = $name;
                }
                acc_touch_account(
                    $pdo, $existingId, $installId, $appVersion,
                    (int) $existingProgress['level'], (int) $existingProgress['total_score']
                );
                acc_track_install($pdo, $existingId, $deviceId, $installId, $appVersion);
                acc_store_daily($pdo, $existingId, $daily, $appVersion);
                jsonResponse([
                    'ok'          => true,
                    'account_id'  => (int) $acc['id'],
                    'created'     => false,
                    // The client already has the real code; the server stores
                    // only its hash, so it genuinely cannot hand it back.
                    'recovery'    => null,
                    'recovery_hint' => $acc['recovery_hint'],
                    'player_name' => $acc['player_name'],
                    'created_at'  => $acc['created_at'],
                    'progress'    => $existingProgress,
                ]);
            }
        }

        // A handful of collisions is fine and is not worth a retry loop: a
        // duplicate here would mean a duplicate account, and the client
        // re-calls register on its next launch anyway.
        for ($attempt = 0; $attempt < 5; $attempt++) {
            $code = acc_new_code();
            $hash = acc_hash($code);
            try {
                $pdo->beginTransaction();
                $name = mb_substr(trim((string)($data['player_name'] ?? '')), 0, 32, 'UTF-8');
                $initial = acc_progress_in($data);
                $pdo->prepare(
                    "INSERT INTO accounts
                        (recovery_hash, recovery_hint, player_name, install_id,
                         app_version, last_ip, last_level, last_score, install_count)
                     VALUES (?,?,?,?,?,?,?,?,0)"
                )->execute([
                    $hash,
                    substr($code, -4),
                    $name,
                    $installId,
                    $appVersion,
                    acc_client_ip(),
                    (int) $initial['level'],
                    (int) $initial['total_score'],
                ]);
                $newId = (int) $pdo->lastInsertId();
                acc_link_device($pdo, $newId, $deviceId);
                acc_store_progress($pdo, $newId, $initial);
                acc_track_install($pdo, $newId, $deviceId, $installId, $appVersion);
                acc_store_daily($pdo, $newId, $daily, $appVersion);
                $pdo->commit();
            } catch (Throwable $e) {
                if ($pdo->inTransaction()) $pdo->rollBack();
                if (str_contains($e->getMessage(), 'uniq_recovery')) continue;  // code collision, retry
                throw $e;
            }
            jsonResponse([
                'ok'            => true,
                'account_id'    => $newId,
                'created'       => true,
                // The one and only time the code is ever transmitted. After
                // this the client keeps it in its own prefs.
                'recovery'      => $code,
                'recovery_hint' => substr($code, -4),
                'player_name'   => '',
                'progress'      => acc_progress_out($pdo, $newId),
            ]);
        }
        jsonResponse(['error' => 'Could not allocate a recovery code, try again'], 500);
    }

    // ── recover ──────────────────────────────────────────────────────────
    // The reset-phone path: paste the old code here and the account follows
    // the player to the new device.
    if ($action === 'recover') {
        $code = acc_normalize_code((string)($data['recovery'] ?? ''));
        if (strlen($code) < 10) {
            jsonResponse(['error' => 'کد بازیابی باید ۱۰ رقم باشد'], 400);
        }

        $st = $pdo->prepare(
            "SELECT id, recovery_hint, player_name, created_at FROM accounts
              WHERE recovery_hash = ?"
        );
        $st->execute([acc_hash($code)]);
        $acc = $st->fetch();
        if (!$acc) jsonResponse(['error' => 'این کد بازیابی معتبر نیست'], 404);

        $accountId = (int) $acc['id'];

        // This device may already belong to a *different* account. Detach it
        // first, otherwise the UNIQUE on device_id would reject the link and
        // the player would recover the account and still be logged into the
        // wrong one — the exact confusion this feature exists to remove.
        acc_link_device($pdo, $accountId, $deviceId);

        // Carry the recovered name forward when the new device has none yet.
        $name = mb_substr(trim((string)($data['player_name'] ?? '')), 0, 32, 'UTF-8');
        if ($name !== '') {
            $pdo->prepare(
                "UPDATE accounts SET player_name = ? WHERE id = ? AND player_name = ''"
            )->execute([$name, $accountId]);
        }

        $recProgress = acc_progress_out($pdo, $accountId);
        acc_touch_account(
            $pdo, $accountId, $installId, $appVersion,
            (int) $recProgress['level'], (int) $recProgress['total_score']
        );
        acc_track_install($pdo, $accountId, $deviceId, $installId, $appVersion);
        acc_store_daily($pdo, $accountId, $daily, $appVersion);

        // The client needs the *code* to keep showing it in settings. We only
        // stored a hash, so the code the player typed is the only copy that
        // will ever exist — echo it back. It came from the player, so this is
        // not a disclosure.
        jsonResponse([
            'ok'            => true,
            'account_id'    => $accountId,
            'recovery'      => $code,
            'recovery_hint' => $acc['recovery_hint'],
            'player_name'   => $acc['player_name'] !== '' ? $acc['player_name'] : $name,
            'created_at'    => $acc['created_at'],
            'progress'      => acc_progress_out($pdo, $accountId),
        ]);
    }

    // ── sync / progress ──────────────────────────────────────────────────
    if ($action === 'sync' || $action === 'progress') {
        $accountId = acc_account_for_device($pdo, $deviceId);
        if ($accountId === null) {
            jsonResponse(['error' => 'No account for this device', 'code' => 'no_account'], 404);
        }
        $pdo->prepare("UPDATE accounts SET last_seen_at = NOW() WHERE id = ?")
            ->execute([$accountId]);

        if ($action === 'progress') {
            jsonResponse([
                'ok'         => true,
                'account_id' => $accountId,
                'progress'   => acc_progress_out($pdo, $accountId),
            ]);
        }

        $mine   = acc_progress_in($data);
        $theirs = acc_progress_out($pdo, $accountId);
        $merged = acc_merge($mine, $theirs);
        acc_store_progress($pdo, $accountId, $merged);
        $pdo->prepare("UPDATE device_links SET last_sync_at = NOW() WHERE device_id = ?")
            ->execute([$deviceId]);

        $name = mb_substr(trim((string)($data['player_name'] ?? '')), 0, 32, 'UTF-8');
        if ($name !== '') {
            $pdo->prepare("UPDATE accounts SET player_name = ? WHERE id = ?")
                ->execute([$name, $accountId]);
        }

        acc_touch_account(
            $pdo, $accountId, $installId, $appVersion,
            (int) $merged['level'], (int) $merged['total_score']
        );
        acc_track_install($pdo, $accountId, $deviceId, $installId, $appVersion);
        acc_store_daily($pdo, $accountId, $daily, $appVersion);

        jsonResponse([
            'ok'         => true,
            'account_id' => $accountId,
            'progress'   => acc_progress_out($pdo, $accountId),
            // True when the server had something the client did not, i.e. this
            // sync actually restored progress. The client shows a different
            // message for that case, because "saved" and "restored" are very
            // different news for someone who just reset their phone.
            'restored'   => ($theirs['total_score'] > $mine['total_score']
                          || $theirs['level'] > $mine['level']),
        ]);
    }

    // ── rotate ───────────────────────────────────────────────────────────
    // Issues a fresh recovery code for the account this device is linked to.
    //
    // Needed for two real cases: an account created before the client stored
    // codes (the server only has the hash and cannot return it), and a player
    // who thinks their code leaked. The old code stops working immediately.
    if ($action === 'rotate') {
        $accountId = acc_account_for_device($pdo, $deviceId);
        if ($accountId === null) {
            jsonResponse(['error' => 'No account for this device', 'code' => 'no_account'], 404);
        }
        $code = acc_rotate_code($pdo, $accountId);
        acc_touch_account(
            $pdo, $accountId, $installId, $appVersion,
            (int) ($data['level'] ?? 1), (int) ($data['total_score'] ?? 0)
        );
        jsonResponse([
            'ok'            => true,
            'account_id'    => $accountId,
            'recovery'      => $code,
            'recovery_hint' => substr($code, -4),
        ]);
    }

    // ── unlink ───────────────────────────────────────────────────────────
    // Forgets this device. Deliberately does NOT delete the account or its
    // progress: "log out of this phone" and "delete my account" are different
    // requests and conflating them loses a real run.
    if ($action === 'unlink') {
        $pdo->prepare("DELETE FROM device_links WHERE device_id = ?")->execute([$deviceId]);
        jsonResponse(['ok' => true]);
    }

    jsonResponse(['error' => 'Unknown action'], 400);

} catch (PDOException $e) {
    error_log('account error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
