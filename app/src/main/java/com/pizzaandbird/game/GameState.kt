package com.pizzaandbird.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 플레이어 진행 상황. 오프라인 저장(JSON in SharedPreferences).
 *
 * 세이브 형식 v4: 메인 스토리 진행도와 완료 상태를 추가했다.
 * v3: 인테리어 스타일·지역별 집 소유권과 탐조가 레벨/경험치/숙련 포인트/스킬을 추가했다.
 * v2 (v0.2.0): 피자 토핑/장식/낮밤 시각/최고 별점 추가.
 * v1~v3 세이브는 자동으로 마이그레이션된다. (없는 필드는 기본값)
 */
class GameState {

    var started = false            // 첫 집 선택 완료(=세이브 존재)
    var gender = "male"          // 플레이어 캐릭터: male / female
    var inHome = false             // 현재 집 안에 있는지
    var money = 0                  // 용돈(원)
    var hunger = 100f              // 배고픔 수치 (100 = 포만, 0 = 배고픔)
    var luck = 50f                 // 행운 수치 (높을수록 희귀새 출현)
    val pizzas = IntArray(9)       // [토핑id*3 + 품질] 피자 개수
    var cameraLevel = 1            // 카메라 등급 (1~5)
    val birdCounts = LinkedHashMap<String, Int>()   // 도감: 새별 촬영 횟수
    val bestStars = LinkedHashMap<String, Int>()    // 도감: 새별 최고 별점
    val visited = LinkedHashSet<String>()           // 방문한 지역
    val ownedHomes = LinkedHashSet<String>()        // 매입한 지역별 집
    val ownedHouseStyles = LinkedHashSet<String>()  // 구매한 인테리어 스타일

    var homeRegion = START_REGION_ID       // 집이 있는 지역
    var region = START_REGION_ID           // 현재 지역
    var houseStyleId = "cozy"              // 현재 집 인테리어
    var px = 0f                    // 월드 좌표(px)
    var py = 0f
    var onBike = false

    var questBird: String? = null  // 박사 사진 의뢰(서브퀘스트): 촬영할 새
    var questReward = 0

    // 메인 스토리. 사진 의뢰와 독립적이므로 어느 쪽이든 언제든 진행할 수 있다.
    var mainQuestStarted = false
    var mainQuestStage = 0
    var mainQuestFinished = false

    var playSeconds = 0f
    var photos = 0                 // 누적 촬영 장수
    var worldTime = 8.5f           // 게임 내 시각 (0.0~24.0, 8.5=오전 8시반)
    var day = 1                    // 게임 내 날짜 (자정을 넘기거나 잠들면 +1 — 날씨가 바뀐다)
    var weatherId = Weather.SUNNY.id // 게임 전체 날씨
    var weatherSeconds = 55f         // 다음 날씨 변화까지 남은 시간

    // 탐조가 성장 --------------------------------------------------------
    var level = 1                  // 캐릭터 레벨 (1~MAX_LEVEL)
    var exp = 0                    // 현재 레벨에서 쌓은 경험치
    var skillPoints = 0            // 사용 가능한 숙련 포인트(SP)
    val skills = LinkedHashMap<String, Int>()   // 스킬id -> 랭크

    var musicOn = true             // 설정: 배경 음악
    var sfxOn = true               // 설정: 효과음/환경음

    val decorSlots = IntArray(3) { -1 }   // 집 장식 칸 (장식id, -1=빈칸)
    val decorOwned = ArrayList<Int>()     // 소유한 장식 id 목록

    // ------------------------------------------------------------------

    val pizzaCount: Int get() = pizzas.sum()

    fun pizzaCountOf(topping: Int): Int {
        var n = 0
        for (q in 0 until 3) n += pizzas[topping.coerceIn(0, 2) * 3 + q]
        return n
    }

    fun pizzaCountOf(topping: Int, quality: Int): Int =
        pizzas[topping.coerceIn(0, 2) * 3 + quality.coerceIn(0, 2)]

    fun addPizza(topping: Int, quality: Int): Boolean {
        if (pizzaCount >= pizzaCapEff()) return false
        pizzas[topping.coerceIn(0, 2) * 3 + quality.coerceIn(0, 2)]++
        return true
    }

    /** 특정 토핑의 가장 좋은 피자 먹기 (없으면 null) */
    fun eat(toppingId: Int): PizzaQ? {
        val t = Toppings.of(toppingId)
        for (q in 2 downTo 0) {
            val idx = t.id * 3 + q
            if (pizzas[idx] > 0) {
                pizzas[idx]--
                val def = PizzaQ.of(q)
                hunger = (hunger + def.hunger + t.hungerBonus).coerceIn(0f, 100f)
                luck = (luck + def.luck + t.luckBonus).coerceIn(0f, 100f)
                return def
            }
        }
        return null
    }

