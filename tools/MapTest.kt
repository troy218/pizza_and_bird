import com.pizzaandbird.game.*

/**
 * 맵/데이터 로직 JVM 검증 스크립트 (Android 런타임 불필요).
 *
 * 사용:
 *   kotlinc -cp <android.jar>:<게임 클래스 출력> -d out tools/MapTest.kt
 *   java -cp <android.jar>:<게임 클래스>:out:<kotlin-stdlib.jar> MapTestKt
 */

fun reach(map: GameMap, sx: Int, sy: Int, tx: Int, ty: Int): Boolean {
    if (sx < 0 || sy < 0 || sx >= map.w || sy >= map.h) return false
    if (map.solidTile(sx, sy)) return false
    val seen = HashSet<Long>()
    val q = ArrayDeque<Pair<Int, Int>>()
    q.add(sx to sy)
    seen.add(sx.toLong() * 1000 + sy)
    while (q.isNotEmpty()) {
        val (x, y) = q.removeFirst()
        if (x == tx && y == ty) return true
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val nx = x + dx
            val ny = y + dy
            if (nx < 0 || ny < 0 || nx >= map.w || ny >= map.h) continue
            val k = nx.toLong() * 1000 + ny
            if (k in seen) continue
            if (map.solidTile(nx, ny)) continue
            if (map.t(nx, ny) == T.TUNNEL) continue
            seen.add(k)
            q.add(nx to ny)
        }
    }
    return false
}

