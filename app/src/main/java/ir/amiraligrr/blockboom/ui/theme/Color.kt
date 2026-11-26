package ir.amiraligrr.blockboom.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ir.amiraligrr.blockboom.data.JoyColorsImpl

// ─────────────────────────────────────────────────────────────────────────────
//  BLOCK BOOM 1.2 — NEBULA
//
//  One identity, two moods. The dark palette is the game's home: deep navy,
//  violet glass and a cyan rim light. The light palette is the same furniture
//  in a soft lavender room, so the app never turns into a different product
//  when the phone is set to light mode.
//
//  Names such as `emerald` and `teal` are kept because ~1,500 call sites use
//  them as *semantic* slots (primary, secondary, accent). Their values are now
//  the Nebula palette, not the old mint one. Renaming them would have touched
//  every screen for no visual gain, and the slot names still describe their
//  role in the hierarchy rather than a specific hue.
// ─────────────────────────────────────────────────────────────────────────────

// Brand aliases used across effects, buttons and the block palette.
val NeonGreen = Color(0xFF8157F5)   // primary action / brand violet
val NeonCyan = Color(0xFF42D9FF)    // electric rim light
val NeonPink = Color(0xFFF47AC8)    // playful secondary
val NeonPurple = Color(0xFFA78BFA)  // lifted violet for dark surfaces
val NeonGold = Color(0xFFFFD166)    // reward gold

// Surfaces — navy at the bottom, violet glass above it.
val DeepSpace = Color(0xFF101024)
val DeepSpaceVariant = Color(0xFF191432)
val DarkSurface = Color(0xFF191432)
val DarkSurfaceVariant = Color(0xFF211B3F)
val DarkSurfaceElevated = Color(0xFF2A2350)
val CardGlass = Color(0xFF191432)
val CardGlassBorder = Color(0x26FFFFFF)
val OverlayScrim = Color(0xE6101024)

// Text
val OffWhite = Color(0xFFF7F5FF)
val OffWhiteDim = Color(0xFFD5D0E8)
val Grey = Color(0xFFA6A2BC)
val GreyMuted = Color(0xFF7E789A)

// Accents
val NeonRed = Color(0xFFFF6B81)
val NeonOrange = Color(0xFFFF9A62)
val SuccessGreen = Color(0xFF4ADE9B)
val WarningAmber = Color(0xFFFFD166)

// ─────────────────────────────────────────────────────────────────────────────
//  Block colours — the board itself stays saturated and readable. These are
//  dealt one per block, so neighbouring obstacles never share a hue.
// ─────────────────────────────────────────────────────────────────────────────
val BlockSky      = Color(0xFF66B1D6)
val BlockIndigo   = Color(0xFF5E66D8)
val BlockViolet   = Color(0xFF9C56D9)
val BlockMagenta  = Color(0xFFDB4DC8)
val BlockRose     = Color(0xFFDC456D)
val BlockOrange   = Color(0xFFDE723C)
val BlockGold     = Color(0xFFE1D533)
val BlockLime     = Color(0xFF80E32A)
val BlockGreen    = Color(0xFF20E53B)
val BlockEmerald  = Color(0xFF17E8B0)

val BlockCyan = BlockSky
val BlockYellow = BlockGold
val BlockAmber = BlockOrange
val BlockPink = BlockRose
val BlockRed = BlockRose
val BlockPurple = BlockEmerald

val BlockPalette: List<Color> = listOf(
    Color(0xFFFF3B30),
    Color(0xFFFF7A00),
    Color(0xFFFFB300),
    Color(0xFFFFD400),
    Color(0xFFA8E10C),
    Color(0xFF3ED400),
    Color(0xFF00D98F),
    Color(0xFF00D6C2),
    Color(0xFF00C2FF),
    Color(0xFF2E9BFF),
    Color(0xFF5B6BFF),
    Color(0xFF8B5CF6),
    Color(0xFFC13BFF),
    Color(0xFFF03BE0),
    Color(0xFFFF3B9E),
    Color(0xFFFF3B6B)
)

val BossColor = Color(0xFFFF4D8D)
val BossColorAlt = Color(0xFFFFB800)

val FunLime = Color(0xFFA3E635)
val FunEmerald = Color(0xFF10B981)
val FunSky = Color(0xFF38BDF8)

