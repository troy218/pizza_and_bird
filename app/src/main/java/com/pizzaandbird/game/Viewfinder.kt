package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 카메라 뷰파인더 연출 (탐조 화면).
 *
 * 진짜 카메라 앱처럼 보이도록 다음을 그린다.
 *  - 부드러운 비네트 + 모서리 브래킷 + 3분할 그리드 + 필름 그레인
 *  - 상단 바: 촬영 표시등 · 시각 · 장비 이름/사거리
 *  - 하단 바: 촬영 정보(ISO/조리개/셔터) · 거리 게이지 · 별점 예상
 *  - AF 박스: 조준 중인 새에 초점 박스 + 이름/별점 예상 힌트
 *  - 사거리 원과 별점 구역(★3/★2/★1) 안내 링
 *  - 카메라 모드 진입 / 촬영 순간의 셔터 블레이드 애니메이션
 *
 * 좌표는 모두 가상 해상도(960x540) 기준이며 월드 캔버스에 그린다.
 */
class Viewfinder(private val game: Game) {

    private val state get() = game.state

    // ------------------------------------------------------------------
    // 상태
    // ------------------------------------------------------------------

    private var clock = 0f          // 연출용 누적 시간
    private var enterT = 1f         // 진입 연출 (0 -> 1)
    private var flash = 0f          // 셔터 플래시 잔상 (0 -> 1)
    private var phase = OPEN        // 셔터 상태
    private var t = 0f              // 현재 단계 경과 시간
    private var grainT = 0f
    private var grainSeed = 0
    private var focusId: String? = null   // 현재 조준 중인 새
    private var focusT = 0f               // 조준 시작 후 경과 (AF 획득 연출)

    /** 카메라 모드에 들어갈 때: 닫힌 셔터가 열리며 등장 */
    fun onEnter() {
        enterT = 0f
        flash = 0.75f
        phase = OPENING
        t = 0f
    }

    /** 촬영: 셔터가 닫힌다 (결과 카드가 닫힐 때 release()) */
    fun shot() {
        phase = CLOSING
        t = 0f
        flash = 0f
    }

    /** 셔터 다시 열기 */
    fun release() {
        if (phase == OPEN) return
        // 닫히는 도중이면 그 지점부터 자연스럽게 이어서 연다
        val closed = closedAmount()
        phase = OPENING
        t = (1f - closed) * OPEN_T
    }

    val isClosed: Boolean get() = phase == CLOSED

    /** 셔터가 닫히는 중 (촬영 중복 방지). 여는 중에는 다시 찍을 수 있다. */
    val busy: Boolean get() = phase == CLOSING

    fun update(dt: Float) {
        clock += dt
        focusT += dt
        if (enterT < 1f) enterT = (enterT + dt / 0.45f).coerceAtMost(1f)
        if (flash > 0f) flash = (flash - dt * 4.6f).coerceAtLeast(0f)
        when (phase) {
            CLOSING -> {
                t += dt
                if (t >= CLOSE_T) {
                    phase = CLOSED
                    t = 0f
                    flash = 1f
                }
            }
            OPENING -> {
                t += dt
                if (t >= OPEN_T) { phase = OPEN; t = 0f }
            }
        }
    }

    private fun closedAmount(): Float = when (phase) {
        CLOSING -> (t / CLOSE_T).coerceIn(0f, 1f)
        CLOSED -> 1f
        OPENING -> (1f - t / OPEN_T).coerceIn(0f, 1f)
        else -> 0f
    }

    // ------------------------------------------------------------------
    // 페인트
    // ------------------------------------------------------------------

