package com.pizzaandbird.game

/**
 * [P11] 피자 **특성(트레잇)** — 피자 종류마다 붙는 맛 성향.
 *
 * 특성은 장식이 아니라 실제 판정에 들어간다:
 * - **먹을 때**: 조각 하나당 배고픔·행운 회복량을 곱/합으로 보정한다 ([PizzaSlices.sliceHunger] · [PizzaSlices.sliceLuck]).
 *   살짝 탄 피자(`PizzaQ.BURNT`)는 `burntMult`가, 걸작(`PERFECT`)은 `perfectLuck`이 추가로 적용된다.
 * - **구울 때**: `cursorMult`는 타이밍 커서 속도, `zoneMult`는 걸작 판정 폭에 곱해진다
 *   ([PizzaDef.cursorSpeedEff] · [PizzaDef.perfectWEff] → `BakeOverlay`).
 *
 * 저장 규칙: 특성은 **고정 데이터**라 세이브에 담지 않는다 (피자 id로 언제든 다시 붙는다).
 * `Data.kt`의 `PizzaDef`는 규칙 4(append-only) 때문에 건드리지 않고, 이 파일의
 * id → 특성 표로 덧씌운다. 새 피자를 추가하면 [PizzaTraits.BY_PIZZA]에 한 줄만 더하면 된다.
 */
enum class PizzaTrait(
    /** 화면에 보이는 짧은 이름 ("매콤") */
    val label: String,
    val emoji: String,
    /** 한 줄 설명 — 빠른 피자 창·메뉴 피자 탭의 특성 카드에 그대로 나간다 */
    val desc: String,
    // ---- 먹을 때 (조각 단위) ----
    val hungerMult: Float = 1f,
    val luckMult: Float = 1f,
    val hungerFlat: Int = 0,
    val luckFlat: Int = 0,
    /** 살짝 탔을 때 효과 배율 (1보다 크면 태워도 먹을 만하다) */
    val burntMult: Float = 1f,
    /** 걸작일 때 얹히는 추가 행운 */
    val perfectLuck: Int = 0,
    // ---- 구울 때 ----
    /** 타이밍 커서 속도 배율 (작을수록 쉬움) */
    val cursorMult: Float = 1f,
    /** 걸작 판정 폭 배율 (클수록 너그러움) */
    val zoneMult: Float = 1f
) {
    /** 🌶 매콤 — 입맛을 돋운다 */
    SPICY(
        "매콤", "🌶", "매운맛이 입맛을 돋워요. 배는 더 든든하지만 행운은 살짝 덜 올라요.",
        hungerMult = 1.10f, luckFlat = -1
    ),

    /** 🍯 달콤 — 행운이 잘 오른다 */
    SWEET(
        "달콤", "🍯", "달콤한 마무리. 먹는 순간 기분이 좋아져 행운이 잘 올라요.",
        hungerMult = 0.95f, luckMult = 1.20f
    ),

    /** 🧀 고소 — 조각 하나가 더 든든하다 */
    SAVORY(
        "고소", "🧀", "치즈의 고소함이 조각 하나를 더 든든하게 만들어요.",
        hungerFlat = 2
    ),

    /** 🫓 바삭 — 굽기 쉽지만 배는 덜 찬다 */
    CRISPY(
        "바삭", "🫓", "얇고 바삭해요. 굽기가 쉽고 판정도 넉넉한 대신 배는 조금 덜 차요.",
        hungerMult = 0.90f, cursorMult = 0.92f, zoneMult = 1.10f
    ),

    /** 🔥 불맛 — 태워도 맛있고 걸작이면 행운이 더 */
    SMOKY(
        "불맛", "🔥", "화덕 불향이 배어 있어요. 살짝 타도 맛있고, 걸작이면 행운이 더 올라요.",
        burntMult = 1.60f, perfectLuck = 2
    ),

    /** 🌿 신선 — 행운을 부르고 굽기도 너그럽다 */
    FRESH(
        "신선", "🌿", "생채소와 허브 향. 행운을 부르고 굽는 손길도 너그러워져요.",
        luckFlat = 1, zoneMult = 1.08f
    ),

    /** 🍗 든든 — 배가 확 차지만 굽기는 조심 */
    HEARTY(
        "든든", "🍗", "재료가 듬뿍. 배가 확 차지만 두꺼워서 굽는 타이밍은 조심해야 해요.",
        hungerMult = 1.15f, cursorMult = 1.06f
    ),

    /** ✨ 진미 — 걸작이면 행운이 크게, 판정은 까다롭게 */
    DELICACY(
        "진미", "✨", "최상급 재료. 걸작으로 구우면 행운이 크게 오르지만 판정은 까다로워요.",
        luckMult = 1.10f, perfectLuck = 3, zoneMult = 0.92f
    ),

    /** 🥔 쫀득 — 덜 익어도 괜찮고 배도 든든 */
    CHEWY(
        "쫀득", "🥔", "쫀득한 도우. 조금 덜 익어도 괜찮고 배도 든든해요.",
        hungerMult = 1.08f, zoneMult = 1.05f
    ),

    /** 🐟 해물 — 행운을 부르지만 태우면 비리다 */
    SEAFOOD(
        "해물", "🐟", "바다 향이 행운을 불러요. 대신 태우면 비려져서 효과가 확 줄어요.",
        hungerMult = 0.95f, luckFlat = 2, burntMult = 0.70f
    ),

    /** 🌰 구수 — 살짝 타도 오히려 구수하다 */
    EARTHY(
        "구수", "🌰", "노릇하게 구운 단맛. 살짝 타도 오히려 구수해서 버릴 게 없어요.",
        hungerMult = 1.10f, burntMult = 1.30f
    );

    /** "🌶 매콤" */
    val chip: String get() = "$emoji $label"
}

