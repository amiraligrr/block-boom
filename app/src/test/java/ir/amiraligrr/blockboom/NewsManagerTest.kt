package ir.amiraligrr.blockboom

import ir.amiraligrr.blockboom.data.NewsItem
import ir.amiraligrr.blockboom.data.parseNewsArray
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewsManagerTest {

    @Test
    fun parsesPublishedItemsAndKeepsOrder() {
        val arr = JSONArray()
            .put(JSONObject().put("id", 7).put("title", " خبر اول ").put("body", "متن"))
            .put(JSONObject().put("id", 8).put("title", "خبر دوم").put("summary", "خلاصه"))

        val items = parseNewsArray(arr)

        assertEquals(2, items.size)
        assertEquals("خبر اول", items[0].title)
        assertEquals(7, items[0].id)
        assertEquals("خبر دوم", items[1].title)
        assertEquals("خلاصه", items[1].summary)
    }

    @Test
    fun skipsAnonymousOrEmptyRowsInsteadOfInventingNews() {
        val arr = JSONArray()
            .put(JSONObject().put("id", 1).put("title", ""))
            .put(JSONObject().put("id", 2))
            .put(JSONObject().put("id", 3).put("title", "واقعی"))

        val items = parseNewsArray(arr)

        assertEquals(1, items.size)
        assertEquals("واقعی", items[0].title)
    }

    @Test
    fun missingArrayMeansNoNews() {
        assertTrue(parseNewsArray(null).isEmpty())
    }

    @Test
    fun itemWithoutOptionalFieldsStillOpens() {
        val item = parseNewsArray(
            JSONArray().put(JSONObject().put("id", 4).put("title", "فقط عنوان"))
        ).single()

        assertEquals("", item.body)
        assertEquals("", item.summary)
        assertEquals("", item.imageUrl)
        assertEquals("", item.publishedAt)
    }
}
