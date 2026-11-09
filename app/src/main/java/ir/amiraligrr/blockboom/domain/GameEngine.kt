package ir.amiraligrr.blockboom.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.data.Telemetry
import ir.amiraligrr.blockboom.data.UpgradeId
import ir.amiraligrr.blockboom.data.UpgradeManager
import ir.amiraligrr.blockboom.domain.BlockShape
import ir.amiraligrr.blockboom.domain.PowerUpType
import ir.amiraligrr.blockboom.ui.theme.BlockPalette
import ir.amiraligrr.blockboom.ui.theme.BossColor
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonPink
import ir.amiraligrr.blockboom.ui.theme.NeonRed
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure game simulation. All coordinates are in screen pixels, all times in
 * seconds. The instance mutates plain entity lists; the only thing the UI has
 * to copy every frame is a lightweight tick counter.
 *
 * [update] is driven by the Compose frame clock on the main thread, the same
 * thread that walks these lists in the draw pass. Keeping both on one thread is
 * deliberate: the lists are unsynchronised [ArrayList]s, so a background update
 * thread would race the renderer and could hand Compose a torn snapshot.
 * Performance comes from pooling, not from moving the work off-thread.
 *
 * The engine only touches Compose state for the coarse props the HUD reads
 * (status, score, coins, tick).
 */
