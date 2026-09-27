package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos

/**
 * 한반도 지도 (실제 경위도 기반 벡터 실루엣).
 *
 * - 모든 좌표는 실제 경도/위도를 정규화 좌표(-1..1 부근, y는 아래가 +)로 투영한 값.
 * - 미니맵(원형)과 큰 지도(확대/이동 가능)가 이 데이터를 공유한다.
 */
object KoreaMap {

    // 투영 기준 (남한이 -0.5..0.9 범위에 들어오도록)
    private const val LON0 = 127.8f
    private const val LAT0 = 35.9f
    private const val SPAN = 3.2f
    private val KX = cos(Math.toRadians(36.5)).toFloat()   // 경도 축 보정 (≈0.804)

    fun nx(lon: Float): Float = (lon - LON0) * KX / SPAN
    fun ny(lat: Float): Float = -(lat - LAT0) / SPAN

    // ------------------------------------------------------------------
    // 해안선 데이터 (lon, lat)
    // ------------------------------------------------------------------

    /** 남한 본토 — 북쪽 DMZ에서 시계방향으로 서해안 → 남해안 → 동해안 */
    private val SOUTH = floatArrayOf(
        126.68f, 37.98f,  126.50f, 37.75f,  126.62f, 37.62f,  126.45f, 37.45f,
        126.70f, 37.30f,  126.82f, 37.18f,  126.62f, 37.05f,  126.52f, 36.96f,
        126.36f, 36.86f,  126.24f, 36.72f,  126.13f, 36.64f,  126.32f, 36.50f,
        126.40f, 36.36f,  126.50f, 36.22f,  126.58f, 36.05f,  126.72f, 35.96f,
        126.60f, 35.82f,  126.48f, 35.66f,  126.63f, 35.55f,  126.45f, 35.40f,
        126.33f, 35.15f,  126.42f, 34.95f,  126.28f, 34.78f,  126.55f, 34.55f,
        126.85f, 34.65f,  127.05f, 34.75f,  127.28f, 34.88f,  127.50f, 34.72f,
        127.75f, 34.90f,  128.05f, 35.02f,  128.30f, 34.92f,  128.60f, 34.88f,
        128.85f, 35.08f,  129.05f, 35.08f,  129.20f, 35.12f,  129.32f, 35.22f,
        129.42f, 35.50f,  129.47f, 35.85f,  129.56f, 36.05f,  129.38f, 36.12f,
        129.42f, 36.40f,  129.45f, 36.90f,  129.33f, 37.30f,  129.02f, 37.60f,
        128.95f, 37.80f,  128.60f, 38.21f,  128.45f, 38.45f,  128.35f, 38.61f,
        127.50f, 38.31f,  126.98f, 38.12f
    )

    /** 북한 (참고용 실루엣 — 흐리게 표시) */
    private val NORTH = floatArrayOf(
        126.98f, 38.12f,  126.55f, 38.28f,  126.20f, 38.50f,  125.60f, 38.62f,
        125.38f, 38.72f,  125.08f, 38.60f,  125.32f, 39.05f,  125.00f, 39.35f,
        125.05f, 39.62f,  124.72f, 39.82f,  124.36f, 40.00f,  125.00f, 40.50f,
        126.00f, 41.10f,  126.90f, 41.70f,  127.60f, 41.45f,  128.25f, 41.42f,
        128.95f, 42.02f,  129.70f, 42.42f,  130.65f, 42.30f,  129.85f, 41.80f,
        129.50f, 41.20f,  129.20f, 40.70f,  128.60f, 40.30f,  127.95f, 40.00f,
        127.55f, 39.75f,  127.42f, 39.18f,  127.62f, 38.82f,  128.35f, 38.61f,
        127.50f, 38.31f
    )

