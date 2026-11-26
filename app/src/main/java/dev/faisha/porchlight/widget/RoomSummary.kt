package dev.faisha.porchlight.widget

import dev.faisha.porchlight.data.Room

/**
 * A room's light status for tiles and headers. Counts only available lights, so one
 * unavailable bulb doesn't stop "All on".
 * @param detailed adds the unavailable count (room header); tiles keep it short.
 */
internal fun roomSummary(room: Room, detailed: Boolean = false): String {
    val base = when {
        room.lights.isEmpty() -> return "No lights"
        room.availableCount == 0 -> return "Unavailable"
        room.onCount == 0 -> "Off"
        room.onCount == room.availableCount -> "All on"
        else -> "${room.onCount} of ${room.availableCount} on"
    }
    return if (detailed && room.unavailableCount > 0) "$base · ${room.unavailableCount} unavailable" else base
}
