package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 카메라 뷰파인더 (탐조 화면) — 이 게임에서 제일 예쁜 화면.
 *
 * 진짜 카메라 앱을 들여다보는 기분이 나도록 다음을 겹겹이 그린다.
 *
 *  1. **렌즈 톤** — 차가운 필름 색조, 좌상단 빛 번짐(시각·계절에 따라 색이 바뀐다),
 *     초점 밖 보케, 필름 그레인, 네 변에서 스며드는 비네트.
 *  2. **프레임** — 유리 가장자리 그림자, 크림색 코너 브래킷 + 금색 포인트,
 *     3분할 그리드와 교차점 마커, 조리개 눈금, 중앙 십자.
 *  3. **사거리 안내** — 점선 사거리 원 + 24눈금, ★3 / ★2 구역 링과 라벨,
 *     최단 촬영 거리(빨강) 링, 아래쪽 사거리 명판.
 *  4. **상단 바** — REC 점멸등 · PHOTO · AF-C · 시각(하루 진행 게이지) · 장비 카드(렌즈 아이콘).
 *  5. **하단 바** — EXIF(조리개·셔터·ISO), 장비 정보, 거리 게이지(★구역·마커), 촬영 수, 계절·날씨.
 *  6. **AF 박스** — 조준 중인 새에 초점 브래킷 + AF 획득 연출 + 이름/등급/별점/거리 명판
 *     + 초점 영역 거리 바 + 3★ 순간의 반짝임.
 *  7. **셔터** — 곡선 블레이드가 닫히고, 빛이 번지고, "찰칵!" 과 플래시가 터진다.
 *
 * 좌표는 모두 가상 해상도(960x540) 기준이며 월드 캔버스에 그린다(HUD 는 나중에 그려진다).
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
    private var sparkT = 0f               // 3★ 조준 반짝임 위상

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
        sparkT += dt
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

    /** 등장 연출 곡선 (smoothstep) */
    private fun easeIn(): Float {
        val e = enterT
        return e * e * (3f - 2f * e)
    }

    // ------------------------------------------------------------------
    // 페인트
    // ------------------------------------------------------------------

    private val fill = Paint()
    private val grad = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true)
    private val textSoft = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), false)
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val grainPaint = Paint().apply { alpha = 46 }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leakPaint = Paint()
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG)

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

    /**
     * @param zoom 월드에 적용된 망원 배율 (CameraRig.zoom) — 표식이 실제 화면 위치에 맞도록 함께 반영한다.
     */
    fun draw(
        c: Canvas,
        birds: List<FieldBird>,
        playerCx: Float,
        playerCy: Float,
        camX: Float,
        camY: Float,
        zoom: Float = 1f
    ) {
        val w = game.virtW.toFloat()
        val h = game.virtH.toFloat()
        val rig = state.rig()
        val k = easeIn()
        val rangeTiles = rig.reach
        val rangePx = rangeTiles * 16f * WORLD_SCALE * zoom
        val px = sx(playerCx, camX, zoom)
        val py = sy(playerCy, camY, zoom)
        val bokeh = ((8f - rig.apTele) / 6f).coerceIn(0f, 1f)

        val focus = pickFocus(birds, playerCx, playerCy, camX, camY, rangeTiles, zoom)
        if (focus?.def?.id != focusId) {
            focusId = focus?.def?.id
            focusT = 0f
        }

        drawTone(c, w, h, bokeh)
        drawRange(c, px, py, rangePx, rangeTiles, rig.minDist * 16f * WORLD_SCALE, rig.minDist, k, rig)
        drawBirdMarks(c, birds, focus, playerCx, playerCy, camX, camY, rangeTiles, zoom, k)
        drawFrame(c, w, h, k)

        drawTopBar(c, w, k)
        drawBottomBar(c, w, h, focus, playerCx, playerCy, rangeTiles, k)

        if (focus == null && clock < 10f) {
            // 첫 사용 안내 (살짝 떠 있다 사라진다)
            val fade = (1f - ((clock - 8f) / 2f).coerceIn(0f, 1f))
            drawHint(c, w, h, fade)
        }
    }

    /** 월드 논리 좌표 -> 화면 좌표 (월드가 화면 중앙 기준으로 zoom 배 확대돼 있다) */
    private fun sx(wx: Float, camX: Float, zoom: Float): Float {
        val hx = game.virtW / 2f
        return hx + ((wx - camX) * WORLD_SCALE - hx) * zoom
    }

    private fun sy(wy: Float, camY: Float, zoom: Float): Float {
        val hy = game.virtH / 2f
        return hy + ((wy - camY) * WORLD_SCALE - hy) * zoom
    }

    // ------------------------------------------------------------------
    // 1. 렌즈 톤 (색조 · 빛 번짐 · 보케 · 그레인 · 비네트)
    // ------------------------------------------------------------------

    private fun drawTone(c: Canvas, w: Float, h: Float, bokeh: Float) {
        // 차가운 필름 색조
        fill.color = Color.argb(13, 42, 60, 92)
        c.drawRect(0f, 0f, w, h, fill)

        // 좌상단 빛 번짐 — 밤에는 달빛, 낮에는 햇살 색으로
        val dark = state.darkness()
        val leak = if (dark > 0.6f) intArrayOf(150, 186, 255) else intArrayOf(255, 214, 150)
        val leakA = (20f * (1f - dark * 0.55f)).toInt().coerceIn(4, 26)
        leakPaint.shader = LinearGradient(
            -w * 0.08f, -h * 0.16f, w * 0.72f, h * 0.86f,
            intArrayOf(Color.argb(leakA, leak[0], leak[1], leak[2]), Color.argb(0, leak[0], leak[1], leak[2])),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, leakPaint)
        leakPaint.shader = null

        drawBokeh(c, w, h, bokeh)
        drawGrain(c, w, h)
        drawVignette(c, w, h)
    }

    /** 초점 밖 하이라이트가 동그랗게 번지는 느낌 — 아주 옅게 떠다닌다 */
    private fun drawBokeh(c: Canvas, w: Float, h: Float, bokeh: Float) {
        if (bokeh < 0.15f) return
        val warm = if (state.darkness() > 0.6f) Glow.soft else Glow.warm
        for (i in 0 until 6) {
            val s = i * 1.71f
            val ph = clock * (0.10f + 0.03f * (i % 3))
            val x = ((sin(ph + s) * 0.5f + 0.5f) * (w + 220f)) - 110f
            val y = ((sin(ph * 0.83f + s * 1.9f) * 0.5f + 0.5f) * (h + 200f)) - 100f
            val r = 30f + 62f * frac(sin(s * 12.9898f) * 43758.5453f)
            val a = (7f + 15f * bokeh * (0.6f + 0.4f * sin(clock * 0.7f + s))).toInt().coerceIn(3, 26)
            Glow.draw(c, warm, x, y, r, r, a)
        }
    }

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

    /** 네 변에서 안쪽으로 부드럽게 어두워지는 비네트 + 위아래 정보 바 그늘 */
    private fun drawVignette(c: Canvas, w: Float, h: Float) {
        val inset = 104f
        val steps = 12
        val band = inset / steps
        for (i in 0 until steps) {
            val k = (steps - i).toFloat() / steps
            val a = (100f * k * k).toInt().coerceAtLeast(1)
            fill.color = Color.argb(a, 12, 10, 20)
            val d = band * i
            c.drawRect(0f, d, w, d + band + 0.5f, fill)
            c.drawRect(0f, h - d - band - 0.5f, w, h - d, fill)
            c.drawRect(d, 0f, d + band + 0.5f, h, fill)
            c.drawRect(w - d - band - 0.5f, 0f, w - d, h, fill)
        }
        // 상단/하단은 정보 바 가독성을 위해 조금 더
        fill.color = Color.argb(40, 10, 8, 18)
        c.drawRect(0f, 0f, w, 84f, fill)
        fill.color = Color.argb(30, 10, 8, 18)
        c.drawRect(0f, h - 160f, w, h, fill)
    }

    // ------------------------------------------------------------------
    // 2. 프레임 (브래킷 · 그리드 · 눈금 · 십자)
    // ------------------------------------------------------------------

    private fun drawFrame(c: Canvas, w: Float, h: Float, k: Float) {
        val m = 24f
        val len = 58f
        val alpha = k

        // 유리 가장자리 — 안쪽으로 살짝 어두운 이중 테
        stroke.pathEffect = null
        stroke.color = Color.argb((44 * alpha).toInt(), 8, 6, 14)
        stroke.strokeWidth = 9f
        c.drawRoundRect(RectF(11f, 11f, w - 11f, h - 11f), 22f, 22f, stroke)
        stroke.color = Color.argb((72 * alpha).toInt(), 8, 6, 14)
        stroke.strokeWidth = 2.4f
        c.drawRoundRect(RectF(19f, 19f, w - 19f, h - 19f), 16f, 16f, stroke)

        // 3분할 그리드 + 교차점 마커
        stroke.color = Color.argb((24 * alpha).toInt(), 255, 250, 235)
        stroke.strokeWidth = 1.2f
        val gw = (w - m * 2f) / 3f
        val gh = (h - m * 2f) / 3f
        for (i in 1..2) {
            c.drawLine(m + gw * i, m, m + gw * i, h - m, stroke)
            c.drawLine(m, m + gh * i, w - m, m + gh * i, stroke)
        }
        fill.color = Color.argb((150 * alpha).toInt(), 255, 250, 235)
        for (i in 1..2) for (j in 1..2) c.drawCircle(m + gw * i, m + gh * j, 2.1f, fill)

        // 좌우 조리개 눈금
        stroke.color = Color.argb((46 * alpha).toInt(), 255, 250, 235)
        stroke.strokeWidth = 1.6f
        var y = m + 26f
        while (y < h - m - 26f) {
            val long = ((y / 44f).toInt() % 2 == 0)
            val tl = if (long) 12f else 6f
            c.drawLine(m - 4f, y, m - 4f + tl, y, stroke)
            c.drawLine(w - m + 4f, y, w - m + 4f - tl, y, stroke)
            y += 22f
        }

        // 코너 브래킷 (크림) + 안쪽 금색 포인트
        stroke.color = Color.argb((236 * alpha).toInt(), 255, 250, 235)
        stroke.strokeWidth = 3.6f
        val p = Path()
        p.moveTo(m, m + len); p.lineTo(m, m); p.lineTo(m + len, m)
        p.moveTo(w - m - len, m); p.lineTo(w - m, m); p.lineTo(w - m, m + len)
        p.moveTo(w - m, h - m - len); p.lineTo(w - m, h - m); p.lineTo(w - m - len, h - m)
        p.moveTo(m + len, h - m); p.lineTo(m, h - m); p.lineTo(m, h - m - len)
        c.drawPath(p, stroke)

        stroke.color = Color.argb((190 * alpha).toInt(), 242, 208, 107)
        stroke.strokeWidth = 2.2f
        val q = Path()
        val g0 = 10f
        val g1 = 23f
        q.moveTo(m + g0, m + g1); q.lineTo(m + g0, m + g0); q.lineTo(m + g1, m + g0)
        q.moveTo(w - m - g1, m + g0); q.lineTo(w - m - g0, m + g0); q.lineTo(w - m - g0, m + g1)
        q.moveTo(w - m - g0, h - m - g1); q.lineTo(w - m - g0, h - m - g0); q.lineTo(w - m - g1, h - m - g0)
        q.moveTo(m + g1, h - m - g0); q.lineTo(m + g0, h - m - g0); q.lineTo(m + g0, h - m - g1)
        c.drawPath(q, stroke)

        // 중앙 십자 + 중심점
        stroke.color = Color.argb((92 * alpha).toInt(), 255, 250, 235)
        stroke.strokeWidth = 1.6f
        c.drawLine(w / 2f - 14f, h / 2f, w / 2f - 5f, h / 2f, stroke)
        c.drawLine(w / 2f + 5f, h / 2f, w / 2f + 14f, h / 2f, stroke)
        c.drawLine(w / 2f, h / 2f - 14f, w / 2f, h / 2f - 5f, stroke)
        c.drawLine(w / 2f, h / 2f + 5f, w / 2f, h / 2f + 14f, stroke)
        fill.color = Color.argb((120 * alpha).toInt(), 255, 250, 235)
        c.drawCircle(w / 2f, h / 2f, 1.7f, fill)
    }

    // ------------------------------------------------------------------
    // 3. 사거리 · 별점 구역
    // ------------------------------------------------------------------

    private fun drawRange(
        c: Canvas,
        px: Float,
        py: Float,
        rangePx: Float,
        rangeTiles: Float,
        minPx: Float,
        minTiles: Float,
        k: Float,
        rig: CameraRig
    ) {
        // 등장할 때 바깥에서 살짝 좁혀 들어온다
        val grow = 1f + 0.06f * (1f - k)
        val rOut = rangePx * grow

        // 사거리 안쪽을 아주 살짝 밝게
        fill.color = Color.argb((9 * k).toInt(), 255, 250, 235)
        c.drawCircle(px, py, rOut, fill)
        fill.color = Color.argb((8 * k).toInt(), 255, 250, 235)
        c.drawCircle(px, py, rOut * 0.72f, fill)

        // 사거리 원 둘레 눈금 (렌즈 거리 스케일 느낌)
        stroke.pathEffect = null
        stroke.strokeWidth = 1.5f
        for (i in 0 until 24) {
            val a = i * 15f
            val rad = Math.toRadians(a.toDouble())
            val cosA = kotlin.math.cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val long = i % 4 == 0
            val inner = rOut + 5f
            val outer = inner + if (long) 9f else 5f
            stroke.color = Color.argb(((if (long) 96 else 46) * k).toInt(), 255, 248, 232)
            c.drawLine(
                px + cosA * inner, py + sinA * inner,
                px + cosA * outer, py + sinA * outer, stroke
            )
        }

        // 사거리 점선 원 (천천히 돈다)
        ring.strokeWidth = 1.7f
        ring.pathEffect = DashPathEffect(floatArrayOf(11f, 9f), -clock * 14f)
        ring.color = Color.argb((124 * k).toInt(), 255, 250, 235)
        c.drawCircle(px, py, rOut, ring)
        ring.pathEffect = null

        // ★3 / ★2 구역 링
        ring.strokeWidth = 1.3f
        ring.color = Color.argb((78 * k).toInt(), 111, 186, 107)
        c.drawCircle(px, py, rOut * 0.38f, ring)
        ring.color = Color.argb((78 * k).toInt(), 242, 182, 60)
        c.drawCircle(px, py, rOut * 0.72f, ring)

        // 최단 촬영 거리 — 초망원은 너무 가까우면 화각에 안 들어온다
        if (minTiles > 0.9f) {
            ring.strokeWidth = 1.5f
            ring.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), clock * 10f)
            ring.color = Color.argb((150 * k).toInt(), 226, 87, 76)
            c.drawCircle(px, py, minPx, ring)
            ring.pathEffect = null
            val mx = px - minPx
            text.textSize = 10.5f
            text.color = Color.argb((190 * k).toInt(), 255, 196, 188)
            val ms = "최소 ${fmt(minTiles)}칸"
            c.drawText(ms, mx - text.measureText(ms) - 6f, py + 4f, text)
        }

        // 별점 구역 라벨 (왼쪽 반지름 위)
        text.textSize = 11.5f
        zoneLabel(c, "★3", px - rOut * 0.38f - 8f, py - 2f, ZONE3, k)
        zoneLabel(c, "★2", px - rOut * 0.72f - 8f, py - 2f, ZONE2, k)

        // 사거리 명판 (원 아래쪽)
        val labelY = (py + rOut + 30f).coerceIn(150f, game.virtH - 112f)
        val lbl = "사거리 ${fmt(rangeTiles)}칸"
        val sub = "★3 ≤ ${fmt(rangeTiles * 0.38f)}칸 · ★2 ≤ ${fmt(rangeTiles * 0.72f)}칸"
        text.textSize = 12.5f
        val lw = maxOf(text.measureText(lbl), textSoft.measureText(sub)) + 30f
        val plate = RectF(px - lw / 2f, labelY - 15f, px + lw / 2f, labelY + 15f)
        glass(c, plate, 8f, (196 * k).toInt(), (110 * k).toInt())
        fill.color = Color.argb((230 * k).toInt(), 242, 208, 107)
        c.drawCircle(plate.left + 12f, plate.centerY(), 3.1f, fill)
        text.color = Color.argb((228 * k).toInt(), 252, 246, 232)
        c.drawText(lbl, plate.left + 21f, plate.top + 13.5f, text)
        textSoft.textSize = 9.6f
        textSoft.color = Color.argb((170 * k).toInt(), 214, 206, 190)
        c.drawText(sub, plate.left + 21f, plate.top + 25.5f, textSoft)
    }

    private fun zoneLabel(c: Canvas, s: String, x: Float, y: Float, color: Int, k: Float) {
        text.textSize = 11.5f
        val tw = text.measureText(s)
        if (x - tw < 26f) return
        val r = RectF(x - tw - 17f, y - 11f, x + 5f, y + 7f)
        glass(c, r, 6f, (150 * k).toInt(), (70 * k).toInt())
        fill.color = Color.argb((235 * k).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        c.drawCircle(r.left + 8f, r.centerY(), 3f, fill)
        text.color = color
        c.drawText(s, r.left + 14f, r.centerY() + 3.8f, text)
    }

    // ------------------------------------------------------------------
    // 4. 새 표시 (AF 박스 · 별점 칩)
    // ------------------------------------------------------------------

    /** 조준 중인 새: 사거리 안에서 가장 가까운 새 (없으면 1.45배까지 후보로 삼아 안내) */
    private fun pickFocus(
        birds: List<FieldBird>, pcx: Float, pcy: Float, camX: Float, camY: Float,
        rangeTiles: Float, zoom: Float
    ): FieldBird? {
        var best: FieldBird? = null
        var bestD = Float.MAX_VALUE
        val w = game.virtW.toFloat()
        val h = game.virtH.toFloat()
        for (b in birds) {
            if (b.state == 2) continue
            val bx = sx(b.cx, camX, zoom)
            val by = sy(b.cy, camY, zoom)
            if (bx < 34f || bx > w - 34f || by < 84f || by > h - 76f) continue
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
        rangeTiles: Float,
        zoom: Float,
        k: Float
    ) {
        for (b in birds) {
            if (b.state == 2) continue
            val bx = sx(b.cx, camX, zoom)
            val by = sy(b.cy, camY, zoom)
            if (bx < -40f || bx > game.virtW + 40f || by < -40f || by > game.virtH + 40f) continue
            val dTiles = hypot(b.cx - pcx, b.cy - pcy) / 16f
            val inRange = dTiles <= rangeTiles
            val topY = by - b.sprH * WORLD_SCALE * 0.5f * zoom - 26f
            if (b === focus) {
                drawFocusBox(c, b, bx, by, dTiles, rangeTiles, inRange, zoom, k)
            } else if (inRange) {
                drawStarChip(c, bx, topY, dTiles / rangeTiles, b, k)
            } else {
                // 사거리 밖 — 아주 옅은 점으로만 존재를 알린다
                val tw = 0.5f + 0.5f * sin(clock * 2.2f + bx * 0.05f)
                fill.color = Color.argb(((60 + 40 * tw) * k).toInt(), 255, 248, 232)
                c.drawCircle(bx, topY + 8f, 2.1f, fill)
                fill.color = Color.argb((26 * k).toInt(), 255, 248, 232)
                c.drawCircle(bx, topY + 8f, 5.2f, fill)
            }
        }
    }

    /** 사거리 안의 다른 새 — 별점 예상 칩 (미확인 종은 금색 ? 칩) */
    private fun drawStarChip(c: Canvas, cx: Float, y: Float, ratio: Float, b: FieldBird, k: Float) {
        val seen = (state.birdCounts[b.def.id] ?: 0) > 0
        val (stars, col) = zone(ratio)
        val label = if (seen) "★".repeat(stars) + "☆".repeat(3 - stars) else "미확인 ?"
        val chipCol = if (seen) col else GOLD
        text.textSize = 11f
        val tw = text.measureText(label)
        val r = RectF(cx - (tw + 20f) / 2f, y - 11f, cx + (tw + 20f) / 2f, y + 7f)
        glass(c, r, 6f, (168 * k).toInt(), (96 * k).toInt())
        fill.color = Color.argb((240 * k).toInt(), Color.red(chipCol), Color.green(chipCol), Color.blue(chipCol))
        c.drawCircle(r.left + 8f, r.centerY(), 2.8f, fill)
        text.color = if (seen) Color.argb((238 * k).toInt(), 250, 244, 230)
        else Color.argb((245 * k).toInt(), Color.red(GOLD), Color.green(GOLD), Color.blue(GOLD))
        c.drawText(label, r.left + 14f, r.centerY() + 3.8f, text)
    }

    /** AF 박스 + 이름/등급/별점 명판 + 초점 거리 바 */
    private fun drawFocusBox(
        c: Canvas,
        b: FieldBird,
        bx: Float,
        by: Float,
        dTiles: Float,
        rangeTiles: Float,
        inRange: Boolean,
        zoom: Float,
        k: Float
    ) {
        val ratio = (dTiles / rangeTiles).coerceAtLeast(0.001f)
        val (stars, col) = zone(ratio)
        val seen = (state.birdCounts[b.def.id] ?: 0) > 0
        val perfect = inRange && ratio < 0.38f
        val pulse = if (inRange) 1f + sin(clock * 5.5f) * 0.018f else 1f + sin(clock * 3f) * 0.05f
        val halfW = (b.sprW * WORLD_SCALE * 0.5f * zoom + 14f) * pulse
        val halfH = (b.sprH * WORLD_SCALE * 0.5f * zoom + 14f) * pulse
        val box = RectF(bx - halfW, by - halfH, bx + halfW, by + halfH)

        // 초점 피사체 뒤의 부드러운 빛 — 심도 어둠 속에서 새가 또렷하게 떠 보이도록
        val ghost = if (perfect) GOLD else 0xFFFFF2CE.toInt()
        Glow.draw(c, Glow.warm, bx, by, halfW * 1.55f, halfH * 1.45f, if (perfect) 46 else 30)
        if (perfect) {
            // 3★ 순간 — 금빛 초점 링
            ring.strokeWidth = 1.8f
            ring.pathEffect = null
            ring.color = Color.argb((120 + 70 * sin(clock * 4f)).toInt().coerceIn(0, 255), Color.red(ghost), Color.green(ghost), Color.blue(ghost))
            c.drawOval(RectF(bx - halfW * 1.25f, by - halfH * 1.2f, bx + halfW * 1.25f, by + halfH * 1.2f), ring)
        }

        // AF 획득 연출 — 바깥에서 좁혀오는 링 두 겹
        if (focusT < 0.5f) {
            val p = (focusT / 0.5f).coerceIn(0f, 1f)
            val grow = (1f - p) * 30f
            val a = ((1f - p) * 210f).toInt().coerceIn(0, 255)
            stroke.pathEffect = null
            stroke.color = Color.argb(a, Color.red(col), Color.green(col), Color.blue(col))
            stroke.strokeWidth = 2.2f
            c.drawRoundRect(
                RectF(box.left - grow, box.top - grow, box.right + grow, box.bottom + grow),
                6f, 6f, stroke
            )
            stroke.strokeWidth = 1.2f
            stroke.color = Color.argb((a * 0.55f).toInt(), 255, 250, 235)
            c.drawRoundRect(
                RectF(box.left - grow * 0.55f, box.top - grow * 0.55f, box.right + grow * 0.55f, box.bottom + grow * 0.55f),
                6f, 6f, stroke
            )
            // 대각선 AF 틱 (카메라 앱 특유의 연출)
            val dl = 9f + 7f * p
            stroke.strokeWidth = 2f
            stroke.color = Color.argb((a * 0.9f).toInt(), Color.red(col), Color.green(col), Color.blue(col))
            val d = grow + 7f + dl
            val dp = Path()
            dp.moveTo(box.left - d, box.top - d + dl); dp.lineTo(box.left - d, box.top - d); dp.lineTo(box.left - d + dl, box.top - d)
            dp.moveTo(box.right + d - dl, box.top - d); dp.lineTo(box.right + d, box.top - d); dp.lineTo(box.right + d, box.top - d + dl)
            dp.moveTo(box.right + d, box.bottom + d - dl); dp.lineTo(box.right + d, box.bottom + d); dp.lineTo(box.right + d - dl, box.bottom + d)
            dp.moveTo(box.left - d + dl, box.bottom + d); dp.lineTo(box.left - d, box.bottom + d); dp.lineTo(box.left - d, box.bottom + d - dl)
            c.drawPath(dp, stroke)
        }

        // 모서리 브래킷
        val tick = minOf(13f, box.width() * 0.32f)
        stroke.color = if (inRange) Color.argb(242, Color.red(col), Color.green(col), Color.blue(col))
        else Color.argb(205, 246, 240, 224)
        stroke.strokeWidth = 2.4f
        stroke.pathEffect = if (inRange) null else DashPathEffect(floatArrayOf(7f, 6f), -clock * 26f)
        val p = Path()
        p.moveTo(box.left, box.top + tick); p.lineTo(box.left, box.top); p.lineTo(box.left + tick, box.top)
        p.moveTo(box.right - tick, box.top); p.lineTo(box.right, box.top); p.lineTo(box.right, box.top + tick)
        p.moveTo(box.right, box.bottom - tick); p.lineTo(box.right, box.bottom); p.lineTo(box.right - tick, box.bottom)
        p.moveTo(box.left + tick, box.bottom); p.lineTo(box.left, box.bottom); p.lineTo(box.left, box.bottom - tick)
        c.drawPath(p, stroke)
        stroke.pathEffect = null

        // 좌우 미세 눈금 (초점 거리 스케일)
        stroke.strokeWidth = 1.3f
        stroke.color = Color.argb(96, 255, 250, 235)
        for (i in 1..4) {
            val yy = box.top + box.height() * i / 5f
            c.drawLine(box.left + 2f, yy, box.left + 8f, yy, stroke)
            c.drawLine(box.right - 8f, yy, box.right - 2f, yy, stroke)
        }

        // 초점 영역 거리 바 (박스 아래) — 구역 색과 현재 거리 마커
        val barR = RectF(box.left, box.bottom + 7f, box.right, box.bottom + 12f)
        fill.color = Color.argb(170, 14, 12, 22)
        c.drawRoundRect(barR, 2.5f, 2.5f, fill)
        val w3 = barR.width() * 0.38f
        fill.color = Color.argb(170, 111, 186, 107)
        c.drawRoundRect(RectF(barR.left, barR.top, barR.left + w3, barR.bottom), 2.5f, 2.5f, fill)
        fill.color = Color.argb(150, 242, 182, 60)
        c.drawRoundRect(RectF(barR.left + w3, barR.top, barR.left + barR.width() * 0.72f, barR.bottom), 2f, 2f, fill)
        fill.color = Color.argb(130, 226, 87, 76)
        c.drawRoundRect(RectF(barR.left + barR.width() * 0.72f, barR.top, barR.right, barR.bottom), 2f, 2f, fill)
        val markX = barR.left + barR.width() * ratio.coerceIn(0f, 1f)
        stroke.color = Color.argb(250, 255, 252, 244)
        stroke.strokeWidth = 1.8f
        c.drawLine(markX, barR.top - 3f, markX, barR.bottom + 3f, stroke)

        // 이름 명판
        val name = if (seen) b.def.name else "??? 미확인"
        val tier = b.def.tier
        val tierStr = if (seen) tier.label else "??"
        val info = if (inRange) "★".repeat(stars) + "☆".repeat(3 - stars) + "  ·  ${fmt(dTiles)}칸"
        else "더 가까이!  ·  ${fmt(dTiles)}칸"

        text.textSize = 13.5f
        val nameW = text.measureText(name)
        text.textSize = 10.5f
        val tierW = text.measureText(tierStr) + 14f
        text.textSize = 11.5f
        val infoW = text.measureText(info)
        val rowNameW = 20f + nameW + (if (seen) 8f + tierW else 0f) + 14f
        val rowInfoW = 20f + infoW + 14f
        val plateW = maxOf(rowNameW, rowInfoW)
        val plateH = 38f
        val plateCx = bx.coerceIn(52f + plateW / 2f, game.virtW - 52f - plateW / 2f)
        var plateTop = box.top - plateH - 12f
        if (plateTop < 88f) plateTop = box.bottom + 20f
        val plate = RectF(plateCx - plateW / 2f, plateTop, plateCx + plateW / 2f, plateTop + plateH)

        glass(c, plate, 9f, 210, 92)
        stroke.color = Color.argb(232, Color.red(col), Color.green(col), Color.blue(col))
        stroke.strokeWidth = 1.4f
        c.drawRoundRect(plate, 9f, 9f, stroke)

        // 왼쪽: 초점 확인 표시 (●) 또는 대기 (◌)
        if (inRange) {
            fill.color = Color.argb(240, Color.red(col), Color.green(col), Color.blue(col))
            c.drawCircle(plate.left + 11f, plate.centerY(), 3.5f, fill)
            fill.color = Color.argb(70, Color.red(col), Color.green(col), Color.blue(col))
            c.drawCircle(plate.left + 11f, plate.centerY(), 6.6f, fill)
        } else {
            stroke.color = Color.argb(200, 246, 240, 224)
            stroke.strokeWidth = 1.4f
            c.drawCircle(plate.left + 11f, plate.centerY(), 4.8f, stroke)
        }

        text.textSize = 13.5f
        text.color = if (seen) Color.argb(244, 252, 248, 238) else Color.argb(215, 202, 197, 212)
        c.drawText(name, plate.left + 20f, plate.top + 16f, text)

        // 등급 뱃지
        if (seen) {
            val tierBg = UiKit.tierColor(tier)
            val badge = RectF(plate.left + 28f + nameW, plate.top + 5f, plate.left + 28f + nameW + tierW, plate.top + 18f)
            fill.color = Color.argb(230, Color.red(tierBg), Color.green(tierBg), Color.blue(tierBg))
            c.drawRoundRect(badge, 4f, 4f, fill)
            text.textSize = 10.5f
            text.color = Color.argb(250, 255, 252, 244)
            c.drawText(tierStr, badge.left + 7f, badge.centerY() + 3.6f, text)
        }

        text.textSize = 11.5f
        text.color = if (inRange) Color.argb(236, Color.red(col), Color.green(col), Color.blue(col))
        else Color.argb(210, 246, 240, 224)
        c.drawText(info, plate.left + 20f, plate.top + 31f, text)

        // 3★ 순간의 반짝임
        if (perfect) {
            UiKit.sparkle(c, plate.right - 12f, plate.top + 10f, 5f, GOLD, sparkT * 1.6f)
            UiKit.sparkle(c, box.right + 6f, box.top + 4f, 4f, 0xFFFFFFFF.toInt(), sparkT * 2.1f)
            UiKit.sparkle(c, box.left - 6f, box.bottom - 4f, 3.5f, GOLD, sparkT * 1.2f)
        }
    }

    // ------------------------------------------------------------------
    // 5. 상단 정보 바
    // ------------------------------------------------------------------

    private fun drawTopBar(c: Canvas, w: Float, k: Float) {
        val y = 26f - (1f - k) * 16f
        val a = k

        // 좌측: REC 표시등 + PHOTO
        val blink = if (sin(clock * 4.2f) > -0.2f) 1f else 0.22f
        text.textSize = 13f
        val badgeW = text.measureText("PHOTO") + 44f
        val badge = RectF(38f, y, 38f + badgeW, y + 28f)
        glass(c, badge, 8f, (176 * a).toInt(), (96 * a).toInt())
        fill.color = Color.argb((72 * a * blink).toInt(), 226, 87, 76)
        c.drawCircle(badge.left + 14f, badge.centerY(), 9f, fill)
        fill.color = Color.argb((242 * a * blink).toInt(), 226, 87, 76)
        c.drawCircle(badge.left + 14f, badge.centerY(), 4.6f, fill)
        text.color = Color.argb((242 * a).toInt(), 250, 246, 236)
        c.drawText("PHOTO", badge.left + 24f, badge.centerY() + 4.6f, text)

        // 그 옆: AF-C · RAW 칩
        text.textSize = 10.5f
        val modeStr = "AF-C · RAW"
        val modeW = text.measureText(modeStr) + 20f
        val modeR = RectF(badge.right + 8f, y + 4f, badge.right + 8f + modeW, y + 24f)
        glass(c, modeR, 7f, (140 * a).toInt(), (58 * a).toInt())
        text.color = Color.argb((196 * a).toInt(), 226, 218, 200)
        c.drawText(modeStr, modeR.left + 10f, modeR.centerY() + 3.6f, text)

        // 중앙: 시각 + 하루 진행 게이지
        val night = state.isNight()
        val clockTxt = "${state.timeEmoji()} ${state.timeLabel()}"
        mono.textSize = 16f
        val cw = mono.measureText(clockTxt) + 56f
        val cr = RectF(w / 2f - cw / 2f, y, w / 2f + cw / 2f, y + 32f)
        glass(c, cr, 9f, (176 * a).toInt(), (96 * a).toInt())
        mono.color = Color.argb((242 * a).toInt(), 250, 246, 236)
        c.drawText(clockTxt, cr.left + 14f, cr.top + 15f, mono)
        // 하루 진행 게이지 (0시 → 24시)
        val dayP = (state.worldTime / 24f).coerceIn(0f, 1f)
        val barR = RectF(cr.left + 14f, cr.top + 21f, cr.right - 14f, cr.top + 26f)
        fill.color = Color.argb((130 * a).toInt(), 32, 28, 44)
        c.drawRoundRect(barR, 2.5f, 2.5f, fill)
        val dayCol = if (night) 0xFF7C8CC8.toInt() else GOLD
        fill.color = Color.argb((190 * a).toInt(), Color.red(dayCol), Color.green(dayCol), Color.blue(dayCol))
        c.drawRoundRect(RectF(barR.left, barR.top, barR.left + barR.width() * dayP, barR.bottom), 2.5f, 2.5f, fill)
        // 밤 구간 표시
        val nightStart = 19.5f / 24f
        fill.color = Color.argb((90 * a).toInt(), 24, 28, 48)
        c.drawRoundRect(RectF(barR.left + barR.width() * nightStart, barR.top, barR.right, barR.bottom), 2.5f, 2.5f, fill)

        // 우측: 장비 카드 (렌즈 아이콘 + 이름 + 성능)
        val rig = state.rig()
        val cardW = 264f
        val card = RectF(w - 38f - cardW, y, w - 38f, y + 46f)
        glass(c, card, 9f, (176 * a).toInt(), (96 * a).toInt())
        val camIcon = game.assets.camIcon(rig.look)
        val iconW = 44f
        val iconH = iconW * 18f / 22f
        c.drawBitmap(
            camIcon, null,
            RectF(card.left + 8f, card.centerY() - iconH / 2f, card.left + 8f + iconW, card.centerY() + iconH / 2f),
            game.assets.sprPaint
        )
        text.textSize = 13f
        var nameTxt = rig.title
        val nameMax = card.width() - iconW - 26f
        if (text.measureText(nameTxt) > nameMax) {
            while (nameTxt.length > 1 && text.measureText("$nameTxt…") > nameMax) nameTxt = nameTxt.dropLast(1)
            nameTxt = "$nameTxt…"
        }
        text.color = Color.argb((242 * a).toInt(), 250, 246, 236)
        c.drawText(nameTxt, card.left + 10f + iconW, card.top + 19f, text)
        textSoft.textSize = 10.8f
        textSoft.color = Color.argb((196 * a).toInt(), 214, 208, 224)
        c.drawText(
            "환산 ${rig.teleMm}mm · ${rig.sensor.label} · 사거리 ${fmt(rig.reach)}칸",
            card.left + 10f + iconW, card.top + 34f, textSoft
        )
    }

    // ------------------------------------------------------------------
    // 6. 하단 정보 바
    // ------------------------------------------------------------------

    private fun drawBottomBar(
        c: Canvas,
        w: Float,
        h: Float,
        focus: FieldBird?,
        pcx: Float,
        pcy: Float,
        rangeTiles: Float,
        k: Float
    ) {
        val y = h - 88f + (1f - k) * 18f
        val boxW = 640f
        val boxH = 62f
        val r = RectF(w / 2f - boxW / 2f, y, w / 2f + boxW / 2f, y + boxH)
        glass(c, r, 11f, (186 * k).toInt(), (100 * k).toInt())

        val rig = state.rig()

        // 왼쪽: EXIF + 장비 정보
        mono.textSize = 13f
        mono.color = Color.argb((236 * k).toInt(), 240, 234, 246)
        c.drawText(rig.exifLine(state.darkness()), r.left + 16f, r.top + 24f, mono)
        mono.textSize = 11f
        mono.color = Color.argb((196 * k).toInt(), 208, 202, 218)
        val steadyTxt = if (rig.steady >= 5f) "IS ●" else "IS ○"
        c.drawText(
            "${fmt(rig.burst)}fps · $steadyTxt · ${rig.weightG}g",
            r.left + 16f, r.top + 42f, mono
        )

        // 구분선
        divider(c, r.left + 214f, r.top + 10f, r.bottom - 10f, k)

        // 가운데: 거리 게이지
        val gx = r.left + 232f
        val gw = 174f
        val gy = r.top + 20f
        val gh = 9f
        fill.color = Color.argb((200 * k).toInt(), 32, 28, 44)
        c.drawRoundRect(RectF(gx, gy, gx + gw, gy + gh), 4.5f, 4.5f, fill)
        fill.color = Color.argb((176 * k).toInt(), 111, 186, 107)
        c.drawRoundRect(RectF(gx + 1f, gy + 1f, gx + gw * 0.38f, gy + gh - 1f), 4f, 4f, fill)
        fill.color = Color.argb((154 * k).toInt(), 242, 182, 60)
        c.drawRoundRect(RectF(gx + gw * 0.38f, gy + 1f, gx + gw * 0.72f, gy + gh - 1f), 4f, 4f, fill)
        fill.color = Color.argb((134 * k).toInt(), 226, 87, 76)
        c.drawRoundRect(RectF(gx + gw * 0.72f, gy + 1f, gx + gw - 1f, gy + gh - 1f), 4f, 4f, fill)

        // 게이지 눈금
        stroke.strokeWidth = 1f
        stroke.color = Color.argb((110 * k).toInt(), 255, 250, 235)
        for (i in 1..7) {
            val tx = gx + gw * i / 8f
            val lh = if (i == 4) 4f else 2.5f
            c.drawLine(tx, gy - lh, tx, gy - 1f, stroke)
        }

        text.textSize = 11f
        if (focus != null) {
            val dTiles = hypot(focus.cx - pcx, focus.cy - pcy) / 16f
            val ratio = (dTiles / rangeTiles).coerceIn(0f, 1f)
            val mx = gx + gw * ratio
            stroke.color = Color.argb((250 * k).toInt(), 255, 252, 244)
            stroke.strokeWidth = 2f
            c.drawLine(mx, gy - 6f, mx, gy + gh + 6f, stroke)
            fill.color = Color.argb((250 * k).toInt(), 255, 252, 244)
            c.drawCircle(mx, gy - 7.5f, 1.9f, fill)
            val (stars, col) = zone((dTiles / rangeTiles).coerceAtLeast(0.001f))
            text.color = Color.argb((238 * k).toInt(), Color.red(col), Color.green(col), Color.blue(col))
            val s = "예상 " + "★".repeat(stars) + "☆".repeat(3 - stars)
            c.drawText(s, gx + gw - text.measureText(s), r.top + 48f, text)
            text.color = Color.argb((205 * k).toInt(), 214, 208, 224)
            c.drawText("거리 ${fmt(dTiles)}칸", gx, r.top + 48f, text)
        } else {
            text.color = Color.argb((205 * k).toInt(), 214, 208, 224)
            c.drawText("새를 찾는 중…", gx, r.top + 48f, text)
            val sx1 = gx + (0.5f + 0.5f * sin(clock * 3f)) * gw
            fill.color = Color.argb((224 * k).toInt(), 246, 240, 224)
            c.drawCircle(sx1, gy + gh / 2f, 3.2f, fill)
        }

        divider(c, r.left + 442f, r.top + 10f, r.bottom - 10f, k)

        // 오른쪽: 촬영 수 + 계절·날씨
        mono.textSize = 12.5f
        mono.color = Color.argb((228 * k).toInt(), 240, 234, 246)
        val shots = "관측 ${state.photos}컷"
        c.drawText(shots, r.left + 460f, r.top + 24f, mono)
        textSoft.textSize = 10.6f
        textSoft.color = Color.argb((190 * k).toInt(), 208, 200, 214)
        c.drawText(
            "${state.seasonLabel()} · ${state.weather().icon} ${state.weather().label}",
            r.left + 460f, r.top + 42f, textSoft
        )
    }

    private fun divider(c: Canvas, x: Float, top: Float, bottom: Float, k: Float) {
        stroke.strokeWidth = 1f
        stroke.pathEffect = null
        stroke.color = Color.argb((70 * k).toInt(), 246, 240, 224)
        c.drawLine(x, top, x, bottom, stroke)
    }

    // ------------------------------------------------------------------
    // 7. 첫 사용 안내
    // ------------------------------------------------------------------

    private fun drawHint(c: Canvas, w: Float, h: Float, fade: Float) {
        val a = (255 * fade).toInt().coerceIn(0, 255)
        if (a < 4) return
        val bob = sin(clock * 2.2f) * 3f
        val cy = h * 0.64f + bob
        val line1 = "새를 탭해 촬영하세요"
        val line2 = "카메라 버튼을 다시 누르면 나갑니다"
        text.textSize = 15f
        textSoft.textSize = 11.5f
        val pw = maxOf(text.measureText(line1), textSoft.measureText(line2)) + 44f
        val plate = RectF(w / 2f - pw / 2f, cy - 27f, w / 2f + pw / 2f, cy + 27f)
        glass(c, plate, 12f, (176 * a / 255), (96 * a / 255))
        // 작은 카메라 아이콘
        val cam = game.assets.camIcon(state.rig().look)
        val iw = 30f
        c.drawBitmap(
            cam, null,
            RectF(plate.left + 12f, plate.centerY() - iw * 9f / 22f, plate.left + 12f + iw, plate.centerY() + iw * 9f / 22f),
            game.assets.sprPaint
        )
        text.textSize = 15f
        text.color = Color.argb(a, 255, 250, 235)
        c.drawText(line1, plate.left + 50f, plate.top + 21f, text)
        textSoft.textSize = 11.5f
        textSoft.color = Color.argb((a * 0.72f).toInt(), 226, 220, 206)
        c.drawText(line2, plate.left + 50f, plate.top + 40f, textSoft)
    }

    // ------------------------------------------------------------------
    // 8. 셔터 (월드 위에 마지막으로 그린다)
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
                // 블레이드 몸통 — 위는 살짝 밝고 아래로 갈수록 어두운 금속 느낌
                grad.shader = LinearGradient(
                    0f, if (top) edge - 40f else h - edge, 0f, if (top) 0f else h,
                    intArrayOf(0xFF1B1622.toInt(), 0xFF100D16.toInt()),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                c.drawPath(p, grad)
                grad.shader = null

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
                glow.color = Color.argb((205 * e).toInt(), 255, 244, 214)
                glow.strokeWidth = 2.4f
                c.drawPath(g, glow)

                // 금색 헤어라인
                glow.color = Color.argb((120 * e).toInt(), 242, 208, 107)
                glow.strokeWidth = 1.1f
                c.drawPath(g, glow)
            }

            // 닫히는 속도감 — 안쪽으로 빨려드는 짧은 선
            val closing = if (phase == CLOSING) (t / CLOSE_T).coerceIn(0f, 1f) else 0f
            if (closing > 0.08f) {
                stroke.strokeWidth = 1.4f
                stroke.pathEffect = null
                for (i in 0 until 9) {
                    val x = w * (0.1f + 0.1f * i)
                    val len = 20f + 34f * closing
                    stroke.color = Color.argb((120 * closing).toInt(), 255, 246, 224)
                    c.drawLine(x, 0f, x + 14f, len, stroke)
                    c.drawLine(x, h, x - 14f, h - len, stroke)
                }
            }
        }

        // "찰칵!" 순간 연출
        if (phase == CLOSING) {
            val p = (t / CLOSE_T).coerceIn(0f, 1f)
            if (p > 0.42f) {
                val q = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)
                val a = (255 * q).toInt().coerceIn(0, 255)
                val pop = 1f + (1f - q) * 0.22f
                text.textSize = 28f * pop
                val s = "찰칵!"
                val tw = text.measureText(s)
                text.color = Color.argb((a * 0.55f).toInt(), 10, 8, 16)
                c.drawText(s, w / 2f - tw / 2f + 2.5f, h / 2f + 13f, text)
                text.color = Color.argb(a, 255, 248, 232)
                c.drawText(s, w / 2f - tw / 2f, h / 2f + 10f, text)
                // 금색 밑줄 플러리시
                stroke.strokeWidth = 2.2f
                stroke.color = Color.argb((a * 0.85f).toInt(), 242, 208, 107)
                c.drawLine(w / 2f - tw / 2f - 8f, h / 2f + 20f, w / 2f + tw / 2f + 8f, h / 2f + 20f, stroke)
            }
        }

        // 플래시 + 따뜻한 파문
        if (flash > 0f) {
            val cx = w / 2f
            val cy = h / 2f
            glow.color = Color.argb((70 * flash).toInt().coerceIn(0, 255), 255, 236, 190)
            glow.strokeWidth = 26f
            glow.style = Paint.Style.STROKE
            glow.isAntiAlias = true
            c.drawCircle(cx, cy, 120f + 260f * (1f - flash), glow)
            glow.style = Paint.Style.FILL
            fill.color = Color.argb((168 * flash).toInt().coerceIn(0, 255), 255, 252, 244)
            c.drawRect(0f, 0f, w, h, fill)
        }
    }

    // ------------------------------------------------------------------
    // 공통
    // ------------------------------------------------------------------

    /** 유리 카드 — 위에서 아래로 살짝 밝아지는 반투명 + 크림 헤어라인 */
    private fun glass(c: Canvas, r: RectF, radius: Float, alpha: Int, edge: Int) {
        if (alpha <= 2) return
        grad.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(Color.argb((alpha * 0.94f).toInt(), 36, 30, 46), Color.argb(alpha, 14, 12, 20)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, radius, radius, grad)
        grad.shader = null
        if (edge > 2) {
            stroke.color = Color.argb(edge, 246, 240, 224)
            stroke.strokeWidth = 1.1f
            stroke.pathEffect = null
            c.drawRoundRect(r, radius, radius, stroke)
            // 상단 하이라이트
            stroke.color = Color.argb((edge * 0.35f).toInt(), 255, 252, 244)
            stroke.strokeWidth = 1f
            c.drawLine(r.left + radius, r.top + 1.1f, r.right - radius, r.top + 1.1f, stroke)
        }
    }

    /** 거리 비율 -> (별점, 색) */
    private fun zone(ratio: Float): Pair<Int, Int> = when {
        ratio < 0.38f -> 3 to ZONE3
        ratio < 0.72f -> 2 to ZONE2
        else -> 1 to ZONE1
    }

    private fun fmt(v: Float): String = String.format("%.1f", v)

    /** 0..1 해시 (보케 자리 잡기용) */
    private fun frac(v: Float): Float = v - kotlin.math.floor(v)

    private companion object {
        const val CLOSING = 0
        const val CLOSED = 1
        const val OPENING = 2
        const val OPEN = 3
        const val CLOSE_T = 0.10f
        const val OPEN_T = 0.28f

        val GOLD = 0xFFF2C86B.toInt()
        val ZONE3 = 0xFF7FD07A.toInt()
        val ZONE2 = 0xFFF2C86B.toInt()
        val ZONE1 = 0xFFE2574C.toInt()
    }
}
