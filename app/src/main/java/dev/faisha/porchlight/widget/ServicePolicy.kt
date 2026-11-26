package dev.faisha.porchlight.widget

import android.app.KeyguardManager
import android.content.Context
import org.json.JSONObject

/** The phone is locked (with a PIN, pattern, password or biometrics) and the action needs it unlocked. */
class DeviceLockedException : Exception("Unlock to use")

/** A widget action asked for a service call the widget never makes. */
class ActionRefusedException(message: String) : Exception(message)

/**
 * Which service calls a widget tap may make, and which of them need the phone unlocked.
 *
 * Every tap's domain, service and data travel in its PendingIntent, so this checks them against
 * what the widget's own buttons send, rather than calling whatever arrives. Lights, room
 * cleaning and the vacuum need an unlocked phone, in case the widget is placed on a lock screen;
 * scenes and TV play/pause don't. "All off" is checked in [AllOffAction].
 */
internal object ServicePolicy {

    enum class Verdict { Allowed, NeedsUnlock, Refused }

    fun check(home: HomeConfig, domain: String, service: String, data: JSONObject, locked: Boolean): Verdict {
        val keys = data.keys().asSequence().toSet()
        val entity = data.optString("entity_id").takeIf { keys == setOf("entity_id") && it.isNotEmpty() }
        val area = data.optString("area_id").takeIf { keys == setOf("area_id") && it.isNotEmpty() }

        val needsUnlock = when {
            domain == "light" && service in LIGHT_SERVICES &&
                (entity?.startsWith("light.") == true || home.room(area) != null) -> true
            domain == "vacuum" && service in VACUUM_SERVICES && entity != null && entity == home.vacuum -> true
            domain == "script" && service == "turn_on" && entity != null &&
                home.rooms.any { it.cleanScript == entity } -> true
            domain == "scene" && service == "turn_on" && entity?.startsWith("scene.") == true -> false
            domain == "media_player" && service == "media_play_pause" && entity != null &&
                entity == home.tv?.entityId -> false
            else -> return Verdict.Refused
        }
        return if (needsUnlock && locked) Verdict.NeedsUnlock else Verdict.Allowed
    }

    /** True while the lock screen is up on a phone with a secure lock. A swipe-only lock doesn't count. */
    fun isDeviceLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    private val LIGHT_SERVICES = setOf("turn_on", "turn_off", "toggle")
    private val VACUUM_SERVICES = setOf("start", "pause", "return_to_base")
}
