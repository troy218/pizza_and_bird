package com.pizzaandbird.game

import android.graphics.PointF
import org.json.JSONObject
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot

/** GameMap.feetTile은 지형 종류(T)를 반환한다. 경로 탐색에는 발 위치의 격자 좌표가 필요하다. */
private fun feetTileCoords(px: Float, py: Float): Pair<Int, Int> =
    ((px + 8f) / 16f).toInt() to ((py + 13f) / 16f).toInt()

/** 퀘스트가 실제로 해결되는 장소의 종류. */
enum class QuestTargetKind {
    PERSON,
    BIRDING_SPOT,
    REGION_CENTER,
    HOME_OVEN
}

/** 씬 전환(터널 포함) 뒤에도 이어지는 자전거 길안내 목표. */
data class QuestTravelPlan(
    val questId: String,
    val targetRegionId: String,
    val targetKind: QuestTargetKind,
    val targetKey: String,
    val targetLabel: String,
    val objective: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("questId", questId)
        put("targetRegionId", targetRegionId)
        put("targetKind", targetKind.name)
        put("targetKey", targetKey)
        put("targetLabel", targetLabel)
        put("objective", objective)
    }

    companion object {
        fun fromJson(json: JSONObject): QuestTravelPlan? {
            val region = json.optString("targetRegionId", "")
            if (region !in Regions.byId) return null
            val kind = runCatching {
                QuestTargetKind.valueOf(json.optString("targetKind", "BIRDING_SPOT"))
            }.getOrDefault(QuestTargetKind.BIRDING_SPOT)
            return QuestTravelPlan(
                questId = json.optString("questId", ""),
                targetRegionId = region,
                targetKind = kind,
                targetKey = json.optString("targetKey", ""),
                targetLabel = json.optString("targetLabel", Regions.byId[region]?.name ?: region),
                objective = json.optString("objective", "퀘스트 목표 장소")
            )
        }
    }
}

data class QuestTrackerView(
    val id: String,
    val title: String,
    val requirement: String,
    val progress: String
)

/**
 * 퀘스트 목적지를 고르고 실제 맵을 걸어서/자전거로 따라갈 수 있는 경로를 만든다.
 * 지역 사이 이동은 지역 그래프의 최단 터널 경로를 타고, 각 지역 안에서는 타일 맵 A*로
 * 최단 경로를 찾는다. 좌표를 씬 생성자로 넘겨 스폰시키는 순간이동 경로는 사용하지 않는다.
 */
object QuestNavigation {
    private val habitatKeys = setOf("city", "forest", "field", "water", "wetland", "coast", "mountain")

    fun startQuest(game: Game, scene: Scene, quest: QuestData) {
        val s = game.state
        s.trackedQuestId = quest.id
        val region = chooseRegion(s, quest.category, quest.targetKey, quest.targetRegionId)
        val isOven = quest.category == QuestCategory.PIZZA_DELIVERY
        val destinationRegion = if (isOven) s.homeRegion else region.id
        val kind = if (isOven) QuestTargetKind.HOME_OVEN else QuestTargetKind.BIRDING_SPOT
        val habitats = if (isOven) emptyList() else relevantHabitats(s, region, quest.category, quest.targetKey)
        val targetName = if (isOven) "우리 집 화덕" else "${region.name} ${habitatName(habitats)}"
        start(
            game, scene,
            QuestTravelPlan(
                questId = quest.id,
                targetRegionId = destinationRegion,
                targetKind = kind,
                targetKey = habitats.joinToString(","),
                targetLabel = targetName,
                objective = requirementText(quest)
            )
        )
    }

    fun startLegacyBirdTrip(game: Game, scene: Scene, birdId: String) {
        val state = game.state
        val bird = Birds.byId[birdId] ?: return
        val region = chooseRegion(state, QuestCategory.BIRD_SPECIES, birdId)
        val habitats = relevantHabitats(state, region, QuestCategory.BIRD_SPECIES, birdId)
        start(
            game, scene,
            QuestTravelPlan(
                questId = "",
                targetRegionId = region.id,
                targetKind = QuestTargetKind.BIRDING_SPOT,
                targetKey = habitats.joinToString(","),
                targetLabel = "${region.name} ${habitatName(habitats)}",
                objective = "모을 새: ${bird.name} 사진"
            )
        )
    }

