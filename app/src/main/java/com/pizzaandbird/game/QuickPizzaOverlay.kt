package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * [P11] **빠른 피자 창** — 🍕 버튼이 먹을 피자를 골라 등록해 두는 화면.
 *
 * 🍕 버튼(또는 E 키)은 **등록한 피자의 한 조각**을 먹는다. 등록이 없으면 예전처럼
 * 배낭에서 제일 좋은 조각을 자동으로 고른다. 이 창은
 * - 🍕 버튼을 **길게 누르거나**(터치) **Q 키**를 눌러 연다 ([Input.EAT_HOLD_MS])
 * - 메뉴 › 피자 탭의 **⚡ 피자 고르기** 배너를 탭해도 열린다
 *
 * 창에서는 피자별 **남은 조각 수**(한 판 = 8조각), **특성**([PizzaTrait]),
 * 조각 하나당 회복량을 한눈에 보고 ⚡ 등록 / 🍕 한 조각 먹기를 고른다.
 * 등록은 세이브되므로(`GameState.quickPizzaId`) 다음 실행에도 그대로다.
 */
class QuickPizzaOverlay(scene: Scene) : Overlay(scene) {

    /** 화면 대부분을 덮으니 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true

    private val tp = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply { letterSpacing = 0.01f }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 0 = 🔥 화덕피자 · 1 = 🍕 일반 피자 · 2 = ✈ 특산 (창을 닫았다 열어도 유지) */
    private var tab = lastTab

    private val tabRects = ArrayList<Pair<RectF, Int>>()
    private val regRects = ArrayList<Pair<RectF, Int>>()
    private val eatRects = ArrayList<Pair<RectF, Int>>()
    private var clearRect = RectF()
    private var closeRect = RectF()

    private fun dp(v: Float): Float = v * scene.game.density
    private fun tdp(v: Float): Float = TypeScale.px(dp(v))

    private fun titles(): List<Triple<Int, String, Int>> {
        val s = scene.game.state
        return listOf(
            Triple(0, "${PizzaKind.OVEN.emoji} ${PizzaKind.OVEN.label}", s.slicesOfKind(PizzaKind.OVEN) - specialSlices()),
            Triple(1, "${PizzaKind.REGULAR.emoji} ${PizzaKind.REGULAR.label}", s.slicesOfKind(PizzaKind.REGULAR)),
            Triple(2, "✈ 특산", specialSlices())
        )
    }

    private fun specialSlices(): Int {
        val s = scene.game.state
        var n = 0
        for (id in Ingredients.SPECIAL_PIZZA_IDS) n += s.slicesOf(id)
        return n
    }

    /** 이 탭에 늘어놓을 피자 목록 (표시 순서는 `Pizzas.ALL`과 같다) */
    private fun list(): List<PizzaDef> = when (tab) {
        0 -> Pizzas.ofKind(PizzaKind.OVEN).filter { !Ingredients.isSpecial(it.id) }
        1 -> Pizzas.ofKind(PizzaKind.REGULAR)
        else -> Pizzas.ALL.filter { Ingredients.isSpecial(it.id) }
    }

    // -------------------------------------------------------------------
    // 입력
    // -------------------------------------------------------------------

