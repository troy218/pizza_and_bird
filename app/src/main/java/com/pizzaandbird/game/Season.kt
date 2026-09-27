package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/**
 * 계절 (v0.5 — 계획 #3 "계절 & 날씨").
 *
 * 게임 내 날짜 [GameState.day] 로 정해진다. 한 계절 = [DAYS_PER_SEASON]일, 1일째는 봄.
 * 계절은 ① 날씨 확률(여름 장마·겨울 눈) ② 철새 출현 가중치(겨울 두루미·여름 제비)
 * ③ 화면 연출(꽃잎·낙엽·색감)에 영향을 준다.
 */
enum class Season(val id: String, val icon: String, val label: String, val description: String) {
    SPRING("spring", "sparkle", "봄", "벚꽃이 흩날리고 여름 철새가 돌아와요"),
    SUMMER("summer", "leaf", "여름", "매미가 울고 장마가 지나가요"),
    AUTUMN("autumn", "leaf", "가을", "단풍이 지고 고추잠자리가 날아요"),
    WINTER("winter", "snow", "겨울", "눈이 쌓이고 두루미가 찾아와요");

    companion object {
        const val DAYS_PER_SEASON = 7
        fun forDay(day: Int): Season = values()[((day - 1).coerceAtLeast(0) / DAYS_PER_SEASON) % 4]
        fun dayInSeason(day: Int): Int = (day - 1).coerceAtLeast(0) % DAYS_PER_SEASON + 1
    }
}

fun GameState.season(): Season = Season.forDay(day)
fun GameState.seasonLabel(): String = "${season().label} ${Season.dayInSeason(day)}일째"

/**
 * 벚꽃은 상시 날씨가 아니라 잠깐 지나가는 꽃바람이다.
 * 봄 3~5일째, 게임 시각 09~10시 / 14~15시에만 보인다 (한 번에 실제 약 12.5초).
 * 밤·비·눈에는 없고, 시작과 끝은 약 1.9초 동안 부드럽게 나타나고 사라진다.
 * 날짜/시계만으로 정하므로 씬 이동·저장 복귀로 연출이 다시 시작되지 않는다.
 */
fun cherryBlossomIntensity(day: Int, hour: Float, weather: Weather): Float {
    val season = Season.forDay(day)
    if (season != Season.SPRING || Season.dayInSeason(day) !in 3..5) return 0f
    if (weather == Weather.RAIN || weather == Weather.SNOW) return 0f
    if (DayCycle.sunAltitude(hour, season) <= 0f) return 0f
    val elapsed = when {
        hour >= 9f && hour < 10f -> hour - 9f
        hour >= 14f && hour < 15f -> hour - 14f
        else -> return 0f
    }
    val fadeHours = 0.15f
    return minOf(elapsed / fadeHours, (1f - elapsed) / fadeHours, 1f).coerceIn(0f, 1f)
}

fun GameState.cherryBlossomIntensity(): Float = cherryBlossomIntensity(day, worldTime, weather())

/** 계절별 날씨 확률표 (맑음, 흐림, 비, 강풍, 눈). 합은 1. */
fun seasonWeatherTable(season: Season): List<Pair<Weather, Float>> = when (season) {
    Season.SPRING -> listOf(Weather.SUNNY to 0.42f, Weather.CLOUDY to 0.22f, Weather.RAIN to 0.18f, Weather.WIND to 0.17f, Weather.SNOW to 0.01f)
    Season.SUMMER -> listOf(Weather.SUNNY to 0.30f, Weather.CLOUDY to 0.22f, Weather.RAIN to 0.40f, Weather.WIND to 0.08f, Weather.SNOW to 0f)
    Season.AUTUMN -> listOf(Weather.SUNNY to 0.46f, Weather.CLOUDY to 0.22f, Weather.RAIN to 0.12f, Weather.WIND to 0.18f, Weather.SNOW to 0.02f)
    Season.WINTER -> listOf(Weather.SUNNY to 0.30f, Weather.CLOUDY to 0.24f, Weather.RAIN to 0.04f, Weather.WIND to 0.14f, Weather.SNOW to 0.28f)
}

fun rollSeasonWeather(season: Season, rnd: Random): Weather {
    var r = rnd.nextFloat()
    val table = seasonWeatherTable(season)
    for ((w, p) in table) { r -= p; if (r <= 0f) return w }
    return table.first().first
}

/** 철새 구분 — 공식 목록엔 도래 시기가 없어 과/이름으로 추정한다. */
enum class Residency { RESIDENT, WINTER, SUMMER, PASSAGE }

