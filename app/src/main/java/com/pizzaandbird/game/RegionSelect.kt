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
                UiKit.iconCenter(c, game, "check", ccx, ccy, dp * 14f)
            }

            val x = r.left + dp * 10
            var y = r.top + dp * 16
            UiKit.icon(c, game, reg.emoji, RectF(x, y - dp * 12f, x + dp * 15f, y + dp * 3f))
            c.drawText(reg.name, x + dp * 20f, y, namePaint)
            val nameW = namePaint.measureText(reg.name) + dp * 20f
            c.drawText(reg.english, x + nameW + dp * 6f, y, enPaint)

            y += dp * 13f
            UiKit.drawIconText(c, game, reg.habitatLabels, x, y, metaLeaf)

            y += dp * 12f
            val sig = "대표 새: " + Regions.signatureBirds(reg).joinToString(", ") { it.name }
            c.drawText(sig, x, y, metaBird)

            y += dp * 12f
            // 사람은 한 장소에만 산다 — 정착지를 고를 때 "누가 있는 동네인지" 보이도록 표시
            val shopHere = CameraShops.shop(reg.id)
            val landmark = when {
                reg.id == NpcRoster.PROFESSOR_REGION && shopHere != null -> " · 보리 박사 · 카메라샵"
                reg.id == NpcRoster.PROFESSOR_REGION -> " · 보리 박사"
                shopHere != null -> " · 카메라샵 ${shopHere.specialty}"
                else -> ""
            }
            UiKit.icon(c, game, "calendar", RectF(x, y - dp * 10f, x + dp * 13f, y + dp * 3f))
            c.drawText("${reg.season}$landmark", x + dp * 17f, y, metaSeason)

            y += dp * 12f
            val descLines = game.hud.wrapText(reg.desc, descPaint, r.width() - dp * 20f).take(1)
            for (ln in descLines) {
                c.drawText(ln, x, y, descPaint)
                y += dp * 11f
            }
            val hasHome = reg.id == START_REGION_ID || game.state.ownsHome(reg.id)
            val homeLine = if (reg.id == START_REGION_ID && !game.state.started) "시작 집 제공"
            else if (hasHome) "보유한 집" else "집 매입 ${won(HousePrices.forRegion(reg.id))}"
            UiKit.icon(c, game, if (hasHome) "house" else "box", RectF(x, y - dp * 9f, x + dp * 13f, y + dp * 4f))
            c.drawText(homeLine, x + dp * 17f, y + dp * 2f, if (hasHome) homeOwnedPaint else homeBuyPaint)
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

    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        game.hud.questLabel = null
        game.hud.photoModeHint = false
    }

    override fun drawWorld(c: Canvas) {
        // 타이틀과 같은 960×540 기준 배경을 2160p 월드 버퍼에 맞춘다.
        // 예전에는 고정 540px까지만 그려져 배경의 나머지가 비어 있었다.
        val sceneScale = game.virtH / 540f
        c.save()
        c.scale(sceneScale, sceneScale)
        val p = Paint()
        val a = game.assets
        val W = game.virtW / sceneScale
        // 하늘
        p.color = 0xFFA4E4EE.toInt()
        c.drawRect(0f, 0f, W, 540f, p)
        p.color = 0xFFBCEAF0.toInt()
        c.drawRect(0f, 180f, W, 540f, p)
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
        // 풀 타일 바닥 (화면비에 맞춰 채운다)
        val grass = a.tiles[T.GRASS.ordinal]
        for (row in 13..16) for (col in 0 until ((W + 31f) / 32f).toInt()) {
            val variant = a.tileVariant(T.GRASS.ordinal, col, row)
            c.drawBitmap(grass[variant], col * 32f, row * 32f, a.sprPaint)
        }
        val t = game.time
        // 날아가는 새들
        val birds = listOf("sparrow", "gull", "greattit", "egret")
        for (i in 0 until 4) {
            val bx = (t * (16f + i * 6f) + i * 240f) % (W + 160f) - 80f
            val by = 60f + i * 34f + sin(t * 1.8f + i * 2f) * 9f
            c.drawBitmap(a.bird(birds[i]), bx, by, a.sprPaint)
        }
        c.drawBitmap(a.pizzaIcon, 34f, 474f + sin(t * 2f) * 3f, a.sprPaint)
        c.drawBitmap(a.birdFlipped("magpie"), 894f, 470f + sin(t * 2.4f) * 3f, a.sprPaint)
        c.drawBitmap(a.bird("crane"), 26f, 428f + sin(t * 1.6f) * 3f, a.sprPaint)
        c.restore()
    }

    override fun drawHud(c: Canvas) {
        val dp = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        backRect = RectF(dp * 14f, dp * 14f, dp * 90f, dp * 50f)
        UiKit.button(c, game, backRect, "arrow_left 뒤로", 0xFFF2E3C2.toInt(), 0xFF4A3728.toInt(), 12f)

        Type.sticker(c, "서울에서 시작해볼까요?", w / 2f, dp * 34f, Role.DISPLAY, Type.INK)
        Type.text(c, "시작 지역은 서울로 고정되어 있어요 · 다른 지역은 여행하며 집을 매입해요",
            w / 2f, dp * 54f, Role.CAPTION, Type.LEAF, 0.5f)

        // 하단 확인 바를 먼저 잡고, 남은 공간에 맞춰 히어로 카드를 올린다
        val barH = dp * 56f
        val bar = RectF(dp * 16f, h - barH - dp * 12f, w - dp * 16f, h - dp * 12f)
        val cardW = minOf(w - dp * 32f, dp * 560f)
        val cardTop = dp * 66f
        val maxCardH = bar.top - dp * 10f - cardTop
        val layout = heroLayout(cardW, maxCardH)
        area = RectF((w - cardW) / 2f, cardTop, (w + cardW) / 2f, cardTop + layout.totalH)
        cardRects = listOf(area)
        drawHeroCard(c, area, selected, layout)

        UiKit.panel(c, game, bar)
        Type.textCentered(c, "서울에 집을 마련하고 여행을 시작할게요", bar.left + dp * 16f, bar.centerY(), Role.BODY, Type.INK)
        val bw = dp * 160f
        confirmRect = RectF(bar.right - bw - dp * 12f, bar.top + dp * 10f, bar.right - dp * 12f, bar.bottom - dp * 10f)
        UiKit.button(c, game, confirmRect, "서울에서 시작!", 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 14.5f)
    }

    /** 히어로 카드의 작은 알약 칩 (서식지 · 추천 시기 · 가게/박사 안내) */
    private data class HeroChip(val label: String, val bg: Int, val fg: Int)

    /** drawHud 측정 → 그리기까지 같은 배치를 공유하기 위한 레이아웃 결과 */
    private data class HeroLayout(
        val pad: Float,
        val headerH: Float,
        val chipLines: List<List<HeroChip>>,
        val chipWidths: List<List<Float>>,
        val chipH: Float,
        val leftW: Float,
        val birdRowH: Float,
        val birdLabelH: Float,
        val descLines: List<String>,
        val tipLines: List<String>,
        val bodyH: Float,
        val totalH: Float
    )

    /**
     * 히어로 카드 배치 계산 — 글자는 크게, 공간은 넉넉하게.
     * 화면이 낮으면 탐조 팁부터 빼고 그래도 모자라면 카드를 납작하게 접는다.
     */
    private fun heroLayout(cardW: Float, maxH: Float): HeroLayout {
        val dp = game.density
        val reg = selected
        val pad = dp * 14f
        val contentW = cardW - pad * 2f

        // 칩 목록: 서식지 + 추천 시기 + (가게·박사가 있으면 안내)
        val chips = ArrayList<HeroChip>()
        for (hab in reg.habitats) {
            chips.add(HeroChip(HabitatLabels[hab] ?: hab, UiKit.PASTEL_MINT, 0xFF3E5A34.toInt()))
        }
        chips.add(HeroChip(reg.season, UiKit.PASTEL_SKY, 0xFF2E4F6B.toInt()))
        val regShop = CameraShops.shop(reg.id)
        if (regShop != null) {
            val chip = if (regShop.flagship) "카메라샵 본점 · 전 라인업" else "카메라샵 · ${regShop.specialty}"
            chips.add(HeroChip(chip, UiKit.PASTEL_LILAC, 0xFF5A4A7A.toInt()))
        }
        if (reg.id == NpcRoster.PROFESSOR_REGION) {
            chips.add(HeroChip("보리 박사", UiKit.PASTEL_PEACH, 0xFF7A4A22.toInt()))
        }
        // 칩 흐름 배치 (한 줄에 다 안 들어가면 다음 줄로)
        val chipPaint = Type.paintAt(11f, true, 0.02f, Type.INK)
        val gap = dp * 6f
        val lines = ArrayList<ArrayList<HeroChip>>()
        val widths = ArrayList<ArrayList<Float>>()
        var cur = ArrayList<HeroChip>()
        var curW = ArrayList<Float>()
        var curLineW = 0f
        for (chip in chips) {
            val cw = chipPaint.measureText(chip.label) + dp * 20f
            if (cur.isNotEmpty() && curLineW + gap + cw > contentW) {
                lines.add(cur); widths.add(curW)
                cur = ArrayList(); curW = ArrayList(); curLineW = 0f
            }
            if (cur.isNotEmpty()) curLineW += gap
            cur.add(chip); curW.add(cw); curLineW += cw
        }
        if (cur.isNotEmpty()) { lines.add(cur); widths.add(curW) }
        val chipRowH = dp * 24f
        val chipH = chipRowH * lines.size + gap * (lines.size - 1).coerceAtLeast(0)

        // 본문 두 단: 왼쪽 대표 새 / 오른쪽 소개 + 탐조 팁
        val leftW = (contentW * 0.40f).coerceIn(dp * 118f, dp * 180f)
        val rightW = contentW - leftW - dp * 12f
        val bodyPaint = Type.paint(Role.BODY, Type.INK)
        val tipPaint = Type.paint(Role.CAPTION, Type.SOFT)
        val bodyLH = Type.lineHeight(Role.BODY)
        val tipLH = Type.lineHeight(Role.CAPTION)

        var headerH = dp * 58f
        var birdLabelH = dp * 18f
        var birdRowH = dp * 25f
        var desc = Type.wrap(reg.desc, bodyPaint, rightW).take(2)
        // 팁은 앞에 전구 아이콘이 붙으니 그 자리만큼 좁게 감는다
        val tipW = (rightW - dp * 17f).coerceAtLeast(dp * 60f)
        var tip = if (reg.tip.isEmpty()) emptyList() else Type.wrap(reg.tip, tipPaint, tipW).take(2)
        fun bodyH(): Float {
            val birds = birdLabelH + birdRowH * 3f
            var text = bodyLH * desc.size
            if (tip.isNotEmpty()) text += dp * 8f + tipLH * tip.size
            return maxOf(birds, text)
        }
        fun total(): Float = pad * 2f + headerH + dp * 10f + chipH + dp * 8f + bodyH()
        // 1차: 팁을 빼고, 2차: 카드를 납작하게 — 그래도 모자라면 설명을 한 줄로
        if (total() > maxH) tip = emptyList()
        if (total() > maxH) {
            headerH = dp * 52f
            birdLabelH = dp * 16f
            birdRowH = dp * 23f
        }
        if (total() > maxH && desc.size > 1) desc = desc.take(1)

        return HeroLayout(
            pad, headerH, lines, widths, chipH, leftW, birdRowH, birdLabelH,
            desc, tip, bodyH(), total().coerceAtMost(maxH.coerceAtLeast(dp * 120f))
        )
    }

    /** 서울 히어로 카드 — 큰 이름 + 서식지 칩 + 대표 새 + 소개를 한눈에 */
    private fun drawHeroCard(c: Canvas, r: RectF, reg: RegionDef, layout: HeroLayout) {
        val dp = game.density
        UiKit.panel(c, game, r)
        val pad = layout.pad
        val lx = r.left + pad

        // 헤더: 지역 아이콘 + 큰 이름 + 영문/성격 + 시작 집 뱃지
        val iconR = (layout.headerH / 2f - dp * 2f).coerceAtLeast(dp * 20f)
        val iconCx = lx + iconR + dp * 2f
        val iconCy = r.top + pad + layout.headerH / 2f
        UiKit.iconCircle(c, game, iconCx, iconCy, iconR, reg.emoji, 0f, reg.kind.color)
        val nameX = lx + iconR * 2f + dp * 12f
        val namePaint = Type.paintAt(20f, true, 0.02f, Type.INK)
        val subPaint = Type.paint(Role.CAPTION, Type.SOFT)
        c.drawText(reg.name, nameX, iconCy - dp * 2f, namePaint)
        c.drawText("${reg.english} · ${reg.kind.label}", nameX, iconCy + dp * 18f, subPaint)
        val houseLabel = "시작 집 제공"
        val housePaint = Type.paintAt(11.5f, true, 0.02f, 0xFF4A2E12.toInt())
        // 글자가 가운데로 그려지니 아이콘 자리를 양쪽에 넉넉히 둔다
        val houseW = housePaint.measureText(houseLabel) + dp * 50f
        val houseH = dp * 26f
        val houseR = RectF(r.right - pad - houseW, iconCy - houseH / 2f, r.right - pad, iconCy + houseH / 2f)
        UiKit.badge(c, game, houseR, houseLabel, UiKit.GOLD, 0xFF4A2E12.toInt(), 11.5f)
        UiKit.icon(c, game, "house", RectF(houseR.left + dp * 8f, houseR.top + dp * 5f, houseR.left + dp * 24f, houseR.bottom - dp * 5f))

        // 구분선
        val divY = r.top + pad + layout.headerH + dp * 5f
        UiKit.divider(c, game, lx, r.right - pad, divY)

        // 칩 줄
        var chipY = divY + dp * 5f
        for (li in layout.chipLines.indices) {
            var cx = lx
            val line = layout.chipLines[li]
            for (ci in line.indices) {
                val chip = line[ci]
                val cw = layout.chipWidths[li][ci]
                UiKit.badge(
                    c, game, RectF(cx, chipY, cx + cw, chipY + dp * 24f),
                    chip.label, chip.bg, chip.fg, 11f
                )
                cx += cw + dp * 6f
            }
            chipY += dp * 30f
        }

        // 본문: 왼쪽 대표 새 / 오른쪽 소개 + 탐조 팁
        val bodyTop = divY + dp * 5f + layout.chipH + dp * 8f
        val birds = Regions.signatureBirds(reg)
        Type.text(c, "대표 새", lx, bodyTop + layout.birdLabelH - dp * 5f, Role.LABEL, Type.BROWN)
        for (i in 0 until 3) {
            if (i >= birds.size) break
            val rowCy = bodyTop + layout.birdLabelH + layout.birdRowH * i + layout.birdRowH / 2f
            drawBirdThumb(c, lx + layout.birdRowH / 2f, rowCy, layout.birdRowH - dp * 4f, birds[i])
            val np = Type.paint(Role.LABEL, Type.INK)
            var birdName = birds[i].name
            val maxNameW = layout.leftW - layout.birdRowH - dp * 6f
            while (birdName.length > 2 && np.measureText(birdName) > maxNameW) birdName = birdName.dropLast(1)
            if (birdName != birds[i].name) birdName = birdName.dropLast(1) + "…"
            c.drawText(birdName, lx + layout.birdRowH + dp * 4f, Type.midBaseline(np, rowCy), np)
        }
        val rightX = lx + layout.leftW + dp * 12f
        val bodyPaint = Type.paint(Role.BODY, Type.INK)
        val bodyLH = Type.lineHeight(Role.BODY)
        var ty = bodyTop + (bodyLH - bodyPaint.descent() - bodyPaint.ascent()) / 2f
        for (ln in layout.descLines) {
            c.drawText(ln, rightX, ty, bodyPaint)
            ty += bodyLH
        }
        if (layout.tipLines.isNotEmpty()) {
            val tipPaint = Type.paint(Role.CAPTION, Type.SOFT)
            val tipLH = Type.lineHeight(Role.CAPTION)
            val tipTop = bodyTop + bodyLH * layout.descLines.size + dp * 8f
            // 첫 줄 앞에 전구 아이콘 — 줄들은 아이콘 오른쪽에 가지런히 맞춘다
            val iconBox = dp * 13f
            UiKit.icon(c, game, "sun", RectF(rightX, tipTop, rightX + iconBox, tipTop + iconBox))
            val tipX = rightX + iconBox + dp * 4f
            var tty = tipTop + (tipLH - tipPaint.descent() - tipPaint.ascent()) / 2f
            for (ln in layout.tipLines) {
                c.drawText(ln, tipX, tty, tipPaint)
                tty += tipLH
            }
        }
    }

    /** 대표 새 미니 썸네일 — 크림 동그라미 + 픽셀 도트 */
    private fun drawBirdThumb(c: Canvas, cx: Float, cy: Float, size: Float, def: BirdDef) {
        val dp = game.density
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        fill.color = Color.argb(60, 60, 42, 22)
        c.drawCircle(cx, cy + dp * 1.5f, size / 2f, fill)
        fill.color = 0xFFFFFBEF.toInt()
        c.drawCircle(cx, cy, size / 2f, fill)
        stroke.color = 0xFFC9A87B.toInt()
        stroke.strokeWidth = dp * 1.2f
        c.drawCircle(cx, cy, size / 2f, stroke)
        val bmp = game.assets.bird(def.id)
        val box = size - dp * 6f
        val k = minOf(box / bmp.width.toFloat(), box / bmp.height.toFloat())
        val bw = bmp.width * k
        val bh = bmp.height * k
        c.drawBitmap(bmp, null, RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f), game.assets.sprPaint)
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
        PhotoArchive.clear(game.context)
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
    /** 전체 화면 패널 — 뒤 월드는 가려지니 재사용한다 */
    override val coversWorld: Boolean get() = true


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

        val t1 = "이사갈 곳을 골라요"
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
            for ((rr, lbl) in listOf(prevRect to "arrow_left 이전", nextRect to "arrow_right 다음")) {
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
