package com.pizzaandbird.game

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.max

/**
 * [P06] 업적과 누적 통계.
 *
 * 진행도는 기본 세이브 prefs의 `feat_stats_v1` 문자열 하나에 저장한다. GameState의
 * 저장 스키마는 바꾸지 않고, 이동은 실제 적용된 거리만 누적한다. 상태 파생 통계와
 * 사진집의 촬영 메타데이터는 1초 스로틀로 확인해 매 프레임 prefs를 쓰지 않는다.
 *
 * 이동 단위는 월드 논리 px이며, 10,000 px를 1 km로 환산한다(월드 px 1개 ≈ 10 cm).
 */
object Ach {
    data class Def(
        val id: String,
        val title: String,
        val desc: String,
        val icon: String,
        val hidden: Boolean = false
    )

    val ALL: List<Def> = listOf(
        Def("first_shot", "첫 셔터", "새 한 마리가 사진 속에 머물렀어요.", "📷"),
        Def("shot_100", "백 장의 계절", "어느새 백 번, 작은 날개를 기록했어요.", "🖼"),
        Def("shot_1000", "천 장의 지도", "천 장의 사진이 여행의 지도가 되었어요.", "🗺"),
        Def("star3_10", "초점의 장인", "열 종의 새에게 가장 좋은 순간을 남겼어요.", "✨"),
        Def("star3_50", "보이지 않는 순간", "쉰 종의 새가 선명한 기억으로 남았어요.", "🌠", hidden = true),
        Def("dex_100", "백 종의 이웃", "백 가지 날개와 인사를 나눴어요.", "🐦"),
        Def("dex_300", "날개의 수집가", "삼백 종이 이웃이 되었어요.", "🪶"),
        Def("dex_all", "한반도 598", "도감의 모든 이웃을 만났어요.", "🌈", hidden = true),
        Def("walk_marathon", "걸어서 마라톤", "발걸음이 마라톤 하나만큼 이어졌어요.", "👟"),
        Def("bike_1000k", "바퀴로 천 킬로", "자전거 바퀴가 천 킬로미터를 돌았어요.", "🚲"),
        Def("region_5", "다섯 고을 손님", "다섯 지역에서 반가운 풍경을 만났어요.", "🏘"),
        Def("region_all", "서른두 곳 지도", "모든 지역에 발자국을 남겼어요.", "🧭", hidden = true),
        Def("owl_friend", "부엉이의 친구", "밤하늘 아래 스무 장의 사진을 찍었어요.", "🦉"),
        Def("rain_day", "빗속의 망원경", "빗방울 사이로 열 장의 사진을 건졌어요.", "🌧"),
        Def("snow_day", "눈 속의 증거", "눈 오는 날, 세 별짜리 순간을 담았어요.", "❄️", hidden = true),
        Def("money_1m", "첫 백만장자", "모아 둔 용돈이 백만 원에 닿았어요.", "💰"),
        Def("money_10m", "동네 부자", "천만 원의 여유가 생겼어요.", "🪙"),
        Def("house_own", "두 번째 지붕", "두 지역에 돌아갈 집이 생겼어요.", "🏠"),
        Def("level_10", "어엿한 걸음", "탐조가로서 열 번째 성장을 맞았어요.", "🌱"),
        Def("level_25", "전설의 탐조가", "스물다섯 번째 레벨에 도착했어요.", "🏅", hidden = true),
        Def("pizza_100", "화덕의 벗", "피자를 백 판 이상 구워 낸 손목이에요.", "🍕"),
        Def("day_100", "백 번째 아침", "백 번째 아침에도 여행을 이어 갔어요.", "🌅"),
        Def("bike_first", "두 바퀴의 바람", "자전거로 첫 1킬로미터를 달렸어요.", "🚴"),
        Def("photo_all_regions", "한 장씩, 모든 고을", "모든 지역에서 한 장씩 추억을 남겼어요.", "📍", hidden = true),
        Def("play_10h", "오래 머문 오후", "이 세계에서 열 시간을 함께 보냈어요.", "☕"),
        Def("pizza_500", "마을의 화덕지기", "오백 판의 피자가 여행을 든든하게 했어요.", "🔥")
    )