private val WINTER_FAMILIES = setOf("두루미과", "오리과", "아비과", "논병아리과", "바다오리과", "되새과", "여새과", "멧새과")
private val SUMMER_FAMILIES = setOf("제비과", "꾀꼬리과", "두견이과", "파랑새과", "물총새과", "팔색조과", "솔딱새과", "휘파람새과", "개개비과", "때까치과", "쏙독새과", "칼새과", "긴꼬리딱새과", "백로과")
private val PASSAGE_FAMILIES = setOf("도요과", "물떼새과", "검은머리물떼새과", "장다리물떼새과")
private val RESIDENT_NAMES = setOf("원앙", "청둥오리", "흰뺨검둥오리", "흰배지빠귀", "노랑턱멧새", "왜가리", "해오라기", "쇠백로", "물총새", "때까치", "직박구리", "참새", "까치", "멧비둘기", "논병아리", "검은머리물떼새")
private val WINTER_NAMES = listOf("두루미", "고니", "기러기", "독수리", "흰꼬리수리", "참수리", "말똥가리", "황새", "재갈매기", "큰부리까마귀떼", "떼까마귀", "되새", "쑥새", "흰죽지", "댕기흰죽지", "노랑지빠귀", "개똥지빠귀", "상모솔새", "홍여새", "황여새")
private val SUMMER_NAMES = listOf("제비", "꾀꼬리", "뻐꾸기", "두견이", "파랑새", "호반새", "팔색조", "소쩍새", "쏙독새", "중대백로", "황로", "청호반새", "긴꼬리딱새", "흰눈썹황금새", "큰유리새", "되지빠귀", "저어새", "뜸부기", "물닭", "흰목물떼새", "꼬마물떼새", "흰물떼새")

private val residencyCache = HashMap<String, Residency>()

fun residencyOf(def: BirdDef): Residency = residencyCache.getOrPut(def.id) {
    val n = def.name
    when {
        n in RESIDENT_NAMES -> Residency.RESIDENT
        WINTER_NAMES.any { n.contains(it) } -> Residency.WINTER
        SUMMER_NAMES.any { n.contains(it) } -> Residency.SUMMER
        def.familyName in PASSAGE_FAMILIES -> Residency.PASSAGE
        def.familyName in WINTER_FAMILIES -> Residency.WINTER
        def.familyName in SUMMER_FAMILIES -> Residency.SUMMER
        else -> Residency.RESIDENT
    }
}

val Residency.label: String
    get() = when (this) {
        Residency.RESIDENT -> "텃새"
        Residency.WINTER -> "겨울 철새"
        Residency.SUMMER -> "여름 철새"
        Residency.PASSAGE -> "나그네새"
    }

/** 계절 × 철새 구분에 따른 출현 배율. 제철이 아니면 아주 드물게(0.08배)만 나온다. */
fun seasonBirdMultiplier(def: BirdDef, season: Season, weather: Weather): Double {
    val base = when (residencyOf(def)) {
        Residency.RESIDENT -> 1.0
        Residency.WINTER -> when (season) { Season.WINTER -> 2.2; Season.AUTUMN -> 0.8; Season.SPRING -> 0.35; Season.SUMMER -> 0.08 }
        Residency.SUMMER -> when (season) { Season.SUMMER -> 2.0; Season.SPRING -> 1.2; Season.AUTUMN -> 0.4; Season.WINTER -> 0.08 }
        Residency.PASSAGE -> when (season) { Season.SPRING, Season.AUTUMN -> 2.0; else -> 0.35 }
    }
    // 겨울 두루미: 눈 내리는 겨울 들녘에서 특히 잘 보인다
    val crane = def.familyName == "두루미과" || def.name.contains("두루미")
    val craneBonus = if (crane && season == Season.WINTER) (if (weather == Weather.SNOW) 2.0 else 1.4) else 1.0
    return base * craneBonus
}

/**
 * 계절별 풀잎 MULTIPLY 틴트 — 지역 틴트와 곱해서 쓴다 (흰색 = 변화 없음).
 * 봄 연두 새잎 · 여름 원색 · 가을 누렇게 익은 풀 · 겨울 차갑게 바랜 풀.
 */
fun seasonFoliageTint(season: Season): Int = when (season) {
    Season.SPRING -> 0xFFE6FFE0.toInt()
    Season.SUMMER -> 0xFFFFFFFF.toInt()
    Season.AUTUMN -> 0xFFFFDF9E.toInt()
    Season.WINTER -> 0xFFD6E2EC.toInt()
}

/** 계절별 물 MULTIPLY 틴트 — 겨울엔 얼음빛, 가을엔 낙엽물빛. */
fun seasonWaterTint(season: Season): Int = when (season) {
    Season.WINTER -> 0xFFCFE6F5.toInt()
    Season.AUTUMN -> 0xFFF2E4C8.toInt()
    else -> 0xFFFFFFFF.toInt()
}

