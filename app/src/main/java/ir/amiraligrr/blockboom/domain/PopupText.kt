package ir.amiraligrr.blockboom.domain

import androidx.compose.ui.graphics.Color

/**
 * Floating text (score gains, damage ticks, combo announcements).
 * Rendered on the game canvas and rises + fades out. Pooled via
 * [PopupTextPool] — these are created on every block crack and every combo, so
 * a fresh object plus an interpolated String per hit was real GC churn.
 */
class PopupText {

    var x: Float = 0f
    var y: Float = 0f
    var text: String = ""
    var color: Color = Color.White
    var age: Float = 0f
    var life: Float = 0.8f
    var big: Boolean = false

    val alpha: Float get() = (1f - age / life).coerceIn(0f, 1f)

    fun reset() {
        x = 0f
        y = 0f
        text = ""
        color = Color.White
        age = 0f
        life = 0.8f
        big = false
    }
}

class PopupTextPool(private val maxCached: Int = 24) {

    private val stack = ArrayDeque<PopupText>(maxCached)

    fun obtain(): PopupText = stack.removeLastOrNull() ?: PopupText()

    fun release(p: PopupText) {
        if (stack.size < maxCached) stack.addLast(p)
    }
}
