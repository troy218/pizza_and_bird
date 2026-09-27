package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin

/**
 * 화면 전체의 미세한 색보정과 시간대별 광선을 담당하는 후처리 패스.
 * 스프라이트의 픽셀은 건드리지 않고, 저채도 오버레이만 얹어 픽셀 아트의 선명함을 보존한다.
 */
class CinematicAtmosphere {
    private val wash = Paint()
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rays = Array(4) { Path() }
    private var shaderW = 0
    private var shaderH = 0

    fun draw(canvas: Canvas, width: Float, height: Float, hour: Float, weather: Weather, time: Float) {
        val dawn = if (hour in 5.5f..7.5f) (1f - abs(hour - 6.5f)).coerceIn(0f, 1f) else 0f
        val dusk = if (hour in 17f..19f) (1f - abs(hour - 18f)).coerceIn(0f, 1f) else 0f
        val golden = maxOf(dawn, dusk)

        // 따뜻한 역광, 비 오는 날의 청회색, 눈 내리는 날의 차가운 공기.
        // 낮에는 색보정을 거의 하지 않아 타일 원색을 살린다.
        val (r, g, b, strength) = when {
            golden > 0.04f -> Quad(255, if (dusk > dawn) 132 else 184, 106, (8f + 13f * golden).toInt())
            weather == Weather.RAIN -> Quad(102, 145, 190, 12)
            weather == Weather.SNOW -> Quad(178, 207, 236, 7)
            weather == Weather.CLOUDY -> Quad(155, 173, 196, 5)
            else -> Quad(255, 244, 218, 3)
        }
        wash.color = Color.argb(strength, r, g, b)
        canvas.drawRect(0f, 0f, width, height, wash)

        if (golden <= 0.08f) return
        ensureShader(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))

        // 해가 낮게 걸리는 시간대에만, 화면 위쪽에서 비스듬히 들어오는 부드러운 빛줄기.
        // 네 개의 넓은 띠를 낮은 알파로 겹쳐 픽셀 풍경에 깊이와 장면성을 더한다.
        val fromLeft = dawn >= dusk
        val sourceX = if (fromLeft) width * 0.08f else width * 0.92f
        val drift = sin(time * 0.22f) * width * 0.018f
        val sign = if (fromLeft) 1f else -1f
        rayPaint.shader = rayShader
        rayPaint.alpha = (golden * 220f).toInt().coerceIn(0, 220)
        for (i in rays.indices) {
            val spread = (i - 1.5f) * width * 0.12f
            val start = sourceX + spread * 0.28f + drift
            val end = start + sign * (width * (0.22f + i * 0.075f))
            val beam = width * (0.045f + i * 0.008f)
            val path = rays[i]
            path.reset()
            path.moveTo(start, -height * 0.08f)
            path.lineTo(start + sign * beam * 0.36f, -height * 0.08f)
            path.lineTo(end + beam, height * 1.08f)
            path.lineTo(end - beam, height * 1.08f)
            path.close()
            canvas.drawPath(path, rayPaint)
        }
        rayPaint.shader = null
    }

    private var rayShader: LinearGradient? = null

    private fun ensureShader(w: Int, h: Int) {
        if (w == shaderW && h == shaderH && rayShader != null) return
        shaderW = w
        shaderH = h
        rayShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.argb(58, 255, 231, 178),
                Color.argb(28, 255, 207, 143),
                Color.argb(0, 255, 196, 128)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private data class Quad(val r: Int, val g: Int, val b: Int, val a: Int)
}
