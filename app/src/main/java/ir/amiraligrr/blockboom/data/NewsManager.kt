package ir.amiraligrr.blockboom.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Published game news. Drafts are not returned by the API.
 *
 * The client never invents an item: an empty response means the UI says there
 * is no news, not that a placeholder should be shown.
 */
data class NewsItem(
    val id: Int,
    val title: String,
    val summary: String,
    val body: String,
    val imageUrl: String,
    val publishedAt: String,
)

object NewsManager {
    suspend fun fetchNews(context: Context): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val (_, resp) = HttpJson.getJson(
                "${LeaderboardManager.apiBase()}/news.php",
                connectMs = 7000,
                readMs = 7000
            ) ?: return@withContext emptyList()
            parseNewsArray(resp.optJSONArray("news"))
        } catch (t: Throwable) {
            emptyList()
        }
    }
}

/** Pure parser so the wire format is testable without a server. */
internal fun parseNewsArray(arr: JSONArray?): List<NewsItem> {
    if (arr == null) return emptyList()
    return buildList(arr.length()) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "").trim()
            if (title.isBlank()) continue
            add(
                NewsItem(
                    id = o.optInt("id"),
                    title = title,
                    summary = o.optString("summary", ""),
                    body = o.optString("body", ""),
                    imageUrl = o.optString("image_url", ""),
                    publishedAt = o.optString("published_at", ""),
                )
            )
        }
    }
}
