package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 지역 카드 공용 UI (새 게임 정착 화면 + 이사 오버레이)
 */
object RegionCards {

    /** 한 페이지에 보여줄 지역 카드 수 */
    const val PER_PAGE = 9

    fun pageCount(total: Int): Int = maxOf(1, (total + PER_PAGE - 1) / PER_PAGE)

    fun pageItems(all: List<RegionDef>, page: Int): List<RegionDef> {
        val from = (page * PER_PAGE).coerceIn(0, maxOf(0, all.size - 1))
        val to = minOf(all.size, from + PER_PAGE)
        return if (from >= to) emptyList() else all.subList(from, to)
    }

    fun cardRects(area: RectF, count: Int, gap: Float): List<RectF> {
        val cols = 3
        val rows = (count + cols - 1) / cols
        val cw = (area.width() - gap * (cols - 1)) / cols
        val chh = (area.height() - gap * (rows - 1)) / rows
        val rects = ArrayList<RectF>()
        for (i in 0 until count) {
            val col = i % cols
            val row = i / cols
            rects.add(
                RectF(
                    area.left + col * (cw + gap), area.top + row * (chh + gap),
                    area.left + col * (cw + gap) + cw, area.top + row * (chh + gap) + chh
                )
            )
        }
        return rects
    }

    fun draw(c: Canvas, game: Game, rects: List<RectF>, regions: List<RegionDef>, selectedId: String?) {
        val dp = game.density
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        // 역할별 페인트: 이름은 진하게, 설명은 보통 두께로
        val namePaint = Type.paintAt(14f, true, 0.03f, Type.INK)
        val enPaint = Type.paintAt(8.5f, false, 0f, Type.SOFT)
        val metaLeaf = Type.paintAt(9.5f, false, 0f, Type.LEAF)
        val metaBird = Type.paintAt(9.5f, false, 0f, Type.BROWN)
        val metaSeason = Type.paintAt(9.5f, false, 0f, Type.SKY)
        val descPaint = Type.paintAt(9.5f, false, 0f, Type.SOFT)
        val homeOwnedPaint = Type.paintAt(9f, true, 0.02f, Type.SKY)
        val homeBuyPaint = Type.paintAt(9f, true, 0.02f, Type.CARAMEL)

        for ((i, reg) in regions.withIndex()) {
            if (i >= rects.size) break
            val r = rects[i]
            val sel = reg.id == selectedId
            UiKit.card(c, game, r, 10f, sel, if (sel) 0xFFE2574C.toInt() else reg.kind.color, if (sel) 3f else 2f)
            // 지역 성격 스트립
            fill.color = reg.kind.color
            c.drawRoundRect(RectF(r.left + dp * 10, r.top + dp * 5, r.left + dp * 44, r.top + dp * 8), dp * 2, dp * 2, fill)
            // 선택 핀
            if (sel) {
                val ccx = r.right - dp * 2f
                val ccy = r.top + dp * 2f
                fill.color = Color.argb(70, 40, 20, 10)
                c.drawCircle(ccx, ccy + dp * 1.5f, dp * 11f, fill)
                fill.color = 0xFFE2574C.toInt()
                c.drawCircle(ccx, ccy, dp * 11f, fill)
                stroke.color = 0xFFFFF8E8.toInt()
                stroke.strokeWidth = dp * 2f
                c.drawCircle(ccx, ccy, dp * 11f, stroke)
                val vp = Type.paintAt(12f, true, 0.02f, 0xFFFFF8E8.toInt())
                c.drawText("✓", ccx - vp.measureText("✓") / 2f, Type.midBaseline(vp, ccy), vp)
            }

            val x = r.left + dp * 10
            var y = r.top + dp * 16
            val nameLine = "${reg.emoji} ${reg.name}"
            c.drawText(nameLine, x, y, namePaint)
            val nameW = namePaint.measureText(nameLine)
            c.drawText(reg.english, x + nameW + dp * 6f, y, enPaint)

            y += dp * 13f
            c.drawText(reg.habitatLabels, x, y, metaLeaf)

            y += dp * 12f
            val sig = "대표 새: " + Regions.signatureBirds(reg).joinToString(", ") { it.name }
            c.drawText(sig, x, y, metaBird)

            y += dp * 12f
            c.drawText("📅 ${reg.season}", x, y, metaSeason)

            y += dp * 12f
            val descLines = game.hud.wrapText(reg.desc, descPaint, r.width() - dp * 20f).take(1)
            for (ln in descLines) {
                c.drawText(ln, x, y, descPaint)
                y += dp * 11f
            }
            val hasHome = reg.id == START_REGION_ID || game.state.ownsHome(reg.id)
            val homeLine = if (reg.id == START_REGION_ID && !game.state.started) "🏠 시작 집 제공"
            else if (hasHome) "🏠 보유한 집" else "집 매입 ${won(HousePrices.forRegion(reg.id))}"
            c.drawText(homeLine, x, y + dp * 2f, if (hasHome) homeOwnedPaint else homeBuyPaint)
        }
    }

