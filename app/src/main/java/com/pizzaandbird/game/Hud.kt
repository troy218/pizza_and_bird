package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 화면 좌표(실제 해상도) 기반 HUD.
 * - 좌상단: 배고픔/행운/돈/피자/카메라/시각 패널
 * - 우상단: 원형 한국 지도 미니맵 (탭하면 큰 지도)
 * - 하단: 아날로그 조이스틱 + A/B/카메라/메뉴/달리기/간식 버튼
 *
 * v0.2.2 조이스틱 업그레이드
 * - 금속 림 + 유리 그라데이션 베이스, 32눈금 링, 4방향 셰브론(입력 시 점등)
 * - 3D 스틱 캡(스페큘러/접촉 그림자/기울어진 축) + 피자 엠블럼
 * - 민 만큼 채워지는 게이지 호, 달리기 회전 링, 잡으면 앰버 글로우
 * - 플로팅 스틱: 왼쪽 아래 영역을 드래그하면 그 자리에 스틱이 생기고 놓으면 홈으로 돌아온다
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

    // ----- 레이아웃(px) -----
    var dpadCx = 0f; var dpadCy = 0f; var dpadR = 0f
    var dpadHomeX = 0f; var dpadHomeY = 0f       // 조이스틱 기본 자리 (플로팅 후 돌아오는 곳)
    var aCx = 0f; var aCy = 0f; var aR = 0f
    var bCx = 0f; var bCy = 0f; var bR = 0f
    var camBCx = 0f; var camBCy = 0f; var camBR = 0f
    var menuCx = 0f; var menuCy = 0f; var menuR = 0f
    var runCx = 0f; var runCy = 0f; var runR = 0f
    var eatCx = 0f; var eatCy = 0f; var eatR = 0f
    var mmCx = 0f; var mmCy = 0f; var mmR = 0f

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

    // ----- 조이스틱 애니메이션 상태 (게임 스레드 전용) -----
    private var stickFloating = false    // 손가락을 따라 베이스가 옮겨진 상태
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
    private val clipPath = Path()
    private val tickDirs = Array(32) { i ->
        val a = i * (Math.PI.toFloat() * 2f / 32f)
        PointF(cos(a), sin(a))
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
        dpadR = dp(56f)
        dpadHomeX = dp(24f) + dpadR
        dpadHomeY = h - dp(22f) - dpadR
        // 화면이 바뀌면 스틱을 홈 자리로 되돌린다
        dpadCx = dpadHomeX
        dpadCy = dpadHomeY
        stickFloating = false
        buildStickShaders(dpadR)

        aR = dp(27f)
        aCx = w - dp(26f) - aR
        aCy = h - dp(26f) - aR

        bR = dp(21f)
        bCx = aCx - aR - dp(8f) - bR
        bCy = h - dp(22f) - bR

        camBR = dp(21f)
        camBCx = aCx
        camBCy = aCy - aR - dp(12f) - camBR

        runR = dp(19f)
        runCx = camBCx - camBR - dp(8f) - runR
        runCy = camBCy + dp(2f)

        eatR = dp(19f)
        eatCx = runCx - runR - dp(8f) - eatR
        eatCy = camBCy + dp(2f)

        menuR = dp(16f)
        menuCx = bCx - bR - dp(10f) - menuR
        menuCy = bCy + dp(6f)

        mmR = dp(58f)
        mmCx = w - dp(16f) - mmR
        mmCy = dp(16f) + mmR
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
        if (inCircle(x, y, dpadCx, dpadCy, dpadR * 1.15f)) return Ctrl.DPAD
        if (inCircle(x, y, aCx, aCy, aR * 1.22f)) return Ctrl.A
        if (inCircle(x, y, bCx, bCy, bR * 1.25f)) return Ctrl.B
        if (inCircle(x, y, camBCx, camBCy, camBR * 1.25f)) return Ctrl.CAM
        if (inCircle(x, y, runCx, runCy, runR * 1.3f)) return Ctrl.RUN
        if (inCircle(x, y, eatCx, eatCy, eatR * 1.3f)) return Ctrl.EAT
        if (inCircle(x, y, menuCx, menuCy, menuR * 1.35f)) return Ctrl.MENU
        if (showMinimap && inCircle(x, y, mmCx, mmCy, mmR * 0.96f)) return Ctrl.MAP
        return Ctrl.NONE
    }

    /**
     * 스틱 벡터: 데드존(0.22R) 밖으로 밀린 만큼을 0→1로 정규화해 돌려준다.
     * 아날로그 이동을 켜면 이 크기만큼 속도가 줄고, 끄면 방향만 쓴다.
     */
    fun dpadVector(p: PointF): PointF {
        val dx = p.x - dpadCx
        val dy = p.y - dpadCy
        val len = sqrt(dx * dx + dy * dy)
        val dead = dpadR * STICK_DEAD
        val max = dpadR * STICK_MAX
        if (len <= dead || len == 0f) return PointF(0f, 0f)
        val k = ((len - dead) / (max - dead)).coerceIn(0f, 1f)
        return PointF(dx / len * k, dy / len * k)
    }

    // ------------------------------------------------------------------
    // 조이스틱 (플로팅) 제어
    // ------------------------------------------------------------------

    /**
     * 움직이는 스틱 영역: 화면 왼쪽 아래.
     * 이 영역을 '드래그'하면 그 자리에 스틱이 생긴다 (탭은 그대로 월드 탭으로 동작).
     */
    fun inStickZone(x: Float, y: Float): Boolean {
        if (!showControls || !game.state.floatStick) return false
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        if (w <= 0f || h <= 0f) return false
        return x < w * 0.46f && y > h * 0.36f && y < h - dp(2f)
    }

    /** 손가락이 닿은 자리로 스틱 베이스를 옮긴다 (화면 밖으로 나가진 않게) */
    fun grabStickAt(x: Float, y: Float) {
        if (!game.state.floatStick) return
        val R = dpadR
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        dpadCx = x.coerceIn(R * 0.8f, (w * 0.5f).coerceAtLeast(R))
        dpadCy = y.coerceIn(h * 0.34f, (h - R * 0.4f).coerceAtLeast(h * 0.34f))
        stickFloating = true
    }

    /** 스틱을 놓음 → 홈 자리로 부드럽게 복귀 */
    fun releaseStick() {
        stickFloating = false
    }

    private fun buildStickShaders(R: Float) {
        val kr = R * KNOB_R
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
        updateStick(dt)
    }

    /** 조이스틱 캡/베이스 애니메이션 (입력은 즉시, 그림은 부드럽게) */
    private fun updateStick(dt: Float) {
        val held = Ctrl.DPAD in game.input.activeControls()
        var tvx = 0f
        var tvy = 0f
        if (held) {
            val v = dpadVector(game.input.dpadTouchPoint())
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

        // 놓으면 홈 자리로 스프링 복귀
        if (!stickFloating) {
            val kb = (dt * 14f).coerceIn(0f, 1f)
            dpadCx += (dpadHomeX - dpadCx) * kb
            dpadCy += (dpadHomeY - dpadCy) * kb
            if (abs(dpadCx - dpadHomeX) < 0.6f && abs(dpadCy - dpadHomeY) < 0.6f) {
                dpadCx = dpadHomeX
                dpadCy = dpadHomeY
            }
        }

        // 달리기 링 회전
        val running = game.input.isRun && stickMag > 0.12f
        dashRot = (dashRot + dt * (if (running) 170f + 260f * stickMag else 14f)) % 360f
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
    // 컨트롤
    // ------------------------------------------------------------------

    private fun drawControls(c: Canvas) {
        val active = game.input.activeControls()

        // 조이스틱
        drawJoystick(c)

        // A (상호작용)
        drawButton(
            c, aCx, aCy, aR,
            if (Ctrl.A in active) 0xFFD99B26.toInt() else 0xFFF2B63C.toInt(),
            "A", dp(18f), Ctrl.A in active
        )
        // B (자전거)
        drawButton(
            c, bCx, bCy, bR,
            if (Ctrl.B in active) 0xFF9F7FC8.toInt() else 0xFFC3A3E8.toInt(),
            "B", dp(14f), Ctrl.B in active
        )
        // 카메라
        drawButton(
            c, camBCx, camBCy, camBR,
            if (photoModeHint) 0xFFE2574C.toInt() else Color.argb(225, 74, 74, 88),
            null, 0f, Ctrl.CAM in active
        )
        val cam = game.assets.cameraIcon
        val cw = cam.width * (dp(20f) / 20f)
        val ch = cam.height * (dp(20f) / 16f)
        c.drawBitmap(cam, null, RectF(camBCx - cw / 2, camBCy - ch / 2, camBCx + cw / 2, camBCy + ch / 2), game.assets.sprPaint)

        // 달리기 (») — 누르고 있으면 강조
        val running = game.input.isRun
        drawButton(
            c, runCx, runCy, runR,
            if (running) 0xFFF2D06B.toInt() else if (Ctrl.RUN in active) 0xFFD9A03C.toInt() else Color.argb(225, 74, 74, 88),
            null, 0f, Ctrl.RUN in active
        )
        text.textSize = dp(17f)
        text.color = if (running) 0xFF4A3728.toInt() else Color.argb(230, 248, 239, 220)
        val runLabel = "»"
        c.drawText(runLabel, runCx - text.measureText(runLabel) / 2, runCy - (text.descent() + text.ascent()) / 2, text)

        // 간식 (🍕) — 피자 개수 표시
        val pizzaN = game.state.pizzaCount
        drawButton(
            c, eatCx, eatCy, eatR,
            if (Ctrl.EAT in active) 0xFFD99B26.toInt() else if (pizzaN > 0) 0xFFF2B63C.toInt() else Color.argb(205, 90, 84, 100),
            null, 0f, Ctrl.EAT in active
        )
        val pz = game.assets.pizzaIcon
        val psz = dp(20f)
        c.drawBitmap(pz, null, RectF(eatCx - psz / 2, eatCy - psz / 2, eatCx + psz / 2, eatCy + psz / 2), game.assets.sprPaint)
        if (pizzaN > 0) {
            fill.color = 0xFF6B4F35.toInt()
            c.drawCircle(eatCx + eatR * 0.62f, eatCy - eatR * 0.62f, dp(8.5f), fill)
            stroke.color = Color.argb(160, 248, 239, 220)
            stroke.strokeWidth = dp(1.2f)
            c.drawCircle(eatCx + eatR * 0.62f, eatCy - eatR * 0.62f, dp(8.5f), stroke)
            text.textSize = dp(10f)
            text.color = 0xFFF8EFDC.toInt()
            c.drawText("$pizzaN", eatCx + eatR * 0.62f - text.measureText("$pizzaN") / 2, eatCy - eatR * 0.62f - (text.descent() + text.ascent()) / 2, text)
        }

        // 메뉴 (≡)
        drawButton(
            c, menuCx, menuCy, menuR,
            if (Ctrl.MENU in active) 0xFF9F7FC8.toInt() else Color.argb(225, 74, 74, 88),
            null, 0f, Ctrl.MENU in active
        )
        linePaint.color = Color.argb(225, 248, 239, 220)
        linePaint.strokeWidth = dp(2.2f)
        for (i in -1..1) {
            c.drawLine(menuCx - dp(6f), menuCy + i * dp(4f), menuCx + dp(6f), menuCy + i * dp(4f), linePaint)
        }

        // 미니맵 살짝 강조 (탭 가능 힌트)
        if (showMinimap && Ctrl.MAP in active) {
            stroke.color = 0xFFF2D06B.toInt()
            stroke.strokeWidth = dp(3f)
            c.drawCircle(mmCx, mmCy, mmR + dp(3f), stroke)
        }
    }

    // ------------------------------------------------------------------
    // 조이스틱 (v0.2.2 디자인)
    // ------------------------------------------------------------------

    private fun drawJoystick(c: Canvas) {
        val R = dpadR
        if (R <= 0f) return
        val press = stickPress
        val mag = stickMag
        val breathe = 0.5f + 0.5f * sin(game.time * 1.5f)
        val running = game.input.isRun && mag > 0.12f
        val knobR0 = R * KNOB_R
        val knobScale = 1f + 0.075f * press
        val travel = R * KNOB_TRAVEL
        val kx = stickVX * travel
        val ky = stickVY * travel

        c.save()
        c.translate(dpadCx, dpadCy)

        // 플로팅 중: 원래 자리(홈)를 점선으로 알려준다
        if (stickFloating) {
            stroke.color = Color.argb(60, 248, 239, 220)
            stroke.strokeWidth = dp(1.6f)
            stroke.pathEffect = ghostDash
            c.drawCircle(dpadHomeX - dpadCx, dpadHomeY - dpadCy, R * 0.9f, stroke)
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
            drawChevron(c, ux * R * 0.615f, uy * R * 0.615f, ux, uy, ix * ux + iy * uy, R)
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
        // 하단 음영
        fill.color = Color.argb(46, 74, 50, 26)
        tmpRect.set(-knobR0, knobR0 * 0.18f, knobR0, knobR0 * 1.1f)
        c.drawOval(tmpRect, fill)
        // 상단 글로스
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

    private fun drawButton(
        c: Canvas, cx: Float, cy: Float, r: Float, color: Int,
        label: String?, labelSize: Float, pressed: Boolean = false
    ) {
        val pr = if (pressed) r * 0.93f else r
        val oy = if (pressed) dp(1.5f) else 0f
        // 그림자
        fill.color = Color.argb(if (pressed) 60 else 96, 10, 8, 18)
        c.drawCircle(cx + dp(1f), cy + dp(3f), pr, fill)
        // 몸통
        fill.color = color
        c.drawCircle(cx, cy + oy, pr, fill)
        // 입체감(글로스/음영) — 원 안쪽만
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
        // 림
        stroke.color = Color.argb(200, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(cx, cy + oy, pr - dp(1f), stroke)
        stroke.color = Color.argb(70, 40, 28, 20)
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(cx, cy + oy, pr, stroke)
        if (label != null) {
            text.color = 0xFF3A2A24.toInt()
            text.textSize = labelSize
            val tw = text.measureText(label)
            val ty = cy + oy - (text.descent() + text.ascent()) / 2f
            c.drawText(label, cx - tw / 2, ty, text)
        }
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

    companion object {
        /** 스틱 데드존 / 최대치 (반지름 비율) */
        const val STICK_DEAD = 0.22f
        const val STICK_MAX = 0.85f

        /** 캡 반지름 / 최대 이동 (반지름 비율) */
        const val KNOB_R = 0.42f
        const val KNOB_TRAVEL = 0.44f
    }

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
