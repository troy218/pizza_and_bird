package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock

/**
 * 프리미엄 UI 키트 — 모든 창/버튼/게이지의 공통 디자인 시스템.
 *
 * 따뜻한 크림 + 골드 + 초코 브라운의 고급 힐링 게임 룩으로 통일한다.
 * Canvas 직접 그리기에서도 대기업 수준의 깊이감(섀도우/그라데이션/글로스)을 낸다.
 */
object UiKit {

    // ------------------------------------------------------------------
    // 팔레트
    // ------------------------------------------------------------------
    val CREAM_HI = 0xFFFFFCF3.toInt()
    val CREAM = 0xFFF8EFDC.toInt()
    val CREAM_DEEP = 0xFFEFDDB8.toInt()
    val CARD_HI = 0xFFFFFBEF.toInt()
    val CARD_LO = 0xFFF5E7CA.toInt()
    val CARD_SEL_HI = 0xFFFFF6DC.toInt()
    val CARD_SEL_LO = 0xFFF8E2B0.toInt()
    val INK = 0xFF2E2118.toInt()
    val BROWN = 0xFF4A3728.toInt()
    val BROWN_MID = 0xFF6B4F35.toInt()
    val BROWN_LINE = 0xFFC9A87B.toInt()
    val GOLD = 0xFFF2B63C.toInt()
    val GOLD_HI = 0xFFFFD97A.toInt()
    val GOLD_DEEP = 0xFFB5651D.toInt()
    val GOLD_DARK = 0xFF8A5A1E.toInt()
    val GREEN = 0xFF6FBA6B.toInt()
    val GREEN_DEEP = 0xFF4E8A4E.toInt()
    val RED = 0xFFE2574C.toInt()
    val RED_DEEP = 0xFFB03A30.toInt()
    val BLUE = 0xFF3F6FB0.toInt()
    val MUTED = 0xFF8A7360.toInt()
    val TRACK = 0xFFD9C6A3.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ------------------------------------------------------------------
    // 등장 애니메이션 (0..1, easeOutCubic)
    // ------------------------------------------------------------------
    fun enter(bornAt: Long, durMs: Long = 220L): Float {
        if (bornAt <= 0L) return 1f
        val t = ((SystemClock.uptimeMillis() - bornAt).toFloat() / durMs).coerceIn(0f, 1f)
        val u = 1f - t
        return 1f - u * u * u
    }

    fun enterShift(game: Game, bornAt: Long, maxDp: Float = 16f): Float {
        return (1f - enter(bornAt)) * maxDp * game.density
    }

    // ------------------------------------------------------------------
    // 색상 유틸
    // ------------------------------------------------------------------
    fun lighten(c: Int, amt: Int): Int {
        return Color.argb(
            Color.alpha(c),
            (Color.red(c) + amt).coerceAtMost(255),
            (Color.green(c) + amt).coerceAtMost(255),
            (Color.blue(c) + amt).coerceAtMost(255)
        )
    }

    fun darken(c: Int, amt: Int): Int {
        return Color.argb(
            Color.alpha(c),
            (Color.red(c) - amt).coerceAtLeast(0),
            (Color.green(c) - amt).coerceAtLeast(0),
            (Color.blue(c) - amt).coerceAtLeast(0)
        )
    }

    // ------------------------------------------------------------------
    // 배경 딤 (등장 페이드 포함)
    // ------------------------------------------------------------------
    fun dim(c: Canvas, game: Game, baseAlpha: Int, bornAt: Long) {
        val k = 0.35f + 0.65f * enter(bornAt, 180L)
        val a = (baseAlpha * k).toInt().coerceIn(0, 255)
        fill.shader = null
        fill.color = Color.argb(a, 22, 16, 30)
        c.drawRect(0f, 0f, game.screenW.toFloat(), game.screenH.toFloat(), fill)
    }