    /** 아무 토핑이나 가장 좋은 피자 먹기 (먹은 토핑 id 반환, 없으면 null) */
    fun eatBest(): Int? {
        for (q in 2 downTo 0) {
            for (t in Toppings.ALL) {
                if (pizzas[t.id * 3 + q] > 0) {
                    eat(t.id)
                    return t.id
                }
            }
        }
        return null
    }

    // ------------------ 탐조가 성장 ------------------

    /** 현재 레벨에서 다음 레벨까지 필요한 경험치 */
    fun expToNext(): Int = Progression.expToNext(level)

    /** 다음 레벨까지의 진행도 (0~1) */
    fun expProgress(): Float {
        val need = expToNext()
        return if (need <= 0) 1f else (exp.toFloat() / need).coerceIn(0f, 1f)
    }

    fun title(): String = Progression.title(level)

    fun gearTier(): Int = Progression.gearTier(level)

    /**
     * 경험치를 더한다. 레벨업이 발생하면 오른 레벨 수를 반환(없으면 0).
     * 레벨업 시 숙련 포인트가 자동 지급된다.
     */
    fun addExp(amount: Int): Int {
        if (amount <= 0 || level >= Progression.MAX_LEVEL) return 0
        exp += amount
        var gained = 0
        while (level < Progression.MAX_LEVEL) {
            val need = Progression.expToNext(level)
            if (need <= 0 || exp < need) break
            exp -= need
            level++
            gained++
            skillPoints += Progression.skillPointsFor(level)
        }
        if (level >= Progression.MAX_LEVEL) exp = 0
        return gained
    }

    /** 스킬 현재 랭크 */
    fun skillRank(id: String): Int = skills[id] ?: 0

    /** 스킬 강화 (SP 소모). 성공하면 true */
    fun upgradeSkill(id: String): Boolean {
        val def = Skills.of(id) ?: return false
        val cur = skillRank(id)
        if (cur >= def.maxRank) return false
        if (skillPoints <= 0) return false
        skillPoints--
        skills[id] = cur + 1
        return true
    }

    // 스킬 효과 --------------------------------------------------------
    /** 이동 속도 배율 (튼튼한 다리) */
    fun speedMult(): Float = 1f + 0.06f * skillRank("legs")

    /** 피자 최대 소지 개수 (넉넉한 배낭) */
    fun pizzaCapEff(): Int = PIZZA_CAP + skillRank("pack")

    /** 새 도망 반경 배율 (고요한 발걸음) — 작을수록 가까이 갈 수 있음 */
    fun fleeMult(): Float = (1f - 0.08f * skillRank("quiet")).coerceAtLeast(0.5f)

    /** 배고픔 감소 배율 (튼튼한 체력) */
    fun hungerMult(): Float = (1f - 0.10f * skillRank("stamina")).coerceAtLeast(0.4f)

    /** 행운 자연 감소 배율 (타고난 행운) */
    fun luckDecayMult(): Float = (1f - 0.20f * skillRank("lucky")).coerceAtLeast(0.2f)

    /** 행운 하한 (타고난 행운) */
    fun luckFloor(): Float = 5f * skillRank("lucky")

    /** 사진에서 별 하나 더 얻을 추가 확률 (매의 눈) */
    fun extraStarChance(): Double = 0.07 * skillRank("sharp")

    /** 지금까지 획득한 숙련 포인트 총량 (사용 + 보유) */
    fun skillInvested(): Int = skillPoints + skills.values.sum()

    /** 사진용품점 '탐조 강습' 비용 — 살수록 비싸진다 (돈으로 SP 구매) */
    fun trainingCost(): Int = 800 + skillInvested() * 500

    // ------------------ 낮/밤 ------------------

    /** 게임 시계를 dt초만큼 진행 (자정을 넘기면 날짜 +1) */
    fun advanceClock(dt: Float) {
        worldTime += dt * 24f / DAY_SECONDS
        while (worldTime >= 24f) {
            worldTime -= 24f
            day += 1
        }
    }

    /** 침대에서 자고 아침 7:12에 일어남 (자정 전에 잤다면 다음 날) */
    fun sleepUntilMorning() {
        if (worldTime > 7.2f) day += 1
        worldTime = 7.2f
    }

    /** 밤(올빼미 등 밤새 출현) 여부 */
    fun isNight(): Boolean = worldTime >= 19.5f || worldTime < 4.5f

    fun timeLabel(): String {
        val h = worldTime.toInt().coerceIn(0, 23)
        val m = (((worldTime - h) * 60f).toInt().coerceIn(0, 59))
        return String.format("%02d:%02d", h, m)
    }

    fun timeEmoji(): String = when {
        worldTime >= 7f && worldTime < 17f -> "☀"
        worldTime >= 17f && worldTime < 19.5f -> "🌆"
        else -> if (worldTime >= 4.5f) "🌅" else "🌙"
    }