    fun hit(rects: List<RectF>, regions: List<RegionDef>, x: Float, y: Float): RegionDef? {
        for ((i, r) in rects.withIndex()) {
            if (i < regions.size && r.contains(x, y)) return regions[i]
        }
        return null
    }
}

/**
 * 새 게임: 정착할 지역 선택
 */
class RegionSelectScene(game: Game) : Scene(game) {

    // 새 게임의 출발지는 서울로 고정한다. 이 화면은 안내와 확인만 담당한다.
    private val selected: RegionDef = Regions.byId[START_REGION_ID]!!
    private var cardRects: List<RectF> = emptyList()
    private var area = RectF()
    private var confirmRect = RectF()
    private var backRect = RectF()
    private val regions = listOf(selected)

    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        game.hud.questLabel = null
        game.hud.photoModeHint = false
    }

    override fun drawWorld(c: Canvas) {
        val p = Paint()
        val a = game.assets
        // 하늘
        p.color = 0xFFA4E4EE.toInt()
        c.drawRect(0f, 0f, 960f, 540f, p)
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
        val t = game.time
        // 날아가는 새들
        val birds = listOf("sparrow", "gull", "greattit", "egret")
        for (i in 0 until 4) {
            val bx = (t * (16f + i * 6f) + i * 240f) % 1120f - 80f
            val by = 60f + i * 34f + sin(t * 1.8f + i * 2f) * 9f
            c.drawBitmap(a.bird(birds[i]), bx, by, a.sprPaint)
        }
        c.drawBitmap(a.pizzaIcon, 34f, 474f + sin(t * 2f) * 3f, a.sprPaint)
        c.drawBitmap(a.birdFlipped("magpie"), 894f, 470f + sin(t * 2.4f) * 3f, a.sprPaint)
        c.drawBitmap(a.bird("crane"), 26f, 428f + sin(t * 1.6f) * 3f, a.sprPaint)
    }

    override fun drawHud(c: Canvas) {
        val dp = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        backRect = RectF(dp * 14f, dp * 14f, dp * 90f, dp * 50f)
        UiKit.button(c, game, backRect, "◀ 뒤로", 0xFFF2E3C2.toInt(), 0xFF4A3728.toInt(), 12f)

        Type.sticker(c, "서울에서 시작해볼까요?", w / 2f, dp * 32f, Role.DISPLAY, Type.INK)
        Type.text(c, "시작 지역은 서울로 고정되어 있어요 · 다른 지역의 집은 여행 후 매입할 수 있어요",
            w / 2f, dp * 50f, Role.CAPTION, Type.LEAF, 0.5f)

        // 카드는 화면을 가득 채우지 않도록 가운데에 알맞은 크기로 배치
        val cardW = minOf(w - dp * 112f, dp * 520f)
        val cardH = minOf(h - dp * 158f, dp * 280f)
        area = RectF((w - cardW) / 2f, dp * 70f, (w + cardW) / 2f, dp * 70f + cardH)
        cardRects = listOf(area)
        RegionCards.draw(c, game, cardRects, regions, selected.id)

        // 하단 확인 바 — 프리미엄 패널 + 골드 버튼
        val barH = dp * 56f
        val bar = RectF(dp * 16f, h - barH - dp * 12f, w - dp * 16f, h - dp * 12f)
        UiKit.panel(c, game, bar)

        val info = "서울에 집을 마련하고 여행을 시작할게요"
        Type.textCentered(c, info, bar.left + dp * 16f, bar.centerY(), Role.BODY, Type.INK)

        val bw = dp * 150f
        confirmRect = RectF(bar.right - bw - dp * 12f, bar.top + dp * 10f, bar.right - dp * 12f, bar.bottom - dp * 10f)
        UiKit.button(c, game, confirmRect, "서울에서 시작!", 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 14f)
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justBack || (tap != null && backRect.contains(tap.x, tap.y))) {
            game.fadeTo { game.scene = CharacterSelectScene(game) }
            return
        }
        if (tap != null && confirmRect.contains(tap.x, tap.y)) {
            game.haptic()
            startGame(selected)
            return
        }
        if (input.justA) startGame(selected)
    }

    private fun startGame(r: RegionDef) {
        game.state.reset(START_REGION_ID)
        SaveManager.save(game.context, game.state)
        game.fadeTo {
            game.scene = WorldScene(game, r.id, SpawnKind.HOME)
        }
    }
}

