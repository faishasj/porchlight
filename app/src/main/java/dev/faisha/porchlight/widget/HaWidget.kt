package dev.faisha.porchlight.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.faisha.porchlight.R
import dev.faisha.porchlight.SettingsActivity
import dev.faisha.porchlight.control.LightControlActivity
import dev.faisha.porchlight.data.HomeSnapshot
import dev.faisha.porchlight.data.Light
import dev.faisha.porchlight.data.LightLevel
import dev.faisha.porchlight.data.Room
import dev.faisha.porchlight.data.ThemeMode
import dev.faisha.porchlight.data.ThemeStore
import org.json.JSONObject
import java.util.Calendar

class HaWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context, currentState()) }
    }
}

/**
 * A colour in each theme. The widget is drawn by the launcher, which never sees the app's own
 * night mode, so each colour carries both values and [provider] picks for the Theme setting.
 */
private class Tone(val light: Color, val dark: Color) {
    fun alpha(a: Float) = Tone(light.copy(alpha = a), dark.copy(alpha = a))
}

private fun tone(dark: Long, light: Long) = Tone(light = Color(light), dark = Color(dark))

/** The Theme setting, read once per composition in [Content]. */
private val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

/** System hands the launcher both colours and lets it follow the phone; Light and Dark pick one. */
@Composable
private fun Tone.provider(): ColorProvider = when (LocalThemeMode.current) {
    ThemeMode.SYSTEM -> DayNightColorProvider(day = light, night = dark)
    ThemeMode.LIGHT -> ColorProvider(light)
    ThemeMode.DARK -> ColorProvider(dark)
}

@Composable
private fun GlanceModifier.background(tone: Tone): GlanceModifier = background(tone.provider())

// The widget's colour palette, dark and light. On light, the accents darken so text stays readable.
private object C {
    val screen = tone(dark = 0xFF000000, light = 0xFFEEEEF2)
    val card = tone(dark = 0xFF1C1C1F, light = 0xFFFFFFFF)
    val cardDark = tone(dark = 0xFF111113, light = 0xFFE4E4EA)
    val text = tone(dark = 0xFFEDEDF0, light = 0xFF17171B)
    val sub = tone(dark = 0xFF9A9AA2, light = 0xFF64646D)
    val muted = tone(dark = 0xFFC9C9CE, light = 0xFF45454C)
    val soft = tone(dark = 0xFFE6E6E9, light = 0xFF2A2A30)
    val dim = tone(dark = 0xFF55555C, light = 0xFFB4B4BB)
    val error = tone(dark = 0xFFFF6B6B, light = 0xFFC93434)
    val amber = tone(dark = 0xFFFFC94D, light = 0xFF8A5A00)
    val blue = tone(dark = 0xFF8FB4FF, light = 0xFF2F5BC4)
    val tileAll = tone(dark = 0xFF4A3800, light = 0xFFFFE29A)
    val tileSome = tone(dark = 0xFF2E2614, light = 0xFFFFF0CC)
    val tileNone = tone(dark = 0xFF1A2230, light = 0xFFE4EBFA)
    val tileUnknown = tone(dark = 0xFF1C1C1C, light = 0xFFE4E4EA)
    val lightOff = tone(dark = 0xFF161618, light = 0xFFFFFFFF)
    val lightSub = tone(dark = 0xFFA9A9B1, light = 0xFF5E5E67)
    val iconOff = tone(dark = 0xFF26262A, light = 0xFFEEEEF2)
    val iconOffTint = tone(dark = 0xFF8E8E96, light = 0xFF7A7A82)
    val vacBg = tone(dark = 0xFF13201B, light = 0xFFDFF2E9)
    val vacBgActive = tone(dark = 0xFF1F4A3A, light = 0xFFBFE8D5)
    val vac = tone(dark = 0xFF7FD6B0, light = 0xFF1C6E4C)
    val vacActive = tone(dark = 0xFF9BF0C8, light = 0xFF0F5E3E)
    val danger = tone(dark = 0xFFFF8A8A, light = 0xFFC93434)
    val confirmBg = tone(dark = 0xFF5A1A1A, light = 0xFFFBDADA)
    val confirm = tone(dark = 0xFFFFB3B3, light = 0xFF9E1F1F)
    /** Behind a lit light's icon, dark theme only (light fills it with the light's own colour). */
    val iconOnDark = Color(0x29FFC94D)
    /** Fallback colour for a lit light that doesn't report one. */
    val glow = Color(0xFFFFC94D)
}

