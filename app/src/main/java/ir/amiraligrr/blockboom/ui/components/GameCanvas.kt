package ir.amiraligrr.blockboom.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ir.amiraligrr.blockboom.data.SkinManager
import ir.amiraligrr.blockboom.domain.Ball
import ir.amiraligrr.blockboom.domain.Block
import ir.amiraligrr.blockboom.domain.BlockShape
import ir.amiraligrr.blockboom.domain.Coin
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.domain.blockColorFor
import ir.amiraligrr.blockboom.domain.blockColorShade
import ir.amiraligrr.blockboom.domain.blockColorTint
import ir.amiraligrr.blockboom.domain.blockOutlineColor
import ir.amiraligrr.blockboom.domain.motherColor
import ir.amiraligrr.blockboom.ui.theme.TerrainMotif
import ir.amiraligrr.blockboom.domain.relativeLuminance
import ir.amiraligrr.blockboom.ui.theme.BackdropStyle
import ir.amiraligrr.blockboom.ui.theme.BallForm
import ir.amiraligrr.blockboom.ui.theme.BallTrail
import ir.amiraligrr.blockboom.ui.theme.BallVisual
import ir.amiraligrr.blockboom.ui.theme.DeepSpace
import ir.amiraligrr.blockboom.ui.theme.GameBackdrop
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import ir.amiraligrr.blockboom.ui.theme.NeonPurple
import ir.amiraligrr.blockboom.ui.theme.OffWhite
import ir.amiraligrr.blockboom.ui.theme.drawBallSprite
import ir.amiraligrr.blockboom.ui.theme.drawEmber
import ir.amiraligrr.blockboom.ui.theme.drawGlint
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun GameField(
    engine: GameEngine,
    skinManager: SkinManager,
    backdrop: GameBackdrop,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    Canvas(
        modifier = modifier
            // The sky is painted properly by drawBackdrop, which needs to know
            // where the horizon sits. This fill is only there so the very first
            // frame is never an unstyled gap while the canvas is still empty.
            .background(Brush.verticalGradient(backdrop.sky))
            // Blocks spawn just above the top edge and coins/particles fly
            // outside the walls, so without this the canvas painted over the
            // HUD (progress bar, header) with entities that belong inside the
            // play field.
            .clipToBounds()
            .onSizeChanged { size ->
                engine.setFieldSize(size.width.toFloat(), size.height.toFloat(), density)
            }
            .pointerInput(engine) {
                detectDragGestures(
                    onDrag = { change, _ -> engine.moveCannonTo(change.position.x) }
                )
            }
    ) {
        drawGame(engine, skinManager, backdrop)
    }
}

private fun DrawScope.drawGame(engine: GameEngine, skinManager: SkinManager, backdrop: GameBackdrop) {
    val bonusesSnapshot = engine.bonuses.toList()
    val powerUpDropsSnapshot = engine.powerUpDrops.toList()
    val coinsSnapshot = engine.coins.toList()
    val blocksSnapshot = engine.blocks.toList()
    val ballsSnapshot = engine.balls.toList()
    val explosionsSnapshot = engine.explosions.toList()
    val particlesSnapshot = engine.particles.toList()
    val popupsSnapshot = engine.popups.toList()

    drawBackdrop(engine, backdrop)

    // Whether this scene is light or dark decides the polarity of every block's
    // rim (see blockOutlineColor): a dark rim on a night sky measures 1.00:1 and
    // disappears, while the same rim on a daylight field lifts the worst block
    // from 1.10:1 to 4.56:1.
    //
    // Resolved once per frame, not per block. Resolved per block it would be
    // 40+ identical luminance computations on a crowded level, in the draw pass.
    val sceneIsLight = backdrop.midTerrain.relativeLuminance() > 0.18f

    if (engine.powerUpFlash > 0f) {
        drawRect(
            color = engine.powerUpFlashColor.copy(alpha = engine.powerUpFlash * 0.22f),
            size = size
        )
    }

    val shakeAmp = dpPx(10f) * engine.shake
    val shakeX = if (engine.shake > 0f) sin(engine.tick * 0.9f) * shakeAmp else 0f
    val shakeY = if (engine.shake > 0f) sin(engine.tick * 1.37f + 1.1f) * shakeAmp * 0.6f else 0f

    translate(shakeX, shakeY) {
        for (bonus in bonusesSnapshot) drawBonus(bonus)
        for (drop in powerUpDropsSnapshot) drawPowerUpDrop(drop)
        for (coin in coinsSnapshot) drawCoin(coin)
        for (block in blocksSnapshot) drawBlock(block, engine, sceneIsLight)
        for (ball in ballsSnapshot) drawBall(ball, skinManager)
        for (explosion in explosionsSnapshot) drawExplosion(explosion)
        for (particle in particlesSnapshot) drawParticle(particle)
        for (popup in popupsSnapshot) drawPopupText(popup)
        drawCannon(engine, skinManager)
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  The backdrop
//
//  This replaces four functions that had drifted apart: a starfield, a neon
//  grid, a pair of teal mountains with pine trees, and a hardcoded vignette —
//  all painted on every background regardless of which one was selected. A
//  space scene, a city grid and a forest were stacked on top of each other, so
//  buying a different background only shifted a gradient behind an identical,
//  incoherent picture.
//
//  Everything below is driven by one [GameBackdrop], so the sky, the light
//  source, the terrain and the edge falloff always belong to the same scene.
//  Layers are painted far-to-near with increasing parallax, which is what sells
//  the depth; the cannon sits on the horizon line so the field reads as a place
//  the player is standing in rather than a panel they are looking at.
// ─────────────────────────────────────────────────────────────────────────

/**
 * Rim colours for a lucky block, dealt by [Block.colorIndex].
 *
 * Keyed off `colorIndex` rather than `shakePhase`, which is what this used to
 * key off: `shakePhase` is an *angle*, so `(shakePhase * 10).toInt() % 5` wrapped
 * at 2π and went negative below zero, making the rim jump between colours and
 * land in the catch-all for half of its range. That is the exact reasoning
 * [Block.colorSeed] was introduced to end.
 *
 * It also includes a real purple. The purple slot pointed at `NeonPurple`, which
 * held emerald green -- so the "rainbow" rendered green twice with no violet in
 * it at all.
 */
private val LUCKY_RAINBOW = listOf(
    NeonGold,
    NeonCyan,
    NeonPurple,
    Color(0xFFFB7185),
    Color(0xFF34D399)
)

/** A smooth rolling-hill silhouette spanning the full width. */
private fun DrawScope.rollingHills(
    baseY: Float,
    amplitude: Float,
    drift: Float,
    seedA: Float,
    seedB: Float
): Path {
    val w = size.width
    val step = dpPx(6f).coerceAtLeast(3f)
    return Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= w + step) {
            // The drift shifts the profile horizontally. It was a declared
            // parameter that nothing read, so the three call sites' distinct
            // rates (6f / 13f / 24f) went nowhere and far, mid and near ridges
            // were all completely static — which is the opposite of the parallax
            // the layer exists to sell.
            val u = (x + drift) / w
            val y = baseY +
                sin(u * 5.1f + seedA) * amplitude +
                sin(u * 2.3f + seedB) * amplitude * 0.55f
            lineTo(x, y)
            x += step
        }
        lineTo(w, size.height)
        close()
    }
}

private fun DrawScope.drawBackdrop(engine: GameEngine, b: GameBackdrop) {
    val w = size.width
    val h = size.height
    val t = engine.clockSeconds

    // The horizon, anchored to where the cannon stands so the cannon is
    // actually standing on the ground rather than floating over a decoration.
    val horizon = (engine.cannon.baseY + dpPx(30f)).coerceIn(h * 0.45f, h * 0.94f)

    // ── sky ────────────────────────────────────────────────────────────────
    //
    // The stops are fractions of the gradient's own range (0 → horizon), not
    // pixels. Compose only validates that stops ascend, then clamps them into
    // 0..1, so a stop written as a pixel offset "works" by being clamped to the
    // end — which silently flattens the sky into two bands on a tall screen
    // and stretches it on a short one.
    drawRect(
        brush = Brush.verticalGradient(
            0f to b.sky[0],
            0.58f to b.sky[1],
            1f to b.sky[2],
            startY = 0f,
            endY = horizon
        ),
        size = Size(w, horizon)
    )

    // ── stars ──────────────────────────────────────────────────────────────
    if (b.starDensity > 0f) {
        val count = (engine.stars.size * b.starDensity).toInt()
        for (i in 0 until count) {
            val star = engine.stars[i]
            val twinkle = 0.30f + 0.45f * abs(sin(t * 1.5f + star.twinklePhase))
            drawCircle(
                color = Color.White.copy(alpha = twinkle),
                radius = star.radius,
                center = Offset(star.x, star.y * 0.92f)
            )
        }
    }

    // ── the light source ───────────────────────────────────────────────────
    // A wide, soft halo first, then the disc. Drawn large and low for the
    // sunset backdrop so the sun sits on the horizon rather than floating.
    val orbX = w * 0.76f
    val orbY = when (b.style) {
        BackdropStyle.DAYLIGHT -> horizon * 0.42f
        BackdropStyle.NEBULA -> h * 0.30f
        BackdropStyle.OCEAN -> horizon * 0.16f
        BackdropStyle.FOG -> horizon * 0.30f
        else -> horizon * 0.24f
    }
    val orbR = when (b.style) {
        BackdropStyle.NEBULA -> dpPx(34f)
        BackdropStyle.DAYLIGHT -> dpPx(30f)
        BackdropStyle.OCEAN -> dpPx(38f)
        BackdropStyle.FOG -> dpPx(22f)
        else -> dpPx(17f)
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(b.orbGlow.copy(alpha = 0.45f), b.orbGlow.copy(alpha = 0.0f)),
            center = Offset(orbX, orbY),
            radius = orbR * 5.5f
        ),
        radius = orbR * 5.5f,
        center = Offset(orbX, orbY)
    )
    drawCircle(color = b.orb, radius = orbR, center = Offset(orbX, orbY))
    drawCircle(
        color = Color.White.copy(alpha = if (b.style == BackdropStyle.NEBULA) 0.10f else 0.30f),
        radius = orbR * 0.62f,
        center = Offset(orbX - orbR * 0.24f, orbY - orbR * 0.26f)
    )
    // A ring, for the space backdrop only.
    if (b.style == BackdropStyle.NEBULA) {
        drawOval(
            brush = Brush.horizontalGradient(
                listOf(b.accent.copy(alpha = 0.10f), b.orb.copy(alpha = 0.55f), b.accent.copy(alpha = 0.10f))
            ),
            topLeft = Offset(orbX - orbR * 2.5f, orbY - orbR * 0.42f),
            size = Size(orbR * 5f, orbR * 0.84f),
            style = Stroke(width = dpPx(3.2f))
        )
    }

    // ── the style-specific mid layer ───────────────────────────────────────
    when (b.style) {
        BackdropStyle.DAYLIGHT -> drawDaylightClouds(b, t)
        BackdropStyle.NEBULA -> drawNebula(b, t)
        BackdropStyle.SKYLINE -> Unit // the skyline is its own terrain layer
        BackdropStyle.AURORA -> drawAurora(b, t)
        BackdropStyle.OCEAN -> drawOceanRays(b, t)
        // A storm's clouds *are* the scene, and they draw with the same puffs
        // the daylight sky uses — in this palette they read as dark cloud
        // rather than as white.
        BackdropStyle.STORM -> drawDaylightClouds(b, t)
        BackdropStyle.FOG -> Unit // the fog itself is the foreground layer
    }

    // ── terrain, far to near ───────────────────────────────────────────────
    // Three layers at three depths. The far one is barely tinted, the near one
    // nearly black, which is what gives the flat field a sense of ground plane.
    val far = rollingHills(horizon - dpPx(14f), dpPx(26f), t * 6f, 0.9f, 2.1f)
    drawPath(far, b.farTerrain)

    // Mountains sit behind the far ridge, and only on scenes that have them.
    //
    // The rolling hills alone gave the horizon a soft, undifferentiated wave —
    // which reads as a gradient rather than as a place. A few hard-edged peaks
    // behind it give the eye something with a defined silhouette to sit against,
    // and they sit *above* the far ridge on screen precisely so the two layers
    // separate instead of merging into one muddy band.
    drawMountains(b, horizon - dpPx(14f), t)

    // Dunes replace both the peaks and the tree row. A dune field built out of
    // the same rolling-hill path is a smear; these are asymmetric and they lean.
    if (b.motif == TerrainMotif.DUNES) drawDunes(b, horizon + dpPx(10f))

    // Trees stand on the mid ridge. Without them the ground is an unbroken
    // green shape; the trunks are what tell you the ground has a scale and a
    // near/far direction.
    drawTrees(b, horizon + dpPx(16f), t)

    if (b.style == BackdropStyle.SKYLINE) {
        drawSkyline(b, horizon, t)
    }

    val mid = rollingHills(horizon + dpPx(16f), dpPx(20f), t * 13f, 3.4f, 1.2f)
    drawPath(mid, b.midTerrain)

    val near = rollingHills(horizon + dpPx(46f), dpPx(15f), t * 24f, 5.0f, 4.4f)
    drawPath(near, b.nearTerrain)

    // Tree canopies overhang the mid ridge, so they are drawn again *after* the
    // near ridge. A tree clipped by the foreground it is standing behind is
    // correct depth ordering, and it stops the mid layer's top edge from cutting
    // straight through the canopies.
    drawTrees(b, horizon + dpPx(16f), t, canopiesOnly = true)

    // Scattered detail on the near ridge: small bushes and stones. Cheap, and it
    // stops the closest band — the one most of the play happens on — from being
    // a flat wash of colour.
    drawGroundDetail(b, horizon + dpPx(46f), t)

    // A bright rim on the near ridge, so the cannon's ground line is crisp
    // rather than dissolving into the layer below it.
    drawPath(
        near,
        color = b.accent.copy(alpha = 0.18f),
        style = Stroke(width = dpPx(1.4f))
    )

    // Petals, over everything. The one foreground layer in the game, and only
    // for the blossom scene.
    drawPetals(b, t)

    // ── the style-specific foreground ──────────────────────────────────────
    // Rain, bubbles and fog sit *in front of* the terrain: they are weather
    // and water in the same space as the blocks, not part of the scenery
    // behind them.
    when (b.style) {
        BackdropStyle.STORM -> drawStormRain(b, t)
        BackdropStyle.OCEAN -> drawOceanForeground(b, t)
        BackdropStyle.FOG -> drawFogBands(b, t)
        else -> Unit
    }

    // ── the dark surround, tuned to this scene's own colours ───────────────
    // The old vignette ended on a hardcoded dark green regardless of backdrop,
    // so on the sunset and space scenes it put a green cast over the corners.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, b.vignette.copy(alpha = 0.55f)),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = maxOf(w, h) * 0.78f
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
            startY = 0f,
            endY = h * 0.12f
        )
    )
}

