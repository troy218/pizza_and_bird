package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.sin

/**
 * 씬 베이스. 오버레이(대화상자 등)를 하나 가질 수 있다.
 */
abstract class Scene(val game: Game) {
    var overlay: Overlay? = null

    /** 이 씬의 시점 리그(화면 움직임). 없는 씬도 있다 — 타이틀/지역선택 등 */
    open fun camera(): ViewRig? = null

    open fun update(dt: Float) {}
    open fun drawWorld(c: Canvas) {}
    open fun drawHud(c: Canvas) {}
    open fun handleInput(input: Input) {}
    open fun onLayout() {}
    /** drawWorld에서 사용 중인 카메라의 논리 월드 좌표. 메뉴 씬은 (0, 0). */
    open fun cameraOffset(): PointF = PointF(0f, 0f)

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
    private val titlePaint = Paint()
    private val skyBands = intArrayOf(
        0xFF7FD4E8.toInt(), 0xFF8FDCEA.toInt(), 0xFFA4E4EE.toInt(),
        0xFFBCEAF0.toInt(), 0xFFD4F2EC.toInt()
    )
    private val decoSpots = arrayOf(
        1 to 13, 5 to 14, 9 to 13, 14 to 15, 18 to 13, 22 to 14, 26 to 13, 28 to 15,
        3 to 16, 7 to 15, 12 to 16, 17 to 16, 21 to 15, 25 to 16,
        31 to 13, 34 to 15, 37 to 14, 40 to 16
    )
    private val treeFractions = floatArrayOf(0.0625f, 0.3125f, 0.5833f, 0.8542f)
    private val ovenBounds = RectF()

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
        val p = titlePaint
        val a = game.assets
        val W = game.virtW.toFloat()          // 화면비 적응 가상 너비
        val ox = (W - 960f) / 2f              // 16:9 구도를 중앙 유지하기 위한 오프셋
        val span = W + 160f                   // 구름/새 반복 범위

        // 하늘 그라데이션(밴드)
        for (i in skyBands.indices) {
            p.color = skyBands[i]
            c.drawRect(0f, i * 76f, W, (i + 1) * 76f, p)
        }

        // 햇살
        p.color = 0xFFF7EDB8.toInt()
        c.drawCircle(856f + ox, 84f, 44f, p)
        p.color = Color.argb(50, 247, 237, 184)
        c.drawCircle(856f + ox, 84f, 62f, p)
        p.color = Color.argb(28, 247, 237, 184)
        c.drawCircle(856f + ox, 84f, 84f, p)
        p.color = 0xFFFFFBE0.toInt()
        c.drawCircle(856f + ox, 84f, 32f, p)

        // 구름 (두 겹)
        p.color = Color.argb(215, 255, 255, 255)
        drawCloud(c, p, (t * 9f) % span - 120f, 70f, 1.4f)
        drawCloud(c, p, (t * 5f + 300f) % span - 120f, 130f, 1.0f)
        drawCloud(c, p, (t * 6.5f + 640f) % span - 120f, 52f, 1.2f)
        p.color = Color.argb(120, 214, 236, 244)
        drawCloud(c, p, (t * 3.5f + 120f) % span - 120f, 180f, 2.2f)
        drawCloud(c, p, (t * 4.5f + 760f) % span - 120f, 110f, 1.8f)

        // 날아가는 새 실루엣
        p.color = Color.argb(150, 90, 80, 90)
        p.strokeWidth = 2.4f
        for (i in 0 until 4) {
            val bx = (t * 26f + i * 240f) % span - 80f
            val by = 90f + i * 38f + sin(t * 2f + i) * 9f
            c.drawLine(bx, by, bx + 9f, by - 4f, p)
            c.drawLine(bx + 9f, by - 4f, bx + 18f, by, p)
        }

        // 먼 언덕 (넓은 화면비에서도 지평선이 빈틈없이 덮이도록 가장자리를 채운다)
        p.color = 0xFF7ABC7A.toInt()
        if (W > 962f) { c.drawCircle(ox - 60f, 474f, 230f, p); c.drawCircle(W - ox + 60f, 480f, 240f, p) }
        c.drawCircle(200f + ox, 470f, 190f, p)
        c.drawCircle(640f + ox, 492f, 230f, p)
        p.color = 0xFF68AC6C.toInt()
        c.drawCircle(420f + ox, 486f, 170f, p)
        c.drawCircle(880f + ox, 478f, 150f, p)