/** MULTIPLY 틴트 두 장을 하나로 합친다 (채널별 곱). */
fun multiplyTint(a: Int, b: Int): Int = Color.rgb(
    Color.red(a) * Color.red(b) / 255,
    Color.green(a) * Color.green(b) / 255,
    Color.blue(a) * Color.blue(b) / 255
)

/**
 * 계절 화면 연출 — 스크린 좌표로 그린다.
 * 봄 꽃바람 · 여름 빛 알갱이(낮) · 가을 단풍잎 · 겨울 가루눈 반짝임.
 * 봄 꽃잎은 [cherryBlossomIntensity]로 제한하고, 다른 계절의 입자는 기존 날씨별 양을 유지한다.
 */
class SeasonFx {
    private class Flake(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float,
                        var size: Float, var color: Int, var phase: Float, var shape: Int, var alpha: Int)

    companion object {
        const val S_PETAL = 0   // 봄 벚꽃잎
        const val S_LEAF = 1    // 가을 단풍잎
        const val S_MOTE = 2    // 여름 빛 알갱이
        const val S_GLINT = 3   // 겨울 눈 반짝임
    }

    private val flakes = ArrayList<Flake>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val tint = Paint()
    private val rnd = Random(7)
    private var t = 0f

    fun update(
        dt: Float, season: Season, weather: Weather, w: Float, h: Float,
        night: Boolean = false, blossomIntensity: Float = 0f
    ) {
        t += dt
        val stormy = weather == Weather.RAIN || weather == Weather.SNOW
        val windy = weather == Weather.WIND
        val petals = if (night || stormy) 0f else blossomIntensity.coerceIn(0f, 1f)
        val (want, shape) = when (season) {
            Season.SPRING -> (if (petals > 0f) 30 else 0) to S_PETAL
            Season.AUTUMN -> (if (stormy) 10 else 26) to S_LEAF
            Season.SUMMER -> when {
                night -> 5 to S_MOTE
                weather == Weather.SUNNY -> 14 to S_MOTE
                stormy -> 4 to S_MOTE
                else -> 9 to S_MOTE
            }
            Season.WINTER -> (if (weather == Weather.SNOW) 8 else 22) to S_GLINT
        }
        // 시간대·날씨가 바뀌면 이미 떠 있는 꽃잎도 즉시 정리한다.
        if (want == 0) {
            flakes.clear()
            return
        }
        val windK = if (windy) 2.6f else 1f
        while (flakes.size < want) flakes.add(spawn(shape, w, h, rnd.nextFloat() * h))
        val it = flakes.iterator()
        var alive = 0
        while (it.hasNext()) {
            val f = it.next()
            // 계절이 바뀌면 남은 입자는 새 계절 입자로 교체한다
            if (f.shape != shape) {
                val n = spawn(shape, w, h, rnd.nextFloat() * h)
                f.x = n.x; f.y = n.y; f.vx = n.vx; f.vy = n.vy
                f.color = n.color; f.size = n.size; f.shape = n.shape; f.alpha = n.alpha
            }
            f.phase += dt
            when (f.shape) {
                S_PETAL -> {
                    f.x += (f.vx * windK + sin(f.phase * 1.7f) * 16f) * dt
                    f.y += f.vy * dt
                    f.rot += dt * 120f * windK
                }
                S_LEAF -> {
                    f.x += (f.vx * windK + sin(f.phase * 2.3f) * 26f) * dt
                    f.y += f.vy * dt
                    f.rot += dt * 200f * windK
                }
                S_MOTE -> {
                    f.x += (sin(f.phase * 0.9f) * 12f + 4f) * dt
                    f.y += f.vy * dt
                    f.alpha = (120 + 110 * sin(f.phase * 2.2f)).toInt().coerceIn(30, 230)
                    if (f.y < -10f) f.y = h + 8f
                    if (f.y > h + 10f) f.y = -8f
                }
                else -> { // S_GLINT — 천천히 흩날리며 반짝인다
                    f.x += (f.vx * 0.5f + sin(f.phase * 1.3f) * 12f) * dt
                    f.y += f.vy * dt
                    f.alpha = (140 + 100 * sin(f.phase * 3.1f)).toInt().coerceIn(40, 240)
                }
            }
            if (f.shape != S_MOTE && (f.y > h + 10f || f.x > w + 20f || f.x < -20f)) {
                if (alive < want) {
                    val n = spawn(shape, w, h, -8f)
                    f.x = n.x; f.y = n.y; f.color = n.color; f.size = n.size; f.alpha = n.alpha
                } else { it.remove(); continue }
            }
            if (f.shape == S_PETAL) f.alpha = (235 * petals).toInt()
            alive++
        }
        // 계절이 바뀌어 목표보다 많으면 초과분부터 정리
        while (flakes.size > want) flakes.removeAt(flakes.size - 1)
    }

