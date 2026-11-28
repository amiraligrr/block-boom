package ir.amiraligrr.blockboom

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import ir.amiraligrr.blockboom.data.LivesManager
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.ui.theme.BlockBoomTheme
import ir.amiraligrr.blockboom.ui.v2.HomeV2
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = QaTestApp::class)
class HomeV2SmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val save = SaveManager(context)
        val lives = LivesManager(save)
        compose.setContent {
            BlockBoomTheme(darkMode = dark) {
                HomeV2(
                    saveManager = save,
                    livesManager = lives,
                    onPlayMode = {},
                    onShop = {},
                    onSkins = {},
                    onMissions = {},
                    onFreeCoins = {},
                    onSettings = {},
                    onLeaderboard = {},
                    onStats = {},
                    onAchievements = {},
                    onSocial = {},
                    onMore = {},
                    onDailyReward = {},
                    onExit = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(350)
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
    fun lightModeRendersTheMenu() {
        render(dark = false)
        compose.onNodeWithText("BLOCK").assertExists()
        compose.onNodeWithText("کلاسیک").assertExists()
        compose.onNodeWithText("بی‌نهایت").assertExists()
        compose.onNodeWithText("لیدربورد").assertExists()
        compose.onNodeWithText("فروشگاه").assertExists()
        capture("homeV2-light")
    }

    @Test
    fun darkModeRendersTheMenu() {
        render(dark = true)
        compose.onNodeWithText("BLOCK").assertExists()
        compose.onNodeWithText("کلاسیک").assertExists()
        compose.onNodeWithText("خانه").assertExists()
        capture("homeV2-dark")
    }
}
