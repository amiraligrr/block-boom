package ir.amiraligrr.blockboom.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ir.amiraligrr.blockboom.data.JoyColorsImpl

// ─────────────────────────────────────────────────────────────────
//  Brand — high-energy arcade palette built around emerald / teal.
//  Production-tuned for contrast, depth and glassmorphism.
// ─────────────────────────────────────────────────────────────────
// Named for its value. This was called `NeonPurple` while holding emerald
// green, and the name was the bug: every "purple accent" written against it
// came out green. It is the brand primary -- the default button colour, the
// glow behind the play button -- so the value is load-bearing and the label
// was not. See [NeonPurple] for the purple that was actually wanted.
val NeonGreen = Color(0xFF22C55E)   // primary "go" green
val NeonCyan = Color(0xFF2DD4BF)   // fresh teal
val NeonPink = Color(0xFFFB7185)   // coral pop
val NeonPurple = Color(0xFFA855F7)  // violet, for accents that must not read as the primary
val NeonGold = Color(0xFFFDE047)   // sunny gold

// Surfaces — deep but alive with green/teal, never plain black.
val DeepSpace = Color(0xFF051A13)
val DeepSpaceVariant = Color(0xFF0A3324)
val DarkSurface = Color(0xFF0E4230)
val DarkSurfaceVariant = Color(0xFF145239)
val DarkSurfaceElevated = Color(0xFF1A5C3F)
val CardGlass = Color(0xFF143D2E)
val CardGlassBorder = Color(0x1AFFFFFF) // 10% white border for glass cards
val OverlayScrim = Color(0xCC051A13) // 80% scrim

// Text
val OffWhite = Color(0xFFF0FDF4)
val OffWhiteDim = Color(0xFFC8E8D4)
val Grey = Color(0xFF86C7A5)
val GreyMuted = Color(0xFF5FA88A)

// Accents
val NeonRed = Color(0xFFFF5A5F)
val NeonOrange = Color(0xFFFF8A3D)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)

// ─────────────────────────────────────────────────────────────────────────
//  Legacy HP-band ramp — NOT what colours a block any more. Kept only so the
//  boss gradient and any future difficulty legend have something to sample.
//
//  This used to be the whole story: colour was a function of HP, ten bands 36°
//  apart. It failed because HP is nearly constant across a level
//  (`generateBlockHP` varies it by only ±10%), so every block on the field
//  landed in the same band and the board came out one flat colour. The ±14° of
//  per-block jitter layered on top was wide enough to look like noise between
//  two neighbours and far too narrow to make a screen feel colourful.
//
//  Difficulty is still legible, but through *depth* — see
//  `blockColorFor` in GameEngine — not through which of these ten you get.
//  The colours players actually see are in [BlockPalette] below.
// ─────────────────────────────────────────────────────────────────────────
val BlockSky      = Color(0xFF66B1D6)   // HP   1-13
val BlockIndigo   = Color(0xFF5E66D8)   // HP  14-21
val BlockViolet   = Color(0xFF9C56D9)   // HP  22-34
val BlockMagenta  = Color(0xFFDB4DC8)   // HP  35-49
val BlockRose     = Color(0xFFDC456D)   // HP  50-74
val BlockOrange   = Color(0xFFDE723C)   // HP  75-109
val BlockGold     = Color(0xFFE1D533)   // HP 110-149
val BlockLime     = Color(0xFF80E32A)   // HP 150-189
val BlockGreen    = Color(0xFF20E53B)   // HP 190-249
val BlockEmerald  = Color(0xFF17E8B0)   // HP 250+

// Kept as aliases so existing call sites and the boss gradient keep compiling.
// They now point at their band equivalents rather than at the old free-form
// colours.
val BlockCyan = BlockSky
val BlockYellow = BlockGold
val BlockAmber = BlockOrange
val BlockPink = BlockRose
val BlockRed = BlockRose
val BlockPurple = BlockEmerald