    /** 섬들 */
    private val ISLANDS = listOf(
        // 강화도
        floatArrayOf(126.30f, 37.78f, 126.53f, 37.79f, 126.57f, 37.66f, 126.38f, 37.58f, 126.28f, 37.66f),
        // 안면도
        floatArrayOf(126.28f, 36.55f, 126.37f, 36.52f, 126.31f, 36.31f, 126.22f, 36.38f),
        // 거제도
        floatArrayOf(128.52f, 34.96f, 128.73f, 34.97f, 128.72f, 34.72f, 128.55f, 34.75f),
        // 남해도
        floatArrayOf(127.84f, 34.95f, 128.06f, 34.92f, 128.00f, 34.71f, 127.85f, 34.78f),
        // 진도
        floatArrayOf(126.10f, 34.55f, 126.36f, 34.55f, 126.32f, 34.34f, 126.12f, 34.38f),
        // 완도
        floatArrayOf(126.65f, 34.40f, 126.83f, 34.40f, 126.80f, 34.25f, 126.66f, 34.27f),
        // 영종도
        floatArrayOf(126.40f, 37.53f, 126.56f, 37.53f, 126.56f, 37.42f, 126.40f, 37.43f),
        // 울릉도
        floatArrayOf(130.83f, 37.52f, 130.93f, 37.52f, 130.93f, 37.44f, 130.83f, 37.44f)
    )

    /** 제주도 (타원) */
    private const val JEJU_LON = 126.55f
    private const val JEJU_LAT = 33.38f
    private const val JEJU_RLON = 0.47f
    private const val JEJU_RLAT = 0.21f

    /** 주요 하천 */
    private val RIVERS = listOf(
        // 한강
        floatArrayOf(127.92f, 37.50f, 127.30f, 37.55f, 126.98f, 37.55f, 126.66f, 37.60f, 126.52f, 37.73f),
        // 임진강
        floatArrayOf(127.25f, 38.22f, 126.95f, 37.96f, 126.72f, 37.87f, 126.58f, 37.80f),
        // 금강
        floatArrayOf(127.62f, 36.52f, 127.30f, 36.46f, 127.02f, 36.20f, 126.72f, 35.99f),
        // 영산강
        floatArrayOf(126.92f, 35.22f, 126.62f, 35.05f, 126.42f, 34.95f),
        // 섬진강
        floatArrayOf(127.40f, 35.62f, 127.48f, 35.36f, 127.66f, 35.10f, 127.76f, 34.98f),
        // 낙동강
        floatArrayOf(128.90f, 36.90f, 128.50f, 36.30f, 128.40f, 35.90f, 128.52f, 35.50f, 128.90f, 35.30f, 128.97f, 35.08f),
        // 만경강 · 동진강
        floatArrayOf(127.10f, 35.88f, 126.85f, 35.90f, 126.72f, 35.88f)
    )

    /** 산 (백두대간 주요 봉우리) — 지도에 작은 삼각형으로 */
    private val PEAKS = floatArrayOf(
        128.47f, 38.12f,   // 설악산
        128.54f, 37.79f,   // 오대산
        128.92f, 37.10f,   // 태백산
        128.48f, 36.95f,   // 소백산
        127.87f, 36.54f,   // 속리산
        127.75f, 35.86f,   // 덕유산
        127.73f, 35.34f,   // 지리산
        128.70f, 36.02f,   // 팔공산
        127.00f, 35.13f,   // 무등산
        126.88f, 35.48f,   // 내장산
        127.17f, 37.75f,   // 광릉 · 운악산
        126.53f, 33.36f    // 한라산
    )

    // ------------------------------------------------------------------
    // 투영된 Path (정규화 좌표계)
    // ------------------------------------------------------------------

    val southPath = Path()
    val northPath = Path()
    val jejuPath = Path()
    val islandPaths = ArrayList<Path>()
    val riverPaths = ArrayList<Path>()
    val dmzPath = Path()
    val peakPoints = ArrayList<FloatArray>()

