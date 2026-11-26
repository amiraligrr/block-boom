package ir.amiraligrr.blockboom

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ir.amiraligrr.blockboom.ui.theme.BlockBoomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the screenshot harness itself works before any screen depends on it.
 *
 * If this fails, every other UI result in the suite is meaningless — a silently
 * blank render still "passes" a contrast check.
 *
 * Capture goes through the decor view instead of `captureToImage()`.
 *
 * `captureToImage()` waits for Espresso idleness first, and a screen with an
 * infinite animation (the home background, the button shimmer) never reaches
 * it. That is exactly what produced the 2-second `ComposeTimeoutException`
 * here even on a static two-node screen: the rule's own capture path was still
 * waiting for an idle window. Drawing the decor view directly returns what is
 * actually on screen, the same approach `ScreenAuditTest` uses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = QaTestApp::class)
class HarnessSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun captureWindow(): ImageBitmap {
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val activity =
                (compose as AndroidComposeTestRule<*, *>).activity
            val decor = activity.window.decorView.rootView
            val w = decor.width
            val h = decor.height
            if (w > 0 && h > 0) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap!!))
            }
        }
        return checkNotNull(bitmap) { "decor view had no size" }.asImageBitmap()
    }

    @Test
    fun rendersTextAndCapturesRealPixels() {
        // Paused clock: the frame we draw is the one that was just laid out,
        // not whichever animation frame happens to be live when capture runs.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BlockBoomTheme(darkMode = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Text("test", color = Color.Black)
                }
            }
        }
        compose.onNodeWithText("test").assertExists()
        compose.mainClock.advanceTimeBy(100)

        val img = captureWindow()
        assertTrue("capture should have real width", img.width > 0)
        assertTrue("capture should have real height", img.height > 0)

        // Both a white background and dark text must be present. Finding them
        // proves capture returns content, not a flat buffer.
        val px = img.toPixelMap()
        var hasWhite = false
        var hasBlack = false
        val distinct = HashSet<Int>()
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val c = px[x, y]
                distinct.add(c.hashCode())
                val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
                if (lum > 0.95f) hasWhite = true
                if (lum < 0.05f) hasBlack = true
            }
        }
        assertTrue("capture returned a single flat colour", distinct.size > 1)
        assertTrue("white background missing from capture", hasWhite)
        assertTrue("black text missing from capture", hasBlack)
    }
}