class GameEngine(
    private val saveManager: SaveManager
) {

    /** Gravity — ultra-fast for هیجانی */
    private val blockGravity = 95f

    /** Bonus cube chance (kept low, main power-ups now come from lucky numbered blocks) */
    private val bonusBlockChance = 0.03f
    /** Chance a normal numbered block becomes a lucky (chance) block */
    private val luckyBlockChance = 0.11f

    /** Seconds a missed bonus cube floats around before quietly expiring. */
    private val bonusLifetime = 25f

    /** How often the power-up HUD mirror is refreshed (seconds). */
    private val POWER_UP_HUD_SYNC_INTERVAL = 0.2f

    /** Buffered mission points before a SharedPreferences write is worth it. */
    private val MISSION_FLUSH_THRESHOLD = 8

    /** Cap on simultaneously alive floating texts. */
    private val MAX_LIVE_POPUPS = 24

    /**
     * Ground bounces a block gets before the last one carries it into the cannon.
     *
     * A budget rather than a damping: every bounce rises to the same height, so
     * this is also how long a block stays on the board after landing.
     */
    private val MAX_GROUND_BOUNCES = 3

    /**
     * Ground bounces an ordinary block gets before the last one carries it into
     * the cannon, tightening with depth.
     *
     * Three everywhere was a flat tax on the player's attention that never grew:
     * a block that survived to the floor at level 3000 got exactly as long a
     * reprieve as one at level 3, and since every other axis was already capped
     * by then it was the last thing still giving deep levels a shape. Two, then
     * one, halves and then quarters the time a block spends hopping.
     *
     * The step points are late on purpose — below level 300 the board is small
     * and fast enough that three hops is the difference between a mistake and a
     * lost run. It is deliberately not applied to the MOTHER, who has no budget
     * at all: she is the level, and a budget would end the run before a player
     * with any upgrades had finished her off. Her consequence is
     * [motherSlam] instead, and it is triggered by her body rather than by a
     * counter.
     */
    private fun bounceBudgetFor(lvl: Int): Int = when {
        lvl < BOUNCE_TIGHTEN_A_LEVEL -> MAX_GROUND_BOUNCES
        lvl < BOUNCE_TIGHTEN_B_LEVEL -> MAX_GROUND_BOUNCES - 1
        else -> 1
    }

    private val BOUNCE_TIGHTEN_A_LEVEL = 300
    private val BOUNCE_TIGHTEN_B_LEVEL = 1200

    /**
     * How high a ground bounce lifts a block, as a fraction of the play field.
     *
     * Fixed, and identical on every bounce — the launch speed is solved from the
     * level's gravity to hit exactly this, so the hop looks the same on level 2
     * and level 400 while getting visibly quicker as gravity rises.
     *
     * ── Why it is a third of the field, and why 15% was wrong ──────────────
     * The rule is unchanged: `v = sqrt(2gh)`, solved fresh from the level's own
     * gravity on every single landing, so the apex is exactly this fraction and
     * nothing decays it. A block that hits the floor comes back to the same
     * height it left at, every time, forever.
     *
     * The fraction itself is what was wrong. At 15% a hop is barely taller than
     * the cannon it has to clear, so a block spent almost its whole cycle
     * hugging the floor — and a block that sits low is a block you cannot get
     * under, cannot shoot from beneath, and can only wait out until it runs out
     * of bounces and takes a heart. Moving the apex up to a third of the field
     * is what makes the vertical space real: the block clears the cannon for
     * most of its airborne time, which is the window the whole game is built
     * around.
     *
     * Taller is also *more* demanding, not less — it covers more of the screen
     * on the way up and the way down, so the player has to shoot around it
     * rather than through it.
     */
    private val BOUNCE_HEIGHT_FRACTION = 0.32f

    /**
     * How long a spent block telegraphs before its last-stand charge.
     *
     * The old behaviour was an endless crawl toward the cannon. That is
     * impossible to read and impossible to escape once it has you in a corner.
     * A fixed 0.78s window is long enough to line up a shot at the block or
     * move out of the lane, and short enough to stay a threat.
     */
    private val LAST_STAND_TELEGRAPH = 0.78f

    /** Speed of the last-stand charge, in dp/s. */
    private val LAST_STAND_CHARGE_SPEED = 250f

    /** A charge that never reaches the cannon or a wall expires after this. */
    private val LAST_STAND_CHARGE_LIFE = 2.6f

    /**
     * The cannon's collision radius as a fraction of its drawn radius.
     *
     * The old test used the full drawn radius, so a block whose visible edge
     * was still a few pixels away could register a hit. 0.68 keeps the
     * collision inside the ball the player can actually see.
     */
    private val CANNON_HITBOX_SCALE = 0.68f

    /** Fraction trimmed from every block edge before testing against the cannon. */
    private val BLOCK_HITBOX_INSET = 0.12f

    /** Coins a single destroyed block may drop, even under COIN RUSH on a boss. */
    private val MAX_COINS_PER_BLOCK = 14

    /**
     * Coins the MOTHER drops, per block her level would otherwise have had.
     *
     * A boss level is one block now (see the MOTHER notes), and coins are paid
     * per destroyed block. Left alone, every tenth level would have paid 5-7
     * coins for two minutes of fighting — the largest level in the game paying
     * nothing, which makes the upgrade track stutter on exactly the levels a
     * player is most engaged on.
     *
     * So she pays what her board would have: the ordinary per-block burst times
     * the block count she replaced. Capped, because every coin is a simulated
     * and drawn entity, and a level-100 boss would otherwise release 490 of them
     * from one explosion.
     */
    private val MOTHER_COINS_PER_BLOCK = 4
    private val MOTHER_MAX_COIN_BURST = 110

    /**
     * Extra gravity (dp/s²) added on top of the per-level ramp, per difficulty
     * tier of ten levels.
     *
     * Kept separate from the per-level term so a tier rollover can only ever add
     * difficulty. When this was folded into the same expression as the per-level
     * term, the reset of `inTier` at each boundary cancelled the step out and
     * gravity *dropped* at levels 11, 21, 31…
     */
    private val TIER_GRAVITY_STEP = 3f

    /** Same idea as [TIER_GRAVITY_STEP], for terminal fall speed. */
    private val TIER_FALL_STEP = 14f

    /**
     * Same idea as [TIER_GRAVITY_STEP], for the HP multiplier.
     *
     * Because the HP multiplier's base ramp runs off `extra` rather than
     * `inTier`, this is a pure additive bonus and nothing can cancel it at a
     * tier boundary — which is the whole point. The multiplier used to carry
     * `inTier * 0.011f` alongside `tier * 0.09f`, so the 0.099 lost to the
     * reset beat the 0.090 gained from the tier step and the multiplier dipped
     * at levels 11, 21, 31...
     */
    private val TIER_HP_STEP = 0.009f

    /** Armour curve against level, before the player's own build is folded in. */
    private val ARMOUR_LEVEL_K = 4.2f
    private val ARMOUR_LEVEL_EXP = 1.2f

    /**
     * How strongly the player's upgrades feed back into armour thickness.
     *
     * See [buildArmourFactor].
     *
     * Solved rather than guessed, against two constraints at once.
     *
     * The ratio: how much thicker a wall a maxed build meets than a weak one.
     * The absolute: how much harder the level cap gets overall, which the design
     * asked to land near 10x rather than at whatever the ratio solution happened
     * to produce. Those pull against each other — a strong feedback ratio is
     * easy to get by making the *level* curve much steeper, which is what the
     * first attempt did, and it put a maxed build on an 18-hour level 9999.
     *
     * At 0.05 / 0.8 the level-9999 board is 10.0x harder for a maxed build and
     * 5.5x for a mid one, the wall a maxed build meets is 3.05x a weak one's,
     * and upgrades still buy 8.2x of speed at level 100 falling to 3.0x at the
     * cap — strong where the player is buying them, and still worth something
     * where they are maxed.
     *
     * Two nearby values were tried and rejected. 1.2 / 0.7 solved the ratio
     * beautifully (a 4.5x wall, 2.0x residual speed) and was 112x on the cap,
     * which is not a difficulty curve. 0.02 / 0.9 only reached 7.7x and left a
     * 4.3x residual speedup, i.e. upgrades nearly stopped mattering.
     */
    private val ARMOUR_BUILD_FEEDBACK = 0.05f
    private val ARMOUR_BUILD_EXP = 0.8f

    /**
     * Ceiling on the HP multiplier.
     *
     * Without it the multiplier compounded forever and, past level 20, HP was the
     * only axis still moving — so late levels were the same level with a bigger
     * number on it. Capping it lets [generateBlockHP]'s own ceiling take over as
     * the real bound, and leaves **armour** as the axis that keeps rising past
     * that, which is the only way a level-5000 board can still differ from a
     * level-1000 one.
     *
     * Was 1.55, reached at level 62. At 2.0 it was reached around level 111,
     * which pushed the saturation point past the level where the upgrade track is
     * still unfinished — and since the upgrade track has since been made harder
     * to finish (DAMAGE capped at 2 and priced out of early reach, several tracks
     * locked behind level), 2.0 flatlined the one axis that was still moving.
     *
     * 2.3 puts the ceiling back around level 140, which is late enough that a
     * board at level 200 is not a level 100 board with the same numbers.
     */
    private val HP_MULTIPLIER_CEILING = 2.3f

    /**
     * Hard ceiling on a single block's HP.
     *
     * ── Why it is a ceiling at all ────────────────────────────────────────
     * `cannon.damage` is the DAMAGE level 1:1 and DAMAGE maxes at 3, but the
     * cannon's *output* keeps climbing through the other tracks: maxed, it does
     * `9.75 shots/s x 5 balls x 3 damage x 8 hits-per-ball` = 1,170 dps against a
     * starting 2.6. HP is bounded so that spread stays meaningful — an unbounded
     * ramp is a number the upgrade track can never catch up to, and the player
     * ends up reading "wait for bigger numbers", which is not a difficulty curve,
     * it is a grind.
     *
     * At 240, a maxed cannon still needs a beat per block (about 0.2s), so the
     * board never erases itself. This was 200 with a cannon that reached 3,861 dps
     * once PIERCE was uncapped — which is the "I played for an hour and now the
     * blocks just fly away" report, and the arithmetic says so exactly.
     *
     * The number is deliberately not large. Past this ceiling difficulty is not
     * supposed to come from bigger digits, it comes from armour plating the
     * player can see, from more blocks arriving faster, and from everything
     * falling sooner.
     */
    val MAX_BLOCK_HP = 240

    // ─────────────────────────────────────────────────────────────────────
    //  The MOTHER — tuning
    //
    //  The fight is ONE block: she is the only thing the level spawns, and the
    //  only thing that spawns anything is her, off the floor.
    //
    //  ── Why she stopped producing on a timer ─────────────────────────────
    //  She used to drop children on a timer, and the level opened with her and
    //  no queue. But `spawnBlockSmart` is reached once per queue tick, and the
    //  mother branch returned *without* consuming a tick — so every remaining
    //  tick of the level's block count spawned another mother, each with its own
    //  spawn timer. A level 10 board was therefore up to `blockCount` mothers,
    //  each dropping up to eight children: the "boss fight" the player met was a
    //  dozen-plus big blockers and a screen full of debris, which is a wall, not
    //  a fight. Nothing in the code said one mother — the intent was one, the
    //  queue said a dozen.
    //
    //  So the queue is now genuinely empty on a boss level (see [startLevel]).
    //  The children came back as *satellites* thrown on each ground bounce (see
    //  [MOTHER_SATELLITES_PER_BOUNCE]): a couple at a time, capped, and on a beat
    //  the player can see — rather than a stream on a timer they cannot.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * The mother's health, in seconds of *this player's* cannon.
     *
     * ── Why it is no longer a function of level alone ─────────────────────
     * It used to be `blockHp * 20 + level * 35`, capped at 120,000. Against a
     * cannon that ranges from 5.2 dps at level 1 to 780 dps fully upgraded —
     * a 150x spread — an absolute number is wrong for almost everyone:
     *
     *   level 10, stock cannon:  890 hp / 5.2 dps  =  171 seconds
     *   level 10, maxed cannon:  890 hp / 780 dps  =  1.1 seconds
     *
     * The first boss was a three-minute wall for the player least equipped to
     * clear it, and a rounding error for the most equipped. That is not a boss
     * fight, it is a paywall or a non-event depending on who is holding the
     * phone, and it is exactly the "the boss has too much life and I cannot
     * hit it" report.
     *
     * So her health is now derived from what the cannon in front of her can
     * actually do, times a target fight length. Every player meets roughly the
     * same *fight*; only the digits differ, and nobody ever has to read them
     * because the ring on her body is a fraction.
     */
    private val MOTHER_TTK_BASE = 3.0f
    private val MOTHER_TTK_PER_ROOT_LEVEL = 0.55f

    /**
     * Ceiling on the target fight length, in seconds.
     *
     * `sqrt` is unbounded, and an endless game means somebody eventually reaches
     * level 9000 — where it would ask for a minute of sustained fire on one
     * block. The ceiling keeps the deepest bosses in the game inside a minute
     * and lets her HP take the rest of the load.
     */
    private val MOTHER_TTK_MAX = 30f

    /**
     * How much of the cannon's ideal output actually lands on her.
     *
     * The ideal figure is `fireRate x balls x damage x hits-per-ball`, which
     * assumes every ball connects. She is wide and slow-moving, so most do —
     * but a fan of five balls at deep level also misses the flatter edges, and
     * calling every ball a hit made her consistently shorter than advertised.
     */
    private val MOTHER_HIT_EFFICIENCY = 0.85f

    /** Hard ceiling on a single boss's HP. Reached nowhere; it is a backstop. */
    private val MOTHER_MAX_HP = 60_000

    /**
     * Satellites the mother throws off the floor, and how many may be alive.
     *
     * She produces nothing on a timer any more — this is the only thing on a boss
     * level that creates anything, and it fires on a moment the player watches:
     * the moment she hits the ground. That makes the fight "shoot her, and deal
     * with what each landing throws at you" instead of "one big number goes down".
     *
     * The cap is what keeps it a fight rather than a wall: past a handful alive
     * the board stops being something you can choose a target on. It sits above
     * the throw rate so that a mother firing twice in a row while the player is
     * busy still produces, rather than silently swallowing both landings — the
     * "she never makes anything" report.
     */
    private val MOTHER_SATELLITES_PER_BOUNCE = 2
    private val MOTHER_MAX_SATELLITES = 6

    /**
     * A satellite's HP, as a fraction of a normal block at the same level.
     *
     * Low on purpose: they exist to be swept up between shots at the boss, not
     * to be fought. Anything close to a real block's health turns each landing
     * into a second fight, and the player has to choose between the boss and the
     * board on every single bounce.
     */
    private val MOTHER_SATELLITE_HP = 0.30f

    /** Coins thrown at the cannon when a NUKE clears the board. */
    private val NUKE_COIN_BURST = 12

    var status by mutableStateOf(GameStatus.Paused)
        internal set

    /** Called when app goes to background - auto-pause without heart loss. */
    fun pauseIfPlaying() {
        if (status == GameStatus.Playing) status = GameStatus.Paused
    }

    var score by mutableIntStateOf(0)

    /**
     * Notified for every score increment, with the delta.
     *
     * The UI points this at the active [ir.amiraligrr.blockboom.data.PlaySession]
     * so the run's event hash chain covers the same numbers the leaderboard
     * will be asked about. Every score mutation goes through [addScore] so
     * there is no path that changes the score without being recorded.
     */
    var onScoreGained: ((delta: Int) -> Unit)? = null

    private fun addScore(delta: Int) {
        if (delta <= 0) return
        score += delta
        onScoreGained?.invoke(delta)
    }

    var coinsInLevel by mutableIntStateOf(0)
        private set

    var level by mutableIntStateOf(1)
        private set

    /** Total blocks that must be destroyed to finish this level. */
    var totalBlocksInLevel by mutableIntStateOf(0)
        private set

    /** Blocks destroyed so far in the current level -> level progress. */
    var blocksDestroyed by mutableIntStateOf(0)

    // ── Run totals ─────────────────────────────────────────────────────────
    //
    // Everything above is per *level*, and a run is however many levels the player
    // chains together, so none of it can answer "how much has this person played".
    //
    // The counters that would answer it existed and were thrown away:
    // [blocksDestroyed] is reset by [startLevel], [shotsFired] is reset by
    // [startLevel], and [ComboTracker.best] is reset there too. The only totals
    // that survived a run were the ones sent to the admin panel — `Telemetry`
    // already collects runs, levels, blocks, coins, shots and max-combo for every
    // install and posts them to the server, so the game knows all of this and
    // shows the player none of it.
    //
    // These are the local mirror of that, kept so the lifetime numbers on the
    // stats screen are real and work offline. They are plain fields, not Compose
    // state: nothing redraws during a run-end, and a snapshot write per kill
    // would be 60 writes a second for a value read once per run.

    /** Blocks destroyed across the whole run. Reset by [beginRun]. */
    var runBlocksDestroyed = 0
        private set

    /** Shots fired across the whole run. Reset by [beginRun]. */
    var runShotsFired = 0
        private set

    /** Best combo reached at any point in the run. Reset by [beginRun]. */
    var runBestCombo = 0
        private set

    /** Coins credited across the whole run. Reset by [beginRun]. */
    var runCoinsEarned = 0
        private set

    /** Levels finished since the run began. Reset by [beginRun]. */
    var runLevelsCleared = 0
        private set

    /**
     * Starts a fresh run's tallies.
     *
     * Deliberately separate from [startLevel], which runs for both "the player
     * pressed play" and "the player finished a level" — folding the reset in
     * there would zero the totals on every stage clear and leave every run
     * reporting exactly one level.
     */
    fun beginRun() {
        runBlocksDestroyed = 0
        runShotsFired = 0
        runBestCombo = 0
        runCoinsEarned = 0
        runLevelsCleared = 0
        runSecondsTotal = 0f
    }

    /**
     * Whether the paid rescue has been used on this level.
     *
     * Per level, not per run. Once per level is what keeps it a rescue rather
     * than a substitute for playing: it cannot be chained to farm, and it
     * cannot turn a run into an infinite sequence of watched adverts. Reset in
     * [startLevel] along with everything else about the stage.
     */
    var reviveUsedThisLevel = false
        private set

    // The daily challenge used to live here: a per-day board hash (`challengeSalt`)
    // plus a `challengeMode` flag consulted at every point that writes campaign
    // state. Both are gone with the mode itself. See [LevelModifier.forLevel],
    // which is deterministic in the level number alone now.

    /** Smallest number of blocks a revive will clear, however small the board. */
    private val reviveMinClear = 6

    /** Fraction of the on-screen board a revive removes. */
    private val reviveClearFraction = 0.4f

    /** Seconds the run has been playing, from the same clock the combo uses. */
    val runSeconds: Float get() = gameElapsed

    /**
     * Seconds the *whole run* has been playing, across every level in it.
     *
     * [runSeconds] reads [gameElapsed], and `startLevel` zeroes that — it is a
     * per-level clock, which is what the combo window and the level-time
     * telemetry want. But it is also what `recordRun` was handed as the run's
     * play time, so a player who chained six levels and then lost had only the
     * last level's seconds banked: the lifetime "total play time" on the stats
     * screen came out at roughly a sixth of the truth for most players.
     *
     * Accumulated separately, from the same `dt`, and reset by [beginRun]
     * alongside the other run totals. It only advances while the game is
     * actually running, because `update` returns early otherwise.
     */
    var runSecondsTotal = 0f
        private set

    /**
     * How many times the cannon has fired this level.
     *
     * Snapshot state, and the reason is the same as [Cannon.targetX]: `balls`
     * is a plain `ArrayList`, so a UI cannot ask "has a shot gone out yet?" —
     * reading `balls.size` from a composable registers no observation and the
     * question would silently always be answered from a stale value. The
     * in-game coach needs exactly that signal, and this is it.
     */
    var shotsFired by mutableIntStateOf(0)
        private set

    /**
     * How many times the player has actually dragged the cannon.
     *
     * The coach's first step is "move the cannon", and it used to gate on
     * `cannon.targetX != 0f`. That condition is true before the player has
     * touched anything: [setFieldSize] centres the cannon on the first layout, so
     * `targetX` is `width/2` from the first frame. The step therefore advanced
     * after 500ms having verified nothing, and the one instruction a brand-new
     * player most needs was the one they were most likely to have skipped.
     *
     * A counter is the only honest signal here — the position the cannon ends up
     * at is the same either way, because the engine snaps it to the centre, so
     * "did it move?" cannot be answered by looking at where it is. It resets with
     * [startLevel] alongside [shotsFired] and [score], so a retried level
     * re-teaches from the top.
     */
    var cannonMoves by mutableIntStateOf(0)
        private set

    /**
     * Every coin the player earns goes through here.
     *
     * It existed only as two `coinsInLevel += n` statements in two different
     * methods, which is exactly the shape that produces a coin total the
     * monitoring panel quietly disagrees with: a third earn path (a lucky block,
     * a coin-rush magnet) would have been added later without a counter.
     */
    private fun addCoinsEarned(n: Int) {
        if (n <= 0) return
        coinsInLevel += n
        runCoinsEarned += n
        coinsSinceSave += n
        Telemetry.addCoins(n)
    }

    /** Bumped once per rendered frame on the main thread -> recompose trigger. */
    var tick by mutableLongStateOf(0L)
        private set

    /**
     * Monotonic seconds since the engine was created, for render-side animation.
 *
     * [tick] is a frame *counter*, so multiplying it by a per-second rate is a
     * unit error: at 60fps `tick * 0.001f` advances by 0.06 per second rather
     * than 1.0, and every animation whose coefficient is written in per-second
     * units runs about 16.7x slow. The backdrops, the cloud drift and the star
     * twinkle were all built on that mistake — a petal that should cross the
     * field in twenty seconds took four minutes, which reads as a frozen screen.
     *
     * Measured from `System.nanoTime()` rather than accumulated from the
     * simulation's `dt`, because this has to keep advancing while the game is
     * paused (`update` returns early then, and the backdrop is still on screen
     * behind the pause overlay) exactly as [tick] did.
     */
    var clockSeconds by mutableFloatStateOf(0f)
        private set

    private var lastFrameNanos = 0L

    /** Called from the main thread after each frame; triggers canvas redraw. */
    fun onFrameRendered() {
        tick++
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            // Clamped like the simulation's dt: a long stall (GC, resume) must
            // not teleport the backdrop by minutes on the next frame.
            clockSeconds += ((now - lastFrameNanos) / 1_000_000_000.0)
                .toFloat().coerceIn(0f, 0.1f)
        }
        lastFrameNanos = now
    }

    var fieldWidth: Float = 0f
        private set
    var fieldHeight: Float = 0f
        private set

    private var density = 1f

    val cannon = Cannon()
    val balls = ArrayList<Ball>(32)
    val blocks = ArrayList<Block>(48)
    val coins = ArrayList<Coin>(32)
    val bonuses = ArrayList<BonusBlock>(8)
    val powerUpDrops = ArrayList<PowerUpDrop>(8)
    val particles = ArrayList<Particle>(96)
    val explosions = ArrayList<Explosion>(8)
    val popups = ArrayList<PopupText>(16)
    val stars = ArrayList<Star>(70)

    /** Active power-ups with their remaining time, read by the HUD timer bar. */
    val activePowerUps = mutableStateMapOf<PowerUpType, Float>()

    /**
     * SHIELD charges held, not a timer.
     *
     * Snapshot state so the HUD can show "🛡️ x2" without the HUD having to poll,
     * and so spending one visibly updates. Reset per level along with everything
     * else — a shield is a save for the level in front of you, and carrying one
     * across levels would make it a permanent second life.
     */
    var shieldsHeld by mutableIntStateOf(0)
        private set

    /** True while GHOST is running: balls cannot be destroyed by a block. */
    val isGhost: Boolean get() = hasPowerUp(PowerUpType.GHOST)

    /**
     * Authoritative remaining time per power-up, indexed by ordinal. The
     * snapshot map above is only a mirror for the HUD: writing to it every
     * frame forced a snapshot mutation 60x/second for something that displays
     * whole seconds, so it is synced a few times a second instead.
     */
    private val powerUpRemaining = FloatArray(PowerUpType.entries.size)
    private var powerUpSyncTimer = 0f

    /** True while [type] is running. Allocation-free — safe in the frame loop. */
    fun hasPowerUp(type: PowerUpType): Boolean = powerUpRemaining[type.ordinal] > 0f

    /**
     * Seconds left on [type], or 0 if it is not running. Public so the canvas can
     * draw the persistent full-screen overlay — the ice, the heat haze — whose
     * intensity has to fade out with the power-up rather than just blinking.
     */
    fun powerUpSecondsLeft(type: PowerUpType): Float = powerUpRemaining[type.ordinal]

    /** Decays 1 -> 0 after a power-up triggers; canvas draws a colored flash. */
    var powerUpFlash by mutableFloatStateOf(0f)
        private set
    var powerUpFlashColor by mutableStateOf(Color.Transparent)
        private set

    /** Decays 1 -> 0; canvas uses it to shake the whole play field. */
    var shake by mutableFloatStateOf(0f)
        private set

    /**
     * Fired on the main thread for audio-reactive moments. The UI layer wires
     * this to [ir.amiraligrr.blockboom.data.SoundManager]; the engine itself
     * never touches Android audio so the simulation stays pure & testable.
     */
    var onSound: ((SoundEvent) -> Unit)? = null
    var onGameStart: (() -> Unit)? = null
    var onGameComplete: (() -> Unit)? = null
    var onBossFight: (() -> Unit)? = null

    /** The twist in force for the current level. Read by the HUD and the field. */
    var levelModifier by mutableStateOf(LevelModifier.NONE)
        private set

    /**
     * Seconds played in the current level.
     *
     * Snapshot state so the HUD can turn the level's announcement card into a
     * compact chip a few seconds in, and back to a full card on the next level,
     * without the screen having to own a timer of its own.
     */
    var levelElapsed by mutableFloatStateOf(0f)
        private set

    private val particlePool = ParticlePool()
    private val coinPool = CoinPool()
    private val popupPool = PopupTextPool()

    private var pendingMissionBlocks = 0
    private var pendingMissionCoins = 0
    private var pendingMissionLucky = 0
    private var pendingMissionTotal = 0

    private val rand = Random(System.nanoTime())

    /**
     * The queue of palette entries still to be dealt out this level.
     *
     * Colour used to be a function of the block's HP, and HP is nearly constant
     * across a level — so every block on the field came out the same colour and
     * the board read as one flat mass. Drawing from a shuffled deck instead
     * means each new block takes the next entry and no entry repeats until the
     * whole palette has been used, which is what makes a busy board readable.
     *
     * It is refilled from [refillColorDeck] the moment it empties, so a long
     * level keeps cycling without ever repeating back-to-back.
     */
    private val colorDeck = ArrayDeque<Int>()

    /**
     * The last palette index handed out.
     *
     * Kept so a refill can guarantee the next block is not the same colour as
     * the last one. Without it, the final entry of an exhausted deck and the
     * first entry of the fresh shuffle collide often enough to be visible —
     * two identically coloured blocks falling in line, which is precisely the
     * "duplicate colour" this deck exists to prevent.
     */
    private var lastColorIndex = -1

    private fun refillColorDeck() {
        val next = BlockPalette.indices.toList().shuffled(rand)
        colorDeck.clear()
        // Rotate so the new head is never the colour we just dealt.
        if (next.size > 1 && next[0] == lastColorIndex) {
            colorDeck.addAll(next.subList(1, next.size))
            colorDeck.add(next[0])
        } else {
            colorDeck.addAll(next)
        }
    }

    /** The palette index for a newly spawned block. Never repeats until the deck is exhausted. */
    private fun nextBlockColorIndex(): Int {
        if (colorDeck.isEmpty()) refillColorDeck()
        val index = colorDeck.removeFirst()
        lastColorIndex = index
        return index
    }
    /**
     * The live combo chain.
     *
     * Public because the HUD meter and the telemetry reporter both need to read
     * it; both only *observe* it. Mutations still go through the engine, which
     * is the only thing that knows the simulation clock.
     */
    val combo = ComboTracker(windowSeconds = COMBO_WINDOW_SECONDS)

    private var gameElapsed = 0f

    /**
     * Simulation time at which the current level began.
     *
     * Elapsed times are measured against this rather than the wall clock
     * because `gameElapsed` is clamped per frame and pauses with the game: a
     * player who backgrounds the app for ten minutes does not report a
     * ten-minute level, which is what made "average session length" on the
     * monitoring panel meaningless.
     */
    private var levelStartedElapsed = 0f

    /** Wall-clock start of the current run, for the run_end event. */
    private var levelStartedAtMs = 0L

    private fun levelElapsedMs(): Int =
        ((gameElapsed - levelStartedElapsed) * 1000f).toInt().coerceAtLeast(0)
    private var fireCooldown = 0f
    private var blocksToSpawn = 0
    private var nextSpawnIn = 0.8f
    private var spawnInterval = 1f
    private var spawnTicking = false

    /**
     * Post-revive grace. While positive, ordinary blocks cannot end the run.
     *
     * Revive clears the lowest blocks and grants a shield, but a fast board
     * could still land a new block on the cannon before the player had
     * re-oriented. This is the same promise the shield makes, applied to the
     * frames immediately after the rescue.
     */
    private var hitGrace = 0f

    /**
     * A boss level's mother is waiting to be spawned.
     *
     * Set by [startLevel] and cleared by the first [update] that has a field to
     * size her against. She cannot be spawned inside [startLevel] itself: her
     * size and position are derived from the play field's width, and that is not
     * known until the canvas has been measured — which happens *after* the level
     * is started, because starting a level is what navigates to the screen that
     * does the measuring. Spawning there gave her a width of zero on the first
     * level of a fresh process: an invisible, unkillable boss.
     */
    private var motherPendingSpawn = false
    private var coinsSinceSave = 0L

    /** Fall gravity / terminal velocity for the running level, in dp. */
    private var levelGravity = 52f
    private var levelMaxFall = 300f

    /**
     * The running level's config, cached by [startLevel].
     *
     * [levelConfig] is deterministic in the level number and runs several
     * `pow`/`ln`/`sqrt` per call. It used to be called on every block spawn and
     * every coin collection — the same arithmetic, the same answer, a level's
     * times over. Set once per level in [startLevel]; read everywhere else.
     */
    private var levelCfg: LevelConfig = LevelConfig(
        blockCount = 0, gravity = 52f, maxFall = 300f, spawnInterval = 1.15f,
        hpMultiplier = 1f, armourBudget = 0, boss = false, isMotherLevel = false
    )

    // ---------------------------------------------------------------- setup

    fun setFieldSize(width: Float, height: Float, density: Float) {
        if (width <= 0f || height <= 0f) return
        val firstLayout = fieldWidth <= 0f
        fieldWidth = width
        fieldHeight = height
        this.density = density

        cannon.radius = dp(26f)
        cannon.baseY = height - dp(96f)
        if (firstLayout) {
            cannon.x = width * 0.5f
            cannon.targetX = cannon.x
        } else {
            // Keep the player's cannon where it is; only clamp to the new bounds.
            cannon.x = cannon.x.coerceIn(cannon.radius, width - cannon.radius)
            cannon.targetX = cannon.targetX.coerceIn(cannon.radius, width - cannon.radius)
        }

        refreshCannonStats()
        if (stars.isEmpty()) generateStars()
    }

    /**
     * Pulls the latest upgrade levels into the cannon so purchases apply
     * immediately, even mid-level. Called on setup, level start and after a
     * purchase from the upgrade screen.
     *
     * ── Every level is clamped to its cap ─────────────────────────────────
     * Not belt-and-braces. A stored level can be *above* the current cap on any
     * install that predates a balance change, because nothing rewrites saved
     * levels when a max is lowered — the shop just hides the row behind "MAX".
     * Reading the raw stored number here meant players kept the old top end: the
     * cannon still fired twelve balls and still pierced eleven blocks long after
     * the catalogue said the caps were five and seven. A rebalance that only
     * edits the shop is a rebalance that does not ship.
     *
     * The caps themselves come from [UpgradeManager] so there is exactly one
     * number per track in the codebase.
     */
    fun refreshCannonStats() {
        cannon.damage = UpgradeManager
            .clampLevel(UpgradeId.DAMAGE, saveManager.upgradeDamage).coerceAtLeast(1)
        cannon.fireRate = UpgradeManager.fireRateFor(
            UpgradeManager.clampLevel(UpgradeId.FIRE_RATE, saveManager.upgradeFireRate)
        )
        cannon.multiBall = UpgradeManager
            .clampLevel(UpgradeId.MULTI_BALL, saveManager.upgradeMultiBall).coerceAtLeast(1)
        cannon.pierce = UpgradeManager
            .clampLevel(UpgradeId.PIERCE, saveManager.upgradePierce)
        cannon.bulletSpeed = dp(38f) * UpgradeManager
            .clampLevel(UpgradeId.BULLET_SPEED, saveManager.upgradeBulletSpeed)
        cannon.magnetRadius = dp(UpgradeManager.MAGNET_DP_PER_LEVEL) * UpgradeManager
            .clampLevel(UpgradeId.MAGNET, saveManager.upgradeMagnet)

        buildPowerIndex = cannonPowerIndex()
    }

    /**
     * This player's cannon output, relative to a starting cannon. 1.0 = the
     * cannon you begin the game with, so every upgrade bought moves it up.
     *
     * Read by [levelConfig] to scale armour. It is the input that makes
     * difficulty a function of the *build* as well as the level number, which is
     * the whole point: a maxed cannon has 300x the output of a starting one, and
     * a difficulty curve that only reads the level hands that 300x straight back
     * to the player as "this level is easy". Measured, the old curve grew
     * difficulty by exactly 8.0x between level 200 and level 9999 *for every
     * build alike* — upgrades translated one-for-one into ease, and the upgrade
     * track stopped being a difficulty axis at all.
     */
    private var buildPowerIndex = 1f

    private fun cannonPowerIndex(): Float {
        // mirrors `dps`: rate x balls x damage x hits-per-ball
        val start = UpgradeManager.FIRE_RATE_BASE * 1f * 1f * 1f
        val now = cannon.fireRate * cannon.multiBall * cannon.damage *
            (cannon.pierce + 1).coerceAtLeast(1)
        return (now / start).coerceAtLeast(1f)
    }

    /**
     * How much thicker this level's armour is because of how strong the player's
     * cannon is.
     *
     * A multiplier on the level's own armour curve, not a flat addition, so it
     * is worth nothing on the early levels that have no armour yet and it scales
     * with the curve rather than running away from it.
     *
     * The exponent is below 1 on purpose. At 1.0 the challenge would be exactly
     * constant — every upgrade fully cancelled — and the shop would sell nothing
     * that changes how the game plays. At 0.5 a maxed cannon's 300x of output
     * buys it about 17x of extra plating, so upgrades are still worth roughly
     * 4x of speed while the wall keeps rising underneath.
     */
    private fun buildArmourFactor(): Float =
        1f + ARMOUR_BUILD_FEEDBACK * (buildPowerIndex - 1f).pow(ARMOUR_BUILD_EXP)

    private fun dp(value: Float): Float = value * density
    private fun randomFloat(from: Float, to: Float): Float =
        from + rand.nextFloat() * (to - from)

    private fun generateStars() {
        stars.clear()
        repeat(70) {
            stars.add(
                Star(
                    x = rand.nextFloat() * fieldWidth,
                    y = rand.nextFloat() * (fieldHeight - dp(120f)),
                    radius = randomFloat(dp(1f), dp(2.6f)),
                    twinklePhase = rand.nextFloat() * 6.2832f
                )
            )
        }
    }

    // ---------------------------------------------------------------- levels

    fun startLevel(nextLevel: Int) {
        level = clampLevel(nextLevel)
        // The one place campaign progress is written.
        saveManager.currentLevel = level

        status = GameStatus.Playing
        score = 0
        coinsInLevel = 0
        gameElapsed = 0f
        fireCooldown = 0f
        coinsSinceSave = 0L
        refreshCannonStats()

        // Shields are for the level in front of you. Carrying one across levels
        // would make it a permanent extra life, which is not what the power-up
        // says it is. Same reasoning for the mercy reprieve.
        shieldsHeld = 0
        levelElapsed = 0f

        balls.clear()
        blocks.clear()
        coins.clear()
        bonuses.clear()
        powerUpDrops.clear()
        particles.clear()
        explosions.clear()
        popups.clear()
        activePowerUps.clear()
        // A fresh palette order every level, so consecutive levels don't open
        // with the same colour in the same corner of the field.
        refillColorDeck()
        powerUpRemaining.fill(0f)
        powerUpSyncTimer = 0f
        powerUpFlash = 0f
        shake = 0f
        combo.reset()

        val cfg = levelConfig(level)
        // Cached for the level, not recomputed per spawn and per coin drop.
        // levelConfig runs several pow/ln/sqrt per call; it was called on every
        // block spawn and every coin collection, which is a level's worth of
        // identical arithmetic spent on a value that cannot change mid-level.
        levelCfg = cfg
        // Resolve the twist before anything is spawned, so every block on the
        // board is consistent with the card the player is about to be shown.
        levelModifier = LevelModifier.forLevel(level, cfg.isMotherLevel)
        // ── The boss level's queue is empty, and it is emptied *here* ────────
        //
        // A boss level used to keep the ordinary `blockCount` queue and open with
        // the mother on top of it. `spawnBlockSmart` then handed every remaining
        // tick of that queue back to `spawnMother`, so the fight was a dozen
        // mothers rather than one — see the MOTHER tuning notes above.
        //
        // The level is now one spawn and no queue, which also means
        // `checkLevelComplete`'s ordinary conditions are what end it: kill her
        // and the board is genuinely empty. The progress bar reads 0/1 → 1/1
        // rather than 3/14, because on this level there is exactly one thing to
        // destroy and the bar has to say so.
        blocksToSpawn = if (cfg.isMotherLevel) 0 else cfg.blockCount
        totalBlocksInLevel = if (cfg.isMotherLevel) 1 else cfg.blockCount
        blocksDestroyed = 0
        shotsFired = 0
        // Reset with the rest, so the coach re-teaches "move the cannon" on a
        // retried level instead of carrying a count from the previous attempt.
        cannonMoves = 0
        reviveUsedThisLevel = false
        nextSpawnIn = 1.4f
        // RUSH gets *worse* with depth, on a curve that flattens out.
        //
        // A flat 0.55 factor made a RUSH level at 10 and a RUSH level at 900 the
        // same fight wearing different numbers. Scaling toward 0.34 as the level
        // grows keeps the modifier meaningful forever — the same rule, but the
        // gap you have to hold tightens — while the flattening stops it from
        // ever becoming an unsurvivable wall of simultaneous blocks.
        spawnInterval = if (levelModifier == LevelModifier.RUSH) {
            val depth = (level / 400f).coerceAtMost(1f)
            cfg.spawnInterval * (0.55f - 0.21f * depth)
        } else cfg.spawnInterval
        // Off on a boss level, and it has to be: `checkLevelComplete` requires
        // `!spawnTicking` as well as an empty queue, and a boss level's queue was
        // never going to tick itself down to zero — nothing spawns on it — so
        // leaving this true made killing her the last thing that happened and
        // never the thing that ended the level.
        spawnTicking = !cfg.isMotherLevel
        levelGravity = cfg.gravity
        levelMaxFall = cfg.maxFall

        // The boss is the level: there is nothing else coming, so she is spawned
        // as soon as there is a field to size her against rather than on the
        // spawner's first tick. See [motherPendingSpawn].
        motherPendingSpawn = cfg.isMotherLevel

        Telemetry.addRun()
        Telemetry.track("run_start", level = level)
        Telemetry.flush()
        levelStartedAtMs = System.currentTimeMillis()
        levelStartedElapsed = gameElapsed
        onGameStart?.invoke()
    }

    fun startNextLevel() {
        startLevel(level + 1)
    }

    private fun clampLevel(lvl: Int): Int = lvl.coerceIn(1, 9999)

    private fun levelConfig(lvl: Int): LevelConfig {
        val extra = (lvl - 1).coerceAtLeast(0)

        // Difficulty ramps in stages rather than smoothly, so there is a
        // felt "wall" every ten levels that upgrades visibly help punch
        // through. A smooth ramp was too gentle to notice.
        //
        // ── How the tier step is applied ──────────────────────────────────
        // `tier * TIER_STEP` used to be added directly to a value that also
        // carried `inTier * PER_LEVEL`. At every tier boundary `inTier` resets
        // from 9 to 0, so the +9 the tier step contributed was immediately
        // clawed back by -14.4 from the reset: gravity fell 5.4 units at levels
        // 11, 21, 31, 41… Every tenth level was *easier* than the one before
        // it, which is the exact opposite of the wall this comment promises.
        //
        // The fix is to make the two terms independent of each other: `extra`
        // already counts every level and only ever increases, so the base ramp
        // uses it, and the tier term is a pure additive bonus on top. Neither
        // term can now decrease as the level goes up.
        val tier = extra / 10
        val inTier = extra % 10

        // Each axis saturates eventually — anything that only rises and stops
        // rising makes late levels differ from each other by nothing but a
        // bigger number. blockCount and spawnInterval both used to flatline
        // (at level 13 and 33), which meant levels 34-115 changed nothing at
        // all. They now keep moving, with the growth deliberately flattening so
        // the pace stays survivable.
        //
        // 34 + 3 per 6 levels, so a level runs about 46 blocks at level 30 and
        // 80 by level 100. It was capped at 30, which made a late level a
        // half-minute of shooting — too short to feel like progress, and short
        // enough that coin income could not keep up with the HP ramp.
        //
        // Capped, and deliberately so. Uncapped this reached 1033 blocks at
        // level 2000: a field you cannot see across, that no cannon can clear,
        // and that costs more to simulate every frame than the device can give.
        // Past this ceiling the level stops adding bodies and starts adding
        // *character* instead — see [modifierFor], which keeps widening, and the
        // armour ramp, which keeps thickening. More blocks was never the honest
        // way to make a level harder anyway; it just made it slower.
        //
        // Raised again, because a level was still over in well under a minute
        // from about level 60 on: 46 blocks at ~2.6 hits each is not a stage,
        // it is a chore, and the only thing it bought was more frequent
        // interstitials. The base runs to 40 and the tier bonus to 4 per 6
        // levels, so level 30 is ~54 blocks and level 100 is ~106 before the
        // ceiling. Held longer, not merely bigger — the depth ramp is unchanged,
        // so the extra bodies arrive at the same pace rather than all at once.
        //
        // Raised once more, and this time for the opposite reason: the board
        // now fills gradually (see [maxBlocksAlive] — eight to sixteen alive at
        // once, never twenty), so a level of the same length is a level the
        // player can win without ever being pressed. The queue is what carries
        // the challenge back: the base runs to 40 and the tier bonus to 5 per
        // 5 levels, so a level is a longer fight at the same width. Gradual
        // arrival, not a smaller level — the two were being traded against
        // each other, and the trade was being won by ease.
        val blockCount = (minOf(8 + extra * 2, 40) + (extra / 5) * 3)
            .coerceAtMost(MAX_BLOCKS_PER_LEVEL)

        // Fall gravity ramps with the level instead of being flat. At a single
        // hard-coded 95dp/s^2 a level-1 block crossed the whole field in under
        // four seconds, so two hearts were gone within ~12s of the first run —
        // new players bounced before they ever saw a block destroyed.
        //
        // Monotonic by construction: `extra` rises every level and TIER_STEP is
        // positive, so this can never dip at a tier boundary.
        //
        // Both caps were reached around level 70, after which falling blocks
        // behaved identically at level 150 and level 2000. The ceilings are now
        // far enough out that a player has to be *very* deep before the curve
        // visibly flattens, and the term below keeps a slow upward creep past
        // the old wall so late levels still feel faster rather than merely
        // bigger. Logarithmic, not linear: a linear term that still moved at
        // level 2000 would be 3000+ dp/s^2, which is not difficulty, it is a
        // wall the player reads through instantly and cannot answer.
        val lateCreep = (ln(1f + extra / 22f) * 8f)
        val gravity = (52f + extra * 1.45f + tier * TIER_GRAVITY_STEP + lateCreep)
            .coerceAtMost(MAX_GRAVITY)
        val maxFall = (280f + extra * 4.5f + tier * TIER_FALL_STEP + lateCreep * 5f)
            .coerceAtMost(MAX_FALL_SPEED)

        // Blocks spawn faster late-game — هیجانی
        //
        // The old floor of 0.32s was hit at level 33, so from there on the spawn
        // rhythm was constant and only the count moved. Dropped to 0.18s: still
        // well above the point where blocks arrive faster than the cannon can
        // service them (which reads as unfair rather than hard), but low enough
        // that a late level has a visibly tighter rhythm than a mid one.
        val spawnInterval = (1.2f - extra * 0.022f).coerceIn(MIN_SPAWN_INTERVAL, 1.2f)

// ── HP, and why it is capped in *balls to kill* rather than in HP ──
        //
        // Built the same way as gravity above, and for the same reason: the base
        // ramp runs off `extra`, which only ever increases, and the tier term is
        // a pure additive bonus on top.
        //
        // It used to read `1 + tier * 0.09 + inTier * 0.011`, carrying the same
        // defect gravity had before it was fixed a few lines up. `inTier` resets
        // 9 -> 0 at each tier boundary, so the bonus the tier step contributed
        // (0.090) was immediately clawed back by the reset (9 x 0.011 = 0.099):
        // the multiplier *fell* by 0.009 at every single boundary. Levels 11,
        // 21, 31 ... 9991 — 999 of them — were slightly easier than the level
        // before, on an axis whose entire stated purpose is the felt wall every
        // ten levels.
        //
        // The coefficients are rebalanced to hold the curve's shape: 0.0085 per
        // level plus 0.009 per tier reaches the 2.3 ceiling at level 141, where
        // the old form reached it at 145 and HP_MULTIPLIER_CEILING's own comment
        // says "around level 140". The largest deviation anywhere in the game is
        // 0.05 on a multiplier that spans 1.0 to 2.3.
        val perLevel = 1f + extra * 0.0085f + tier * TIER_HP_STEP
        val hpMultiplier = perLevel.coerceAtMost(HP_MULTIPLIER_CEILING)

        // The old ramp was a piecewise-linear function with no ceiling, while
        // `cannon.damage` is literally the DAMAGE upgrade level and that upgrade
        // maxes out at 20. So past level 8 a single ball could never one-shot
        // anything, and by level 50 a block needed 11 balls from a player whose
        // damage was already maxed — the UI said "MAX" while asking for more.
        // Once past ~level 20 the only axis still moving was HP, so the game was
        // "wait for bigger numbers", which is not a difficulty curve, it is a
        // grind.
        //
        // HP is now derived from a target **shots-to-kill** that stays inside a
        // readable band: about 2 balls when fully upgraded, so upgrading always
        // visibly shortens every kill, and never more than a handful for a
        // player who has not invested. Difficulty then comes from pressure —
        // more blocks, faster fall, tighter spawn gaps — which is what a player
        // can actually see and respond to, instead of from a number that
        // silently outgrew the upgrade track.

        // ── The endless axis: armour thickness ───────────────────────────
        //
        // Zero until level 25. It used to start at level 50, on the reasoning that
        // armour only matters once HP saturates — but HP saturates at 240 against
        // a cannon that reaches several hundred dps long before that, so the first
        // quarter of the game had no toughness axis at all and every level in it
        // was the same board with a bigger number.
        //
        // This is now the *only* axis that keeps rising, and it has to be:
        // block count, HP, the HP multiplier, gravity, fall speed and spawn rate
        // all reach their ceilings between level 60 and level 111. Past that the
        // old curve gave level 300 and level 3000 the same board — measured, a
        // maxed player cleared both in the same ~35 seconds, and the whole rest
        // of the game was one number getting bigger.
        //
        // Armour is the right axis for the tail because it is the one that costs
        // the player something real rather than asking them to read a longer
        // number. It is drawn as plating on the block, it is consumed before HP by
        // every hit that carries PIERCE (so upgrades keep mattering however thick
        // it gets), and it scales without ever becoming unreadable.
        //
        // `((extra - 24) / 4.2)^1.2` reaches roughly 13 armour at level 50, 76 at
        // 100, 470 at 300 and 3,900 at 1,000 — which keeps the endgame a fight
        // while starting the axis a quarter of the way in instead of halfway.
        // The previous `(extra-49)/16)^0.8` reached 171 at level 9,999, which is
        // noise against 240 HP.
        //
        // Still sub-linear in practice: the exponent is above 1, so the curve is
        // convex, but it is applied to a small divisor so the absolute values stay
        // inside the range where a block is still killable in seconds rather than
        // in minutes. Difficulty keeps moving without ever outrunning the
        // player's ability to respond to it — that is the whole design brief for
        // an endless game in one line.
        val armourBudget = if (extra < 24) 0 else
            (((extra - 24) / ARMOUR_LEVEL_K).pow(ARMOUR_LEVEL_EXP) * buildArmourFactor()).toInt()

        // ── Boss fights every 10 levels, not every 5 ─────────────────────
        // Every fifth level made half the game a boss level, so the fight stopped
        // being an event and became the default texture of the game. Every tenth
        // is roughly where the "wall" the tier system already implies lands, so
        // the fight and the difficulty step happen together and a player who
        // cleared nine levels knows a boss is coming.
        val bossLevel = lvl % BOSS_EVERY_N_LEVELS == 0

        return LevelConfig(
            blockCount = blockCount,
            gravity = gravity,
            maxFall = maxFall,
            spawnInterval = spawnInterval,
            hpMultiplier = hpMultiplier,
            armourBudget = armourBudget,
            boss = bossLevel,
            isMotherLevel = bossLevel
        )
    }

    private data class LevelConfig(
        val blockCount: Int,
        val gravity: Float,
        val maxFall: Float,
        val spawnInterval: Float,
        /** Difficulty tier multiplier applied to block HP. */
        val hpMultiplier: Float,
        /**
         * Extra hit-points of armour to hand out on this level, over and above
         * what [LevelModifier.ARMOR] would give on its own.
         *
         * This is the axis that makes the game endless.
         *
         * HP has to stop somewhere: `MAX_BLOCK_HP` is 200 because a maxed cannon
         * does ~330 dps, and a block above that is not a bigger block, it is a
         * number the upgrade track can never catch up to. Past that ceiling the
         * only honest place left to put difficulty is **armour** — and armour
         * scales further, because it is consumed *before* HP by every hit that
         * carries PIERCE, so the upgrade track keeps mattering no matter how
         * thick it gets.
         *
         * It also reads better. A block with 400 HP is just a big number; a
         * block with visible armour plating is something you can see and
         * strategise around, which is what keeps a deep level interesting
         * instead of merely long.
         */
        val armourBudget: Int,
        val boss: Boolean,
        /**
         * This level is a boss level: it opens with the MOTHER and nothing else.
         *
         * Separate from [boss] even though they coincide today. One is about the
         * queue (a boss level has no queue at all — see [startLevel]), the other
         * about the announcement and the [LevelModifier] card. They can be tuned
         * apart without either silently changing the other.
         */
        val isMotherLevel: Boolean
    )

    /**
     * A boss every N levels.
     *
     * Public, because the home screen tells the player when the next one is, and
     * that number has to be *this* number. It used to be a private `5` here and
     * a separate hard-coded `5` in HomeScreen's level preview: the engine said
     * ten, the card said five, and a player at level 9 was told "🔥 مرحله بعد
     * رئیس!" about a level with no boss on it. Two copies of a rule the player
     * is shown directly is a rule that will eventually be wrong in one place.
     *
     * Ten, not five: every fifth level made half the game a boss level, so the
     * fight stopped being an event and became the texture of the game.
     */
    companion object {
        const val BOSS_EVERY_N_LEVELS = 10
    }

    /**
     * Ceilings for the endless curve.
     *
     * These are the numbers that decide whether level 2000 exists. Set them too
     * low and the game flatlines into "the same level with a bigger number";
     * set them too high and it stops being playable. The block cap matters most
     * — it is the one axis that degrades the *device*, not just the challenge.
     *
     * Gravity, fall speed and spawn rate were all raised so their ceilings land
     * well past the level where the upgrade track finishes (level ~304), instead
     * of all three having stopped moving by level 113. They are pressure, not
     * digits: the player can see them coming and answer them by moving, which is
     * the only kind of difficulty that stays fair once raw HP is capped.
     *
     * The block cap is the one axis here that costs frames rather than challenge,
     * so it is the one that has to be argued for rather than just raised. It is
     * now 96, up from 64, and the reason is that every *other* axis was already
     * spent by level 200: block count, block HP, the HP multiplier, gravity,
     * fall speed and spawn interval had all reached their ceilings, so a late
     * level had the same 64 bodies to shoot as a level-61 board and nothing but
     * a larger armour number to tell them apart.
     *
     * Ninety-six is chosen against the two things it can actually cost. A level's
     * entity count is what the simulation walks and the renderer draws every
     * frame, so this is a 50% increase in per-frame work on a deep board — which
     * is why it is 96 and not 150. And every block carries a satellite effect
     * and a collision test, so this is also 50% more opportunity for a stray
     * block to reach the cannon. Both are accepted deliberately: the levels were
     * too short and too samey, and a longer board is difficulty the player can
     * *see* rather than a number they have to be told about.
     *
     * 72, not the 96 or 112 this has been. A level's length is the queue times
     * how full the field is allowed to get — see [maxBlocksAlive] — so a long
     * queue no longer buys a longer stage, it buys a longer wait between waves.
     * 72 against a field that holds a dozen to twenty is four to six waves,
     * which is a stage; 112 was seven to eleven, which is a chore with a
     * progress bar.
     */
    private val MAX_BLOCKS_PER_LEVEL = 72
    private val MAX_GRAVITY = 290f
    private val MAX_FALL_SPEED = 1400f
    private val MIN_SPAWN_INTERVAL = 0.16f

    private fun bossHp(lvl: Int): Int = generateBlockHP(lvl) * 2

