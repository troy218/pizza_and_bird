package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 화면 좌표(실제 해상도) 기반 HUD.
 * - 좌상단: 배고픔/행운/돈/피자/카메라/시각 패널
 * - 우상단: 황동 회중 나침반 미니맵 (낡은 종이 해도, 탭하면 큰 지도)
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

        mmR = dp(68f)
        mmCx = w - dp(8f) - mmR
        mmCy = dp(8f) + mmR
        softShadow.maskFilter = BlurMaskFilter(dp(3.4f), BlurMaskFilter.Blur.NORMAL)
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
        if (inCircle(x, y, dpadCx, dpadCy, dpadR * 1.12f)) return Ctrl.DPAD
        if (inCircle(x, y, aCx, aCy, aR * 1.22f)) return Ctrl.A
        if (inCircle(x, y, bCx, bCy, bR * 1.25f)) return Ctrl.B
        if (inCircle(x, y, camBCx, camBCy, camBR * 1.25f)) return Ctrl.CAM
        if (inCircle(x, y, runCx, runCy, runR * 1.3f)) return Ctrl.RUN
        if (inCircle(x, y, eatCx, eatCy, eatR * 1.3f)) return Ctrl.EAT
        if (inCircle(x, y, menuCx, menuCy, menuR * 1.35f)) return Ctrl.MENU
        if (hitMinimap(x, y)) return Ctrl.MAP
        return Ctrl.NONE
    }

    private fun hitMinimap(x: Float, y: Float): Boolean {
        if (!showMinimap || mmR <= 0f) return false
        if (inCircle(x, y, mmCx, mmCy, mmR * 1.04f)) return true
        val tag = fieldTagRect() ?: return false
        tag.inset(-dp(5f), -dp(4f))
        return tag.contains(x, y)
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
            if (regionLabel.isNotEmpty()) drawFieldTag(c)
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
                inkText.typeface = Typeface.DEFAULT_BOLD
                inkText.textSize = dp(8f)
                inkText.color = if (isCurrent) 0xFFB4332A.toInt() else 0xFF3A2A1C.toInt()
                val tw = inkText.measureText(reg.name)
                val ty = y + dot + dp(9f)
                fx.style = Paint.Style.FILL
                fx.shader = null
                fx.color = Color.argb(210, 244, 232, 204)
                c.drawRoundRect(
                    RectF(x - tw / 2f - dp(2f), ty - dp(8f), x + tw / 2f + dp(2f), ty + dp(2.5f)),
                    dp(2f), dp(2f), fx
                )
                c.drawText(reg.name, x - tw / 2f, ty, inkText)
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
        val aR = dp(27f)
        val aCy = game.screenH - dp(26f) - aR
        val camBR = dp(21f)
        return aCy - aR - dp(12f) - camBR * 2f
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
