package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 토스트 메시지가 머무는 시간(초) */
private const val MESSAGE_LIFE = 2.8f

/** 지역 배너가 머무는 시간(초) */
private const val BANNER_LIFE = 2.6f

/**
 * 화면 좌표(실제 해상도) 기반 HUD.
 * - 좌상단: 배고픔/행운/돈/피자/카메라/시각 패널
 * - 우상단: 황동 회중 나침반 미니맵 (낡은 종이 해도, 탭하면 큰 지도)
 * - 하단: 플로팅 조이스틱 + 육각 메인 버튼 · 아크 버튼(자전거/카메라/달리기/간식) + 메뉴
 */
class Hud(private val game: Game) {

    private val d: Float get() = game.density
    private fun dp(v: Float): Float = v * d

    // ----- 표시 플래그 (씬이 설정) -----
    var showControls = false
    var showStats = false
    var showMinimap = false
    var regionLabel = ""
    var questLabel: String? = null

    /** 카메라 모드 활성 (뷰파인더가 세계를 덮고 있다) */
    var photoModeHint = false

    /** 메인 버튼에 표시할 맥락 아이콘(근처 상호작용 대상). null이면 기본 주먹 아이콘. 씬이 매 프레임 설정 */
    var contextIcon: String? = null

    // ----- 조이스틱 상태 (듀랑고식 플로팅) -----
    var stickHeld = false
        private set
    var stickBaseX = 0f
        private set
    var stickBaseY = 0f
        private set

    // ----- 레이아웃(px) — 조이스틱 -----
    var stickBaseR = 0f; var stickKnobR = 0f
    var stickIdleX = 0f; var stickIdleY = 0f
    private var stickZoneRight = 0f   // 조이스틱 구역: x < 이 값
    private var stickZoneTop = 0f     // 조이스틱 구역: y > 이 값

    // ----- 레이아웃(px) — 버튼 -----
    var mainCx = 0f; var mainCy = 0f; var mainR = 0f          // 육각 메인(상호작용)
    var bikeCx = 0f; var bikeCy = 0f; var bikeR = 0f          // 자전거 (아크)
    var camBCx = 0f; var camBCy = 0f; var camBR = 0f          // 카메라 (아크)
    var runCx = 0f; var runCy = 0f; var runR = 0f             // 달리기 (아크)
    var eatCx = 0f; var eatCy = 0f; var eatR = 0f             // 간식 (아크)
    var menuCx = 0f; var menuCy = 0f; var menuR = 0f          // 메뉴 클러스터(좌하단)
    var mmCx = 0f; var mmCy = 0f; var mmR = 0f                // 미니맵(우상단)

    private val messages = ArrayList<Message>()
    private var bannerText: String? = null
    private var bannerT = 0f

    private class Message(var text: String, var t: Float, val life: Float)