    /** 남한 본토+제주를 감싸는 정규화 좌표 경계 (지도 기본 화면 맞춤용) */
    val southBounds = RectF()

    init {
        fun poly(src: FloatArray, dst: Path, close: Boolean = true) {
            dst.moveTo(nx(src[0]), ny(src[1]))
            var i = 2
            while (i < src.size) {
                dst.lineTo(nx(src[i]), ny(src[i + 1]))
                i += 2
            }
            if (close) dst.close()
        }
        poly(SOUTH, southPath)
        poly(NORTH, northPath)
        for (isl in ISLANDS) {
            val p = Path()
            poly(isl, p)
            islandPaths.add(p)
        }
        // 제주도 타원 (약간 찌그러진 럭비공)
        run {
            val steps = 28
            for (i in 0..steps) {
                val a = (i.toFloat() / steps) * (Math.PI * 2).toFloat()
                val lon = JEJU_LON + JEJU_RLON * kotlin.math.cos(a)
                val lat = JEJU_LAT + JEJU_RLAT * kotlin.math.sin(a) * (1f + 0.12f * kotlin.math.cos(a))
                if (i == 0) jejuPath.moveTo(nx(lon), ny(lat)) else jejuPath.lineTo(nx(lon), ny(lat))
            }
            jejuPath.close()
        }
        for (r in RIVERS) {
            val p = Path()
            poly(r, p, close = false)
            riverPaths.add(p)
        }
        // 휴전선 (DMZ)
        dmzPath.moveTo(nx(126.68f), ny(37.98f))
        dmzPath.lineTo(nx(126.98f), ny(38.12f))
        dmzPath.lineTo(nx(127.50f), ny(38.31f))
        dmzPath.lineTo(nx(128.35f), ny(38.61f))

        var i = 0
        while (i < PEAKS.size) {
            peakPoints.add(floatArrayOf(nx(PEAKS[i]), ny(PEAKS[i + 1])))
            i += 2
        }

        val b = RectF()
        southPath.computeBounds(b, true)
        southBounds.set(b)
        val jb = RectF()
        jejuPath.computeBounds(jb, true)
        southBounds.union(jb)
    }

    // ------------------------------------------------------------------
    // 그리기 — 캔버스가 이미 정규화 좌표계로 변환된 상태에서 호출한다.
    // unit = 정규화 1 단위가 화면 몇 px 인지 (선 굵기 보정용)
    // ------------------------------------------------------------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * @param detail 0 = 단순(미니맵), 1 = 보통, 2 = 상세(강/산/DMZ)
     * @param analog true 면 낡은 종이 해도 (수채 육지·잉크 해안선). 큰 지도는 false.
     */
    fun drawLand(c: Canvas, unit: Float, detail: Int, night: Boolean = false, analog: Boolean = false) {
        if (analog) {
            drawAnalogLand(c, unit, detail)
            return
        }
        val landCol = if (night) 0xFF5E7B5A.toInt() else 0xFFB9DCA0.toInt()
        val landEdge = if (night) 0xFF3E5540.toInt() else 0xFF7FA96A.toInt()
        val northCol = if (night) 0xFF4A5A55.toInt() else 0xFFCBD9C0.toInt()

        // 북한 (흐리게)
        fill.color = northCol
        c.drawPath(northPath, fill)

        // 남한
        fill.color = landCol
        c.drawPath(southPath, fill)
        c.drawPath(jejuPath, fill)
        for (p in islandPaths) c.drawPath(p, fill)

        // 해안선
        line.color = landEdge
        line.strokeWidth = (if (detail >= 2) 1.4f else 1.0f) / unit
        c.drawPath(southPath, line)
        c.drawPath(jejuPath, line)
        for (p in islandPaths) c.drawPath(p, line)
        line.color = if (night) 0xFF3E5550.toInt() else 0xFFAFC2A4.toInt()
        c.drawPath(northPath, line)

        if (detail >= 1) {
            // 하천
            line.color = if (night) 0xFF3F6E8E.toInt() else 0xFF7EC4E8.toInt()
            line.strokeWidth = (if (detail >= 2) 1.6f else 1.1f) / unit
            for (p in riverPaths) c.drawPath(p, line)
        }

        if (detail >= 2) {
            // 산
            fill.color = if (night) 0xFF4E5F45.toInt() else 0xFF8FA86B.toInt()
            val s = 6f / unit
            for (pt in peakPoints) {
                val path = Path()
                path.moveTo(pt[0], pt[1] - s)
                path.lineTo(pt[0] + s * 0.85f, pt[1] + s * 0.6f)
                path.lineTo(pt[0] - s * 0.85f, pt[1] + s * 0.6f)
                path.close()
                c.drawPath(path, fill)
            }
            // 휴전선
            line.color = 0xFFD98A8A.toInt()
            line.strokeWidth = 1.4f / unit
            val dash = android.graphics.DashPathEffect(floatArrayOf(5f / unit, 4f / unit), 0f)
            line.pathEffect = dash
            c.drawPath(dmzPath, line)
            line.pathEffect = null
        }
    }