private fun DrawScope.drawDaylightClouds(b: GameBackdrop, t: Float) {
    if (b.cloudAlpha <= 0f) return
    val w = size.width
    val h = size.height
    // Four puffs, each three overlapping discs so they read as a cloud rather
    // than as a row of circles.
    for (i in 0 until 4) {
        val cx = w * (0.14f + i * 0.26f) + sin(t * 0.10f + i * 1.7f) * w * 0.03f
        val cy = h * (0.09f + (i % 2) * 0.055f)
        val s = dpPx(16f + (i % 3) * 6f)
        drawCircle(b.cloud.copy(alpha = b.cloudAlpha), radius = s, center = Offset(cx, cy))
        drawCircle(b.cloud.copy(alpha = b.cloudAlpha * 0.92f), radius = s * 1.25f, center = Offset(cx - s * 0.9f, cy + s * 0.18f))
        drawCircle(b.cloud.copy(alpha = b.cloudAlpha * 0.92f), radius = s * 1.05f, center = Offset(cx + s * 0.95f, cy + s * 0.2f))
        drawCircle(b.cloud.copy(alpha = b.cloudAlpha * 0.75f), radius = s * 0.8f, center = Offset(cx + s * 0.1f, cy - s * 0.55f))
    }
}

private fun DrawScope.drawNebula(b: GameBackdrop, t: Float) {
    val w = size.width
    val h = size.height
    // Two large, very soft clouds of colour. Kept under 12% alpha: a nebula
    // that is bright enough to look like a nebula is also bright enough to
    // compete with the blocks.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(b.accent.copy(alpha = 0.13f), Color.Transparent),
            center = Offset(w * 0.28f, h * 0.30f),
            radius = w * 0.55f
        ),
        radius = w * 0.55f,
        center = Offset(w * 0.28f, h * 0.30f)
    )
    // One centre, used for both the gradient hotspot and the disc it lights.
    // They were two separate Offset literals three lines apart and only one of
    // them carried the wobble, so the glow breathed up to 1% of the screen
    // height out of phase with the orb. The first nebula cloud in this function
    // already got this right.
    val orbCenter = Offset(w * 0.78f, h * 0.46f + sin(t * 0.2f) * h * 0.01f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(b.orbGlow.copy(alpha = 0.11f), Color.Transparent),
            center = orbCenter,
            radius = w * 0.45f
        ),
        radius = w * 0.45f,
        center = orbCenter
    )

    // A comet, and only on the densest starfield. It crosses the sky on a slow
    // loop — head plus a tapering tail of fading dots — so the midnight scene
    // has something moving in it that the cosmic scene does not.
    if (b.starDensity >= 1f) {
        val period = 14f
        val u = (t % period) / period
        // Ease in and out: the comet lingers at the edges instead of snapping.
        val eased = u * u * (3f - 2f * u)
        val cx = w * (0.12f + eased * 0.76f)
        val cy = h * (0.10f + eased * 0.22f) + sin(u * 6.2832f) * h * 0.012f
        for (i in 0 until 12) {
            val back = i / 12f
            val px = cx - back * w * 0.16f
            val py = cy - back * h * 0.045f
            val a = (1f - back) * 0.5f
            if (a <= 0.02f) continue
            drawCircle(
                color = Color.White.copy(alpha = a),
                radius = dpPx(2.6f) * (1f - back * 0.7f),
                center = Offset(px, py)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = dpPx(12f)
            ),
            radius = dpPx(12f),
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawAurora(b: GameBackdrop, t: Float) {
    val w = size.width
    val h = size.height
    // Ribbons: tall, narrow vertical gradients that ripple sideways. Painted as
    // bands rather than lines so they have a soft, curtain-like falloff.
    for (i in 0 until 3) {
        val baseX = w * (0.22f + i * 0.28f)
        val bandW = w * 0.15f
        val sway = sin(t * 0.5f + i * 2.0f) * w * 0.05f
        val top = h * 0.06f
        val bottom = h * 0.52f
        val path = Path().apply {
            moveTo(baseX + sway, top)
            lineTo(baseX + bandW + sway, top)
            lineTo(baseX + bandW * 1.5f + sway, bottom)
            lineTo(baseX + bandW * 0.5f + sway, bottom)
            close()
        }
        drawPath(
            path,
            brush = Brush.verticalGradient(
                listOf(
                    b.accent.copy(alpha = 0.0f),
                    b.accent.copy(alpha = 0.22f),
                    b.accent.copy(alpha = 0.05f)
                ),
                startY = top,
                endY = bottom
            )
        )
    }
}

/**
 * Light shafts down through the water.
 *
 * Diagonal bands from the surface, each swaying slowly out of phase with the
 * others. Kept under 7% alpha: a shaft bright enough to read as a beam is also
 * bright enough to wash out the blocks falling through it.
 */
private fun DrawScope.drawOceanRays(b: GameBackdrop, t: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 4) {
        val baseX = w * (0.12f + i * 0.24f)
        val bandW = w * (0.05f + 0.02f * (i % 2))
        val sway = sin(t * 0.23f + i * 1.9f) * w * 0.03f
        val alpha = 0.045f + 0.025f * abs(sin(t * 0.31f + i * 2.6f))
        val path = Path().apply {
            moveTo(baseX + sway, 0f)
            lineTo(baseX + bandW + sway, 0f)
            lineTo(baseX + bandW * 2.2f + sway * 2f, h * 0.72f)
            lineTo(baseX - bandW * 0.6f + sway * 2f, h * 0.72f)
            close()
        }
        drawPath(path, b.accent.copy(alpha = alpha))
    }
}

/**
 * The living part of the ocean scene: bubbles rising and kelp swaying.
 *
 * Both are deterministic per index — a hash of `i`, never `random` — so a
 * bubble does not teleport when the frame rate stutters, and the kelp does not
 * reshuffle between frames.
 */
private fun DrawScope.drawOceanForeground(b: GameBackdrop, t: Float) {
    val w = size.width
    val h = size.height

    // ── kelp on the seabed ────────────────────────────────────────────────
    val kelpCount = 9
    for (i in 0 until kelpCount) {
        val seed = i * 7.31f
        val x = w * (0.06f + i * 0.11f) + sin(seed) * w * 0.02f
        val kh = dpPx(26f + 22f * abs(sin(seed * 1.7f)))
        val sway = sin(t * 0.9f + seed) * dpPx(7f)
        val y = h - dpPx(52f) + 6f * abs(cos(seed))
        drawPath(
            Path().apply {
                moveTo(x, y)
                cubicTo(
                    x - dpPx(4f) + sway * 0.4f, y - kh * 0.4f,
                    x + dpPx(4f) + sway * 0.8f, y - kh * 0.7f,
                    x + sway, y - kh
                )
            },
            b.accent.copy(alpha = 0.35f),
            style = Stroke(width = dpPx(2.4f), cap = StrokeCap.Round)
        )
    }

    // ── bubbles ───────────────────────────────────────────────────────────
    val bubbleCount = 14
    for (i in 0 until bubbleCount) {
        val seed = i * 12.9898f
        val speed = h * (0.03f + 0.025f * abs(sin(seed * 3.1f)))
        val span = h + dpPx(24f)
        val rise = (seed * 97.13f + t * speed) % span
        val y = h - dpPx(12f) - rise
        val x = w * (0.05f + (i % 7) * 0.14f) + sin(t * 1.3f + seed) * dpPx(5f)
        val r = dpPx(1.6f + 2.2f * abs(sin(seed)))
        val alpha = (0.30f * (1f - rise / span)).coerceIn(0.08f, 0.30f)
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.5f),
            radius = r,
            center = Offset(x, y),
            style = Stroke(width = dpPx(1.1f))
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = r * 0.28f,
            center = Offset(x - r * 0.3f, y - r * 0.3f)
        )
    }
}

/**
 * Driving rain, and the lightning that pays for it.
 *
 * The rain is slanted (a storm has wind), each streak a short line whose
 * position is a pure function of its index and `t` — so the sheet of rain is
 * continuous rather than a field of dots that happens to be moving.
 *
 * Lightning is a hashed interval: a flash every few seconds, plus a jagged
 * bolt for the last fraction of a second of it. Both are driven off `t`, so
 * they cost nothing per frame and never need resetting.
 */
