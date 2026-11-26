package dev.faisha.porchlight.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.faisha.porchlight.data.BackgroundAccess
import dev.faisha.porchlight.data.BackgroundBlock
import dev.faisha.porchlight.data.ConfigStore
import dev.faisha.porchlight.data.HaClient
import dev.faisha.porchlight.data.HaHttpException
import dev.faisha.porchlight.data.HomeSnapshot
import dev.faisha.porchlight.data.InsecureUrlException
import dev.faisha.porchlight.data.NoNetworkException
import dev.faisha.porchlight.data.PlainHttpOffNetworkException
import dev.faisha.porchlight.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.Date
import javax.net.ssl.SSLException

/** Fetches the home snapshot from HA and pushes it into every placed instance of the widget. */
object WidgetRefresher {

    /** Outlives the settings screen, which is recreated as soon as the theme changes. */
    private val themeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Redraws every placed widget after the Theme setting changes. */
    fun redrawForTheme(context: Context, mode: ThemeMode) {
        val app = context.applicationContext
        themeScope.launch { runCatching { writeAll(app) { it[WidgetKeys.theme] = mode.key } } }
    }

    suspend fun refreshAll(context: Context) {
        val config = ConfigStore(context).load()
        if (config == null) {
            writeAll(context) {
                it[WidgetKeys.configured] = false
                it[WidgetKeys.error] = "Tap to set up"
                it.remove(WidgetKeys.errorFix)
            }
            return
        }

        setBusy(context, REFRESH_BUSY_KEY, true)
        val result = runCatching {
            val raw = HaClient(context, config).renderTemplate(HomeConfig.get(context).snapshotTemplate)
            HomeSnapshot.parse(raw) // fail here, not at render time, if HA sent something odd
            raw
        }

        val error = result.exceptionOrNull()?.let { widgetError(context, it) }
        writeAll(context) { prefs ->
            val now = System.currentTimeMillis()
            prefs[WidgetKeys.configured] = true
            // Also drops busy entries a killed process left behind.
            prefs[WidgetKeys.busy] = BusyState.without(prefs[WidgetKeys.busy] ?: emptySet(), REFRESH_BUSY_KEY, now)
            disarmAllOffIfExpired(prefs, now)
            result.onSuccess { raw ->
                prefs[WidgetKeys.snapshot] = raw
                prefs.remove(WidgetKeys.error)
                prefs.remove(WidgetKeys.errorFix)
                prefs[WidgetKeys.updatedAt] = now
            }
            error?.let { it.writeTo(prefs) }
        }
    }

    suspend fun setBusy(context: Context, key: String, busy: Boolean) = writeAll(context) {
        val now = System.currentTimeMillis()
        val others = BusyState.without(it[WidgetKeys.busy] ?: emptySet(), key, now)
        it[WidgetKeys.busy] = if (busy) others + BusyState.entry(key, now) else others
    }

    suspend fun setError(context: Context, e: Throwable) {
        val error = widgetError(context, e)
        writeAll(context) { error.writeTo(it) }
    }

    /** Clears "Tap again to confirm" once its window has passed, so it doesn't linger. */
    fun disarmAllOffIfExpired(prefs: MutablePreferences, now: Long) {
        if (prefs[WidgetKeys.allOffArmedAt] != null && !isAllOffArmed(prefs[WidgetKeys.allOffArmedAt], now)) {
            prefs.remove(WidgetKeys.allOffArmedAt)
        }
    }

    /** A failed refresh or action, as the widget shows it. [fix] is set when a setting caused it. */
    private class WidgetError(val text: String, val fix: BackgroundBlock?) {
        fun writeTo(prefs: MutablePreferences) {
            prefs[WidgetKeys.error] = text
            if (fix != null) prefs[WidgetKeys.errorFix] = fix.name else prefs.remove(WidgetKeys.errorFix)
        }
    }

    /**
     * The widget's one-line error: what went wrong and when, e.g. "Can't reach HA · 13:42",
     * so a retry that fails again still visibly changes something. Network failures that the
     * Battery or Data Saver setting explains say so instead.
     */
    private fun widgetError(context: Context, e: Throwable): WidgetError {
        val fix = BackgroundAccess.explains(
            e, BackgroundAccess.batteryMode(context), BackgroundAccess.dataSaverBlocking(context),
        )
        val what = when (fix) {
            BackgroundBlock.BATTERY -> "Battery restricted"
            BackgroundBlock.DATA_SAVER -> "Data Saver on"
            null -> describe(e)
        }
        return WidgetError("$what · ${DateFormat.getTimeFormat(context).format(Date())}", fix)
    }

    /** Updates shared data in every widget instance and redraws them. */
    suspend fun writeAll(context: Context, block: (MutablePreferences) -> Unit) {
        GlanceAppWidgetManager(context).getGlanceIds(HaWidget::class.java).forEach { id ->
            write(context, id, block)
        }
    }

    /** Updates one widget instance (navigation state) and redraws it. */
    suspend fun write(context: Context, id: GlanceId, block: (MutablePreferences) -> Unit) {
        updateAppWidgetState(context, id) { prefs -> block(prefs) }
        HaWidget().update(context, id)
    }

    fun describe(e: Throwable): String = when (e) {
        is HaHttpException -> when (e.code) {
            401 -> "Bad token"
            400 -> "Template error"
            404 -> "Not found (check URL)"
            in 500..599 -> "HA server error (${e.code})"
            else -> "HTTP ${e.code}"
        }
        is PlainHttpOffNetworkException -> "http needs Wi-Fi"
        is NoNetworkException -> "No network"
        is InsecureUrlException -> "http not allowed"
        is UnknownHostException -> "Can't find host"
        is SocketTimeoutException -> "Timed out"
        is ConnectException, is NoRouteToHostException -> "Can't reach HA"
        is SSLException -> "Certificate error"
        // Thrown when the platform blocks cleartext http.
        is UnknownServiceException -> "http blocked"
        is IOException -> "Network error"
        is JSONException -> "Bad data from HA"
        is HomeConfigException -> "Config error"
        is DeviceLockedException -> "Unlock to use"
        is ActionRefusedException -> "Not allowed"
        else -> "Error"
    }
}
