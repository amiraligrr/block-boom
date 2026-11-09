package ir.amiraligrr.blockboom.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The silhouette a projectile is drawn with.
 *
 * This exists because the shop sold "skins" that were only a colour swap: six
 * balls, all of them a filled circle with a white core, differing only in hue —
 * and the descriptions promised things that were never drawn ("با افکت آتش" on
 * a ball that had no fire). Buying one changed nothing you could see except a
 * tint. A [BallForm] is what makes a purchase visibly different: the shape
 * itself is the skin, and the colour is secondary.
 */
enum class BallForm {
    /** Smooth sphere. The default, and the safest thing to shoot. */
    ORB,

    /** Faceted, cut like a gemstone. Catches light on flat planes. */
    GEM,

    /** Five-pointed star. Spins, so its points read at any angle. */
    STAR,

    /** Spiked ball — a thrown hazard rather than a marble. */
    SPIKED,

    /** Iron sphere with an equatorial band. Heavy, obviously solid. */
    CANNONBALL,

    /** A live bomb: dark shell, hot core, lit fuse. */
    FLAME,

    /** Hexagonal crystal with a bright inner facet. */
    CRYSTAL,

    /** A heart. */
    HEART,

    /**
     * A flat cut with a table on top — the round-brilliant outline.
     *
     * Distinct from [GEM], which is already a five-sided stone: this one is the
     * eight-sided silhouette, so the two read as two different cuts rather than
     * as the same gem at two sizes.
     */
    DIAMOND,

    /** A banded gas giant with a ring. The one form that is wider than it is tall. */
    RINGED,

    /** A spiral disc. Reads as motion even when it is not rotating. */
    SWIRL,

    /** A soap bubble: hollow rim, thin film, one hard specular dot. */
    BUBBLE,

    /** A lightning bolt. Two hard diagonals and a notch — the fastest read here. */
    BOLT,

    /** A spinning coin: a disc with a raised rim and an embossed face. */
    COIN,

    /** A heater shield: flat top, curved shoulders, a point at the base. */
    SHIELD,

    /**
     * A four-point throwing star. The only form with curved blades, and the
     * fastest spinner — it reads as a blur at speed, which is the point.
     */
    SHURIKEN,

    /** A rocket: capsule body, nose cone, fins, and a flickering exhaust. */
    ROCKET,

    /** A skull: cranium, jaw, and two dark sockets. */
    SKULL,

    /** A flying saucer: a disc, a glass dome, and blinking running lights. */
    UFO
}

/** How the streak behind a projectile is drawn. */
enum class BallTrail {
    /** Soft shrinking dots. Neutral, suits [BallForm.ORB]. */
    DOT,

    /** Bright sparks that fall away. */
    SPARK,

    /** Rising embers in the trailing colour. */
    EMBER,

    /** Tiny four-point glints. */
    GLINT,

    /** A continuous tapering ribbon. */
    RIBBON,

    /** Soft grey puffs that hang in the air. */
    SMOKE
}

/**
 * A ball's complete appearance: silhouette, the two colours its shading is
 * built from, the glow around it, and its trail.
 *
 * [primary] and [secondary] are *different hues*, not one colour at two
 * lightnesses. Every sprite below shades from a lit tone at the top-left to a
 * shadowed, more saturated tone at the bottom-right, and a second hue is what
 * makes that read as a material rather than as a flat disc.
 */
data class BallVisual(
    val form: BallForm,
    val primary: Color,
    val secondary: Color,
    val glow: Color,
    val trail: BallTrail
) {
    /** Top-left lit stop. */
    val lit: Color get() = lighten(primary, 0.42f)
    /** Bottom-right shadowed stop. */
    val shade: Color get() = darken(secondary, 0.30f)
}

private fun lighten(c: Color, amount: Float): Color = Color(
    c.red + (1f - c.red) * amount,
    c.green + (1f - c.green) * amount,
    c.blue + (1f - c.blue) * amount,
    c.alpha
)

private fun darken(c: Color, amount: Float): Color = Color(
    c.red * (1f - amount),
    c.green * (1f - amount),
    c.blue * (1f - amount),
    c.alpha
)

/**
 * Every purchasable ball, keyed by the same ids the save file already uses.
 *
 * The pre-existing ids are kept so a player who already bought `fire_ball` does
 * not lose it, but their appearance is now genuinely different rather than a
 * recolour of the same circle.
 */
object BallVisuals {