    fun startDailyQuest(game: Game, scene: Scene, quest: DailyQuestData) {
        val s = game.state
        s.trackedQuestId = quest.id
        val isOven = quest.category == QuestCategory.PIZZA_DELIVERY
        val region = if (isOven) Regions.byId[s.homeRegion] ?: Regions.ALL.first()
        else chooseRegion(s, quest.category, quest.targetKey, quest.targetRegionId)
        val habitats = if (isOven) emptyList() else relevantHabitats(s, region, quest.category, quest.targetKey)
        start(
            game, scene,
            QuestTravelPlan(
                questId = quest.id,
                targetRegionId = region.id,
                targetKind = if (isOven) QuestTargetKind.HOME_OVEN else QuestTargetKind.BIRDING_SPOT,
                targetKey = habitats.joinToString(","),
                targetLabel = if (isOven) "우리 집 화덕" else "${region.name} ${habitatName(habitats)}",
                objective = requirementText(quest)
            )
        )
    }

    /** 메인 퀘스트 카드/로그의 목적지: 필요한 새가 있는 서식지 또는 보고할 사람. */
    fun startMainQuest(game: Game, scene: Scene) {
        val s = game.state
        val chapter = MainStory.current(s) ?: return
        if (!s.mainQuestStarted || chapter.isComplete(s)) {
            startPersonTrip(game, scene, NpcRoster.professor, "main")
            return
        }
        val advice = MainQuestAdvisor.advise(s) ?: return
        val region = Regions.byId[advice.regionId] ?: Regions.byId[s.region] ?: Regions.ALL.first()
        val visitOnly = advice.reason.startsWith("방문 ")
        val missing = chapter.collectionDef()?.species?.filterNot { s.hasBirdName(it) }.orEmpty()
        val targetBirds = missing.mapNotNull { Birds.byName[it] }
            .filter { it in (Birds.poolFor(region, false) + Birds.poolFor(region, true)) }
        val targetKey = if (visitOnly) "" else {
            val hs = targetBirds.flatMap { it.habitats }.filter { it in region.habitats }.distinct()
            (hs.ifEmpty { region.habitats.toList() }).joinToString(",")
        }
        val targetKind = if (visitOnly) QuestTargetKind.REGION_CENTER else QuestTargetKind.BIRDING_SPOT
        start(
            game, scene,
            QuestTravelPlan(
                questId = "main",
                targetRegionId = region.id,
                targetKind = targetKind,
                targetKey = targetKey,
                targetLabel = if (visitOnly) "${region.name} 방문" else "${region.name} ${habitatName(targetKey.split(',').filter { it.isNotBlank() })}",
                objective = if (targetBirds.isNotEmpty()) "먼저 기록할 새: ${targetBirds.take(3).joinToString("·") { it.name }}" else advice.tip
            )
        )
    }

    fun startPersonTrip(game: Game, scene: Scene, person: NpcPerson, questId: String = "") {
        start(
            game, scene,
            QuestTravelPlan(
                questId = questId,
                targetRegionId = person.regionId,
                targetKind = QuestTargetKind.PERSON,
                targetKey = person.id,
                targetLabel = "${Regions.byId[person.regionId]?.name ?: person.regionId} ${person.spot.label} · ${person.name}",
                objective = "${person.name} 만나기"
            )
        )
    }

    fun startRegionTrip(game: Game, scene: Scene, regionId: String, reason: String = "지역 탐방") {
        val region = Regions.byId[regionId] ?: return
        start(
            game, scene,
            QuestTravelPlan(
                questId = "",
                targetRegionId = region.id,
                targetKind = QuestTargetKind.REGION_CENTER,
                targetKey = "",
                targetLabel = "${region.name} 중앙 광장",
                objective = reason
            )
        )
    }

    private fun start(game: Game, scene: Scene, plan: QuestTravelPlan) {
        if (scene is WorldScene) scene.prepareForQuestTravel()
        game.state.questTravelPlan = plan
        if (plan.questId.isNotBlank()) game.state.trackedQuestId = plan.questId
        scene.overlay?.finished = true
        game.toast("🚲 ${plan.targetLabel}까지 자전거로 출발!")
        game.sfx(Audio.Sfx.BIKE_BELL, 0.65f)
        SaveManager.save(game.context, game.state)
    }

