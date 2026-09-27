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
        // 프레스 상태 — 누른 카드가 살짝 눌린다 (터치 피드백)
        val pressed = game.input.isPressedIn(r)
        if (pressed) {
            c.save()
            c.translate(0f, 1.4f * d)
        }
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
        if (pressed) {
            c.restore()
            fill.shader = null
            fill.color = Color.argb(30, 40, 26, 12)
            c.drawRoundRect(r, radius, radius, fill)
        }
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
        // 프레스 상태 — 누른 만큼 눌리고 어둡게 (UI 공통 터치 피드백)
        val pressed = game.input.isPressedIn(r)
        if (pressed) {
            c.save()
            c.translate(0f, 1.8f * d)
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
        if (pressed) {
            c.restore()
            fill.shader = null
            fill.color = Color.argb(34, 40, 26, 12)
            c.drawRoundRect(r, radius, radius, fill)
        }
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
        // 프레스 상태 — 원형 히트체크 + 눌림 피드백
        val pressed = game.input.isPressedInCircle(cx, cy, radius)
        if (pressed) {
            c.save()
            c.translate(0f, 1.6f * d)
        }
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
        if (pressed) {
            c.restore()
            fill.shader = null
            fill.color = Color.argb(34, 40, 26, 12)
            c.drawCircle(cx, cy, radius, fill)
        }
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

    // ==================================================================
    // 레이아웃 토큰 — 모든 창이 같은 여백·모서리·행 높이를 쓴다
    // ==================================================================
    const val RAD_PANEL = 16f        // 큰 패널 모서리
    const val RAD_CARD = 11f         // 카드/행 모서리
    const val PAD_PANEL = 14f        // 패널 안쪽 여백
    const val GAP = 8f               // 블록 사이
    const val GAP_SM = 5f           // 행/열 사이
    const val ROW = 32f              // 표준 행 높이
    const val HDR = 44f              // 표준 헤더 높이

    /** 콘텐츠 박스 — 패널에서 여백을 뺀 사각형 */
    fun inset(game: Game, r: RectF, padDp: Float = PAD_PANEL): RectF {
        val p = padDp * game.density
        return RectF(r.left + p, r.top + p, r.right - p, r.bottom - p)
    }

    /**
     * 화면 중앙의 표준 패널.
     * 비율로만 크기를 주면 와이드 화면에서 내용물이 끝없이拉长되므로 dp 상한을 함께 쓴다.
     */
    fun centerPanel(game: Game, wRatio: Float, hRatio: Float, maxWDp: Float, maxHDp: Float): RectF {
        val d = game.density
        val pw = minOf(game.screenW * wRatio, maxWDp * d)
        val ph = minOf(game.screenH * hRatio, maxHDp * d)
        val cx = game.screenW / 2f
        val cy = game.screenH / 2f
        return RectF(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
    }

    /** 텍스트가 maxW 를 넘으면 말줄임 (…) — 창마다 반복되는 잘림 방지 */
    fun fit(text: String, paint: Paint, maxW: Float): String {
        if (maxW <= 0f || text.isEmpty()) return text
        if (paint.measureText(text) <= maxW) return text
        var s = text
        while (s.length > 1 && paint.measureText("$s…") > maxW) s = s.dropLast(1)
        return "$s…"
    }

    /** fit + 그려진 텍스트 폭을 함께 돌려준다 (오른쪽 정렬 배치용) */
    fun fitW(text: String, paint: Paint, maxW: Float): Pair<String, Float> {
        val s = fit(text, paint, maxW)
        return s to paint.measureText(s)
    }

    // ------------------------------------------------------------------
    // 헤더 — 아이콘 칩 + 제목/부제 + 우측 트레일 칩 + 원형 닫기 버튼
    // ------------------------------------------------------------------
    class HeaderBox(val bottom: Float, val close: RectF)

    /**
     * 모든 창의 표준 머리말. 창마다 닫기 버튼의 크기와 자리가 달랐던 것을 통일한다.
     * @param trail 우측 상단 알약 칩(예: 보유 금전). 빈 문자열이면 생략
     * @return 헤더 하단 y(= 콘텐츠 시작점) 와 닫기 히트 박스
     */
    fun header(
        c: Canvas, game: Game, panelR: RectF,
        icon: String, title: String, sub: String = "",
        trail: String = "", trailTint: Int = GOLD,
        titleSizeDp: Float = 13.5f, hDp: Float = HDR
    ): HeaderBox {
        val d = game.density
        val left = panelR.left + dp(game, 12f)
        val rowCy = panelR.top + dp(game, hDp) / 2f

        // 우측 자리를 먼저 계산한다 — 제목은 남은 폭 안에서만 넓어진다
        val closeR = dp(game, 12.5f)
        val closeCx = panelR.right - dp(game, 12f) - closeR
        val closeHit = RectF(closeCx - closeR - dp(game, 5f), rowCy - closeR - dp(game, 5f),
            closeCx + closeR + dp(game, 5f), rowCy + closeR + dp(game, 5f))
        var titleRight = closeCx - closeR - dp(game, 8f)
        if (trail.isNotEmpty()) {
            val tp = Type.paintAt(10f, true, 0.02f, 0xFF4A2E12.toInt())
            val tw = tp.measureText(trail) + dp(game, 18f)
            val th = dp(game, 19f)
            val tr = RectF(titleRight - tw, rowCy - th / 2f, titleRight, rowCy + th / 2f)
            badge(c, game, tr, trail, trailTint, 0xFF4A2E12.toInt(), 10f)
            titleRight = tr.left - dp(game, 9f)
        }

        val chipS = dp(game, 25f)
        iconChip(c, game, RectF(left, rowCy - chipS / 2f, left + chipS, rowCy + chipS / 2f), icon)

        val tx = left + chipS + dp(game, 9f)
        val titlePaint = Type.paintAt(titleSizeDp, true, 0.02f, INK)
        val tTxt = fit(title, titlePaint, (titleRight - tx).coerceAtLeast(dp(game, 24f)))
        if (sub.isEmpty()) {
            c.drawText(tTxt, tx, Type.midBaseline(titlePaint, rowCy), titlePaint)
        } else {
            val subPaint = Type.paintAt(9f, false, 0.01f, MUTED)
            c.drawText(tTxt, tx, Type.midBaseline(titlePaint, rowCy - dp(game, 5.5f)), titlePaint)
            c.drawText(fit(sub, subPaint, (titleRight - tx).coerceAtLeast(dp(game, 24f))), tx,
                Type.midBaseline(subPaint, rowCy + dp(game, 7f)), subPaint)
        }

        circleButton(c, game, closeCx, rowCy, closeR, "\u2715", 11f)
        val bottom = panelR.top + dp(game, hDp)
        divider(c, game, panelR.left + dp(game, 12f), panelR.right - dp(game, 12f), bottom - dp(game, 3f))
        return HeaderBox(bottom, closeHit)
    }

    /** 각진 스퀴어 아이콘 칩 (이모지/글리프 배경) */
    fun iconChip(c: Canvas, game: Game, r: RectF, glyph: String, base: Int = GOLD) {
        val d = game.density
        val rad = r.height() * 0.32f
        fill.shader = null
        fill.color = Color.argb(50, 60, 40, 16)
        c.drawRoundRect(RectF(r.left, r.top + 1.4f * d, r.right, r.bottom + 1.4f * d), rad, rad, fill)
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, lighten(base, 36), darken(base, 22), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, fill)
        fill.shader = null
        stroke.color = darken(base, 70)
        stroke.strokeWidth = 1.4f * d
        c.drawRoundRect(r, rad, rad, stroke)
        fill.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(RectF(r.left + 2f * d, r.top + 1.6f * d, r.right - 2f * d, r.top + r.height() * 0.42f), rad * 0.6f, rad * 0.6f, fill)
        val gp = Type.paintAt(r.height() / d * 0.6f, false, 0f, BROWN)
        c.drawText(glyph, r.centerX() - gp.measureText(glyph) / 2f, Type.midBaseline(gp, r.centerY()) + 0.6f * d, gp)
    }

    // ------------------------------------------------------------------
    // 수직 레일 탭 — 넓은 화면에서 지저분한 가로 바를 대신하는 사이드 메뉴
    // ------------------------------------------------------------------
    /**
     * @param items (아이콘, 라벨)
     * @param count 0보다 큰 값을 넘기면 라벨 옆에 숫자 배지를 그린다 (행 인덱스순)
     * @return 항목별 히트 박스
     */
    fun railTabs(
        c: Canvas, game: Game, r: RectF,
        items: List<Pair<String, String>>, sel: Int,
        itemH: Float = dp(game, 38f), gap: Float = dp(game, 5f), textSizeDp: Float = 10.5f
    ): List<RectF> {
        val d = game.density
        val out = ArrayList<RectF>(items.size)
        var y = r.top
        val labelPaint = Type.paintAt(textSizeDp, true, 0.02f, INK)
        for ((i, it) in items.withIndex()) {
            val hr = RectF(r.left, y, r.right, (y + itemH).coerceAtMost(r.bottom))
            val on = i == sel
            if (on) {
                card(c, game, hr, RAD_CARD, true, BROWN_LINE, 1.6f)
                // 선택 표시 — 왼쪽 골드 액센트 바
                fill.shader = null
                fill.color = GOLD
                val bh = hr.height() - dp(game, 10f)
                c.drawRoundRect(
                    RectF(hr.left + 3f * d, hr.centerY() - bh / 2f, hr.left + 6f * d, hr.centerY() + bh / 2f),
                    1.5f * d, 1.5f * d, fill
                )
            } else {
                val pressed = game.input.isPressedIn(hr)
                fill.shader = null
                fill.color = if (pressed) Color.argb(70, 160, 128, 84) else Color.argb(34, 170, 140, 96)
                c.drawRoundRect(hr, RAD_CARD * d, RAD_CARD * d, fill)
            }
            val glyphSize = itemH * 0.52f
            iconChip(c, game, RectF(hr.left + 9f * d, hr.centerY() - glyphSize / 2f, hr.left + 9f * d + glyphSize, hr.centerY() + glyphSize / 2f), it.first,
                if (on) GOLD else CREAM_DEEP)
            val lx = hr.left + 9f * d + glyphSize + 7f * d
            c.drawText(it.second, lx, Type.midBaseline(labelPaint, hr.centerY()),
                if (on) Type.paintAt(textSizeDp, true, 0.02f, INK) else Type.paintAt(textSizeDp, true, 0.02f, MUTED))
            out.add(hr)
            y += itemH + gap
        }
        return out
    }

    // ------------------------------------------------------------------
    // 세그먼티드 탭 — 콘텐츠 너비만큼만 쓰는 컴팩트 탭 (전폭 바 대체)
    // ------------------------------------------------------------------
    const val SEG_LEFT = 0
    const val SEG_CENTER = 1
    const val SEG_RIGHT = 2

    /**
     * 알약 트랙 위에 항목 너비만큼만 올리는 탭 바.
     * @param maxH_dp 트랙 높이(dp)
     * @return 항목별 히트 박스
     */
    fun segmented(
        c: Canvas, game: Game, x: Float, y: Float, maxW: Float,
        items: List<Pair<String, String>>, sel: Int,
        hDp: Float = 26f, textSizeDp: Float = 10.5f, align: Int = SEG_LEFT
    ): List<RectF> {
        val d = game.density
        val h = dp(game, hDp)
        val padX = dp(game, 11f)
        val gapSeg = dp(game, 3f)
        val labelP = Type.paintAt(textSizeDp, true, 0.02f, INK)

        // 라벨 폭 = 아이콘 + 라벨
        val labels = ArrayList<String>(items.size)
        val widths = ArrayList<Float>(items.size)
        var need = 0f
        for (it in items) {
            val s = if (it.first.isEmpty()) it.second else "${it.first} ${it.second}"
            labels.add(s)
            val w = labelP.measureText(s) + padX * 2f
            widths.add(w)
            need += w
        }
        need += gapSeg * (items.size - 1).coerceAtLeast(0)
        // 폭이 모자라면 패딩을 줄이고, 그래도 부족하면 균등 분배
        var pad2 = padX
        if (need > maxW) {
            pad2 = ((maxW / items.size - labelP.measureText(" ") * 2f) / 2f).coerceAtLeast(dp(game, 5f))
            need = 0f
            for (i in items.indices) {
                val w = (labelP.measureText(labels[i]) + pad2 * 2f).coerceAtMost(maxW / items.size)
                widths[i] = w
                need += w
            }
            need += gapSeg * (items.size - 1).coerceAtLeast(0)
        }
        val trackW = minOf(maxW, need)
        val trackX = when (align) {
            SEG_CENTER -> x + (maxW - trackW) / 2f
            SEG_RIGHT -> x + maxW - trackW
            else -> x
        }

        // 트랙 — 패인 알약
        fill.shader = null
        fill.color = Color.argb(60, 150, 120, 78)
        c.drawRoundRect(RectF(trackX, y, trackX + trackW, y + h), h / 2f, h / 2f, fill)
        fill.color = Color.argb(38, 90, 66, 40)
        c.drawRoundRect(RectF(trackX + 1.5f * d, y + 1.2f * d, trackX + trackW - 1.5f * d, y + h * 0.45f), h / 2.6f, h / 2.6f, fill)
        stroke.color = Color.argb(120, 201, 168, 123)
        stroke.strokeWidth = 1f * d
        c.drawRoundRect(RectF(trackX, y, trackX + trackW, y + h), h / 2f, h / 2f, stroke)

        val out = ArrayList<RectF>(items.size)
        var sx = trackX + (trackW - need) / 2f
        val innerH = h - dp(game, 5f)
        for (i in items.indices) {
            val w = widths[i]
            val r = RectF(sx, y + dp(game, 2.5f), sx + w, y + dp(game, 2.5f) + innerH)
            if (i == sel) {
                card(c, game, r, innerH / 2f, true, BROWN_LINE, 1.4f)
                // 골드 밑줄 — 선택된 항목만 강조
                fill.shader = null
                fill.color = GOLD
                val uw = minOf(w - dp(game, 16f), dp(game, 26f)).coerceAtLeast(dp(game, 10f))
                c.drawRoundRect(
                    RectF(r.centerX() - uw / 2f, r.bottom - 2.6f * d, r.centerX() + uw / 2f, r.bottom - 0.8f * d),
                    1f * d, 1f * d, fill
                )
                c.drawText(labels[i], r.centerX() - labelP.measureText(labels[i]) / 2f,
                    Type.midBaseline(labelP, r.centerY() - dp(game, 0.4f)), labelP)
            } else {
                val pressed = game.input.isPressedIn(r)
                fill.shader = null
                if (pressed) {
                    fill.color = Color.argb(60, 120, 92, 58)
                    c.drawRoundRect(r, innerH / 2f, innerH / 2f, fill)
                }
                c.drawText(labels[i], r.centerX() - labelP.measureText(labels[i]) / 2f,
                    Type.midBaseline(labelP, r.centerY() - dp(game, 0.4f)),
                    Type.paintAt(textSizeDp, true, 0.02f, if (pressed) BROWN else MUTED))
            }
            out.add(r)
            sx += w + gapSeg
        }
        return out
    }

    private fun dp(game: Game, v: Float): Float = v * game.density

    // ------------------------------------------------------------------
    // KPI 타일 — 라벨이 작고 값이 큰 요약 카드 (지갑·레벨·재고 같은 것들)
    // ------------------------------------------------------------------
    fun tile(
        c: Canvas, game: Game, r: RectF, icon: String, label: String, value: String,
        sub: String = "", accent: Int = GOLD, selected: Boolean = false
    ) {
        card(c, game, r, RAD_CARD, selected, if (selected) accent else BROWN_LINE, if (selected) 2f else 1.4f)
        val chipS = dp(game, 19f)
        iconChip(c, game, RectF(r.left + dp(game, 8f), r.centerY() - chipS / 2f,
            r.left + dp(game, 8f) + chipS, r.centerY() + chipS / 2f), icon, accent)
        val lx = r.left + dp(game, 8f) + chipS + dp(game, 7f)
        val space = r.right - dp(game, 9f) - lx
        if (space <= dp(game, 22f)) return
        val labP = Type.paintAt(8.5f, false, 0.04f, MUTED)
        val valP = Type.paintAt(if (sub.isEmpty()) 13f else 12f, true, 0.02f, INK)
        if (sub.isEmpty()) {
            c.drawText(fit(label, labP, space), lx, Type.midBaseline(labP, r.centerY() - dp(game, 6.5f)), labP)
            c.drawText(fit(value, valP, space), lx, Type.midBaseline(valP, r.centerY() + dp(game, 5.5f)), valP)
        } else {
            // 윗줄 = 라벨 + 보조 정보, 아랫줄 = 값 (값이 잘리지 않게 끝까지 쓴다)
            val subP = Type.paintAt(8.2f, false, 0.02f, MUTED)
            // 라벨은 끝까지 살리고 보조 문구부터 줄인다
            val lt = fit(label, labP, space * 0.45f)
            val st = fit(sub, subP, (space - labP.measureText(lt) - dp(game, 10f)).coerceAtLeast(dp(game, 12f)))
            c.drawText(lt, lx, Type.midBaseline(labP, r.centerY() - dp(game, 7f)), labP)
            c.drawText(st, r.right - dp(game, 9f) - subP.measureText(st), Type.midBaseline(subP, r.centerY() - dp(game, 7f)), subP)
            c.drawText(fit(value, valP, space), lx, Type.midBaseline(valP, r.centerY() + dp(game, 6f)), valP)
        }
    }

    // ------------------------------------------------------------------
    // 라벨/값 격자 — 길게 늘어진 한 줄 행들을 카드 한 장으로 합친다
    // ------------------------------------------------------------------
    fun kvGrid(
        c: Canvas, game: Game, r: RectF,
        cells: List<Pair<String, String>>, cols: Int,
        labelSizeDp: Float = 9.5f, valueSizeDp: Float = 10.5f,
        zebra: Boolean = true
    ) {
        val d = game.density
        if (cells.isEmpty()) return
        card(c, game, r, RAD_CARD, false, BROWN_LINE, 1.4f)
        val rows = (cells.size + cols - 1) / cols
        val rowH = r.height() / rows
        val colW = r.width() / cols
        val labP = Type.paintAt(labelSizeDp, false, 0.02f, MUTED)
        val valP = Type.paintAt(valueSizeDp, true, 0.02f, INK)
        val padX = dp(game, 11f)
        for (i in cells.indices) {
            val col = i % cols
            val row = i / cols
            val cx0 = r.left + col * colW
            val cy0 = r.top + row * rowH
            val midY = cy0 + rowH / 2f
            // 지브라 — 행이 여러 줄일 때 눈으로 따라가기 쉽게
            if (zebra && rows > 1 && row % 2 == 1) {
                fill.shader = null
                fill.color = Color.argb(26, 180, 150, 104)
                c.drawRect(cx0 + 2f * d, cy0, cx0 + colW - 2f * d, cy0 + rowH, fill)
            }
            val cell = cells[i]
            val labW = labP.measureText(cell.first)
            val maxVal = colW - padX * 2f - labW - dp(game, 8f)
            c.drawText(cell.first, cx0 + padX, Type.midBaseline(labP, midY), labP)
            if (maxVal > dp(game, 16f)) {
                val (vTxt, vW) = fitW(cell.second, valP, maxVal)
                c.drawText(vTxt, cx0 + colW - padX - vW, Type.midBaseline(valP, midY), valP)
            }
            // 행 구분 헤어라인
            if (row < rows - 1) {
                stroke.shader = null
                stroke.color = Color.argb(70, 201, 168, 123)
                stroke.strokeWidth = 1f * d
                c.drawLine(cx0 + padX * 0.6f, cy0 + rowH, cx0 + colW - padX * 0.6f, cy0 + rowH, stroke)
            }
        }
        // 열 구분 — 진짜 표처럼
        if (cols > 1 && rows > 0) {
            stroke.shader = null
            stroke.color = Color.argb(70, 201, 168, 123)
            stroke.strokeWidth = 1f * d
            for (col in 1 until cols) {
                val xx = r.left + col * colW
                c.drawLine(xx, r.top + dp(game, 5f), xx, r.bottom - dp(game, 5f), stroke)
            }
        }
    }

    // ------------------------------------------------------------------
    // 게이지 행 — 라벨 | 바 | 값 을 한 칸에 정렬 (바가 화면 끝까지 늘지 않게)
    // ------------------------------------------------------------------
    fun gaugeRow(
        c: Canvas, game: Game, r: RectF, label: String, frac: Float,
        c0: Int, c1: Int, value: String, labelW: Float = dp(game, 58f), valueW: Float = dp(game, 40f),
        icon: String = ""
    ) {
        val d = game.density
        val labP = Type.paintAt(10f, true, 0.02f, BROWN)
        val valP = Type.paintAt(10f, true, 0.02f, INK)
        var lx = r.left + dp(game, 2f)
        if (icon.isNotEmpty()) {
            val ip = Type.paintAt(11f, false, 0f, INK)
            c.drawText(icon, lx, Type.midBaseline(ip, r.centerY()), ip)
            lx += ip.measureText(icon) + dp(game, 5f)
        }
        c.drawText(label, lx, Type.midBaseline(labP, r.centerY()), labP)
        val bx = lx + labelW
        val right = r.right - valueW - dp(game, 6f)
        if (right > bx + dp(game, 20f)) {
            bar(c, game, bx, r.centerY() - dp(game, 6f), right - bx, dp(game, 12f), frac, c0, c1)
        }
        val (vTxt, vW) = fitW(value, valP, valueW)
        c.drawText(vTxt, r.right - vW, Type.midBaseline(valP, r.centerY()), valP)
    }

    // ------------------------------------------------------------------
    // 섹션 라벨 + 페이지네이션 + 빈 상태
    // ------------------------------------------------------------------
    fun sectionLabel(
        c: Canvas, game: Game, x: Float, y: Float, title: String,
        trailing: String = "", rightEdge: Float = 0f
    ) {
        val d = game.density
        fill.shader = null
        fill.color = GOLD
        c.drawRoundRect(RectF(x, y - dp(game, 8f), x + 2.6f * d, y + dp(game, 1f)), 1.3f * d, 1.3f * d, fill)
        val p = Type.paintAt(9.5f, true, 0.05f, BROWN_MID)
        val tTxt = title
        c.drawText(tTxt, x + dp(game, 7f), y, p)
        if (trailing.isNotEmpty()) {
            val tp = Type.paintAt(8.5f, false, 0.02f, MUTED)
            val right = if (rightEdge > 0f) rightEdge else game.screenW.toFloat() - dp(game, 8f)
            val maxW = (right - x - p.measureText(tTxt) - dp(game, 18f)).coerceAtLeast(0f)
            val txt = fit(trailing, tp, maxW)
            c.drawText(txt, right - tp.measureText(txt), y, tp)
        }
    }

    /** ‹ 이전 / 페이지 알약 / 다음 › — 창마다 따로 놀던 페이지 넘기기 통일 */
    fun pager(
        c: Canvas, game: Game, r: RectF, page: Int, pages: Int, hint: String = ""
    ): Pair<RectF, RectF> {
        val bw = dp(game, 54f)
        val half = r.height() / 2f
        val prev = RectF(r.left, r.centerY() - half, r.left + bw, r.centerY() + half)
        val next = RectF(r.right - bw, r.centerY() - half, r.right, r.centerY() + half)
        button(c, game, prev, "\u2039 이전", if (page > 0) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10f)
        button(c, game, next, "다음 \u203a", if (page < pages - 1) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10f)
        val p = Type.paintAt(9.5f, true, 0.03f, MUTED)
        val txt = "${page + 1} / ${maxOf(pages, 1)}"
        val pw = p.measureText(txt) + dp(game, 18f)
        val cx = r.centerX()
        val ph = minOf(r.height(), dp(game, 19f))
        badge(c, game, RectF(cx - pw / 2f, r.centerY() - ph / 2f, cx + pw / 2f, r.centerY() + ph / 2f),
            txt, 0xFFFFF6DC.toInt(), MUTED, 9.5f)
        if (hint.isNotEmpty()) {
            val hp = Type.paintAt(8f, false, 0.02f, Color.argb(190, 138, 115, 96))
            val maxW = (cx - pw / 2f - prev.right - dp(game, 10f)) * 2f
            val ht = fit(hint, hp, maxW.coerceAtLeast(dp(game, 30f)))
            c.drawText(ht, cx - hp.measureText(ht) / 2f, r.bottom + dp(game, 1f), hp)
        }
        return prev to next
    }

    /** 목록이 비었을 때의 안내 — 그냥 텍스트가 아니라 점선 카드 안에 담는다 */
    fun emptyHint(c: Canvas, game: Game, r: RectF, text: String) {
        val d = game.density
        fill.shader = null
        fill.color = Color.argb(26, 150, 120, 78)
        c.drawRoundRect(r, RAD_CARD * d, RAD_CARD * d, fill)
        stroke.color = Color.argb(120, 201, 168, 123)
        stroke.strokeWidth = 1.2f * d
        c.drawRoundRect(r, RAD_CARD * d, RAD_CARD * d, stroke)
        val p = Type.paintAt(10.5f, false, 0.02f, MUTED)
        c.drawText(text, r.centerX() - p.measureText(text) / 2f, Type.midBaseline(p, r.centerY()), p)
    }
}
