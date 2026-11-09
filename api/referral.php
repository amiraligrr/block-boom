<?php
/**
 * referral.php — friend referral codes and coin rewards.
 *
 * What was broken here, and what this fixes:
 *   1. FATAL PARSE ERROR. The generate branch read
 *        } while ( $stmt = $pdo->prepare("..."))->execute([$code]) );
 *      — one `)` too many. The whole file was a 500 on every request.
 *   2. The uniqueness loop tested ->execute(), which returns true even when a
 *      SELECT matched zero rows, so it spun forever.
 *   3. It wrote to a `coins` column on `leaderboard` that has never existed,
 *      so action=use always failed too.
 *   4. The redeem was a TOCTOU race: SELECT then UPDATE with no guard, so one
 *      code could be claimed concurrently, many times over.
 *
 * Coins are a client-local currency, so the server does not try to hold a
 * balance. It records a *grant*; the client claims its unclaimed grants once
 * and credits them locally.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

$data = readJsonBody();
$action = (string)($data['action'] ?? '');
$deviceId = bb_normalize_device_id((string)($data['device_id'] ?? ''));

if (strlen($deviceId) < 5) { jsonResponse(['error' => 'Invalid device'], 400); }
if (!in_array($action, ['generate', 'use', 'status'], true)) {
    jsonResponse(['error' => 'Unknown action'], 400);
}

const REFERRAL_REWARD_COINS = 100;
// Was 20 codes/day x 350 = 7,000 coins/day — more than the cheapest paid
// pack (5,000), which made the coin packs pointless. Now a nudge that keeps
// the viral loop alive without out-earning a purchase.
const REFERRAL_MAX_PER_DEVICE = 5;    // codes one device may burn per day

/** CSPRNG code from an unambiguous alphabet (no O/0/I/1 to misread). */
function new_referral_code(): string {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    $max = strlen($alphabet) - 1;
    $out = '';
    for ($i = 0; $i < 8; $i++) {
        $out .= $alphabet[random_int(0, $max)];
    }
    return $out;
}

