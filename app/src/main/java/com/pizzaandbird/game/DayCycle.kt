package com.pizzaandbird.game

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 하루의 빛 — 실시간 색 엔진.
 *
 * 예전에는 "새벽 / 낮 / 노을 / 밤" 네 덩어리를 if 로 끊어 썼다. 경계에서 색이 툭 바뀌고,
 * 계절이 바뀌어도 해는 늘 같은 시각에 떴다. 여기서는 하루를 하나의 연속 함수로 본다.
 *
 *   1) 계절마다 일출·일몰 시각이 달라진다 (여름은 길고 겨울은 짧다).
 *   2) 실제 시각을 '태양 시각(solar hour)' 으로 옮긴다 — 일출=6시, 정오=12시, 일몰=18시.
 *   3) 태양 고도를 부드러운 사인 곡선으로 구하고,
 *   4) 고도/태양 시각을 키프레임 색표에 통과시켜 매 프레임 색을 보간한다.
 *
 * 보간은 smoothstep + 감마 보정(선형광 공간)으로 한다. 덕분에 어두워질 때 회색으로
 * 가라앉지 않고, 남색 → 주황 → 하늘색이 자연스럽게 흐른다.
 *
 * 쓰는 곳
 *   - WorldScene  : 화면 전체 앰비언트, 그림자 방향·길이, 노을 빛줄기, 조명 점등 세기
 *   - HomeScene   : 창밖 하늘색, 실내 조도
 *   - Atmosphere  : 후처리 색보정 워시
 *   - GameState   : darkness() (사진 노출·연출 강도)
 *
 * [season] 은 씬이 매 프레임 동기화한다(기본 봄). 전역 하나로 두어 daylight(hour) 같은
 * 기존 시그니처를 그대로 유지한다.
 */
object DayCycle {

    /** 현재 계절 — WorldScene/HomeScene 이 매 프레임 갱신한다. */
    @JvmStatic
    var season: Season = Season.SPRING

    // -----------------------------------------------------------------
    // 태양 궤도
    // -----------------------------------------------------------------

    /** 계절별 일출 시각 */
    fun sunrise(s: Season = season): Float = when (s) {
        Season.SPRING -> 6.0f
        Season.SUMMER -> 5.2f
        Season.AUTUMN -> 6.4f
        Season.WINTER -> 7.4f
    }

    /** 계절별 일몰 시각 */
    fun sunset(s: Season = season): Float = when (s) {
        Season.SPRING -> 18.6f
        Season.SUMMER -> 19.8f
        Season.AUTUMN -> 18.0f
        Season.WINTER -> 17.2f
    }

    /** 남중(정오) 시각 */
    fun solarNoon(s: Season = season): Float = (sunrise(s) + sunset(s)) * 0.5f

    /**
     * 실제 시각 → 태양 시각(0..24). 일출=6, 남중=12, 일몰=18 로 정규화한다.
     * 색표를 한 벌만 두고 모든 계절에 쓰기 위한 변환.
     */
    fun solarHour(hour: Float, s: Season = season): Float {
        val h = wrap24(hour)
        val sr = sunrise(s)
        val ss = sunset(s)
        return when {
            h < sr -> {
                // 자정~일출 : 18..24(+6) 구간을 밤 길이에 맞춰 늘린다
                val night = 24f - ss + sr
                val k = (h + 24f - ss) / night
                wrap24(18f + k * 12f)
            }
            h < ss -> {
                val k = (h - sr) / (ss - sr)
                6f + k * 12f
            }
            else -> {
                val night = 24f - ss + sr
                val k = (h - ss) / night
                18f + k * 12f
            }
        }
    }

    /** 태양 고도 -1..1 (0 = 지평선, 1 = 남중). 완전히 연속. */
    fun sunAltitude(hour: Float, s: Season = season): Float {
        val sh = solarHour(hour, s)
        val a = sin((sh - 6f) / 12f * Math.PI).toFloat()
        // 계절에 따라 한낮 고도(=빛의 세기)도 달라진다
        val peak = when (s) {
            Season.SUMMER -> 1f
            Season.SPRING, Season.AUTUMN -> 0.88f
            Season.WINTER -> 0.68f
        }
        return if (a >= 0f) a * peak else a
    }

    /** 밤 경계 고도 — 이 아래면 밤(올빼미 등 밤새 출현)이다 */
    const val NIGHT_ALTITUDE = -0.12f

    /** 밤 여부 — 출현·HUD·대사·업적 집계가 함께 쓰는 단일 판정 */
    fun isNightAt(hour: Float, s: Season = season): Boolean = sunAltitude(hour, s) < NIGHT_ALTITUDE

