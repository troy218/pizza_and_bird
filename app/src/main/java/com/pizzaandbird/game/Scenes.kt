package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * 씬 베이스. 오버레이(대화상자 등)를 하나 가질 수 있다.
 */
abstract class Scene(val game: Game) {
    var overlay: Overlay? = null

    open fun update(dt: Float) {}
    open fun drawWorld(c: Canvas) {}
    open fun drawHud(c: Canvas) {}
    open fun handleInput(input: Input) {}
    open fun onLayout() {}

    fun openOverlay(o: Overlay) {
        overlay = o
    }

    fun closeOverlay() {
        overlay = null
    }
}

/**
 * 타이틀 화면
 */
class TitleScene(game: Game) : Scene(game) {

    private var t = 0f
    private var startRect = RectF()
    private var contRect = RectF()
    private var exitRect = RectF()

    // 매 프레임 픽셀맵을 복사하지 않도록 플립 스프라이트 캐시
    private val birdFrames: Array<android.graphics.Bitmap> by lazy {
        arrayOf(game.assets.birdFlipped("sparrow", 0), game.assets.birdFlipped("sparrow", 1))
    }

    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        game.hud.questLabel = null
        game.hud.photoModeHint = false
        game.hud.regionLabel = ""
    }

    override fun update(dt: Float) {
        t += dt
        game.hud.update(dt)
    }

    override fun drawWorld(c: Canvas) {
        val p = Paint()
        // 하늘
        p.color = 0xFFA8E0D0.toInt()
        c.drawRect(0f, 0f, 480f, 270f, p)
        // 햇살
        p.color = 0xFFF7EDB8.toInt()
        c.drawCircle(430f, 40f, 22f, p)
        p.color = Color.argb(50, 247, 237, 184)
        c.drawCircle(430f, 40f, 30f, p)
        // 구름
        p.color = Color.argb(210, 255, 255, 255)
        drawCloud(c, p, 60f + (t * 6f) % 560f - 40f, 46f)
        drawCloud(c, p, 240f + (t * 3.5f) % 560f - 60f, 78f)
        drawCloud(c, p, 400f + (t * 4.5f) % 560f - 40f, 30f)
        // 언덕
        p.color = 0xFF8CCB8C.toInt()
        c.drawRect(0f, 190f, 480f, 270f, p)
        p.color = 0xFF7ABC7A.toInt()
        c.drawCircle(120f, 262f, 90f, p)
        c.drawCircle(360f, 268f, 110f, p)
        // 풀 디테일
        p.color = 0xFF6FAE6F.toInt()
        c.drawRect(0f, 226f, 480f, 270f, p)

        // 피자 & 새 로고
        val a = game.assets
        val bob = kotlin.math.sin(t * 2.2f) * 3f
        val pz = a.pizzaIconBig
        c.drawBitmap(pz, 196f, 176f + bob, a.sprPaint)
        val frame = ((t * 3f).toInt() % 2)
        val fb = kotlin.math.sin(t * 2.6f + 1f) * 4f
        c.drawBitmap(birdFrames[frame], 268f, 162f + fb, a.sprPaint)

        // 날아가는 새들
        p.color = Color.argb(140, 90, 80, 90)
        for (i in 0 until 3) {
            val bx = (t * 18f + i * 130f) % 560f - 40f
            val by = 60f + i * 22f + kotlin.math.sin(t * 2f + i) * 6f
            c.drawLine(bx, by, bx + 5f, by - 2f, p)
            c.drawLine(bx + 5f, by - 2f, bx + 10f, by, p)
        }
    }

    private fun drawCloud(c: Canvas, p: Paint, x: Float, y: Float) {
        c.drawCircle(x, y, 9f, p)
        c.drawCircle(x + 10f, y - 3f, 11f, p)
        c.drawCircle(x + 22f, y, 8f, p)
        c.drawRect(x - 8f, y, x + 28f, y + 9f, p)
    }

    override fun drawHud(c: Canvas) {
        fun dp(v: Float): Float = v * game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFakeBoldText = true
            textSize = dp(34f)
            color = 0xFF4A3728.toInt()
        }
        val shadow = Paint(titlePaint).apply { color = Color.argb(70, 74, 55, 40) }
        val cx = w / 2f

        val t1 = "PIZZA and BIRD"
        val t2 = "피자와 새"

        var ty = dp(46f)
        c.drawText(t1, cx - titlePaint.measureText(t1) / 2 + dp(2f), ty + dp(2f), shadow)
        c.drawText(t1, cx - titlePaint.measureText(t1) / 2, ty, titlePaint)

        titlePaint.textSize = dp(20f)
        titlePaint.color = 0xFF6B4F35.toInt()
        ty += dp(30f)
        c.drawText(t2, cx - titlePaint.measureText(t2) / 2 + dp(1.5f), ty + dp(1.5f), shadow)
        c.drawText(t2, cx - titlePaint.measureText(t2) / 2, ty, titlePaint)

        titlePaint.textSize = dp(12f)
        titlePaint.color = 0xFF6FAE6F.toInt()
        val sub = "피자를 굽고, 새를 찍는 힐링 여행"
        ty += dp(22f)
        c.drawText(sub, cx - titlePaint.measureText(sub) / 2, ty, titlePaint)

        // 버튼
        val bw = dp(190f)
        val bh = dp(40f)
        val by = h * 0.52f
        startRect = RectF(cx - bw / 2, by, cx + bw / 2, by + bh)
        contRect = RectF(cx - bw / 2, by + bh + dp(12f), cx + bw / 2, by + bh * 2 + dp(12f))

        val fill = Paint()
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
            color = 0xFF6B4F35.toInt()
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFakeBoldText = true
            textSize = dp(15f)
            color = 0xFF4A3728.toInt()
        }

        fun button(rect: RectF, label: String, enabled: Boolean) {
            fill.color = if (enabled) 0xFFF8EFDC.toInt() else Color.argb(120, 200, 190, 175)
            c.drawRoundRect(rect, dp(12f), dp(12f), fill)
            c.drawRoundRect(rect, dp(12f), dp(12f), border)
            tp.color = if (enabled) 0xFF4A3728.toInt() else Color.argb(140, 74, 55, 40)
            val tw = tp.measureText(label)
            c.drawText(label, rect.centerX() - tw / 2, rect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
        }

        button(startRect, "새로 시작하기", true)
        button(contRect, "이어하기", game.state.started)

        // 하단 정보
        tp.textSize = dp(10f)
        tp.color = Color.argb(170, 248, 239, 220)
        val info = "v0.1.1 beta · 오프라인 게임 · made with 🍕"
        c.drawText(info, cx - tp.measureText(info) / 2, h - dp(12f), tp)
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            when {
                contRect.contains(tap.x, tap.y) && game.state.started -> continueGame()
                startRect.contains(tap.x, tap.y) -> {
                    game.fadeTo { game.scene = RegionSelectScene(game) }
                }
            }
        }
        if (input.justA) {
            if (game.state.started) continueGame() else
                game.fadeTo { game.scene = RegionSelectScene(game) }
        }
        if (input.justBack) {
            game.openExitConfirm()
        }
    }

    private fun continueGame() {
        game.fadeTo {
            if (game.state.inHome) {
                game.scene = HomeScene(game)
            } else {
                game.scene = WorldScene(game, game.state.region, SpawnKind.SAVED)
            }
        }
    }
}

/** 스폰 위치 종류 */
enum class SpawnKind { SAVED, TUNNEL, HOME }
