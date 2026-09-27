package com.pizzaandbird.game

import android.content.Context
import org.json.JSONObject

/**
 * [P07] 피자 확장 — 도우 종류 & 지역 특산 재료(토핑).
 *
 * 굽기 흐름이 **① 도우 선택 → ② 피자 선택(+현재 지역 특산) → ③ 타이밍 굽기** 3단이 된다.
 * - [Dough] 3종: 가격·게이지 속도·판정 폭·굽기 완료 시 즉시 보너스가 다르다.
 * - [Ingredients]: 12개 도시의 **특산 재료 8종**. 재료는 그 지역에서 사들이고(팬트리 보관),
 *   아무 집 화덕에서나 구울 수 있다 — "여행지에서 사 온 특산 재료로 집에서 굽는 집밥".
 *
 * 저장(규칙 3): `GameState.kt`를 건드리지 않고 기본 SharedPreferences의 **`feat_pantry_v1`** 키에
 * JSON 한 덩어리로 둔다. `feat_` 접두사 덕분에 P5(백업 코드)가 자동으로 수집한다.
 *
 * ```json
 * {"stock":{"chuncheon_dakgalbi":2, ...}, "dough":"whole"}
 * ```
 */

/**
 * [P07] 도우 3종 — 굽기 난이도와 결과를 바꾼다.
 *
 * `gaugeSpeed`는 타이밍 커서 속도 배율, `zoneScale`은 판정 폭(걸작/맛있는 구간) 배율이다.
 * 보너스는 **구워낸 순간** 바로 적용된다 (피자 인벤토리에는 도우 정보가 남지 않으므로
 * 먹을 때 몰래 더해지는 수치는 만들지 않는다 — 표시된 숫자가 전부다).
 */
enum class Dough(
    val label: String,
    val icon: String,
    val price: Int,
    val gaugeSpeed: Double,
    val bonusLabel: String,
    // ↓ [P07] 구현용 상세 (계약 API 뒤에 기본값 추가로 붙였으므로 외부 사용부는 그대로)
    val zoneScale: Float = 1f,
    val hungerBonus: Int = 0,
    val luckBonus: Int = 0,
    val desc: String = ""
) {
    CLASSIC(
        "클래식 도우", "🍞", 0, 1.00, "기본 밸런스",
        1.00f, 0, 0,
        "늘 굽던 익숙한 도우. 가격도, 난이도도, 효과도 그대로예요."
    ),
    THIN(
        "얇은 화덕 도우", "🫓", 1500, 1.12, "행운 +3 · 판정 좁음",
        0.92f, 0, 3,
        "바삭한 도우. 커서가 빠르고 판정이 좁아지는 대신 굽자마자 행운이 올라요."
    ),
    WHOLE(
        "통밀 도우", "🌾", 2000, 0.88, "배고픔 +8 · 판정 넓음",
        1.08f, 8, 0,
        "든든한 통밀 도우. 느긋하게 굽고 판정도 넓어요. 굽자마자 배가 든든!"
    );

    /** 게이지 속도 배율 (Float) — 피자 `cursorSpeed`에 곱해진다 */
    val speedF: Float get() = gaugeSpeed.toFloat()

    /** 가격 표시: 기본 도우는 "무료" */
    val priceLabel: String get() = if (price <= 0) "기본 (무료)" else won(price)

    /** 난이도 표시 — 속도가 빠를수록 불이 많아진다 */
    fun difficultyDots(): String {
        val n = when {
            gaugeSpeed >= 1.10 -> 4
            gaugeSpeed >= 1.00 -> 3
            gaugeSpeed >= 0.95 -> 2
            else -> 1
        }
        return "●".repeat(n) + "○".repeat(5 - n)
    }

    companion object {
        fun of(name: String?): Dough = values().firstOrNull { it.name == name } ?: CLASSIC
    }
}

/**
 * [P07] 지역 특산 재료 & 팬트리(재료 보관함).
 *
 * 공개 API (OVERVIEW §5 계약):
 * - [toppingsFor]`(regionId)` → 그 지역의 특산 재료 목록 (P8 피자 납품 에피소드가 선택 사용)
 * - [stock] / [buy] / [consume] → 재료 재고
 * - 저장 키 `feat_pantry_v1` → P5 백업이 수집
 */
object Ingredients {

