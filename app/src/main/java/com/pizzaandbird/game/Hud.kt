package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
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
        if (photoModeHint) drawPhotoHint(c)
        drawBanner(c)
        drawMessages(c)
    }

    private fun questChipX(): Float = dp(16f) + dp(162f) / 2f
    private fun questChipY(): Float = dp(12f) + dp(150f) + dp(18f)

    private fun drawStats(c: Canvas) {
        val s = game.state
        val left = dp(12f)
        val top = dp(12f)
        val w = dp(162f)
        val h = dp(150f)

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

        // 레벨 + 경험치 바
        val ly = iy2 + dp(82f)
        text.textSize = dp(11.5f)
        text.color = 0xFF4A3728.toInt()
        c.drawText("Lv.${s.level}", left + dp(12f), ly + dp(8f), text)
        text.textSize = dp(9f)
        text.color = 0xFF8A7360.toInt()
        val tt = s.title()
        c.drawText(tt, left + dp(46f), ly + dp(7f), text)
        // 바
        val bx = left + dp(12f)
        val bw = w - dp(24f)
        val by = ly + dp(12f)
        val bh = dp(6f)
        fill.color = Color.argb(255, 214, 197, 164)
        c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, fill)
        if (s.level >= Progression.MAX_LEVEL) {
            fill.color = 0xFFF2D06B.toInt()
            c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, fill)
        } else {
            val prog = s.expProgress()
            if (prog > 0.01f) {
                fill.color = 0xFF6FBA6B.toInt()
                c.drawRoundRect(RectF(bx, by, bx + bw * prog, by + bh), bh / 2, bh / 2, fill)
            }
        }
        stroke.color = 0xFF6B4F35.toInt()
        stroke.strokeWidth = dp(1.2f)
        c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, stroke)
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

        // D패드
        fill.color = Color.argb(88, 40, 36, 54)
        c.drawCircle(dpadCx, dpadCy, dpadR, fill)
        stroke.color = Color.argb(150, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(dpadCx, dpadCy, dpadR, stroke)

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
            fill.color = Color.argb(120, 242, 208, 107)
            val v = dpadVector(game.input.dpadTouchPoint())
            c.drawCircle(dpadCx + v.x * inn, dpadCy + v.y * inn, dp(10f), fill)
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
            fill.color = 0xFF6B4F35.toInt()
            c.drawCircle(eatCx + eatR * 0.62f, eatCy - eatR * 0.62f, dp(8.5f), fill)
            text.textSize = dp(10f)
            text.color = 0xFFF8EFDC.toInt()
            c.drawText("$pizzaN", eatCx + eatR * 0.62f - text.measureText("$pizzaN") / 2, eatCy - eatR * 0.62f - (text.descent() + text.ascent()) / 2, text)
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
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
        stroke.color = Color.argb(190, 248, 239, 220)
        stroke.strokeWidth = dp(2f)
        c.drawCircle(cx, cy, r, stroke)
        if (label != null) {
            text.color = 0xFF3A2A24.toInt()
            text.textSize = labelSize
            val tw = text.measureText(label)
            val ty = cy - (text.descent() + text.ascent()) / 2f
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

        // 바다
        fill.color = 0xFFA8D8E8.toInt()
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
