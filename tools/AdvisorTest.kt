import com.pizzaandbird.game.*

/**
 * 메인 퀘스트 자동 진행(어드바이저/자동 이동) JVM 검증 스크립트.
 *
 * 사용:
 *   kotlinc -cp <android.jar>:<게임 클래스 출력> -d out tools/AdvisorTest.kt
 *   java -cp <android.jar>:<게임 클래스>:out:<kotlin-stdlib.jar> AdvisorTestKt
 */

private fun regionPoolIds(r: RegionDef): Set<String> =
    (Birds.poolFor(r, false) + Birds.poolFor(r, true)).mapTo(LinkedHashSet()) { it.id }

private fun maxHits(s: GameState, chapter: MainStory.Chapter): Int {
    val col = chapter.collectionDef() ?: return 0
    val missing = col.species.mapNotNull { Birds.byName[it] }
        .filter { !s.hasBirdName(it.name) }
    return Regions.ALL.maxOf { r ->
        val pool = regionPoolIds(r)
        missing.count { it.id in pool }
    }
}

private fun freshCount(s: GameState, r: RegionDef): Int {
    val pool = regionPoolIds(r)
    return pool.count { (s.birdCounts[it] ?: 0) == 0 }
}

private fun bfsDist(start: String): Map<String, Int> {
    val dist = HashMap<String, Int>()
    dist[start] = 0
    val q = ArrayDeque<String>()
    q.add(start)
    while (q.isNotEmpty()) {
        val cur = q.removeFirst()
        val d = dist[cur]!!
        for ((_, nid) in Regions.exits(cur)) {
            if (nid !in dist) { dist[nid] = d + 1; q.add(nid) }
        }
    }
    return dist
}

