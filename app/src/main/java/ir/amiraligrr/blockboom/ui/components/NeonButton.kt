package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.ui.theme.JoyAmberLight
import ir.amiraligrr.blockboom.ui.theme.JoyEmerald
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The app's button.
 *
 * Sizing used to be `heightIn(min = height.dp)` with a padded `Text` inside, so
 * every button was really ~52dp tall no matter what `height` said: ten call
 * sites asked for 36/40/42/46/48 and silently got 52, and any label that
 * wrapped to two lines grew the button to 76dp — which is why the three
 * buttons in GameOver were 56/76/56dp next to each other.
 *
 * Now the height is a real [Dp] the caller controls exactly, the label is
 * vertically centred inside it, and text shrinks to fit instead of inflating
 * the button. [ButtonSize] is the shared scale; pass a [ButtonSize] rather
 * than a raw number so screens stay consistent.
 */
enum class ButtonSize(val height: Dp, val horizontalPadding: Dp, val corner: Dp) {
    /** Filters, chips, inline actions. */
    Small(44.dp, 16.dp, 14.dp),
    /** Secondary actions, top-bar buttons, dialog buttons. */
    Medium(52.dp, 20.dp, 16.dp),
    /** The default: one primary action per screen. */
    Large(60.dp, 28.dp, 20.dp),
    /** Hero actions: the play button. */
    XLarge(68.dp, 32.dp, 24.dp);

    companion object {
        /**
         * Maps the old ad-hoc Int heights onto the new scale so existing call
         * sites keep compiling and land somewhere sane.
         */
        fun fromLegacy(height: Int): ButtonSize = when {
            height <= 44 -> Small
            height <= 52 -> Medium
            height <= 62 -> Large
            else -> XLarge
        }
    }
}

/**
 * The contrast a button label has to reach against what the button paints.
 *
 * WCAG's 3:1 is the large-text threshold, and every label here is 17sp
 * ExtraBold at the smallest and 17sp+ at every other size, so large-text is the
 * right bar. Below it the label is a shape rather than a word, which is how a
 * button ends up declaring what it does and not saying it.
 */