    // ------------------------------------------------------------------
    // 메인 패널 — 깊은 섀도우 + 크림 그라데이션 + 이중 테두리 + 상단 광택
    // ------------------------------------------------------------------
    fun panel(c: Canvas, game: Game, r: RectF, radiusDp: Float = 14f) {
        val d = game.density
        val radius = radiusDp * d
        fill.shader = null
        // 부드러운 드롭 섀도우 (2단 레이어로 번짐 표현)
        fill.color = Color.argb(36, 30, 20, 12)
        c.drawRoundRect(
            RectF(r.left - 1f * d, r.top + 3f * d, r.right + 1f * d, r.bottom + 8f * d),
            radius, radius, fill
        )
        fill.color = Color.argb(54, 30, 20, 12)
        c.drawRoundRect(
            RectF(r.left, r.top + 2f * d, r.right, r.bottom + 4f * d),
            radius, radius, fill
        )
        // 본문 그라데이션
        fill.shader = LinearGradient(
            r.left, r.top, r.left, r.bottom,
            CREAM_HI, CREAM_DEEP, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        // 상단 하이라이트 라인
        fill.color = Color.argb(120, 255, 255, 255)
        c.drawRoundRect(
            RectF(r.left + 10f * d, r.top + 2.5f * d, r.right - 10f * d, r.top + 5f * d),
            2f * d, 2f * d, fill
        )
        // 외곽선 + 이너 헤어라인
        stroke.color = BROWN_MID
        stroke.strokeWidth = 2.5f * d
        c.drawRoundRect(r, radius, radius, stroke)
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 1f * d
        val inset = 3.2f * d
        c.drawRoundRect(
            RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset),
            (radiusDp - 3.2f).coerceAtLeast(4f) * d, (radiusDp - 3.2f).coerceAtLeast(4f) * d, stroke
        )
    }

    // ------------------------------------------------------------------
    // 카드 (리스트 행/도감 셀/상점 행 공용)
    // ------------------------------------------------------------------
    fun card(
        c: Canvas, game: Game, r: RectF,
        radiusDp: Float = 10f,
        selected: Boolean = false,
        borderColor: Int = BROWN_LINE,
        borderWidthDp: Float = 1.5f
    ) {
        val d = game.density
        val radius = radiusDp * d
        fill.shader = null
        // 섀도우
        fill.color = Color.argb(if (selected) 55 else 34, 60, 42, 22)
        c.drawRoundRect(
            RectF(r.left, r.top + 1.5f * d, r.right, r.bottom + 2.5f * d),
            radius, radius, fill
        )
        // 본문
        val top = if (selected) CARD_SEL_HI else CARD_HI
        val bottom = if (selected) CARD_SEL_LO else CARD_LO
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        // 상단 광택
        fill.color = Color.argb(90, 255, 255, 255)
        c.drawRoundRect(
            RectF(r.left + 6f * d, r.top + 1.5f * d, r.right - 6f * d, r.top + 3.5f * d),
            1.5f * d, 1.5f * d, fill
        )
        // 테두리
        stroke.color = borderColor
        stroke.strokeWidth = borderWidthDp * d
        c.drawRoundRect(r, radius, radius, stroke)
    }

    // ------------------------------------------------------------------
    // 버튼 — 베이스 색상에서 그라데이션/테두리를 자동 파생
    // ------------------------------------------------------------------
    fun button(
        c: Canvas, game: Game, r: RectF, label: String,
        base: Int, textCol: Int, textSizeDp: Float
    ) {
        val d = game.density
        val radius = 9f * d
        fill.shader = null
        // 비활성 (호출부가 반투명 색을 넘기는 관례 유지)
        if (Color.alpha(base) < 200) {
            fill.color = base
            c.drawRoundRect(r, radius, radius, fill)
            stroke.color = Color.argb(110, 140, 125, 105)
            stroke.strokeWidth = 1.5f * d
            c.drawRoundRect(r, radius, radius, stroke)
            drawCenterText(c, game, label, r, textSizeDp, textCol, shadow = false)
            return
        }
        // 섀도우
        fill.color = Color.argb(66, 50, 30, 12)
        c.drawRoundRect(RectF(r.left, r.top + 2f * d, r.right, r.bottom + 2.5f * d), radius, radius, fill)
        // 그라데이션 본문
        fill.shader = LinearGradient(
            r.left, r.top, r.left, r.bottom,
            lighten(base, 34), darken(base, 16), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        // 글로스 (위 45%)
        if (r.height() > 14f * d) {
            fill.color = Color.argb(66, 255, 255, 255)
            c.drawRoundRect(
                RectF(r.left + 2.5f * d, r.top + 1.8f * d, r.right - 2.5f * d, r.top + r.height() * 0.44f),
                6f * d, 6f * d, fill
            )
        }
        // 테두리 (베이스를 진하게) + 이너 광택선
        stroke.color = darken(base, 74)
        stroke.strokeWidth = 1.8f * d
        c.drawRoundRect(r, radius, radius, stroke)
        stroke.color = Color.argb(80, 255, 255, 255)
        stroke.strokeWidth = 1f * d
        val inset = 2.4f * d
        if (r.height() > inset * 2f + 4f * d) {
            c.drawRoundRect(
                RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset),
                7f * d, 7f * d, stroke
            )
        }
        drawCenterText(c, game, label, r, textSizeDp, textCol, shadow = true)
    }

