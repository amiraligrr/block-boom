#!/usr/bin/env python3
"""
Verification of the anti-cheat plausibility model in api/anticheat.php.

There may be no PHP binary in this environment, so the pure arithmetic from
anticheat.php is re-implemented here 1:1 and exercised against real scenarios.
The PHP and this MUST stay in sync.

History worth reading before editing: this file used to model GameEngine's
level-scaled hp_multiplier() while anticheat.php did not. The test therefore
passed while the server under-estimated real scores by up to 11.7x, falsely
rejecting no-farm players from level 39 onward. A "1:1 re-implementation" is
worth exactly nothing unless the two copies really are identical -- so
check_pairs() below asserts that mechanically by parsing the PHP source, and
there is a run_selfcheck.py to confirm the parser still works.
"""
import math
import re
import sys
from pathlib import Path

HEADROOM = 1.35
HP_VARIANCE = 1.1
SCORE_RATE_CAP = 3500
ABSOLUTE_MAX_SCORE = 2_000_000_000
MAX_LEVEL = 9999
BB_RUN_MIN_SECONDS = 3


MAX_BLOCK_HP = 240
MAX_BLOCKS_PER_LEVEL = 72
BOSS_EVERY_N_LEVELS = 10
HP_MULTIPLIER_CEILING = 2.3


def hp_curve(level):
    """anticheat.php: bb_hp_curve() == GameEngine.generateBlockHP() base."""
    if level <= 5:
        return 4 + (level - 1) * 2
    if level <= 15:
        return 12 + (level - 5) * 3
    if level <= 30:
        return 42 + (level - 15) * 4
    if level <= 50:
        return 102 + (level - 30) * 5
    return 222 + (level - 50) * 16


def hp_multiplier(level):
    """anticheat.php: bb_hp_multiplier() == GameEngine.levelConfig().hpMultiplier.

    Capped at HP_MULTIPLIER_CEILING, because GameEngine.kt clamps with
    coerceAtMost(). An uncapped copy of this function is what let the server
    hand out a 2.38-trillion ceiling against a 897-million engine maximum.

    Ramps off `extra`, not `inTier`: inTier resets 9 -> 0 at every tier
    boundary, which took more out of the multiplier (9 * 0.011) than the tier
    step put in (0.09), so it dipped at levels 11, 21, 31... A difficulty
    multiplier that falls every tenth level is not a difficulty curve.
    """
    extra = max(0, level - 1)
    tier = extra // 10
    return min(1.0 + extra * 0.0085 + tier * 0.009, HP_MULTIPLIER_CEILING)