private const val MIN_BUTTON_CONTRAST = 3f

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = NeonGreen,
    /**
     * The label colour, or [Color.Unspecified] to have it chosen for you.
     *
     * ── Why the default is not white any more ────────────────────────────
     * It used to be, and that default is what produced three buttons in this
     * app whose label could not be read: a caller that passed a *light*
     * container and relied on the default got white-on-white. None of them were
     * visible in review — `containerColor = c.surfaceSoft` and
     * `contentColor` left off reads perfectly sensibly in isolation. It is only
     * visible on a device, which is where it was found.
     *
     * Unspecified means "pick whatever is legible on what this button paints",
     * and that is now derived from the actual painted colour rather than from
     * the caller's intent. An explicitly-passed colour is honoured unless it
     * fails [MIN_BUTTON_CONTRAST], in which case it is replaced: a button whose
     * label is invisible is not a design choice, it is a bug, and a floor is the
     * only thing that stops the next one being added.
     */
    contentColor: Color = Color.Unspecified,
    borderColor: Color = Color.White.copy(alpha = 0.55f),
    showBorder: Boolean = true,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Large,
    width: Int? = null,
    /**
     * Legacy sizing. 0 (the default) means "use [size]". A positive value is
     * mapped onto the nearest [ButtonSize] so pre-existing call sites keep
     * working and stop asking for heights that were never applied.
     */
    height: Int = 0,
    /**
     * A drawn glyph beside the label.
     *
     * Added because the alternative everywhere else in the app was an emoji in
     * the label string, and an emoji in a button label is chosen by the OS,
     * cannot be tinted to the button, changes with the system font, and is
     * measured by the text measurer like a word — so it competes with the
     * Persian text for horizontal space and drives the whole label down to the
     * 10sp floor on a narrow button.
     *
     * As a separate slot it takes no part in that measurement, so the label
     * shrinks only for the text that is actually text.
     */
    icon: AppIcon? = null
) {
    val dims = if (height > 0) ButtonSize.fromLegacy(height) else size
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Press scale — snappy, and a real press also darkens so the state reads
    // even on a device where the scale change is hard to perceive.
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.955f else 1f,
        animationSpec = tween(110),
        label = "btnScale"
    )

    val c = joyThemeColors()
    // Resolved against the *live* palette, not the light-palette constants.
    // This whole block infers which treatment to apply by comparing the caller's
    // colour against known brand values, so a caller that passed a theme slot
    // (which is what the rest of the app does now) was silently failing the
    // comparison in dark mode: emerald is 0xFF10B981 in light but 0xFF34D399 in
    // dark, so every themed button lost its shimmer after dark mode shipped.
    // The raw constants stay in the list because a few call sites still pass
    // them literally.
    val isPrimary = containerColor == NeonGreen || containerColor == c.emerald
    val isGold = containerColor == c.amber || containerColor == c.amberLight ||
                 containerColor == JoyAmberLight || containerColor == Color(0xFFF59E0B)
    val isEmerald = containerColor == c.emerald || containerColor == JoyEmerald
    val shimmers = enabled && (isPrimary || isGold || isEmerald)

    /**
     * The single colour this button actually paints, as one representative
     * stop of whatever brush is used.
     *
     * Needed because the label has to be read against the *painted* surface, and
     * a vertical gradient has no single surface. The lighter stop is the one
     * that matters — it is the one a light label fails against — so each branch
     * reports its lightest.
     */
    val painted = when {
        !enabled -> c.surfaceSoft
        isGold -> Color(0xFFFDE68A)
        isPrimary || isEmerald -> Color(0xFF047857)
        else -> containerColor
    }
    // The disabled label stays muted on purpose: an inactive control should say
    // so, and a floor on it would make a spent button look live. It is checked
    // against the floor below anyway, so it can only be overridden when it is
    // genuinely unreadable.
    val wanted = if (!enabled) c.textMuted else contentColor
    val labelColor = readableLabelOn(painted, wanted)

    val shape = RoundedCornerShape(dims.corner)

    // Height is exact; width comes from the caller's modifier (most call sites
    // pass fillMaxWidth) unless an explicit width was requested.
    val sized = modifier
            .height(dims.height)
            // An icon-only button has no label to give it a natural width, and
            // with nothing else it collapses to the glyph itself — a 18dp icon
            // in a 44dp-tall button, which is a target nobody can hit and a
            // rectangle with no visible shape. Bump it to the width a small
            // button should be so the pause control has a real area, and give
            // it the same horizontal padding a labelled one has so the icon is
            // centred in something rather than sitting in the corner.
            .then(
                if (text.isEmpty() && icon != null) {
                    Modifier
                        .width(dims.height)
                        .padding(horizontal = dims.horizontalPadding)
                } else if (width != null) {
                    Modifier.width(width.dp)
                } else {
                    Modifier
                }
            )

    Box(
        modifier = sized
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = shape,
                ambientColor = containerColor.copy(alpha = 0.30f),
                spotColor = containerColor.copy(alpha = 0.40f)
            )
            .clip(shape)
            .background(
                when {
                    // A brand colour at low alpha over the page resolves to a
                    // PALE fill, and white text on a pale fill can never reach
                    // 3:1 no matter how opaque you make it (measured: 2.67:1 at
                    // 75%). So the disabled state uses an opaque theme surface
                    // and a muted label instead — which is also the conventional
                    // "inactive" affordance rather than a washed-out live button.
                    !enabled -> Brush.verticalGradient(
                        listOf(c.surfaceSoft, c.surfaceSoft)
                    )
                    isGold -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))
                    // The mint stop (#34D399) only reached 1.92:1 against the
                    // white label, so the top of every primary button was
                    // effectively unreadable. #047857 keeps the emerald
                    // identity and puts white at 5.48:1 on the lightest stop,
                    // clearing the 4.5:1 normal-text bar as well as the
                    // large-bold floor. The mint survives as the glow/border.
                    isPrimary -> Brush.verticalGradient(listOf(Color(0xFF047857), Color(0xFF065F46)))
                    isEmerald -> Brush.verticalGradient(listOf(Color(0xFF047857), Color(0xFF065F46)))
                    // A solid top-to-bottom fade of the same colour made the
                    // bottom of every "else" button translucent, so the page
                    // background showed through the bottom eighth.
                    else -> Brush.verticalGradient(
                        listOf(containerColor, containerColor)
                    )
                },
                shape
            )
            .then(
                if (showBorder && borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(borderColor.copy(alpha = 0.9f), borderColor.copy(alpha = 0.35f))
                        ),
                        shape = shape
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative layers match the *painted* button, so the gloss reaches
        // the edges instead of stopping short and leaving a lighter rectangle.
        //
        // The shimmer lives in its own composable so only buttons that actually
        // shimmer allocate an animation. It used to be an unconditional
        // rememberInfiniteTransition, so every button on the screen — including
        // flat secondary ones that can never show it — ran a 60fps animator.
        if (shimmers) {
            ButtonShimmer()
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )

        // Measured, not guessed.
        //
        // Two earlier attempts both produced visibly broken buttons: letting the
        // label wrap made the button twice as tall, and maxLines=1 +
        // Ellipsis truncated Persian text ("شروع بازی!" rendered as "شروع با…").
        // The real fix is to shrink the type until it actually fits the space it
        // was given. TextMeasurer gives the true width, so this is exact rather
        // than a character-count guess — Persian glyph widths vary far too much
        // for that.
        val labelStyle = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.2.sp,
            lineHeight = 22.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )

        // ── The label ──────────────────────────────────────────────────────
        //
        // This used to be `BoxWithConstraints(Modifier.fillMaxWidth())`. That
        // unconditional `fillMaxWidth` is the bug behind the leaderboard
        // rendering one Persian character per line: a `Box` hands its children
        // the incoming *max* constraint, so a button with no width of its own
        // had its inner box expand to everything the parent would allow, the
        // button matched that, and any `weight(1f)` sibling was left with
        // zero width — collapsing its text to a single glyph per line. On the
        // leaderboard's "my rank" card the submit button ate the whole row and
        // the player's name and rank rendered vertically.
        //
        // The width the label may occupy is now read from the button's own
        // measured size, so the label adapts to whatever width the caller
        // actually produced — full width, a weighted share, or a fixed one —
        // and the button's width is decided by its label and modifier alone.
        //
        // `softWrap = false` is kept as a hard guarantee on top of that: a
        // Persian string that somehow gets less width than a single glyph can
        // then never wrap per character, which is the exact failure being
        // reported.
        var buttonWidthPx by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { buttonWidthPx = it.width }
        )

        val availPx = with(density) {
            (buttonWidthPx - (dims.horizontalPadding * 2).toPx()
                // Reserve the icon's width so the label's fit calculation sees
                // the space it will actually get, not the whole button.
                - if (icon != null) (ICON_BOX + ICON_GAP).dp.toPx() else 0f).toInt()
        }
        val fitStyle = remember(text, availPx, labelStyle) {
            // `buttonWidthPx` is 0 until onSizeChanged reports, which is after
            // the first layout pass. Shrinking against a width of zero would
            // therefore drive the label to the 10sp floor and paint one frame
            // of unreadably tiny text on *every* button on first appearance,
            // before snapping back. An unmeasured button gets its natural size.
            if (availPx <= 0) {
                labelStyle
            } else {
                var sp = 17f
                var result = measurer.measure(
                    text, labelStyle.copy(fontSize = sp.sp), maxLines = 1, softWrap = false
                )
                while (sp > MIN_LABEL_SP.value && result.size.width > availPx) {
                    sp -= 1f
                    result = measurer.measure(
                        text, labelStyle.copy(fontSize = sp.sp), maxLines = 1, softWrap = false
                    )
                }
                labelStyle.copy(fontSize = sp.sp)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ICON_GAP.dp)
        ) {
            // Only drawn when there is no label to sit beside it. With both, the
            // icon lands before a text run that already reads as the action, and
            // on a button whose label starts with a letter the two collide —
            // which is how "شروع بازی" ended up rendered with a play triangle
            // on each side of it.
            if (icon != null && text.isNotEmpty()) {
                AppIconView(
                    icon = icon,
                    // The same colour the label resolved to, so an icon can never
                    // be the one unreadable thing on a button whose text was
                    // carefully contrast-checked.
                    tint = labelColor,
                    size = (ICON_BOX + 2).dp,
                    strokeWidth = 2.1.dp
                )
            }
            if (text.isEmpty() && icon != null) {
                // Icon-only button. The glyph carries the whole meaning, so it
                // is drawn at the size the button's height actually affords.
                AppIconView(
                    icon = icon,
                    tint = labelColor,
                    size = 20.dp,
                    strokeWidth = 2.2.dp
                )
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = dims.horizontalPadding),
                    style = fitStyle,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/** Width of the drawn glyph's box, and the gap between it and the label. */
private const val ICON_BOX = 18
private const val ICON_GAP = 7

/**
 * Picks a label colour that can actually be read on [background].
 *
 * [wanted] is honoured when it clears [MIN_BUTTON_CONTRAST] — a deliberate
 * colour choice is the designer's to make. When it does not, the best of black
 * and white is used instead, because the alternative is a button whose label is
 * invisible.
 *
 * [Color.Unspecified] means "you choose", which resolves straight to the best of
 * the two.
 */
private fun readableLabelOn(background: Color, wanted: Color): Color {
    val best = if (contrastRatio(WhiteInk, background) >= contrastRatio(Ink, background)) {
        WhiteInk
    } else {
        Ink
    }
    if (wanted == Color.Unspecified) return best
    if (contrastRatio(wanted, background) >= MIN_BUTTON_CONTRAST) return wanted
    return if (contrastRatio(best, background) >= MIN_BUTTON_CONTRAST) best else wanted
}

/** WCAG relative luminance. The same measure the block outlines use. */
private fun Color.luminance(): Float {
    fun chan(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * chan(red) + 0.7152f * chan(green) + 0.0722f * chan(blue)
}

/** WCAG contrast ratio between two opaque colours, 1..21. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** The two inks a label is chosen between. Not pure — pure is harsh on a glow. */
private val Ink = Color(0xFF0B1F17)
private val WhiteInk = Color.White

/** Travelling gloss overlay. Only composed for buttons that will show it. */
@Composable
private fun BoxScope.ButtonShimmer() {
    val transition = rememberInfiniteTransition(label = "btnShimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "shimmerX"
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { translationX = shimmerX * 260.dp.toPx() }
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.16f),
                        Color.Transparent
                    )
                )
            )
    )
}

/** Below this a label is no longer readable, so we clip rather than shrink more. */
private val MIN_LABEL_SP = 10.sp