    val all: Map<String, BallVisual> = linkedMapOf(
        "default" to BallVisual(
            form = BallForm.ORB,
            primary = Color(0xFF5EEAD4),
            secondary = Color(0xFF0D9488),
            glow = Color(0xFF2DD4BF),
            trail = BallTrail.DOT
        ),
        "fire_ball" to BallVisual(
            form = BallForm.FLAME,
            primary = Color(0xFFFFB020),
            secondary = Color(0xFFD7263D),
            glow = Color(0xFFFF6B35),
            trail = BallTrail.EMBER
        ),
        "ice_ball" to BallVisual(
            form = BallForm.CRYSTAL,
            primary = Color(0xFFA5F3FC),
            secondary = Color(0xFF0EA5E9),
            glow = Color(0xFF38BDF8),
            trail = BallTrail.GLINT
        ),
        "neon_ball" to BallVisual(
            form = BallForm.ORB,
            primary = Color(0xFFF0ABFC),
            secondary = Color(0xFFA21CAF),
            glow = Color(0xFFE879F9),
            trail = BallTrail.RIBBON
        ),
        "galaxy_ball" to BallVisual(
            form = BallForm.SPIKED,
            primary = Color(0xFFC4B5FD),
            secondary = Color(0xFF5B21B6),
            glow = Color(0xFF8B5CF6),
            trail = BallTrail.SPARK
        ),
        "cannon_ball" to BallVisual(
            form = BallForm.CANNONBALL,
            primary = Color(0xFFB0BEC5),
            secondary = Color(0xFF37474F),
            glow = Color(0xFF90A4AE),
            trail = BallTrail.DOT
        ),
        "star_ball" to BallVisual(
            form = BallForm.STAR,
            primary = Color(0xFFFDE047),
            secondary = Color(0xFFEA580C),
            glow = Color(0xFFFACC15),
            trail = BallTrail.GLINT
        ),
        "gem_ball" to BallVisual(
            form = BallForm.GEM,
            primary = Color(0xFF34D399),
            secondary = Color(0xFF065F46),
            glow = Color(0xFF10B981),
            trail = BallTrail.RIBBON
        ),
        "heart_ball" to BallVisual(
            form = BallForm.HEART,
            primary = Color(0xFFFB7185),
            secondary = Color(0xFF9F1239),
            glow = Color(0xFFF43F5E),
            trail = BallTrail.DOT
        ),
        "gold_ball" to BallVisual(
            form = BallForm.GEM,
            primary = Color(0xFFFDE68A),
            secondary = Color(0xFF92400E),
            glow = Color(0xFFFACC15),
            trail = BallTrail.SPARK
        ),
        "bubble_ball" to BallVisual(
            form = BallForm.BUBBLE,
            primary = Color(0xFFBAE6FD),
            secondary = Color(0xFF3B82F6),
            glow = Color(0xFF7DD3FC),
            trail = BallTrail.DOT
        ),
        "plasma_ball" to BallVisual(
            form = BallForm.SWIRL,
            primary = Color(0xFF22D3EE),
            secondary = Color(0xFFA21CAF),
            glow = Color(0xFFE879F9),
            trail = BallTrail.SPARK
        ),
        "planet_ball" to BallVisual(
            form = BallForm.RINGED,
            primary = Color(0xFFFDBA74),
            secondary = Color(0xFF7C2D12),
            glow = Color(0xFFFB923C),
            trail = BallTrail.DOT
        ),
        "diamond_ball" to BallVisual(
            form = BallForm.DIAMOND,
            primary = Color(0xFFE0F2FE),
            secondary = Color(0xFF0369A1),
            glow = Color(0xFF67E8F9),
            trail = BallTrail.GLINT
        ),
        "bolt_ball" to BallVisual(
            form = BallForm.BOLT,
            primary = Color(0xFFFDE047),
            secondary = Color(0xFF7C2D12),
            glow = Color(0xFFFBBF24),
            trail = BallTrail.SPARK
        ),
        "shield_ball" to BallVisual(
            form = BallForm.SHIELD,
            primary = Color(0xFF93C5FD),
            secondary = Color(0xFF1E3A8A),
            glow = Color(0xFF60A5FA),
            trail = BallTrail.RIBBON
        ),
        "coin_ball" to BallVisual(
            form = BallForm.COIN,
            primary = Color(0xFFFCD34D),
            secondary = Color(0xFF92400E),
            glow = Color(0xFFF59E0B),
            trail = BallTrail.GLINT
        ),
        "shuriken_ball" to BallVisual(
            form = BallForm.SHURIKEN,
            primary = Color(0xFFE2E8F0),
            secondary = Color(0xFF475569),
            glow = Color(0xFF94A3B8),
            trail = BallTrail.SPARK
        ),
        "rocket_ball" to BallVisual(
            form = BallForm.ROCKET,
            primary = Color(0xFFE2E8F0),
            secondary = Color(0xFFEF4444),
            glow = Color(0xFFFCA5A5),
            trail = BallTrail.SMOKE
        ),
        "skull_ball" to BallVisual(
            form = BallForm.SKULL,
            primary = Color(0xFFEDE6D6),
            secondary = Color(0xFF6B6455),
            glow = Color(0xFFD9D2C0),
            trail = BallTrail.DOT
        ),
        "ufo_ball" to BallVisual(
            form = BallForm.UFO,
            primary = Color(0xFFA5F3FC),
            secondary = Color(0xFF0E7490),
            glow = Color(0xFF67E8F9),
            trail = BallTrail.GLINT
        )
    )

    val defaultId = "default"

    fun byId(id: String?): BallVisual = all[id] ?: all.getValue(defaultId)
}

// ─────────────────────────────────────────────────────────────────────────
//  Drawing
//
//  One implementation, used by both the live projectile in GameCanvas and the
//  shop's preview tile. They used to be different code paths — the shop showed
//  a flat rounded square of the skin's colour, the game showed a circle — so
//  the tile never showed the thing being sold.
// ─────────────────────────────────────────────────────────────────────────

