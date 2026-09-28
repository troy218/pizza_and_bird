package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * 포스트프로세싱 파이프라인 — 블룸 · 비네트 · 필름그레인 · 색보정 · 색수차 · 샤프닝 · 스캔라인.
 *
 * PR #130의 8K 렌더링 설계를 잇는다. RenderScript(GPU 가속) 대신 CPU 픽셀 연산으로 구현한다 —
 * 이 효과들은 저해상도 픽셀아트 위 소규모 연산이라 CPU로 충분하고, RenderScript는 API 31+에서
 * 폐기 예정이라 호환성 함정이다. [enableBloom]이 꺼져 있거나 [applyBloom]의 worldScale이 2 미만이면
 * 블룸은 건너뛴다.
 */

/** 블룸 추출 임계값 — 이보다 밝은 픽셀만 빛나게 퍼진다 (PR #130: 0.85). */
const val BLOOM_THRESHOLD = 0.85f

/** 블룸 기본 강도 (PR #130: 0.35). */
const val BLOOM_INTENSITY = 0.35f

/** 블룸 블러 반경 기준(월드 스케일당 px). */
const val BLOOM_BASE_RADIUS = 4

/** 비네트(모서리 어둡힘) 기본 강도 (PR #130: 0.25). */
const val VIGNETTE_INTENSITY = 0.25f

/** 필름그레인 기본 세기 (PR #130: 0.02). */
const val FILM_GRAIN_AMOUNT = 0.02f

/** 색수차 채널 분리폭 — 최소 변에 비한 비율 (PR #130: 0.008). */
const val CHROMATIC_ABERRATION_STRENGTH = 0.008f

/** 블룸 전체 스위치. */
var enableBloom: Boolean = true

/** 색보정 기준이 되는 시간대. */
enum class TimeOfDay { DAWN, DAY, DUSK, NIGHT }

class PostProcessing {

    // ── Bloom ─────────────────────────────────────────────────────────────

    /**
     * 밝은 영역(하이라이트)을 추출해 흐리게 퍼뜨리고 다시 합성한다.
     * 픽셀아트 격자를 보존하려고 worldScale 정수 배에서만 켠다.
     */
    fun applyBloom(src: Bitmap, worldScale: Int, intensity: Float = BLOOM_INTENSITY): Bitmap {
        if (!enableBloom || worldScale < 2) return src
        val bright = extractBrightPixels(src, BLOOM_THRESHOLD, intensity)
        val radius = (BLOOM_BASE_RADIUS * worldScale).coerceIn(1, 8)
        return screenCompose(src, boxBlur(bright, radius))
    }