// ─────────────────────────────────────────────────────────────────────────
//  The obstacle palette — what a block actually wears on screen.
//
//  The HP bands above are still the *difficulty* scale, but they are no longer
//  what colours a block. The reason: within one level nearly every block is
//  generated with almost the same HP (`generateBlockHP` varies it by ±10%), so
//  all of them landed in the same band and rendered the same colour. Per-block
//  jitter on top of that was ±14° of hue — enough to look like noise between
//  two neighbours, not enough to make a screen feel colourful. The result was
//  the thing players called "the blocks all look the same".
//
//  So colour is now drawn from this list instead, dealt out one entry at a time
//  with no repeats (see GameEngine.nextBlockColorIndex), and the HP band only
//  nudges the *depth* of that colour. That buys two things at once:
//
//   1. Every block on screen is a different colour, so a crowded board is
//      readable and each block is individually trackable while it falls.
//   2. A block keeps its colour for its whole life, and its fragments inherit
//      it, so a block shattering looks like that block shattering.
//
//  Sixteen entries, one every 22.5° around the hue circle, all at high
//  saturation and a similar mid-high lightness. Evenly spaced is what stops
//  two dealt colours from being neighbours on the wheel and reading as the
//  same colour; the shared saturation/lightness band is what stops one entry
//  shouting over the rest.
// ─────────────────────────────────────────────────────────────────────────
val BlockPalette: List<Color> = listOf(
    Color(0xFFFF3B30), // 0°   red
    Color(0xFFFF7A00), // 25°  orange
    Color(0xFFFFB300), // 45°  amber
    Color(0xFFFFD400), // 65°  yellow
    Color(0xFFA8E10C), // 90°  chartreuse
    Color(0xFF3ED400), // 130° green
    Color(0xFF00D98F), // 160° spring
    Color(0xFF00D6C2), // 180° teal
    Color(0xFF00C2FF), // 195° azure
    Color(0xFF2E9BFF), // 215° blue
    Color(0xFF5B6BFF), // 250° indigo
    Color(0xFF8B5CF6), // 275° violet
    Color(0xFFC13BFF), // 300° purple
    Color(0xFFF03BE0), // 325° magenta
    Color(0xFFFF3B9E), // 345° pink
    Color(0xFFFF3B6B)  // 355° rose
)

/** The boss block — pulsing magenta-gold. */
val BossColor = Color(0xFFFF4D8D)
val BossColorAlt = Color(0xFFFFB800)

// Extra fun accents used by backgrounds / decorations
val FunLime = Color(0xFFA3E635)
val FunEmerald = Color(0xFF10B981)
val FunSky = Color(0xFF38BDF8)

// ─────────────────────────────────────────────────────────────────
//  Production gradients — pre-built brushes for consistent depth
// ─────────────────────────────────────────────────────────────────
val GradientPrimary = Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFF15803D)))
val GradientPrimaryGlow = Brush.radialGradient(listOf(Color(0xFF22C55E).copy(alpha = 0.45f), Color.Transparent))
val GradientGold = Brush.linearGradient(listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFEA580C)))
val GradientCyan = Brush.linearGradient(listOf(Color(0xFF2DD4BF), Color(0xFF0D9488)))
val GradientCoral = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
val GradientCardGlass = Brush.verticalGradient(listOf(Color(0xFF143D2E).copy(alpha = 0.96f), Color(0xFF0E2E22).copy(alpha = 0.96f)))
val GradientCardGold = Brush.verticalGradient(listOf(Color(0xFF1E3A12), Color(0xFF145239)))
val GradientBackground = Brush.verticalGradient(
    listOf(
        Color(0xFF051A13),
        Color(0xFF0A2E1F),
        Color(0xFF0E4230),
        Color(0xFF0A3324),
        Color(0xFF051A13)
    )
)
val GradientBackgroundLight = Brush.verticalGradient(
    listOf(Color(0xFFF1FBF5), Color(0xFFE0F2E9), Color(0xFFC8E8D4))
)
val GradientButtonPrimary = Brush.verticalGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A)))
val GradientButtonGold = Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))

// Shadows / glows
val ShadowGreen = Color(0xFF052E16)
val GlowGreen = Color(0xFF22C55E).copy(alpha = 0.28f)
val GlowCyan = Color(0xFF2DD4BF).copy(alpha = 0.24f)
val GlowGold = Color(0xFFFDE047).copy(alpha = 0.30f)