/**
     * Level-scaled HP. Grows smoothly so early levels stay forgiving and later
     * levels ramp up reasonably.
 *
 * ── Why this is capped, and why the cap is in [MAX_BLOCK_HP] ─────────
 * `cannon.damage` is the DAMAGE upgrade level 1:1 and that upgrade maxes at
 * 3, but the *output* keeps climbing through the other five tracks: a maxed
 * cannon does `9.75 shots/s x 5 balls x 3 damage x 8 hits-per-ball` = 1,170 dps
 * against a starting 2.6, a 450x spread. HP is bounded so that spread stays
 * meaningful — see [MAX_BLOCK_HP] for why the bound is 240 and not something
 * smaller.
 *
 * The shape is unchanged at the start — a new player's first blocks still
 * shatter in one or two shots, with a ±10% variance so a field never looks
 * uniform — and the late ramp is steeper (16 per level rather than 11) so HP
 * keeps climbing past the point where every other axis has flattened.
 *
 * Past [MAX_BLOCK_HP] this returns the ceiling and the level's difficulty is
 * carried entirely by the armour budget, which is the intended arrangement: the
 * number on a block stays readable and the toughness moves onto something the
 * player can actually see.
 */
fun generateBlockHP(level: Int): Int {
        val baseHP = when {
            level <= 5 -> 4 + (level - 1) * 2
            level <= 15 -> 12 + (level - 5) * 3
            level <= 30 -> 42 + (level - 15) * 4
            level <= 50 -> 102 + (level - 30) * 5
            else -> 222 + (level - 50) * 16
        }
        val variance = 0.9f + rand.nextFloat() * 0.2f
        return (baseHP * variance).toInt().coerceIn(1, MAX_BLOCK_HP)
    }

    /**
 * Shape weights at spawn.
 *
 * Square stays the most common so the board keeps a readable base rhythm, but it
 * is no longer 55% — past that the field looked like graph paper with
 * occasional interruptions. The new shapes take meaningful share, so a crowded
 * late level is a mix the eye can separate rather than one repeated silhouette.
 *
 * TRIANGLE and STAR are capped lower deliberately: both are strong, distinctive
 * outlines, and too many of them at once makes the field busy in a way that
 * competes with the blocks the player is actually aiming at.
 */
