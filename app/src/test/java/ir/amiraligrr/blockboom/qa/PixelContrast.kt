package ir.amiraligrr.blockboom.qa

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** An axis-aligned box in root pixels. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
}

/**
 * Contrast measured from pixels that were actually painted.
 *
 * The static audit in `tools/check_contrast.py` compares colours the source
 * *names*. This compares the colours the device *drew*. The two disagree in
 * exactly the cases that ship: a gradient whose two stops were each checked
 * against nothing, a colour composited over an unknown backdrop, an alpha
 * channel that quietly moved a label from 5:1 to 1.9:1, and a button whose
 * label colour is computed at runtime from a theme slot.
 */
object PixelContrast {

    /** WCAG relative luminance of an 8-bit sRGB triple. */
    fun luminance(r: Int, g: Int, b: Int): Double {
        fun ch(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }

    /** WCAG contrast ratio, 1.0..21.0. */
    fun ratio(ar: Int, ag: Int, ab: Int, br: Int, bg: Int, bb: Int): Double {
        val la = luminance(ar, ag, ab)
        val lb = luminance(br, bg, bb)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun bucket(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
    }

    private fun unbucket(key: Int): Triple<Int, Int, Int> {
        val r = ((key shr 10) and 0x1F) shl 3
        val g = ((key shr 5) and 0x1F) shl 3
        val b = (key and 0x1F) shl 3
        return Triple(r, g, b)
    }

    /**
     * The contrast actually rendered inside [box].
     *
     * The method: the background is the most common colour in the box, the ink
     * is the populated colour that got *furthest* from it, and the reported
     * ratio is between those two. Taking the extreme rather than an average is
     * deliberate — an average over an anti-aliased glyph is pulled back toward
     * the background and understates a real contrast, while the solid core of
     * the stroke is the colour the type was actually set in.
     *
     * A minimum population keeps anti-aliasing dust and gradient seams from
     * being read as type; a box with no text at all comes back as null.
     */
    fun measure(image: ImageBitmap, box: IntRect): Result? {
        val x0 = max(0, box.left)
        val y0 = max(0, box.top)
        val x1 = min(image.width, box.right)
        val y1 = min(image.height, box.bottom)
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 1 || h <= 1) return null

        val px = image.toPixelMap()
        val counts = HashMap<Int, Int>()
        val sumR = HashMap<Int, Long>()
        val sumG = HashMap<Int, Long>()
        val sumB = HashMap<Int, Long>()

        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val argb = px[x, y].toArgb()
                val k = bucket(argb)
                counts[k] = (counts[k] ?: 0) + 1
                sumR[k] = (sumR[k] ?: 0) + ((argb shr 16) and 0xFF)
                sumG[k] = (sumG[k] ?: 0) + ((argb shr 8) and 0xFF)
                sumB[k] = (sumB[k] ?: 0) + (argb and 0xFF)
            }
        }
        if (counts.isEmpty()) return null
        val total = (w * h).toDouble()

        // Background = the modal colour. On a card or a button that is the fill;
        // on a bare label over the page it is the page.
        val bgKey = counts.entries.maxByOrNull { it.value }!!.key
        val (br, bg, bb) = unbucket(bgKey)
        val bgLum = luminance(br, bg, bb)

        // Ink = the populated colour furthest in luminance from the background.
        var inkLum = bgLum
        var inkKey = -1
        val minPop = max(3, (w * h) / 400)
        for ((k, count) in counts) {
            if (k == bgKey || count < minPop) continue
            val (r, g, b) = unbucket(k)
            val l = luminance(r, g, b)
            if (abs(l - bgLum) > abs(inkLum - bgLum)) {
                inkLum = l
                inkKey = k
            }
        }
        if (inkKey < 0) return Result(0.0, 0.0, null, Triple(br, bg, bb))

        val n = counts[inkKey]!!
        val ir = (sumR[inkKey]!! / n).toInt()
        val ig = (sumG[inkKey]!! / n).toInt()
        val ib = (sumB[inkKey]!! / n).toInt()

        return Result(
            ratio = ratio(ir, ig, ib, br, bg, bb),
            inkCoverage = n / total,
            ink = Triple(ir, ig, ib),
            background = Triple(br, bg, bb)
        )
    }

    data class Result(
        val ratio: Double,
        val inkCoverage: Double,
        val ink: Triple<Int, Int, Int>?,
        val background: Triple<Int, Int, Int>?
    ) {
        fun hex(c: Triple<Int, Int, Int>?) =
            c?.let { "#%02X%02X%02X".format(it.first, it.second, it.third) } ?: "-"
    }
}

/** Bounds of a node in the root's coordinate space. */
fun SemanticsNode.rootBounds(): IntRect {
    val b: Rect = boundsInRoot
    return IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
}