    val total: Int get() = ALL.size

    /** OVERVIEW.md 공개 계약: 현재 저장소에서 해금된 업적 수. */
    fun unlocked(): Int = synchronized(this) { progress?.unlockedAt?.size ?: 0 }

    /** 상세 조회 API: 해금된 업적 ID 집합. */
    fun unlocked(ctx: Context): Set<String> = synchronized(this) {
        current(ctx, refreshFromPrefs = false).unlockedAt.keys.toSet()
    }

    fun isUnlocked(ctx: Context, id: String): Boolean = synchronized(this) {
        current(ctx, refreshFromPrefs = false).unlockedAt.containsKey(id)
    }

    fun unlockDay(ctx: Context, id: String): Int? = synchronized(this) {
        current(ctx, refreshFromPrefs = false).unlockedAt[id]
    }

    data class Stats(
        val distWalkPx: Long,
        val distBikePx: Long,
        val photos: Int,
        val discoveredSpecies: Int,
        val threeStarSpecies: Int,
        val visitedRegions: Int,
        val nightPhotos: Int,
        val rainPhotos: Int,
        val snowThreeStarPhotos: Int,
        val pizzasProduced: Int,
        val daysPlayed: Int,
        val maxDay: Int,
        val maxMoney: Int,
        val maxLevel: Int,
        val totalPlaySeconds: Long,
        val photosByRegion: Map<String, Int>,
        val unlockedCount: Int
    ) {
        val walkKm: Double get() = distWalkPx / PX_PER_KM
        val bikeKm: Double get() = distBikePx / PX_PER_KM
    }

    fun stats(ctx: Context): Stats = synchronized(this) {
        val p = current(ctx, refreshFromPrefs = false)
        Stats(
            distWalkPx = p.walkPx.toLong(),
            distBikePx = p.bikePx.toLong(),
            photos = p.maxPhotos,
            discoveredSpecies = p.maxSpecies,
            threeStarSpecies = p.maxThreeStarSpecies,
            visitedRegions = p.maxVisitedRegions,
            nightPhotos = p.nightPhotos,
            rainPhotos = p.rainPhotos,
            snowThreeStarPhotos = p.snowThreeStarPhotos,
            pizzasProduced = p.pizzasProduced,
            daysPlayed = p.daysPlayed,
            maxDay = p.maxDay,
            maxMoney = p.maxMoney,
            maxLevel = p.maxLevel,
            totalPlaySeconds = p.totalPlaySeconds,
            photosByRegion = p.photosByRegion.toMap(),
            unlockedCount = p.unlockedAt.size
        )
    }

    /**
     * Optional eager-load hook for a new world session. The normal WorldScene path initializes
     * lazily from [tick], so this does not require a third WorldScene integration point.
     */
    fun onGameStart(ctx: Context) {
        synchronized(this) { current(ctx, refreshFromPrefs = true) }
    }

    /**
     * WorldScene hook. It is intentionally throttled to one pass per second; the Game parameter
     * lets unlock notifications use the existing toast and reward sound without changing HUD APIs.
     */
    fun tick(scene: WorldScene, s: GameState) {
        val now = System.currentTimeMillis()
        val notifications = synchronized(this) {
            if (lastTickMs != Long.MIN_VALUE && now - lastTickMs < TICK_INTERVAL_MS) return
            lastTickMs = now
            val ctx = scene.game.context
            val p = current(ctx, refreshFromPrefs = true)
            val before = p.toJson()
            if (!p.initialized) initialize(p, s) else collectDeltas(p, s)
            collectStateMaxima(p, s)
            val unlockedNow = evaluate(p, s)
            if (p.toJson() != before) save(ctx, p)
            unlockedNow
        }
        if (notifications.isNotEmpty()) {
            val game = scene.game
            if (notifications.size == 1) {
                val def = byId[notifications[0]]
                if (def != null) game.toast("🏅 업적 해금: ${def.title}")
            } else {
                game.toast("🏅 업적 ${notifications.size}개를 새로 열었어요!")
            }
            game.sfx(Audio.Sfx.REWARD, 0.9f)
        }
    }

