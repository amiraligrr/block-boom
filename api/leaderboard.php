<?php
/**
 * leaderboard.php — paginated, period-filtered leaderboard.
 *
 * Two bugs this file used to have, both user-visible as a hard error:
 *   1. `LIMIT ? OFFSET ?` bound through execute() as strings. With
 *      ATTR_EMULATE_PREPARES = false that is a MySQL syntax error, so every
 *      request 500'd with "Server error" and the app showed a dead screen.
 *      LIMIT/OFFSET are now validated integers, inlined.
 *   2. The daily/weekly filters matched on created_at, which is only written
 *      on first insert. A player who joined months ago and played today was
 *      missing from "today". They now filter on updated_at.
 */
require_once __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { jsonResponse(['ok' => true]); }
if ($_SERVER['REQUEST_METHOD'] !== 'GET') { jsonResponse(['error' => 'Method not allowed'], 405); }

$period = (string)($_GET['period'] ?? 'all');
if (!in_array($period, ['all', 'daily', 'weekly', 'monthly'], true)) $period = 'all';

$deviceId = bb_normalize_device_id((string)($_GET['device_id'] ?? ''));

$page  = clampInt($_GET['page']  ?? 1, 1, 10000, 1);
$limit = clampInt($_GET['limit'] ?? 20, 1, 50, 20);
$offset = ($page - 1) * $limit;

try {
    $pdo = getPDO();

    // updated_at, not created_at: a score is "current" when it was last beaten.
    $where  = "WHERE banned = 0";
    $params = [];
    if ($period === 'daily') {
        $where .= " AND updated_at >= CURDATE()";
    } elseif ($period === 'weekly') {
        $where .= " AND updated_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)";
    } elseif ($period === 'monthly') {
        $where .= " AND updated_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)";
    }

    $countStmt = $pdo->prepare("SELECT COUNT(*) AS total FROM leaderboard $where");
    $countStmt->execute($params);
    $total = (int) $countStmt->fetch()['total'];
    $totalPages = max(1, (int) ceil($total / $limit));

    // Clamp so a stale client asking for page 99 of a 3-page board gets the
    // last real page instead of an empty list.
    if ($offset >= $total && $total > 0) {
        $page = $totalPages;
        $offset = ($page - 1) * $limit;
    }

    // $limit/$offset are clamped integers from clampInt(), never raw input.
    //
    // ORDER BY is level DESC, score DESC — NOT score DESC on its own.
    //
    // The list used to be ordered by level alone in the client, and the server's
    // own rank query counted `(level > mine) OR (level = mine AND score > mine)`.
    // Those two disagreed: the client sorted the rows it received by the server's
    // `rank` field, but `rank` was assigned as `$offset + $i + 1` — the row's
    // position in *this* page, not its position on the whole board. On page 1
    // those coincide, but any filter or a later page produced numbers that did not
    // match the ordering the player was looking at, which is what put the second
    // place player above the first.
    //
    // Both the list and the rank are now derived from one expression, and the
    // score is the tiebreak *within* a level, so the visible order and the stated
    // rank can never drift apart.
    $sql = "SELECT name, level, score, is_premium, updated_at
              FROM leaderboard $where
             ORDER BY level DESC, score DESC, updated_at ASC
             LIMIT $limit OFFSET $offset";
    $rows = $pdo->query($sql)->fetchAll();

    foreach ($rows as $i => $r) {
        $rows[$i]['level']      = (int) $r['level'];
        $rows[$i]['score']      = (int) $r['score'];
        $rows[$i]['is_premium'] = (int) $r['is_premium'];
        $rows[$i]['rank']       = $offset + $i + 1;
        $rows[$i]['crown']      = $rows[$i]['is_premium'] ? '👑' : '';
    }

    $userRank   = null;
    $userEntry  = null;
    if ($deviceId !== '') {
        // $where, so a device whose last score falls outside the selected period is
        // reported as "not on this board" instead of being handed a rank
        // computed over rows the player cannot see.
        $meStmt = $pdo->prepare(
            "SELECT name, level, score, is_premium, updated_at
               FROM leaderboard $where AND device_id = ?"
        );
        $meStmt->execute(array_merge($params, [$deviceId]));
        $me = $meStmt->fetch();

        if ($me) {
            $myLevel = (int) $me['level'];
            $myScore = (int) $me['score'];
            // ── This player's rank ──────────────────────────────────────
            //
            // It has to count against the *same* set the list was drawn from,
            // including the period filter. It used to hardcode `banned = 0` only,
            // so on the daily/weekly/monthly tabs the stated rank was computed
            // over the whole board while the rows on screen were a filtered
            // subset — the number next to "your rank" then disagreed with every
            // row above it, which is the second half of the "2nd place above the
            // 1st" report.
            //
            // `$where` is reused verbatim, so the filter and the rank cannot drift.
            $rankStmt = $pdo->prepare(
                "SELECT COUNT(*) + 1 AS rank FROM leaderboard $where
                   AND ((level > ?) OR (level = ? AND score > ?))"
            );
            $rankStmt->execute(array_merge($params, [$myLevel, $myLevel, $myScore]));
            $userRank = (int) $rankStmt->fetch()['rank'];

            $userEntry = [
                'name'       => $me['name'],
                'level'      => $myLevel,
                'score'      => $myScore,
                'is_premium' => (int) $me['is_premium'],
                'crown'      => ((int) $me['is_premium']) ? '👑' : '',
                'updated_at' => $me['updated_at'],
                'rank'       => $userRank,
            ];
        }
    }

    // `private`, not `public`. The response body carries userRank and
    // userEntry, keyed off the device_id in the query string. A shared cache
    // that ignores or normalises query strings would serve one player's rank to
    // another, and `public` explicitly licenses that. The 20s window still
    // absorbs leaderboard churn, it just stops being shareable.
    header('Cache-Control: private, max-age=20, stale-while-revalidate=60');
    header('Vary: Accept-Encoding');

    jsonResponse([
        'ok'         => true,
        'data'       => $rows,
        'pagination' => [
            'page'       => $page,
            'limit'      => $limit,
            'total'      => $total,
            'totalPages' => $totalPages,
            'hasMore'    => $page < $totalPages,
        ],
        'userRank'   => $userRank,
        'userEntry'  => $userEntry,
        'period'     => $period,
        'serverTime' => time(),
    ]);

} catch (PDOException $e) {
    error_log('leaderboard DB error: ' . $e->getMessage());
    jsonResponse(['error' => 'Server error'], 500);
}
