package ir.amiraligrr.blockboom

import android.app.Application
import android.util.Log
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.BillingManager
import ir.amiraligrr.blockboom.data.DailyReminder
import ir.amiraligrr.blockboom.data.LeaderboardManager
import ir.amiraligrr.blockboom.data.LivesManager
import ir.amiraligrr.blockboom.data.PendingSubmissions
import ir.amiraligrr.blockboom.data.PlayerSyncManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.data.RewardService
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.data.SaveMigrations
import ir.amiraligrr.blockboom.data.SkinManager
import ir.amiraligrr.blockboom.data.SoundManager
import ir.amiraligrr.blockboom.data.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The application.
 *
 * Marked [open] so a test subclass can instantiate the real managers with the
 * real [SaveManager] while skipping the networked half of startup (ad SDK
 * binding, billing binding, telemetry heartbeat, the API-backed coroutines).
 * Those are not what the UI audit is about, they leave the process, and under
 * Robolectric they wedge the main looper — which surfaces as "the screen is
 * blank" rather than as the real reason.
 *
 * Nothing in the shipping app subclasses it; `android:name` still points here.
 */
open class BlockBoomApp : Application() {

    /**
     * `private set` is right for the shipping app — nothing may reassign these
     * after startup — but a test subclass has to be able to build them, so the
     * setters are `internal`. `internal` stays inside the module, which is the
     * whole build, and out of any consumer.
     */
    lateinit var saveManager: SaveManager
        internal set

    lateinit var livesManager: LivesManager
        internal set

    lateinit var billingManager: BillingManager
        internal set

    lateinit var skinManager: SkinManager
        internal set

    lateinit var soundManager: SoundManager
        internal set

    /**
     * Delivered server-side coin grants, for the home screen to announce.
     *
     * A purchase verified in the admin panel writes a `coin_grants` row that the
     * server holds until the device collects it. Nothing used to call
     * `claim_coins.php`, so a paid coin pack was recorded as a completed sale
     * and never arrived. [collectPendingRewards] closes that loop on launch.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Delivered server-side coin grants, for the home screen to announce.
     *
     * A purchase verified in the admin panel writes a `coin_grants` row that the
     * server holds until the device collects it. Nothing used to call
     * `claim_coins.php`, so a paid coin pack was recorded as a completed sale
     * and never arrived. [collectPendingRewards] closes that loop on launch.
     *
     * This was a `@Volatile var` that the home screen polled in a loop, which
     * cannot work: reading a plain field registers no snapshot observation, so
     * the assignment below never triggered a recomposition and the "you got
     * N coins" banner only ever appeared if something else happened to
     * recompose in the same 700ms window. It is a [StateFlow] now, and the
     * grant is *consumed* through [consumeDeliveredReward] rather than by the
     * caller writing back to a field it cannot see.
     */
    private val _deliveredRewardCoins = MutableStateFlow(0)

    /**
     * Coins refunded by [SaveMigrations] on this launch, for the home screen to
     * announce once.
     *
     * Separate from [_deliveredRewardCoins] because the two arrive through
     * different paths — one synchronously during [onCreate], the other
     * asynchronously from the server — and merging them would let one overwrite
     * the other. A player who gets both is told about both, rather than about
     * whichever was written last.
     */
    private val _balanceRefundCoins = MutableStateFlow(0)

    /** Open so [QaTestApp] can substitute a networked-free version. */
    override fun onCreate() {
        super.onCreate()

        saveManager = SaveManager(this)
        saveManager.migrateSkins()
        // Save migrations, before anything reads the save. Idempotent and stamped,
        // so this is cheap on every launch after the first — see SaveMigrations.
        // The grant is stashed rather than shown here because onCreate returns
        // before any UI exists.
        _balanceRefundCoins.value = SaveMigrations.run(saveManager).toInt()
        // Persian, unconditionally.
        //
        // This used to be `if (language.isBlank()) language = "fa"`, i.e. only a
        // default for a fresh install. Anyone who had once switched to English
        // kept "en" forever. Now that the English UI and the language switcher
        // are both gone, that stored value is a trap: the app would launch
        // against a `values-en` resource set that no longer exists, fall back
        // to the default (Persian) for every string it can, and use English
        // for the rest — with no way to change it from the UI. So an install
        // carrying "en" is corrected here instead.
        if (saveManager.language != "fa") saveManager.language = "fa"
        ir.amiraligrr.blockboom.utils.LocaleHelper.apply(this, saveManager.language)
        ir.amiraligrr.blockboom.utils.LocaleHelper.applyToSystem(this, saveManager.language)

        livesManager = LivesManager(saveManager)
        skinManager = SkinManager(saveManager)
        billingManager = BillingManager(this, saveManager, livesManager)
        soundManager = SoundManager(this, saveManager)

        ProgressState.sync(saveManager)

        // Reporting loop for the admin monitoring panel. Started here rather
        // than lazily so the presence heartbeat is already ticking by the time
        // the player reaches the first run.
        Telemetry.init(this)

        AdManager.init(this)   // also preloads rewarded + interstitial
        billingManager.init()

        // Register this install and merge any progress the server already has.
        // Off the startup path on purpose: the home screen must render even if
        // the API is unreachable, and this is also what restores a player after
        // an uninstall/reinstall (ANDROID_ID is unchanged) or a recovery code.
        PlayerSyncManager.init(this)
        PlayerSyncManager.syncNow(force = true)

        // Daily reminder: create the channel and (re)arm the alarm.
        //
        // Arming on every launch is what makes this self-healing. The alarm chain
        // re-arms itself after firing and after a reboot, but devices also clear
        // alarms for their own reasons — battery management, an app-standby list,
        // a user wiping alarms — and none of those leave a trace. Re-deriving the
        // alarm from the save on every launch means the worst case is a missed
        // reminder, never a permanently dead chain.
        runCatching {
            DailyReminder.ensureChannel(this)
            DailyReminder.sync(this, saveManager)
        }.onFailure { Log.d("BlockBoom", "daily reminder unavailable: ${it.message}") }

        collectPendingRewards()
        flushPendingScore()
        fetchAnnouncements()
    }