    /** 수동 이동이 시작되면 자동 길안내를 정중히 취소한다. */
    fun cancel(game: Game, message: String? = null) {
        if (game.state.questTravelPlan == null) return
        game.state.questTravelPlan = null
        SaveManager.save(game.context, game.state)
        if (message != null) game.toast(message)
    }

    fun planFor(state: GameState): QuestTravelPlan? = state.questTravelPlan

    fun openTracker(scene: Scene) {
        val state = scene.game.state
        val tracker = tracker(state)
        val active = state.activeQuests.firstOrNull { it.id == tracker?.id && !it.completed }
        val daily = state.dailyQuests.firstOrNull { it.id == tracker?.id && !it.completed }
        val body = tracker?.let { "${it.requirement}\n\n진행: ${it.progress}" }
            ?: "진행 중인 퀘스트가 없어요. 게시판이나 일일 의뢰에서 새 목표를 골라 보세요."
        val choices = buildList {
            when {
                active != null -> add(DialogOverlay.Choice("🚲 선택한 의뢰 목표로 이동") {
                    startQuest(scene.game, scene, active)
                })
                tracker?.id == "legacy_bird" -> state.questBird?.let { birdId ->
                    add(DialogOverlay.Choice("🚲 새 서식지로 이동") {
                        startLegacyBirdTrip(scene.game, scene, birdId)
                    })
                }
                tracker?.id == "main" -> add(DialogOverlay.Choice("🚲 메인 목표로 이동") {
                    startMainQuest(scene.game, scene)
                })
                daily != null -> add(DialogOverlay.Choice("🚲 일일 의뢰 목표로 이동") {
                    startDailyQuest(scene.game, scene, daily)
                })
            }
            add(DialogOverlay.Choice("닫기"))
        }
        scene.openOverlay(DialogOverlay(scene, tracker?.title ?: "퀘스트", body, choices))
    }

    /**
     * HUD 퀘스트 카드를 눌렀을 때 — 목표를 **다시 읽어 보는 대신 곧바로 데려다준다.**
     *
     * 트래커가 고른 퀘스트(수락한 서브 의뢰 · 일일 미션 · 메인 장 · 예전 새 의뢰)에 맞춰
     * 자전거 길안내를 시작한다. 목적지가 집 화덕이면 실내에서 화덕까지 걸어가고,
     * 밖이면 지역 그래프를 따라 터널을 넘어간다. 안내할 목표가 없으면 false.
     */
    fun autoTravelTracked(game: Game, scene: Scene): Boolean {
        val s = game.state
        val tracker = tracker(s) ?: return false
        val active = s.activeQuests.firstOrNull { it.id == tracker.id && !it.completed }
        if (active != null) {
            startQuest(game, scene, active)
            return true
        }
        if (tracker.id == "legacy_bird") {
            val birdId = s.questBird ?: return false
            startLegacyBirdTrip(game, scene, birdId)
            return true
        }
        if (tracker.id == "main") {
            startMainQuest(game, scene)
            return true
        }
        val daily = s.dailyQuests.firstOrNull { it.id == tracker.id && !it.completed }
        if (daily != null) {
            startDailyQuest(game, scene, daily)
            return true
        }
        return false
    }

    fun nextRegionOnRoute(fromRegionId: String, targetRegionId: String): String? {
        val route = regionRoute(fromRegionId, targetRegionId) ?: return null
        return route.getOrNull(1)
    }

