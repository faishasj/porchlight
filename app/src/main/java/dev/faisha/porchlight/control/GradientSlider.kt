package dev.faisha.porchlight.control

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A pill-shaped slider whose track is a colour gradient, with a round thumb.
 * [value] runs 0–1. [onChange] fires while dragging (final = false) and once on release (final = true).
 */
class GradientSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var value: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    /** Track gradient, left to right. */
    var colors: IntArray = intArrayOf(Color.DKGRAY, Color.WHITE)
        set(v) {
            field = v
            updateShader()
            invalidate()
        }

    var onChange: ((value: Float, final: Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val track = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x55000000
    }

    private val inset get() = height / 2f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize((44 * density).toInt(), heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        track.set(0f, 0f, w.toFloat(), h.toFloat())
        updateShader()
    }

    private fun updateShader() {
        trackPaint.shader = if (width > 0) {
            LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
        } else {
            null
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(track, r, r, trackPaint)
        val cx = inset + value * (width - 2 * inset)
        val thumbR = r - 5 * density
        canvas.drawCircle(cx, r, thumbR, thumbPaint)
        canvas.drawCircle(cx, r, thumbR, thumbRing)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                moveTo(event.x, final = false)
            }
            MotionEvent.ACTION_MOVE -> moveTo(event.x, final = false)
            MotionEvent.ACTION_UP -> {
                moveTo(event.x, final = true)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> onChange?.invoke(value, true)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun moveTo(x: Float, final: Boolean) {
        val span = (width - 2 * inset).coerceAtLeast(1f)
        value = (x - inset) / span
        onChange?.invoke(value, final)
    }
}