/**
 * 이사 오버레이 (집 안의 이사 박스)
 */
class RegionSelectOverlay(
    scene: Scene,
    private val onPick: (RegionDef) -> Unit
) : Overlay(scene) {

    private var selected: RegionDef? = null
    private var cardRects: List<RectF> = emptyList()
    private var area = RectF()
    private var confirmRect = RectF()
    private var cancelRect = RectF()
    private var panelR = RectF()
    private var page = 0
    private var prevRect = RectF()
    private var nextRect = RectF()
    private val allRegions: List<RegionDef>
        get() = Regions.ALL.filter { it.id in scene.game.state.visited }
    private val regions: List<RegionDef>
        get() = RegionCards.pageItems(allRegions, page)

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap == null) {
            if (input.justB || input.justBack) finished = true
            return
        }
        if (selected == null) {
            val pages = RegionCards.pageCount(allRegions.size)
            if (prevRect.contains(tap.x, tap.y)) {
                scene.game.sfx(Audio.Sfx.TAP, 0.5f)
                page = (page - 1 + pages) % pages
                return
            }
            if (nextRect.contains(tap.x, tap.y)) {
                scene.game.sfx(Audio.Sfx.TAP, 0.5f)
                page = (page + 1) % pages
                return
            }
            val hit = RegionCards.hit(cardRects, regions, tap.x, tap.y)
            if (hit != null) {
                scene.game.haptic()
                selected = hit
            }
            if (input.justB || input.justBack) finished = true
            return
        }
        if (cancelRect.contains(tap.x, tap.y)) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            selected = null
            return
        }
        if (confirmRect.contains(tap.x, tap.y)) {
            scene.game.haptic()
            val r = selected
            selected = null
            if (r != null) {
                finished = true
                onPick(r)
            }
        }
    }

    override fun draw(c: Canvas) {
        val game = scene.game
        val dp = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()

        UiKit.dim(c, game, 150, bornAt)

        panelR = RectF(dp * 14f, dp * 14f, w - dp * 14f, h - dp * 14f)
        UiKit.panel(c, game, panelR)

        val t1 = "📦 이사갈 곳을 골라요"
        Type.text(c, t1, panelR.left + dp * 16f, panelR.top + dp * 28f, Role.TITLE, Type.INK)
        val t2 = "이사 ${won(MOVE_COST)} · 방문한 지역의 집을 매입해 내 집으로 만들 수 있어요"
        Type.text(c, t2, panelR.left + dp * 16f, panelR.top + dp * 46f, Role.CAPTION, Type.SOFT)

        area = RectF(
            panelR.left + dp * 12f, panelR.top + dp * 56f,
            panelR.right - dp * 12f, panelR.bottom - dp * 66f
        )
        val pageRegions = regions
        cardRects = RegionCards.cardRects(area, pageRegions.size, dp * 8f)
        RegionCards.draw(c, game, cardRects, pageRegions, selected?.id)

        if (selected == null) {
            // 페이지 이동 바
            val pages = RegionCards.pageCount(allRegions.size)
            if (page >= pages) page = 0
            val navH = dp * 26f
            val navY = panelR.bottom - dp * 46f
            prevRect = RectF(panelR.centerX() - dp * 130f, navY, panelR.centerX() - dp * 54f, navY + navH)
            nextRect = RectF(panelR.centerX() + dp * 54f, navY, panelR.centerX() + dp * 130f, navY + navH)
            for ((rr, lbl) in listOf(prevRect to "◀ 이전", nextRect to "다음 ▶")) {
                UiKit.button(c, game, rr, lbl, 0xFFF2E3C2.toInt(), 0xFF4A3728.toInt(), 11.5f)
            }
            val pg = "${page + 1} / $pages  (방문 ${allRegions.size}곳)"
            val pgW = Type.width(Role.LABEL, pg, Type.INK) + dp * 22f
            UiKit.badge(
                c, game, RectF(panelR.centerX() - pgW / 2f, navY, panelR.centerX() + pgW / 2f, navY + navH),
                pg, 0xFF6B4F35.toInt(), Type.INK, 12f
            )
            return
        }

        // 확인 바 — 카드 배경 + 골드/크림 버튼
        val bar = RectF(panelR.left + dp * 12f, panelR.bottom - dp * 58f, panelR.right - dp * 12f, panelR.bottom - dp * 10f)
        UiKit.card(c, game, bar, 10f, false, 0xFFC9A87B.toInt(), 1.5f)
        val cur = scene.game.state
        val houseCost = if (cur.ownsHome(selected!!.id)) 0 else HousePrices.forRegion(selected!!.id)
        var info = when {
            selected!!.id == cur.homeRegion -> "지금 사는 곳이에요!"
            houseCost == 0 -> "${selected!!.name} 집 보유 · 이사 ${won(MOVE_COST)} · 보유 ${won(cur.money)}"
            else -> "${selected!!.name} 집 매입 ${won(houseCost)} + 이사 ${won(MOVE_COST)} · 보유 ${won(cur.money)}"
        }
        val bw = dp * 110f
        confirmRect = RectF(bar.right - bw - dp * 10f, bar.top + dp * 8f, bar.right - dp * 10f, bar.bottom - dp * 8f)
        cancelRect = RectF(confirmRect.left - bw - dp * 8f, bar.top + dp * 8f, confirmRect.left - dp * 8f, bar.bottom - dp * 8f)
        // 정보 문구가 버튼과 겹치지 않게 말줄임
        val infoPaint = Type.paint(Role.BODY, Type.INK)
        val maxIW = cancelRect.left - bar.left - dp * 16f
        if (maxIW > dp * 60f) {
            val origLen = info.length
            while (info.length > 4 && infoPaint.measureText(info) > maxIW) info = info.dropLast(1)
            if (info.length < origLen) info = info.dropLast(1) + "…"
        }
        Type.text(c, info, bar.left + dp * 10f, Type.midBaseline(infoPaint, bar.centerY()), Role.BODY, Type.INK)

        val label = if (houseCost > 0) "매입 & 이사" else "이사!"
        UiKit.button(c, game, confirmRect, label, 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 13f)
        UiKit.button(c, game, cancelRect, "취소", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 13f)
    }
}
