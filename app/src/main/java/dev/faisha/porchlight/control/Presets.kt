package dev.faisha.porchlight.control

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** One-tap colours on the control screen (moved here from the widget's old light page). */
internal class Preset(
    val key: String,
    val label: String,
    val display: Int,
    val kelvin: Int? = null,
    val hue: Float? = null,
    val sat: Float = 100f,
) {
    val isWhite get() = kelvin != null

    /** The turn_on fields for this preset. */
    fun data(): JSONObject = JSONObject().apply {
        if (kelvin != null) {
            put("color_temp_kelvin", kelvin)
        } else {
            put("hs_color", JSONArray().put(hue!!.toDouble()).put(sat.toDouble()))
        }
    }
}

internal val PRESETS = listOf(
    Preset("warm", "Warm white", 0xFFFFB866.toInt(), kelvin = 2700),
    Preset("day", "Daylight", 0xFFFFF1D6.toInt(), kelvin = 5000),
    Preset("red", "Red", 0xFFFF6B6B.toInt(), hue = 0f),
    Preset("green", "Green", 0xFF6BDB8A.toInt(), hue = 120f, sat = 90f),
    Preset("blue", "Blue", 0xFF6B9BFF.toInt(), hue = 225f, sat = 90f),
    Preset("purple", "Purple", 0xFFB78CFF.toInt(), hue = 275f, sat = 80f),
)

/** Which preset the light's current colour is closest to, if any. */
internal fun closestPreset(isOn: Boolean, colorMode: String?, kelvin: Int?, hue: Float?, saturation: Float?): String? {
    if (!isOn) return null
    if (colorMode == "color_temp") return if ((kelvin ?: 2700) < 3800) "warm" else "day"
    if (hue == null || (saturation ?: 0f) < 25f) return null
    return PRESETS.filter { it.hue != null }.minByOrNull { p ->
        val d = abs(p.hue!! - hue) % 360f
        if (d > 180f) 360f - d else d
    }?.key
}
