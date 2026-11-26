<?php
/**
 * Support tickets.
 *
 * Four small endpoints rather than one with an `action` field, so each has a
 * single, obvious shape and the admin panel can use the same tables directly
 * (the panel and this API share one database on the same host).
 *
 * Every read is scoped by device_id and ownership is checked on each write, so
 * one player cannot read or reply to another's ticket by guessing an id.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }

$data = readJsonBody();
$action = (string)($data['action'] ?? '');

const TICKET_MAX_PER_DAY = 5;
const MSG_MAX_LEN = 2000;
const SUBJECT_MAX_LEN = 160;

function ticket_device(array $d): string {
    $id = bb_normalize_device_id((string)($d['device_id'] ?? ''));
    if (strlen($id) < 5) jsonResponse(['error' => 'Invalid device'], 400);
    return $id;
}

/**
 * The set of ticket ids this request is allowed to see or touch.
 *
 * ── Why this is not just device_id anymore ────────────────────────────────
 * Every read and write here used to be scoped by device_id alone. device_id is
 * ANDROID_ID, which changes on a factory reset — so a player who reset their
 * phone lost access to their own support history, and could not even file a
 * ticket about having lost it, because their previous threads were invisible.
 * That is precisely the "nothing registers and I can't send a ticket" report.
 *
 * So ownership is now "this device *or* the account it is linked to". An old
 * install that predates accounts still works, because bb_account_for_device
 * returns null and the set is just the one device.
 *
 * Ownership is still enforced: a ticket is reachable only by a device on the
 * owning account, so guessing an id buys nothing. The SQL takes the ids as an
 * IN (...) list built from these two sources, never from raw input.
 */
function ticket_owned_ids(PDO $pdo, string $deviceId): array {
    $ids = [$deviceId];
    $accountId = bb_account_for_device($pdo, $deviceId);
    if ($accountId !== null) {
        try {
            $st = $pdo->prepare("SELECT DISTINCT device_id FROM device_links WHERE account_id = ?");
            $st->execute([$accountId]);
            foreach ($st->fetchAll() as $r) {
                $did = (string) $r['device_id'];
                if ($did !== '' && !in_array($did, $ids, true)) $ids[] = $did;
            }
        } catch (PDOException $e) {
            // device_links missing (pre-migration). The device-only scope above
            // is still correct, just narrower.
        }
    }
    return $ids;
}

/** Renders an id list for a placeholder IN () clause. @return array{0:string,1:array} */
function ticket_in_clause(array $ids): array {
    $ph = implode(',', array_fill(0, count($ids), '?'));
    return ["($ph)", $ids];
}

