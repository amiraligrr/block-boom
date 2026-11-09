#!/usr/bin/env python3
"""
Guard against the layout regressions this app has actually shipped.

These are not style opinions. Each check below corresponds to a bug that was
found on a real device in this project, and each of them is invisible to the
compiler — the code compiles, the build succeeds, and the screen is broken.

Run:  python3 tools/check_ui_regressions.py
Exit code is non-zero if any check fails.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")
RES = os.path.join(ROOT, "app", "src", "main", "res")

failures = []
notes = []


def kotlin_files():
    for dirpath, _dirs, files in os.walk(SRC):
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def read(p):
    with open(p, encoding="utf-8") as fh:
        return fh.read()


def rel(p):
    return os.path.relpath(p, ROOT)


def strip_comments(src):
    """
    Remove // and /* */ comments.

    Needed because the most misleading thing a lint of this kind can report is
    a *comment describing the bug it is checking for*. Both the NeonButton and
    the GameCoach fix are explained in prose that names the very expression the
    check forbids, so without this every check would fire on its own
    documentation and the tool would be unusable.
    """
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


# ── 1. A button must not decide its own width ──────────────────────────────
#
# NeonButton measured its label inside `BoxWithConstraints(Modifier.fillMaxWidth())`.
# A Box hands its children the incoming *max* constraint, so a button with no
# width of its own expanded to everything the parent allowed, and any
# `weight(1f)` sibling was left with zero width — collapsing Persian text to one
# character per line. This shipped, and it is not something the compiler or a
# screenshot review in a pull request would have caught.
#
# The invariant: the label must be measured against the button's own measured
# size, never against a fill of the incoming constraint.
def check_button_width():
    p = os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "components", "NeonButton.kt")
    body = read(p)
    bad = re.search(r"BoxWithConstraints\s*\(\s*modifier\s*=\s*Modifier\.fillMaxWidth", body)
    if bad:
        failures.append(
            "NeonButton measures its label inside fillMaxWidth() — a width-less "
            "button will steal the row from its weight(1f) siblings and collapse "
            "their text to one character per line (NeonButton.kt)"
        )
    if "onSizeChanged" not in body:
        failures.append(
            "NeonButton no longer measures its label from its own size; the shrink "
            "-to-fit logic needs a real width or every long label clips (NeonButton.kt)"
        )


# ── 2. Weighted text must not be able to wrap per character ────────────────
#
# Any Text that shares a Row with `weight` and is given a bounded width can be
# squeezed below one glyph, and Persian then breaks one character per line. This
# is only safe with `softWrap = false`, which turns an over-tight box into a
# clipped label instead of a vertical column of letters.
def check_weighted_text_softwrap():
    # Texts carrying a weight modifier...
    for p in kotlin_files():
        body = read(p)
        # crude but effective: find Text( ... ) blocks that mention weight(
        for m in re.finditer(r"Text\((?:[^()]|\([^()]*\))*\)", body, re.S):
            block = m.group(0)
            if "weight(" not in block:
                continue
            if "softWrap" not in block:
                line = body[: m.start()].count("\n") + 1
                notes.append(
                    f"{rel(p)}:{line}  Text uses weight() without softWrap=false"
                )


# ── 3. No English locale ───────────────────────────────────────────────────
#
# The app is Persian-only. `values-en` was a partial translation, so a player
# who picked English got a screen that mixed translated labels with hardcoded
# Persian and English fragments, with no way back.
def check_no_english_locale():
    d = os.path.join(RES, "values-en")
    if os.path.isdir(d):
        failures.append(
            "res/values-en exists — the app is Persian-only and the English string "
            "set was never complete, so it produced a half-translated UI"
        )
    p = os.path.join(SRC, "ir", "amiraligrr", "blockboom", "BlockBoomApp.kt")
    if '"fa"' in read(p):
        body = read(p)
        if 'saveManager.language != "fa"' not in body:
            failures.append(
                "BlockBoomApp must normalise any stored non-fa language to fa; an "
                "install that once selected English would otherwise launch against "
                "a locale that no longer has resources"
            )


# ── 4. No English UI literals in composables ───────────────────────────────
#
# The game itself renders its own overlays, so a hardcoded English fragment is
# visible in play, not just in a screen. These are the ones that actually
# shipped: the leaderboard's "Lv N" and the combo callout.
BANNED_LITERALS = [
    r'"Lv\s',
    r'"COMBO',
    r'"Play"',
    r'"Shop"',
    r'"Settings"',
    r'"Score"',
    r'"Level\s',
    r'"Continue"',
    r'"Retry"',
    r'"Buy"',
]


def check_no_english_literals():
    for p in kotlin_files():
        body = strip_comments(read(p))
        for pat in BANNED_LITERALS:
            for m in re.finditer(pat, body):
                line = body[: m.start()].count("\n") + 1
                snippet = body[m.start() : m.start() + 30].split("\n")[0]
                failures.append(
                    f"{rel(p)}:{line}  hardcoded English in UI: {snippet.strip()!r}"
                )


# ── 5. Snapshots a UI observes must actually be observable ─────────────────
#
# The in-game coach waits for "did the player move the cannon?" and "did a shot
# fire?". It read `cannon.targetX` (a plain var) and `balls.size` (a plain
# ArrayList), neither of which registers any snapshot observation — so the
# coach could never advance on a real event and only ever moved on its timeout.
# This is the class of bug that reads as correct and silently does nothing.
def check_coach_signals_observable():
    p = os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "components", "GameCoach.kt")
    if not os.path.exists(p):
        failures.append("ui/components/GameCoach.kt is missing — the game teaches nothing")
        return
    body = strip_comments(read(p))
    for forbidden, why in [
        (r"engine\.balls\b", "balls is a plain ArrayList and registers no observation"),
        (r"engine\.blocks\b", "blocks is a plain ArrayList and registers no observation"),
    ]:
        if re.search(forbidden, body):
            failures.append(f"GameCoach reads {forbidden.strip()} — {why}")

    eng = read(
        os.path.join(SRC, "ir", "amiraligrr", "blockboom", "domain", "GameEngine.kt")
    )
    if "var shotsFired by mutableIntStateOf" not in eng:
        failures.append(
            "GameEngine has no observable shotsFired counter; the coach cannot "
            "detect a shot"
        )
    cannon = read(
        os.path.join(SRC, "ir", "amiraligrr", "blockboom", "domain", "Cannon.kt")
    )
    if "var targetX by mutableFloatStateOf" not in cannon:
        failures.append(
            "Cannon.targetX is not snapshot state; the coach cannot detect a move"
        )


def check_overlay_emission_order():
    """Overlays must be emitted AFTER the screen content they cover.

    A NavHost destination lays its children out stacked, so within one
    composable emission order is both paint order and hit-test order. An overlay
    emitted *before* the screen is therefore painted underneath that screen's
    opaque background — invisible — and its buttons sit below the screen's
    pointer input, so they cannot be tapped.

    Three shipped bugs of exactly this shape, all in one session:
      - GameScreen's ReviveOffer: the dim scrim never appeared, and neither
        button was reachable, so a board frozen on HitByBlock had no way out.
      - HomeScreen's three toasts (reward delivered, balance refund, daily
        reward) were under NeonBackground's opaque gradient, so the banners
        announcing that a purchase had paid out were never seen at all.

    The rule: an overlay-looking composable must not appear textually before the
    screen's root Box/Column in the same file's top-level destination body.
    """
    targets = [
        # (file, overlay marker, what it is)
        (
            os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "screens", "GameScreen.kt"),
            "if (reviveOffer) {",
            "the revive offer (scrim + ad/decline buttons)",
        ),
        (
            os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "screens", "HomeScreen.kt"),
            "deliveredReward?.let {",
            "the reward-delivered toast",
        ),
        (
            os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "screens", "HomeScreen.kt"),
            "balanceRefund?.let {",
            "the balance-refund toast",
        ),
        (
            os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "screens", "HomeScreen.kt"),
            "dailyRewardClaimed?.let {",
            "the daily-reward toast",
        ),
    ]

    # What counts as "the screen content" in each file.
    content_markers = {
        "GameScreen.kt": "Column(",
        "HomeScreen.kt": "Box(modifier = Modifier.fillMaxSize()) {",
    }

    for path, overlay, what in targets:
        name = os.path.basename(path)
        if not os.path.exists(path):
            failures.append(f"ui/screens/{name} is missing")
            continue
        src = read(path)
        overlay_at = src.find(overlay)
        if overlay_at == -1:
            notes.append(f"{name}: {what} not found — check this rule still applies")
            continue
        content_at = src.find(content_markers[name])
        if content_at == -1:
            notes.append(f"{name}: could not locate the screen's root layout")
            continue
        if overlay_at < content_at:
            failures.append(
                f"{name}: {what} is emitted BEFORE the screen's "
                f"{content_markers[name].strip()} — it will paint underneath it "
                f"and its controls will be unreachable"
            )


def check_render_clock_in_seconds():
    """Backdrop/shake animation must not multiply a frame counter by a
    per-second rate.

    `engine.tick` counts frames. `tick * 0.001f` is the "treat it as
    milliseconds" idiom, and at 60fps it advances 0.06 per second instead of
    1.0 — so every coefficient written in per-second units ran ~16.7x slow. The
    cloud drift had a 63-second period become 17 minutes, and a petal that
    should cross the field in ~20s took four, which reads as a frozen screen
    rather than as slow animation.

    Same bug applied to `engine.gameElapsed`, which resets every level, so
    anything that must keep moving behind the pause overlay cannot use it.
    """
    p = os.path.join(SRC, "ir", "amiraligrr", "blockboom", "ui", "components", "GameCanvas.kt")
    if not os.path.exists(p):
        failures.append("ui/components/GameCanvas.kt is missing")
        return
    body = strip_comments(read(p))
    for bad, why in [
        (r"engine\.tick\s*\*\s*0\.00\d*f", "frame counter used as a seconds clock"),
        (r"engine\.gameElapsed", "resets every level, so it freezes at a level boundary"),
    ]:
        for m in re.finditer(bad, body):
            line = body[: m.start()].count("\n") + 1
            failures.append(
                f"GameCanvas.kt:{line} {why} ({m.group(0).strip()})"
            )

    eng = read(
        os.path.join(SRC, "ir", "amiraligrr", "blockboom", "domain", "GameEngine.kt")
    )
    if "var clockSeconds" not in eng:
        failures.append(
            "GameEngine has no clockSeconds; there is no real seconds clock for "
            "the renderer to use"
        )


def check_particle_radius_shrinks():
    """Particles must get smaller as they age.

    `progress` runs 0 -> 1 with age, so the shrink term has to cube *progress*
    to hold full size then collapse. Cubing `(1 - progress)` runs the curve
    backwards: every particle was born at 22% of its size — below the draw
    pass's 0.1px cutoff, so invisible — and inflated to full size exactly as it
    faded out. Sparks read as growing bubbles.
    """
    p = os.path.join(SRC, "ir", "amiraligrr", "blockboom", "domain", "Particle.kt")
    if not os.path.exists(p):
        failures.append("domain/Particle.kt is missing")
        return
    body = strip_comments(read(p))
    m = re.search(r"val\s+radius\s*:\s*Float\s*get\(\)\s*\{(.*?)\n\s*\}", body, re.S)
    if not m:
        failures.append("Particle.radius getter not found")
        return
    getter = m.group(1)
    if re.search(r"\(\s*1f\s*-\s*progress\s*\)\s*\.\s*pow", getter):
        failures.append(
            "Particle.radius cubes (1 - progress): the particle grows instead of "
            "shrinking. Cube `progress`."
        )
    if "progress" not in getter:
        failures.append("Particle.radius no longer depends on progress")


def main():
    for fn in (
        check_button_width,
        check_no_english_locale,
        check_no_english_literals,
        check_coach_signals_observable,
        check_weighted_text_softwrap,
        check_overlay_emission_order,
        check_render_clock_in_seconds,
        check_particle_radius_shrinks,
    ):
        fn()

    for n in notes:
        print(f"  note  {n}")
    if notes:
        print()

    if failures:
        print(f"{len(failures)} problem(s):")
        for f in failures:
            print(f"  - {f}")
        return 1

    print("no known UI regressions found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
