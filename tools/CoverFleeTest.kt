import com.pizzaandbird.game.*
import kotlin.math.abs

/**
 * 지형지물 은엄폐(시야 차폐) 판정과, 지형지물 특성별·종군별 도망 반경 감소를 검증한다.
 * (생태학 근거·수치 출처는 docs/BIRD_ECOLOGY.md "지형지물 은엄폐" —
 *  로벨 시야차단량 등급, FID-커버 관계(Stankowich & Blumstein 2005), 맹금 시각 능력 반영)
 *
 *   - [GameMap.concealmentAt]: 갈대 > 산·건물 > 나무 > 바위 > 키 큰 풀 > 소품(0)의 차폐 등급.
 *   - [GameMap.concealmentAlong]: 여러 차폐가 겹치면 투과율을 곱해 합성 (1−(1−q₁)(1−q₂)).
 *   - [GameMap.isOccluded]: 두 월드 좌표 사이에 차폐가 있으면 true.
 *   - [FieldBird.update]: 은폐가 깊을수록 도망 반경이 더 줄고, 맹금류는 엄폐 효과가 약하며,
 *     풀섶 부분 은폐는 약한 감소만 낸다. 극근접(12px 미만)에서는 은폐와 무관하게 도망친다.
 *
 * 실행 (프리뷰 안드로이드 스텁과 함께 컴파일 — android.jar의 Paint는 Stub!로 죽는다):
 *   SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
 *     ! -name 'MainActivity.kt' ! -name 'GameView.kt')
 *   kotlinc $(find tools/preview/src -maxdepth 1 -name '*.kt') $SRCS tools/CoverFleeTest.kt \
 *     -d /tmp/kt/tests-cover -jvm-target 17
 *   java -cp /tmp/kt/tests-cover:$KOTLIN_HOME/lib/kotlin-stdlib.jar CoverFleeTestKt
 */

private fun mapWith(vararg placed: Pair<Pair<Int, Int>, T>): GameMap {
    val size = 12
    val tiles = Array(size) { IntArray(size) { T.GRASS.ordinal } }
    for ((pos, tile) in placed) tiles[pos.second][pos.first] = tile.ordinal
    return GameMap(
        Regions.ALL.first(), size, size, tiles,
        Array(size) { IntArray(size) }, Array(size) { IntArray(size) },
        Array(size) { IntArray(size) }, emptyList(), false, 0, 0
    )
}

/** 종군(이동 스타일)만 바꾼 테스트 새 — 기본은 명금형(SONG_BIRD). */
private fun testBird(x: Float, y: Float, orderName: String = "", familyName: String = ""): FieldBird {
    val def = BirdDef(
        "cover_test", "테스트희귀새", Tier.RARE, setOf("field"), 1.0, 10000,
        "은엄폐 판정 전용 테스트 새.",
        null,
        BirdArt(0, 0xFF888888.toInt(), 0xFFDDDDDD.toInt(), 0xFF555555.toInt(),
            0xFF333333.toInt(), 0xFF555555.toInt(), 0xFF777777.toInt()),
        orderName = orderName, familyName = familyName
    )
    return FieldBird(def, x, y)
}

private fun near(a: Float, b: Float) = abs(a - b) < 0.005f