private fun randomBlockShape(): BlockShape {
    val roll = rand.nextFloat() * 100f
    return when {
        roll < 38f -> BlockShape.SQUARE
        roll < 60f -> BlockShape.CIRCLE
        roll < 80f -> BlockShape.HEXAGON
        roll < 91f -> BlockShape.TRIANGLE
        else -> BlockShape.STAR
    }
}

    // ---------------------------------------------------------------- frames

    fun update(dt: Float) {
        if (status != GameStatus.Playing) return
        gameElapsed += dt
        runSecondsTotal += dt
        if (hitGrace > 0f) hitGrace = (hitGrace - dt).coerceAtLeast(0f)

        // Frame one of a boss level: the fight starts now, not after the spawn
        // delay an ordinary level's first block waits out.
        if (motherPendingSpawn && fieldWidth > 0f) {
            motherPendingSpawn = false
            spawnMother()
        }

        updateCannon(dt)
        updateSpawning(dt)
        updateBalls(dt)
        updateBlocks(dt)
        updateBonuses(dt)
        updateCoins(dt)
        updatePowerUpDrops(dt)
        updateParticles(dt)
        updatePopups(dt)
        updatePowerUps(dt)
        // Expire the combo on simulation time. Without this the chain only
        // reset on the *next* kill, so the HUD meter would keep showing a live
        // combo indefinitely after the player stopped shooting.
        levelElapsed += dt
        combo.tick(gameElapsed)
        // Active play time. Counted from the simulation clock so idle time
        // spent paused does not read as engagement.
        Telemetry.addActiveMs((dt * 1000f).toLong())

        if (shake > 0f) shake = (shake - dt * 3.5f).coerceAtLeast(0f)

        checkLevelComplete()
    }

    private fun updateCannon(dt: Float) {
        val c = cannon
        c.pulseTime += dt
        c.recoil *= exp(-10f * dt)

        // Smoother interpolation for more responsive feel
        val t = 1f - exp(-18f * dt)
        c.x += (c.targetX - c.x) * t
        c.x = c.x.coerceIn(c.radius, fieldWidth - c.radius)

        fireCooldown -= dt
        if (fireCooldown <= 0f) {
            fire()
            fireCooldown = 1f / shotsPerSecond()
        }
    }

    private fun shotsPerSecond(): Float =
        cannon.fireRate * if (hasPowerUp(PowerUpType.RAPID_FIRE)) 2f else 1f

    private fun fire() {
        val damage = cannon.damage *
            if (hasPowerUp(PowerUpType.DOUBLE_DAMAGE)) 2 else 1
        val multi = cannon.multiBall
        val pierce = cannon.pierce
        val speed = cannon.bulletSpeed

        cannon.recoil = 1f
        shotsFired++
        runShotsFired++
        Telemetry.addShots(1)
        onSound?.invoke(SoundEvent.FIRE)
        val muzzleY = cannon.centerY - cannon.radius

        // Muzzle sparkles
        repeat(3) {
            val p = particlePool.obtain()
            p.x = cannon.x
            p.y = muzzleY
            p.vx = randomFloat(-dp(40f), dp(40f))
            p.vy = -randomFloat(dp(30f), dp(90f))
            p.life = 0.18f
            p.size = dp(randomFloat(2f, 4f))
            p.color = NeonCyan
            particles += p
        }

        repeat(multi) { i ->
            val b = Ball()
            b.x = cannon.x
            b.y = muzzleY
            // Widen the fan as the volley gets wider, so 12 balls still read as a
            // stream rather than as a single thick column.
            val spread = if (multi == 1) 0f else (i - (multi - 1) / 2f) * (0.05f + multi * 0.004f)
            b.vx = sin(spread) * speed
            b.vy = -cos(spread) * speed
            // Smaller balls. At 8dp with multi-ball maxed at 12 the muzzle became
            // an opaque wall of overlapping discs that hid the blocks behind it —
            // which is exactly where the player has to aim. 5dp keeps twelve of
            // them visually separable.
            b.radius = dp(5f)
            b.damage = damage
            b.pierce = pierce
            // Fire while RAPID FIRE runs, so the power-up is legible in the volley
            // itself and not only in the HUD chip. Read at spawn, so a power-up
            // that starts mid-volley affects the next shot, not balls already in
            // flight.
            b.isFlaming = hasPowerUp(PowerUpType.RAPID_FIRE)
            // Same for GHOST. Immunity changes nothing about the damage a shot
            // does, so without a marker on the ball the only evidence it is
            // running is the HUD chip.
            b.isSpectral = hasPowerUp(PowerUpType.GHOST)
            balls += b
        }
    }

    // ---------------------------------------------------------------- spawn

    /**
     * How many blocks may be on the field at the same time.
     *
     * ── Why this is a ceiling on bodies, not just on the queue ────────────
     * `blockCount` bounds how many blocks a *level* contains; it never bounded
     * how many are on screen at once. Those are different limits and only one of
     * them was set. A block now bounces to a third of the field, which means it
     * stays airborne several times longer than it used to, so the queue can be
     * far ahead of the field and still end up with thirty-odd bodies in flight
     * at the same moment — a wall you cannot read, cannot aim through, and
     * cannot survive, because the cannon services a handful of blocks at a time.
     *
     * So the spawner holds when the field is full, and the level simply takes
     * longer rather than becoming a solid mass. That is the honest trade:
     * `blockCount` decides how long the stage is, and this decides how wide it
     * ever gets.
     *
     * It rises slowly with depth because a deep cannon clears blocks faster and
     * can handle a busier board — but it stays in the low twenties, because past
     * about two dozen the field stops being something the eye can pick a target
     * off.
     *
     * Lowered across the board, and growing more slowly: eight to sixteen
     * rather than ten to twenty. A level's blocks are a queue the player
     * watches arrive, and a queue that lands fifteen bodies in the first ten
     * seconds is not a queue, it is a wall — the player spends the level
     * reading a crowd instead of playing it. The board now fills gradually,
     * one block at a time, and the depth of a level lives in how long the
     * queue is rather than in how wide it gets at once.
     */
    private fun maxBlocksAlive(lvl: Int): Int = (8 + lvl / 25).coerceAtMost(16)

    private fun updateSpawning(dt: Float) {
        if (blocksToSpawn <= 0) return

        // Hold, do not skip. Consuming the timer while the field is full would
        // mean a level's blocks arrived in a burst the moment the field cleared,
        // which is the opposite of the steady rhythm the spawn interval exists
        // to give. The timer is clamped instead, so the first free slot produces
        // a block almost immediately and then the normal cadence resumes.
        if (blocks.size >= maxBlocksAlive(level)) {
            nextSpawnIn = nextSpawnIn.coerceAtMost(0.12f)
            return
        }

        nextSpawnIn -= dt
        if (nextSpawnIn <= 0f) {
            nextSpawnIn = spawnInterval * randomFloat(0.82f, 1.12f)
            if (rand.nextFloat() < bonusBlockChance) {
                spawnBonus()
            } else {
                // One block per tick, always. The spawner used to deal a row of
                // three at once a fifth of the time, which put three bodies in
                // the same corner of the field in the same frame — the board
                // arrived in clumps, and a clump is something you survive
                // rather than something you play. The queue is the level's
                // length; the interval is its rhythm; neither is served by
                // bunching them.
                spawnBlockSmart()
                blocksToSpawn--
                if (blocksToSpawn <= 0) spawnTicking = false
            }
        }
    }

    /** Smart spawn that avoids stacking directly over existing falling blocks */
    private fun spawnBlockSmart() {
        // A boss level never reaches this. `startLevel` spawns the one mother
        // itself and leaves `blocksToSpawn` at zero, so `updateSpawning` returns
        // immediately and never calls in here.
        //
        // It used to be the opposite: this branch existed to hand the level over
        // to `spawnMother` and returned *without* decrementing `blocksToSpawn`,
        // so the spawner came straight back for the next tick and the next, and
        // every one of them was another mother. The queue was the bug.

        // Try up to 7 times to find x that doesn't overlap horizontally with recent blocks near top
        var attempts = 0
        var chosenX = 0f
        var ok = false
        val cfg = levelCfg
        val isFinal = blocksToSpawn == 1
        val hp = if (cfg.boss && isFinal) bossHp(level) else (generateBlockHP(level) * cfg.hpMultiplier).toInt()
        val size = blockSizeFor(hp, cfg.boss && isFinal)
        while (attempts < 7 && !ok) {
            chosenX = randomFloat(0f, max(0f, fieldWidth - size))
            ok = true
            for (b in blocks) {
                if (b.y < dp(140f) && kotlin.math.abs(b.centerX - (chosenX + size * 0.5f)) < (b.w + size) * 0.55f) {
                    ok = false
                    break
                }
            }
            attempts++
        }
        // Use chosen x
        spawnBlockAt(chosenX, hp, cfg, isFinal, size)
    }

    /**
     * Armour hit-points for a block, given its HP and the level's armour budget.
     *
     * Two inputs, and the order matters:
     *
     * 1. A share of the block's own HP, so a small early block does not need
     *    twenty hits and a big late one is not trivially peeled. This is what
     *    armour did before the budget existed.
     * 2. The level budget, which is zero below level 50 and grows slowly after.
     *    This is what keeps a level 2000 block meaningfully tougher than a
     *    level 50 block once HP itself has stopped climbing.
     *
     * Both are recorded on [Block.armorMaxHp] as they are applied. Without that
     * second field the HUD could not draw wear, because it had nothing to
     * compare the remaining armour against — it was dividing armour by itself
     * and so always drew a full, undamaged ring.
     */
    /**
     * A block's plating, and how much of it the pieces keep.
     *
     * Fragments used to inherit none of it, which quietly deleted the whole
     * armour tax the moment a block broke: the player paid the full plating on
     * the parent and then destroyed the pieces as if they were bare. On a deep
     * board, where the plating is most of the block's effective health, that made
     * the "endless axis" a one-shot toll rather than a wall — and it is the same
     * reason a maxed build trivialised late levels, because the very first
     * volley removed the part of the block that was supposed to resist it.
     *
     * A third each rather than all of it: the pieces are visibly thinner plates,
     * and the reward for breaking a block is still that it stops being tough.
     */
