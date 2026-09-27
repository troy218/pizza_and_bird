import com.pizzaandbird.game.*

/**
 * [P02] SpawnTables 검증 시뮬레이션 (계획서 P02_spawn_tables.md §6.3).
 *
 * 지역 5곳(철원 평야·순천만·시화호·서울·제주) × 4계절로
 * SpawnTables.weight를 풀 전체에 돌려:
 *   1) 평균 배율(= 스폰 가중치 총합 비)이 0.5~2.0 범위인지
 *   2) 계절 피크가 §5 표 의도와 일치하는지 (예: 겨울 철원 상위에 두루미)
 *   3) 출현 불가(가중치 0) 조합이 있는지 (클램프상 최소 0.05여야 함)
 * 를 표로 출력한다.
 *
 * 사용:
 *   kotlinc -cp <android.jar>:<게임 클래스 출력> -d simout tools/SpawnSim.kt
 *   java -cp <게임 클래스>:simout:<android.jar>:<kotlin-stdlib.jar> SpawnSimKt
 */

private val REGION_IDS = listOf("cheorwon", "suncheon", "sihwa", "seoul", "jeju")
private val SEASONS = listOf(Season.SPRING, Season.SUMMER, Season.AUTUMN, Season.WINTER)

fun main() {
    var fails = 0
    fun check(cond: Boolean, msg: () -> String) {
        if (!cond) { println("FAIL: " + msg()); fails++ }
    }

    println("### P02 SpawnTables 검증 — 지역 5곳 × 4계절")
    println()
    println("| 지역 | 계절 | 풀 크기 | 평균 배율 | 최소 배율 | 최대 배율 | 0 배율 수 |")
    println("|---|---|---:|---:|---:|---:|---:|")

    for (rid in REGION_IDS) {
        val region = checkNotNull(Regions.byId[rid]) { "지역 없음: $rid" }
        for (s in SEASONS) {
            val pool = Birds.poolFor(region, night = false)
            check(pool.isNotEmpty()) { "$rid ${s.label}: 풀이 비어 있음" }
            val ws = pool.map { SpawnTables.weight(it, rid, region.habitats, s, false) }
            val mean = ws.average()
            val zeros = ws.count { it <= 0.0 }
            println("| ${region.name} | ${s.label} | ${pool.size} | %.2f | %.2f | %.2f | $zeros |".format(mean, ws.min(), ws.max()))
            check(zeros == 0) { "$rid ${s.label}: 가중치 0인 종 존재" }
            check(mean in 0.5..2.0) { "$rid ${s.label}: 평균 배율 %.2f — 0.5~2.0 범위 이탈".format(mean) }
        }
    }

    println()
    println("### 계절 피크 점검 (§5 표 의도)")

    // 1) 겨울 철원 — 두루미류가 최상위 배율에 포함되는가
    run {
        val region = Regions.byId.getValue("cheorwon")
        val ranked = Birds.poolFor(region, night = false)
            .map { it to SpawnTables.weight(it, region.id, region.habitats, Season.WINTER, false) }
            .sortedByDescending { it.second }
        val topIds = ranked.take(5).map { it.first.id }
        val craneTop = topIds.any { it in setOf("crane", "redcrown", "hoodedcrane") }
        println("- 겨울 철원 배율 상위5: " + ranked.take(5).joinToString(", ") { "${it.first.name}(${("%.2f").format(it.second)})" })
        check(craneTop) { "겨울 철원 상위5에 두루미류 없음: $topIds" }
        // 여름엔 희귀 플로어(0.25)를 때리면서도 겨울의 1/10 수준으로 눈에 띄게 줄어야 함
        val craneSummer = SpawnTables.weight(Birds.byId.getValue("crane"), region.id, region.habitats, Season.SUMMER, false)
        val craneWinter = SpawnTables.weight(Birds.byId.getValue("crane"), region.id, region.habitats, Season.WINTER, false)
        check(craneSummer <= 0.25 && craneWinter >= 4.0 * craneSummer) { "여름철 두루미 배율 이상: 여름 $craneSummer / 겨울 $craneWinter" }
        println("- 두루미 배율 계절 변화 (철원): " + SEASONS.joinToString(" → ") { s ->
            "${
                ("%.2f").format(SpawnTables.weight(Birds.byId.getValue("crane"), region.id, region.habitats, s, false))
            }"
        })
    }

    // 2) 봄·가을 갯벌 — 도요류 피크
    run {
        val region = Regions.byId.getValue("suncheon")
        for (s in listOf(Season.SPRING, Season.AUTUMN)) {
            val wader = SpawnTables.weight(Birds.byId.getValue("greatknot"), region.id, region.habitats, s, false)
            check(wader >= 1.5) { "$s 순천만 붉은어깨도요 배율 부족: $wader" }
        }
        val waderWinter = SpawnTables.weight(Birds.byId.getValue("greatknot"), region.id, region.habitats, Season.WINTER, false)
        check(waderWinter < 1.0) { "겨울 순천만 붉은어깨도요 배율이 피크와 비슷: $waderWinter" }
        println("- 순천만 붉은어깨도요: " + SEASONS.joinToString(" / ") { s ->
            "${
                s.label + " " + ("%.2f").format(
                    SpawnTables.weight(Birds.byId.getValue("greatknot"), region.id, region.habitats, s, false)
                )
            }"
        })
    }

    // 3) 숲 지역 — 팔색조(여름 번식조) 피크 / 서식지 밖 지역은 의도적으로 미출현
    run {
        val forest = Regions.byId.getValue("gwangneung")   // 광릉숲
        val su = SpawnTables.weight(Birds.byId.getValue("pitta"), forest.id, forest.habitats, Season.SUMMER, false)
        val wi = SpawnTables.weight(Birds.byId.getValue("pitta"), forest.id, forest.habitats, Season.WINTER, false)
        check(su >= 1.5 && wi <= 0.25) { "팔색조 계절 배율 이상 (광릉숲 여름 $su / 겨울 $wi)" }
        println("- 광릉숲 팔색조: 여름 %.2f / 겨울 %.2f (희귀 플로어 0.25)".format(su, wi))
        // 서울(도시·강·들판)엔 숲종 팔색조 → 서식지 불일치 ×0.2, 그리고 풀 자체에도 없다
        val seoul = Regions.byId.getValue("seoul")
        val seoulPitta = SpawnTables.weight(Birds.byId.getValue("pitta"), seoul.id, seoul.habitats, Season.SUMMER, false)
        val inSeoulPool = Birds.poolFor(seoul, night = false).any { it.id == "pitta" }
        check(seoulPitta < 1.0 && !inSeoulPool) { "서울 팔색조 서식지 불일치 동작 이상: $seoulPitta / inPool=$inSeoulPool" }
        println("- 서울 팔색조(서식지 불일치): 배율 %.2f · 지역 풀 포함=%s (실제 미출현)".format(seoulPitta, inSeoulPool))
    }

    // 4) 희귀·전설 클램프 — 절대 확률이 범위를 넘지 않는가
    run {
        for (rid in REGION_IDS) {
            val region = Regions.byId.getValue(rid)
            for (s in SEASONS) {
                for (def in Birds.ALL) {
                    val m = SpawnTables.weight(def, rid, region.habitats, s, false)
                    if (def.tier.star >= 3) check(m in 0.25..4.0) { "$rid ${def.id} $s 희귀 배율 $m" }
                    else check(m in 0.05..3.0) { "$rid ${def.id} $s 배율 $m" }
                }
            }
        }
        println("- 희귀(3★+) 0.25~4.0 / 그 외 0.05~3.0 클램프: 5지역 × 4계절 × 전 종 통과")
    }

    // 5) highlights — 계절·지역 대표 새가 비어 있지 않은가
    run {
        for (rid in REGION_IDS) {
            for (s in SEASONS) {
                val hs = SpawnTables.highlights(rid, s)
                check(hs.isNotEmpty()) { "highlights 비어 있음: $rid $s" }
            }
        }
        val winterCheorwon = SpawnTables.highlights("cheorwon", Season.WINTER).joinToString(", ") { it.name }
        println("- highlights(철원, 겨울): $winterCheorwon")
    }

    println()
    if (fails == 0) println("SpawnSim: ALL PASS ✅") else println("SpawnSim: $fails FAIL ❌")
    if (fails > 0) kotlin.system.exitProcess(1)
}