private fun DrawScope.drawStormRain(b: GameBackdrop, t: Float) {
    val w = size.width
    val h = size.height

    // ── rain ──────────────────────────────────────────────────────────────
    val drops = 42
    val slant = dpPx(4f)
    for (i in 0 until drops) {
        val seed = i * 5.37f
        val speed = h * (0.55f + 0.35f * abs(sin(seed * 2.3f)))
        val span = h + dpPx(30f)
        val fall = (seed * 61.7f + t * speed) % span
        val x = ((w * (0.5f + 0.5f * sin(seed)) + t * dpPx(18f) * (1f + 0.3f * sin(seed * 3f))) % (w + dpPx(20f))) - dpPx(10f)
        val y = -dpPx(15f) + fall
        val len = dpPx(9f + 7f * abs(cos(seed * 1.3f)))
        val alpha = 0.16f + 0.12f * abs(sin(seed * 7f))
        drawLine(
            color = b.accent.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + slant, y + len),
            strokeWidth = dpPx(1.3f),
            cap = StrokeCap.Round
        )
    }

    // ── lightning ─────────────────────────────────────────────────────────
    // A seven-second cycle: the sky flashes, and a bolt is drawn on the flash.
    // `flash` is a sharp pulse — sin to the 40th power is near zero almost
    // everywhere and near one at the peak, which is what a lightning flash
    // actually looks like. The bolt lives in the same window as the peak
    // (sin(cycle * 0.9) tops out at cycle ≈ 1.75), so the light and the bolt
    // arrive together rather than five seconds apart.
    val cycle = t % 7f
    val flash = sin(cycle * 0.9f).pow(40f)
    if (flash > 0.02f) {
        drawRect(
            color = Color.White.copy(alpha = flash * 0.14f),
            size = Size(w, h)
        )
    }
    if (cycle > 1.55f) {
        val boltAlpha = ((1.78f - cycle) / 0.23f).coerceIn(0f, 1f)
        val x0 = w * (0.2f + 0.6f * abs(sin(t * 0.13f)))
        val path = Path().apply {
            moveTo(x0, 0f)
            var y = 0f
            var x = x0
            while (y < h * 0.55f) {
                y += dpPx(26f)
                x += dpPx(14f) * sin(y * 0.05f + t)
                lineTo(x, y)
            }
        }
        drawPath(path, Color.White.copy(alpha = boltAlpha * 0.85f), style = Stroke(width = dpPx(2.6f), cap = StrokeCap.Round))
        drawPath(path, b.accent.copy(alpha = boltAlpha * 0.4f), style = Stroke(width = dpPx(6f), cap = StrokeCap.Round))
    }
}

/**
 * Fog banks drifting across the field.
 *
 * Wide, soft, slow, and at three depths: a high band that barely moves, a mid
 * band that crosses the play field, and a low ground fog that sits over the
 * near ridge. Each is a rounded slab whose alpha breathes, so the fog reads as
 * air rather than as grey rectangles.
 */
private fun DrawScope.drawFogBands(b: GameBackdrop, t: Float) {
    val w = size.width
    val h = size.height
    val bandH = dpPx(30f)
    for (i in 0 until 3) {
        val speed = w * (0.008f + 0.006f * i)
        val span = w + bandH * 6f
        val raw = i * span / 3f + t * speed
        val x = ((raw % span) + span) % span - bandH * 3f
        val y = h * (0.30f + i * 0.16f) + sin(t * 0.2f + i * 2.2f) * dpPx(8f)
        val alpha = 0.10f + 0.05f * abs(sin(t * 0.27f + i * 1.4f)) + i * 0.02f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = alpha),
                    Color.White.copy(alpha = 0f)
                ),
                startY = y - bandH,
                endY = y + bandH
            ),
            topLeft = Offset(x, y - bandH),
            size = Size(bandH * 6f, bandH * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandH, bandH)
        )
    }
    // Ground fog: a soft white wash over the near ridge, so the cannon sits in
    // the fog rather than above it.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)),
            startY = h * 0.62f,
            endY = h
        ),
        size = Size(w, h * 0.38f)
    )
}

private fun DrawScope.drawSkyline(b: GameBackdrop, horizon: Float, t: Float) {
    val w = size.width
    // A row of towers of varying height and width, deterministic from the
    // index so it does not reshuffle between frames.
    var x = -dpPx(6f)
    var i = 0
    while (x < w + dpPx(10f)) {
        val bw = dpPx(20f) + ((i * 37) % 5) * dpPx(7f)
        val bh = dpPx(38f) + ((i * 53) % 7) * dpPx(15f)
        val top = horizon - bh
        drawRect(
            color = b.midTerrain,
            topLeft = Offset(x, top),
            size = Size(bw, bh)
        )
        // Lit windows, on a slow flicker so the city is not static.
        val cols = (bw / dpPx(8f)).toInt().coerceAtLeast(1)
        val rows = (bh / dpPx(12f)).toInt().coerceAtLeast(1)
        for (cx in 0 until cols) {
            for (cy in 0 until rows) {
                val k = (i * 31 + cx * 7 + cy * 13)
                if (k % 3 != 0) continue
                val on = abs(sin(t * 0.7f + k * 0.35f)) > 0.25f
                drawRect(
                    color = b.accent.copy(alpha = if (on) 0.55f else 0.12f),
                    topLeft = Offset(x + dpPx(2.5f) + cx * dpPx(8f), top + dpPx(4f) + cy * dpPx(12f)),
                    size = Size(dpPx(3.4f), dpPx(5f))
                )
            }
        }
        x += bw + dpPx(3f)
        i++
    }
}

/** Spinning golden bonus cube with its power-up icon. */
private fun DrawScope.drawBonus(bonus: ir.amiraligrr.blockboom.domain.BonusBlock) {
    val cx = bonus.centerX
    val cy = bonus.centerY
    val r = bonus.size * 0.5f
    val dim = bonus.size * (0.92f + 0.12f * sin(bonus.rotation * 0.02f))

    // Golden glow + trail
    drawCircle(
        color = NeonGold.copy(alpha = 0.4f),
        radius = r * 1.7f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = NeonGold.copy(alpha = 0.85f),
        radius = r * 0.96f,
        center = Offset(cx, cy)
    )

    // Rotating inner diamond to sell the "bonus" look.
    val inner = regularPolygonPath(cx, cy, dim * 0.34f, 4, 45f + bonus.rotation * 0.5f)
    drawPath(inner, color = OffWhite)

    // Icon
    GamePaint.bonus.textSize = bonus.size * 0.62f
    drawContext.canvas.nativeCanvas.drawText(
        bonus.type.icon,
        cx,
        cy + bonus.size * 0.22f,
        GamePaint.bonus
    )
}

/**
 * dp to pixels, for the draw layer. Shared with [PowerUpScreenOverlay] so the
 * overlay sizes its strokes to the device the same way the field does.
 */
internal fun DrawScope.dpPx(value: Float): Float = value * density

// ------------------------------------------------------------------ blocks

/**
 * The opacity already applied to a block's colour.
 *
 * The damage-based translucency is baked into the colour at the call site, so
 * every derived stop (tint, shade) has to carry the same alpha or the gradient
 * will show the background through one band of an otherwise opaque block.
 */
private fun baseAlphaOf(color: Color): Float = color.alpha

