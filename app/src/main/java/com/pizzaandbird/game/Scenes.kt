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

        // 시작 버튼에 은은하게 흐르는 빛 — 게임 초입의 분위기를 살려준다
        fun button(rect: RectF, label: String, enabled: Boolean) {
            if (enabled && rect == startRect) {   // 주요 버튼(새로 시작)에만 빛 흐름
                val pulse = 0.5f + 0.5f * kotlin.math.sin(t * 2.4f)
                fill.color = Color.argb((46 + 46 * pulse).toInt(), 242, 208, 107)
                c.drawRoundRect(
                    RectF(rect.left - dp(5f), rect.top - dp(5f), rect.right + dp(5f), rect.bottom + dp(5f)),
                    dp(15f), dp(15f), fill
                )
            }

            val pressed = enabled && game.input.isPressedIn(rect)
            fill.color = if (!enabled) Color.argb(120, 200, 190, 175)
            else if (pressed) blendToward(0xFFF8EFDC.toInt(), 0xFF6B4F35.toInt(), 0.16f)
            else 0xFFF8EFDC.toInt()
            c.drawRoundRect(rect, dp(12f), dp(12f), fill)
            c.drawRoundRect(rect, dp(12f), dp(12f), border)
            tp.color = if (enabled) 0xFF4A3728.toInt() else Color.argb(140, 74, 55, 40)
            val dy = if (pressed) dp(1.5f) else 0f
            val tw = tp.measureText(label)
            c.drawText(label, rect.centerX() - tw / 2, rect.centerY() - (tp.descent() + tp.ascent()) / 2 + dy, tp)
        }

        button(startRect, "새로 시작하기", true)
        button(contRect, "이어하기", game.state.started)

        // 하단 정보
        tp.textSize = dp(10f)
        tp.color = Color.argb(180, 74, 55, 40)
        val info = "v0.3.2 beta · 오프라인 · 한국 32곳 · 공식 새 598종 · 탐조가 성장 · made with 🍕"
        c.drawText(info, cx - tp.measureText(info) / 2, h - dp(12f), tp)
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            when {
                contRect.contains(tap.x, tap.y) && game.state.started -> {
                    game.haptic()
                    continueGame()
                }
                startRect.contains(tap.x, tap.y) -> {
                    game.haptic()
                    game.fadeTo { game.scene = CharacterSelectScene(game) }
                }
            }
        }
        if (input.justA) {
            game.haptic()
            if (game.state.started) continueGame() else
                game.fadeTo { game.scene = CharacterSelectScene(game) }
        }
        if (input.justBack) {
            // 저장이 없어도 백 버튼으로 항상 나갈 수 있어야 한다
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

/** 첫 플레이 시 아바타 선택 화면 */
class CharacterSelectScene(game: Game) : Scene(game) {

    private var maleRect = RectF()
    private var femaleRect = RectF()

    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        game.hud.questLabel = null
        game.hud.photoModeHint = false
        game.hud.regionLabel = ""
        if (game.state.gender != "male" && game.state.gender != "female") game.state.gender = "male"
    }

    override fun update(dt: Float) {
        game.hud.update(dt)
    }

    override fun drawWorld(c: Canvas) {
        val p = Paint()
        val a = game.assets

        // 하늘
        c.drawColor(0xFFA4E4EE.toInt())
        p.color = 0xFFBCEAF0.toInt()
        c.drawRect(0f, 180f, 960f, 540f, p)

        // 구름
        p.color = Color.argb(200, 255, 255, 255)
        c.drawCircle(120f, 60f, 16f, p); c.drawCircle(146f, 52f, 20f, p); c.drawCircle(174f, 62f, 15f, p)
        c.drawRect(104f, 60f, 190f, 78f, p)
        c.drawCircle(760f, 90f, 14f, p); c.drawCircle(784f, 82f, 18f, p); c.drawCircle(808f, 92f, 13f, p)
        c.drawRect(748f, 90f, 822f, 106f, p)

        // 언덕
        p.color = 0xFF8CCB8C.toInt()
        c.drawCircle(180f, 600f, 260f, p)
        c.drawCircle(780f, 630f, 300f, p)
        p.color = 0xFF7ABC7A.toInt()
        c.drawCircle(470f, 620f, 240f, p)

        // 풀 타일 바닥
        val grass = a.tiles[T.GRASS.ordinal]
        for (row in 13..16) for (col in 0 until 30) {
            val variant = a.tileVariant(T.GRASS.ordinal, col, row)
            c.drawBitmap(grass[variant], col * 32f, row * 32f, a.sprPaint)
        }

        // 날아가는 새들
        val t = game.time
        val birds = listOf("sparrow", "gull", "greattit", "egret")
        for (i in 0 until 4) {
            val bx = (t * (16f + i * 6f) + i * 240f) % 1120f - 80f
            val by = 60f + i * 34f + sin(t * 1.8f + i * 2f) * 9f
            c.drawBitmap(a.bird(birds[i]), bx, by, a.sprPaint)
        }

        // 코너 장식
        c.drawBitmap(a.pizzaIcon, 34f, 474f + sin(t * 2f) * 3f, a.sprPaint)
        c.drawBitmap(a.birdFlipped("magpie"), 894f, 470f + sin(t * 2.4f) * 3f, a.sprPaint)
        c.drawBitmap(a.bird("crane"), 26f, 428f + sin(t * 1.6f) * 3f, a.sprPaint)
    }

    override fun drawHud(c: Canvas) {
        fun dp(v: Float): Float = v * game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        val cx = w / 2f

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        val fill = Paint()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        // 제목
        tp.textSize = dp(26f)
        tp.color = 0xFF4A3728.toInt()
        val title = "여행할 캐릭터를 골라 주세요"
        c.drawText(title, cx - tp.measureText(title) / 2, dp(56f), tp)
        tp.textSize = dp(12f)
        tp.color = 0xFF4F6B4F.toInt()
        val sub = "선택한 캐릭터는 게임 내내 함께 여행해요"
        c.drawText(sub, cx - tp.measureText(sub) / 2, dp(78f), tp)

        // 카드 2장 (화면 중앙)
        val cw = dp(230f)
        val ch = dp(160f)
        val gap = dp(30f)
        val x0 = cx - (cw * 2f + gap) / 2f
        val cardTop = maxOf(dp(104f), (h - ch) / 2f - dp(6f))
        maleRect = RectF(x0, cardTop, x0 + cw, cardTop + ch)
        femaleRect = RectF(x0 + cw + gap, cardTop, x0 + cw * 2f + gap, cardTop + ch)

        fun card(r: RectF, label: String, gender: String) {
            val selected = game.state.gender == gender
            val pressed = game.input.isPressedIn(r)

            // 선택된 카드는 은은하게 맥동하는 테두리를 그린다
            if (selected) {
                val pulse = (170 + 70 * (0.5 + 0.5 * sin(game.time * 3f))).toInt()
                stroke.color = Color.argb(pulse, 226, 87, 76)
                stroke.strokeWidth = dp(5f)
                c.drawRoundRect(r, dp(16f), dp(16f), stroke)
            }

            fill.color = when {
                pressed -> blendToward(0xFFFDF3D8.toInt(), 0xFF6B4F35.toInt(), 0.14f)
                selected -> 0xFFFDF3D8.toInt()
                else -> 0xFFF8EFDC.toInt()
            }
            c.drawRoundRect(r, dp(14f), dp(14f), fill)
            stroke.color = if (selected) 0xFFE2574C.toInt() else 0xFF6B4F35.toInt()
            stroke.strokeWidth = dp(if (selected) 3f else 2f)
            c.drawRoundRect(r, dp(14f), dp(14f), stroke)

            // 스프라이트 (관절 애니메이션 idle 클립 + 비율 유지 + 살짝 흔들)
            val idleClip = game.assets.playerSet(gender, 0).idle
            val bmp = idleClip.frame(
                Dir.S,
                ((game.time + if (gender == "female") 0.8f else 0f) / Anim.IDLE.frameTime).toInt()
            )
            val k = minOf(dp(74f) / bmp.height, dp(90f) / bmp.width)
            val bw = bmp.width * k
            val bh = bmp.height * k
            val bob = sin(game.time * 2.2f + if (gender == "female") 1.2f else 0f) * dp(3f)
            val bx = r.centerX() - bw / 2f
            val by = r.top + dp(16f) + bob
            fill.color = Color.argb(60, 30, 40, 30)
            c.drawOval(RectF(bx + dp(6f), by + bh - dp(3f), bx + bw - dp(6f), by + bh + dp(4f)), fill)
            c.drawBitmap(bmp, null, RectF(bx, by, bx + bw, by + bh), game.assets.sprPaint)

            // 이름
            tp.textSize = dp(17f)
            tp.color = 0xFF4A3728.toInt()
            c.drawText(label, r.centerX() - tp.measureText(label) / 2, r.bottom - dp(24f), tp)

            // 선택됨 뱃지
            if (selected) {
                val badgeW = dp(46f)
                val badgeH = dp(17f)
                val br = RectF(r.right - badgeW - dp(8f), r.top + dp(8f), r.right - dp(8f), r.top + dp(8f) + badgeH)
                fill.color = 0xFFE2574C.toInt()
                c.drawRoundRect(br, dp(8f), dp(8f), fill)
                tp.textSize = dp(9.5f)
                tp.color = 0xFFFFF8E8.toInt()
                val bt = "선택됨"
                c.drawText(bt, br.centerX() - tp.measureText(bt) / 2, br.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
            }
        }

        card(maleRect, "남자", "male")
        card(femaleRect, "여자", "female")

        // 조작 안내
        tp.textSize = dp(11.5f)
        tp.isFakeBoldText = false
        tp.color = Color.argb(190, 74, 55, 40)
        val hint = "태그해서 선택 · A 버튼(또는 Z): 출발 · 시스템 뒤로가기: 돌아가기"
        c.drawText(hint, cx - tp.measureText(hint) / 2, h - dp(22f), tp)
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            when {
                maleRect.contains(tap.x, tap.y) -> {
                    game.state.gender = "male"
                    game.haptic()
                }
                femaleRect.contains(tap.x, tap.y) -> {
                    game.state.gender = "female"
                    game.haptic()
                }
            }
        }
        if (input.justA &&
            (game.state.gender == "male" || game.state.gender == "female")
        ) {
            game.haptic()
            game.fadeTo { game.scene = RegionSelectScene(game) }
        }
        if (input.justB || input.justBack) {
            game.fadeTo { game.scene = TitleScene(game) }
        }
    }
}