// ─────────────────────────────────────────────────────────────────
//  JOY — bright, clean, motivational palette (escape dark dry)
//  Warm off-white base with real ink. Nothing here is pure white or pure
//  black: both are visually harsh, and pure white against saturated emerald
//  measures ~2.4:1, which is why every label on a filled button used to be
//  borderline unreadable.
//
//  ── What changed and why ───────────────────────────────────────────────
//  The base was a mint ramp that bottomed out at #6EE7B7 — a saturated
//  mid-green — with the muted text token a neutral grey (#6B7280). On the
//  lower half of the screen that pairing measured about 2.3:1, so every
//  secondary label dissolved into the background: level counters, mission
//  rows, the "until next boss" caption, every caption on the big cards.
//  It read as designed-for-screenshot text that nobody could actually read
//  on a phone.
//
//  The base is now a warm near-white that stays light all the way down, and
//  every text token is a deep ink with enough weight to clear 4.5:1 on it.
//  "Light" is no longer achieved by making the background brighter — it is
//  achieved by making the background *paler* and the ink darker, which is
//  the only combination in which both can be legible at once.
// ─────────────────────────────────────────────────────────────────
val JoyBackgroundTop = Color(0xFFFDFCF8)
val JoyBackgroundMid = Color(0xFFF6F4ED)
val JoyBackgroundBottom = Color(0xFFEFEDE4)
val JoyMintLight = Color(0xFFB7F0DA)
val JoyMintMid = Color(0xFF34D399)
val JoyEmerald = Color(0xFF0B8F63)
val JoyTeal = Color(0xFF0E8A82)
val JoySky = Color(0xFF0369A1)
val JoyCoral = Color(0xFFE0455E)
val JoyCoralSoft = Color(0xFFF2637A)
val JoyAmber = Color(0xFFB45309)
val JoyAmberLight = Color(0xFFFCD34D)

val JoySurface = Color(0xFFFFFEFA)
val JoySurfaceSoft = Color(0xFFF8F7F2)
val JoyCardBorder = Color(0x1F0F172A) // 12% slate border
val JoyCardShadow = Color(0x140F172A) // soft slate shadow
val JoyTextPrimary = Color(0xFF16211C)
val JoyTextSecondary = Color(0xFF2F4438)
val JoyTextMuted = Color(0xFF5B6B62)
val JoyTextOnDark = Color(0xFFF0FDF4)

val JoyGradientBackground = Brush.verticalGradient(
    listOf(JoyBackgroundTop, JoyBackgroundMid, JoyBackgroundBottom)
)
val JoyGradientCard = Brush.verticalGradient(listOf(Color.White, Color(0xFFF0FDF4)))
val JoyGradientCardElevated = Brush.verticalGradient(listOf(Color.White, Color(0xFFECFDF5)))
val JoyGradientPrimary = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
val JoyGradientTeal = Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0D9488)))
val JoyGradientSky = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0EA5E9)))
val JoyGradientCoral = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFF43F5E)))
val JoyGradientGold = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))
val JoyGradientGoldSoft = Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)))
val JoyGradientSunset = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFFB923C), Color(0xFFFB7185)))

// Legacy alias for light theme compatibility
val LightBackground = JoyBackgroundTop
val LightSurface = JoySurface


// ─────────────────────────────────────────────────────────────────
//  Dark mode color palette
// ─────────────────────────────────────────────────────────────────
val DarkJoyBackgroundTop = Color(0xFF061A12)
val DarkJoyBackgroundMid = Color(0xFF0B2D1E)
val DarkJoyBackgroundBottom = Color(0xFF0E3A23)
val DarkJoyMintLight = Color(0xFF064E3B)
val DarkJoyMintMid = Color(0xFF059669)
val DarkJoyEmerald = Color(0xFF34D399)
val DarkJoyTeal = Color(0xFF2DD4BF)
val DarkJoySky = Color(0xFF38BDF8)
val DarkJoyCoral = Color(0xFFF87171)
val DarkJoyCoralSoft = Color(0xFFF87171)
val DarkJoyAmber = Color(0xFFFBBF24)
val DarkJoyAmberLight = Color(0xFFFDE68A)

val DarkJoySurface = Color(0xFF1E3A32)
val DarkJoySurfaceSoft = Color(0xFF143D2E)
val DarkJoyCardBorder = Color(0x4D10B981) // 30% emerald border
val DarkJoyCardShadow = Color(0x66000000) // soft shadow
val DarkJoyTextPrimary = Color(0xFFECFDF5)
val DarkJoyTextSecondary = Color(0xFFA7F3D0)
val DarkJoyTextMuted = Color(0xFF9CA3AF)
val DarkJoyTextOnDark = Color(0xFF061A12)