try {
    $pdo = getPDO();

    // ── create ─────────────────────────────────────────────────────────────
    if ($action === 'create') {
        $deviceId = ticket_device($data);
        $subject  = mb_substr(trim((string)($data['subject'] ?? '')), 0, SUBJECT_MAX_LEN, 'UTF-8');
        $body     = mb_substr(trim((string)($data['body'] ?? '')), 0, MSG_MAX_LEN, 'UTF-8');
        $category = (string)($data['category'] ?? 'other');
        $name     = mb_substr(trim((string)($data['name'] ?? '')), 0, 32, 'UTF-8');

        if ($subject === '' || $body === '') {
            jsonResponse(['error' => 'موضوع و متن پیام الزامی است'], 400);
        }
        if (!in_array($category, ['bug', 'payment', 'account', 'cheat', 'other'], true)) {
            $category = 'other';
        }

        // Rate limit: a support form with no cap is a spam vector.
        $st = $pdo->prepare("SELECT COUNT(*) c FROM tickets WHERE device_id = ? AND created_at >= CURDATE()");
        $st->execute([$deviceId]);
        if ((int) $st->fetch()['c'] >= TICKET_MAX_PER_DAY) {
            jsonResponse(['error' => 'امروز بیش از حد تیکت فرستادی. کمی بعد دوباره تلاش کن.'], 429);
        }

        $pdo->beginTransaction();
        try {
            // Tag with the account when there is one, so the admin panel can
            // see a player's history across a device change. The column only
            // exists after the account migration, so this is written separately
            // and a failure is not allowed to fail the ticket.
            $accountId = bb_account_for_device($pdo, $deviceId);
            $st = $pdo->prepare(
                "INSERT INTO tickets (device_id, player_name, subject, category, account_id) VALUES (?,?,?,?,?)"
            );
            $st->execute([$deviceId, $name, $subject, $category, $accountId]);
            $ticketId = (int) $pdo->lastInsertId();

            $pdo->prepare("INSERT INTO ticket_messages (ticket_id, from_admin, body) VALUES (?,0,?)")
                ->execute([$ticketId, $body]);

            $pdo->commit();
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            // Pre-migration database: retry without account_id rather than
            // losing the player's support request.
            if ($accountId === null) {
                try {
                    $pdo->beginTransaction();
                    $st = $pdo->prepare(
                        "INSERT INTO tickets (device_id, player_name, subject, category) VALUES (?,?,?,?)"
                    );
                    $st->execute([$deviceId, $name, $subject, $category]);
                    $ticketId = (int) $pdo->lastInsertId();
                    $pdo->prepare("INSERT INTO ticket_messages (ticket_id, from_admin, body) VALUES (?,0,?)")
                        ->execute([$ticketId, $body]);
                    $pdo->commit();
                } catch (Throwable $e2) {
                    if ($pdo->inTransaction()) $pdo->rollBack();
                    throw $e2;
                }
            } else {
                throw $e;
            }
        }

        jsonResponse(['ok' => true, 'ticket_id' => $ticketId, 'status' => 'open']);
    }

    // ── list ───────────────────────────────────────────────────────────────
    if ($action === 'list') {
        $deviceId = ticket_device($data);
        [$in, $inArgs] = ticket_in_clause(ticket_owned_ids($pdo, $deviceId));
        $st = $pdo->prepare(
            "SELECT t.id, t.subject, t.category, t.status, t.created_at, t.updated_at,
                    t.last_read_at, t.last_admin_reply_at,
                    (SELECT body FROM ticket_messages m WHERE m.ticket_id = t.id
                      ORDER BY m.id DESC LIMIT 1) AS last_body,
                    (SELECT COUNT(*) FROM ticket_messages m WHERE m.ticket_id = t.id) AS msg_count
               FROM tickets t
              WHERE t.device_id IN $in
              ORDER BY t.updated_at DESC
              LIMIT 50"
        );
        $st->execute($inArgs);

        $out = [];
        foreach ($st->fetchAll() as $r) {
            $out[] = [
                'id'         => (int) $r['id'],
                'subject'    => $r['subject'],
                'category'   => $r['category'],
                'status'     => $r['status'],
                'created_at' => $r['created_at'],
                'updated_at' => $r['updated_at'],
                'last_body'  => $r['last_body'],
                'msg_count'  => (int) $r['msg_count'],
                // Unread means "an admin replied after I last opened the thread".
                // Comparing last_admin_reply_at against `updated_at` also flagged
                // a ticket as unread after the player's own reply, because that
                // bumps updated_at too.
                'unread'     => $r['last_admin_reply_at'] !== null
                                && ($r['last_read_at'] === null
                                    || $r['last_admin_reply_at'] > $r['last_read_at']),
            ];
        }
        jsonResponse(['ok' => true, 'tickets' => $out]);
    }

    // ── thread ─────────────────────────────────────────────────────────────
    if ($action === 'thread') {
        $deviceId = ticket_device($data);
        $ticketId = (int)($data['ticket_id'] ?? 0);
        [$in, $inArgs] = ticket_in_clause(ticket_owned_ids($pdo, $deviceId));

        $st = $pdo->prepare(
            "SELECT id, subject, category, status, created_at FROM tickets
              WHERE id = ? AND device_id IN $in"
        );
        $st->execute(array_merge([$ticketId], $inArgs));
        $t = $st->fetch();
        if (!$t) jsonResponse(['error' => 'تیکت پیدا نشد'], 404);

        $st = $pdo->prepare(
            "SELECT id, from_admin, body, created_at
               FROM ticket_messages WHERE ticket_id = ? ORDER BY id ASC LIMIT 200"
        );
        $st->execute([$ticketId]);
        // `from_admin` is sent as the integer 0/1, not as a boolean.
        //
        // `(int) $m['from_admin'] === 1` produced JSON `true`/`false`, and the app
        // reads it with `optInt("from_admin", 0) == 1`. `optInt` returns the
        // default for a JSON boolean, so *every* message — the support replies
        // included — came back as 0 and the client drew the whole thread on the
        // player's side of the conversation. Support and player messages have to
        // be told apart for the bubbles to land on opposite sides, and a bare
        // boolean was silently collapsing that distinction.
        $msgs = array_map(fn($m) => [
            'id'         => (int) $m['id'],
            'from_admin' => (int) ((int) $m['from_admin'] === 1),
            'body'       => $m['body'],
            'created_at' => $m['created_at'],
        ], $st->fetchAll());

        // Opening the thread marks it read for the player.
        //
        // The previous statement here was `UPDATE tickets SET updated_at =
        // updated_at`, which is a no-op that looks like it does something. The
        // comment above it claimed the behaviour was implemented; it was not.
        // `last_read_at` is now a real column (see init_db.sql MIGRATION) and
        // the `unread` flag in the list action compares against it instead of
        // `updated_at` — which moves on the player's own replies, so it could
        // never be trusted either.
        $pdo->prepare("UPDATE tickets SET last_read_at = NOW() WHERE id = ?")
            ->execute([$ticketId]);

        jsonResponse([
            'ok'      => true,
            'ticket'  => [
                'id'         => (int) $t['id'],
                'subject'    => $t['subject'],
                'category'   => $t['category'],
                'status'     => $t['status'],
                'created_at' => $t['created_at'],
            ],
            'messages' => $msgs,
        ]);
    }

    // ── player reply ───────────────────────────────────────────────────────
    if ($action === 'reply') {
        $deviceId = ticket_device($data);
        $ticketId = (int)($data['ticket_id'] ?? 0);
        $body     = mb_substr(trim((string)($data['body'] ?? '')), 0, MSG_MAX_LEN, 'UTF-8');
        if ($body === '') jsonResponse(['error' => 'متن پیام خالی است'], 400);

        [$in, $inArgs] = ticket_in_clause(ticket_owned_ids($pdo, $deviceId));
        $st = $pdo->prepare("SELECT id, status FROM tickets WHERE id = ? AND device_id IN $in");
        $st->execute(array_merge([$ticketId], $inArgs));
        $t = $st->fetch();
        if (!$t) jsonResponse(['error' => 'تیکت پیدا نشد'], 404);
        if ($t['status'] === 'closed') {
            jsonResponse(['error' => 'این تیکت بسته شده است'], 409);
        }

        $pdo->prepare("INSERT INTO ticket_messages (ticket_id, from_admin, body) VALUES (?,0,?)")
            ->execute([$ticketId, $body]);
        $pdo->prepare("UPDATE tickets SET status = 'open' WHERE id = ?")->execute([$ticketId]);

        jsonResponse(['ok' => true]);
    }

    jsonResponse(['error' => 'Unknown action'], 400);

} catch (PDOException $e) {
    error_log('tickets error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