/** Everything a screen needs besides the snapshot. */
private class Ui(
    val context: Context,
    val home: HomeConfig,
    val snapshot: HomeSnapshot,
    val busy: Set<String>,
    val error: String?,
    /** [error] comes from a setting (Battery, Data Saver); tapping it opens the app to fix it. */
    val errorFixable: Boolean,
    val updatedAt: Long?,
    val lastCleanRoom: String?,
    val allOffArmed: Boolean,
) {
    fun isBusy(key: String) = key in busy
    val refreshing get() = isBusy(REFRESH_BUSY_KEY)
    val vacuumRunning get() = snapshot.vacuum == "cleaning"
    val roomsOn get() = snapshot.rooms.count { it.onCount > 0 }

    val vacuumLine: String
        get() {
            val base = when (val s = snapshot.vacuum) {
                "cleaning" -> "Cleaning"
                "docked" -> "Docked"
                "paused" -> "Paused"
                "returning" -> "Returning to dock"
                "idle" -> "Idle"
                "error" -> "Error"
                null, "unknown" -> "Unknown"
                "unavailable" -> "Unavailable"
                else -> s.replaceFirstChar { it.uppercase() }
            }
            val room = home.room(lastCleanRoom)?.label
            return if (vacuumRunning && room != null) "$base · $room" else base
        }

    val tvLine: String
        get() = when (val s = snapshot.tv) {
            "playing" -> "Playing"
            "paused" -> "Paused"
            "on", "idle" -> "On"
            "off", "standby" -> "Off"
            null, "unknown", "unavailable" -> "Unavailable"
            else -> s.replaceFirstChar { it.uppercase() }
        }
}

@Composable
private fun Content(context: Context, prefs: Preferences) {
    val configured = prefs[WidgetKeys.configured] ?: false
    val snapshot = prefs[WidgetKeys.snapshot]?.let { runCatching { HomeSnapshot.parse(it) }.getOrNull() }
    val error = prefs[WidgetKeys.error]
    val errorFixable = error != null && prefs[WidgetKeys.errorFix] != null
    val now = System.currentTimeMillis()
    val busy = BusyState.active(prefs[WidgetKeys.busy] ?: emptySet(), now)

    // Null only if the bundled home.json is broken. The build checks it, so this is a safety net.
    val home = runCatching { HomeConfig.get(context) }.getOrNull()

    CompositionLocalProvider(LocalThemeMode provides ThemeStore.get(context)) {
        Column(modifier = GlanceModifier.fillMaxSize().background(C.screen).padding(6.dp)) {
            if (home == null) {
                SetupScreen(configured = true, error = "Config error", fixable = false, retrying = false)
            } else if (!configured || snapshot == null) {
                SetupScreen(configured, error, errorFixable, retrying = REFRESH_BUSY_KEY in busy)
            } else {
                val ui = Ui(
                    context = context,
                    home = home,
                    snapshot = snapshot,
                    busy = busy,
                    error = error,
                    errorFixable = errorFixable,
                    updatedAt = prefs[WidgetKeys.updatedAt],
                    lastCleanRoom = prefs[WidgetKeys.lastCleanRoom],
                    allOffArmed = isAllOffArmed(prefs[WidgetKeys.allOffArmedAt], now),
                )
                var screen = prefs[WidgetKeys.screen]
                    ?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.HOME
                val room = snapshot.room(prefs[WidgetKeys.room])
                // If HA no longer has the room we were showing, step back a level.
                if (screen == Screen.ROOM && room == null) screen = Screen.LIGHTS
                // Likewise if home.yaml no longer sets up the screen we were on.
                val unavailable = (screen == Screen.VACUUM && home.vacuum == null) ||
                    (screen == Screen.TV && home.tv == null) ||
                    (screen == Screen.HOUSE && !home.hasHouse)
                if (unavailable) screen = Screen.HOME

                when (screen) {
                    Screen.HOME -> HomeScreen(ui)
                    Screen.LIGHTS -> LightsScreen(ui)
                    Screen.ROOM -> RoomScreen(ui, room!!)
                    Screen.VACUUM -> VacuumScreen(ui)
                    Screen.TV -> TvScreen(ui)
                    Screen.HOUSE -> HouseScreen(ui)
                }
            }
        }
    }
}

