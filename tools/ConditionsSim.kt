import com.pizzaandbird.game.*

/**
 * 계절 × 밤낮 × 날씨 조합별 "나타나는 새" 검증 시뮬레이션.
 *
 * WorldScene.trySpawnBird와 동일한 가중치 식
 *   spawnWeight × weatherBirdMultiplier(계절·밤낮 포함) × seasonBirdMultiplier × SpawnTables.weight
 * 을 풀 전체에 돌려 조건이 바뀔 때 출현 구성(상위 종·풀 크기)이 실제로 달라지는지 본다.
 *
 * 사용 (미리보기 스텁 + 게임 소스 + 이 파일을 함께 컴파일):
 *   kotlinc tools/preview/src .kt와 게임 소스, ConditionsSim.kt → -d simout -jvm-target 17
 *   java -cp simout:kotlin-stdlib.jar ConditionsSimKt
 */

private val REGION_IDS = listOf("cheorwon", "suncheon", "sihwa", "seoul", "jeju")

private fun isNightHour(hour: Float): Boolean = hour >= 19.5f || hour < 4.5f

private fun effWeight(
    def: BirdDef, region: RegionDef, day: Int, hour: Float, season: Season, weather: Weather
): Double =
    Birds.spawnWeight(def, region, day, hour) *
            weatherBirdMultiplier(def, weather, season, isNightHour(hour)) *
            seasonBirdMultiplier(def, season, weather) *
            SpawnTables.weight(def, region.id, region.habitats, season, isNightHour(hour))

private fun topOf(
    pool: List<BirdDef>, region: RegionDef, day: Int, hour: Float, season: Season, weather: Weather, n: Int
): List<Pair<BirdDef, Double>> {
    if (pool.isEmpty()) return emptyList()
    val ws = pool.map { effWeight(it, region, day, hour, season, weather) }
    val sum = ws.sum()
    return pool.zip(ws).sortedByDescending { it.second }.take(n).map { it.first to it.second / sum }
}

private fun fmt(list: List<Pair<BirdDef, Double>>): String =
    if (list.isEmpty()) "(빈 풀)" else list.joinToString(", ") { "${it.first.name} ${"%.0f".format(it.second * 100)}%" }

private var fails = 0
private fun check(cond: Boolean, msg: () -> String) {
    if (!cond) { println("FAIL: " + msg()); fails++ }
}

private fun seasonDay(s: Season): Int = when (s) {
    Season.SPRING -> 1
    Season.SUMMER -> 8
    Season.AUTUMN -> 15
    Season.WINTER -> 22
}

