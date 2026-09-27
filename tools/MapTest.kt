import com.pizzaandbird.game.*

/**
 * 맵/데이터 로직 JVM 검증 스크립트 (Android 런타임 불필요).
 *
 * 사용:
 *   kotlinc -cp <android.jar>:<게임 클래스 출력> -d out tools/MapTest.kt
 *   java -cp <android.jar>:<게임 클래스>:out:<kotlin-stdlib.jar> MapTestKt
 */

// 광장 중심 — 사람 배치 검증의 출발점 (MapBuilder.PLAZA_* 와 같은 값)
private const val PLAZA_CX = 21
private const val PLAZA_CY = 15

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
    check(Regions.byId["incheon"]!!.waterEdges == setOf(Dir.W), "인천은 서해 쪽이어야 함")
    check(Regions.byId["gangneung"]!!.waterEdges == setOf(Dir.E), "강릉은 동해 쪽이어야 함")
    check(Regions.byId["jeju"]!!.waterEdges == setOf(Dir.N, Dir.E, Dir.S, Dir.W), "제주 해안 방향 오류")

    // 2.5 새 데이터: 낮 풀/밤 풀 기본 조건
    check(Birds.ALL.isNotEmpty(), "새 데이터 없음")
    check(Birds.ALL.any { it.active == "night" }, "밤새(active=night) 정의 없음")
    check(Birds.ALL.all { it.habitats.isNotEmpty() }, "서식지 없는 새 존재")

    // 2.55 캐스팅 북(NpcRoster) — 「한 사람은 한 장소에만」의 전제 조건
    run {
        val ids = NpcRoster.ALL.map { it.id }
        val dupId = ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        check(dupId.isEmpty(), "NPC id 중복: $dupId")
        val names = NpcRoster.ALL.map { it.name }
        val dupName = names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        check(dupName.isEmpty(), "NPC 이름 중복: $dupName")
        check(NpcRoster.ALL.count { it.kind == NpcKind.PROFESSOR } == 1, "보리 박사는 한 명이어야 한다")
        check(NpcRoster.ALL.count { it.kind == NpcKind.SHOP } == 1, "사진용품점은 한 곳이어야 한다")
        check(NpcRoster.byId[NpcRoster.professor.id]?.regionId == NpcRoster.PROFESSOR_REGION,
            "보리 박사 거주지 오류")
        check(NpcRoster.shopkeeper.regionId == NpcRoster.SHOP_REGION, "사진용품점 위치 오류")
        for (r in Regions.ALL) {
            val cast = NpcRoster.forRegion(r.id)
            check(cast.size >= 2, "사람이 2명 미만인 지역 ${r.id}: ${cast.size}명")
            check(cast.all { it.regionId == r.id }, "캐스팅 지역 id 오류 ${r.id}")
            check(cast.count { it.resident } == 1, "이웃 주민이 정확히 한 명이 아닌 지역 ${r.id}")
            check(cast.all { it.title.isNotBlank() }, "별명(직함)이 없는 사람이 있는 지역 ${r.id}")
            check(cast.all { it.lines.isNotEmpty() && it.lines.all { l -> l.isNotBlank() } },
                "대사가 없는 사람이 있는 지역 ${r.id}")
            // 같은 지역 안에서 옷차림이 같으면 사람 구별이 안 된다
            val looks = cast.map { listOf(it.look.hair, it.look.top, it.look.pants, it.look.pack) }
            check(looks.toSet().size == looks.size, "같은 지역 안에서 옷차림 중복 ${r.id}")
            val spots = cast.map { it.spot }
            check(spots.toSet().size == spots.size, "같은 지역 안에서 자리(${spots}) 중복 ${r.id}")
        }
    }

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

            // 지역 사람(NPC) — 캐스팅 북에 있는 그 지역 사람만, 걸어서 갈 수 있는 자리에 선다
            val cast = NpcRoster.forRegion(r.id)
            check(map.npcs.size == cast.size,
                "사람 수 불일치 ${r.id}(home=${home.id}): 캐스팅 ${cast.size}명 · 배치 ${map.npcs.size}명 " +
                    "(${cast.map { it.name } - map.npcs.map { it.name }})")
            check(map.npcs.map { it.person.id }.toSet().size == map.npcs.size, "사람 중복 배치 ${r.id}")
            for (n in map.npcs) {
                check(n.person.regionId == r.id,
                    "다른 지역 사람이 나타남 ${r.id}: ${n.name}(거주=${n.person.regionId})")
                check(!map.solidTile(n.tileX, n.tileY), "NPC 막힘 ${r.id} (${n.tileX},${n.tileY}) ${n.name}")
                check(!map.solidTile(n.tileX, n.tileY + 1), "NPC 앞 막힘 ${r.id} (${n.tileX},${n.tileY + 1}) ${n.name}")
                check(map.walkableTile(n.greetX, n.greetY),
                    "인사 자리 막힘 ${r.id} ${n.name} (${n.greetX},${n.greetY})=${map.t(n.greetX, n.greetY)}")
                check(!map.solidBox(n.greetX * 16f, n.greetY * 16f),
                    "인사 자리 박스 막힘 ${r.id} ${n.name} (${n.greetX},${n.greetY})")
                check(reach(map, (PLAZA_CX), (PLAZA_CY), n.tileX, n.tileY),
                    "걸어서 갈 수 없는 사람 ${r.id} ${n.name} (${n.tileX},${n.tileY}) 자리=${n.person.spot}")
                val gd = kotlin.math.hypot(
                    (n.greetX - n.tileX).toDouble(), (n.greetY - n.tileY).toDouble())
                check(gd <= 1.5, "인사 자리가 너무 멂 ${r.id} ${n.name} (${gd}칸)")
                for (m in map.npcs) {
                    if (m === n) continue
                    val d = kotlin.math.abs(m.tileX - n.tileX) + kotlin.math.abs(m.tileY - n.tileY)
                    check(d >= 3, "사람이 붙어 있음 ${r.id}: ${n.name}(${n.tileX},${n.tileY}) ↔ ${m.name}(${m.tileX},${m.tileY})")
                }
            }
            // 보리 박사·사진용품점은 자기 동네에만 있다
            for (n in map.npcs) {
                if (n.kind == NpcKind.PROFESSOR) {
                    check(r.id == NpcRoster.PROFESSOR_REGION, "다른 지역에 나타난 보리 박사 ${r.id}")
                }
                if (n.kind == NpcKind.SHOP) {
                    check(r.id == NpcRoster.SHOP_REGION, "다른 지역에 나타난 사진용품점 ${r.id}")
                }
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

    // 4.5 피자 데이터 (화덕피자 / 일반 피자)
    check(Pizzas.ALL.size == 12, "피자 종류 수 오류: ${Pizzas.ALL.size}")
    check(Pizzas.ALL.withIndex().all { (i, p) -> p.id == i }, "피자 id는 ALL 인덱스와 같아야 함 (세이브 호환)")
    check(Pizzas.ofKind(PizzaKind.OVEN).size == 6 && Pizzas.ofKind(PizzaKind.REGULAR).size == 6, "계열별 6종이어야 함")
    check(Pizzas.of(0).name == "치즈" && Pizzas.of(1).name == "버섯" && Pizzas.of(2).name == "불고기", "v0.2 토핑 id 호환 깨짐")
    check(Pizzas.ALL.all { it.difficulty in 1..5 && it.perfectW > 0f && it.cursorSpeed >= 1f }, "피자 난이도 데이터 오류")
    check(Pizzas.ALL.map { it.name }.toSet().size == Pizzas.ALL.size, "피자 이름 중복")
    check(Pizzas.representative(PizzaKind.OVEN).kind == PizzaKind.OVEN, "대표 화덕피자 오류")

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
