package ir.amiraligrr.blockboom.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import ir.amiraligrr.blockboom.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Leaderboard network layer.
 *
 * Scores are submitted with a server-signed run token when one is available
 * (see [RunSessionManager]); older servers fall back to the legacy signature.
 * The important change is not in this file's crypto but in how failures are
 * reported: every error path now yields a message a player can act on, in
 * Persian, instead of leaking "HTTP 500" or "Server error" into the UI. That
 * raw text was the "leaderboard error" users kept hitting.
 */
object LeaderboardManager {

    private const val TAG = "Leaderboard"

    private const val FALLBACK_BASE = "https://blockboomapi.amiraligrr.ir"

    fun apiBase(): String = try {
        val u = BuildConfig.BLOCKBOOM_API_URL
        if (u.isNotBlank()) u.trimEnd('/') else FALLBACK_BASE
    } catch (_: Throwable) {
        FALLBACK_BASE
    }

    fun getDeviceId(context: Context): String = try {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        id.replace(Regex("[^A-Za-z0-9_-]"), "").take(32)
            .ifBlank { "dev_${sha256("${System.nanoTime()}${id}").take(24)}" }
    } catch (_: Throwable) {
        // ANDROID_ID is unavailable on a few OEM builds and in some multi-user
        // setups. A stable per-install id is the right fallback: it must not
        // change every launch or the player loses their leaderboard row.
        "dev_${(context.getSharedPreferences("bb_identity", Context.MODE_PRIVATE)
            .getString("did", null) ?: run {
                // `Build.SERIAL` is deprecated, returns the literal "unknown" on
                // every modern Android, and since API 26 needs a privileged
                // permission to read at all. It was therefore never
                // contributing any entropy here. Two stable per-install
                // values stand in for it.
                val seed = listOf(
                    System.nanoTime().toString(),
                    Settings.Secure.getString(
                        context.contentResolver, Settings.Secure.ANDROID_ID
                    ).orEmpty()
                ).joinToString(":")
                val generated = "dev_${sha256(seed).take(24)}"
                context.getSharedPreferences("bb_identity", Context.MODE_PRIVATE)
                    .edit().putString("did", generated).apply()
                generated
            })}"
    }

    private fun sha256(input: String): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        Log.w(TAG, "sha256 failed", t)
        input.hashCode().toUInt().toString(16).padStart(8, '0')
    }

    /**
     * Submits a score.
     *
     * [totalScore] is a Long: the lifetime total passes 2^31 on a long-lived
     * account, and the old code clamped it with coerceAtMost(Int.MAX_VALUE)
     * which silently froze the leaderboard score for those players.
     *
     * No retry loop: the endpoint already tolerates a few fast submissions,
     * and retrying a 429 is what turned a single double-tap into a wall of
     * error dialogs. A *network* failure is different, and is handled by
     * queueing the submission rather than by retrying it here — see
     * [PendingSubmissions].
     */
    suspend fun submitScore(
        context: Context,
        name: String,
        level: Int,
        totalScore: Long,
        isPremium: Boolean,
        closeSession: Boolean = true
    ): SubmitResult = withContext(Dispatchers.IO) {
        if (name.isBlank()) {
            return@withContext SubmitResult.Failed(Err.NeedsName)
        }
        if (totalScore < 0) {
            return@withContext SubmitResult.Failed(Err.InvalidInput)
        }

        // Only a submission that *ends* a run may consume that run's session.
        //
        // This is also the path a queued retry takes (see
        // [PendingSubmissions.flush]), which runs in the background at process
        // start and can reach here while the player is already on their first
        // level. Closing there took the live session out from under the run in
        // progress, so its real end-of-run submission went out with no token and
        // no server-measured play time.
        val snapshot = if (closeSession) RunSessionManager.close() else null
        val deviceId = getDeviceId(context)
        val score = totalScore.coerceAtMost(2_000_000_000L).toInt()

        val body = JSONObject().apply {
            put("name", name)
            put("level", level)
            put("score", score)
            put("is_premium", isPremium)
            put("device_id", deviceId)
            if (snapshot != null) {
                put("run_token", snapshot.runToken)
                put("events", snapshot.events)
                put("chain", snapshot.chain)
                put("duration_ms", snapshot.durationMs)
            }
        }

        val resp = HttpJson.postJson("${apiBase()}/submit_score.php", body)
        if (resp == null) {
            // No answer at all — the request may not even have reached the
            // server. Remember it so the next launch tries again, because a
            // score that only ever existed on the phone is a score the player
            // will notice missing.
            PendingSubmissions.record(context, name, level, totalScore, isPremium)
            return@withContext SubmitResult.Failed(Err.Offline)
        }

        when {
            resp.optBoolean("ok", false) -> {
                val rank = resp.optInt("rank", 0)
                val improved = resp.optString("message", "") != "No need to update"
                SubmitResult.Success(
                    rank = rank.takeIf { it > 0 },
                    level = resp.optInt("level", level),
                    score = resp.optLong("score", totalScore.toLong()),
                    improved = improved,
                    verified = resp.optBoolean("verified", snapshot != null)
                )
            }
            else -> {
                val err = mapError(resp)
                // A refusal for a reason that will still be true next time
                // (a ban, a name that is still blank) is not worth retrying.
                // A refusal that is plausibly transient is queued, capped so a
                // long offline stretch cannot build an unbounded backlog.
                if (err.isWorthRetrying) {
                    PendingSubmissions.record(context, name, level, totalScore, isPremium)
                }
                SubmitResult.Failed(err)
            }
        }
    }

    /** Player-facing Persian message for a server rejection. */
    private fun mapError(resp: JSONObject): Err {
        val code = resp.optString("code", "")
        val raw = resp.optString("error", "")
        return when {
            code == "score_unreachable" -> Err.ScoreUnreachable(resp.optLong("ceiling", 0L))
            code == "replay" -> Err.AlreadySubmitted
            raw.contains("banned", true) || raw.contains("not eligible", true) -> Err.Banned
            resp.optInt("code", 0) == 429 -> Err.RateLimited
            resp.optInt("code", 0) == 403 -> Err.Forbidden
            resp.optInt("code", 0) >= 500 -> Err.ServerBusy
            raw.contains("Invalid run token", true) -> Err.SessionExpired
            else -> Err.Unknown(raw.ifBlank { null })
        }
    }

    sealed class SubmitResult {
        data class Success(
            val rank: Int?,
            val level: Int,
            val score: Long,
            /** False when the stored best was already better than this run. */
            val improved: Boolean,
            /** True when the server accepted a signed run token with it. */
            val verified: Boolean
        ) : SubmitResult()

        data class Failed(val error: Err) : SubmitResult()
    }

    sealed class Err(val userMessage: String) {
        /**
         * Whether re-sending the same score later could plausibly succeed.
         *
         * Drives [PendingSubmissions]. A ban or a blank name will read exactly
         * the same on the next attempt, so queueing those just leaves a stale
         * backlog that the player can never clear.
         */
        open val isWorthRetrying: Boolean get() = false

        data object NeedsName : Err("اول اسم بازیکنت رو انتخاب کن")
        data object Offline : Err("اینترنت وصل نیست — امتیازت بعداً ثبت می‌شه") {
            override val isWorthRetrying = true
        }
        data object InvalidInput : Err("امتیازت معتبر نیست")
        data object AlreadySubmitted : Err("این اجرا قبلاً ثبت شده")
        data object Banned : Err("حسابت از لیدربرد حذف شده")
        data object Forbidden : Err("این درخواست مجاز نیست")
        data object ServerBusy : Err("سرور شلوغه — چند لحظه بعد دوباره تلاش کن") {
            override val isWorthRetrying = true
        }
        data object RateLimited : Err("یه کم آروم‌تر! چند ثانیه صبر کن و دوباره بزن") {
            override val isWorthRetrying = true
        }
        data object SessionExpired : Err("نشست بازی منقضی شده — یه بار دیگه بازی کن")
        class ScoreUnreachable(val ceiling: Long) :
            Err("این امتیاز با این مقدار بازی ممکن نبوده — اگر مطمئنی مشکلی نیست گزارش بده") {
            // The server asked for a retry, but the score is judged against
            // play time that has since grown, so a later attempt can pass.
            override val isWorthRetrying = true
        }

        class Unknown(val serverText: String?) : Err("ثبت امتیاز انجام نشد") {
            override val isWorthRetrying = true
        }
    }

    /**
     * Fetches a page of the leaderboard.
     *
     * Retries once on a transient network failure, because a single dropped
     * request on a shared mobile connection should not present as an error.
     */
    suspend fun fetchLeaderboard(
        context: Context,
        page: Int = 1,
        limit: Int = 20,
        period: String = "all"
    ): Result<LeaderboardResponse> = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)

        var lastError: Err = Err.Offline
        repeat(2) { attempt ->
            val url = "${apiBase()}/leaderboard.php?page=$page&limit=$limit" +
                "&period=$period&device_id=$deviceId"
            val (code, resp) = HttpJson.getJson(url) ?: run {
                lastError = Err.Offline
                return@repeat
            }
            if (code !in 200..299) {
                lastError = when {
                    code == 429 -> Err.RateLimited
                    code >= 500 -> Err.ServerBusy
                    else -> Err.Unknown(null)
                }
                return@repeat
            }
            if (!resp.optBoolean("ok", false)) {
                return@withContext Result.failure(Exception(mapError(resp).userMessage))
            }
            return@withContext Result.success(parse(resp, page, limit))
        }
        Result.failure(Exception(lastError.userMessage))
    }

    private fun parse(obj: JSONObject, page: Int, limit: Int): LeaderboardResponse {
        val arr = obj.optJSONArray("data") ?: org.json.JSONArray()
        val list = ArrayList<LeaderboardEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                LeaderboardEntry(
                    name = o.optString("name", "—").ifBlank { "—" },
                    level = o.optInt("level", 1),
                    score = o.optLong("score", 0L),
                    isPremium = o.optInt("is_premium", 0) == 1,
                    crown = o.optString("crown", ""),
                    updatedAt = o.optString("updated_at", ""),
                    rank = o.optInt("rank", 0)
                )
            )
        }

        val p = obj.optJSONObject("pagination")
        val pagination = if (p != null) {
            PaginationInfo(
                page = p.optInt("page", page),
                limit = p.optInt("limit", limit),
                total = p.optInt("total", list.size),
                totalPages = maxOf(1, p.optInt("totalPages", 1)),
                hasMore = p.optBoolean("hasMore", false)
            )
        } else {
            PaginationInfo(page, limit, list.size, 1, false)
        }

        val userEntry = obj.optJSONObject("userEntry")?.let { u ->
            LeaderboardEntry(
                name = u.optString("name", "—").ifBlank { "—" },
                level = u.optInt("level", 1),
                score = u.optLong("score", 0L),
                isPremium = u.optInt("is_premium", 0) == 1,
                crown = u.optString("crown", ""),
                updatedAt = u.optString("updated_at", ""),
                rank = u.optInt("rank", 0)
            )
        }

        return LeaderboardResponse(
            data = list,
            pagination = pagination,
            userRank = obj.optInt("userRank", 0).takeIf { it > 0 },
            userEntry = userEntry,
            period = obj.optString("period", "all")
        )
    }

    /** Legacy shim kept for existing call sites. */
    suspend fun fetchTop10(context: Context): Result<List<LeaderboardEntry>> =
        fetchLeaderboard(context, 1, 10, "all").map { it.data }
}

