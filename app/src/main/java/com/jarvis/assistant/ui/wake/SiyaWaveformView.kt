package com.jarvis.assistant.ui.wake

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import com.jarvis.assistant.R
import kotlin.math.sin

/**
 * Native animated waveform bars for the Siya AI floating popup.
 *
 * Renders 6 vertical rounded bars driven by real-time audioLevel from JarvisConversationService.
 * Seamlessly transitions to a gentle sine-wave idle breathing animation when audio level is near 0.
 * Automatically samples the active theme's accent gradient (themeAccent1 -> themeAccent2).
 */
class SiyaWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 6
    private val barPaints = Array(barCount) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val barRect = RectF()

    private var currentLevel = 0.05f
    private var targetLevel = 0.05f

    private var accent1Color: Int = Color.parseColor("#FF6A3D")
    private var accent2Color: Int = Color.parseColor("#F43F5E")

    private var phase = 0f
    private val idleAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 2f * Math.PI.toFloat()).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            phase = animator.animatedValue as Float
            // Smoothly lerp towards target audio level
            currentLevel += (targetLevel - currentLevel) * 0.28f
            invalidate()
        }
    }

    init {
        resolveThemeAccents()
    }

    private fun resolveThemeAccents() {
        val typedValue = TypedValue()
        val theme = context.theme

        if (theme.resolveAttribute(R.attr.themeAccent1, typedValue, true)) {
            accent1Color = typedValue.data
        }
        if (theme.resolveAttribute(R.attr.themeAccent2, typedValue, true)) {
            accent2Color = typedValue.data
        } else if (theme.resolveAttribute(R.attr.themeAccent, typedValue, true)) {
            accent2Color = typedValue.data
        }
    }

    fun setAudioLevel(level: Float) {
        targetLevel = level.coerceIn(0.0f, 1.0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!idleAnimator.isRunning) {
            idleAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleAnimator.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            accent1Color, accent2Color,
            Shader.TileMode.CLAMP
        )
        for (paint in barPaints) {
            paint.shader = shader
            paint.style = Paint.Style.FILL
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val spacing = w / (barCount * 2f + 1f)
        val barWidth = spacing * 1.25f
        val maxBarHeight = h * 0.88f
        val minBarHeight = h * 0.16f
        val cornerRadius = barWidth / 2f

        val totalGroupWidth = barCount * barWidth + (barCount - 1) * spacing
        var startX = (w - totalGroupWidth) / 2f

        for (i in 0 until barCount) {
            // Harmonic wave offset for each bar
            val idleOffset = sin(phase + i * 0.75).toFloat() * 0.5f + 0.5f
            val baseIdleHeight = minBarHeight + (maxBarHeight * 0.22f * idleOffset)

            // Dynamic height based on audio level
            val dynamicBoost = currentLevel * maxBarHeight * (0.6f + 0.4f * sin(phase * 1.5 + i).toFloat())
            val barH = (baseIdleHeight + dynamicBoost).coerceIn(minBarHeight, maxBarHeight)

            val barTop = (h - barH) / 2f
            val barBottom = barTop + barH

            barRect.set(startX, barTop, startX + barWidth, barBottom)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaints[i])

            startX += barWidth + spacing
        }
    }
}
