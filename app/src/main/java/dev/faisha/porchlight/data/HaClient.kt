package dev.faisha.porchlight.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HaHttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

/**
 * The phone has no usable network: none is connected, or Android is blocking Porchlight's access
 * (battery setting, Data Saver), which looks the same to the app. See [BackgroundAccess].
 */
class NoNetworkException : IOException("No network, or Android is blocking Porchlight's access to it")

/**
 * Minimal Home Assistant REST client (no extra dependencies).
 *
 * Every request tries the local URL first with a short connect timeout, then falls back to
 * the remote URL (if configured) on a network error. HTTP errors (401, 404...) are not
 * retried on the other URL, since they'd fail the same way there.
 *
 * A plain http URL is only tried while the phone is on Wi-Fi, Ethernet or a VPN (see
 * [UrlPolicy.requireAllowed]); on mobile data it's skipped and the other URL is used. With no
 * usable network at all, nothing is tried and [NoNetworkException] is thrown.
 */
class HaClient(context: Context, private val config: HaConfig) {

    private val appContext = context.applicationContext

    /** GET /api/ — returns HA's "API running." message. Used by the settings screen. */
    suspend fun ping(): String = request("GET", "/api/").let { JSONObject(it).optString("message") }

    /** GET /api/states/<entity_id> — returns the state string, or null if the entity doesn't exist. */
    suspend fun getState(entityId: String): String? = try {
        JSONObject(request("GET", "/api/states/$entityId")).optString("state")
    } catch (e: HaHttpException) {
        if (e.code == 404) null else throw e
    }

    /** GET /api/states/<entity_id> — the full state object (state + attributes), or null if the entity doesn't exist. */
    suspend fun getEntity(entityId: String): JSONObject? = try {
        JSONObject(request("GET", "/api/states/$entityId"))
    } catch (e: HaHttpException) {
        if (e.code == 404) null else throw e
    }

    /** POST /api/template — renders a Jinja template server-side and returns the text. */
    suspend fun renderTemplate(template: String): String =
        request("POST", "/api/template", JSONObject().put("template", template).toString())

    /** POST /api/services/<domain>/<service> with a JSON body (entity_id, area_id, ...). */
    suspend fun callService(domain: String, service: String, data: JSONObject) {
        request("POST", "/api/services/$domain/$service", data.toString())
    }

    private suspend fun request(method: String, path: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val bases = listOfNotNull(config.localUrl, config.remoteUrl).map { it.trimEnd('/') }
            val network = activeNetworkKind()
            // Android reports a blocked network as no network, so this must not read as "not on Wi-Fi".
            if (network == NetworkKind.NONE) throw NoNetworkException()
            val plainHttpNetwork = network == NetworkKind.LOCAL
            var lastError: IOException? = null
            for ((i, base) in bases.withIndex()) {
                try {
                    UrlPolicy.requireAllowed(base, plainHttpNetwork)
                    // Local gets a short connect timeout so being away from home falls back quickly.
                    return@withContext send(base + path, method, body, connectTimeoutMs = if (i == 0) 2_000 else 6_000)
                } catch (e: HaHttpException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("No Home Assistant URL configured")
        }

    private enum class NetworkKind {
        /** No default network, or Android is blocking it for this app. */
        NONE,
        /** Wi-Fi, Ethernet or a VPN (Tailscale included): plain http is allowed. */
        LOCAL,
        /** Mobile data, or a network we can't identify: https only. */
        OTHER,
    }

    private fun activeNetworkKind(): NetworkKind {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.OTHER
        // Null both with no network and when the network is blocked for this app.
        val network = cm.activeNetwork ?: return NetworkKind.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkKind.OTHER
        val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return if (local) NetworkKind.LOCAL else NetworkKind.OTHER
    }

    private fun send(url: String, method: String, body: String?, connectTimeoutMs: Int): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            // HA's API never redirects, and a redirect target would skip UrlPolicy.
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = 8_000
            conn.setRequestProperty("Authorization", "Bearer ${config.token}")
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HaHttpException(code, text.take(200))
            return text
        } finally {
            conn.disconnect()
        }
    }
}