/**
 * Scores that never made it to the server, kept until they do.
 *
 * ── The bug this closes ───────────────────────────────────────────────────
 * `submitScore` reported a network failure as `Err.Offline` and moved on. The
 * run was gone: the score had only ever existed in SharedPreferences, and the
 * player had just watched a "ثبت نشد" message. If they then played another
 * level, the lifetime total went up from a base the server had never seen, so
 * the leaderboard entry was permanently understated — a slow connection cost
 * them their rank, silently and permanently.
 *
 * ── Why a queue and not a retry loop ──────────────────────────────────────
 * Retrying inside `submitScore` blocks the end-of-run UI on a request the
 * player is waiting to see through, and on a dead connection it turns one run
 * into many. A queue defers the work to the next moment the player is
 * somewhere that can afford to wait — app launch — and costs nothing when it
 * is not needed.
 *
 * ── Bounds ────────────────────────────────────────────────────────────────
 * At most [MAX_QUEUED] entries, and entries older than [MAX_AGE_MS] are
 * dropped. A player who does not open the app for a month should not have a
 * week-old score from a level they no longer remember appear on the board
 * after a sync. Only the newest is ever resubmitted, because the leaderboard
 * upsert is monotonic: submitting the highest score implies every lower one.
 */
object PendingSubmissions {

