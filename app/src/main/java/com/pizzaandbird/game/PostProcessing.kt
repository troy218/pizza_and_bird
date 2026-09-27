package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.renderscript.Allocation
import android.graphics.RenderEffect
import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.renderscript.RenderScript
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.Type
import android.content.Context
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Post-processing effects for enhanced graphics quality.
 * Adds bloom, color grading, vignette, film grain, and chromatic aberration
 * while preserving the pixel art aesthetic.
 */
class PostProcessing(private val context: Context) {

    private var rs: RenderScript? = null
    private var blurScript: ScriptIntrinsicBlur? = null
    private var inputAllocation: Allocation? = null
    private var outputAllocation: Allocation? = null

    init {
        try {
            rs = RenderScript.create(context)
            blurScript = ScriptIntrinsicBlur.create(rs!!, Element.U8_4(rs!!))
        } catch (e: Exception) {
            // RenderScript not available, fallback to CPU blur
        }
    }

    /**
     * Applies bloom effect to bright areas.
     * Preserves pixel art grid by working at integer multiples.
     */
    fun applyBloom(src: Bitmap, worldScale: Int, intensity: Float = BLOOM_INTENSITY): Bitmap {
        if (!enableBloom || worldScale < 2) return src

        val w = src.width
        val h = src.height
        val radius = (4 * worldScale).coerceIn(2, 16)

        // Use RenderScript for efficient blur if available
        if (rs != null && blurScript != null) {
            return applyBloomRS(src, radius)
        }

        // Fallback: simple bright pixel extraction + box blur
        return applyBloomCPU(src, worldScale)
    }

    private fun extractBrightPixels(src: Bitmap, threshold: Float, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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

            if (luminance > BLOOM_THRESHOLD) {
                val excess = (luminance - BLOOM_THRESHOLD) / (1f - BLOOM_THRESHOLD)
                val scale = (excess * BLOOM_INTENSITY).coerceIn(0f, 1f)
                val nr = ((c shr 16) and 0xFF).toFloat() * scale
                val ng = ((c shr 8) and 0xFF) * scale
                val nb = (b * scale * 255).toInt()
                pixels[i] = Color.argb((a * scale).toInt().coerceIn(0, 255), nr, ng, nb)
            } else {
                pixels[i] = 0
            }
        }
        val brightBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bright.setPixels(pixels, 0, w, 0, 0, w, h)

        // Blur the bright pixels
        return boxBlur(bright, (BLOOM_BASE_RADIUS * worldScale).toInt().coerceIn(1, 8))
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tmpPixels = IntArray(w * h)
        src.getPixels(tmp, 0, w, 0, 0, w, h)

        // Horizontal pass
        for (y in 0 until h) {
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            val rowStart = y * w
            for (x in 0 until w) {
                val idx = rowStart + x
                val c = tmp[idx]
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
                sumA += (c ushr 24) and 0xFF

                val removeX = x - radius - 1
                if (x > radius) {
                    val c2 = tmp[leftIdx]
                    sumR -= (c2 shr 16) and 0xFF
                    sumG -= (c2 shr 8) and 0xFF
                    sumB -= c2 and 0xFF
                    sumA -= (c2 ushr 24) and 0xFF
                }
                val count = min(x + radius + 1, w) - (x - radius).coerceAtLeast(0)
                val outIdx = rowStart + x
                dst[idx] = Color.argb(
                    (sumA / count).coerceIn(0, 255),
                    (sumR / count).coerceIn(0, 255),
                    (sumG / count).coerceIn(0, 255),
                    (sumB / count).coerceIn(0, 255)
                )
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            for (y in 0 until h) {
                val idx = y * w + x
                val c = dst[idx]
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
                sumA += (c ushr 24) and 0xFF

                val upIdx = (y - radius - 1).coerceIn(0, h - 1) * w + x
                if (y > radius) {
                    val c2 = dst[upIdx]
                    sumR -= (c2 shr 16) and 0xFF
                    sumG -= (c2 shr 8) and 0xFF
                    sumB -= c2 and 0xFF
                    sumA -= (c2 ushr 24) and 0xFF
                }

                val count = min(y + radius + 1, h) - (y - radius).coerceAtLeast(0)
                val outIdx = y * w + x
                dst[idx] = Color.argb(
                    (sumA / count).coerceIn(0, 255),
                    (sumR / count).coerceIn(0, 255),
                    (sumG / count).coerceIn(0, 255),
                    (sumB / count).coerceIn(0, 255)
                )
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst2, 0, w, 0, 0, w, h)
        return result
    }

    // ── Vignette ──────────────────────────────────────────────────────────

    /** Applies a subtle vignette (darkened corners) to focus attention. */
    fun applyVignette(src: Bitmap, intensity: Float = 0.35f): Bitmap {
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(dst)
        val paint = Paint().apply { isAntiAlias = true }

        val cx = w / 2f
        val centerY = h / 2f
        val maxRadius = kotlin.math.hypot(centerX, centerY)
        val vignettePaint = Paint().apply {
            shader = android.graphics.RadialGradient(
                centerX, centerY, maxRadius,
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
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return dst
    }

    // ── Film Grain ────────────────────────────────────────────────────────

    /** Adds subtle film grain for texture (only at high scales). */
    fun applyFilmGrain(src: Bitmap, amount: Float = 0.03f): Bitmap {
        if (amount <= 0f) return src
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val random = java.util.Random(System.nanoTime())
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            val grain = (random.nextFloat() - 0.5f) * 2f * amount * 255f
            val r = (((c shr 16) and 0xFF) + grain).coerceIn(0, 255)
            val gr = (((c shr 8) and 0xFF) + grain).coerceIn(0, 255)
            val bl = ((c and 0xFF) + grain).coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, ng, nb)
        }
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Sharpen (Unsharp Mask) ────────────────────────────────────────────

    /** Unsharp mask for crispness at high scales (only when worldScale >= 2). */
    fun applySharpen(src: Bitmap, worldScale: Int, amount: Float = 0.5f): Bitmap {
        if (worldScale < 2 || amount <= 0f) return src
        // Unsharp mask: blur → subtract from original → add back
        // Simplified: just a light sharpen via convolution
        return src // Placeholder - implement if needed
    }

    // ── Scanlines (CRT feel) ──────────────────────────────────────────────

    /** Optional scanlines for CRT feel — very subtle. */
    fun applyScanlines(src: Bitmap, intensity: Float = 0.08f): Bitmap {
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(dst)
        val p = Paint()
        canvas.drawBitmap(src, 0f, 0f, paint)

        // Draw horizontal lines every 2px
        val linePaint = Paint().apply { color = Color.argb((32 * amount).toInt(), 0, 0, 0) }
        for (y in 0 until h step 2) {
            canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), paint)
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
        var bmp = src

        if (enableBloom && worldScale >= 2) {
            current = applyBloom(current, worldScale)
        }
        if (enableVignette) {
            current = applyVignette(current, 0.25f)
        }
        if (enableColorGrading) {
            current = applyColorGrading(current, timeOfDay)
        }
        if (enableFilmGrain) {
            current = applyFilmGrain(current)
        }
        if (enableChromaticAberration) {
            current = applyChromaticAberration(current)
        }
        return current
    }
}