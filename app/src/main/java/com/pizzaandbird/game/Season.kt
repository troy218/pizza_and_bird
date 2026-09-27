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
    SPRING("spring", "sparkle", "봄", "여름 철새가 돌아오고 나그네새가 지나가요"),
    SUMMER("summer", "leaf", "여름", "장마철 — 비가 잦고 여름 철새가 번식해요"),
    AUTUMN("autumn", "leaf", "가을", "도요·물떼새와 기러기가 남쪽으로 이동해요"),
    WINTER("winter", "snow", "겨울", "두루미·고니·독수리 같은 겨울 손님이 찾아와요");

    companion object {
        const val DAYS_PER_SEASON = 7
        fun forDay(day: Int): Season = values()[((day - 1).coerceAtLeast(0) / DAYS_PER_SEASON) % 4]
        fun dayInSeason(day: Int): Int = (day - 1).coerceAtLeast(0) % DAYS_PER_SEASON + 1
    }
}

fun GameState.season(): Season = Season.forDay(day)
fun GameState.seasonLabel(): String = "${season().label} ${Season.dayInSeason(day)}일째"

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
private val RESIDENT_NAMES = setOf("원앙", "청둥오리", "흰뺨검둥오리", "흰배지빠귀", "노랑턱멧새", "왜가리", "해오라기", "쇠백로", "물총새", "때까치", "직박구리", "참새", "까치", "멧비둘기")
private val WINTER_NAMES = listOf("두루미", "고니", "기러기", "독수리", "흰꼬리수리", "참수리", "말똥가리", "황새", "재갈매기", "큰부리까마귀떼", "떼까마귀", "되새", "쑥새", "흰죽지", "댕기흰죽지", "노랑지빠귀", "개똥지빠귀", "상모솔새", "홍여새", "황여새")
private val SUMMER_NAMES = listOf("제비", "꾀꼬리", "뻐꾸기", "두견이", "파랑새", "호반새", "팔색조", "소쩍새", "쏙독새", "중대백로", "황로", "청호반새", "긴꼬리딱새", "흰눈썹황금새", "큰유리새", "되지빠귀", "저어새", "뜸부기", "물닭")

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

/** 계절 화면 연출 — 봄 꽃잎, 가을 낙엽, 여름/겨울 색감. 스크린 좌표로 그린다. */
class SeasonFx {
    private class Flake(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var size: Float, var color: Int, var phase: Float)

    private val flakes = ArrayList<Flake>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tint = Paint()
    private val rnd = Random(7)
    private var t = 0f

    fun update(dt: Float, season: Season, weather: Weather, w: Float, h: Float) {
        t += dt
        val want = when {
            weather == Weather.RAIN || weather == Weather.SNOW -> 0
            season == Season.SPRING -> 18
            season == Season.AUTUMN -> 16
            else -> 0
        }
        val windK = if (weather == Weather.WIND) 2.6f else 1f
        while (flakes.size < want) flakes.add(spawn(season, w, h, rnd.nextFloat() * h))
        val it = flakes.iterator()
        var alive = 0
        while (it.hasNext()) {
            val f = it.next()
            f.phase += dt
            f.x += (f.vx * windK + sin(f.phase * 1.7f) * 14f) * dt
            f.y += f.vy * dt
            f.rot += dt * 90f * windK
            if (f.y > h + 10f || f.x > w + 20f || f.x < -20f) {
                if (alive < want) { val n = spawn(season, w, h, -8f); f.x = n.x; f.y = n.y; f.color = n.color; f.size = n.size }
                else { it.remove(); continue }
            }
            alive++
        }
    }

    private fun spawn(season: Season, w: Float, h: Float, y: Float): Flake {
        val color = if (season == Season.SPRING) {
            if (rnd.nextBoolean()) 0xFFFFD1DC.toInt() else 0xFFFBE3EA.toInt()
        } else {
            listOf(0xFFD9822B.toInt(), 0xFFC0392B.toInt(), 0xFFE0B040.toInt(), 0xFF9C5B2E.toInt())[rnd.nextInt(4)]
        }
        return Flake(rnd.nextFloat() * w * 1.1f - w * 0.1f, y, 10f + rnd.nextFloat() * 16f, 16f + rnd.nextFloat() * 18f,
            rnd.nextFloat() * 360f, if (season == Season.SPRING) 2.2f + rnd.nextFloat() * 1.4f else 3f + rnd.nextFloat() * 2f,
            color, rnd.nextFloat() * 6f)
    }

    fun draw(c: Canvas, season: Season, w: Float, h: Float) {
        // 은은한 계절 색감
        val col = when (season) {
            Season.SPRING -> Color.argb(10, 255, 190, 210)
            Season.SUMMER -> Color.argb(12, 255, 230, 140)
            Season.AUTUMN -> Color.argb(16, 230, 140, 50)
            Season.WINTER -> Color.argb(18, 190, 215, 255)
        }
        tint.color = col
        c.drawRect(0f, 0f, w, h, tint)
        for (f in flakes) {
            paint.color = f.color
            c.save()
            c.rotate(f.rot, f.x, f.y)
            c.drawOval(f.x - f.size, f.y - f.size * 0.55f, f.x + f.size, f.y + f.size * 0.55f, paint)
            c.restore()
        }
    }
}