    override fun handleInput(input: Input) {
        if (input.justB || input.justBack) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        val tap = input.consumeTapScreen() ?: return
        for ((r, t) in tabRects) {
            if (r.contains(tap.x, tap.y)) {
                if (tab != t) {
                    tab = t
                    lastTab = t
                    scene.game.sfx(Audio.Sfx.TAP, 0.6f)
                }
                return
            }
        }
        for ((r, id) in regRects) {
            if (r.contains(tap.x, tap.y)) { toggleRegister(id); return }
        }
        for ((r, id) in eatRects) {
            if (r.contains(tap.x, tap.y)) { eatOne(id); return }
        }
        if (clearRect.contains(tap.x, tap.y)) {
            toggleRegister(-1)
            return
        }
        if (closeRect.contains(tap.x, tap.y)) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
        }
    }

    /** 빠른 피자 등록/해제 (이미 등록한 피자를 누르면 해제) */
    private fun toggleRegister(pizzaId: Int) {
        val g = scene.game
        val s = g.state
        if (pizzaId < 0 || s.quickPizzaId == pizzaId) {
            s.setQuickPizza(-1)
            SaveManager.save(g.context, s)
            g.sfx(Audio.Sfx.TAP, 0.6f)
            g.toast("⚡ 빠른 피자 등록을 해제했어요 — 🍕 버튼은 제일 좋은 조각을 골라요")
            return
        }
        s.setQuickPizza(pizzaId)
        SaveManager.save(g.context, s)
        val p = Pizzas.of(pizzaId)
        val left = s.slicesOf(pizzaId)
        g.sfx(Audio.Sfx.NOTIFY, 0.75f)
        g.toast(
            if (left > 0) "⚡ 빠른 피자 등록! ${p.emoji} ${p.fullName} — 남은 $left 조각"
            else "⚡ 빠른 피자 등록! ${p.emoji} ${p.fullName} — 구워 두면 🍕 버튼이 이 피자부터 먹어요"
        )
    }

    /** 한 조각 먹기 */
    private fun eatOne(pizzaId: Int) {
        val g = scene.game
        val s = g.state
        val p = Pizzas.of(pizzaId)
        val bite = s.eatSlice(pizzaId)
        if (bite == null) {
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            g.toast("${p.emoji} ${p.name}은(는) 남은 조각이 없어요 — 집에서 한 판 구워 오세요")
            return
        }
        SaveManager.save(g.context, s)
        g.sfx(Audio.Sfx.EAT, 0.9f)
        g.toast(
            "냠냠! ${bite.label} · 배고픔 +${bite.hunger} 행운 +${bite.luck}" +
                " · ${p.name} 남은 ${bite.slicesLeft}조각"
        )
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        UiKit.dim(c, g, 140, bornAt)

        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val cw = minOf(w * 0.92f, dp(600f))
        val chh = minOf(h - dp(10f), dp(420f))
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        UiKit.panel(c, g, r)

        val padX = dp(14f)
        var ty = r.top + dp(20f)

        // ---- 헤더 ----
        tp.textSize = tdp(15f)
        tp.color = UiKit.BROWN
        UiKit.drawIconText(c, g, "⚡ 빠른 피자 고르기", r.left + padX, ty, tp)
        tp.textSize = tdp(9f)
        tp.color = UiKit.MUTED
        UiKit.drawIconText(
            c, g, "🍕 버튼을 짧게 누르면 등록한 피자의 한 조각을 먹어요 · 길게 누르면 이 창이 다시 열려요",
            r.left + padX, ty + dp(13f), tp
        )
        // 배낭 요약 (오른쪽)
        tp.textSize = tdp(9.5f)
        tp.color = UiKit.GOLD_DEEP
        val bagTxt = "배낭 ${s.pizzaCount}/${s.pizzaCapEff()}판 · ${s.sliceCount}조각"
        UiKit.drawIconText(c, g, bagTxt, r.right - padX - UiKit.iconTextWidth(bagTxt, tp), ty, tp)
        ty += dp(20f)
        UiKit.divider(c, g, r.left + padX, r.right - padX, ty)
        ty += dp(8f)

        // ---- 등록 상태 배너 ----
        val banH = dp(30f)
        val banR = RectF(r.left + padX, ty, r.right - padX, ty + banH)
        val quick = s.quickPizza()
        val banTint = if (quick != null) UiKit.lighten(quick.kind.tint, 78) else UiKit.CREAM_HI
        UiKit.stitchCard(c, g, banR, banTint, if (quick != null) quick.kind.tint else UiKit.BROWN_LINE, 1.6f, stitched = false)
        clearRect = RectF()
        if (quick != null) {
            val artSz = banH - dp(10f)
            c.drawBitmap(
                a.pizzaArt(quick.id), null,
                RectF(banR.left + dp(6f), banR.centerY() - artSz / 2f, banR.left + dp(6f) + artSz, banR.centerY() + artSz / 2f),
                a.sprPaint
            )
            val tx = banR.left + dp(10f) + artSz
            tp.textSize = tdp(10.5f)
            tp.color = UiKit.BROWN
            val left = s.slicesOf(quick.id)
            val head = "등록됨 · ${quick.emoji} ${quick.fullName}"
            UiKit.drawIconText(c, g, head, tx, banR.centerY() - dp(1f), tp)
            tp.textSize = tdp(9f)
            tp.color = if (left > 0) UiKit.GREEN_DEEP else UiKit.RED_DEEP
            val sub = if (left > 0) "남은 $left 조각 · ${quick.traitChips}" else "재고 없음 — 구워 두면 다시 이 피자를 먹어요 · ${quick.traitChips}"
            drawFit(c, sub, tx, banR.centerY() + dp(11f), banR.width() - dp(90f) - artSz, 9f, 7f)
            // 해제 버튼
            val bw = dp(66f)
            clearRect = RectF(banR.right - dp(8f) - bw, banR.centerY() - dp(11f), banR.right - dp(8f), banR.centerY() + dp(11f))
            UiKit.cuteButton(c, g, clearRect, "등록 해제", UiKit.PASTEL_SAND, UiKit.BROWN_MID, 9.5f, depthDp = 2f)
        } else {
            tp.textSize = tdp(10.5f)
            tp.color = UiKit.BROWN_MID
            UiKit.drawIconText(
                c, g, "등록된 피자가 없어요 — 아래에서 ⚡ 등록을 누르면 🍕 버튼이 그 피자를 기억해요",
                banR.left + dp(12f), banR.centerY() + dp(4f), tp
            )
        }
        ty += banH + dp(8f)

        // ---- 탭 ----
        val tabs = titles()
        val tabH = dp(24f)
        val tabGap = dp(6f)
        val tabW = (r.width() - padX * 2 - tabGap * (tabs.size - 1)) / tabs.size
        tabRects.clear()
        for ((i, t) in tabs.withIndex()) {
            val tr = RectF(r.left + padX + i * (tabW + tabGap), ty, r.left + padX + i * (tabW + tabGap) + tabW, ty + tabH)
            val sel = i == tab
            val label = "${t.second} ${t.third}조각"
            val base = if (sel) (if (i == 1) PizzaKind.REGULAR.tint else PizzaKind.OVEN.tint) else UiKit.PASTEL_SAND
            UiKit.cuteButton(c, g, tr, label, base, if (sel) 0xFFFFFBF0.toInt() else UiKit.MUTED, 10f, depthDp = 2f)
            tabRects.add(tr to t.first)
        }
        ty += tabH + dp(7f)

        // ---- 피자 카드 (2열 그리드) ----
        regRects.clear()
        eatRects.clear()
        val items = list()
        val cols = 2
        val rows = ((items.size + cols - 1) / cols).coerceAtLeast(1)
        val gap = dp(6f)
        val footH = dp(36f)
        val avail = r.bottom - dp(8f) - footH - ty
        val cellW = (r.width() - padX * 2 - gap * (cols - 1)) / cols
        val cellH = (avail - gap * (rows - 1)) / rows
        val roomy = cellH >= dp(52f)
        for ((i, p) in items.withIndex()) {
            val col = i % cols
            val row = i / cols
            val cr = RectF(
                r.left + padX + col * (cellW + gap), ty + row * (cellH + gap),
                r.left + padX + col * (cellW + gap) + cellW, ty + row * (cellH + gap) + cellH
            )
            val slices = s.slicesOf(p.id)
            val pans = s.pizzaCountOf(p.id)
            val isQuick = s.quickPizzaId == p.id
            val tint = when {
                isQuick -> UiKit.lighten(p.kind.tint, 74)
                slices > 0 -> UiKit.CARD_HI
                else -> UiKit.CARD_LO
            }
            UiKit.stitchCard(
                c, g, cr, tint,
                if (isQuick) p.kind.tint else if (slices > 0) UiKit.BROWN_LINE else Color.argb(140, 201, 168, 123),
                if (isQuick) 2.4f else 1.5f, stitched = false, selected = isQuick
            )

            // 아이콘
            val artW = minOf(dp(34f), cellH - dp(10f)).coerceAtLeast(dp(22f))
            if (slices > 0) {
                fillP.color = Color.argb(60, 255, 255, 255)
                c.drawCircle(cr.left + dp(7f) + artW / 2f, cr.centerY(), artW * 0.62f, fillP)
            }
            c.drawBitmap(
                a.pizzaArt(p.id), null,
                RectF(cr.left + dp(7f), cr.centerY() - artW / 2f, cr.left + dp(7f) + artW, cr.centerY() + artW / 2f),
                a.sprPaint
            )

            // 오른쪽 버튼 두 개 (⚡ 등록 / 🍕 한 조각)
            val btnW = dp(60f)
            val btnH = if (roomy) dp(20f) else dp(17f)
            val bx0 = cr.right - dp(7f) - btnW
            val reg = RectF(bx0, cr.centerY() - btnH - dp(2f), bx0 + btnW, cr.centerY() - dp(2f))
            val eat = RectF(bx0, cr.centerY() + dp(2f), bx0 + btnW, cr.centerY() + dp(2f) + btnH)
            if (isQuick) {
                UiKit.cuteButton(c, g, reg, "⚡ 등록됨", p.kind.tint, 0xFFFFFBF0.toInt(), 9f, depthDp = 2f)
            } else {
                UiKit.cuteButton(c, g, reg, "⚡ 등록", UiKit.PASTEL_LEMON, UiKit.BROWN_MID, 9f, depthDp = 2f)
            }
            regRects.add(reg to p.id)
            if (slices > 0) {
                UiKit.cuteButton(c, g, eat, "🍕 한 조각", UiKit.PASTEL_TOMATO, 0xFF4A2E12.toInt(), 9f, depthDp = 2f)
            } else {
                UiKit.cuteButton(c, g, eat, "재고 없음", Color.argb(120, 200, 190, 175), Color.argb(150, 74, 55, 40), 9f, depthDp = 2f)
            }
            eatRects.add(eat to p.id)

            // 글 블록
            val tx = cr.left + dp(9f) + artW + dp(6f)
            val textW = bx0 - dp(6f) - tx
            var ly = cr.top + dp(13f)
            // 이름 + 조각 뱃지
            tp.textSize = tdp(10f)
            tp.color = if (slices > 0) UiKit.BROWN else UiKit.MUTED
            val nameTxt = "${p.emoji} ${p.name}"
            val badgeTxt = if (pans > 0) "$slices 조각 · $pans 판" else "없음"
            tp.textSize = tdp(8.5f)
            val badgeW = UiKit.iconTextWidth(badgeTxt, tp) + dp(10f)
            drawFit(c, nameTxt, tx, ly, textW - badgeW - dp(4f), 10.5f, 8f)
            UiKit.badge(
                c, g, RectF(tx + textW - badgeW, ly - dp(9f), tx + textW, ly + dp(3f)),
                badgeTxt,
                if (slices > 0) UiKit.GOLD else Color.argb(150, 200, 190, 175),
                if (slices > 0) 0xFF4A2E12.toInt() else Color.argb(160, 74, 55, 40), 8.5f
            )
            // 특성
            ly += dp(12f)
            tp.color = p.kind.tint
            drawFit(c, p.traitChips, tx, ly, textW, 9f, 7f)
            // 조각당 효과
            if (roomy) {
                ly += dp(12f)
                tp.color = UiKit.MUTED
                val qIdx = PizzaSlices.bestQualityOf(s, p.id).let { if (it < 0) 1 else it }
                val eff = "${PizzaSlices.sliceLine(p, PizzaQ.of(qIdx))} · ${PizzaQ.of(qIdx).label}"
                drawFit(c, eff, tx, ly, textW, 8.5f, 7f)
            }
        }

        // ---- 풋터 ----
        closeRect = RectF(r.centerX() - dp(56f), r.bottom - dp(30f), r.centerX() + dp(56f), r.bottom - dp(8f))
        UiKit.button(c, g, closeRect, "닫기", 0xFFF2E3C2.toInt(), UiKit.BROWN_MID, 11.5f)
        tp.textSize = tdp(9f)
        tp.color = UiKit.MUTED
        val hint = "한 판 = ${PizzaSlices.PER_PIZZA}조각 · 특성은 조각 효과와 굽기 난이도에 그대로 반영돼요"
        UiKit.drawIconText(c, g, hint, r.right - padX - UiKit.iconTextWidth(hint, tp), r.bottom - dp(34f), tp)
        tp.textSize = tdp(9f)
        tp.color = UiKit.MUTED
        UiKit.drawIconText(c, g, "⚡ 등록 · 🍕 한 조각", r.left + padX, r.bottom - dp(34f), tp)
    }

    /** 폭에 맞춰 글자 크기를 줄여 그린다 (이모지는 SVG 아이콘으로 치환) */
    private fun drawFit(c: Canvas, text: String, x: Float, y: Float, maxW: Float, sizeDp: Float, minDp: Float = 7.5f) {
        var sz = sizeDp
        tp.textSize = tdp(sz)
        while (UiKit.iconTextWidth(text, tp) > maxW && sz > minDp) {
            sz -= 0.5f
            tp.textSize = tdp(sz)
        }
        UiKit.drawIconText(c, scene.game, text, x, y, tp)
    }

    companion object {
        /** 마지막으로 보던 탭 (창을 닫았다 열어도 유지) */
        private var lastTab = 0
    }
}