/** Tilt of [BallForm.RINGED]'s ring, in degrees. Enough to read as a ring. */
private const val PLANET_RING_TILT = -22f

/** `radius` is the sprite's half-extent; every form is inscribed in that. */
fun DrawScope.drawBallSprite(
    visual: BallVisual,
    center: Offset,
    radius: Float,
    phase: Float = 0f,
    alpha: Float = 1f
) {
    if (radius <= 0.5f) return
    val lit = visual.lit.copy(alpha = alpha)
    val mid = visual.primary.copy(alpha = alpha)
    val shade = visual.shade.copy(alpha = alpha)

    // A soft halo behind every form, so the projectile separates from whatever
    // is behind it. Cheap, and it is what makes a light ball visible against a
    // light sky.
    drawCircle(
        color = visual.glow.copy(alpha = 0.30f * alpha),
        radius = radius * 1.75f,
        center = center
    )

    when (visual.form) {
        BallForm.ORB -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lit, mid, shade),
                    center = Offset(center.x - radius * 0.32f, center.y - radius * 0.36f),
                    radius = radius * 1.55f
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * alpha),
                radius = radius * 0.26f,
                center = Offset(center.x - radius * 0.34f, center.y - radius * 0.38f)
            )
        }

        BallForm.GEM -> {
            // A cut stone: flat facets catching light, so it reads as hard and
            // faceted rather than as a rounded blob.
            val outer = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius * 0.78f, center.y - radius * 0.30f)
                lineTo(center.x + radius * 0.52f, center.y + radius * 0.80f)
                lineTo(center.x - radius * 0.52f, center.y + radius * 0.80f)
                lineTo(center.x - radius * 0.78f, center.y - radius * 0.30f)
                close()
            }
            drawPath(outer, mid)
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - radius)
                    lineTo(center.x - radius * 0.78f, center.y - radius * 0.30f)
                    lineTo(center.x - radius * 0.52f, center.y + radius * 0.80f)
                    close()
                },
                lit.copy(alpha = 0.75f * alpha)
            )
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - radius)
                    lineTo(center.x + radius * 0.78f, center.y - radius * 0.30f)
                    lineTo(center.x + radius * 0.52f, center.y + radius * 0.80f)
                    close()
                },
                shade.copy(alpha = 0.8f * alpha)
            )
            // Table facet.
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - radius * 0.48f)
                    lineTo(center.x + radius * 0.42f, center.y - radius * 0.10f)
                    lineTo(center.x - radius * 0.42f, center.y - radius * 0.10f)
                    close()
                },
                Color.White.copy(alpha = 0.42f * alpha)
            )
            drawPath(outer, Color.White.copy(alpha = 0.20f * alpha), style = Stroke(width = radius * 0.06f))
        }

        BallForm.STAR -> {
            val path = starPath(center, radius, 5, phase)
            drawPath(path, visual.glow.copy(alpha = 0.35f * alpha))
            drawPath(path, mid)
            drawPath(path, Color.White.copy(alpha = 0.28f * alpha), style = Stroke(width = radius * 0.09f))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.9f * alpha), mid.copy(alpha = 0f)),
                    center = center,
                    radius = radius * 0.62f
                ),
                radius = radius * 0.62f,
                center = center
            )
        }

        BallForm.SPIKED -> {
            // Spikes first, so the body covers their roots.
            for (i in 0 until 8) {
                val a = phase + i * (6.2832f / 8f)
                val tip = Offset(center.x + cos(a) * radius * 1.34f, center.y + sin(a) * radius * 1.34f)
                val l = Offset(center.x + cos(a + 0.30f) * radius * 0.42f, center.y + sin(a + 0.30f) * radius * 0.42f)
                val r = Offset(center.x + cos(a - 0.30f) * radius * 0.42f, center.y + sin(a - 0.30f) * radius * 0.42f)
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(r.x, r.y); close()
                    },
                    shade
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lit, mid, shade),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.34f),
                    radius = radius * 1.5f
                ),
                radius = radius,
                center = center
            )
            // Two orbit rings, the "galaxy" read.
            drawCircle(
                color = visual.glow.copy(alpha = 0.5f * alpha),
                radius = radius * 1.18f,
                center = center,
                style = Stroke(width = radius * 0.07f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.30f * alpha),
                radius = radius * 0.24f,
                center = Offset(center.x - radius * 0.32f, center.y - radius * 0.36f)
            )
        }

        BallForm.CANNONBALL -> {
            drawCircle(color = Color.Black.copy(alpha = 0.30f * alpha), radius = radius, center = Offset(center.x + radius * 0.08f, center.y + radius * 0.10f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(visual.primary, mid, visual.secondary),
                    center = Offset(center.x - radius * 0.34f, center.y - radius * 0.38f),
                    radius = radius * 1.5f
                ),
                radius = radius,
                center = center
            )
            // Equatorial band.
            drawCircle(
                color = visual.secondary.copy(alpha = 0.85f * alpha),
                radius = radius,
                center = center,
                style = Stroke(width = radius * 0.20f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.34f * alpha),
                radius = radius * 0.30f,
                center = Offset(center.x - radius * 0.36f, center.y - radius * 0.40f)
            )
        }

        BallForm.FLAME -> {
            // Bomb body: dark, so the hot core and the fuse read against it.
            drawCircle(color = Color(0xFF1C1917).copy(alpha = alpha), radius = radius * 0.86f, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lit.copy(alpha = alpha), mid.copy(alpha = alpha * 0.85f), Color(0xFF1C1917).copy(alpha = alpha)),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                    radius = radius * 1.2f
                ),
                radius = radius * 0.86f,
                center = center
            )
            // Hot core.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.9f * alpha), lit.copy(alpha = 0f)),
                    center = center,
                    radius = radius * 0.62f
                ),
                radius = radius * 0.62f,
                center = center
            )
            // Fuse, curling and flickering with `phase`.
            val fuseBase = Offset(center.x + radius * 0.20f, center.y - radius * 0.80f)
            val flick = sin(phase * 3f) * radius * 0.10f
            drawPath(
                Path().apply {
                    moveTo(fuseBase.x, fuseBase.y)
                    quadraticTo(
                        fuseBase.x + radius * 0.45f,
                        fuseBase.y - radius * 0.35f + flick,
                        fuseBase.x + radius * 0.28f,
                        fuseBase.y - radius * 0.68f + flick
                    )
                },
                Color(0xFFD6D3D1).copy(alpha = alpha),
                style = Stroke(width = radius * 0.11f, cap = StrokeCap.Round)
            )
            val spark = Offset(fuseBase.x + radius * 0.28f, fuseBase.y - radius * 0.68f + flick)
            drawCircle(
                color = Color(0xFFFFF3C4).copy(alpha = 0.45f * alpha),
                radius = radius * 0.42f,
                center = spark
            )
            drawCircle(color = Color.White.copy(alpha = alpha), radius = radius * 0.17f, center = spark)
        }

        BallForm.CRYSTAL -> {
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val a = phase + i * (6.2832f / 6f) - 1.5708f
                    val px = center.x + cos(a) * radius
                    val py = center.y + sin(a) * radius
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(hex, visual.glow.copy(alpha = 0.30f * alpha))
            drawPath(
                hex,
                brush = Brush.linearGradient(listOf(lit, mid, shade), start = Offset(center.x - radius, center.y - radius), end = Offset(center.x + radius, center.y + radius))
            )
            // Inner facet.
            drawPath(
                Path().apply {
                    for (i in 0 until 6) {
                        val a = phase + i * (6.2832f / 6f) - 1.5708f
                        val px = center.x + cos(a) * radius * 0.52f
                        val py = center.y + sin(a) * radius * 0.52f
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                },
                Color.White.copy(alpha = 0.30f * alpha)
            )
            drawPath(hex, Color.White.copy(alpha = 0.35f * alpha), style = Stroke(width = radius * 0.07f))
        }

        BallForm.HEART -> {
            val path = heartPath(center, radius)
            drawPath(path, visual.glow.copy(alpha = 0.32f * alpha))
            drawPath(
                path,
                brush = Brush.linearGradient(
                    listOf(lit, mid, shade),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.50f * alpha),
                radius = radius * 0.19f,
                center = Offset(center.x - radius * 0.34f, center.y - radius * 0.30f)
            )
        }

        BallForm.DIAMOND -> {
            // Round brilliant: a flat table across the top, a girdle at the
            // widest point, and a point at the bottom.
            val top = center.y - radius * 0.52f
            val girdle = center.y + radius * 0.04f
            val tableW = radius * 0.44f
            val body = Path().apply {
                moveTo(center.x - tableW, top)
                lineTo(center.x + tableW, top)
                lineTo(center.x + radius, girdle)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius, girdle)
                close()
            }
            drawPath(body, visual.glow.copy(alpha = 0.34f * alpha))
            drawPath(
                body,
                brush = Brush.linearGradient(
                    listOf(lit, mid, shade),
                    start = Offset(center.x - radius, top),
                    end = Offset(center.x + radius, center.y + radius)
                )
            )
            // Crown facets: the wedges between the table and the girdle.
            for (side in intArrayOf(-1, 1)) {
                drawPath(
                    Path().apply {
                        moveTo(center.x + side * tableW, top)
                        lineTo(center.x + side * radius, girdle)
                        lineTo(center.x, girdle)
                        close()
                    },
                    (if (side < 0) lit else shade).copy(alpha = 0.45f * alpha)
                )
            }
            // Table.
            drawPath(
                Path().apply {
                    moveTo(center.x - tableW, top)
                    lineTo(center.x + tableW, top)
                    lineTo(center.x + tableW * 0.72f, top - radius * 0.16f)
                    lineTo(center.x - tableW * 0.72f, top - radius * 0.16f)
                    close()
                },
                Color.White.copy(alpha = 0.50f * alpha)
            )
            drawPath(body, Color.White.copy(alpha = 0.28f * alpha), style = Stroke(width = radius * 0.06f))
        }

        BallForm.RINGED -> {
            // The ring is a tilted ellipse drawn in two halves: the far half behind
            // the planet and the near half in front of it. Drawn as one whole ring
            // it reads as a hoop lying on top of the sphere.
            val rx = radius * 1.32f
            val ry = radius * 0.32f
            val ringW = radius * 0.13f
            val topLeft = Offset(center.x - rx, center.y - ry)
            val size = Size(rx * 2f, ry * 2f)

            drawCircle(visual.glow.copy(alpha = 0.30f * alpha), radius = radius, center = center)
            // Far half.
            rotate(PLANET_RING_TILT, center) {
                drawArc(
                    color = visual.glow.copy(alpha = 0.55f * alpha),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = ringW)
                )
            }
            // The planet itself.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lit, mid, shade),
                    center = Offset(center.x - radius * 0.32f, center.y - radius * 0.36f),
                    radius = radius * 1.55f
                ),
                radius = radius,
                center = center
            )
            // Latitude bands, so the sphere is not a flat shaded disc.
            for (i in 1..2) {
                val yOff = (i - 1.5f) * radius * 0.42f
                val halfW = sqrt(max(0f, radius * radius - yOff * yOff))
                if (halfW <= 0f) continue
                drawArc(
                    color = visual.secondary.copy(alpha = 0.30f * alpha),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - halfW, center.y + yOff - radius * 0.09f),
                    size = Size(halfW * 2f, radius * 0.18f)
                )
            }
            // Near half, over the planet.
            rotate(PLANET_RING_TILT, center) {
                drawArc(
                    color = visual.glow.copy(alpha = 0.95f * alpha),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = ringW)
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.40f * alpha),
                radius = radius * 0.24f,
                center = Offset(center.x - radius * 0.34f, center.y - radius * 0.38f)
            )
        }

        BallForm.SWIRL -> {
            // A disc wound with one spiral. `phase` spins it, so two identical
            // balls on the board are never the same shape twice.
            drawCircle(visual.glow.copy(alpha = 0.30f * alpha), radius = radius, center = center)
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(lit, mid, shade, lit),
                    center = center
                ),
                radius = radius * 0.94f,
                center = center
            )
            val turns = 2.6f
            val steps = 46
            val spiral = Path()
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val a = phase + t * turns * 6.2832f
                val r = radius * (0.12f + t * 0.80f)
                val px = center.x + cos(a) * r
                val py = center.y + sin(a) * r
                if (i == 0) spiral.moveTo(px, py) else spiral.lineTo(px, py)
            }
            drawPath(spiral, Color.White.copy(alpha = 0.34f * alpha), style = Stroke(width = radius * 0.10f, cap = StrokeCap.Round))
            drawCircle(Color.White.copy(alpha = 0.45f * alpha), radius = radius * 0.14f, center = center)
        }

        BallForm.BOLT -> {
            // A bolt, drawn as a wide outline and a narrower core so it has an
            // edge rather than being a yellow triangle. `phase` does nothing to
            // the shape — it is the only form that reads identically at every
            // angle, which is what makes it legible on a crowded board.
            val w = radius * 0.86f
            val h = radius * 1.72f
            val outline = Path().apply {
                moveTo(center.x + w * 0.34f, center.y - h * 0.5f)
                lineTo(center.x - w * 0.46f, center.y + h * 0.06f)
                lineTo(center.x - w * 0.02f, center.y + h * 0.06f)
                lineTo(center.x - w * 0.22f, center.y + h * 0.5f)
                lineTo(center.x + w * 0.58f, center.y - h * 0.10f)
                lineTo(center.x + w * 0.12f, center.y - h * 0.10f)
                close()
            }
            drawPath(outline, visual.glow.copy(alpha = 0.38f * alpha))
            drawPath(
                outline,
                brush = Brush.linearGradient(
                    listOf(lit, mid, shade),
                    start = Offset(center.x - w, center.y - h * 0.5f),
                    end = Offset(center.x + w, center.y + h * 0.5f)
                )
            )
            // The white core that makes it read as electricity, not paper.
            drawPath(
                Path().apply {
                    moveTo(center.x + w * 0.26f, center.y - h * 0.40f)
                    lineTo(center.x - w * 0.34f, center.y + h * 0.04f)
                    lineTo(center.x - w * 0.02f, center.y + h * 0.04f)
                    lineTo(center.x - w * 0.16f, center.y + h * 0.40f)
                    lineTo(center.x + w * 0.44f, center.y - h * 0.12f)
                    lineTo(center.x + w * 0.10f, center.y - h * 0.12f)
                    close()
                },
                Color.White.copy(alpha = 0.72f * alpha)
            )
        }

        BallForm.COIN -> {
            // A disc seen at an angle: the rim is an ellipse that narrows and
            // widens with `phase`, so the coin turns over as it flies. A coin
            // drawn as a flat circle would be a gold orb, which the catalogue
            // already has twice.
            val tilt = 0.22f + 0.78f * abs(sin(phase * 1.6f))
            val rx = radius * tilt.coerceAtLeast(0.12f)
            val ry = radius
            drawCircle(visual.glow.copy(alpha = 0.32f * alpha), radius = radius * 1.05f, center = center)
            // Edge, seen as the coin turns.
            drawOval(
                color = visual.secondary.copy(alpha = 0.85f * alpha),
                topLeft = Offset(center.x - rx, center.y - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    listOf(lit, mid, shade),
                    center = Offset(center.x - rx * 0.35f, center.y - ry * 0.35f),
                    radius = radius * 1.4f
                ),
                topLeft = Offset(center.x - rx * 0.88f, center.y - ry * 0.88f),
                size = Size(rx * 1.76f, ry * 1.76f)
            )
            // Raised rim, inset from the edge.
            drawOval(
                color = visual.secondary.copy(alpha = 0.55f * alpha),
                topLeft = Offset(center.x - rx * 0.70f, center.y - ry * 0.70f),
                size = Size(rx * 1.40f, ry * 1.40f),
                style = Stroke(width = radius * 0.10f)
            )
            // The face mark, only while the coin is showing enough of itself.
            if (tilt > 0.45f) {
                drawPath(
                    Path().apply {
                        moveTo(center.x, center.y - ry * 0.34f)
                        lineTo(center.x + rx * 0.22f, center.y - ry * 0.04f)
                        lineTo(center.x, center.y + ry * 0.34f)
                        lineTo(center.x - rx * 0.22f, center.y - ry * 0.04f)
                        close()
                    },
                    Color.White.copy(alpha = 0.55f * alpha)
                )
            }
        }

        BallForm.SHIELD -> {
            val w = radius * 0.86f
            val h = radius * 1.10f
            val outline = Path().apply {
                moveTo(center.x - w, center.y - h * 0.82f)
                lineTo(center.x + w, center.y - h * 0.82f)
                lineTo(center.x + w, center.y + h * 0.02f)
                cubicTo(
                    center.x + w, center.y + h * 0.72f,
                    center.x + w * 0.42f, center.y + h * 0.92f,
                    center.x, center.y + h
                )
                cubicTo(
                    center.x - w * 0.42f, center.y + h * 0.92f,
                    center.x - w, center.y + h * 0.72f,
                    center.x - w, center.y + h * 0.02f
                )
                close()
            }
            drawPath(outline, visual.glow.copy(alpha = 0.34f * alpha))
            drawPath(
                outline,
                brush = Brush.linearGradient(
                    listOf(lit, mid, shade),
                    start = Offset(center.x - w, center.y - h),
                    end = Offset(center.x + w, center.y + h)
                )
            )
            // The band across the top, which is what makes it a heraldic shield
            // rather than a rounded triangle.
            drawPath(
                Path().apply {
                    moveTo(center.x - w, center.y - h * 0.82f)
                    lineTo(center.x + w, center.y - h * 0.82f)
                    lineTo(center.x + w, center.y - h * 0.40f)
                    lineTo(center.x - w, center.y - h * 0.40f)
                    close()
                },
                Color.White.copy(alpha = 0.42f * alpha)
            )
            drawPath(outline, Color.White.copy(alpha = 0.30f * alpha), style = Stroke(width = radius * 0.06f))
        }

        BallForm.SHURIKEN -> {
            // Four curved blades. The curve is the whole read: a straight
            // four-point star is a sparkle, a curved one is a weapon, and the
            // direction of the curve says which way it is spinning.
            val spin = phase * 2.2f
            val path = Path().apply {
                for (i in 0 until 4) {
                    val a = spin + i * (6.2832f / 4f)
                    val tipX = center.x + cos(a) * radius * 1.18f
                    val tipY = center.y + sin(a) * radius * 1.18f
                    val midA = a + 0.42f
                    val midX = center.x + cos(midA) * radius * 0.42f
                    val midY = center.y + sin(midA) * radius * 0.42f
                    val nubA = a - 0.42f
                    val nubX = center.x + cos(nubA) * radius * 0.42f
                    val nubY = center.y + sin(nubA) * radius * 0.42f
                    if (i == 0) moveTo(tipX, tipY) else lineTo(tipX, tipY)
                    // Concave edge from the tip back in to the hub, so the
                    // blades read as scythes rather than as triangles.
                    quadraticTo(midX, midY, center.x + cos(a + 0.9f) * radius * 0.30f, center.y + sin(a + 0.9f) * radius * 0.30f)
                    quadraticTo(nubX, nubY, tipX, tipY)
                }
                close()
            }
            drawPath(path, visual.glow.copy(alpha = 0.30f * alpha))
            drawPath(
                path,
                brush = Brush.linearGradient(
                    listOf(lit, mid, shade),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                )
            )
            // The hub: a bright rivet, so the blades have something to be
            // blades *of*.
            drawCircle(color = shade, radius = radius * 0.30f, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.95f * alpha), lit.copy(alpha = 0f)),
                    center = center,
                    radius = radius * 0.34f
                ),
                radius = radius * 0.34f,
                center = center
            )
        }

        BallForm.ROCKET -> {
            // Nose up, exhaust down — the ball flies "up" the screen by
            // construction, so the rocket points the way it is going and the
            // flame flickers at the back.
            val bodyW = radius * 0.52f
            val bodyH = radius * 1.05f
            // Body.
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(lit, mid),
                    start = Offset(center.x - bodyW, center.y - bodyH * 0.75f),
                    end = Offset(center.x + bodyW, center.y + bodyH * 0.3f)
                ),
                topLeft = Offset(center.x - bodyW, center.y - bodyH * 0.75f),
                size = Size(bodyW * 2f, bodyH),
                cornerRadius = CornerRadius(bodyW, bodyW * 0.7f)
            )
            // Nose cone.
            drawPath(
                Path().apply {
                    moveTo(center.x - bodyW, center.y - bodyH * 0.72f)
                    quadraticTo(center.x, center.y - bodyH * 1.45f, center.x + bodyW, center.y - bodyH * 0.72f)
                    close()
                },
                shade
            )
            // Fins.
            for (side in intArrayOf(-1, 1)) {
                drawPath(
                    Path().apply {
                        moveTo(center.x + side * bodyW * 0.85f, center.y + bodyH * 0.05f)
                        lineTo(center.x + side * bodyW * 1.55f, center.y + bodyH * 0.55f)
                        lineTo(center.x + side * bodyW * 0.9f, center.y + bodyH * 0.5f)
                        close()
                    },
                    visual.secondary.copy(alpha = 0.9f * alpha)
                )
            }
            // Exhaust: a flame that flickers with `phase`.
            val flick = 0.75f + 0.25f * abs(sin(phase * 9f))
            drawPath(
                Path().apply {
                    moveTo(center.x - bodyW * 0.55f, center.y + bodyH * 0.28f)
                    quadraticTo(
                        center.x,
                        center.y + bodyH * (0.75f + 0.5f * flick),
                        center.x + bodyW * 0.55f,
                        center.y + bodyH * 0.28f
                    )
                    close()
                },
                visual.glow.copy(alpha = 0.75f * alpha)
            )
            drawPath(
                Path().apply {
                    moveTo(center.x - bodyW * 0.25f, center.y + bodyH * 0.28f)
                    quadraticTo(
                        center.x,
                        center.y + bodyH * (0.6f + 0.35f * flick),
                        center.x + bodyW * 0.25f,
                        center.y + bodyH * 0.28f
                    )
                    close()
                },
                Color.White.copy(alpha = 0.85f * alpha)
            )
            // A porthole, because a rocket without a porthole is a missile.
            drawCircle(
                color = Color.White.copy(alpha = 0.75f * alpha),
                radius = bodyW * 0.38f,
                center = Offset(center.x, center.y - bodyH * 0.25f)
            )
            drawCircle(
                color = visual.secondary.copy(alpha = 0.85f * alpha),
                radius = bodyW * 0.24f,
                center = Offset(center.x, center.y - bodyH * 0.25f)
            )
        }

        BallForm.SKULL -> {
            // Cranium, jaw, two sockets. Bone-white against a dark game, so
            // it is one of the most visible forms in the catalogue.
            val crW = radius * 0.92f
            val crH = radius * 0.98f
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(lit, mid, shade),
                    start = Offset(center.x - crW, center.y - crH),
                    end = Offset(center.x + crW, center.y + crH * 0.6f)
                ),
                topLeft = Offset(center.x - crW, center.y - crH),
                size = Size(crW * 2f, crH * 1.25f),
                cornerRadius = CornerRadius(crW * 0.9f, crW * 0.9f)
            )
            // Jaw, narrower and shorter, under the cranium.
            drawRoundRect(
                color = mid.copy(alpha = alpha),
                topLeft = Offset(center.x - crW * 0.5f, center.y + crH * 0.22f),
                size = Size(crW, crH * 0.55f),
                cornerRadius = CornerRadius(crW * 0.28f, crW * 0.28f)
            )
            // Sockets: the two dark holes that make it a skull rather than a
            // white brick.
            for (side in intArrayOf(-1, 1)) {
                drawOval(
                    color = Color(0xFF141210).copy(alpha = 0.92f * alpha),
                    topLeft = Offset(center.x + side * crW * 0.42f - crW * 0.21f, center.y - crH * 0.52f),
                    size = Size(crW * 0.42f, crH * 0.5f)
                )
            }
            // Nasal hole.
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - crH * 0.02f)
                    lineTo(center.x + crW * 0.12f, center.y + crH * 0.18f)
                    lineTo(center.x - crW * 0.12f, center.y + crH * 0.18f)
                    close()
                },
                Color(0xFF141210).copy(alpha = 0.92f * alpha)
            )
            // Teeth.
            for (i in 0 until 3) {
                drawLine(
                    color = shade.copy(alpha = 0.8f * alpha),
                    start = Offset(center.x - crW * 0.36f + i * crW * 0.36f, center.y + crH * 0.26f),
                    end = Offset(center.x - crW * 0.36f + i * crW * 0.36f, center.y + crH * 0.72f),
                    strokeWidth = radius * 0.045f
                )
            }
        }

        BallForm.UFO -> {
            // A saucer seen slightly from above: a wide flat disc, a dome on
            // top, and running lights around the rim that blink in sequence
            // with `phase` — the one form that is wider than the ball it
            // replaces, and the blinking is what sells it.
            val dishRx = radius * 1.15f
            val dishRy = radius * 0.42f
            // Dome.
            drawPath(
                Path().apply {
                    moveTo(center.x - radius * 0.5f, center.y - dishRy * 0.4f)
                    cubicTo(
                        center.x - radius * 0.5f, center.y - radius * 1.05f,
                        center.x + radius * 0.5f, center.y - radius * 1.05f,
                        center.x + radius * 0.5f, center.y - dishRy * 0.4f
                    )
                    close()
                },
                brush = Brush.verticalGradient(
                    listOf(lit.copy(alpha = 0.95f * alpha), visual.secondary.copy(alpha = 0.85f * alpha)),
                    startY = center.y - radius * 1.05f,
                    endY = center.y - dishRy * 0.4f
                )
            )
            // Glass shine on the dome.
            drawOval(
                color = Color.White.copy(alpha = 0.5f * alpha),
                topLeft = Offset(center.x - radius * 0.3f, center.y - radius * 0.85f),
                size = Size(radius * 0.24f, radius * 0.4f)
            )
            // The disc: a dark rim under a lit plate.
            drawOval(
                color = shade,
                topLeft = Offset(center.x - dishRx, center.y - dishRy * 0.75f),
                size = Size(dishRx * 2f, dishRy * 1.5f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    listOf(lit, mid),
                    center = Offset(center.x - dishRx * 0.3f, center.y - dishRy * 0.3f),
                    radius = dishRx * 1.3f
                ),
                topLeft = Offset(center.x - dishRx * 0.92f, center.y - dishRy * 0.62f),
                size = Size(dishRx * 1.84f, dishRy * 1.24f)
            )
            // Running lights, blinking in sequence around the rim.
            val lights = 7
            for (i in 0 until lights) {
                val a = (i / lights.toFloat()) * 6.2832f + 1.5708f
                val lx = center.x + cos(a) * dishRx * 0.88f
                val ly = center.y + sin(a) * dishRy * 0.55f
                val on = abs(sin(phase * 2.4f + i * 1.1f)) > 0.35f
                drawCircle(
                    color = (if (on) visual.glow else shade).copy(alpha = (if (on) 0.95f else 0.5f) * alpha),
                    radius = radius * 0.09f,
                    center = Offset(lx, ly)
                )
            }
            // Under-glow, so the saucer reads as hovering rather than stuck.
            drawOval(
                color = visual.glow.copy(alpha = 0.22f * alpha),
                topLeft = Offset(center.x - dishRx * 0.55f, center.y + dishRy * 0.5f),
                size = Size(dishRx * 1.1f, dishRy * 0.8f)
            )
        }

        BallForm.BUBBLE -> {
            // Almost no fill: the film is thin, and the read comes from the rim
            // and the one hard highlight. A solid ball here would just be the
            // default orb in a different colour.
            drawCircle(visual.glow.copy(alpha = 0.16f * alpha), radius = radius, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.05f * alpha), Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            // Iridescent rim.
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        visual.primary.copy(alpha = 0.85f * alpha),
                        visual.secondary.copy(alpha = 0.85f * alpha),
                        Color.White.copy(alpha = 0.85f * alpha),
                        visual.primary.copy(alpha = 0.85f * alpha)
                    ),
                    center = center
                ),
                radius = radius * 0.92f,
                center = center,
                style = Stroke(width = radius * 0.14f)
            )
            // The specular dot every soap bubble has.
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * alpha),
                radius = radius * 0.15f,
                center = Offset(center.x - radius * 0.36f, center.y - radius * 0.40f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f * alpha),
                radius = radius * 0.07f,
                center = Offset(center.x + radius * 0.34f, center.y + radius * 0.30f)
            )
        }
    }
}

