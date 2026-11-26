package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

// ─────────────────────────────────────────────────────────────────
//  The app's surfaces.
//
//  Before this file the card recipe was copy-pasted into roughly forty
//  call sites as
//
//      .clip(RoundedCornerShape(20.dp))
//      .background(Color.White)
//      .border(1.dp, JoyCardBorder, RoundedCornerShape(20.dp))
//      .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = JoyCardShadow, …)
//      .padding(16.dp)
//
//  and had already drifted: 12/14/20/22/26dp corners, 8/10/12/20dp shadows,
//  14/16/22dp padding, and `Color.White` on some cards but
//  `joyThemeColors().surface` on others. A dark-mode player got a
//  patchwork because half the call sites painted white unconditionally.
//
//  Everything below reads the active palette, so a surface is correct in
//  either theme by construction and the recipe has one home.
// ─────────────────────────────────────────────────────────────────

/** The corner-radius scale. Use these instead of raw dp. */
object JoyShape {
    /** Inline chips, badges, step dots. */
    val Chip = RoundedCornerShape(12.dp)
    /** Cards, tiles, list rows. */
    val Card = RoundedCornerShape(20.dp)
    /** Large feature cards, dialogs, sheets. */
    val Large = RoundedCornerShape(26.dp)
    /** Fully rounded — pills, avatars, the play button. */
    val Pill = RoundedCornerShape(50)
}

/** The elevation scale for resting surfaces. */
object JoyElevation {
    /** Flush — segmented controls, tracks, wells. */
    val None = 0.dp
    /** Rows and inline tiles that sit on a card. */
    val Low = 4.dp
    /** The default card. */
    val Card = 10.dp
    /** Cards that carry the primary action of a screen. */
    val High = 14.dp
    /** Dialogs and modals. */
    val Modal = 20.dp
}

/**
 * The standard card.
 *
 * @param containerColor `null` picks the themed surface.
 * @param bordered `false` drops the hairline — for cards already nested
 *   inside another card, where a second border only reads as a seam.
 */
@Composable
fun JoyCard(
    modifier: Modifier = Modifier,
    shape: Shape = JoyShape.Card,
    containerColor: Color? = null,
    borderColor: Color? = null,
    elevation: Dp = JoyElevation.Card,
    bordered: Boolean = true,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = c.cardShadow,
                spotColor = c.cardShadow
            )
            .clip(shape)
            .background(containerColor ?: c.surface)
            .then(
                if (bordered) Modifier.border(1.dp, borderColor ?: c.cardBorder, shape)
                else Modifier
            )
            .padding(contentPadding),
        content = content
    )
}

/**
 * A card that can be tapped.
 *
 * Adds a press response a plain [JoyCard] plus [Modifier.clickable] does not:
 * the whole card scales slightly, which survives being read at a glance far
 * better than a ripple that a white card largely swallows.
 */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = JoyShape.Card,
    containerColor: Color? = null,
    borderColor: Color? = null,
    elevation: Dp = JoyElevation.Card,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardPress"
    )
    JoyCard(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        containerColor = containerColor,
        borderColor = borderColor,
        elevation = if (enabled) elevation else JoyElevation.None,
        contentPadding = contentPadding
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
        )
        content()
    }
}

/**
 * A small rounded label — counters, streak days, "2/3", "3 levels to boss".
 */
@Composable
fun JoyChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    fontSize: TextUnit = 11.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            .clip(JoyShape.Pill)
            .background(containerColor ?: c.chip)
            .then(
                if (borderColor == null) Modifier
                else Modifier.border(1.dp, borderColor, JoyShape.Pill)
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor ?: c.textMuted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Black
            ),
            maxLines = 1
        )
    }
}

/**
 * A rounded icon well — the emoji tile that leads every card and list row.
 *
 * [tint] paints a soft wash of the row's accent behind the glyph, which is
 * what gives each row its colour identity; pass `null` for a neutral well.
 */
