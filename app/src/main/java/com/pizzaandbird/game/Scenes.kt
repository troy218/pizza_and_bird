package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 씬 베이스. 오버레이(대화상자 등)를 하나 가질 수 있다.
 */
abstract class Scene(val game: Game) {
    var overlay: Overlay? = null

    /** 이 씬의 몰입 카메라 리그 (없는 씬도 있다 — 타이틀/지역선택 등) */
    open fun camera(): CameraRig? = null

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
        val a = game.assets

        // 하늘 그라데이션(밴드)
        val skyBands = intArrayOf(
            0xFF7FD4E8.toInt(), 0xFF8FDCEA.toInt(), 0xFFA4E4EE.toInt(),
            0xFFBCEAF0.toInt(), 0xFFD4F2EC.toInt()
        )
        for (i in skyBands.indices) {
            p.color = skyBands[i]
            c.drawRect(0f, i * 76f, 960f, (i + 1) * 76f, p)
        }

        // 햇살
        p.color = 0xFFF7EDB8.toInt()
        c.drawCircle(856f, 84f, 44f, p)
        p.color = Color.argb(50, 247, 237, 184)
        c.drawCircle(856f, 84f, 62f, p)
        p.color = Color.argb(28, 247, 237, 184)
        c.drawCircle(856f, 84f, 84f, p)
        p.color = 0xFFFFFBE0.toInt()
        c.drawCircle(856f, 84f, 32f, p)

        // 구름 (두 겹)
        p.color = Color.argb(215, 255, 255, 255)
        drawCloud(c, p, (t * 9f) % 1120f - 120f, 70f, 1.4f)
        drawCloud(c, p, (t * 5f + 300f) % 1120f - 120f, 130f, 1.0f)
        drawCloud(c, p, (t * 6.5f + 640f) % 1120f - 120f, 52f, 1.2f)
        p.color = Color.argb(120, 214, 236, 244)
        drawCloud(c, p, (t * 3.5f + 120f) % 1120f - 120f, 180f, 2.2f)
        drawCloud(c, p, (t * 4.5f + 760f) % 1120f - 120f, 110f, 1.8f)

        // 날아가는 새 실루엣
        p.color = Color.argb(150, 90, 80, 90)
        p.strokeWidth = 2.4f
        for (i in 0 until 4) {
            val bx = (t * 26f + i * 240f) % 1120f - 80f
            val by = 90f + i * 38f + sin(t * 2f + i) * 9f
            c.drawLine(bx, by, bx + 9f, by - 4f, p)
            c.drawLine(bx + 9f, by - 4f, bx + 18f, by, p)
        }

        // 먼 언덕
        p.color = 0xFF7ABC7A.toInt()
        c.drawCircle(200f, 470f, 190f, p)
        c.drawCircle(640f, 492f, 230f, p)
        p.color = 0xFF68AC6C.toInt()
        c.drawCircle(420f, 486f, 170f, p)
        c.drawCircle(880f, 478f, 150f, p)

        // 풀 타일 바닥 (하단 3줄)
        val grass = a.tiles[T.GRASS.ordinal]
        val flowers = a.tiles[T.FLOWER.ordinal]
        val tall = a.tiles[T.TALLGRASS.ordinal]
        for (row in 12..16) {
            for (col in 0 until 30) {
                val variant = a.tileVariant(T.GRASS.ordinal, col, row)
                c.drawBitmap(grass[variant], col * 32f, row * 32f, a.sprPaint)
            }
        }
        // 꽃/풀숲 포인트
        val decoSpots = listOf(
            1 to 13, 5 to 14, 9 to 13, 14 to 15, 18 to 13, 22 to 14, 26 to 13, 28 to 15,
            3 to 16, 7 to 15, 12 to 16, 17 to 16, 21 to 15, 25 to 16
        )
        for ((i, pair) in decoSpots.withIndex()) {
            val (col, row) = pair
            val bmp = if (i % 3 == 2) tall[i % tall.size] else flowers[i % flowers.size]
            c.drawBitmap(bmp, col * 32f, row * 32f, a.sprPaint)
        }

        // 지평선 나무
        val trees = a.tiles[T.TREE.ordinal]
        for ((i, tx) in listOf(60f, 300f, 560f, 820f).withIndex()) {
            c.drawBitmap(trees[i % trees.size], tx, 352f, a.sprPaint)
        }
        val pines = trees.size
        if (pines > 1) c.drawBitmap(trees[1], 690f, 356f, a.sprPaint)