    // ------------------ 장식 ------------------

    /** 현재 적용 중인 인테리어 스타일 */
    fun houseStyle(): HouseStyle = HouseStyles.of(houseStyleId)

    fun ownsHome(regionId: String): Boolean = regionId in ownedHomes

    /** 설치된 장식의 행운 보너스 합 */
    fun decorLuck(): Int {
        var s = 0
        for (id in decorSlots) {
            val d = Decors.of(id)
            if (d != null) s += d.luck
        }
        return s
    }

    /** 희귀새 출현 계산에 쓰는 실효 행운 */
    fun effectiveLuck(): Float = (luck + decorLuck()).coerceAtMost(100f)

    // ------------------------------------------------------------------

    /** 새로운 게임 시작: 선택한 지역에 집 정착 */
    fun reset(@Suppress("UNUSED_PARAMETER") homeRegionId: String = START_REGION_ID) {
        started = true
        inHome = false
        // 첫 정착지는 항상 서울. 다른 지역은 여행 후 집을 매입한다.
        money = 30000
        hunger = 100f
        luck = 50f
        for (i in pizzas.indices) pizzas[i] = 0
        cameraLevel = 1
        birdCounts.clear()
        bestStars.clear()
        visited.clear()
        homeRegion = START_REGION_ID
        region = START_REGION_ID
        visited.add(START_REGION_ID)
        ownedHomes.clear()
        ownedHomes.add(START_REGION_ID)
        ownedHouseStyles.clear()
        ownedHouseStyles.add("cozy")
        houseStyleId = "cozy"
        px = 0f
        py = 0f
        onBike = false
        questBird = null
        questReward = 0
        mainQuestStarted = false
        mainQuestStage = 0
        mainQuestFinished = false
        playSeconds = 0f
        photos = 0
        worldTime = 8.5f
        day = 1
        weatherId = Weather.SUNNY.id
        weatherSeconds = 55f
        for (i in decorSlots.indices) decorSlots[i] = -1
        decorOwned.clear()
        level = 1
        exp = 0
        skillPoints = 0
        skills.clear()
    }

    // ------------------------------------------------------------------
    // 직렬화
    // ------------------------------------------------------------------

    fun toJSON(): JSONObject = JSONObject().apply {
        put("v", 4)
        put("started", started)
        put("gender", gender)
        put("inHome", inHome)
        put("money", money)
        put("hunger", hunger.toDouble())
        put("luck", luck.toDouble())
        put("cameraLevel", cameraLevel)
        put("homeRegion", homeRegion)
        put("region", region)
        put("houseStyleId", houseStyleId)
        put("ownedHomes", JSONArray().apply { ownedHomes.forEach { put(it) } })
        put("ownedHouseStyles", JSONArray().apply { ownedHouseStyles.forEach { put(it) } })
        put("px", px.toDouble())
        put("py", py.toDouble())
        put("onBike", onBike)
        put("questBird", questBird ?: "")
        put("questReward", questReward)
        put("mainQuestStarted", mainQuestStarted)
        put("mainQuestStage", mainQuestStage)
        put("mainQuestFinished", mainQuestFinished)
        put("playSeconds", playSeconds.toDouble())
        put("photos", photos)
        put("worldTime", worldTime.toDouble())
        put("day", day)
        put("musicOn", musicOn)
        put("sfxOn", sfxOn)
        put("weatherId", weatherId)
        put("weatherSeconds", weatherSeconds.toDouble())
        put("level", level)
        put("exp", exp)
        put("skillPoints", skillPoints)
        put("skills", JSONObject(skills as Map<*, *>))
        put("pizzas", JSONArray().apply { pizzas.forEach { put(it) } })
        put("birdCounts", JSONObject(birdCounts as Map<*, *>))
        put("bestStars", JSONObject(bestStars as Map<*, *>))
        put("visited", JSONArray().apply { visited.forEach { put(it) } })
        put("decorSlots", JSONArray().apply { decorSlots.forEach { put(it) } })
        put("decorOwned", JSONArray().apply { decorOwned.forEach { put(it) } })
    }

