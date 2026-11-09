package ir.amiraligrr.blockboom.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Which palette the player wants.
 *
 * [AUTO] follows the system setting, which is the default so the app behaves
 * the way the rest of their phone does on first launch.
 */
enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * Central persistence layer backed by SharedPreferences.
 * Game state is written here (with debounce in the game engine)
 * whenever it meaningfully changes.
 */

/**
 * Joyful color palette for light mode
 */
object JoyColors {
    // Light mode colors
    val Light = JoyColorsImpl(
        backgroundTop = Color(0xFFECFDF5),
        backgroundMid = Color(0xFFA7F3D0),
        backgroundBottom = Color(0xFF86EFAC),
        surface = Color.White,
        surfaceVariant = Color(0xFFF3F4F6),
        cardBackground = Color.White,
        textPrimary = Color(0xFF143D2E),
        textSecondary = Color(0xFF1A5C3F),
        textMuted = Color(0xFF6B7280),
        cardBorder = Color(0xFFD1FAE5),
        cardShadow = Color(0xFF10B981).copy(alpha = 0.15f),
        primary = Color(0xFF10B981),
        primaryVariant = Color(0xFF059669),
        secondary = Color(0xFF0EA5E9),
        accent = Color(0xFFF59E0B),
        error = Color(0xFFEF4444),
        onPrimary = Color.White,
        onSurface = Color(0xFF143D2E),
        divider = Color(0xFFD1FAE5),
        navigationBar = Color(0xFFECFDF5),
        statusBar = Color(0xFFECFDF5)
    )
}

data class JoyColorsImpl(
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val cardBorder: Color,
    val cardShadow: Color,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val accent: Color,
    val error: Color,
    val onPrimary: Color,
    val onSurface: Color,
    val divider: Color,
    val navigationBar: Color,
    val statusBar: Color
)

class SaveManager(context: Context) {

    /**
     * The application context, for the few things that legitimately need to
     * touch prefs from outside — currently [SaveMigrations], which has to read
     * its own schema stamp from the same file it is migrating.
     *
     * Application-scoped on purpose, so holding it cannot leak an activity.
     */
    val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentLevel: Int
        get() = prefs.getInt(KEY_LEVEL, 1)
        set(value) = prefs.edit().putInt(KEY_LEVEL, value).apply()

    /** Advances the stored level by one, clamped to at least 1. */
    fun nextLevel() {
        currentLevel = (currentLevel + 1).coerceAtLeast(1)
    }

    var coins: Long
        get() = prefs.getLong(KEY_COINS, 0L)
        set(value) = prefs.edit().putLong(KEY_COINS, value).apply()

    /** Adds [amount] to the stored coin balance. */
    fun addCoins(amount: Long) {
        coins = (coins + amount).coerceAtLeast(0L)
    }

    /**
     * Spends [amount] coins, or returns false when the balance is short.
     *
     * The balance is never touched on failure, so a caller can offer-then-
     * charge without a separate pre-check that could race a second spender.
     */
    fun spendCoins(amount: Long): Boolean {
        if (amount <= 0) return true
        if (coins < amount) return false
        coins = coins - amount
        return true
    }

    /**
     * Hearts remaining.
     *
     * The bound is [LivesManager.MAX_LIVES], and it used to be a literal 15 here
     * while `LivesManager` had already been changed to 25 and documented *why* —
     * that with one heart per level and no mercy reprieve, 15 meant the first bad
     * level of a session decided whether the player played another ten minutes.
     *
     * Because the setter clamped on write, that raise could never take effect:
     * every grant, every regeneration and every ad reward was truncated back to
     * 15 the moment it was stored, so the player could never actually hold more
     * than fifteen hearts no matter how long they waited. A documented number the
     * persistence layer silently refuses is worse than no documentation.
     *
     * Reads default to a full bar, matching what `LivesManager` seeds a fresh
     * install with. Existing saves below the new cap are untouched and simply
     * regenerate upward.
     */
    var lives: Int
        get() = prefs.getInt(KEY_LIVES_REMAINING, LivesManager.MAX_LIVES)
        set(value) = prefs.edit()
            .putInt(KEY_LIVES_REMAINING, value.coerceIn(0, LivesManager.MAX_LIVES))
            .apply()

    var lastLifeReset: Long
        get() = prefs.getLong(KEY_LAST_RESET_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RESET_TIMESTAMP, value).apply()

    var lastElapsedRealtime: Long
        get() = prefs.getLong(KEY_LAST_ELAPSED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ELAPSED, value).apply()

