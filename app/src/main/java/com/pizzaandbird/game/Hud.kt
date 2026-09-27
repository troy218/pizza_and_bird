package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.sqrt

/**
 * 화면 좌표(실제 해상도) 기반 HUD — 듀랑고(Wild Lands) 스타일 조작 구조.
 * - 좌상단: 배고픔/행운/돈/피자/카메라/시각 패널
 * - 우상단: 원형 한국 지도 미니맵 (탭하면 큰 지도)
 * - 좌하단: 플로팅 가상 조이스틱 (손을 대는 자리에 베이스가 생긴다)
 * - 우하단: 육각 메인 버튼 + 부채꼴 아크 버튼(자전거·카메라·달리기·간식)
 * - 좌하단 구석: 메뉴 클러스터(≡)
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

    private class Message(var text: String, var t: Float)

    // ----- 페인트 캐시 -----
    private val fill = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 한국 실루엣 (정규화 -1..1, y 아래가 +)
    private val koreaPath = Path()
    private val jeju = PointF(-0.12f, 0.48f)

    init {
        val pts = listOf(
            -0.42f to -0.42f, -0.30f to -0.62f, -0.10f to -0.74f, 0.12f to -0.80f,
            0.34f to -0.70f, 0.46f to -0.74f, 0.58f to -0.60f, 0.62f to -0.40f,
            0.70f to -0.22f, 0.74f to -0.02f, 0.70f to 0.14f, 0.52f to 0.18f,
            0.34f to 0.28f, 0.18f to 0.38f, 0.02f to 0.32f, -0.10f to 0.18f,
            -0.22f to 0.02f, -0.30f to -0.12f, -0.44f to -0.24f, -0.36f to -0.32f
        )
        koreaPath.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) koreaPath.lineTo(pts[i].first, pts[i].second)
        koreaPath.close()
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

        // --- 미니맵 (오른쪽 위) ---
        mmR = dp(58f)
        mmCx = wf - dp(16f) - mmR
        mmCy = dp(16f) + mmR
    }

    // ------------------------------------------------------------------
    // 조이스틱 (게임 스레드에서 Input이 호출)
    // ------------------------------------------------------------------

    /** 손을 댄 자리가 조이스틱 베이스가 된다 (듀랑고식 플로팅) */
    fun grabStick(x: Float, y: Float) {
        stickHeld = true
        val minX = stickBaseR * 0.35f
        stickBaseX = x.coerceIn(minX, stickZoneRight)
        stickBaseY = y.coerceIn(stickZoneTop, game.screenH.toFloat() - stickBaseR * 0.35f)
    }

    fun releaseStick() {
        stickHeld = false
        stickBaseX = stickIdleX
        stickBaseY = stickIdleY
    }

    /** 베이스 대비 손가락 위치 → 이동 벡터 (길이 0..1) */
    fun stickVector(p: PointF): PointF {
        val dx = p.x - stickBaseX
        val dy = p.y - stickBaseY
        val len = sqrt(dx * dx + dy * dy)
        if (len < stickBaseR * 0.18f || len == 0f) return PointF(0f, 0f)
        val maxLen = stickBaseR * 0.9f
        val k = if (len > maxLen) 1f else (len - stickBaseR * 0.18f) / (maxLen - stickBaseR * 0.18f)
        return PointF(dx / len * k, dy / len * k)
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
            // 컨트롤이 숨겨져 있어도 미니맵은 탭 가능
            if (showMinimap && inCircle(x, y, mmCx, mmCy, mmR * 0.96f)) return Ctrl.MAP
            return Ctrl.NONE
        }
        // 버튼 최우선 (메뉴 클러스터가 조이스틱 구역과 겹치므로 먼저 판정)
        if (inCircle(x, y, menuCx, menuCy, menuR * 1.35f)) return Ctrl.MENU
        if (inCircle(x, y, mainCx, mainCy, mainR * 1.22f)) return Ctrl.A
        if (inCircle(x, y, bikeCx, bikeCy, bikeR * 1.3f)) return Ctrl.B
        if (inCircle(x, y, camBCx, camBCy, camBR * 1.3f)) return Ctrl.CAM
        if (inCircle(x, y, runCx, runCy, runR * 1.3f)) return Ctrl.RUN
        if (inCircle(x, y, eatCx, eatCy, eatR * 1.3f)) return Ctrl.EAT
        if (showMinimap && inCircle(x, y, mmCx, mmCy, mmR * 0.96f)) return Ctrl.MAP
        // 듀랑고식: 왼쪽 아래 구역은 어디를 짚어도 그 자리가 조이스틱
        if (!stickHeld && x < stickZoneRight && y > stickZoneTop) return Ctrl.STICK
        return Ctrl.NONE
    }

    // ------------------------------------------------------------------
    // 메시지(토스트) / 배너
    // ------------------------------------------------------------------

    fun toast(msg: String) {
        messages.add(Message(msg, 2.8f))
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
    }

    // ------------------------------------------------------------------
    // 그리기
    // ------------------------------------------------------------------

    fun draw(c: Canvas) {
        if (showStats) drawStats(c)
        if (showMinimap) {
            drawMinimap(c, mmCx, mmCy, mmR, false)
            if (regionLabel.isNotEmpty()) {
                drawChip(c, mmCx, mmCy + mmR + dp(18f), "📍 $regionLabel")
            }
        }
        if (questLabel != null) {
            drawChip(c, questChipX(), questChipY(), "🔍 $questLabel")
        }
        if (showControls) drawControls(c)
        if (photoModeHint) drawPhotoHint(c)
        drawBanner(c)
        drawMessages(c)
    }

    private fun questChipX(): Float = dp(16f) + dp(162f) / 2f
    private fun questChipY(): Float = dp(12f) + dp(126f) + dp(18f)

    private fun drawStats(c: Canvas) {
        val s = game.state
        val left = dp(12f)
        val top = dp(12f)
        val w = dp(162f)
        val h = dp(126f)

        // 패널
        fill.color = Color.argb(216, 248, 239, 220)
        stroke.color = 0xFF6B4F35.toInt()
        stroke.strokeWidth = dp(2.5f)
        val r = RectF(left, top, left + w, top + h)
        c.drawRoundRect(r, dp(10f), dp(10f), fill)
        c.drawRoundRect(r, dp(10f), dp(10f), stroke)

        val a = game.assets

        // 배고픔
        val iy1 = top + dp(12f)
        val iconSz = dp(16f)
        c.drawBitmap(a.pizzaIcon, null, RectF(left + dp(12f), iy1, left + dp(12f) + iconSz, iy1 + iconSz), a.sprPaint)
        drawBar(c, left + dp(36f), iy1 + dp(2f), dp(112f), dp(12f), s.hunger,
            if (s.hunger < 25f) 0xFFE2574C.toInt() else 0xFFF2913C.toInt())

        // 행운
        val iy2 = iy1 + dp(22f)
        c.drawBitmap(a.cloverIcon, null, RectF(left + dp(12f), iy2, left + dp(12f) + iconSz, iy2 + iconSz), a.sprPaint)
        drawBar(c, left + dp(36f), iy2 + dp(2f), dp(112f), dp(12f), s.effectiveLuck(), 0xFF6FBA6B.toInt())

        // 돈
        text.color = 0xFF4A3728.toInt()
        text.textSize = dp(14f)
        c.drawText(won(s.money), left + dp(12f), iy2 + dp(36f), text)

        // 피자 / 카메라
        text.textSize = dp(12f)
        c.drawBitmap(a.pizzaIcon, null, RectF(left + dp(12f), iy2 + dp(42f), left + dp(12f) + dp(14f), iy2 + dp(42f) + dp(14f)), a.sprPaint)
        c.drawText("×${s.pizzaCount}", left + dp(30f), iy2 + dp(53f), text)
        c.drawBitmap(a.cameraIcon, null, RectF(left + dp(54f), iy2 + dp(42f), left + dp(54f) + dp(17f), iy2 + dp(42f) + dp(14f)), a.sprPaint)
        c.drawText("Lv.${s.cameraLevel}", left + dp(76f), iy2 + dp(53f), text)

        // 시각 + 사진
        val night = s.isNight()
        val clockIcon = if (night) a.moonIcon else a.sunIcon
        c.drawBitmap(clockIcon, null, RectF(left + dp(11f), iy2 + dp(62f), left + dp(11f) + dp(14f), iy2 + dp(62f) + dp(14f)), a.sprPaint)
        text.textSize = dp(11.5f)
        text.color = 0xFF6B5A48.toInt()
        c.drawText(s.timeLabel(), left + dp(30f), iy2 + dp(73f), text)
        c.drawText("📷 ${s.photos}", left + dp(76f), iy2 + dp(73f), text)
    }

    private fun drawBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, v: Float, color: Int) {
        fill.color = Color.argb(255, 214, 197, 164)
        c.drawRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, fill)
        val p = (v / 100f).coerceIn(0f, 1f)
        if (p > 0.01f) {
            fill.color = color
            c.drawRoundRect(RectF(x + dp(1.5f), y + dp(1.5f), x + (w - dp(1.5f)) * p + dp(1.5f), y + h - dp(1.5f)), h / 2, h / 2, fill)
        }
        stroke.color = 0xFF6B4F35.toInt()
        stroke.strokeWidth = dp(1.5f)
        c.drawRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, stroke)
    }

    private fun drawChip(c: Canvas, cx: Float, cy: Float, txt: String) {
        text.textSize = dp(12f)
        val tw = text.measureText(txt)
        val pad = dp(8f)
        val r = RectF(cx - tw / 2 - pad, cy - dp(12f), cx + tw / 2 + pad, cy + dp(12f))
        fill.color = Color.argb(200, 58, 52, 74)
        c.drawRoundRect(r, dp(12f), dp(12f), fill)
        text.color = 0xFFF8EFDC.toInt()
        val ty = cy - (text.descent() + text.ascent()) / 2f
        c.drawText(txt, cx - tw / 2, ty, text)
    }

    private fun drawMessages(c: Canvas) {
        text.textSize = dp(12.5f)
        var y = dp(20f)
        for (m in messages) {
            val tw = text.measureText(m.text)
            val cx = game.screenW / 2f
            val pad = dp(9f)
            val alpha = (255 * (m.t.coerceIn(0f, 0.4f) / 0.4f)).toInt()
            fill.color = Color.argb((alpha * 0.82f).toInt(), 248, 239, 220)
            val r = RectF(cx - tw / 2 - pad, y - dp(12f), cx + tw / 2 + pad, y + dp(13f))
            c.drawRoundRect(r, dp(12f), dp(12f), fill)
            stroke.color = Color.argb((alpha * 0.9f).toInt(), 107, 79, 53)
            stroke.strokeWidth = dp(1.5f)
            c.drawRoundRect(r, dp(12f), dp(12f), stroke)
            text.color = Color.argb(alpha, 74, 55, 40)
            val ty = y - (text.descent() + text.ascent()) / 2f
            c.drawText(m.text, cx - tw / 2, ty, text)
            y += dp(28f)
        }
    }

    private fun drawBanner(c: Canvas) {
        val bt = bannerText ?: return
        if (bannerT <= 0f) return
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        val fadeIn = (bannerT - 2.1f).coerceIn(0f, 1f)      // 마지막 0.5초 페이드아웃
        val alpha = (255 * fadeIn).toInt()

        text.textSize = dp(26f)
        text.color = Color.argb(alpha, 248, 239, 220)
        val tw = text.measureText(bt)
        val cx = w / 2f
        val cy = h * 0.24f
        val r = RectF(cx - tw / 2 - dp(20f), cy - dp(24f), cx + tw / 2 + dp(20f), cy + dp(24f))
        fill.color = Color.argb((alpha * 0.72f).toInt(), 43, 38, 58)
        c.drawRoundRect(r, dp(16f), dp(16f), fill)
        stroke.color = Color.argb((alpha * 0.9f).toInt(), 242, 208, 107)
        stroke.strokeWidth = dp(2f)
        c.drawRoundRect(r, dp(16f), dp(16f), stroke)
        val ty = cy - (text.descent() + text.ascent()) / 2f
        c.drawText(bt, cx - tw / 2, ty, text)
    }

    private fun drawPhotoHint(c: Canvas) {
        drawChip(c, game.screenW / 2f, dp(24f), "카메라 모드! 새를 탭해서 촬영하세요")
    }

    // ------------------------------------------------------------------
    // 컨트롤 (듀랑고 스타일)
    // ------------------------------------------------------------------

    private fun drawControls(c: Canvas) {
        val active = game.input.activeControls()

        // ------------------------------------------------------------
        // 1) 플로팅 조이스틱 (왼쪽) — 잡으면 손을 댄 자리에 베이스가 생긴다
        // ------------------------------------------------------------
        val grabbed = Ctrl.STICK in active
        val bx = if (stickHeld) stickBaseX else stickIdleX
        val by = if (stickHeld) stickBaseY else stickIdleY
        val ringAlpha = if (stickHeld) 1f else 0.62f

        fill.color = Color.argb((96 * ringAlpha).toInt(), 40, 36, 54)
        c.drawCircle(bx, by, stickBaseR, fill)
        stroke.color = Color.argb((170 * ringAlpha).toInt(), 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(bx, by, stickBaseR, stroke)
        stroke.color = Color.argb((70 * ringAlpha).toInt(), 248, 239, 220)
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(bx, by, stickBaseR * 0.55f, stroke)

        // 노브 (손가락 방향으로 이동)
        val v = if (grabbed) stickVector(game.input.stickTouchPoint()) else PointF(0f, 0f)
        val knobMax = stickBaseR - stickKnobR
        val kx = bx + v.x * knobMax
        val ky = by + v.y * knobMax
        fill.color = if (grabbed) 0xFFF2D06B.toInt() else Color.argb(215, 248, 239, 220)
        c.drawCircle(kx, ky, stickKnobR, fill)
        stroke.color = Color.argb(190, 74, 55, 40)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(kx, ky, stickKnobR, stroke)

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
        })
        drawGlyph(c, bikeCx, bikeCy, "🚲", dp(17f))

        // 카메라 (📷)
        val camPressed = Ctrl.CAM in active
        drawArcButton(c, camBCx, camBCy, camBR, when {
            camPressed -> 0xFFD99B26.toInt()
            photoModeHint -> 0xFFE2574C.toInt()
            else -> Color.argb(220, 74, 74, 88)
        })
        val cam = game.assets.cameraIcon
        val cw = cam.width * (dp(20f) / 20f)
        val ch = cam.height * (dp(20f) / 16f)
        c.drawBitmap(cam, null, RectF(camBCx - cw / 2, camBCy - ch / 2, camBCx + cw / 2, camBCy + ch / 2), game.assets.sprPaint)

        // 달리기 (») — 누르고 있으면 강조
        val running = game.input.isRun
        drawArcButton(
            c, runCx, runCy, runR,
            if (running) 0xFFF2D06B.toInt() else if (Ctrl.RUN in active) 0xFFD9A03C.toInt() else Color.argb(220, 74, 74, 88)
        )
        drawGlyph(c, runCx, runCy, "»", dp(19f), if (running) 0xFF4A3728.toInt() else Color.argb(235, 248, 239, 220))

        // 간식 (🍕) — 피자 개수 표시
        val pizzaN = game.state.pizzaCount
        drawArcButton(
            c, eatCx, eatCy, eatR,
            if (Ctrl.EAT in active) 0xFFD99B26.toInt() else if (pizzaN > 0) 0xFFF2B63C.toInt() else Color.argb(200, 90, 84, 100)
        )
        val pz = game.assets.pizzaIcon
        val psz = dp(20f)
        c.drawBitmap(pz, null, RectF(eatCx - psz / 2, eatCy - psz / 2, eatCx + psz / 2, eatCy + psz / 2), game.assets.sprPaint)
        if (pizzaN > 0) {
            fill.color = 0xFF6B4F35.toInt()
            c.drawCircle(eatCx + eatR * 0.62f, eatCy - eatR * 0.62f, dp(8.5f), fill)
            text.textSize = dp(10f)
            text.color = 0xFFF8EFDC.toInt()
            c.drawText("$pizzaN", eatCx + eatR * 0.62f - text.measureText("$pizzaN") / 2, eatCy - eatR * 0.62f - (text.descent() + text.ascent()) / 2, text)
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

    /** 원형 아크 버튼 몸통 */
    private fun drawArcButton(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
        stroke.color = Color.argb(200, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(cx, cy, r, stroke)
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
    // 원형 미니맵 (한국 지도)
    // ------------------------------------------------------------------

    fun drawMinimap(c: Canvas, cx: Float, cy: Float, r: Float, showNames: Boolean) {
        val s = game.state
        val scale = r * 0.94f

        fun px(mm: Float): Float = cx + mm * scale
        fun py(mm: Float): Float = cy + mm * scale

        // 바다
        fill.color = 0xFFA8D8E8.toInt()
        c.drawCircle(cx, cy, r, fill)

        c.save()
        c.translate(cx, cy)
        c.scale(scale, scale)
        // 육지
        fill.color = 0xFFB8DCA0.toInt()
        c.drawPath(koreaPath, fill)
        // 제주도
        c.drawCircle(jeju.x, jeju.y, 0.09f, fill)
        c.restore()

        // 연결선 (방문한 지역끼리)
        linePaint.color = Color.argb(110, 255, 255, 255)
        linePaint.strokeWidth = dp(1.6f)
        for (reg in Regions.ALL) {
            for ((_, targetId) in Regions.exits(reg.id)) {
                val target = Regions.byId[targetId] ?: continue
                if (reg.id in s.visited && targetId in s.visited) {
                    linePaint.alpha = 140
                    c.drawLine(px(reg.mmX), py(reg.mmY), px(target.mmX), py(target.mmY), linePaint)
                }
            }
        }

        // 지역 점
        for (reg in Regions.ALL) {
            val visited = reg.id in s.visited
            val isHome = reg.id == s.homeRegion
            val isCurrent = reg.id == s.region
            val dotR = if (isCurrent) r * 0.085f else r * 0.06f

            if (visited) {
                fill.color = if (isCurrent) 0xFFE2574C.toInt() else 0xFFF7CE5B.toInt()
                c.drawCircle(px(reg.mmX), py(reg.mmY), dotR, fill)
                if (isCurrent) {
                    stroke.color = Color.argb(160, 226, 87, 76)
                    stroke.strokeWidth = dp(2f)
                    val pulse = r * (0.13f + 0.03f * kotlin.math.sin(game.time * 4f))
                    c.drawCircle(px(reg.mmX), py(reg.mmY), pulse, stroke)
                }
            } else {
                fill.color = Color.argb(120, 90, 80, 70)
                c.drawCircle(px(reg.mmX), py(reg.mmY), dotR * 0.8f, fill)
            }

            if (showNames) {
                text.textSize = dp(11f)
                text.color = if (isCurrent) 0xFFE2574C.toInt() else 0xFF4A3728.toInt()
                val nm = reg.name
                val tw = text.measureText(nm)
                c.drawText(nm, px(reg.mmX) - tw / 2, py(reg.mmY) + r * 0.15f, text)
            }

            if (isHome && showNames) {
                val hi = game.assets.houseIcon
                val hw = r * 0.11f
                c.drawBitmap(
                    hi, null,
                    RectF(px(reg.mmX) - hw / 2, py(reg.mmY) - r * 0.16f - hw, px(reg.mmX) + hw / 2, py(reg.mmY) - r * 0.16f),
                    game.assets.sprPaint
                )
            }
        }

        // 테두리
        stroke.color = 0xFF6B4F35.toInt()
        stroke.strokeWidth = dp(3f)
        c.drawCircle(cx, cy, r, stroke)
        stroke.color = Color.argb(90, 248, 239, 220)
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(cx, cy, r - dp(3f), stroke)
    }

    // ------------------------------------------------------------------
    // 텍스트 유틸
    // ------------------------------------------------------------------

    fun wrapText(txt: String, tp: Paint, width: Float): List<String> {
        val lines = ArrayList<String>()
        for (para in txt.split("\n")) {
            var cur = ""
            for (word in para.split(" ")) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (tp.measureText(test) <= width) {
                    cur = test
                } else {
                    if (cur.isNotEmpty()) lines.add(cur)
                    cur = word
                }
            }
            if (cur.isNotEmpty()) lines.add(cur)
        }
        return lines
    }
}
