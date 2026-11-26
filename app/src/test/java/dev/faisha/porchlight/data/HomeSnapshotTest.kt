package dev.faisha.porchlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON below is hand-written in the shape HomeSnapshot.template() renders, not captured from HA. */
class HomeSnapshotTest {

    private val raw = """
        {
          "rooms": [
            {
              "area": "living_room", "name": "Living Room",
              "lights": [
                {"id": "light.living_room_floor_lamp", "name": "Living Room Floor Lamp", "state": "on",
                 "brightness": 128, "color_mode": "hs", "modes": ["hs", "color_temp"],
                 "hs": [30.5, 80], "rgb": [255, 170, 51], "kelvin": null,
                 "effect": "Rainbow", "effects": ["Rainbow", "Candle"], "icon": "mdi:floor-lamp"},
                {"id": "light.tree", "name": "Tree", "state": "unavailable",
                 "brightness": null, "color_mode": "None", "modes": [], "hs": [], "rgb": [],
                 "kelvin": null, "effect": null, "effects": [], "icon": null}
              ],
              "scenes": [{"id": "scene.living_room_movie", "name": "Living Room Movie"}]
            },
            {"area": "study", "name": null, "lights": [], "scenes": []}
          ],
          "vacuum": "docked",
          "tv": null,
          "sensors": {"sensor.litter_box": "3", "sensor.door": "off"}
        }
    """.trimIndent()

    @Test fun `parses rooms, lights and scenes`() {
        val s = HomeSnapshot.parse(raw)
        assertEquals(listOf("living_room", "study"), s.rooms.map { it.areaId })

        val living = s.room("living_room")!!
        assertEquals("Living Room", living.name)
        assertEquals(listOf("Movie"), living.scenes.map { it.name }) // area prefix stripped

        val lamp = living.lights[0]
        assertEquals("Floor Lamp", lamp.name)
        assertTrue(lamp.isOn)
        assertEquals(128, lamp.brightness)
        assertEquals(50, lamp.brightnessPct)
        assertEquals("hs", lamp.colorMode)
        assertEquals(30.5f, lamp.hue!!, 0.001f)
        assertEquals(80f, lamp.saturation!!, 0.001f)
        assertEquals(0xFFAA33, lamp.rgb)
        assertNull(lamp.kelvin)
        assertEquals("Rainbow", lamp.effect)
        assertEquals(listOf("Rainbow", "Candle"), lamp.effects)
        assertTrue(lamp.supportsColor && lamp.supportsTemp && lamp.supportsBrightness)
    }

    @Test fun `null and empty attributes become nulls`() {
        val tree = HomeSnapshot.parse(raw).room("living_room")!!.lights[1]
        assertFalse(tree.isAvailable)
        assertNull(tree.brightness)
        assertNull(tree.colorMode) // "None" from HA's str() of a null
        assertNull(tree.hue)
        assertNull(tree.rgb)
        assertNull(tree.icon)
        assertEquals("Tree", tree.name) // no area prefix to strip
    }

    @Test fun `room with no name falls back to its area id`() {
        assertEquals("study", HomeSnapshot.parse(raw).room("study")!!.name)
    }

    @Test fun `vacuum, tv and sensors`() {
        val s = HomeSnapshot.parse(raw)
        assertEquals("docked", s.vacuum)
        assertNull(s.tv)
        assertEquals(mapOf("sensor.litter_box" to "3", "sensor.door" to "off"), s.sensors)
    }

    @Test fun `missing sensors object and empty rooms are fine`() {
        val s = HomeSnapshot.parse("""{"rooms": [], "vacuum": null, "tv": "playing"}""")
        assertTrue(s.rooms.isEmpty())
        assertTrue(s.sensors.isEmpty())
        assertNull(s.room("living_room"))
        assertEquals("playing", s.tv)
    }

    @Test fun `a name that is only the area name is kept`() {
        val s = HomeSnapshot.parse(
            """{"rooms": [{"area": "hall", "name": "Hall", "lights": [
                {"id": "light.hall", "name": "Hall", "state": "off"}], "scenes": []}]}""",
        )
        assertEquals("Hall", s.rooms[0].lights[0].name)
    }
}
