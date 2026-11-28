package ir.amiraligrr.blockboom

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ir.amiraligrr.blockboom.data.NewsItem
import androidx.compose.ui.unit.LayoutDirection
import ir.amiraligrr.blockboom.ui.theme.BlockBoomTheme
import ir.amiraligrr.blockboom.ui.v2.NewsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = QaTestApp::class)
class NewsScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private val sample = listOf(
        NewsItem(
            id = 2,
            title = "به نسل جدید Block Boom خوش آمدی",
            summary = "منوی تازه، حالت بی‌نهایت پیوسته و لیدربورد.",
            body = "این اولین خبر رسمی است. بین خبرها ورق بزن و متن کامل را بخوان.",
            imageUrl = "",
            publishedAt = "2026-11-28 12:00:00",
        ),
        NewsItem(
            id = 1,
            title = "خبر دوم برای تست ورق‌زدن",
            summary = "",
            body = "صفحهٔ دوم.",
            imageUrl = "",
            publishedAt = "2026-11-27 09:00:00",
        ),
    )

    private fun render(dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BlockBoomTheme(darkMode = dark) {
                    NewsScreen(news = sample, onBack = {})
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(400)
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val activity = (compose as AndroidComposeTestRule<*, *>).activity
            val decor = activity.window.decorView.rootView
            if (decor.width > 0 && decor.height > 0) {
                bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap!!))
            }
        }
        val bmp = bitmap ?: return
        val dir = File(System.getProperty("blockboom.qa.dir") ?: "build/qa")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun lightModeShowsFirstNewsPage() {
        render(dark = false)
        compose.onNodeWithText("اخبار بازی").assertExists()
        compose.onNodeWithText(sample[0].title).assertExists()
        compose.onNodeWithText("بین خبرها ورق بزن").assertExists()
        capture("news-light")
    }

    @Test
    fun darkModeShowsFirstNewsPage() {
        render(dark = true)
        compose.onNodeWithText(sample[0].title).assertExists()
        capture("news-dark")
    }

    @Test
    fun emptyFeedSaysSoInsteadOfShowingFakeNews() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BlockBoomTheme(darkMode = false) {
                    NewsScreen(news = emptyList(), onBack = {})
                }
            }
        }
        compose.onNodeWithText("هنوز خبری منتشر نشده است").assertExists()
    }
}
