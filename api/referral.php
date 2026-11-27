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

/**
 * Whether the referral table has the owner/redeemer split.
 *
 * The original schema overloaded `used_by` as both roles, which made every
 * generated code look already-used. The migration adds owner_device,
 * redeemed_by and redeemed_at. Until it runs, fall back to the old behaviour
 * rather than turning the endpoint into a 500.
 */
function referral_has_owner_columns(PDO $pdo): bool {
    try {
        $pdo->query("SELECT owner_device, redeemed_by, redeemed_at FROM referrals LIMIT 0");
        return true;
    } catch (Throwable $e) {
        return false;
    }
}

try {
    $pdo = getPDO();
    $hasOwner = referral_has_owner_columns($pdo);

    // ── status: list my codes and anything waiting to be claimed ───────────
    if ($action === 'status') {
        $stmt = $hasOwner
            ? $pdo->prepare(
                "SELECT code, redeemed_by, coins_awarded, created_at
                   FROM referrals WHERE owner_device = ? ORDER BY created_at DESC LIMIT 20"
            )
            : $pdo->prepare(
                "SELECT code, used_by, coins_awarded, created_at
                   FROM referrals WHERE used_by = ? ORDER BY created_at DESC LIMIT 20"
            );
        $stmt->execute([$deviceId]);
        $codes = [];
        foreach ($stmt->fetchAll() as $r) {
            $used = $hasOwner
                ? ($r['redeemed_by'] !== null)
                : ((int) ($r['used_by'] !== null) === 1);
            $shareable = $hasOwner
                ? ($r['redeemed_by'] === null && (int) $r['coins_awarded'] === 0)
                : false;
            $codes[] = [
                'code' => $r['code'],
                'used' => $used,
                // The client reads this to decide which code to share. It
                // must be true only while the code has never paid out.
                'shareable' => $shareable,
            ];
        }

        $stmt = $pdo->prepare(
            "SELECT COALESCE(SUM(coins),0) AS c, COUNT(*) AS n
               FROM coin_grants WHERE device_id = ? AND claimed_at IS NULL"
        );
        $stmt->execute([$deviceId]);
        $g = $stmt->fetch();

        if ($hasOwner) {
            $stmt = $pdo->prepare(
                "SELECT COUNT(*) AS n FROM referrals
                  WHERE redeemed_by = ? AND redeemed_at IS NOT NULL
                    AND DATE(redeemed_at) = ?"
            );
        } else {
            $stmt = $pdo->prepare(
                "SELECT COUNT(*) AS n FROM referrals
                  WHERE used_by = ? AND DATE(created_at) = ?"
            );
        }
        $stmt->execute([$deviceId, date('Y-m-d')]);
        $redeemedToday = (int) $stmt->fetch()['n'];

        $stmt = $pdo->prepare(
            "SELECT COALESCE(SUM(coins),0) AS c FROM coin_grants
              WHERE device_id = ? AND claimed_at IS NOT NULL"
        );
        $stmt->execute([$deviceId]);
        $claimedTotal = (int) $stmt->fetch()['c'];

        jsonResponse([
            'ok'             => true,
            'codes'          => $codes,
            'pending_coins'  => (int) $g['c'],
            'pending_count'  => (int) $g['n'],
            'redeemed_today' => $redeemedToday,
            'max_per_day'    => REFERRAL_MAX_PER_DEVICE,
            'reward_coins'   => REFERRAL_REWARD_COINS,
            'claimed_total'  => $claimedTotal,
        ]);
    }

    // ── generate ───────────────────────────────────────────────────────────
    if ($action === 'generate') {
        $stmt = $hasOwner
            ? $pdo->prepare("SELECT code FROM referrals WHERE owner_device = ? LIMIT 1")
            : $pdo->prepare("SELECT code FROM referrals WHERE used_by = ? LIMIT 1");
        $stmt->execute([$deviceId]);
        $row = $stmt->fetch();
        if ($row) { jsonResponse(['ok' => true, 'code' => $row['code']]); }

        for ($attempt = 0; $attempt < 8; $attempt++) {
            $code = new_referral_code();
            try {
                if ($hasOwner) {
                    $pdo->prepare("INSERT INTO referrals (code, owner_device) VALUES (?,?)")
                        ->execute([$code, $deviceId]);
                } else {
                    $pdo->prepare("INSERT INTO referrals (code, used_by) VALUES (?,?)")
                        ->execute([$code, $deviceId]);
                }
                jsonResponse(['ok' => true, 'code' => $code]);
            } catch (PDOException $e) {
                if ($e->getCode() !== '23000') throw $e;
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
        if ($hasOwner) {
            $stmt = $pdo->prepare(
                "SELECT COUNT(*) AS n FROM referrals
                  WHERE redeemed_by = ? AND DATE(redeemed_at) = ? FOR UPDATE"
            );
        } else {
            $stmt = $pdo->prepare(
                "SELECT COUNT(*) AS n FROM referrals
                  WHERE used_by = ? AND DATE(created_at) = ? FOR UPDATE"
            );
        }
        $stmt->execute([$deviceId, $today]);
        if ((int) $stmt->fetch()['n'] >= REFERRAL_MAX_PER_DEVICE) {
            $pdo->rollBack();
            jsonResponse(['error' => 'Daily referral limit reached, try again tomorrow'], 429);
        }

        if ($hasOwner) {
            $stmt = $pdo->prepare(
                "SELECT id, owner_device, redeemed_by FROM referrals WHERE code = ? FOR UPDATE"
            );
        } else {
            $stmt = $pdo->prepare("SELECT id, used_by FROM referrals WHERE code = ? FOR UPDATE");
        }
        $stmt->execute([$code]);
        $ref = $stmt->fetch();

        if (!$ref) {
            $pdo->rollBack();
            jsonResponse(['error' => 'This code does not exist'], 404);
        }

        if ($hasOwner) {
            if ($ref['redeemed_by'] !== null) {
                $pdo->rollBack();
                jsonResponse(['error' => 'This code has already been used'], 409);
            }
            if ($ref['owner_device'] === $deviceId) {
                $pdo->rollBack();
                jsonResponse(['error' => 'You cannot use your own code'], 400);
            }
            $pdo->prepare(
                "UPDATE referrals SET redeemed_by = ?, redeemed_at = NOW(), coins_awarded = 1 WHERE id = ?"
            )->execute([$deviceId, $ref['id']]);

            // Both sides are paid, exactly once, in the same transaction.
            $pdo->prepare(
                "INSERT INTO coin_grants (device_id, coins, reason, ref_code) VALUES (?,?,?,?)"
            )->execute([$deviceId, REFERRAL_REWARD_COINS, 'referral', $code]);
            $owner = (string) ($ref['owner_device'] ?? '');
            if ($owner !== '') {
                $pdo->prepare(
                    "INSERT INTO coin_grants (device_id, coins, reason, ref_code) VALUES (?,?,?,?)"
                )->execute([$owner, REFERRAL_REWARD_COINS, 'referral', $code]);
            }
        } else {
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
            $pdo->prepare(
                "INSERT INTO coin_grants (device_id, coins, reason, ref_code) VALUES (?,?,?,?)"
            )->execute([$deviceId, REFERRAL_REWARD_COINS, 'referral', $code]);
        }

        $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    jsonResponse([
        'ok'    => true,
        'coins' => REFERRAL_REWARD_COINS,
        'code'  => $code,
    ]);

} catch (PDOException $e) {
    error_log('referral DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