private fun DrawScope.drawBlock(block: Block, engine: GameEngine, sceneIsLight: Boolean) {
    val hpRatio = (block.hp.toFloat() / block.maxHp.toFloat()).coerceIn(0f, 1f)
    val damage = 1f - hpRatio
    val baseAlpha = if (block.isBoss) 0.78f + 0.22f * hpRatio else 0.55f + 0.45f * hpRatio
    // The block's own palette colour, dealt per block by the engine so no two
    // blocks in flight look alike. Seeded from colorSeed, never from
    // shakePhase — the hit animation mutates shakePhase, and deriving colour
    // from it made every block change shade each time it was struck.
    // The MOTHER wears her own colour, not a palette slot — see motherColor() for
    // why she has to stay off the deck her children are dealt from. Her shade
    // tracks her HP, so the enrage phase is visible on her body.
    val hpRatioForColor = (block.hp.toFloat() / max(1, block.maxHp).toFloat()).coerceIn(0f, 1f)
    // Modifier dressing, before the fill colour is resolved.
    //
    // ICE reads as a frosted, paler block; BOULDER as a heavier, desaturated
    // one. Both are colour shifts rather than icons, because a board can hold
    // forty blocks and an icon on each of them is noise — the block has to
    // announce its own rule from its own surface.
    val color = if (block.isIce) {
        blockColorTint(blockColorFor(block.colorIndex, block.maxHp, false, block.colorSeed, baseAlpha), 1.22f)
    } else if (block.isBoulder) {
        blockColorShade(blockColorFor(block.colorIndex, block.maxHp, false, block.colorSeed, baseAlpha), 0.62f)
    } else if (block.isMother) {
        motherColor(hpRatioForColor, enraged = hpRatioForColor < MOTHER_ENRAGE_HP_RATIO, alpha = baseAlpha)
    } else {
        blockColorFor(
            index = block.colorIndex,
            hp = block.maxHp,
            boss = block.isBoss,
            colorSeed = block.colorSeed,
            alpha = baseAlpha
        )
    }
    val shakeX = if (block.shakeTime > 0f) {
        sin(block.shakePhase + engine.clockSeconds * 8f) * block.w * 0.05f
    } else 0f

    // The block's contrasting rim. See blockOutlineColor(): against the daylight
    // backdrop's green terrain the weakest fill in the palette measured 1.00:1
    // — the same luminance as the ground — so a third of the blocks were
    // effectively invisible. The rim is drawn under the fill, slightly larger, so
    // it reads as an edge rather than as a second shape.
    //
    // Drawn for every shape here, once, rather than inside each drawBlock* so
    // the five shapes cannot drift apart and a new shape cannot ship without it.
    val rim = blockOutlineColor(color, sceneIsLight)

    val cx = block.centerX + shakeX
    val cy = block.centerY
    val half = block.w * 0.5f
    val topLeft = Offset(block.x + shakeX, block.y)

    val rimWidth = (block.w * 0.055f).coerceIn(dpPx(1.5f), dpPx(3f))
    val grow = rimWidth

    // Lucky blocks swell on every hit. Scaling the whole block — not just an
    // overlay — is what makes the hit land: the silhouette the player is tracking
    // physically flinches, which is the only feedback they get now that the HP
    // number is hidden.
    //
    // Applied to the geometry the draw functions receive rather than via
    // graphicsLayer, so the hitboxes are not being lied to about size mid-flight.
    // Gated on isLucky as well as the puff. Block.applyHit() sets luckyPuff on
    // every block it hits, so testing the timer alone swelled *ordinary* blocks
    // by 22% on every shot — a second hit reaction stacked on the flash and the
    // HP number that are already their feedback, and it contradicted
    // Block.luckyPuff's own doc ("non-lucky blocks ignore this entirely").
    val swell = if (block.isLucky && block.luckyPuff > 0f) {
        1f + 0.22f * block.luckyPuff
    } else {
        1f
    }

    // How far the lucky rainbow rim sits outside the block. In dp: the raw 2f
    // it used to be was a third of that on a 3x screen.
    val rimPad = dpPx(2f)

    when (block.shapeType) {
        BlockShape.SQUARE -> {
            drawRoundRect(
                color = rim,
                topLeft = Offset(topLeft.x - grow, block.y - grow),
                size = Size(block.w + grow * 2, block.h + grow * 2),
                cornerRadius = CornerRadius(block.w * 0.25f + grow, block.w * 0.25f + grow)
            )
            if (swell > 1f) {
                // Grow about the centre, so the block expands in place instead of
                // sliding down-right as its top-left corner moves.
                val inset = -(block.w * (swell - 1f)) * 0.5f
                val insetH = -(block.h * (swell - 1f)) * 0.5f
                drawBlockSquare(
                    block, color,
                    Offset(topLeft.x + inset, block.y + insetH),
                    engine, block.w * swell, block.h * swell
                )
            } else {
                drawBlockSquare(block, color, topLeft, engine)
            }
        }
        BlockShape.CIRCLE -> {
            drawCircle(color = rim, radius = half * swell + grow, center = Offset(cx, cy))
            drawBlockCircle(block, color, cx, cy, half * swell)
        }
        BlockShape.HEXAGON -> {
            drawPath(regularPolygonPath(cx, cy, half * swell + grow, 6, 0f), color = rim)
            drawBlockPolygon(block, color, cx, cy, half * swell, 6, 0f)
        }
        // Pointing up, and sized to the *inscribed* radius so the shape fills its
        // bounding box the way the square and circle do — see BlockShape: hitboxes
        // are rectangles, so a shape that only filled part of its box would look
        // hittable where it is not, and look wrong where it is.
        BlockShape.TRIANGLE -> {
            val r = half * swell / 0.866f
            // Shifted down by r/4 to centre the shape on its bounding box.
            //
            // An up-pointing equilateral triangle spans cy - r to cy + r/2, but
            // the hitbox is cy - h/2 to cy + h/2. Drawn on the circumcentre it
            // overshot the top of the box by 0.077w and left the bottom fifth
            // empty -- so shots at the visible tip passed through, and shots in
            // the invisible lower quarter hit nothing. (w == h for every block
            // except the MOTHER, whose box this still lines up with, since the
            // offset is derived from the box it is centring on.)
            val cyTri = cy + r * 0.25f
            drawPath(regularPolygonPath(cx, cyTri, r + grow, 3, -90f), color = rim)
            drawBlockPolygon(block, color, cx, cyTri, r, 3, -90f)
        }
        BlockShape.STAR -> {
            val r = half * swell / 0.951f
            drawPath(starPath(cx, cy, r + grow), color = rim)
            drawBlockStar(block, color, cx, cy, half * swell)
        }
    }

    // Lucky chance block adornment — rainbow aura + clover icon (Ball Blast)
    if (block.isLucky) {
        val pulse = 0.75f + 0.25f * sin(engine.tick * 0.18f + block.shakePhase)
        drawCircle(color = NeonGold.copy(alpha = 0.35f * pulse), radius = half * 1.9f, center = Offset(cx, cy))
        // Rainbow rim
        val luckyRim = LUCKY_RAINBOW[
            ((block.colorIndex % LUCKY_RAINBOW.size) + LUCKY_RAINBOW.size) % LUCKY_RAINBOW.size
        ]
        when (block.shapeType) {
            // The rim is inset in dp, and it is positioned off the shaken centre
            // like the fill is. Keyed off block.x/y it stayed put while the body
            // jittered for the whole 0.18s of shakeTime, so the outline visibly
            // detached from the block it is outlining. The triangle branch uses
            // the same scaled radius as the fill, or the rim traces a shape 13%
            // smaller than the block.
            BlockShape.SQUARE -> drawRoundRect(color = luckyRim.copy(alpha = 0.95f), topLeft = Offset(block.x + shakeX - rimPad, block.y - rimPad), size = Size(block.w + rimPad * 2f, block.h + rimPad * 2f), cornerRadius = CornerRadius(block.w * 0.25f, block.w * 0.25f), style = Stroke(width = dpPx(3.2f) * pulse))
            BlockShape.CIRCLE -> drawCircle(color = luckyRim.copy(alpha = 0.95f), radius = half + rimPad, center = Offset(cx, cy), style = Stroke(width = dpPx(3.2f) * pulse))
            // The star's rim has to come from starPath, not regularPolygonPath:
            // a 10-gon at the star's inner radius is a completely different
            // outline, and the rim is drawn wider than the block so the mismatch
            // would be visible around the whole shape.
            BlockShape.STAR -> drawPath(starPath(cx, cy, half / 0.951f + rimPad), color = luckyRim.copy(alpha = 0.95f), style = Stroke(width = dpPx(3.2f) * pulse))
            BlockShape.TRIANGLE -> {
                val r = half * swell / 0.866f
                drawPath(regularPolygonPath(cx, cy + r * 0.25f, r + rimPad, 3, -90f), color = luckyRim.copy(alpha = 0.95f), style = Stroke(width = dpPx(3.2f) * pulse))
            }
            else -> drawPath(regularPolygonPath(cx, cy, half + rimPad, sidesFor(block.shapeType), startDegFor(block.shapeType)), color = luckyRim.copy(alpha = 0.95f), style = Stroke(width = dpPx(3.2f) * pulse))
        }
        drawLuckyBlock(block, cx, cy, half)
    }

    // Cracks are skipped on lucky blocks: the swell and debris in drawLuckyBlock
    // are their hit feedback, and overlaying a static crack pattern on top made
    // both read as noise.
    if (!block.isLucky) drawCracks(block, cx, cy, half, damage)

    // The mother's marks: horns and a health ring.
    //
    // She is the largest object on the board but was otherwise drawn like any
    // other hexagon, so the boss had nothing on screen marking her as the thing
    // the level is about. The horns and the ring give her a silhouette you can
    // find while the board is full, and the ring is her remaining HP — which,
    // with her health in the tens of thousands, is the only readable statement
    // of how the fight is going.
    if (block.isMother) drawMotherMarks(block, cx, cy, half)
    if (block.hasArmor) drawArmour(block, cx, cy, half)

    // ── Last-stand warning ───────────────────────────────────────────────
    // A spent block used to crawl at the cannon with no warning at all. Now
    // the telegraph is visible: a pulsing red ring plus a countdown arc while
    // it winds up, and speed streaks once it charges. The block is shootable
    // for the whole telegraph, so the warning is also the instruction.
    if (block.isHoming) {
        val danger = Color(0xFFFF3B30)
        val pulse = 0.55f + 0.45f * kotlin.math.sin(engine.clockSeconds * 14f)
        if (block.lastStandTimer > 0f) {
            drawCircle(
                color = danger.copy(alpha = 0.16f * pulse),
                radius = half * 1.8f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = danger.copy(alpha = 0.9f),
                radius = half * 1.5f,
                center = Offset(cx, cy),
                style = Stroke(width = dpPx(3f) * pulse)
            )
            val fraction = (block.lastStandTimer / 0.78f).coerceIn(0f, 1f)
            drawArc(
                color = danger,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(cx - half * 1.8f, cy - half * 1.8f),
                size = Size(half * 3.6f, half * 3.6f),
                style = Stroke(width = dpPx(4f))
            )
        } else {
            val dir = if (block.chargeDir >= 0f) 1f else -1f
            repeat(3) { i ->
                val yOff = (i - 1) * half * 0.55f
                drawLine(
                    color = danger.copy(alpha = 0.55f),
                    start = Offset(cx - dir * half * 1.0f, cy + yOff),
                    end = Offset(cx - dir * half * 2.2f, cy + yOff),
                    strokeWidth = dpPx(3f)
                )
            }
        }
    }

    // Lucky blocks show "?" instead of their HP.
    //
    // Showing the number made the block a worse deal than every other block on
    // the board — you could see it was the weakest thing falling at you, and the
    // reward was random anyway. Hiding the number also means the only feedback a
    // hit produces is the physical one: the swell in drawLuckyBlock above. That
    // feedback has to carry the whole read, which is why it is a real animation
    // rather than a number ticking down.
    if (block.hp > 0 && !block.isLucky) {
        val maxTextWidth = block.w * 0.85f
        val text = "${block.hp}"
        GamePaint.number.textSize = block.h * 0.48f
        while (GamePaint.number.measureText(text) > maxTextWidth && GamePaint.number.textSize > 8f) {
            GamePaint.number.textSize *= 0.85f
        }
        val numberY = cy + GamePaint.number.descent() * 0.5f
        drawContext.canvas.nativeCanvas.drawText(text, cx, numberY, GamePaint.number)
    }
}

/**
 * The lucky block's face, and the hit reaction.
 *
 * Drawn as an overlay on top of whatever shape the block has, so it works for
 * all five silhouettes without a second implementation per shape.
 *
 * The swell scales the *whole* block via [Block.luckyPuff] — a 22% pop that
 * springs back — while the debris ring is drawn here as short radial spikes whose
 * length follows the same curve. Both are driven off one value so they cannot
 * drift apart, and the ring is drawn even as the swell decays so the hit is
 * legible for its whole duration rather than only at the peak.
 */
private fun DrawScope.drawLuckyBlock(block: Block, cx: Float, cy: Float, half: Float) {
    val puff = block.luckyPuff

    // The "?" mark, plus the sparkle that says "chance".
    GamePaint.lucky.textSize = half * 1.05f
    val qy = cy + GamePaint.lucky.descent() * 0.5f
    drawContext.canvas.nativeCanvas.drawText("?", cx, qy, GamePaint.lucky)

    if (puff <= 0f) return

    // Debris ring. Spikes lengthen then retract with the swell, so the motion
    // reads as the block flinching rather than as a static halo.
    val spikes = 7
    for (i in 0 until spikes) {
        val angle = (i * 360f / spikes + block.shakePhase * 40f) * 0.0174533f
        val inner = half * 0.82f
        val outer = half * (0.95f + 0.42f * puff)
        drawLine(
            color = NeonGold.copy(alpha = 0.55f * puff),
            start = Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
            end = Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
            strokeWidth = dpPx(2.2f) * (0.5f + puff)
        )
    }

    // A brighter core flash at the peak of the swell.
    drawCircle(
        color = NeonGold.copy(alpha = 0.30f * puff * puff),
        radius = half * (0.7f + 0.5f * puff),
        center = Offset(cx, cy)
    )
}

/** Jagged black fissures that grow as a block's HP drops. */
private fun DrawScope.drawCracks(block: Block, cx: Float, cy: Float, half: Float, damage: Float) {
    if (damage < 0.12f) return
    val count = (1 + damage * 4f).toInt().coerceIn(1, 5)
    val crackColor = Color.Black.copy(alpha = 0.30f + 0.40f * damage)

    for (i in 0 until count) {
        val angle = block.shakePhase + i * 1.7f
        val len = half * (0.45f + 0.55f * damage)
        val path = Path()
        path.moveTo(cx, cy)
        val steps = 3
        for (s in 1..steps) {
            val f = s / steps.toFloat()
            val px = cx + kotlin.math.cos(angle) * len * f + sin(angle * 3f + i) * half * 0.14f
            val py = cy + sin(angle) * len * f + kotlin.math.cos(angle * 2f + i) * half * 0.14f
            path.lineTo(px, py)
        }
        drawPath(
            path,
            color = crackColor,
            style = Stroke(width = dpPx(1.5f + damage * 2.5f))
        )
    }
}