    /** 햇빛 세기 0..1 — 지평선 부근에서 부드럽게 오르내린다. */
    fun daylight(hour: Float, s: Season = season): Float {
        val alt = sunAltitude(hour, s)
        return smooth01((alt + 0.10f) / 0.42f)
    }

    /** 어둠 0..1 (날씨 제외 순수 시간 성분) */
    fun darkness(hour: Float, s: Season = season): Float {
        val alt = sunAltitude(hour, s)
        return 1f - smooth01((alt + 0.22f) / 0.5f)
    }

    /** 황금시간(일출·일몰 직전후) 0..1 */
    fun golden(hour: Float, s: Season = season): Float {
        val alt = sunAltitude(hour, s)
        if (alt < -0.16f || alt > 0.34f) return 0f
        // 고도 0.09 부근에서 최대
        return smooth01(1f - abs(alt - 0.09f) / 0.25f)
    }

    /** 블루아워(해가 지평선 아래 살짝) 0..1 */
    fun blueHour(hour: Float, s: Season = season): Float {
        val alt = sunAltitude(hour, s)
        if (alt > 0.02f || alt < -0.36f) return 0f
        return smooth01(1f - abs(alt + 0.17f) / 0.19f)
    }

    /** 별빛 세기 0..1 — 해가 충분히 내려가야 보인다. */
    fun starlight(hour: Float, s: Season = season): Float =
        smooth01((-sunAltitude(hour, s) - 0.14f) / 0.3f)

    /** 아침이면 -1(서쪽으로 길게), 저녁이면 +1(동쪽으로 길게) */
    fun sunDirX(hour: Float, s: Season = season): Float {
        val sh = solarHour(hour, s)
        return (-cos((sh - 6f) / 12f * Math.PI).toFloat()).coerceIn(-1.1f, 1.1f)
    }

    /** 그림자 길이 배수 — 해가 낮을수록 길다 */
    fun shadowStretch(hour: Float, s: Season = season): Float {
        val alt = sunAltitude(hour, s).coerceAtLeast(0.05f)
        return (0.55f / alt).coerceIn(0.6f, 3.2f)
    }

    /** 해가 지평선을 막 넘는 순간인지 (동틀 녘 연출 트리거용) */
    fun isSunriseMoment(hour: Float, s: Season = season): Boolean {
        val sh = solarHour(hour, s)
        return sh in 5.7f..6.5f
    }

    /** 시간대 이름 — HUD/일지용 */
    fun phaseLabel(hour: Float, s: Season = season): String {
        val sh = solarHour(hour, s)
        return when {
            sh < 4.6f -> "한밤"
            sh < 5.6f -> "여명"
            sh < 6.6f -> "동틀 녘"
            sh < 8.4f -> "아침"
            sh < 11f -> "오전"
            sh < 13.4f -> "한낮"
            sh < 16f -> "오후"
            sh < 17.4f -> "해질 무렵"
            sh < 18.6f -> "노을"
            sh < 19.8f -> "땅거미"
            else -> "밤"
        }
    }

    fun phaseEmoji(hour: Float, s: Season = season): String {
        val sh = solarHour(hour, s)
        return when {
            sh < 5.4f -> "🌙"
            sh < 6.8f -> "🌅"
            sh < 16.4f -> "☀"
            sh < 19.2f -> "🌇"
            else -> "🌙"
        }
    }

    // -----------------------------------------------------------------
    // 색표 (태양 시각 기준 키프레임)
    // -----------------------------------------------------------------

    /** 화면 전체에 얹는 앰비언트 오버레이 (ARGB, 알파 0 = 손대지 않음) */
    private val AMBIENT = ramp(
        0.0f to Color.argb(150, 12, 16, 48),
        3.2f to Color.argb(152, 10, 14, 46),
        4.6f to Color.argb(138, 22, 30, 78),
        5.4f to Color.argb(112, 44, 58, 122),      // 여명 — 남색이 옅어진다
        5.9f to Color.argb(92, 108, 84, 150),      // 보랏빛 박명
        6.2f to Color.argb(74, 214, 122, 108),     // 해가 지평선에 닿는 순간
        6.8f to Color.argb(52, 255, 172, 104),     // 동틀 녘 금빛
        7.8f to Color.argb(26, 255, 214, 160),
        9.5f to Color.argb(10, 255, 240, 214),
        12.0f to Color.argb(0, 255, 255, 255),     // 한낮 — 원색 그대로
        14.5f to Color.argb(8, 255, 242, 216),
        16.2f to Color.argb(24, 255, 214, 158),
        17.2f to Color.argb(46, 255, 168, 96),     // 오후 금빛
        17.9f to Color.argb(66, 250, 122, 70),     // 노을
        18.4f to Color.argb(82, 206, 88, 84),
        18.9f to Color.argb(96, 120, 70, 128),     // 자줏빛 땅거미
        19.6f to Color.argb(116, 48, 56, 118),     // 블루아워
        20.8f to Color.argb(138, 22, 28, 74),
        22.5f to Color.argb(150, 12, 16, 48),
        24.0f to Color.argb(150, 12, 16, 48)
    )

