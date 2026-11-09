package ir.amiraligrr.blockboom.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The game's icon set, drawn as vectors.
 *
 * ── Why these are drawn rather than typed ────────────────────────────────
 * Every icon in the app was an emoji. That looks fine on the designer's
 * machine and inconsistent everywhere else: the glyph is chosen by the OS, so
 * the same `"🛒"` is a flat pictogram on one phone and a full-colour
 * three-dimensional emoji on the next, it changes with the system font, it is
 * unreadable at 12sp, and it cannot be tinted to match the button it sits on.
 *
 * Every glyph here is a [Path] on the same 24dp grid, stroked with the same
 * width and cap, so a row of them lines up optically instead of each one
 * carrying its own baseline and padding. They take the colour they are given,
 * which is what lets an icon sit on an amber button or an emerald one without
 * being a second image pasted on top of it.
 *
 * Filled or stroked, not both: a filled glyph at this size is a blob, and a
 * stroked one stays legible at the 14-18dp these are actually used at.
 */
enum class AppIcon {
    SHOP,
    UPGRADE,
    TROPHY,
    SUPPORT,
    COACH,
    GIFT,
    COIN,
    HEART,
    PLAY,
    PAUSE,
    SHIELD,
    BOLT,
    STAR,
    FIRE,
    CHECK,
    CLOSE,
    COPY,
    SEND,
    LOCK,
    SHARE,
    CROWN,
    TAG,

    /**
     * A music note.
     *
     * Needed because 🎵 and 🔔 sat on adjacent rows of the settings card and
     * were indistinguishable at 16dp — the one place a player has to tell two
     * toggles apart by shape rather than by reading.
     */
    NOTE,

    /** A bell — sound effects and notifications. */
    BELL,

    /** A phone with waves — vibration. */
    VIBRATE,

    /** A speaker with arcs — the sound-effects toggle. */
    SPEAKER,

    /** A gear — settings. */
    GEAR
}

/**
 * Draws [icon] at [size], in [tint].
 *
 * Sized in dp and stroked proportionally, so the same icon is the same visual
 * weight on a small phone and a large one.
 */
@Composable
fun AppIconView(
    icon: AppIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawAppIcon(icon, tint, strokeWidth.toPx())
    }
}