        // 풀 타일 바닥 (하단 3줄 — 화면비에 맞춰 채운다)
        val grass = a.tiles[T.GRASS.ordinal]
        val flowers = a.tiles[T.FLOWER.ordinal]
        val tall = a.tiles[T.TALLGRASS.ordinal]
        val maxCol = (game.virtW + 31) / 32
        for (row in 12..16) {
            for (col in 0 until maxCol) {
                val variant = a.tileVariant(T.GRASS.ordinal, col, row)
                c.drawBitmap(grass[variant], col * 32f, row * 32f, a.sprPaint)
            }
        }
        // 꽃/풀숲 포인트
        var visibleSpotIndex = 0
        for (i in decoSpots.indices) {
            val (col, row) = decoSpots[i]
            if (col >= maxCol) continue
            val bmp = if (visibleSpotIndex % 3 == 2) tall[visibleSpotIndex % tall.size] else flowers[visibleSpotIndex % flowers.size]
            c.drawBitmap(bmp, col * 32f, row * 32f, a.sprPaint)
            visibleSpotIndex++
        }

        // 지평선 나무 (화면비에 따라 분포)
        val trees = a.tiles[T.TREE.ordinal]
        for (i in treeFractions.indices) {
            c.drawBitmap(trees[i % trees.size], treeFractions[i] * W, 352f, a.sprPaint)
        }
        val pines = trees.size
        if (pines > 1) c.drawBitmap(trees[1], 0.719f * W, 356f, a.sprPaint)

        // 피자 & 새 로고
        val bob = sin(t * 2.2f) * 5f
        val pz = a.pizzaIconBig
        c.drawBitmap(pz, 384f + ox, 336f + bob, a.sprPaint)
        p.color = Color.argb((34f + 12f * (0.5f + 0.5f * sin(t * 3f))).toInt(), 255, 139, 66)
        c.drawCircle(658f + ox, 380f, 43f, p)
        ovenBounds.set(620f + ox, 334f, 696f + ox, 424f)
        game.illustrations.draw(c, "wood_fired_oven.svg", ovenBounds)
        val bird = a.bird("sparrow")
        c.drawBitmap(bird, 296f + ox, 348f + sin(t * 2.4f) * 4f, a.sprPaint)
        val fb = sin(t * 2.6f + 1f) * 7f
        c.drawBitmap(a.birdFlipped("sparrow"), 536f + ox, 300f + fb, a.sprPaint)
        val crane = a.bird("crane")
        c.drawBitmap(crane, 130f + ox, 300f + sin(t * 1.7f) * 5f, a.sprPaint)
        val owl = a.bird("owl")
        c.drawBitmap(owl, 806f + ox, 306f + sin(t * 2.9f) * 4f, a.sprPaint)
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

        // 시작 버튼에 은은하게 흐르는 빛 — 게임 초입의 분위기를 살려준다
        fun button(rect: RectF, label: String, enabled: Boolean) {
            val pressed = enabled && game.input.isPressedIn(rect)
            fill.color = if (!enabled) Color.argb(120, 200, 190, 175)
            else if (pressed) blendToward(0xFFF8EFDC.toInt(), 0xFF6B4F35.toInt(), 0.16f)
            else 0xFFF8EFDC.toInt()
            c.drawRoundRect(rect, dp(13f), dp(13f), fill)
            c.drawRoundRect(rect, dp(13f), dp(13f), border)
            val col = if (enabled) Type.INK else Color.argb(140, 74, 55, 40)
            val p = Type.paintAt(16f, true, 0.06f, col)
            val by2 = rect.centerY() - (p.descent() + p.ascent()) / 2
            if (enabled) c.drawText(label, rect.centerX() - p.measureText(label) / 2, by2 + dp(1.2f), Type.paintAt(16f, true, 0.06f, Type.DROP))
            c.drawText(label, rect.centerX() - p.measureText(label) / 2, by2 + if (pressed) dp(1.5f) else 0f, p)
        }

        button(startRect, "새로 시작하기", true)
        button(contRect, "이어하기", game.state.started)

