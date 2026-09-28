package com.pizzaandbird.game

/** 게임 전체에 적용되는 날씨. 날씨는 새 출현 가중치와 월드 연출에 함께 영향을 준다. */
enum class Weather(
    val id: String,
    val icon: String,
    val label: String,
    val description: String
) {
    SUNNY("sunny", "sun", "맑음", "하늘을 나는 새가 활발해요"),
    CLOUDY("cloudy", "cloud", "흐림", "숲새가 편안하게 활동해요"),
    RAIN("rain", "rain", "비", "물새가 모습을 드러내요"),
    WIND("wind", "wind", "강풍", "맹금과 제비가 바람을 타요"),
    SNOW("snow", "snow", "눈", "추위를 견디는 새가 찾아와요");

    companion object {
        fun fromId(id: String): Weather = values().firstOrNull { it.id == id } ?: SUNNY
    }
}

fun GameState.weather(): Weather = Weather.fromId(weatherId)

// -----------------------------------------------------------------------
// 기후 × 계절 × 밤낮 — 출현 가중치의 세 번째 축
// -----------------------------------------------------------------------

/** 나그네새(도요·물떼새류) — 봄·가을 통과 철새 */
private val PASSAGE_FAMILIES = setOf("도요과", "물떼새과", "검은머리물떼새과", "장다리물떼새과")

/** 겨울 월동 대형 철새 — 눈 내린 월동지의 상징 */
private fun isWinterGuest(def: BirdDef): Boolean =
    residencyOf(def) == Residency.WINTER ||
        def.familyName in setOf("기러기과", "백조과", "두루미과") ||
        def.name.contains("기러기") || def.name.contains("고니") || def.name.contains("두루미")

private fun isCrane(def: BirdDef): Boolean =
    def.familyName == "두루미과" || def.name.contains("두루미")

/**
 * 현재 날씨에서 새가 나타날 가능성 — 1보다 크면 잘 나타나고, 작으면 드물다.
 *
 * [season]·[night]를 함께 주면 기후 효과를 그 맥락에 맞게 굴린다 (둘 다 생략 가능).
 *
 * - **계절 기후**: 봄·가을의 비는 나그네새 통과를 방해하고, 여름비(장마)는 습지 번식조를 부른다.
 *   가을 강풍은 이동기 맹금을 띄우고, 겨울 눈은 월동철새와 눈을 견디는 텃새를 부른다.
 * - **밤 기후**: 맑은 달밤엔 올빼미가 사냥을 나서고, 비 오는 밤엔 야행성이 조용해진다.
 *   밤을 나는 철새는 맑고 잔잔한 밤을 항해 삼는다.
 * - 최종값은 0.05~3.0으로 클램프해 특정 조합이 출현을 완전히 끊지 않게 한다.
 *   (완전 차단은 [Birds.poolFor]의 계절·밤낮 필터가 관할 — 이 배율은 "얼마나 잘 만날까"를 결정)
 */
fun weatherBirdMultiplier(def: BirdDef, weather: Weather, season: Season? = null, night: Boolean = false): Double {
    val water = "water" in def.habitats || "wetland" in def.habitats || "coast" in def.habitats
    val aerial = def.art.template == 12 || "aerial" in def.habitats   // 제비·칼새류
    val raptor = def.art.template == 3
    val forest = "forest" in def.habitats || "mountain" in def.habitats
    val nocturnal = def.active == "night"
    val passage = def.familyName in PASSAGE_FAMILIES

    // ① 날씨 기본 배율 — 서식지·체형별 활동량
    var m = when (weather) {
        Weather.SUNNY -> when {
            aerial -> 1.35
            water -> 0.92
            else -> 1.0
        }
        Weather.CLOUDY -> when {
            forest -> 1.18
            aerial -> 0.85
            else -> 1.0
        }
        Weather.RAIN -> when {
            water -> 1.9          // 비 올수록 물가·습지 새는 활발하다
            forest -> 1.12        // 땅벌레가 삐져나오는 숲
            aerial -> 0.18        // 하늘 새는 날개를 펴지 못한다
            raptor -> 0.5
            else -> 0.78          // 지상의 작은 새는 둥지로 몸을 숨긴다
        }
        Weather.WIND -> when {
            aerial || raptor -> 2.2  // 바람을 타는 새의 시간
            water -> 1.12
            else -> 0.55             // 지상 새는 웅크리고 바닥을 날아다닌다
        }
        Weather.SNOW -> when {
            nocturnal || isCrane(def) -> 1.75   // 눈 밝힌 밤의 부엉이, 설원의 두루미
            isWinterGuest(def) -> 1.4           // 눈 내린 겨울 월동지
            water -> 0.72
            aerial -> 0.5
            else -> 1.05                        // 눈을 견디는 텃새(참새·딱새류 등)
        }
    }

    // ② 계절 × 기후 — 같은 비·바람·눈도 계절에 따라 다른 새를 부른다
    if (season != null) {
        m *= when (weather) {
            Weather.RAIN -> when (season) {
                Season.SUMMER -> if (water || forest) 1.25 else 1.0          // 장마 습지 번식조
                Season.SPRING, Season.AUTUMN -> if (passage) 0.7 else 1.0     // 봄·가을 나그네새 통과 방해
                Season.WINTER -> 0.9                                          // 겨울비엔 대체로 웅크린다
            }
            Weather.WIND -> when (season) {
                Season.AUTUMN -> if (raptor || passage) 1.3 else 1.0          // 가을 이동철 — 능선을 타는 맹금
                Season.SUMMER -> if (aerial) 0.75 else 1.0                    // 여름 스콜 바람엔 공중종 회피
                else -> 1.0
            }
            Weather.SNOW -> when (season) {
                Season.WINTER -> when {
                    isWinterGuest(def) -> 1.25                                // 겨울 한복판의 월동지
                    !water && residencyOf(def) == Residency.RESIDENT -> 1.15  // 눈을 견디는 텃새
                    else -> 1.0
                }
                else -> 1.0                                                   // 이례적인 계절 눈은 평시처럼
            }
            else -> 1.0
        }
    }

    // ③ 밤 × 기후 — 밤 풀(야행성·야간 철새) 안의 종 구성까지 바꾼다
    if (night) {
        m *= when {
            nocturnal -> when (weather) {                 // 올빼미·쏙독새·해오라기
                Weather.SUNNY -> 1.45                     // 달 밝은 밤 — 사냥 개시
                Weather.CLOUDY -> 1.25
                Weather.WIND -> 0.7
                Weather.RAIN -> 0.45                      // 빗소리에 사냥이 불가능하다
                Weather.SNOW -> 1.3                       // 설경이 밤을 밝힌다
            }
            passage || isWinterGuest(def) -> when (weather) {  // 밤하늘을 나는 철새
                Weather.SUNNY, Weather.CLOUDY -> 1.15          // 맑고 잔잔한 밤은 항해 좋은 밤
                Weather.WIND -> 0.55
                Weather.RAIN -> 0.6
                Weather.SNOW -> 0.65
            }
            else -> 1.0
        }
    }

    return m.coerceIn(0.05, 3.0)
}
