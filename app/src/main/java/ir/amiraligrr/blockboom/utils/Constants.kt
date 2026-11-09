package ir.amiraligrr.blockboom.utils

import ir.amiraligrr.blockboom.BuildConfig

/**
 * The version shown to the player.
 *
 * ── Why this reads BuildConfig instead of declaring its own constants ──────
 * It used to hardcode `1.0.9` / `10` while app/build.gradle.kts said `1.5.0` /
 * `17`. Two sources of truth for one value, and they had already drifted. The
 * About screen printed 1.0.9 to everyone, so a player reporting a bug said
 * "1.0.9" and there was no way to tell which build they were actually on.
 *
 * BuildConfig is the value that is compiled into the APK, so it is the only one
 * that can be right.
 */
object AppConfig {
    const val VERSION_NAME = BuildConfig.VERSION_NAME
    const val VERSION_CODE = BuildConfig.VERSION_CODE
}