// ---- Screens ------------------------------------------------------------------------------

@Composable
private fun ColumnScope.HomeScreen(ui: Ui) {
    Header(ui, greeting(), subtitle = null, back = null)
    val home = ui.home
    val lights = if (ui.roomsOn > 0) C.tileSome to C.amber else C.tileNone to C.blue
    // Lights always; the others only when home.yaml sets them up.
    val tiles = listOfNotNull(
        TileSpec(
            "Lights",
            if (ui.roomsOn > 0) "${ui.roomsOn} of ${ui.snapshot.rooms.size} rooms on" else "All off",
            res(R.drawable.ic_light_bulb), lights.first, lights.second, navigate(Screen.LIGHTS), 40.dp,
        ),
        home.vacuum?.let {
            TileSpec(
                "Vacuum", ui.vacuumLine, res(R.drawable.ic_robot),
                if (ui.vacuumRunning) C.vacBgActive else C.vacBg, C.vac, navigate(Screen.VACUUM), 40.dp,
            )
        },
        home.tv?.let { TileSpec("TV", ui.tvLine, res(R.drawable.ic_tv), C.card, C.soft, navigate(Screen.TV), 40.dp) },
        if (home.hasHouse) {
            TileSpec("House", houseSummary(home), res(R.drawable.ic_house), C.card, C.soft, navigate(Screen.HOUSE), 40.dp)
        } else {
            null
        },
    )
    TileGrid(tiles.chunked(2))
}

@Composable
private fun ColumnScope.LightsScreen(ui: Ui) {
    val total = ui.snapshot.rooms.size
    Header(
        ui, "Lights",
        if (ui.roomsOn > 0) "${ui.roomsOn} of $total rooms on" else "Everything off",
        back = navigate(Screen.HOME),
    )
    val tiles = ui.home.rooms.map { cfg ->
        val room = ui.snapshot.room(cfg.areaId)
        val (bg, fg) = levelColors(room?.level ?: LightLevel.UNKNOWN)
        TileSpec(
            cfg.label, room?.let(::roomSummary) ?: "Not found", mdi(cfg.icon), bg, fg,
            room?.let { navigate(Screen.ROOM, room = cfg.areaId) },
        )
    }
    TileGrid(tiles.chunked(2))
}