    // ------------------------------------------------------------------
    // 원형 아이콘 버튼 (닫기 X / 줌 +,- 공용)
    // ------------------------------------------------------------------
    fun circleButton(
        c: Canvas, game: Game, cx: Float, cy: Float, radius: Float,
        glyph: String, glyphSizeDp: Float,
        base: Int = CREAM, glyphCol: Int = BROWN_MID
    ) {
        val d = game.density
        fill.shader = null
        fill.color = Color.argb(60, 40, 26, 12)
        c.drawCircle(cx, cy + 2f * d, radius, fill)
        fill.shader = LinearGradient(cx, cy - radius, cx, cy + radius, lighten(base, 26), darken(base, 14), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        stroke.color = darken(base, 74)
        stroke.strokeWidth = 1.8f * d
        c.drawCircle(cx, cy, radius, stroke)
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 1f * d
        c.drawCircle(cx, cy, radius - 2.4f * d, stroke)
        val gp = Type.paintAt(glyphSizeDp, true, 0.02f, glyphCol)
        c.drawText(glyph, cx - gp.measureText(glyph) / 2f, Type.midBaseline(gp, cy), gp)
    }

    // ------------------------------------------------------------------
    // 게이지 바 — 트랙 + 그라데이션 필 + 광택
    // ------------------------------------------------------------------
    fun bar(
        c: Canvas, game: Game,
        x: Float, y: Float, w: Float, h: Float,
        frac: Float, c0: Int, c1: Int
    ) {
        val d = game.density
        val r = RectF(x, y, x + w, y + h)
        fill.shader = null
        // 트랙
        fill.color = TRACK
        c.drawRoundRect(r, h / 2f, h / 2f, fill)
        // 트랙 이너 섀도우 (위쪽 어둡게)
        fill.color = Color.argb(70, 90, 66, 40)
        c.drawRoundRect(RectF(x + 1.5f * d, y + 1.2f * d, x + w - 1.5f * d, y + h * 0.42f), h / 2.4f, h / 2.4f, fill)
        // 필
        val p = frac.coerceIn(0f, 1f)
        val inset = 1.6f * d
        val fillW = (w - inset * 2f) * p
        if (p > 0.005f && fillW > 2f * d) {
            val fr = RectF(x + inset, y + inset, x + inset + fillW, y + h - inset)
            fill.shader = LinearGradient(fr.left, fr.top, fr.right, fr.top, c0, c1, Shader.TileMode.CLAMP)
            c.drawRoundRect(fr, (h - inset * 2f) / 2f, (h - inset * 2f) / 2f, fill)
            fill.shader = null
            // 광택선
            if (fillW > 8f * d) {
                fill.color = Color.argb(95, 255, 255, 255)
                c.drawRoundRect(
                    RectF(fr.left + 2.5f * d, fr.top + 1.2f * d, fr.right - 2.5f * d, fr.top + (fr.height()) * 0.44f),
                    3f * d, 3f * d, fill
                )
            }
        }
        stroke.color = BROWN_MID
        stroke.strokeWidth = 1.5f * d
        c.drawRoundRect(r, h / 2f, h / 2f, stroke)
    }

    // ------------------------------------------------------------------
    // 작은 알약 뱃지 (개수/등급 표시)
    // ------------------------------------------------------------------
    fun badge(c: Canvas, game: Game, r: RectF, label: String, bg: Int, fg: Int, textSizeDp: Float) {
        val d = game.density
        fill.shader = null
        fill.color = Color.argb(50, 50, 34, 16)
        c.drawRoundRect(RectF(r.left, r.top + 1f * d, r.right, r.bottom + 1f * d), r.height() / 2f, r.height() / 2f, fill)
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, lighten(bg, 22), darken(bg, 12), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
        fill.shader = null
        stroke.color = darken(bg, 60)
        stroke.strokeWidth = 1f * d
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, stroke)
        drawCenterText(c, game, label, r, textSizeDp, fg, shadow = false)
    }