// ─────────────────────────────────────────────────────────────────────────────
//  Production gradients
// ─────────────────────────────────────────────────────────────────────────────
val GradientPrimary = Brush.linearGradient(
    listOf(Color(0xFF8157F5), Color(0xFF6D46E8), Color(0xFF5B3CC4))
)
val GradientPrimaryGlow = Brush.radialGradient(
    listOf(Color(0xFF8157F5).copy(alpha = 0.45f), Color.Transparent)
)
val GradientGold = Brush.linearGradient(listOf(Color(0xFFFFE29A), Color(0xFFFFD166), Color(0xFFF2A93B)))
val GradientCyan = Brush.linearGradient(listOf(Color(0xFF7DE7FF), Color(0xFF42D9FF), Color(0xFF1E8FB8)))
val GradientCoral = Brush.linearGradient(listOf(Color(0xFFFFA7DC), Color(0xFFF47AC8), Color(0xFFC0266C)))
val GradientCardGlass = Brush.verticalGradient(
    listOf(Color(0xFF201942).copy(alpha = 0.97f), Color(0xFF17122F).copy(alpha = 0.97f))
)
val GradientCardGold = Brush.verticalGradient(listOf(Color(0xFF332646), Color(0xFF241B3D)))
val GradientBackground = Brush.verticalGradient(
    listOf(
        Color(0xFF101024),
        Color(0xFF15112D),
        Color(0xFF1B153A),
        Color(0xFF17122F),
        Color(0xFF101024)
    )
)
val GradientBackgroundLight = Brush.verticalGradient(
    listOf(Color(0xFFFBFAFF), Color(0xFFF3EEFF), Color(0xFFE9E1FF))
)
val GradientButtonPrimary = Brush.verticalGradient(
    listOf(Color(0xFF8157F5), Color(0xFF7048E8), Color(0xFF5B3CC4))
)
val GradientButtonGold = Brush.verticalGradient(listOf(Color(0xFFFFE9A8), Color(0xFFFFD166)))

// Shadows / glows
val ShadowGreen = Color(0xFF08051A)
val GlowGreen = Color(0xFF8157F5).copy(alpha = 0.30f)
val GlowCyan = Color(0xFF42D9FF).copy(alpha = 0.24f)
val GlowGold = Color(0xFFFFD166).copy(alpha = 0.28f)

// ─────────────────────────────────────────────────────────────────────────────
//  Light palette — soft lavender paper, deep violet ink.
// ─────────────────────────────────────────────────────────────────────────────
val JoyBackgroundTop = Color(0xFFFBFAFF)
val JoyBackgroundMid = Color(0xFFF3EEFF)
val JoyBackgroundBottom = Color(0xFFE9E1FF)
val JoyMintLight = Color(0xFFE4DBFF)
val JoyMintMid = Color(0xFF8157F5)
val JoyEmerald = Color(0xFF6D46E8)
val JoyTeal = Color(0xFF8B5CF6)
val JoySky = Color(0xFF0B6178)
val JoyCoral = Color(0xFFC0266C)
val JoyCoralSoft = Color(0xFFF47AC8)
val JoyAmber = Color(0xFF9A6700)
val JoyAmberLight = Color(0xFFFFD166)

val JoySurface = Color(0xFFFFFFFF)
val JoySurfaceSoft = Color(0xFFF7F4FF)
val JoyCardBorder = Color(0xFFE5DEF8)
val JoyCardShadow = Color(0x1A17132E)
val JoyTextPrimary = Color(0xFF17132E)
val JoyTextSecondary = Color(0xFF41395F)
val JoyTextMuted = Color(0xFF635C7B)
val JoyTextOnDark = Color(0xFFF7F5FF)

val JoyGradientBackground = Brush.verticalGradient(
    listOf(JoyBackgroundTop, JoyBackgroundMid, JoyBackgroundBottom)
)
val JoyGradientCard = Brush.verticalGradient(listOf(Color.White, Color(0xFFF8F4FF)))
val JoyGradientCardElevated = Brush.verticalGradient(listOf(Color.White, Color(0xFFF0EAFF)))
val JoyGradientPrimary = Brush.linearGradient(
    listOf(Color(0xFF8157F5), Color(0xFF7048E8), Color(0xFF5B3CC4))
)
val JoyGradientTeal = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C5CFA)))
val JoyGradientSky = Brush.linearGradient(listOf(Color(0xFF42D9FF), Color(0xFF0B6178)))
val JoyGradientCoral = Brush.linearGradient(listOf(Color(0xFFF47AC8), Color(0xFFC0266C)))
val JoyGradientGold = Brush.linearGradient(listOf(Color(0xFFFFE29A), Color(0xFFFFD166), Color(0xFFF2A93B)))
val JoyGradientGoldSoft = Brush.verticalGradient(listOf(Color(0xFFFFFAEB), Color(0xFFFFF0C7)))
val JoyGradientSunset = Brush.linearGradient(
    listOf(Color(0xFFFFE29A), Color(0xFFF47AC8), Color(0xFF8157F5))
)

// Legacy aliases for light theme compatibility.
val LightBackground = JoyBackgroundTop
val LightSurface = JoySurface

