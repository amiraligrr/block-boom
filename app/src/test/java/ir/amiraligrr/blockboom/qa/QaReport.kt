package ir.amiraligrr.blockboom.qa

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import ir.amiraligrr.blockboom.QaFindings
import java.io.File

object QaReport {
    val strings = mutableListOf<String>()
    val shots = mutableListOf<File>()
    fun fail(msg: String) { strings.add(msg) }
    fun note(msg: String) { strings.add(msg) }
    fun shot(f: File) { shots.add(f) }

    /** Where screenshots go. `build/qa` at the repo root, not inside `app/`. */
    fun shotDir(): File =
        File(System.getProperty("blockboom.qa.dir") ?: "build/qa").also { it.mkdirs() }
}

object Qa {
    private fun outDir(): File =
        File(System.getProperty("blockboom.qa.dir") ?: "build/qa").also { it.mkdirs() }

    private fun toBitmap(img: ImageBitmap): Bitmap {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val px = img.toPixelMap()
        for (y in 0 until img.height) for (x in 0 until img.width) {
            bmp.setPixel(x, y, px[x, y].toArgb())
        }
        return bmp
    }

    fun save(name: String, img: ImageBitmap): File {
        val dir = outDir()
        val f = File(dir, "$name.png")
        val report = StringBuilder()
        try {
            report.append("prop=").append(System.getProperty("blockboom.qa.dir"))
                .append(" size=").append(img.width).append("x").append(img.height)
            val bmp = toBitmap(img)
            f.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            report.append(" wrote=").append(f.exists()).append("/").append(f.length())
        } catch (t: Throwable) {
            report.append(" ERROR=").append(t.javaClass.name).append(":").append(t.message)
        }
        // Surfaced rather than swallowed: a screenshot that silently failed to
        // write is a silent pass, which is worse than a failed one.
        QaFindings.diagnostic("QA-SAVE[$name] $report")
        QaReport.shot(f)
        QaFindings.shot(f.name)
        return f
    }

    /**
     * A zoomed crop of a failing box, so a human can see what the numbers saw.
     *
     * Contrast failures on decorated UI are frequently ambiguous from numbers
     * alone — gloss, shimmer and emoji all paint pixels that are not type. The
     * crop settles "is this label actually readable" in one glance, which is
     * the difference between fixing a real bug and chasing a measurement
     * artifact for an hour.
     */
    fun saveCrop(name: String, img: ImageBitmap, box: IntRect): File {
        val pad = 24
        val scale = 3
        val x0 = (box.left - pad).coerceAtLeast(0)
        val y0 = (box.top - pad).coerceAtLeast(0)
        val x1 = (box.right + pad).coerceAtMost(img.width)
        val y1 = (box.bottom + pad).coerceAtMost(img.height)
        val src = toBitmap(img)
        try {
            val w = ((x1 - x0) * scale).coerceAtLeast(1)
            val h = ((y1 - y0) * scale).coerceAtLeast(1)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            canvas.scale(scale.toFloat(), scale.toFloat())
            canvas.drawBitmap(
                src,
                android.graphics.Rect(x0, y0, x1, y1),
                android.graphics.RectF(0f, 0f, (x1 - x0).toFloat(), (y1 - y0).toFloat()),
                null
            )
            val f = File(outDir(), "$name.png")
            try {
                f.outputStream().use { stream -> out.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            } finally {
                out.recycle()
            }
            QaReport.shot(f)
            QaFindings.shot(f.name)
            return f
        } finally {
            src.recycle()
        }
    }
}

/** Depth-first walk of the unmerged semantics tree. */
fun walkTree(root: SemanticsNode, visit: (SemanticsNode) -> Unit) {
    visit(root)
    root.children.forEach { walkTree(it, visit) }
}