        // 하단 정보 — 한글·이모지가 섞여 있어 시스템 폰트로 그려진다
        val info = "v0.4.2 beta · 2K 렌더링 · 오프라인 · 한국 32곳 · 공식 새 598종 · 몰입 카메라 · made with 🍕"
        Type.text(c, info, cx, h - dp(12f), Role.CAPTION, Color.argb(180, 74, 55, 40), 0.5f)
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

/** 첫 플레이 시 아바타 선택 화면. 카드는 가상 캔버스(화면비 적응), 버튼은 실제 화면 좌표로 그린다. */
class CharacterSelectScene(game: Game) : Scene(game) {
    private val male: RectF
    private val female: RectF
    private var nextRect = RectF()
    private var backRect = RectF()
    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        // 카드 레이아웃은 가상 너비(화면비 적응) 중심으로 배치한다
        val cx = game.virtW / 2f
        male = RectF(cx - 300f, 245f, cx - 30f, 390f)
        female = RectF(cx + 30f, 245f, cx + 300f, 390f)
    }

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFFA4E4EE.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 이 화면은 가상 해상도 월드 캔버스에 그려지므로 px 단위로 지정한다 (가로는 화면비 적응)
        val cx = game.virtW / 2f
        val t1 = Type.paintPx(30f, true, 0.05f, Type.BROWN)
        val s1 = "여행할 캐릭터를 골라 주세요"
        c.drawText(s1, cx - t1.measureText(s1) / 2f, 115f, t1)
        val t2 = Type.paintPx(16f, false, 0f, Type.SOFT)
        val s2 = "선택한 캐릭터는 게임 내내 함께 여행해요"
        c.drawText(s2, cx - t2.measureText(s2) / 2f, 145f, t2)
        fun card(r: RectF, label: String, selected: Boolean, bmp: android.graphics.Bitmap) {
            p.color = if (selected) 0xFFFFE0A3.toInt() else 0xFFF8EFDC.toInt()
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = if (selected) 5f else 2f
            p.color = 0xFF6B4F35.toInt()
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.FILL
            c.drawBitmap(bmp, null, RectF(r.centerX()-32f, r.top+18f, r.centerX()+32f, r.top+82f), p)
            val lp = Type.paintPx(22f, true, 0.04f, Type.INK)
            c.drawText(label, r.centerX()-lp.measureText(label)/2f, r.bottom-25f, lp)
        }
        val t = game.time
        val maleIdle = game.assets.playerSet("male", 0).idle
        val femaleIdle = game.assets.playerSet("female", 0).idle
        card(male, "남자", game.state.gender == "male", maleIdle.frame(Dir.S, (t / Anim.IDLE.frameTime).toInt()))
        card(female, "여자", game.state.gender == "female", femaleIdle.frame(Dir.S, ((t + 0.8f) / Anim.IDLE.frameTime).toInt()))
        val t3 = Type.paintPx(15f, false, 0f, Type.SOFT)
        val s3 = "캐릭터를 탭해서 선택한 뒤 계속하기를 누르세요"
        c.drawText(s3, cx - t3.measureText(s3) / 2f, 448f, t3)
    }

    override fun drawHud(c: Canvas) {
        val d = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        val bw = minOf(d * 170f, w * 0.36f)
        val bh = d * 40f
        nextRect = RectF((w - bw) / 2f, h - d * 16f - bh, (w + bw) / 2f, h - d * 16f)
        backRect = RectF(d * 14f, d * 14f, d * 90f, d * 50f)
        UiKit.button(c, game, nextRect, "계속하기 ▶", 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 14f)
        UiKit.button(c, game, backRect, "◀ 뒤로", 0xFFF2E3C2.toInt(), 0xFF4A3728.toInt(), 12f)
    }

    override fun handleInput(input: Input) {
        if (input.justBack || input.justB) { game.fadeTo { game.scene = TitleScene(game) }; return }
        val tap = input.consumeTapScreen()
        if (tap != null) {
            if (backRect.contains(tap.x, tap.y)) {
                game.fadeTo { game.scene = TitleScene(game) }
                return
            }
            if (nextRect.contains(tap.x, tap.y)) {
                game.fadeTo { game.scene = RegionSelectScene(game) }
                return
            }
            val v = game.screenToVirtual(tap)
            when {
                male.contains(v.x, v.y) -> { game.state.gender = "male"; game.haptic() }
                female.contains(v.x, v.y) -> { game.state.gender = "female"; game.haptic() }
            }
        }
        if (input.justA) game.fadeTo { game.scene = RegionSelectScene(game) }
    }
}