val DarkJoyGradientBackground = Brush.verticalGradient(
    listOf(DarkJoyBackgroundTop, DarkJoyBackgroundMid, DarkJoyMintLight, DarkJoyBackgroundBottom)
)
val DarkJoyGradientCard = Brush.verticalGradient(listOf(DarkJoySurface, DarkJoySurfaceSoft))
val DarkJoyGradientCardElevated = Brush.verticalGradient(listOf(DarkJoySurface, Color(0xFF234A3E)))
val DarkJoyGradientPrimary = Brush.linearGradient(listOf(DarkJoyEmerald, DarkJoyTeal))
val DarkJoyGradientTeal = Brush.linearGradient(listOf(DarkJoyTeal, Color(0xFF0D9488)))
val DarkJoyGradientSky = Brush.linearGradient(listOf(DarkJoySky, Color(0xFF0EA5E9)))
val DarkJoyGradientCoral = Brush.linearGradient(listOf(DarkJoyCoral, Color(0xFFE11D48)))
val DarkJoyGradientGold = Brush.linearGradient(listOf(DarkJoyAmberLight, DarkJoyAmber))
val DarkJoyGradientGoldSoft = Brush.verticalGradient(listOf(Color(0xFF1E3A32), Color(0xFF143D2E)))
val DarkJoyGradientSunset = Brush.linearGradient(listOf(DarkJoyAmberLight, Color(0xFFFB923C), DarkJoyCoral))

/**
 * Dark-mode counterpart to `JoyColors.Light`.
 *
 * The app previously had no dark mode at all: the theme was hardcoded to
 * `lightColorScheme` and every screen imported the light `Joy*` constants
 * directly, which left this whole dark palette unused.
 */
val JoyDarkColors = JoyColorsImpl(
    backgroundTop = DarkJoyBackgroundTop,
    backgroundMid = DarkJoyBackgroundMid,
    backgroundBottom = DarkJoyBackgroundBottom,
    surface = DarkJoySurface,
    surfaceVariant = DarkJoySurfaceSoft,
    cardBackground = DarkJoySurface,
    textPrimary = DarkJoyTextPrimary,
    textSecondary = DarkJoyTextSecondary,
    textMuted = DarkJoyTextMuted,
    cardBorder = DarkJoyCardBorder,
    cardShadow = DarkJoyCardShadow,
    primary = DarkJoyEmerald,
    primaryVariant = DarkJoyMintMid,
    secondary = DarkJoySky,
    accent = DarkJoyAmber,
    error = DarkJoyCoral,
    onPrimary = Color(0xFF04211A),
    onSurface = DarkJoyTextPrimary,
    divider = DarkJoyCardBorder,
    navigationBar = DarkJoyBackgroundTop,
    statusBar = DarkJoyBackgroundTop
)

// ─────────────────────────────────────────────────────────────────
//  Theme-aware color provider via CompositionLocal
// ─────────────────────────────────────────────────────────────────
data class JoyThemeColors(
    /**
     * Which palette is live.
     *
     * Almost everything is better served by a named colour slot, but a few
     * effects are genuinely *inverted* rather than re-tinted — the background
     * vignette has to darken edges in dark mode where it lightens them in
     * light mode, and a glow has to be dialled down rather than swapped.
     */
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val mintLight: Color,
    val mintMid: Color,
    val emerald: Color,
    val teal: Color,
    val sky: Color,
    val coral: Color,
    val coralSoft: Color,
    val amber: Color,
    val amberLight: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val cardBorder: Color,
    val cardShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnDark: Color,
    /**
     * Neutral wells: progress/meter tracks, segmented-control containers and
     * idle step dots.
     *
     * These were literal `#F3F4F6` / `#ECFDF5` / `#E5E7EB` greys in ~40 places.
     * Those are the standard Tailwind greys, so they read as "generic UI" and
     * sit outside the mint/emerald brand — and they have no dark counterpart
     * at all, which is why a dark-mode player got near-white wells punched
     * through dark cards.
     */
    val track: Color,
    val trackBorder: Color,
    val chip: Color,
    val dotIdle: Color,
    /** Text/icon colour that sits ON a saturated gold or amber fill. */
    val onGold: Color,
    /**
     * Text/icon colour that sits ON the primary fill (`emerald`).
     *
     * The same dark ink in both themes, which looks like an oversight and is
     * not: the emerald a button is filled with is a mid-tone in both palettes
     * (#10B981 light, #34D399 dark), and dark ink is the only readable option on
     * either. White on light-mode emerald lands around 2.4:1, which is why the
     * tick on a selected skin and the label on a selected tab used to be close
     * to invisible.
     */
    val onPrimary: Color,
    /**
     * Strength of the white top-gloss painted over buttons and tiles.
     *
     * A white gloss is invisible on a white card but a visible haze on a dark
     * one, so it has to be theme-driven rather than a constant alpha.
     */
    val glossAlpha: Float,
    val gradientBackground: Brush,
    val gradientCard: Brush,
    val gradientCardElevated: Brush,
    val gradientPrimary: Brush,
    val gradientTeal: Brush,
    val gradientSky: Brush,
    val gradientCoral: Brush,
    val gradientGold: Brush,
    val gradientGoldSoft: Brush,
    val gradientSunset: Brush
)