    /** 하늘색 (창밖·배경) */
    private val SKY = ramp(
        0.0f to 0xFF0E1436.toInt(),
        3.2f to 0xFF0C1232.toInt(),
        4.6f to 0xFF1B2555.toInt(),
        5.4f to 0xFF35427E.toInt(),
        5.9f to 0xFF6E5A93.toInt(),
        6.2f to 0xFFC87A79.toInt(),
        6.6f to 0xFFF2A07A.toInt(),      // 동틀 녘 살구빛
        7.4f to 0xFFF0C79A.toInt(),
        8.6f to 0xFFBFDCEE.toInt(),
        12.0f to 0xFF9FD4F0.toInt(),     // 한낮 하늘
        15.5f to 0xFFA6D6EC.toInt(),
        16.8f to 0xFFD9CFA8.toInt(),
        17.6f to 0xFFF0A86A.toInt(),
        18.2f to 0xFFF0784E.toInt(),     // 노을
        18.7f to 0xFFB85A6E.toInt(),
        19.3f to 0xFF5B4A84.toInt(),
        20.0f to 0xFF2A3164.toInt(),
        21.5f to 0xFF141A44.toInt(),
        24.0f to 0xFF0E1436.toInt()
    )

    /** 하늘 위쪽(천정) 색 — 아래(지평선)보다 늘 짙다 */
    private val SKY_TOP = ramp(
        0.0f to 0xFF070B22.toInt(),
        4.6f to 0xFF101838.toInt(),
        5.6f to 0xFF283566.toInt(),
        6.4f to 0xFF6E86B4.toInt(),
        8.0f to 0xFF88BEE2.toInt(),
        12.0f to 0xFF6FB8E8.toInt(),
        16.5f to 0xFF7FB6DE.toInt(),
        17.8f to 0xFFA87FA8.toInt(),
        18.6f to 0xFF6B4C86.toInt(),
        19.6f to 0xFF23295C.toInt(),
        21.5f to 0xFF0A1028.toInt(),
        24.0f to 0xFF070B22.toInt()
    )

    /** 햇빛(직사광) 색 — 창살 햇살·역광에 곱한다 */
    private val SUNLIGHT = ramp(
        0.0f to 0xFF8FA0D8.toInt(),
        5.6f to 0xFFB49AC0.toInt(),
        6.3f to 0xFFFFA36A.toInt(),
        7.2f to 0xFFFFC488.toInt(),
        9.0f to 0xFFFFE6C0.toInt(),
        12.0f to 0xFFFFF8E8.toInt(),
        15.5f to 0xFFFFEEC8.toInt(),
        17.2f to 0xFFFFC078.toInt(),
        18.2f to 0xFFFF8A4E.toInt(),
        19.2f to 0xFFB0709A.toInt(),
        20.5f to 0xFF8090D0.toInt(),
        24.0f to 0xFF8FA0D8.toInt()
    )

    /** 후처리 워시 색(저채도) — Atmosphere 가 쓴다 */
    private val WASH = ramp(
        0.0f to Color.argb(16, 90, 118, 200),
        5.0f to Color.argb(14, 110, 130, 210),
        6.0f to Color.argb(16, 214, 150, 170),
        6.6f to Color.argb(20, 255, 184, 106),
        8.5f to Color.argb(8, 255, 232, 196),
        12.0f to Color.argb(3, 255, 246, 224),
        16.0f to Color.argb(7, 255, 236, 200),
        17.6f to Color.argb(18, 255, 150, 96),
        18.4f to Color.argb(21, 255, 118, 92),
        19.4f to Color.argb(17, 168, 118, 180),
        20.6f to Color.argb(16, 96, 116, 196),
        24.0f to Color.argb(16, 90, 118, 200)
    )