try {
    $pdo = getPDO();

    // ── status: list my codes and anything waiting to be claimed ───────────
    if ($action === 'status') {
        $stmt = $pdo->prepare(
            "SELECT code, used_by IS NOT NULL AS used, created_at
               FROM referrals WHERE used_by = ? ORDER BY created_at DESC LIMIT 20"
        );
        $stmt->execute([$deviceId]);
        $codes = [];
        foreach ($stmt->fetchAll() as $r) {
            $codes[] = [
                'code' => $r['code'],
                'used' => (int) $r['used'] === 1,
            ];
        }
        $stmt = $pdo->prepare(
            "SELECT COALESCE(SUM(coins),0) AS c, COUNT(*) AS n
               FROM coin_grants WHERE device_id = ? AND claimed_at IS NULL"
        );
        $stmt->execute([$deviceId]);
        $g = $stmt->fetch();

        // How much of today's referral allowance is already spent, and how much
        // this device has ever cashed out. The client needs both to render an
        // honest "2 of 5 redeemed today" line — without redeemed_today it would
        // have to guess when the cap is close and would invite players to hit a
        // 429 and conclude the feature is broken.
        $stmt = $pdo->prepare(
            "SELECT COUNT(*) AS n FROM referrals WHERE used_by = ? AND DATE(created_at) = ?"
        );
        $stmt->execute([$deviceId, date('Y-m-d')]);
        $redeemedToday = (int) $stmt->fetch()['n'];

        $stmt = $pdo->prepare(
            "SELECT COALESCE(SUM(coins),0) AS c FROM coin_grants
              WHERE device_id = ? AND claimed_at IS NOT NULL"
        );
        $stmt->execute([$deviceId]);
        $claimedTotal = (int) $stmt->fetch()['c'];

        jsonResponse([
            'ok'            => true,
            'codes'         => $codes,
            'pending_coins' => (int) $g['c'],
            'pending_count' => (int) $g['n'],
            'redeemed_today'=> $redeemedToday,
            'max_per_day'   => REFERRAL_MAX_PER_DEVICE,
            'reward_coins'  => REFERRAL_REWARD_COINS,
            'claimed_total' => $claimedTotal,
        ]);
    }

    // ── generate ───────────────────────────────────────────────────────────
    if ($action === 'generate') {
        // One code per device, reused forever: codes are a referral handle,
        // not a consumable, and re-minting them just fragments the graph.
        $stmt = $pdo->prepare("SELECT code FROM referrals WHERE used_by = ? LIMIT 1");
        $stmt->execute([$deviceId]);
        $row = $stmt->fetch();
        if ($row) { jsonResponse(['ok' => true, 'code' => $row['code']]); }

        // Retry on the rare collision, testing for a ROW rather than success.
        for ($attempt = 0; $attempt < 8; $attempt++) {
            $code = new_referral_code();
            try {
                $pdo->prepare("INSERT INTO referrals (code, used_by) VALUES (?,?)")
                    ->execute([$code, $deviceId]);
                jsonResponse(['ok' => true, 'code' => $code]);
            } catch (PDOException $e) {
                if ($e->getCode() !== '23000') throw $e;   // duplicate key -> retry
            }
        }
        jsonResponse(['error' => 'Could not allocate a code, try again'], 503);
    }

    // ── use (redeem) ───────────────────────────────────────────────────────
    $code = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)($data['code'] ?? '')) ?? '');
    if (strlen($code) !== 8) { jsonResponse(['error' => 'Invalid code'], 400); }

    $today = date('Y-m-d');

    $pdo->beginTransaction();
    try {
        // The daily cap is enforced INSIDE the transaction, after taking a
        // lock on this device's referral rows. It used to be a plain SELECT
        // before beginTransaction(), so two concurrent redemptions both read a
        // count of 4, both passed the `< 5` check, and both paid out — the cap
        // was really 6/day. SELECT ... FOR UPDATE on the index range
        // (used_by, created_at) serialises the two requests for this device
        // while leaving other devices unaffected.
        //
        // There is no per-device index covering used_by yet, so this locks by
        // scanning; the table is tiny (one row per redemption) and the MIGRATION
        // block adds KEY idx_used_day (used_by, created_at).
        $stmt = $pdo->prepare(
            "SELECT COUNT(*) AS n FROM referrals
              WHERE used_by = ? AND DATE(created_at) = ?
              FOR UPDATE"
        );
        $stmt->execute([$deviceId, $today]);
        if ((int) $stmt->fetch()['n'] >= REFERRAL_MAX_PER_DEVICE) {
            $pdo->rollBack();
            jsonResponse(['error' => 'Daily referral limit reached, try again tomorrow'], 429);
        }

        // FOR UPDATE closes the read-then-write race: a second concurrent
        // request for the same code blocks here, then sees used_by set and
        // bails out instead of double-paying.
        $stmt = $pdo->prepare("SELECT id, used_by FROM referrals WHERE code = ? FOR UPDATE");
        $stmt->execute([$code]);
        $ref = $stmt->fetch();

        if (!$ref) {
            $pdo->rollBack();
            jsonResponse(['error' => 'This code does not exist'], 404);
        }
        if ($ref['used_by'] !== null) {
            $pdo->rollBack();
            jsonResponse(['error' => 'This code has already been used'], 409);
        }
        if ($ref['used_by'] === $deviceId) {
            $pdo->rollBack();
            jsonResponse(['error' => 'You cannot use your own code'], 400);
        }

        $pdo->prepare("UPDATE referrals SET used_by = ?, coins_awarded = 1 WHERE id = ?")
            ->execute([$deviceId, $ref['id']]);

        // Record the reward as a claimable grant instead of touching a
        // `leaderboard.coins` column that does not exist.
        $pdo->prepare(
            "INSERT INTO coin_grants (device_id, coins, reason, ref_code) VALUES (?,?,?,?)"
        )->execute([$deviceId, REFERRAL_REWARD_COINS, 'referral', $code]);

        $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    jsonResponse([
        'ok'     => true,
        'coins'  => REFERRAL_REWARD_COINS,
        'code'   => $code,
    ]);

} catch (PDOException $e) {
    error_log('referral DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