    // ----- 페인트 캐시 -----
    private val fill = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // 컨트롤 레이블용 텍스트 페인트 (본문 타이포그래피는 Type이 담당)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
    }
    // ----- 조이스틱 애니메이션 상태 (게임 스레드 전용) -----
    private var stickVX = 0f             // 부드러운 캡 벡터 (-1..1)
    private var stickVY = 0f
    private var stickMag = 0f            // 0..1 (캡이 밀린 정도)
    private var stickPress = 0f          // 0..1 (잡힘 정도)
    private var dashRot = 0f             // 달리기 링 회전(도)

    // ----- 조이스틱 전용 페인트/셰이더 (layout에서 생성, 매 프레임 재사용) -----
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val chevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val knobIdlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobHotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var runDash: DashPathEffect? = null
    private var ghostDash: DashPathEffect? = null

    private val tmpRect = RectF()
    private val chevPath = Path()
    private val tickDirs = Array(32) { i ->
        val a = i * (Math.PI.toFloat() * 2f / 32f)
        PointF(cos(a), sin(a))
    }

    private val fx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val inkText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val softShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 36, 24, 12)
    }
    private val clipPath = Path()
    private val tmpPath = Path()
    private var paperBmp: Bitmap? = null
    private val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private var routeDashFx: DashPathEffect? = null

    private val GLASS_RATIO = 0.745f

    private fun routeDash(): DashPathEffect {
        routeDashFx?.let { return it }
        val fx = DashPathEffect(floatArrayOf(dp(2.2f), dp(3.4f)), 0f)
        routeDashFx = fx
        return fx
    }

    // ------------------------------------------------------------------

    fun layout(w: Int, h: Int) {
        val wf = w.toFloat()
        val hf = h.toFloat()

        // --- 플로팅 조이스틱 (왼쪽 아래 구역) ---
        stickBaseR = dp(46f)
        stickKnobR = dp(20f)
        stickIdleX = dp(86f)
        stickIdleY = hf - dp(86f)
        stickZoneRight = wf * 0.42f
        stickZoneTop = hf * 0.40f
        stickBaseX = stickIdleX
        stickBaseY = stickIdleY

        // --- 육각 메인 버튼 (오른쪽 아래 구석) ---
        mainR = dp(31f)
        mainCx = wf - dp(26f) - mainR
        mainCy = hf - dp(26f) - mainR

        // --- 아크 버튼 (메인 버튼 중심 부채꼴 — 듀랑고 스타일) ---
        val arcR = dp(19f)
        val arcDist = mainR + dp(9f) + arcR
        fun arc(angleDeg: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angleDeg.toDouble())
            return Pair(
                mainCx + arcDist * kotlin.math.cos(rad).toFloat(),
                mainCy - arcDist * kotlin.math.sin(rad).toFloat()
            )
        }
        bikeR = arcR; val (bx, by) = arc(66f); bikeCx = bx; bikeCy = by
        camBR = arcR; val (cx2, cy2) = arc(105f); camBCx = cx2; camBCy = cy2
        runR = arcR; val (rx, ry) = arc(144f); runCx = rx; runCy = ry
        eatR = arcR; val (ex, ey) = arc(183f); eatCx = ex; eatCy = ey

        // --- 메뉴 클러스터 (왼쪽 아래 구석) ---
        menuR = dp(17f)
        menuCx = dp(18f) + menuR
        menuCy = hf - dp(18f) - menuR

        mmR = dp(68f)
        mmCx = w - dp(8f) - mmR
        mmCy = dp(8f) + mmR
        softShadow.maskFilter = BlurMaskFilter(dp(3.4f), BlurMaskFilter.Blur.NORMAL)
        buildStickShaders(stickBaseR)
    }

    // ------------------------------------------------------------------
    // 입력 판정
    // ------------------------------------------------------------------

    private fun inCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    fun controlAt(x: Float, y: Float): Ctrl {
        if (!showControls) {
            // 컨트롤이 숨겨져 있어도 나침반·메모지는 탭 가능
            if (hitMinimap(x, y)) return Ctrl.MAP
            return Ctrl.NONE
        }
        // 버튼 최우선 (메뉴 클러스터가 조이스틱 구역과 겹치므로 먼저 판정)
        if (inCircle(x, y, menuCx, menuCy, menuR * 1.35f)) return Ctrl.MENU
        if (inCircle(x, y, mainCx, mainCy, mainR * 1.22f)) return Ctrl.A
        if (inCircle(x, y, bikeCx, bikeCy, bikeR * 1.3f)) return Ctrl.B
        if (inCircle(x, y, camBCx, camBCy, camBR * 1.3f)) return Ctrl.CAM
        if (inCircle(x, y, runCx, runCy, runR * 1.3f)) return Ctrl.RUN
        if (inCircle(x, y, eatCx, eatCy, eatR * 1.3f)) return Ctrl.EAT
        if (hitMinimap(x, y)) return Ctrl.MAP
        // 듀랑고식: 왼쪽 아래 구역은 어디를 짚어도 그 자리가 조이스틱
        // ('움직이는 스틱'을 면 고정 자리 근처에서만 잡힌다)
        if (!stickHeld) {
            if (game.state.floatStick && x < stickZoneRight && y > stickZoneTop) return Ctrl.STICK
            if (!game.state.floatStick && inCircle(x, y, stickIdleX, stickIdleY, stickBaseR * 1.2f)) return Ctrl.STICK
        }
        return Ctrl.NONE
    }

    private fun hitMinimap(x: Float, y: Float): Boolean {
        if (!showMinimap || mmR <= 0f) return false
        if (inCircle(x, y, mmCx, mmCy, mmR * 1.04f)) return true
        val tag = fieldTagRect() ?: return false
        tag.inset(-dp(5f), -dp(4f))
        return tag.contains(x, y)
    }

    // ------------------------------------------------------------------
    // 조이스틱 (게임 스레드에서 Input이 호출)
    // ------------------------------------------------------------------

    /**
     * 손을 댄 자리가 조이스틱 베이스가 된다 (듀랑고식 플로팅).
     * 설정에서 '움직이는 스틱'을 면 베이스는 고정 자리에 머문다.
     */
    fun grabStick(x: Float, y: Float) {
        stickHeld = true
        if (!game.state.floatStick) {
            stickBaseX = stickIdleX
            stickBaseY = stickIdleY
            return
        }
        val minX = stickBaseR * 0.35f
        stickBaseX = x.coerceIn(minX, stickZoneRight)
        stickBaseY = y.coerceIn(stickZoneTop, game.screenH.toFloat() - stickBaseR * 0.35f)
    }

    /** 스틱을 놓음 — 베이스는 updateStick()에서 고정 자리로 부드럽게 돌아온다 */
    fun releaseStick() {
        stickHeld = false
    }

    /** 베이스 대비 손가락 위치 → 이동 벡터 (길이 0..1) */
    fun stickVector(p: PointF): PointF {
        val dx = p.x - stickBaseX
        val dy = p.y - stickBaseY
        val len = sqrt(dx * dx + dy * dy)
        val dead = stickBaseR * STICK_DEAD
        val maxLen = stickBaseR * STICK_MAX
        if (len <= dead || len == 0f) return PointF(0f, 0f)
        val k = ((len - dead) / (maxLen - dead)).coerceIn(0f, 1f)
        return PointF(dx / len * k, dy / len * k)
    }

    // ------------------------------------------------------------------
    // 메시지(토스트) / 배너
    // ------------------------------------------------------------------

    fun toast(msg: String) {
        messages.add(Message(msg, MESSAGE_LIFE, MESSAGE_LIFE))
        if (messages.size > 3) messages.removeAt(0)
    }

    fun banner(msg: String) {
        bannerText = msg
        bannerT = 2.6f
    }

    fun update(dt: Float) {
        val it = messages.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.t -= dt
            if (m.t <= 0f) it.remove()
        }
        if (bannerT > 0f) bannerT -= dt
        updateStick(dt)
    }

    /** 조이스틱 캡/베이스 애니메이션 (입력은 즉시, 그림은 부드럽게) */
    private fun updateStick(dt: Float) {
        val held = game.input.stickEngaged
        var tvx = 0f
        var tvy = 0f
        if (held) {
            val v = stickVector(game.input.stickTouchPoint())
            tvx = v.x
            tvy = v.y
        }
        val k = (dt * (if (held) 30f else 22f)).coerceIn(0f, 1f)
        stickVX += (tvx - stickVX) * k
        stickVY += (tvy - stickVY) * k
        if (!held && abs(stickVX) < 0.004f && abs(stickVY) < 0.004f) {
            stickVX = 0f
            stickVY = 0f
        }
        stickMag = sqrt(stickVX * stickVX + stickVY * stickVY).coerceIn(0f, 1f)

        val targetPress = if (held) 1f else 0f
        stickPress += (targetPress - stickPress) * (dt * (if (held) 26f else 16f)).coerceIn(0f, 1f)
        if (!held && stickPress < 0.01f) stickPress = 0f

        // 놓으면 베이스가 고정 자리로 스프링 복귀
        if (!stickHeld) {
            val kb = (dt * 14f).coerceIn(0f, 1f)
            stickBaseX += (stickIdleX - stickBaseX) * kb
            stickBaseY += (stickIdleY - stickBaseY) * kb
            if (abs(stickBaseX - stickIdleX) < 0.6f && abs(stickBaseY - stickIdleY) < 0.6f) {
                stickBaseX = stickIdleX
                stickBaseY = stickIdleY
            }
        }

        val running = game.input.isRun && stickMag > 0.12f
        dashRot = (dashRot + dt * (if (running) 170f + 260f * stickMag else 14f)) % 360f
    }

    private fun buildStickShaders(R: Float) {
        val kr = stickKnobR
        basePaint.shader = RadialGradient(
            0f, -R * 0.18f, R * 1.30f,
            intArrayOf(
                Color.argb(206, 86, 79, 108),
                Color.argb(192, 44, 39, 61),
                Color.argb(208, 22, 19, 33)
            ),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            -R * 0.75f, -R, R * 0.8f, R * 0.95f,
            intArrayOf(
                Color.argb(245, 255, 249, 230),
                Color.argb(165, 226, 205, 165),
                Color.argb(225, 107, 79, 53),
                Color.argb(245, 206, 173, 126)
            ),
            floatArrayOf(0f, 0.34f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        rimPaint.strokeWidth = dp(3.2f)
        knobIdlePaint.shader = RadialGradient(
            -kr * 0.32f, -kr * 0.42f, kr * 1.55f,
            intArrayOf(0xFFFFFBEE.toInt(), 0xFFF6E7C4.toInt(), 0xFFE3CB9E.toInt(), 0xFFB08F63.toInt()),
            floatArrayOf(0f, 0.34f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        knobHotPaint.shader = RadialGradient(
            -kr * 0.32f, -kr * 0.42f, kr * 1.55f,
            intArrayOf(0xFFFFFBE6.toInt(), 0xFFFBDB7E.toInt(), 0xFFF0B93F.toInt(), 0xFFC08424.toInt()),
            floatArrayOf(0f, 0.34f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            0f, 0f, R * 1.05f,
            intArrayOf(
                Color.argb(0, 242, 208, 107),
                Color.argb(42, 242, 208, 107),
                Color.argb(92, 247, 206, 91),
                Color.argb(0, 247, 206, 91)
            ),
            floatArrayOf(0.60f, 0.80f, 0.93f, 1f), Shader.TileMode.CLAMP
        )
        runDash = DashPathEffect(floatArrayOf(dp(7f), dp(9f)), 0f)
        ghostDash = DashPathEffect(floatArrayOf(dp(3f), dp(7f)), 0f)
    }


    // ------------------------------------------------------------------
    // 그리기
    // ------------------------------------------------------------------

    fun draw(c: Canvas) {
        if (showStats) drawStats(c)
        if (showMinimap) {
            drawMinimap(c, mmCx, mmCy, mmR, false)
            if (regionLabel.isNotEmpty()) drawFieldTag(c)
        }
        if (questLabel != null) {
            drawChip(c, questChipX(), questChipY(), "🔍 $questLabel")
        }
        if (showControls) drawControls(c)
        drawBanner(c)
        drawMessages(c)
    }

    private fun questChipX(): Float = dp(16f) + dp(162f) / 2f
    private fun questChipY(): Float = dp(12f) + dp(170f) + dp(22f)
    private fun drawStats(c: Canvas) {
        val s = game.state
        val left = dp(12f)
        val top = dp(12f)
        val w = dp(162f)
        val h = dp(170f)

        // 프리미엄 패널
        val r = RectF(left, top, left + w, top + h)
        UiKit.panel(c, game, r, 12f)

        val a = game.assets

        // 배고픔 — 아이콘 메달 + 그라데이션 바 (위험하면 맥동해 알린다)
        val iy1 = top + dp(12f)
        val iconSz = dp(16f)
        fill.color = if (s.hunger < 25f) Color.argb(60, 226, 87, 76) else Color.argb(60, 242, 178, 60)
        c.drawCircle(left + dp(20f), iy1 + dp(8f), dp(11f), fill)
        c.drawBitmap(a.pizzaIcon, null, RectF(left + dp(12f), iy1, left + dp(12f) + iconSz, iy1 + iconSz), a.sprPaint)
        val hungerColor = when {
            s.hunger >= 25f -> 0xFFF2913C.toInt()
            s.hunger >= 15f -> 0xFFE2574C.toInt()
            else -> blendToward(
                0xFFE2574C.toInt(), 0xFFFFE9C9.toInt(),
                (0.5f + 0.5f * sin(game.time * 6f)) * 0.5f
            )
        }
        drawBar(c, left + dp(36f), iy1 + dp(2f), dp(112f), dp(12f), s.hunger, hungerColor)

        // 행운
        val iy2 = iy1 + dp(22f)
        fill.color = Color.argb(60, 111, 186, 107)
        c.drawCircle(left + dp(20f), iy2 + dp(8f), dp(11f), fill)
        c.drawBitmap(a.cloverIcon, null, RectF(left + dp(12f), iy2, left + dp(12f) + iconSz, iy2 + iconSz), a.sprPaint)
        drawBar(c, left + dp(36f), iy2 + dp(2f), dp(112f), dp(12f), s.effectiveLuck(), 0xFF6FBA6B.toInt())

        UiKit.divider(c, game, left + dp(10f), left + w - dp(10f), top + dp(53f))

        // 돈 — 골드 도트 + 금액(숫자는 픽셀 폰트)
        fill.color = 0xFFF2B63C.toInt()
        c.drawCircle(left + dp(18f), iy2 + dp(31f), dp(5f), fill)
        stroke.color = 0xFFB5651D.toInt()
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(left + dp(18f), iy2 + dp(31f), dp(5f), stroke)
        Type.text(c, won(s.money), left + dp(28f), iy2 + dp(36f), Role.HEADING, Type.INK)

        // 피자 / 카메라
        c.drawBitmap(a.pizzaIcon, null, RectF(left + dp(12f), iy2 + dp(42f), left + dp(12f) + dp(14f), iy2 + dp(42f) + dp(14f)), a.sprPaint)
        Type.text(c, "×${s.pizzaCount}", left + dp(30f), iy2 + dp(53f), Role.LABEL, Type.INK)
        val rig = s.rig()
        c.drawBitmap(
            a.camIcon(rig.look), null,
            RectF(left + dp(52f), iy2 + dp(41f), left + dp(52f) + dp(19f), iy2 + dp(41f) + dp(15.5f)),
            a.sprPaint
        )
        Type.text(c, "${rig.teleMm}mm", left + dp(75f), iy2 + dp(53f), Role.LABEL, Type.INK)

        UiKit.divider(c, game, left + dp(10f), left + w - dp(10f), top + dp(94f))

        // 시각 + 사진
        val night = s.isNight()
        val clockIcon = if (night) a.moonIcon else a.sunIcon
        c.drawBitmap(clockIcon, null, RectF(left + dp(11f), iy2 + dp(62f), left + dp(11f) + dp(14f), iy2 + dp(62f) + dp(14f)), a.sprPaint)
        Type.text(c, s.timeLabel(), left + dp(30f), iy2 + dp(73f), Role.LABEL, Type.MUTED)
        Type.text(c, "📷 ${s.photos}", left + dp(79f), iy2 + dp(73f), Role.LABEL, Type.MUTED)

        UiKit.divider(c, game, left + dp(10f), left + w - dp(10f), top + dp(114f))

        // 날씨: 새 스폰과 월드 연출에 적용되는 현재 상태
        val weather = s.weather()
        Type.text(c, "${weather.icon} ${weather.label}", left + dp(12f), iy2 + dp(92f), Role.LABEL, 0xFF587083.toInt())

        // 레벨 + 경험치 바
        // 날씨 줄과 겹치지 않도록 그 아래에 배치
        val ly = iy2 + dp(100f)
        Type.text(c, "Lv.${s.level}", left + dp(12f), ly + dp(12f), Role.LABEL, Type.INK)
        Type.text(c, s.title(), left + dp(46f), ly + dp(11f), Role.CAPTION, Type.SOFT)

        // 바 (프리미엄 그라데이션)
        val bx = left + dp(12f)
        val bw = w - dp(24f)
        val by = ly + dp(16f)
        val bh = dp(6f)
        if (s.level >= Progression.MAX_LEVEL) {
            UiKit.bar(c, game, bx, by, bw, bh, 1f, 0xFFFFE08A.toInt(), 0xFFF2D06B.toInt())
        } else {
            UiKit.bar(c, game, bx, by, bw, bh, s.expProgress(), 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        }

    }

    private fun drawBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, v: Float, color: Int) {
        UiKit.bar(c, game, x, y, w, h, v / 100f, UiKit.lighten(color, 36), color)
    }

    private fun drawChip(c: Canvas, cx: Float, cy: Float, txt: String) {
        UiKit.darkChip(c, game, cx, cy, txt, 12f)
    }

    private fun drawMessages(c: Canvas) {
        val th = game.screenH.toFloat()
        var y = dp(20f) + if (photoModeHint) dp(62f) else 0f
        // 등장한 배너가 토스트와 겹치지 않도록 아래로 밀어 낸다
        if (bannerText != null && bannerT > 0f) y = maxOf(y, th * 0.24f + dp(40f))
        for (m in messages) {
            // 등장 슬라이드 + 페이드인/아웃
            val age = 2.8f - m.t
            val inK = (age / 0.22f).coerceIn(0f, 1f)
            val outK = (m.t.coerceIn(0f, 0.4f) / 0.4f)
            val alpha = (255 * minOf(inK, outK)).toInt().coerceIn(0, 255)
            if (alpha < 4) {
                y += dp(28f)
                continue
            }
            val yy = y + (1f - inK) * -dp(10f)
            // 긴 문구는 화면에 맞게 축소/말줄임 (글꼴은 Type 이 정한다)
            val tcol = Color.argb(alpha, 248, 239, 220)
            val maxW = game.screenW - dp(70f)
            var msg = m.text
            var tp = Type.paintAt(12.5f, true, 0.02f, tcol)
            if (tp.measureText(msg) > maxW) tp = Type.paintAt(11f, true, 0.02f, tcol)
            val full = msg
            while (msg.length > 4 && tp.measureText("$msg…") > maxW) msg = msg.dropLast(1)
            if (msg != full) msg = "$msg…"
            val tw = tp.measureText(msg)
            val cx = game.screenW / 2f
            val pad = dp(10f)
            val r = RectF(cx - tw / 2 - pad, yy - dp(12f), cx + tw / 2 + pad, yy + dp(13f))
            // 다크 토스트 + 골드 엣지
            fill.color = Color.argb((alpha * 0.35f).toInt(), 20, 14, 26)
            c.drawRoundRect(RectF(r.left, r.top + dp(2f), r.right, r.bottom + dp(2f)), dp(13f), dp(13f), fill)
            fill.color = Color.argb((alpha * 0.92f).toInt(), 46, 40, 58)
            c.drawRoundRect(r, dp(13f), dp(13f), fill)
            stroke.color = Color.argb((alpha * 0.85f).toInt(), 233, 196, 106)
            stroke.strokeWidth = dp(1.5f)
            c.drawRoundRect(r, dp(13f), dp(13f), stroke)
            // 왼쪽 골드 도트
            fill.color = Color.argb(alpha, 242, 182, 60)
            c.drawCircle(r.left + dp(10f), yy + dp(0.5f), dp(3f), fill)
            c.drawText(msg, cx - tw / 2, Type.midBaseline(tp, yy), tp)
            y += dp(29f)
        }
    }

    private fun drawBanner(c: Canvas) {
        val bt = bannerText ?: return
        if (bannerT <= 0f) return
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        val fadeOut = (bannerT - 2.1f).coerceIn(0f, 1f)      // 마지막 0.5초 페이드아웃
        val age = 2.6f - bannerT
        val inK = (age / 0.3f).coerceIn(0f, 1f)
        val eased = 1f - (1f - inK) * (1f - inK) * (1f - inK)
        val alpha = (255 * minOf(fadeOut, eased)).toInt().coerceIn(0, 255)
        if (alpha < 4) return

        val bcol = Color.argb(alpha, 248, 239, 220)
        val tp = Type.paintAt(26f, true, 0.04f, bcol)
        // 좌우 HUD를 피해 폭이 넘치면 폰트를 줄여 한 줄에 맞춘다
        val bwMax = (if (showStats) w - dp(360f) else w - dp(40f))
            .coerceIn(dp(200f), (w - dp(40f)).coerceAtLeast(dp(200f)))
        while (tp.measureText(bt) + dp(44f) > bwMax && tp.textSize > dp(16f)) {
            tp.textSize -= dp(1f)
        }
        val tw = tp.measureText(bt)
        val cx = w / 2f
        val cy = h * 0.24f
        // 등장 팝 스케일
        val scale = 0.86f + 0.14f * eased
        c.save()
        c.scale(scale, scale, cx, cy)
        val r = RectF(cx - tw / 2 - dp(22f), cy - dp(25f), cx + tw / 2 + dp(22f), cy + dp(25f))
        fill.color = Color.argb((alpha * 0.4f).toInt(), 20, 14, 26)
        c.drawRoundRect(RectF(r.left, r.top + dp(3f), r.right, r.bottom + dp(4f)), dp(17f), dp(17f), fill)
        fill.color = Color.argb((alpha * 0.88f).toInt(), 43, 38, 58)
        c.drawRoundRect(r, dp(17f), dp(17f), fill)
        // 상단 광택
        fill.color = Color.argb((alpha * 0.25f).toInt(), 255, 255, 255)
        c.drawRoundRect(RectF(r.left + dp(14f), r.top + dp(3f), r.right - dp(14f), r.top + dp(6f)), dp(2f), dp(2f), fill)
        // 이중 골드 테두리
        stroke.color = Color.argb((alpha * 0.95f).toInt(), 242, 208, 107)
        stroke.strokeWidth = dp(2.2f)
        c.drawRoundRect(r, dp(17f), dp(17f), stroke)
        stroke.color = Color.argb((alpha * 0.4f).toInt(), 242, 208, 107)
        stroke.strokeWidth = dp(1f)
        c.drawRoundRect(
            RectF(r.left + dp(4f), r.top + dp(4f), r.right - dp(4f), r.bottom - dp(4f)),
            dp(13f), dp(13f), stroke
        )
        c.drawText(bt, cx - tw / 2, Type.midBaseline(tp, cy), tp)
        c.restore()
    }


    // ------------------------------------------------------------------
    // 컨트롤 (듀랑고 스타일)
    // ------------------------------------------------------------------

    private fun drawControls(c: Canvas) {
        val active = game.input.activeControls()

        // ------------------------------------------------------------
        // 1) 플로팅 조이스틱 (왼쪽) — 잡으면 손을 댄 자리에 베이스가 생긴다
        // ------------------------------------------------------------
        drawJoystick(c)

        // ------------------------------------------------------------
        // 2) 육각 메인 버튼 (오른쪽 아래) — 상호작용/맥락 액션
        // ------------------------------------------------------------
        val pressedA = Ctrl.A in active
        val hasCtx = contextIcon != null
        val hexR = mainR * (if (pressedA) 1.07f else 1f)
        drawHexButton(
            c, mainCx, mainCy, hexR,
            when {
                pressedA -> 0xFFD99B26.toInt()
                hasCtx -> 0xFF4A4458.toInt()
                else -> Color.argb(220, 74, 74, 88)
            },
            emphasized = hasCtx || pressedA
        )
        // 메인 아이콘: 근처 상호작용 대상이 있으면 그 아이콘, 없으면 기본 주먹
        text.textSize = dp(21f)
        text.color = 0xFFF8EFDC.toInt()
        val mainIcon = contextIcon ?: "👊"
        val miTw = text.measureText(mainIcon)
        c.drawText(mainIcon, mainCx - miTw / 2, mainCy - (text.descent() + text.ascent()) / 2f, text)

        // ------------------------------------------------------------
        // 3) 아크 버튼 (메인 버튼 중심 부채꼴)
        // ------------------------------------------------------------
        // 자전거 (🚲)
        val pressedB = Ctrl.B in active
        val onBike = game.state.onBike
        drawArcButton(c, bikeCx, bikeCy, bikeR, when {
            pressedB -> 0xFFD99B26.toInt()
            onBike -> 0xFF9F7FC8.toInt()
            else -> Color.argb(220, 74, 74, 88)
        }, pressedB)
        drawGlyph(c, bikeCx, bikeCy, "🚲", dp(17f))

        // 카메라 (📷)
        val camPressed = Ctrl.CAM in active
        drawArcButton(c, camBCx, camBCy, camBR, when {
            camPressed -> 0xFFD99B26.toInt()
            photoModeHint -> 0xFFE2574C.toInt()
            else -> Color.argb(220, 74, 74, 88)
        }, camPressed)
        val cam = game.assets.camIcon(game.state.rig().look)
        val cw = dp(20f)
        val ch = dp(20f * 18f / 22f)
        c.drawBitmap(cam, null, RectF(camBCx - cw / 2, camBCy - ch / 2, camBCx + cw / 2, camBCy + ch / 2), game.assets.sprPaint)

        // 카메라 모드: 촬영 중임을 알리는 붉은 펄스 링 + 회전하는 점선 아크
        if (photoModeHint) {
            val pulse = 0.5f + 0.5f * sin(game.time * 3.4f)
            stroke.color = Color.argb(46, 226, 87, 76)
            stroke.strokeWidth = dp(9f)
            c.drawCircle(camBCx, camBCy, camBR + dp(5f) + dp(4f) * pulse, stroke)
            stroke.color = Color.argb((165 + 70 * pulse).toInt().coerceIn(0, 255), 226, 87, 76)
            stroke.strokeWidth = dp(2.6f)
            c.drawCircle(camBCx, camBCy, camBR + dp(3f) + dp(3f) * pulse, stroke)

            val pulseR = camBR + dp(8f)
            val arcRect = RectF(camBCx - pulseR, camBCy - pulseR, camBCx + pulseR, camBCy + pulseR)
            linePaint.color = Color.argb(225, 255, 232, 220)
            linePaint.strokeWidth = dp(3f)
            val baseDeg = (game.time * 96f) % 360f
            for (i in 0 until 6) {
                c.drawArc(arcRect, baseDeg + i * 60f, 20f, false, linePaint)
            }
        }

        // 달리기 (») — 누르고 있으면 강조
        val running = game.input.isRun
        drawArcButton(
            c, runCx, runCy, runR,
            if (running) 0xFFF2D06B.toInt() else if (Ctrl.RUN in active) 0xFFD9A03C.toInt() else Color.argb(220, 74, 74, 88),
            Ctrl.RUN in active
        )
        val runCol = if (running) Type.INK else Color.argb(230, 248, 239, 220)
        val runLabel = "»"
        PixelFont.draw(c, runLabel, runCx, PixelFont.midY(runCy, 3), 3, runCol, 0.5f)

        // 간식 (🍕) — 피자 개수 표시
        val pizzaN = game.state.pizzaCount
        drawArcButton(
            c, eatCx, eatCy, eatR,
            if (Ctrl.EAT in active) 0xFFD99B26.toInt() else if (pizzaN > 0) 0xFFF2B63C.toInt() else Color.argb(200, 90, 84, 100),
            Ctrl.EAT in active
        )
        val pz = game.assets.pizzaIcon
        val psz = dp(20f)
        c.drawBitmap(pz, null, RectF(eatCx - psz / 2, eatCy - psz / 2, eatCx + psz / 2, eatCy + psz / 2), game.assets.sprPaint)
        if (pizzaN > 0) {
            val bx = eatCx + eatR * 0.62f
            val by = eatCy - eatR * 0.62f
            fill.color = Color.argb(80, 20, 12, 8)
            c.drawCircle(bx, by + dp(1.5f), dp(8.5f), fill)
            fill.color = 0xFF6B4F35.toInt()
            c.drawCircle(bx, by, dp(8.5f), fill)
            stroke.color = 0xFFF2D06B.toInt()
            stroke.strokeWidth = dp(1.4f)
            c.drawCircle(bx, by, dp(8.5f), stroke)
            val np = Type.paintAt(10f, true, 0.02f, Type.CREAM)
            val nt = "$pizzaN"
            c.drawText(nt, bx - np.measureText(nt) / 2, Type.midBaseline(np, by), np)
        }

        // ------------------------------------------------------------
        // 4) 메뉴 클러스터 (왼쪽 아래 구석, 모서리 둥근 사각형)
        // ------------------------------------------------------------
        val menuPressed = Ctrl.MENU in active
        val ms = menuR
        val mr = RectF(menuCx - ms, menuCy - ms, menuCx + ms, menuCy + ms)
        fill.color = if (menuPressed) 0xFF9F7FC8.toInt() else Color.argb(220, 58, 52, 74)
        c.drawRoundRect(mr, dp(7f), dp(7f), fill)
        stroke.color = Color.argb(190, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawRoundRect(mr, dp(7f), dp(7f), stroke)
        linePaint.color = Color.argb(230, 248, 239, 220)
        linePaint.strokeWidth = dp(2.2f)
        for (i in -1..1) {
            c.drawLine(menuCx - dp(6.5f), menuCy + i * dp(4.2f), menuCx + dp(6.5f), menuCy + i * dp(4.2f), linePaint)
        }

        // 미니맵 살짝 강조 (탭 가능 힌트)
        if (showMinimap && Ctrl.MAP in active) {
            stroke.color = 0xFFF2D06B.toInt()
            stroke.strokeWidth = dp(3f)
            c.drawCircle(mmCx, mmCy, mmR + dp(3f), stroke)
        }
    }

    /** 원형 아크 버튼 몸통 (v0.4.1: 그림자 + 글로스 입체감, 눌리면 오므림) */
    private fun drawArcButton(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, pressed: Boolean = false) {
        val pr = if (pressed) r * 0.93f else r
        val oy = if (pressed) dp(1.5f) else 0f
        fill.color = Color.argb(if (pressed) 60 else 96, 10, 8, 18)
        c.drawCircle(cx + dp(1f), cy + dp(3f), pr, fill)
        fill.color = color
        c.drawCircle(cx, cy + oy, pr, fill)
        clipPath.reset()
        clipPath.addCircle(cx, cy + oy, pr, Path.Direction.CW)
        c.save()
        c.clipPath(clipPath)
        fill.color = Color.argb(40, 26, 16, 10)
        tmpRect.set(cx - pr, cy + oy + pr * 0.14f, cx + pr, cy + oy + pr * 1.2f)
        c.drawOval(tmpRect, fill)
        fill.color = Color.argb(if (pressed) 34 else 62, 255, 253, 244)
        tmpRect.set(cx - pr * 0.78f, cy + oy - pr * 0.94f, cx + pr * 0.78f, cy + oy - pr * 0.08f)
        c.drawOval(tmpRect, fill)
        c.restore()
        stroke.color = Color.argb(200, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(cx, cy + oy, pr - dp(1f), stroke)
        stroke.color = Color.argb(70, 40, 28, 20)
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(cx, cy + oy, pr, stroke)
    }

    // ------------------------------------------------------------------
    // 조이스틱 (v0.4.1 디자인)
    // ------------------------------------------------------------------

    private fun drawJoystick(c: Canvas) {
        val R = stickBaseR
        if (R <= 0f) return
        val press = stickPress
        val mag = stickMag
        val breathe = 0.5f + 0.5f * sin(game.time * 1.5f)
        val running = game.input.isRun && mag > 0.12f
        val knobR0 = stickKnobR
        val knobScale = 1f + 0.075f * press
        val travel = R - knobR0
        val kx = stickVX * travel
        val ky = stickVY * travel
        val floating = stickHeld && game.state.floatStick

        c.save()
        c.translate(stickBaseX, stickBaseY)

        // 플로팅 중: 고정 자리를 점선으로 알려준다
        if (floating) {
            stroke.color = Color.argb(60, 248, 239, 220)
            stroke.strokeWidth = dp(1.6f)
            stroke.pathEffect = ghostDash
            c.drawCircle(stickIdleX - stickBaseX, stickIdleY - stickBaseY, R * 0.9f, stroke)
            stroke.pathEffect = null
        }

        // 달리기 링 (회전 점선)
        if (running) {
            c.save()
            c.rotate(dashRot)
            dashPaint.color = Color.argb(215, 242, 208, 107)
            dashPaint.strokeWidth = dp(3.2f)
            dashPaint.pathEffect = runDash
            c.drawCircle(0f, 0f, R * 1.17f, dashPaint)
            dashPaint.pathEffect = null
            c.restore()
        }

        // 앰버 글로우 (잡으면 퍼진다)
        glowPaint.alpha = (34 + 178 * press + 26 * breathe * (1f - press)).toInt().coerceIn(0, 255)
        val gs = 1f + 0.11f * press + 0.02f * breathe
        c.save()
        c.scale(gs, gs)
        c.drawCircle(0f, 0f, R * 1.05f, glowPaint)
        c.restore()

        // 바닥 그림자
        fill.color = Color.argb((74 + 40 * press).toInt(), 10, 8, 18)
        c.drawCircle(dp(1.5f), dp(4.5f) + dp(1.5f) * press, R * 0.99f, fill)

        // 베이스(유리) + 금속 림
        c.drawCircle(0f, 0f, R, basePaint)
        c.drawCircle(0f, 0f, R - dp(1.6f), rimPaint)

        // 안쪽 홈 (음영 + 하이라이트 2중 라인)
        groovePaint.strokeWidth = dp(3f)
        groovePaint.color = Color.argb(150, 16, 13, 26)
        c.drawCircle(0f, 0f, R * 0.80f, groovePaint)
        groovePaint.strokeWidth = dp(1.2f)
        groovePaint.color = Color.argb(44, 255, 249, 230)
        c.drawCircle(0f, 0f, R * 0.80f - dp(2.2f), groovePaint)

        // 눈금 32개 (4방위는 굵게)
        for (i in tickDirs.indices) {
            val p = tickDirs[i]
            val cardinal = i % 8 == 0
            val r0 = if (cardinal) R * 0.845f else R * 0.90f
            val r1 = R * 0.955f
            tickPaint.color =
                if (cardinal) Color.argb(205, 248, 239, 220)
                else Color.argb((62 + 34 * breathe).toInt(), 248, 239, 220)
            tickPaint.strokeWidth = if (cardinal) dp(2.2f) else dp(1.1f)
            c.drawLine(p.x * r0, p.y * r0, p.x * r1, p.y * r1, tickPaint)
        }

        // 민 만큼 채워지는 게이지 호
        if (mag > 0.03f) {
            val ar = R * 0.80f
            tmpRect.set(-ar, -ar, ar, ar)
            val ang = Math.toDegrees(atan2(stickVY.toDouble(), stickVX.toDouble())).toFloat()
            val sweep = 320f * mag
            arcPaint.color = if (running) 0xFFF7D977.toInt() else Color.argb(225, 247, 206, 91)
            arcPaint.strokeWidth = dp(3.4f)
            c.drawArc(tmpRect, ang - sweep / 2f, sweep, false, arcPaint)
        }

        // 데드존 안내 점선
        stroke.color = Color.argb(52, 248, 239, 220)
        stroke.strokeWidth = dp(1.2f)
        stroke.pathEffect = ghostDash
        c.drawCircle(0f, 0f, R * STICK_DEAD, stroke)
        stroke.pathEffect = null

        // 4방향 셰브론 (입력 방향 점등)
        val ix = game.input.dirX
        val iy = game.input.dirY
        fun chev(ux: Float, uy: Float) {
            drawChevron(c, ux * R * 0.60f, uy * R * 0.60f, ux, uy, ix * ux + iy * uy, R)
        }
        chev(0f, -1f)
        chev(0f, 1f)
        chev(-1f, 0f)
        chev(1f, 0f)

        // 기울어진 스틱 축
        if (mag > 0.02f) {
            shaftPaint.color = Color.argb(115, 12, 10, 20)
            shaftPaint.strokeWidth = knobR0 * 0.9f * knobScale
            c.drawLine(0f, dp(2f), kx, ky + dp(2f), shaftPaint)
        }

        // 캡
        c.save()
        c.translate(kx, ky)
        fill.color = Color.argb((48 + 38 * press).toInt(), 8, 6, 14)
        c.drawCircle(dp(1.2f), dp(3.6f), knobR0 * knobScale * 1.02f, fill)
        c.scale(knobScale, knobScale)
        c.drawCircle(0f, 0f, knobR0, if (press > 0.45f) knobHotPaint else knobIdlePaint)

        // 캡 안쪽(클립) 디테일
        clipPath.reset()
        clipPath.addCircle(0f, 0f, knobR0, Path.Direction.CW)
        c.save()
        c.clipPath(clipPath)
        fill.color = Color.argb(46, 74, 50, 26)
        tmpRect.set(-knobR0, knobR0 * 0.18f, knobR0, knobR0 * 1.1f)
        c.drawOval(tmpRect, fill)
        fill.color = Color.argb((78 + 42 * press).toInt(), 255, 253, 244)
        tmpRect.set(-knobR0 * 0.52f, -knobR0 * 0.82f, knobR0 * 0.12f, -knobR0 * 0.40f)
        c.drawOval(tmpRect, fill)
        c.restore()

        // 갈색 림 + 안쪽 홈 링
        stroke.color = Color.argb(200, 107, 79, 53)
        stroke.strokeWidth = dp(1.8f)
        c.drawCircle(0f, 0f, knobR0 - dp(1f), stroke)
        stroke.color = Color.argb(52, 107, 79, 53)
        stroke.strokeWidth = dp(1.4f)
        c.drawCircle(0f, 0f, knobR0 * 0.78f, stroke)

        // 피자 엠블럼 (픽셀 아트)
        val pzIcon = game.assets.pizzaIcon
        val emW = knobR0 * 1.06f
        val emH = emW * (pzIcon.height.toFloat() / pzIcon.width.toFloat())
        val spr = game.assets.sprPaint
        val savedAlpha = spr.alpha
        spr.alpha = (200 + 55 * press).toInt().coerceIn(0, 255)
        tmpRect.set(-emW / 2f, -emH / 2f + dp(0.6f), emW / 2f, emH / 2f + dp(0.6f))
        c.drawBitmap(pzIcon, null, tmpRect, spr)
        spr.alpha = savedAlpha

        c.restore()   // 캡
        c.restore()   // 베이스
    }

    /** 방향 셰브론 하나 (comp: 해당 방향으로 밀린 정도 0~1) */
    private fun drawChevron(c: Canvas, px: Float, py: Float, ux: Float, uy: Float, comp: Float, R: Float) {
        val t = comp.coerceIn(0f, 1f)
        val on = t > 0.28f
        val s = R * 0.085f * (1f + 0.22f * t)
        val w = R * 0.125f * (1f + 0.22f * t)
        val ox = ux * dp(1.6f) * t
        val oy = uy * dp(1.6f) * t
        val vx = -uy
        val vy = ux
        chevPath.reset()
        chevPath.moveTo(px + ox - ux * s + vx * w, py + oy - uy * s + vy * w)
        chevPath.lineTo(px + ox + ux * s, py + oy + uy * s)
        chevPath.lineTo(px + ox - ux * s - vx * w, py + oy - uy * s - vy * w)
        if (on) {
            chevPaint.color = Color.argb((80 * t).toInt().coerceIn(0, 80), 242, 208, 107)
            chevPaint.strokeWidth = dp(8f)
            c.drawPath(chevPath, chevPaint)
        }
        chevPaint.color =
            if (on) Color.argb((170 + 85 * t).toInt().coerceIn(0, 255), 247, 217, 119)
            else Color.argb(120, 248, 239, 220)
        chevPaint.strokeWidth = if (on) dp(3.4f) else dp(2.4f)
        c.drawPath(chevPath, chevPaint)
    }

    /** 듀랑고식 육각 메인 버튼 */
    private fun drawHexButton(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, emphasized: Boolean) {
        fill.color = color
        c.drawPath(hexPath(cx, cy, r), fill)
        stroke.color = Color.argb(230, 248, 239, 220)
        stroke.strokeWidth = dp(2.5f)
        c.drawPath(hexPath(cx, cy, r), stroke)
        // 안쪽 골드 링 (듀랑고의 이중 테두리)
        stroke.color = if (emphasized) Color.argb(220, 242, 208, 107) else Color.argb(110, 242, 208, 107)
        stroke.strokeWidth = dp(1.4f)
        c.drawPath(hexPath(cx, cy, r - dp(4f)), stroke)
    }

    /** 납작한 윗면의 육각형 (듀랑고 메인 버튼 모양) */
    private fun hexPath(cx: Float, cy: Float, r: Float): Path {
        val p = Path()
        for (i in 0 until 6) {
            val ang = Math.toRadians((60.0 * i))
            val x = cx + r * kotlin.math.cos(ang).toFloat()
            val y = cy + r * kotlin.math.sin(ang).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    /** 버튼 중앙에 글자/이모지 그리기 */
    private fun drawGlyph(c: Canvas, cx: Float, cy: Float, glyph: String, size: Float, color: Int = 0xFFF8EFDC.toInt()) {
        text.textSize = size
        text.color = color
        val tw = text.measureText(glyph)
        c.drawText(glyph, cx - tw / 2, cy - (text.descent() + text.ascent()) / 2f, text)
    }

    // ------------------------------------------------------------------
    // 원형 미니맵 — 황동 회중 나침반 + 낡은 종이 해도
    // ------------------------------------------------------------------

    fun drawMinimap(c: Canvas, cx: Float, cy: Float, r: Float, showNames: Boolean) {
        if (r <= 1f) return
        val s = game.state
        val glass = r * GLASS_RATIO
        val pressed = Ctrl.MAP in game.input.activeControls()

        softShadow.color = Color.argb(if (s.isNight()) 78 else 58, 36, 24, 12)
        c.drawCircle(cx + dp(1.4f), cy + dp(2.8f), r * 0.98f, softShadow)

        drawBrassDisc(c, cx, cy, r)
        drawCompassFace(c, cx, cy, r, glass, showNames)
        drawBezelMarks(c, cx, cy, r, glass)
        drawInstrumentLight(c, cx, cy, r, glass)

        if (pressed) {
            ink.shader = null
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = dp(2.4f)
            ink.color = 0xFFF2D06B.toInt()
            ink.pathEffect = null
            c.drawCircle(cx, cy, r + dp(1.6f), ink)
        }
    }

    private fun drawBrassDisc(c: Canvas, cx: Float, cy: Float, r: Float) {
        fx.style = Paint.Style.FILL
        fx.shader = RadialGradient(
            cx - r * 0.40f, cy - r * 0.48f, r * 1.45f,
            intArrayOf(
                0xFFF8E8B8.toInt(), 0xFFE6C068.toInt(), 0xFFC48E40.toInt(),
                0xFF845C2C.toInt(), 0xFF56381E.toInt()
            ),
            floatArrayOf(0f, 0.22f, 0.50f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, fx)
        fx.shader = null
    }

    private fun drawCompassFace(c: Canvas, cx: Float, cy: Float, r: Float, glass: Float, showNames: Boolean) {
        val s = game.state
        c.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, glass, Path.Direction.CW)
        c.clipPath(clipPath)

        val paper = paperBitmap()
        c.drawBitmap(paper, null, RectF(cx - glass, cy - glass, cx + glass, cy + glass), paperPaint)

        fx.style = Paint.Style.FILL
        fx.shader = RadialGradient(
            cx - glass * 0.12f, cy - glass * 0.18f, glass * 1.05f,
            intArrayOf(Color.argb(132, 62, 118, 142), Color.argb(168, 48, 96, 124)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, glass, fx)
        fx.shader = null

        val b = KoreaMap.southBounds
        val fitScale = glass * 1.78f / maxOf(b.width(), b.height())
        val ox = cx - b.centerX() * fitScale
        val oy = cy - b.centerY() * fitScale

        c.save()
        c.translate(ox, oy)
        c.scale(fitScale, fitScale)
        KoreaMap.drawLand(c, fitScale, 1, false, analog = true)
        drawChartGrid(c, fitScale)
        c.restore()

        drawTravelRoutes(c, ox, oy, fitScale, s)
        drawMapMarks(c, ox, oy, fitScale, glass, showNames, s)
        drawGlassDepth(c, cx, cy, glass)
        c.restore()
    }

    private fun drawChartGrid(c: Canvas, unit: Float) {
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1.05f / unit
        ink.color = Color.argb(38, 96, 74, 42)
        ink.pathEffect = null
        var lon = 125f
        while (lon <= 131f) {
            val x = KoreaMap.nx(lon)
            c.drawLine(x, KoreaMap.ny(42.5f), x, KoreaMap.ny(33f), ink)
            lon += 2f
        }
        var lat = 33f
        while (lat <= 39f) {
            val y = KoreaMap.ny(lat)
            c.drawLine(KoreaMap.nx(124.4f), y, KoreaMap.nx(130.8f), y, ink)
            lat += 2f
        }
    }

    private fun drawTravelRoutes(c: Canvas, ox: Float, oy: Float, scale: Float, s: GameState) {
        ink.style = Paint.Style.STROKE
        ink.strokeCap = Paint.Cap.ROUND
        ink.pathEffect = routeDash()
        for (pass in 0..1) {
            val full = pass == 1
            ink.color = if (full) Color.argb(175, 92, 62, 36) else Color.argb(58, 110, 90, 62)
            ink.strokeWidth = if (full) dp(1.2f) else dp(0.85f)
            for ((a, b) in Regions.allLinks()) {
                val ra = Regions.byId[a] ?: continue
                val rb = Regions.byId[b] ?: continue
                val va = a in s.visited
                val vb = b in s.visited
                val known = when {
                    va && vb -> 2
                    va || vb -> 1
                    else -> 0
                }
                if (known == 0) continue
                if ((known == 2) != full) continue
                c.drawLine(ox + ra.mmX * scale, oy + ra.mmY * scale, ox + rb.mmX * scale, oy + rb.mmY * scale, ink)
            }
        }
        ink.pathEffect = null
    }

    private fun analogKindColor(kind: RegionKind): Int = when (kind) {
        RegionKind.TOWN -> 0xFFBA8024.toInt()
        RegionKind.WETLAND -> 0xFF2A8470.toInt()
        RegionKind.RIVER -> 0xFF3474A4.toInt()
        RegionKind.MOUNTAIN -> 0xFF567234.toInt()
        RegionKind.COAST -> 0xFFBC6630.toInt()
    }

    private fun drawMapMarks(
        c: Canvas, ox: Float, oy: Float, scale: Float, glass: Float, showNames: Boolean, s: GameState
    ) {
        val dot = maxOf(dp(2.35f), glass * 0.040f)
        var homeX = 0f
        var homeY = 0f
        var hasHome = false
        var curX = 0f
        var curY = 0f
        var hasCur = false

        for (reg in Regions.ALL) {
            val x = ox + reg.mmX * scale
            val y = oy + reg.mmY * scale
            val visited = reg.id in s.visited
            val isCurrent = reg.id == s.region
            val isHome = reg.id == s.homeRegion
            if (isHome) { homeX = x; homeY = y; hasHome = true }
            if (isCurrent) { curX = x; curY = y; hasCur = true }

            if (visited && !isCurrent) {
                fx.style = Paint.Style.FILL
                fx.shader = null
                fx.color = 0xFFF4E8CC.toInt()
                c.drawCircle(x, y, dot + dp(0.85f), fx)
                fx.color = analogKindColor(reg.kind)
                c.drawCircle(x, y, dot, fx)
                ink.style = Paint.Style.STROKE
                ink.strokeWidth = dp(0.7f)
                ink.color = 0xFF3A281A.toInt()
                ink.pathEffect = null
                c.drawCircle(x, y, dot, ink)
            } else if (!visited && !isCurrent) {
                ink.style = Paint.Style.STROKE
                ink.strokeWidth = dp(0.75f)
                ink.color = Color.argb(155, 124, 104, 76)
                ink.pathEffect = null
                c.drawCircle(x, y, dot * 0.68f, ink)
            }

            if (showNames && (isCurrent || isHome)) {
                val nm = reg.name
                val ip = Type.paintAt(8f, true, 0.01f, if (isCurrent) 0xFFB4332A.toInt() else 0xFF3A2A1C.toInt())
                val tw = ip.measureText(nm)
                val ty = y + dot + dp(9f)
                fx.style = Paint.Style.FILL
                fx.shader = null
                fx.color = Color.argb(210, 244, 232, 204)
                c.drawRoundRect(
                    RectF(x - tw / 2f - dp(2f), ty - dp(8f), x + tw / 2f + dp(2f), ty + dp(2.5f)),
                    dp(2f), dp(2f), fx
                )
                c.drawText(nm, x - tw / 2f, ty, ip)
            }
        }

        if (hasCur) {
            val phase = (game.time * 0.62f) % 1f
            val pr = dot * 1.3f + dp(1.4f) + phase * dp(6.2f)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = dp(1.1f)
            ink.color = Color.argb(((1f - phase) * 150f).toInt(), 176, 46, 36)
            ink.pathEffect = null
            c.drawCircle(curX, curY, pr, ink)

            val rr = dot * 1.32f
            fx.style = Paint.Style.FILL
            fx.shader = null
            fx.color = 0xFFF4E8CC.toInt()
            c.drawCircle(curX, curY, rr + dp(0.8f), fx)
            fx.color = 0xFFC63A2E.toInt()
            c.drawCircle(curX, curY, rr, fx)
            fx.color = Color.argb(210, 255, 230, 214)
            c.drawCircle(curX - dp(0.45f), curY - dp(0.5f), dp(1.05f), fx)
        }

        if (hasHome) {
            val hw = dp(7.2f)
            val above = if (hasCur && kotlin.math.abs(homeX - curX) < dp(4f) && kotlin.math.abs(homeY - curY) < dp(4f)) {
                dot * 1.3f + dp(8f)
            } else {
                dot + dp(2f)
            }
            drawInkHouse(c, homeX, homeY - above, hw)
        }
    }

    private fun drawInkHouse(c: Canvas, x: Float, bottom: Float, w: Float) {
        val bodyH = w * 0.58f
        val roofH = w * 0.46f
        val left = x - w / 2f
        val bodyTop = bottom - bodyH
        fx.style = Paint.Style.FILL
        fx.shader = null
        fx.color = 0xFFF4E8CC.toInt()
        c.drawRect(left, bodyTop, left + w, bottom, fx)
        tmpPath.reset()
        tmpPath.moveTo(x, bodyTop - roofH)
        tmpPath.lineTo(left - w * 0.16f, bodyTop + w * 0.06f)
        tmpPath.lineTo(left + w + w * 0.16f, bodyTop + w * 0.06f)
        tmpPath.close()
        c.drawPath(tmpPath, fx)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = dp(0.75f)
        ink.color = 0xFF3C2A1C.toInt()
        ink.pathEffect = null
        c.drawRect(left, bodyTop, left + w, bottom, ink)
        c.drawPath(tmpPath, ink)
        fx.color = 0xFF8A5A38.toInt()
        val dw = w * 0.24f
        c.drawRect(x - dw / 2f, bottom - bodyH * 0.52f, x + dw / 2f, bottom, fx)
    }

    private fun drawGlassDepth(c: Canvas, cx: Float, cy: Float, glass: Float) {
        fx.style = Paint.Style.FILL
        fx.shader = RadialGradient(
            cx, cy, glass,
            intArrayOf(0x00000000, 0x00000000, Color.argb(58, 48, 32, 18)),
            floatArrayOf(0f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, glass, fx)
        fx.shader = null

        val sway = sin(game.time * 0.45f) * 7f
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = dp(2.0f)
        ink.color = Color.argb(if (Ctrl.MAP in game.input.activeControls()) 150 else 92, 255, 255, 255)
        ink.pathEffect = null
        val inset = glass * 0.80f
        c.drawArc(RectF(cx - inset, cy - inset, cx + inset, cy + inset), 206f + sway, 64f, false, ink)
    }

    private fun drawBezelMarks(c: Canvas, cx: Float, cy: Float, r: Float, glass: Float) {
        ink.style = Paint.Style.STROKE
        ink.pathEffect = null
        ink.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 72) {
            if (i == 0) continue
            val ang = Math.toRadians((i * 5 - 90).toDouble())
            val major = i % 18 == 0
            val mid = i % 6 == 0
            val inner = r - dp(if (major) 6.0f else if (mid) 4.5f else 3.15f)
            val outer = r - dp(1.7f)
            ink.strokeWidth = dp(if (major) 1.35f else 0.85f)
            ink.color = Color.argb(if (major) 230 else 168, 42, 28, 16)
            val cs = cos(ang).toFloat()
            val sn = sin(ang).toFloat()
            c.drawLine(cx + cs * inner, cy + sn * inner, cx + cs * outer, cy + sn * outer, ink)
        }

        val tipR = r - dp(1.15f)
        val baseR = r - dp(5.6f)
        val spread = dp(2.45f)
        tmpPath.reset()
        tmpPath.moveTo(cx, cy - tipR)
        tmpPath.lineTo(cx - spread, cy - baseR)
        tmpPath.lineTo(cx + spread, cy - baseR)
        tmpPath.close()
        fx.style = Paint.Style.FILL
        fx.shader = null
        fx.color = 0xFFBA2E24.toInt()
        c.drawPath(tmpPath, fx)

        inkText.typeface = serifBold
        inkText.textSize = dp(8.2f)
        val letterR = glass + dp(6.0f)
        val letters = arrayOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
        for ((lab, deg) in letters) {
            val ang = Math.toRadians((deg - 90).toDouble())
            inkText.color = if (lab == "N") 0xFF9E221A.toInt() else 0xFF2E1E10.toInt()
            drawCentered(
                c, lab,
                cx + cos(ang).toFloat() * letterR,
                cy + sin(ang).toFloat() * letterR,
                inkText
            )
        }

        // 네 귀의 작은 나사
        fx.style = Paint.Style.FILL
        for (deg in intArrayOf(45, 135, 225, 315)) {
            val ang = Math.toRadians((deg - 90).toDouble())
            val rr = (glass + r) * 0.52f
            val x = cx + cos(ang).toFloat() * rr
            val y = cy + sin(ang).toFloat() * rr
            fx.color = 0xFF5C4020.toInt()
            c.drawCircle(x, y, dp(1.65f), fx)
            fx.color = Color.argb(190, 240, 214, 150)
            c.drawCircle(x - dp(0.4f), y - dp(0.45f), dp(0.55f), fx)
        }

        ink.style = Paint.Style.STROKE
        ink.pathEffect = null
        ink.strokeWidth = dp(1.45f)
        ink.color = 0xFF24180E.toInt()
        c.drawCircle(cx, cy, r - dp(0.4f), ink)
        ink.strokeWidth = dp(2.05f)
        ink.color = 0xFF342414.toInt()
        c.drawCircle(cx, cy, glass, ink)
        ink.strokeWidth = dp(0.8f)
        ink.color = Color.argb(120, 255, 226, 170)
        c.drawCircle(cx, cy, glass + dp(1.7f), ink)

        val gang = -1.5707963f + game.time * 0.38f
        val gx = cx + cos(gang) * (r * 0.945f)
        val gy = cy + sin(gang) * (r * 0.945f)
        val ga = (90f + 50f * sin(game.time * 1.1f)).toInt().coerceIn(40, 150)
        fx.style = Paint.Style.FILL
        fx.shader = null
        fx.color = Color.argb(ga, 255, 246, 214)
        c.drawCircle(gx, gy, dp(1.7f), fx)
    }

    /** 해 질 녘·밤에는 나침반 유리에 노을/등잔 빛이 돈다. 한낮에는 그리지 않는다. */
    private fun drawInstrumentLight(c: Canvas, cx: Float, cy: Float, r: Float, glass: Float) {
        val h = game.state.worldTime
        val night = h >= 19.5f || h < 4.5f
        val dusk = h >= 17f && h < 19.5f
        val dawn = h >= 4.5f && h < 7.2f
        if (!night && !dusk && !dawn) return

        val center: Int
        val rim: Int
        if (night) {
            center = Color.argb(42, 255, 188, 112)
            rim = Color.argb(82, 12, 22, 48)
        } else if (dusk) {
            center = Color.argb(22, 255, 160, 80)
            rim = Color.argb(46, 170, 72, 36)
        } else {
            center = Color.argb(20, 255, 176, 110)
            rim = Color.argb(36, 196, 110, 64)
        }
        c.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(clipPath)
        fx.style = Paint.Style.FILL
        fx.shader = RadialGradient(
            cx, cy + glass * 0.04f, r,
            intArrayOf(center, 0x00000000, rim),
            floatArrayOf(0f, 0.40f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, fx)
        fx.shader = null
        c.restore()
    }

    private fun paperBitmap(): Bitmap {
        paperBmp?.let { if (!it.isRecycled) return it }
        val n = 256
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val pc = Canvas(bmp)
        val p = Paint()
        p.color = 0xFFE8D6AC.toInt()
        pc.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        val rnd = java.util.Random(1958)
        for (i in 0 until 1600) {
            val x = rnd.nextInt(n).toFloat()
            val y = rnd.nextInt(n).toFloat()
            val len = 1.5f + rnd.nextFloat() * 6f
            val a = 14 + rnd.nextInt(34)
            p.strokeWidth = 1f
            p.color = if (rnd.nextBoolean()) Color.argb(a, 146, 108, 62) else Color.argb(a, 248, 240, 214)
            pc.drawLine(x, y, x + len, y + rnd.nextFloat() * 1.2f, p)
        }
        // 커피 잔 자국 — 유리 왼쪽 위(서해)에 앉도록
        p.shader = RadialGradient(
            n * 0.28f, n * 0.22f, n * 0.20f,
            intArrayOf(0x00000000, 0x00000000, 0x385C4024, 0x105C4024, 0x00000000),
            floatArrayOf(0f, 0.42f, 0.62f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        pc.drawCircle(n * 0.28f, n * 0.22f, n * 0.20f, p)
        p.shader = null
        paperBmp = bmp
        return bmp
    }

    // ------------------------------------------------------------------
    // 나침반 아래 탐조 메모지
    // ------------------------------------------------------------------

    private fun dms(value: Float, pos: String, neg: String): String {
        val sign = if (value >= 0f) pos else neg
        var v = kotlin.math.abs(value)
        var d = v.toInt()
        var m = ((v - d) * 60f).roundToInt()
        if (m >= 60) {
            d += 1
            m = 0
        }
        return "$d°${m.toString().padStart(2, '0')}'$sign"
    }

    private fun coordLine(reg: RegionDef?): String {
        if (reg == null) return "지도"
        return "${dms(reg.lat, "N", "S")}   ${dms(reg.lon, "E", "W")}"
    }

    /** 메모지 위치. 아래가 카메라 버튼과 겹치면 나침반 왼쪽으로 붙인다. */
    private fun fieldTagRect(): RectF? {
        if (!showMinimap || regionLabel.isEmpty() || mmR <= 0f || game.screenW <= 0) return null
        val reg = Regions.byId[game.state.region]
        measurePaint.typeface = Typeface.DEFAULT_BOLD
        measurePaint.textSize = dp(12.2f)
        val nameW = measurePaint.measureText(regionLabel)
        measurePaint.typeface = serif
        measurePaint.textSize = dp(7.3f)
        val subW = measurePaint.measureText(coordLine(reg))
        val wax = dp(16f)
        val pad = dp(8f)
        val fold = dp(8f)
        val w = maxOf(nameW, subW) + wax + pad * 2f + fold
        val h = dp(36f)
        var left = mmCx - w / 2f
        var top = mmCy + mmR + dp(5f)
        val rightLimit = game.screenW - dp(6f)
        val leftLimit = dp(6f)
        if (showControls && top + h > controlsTop() - dp(4f)) {
            left = mmCx - mmR - dp(6f) - w
            top = mmCy + mmR - h
        }
        if (left + w > rightLimit) left = rightLimit - w
        if (left < leftLimit) left = leftLimit
        return RectF(left, top, left + w, top + h)
    }

    private fun controlsTop(): Float {
        if (mainR <= 0f) return game.screenH.toFloat()
        // 오른쪽 버튼 클러스터(육각 메인 + 카메라 아크 + 펄스 링) 상단
        return minOf(mainCy - mainR, camBCy - camBR) - dp(22f)
    }

    private fun drawFieldTag(c: Canvas) {
        val rect = fieldTagRect() ?: return
        val reg = Regions.byId[game.state.region]
        val coord = coordLine(reg)

        c.save()
        c.rotate(-1.3f, rect.centerX(), rect.centerY())

        softShadow.color = Color.argb(70, 40, 28, 16)
        c.drawRoundRect(
            RectF(rect.left + dp(1.1f), rect.top + dp(1.6f), rect.right + dp(1.1f), rect.bottom + dp(1.6f)),
            dp(2.4f), dp(2.4f), softShadow
        )

        c.save()
        clipPath.reset()
        clipPath.addRoundRect(rect, dp(2.4f), dp(2.4f), Path.Direction.CW)
        c.clipPath(clipPath)
        val src = Rect(96, 150, 230, 230)
        c.drawBitmap(paperBitmap(), src, rect, paperPaint)
        c.restore()

        ink.style = Paint.Style.STROKE
        ink.pathEffect = null
        ink.strokeWidth = dp(1.15f)
        ink.color = 0xFF604830.toInt()
        c.drawRoundRect(rect, dp(2.4f), dp(2.4f), ink)
        ink.strokeWidth = dp(0.7f)
        ink.color = Color.argb(170, 186, 154, 98)
        c.drawRoundRect(
            RectF(rect.left + dp(2.2f), rect.top + dp(2.2f), rect.right - dp(2.2f), rect.bottom - dp(2.2f)),
            dp(1.4f), dp(1.4f), ink
        )

        val fold = dp(7f)
        tmpPath.reset()
        tmpPath.moveTo(rect.right - fold, rect.bottom)
        tmpPath.lineTo(rect.right, rect.bottom - fold)
        tmpPath.lineTo(rect.right - fold, rect.bottom - fold)
        tmpPath.close()
        fx.style = Paint.Style.FILL
        fx.shader = null
        fx.color = 0xFFD6BC8E.toInt()
        c.drawPath(tmpPath, fx)
        ink.strokeWidth = dp(0.7f)
        ink.color = Color.argb(200, 150, 118, 78)
        c.drawLine(rect.right - fold, rect.bottom, rect.right, rect.bottom - fold, ink)

        val wx = rect.left + dp(12f)
        val wy = rect.centerY()
        fx.color = 0xFFB03028.toInt()
        c.drawCircle(wx, wy, dp(4.3f), fx)
        ink.strokeWidth = dp(0.7f)
        ink.color = 0xFF701E1A.toInt()
        c.drawCircle(wx, wy, dp(4.3f), ink)
        fx.color = Color.argb(160, 255, 206, 186)
        c.drawCircle(wx - dp(1.1f), wy - dp(1.2f), dp(1.35f), fx)

        inkText.typeface = Typeface.DEFAULT_BOLD
        inkText.textSize = dp(12.2f)
        inkText.color = 0xFF36261A.toInt()
        val nameX = rect.left + dp(20f)
        drawHandInk(c, regionLabel, nameX, rect.top + dp(15.6f), inkText)

        inkText.typeface = serif
        inkText.textSize = dp(7.3f)
        inkText.color = 0xFF766044.toInt()
        c.drawText(coord, nameX, rect.bottom - dp(8.2f), inkText)

        c.restore()
    }

    private fun drawHandInk(c: Canvas, s: String, x: Float, y: Float, p: Paint) {
        var cx = x
        var i = 0
        for (ch in s) {
            val wobble = ((i * 37 + ch.code) % 7) - 3
            val glyph = ch.toString()
            c.save()
            c.rotate(wobble * 0.55f, cx, y)
            c.drawText(glyph, cx, y + wobble * dp(0.18f), p)
            c.restore()
            cx += p.measureText(glyph)
            i++
        }
    }

    private fun drawCentered(c: Canvas, s: String, x: Float, y: Float, p: Paint) {
        c.drawText(s, x - p.measureText(s) / 2f, y - (p.descent() + p.ascent()) / 2f, p)
    }

    // ------------------------------------------------------------------
    // 텍스트 유틸
    // ------------------------------------------------------------------

    companion object {
        /** 스틱 데드존 / 최대치 (베이스 반지름 비율) */
        const val STICK_DEAD = 0.18f
        const val STICK_MAX = 0.90f
    }

    /** 줄바꿈 규칙은 Type.wrap 이 담당한다(한글 줄바꿈 — 금칙 문자 처리 포함) */
    fun wrapText(txt: String, tp: Paint, width: Float): List<String> = Type.wrap(txt, tp, width)
}
