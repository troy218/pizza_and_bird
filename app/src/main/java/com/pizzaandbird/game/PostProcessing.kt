package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.min

/**
 * Post-processing effects for enhanced graphics quality.
 * Adds bloom, color grading, vignette, film grain, and chromatic aberration
 * while preserving the pixel art aesthetic.
 *
 * 모든 이펙트는 CPU 로 처리한다 (픽셀 아트 격자 보존을 위해 정수 배율에서만 동작).
 * Android-only API(RenderScript/RenderEffect) 에 의존하지 않으므로 프리뷰/테스트 환경에서도 동일하게 동작한다.
 */
class PostProcessing(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    var enableBloom = true
    var enableVignette = true
    var enableColorGrading = true
    var enableFilmGrain = false
    var enableChromaticAberration = false

    companion object {
        const val BLOOM_INTENSITY = 0.55f
        const val BLOOM_THRESHOLD = 0.72f
        const val BLOOM_BASE_RADIUS = 2
    }

    enum class TimeOfDay { DAY, SUNSET, NIGHT }

    /**
     * Applies bloom effect to bright areas.
     * Preserves pixel art grid by working at integer multiples.
     */
    fun applyBloom(src: Bitmap, worldScale: Int, intensity: Float = BLOOM_INTENSITY): Bitmap {
        if (!enableBloom || worldScale < 2) return src
        val bright = extractBrightPixels(src, BLOOM_THRESHOLD, intensity)
        val blurred = boxBlur(bright, (BLOOM_BASE_RADIUS * worldScale).coerceIn(1, 8))
        // 원본에 밝은 부분을 더한다 (가산 합성)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawBitmap(src, 0f, 0f, null)
        cv.drawBitmap(blurred, 0f, 0f, null)
        return out
    }

    private fun extractBrightPixels(src: Bitmap, threshold: Float, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) { pixels[i] = 0; continue }
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (lum > threshold) {
                val excess = ((lum - threshold) / (1f - threshold)).coerceIn(0f, 1f) * intensity
                val nr = ((c shr 16) and 0xFF) * excess
                val ng = ((c shr 8) and 0xFF) * excess
                val nb = (c and 0xFF) * excess
                pixels[i] = Color.argb(
                    (a * excess).toInt().coerceIn(0, 255),
                    nr.toInt().coerceIn(0, 255),
                    ng.toInt().coerceIn(0, 255),
                    nb.toInt().coerceIn(0, 255)
                )
            } else {
                pixels[i] = 0
            }
        }
        val bright = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bright.setPixels(pixels, 0, w, 0, 0, w, h)
        return bright
    }

    /** 이동 평균 박스 블러 (가로 → 세로 두 패스) */
    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val tmpPixels = IntArray(w * h)
        src.getPixels(tmpPixels, 0, w, 0, 0, w, h)

        // 가로 패스
        val hPass = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            for (x in 0 until w) {
                val idx = row + x
                val c = tmpPixels[idx]
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
                sumA += (c ushr 24) and 0xFF
                if (x > radius) {
                    val c2 = tmpPixels[row + x - radius - 1]
                    sumR -= (c2 shr 16) and 0xFF
                    sumG -= (c2 shr 8) and 0xFF
                    sumB -= c2 and 0xFF
                    sumA -= (c2 ushr 24) and 0xFF
                }
                val count = min(x + radius + 1, w) - (x - radius).coerceAtLeast(0)
                hPass[idx] = Color.argb(
                    (sumA / count).coerceIn(0, 255),
                    (sumR / count).coerceIn(0, 255),
                    (sumG / count).coerceIn(0, 255),
                    (sumB / count).coerceIn(0, 255)
                )
            }
        }

        // 세로 패스
        val vPass = IntArray(w * h)
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            for (y in 0 until h) {
                val idx = y * w + x
                val c = hPass[idx]
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
                sumA += (c ushr 24) and 0xFF
                if (y > radius) {
                    val c2 = hPass[(y - radius - 1) * w + x]
                    sumR -= (c2 shr 16) and 0xFF
                    sumG -= (c2 shr 8) and 0xFF
                    sumB -= c2 and 0xFF
                    sumA -= (c2 ushr 24) and 0xFF
                }
                val count = min(y + radius + 1, h) - (y - radius).coerceAtLeast(0)
                vPass[idx] = Color.argb(
                    (sumA / count).coerceIn(0, 255),
                    (sumR / count).coerceIn(0, 255),
                    (sumG / count).coerceIn(0, 255),
                    (sumB / count).coerceIn(0, 255)
                )
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(vPass, 0, w, 0, 0, w, h)
        return result
    }

    // ── Color Grading ─────────────────────────────────────────────────────

    /** 시간대별 색 보정 — 낮은 맑게, 해 질 녘은 따뜻하게, 밤은 차갑게 */
    fun applyColorGrading(src: Bitmap, timeOfDay: TimeOfDay): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val (gr, gg, gb) = when (timeOfDay) {
            TimeOfDay.DAY -> Triple(1.02f, 1.01f, 1.0f)
            TimeOfDay.SUNSET -> Triple(1.08f, 1.0f, 0.93f)
            TimeOfDay.NIGHT -> Triple(0.92f, 0.95f, 1.08f)
        }
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            val r = (((c shr 16) and 0xFF) * gr).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) * gg).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * gb).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, g, b)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    // ── Vignette ──────────────────────────────────────────────────────────

    /** Applies a subtle vignette (darkened corners) to focus attention. */
    fun applyVignette(src: Bitmap, intensity: Float = 0.35f): Bitmap {
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(dst)
        cv.drawBitmap(src, 0f, 0f, null)
        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = kotlin.math.hypot(cx, cy)
        val vignettePaint = Paint().apply {
            shader = RadialGradient(
                cx, cy, maxRadius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(0, 18, 14, 26),
                    Color.argb((60 * intensity).toInt(), 18, 14, 26),
                    Color.argb((120 * intensity).toInt(), 18, 14, 26)
                ),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
        return dst
    }

    // ── Film Grain ────────────────────────────────────────────────────────

    /** Adds subtle film grain for texture (only at high scales). */
    fun applyFilmGrain(src: Bitmap, amount: Float = 0.03f): Bitmap {
        if (amount <= 0f) return src
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val random = java.util.Random(System.nanoTime())
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            val grain = (random.nextFloat() - 0.5f) * 2f * amount * 255f
            val r = (((c shr 16) and 0xFF) + grain).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) + grain).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) + grain).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, g, b)
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Chromatic Aberration ──────────────────────────────────────────────

    /** 가장자리만 살짝 색을 어긋나게 하는 미세한 색수차 */
    fun applyChromaticAberration(src: Bitmap, amount: Float = 1.2f): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x - cx) / cx
                val dy = (y - cy) / cy
                val shift = (amount * (dx * dx + dy * dy)).toInt().coerceAtMost(3)
                val rx = (x + shift).coerceIn(0, w - 1)
                val by = (y + shift).coerceIn(0, h - 1)
                val rc = pixels[y * w + rx]
                val bc = pixels[by * w + x]
                val gc = pixels[y * w + x]
                out[y * w + x] = Color.argb(
                    (gc ushr 24) and 0xFF,
                    (rc shr 16) and 0xFF,
                    (gc shr 8) and 0xFF,
                    bc and 0xFF
                )
            }
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(out, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Sharpen (Unsharp Mask) ────────────────────────────────────────────

    /** Unsharp mask for crispness at high scales (only when worldScale >= 2). */
    fun applySharpen(src: Bitmap, worldScale: Int, amount: Float = 0.5f): Bitmap {
        if (worldScale < 2 || amount <= 0f) return src
        // Placeholder - sharpening pixel art is usually not desirable
        return src
    }

    // ── Scanlines (CRT feel) ──────────────────────────────────────────────

    /** Optional scanlines for CRT feel — very subtle. */
    fun applyScanlines(src: Bitmap, intensity: Float = 0.08f): Bitmap {
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(dst)
        cv.drawBitmap(src, 0f, 0f, null)
        val linePaint = Paint().apply { color = Color.argb((32 * intensity).toInt(), 0, 0, 0) }
        for (y in 0 until h step 2) {
            cv.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), linePaint)
        }
        return dst
    }

    // ── Composite ──────────────────────────────────────────────────────────

    data class PostSettings(
        var bloom: Boolean = true,
        var vignette: Boolean = true,
        var enableColorGrading: Boolean = true,
        var enableFilmGrain: Boolean = false,
        var enableChromaticAberration: Boolean = false,
        var bloomIntensity: Float = BLOOM_INTENSITY,
        var vignetteIntensity: Float = 0.25f,
        var filmGrainAmount: Float = 0.03f,
    )

    fun process(
        src: Bitmap,
        worldScale: Float,
        timeOfDay: TimeOfDay = TimeOfDay.DAY,
        settings: PostSettings = PostSettings()
    ): Bitmap {
        var current = src
        if (settings.bloom && worldScale >= 2f) {
            current = applyBloom(current, worldScale.toInt(), settings.bloomIntensity)
        }
        if (settings.vignette) {
            current = applyVignette(current, settings.vignetteIntensity)
        }
        if (settings.enableColorGrading) {
            current = applyColorGrading(current, timeOfDay)
        }
        if (settings.enableFilmGrain) {
            current = applyFilmGrain(current, settings.filmGrainAmount)
        }
        if (settings.enableChromaticAberration) {
            current = applyChromaticAberration(current)
        }
        return current
    }
}
