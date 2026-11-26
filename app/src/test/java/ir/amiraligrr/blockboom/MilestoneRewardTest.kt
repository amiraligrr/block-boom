package ir.amiraligrr.blockboom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.amiraligrr.blockboom.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The five-level chest is the short-term goal the home card promises before a
 * run starts. These tests pin down the two ways that promise can break: paying
 * the same chest twice, or making a new player clear a level the card did not
 * count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MilestoneRewardTest {

    private lateinit var context: Context

    @Before
    fun resetPrefs() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("blockboom_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun newSave(): SaveManager = SaveManager(context)

    @Test
    fun aFreshSaveIsFiveClearsFromTheFirstChest() {
        val save = newSave()

        assertEquals(5, save.nextMilestoneLevel())
        // Levels 1, 2, 3, 4 and 5 all have to be cleared, so the count includes
        // the level in front of the player.
        assertEquals(5, save.levelsUntilMilestone())
        assertEquals(140L, save.milestoneRewardFor(5))
    }

    @Test
    fun clearingTheChestLevelPaysItExactlyOnce() {
        val save = newSave()

        // GameEngine calls nextLevel() before claimMilestoneIfDue(), so at the
        // moment the chest is paid the save is already pointing at level 6.
        save.currentLevel = 6
        val chest = save.claimMilestoneIfDue()

        assertEquals(5, chest?.level)
        assertEquals(140L, chest?.coins)
        assertEquals(140L, save.coins)
        assertNull(save.claimMilestoneIfDue())
        assertEquals(10, save.nextMilestoneLevel())
        assertEquals(5, save.levelsUntilMilestone())
    }

    @Test
    fun bossChestsPayDouble() {
        val save = newSave()
        save.currentLevel = 6
        save.claimMilestoneIfDue()

        save.currentLevel = 11
        val bossChest = save.claimMilestoneIfDue()

        assertEquals(10, bossChest?.level)
        assertEquals(400L, bossChest?.coins)
        assertEquals(540L, save.coins)
    }

    @Test
    fun anUpdatedSaveStartsAtTheNextChestInsteadOfOpeningABacklog() {
        // A player already on level 12 has cleared 11 levels. The migration
        // must baseline them at the last passed milestone (10), not pay them
        // for chests they never saw.
        context.getSharedPreferences("blockboom_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("current_level", 12)
            .commit()

        val save = newSave()

        assertEquals(15, save.nextMilestoneLevel())
        assertEquals(4, save.levelsUntilMilestone())
        assertNull(save.claimMilestoneIfDue())
        assertEquals(0L, save.coins)
    }
}
