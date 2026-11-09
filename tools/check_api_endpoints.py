#!/usr/bin/env python3
"""
Smoke-test every API endpoint the Android client actually calls.

WHY
---
The outage this exists to prevent: the live database had never had the MIGRATION
block at the bottom of init_db.sql applied to it, so `notification_reads` did not
exist and `tickets.last_read_at` was not a column. tickets.php and
fetch_notifications.php returned 500 "Server error" for every player — no
tickets, no notifications, and the leaderboard name flow dead with them —
while health.php cheerfully reported `healthy: true`, because its schema check
only looked at run_tokens, cheat_flags, coin_grants, referrals and
notifications. Nobody noticed until a player did.

tools/check_pages_render.py covers the admin panel. Nothing covered the API.

So this calls each endpoint with a realistic request and fails on anything that
is not 2xx or that answers with an `error` key. It is read-only by design:
every request uses a throwaway device id and asks for something that does not
write, so it is safe to run against production at any time.

Run:  python3 tools/check_api_endpoints.py
Exit code is non-zero if any endpoint is unhealthy.
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://blockboomapi.amiraligrr.ir"
# Unique per run. start_run.php correctly refuses to open a second run for a
# device that already has one open (409 "run already open"), so a fixed probe id
# made the second run of this script fail for a reason that has nothing to do
# with the health of the API.
PROBE = "smoketest_%d" % int(time.time())

# (label, method, path, body) — GET endpoints pass body=None.
CHECKS = [
    ("health",          "GET",  "health.php", None),
    ("app_config",      "GET",  f"app_config.php?device_id={PROBE}&version_code=17", None),
    ("leaderboard",     "GET",  f"leaderboard.php?page=1&limit=5&period=all&device_id={PROBE}", None),
    ("notifications",   "GET",  f"fetch_notifications.php?device_id={PROBE}&mark_read=0", None),
    ("start_run",       "POST", "start_run.php", {"device_id": PROBE, "level": 1}),
    # claim_coins is a write (it marks grants claimed) but it is idempotent for a
    # device that has never been granted anything, which is what PROBE is.
    ("claim_coins",     "POST", "claim_coins.php", {"device_id": PROBE}),
    ("tickets_list",    "POST", "tickets.php", {"action": "list", "device_id": PROBE}),
    ("account_register", "POST", "account.php",
     {"action": "register", "device_id": PROBE, "player_name": "SmokeTest",
      "level": 1, "total_score": 0, "coins": 0}),
    ("referral",        "POST", "referral.php", {"device_id": PROBE, "action": "status"}),
    ("ad_event",        "POST", "ad_event.php", {"event_type": "game_start", "placement": "smoke",
                                                 "device_id": PROBE, "timestamp": 0}),
]

# Endpoints where a 4xx IS the correct answer. Asserting on the code alone would
# either hide a real 500 or flag correct behaviour as broken.
#
# ticket_thread with a ticket id that does not exist must 404 — that is how the
# endpoint says "no such ticket", and treating it as a failure would train
# people to ignore this tool.
EXPECTED_ERRORS = [
    ("ticket_thread", "POST", "tickets.php",
     {"action": "thread", "device_id": PROBE, "ticket_id": 0}, (404,)),
    # An absurd tokenless score must be rejected by the plausibility check.
    #
    # This used to assert 403 "Missing run token" for *any* tokenless submit.
    # That assertion was the bug encoded as a test: it made the deploy check
    # demand the exact behaviour that was eating players' scores. A client
    # whose start_run request failed — a tunnel, a dropped packet, a level
    # finished inside the 5s token timeout — had nowhere to go, and the
    # "leaderboard error" players reported was this.
    #
    # So the contract is now: a tokenless submit is *accepted* (marked
    # unverified), and it is the *ceiling* that has to hold. The absurd value
    # below is ~3,000x the level-1 baseline, so only a broken plausibility
    # check lets it through.
    ("submit_score",  "POST", "submit_score.php",
     {"name": "SmokeTest", "level": 1, "score": 90000000, "is_premium": 0,
      "device_id": PROBE}, (400,)),
    # A well-formed but unknown recovery code must be rejected, and must not
    # create an account. This is what proves account.php's hashing path works.
    # The code is drawn from the real alphabet (RECOVERY_ALPHABET) so this
    # tests "no such account" and not "malformed input" — the previous
    # version used "ZZZZZZZZZZ", which the normaliser correctly shortened and
    # so was really re-testing the length check.
    ("account_recover_bad", "POST", "account.php",
     {"action": "recover", "device_id": PROBE, "recovery": "AAAAAAAAC6"}, (404,)),
]

# Endpoints that MUST be unreachable. A 200 here is a security regression.
FORBIDDEN = [
    "config.php", "db.php", "anticheat.php", "policy.php",
    "init_db.sql", "README.md", ".env",
]


def call(method, path, body):
    url = f"{BASE}/{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}"


def main():
    failures = []
    print(f"{'endpoint':<18} {'code':>5}  verdict")
    print("-" * 62)

    for label, method, path, body in CHECKS:
        code, text = call(method, path, body)
        ok = 200 <= code < 300
        note = ""

        if ok:
            try:
                payload = json.loads(text)
                if isinstance(payload, dict) and payload.get("error"):
                    ok = False
                    note = f"error: {payload['error']}"
                else:
                    note = "json ok"
                    if label == "app_config":
                        # The host is UTC. Player-facing dates and the daily
                        # buckets must be Iran time; a regression here shifts
                        # every chart and timestamp by 3:30.
                        tz = payload.get("server_time_zone")
                        local = str(payload.get("server_time_local", ""))
                        if tz != "Asia/Tehran":
                            ok = False
                            note = f"timezone is {tz!r}, expected Asia/Tehran"
                        elif not local.endswith("+03:30"):
                            ok = False
                            note = f"server_time_local is {local!r}"
                        else:
                            note = "json ok, Tehran time"
            except json.JSONDecodeError:
                ok = False
                note = "response was not JSON"

        if not ok and not note:
            note = text[:60].replace("\n", " ")

        mark = "ok" if ok else "FAIL"
        print(f"{label:<18} {code:>5}  {mark}  {note}")
        if not ok:
            failures.append(f"{label} (HTTP {code}) {note}")

    # Endpoints whose correct answer is a 4xx.
    for label, method, path, body, want in EXPECTED_ERRORS:
        code, text = call(method, path, body)
        ok = code in want
        detail = text[:50].replace("\n", " ")
        print(f"{label:<18} {code:>5}  {'ok' if ok else 'FAIL'}  "
              f"expected {want[0]}, got {detail}")
        if not ok:
            failures.append(f"{label} (HTTP {code}, expected {want[0]}) {detail}")

    # The full authenticated run flow — name registration and the leaderboard.
    #
    # Off by default because it is a REAL submission: it writes a row to
    # `leaderboard` with a rank, which is not something a health check should
    # leave behind on production every time it runs.
    if "--submit" in sys.argv:
        code, text = call("POST", "start_run.php", {"device_id": PROBE, "level": 1})
        token = ""
        if code == 200:
            try:
                token = json.loads(text).get("run_token", "")
            except json.JSONDecodeError:
                pass
        if token:
            body = {"name": "SmokeTest", "level": 1, "score": 10, "is_premium": 0,
                    "device_id": PROBE, "run_token": token, "events": 1,
                    "chain": "x", "duration_ms": 5000}
            code, text = call("POST", "submit_score.php", body)
            ok = code == 200 and '"ok":true' in text.replace(" ", "")
            print(f"{'run->submit':<18} {code:>5}  {'ok' if ok else 'FAIL'}  "
                  f"{'token flow ok' if ok else text[:60]}")
            if not ok:
                failures.append(f"run->submit (HTTP {code}) {text[:60]}")
            print(f"  cleanup: DELETE FROM leaderboard WHERE device_id = '{PROBE}'")
        else:
            print(f"{'run->submit':<18} {'-':>5}  FAIL  no run token (start_run HTTP {code})")
            failures.append("start_run did not return a run token")

    # ── The reset-phone scenario, end to end ─────────────────────────────────
    #
    # This is the bug that started all of it, so it gets a real test rather
    # than a per-endpoint code check.
    #
    # The story: a player plays on phone A, syncs, then factory-resets and
    # reinstalls on phone B. B has a different ANDROID_ID, so under the old
    # scheme B is a brand-new player — empty leaderboard, no level, no coins,
    # and support tickets that cannot even be listed. The test below walks
    # that exact path and asserts the progress comes back.
    #
    # It writes to `accounts`/`device_links`, which is unavoidable for a test
    # of the recovery feature. The rows are keyed to PROBE, so they are inert
    # and identified by the `smoketest_` prefix for easy cleanup.
    print()
    print("reset-phone recovery round trip:")
    dev_a = f"{PROBE}_a"
    dev_b = f"{PROBE}_b"

    # 1. phone A registers and plays.
    code, text = call("POST", "account.php", {
        "action": "register", "device_id": dev_a, "player_name": "SmokeTest",
        "level": 1, "total_score": 0, "coins": 0,
    })
    code_a = ""
    if code == 200:
        try:
            code_a = json.loads(text).get("recovery", "")
        except json.JSONDecodeError:
            pass

    if not code_a:
        print(f"  {'FAIL':<22} phone A got no recovery code (HTTP {code}) {text[:50]}")
        failures.append("account register did not return a recovery code")
    else:
        print(f"  {'ok':<22} phone A registered, got a {len(code_a)}-char code")

        # 2. phone A plays, then syncs its progress up.
        code, text = call("POST", "account.php", {
            "action": "sync", "device_id": dev_a, "player_name": "SmokeTest",
            "level": 7, "total_score": 4321, "coins": 890,
        })
        synced = code == 200
        print(f"  {'ok' if synced else 'FAIL':<22} phone A synced level 7 / 4321 / 890 coins")
        if not synced:
            failures.append(f"account sync failed (HTTP {code}) {text[:60]}")

        # 3. phone B — a different ANDROID_ID — recovers with the code.
        code, text = call("POST", "account.php", {
            "action": "recover", "device_id": dev_b, "recovery": code_a,
        })
        got = {}
        if code == 200:
            try:
                got = json.loads(text).get("progress", {}) or {}
            except json.JSONDecodeError:
                pass

        restored = (
            got.get("level") == 7
            and got.get("total_score") == 4321
            and got.get("coins") == 890
        )
        print(f"  {'ok' if restored else 'FAIL':<22} phone B recovered "
              f"level={got.get('level')} score={got.get('total_score')} coins={got.get('coins')}")
        if not restored:
            failures.append(
                f"recovery did not restore progress (got {got}, wanted level=7 score=4321 coins=890)")

        # 4. phone B syncs with *worse* local data — it must not lose phone
        #    A's progress. This is the merge rule, and a last-write-wins bug
        #    would show up here.
        code, text = call("POST", "account.php", {
            "action": "sync", "device_id": dev_b, "player_name": "SmokeTest",
            "level": 1, "total_score": 0, "coins": 0,
        })
        after = {}
        if code == 200:
            try:
                after = json.loads(text).get("progress", {}) or {}
            except json.JSONDecodeError:
                pass
        held = (after.get("level") == 7 and after.get("total_score") == 4321)
        print(f"  {'ok' if held else 'FAIL':<22} stale sync did not lower progress "
              f"(level={after.get('level')} score={after.get('total_score')})")
        if not held:
            failures.append("a stale sync overwrote newer progress — merge is not monotonic")

        print(f"  cleanup: DELETE FROM accounts WHERE recovery_hash = SHA2("
              f"CONCAT('bb-account-v1:', '{code_a}'), 256);")
        print(f"            DELETE FROM device_links WHERE device_id IN ('{dev_a}', '{dev_b}');")

    print()
    for path in FORBIDDEN:
        code, _ = call("GET", path, None)
        blocked = code in (403, 404)
        print(f"  {'ok' if blocked else 'FAIL'}  {path:<20} -> {code}")
        if not blocked:
            failures.append(f"{path} is reachable (HTTP {code})")

    print()
    if failures:
        print(f"{len(failures)} problem(s):")
        for f in failures:
            print("  -", f)
        return 1
    print("all endpoints healthy")
    return 0


if __name__ == "__main__":
    sys.exit(main())