    companion object {
        fun fromJSON(j: JSONObject): GameState {
            val s = GameState()
            val v = j.optInt("v", 1)
            s.started = j.optBoolean("started", false)
            s.gender = j.optString("gender", "male")
            s.inHome = j.optBoolean("inHome", false)
            s.money = j.optInt("money", 0)
            s.hunger = j.optDouble("hunger", 100.0).toFloat()
            s.luck = j.optDouble("luck", 50.0).toFloat()
            s.cameraLevel = j.optInt("cameraLevel", 1)
            s.homeRegion = j.optString("homeRegion", START_REGION_ID)
            s.region = j.optString("region", s.homeRegion)
            s.houseStyleId = j.optString("houseStyleId", "cozy")
            val oh = j.optJSONArray("ownedHomes")
            if (oh != null) {
                for (i in 0 until oh.length()) {
                    val id = oh.optString(i, "")
                    if (id in Regions.byId) s.ownedHomes.add(id)
                }
            }
            // v1/v2 세이브에는 소유 집 목록이 없었으므로 당시 집을 자동 보존한다.
            s.ownedHomes.add(s.homeRegion)
            val os = j.optJSONArray("ownedHouseStyles")
            if (os != null) {
                for (i in 0 until os.length()) {
                    val id = os.optString(i, "")
                    if (id in HouseStyles.byId) s.ownedHouseStyles.add(id)
                }
            }
            s.ownedHouseStyles.add("cozy")
            if (s.houseStyleId !in s.ownedHouseStyles) s.houseStyleId = "cozy"
            s.px = j.optDouble("px", 0.0).toFloat()
            s.py = j.optDouble("py", 0.0).toFloat()
            s.onBike = j.optBoolean("onBike", false)
            s.questBird = j.optString("questBird", "").ifEmpty { null }
            s.questReward = j.optInt("questReward", 0)
            s.mainQuestStarted = j.optBoolean("mainQuestStarted", false)
            s.mainQuestStage = j.optInt("mainQuestStage", 0).coerceIn(0, MainStory.CHAPTERS.size)
            s.mainQuestFinished = j.optBoolean("mainQuestFinished", false) || s.mainQuestStage >= MainStory.CHAPTERS.size
            s.playSeconds = j.optDouble("playSeconds", 0.0).toFloat()
            s.photos = j.optInt("photos", 0)
            s.worldTime = j.optDouble("worldTime", 8.5).toFloat().coerceIn(0f, 24f)
            s.day = j.optInt("day", 1).coerceAtLeast(1)
            s.musicOn = j.optBoolean("musicOn", true)
            s.sfxOn = j.optBoolean("sfxOn", true)
            s.weatherId = j.optString("weatherId", Weather.SUNNY.id)
            s.weatherSeconds = j.optDouble("weatherSeconds", 55.0).toFloat().coerceIn(0f, 120f)

            s.level = j.optInt("level", 1).coerceIn(1, Progression.MAX_LEVEL)
            s.exp = j.optInt("exp", 0).coerceAtLeast(0)
            s.skillPoints = j.optInt("skillPoints", 0).coerceAtLeast(0)
            val sk = j.optJSONObject("skills")
            if (sk != null) {
                val itSk = sk.keys()
                while (itSk.hasNext()) {
                    val k = itSk.next()
                    val def = Skills.of(k)
                    if (def != null) s.skills[k] = sk.optInt(k, 0).coerceIn(0, def.maxRank)
                }
            }

            val pz = j.optJSONArray("pizzas")
            if (pz != null) {
                if (v >= 2 || pz.length() == 9) {
                    for (i in 0 until minOf(pz.length(), s.pizzas.size)) s.pizzas[i] = pz.optInt(i, 0)
                } else {
                    // v1: 품질 3칸 배열 -> 치즈 피자로 마이그레이션
                    for (q in 0 until minOf(pz.length(), 3)) s.pizzas[q] = pz.optInt(q, 0)
                }
            }
            val bc = j.optJSONObject("birdCounts")
            if (bc != null) {
                val it2 = bc.keys()
                while (it2.hasNext()) {
                    val k = it2.next()
                    s.birdCounts[k] = bc.optInt(k, 0)
                }
            }
            val bs = j.optJSONObject("bestStars")
            if (bs != null) {
                val it3 = bs.keys()
                while (it3.hasNext()) {
                    val k = it3.next()
                    s.bestStars[k] = bs.optInt(k, 0)
                }
            }
            val vs = j.optJSONArray("visited")
            if (vs != null) {
                for (i in 0 until vs.length()) s.visited.add(vs.optString(i))
            }
            val ds = j.optJSONArray("decorSlots")
            if (ds != null && ds.length() >= 3) {
                for (i in 0 until 3) s.decorSlots[i] = ds.optInt(i, -1)
            }
            val dwn = j.optJSONArray("decorOwned")
            if (dwn != null) {
                for (i in 0 until dwn.length()) {
                    val id = dwn.optInt(i, -1)
                    if (id >= 0) s.decorOwned.add(id)
                }
            }
            return s
        }
    }
}

/** 저장/로드 관리자 */
object SaveManager {
    private const val PREFS = "pizza_and_bird_save"
    private const val KEY = "state"

    fun save(ctx: Context, s: GameState) {
        if (!s.started) return
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, s.toJSON().toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context): GameState {
        return try {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            if (raw != null) GameState.fromJSON(JSONObject(raw)) else GameState()
        } catch (_: Exception) {
            GameState()
        }
    }

    fun hasSave(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