@Composable
private fun ColumnScope.RoomScreen(ui: Ui, room: Room) {
    val label = ui.home.room(room.areaId)?.label ?: room.name
    Header(ui, label, roomSummary(room, detailed = true), back = navigate(Screen.LIGHTS))

    val area = JSONObject().put("area_id", room.areaId)
    val chips = buildList {
        add(ChipSpec("All on", R.drawable.ic_all_on, C.amber, callService("light", "turn_on", area, "on:${room.areaId}"), ui.isBusy("on:${room.areaId}")))
        add(ChipSpec("All off", R.drawable.ic_all_off, C.blue, callService("light", "turn_off", area, "off:${room.areaId}"), ui.isBusy("off:${room.areaId}")))
        room.scenes.forEach { scene ->
            add(ChipSpec(scene.name, R.drawable.ic_scene, C.soft, callService("scene", "turn_on", entity(scene.entityId), scene.entityId), ui.isBusy(scene.entityId)))
        }
    }

    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        chips.chunked(3).forEach { row ->
            item {
                ListRow {
                    row.forEachIndexed { i, chip ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Box(GlanceModifier.defaultWeight()) { IconChip(chip) }
                    }
                    repeat(3 - row.size) {
                        Spacer(GlanceModifier.width(6.dp))
                        Box(GlanceModifier.defaultWeight()) {}
                    }
                }
            }
        }
        if (room.lights.isEmpty()) {
            item { ListRow { Hint("No lights in this area") } }
        }
        room.lights.chunked(2).forEach { pair ->
            item {
                ListRow {
                    Box(GlanceModifier.defaultWeight()) { LightTile(ui, room, pair[0]) }
                    Spacer(GlanceModifier.width(6.dp))
                    Box(GlanceModifier.defaultWeight()) { pair.getOrNull(1)?.let { LightTile(ui, room, it) } }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.VacuumScreen(ui: Ui) {
    val vacuum = ui.home.vacuum ?: return
    Header(ui, "Vacuum", ui.vacuumLine, back = navigate(Screen.HOME))
    val running = ui.vacuumRunning
    val vac = entity(vacuum)

    val startPause = TileSpec(
        if (running) "Pause" else "Start",
        when {
            ui.isBusy("vac") -> "…"
            running -> "Cleaning"
            else -> "Whole home"
        },
        res(if (running) R.drawable.ic_pause else R.drawable.ic_play),
        C.card, C.vac,
        if (running) callService("vacuum", "pause", vac, "vac") else callService("vacuum", "start", vac, "vac", cleanRoom = ""),
        30.dp,
    )
    val dock = TileSpec(
        "Dock", if (ui.isBusy("dock")) "…" else "Send home", res(R.drawable.ic_dock), C.card, C.soft,
        callService("vacuum", "return_to_base", vac, "dock", cleanRoom = ""), 30.dp,
    )
    // One tile per room that has a clean_script.
    val rooms = ui.home.rooms.mapNotNull { cfg ->
        val script = cfg.cleanScript ?: return@mapNotNull null
        val active = running && ui.lastCleanRoom == cfg.areaId
        val key = "clean:${cfg.areaId}"
        TileSpec(
            cfg.label,
            when {
                ui.isBusy(key) -> "Starting…"
                active -> "Cleaning now"
                else -> "Clean room"
            },
            mdi(cfg.icon),
            if (active) C.vacBgActive else C.vacBg,
            if (active) C.vacActive else C.vac,
            callService("script", "turn_on", entity(script), key, cleanRoom = cfg.areaId),
            30.dp,
        )
    }
    // Start and Dock on the left, then the rooms in two rows, for any number of rooms.
    val top = rooms.take((rooms.size + 1) / 2)
    val bottom = rooms.drop(top.size)
    TileGrid(
        listOf(
            listOf(startPause) + top,
            listOf(dock) + bottom + List(top.size - bottom.size) { null },
        ),
    )
}

@Composable
private fun ColumnScope.TvScreen(ui: Ui) {
    val tv = ui.home.tv ?: return
    Header(ui, "TV", ui.tvLine, back = navigate(Screen.HOME))
    val playing = ui.snapshot.tv == "playing"
    TileGrid(
        listOf(
            listOf(
                TileSpec(
                    if (playing) "Pause" else "Play",
                    if (ui.isBusy("tv")) "…" else tv.label ?: "Play or pause",
                    res(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    C.card, C.soft,
                    callService("media_player", "media_play_pause", entity(tv.entityId), "tv"),
                    56.dp,
                ),
            ),
        ),
    )
}

@Composable
private fun ColumnScope.HouseScreen(ui: Ui) {
    Header(ui, "House", subtitle = null, back = navigate(Screen.HOME))
    val home = ui.home
    val allOff = home.allOffScript?.let {
        when {
            ui.isBusy(ALL_OFF_BUSY_KEY) ->
                TileSpec("Turning off…", "Lights and media", res(R.drawable.ic_power), C.card, C.dim, null, 34.dp)
            ui.allOffArmed ->
                TileSpec("Tap again to confirm", "Lights and media off", res(R.drawable.ic_power), C.confirmBg, C.confirm, actionRunCallback<AllOffAction>(), 34.dp)
            else ->
                TileSpec("All off", "Lights and media", res(R.drawable.ic_power), C.card, C.danger, actionRunCallback<AllOffAction>(), 34.dp)
        }
    }
    val sensors = home.sensors.map { sensor ->
        val state = ui.snapshot.sensors[sensor.entityId]?.takeUnless { it == "unknown" || it == "unavailable" }
        TileSpec(
            sensor.label, state?.let { listOfNotNull(it, sensor.unit).joinToString(" ") } ?: "—",
            mdi(sensor.icon), C.cardDark, C.muted, null,
        )
    }
    val vacuum = home.vacuum?.let {
        TileSpec("Vacuum", ui.vacuumLine, res(R.drawable.ic_robot), C.cardDark, C.muted, navigate(Screen.VACUUM))
    }
    // All off across the top, then the small tiles two to a row.
    TileGrid(listOfNotNull(allOff?.let { listOf(it) }) + (sensors + listOfNotNull(vacuum)).chunked(2))
}

@Composable
private fun SetupScreen(configured: Boolean, error: String?, fixable: Boolean, retrying: Boolean) {
    val showError = configured && error != null && !retrying
    val action: Action = when {
        !configured -> actionStartActivity<SettingsActivity>()
        showError && fixable -> fixSettingAction()
        else -> actionRunCallback<RefreshAction>()
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.ic_house, "Home Assistant", C.blue, 48.dp)
        Spacer(GlanceModifier.height(10.dp))
        Text(
            when {
                !configured -> "Tap to set up"
                retrying && error != null -> "Retrying…"
                error != null -> error
                else -> "Loading…"
            },
            style = textStyle(if (showError) C.error else C.text, 15, bold = true, center = true),
        )
        if (showError) {
            Text(if (fixable) "Tap to fix" else "Tap to retry", style = textStyle(C.sub, 12, center = true))
        }
    }
}

/**
 * Opens the app's main screen, whose banner explains the Battery or Data Saver setting and opens
 * it, then refreshes the widget once it's fixed. Not the setting itself: a widget can't tell when
 * it has been changed, and the intent would be stale after.
 */
private fun fixSettingAction(): Action = actionStartActivity<SettingsActivity>(
    actionParametersOf(ActionParameters.Key<Boolean>(SettingsActivity.EXTRA_FIX_BACKGROUND) to true),
)

// ---- Building blocks ----------------------------------------------------------------------

/** @param subtitle null shows the live "Updated … ago" timer. */
@Composable
private fun Header(ui: Ui, title: String, subtitle: String?, back: Action?) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) {
            IconButton(R.drawable.ic_back, "Back", C.text, back, bg = C.card)
            Spacer(GlanceModifier.width(8.dp))
        } else {
            Spacer(GlanceModifier.width(6.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(title, maxLines = 1, style = textStyle(C.text, 16, bold = true))
            when {
                ui.error != null && ui.refreshing -> Text("Refreshing…", maxLines = 1, style = textStyle(C.sub, 12))
                ui.error != null && ui.errorFixable -> Text(
                    ui.error, maxLines = 1, style = textStyle(C.error, 12),
                    modifier = GlanceModifier.clickable(fixSettingAction()),
                )
                ui.error != null -> Text(ui.error, maxLines = 1, style = textStyle(C.error, 12))
                subtitle != null -> Text(subtitle, maxLines = 1, style = textStyle(C.sub, 12))
                ui.updatedAt != null -> UpdatedTimer(ui.context, ui.updatedAt)
            }
        }
        IconButton(
            R.drawable.ic_refresh, "Refresh",
            if (ui.refreshing) C.dim else C.blue,
            actionRunCallback<RefreshAction>(),
        )
    }
    Spacer(GlanceModifier.height(6.dp))
}

/**
 * "Updated 04:12 ago", ticking on its own. A widget can't redraw itself every minute, so a
 * plain "4 min ago" label would be wrong between refreshes; a Chronometer is always right.
 */
@Composable
private fun UpdatedTimer(context: Context, updatedAt: Long) {
    val views = RemoteViews(context.packageName, R.layout.updated_chronometer)
    val age = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0)
    val id = R.id.updated_chronometer
    when (LocalThemeMode.current) {
        ThemeMode.SYSTEM -> views.setColorInt(id, "setTextColor", C.sub.light.toArgb(), C.sub.dark.toArgb())
        ThemeMode.LIGHT -> views.setTextColor(id, C.sub.light.toArgb())
        ThemeMode.DARK -> views.setTextColor(id, C.sub.dark.toArgb())
    }
    views.setChronometer(
        R.id.updated_chronometer,
        SystemClock.elapsedRealtime() - age,
        context.getString(R.string.updated_format),
        true,
    )
    AndroidRemoteViews(views)
}

private class TileSpec(
    val label: String,
    val sub: String,
    val icon: ImageProvider,
    val bg: Tone,
    val fg: Tone,
    val action: Action?,
    val iconSize: Dp = 36.dp,
)

/** A null tile leaves its cell empty, so rows of different lengths stay aligned. */
@Composable
private fun ColumnScope.TileGrid(rows: List<List<TileSpec?>>) {
    Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        rows.forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                row.forEach { tile ->
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(3.dp)) {
                        tile?.let { BigTile(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigTile(t: TileSpec) {
    var modifier = GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(20.dp).padding(6.dp)
    if (t.action != null) modifier = modifier.clickable(t.action)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(t.icon, t.label, t.fg, t.iconSize)
        Spacer(GlanceModifier.height(5.dp))
        Text(t.label, maxLines = 2, style = textStyle(t.fg, 15, bold = true, center = true))
        Text(t.sub, maxLines = 2, style = textStyle(t.fg.alpha(0.85f), 12, center = true))
    }
}

@Composable
private fun LightTile(ui: Ui, room: Room, light: Light) {
    val busy = ui.isBusy(light.entityId)
    // Dark theme tints the glyph with the light's colour on a faint amber circle. Light theme
    // fills the circle with the light's colour and draws the glyph dark (or white on a dark colour),
    // since a pale glyph would vanish on a pale tile.
    val glow = lightTint(light)
    val iconTint = when {
        busy || !light.isAvailable -> C.dim
        light.isOn -> Tone(light = if (glow.luminance() > 0.35f) Color(0xFF17171B) else Color.White, dark = glow)
        else -> C.iconOffTint
    }
    val iconBg = if (light.isOn) Tone(light = glow, dark = C.iconOnDark) else C.iconOff
    var iconBox = GlanceModifier.size(44.dp).background(iconBg).cornerRadius(22.dp)
    if (light.isAvailable) iconBox = iconBox.clickable(callService("light", "toggle", entity(light.entityId), light.entityId))

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(64.dp)
            .background(if (light.isOn) C.tileSome else C.lightOff).cornerRadius(18.dp)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = iconBox, contentAlignment = Alignment.Center) {
            Icon(mdi(light.icon, 24), "Toggle ${light.name}", iconTint, 24.dp)
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                .clickable(openLightControls(light)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(light.name, maxLines = 2, style = textStyle(C.text, 13, bold = true))
            Text(lightSummary(light, busy), maxLines = 1, style = textStyle(C.lightSub, 12))
        }
    }
}

private class ChipSpec(val label: String, val icon: Int, val fg: Tone, val action: Action, val busy: Boolean)

@Composable
private fun IconChip(chip: ChipSpec) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(44.dp).background(C.card).cornerRadius(22.dp)
            .padding(horizontal = 10.dp).clickable(chip.action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(chip.icon, null, if (chip.busy) C.dim else chip.fg, 18.dp)
        Spacer(GlanceModifier.width(6.dp))
        Text(if (chip.busy) "…" else chip.label, maxLines = 1, style = textStyle(if (chip.busy) C.dim else chip.fg, 13, bold = true))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = textStyle(C.sub, 13), modifier = GlanceModifier.padding(8.dp))
}

/** A full-width row inside a LazyColumn, with the gap below it. */
@Composable
private fun ListRow(content: @Composable RowScope.() -> Unit) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun IconButton(icon: Int, description: String, tint: Tone, action: Action, bg: Tone? = null) {
    var modifier = GlanceModifier.size(44.dp)
    if (bg != null) modifier = modifier.background(bg).cornerRadius(22.dp)
    Box(modifier = modifier.clickable(action), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint, 22.dp)
    }
}

private fun res(id: Int) = ImageProvider(id)

/** An MDI icon by name (`sofa` or `mdi:sofa`), or a bulb if MDI has no such icon. */
@Composable
private fun mdi(name: String?, sizeDp: Int = 40): ImageProvider =
    name?.let { MdiIcons.bitmap(LocalContext.current, it, sizeDp) }?.let { ImageProvider(it) }
        ?: res(R.drawable.ic_light_bulb)

@Composable
private fun Icon(id: Int, description: String?, tint: Tone, size: Dp) = Icon(res(id), description, tint, size)

@Composable
private fun Icon(provider: ImageProvider, description: String?, tint: Tone, size: Dp) {
    Image(
        provider = provider,
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint.provider()),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun textStyle(color: Tone, size: Int, bold: Boolean = false, center: Boolean = false) = TextStyle(
    color = color.provider(),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    textAlign = if (center) TextAlign.Center else TextAlign.Start,
)

// ---- Helpers ------------------------------------------------------------------------------

private val ControlEntityKey = ActionParameters.Key<String>(LightControlActivity.EXTRA_ENTITY_ID)
private val ControlNameKey = ActionParameters.Key<String>(LightControlActivity.EXTRA_NAME)

/** Opens the light's control screen (power, sliders, colour wheel, presets, modes). */
private fun openLightControls(light: Light): Action = actionStartActivity<LightControlActivity>(
    actionParametersOf(ControlEntityKey to light.entityId, ControlNameKey to light.name),
)

private fun lightTint(light: Light): Color =
    light.rgb?.let { Color(0xFF000000.toInt() or it) } ?: C.glow

private fun lightSummary(light: Light, busy: Boolean): String = when {
    busy -> "…"
    !light.isAvailable -> light.state.replaceFirstChar { it.uppercase() }
    light.isOn -> light.brightnessPct?.let { "On · $it%" } ?: "On"
    else -> "Off"
}

/** The House tile's subtitle, e.g. "All off · Outside" or "All off · 3 sensors". */
private fun houseSummary(home: HomeConfig): String = listOfNotNull(
    home.allOffScript?.let { "All off" },
    when (home.sensors.size) {
        0 -> null
        1 -> home.sensors[0].label
        else -> "${home.sensors.size} sensors"
    },
).joinToString(" · ")

private fun levelColors(level: LightLevel): Pair<Tone, Tone> = when (level) {
    LightLevel.ALL -> C.tileAll to C.amber
    LightLevel.SOME -> C.tileSome to C.amber
    LightLevel.NONE -> C.tileNone to C.blue
    LightLevel.UNKNOWN -> C.tileUnknown to C.sub
}


private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}