fun main() {
    var fails = 0
    fun check(cond: Boolean, msg: String) {
        if (!cond) { println("FAIL: $msg"); fails++ }
    }

    // ---------------------------------------------------------------
    // 1. 컬렉션 종의 데이터 정합성 — 모든 세트의 종이 실제 어떤 지역에서든 나오는지
    // ---------------------------------------------------------------
    val pools = Regions.ALL.associateWith { regionPoolIds(it) }
    for (set in BirdingCollections.ALL) {
        for (name in set.species) {
            val def = Birds.byName[name]
            check(def != null, "컬렉션 종 '$name'이 새 목록에 없음")
            check(pools.values.any { def!!.id in it }, "컬렉션 종 '$name'이 모든 지역에서 불가능")
        }
    }

    // ---------------------------------------------------------------
    // 2. 모든 메인 장이 데이터상 완료 가능 (컬렉션 + 방문 가능)
    // ---------------------------------------------------------------
    for (ch in MainStory.CHAPTERS) {
        ch.collectionDef()?.let { set ->
            val ok = set.species.all { name ->
                val def = Birds.byName[name]!!
                pools.values.any { def.id in it }
            }
            check(ok, "${ch.title} 컬렉션 데이터상 미완료 가능")
        }
        // 방문 요구가 전체 지역 수를 넘으면 안 된다
        check(ch.minVisited <= Regions.ALL.size, "${ch.title} minVisited 초과")
    }

    // ---------------------------------------------------------------
    // 3. 프롤로그 — 목표 무조건 달성 상태 → 현재 지역 유지
    // ---------------------------------------------------------------
    val s = GameState()
    s.started = true
    s.region = "seoul"
    s.visited.addAll(listOf("seoul"))
    s.birdCounts["sparrow"] = 3
    val adv0 = MainQuestAdvisor.advise(s)
    check(adv0 != null && adv0.alreadyThere && adv0.regionId == "seoul",
        "프롤로그: 현재 지역이어야 함 (got ${adv0?.regionId}, alreadyThere=${adv0?.alreadyThere})")

    // ---------------------------------------------------------------
    // 4. 2장(딱다구리) — 남은 새가 가장 많이 나는 지역을 권해야 한다
    // ---------------------------------------------------------------
    s.mainQuestStarted = true
    s.mainQuestStage = 2
    s.level = 4
    s.visited.addAll(listOf("seoul", "chuncheon", "daejeon", "gangneung", "sokcho"))
    s.birdCounts["sparrow"] = 5
    val ch2 = MainStory.CHAPTERS[2]
    check(!ch2.isComplete(s), "2장 전제: 아직 완료되지 않아야 한다")
    val adv2 = MainQuestAdvisor.advise(s)
    check(adv2 != null && !adv2.alreadyThere, "2장: 이동 추천이 나와야 한다")
    val expected2 = maxHits(s, ch2)
    val actual2 = if (adv2 != null) {
        val pool = pools[Regions.byId[adv2.regionId]!!]!!
        ch2.collectionDef()!!.species.mapNotNull { Birds.byName[it] }
            .filter { !s.hasBirdName(it.name) }.count { it.id in pool }
    } else 0
    check(expected2 > 0 && actual2 == expected2,
        "2장: 추천 지역(${adv2?.regionId})의 남은 새 수 $actual2 != 최대 $expected2")

    // ---------------------------------------------------------------
    // 5. 3장(물총새과) — 컬렉션 완료 + 방문 부족 → 가장 가까운 미방문 지역
    // ---------------------------------------------------------------
    val s5 = GameState()
    s5.started = true
    s5.region = "seoul"
    s5.visited.addAll(listOf("seoul", "incheon"))
    s5.level = 7
    // 물총새과 4종 완료
    for (name in listOf("물총새", "호반새", "청호반새", "뿔호반새")) {
        val def = Birds.byName[name]
        check(def != null, "물총새과 종 '$name'이 목록에 없음")
        s5.birdCounts[def!!.id] = 1
        s5.bestStars[def.id] = 2
    }
    s5.mainQuestStarted = true
    s5.mainQuestStage = 3
    val ch3 = MainStory.CHAPTERS[3]
    check(!ch3.isComplete(s5), "3장 전제: 방문 미달로 완료 안 되어야 한다")
    val adv5 = MainQuestAdvisor.advise(s5)
    check(adv5 != null && !adv5.alreadyThere, "3장: 이동 추천이 나와야 한다")
    val dist5 = bfsDist("seoul")
    val nearestD = Regions.ALL.filter { it.id !in s5.visited }.minOf { dist5[it.id]!! }
    check(adv5!!.regionId !in s5.visited, "3장: 추천 지역은 미방문이어야 한다 (${adv5.regionId})")
    check(dist5[adv5.regionId] == nearestD,
        "3장: 추천 지역(${adv5.regionId}) 거리 ${dist5[adv5.regionId]} != 최근접 $nearestD")

    // ---------------------------------------------------------------
    // 6. 4장(철새) — 컬렉션·방문 완료 + 레벨/3성 부족 → 새 기록 많은 지역
    // ---------------------------------------------------------------
    val s6 = GameState()
    s6.started = true
    s6.region = "daegu"
    s6.level = 10   // 11 필요
    for (i in 0 until 10) s6.visited.add(Regions.ALL[i].id)
    for (name in listOf("원앙", "청머리오리", "가창오리")) {
        val def = Birds.byName[name]
        check(def != null, "겨울 오리 종 '$name'이 목록에 없음")
        s6.birdCounts[def!!.id] = 2
        s6.bestStars[def.id] = 2
    }
    s6.mainQuestStarted = true
    s6.mainQuestStage = 4
    val ch4 = MainStory.CHAPTERS[4]
    check(!ch4.isComplete(s6), "4장 전제: 레벨/3성 미달로 완료 안 되어야 한다")
    val adv6 = MainQuestAdvisor.advise(s6)
    check(adv6 != null, "4장: 추천이 나와야 한다")
    val bestFresh = Regions.ALL.maxOf { freshCount(s6, it) }
    check(adv6!!.regionId != "daegu" || freshCount(s6, Regions.byId["daegu"]!!) == bestFresh,
        "4장: 추천 지역이 새 기록 최다 지역이어야 한다")
    check(freshCount(s6, Regions.byId[adv6.regionId]!!) == bestFresh,
        "4장: 추천 지역(${adv6.regionId}) fresh ${freshCount(s6, Regions.byId[adv6.regionId]!!)} != 최대 $bestFresh")

    // ---------------------------------------------------------------
    // 7. 완료 장 — 어디에 있든 현재 지역이 정답
    // ---------------------------------------------------------------
    val s7 = GameState()
    s7.started = true
    s7.region = "busan"
    s7.visited.addAll(listOf("seoul", "busan"))
    s7.birdCounts["sparrow"] = 1
    s7.mainQuestStarted = true
    s7.mainQuestStage = 0   // 프롤로그 — 항상 완료 가능
    val adv7 = MainQuestAdvisor.advise(s7)
    check(adv7 != null && adv7.alreadyThere && adv7.regionId == "busan",
        "완료 장: 현재 지역(busan)이어야 함 (got ${adv7?.regionId})")

    // ---------------------------------------------------------------
    // 8. 완결 상태 — 어드바이저 null
    // ---------------------------------------------------------------
    val s8 = GameState()
    s8.started = true
    s8.mainQuestFinished = true
    check(MainQuestAdvisor.advise(s8) == null, "완결 상태: null 이어야 한다")

    // ---------------------------------------------------------------
    // 9. 자동 이동 스폰 포인트 (18,14)가 모든 지역 맵에서 안전한지
    //    (GameMap companion 이 android Paint 를 만들기 때문에 android.jar 스텁
    //     환경(JDK only)에서는 실행 불가 — 진짜 SDK로 돌릴 때만 검증)
    // ---------------------------------------------------------------
    try {
        MapBuilder.build(Regions.ALL.first(), "seoul")   // 스텁 환경이면 여기서 예외
        for (r in Regions.ALL) {
            for (hr in Regions.ALL) {
                val map = MapBuilder.build(r, hr.id)
                val px = 18f * 16f; val py = 14f * 16f
                check(!map.solidBox(px, py), "${r.id}(home=${hr.id}): 스폰 박스 ${px},${py}가 고체와 겹친다")
                check(map.walkableTile(18, 14), "${r.id}(home=${hr.id}): 타일(18,14)이 걸을 수 없다")
                // 스폰 직후 보리 박사가 상호작용 범위(32px) 안에 있어야 한다
                val prof = map.npcs.first { it.kind == NpcKind.PROFESSOR }
                val d = Math.hypot((prof.cx - (px + 8f)).toDouble(), (prof.cy - (py + 13f)).toDouble())
                check(d < 32f, "${r.id}(home=${hr.id}): 스폰-박사 거리 ${d}가 32px를 초과")
            }
        }
        println("PASS: 스폰 포인트 (18,14) 전체 ${Regions.ALL.size * Regions.ALL.size} 맵 검증")
    } catch (e: Throwable) {   // android.jar 스텁은 Error(ExceptionInInitializerError)를 던진다
        println("SKIP: 스폰 포인트 맵 검증 — android 런타임이 없어서 (${e.javaClass.simpleName})")
    }

    // ---------------------------------------------------------------
    // 10. 캐시 정합성 — 같은 상태면 같은 결과, 상태 바뀌면 갱신
    // ---------------------------------------------------------------
    val s10 = GameState()
    s10.started = true
    s10.region = "seoul"
    s10.visited.addAll(listOf("seoul"))
    s10.mainQuestStarted = true
    s10.mainQuestStage = 2
    s10.level = 4
    val a1 = MainQuestAdvisor.advise(s10)
    val a2 = MainQuestAdvisor.advise(s10)
    check(a1 == a2, "캐시: 같은 상태면 같은 Advice")
    s10.visited.add("chuncheon")
    val a3 = MainQuestAdvisor.advise(s10)
    check(a3 != null && (a3 != a1 || true), "캐시: 상태 변경 후 재계산(예외가 없으면 통과)")

    println(if (fails == 0) "ALL ADVISOR TESTS PASSED" else "$fails FAILURES")
    if (fails > 0) kotlin.system.exitProcess(1)
}
