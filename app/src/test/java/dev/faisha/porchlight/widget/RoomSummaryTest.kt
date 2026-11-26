package dev.faisha.porchlight.widget

import dev.faisha.porchlight.data.Light
import dev.faisha.porchlight.data.LightLevel
import dev.faisha.porchlight.data.Room
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSummaryTest {

    private fun light(state: String) = Light(
        entityId = "light.$state${counter++}", name = "Lamp", state = state, brightness = null,
        colorMode = null, colorModes = emptySet(), hue = null, saturation = null, rgb = null,
        kelvin = null, effect = null, effects = emptyList(), icon = null,
    )
    private var counter = 0
    private fun room(vararg states: String) = Room("study", "Study", states.map(::light), emptyList())

    @Test fun `all available lights on is All on, even with one unavailable`() {
        val r = room("on", "on", "unavailable")
        assertEquals(LightLevel.ALL, r.level)
        assertEquals("All on", roomSummary(r))
        assertEquals("All on · 1 unavailable", roomSummary(r, detailed = true))
    }

    @Test fun `some on counts only available lights`() {
        val r = room("on", "off", "unknown")
        assertEquals(LightLevel.SOME, r.level)
        assertEquals("1 of 2 on", roomSummary(r))
        assertEquals("1 of 2 on · 1 unavailable", roomSummary(r, detailed = true))
    }

    @Test fun `all off`() {
        val r = room("off", "off")
        assertEquals(LightLevel.NONE, r.level)
        assertEquals("Off", roomSummary(r))
        assertEquals("Off", roomSummary(r, detailed = true))
    }

    @Test fun `every light unavailable`() {
        val r = room("unavailable", "unknown")
        assertEquals(LightLevel.UNKNOWN, r.level)
        assertEquals("Unavailable", roomSummary(r))
        assertEquals("Unavailable", roomSummary(r, detailed = true))
    }

    @Test fun `no lights`() {
        val r = room()
        assertEquals(LightLevel.UNKNOWN, r.level)
        assertEquals("No lights", roomSummary(r, detailed = true))
    }

    @Test fun `plain All on with nothing unavailable has no suffix`() {
        assertEquals("All on", roomSummary(room("on", "on"), detailed = true))
    }
}
