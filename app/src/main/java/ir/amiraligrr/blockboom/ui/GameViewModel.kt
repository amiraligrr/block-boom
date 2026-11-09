package ir.amiraligrr.blockboom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.data.Achievements
import ir.amiraligrr.blockboom.data.AdAnalytics
import ir.amiraligrr.blockboom.data.LeaderboardManager
import ir.amiraligrr.blockboom.data.PlayerSyncManager
import ir.amiraligrr.blockboom.data.RunSessionManager
import ir.amiraligrr.blockboom.domain.GameEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of the automatic submit that runs when a run ends. */
sealed class SubmitState {
    data object Idle : SubmitState()
    data object Submitting : SubmitState()
    data class Done(val rank: Int?, val message: String) : SubmitState()
    data class Failed(val message: String) : SubmitState()
}

/**
 * An achievement earned at the end of a run, for the game-over screen to show.
 *
 * Published rather than returned from [GameViewModel.submitRunScore] because that
 * call returns before the submit finishes, and the screen that would display it
 * is composed later. The coins are already in the save by the time this is
 * emitted — the award and the announcement are separate steps, and it is the
 * award that must not be lost if nobody is looking at the screen.
 */
data class AchievementUnlock(val emoji: String, val title: String, val coins: Long)

/**
 * Activity-scoped view model so the same engine instance is shared across
 * the Game / Upgrade / GameOver navigation stack without state duplication.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BlockBoomApp

    val engine: GameEngine = GameEngine(app.saveManager)

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _unlocks = MutableStateFlow<List<AchievementUnlock>>(emptyList())
    val unlocks: StateFlow<List<AchievementUnlock>> = _unlocks.asStateFlow()

    fun clearUnlocks() {
        _unlocks.value = emptyList()
    }

    private val achievements = Achievements(app.saveManager)

    init {
        engine.onGameStart = { AdAnalytics.log(application, "game_start", "game") }
        engine.onGameComplete = { AdAnalytics.log(application, "game_complete", "game") }

        // Every score increment is folded into the run's hash chain. The
        // session itself is opened in startRun() below; until it exists this
        // is a no-op, so an offline device is unaffected.
        engine.onScoreGained = { delta ->
            RunSessionManager.active()?.onScore(delta, System.currentTimeMillis())
        }
    }

    private fun openRunSession(level: Int) {
        RunSessionManager.start(getApplication(), level) { }
    }

    /** Always continue from the player's saved level; losing never resets it.
     *  HomeScreen already consumed a life via LivesManager, so this just starts.
     *  Use [startNewGameConsuming] when no prior consumption happened (GameOver). */
    fun startNewGame() {
        engine.beginRun()
        runBanked = false
        engine.startLevel(app.saveManager.currentLevel)
        openRunSession(engine.level)
    }

    /** Consumes a heart (anti-bypass) then starts. Returns false if no lives. */
    fun startNewGameConsuming(): Boolean {
        if (!app.livesManager.canStart()) return false
        if (!app.livesManager.tryConsumeLife()) return false
        // A new run, so the run totals reset. Level is unchanged — retrying is
        // "try again", not "start over" — but the run is a distinct attempt and
        // its stats should not accumulate onto the previous one.
        engine.beginRun()
        runBanked = false
        engine.startLevel(app.saveManager.currentLevel)
        openRunSession(engine.level)
        return true
    }

    fun startNextLevel() {
        engine.startNextLevel()
        openRunSession(engine.level)
    }

    /**
     * Banks the run and submits the lifetime score.
     *
     * This used to fire from a LaunchedEffect nested inside `if (showHeart)`,
     * so the score was only saved if a separate animation happened to flip
     * that flag, and the server's answer was thrown away — a rejected submit
     * looked identical to a successful one. It is now driven from the ViewModel
     * (so it survives the screen being disposed) and its result is published
     * for the UI to show.
     */
    /**
     * True when a finished run has a score that has not reached the server yet
     * because the player had not chosen a name.
     *
     * The game-over screen reads this to decide whether to prompt, and calls
     * [submitRunScore] again once a name exists. It is cleared on success and on
     * explicit dismissal so a name prompt cannot reappear on the next run.
     */
    private val _pendingSubmit = MutableStateFlow(false)
    val pendingSubmit: StateFlow<Boolean> = _pendingSubmit.asStateFlow()

    /**
     * Whether this run's score and statistics have already been folded into the
     * lifetime save.
     *
     * `submitRunScore` is called from two places — once automatically when the
     * game-over screen appears, and again from the name prompt once the player
     * supplies a name — and the second call arrives while the state is still
     * `Idle`, so guarding on [SubmitState.Done] did not stop it. Every
     * `addScore` and `recordRun` therefore ran twice for a run played without a
     * pre-set name, doubling the leaderboard total and every lifetime statistic.
     *
     * This latch is what makes the banking idempotent. It is reset only when a
     * new run begins, so a retry after a failed submit can still reach the
     * server without paying the player twice.
     */
    private var runBanked = false

    /** Called by the game-over name prompt. */
    fun setPlayerName(name: String) {
        app.saveManager.playerName = name
    }

    fun submitRunScore() {
        if (runBanked) return
        runBanked = true

        val runScore = engine.score
        if (runScore > 0) app.saveManager.addScore(runScore)

        // Fold the run into the lifetime totals before the early return below, so
        // a run whose score is waiting on a name still counts. Losing a run is a
        // bad moment to also lose the record of it.
        app.saveManager.recordRun(
            blocks = engine.runBlocksDestroyed,
            shots = engine.runShotsFired,
            coins = engine.runCoinsEarned,
            levelsCleared = engine.runLevelsCleared,
            bestCombo = engine.runBestCombo,
            score = runScore,
            deepestLevel = engine.level,
            playSeconds = engine.runSecondsTotal
        )

        // The daily snapshot and the player's server row are written on every
        // run end, including runs by players who never touch the leaderboard.
        // Fire-and-forget: the game-over screen must not wait on the network.
        PlayerSyncManager.syncNow(force = true)

        // Achievements are checked here for the same reason the totals are
        // folded in above: once per run, before the early return for a player who
        // has not chosen a name yet.
        val unlocked = achievements.collect(
            Achievements.Snapshot(
                runStarted = true,
                runScore = runScore,
                runBestCombo = engine.runBestCombo,
                deepestLevel = engine.level,
                statRuns = app.saveManager.statRuns,
                statLevelsCleared = app.saveManager.statLevelsCleared,
                statBlocks = app.saveManager.statBlocksDestroyed,
                statShots = app.saveManager.statShotsFired,
                statBestCombo = app.saveManager.statBestCombo,
                statCoinsEarned = app.saveManager.statCoinsEarned
            )
        )
        if (!unlocked.isEmpty) {
            _unlocks.value = unlocked.unlocked.map { a ->
                AchievementUnlock(
                    emoji = a.emoji,
                    title = app.getString(a.titleRes),
                    coins = a.coins
                )
            }
        }

        val name = app.saveManager.playerName
        if (name.isBlank()) {
            // Do NOT fail here.
            //
            // A player who finishes a run without having set a name got an error
            // badge on the game-over screen and nothing else: their score was
            // thrown away and there was no way to fix it from where they were.
            // They had to guess that the leaderboard screen had a name prompt,
            // go and find it, and resubmit. Most did not, which is why the admin
            // leaderboard looked empty of names for players who were plainly
            // playing every day.
            //
            // So the run is remembered and the game-over screen asks for the
            // name instead — see [pendingScoreSubmission]. Losing a run is a bad
            // moment to also lose the score for it.
            _pendingSubmit.value = true
            _submitState.value = SubmitState.Idle
            return
        }

        _submitState.value = SubmitState.Submitting
        viewModelScope.launch {
            when (
                val res = LeaderboardManager.submitScore(
                    context = getApplication(),
                    name = name,
                    level = app.saveManager.currentLevel,
                    totalScore = app.saveManager.totalScore,
                    isPremium = app.saveManager.isPremium
                )
            ) {
                is LeaderboardManager.SubmitResult.Success -> {
                    _pendingSubmit.value = false
                    val rank = res.rank
                    _submitState.value = SubmitState.Done(
                        rank = rank,
                        message = when {
                            rank != null && rank > 0 -> "امتیازت ثبت شد — رتبهٔ $rank"
                            res.improved -> "امتیازت ثبت شد"
                            else -> "رکورد قبلیت بهتره"
                        }
                    )
                }
                is LeaderboardManager.SubmitResult.Failed -> {
                    _submitState.value = SubmitState.Failed(res.error.userMessage)
                }
            }
        }
    }

    fun clearSubmitState() {
        _submitState.value = SubmitState.Idle
    }

    fun quitToHome() {
        engine.flushCoins()
        // The run is over without having been submitted, so its server session
        // must not be left open for the next one to inherit.
        RunSessionManager.abandon()
    }

    override fun onCleared() {
        engine.flushCoins()
    }
}
