package ir.amiraligrr.blockboom.data

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Tiny reactive mirrors of SaveManager values so Compose UI (home hearts,
 * shop coins, banner visibility) recomposes when purchases or gameplay change them.
 */
object ProgressState {
    val coins = mutableLongStateOf(0L)
    val isPremium = mutableStateOf(false)

    fun sync(saveManager: SaveManager) {
        coins.longValue = saveManager.coins
        isPremium.value = saveManager.isPremium
    }
}