package com.pizzaandbird.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 플레이어 진행 상황. 오프라인 저장(JSON in SharedPreferences).
 */
class GameState {

    var started = false            // 첫 집 선택 완료(=세이브 존재)
    var inHome = false             // 현재 집 안에 있는지
    var money = 0                  // 용돈(원)
    var hunger = 100f              // 배고픔 수치 (100 = 포만, 0 = 배고픔)
    var luck = 50f                 // 행운 수치 (높을수록 희귀새 출현)
    val pizzas = IntArray(3)       // 품질별 피자 개수 (0=살짝탐, 1=맛있는, 2=걸작)
    var cameraLevel = 1            // 카메라 등급 (1~5)
    val birdCounts = LinkedHashMap<String, Int>()   // 도감: 새별 촬영 횟수
    val visited = LinkedHashSet<String>()           // 방문한 지역

    var homeRegion = "seoul"       // 집이 있는 지역
    var region = "seoul"           // 현재 지역
    var px = 0f                    // 월드 좌표(px)
    var py = 0f
    var onBike = false

    var questBird: String? = null  // 박사 의뢰: 촬영할 새
    var questReward = 0

    var playSeconds = 0f

    // ------------------------------------------------------------------

    val pizzaCount: Int get() = pizzas[0] + pizzas[1] + pizzas[2]

    fun addPizza(quality: Int): Boolean {
        if (pizzaCount >= PIZZA_CAP) return false
        pizzas[quality.coerceIn(0, 2)]++
        return true
    }

    /** 특정 품질 피자 먹기 (인벤토리에서 제거하고 품질 인덱스 반환, 없으면 null) */
    fun takePizza(quality: Int): Int? {
        val q = quality.coerceIn(0, 2)
        if (pizzas[q] <= 0) return null
        pizzas[q]--
        return q
    }

    fun eat(quality: Int): PizzaQ? {
        val q = takePizza(quality) ?: return null
        val def = PizzaQ.of(q)
        hunger = (hunger + def.hunger).coerceAtMost(100f)
        luck = (luck + def.luck).coerceAtMost(100f)
        return def
    }

    /** 새로운 게임 시작: 선택한 지역에 집 정착 */
    fun reset(homeRegionId: String) {
        started = true
        inHome = false
        money = 0
        hunger = 100f
        luck = 50f
        for (i in pizzas.indices) pizzas[i] = 0
        cameraLevel = 1
        birdCounts.clear()
        visited.clear()
        homeRegion = homeRegionId
        region = homeRegionId
        visited.add(homeRegionId)
        px = 0f
        py = 0f
        onBike = false
        questBird = null
        questReward = 0
        playSeconds = 0f
    }

    // ------------------------------------------------------------------
    // 직렬화
    // ------------------------------------------------------------------

    fun toJSON(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("started", started)
        put("inHome", inHome)
        put("money", money)
        put("hunger", hunger.toDouble())
        put("luck", luck.toDouble())
        put("cameraLevel", cameraLevel)
        put("homeRegion", homeRegion)
        put("region", region)
        put("px", px.toDouble())
        put("py", py.toDouble())
        put("onBike", onBike)
        put("questBird", questBird ?: "")
        put("questReward", questReward)
        put("playSeconds", playSeconds.toDouble())
        put("pizzas", JSONArray().apply { pizzas.forEach { put(it) } })
        put("birdCounts", JSONObject(birdCounts as Map<*, *>))
        put("visited", JSONArray().apply { visited.forEach { put(it) } })
    }

    companion object {
        fun fromJSON(j: JSONObject): GameState {
            val s = GameState()
            s.started = j.optBoolean("started", false)
            s.inHome = j.optBoolean("inHome", false)
            s.money = j.optInt("money", 0)
            s.hunger = j.optDouble("hunger", 100.0).toFloat()
            s.luck = j.optDouble("luck", 50.0).toFloat()
            s.cameraLevel = j.optInt("cameraLevel", 1)
            s.homeRegion = j.optString("homeRegion", "seoul")
            s.region = j.optString("region", s.homeRegion)
            s.px = j.optDouble("px", 0.0).toFloat()
            s.py = j.optDouble("py", 0.0).toFloat()
            s.onBike = j.optBoolean("onBike", false)
            s.questBird = j.optString("questBird", "").ifEmpty { null }
            s.questReward = j.optInt("questReward", 0)
            s.playSeconds = j.optDouble("playSeconds", 0.0).toFloat()
            val pz = j.optJSONArray("pizzas")
            if (pz != null) {
                for (i in 0 until minOf(pz.length(), s.pizzas.size)) s.pizzas[i] = pz.optInt(i, 0)
            }
            val bc = j.optJSONObject("birdCounts")
            if (bc != null) {
                val it2 = bc.keys()
                while (it2.hasNext()) {
                    val k = it2.next()
                    s.birdCounts[k] = bc.optInt(k, 0)
                }
            }
            val vs = j.optJSONArray("visited")
            if (vs != null) {
                for (i in 0 until vs.length()) s.visited.add(vs.optString(i))
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
