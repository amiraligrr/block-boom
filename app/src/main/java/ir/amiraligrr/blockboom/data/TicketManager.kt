package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Client for the support-ticket API that already lives on the server
 * (`tickets.php`, actions: create / list / thread / reply), answered from
 * the existing admin panel (`admin/tickets.php`).
 *
 * ── Why this file exists ───────────────────────────────────────────────
 * The whole loop was already built server-side — the API, the admin inbox,
 * the reply flow, the unread flags — but the app never called any of it.
 * SupportScreen was a static FAQ plus a mailto: link, so a player with a
 * problem and an admin with an inbox could never meet. This is the missing
 * half: same contract the panel already answers.
 *
 * Everything is offline-tolerant. A failed call returns a Persian message,
 * never throws, and nothing the player typed is lost on a failure — the
 * composer keeps its text and can retry.
 */
object TicketManager {

    private const val TAG = "Tickets"

    /** Categories the server accepts; anything else is coerced to "other". */
    enum class Category(val api: String, val fa: String) {
        BUG("bug", "گزارش باگ"),
        PAYMENT("payment", "مشکل خرید"),
        ACCOUNT("account", "حساب کاربری"),
        CHEAT("cheat", "گزارش متقلب"),
        OTHER("other", "سایر");

        companion object {
            fun fromApi(api: String): Category =
                entries.firstOrNull { it.api == api } ?: OTHER
        }
    }

    data class Ticket(
        val id: Int,
        val subject: String,
        val category: Category,
        val status: String,
        val createdAt: String,
        val updatedAt: String,
        val lastBody: String,
        val msgCount: Int,
        val unread: Boolean
    ) {
        /** True while the thread is still alive for both sides. */
        val isOpen: Boolean get() = status != "closed"

        fun statusFa(): String = when (status) {
            "open" -> "باز"
            "answered" -> "پاسخ داده شد"
            "closed" -> "بسته"
            else -> status
        }
    }

    data class TicketMessage(
        val id: Int,
        val fromAdmin: Boolean,
        val body: String,
        val createdAt: String
    )

    data class Thread(
        val id: Int,
        val subject: String,
        val category: Category,
        val status: String,
        val createdAt: String,
        val messages: List<TicketMessage>
    )

    /**
     * Tickets with an admin reply the player has not opened yet.
     *
     * Observed by the home/settings badge so "پشتیبانی جواب داد" appears
     * without opening the screen. Refreshed by [refreshUnread]; the screen
     * itself refreshes it too, so the badge clears the moment the list loads.
     */
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private fun endpoint(): String = "${LeaderboardManager.apiBase()}/tickets.php"

    private fun base(deviceId: String, action: String): JSONObject =
        JSONObject().apply {
            put("device_id", deviceId)
            put("action", action)
        }

    private fun parseTicket(o: JSONObject): Ticket = Ticket(
        id = o.optInt("id"),
        subject = o.optString("subject", "—"),
        category = Category.fromApi(o.optString("category", "other")),
        status = o.optString("status", "open"),
        createdAt = o.optString("created_at", ""),
        updatedAt = o.optString("updated_at", ""),
        lastBody = o.optString("last_body", ""),
        msgCount = o.optInt("msg_count", 0),
        unread = o.optBoolean("unread", false)
    )

    private fun errorOf(resp: JSONObject?): String {
        if (resp == null) return "اینترنت وصل نیست — بعداً دوباره تلاش کن"
        if (resp.optBoolean("ok", false)) return ""
        return resp.optString("error", "خطایی رخ داد").ifBlank { "خطایی رخ داد" }
    }