    private fun spawn(shape: Int, w: Float, h: Float, y: Float): Flake {
        val x = rnd.nextFloat() * w * 1.1f - w * 0.1f
        return when (shape) {
            S_PETAL -> {
                val color = listOf(0xFFFFD1DC.toInt(), 0xFFFFC2D4.toInt(), 0xFFFBE3EA.toInt(), 0xFFFFF2F5.toInt())[rnd.nextInt(4)]
                Flake(x, y, 10f + rnd.nextFloat() * 18f, 15f + rnd.nextFloat() * 20f,
                    rnd.nextFloat() * 360f, 2.4f + rnd.nextFloat() * 1.8f, color, rnd.nextFloat() * 6f, shape, 235)
            }
            S_LEAF -> {
                val color = listOf(0xFFD94F3D.toInt(), 0xFFE8823C.toInt(), 0xFFF2C14E.toInt(), 0xFFB23F44.toInt())[rnd.nextInt(4)]
                Flake(x, y, 8f + rnd.nextFloat() * 14f, 24f + rnd.nextFloat() * 24f,
                    rnd.nextFloat() * 360f, 3f + rnd.nextFloat() * 2.2f, color, rnd.nextFloat() * 6f, shape, 245)
            }
            S_MOTE -> {
                Flake(rnd.nextFloat() * w, y, 0f, -3f - rnd.nextFloat() * 5f,
                    0f, 1.4f + rnd.nextFloat() * 1.4f, 0xFFFFF6C8.toInt(), rnd.nextFloat() * 6f, shape, 170)
            }
            else -> { // S_GLINT
                val color = if (rnd.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFFDFF0FF.toInt()
                Flake(x, y, 6f + rnd.nextFloat() * 10f, 10f + rnd.nextFloat() * 14f,
                    0f, 1.3f + rnd.nextFloat() * 1.3f, color, rnd.nextFloat() * 6f, shape, 190)
            }
        }
    }

    private fun withAlpha(color: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun draw(c: Canvas, season: Season, w: Float, h: Float) {
        // 은은한 계절 색감
        val col = when (season) {
            Season.SPRING -> Color.argb(12, 255, 190, 210)
            Season.SUMMER -> Color.argb(10, 255, 230, 140)
            Season.AUTUMN -> Color.argb(18, 230, 140, 50)
            Season.WINTER -> Color.argb(20, 190, 215, 255)
        }
        tint.color = col
        c.drawRect(0f, 0f, w, h, tint)
        for (f in flakes) {
            when (f.shape) {
                S_PETAL -> {
                    paint.color = withAlpha(f.color, f.alpha)
                    c.save()
                    c.rotate(f.rot, f.x, f.y)
                    c.drawOval(f.x - f.size, f.y - f.size * 0.55f, f.x + f.size, f.y + f.size * 0.55f, paint)
                    // 꽃잎 끝의 진한 점
                    veinPaint.color = withAlpha(0xFFE87F9A.toInt(), (f.alpha * 0.7f).toInt())
                    c.drawCircle(f.x + f.size * 0.45f, f.y, f.size * 0.22f, veinPaint)
                    c.restore()
                }
                S_LEAF -> {
                    paint.color = withAlpha(f.color, f.alpha)
                    c.save()
                    c.rotate(f.rot, f.x, f.y)
                    // 단풍잎 — 마름모 몸통 + 잎자루
                    val s = f.size
                    c.drawRect(f.x - s * 0.7f, f.y - s * 0.7f, f.x + s * 0.7f, f.y + s * 0.7f, paint)
                    c.save()
                    c.rotate(45f, f.x, f.y)
                    c.drawRect(f.x - s * 0.45f, f.y - s * 0.45f, f.x + s * 0.45f, f.y + s * 0.45f, paint)
                    c.restore()
                    veinPaint.color = withAlpha(0xFF8A4A2B.toInt(), f.alpha)
                    c.drawLine(f.x, f.y + s * 0.7f, f.x, f.y + s * 1.3f, veinPaint)
                    c.restore()
                }
                S_MOTE -> {
                    paint.color = withAlpha(f.color, (f.alpha * 0.35f).toInt())
                    c.drawCircle(f.x, f.y, f.size * 2.4f, paint)
                    paint.color = withAlpha(f.color, f.alpha)
                    c.drawCircle(f.x, f.y, f.size * 0.8f, paint)
                }
                else -> { // S_GLINT — 십자 반짝임
                    paint.color = withAlpha(f.color, f.alpha)
                    val s = f.size
                    c.drawRect(f.x - s * 1.6f, f.y - s * 0.28f, f.x + s * 1.6f, f.y + s * 0.28f, paint)
                    c.drawRect(f.x - s * 0.28f, f.y - s * 1.6f, f.x + s * 0.28f, f.y + s * 1.6f, paint)
                }
            }
        }
    }
}