private fun fragmentArmour(parentArmourHp: Int): Int =
        if (parentArmourHp <= 0) 0 else max(1, parentArmourHp / 3)

    private fun armourFor(hp: Int, budget: Int): Int =
        max(2, (hp / 6).coerceAtMost(24)) + budget

    private fun spawnBlockAt(x: Float, hp: Int, cfg: LevelConfig, isFinal: Boolean, size: Float) {
        val block = Block()
        block.maxHp = hp
        block.hp = hp
        block.scoreValue = hp
        block.isBoss = cfg.boss && isFinal
        // 11% of normal blocks become lucky numbered chance blocks (not bosses)
        block.isLucky = !block.isBoss && rand.nextFloat() < luckyBlockChance
        // Shape + phase are randomised per block: without a unique phase every
        // block sways in lockstep and the per-block colour jitter is a no-op.
        block.shapeType = randomBlockShape()
        block.shakePhase = rand.nextFloat() * 6.2832f
        // Stable colour identity. Must not be shakePhase — see Block.colorSeed.
        block.colorSeed = rand.nextFloat()
        // The block's palette slot, dealt so no two blocks in flight share a
        // colour until the deck has been fully cycled.
        block.colorIndex = nextBlockColorIndex()

        // ── Modifier properties ─────────────────────────────────────────
        // Applied to a share of the board rather than all of it: if every block
        // were ice, the rule stops being a rule and becomes the level. About a
        // third is enough that the player has to look, and not so much that the
        // level turns into a different game.
        // How much of the board the modifier claims. Grows with depth, capped,
        // so a late level is mostly the modifier and an early one is a taste of
        // it. Past the cap it would stop being a rule and become the level.
        val depthShare = (level / 300f).coerceAtMost(1f)
        val iceShare = 0.34f + 0.22f * depthShare
        val armourShare = 0.30f + 0.24f * depthShare

        when (levelModifier) {
            LevelModifier.ICE -> if (!block.isBoss && rand.nextFloat() < iceShare) {
                block.isIce = true
            }
            LevelModifier.ARMOR -> if (!block.isBoss && rand.nextFloat() < armourShare) {
                block.hasArmor = true
                block.armorHp = armourFor(hp, cfg.armourBudget)
                block.armorMaxHp = block.armorHp
            }
            LevelModifier.BOULDER -> if (!block.isBoss && rand.nextFloat() < 0.16f) {
                block.isBoulder = true
                // A visible multiple rather than a flat number: the player can
                // see that this one is different without being told.
                block.maxHp = (hp * 3).toInt().coerceAtMost(MAX_BLOCK_HP)
                block.hp = block.maxHp
                block.scoreValue = block.maxHp
            }
            else -> Unit
        }

        // Deep levels arm their blocks even when the modifier is something else.
        //
        // Without this the armour budget would only ever be spent on the ~30% of
        // blocks that land on an ARMOR level, and the other 70% of a level 300
        // board would be identical to level 50. Sharing the budget out means a
        // deep level reads as "everything here is tougher", which is the honest
        // signal — and it means the ceiling on HP stops being the ceiling on
        // difficulty.
        if (cfg.armourBudget > 0 && !block.hasArmor && !block.isBoss &&
            rand.nextFloat() < 0.55f
        ) {
            block.hasArmor = true
            block.armorHp = armourFor(hp, cfg.armourBudget)
        }

        if (block.isBoss) {
            // Assign a random boss type for variety
            block.bossType = when (rand.nextFloat()) {
                in 0f..0.5f -> BossType.CLASSIC
                in 0.5f..0.7f -> BossType.TITAN
                in 0.7f..0.85f -> BossType.SHADOW
                else -> BossType.INFERNO
            }
            // Apply HP multiplier based on boss type
            val hpMultiplier = when (block.bossType) {
                BossType.CLASSIC -> 2.5f
                BossType.TITAN -> 4.0f
                BossType.SHADOW -> 2.0f
                BossType.INFERNO -> 3.0f
                else -> 2.5f
            }
            block.maxHp = (hp * hpMultiplier).toInt().coerceAtLeast(1)
            block.hp = block.maxHp
            block.scoreValue = block.maxHp
            // Make boss blocks significantly larger
            block.w = size * 1.6f
            block.h = size * 1.6f
            onSound?.invoke(SoundEvent.BOSS_SPAWN)
            onBossFight?.invoke()
        } else {
            block.w = size
            block.h = size
        }
        // Start blocks slightly above screen so they fall in smoothly (like bonus blocks)
        block.x = x.coerceIn(0f, max(0f, fieldWidth - block.w))
        block.y = -block.h
        block.vx = 0f
        block.vy = 0f
        // Without this the block never entered the world and the level could
        // not be lost or completed by shooting.
        blocks += block
    }

    // ─────────────────────────────────────────────────────────────────────
    //  The MOTHER
    //
    //  One block, on an otherwise empty board, with twice the health. That is the
    //  whole boss level.
    //
    //  It was a "source" instead: she dropped children on a timer, so the board
    //  was never empty and clearing it could not end the level. Combined with the
    //  queue bug above, a boss level was a dozen mothers each producing eight
    //  children — a wall of large blocks arriving faster than they could be
    //  cleared, which is exactly what a boss level read like and nothing like a
    //  fight.
    //
    //  So the fight is one target you have to commit to. The cost of removing
    //  the board is paid in her HP rather than in the number of things on it.
    // ─────────────────────────────────────────────────────────────────────

    /** Opens a boss fight: the one and only block on the level. */
    private fun spawnMother() {
        val mother = Block()
        mother.isMother = true
        // She is the boss and the level's only target, but *not* the level's
        // "final block": that flag drives the boss-hit sound, the doubled score
        // and the coin burst, none of which should fire on every one of her
        // thousands of hits.
        mother.isBoss = false
        mother.shapeType = BlockShape.HEXAGON
        // Sized off the field, not off a constant: on a small phone a fixed size
        // could be wider than the play area.
        //
        // 0.46 rather than 0.52 of the field. At 52% she was a wall the cannon
        // could not get a clean line on and could not get out from under, which
        // is the same complaint the ordinary blocks got — she is meant to be
        // aimed at, not merely survived. Slightly smaller also means the player
        // has a corridor beside her on a narrow phone.
        mother.w = minOf(fieldWidth * 0.46f, dp(150f))
        mother.h = mother.w * 0.82f
        // Health as a length of fight for the cannon that is actually pointed at
        // her. See MOTHER_TTK_BASE for why this is not a function of the level
        // alone — an absolute number is a three-minute wall for a stock cannon
        // and a rounding error for a maxed one.
        mother.maxHp = motherHpFor(level).coerceIn(24, MOTHER_MAX_HP)
        mother.hp = mother.maxHp
        mother.scoreValue = mother.maxHp * 3
        mother.colorIndex = nextBlockColorIndex()
        mother.colorSeed = rand.nextFloat()
        mother.shakePhase = rand.nextFloat() * 6.2832f
        mother.x = (fieldWidth - mother.w) * 0.5f
        mother.y = -mother.h
        mother.vx = 0f
        mother.vy = 0f
        blocks += mother

        onSound?.invoke(SoundEvent.BOSS_SPAWN)
        onBossFight?.invoke()
        pushPopup {
            x = fieldWidth * 0.5f
            y = fieldHeight * 0.34f
            text = "☠️ باس فایت!"
            color = NeonPink
            life = 1.8f
            big = true
        }
        pushPopup {
            x = fieldWidth * 0.5f
            y = fieldHeight * 0.34f + dp(34f)
            text = "بلاک مادر — هر بار به کف می‌خورد بچه می‌سازه"
            color = NeonGold
            life = 2.6f
            big = false
        }
    }

    /** True while the level's MOTHER is still alive. */
    private fun motherAlive(): Boolean = blocks.any { it.isMother }

    /**
     * This cannon's ideal damage per second.
     *
     * The same product [cannonPowerIndex] uses, un-divided, because what is
     * wanted here is the output itself rather than a ratio to the starting
     * cannon. Read live from [cannon] rather than from the stored upgrade
     * levels so a purchase made mid-level counts immediately.
     */
    private fun cannonDps(): Float {
        val hits = (cannon.pierce + 1).coerceAtLeast(1)
        return cannon.fireRate * cannon.multiBall * cannon.damage * hits
    }

    /**
     * How long the mother should last against [cannonDps], in seconds.
     *
     * Sub-linear in the level on purpose: the fight grows, but a boss at level
     * 9000 has to stay a fight and not become a shift. `sqrt` reaches about 20
     * seconds at 2,500 against 4.7 on the first one, and [MOTHER_TTK_MAX] caps
     * what the curve asks for after that.
     */
    private fun motherTtk(lvl: Int): Float =
        (MOTHER_TTK_BASE + MOTHER_TTK_PER_ROOT_LEVEL * sqrt(lvl.toFloat().coerceAtLeast(1f)))
            .coerceAtMost(MOTHER_TTK_MAX)

    /** The mother's HP for [lvl], against the cannon that will be shooting her. */
    private fun motherHpFor(lvl: Int): Int =
        (cannonDps() * MOTHER_HIT_EFFICIENCY * motherTtk(lvl)).toInt()

    /**
     * Two satellites off the mother's flanks, thrown on every ground bounce.
     *
     * Deliberately *not* a timer. A spawn the player cannot predict is a spawn
     * they cannot answer, and the whole point of putting production on the bounce
     * is that it lands on something they watched happen: the mother comes down,
     * the board tells you where she is, and then there are two more things to
     * clear. Two, not more, and never more than
     * [MOTHER_MAX_SATELLITES] alive — the cap is what keeps a boss level a
     * fight rather than the wall this level used to be.
     *
     * Every landing produces, and the cap is what stops it running away. The
     * previous cap sat *at* the throw rate, so two landings in a row while the
     * player was busy produced nothing at all — the mother looked sterile and
     * the fight read as one big number.
     *
     * They are ordinary blocks with everything ordinary about them: shootable,
     * worth coins, and dangerous if one reaches the cannon. Only her HP is
     * scaled down, so they are errands rather than threats.
     */
    private fun spawnSatellites(mother: Block) {
        var alive = 0
        for (b in blocks) if (b.isSatellite) alive++
        val room = MOTHER_MAX_SATELLITES - alive
        if (room <= 0) return

        val size = minOf(fieldWidth * 0.13f, dp(38f))
        // A fraction of a *normal block at this level*, so a satellite is a
        // couple of hits at any depth. A flat number would be trivial on the
        // first boss and a wall on the last.
        val levelBlockHp = generateBlockHP(level).toFloat()
        repeat(minOf(MOTHER_SATELLITES_PER_BOUNCE, room)) { i ->
            val sat = Block()
            sat.maxHp = max(1, (levelBlockHp * MOTHER_SATELLITE_HP).toInt())
            sat.hp = sat.maxHp
            sat.scoreValue = sat.maxHp
            sat.isSatellite = true
            sat.shapeType = randomBlockShape()
            sat.colorIndex = mother.colorIndex   // her colour, so the family reads
            sat.colorSeed = rand.nextFloat()
            sat.shakePhase = rand.nextFloat() * 6.2832f
            sat.w = size
            sat.h = size
            // Beside her, not on top of her: one each side, so the pair frames
            // the boss instead of hiding behind it. They sit on the floor beside
            // her, level with the cannon's own line — the same floor everything
            // else lands on.
            val side = if (i % 2 == 0) -1f else 1f
            // Keep the pair a little inside the walls. They used to be clamped
            // to exactly 0 / fieldWidth - size, and their outward launch
            // velocity then drove them straight into the wall on their first
            // frame — a side-spawned satellite visibly bounced off the edge
            // before it had even left the mother. A small margin plus an
            // inward launch when the spawn lands on that margin gives them a
            // readable arc instead.
            val wallMargin = dp(6f)
            val minX = wallMargin.coerceAtMost(fieldWidth * 0.5f)
            val maxX = (fieldWidth - size - wallMargin).coerceAtLeast(minX)
            sat.x = (mother.centerX + side * (mother.w * 0.62f) - size * 0.5f +
                randomFloat(-dp(10f), dp(10f)))
                .coerceIn(minX, maxX)
            sat.y = cannon.baseY - sat.h
            val launchDir = when {
                sat.x <= minX + dp(0.5f) -> 1f
                sat.x >= maxX - dp(0.5f) -> -1f
                else -> side
            }
            sat.vx = launchDir * randomFloat(dp(30f), dp(70f))
            sat.vy = dp(14f)
            blocks += sat
        }
        onSound?.invoke(SoundEvent.BLOCK_CRACK)
    }

    private fun spawnBonus() {
        val b = BonusBlock()
        b.size = dp(34f)
        b.x = randomFloat(0f, max(0f, fieldWidth - b.size))
        b.y = -b.size
        b.vy = dp(26f) + randomFloat(0f, dp(12f))
        b.vx = randomFloat(-dp(40f), dp(40f))
        b.spin = randomFloat(150f, 320f)
        b.type = randomPowerUp()
        bonuses += b
    }

    /**
 * Which power-up a floating bonus cube grants.
     *
     * Weighted by how much the player *notices* a change. NUKE is rarest because
     * it ends the level outright; GHOST is common because it is the most
     * spectacular thing to see happen (every block passing straight through) and
     * the one most worth building a level towards.
     *
     * SHIELD is deliberately rare: it is a save, and a save that shows up often
     * stops reading as a save.
     */
    private fun randomPowerUp(): PowerUpType {
        val roll = rand.nextFloat() * 100f
        return when {
            roll < 4f -> PowerUpType.NUKE
            roll < 8f -> PowerUpType.SHIELD
            roll < 26f -> PowerUpType.FREEZE
            roll < 48f -> PowerUpType.DOUBLE_DAMAGE
            roll < 70f -> PowerUpType.RAPID_FIRE
            roll < 88f -> PowerUpType.GHOST
            else -> PowerUpType.COIN_RUSH
        }
    }

    private fun blockSizeFor(hp: Int, boss: Boolean, boulder: Boolean = false): Float {
        if (boss) return dp(104f)
        if (boulder) return dp(92f)
        return when {
            hp <= 5 -> dp(52f)
            hp <= 10 -> dp(58f)
            hp <= 20 -> dp(64f)
            hp <= 50 -> dp(72f)
            hp <= 100 -> dp(80f)
            else -> dp(88f)
        }
    }

    // ---------------------------------------------------------------- balls

    private fun updateBalls(dt: Float) {
        var i = 0
        while (i < balls.size) {
            val b = balls[i]
            b.age += dt
            val prevX = b.x
            val prevY = b.y
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.pushTrail(b.x, b.y)
            b.scale = min(1f, 0.3f + b.age * 9f)

            val bonusConsumed = hitBonus(b)

            val dead = b.y < -dp(40f) ||
                b.y > fieldHeight + dp(40f) ||
                b.x < -dp(40f) || b.x > fieldWidth + dp(40f) ||
                bonusConsumed ||
                !hitBlocks(b, prevX, prevY)

            if (dead) {
                balls.removeAt(i)
            } else {
                i++
            }
        }
    }

    /**
     * Returns false when the ball was consumed.
     *
     * [prevX]/[prevY] are the ball's position at the start of the frame so a
     * fast bullet can't tunnel through a block. The pierce guard in
     * [Ball.lastHitBlock] is released only once the ball has actually left the
     * block it was guarding, so a piercing ball damages a block once instead of
     * once per frame for as long as it overlaps.
     */
    private fun hitBlocks(b: Ball, prevX: Float, prevY: Float): Boolean {
        val guarded = b.lastHitBlock
        if (guarded != null && !CollisionDetector.ballHitsBlock(b.x, b.y, b.currentRadius, guarded)) {
            b.lastHitBlock = null
        }

        for (block in blocks) {
            if (block === b.lastHitBlock) continue
            if (!CollisionDetector.sweptBallHitsBlock(
                    prevX, prevY, b.x, b.y, b.currentRadius, block
                )
            ) continue

            b.lastHitBlock = block

            // ── ARMOUR ───────────────────────────────────────────────────
            // The shield absorbs the hit entirely. It is checked before ice on
            // purpose: an armoured ice block would otherwise take one hit to
            // crack the ice and a second to crack the armour, which is a
            // four-hit block wearing two props and reads as a bug.
            if (block.hasArmor && block.armorHp > 0) {
                block.armorHp -= b.damage
                block.applyHit()
                spawnHitParticles(b, block)
                if (block.armorHp <= 0) {
                    block.hasArmor = false
                    block.armorHp = 0
                    pushPopup {
                        x = block.centerX
                        y = block.centerY - block.h * 0.4f
                        text = "زره شکست!"
                        color = NeonCyan
                        life = 0.6f
                        big = false
                    }
                }
                return !consumedForHit(block, b)
            }

            // ── ICE ──────────────────────────────────────────────────────
            // Survives the first hit, then shatters. The hit is *not* wasted —
            // it is stored as a crack, so two hits always kill regardless of how
            // the damage happens to split between them.
            if (block.isIce) {
                block.isIce = false
                // The cracking shot is absorbed by the ice: no HP is lost here.
                //
                // This was originally written with a comment claiming the damage
                // was "stored as a crack", and no such field existed — so the
                // shot was doing nothing and the comment was simply false. The
                // behaviour itself is the right one, so it stays; only the
                // claim was wrong.
                //
                // An earlier attempt banked the damage into `iceCrackDamage` and
                // spent it on the following hit. Measured, that made ice cost
                // *zero* extra shots: a 200 HP block still died in 67, because
                // the second shot simply applied two hits' worth at once. That
                // silently deleted the rule while looking like a fix.
                //
                // So: ice costs exactly one extra hit, at every HP. On a small
                // block that is a real price. On a 200 HP block it is mostly a
                // delay rather than an ammo cost, which is the honest ceiling
                // for this mechanic and is why ice stays a minor modifier.
                block.applyHit()
                spawnHitParticles(b, block)
                onSound?.invoke(SoundEvent.BLOCK_CRACK)
                shake = max(shake, 0.3f)
                repeat(8) {
                    val p = particlePool.obtain()
                    p.x = b.x
                    p.y = b.y
                    val a2 = rand.nextFloat() * 6.2832f
                    val s2 = randomFloat(dp(60f), dp(180f))
                    p.vx = cos(a2) * s2
                    p.vy = sin(a2) * s2
                    p.life = 0.4f
                    p.size = dp(randomFloat(2f, 4f))
                    p.color = Color(0xFFBAE6FD)
                    particles += p
                }
                return !consumedForHit(block, b)
            }

            block.hp -= b.damage
            block.applyHit()
            spawnHitParticles(b, block)

            val consumed = consumedForHit(block, b)

            if (block.hp <= 0) {
                // Count the kill *before* the reward so the combo bonus reflects
                // this kill, not the previous one.
                val comboCount = combo.onBlockDestroyed(gameElapsed)
                // `ComboTracker.best` is cleared by startLevel, so on its own it
                // can only ever report the best combo of the *last* level of a
                // run. A run is several levels; the high-water mark has to
                // survive the stage boundary to mean anything.
                if (combo.best > runBestCombo) runBestCombo = combo.best
                // Reported on every kill, not just milestones: the panel's
                // combo distribution is built from the counters this feeds, and
                // noteCombo is a no-op unless the chain actually grew.
                Telemetry.noteCombo(comboCount)
                Telemetry.addBlocks(1)
                destroyBlock(block, comboCount = comboCount)
                onSound?.invoke(SoundEvent.BLOCK_DESTROY)
            } else {
                onSound?.invoke(SoundEvent.BLOCK_CRACK)
                shake = max(shake, 0.28f)
                pushPopup {
                    x = block.centerX + randomFloat(-block.w * 0.2f, block.w * 0.2f)
                    y = block.y + dp(6f)
                    text = "-${b.damage}"
                    color = Color.White
                    age = 0f
                    life = 0.45f
                    big = false
                }
            }
            return !consumed
        }
        return true
    }

    /**
     * Whether a hit consumes the ball, honouring PIERCE and GHOST.
     *
     * This is the single place that decides a ball's survival.
     *
     * It used to be three copies of the same two-line test. Armour and ice both
     * return early from [hitBlocks] without reaching the normal bookkeeping
     * further down, so they each had to make the decision themselves — and
     * nothing stopped one of the three from drifting. It had already: the
     * inlined copy in the main path was the only one that did not know about
     * GHOST, so a ball that survived one armour plate and one ice block by
     * spending pierce was consumed anyway by the third, while a ball that hit
     * armour or ice under GHOST was consumed too.
     *
     * GHOST is why that mattered. [isGhost] is documented as "blocks cannot
     * destroy a ball while it is active" and it was read *nowhere* — the enum
     * entry, its 7-second timer, its HUD chip and its 18% drop weight were all
     * live, and the only thing it actually did was stop a block that reached the
     * cannon costing a heart. A player who rolled the one power-up described as
     * "your balls cannot be destroyed" watched their balls keep getting
     * destroyed, and had no way to tell that was intended.
     *
     * Order matters: GHOST is checked first and does not spend pierce, so the
     * timer is spent on immunity rather than on the next block.
     */
    private fun consumedForHit(block: Block, b: Ball): Boolean {
        if (isGhost) return false
        if (b.pierce > 0) {
            b.pierce--
            return false
        }
        return true
    }

    private fun spawnHitParticles(b: Ball, block: Block) =
        spawnHitParticlesAt(b.x, b.y)

    /**
     * Sparks at a point.
     *
     * Split out from [spawnHitParticles] because it only ever needed the ball's
     * position, never the ball: the revive clears blocks without firing at them,
     * and passing a throwaway `Ball()` per cleared block to express "at these
     * coordinates" was allocating projectiles for an explosion.
     */
    private fun spawnHitParticlesAt(x: Float, y: Float) {
        repeat(3) {
            val p = particlePool.obtain()
            p.x = x
            p.y = y
            p.vx = randomFloat(-dp(70f), dp(70f))
            p.vy = randomFloat(-dp(70f), dp(30f))
            p.life = 0.25f
            p.size = dp(randomFloat(1.5f, 3f))
            p.color = Color.White
            particles += p
        }
    }

// ---------------------------------------------------------------- blocks

