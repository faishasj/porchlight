package dev.faisha.porchlight.data

import org.json.JSONArray
import org.json.JSONObject

/** How many of a room's lights are on. */
enum class LightLevel { ALL, SOME, NONE, UNKNOWN }

data class Light(
    val entityId: String,
    val name: String,
    val state: String,
    /** 0–255, only while on. */
    val brightness: Int?,
    val colorMode: String?,
    val colorModes: Set<String>,
    val hue: Float?,
    val saturation: Float?,
    /** 0xRRGGBB, only while on. */
    val rgb: Int?,
    val kelvin: Int?,
    val effect: String?,
    val effects: List<String>,
    /** Icon override from HA, e.g. "mdi:floor-lamp". */
    val icon: String?,
) {
    val isOn get() = state == "on"
    val isAvailable get() = state == "on" || state == "off"
    val brightnessPct: Int? get() = brightness?.let { Math.round(it * 100f / 255f) }

    val supportsBrightness get() =
        if (colorModes.isEmpty()) brightness != null else colorModes.any { it != "onoff" }
    val supportsColor get() = colorModes.any { it in COLOR_MODES }
    val supportsTemp get() = "color_temp" in colorModes

    private companion object {
        val COLOR_MODES = setOf("hs", "xy", "rgb", "rgbw", "rgbww")
    }
}

data class Scene(val entityId: String, val name: String)

data class Room(
    val areaId: String,
    val name: String,
    val lights: List<Light>,
    val scenes: List<Scene>,
) {
    val onCount get() = lights.count { it.isOn }
    /** Lights that are on or off; unavailable ones don't count against "All on". */
    val availableCount get() = lights.count { it.isAvailable }
    val unavailableCount get() = lights.size - availableCount
    val level: LightLevel
        get() = when {
            availableCount == 0 -> LightLevel.UNKNOWN
            onCount == 0 -> LightLevel.NONE
            onCount == availableCount -> LightLevel.ALL
            else -> LightLevel.SOME
        }
}

/**
 * Everything the widget shows, fetched with one template call per refresh.
 * Lights and scenes are found by area at runtime,
 * so adding a light to an area in HA adds it to the widget.
 */
data class HomeSnapshot(
    val rooms: List<Room>,
    val vacuum: String?,
    val tv: String?,
    /** State of each configured sensor, by entity ID. */
    val sensors: Map<String, String>,
) {
    fun room(areaId: String?): Room? = rooms.firstOrNull { it.areaId == areaId }

    companion object {

        /**
         * Builds the Jinja template. It returns JSON; the rendered text is what gets cached
         * in widget state and parsed with [parse].
         */
        fun template(areaIds: List<String>, vacuum: String?, tv: String?, sensors: List<String>): String {
            // IDs are checked against [a-z0-9_.] at build time, so quoting them is safe.
            val areas = areaIds.joinToString(",") { "'$it'" }
            fun state(id: String?) = id?.let { "states('$it')" } ?: "none"
            val sensorStates = sensors.joinToString(", ") { "'$it': states('$it')" }
            return """
{%- set ns = namespace(rooms=[]) -%}
{%- for a in [$areas] -%}
  {%- set ids = area_entities(a) -%}
  {%- set ls = namespace(items=[]) -%}
  {%- for s in states.light | selectattr('entity_id', 'in', ids) | sort(attribute='name') -%}
    {%- set e = s.entity_id -%}
    {%- set ls.items = ls.items + [{
      'id': e,
      'name': s.name,
      'state': s.state,
      'brightness': state_attr(e, 'brightness'),
      'color_mode': (state_attr(e, 'color_mode') or '') | string,
      'modes': (state_attr(e, 'supported_color_modes') or []) | map('string') | list,
      'hs': (state_attr(e, 'hs_color') or []) | list,
      'rgb': (state_attr(e, 'rgb_color') or []) | list,
      'kelvin': state_attr(e, 'color_temp_kelvin'),
      'effect': state_attr(e, 'effect'),
      'effects': (state_attr(e, 'effect_list') or []) | map('string') | list,
      'icon': state_attr(e, 'icon')
    }] -%}
  {%- endfor -%}
  {%- set ss = namespace(items=[]) -%}
  {%- for s in states.scene | selectattr('entity_id', 'in', ids) | sort(attribute='name') -%}
    {%- set ss.items = ss.items + [{'id': s.entity_id, 'name': s.name}] -%}
  {%- endfor -%}
  {%- set ns.rooms = ns.rooms + [{'area': a, 'name': area_name(a) or a, 'lights': ls.items, 'scenes': ss.items}] -%}
{%- endfor -%}
{{ {'rooms': ns.rooms, 'vacuum': ${state(vacuum)}, 'tv': ${state(tv)}, 'sensors': {$sensorStates}} | tojson }}
""".trim()
        }

        fun parse(raw: String): HomeSnapshot {
            val o = JSONObject(raw)
            val rooms = o.getJSONArray("rooms").objects().map { r ->
                val areaName = r.str("name") ?: r.getString("area")
                Room(
                    areaId = r.getString("area"),
                    name = areaName,
                    lights = r.getJSONArray("lights").objects().map { parseLight(it, areaName) },
                    scenes = r.getJSONArray("scenes").objects().map {
                        Scene(it.getString("id"), stripArea(it.str("name") ?: it.getString("id"), areaName))
                    },
                )
            }
            val sensors = o.optJSONObject("sensors")?.let { s ->
                s.keys().asSequence().associateWith { s.optString(it) }
            }.orEmpty()
            return HomeSnapshot(rooms, o.str("vacuum"), o.str("tv"), sensors)
        }

        private fun parseLight(l: JSONObject, areaName: String): Light {
            val hs = l.optJSONArray("hs")?.takeIf { it.length() == 2 }
            val rgb = l.optJSONArray("rgb")?.takeIf { it.length() == 3 }?.let {
                (it.getInt(0) shl 16) or (it.getInt(1) shl 8) or it.getInt(2)
            }
            return Light(
                entityId = l.getString("id"),
                name = stripArea(l.str("name") ?: l.getString("id"), areaName),
                state = l.str("state") ?: "unknown",
                brightness = l.int("brightness"),
                colorMode = l.str("color_mode")?.takeIf { it.isNotEmpty() && it != "None" },
                colorModes = l.optJSONArray("modes")?.strings()?.toSet().orEmpty(),
                hue = hs?.getDouble(0)?.toFloat(),
                saturation = hs?.getDouble(1)?.toFloat(),
                rgb = rgb,
                kelvin = l.int("kelvin"),
                effect = l.str("effect"),
                effects = l.optJSONArray("effects")?.strings().orEmpty(),
                icon = l.str("icon"),
            )
        }

        /** "Living Room Floor Lamp" → "Floor Lamp", since the room is already on screen. */
        private fun stripArea(name: String, areaName: String): String {
            val prefix = "$areaName "
            return if (name.length > prefix.length && name.startsWith(prefix, ignoreCase = true)) {
                name.substring(prefix.length)
            } else {
                name
            }
        }

        private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)
        private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else optDouble(key).toInt()
        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    }
}