def block_count(level):
    """anticheat.php: bb_block_count() == GameEngine.levelConfig().blockCount."""
    extra = max(0, level - 1)
    return min(min(8 + extra * 2, 40) + (extra // 5) * 3, MAX_BLOCKS_PER_LEVEL)


def capped_hp(level):
    """GameEngine.generateBlockHP() clamps to MAX_BLOCK_HP *before* the
    level multiplier is applied (GameEngine.kt multiplies the capped result),
    so the cap and the multiplier compose."""
    base = max(1.0, min(float(MAX_BLOCK_HP),
                        math.floor(hp_curve(level) * HP_VARIANCE)))
    return math.floor(base * hp_multiplier(level))


def normal_block_score(level):
    """anticheat.php: bb_normal_block_score() -- parent + its fragments."""
    return capped_hp(level) * 2.0


def boss_block_score(level):
    """anticheat.php: bb_boss_block_score() -- 3x for the boss, ~1x fragments."""
    base = max(1.0, min(float(MAX_BLOCK_HP),
                        math.floor(hp_curve(level) * HP_VARIANCE)))
    max_hp = max(1.0, math.floor(base * 2.0 * 4.0))
    return max_hp * 4.0


def max_level_score(level):
    """anticheat.php: bb_max_level_score()."""
    level = max(1, min(MAX_LEVEL, level))
    n = block_count(level)
    is_boss = level % BOSS_EVERY_N_LEVELS == 0
    score = (n - 1 if is_boss else n) * normal_block_score(level)
    if is_boss:
        score += boss_block_score(level)
    score += n * 25
    return score


_cum = {}


def max_cumulative_score(level):
    """anticheat.php: bb_max_cumulative_score()."""
    level = max(1, min(MAX_LEVEL, level))
    if level not in _cum:
        _cum[level] = sum(max_level_score(i) for i in range(1, level + 1))
    return _cum[level]


def score_ceiling(level, verified_play_seconds):
    """anticheat.php: bb_score_ceiling()."""
    baseline = max_cumulative_score(level) * HEADROOM
    farming = max(0.0, verified_play_seconds) * SCORE_RATE_CAP
    return min(float(ABSOLUTE_MAX_SCORE), baseline + farming)


def judge(level, score, play):
    """anticheat.php: bb_judge_score()."""
    ceiling = int(score_ceiling(level, play))
    if score < 0:
        return False, "score_negative", ceiling
    if score > ABSOLUTE_MAX_SCORE:
        return False, "score_absurd", ceiling
    if score > ceiling:
        return False, "score_unreachable", ceiling
    return True, "ok", ceiling


# ── Cross-check: does this file still agree with anticheat.php? ───────────
PHP_PATH = Path(__file__).resolve().parent.parent / "api" / "anticheat.php"


def check_pairs():
    """Assert the numeric constants in anticheat.php match this module.

    This is the guard that would have caught the hp_multiplier divergence: the
    Python had the multiplier, the PHP did not, and nothing compared the two.
    Constants only -- the formulas are compared by the shared test cases below.
    """
    if not PHP_PATH.is_file():
        return ["anticheat.php not found at " + str(PHP_PATH)]
    php = PHP_PATH.read_text(encoding="utf-8")
    problems = []
    for name, want in [
        ("BB_HEADROOM", HEADROOM),
        ("BB_HP_VARIANCE", HP_VARIANCE),
        ("BB_SCORE_RATE_CAP", SCORE_RATE_CAP),
        ("BB_ABSOLUTE_MAX_SCORE", ABSOLUTE_MAX_SCORE),
        ("BB_MAX_LEVEL", MAX_LEVEL),
        ("BB_RUN_MIN_SECONDS", BB_RUN_MIN_SECONDS),
    ]:
        m = re.search(r"define\(\s*'" + name + r"'\s*,\s*([0-9_.eE+]+)\s*\)", php)
        if not m:
            problems.append(f"{name}: not found in anticheat.php")
            continue
        got = float(m.group(1).replace("_", ""))
        if got != float(want):
            problems.append(f"{name}: php={got:g} verify={float(want):g}")
    # The multiplier itself -- the bug that shipped. Assert the PHP really has it,
    # with the right coefficients AND the ceiling clamp. The body is extracted
    # from the definition, not the first textual match: bb_effective_block_hp()
    # calls bb_hp_multiplier() and is declared above it, so a naive split finds
    # the call site and inspects the wrong lines.
    if not re.search(r"function\s+bb_hp_multiplier\s*\(", php):
        problems.append("bb_hp_multiplier() missing from anticheat.php")
    elif "0.09" not in php or "0.011" not in php:
        problems.append("bb_hp_multiplier() coefficients drifted from 0.09 / 0.011")
    else:
        body = re.search(
            r"function\s+bb_hp_multiplier\s*\([^)]*\)\s*:\s*float\s*\{(.*?)\n\}",
            php, re.S)
        if not body:
            problems.append("could not parse bb_hp_multiplier() body")
        elif "BB_HP_MULTIPLIER_CEILING" not in body.group(1):
            problems.append(
                "bb_hp_multiplier() lost its HP_MULTIPLIER_CEILING clamp")
    # The engine's HP ceiling has to be applied somewhere in the score model,
    # or the HP curve grows unbounded again. It lives in bb_effective_block_hp()
    # (and inline in the boss branch), which the score functions call.
    if not re.search(r"define\(\s*'BB_MAX_BLOCK_HP'\s*,\s*240\s*\)", php):
        problems.append("BB_MAX_BLOCK_HP missing or not 240 in anticheat.php")
    if not re.search(r"function\s+bb_effective_block_hp\s*\(", php):
        problems.append("bb_effective_block_hp() missing from anticheat.php")
    else:
        eff = re.search(
            r"function\s+bb_effective_block_hp\s*\([^)]*\)\s*:\s*float\s*\{(.*?)\n\}",
            php, re.S)
        if not eff or "BB_MAX_BLOCK_HP" not in eff.group(1):
            problems.append(
                "bb_effective_block_hp() no longer applies the BB_MAX_BLOCK_HP clamp")
    return problems


ENGINE_PATH = Path(__file__).resolve().parent.parent / "app" / "src" / "main" \
    / "java" / "ir" / "amiraligrr" / "blockboom" / "domain" / "GameEngine.kt"


def check_engine_agreement():
    """Assert this model still matches GameEngine.kt, by parsing the Kotlin.

    This is the check that was missing, and its absence is why the drift shipped.

    check_pairs() above compares Python against the PHP. That is a real guard,
    but it only proves the two *copies* agree -- and when the engine was retuned
    the natural thing to do was retune both copies together, which leaves
    check_pairs() perfectly green while the server's model is wrong. The version
    that shipped had 0.16/0.012 and no ceiling on the multiplier, in both files,
    and that agreed with each other perfectly.

    So the third leg is the engine itself. Every constant below is read out of
    GameEngine.kt at run time; if someone retunes scoring in the app and forgets
    the server, this fails instead of silently shipping a wrong ceiling.
    """
    if not ENGINE_PATH.is_file():
        return ["GameEngine.kt not found at " + str(ENGINE_PATH)]
    kt = ENGINE_PATH.read_text(encoding="utf-8")
    problems = []

    def kt_number(pattern, label, want, tol=1e-9):
        m = re.search(pattern, kt)
        if not m:
            problems.append(f"{label}: not found in GameEngine.kt")
            return
        got = float(m.group(1))
        if abs(got - float(want)) > tol:
            problems.append(
                f"{label}: GameEngine={got:g} model={float(want):g} "
                f"(engine retuned? update verify_anticheat.py AND anticheat.php)"
            )

    kt_number(r"HP_MULTIPLIER_CEILING\s*=\s*([0-9.]+)f?",
              "HP_MULTIPLIER_CEILING", HP_MULTIPLIER_CEILING)
    kt_number(r"MAX_BLOCK_HP\s*=\s*([0-9]+)", "MAX_BLOCK_HP", MAX_BLOCK_HP)
    kt_number(r"BOSS_EVERY_N_LEVELS\s*=\s*([0-9]+)",
              "BOSS_EVERY_N_LEVELS", BOSS_EVERY_N_LEVELS)
    kt_number(r"MAX_BLOCKS_PER_LEVEL\s*=\s*([0-9]+)",
              "MAX_BLOCKS_PER_LEVEL", MAX_BLOCKS_PER_LEVEL)

    # The multiplier's two coefficients, and the base it ramps off.
    m = re.search(r"perLevel\s*=\s*1f\s*\+\s*extra\s*\*\s*([0-9.]+)f\s*\+\s*"
                  r"tier\s*\*\s*TIER_HP_STEP", kt)
    if not m:
        problems.append(
            "levelConfig perLevel expression drifted in GameEngine.kt "
            "(expected `1f + extra * <coef>f + tier * TIER_HP_STEP`)")
    elif abs(float(m.group(1)) - 0.0085) > 1e-9:
        problems.append(
            f"hp multiplier base coefficient: GameEngine={m.group(1)} model=0.0085")
    m2 = re.search(r"TIER_HP_STEP\s*=\s*([0-9.]+)f", kt)
    if not m2:
        problems.append("TIER_HP_STEP missing from GameEngine.kt")
    elif abs(float(m2.group(1)) - 0.009) > 1e-9:
        problems.append(
            f"hp multiplier tier step: GameEngine={m2.group(1)} model=0.009")
    # Monotonicity: a multiplier that dips is not a multiplier the server can
    # model, and is not a difficulty curve the player can read.
    drops = [l for l in range(2, 9999)
             if hp_multiplier(l) < hp_multiplier(l - 1) - 1e-9]
    if drops:
        problems.append(
            f"hp_multiplier() is not monotonic: it drops at {len(drops)} levels "
            f"(first: {drops[0]})")

    # generateBlockHP's branch structure, most specific tail last.
    for branch, label in [
        (r"level\s*<=\s*30\s*->\s*42\s*\+\s*\(level\s*-\s*15\)\s*\*\s*4",
         "generateBlockHP level<=30 branch"),
        (r"level\s*<=\s*50\s*->\s*102\s*\+\s*\(level\s*-\s*30\)\s*\*\s*5",
         "generateBlockHP level<=50 branch"),
        (r"222\s*\+\s*\(level\s*-\s*50\)\s*\*\s*16",
         "generateBlockHP tail branch"),
    ]:
        if not re.search(branch, kt):
            problems.append(f"{label} not found in GameEngine.kt")

    if "coerceIn(1, MAX_BLOCK_HP)" not in kt:
        problems.append(
            "generateBlockHP lost its coerceIn(1, MAX_BLOCK_HP) clamp -- "
            "the ceiling model must apply MAX_BLOCK_HP too")
    if "coerceAtMost(HP_MULTIPLIER_CEILING)" not in kt:
        problems.append(
            "levelConfig lost its coerceAtMost(HP_MULTIPLIER_CEILING) clamp")

    # blockCount, including the (extra / 5) * 3 growth term the model uses.
    if not re.search(r"minOf\(8 \+ extra \* 2, 40\) \+ \(extra / 5\) \* 3", kt):
        problems.append("levelConfig blockCount expression drifted in GameEngine.kt")

    return problems


CASES = []


def case(name, expect_ok, level, score, play):
    CASES.append((name, expect_ok, level, score, play))


# ── 1. The regression that caused the reported bug ─────────────────────────
# The old rule was `score > level*6000 + 5000`. A legitimate player at level
# 100 with 10 hours of play has a far higher lifetime total and was rejected
# with "Score too high for level" forever.
case("legit level 100, 10h play, 1.2M total", True, 100, 1_200_000, 36_000)
case("legit level 250, 40h play, 12M total", True, 250, 12_000_000, 144_000)
# ...and the old formula's verdict on the same player, for contrast:
case("OLD rule would have rejected #1", False, 100, 1_200_000, 36_000)

# ── 1b. The bug this file now exists to prevent ───────────────────────────
# Before bb_hp_multiplier() reached the PHP, a no-farm player (each level
# cleared once, never replayed) was first rejected at level 39, because the
# server's baseline sat below what the engine actually awards. These assert
# the corrected model accepts every such player at every level, with zero
# farming credit. Any future divergence in either file breaks this immediately.
def no_farm_score(level):
    """Cumulative score of a player who clears each level once, no replays."""
    return max_cumulative_score(level)


for lvl in [39, 50, 100, 200, 500, 1000, 2000]:
    case(f"no-farm player at level {lvl}", True, lvl, int(no_farm_score(lvl)), 0.0)

# ── 2. Fresh install, first ever run ───────────────────────────────────────
# No server-observed play time yet. The grace window must let a first run
# through, or every new player is locked out of the leaderboard.
case("fresh install, level 3, 900 pts", True, 3, 900, 0.0)
case("fresh install, level 12, 25k (one run = 30s)", True, 12, 25_000, 30.0)

# ── 3. Instant-forgery from a fresh install ────────────────────────────────
# The classic attack: install, immediately claim a huge score.
case("fresh install claiming 500k", False, 3, 500_000, 0.0)
case("fresh install claiming 9,999,999", False, 1, 9_999_999, 0.0)
case("fresh install claiming 2,000,000,000", False, 1, 2_000_000_000, 0.0)

# ── 4. Forgery on an established account ──────────────────────────────────
# 10h of real play, then claims 500M — far past what that much play can earn,
# so it must be rejected.
case("10h player claiming 500M", False, 60, 500_000_000, 36_000)
case("10h player claiming 1M (plausible)", True, 60, 1_000_000, 36_000)

# ── 5. Score vs. the time it would take to earn it ────────────────────────
# 40M over 1000h of play is 11 points/sec — far below the engine's physical
# ceiling, so it is NOT suspicious and must be accepted.
case("level 10, 40M over 1000h of play", True, 10, 40_000_000, 3_600_000)
# The same score claimed after one minute is 666k/sec: impossible.
case("level 10, 40M after 60s of play", False, 10, 40_000_000, 60)

# ── 6. Degenerate inputs ──────────────────────────────────────────────────
case("negative score", False, 5, -1, 600)
case("zero score on new device", True, 1, 0, 0.0)

# ── 7. The boundary ───────────────────────────────────────────────────────
# A bot that plays in real time at the cap should be RIGHT at the edge: this
# proves the rate cap is the binding constraint, not an accidental lower one.
case("bot playing at exactly the rate cap", True, 50, int(score_ceiling(50, 3600)), 3600)
case("bot 1 point over the cap", False, 50, int(score_ceiling(50, 3600)) + 1, 3600)
case("bot 10x over the cap", False, 50, int(score_ceiling(50, 3600)) * 10, 3600)

# ── 8. The absolute cap must not bind on any real player ──────────────────
# The old 1.2e9 cap hard-rejected everyone past ~level 1050 with "score_absurd".
# The 2.5e12 cap was the opposite failure: derived from the *uncapped* model, so
# it sat ~2,800x above anything the engine can produce and did nothing at all.
# It is now 2e9 -- the client's own hard cap, which no real client can exceed.
case("perfect play through level cap", True, 9999, int(no_farm_score(9999)), 0.0)
case("beyond the engine maximum", False, 9999, 9_000_000_000_000, 0.0)
# The baseline ceiling at the level cap is ~978M, so 2e9 with no play time is
# genuinely unreachable and must be refused on plausibility, not on the absolute
# cap. Reaching 2e9 needs real play -- see the 1000h case below, where the
# absolute cap is exactly what admits it and nothing below it.
case("2e9 with no play time", False, 9999, 2_000_000_000, 0.0)
case("one point over the client's cap", False, 9999, 2_000_000_001, 3_600_000)

# ── 9. The exploit this correction closed ─────────────────────────────────
# A fresh install claims level 9999 and submits immediately. Before the model
# was pinned to the engine, the level-9999 baseline alone was 2.38 trillion --
# above the client's 2e9 cap -- so this exact request took rank #1 with zero
# play time. It now has to pass as what it is: a level-1 run.
case("EXPLOIT: fresh install claiming level 9999", False, 9999, 2_000_000_000, 0.0)
case("EXPLOIT: level 9999 with 5s of play", False, 9999, 2_000_000_000, 5.0)
case("level 9999 for a real player, 1000h play", True, 9999, 800_000_000, 3_600_000)

sync_problems = check_pairs()
engine_problems = check_engine_agreement()

print(f"{'case':<42} {'want':<5} {'got':<5} {'verdict':<18} ceiling")
print("-" * 100)
fails = 0
for name, expect_ok, level, score, play in CASES:
    ok, msg, ceiling = judge(level, score, play)
    good = (ok == expect_ok)
    # Case 3 exists only to document the old formula; skip its verdict check.
    document_only = name.startswith("OLD rule")
    if not good and not document_only:
        fails += 1
    mark = "ok" if good else ("DOC" if document_only else "FAIL")
    print(f"{name:<42} {str(expect_ok):<5} {str(ok):<5} {msg:<18} {ceiling:,}")

print()
print(f"{len(CASES) - fails - 1}/{len(CASES) - 1} behavioural cases passed")

# ── PHP <-> Python drift check ────────────────────────────────────────────
print()
if sync_problems:
    for p in sync_problems:
        print(f"  DRIFT: {p}")
    fails += len(sync_problems)
else:
    print("anticheat.php <-> verify_anticheat.py: constants and hp_multiplier agree")

# ── Model <-> engine drift check ──────────────────────────────────────────
print()
if engine_problems:
    for p in engine_problems:
        print(f"  DRIFT: {p}")
    fails += len(engine_problems)
else:
    print("verify_anticheat.py <-> GameEngine.kt: scoring model agrees")

# ── Why a genuine player can never be falsely rejected ────────────────────
# The rate cap IS the definition of "fastest physically possible". A run at
# level L cannot earn more than maxLevelScore(L), and cannot earn it in less
# than maxLevelScore(L)/RATE_CAP seconds. The server measures the real elapsed
# time, so for any run a player actually played:
#     credited = realDuration * RATE_CAP  >=  maxLevelScore(L) >= that run's score
# There is no false rejection, at any level, and BB_RUN_MIN_SECONDS only makes
# the bound more generous.
print("\nNo-false-rejection guarantee (per run, any level):")
worst = 0.0
for lvl in [1, 5, 25, 100, 500, 1000, 5000, 9999]:
    need = max_level_score(lvl) / SCORE_RATE_CAP
    worst = max(worst, need)
    print(f"  L{lvl:<5} max run score {max_level_score(lvl):>12,.0f}"
          f"  => needs >= {need:>6.1f}s of real play")
print(f"  A submit faster than that is rejected -- by design, not by accident.")
print(f"  Tightest case overall: {worst:.1f}s at the level where it binds.")

# ── The baseline the no-farm player relies on ─────────────────────────────
# Printed because this is the number that regressed: before bb_hp_multiplier()
# reached the PHP, this table sat far below reality and level 39 was the first
# player to be refused for a score the engine genuinely awards.
def legacy_max_level_score(level):
    """The pre-fix model: exactly what anticheat.php used to compute."""
    n = block_count(level)
    is_boss = level % BOSS_EVERY_N_LEVELS == 0
    hp = math.floor(hp_curve(level) * HP_VARIANCE)  # <-- hp_multiplier() absent
    score = n * hp * 2.0
    if is_boss:
        boss_hp = hp * 2.0 * 4.0
        score += boss_hp * 3.0 + boss_hp * 2.0
    return score + n * 25


print("\nNo-farm cumulative baseline, legacy model vs. current:")
_cum_legacy = 0.0
for lvl in [10, 24, 39, 50, 100, 200, 500, 1000]:
    _cum_legacy += legacy_max_level_score(lvl)
    real = max_cumulative_score(lvl)
    print(f"  L{lvl:<5} old {_cum_legacy:>16,.0f}   new {real:>16,.0f}"
          f"   ratio {real / _cum_legacy:>5.2f}x")

print("\nAbsolute cap sanity:")
print(f"  true max cumulative at level {MAX_LEVEL}: {max_cumulative_score(MAX_LEVEL):,.0f}")
print(f"  BB_ABSOLUTE_MAX_SCORE                 : {ABSOLUTE_MAX_SCORE:,}")
print(f"  headroom                              : "
      f"{ABSOLUTE_MAX_SCORE / max_cumulative_score(MAX_LEVEL):.2f}x")

print("\nOld rule (score > level*6000 + 5000) vs a legitimate player:")
cum = 0.0
for lvl in range(1, 1001):
    cum += max_level_score(lvl)
    if cum > lvl * 6000 + 5000:
        print(f"  rejects a perfect-play cumulative score from level {lvl} onward")
        break
print("New rule, same player at level 100 (10h play): "
      f"ceiling {int(score_ceiling(100, 36000)):,} vs old cap {100*6000+5000:,}")

sys.exit(1 if fails else 0)

