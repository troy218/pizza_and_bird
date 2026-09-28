package com.pizzaandbird.game

import kotlin.math.roundToInt

/**
 * [P11] 피자 **조각** 시스템 — 한 판을 구우면 8조각으로 쪼개진다.
 *
 * 이전에는 피자 한 판을 통째로 한 번에 먹었다. 이제 화덕에서 나온 한 판은
 * [PER_PIZZA]조각으로 잘려 배낭에 들어가고, 🍕 버튼(또는 메뉴의 냠냠)은 **한 조각씩** 먹는다.
 * 판을 다 비우면 그 판은 배낭에서 사라진다 → 배낭 한도([GameState.pizzaCapEff])는 계속 **판** 단위다.
 *
 * ## 저장
 * 조각 수는 [GameState.pizzaSlices] (인덱스 규칙은 `pizzas`와 동일한 `피자id*3 + 품질`)에 담기고
 * 세이브 v6부터 직렬화된다. v5 이하 세이브는 [reconcile]이 "판 1개 = 8조각"으로 채워 준다.
 *
 * ## 불변식
 * ```
 * pizzas[i]      = ceil(pizzaSlices[i] / 8)      (판 수)
 * 0 <= pizzaSlices[i] <= pizzas[i] * 8
 * ```
 * 이 규칙 덕분에 예전처럼 `pizzas[i]`를 직접 고치는 코드(사이드 스토리 피자 납품 등)가 있어도
 * [reconcile] 한 번이면 조각 수가 다시 맞는다.
 *
 * ## 조각 하나의 효과
 * 판 전체를 한 번에 먹던 옛 수치의 **조각 비율**을 곱한 뒤 피자 특성([PizzaTrait])을 얹는다.
 * 걸작 한 판(+60/+18) ≈ 조각 4개분이라, 네 조각이면 예전의 한 판과 비슷한 포만감이 된다.
 */
object PizzaSlices {

    /** 한 판을 쪼개면 나오는 조각 수 */
    const val PER_PIZZA = 8

    /** 판 전체 효과 → 조각 하나 효과 (배고픔) */
    private const val HUNGER_F = 0.20f

    /** 판 전체 효과 → 조각 하나 효과 (행운) */
    private const val LUCK_F = 0.15f

    /** `GameState.pizzas` / `pizzaSlices` 공용 인덱스 */
    fun idx(pizzaId: Int, quality: Int): Int =
        pizzaId.coerceIn(0, Pizzas.ALL.size - 1) * 3 + quality.coerceIn(0, 2)

    // -------------------------------------------------------------------
    // 재고 읽기
    // -------------------------------------------------------------------

    /** 특정 피자(품질 무관)의 남은 조각 수 */
    fun of(s: GameState, pizzaId: Int): Int {
        var n = 0
        for (q in 0 until 3) n += s.pizzaSlices[idx(pizzaId, q)]
        return n
    }

    /** 특정 피자·품질의 남은 조각 수 */
    fun of(s: GameState, pizzaId: Int, quality: Int): Int = s.pizzaSlices[idx(pizzaId, quality)]

    /** 배낭 전체의 남은 조각 수 (= 🍕 버튼을 몇 번 누를 수 있는지) */
    fun total(s: GameState): Int = s.pizzaSlices.sum()

    /** 계열(화덕피자/일반 피자)별 남은 조각 수 */
    fun ofKind(s: GameState, kind: PizzaKind): Int {
        var n = 0
        for (p in Pizzas.ALL) if (p.kind == kind) n += of(s, p.id)
        return n
    }

    /** 지금 먹을 수 있는 가장 좋은 품질 (없으면 -1) */
    fun bestQualityOf(s: GameState, pizzaId: Int): Int {
        for (q in 2 downTo 0) if (of(s, pizzaId, q) > 0) return q
        return -1
    }

    // -------------------------------------------------------------------
    // 굽기 / 정리
    // -------------------------------------------------------------------

    /** 한 판이 화덕에서 나왔다 — 판 +1, 조각 [PER_PIZZA]개 추가 */
    fun onBaked(s: GameState, pizzaId: Int, quality: Int) {
        val i = idx(pizzaId, quality)
        s.pizzas[i] = s.pizzas[i] + 1
        s.pizzaSlices[i] = s.pizzaSlices[i] + PER_PIZZA
    }

    /**
     * 판 인벤토리와 조각 수를 맞춘다.
     * - 조각 필드가 없는 예전 세이브(v5 이하): 판 1개 = 8조각으로 채운다
     * - 조각이 판 수를 넘으면 자르고, 판이 있는데 조각이 0이면 다시 채운다
     */
    fun reconcile(s: GameState) {
        for (p in Pizzas.ALL) {
            for (q in 0 until 3) {
                val i = idx(p.id, q)
                val pans = s.pizzas[i].coerceAtLeast(0)
                s.pizzas[i] = pans
                val slices = when {
                    pans <= 0 -> 0
                    s.pizzaSlices[i] <= 0 -> pans * PER_PIZZA
                    else -> s.pizzaSlices[i].coerceIn(1, pans * PER_PIZZA)
                }
                s.pizzaSlices[i] = slices
                s.pizzas[i] = pansOf(slices)
            }
        }
        if (s.quickPizzaId >= 0 && s.quickPizzaId !in Pizzas.byId) s.quickPizzaId = -1
    }

    /** 조각 수 → 판 수 (올림) */
    private fun pansOf(slices: Int): Int = (slices + PER_PIZZA - 1) / PER_PIZZA