    /** Actual movement distance after collision resolution; called only when the player moves. */
    fun onMove(ctx: Context, movedPx: Float, bike: Boolean) {
        if (!movedPx.isFinite() || movedPx <= 0f) return
        synchronized(this) {
            val p = current(ctx, refreshFromPrefs = false)
            if (bike) p.bikePx += movedPx.toDouble() else p.walkPx += movedPx.toDouble()
        }
    }

    private data class Progress(
        var initialized: Boolean = false,
        var walkPx: Double = 0.0,
        var bikePx: Double = 0.0,
        var maxPhotos: Int = 0,
        var lastPhotos: Int = 0,
        var maxSpecies: Int = 0,
        var maxThreeStarSpecies: Int = 0,
        var maxVisitedRegions: Int = 0,
        var maxHomes: Int = 0,
        var maxMoney: Int = 0,
        var maxLevel: Int = 1,
        var maxDay: Int = 1,
        var daysPlayed: Int = 1,
        var totalPlaySeconds: Long = 0L,
        var lastPizzaInventory: Int = 0,
        var pizzasProduced: Int = 0,
        var nightPhotos: Int = 0,
        var rainPhotos: Int = 0,
        var snowThreeStarPhotos: Int = 0,
        val photosByRegion: LinkedHashMap<String, Int> = LinkedHashMap(),
        val unlockedAt: LinkedHashMap<String, Int> = LinkedHashMap()
    ) {
        fun toJson(): String {
            val regions = JSONObject()
            for ((id, count) in photosByRegion) regions.put(id, count)
            val unlocked = JSONObject()
            for ((id, day) in unlockedAt) unlocked.put(id, day)
            return JSONObject()
                .put("v", 1)
                .put("init", initialized)
                .put("walk", walkPx)
                .put("bike", bikePx)
                .put("photos", maxPhotos)
                .put("lastPhotos", lastPhotos)
                .put("species", maxSpecies)
                .put("threeStar", maxThreeStarSpecies)
                .put("regionsSeen", maxVisitedRegions)
                .put("homes", maxHomes)
                .put("money", maxMoney)
                .put("level", maxLevel)
                .put("day", maxDay)
                .put("daysPlayed", daysPlayed)
                .put("playSeconds", totalPlaySeconds)
                .put("lastPizza", lastPizzaInventory)
                .put("pizzas", pizzasProduced)
                .put("nightPhotos", nightPhotos)
                .put("rainPhotos", rainPhotos)
                .put("snow3Photos", snowThreeStarPhotos)
                .put("photosByRegion", regions)
                .put("unlocked", unlocked)
                .toString()
        }

        companion object {
            fun fromJson(raw: String?): Progress {
                if (raw.isNullOrBlank()) return Progress()
                return try {
                    val j = JSONObject(raw)
                    val p = Progress(
                        initialized = j.optBoolean("init", false),
                        walkPx = j.optDouble("walk", 0.0).coerceAtLeast(0.0),
                        bikePx = j.optDouble("bike", 0.0).coerceAtLeast(0.0),
                        maxPhotos = j.optInt("photos", 0).coerceAtLeast(0),
                        lastPhotos = j.optInt("lastPhotos", 0).coerceAtLeast(0),
                        maxSpecies = j.optInt("species", 0).coerceAtLeast(0),
                        maxThreeStarSpecies = j.optInt("threeStar", 0).coerceAtLeast(0),
                        maxVisitedRegions = j.optInt("regionsSeen", 0).coerceAtLeast(0),
                        maxHomes = j.optInt("homes", 0).coerceAtLeast(0),
                        maxMoney = j.optInt("money", 0).coerceAtLeast(0),
                        maxLevel = j.optInt("level", 1).coerceAtLeast(1),
                        maxDay = j.optInt("day", 1).coerceAtLeast(1),
                        daysPlayed = j.optInt("daysPlayed", 1).coerceAtLeast(1),
                        totalPlaySeconds = j.optLong("playSeconds", 0L).coerceAtLeast(0L),
                        lastPizzaInventory = j.optInt("lastPizza", 0).coerceAtLeast(0),
                        pizzasProduced = j.optInt("pizzas", 0).coerceAtLeast(0),
                        nightPhotos = j.optInt("nightPhotos", 0).coerceAtLeast(0),
                        rainPhotos = j.optInt("rainPhotos", 0).coerceAtLeast(0),
                        snowThreeStarPhotos = j.optInt("snow3Photos", 0).coerceAtLeast(0)
                    )
                    val regions = j.optJSONObject("photosByRegion")
                    if (regions != null) {
                        val it = regions.keys()
                        while (it.hasNext()) {
                            val id = it.next()
                            p.photosByRegion[id] = regions.optInt(id, 0).coerceAtLeast(0)
                        }
                    }
                    val unlocked = j.optJSONObject("unlocked")
                    if (unlocked != null) {
                        val it = unlocked.keys()
                        while (it.hasNext()) {
                            val id = it.next()
                            p.unlockedAt[id] = unlocked.optInt(id, 1).coerceAtLeast(1)
                        }
                    }
                    // 구 id 마이그레이션: 힐링 기념 "night_owl"과 겹치던 업적 id 변경
                    if ("night_owl" in p.unlockedAt && "owl_friend" !in p.unlockedAt) {
                        p.unlockedAt["owl_friend"] = p.unlockedAt.getValue("night_owl")
                    }
                    p.unlockedAt.remove("night_owl")
                    p
                } catch (_: Throwable) {
                    Progress()
                }
            }
        }
    }

