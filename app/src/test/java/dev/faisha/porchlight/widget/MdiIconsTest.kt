package dev.faisha.porchlight.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MdiIconsTest {
    @Test
    fun parsesNameAndHexPairs() {
        val map = MdiIcons.parseCodepoints("bed f02e3\nsofa f04b9\n\n")
        assertEquals(mapOf("bed" to 0xf02e3, "sofa" to 0xf04b9), map)
    }

    @Test
    fun skipsMalformedLines() {
        assertEquals(mapOf("television" to 0xf0502), MdiIcons.parseCodepoints("television f0502\nbroken\na b c"))
    }

    @Test
    fun acceptsNamesWithOrWithoutTheMdiPrefix() {
        assertEquals("floor-lamp", MdiIcons.normalise("mdi:floor-lamp"))
        assertEquals("floor-lamp", MdiIcons.normalise(" floor-lamp "))
    }

    @Test
    fun rejectsOtherIconSets() {
        assertNull(MdiIcons.normalise("hue:bulb-group"))
        assertNull(MdiIcons.normalise("mdi:"))
    }
}