    /**
     * 판째로 배낭에서 뺀다 (사이드 스토리 피자 납품용).
     * 가장 나중에 구운 판 하나분 조각(8개)이 함께 사라진다.
     */
    fun removePan(s: GameState, index: Int): Boolean {
        if (index !in s.pizzas.indices) return false
        if (s.pizzas[index] <= 0) return false
        val slices = (s.pizzaSlices[index] - PER_PIZZA).coerceAtLeast(0)
        s.pizzaSlices[index] = slices
        s.pizzas[index] = pansOf(slices)
        return true
    }

    /** 피자 id로 판째로 뺀다 (가장 좋은 품질부터) */
    fun removePanOf(s: GameState, pizzaId: Int): Boolean {
        for (q in 2 downTo 0) {
            val i = idx(pizzaId, q)
            if (s.pizzas[i] > 0) return removePan(s, i)
        }
        return false
    }

    // -------------------------------------------------------------------
    // 조각 하나의 효과 (피자 특성 반영)
    // -------------------------------------------------------------------

    /** 이 피자·품질의 **조각 하나**가 채워 주는 배고픔 */
    fun sliceHunger(def: PizzaDef, q: PizzaQ): Int {
        var v = (q.hunger + def.hungerBonus) * HUNGER_F
        if (q == PizzaQ.BURNT) for (t in def.traits) v *= t.burntMult
        for (t in def.traits) v *= t.hungerMult
        var n = v.roundToInt()
        for (t in def.traits) n += t.hungerFlat
        return n.coerceAtLeast(1)
    }

    /** 이 피자·품질의 **조각 하나**가 올려 주는 행운 */
    fun sliceLuck(def: PizzaDef, q: PizzaQ): Int {
        var v = (q.luck + def.luckBonus) * LUCK_F
        if (q == PizzaQ.BURNT) for (t in def.traits) v *= t.burntMult
        for (t in def.traits) v *= t.luckMult
        var n = v.roundToInt()
        for (t in def.traits) n += t.luckFlat
        if (q == PizzaQ.PERFECT) for (t in def.traits) n += t.perfectLuck
        return n.coerceAtLeast(0)
    }

    /** 화면 표시용: "조각당 배고픔 +15 · 행운 +3" */
    fun sliceLine(def: PizzaDef, q: PizzaQ): String =
        "조각당 배고픔 +${sliceHunger(def, q)} · 행운 +${sliceLuck(def, q)}"

    // -------------------------------------------------------------------
    // 먹기
    // -------------------------------------------------------------------

    /** 한 조각을 먹은 결과 — 실제로 오른 수치와 남은 재고가 그대로 담긴다 */
    class Bite(
        val pizzaId: Int,
        val quality: Int,
        val hunger: Int,
        val luck: Int,
        /** 이 품질에 남은 조각 */
        val slicesLeftOfQuality: Int,
        /** 이 피자에 남은 조각 (품질 무관) */
        val slicesLeft: Int,
        /** 방금 한 판을 다 비웠는지 */
        val panFinished: Boolean = false
    ) {
        /** 등록한 빠른 피자를 먹었는지 (자동 선택이면 false) */
        var fromQuick = false

        val def: PizzaDef get() = Pizzas.of(pizzaId)
        val q: PizzaQ get() = PizzaQ.of(quality)

        /** "🍕 페퍼로니 피자 · 맛있는 피자" */
        val label: String get() = "${def.emoji} ${def.fullName} · ${q.label}"
    }

    /**
     * 가장 좋은 품질부터 **한 조각**을 먹고 배고픔·행운을 적용한다.
     * 조각이 없으면 null (판만 있고 조각이 없는 상태는 [reconcile]이 막아 준다).
     */
    fun takeSlice(s: GameState, pizzaId: Int): Bite? {
        val def = Pizzas.of(pizzaId)
        for (q in 2 downTo 0) {
            val i = idx(def.id, q)
            if (s.pizzaSlices[i] <= 0 || s.pizzas[i] <= 0) continue
            val pansBefore = s.pizzas[i]
            s.pizzaSlices[i] = s.pizzaSlices[i] - 1
            s.pizzas[i] = pansOf(s.pizzaSlices[i])
            val qual = PizzaQ.of(q)
            val h = sliceHunger(def, qual)
            val l = sliceLuck(def, qual)
            s.hunger = (s.hunger + h).coerceIn(0f, 100f)
            s.luck = (s.luck + l).coerceIn(0f, 100f)
            return Bite(def.id, q, h, l, s.pizzaSlices[i], of(s, def.id), s.pizzas[i] < pansBefore)
        }
        return null
    }

    /**
     * 배낭에서 제일 좋은 조각 하나 (🍕 버튼의 자동 선택).
     * 품질이 같으면 조각당 배고픔이 큰 피자를 고른다.
     */
    fun takeBest(s: GameState): Bite? {
        for (q in 2 downTo 0) {
            var best: PizzaDef? = null
            var bestH = -1
            for (p in Pizzas.ALL) {
                if (s.pizzaSlices[idx(p.id, q)] <= 0) continue
                val h = sliceHunger(p, PizzaQ.of(q))
                if (best == null || h > bestH) { best = p; bestH = h }
            }
            if (best != null) return takeSlice(s, best.id)
        }
        return null
    }

    /**
     * 🍕 **빠른 피자** 한 조각 — 등록해 둔 피자를 우선 먹고, 없으면 제일 좋은 조각으로 넘어간다.
     * 등록한 피자가 품절이면 등록은 그대로 두고(다음에 구우면 다시 작동) 자동 선택 결과를 반환한다.
     */
    fun takeQuick(s: GameState): Bite? {
        val quick = s.quickPizzaId
        if (quick >= 0 && of(s, quick) > 0) {
            val bite = takeSlice(s, quick)
            if (bite != null) {
                bite.fromQuick = true
                return bite
            }
        }
        return takeBest(s)
    }
}
