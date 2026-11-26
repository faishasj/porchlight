package dev.faisha.porchlight.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/** Which screen a widget instance is showing. Stored by name. */
enum class Screen { HOME, LIGHTS, ROOM, VACUUM, TV, HOUSE }

/** Keys for the per-widget Glance Preferences state. */
object WidgetKeys {
    // Shared data, written to every widget instance on refresh.
    /** Raw JSON from the snapshot template (see HomeSnapshot.parse). */
    val snapshot = stringPreferencesKey("snapshot")
    /** Null when the last refresh succeeded. */
    val error = stringPreferencesKey("error")
    /** Set when [error] is caused by a setting the user can change ([BackgroundBlock] name); tapping the error opens the app to fix it. */
    val errorFix = stringPreferencesKey("error_fix")
    val configured = booleanPreferencesKey("configured")
    val updatedAt = longPreferencesKey("updated_at")
    /** Entity ids (or other action keys) with a service call in flight, stored by [BusyState]. */
    val busy = stringSetPreferencesKey("busy")
    /** Area of the last room-clean started from the widget, to highlight while cleaning. */
    val lastCleanRoom = stringPreferencesKey("last_clean_room")
    /** The Theme setting when last changed. The widget reads ThemeStore; writing this just redraws it. */
    val theme = stringPreferencesKey("theme")

    // Navigation, per widget instance.
    val screen = stringPreferencesKey("screen")
    val room = stringPreferencesKey("room")
    /** When "All off" was first tapped; a second tap within the window runs it. */
    val allOffArmedAt = longPreferencesKey("all_off_armed_at")
}

const val REFRESH_BUSY_KEY = "_refresh"
const val ALL_OFF_BUSY_KEY = "_all_off"
const val ALL_OFF_CONFIRM_WINDOW_MS = 10_000L

/**
 * A busy entry older than this counts as finished. A service call plus its refresh takes about
 * 50s at worst (local and remote timeouts); past that, the process died mid-call.
 */
const val BUSY_TIMEOUT_MS = 60_000L

/**
 * Busy entries are stored as "key@startedAtMillis", so one left behind by a killed process
 * expires instead of showing "…" for good. Entries without a timestamp count as expired.
 */
object BusyState {
    private const val SEP = '@'

    fun entry(key: String, now: Long): String = "$key$SEP$now"

    /** The keys still in flight at [now]. */
    fun active(entries: Set<String>, now: Long): Set<String> =
        entries.mapNotNullTo(mutableSetOf()) { e -> if (isLive(e, now)) e.substringBeforeLast(SEP) else null }

    /** [entries] without [key] and without anything expired. */
    fun without(entries: Set<String>, key: String, now: Long): Set<String> =
        entries.filterTo(mutableSetOf()) { isLive(it, now) && it.substringBeforeLast(SEP) != key }

    private fun isLive(entry: String, now: Long): Boolean {
        val i = entry.lastIndexOf(SEP)
        val started = if (i < 0) null else entry.substring(i + 1).toLongOrNull()
        return started != null && now - started in 0..BUSY_TIMEOUT_MS
    }
}

fun isAllOffArmed(armedAt: Long?, now: Long): Boolean =
    armedAt != null && now - armedAt in 0..ALL_OFF_CONFIRM_WINDOW_MS
