package ir.amiraligrr.blockboom.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R

/**
 * Vazirmatn, the full weight range.
 *
 * This family previously declared only Normal and Bold while the typography
 * scale asked for SemiBold, ExtraBold and — at a dozen call sites — Black.
 * Compose resolves a missing weight by snapping to the nearest one it *does*
 * have, so every heading, price and stat in the app silently rendered as Bold:
 * the visual hierarchy the type scale describes simply was not on screen, and
 * there was no way to tell from the code that anything was wrong.
 *
 * The full range is shipped now, so `fontWeight = FontWeight.Black` on the
 * leaderboard actually produces Black.
 */
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_thin, FontWeight.Thin),
    Font(R.font.vazirmatn_extralight, FontWeight.ExtraLight),
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazirmatn_black, FontWeight.Black)
)

/**
 * The type scale.
 *
 * Two decisions here are specific to an Arabic-script app and would be wrong
 * to copy from a Latin Material scale:
 *
 *  - **No negative letter spacing.** Persian and Arabic are cursive: glyphs
 *    join along a baseline. Tightening the tracking pulls joined letter pairs
 *    apart and, at display sizes, clips the tails of ک/گ and the descenders of
 *    ی/ج. This scale used to open at -1sp on displayLarge. Positive tracking
 *    (0.2–0.8sp on the label tiers) is the correct direction for this script.
 *
 *  - **Line height ≥ 1.45× the size.** Arabic-script ascenders and descenders
 *    are much taller relative to the x-height than Latin's, so the Material
 *    defaults (1.2×) clip the tops of ء and آ and crowd the rows of Persian
 *    text stacked on the home and settings screens.
 *
 * Weights now resolve to real faces because [VazirmatnFamily] ships all nine.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 70.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 58.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 42.sp
    ),
    titleLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 34.sp
    ),
    titleMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 30.sp
    ),
    titleSmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.4.sp
    )
)