    /**
     * 특산 재료 한 종류. `regionId`는 이 재료를 **사들일 수 있는** 지역(도시) id.
     * `crustColors`는 아이콘 자동 생성 규약에 쓰는 색 쌍 — (피자 바탕색, 토핑색1)이며
     * `Data.kt` 피자 id 12~19의 `baseColor`/`topColorA`와 1:1로 맞춘다 (`Assets.kt` 무수정).
     */
    data class ToppingDef(
        val id: String,
        val label: String,
        val icon: String,
        val regionId: String,      // 이 재료를 쓸 수 있는 지역(town) id
        val price: Int,
        val hungerBonus: Int,
        val luckBonus: Int,
        val crustColors: Pair<Int, Int>  // 아이콘 자동생성용 (기존 규칙)
    ) {
        /** 지역 이름 (없으면 id 그대로) */
        val regionName: String get() = Regions.byId[regionId]?.name ?: regionId

        /** 이 재료로 굽는 피자 (id 12~19) — 목록 순서가 곧 피자 id 순서 */
        val pizzaId: Int get() = TOPPING_PIZZA_ID + TOPPINGS.indexOf(this)

        val pizza: PizzaDef get() = Pizzas.of(pizzaId)

        /** "🍗 춘천 닭갈비 피자" */
        val fullName: String get() = "$icon ${pizza.name} 피자"
    }

    /** 특산 피자가 시작하는 피자 id (id 12~19 — 절대 변경 금지) */
    const val TOPPING_PIZZA_ID = 12

    private fun c(v: Long): Int = v.toInt()

    /**
     * §5 표의 8종. **목록 순서 = 피자 id 12~19 순서**이므로 순서를 바꾸거나 중간에 넣지 말 것.
     * 배고픔/행운 보너스는 `Data.kt`의 해당 PizzaDef 값과 같다 (먹을 때 적용되는 실제 수치).
     */
    val TOPPINGS: List<ToppingDef> = listOf(
        ToppingDef(
            "chuncheon_dakgalbi", "춘천 닭갈비", "🍗", "chuncheon", 3000, 12, 3,
            c(0xFFD9503F) to c(0xFF8F3A22)
        ),
        ToppingDef(
            "gangneung_ongsimi", "강릉 감자 옹심이", "🥔", "gangneung", 2500, 14, 2,
            c(0xFFF5E3A3) to c(0xFFD9B36B)
        ),
        ToppingDef(
            "sokcho_squid", "속초 오징어", "🦑", "sokcho", 3500, 10, 5,
            c(0xFFFDF0DC) to c(0xFFE8A0A8)
        ),
        ToppingDef(
            "jeonju_kongnamul", "전주 콩나물 비빔", "🫘", "jeonju", 2800, 11, 4,
            c(0xFFF2D06B) to c(0xFFFDF6E8)
        ),
        ToppingDef(
            "daegu_napjak", "대구 납작 치즈", "🧀", "daegu", 2600, 9, 4,
            c(0xFFF7E3A8) to c(0xFFE8A75C)
        ),
        ToppingDef(
            "gwangju_yukjeon", "광주 상추 육전", "🥬", "gwangju", 3200, 12, 4,
            c(0xFFE8D8A0) to c(0xFF8A5A2E)
        ),
        ToppingDef(
            "busan_eomuk", "부산 어묵 꼬치", "🐟", "busan", 2900, 11, 3,
            c(0xFFF0DCB0) to c(0xFFC89A5E)
        ),
        ToppingDef(
            "jeju_heukdoeji", "제주 흑돼지", "🐖", "jeju", 5000, 16, 6,
            c(0xFFE0A878) to c(0xFF7A4226)
        )
    )

    val byId: Map<String, ToppingDef> = TOPPINGS.associateBy { it.id }

    fun of(id: String): ToppingDef? = byId[id]

    /** 피자 id(12~19) → 그 피자의 특산 재료 */
    fun toppingOfPizza(pizzaId: Int): ToppingDef? = TOPPINGS.getOrNull(pizzaId - TOPPING_PIZZA_ID)

    /** 특산 피자 id 전체 (도감/메뉴 표시용) */
    val SPECIAL_PIZZA_IDS: List<Int> = TOPPINGS.indices.map { TOPPING_PIZZA_ID + it }

    /** 특산 피자 여부 */
    fun isSpecial(pizzaId: Int): Boolean = pizzaId in SPECIAL_PIZZA_IDS

    /** 이 지역의 특산 재료 (없으면 빈 목록) — 굽기 화면의 "✈ 특산" 탭을 채운다 */
    fun toppingsFor(regionId: String): List<ToppingDef> = TOPPINGS.filter { it.regionId == regionId }

