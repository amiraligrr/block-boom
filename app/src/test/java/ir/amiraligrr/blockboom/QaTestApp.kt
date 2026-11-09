package ir.amiraligrr.blockboom

import android.app.Application
import ir.amiraligrr.blockboom.data.BillingManager
import ir.amiraligrr.blockboom.data.LivesManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.data.SkinManager
import ir.amiraligrr.blockboom.data.SoundManager
import ir.amiraligrr.blockboom.utils.LocaleHelper

/**
 * The real [BlockBoomApp] with the networked half of its startup skipped.
 *
 * The screens reach for `context.applicationContext as BlockBoomApp`, so the
 * cast has to succeed and the managers it reaches through have to be the real
 * ones — the save decides whether a track is locked, whether a skin is owned,
 * how many coins there are, and that is exactly the state the audit reads.
 * Faking them would mean auditing a game state that cannot occur.
 *
 * What is dropped is everything that leaves the device: `Adivery.configure`,
 * the Myket billing binding, the telemetry heartbeat, the daily-reminder alarm,
 * and the three startup coroutines that call the API. Those are not under test,
 * their failure is external to the process, and under Robolectric they either
 * throw or wedge the main looper — which would read as "the screen is blank"
 * instead of as the real reason.
 */
class QaTestApp : BlockBoomApp() {

    override fun onCreate() {
        // Deliberately NOT calling super.onCreate(). The real one configures
        // the Adivery SDK, binds the Myket billing service, starts the telemetry
        // heartbeat and launches three API-backed coroutines. Of those, the
        // billing bind is the one that actively breaks the test: it binds a
        // service that does not exist on the emulator, and the callback fires
        // with a null `name`, which throws NPE out of the Adivery SDK's
        // ServiceConnection and takes the test down with it.
        //
        // Everything the screens actually read — the save, the lives, the
        // skins, the sound — is rebuilt here on the real SaveManager, so the
        // audit is still measuring the game as a player would see it.
        saveManager = SaveManager(this)
        saveManager.migrateSkins()
        saveManager.language = "fa"
        LocaleHelper.apply(this, "fa")
        LocaleHelper.applyToSystem(this, "fa")

        livesManager = LivesManager(saveManager)
        skinManager = SkinManager(saveManager)
        // Constructed but never `init()`ed. The screens only read state from it
        // — `coins`, `premium`, the error toast — and all of that is backed by
        // the save. `init()` binds the Myket billing service, which does not
        // exist on the test device and is the thing that was crashing the run.
        billingManager = BillingManager(this, saveManager, livesManager)
        soundManager = SoundManager(this, saveManager)

        ProgressState.sync(saveManager)
    }
}