private fun DrawScope.drawBlockSquare(
    block: Block,
    color: Color,
    topLeft: Offset,
    engine: GameEngine,
    /** Draw size. Defaults to the block's own, and only differs while a lucky
     *  block is swelling — see Block.luckyPuff. */
    w: Float = block.w,
    h: Float = block.h
) {
    val blockSize = Size(w, h)
    val corner = CornerRadius(
        x = (w * 0.25f).coerceAtMost(h * 0.5f),
        y = (w * 0.25f).coerceAtMost(h * 0.5f)
    )
    // Soft outer glow
    drawRoundRect(
        color = color.copy(alpha = 0.28f),
        topLeft = Offset(topLeft.x - w * 0.06f, topLeft.y - h * 0.06f),
        size = Size(w * 1.12f, h * 1.12f),
        cornerRadius = CornerRadius(corner.x * 1.15f, corner.y * 1.15f)
    )
    // Drop shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(topLeft.x + w * 0.06f, topLeft.y + h * 0.09f),
        size = blockSize,
        cornerRadius = corner
    )
    // Main gradient fill.
    //
    // This was three stops of the *same* colour at different alphas
    // (0.98 / 0.75 / 0.88). Because alpha is the only thing varying, the block
    // had no actual colour range — the darker band in the middle read as a
    // shadow through a translucent tile rather than as a deeper version of the
    // hue, and the flat alpha notch was visible as a band on large blocks.
    //
    // It is now a real three-stop gradient in HSL: lit at the top, saturated
    // through the middle, deep at the bottom. Derived from the block's own
    // colour, so a blue block gets a deeper blue and a yellow one a deeper
    // gold, rather than both going grey.
    val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            blockColorTint(color, 1.18f).copy(alpha = baseAlphaOf(color)),
            color.copy(alpha = baseAlphaOf(color)),
            blockColorShade(color, 0.80f).copy(alpha = baseAlphaOf(color))
        ),
        startY = topLeft.y,
        endY = topLeft.y + h
    )
    drawRoundRect(brush = gradient, topLeft = topLeft, size = blockSize, cornerRadius = corner)
    // Glossy top highlight (3D)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = Offset(topLeft.x + w * 0.08f, topLeft.y + h * 0.06f),
        size = Size(w * 0.84f, h * 0.28f),
        cornerRadius = CornerRadius(corner.x * 0.65f, corner.y * 0.65f)
    )
    // Thin inner inner light line
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
        size = Size(w - 4f, h - 4f),
        cornerRadius = CornerRadius(corner.x * 0.9f, corner.y * 0.9f),
        style = Stroke(width = 1.2f)
    )
    // Hit flash
    if (block.hitFlash > 0f) {
        drawRoundRect(
            color = Color.White.copy(alpha = block.hitFlash * 0.55f),
            topLeft = topLeft,
            size = blockSize,
            cornerRadius = corner
        )
    }
    // Boss golden frame + pulsing
    if (block.isBoss) {
        val pulse = 0.7f + 0.3f * sin(engine.tick * 0.12f)
        drawRoundRect(
            color = NeonGold.copy(alpha = 0.85f * pulse),
            topLeft = topLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = w * 0.07f)
        )
        // Crown highlight
        drawRoundRect(
            color = NeonGold.copy(alpha = 0.25f),
            topLeft = Offset(topLeft.x - 3f, topLeft.y - 3f),
            size = Size(w + 6f, h + 6f),
            cornerRadius = corner,
            style = Stroke(width = 2f)
        )
    } else {
        // Subtle neon rim for normal blocks
        drawRoundRect(
            color = color.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = 1.6f)
        )
    }
}

/** A five-pointed star path, outer radius [r], inner radius 0.46r. */
private fun DrawScope.starPath(cx: Float, cy: Float, r: Float): Path {
    val inner = r * 0.46f
    return Path().apply {
        for (i in 0 until 10) {
            // Every other vertex alternates outer/inner; -90° puts a point at the
            // top, matching the triangle's orientation.
            val radius = if (i % 2 == 0) r else inner
            val angle = (-90f + i * 36f) * 0.0174533f
            val px = cx + cos(angle) * radius
            val py = cy + sin(angle) * radius
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

/**
 * The star block.
 *
 * Built to match drawBlockPolygon/drawBlockCircle layer for layer — glow,
 * drop shadow, vertical gradient, top shine, hit flash, boss rim — so a board
 * mixing all five shapes still reads as one coherent set of objects rather than
 * as two different renderers.
 *
 * The radius is corrected so the star spans its bounding box: a five-pointed star
 * with points at 0°/±72° is narrower than it is tall, so drawing it at `half`
 * left visible gaps at the left and right edges of a box the player can hit.
 */
private fun DrawScope.drawBlockStar(block: Block, color: Color, cx: Float, cy: Float, half: Float) {
    // 0.951 = cos(18°), the widest horizontal extent of a 5-point star at outer
    // radius 1. Dividing by it makes the star span the full half-width.
    val r = half / 0.951f
    val path = starPath(cx, cy, r)
    val a = baseAlphaOf(color)

    drawCircle(color = color.copy(alpha = 0.24f), radius = r * 1.18f, center = Offset(cx, cy))
    drawPath(starPath(cx + r * 0.07f, cy + r * 0.09f, r * 0.98f), color = Color.Black.copy(alpha = 0.32f))
    drawPath(path, color = color.copy(alpha = a))
    drawPath(
        path,
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                blockColorTint(color, 1.16f).copy(alpha = 0.55f * a),
                Color.Transparent,
                blockColorShade(color, 0.76f).copy(alpha = 0.55f * a)
            ),
            startY = cy - r,
            endY = cy + r
        )
    )
    val shine = Path().apply {
        val c = Offset(cx - r * 0.14f, cy - r * 0.30f)
        addOval(androidx.compose.ui.geometry.Rect(c, r * 0.26f))
    }
    drawPath(shine, color = Color.White.copy(alpha = 0.20f))
    if (block.hitFlash > 0f) {
        drawPath(path, color = Color.White.copy(alpha = block.hitFlash * 0.55f))
    }
    if (block.isBoss) {
        drawPath(path, color = NeonGold.copy(alpha = 0.88f), style = Stroke(width = r * 0.14f))
        drawPath(
            starPath(cx, cy, r * 1.12f),
            color = NeonGold.copy(alpha = 0.20f),
            style = Stroke(width = 3f)
        )
    } else {
        drawPath(path, color = Color.White.copy(alpha = 0.16f), style = Stroke(width = 1.5f))
    }
}

private fun DrawScope.drawBlockCircle(block: Block, color: Color, cx: Float, cy: Float, r: Float) {
    val a = baseAlphaOf(color)
    // Outer glow + shadow
    drawCircle(color = color.copy(alpha = 0.26f), radius = r * 1.65f, center = Offset(cx, cy))
    drawCircle(color = Color.Black.copy(alpha = 0.30f), radius = r, center = Offset(cx + r * 0.08f, cy + r * 0.1f))
    // Real gradient fill, off-centre like a light source above and to the left.
    // The old version painted a flat disc and then a white radial overlay, so
    // every circular block was the same tone and the "3D" was just a white
    // smudge. The rim stop is now the shaded version of the block's own hue.
    drawCircle(brush = androidx.compose.ui.graphics.Brush.radialGradient(
        colors = listOf(
            blockColorTint(color, 1.16f).copy(alpha = a),
            color.copy(alpha = a),
            blockColorShade(color, 0.78f).copy(alpha = a)
        ),
        center = Offset(cx - r * 0.32f, cy - r * 0.38f),
        radius = r * 1.55f
    ), radius = r, center = Offset(cx, cy))
    // Specular bloom
    drawCircle(brush = androidx.compose.ui.graphics.Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
        center = Offset(cx - r * 0.3f, cy - r * 0.35f),
        radius = r * 0.9f
    ), radius = r, center = Offset(cx, cy))
    // Highlight dot
    drawCircle(color = Color.White.copy(alpha = 0.22f), radius = r * 0.58f, center = Offset(cx - r * 0.22f, cy - r * 0.24f))
    if (block.hitFlash > 0f) {
        drawCircle(color = Color.White.copy(alpha = block.hitFlash * 0.55f), radius = r, center = Offset(cx, cy))
    }
    if (block.isBoss) {
        drawCircle(
            color = NeonGold.copy(alpha = 0.85f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.12f)
        )
        drawCircle(color = NeonGold.copy(alpha = 0.18f), radius = r * 1.28f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
    } else {
        // Rim in the block's own hue rather than white: a white hairline on a
        // pale block is invisible, and on a dark one it reads as a seam.
        drawCircle(
            color = blockColorTint(color, 1.25f).copy(alpha = 0.30f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.6f)
        )
    }
}

private fun DrawScope.drawBlockPolygon(
    block: Block,
    color: Color,
    cx: Float,
    cy: Float,
    r: Float,
    sides: Int,
    startDeg: Float
) {
    val path = regularPolygonPath(cx, cy, r, sides, startDeg)
    val a = baseAlphaOf(color)
    // Glow + shadow
    drawCircle(color = color.copy(alpha = 0.24f), radius = r * 1.55f, center = Offset(cx, cy))
    val shadowPath = regularPolygonPath(cx + r * 0.07f, cy + r * 0.09f, r * 0.98f, sides, startDeg)
    drawPath(shadowPath, color = Color.Black.copy(alpha = 0.32f))
    // Vertical gradient clipped to the polygon, matching the square and circle
    // so a mixed-shape board still reads as one coherent set of objects.
    drawPath(path, color = color.copy(alpha = a))
    drawPath(
        path,
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                blockColorTint(color, 1.16f).copy(alpha = 0.55f * a),
                Color.Transparent,
                blockColorShade(color, 0.76f).copy(alpha = 0.55f * a)
            ),
            startY = cy - r,
            endY = cy + r
        )
    )
    // Top shine
    val shine = Path().apply {
        val c = Offset(cx - r * 0.18f, cy - r * 0.22f)
        addOval(androidx.compose.ui.geometry.Rect(c, r * 0.45f))
    }
    drawPath(shine, color = Color.White.copy(alpha = 0.20f))
    if (block.hitFlash > 0f) {
        drawPath(path, color = Color.White.copy(alpha = block.hitFlash * 0.55f))
    }
    if (block.isBoss) {
        drawPath(
            path,
            color = NeonGold.copy(alpha = 0.88f),
            style = Stroke(width = r * 0.14f)
        )
        // Outer golden aura
        drawPath(
            regularPolygonPath(cx, cy, r * 1.12f, sides, startDeg),
            color = NeonGold.copy(alpha = 0.20f),
            style = Stroke(width = 3f)
        )
    } else {
        drawPath(path, color = Color.White.copy(alpha = 0.16f), style = Stroke(width = 1.5f))
    }
}

