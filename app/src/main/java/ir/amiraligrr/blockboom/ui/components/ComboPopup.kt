package ir.amiraligrr.blockboom.ui.components

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import ir.amiraligrr.blockboom.domain.PopupText

/**
 * Renders the big "COMBO xN" announcements (and any other floating popup)
 * on the game canvas using a pooled native text paint.
 */
internal fun DrawScope.drawPopupText(popup: PopupText) {
    val paint = GamePaintHolder.popup
    paint.textSize = (if (popup.big) 32f else 15f) * density
    paint.color = popup.color.toArgb()
    paint.alpha = (255 * popup.alpha).coerceIn(0f, 255f).toInt()
    drawContext.canvas.nativeCanvas.drawText(popup.text, popup.x, popup.y, paint)
}

private object GamePaintHolder {
    val popup: android.graphics.Paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 3f, android.graphics.Color.argb(220, 0, 0, 0))
        }
}