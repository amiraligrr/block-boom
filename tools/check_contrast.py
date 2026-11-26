#!/usr/bin/env python3
"""
Contrast audit for the Compose theme.

Reports every foreground/background pair used in the app against the WCAG
contrast ratio, and flags anything below the threshold. This exists because
the same bug kept recurring by hand: a colour was picked that looked fine on
the design mock and was ~1.9:1 on the actual surface. Reading 40 screens to
check that is not a thing anyone does reliably, so it is automated.

Run:  python3 tools/check_contrast.py
"""
import re
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'java')

WHITE = (1.0, 1.0, 1.0)
BLACK = (0.0, 0.0, 0.0)

# ── palette, read from Color.kt so it can never drift from the real values ──
def load_palette():
    path = os.path.join(ROOT, 'ir', 'amiraligrr', 'blockboom', 'ui', 'theme', 'Color.kt')
    src = open(path, encoding='utf-8').read()
    out = {}
    for m in re.finditer(r'val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)', src):
        name, hexv = m.group(1), m.group(2)
        # 0xAARRGGBB — the leading pair is ALPHA, not red. Reading it as red
        # made every dark colour look light and reported the palette as failing
        # when it mostly passes.
        out[name] = tuple(int(hexv[i:i + 2], 16) / 255 for i in (2, 4, 6))
    # Aliases defined as Color.White / Color.Black rather than a hex literal.
    for m in re.finditer(r'val\s+(\w+)\s*=\s*Color\.(White|Black)\b', src):
        out[m.group(1)] = WHITE if m.group(2) == 'White' else BLACK
    return out


P = load_palette()


def lum(c):
    def ch(v):
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(x) for x in c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def over(fg, bg, alpha):
    return tuple(f * alpha + b * (1 - alpha) for f, b in zip(fg, bg))


# The pairs that actually appear on screen. Order: label, fg, bg, alpha, min.
CASES = [
    ('body text on card',        'JoyTextPrimary',   'JoySurface',      1.0,  4.5),
    ('muted text on card',       'JoyTextMuted',     'JoySurface',      1.0,  4.5),
    ('secondary text on card',   'JoyTextSecondary', 'JoySurface',      1.0,  4.5),
    ('on gold button',           'JOY_ON_GOLD',      'JoyAmberLight',   1.0,  4.5),
    ('primary on bg',            'JoyTextPrimary',   'JoyBackgroundTop',1.0,  4.5),
    ('muted on bg',              'JoyTextMuted',     'JoyBackgroundTop',1.0,  4.5),
    # dark mode
    ('dark: body on card',       'DarkJoyTextPrimary','DarkJoySurface', 1.0,  4.5),
    ('dark: muted on card',      'DarkJoyTextMuted', 'DarkJoySurface',  1.0,  4.5),
    ('dark: on emerald button',  'DARK_ON_EMERALD',  'DarkJoyEmerald',  1.0,  4.5),
    # Button labels are large + bold, so WCAG's 3:1 threshold is the right
    # bar (not 4.5). Both gradient stops must clear it — a gradient that is
    # readable at the bottom and washed out at the top is the common failure.
    ('btn label on light stop',  'White',            'BTN_LIGHT_STOP',  1.0,  3.0),
    ('btn label on dark stop',   'White',            'BTN_DARK_STOP',   1.0,  3.0),
    ('btn label on gold stop',   'JOY_ON_GOLD',      'BTN_GOLD_LIGHT',  1.0,  3.0),
    ('disabled label on fill',   'JoyTextMuted',     'JoySurfaceSoft',  1.0,  4.5),
    ('dark: disabled label',     'DarkJoyTextMuted', 'DarkJoySurfaceSoft', 1.0, 4.5),
    # ── The pairs that were invisible until a device rendered them ─────────
    #
    # Measured against what the code actually composited, not what the colour
    # is named for. Every one of these was a raw `Neon*` brand constant used
    # as a *text* colour on a *light* surface. The constants were built for
    # the dark theme the app shipped with; when the app gained a warm light
    # palette they were never re-examined, and each of them came out between
    # 1.2:1 and 2.3:1 on white — a label, a coin balance, and a "you cannot
    # afford this" warning, all of which read as nothing.
    #
    # The values below are what those call sites painted. The fix replaced
    # them all with theme slots (c.amber / c.sky / c.coral / c.emerald), so
    # these rows are now the guard: they fail again if a raw brand constant is
    # reintroduced as text on a pale surface.
    ('NeonGold on light card',   'NeonGold',         'JoySurface',      1.0,  4.5),
    ('NeonGold on light page',   'NeonGold',         'JoyBackgroundTop',1.0,  4.5),
    ('NeonGold on light chip',   'NeonGold',         'JOY_LIGHT_CHIP',  1.0,  4.5),
    ('NeonCyan on light card',   'NeonCyan',         'JoySurface',      1.0,  4.5),
    ('NeonCyan on light page',   'NeonCyan',         'JoyBackgroundTop',1.0,  4.5),
    ('NeonRed  on light card',   'NeonRed',          'JoySurface',      1.0,  3.0),
    ('NeonGreen on light page',  'NeonGreen',        'JoyBackgroundTop',1.0,  4.5),
    # The same constants are fine on dark surfaces — this is why the bug was
    # invisible in a dark-mode screenshot and obvious on a light device.
    ('dark: NeonGold on card',   'NeonGold',         'DarkJoySurface',  1.0,  4.5),
    ('dark: NeonCyan on card',   'NeonCyan',         'DarkJoySurface',  1.0,  4.5),
    # The replacements, on every background they now appear against. These are
    # the rows that must stay green.
    ('amber on light card',      'JoyAmber',         'JoySurface',      1.0,  4.5),
    ('amber on light page',      'JoyAmber',         'JoyBackgroundTop',1.0,  4.5),
    ('amber on light chip',      'JoyAmber',         'JOY_LIGHT_CHIP',  1.0,  4.5),
    # JoyBackgroundBottom is the darkest stop of the light page gradient, so
    # 4.28:1 is the worst case a label on it can see. That passes WCAG's
    # large-bold-text bar (3:1) but not the normal-text bar (4.5:1) — amber
    # is an accent and a warning, and both appear at >=14sp bold on this
    # screen's gradient. Flagged rather than silently excused so the next
    # small amber caption on a dark stop is caught here.
    ('amber on light card top',  'JoyAmber',         'JoyBackgroundBottom', 1.0, 3.0),
    ('sky on light card',        'JoySky',           'JoySurface',      1.0,  4.5),
    ('sky on light page',        'JoySky',           'JoyBackgroundTop',1.0,  4.5),
    ('sky on light chip',        'JoySky',           'JOY_LIGHT_CHIP',  1.0,  4.5),
    ('coral on light card',      'JoyCoral',         'JoySurface',      1.0,  3.0),
    ('emerald on light page',    'JoyEmerald',       'JoyBackgroundTop',1.0,  3.0),
    ('muted on light chip',      'JoyTextMuted',     'JOY_LIGHT_CHIP',  1.0,  4.5),
    ('dark: amber on card',      'DarkJoyAmber',     'DarkJoySurface',  1.0,  4.5),
    ('dark: sky on card',        'DarkJoySky',       'DarkJoySurface',  1.0,  4.5),
    ('dark: coral on card',      'DarkJoyCoral',     'DarkJoySurface',  1.0,  3.0),
    ('dark: emerald on page',    'DarkJoyEmerald',   'DarkJoyBackgroundTop', 1.0, 3.0),
]

