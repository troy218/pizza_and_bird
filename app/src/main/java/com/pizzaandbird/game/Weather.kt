package com.pizzaandbird.game

/** 게임 전체에 적용되는 날씨. 날씨는 새 출현 가중치와 월드 연출에 함께 영향을 준다. */
enum class Weather(
    val id: String,
    val icon: String,
    val label: String,
    val description: String
) {
    SUNNY("sunny", "☀", "맑음", "하늘을 나는 새가 활발해요"),
    CLOUDY("cloudy", "☁", "흐림", "숲새가 편안하게 활동해요"),
    RAIN("rain", "☂", "비", "물새가 모습을 드러내요"),
    WIND("wind", "≋", "강풍", "맹금과 제비가 바람을 타요"),
    SNOW("snow", "❄", "눈", "추위를 견디는 새가 찾아와요");

    companion object {
        fun fromId(id: String): Weather = values().firstOrNull { it.id == id } ?: SUNNY
    }
}

fun GameState.weather(): Weather = Weather.fromId(weatherId)

/** 현재 날씨에서 새가 나타날 가능성. 1보다 크면 잘 나타나고, 1보다 작으면 드물다. */
fun weatherBirdMultiplier(def: BirdDef, weather: Weather): Double {
    val water = "water" in def.habitats || "wetland" in def.habitats || "coast" in def.habitats
    val aerial = def.art.template == 12 || "aerial" in def.habitats
    val raptor = def.art.template == 3
    val forest = "forest" in def.habitats || "mountain" in def.habitats
    return when (weather) {
        Weather.SUNNY -> when { aerial -> 1.35; water -> 0.92; else -> 1.0 }
        Weather.CLOUDY -> when { forest -> 1.18; aerial -> 0.85; else -> 1.0 }
        Weather.RAIN -> when { water -> 1.65; aerial -> 0.42; raptor -> 0.62; else -> 0.9 }
        Weather.WIND -> when { aerial || raptor -> 1.8; water -> 1.12; else -> 0.8 }
        Weather.SNOW -> when {
            def.id == "owl" || def.id == "crane" || def.id == "nightheron" -> 1.75
            water -> 0.72
            aerial -> 0.5
            else -> 0.86
        }
    }
}