private fun updateBlocks(dt: Float) {
        // Blocks land on the cannon's own baseline. Always did — a block settles
        // on the surface the player is standing on, which is what makes going
        // under one while it is in the air a choice you have to make.
        val groundY = cannon.baseY
        val freeze = if (hasPowerUp(PowerUpType.FREEZE)) 0.28f else 1f
        var idx = 0
        while (idx < blocks.size) {
            val block = blocks[idx]
            block.updateShake(dt)

            if (block.isHoming) {
                // A spent block owns the floor. It telegraphs, then charges
                // once in a fixed direction. It never re-aims at the cannon,
                // so stepping out of the lane is always a real answer.
                if (updateFloorThreat(block, dt, groundY)) continue
            } else {
                val gravScale = if (block.isLucky) 0.78f else 1f
                block.vy += dp(levelGravity) * dt * freeze * gravScale
                block.vy = block.vy.coerceAtMost(dp(levelMaxFall) * (if (freeze < 1f) 0.42f else 1f))
                block.vx -= block.vx * dt * 0.5f
                block.vx += sin(gameElapsed * 0.9f + block.shakePhase) * dp(8f) * dt

                // ── WIND / LIFT ──────────────────────────────────────────────
                // A steady sideways push, and a gentle upward one. Both are
                // applied as an acceleration rather than by setting vx
                // directly, so they compose with the existing drag and the
                // wall bounce instead of fighting them: a block pushed into a
                // wall bounces off it and drifts back, which is the behaviour
                // that makes wind readable.
                //
                // Both forces strengthen with depth, on the same flattening
                // curve as RUSH. The ceiling is kept modest on purpose: past
                // roughly 60% of it the wind stops being something you read
                // and starts being something that overrides your aim.
                val push = 1f + 0.85f * (level / 400f).coerceAtMost(1f)
                when (levelModifier) {
                    LevelModifier.WIND ->
                        block.vx += dp(26f) * push * dt * (1f + 0.22f * sin(gameElapsed * 0.5f))
                    LevelModifier.LIFT ->
                        block.vy -= dp(30f) * push * dt * (1f + 0.25f * sin(gameElapsed * 0.7f))
                    else -> Unit
                }
                block.x += block.vx * dt * freeze
                block.y += block.vy * dt * freeze

                // Resolve the side walls through the same helper the floor
                // threat uses. The old inline test reflected vx on *contact*
                // rather than on *approach*, so a block that had been clamped
                // to a wall (by separation, a resize, or its own spawn margin)
                // could have its inward velocity flipped back out and jitter
                // there instead of leaving. See [keepBlockInField].
                keepBlockInField(block)

                // Ground first. The cannon test runs after the floor has been
                // resolved, so a block resting on the baseline is not treated
                // as a hit merely because it is near the cannon.
                if (block.bottom >= groundY) {
                    block.y = groundY - block.h
                    if (block.isMother) {
                        // The mother has no bounce budget: she is the level.
                        // What makes her dangerous is her body — she is wide,
                        // and she comes down on every single bounce.
                        block.vy = -bounceSpeed()
                        block.vx = randomFloat(-dp(46f), dp(46f))
                        block.bounceCount++
                        block.shakeTime = 0.16f
                        block.hitFlash = 0.25f
                        spawnSatellites(block)
                    } else if (block.bounceCount >= bounceBudgetFor(level)) {
                        // Out of bounces. Only ONE spent block may threaten the
                        // floor at a time. A second one shatters harmlessly on
                        // landing instead of forming a wall with no gap — that
                        // wall was the "two obstacles, no room to dodge" death.
                        val anotherThreat = blocks.any { it !== block && it.isHoming }
                        if (anotherThreat) {
                            shatterFloorBlock(block)
                            blocks.removeAt(idx)
                            continue
                        }
                        block.isHoming = true
                        block.lastStandTimer = LAST_STAND_TELEGRAPH
                        val desiredDir = when {
                            abs(cannon.x - block.centerX) > dp(4f) ->
                                if (cannon.x > block.centerX) 1f else -1f
                            block.vx >= 0f -> 1f
                            else -> -1f
                        }
                        // A body already pinned to a wall must not pick that
                        // wall as its charge lane: it would spend one frame
                        // moving out of the field and shatter immediately,
                        // which reads as "the side obstacle hit the wall and
                        // vanished" rather than as a threat the player dodged.
                        // If the cannon is on that side the two are already
                        // overlapping (the collision pass above would have
                        // resolved it), so charging the other way is safe.
                        block.chargeDir = when {
                            desiredDir < 0f && block.x <= dp(1f) &&
                                block.x + block.w < fieldWidth - dp(1f) -> 1f
                            desiredDir > 0f && block.x + block.w >= fieldWidth - dp(1f) &&
                                block.x > dp(1f) -> -1f
                            else -> desiredDir
                        }
                        block.chargeElapsed = 0f
                        block.isCharging = false
                        block.vy = 0f
                        block.vx = 0f
                        block.y = groundY - block.h
                        block.shakeTime = 0.2f
                        block.hitFlash = 0.35f
                    } else {
                        block.vy = -bounceSpeed()
                        // Re-aimed sideways on every bounce, so a board of
                        // blocks does not settle into one tidy stack in a
                        // corner.
                        block.vx = randomFloat(-dp(46f), dp(46f))
                        block.bounceCount++
                        block.shakeTime = 0.16f
                        block.hitFlash = 0.25f
                    }
                }
            }

            idx++
        }

        // Two blocks must never share the same space. Overlapping bodies were
        // the second half of the unfair-death report: a pair could arrive as
        // one silhouette, and the only visible gap was not a gap at all.
        separateBlocks()

        // Collision is tested AFTER separation, so a body that was just pushed
        // is evaluated where it actually ended up. The block hitbox is inset
        // and the cannon radius is scaled inside [blockHitsCannon].
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            if (!blockHitsCannon(block)) {
                i++
                continue
            }
            if (block.isMother) {
                motherSlam(block)
                i++
            } else {
                handleCannonHit(block)
                if (status != GameStatus.Playing) break
                // handleCannonHit removes the block; do not increment.
            }
        }
    }

    /**
     * Keeps a block inside the side walls, and reports whether it touched one.
     *
     * Two details matter here and both were bugs in the old inline test:
     *
     * 1. The velocity is reflected only when the block is actually moving
     *    *into* the wall. The old code bounced on any contact, so a body that
     *    had already been clamped to an edge and was moving back inward had
     *    that inward velocity reversed and was pushed into the wall again. A
     *    side-spawned satellite next to a wall could visibly sit there and
     *    vibrate instead of leaving.
     * 2. A body wider than the field (a boss on a very narrow layout) gets a
     *    zero-width valid range. The old right-edge test could never be false
     *    for it, so it reflected every frame. Clamping to the single valid x
     *    and not re-reflecting an inward velocity keeps it stable.
     *
     * Returns true when the wall clamped the body this call.
     */
    private fun keepBlockInField(block: Block): Boolean {
        val maxX = (fieldWidth - block.w).coerceAtLeast(0f)
        if (block.x < 0f) {
            block.x = 0f
            if (block.vx < 0f) block.vx = -block.vx * 0.85f
            return true
        }
        if (block.x > maxX) {
            block.x = maxX
            if (block.vx > 0f) block.vx = -block.vx * 0.85f
            return true
        }
        return false
    }

    /**
     * Updates a spent block's last-stand telegraph and charge.
     *
     * Returns true when the block was removed, so the caller can continue the
     * iteration without touching a stale index.
     */
    private fun updateFloorThreat(block: Block, dt: Float, groundY: Float): Boolean {
        block.vy = 0f
        block.y = groundY - block.h

        if (block.lastStandTimer > 0f) {
            block.vx = 0f
            block.lastStandTimer = (block.lastStandTimer - dt).coerceAtLeast(0f)
            // A visible quiver while it winds up. The block stays fully
            // shootable during this window; that is the counterplay.
            block.x = (block.x + sin(gameElapsed * 46f + block.shakePhase) * dp(18f) * dt)
                .coerceIn(0f, (fieldWidth - block.w).coerceAtLeast(0f))
            return false
        }

        if (!block.isCharging) {
            block.isCharging = true
            block.shakeTime = 0.2f
        }
        // Set the charge velocity every frame, not just on the first one.
        // The old code zeroed vx at the top of this function and restored it
        // only when `isCharging` flipped true, so the block moved for a single
        // frame (~4px), stopped, and then expired two and a half seconds later
        // without ever reaching the cannon or a wall. That is why the floor
        // threat read as a block that "just hit the wall" and vanished.
        block.vx = block.chargeDir * dp(LAST_STAND_CHARGE_SPEED)
        block.x += block.vx * dt
        block.chargeElapsed += dt

        // Clamp before shattering so the explosion is drawn on the wall the
        // player saw it reach, not a block-width outside the field. The return
        // value is what tells us the charge was stopped by a wall; the old test
        // also treated an exact edge position as a wall hit even when the block
        // was charging away from it.
        val hitWall = keepBlockInField(block)
        if (hitWall || block.chargeElapsed >= LAST_STAND_CHARGE_LIFE) {
            shatterFloorBlock(block)
            blocks.remove(block)
            return true
        }
        return false
    }

    /** A spent block that cannot become the floor threat breaks apart. */
    private fun shatterFloorBlock(block: Block) {
        spawnExplosion(block)
        onSound?.invoke(SoundEvent.BLOCK_DESTROY)
        pushPopup {
            x = block.centerX
            y = block.centerY - dp(10f)
            text = "خنثی شد"
            color = NeonCyan
            life = 0.65f
        }
    }

    /**
     * Pushes overlapping blocks apart horizontally.
     *
     * O(n²), but the field is capped at sixteen bodies, and the alternative —
     * letting two rectangles occupy the same pixels — is a death the player
     * cannot see coming. The mother is skipped because her satellites are
     * meant to orbit her silhouette.
     */
    private fun separateBlocks() {
        if (blocks.size < 2) return
        val margin = dp(1.5f)
        for (i in blocks.indices) {
            val a = blocks[i]
            if (a.isMother) continue
            for (j in i + 1 until blocks.size) {
                val b = blocks[j]
                if (b.isMother) continue
                val overlapX = min(a.x + a.w, b.x + b.w) - max(a.x, b.x)
                val overlapY = min(a.y + a.h, b.y + b.h) - max(a.y, b.y)
                if (overlapX < dp(1f) || overlapY < dp(1f)) continue
                val push = ((overlapX + margin) * 0.5f).coerceAtMost(dp(8f))
                val dir = if (a.centerX <= b.centerX) -1f else 1f
                a.x = (a.x + dir * push).coerceIn(0f, (fieldWidth - a.w).coerceAtLeast(0f))
                b.x = (b.x - dir * push).coerceIn(0f, (fieldWidth - b.w).coerceAtLeast(0f))

                // Wedged against the same wall: lift the upper body a little
                // so the two stop drawing as one block.
                val stillOverlapX = min(a.x + a.w, b.x + b.w) - max(a.x, b.x)
                if (stillOverlapX > dp(2f) && overlapY < min(a.h, b.h) * 0.5f) {
                    val lift = min(overlapY, dp(6f))
                    if (a.y < b.y) a.y -= lift else b.y -= lift
                }
            }
        }
    }

    /**
     * Does this block physically reach the cannon body?
     *
     * Both sides of the test are deliberately smaller than what is drawn:
     * the block loses [BLOCK_HITBOX_INSET] of each edge (triangles and stars
     * do not fill their bounding box) and the cannon uses
     * [CANNON_HITBOX_SCALE] of its radius. [hitGrace] covers the frames right
     * after a revive.
     */
    private fun blockHitsCannon(block: Block): Boolean {
        if (hitGrace > 0f && !block.isMother) return false
        val insetX = block.w * BLOCK_HITBOX_INSET
        val insetY = block.h * BLOCK_HITBOX_INSET
        return CollisionDetector.rectHitsCircle(
            cannon.x,
            cannon.centerY,
            cannon.radius * CANNON_HITBOX_SCALE,
            block.x + insetX,
            block.y + insetY,
            (block.w - insetX * 2f).coerceAtLeast(dp(1f)),
            (block.h - insetY * 2f).coerceAtLeast(dp(1f))
        )
    }

    /**
     * The mother reaches the cannon and is not destroyed by it.
     *
     * ── Why she is not simply [handleCannonHit] ───────────────────────────
     * She is the level. A block that reaches the cannon is removed, and
     * removing her on a boss level — whose queue is empty and whose only other
     * exit condition is an empty board — would complete the stage with the boss
     * still standing. A player could beat every boss in the game by standing
     * still.
     *
     * So the consequence of landing on the cannon is a heart, exactly like any
     * other block, and she stays on the board to be shot. GHOST and SHIELD
     * intercept it for the same reason they intercept an ordinary block: they
     * are a promise not to be hit, and this is being hit.
     *
     * She is then bounced straight back up off the same spot, so a slam that was
     * shielded or ghosted does not immediately repeat — otherwise the player who
     * spent the shield would simply be hit again next frame, which is the
     * opposite of what a shield is for.
     */
    private fun motherSlam(mother: Block) {
        spawnExplosion(mother)
        shake = max(shake, 0.55f)
        combo.reset()

        if (hasPowerUp(PowerUpType.GHOST)) {
            onSound?.invoke(SoundEvent.BLOCK_DESTROY)
            pushPopup {
                x = fieldWidth * 0.5f
                y = fieldHeight * 0.55f
                text = "👻 رد شد!"
                color = PowerUpType.GHOST.color
                life = 0.9f
                big = true
            }
        } else if (shieldsHeld > 0) {
            shieldsHeld -= 1
            onSound?.invoke(SoundEvent.BLOCK_DESTROY)
            pushPopup {
                x = fieldWidth * 0.5f
                y = fieldHeight * 0.55f
                text = "🛡️ سپر مصرف شد"
                color = PowerUpType.SHIELD.color
                life = 0.9f
                big = true
            }
        } else {
            onSound?.invoke(SoundEvent.HEART_LOST)
            pushPopup {
                x = fieldWidth * 0.5f
                y = fieldHeight * 0.55f
                text = "💥 رئیس از کف رد شد!"
                color = NeonPink
                life = 1.1f
                big = true
            }
            flushCoins()
            Telemetry.track(
                "run_end",
                level = level,
                value = score,
                durationMs = levelElapsedMs(),
                meta = "outcome=slam,blocks=$blocksDestroyed,coins=$coinsInLevel,combo=${combo.best}"
            )
            Telemetry.flush()
            status = GameStatus.HitByBlock
        }

        // Refund the bounces and send her straight back up, exactly as an
        // ordinary bounce does: same position, inverted velocity. Teleporting
        // her to the top of the field instead would read as a respawn rather
        // than as a rebound, and a fight that teleports its own boss is a fight
        // the player cannot track.
        //
        // Only reached when the run is still going — a saved slam — because
        // `update` stops when it is not, and an already-ended run has nothing
        // left to keep moving.
        if (status == GameStatus.Playing) {
            mother.bounceCount = 0
            mother.vy = -bounceSpeed()
            mother.vx = randomFloat(-dp(46f), dp(46f))
        }
    }

    /**
     * The launch speed for a ground bounce, in px/s.
     *
     * Solved from the level's own gravity so the apex lands on
     * [BOUNCE_HEIGHT_FRACTION] of the field: `v = sqrt(2gh)`. Fixed *height*
     * rather than fixed speed, because a fixed speed would make the hop taller
     * the deeper the level got as gravity rose — the opposite of what rising
     * gravity is supposed to feel like.
     */
    private fun bounceSpeed(): Float {
        val height = (fieldHeight * BOUNCE_HEIGHT_FRACTION).coerceAtLeast(dp(24f))
        return sqrt(2f * dp(levelGravity) * height).coerceAtMost(dp(levelMaxFall))
    }

    /**
     * A block reached the cannon. The only place a run can end this way.
     *
     * ── Why this consults GHOST and SHIELD ───────────────────────────────
     * Both intercept the event that would otherwise end the level, so the
     * damage is avoided rather than merely reduced — the player loses the block,
     * and nothing else.
     *
     * GHOST is checked first because it is a window, not an item: it applies to
     * the whole volley of balls. SHIELD is a single held charge, so it is spent.
     * Checking them in the other order would burn a shield during a window when
     * the shield was never needed.
     *
     * The block is destroyed either way. Letting a block sit on the cannon would
     * mean the player is being hit by something they can neither move nor shoot
     * (it is already at the bottom), which reads as a bug rather than as a
     * reward.
     */
    private fun handleCannonHit(block: Block) {
        spawnExplosion(block)
        blocks.remove(block)
        combo.reset()

        if (hasPowerUp(PowerUpType.GHOST)) {
            // Push the offending block off the bottom of the field rather than
            // leaving it hovering at the cannon.
            block.y = fieldHeight + block.h
            onSound?.invoke(SoundEvent.BLOCK_DESTROY)
            pushPopup {
                x = fieldWidth * 0.5f
                y = fieldHeight * 0.55f
                text = "👻 ازش گذشت!"
                color = PowerUpType.GHOST.color
                life = 0.9f
                big = true
            }
            return
        }

        if (shieldsHeld > 0) {
            shieldsHeld -= 1
            onSound?.invoke(SoundEvent.BLOCK_DESTROY)
            pushPopup {
                x = fieldWidth * 0.5f
                y = fieldHeight * 0.55f
                text = "🛡️ سپر مصرف شد"
                color = PowerUpType.SHIELD.color
                life = 0.9f
                big = true
            }
            return
        }

        onSound?.invoke(SoundEvent.HEART_LOST)

        // No reprieve: a block that reaches the cannon ends the level.
        //
        // There was a mercy window here that spent a heart and let the player
        // carry on, once per level. It is gone because it made the only rule in
        // the game inconsistent — the same collision ended the level or did not,
        // depending on a flag the player could not see. Hearts are the buffer, and
        // the loss is real, which is what makes a win mean something.
        flushCoins()
        Telemetry.track(
            "run_end",
            level = level,
            value = score,
            durationMs = levelElapsedMs(),
            meta = "outcome=hit,blocks=$blocksDestroyed,coins=$coinsInLevel,combo=${combo.best}"
        )
        Telemetry.flush()
        status = GameStatus.HitByBlock
    }

/**
 * Buys the player out of a block hit, once per level, in exchange for a rewarded
 * ad.
 *
 * ── Why it clears the board instead of restarting the level ──────────────
 * "Continue" in an arcade game means keep what you earned. Restarting the level
 * would be the easy implementation and it is not what the player is paying for:
 * at level 300 a level is about a minute of work and, with the current balance,
 * up to several. Asking someone to watch an advert to be moved back to the start
 * of the minute they just failed is a bad trade in both directions — it is worse
 * than restarting voluntarily, so they stop watching, and it makes the deep
 * levels, which are the ones worth monetising, the worst place to ever show the
 * button.
 *
 * So this buys time rather than erasing it: the blocks nearest the cannon are
 * destroyed outright, a shield is granted so the next one does not end the
 * level, and play resumes on the same board with the same progress.
 *
 * ── Why the blocks pay nothing ───────────────────────────────────────────
 * The cleared blocks go through the normal explosion path but skip the reward:
 * no score, no coins, no lucky-block trigger, no splits. An ad that printed
 * money would be a paywall on progression — level 300's income is tuned against
 * a measured ~1,275 coins and handing out a board's worth for free breaks the
 * upgrade pricing. Progress *does* count, because the level's win condition is
 * "board empty" and pretending otherwise would desynchronise the bar.
 *
 * @return true if the player was actually revived.
 */
/**
 * Coins a paid revive costs at [level].
 *
 * Deliberately expensive: roughly two levels of income, so it is a real
 * decision rather than a reflex tap. Flat would be trivial late and
 * punishing early; scaling with the level keeps it the same decision
 * everywhere. Capped so a deep level cannot price it out of reach.
 */
fun reviveCoinCost(level: Int): Long =
    (400L + level.coerceAtLeast(1) * 20L).coerceAtMost(6_000L)

fun revive(): Boolean {
    if (status != GameStatus.HitByBlock) return false
    if (reviveUsedThisLevel) return false

    reviveUsedThisLevel = true

    // The lowest slice of the board, by screen position. y grows downward, so the
    // blocks closest to the cannon are the ones with the largest centre y.
    //
    // The mother is not eligible. A boss level is her and nothing else, so
    // "clear the lowest six blocks" would clear the boss: the advert would hand
    // the player the level. On a boss level the rescue is the shield alone,
    // which is the honest version of what it buys.
    val clearable = blocks.filter { !it.isMother }
    val count = max(reviveMinClear, ceil(clearable.size * reviveClearFraction).toInt())
    val victims = clearable.sortedByDescending { it.centerY }.take(count.coerceAtMost(clearable.size))

    for (block in victims) {
        spawnExplosion(block)
        spawnHitParticlesAt(block.centerX, block.centerY)
        blocks.remove(block)
        blocksDestroyed++
    }

    // The shield is the reason this is a rescue rather than a second chance at
    // the same mistake: the board is clear, but the next block to arrive still
    // ends the level unless the player spends this.
    shieldsHeld++

    onSound?.invoke(SoundEvent.BLOCK_DESTROY)
    pushPopup {
        x = fieldWidth * 0.5f
        y = fieldHeight * 0.55f
        text = "🛡️ نجات پیدا کردی!"
        color = PowerUpType.SHIELD.color
        life = 1.2f
        big = true
    }

    status = GameStatus.Playing
    // The board is clear, but the next block can arrive within a second.
    // Give the player the same visible breathing room the shield promises.
    hitGrace = 1.2f
    Telemetry.track(
        "revive",
        level = level,
        value = victims.size,
        durationMs = levelElapsedMs(),
        meta = "cleared=${victims.size}"
    )
    return true
}

// ---------------------------------------------------------------- power-ups