private fun regularPolygonPath(cx: Float, cy: Float, r: Float, sides: Int, startDeg: Float): Path {
    val start = startDeg * 0.0174533f
    val step = 6.2832f / sides
    return Path().apply {
        for (i in 0 until sides) {
            val angle = start + step * i
            val px = cx + kotlin.math.cos(angle) * r
            val py = cy + kotlin.math.sin(angle) * r
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

/**
 * Side count for the lucky-block rainbow rim.
 *
 * STAR has no single "sides" value, so it is given 10 and drawn through
 * [starPath] by the caller instead — see the `when` in drawBlock. Keeping this
 * fallback honest matters because it is the only branch that would silently draw
 * nothing if a new shape were added without updating it.
 */
private fun sidesFor(shape: BlockShape): Int = when (shape) {
    BlockShape.SQUARE -> 4
    BlockShape.CIRCLE -> 32
    BlockShape.HEXAGON -> 6
    BlockShape.TRIANGLE -> 3
    BlockShape.STAR -> 10
}

private fun startDegFor(shape: BlockShape): Float = when (shape) {
    BlockShape.SQUARE -> 45f
    BlockShape.CIRCLE -> 0f
    BlockShape.HEXAGON -> 0f
    BlockShape.TRIANGLE -> -90f
    BlockShape.STAR -> -90f
}

// ------------------------------------------------------------------- balls

/**
 * A projectile: its skin-aware trail, then the sprite itself.
 *
 * The trail used to be a chain of shrinking circles in the ball's flat colour,
 * identical for every skin, so a fire ball and a gold ball had the same streak
 * and only the disc differed. The trail is now part of the skin, which is half
 * of what made a purchase look like a purchase.
 */
private fun DrawScope.drawBall(ball: Ball, skinManager: SkinManager) {
    val center = Offset(ball.x, ball.y)
    val r = ball.currentRadius
    val visual = skinManager.getBallVisual()
    // Rotation, so the non-radial forms (star, crystal, spiked) visibly spin as
    // they fly instead of reading as the same static disc.
    val phase = ball.age * 6f

    drawBallAura(ball, center, r)

    drawBallTrail(ball, visual, r)

    drawBallSprite(visual, center, r, phase)
}

/**
 * The active power-up mark on a ball in flight.
 *
 * Both flags here were set on every shot and read by nothing: RAPID FIRE's
 * `isFlaming` and GHOST's `isSpectral` were assigned at spawn and then never
 * consulted, so the two power-ups whose whole point is "your shots are different
 * right now" were invisible in the volley. The HUD chip was the only evidence,
 * and the chip disappears the moment the player looks back at the board.
 *
 * Drawn under the trail and the sprite, so it reads as light coming off the
 * ball rather than as a sticker on top of it.
 */
private fun DrawScope.drawBallAura(ball: Ball, center: Offset, r: Float) {
    if (ball.isFlaming) {
        val pulse = 0.72f + 0.28f * sin(ball.age * 14f)
        drawCircle(
            color = Color(0xFFFF9A3C).copy(alpha = 0.30f * pulse),
            radius = r * 1.85f,
            center = center
        )
        drawCircle(
            color = Color(0xFFFFD166).copy(alpha = 0.45f * pulse),
            radius = r * 1.20f,
            center = center
        )
    }
    if (ball.isSpectral) {
        // Cool, transparent, and outlined rather than filled — GHOST is absence of
        // harm, so it should not look like more power.
        val pulse = 0.68f + 0.32f * sin(ball.age * 9f)
        drawCircle(
            color = Color(0xFFB39DFF).copy(alpha = 0.26f * pulse),
            radius = r * 2.05f,
            center = center
        )
        drawCircle(
            color = Color(0xFFE8DEFF).copy(alpha = 0.70f * pulse),
            radius = r * 1.15f,
            center = center,
            style = Stroke(width = r * 0.32f)
        )
    }
}

private fun DrawScope.drawBallTrail(ball: Ball, visual: BallVisual, r: Float) {
    for (i in 0 until Ball.TRAIL_SIZE) {
        val point = ball.trailPoint(i) ?: break
        val fade = ball.trailAlphaRatio(i)
        if (fade <= 0f) break
        val p = Offset(point.first, point.second)
        val shrink = (1f - i * 0.10f).coerceAtLeast(0.25f)
        when (visual.trail) {
            BallTrail.DOT -> drawCircle(
                color = visual.glow,
                radius = r * 0.72f * shrink,
                center = p,
                alpha = fade * 0.30f
            )
            BallTrail.EMBER -> drawEmber(p, r * 0.50f * shrink, visual.primary, fade * 0.55f)
            BallTrail.GLINT -> drawGlint(p, r * 0.60f * shrink, visual.lit, fade * 0.50f)
            BallTrail.SPARK -> drawCircle(
                color = visual.lit,
                radius = r * 0.26f * shrink,
                center = p,
                alpha = fade * 0.65f
            )
            // A continuous ribbon rather than separate marks, so a fast-moving
            // ribbon ball leaves an unbroken streak.
            // i + 1, not i - 1: `i` already counts back from the newest point,
            // so the previous one is further from the head, not nearer. The
            // negative index read one slot *ahead* of the newest write, which is
            // either an unwritten (0,0) — stroking a line in from the top-left
            // corner of the field on every shot's first few frames — or, once
            // the ring buffer had wrapped, the oldest point, so the i=0 segment
            // spanned the entire trail instead of one segment of it.
            BallTrail.RIBBON -> ball.trailPoint(i + 1)?.let { prev ->
                drawLine(
                    color = visual.glow,
                    start = Offset(prev.first, prev.second),
                    end = p,
                    strokeWidth = r * 1.1f * shrink,
                    cap = StrokeCap.Round,
                    alpha = fade * 0.35f
                )
            }
            // Smoke: a soft grey puff that grows as it fades, so the trail
            // reads as something the rocket left behind rather than as marks
            // on the screen.
            BallTrail.SMOKE -> drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        visual.secondary.copy(alpha = fade * 0.30f),
                        visual.secondary.copy(alpha = 0f)
                    ),
                    center = p,
                    radius = r * (0.5f + (1f - shrink) * 0.9f)
                ),
                radius = r * (0.5f + (1f - shrink) * 0.9f),
                center = p
            )
        }
    }
}

// ------------------------------------------------------------------- coins

private fun DrawScope.drawCoin(coin: Coin) {
    val wobble = 1f + 0.18f * sin(coin.rotation * 0.02f)
    val radius = dpPx(12f) * wobble
    val center = Offset(coin.x, coin.y)

    drawCircle(color = NeonGold.copy(alpha = 0.3f), radius = radius * 1.8f, center = center)
    drawCircle(color = CoinDark, radius = radius, center = center)
    drawCircle(color = CoinLight, radius = radius * 0.72f, center = center)

    GamePaint.coin.textSize = radius * 1.25f
    drawContext.canvas.nativeCanvas.drawText(
        "$",
        center.x,
        center.y + radius * 0.4f,
        GamePaint.coin
    )
}

// --------------------------------------------------------------- fx / popups

private fun DrawScope.drawPowerUpDrop(drop: ir.amiraligrr.blockboom.domain.PowerUpDrop) {
    val cx = drop.centerX
    val cy = drop.centerY
    val r = drop.size * 0.5f
    val color = drop.type.color

    // Pulsing glow
    val pulse = 1f + 0.2f * sin(drop.age * 8f)
    drawCircle(
        color = color.copy(alpha = 0.4f),
        radius = r * 1.8f * pulse,
        center = Offset(cx, cy)
    )

    // Main body with gradient-like effect
    drawCircle(
        color = color.copy(alpha = 0.9f),
        radius = r * 0.95f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = r * 0.6f,
        center = Offset(cx - r * 0.15f, cy - r * 0.2f)
    )

    // Rotating border
    val borderPath = regularPolygonPath(cx, cy, r * 1.1f, 6, drop.rotation)
    drawPath(
        borderPath,
        color = color.copy(alpha = 0.6f),
        style = Stroke(width = dpPx(2f))
    )

    // Power-up icon
    GamePaint.bonus.textSize = drop.size * 0.55f
    drawContext.canvas.nativeCanvas.drawText(
        drop.type.icon,
        cx,
        cy + drop.size * 0.2f,
        GamePaint.bonus
    )

    // Collection hint when near cannon (handled by game engine logic)
}

private fun DrawScope.drawParticle(particle: ir.amiraligrr.blockboom.domain.Particle) {
    val r = particle.radius
    if (r <= 0.1f) return
    val center = Offset(particle.x, particle.y)
    val a = particle.alpha

    if (particle.spark) {
        // A spark gets a soft halo as well as its core, which is what sells it
        // as hot. One flat circle of the same radius reads as a dot no matter
        // what its colour is.
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = a * 0.9f),
                    particle.color.copy(alpha = a * 0.55f),
                    Color.Transparent
                ),
                center = center,
                radius = r * 2.6f
            ),
            radius = r * 2.6f,
            center = center
        )
        drawCircle(color = particle.color.copy(alpha = a), radius = r, center = center)
    } else {
        drawCircle(color = particle.color.copy(alpha = a), radius = r, center = center)
        // A slight rim keeps debris from dissolving into the background as it
        // fades, so a large particle still has a silhouette in its last frames.
        drawCircle(
            color = Color.White.copy(alpha = a * 0.22f),
            radius = r,
            center = center,
            style = Stroke(width = maxOf(1f, r * 0.18f))
        )
    }
}

private fun DrawScope.drawExplosion(explosion: ir.amiraligrr.blockboom.domain.Explosion) {
    val center = Offset(explosion.x, explosion.y)
    val t = explosion.alpha
    val p = explosion.progress
    val radius = explosion.radius

    // Core flash.
    //
    // This was a filled disc at `radius * (0.6 + 0.4t)` — so it *grew* for the
    // whole lifetime, at up to 0.5 alpha, on top of a ring already expanding
    // outward. The result was a translucent blob that grew and shrank in
    // opacity at the same time, and never read as a point of light.
    //
    // Now it is a small, bright, fast-decaying core: radius collapses, alpha
    // falls off a curve, so the flash is over almost immediately and leaves
    // the ring behind it to do the work of describing the blast.
    val coreAlpha = t * t
    if (coreAlpha > 0.01f) {
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = coreAlpha * 0.95f),
                    explosion.color.copy(alpha = coreAlpha * 0.7f),
                    Color.Transparent
                ),
                center = center,
                radius = explosion.startRadius * 2.2f
            ),
            radius = explosion.startRadius * 2.2f,
            center = center
        )
    }

    // Shockwave ring. Thins as it expands, which is what a pressure front
    // does; the old width formula grew it instead.
    drawCircle(
        color = explosion.color.copy(alpha = t * 0.8f),
        radius = radius,
        center = center,
        style = Stroke(
            width = (dpPx(3.2f) * (1f - p) + dpPx(0.8f)) * (0.6f + 0.4f * (1f - p)),
            cap = StrokeCap.Round
        )
    )

    // A second, slower wave trailing the first. One ring is a circle; two at
    // different rates is a blast with a leading edge and a wake.
    val trailAlpha = t * t * 0.45f
    if (trailAlpha > 0.01f) {
        drawCircle(
            color = explosion.color.copy(alpha = trailAlpha),
            radius = radius * 0.62f,
            center = center,
            style = Stroke(width = dpPx(1.6f), cap = StrokeCap.Round)
        )
    }
}

// ------------------------------------------------------------------ cannon

private fun DrawScope.drawCannon(engine: GameEngine, skinManager: SkinManager) {
    val c = engine.cannon
    val glowPulse = 0.12f * sin(c.pulseTime * 4f)
    val center = Offset(c.centerX, c.centerY)
    val cannonSkin = skinManager.getCannonVisual()
    val cannonColor = cannonSkin.body
    // The cannon is dressed in the equipped ball's colours.
    //
    // It used to be a fixed cyan no matter what ball was loaded, so a fire ball
    // and a gold ball both came out of a cyan barrel. Tying the ring and the
    // muzzle flash to the ball makes the loadout read as one object.
    val ball = skinManager.getBallVisual()

    // Neon halo. The skin's own glow, so a premium cannon announces itself
    // across the play area rather than only on the barrel.
    drawCircle(
        color = cannonSkin.glow.copy(alpha = 0.22f),
        radius = c.radius * (1.6f + glowPulse),
        center = center
    )
    // Outer ring + inner body. The ring is the *trim*, which is the part that
    // tells one cannon skin from another once the board is full of blocks.
    drawCircle(color = cannonSkin.trim.copy(alpha = 0.75f), radius = c.radius * 1.18f, center = center)
    drawCircle(color = cannonColor, radius = c.radius, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(cannonSkin.lit, cannonColor, cannonSkin.trim),
            center = Offset(center.x - c.radius * 0.22f, center.y - c.radius * 0.26f),
            radius = c.radius
        ),
        radius = c.radius * 0.86f,
        center = center
    )
    // A band across the top of the body, so the cannon has a front.
    drawArc(
        color = cannonSkin.trim.copy(alpha = 0.85f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - c.radius * 0.74f, center.y - c.radius * 0.74f),
        size = Size(c.radius * 1.48f, c.radius * 1.48f),
        style = Stroke(width = dpPx(2.4f))
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(ball.lit, ball.primary, ball.secondary),
            center = Offset(center.x - c.radius * 0.2f, center.y - c.radius * 0.25f),
            radius = c.radius
        ),
        radius = c.radius * 0.5f,
        center = center
    )

    // Nozzle: the ball's colour, with a trim collar at the muzzle so the barrel
    // reads as part of the cannon rather than as a separate object.
    val nozzleW = dpPx(12f)
    val nozzleH = dpPx(20f)
    val nozzleTopLeft = Offset(c.x - nozzleW / 2f, center.y - c.radius - nozzleH)
    drawRoundRect(
        color = ball.primary,
        topLeft = nozzleTopLeft,
        size = Size(nozzleW, nozzleH),
        cornerRadius = CornerRadius(dpPx(5f), dpPx(5f))
    )
    drawRoundRect(
        color = cannonSkin.trim,
        topLeft = Offset(c.x - nozzleW * 0.78f, center.y - c.radius - dpPx(5f)),
        size = Size(nozzleW * 1.56f, dpPx(6f)),
        cornerRadius = CornerRadius(dpPx(2f), dpPx(2f))
    )

    // Base stand, in the trim so it separates from the play field behind it.
    drawRoundRect(
        color = cannonSkin.trim,
        topLeft = Offset(c.x - c.radius * 0.9f, c.baseY - dpPx(12f)),
        size = Size(c.radius * 1.8f, dpPx(26f)),
        cornerRadius = CornerRadius(dpPx(10f), dpPx(10f))
    )
    drawRoundRect(
        color = cannonColor.copy(alpha = 0.85f),
        topLeft = Offset(c.x - c.radius * 0.72f, c.baseY - dpPx(8f)),
        size = Size(c.radius * 1.44f, dpPx(14f)),
        cornerRadius = CornerRadius(dpPx(7f), dpPx(7f))
    )

    // Muzzle flash while recoiling
    if (c.recoil > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = c.recoil), ball.glow.copy(alpha = c.recoil * 0.8f), Color.Transparent),
                center = Offset(c.x, center.y - c.radius - nozzleH * 0.5f),
                radius = c.radius * 1.1f
            ),
            radius = c.radius * 1.1f,
            center = Offset(c.x, center.y - c.radius - nozzleH * 0.5f)
        )
    }
}

