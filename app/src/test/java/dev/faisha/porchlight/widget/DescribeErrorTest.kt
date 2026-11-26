package dev.faisha.porchlight.widget

import dev.faisha.porchlight.data.HaHttpException
import dev.faisha.porchlight.data.InsecureUrlException
import dev.faisha.porchlight.data.NoNetworkException
import dev.faisha.porchlight.data.PlainHttpOffNetworkException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException

class DescribeErrorTest {
    private fun d(e: Throwable) = WidgetRefresher.describe(e)

    @Test fun `http errors are matched before the generic IOException`() {
        assertEquals("Bad token", d(HaHttpException(401, "")))
        assertEquals("Template error", d(HaHttpException(400, "")))
        assertEquals("Not found (check URL)", d(HaHttpException(404, "")))
        assertEquals("HA server error (502)", d(HaHttpException(502, "")))
        assertEquals("HTTP 403", d(HaHttpException(403, "")))
    }

    @Test fun `network failures each get their own message`() {
        assertEquals("http not allowed", d(InsecureUrlException("x")))
        assertEquals("http needs Wi-Fi", d(PlainHttpOffNetworkException("x")))
        assertEquals("No network", d(NoNetworkException()))
        assertEquals("Can't find host", d(UnknownHostException("ha.local")))
        assertEquals("Timed out", d(SocketTimeoutException()))
        assertEquals("Can't reach HA", d(ConnectException()))
        assertEquals("Can't reach HA", d(NoRouteToHostException()))
        assertEquals("Certificate error", d(SSLHandshakeException("bad cert")))
        assertEquals("http blocked", d(UnknownServiceException("CLEARTEXT communication not permitted")))
        assertEquals("Network error", d(IOException()))
    }

    @Test fun `other failures`() {
        assertEquals("Bad data from HA", d(JSONException("x")))
        assertEquals("Config error", d(HomeConfigException("x")))
        assertEquals("Unlock to use", d(DeviceLockedException()))
        assertEquals("Not allowed", d(ActionRefusedException("x")))
        assertEquals("Error", d(IllegalStateException()))
    }
}
