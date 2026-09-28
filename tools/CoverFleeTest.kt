import com.pizzaandbird.game.*
import kotlin.math.abs

/**
 * 지형지물 은엄폐(시야 차폐) 판정과, 지형지물 특성별·종군별 도망 반경 감소를 검증한다.
 * (생태학 근거·수치 출처는 docs/BIRD_ECOLOGY.md "지형지물 은엄폐" —
 *  로벨 시야차단량 등급, FID-커버 관계(Stankowich & Blumstein 2005), 맹금 시각 능력 반영)
 *
 *   - [GameMap.concealmentAt]: 갈대 > 산·건물 > 나무 > 큰 바위 > 낮은 돌 > 키 큰 풀 > 소품(0)
 *     의 차폐 등급. 바위는 크기급(`PropSize`)마다 다르고 **발목 자갈 뒤에는 숨지 못한다**.
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

private fun mapWith(vararg placed: Pair<Pair<Int, Int>, T>): GameMap =
    mapWithIn(Regions.ALL.first(), *placed)

private fun mapWithIn(
    region: RegionDef,
    vararg placed: Pair<Pair<Int, Int>, T>
): GameMap {
    val size = 12
    val tiles = Array(size) { IntArray(size) { T.GRASS.ordinal } }
    for ((pos, tile) in placed) tiles[pos.second][pos.first] = tile.ordinal
    return GameMap(
        region, size, size, tiles,
        Array(size) { IntArray(size) }, Array(size) { IntArray(size) },
        Array(size) { IntArray(size) }, emptyList(), false, 0, 0
    )
}

/**
 * [size] 급 바위가 놓일 지역·좌표 — 바위 변형은 지역·좌표 해시로 정해지므로
 * 실제 그 크기의 변형이 나오는 자리를 찾아 쓴다. (바위는 32px 타일 1:1 아트라
 * 크기급이 곧 은폐 등급 — docs/BIRD_ECOLOGY.md)
 */
