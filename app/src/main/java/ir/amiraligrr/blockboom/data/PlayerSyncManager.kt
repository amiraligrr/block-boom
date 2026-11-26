package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/**
 * Server-side player identity, progress backup and daily activity.
 *
 * ── Why this exists ──────────────────────────────────────────────────────
 * The API has had `account.php` (register / recover / sync) and a
 * `device_links` table for a while, but the app never called either. The
 * result was a game where only leaderboard players existed on the server:
 * everyone else was a number in a telemetry bucket with no name, no level and
 * no way to get their progress back after a reinstall.
 *
 * This manager makes every install a real server player:
 *
 *  - `register` is called on first launch and is idempotent.
 *  - A stable [SaveManager.installId] distinguishes a reinstall from a
 *    first install; ANDROID_ID is still the primary device key, so an
 *    ordinary uninstall/reinstall restores automatically.
 *  - `sync` pushes level / score / coins / upgrades and today's activity.
 *    The server max-merges the progress and GREATEST-merges the daily row,
 *    so retries are safe.
 *  - A recovery code is stored locally and shown in Settings. It is the
 *    fallback for a factory reset or a new phone, where ANDROID_ID changes.
 *
 * All network work is fire-and-forget. A failure is logged and retried on the
 * next launch/run; it never blocks gameplay.
 */
object PlayerSyncManager {

    private const val TAG = "PlayerSync"

    /** Minimum gap between automatic syncs, unless [force] is set. */
    private const val AUTO_SYNC_INTERVAL_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var save: SaveManager

    @Volatile private var initialized = false
    @Volatile private var lastAttemptAt = 0L
    @Volatile private var lastSuccessAt = 0L

    private val _notice = MutableStateFlow<String?>(null)

    /** One-shot player-facing message, e.g. "progress restored". */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            save = (appContext as BlockBoomApp).saveManager
            initialized = true
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    /** Recovery code for the Settings card, or "" when none has been issued. */
    fun recoveryCode(): String = if (initialized) save.accountRecoveryCode else ""

    fun accountId(): Int = if (initialized) save.accountId else 0

    fun lastSyncAt(): Long = if (initialized) save.lastCloudSyncAt else 0L

    /** Fire-and-forget sync. Safe to call from any thread. */
    fun syncNow(force: Boolean = false) {
        if (!initialized) return
        scope.launch { runSync(force) }
    }

    /**
     * Synchronous variant, used by the Settings screen's "sync now" button and
     * by tests. Returns true on a successful round-trip.
     */
    suspend fun syncBlocking(force: Boolean = true): Boolean =
        withContext(Dispatchers.IO) { runSync(force) }

    private suspend fun runSync(force: Boolean): Boolean = mutex.withLock {
        if (!initialized) return@withLock false
        val now = System.currentTimeMillis()
        if (!force && now - lastAttemptAt < AUTO_SYNC_INTERVAL_MS) return@withLock false
        lastAttemptAt = now
        _syncing.value = true
        _lastError.value = null
        try {
            val deviceId = LeaderboardManager.getDeviceId(appContext)

            // ── 1. Register ────────────────────────────────────────────────
            // Deliberately no progress in the register payload. If the
            // account is new it starts at level 1 and the sync below fills
            // it in; if it already exists, the response is the authoritative
            // snapshot we can restore from. Sending local progress here
            // would overwrite a reinstall's server snapshot with defaults
            // before we ever had a chance to read it.
            val register = post(basePayload("register", deviceId))
            if (register.optBoolean("ok", false)) {
                save.accountId = register.optInt("account_id", save.accountId)
                save.cloudRegistered = true
                val newCode = register.optString("recovery", "")
                if (newCode.isNotBlank()) save.accountRecoveryCode = newCode

                val serverProgress = register.optJSONObject("progress")
                if (serverProgress != null) {
                    applyServerProgress(serverProgress, isRegister = true)
                }
            }

            // ── 2. Push the merged local state + today's activity ──────────
            val sync = post(basePayload("sync", deviceId).apply {
                put("progress", progressJson())
            })
            if (sync.optBoolean("ok", false)) {
                val serverProgress = sync.optJSONObject("progress")
                if (serverProgress != null) {
                    // A normal sync is a merge, not a restore: never apply
                    // the server's preferences over local ones.
                    applyServerProgress(serverProgress, isRegister = false)
                }
                if (sync.optBoolean("restored", false)) {
                    _notice.value = "پیشرفت قبلی‌ات بازیابی شد ✅"
                }
                save.lastCloudSyncAt = System.currentTimeMillis()
                lastSuccessAt = save.lastCloudSyncAt
                return@withLock true
            }
            _lastError.value = sync.optString("error", "همگام‌سازی ناموفق بود")
            false
        } catch (t: Throwable) {
            // Offline is normal. Keep the local game playable and retry later.
            Log.d(TAG, "sync failed: ${t.message}")
            _lastError.value = "اتصال به سرور برقرار نشد"
            false
        } finally {
            _syncing.value = false
        }
    }

