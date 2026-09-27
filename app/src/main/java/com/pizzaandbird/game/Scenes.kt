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
        game.audio.stopAmb()
        game.audio.playBgm(R.raw.bgm_title)   // 🎵 신비로운 세계
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
        p.color = Color.argb((34f + 12f * (0.5f + 0.5f * sin(t * 3f))).toInt(), 255, 139, 66)
        c.drawCircle(658f, 380f, 43f, p)
        game.illustrations.draw(c, "wood_fired_oven.svg", RectF(620f, 334f, 696f, 424f))
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
        val cx = w / 2f

        // 로고 — 라틴이라 5x7 픽셀 폰트 + 크림색 테두리(스티커 느낌)
        Type.sticker(c, "PIZZA and BIRD", cx, dp(58f), Role.HERO, Type.INK)

        // 한글로도 크게
        Type.sticker(c, "피자와 새", cx, dp(96f), Role.DISPLAY, Type.CARAMEL)

        val sub = "피자를 굽고, 자전거를 타고, 새를 찍는 힐링 여행"
        Type.text(c, sub, cx, dp(122f), Role.CAPTION, Type.LEAF, 0.5f)

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

        fun button(rect: RectF, label: String, enabled: Boolean) {
            fill.color = if (enabled) 0xFFF8EFDC.toInt() else Color.argb(120, 200, 190, 175)
            c.drawRoundRect(rect, dp(13f), dp(13f), fill)
            c.drawRoundRect(rect, dp(13f), dp(13f), border)
            val col = if (enabled) Type.INK else Color.argb(140, 74, 55, 40)
            val p = Type.paintAt(16f, true, 0.06f, col)
            val by2 = rect.centerY() - (p.descent() + p.ascent()) / 2
            if (enabled) c.drawText(label, rect.centerX() - p.measureText(label) / 2, by2 + dp(1.2f), Type.paintAt(16f, true, 0.06f, Type.DROP))
            c.drawText(label, rect.centerX() - p.measureText(label) / 2, by2, p)
        }

        button(startRect, "새로 시작하기", true)
        button(contRect, "이어하기", game.state.started)

        // 하단 정보 — 한글·이모지가 섞여 있어 시스템 폰트로 그려진다
        val info = "v0.3.2 beta · 오프라인 · 한국 12곳 · 공식 새 598종 · 탐조가 성장 · made with 🍕"
        Type.text(c, info, cx, h - dp(12f), Role.CAPTION, Color.argb(180, 74, 55, 40), 0.5f)
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
        // 이 화면은 가상 해상도(960x540) 월드 캔버스에 그려지므로 px 단위로 지정한다
        c.drawText("여행할 캐릭터를 골라 주세요", 250f, 115f, Type.paintPx(30f, true, 0.05f, Type.BROWN))
        c.drawText("선택한 캐릭터는 게임 내내 함께 여행해요", 315f, 145f, Type.paintPx(16f, false, 0f, Type.SOFT))
    }
    override fun handleInput(input:Input) {
        if (input.justBack || input.justB) {
            game.fadeTo { game.scene = TitleScene(game) }
            return
        }
        val t = input.consumeTapScreen()
        if (t != null) {
            if (male.contains(t.x, t.y)) game.state.gender = "male"
            if (female.contains(t.x, t.y)) game.state.gender = "female"
            if (male.contains(t.x, t.y) || female.contains(t.x, t.y)) game.haptic()
        }
        if (input.justA && (game.state.gender == "male" || game.state.gender == "female")) {
            game.fadeTo { game.scene = RegionSelectScene(game) }
        }
    }
}