val LightJoyThemeColors = JoyThemeColors(
    isDark = false,
    backgroundTop = JoyBackgroundTop,
    backgroundMid = JoyBackgroundMid,
    backgroundBottom = JoyBackgroundBottom,
    mintLight = JoyMintLight,
    mintMid = JoyMintMid,
    emerald = JoyEmerald,
    teal = JoyTeal,
    sky = JoySky,
    coral = JoyCoral,
    coralSoft = JoyCoralSoft,
    amber = JoyAmber,
    amberLight = JoyAmberLight,
    surface = JoySurface,
    surfaceSoft = JoySurfaceSoft,
    cardBorder = JoyCardBorder,
    cardShadow = JoyCardShadow,
    textPrimary = JoyTextPrimary,
    textSecondary = JoyTextSecondary,
    textMuted = JoyTextMuted,
    textOnDark = JoyTextOnDark,
    track = Color(0xFFECFDF5),
    trackBorder = JoyCardBorder,
    chip = Color(0xFFF3F4F6),
    dotIdle = Color(0xFFE5E7EB),
    onGold = Color(0xFF78350F),
    onPrimary = Color(0xFF04211A),
    glossAlpha = 0.18f,
    gradientBackground = JoyGradientBackground,
    gradientCard = JoyGradientCard,
    gradientCardElevated = JoyGradientCardElevated,
    gradientPrimary = JoyGradientPrimary,
    gradientTeal = JoyGradientTeal,
    gradientSky = JoyGradientSky,
    gradientCoral = JoyGradientCoral,
    gradientGold = JoyGradientGold,
    gradientGoldSoft = JoyGradientGoldSoft,
    gradientSunset = JoyGradientSunset
)

val DarkJoyThemeColors = JoyThemeColors(
    isDark = true,
    backgroundTop = DarkJoyBackgroundTop,
    backgroundMid = DarkJoyBackgroundMid,
    backgroundBottom = DarkJoyBackgroundBottom,
    mintLight = DarkJoyMintLight,
    mintMid = DarkJoyMintMid,
    emerald = DarkJoyEmerald,
    teal = DarkJoyTeal,
    sky = DarkJoySky,
    coral = DarkJoyCoral,
    coralSoft = DarkJoyCoralSoft,
    amber = DarkJoyAmber,
    amberLight = DarkJoyAmberLight,
    surface = DarkJoySurface,
    surfaceSoft = DarkJoySurfaceSoft,
    cardBorder = DarkJoyCardBorder,
    cardShadow = DarkJoyCardShadow,
    textPrimary = DarkJoyTextPrimary,
    textSecondary = DarkJoyTextSecondary,
    textMuted = DarkJoyTextMuted,
    textOnDark = DarkJoyTextOnDark,
    track = Color(0xFF0E3A2C),
    trackBorder = DarkJoyCardBorder,
    chip = Color(0xFF16332B),
    dotIdle = Color(0xFF2A4B40),
    onGold = Color(0xFF3F2A06),
    onPrimary = Color(0xFF04211A),
    glossAlpha = 0.10f,
    gradientBackground = DarkJoyGradientBackground,
    gradientCard = DarkJoyGradientCard,
    gradientCardElevated = DarkJoyGradientCardElevated,
    gradientPrimary = DarkJoyGradientPrimary,
    gradientTeal = DarkJoyGradientTeal,
    gradientSky = DarkJoyGradientSky,
    gradientCoral = DarkJoyGradientCoral,
    gradientGold = DarkJoyGradientGold,
    gradientGoldSoft = DarkJoyGradientGoldSoft,
    gradientSunset = DarkJoyGradientSunset
)

internal val LocalJoyThemeColors = staticCompositionLocalOf { LightJoyThemeColors }

/** Provides theme-aware Joy colors that automatically adapt to light/dark mode. */
@Composable
fun provideJoyThemeColors(isDark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalJoyThemeColors provides (if (isDark) DarkJoyThemeColors else LightJoyThemeColors)) {
        content()
    }
}

/** Access the current theme-aware Joy colors. */
@Composable
fun joyThemeColors(): JoyThemeColors = LocalJoyThemeColors.current