    /**
     * Restores a factory-reset/new-phone player from their recovery code.
     * The callback runs on the calling thread after the network round-trip.
     */
    fun recover(code: String, onResult: (Result<String>) -> Unit) {
        if (!initialized) {
            onResult(Result.failure(IllegalStateException("not initialized")))
            return
        }
        scope.launch {
            val result = runCatching {
                val deviceId = LeaderboardManager.getDeviceId(appContext)
                val payload = basePayload("recover", deviceId).apply {
                    put("recovery", code.trim())
                }
                val json = post(payload)
                if (!json.optBoolean("ok", false)) {
                    throw IOException(json.optString("error", "کد بازیابی معتبر نیست"))
                }
                val accountId = json.optInt("account_id", 0)
                val recovery = json.optString("recovery", code.trim())
                save.accountId = accountId
                save.accountRecoveryCode = recovery
                save.cloudRegistered = true
                json.optJSONObject("progress")?.let { applyServerProgress(it, isRegister = true) }
                // Push the restored snapshot back with today's bucket so the
                // admin immediately sees the player as active again.
                post(basePayload("sync", deviceId).apply { put("progress", progressJson()) })
                save.lastCloudSyncAt = System.currentTimeMillis()
                _notice.value = "حسابت بازیابی شد ✅"
                json.optString("player_name", "")
            }
            result.onSuccess { name ->
                onResult(Result.success(name))
            }.onFailure {
                onResult(Result.failure(it))
            }
        }
    }

