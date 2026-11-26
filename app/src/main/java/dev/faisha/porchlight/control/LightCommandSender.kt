package dev.faisha.porchlight.control

import android.content.Context
import dev.faisha.porchlight.data.HaClient
import dev.faisha.porchlight.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Sends the control screen's light calls (turn_on with brightness/colour/effect, or turn_off).
 *
 * One call is in flight at a time. turn_on updates that arrive meanwhile are merged per light
 * (the newest value of each field wins), so a drag sends a few calls rather than one per touch
 * event, and a released brightness isn't lost when the warm/cool slider is touched straight
 * after. A turn_off replaces anything still queued for that light, and a later turn_on replaces
 * a queued turn_off. Lives outside the activity so the last value still goes out if "Done" is
 * tapped right after letting go.
 */
internal object LightCommandSender {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private class Pending(val client: HaClient, val service: String, val data: JSONObject)

    private val pending = LinkedHashMap<String, Pending>()
    private var refreshJob: Job? = null

    /** Set by the visible control screen; called on a background thread. */
    @Volatile
    var onError: ((Throwable) -> Unit)? = null

    init {
        scope.launch { for (signal in wake) drain() }
    }

    /** light.turn_on with [update] (empty just turns it on), merged into anything queued. */
    fun turnOn(client: HaClient, entityId: String, update: JSONObject = JSONObject()) {
        synchronized(pending) {
            val data = pending[entityId]?.takeIf { it.service == "turn_on" }?.data ?: JSONObject()
            // A light is either in a colour mode or a white mode; the newest pick wins.
            if (update.has("hs_color")) data.remove("color_temp_kelvin")
            if (update.has("color_temp_kelvin")) data.remove("hs_color")
            update.keys().forEach { data.put(it, update.get(it)) }
            pending[entityId] = Pending(client, "turn_on", data)
        }
        wake.trySend(Unit)
    }

    fun turnOff(client: HaClient, entityId: String) {
        synchronized(pending) { pending[entityId] = Pending(client, "turn_off", JSONObject()) }
        wake.trySend(Unit)
    }

    /** Re-fetches the widget once things have settled, so it shows the new state. Debounced. */
    fun refreshWidgetsSoon(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                delay(REFRESH_DELAY_MS)
                WidgetRefresher.refreshAll(app)
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            val (entityId, call) = synchronized(pending) {
                val next = pending.entries.firstOrNull() ?: return
                pending.remove(next.key)
                next.key to next.value
            }
            try {
                call.client.callService("light", call.service, call.data.put("entity_id", entityId))
            } catch (e: Exception) {
                onError?.invoke(e)
            }
            delay(MIN_INTERVAL_MS)
        }
    }

    private const val MIN_INTERVAL_MS = 200L
    private const val REFRESH_DELAY_MS = 1_000L
}