// ---------------------------------------------------------------------------
// [P11] 🍕 버튼 연결 — 월드/집/랜드마크 세 장면이 같은 규칙을 쓴다
// ---------------------------------------------------------------------------

/** 🍕 버튼을 **길게** 눌렀을 때(또는 Q 키): 빠른 피자 창을 연다 */
fun Scene.openQuickPizza() {
    game.sfx(Audio.Sfx.BAG_OPEN, 0.6f)
    openOverlay(QuickPizzaOverlay(this))
}

/**
 * 🍕 버튼(또는 E 키): 등록한 **빠른 피자**의 한 조각을 먹고, 등록이 없거나 품절이면
 * 배낭에서 제일 좋은 조각을 대신 먹는다. 결과는 토스트로 숫자까지 그대로 알려 준다.
 */
fun Scene.quickEatSlice() {
    val g = game
    val s = g.state
    val bite = s.eatQuick()
    if (bite == null) {
        g.toast("피자가 없어요! 집에서 한 판 구워 와요")
        g.sfx(Audio.Sfx.FAIL, 0.45f)
        return
    }
    SaveManager.save(g.context, s)
    g.sfx(Audio.Sfx.EAT, 0.9f)
    val p = bite.def
    val tail = when {
        bite.panFinished && bite.slicesLeft == 0 -> " — 한 판을 다 비웠어요!"
        bite.panFinished -> " — 한 판 끝! 남은 ${bite.slicesLeft}조각"
        else -> " · 남은 ${bite.slicesLeft}조각"
    }
    val note = if (!bite.fromQuick && s.quickPizzaId >= 0) " (등록한 피자는 다 떨어졌어요)" else ""
    g.toast("냠냠! ${p.emoji} ${p.name} (${bite.q.label}) — 배고픔 +${bite.hunger} 행운 +${bite.luck}$tail$note")
}
