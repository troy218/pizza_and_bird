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

    // 2.1 지역 맵 팔레트와 실제 해안 방위가 구별되는지
    check(Regions.ALL.map { RegionMapStyles.forRegion(it).foliageFilter }.toSet().size >= 5,
        "지역별 풀 팔레트가 충분히 구별되지 않음")
    val natureStyles = Regions.ALL.map { RegionMapStyles.forRegion(it).natureArt }
    check(natureStyles.map { it.rocks }.toSet().size >= 10, "지역별 바위 실루엣 선택이 충분히 다르지 않음")
    check(natureStyles.map { it.trees }.toSet().size >= 10, "지역별 나무 종류 선택이 충분히 다르지 않음")
    for ((i, nature) in natureStyles.withIndex()) {
        check(nature.grass.all { it in 0..5 }, "잔디 변형 인덱스 오류 region=${Regions.ALL[i].id}")
        check(nature.tallGrass.all { it in 0..3 }, "억새 변형 인덱스 오류 region=${Regions.ALL[i].id}")
        check(nature.flowers.all { it in 0..5 }, "꽃 변형 인덱스 오류 region=${Regions.ALL[i].id}")
        check(nature.reeds.all { it in 0..3 }, "갈대 변형 인덱스 오류 region=${Regions.ALL[i].id}")
        check(nature.rocks.all { it in 0..8 }, "바위 변형 인덱스 오류 region=${Regions.ALL[i].id}")
        check(nature.mountains.all { it in 0..3 }, "산 변형 인덱스 오류 region=${Regions.ALL[i].id}")
        check(nature.trees.all { it in 0..11 }, "나무 변형 인덱스 오류 region=${Regions.ALL[i].id}")
    }
    check(Regions.byId["incheon"]!!.waterEdges == setOf(Dir.W), "인천은 서해 쪽이어야 함")
    check(Regions.byId["gangneung"]!!.waterEdges == setOf(Dir.E), "강릉은 동해 쪽이어야 함")
    check(Regions.byId["jeju"]!!.waterEdges == setOf(Dir.N, Dir.E, Dir.S, Dir.W), "제주 해안 방향 오류")

    // 2.5 새 데이터: 낮 풀/밤 풀 기본 조건
    check(Birds.ALL.isNotEmpty(), "새 데이터 없음")
    check(Birds.ALL.any { it.active == "night" }, "밤새(active=night) 정의 없음")
    check(Birds.ALL.all { it.habitats.isNotEmpty() }, "서식지 없는 새 존재")

    // 2.6 해안·호수·하천 타일은 지정한 지역 방향과 위치에 놓인다
    for (r in Regions.ALL) {
        val map = MapBuilder.build(r, START_REGION_ID)
        val style = RegionMapStyles.forRegion(r)
        for (edge in r.waterEdges) {
            val water = when (edge) {
                Dir.N -> 7 to 0
                Dir.E -> map.w - 1 to 7
                Dir.S -> 7 to map.h - 1
                Dir.W -> 0 to 7
            }
            check(map.groundAt(water.first, water.second) == T.WATER, "해안 방향 물 타일 없음 ${r.id} $edge")
            if (edge in r.sandEdges) {
                val beach = when (edge) {
                    Dir.N -> 7 to style.seaDepth
                    Dir.E -> map.w - 1 - style.seaDepth to 7
                    Dir.S -> 7 to map.h - 1 - style.seaDepth
                    Dir.W -> style.seaDepth to 7
                }
                check(map.groundAt(beach.first, beach.second) == T.SAND, "${r.id} $edge 해안의 모래·갯벌 띠 없음")
            }
        }
        style.lake?.let { lake ->
            check(map.groundAt(lake.center.x, lake.center.y) == T.WATER, "호수 위치 오류 ${r.id}")
        }
        for (river in style.rivers) {
            check(river.course.any { map.groundAt(it.x, it.y) == T.WATER }, "하천 경로 타일 없음 ${r.id}")
        }
    }

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
                    check(map.tunnelDirectionAt(tx, ty) == d, "터널 방향 표시 오류 ${r.id} $d ($tx,$ty)")
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

            // 길(포장면) 검증 ------------------------------------------------
            // (1) 포장면은 전부 하나로 이어져 있어야 한다 (섬처럼 떨어진 길 금지)
            val paved = ArrayList<Pair<Int, Int>>()
            for (y in 0 until map.h) for (x in 0 until map.w) {
                if (map.paveAt(x, y) != Pave.NONE) paved.add(x to y)
            }
            check(paved.isNotEmpty(), "길이 하나도 없음 ${r.id}")
            if (paved.isNotEmpty()) {
                val seenRoad = HashSet<Long>()
                val rq = ArrayDeque<Pair<Int, Int>>()
                rq.add(paved[0])
                seenRoad.add(paved[0].first.toLong() * 1000 + paved[0].second)
                while (rq.isNotEmpty()) {
                    val (x, y) = rq.removeFirst()
                    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= map.w || ny >= map.h) continue
                        if (map.paveAt(nx, ny) == Pave.NONE) continue
                        val k = nx.toLong() * 1000 + ny
                        if (k in seenRoad) continue
                        seenRoad.add(k)
                        rq.add(nx to ny)
                    }
                }
                check(seenRoad.size == paved.size,
                    "길이 끊겨 있음 ${r.id}: ${paved.size}칸 중 ${seenRoad.size}칸만 연결")
            }
            // (2) 구조물 위에는 길이 깔리면 안 된다
            for ((x, y) in paved) {
                check(!map.t(x, y).bulk, "구조물 위에 길 ${r.id} ($x,$y)=${map.t(x, y)}")
            }
            // (3) 터널 앞 진입로는 반드시 포장되어 있어야 한다 (길이 끊긴 채 터널만 뚫림 방지)
            for (d in Regions.exits(r.id).keys) {
                val foot = when (d) {
                    Dir.N -> listOf(19 to 1, 20 to 1)
                    Dir.S -> listOf(19 to map.h - 2, 20 to map.h - 2)
                    Dir.W -> listOf(1 to 15, 1 to 16)
                    Dir.E -> listOf(map.w - 2 to 15, map.w - 2 to 16)
                }
                for ((x, y) in foot) {
                    check(map.paveAt(x, y) != Pave.NONE, "터널 진입로가 길이 아님 ${r.id} $d ($x,$y)")
                }
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

    // 4. 집 내부 (v0.4: 16x12 확장 레이아웃 — 화덕 우상단, 가정용 오븐은 화덕 왼쪽)
    val hm = MapBuilder.buildHome()
    check(hm.w == 16 && hm.h == 12, "집 내부 16x12 오류: ${hm.w}x${hm.h}")
    check(!hm.solidBox(120f, 136f), "집 스폰 막힘")
    check(hm.t(7, 11) == T.HOUSE_DOOR && hm.t(8, 11) == T.HOUSE_DOOR, "집 현관문 오류 (7,11)=${hm.t(7, 11)}")
    check(reach(hm, 7, 10, 7, 9), "집 내부 이동 불가")
    check(!hm.solidTile(11, 4) && !hm.solidTile(12, 4), "화덕 앞 막힘")
    check(!hm.solidTile(2, 3) && !hm.solidTile(3, 3), "침대 앞 막힘")
    check(!hm.solidTile(2, 9), "박스 앞 막힘")
    check(hm.t(12, 2) == T.OVEN && hm.t(13, 3) == T.OVEN, "화덕 위치 오류")
    check(hm.t(11, 2) == T.RANGE_TOP && hm.t(11, 3) == T.RANGE, "가정용 오븐 위치 오류")
    check(!hm.solidTile(11, 4), "오븐 앞 막힘")
    check(hm.solidTile(11, 3) && hm.solidTile(11, 2), "오븐이 통과됨")
    check(hm.t(2, 2) == T.BED, "침대 위치 오류")
    check(hm.t(6, 2) == T.DECOR && hm.t(9, 2) == T.DECOR && hm.t(13, 7) == T.DECOR, "장식 칸 오류")
    check(hm.t(2, 1) == T.WALL_WIN && hm.t(6, 1) == T.WALL_WIN && hm.t(10, 1) == T.WALL_WIN && hm.t(13, 1) == T.WALL_WIN, "창문 위치 오류")

    // 4.5 피자 데이터 (화덕피자 / 일반 피자 + [P07] 지역 특산 8종)
    check(Pizzas.ALL.size == 20, "피자 종류 수 오류: ${Pizzas.ALL.size}")
    check(Pizzas.ALL.withIndex().all { (i, p) -> p.id == i }, "피자 id는 ALL 인덱스와 같아야 함 (세이브 호환)")
    check(Pizzas.ofKind(PizzaKind.OVEN).size == 14 && Pizzas.ofKind(PizzaKind.REGULAR).size == 6, "계열별 6/14종이어야 함")
    check(Pizzas.of(0).name == "치즈" && Pizzas.of(1).name == "버섯" && Pizzas.of(2).name == "불고기", "v0.2 토핑 id 호환 깨짐")
    // [P07] id 0~11 은 값까지 그대로여야 한다 (append-only, 규칙 4)
    val legacyNames = listOf("치즈", "버섯", "불고기", "페퍼로니", "고구마", "콤비네이션",
        "마르게리타", "마리나라", "콰트로 포르마지", "고르곤졸라", "디아볼라", "루꼴라 프로슈토")
    check((0..11).all { Pizzas.of(it).name == legacyNames[it] }, "[P07] 기존 피자 이름이 바뀌었다 (append-only 위반)")
    val legacySpeed = listOf(1.00f, 1.15f, 1.38f, 1.10f, 1.22f, 1.30f, 1.45f, 1.40f, 1.55f, 1.60f, 1.70f, 1.75f)
    check((0..11).all { Pizzas.of(it).cursorSpeed == legacySpeed[it] }, "[P07] 기존 피자 난이도가 바뀌었다")
    check(Pizzas.ALL.all { it.difficulty in 1..5 && it.perfectW > 0f && it.cursorSpeed >= 1f }, "피자 난이도 데이터 오류")
    check(Pizzas.ALL.map { it.name }.toSet().size == Pizzas.ALL.size, "피자 이름 중복")
    check(Pizzas.representative(PizzaKind.OVEN).kind == PizzaKind.OVEN, "대표 화덕피자 오류")

    // 4.6 [P07] 도우 & 지역 특산 재료
    check(Dough.values().size == 3 && Dough.CLASSIC.price == 0, "도우 3종/기본 무료 오류")
    check(Dough.values().all { it.gaugeSpeed in 0.8..1.2 && it.zoneScale in 0.85f..1.15f }, "도우 보정 범위 오류")
    check(Ingredients.TOPPINGS.size == 8, "특산 재료 8종 오류: ${Ingredients.TOPPINGS.size}")
    check(Ingredients.TOPPINGS.map { it.id }.toSet().size == 8, "특산 재료 id 중복")
    check(Ingredients.TOPPINGS.withIndex().all { (i, tp) ->
        tp.pizzaId == Ingredients.TOPPING_PIZZA_ID + i && Pizzas.of(tp.pizzaId).kind == PizzaKind.OVEN
    }, "[P07] 특산 재료 → 피자 id 12~19 매핑 오류")
    check(Ingredients.TOPPINGS.all { it.regionId in Regions.byId },
        "[P07] 특산 재료 지역 id 오류 (존재하지 않는 지역)")
    check(Ingredients.TOPPINGS.map { it.regionId }.toSet().size == 8, "특산 재료는 서로 다른 도시여야 함")
    check(Ingredients.TOPPINGS.all { it.price in 1000..6000 && it.hungerBonus > 0 && it.luckBonus > 0 }, "특산 재료 밸런스 오류")
    check(Ingredients.SPECIAL_PIZZA_IDS.all { Pizzas.of(it).cursorSpeed in 1.30f..1.60f && Pizzas.of(it).perfectW in 0.18f..0.24f },
        "[P07] 특산 피자 난이도 범위(속도 1.30~1.60, 걸작 폭 0.18~0.24) 오류")
    check(Ingredients.TOPPINGS.all { tp ->
        val p = tp.pizza
        p.hungerBonus == tp.hungerBonus && p.luckBonus == tp.luckBonus &&
            p.baseColor == tp.crustColors.first && p.topColorA == tp.crustColors.second
    }, "[P07] 특산 재료 수치/아이콘 색이 피자 정의와 어긋남")
    check(Ingredients.toppingsFor("chuncheon").size == 1 && Ingredients.toppingsFor("seoul").isEmpty(),
        "[P07] 지역별 특산 조회 오류")

    // 5. 게임 상태 로직 (v0.2: 토핑×품질 → v0.3: 피자 12종×품질)
    val gs = GameState()
    gs.reset("jeju") // 인자로 무엇을 넘겨도 새 게임은 서울에서 시작
    check(gs.started && gs.homeRegion == START_REGION_ID && START_REGION_ID in gs.visited, "서울 고정 시작 오류")
    check(gs.ownedHomes == linkedSetOf(START_REGION_ID), "초기 서울 집 소유 오류")
    check(HouseStyles.ALL.size == 4 && gs.houseStyleId == "cozy", "인테리어 초기화 오류")
    check(HousePrices.forRegion("seoul") > HousePrices.forRegion("jeonju"), "지역별 집값 데이터 오류")
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

    // 화덕피자 재고/계열 집계 + 세이브 왕복
    check(gs.pizzas.size == Pizzas.ALL.size * 3, "피자 배열 크기 오류: ${gs.pizzas.size}")
    for (i in gs.pizzas.indices) gs.pizzas[i] = 0
    check(gs.addPizza(9, 2) && gs.addPizza(11, 0) && gs.addPizza(3, 1), "화덕/일반 피자 추가 오류")
    check(gs.pizzaCountOfKind(PizzaKind.OVEN) == 2 && gs.pizzaCountOfKind(PizzaKind.REGULAR) == 1, "계열별 집계 오류")
    val bestId = gs.eatBest()
    check(bestId == 9, "eatBest는 걸작(고르곤졸라)부터 먹어야 함: $bestId")
    // 세이브 왕복 + v2/v3 세이브(9칸) 마이그레이션 — 실제 org.json 구현이 클래스패스에 있을 때만 검사
    try {
        val roundTrip = GameState.fromJSON(gs.toJSON())
        check(roundTrip.pizzas.contentEquals(gs.pizzas), "피자 세이브 왕복 오류")
        val legacy = gs.toJSON()
        legacy.put("v", 3)
        legacy.put("pizzas", org.json.JSONArray(listOf(1, 0, 2, 0, 0, 0, 0, 3, 0)))
        val migrated = GameState.fromJSON(legacy)
        check(migrated.pizzaCountOf(0) == 3 && migrated.pizzaCountOf(2, 1) == 3 && migrated.pizzaCountOfKind(PizzaKind.OVEN) == 0,
            "v3 세이브 피자 마이그레이션 오류")
        // [P07] 업데이트 호환: 피자 12종 시절(v4, 36칸) 세이브 → 20종(60칸)으로 읽어도 재고가 그대로여야 한다
        val v4 = gs.toJSON()
        v4.put("v", 4)
        v4.put("pizzas", org.json.JSONArray((0 until 36).map { if (it == 9 * 3 + 2) 4 else if (it == 2) 1 else 0 }))
        val updated = GameState.fromJSON(v4)
        check(updated.pizzas.size == Pizzas.ALL.size * 3 && updated.pizzaCountOf(0, 2) == 1 && updated.pizzaCountOf(9, 2) == 4,
            "[P07] 기존 세이브(12종) 피자 재고 보존 오류")
        check((12..19).all { updated.pizzaCountOf(it) == 0 }, "[P07] 신규 특산 피자 재고가 0이 아님")
    } catch (e: RuntimeException) {
        println("SKIP: JSON 검사 생략 (android.jar 스텁) — ${e.message}")
    }

    // 시간 흐름: 24시간 순환
    gs.worldTime = 23.9f
    check(gs.isNight(), "23:54 밤 판정 오류")
    gs.worldTime = 12f
    check(!gs.isNight(), "낮 판정 오류")

    // 장식 행운
    gs.decorOwned.add(0); gs.decorOwned.add(4)
    gs.decorSlots[0] = 0; gs.decorSlots[1] = 4
    check(gs.decorLuck() == 4, "장식 행운 합산 오류: ${gs.decorLuck()}")

    check(gs.money == 30000 && won(gs.money) == "₩30,000", "초기 원화 오류")

    // 6. JSON 직렬화 왕복 (org.json은 Android 런타임 필요 — 여기선 미실행)

    println(if (fails == 0) "OK: 모든 맵/로직 테스트 통과! (지역 ${Regions.ALL.size} x 홈 ${Regions.ALL.size} = ${Regions.ALL.size * Regions.ALL.size}개 맵 조합)" else "FAILURES: ${fails}건")
    if (fails > 0) kotlin.system.exitProcess(1)
}