    private var prefsCache: SharedPreferences? = null
    private var cachedJson: String? = null
    private var progress: Progress? = null
    private var lastTickMs = Long.MIN_VALUE

    private const val STORE_KEY = "feat_stats_v1"
    private const val TICK_INTERVAL_MS = 1_000L
    private const val PX_PER_KM = 10_000.0
    private const val WALK_MARATHON_PX = 421_950L
    private const val BIKE_THOUSAND_KM_PX = 10_000_000L
    private val byId: Map<String, Def> = ALL.associateBy { it.id }

    private fun current(ctx: Context, refreshFromPrefs: Boolean): Progress {
        val prefs = prefsCache ?: ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
            .also { prefsCache = it }
        if (progress == null) {
            val raw = prefs.getString(STORE_KEY, null)
            cachedJson = raw
            progress = Progress.fromJson(raw)
        } else if (refreshFromPrefs) {
            val raw = prefs.getString(STORE_KEY, null)
            if (raw != cachedJson) {
                cachedJson = raw
                progress = Progress.fromJson(raw)
                prefsCache = prefs
            }
        }
        return progress!!
    }

    private fun save(ctx: Context, p: Progress) {
        val prefs = prefsCache ?: ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
            .also { prefsCache = it }
        val raw = p.toJson()
        prefs.edit().putString(STORE_KEY, raw).apply()
        cachedJson = raw
        progress = p
    }

    private fun initialize(p: Progress, s: GameState) {
        p.initialized = true
        p.lastPhotos = s.photos.coerceAtLeast(0)
        p.lastPizzaInventory = s.pizzaCount.coerceAtLeast(0)
        p.pizzasProduced = max(p.pizzasProduced, p.lastPizzaInventory)
        // Seed the retained album once so an existing save starts with its visible history.
        for (record in s.photoAlbum) recordPhoto(p, record)
        collectStateMaxima(p, s)
    }

