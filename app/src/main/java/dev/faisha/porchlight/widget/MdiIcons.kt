package dev.faisha.porchlight.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import kotlin.math.roundToInt

/**
 * Material Design Icons (the set Home Assistant uses), bundled as a font in assets/mdi, so an
 * `icon:` in home.yaml and a light's icon in HA can be any name at
 * https://pictogrammers.com/library/mdi/.
 *
 * Each glyph is drawn white into a bitmap, and the widget tints it like its drawable icons.
 */
object MdiIcons {
    private const val DIR = "mdi"

    // The font's em is the icon's 24-unit box. Its ascent is 448 of 512 units, so the baseline
    // sits 7/8 of the way down the box.
    private const val BASELINE = 448f / 512f

    @Volatile private var font: Typeface? = null
    @Volatile private var codepoints: Map<String, Int>? = null
    private val bitmaps = LruCache<String, Bitmap>(64)

    /**
     * The glyph for [name] (`sofa` or `mdi:sofa`) drawn [sizeDp] square, or null when MDI has
     * no icon by that name.
     */
    fun bitmap(context: Context, name: String, sizeDp: Int): Bitmap? {
        val id = normalise(name) ?: return null
        val px = (sizeDp * context.resources.displayMetrics.density).roundToInt()
        val key = "$id@$px"
        bitmaps.get(key)?.let { return it }
        val codepoint = codepoints(context)[id] ?: return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context)
            color = Color.WHITE
            textSize = px.toFloat()
        }
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(String(Character.toChars(codepoint)), 0f, px * BASELINE, paint)
        bitmaps.put(key, bitmap)
        return bitmap
    }

    /** `mdi:sofa` and `sofa` are the same icon; other prefixes (`hass:`, `hue:`) aren't MDI. */
    internal fun normalise(name: String): String? {
        val trimmed = name.trim()
        val id = if (':' in trimmed) trimmed.removePrefix("mdi:").takeUnless { ':' in it } else trimmed
        return id?.takeIf { it.isNotEmpty() }
    }

    private fun font(context: Context): Typeface =
        font ?: Typeface.createFromAsset(context.assets, "$DIR/materialdesignicons.ttf").also { font = it }

    private fun codepoints(context: Context): Map<String, Int> =
        codepoints ?: context.assets.open("$DIR/codepoints.txt").bufferedReader()
            .use { parseCodepoints(it.readText()) }
            .also { codepoints = it }

    /** Reads the bundled name list: one `name hex` pair per line. */
    internal fun parseCodepoints(text: String): Map<String, Int> =
        text.lineSequence()
            .map { it.trim().split(' ') }
            .filter { it.size == 2 }
            .associate { (name, hex) -> name to hex.toInt(16) }
}
