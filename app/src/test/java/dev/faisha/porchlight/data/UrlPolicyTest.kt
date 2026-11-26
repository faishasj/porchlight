package dev.faisha.porchlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    private val privateHosts = listOf(
        "http://192.168.1.10:8123",
        "http://10.0.0.5:8123",
        "http://172.16.0.1",
        "http://172.31.255.254",
        "http://127.0.0.1:8123",
        "http://169.254.10.10",
        "http://100.64.0.1:8123",   // Tailscale (CGNAT) lower bound
        "http://100.127.255.255",   // Tailscale upper bound
        "http://homeassistant.local:8123",
        "http://ha.lan",
        "http://ha.home",
        "http://ha.home.arpa",
        "http://ha.internal",
        "http://ha.localdomain",
        "http://ha.tail1234.ts.net:8123",
        "http://homeassistant:8123", // single label
        "http://[::1]:8123",
        "http://[fd12:3456::1]:8123",
        "http://[fe80::1]:8123",
        "http://HomeAssistant.LOCAL:8123", // case-insensitive
    )

    private val publicHosts = listOf(
        "http://172.32.0.1",          // just past 172.16/12
        "http://172.15.0.1",          // just before it
        "http://100.128.0.1",         // just past 100.64/10
        "http://100.63.255.255",      // just before it
        "http://8.8.8.8",
        "http://192.168.example.com", // looks private, isn't an IP
        "http://fdsa.com",            // starts like an IPv6 ULA, isn't one
        "http://example.com",
        "http://ha.duckdns.org:8123",
        "http://notlocal.io",
        "http://[2001:db8::1]",
    )

    @Test fun `http to a private host is allowed`() {
        privateHosts.forEach { url ->
            assertNull(url, UrlPolicy.problem(url, "Local"))
            UrlPolicy.requireAllowed(url) // doesn't throw
        }
    }

    @Test fun `http to a public host is refused`() {
        publicHosts.forEach { url ->
            assertNotNull(url, UrlPolicy.problem(url, "Remote"))
            try {
                UrlPolicy.requireAllowed(url)
                throw AssertionError("expected InsecureUrlException for $url")
            } catch (_: InsecureUrlException) {
            }
        }
    }

    @Test fun `https is always allowed`() {
        (privateHosts + publicHosts).map { it.replaceFirst("http://", "https://") }.forEach { url ->
            assertNull(url, UrlPolicy.problem(url, "Remote"))
            UrlPolicy.requireAllowed(url)
        }
    }

    @Test fun `bad scheme and missing host are reported with the label`() {
        assertEquals("Local URL must start with http:// or https://", UrlPolicy.problem("192.168.1.10:8123", "Local"))
        assertEquals("Remote URL must start with http:// or https://", UrlPolicy.problem("ftp://ha.local", "Remote"))
        assertEquals("Local URL has no host", UrlPolicy.problem("http://", "Local"))
    }

    @Test fun `requireAllowed refuses what problem refuses`() {
        listOf("ftp://10.0.0.1", "192.168.1.10:8123", "http://").forEach { url ->
            try {
                UrlPolicy.requireAllowed(url)
                throw AssertionError("expected InsecureUrlException for $url")
            } catch (_: InsecureUrlException) {
            }
        }
    }

    @Test fun `plain http is skipped off Wi-Fi, https isn't`() {
        try {
            UrlPolicy.requireAllowed("http://192.168.1.10:8123", plainHttpNetwork = false)
            throw AssertionError("expected PlainHttpOffNetworkException")
        } catch (_: PlainHttpOffNetworkException) {
        }
        UrlPolicy.requireAllowed("https://ha.example.com", plainHttpNetwork = false)
        UrlPolicy.requireAllowed("http://192.168.1.10:8123", plainHttpNetwork = true)
    }

    @Test fun `refusal names the host`() {
        val problem = UrlPolicy.problem("http://example.com:8123", "Remote")!!
        assertTrue(problem, "example.com" in problem)
    }
}
