package dev.faisha.porchlight.control

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.faisha.porchlight.R
import dev.faisha.porchlight.data.ConfigStore
import dev.faisha.porchlight.data.HaClient
import dev.faisha.porchlight.data.Light
import dev.faisha.porchlight.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * A light's controls, opened by tapping its name on the widget: power, a brightness slider,
 * a warm/cool slider, a colour wheel, colour presets and modes (the light's effect_list).
 * Widgets only get taps, so the sliders and wheel have to live here. Only the controls the
 * light supports are shown.
 *
 * Getting back to the widget: "Done", the back gesture, or leaving the screen (it's noHistory,
 * so it never lingers behind the widget).
 */
class LightControlActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var entityId: String
    private var client: HaClient? = null
    private var minKelvin = DEFAULT_MIN_KELVIN
    private var maxKelvin = DEFAULT_MAX_KELVIN

    // What the screen shows. Updated as soon as a control is used, rather than waiting on HA.
    private var isOn = false
    private var brightnessPct: Int? = null
    private var colorMode: String? = null
    private var kelvin: Int? = null
    private var hue: Float? = null
    private var saturation: Float? = null
    private var effect: String? = null

    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var power: ImageView
    private lateinit var content: View
    private lateinit var controls: View
    private lateinit var sliders: View
    private lateinit var brightnessRow: View
    private lateinit var brightnessValue: TextView
    private lateinit var brightness: GradientSlider
    private lateinit var tempRow: View
    private lateinit var tempValue: TextView
    private lateinit var temp: GradientSlider
    private lateinit var wheel: ColorWheelView
    private lateinit var presetsLabel: View
    private lateinit var presets: LinearLayout
    private lateinit var modesLabel: View
    private lateinit var modes: LinearLayout

    private val presetViews = mutableListOf<Pair<Preset, View>>()
    private val modeViews = mutableListOf<Pair<String, TextView>>()

    private val errorListener: (Throwable) -> Unit = { e ->
        runOnUiThread { status.text = "Couldn't reach Home Assistant (${WidgetRefresher.describe(e)})" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: run { finish(); return }
        setContentView(R.layout.activity_light_control)

        title = findViewById(R.id.title)
        status = findViewById(R.id.status)
        power = findViewById(R.id.power)
        content = findViewById(R.id.content)
        controls = findViewById(R.id.controls)
        sliders = findViewById(R.id.sliders)
        brightnessRow = findViewById(R.id.brightness_row)
        brightnessValue = findViewById(R.id.brightness_value)
        brightness = findViewById(R.id.brightness)
        tempRow = findViewById(R.id.temp_row)
        tempValue = findViewById(R.id.temp_value)
        temp = findViewById(R.id.temp)
        wheel = findViewById(R.id.wheel)
        presetsLabel = findViewById(R.id.presets_label)
        presets = findViewById(R.id.presets)
        modesLabel = findViewById(R.id.modes_label)
        modes = findViewById(R.id.modes)

        title.text = intent.getStringExtra(EXTRA_NAME) ?: entityId
        findViewById<View>(R.id.done).setOnClickListener { finish() }
        power.setOnClickListener { togglePower() }

        brightness.colors = intArrayOf(getColor(R.color.app_track_start), AMBER)
        temp.colors = intArrayOf(0xFFFFA94D.toInt(), 0xFFFFF1D6.toInt(), 0xFFCFE0FF.toInt())

        brightness.onChange = { v, final ->
            brightnessPct = pctOf(v)
            showBrightness()
            send(JSONObject().put("brightness_pct", brightnessPct), final)
        }
        temp.onChange = { v, final ->
            kelvin = kelvinOf(v)
            colorMode = "color_temp"
            send(JSONObject().put("color_temp_kelvin", kelvin), final)
        }
        wheel.onChange = { h, s, final ->
            hue = h
            saturation = s
            colorMode = "hs"
            send(JSONObject().put("hs_color", JSONArray().put(h.toDouble()).put(s.toDouble())), final)
        }

        val config = ConfigStore(this).load()
        if (config == null) {
            status.text = "Open Porchlight and set up Home Assistant first"
            return
        }
        HaClient(this, config).also { client = it; LightCommandSender.onError = errorListener }
        status.text = "Loading…"
        scope.launch { load() }
    }

    override fun onDestroy() {
        scope.cancel()
        if (LightCommandSender.onError === errorListener) LightCommandSender.onError = null
        super.onDestroy()
    }

    private suspend fun load() {
        val client = client ?: return
        val json = try {
            client.getEntity(entityId)
        } catch (e: Exception) {
            status.text = "Couldn't load the light (${WidgetRefresher.describe(e)})"
            return
        }
        if (json == null) {
            status.text = "Home Assistant has no $entityId"
            return
        }
        val a = json.optJSONObject("attributes") ?: JSONObject()
        val hs = a.optJSONArray("hs_color")?.takeIf { it.length() == 2 }
        val light = Light(
            entityId = entityId,
            name = title.text.toString(),
            state = json.optString("state", "unknown"),
            brightness = a.intOrNull("brightness"),
            colorMode = a.strOrNull("color_mode"),
            colorModes = a.optJSONArray("supported_color_modes")?.strings()?.toSet().orEmpty(),
            hue = hs?.getDouble(0)?.toFloat(),
            saturation = hs?.getDouble(1)?.toFloat(),
            rgb = null,
            kelvin = a.intOrNull("color_temp_kelvin"),
            effect = a.strOrNull("effect"),
            effects = a.optJSONArray("effect_list")?.strings().orEmpty(),
            icon = null,
        )
        minKelvin = a.intOrNull("min_color_temp_kelvin") ?: DEFAULT_MIN_KELVIN
        maxKelvin = a.intOrNull("max_color_temp_kelvin") ?: DEFAULT_MAX_KELVIN
        if (maxKelvin <= minKelvin) {
            minKelvin = DEFAULT_MIN_KELVIN
            maxKelvin = DEFAULT_MAX_KELVIN
        }

        if (!light.isAvailable) {
            status.text = "This light is ${light.state} in Home Assistant"
            return
        }

        isOn = light.isOn
        brightnessPct = if (light.isOn) light.brightnessPct else null
        colorMode = light.colorMode
        kelvin = light.kelvin?.coerceIn(minKelvin, maxKelvin)
        hue = light.hue
        saturation = light.saturation
        effect = light.effect

        show(brightnessRow, light.supportsBrightness)
        show(brightness, light.supportsBrightness)
        show(tempRow, light.supportsTemp)
        show(temp, light.supportsTemp)
        show(sliders, light.supportsBrightness || light.supportsTemp)
        show(wheel, light.supportsColor)
        show(controls, light.supportsBrightness || light.supportsTemp || light.supportsColor)

        brightness.value = brightnessPct?.let { (it - 1) / 99f } ?: 0f
        temp.value = kelvin?.let { (it - minKelvin).toFloat() / (maxKelvin - minKelvin) } ?: 0.5f
        wheel.setColor(hue ?: 0f, saturation ?: 0f, showMarker = false)
        showBrightness()

        buildPresets(PRESETS.filter { if (it.isWhite) light.supportsTemp else light.supportsColor })
        buildModes(light.effects)

        power.visibility = View.VISIBLE
        content.visibility = View.VISIBLE
        render()
    }

    // ---- Actions ------------------------------------------------------------------------------

    private fun togglePower() {
        val client = client ?: return
        isOn = !isOn
        if (isOn) LightCommandSender.turnOn(client, entityId) else LightCommandSender.turnOff(client, entityId)
        LightCommandSender.refreshWidgetsSoon(this)
        showBrightness()
        render()
    }

    private fun pickPreset(p: Preset) {
        if (p.kelvin != null) {
            kelvin = p.kelvin.coerceIn(minKelvin, maxKelvin)
            colorMode = "color_temp"
            temp.value = (kelvin!! - minKelvin).toFloat() / (maxKelvin - minKelvin)
        } else {
            hue = p.hue
            saturation = p.sat
            colorMode = "hs"
            wheel.setColor(p.hue!!, p.sat, showMarker = true)
        }
        send(p.data(), final = true)
    }

    private fun pickMode(name: String) {
        effect = name
        send(JSONObject().put("effect", name), final = true)
    }

    /** turn_on with [data]; anything but the power button turns the light on. */
    private fun send(data: JSONObject, final: Boolean) {
        val client = client ?: return
        val wasOn = isOn
        isOn = true
        LightCommandSender.turnOn(client, entityId, data)
        if (final) LightCommandSender.refreshWidgetsSoon(this)
        if (!wasOn) showBrightness()
        render()
    }

    // ---- Rendering ----------------------------------------------------------------------------

    /** Brings the parts that depend on on/off, colour mode or effect up to date. */
    private fun render() {
        // Power shows its state by fill: amber when on, plain when off.
        power.setBackgroundResource(if (isOn) R.drawable.bg_power_on else R.drawable.bg_round)
        power.imageTintList = ColorStateList.valueOf(getColor(if (isOn) R.color.app_on_amber else R.color.app_sub))
        power.contentDescription = if (isOn) "Turn off" else "Turn on"

        val mode = colorMode
        wheel.showMarker = isOn && mode != null && mode in COLOUR_MODES && hue != null
        tempValue.text = if (isOn && mode == "color_temp" && kelvin != null) "$kelvin K" else ""

        val current = closestPreset(isOn, mode, kelvin, hue, saturation)
        presetViews.forEach { (p, v) -> v.background = dot(p.display, selected = p.key == current) }
        modeViews.forEach { (name, v) -> styleChip(v, selected = isOn && name == effect) }
        status.text = statusLine(current)
    }

    /** "Off", or what the light is doing: "On · 62% · Warm white". */
    private fun statusLine(presetKey: String?): String {
        if (!isOn) return "Off"
        val mode = effect?.takeIf { e ->
            modeViews.any { it.first == e } && !e.equals("off", ignoreCase = true) && !e.equals("none", ignoreCase = true)
        }
        val detail = mode ?: PRESETS.firstOrNull { it.key == presetKey }?.label
        return listOfNotNull("On", brightnessPct?.let { "$it%" }, detail).joinToString(" · ")
    }

    private fun showBrightness() {
        val pct = brightnessPct
        brightnessValue.text = if (!isOn || pct == null) "Off" else "$pct%"
        brightnessValue.setTextColor(getColor(if (!isOn || pct == null) R.color.app_sub else R.color.app_text))
    }

    private fun buildPresets(list: List<Preset>) {
        presets.removeAllViews()
        presetViews.clear()
        show(presetsLabel, list.isNotEmpty())
        show(presets, list.isNotEmpty())
        for (p in list) {
            val dotView = View(this).apply {
                contentDescription = p.label
                isClickable = true
                setOnClickListener { pickPreset(p) }
            }
            val cell = FrameLayout(this)
            cell.addView(dotView, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
            presets.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
            presetViews += p to dotView
        }
    }

    /** Effects as chips, three to a row like the widget used to show them. */
    private fun buildModes(names: List<String>) {
        modes.removeAllViews()
        modeViews.clear()
        show(modesLabel, names.isNotEmpty())
        show(modes, names.isNotEmpty())
        names.chunked(3).forEachIndexed { rowIndex, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 3) {
                val lp = LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(6) }
                val name = row.getOrNull(i)
                if (name == null) {
                    line.addView(View(this), lp)
                    continue
                }
                val chip = TextView(this).apply {
                    text = name
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(4), 0, dp(4), 0)
                    isClickable = true
                    setOnClickListener { pickMode(name) }
                }
                line.addView(chip, lp)
                modeViews += name to chip
            }
            val rowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (rowIndex > 0) rowLp.topMargin = dp(6)
            modes.addView(line, rowLp)
        }
    }

    /**
     * A preset swatch. The selected one gets a ring in the text colour with a gap, so it shows on
     * both light and dark backgrounds. The hairline edge keeps pale swatches visible on light.
     */
    private fun dot(color: Int, selected: Boolean): Drawable {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            if (selected) setStroke(dp(2), getColor(R.color.app_text))
        }
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(1), getColor(R.color.app_field_line))
        }
        return LayerDrawable(arrayOf(ring, fill)).apply { setLayerInset(1, dp(5), dp(5), dp(5), dp(5)) }
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(getColor(if (selected) R.color.app_selected else R.color.app_card))
        }
        chip.setTextColor(getColor(if (selected) R.color.app_on_selected else R.color.app_muted))
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun pctOf(v: Float) = (1 + v * 99).roundToInt()

    /** Rounded to 50 K so a drag doesn't send meaningless 1 K steps. */
    private fun kelvinOf(v: Float) =
        ((minKelvin + v * (maxKelvin - minKelvin)) / 50f).roundToInt() * 50

    private fun show(view: View, shown: Boolean) {
        view.visibility = if (shown) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optDouble(key).toInt()
    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() && it != "None" }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    companion object {
        /** Intent extra keys; the widget passes them as Glance action parameters of the same name. */
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_NAME = "name"

        // HA's defaults when a light doesn't report its own range.
        private const val DEFAULT_MIN_KELVIN = 2000
        private const val DEFAULT_MAX_KELVIN = 6500

        private val AMBER = 0xFFFFC94D.toInt()
        private val COLOUR_MODES = setOf("hs", "xy", "rgb", "rgbw", "rgbww")
    }
}
