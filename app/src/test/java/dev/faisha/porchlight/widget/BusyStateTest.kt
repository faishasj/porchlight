package dev.faisha.porchlight.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusyStateTest {
    private val t0 = 1_800_000_000_000L

    @Test fun `entry is live until the timeout and expired just after`() {
        val entries = setOf(BusyState.entry("light.lamp", t0))
        assertEquals(setOf("light.lamp"), BusyState.active(entries, t0))
        assertEquals(setOf("light.lamp"), BusyState.active(entries, t0 + BUSY_TIMEOUT_MS))
        assertEquals(emptySet<String>(), BusyState.active(entries, t0 + BUSY_TIMEOUT_MS + 1))
    }

    @Test fun `entries without a timestamp count as expired`() {
        // The format before the fix: bare keys, which could be stuck forever.
        assertEquals(emptySet<String>(), BusyState.active(setOf("light.lamp", "_refresh"), t0))
        assertEquals(emptySet<String>(), BusyState.active(setOf("light.lamp@notanumber"), t0))
    }

    @Test fun `future timestamps count as expired`() {
        assertEquals(emptySet<String>(), BusyState.active(setOf(BusyState.entry("tv", t0 + 1)), t0))
    }

    @Test fun `keys containing the separator survive`() {
        assertEquals(setOf("a@b"), BusyState.active(setOf(BusyState.entry("a@b", t0)), t0))
    }

    @Test fun `without drops only the named key, and anything expired`() {
        val entries = setOf(
            BusyState.entry("on:living_room", t0),
            BusyState.entry("tv", t0),
            BusyState.entry("dock", t0 - BUSY_TIMEOUT_MS - 1), // stale
            "_refresh", // old format
        )
        val left = BusyState.without(entries, "tv", t0)
        assertEquals(setOf(BusyState.entry("on:living_room", t0)), left)
    }

    @Test fun `re-marking a key replaces its old entry`() {
        val first = setOf(BusyState.entry("tv", t0))
        val again = BusyState.without(first, "tv", t0 + 5) + BusyState.entry("tv", t0 + 5)
        assertEquals(setOf(BusyState.entry("tv", t0 + 5)), again)
    }

    @Test fun `all off stays armed for exactly the confirm window`() {
        assertTrue(isAllOffArmed(t0, t0))
        assertTrue(isAllOffArmed(t0, t0 + ALL_OFF_CONFIRM_WINDOW_MS))
        assertFalse(isAllOffArmed(t0, t0 + ALL_OFF_CONFIRM_WINDOW_MS + 1))
        assertFalse(isAllOffArmed(null, t0))
        assertFalse(isAllOffArmed(t0 + 1, t0)) // clock went backwards
    }
}
