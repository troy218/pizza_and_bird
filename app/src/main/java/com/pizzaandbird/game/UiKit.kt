package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import kotlin.math.sin

/**
 * 프리미엄 UI 키트 — 모든 창/버튼/게이지의 공통 디자인 시스템.
 *
 * 따뜻한 크림 + 골드 + 초코 브라운의 고급 힐링 게임 룩으로 통일한다.
 * Canvas 직접 그리기에서도 대기업 수준의 깊이감(섀도우/그라데이션/글로스)을 낸다.
 */
object UiKit {

    /**
     * Canvas에서 문자를 아이콘으로 그리는 공용 어댑터.
     *
     * 예전 UI는 이모지를 글꼴에 의존해 그렸기 때문에 제조사/OS마다 모양과
     * baseline이 달랐다. 이제 아이콘은 모두 assets/ui의 작은 SVG로 고정하고,
     * 이 표는 저장 데이터에 남아 있는 옛 토큰도 자연스럽게 받아들인다.
     */
    private val iconAliases = mapOf(
        // SVG 에셋 이름 그대로도 쓸 수 있다 (이모지 별칭 없는 키의 자기 매핑)
        "arrow_down" to "arrow_down",
        "arrow_left" to "arrow_left",
        "arrow_right" to "arrow_right",
        "arrow_up" to "arrow_up",
        "chair" to "chair",
        "cheese" to "cheese",
        "cloud" to "cloud",
        "fire" to "fire",
        "minus" to "minus",
        "mushroom" to "mushroom",
        "photo" to "photo",
        "plant" to "plant",
        "plus" to "plus",
        "star" to "star",
        "star_empty" to "star_empty",
        "sun" to "sun",

        "☀" to "sun", "☀️" to "sun", "sunny" to "sun",
        "☁" to "cloud", "cloudy" to "cloud",
        "☂" to "rain", "☔" to "rain", "rain" to "rain",
        "≋" to "wind", "wind" to "wind",
        "❄" to "snow", "snow" to "snow",
        "🌙" to "moon", "moon" to "moon",
        "★" to "star", "☆" to "star_empty",
        "📷" to "camera", "📸" to "photo", "camera" to "camera", "🔭" to "lens", "lens" to "lens",
        "🍕" to "pizza", "pizza" to "pizza", "🔥" to "fire", "🌶" to "fire",
        "🧀" to "cheese", "🍄" to "mushroom", "🥩" to "pizza", "🍅" to "pizza", "🍠" to "pizza", "🥓" to "pizza", "🥗" to "leaf", "🍯" to "star",
        "🚲" to "bike", "bike" to "bike", "💞" to "bike",
        "📚" to "book", "📖" to "book", "book" to "book", "📊" to "note", "🌱" to "leaf",
        "🎒" to "backpack", "backpack" to "backpack",
        "🗺" to "map", "🗺️" to "map", "map" to "map",
        "🏠" to "house", "house" to "house",
        "📍" to "pin", "pin" to "pin",
        "📅" to "calendar", "calendar" to "calendar", "🕐" to "calendar", "⏱" to "calendar",
        "🐦" to "bird", "bird" to "bird", "🏘" to "house", "🎨" to "sparkle", "🧺" to "box",
        "⚙" to "gear", "gear" to "gear",
        "✕" to "close", "×" to "close", "close" to "close",
        "✓" to "check", "✔" to "check", "check" to "check",
        "⚠" to "warning", "warning" to "warning",
        "🔍" to "search", "search" to "search",
        "🏆" to "trophy", "trophy" to "trophy", "💰" to "coin", "coin" to "coin",
        "🌵" to "plant", "🪴" to "plant", "🌿" to "leaf", "🧶" to "plant", "💡" to "sun", "🪑" to "chair", "🖼" to "photo", "✉️" to "note",
        "📻" to "radio", "radio" to "radio",
        "🎵" to "music", "🎶" to "music", "music" to "music",
        "📓" to "note", "note" to "note",
        "📦" to "box", "box" to "box",
        "✨" to "sparkle", "sparkle" to "sparkle",
        "🌿" to "leaf", "🌱" to "leaf", "🌸" to "sparkle", "🍁" to "leaf", "leaf" to "leaf", "🪴" to "plant", "🧴" to "plant", "🌅" to "sun", "🏔" to "leaf", "🌊" to "rain", "🏖" to "sun", "🌾" to "leaf", "🏙" to "house", "🌲" to "leaf",
        "💬" to "note", "🪧" to "map", "☕" to "coffee", "coffee" to "coffee", "🐈" to "bird", "🚪" to "house", "🛏" to "house", "🎨" to "sparkle", "👊" to "check",
        "+" to "plus", "＋" to "plus", "-" to "minus", "−" to "minus",
        "◀" to "arrow_left", "‹" to "arrow_left", "←" to "arrow_left",
        "▶" to "arrow_right", "›" to "arrow_right", "→" to "arrow_right",
        "▲" to "arrow_up", "↑" to "arrow_up", "▼" to "arrow_down", "↓" to "arrow_down"
    )