    // ------------------------------------------------------------------
    // 팬트리 저장 — feat_pantry_v1 (규칙 3)
    // ------------------------------------------------------------------

    private const val PREFS = "pizza_and_bird_save"   // 세이브와 같은 prefs 파일
    const val PANTRY_KEY = "feat_pantry_v1"            // P5 백업이 훑는 키

    /** 프로세스 수명 동안의 팬트리 상태 (prefs를 매번 파싱하지 않도록 캐시) */
    private class Pantry(
        val stock: LinkedHashMap<String, Int> = LinkedHashMap(),
        var dough: Dough = Dough.CLASSIC
    )

    private val cache = HashMap<Context, Pantry>()

    private fun prefsOf(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pantry(ctx: Context): Pantry {
        cache[ctx]?.let { return it }
        val p = Pantry()
        try {
            val raw = prefsOf(ctx).getString(PANTRY_KEY, null)
            if (raw != null) {
                val j = JSONObject(raw)
                val st = j.optJSONObject("stock")
                if (st != null) {
                    val it = st.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        if (k in byId) p.stock[k] = st.optInt(k, 0).coerceAtLeast(0)
                    }
                }
                p.dough = Dough.of(j.optString("dough", Dough.CLASSIC.name))
            }
        } catch (_: Exception) {
            // prefs가 없는 환경(프리뷰 스텁 등) — 빈 팬트리로 진행한다
        }
        cache[ctx] = p
        return p
    }

    private fun persist(ctx: Context, p: Pantry) {
        try {
            val j = JSONObject()
            val st = JSONObject()
            for ((k, v) in p.stock) if (v > 0) st.put(k, v)
            j.put("stock", st)
            j.put("dough", p.dough.name)
            prefsOf(ctx).edit().putString(PANTRY_KEY, j.toString()).apply()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // 재고
    // ------------------------------------------------------------------

    /** 이 재료의 보유 개수 */
    fun stock(ctx: Context, toppingId: String): Int = pantry(ctx).stock[toppingId] ?: 0

    /** 팬트리에 들어 있는 재료 종류 수 (UI 뱃지용) */
    fun stockKinds(ctx: Context): Int = pantry(ctx).stock.count { it.value > 0 }

    /** 보유한 재료 총 개수 */
    fun stockTotal(ctx: Context): Int = pantry(ctx).stock.values.sum()

    /**
     * 재고를 하나 늘린다. **골드 차감은 호출부에서** 한다 (규칙: 경제 처리는 UI 쪽).
     * @return 저장까지 성공했으면 true
     */
    fun buy(ctx: Context, toppingId: String): Boolean {
        if (toppingId !in byId) return false
        val p = pantry(ctx)
        p.stock[toppingId] = (p.stock[toppingId] ?: 0) + 1
        persist(ctx, p)
        return true
    }

    /** 재고를 하나 쓴다 (부족하면 false — 재고는 음수가 되지 않는다) */
    fun consume(ctx: Context, toppingId: String): Boolean {
        val p = pantry(ctx)
        val n = p.stock[toppingId] ?: 0
        if (n <= 0) return false
        p.stock[toppingId] = n - 1
        persist(ctx, p)
        return true
    }

    // ------------------------------------------------------------------
    // 도우 선택 기억
    // ------------------------------------------------------------------

    /** 마지막으로 고른 도우 (다음 굽기의 기본 선택) */
    fun lastDough(ctx: Context): Dough = pantry(ctx).dough

    fun setDough(ctx: Context, d: Dough) {
        val p = pantry(ctx)
        p.dough = d
        persist(ctx, p)
    }

    /** 도우 보너스를 지금 적용하고 문구를 반환한다 (보너스 없으면 null) */
    fun applyDoughBonus(state: GameState, d: Dough): String? {
        if (d.hungerBonus == 0 && d.luckBonus == 0) return null
        if (d.hungerBonus != 0) state.hunger = (state.hunger + d.hungerBonus).coerceIn(0f, 100f)
        if (d.luckBonus != 0) state.luck = (state.luck + d.luckBonus).coerceIn(0f, 100f)
        val parts = ArrayList<String>()
        if (d.hungerBonus != 0) parts += "배고픔 +${d.hungerBonus}"
        if (d.luckBonus != 0) parts += "행운 +${d.luckBonus}"
        return parts.joinToString(" · ")
    }
}