@Composable
fun JoyIconTile(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color? = null,
    glyphSize: TextUnit = 18.sp
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(
                if (tint != null) {
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.10f))
                    )
                } else {
                    Brush.verticalGradient(listOf(c.chip, c.chip))
                }
            )
            .border(
                1.dp,
                if (tint != null) tint.copy(alpha = 0.28f) else c.cardBorder,
                RoundedCornerShape(size / 3)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = glyph, fontSize = glyphSize)
    }
}

/**
 * The empty well behind a progress bar or a meter.
 *
 * A component because the track+fill+spark trio was re-derived slightly
 * differently in five places, and because a track lighter than the page is
 * invisible in dark mode — this one always sits below its fill in both
 * themes.
 */
@Composable
fun JoyTrack(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    borderColor: Color? = null,
    innerPadding: Dp = 2.dp,
    /**
     * Drawn inside the well, inset by [innerPadding].
     *
     * This slot is what lets JoyProgressTrack paint the fill as content instead
     * of rebuilding the track's own modifier chain — the gradient has to be
     * built inside drawBehind to avoid recomposing the subtree 60x/second, and
     * that is only possible from a slot. Without it, callers had to re-declare
     * the track's clip/background/border and the copies drifted.
     */
    content: @Composable BoxScope.() -> Unit = {}
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(c.track)
            .border(1.dp, borderColor ?: c.trackBorder, shape)
            .padding(innerPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

/** A horizontal rule matching the themed hairline. */
@Composable
fun JoyDivider(modifier: Modifier = Modifier, color: Color? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color ?: joyThemeColors().cardBorder)
    )
}

/**
 * A progress bar with a highlight travelling along the filled portion.
 *
 * The track/fill/spark trio was re-derived three separate times (home's boss
 * road, the mission meters, and the in-game level bar) and the copies drifted.
 * Two things are load-bearing and are easy to get wrong by hand:
 *
 *  - The travelling gradient is built inside [drawBehind], not in the modifier
 *    chain. Passing the animation value into `Brush.horizontalGradient` while
 *    composing makes the whole subtree recompose 60x/second — and on the home
 *    screen that subtree also reads SharedPreferences.
 *  - The spark is positioned as a fraction of the *fill's* width and measured
 *    from the track, not from a hardcoded dp, so it lands exactly on the fill's
 *    end at any screen width.
 */
@Composable
fun JoyProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 12.dp,
    fillHeight: Dp = 8.dp,
    /** The colours the highlight cycles through along the filled portion. */
    colors: List<Color> = listOf(NeonGreen, NeonCyan, NeonGold),
    showSpark: Boolean = true,
    animationDurationMillis: Int = 1400
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "trackProgress"
    )
    val transition = rememberInfiniteTransition(label = "trackShimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(animationDurationMillis), RepeatMode.Restart),
        label = "shimmer"
    )
    val fillShape = RoundedCornerShape(trackHeight / 2)

    JoyTrack(
        modifier = modifier,
        height = trackHeight,
        shape = fillShape
    ) {
        val fillModifier = Modifier
            .fillMaxWidth(animated)
            .height(fillHeight)
            .clip(fillShape)
            .drawBehind {
                val span = size.width
                val travel = span + 300f
                val cx = shimmer * travel - 150f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = colors,
                        startX = cx - 150f,
                        endX = cx + 150f
                    )
                )
            }
        Box(modifier = fillModifier)
        if (showSpark && animated in 0.05f..0.97f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(fillHeight),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, JoyShape.Pill)
                        .border(1.5.dp, colors.last(), JoyShape.Pill)
                )
            }
        }
    }
}

/**
 * A white gradient haze across the top of a tile.
 *
 * On a light card this is what makes a flat fill read as a physical surface;
 * on a dark card the same constant alpha turns into a grey smear, so the
 * strength comes from the palette.
 */
@Composable
fun BoxScope.JoyGloss(alpha: Float? = null) {
    val strength = alpha ?: joyThemeColors().glossAlpha
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = strength), Color.Transparent)
                )
            )
    )
}
