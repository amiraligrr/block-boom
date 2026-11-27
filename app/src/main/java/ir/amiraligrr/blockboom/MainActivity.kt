package ir.amiraligrr.blockboom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import ir.amiraligrr.blockboom.ui.navigation.AppNavigation
import ir.amiraligrr.blockboom.ui.theme.BlockBoomTheme
import ir.amiraligrr.blockboom.data.GateStatus
import ir.amiraligrr.blockboom.data.OnlineGate
import ir.amiraligrr.blockboom.ui.v2.OnlineGateScreen
import ir.amiraligrr.blockboom.ui.v2.intro.CinematicIntro
import ir.amiraligrr.blockboom.ui.v2.intro.SessionIntro
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences("blockboom_prefs", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "fa") ?: "fa"
        val wrapped = ir.amiraligrr.blockboom.utils.LocaleHelper.wrap(newBase, lang)
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cafe Bazaar's billing client is ActivityResult based. Myket's
        // implementation ignores this call; both flavors expose the same method.
        (application as BlockBoomApp).billingManager.attachActivityResultRegistry(activityResultRegistry)
        enableEdgeToEdge()
        setContent {
            // Read through the app so the preference survives process death,
            // and keep the Read so that toggling it in Settings repaints
            // without restarting the activity.
            val app = applicationContext as ir.amiraligrr.blockboom.BlockBoomApp
            val themeMode = app.saveManager.themeMode
            val systemDark = isSystemInDarkTheme()
            val darkMode = when (themeMode) {
                ir.amiraligrr.blockboom.data.ThemeMode.AUTO -> systemDark
                ir.amiraligrr.blockboom.data.ThemeMode.LIGHT -> false
                ir.amiraligrr.blockboom.data.ThemeMode.DARK -> true
            }
            BlockBoomTheme(darkMode = darkMode) {
                // The app is Persian-only — Settings says so and there is no
                // English string set. Compose was taking its layout direction
                // from whatever the *device* locale happened to be, so on a
                // phone set to English every row, header and button laid out
                // left-to-right while the words inside them read right-to-left:
                // back buttons on the wrong side, misaligned headers, chevrons
                // pointing the wrong way. A Persian UI is an RTL UI regardless
                // of the phone's language setting, so it is pinned here rather
                // than inferred.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // The intro covers a fully composed home, so the hand-off
                    // is a reveal, not a screen load. `SessionIntro.started`
                    // is process-scoped: a cold launch shows the sequence,
                    // while rotation or returning from a run does not.
                    var showIntro by remember { mutableStateOf(!SessionIntro.started) }
                    var gateStatus by remember { mutableStateOf(GateStatus.CHECKING) }
                    val gateScope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        gateStatus = OnlineGate.check(applicationContext)
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (gateStatus == GateStatus.ONLINE) {
                            AppNavigation()
                        } else {
                            OnlineGateScreen(
                                status = gateStatus,
                                onRetry = {
                                    gateScope.launch {
                                        gateStatus = GateStatus.CHECKING
                                        gateStatus = OnlineGate.check(applicationContext)
                                    }
                                }
                            )
                        }
                        AnimatedVisibility(
                            visible = showIntro,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(420))
                        ) {
                            CinematicIntro(onFinished = { showIntro = false })
                        }
                    }
                }
            }
        }
    }
}