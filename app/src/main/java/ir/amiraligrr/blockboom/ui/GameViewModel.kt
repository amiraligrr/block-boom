package ir.amiraligrr.blockboom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.data.Achievements
import ir.amiraligrr.blockboom.data.AdAnalytics
import ir.amiraligrr.blockboom.data.GameMode
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

    private fun openRunSession(level: Int, mode: GameMode) {
        RunSessionManager.start(getApplication(), level, mode.name.lowercase()) { }
    }

    private fun beginRun(mode: GameMode, startLevelOverride: Int? = null) {
        app.saveManager.preferredGameMode = mode
        engine.beginRun(mode)
        runBanked = false
        submissionStarted = false
        _pendingSubmit.value = false
        _submitState.value = SubmitState.Idle
        // Each mode has its own starting line. Stage continues the campaign;
        // Boss Fight starts at the next boss the player has not cleared;
        // Record and Time Attack always start from level 1 so the score is
        // comparable between players. A retry after losing a boss passes the
        // boss it actually attempted, so the player does not get sent back to
        // a shallower boss just because the campaign checkpoint is lower.
        engine.startLevel(startLevelOverride ?: startLevelFor(mode))
        if (mode == GameMode.STAGE || mode == GameMode.RECORD) {
            // Only the ranked modes need a start_run token. Boss/Time Attack
            // have no server board yet; opening a token they will never submit
            // just churns run_tokens and logs "abandoned" rows.
            openRunSession(engine.level, mode)
        }
    }

    /**
     * The first level of a run, per mode.
     *
     * Boss Fight starts at the next multiple of ten above the campaign
     * checkpoint, so a player at level 12 meets the level-20 boss instead of
     * replaying levels 1-19. The checkpoint is never written by this mode.
     */
    private fun startLevelFor(mode: GameMode): Int = when (mode) {
        GameMode.STAGE -> app.saveManager.currentLevel.coerceAtLeast(1)
        GameMode.BOSS -> {
            val current = app.saveManager.currentLevel.coerceAtLeast(1)
            val nextBoss = ((current / GameEngine.BOSS_EVERY_N_LEVELS) + 1) *
                GameEngine.BOSS_EVERY_N_LEVELS
            nextBoss.coerceIn(1, 9999)
        }
        GameMode.RECORD -> 1
    }

    /** Starts the selected mode. HomeScreen already consumed a life. */
    fun startNewGame(mode: GameMode = app.saveManager.preferredGameMode) {
        beginRun(mode)
    }

    /** Consumes a heart (anti-bypass) then starts. Returns false if no lives. */
    fun startNewGameConsuming(mode: GameMode = engine.mode): Boolean {
        if (!app.livesManager.canStart()) return false
        if (!app.livesManager.tryConsumeLife()) return false
        // A new run, so the run totals reset. In stage mode retrying is "try
        // again" from the same level; in record mode it is a fresh attempt from
        // level 1, because a record has no checkpoint.
        val bossRetryLevel = if (mode == GameMode.BOSS && engine.mode == GameMode.BOSS) {
            engine.level
        } else null
        beginRun(mode, startLevelOverride = bossRetryLevel)
        return true
    }

    fun startNextLevel() {
        // Do not open a new token per level. A record attempt is one run from
        // level 1 to the death that ends it, and the token opened in beginRun()
        // carries the server-measured play time for the whole attempt. Opening
        // a second token here used to hit "run already open", clear the active
        // session, and force the final submission onto the unverified path.
        engine.startNextLevel()
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

    /**
     * True once the network submission for this run has been started.
     *
     * Separate from [runBanked] because the name prompt calls
     * [submitRunScore] a second time: the first call banks the run, asks for a
     * name and returns; the second must be allowed to send the score. A single
     * latch could not express both "do not pay the player twice" and "the
     * submission has not happened yet", and using it for both silently dropped
     * every unnamed player's score.
     */
    private var submissionStarted = false

    /** Called by the game-over name prompt. */
    fun setPlayerName(name: String) {
        app.saveManager.playerName = name
    }

    fun submitRunScore() {
        if (!runBanked) {
            runBanked = true
            bankRun()
        }

        // Stage runs never reach the leaderboard. The record board is the only
        // board, and it must contain only runs that started at level 1.
        if (engine.mode != GameMode.RECORD) {
            _pendingSubmit.value = false
            _submitState.value = SubmitState.Idle
            return
        }

        // A record must never be lost because the player has not opened the
        // name picker yet. That is what used to happen: the run was banked
        // locally, the prompt was shown, and if the player backed out the score
        // was never sent. Fall back to a guest name and let the player rename
        // themselves later from the leaderboard screen.
        var name = app.saveManager.playerName.trim()
        if (name.isBlank()) {
            name = "بازیکن"
            app.saveManager.playerName = name
        }
        if (submissionStarted) return
        submissionStarted = true

        val runScore = engine.runScore
        val level = engine.level
        _submitState.value = SubmitState.Submitting
        viewModelScope.launch {
            when (
                val res = LeaderboardManager.submitScore(
                    context = getApplication(),
                    name = name,
                    level = level,
                    totalScore = runScore.toLong(),
                    isPremium = app.saveManager.isPremium,
                    mode = "record"
                )
            ) {
                is LeaderboardManager.SubmitResult.Success -> {
                    _pendingSubmit.value = false
                    val rank = res.rank
                    _submitState.value = SubmitState.Done(
                        rank = rank,
                        message = when {
                            rank != null && rank > 0 -> "رکوردت ثبت شد — رتبهٔ $rank"
                            res.improved -> "رکوردت ثبت شد"
                            else -> "رکورد قبلیت بهتره"
                        }
                    )
                }
                is LeaderboardManager.SubmitResult.Failed -> {
                    submissionStarted = false
                    _submitState.value = SubmitState.Failed(res.error.userMessage)
                }
            }
        }
    }

    /**
     * Folds one finished attempt into local totals and achievements.
     *
     * Idempotent through [runBanked]. Record bests are banked by the engine as
     * each level is cleared, so a crash cannot erase a record mid-run; this
     * final update catches the level the player died on.
     */
    private fun bankRun() {
        val runScore = engine.runScore
        if (runScore > 0) app.saveManager.addScore(runScore)
        if (engine.mode == GameMode.RECORD && runScore > 0) {
            app.saveManager.updateRecord(engine.level, runScore)
        }

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