    /** Issues a fresh recovery code for an existing device-linked account. */
    fun rotateRecoveryCode(onResult: (Result<String>) -> Unit) {
        if (!initialized) {
            onResult(Result.failure(IllegalStateException("not initialized")))
            return
        }
        scope.launch {
            runCatching {
                val deviceId = LeaderboardManager.getDeviceId(appContext)
                val json = post(basePayload("rotate", deviceId))
                if (!json.optBoolean("ok", false)) {
                    throw IOException(json.optString("error", "ساخت کد ناموفق بود"))
                }
                val code = json.optString("recovery", "")
                if (code.isBlank()) throw IOException("empty recovery code")
                save.accountRecoveryCode = code
                save.cloudRegistered = true
                code
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure { onResult(Result.failure(it)) }
        }
    }

    // ── payload construction ─────────────────────────────────────────────

    private fun basePayload(action: String, deviceId: String): JSONObject = JSONObject().apply {
        put("action", action)
        put("device_id", deviceId)
        put("install_id", save.installId)
        put("app_version", BuildConfig.VERSION_NAME)
        put("version_code", BuildConfig.VERSION_CODE)
        put("player_name", save.playerName)
        put("daily", dailyJson())
    }

    private fun progressJson(): JSONObject = JSONObject().apply {
        put("level", save.currentLevel)
        put("total_score", save.totalScore)
        put("coins", save.coins)
        put("upgrades", JSONObject().apply {
            put("damage", save.upgradeDamage)
            put("fire_rate", save.upgradeFireRate)
            put("multi_ball", save.upgradeMultiBall)
            put("pierce", save.upgradePierce)
            put("magnet", save.upgradeMagnet)
            put("bullet_speed", save.upgradeBulletSpeed)
        })
        put("meta", metaJson())
    }

    private fun metaJson(): JSONObject = JSONObject().apply {
        put("player_name", save.playerName)
        put("theme_mode", save.themeMode.name)
        put("onboarding_completed", save.onboardingCompleted)
        put("coach_done", save.coachDone)
        put("coach_seen_version", save.coachSeenVersion)
        put("ball_skin", save.selectedBallSkin)
        put("cannon_skin", save.selectedCannonSkin)
        put("background_skin", save.selectedBackgroundSkin)
        put("owned_ball_skins", save.ownedBallSkins)
        put("owned_cannon_skins", save.ownedCannonSkins)
        put("owned_background_skins", save.ownedBackgroundSkins)
        put("stat_runs", save.statRuns)
        put("stat_levels_cleared", save.statLevelsCleared)
        put("stat_blocks", save.statBlocksDestroyed)
        put("stat_shots", save.statShotsFired)
        put("stat_best_combo", save.statBestCombo)
        put("stat_play_seconds", save.statPlaySeconds)
    }

    private fun dailyJson(): JSONObject {
        val d = save.dailySnapshot()
        return JSONObject().apply {
            put("day", d.day)
            put("level", d.level)
            put("total_score", d.totalScore)
            put("coins", d.coins)
            put("runs", d.runs)
            put("levels_cleared", d.levelsCleared)
            put("blocks", d.blocks)
            put("shots", d.shots)
            put("best_combo", d.bestCombo)
            put("play_seconds", d.playSeconds)
            put("coins_earned", d.coinsEarned)
        }
    }

    // ── applying the server snapshot ─────────────────────────────────────

    /**
     * @param isRegister true only for the initial register/recover read. In
     *   that case the server snapshot is allowed to restore local
     *   preferences (name, skins, onboarding) when this looks like a fresh
     *   install. A normal sync only max-merges progress.
     */
    private fun applyServerProgress(progress: JSONObject, isRegister: Boolean) {
        val serverLevel = progress.optInt("level", 1)
        val serverScore = progress.optLong("total_score", 0L)
        val serverCoins = progress.optLong("coins", 0L)

        save.currentLevel = max(save.currentLevel, serverLevel)
        save.totalScore = max(save.totalScore, serverScore)
        save.coins = max(save.coins, serverCoins)

        progress.optJSONObject("upgrades")?.let { up ->
            save.upgradeDamage = max(save.upgradeDamage, up.optInt("damage", 0))
            save.upgradeFireRate = max(save.upgradeFireRate, up.optInt("fire_rate", 0))
            save.upgradeMultiBall = max(save.upgradeMultiBall, up.optInt("multi_ball", 0))
            save.upgradePierce = max(save.upgradePierce, up.optInt("pierce", 0))
            save.upgradeMagnet = max(save.upgradeMagnet, up.optInt("magnet", 0))
            save.upgradeBulletSpeed = max(save.upgradeBulletSpeed, up.optInt("bullet_speed", 0))
        }

        val freshInstall = !save.onboardingCompleted &&
            save.statRuns == 0 &&
            save.totalScore == 0L &&
            save.currentLevel <= 1

        val hasServerProgress = serverLevel > 1 || serverScore > 0L || serverCoins > 0L

        if (isRegister && (freshInstall || hasServerProgress)) {
            progress.optJSONObject("meta")?.let { meta ->
                val name = meta.optString("player_name", "").trim()
                if (name.isNotBlank()) save.playerName = name

                // Theme is a player preference, not progress. Restore it only
                // on register/recover, where the server snapshot is allowed to
                // repopulate a fresh install; a normal sync must not undo a
                // choice the player just made on this device.
                runCatching {
                    ThemeMode.valueOf(meta.optString("theme_mode", ""))
                }.getOrNull()?.let { save.themeMode = it }

                if (meta.optBoolean("onboarding_completed", false) || hasServerProgress) {
                    save.onboardingCompleted = true
                }
                if (meta.optBoolean("coach_done", false)) save.coachDone = true
                save.coachSeenVersion = max(save.coachSeenVersion, meta.optInt("coach_seen_version", 0))

                applySkin(meta, "ball_skin") { save.selectedBallSkin = it }
                applySkin(meta, "cannon_skin") { save.selectedCannonSkin = it }
                applySkin(meta, "background_skin") { save.selectedBackgroundSkin = it }
                save.ownedBallSkins = mergeCsv(save.ownedBallSkins, meta.optString("owned_ball_skins", ""))
                save.ownedCannonSkins = mergeCsv(save.ownedCannonSkins, meta.optString("owned_cannon_skins", ""))
                save.ownedBackgroundSkins = mergeCsv(save.ownedBackgroundSkins, meta.optString("owned_background_skins", ""))
            }
            if (freshInstall && hasServerProgress) {
                _notice.value = "پیشرفت قبلی‌ات بازیابی شد ✅"
            }
        }
    }

    private inline fun applySkin(meta: JSONObject, key: String, set: (String) -> Unit) {
        val value = meta.optString(key, "").trim()
        if (value.isNotBlank()) set(value)
    }

    private fun mergeCsv(local: String, server: String): String =
        (local.split(',') + server.split(','))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")

    // ── HTTP ─────────────────────────────────────────────────────────────

    private fun post(payload: JSONObject, timeoutMs: Int = 12_000): JSONObject {
        val url = URL("${LeaderboardManager.apiBase()}/account.php")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 7_000
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${text.take(180)}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
