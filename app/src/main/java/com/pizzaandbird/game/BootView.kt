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
 * 타이틀 화면과 같은 하늘색이라 게임으로 넘어갈 때도 자연스럽게 이어진다.
 */
class BootView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private val bands = intArrayOf(
        0xFF7FD4E8.toInt(), 0xFF8FDCEA.toInt(), 0xFFA4E4EE.toInt(),
        0xFFBCEAF0.toInt(), 0xFFD4F2EC.toInt()
    )
    private val bandPaint = Paint()
    private val sunFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7EDB8.toInt() }
    private val sunGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A3728.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dots = arrayOf("·  ", "·· ", "···", " ·  ")
    private var t0 = 0L

    override fun onDraw(c: Canvas) {
        if (t0 == 0L) t0 = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        val d = density

        // 타이틀 화면과 같은 하늘 그라데이션 밴드
        val bandH = h / bands.size
        for (i in bands.indices) {
            bandPaint.color = bands[i]
            c.drawRect(0f, i * bandH, w, (i + 1) * bandH + 1f, bandPaint)
        }

        // 햇살
        val step = ((SystemClock.uptimeMillis() - t0) / 380 % 4).toInt()
        val cx = w * 0.89f
        val cy = h * 0.16f
        sunGlow.color = 0x50F7EDB8.toInt()
        c.drawCircle(cx, cy, d * 44f, sunGlow)
        c.drawCircle(cx, cy, d * 30f, sunFill)

        // 로고 — Type.init 전이라 시스템 폰트로 가볍게
        titlePaint.textSize = d * 30f
        c.drawText("피자와 새", w / 2f, h * 0.42f, titlePaint)

        subPaint.textSize = d * 13f
        subPaint.color = 0xE04A3728.toInt()
        c.drawText("불러오는 중" + dots[step], w / 2f, h * 0.42f + d * 28f, subPaint)

        if (isAttachedToWindow) postInvalidateDelayed(100)
    }
}