private fun rockSpot(size: PropSize): Triple<RegionDef, Int, Int> {
    for (region in Regions.ALL) {
        val probe = mapWithIn(region)
        for (y in 1..10) for (x in 0..11) {
            if (PropLooks.rockSize(probe.rockLook(x, y)) == size) return Triple(region, x, y)
        }
    }
    error("$size 급 바위 변형이 어느 지역에도 없다")
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
    val open = mapWith()

    // --- concealmentAt: 지형지물마다 은폐 특성이 다르다 ---
    val grades = mapWith(
        (1 to 1) to T.REED, (2 to 1) to T.TREE, (4 to 1) to T.MOUNTAIN,
        (5 to 1) to T.TALLGRASS, (6 to 1) to T.BENCH, (7 to 1) to T.LAMP, (8 to 1) to T.GRASS
    )
    check(grades.concealmentAt(1, 1) == 0.95f)     // 갈대 군락 — 가장 강한 숨을 곳
    check(grades.concealmentAt(4, 1) == 0.9f)      // 산·건물 등 부피 구조물 — 완전한 덩어리
    check(grades.concealmentAt(2, 1) == 0.85f)     // 나무 — 도피 커버 (줄기 사이 틈)
    check(grades.concealmentAt(5, 1) == 0.4f)      // 키 큰 풀 — 부분 차폐
    check(grades.concealmentAt(6, 1) == 0f)        // 벤치 — 키가 낮아 숨을 수 없음
    check(grades.concealmentAt(7, 1) == 0f)        // 가로등
    check(grades.concealmentAt(8, 1) == 0f)        // 잔디 — 틈 없음

    // --- 바위는 크기급(PropSize)마다 은폐가 다르다 ---
    val (tallRegion, tallX, tallY) = rockSpot(PropSize.TALL)
    val (lowRegion, lowX, lowY) = rockSpot(PropSize.LOW)
    val (pebRegion, pebX, pebY) = rockSpot(PropSize.PEBBLE)
    val tallRock = mapWithIn(tallRegion, (tallX to tallY) to T.ROCK)
    val lowRock = mapWithIn(lowRegion, (lowX to lowY) to T.ROCK)
    val pebRock = mapWithIn(pebRegion, (pebX to pebY) to T.ROCK)
    check(tallRock.concealmentAt(tallX, tallY) == 0.8f)    // 큰 바위 — 강한 수평 차폐
    check(lowRock.concealmentAt(lowX, lowY) == 0.55f)      // 낮은 돌 — 웅크려야 부분 차폐
    check(pebRock.concealmentAt(pebX, pebY) == 0f)         // 발목 자갈 — 숨을 수 없다
    check(tallRock.occludesSight(tallX, tallY))
    check(lowRock.occludesSight(lowX, lowY))
    check(!pebRock.occludesSight(pebX, pebY))

    // --- 큰 바위를 기준으로 세운 세로 시선: 새 (rx, ry−1) ↔ 플레이어 (rx, ry+1) ---
    val cx = tallX * 16f + 8f
    val birdCy = tallY * 16f - 10f
    val playerCy = birdCy + 35f
    val openRay = near(open.concealmentAlong(cx, playerCy, cx, birdCy), 0f)
    check(openRay) { "가리는 것 없음" }
    check(near(open.concealmentAlong(30f, 30f, 34f, 34f), 0f))     // 너무 가까우면 판정 생략
    check(near(open.concealmentAlong(20f, 100f, 160f, 100f), 0f))  // 빗겨가는 수평선

    // --- concealmentAlong: 투과율 합성 ---
    check(near(tallRock.concealmentAlong(cx, playerCy, cx, birdCy), 0.8f))            // 큰 바위 하나
    val oneGrass = mapWithIn(tallRegion, (tallX to tallY) to T.TALLGRASS)
    check(near(oneGrass.concealmentAlong(cx, playerCy, cx, birdCy), 0.4f))            // 부분 차폐 하나
    val twoGrass = mapWithIn(tallRegion, (tallX to tallY) to T.TALLGRASS, (tallX to tallY + 1) to T.TALLGRASS)
    check(near(twoGrass.concealmentAlong(cx, playerCy, cx, birdCy), 0.64f))           // 1−(1−0.4)² — 겹치면 깊어진다
    val mixed = mapWithIn(tallRegion, (tallX to tallY) to T.ROCK, (tallX to tallY + 1) to T.TALLGRASS)
    check(near(mixed.concealmentAlong(cx, playerCy, cx, birdCy), 0.88f))              // 1−(1−0.8)(1−0.4) = 0.88
    check(tallRock.isOccluded(cx, playerCy, cx, birdCy))
    check(!open.isOccluded(cx, playerCy, cx, birdCy))
    check(!pebRock.isOccluded(pebX * 16f + 8f, pebY * 16f - 10f + 35f, pebX * 16f + 8f, pebY * 16f - 10f)) {
        "자갈은 시야를 가리지 않는다"
    }

    // --- FieldBird.update: RARE 기준 도망 반경 = 3.0타일(48px) ---
    // 새 중심 = (rx·16+8, ry·16−10) (스프라이트 24×24: 중심 = 좌상단 + (12, 17.28))
    val birdX = cx - 12f
    val birdY = birdCy - 17.28f

    // 1) 탁 트인 곳: 35 < 48 → 도망. 가림 없음.
    val exposed = testBird(birdX, birdY)
    exposed.update(0.016f, cx, playerCy, onBike = false, sneaking = false, map = open)
    check(!exposed.hiddenFromPlayer) { "탁 트인 곳에서는 가림 판정이 없어야 한다" }
    check(exposed.state == 2) { "가림 없이 도망 반경 안에 들어가면 도망가야 한다" }

    // 2) 큰 바위 뒤(0.8 → 도망 반경 48×0.56 = 26.9px): 35px에서는 안 도망, 경계 자세만.
    val hidden = testBird(birdX, birdY)
    hidden.update(0.016f, cx, playerCy, onBike = false, sneaking = false, map = tallRock)
    check(hidden.hiddenFromPlayer) { "큰 바위 뒤 플레이어는 가려져야 한다" }
    check(near(hidden.coverQuality, 0.8f))
    check(hidden.state == 0) { "숨은 상태에서는 평소 도망 반경 안에서도 도망가지 않아야 한다" }
    check(hidden.renderPose == BirdPose.ALERT) { "숨었지만 가까운 플레이어를 경계 자세로 살핀다" }

    // 3) 큰 바위 뒤라도 너무 가까우면(22px < 26.9px) 도망 — 엄폐에도 최소 안전 거리가 있다.
    val tooClose = testBird(birdX, birdY)
    tooClose.update(0.016f, cx, birdCy + 22f, onBike = false, sneaking = false, map = tallRock)
    check(tooClose.state == 2) { "은폐한 지형지물 바로 앞까지 붙으면 도망가야 한다" }

    // 4) 극근접(시선 판정 거리 12px 미만)에서는 엄폐를 따지지 않고 무조건 도망 — 밟히기 전에 뜬다.
    val cornered = testBird(birdX, birdY)
    cornered.update(0.016f, cx, birdCy + 7f, onBike = false, sneaking = false, map = tallRock)
    check(cornered.state == 2) { "너무 가까이 붙으면 가림과 무관하게 도망가야 한다" }

    // 5) 갈대 군락(0.95 → 48×0.4775 = 22.9px): 큰 바위보다 더 가까이 접근할 수 있다.
    val reedMap = mapWithIn(tallRegion, (tallX to tallY) to T.REED)
    val inReeds = testBird(birdX, birdY)
    inReeds.update(0.016f, cx, birdCy + 23f, onBike = false, sneaking = false, map = reedMap)
    check(inReeds.state == 0) { "갈대 뒤에서는 23px까지 접근해도 안 도망해야 한다" }
    check(near(inReeds.coverQuality, 0.95f))
    val reedFlush = testBird(birdX, birdY)
    reedFlush.update(0.016f, cx, birdCy + 22f, onBike = false, sneaking = false, map = reedMap)
    check(reedFlush.state == 2) { "갈대 뒤라도 22px 안쪽이면 도망가야 한다" }

    // 6) 키 큰 풀 부분 은폐(0.4 → 48×0.78 = 37.4px): 35px에서 아직 도망 — 큰 바위보다 약하다.
    val grassRun = testBird(birdX, birdY)
    grassRun.update(0.016f, cx, playerCy, onBike = false, sneaking = false, map = oneGrass)
    check(grassRun.hiddenFromPlayer) { "풀섶도 부분 은폐로 인정된다" }
    check(near(grassRun.coverQuality, 0.4f))
    check(grassRun.state == 2) { "부분 은폐만으로는 35px 접근을 막지 못한다" }

    // 7) 키 큰 풀 두 겹(0.64 → 48×0.648 = 31.1px): 겹치면 35px에서 안 도망 — 차폐가 합성된다.
    val grassStay = testBird(birdX, birdY)
    grassStay.update(0.016f, cx, playerCy, onBike = false, sneaking = false, map = twoGrass)
    check(grassStay.state == 0) { "겹친 부분 은폐는 단독보다 깊어져야 한다" }

    // 8) 자갈 뒤(차폐 0): 큰 바위와 달리 숨지 못해 35px에서 도망 — 자갈 더미를 피해 다가가야 한다.
    val pebBird = testBird(pebX * 16f + 8f - 12f, pebY * 16f - 10f - 17.28f)
    pebBird.update(0.016f, pebX * 16f + 8f, pebY * 16f - 10f + 35f, onBike = false, sneaking = false, map = pebRock)
    check(!pebBird.hiddenFromPlayer) { "자갈 뒤에는 숨은 상태가 아니다" }
    check(near(pebBird.coverQuality, 0f))
    check(pebBird.state == 2) { "자갈 뒤에서는 도망 반경이 줄지 않아야 한다" }

    // 9) 낮은 돌(0.55 → 48×0.6975 = 33.5px)은 32px에서 막지 못하고, 큰 바위는 막는다.
    val lowBird = testBird(lowX * 16f + 8f - 12f, lowY * 16f - 10f - 17.28f)
    lowBird.update(0.016f, lowX * 16f + 8f, lowY * 16f - 10f + 32f, onBike = false, sneaking = false, map = lowRock)
    check(lowBird.state == 2) { "낮은 돌 뒤에서는 32px 접근을 막지 못한다" }
    val tallAt32 = testBird(birdX, birdY)
    tallAt32.update(0.016f, cx, birdCy + 32f, onBike = false, sneaking = false, map = tallRock)
    check(tallAt32.state == 0) { "큰 바위 뒤에서는 32px에서 버텨야 한다" }

    // 10) 맹금류(coverEffect 0.45): 큰 바위 뒤 35px에서도 도망 — 시력이 좋아 엄폐가 잘 통하지 않는다.
    val raptor = testBird(birdX, birdY, orderName = "수리목")
    raptor.update(0.016f, cx, playerCy, onBike = false, sneaking = false, map = tallRock)
    check(near(raptor.coverQuality, 0.36f)) { "맹금은 바위 엄폐 효과가 크게 깎인다" }
    check(raptor.state == 2) { "맹금류는 바위 뒤에서도 같은 거리에서 도망가야 한다" }

    // 11) 도요류(coverEffect 1.15): 큰 바위 뒤 25px에서 버틴다 — 트인 물가 무리는 은폐한 접근에 둔감.
    val wader = testBird(birdX, birdY, familyName = "도요과")
    wader.update(0.016f, cx, birdCy + 25f, onBike = false, sneaking = false, map = tallRock)
    check(near(wader.coverQuality, 0.92f))
    check(wader.state == 0) { "은폐한 접근에는 도요류도 더 가까이 허용해야 한다" }
    val waderSongbird = testBird(birdX, birdY)
    waderSongbird.update(0.016f, cx, birdCy + 25f, onBike = false, sneaking = false, map = tallRock)
    check(waderSongbird.state == 2) { "같은 조건의 명금형은 25px에서 이미 도망쳐야 한다" }

    println("Cover flee: graded terrain concealment, rock sizes, stacking, species cover response — passed")
}
