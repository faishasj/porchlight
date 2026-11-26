package dev.faisha.porchlight.control

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Hue/saturation wheel: hue goes round (0° at 3 o'clock, clockwise, like HA's hs_color),
 * saturation goes from white in the middle to full colour at the edge.
 * [onChange] fires while dragging (final = false) and once on release (final = true).
 */
class ColorWheelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** 0–360. */
    var hue = 0f
        private set
    /** 0–100. */
    var saturation = 0f
        private set
    /** Off while the light is off or in a white (colour temperature) mode. */
    var showMarker = false
        set(v) {
            field = v
            invalidate()
        }

    var onChange: ((hue: Float, saturation: Float, final: Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.WHITE
    }
    private val markerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x88000000.toInt()
    }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    fun setColor(hue: Float, saturation: Float, showMarker: Boolean) {
        this.hue = ((hue % 360f) + 360f) % 360f
        this.saturation = saturation.coerceIn(0f, 100f)
        this.showMarker = showMarker
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - 2 * density
        if (radius <= 0f) return
        val stops = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f)) }
        huePaint.shader = SweepGradient(cx, cy, stops, null)
        whitePaint.shader = RadialGradient(cx, cy, radius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, huePaint)
        canvas.drawCircle(cx, cy, radius, whitePaint)
        if (!showMarker) return
        val angle = Math.toRadians(hue.toDouble())
        val dist = radius * saturation / 100f
        val mx = cx + (dist * cos(angle)).toFloat()
        val my = cy + (dist * sin(angle)).toFloat()
        val r = 11 * density
        markerFill.color = Color.HSVToColor(floatArrayOf(hue, saturation / 100f, 1f))
        canvas.drawCircle(mx, my, r, markerFill)
        canvas.drawCircle(mx, my, r, markerRing)
        canvas.drawCircle(mx, my, r + 2 * density, markerShadow)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Let touches in the corners outside the wheel fall through (e.g. to scroll).
                if (hypot(event.x - cx, event.y - cy) > radius + 12 * density) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                pick(event.x, event.y, final = false)
            }
            MotionEvent.ACTION_MOVE -> pick(event.x, event.y, final = false)
            MotionEvent.ACTION_UP -> {
                pick(event.x, event.y, final = true)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> onChange?.invoke(hue, saturation, true)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun pick(x: Float, y: Float, final: Boolean) {
        val dx = x - cx
        val dy = y - cy
        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        hue = Math.round((deg + 360f) % 360f).toFloat() % 360f
        saturation = Math.round(min(hypot(dx, dy) / radius, 1f) * 100f).toFloat()
        showMarker = true
        onChange?.invoke(hue, saturation, final)
    }
}