    /**
     * Server announcements this device has not read yet.
     *
     * `fetch_notifications.php` has existed on the server the whole time and
     * `RewardService.fetchAnnouncements` has had a typed client for it — but
     * nothing ever called either. So every announcement ever posted from the
     * admin panel went to a table that no code path ever read: maintenance
     * notices, compensation, event announcements. Invisible, which is
     * indistinguishable from never having been written.
     *
     * Fetched here, off the startup path, and marked read at the same time, so
     * a per-device receipt is written and the row is not re-shown on the next
     * launch. A broadcast announcement is delivered to every device exactly
     * once each, which is the behaviour the server's receipt table was built
     * for.
     */
    private fun fetchAnnouncements() {
        appScope.launch {
            val items = RewardService.fetchAnnouncements(this@BlockBoomApp, markRead = true)
            if (items.isNotEmpty()) {
                _announcements.value = items
            }
        }
    }

    /** Unread server announcements, newest first. */
    private val _announcements = MutableStateFlow<List<RewardService.Announcement>>(emptyList())
    val announcements: StateFlow<List<RewardService.Announcement>> = _announcements.asStateFlow()

    /**
     * Takes the pending announcements and clears them in one step.
     *
     * Same reason as [consumeDeliveredReward]: reading a field and writing back
     * is a race when more than one collector can observe it.
     */
    fun consumeAnnouncements(): List<RewardService.Announcement> {
        val pending = _announcements.value
        if (pending.isNotEmpty()) _announcements.value = emptyList()
        return pending
    }

    /**
     * Re-sends a score that never reached the server.
     *
     * Runs on the background scope at startup, off the critical path to the
     * home screen. See [PendingSubmissions] for why this exists at all.
     *
     * This used to sync the cloud account as well. There is no account any more —
     * the recovery-code / cloud-backup feature has been removed — so the leaderboard
     * submit is the only thing a run has to reach the server for.
     */
    private fun flushPendingScore() {
        appScope.launch {
            // The in-memory count starts at zero, so the persisted queue has to
            // be read in before anything can report it.
            PendingSubmissions.restoreCount(this@BlockBoomApp)
            if (PendingSubmissions.count(this@BlockBoomApp) > 0) {
                PendingSubmissions.flush(this@BlockBoomApp)
            }
        }
    }

    /**
     * Claims whatever the server is holding for this device, then announces it.
     *
     * Deliberately fired on a background scope at startup rather than blocking
     * anything: a player on a train with no signal must reach the home screen
     * instantly. If the claim fails, the grant is still unclaimed in the
     * database and the next launch tries again — `claim_coins.php` marks rows
     * claimed and pays out in one transaction precisely so this retry can never
     * double-credit.
     */
    private fun collectPendingRewards() {
        appScope.launch {
            val coins = RewardService.claimPendingCoins(this@BlockBoomApp)
            if (coins > 0) {
                saveManager.addCoins(coins.toLong())
                ProgressState.sync(saveManager)
                _deliveredRewardCoins.value = coins
            }
        }
    }

    /** The pending announcement, or 0 when there is nothing to announce. */
    val deliveredRewardCoins: StateFlow<Int> = _deliveredRewardCoins.asStateFlow()

    /**
     * Takes the pending announcement and clears it in one step.
     *
     * The previous version had the UI read the field and then assign 0 back to
     * it, which is a race: two collectors can both observe the same non-zero
     * value before either clears it, and the player is told about the grant
     * twice. Reading and clearing here makes it atomic with respect to the
     * single flow collector.
     *
     * @return the grant that was pending, or 0 if there was none.
     */
    fun consumeDeliveredReward(): Int {
        val pending = _deliveredRewardCoins.value
        if (pending != 0) _deliveredRewardCoins.value = 0
        return pending
    }

    /** The balance-change refund pending announcement, or 0. */
    val balanceRefundCoins: StateFlow<Int> = _balanceRefundCoins.asStateFlow()

    /**
     * Takes the refund announcement and clears it in one step, for the same
     * reason [consumeDeliveredReward] does — two collectors must not both see it.
     */
    fun consumeBalanceRefund(): Int {
        val pending = _balanceRefundCoins.value
        if (pending != 0) _balanceRefundCoins.value = 0
        return pending
    }

    override fun attachBaseContext(base: android.content.Context) {
        // Ensure every context is wrapped with the app locale.
        //
        // Read from prefs directly, like every other code path in the app, but
        // anything other than "fa" is normalised to "fa" here too. This runs
        // *before* onCreate, so the window, resources and configuration are
        // already built by the time BlockBoomApp corrects the stored value —
        // an install that had switched to English would otherwise be wrapped in
        // an English context for its first frame and then flip, which is a
        // visible flash.
        val tmpPrefs = base.getSharedPreferences("blockboom_prefs", android.content.Context.MODE_PRIVATE)
        val lang = tmpPrefs.getString("language", "fa")?.takeIf { it == "fa" } ?: "fa"
        val wrapped = ir.amiraligrr.blockboom.utils.LocaleHelper.wrap(base, lang)
        super.attachBaseContext(wrapped)
    }

    override fun onTerminate() {
        // Push whatever the last run buffered before the process goes away.
        // Best-effort: Telemetry drops on failure by design and never blocks.
        Telemetry.flush("terminate")
        AdManager.release()
        soundManager.release()
        super.onTerminate()
    }
}