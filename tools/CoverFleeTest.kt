import com.pizzaandbird.game.*

/**
 * 지형지물 뒤 은엄폐(시야 차폐) 판정과, 그에 따른 새 도망 반경 감소를 검증한다.
 *
 *   - [GameMap.occludesSight]: 바위·나무·산·건물은 시야를 가리고, 벤치·가로등은 가리지 않는다.
 *   - [GameMap.isOccluded]: 두 월드 좌표 사이에 지형지물이 있으면 true.
 *   - [FieldBird.update]: 바위 뒤에 숨으면 평소 도망 반경 안에서도 도망가지 않고,
 *     가림이 없으면 같은 거리에서 도망간다.
 *
 * 실행: 컴파일된 게임 클래스 + android.jar + kotlin-stdlib 을 클래스패스에 두고 JVM으로 돌린다.
 */

private fun blankMap(size: Int, placeRock: Boolean): GameMap {
    val tiles = Array(size) { IntArray(size) { T.GRASS.ordinal } }
    if (placeRock) tiles[5][5] = T.ROCK.ordinal
    return GameMap(
        Regions.ALL.first(), size, size, tiles,
        Array(size) { IntArray(size) }, Array(size) { IntArray(size) },
        Array(size) { IntArray(size) }, emptyList(), false, 0, 0
    )
}

private fun rareBird(x: Float, y: Float): FieldBird {
    val def = BirdDef(
        "cover_test", "테스트희귀새", Tier.RARE, setOf("field"), 1.0, 10000,
        "은엄폐 판정 전용 테스트 새.",
        null,
        BirdArt(0, 0xFF888888.toInt(), 0xFFDDDDDD.toInt(), 0xFF555555.toInt(),
            0xFF333333.toInt(), 0xFF555555.toInt(), 0xFF777777.toInt())
    )
    return FieldBird(def, x, y)
}

fun main() {
    val size = 12
    val rock = blankMap(size, true)
    val open = blankMap(size, false)

    // --- occludesSight: 키 큰 지형지물만 시야를 가린다 ---
    check(rock.occludesSight(5, 5))                          // ROCK
    val treeMap = run {
        val t = Array(size) { IntArray(size) { T.GRASS.ordinal } }
        t[3][3] = T.TREE.ordinal; t[3][4] = T.BENCH.ordinal; t[3][6] = T.MOUNTAIN.ordinal
        GameMap(Regions.ALL.first(), size, size, t,
            Array(size) { IntArray(size) }, Array(size) { IntArray(size) },
            Array(size) { IntArray(size) }, emptyList(), false, 0, 0)
    }
    check(treeMap.occludesSight(3, 3))                       // TREE (tiles[3][3])
    check(treeMap.occludesSight(6, 3))                       // MOUNTAIN (tiles[3][6])
    check(!treeMap.occludesSight(4, 3))                      // BENCH (tiles[3][4]) — 키가 낮아 숨을 수 없음
    check(!rock.occludesSight(0, 0))                         // GRASS

    // --- isOccluded: 새(위)와 플레이어(아래) 사이에 바위가 있는가 ---
    // 바위 타일 (5,5) 중심 월드 좌표 ≈ (88,88)
    check(rock.isOccluded(88f, 105f, 88f, 70f))              // 바위가 사이를 가림
    check(!open.isOccluded(88f, 105f, 88f, 70f))             // 가리는 것 없음
    check(!rock.isOccluded(30f, 30f, 34f, 34f))              // 너무 가까우면 판정 생략
    check(!rock.isOccluded(20f, 100f, 160f, 100f))           // 바위를 빗겨가는 수평선

    // --- FieldBird.update: 숨으면 안 도망, 안 숨으면 도망 ---
    // RARE 기준 도망 반경 = 3.0타일(48px) · 숨으면 ×0.45 ≈ 21.6px
    // 새 중심 (88,70) ↔ 플레이어 중심 (88,105) 거리 = 35px → 21.6 < 35 < 48
    // (새 스프라이트 24×24 기본: 중심 = 좌상단 + (12, 17.28))
    val birdX = 88f - 12f
    val birdY = 70f - 17.28f

    val hidden = rareBird(birdX, birdY)
    hidden.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = rock)
    check(hidden.hiddenFromPlayer) { "바위 뒤 플레이어는 가려져야 한다" }
    check(hidden.state == 0) { "숨은 상태에서는 평소 도망 반경 안에서도 도망가지 않아야 한다" }

    val exposed = rareBird(birdX, birdY)
    exposed.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = open)
    check(!exposed.hiddenFromPlayer) { "탁 트인 곳에서는 가림 판정이 없어야 한다" }
    check(exposed.state == 2) { "가림 없이 도망 반경 안에 들어가면 도망가야 한다" }

    // 숨었더라도 최소 도망 거리(9px) 안까지 밟히면 여전히 도망간다
    val cornered = rareBird(birdX, birdY)
    cornered.update(0.016f, 88f, 77f, onBike = false, sneaking = false, map = rock)
    check(cornered.state == 2) { "너무 가까이 붙으면 가림과 무관하게 도망가야 한다" }

    println("Cover flee: sight occlusion tiles, hidden birds stay, exposed birds flee — passed")
}