        // 피자 & 새 로고
        val bob = sin(t * 2.2f) * 5f
        val pz = a.pizzaIconBig
        c.drawBitmap(pz, 384f, 336f + bob, a.sprPaint)
        val bird = a.bird("sparrow")
        c.drawBitmap(bird, 296f, 348f + sin(t * 2.4f) * 4f, a.sprPaint)
        val fb = sin(t * 2.6f + 1f) * 7f
        c.drawBitmap(a.birdFlipped("sparrow"), 536f, 300f + fb, a.sprPaint)
        val crane = a.bird("crane")
        c.drawBitmap(crane, 130f, 300f + sin(t * 1.7f) * 5f, a.sprPaint)
        val owl = a.bird("owl")
        c.drawBitmap(owl, 806f, 306f + sin(t * 2.9f) * 4f, a.sprPaint)
    }

    private fun drawCloud(c: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, 9f * s, p)
        c.drawCircle(x + 10f * s, y - 3f * s, 11f * s, p)
        c.drawCircle(x + 22f * s, y, 8f * s, p)
        c.drawRect(x - 8f * s, y, x + 28f * s, y + 9f * s, p)
    }

    override fun drawHud(c: Canvas) {
        fun dp(v: Float): Float = v * game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFakeBoldText = true
            textSize = dp(38f)
            color = 0xFF4A3728.toInt()
        }
        val shadow = Paint(titlePaint).apply { color = Color.argb(80, 74, 55, 40) }
        val cx = w / 2f

        val t1 = "PIZZA and BIRD"
        val t2 = "피자와 새"

        var ty = dp(52f)
        c.drawText(t1, cx - titlePaint.measureText(t1) / 2 + dp(2.5f), ty + dp(2.5f), shadow)
        c.drawText(t1, cx - titlePaint.measureText(t1) / 2, ty, titlePaint)

        titlePaint.textSize = dp(22f)
        titlePaint.color = 0xFF6B4F35.toInt()
        ty += dp(34f)
        c.drawText(t2, cx - titlePaint.measureText(t2) / 2 + dp(1.5f), ty + dp(1.5f), shadow)
        c.drawText(t2, cx - titlePaint.measureText(t2) / 2, ty, titlePaint)

        titlePaint.textSize = dp(12.5f)
        titlePaint.color = 0xFF6FAE6F.toInt()
        val sub = "피자를 굽고, 자전거를 타고, 새를 찍는 힐링 여행"
        ty += dp(24f)
        c.drawText(sub, cx - titlePaint.measureText(sub) / 2, ty, titlePaint)

        // 버튼
        val bw = dp(210f)
        val bh = dp(44f)
        val by = h * 0.56f
        startRect = RectF(cx - bw / 2, by, cx + bw / 2, by + bh)
        contRect = RectF(cx - bw / 2, by + bh + dp(14f), cx + bw / 2, by + bh * 2 + dp(14f))

        val fill = Paint()
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
            color = 0xFF6B4F35.toInt()
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFakeBoldText = true
            textSize = dp(16f)
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
        tp.color = Color.argb(180, 74, 55, 40)
        val info = "v0.3.2 beta · 오프라인 · 한국 32곳 · 공식 새 598종 · 몰입 카메라 · made with 🍕"
        c.drawText(info, cx - tp.measureText(info) / 2, h - dp(12f), tp)
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            when {
                contRect.contains(tap.x, tap.y) && game.state.started -> continueGame()
                startRect.contains(tap.x, tap.y) -> {
                    game.fadeTo { game.scene = CharacterSelectScene(game) }
                }
            }
        }
        if (input.justA) {
            if (game.state.started) continueGame() else
                game.fadeTo { game.scene = CharacterSelectScene(game) }
        }
        if (input.justBack) {
            if (game.state.started) {
                game.openExitConfirm()
            }
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

/** 첫 플레이 시 아바타 선택 화면 */
class CharacterSelectScene(game: Game) : Scene(game) {
    private val male = RectF(180f, 245f, 450f, 390f)
    private val female = RectF(510f, 245f, 780f, 390f)
    init { game.hud.showControls = false; game.hud.showStats = false; game.hud.showMinimap = false }
    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFFA4E4EE.toInt()); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=0xFF6B4F35.toInt(); p.textSize=30f; p.isFakeBoldText=true
        c.drawText("여행할 캐릭터를 골라 주세요", 250f, 115f, p)
        p.textSize=16f; p.isFakeBoldText=false; c.drawText("선택한 캐릭터는 게임 내내 함께 여행해요", 315f, 145f, p)
        fun card(r:RectF, label:String, selected:Boolean, bmp:android.graphics.Bitmap) { p.color=if(selected) 0xFFFFE0A3.toInt() else 0xFFF8EFDC.toInt(); c.drawRoundRect(r,18f,18f,p); p.style=Paint.Style.STROKE; p.strokeWidth=if(selected)5f else 2f; p.color=0xFF6B4F35.toInt(); c.drawRoundRect(r,18f,18f,p); p.style=Paint.Style.FILL; c.drawBitmap(bmp,null,RectF(r.centerX()-32,r.top+18,r.centerX()+32,r.top+82),p); p.textSize=22f; p.isFakeBoldText=true; c.drawText(label,r.centerX()-p.measureText(label)/2,r.bottom-25,p) }
        card(male,"남자",game.state.gender=="male",game.assets.playerDown[0]); card(female,"여자",game.state.gender=="female",game.assets.playerDown[0])
        p.textSize=15f; p.isFakeBoldText=false; c.drawText("탭해서 선택 · A 버튼으로 계속",350f,455f,p)
    }
    override fun handleInput(input:Input) { val t=input.consumeTapScreen(); if(t!=null){ if(male.contains(t.x,t.y)) game.state.gender="male"; if(female.contains(t.x,t.y)) game.state.gender="female"; if(male.contains(t.x,t.y)||female.contains(t.x,t.y)) game.haptic() }; if(input.justA && (game.state.gender=="male"||game.state.gender=="female")) game.fadeTo { game.scene=RegionSelectScene(game) } }
}