    /** 회중 나침반용 — 종이에 찍힌 잉크 해도. 밤색은 호출측 조명으로 처리한다. */
    private fun drawAnalogLand(c: Canvas, unit: Float, detail: Int) {
        line.pathEffect = null
        fill.color = Color.argb(150, 196, 184, 150)
        c.drawPath(northPath, fill)

        // 레터프레스처럼 살짝 비낀 잉크 그림자
        c.save()
        c.translate(1.7f / unit, 2.0f / unit)
        fill.color = Color.argb(48, 72, 48, 24)
        c.drawPath(southPath, fill)
        c.drawPath(jejuPath, fill)
        for (p in islandPaths) c.drawPath(p, fill)
        c.restore()

        fill.color = Color.argb(236, 186, 164, 104)
        c.drawPath(southPath, fill)
        c.drawPath(jejuPath, fill)
        for (p in islandPaths) c.drawPath(p, fill)

        line.color = Color.argb(78, 62, 44, 28)
        line.strokeWidth = 2.6f / unit
        c.drawPath(southPath, line)
        line.strokeWidth = 1.7f / unit
        c.drawPath(jejuPath, line)
        for (p in islandPaths) c.drawPath(p, line)

        line.color = 0xFF36261C.toInt()
        line.strokeWidth = 1.2f / unit
        c.drawPath(southPath, line)
        line.strokeWidth = 1.0f / unit
        c.drawPath(jejuPath, line)
        for (p in islandPaths) c.drawPath(p, line)

        line.color = Color.argb(150, 128, 112, 86)
        line.strokeWidth = 0.85f / unit
        c.drawPath(northPath, line)

        if (detail >= 1) {
            line.color = Color.argb(70, 86, 138, 164)
            line.strokeWidth = 2.4f / unit
            for (p in riverPaths) c.drawPath(p, line)
            line.color = Color.argb(225, 58, 104, 128)
            line.strokeWidth = 1.05f / unit
            for (p in riverPaths) c.drawPath(p, line)
        }

        if (detail >= 2) {
            fill.color = 0xFF6E5A3A.toInt()
            val s = 6f / unit
            for (pt in peakPoints) {
                val path = Path()
                path.moveTo(pt[0], pt[1] - s)
                path.lineTo(pt[0] + s * 0.85f, pt[1] + s * 0.6f)
                path.lineTo(pt[0] - s * 0.85f, pt[1] + s * 0.6f)
                path.close()
                c.drawPath(path, fill)
            }
            line.color = 0xFFB07070.toInt()
            line.strokeWidth = 1.2f / unit
            line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f / unit, 4f / unit), 0f)
            c.drawPath(dmzPath, line)
            line.pathEffect = null
        }
    }
}
