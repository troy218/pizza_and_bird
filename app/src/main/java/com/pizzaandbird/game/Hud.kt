package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 화면 좌표(실제 해상도) 기반 HUD.
 * - 좌상단: 배고픔/행운/돈/피자/카메라/시각 패널
 * - 우상단: 원형 한국 지도 미니맵 (탭하면 큰 지도)
 * - 하단: D패드 + A/B/카메라/메뉴/달리기/간식 버튼
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

    // ----- 레이아웃(px) -----
    var dpadCx = 0f; var dpadCy = 0f; var dpadR = 0f
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

    // ------------------------------------------------------------------

    fun layout(w: Int, h: Int) {
        dpadR = dp(54f)
        dpadCx = dp(26f) + dpadR
        dpadCy = h - dp(24f) - dpadR

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
        if (inCircle(x, y, dpadCx, dpadCy, dpadR * 1.12f)) return Ctrl.DPAD
        if (inCircle(x, y, aCx, aCy, aR * 1.22f)) return Ctrl.A
        if (inCircle(x, y, bCx, bCy, bR * 1.25f)) return Ctrl.B
        if (inCircle(x, y, camBCx, camBCy, camBR * 1.25f)) return Ctrl.CAM
        if (inCircle(x, y, runCx, runCy, runR * 1.3f)) return Ctrl.RUN
        if (inCircle(x, y, eatCx, eatCy, eatR * 1.3f)) return Ctrl.EAT
        if (inCircle(x, y, menuCx, menuCy, menuR * 1.35f)) return Ctrl.MENU
        if (showMinimap && inCircle(x, y, mmCx, mmCy, mmR * 0.96f)) return Ctrl.MAP
        return Ctrl.NONE
    }

    fun dpadVector(p: PointF): PointF {
        val dx = p.x - dpadCx
        val dy = p.y - dpadCy
        val len = sqrt(dx * dx + dy * dy)
        if (len < dpadR * 0.22f || len == 0f) return PointF(0f, 0f)
        val k = if (len > dpadR * 0.85f) 1f else len / (dpadR * 0.85f)
        return PointF(dx / len * k, dy / len * k)
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

        // 배고픔 — 아이콘 메달 + 그라데이션 바
        val iy1 = top + dp(12f)
        val iconSz = dp(16f)
        fill.color = if (s.hunger < 25f) Color.argb(60, 226, 87, 76) else Color.argb(60, 242, 178, 60)
        c.drawCircle(left + dp(20f), iy1 + dp(8f), dp(11f), fill)
        c.drawBitmap(a.pizzaIcon, null, RectF(left + dp(12f), iy1, left + dp(12f) + iconSz, iy1 + iconSz), a.sprPaint)
        drawBar(c, left + dp(36f), iy1 + dp(2f), dp(112f), dp(12f), s.hunger,
            if (s.hunger < 25f) 0xFFE2574C.toInt() else 0xFFF2913C.toInt())

        // 행운
        val iy2 = iy1 + dp(22f)
        fill.color = Color.argb(60, 111, 186, 107)
        c.drawCircle(left + dp(20f), iy2 + dp(8f), dp(11f), fill)
        c.drawBitmap(a.cloverIcon, null, RectF(left + dp(12f), iy2, left + dp(12f) + iconSz, iy2 + iconSz), a.sprPaint)
        drawBar(c, left + dp(36f), iy2 + dp(2f), dp(112f), dp(12f), s.effectiveLuck(), 0xFF6FBA6B.toInt())

        UiKit.divider(c, game, left + dp(10f), left + w - dp(10f), top + dp(53f))

        // 돈 — 골드 도트 + 금액
        fill.color = 0xFFF2B63C.toInt()
        c.drawCircle(left + dp(18f), iy2 + dp(31f), dp(5f), fill)
        stroke.color = 0xFFB5651D.toInt()
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(left + dp(18f), iy2 + dp(31f), dp(5f), stroke)
        text.color = 0xFF4A3728.toInt()
        text.textSize = dp(14f)
        c.drawText(won(s.money), left + dp(28f), iy2 + dp(36f), text)

        // 피자 / 카메라
        text.textSize = dp(12f)
        c.drawBitmap(a.pizzaIcon, null, RectF(left + dp(12f), iy2 + dp(42f), left + dp(12f) + dp(14f), iy2 + dp(42f) + dp(14f)), a.sprPaint)
        c.drawText("×${s.pizzaCount}", left + dp(30f), iy2 + dp(53f), text)
        c.drawBitmap(a.cameraIcon, null, RectF(left + dp(58f), iy2 + dp(42f), left + dp(58f) + dp(17f), iy2 + dp(42f) + dp(14f)), a.sprPaint)
        c.drawText("Lv.${s.cameraLevel}", left + dp(79f), iy2 + dp(53f), text)

        UiKit.divider(c, game, left + dp(10f), left + w - dp(10f), top + dp(94f))

        // 시각 + 사진
        val night = s.isNight()
        val clockIcon = if (night) a.moonIcon else a.sunIcon
        c.drawBitmap(clockIcon, null, RectF(left + dp(11f), iy2 + dp(62f), left + dp(11f) + dp(14f), iy2 + dp(62f) + dp(14f)), a.sprPaint)
        text.textSize = dp(11.5f)
        text.color = 0xFF6B5A48.toInt()
        c.drawText(s.timeLabel(), left + dp(30f), iy2 + dp(73f), text)
        c.drawText("📷 ${s.photos}", left + dp(79f), iy2 + dp(73f), text)

        UiKit.divider(c, game, left + dp(10f), left + w - dp(10f), top + dp(114f))

        // 날씨: 새 스폰과 월드 연출에 적용되는 현재 상태
        val weather = s.weather()
        text.color = 0xFF587083.toInt()
        text.textSize = dp(11.5f)
        c.drawText("${weather.icon} ${weather.label}", left + dp(12f), iy2 + dp(92f), text)

        // 레벨 + 경험치 바
        val ly = iy2 + dp(96f)
        text.textSize = dp(11.5f)
        text.color = 0xFF4A3728.toInt()
        c.drawText("Lv.${s.level}", left + dp(12f), ly + dp(12f), text)
        text.textSize = dp(9f)
        text.color = 0xFF8A7360.toInt()
        val tt = s.title()
        c.drawText(tt, left + dp(46f), ly + dp(11f), text)
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
        var y = dp(20f) + if (photoModeHint) dp(62f) else 0f
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
            // 긴 문구는 화면에 맞게 축소/말줄임
            var sizeDp = 12.5f
            text.textSize = dp(sizeDp)
            var msg = m.text
            val maxW = game.screenW - dp(70f)
            if (text.measureText(msg) > maxW) {
                sizeDp = 11f
                text.textSize = dp(sizeDp)
            }
            if (text.measureText(msg) > maxW && maxW > dp(60f)) {
                while (msg.length > 4 && text.measureText("$msg…") > maxW) msg = msg.dropLast(1)
                msg = "$msg…"
            }
            val tw = text.measureText(msg)
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
            text.color = Color.argb(alpha, 248, 239, 220)
            val ty = yy - (text.descent() + text.ascent()) / 2f
            c.drawText(msg, cx - tw / 2, ty, text)
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

        text.textSize = dp(26f)
        val tw = text.measureText(bt)
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
        text.color = Color.argb(alpha, 248, 239, 220)
        val ty = cy - (text.descent() + text.ascent()) / 2f
        c.drawText(bt, cx - tw / 2, ty, text)
        c.restore()
    }


    private fun drawControls(c: Canvas) {
        val active = game.input.activeControls()

        // D패드 — 섀도우 + 다크 글래스 + 이너 링
        fill.color = Color.argb(70, 18, 12, 24)
        c.drawCircle(dpadCx, dpadCy + dp(3f), dpadR, fill)
        fill.color = Color.argb(110, 40, 36, 54)
        c.drawCircle(dpadCx, dpadCy, dpadR, fill)
        fill.color = Color.argb(60, 70, 63, 88)
        c.drawCircle(dpadCx, dpadCy, dpadR * 0.68f, fill)
        stroke.color = Color.argb(150, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(dpadCx, dpadCy, dpadR, stroke)
        stroke.color = Color.argb(60, 248, 239, 220)
        stroke.strokeWidth = dp(1f)
        c.drawCircle(dpadCx, dpadCy, dpadR * 0.68f, stroke)

        val tri = dp(10f)
        val inn = dpadR * 0.62f
        fun triAt(cx: Float, cy: Float, dirX: Float, dirY: Float, on: Boolean) {
            fill.color = if (on) 0xFFF2D06B.toInt() else Color.argb(200, 248, 239, 220)
            val px = -dirY; val py = dirX
            val path = Path()
            path.moveTo(cx + dirX * tri, cy + dirY * tri)
            path.lineTo(cx - dirX * tri * 0.5f + px * tri * 0.8f, cy - dirY * tri * 0.5f + py * tri * 0.8f)
            path.lineTo(cx - dirX * tri * 0.5f - px * tri * 0.8f, cy - dirY * tri * 0.5f - py * tri * 0.8f)
            path.close()
            c.drawPath(path, fill)
        }
        val dx = game.input.dirX
        val dy = game.input.dirY
        triAt(dpadCx, dpadCy - inn, 0f, -1f, dy < -0.25f)
        triAt(dpadCx, dpadCy + inn, 0f, 1f, dy > 0.25f)
        triAt(dpadCx - inn, dpadCy, -1f, 0f, dx < -0.25f)
        triAt(dpadCx + inn, dpadCy, 1f, 0f, dx > 0.25f)
        if (Ctrl.DPAD in active) {
            val v = dpadVector(game.input.dpadTouchPoint())
            val kx = dpadCx + v.x * inn
            val ky = dpadCy + v.y * inn
            fill.color = Color.argb(90, 20, 12, 8)
            c.drawCircle(kx, ky + dp(1.5f), dp(13f), fill)
            fill.color = Color.argb(170, 242, 182, 60)
            c.drawCircle(kx, ky, dp(13f), fill)
            fill.color = Color.argb(235, 255, 217, 122)
            c.drawCircle(kx, ky, dp(8.5f), fill)
        } else {
            // 중앙 홈
            fill.color = Color.argb(70, 20, 16, 28)
            c.drawCircle(dpadCx, dpadCy, dp(7f), fill)
        }

        // A (상호작용)
        drawButton(c, aCx, aCy, aR, if (Ctrl.A in active) 0xFFD99B26.toInt() else 0xFFF2B63C.toInt(), "A", dp(18f))
        // B (자전거)
        drawButton(c, bCx, bCy, bR, if (Ctrl.B in active) 0xFF9F7FC8.toInt() else 0xFFC3A3E8.toInt(), "B", dp(14f))
        // 카메라
        drawButton(c, camBCx, camBCy, camBR, if (photoModeHint) 0xFFE2574C.toInt() else Color.argb(220, 74, 74, 88), null, 0f)
        val cam = game.assets.cameraIcon
        val cw = cam.width * (dp(20f) / 20f)
        val ch = cam.height * (dp(20f) / 16f)
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

            val arcR = camBR + dp(8f)
            val arcRect = RectF(camBCx - arcR, camBCy - arcR, camBCx + arcR, camBCy + arcR)
            linePaint.color = Color.argb(225, 255, 232, 220)
            linePaint.strokeWidth = dp(3f)
            val baseDeg = (game.time * 96f) % 360f
            for (i in 0 until 6) {
                c.drawArc(arcRect, baseDeg + i * 60f, 20f, false, linePaint)
            }
        }

        // 달리기 (») — 누르고 있으면 강조
        val running = game.input.isRun
        drawButton(
            c, runCx, runCy, runR,
            if (running) 0xFFF2D06B.toInt() else if (Ctrl.RUN in active) 0xFFD9A03C.toInt() else Color.argb(220, 74, 74, 88),
            null, 0f
        )
        text.textSize = dp(17f)
        text.color = if (running) 0xFF4A3728.toInt() else Color.argb(230, 248, 239, 220)
        val runLabel = "»"
        c.drawText(runLabel, runCx - text.measureText(runLabel) / 2, runCy - (text.descent() + text.ascent()) / 2, text)

        // 간식 (🍕) — 피자 개수 표시
        val pizzaN = game.state.pizzaCount
        drawButton(
            c, eatCx, eatCy, eatR,
            if (Ctrl.EAT in active) 0xFFD99B26.toInt() else if (pizzaN > 0) 0xFFF2B63C.toInt() else Color.argb(200, 90, 84, 100),
            null, 0f
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
            text.textSize = dp(10f)
            text.color = 0xFFF8EFDC.toInt()
            c.drawText("$pizzaN", bx - text.measureText("$pizzaN") / 2, by - (text.descent() + text.ascent()) / 2, text)
        }

        // 메뉴 (≡)
        drawButton(c, menuCx, menuCy, menuR, if (Ctrl.MENU in active) 0xFF9F7FC8.toInt() else Color.argb(220, 74, 74, 88), null, 0f)
        linePaint.color = Color.argb(220, 248, 239, 220)
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

    private fun drawButton(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, label: String?, labelSize: Float) {
        // 섀도우
        fill.color = Color.argb(70, 18, 12, 24)
        c.drawCircle(cx, cy + dp(2.5f), r, fill)
        // 본문 + 위쪽 광택
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
        fill.color = Color.argb(52, 255, 255, 255)
        c.drawCircle(cx - r * 0.18f, cy - r * 0.26f, r * 0.62f, fill)
        // 테두리 + 이너 광택 링
        stroke.color = Color.argb(190, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(cx, cy, r, stroke)
        stroke.color = Color.argb(70, 255, 255, 255)
        stroke.strokeWidth = dp(1f)
        c.drawCircle(cx, cy, r - dp(3f), stroke)
        if (label != null) {
            text.textSize = labelSize
            val tw = text.measureText(label)
            val ty = cy - (text.descent() + text.ascent()) / 2f
            text.color = Color.argb(110, 30, 20, 10)
            c.drawText(label, cx - tw / 2, ty + dp(1f), text)
            text.color = 0xFF3A2A24.toInt()
            c.drawText(label, cx - tw / 2, ty, text)
        }
    }

    // ------------------------------------------------------------------
    // 원형 미니맵 (한국 지도)
    // ------------------------------------------------------------------

    fun drawMinimap(c: Canvas, cx: Float, cy: Float, r: Float, showNames: Boolean) {
        val s = game.state
        // 남한 전체가 원 안에 들어오도록 맞춤
        val b = KoreaMap.southBounds
        val fitScale = r * 1.86f / maxOf(b.width(), b.height())
        val ox = cx - b.centerX() * fitScale
        val oy = cy - b.centerY() * fitScale

        fun px(mm: Float): Float = ox + mm * fitScale
        fun py(mm: Float): Float = oy + mm * fitScale

        // 섀도우 + 바다 (밤에는 깊게)
        fill.color = Color.argb(80, 18, 12, 24)
        c.drawCircle(cx, cy + dp(3f), r + dp(1f), fill)
        fill.color = if (s.isNight()) 0xFF7FA8C8.toInt() else 0xFFA8D8E8.toInt()
        c.drawCircle(cx, cy, r, fill)

        c.save()
        val clip = Path()
        clip.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(clip)
        c.translate(ox, oy)
        c.scale(fitScale, fitScale)
        KoreaMap.drawLand(c, fitScale, 1, s.isNight())
        c.restore()

        // 연결선 (방문한 지역끼리)
        linePaint.color = Color.argb(110, 255, 255, 255)
        linePaint.strokeWidth = dp(1.4f)
        for (reg in Regions.ALL) {
            for ((_, targetId) in Regions.exits(reg.id)) {
                val target = Regions.byId[targetId] ?: continue
                if (reg.id in s.visited && targetId in s.visited) {
                    linePaint.alpha = 150
                    c.drawLine(px(reg.mmX), py(reg.mmY), px(target.mmX), py(target.mmY), linePaint)
                }
            }
        }

        // 지역 점
        for (reg in Regions.ALL) {
            val visited = reg.id in s.visited
            val isHome = reg.id == s.homeRegion
            val isCurrent = reg.id == s.region
            val dotR = if (isCurrent) r * 0.075f else r * 0.042f

            if (visited) {
                fill.color = if (isCurrent) 0xFFE2574C.toInt() else reg.kind.color
                c.drawCircle(px(reg.mmX), py(reg.mmY), dotR, fill)
                stroke.color = Color.argb(200, 255, 255, 255)
                stroke.strokeWidth = dp(0.9f)
                c.drawCircle(px(reg.mmX), py(reg.mmY), dotR, stroke)
                if (isCurrent) {
                    stroke.color = Color.argb(160, 226, 87, 76)
                    stroke.strokeWidth = dp(2f)
                    val pulse = r * (0.12f + 0.03f * kotlin.math.sin(game.time * 4f))
                    c.drawCircle(px(reg.mmX), py(reg.mmY), pulse, stroke)
                }
            } else {
                fill.color = Color.argb(110, 90, 80, 70)
                c.drawCircle(px(reg.mmX), py(reg.mmY), dotR * 0.75f, fill)
            }

            if (showNames && (isCurrent || isHome)) {
                text.textSize = dp(10f)
                text.color = if (isCurrent) 0xFFE2574C.toInt() else 0xFF4A3728.toInt()
                val nm = reg.name
                val tw = text.measureText(nm)
                c.drawText(nm, px(reg.mmX) - tw / 2, py(reg.mmY) + r * 0.14f, text)
            }

            if (isHome) {
                val hi = game.assets.houseIcon
                val hw = r * 0.10f
                c.drawBitmap(
                    hi, null,
                    RectF(px(reg.mmX) - hw / 2, py(reg.mmY) - r * 0.13f - hw, px(reg.mmX) + hw / 2, py(reg.mmY) - r * 0.13f),
                    game.assets.sprPaint
                )
            }
        }

        // 테두리 — 초코 + 골드 + 광택 헤어라인
        stroke.color = 0xFF6B4F35.toInt()
        stroke.strokeWidth = dp(3f)
        c.drawCircle(cx, cy, r, stroke)
        stroke.color = 0xFFE9C46A.toInt()
        stroke.strokeWidth = dp(1.4f)
        c.drawCircle(cx, cy, r - dp(2.6f), stroke)
        stroke.color = Color.argb(90, 248, 239, 220)
        stroke.strokeWidth = dp(1.2f)
        c.drawCircle(cx, cy, r - dp(4.5f), stroke)
        // 위쪽 광택
        fill.color = Color.argb(50, 255, 255, 255)
        c.drawCircle(cx - r * 0.3f, cy - r * 0.42f, r * 0.28f, fill)
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