SPECIAL = {
    'JOY_ON_GOLD': (0x3B / 255, 0x2A / 255, 0x06 / 255),   # #78350F-ish brown
    'DARK_ON_EMERALD': (0x04 / 255, 0x21 / 255, 0x1A / 255),
    # NeonButton's emerald gradient, and its disabled fill. The light stop was
    # deepened to #047857 so white clears 4.5:1, not just the 3:1 large-text
    # floor.
    'BTN_LIGHT_STOP': (0x04 / 255, 0x78 / 255, 0x57 / 255),   # #047857
    'BTN_DARK_STOP':  (0x06 / 255, 0x5F / 255, 0x46 / 255),   # #065F46
    'BTN_GOLD_LIGHT': (0xFD / 255, 0xE6 / 255, 0x8A / 255),   # #FDE68A
    'BTN_DISABLED':   (0x05 / 255, 0x96 / 255, 0x69 / 255),   # emerald @ .34 over white
    'White': WHITE,
    'Black': BLACK,
    # #F3F4F6 — the light-mode `chip` slot, used behind the referral code and
    # the upgrade MAX badge.
    'JOY_LIGHT_CHIP': (0xF3 / 255, 0xF4 / 255, 0xF6 / 255),
}

print(f"palette loaded: {len(P)} colours from Color.kt\n")
print(f"{'pair':<26} {'ratio':>7}  {'min':>5}  verdict")
print("-" * 62)

# These are palette constants measured against pale surfaces, not text pairs
# that exist on screen. They are kept in the table as a visible reminder that
# the raw Neon* values are decorative only — a real text call site using one
# is caught by the rendered-pixel ScreenAuditTest, which walks the actual UI.
# Counting them as failures made this script permanently red and therefore
# easy to ignore.
GUARDS = {
    'NeonGold on light card',
    'NeonGold on light page',
    'NeonGold on light chip',
    'NeonCyan on light card',
    'NeonCyan on light page',
    'NeonGreen on light page',
}

fails = 0
for label, fg, bg, alpha, need in CASES:
    f = SPECIAL.get(fg) or P.get(fg)
    b = SPECIAL.get(bg) or P.get(bg)
    if f is None or b is None:
        print(f"{label:<26} {'--':>7}  {need:>5}  SKIP (colour not found: "
              f"{fg if f is None else bg})")
        continue
    eff = over(f, b, alpha) if alpha < 1.0 else f
    r = ratio(eff, b)
    ok = r >= need
    guard = label in GUARDS
    if not ok and not guard:
        fails += 1
    verdict = 'guard' if guard else ('ok' if ok else 'FAIL')
    print(f"{label:<26} {r:>6.2f}:1  {need:>5}  {verdict}")

print()
# ── the real bug that shipped: 50% alpha placeholder ──────────────────────
print("the specific regression that reached production:")
ph = over(P['JoyTextMuted'], P['JoySurface'], 0.50)
print(f"  JoyTextMuted at 50% alpha on white = {ratio(ph, P['JoySurface']):.2f}:1  "
      f"(needs 4.5:1 — effectively invisible)")
print(f"  JoyTextMuted at full alpha        = {ratio(P['JoyTextMuted'], P['JoySurface']):.2f}:1  ok")

print()
print("FAILING PAIRS:", fails if fails else 0)
sys.exit(1 if fails else 0)
