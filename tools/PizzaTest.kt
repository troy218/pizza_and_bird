import com.pizzaandbird.game.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * [P11] 피자 조각 · 빠른 피자 · 특성 로직 JVM 검증 스크립트 (Android 런타임 불필요).
 *
 * 사용:
 * ```
 * kotlinc -d /tmp/jsonstub tools/jvm_stub/Json.kt                     # org.json 실제 동작 스텁
 * kotlinc -cp android.jar:/tmp/game -d /tmp/test tools/PizzaTest.kt
 * java -cp /tmp/jsonstub:android.jar:/tmp/game:/tmp/test:kotlin-stdlib.jar PizzaTestKt
 * ```
 * `MapTest.kt` 와 달리 지도/ Paint 를 건드리지 않으므로 android.jar 스텁으로도 끝까지 돈다.
 */

private var passed = 0
private var failed = 0

private fun check(cond: Boolean, msg: String) {
    if (cond) passed++ else { failed++; println("FAIL: $msg") }
}

private fun fresh(): GameState = GameState().apply { reset("seoul") }

fun main() {
    println("== [P11] 피자 조각 / 빠른 피자 / 특성 검증 ==")

    // 1) 한 판을 구우면 8조각 -------------------------------------------------
    val s = fresh()
    check(s.pizzaCount == 0 && s.sliceCount == 0, "초기 재고가 0이 아님")
    check(s.addPizza(3, 2), "피자 추가 실패")
    check(s.pizzaCount == 1, "판 수 오류: ${s.pizzaCount}")
    check(s.sliceCount == PizzaSlices.PER_PIZZA, "한 판 = 8조각 오류: ${s.sliceCount}")
    check(s.slicesOf(3) == 8 && s.slicesOf(3, 2) == 8, "조각 집계 오류: ${s.slicesOf(3)}")
    check(s.slicesOfKind(PizzaKind.REGULAR) == 8 && s.slicesOfKind(PizzaKind.OVEN) == 0, "계열별 조각 집계 오류")

    // 같은 (피자, 품질) 한 판을 더 구우면 조각이 쌓인다 (16)
    s.addPizza(3, 2)
    check(s.pizzaCount == 2 && s.slicesOf(3) == 16, "두 판 조각 합산 오류: ${s.pizzaCount}/${s.slicesOf(3)}")

    // 2) 먹기는 한 조각씩 -----------------------------------------------------
    s.hunger = 40f
    s.luck = 30f
    val bite = s.eatSlice(3)
    check(bite != null && bite.pizzaId == 3 && bite.quality == 2, "조각 먹기 실패")
    check(s.slicesOf(3) == 15 && s.pizzaCount == 2, "한 조각만 사라져야 함: ${s.slicesOf(3)}/${s.pizzaCount}")
    val wantH = PizzaSlices.sliceHunger(Pizzas.of(3), PizzaQ.PERFECT)
    val wantL = PizzaSlices.sliceLuck(Pizzas.of(3), PizzaQ.PERFECT)
    check(bite != null && bite.hunger == wantH && bite.luck == wantL, "조각 효과 수치 오류")
    check(s.hunger == 40f + wantH && s.luck == 30f + wantL, "상태에 효과가 반영되지 않음: ${s.hunger}/${s.luck}")
    check(wantH in 1..40, "조각 배고픔 범위 벗어남: $wantH")
    check(s.eat(3)?.label == "걸작 피자", "eat() 호환 API 오류")

    // 3) 한 판을 다 비우면 판이 사라진다 ---------------------------------------
    val one = fresh()
    one.addPizza(0, 1)
    var last: PizzaSlices.Bite? = null
    repeat(PizzaSlices.PER_PIZZA) { last = one.eatSlice(0) }
    check(one.slicesOf(0) == 0 && one.pizzaCount == 0, "8조각을 다 먹어도 판이 남음: ${one.pizzaCount}")
    check(last != null && last.panFinished, "마지막 조각에서 panFinished 가 서지 않음")
    check(one.eatSlice(0) == null && one.eatBest() == null && one.eatQuick() == null, "빈 배낭에서 먹기가 성공")

    // 4) 배낭 한도는 판 단위 ---------------------------------------------------
    val cap = fresh()
    var added = 0
    repeat(PIZZA_CAP + 5) { if (cap.addPizza(1, 0)) added++ }
    check(added == PIZZA_CAP && cap.pizzaCount == PIZZA_CAP, "배낭 한도 오류: $added")
    check(cap.sliceCount == PIZZA_CAP * PizzaSlices.PER_PIZZA, "한도까지 채운 조각 수 오류: ${cap.sliceCount}")
    check(!cap.addPizza(1, 0), "가득 찬 배낭에 피자가 더 들어감")

    // 5) 빠른 피자 등록 --------------------------------------------------------
    val q = fresh()
    q.addPizza(0, 1)      // 치즈 (일반)
    q.addPizza(6, 2)      // 마르게리타 (화덕)
    check(q.quickPizza() == null && q.quickPizzaId == -1, "초기 빠른 피자 등록이 비어 있지 않음")
    q.setQuickPizza(6)
    check(q.quickPizza()?.id == 6, "빠른 피자 등록 오류")
    val b1 = q.eatQuick()
    check(b1 != null && b1.pizzaId == 6 && b1.fromQuick, "등록한 피자를 우선 먹지 않음: ${b1?.pizzaId}")
    repeat(7) { q.eatQuick() }
    check(q.slicesOf(6) == 0 && q.pizzaCountOf(6) == 0, "등록 피자 8조각 소진 오류")
    val b2 = q.eatQuick()
    check(b2 != null && b2.pizzaId == 0 && !b2.fromQuick, "품절 후 자동 선택 오류: ${b2?.pizzaId}")
    check(q.quickPizzaId == 6, "품절됐다고 등록이 지워지면 안 된다")
    q.setQuickPizza(-1)
    check(q.quickPizza() == null, "빠른 피자 해제 오류")
    q.setQuickPizza(999)
    check(q.quickPizzaId == -1, "없는 피자 id가 등록됨")

    // 6) 피자 특성 ------------------------------------------------------------
    check(PizzaTraits.missing().isEmpty(), "특성이 없는 피자: ${PizzaTraits.missing()}")
    check(Pizzas.ALL.all { it.traits.size >= 2 }, "특성이 2개 미만인 피자가 있음")
    check(PizzaTrait.values().all { it.label.isNotBlank() && it.desc.isNotBlank() }, "특성 표시 데이터 누락")
    val smoky = Pizzas.of(10)     // 디아볼라 — 매콤 + 불맛
    val sea = Pizzas.of(14)       // 속초 오징어 — 해물 + 고소
    check(PizzaSlices.sliceHunger(smoky, PizzaQ.BURNT) > PizzaSlices.sliceHunger(sea, PizzaQ.BURNT),
        "burntMult(불맛/해물) 반영 오류")
    check(Pizzas.of(7).cursorSpeedEff() < Pizzas.of(7).cursorSpeed, "바삭 특성의 커서 보정 오류")
    check(Pizzas.of(7).perfectWEff() > Pizzas.of(7).perfectW, "신선·바삭 특성의 판정 보정 오류")
    check(Pizzas.of(19).perfectWEff() < Pizzas.of(19).perfectW, "진미 특성의 판정 보정 오류")
    check(Pizzas.ALL.all { it.difficultyEff() in 1..5 }, "특성 반영 난이도 범위 오류")
    // 품질 순서: 걸작 > 맛있는 > 살짝 탐
    for (p in Pizzas.ALL) {
        val h2 = PizzaSlices.sliceHunger(p, PizzaQ.PERFECT)
        val h1 = PizzaSlices.sliceHunger(p, PizzaQ.GOOD)
        val h0 = PizzaSlices.sliceHunger(p, PizzaQ.BURNT)
        check(h2 >= h1 && h1 >= h0 && h0 >= 1, "품질별 조각 효과 순서 오류: ${p.name} $h0/$h1/$h2")
        check(PizzaSlices.sliceLuck(p, PizzaQ.PERFECT) >= 0, "조각 행운이 음수: ${p.name}")
    }

    // 7) 판째로 내주기 (사이드 스토리 납품) -------------------------------------
    val d = fresh()
    d.addPizza(12, 2)
    d.addPizza(12, 2)
    d.eatSlice(12)                       // 조각 15 / 판 2
    check(d.slicesOf(12) == 15 && d.pizzaCountOf(12) == 2, "납품 전제 오류: ${d.slicesOf(12)}/${d.pizzaCountOf(12)}")
    check(d.removePanOf(12), "판째로 내주기 실패")
    check(d.slicesOf(12) == 7 && d.pizzaCountOf(12) == 1, "납품 후 재고 오류: ${d.slicesOf(12)}/${d.pizzaCountOf(12)}")
    check(d.removePanAt(PizzaSlices.idx(12, 2)) && d.sliceCount == 0, "인덱스 납품 후 재고 오류")
    check(!d.removePanAt(PizzaSlices.idx(12, 2)), "빈 슬롯에서 납품이 성공")

    // 8) 세이브 왕복 / 옛 세이브 마이그레이션 -----------------------------------
    val r = fresh()
    r.addPizza(9, 2); r.addPizza(3, 1); r.eatSlice(9); r.setQuickPizza(9)
    val json = r.toJSON()
    check(json.optInt("v") == 7, "세이브 버전이 v7이 아님: ${json.optInt("v")}")
    val back = GameState.fromJSON(json)
    check(back.pizzas.contentEquals(r.pizzas), "판 세이브 왕복 오류")
    check(back.pizzaSlices.contentEquals(r.pizzaSlices), "조각 세이브 왕복 오류")
    check(back.quickPizzaId == 9, "빠른 피자 세이브 왕복 오류")

    // v6 세이브(조각 필드 없음) — 가진 판만큼 8조각씩 채워진다
    val v6 = r.toJSON()
    v6.put("v", 6)
    v6.remove("pizzaSlices")
    v6.remove("quickPizzaId")
    val mig = GameState.fromJSON(v6)
    check(mig.pizzaCount == r.pizzaCount, "v6 마이그레이션에서 판이 사라짐: ${mig.pizzaCount}")
    check(mig.sliceCount == r.pizzaCount * PizzaSlices.PER_PIZZA, "v6 마이그레이션 조각 오류: ${mig.sliceCount}")
    check(mig.quickPizzaId == -1, "v6 마이그레이션 빠른 피자 기본값 오류")

    // v3 세이브(9칸 피자 배열) — 치즈/불고기 재고가 그대로 + 조각으로 변환
    val v3 = r.toJSON()
    v3.put("v", 3)
    v3.put("pizzas", JSONArray(listOf(1, 0, 2, 0, 0, 0, 0, 3, 0)))
    v3.remove("pizzaSlices")
    val old = GameState.fromJSON(v3)
    check(old.pizzaCountOf(0) == 3 && old.pizzaCountOf(2, 1) == 3, "v3 피자 마이그레이션 오류")
    check(old.slicesOf(0) == 24 && old.sliceCount == 6 * PizzaSlices.PER_PIZZA, "v3 조각 변환 오류: ${old.sliceCount}")

    // 문자열로 저장 → 다시 읽기 (실제 SharedPreferences 경로와 같은 형태)
    val txt = GameState.fromJSON(JSONObject(r.toJSON().toString()))
    check(txt.sliceCount == r.sliceCount && txt.quickPizzaId == 9, "JSON 문자열 왕복 오류")

    // 9) 불변식 복구 (재고를 직접 고친 뒤 reconcile) -----------------------------
    val x = fresh()
    x.pizzas[PizzaSlices.idx(4, 2)] = 3
    PizzaSlices.reconcile(x)
    check(x.slicesOf(4) == 24 && x.pizzaCountOf(4) == 3, "reconcile 조각 채우기 오류: ${x.slicesOf(4)}")
    x.pizzaSlices[PizzaSlices.idx(4, 2)] = 999
    PizzaSlices.reconcile(x)
    check(x.slicesOf(4, 2) == 24, "reconcile 조각 자르기 오류: ${x.slicesOf(4, 2)}")
    x.pizzas[PizzaSlices.idx(4, 2)] = 0
    PizzaSlices.reconcile(x)
    check(x.slicesOf(4) == 0, "판이 없는데 조각이 남음")

    println("통과 $passed / 실패 $failed")
    if (failed > 0) kotlin.system.exitProcess(1)
}
