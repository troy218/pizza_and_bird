package com.pizzaandbird.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/**
 * 부팅 스플래시 — 게임 자산이 준비될 때까지 즉시 보여주는 아주 가벼운 화면.
 *
 * 예전에는 앱을 켜면 Game/Assets 생성이 UI 스레드에서 돌아 화면이
 * 한참 멈춰 있었는데, 이제 이 화면이 먼저 뜨고 초기화는 백그라운드로 간다.
 *
 * 타이틀 화면과 같은 하늘·언덕·잔디 구도를 사용(시스템 폰트만 — Type.init
 * 전이라 자산 폰트는 못 씀)해서 게임으로 넘어가는 순간이 자연스럽게 이어진다.
 */
class BootView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    /** 타이틀 화면과 같은 하늘 그라데이션 (24단계 보간) */
    private val bands: IntArray = run {
        val anchors = intArrayOf(
            0xFF4FB3E4.toInt(), 0xFF9BDCF2.toInt(), 0xFFCDEFF0.toInt(), 0xFFF2FADE.toInt()
        )
        val stops = floatArrayOf(0f, 0.45f, 0.78f, 1f)
        val out = IntArray(24)
        for (i in out.indices) {
            val u = i / (out.size - 1).toFloat()
            var s = 1
            while (s < stops.size - 1 && u > stops[s]) s++
            val k = ((u - stops[s - 1]) / (stops[s] - stops[s - 1])).coerceIn(0f, 1f)
            val a = anchors[s - 1]
            val b = anchors[s]
            out[i] = android.graphics.Color.rgb(
                (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * k).toInt(),
                (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * k).toInt(),
                (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * k).toInt()
            )
        }
        out
    }
    private val bandPaint = Paint()
    private val sunFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7EDB8.toInt() }
    private val sunGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
    private val farCloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x6AD6ECF4 }
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x965A505A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
    }
    private val hillFar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8FCF8E.toInt() }
    private val hillNear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF79BC78.toInt() }
    private val grassPaint = Paint().apply { color = 0xFF7CB878.toInt() }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A3728.toInt()
        isFakeBoldText = true
    }
    private val koreanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB5651D.toInt()
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dots = arrayOf("·  ", "·· ", "···", " ·  ")
    private var t0 = 0L

    override fun onDraw(c: Canvas) {
        if (t0 == 0L) t0 = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        val d = density
        val now = (SystemClock.uptimeMillis() - t0) / 1000f

        // 하늘 그라데이션 밴드 (타이틀 화면과 같은 9단계)
        val bandH = h / bands.size
        for (i in bands.indices) {
            bandPaint.color = bands[i]
            c.drawRect(0f, i * bandH, w, (i + 1) * bandH + 1f, bandPaint)
        }

        // 햇살 (은은한 헤일 3단)
        val sx = w * 0.86f
        val sy = h * 0.15f
        sunGlow.color = 0x1AF8F0BE
        c.drawCircle(sx, sy, d * 58f, sunGlow)
        sunGlow.color = 0x29F8F0BE
        c.drawCircle(sx, sy, d * 42f, sunGlow)
        c.drawCircle(sx, sy, d * 30f, sunFill)
        sunGlow.color = 0xFFFFFBE0.toInt()
        c.drawCircle(sx, sy, d * 22f, sunGlow)

        // 구름 — 천천히 왼쪽으로 이동 (두 겹)
        val span = w + d * 120f
        cloudPaint.color = 0xE6FFFFFF.toInt()
        drawCloud(c, cloudPaint, (now * d * 8f) % span - d * 100f, h * 0.14f, d * 1.1f)
        drawCloud(c, cloudPaint, (now * d * 4f + span * 0.4f) % span - d * 100f, h * 0.26f, d * 0.8f)
        farCloudPaint.color = 0x6AD6ECF4.toInt()
        drawCloud(c, farCloudPaint, (now * d * 3f + span * 0.7f) % span - d * 100f, h * 0.42f, d * 1.7f)

        // 새 실루엣 3마리
        for (i in 0 until 3) {
            val bx = ((now * d * 20f + i * d * 160f) % span) - d * 60f
            val by = h * (0.12f + i * 0.07f) + Math.sin((now * 2f + i).toDouble()).toFloat() * d * 6f
            birdPaint.strokeWidth = 2.2f * d
            c.drawLine(bx, by, bx + 8f * d, by - 3.5f * d, birdPaint)
            c.drawLine(bx + 8f * d, by - 3.5f * d, bx + 16f * d, by, birdPaint)
        }

        // 언덕 + 잔디 (하단)
        val grassTop = h * 0.82f
        c.drawCircle(w * 0.2f, grassTop + h * 0.14f, w * 0.24f, hillFar)
        c.drawCircle(w * 0.62f, grassTop + h * 0.16f, w * 0.26f, hillFar)
        c.drawCircle(w * 0.95f, grassTop + h * 0.13f, w * 0.22f, hillNear)
        c.drawCircle(w * 0.38f, grassTop + h * 0.17f, w * 0.2f, hillNear)
        c.drawRect(0f, grassTop, w, h, grassPaint)

        // 로고 — Type.init 전이라 시스템 폰트로 가볍게 (자간은 수동으로)
        val cx = w / 2f
        titlePaint.textSize = d * 21f
        drawTracked(c, "PIZZA and BIRD", cx, h * 0.34f, titlePaint, 3f * d)
        koreanPaint.textSize = d * 34f
        c.drawText("피자와 새", cx - koreanPaint.measureText("피자와 새") / 2f, h * 0.445f, koreanPaint)

        // 불러오는 중...
        val step = ((SystemClock.uptimeMillis() - t0) / 380 % 4).toInt()
        subPaint.textSize = d * 13f
        subPaint.color = 0xB84A3728.toInt()
        val sub = "불러오는 중" + dots[step]
        c.drawText(sub, cx - subPaint.measureText(sub) / 2f, h * 0.53f, subPaint)

        if (isAttachedToWindow) postInvalidateDelayed(100)
    }

    private fun drawCloud(c: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, 9f * s, p)
        c.drawCircle(x + 10f * s, y - 3f * s, 11f * s, p)
        c.drawCircle(x + 22f * s, y, 8f * s, p)
        c.drawRect(x - 8f * s, y, x + 28f * s, y + 9f * s, p)
    }

    /** 가운데 정렬 + 수동 자간 (Canvas 에 letterSpacing 가 없는 저버전 호환). */
    private fun drawTracked(c: Canvas, s: String, cx: Float, y: Float, p: Paint, extra: Float) {
        val total = p.measureText(s) + extra * (s.length - 1)
        var x = cx - total / 2f
        for (ch in s) {
            c.drawText(ch.toString(), x, y, p)
            x += p.measureText(ch.toString()) + extra
        }
    }
}