private fun starPath(center: Offset, r: Float, points: Int, phase: Float): Path {
    val inner = r * 0.46f
    return Path().apply {
        for (i in 0 until points * 2) {
            val a = phase + i * (6.2832f / (points * 2)) - 1.5708f
            val rad = if (i % 2 == 0) r else inner
            val px = center.x + cos(a) * rad
            val py = center.y + sin(a) * rad
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

private fun heartPath(center: Offset, r: Float): Path {
    val x = center.x
    val y = center.y
    return Path().apply {
        moveTo(x, y + r * 0.95f)
        cubicTo(x - r * 1.35f, y - r * 0.10f, x - r * 0.62f, y - r * 1.05f, x, y - r * 0.35f)
        cubicTo(x + r * 0.62f, y - r * 1.05f, x + r * 1.35f, y - r * 0.10f, x, y + r * 0.95f)
        close()
    }
}

/** A four-point glint, for trails and sparkles. */
fun DrawScope.drawGlint(center: Offset, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0.01f || r <= 0.2f) return
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}

/** A teardrop flame, for ember trails. */
fun DrawScope.drawEmber(center: Offset, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0.01f || r <= 0.2f) return
    val path = Path().apply {
        moveTo(center.x, center.y - r * 1.7f)
        cubicTo(center.x + r, center.y - r * 0.3f, center.x + r * 0.8f, center.y + r, center.x, center.y + r)
        cubicTo(center.x - r * 0.8f, center.y + r, center.x - r, center.y - r * 0.3f, center.x, center.y - r * 1.7f)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}