    /** 임계값보다 밝은 픽셀만 남기고 그 밝기에 비례한 광원 비트맵을 만든다. */
    private fun extractBrightPixels(src: Bitmap, threshold: Float, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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
                val excess = (lum - threshold) / (1f - threshold)
                val scale = (excess * intensity).coerceIn(0f, 1f)
                pixels[i] = Color.argb(
                    (a * scale).toInt().coerceIn(0, 255),
                    ((c shr 16) and 0xFF).toFloat().times(scale).toInt().coerceIn(0, 255),
                    ((c shr 8) and 0xFF).toFloat().times(scale).toInt().coerceIn(0, 255),
                    (b * scale * 255).toInt().coerceIn(0, 255)
                )
            } else {
                pixels[i] = 0
            }
        }
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    /** 합성(srcOver 아님): base와 glow를 screen 블렌드 — 절대 오버플로 나지 않는 블룸 합성. */
    private fun screenCompose(base: Bitmap, glow: Bitmap): Bitmap {
        val w = base.width
        val h = base.height
        val bp = IntArray(w * h); base.getPixels(bp, 0, w, 0, 0, w, h)
        val gp = IntArray(w * h); glow.getPixels(gp, 0, w, 0, 0, w, h)
        for (i in bp.indices) {
            val ga = (gp[i] ushr 24) and 0xFF
            if (ga == 0) continue
            val c = bp[i]
            val sr = (c shr 16) and 0xFF
            val sg = (c shr 8) and 0xFF
            val sb = c and 0xFF
            val gr = (gp[i] shr 16) and 0xFF
            val gg = (gp[i] shr 8) and 0xFF
            val gb = gp[i] and 0xFF
            bp[i] = Color.argb(
                max((c ushr 24) and 0xFF, ga),
                255 - (255 - sr) * (255 - gr) / 255,
                255 - (255 - sg) * (255 - gg) / 255,
                255 - (255 - sb) * (255 - gb) / 255
            )
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(bp, 0, w, 0, 0, w, h)
        return out
    }

    /** 박스 블러 — 가로·세로 두 패스(경계는 가장자리 픽셀로 클램프). radius<=0이면 원본 반환. */
    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val inp = IntArray(w * h)
        src.getPixels(inp, 0, w, 0, 0, w, h)

        val hor = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var ar = 0L; var ag = 0L; var ab = 0L; var aa = 0L
                for (dx in -radius..radius) {
                    val xx = (x + dx).coerceIn(0, w - 1)
                    val c = inp[y * w + xx]
                    aa += ((c ushr 24) and 0xFF).toLong()
                    ar += ((c shr 16) and 0xFF).toLong()
                    ag += ((c shr 8) and 0xFF).toLong()
                    ab += (c and 0xFF).toLong()
                }
                val n = (radius * 2 + 1).toLong()
                hor[y * w + x] = Color.argb((aa / n).toInt(), (ar / n).toInt(), (ag / n).toInt(), (ab / n).toInt())
            }
        }

        val res = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var ar = 0L; var ag = 0L; var ab = 0L; var aa = 0L
                for (dy in -radius..radius) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    val c = hor[yy * w + x]
                    aa += ((c ushr 24) and 0xFF).toLong()
                    ar += ((c shr 16) and 0xFF).toLong()
                    ag += ((c shr 8) and 0xFF).toLong()
                    ab += (c and 0xFF).toLong()
                }
                val n = (radius * 2 + 1).toLong()
                res[y * w + x] = Color.argb((aa / n).toInt(), (ar / n).toInt(), (ag / n).toInt(), (ab / n).toInt())
            }
        }

        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(res, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Vignette ──────────────────────────────────────────────────────────

    /** 모서리를 어둡게 깔아 시선을 중앙으로 모은다. */
    fun applyVignette(src: Bitmap, intensity: Float = VIGNETTE_INTENSITY): Bitmap {
        if (intensity <= 0f) return src
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(dst)
        cv.drawBitmap(src, 0f, 0f, null)

        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = kotlin.math.hypot(cx, cy)
        val edge = (140 * intensity).toInt().coerceIn(0, 255)
        val mid = (70 * intensity).toInt().coerceIn(0, 255)
        val vignettePaint = Paint().apply {
            shader = RadialGradient(
                cx, cy, maxRadius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(mid, 18, 14, 26),
                    Color.argb(edge, 18, 14, 26)
                ),
                floatArrayOf(0f, 0.62f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
        return dst
    }

    // ── Film Grain ────────────────────────────────────────────────────────

    /** 미세한 필름 그레인을 더한다 — 픽셀마다 같은 폭의 노이즈. */
    fun applyFilmGrain(src: Bitmap, amount: Float = FILM_GRAIN_AMOUNT): Bitmap {
        if (amount <= 0f) return src
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val random = java.util.Random(0x5EED)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            val grain = (random.nextFloat() - 0.5f) * 2f * amount * 255f
            val r = ((c shr 16) and 0xFF) + grain
            val g = ((c shr 8) and 0xFF) + grain
            val b = (c and 0xFF) + grain
            pixels[i] = Color.argb(
                a,
                r.toInt().coerceIn(0, 255),
                g.toInt().coerceIn(0, 255),
                b.toInt().coerceIn(0, 255)
            )
        }
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Sharpen (Unsharp Mask) ────────────────────────────────────────────

    /** 언차프 마스크 — 원본에서 블러를 뺀 차이를 amount배 더해 윤곽을 또렷하게. */
    fun applySharpen(src: Bitmap, worldScale: Int, amount: Float = 0.5f): Bitmap {
        if (worldScale < 2 || amount <= 0f) return src
        val w = src.width
        val h = src.height
        val blur = boxBlur(src, 1)
        val sp = IntArray(w * h); src.getPixels(sp, 0, w, 0, 0, w, h)
        val bp = IntArray(w * h); blur.getPixels(bp, 0, w, 0, 0, w, h)
        for (i in sp.indices) {
            val c = sp[i]
            val bc = bp[i]
            val sr = ((c shr 16) and 0xFF) + amount * (((c shr 16) and 0xFF) - ((bc shr 16) and 0xFF))
            val sg = ((c shr 8) and 0xFF) + amount * (((c shr 8) and 0xFF) - ((bc shr 8) and 0xFF))
            val sb = (c and 0xFF) + amount * ((c and 0xFF) - (bc and 0xFF))
            sp[i] = Color.argb(
                (c ushr 24) and 0xFF,
                sr.toInt().coerceIn(0, 255),
                sg.toInt().coerceIn(0, 255),
                sb.toInt().coerceIn(0, 255)
            )
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(sp, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Scanlines (CRT feel) ──────────────────────────────────────────────

    /** 2px 간격의 옅은 가로선 — 레트로 CRT 느낌. */
    fun applyScanlines(src: Bitmap, intensity: Float = 0.08f): Bitmap {
        if (intensity <= 0f) return src
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(dst)
        cv.drawBitmap(src, 0f, 0f, null)

        val linePaint = Paint().apply {
            color = Color.argb((32 * intensity).toInt().coerceIn(0, 255), 0, 0, 0)
        }
        var y = 0
        while (y < h) {
            cv.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), linePaint)
            y += 2
        }
        return dst
    }

    // ── Color Grading (시간대 보정) ───────────────────────────────────────

    /** 시간대별 채널 게인 — 새벽 따뜻함 · 해질녘 주황 · 밤 차가운 청색. */
    fun applyColorGrading(src: Bitmap, timeOfDay: TimeOfDay): Bitmap {
        val gain = when (timeOfDay) {
            TimeOfDay.DAWN -> floatArrayOf(1.05f, 0.99f, 0.94f)
            TimeOfDay.DUSK -> floatArrayOf(1.09f, 1.00f, 0.88f)
            TimeOfDay.NIGHT -> floatArrayOf(0.84f, 0.90f, 1.16f)
            TimeOfDay.DAY -> return src
        }
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            pixels[i] = Color.argb(
                a,
                ((c shr 16) and 0xFF).times(gain[0]).toInt().coerceIn(0, 255),
                ((c shr 8) and 0xFF).times(gain[1]).toInt().coerceIn(0, 255),
                (c and 0xFF).times(gain[2]).toInt().coerceIn(0, 255)
            )
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Chromatic Aberration ──────────────────────────────────────────────

    /** 빨강은 오른쪽·파랑은 왼쪽으로 미세하게 밀어 렌즈 색수차 흉내. */
    fun applyChromaticAberration(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val k = (min(w, h) * CHROMATIC_ABERRATION_STRENGTH).toInt().coerceIn(1, 8)
        val sp = IntArray(w * h)
        src.getPixels(sp, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val xr = (x + k).coerceIn(0, w - 1)
                val xb = (x - k).coerceIn(0, w - 1)
                val c = sp[row + x]
                out[row + x] = Color.argb(
                    (c ushr 24) and 0xFF,
                    (sp[row + xr] shr 16) and 0xFF,
                    (c shr 8) and 0xFF,
                    sp[row + xb] and 0xFF
                )
            }
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(out, 0, w, 0, 0, w, h)
        return dst
    }

    // ── Composite ─────────────────────────────────────────────────────────

    /** 포스트프로세싱 토글과 강도 — [process]에 넘긴다. */
    data class PostSettings(
        var bloom: Boolean = true,
        var vignette: Boolean = true,
        var enableColorGrading: Boolean = true,
        var enableFilmGrain: Boolean = false,
        var enableChromaticAberration: Boolean = false,
        var bloomIntensity: Float = BLOOM_INTENSITY,
        var vignetteIntensity: Float = VIGNETTE_INTENSITY,
        var filmGrainAmount: Float = FILM_GRAIN_AMOUNT,
    )

    /** 설정에 따라 모든 효과를 순서대로 적용한다. worldScale < 2에서는 블룸·샤프닝 생략. */
    fun process(
        src: Bitmap,
        worldScale: Float,
        timeOfDay: TimeOfDay = TimeOfDay.DAY,
        settings: PostSettings = PostSettings()
    ): Bitmap {
        var bmp = src
        if (settings.bloom && enableBloom && worldScale >= 2) {
            bmp = applyBloom(bmp, worldScale.toInt(), settings.bloomIntensity)
        }
        if (settings.vignette) {
            bmp = applyVignette(bmp, settings.vignetteIntensity)
        }
        if (settings.enableColorGrading) {
            bmp = applyColorGrading(bmp, timeOfDay)
        }
        if (settings.enableFilmGrain) {
            bmp = applyFilmGrain(bmp, settings.filmGrainAmount)
        }
        if (settings.enableChromaticAberration) {
            bmp = applyChromaticAberration(bmp)
        }
        return bmp
    }
}