    private fun collectDeltas(p: Progress, s: GameState) {
        val photosNow = s.photos.coerceAtLeast(0)
        val photoDelta = (photosNow - p.lastPhotos).coerceAtLeast(0)
        if (photoDelta > 0) {
            val recent = s.photoAlbum.takeLast(photoDelta.coerceAtMost(s.photoAlbum.size))
            for (record in recent) recordPhoto(p, record)
        }
        p.lastPhotos = photosNow

        val pizzasNow = s.pizzaCount.coerceAtLeast(0)
        if (pizzasNow > p.lastPizzaInventory) {
            p.pizzasProduced += pizzasNow - p.lastPizzaInventory
        }
        p.lastPizzaInventory = pizzasNow
    }

    private fun recordPhoto(p: Progress, record: BirdPhotoRecord) {
        p.photosByRegion[record.regionId] = (p.photosByRegion[record.regionId] ?: 0) + 1
        if (record.time >= 19.5f || record.time < 4.5f) p.nightPhotos++
        if (record.weatherId == Weather.RAIN.id) p.rainPhotos++
        if (record.weatherId == Weather.SNOW.id && record.stars >= 3) p.snowThreeStarPhotos++
    }

    private fun collectStateMaxima(p: Progress, s: GameState) {
        p.maxPhotos = max(p.maxPhotos, s.photos.coerceAtLeast(0))
        p.maxSpecies = max(p.maxSpecies, s.birdCounts.values.count { it > 0 })
        p.maxThreeStarSpecies = max(p.maxThreeStarSpecies, s.bestStars.values.count { it >= 3 })
        p.maxVisitedRegions = max(p.maxVisitedRegions, s.visited.size)
        p.maxHomes = max(p.maxHomes, s.ownedHomes.size)
        p.maxMoney = max(p.maxMoney, s.money.coerceAtLeast(0))
        p.maxLevel = max(p.maxLevel, s.level.coerceAtLeast(1))
        p.maxDay = max(p.maxDay, s.day.coerceAtLeast(1))
        p.daysPlayed = max(p.daysPlayed, s.day.coerceAtLeast(1))
        p.totalPlaySeconds = max(p.totalPlaySeconds, s.playSeconds.toLong().coerceAtLeast(0L))
    }

    private fun evaluate(p: Progress, s: GameState): List<String> {
        val earned = ArrayList<String>()
        fun award(id: String, condition: Boolean) {
            if (condition && id !in p.unlockedAt) {
                p.unlockedAt[id] = s.day.coerceAtLeast(1)
                earned.add(id)
            }
        }
        val regions = Regions.ALL.map { it.id }.toSet()
        award("first_shot", p.maxPhotos >= 1)
        award("shot_100", p.maxPhotos >= 100)
        award("shot_1000", p.maxPhotos >= 1_000)
        award("star3_10", p.maxThreeStarSpecies >= 10)
        award("star3_50", p.maxThreeStarSpecies >= 50)
        award("dex_100", p.maxSpecies >= 100)
        award("dex_300", p.maxSpecies >= 300)
        award("dex_all", p.maxSpecies >= Birds.ALL.size)
        award("walk_marathon", p.walkPx >= WALK_MARATHON_PX)
        award("bike_1000k", p.bikePx >= BIKE_THOUSAND_KM_PX)
        award("region_5", p.maxVisitedRegions >= 5)
        award("region_all", p.maxVisitedRegions >= Regions.ALL.size)
        award("owl_friend", p.nightPhotos >= 20)
        award("rain_day", p.rainPhotos >= 10)
        award("snow_day", p.snowThreeStarPhotos >= 1)
        award("money_1m", p.maxMoney >= 1_000_000)
        award("money_10m", p.maxMoney >= 10_000_000)
        award("house_own", p.maxHomes >= 2)
        award("level_10", p.maxLevel >= 10)
        award("level_25", p.maxLevel >= 25)
        award("pizza_100", p.pizzasProduced >= 100)
        award("day_100", p.maxDay >= 100)
        award("bike_first", p.bikePx >= PX_PER_KM)
        award("photo_all_regions", p.photosByRegion.keys.containsAll(regions))
        award("play_10h", p.totalPlaySeconds >= 36_000L)
        award("pizza_500", p.pizzasProduced >= 500)
        return earned
    }
}
