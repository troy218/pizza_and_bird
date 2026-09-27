package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * [P07] 굽기 ①단계 — **도우 선택** 오버레이.
 *
 * 집의 화덕/오븐에 말을 걸면 이 화면이 먼저 뜨고, 도우를 고르면 기존 [BakeOverlay]
 * (② 피자 선택 → ③ 타이밍 굽기)로 그대로 이어진다. 도우 가격은 고르는 순간 결제되고,
 * 도우의 속도/판정/보너스는 [BakeOverlay]가 [Dough] 값으로 적용한다.
 *
 * ⚠️ 이 파일은 ①만 담당한다 — 피자 선택(특산 탭 포함)은 `Overlays.kt`의 [BakeOverlay].
 */
class DoughOverlay(scene: Scene, private val kind: PizzaKind = PizzaKind.OVEN) : Overlay(scene) {

    private val tp = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply { letterSpacing = 0.01f }
    private val cardRects = ArrayList<Pair<RectF, Dough>>()
    private var cancelRect = RectF()

    private fun dp(v: Float): Float = v * scene.game.density

    override fun handleInput(input: Input) {
        if (input.justB || input.justBack) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        val tap = input.consumeTapScreen() ?: return
        for ((r, d) in cardRects) {
            if (r.contains(tap.x, tap.y)) {
                pick(d)
                return
            }
        }
        if (cancelRect.contains(tap.x, tap.y)) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
        }
    }

    private fun pick(d: Dough) {
        val g = scene.game
        val s = g.state
        if (d.price > 0) {
            if (s.money < d.price) {
                g.toast("${d.icon} ${d.label}은(는) ${won(d.price)} 필요해요… 돈이 부족해요")
                g.sfx(Audio.Sfx.FAIL, 0.5f)
                return
            }
            s.money -= d.price
            g.sfx(Audio.Sfx.BUY, 0.8f)
            g.toast("${d.icon} ${d.label} 반죽 완료! (${won(d.price)})")
        } else {
            g.sfx(Audio.Sfx.TAP, 0.7f)
        }
        Ingredients.setDough(g.context, d)
        SaveManager.save(g.context, s)
        // ② 피자 선택(+현재 지역 특산 탭) → ③ 타이밍 굽기로 이어 붙인다
        g.scene.openOverlay(BakeOverlay(g.scene, kind, d))
        finished = true
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        UiKit.dim(c, g, 130, bornAt)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val cw = minOf(w * 0.86f, dp(520f))
        val chh = minOf(h - dp(16f), dp(284f))
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        UiKit.panel(c, g, r)

        // 헤더 — 제목 + 보유금 + 조리기구 안내
        tp.textSize = dp(16f)
        tp.color = UiKit.BROWN
        val title = "${kind.emoji} 어떤 도우로 구울까요?"
        c.drawText(title, r.left + dp(16f), r.top + dp(26f), tp)
        tp.textSize = dp(10.5f)
        tp.color = UiKit.MUTED
        c.drawText("도우를 고르면 ${kind.station}에서 구울 피자를 정해요", r.left + dp(16f), r.top + dp(42f), tp)

        tp.textSize = dp(10.5f)
        val moneyTxt = "💰 ${won(s.money)}"
        val moneyW = tp.measureText(moneyTxt) + dp(18f)
        UiKit.badge(
            c, g, RectF(r.right - dp(14f) - moneyW, r.top + dp(14f), r.right - dp(14f), r.top + dp(34f)),
            moneyTxt, UiKit.GOLD, 0xFF4A2E12.toInt(), 10.5f
        )
        UiKit.divider(c, g, r.left + dp(14f), r.right - dp(14f), r.top + dp(50f))

        // 도우 카드 3장
        cardRects.clear()
        val doughs = Dough.values()
        val gap = dp(10f)
        val top = r.top + dp(60f)
        val bottomArea = r.bottom - dp(54f)   // 풋터 힌트 줄 공간 확보
        val cardW = (r.width() - dp(14f) * 2 - gap * (doughs.size - 1)) / doughs.size
        val cardH = bottomArea - top
        val last = Ingredients.lastDough(g.context)
        for ((i, d) in doughs.withIndex()) {
            val cr = RectF(
                r.left + dp(14f) + i * (cardW + gap), top,
                r.left + dp(14f) + i * (cardW + gap) + cardW, top + cardH
            )
            val afford = d.price <= 0 || s.money >= d.price
            val tint = if (!afford) UiKit.CARD_LO
            else if (d == last) UiKit.lighten(kind.tint, 78)
            else UiKit.CARD_HI
            UiKit.stitchCard(
                c, g, cr, tint,
                if (d == last && afford) kind.tint else UiKit.BROWN_LINE,
                if (d == last && afford) 2.2f else 1.6f,
                stitched = cr.width() > dp(70f)
            )
            cardRects.add(cr to d)

            val cx = cr.centerX()
            // 아이콘 원 + 이름
            UiKit.iconCircle(c, g, cx, cr.top + dp(26f), dp(15f), d.icon, 16f,
                if (afford) UiKit.GOLD else Color.argb(150, 200, 190, 175))
            tp.textSize = dp(11.5f)
            tp.color = if (afford) UiKit.BROWN else UiKit.MUTED
            c.drawText(d.label, cx - tp.measureText(d.label) / 2f, cr.top + dp(58f), tp)

            // 가격 뱃지
            tp.textSize = dp(9.5f)
            val priceTxt = d.priceLabel
            val pw = tp.measureText(priceTxt) + dp(14f)
            UiKit.badge(
                c, g, RectF(cx - pw / 2f, cr.top + dp(64f), cx + pw / 2f, cr.top + dp(80f)),
                priceTxt,
                if (d.price <= 0) UiKit.GREEN else if (afford) UiKit.GOLD else Color.argb(120, 200, 190, 175),
                if (d.price <= 0) 0xFFFFF8E8.toInt() else if (afford) 0xFF4A2E12.toInt() else Color.argb(150, 74, 55, 40),
                9.5f
            )

            // 굽기 속도(난이도) + 효과
            tp.textSize = dp(9f)
            tp.color = UiKit.MUTED
            val speedTxt = "굽기 속도 ×${"%.2f".format(d.gaugeSpeed)}"
            c.drawText(speedTxt, cx - tp.measureText(speedTxt) / 2f, cr.top + dp(95f), tp)
            tp.textSize = dp(9.5f)
            tp.color = 0xFFE8830C.toInt()
            c.drawText(d.difficultyDots(), cx - tp.measureText(d.difficultyDots()) / 2f, cr.top + dp(108f), tp)

            tp.textSize = dp(9.5f)
            tp.color = if (afford) UiKit.GREEN_DEEP else UiKit.MUTED
            val bonus = d.bonusLabel
            c.drawText(bonus, cx - tp.measureText(bonus) / 2f, cr.top + dp(124f), tp)

            // 설명 — 카드 폭에 맞춰 최대 3줄 (카드 안에 들어가도록 간격 압축)
            tp.textSize = dp(8.5f)
            tp.color = UiKit.MUTED
            var ty = cr.top + dp(134f)
            for (line in wrap(d.desc, cr.width() - dp(16f), 3)) {
                c.drawText(line, cx - tp.measureText(line) / 2f, ty, tp)
                ty += dp(10.5f)
            }

            // 직전 선택 표시
            if (d == last) {
                tp.textSize = dp(8.5f)
                val tag = "지난번"
                val tw = tp.measureText(tag) + dp(10f)
                UiKit.badge(
                    c, g, RectF(cr.right - dp(6f) - tw, cr.top + dp(5f), cr.right - dp(6f), cr.top + dp(18f)),
                    tag, kind.tint, 0xFFFFFBF0.toInt(), 8.5f
                )
            }
        }

        // 풋터 — 그만두기 + 힌트
        cancelRect = RectF(r.centerX() - dp(60f), r.bottom - dp(38f), r.centerX() + dp(60f), r.bottom - dp(12f))
        UiKit.button(c, g, cancelRect, "그만두기", 0xFFF2E3C2.toInt(), UiKit.BROWN_MID, 12f)
        tp.textSize = dp(9.5f)
        tp.color = UiKit.MUTED
        val hint = "탭하면 결제 후 피자 선택으로 · 굽기 전 취소하면 환불"
        c.drawText(hint, r.right - dp(14f) - tp.measureText(hint), r.bottom - dp(44f), tp)
    }

    /** 글자를 폭에 맞춰 자른다 (maxLines 초과분은 …) */
    private fun wrap(text: String, maxW: Float, maxLines: Int): List<String> {
        val out = ArrayList<String>()
        var rest = text
        while (rest.isNotEmpty() && out.size < maxLines) {
            var n = rest.length
            while (n > 1 && tp.measureText(rest.take(n)) > maxW) n--
            var line = rest.take(n)
            rest = rest.drop(n)
            if (rest.isNotEmpty() && out.size == maxLines - 1) line = line.dropLast(1) + "…"
            out.add(line)
        }
        return out
    }
}