    /** SVG 파일 이름으로 변환한다. 아이콘이 아닌 문자열이면 null을 반환한다. */
    fun iconName(token: String): String? = iconAliases[token]

    /** 별점 한 줄을 SVG로 그린다. */
    fun starRow(c: Canvas, game: Game, x: Float, y: Float, filled: Int, total: Int = 3, size: Float = 14f): Float {
        for (i in 0 until total) {
            val name = if (i < filled) "star" else "star_empty"
            icon(c, game, name, RectF(x + i * (size + 2f), y - size, x + i * (size + 2f) + size, y))
        }
        return total * size + (total - 1).coerceAtLeast(0) * 2f
    }

    /** 아이콘을 원하는 사각형에 맞춰 그린다. */
    fun icon(c: Canvas, game: Game, token: String, bounds: RectF): Boolean {
        // 아직 별칭이 없는 옛 저장 토큰도 빈 칸으로 남기지 않고 작은 반짝이로
        // 대체한다. 화면에는 OS 이모지가 절대 직접 그려지지 않는다.
        val name = iconName(token) ?: "sparkle"
        game.illustrations.draw(c, "ui/$name.svg", bounds)
        return true
    }

    /** 중심 좌표 기준 아이콘. SVG는 텍스트보다 baseline 차이가 없어 작은 UI에도 안정적이다. */
    fun iconCenter(c: Canvas, game: Game, token: String, cx: Float, cy: Float, size: Float): Boolean {
        return icon(c, game, token, RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f))
    }

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

    // Shader colors have their own alpha. Reset Paint.alpha before each gradient:
    // assigning a shader does not clear the translucent shadow color's alpha.
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
        fill.alpha = 255
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
        fill.alpha = 255
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
            drawCenterLabel(c, game, label, r, textSizeDp, textCol, shadow = false)
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
        fill.alpha = 255
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
        drawCenterLabel(c, game, label, r, textSizeDp, textCol, shadow = true)
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
        fill.alpha = 255
        fill.shader = LinearGradient(cx, cy - radius, cx, cy + radius, lighten(base, 26), darken(base, 14), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        stroke.color = darken(base, 74)
        stroke.strokeWidth = 1.8f * d
        c.drawCircle(cx, cy, radius, stroke)
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 1f * d
        c.drawCircle(cx, cy, radius - 2.4f * d, stroke)
        // 닫기/확대/축소 기호도 폰트 글리프가 아니라 동일한 SVG 아이콘으로 통일한다.
        if (!iconCenter(c, game, glyph, cx, cy, radius * 1.35f)) {
            val gp = Type.paintAt(glyphSizeDp, true, 0.02f, glyphCol)
            c.drawText(glyph, cx - gp.measureText(glyph) / 2f, Type.midBaseline(gp, cy), gp)
        }
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
            fill.alpha = 255
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
        fill.alpha = 255
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
        emoji: String, _emojiSizeDp: Float, base: Int = GOLD
    ) {
        val d = game.density
        fill.shader = null
        fill.color = Color.argb(55, 60, 40, 16)
        c.drawCircle(cx, cy + 1.5f * d, radius, fill)
        fill.alpha = 255
        fill.shader = LinearGradient(cx, cy - radius, cx, cy + radius, lighten(base, 40), darken(base, 8), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        stroke.color = darken(base, 70)
        stroke.strokeWidth = 1.6f * d
        c.drawCircle(cx, cy, radius, stroke)
        // 광택 점
        fill.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(cx - radius * 0.3f, cy - radius * 0.34f, radius * 0.22f, fill)
        // 장식/피자/장비 아이콘은 OS 이모지 대신 로컬 SVG를 사용한다.
        iconCenter(c, game, emoji, cx, cy, radius * 1.45f)
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
        fill.alpha = 255
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
    // 🎒 아기자기 키트 — 여행 가방 안쪽 UI 전용 디자인 언어
    //
    //  게임 화면이 전부 픽셀 아트라서, 가방 속 UI도 "픽셀 모서리 + 캔디 베벨 +
    //  손바느질 스티치" 로 그린다.
    //   · pixelRect   : 모서리를 2단 계단으로 깎은 사각형 (둥근 픽셀 느낌)
    //   · cuteButton  : 아래쪽에 두툼한 굽이 달린 캔디 버튼 — 누르면 굽이 들어간다
    //   · stitchCard  : 크림색 천 카드 + 점선 스티치 안감
    //   · nameTag     : 가방에 다는 이름표 스티커
    //   · sparkle     : 반짝이는 픽셀 별 (장식)
    // ==================================================================

    // 파스텔 팔레트 (탭·카드 색)
    val PASTEL_PEACH = 0xFFFFC9A3.toInt()
    val PASTEL_SKY = 0xFFB3DBF2.toInt()
    val PASTEL_MINT = 0xFFBDE6B8.toInt()
    val PASTEL_LEMON = 0xFFFFE38F.toInt()
    val PASTEL_LILAC = 0xFFDCCBF2.toInt()
    val PASTEL_ROSE = 0xFFF9BFCB.toInt()
    val PASTEL_SAND = 0xFFEEDFC0.toInt()
    val PASTEL_TOMATO = 0xFFF7A08C.toInt()
    /** 픽셀 외곽선 — 진한 초코 */
    val OUTLINE = 0xFF4A3220.toInt()
    /** 스티치 실 색 */
    val THREAD = 0xFFC9A87B.toInt()

    private val pixelPath = Path()
    private val stitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    /**
     * 픽셀 모서리 사각형 경로. 모서리를 u 단위로 2단 계단 처리해 "둥근 픽셀" 느낌을 낸다.
     * (r 이 너무 작으면 계단을 자동으로 줄인다)
     */
    fun pixelRect(r: RectF, unit: Float, out: Path = pixelPath): Path {
        val u = minOf(unit, r.width() / 5f, r.height() / 5f).coerceAtLeast(0.5f)
        val l = r.left; val t = r.top; val rr = r.right; val b = r.bottom
        out.reset()
        out.moveTo(l + 2 * u, t)
        out.lineTo(rr - 2 * u, t)
        out.lineTo(rr - 2 * u, t + u)
        out.lineTo(rr - u, t + u)
        out.lineTo(rr - u, t + 2 * u)
        out.lineTo(rr, t + 2 * u)
        out.lineTo(rr, b - 2 * u)
        out.lineTo(rr - u, b - 2 * u)
        out.lineTo(rr - u, b - u)
        out.lineTo(rr - 2 * u, b - u)
        out.lineTo(rr - 2 * u, b)
        out.lineTo(l + 2 * u, b)
        out.lineTo(l + 2 * u, b - u)
        out.lineTo(l + u, b - u)
        out.lineTo(l + u, b - 2 * u)
        out.lineTo(l, b - 2 * u)
        out.lineTo(l, t + 2 * u)
        out.lineTo(l + u, t + 2 * u)
        out.lineTo(l + u, t + u)
        out.lineTo(l + 2 * u, t + u)
        out.close()
        return out
    }

    /** 픽셀 모서리 사각형 채우기 */
    fun pixelFill(c: Canvas, r: RectF, unit: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawPath(pixelRect(r, unit), fill)
    }

    /** 픽셀 모서리 사각형 채우기 (세로 그라데이션) */
    fun pixelFillGradient(c: Canvas, r: RectF, unit: Float, top: Int, bottom: Int) {
        fill.alpha = 255
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(pixelRect(r, unit), fill)
        fill.shader = null
    }

    /** 픽셀 모서리 사각형 외곽선 */
    fun pixelStroke(c: Canvas, r: RectF, unit: Float, color: Int, widthPx: Float) {
        stroke.color = color
        stroke.strokeWidth = widthPx
        stroke.pathEffect = null
        c.drawPath(pixelRect(r, unit), stroke)
    }

    /** 점선 스티치 (손바느질 안감) */
    fun stitch(c: Canvas, game: Game, r: RectF, unit: Float, color: Int = THREAD, alpha: Int = 170) {
        val d = game.density
        stitchPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        stitchPaint.strokeWidth = 1.2f * d
        stitchPaint.pathEffect = DashPathEffect(floatArrayOf(3.2f * d, 2.6f * d), 0f)
        c.drawPath(pixelRect(r, unit), stitchPaint)
    }

    /** 가로 스티치 선 (구분선 대용) */
    fun stitchLine(c: Canvas, game: Game, x0: Float, x1: Float, y: Float, color: Int = THREAD, alpha: Int = 190) {
        val d = game.density
        stitchPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        stitchPaint.strokeWidth = 1.4f * d
        stitchPaint.pathEffect = DashPathEffect(floatArrayOf(4f * d, 3f * d), 0f)
        c.drawLine(x0, y, x1, y, stitchPaint)
    }

    /**
     * 반짝이는 픽셀 별 — 가운데 점 + 십자 4점. 크기가 phase 에 따라 숨쉰다.
     * (장식용. 살짝만 써야 아기자기하다)
     */
    fun sparkle(c: Canvas, cx: Float, cy: Float, sizePx: Float, color: Int, phase: Float = 0f) {
        val k = 0.72f + 0.28f * sin(phase)
        val u = sizePx * k / 3f
        fill.shader = null
        fill.color = color
        c.drawRect(cx - u / 2f, cy - u / 2f, cx + u / 2f, cy + u / 2f, fill)
        c.drawRect(cx - u / 2f, cy - u * 1.5f, cx + u / 2f, cy - u / 2f, fill)
        c.drawRect(cx - u / 2f, cy + u / 2f, cx + u / 2f, cy + u * 1.5f, fill)
        c.drawRect(cx - u * 1.5f, cy - u / 2f, cx - u / 2f, cy + u / 2f, fill)
        c.drawRect(cx + u / 2f, cy - u / 2f, cx + u * 1.5f, cy + u / 2f, fill)
        // 살짝 흰 하이라이트
        fill.color = Color.argb(150, 255, 255, 255)
        c.drawRect(cx - u / 2f, cy - u / 2f, cx, cy, fill)
    }

    /**
     * 캔디 버튼 — 픽셀 모서리 + 두툼한 굽(depth) + 초코 외곽선 + 픽셀 글린트.
     * 누르는 동안 굽이 눌려 들어가며 얼굴이 살짝 어두워진다.
     * 비활성은 호출부 관례대로 반투명 base 를 넘기면 납작한 회색 천으로 그린다.
     */
    fun cuteButton(
        c: Canvas, game: Game, r: RectF, label: String,
        base: Int, textCol: Int, textSizeDp: Float,
        depthDp: Float = 3f
    ) {
        val d = game.density
        val u = 1.5f * d
        // ---- 비활성 ----
        if (Color.alpha(base) < 200) {
            val a = Color.alpha(base)
            pixelFill(c, r, u, Color.argb(a, Color.red(base), Color.green(base), Color.blue(base)))
            stroke.pathEffect = DashPathEffect(floatArrayOf(2.6f * d, 2.2f * d), 0f)
            stroke.color = Color.argb(120, 140, 125, 105)
            stroke.strokeWidth = 1.2f * d
            c.drawPath(pixelRect(r, u), stroke)
            stroke.pathEffect = null
            drawCenterLabel(c, game, label, r, textSizeDp, textCol, shadow = false)
            return
        }
        val pressed = game.input.isPressedIn(r)
        val depth = depthDp * d
        val sink = if (pressed) depth * 0.72f else 0f
        val bw = 1.6f * d
        val outline = OUTLINE
        val side = darken(base, 52)
        // 그림자 (땅에 닿는 느낌)
        fill.shader = null
        fill.color = Color.argb(if (pressed) 26 else 46, 50, 30, 12)
        c.drawPath(pixelRect(RectF(r.left + 0.5f * d, r.top + depth + 1.5f * d, r.right + 0.5f * d, r.bottom + depth + 1.5f * d), u), fill)
        // 외곽 (버튼 + 굽 전체)
        val whole = RectF(r.left, r.top + sink, r.right, r.bottom + depth)
        pixelFill(c, whole, u, outline)
        // 굽
        val sideR = RectF(whole.left + bw, whole.top + bw, whole.right - bw, whole.bottom - bw)
        pixelFill(c, sideR, u, side)
        // 얼굴
        val face = RectF(r.left + bw, r.top + sink + bw, r.right - bw, r.bottom + sink - bw)
        val faceTop = if (pressed) lighten(base, 6) else lighten(base, 30)
        val faceBot = if (pressed) darken(base, 12) else base
        pixelFillGradient(c, face, u, faceTop, faceBot)
        // 굽과 얼굴 사이 어두운 픽셀선 (입체감)
        fill.color = Color.argb(70, 40, 24, 10)
        c.drawRect(face.left + u, face.bottom - u * 0.8f, face.right - u, face.bottom, fill)
        // 상단 하이라이트 띠 + 왼쪽 위 픽셀 글린트
        if (face.height() > 10f * d) {
            fill.color = Color.argb(if (pressed) 60 else 120, 255, 255, 255)
            c.drawRect(face.left + 2 * u, face.top + u * 0.6f, face.right - 2 * u, face.top + u * 1.5f, fill)
            fill.color = Color.argb(if (pressed) 90 else 190, 255, 255, 255)
            c.drawRect(face.left + u, face.top + u, face.left + 2 * u, face.top + 2 * u, fill)
        }
        // 라벨
        val labelR = RectF(face.left, face.top, face.right, face.bottom - u * 0.4f)
        val tp = Type.paintAt(textSizeDp, true, 0.03f, textCol)
        var lbl = label
        val maxW = labelR.width() - 4f * d
        if (tp.measureText(lbl) > maxW && maxW > 8f * d) {
            while (lbl.length > 1 && tp.measureText("$lbl…") > maxW) lbl = lbl.dropLast(1)
            lbl = "$lbl…"
        }
        drawCenterLabel(c, game, lbl, labelR, textSizeDp, textCol, shadow = true)
    }

    /** 아이콘 토큰으로 시작하는 버튼 라벨을 SVG + 텍스트로 조합한다. */
    private fun drawCenterLabel(
        c: Canvas, game: Game, label: String, r: RectF,
        textSizeDp: Float, color: Int, shadow: Boolean
    ) {
        val firstSpace = label.indexOf(' ')
        val token = if (firstSpace > 0) label.substring(0, firstSpace) else label
        val icon = iconName(token)
        if (icon == null) {
            drawCenterText(c, game, label, r, textSizeDp, color, shadow)
            return
        }
        val text = if (firstSpace > 0) label.substring(firstSpace + 1).trim() else ""
        val tp = Type.paintAt(textSizeDp, true, 0.03f, color)
        val gap = game.density * 3f
        val iconSize = minOf(r.height() * 0.62f, game.density * (textSizeDp + 2f))
        val total = iconSize + gap + tp.measureText(text)
        val left = r.centerX() - total / 2f
        icon(c, game, token, RectF(left, r.centerY() - iconSize / 2f, left + iconSize, r.centerY() + iconSize / 2f))
        val tx = left + iconSize + gap
        val ty = Type.midBaseline(tp, r.centerY())
        if (shadow) c.drawText(text, tx, ty + game.density, Type.paintAt(textSizeDp, true, 0.03f, Color.argb(80, 40, 26, 12)))
        c.drawText(text, tx, ty, tp)
    }

    /**
     * 스티치 카드 — 천 느낌의 픽셀 모서리 카드. 안쪽에 점선 바느질 자국이 있고,
     * tint 로 파스텔 색을 살짝 입힐 수 있다. 누르면 살짝 눌린다.
     */
    fun stitchCard(
        c: Canvas, game: Game, r: RectF,
        tint: Int = CARD_HI,
        border: Int = BROWN_LINE,
        borderWidthDp: Float = 1.6f,
        stitched: Boolean = true,
        selected: Boolean = false
    ) {
        val d = game.density
        val u = 2f * d
        val pressed = game.input.isPressedIn(r)
        if (pressed) {
            c.save()
            c.translate(0f, 1.2f * d)
        }
        // 그림자
        fill.shader = null
        fill.color = Color.argb(if (selected) 60 else 36, 60, 42, 22)
        c.drawPath(pixelRect(RectF(r.left, r.top + 2f * d, r.right, r.bottom + 2.5f * d), u), fill)
        // 본문 — tint 를 위는 밝게, 아래는 그대로
        val top = if (selected) lighten(tint, 18) else lighten(tint, 10)
        val bottom = if (selected) tint else darken(tint, 6)
        pixelFillGradient(c, r, u, top, bottom)
        // 위쪽 밝은 픽셀 띠
        fill.color = Color.argb(110, 255, 255, 255)
        c.drawRect(r.left + 2 * u, r.top + u * 0.5f, r.right - 2 * u, r.top + u * 1.1f, fill)
        // 테두리
        pixelStroke(c, r, u, border, borderWidthDp * d)
        // 스티치 안감
        if (stitched && r.width() > 40f * d && r.height() > 18f * d) {
            val ins = 3.6f * d
            stitch(c, game, RectF(r.left + ins, r.top + ins, r.right - ins, r.bottom - ins), u * 0.7f,
                if (selected) GOLD_DEEP else THREAD, if (selected) 190 else 150)
        }
        if (pressed) {
            c.restore()
            fill.shader = null
            fill.color = Color.argb(28, 40, 26, 12)
            c.drawPath(pixelRect(r, u), fill)
        }
    }

    /**
     * 가방 이름표 — 크림색 스티커에 구멍과 끈이 달린 태그. 제목용.
     * 왼쪽 hole 자리 만큼 텍스트가 오른쪽으로 밀린다.
     */
    fun nameTag(
        c: Canvas, game: Game, r: RectF, label: String,
        textSizeDp: Float, base: Int = CREAM_HI, textCol: Int = BROWN
    ) {
        val d = game.density
        val u = 1.5f * d
        // 끈 (작은 고리)
        stroke.pathEffect = null
        stroke.color = GOLD_DEEP
        stroke.strokeWidth = 1.6f * d
        val hx = r.left + 9f * d
        val hy = r.centerY()
        c.drawCircle(hx - 3.5f * d, hy - 3f * d, 3.5f * d, stroke)
        // 태그 몸통
        fill.shader = null
        fill.color = Color.argb(50, 50, 30, 12)
        c.drawPath(pixelRect(RectF(r.left, r.top + 2f * d, r.right, r.bottom + 2f * d), u), fill)
        pixelFillGradient(c, r, u, lighten(base, 6), darken(base, 10))
        pixelStroke(c, r, u, OUTLINE, 1.6f * d)
        // 구멍 (금속 아일렛)
        fill.color = GOLD_DEEP
        c.drawCircle(hx, hy, 3.2f * d, fill)
        fill.color = CREAM_DEEP
        c.drawCircle(hx, hy, 1.6f * d, fill)
        // 글자 + 첫 토큰 아이콘
        val firstSpace = label.indexOf(' ')
        val token = if (firstSpace > 0) label.substring(0, firstSpace) else ""
        val visible = if (firstSpace > 0) label.substring(firstSpace + 1) else label
        val hasIcon = iconName(token) != null
        if (hasIcon) icon(c, game, token, RectF(hx + 5f * d, r.centerY() - 7f * d, hx + 19f * d, r.centerY() + 7f * d))
        val tp = Type.paintAt(textSizeDp, true, 0.04f, textCol)
        val tx = hx + if (hasIcon) 22f * d else 8f * d
        c.drawText(visible, tx, Type.midBaseline(tp, r.centerY()) + 1f * d, Type.paintAt(textSizeDp, true, 0.04f, Color.argb(70, 40, 26, 12)))
        c.drawText(visible, tx, Type.midBaseline(tp, r.centerY()), tp)
    }

    /** 이름표 폭 계산 (nameTag 와 짝) */
    fun nameTagWidth(game: Game, label: String, textSizeDp: Float): Float {
        val d = game.density
        val firstSpace = label.indexOf(' ')
        val token = if (firstSpace > 0) label.substring(0, firstSpace) else ""
        val visible = if (firstSpace > 0) label.substring(firstSpace + 1) else label
        return 17f * d + (if (iconName(token) != null) 14f * d else 0f) +
            Type.paintAt(textSizeDp, true, 0.04f, BROWN).measureText(visible) + 12f * d
    }

    /**
     * 귀여운 픽셀 토글 스위치 — 설정 탭용.
     */
    fun cuteToggle(c: Canvas, game: Game, right: Float, cy: Float, on: Boolean): RectF {
        val d = game.density
        val sw = 36f * d; val sh = 19f * d
        val r = RectF(right - sw, cy - sh / 2f, right, cy + sh / 2f)
        val u = 1.5f * d
        fill.shader = null
        fill.color = Color.argb(40, 50, 30, 12)
        c.drawPath(pixelRect(RectF(r.left, r.top + 1.5f * d, r.right, r.bottom + 1.5f * d), u), fill)
        pixelFillGradient(c, r, u, if (on) lighten(GREEN, 24) else lighten(TRACK, 10), if (on) GREEN else TRACK)
        pixelStroke(c, r, u, OUTLINE, 1.5f * d)
        // 상태 글자 (ON/OFF 대신 점)
        val knobW = sh - 5f * d
        val kx = if (on) r.right - 2.5f * d - knobW else r.left + 2.5f * d
        val kr = RectF(kx, r.top + 2.5f * d, kx + knobW, r.bottom - 2.5f * d)
        pixelFillGradient(c, kr, u * 0.8f, CREAM_HI, CREAM_DEEP)
        pixelStroke(c, kr, u * 0.8f, OUTLINE, 1.2f * d)
        fill.color = Color.argb(170, 255, 255, 255)
        c.drawRect(kr.left + u, kr.top + u, kr.left + 2 * u, kr.top + 2 * u, fill)
        if (on) {
            fill.color = Color.argb(200, 255, 255, 255)
            c.drawCircle(r.left + 8f * d, cy, 1.8f * d, fill)
        }
        return r
    }
}
