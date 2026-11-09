package ir.amiraligrr.blockboom.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    /** Wraps [context] with the given [language] locale configuration. */
    fun wrap(context: Context, language: String): Context {
        val code = if (language.isEmpty()) "fa" else language
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Sets the JVM default locale, which is what date and number formatting read.
     *
     * This used to also call `Resources.updateConfiguration`, which has been
     * deprecated since API 25 and mutates process-wide state as a side effect of
     * asking for a locale change. It is gone, and nothing is lost:
     *
     *  - **Resource resolution** comes from [wrap], applied in
     *    `Application.attachBaseContext` and again in `MainActivity.attachBaseContext`,
     *    so the window and its resources are already correct before the first
     *    frame. Both paths use `createConfigurationContext`, the supported API.
     *  - **System-wide locale** is set by [applyToSystem] through
     *    `LocaleManager.applicationLocales` on API 33+.
     *  - **Formatting** — `SimpleDateFormat`, `NumberFormat`, `String.format`
     *    — reads [Locale.getDefault], which is what this sets.
     *
     * Everything the app actually formats by locale goes through the third case.
     * The removed call only ever mattered for the first, and the first is handled
     * where it has to be.
     */
    fun apply(context: Context, language: String) {
        val code = if (language.isEmpty()) "fa" else language
        Locale.setDefault(Locale.forLanguageTag(code))
    }

    /** Applies the locale system-wide for this app (API 33+). */
    fun applyToSystem(context: Context, language: String) {
        val code = if (language.isEmpty()) "fa" else language
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            val locale = Locale.forLanguageTag(code)
            localeManager?.applicationLocales = android.os.LocaleList(locale)
        }
        apply(context, code)
    }
}