fun main() {
    var fails = 0
    fun check(cond: Boolean, msg: String) {
        if (!cond) { println("FAIL: $msg"); fails++ }
    }

    // 1. 출구 대칭성
    for (r in Regions.ALL) {
        for ((d, target) in Regions.exits(r.id)) {
            check(Regions.exits(target)[Regions.opposite(d)] == r.id, "출구 비대칭 ${r.id} -$d-> $target")
        }
    }

    // 2. 지역 그래프 연결성
    val seen = mutableSetOf("seoul")
    val q = ArrayDeque<String>()
    q.add("seoul")
    while (q.isNotEmpty()) {
        val cur = q.removeFirst()
        for ((_, t) in Regions.exits(cur)) if (t !in seen) { seen.add(t); q.add(t) }
    }
    check(seen.size == Regions.ALL.size, "연결 안 된 지역: ${Regions.ALL.map { it.id }.filter { it !in seen }}")

    // 2.5 새 데이터: 낮 풀/밤 풀 기본 조건
    check(Birds.ALL.isNotEmpty(), "새 데이터 없음")
    check(Birds.ALL.any { it.active == "night" }, "밤새(active=night) 정의 없음")
    check(Birds.ALL.all { it.habitats.isNotEmpty() }, "서식지 없는 새 존재")

    // 3. 모든 (지역, 홈) 조합에 대한 맵 검증
    for (home in Regions.ALL) {
        for (r in Regions.ALL) {
            val map = MapBuilder.build(r, home.id)

            // 스폰 위치가 막히지 않았는지 (터널 스폰은 실제 출구 방향만, HOME 스폰은 홈 지역만)
            val spawns = ArrayList<Pair<String, Pair<Float, Float>>>()
            if (r.id == home.id) spawns.add("HOME" to (376f to 12.2f * 16f))
            for (d in Regions.exits(r.id).keys) {
                when (d) {
                    Dir.N -> spawns.add("N" to (312f to 3f * 16f))
                    Dir.S -> spawns.add("S" to (312f to (map.h - 4f) * 16f))
                    Dir.W -> spawns.add("W" to (3f * 16f to 15.5f * 16f))
                    Dir.E -> spawns.add("E" to ((map.w - 4f) * 16f to 15.5f * 16f))
                }
            }
            for ((name, sp) in spawns) {
                check(!map.solidBox(sp.first, sp.second),
                    "스폰 막힘 region=${r.id} home=${home.id} spawn=$name tile=${map.feetTile(sp.first, sp.second)}")
            }

            // NPC 위치
            for (n in map.npcs) {
                check(!map.solidTile(n.tileX, n.tileY), "NPC 막힘 ${r.id} (${n.tileX},${n.tileY}) ${n.kind}")
                check(!map.solidTile(n.tileX, n.tileY + 1), "NPC 앞 막힘 ${r.id} (${n.tileX},${n.tileY + 1})")
            }

            // 터널 타일 & 플라자까지 경로
            for ((d, _) in Regions.exits(r.id)) {
                val tunnelTiles = when (d) {
                    Dir.N -> listOf(19 to 0, 20 to 0)
                    Dir.S -> listOf(19 to map.h - 1, 20 to map.h - 1)
                    Dir.W -> listOf(0 to 15, 0 to 16)
                    Dir.E -> listOf(map.w - 1 to 15, map.w - 1 to 16)
                }
                for ((tx, ty) in tunnelTiles) {
                    check(map.t(tx, ty) == T.TUNNEL, "터널 타일 아님 ${r.id} $d ($tx,$ty)=${map.t(tx, ty)}")
                }
                val start = when (d) {
                    Dir.N -> 19 to 1
                    Dir.S -> 19 to (map.h - 2)
                    Dir.W -> 1 to 15
                    Dir.E -> (map.w - 2) to 15
                }
                check(reach(map, start.first, start.second, 20, 15), "터널->플라자 경로 없음 ${r.id} $d")

                // 이정표: 타일 존재 + 4방향 중 하나 이상 걸을 수 있어야
                val (sx, sy) = when (d) {
                    Dir.N -> 22 to 1
                    Dir.S -> 22 to (map.h - 4)
                    Dir.W -> 2 to 13
                    Dir.E -> (map.w - 3) to 13
                }
                check(map.t(sx, sy) == T.SIGN, "이정표 없음 ${r.id} $d ($sx,$sy)=${map.t(sx, sy)}")
                check(!map.solidTile(sx + 1, sy) || !map.solidTile(sx - 1, sy) ||
                        !map.solidTile(sx, sy + 1) || !map.solidTile(sx, sy - 1),
                    "이정표가 완전히 막힘 ${r.id} $d ($sx,$sy)")
            }

            // 홈 지역: 집 문 & 경로
            if (home.id == r.id) {
                check(map.hasHouse, "집 없음 ${r.id}")
                check(map.t(map.houseDoorX, map.houseDoorY) == T.HOUSE_DOOR, "집 문 오류 ${r.id}")
                check(map.t(map.houseDoorX, map.houseDoorY + 1) == T.PLAZA, "집 문 아래가 플라자 아님 ${r.id} = ${map.t(map.houseDoorX, map.houseDoorY + 1)}")
                check(!map.solidTile(20, 12), "세로길 상단 막힘 ${r.id} (20,12)=${map.t(20, 12)}")
                check(reach(map, 19, 2, 20, 15), "북쪽 터널->플라자 경로 (집 배치 후) ${r.id}")
            } else {
                check(!map.hasHouse, "집 있으면 안 됨 ${r.id} (홈=${home.id})")
            }

            // 새 풀 (낮 기준 — 밤 풀이 비면 WorldScene이 낮 풀로 폴백)
            check(Birds.poolFor(r).isNotEmpty(), "새 풀 빈음 ${r.id}")

            // 지도 가장자리: 밖으로 나갈 수 없어야 함
            for (x in 0 until map.w) {
                check(map.solidTile(x, 0) || map.t(x, 0) == T.TUNNEL || map.t(x, 0) == T.SAND,
                    "상단 경계 뚫림 ${r.id} ($x,0)=${map.t(x, 0)}")
                check(map.solidTile(x, map.h - 1) || map.t(x, map.h - 1) == T.TUNNEL || map.t(x, map.h - 1) == T.SAND,
                    "하단 경계 뚫림 ${r.id} ($x,${map.h - 1})=${map.t(x, map.h - 1)}")
            }
            for (y in 0 until map.h) {
                check(map.solidTile(0, y) || map.t(0, y) == T.TUNNEL || map.t(0, y) == T.SAND,
                    "좌측 경계 뚫림 ${r.id} (0,$y)=${map.t(0, y)}")
                check(map.solidTile(map.w - 1, y) || map.t(map.w - 1, y) == T.TUNNEL || map.t(map.w - 1, y) == T.SAND,
                    "우측 경계 뚫림 ${r.id} (${map.w - 1},$y)=${map.t(map.w - 1, y)}")
            }
        }
    }

    // 4. 집 내부
    val hm = MapBuilder.buildHome()
    check(!hm.solidBox(100f, 96f), "집 스폰 막힘")
    check(hm.t(6, 8) == T.HOUSE_DOOR, "집 현관문 오류 (6,8)=${hm.t(6, 8)}")
    check(reach(hm, 6, 7, 6, 6), "집 내부 이동 불가")
    check(!hm.solidTile(9, 4) && !hm.solidTile(10, 4), "화덕 앞 막힘")
    check(!hm.solidTile(2, 3) && !hm.solidTile(3, 3), "침대 앞 막힘")
    check(!hm.solidTile(2, 5), "박스 앞 막힘")
    check(hm.t(9, 2) == T.OVEN, "화덕 위치 오류")
    check(hm.t(2, 2) == T.BED, "침대 위치 오류")
    check(hm.t(5, 2) == T.DECOR && hm.t(7, 2) == T.DECOR && hm.t(11, 5) == T.DECOR, "장식 칸 오류")
    check(hm.t(2, 1) == T.WALL_WIN && hm.t(5, 1) == T.WALL_WIN && hm.t(8, 1) == T.WALL_WIN, "창문 위치 오류")

    // 5. 게임 상태 로직 (v0.2: 토핑×품질)
    val gs = GameState()
    gs.reset("jeju")
    check(gs.started && gs.homeRegion == "jeju" && "jeju" in gs.visited, "reset 오류")
    check(gs.worldTime == 8.5f && !gs.isNight(), "초기 시각 오류")
    check(gs.decorSlots.all { it == -1 } && gs.decorLuck() == 0, "장식 초기화 오류")

    check(gs.addPizza(0, 2) && gs.addPizza(1, 1) && gs.addPizza(2, 0), "피자 추가 오류")
    check(gs.pizzaCountOf(0) == 1 && gs.pizzaCountOf(0, 2) == 1 && gs.pizzaCountOf(2, 0) == 1, "피자 개수 집계 오류")
    check(gs.pizzaCount == 3, "피자 총합 오류: ${gs.pizzaCount}")

    var capOk = true
    for (i in 0 until 10) if (!gs.addPizza(1, 0)) capOk = false
    check(!capOk, "피자 상한 작동 안 함 (항상 추가됨)")
    check(gs.pizzaCount == PIZZA_CAP, "피자 상한 개수 오류: ${gs.pizzaCount}")

    // 가장 좋은 품질부터 먹히는지 (버섯엔 품질1 + 품질0 재고 → '맛있는 피자' 먼저)
    val before = gs.pizzaCountOf(1)
    val eaten = gs.eat(1)
    check(eaten != null && eaten.label == "맛있는 피자", "토핑별 먹기 오류: ${eaten?.label}")
    check(gs.pizzaCountOf(1) == before - 1, "먹은 후 개수 오류: ${gs.pizzaCountOf(1)} != ${before - 1}")
    val anyEaten = gs.eatBest()
    check(anyEaten != null, "eatBest 실패 (재고 있는데 null)")
    check(gs.pizzaCount == PIZZA_CAP - 2, "eatBest 후 총 개수 오류: ${gs.pizzaCount}")

    // 시간 흐름: 24시간 순환
    gs.worldTime = 23.9f
    check(gs.isNight(), "23:54 밤 판정 오류")
    gs.worldTime = 12f
    check(!gs.isNight(), "낮 판정 오류")

    // 장식 행운
    gs.decorOwned.add(0); gs.decorOwned.add(4)
    gs.decorSlots[0] = 0; gs.decorSlots[1] = 4
    check(gs.decorLuck() == 4, "장식 행운 합산 오류: ${gs.decorLuck()}")

    check(gs.money == 0, "초기 돈 오류")

    // 6. JSON 직렬화 왕복 (org.json은 Android 런타임 필요 — 여기선 미실행)

    println(if (fails == 0) "OK: 모든 맵/로직 테스트 통과! (지역 ${Regions.ALL.size} x 홈 ${Regions.ALL.size} = ${Regions.ALL.size * Regions.ALL.size}개 맵 조합)" else "FAILURES: ${fails}건")
    if (fails > 0) kotlin.system.exitProcess(1)
}