// ---------------------------------------------------------------- paint pool

private object GamePaint {
    val number: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isFakeBoldText = true
        setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(200, 0, 0, 0))
    }
    val lucky: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(220, 0, 0, 0))
    }

    val coin: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB45309.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isFakeBoldText = true
    }

    val bonus: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
}

private val CoinDark = Color(0xFFB45309)
private val CoinLight = Color(0xFFFDE68A)
private val CannonBody = Color(0xFF6D28D9)
/**
 * The HP fraction at which the mother enrages.
 *
 * A pure telegraph now: she changes colour at half health and nothing else
 * changes. It used to have to agree exactly with the spawn-rate change in the
 * engine, because the colour and a triple-spawn had to land together.
 */
private const val MOTHER_ENRAGE_HP_RATIO = 0.5f

/**
 * The mother's horns, eye and health ring.
 *
 * Drawn as an overlay so it works regardless of which shape she happens to be
 * spawned with, and so it cannot drift out of sync with the shape draw functions.
 *
 * The ring is her remaining HP. It used to be a spawn clock, filling as
 * [Block.spawnTimer] ran down to the next child — an honest clock while the
 * mother produced, and meaningless the moment she stopped. With her HP in the
 * tens of thousands the number on her body is unreadable at speed, so the ring
 * is what the fight is actually read from.
 */
private fun DrawScope.drawMotherMarks(block: Block, cx: Float, cy: Float, half: Float) {
    val hpRatio = (block.hp.toFloat() / max(1, block.maxHp).toFloat()).coerceIn(0f, 1f)
    val enraged = hpRatio < MOTHER_ENRAGE_HP_RATIO
    val accent = if (enraged) Color(0xFFF472B6) else Color(0xFFFDE047)

    // Health ring: the remaining fraction of her HP, clockwise from the top.
    if (hpRatio > 0.02f) {
        drawArc(
            brush = Brush.sweepGradient(
                listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.75f))
            ),
            startAngle = -90f,
            sweepAngle = 360f * hpRatio,
            useCenter = false,
            topLeft = Offset(cx - half * 1.16f, cy - half * 1.16f),
            size = Size(half * 2.32f, half * 2.32f),
            style = Stroke(width = dpPx(3f))
        )
    }

    // Horns.
    val hornW = half * 0.30f
    val hornH = half * 0.42f
    for (side in intArrayOf(-1, 1)) {
        val bx = cx + side * half * 0.62f
        val by = cy - half * 0.52f
        val horn = Path().apply {
            moveTo(bx - hornW * 0.5f, by)
            lineTo(bx + side * hornW * 0.35f, by - hornH)
            lineTo(bx + hornW * 0.5f, by + hornH * 0.18f)
            close()
        }
        drawPath(horn, color = Color(0xFF2B0A1C))
        drawPath(horn, color = accent.copy(alpha = 0.85f), style = Stroke(width = dpPx(1.6f)))
    }

    // Eye — two glowing slits. Reads as a face at any size, which is what makes
    // her legible as a character rather than as a big block.
    val eyeY = cy - half * 0.10f
    val eyeW = half * 0.20f
    val eyeH = half * 0.075f
    for (side in intArrayOf(-1, 1)) {
        val ex = cx + side * half * 0.26f
        drawRoundRect(
            color = accent,
            topLeft = Offset(ex - eyeW * 0.5f, eyeY - eyeH * 0.5f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeH * 0.5f, eyeH * 0.5f)
        )
    }

    // Mouth: a jagged line, because a smile would read as friendly and the whole
    // point is that she is not.
    val mouthY = cy + half * 0.24f
    val teeth = 5
    val span = half * 0.62f
    val path = Path().apply {
        moveTo(cx - span * 0.5f, mouthY)
        for (i in 0 until teeth) {
            val t = (i + 0.5f) / teeth
            lineTo(cx - span * 0.5f + span * t, mouthY + if (i % 2 == 0) -half * 0.07f else half * 0.05f)
        }
        lineTo(cx + span * 0.5f, mouthY)
    }
    drawPath(path, color = Color(0xFF2B0A1C), style = Stroke(width = dpPx(2.2f)))
}

// ─────────────────────────────────────────────────────────────────────────
//  Landscape detail — mountains, trees, ground cover
//
//  The scene was sky + three soft ridges and nothing else. That is why every
//  backdrop read as "a gradient": a ridge is a curve, and a screen full of curves
//  has no hard edges for the eye to grab hold of, so the whole thing stays flat
//  no matter how the colours are chosen.
//
//  These three layers add the missing hard edges, and they are placed by depth
//  rather than scattered:
//
//    mountains  — behind the far ridge, low contrast, never moving much
//    trees      — on the mid ridge, trunks visible, canopies overhanging forward
//    cover      — on the near ridge, small and fast, purely as texture
//
//  Everything is derived from the backdrop's own three terrain colours, so a new
//  backdrop gets a landscape for free and none of these can clash with it.
// ─────────────────────────────────────────────────────────────────────────

/**
 * A jagged peak silhouette behind the far ridge.
 *
 * Peaks are triangles rather than curves because that is the whole point: the
 * eye needs a hard edge up there to read depth against the soft ridges below.
 */
private fun DrawScope.peakPath(baseY: Float, peaks: Int, maxH: Float, seed: Float): Path {
    val w = size.width
    val step = w / peaks
    return Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        for (i in 0 until peaks) {
            // Deterministic pseudo-random from the index, so the skyline is the
            // same every frame and drifts with `seed` rather than reshuffling.
            val r1 = abs(sin(seed + i * 1.7f))
            val r2 = abs(sin(seed * 1.3f + i * 2.9f))
            val topY = baseY - maxH * (0.45f + r1 * 0.55f)
            val midX = x + step * (0.5f + r2 * 0.25f)
            // A slight shoulder on one side so the peaks are not a row of
            // identical triangles.
            lineTo(midX - step * 0.18f, baseY - maxH * 0.22f * r2)
            lineTo(midX, topY)
            lineTo(midX + step * 0.34f, baseY - maxH * 0.14f * r1)
            x += step
            lineTo(x, baseY)
        }
        lineTo(w, size.height)
        close()
    }
}

private fun DrawScope.drawMountains(b: GameBackdrop, baseY: Float, t: Float) {
    // Not for the night scenes: hard black peaks against a starfield read as
    // mountains blocking the sky, which is a different (and wrong) picture.
    if (b.style != BackdropStyle.DAYLIGHT) return
    // Dunes and coasts have no mountains. A dune field with a mountain range
    // behind it is not a desert, it is the default background recoloured — which
    // is exactly what every daylight scene here used to be.
    if (b.motif == TerrainMotif.DUNES || b.motif == TerrainMotif.COAST) return

    val peakH = if (b.motif == TerrainMotif.SNOW) dpPx(118f) else dpPx(96f)
    // Two ranges, the far one lighter and smaller, for aerial perspective.
    val back = peakPath(baseY - dpPx(10f), peaks = 5, maxH = peakH * 0.62f, seed = 1.7f)
    drawPath(back, b.farTerrain.copy(alpha = 0.55f))

    val front = peakPath(baseY + dpPx(4f), peaks = 3, maxH = peakH, seed = 4.3f)
    drawPath(front, b.farTerrain.copy(alpha = 0.85f))

    // Snow caps, and only where the scene has any. A cap on a green meadow is a
    // white triangle in the wrong place, and a cap that does not line up with its
    // own peak reads as a floating white triangle anyway — so this clips to the
    // peak path rather than drawing a free triangle.
    if (b.motif == TerrainMotif.SNOW) {
        drawPath(
            front,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                startY = baseY - peakH,
                endY = baseY - peakH * 0.40f
            )
        )
    }

    // Parallax: the ranges drift at different rates, which is what makes them
    // read as being at different distances.
    if (t > 0f) Unit   // `t` is unused for geometry; kept for signature symmetry
}

private fun DrawScope.treePath(x: Float, baseY: Float, h: Float, w: Float): Pair<Path, Path> {
    val trunk = Path().apply {
        moveTo(x - w * 0.16f, baseY)
        lineTo(x - w * 0.10f, baseY - h * 0.42f)
        lineTo(x + w * 0.10f, baseY - h * 0.42f)
        lineTo(x + w * 0.16f, baseY)
        close()
    }
    val canopy = Path().apply {
        moveTo(x, baseY - h)
        lineTo(x + w * 0.5f, baseY - h * 0.40f)
        lineTo(x + w * 0.30f, baseY - h * 0.40f)
        lineTo(x + w * 0.46f, baseY - h * 0.05f)
        lineTo(x - w * 0.46f, baseY - h * 0.05f)
        lineTo(x - w * 0.30f, baseY - h * 0.40f)
        lineTo(x - w * 0.5f, baseY - h * 0.40f)
        close()
    }
    return trunk to canopy
}

/**
 * A broad, round canopy on a short trunk — a blossom tree rather than a conifer.
 *
 * The silhouette is the whole point: the conifer below is a stack of spikes, and
 * putting a pink tree in the forest backdrop is not a cherry orchard, it is a
 * conifer with a different colour. This one is a dome.
 */
private fun DrawScope.blossomPath(x: Float, baseY: Float, h: Float, w: Float): Pair<Path, Path> {
    val trunk = Path().apply {
        moveTo(x - w * 0.09f, baseY)
        lineTo(x - w * 0.05f, baseY - h * 0.34f)
        lineTo(x + w * 0.05f, baseY - h * 0.34f)
        lineTo(x + w * 0.09f, baseY)
        close()
    }
    val canopy = Path().apply {
        moveTo(x - w * 0.52f, baseY - h * 0.30f)
        cubicTo(
            x - w * 0.62f, baseY - h * 0.86f,
            x + w * 0.62f, baseY - h * 0.86f,
            x + w * 0.52f, baseY - h * 0.30f
        )
        cubicTo(
            x + w * 0.26f, baseY - h * 0.14f,
            x - w * 0.26f, baseY - h * 0.14f,
            x - w * 0.52f, baseY - h * 0.30f
        )
        close()
    }
    return trunk to canopy
}

