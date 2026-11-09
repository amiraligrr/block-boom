package ir.amiraligrr.blockboom.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.domain.SoundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Enhanced SoundPool with preloading, music, haptics and organic pitch layering.
 * Makes the game feel addictive (پولیش صوتی معتادکننده).
 */
class SoundManager(val context: Context, private val saveManager: SaveManager) {

    private val pool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } catch (t: Throwable) {
        Log.w(TAG, "SoundPool unavailable", t)
        null
    }

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentMusicRes: Int? = null
    private val ids = HashMap<SoundEvent, Int>()
    private val loaded = HashSet<Int>()
    private val rand = Random(System.nanoTime())
    // For combo pitch escalation
    private var coinPitchStreak = 0
    private var lastCoinTime = 0L
    private var hitCount = 0
    // Anti-fatigue: debounce repetitive sounds so they never feel spammy
    private val lastPlayMs = HashMap<SoundEvent, Long>()
    private val recentPlays = ArrayDeque<Long>() // timestamps of last plays for global throttling
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()

    // Audio focus: without this the music keeps playing over a phone call, a
    // navigation prompt, or another app's audio, and Android may not even tell
    // us to stop.
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var wantsToPlay = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                try { mediaPlayer?.takeIf { it.isPlaying }?.pause() } catch (_: Throwable) {}
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                try { mediaPlayer?.takeIf { it.isPlaying }?.pause() } catch (_: Throwable) {}
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duckMusic(true)
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                duckMusic(false)
                if (wantsToPlay) resumeMusic()
            }
        }
    }

    init {
        pool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        preloadAll()
        // Don't auto-init music here — let screens choose home vs game track
    }

    private fun preloadAll() {
        val p = pool ?: return
        try {
            // Map each unique raw to an event (duplicates share same file but distinct pitch)
            val map = mapOf(
                SoundEvent.FIRE to R.raw.fire,
                SoundEvent.BLOCK_CRACK to R.raw.block_crack,
                SoundEvent.BLOCK_DESTROY to R.raw.block_destroy,
                SoundEvent.COIN to R.raw.coin,
                SoundEvent.POWER_UP_SPAWN to R.raw.powerup,
                SoundEvent.BOSS_HIT to R.raw.boss_hit,
                SoundEvent.BOSS_SPAWN to R.raw.boss_spawn,
                SoundEvent.LEVEL_COMPLETE to R.raw.level_complete,
                SoundEvent.GAME_OVER to R.raw.game_over
            )
            for ((ev, res) in map) {
                val id = p.load(context, res, 1)
                ids[ev] = id
            }
            // Aliases share same sample but with different play params
            ids[SoundEvent.BLOCK_HIT] = ids[SoundEvent.BLOCK_DESTROY]!!
            ids[SoundEvent.POWER_UP_COLLECT] = ids[SoundEvent.POWER_UP_SPAWN]!!
            ids[SoundEvent.FREEZE_ACTIVATE] = ids[SoundEvent.POWER_UP_SPAWN]!!
            ids[SoundEvent.HEART_LOST] = ids[SoundEvent.GAME_OVER]!!
            ids[SoundEvent.COMBO] = ids[SoundEvent.COIN]!!
        } catch (t: Throwable) {
            Log.w(TAG, "preload failed", t)
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────────
    //
    //  Everything below exists because of one bug: "the screen freezes when you
    //  lose".
    //
    //  Losing navigates from the game route to the game-over route, and the music
    //  is chosen by route. So the loss itself requested a *different* track, which
    //  called `MediaPlayer.create` — a blocking call that decodes the whole OGG —
    //  on the main thread, mid-navigation. The UI stopped drawing for as long as
    //  it took, which on a slow phone is long enough for the player to tap again
    //  and see nothing happen. The freeze was not the loss; it was the soundtrack
    //  change that came with it, and it was on the one transition the player is
    //  most likely to be watching.
    //
    //  So preparation moved off the main thread, and prepared players are kept
    //  rather than released. Switching between the game track and the home track
    //  — which is what every navigation in the app does — is now a pause of one
    //  and a start of the other, with no decode at all.
    private val musicScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Prepared players by resource id. Never handed back to the pool. */
    private val musicCache = HashMap<Int, android.media.MediaPlayer>()

    /**
     * Set by [release] so a music prepare that is still in flight knows its
     * player will never be cached, and releases it instead.
     */
    @Volatile private var released = false

    /**
     * The track the last request asked for.
     *
     * A prepare in flight can finish after the player has moved on — tap Shop,
     * go back, tap Play. Whoever asked last wins, and the loser is released rather
     * than left attached and silent.
     */
    private var wantedMusicRes: Int? = null

    /** How a track is played: tempo, where it starts, how loud, how it fades in. */
    private data class MusicStyle(
        val speedMin: Float,
        val speedMax: Float,
        /** Only seek into the track if it is longer than this, in ms. */
        val seekFloorMs: Int,
        /** How far into the track the random start point may land, in ms. */
        val seekWindowMs: Int,
        val volume: Float,
        val fadeMs: Long
    )

    private val GAME_STYLE = MusicStyle(0.97f, 1.04f, 8_000, 4_000, 0.60f, 700L)
    private val HOME_STYLE = MusicStyle(0.96f, 1.01f, 6_000, 3_000, 0.38f, 1_000L)

    /**
     * Plays [resId], preparing it first if it is not already.
     *
     * Cached tracks swap synchronously; a first-time track is prepared on
     * [musicScope] and started on the main thread when it is ready. Both paths
     * end in [startAttached], so the music that plays is identical either way —
     * only the wait before it starts differs, and that wait is no longer on the
     * thread that draws the screen.
     */
    private fun switchTo(resId: Int, style: MusicStyle) {
        if (!saveManager.musicEnabled) return
        wantedMusicRes = resId

        val cached = synchronized(musicCache) { musicCache[resId] }
        if (cached != null) {
            attach(resId, cached)
            startAttached(cached, style)
            return
        }

        musicScope.launch {
            val prepared = createMusicPlayer(resId) ?: return@launch
            // Whether this player was handed to the cache (and so is somebody
            // else's responsibility to release) or is still ours.
            var handedOff = false
            try {
                withContext(Dispatchers.Main) {
                    // The manager was torn down while this was decoding. Nothing
                    // is left to attach it to, and the cache walk in [release]
                    // has already been past this point.
                    if (released) return@withContext
                    // Something else asked for a track while this one was decoding.
                    // Discard it rather than caching it: it is a live decoder, and
                    // nothing is playing it. Whoever asked last wins.
                    if (wantedMusicRes != resId) return@withContext

                    // Adopt-or-release. Two requests for the same track inside one
                    // decode window — a Game -> Shop -> Game burst, and
                    // `startGameMusic()` picks `game_music` most of the time — both
                    // reach this point and both pass the check above. The second
                    // write simply overwrote the first in the map, and an
                    // overwritten player is in no map at all: still a live decoder
                    // holding native audio resources, and [release], which walks
                    // only the cache, never touched it. The first one to arrive
                    // wins and keeps the slot; the other is released here.
                    val existing = synchronized(musicCache) {
                        musicCache.putIfAbsent(resId, prepared)
                    }
                    if (existing != null) {
                        handedOff = true
                        try { prepared.release() } catch (_: Throwable) {}
                        return@withContext
                    }

                    handedOff = true
                    attach(resId, prepared)
                    // The app may have been backgrounded while this was decoding, in
                    // which case [pauseMusic] had no player to pause. Honour the flag
                    // rather than starting music into a backgrounded app.
                    if (!wantsToPlay) return@withContext
                    startAttached(prepared, style)
                }
            } finally {
                // Covers the paths where this player was never adopted: cancelled
                // mid-handoff (which is why the cache walk happens after the scope
                // is cancelled), superseded by another track, or the manager
                // released underneath it. Without this the player was simply lost.
                if (!handedOff) {
                    try { prepared.release() } catch (_: Throwable) {}
                }
            }
        }
    }

    /** Builds a looping player off the main thread. Null if the resource is unreadable. */
    private fun createMusicPlayer(resId: Int): android.media.MediaPlayer? = try {
        (try {
            android.media.MediaPlayer.create(context, resId)
        } catch (_: Throwable) {
            android.media.MediaPlayer.create(context, R.raw.bg_music)
        })?.apply {
            isLooping = true
            setVolume(0.55f, 0.55f)
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
            } catch (_: Throwable) {}
        }
    } catch (t: Throwable) {
        Log.w(TAG, "music init failed", t)
        null
    }

    /**
     * Makes [mp] the live player, without releasing the one it replaces.
     *
     * Releasing the outgoing player is what used to make each transition cost a
     * fresh decode; it is only ever released in [release] and on a superseded
     * prepare. Pausing it is enough — it is already decoded and looping.
     */
    private fun attach(resId: Int, mp: android.media.MediaPlayer) {
        if (mediaPlayer === mp) return
        try { mediaPlayer?.takeIf { it.isPlaying }?.pause() } catch (_: Throwable) {}
        cancelFades()
        mediaPlayer = mp
        currentMusicRes = resId
    }

    /** Seek, tempo, start, fade. Must run on the main thread. */
    private fun startAttached(mp: android.media.MediaPlayer, style: MusicStyle) {
        try {
            val fresh = !mp.isPlaying
            if (fresh) {
                val dur = try { mp.duration } catch (_: Throwable) { 0 }
                if (dur > style.seekFloorMs) {
                    mp.seekTo(rand.nextInt((dur - style.seekWindowMs).coerceAtLeast(1)))
                } else {
                    mp.seekTo(0)
                }
            }
            // Slight speed/pitch humanization.
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                try {
                    val pp = mp.playbackParams
                    pp.speed = style.speedMin + rand.nextFloat() * (style.speedMax - style.speedMin)
                    mp.playbackParams = pp
                } catch (_: Throwable) {}
            }
            if (fresh) mp.start()
            // Louder, with slight randomization.
            fadeTo(style.volume + rand.nextFloat() * 0.06f, style.fadeMs)
        } catch (t: Throwable) {
            Log.w(TAG, "music start failed", t)
        }
    }

    fun startMusic() = startGameMusic()

    /** Exciting fast track for gameplay — louder & punchier with humanized start */
    fun startGameMusic() {
        if (!saveManager.musicEnabled) return
        wantsToPlay = true
        requestFocus()
        // Randomly pick exciting variation: 70% game_music, 30% bg_music for surprise
        val pick = if (rand.nextFloat() < 0.7f) R.raw.game_music else R.raw.bg_music
        switchTo(pick, GAME_STYLE)
    }

    /** Chill mellow track for home/menu — calm but not boring */
    fun startHomeMusic() {
        if (!saveManager.musicEnabled) return
        wantsToPlay = true
        requestFocus()
        switchTo(R.raw.home_music, HOME_STYLE)
    }

    fun pauseMusic() {
        wantsToPlay = false
        abandonFocus()
        try { mediaPlayer?.takeIf { it.isPlaying }?.pause() } catch (_: Throwable) {}
    }

    fun duckMusic(duck: Boolean) {
        try {
            val base = when (currentMusicRes) {
                R.raw.game_music -> 0.64f
                R.raw.home_music -> 0.42f
                else -> 0.52f
            }
            val v = if (duck) base * 0.33f else base
            mediaPlayer?.setVolume(v, v)
        } catch (_: Throwable) {}
    }

    /**
     * Turning music off used to only flip a preference; the current track kept
     * playing until something else happened to restart it, which is why the
     * settings toggle appeared to do nothing.
     */
    fun setMusicEnabled(enabled: Boolean) {
        saveManager.musicEnabled = enabled
        if (enabled) startMusic() else pauseMusic()
    }

    private fun requestFocus() {
        val am = audioManager ?: return
        try {
            if (hasAudioFocus) return
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val result = if (android.os.Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (t: Throwable) {
            Log.w(TAG, "audio focus request failed", t)
            // Without focus the music still plays; better than silence.
            hasAudioFocus = true
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        try {
            if (!hasAudioFocus) return
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusListener)
            }
            hasAudioFocus = false
        } catch (_: Throwable) {}
    }

    fun setMusicVolume(vol: Float) {
        try { mediaPlayer?.setVolume(vol.coerceIn(0f,1f), vol.coerceIn(0f,1f)) } catch (_: Throwable) {}
    }

    /** Resume music without re-randomizing seek/speed (for lifecycle resume) */
    fun resumeMusic() {
        if (!saveManager.musicEnabled) return
        try {
            val mp = mediaPlayer ?: return
            if (!mp.isPlaying) {
                mp.start()
                fadeTo(when (currentMusicRes) {
                    R.raw.game_music -> 0.64f
                    R.raw.home_music -> 0.42f
                    else -> 0.52f
                }, 500)
            }
        } catch (_: Throwable) {}
    }

    /** Track current animator for cancellation */
    private var currentFadeAnimator: android.animation.ValueAnimator? = null

    private fun fadeTo(target: Float, duration: Long) {
        try {
            val mp = mediaPlayer ?: return
            currentFadeAnimator?.cancel()
            val animator = android.animation.ValueAnimator.ofFloat(0f, target).apply {
                this.duration = duration
                addUpdateListener { a ->
                    val v = a.animatedValue as Float
                    try { mp.setVolume(v, v) } catch (_: Throwable) {}
                }
                start()
            }
            currentFadeAnimator = animator
        } catch (_: Throwable) {
            try { mediaPlayer?.setVolume(target, target) } catch (_: Throwable) {}
        }
    }

    private fun cancelFades() {
        currentFadeAnimator?.cancel()
        currentFadeAnimator = null
    }

    fun release() {
        cancelFades()
        // Set before the scope is cancelled, so an in-flight `switchTo` that is
        // resumed on the main thread before it reaches the cache sees that there
        // is nothing left to attach to.
        released = true
        musicScope.cancel()
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {}
        mediaPlayer = null
        // Every cached player is a live decoder, not just the attached one.
        synchronized(musicCache) {
            for (mp in musicCache.values) {
                try { mp.release() } catch (_: Throwable) {}
            }
            musicCache.clear()
        }
        currentMusicRes = null
        wantedMusicRes = null
        try {
            pool?.release()
        } catch (_: Throwable) {
            // already released
        }
        ids.clear()
        loaded.clear()
    }

    /** Play with optional volume/pitch randomization for organic feel */
    private fun playInternal(event: SoundEvent, baseVolume: Float = 1f, pitchVariance: Float = 0f, pitchOverride: Float? = null) {
        if (!saveManager.soundEnabled) return
        val id = ids[event] ?: return
        // If using SoundPool linked aliases, ensure loaded
        // Allow play even if not yet marked loaded (pool queues)
        try {
            val volume = (baseVolume * (1f + rand.nextFloat() * 0.1f - 0.05f)).coerceIn(0.3f, 1f)
            val pitch = pitchOverride ?: (1f + rand.nextFloat() * pitchVariance - pitchVariance / 2).coerceIn(0.7f, 1.4f)
            pool?.play(id, volume, volume, 1, 0, pitch)
        } catch (t: Throwable) {
            Log.w(TAG, "play failed", t)
        }
        // Haptic complement for addictive feedback
        if (saveManager.vibrationEnabled) {
            val vib = when (event) {
                SoundEvent.BLOCK_DESTROY -> 45L to 180
                SoundEvent.BOSS_HIT -> 70L to 220
                SoundEvent.BOSS_SPAWN -> 80L to 200
                SoundEvent.COIN -> 18L to 110
                SoundEvent.COMBO -> 30L to 150
                SoundEvent.POWER_UP_COLLECT -> 35L to 160
                SoundEvent.LEVEL_COMPLETE -> 60L to 200
                SoundEvent.GAME_OVER, SoundEvent.HEART_LOST -> 90L to 210
                else -> null
            }
            vib?.let { (dur, amp) ->
                try { ir.amiraligrr.blockboom.utils.VibrationHelper.vibrate(context, dur, amp) } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Thread-safe play.
     *
     * The cooldown and spam guards used to run *after* posting to the main
     * thread, so every fire/crack/coin event allocated a Runnable and a
     * message only for playOnMainThread to immediately discard most of them.
     * During a busy level that is dozens of allocations per second on the main
     * thread. The decision is now made on the calling thread (cheap, and
     * guarded by a lock) and only the actual playback is posted.
     */
    fun play(event: SoundEvent) {
        if (!saveManager.soundEnabled) return
        if (!shouldPlay(event)) return
        mainHandler.post { playOnMainThread(event) }
    }

    /**
     * Cooldown + global spam guard. Must be called at most once per accepted
     * event; returns false when the event is dropped as redundant.
     */
    private fun shouldPlay(event: SoundEvent): Boolean = synchronized(stateLock) {
        val now = System.currentTimeMillis()

        val cooldown = when (event) {
            SoundEvent.FIRE -> 36L
            SoundEvent.BLOCK_CRACK -> 62L
            SoundEvent.BLOCK_HIT -> 58L
            SoundEvent.BLOCK_DESTROY -> 70L
            SoundEvent.COIN -> 34L
            SoundEvent.COMBO -> 140L
            SoundEvent.BOSS_HIT -> 90L
            else -> 50L
        }
        val last = lastPlayMs[event] ?: 0L
        if (now - last < cooldown) {
            // Skip spammy crack/coin/fire if too soon
            if (event == SoundEvent.FIRE || event == SoundEvent.BLOCK_CRACK ||
                event == SoundEvent.BLOCK_HIT || event == SoundEvent.COIN
            ) return@synchronized false
        }

        // Global spam guard: drop quiet sounds if 5+ in the last 130ms
        while (recentPlays.isNotEmpty() && now - recentPlays.first() > 130) recentPlays.removeFirst()
        if (recentPlays.size >= 5 &&
            (event == SoundEvent.BLOCK_CRACK || event == SoundEvent.COIN || event == SoundEvent.BLOCK_HIT)
        ) {
            return@synchronized false
        }

        lastPlayMs[event] = now
        recentPlays.addLast(now)
        if (recentPlays.size > 10) recentPlays.removeFirst()
        true
    }

    /** Actual play logic running on main thread */
    private fun playOnMainThread(event: SoundEvent) {
        val now = System.currentTimeMillis()
        when (event) {
            SoundEvent.FIRE -> {
                hitCount++
                // Much more variation so fire never feels like a loop
                val base = 0.92f + (hitCount % 7) * 0.035f
                val jitter = rand.nextFloat() * 0.14f - 0.07f
                val firePitch = (base + jitter).coerceIn(0.86f, 1.28f)
                // Slight volume duck on rapid streak to reduce fatigue
                val vol = if (hitCount % 3 == 0) 0.34f else 0.38f
                playInternal(event, vol, 0f, firePitch)
            }
            SoundEvent.BLOCK_HIT -> playInternal(event, 0.42f, 0.22f)
            SoundEvent.BLOCK_CRACK -> {
                // Wider pitch + volume swing so each crack feels organic
                val crackPitchVar = 0.42f
                val crackVol = 0.46f + rand.nextFloat() * 0.14f
                playInternal(event, crackVol, crackPitchVar)
            }
            SoundEvent.BLOCK_DESTROY -> {
                // Punch with humanized pitch and occasional fat layer
                val destroyPitch = 0.92f + rand.nextFloat() * 0.18f
                playInternal(event, 0.62f, 0.14f, destroyPitch)
                if (rand.nextFloat() < 0.28f) playInternal(event, 0.38f, 0.10f, 0.82f + rand.nextFloat() * 0.08f)
            }
            SoundEvent.COIN -> {
                if (now - lastCoinTime < 420) coinPitchStreak++ else coinPitchStreak = 0
                lastCoinTime = now
                val escalate = (coinPitchStreak * 0.065f).coerceAtMost(0.32f)
                val coinPitch = (1f + escalate + rand.nextFloat() * 0.14f - 0.07f).coerceIn(0.92f, 1.42f)
                val coinVol = 0.44f + rand.nextFloat() * 0.10f
                playInternal(event, coinVol, 0.08f, coinPitch)
            }
            SoundEvent.POWER_UP_SPAWN -> playInternal(event, 0.52f, 0.12f, 1.02f + rand.nextFloat() * 0.08f)
            SoundEvent.POWER_UP_COLLECT -> playInternal(event, 0.60f, 0.16f, 1.10f + rand.nextFloat() * 0.12f)
            SoundEvent.FREEZE_ACTIVATE -> playInternal(event, 0.56f, 0.12f, 0.86f + rand.nextFloat() * 0.06f)
            SoundEvent.BOSS_HIT -> playInternal(event, 0.74f, 0.10f, 0.92f + rand.nextFloat() * 0.10f)
            SoundEvent.BOSS_SPAWN -> playInternal(event, 0.70f, 0.08f, 0.90f + rand.nextFloat() * 0.06f)
            SoundEvent.LEVEL_COMPLETE -> playInternal(event, 0.68f, 0f, 1f)
            SoundEvent.GAME_OVER -> playInternal(event, 0.66f, 0f, 0.95f)
            SoundEvent.HEART_LOST -> playInternal(event, 0.64f, 0.08f, 0.88f + rand.nextFloat() * 0.06f)
            SoundEvent.COMBO -> {
                val comboPitch = 1.12f + rand.nextFloat() * 0.28f
                playInternal(event, 0.58f, 0f, comboPitch)
            }
        }
    }

    private companion object {
        const val TAG = "SoundManager"
    }
}

/** Returns the application-scoped [SoundManager] instance. */
@Composable
fun rememberSoundManager(saveManager: SaveManager): SoundManager {
    val context = LocalContext.current.applicationContext
    val app = context as BlockBoomApp
    return app.soundManager
}