fun main() {
    val rock = mapWith((5 to 5) to T.ROCK)
    val open = mapWith()

    // --- concealmentAt: 지형지물마다 은폐 특성이 다르다 ---
    val grades = mapWith(
        (1 to 1) to T.REED, (2 to 1) to T.TREE, (3 to 1) to T.ROCK,
        (4 to 1) to T.MOUNTAIN, (5 to 1) to T.TALLGRASS,
        (6 to 1) to T.BENCH, (7 to 1) to T.LAMP, (8 to 1) to T.GRASS
    )
    check(grades.concealmentAt(1, 1) == 0.95f)     // 갈대 군락 — 가장 강한 숨을 곳
    check(grades.concealmentAt(4, 1) == 0.9f)      // 산·건물 등 부피 구조물 — 완전한 덩어리
    check(grades.concealmentAt(2, 1) == 0.85f)     // 나무 — 도피 커버 (줄기 사이 틈)
    check(grades.concealmentAt(3, 1) == 0.8f)      // 바위 — 수평 차폐, 위로는 틈
    check(grades.concealmentAt(5, 1) == 0.4f)      // 키 큰 풀 — 부분 차폐
    check(grades.concealmentAt(6, 1) == 0f)        // 벤치 — 키가 낮아 숨을 수 없음
    check(grades.concealmentAt(7, 1) == 0f)        // 가로등
    check(grades.concealmentAt(8, 1) == 0f)        // 잔디 — 틈 없음

    // --- occludesSight: 차폐가 0보다 큰 지형지물만 시야를 가린다 ---
    check(rock.occludesSight(5, 5))                          // ROCK
    val treeMap = mapWith((3 to 3) to T.TREE, (4 to 3) to T.BENCH, (6 to 3) to T.MOUNTAIN)
    check(treeMap.occludesSight(3, 3))                       // TREE
    check(treeMap.occludesSight(6, 3))                       // MOUNTAIN
    check(!treeMap.occludesSight(4, 3))                      // BENCH (tiles[3][4]) — 키가 낮아 숨을 수 없음
    check(!rock.occludesSight(0, 0))                         // GRASS

    // --- concealmentAlong: 투과율 합성 — 바위 0.8, 키 큰 풀 하나 0.4, 둘 0.64 ---
    check(near(rock.concealmentAlong(88f, 105f, 88f, 70f), 0.8f))                  // 바위가 사이를 가림
    check(near(open.concealmentAlong(88f, 105f, 88f, 70f), 0f))                    // 가리는 것 없음
    check(near(open.concealmentAlong(30f, 30f, 34f, 34f), 0f))                     // 너무 가까우면 판정 생략
    check(near(open.concealmentAlong(20f, 100f, 160f, 100f), 0f))                  // 빗겨가는 수평선
    val oneGrass = mapWith((5 to 5) to T.TALLGRASS)
    check(near(oneGrass.concealmentAlong(88f, 105f, 88f, 70f), 0.4f))              // 부분 차폐 하나
    val twoGrass = mapWith((5 to 5) to T.TALLGRASS, (5 to 6) to T.TALLGRASS)
    check(near(twoGrass.concealmentAlong(88f, 105f, 88f, 70f), 0.64f))             // 1−(1−0.4)² — 겹치면 깊어진다
    val mixed = mapWith((5 to 5) to T.ROCK, (5 to 6) to T.TALLGRASS)
    check(near(mixed.concealmentAlong(88f, 105f, 88f, 70f), 0.88f))                // 1−(1−0.8)(1−0.4) = 0.88
    check(rock.isOccluded(88f, 105f, 88f, 70f))
    check(!open.isOccluded(88f, 105f, 88f, 70f))

    // --- FieldBird.update: RARE 기준 도망 반경 = 3.0타일(48px) ---
    // 새 중심 (88,70) ↔ 플레이어 중심 (88,105) 거리 = 35px
    // (새 스프라이트 24×24 기본: 중심 = 좌상단 + (12, 17.28))
    val birdX = 88f - 12f
    val birdY = 70f - 17.28f

    // 1) 탁 트인 곳: 35 < 48 → 도망. 가림 없음.
    val exposed = testBird(birdX, birdY)
    exposed.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = open)
    check(!exposed.hiddenFromPlayer) { "탁 트인 곳에서는 가림 판정이 없어야 한다" }
    check(exposed.state == 2) { "가림 없이 도망 반경 안에 들어가면 도망가야 한다" }

    // 2) 바위 뒤(0.8 → 도망 반경 48×0.56 = 26.9px): 35px에서는 안 도망, 경계 자세만.
    val hidden = testBird(birdX, birdY)
    hidden.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = rock)
    check(hidden.hiddenFromPlayer) { "바위 뒤 플레이어는 가려져야 한다" }
    check(near(hidden.coverQuality, 0.8f))
    check(hidden.state == 0) { "숨은 상태에서는 평소 도망 반경 안에서도 도망가지 않아야 한다" }
    check(hidden.renderPose == BirdPose.ALERT) { "숨었지만 가까운 플레이어를 경계 자세로 살핀다" }

    // 3) 바위 뒤라도 너무 가까우면(22px < 26.9px) 도망 — 엄폐에도 최소 안전 거리가 있다.
    val tooClose = testBird(birdX, birdY)
    tooClose.update(0.016f, 88f, 92f, onBike = false, sneaking = false, map = rock)
    check(tooClose.state == 2) { "은폐한 지형지물 바로 앞까지 붙으면 도망가야 한다" }

    // 4) 극근접(시선 판정 거리 12px 미만)에서는 엄폐를 따지지 않고 무조건 도망 — 밟히기 전에 뜬다.
    val cornered = testBird(birdX, birdY)
    cornered.update(0.016f, 88f, 77f, onBike = false, sneaking = false, map = rock)
    check(cornered.state == 2) { "너무 가까이 붙으면 가림과 무관하게 도망가야 한다" }

    // 5) 갈대 군락(0.95 → 48×0.4775 = 22.9px): 바위보다 더 가까이 접근할 수 있다.
    val reedMap = mapWith((5 to 5) to T.REED)
    val inReeds = testBird(birdX, birdY)
    inReeds.update(0.016f, 88f, 93f, onBike = false, sneaking = false, map = reedMap)  // 23px
    check(inReeds.state == 0) { "갈대 뒤에서는 23px까지 접근해도 안 도망해야 한다" }
    check(near(inReeds.coverQuality, 0.95f))
    val reedFlush = testBird(birdX, birdY)
    reedFlush.update(0.016f, 88f, 92f, onBike = false, sneaking = false, map = reedMap)  // 22px
    check(reedFlush.state == 2) { "갈대 뒤라도 22px 안쪽이면 도망가야 한다" }

    // 6) 키 큰 풀 부분 은폐(0.4 → 48×0.78 = 37.4px): 35px에서 아직 도망 — 바위보다 약하다.
    val grassRun = testBird(birdX, birdY)
    grassRun.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = oneGrass)
    check(grassRun.hiddenFromPlayer) { "풀섶도 부분 은폐로 인정된다" }
    check(near(grassRun.coverQuality, 0.4f))
    check(grassRun.state == 2) { "부분 은폐만으로는 35px 접근을 막지 못한다" }

    // 7) 키 큰 풀 두 겹(0.64 → 48×0.648 = 31.1px): 겹치면 35px에서 안 도망 — 차폐가 합성된다.
    val grassStay = testBird(birdX, birdY)
    grassStay.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = twoGrass)
    check(grassStay.state == 0) { "겹친 부분 은폐는 단독보다 깊어져야 한다" }

    // 8) 맹금류(coverEffect 0.45): 바위 뒤 35px에서도 도망 — 시력이 좋아 엄폐가 잘 통하지 않는다.
    val raptor = testBird(birdX, birdY, orderName = "수리목")
    raptor.update(0.016f, 88f, 105f, onBike = false, sneaking = false, map = rock)
    check(near(raptor.coverQuality, 0.36f)) { "맹금은 바위 엄폐 효과가 크게 깎인다" }
    check(raptor.state == 2) { "맹금류는 바위 뒤에서도 같은 거리에서 도망가야 한다" }

    // 9) 도요류(coverEffect 1.15): 바위 뒤 25px에서 버틴다 — 트인 물가 무리는 은폐한 접근에 둔감.
    val wader = testBird(birdX, birdY, familyName = "도요과")
    wader.update(0.016f, 88f, 95f, onBike = false, sneaking = false, map = rock)   // 25px
    check(near(wader.coverQuality, 0.92f))
    check(wader.state == 0) { "은폐한 접근에는 도요류도 더 가까이 허용해야 한다" }
    val waderSongbird = testBird(birdX, birdY)
    waderSongbird.update(0.016f, 88f, 95f, onBike = false, sneaking = false, map = rock)  // 25px
    check(waderSongbird.state == 2) { "같은 조건의 명금형은 25px에서 이미 도망쳐야 한다" }

    println("Cover flee: graded terrain concealment, stacking, species cover response — passed")
}
