package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Shared, subtle world-time color grading. The HUD is drawn separately and stays crisp. */
object WorldLighting {
    private val tint = Paint()

    fun draw(canvas: Canvas, playSeconds: Float, width: Int, height: Int) {
        val phase = ((playSeconds.coerceAtLeast(0f) % 240f) / 240f) * (Math.PI * 2.0).toFloat()
        val night = ((1f - cos(phase)) * 0.5f).coerceIn(0f, 1f)
        val nightAlpha = (night * 62f).toInt()
        if (nightAlpha > 0) {
            tint.color = Color.argb(nightAlpha, 19, 31, 67)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tint)
        }

        // Warm dawn/dusk tint, intentionally very restrained for readable pixel colors.
        val warmAlpha = (abs(sin(phase)) * 15f).toInt()
        if (warmAlpha > 0) {
            tint.color = Color.argb(warmAlpha, 255, 158, 94)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tint)
        }
    }
}