fun main() {
    println("### 조건별 출현 구성 — trySpawnBird 가중치식 그대로")
    println()

    // ---------------------------------------------------------------- 1) 시간대별 풀
    println("#### ① 시간대별 풀 (여름 10일차 기준)")
    for (rid in REGION_IDS) {
        val region = Regions.byId.getValue(rid)
        for ((label, hour) in listOf("새벽" to 6f, "낮" to 10f, "해질녘" to 18f, "밤" to 22f)) {
            val night = isNightHour(hour)
            val pool = Birds.poolFor(region, night = night, day = 10, hour = hour)
            val shown = pool.take(10).joinToString(", ") { it.name }
            println("- ${region.name} ${label}: ${pool.size}종 — $shown" + if (pool.size > 10) " …" else "")
        }
    }
    println()

    // ---------------------------------------------------------------- 2) 계절
    println("#### ② 계절 변화 (낮 10시 · 맑음)")
    for (rid in listOf("cheorwon", "suncheon", "jeju")) {
        val region = Regions.byId.getValue(rid)
        for (s in listOf(Season.SPRING, Season.SUMMER, Season.AUTUMN, Season.WINTER)) {
            val pool = Birds.poolFor(region, night = false, day = seasonDay(s), hour = 10f)
            val top = topOf(pool, region, seasonDay(s), 10f, s, Weather.SUNNY, 6)
            println("- ${region.name} ${s.label} (풀 ${pool.size}): ${fmt(top)}")
        }
        println()
    }

    // ---------------------------------------------------------------- 3) 날씨
    println("#### ③ 날씨 변화 (서울 · 봄 3일차 · 낮 10시)")
    run {
        val region = Regions.byId.getValue("seoul")
        val day = 3
        for (w in Weather.values()) {
            val pool = Birds.poolFor(region, night = false, day = day, hour = 10f)
            val top = topOf(pool, region, day, 10f, Season.SPRING, w, 6)
            println("- ${w.label}: ${fmt(top)}")
        }
    }
    println()
    println("#### ④ 날씨 변화 (순천만 · 여름 · 낮 10시)")
    run {
        val region = Regions.byId.getValue("suncheon")
        val day = 10
        for (w in Weather.values()) {
            val pool = Birds.poolFor(region, night = false, day = day, hour = 10f)
            val top = topOf(pool, region, day, 10f, Season.SUMMER, w, 6)
            println("- ${w.label}: ${fmt(top)}")
        }
    }
    println()

    // ---------------------------------------------------------------- 4) 밤 × 날씨
    println("#### ⑤ 밤 × 날씨 (서울 한강 · 여름 · 22시)")
    run {
        val region = Regions.byId.getValue("seoul")
        val day = 10
        val pool = Birds.poolFor(region, night = true, day = day, hour = 22f)
        for (w in Weather.values()) {
            val top = topOf(pool, region, day, 22f, Season.SUMMER, w, 6)
            println("- ${w.label} (풀 ${pool.size}): ${fmt(top)}")
        }
    }
    println()

    // ---------------------------------------------------------------- 5) 야행성 종 생존 여부
    println("#### ⑥ 야행성(밤 전용) 종이 실제로 나오는가 (여름 10일차)")
    val nightOnly = Birds.ALL.filter { it.active == "night" }
    for (def in nightOnly) {
        val regions = REGION_IDS.map { Regions.byId.getValue(it) }
            .filter { r -> def.habitats.intersect(r.habitats).isNotEmpty() && (def.onlyRegions == null || r.id in def.onlyRegions) }
        val duskHit = regions.any { r -> Birds.poolFor(r, night = false, day = 10, hour = 18f).any { it.id == def.id } }
        val nightHit = regions.any { r -> Birds.poolFor(r, night = true, day = 10, hour = 22f).any { it.id == def.id } }
        println("- ${def.name} (${def.id}): 해질녘=${if (duskHit) "O" else "X"} / 밤=${if (nightHit) "O" else "X"}" +
                if (!nightHit) "  ⚠ 밤에도 풀에 안 들어감" else "")
        check(nightHit) { "${def.name}: 서식지가 맞는 지역에서 밤 풀에 안 들어감" }
    }

    // ---------------------------------------------------------------- 6) 회귀 검증
    println()
    println("#### ⑦ 조건이 실제로 종 구성을 바꾸는가 (회귀 검증)")

    // (a) 계절: 겨울 철원 상위에 겨울철새가, 여름철 철원 상위에 여름철새가 있어야 한다
    run {
        val region = Regions.byId.getValue("cheorwon")
        val winterPool = Birds.poolFor(region, night = false, day = seasonDay(Season.WINTER), hour = 10f)
        val winterTop = topOf(winterPool, region, seasonDay(Season.WINTER), 10f, Season.WINTER, Weather.SUNNY, 8).map { it.first.id }
        check(winterTop.any { it in setOf("beangoose", "baikalteal", "tuftedduck", "spotduck") }) {
            "겨울 철원 상위8에 겨울 오리·기러기가 없음: $winterTop"
        }
        val summerPool = Birds.poolFor(region, night = false, day = seasonDay(Season.SUMMER), hour = 10f)
        val summerTop = topOf(summerPool, region, seasonDay(Season.SUMMER), 10f, Season.SUMMER, Weather.SUNNY, 8).map { it.first.id }
        check(summerTop.any { it in setOf("bird_hirundo_rustica", "bird_hirundo_daurica", "reedwarbler", "cuckoo", "pitta") }) {
            "여름 철원 상위8에 여름철새가 없음: $summerTop"
        }
    }

    // (b) 기후: 서울 비 상위엔 물새가, 강풍 상위엔 공중종이, 눈 상위엔 월동철새가 있어야 한다
    run {
        val region = Regions.byId.getValue("seoul")
        val day = 3
        val pool = Birds.poolFor(region, night = false, day = day, hour = 10f)
        fun topIds(w: Weather, season: Season) = topOf(pool, region, day, 10f, season, w, 8).map { it.first.id }
        val rainTop = topIds(Weather.RAIN, Season.SPRING)
        check(rainTop.any { Birds.byId[it]?.let { b -> "water" in b.habitats || "wetland" in b.habitats } == true }) {
            "서울 비 상위8에 물새가 없음: $rainTop"
        }
        val windTop = topIds(Weather.WIND, Season.SPRING)
        check(windTop.any { Birds.byId[it]?.art?.template == 12 || Birds.byId[it]?.art?.template == 3 }) {
            "서울 강풍 상위8에 공중종·맹금이 없음: $windTop"
        }
        val snowTop = topIds(Weather.SNOW, Season.WINTER)
        check(snowTop.any { Birds.byId[it]?.let { b -> residencyOf(b) == Residency.WINTER } == true }) {
            "서울(겨울) 눈 상위8에 월동철새가 없음: $snowTop"
        }
        // 비/눈/강풍이 한 조합에서 뚜렷이 다르도록 (상위 3종 겹침 비교)
        val sunnyTop = topIds(Weather.SUNNY, Season.SPRING).take(3)
        val rainTop3 = rainTop.take(3)
        check(sunnyTop.intersect(rainTop3).size < 3) {
            "맑음/비 상위3종이 완전히 동일 — 기후가 출현 구성을 바꾸지 못함: $sunnyTop vs $rainTop3"
        }
    }

    // (c) 밤: 순천만 밤 풀이 비지 않고 해오라기·야간 철새가 있어야 한다
    run {
        val region = Regions.byId.getValue("suncheon")
        val nightPool = Birds.poolFor(region, night = true, day = 10, hour = 22f)
        check(nightPool.isNotEmpty()) { "순천만 밤 풀이 비어 있음" }
        check(nightPool.any { it.id == "nightheron" }) {
            "순천만 밤 풀에 검은댕기해오라기 없음: " + nightPool.map { it.id }
        }
        check(nightPool.any { it.familyName in setOf("도요과", "물떼새과") }) {
            "순천만 밤 풀에 야간 통과 도요류 없음: " + nightPool.map { it.id }
        }
        // 비 오는 밤엔 부엉이류가 눈에 띄게 줄어야 한다 (숲이 있는 지역)
        val jeju = Regions.byId.getValue("jeju")
        val owlNight = Birds.poolFor(jeju, night = true, day = 10, hour = 22f).filter { it.orderName == "올빼미목" }
        check(owlNight.isNotEmpty()) { "제주 밤 풀에 올빼미목 없음" }
        val clearW = owlNight.map { weatherBirdMultiplier(it, Weather.SUNNY, Season.SUMMER, true) }.average()
        val rainyW = owlNight.map { weatherBirdMultiplier(it, Weather.RAIN, Season.SUMMER, true) }.average()
        check(rainyW < clearW * 0.6) { "비 올 밤 부엉이 배율이 충분히 안 떨어짐: 맑음 $clearW / 비 $rainyW" }
    }

    // (d) 밤 전용 종은 어떤 시간창에서도 배제되지 않는다 (낮에는 여전히 안 나온다)
    run {
        val region = Regions.byId.getValue("seoul")
        val dayPool = Birds.poolFor(region, night = false, day = 10, hour = 10f)
        check(dayPool.none { it.active == "night" }) { "낮 풀에 밤 전용 종이 섞임" }
    }

    // (e) 겨울 밤 철원 — 야간 이동하는 기러기·두루미 무리가 밤 풀에 있어야 한다
    run {
        val region = Regions.byId.getValue("cheorwon")
        val nightPool = Birds.poolFor(region, night = true, day = 22, hour = 22f)
        check(nightPool.any {
            it.familyName in setOf("기러기과", "백조과", "두루미과") || it.name.contains("두루미")
        }) { "겨울 밤 철원에 야간 이동 기러기·두루미 없음: " + nightPool.map { it.name } }
        println("- 겨울 밤 철원 풀 (${nightPool.size}종): " + nightPool.take(14).joinToString(", ") { it.name })
    }

    println()
    if (fails == 0) println("OK — 모든 조건 검증 통과") else println("FAIL: ${fails}건 실패")
}