    private fun regionRoute(from: String, to: String): List<String>? {
        if (from !in Regions.byId || to !in Regions.byId) return null
        if (from == to) return listOf(from)
        val previous = HashMap<String, String>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(from)
        seen.add(from)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in Regions.exits(current).values) {
                if (!seen.add(next)) continue
                previous[next] = current
                if (next == to) {
                    val route = ArrayList<String>()
                    var at = to
                    route.add(at)
                    while (at != from) {
                        at = previous[at] ?: return null
                        route.add(at)
                    }
                    route.reverse()
                    return route
                }
                queue.add(next)
            }
        }
        return null
    }

    private fun regionDistances(from: String): Map<String, Int> {
        val distances = HashMap<String, Int>()
        if (from !in Regions.byId) return distances
        val queue = ArrayDeque<String>()
        distances[from] = 0
        queue.add(from)
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            for (next in Regions.exits(at).values) {
                if (next in distances) continue
                distances[next] = distances[at]!! + 1
                queue.add(next)
            }
        }
        return distances
    }

    private fun chooseRegion(
        state: GameState,
        category: QuestCategory,
        targetKey: String,
        preferredRegionId: String = ""
    ): RegionDef {
        fun eligible(region: RegionDef): Boolean {
            val pool = (Birds.poolFor(region, false) + Birds.poolFor(region, true)).distinctBy { it.id }
            return when (category) {
                QuestCategory.BIRD_SPECIES -> pool.any { it.id == targetKey }
                QuestCategory.HABITAT_SURVEY -> pool.any { targetKey in it.habitats }
                QuestCategory.NIGHT_EXPEDITION -> Birds.poolFor(region, true).isNotEmpty()
                QuestCategory.FAMILY_RESEARCH -> pool.any { it.name.contains(targetKey) }
                QuestCategory.LIFER_DISCOVERY -> pool.any { (state.birdCounts[it.id] ?: 0) == 0 }
                QuestCategory.PIZZA_DELIVERY -> region.id == state.homeRegion
                else -> pool.isNotEmpty()
            }
        }
        Regions.byId[preferredRegionId]?.takeIf(::eligible)?.let { return it }
        val distances = regionDistances(state.region)
        return Regions.ALL.asSequence()
            .filter(::eligible)
            .minWithOrNull(compareBy<RegionDef> { distances[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { candidateScore(state, it, category, targetKey) })
            ?: Regions.byId[state.region]
            ?: Regions.ALL.first()
    }

    private fun candidateScore(state: GameState, region: RegionDef, category: QuestCategory, targetKey: String): Int {
        val pool = Birds.poolFor(region, false) + Birds.poolFor(region, true)
        return when (category) {
            QuestCategory.BIRD_SPECIES -> if (pool.any { it.id == targetKey }) 100 else 0
            QuestCategory.HABITAT_SURVEY -> pool.count { targetKey in it.habitats }
            QuestCategory.NIGHT_EXPEDITION -> Birds.poolFor(region, true).size
            QuestCategory.FAMILY_RESEARCH -> pool.count { it.name.contains(targetKey) }
            QuestCategory.LIFER_DISCOVERY -> pool.count { (state.birdCounts[it.id] ?: 0) == 0 }
            else -> pool.size
        }
    }

    private fun relevantHabitats(
        state: GameState,
        region: RegionDef,
        category: QuestCategory,
        targetKey: String
    ): List<String> {
        val pool = (Birds.poolFor(region, false) + Birds.poolFor(region, true)).distinctBy { it.id }
        val relevant = when (category) {
            QuestCategory.BIRD_SPECIES -> pool.filter { it.id == targetKey }
            QuestCategory.HABITAT_SURVEY -> pool.filter { targetKey in it.habitats }
            QuestCategory.NIGHT_EXPEDITION -> Birds.poolFor(region, true)
            QuestCategory.FAMILY_RESEARCH -> pool.filter { it.name.contains(targetKey) }
            QuestCategory.LIFER_DISCOVERY -> pool.filter { (state.birdCounts[it.id] ?: 0) == 0 }
            else -> pool
        }
        val counts = HashMap<String, Int>()
        relevant.forEach { bird ->
            bird.habitats.filter { it in region.habitats }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val targetHabitat = targetKey.takeIf { it in habitatKeys && it in region.habitats }
        val ranked = counts.keys.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { it })
        return (listOfNotNull(targetHabitat) + ranked).distinct().ifEmpty { region.habitats.toList() }
    }

    private fun habitatName(habitats: List<String>): String {
        if (habitats.isEmpty()) return "탐조 길"
        val label = habitats.take(2).map { HabitatLabels[it] ?: it }.joinToString("·")
        return "$label 탐조 자리"
    }

    fun targetForWorld(map: GameMap, plan: QuestTravelPlan, startX: Float, startY: Float): PointF? {
        return when (plan.targetKind) {
            QuestTargetKind.PERSON -> {
                val person = map.npcs.firstOrNull { it.person.id == plan.targetKey } ?: return null
                PointF(person.greetX * 16f, person.greetY * 16f)
            }
            QuestTargetKind.REGION_CENTER -> PointF(20f * 16f, 15f * 16f)
            QuestTargetKind.HOME_OVEN -> {
                if (map.region.id != plan.targetRegionId || !map.hasHouse) return null
                PointF(map.houseDoorX * 16f, map.houseDoorY * 16f)
            }
            QuestTargetKind.BIRDING_SPOT -> bestHabitatSpot(map, startX, startY, plan.targetKey)
        }
    }

    private fun bestHabitatSpot(map: GameMap, startX: Float, startY: Float, rawHabitats: String): PointF? {
        val habitats = rawHabitats.split(',').map { it.trim() }.filter { it in habitatKeys }
            .ifEmpty { map.region.habitats.toList() }
        val startTile = feetTileCoords(startX, startY)
        val candidates = ArrayList<Triple<Float, Int, Int>>()
        for (y in 1 until map.h - 1) for (x in 1 until map.w - 1) {
            if (!standable(map, x, y)) continue
            val scenic = sceneryScore(map, x, y, habitats)
            val distance = abs(x - startTile.first) + abs(y - startTile.second)
            // 서식지에 잘 맞는 곳을 우선하되, 동점이면 가까운 곳으로 향한다.
            candidates.add(Triple(scenic * 4f - distance * 0.07f, x, y))
        }
        candidates.sortByDescending { it.first }
        for ((_, x, y) in candidates.take(90)) {
            val goalX = x * 16f
            val goalY = y * 16f - 1f
            if (QuestPathfinder.findPath(map, startX, startY, goalX, goalY) != null) {
                return PointF(goalX, goalY)
            }
        }
        val fallbackX = 20f * 16f
        val fallbackY = 15f * 16f
        return if (QuestPathfinder.findPath(map, startX, startY, fallbackX, fallbackY) != null) {
            PointF(fallbackX, fallbackY)
        } else null
    }

    private fun sceneryScore(map: GameMap, x: Int, y: Int, habitats: List<String>): Float {
        var trees = 0
        var tall = 0
        var flowers = 0
        var wet = 0
        var sand = 0
        var pave = 0
        var rocks = 0
        for (dy in -3..3) for (dx in -3..3) {
            val tx = x + dx
            val ty = y + dy
            if (tx !in 0 until map.w || ty !in 0 until map.h) continue
            when (map.t(tx, ty)) {
                T.TREE -> trees++
                T.TALLGRASS -> tall++
                T.FLOWER, T.REED -> flowers++
                T.ROCK, T.MOUNTAIN -> rocks++
                else -> {}
            }
            when (map.groundAt(tx, ty)) {
                T.WATER -> wet++
                T.SAND -> sand++
                T.TALLGRASS -> tall++
                T.FLOWER, T.REED -> flowers++
                else -> {}
            }
            if (map.paveAt(tx, ty) != Pave.NONE) pave++
        }
        fun score(key: String): Float = when (key) {
            "forest" -> trees * 1.2f + tall * 0.3f - pave * 0.12f
            "water", "wetland" -> wet * 1.0f + flowers * 0.25f - pave * 0.06f
            "coast" -> wet * 0.8f + sand * 1.2f + flowers * 0.15f
            "mountain" -> rocks * 1.0f + trees * 0.5f - pave * 0.1f
            "field" -> tall * 0.8f + flowers * 0.55f - trees * 0.12f
            "city" -> pave * 0.75f + flowers * 0.1f
            else -> 0f
        }
        return habitats.maxOfOrNull(::score) ?: 0f
    }

    private fun standable(map: GameMap, x: Int, y: Int): Boolean {
        if (x !in 0 until map.w || y !in 0 until map.h) return false
        val tile = map.t(x, y)
        if (tile == T.TUNNEL || tile == T.HOUSE_DOOR || tile == T.LANDMARK_DOOR) return false
        return !map.solidBox(x * 16f, y * 16f - 1f)
    }

    fun requirementText(quest: QuestData): String = requirementText(
        quest.category, quest.targetKey, quest.targetCount
    )

    fun requirementText(quest: DailyQuestData): String = requirementText(
        quest.category, quest.targetKey, quest.targetCount
    )

    private fun requirementText(category: QuestCategory, targetKey: String, count: Int): String {
        val amount = if (count > 1) " ${count}회" else ""
        return when (category) {
            QuestCategory.BIRD_SPECIES -> "모을 새: ${Birds.byId[targetKey]?.name ?: targetKey} 사진"
            QuestCategory.HABITAT_SURVEY -> "모을 새: ${HabitatLabels[targetKey] ?: targetKey} 서식 새 ${count}종"
            QuestCategory.STAR_QUALITY -> "모을 것: 3성 사진 ${count}장"
            QuestCategory.NIGHT_EXPEDITION -> "모을 새: 밤에 활동하는 새 ${count}종"
            QuestCategory.FAMILY_RESEARCH -> "모을 새: ${targetKey}과 사진${amount}"
            QuestCategory.WEATHER_EXPEDITION -> "모을 것: 비·눈 속 새 사진${amount}"
            QuestCategory.LIFER_DISCOVERY -> "모을 새: 도감에 없는 새 ${count}종"
            QuestCategory.IN_FLIGHT_ACTION -> "모을 것: 나는 새의 사진${amount}"
            QuestCategory.PIZZA_DELIVERY -> if (targetKey == "bake") "만들 것: 피자 1판 굽기" else "전달할 것: 따뜻한 피자"
        }
    }

    fun tracker(state: GameState): QuestTrackerView? {
        QuestManager.ensureDailyQuests(state)
        val chapter = MainStory.current(state)
        if (state.trackedQuestId == "main" && chapter != null) return mainTracker(state, chapter)

        val selectedActive = state.activeQuests.firstOrNull { it.id == state.trackedQuestId && !it.completed }
        if (selectedActive != null) {
            return QuestTrackerView(selectedActive.id, selectedActive.title, requirementText(selectedActive), selectedActive.progressText)
        }
        val selectedDaily = state.dailyQuests.firstOrNull { it.id == state.trackedQuestId && !it.completed }
        if (selectedDaily != null) {
            return QuestTrackerView(selectedDaily.id, selectedDaily.title, requirementText(selectedDaily), selectedDaily.progressText)
        }
        if (state.trackedQuestId != null && state.trackedQuestId != "main" && state.trackedQuestId != "legacy_bird") {
            state.trackedQuestId = null
        }
        val active = state.activeQuests.firstOrNull { !it.completed }
        if (active != null) {
            state.trackedQuestId = active.id
            return QuestTrackerView(active.id, active.title, requirementText(active), active.progressText)
        }
        val bird = state.questBird
        if (bird != null) {
            val name = Birds.byId[bird]?.name ?: "새"
            return QuestTrackerView("legacy_bird", "$name 사진 의뢰", "모을 새: $name 사진", "${if ((state.birdCounts[bird] ?: 0) > 0) 1 else 0}/1")
        }
        if (state.trackedQuestId == "legacy_bird") state.trackedQuestId = null
        if (chapter != null && state.trackedQuestId == null) return mainTracker(state, chapter)

        val daily = state.dailyQuests.firstOrNull { !it.completed }
        if (daily != null) {
            state.trackedQuestId = daily.id
            return QuestTrackerView(daily.id, daily.title, requirementText(daily), daily.progressText)
        }
        if (state.trackedQuestId != null) state.trackedQuestId = null
        return null
    }

    private fun mainTracker(state: GameState, chapter: MainStory.Chapter): QuestTrackerView {
        if (!state.mainQuestStarted) {
            return QuestTrackerView("main", chapter.title, "할 일: 보리 박사 만나기", "미시작")
        }
        val collection = chapter.collectionDef()
        val missing = collection?.species?.filterNot { state.hasBirdName(it) }.orEmpty()
        val requirement = if (missing.isNotEmpty()) {
            "남은 새: ${missing.take(4).joinToString("·")}" + if (missing.size > 4) " 외" else ""
        } else chapter.objective(state)
        return QuestTrackerView("main", chapter.title, requirement, chapter.objective(state))
    }

}

/** 타일 충돌 상자를 고려해 실내·실외 모두에 쓰는 가장 짧은 이동 경로. */
object QuestPathfinder {
    private data class OpenNode(val index: Int, val f: Float)
    private val offsets = arrayOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0,            1 to 0,
        -1 to 1,  0 to 1,  1 to 1
    )

    fun findPath(map: GameMap, startX: Float, startY: Float, targetX: Float, targetY: Float): List<PointF>? {
        val startTile = feetTileCoords(startX, startY)
        val goalTile = feetTileCoords(targetX, targetY)
        if (startTile.first !in 0 until map.w || startTile.second !in 0 until map.h) return null
        if (goalTile.first !in 0 until map.w || goalTile.second !in 0 until map.h) return null
        val size = map.w * map.h
        val start = startTile.second * map.w + startTile.first
        val goal = goalTile.second * map.w + goalTile.first
        val g = FloatArray(size) { Float.POSITIVE_INFINITY }
        val previous = IntArray(size) { -1 }
        val closed = BooleanArray(size)
        val open = PriorityQueue<OpenNode>(compareBy { it.f })
        g[start] = 0f
        open.add(OpenNode(start, heuristic(startTile.first, startTile.second, goalTile.first, goalTile.second)))

        fun allowed(x: Int, y: Int): Boolean {
            if (x !in 0 until map.w || y !in 0 until map.h) return false
            val isGoal = x == goalTile.first && y == goalTile.second
            val tile = map.t(x, y)
            if (!isGoal && (tile == T.TUNNEL || tile == T.HOUSE_DOOR || tile == T.LANDMARK_DOOR)) return false
            return !map.solidBox(x * 16f, y * 16f - 1f)
        }

        while (open.isNotEmpty()) {
            val current = open.remove().index
            if (closed[current]) continue
            if (current == goal) break
            closed[current] = true
            val cx = current % map.w
            val cy = current / map.w
            for ((dx, dy) in offsets) {
                val nx = cx + dx
                val ny = cy + dy
                if (!allowed(nx, ny)) continue
                if (dx != 0 && dy != 0) {
                    val sideX = cx + dx
                    val sideY = cy + dy
                    if (!allowed(sideX, cy) || !allowed(cx, sideY)) continue
                    // Movement applies horizontal and vertical deltas separately. Make sure
                    // either order stays on legal adjacent elevation steps, as moveBy() does.
                    if (!elevationStep(map, cx, cy, sideX, cy) ||
                        !elevationStep(map, sideX, cy, nx, ny) ||
                        !elevationStep(map, cx, cy, cx, sideY) ||
                        !elevationStep(map, cx, sideY, nx, ny)
                    ) continue
                } else if (!elevationStep(map, cx, cy, nx, ny)) {
                    continue
                }
                val ni = ny * map.w + nx
                if (closed[ni]) continue
                val step = if (dx == 0 || dy == 0) 1f else 1.4142135f
                val nextG = g[current] + step
                if (nextG >= g[ni]) continue
                g[ni] = nextG
                previous[ni] = current
                open.add(OpenNode(ni, nextG + heuristic(nx, ny, goalTile.first, goalTile.second)))
            }
        }
        if (start != goal && previous[goal] < 0) return null

        val nodes = ArrayList<Int>()
        var at = goal
        while (at != start && at >= 0) {
            nodes.add(at)
            at = previous[at]
        }
        nodes.reverse()
        val result = nodes.map { index ->
            PointF((index % map.w) * 16f, (index / map.w) * 16f - 1f)
        }.toMutableList()
        val last = result.lastOrNull()
        if (last == null || hypot(last.x - targetX, last.y - targetY) > 2f) {
            result.add(PointF(targetX, targetY))
        }
        return result
    }

    private fun elevationStep(map: GameMap, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean =
        abs(map.elevationAt(fromX, fromY) - map.elevationAt(toX, toY)) <= 1

    private fun heuristic(x: Int, y: Int, tx: Int, ty: Int): Float {
        val dx = abs(tx - x).toFloat()
        val dy = abs(ty - y).toFloat()
        val diagonal = minOf(dx, dy)
        return dx + dy - diagonal * (2f - 1.4142135f)
    }
}