    var lastWallCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_WALL_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_WALL_CHECK, value).apply()

    var musicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MUSIC_ENABLED, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "fa") ?: "fa"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /**
     * Light / dark / follow-the-system.
     *
     * Snapshot state, not a bare prefs read, so flipping the switch repaints
     * the app immediately. The app used to call `BlockBoomTheme()` with no
     * argument, which meant "follow the system" — and then ignored that
     * anyway, because the background canvas and most cards painted their
     * light colours unconditionally. A player on system dark mode saw a
     * white screen at 2am.
     */
    private var storedThemeMode by mutableStateOf(ThemeMode.AUTO)

    var themeMode: ThemeMode
        get() = storedThemeMode
        set(value) {
            if (storedThemeMode == value) return
            storedThemeMode = value
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    var tutorialSeen: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, value).apply()

    // ── The rating prompt ────────────────────────────────────────────────
    //
    // Asking for a rating is asking a working player to leave the app, go to the
    // store, log in, and tap stars — for nothing they will see. Done badly it
    // costs a session and buys nothing, which is why it is gated three ways
    // rather than fired on the first launch:
    //
    //  1. **Only once the player is engaged.** Not on a fresh install, where
    //     they have no opinion yet. The bar is a finished run plus a level
    //     reached, so the person being asked has actually played.
    //  2. **Never twice for nothing.** "Later" re-arms after a gap; "no thanks"
    //     never does again. A prompt that ignores its own dismissals is a prompt
    //     the player learns to dismiss without reading.
    //  3. **Only when there is somewhere to go.** The store link comes from the
    //     server, so the prompt is suppressed entirely while it is blank.

    /** How many finished runs qualify the player to be asked. */
    val RATE_MIN_RUNS = 2

    /** The level a player must have reached before they are worth asking. */
    val RATE_MIN_LEVEL = 5

    /** Days after "later" before the prompt may appear again. */
    val RATE_COOLDOWN_DAYS = 21L

    /**
     * True once the player has said they do not want to be asked, ever.
     *
     * Deliberately separate from "asked": the difference between a player who
     * deferred and a player who declined is the difference between a cooldown
     * and a permanent no.
     */
    var rateDeclined: Boolean
        get() = prefs.getBoolean(KEY_RATE_DECLINED, false)
        set(value) = prefs.edit().putBoolean(KEY_RATE_DECLINED, value).apply()

    /** Wall-clock of the last time the prompt was shown, or 0 if never. */
    var rateLastAskedAt: Long
        get() = prefs.getLong(KEY_RATE_LAST_ASKED, 0L)
        set(value) = prefs.edit().putLong(KEY_RATE_LAST_ASKED, value).apply()

    /** True once the player has been sent to the store by the prompt. */
    var rateLeftForStore: Boolean
        get() = prefs.getBoolean(KEY_RATE_WENT_TO_STORE, false)
        set(value) = prefs.edit().putBoolean(KEY_RATE_WENT_TO_STORE, value).apply()

    /**
     * Whether the rating prompt may be shown now.
     *
     * Computed rather than stored so it can never disagree with the counters it
     * reads. A stored "should ask" flag is a flag that has to be invalidated by
     * every path that changes an input, and there are three.
     */
    fun shouldPromptForRating(now: Long = System.currentTimeMillis()): Boolean {
        if (rateDeclined) return false
        if (statRuns < RATE_MIN_RUNS) return false
        if (currentLevel < RATE_MIN_LEVEL) return false
        if (rateLeftForStore) return false
        val last = rateLastAskedAt
        if (last == 0L) return true
        return now - last >= RATE_COOLDOWN_DAYS * 24L * 60L * 60L * 1000L
    }

    /**
     * Whether the in-game coach has been completed or dismissed.
     *
     * Distinct from [tutorialSeen] on purpose. `tutorialSeen` is set the moment
     * the player taps Play on the home screen, so it only ever recorded "saw
     * the bouncing arrow on a card" — a thing that says nothing about whether
     * they can play. Anything keyed off it, including the coach, therefore
     * could never run: the flag was always already true by the time the first
     * level was on screen.
     */
    var coachDone: Boolean
        get() = prefs.getBoolean(KEY_COACH_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_COACH_DONE, value).apply()

    /**
     * The revision of the in-game coach this player has actually seen.
     *
     * ── Why a version, not the boolean ─────────────────────────────────────
     * [coachDone] is a one-bit flag, which makes "has this player seen the
     * coach" and "has this player seen *this* coach" the same question. So the
     * moment the coach gained a step — the warning that a block reaching you
     * costs a heart, which is the rule that ends runs and was previously never
     * stated anywhere — every existing player had already flipped that bit to
     * true and would never be shown the new step.
     *
     * An int makes the question answerable. [coachSeenVersion] is stored when
     * the coach completes or is dismissed, and the screen shows the coach
     * while that is below [COACH_VERSION]. Bump [COACH_VERSION] whenever the
     * steps change; existing players get the new revision exactly once, and
     * never see an unchanged coach twice.
     *
     * [coachDone] stays as the "has this player been coached at all" flag for
     * the home screen's "how to play" label.
     */
    var coachSeenVersion: Int
        get() = prefs.getInt(KEY_COACH_SEEN_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_COACH_SEEN_VERSION, value).apply()

    /** True when the current coach revision still needs to be shown. */
    fun needsCoach(): Boolean = coachSeenVersion < COACH_VERSION

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var lastDailyRewardClaim: Long
        get() = prefs.getLong(KEY_LAST_DAILY_REWARD, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DAILY_REWARD, value).apply()

    var dailyStreak: Int
        get() = prefs.getInt(KEY_DAILY_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_STREAK, value.coerceAtLeast(0)).apply()

    /** Checks if daily reward is available (24h since last claim). */
    fun isDailyRewardAvailable(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastDailyRewardClaim >= 24 * 60 * 60 * 1000L || lastDailyRewardClaim == 0L
    }

    /**
     * How many rewarded-ad free upgrades have already been claimed today.
     *
     * This has to be persisted and day-stamped, not held in a
     * `remember { mutableIntStateOf(0) }` inside UpgradeScreen. That version
     * reset on every screen disposal, rotation and process restart, so
     * "2 free upgrades per day" was really "2 per visit": rotate the screen
     * twice and the cap was gone. Since the grant skips the coin check
     * entirely, that made every paid coin pack strictly worse value than an
     * ad, which is precisely backwards.
     */
    var freeUpgradesUsedToday: Int
        get() {
            val today = todayStamp()
            return if (prefs.getLong(KEY_FREE_UPGRADE_DAY, 0L) == today) {
                prefs.getInt(KEY_FREE_UPGRADE_USED, 0)
            } else {
                0
            }
        }
        set(value) {
            prefs.edit()
                .putLong(KEY_FREE_UPGRADE_DAY, todayStamp())
                .putInt(KEY_FREE_UPGRADE_USED, value.coerceAtLeast(0))
                .apply()
        }

    /** Records one rewarded-ad upgrade and returns how many are left today. */
    fun consumeFreeUpgrade(): Int {
        val used = freeUpgradesUsedToday + 1
        freeUpgradesUsedToday = used
        return (FREE_UPGRADES_PER_DAY - used).coerceAtLeast(0)
    }

    fun freeUpgradesLeftToday(): Int =
        (AdRewards.FREE_UPGRADES_PER_DAY - freeUpgradesUsedToday).coerceAtLeast(0)

    // ── Rewarded-ad coin watches ────────────────────────────────────────
    //
    // The "watch for coins" tap is paid for exactly like the free upgrade is,
    // and for exactly the same reason: a cap held in a composable's `remember`
    // is a cap that survives a rotation, so "15 a day" becomes "15 a visit" and
    // then fifteen again on the next screen. Coins are the currency the whole
    // shop is priced in, so this one is not merely a politeness bound — it is
    // the difference between an ad being worth watching and an ad replacing the
    // store.
    //
    // Same local-calendar-day stamp as the upgrade counter, for the same reason:
    // "per day" has to mean the same thing to the player and to the economy.

    private var adWatchesUsedToday: Int
        get() {
            val today = todayStamp()
            return if (prefs.getLong(KEY_AD_WATCH_DAY, 0L) == today) {
                prefs.getInt(KEY_AD_WATCH_USED, 0)
            } else {
                0
            }
        }
        set(value) {
            prefs.edit()
                .putLong(KEY_AD_WATCH_DAY, todayStamp())
                .putInt(KEY_AD_WATCH_USED, value.coerceAtLeast(0))
                .apply()
        }

    /** How many rewarded coin watches are still available today. */
    fun adWatchesLeftToday(): Int =
        (AdRewards.MAX_ADS_PER_DAY - adWatchesUsedToday).coerceAtLeast(0)

    /**
     * Records one rewarded coin watch and returns how many are left today, or
     * -1 when the day's cap is already spent.
     *
     * Increments *before* the ad is shown rather than after it is earned, on
     * purpose. A reward that is credited on completion and counted on request
     * lets a player who closes the ad early retry indefinitely, and the ad
     * network sees the abandoned impressions.
     */
    fun consumeAdWatch(): Int =
        if (adWatchesUsedToday >= AdRewards.MAX_ADS_PER_DAY) -1
        else (AdRewards.MAX_ADS_PER_DAY - ++adWatchesUsedToday)

    /**
     * Local-day stamp. Uses the device's current calendar day rather than a
     * rolling 24h window so "per day" means the same thing to the player and to
     * the economy: the cap lifts at their midnight, not 24h after they first
     * happened to open the screen.
     */
    private fun todayStamp(): Long =
        java.util.Calendar.getInstance().let {
            it.set(java.util.Calendar.HOUR_OF_DAY, 0)
            it.set(java.util.Calendar.MINUTE, 0)
            it.set(java.util.Calendar.SECOND, 0)
            it.set(java.util.Calendar.MILLISECOND, 0)
            it.timeInMillis
        }

    /** Claims daily reward, updates streak, returns reward amount. */
    fun claimDailyReward(): Long {
        val now = System.currentTimeMillis()
        val isConsecutive = now - lastDailyRewardClaim < 48 * 60 * 60 * 1000L && lastDailyRewardClaim != 0L
        dailyStreak = if (isConsecutive) (dailyStreak + 1).coerceAtMost(7) else 1
        lastDailyRewardClaim = now
        val reward = dailyRewardForStreak(dailyStreak)
        addCoins(reward)
        return reward
    }

    /**
     * The daily reward for a given streak length — the single source of truth.
     *
     * This used to be an inline `(40 * streak).coerceAtMost(200)` in
     * claimDailyReward(), while DailyRewardDialog displayed a completely
     * different formula: `100 * (streak + 1)` capped at 7, i.e. up to 700. So
     * the dialog promised up to 700 coins and the game paid at most 200, and
     * the "2× by watching an ad" path doubled the *displayed* figure rather
     * than the actual one. Both paths now call this, so what the dialog shows
     * and what the balance receives cannot drift again.
     *
     * Day 1 pays 40. It is intentionally less than a single coin pack's daily
     * equivalent: the reward should buy the occasional upgrade, not compete
     * with the paid SKUs.
     */
    fun dailyRewardForStreak(streak: Int): Long =
        (DAILY_REWARD_BASE * streak.coerceIn(1, DAILY_REWARD_MAX_STREAK))
            .coerceAtMost(DAILY_REWARD_MAX)

    var selectedBallSkin: String
        get() = prefs.getString(KEY_BALL_SKIN, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_BALL_SKIN, value).apply()

    var selectedCannonSkin: String
        get() = prefs.getString(KEY_CANNON_SKIN, "cannon_default") ?: "cannon_default"
        set(value) = prefs.edit().putString(KEY_CANNON_SKIN, value).apply()

    var selectedBackgroundSkin: String
        get() = prefs.getString(KEY_BACKGROUND_SKIN, "bg_default") ?: "bg_default"
        set(value) = prefs.edit().putString(KEY_BACKGROUND_SKIN, value).apply()

    var ownedBallSkins: String
        get() = prefs.getString(KEY_OWNED_BALL_SKINS, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_OWNED_BALL_SKINS, value).apply()

    var ownedCannonSkins: String
        get() = prefs.getString(KEY_OWNED_CANNON_SKINS, "cannon_default") ?: "cannon_default"
        set(value) = prefs.edit().putString(KEY_OWNED_CANNON_SKINS, value).apply()

    var ownedBackgroundSkins: String
        get() = prefs.getString(KEY_OWNED_BACKGROUND_SKINS, "bg_default") ?: "bg_default"
        set(value) = prefs.edit().putString(KEY_OWNED_BACKGROUND_SKINS, value).apply()

    /** Migration: fix old "default" ids for cannon/bg to new ids */
    fun migrateSkins() {
        if (selectedCannonSkin == "default") selectedCannonSkin = "cannon_default"
        if (selectedBackgroundSkin == "default") selectedBackgroundSkin = "bg_default"
        // These three are comma-separated *sets*, not single values, so the
        // rename has to happen per entry.
        //
        // Renaming on a whole-value match ("default" == the entire string) only
        // ever fired for a player who owned nothing else, which left the legacy
        // id sitting in the middle of everyone else's inventory. Ownership still
        // worked, because `isCannonOwned`/`isBackgroundOwned` accept the legacy id
        // as a fallback for the new default — but the stored value stayed wrong,
        // and the two spellings of the same skin were free to drift apart.
        ownedCannonSkins = renameSkinInInventory(ownedCannonSkins, "default", "cannon_default")
        ownedBackgroundSkins = renameSkinInInventory(ownedBackgroundSkins, "default", "bg_default")

        // Ensure ball default present.
        //
        // This used to *assign* `"default"` when the inventory did not already
        // contain it, which replaced the whole comma-separated value: any save
        // whose ball inventory lacked that literal — a pre-migration install, or
        // one written by a build that never seeded it — lost every purchased ball
        // skin on the next launch. Adding the missing entry is what was meant.
        ownedBallSkins = withSkinInInventory(ownedBallSkins, "default")
    }

    private fun renameSkinInInventory(value: String, from: String, to: String): String =
        value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            .map { if (it == from) to else it }
            .distinct()
            .joinToString(",")

    private fun withSkinInInventory(value: String, id: String): String =
        (value.split(",").map { it.trim() }.filter { it.isNotBlank() } + id)
            .distinct()
            .joinToString(",")

    var upgradeDamage: Int
        get() = prefs.getInt(KEY_UPGRADE_DAMAGE, 1)
        set(value) = prefs.edit().putInt(KEY_UPGRADE_DAMAGE, value).apply()

    var upgradeFireRate: Int
        get() = prefs.getInt(KEY_UPGRADE_FIRE_RATE, 2)
        set(value) = prefs.edit().putInt(KEY_UPGRADE_FIRE_RATE, value).apply()

    var upgradeMultiBall: Int
        get() = prefs.getInt(KEY_UPGRADE_MULTI_BALL, 1)
        set(value) = prefs.edit().putInt(KEY_UPGRADE_MULTI_BALL, value).apply()

    var upgradePierce: Int
        get() = prefs.getInt(KEY_UPGRADE_PIERCE, 0)
        set(value) = prefs.edit().putInt(KEY_UPGRADE_PIERCE, value).apply()

    var upgradeMagnet: Int
        get() = prefs.getInt(KEY_UPGRADE_MAGNET, 0)
        set(value) = prefs.edit().putInt(KEY_UPGRADE_MAGNET, value).apply()

    var upgradeBulletSpeed: Int
        get() = prefs.getInt(KEY_UPGRADE_BULLET_SPEED, 8)
        set(value) = prefs.edit().putInt(KEY_UPGRADE_BULLET_SPEED, value).apply()

    var playerName: String
        get() = prefs.getString(KEY_PLAYER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAYER_NAME, value.trim().take(16)).apply()

    /** Lifetime score accumulated across all runs — used for the leaderboard. */
    var totalScore: Long
        get() = prefs.getLong(KEY_TOTAL_SCORE, 0L)
        set(value) = prefs.edit().putLong(KEY_TOTAL_SCORE, value.coerceAtLeast(0L)).apply()

    // ── Cloud identity ────────────────────────────────────────────────────
    // The server has supported accounts for a while, but the client never
    // used them: a reinstall came back to a blank game. These fields make
    // identity and recovery real on the device.

    /**
     * Stable per-install identifier.
     *
     * ANDROID_ID survives a reinstall on the same device, but not a factory
     * reset or a new phone. This id lets the server tell "same device, new
     * install" from "same device, same install" and count reinstalls without
     * guessing. It is generated once and never derived from hardware.
     */
    var installId: String
        get() {
            val existing = prefs.getString(KEY_INSTALL_ID, "") ?: ""
            if (existing.isNotBlank()) return existing
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, fresh).apply()
            return fresh
        }
        set(value) = prefs.edit().putString(KEY_INSTALL_ID, value.take(64)).apply()

    /** Server account id, or 0 before the first successful register. */
    var accountId: Int
        get() = prefs.getInt(KEY_ACCOUNT_ID, 0)
        set(value) = prefs.edit().putInt(KEY_ACCOUNT_ID, value.coerceAtLeast(0)).apply()

    /**
     * The recovery code, kept on this device only.
     *
     * The server stores a hash and genuinely cannot return it, so this is the
     * only copy. Settings shows it and lets the player copy it; losing it
     * means a factory reset cannot restore the account.
     */
    var accountRecoveryCode: String
        get() = prefs.getString(KEY_ACCOUNT_RECOVERY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCOUNT_RECOVERY, value.trim().take(16)).apply()

    /** Last successful cloud sync, in wall-clock milliseconds. 0 = never. */
    var lastCloudSyncAt: Long
        get() = prefs.getLong(KEY_LAST_CLOUD_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CLOUD_SYNC, value.coerceAtLeast(0L)).apply()

    /** True once this install has a server account. */
    var cloudRegistered: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_REGISTERED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_REGISTERED, value).apply()

    /** True while a sync is in flight, for the settings card. */
    var cloudSyncing: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_SYNCING, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_SYNCING, value).apply()

    // ── Today's activity ─────────────────────────────────────────────────
    // A daily snapshot is written even for players who never reach the
    // leaderboard. These counters are cumulative for the current local day
    // and are persisted, so a process death does not lose them. The server
    // upserts with GREATEST(), which makes a retry idempotent.

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureDailyBucket() {
        val today = todayKey()
        if (prefs.getString(KEY_DAILY_DAY, "") == today) return
        prefs.edit()
            .putString(KEY_DAILY_DAY, today)
            .putInt(KEY_DAILY_RUNS, 0)
            .putInt(KEY_DAILY_LEVELS, 0)
            .putInt(KEY_DAILY_BLOCKS, 0)
            .putInt(KEY_DAILY_SHOTS, 0)
            .putInt(KEY_DAILY_BEST_COMBO, 0)
            .putLong(KEY_DAILY_PLAY_SECONDS, 0L)
            .putLong(KEY_DAILY_COINS, 0L)
            .apply()
    }

    data class DailySnapshot(
        val day: String,
        val level: Int,
        val totalScore: Long,
        val coins: Long,
        val runs: Int,
        val levelsCleared: Int,
        val blocks: Int,
        val shots: Int,
        val bestCombo: Int,
        val playSeconds: Long,
        val coinsEarned: Long
    )

    /** A consistent snapshot of today's activity, for one server upsert. */
    fun dailySnapshot(): DailySnapshot {
        ensureDailyBucket()
        return DailySnapshot(
            day = prefs.getString(KEY_DAILY_DAY, todayKey()) ?: todayKey(),
            level = currentLevel,
            totalScore = totalScore,
            coins = coins,
            runs = prefs.getInt(KEY_DAILY_RUNS, 0),
            levelsCleared = prefs.getInt(KEY_DAILY_LEVELS, 0),
            blocks = prefs.getInt(KEY_DAILY_BLOCKS, 0),
            shots = prefs.getInt(KEY_DAILY_SHOTS, 0),
            bestCombo = prefs.getInt(KEY_DAILY_BEST_COMBO, 0),
            playSeconds = prefs.getLong(KEY_DAILY_PLAY_SECONDS, 0L),
            coinsEarned = prefs.getLong(KEY_DAILY_COINS, 0L)
        )
    }

    /**
     * Comma-separated ids of unlocked achievements.
     * Stored in the same shape as the skin inventories so there is one kind of
     * "player owns a set of things" in this file. Sorted on write — see
     * [Achievements.persist] — so the value does not depend on the order
     * achievements happen to be checked in.
     */
    var unlockedAchievements: String
        get() = prefs.getString(KEY_UNLOCKED_ACHIEVEMENTS, "") ?: ""
        set(value) = prefs.edit()
            .putString(KEY_UNLOCKED_ACHIEVEMENTS, value.trim().take(600))
            .apply()

    /** Coin multiplier from the player's subscription.
     *
     * Premium used to be purely subtractive — you lost the ads and got nothing
     * else, which is the framing that converts worst. Now it also pays double
     * coins, so the 30 days are worth paying for even if you never open a
     * rewarded ad.
     */
    fun coinMultiplier(): Float = if (isPremium) 2f else 1f

    /** Adds [amount] to [totalScore] and returns the new lifetime total. */
    fun addScore(amount: Int): Long {
        if (amount > 0) totalScore += amount
        return totalScore
    }

    // ── Lifetime stats ─────────────────────────────────────────────────────
    //
    // Everything a stats screen shows, kept locally so it works offline and
    // survives the panel being unreachable.
    //
    // These numbers were already being collected — `Telemetry` tracks runs,
    // levels, blocks, coins, shots, combos and max-combo for every install and
    // posts them to the server — but they were only ever sent somewhere the
    // player cannot see, and they were sent as per-install aggregates that vanish
    // on a factory reset. Everything below is derived from the same events, but
    // kept on the device, which is the only place a player can be shown their own
    // progress.
    //
    // The single biggest miss here is `bestCombo`: the game computed a combo
    // high-water mark every run and displayed it once, on the game-over screen,
    // then threw it away. It was the number a player is most proud of and the
    // one number a stats screen exists to remember.

    /** Runs started. */
    var statRuns: Int
        get() = prefs.getInt(KEY_STAT_RUNS, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_RUNS, value.coerceAtLeast(0)).apply()

    /** Levels cleared, across all runs. */
    var statLevelsCleared: Int
        get() = prefs.getInt(KEY_STAT_LEVELS, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_LEVELS, value.coerceAtLeast(0)).apply()

    /** Blocks destroyed, across all runs. */
    var statBlocksDestroyed: Int
        get() = prefs.getInt(KEY_STAT_BLOCKS, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_BLOCKS, value.coerceAtLeast(0)).apply()

    /** Shots fired, across all runs. */
    var statShotsFired: Int
        get() = prefs.getInt(KEY_STAT_SHOTS, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_SHOTS, value.coerceAtLeast(0)).apply()

    /** Highest combo ever reached. Never decreases. */
    var statBestCombo: Int
        get() = prefs.getInt(KEY_STAT_BEST_COMBO, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_BEST_COMBO, value.coerceAtLeast(0)).apply()

    /** Coins earned from play, across all runs. Excludes purchases and rewards. */
    var statCoinsEarned: Long
        get() = prefs.getLong(KEY_STAT_COINS, 0L)
        private set(value) = prefs.edit().putLong(KEY_STAT_COINS, value.coerceAtLeast(0L)).apply()

    /** Highest score in a single run. Never decreases. */
    var statBestRunScore: Int
        get() = prefs.getInt(KEY_STAT_BEST_RUN, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_BEST_RUN, value.coerceAtLeast(0)).apply()

    /** Deepest level ever reached. Never decreases — losing does not reset it. */
    var statDeepestLevel: Int
        get() = prefs.getInt(KEY_STAT_DEEPEST, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_DEEPEST, value.coerceAtLeast(0)).apply()

    /** Seconds actually spent playing, across all runs. */
    var statPlaySeconds: Long
        get() = prefs.getLong(KEY_STAT_PLAY_SECONDS, 0L)
        private set(value) = prefs.edit().putLong(KEY_STAT_PLAY_SECONDS, value.coerceAtLeast(0L)).apply()

    /**
     * Folds one finished run into the lifetime totals.
     *
     * One call per run, from the run-end path, and it is additive with
     * high-water marks rather than overwrites — a player must never see a
     * lifetime figure go down.
     *
     * Counters that cannot decrease are guarded, which matters because a run can
     * legitimately report a smaller number than an earlier one (an easy level
     * after a hard one) and writing those back would erase a record.
     */
    fun recordRun(
        blocks: Int,
        shots: Int,
        coins: Int,
        levelsCleared: Int,
        bestCombo: Int,
        score: Int,
        deepestLevel: Int,
        playSeconds: Float
    ) {
        if (blocks > 0) statBlocksDestroyed += blocks
        if (shots > 0) statShotsFired += shots
        if (coins > 0) statCoinsEarned += coins.toLong()
        if (levelsCleared > 0) statLevelsCleared += levelsCleared
        // Truncate to whole seconds, explicitly. This read `(x * 1000).toLong() / 1000`,
        // which multiplies by a thousand and divides straight back: it is exactly
        // `floor(x)`, so every run threw away its sub-second remainder while
        // looking like it was preserving milliseconds.
        if (playSeconds > 0f) statPlaySeconds += playSeconds.toLong()
        statRuns += 1
        if (bestCombo > statBestCombo) statBestCombo = bestCombo
        if (score > statBestRunScore) statBestRunScore = score
        if (deepestLevel > statDeepestLevel) statDeepestLevel = deepestLevel

        // Today's bucket. These are the rows the admin players page reads,
        // and they exist for every player — not only the handful who submit a
        // leaderboard score.
        ensureDailyBucket()
        prefs.edit()
            .putInt(KEY_DAILY_RUNS, prefs.getInt(KEY_DAILY_RUNS, 0) + 1)
            .putInt(KEY_DAILY_LEVELS, prefs.getInt(KEY_DAILY_LEVELS, 0) + levelsCleared.coerceAtLeast(0))
            .putInt(KEY_DAILY_BLOCKS, prefs.getInt(KEY_DAILY_BLOCKS, 0) + blocks.coerceAtLeast(0))
            .putInt(KEY_DAILY_SHOTS, prefs.getInt(KEY_DAILY_SHOTS, 0) + shots.coerceAtLeast(0))
            .putInt(KEY_DAILY_BEST_COMBO, maxOf(prefs.getInt(KEY_DAILY_BEST_COMBO, 0), bestCombo))
            .putLong(KEY_DAILY_PLAY_SECONDS, prefs.getLong(KEY_DAILY_PLAY_SECONDS, 0L) + playSeconds.toLong().coerceAtLeast(0L))
            .putLong(KEY_DAILY_COINS, prefs.getLong(KEY_DAILY_COINS, 0L) + coins.coerceAtLeast(0).toLong())
            .apply()
    }

    /** Blocks destroyed per shot, or 0 before the player has done anything. */
    fun statAccuracy(): Float =
        if (statShotsFired <= 0) 0f
        else (statBlocksDestroyed.toFloat() / statShotsFired).coerceAtMost(9.99f)

    /**
 * Whether the player wants the daily reminder.
 *
 * Defaults on, because the feature exists to protect a streak and the streak
 * is the thing that would otherwise be lost silently. It is still a toggle, and
 * the Settings row is written to be findable — a reminder the player cannot turn
 * off is how the OS permission below gets revoked instead.
 *
 * This is the app's own preference. Whether the OS will actually deliver is a
 * separate question answered by [DailyReminder.canPost]; treating the two as one
 * is what produces reminders that are switched on and never arrive.
 */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    /**
     * Whether the runtime permission prompt has been shown, or has been exhausted.
     *
     * Two things are folded into one flag because from the app's point of view
     * they are the same decision: "have I put a permission dialog in front of this
     * player". Asking twice is the single most reliable way to get the permission
     * permanently revoked, and on API 33+ the system stops showing the dialog
     * after two denials anyway — so a flag that keeps offering it is a flag that
     * looks broken.
     *
     * Set on showing, not on answering, so that dismissing the dialog counts.
     */
    var notifPromptShown: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_PROMPT_SHOWN, value).apply()

    /**
     * When the last daily reminder was actually delivered.
     *
     * The anchor for tomorrow's. It used to be derived from
     * [lastDailyRewardClaim], which made the reminder a follower of the reward's
     * window: a player who had not claimed — the one most worth bringing back —
     * got no reminder at all. A reminder with its own anchor is a reminder that
     * exists for players who are not already opening the app.
     */
    var lastReminderFiredAt: Long
        get() = prefs.getLong(KEY_LAST_REMINDER, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REMINDER, value).apply()

    /**
     * Whether this player has premium right now.
     *
     * Two independent sources, OR-ed together, because they have different
     * owners and different rules:
     *
     *  - [premiumUntilMillis] — bought and verified through Myket. Written by
     *    BillingManager, extended by grantPremium(), never touched by the server.
     *  - [premiumGrantUntilMillis] — granted by the operator from the admin
     *    panel. The server is authoritative here and can take it away.
     *
     * They must stay separate. If both lived in one field, revoking an operator
     * gift would be indistinguishable from a refund, so the only way to make
     * revoke work would be to also strip premium the player had paid for — and
     * the only way to make that safe would be to make revoke a no-op. Keeping
     * them apart means [applyServerPremium] can clear exactly the gift and
     * leave a real purchase alone.
     */
    /**
     * The purchase token of the premium transaction that was last delivered.
     *
     * Premium is a 30-day entitlement, not a consumable, so Myket keeps reporting
     * it as owned after it expires. Anything that reacts to "the player owns
     * premium" — rather than to "the player paid and we have not delivered yet" —
     * therefore re-grants it forever. See BillingManager.restorePremium.
     *
     * One token, overwritten on each delivery: a new purchase has a new token and
     * so delivers, the same token never delivers twice.
     */
    var deliveredPremiumPurchaseToken: String
        get() = prefs.getString(KEY_DELIVERED_PREMIUM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DELIVERED_PREMIUM_TOKEN, value).apply()

    /**
     * Whether premium can be bought right now.
     *
     * False while it is active — the shop shows a "cannot buy again" state rather
     * than a button — and true once it lapses, which is what makes premium a
     * renewable purchase instead of a one-off.
     */
    val canBuyPremium: Boolean get() = !isPremium

    /**
     * Whether premium is active right now.
     *
     * Read-only by design. This used to have a setter writing a `KEY_IS_PREMIUM`
     * boolean that nothing ever read back — so `saveManager.isPremium = false`
     * looked like the way to revoke, silently did nothing, and any future fix
     * for the expiry semantics above would have written a key no getter consults.
     * Premium is expressed entirely through the two expiry timestamps; use
     * [grantPremium] / [revokePremium] to move them.
     */
    val isPremium: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now < premiumUntilMillis || now < premiumGrantUntilMillis
        }

    /** Premium auto-expires: when now >= this millisecond, isPremium turns false. */
    var premiumUntilMillis: Long
        get() = prefs.getLong(KEY_PREMIUM_UNTIL_MILLIS, 0L)
        set(value) = prefs.edit().putLong(KEY_PREMIUM_UNTIL_MILLIS, value).apply()

    /**
     * Operator-granted premium, expiry in millis. 0 = not granted.
     *
     * This is the only field the server writes. See [isPremium] for why it is
     * not the same field as a purchase.
     */
    var premiumGrantUntilMillis: Long
        get() = prefs.getLong(KEY_PREMIUM_GRANT_UNTIL_MILLIS, 0L)
        private set(value) = prefs.edit().putLong(KEY_PREMIUM_GRANT_UNTIL_MILLIS, value).apply()

    /**
     * Applies the server's answer about the operator gift.
     *
     * @param active  is the operator currently granting premium to this player?
     * @param untilMs expiry the server sent, in millis; 0 means "no expiry", which
     *   is stored as a far-future date so the comparison in [isPremium] stays a
     *   single "now < x" with no special case.
     *
     * Only touches [premiumGrantUntilMillis], so revoking a gift leaves a
     * Myket purchase intact.
     */
    fun applyServerPremium(active: Boolean, untilMs: Long) {
        val previous = premiumGrantUntilMillis
        val next = when {
            !active -> 0L
            untilMs <= 0L -> NO_EXPIRY_MILLIS
            else -> untilMs
        }
        if (next == previous) return
        premiumGrantUntilMillis = next
        ProgressState.sync(this)
    }

    /** Grants premium for [PREMIUM_PERIOD_MILLIS] from now (isPremium auto-expires). */
    fun grantPremium() {
        val now = System.currentTimeMillis()
        premiumUntilMillis = maxOf(premiumUntilMillis, now) + PREMIUM_PERIOD_MILLIS
    }

    /** Instantly removes premium (refund / manual revoke). */
    fun revokePremium() {
        premiumUntilMillis = 0L
    }

    // --- Daily missions for hours-long engagement (not boring) ---
    //
    // These are Compose snapshot state, not bare SharedPreferences reads.
    // Previously every getter hit prefs, and the home screen's mission card
    // read all of them (plus three day-rollover checks that each build a
    // Calendar) from inside its composable body — which then recomposed 60x a
    // second because it also read an infinite animation there. That was
    // several hundred synchronous prefs operations per second, forever, on the
    // main thread, just to draw one card. Reading snapshot state is free and
    // only the composables that actually use a value recompose.
    var missionBlocks by mutableIntStateOf(0)
        private set
    var missionCoins by mutableIntStateOf(0)
        private set
    var missionLucky by mutableIntStateOf(0)
        private set
    var missionClaimed by mutableStateOf(false)
        private set

    private var missionDate: Long = 0L
        private set

    private var missionLoaded = false

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun loadMissions() {
        if (missionLoaded) return
        missionLoaded = true
        missionDate = prefs.getLong(KEY_MISSION_DATE, 0L)
        missionBlocks = prefs.getInt(KEY_MISSION_BLOCKS, 0)
        missionCoins = prefs.getInt(KEY_MISSION_COINS, 0)
        missionLucky = prefs.getInt(KEY_MISSION_LUCKY, 0)
        missionClaimed = prefs.getBoolean(KEY_MISSION_CLAIMED, false)
    }

    private fun persistMissions() {
        prefs.edit()
            .putLong(KEY_MISSION_DATE, missionDate)
            .putInt(KEY_MISSION_BLOCKS, missionBlocks)
            .putInt(KEY_MISSION_COINS, missionCoins)
            .putInt(KEY_MISSION_LUCKY, missionLucky)
            .putBoolean(KEY_MISSION_CLAIMED, missionClaimed)
            .apply()
    }

    /**
     * Rolls the missions over at midnight. Call this from a LaunchedEffect on
     * entry to the screen, not from a composable body — it performs writes.
     */
    fun refreshMissions() {
        loadMissions()
        val today = startOfToday()
        if (missionDate != today) {
            missionDate = today
            missionBlocks = 0
            missionCoins = 0
            missionLucky = 0
            missionClaimed = false
            persistMissions()
        }
    }

    fun addMissionProgress(blocks: Int = 0, coins: Int = 0, lucky: Int = 0) {
        loadMissions()
        refreshMissions()
        var changed = false
        if (blocks > 0) { missionBlocks = (missionBlocks + blocks).coerceAtMost(9999); changed = true }
        if (coins > 0) { missionCoins = (missionCoins + coins).coerceAtMost(9999); changed = true }
        if (lucky > 0) { missionLucky = (missionLucky + lucky).coerceAtMost(999); changed = true }
        if (changed) persistMissions()
    }

    fun areMissionsComplete(): Boolean =
        missionBlocks >= 30 && missionCoins >= 80 && missionLucky >= 2 && !missionClaimed

    fun canClaimMissions(): Boolean = areMissionsComplete()

    fun claimMissions(): Long {
        loadMissions()
        if (!canClaimMissions()) return 0L
        missionClaimed = true
        persistMissions()
        val reward = MISSION_REWARD_COINS
        addCoins(reward)
        return reward
    }

    init {
        // Restored from prefs rather than defaulted in the property, so the
        // setter stays the only writer and the two can never disagree.
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: ThemeMode.AUTO.name)
        }.getOrDefault(ThemeMode.AUTO)
    }

    companion object {
        const val PREMIUM_PERIOD_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val PREFS_NAME = "blockboom_prefs"

        /** Daily reward shape, shared by the dialog and the payout. */
        const val DAILY_REWARD_BASE = 40L
        const val DAILY_REWARD_MAX_STREAK = 7
        const val DAILY_REWARD_MAX = 200L

        /**
         * Daily mission payout.
         *
         * The missions card advertised "دریافت ۳۵۰ سکه" while claimMissions()
         * paid 100 — a 3.5x overpromise on the one screen where the player is
         * most motivated. Raised to 350 to match what was already promised,
         * because quietly lowering it would have felt like a worse betrayal
         * than honouring it, and 350 is still well under a single coin pack.
         */
        const val MISSION_REWARD_COINS = 350L

        /** Rewarded-ad free upgrades allowed per calendar day. */
        const val FREE_UPGRADES_PER_DAY = AdRewards.FREE_UPGRADES_PER_DAY

        private const val KEY_AD_WATCH_DAY = "ad_watch_day"
        private const val KEY_AD_WATCH_USED = "ad_watch_used"

        private const val KEY_RATE_DECLINED = "rate_declined"
        private const val KEY_RATE_LAST_ASKED = "rate_last_asked_at"
        private const val KEY_RATE_WENT_TO_STORE = "rate_went_to_store"

        const val KEY_LEVEL = "current_level"
        const val KEY_COINS = "coins"

        // Lifetime stats. Namespaced with a "stat_" prefix so they cannot collide
        // with a future field that happens to want the same word.
        private const val KEY_STAT_RUNS = "stat_runs"
        private const val KEY_STAT_LEVELS = "stat_levels_cleared"
        private const val KEY_STAT_BLOCKS = "stat_blocks_destroyed"
        private const val KEY_STAT_SHOTS = "stat_shots_fired"
        private const val KEY_STAT_BEST_COMBO = "stat_best_combo"
        private const val KEY_STAT_COINS = "stat_coins_earned"
        private const val KEY_STAT_BEST_RUN = "stat_best_run_score"
        private const val KEY_STAT_DEEPEST = "stat_deepest_level"
        private const val KEY_STAT_PLAY_SECONDS = "stat_play_seconds"
        private const val KEY_UNLOCKED_ACHIEVEMENTS = "unlocked_achievements"

        // Cloud identity + daily activity.
        private const val KEY_INSTALL_ID = "cloud_install_id"
        private const val KEY_ACCOUNT_ID = "cloud_account_id"
        private const val KEY_ACCOUNT_RECOVERY = "cloud_recovery_code"
        private const val KEY_LAST_CLOUD_SYNC = "cloud_last_sync_at"
        private const val KEY_CLOUD_REGISTERED = "cloud_registered"
        private const val KEY_CLOUD_SYNCING = "cloud_syncing"
        private const val KEY_DAILY_DAY = "daily_day"
        private const val KEY_DAILY_RUNS = "daily_runs"
        private const val KEY_DAILY_LEVELS = "daily_levels"
        private const val KEY_DAILY_BLOCKS = "daily_blocks"
        private const val KEY_DAILY_SHOTS = "daily_shots"
        private const val KEY_DAILY_BEST_COMBO = "daily_best_combo"
        private const val KEY_DAILY_PLAY_SECONDS = "daily_play_seconds"
        private const val KEY_DAILY_COINS = "daily_coins"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIF_PROMPT_SHOWN = "notif_prompt_shown"
        private const val KEY_LAST_REMINDER = "last_reminder_fired_at"
        const val KEY_UPGRADE_DAMAGE = "upgrade_damage"
        const val KEY_UPGRADE_FIRE_RATE = "upgrade_fire_rate"
        const val KEY_UPGRADE_MULTI_BALL = "upgrade_multi_ball"
        const val KEY_UPGRADE_PIERCE = "upgrade_pierce"
        const val KEY_UPGRADE_MAGNET = "upgrade_magnet"
        const val KEY_UPGRADE_BULLET_SPEED = "upgrade_bullet_speed"
        const val KEY_PREMIUM_UNTIL_MILLIS = "premium_until_millis"
        const val KEY_DELIVERED_PREMIUM_TOKEN = "delivered_premium_token"
        const val KEY_PREMIUM_GRANT_UNTIL_MILLIS = "premium_grant_until_millis"

        /**
         * "No expiry" for an operator gift, as a far-future millisecond value.
         *
         * The server sends `until_ts = 0` to mean "this gift does not lapse".
         * Storing that as 0 would read as "already expired" through the same
         * `now < x` comparison every other path uses, so it is normalised into a
         * real far-future date on the way in instead of special-casing the
         * comparison. 2100-01-01.
         */
        const val NO_EXPIRY_MILLIS = 4102444800000L
        const val KEY_LIVES_REMAINING = "lives_remaining"
        const val KEY_LAST_RESET_TIMESTAMP = "last_reset_timestamp"
        const val KEY_LAST_ELAPSED = "last_elapsed_realtime"
        const val KEY_LAST_WALL_CHECK = "last_wall_check"
        const val KEY_MUSIC_ENABLED = "music_enabled"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_FREE_UPGRADE_USED = "free_upgrades_used_today"
        const val KEY_FREE_UPGRADE_DAY = "free_upgrades_day_stamp"
        const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        const val KEY_COACH_DONE = "coach_done"
        const val KEY_COACH_SEEN_VERSION = "coach_seen_version"

        /**
         * Which revision of the in-game coach this player has completed.
         *
         * Bump this in GameCoach.kt whenever a step is added, removed or
         * reworded. Players on an older revision are shown the coach once more;
         * players on this one are not. See [coachSeenVersion].
         */
        const val COACH_VERSION = 2
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_LAST_DAILY_REWARD = "last_daily_reward"
        const val KEY_DAILY_STREAK = "daily_streak"

        const val KEY_BALL_SKIN = "ball_skin"
        const val KEY_CANNON_SKIN = "cannon_skin"
        const val KEY_BACKGROUND_SKIN = "background_skin"
        const val KEY_OWNED_BALL_SKINS = "owned_ball_skins"
        const val KEY_OWNED_CANNON_SKINS = "owned_cannon_skins"
        const val KEY_OWNED_BACKGROUND_SKINS = "owned_background_skins"
        const val KEY_MISSION_DATE = "mission_date"
        const val KEY_MISSION_BLOCKS = "mission_blocks"
        const val KEY_MISSION_COINS = "mission_coins"
        const val KEY_MISSION_LUCKY = "mission_lucky"
        const val KEY_MISSION_CLAIMED = "mission_claimed"
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_TOTAL_SCORE = "total_score"

        /** One month (30 days) — premium auto-expires; read via [isPremium]. */
    
}
}