/** A palm: a bare curved trunk with fronds. Only on the coast. */
private fun DrawScope.palmPath(x: Float, baseY: Float, h: Float, w: Float): Pair<Path, Path> {
    val lean = w * 0.30f
    val trunk = Path().apply {
        moveTo(x - w * 0.10f, baseY)
        cubicTo(x - w * 0.06f, baseY - h * 0.5f, x + lean * 0.5f, baseY - h * 0.8f, x + lean - w * 0.05f, baseY - h)
        lineTo(x + lean + w * 0.05f, baseY - h)
        cubicTo(x + lean * 0.5f + w * 0.12f, baseY - h * 0.8f, x + w * 0.12f, baseY - h * 0.5f, x + w * 0.14f, baseY)
        close()
    }
    val canopy = Path().apply {
        val topX = x + lean
        val topY = baseY - h
        for (i in 0..5) {
            val a = (-160f + i * 44f) * 0.0174533f
            val tipX = topX + cos(a) * w * 0.62f
            val tipY = topY + sin(a) * w * 0.44f + w * 0.10f
            val midX = topX + cos(a + 0.5f) * w * 0.26f
            val midY = topY + sin(a + 0.5f) * w * 0.20f - w * 0.10f
            if (i == 0) moveTo(topX, topY) else lineTo(midX, midY)
            lineTo(tipX, tipY)
            lineTo(midX, midY + w * 0.10f)
        }
        close()
    }
    return trunk to canopy
}

private fun DrawScope.drawTrees(
    b: GameBackdrop,
    baseY: Float,
    t: Float,
    canopiesOnly: Boolean = false
) {
    // The fog scene gets trees too, as dark silhouettes: the style check is
    // what keeps the daylight scenes' green conifers out of it, and the palette
    // (nearTerrain is a slate grey there) does the rest.
    if (b.style != BackdropStyle.DAYLIGHT && b.style != BackdropStyle.FOG) return
    // Dune fields and lava fields have no trees at all. There was nothing to put
    // there but the same conifer every daylight scene had.
    if (b.motif == TerrainMotif.DUNES || b.motif == TerrainMotif.ROCK) return

    val count = if (b.motif == TerrainMotif.COAST) 4 else 7
    val treeH = if (b.motif == TerrainMotif.COAST) dpPx(74f) else dpPx(52f)
    val treeW = treeH * 0.62f
    // Slow drift, slower than the mid ridge they stand on, so they read as being
    // on it rather than sliding across it.
    // `drift` has to be reduced by the *same* period `x` wraps on, or every
    // time drift jumps the whole row teleports sideways by the difference —
    // treeW, about 32dp — in a single frame. It was wrapping on treeW * 2f
    // while x wrapped on treeW * 3f.
    val wrapPeriod = size.width + treeW * 3f
    val drift = (t * 9f) % wrapPeriod

    for (i in 0 until count) {
        val seed = i * 2.31f
        val spacing = size.width / count
        // -1 so the first tree starts off-screen left and wraps in.
        val raw = spacing * i - drift
        val x = ((raw % wrapPeriod) + wrapPeriod) % wrapPeriod - treeW * 1.5f
        // Vary the size a little so the row is not a picket fence.
        val scale = 0.82f + 0.35f * abs(sin(seed))
        val h = treeH * scale
        val w = treeW * scale
        // Sink each trunk slightly so it looks planted rather than resting on.
        val y = baseY + dpPx(3f) * scale

        val (trunk, canopy) = when (b.motif) {
            TerrainMotif.BLOSSOM -> blossomPath(x, y, h, w)
            TerrainMotif.COAST -> palmPath(x, y, h, w)
            else -> treePath(x, y, h, w)
        }

        if (!canopiesOnly) {
            drawPath(trunk, color = b.nearTerrain.copy(alpha = 0.55f))
        }
        drawPath(canopy, color = b.nearTerrain)
        // A lighter face on the sun side, which is what stops the canopy reading
        // as a flat blob.
        drawPath(canopy, color = b.midTerrain.copy(alpha = 0.45f))
        // Snow settles on the branches, and lava lights them from underneath.
        if (b.motif == TerrainMotif.SNOW) {
            drawPath(canopy, color = Color.White.copy(alpha = 0.30f))
        }
        if (b.motif == TerrainMotif.ROCK) {
            drawPath(canopy, color = b.accent.copy(alpha = 0.16f))
        }
    }
}

private fun DrawScope.drawGroundDetail(b: GameBackdrop, baseY: Float, t: Float) {
    if (b.style != BackdropStyle.DAYLIGHT) return

    // Same period as the wrap below — see drawTrees.
    val wrapPeriod = size.width + dpPx(90f)
    val drift = (t * 22f) % wrapPeriod
    for (i in 0 until 9) {
        val seed = i * 3.77f
        val spacing = size.width / 9f
        val raw = spacing * i - drift
        val x = ((raw % wrapPeriod) + wrapPeriod) % wrapPeriod - dpPx(45f)
        val y = baseY + dpPx(10f) + 6f * abs(sin(seed))
        val r = dpPx(3f + 3f * abs(cos(seed * 1.7f)))
        // A lighter tone of the near terrain reads as a lit pebble; the darker
        // one as a shadow. Both, so they sit on the ground rather than float.
        when (b.motif) {
            // Scrub: a tuft, not a ball. Three short strokes off a common root.
            TerrainMotif.DUNES -> {
                drawLine(
                    color = b.midTerrain.copy(alpha = 0.7f),
                    start = Offset(x, y),
                    end = Offset(x - r * 0.7f, y - r * 1.8f),
                    strokeWidth = dpPx(1.5f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = b.midTerrain.copy(alpha = 0.55f),
                    start = Offset(x, y),
                    end = Offset(x + r * 0.8f, y - r * 1.5f),
                    strokeWidth = dpPx(1.5f),
                    cap = StrokeCap.Round
                )
            }
            // A glowing crack: the only light source on a lava field.
            TerrainMotif.ROCK -> {
                drawLine(
                    color = b.accent.copy(alpha = 0.30f),
                    start = Offset(x - r * 2.2f, y),
                    end = Offset(x + r * 2.2f, y - r * 0.4f),
                    strokeWidth = dpPx(2f),
                    cap = StrokeCap.Round
                )
            }
            TerrainMotif.SNOW -> {
                drawCircle(color = Color.White.copy(alpha = 0.26f), radius = r * 1.3f, center = Offset(x, y))
            }
            else -> {
                drawCircle(color = b.accent.copy(alpha = 0.22f), radius = r, center = Offset(x, y + r * 0.7f))
                drawCircle(color = b.midTerrain.copy(alpha = 0.75f), radius = r, center = Offset(x, y))
            }
        }
    }
}

/**
 * The two layers that make a dune field a dune field.
 *
 * Drawn instead of mountains and trees: a wide, shallow S-curve for each ridge
 * (wind-shaped, so it leans the same way every time) and a couple of long
 * shadow troughs between them. Both use the backdrop's own terrain colours, so a
 * scene buys a different *shape* and not just a different tint.
 */
private fun DrawScope.drawDunes(b: GameBackdrop, baseY: Float) {
    val h = dpPx(46f)
    for (i in 0..2) {
        val y = baseY + i * dpPx(17f)
        val amp = h * (0.55f + 0.2f * i)
        val path = Path().apply {
            moveTo(-size.width * 0.1f, y + amp)
            cubicTo(
                size.width * 0.22f, y - amp,
                size.width * 0.52f, y + amp * 0.8f,
                size.width * 1.1f, y - amp * 0.5f
            )
            lineTo(size.width * 1.1f, y + amp * 2.4f)
            lineTo(-size.width * 0.1f, y + amp * 2.4f)
            close()
        }
        val fill = when (i) {
            0 -> b.farTerrain.copy(alpha = 0.85f)
            1 -> b.midTerrain.copy(alpha = 0.9f)
            else -> b.nearTerrain
        }
        drawPath(path, fill)
        // The lit crest, which is what stops a dune reading as a flat wave.
        drawPath(path, color = b.orb.copy(alpha = 0.16f), style = Stroke(width = dpPx(1.6f)))
    }
}

/**
 * Petals falling through a blossom scene.
 *
 * Drawn over the terrain rather than behind it, and only for [TerrainMotif.BLOSSOM]
 * — this is the one background detail that is allowed in front of the play field,
 * because it is small, slow and the lowest contrast thing on screen. It is what
 * makes the scene a season rather than a colour scheme.
 */
private fun DrawScope.drawPetals(b: GameBackdrop, t: Float) {
    if (b.motif != TerrainMotif.BLOSSOM) return
    val count = 16
    for (i in 0 until count) {
        val seed = i * 1.37f
        val speed = dpPx(16f + 14f * abs(sin(seed)))
        // Wrapped, so the column never empties and never doubles up.
        val y = ((t * speed + seed * 137f) % (size.height + dpPx(40f)))
        val sway = sin(t * 1.1f + seed * 3f) * dpPx(16f)
        val x = ((seed * 91f) % size.width) + sway
        val r = dpPx(2.2f + 1.4f * abs(cos(seed * 2.3f)))
        // A petal is a small ellipse turned on the fall, not a dot.
        rotate(sin(t * 1.6f + seed) * 42f, Offset(x, y)) {
            drawOval(
                color = b.accent.copy(alpha = 0.55f),
                topLeft = Offset(x - r, y - r * 0.62f),
                size = Size(r * 2f, r * 1.24f)
            )
        }
    }
}

/**
 * The armour plate drawn over an armoured block.
 *
 * Drawn as corner brackets plus a hairline rather than a filled overlay, so the
 * block's own colour and number stay readable underneath — an armoured block
 * that cannot be read is an armoured block the player cannot aim at.
 *
 * The brackets shrink as the shield is stripped, which is what makes the shield
 * a *countdown* the player can see rather than an invisible tax on every hit.
 */
private fun DrawScope.drawArmour(block: Block, cx: Float, cy: Float, half: Float) {
    if (!block.hasArmor || block.armorHp <= 0) return

    val steel = Color(0xFFB8C4D0)
    // Divided by what the block *started* with, so the ring actually empties as
    // the armour is stripped. Dividing by the remaining value gives 1 - 1 = 0
    // wear forever, which is to say: the ring never showed damage at all.
    val wear = 1f - (block.armorHp.toFloat() / max(1, block.armorMaxHp).toFloat())
    // Shrink the plate as it is stripped, so the last hits visibly land.
    val inset = half * (0.16f + 0.10f * wear.coerceIn(0f, 0.9f))
    val armLen = half * 0.34f
    val w = dpPx(2.2f)

    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val x = cx + sx * inset
            val y = cy + sy * inset
            drawLine(
                color = steel.copy(alpha = 0.85f),
                start = Offset(x, y),
                end = Offset(x - sx * armLen, y),
                strokeWidth = w
            )
            drawLine(
                color = steel.copy(alpha = 0.85f),
                start = Offset(x, y),
                end = Offset(x, y - sy * armLen),
                strokeWidth = w
            )
        }
    }

    // Remaining shield as a thin arc under the block, so "how much is left" is
    // answerable at a glance without a second number on the block itself.
    if (block.armorHp > 0) {
        // Against the block's OWN starting armour, not a hardcoded 24.
        //
        // 24 was the old standalone cap and is still the shape of the base term,
        // but the total is now that plus the level's armour budget. Dividing by
        // 24 alone meant that from roughly level 60 every block saturated the arc
        // and it stopped reporting anything — a 2000-level block with 46 armour
        // and one with 70 drew exactly the same full arc. Using armorMaxHp makes
        // the ring mean "how much of this block's armour is left" at every level.
        val frac = (block.armorHp.toFloat() / max(1, block.armorMaxHp).toFloat())
            .coerceIn(0.08f, 1f)
        drawArc(
            color = steel.copy(alpha = 0.9f),
            startAngle = 200f,
            sweepAngle = 140f * frac,
            useCenter = false,
            topLeft = Offset(cx - half * 0.55f, cy + half * 0.30f),
            size = Size(half * 1.1f, half * 1.1f),
            style = Stroke(width = dpPx(2.4f))
        )
    }
}