    /** Player's threads, newest first. Also refreshes [unreadCount]. */
    suspend fun list(context: Context): Result<List<Ticket>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = HttpJson.postJson(
                    endpoint(),
                    base(LeaderboardManager.getDeviceId(context), "list")
                ) ?: return@withContext Result.failure(Exception(errorOf(null)))
                val err = errorOf(resp)
                if (err.isNotEmpty()) return@withContext Result.failure(Exception(err))
                val arr = resp.optJSONArray("tickets") ?: org.json.JSONArray()
                val out = ArrayList<Ticket>(arr.length())
                for (i in 0 until arr.length()) {
                    runCatching { out.add(parseTicket(arr.getJSONObject(i))) }
                }
                _unreadCount.value = out.count { it.unread }
                Result.success(out)
            } catch (t: Throwable) {
                Log.i(TAG, "list failed: ${t.message}")
                Result.failure(Exception("اینترنت وصل نیست — بعداً دوباره تلاش کن"))
            }
        }

    /** Full thread, oldest message first. Opening marks it read server-side. */
    suspend fun thread(context: Context, ticketId: Int): Result<Thread> =
        withContext(Dispatchers.IO) {
            try {
                val body = base(LeaderboardManager.getDeviceId(context), "thread")
                body.put("ticket_id", ticketId)
                val resp = HttpJson.postJson(endpoint(), body)
                    ?: return@withContext Result.failure(Exception(errorOf(null)))
                val err = errorOf(resp)
                if (err.isNotEmpty()) return@withContext Result.failure(Exception(err))
                val t = resp.optJSONObject("ticket")
                    ?: return@withContext Result.failure(Exception("تیکت پیدا نشد"))
                val arr = resp.optJSONArray("messages") ?: org.json.JSONArray()
                val msgs = ArrayList<TicketMessage>(arr.length())
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    msgs.add(
                        TicketMessage(
                            id = m.optInt("id"),
                            fromAdmin = m.optBoolean("from_admin", false),
                            body = m.optString("body", ""),
                            createdAt = m.optString("created_at", "")
                        )
                    )
                }
                // The thread was just opened, so its unread flag is spent.
                _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)
                Result.success(
                    Thread(
                        id = t.optInt("id", ticketId),
                        subject = t.optString("subject", "—"),
                        category = Category.fromApi(t.optString("category", "other")),
                        status = t.optString("status", "open"),
                        createdAt = t.optString("created_at", ""),
                        messages = msgs
                    )
                )
            } catch (t: Throwable) {
                Log.i(TAG, "thread failed: ${t.message}")
                Result.failure(Exception("اینترنت وصل نیست — بعداً دوباره تلاش کن"))
            }
        }

    /** Opens a new thread. Returns the ticket id. */
    suspend fun create(
        context: Context,
        subject: String,
        body: String,
        category: Category,
        name: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val req = base(LeaderboardManager.getDeviceId(context), "create")
            req.put("subject", subject.trim())
            req.put("body", body.trim())
            req.put("category", category.api)
            req.put("name", name.trim().take(32))
            val resp = HttpJson.postJson(endpoint(), req)
                ?: return@withContext Result.failure(Exception(errorOf(null)))
            val err = errorOf(resp)
            if (err.isNotEmpty()) return@withContext Result.failure(Exception(err))
            Result.success(resp.optInt("ticket_id", 0))
        } catch (t: Throwable) {
            Log.i(TAG, "create failed: ${t.message}")
            Result.failure(Exception("اینترنت وصل نیست — بعداً دوباره تلاش کن"))
        }
    }

    /** Replies on an open thread. */
    suspend fun reply(context: Context, ticketId: Int, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val req = base(LeaderboardManager.getDeviceId(context), "reply")
                req.put("ticket_id", ticketId)
                req.put("body", body.trim())
                val resp = HttpJson.postJson(endpoint(), req)
                    ?: return@withContext Result.failure(Exception(errorOf(null)))
                val err = errorOf(resp)
                if (err.isNotEmpty()) return@withContext Result.failure(Exception(err))
                Result.success(Unit)
            } catch (t: Throwable) {
                Log.i(TAG, "reply failed: ${t.message}")
                Result.failure(Exception("اینترنت وصل نیست — بعداً دوباره تلاش کن"))
            }
        }

    /** Re-reads the list silently, just to update the badge. Cheap; one call. */
    suspend fun refreshUnread(context: Context) {
        runCatching { list(context) }
    }
}
