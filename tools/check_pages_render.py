#!/usr/bin/env python3
"""
Fetch every admin page and verify it renders to completion.

WHY
---
The server runs with display_errors off, so a PHP fatal (a wrong argument
shape, a missing include, a bad type) does not print an error — it silently
stops output mid-page. That is how a single bad `tableHeader([...])` call in
ads.php truncated the whole panel at half height and nobody noticed: the HTTP
status was still 200 and health.php still said healthy.

A structural syntax check cannot catch this. ad_event.php and ads.php both
passed tools/php_syntax_check.py while ads.php was fatally broken, because the
file was syntactically valid — it just blew up at runtime.

So this checks the thing that actually matters: that the bytes on the wire are
a complete document. A truncated page fails here.

Run:  python3 tools/check_pages_render.py
Needs admin credentials in /tmp/opencode/adminpass.txt; exits non-zero if any
page is truncated or errors.
"""
import os
import re
import subprocess
import sys
import json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = "https://blockboomapi.amiraligrr.ir"
PAGES = [
    "dashboard.php", "monitor.php", "ads.php", "bans.php", "integrity.php", "leaderboard.php",
    "notifications.php", "purchases.php", "settings.php", "tickets.php",
    "versions.php",
]

def sh(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout

def csrf_of(html):
    m = re.search(r'name="csrf" value="([^"]+)"', html)
    return m.group(1) if m else ""

def main():
    user = re.search(r"define\('ADMIN_USER'.*\?: '([^']+)'",
                     open(os.path.join(ROOT, "admin", "config.php"), encoding="utf-8").read())
    if not user:
        print("cannot read ADMIN_USER from admin/config.php")
        return 1
    user = user.group(1)

    pwfile = "/tmp/opencode/adminpass.txt"
    if not os.path.exists(pwfile):
        print(f"no admin password at {pwfile}")
        return 1
    pw = open(pwfile).read().strip()

    jar = "/tmp/opencode/_render_check.cookies"
    for f in (jar, jar + ".html"):
        if os.path.exists(f):
            os.remove(f)

    sh(f'curl -s -c {jar} "{BASE}/admin/login.php" -o {jar}.html')
    c = csrf_of(open(jar + ".html", encoding="utf-8").read())
    sh(f'curl -s -b {jar} -c {jar} -o /dev/null '
       f'--data-urlencode "csrf={c}" --data-urlencode "username={user}" '
       f'--data-urlencode "password={pw}" "{BASE}/admin/login.php"')

    # Confirm the login actually worked BEFORE blaming the pages.
    #
    # Every admin page starts with requireAuth(), so an unauthenticated request
    # is answered with a 302 to login.php — which is a *correct* response, and
    # produced "HTTP 302; TRUNCATED ... 11 page(s) not rendering correctly" for
    # every single page. That reads exactly like a panel-wide fatal and is the
    # worst possible way to spend an operator's time: the real cause was one
    # wrong password in a local file, and login.php additionally rate-limits to
    # 8 strikes per 10 minutes per IP, so retrying it blindly locks the admin
    # out of their own panel.
    probe = sh(f'curl -s -b {jar} -o /dev/null -w "%{{http_code}}" "{BASE}/admin/dashboard.php"').strip()
    if probe != "200":
        print(f"cannot log in to the admin panel (dashboard.php -> HTTP {probe}).")
        print()
        print("  Either the password in /tmp/opencode/adminpass.txt is wrong, or the")
        print("  account is rate-limited by login.php (8 strikes, then a 10-minute")
        print("  lockout keyed on this IP). Nothing about the pages themselves has")
        print("  been tested — re-running this now would only add a strike.")
        print()
        print("  Fix the password, then re-run. Do not retry blindly.")
        return 2

    bad = []
    print(f"{'page':<22} {'bytes':>7}  verdict")
    print("-" * 48)
    for page in PAGES:
        out = f"/tmp/opencode/_render_{page}.html"
        code = sh(f'curl -s -b {jar} -o {out} -w "%{{http_code}}" "{BASE}/admin/{page}"').strip()
        body = open(out, encoding="utf-8", errors="replace").read()
        size = len(body)

        problems = []
        if code != "200":
            problems.append(f"HTTP {code}")
        if not body.rstrip().endswith("</html>"):
            problems.append("TRUNCATED (fatal with display_errors off)")
        if re.search(r"(Fatal error|Parse error|Warning:|Uncaught)", body):
            problems.append("PHP error text in output")
        if "login" in body[:400].lower() and "csrf" not in body:
            problems.append("redirected to login")

        if problems:
            bad.append((page, problems))
            print(f"{page:<22} {size:>7}  FAIL: {'; '.join(problems)}")
        else:
            print(f"{page:<22} {size:>7}  ok")
        os.remove(out)

    for f in (jar, jar + ".html"):
        if os.path.exists(f):
            os.remove(f)

    print()
    if bad:
        print(f"{len(bad)} page(s) not rendering correctly — see FAIL rows above.")
        return 1
    print(f"All {len(PAGES)} admin pages render to completion.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