    /** 시간에 따른 앰비언트(날씨 반영). 날씨는 채도를 낮추고 한 톤 더 어둡게 만든다. */
    fun ambient(hour: Float, weather: Weather = Weather.SUNNY, s: Season = season): Int {
        val base = AMBIENT.at(solarHour(hour, s))
        val (wr, wg, wb, wa) = weatherDim(weather)
        if (wa <= 0f) return base
        val a = (Color.alpha(base) + wa * 255f).toInt().coerceIn(0, 235)
        val k = wa * 1.6f
        return Color.argb(
            a,
            mixTo(Color.red(base), wr, k),
            mixTo(Color.green(base), wg, k),
            mixTo(Color.blue(base), wb, k)
        )
    }

    /** 앰비언트 진하기 0..1 — 가로등·창문 점등 세기에 그대로 쓴다. */
    fun lightK(hour: Float, weather: Weather = Weather.SUNNY, s: Season = season): Float =
        (Color.alpha(ambient(hour, weather, s)) / 130f).coerceIn(0f, 1f)

    /** 지평선 하늘색 */
    fun skyColor(hour: Float, s: Season = season): Int = SKY.at(solarHour(hour, s))

    /** 천정 하늘색 */
    fun skyTopColor(hour: Float, s: Season = season): Int = SKY_TOP.at(solarHour(hour, s))

    /** 직사광 색 */
    fun sunlightColor(hour: Float, s: Season = season): Int = SUNLIGHT.at(solarHour(hour, s))

    /** 후처리 워시 */
    fun washColor(hour: Float, weather: Weather = Weather.SUNNY, s: Season = season): Int {
        val base = WASH.at(solarHour(hour, s))
        val (wr, wg, wb, wa) = weatherDim(weather)
        if (wa <= 0f) return base
        return lerpColor(base, Color.argb((10 + wa * 26f).toInt(), wr, wg, wb), (wa * 2.2f).coerceIn(0f, 0.9f))
    }

    /** 날씨별 (r, g, b, 세기) — 비는 청회색, 눈은 차가운 흰빛 */
    private fun weatherDim(w: Weather): Quad = when (w) {
        Weather.RAIN -> Quad(58, 78, 108, 0.22f)
        Weather.SNOW -> Quad(120, 146, 176, 0.14f)
        Weather.CLOUDY -> Quad(96, 108, 128, 0.10f)
        Weather.WIND -> Quad(120, 132, 148, 0.04f)
        else -> Quad(0, 0, 0, 0f)
    }

    private data class Quad(val r: Int, val g: Int, val b: Int, val a: Float)

    private fun mixTo(v: Int, target: Int, k: Float): Int =
        (v + (target - v) * k.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)

    // -----------------------------------------------------------------
    // 보간 유틸
    // -----------------------------------------------------------------

    /** 태양 시각(0..24)을 키로 하는 순환 색 램프 */
    class ColorRamp(pairs: Array<out Pair<Float, Int>>) {
        private val keys = FloatArray(pairs.size) { pairs[it].first }
        private val cols = IntArray(pairs.size) { pairs[it].second }

        fun at(hourIn: Float): Int {
            val h = wrap24(hourIn)
            var i = 0
            while (i < keys.size - 1 && keys[i + 1] < h) i++
            val a = keys[i]
            val b = if (i + 1 < keys.size) keys[i + 1] else 24f
            val ca = cols[i]
            val cb = if (i + 1 < cols.size) cols[i + 1] else cols[0]
            val span = (b - a)
            val t = if (span <= 0.0001f) 0f else smooth01((h - a) / span)
            return lerpColor(ca, cb, t)
        }
    }

    private fun ramp(vararg pairs: Pair<Float, Int>) = ColorRamp(pairs)

    /** 감마 보정 색 보간 — 어두워질 때 탁해지지 않는다 */
    fun lerpColor(c0: Int, c1: Int, tIn: Float): Int {
        val t = tIn.coerceIn(0f, 1f)
        if (t <= 0f) return c0
        if (t >= 1f) return c1
        return Color.argb(
            (Color.alpha(c0) + (Color.alpha(c1) - Color.alpha(c0)) * t).toInt().coerceIn(0, 255),
            gLerp(Color.red(c0), Color.red(c1), t),
            gLerp(Color.green(c0), Color.green(c1), t),
            gLerp(Color.blue(c0), Color.blue(c1), t)
        )
    }

    private fun gLerp(a: Int, b: Int, t: Float): Int {
        val la = (a / 255f).pow(2.2f)
        val lb = (b / 255f).pow(2.2f)
        val l = la + (lb - la) * t
        return (l.pow(1f / 2.2f) * 255f).toInt().coerceIn(0, 255)
    }

    /** 0..1 클램프 + 스무스스텝 */
    fun smooth01(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun wrap24(h: Float): Float {
        var x = h % 24f
        if (x < 0f) x += 24f
        return x
    }
}
