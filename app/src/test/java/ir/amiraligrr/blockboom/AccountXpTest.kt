package ir.amiraligrr.blockboom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.amiraligrr.blockboom.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * XP is the only new progression number in the next-generation slice. It is
 * easy to get wrong in three ways: paying per frame instead of per run,
 * letting a server restore lower it, or letting the loop overflow on a large
 * value. These tests pin the contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountXpTest {

    private lateinit var context: Context

    @Before
    fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("blockboom_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun save() = SaveManager(context)

    @Test
    fun freshSaveStartsAtLevelOneWithNoXp() {
        val save = save()
        assertEquals(1, save.accountLevel())
        assertEquals(0L, save.xpIntoLevel())
        assertEquals(0f, save.xpProgress(), 0.0001f)
    }

    @Test
    fun aRunAwardsXpAndCanLevelUp() {
        val save = save()
        val before = save.accountLevel()
        val gained = save.addRunXp(score = 2_500, blocks = 300, levelsCleared = 2, coins = 100)
        assertTrue("a real run should award at least one level", gained >= 1)
        assertTrue(save.accountLevel() > before)
        assertEquals(2, save.accountLevel())
        assertEquals(240L, save.xp)
    }

    @Test
    fun restoreNeverLowersXp() {
        val save = save()
        save.addRunXp(score = 5_000, blocks = 0, levelsCleared = 0, coins = 0)
        val earned = save.xp
        save.restoreXp(earned - 50)
        assertEquals(earned, save.xp)
        save.restoreXp(earned + 50)
        assertEquals(earned + 50, save.xp)
    }

    @Test
    fun zeroRunDoesNotAwardXp() {
        val save = save()
        assertEquals(0, save.addRunXp(0, 0, 0, 0))
        assertEquals(0L, save.xp)
    }
}