/** Golden bonus cubes: slower fall, spinning, expire quietly if missed. */
private fun updateBonuses(dt: Float) {
    val groundY = cannon.baseY
    var i = 0
    while (i < bonuses.size) {
        val b = bonuses[i]
        b.age += dt
        b.rotation += b.spin * dt

        b.vy += dp(28f) * dt
        b.vy = b.vy.coerceAtMost(dp(130f))
        b.x += b.vx * dt
        b.y += b.vy * dt

        if (b.x <= 0f) {
            b.x = 0f
            b.vx = -b.vx
        } else if (b.x + b.size >= fieldWidth) {
            b.x = fieldWidth - b.size
            b.vx = -b.vx
        }

        // Ground, bounced the same way a block bounces.
        //
        // This used to be `b.vy = -b.vy * 0.5f`, a restitution, which is exactly
        // the "sticks to the ground and never comes back up" behaviour in the
        // clearest form the game has: the cube fell, hopped, hopped smaller, and
        // then sat on the cannon's baseline for the rest of its 25-second life
        // with nowhere to go and nothing to collect it with. It looked broken,
        // and it was — the one thing on the field that a player can pick up was
        // the one thing they could not reach.
        //
        // Now it gets the same fixed, non-decaying apex a block does, so it keeps
        // rising to a collectable height and never settles.
        if (b.bottom >= groundY) {
            b.y = groundY - b.size
            b.vy = -bounceSpeed()
            b.vx = -b.vx * 0.85f
        }

        if (b.age > bonusLifetime) {
            // Fade out with a tiny golden puff (no reward).
            repeat(4) {
                val p = particlePool.obtain()
                p.x = b.centerX
                p.y = b.centerY
                p.vx = randomFloat(-dp(30f), dp(30f))
                p.vy = randomFloat(-dp(30f), dp(10f))
                p.life = 0.4f
                p.size = dp(2.5f)
                p.color = NeonGold
                particles += p
            }
            bonuses.removeAt(i)
        } else {
            i++
        }
    }
}

    private fun updatePowerUps(dt: Float) {
        var changed = false
        for (i in powerUpRemaining.indices) {
            val left = powerUpRemaining[i] - dt
            if (left <= 0f) {
                if (powerUpRemaining[i] != 0f) {
                    powerUpRemaining[i] = 0f
                    changed = true
                }
            } else {
                powerUpRemaining[i] = left
            }
        }
        if (powerUpFlash > 0f) powerUpFlash -= dt * 2f

        // Mirror into the HUD map a few times a second (it renders whole
        // seconds) instead of on every frame.
        powerUpSyncTimer -= dt
        if (changed || powerUpSyncTimer <= 0f) {
            powerUpSyncTimer = POWER_UP_HUD_SYNC_INTERVAL
            for (i in powerUpRemaining.indices) {
                val type = PowerUpType.entries[i]
                val left = powerUpRemaining[i]
                if (left > 0f) {
                    activePowerUps[type] = left
                } else {
                    activePowerUps.remove(type)
                }
            }
        }
    }

private fun updatePowerUpDrops(dt: Float) {
    val groundY = cannon.baseY
    var i = 0
    while (i < powerUpDrops.size) {
        val drop = powerUpDrops[i]
        drop.age += dt
        drop.rotation += drop.spin * dt

        drop.vy += dp(300f) * dt
        drop.vy = drop.vy.coerceAtMost(dp(400f))
        drop.vx -= drop.vx * dt * 1.5f
        drop.x += drop.vx * dt
        drop.y += drop.vy * dt

        // Wall bounces
        if (drop.x - drop.size * 0.5f < 0f) {
            drop.x = drop.size * 0.5f
            drop.vx = -drop.vx * 0.6f
        } else if (drop.x + drop.size * 0.5f > fieldWidth) {
            drop.x = fieldWidth - drop.size * 0.5f
            drop.vx = -drop.vx * 0.6f
        }

        // Ground check.
        //
        // A restitution, so a drop comes to rest on the floor and stays there
        // until it expires. That is right for this one and was not for the bonus
        // cube: a drop is picked up by the cannon walking into it, so a resting
        // drop is a target rather than an obstacle — the player can go and get
        // it. It rests on the cannon's own line precisely because that is where
        // the cannon goes.
        if (drop.y >= groundY - drop.size * 0.5f) {
            drop.y = groundY - drop.size * 0.5f
            if (drop.vy > dp(30f)) {
                drop.vy = -drop.vy * 0.3f
                drop.vx *= 0.7f
            } else {
                drop.vy = 0f
                drop.vx = 0f
            }
        }

        // Check cannon collision
        val distToCannon = CollisionDetector.distance(
            drop.centerX, drop.centerY,
            cannon.centerX, cannon.centerY
        )
        val cannonRadius = cannon.radius * 1.5f
        if (distToCannon < cannonRadius) {
            applyPowerUp(drop.type)
            onSound?.invoke(SoundEvent.POWER_UP_COLLECT)
            if (drop.type == PowerUpType.FREEZE) {
                onSound?.invoke(SoundEvent.FREEZE_ACTIVATE)
            }
            powerUpDrops.removeAt(i)
        } else if (drop.age > 15f) {
            // Expire after 15 seconds
            powerUpDrops.removeAt(i)
        } else {
            i++
        }
    }
}