private fun DrawScope.drawAppIcon(icon: AppIcon, tint: Color, stroke: Float) {
    // One grid unit, whatever the canvas size: every glyph is authored against
    // a 24x24 box so they share a baseline and an optical weight.
    val u = size.minDimension / 24f
    fun x(v: Float) = v * u
    fun y(v: Float) = v * u
    fun p(vararg c: Float) = Path().apply {
        moveTo(x(c[0]), y(c[1]))
        for (i in 2 until c.size step 2) lineTo(x(c[i]), y(c[i + 1]))
    }

    val strokeStyle = Stroke(
        width = stroke.coerceAtLeast(1f),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    when (icon) {
        // ── Navigation ──────────────────────────────────────────────────────

        AppIcon.SHOP -> {
            // A storefront: awning, body, door.
            drawPath(
                Path().apply {
                    moveTo(x(4f), y(9f))
                    lineTo(x(5.5f), y(4.5f))
                    lineTo(x(18.5f), y(4.5f))
                    lineTo(x(20f), y(9f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(5f), y(9f)); lineTo(x(5f), y(19.5f))
                    moveTo(x(19f), y(9f)); lineTo(x(19f), y(19.5f))
                    moveTo(x(3f), y(19.5f)); lineTo(x(21f), y(19.5f))
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(9.5f), y(19.5f))
                    lineTo(x(9.5f), y(14f))
                    lineTo(x(14.5f), y(14f))
                    lineTo(x(14.5f), y(19.5f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.UPGRADE -> {
            // Two chevrons pointing UP — "improve", and the direction has to be
            // right: an arrow pointing down reads as "collapse" and was the
            // first thing that looked broken on the home screen.
            drawPath(
                Path().apply {
                    moveTo(x(5f), y(14f)); lineTo(x(12f), y(7f)); lineTo(x(19f), y(14f))
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(5f), y(20f)); lineTo(x(12f), y(13f)); lineTo(x(19f), y(20f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.TROPHY -> {
            // Cup, handles, stem, base.
            drawPath(
                Path().apply {
                    moveTo(x(8f), y(4f)); lineTo(x(16f), y(4f))
                    moveTo(x(9f), y(4f))
                    lineTo(x(9f), y(8f))
                    cubicTo(x(9f), y(10.5f), x(15f), y(10.5f), x(15f), y(8f))
                    lineTo(x(15f), y(4f))
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(9f), y(6f))
                    cubicTo(x(5.5f), y(6.5f), x(6f), y(11f), x(9.5f), y(11.5f))
                    moveTo(x(15f), y(6f))
                    cubicTo(x(18.5f), y(6.5f), x(18f), y(11f), x(14.5f), y(11.5f))
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(12f), y(11f)); lineTo(x(12f), y(16f))
                    moveTo(x(8.5f), y(20f)); lineTo(x(12f), y(16f)); lineTo(x(15.5f), y(20f))
                    moveTo(x(8.5f), y(20f)); lineTo(x(15.5f), y(20f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.SUPPORT -> {
            // A headset with a mic boom — the honest read for "talk to a person".
            drawPath(
                Path().apply {
                    moveTo(x(4f), y(15f))
                    cubicTo(x(4f), y(7f), x(20f), y(7f), x(20f), y(15f))
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(4f), y(13f)); lineTo(x(4f), y(17f)); lineTo(x(7.5f), y(17f))
                    moveTo(x(20f), y(13f)); lineTo(x(20f), y(17f)); lineTo(x(16.5f), y(17f))
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(16.5f), y(17f))
                    cubicTo(x(16.5f), y(21f), x(11f), y(21f), x(10.5f), y(18.5f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.COACH -> {
            // A mortarboard with a tassel.
            drawPath(
                Path().apply {
                    moveTo(x(2.5f), y(9f)); lineTo(x(12f), y(4.5f)); lineTo(x(21.5f), y(9f))
                    lineTo(x(12f), y(13.5f)); close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(6.5f), y(11.2f))
                    lineTo(x(6.5f), y(16f))
                    cubicTo(x(6.5f), y(18f), x(17.5f), y(18f), x(17.5f), y(16f))
                    lineTo(x(17.5f), y(11.2f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.GIFT -> {
            // A present with a ribbon and a bow.
            drawPath(
                Path().apply {
                    moveTo(x(3.5f), y(11f))
                    lineTo(x(3.5f), y(20f))
                    lineTo(x(20.5f), y(20f))
                    lineTo(x(20.5f), y(11f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(2.5f), y(7.5f))
                    lineTo(x(21.5f), y(7.5f))
                    lineTo(x(21.5f), y(11f))
                    lineTo(x(2.5f), y(11f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(12f), y(7.5f)); lineTo(x(12f), y(20f))
                    moveTo(x(12f), y(7.5f))
                    cubicTo(x(12f), y(3.5f), x(6.5f), y(3.5f), x(8f), y(6f))
                    cubicTo(x(9f), y(7.6f), x(11f), y(7.5f), x(12f), y(7.5f))
                    moveTo(x(12f), y(7.5f))
                    cubicTo(x(12f), y(3.5f), x(17.5f), y(3.5f), x(16f), y(6f))
                    cubicTo(x(15f), y(7.6f), x(13f), y(7.5f), x(12f), y(7.5f))
                }, tint, style = strokeStyle
            )
        }

        // ── Currency and state ──────────────────────────────────────────────

        AppIcon.COIN -> {
            drawCircle(tint, u * 10f, Offset(x(12f), y(12f)), style = strokeStyle)
            drawCircle(tint, u * 6f, Offset(x(12f), y(12f)), style = strokeStyle)
        }

        AppIcon.HEART -> {
            drawPath(
                Path().apply {
                    moveTo(x(12f), y(20.5f))
                    cubicTo(x(12f), y(20.5f), x(3f), y(14.5f), x(3f), y(8.5f))
                    cubicTo(x(3f), y(5f), x(8f), y(3.5f), x(12f), y(6.5f))
                    cubicTo(x(16f), y(3.5f), x(21f), y(5f), x(21f), y(8.5f))
                    cubicTo(x(21f), y(14.5f), x(12f), y(20.5f), x(12f), y(20.5f))
                    close()
                }, tint, style = strokeStyle
            )
        }

        AppIcon.SHIELD -> {
            drawPath(
                Path().apply {
                    moveTo(x(12f), y(3f))
                    lineTo(x(20f), y(6.5f))
                    lineTo(x(20f), y(12f))
                    cubicTo(x(20f), y(17f), x(16f), y(20f), x(12f), y(21.5f))
                    cubicTo(x(8f), y(20f), x(4f), y(17f), x(4f), y(12f))
                    lineTo(x(4f), y(6.5f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(8.5f), y(12f)); lineTo(x(11f), y(14.5f)); lineTo(x(15.5f), y(9.5f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.BOLT -> {
            drawPath(
                p(
                    x(13.5f), y(2.5f), x(5f), y(13.5f), x(11f), y(13.5f),
                    x(10.5f), y(21.5f), x(19f), y(10.5f), x(13f), y(10.5f),
                    x(13.5f), y(2.5f)
                ), tint, style = strokeStyle
            )
        }

        AppIcon.STAR -> {
            drawPath(starPath(Offset(x(12f), y(12f)), u * 9.5f), tint, style = strokeStyle)
        }

        AppIcon.CROWN -> {
            // Five points, a heavy base, and a jewel on each peak. The silhouette
            // is what reads at 16dp; the jewels are only there for the large
            // premium card.
            drawPath(
                Path().apply {
                    moveTo(x(3f), y(18.5f))
                    lineTo(x(4.5f), y(7.5f))
                    lineTo(x(9f), y(11.5f))
                    lineTo(x(12f), y(4.5f))
                    lineTo(x(15f), y(11.5f))
                    lineTo(x(19.5f), y(7.5f))
                    lineTo(x(21f), y(18.5f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(p(x(3.8f), y(21f), x(20.2f), y(21f)), tint, style = strokeStyle)
            drawCircle(tint, u * 0.85f, Offset(x(12f), y(8.2f)))
        }

        AppIcon.TAG -> {
            // A price tag with its eyelet, pointing into the corner.
            drawPath(
                Path().apply {
                    moveTo(x(3f), y(3f))
                    lineTo(x(12f), y(3f))
                    lineTo(x(21f), y(12f))
                    lineTo(x(12f), y(21f))
                    lineTo(x(3f), y(12f))
                    close()
                }, tint, style = strokeStyle
            )
            drawCircle(tint, u * 1.35f, Offset(x(7.2f), y(7.2f)), style = strokeStyle)
        }

        AppIcon.FIRE -> {
            drawPath(
                Path().apply {
                    moveTo(x(12f), y(2.5f))
                    cubicTo(x(12f), y(2.5f), x(18f), y(8f), x(18f), y(13f))
                    cubicTo(x(18f), y(17.5f), x(15.5f), y(21f), x(12f), y(21f))
                    cubicTo(x(8.5f), y(21f), x(6f), y(17.5f), x(6f), y(13f))
                    cubicTo(x(6f), y(10.5f), x(7.5f), y(8.5f), x(9f), y(6.5f))
                    cubicTo(x(9.5f), y(10f), x(10.5f), y(11.5f), x(11.5f), y(10f))
                    cubicTo(x(12.5f), y(8.5f), x(10.5f), y(6f), x(12f), y(2.5f))
                    close()
                }, tint, style = strokeStyle
            )
        }

        // ── Controls ────────────────────────────────────────────────────────

        AppIcon.PLAY -> {
            drawPath(
                Path().apply {
                    moveTo(x(7f), y(4.5f))
                    lineTo(x(19f), y(12f))
                    lineTo(x(7f), y(19.5f))
                    close()
                }, tint, style = strokeStyle
            )
        }

        AppIcon.PAUSE -> {
            drawPath(
                Path().apply {
                    moveTo(x(9f), y(5f)); lineTo(x(9f), y(19f))
                    moveTo(x(15f), y(5f)); lineTo(x(15f), y(19f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.CHECK -> {
            drawPath(
                p(x(4.5f), y(12.5f), x(9.5f), y(17.5f), x(19.5f), y(6.5f)),
                tint, style = strokeStyle
            )
        }

        AppIcon.CLOSE -> {
            drawPath(p(x(6f), y(6f), x(18f), y(18f)), tint, style = strokeStyle)
            drawPath(p(x(18f), y(6f), x(6f), y(18f)), tint, style = strokeStyle)
        }

        AppIcon.COPY -> {
            drawPath(
                Path().apply {
                    moveTo(x(9f), y(9f))
                    lineTo(x(19f), y(9f))
                    lineTo(x(19f), y(19f))
                    lineTo(x(9f), y(19f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(15f), y(5.5f)); lineTo(x(5f), y(5.5f)); lineTo(x(5f), y(15.5f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.SEND -> {
            drawPath(
                Path().apply {
                    moveTo(x(21f), y(3.5f))
                    lineTo(x(10.5f), y(13.5f))
                    moveTo(x(21f), y(3.5f)); lineTo(x(14.5f), y(21f)); lineTo(x(10.5f), y(13.5f))
                    moveTo(x(14.5f), y(21f)); lineTo(x(3f), y(10f)); lineTo(x(10.5f), y(13.5f))
                    close()
                }, tint, style = strokeStyle
            )
        }

        AppIcon.LOCK -> {
            drawPath(
                Path().apply {
                    moveTo(x(5f), y(11f))
                    lineTo(x(19f), y(11f))
                    lineTo(x(19f), y(20f))
                    lineTo(x(5f), y(20f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(8f), y(11f))
                    lineTo(x(8f), y(7.5f))
                    cubicTo(x(8f), y(3.5f), x(16f), y(3.5f), x(16f), y(7.5f))
                    lineTo(x(16f), y(11f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.SHARE -> {
            // Three nodes and two links — the universal share mark, and the one
            // that reads as "send this to someone" without a word.
            drawCircle(tint, u * 2.6f, Offset(x(18f), y(5.5f)), style = strokeStyle)
            drawCircle(tint, u * 2.6f, Offset(x(6f), y(12f)), style = strokeStyle)
            drawCircle(tint, u * 2.6f, Offset(x(18f), y(18.5f)), style = strokeStyle)
            drawPath(
                Path().apply {
                    moveTo(x(8.3f), y(10.7f)); lineTo(x(15.7f), y(6.8f))
                    moveTo(x(8.3f), y(13.3f)); lineTo(x(15.7f), y(17.2f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.NOTE -> {
            // A quaver: stem, beam, notehead.
            drawPath(
                Path().apply {
                    moveTo(x(9f), y(17.5f))
                    lineTo(x(9f), y(4.5f))
                    lineTo(x(18f), y(7f))
                    moveTo(x(9f), y(4.5f)); lineTo(x(18f), y(7f))
                }, tint, style = strokeStyle
            )
            drawCircle(
                tint, u * 2.4f, Offset(x(6.8f), y(18f)),
                style = strokeStyle
            )
        }

        AppIcon.BELL -> {
            // A bell with a clapper.
            drawPath(
                Path().apply {
                    moveTo(x(5f), y(17f))
                    lineTo(x(5f), y(11f))
                    cubicTo(x(5f), y(6.5f), x(8f), y(4f), x(12f), y(4f))
                    cubicTo(x(16f), y(4f), x(19f), y(6.5f), x(19f), y(11f))
                    lineTo(x(19f), y(17f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(12f), y(3f)); lineTo(x(12f), y(4.5f))
                    moveTo(x(10f), y(19.5f))
                    cubicTo(x(10f), y(17f), x(14f), y(17f), x(14f), y(19.5f))
                    close()
                }, tint, style = strokeStyle
            )
        }

        AppIcon.VIBRATE -> {
            // A phone with waves on both sides.
            drawPath(
                Path().apply {
                    moveTo(x(9f), y(3.5f))
                    lineTo(x(15f), y(3.5f))
                    lineTo(x(15f), y(20.5f))
                    lineTo(x(9f), y(20.5f))
                    close()
                }, tint, style = strokeStyle
            )
            // Waves, which is what makes it vibration rather than a phone.
            drawPath(
                Path().apply {
                    moveTo(x(6.5f), y(9f))
                    cubicTo(x(4f), y(10.5f), x(4f), y(13.5f), x(6.5f), y(15f))
                    moveTo(x(17.5f), y(9f))
                    cubicTo(x(20f), y(10.5f), x(20f), y(13.5f), x(17.5f), y(15f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.SPEAKER -> {
            // A speaker cone with two arcs.
            drawPath(
                Path().apply {
                    moveTo(x(4f), y(9.5f))
                    lineTo(x(8f), y(9.5f))
                    lineTo(x(13f), y(5f))
                    lineTo(x(13f), y(19f))
                    lineTo(x(8f), y(14.5f))
                    lineTo(x(4f), y(14.5f))
                    close()
                }, tint, style = strokeStyle
            )
            drawPath(
                Path().apply {
                    moveTo(x(16f), y(9.5f))
                    cubicTo(x(17.5f), y(11f), x(17.5f), y(13f), x(16f), y(14.5f))
                    moveTo(x(18.5f), y(7f))
                    cubicTo(x(21.5f), y(10f), x(21.5f), y(14f), x(18.5f), y(17f))
                }, tint, style = strokeStyle
            )
        }

        AppIcon.GEAR -> {
            // A cog: an outer ring with teeth, and a hub.
            drawCircle(tint, u * 7f, Offset(x(12f), y(12f)), style = strokeStyle)
            drawCircle(tint, u * 2.6f, Offset(x(12f), y(12f)), style = strokeStyle)
            for (i in 0 until 8) {
                val a = i * 0.7853982f
                val cx = x(12f) + cos(a) * u * 9f
                val cy = y(12f) + sin(a) * u * 9f
                val ex = x(12f) + cos(a) * u * 11.4f
                val ey = y(12f) + sin(a) * u * 11.4f
                drawLine(
                    color = tint,
                    start = Offset(cx, cy),
                    end = Offset(ex, ey),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun starPath(center: Offset, r: Float): Path {
    val inner = r * 0.46f
    return Path().apply {
        for (i in 0 until 10) {
            val a = i * 0.6283185f - 1.5708f
            val rad = if (i % 2 == 0) r else inner
            val px = center.x + kotlin.math.cos(a) * rad
            val py = center.y + kotlin.math.sin(a) * rad
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}