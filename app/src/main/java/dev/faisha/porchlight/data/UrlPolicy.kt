package dev.faisha.porchlight.data

import java.io.IOException
import java.net.URI

/** Thrown before connecting when a URL would send the token over plain http to a public host. */
open class InsecureUrlException(message: String) : IOException(message)

/**
 * Thrown before connecting when a private http URL is skipped because the phone isn't on
 * Wi-Fi, Ethernet or a VPN. On mobile data a "private" address such as 100.64.x.x (carrier NAT)
 * or 10.x can belong to someone else's device on the carrier's network.
 */
class PlainHttpOffNetworkException(message: String) : InsecureUrlException(message)

/**
 * The manifest allows cleartext app-wide (a network security config can't allow an IP range),
 * so this is where http:// is limited: only hosts on a private network or an encrypted tunnel,
 * and (in HaClient) only while the phone is on Wi-Fi, Ethernet or a VPN.
 * Nothing is resolved over DNS; the check is on the URL text alone, so a hostile Wi-Fi network
 * that answers the same address still gets the token. https is the real fix.
 */
object UrlPolicy {

    /** Null when [url] is fine, otherwise a sentence for the settings screen. */
    fun problem(url: String, label: String): String? {
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return "$label URL must start with http:// or https://"
        val host = hostOf(url) ?: return "$label URL has no host"
        if (scheme == "http" && !isPrivateHost(host)) {
            return "$label URL must use https:// ($host isn't a local address, and http would send your token unencrypted)"
        }
        return null
    }

    /**
     * Checked before every request. Throws [InsecureUrlException] for anything [problem] refuses
     * (a scheme other than http/https, no host, or plain http to a public host), and
     * [PlainHttpOffNetworkException] for plain http when [plainHttpNetwork] is false, i.e. the
     * active network isn't Wi-Fi, Ethernet or a VPN.
     */
    fun requireAllowed(url: String, plainHttpNetwork: Boolean = true) {
        problem(url, "HA")?.let { throw InsecureUrlException(it) }
        if (!plainHttpNetwork && isPlainHttp(url)) {
            throw PlainHttpOffNetworkException("Skipping plain http to ${hostOf(url)}: not on Wi-Fi, Ethernet or a VPN")
        }
    }

    fun isPlainHttp(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.trim('[', ']')?.lowercase()?.takeIf { it.isNotEmpty() }

    internal fun isPrivateHost(host: String): Boolean {
        ipv4(host)?.let { (a, b) ->
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127) // CGNAT range, used by Tailscale
        }
        if (':' in host) {
            // IPv6 literal: loopback, unique local (fc00::/7), link-local (fe80::/10).
            return host == "::1" || host.startsWith("fc") || host.startsWith("fd") ||
                host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
        }
        if ('.' !in host) return true // single label, e.g. "homeassistant"
        return PRIVATE_SUFFIXES.any { host.endsWith(it) }
    }

    /** First two octets of a dotted IPv4 literal, or null if [host] isn't one. */
    private fun ipv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val n = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return null }
        return n[0] to n[1]
    }

    private val PRIVATE_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain", ".ts.net")
}