private fun spawnPowerUpDrop(bonus: BonusBlock) {
    val drop = PowerUpDrop()
    drop.size = dp(32f)
    drop.x = bonus.centerX - drop.size * 0.5f
    drop.y = bonus.centerY - drop.size * 0.5f
    drop.vx = randomFloat(-dp(80f), dp(80f))
    drop.vy = -randomFloat(dp(100f), dp(200f))
    drop.type = bonus.type
    drop.rotation = rand.nextFloat() * 360f
    drop.spin = randomFloat(180f, 360f)
    powerUpDrops += drop
}

    /** Triggered when a ball touches a bonus cube; returns true if ball consumed. */
    private fun hitBonus(b: Ball): Boolean {
        for (bonus in bonuses) {
            // Raw-rect test: allocating a throwaway Block per ball per frame
            // was the single biggest source of GC churn in the hot loop.
            if (!CollisionDetector.ballHitsRect(
                    b.x, b.y, b.currentRadius, bonus.x, bonus.y, bonus.size, bonus.size
                )
            ) continue
        spawnPowerUpDrop(bonus)
        bonuses.remove(bonus)
        onSound?.invoke(SoundEvent.POWER_UP_SPAWN)

        repeat(10) {
            val p = particlePool.obtain()
            p.x = bonus.centerX
            p.y = bonus.centerY
            val angle = rand.nextFloat() * 6.2832f
            val speed = randomFloat(dp(40f), dp(180f))
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed
            p.life = 0.45f
            p.size = dp(randomFloat(2.5f, 5f))
            p.color = bonus.type.color
            particles += p
        }

        return b.pierce <= 0
    }
    return false
}

    private fun applyPowerUp(type: PowerUpType) {
        if (type == PowerUpType.SHIELD) {
            // A counter, not a timer. See PowerUpType.SHIELD: it is spent when a
            // block actually reaches the cannon, and stacking extra seconds would
            // be a different (and much stronger) power-up.
            shieldsHeld += 1
            onSound?.invoke(SoundEvent.POWER_UP_COLLECT)
        } else if (type == PowerUpType.NUKE) {
            // NUKE clears the board. It used to give every block its full FX set
            // (particles plus up to 75 coins each), which on a late-game board
            // meant tens of thousands of objects allocated inside one update()
            // call. Now each block just explodes, and the coin payout is a
            // single burst at the cannon.
            //
            // Iterate backwards: destroyBlock removes the current entry, so
            // forward iteration would skip elements and then index past the end.
            val hadBlocks = blocks.isNotEmpty()
            for (i in blocks.indices.reversed()) {
                destroyBlock(blocks[i], forceNoSplit = true, comboCount = 0)
            }
            for (coin in coins) coinPool.release(coin)
            coins.clear()
            if (hadBlocks) {
                repeat(NUKE_COIN_BURST) { spawnCoinsAtCannon() }
                shake = 1f
            }
        } else {
            powerUpRemaining[type.ordinal] = type.duration
            powerUpSyncTimer = 0f
            onSound?.invoke(SoundEvent.POWER_UP_COLLECT)
        }

        powerUpFlash = 1f
        powerUpFlashColor = type.color

        pushPopup {
            x = fieldWidth * 0.5f
            y = fieldHeight * 0.4f
            text = type.label
            color = type.color
            life = 1.1f
            big = true
        }
    }

    /** Lucky block - gives a random timed power-up instantly (Ball Blast style) */
    private fun triggerLuckyBlockReward(anchor: Block) {
        val luckyType = randomPowerUp()
        repeat(2) { spawnCoins(anchor) }
        applyPowerUp(luckyType)
        registerMissionProgress(lucky = 1)
        onSound?.invoke(SoundEvent.POWER_UP_COLLECT)
        if (luckyType == PowerUpType.FREEZE) onSound?.invoke(SoundEvent.FREEZE_ACTIVATE)
        pushPopup {
            x = anchor.centerX
            y = anchor.centerY - anchor.h * 0.6f
            text = "🍀 ${luckyType.label}!"
            color = NeonGold
            life = 1.2f
            big = true
        }
        repeat(18) {
            val p = particlePool.obtain()
            p.x = anchor.centerX
            p.y = anchor.centerY
            val a = rand.nextFloat() * 6.2832f
            val s = randomFloat(dp(90f), dp(260f))
            p.vx = cos(a) * s
            p.vy = sin(a) * s
            p.life = 0.55f
            p.size = dp(randomFloat(3f, 6f))
            p.color = luckyType.color
            particles += p
        }
    }

    private fun destroyBlock(
        block: Block,
        forceNoSplit: Boolean = false,
        comboCount: Int = 0
    ) {
        addScore(block.scoreValue)
        if (block.isLucky) triggerLuckyBlockReward(block)
        onSound?.invoke(if (block.isBoss) SoundEvent.BOSS_HIT else SoundEvent.BLOCK_DESTROY)
        spawnExplosion(block)
        shake = max(shake, if (block.isBoss) 1f else if (block.isLucky) 0.65f else 0.45f)

        if (comboCount >= 2) {
            onSound?.invoke(SoundEvent.COMBO)
            // Tiered. See comboMultiplierFor for why the multiplier is on the
            // score and deliberately not on the coin.
            val bonusScore = comboBonusScore(comboCount)
            addScore(bonusScore)
            if (comboCount >= 3) {
                // One extra coin per kill, from three up, and deliberately not
                // scaled by the tier. Upgrade pricing is tuned against a
                // measured ~1,275 coins of income per level; scaling this would
                // move the whole upgrade track earlier for exactly the players
                // good at chaining, which is the change least likely to be
                // noticed and hardest to undo.
                addCoinsEarned(1)
            }
            val mult = comboMultiplierFor(comboCount)
            pushPopup {
                x = fieldWidth * 0.5f
                y = fieldHeight * 0.35f
                // The multiplier only appears once it is above one. Announcing
                // "×1" every kill of every chain would be noise that teaches the
                // player to ignore the number entirely.
                text = if (mult > 1) "کمبو ×$comboCount  +$bonusScore  ($mult×)"
                else "کمبو ×$comboCount  +$bonusScore"
                color = NeonGold
                life = 0.9f
                big = true
            }
        }

        if (block.isBoss) addScore(block.scoreValue * 2)
        // The mother is scored like a boss too, and deliberately not flagged as
        // one: `isBoss` also drives the boss-hit sound and the shake, which would
        // then fire on every one of the thousands of hits she takes.
        if (block.isMother) addScore(block.scoreValue * 2)

        blocks.remove(block)

        // Big blocks (50+) crack into 4 smaller bouncy pieces instead of dying.
        // The pieces inherit the number behaviour, so they count toward progress
        // as they are destroyed; circles keep the one-shot behaviour. A NUKE
        // clears the board without splitting.
        if (forceNoSplit || !trySplitBlock(block)) {
            spawnCoins(block)
            registerMissionProgress(blocks = 1)
            blocksDestroyed++
            runBlocksDestroyed++
        }
    }

    /** Returns true when the block split into smaller pieces instead of coins. */
    private fun trySplitBlock(block: Block): Boolean {
        // Higher levels split more eagerly and into more pieces (more chaos, more fun)
        val levelThreshold = (40 - level * 0.9f).coerceAtLeast(14f)
        if (block.isFragment) return false
        // The mother is one hexagon, not a boulder: a boss level is a single
        // target, and splitting her into four thirds of her health would hand
        // the player the same board back as four ordinary blocks — the level
        // would go on after the "boss" died, which is the wall this level was
        // supposed to stop being.
        if (block.isMother) return false
        if (block.maxHp < levelThreshold) return false
        // Only convex shapes split. See BlockShape.canSplit: a star or a triangle
        // cut into four pieces looks like a rendering glitch, not a block
        // breaking.
        if (!block.shapeType.canSplit) return false
        if (block.w < dp(38f)) return false

        val pieces = when {
            level >= 12 && block.maxHp >= 120 -> 4
            level >= 7 && block.maxHp >= 80 -> 3
            else -> 2
        }
        val childHp = (block.maxHp / pieces).coerceAtLeast(1)
        val childW = (block.w * if (pieces == 2) 0.56f else 0.44f).coerceAtLeast(dp(22f))
        val childH = (block.h * if (pieces == 2) 0.56f else 0.44f).coerceAtLeast(dp(22f))

        totalBlocksInLevel += (pieces - 1)

        repeat(pieces) { i ->
            val mini = Block()
            mini.w = childW
            mini.h = childH
            mini.maxHp = childHp
            mini.hp = childHp
            mini.scoreValue = childHp
            mini.shapeType = block.shapeType
            mini.isBoss = false
            mini.isLucky = false
            mini.isFragment = true
            // The plating follows the break, at a third each. See
            // [fragmentArmour] — without this the armour a block had was thrown
            // away the instant it was destroyed.
            if (block.hasArmor) {
                val plate = fragmentArmour(block.armorHp)
                mini.hasArmor = plate > 0
                mini.armorHp = plate
                mini.armorMaxHp = plate
            }
            mini.shakePhase = rand.nextFloat() * 6.2832f
            mini.colorSeed = rand.nextFloat()
            // Fragments keep the parent's colour. Dealing them fresh palette
            // slots made one block visibly change colour as it shattered, and
            // could put two halves of the same block on screen at once.
            mini.colorIndex = block.colorIndex

            // Spread around parent center
            val angle = (i * (360f / pieces) + rand.nextFloat() * 18f - 9f) * 0.0174533f
            val offset = block.w * 0.22f
            mini.x = block.centerX - childW * 0.5f + cos(angle) * offset
            mini.y = block.centerY - childH * 0.5f + sin(angle) * offset

            val speed = randomFloat(dp(80f), dp(170f))
            mini.vx = block.vx * 0.35f + cos(angle) * speed
            mini.vy = block.vy * 0.3f + sin(angle) * speed - randomFloat(dp(20f), dp(70f))

            blocks += mini
        }
        onSound?.invoke(SoundEvent.BLOCK_CRACK)
        shake = max(shake, 0.38f)
        return true
    }

    private fun spawnExplosion(block: Block) {
        // Match the block's own colour, not the flat HP-band colour, so
        // the debris is recognisably that block's.
        val explosionColor = blockColorFor(block.colorIndex, block.maxHp, block.isBoss, block.colorSeed)

        // Two populations. Previously every particle was identical: same size
        // band, same speed band, one colour, evenly spread over 360°. The eye
        // reads that as a ring of dots scaling outward, not as a burst.
        //
        // Sparks are the small, bright, fast, short-lived half; debris is the
        // larger, slower, longer-lived half. The asymmetry is the point — it
        // is what gives the burst a readable centre and a soft edge.
        val sparkCount = 10 + rand.nextInt(6)
        val debrisCount = 6 + rand.nextInt(5)

        repeat(sparkCount + debrisCount) { i ->
            val isSpark = i < sparkCount
            val p = particlePool.obtain()
            p.x = block.centerX
            p.y = block.centerY
            // Full circle rather than an upward-biased fan: the block explodes
            // in all directions, and the gravity added in updateParticles is
            // what produces the natural downward sag afterwards.
            val angle = rand.nextFloat() * 6.2832f
            val speed = if (isSpark) {
                randomFloat(dp(140f), dp(420f))
            } else {
                randomFloat(dp(50f), dp(190f))
            }
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed
            p.life = if (isSpark) randomFloat(0.22f, 0.42f) else randomFloat(0.40f, 0.72f)
            val size = if (isSpark) randomFloat(1.6f, 3.4f) else randomFloat(3.4f, 6.6f)
            p.size = dp(size)
            p.spark = isSpark
            p.color = if (isSpark) {
                // Sparks run hot: the block's colour pushed toward white.
                blockColorTint(explosionColor, 1.35f)
            } else {
                explosionColor
            }
            particles += p
        }

        explosions += Explosion().apply {
            x = block.centerX
            y = block.centerY
            startRadius = block.w * 0.45f
            endRadius = block.w * 1.9f
            // A boss dying is a bigger event than a level-1 block; the old
            // fixed 0.45s made both read identically.
            life = if (block.isBoss) 0.62f else 0.42f
            color = explosionColor
        }
    }

    private fun spawnCoins(block: Block) {
        val coinRush = if (hasPowerUp(PowerUpType.COIN_RUSH)) 3 else 1
        val bossMultiplier = if (block.isBoss) 3 else 1
        // COIN RUSH x boss hit used to reach 75 coins from a single block, and
        // every one of them is simulated and drawn. Cap the burst.
        //
        // 2-4 per block, paid at 1:1. Down one point from the 3-5 this shipped
        // with, and COIN RUSH down from five stacks to three.
        //
        // Why now: the paid revive is new. A rescue has to cost something real
        // or nobody touches it — a free "keep my level" is a button players
        // learn to mash, which hands back the exact thing the hit was meant to
        // cost. Coins are the only currency a player can spend *and* earn, so
        // that is where a rescue belongs, and a paid rescue is only believable
        // if the free income that competes with it is worth having.
        //
        // The price is deliberately deep (see reviveCoinCost) so the coin packs
        // still dominate: a player buying a pack has to be buying a faster
        // route than 60 minutes of tapping, and trimming passive income widens
        // that gap rather than narrowing it. Nothing here touches what a pack
        // is worth — every purchase is exactly as good as it was, and the same
        // for premium.
        val count = when {
            // She stands in for the whole board — see MOTHER_COINS_PER_BLOCK.
            block.isMother ->
                (levelCfg.blockCount * MOTHER_COINS_PER_BLOCK)
                    .coerceIn(1, MOTHER_MAX_COIN_BURST)
            else ->
                ((2 + rand.nextInt(3)) * coinRush * bossMultiplier)
                    .coerceIn(1, MAX_COINS_PER_BLOCK)
        }
        spawnCoinBurst(block.centerX, block.centerY, count, block.isBoss)
    }

    /** Coins fanned out from the cannon, used by the NUKE payout. */
    private fun spawnCoinsAtCannon() {
        spawnCoinBurst(cannon.centerX, cannon.centerY, 3, false)
    }

    private fun spawnCoinBurst(originX: Float, originY: Float, count: Int, boss: Boolean) {
        for (i in 0 until count) {
            val c = coinPool.obtain()
            c.x = originX
            c.y = originY
            val angle = rand.nextFloat() * 6.2832f
            val speed = randomFloat(dp(40f), dp(200f))
            c.vx = cos(angle) * speed
            c.vy = sin(angle) * speed * 0.7f
            c.rotation = rand.nextFloat() * 360f
            c.spinSpeed = randomFloat(240f, 520f)
            c.life = 1f
            coins += c
        }
    }

    // ---------------------------------------------------------------- coins

    private fun updateCoins(dt: Float) {
    val groundY = cannon.baseY
    val coinR = dp(11f)
    val manualRadius = cannon.radius * 1.8f
    val magnetRadius = cannon.magnetRadius

    var i = 0
    while (i < coins.size) {
        val c = coins[i]
        c.age += dt
        c.rotation += c.spinSpeed * dt

        // A rising magnet upgrade starts pulling coins as soon as they spawn.
        if (c.state == CoinState.Free && magnetRadius > 0f &&
            CollisionDetector.distance(c.x, c.y, cannon.centerX, cannon.centerY) < magnetRadius
        ) {
            c.state = CoinState.Magnetized
        }

        when (c.state) {
            CoinState.Free -> {
                // Gravity + wall/floor bounces, then rest on the floor.
                c.vy += dp(420f) * dt
                c.vy = c.vy.coerceAtMost(dp(280f))
                c.vx -= c.vx * dt * 1.5f
                c.x += c.vx * dt
                c.y += c.vy * dt

                if (c.x - coinR < 0f) {
                    c.x = coinR
                    c.vx = -c.vx * 0.6f
                } else if (c.x + coinR > fieldWidth) {
                    c.x = fieldWidth - coinR
                    c.vx = -c.vx * 0.6f
                }

                if (c.y >= groundY - coinR) {
                    c.y = groundY - coinR
                    if (c.vy > dp(30f)) {
                        c.vy = -c.vy * 0.35f
                        c.vx *= 0.7f
                    } else {
                        c.vy = 0f
                        c.vx = 0f
                    }
                }
            }

            CoinState.Magnetized -> {
                // Acceleration toward the cannon for a satisfying swoosh.
                val dx = cannon.centerX - c.x
                val dy = cannon.centerY - c.y
                val dist = kotlin.math.hypot(dx, dy)
                if (dist > 1f) {
                    c.vx += dx / dist * dp(1400f) * dt
                    c.vy += dy / dist * dp(1400f) * dt
                    val speed = kotlin.math.hypot(c.vx, c.vy)
                    val maxSpeed = dp(520f)
                    if (speed > maxSpeed) {
                        c.vx = c.vx / speed * maxSpeed
                        c.vy = c.vy / speed * maxSpeed
                    }
                    c.x += c.vx * dt
                    c.y += c.vy * dt
                }
            }
        }

        val distToCannon =
            CollisionDetector.distance(c.x, c.y, cannon.centerX, cannon.centerY)

        val collected = when {
            // Moving the cannon over the coins picks them up.
            distToCannon < manualRadius -> true
            c.state == CoinState.Magnetized && distToCannon < dp(26f) -> true
            // Only sweep up unattended coins much later, and never while the
            // magnet upgrade is owned: auto-collecting after 12s silently
            // converted every coin the player had not reached yet, which made
            // the collect radius and magnet upgrades meaningless.
            c.state == CoinState.Free && magnetRadius <= 0f && c.age >= 45f -> true
            else -> false
        }

        if (collected) {
            onCoinCollected()
            coinPool.release(c)
            coins.removeAt(i)
        } else {
            i++
        }
    }
}

    private fun onCoinCollected() {
        onSound?.invoke(SoundEvent.COIN)
        // One coin entity, one coin. This used to run through a fraction
        // bank — a numerator, a denominator and a remainder accumulator — so
        // a fraction could be credited without throwing coins away. The
        // fraction is 1/1, so the bank added one to the remainder, saw it was
        // whole, and paid it out: three fields and a comment block to do
        // `addCoinsEarned(1)`.
        addCoinsEarned(1)
        registerMissionProgress(coins = 1)
        if (coinsSinceSave >= 20) {
            flushCoins()
        }
    }

    /**
     * Buffers daily-mission progress in memory and only touches
     * SharedPreferences when the buffer is worth a write.
     *
     * Every destroyed block and every collected coin used to call
     * [SaveManager.addMissionProgress] directly, which re-read the prefs file,
     * built a new day-check and queued an async disk write for *each* one — up
     * to ~25 serialised preferences transactions in a single frame. That disk IO
     * inside the 16.6ms budget was the main source of stutter.
     */
    private fun registerMissionProgress(blocks: Int = 0, coins: Int = 0, lucky: Int = 0) {
        pendingMissionBlocks += blocks
        pendingMissionCoins += coins
        pendingMissionLucky += lucky
        pendingMissionTotal += (blocks + coins + lucky)
        if (pendingMissionTotal >= MISSION_FLUSH_THRESHOLD) flushMissionProgress()
    }

    /** Writes any buffered mission progress. Safe to call at any time. */
    fun flushMissionProgress() {
        if (pendingMissionTotal <= 0) return
        val b = pendingMissionBlocks
        val c = pendingMissionCoins
        val l = pendingMissionLucky
        pendingMissionBlocks = 0
        pendingMissionCoins = 0
        pendingMissionLucky = 0
        pendingMissionTotal = 0
        saveManager.addMissionProgress(blocks = b, coins = c, lucky = l)
    }

    private inline fun pushPopup(init: PopupText.() -> Unit) {
        // Hard cap: a dense board with piercing multi-shot could otherwise stack
        // hundreds of live popups, each also allocating a String.
        if (popups.size >= MAX_LIVE_POPUPS) popups.removeAt(0)
        val p = popupPool.obtain()
        p.reset()
        p.init()
        popups += p
    }

    /** Persists earned coins to the save with debounce. */
    fun flushCoins() {
        flushMissionProgress()
        if (coinsSinceSave > 0) {
            // Premium doubles the payout here rather than at every collection,
            // so the number of coin entities is unchanged and the display
            // still animates the same way.
            saveManager.addCoins((coinsSinceSave * saveManager.coinMultiplier()).toLong())
            coinsSinceSave = 0
        }
    }

    // ---------------------------------------------------------------- fx / popups

    /**
     * Per-frame drag applied to every particle.
     *
     * Fraction of velocity retained per second, as a rate. A constant 2.0 was
     * the old single knob and it was the reason debris flew in dead-straight
     * lines at constant speed: exponential drag with no gravity has no
     * terminal behaviour to read as "settling".
     */
    private val particleDrag = 2.6f

    /** Downward acceleration, px/s². Scaled to density like every other unit. */
    private val particleGravity = dp(900f)

    private fun updateParticles(dt: Float) {
        // Exponential decay, integrated per frame. Computing it as a
        // multiplicative factor from the rate (rather than the old inline
        // `v *= 1 - dt*2`, which goes *negative* — and would reverse a
        // particle's direction — whenever dt exceeds 0.5s, e.g. after a long
        // GC pause or when the app is resumed) keeps it stable and monotonic.
        val decay = (1f - particleDrag * dt).coerceAtLeast(0f)

        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.age += dt
            // Integrate in the right order: apply forces to velocity, then
            // velocity to position. Doing it the other way round makes every
            // particle lag a frame behind the effect it belongs to.
            p.vy += particleGravity * dt
            p.vx *= decay
            p.vy *= decay
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.age >= p.life) {
                particlePool.release(p)
                particles.removeAt(i)
            } else {
                i++
            }
        }

        var j = 0
        while (j < explosions.size) {
            val e = explosions[j]
            e.age += dt
            if (e.age >= e.life) {
                explosions.removeAt(j)
            } else {
                j++
            }
        }
    }

    private fun updatePopups(dt: Float) {
        var i = 0
        while (i < popups.size) {
            val p = popups[i]
            p.age += dt
            // Each popup rises at a speed of its own. This was one speed for
            // the whole list, read off popups[0] — so a single big combo
            // announcement slowed every damage number on screen to a drift, and
            // a field of small ones made the big ones dart away.
            p.y -= dp(if (p.big) 12f else 28f) * dt
            if (p.age >= p.life) {
                popupPool.release(p)
                popups.removeAt(i)
            } else {
                i++
            }
        }
    }

    /** Call when the level is won to visually suck every remaining coin to the cannon */
    fun collectAllRemainingCoins() {
        if (coins.isEmpty()) return
        // Instantly convert all floating coins to collected ones with a satisfying burst
        val count = coins.size
        for (c in coins) coinPool.release(c)
        coins.clear()
        repeat(count) { onCoinCollected() }
        // Add a coin shower particle burst at cannon
        repeat(count * 2) {
            val p = particlePool.obtain()
            p.x = cannon.centerX
            p.y = cannon.centerY
            val a = rand.nextFloat() * 6.2832f
            val s = randomFloat(dp(80f), dp(220f))
            p.vx = cos(a) * s
            p.vy = sin(a) * s - dp(60f)
            p.life = 0.6f
            p.size = dp(randomFloat(2f, 4f))
            p.color = NeonGold
            particles += p
        }
        flushCoins()
    }

    private fun checkLevelComplete() {
        if (status != GameStatus.Playing) return

        // A boss level is not finished while she is alive. On a boss level that
        // is also the only thing on the board, so the ordinary conditions below
        // are exactly "is the board empty" — they used to be unreachable on a
        // boss level, because the queue never drained, and that is what kept a
        // killed mother from ending anything.
        if (motherAlive()) return
        // …and not finished before she has arrived either. A boss level's queue is
        // empty from the first frame, so without this the level would complete
        // itself in the one frame between [startLevel] and her spawn.
        if (motherPendingSpawn) return

        if (blocksToSpawn == 0 && blocks.isEmpty() && !spawnTicking) {
            // Flip the status *first*. The old order saved the level and played
            // sounds before setting the status, so if anything in between threw
            // the status stayed Playing and this ran again on every frame,
            // incrementing the persisted level without bound.
            status = GameStatus.LevelComplete
            // Auto-collect every leftover coin before showing the win screen
            collectAllRemainingCoins()
            flushCoins()
            flushMissionProgress()
            saveManager.nextLevel()
            runLevelsCleared++
            Telemetry.addLevelComplete()
            Telemetry.track(
                "level_complete",
                level = level,
                value = score,
                durationMs = levelElapsedMs(),
                meta = "blocks=${totalBlocksInLevel}"
            )
            Telemetry.flush()
            onSound?.invoke(SoundEvent.LEVEL_COMPLETE)
            onGameComplete?.invoke()
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Absolute finger position, call from UI thread. */
    fun moveCannonTo(x: Float) {
        // Counted even if the target barely changed: what the coach needs to know
        // is that the player is dragging, not that the cannon ended up somewhere
        // new. See [cannonMoves].
        cannonMoves++
        cannon.targetX = x
    }

    fun togglePause() {
        status = when (status) {
            GameStatus.Playing -> GameStatus.Paused
            GameStatus.Paused -> GameStatus.Playing
            else -> status
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Colour space helpers
//
//  Compose 1.8 has no public Color.toHsl() — the one that used to exist is
//  gone from ui-graphics, so depending on it would mean this file compiles on
//  one Compose version and not the next. These are the standard formulas,
//  written out so the colour maths is explicit and version-independent.
// ─────────────────────────────────────────────────────────────────────────

/** hue in [0,1), saturation and lightness in [0,1]. */
private fun Color.rgbToHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)          // achromatic

    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return floatArrayOf(h, s, l)
}

/** Packs hue/saturation/lightness back into a Color, preserving alpha. */
private fun hsl(h: Float, s: Float, l: Float, alpha: Float): Color {
    val hue = ((h % 1f) + 1f) % 1f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((hue * 6f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when ((hue * 6f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f), alpha)
}

/**
 * The second stop of a block's vertical gradient — the deeper, richer version
 * of its own colour.
 *
 * Painted from the top stop so every block gets a lit-from-above look for free
 * and stops reading as a flat rectangle. Derived by dropping lightness in HSL
 * rather than by multiplying RGB, because a multiply darkens saturated hues
 * unevenly (blue goes to near-black, yellow barely moves) and produces muddy
 * results exactly on the most colourful blocks.
 */
fun blockColorShade(color: Color, depth: Float = 0.82f): Color {
    val hsl = color.rgbToHsl()
    return hsl(
        hsl[0],
        (hsl[1] * 1.06f).coerceIn(0f, 1f),      // deepen as it darkens
        (hsl[2] * depth).coerceIn(0.08f, 1f),
        color.alpha
    )
}

/** The lit top stop: same hue, lifted lightness. */
fun blockColorTint(color: Color, lift: Float = 1.14f): Color {
    val hsl = color.rgbToHsl()
    return hsl(
        hsl[0],
        (hsl[1] * 0.92f).coerceIn(0f, 1f),      // desaturate as it lightens
        (hsl[2] * lift).coerceIn(0f, 1f),
        color.alpha
    )
}

/**
 * A block's colour, from the palette slot the engine dealt it.
 *
 * This replaces "colour = f(HP)" as the block's identity. HP still plays a
 * part, but only as a *depth* adjustment on top of the dealt hue: a tougher
 * block is a deeper, richer version of its colour and an easy one is brighter.
 * That keeps the difficulty read that the old HP bands gave — a glance still
 * separates a heavy block from a soft one — while hue, which is what the eye
 * actually distinguishes, stays unique per block.
 *
 * [colorSeed] adds a little tone variation so two blocks that share a slot
 * after a deck cycle are not pixel-identical, and the alpha is copied from the
 * caller's damage-based translucency so the gradient stops built from it all
 * fade together.
 */
fun blockColorFor(index: Int, hp: Int, boss: Boolean, colorSeed: Float, alpha: Float = 1f): Color {
    if (boss) return BossColor.copy(alpha = alpha)
    val base = BlockPalette[((index % BlockPalette.size) + BlockPalette.size) % BlockPalette.size]
    val hsl = base.rgbToHsl()
    // Depth ramp: 0 at HP 1, 1 at HP 300 and above, so every reachable HP maps
    // somewhere on the scale without the curve ever pinning at either end.
    val toughness = (hp.coerceIn(1, 300) / 300f)
    // Two decorrelated rolls from one seed, so a block does not land on max
    // variation in every channel at once.
    val v1 = (colorSeed * 587f) % 1f
    val v2 = (colorSeed * 331f) % 1f
    return hsl(
        hsl[0],
        (hsl[1] * (0.92f + toughness * 0.16f) + (v1 - 0.5f) * 0.10f).coerceIn(0.34f, 1f),
        // Floor raised from 0.34 to 0.40. On the light daylight backdrop the
        // darkest fills were the hardest to see, and the outline in
        // blockOutlineColor is what carries the silhouette — a fill too dark to
        // read leaves a dark blob on dark ground with nothing to separate it.
        (hsl[2] * (1.06f - toughness * 0.22f) + (v2 - 0.5f) * 0.06f).coerceIn(0.40f, 0.80f),
        alpha
    )
}

/**
 * The mother's colour, or null for anything that is not her.
 *
 * ── Why she is not on the palette ────────────────────────────────────────
 * Every normal block draws its colour from a 16-entry deck so that no two blocks
 * in flight look alike. The mother has to break that rule: she is one object
 * and her children inherit her `colorIndex`, so the whole family shares a
 * colour. Giving her a palette slot would put her inside the rotation and let a
 * child land on her own slot.
 *
 * She is crimson with a violet cast, which is a hue the palette does not use at
 * that lightness — the nearest entries are the pure reds at 0° and 355°, both
 * flat. The violet is what separates "the boss" from "a red block" at a glance
 * on a crowded board, and it shifts toward magenta as she enrages so the phase
 * change is visible on her body and not only in the spawn rate.
 */
fun motherColor(hpRatio: Float, enraged: Boolean, alpha: Float = 1f): Color {
    val base = if (enraged) Color(0xFFEC4899) else Color(0xFFB4185F)
    val hsl = base.rgbToHsl()
    return hsl(
        hsl[0],
        hsl[1],
        (hsl[2] * (0.85f + (1f - hpRatio.coerceIn(0f, 1f)) * 0.25f)).coerceIn(0f, 0.72f),
        alpha
    )
}

data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val twinklePhase: Float
)

/**
 * The dark rim drawn around a block, so its silhouette survives a bright scene.
 *
 * ── Why blocks need an outline on a light background ──────────────────────
 * Block fill colours are saturated mid-tones by design, and that is fine against
 * a dark sky — it is exactly why the neon look worked. Measured against the
 * daylight backdrop's green terrain (#2C8B60), the *worst* fill in the palette
 * (#FF3B30 red) sat at a contrast ratio of 1.00:1 — literally the same
 * luminance as the ground behind it. Reds, greens and mid blues all fell below
 * 1.4:1, so a third of the palette vanished into the hills and the player could
 * not see what was falling toward their cannon.
 *
 * Raising the terrain's contrast instead is not the fix: making the ground dark
 * again is what the light theme was for, and making it white would fight every
 * block colour in turn. The fix is the one every legible game uses — a
 * contrasting edge that is independent of the fill. The rim is the block's own
 * hue pushed to low lightness, so it stays related to the fill (a red block gets
 * a dark red rim, a blue one a dark blue) while sitting well clear of any
 * background. Measured that way, the worst rim against the green terrain is
 * 2.07:1 and against the pale horizon 7.05:1, versus 1.00:1 and 1.15:1 for the
 * fills.
 *
 * Also raises the lightness floor in [blockColorFor]'s clamp, because a rim
 * cannot help a fill that is itself too dark to see.
 *
 * ── The rim's polarity depends on the scene ──────────────────────────────
 * A dark rim is the right answer on a light background and the *wrong* one on a
 * dark one: measured against the cosmic terrain, the fills sit at 2.73:1 while a
 * dark rim measures 1.00:1 — invisible. So [blockOutlineColor] flips to a light
 * rim on dark scenes, which is what makes a mid-tone block pop against a night
 * sky. The caller passes the backdrop's luminance rather than this guessing it,
 * because the same block on the same level may sit over sky or over terrain.
 */
fun blockOutlineColor(color: Color, againstBackgroundIsLight: Boolean = true): Color {
    val hsl = color.rgbToHsl()
    return if (againstBackgroundIsLight) {
        // Light scene: a dark rim. Measured against the daylight near-ground
        // (#5CBE8C) the weakest fill in the palette sits at 1.10:1 and this rim
        // lifts the worst case to 4.56:1. Not black — a pure black rim on a
        // candy-coloured block reads as a cutout.
        hsl(hsl[0], (hsl[1] * 1.15f).coerceIn(0f, 1f), (hsl[2] * 0.30f).coerceIn(0.06f, 0.26f), color.alpha)
    } else {
        // Dark scene: the dark rim would be *worse* than no rim — against the
        // cosmic terrain (#412C70) the fills measure 2.73:1 but a dark rim only
        // 1.00:1, i.e. invisible. So the polarity flips: a light rim separates a
        // mid-tone block from a dark sky, which is the neon look these scenes
        // were built for.
        hsl(hsl[0], (hsl[1] * 0.85f).coerceIn(0f, 1f), (hsl[2] * 1.55f + 0.30f).coerceIn(0.78f, 1f), color.alpha)
    }
}

/** Relative luminance, used to choose the rim polarity in [blockOutlineColor]. */
fun Color.relativeLuminance(): Float {
    fun ch(v: Float): Float = if (v <= 0.03928f) v / 12.92f
    else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * ch(red) + 0.7152f * ch(green) + 0.0722f * ch(blue)
}