    // ------------------------------------------------------------------
    // 아이콘 원 (피자/장식/토핑 이모지 배경)
    // ------------------------------------------------------------------
    fun iconCircle(
        c: Canvas, game: Game, cx: Float, cy: Float, radius: Float,
        emoji: String, emojiSizeDp: Float, base: Int = GOLD
    ) {
        val d = game.density
        fill.shader = null
        fill.color = Color.argb(55, 60, 40, 16)
        c.drawCircle(cx, cy + 1.5f * d, radius, fill)
        fill.shader = LinearGradient(cx, cy - radius, cx, cy + radius, lighten(base, 40), darken(base, 8), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        stroke.color = darken(base, 70)
        stroke.strokeWidth = 1.6f * d
        c.drawCircle(cx, cy, radius, stroke)
        // 광택 점
        fill.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(cx - radius * 0.3f, cy - radius * 0.34f, radius * 0.22f, fill)
        val ep = Type.paintAt(emojiSizeDp, false, 0f, BROWN)
        c.drawText(emoji, cx - ep.measureText(emoji) / 2f, Type.midBaseline(ep, cy) + 1f * d, ep)
    }

    // ------------------------------------------------------------------
    // 골드 디바이더
    // ------------------------------------------------------------------
    fun divider(c: Canvas, game: Game, x0: Float, x1: Float, y: Float) {
        val d = game.density
        stroke.strokeWidth = 1.2f * d
        stroke.color = Color.argb(150, 185, 150, 105)
        c.drawLine(x0, y, x1, y, stroke)
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 1f * d
        c.drawLine(x0, y + 1.2f * d, x1, y + 1.2f * d, stroke)
    }

    // ------------------------------------------------------------------
    // 다크 칩 (퀘스트/지역/힌트)
    // ------------------------------------------------------------------
    fun darkChip(c: Canvas, game: Game, cx: Float, cy: Float, txt: String, textSizeDp: Float = 12f) {
        val d = game.density
        val tp = Type.paintAt(textSizeDp, true, 0.02f, CREAM)
        val tw = tp.measureText(txt)
        val pad = 9f * d
        val r = RectF(cx - tw / 2f - pad, cy - 12f * d, cx + tw / 2f + pad, cy + 12f * d)
        fill.shader = null
        fill.color = Color.argb(70, 20, 14, 26)
        c.drawRoundRect(RectF(r.left, r.top + 2f * d, r.right, r.bottom + 2f * d), 12f * d, 12f * d, fill)
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, 0xFF4A4258.toInt(), 0xFF322C40.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 12f * d, 12f * d, fill)
        fill.shader = null
        stroke.color = Color.argb(170, 233, 196, 106)
        stroke.strokeWidth = 1.4f * d
        c.drawRoundRect(r, 12f * d, 12f * d, stroke)
        c.drawText(txt, cx - tw / 2f, Type.midBaseline(tp, cy), tp)
    }

    // ------------------------------------------------------------------
    // 등급 색상
    // ------------------------------------------------------------------
    fun tierColor(tier: Tier): Int = when (tier) {
        Tier.COMMON -> 0xFF9AA3AD.toInt()
        Tier.UNCOMMON -> 0xFF5FA355.toInt()
        Tier.RARE -> 0xFF3F6FB0.toInt()
        Tier.LEGEND -> 0xFFD9403A.toInt()
    }

    fun tierBg(tier: Tier): Int = when (tier) {
        Tier.COMMON -> 0xFF8B939B.toInt()
        Tier.UNCOMMON -> 0xFF5FA355.toInt()
        Tier.RARE -> 0xFF3F6FB0.toInt()
        Tier.LEGEND -> 0xFFE2574C.toInt()
    }

    // ------------------------------------------------------------------
    // 가운데 텍스트 (부드러운 그림자 포함)
    // ------------------------------------------------------------------
    fun drawCenterText(
        c: Canvas, game: Game, label: String, r: RectF,
        textSizeDp: Float, color: Int, shadow: Boolean = true
    ) {
        val d = game.density
        val tp = Type.paintAt(textSizeDp, true, 0.03f, color)
        val tw = tp.measureText(label)
        val ty = r.centerY() - (tp.descent() + tp.ascent()) / 2f
        if (shadow) {
            c.drawText(label, r.centerX() - tw / 2f, ty + 1f * d, Type.paintAt(textSizeDp, true, 0.03f, Color.argb(80, 40, 26, 12)))
        }
        c.drawText(label, r.centerX() - tw / 2f, ty, tp)
    }

    /** 왼쪽 정렬 텍스트 (그림자 포함) */
    fun drawText(c: Canvas, game: Game, label: String, x: Float, y: Float, textSizeDp: Float, color: Int) {
        c.drawText(label, x, y, Type.paintAt(textSizeDp, true, 0.02f, color))
    }
}
