import com.pizzaandbird.game.BirdMovement
import com.pizzaandbird.game.BirdMovementStyle
import com.pizzaandbird.game.Birds

/** Run against the compiled game classes to guard the ecology-to-motion mapping. */
fun main() {
    fun style(name: String) = BirdMovement.profile(Birds.byName.getValue(name)).style

    check(style("참새") == BirdMovementStyle.SONG_BIRD)
    check(style("청둥오리") == BirdMovementStyle.WATERFOWL)
    check(style("중대백로") == BirdMovementStyle.WADER)
    check(style("황조롱이") == BirdMovementStyle.RAPTOR)
    check(style("제비") == BirdMovementStyle.AERIAL)
    check(style("수리부엉이") == BirdMovementStyle.OWL)
    val groundBird = Birds.ALL.first { it.orderName in setOf("닭목", "사막꿩목", "느시목") }
    check(BirdMovement.profile(groundBird).style == BirdMovementStyle.GROUNDFORAGER)

    val profiles = Birds.ALL.map { BirdMovement.profile(it) }
    check(profiles.map { it.style }.toSet().size >= 7)
    check(profiles.all { it.restMin > 0f && it.restMax >= it.restMin && it.stepSeconds > 0f })
    check(BirdMovement.profile(Birds.byName.getValue("제비")).stepSeconds <
        BirdMovement.profile(Birds.byName.getValue("수리부엉이")).stepSeconds)

    // 지형지물 은폐 효과: 시력 좋은 맹금/올빼미 < 명금 < 트인 물가의 도요·백로 (docs/BIRD_ECOLOGY.md)
    fun cover(name: String) = BirdMovement.profile(Birds.byName.getValue(name)).coverEffect
    check(cover("황조롱이") < cover("참새")) { "맹금은 엄폐가 잘 통하지 않아야 한다" }
    check(cover("수리부엉이") < cover("참새")) { "올빼미류도 시각 예민해 엄폐 효과가 약해야 한다" }
    check(cover("참새") < cover("중대백로")) { "트인 물가 무리는 은폐한 접근에 더 둔감해야 한다" }
    check(profiles.all { it.coverEffect in 0.4f..1.2f })
    println("Bird movement: seven ecological movement styles and species profiles passed")
}