// ─────────────────────────────────────────────────────────────────────────────
//  Dark palette — the signature Nebula look.
// ─────────────────────────────────────────────────────────────────────────────
val DarkJoyBackgroundTop = Color(0xFF101024)
val DarkJoyBackgroundMid = Color(0xFF15112D)
val DarkJoyBackgroundBottom = Color(0xFF1B153A)
val DarkJoyMintLight = Color(0xFF2E2554)
val DarkJoyMintMid = Color(0xFF8157F5)
val DarkJoyEmerald = Color(0xFFA78BFA)
val DarkJoyTeal = Color(0xFFC4B5FD)
val DarkJoySky = Color(0xFF42D9FF)
val DarkJoyCoral = Color(0xFFF47AC8)
val DarkJoyCoralSoft = Color(0xFFF9A8D9)
val DarkJoyAmber = Color(0xFFFFD166)
val DarkJoyAmberLight = Color(0xFFFFE9A8)

val DarkJoySurface = Color(0xFF191432)
val DarkJoySurfaceSoft = Color(0xFF211B3F)
val DarkJoyCardBorder = Color(0x4D8B6DFF)
val DarkJoyCardShadow = Color(0x99000000)
val DarkJoyTextPrimary = Color(0xFFF7F5FF)
val DarkJoyTextSecondary = Color(0xFFCFC9E6)
val DarkJoyTextMuted = Color(0xFFA6A2BC)
val DarkJoyTextOnDark = Color(0xFF101024)

val DarkJoyGradientBackground = Brush.verticalGradient(
    listOf(
        DarkJoyBackgroundTop,
        DarkJoyBackgroundMid,
        DarkJoyBackgroundBottom,
        Color(0xFF15112D),
        DarkJoyBackgroundTop
    )
)
val DarkJoyGradientCard = Brush.verticalGradient(listOf(DarkJoySurface, DarkJoySurfaceSoft))
val DarkJoyGradientCardElevated = Brush.verticalGradient(
    listOf(Color(0xFF211B3F), Color(0xFF2A2350))
)
val DarkJoyGradientPrimary = Brush.linearGradient(
    listOf(Color(0xFF8157F5), Color(0xFF7048E8), Color(0xFF5B3CC4))
)
val DarkJoyGradientTeal = Brush.linearGradient(listOf(Color(0xFFC4B5FD), Color(0xFF8157F5)))
val DarkJoyGradientSky = Brush.linearGradient(listOf(Color(0xFF7DE7FF), Color(0xFF1E8FB8)))
val DarkJoyGradientCoral = Brush.linearGradient(listOf(Color(0xFFF9A8D9), Color(0xFFC0266C)))
val DarkJoyGradientGold = Brush.linearGradient(
    listOf(Color(0xFFFFE9A8), Color(0xFFFFD166), Color(0xFFF2A93B))
)
val DarkJoyGradientGoldSoft = Brush.verticalGradient(
    listOf(Color(0xFF2A2140), Color(0xFF221B3F))
)
val DarkJoyGradientSunset = Brush.linearGradient(
    listOf(Color(0xFFFFE9A8), Color(0xFFF47AC8), Color(0xFF8157F5))
)

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
    onPrimary = DarkJoyTextOnDark,
    onSurface = DarkJoyTextPrimary,
    divider = DarkJoyCardBorder,
    navigationBar = DarkJoyBackgroundTop,
    statusBar = DarkJoyBackgroundTop
)

// ─────────────────────────────────────────────────────────────────────────────
//  Theme-aware colour provider
// ─────────────────────────────────────────────────────────────────────────────
data class JoyThemeColors(
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
    val track: Color,
    val trackBorder: Color,
    val chip: Color,
    val dotIdle: Color,
    val onGold: Color,
    val onPrimary: Color,
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
    track = Color(0xFFEDE7FF),
    trackBorder = JoyCardBorder,
    chip = Color(0xFFF2EDFF),
    dotIdle = Color(0xFFDCD4F2),
    onGold = Color(0xFF3D2A00),
    onPrimary = Color.White,
    glossAlpha = 0.16f,
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
    track = Color(0xFF241D45),
    trackBorder = DarkJoyCardBorder,
    chip = Color(0xFF251E48),
    dotIdle = Color(0xFF3A3060),
    onGold = Color(0xFF3D2A00),
    onPrimary = Color(0xFF17132E),
    glossAlpha = 0.08f,
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

internal val LocalJoyThemeColors = staticCompositionLocalOf { DarkJoyThemeColors }

@Composable
fun provideJoyThemeColors(isDark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalJoyThemeColors provides (if (isDark) DarkJoyThemeColors else LightJoyThemeColors)
    ) {
        content()
    }
}

@Composable
fun joyThemeColors(): JoyThemeColors = LocalJoyThemeColors.current
