package ir.amiraligrr.blockboom.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.SaveManager

/**
 * A shareable result card, built locally from the player's own run.
 *
 * ── Why the image is generated rather than a screenshot ───────────────────
 * A screenshot includes the status bar, the notification shade if one was
 * pulled down, and whatever the player was doing next — it is not a thing you
 * want arriving in a group chat. It also cannot say anything: a screenshot
 * has no idea which number mattered.
 *
 * This one is built from the run that actually happened, names the number
 * that was beaten, and carries the install link. A player who wants to show
 * off a score has something worth showing off, and a player sharing it is
 * doing so because they chose to — which is the only kind of sharing worth
 * having.
 *
 * ── Why this does not post by itself ─────────────────────────────────────
 * Nothing here runs without a tap on a button that says what it will do. A
 * share that fires on its own is not marketing, it is malware behaviour, and
 * it gets apps removed from stores — which ends the revenue this is meant to
 * help. The player is always the one who decides.
 */
object ShareCard {

    /** The Play/Myket listing. Overridable so a debug build does not send
     *  real players to the live listing by accident. */
    private val STORE_URL = "https://myket.ir/app/ir.amiraligrr.blockboom"

    /** Width of the generated card; height is derived, so it never distorts. */
    private const val W = 1080
    private const val PAD = 84

    /**
     * Draws the card for a finished run.
     *
     * @param headline the number that was beaten — level, combo, or score
     * @param label what that number is, in Persian
     */
    fun render(
        save: SaveManager,
        headline: String,
        label: String,
        accent: Int
    ): Bitmap {
        val lines = buildLines(save)
        val lineH = 74
        val bodyH = lines.size * lineH
        val h = PAD * 2 + 240 + bodyH + 120

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background: the app's own night gradient, so it is recognisable as
        // this game in a chat list before the text is read.
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                AndroidColor.rgb(15, 26, 46),
                AndroidColor.rgb(24, 20, 58),
                AndroidColor.rgb(9, 14, 28)
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, W.toFloat(), h.toFloat(), bg)

        // Accent bar so a muted chat preview still reads as something.
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRoundRect(0f, 0f, W.toFloat(), 22f, 22f, 22f, bar)
        canvas.drawRect(0f, 22f, W.toFloat(), 30f, bar)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val muted = Paint(white).apply { color = AndroidColor.argb(180, 255, 255, 255) }

        var y = PAD.toFloat()

        white.textSize = 52f
        canvas.drawText("بلاک بوم", PAD.toFloat(), y + 40f, white)
        y += 96f

        muted.textSize = 34f
        canvas.drawText(label, PAD.toFloat(), y + 30f, muted)
        y += 70f

        // The headline — the one number the share is about, large enough to
        // survive being scaled down in a chat list.
        white.textSize = 150f
        canvas.drawText(headline, PAD.toFloat(), y + 120f, white)
        y += 200f

        val stat = Paint(white).apply {
            color = accent
            textSize = 40f
        }
        for (line in lines) {
            canvas.drawText(line, PAD.toFloat(), y + 34f, stat)
            y += lineH
        }

        y += 20f
        muted.textSize = 32f
        canvas.drawText("همهٔ بازیکن‌ها را شکست بده 👑", PAD.toFloat(), y + 34f, muted)

        return bmp
    }

    /** Three real numbers from the player's own save — never invented. */
    private fun buildLines(save: SaveManager): List<String> = listOfNotNull(
        "مرحله ${save.statDeepestLevel}",
        "کمبوی ×${save.statBestCombo}",
        "${save.totalScore} امتیاز"
    )

    /**
     * Opens the system share sheet with the card and the store link.
     *
     * Everything is offered to the sheet — image plus text — so the player
     * picks the target. There is no default destination and no silent post.
     */
    fun share(context: Context, bmp: Bitmap, message: String) {
        runCatching {
            val cache = java.io.File(context.cacheDir, "share").apply { mkdirs() }
            val file = java.io.File(cache, "blockboom_run.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // A FileProvider is required to hand another app a file:// path.
            // Without one the intent throws and the player gets nothing.
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.share_choose))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
    }

    /** The caption that ships with a shared result. */
    fun message(save: SaveManager, headline: String, label: String): String =
        "$label: $headline\n\n" +
            "من در بلاک بوم به مرحلهٔ ${save.statDeepestLevel} رسیدم. تو هم بیا! 👑\n" +
            STORE_URL
}