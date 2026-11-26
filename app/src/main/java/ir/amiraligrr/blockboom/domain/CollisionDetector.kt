package ir.amiraligrr.blockboom.domain

import kotlin.math.abs
import kotlin.math.hypot

object CollisionDetector {

    /** Circle (ball) vs rounded-rect (block) intersection. */
    fun ballHitsBlock(bx: Float, by: Float, br: Float, block: Block): Boolean =
        ballHitsRect(bx, by, br, block.x, block.y, block.w, block.h)

    /** Same test on raw rect bounds, so callers do not have to build a [Block]. */
    fun ballHitsRect(bx: Float, by: Float, br: Float, rx: Float, ry: Float, rw: Float, rh: Float): Boolean {
        val cx = bx.coerceIn(rx, rx + rw)
        val cy = by.coerceIn(ry, ry + rh)
        val dx = bx - cx
        val dy = by - cy
        return dx * dx + dy * dy <= br * br
    }

    /**
     * Swept test for a ball that moved from ([ax],[ay]) to ([bx],[by]) this
     * frame. A plain point test lets a fast ball step clean over a small block
     * and never register the hit, so the path between the two positions is
     * tested against the rect grown by the ball radius.
     */
    fun sweptBallHitsBlock(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        br: Float,
        block: Block
    ): Boolean {
        if (ballHitsBlock(bx, by, br, block)) return true
        val dx = bx - ax
        val dy = by - ay
        // Already overlapping the grown rect on both axes -> the cheap test above
        // would have caught it; only the moving case needs the slab test.
        if (abs(dx) < 0.0001f && abs(dy) < 0.0001f) return false
        return segmentHitsRect(
            ax, ay, bx, by,
            block.x - br, block.y - br,
            block.x + block.w + br, block.y + block.h + br
        )
    }

    /** Liang-Barsky segment vs axis-aligned rect. */
    private fun segmentHitsRect(
        ax: Float, ay: Float, bx: Float, by: Float,
        minX: Float, minY: Float, maxX: Float, maxY: Float
    ): Boolean {
        val dx = bx - ax
        val dy = by - ay
        var t0 = 0f
        var t1 = 1f

        if (abs(dx) < 0.0001f) {
            if (ax < minX || ax > maxX) return false
        } else {
            var tA = (minX - ax) / dx
            var tB = (maxX - ax) / dx
            if (tA > tB) { val tmp = tA; tA = tB; tB = tmp }
            if (tA > t0) t0 = tA
            if (tB < t1) t1 = tB
            if (t0 > t1) return false
        }

        if (abs(dy) < 0.0001f) {
            if (ay < minY || ay > maxY) return false
        } else {
            var tA = (minY - ay) / dy
            var tB = (maxY - ay) / dy
            if (tA > tB) { val tmp = tA; tA = tB; tB = tmp }
            if (tA > t0) t0 = tA
            if (tB < t1) t1 = tB
            if (t0 > t1) return false
        }
        return true
    }

    /** Rect (block) vs circle (cannon body) intersection. */
    fun rectHitsCircle(cx: Float, cy: Float, cr: Float, block: Block): Boolean =
        rectHitsCircle(cx, cy, cr, block.x, block.y, block.w, block.h)

    /**
     * Same test on raw rect bounds.
     *
     * The cannon uses this with a rect inset from the block's visual bounds:
     * several block shapes (triangles, stars) fill only part of their bounding
     * box, so testing the full box made a shot that visibly missed still count
     * as a hit. A smaller, honest hitbox is what removes those phantom deaths.
     */
    fun rectHitsCircle(
        cx: Float, cy: Float, cr: Float,
        rx: Float, ry: Float, rw: Float, rh: Float
    ): Boolean {
        val closestX = cx.coerceIn(rx, rx + rw)
        val closestY = cy.coerceIn(ry, ry + rh)
        val dx = cx - closestX
        val dy = cy - closestY
        return dx * dx + dy * dy <= cr * cr
    }

    /** Center-to-center distance check. */
    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return hypot(bx - ax, by - ay)
    }
}