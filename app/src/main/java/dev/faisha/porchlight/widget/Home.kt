package dev.faisha.porchlight.widget

import android.content.Context
import dev.faisha.porchlight.data.HomeSnapshot
import org.json.JSONArray
import org.json.JSONObject

/*
 * Your home, as set in home.yaml at the project root (see home.example.yaml for every option).
 * The build checks that file and bundles it as assets/home.json; this reads it back.
 * Lights and scenes are not listed: they're found per area at runtime, so adding a light to an
 * area in HA adds it to the widget.
 */

data class RoomConfig(
    val areaId: String,
    val label: String,
    /** MDI name, drawn by [MdiIcons]. */
    val icon: String,
    /** Script that sends the vacuum to clean just this room, if any. */
    val cleanScript: String?,
)

data class TvConfig(val entityId: String, val label: String?)

/** A read-only tile on the House screen. */
data class SensorConfig(val entityId: String, val label: String, val icon: String, val unit: String?)

data class HomeConfig(
    val rooms: List<RoomConfig>,
    val vacuum: String?,
    val tv: TvConfig?,
    val allOffScript: String?,
    val sensors: List<SensorConfig>,
) {
    /** The House screen shows only when it has something on it. */
    val hasHouse get() = allOffScript != null || sensors.isNotEmpty()

    fun room(areaId: String?): RoomConfig? = rooms.firstOrNull { it.areaId == areaId }

    val snapshotTemplate: String by lazy {
        HomeSnapshot.template(
            areaIds = rooms.map { it.areaId },
            vacuum = vacuum,
            tv = tv?.entityId,
            sensors = sensors.map { it.entityId },
        )
    }

    companion object {
        private const val ASSET = "home.json"

        // Keep in step with HomeConfigChecker in app/build.gradle.kts.
        const val DEFAULT_ROOM_ICON = "lightbulb-outline"
        const val DEFAULT_SENSOR_ICON = "eye-outline"

        @Volatile private var cached: HomeConfig? = null

        /** @throws HomeConfigException if the bundled config can't be read. */
        fun get(context: Context): HomeConfig =
            cached ?: load(context.applicationContext).also { cached = it }

        private fun load(context: Context): HomeConfig = try {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(JSONObject(raw))
        } catch (e: HomeConfigException) {
            throw e
        } catch (e: Exception) {
            throw HomeConfigException("Can't read $ASSET: ${e.message}", e)
        }

        internal fun parse(o: JSONObject) = HomeConfig(
            rooms = o.objects("rooms").map {
                RoomConfig(
                    areaId = it.getString("area"),
                    label = it.getString("label"),
                    icon = it.text("icon") ?: DEFAULT_ROOM_ICON,
                    cleanScript = it.text("clean_script"),
                )
            },
            vacuum = o.text("vacuum"),
            tv = o.optJSONObject("tv")?.let { TvConfig(it.getString("entity"), it.text("label")) },
            allOffScript = o.text("all_off_script"),
            sensors = o.objects("sensors").map {
                SensorConfig(
                    entityId = it.getString("entity"),
                    label = it.getString("label"),
                    icon = it.text("icon") ?: DEFAULT_SENSOR_ICON,
                    unit = it.text("unit"),
                )
            },
        )

        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else optString(key).ifBlank { null }

        private fun JSONObject.objects(key: String): List<JSONObject> {
            val a: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.getJSONObject(it) }
        }
    }
}

class HomeConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
