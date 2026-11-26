package dev.faisha.porchlight.data

import dev.faisha.porchlight.data.BackgroundBlock.BATTERY
import dev.faisha.porchlight.data.BackgroundBlock.DATA_SAVER
import dev.faisha.porchlight.data.BatteryMode.OPTIMIZED
import dev.faisha.porchlight.data.BatteryMode.RESTRICTED
import dev.faisha.porchlight.data.BatteryMode.UNRESTRICTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class BackgroundAccessTest {

    @Test fun `battery comes before Data Saver, and Unrestricted with Data Saver off is clear`() {
        assertEquals(BATTERY, BackgroundAccess.block(OPTIMIZED, dataSaver = true))
        assertEquals(BATTERY, BackgroundAccess.block(RESTRICTED, dataSaver = false))
        assertEquals(DATA_SAVER, BackgroundAccess.block(UNRESTRICTED, dataSaver = true))
        assertNull(BackgroundAccess.block(UNRESTRICTED, dataSaver = false))
    }

    @Test fun `a blocked network is blamed on the setting that blocks it`() {
        val e = NoNetworkException()
        assertEquals(BATTERY, BackgroundAccess.explains(e, OPTIMIZED, dataSaver = false))
        assertEquals(BATTERY, BackgroundAccess.explains(e, RESTRICTED, dataSaver = true))
        assertEquals(DATA_SAVER, BackgroundAccess.explains(e, UNRESTRICTED, dataSaver = true))
        assertNull(BackgroundAccess.explains(e, UNRESTRICTED, dataSaver = false))
    }

    @Test fun `Optimised doesn't explain a failure that got as far as the network`() {
        assertNull(BackgroundAccess.explains(SocketTimeoutException(), OPTIMIZED, dataSaver = true))
        assertNull(BackgroundAccess.explains(ConnectException(), UNRESTRICTED, dataSaver = true))
    }

    @Test fun `Restricted explains any network failure, but not HA or URL errors`() {
        assertEquals(BATTERY, BackgroundAccess.explains(ConnectException(), RESTRICTED, dataSaver = false))
        assertNull(BackgroundAccess.explains(HaHttpException(401, ""), RESTRICTED, dataSaver = false))
        assertNull(BackgroundAccess.explains(PlainHttpOffNetworkException("x"), RESTRICTED, dataSaver = false))
        assertNull(BackgroundAccess.explains(IllegalStateException(), RESTRICTED, dataSaver = false))
    }
}
