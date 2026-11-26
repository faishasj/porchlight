package dev.faisha.porchlight.data

import android.app.UiModeManager
import android.content.Context

enum class ThemeMode(val key: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

/**
 * The Theme setting (System / Light / Dark).
 *
 * The app's own screens get it through Android's per-app night mode (API 31+), which the system
 * persists and applies to every activity, so values-night resources just work. The widget is drawn
 * by the launcher and never sees that, so it reads [get] and picks its colours itself.
 */
object ThemeStore {
    private const val PREFS = "appearance"
    private const val KEY = "theme"

    fun get(context: Context): ThemeMode {
        val saved = prefs(context).getString(KEY, null)
        return ThemeMode.entries.firstOrNull { it.key == saved } ?: ThemeMode.SYSTEM
    }

    /** Saves [mode] and applies it. Open activities are recreated in the new theme. */
    fun set(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY, mode.key).apply()
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
