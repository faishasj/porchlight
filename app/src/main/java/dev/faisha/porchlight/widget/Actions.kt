package dev.faisha.porchlight.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import dev.faisha.porchlight.data.ConfigStore
import dev.faisha.porchlight.data.HaClient
import kotlinx.coroutines.delay
import org.json.JSONObject

// ---- Navigation ---------------------------------------------------------------------------

private val ScreenParam = ActionParameters.Key<String>("screen")
private val RoomParam = ActionParameters.Key<String>("room")

fun navigate(screen: Screen, room: String? = null): Action {
    val params = mutableListOf<ActionParameters.Pair<out Any>>(ScreenParam to screen.name)
    room?.let { params += RoomParam to it }
    return actionRunCallback<NavigateAction>(actionParametersOf(*params.toTypedArray()))
}

/** Switches screens in this widget instance only. Local, so it's instant. */
class NavigateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val screen = parameters[ScreenParam] ?: return
        WidgetRefresher.write(context, glanceId) { prefs ->
            prefs[WidgetKeys.screen] = screen
            parameters[RoomParam]?.let { prefs[WidgetKeys.room] = it }
            prefs.remove(WidgetKeys.allOffArmedAt)
        }
    }
}

// ---- Home Assistant service calls ---------------------------------------------------------

private val DomainParam = ActionParameters.Key<String>("domain")
private val ServiceParam = ActionParameters.Key<String>("service")
private val DataParam = ActionParameters.Key<String>("data")
private val BusyParam = ActionParameters.Key<String>("busy")
private val CleanRoomParam = ActionParameters.Key<String>("clean_room")

/**
 * Builds a tap action that calls an HA service, then refreshes.
 *
 * @param busyKey shown as "working" on the matching tile until the call finishes.
 * @param cleanRoom remembers which room the vacuum was sent to ("" clears it).
 */
fun callService(
    domain: String,
    service: String,
    data: JSONObject = JSONObject(),
    busyKey: String? = null,
    cleanRoom: String? = null,
): Action {
    val params = mutableListOf<ActionParameters.Pair<out Any>>(
        DomainParam to domain,
        ServiceParam to service,
        DataParam to data.toString(),
    )
    busyKey?.let { params += BusyParam to it }
    cleanRoom?.let { params += CleanRoomParam to it }
    return actionRunCallback<ServiceAction>(actionParametersOf(*params.toTypedArray()))
}

fun entity(id: String): JSONObject = JSONObject().put("entity_id", id)

class ServiceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val domain = parameters[DomainParam] ?: return
        val service = parameters[ServiceParam] ?: return
        val data = parameters[DataParam]?.let(::JSONObject) ?: JSONObject()
        val locked = ServicePolicy.isDeviceLocked(context)
        when (ServicePolicy.check(HomeConfig.get(context), domain, service, data, locked)) {
            ServicePolicy.Verdict.Allowed -> Unit
            ServicePolicy.Verdict.NeedsUnlock -> return WidgetRefresher.setError(context, DeviceLockedException())
            ServicePolicy.Verdict.Refused ->
                return WidgetRefresher.setError(context, ActionRefusedException("$domain.$service isn't a widget action"))
        }
        parameters[CleanRoomParam]?.let { room ->
            WidgetRefresher.writeAll(context) {
                if (room.isEmpty()) it.remove(WidgetKeys.lastCleanRoom) else it[WidgetKeys.lastCleanRoom] = room
            }
        }
        runService(context, domain, service, data, parameters[BusyParam])
    }
}

/** "All off" needs a second tap within [ALL_OFF_CONFIRM_WINDOW_MS], so a stray tap can't run it. */
class AllOffAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Checked before arming, so a locked phone never shows "Tap again to confirm".
        if (ServicePolicy.isDeviceLocked(context)) return WidgetRefresher.setError(context, DeviceLockedException())
        val now = System.currentTimeMillis()
        var confirmed = false
        WidgetRefresher.write(context, glanceId) { prefs ->
            val armedAt = prefs[WidgetKeys.allOffArmedAt]
            if (armedAt != null && now - armedAt in 0..ALL_OFF_CONFIRM_WINDOW_MS) {
                prefs.remove(WidgetKeys.allOffArmedAt)
                confirmed = true
            } else {
                // First tap, or the previous one went stale: arm (again) rather than act.
                prefs[WidgetKeys.allOffArmedAt] = now
            }
        }
        // Nothing else redraws the widget when the window closes, so schedule it.
        if (!confirmed) RefreshScheduler.disarmAllOffLater(context)
        if (confirmed) {
            val script = HomeConfig.get(context).allOffScript ?: return
            runService(context, "script", "turn_on", entity(script), ALL_OFF_BUSY_KEY)
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefresher.refreshAll(context)
    }
}

private suspend fun runService(
    context: Context,
    domain: String,
    service: String,
    data: JSONObject,
    busyKey: String?,
) {
    val config = ConfigStore(context).load() ?: return
    busyKey?.let { WidgetRefresher.setBusy(context, it, true) }
    try {
        HaClient(context, config).callService(domain, service, data)
        // States (and template sensors) settle just after the call returns.
        delay(STATE_SETTLE_MS)
        WidgetRefresher.refreshAll(context)
    } catch (e: Exception) {
        WidgetRefresher.setError(context, e)
    } finally {
        busyKey?.let { WidgetRefresher.setBusy(context, it, false) }
    }
}

private const val STATE_SETTLE_MS = 700L