/**
 * [P11] 피자 id → 특성 표. 특성은 피자마다 2개씩 붙인다.
 *
 * ⚠️ 피자 id는 세이브 인덱스([Data.kt] `Pizzas`)이므로 **키를 바꾸지 말고 새 피자는 끝에 추가**한다.
 */
object PizzaTraits {

    private val BY_PIZZA: Map<Int, List<PizzaTrait>> = mapOf(
        // ---- 일반 피자 (가정용 오븐) ----
        0 to listOf(PizzaTrait.SAVORY, PizzaTrait.CHEWY),          // 치즈 — 고소하고 쫀득한 기본
        1 to listOf(PizzaTrait.EARTHY, PizzaTrait.FRESH),          // 버섯 — 구수한 숲의 향
        2 to listOf(PizzaTrait.HEARTY, PizzaTrait.SWEET),          // 불고기 — 든든하고 달콤짭짤
        3 to listOf(PizzaTrait.SPICY, PizzaTrait.HEARTY),          // 페퍼로니 — 매콤하고 든든
        4 to listOf(PizzaTrait.SWEET, PizzaTrait.EARTHY),          // 고구마 — 달콤구수
        5 to listOf(PizzaTrait.HEARTY, PizzaTrait.FRESH),          // 콤비네이션 — 듬뿍 + 피망·양파

        // ---- 화덕피자 (장작 화덕) ----
        6 to listOf(PizzaTrait.FRESH, PizzaTrait.SMOKY),           // 마르게리타 — 바질 신선 + 화덕 불맛
        7 to listOf(PizzaTrait.FRESH, PizzaTrait.CRISPY),          // 마리나라 — 치즈 없는 담백·바삭
        8 to listOf(PizzaTrait.SAVORY, PizzaTrait.HEARTY),         // 콰트로 포르마지 — 네 치즈의 고소·든든
        9 to listOf(PizzaTrait.SWEET, PizzaTrait.DELICACY),        // 고르곤졸라 — 꿀의 달콤 + 진미
        10 to listOf(PizzaTrait.SPICY, PizzaTrait.SMOKY),          // 디아볼라 — 매콤 + 불맛
        11 to listOf(PizzaTrait.FRESH, PizzaTrait.DELICACY),       // 루꼴라 프로슈토 — 신선한 생햄 진미

        // ---- [P07] 지역 특산 피자 ----
        12 to listOf(PizzaTrait.SPICY, PizzaTrait.HEARTY),         // 춘천 닭갈비 — 매콤달콤 철판
        13 to listOf(PizzaTrait.CHEWY, PizzaTrait.EARTHY),         // 강릉 감자 옹심이 — 쫀득구수
        14 to listOf(PizzaTrait.SEAFOOD, PizzaTrait.SAVORY),       // 속초 오징어 — 바다 향
        15 to listOf(PizzaTrait.SPICY, PizzaTrait.FRESH),          // 전주 콩나물 비빔 — 고추장 + 아삭
        16 to listOf(PizzaTrait.CRISPY, PizzaTrait.SAVORY),        // 대구 납작 치즈 — 바삭고소
        17 to listOf(PizzaTrait.FRESH, PizzaTrait.HEARTY),         // 광주 상추 육전 — 상추 + 듬뿍
        18 to listOf(PizzaTrait.SEAFOOD, PizzaTrait.CHEWY),        // 부산 어묵 꼬치 — 국물 향 쫀득
        19 to listOf(PizzaTrait.HEARTY, PizzaTrait.DELICACY)       // 제주 흑돼지 — 듬뿍 + 최상급
    )

    /** 이 피자에 붙은 특성 (없는 id면 빈 목록) */
    fun of(pizzaId: Int): List<PizzaTrait> = BY_PIZZA[pizzaId] ?: emptyList()

    /** 모든 피자 id가 특성을 가지고 있는지 (데이터 검수용) */
    fun missing(): List<Int> = Pizzas.ALL.filter { BY_PIZZA[it.id].isNullOrEmpty() }.map { it.id }
}

// ---------------------------------------------------------------------------
// [P11] PizzaDef 확장 — Data.kt 를 건드리지 않고 특성/조각 수치를 덧씌운다
// ---------------------------------------------------------------------------

/** 이 피자의 특성 목록 */
val PizzaDef.traits: List<PizzaTrait> get() = PizzaTraits.of(id)

/** "🌶 매콤 · 🔥 불맛" — 카드에 그대로 붙이는 칩 문자열 */
val PizzaDef.traitChips: String get() = traits.joinToString(" · ") { it.chip }

/** 특성이 반영된 커서 속도 (도우 배율은 `BakeOverlay`가 따로 곱한다) */
fun PizzaDef.cursorSpeedEff(): Float =
    cursorSpeed * traits.fold(1f) { acc, t -> acc * t.cursorMult }

/** 특성이 반영된 걸작 판정 폭 */
fun PizzaDef.perfectWEff(): Float =
    perfectW * traits.fold(1f) { acc, t -> acc * t.zoneMult }

/** 특성까지 반영한 실제 난이도 점 (표시용 1~5) */
fun PizzaDef.difficultyEff(): Int {
    val spd = cursorSpeedEff()
    val zone = perfectWEff()
    var n = difficulty
    if (spd <= cursorSpeed * 0.95f || zone >= perfectW * 1.05f) n -= 1
    if (spd >= cursorSpeed * 1.05f || zone <= perfectW * 0.95f) n += 1
    return n.coerceIn(1, 5)
}

/** 난이도 점 문자열 (특성 반영판) — 화면은 이쪽을 쓴다 */
fun PizzaDef.difficultyDotsEff(): String =
    "●".repeat(difficultyEff()) + "○".repeat(5 - difficultyEff())