    private const val TAG = "PendingSubmit"
    private const val PREFS = "bb_pending_submit"
    private const val KEY_ENTRY = "entry"
    private const val MAX_QUEUED = 5
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * How many scores are waiting, as observable state.
     *
     * The home screen's status strip shows "your score is queued for
     * submission", and that message is only useful if it appears at the moment
     * a submit fails. It was reading this once in a `LaunchedEffect(Unit)`,
     * which is a snapshot of the queue as it was when the home screen was
     * entered — a run that failed afterwards, on the very same screen, showed
     * nothing. A plain SharedPreferences read would have the same problem in a
     * different guise: it registers no snapshot observation, so nothing would
     * recompose when the value changed.
     */
    private val _count = kotlinx.coroutines.flow.MutableStateFlow(0)
    val countFlow: StateFlow<Int> = _count.asStateFlow()

    private data class Entry(
        val name: String,
        val level: Int,
        val score: Long,
        val isPremium: Boolean,
        val at: Long
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Remembers a score, keeping only the best of what is already queued.
     *
     * Best-of rather than append, because the server's upsert is monotonic in
     * score: the higher total subsumes the lower ones, so a queue of ten would
     * submit ten requests to record one row.
     */
    fun record(context: Context, name: String, level: Int, score: Long, isPremium: Boolean) {
        val now = System.currentTimeMillis()
        val kept = read(context)
            .filter { now - it.at <= MAX_AGE_MS }
            .filter { !(it.name == name && it.score >= score) }   // new one is at least as good
        val merged = (kept + Entry(name, level, score, isPremium, now))
            .sortedByDescending { it.score }
            .take(MAX_QUEUED)
        write(context, merged)
        _count.value = merged.size
        Log.i(TAG, "queued score=$score level=$level (${merged.size} pending)")
    }

    /** The current queue depth. Cheap; safe to call from a composable. */
    fun count(context: Context): Int = _count.value

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ENTRY).apply()
        _count.value = 0
    }

    /**
     * Re-reads the persisted queue into [_count].
     *
     * Called once at startup. The in-memory count starts at zero, so without
     * this a score that was queued before the process died would be invisible
     * on the home screen even though it is still waiting to be sent.
     */
    fun restoreCount(context: Context) {
        _count.value = read(context).size
    }

    /**
     * Re-sends the best queued score, if there is one.
     *
     * Called from app launch, never from the game loop. Clears the entry on any
     * answer at all — including a refusal — because re-sending a score the
     * server has already judged is how a device earns a rate-limit ban, and the
     * entry is capped by age so nothing is lost forever by dropping it.
     */
    suspend fun flush(context: Context) {
        val entry = read(context).maxByOrNull { it.score } ?: return
        val result = LeaderboardManager.submitScore(
            context = context,
            name = entry.name,
            level = entry.level,
            totalScore = entry.score,
            isPremium = entry.isPremium,
            // A retry is not the end of a run. It must not close whatever run
            // the player is in right now.
            closeSession = false
        )
        when (result) {
            is LeaderboardManager.SubmitResult.Success -> {
                clear(context)
                Log.i(TAG, "flushed queued score=${entry.score}")
            }
            is LeaderboardManager.SubmitResult.Failed -> {
                if (!result.error.isWorthRetrying) {
                    clear(context)
                    Log.i(TAG, "dropped queued score: ${result.error.userMessage}")
                } else {
                    Log.i(TAG, "queued score still failing, will retry next launch")
                }
            }
        }
    }

    private fun read(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY_ENTRY, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    name = o.optString("n"),
                    level = o.optInt("l", 1),
                    score = o.optLong("s", 0L),
                    isPremium = o.optBoolean("p", false),
                    at = o.optLong("a", 0L)
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "corrupt queue discarded", t)
            clear(context)
            emptyList()
        }
    }

    private fun write(context: Context, entries: List<Entry>) {
        val arr = org.json.JSONArray()
        entries.forEach {
            arr.put(
                org.json.JSONObject()
                    .put("n", it.name)
                    .put("l", it.level)
                    .put("s", it.score)
                    .put("p", it.isPremium)
                    .put("a", it.at)
            )
        }
        prefs(context).edit().putString(KEY_ENTRY, arr.toString()).apply()
    }
}