    private val fill = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    // 뷰파인더 라벨은 따뜻한 둥근 글꼴, 촬영 수치(ISO/조리개/셔터)는 카메라다운 모노스페이스
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.round
        isFakeBoldText = true
    }
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val grainPaint = Paint().apply { alpha = 46 }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var grainBmp: Bitmap? = null

    private fun grain(): Bitmap {
        grainBmp?.let { return it }
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint()
        val rnd = java.util.Random(20240607L)
        repeat(3200) {
            val bright = rnd.nextBoolean()
            val a = 16 + rnd.nextInt(30)
            p.color = if (bright) Color.argb(a, 255, 252, 240) else Color.argb(a, 20, 16, 30)
            val x = rnd.nextInt(256).toFloat()
            val y = rnd.nextInt(256).toFloat()
            if (rnd.nextInt(4) == 0) cv.drawRect(x, y, x + 1f, y + 2f, p) else cv.drawRect(x, y, x + 1f, y + 1f, p)
        }
        grainBmp = b
        return b
    }

    // ------------------------------------------------------------------
    // 그리기
    // ------------------------------------------------------------------

    fun draw(
        c: Canvas,
        birds: List<FieldBird>,
        playerCx: Float,
        playerCy: Float,
        camX: Float,
        camY: Float
    ) {
        val w = game.virtW.toFloat()
        val h = game.virtH.toFloat()
        val rangeTiles = CameraDefs.range(state.cameraLevel)
        val rangePx = rangeTiles * 16f * WORLD_SCALE
        val px = (playerCx - camX) * WORLD_SCALE
        val py = (playerCy - camY) * WORLD_SCALE

        // 카메라 색감 (약간 차갑게)
        fill.color = Color.argb(16, 42, 60, 92)
        c.drawRect(0f, 0f, w, h, fill)
        drawGrain(c, w, h)
        drawVignette(c, w, h)

        drawRange(c, px, py, rangePx, rangeTiles)

        val focus = pickFocus(birds, playerCx, playerCy, camX, camY, rangeTiles)
        if (focus?.def?.id != focusId) {
            focusId = focus?.def?.id
            focusT = 0f
        }
        drawBirdMarks(c, birds, focus, playerCx, playerCy, camX, camY, rangeTiles)
        drawFrame(c, w, h)

        drawTopBar(c, w)
        drawBottomBar(c, w, h, focus, playerCx, playerCy, rangeTiles)

        if (focus == null && clock < 10f) {
            // 첫 사용 안내 (살짝 떠 있다 사라진다)
            val fade = (1f - ((clock - 8f) / 2f).coerceIn(0f, 1f))
            val a = (255 * fade).toInt().coerceIn(0, 255)
            val bob = sin(clock * 2.2f) * 3f
            // 안내는 둥근 고딕(비례 폰트)으로 — 모노스페이스는 장비 정보에만 쓴다
            hudLine(c, w / 2f, h * 0.60f + bob, "새를 탭해 촬영하세요", 15f, Color.argb(a, 255, 250, 235), false)
            hudLine(c, w / 2f, h * 0.60f + 22f + bob, "카메라 버튼을 다시 누르면 나갑니다", 11.5f, Color.argb((a * 0.75f).toInt(), 226, 220, 206), false)
        }
    }

    // ----- 배경 연출 ---------------------------------------------------

    private fun drawGrain(c: Canvas, w: Float, h: Float) {
        grainT += 0.016f
        if (grainT > 0.07f) { grainT = 0f; grainSeed++ }
        val bmp = grain()
        val ox = -((grainSeed * 61) % 256).toFloat()
        val oy = -((grainSeed * 113) % 256).toFloat()
        var y = oy
        while (y < h) {
            var x = ox
            while (x < w) {
                c.drawBitmap(bmp, x, y, grainPaint)
                x += 256f
            }
            y += 256f
        }
    }

    /** 네 변에서 안쪽으로 부드럽게 어두워지는 비네트 */
    private fun drawVignette(c: Canvas, w: Float, h: Float) {
        val inset = 96f
        val steps = 8
        val band = inset / steps
        for (i in 0 until steps) {
            val k = (steps - i).toFloat() / steps
            val a = (132f * k * k).toInt().coerceAtLeast(1)
            fill.color = Color.argb(a, 12, 10, 20)
            val d = band * i
            c.drawRect(0f, d, w, d + band + 0.5f, fill)
            c.drawRect(0f, h - d - band - 0.5f, w, h - d, fill)
            c.drawRect(d, 0f, d + band + 0.5f, h, fill)
            c.drawRect(w - d - band - 0.5f, 0f, w - d, h, fill)
        }
        // 상단/하단은 정보 바 가독성을 위해 조금 더
        fill.color = Color.argb(40, 10, 8, 18)
        c.drawRect(0f, 0f, w, 78f, fill)
        fill.color = Color.argb(30, 10, 8, 18)
        c.drawRect(0f, h - 150f, w, h, fill)
    }

    /** 뷰파인더 프레임: 모서리 브래킷 + 3분할 그리드 + 눈금 + 중앙 십자 */
    private fun drawFrame(c: Canvas, w: Float, h: Float) {
        val m = 22f
        val len = 54f

        stroke.color = Color.argb(232, 255, 250, 235)
        stroke.strokeWidth = 3.4f
        stroke.pathEffect = null
        val p = Path()
        p.moveTo(m, m + len); p.lineTo(m, m); p.lineTo(m + len, m)
        p.moveTo(w - m - len, m); p.lineTo(w - m, m); p.lineTo(w - m, m + len)
        p.moveTo(w - m, h - m - len); p.lineTo(w - m, h - m); p.lineTo(w - m - len, h - m)
        p.moveTo(m + len, h - m); p.lineTo(m, h - m); p.lineTo(m, h - m - len)
        c.drawPath(p, stroke)

        // 모서리 안쪽 금색 포인트
        stroke.color = Color.argb(190, 242, 208, 107)
        stroke.strokeWidth = 2.2f
        val q = Path()
        q.moveTo(m + 9f, m + 20f); q.lineTo(m + 9f, m + 9f); q.lineTo(m + 20f, m + 9f)
        q.moveTo(w - m - 20f, m + 9f); q.lineTo(w - m - 9f, m + 9f); q.lineTo(w - m - 9f, m + 20f)
        q.moveTo(w - m - 9f, h - m - 20f); q.lineTo(w - m - 9f, h - m - 9f); q.lineTo(w - m - 20f, h - m - 9f)
        q.moveTo(m + 20f, h - m - 9f); q.lineTo(m + 9f, h - m - 9f); q.lineTo(m + 9f, h - m - 20f)
        c.drawPath(q, stroke)

        // 3분할 그리드
        stroke.color = Color.argb(28, 255, 250, 235)
        stroke.strokeWidth = 1.2f
        val gw = (w - m * 2f) / 3f
        val gh = (h - m * 2f) / 3f
        for (i in 1..2) {
            c.drawLine(m + gw * i, m, m + gw * i, h - m, stroke)
            c.drawLine(m, m + gh * i, w - m, m + gh * i, stroke)
        }

        // 좌우 눈금 (조리개 스케일 느낌)
        stroke.color = Color.argb(46, 255, 250, 235)
        stroke.strokeWidth = 1.6f
        var y = m + 24f
        while (y < h - m - 24f) {
            val long = ((y / 44f).toInt() % 2 == 0)
            val tl = if (long) 11f else 6f
            c.drawLine(m, y, m + tl, y, stroke)
            c.drawLine(w - m, y, w - m - tl, y, stroke)
            y += 22f
        }

        // 중앙 십자
        stroke.color = Color.argb(70, 255, 250, 235)
        stroke.strokeWidth = 1.6f
        c.drawLine(w / 2f - 12f, h / 2f, w / 2f - 4f, h / 2f, stroke)
        c.drawLine(w / 2f + 4f, h / 2f, w / 2f + 12f, h / 2f, stroke)
        c.drawLine(w / 2f, h / 2f - 12f, w / 2f, h / 2f - 4f, stroke)
        c.drawLine(w / 2f, h / 2f + 4f, w / 2f, h / 2f + 12f, stroke)
    }

    /** 사거리 원 + 별점 구역 링 */
    private fun drawRange(c: Canvas, px: Float, py: Float, rangePx: Float, rangeTiles: Float) {
        // 사거리 안쪽을 아주 살짝 밝게
        fill.color = Color.argb(9, 255, 250, 235)
        c.drawCircle(px, py, rangePx, fill)
        fill.color = Color.argb(9, 255, 250, 235)
        c.drawCircle(px, py, rangePx * 0.67f, fill)

        ring.strokeWidth = 1.7f
        ring.pathEffect = DashPathEffect(floatArrayOf(11f, 9f), -clock * 14f)
        ring.color = Color.argb(120, 255, 250, 235)
        c.drawCircle(px, py, rangePx, ring)

        ring.pathEffect = null
        ring.strokeWidth = 1.3f
        ring.color = Color.argb(74, 111, 186, 107)
        c.drawCircle(px, py, rangePx * 0.34f, ring)
        ring.color = Color.argb(74, 242, 182, 60)
        c.drawCircle(px, py, rangePx * 0.67f, ring)

        // 라벨: 사거리 (원 아래쪽, 화면 안으로 보정)
        val labelY = (py + rangePx + 18f).coerceIn(120f, game.virtH - 104f)
        text.textSize = 12.5f
        val lbl = "사거리 ${fmt(rangeTiles)}칸"
        val lw = text.measureText(lbl)
        fill.color = Color.argb(150, 16, 14, 24)
        c.drawRoundRect(RectF(px - lw / 2f - 7f, labelY - 12f, px + lw / 2f + 7f, labelY + 5f), 5f, 5f, fill)
        text.color = Color.argb(210, 246, 240, 224)
        c.drawText(lbl, px - lw / 2f, labelY, text)

        // 별점 구역 라벨 (왼쪽 수평선 위)
        text.textSize = 11.5f
        zoneLabel(c, "★3", px - rangePx * 0.34f - 6f, py, 0xFF9BD98F.toInt())
        zoneLabel(c, "★2", px - rangePx * 0.67f - 6f, py, 0xFFF2C86B.toInt())
    }

    private fun zoneLabel(c: Canvas, s: String, x: Float, y: Float, color: Int) {
        val tw = text.measureText(s)
        if (x - tw < 8f) return
        fill.color = Color.argb(140, 16, 14, 24)
        c.drawRoundRect(RectF(x - tw - 5f, y - 10f, x + 5f, y + 6f), 4f, 4f, fill)
        text.color = color
        c.drawText(s, x - tw, y + 3f, text)
    }

    // ----- 새 표시 -----------------------------------------------------

    /** 조준 중인 새: 사거리 안에서 가장 가까운 새 (없으면 1.45배까지 후보로 삼아 안내) */
    private fun pickFocus(
        birds: List<FieldBird>, pcx: Float, pcy: Float, camX: Float, camY: Float, rangeTiles: Float
    ): FieldBird? {
        var best: FieldBird? = null
        var bestD = Float.MAX_VALUE
        val w = game.virtW.toFloat()
        val h = game.virtH.toFloat()
        for (b in birds) {
            if (b.state == 2) continue
            val sx = (b.cx - camX) * WORLD_SCALE
            val sy = (b.cy - camY) * WORLD_SCALE
            if (sx < 34f || sx > w - 34f || sy < 84f || sy > h - 76f) continue
            val d = hypot(b.cx - pcx, b.cy - pcy) / 16f
            if (d > rangeTiles * 1.45f) continue
            if (d < bestD) { bestD = d; best = b }
        }
        return best
    }

    private fun drawBirdMarks(
        c: Canvas,
        birds: List<FieldBird>,
        focus: FieldBird?,
        pcx: Float,
        pcy: Float,
        camX: Float,
        camY: Float,
        rangeTiles: Float
    ) {
        for (b in birds) {
            if (b.state == 2) continue
            val sx = (b.cx - camX) * WORLD_SCALE
            val sy = (b.cy - camY) * WORLD_SCALE
            if (sx < -40f || sx > game.virtW + 40f || sy < -40f || sy > game.virtH + 40f) continue
            val dTiles = hypot(b.cx - pcx, b.cy - pcy) / 16f
            val inRange = dTiles <= rangeTiles
            if (b === focus) {
                drawFocusBox(c, b, sx, sy, dTiles, rangeTiles, inRange)
            } else if (inRange) {
                // 사거리 안의 다른 새: 별점 예상만 조그맣게
                val ratio = dTiles / rangeTiles
                val (stars, col) = zone(ratio)
                val txt = "★".repeat(stars) + "☆".repeat(3 - stars)
                text.textSize = 11.5f
                val tw = text.measureText(txt)
                val by = sy - b.sprH * WORLD_SCALE * 0.5f - 24f
                fill.color = Color.argb(140, 16, 14, 24)
                c.drawRoundRect(RectF(sx - tw / 2f - 6f, by - 11f, sx + tw / 2f + 6f, by + 6f), 5f, 5f, fill)
                stroke.color = Color.argb(150, Color.red(col), Color.green(col), Color.blue(col))
                stroke.strokeWidth = 1.2f
                c.drawRoundRect(RectF(sx - tw / 2f - 6f, by - 11f, sx + tw / 2f + 6f, by + 6f), 5f, 5f, stroke)
                text.color = Color.argb(225, 246, 240, 224)
                c.drawText(txt, sx - tw / 2f, by + 2f, text)
            }
        }
    }

    /** AF 박스 + 이름/별점 예상 라벨 */
    private fun drawFocusBox(
        c: Canvas,
        b: FieldBird,
        sx: Float,
        sy: Float,
        dTiles: Float,
        rangeTiles: Float,
        inRange: Boolean
    ) {
        val ratio = (dTiles / rangeTiles).coerceAtLeast(0.001f)
        val (stars, col) = zone(ratio)
        val pulse = if (inRange) 1f + sin(clock * 5.5f) * 0.02f else 1f + sin(clock * 3f) * 0.05f
        val halfW = (b.sprW * WORLD_SCALE * 0.5f + 13f) * pulse
        val halfH = (b.sprH * WORLD_SCALE * 0.5f + 13f) * pulse
        val box = RectF(sx - halfW, sy - halfH, sx + halfW, sy + halfH)

        // 어두운 보조 사각형 (초점 영역 강조)
        fill.color = Color.argb(26, 10, 8, 18)
        c.drawRect(box, fill)

        // AF 획득 순간: 바깥에서 안으로 좁혀오는 링
        if (focusT < 0.42f) {
            val k = (focusT / 0.42f).coerceIn(0f, 1f)
            val grow = (1f - k) * 26f
            val a = ((1f - k) * 210f).toInt().coerceIn(0, 255)
            stroke.color = Color.argb(a, Color.red(col), Color.green(col), Color.blue(col))
            stroke.strokeWidth = 2.2f
            stroke.pathEffect = null
            c.drawRoundRect(
                RectF(box.left - grow, box.top - grow, box.right + grow, box.bottom + grow),
                5f, 5f, stroke
            )
        }

        val tick = minOf(12f, box.width() * 0.34f)
        stroke.color = if (inRange) Color.argb(240, Color.red(col), Color.green(col), Color.blue(col))
        else Color.argb(200, 246, 240, 224)
        stroke.strokeWidth = 2.4f
        stroke.pathEffect = if (inRange) null else DashPathEffect(floatArrayOf(7f, 6f), -clock * 26f)
        val p = Path()
        p.moveTo(box.left, box.top + tick); p.lineTo(box.left, box.top); p.lineTo(box.left + tick, box.top)
        p.moveTo(box.right - tick, box.top); p.lineTo(box.right, box.top); p.lineTo(box.right, box.top + tick)
        p.moveTo(box.right, box.bottom - tick); p.lineTo(box.right, box.bottom); p.lineTo(box.right - tick, box.bottom)
        p.moveTo(box.left + tick, box.bottom); p.lineTo(box.left, box.bottom); p.lineTo(box.left, box.bottom - tick)
        c.drawPath(p, stroke)
        stroke.pathEffect = null

        // 라벨: 이름(찍은 적 있으면 공개) + 별점 예상 + 거리
        val seen = (state.birdCounts[b.def.id] ?: 0) > 0
        val name = if (seen) b.def.name else "??? 미확인"
        val starTxt = "★".repeat(stars) + "☆".repeat(3 - stars)
        val info = if (inRange) "$starTxt  ·  ${fmt(dTiles)}칸" else "더 가까이!  ·  ${fmt(dTiles)}칸"

        text.textSize = 13.5f
        val nameW = text.measureText(name)
        text.textSize = 11.5f
        val infoW = text.measureText(info)
        val plateW = maxOf(nameW, infoW) + 22f
        val plateH = 34f
        val plateCx = sx.coerceIn(46f + plateW / 2f, game.virtW - 46f - plateW / 2f)
        var plateTop = box.top - plateH - 8f
        if (plateTop < 84f) plateTop = box.bottom + 8f
        val plate = RectF(plateCx - plateW / 2f, plateTop, plateCx + plateW / 2f, plateTop + plateH)

        fill.color = Color.argb(196, 14, 12, 22)
        c.drawRoundRect(plate, 7f, 7f, fill)
        stroke.color = Color.argb(215, Color.red(col), Color.green(col), Color.blue(col))
        stroke.strokeWidth = 1.5f
        c.drawRoundRect(plate, 7f, 7f, stroke)

        // 초점 확인 표시
        if (inRange) {
            fill.color = Color.argb(235, Color.red(col), Color.green(col), Color.blue(col))
            c.drawCircle(plate.left + 11f, plate.top + plateH / 2f, 3.4f, fill)
        } else {
            stroke.color = Color.argb(200, 246, 240, 224)
            stroke.strokeWidth = 1.4f
            c.drawCircle(plate.left + 11f, plate.top + plateH / 2f, 4.6f, stroke)
        }

        text.textSize = 13.5f
        text.color = if (seen) Color.argb(240, 250, 246, 236) else Color.argb(210, 200, 195, 210)
        c.drawText(name, plate.left + 20f, plate.top + 14.5f, text)
        text.textSize = 11.5f
        text.color = if (inRange) Color.argb(232, Color.red(col), Color.green(col), Color.blue(col))
        else Color.argb(205, 246, 240, 224)
        c.drawText(info, plate.left + 20f, plate.top + 28f, text)
    }

    // ----- 상단 정보 바 -------------------------------------------------

    private fun drawTopBar(c: Canvas, w: Float) {
        val k = enterT
        val y = 26f - (1f - k) * 16f
        val alpha = (255 * k).toInt()

        // 좌측: 촬영 표시등 + PHOTO
        val blink = if (sin(clock * 4.2f) > -0.2f) 1f else 0.25f
        text.textSize = 13f
        val badgeW = text.measureText("PHOTO") + 46f
        val badge = RectF(40f, y, 40f + badgeW, y + 28f)
        fill.color = Color.argb((168 * k).toInt(), 14, 12, 22)
        c.drawRoundRect(badge, 7f, 7f, fill)
        stroke.color = Color.argb((90 * k).toInt(), 246, 240, 224)
        stroke.strokeWidth = 1.2f
        c.drawRoundRect(badge, 7f, 7f, stroke)
        fill.color = Color.argb((240 * k * blink).toInt(), 226, 87, 76)
        c.drawCircle(badge.left + 14f, badge.centerY(), 4.6f, fill)
        fill.color = Color.argb((70 * k * blink).toInt(), 226, 87, 76)
        c.drawCircle(badge.left + 14f, badge.centerY(), 9f, fill)
        text.color = Color.argb((240 * k).toInt(), 250, 246, 236)
        c.drawText("PHOTO", badge.left + 26f, badge.centerY() + 4.5f, text)

        // 중앙: 시각
        val night = state.isNight()
        val clockTxt = "${if (night) "🌙" else "☀"} ${state.timeLabel()}"
        mono.textSize = 16f
        val cw = mono.measureText(clockTxt) + 26f
        val cr = RectF(w / 2f - cw / 2f, y, w / 2f + cw / 2f, y + 28f)
        fill.color = Color.argb((168 * k).toInt(), 14, 12, 22)
        c.drawRoundRect(cr, 7f, 7f, fill)
        mono.color = Color.argb((240 * k).toInt(), 250, 246, 236)
        c.drawText(clockTxt, cr.left + 13f, cr.centerY() + 5.5f, mono)

        // 우측: 장비 + 사거리
        val cam = CameraDefs.LEVELS[(state.cameraLevel - 1).coerceIn(0, CameraDefs.LEVELS.size - 1)]
        text.textSize = 13f
        val nameTxt = "${cam.name}  Lv.${state.cameraLevel}"
        val subTxt = "사거리 ${fmt(cam.rangeTiles)}칸 · 찍은 사진 ${state.photos}장"
        val bw = maxOf(text.measureText(nameTxt), text.measureText(subTxt)) + 24f
        val br = RectF(w - 40f - bw, y, w - 40f, y + 44f)
        fill.color = Color.argb((168 * k).toInt(), 14, 12, 22)
        c.drawRoundRect(br, 7f, 7f, fill)
        stroke.color = Color.argb((90 * k).toInt(), 246, 240, 224)
        stroke.strokeWidth = 1.2f
        c.drawRoundRect(br, 7f, 7f, stroke)
        text.color = Color.argb((240 * k).toInt(), 250, 246, 236)
        c.drawText(nameTxt, br.left + 12f, br.top + 19f, text)
        text.textSize = 11.5f
        text.color = Color.argb((200 * k).toInt(), 214, 208, 224)
        c.drawText(subTxt, br.left + 12f, br.top + 34f, text)
    }

    // ----- 하단 정보 바 -------------------------------------------------

    private fun drawBottomBar(
        c: Canvas,
        w: Float,
        h: Float,
        focus: FieldBird?,
        pcx: Float,
        pcy: Float,
        rangeTiles: Float
    ) {
        val k = enterT
        val y = h - 92f + (1f - k) * 18f
        val boxW = 432f
        val boxH = 58f
        val r = RectF(w / 2f - boxW / 2f, y, w / 2f + boxW / 2f, y + boxH)

        fill.color = Color.argb((168 * k).toInt(), 14, 12, 22)
        c.drawRoundRect(r, 10f, 10f, fill)
        stroke.color = Color.argb((90 * k).toInt(), 246, 240, 224)
        stroke.strokeWidth = 1.2f
        c.drawRoundRect(r, 10f, 10f, stroke)

        // 왼쪽: 촬영 정보
        val lvl = state.cameraLevel
        val iso = 100 * (1 shl ((lvl - 1).coerceIn(0, 4) / 2))
        val fnum = when (lvl) { 1 -> "f/2.2"; 2 -> "f/2.2"; 3 -> "f/2.8"; 4 -> "f/2.8"; else -> "f/4.0" }
        val shutter = when (lvl) { 1 -> "1/60"; 2 -> "1/90"; 3 -> "1/125"; 4 -> "1/200"; else -> "1/320" }
        mono.textSize = 12.5f
        mono.color = Color.argb((225 * k).toInt(), 232, 226, 240)
        c.drawText("ISO $iso  $fnum  $shutter", r.left + 14f, r.top + 22f, mono)
        mono.textSize = 11f
        mono.color = Color.argb((190 * k).toInt(), 206, 200, 216)
        val count = state.birdCounts.size
        c.drawText("도감 ${count}종 · 관측 ${state.photos}컷", r.left + 14f, r.top + 40f, mono)

        // 오른쪽: 거리 게이지
        val gx = r.right - 190f
        val gy = r.top + 24f
        val gw = 160f
        val gh = 9f
        fill.color = Color.argb((200 * k).toInt(), 32, 28, 44)
        c.drawRoundRect(RectF(gx, gy, gx + gw, gy + gh), 4.5f, 4.5f, fill)
        // 구역: ★3 (초록) / ★2 (노랑) / ★1 (빨강)
        fill.color = Color.argb((170 * k).toInt(), 111, 186, 107)
        c.drawRoundRect(RectF(gx + 1f, gy + 1f, gx + gw * 0.34f, gy + gh - 1f), 4f, 4f, fill)
        fill.color = Color.argb((150 * k).toInt(), 242, 182, 60)
        c.drawRoundRect(RectF(gx + gw * 0.34f, gy + 1f, gx + gw * 0.67f, gy + gh - 1f), 4f, 4f, fill)
        fill.color = Color.argb((130 * k).toInt(), 226, 87, 76)
        c.drawRoundRect(RectF(gx + gw * 0.67f, gy + 1f, gx + gw - 1f, gy + gh - 1f), 4f, 4f, fill)

        text.textSize = 11f
        if (focus != null) {
            val dTiles = hypot(focus.cx - pcx, focus.cy - pcy) / 16f
            val ratio = (dTiles / rangeTiles).coerceIn(0f, 1f)
            val mx = gx + gw * ratio
            stroke.color = Color.argb((250 * k).toInt(), 255, 252, 244)
            stroke.strokeWidth = 2f
            c.drawLine(mx, gy - 5f, mx, gy + gh + 5f, stroke)
            val (stars, col) = zone((dTiles / rangeTiles).coerceAtLeast(0.001f))
            text.color = Color.argb((235 * k).toInt(), Color.red(col), Color.green(col), Color.blue(col))
            val s = "예상 " + "★".repeat(stars) + "☆".repeat(3 - stars)
            c.drawText(s, gx + gw - text.measureText(s), r.top + 48f, text)
            text.color = Color.argb((200 * k).toInt(), 214, 208, 224)
            c.drawText("거리 ${fmt(dTiles)}칸", gx, r.top + 48f, text)
        } else {
            text.color = Color.argb((200 * k).toInt(), 214, 208, 224)
            c.drawText("새를 찾는 중…", gx, r.top + 48f, text)
            val sx = gx + (0.5f + 0.5f * sin(clock * 3f)) * gw
            fill.color = Color.argb((220 * k).toInt(), 246, 240, 224)
            c.drawCircle(sx, gy + gh / 2f, 3.2f, fill)
        }

    }

    private fun hudLine(c: Canvas, cx: Float, y: Float, s: String, size: Float, color: Int, mono: Boolean) {
        val p = if (mono) this.mono else this.text
        p.textSize = size
        p.color = color
        c.drawText(s, cx - p.measureText(s) / 2f, y, p)
    }

    // ------------------------------------------------------------------
    // 셔터 애니메이션 (월드 위에 마지막으로 그린다)
    // ------------------------------------------------------------------

    fun drawShutter(c: Canvas) {
        val e = closedAmount()
        if (e <= 0.002f && flash <= 0.01f) return
        val w = game.virtW.toFloat()
        val h = game.virtH.toFloat()

        if (e > 0.002f) {
            val mid = h / 2f
            val edge = mid * e
            val curve = 18f * e
            for (top in booleanArrayOf(true, false)) {
                val p = Path()
                if (top) {
                    p.moveTo(-4f, -4f); p.lineTo(w + 4f, -4f)
                    p.lineTo(w + 4f, edge); p.quadTo(w / 2f, edge + curve, -4f, edge)
                    p.close()
                } else {
                    p.moveTo(-4f, h + 4f); p.lineTo(w + 4f, h + 4f)
                    p.lineTo(w + 4f, h - edge); p.quadTo(w / 2f, h - edge - curve, -4f, h - edge)
                    p.close()
                }
                bladePaint.color = 0xFF120F1A.toInt()
                bladePaint.isAntiAlias = true
                c.drawPath(p, bladePaint)

                // 날 끝의 따뜻한 빛
                val g = Path()
                if (top) {
                    g.moveTo(-4f, edge); g.quadTo(w / 2f, edge + curve, w + 4f, edge)
                } else {
                    g.moveTo(-4f, h - edge); g.quadTo(w / 2f, h - edge - curve, w + 4f, h - edge)
                }
                glow.color = Color.argb((90 * e).toInt(), 255, 236, 190)
                glow.strokeWidth = 13f
                glow.style = Paint.Style.STROKE
                glow.isAntiAlias = true
                c.drawPath(g, glow)
                glow.color = Color.argb((200 * e).toInt(), 255, 244, 214)
                glow.strokeWidth = 2.4f
                c.drawPath(g, glow)
            }
        }

        // "찰칵!" 순간 연출
        if (phase == CLOSING) {
            val p = (t / CLOSE_T).coerceIn(0f, 1f)
            if (p > 0.45f) {
                val a = (255 * ((p - 0.45f) / 0.55f)).toInt().coerceIn(0, 255)
                text.textSize = 27f
                val s = "찰칵!"
                val tw = text.measureText(s)
                text.color = Color.argb((a * 0.5f).toInt(), 10, 8, 16)
                c.drawText(s, w / 2f - tw / 2f + 2f, h / 2f + 12f, text)
                text.color = Color.argb(a, 255, 248, 232)
                c.drawText(s, w / 2f - tw / 2f, h / 2f + 10f, text)
            }
        }

        // 플래시
        if (flash > 0f) {
            fill.color = Color.argb((165 * flash).toInt().coerceIn(0, 255), 255, 252, 244)
            c.drawRect(0f, 0f, w, h, fill)
        }
    }

    // ------------------------------------------------------------------

    /** 거리 비율 -> (별점, 색) */
    private fun zone(ratio: Float): Pair<Int, Int> = when {
        ratio < 0.34f -> 3 to 0xFF7FD07A.toInt()
        ratio < 0.67f -> 2 to 0xFFF2C86B.toInt()
        else -> 1 to 0xFFE2574C.toInt()
    }

    private fun fmt(v: Float): String = String.format("%.1f", v)

    private companion object {
        const val CLOSING = 0
        const val CLOSED = 1
        const val OPENING = 2
        const val OPEN = 3
        const val CLOSE_T = 0.10f
        const val OPEN_T = 0.28f
    }
}
