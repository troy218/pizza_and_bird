# P11. 피자 조각 · 빠른 피자 등록 · 피자 특성

> 후속 파트 (P1~P10 릴리스 이후) · 규모 M · 담당 1명
> 의존: **P7**(피자 20종·굽기 게이지), **P8**(사이드 스토리 납품), **P5**(백업 수집)
> ⚠️ `GameState.kt` 의 저장 포맷을 **v6 → v7** 으로 올린다. 세이브 마이그레이션 표(§6)가 이 파트의 핵심 리스크.

---

## 1. 목표

사용자 요청 3건을 한 묶음으로 처리한다.

| # | 요청 | 구현 |
|---|---|---|
| ① | **피자 한 판을 구우면 쪼개서 8조각** | 재고의 *표시·소비 단위*를 조각으로 바꾼다. 저장 인덱스는 그대로(판 단위) 두고 `pizzaSlices[]` 를 곁들인다 |
| ② | **빠른 피자 창에 먹을 피자를 골라 등록** | 🍕 버튼 **탭 = 등록한 피자 한 조각**, **길게 누르기 / `Q` = 등록 창(오버레이)**, 등록은 세이브에 저장 |
| ③ | **피자마다 특성 부여** | 피자 20종에 **특성 2개씩**. 배고픔/행운 배율·보너스, 태움 페널티, 커서 속도, 판정 폭, 걸작 추가 행운을 건드린다 |

핵심 제약은 P7과 같다: **피자 id = 세이브 인덱스**. 그래서 특성은 `PizzaDef` 필드를 늘리는 대신
**id → 특성** 별도 표(`PizzaTraits.kt`)로 둔다. 이러면 `Data.kt` 의 `Pizzas.ALL` 리터럴은 **한 줄도 건드리지 않는다**.

---

## 2. 플레이어 경험

- 굽기 완료 → 이제 **판이 아니라 조각 8개**가 생긴다 (`🍕 페퍼로니 ×8조각`). 한 판은 여덟 번의 간식이다.
- HUD 🍕 버튼:
  - **탭** → 등록해 둔 피자의 조각 하나를 바로 먹는다. 남은 조각 수가 토스트에 찍힌다.
  - **길게 누르기(0.42초) 또는 `Q`** → **빠른 피자 창**: 가진 피자 목록에서 고르고 `✅ 등록` / `🍴 지금 한 조각`.
  - 버튼을 누르고 있는 동안 **진행 링**이 그려져 "길게 누르면 창이 열린다"는 사실을 알려준다.
  - 등록한 피자가 있으면 버튼 오른쪽에 **작은 피자 아이콘 + ×N** 이 붙는다.
- HUD 재고 표시도 조각 단위: `🍕 피자 ×2` (판) + `◔ 12조각`.
- 굽기 오버레이: 선택한 피자 카드에 **특성 뱃지 2개**(예: `🌶 매콤`, `🍗 든든`)와 설명이 뜬다.
  게이지의 노란 **걸작 구간 폭도 특성 반영 후 값**(`perfectWEff`)으로 그린다. 굽기 게이지 화면에서는
  칩 아래에 **특성 설명을 한 줄씩** 풀어 써서(여유 공간이 있을 때만) "매콤이 뭔지" 알 수 있게 한다.
  성공 시 결과는 "한 판 = 8조각 · 조각당 +12 🍖 +2 🍀" 처럼 **조각 단위 수치**로 안내한다.
- 메뉴 → 피자 탭: 판/조각 재고, **특성 뱃지**, 빠른 피자 **등록 버튼**까지 한 화면에서.
- 사이드 스토리 납품은 그대로 **한 판 단위** (조각으로 쪼개진 피자를 "반 판" 내줄 수는 없다).

---

## 3. 소유 파일

### 새 파일 (전액 소유)

**`PizzaTraits.kt`** — `[P11]` 특성 enum + `BY_PIZZA` 표 + `PizzaDef` 확장 함수.

```kotlin
enum class PizzaTrait(val label: String, val chip: String, val desc: String,
    val hungerMult: Float, val luckMult: Float, val hungerFlat: Int, val luckFlat: Int,
    val burntMult: Float, val perfectLuck: Int, val cursorMult: Float, val zoneMult: Float)

object PizzaTraits {
    val BY_PIZZA: Map<Int, List<PizzaTrait>>   // 피자 id → 특성 2개
    fun of(pizzaId: Int): List<PizzaTrait>
    fun missing(): List<Int>                   // 특성 배정이 빠진 id (테스트용)
}

val PizzaDef.traits: List<PizzaTrait>          // = PizzaTraits.of(id)
val PizzaDef.traitChips: String                // "🌶 매콤 · 🍗 든든"
fun PizzaDef.cursorSpeedEff(): Float           // 커서 속도 × cursorMult 곱
fun PizzaDef.perfectWEff(): Float              // 걸작 구간 폭 × zoneMult 곱
fun PizzaDef.difficultyEff(): Int              // 1..5 로 조여 반환
fun PizzaDef.difficultyDotsEff(): String       // "●●●○" 표시용
```

수치 배율(`hungerMult` 등)은 **특성 enum이 들고 있고**, 적용은 `PizzaSlices.sliceHunger/sliceLuck` 한 곳에서만 한다
— 계산식을 한 파일에 모아 두어야 밸런스 수정이 한 줄로 끝난다.

**`PizzaSlices.kt`** — `[P11]` 조각 계산 + 재고 불변식.

```kotlin
object PizzaSlices {
    const val PER_PIZZA = 8; const val HUNGER_F = 0.20f; const val LUCK_F = 0.15f

    fun idx(pizzaId: Int, quality: Int): Int          // = pizzaId * 3 + quality (P7 규칙 그대로)
    fun of(s: GameState, pizzaId: Int): Int           // 이 피자의 남은 조각(품질 무관)
    fun of(s: GameState, pizzaId: Int, quality: Int): Int
    fun total(s: GameState): Int                      // 배낭의 전체 조각
    fun ofKind(s: GameState, kind: PizzaKind): Int
    fun bestQualityOf(s: GameState, pizzaId: Int): Int

    fun onBaked(s: GameState, pizzaId: Int, quality: Int)   // 굽기 성공 → 판 +1 / 조각 +8
    fun reconcile(s: GameState)                             // pizzas[] ↔ pizzaSlices[] 불변식 복구
    fun removePan(s: GameState, index: Int): Boolean        // index = pizzaIdx — 조각 8개와 판 1개를 함께
    fun removePanOf(s: GameState, pizzaId: Int): Boolean    // 제일 좋은 품질의 판 하나 (납품용)

    fun sliceHunger(def: PizzaDef, q: PizzaQ): Int          // 조각 하나의 배고픔 회복량
    fun sliceLuck(def: PizzaDef, q: PizzaQ): Int            // 조각 하나의 행운
    fun sliceLine(def: PizzaDef, q: PizzaQ): String         // "조각당 배고픔 +15 · 행운 +3"

    fun takeSlice(s: GameState, pizzaId: Int): Bite?        // 좋은 품질부터 한 조각
    fun takeBest(s: GameState): Bite?                       // 배낭 전체에서 제일 좋은 조각
    fun takeQuick(s: GameState): Bite?                      // 등록한 빠른 피자 우선, 없으면 takeBest

    class Bite(val pizzaId: Int, val quality: Int, val hunger: Int, val luck: Int,
               val slicesLeftOfQuality: Int, val slicesLeft: Int, val panFinished: Boolean = false) {
        var fromQuick = false                     // 등록 피자를 먹었는지(자동 선택이면 false)
        val def: PizzaDef; val q: PizzaQ; val label: String
    }
}
```

`GameState` 에는 얇은 래퍼를 붙여 호출부가 배낭을 직접 다루지 않게 했다:
`sliceCount`, `slicesOf(id[, q])`, `slicesOfKind(kind)`, `quickPizza()`, `setQuickPizza(id)`,
`eatSlice(id)`, `eatBestSlice()`, `eatQuick()`, `removePanAt(idx)`, `removePanOf(id)` —
그리고 기존 호출부를 깨지 않도록 `eat(id): PizzaQ?`, `eatBest(): Int?` 는 그대로 살아 있다.

**`QuickPizzaOverlay.kt`** — `[P11]` 빠른 피자 등록 창 + 씬 확장 함수(`openQuickPizza`, `quickEatSlice`).
세 씬(World/Home/Landmark)이 같은 코드를 쓰도록 **Scene 확장 함수로 모았다**.

**`tools/jvm_stub/Json.kt`** — org.json 의 **JVM 실행용 구현**(테스트 전용, 앱 빌드 미포함).
`GameState` 는 `android.graphics` 를 안 쓰므로 이 스텁만 있으면 저장 로직을 PC에서 실제로 돌릴 수 있다.

### 패치 파일 (앵커 준수)

| 파일 | 변경 내용 |
|---|---|
| `GameState.kt` | `pizzaSlices`(IntArray 60), `quickPizzaId`, 저장 **v7** + 마이그레이션, `addPizza`/`addPizzaAll`/`removePanAt`/`removePanOf`/`eat`/`eatBest`/`eatQuick`/`eatSlice`/`setQuickPizza`/`slicesOf`/`sliceCount` |
| `Input.kt` | `justEat`(떼는 순간 발동), `justEatPick`(길게 누르기 / `Q`), `eatHoldT`, `EAT_HOLD_MS = 420` |
| `Hud.kt` | 🍕 버튼에 **홀드 링**·**등록 피자 아이콘**, 상단 재고에 조각 수 뱃지 |
| `Overlays.kt` | `BakeOverlay`: 특성 뱃지·설명, `perfectWEff` 게이지, 결과 안내를 조각 단위로. `MenuOverlay` 피자 탭: 판/조각 표시 + 특성 뱃지 + `✅ 등록`/`🍴 지금 한 조각` 버튼 |
| `WorldScene.kt` / `HomeScene.kt` / `LandmarkScene.kt` | 먹는 입력 처리를 `Scene.quickEatSlice()` / `Scene.openQuickPizza()` 호출로 교체 (인라인 로직 삭제) |
| `SideStories.kt` | `consumeDeliverable` → `removePanAt(idx)` (납품은 판 단위 유지) |

### 절대 건드리면 안 되는 것

- `Data.kt` 의 `Pizzas.ALL` 리터럴 — 특성은 별도 표로. (피자 id/순서/개수 불변)
- 저장 키 이름(`PIZZA_KEY`, `v`, `pizzas`, `pizzaSlices`, `quickPizzaId`) — v7 이후 재변경 금지.
- `Achievements.kt` 의 피자 카운트(판 단위) — "피자 N판 굽기" 업적은 판이 맞다.

---

## 4. 특성 11종

| 뱃지 | 이름 | 배고픔 | 행운 | 태움 보정 | 커서 | 판정 폭 | 의도 |
|---|---|---|---|---|---|---|---|
| 🌶 | 매콤 | ×1.10 | −1 (고정) | — | — | — | 매운맛이 입맛을 돋움 — 배는 든든, 행운은 살짝 덜 |
| 🍯 | 달콤 | ×0.95 | ×1.20 | — | — | — | 기분 좋은 마무리 — 행운 피자 |
| 🧀 | 고소 | +2 (고정) | — | — | — | — | 치즈의 고소함이 조각 하나를 더 든든하게 |
| 🫓 | 바삭 | ×0.90 | — | — | ×0.92 | ×1.10 | 굽기 쉽고 판정 넉넉, 배는 조금 덜 참 |
| 🔥 | 불맛 | — | — | ×1.6 | — | — | 화덕 불향 — 살짝 타도 맛있고, 걸작이면 행운 +2 |
| 🌿 | 신선 | — | +1 (고정) | — | — | ×1.08 | 생채소·허브 — 행운을 부르고 굽는 손길도 너그러움 |
| 🍗 | 든든 | ×1.15 | — | — | ×1.06 | — | 재료가 듬뿍 — 배는 확 차지만 타이밍은 조심 |
| ✨ | 진미 | — | ×1.10 | — | — | ×0.92 | 최상급 재료 — 걸작이면 행운 +3, 판정은 까다로움 |
| 🥔 | 쫀득 | ×1.08 | — | — | — | ×1.05 | 쫀득한 도우 — 덜 익어도 괜찮고 배도 든든 |
| 🐟 | 해물 | ×0.95 | +2 (고정) | ×0.7 | — | — | 바다 향이 행운을 부르지만 태우면 비려져 급락 |
| 🌰 | 구수 | ×1.10 | — | ×1.3 | — | — | 노릇하게 구운 단맛 — 살짝 타도 버릴 게 없음 |

- **한 피자에 특성 2개**, 순서도 의미 있다(`traits[0]` 이 먼저 표시됨).
- `burntMult` 는 **태운 품질(BURNT)에만** 곱해진다 → 🐟 해물 0.7 / 🔥 불맛 1.6 / 🌰 구수 1.3.
- `cursorMult`, `zoneMult` 는 굽기 미니게임의 **실제 난이도**를 바꾼다(`cursorSpeedEff`, `perfectWEff`).
- `perfectLuck` 은 **걸작 품질의 조각**에만 더해진다(🔥 +2, ✨ +3).

## 5. 조각 한 개의 효과

```
base    = (품질 기본치 + PizzaDef.hungerBonus) × HUNGER_F(0.20)
          품질 기본치: 살짝 탄 20 · 맛있는 38 · 걸작 60      (행운은 5 · 10 · 18, LUCK_F = 0.15)
×       = 곱 특성 전부 (매콤·든든·달콤·바삭·쫀득·해물·구수 …)
태움    = quality == BURNT 이면 × burntMult
→ 반올림 → + 고정 보너스(hungerFlat: 🧀 고소 +2) → 최소 1
```

→ **조각 하나 ≈ 옛 "한 판 통째"의 25% 남짓.** 한 판을 전부 먹으면 예전보다 1.6~2배 넉넉해지는데,
배고픔은 100에서 막히고 시간 지나면 다시 줄어드는 데다 여덟 번에 나눠 먹으므로 체감 차이는 크지 않다.
과하다고 판단되면 **`PizzaSlices.HUNGER_F` 상수 하나만** 내리면 전체 밸런스가 함께 내려간다 (조각 값은 전부 이 함수에서 나온다).

행운도 같은 구조(`LUCK_F = 0.15`, 고정 `luckFlat`: 🌶 −1 · 🌿 +1 · 🐟 +2, 걸작이면 `+perfectLuckBonus`).
배고픔은 **최소 1** 보장(아무리 형편없는 조각도 굶기지는 않는다), 행운은 **최소 0**(깎이지는 않는다).

### 실측 표 (tools/PizzaTest.kt 가 검증한 값)

`조각 배고픔(탄/맛/걸작)` · `조각 행운(탄/맛/걸작)` · 커서·판정폭은 `원본→특성 반영` · 난이도는 `원본→반영`.

| id | 피자 | 특성 | 배고픔 | 행운 | 커서 | 판정폭 | 난이도 |
|---|---|---|---|---|---|---|---|
| 0 | 치즈 | 🧀 고소 · 🥔 쫀득 | 6/10/15 | 1/2/3 | 1.00→1.00 | 0.26→0.27 | 1→1 |
| 1 | 버섯 | 🌰 구수 · 🌿 신선 | 5/7/12 | 3/3/5 | 1.15→1.15 | 0.32→0.35 | 2→1 |
| 2 | 불고기 | 🍗 든든 · 🍯 달콤 | 6/10/15 | 1/2/4 | 1.38→1.46 | 0.20→0.20 | 3→4 |
| 3 | 페퍼로니 | 🌶 매콤 · 🍗 든든 | 7/11/17 | 0/1/2 | 1.10→1.17 | 0.26→0.26 | 1→2 |
| 4 | 고구마 | 🍯 달콤 · 🌰 구수 | 7/9/13 | 2/3/4 | 1.22→1.22 | 0.26→0.26 | 2→2 |
| 5 | 콤비네이션 | 🍗 든든 · 🌿 신선 | 7/11/16 | 2/3/4 | 1.30→1.38 | 0.22→0.24 | 3→3 |
| 6 | 마르게리타 | 🌿 신선 · 🔥 불맛 | 8/8/13 | 4/4/7 | 1.45→1.45 | 0.22→0.24 | 3→2 |
| 7 | 마리나라 | 🌿 신선 · 🫓 바삭 | 4/7/11 | 3/4/5 | 1.40→1.29 | 0.24→0.29 | 3→2 |
| 8 | 콰트로 포르마지 | 🧀 고소 · 🍗 든든 | 9/13/18 | 2/2/4 | 1.55→1.64 | 0.20→0.20 | 4→5 |
| 9 | 고르곤졸라 | 🍯 달콤 · ✨ 진미 | 4/8/12 | 4/5/9 | 1.60→1.60 | 0.18→0.17 | 4→5 |
| 10 | 디아볼라 | 🌶 매콤 · 🔥 불맛 | 11/11/16 | 1/1/4 | 1.70→1.70 | 0.18→0.18 | 5→5 |
| 11 | 루꼴라 프로슈토 | 🌿 신선 · ✨ 진미 | 6/9/14 | 4/5/9 | 1.75→1.75 | 0.16→0.16 | 5→5 |
| 12 | 춘천 닭갈비 | 🌶 매콤 · 🍗 든든 | 8/13/18 | 0/1/2 | 1.32→1.40 | 0.24→0.24 | 3→4 |
| 13 | 강릉 감자 옹심이 | 🥔 쫀득 · 🌰 구수 | 11/12/18 | 1/2/3 | 1.30→1.30 | 0.24→0.25 | 3→2 |
| 14 | 속초 오징어 | 🐟 해물 · 🧀 고소 | 6/11/15 | 3/4/5 | 1.48→1.48 | 0.20→0.20 | 4→4 |
| 15 | 전주 콩나물 비빔 | 🌶 매콤 · 🌿 신선 | 7/11/16 | 1/2/3 | 1.36→1.36 | 0.22→0.24 | 4→3 |
| 16 | 대구 납작 치즈 | 🫓 바삭 · 🧀 고소 | 7/10/14 | 1/2/3 | 1.30→1.20 | 0.22→0.24 | 3→2 |
| 17 | 광주 상추 육전 | 🌿 신선 · 🍗 든든 | 7/12/17 | 2/3/4 | 1.44→1.53 | 0.20→0.22 | 4→4 |
| 18 | 부산 어묵 꼬치 | 🐟 해물 · 🥔 쫀득 | 4/10/15 | 3/4/5 | 1.52→1.52 | 0.18→0.19 | 4→3 |
| 19 | 제주 흑돼지 | 🍗 든든 · ✨ 진미 | 8/12/17 | 2/3/7 | 1.60→1.70 | 0.18→0.17 | 5→5 |

읽는 법: **디아볼라(10)** 는 태워도 11(맛있는 것과 같다) — 🔥 불맛 덕분. 반대로 **속초 오징어(14)** 는 태우면 11→6으로 급락.
**고르곤졸라(9)/루꼴라(11)** 는 걸작 조각 하나에 행운 9 — "행운 피자" 정체성이 조각 단위에서도 살아 있다.
한 판(8조각)을 다 먹으면 대략 걸작 기준 **88~144 배고픔**으로, P7 시절 "한 판 = 60~90" 보다 여유롭지만
조각 단위로 쪼개 먹으니 **실제 소모 속도**는 비슷하다(간식 8회).

---

## 6. 저장 포맷 & 마이그레이션 (v7)

```jsonc
{
  "v": 7,
  "pizzas":      [ … 60칸 … ],   // 그대로: pizzaId*3 + quality 인덱스의 **판** 개수
  "pizzaSlices": [ … 60칸 … ],   // [P11] 신규: 같은 인덱스의 **남은 조각** 개수 (0..8)
  "quickPizzaId": 9              // [P11] 신규: 🍕 버튼에 등록한 피자 id (-1 = 없음)
}
```

불변식: **`pizzas[i] == ceil(pizzaSlices[i] / 8)`**. 어긋나면 `PizzaSlices.reconcile()` 이 판 수를 기준으로 조각을 복구한다
(판이 있는데 조각이 0이면 8로 채우고, 조각이 8×판을 넘으면 잘라 낸다). `fromJSON()` 마지막에 항상 한 번 호출한다.

| 들어오는 세이브 | 처리 |
|---|---|
| v7 | `pizzaSlices`/`quickPizzaId` 그대로 읽음 → `reconcile` |
| v4~v6 (`pizzaSlices` 없음) | 판 재고는 그대로 유지, **보유한 판 1개 = 8조각**으로 채움, `quickPizzaId = -1` |
| v3 (9칸 피자 배열) | 기존 치즈/불고기 변환 규칙 적용 후 똑같이 조각 채움 |
| v1~v2 (품질 3칸) | 기존 치즈 변환 → 조각 채움 |
| 미래 세이브 (v > 7) | 키가 있으면 읽고, 없으면 `reconcile` 이 기본값을 만든다 → **다운그레이드 해도 재고가 통째로 날아가지 않는다** |

⚠️ 조각만 저장하고 판 저장을 버리면 **롤백이 불가능**해진다. 그래서 판 배열을 남기는 이중 기록 방식을 택했다
(P7의 `pizzas[]` 인덱스 규칙을 그대로 이어간다).

---

## 7. 테스트 / 검증

- **`tools/PizzaTest.kt`** — [P11] 전용 JVM 테스트. 9개 묶음 / **93개 검사 전부 통과**.
  Android 런타임 불필요: `tools/jvm_stub/Json.kt`(org.json 실제 구현) 를 클래스패스 앞에 두면
  `GameState` 저장 로직까지 PC에서 돈다. (`MapTest.kt` 는 `android.graphics.Paint` 스텁 때문에 JVM 실행 불가 —
  이 한계는 P11 이전부터 있던 것이고, 그래서 별도 파일을 만들었다.)

  ```bash
  kotlinc -d /tmp/jsonstub tools/jvm_stub/Json.kt
  kotlinc -cp android.jar:/tmp/game -d /tmp/test tools/PizzaTest.kt
  java -cp /tmp/jsonstub:android.jar:/tmp/game:/tmp/test:kotlin-stdlib.jar PizzaTestKt
  # → 통과 93 / 실패 0
  ```

- **`tools/MapTest.kt`** — 기존 회귀 스위트에도 `[P11]` 블록 추가 (조각/빠른 피자/특성/v7 왕복/v3·v4 마이그레이션).
  실기기·에뮬레이터나 Robolectric 에서 돌린다.
- **`tools/typecheck.sh`** — 게임 소스 전체 타입체크 **오류 0**.

수동 점검 시나리오:

1. 오븐에서 페퍼로니 굽기 → 토스트에 `×8조각`, 재고 `🍕 ×1 · ◔ 8조각`.
2. 🍕 탭 8번 → 조각이 하나씩 줄고, 마지막에 "한 판을 다 먹었다" 토스트 + 재고에서 판이 사라짐.
3. 🍕 길게 누르기(또는 `Q`) → 창 열림 → 고르곤졸라 선택 → `✅ 등록` → HUD 버튼에 아이콘 표시 →
   앱 재시작 후에도 등록 유지.
4. 등록한 피자를 다 먹은 뒤 탭 → 다른 피자로 폴백되고, 등록 자체는 유지.
5. 굽기 오버레이에서 피자별 특성 뱃지 2개 + 걸작 구간 폭이 피자마다 다른지 확인.
6. 사이드 스토리 피자 납품: 조각이 남아 있어도 **한 판**이 통째로 나감.
7. v6 세이브(백업 코드 포함) 복원 → 피자 재고가 조각으로 살아 있는지.

---

## 8. 완료 조건 (DoD)

- [x] `PizzaTraits.kt` / `PizzaSlices.kt` / `QuickPizzaOverlay.kt` 신규, `[P11]` KDoc
- [x] `Data.kt` diff 0 — 특성은 id → 특성 별도 표로
- [x] 저장 **v7**: `pizzaSlices` + `quickPizzaId`, v1~v6 세이브 마이그레이션 검증(PizzaTest 93개 통과)
- [x] 한 판 = 8조각, 먹기는 조각 단위, 판은 조각이 다 떨어져야 사라짐 (`ceil` 불변식 + `reconcile`)
- [x] 🍕 탭 = 등록 피자 한 조각 / 길게 누르기·`Q` = 등록 창, 홀드 링 + 등록 아이콘 표시
- [x] 세 씬(World/Home/Landmark)이 같은 `Scene` 확장 함수 사용 — 인라인 중복 코드 제거
- [x] 특성 20종 × 2개 전부 배정, 굽기 게이지(`perfectWEff`)·커서(`cursorSpeedEff`)·난이도 표시 반영
- [x] 사이드 스토리 납품은 판 단위 유지 (`removePanAt`)
- [x] `tools/typecheck.sh` 전체 타입체크 오류 0 + `tools/PizzaTest.kt` 통과
- [x] 문서: `README.md`(조작법·코어 루프·파일 트리), `docs/DESIGN.md` §2.3, `docs/plan/OVERVIEW.md`

## 9. 인터페이스 (내가 남기는 것)

| API | 소비자 | 의미 |
|---|---|---|
| `GameState.eatQuick()` / `eatSlice(id)` / `setQuickPizza(id)` / `slicesOf(id, q)` | HUD·오버레이·씬 | 조각 단위 먹기/조회 |
| `PizzaSlices.Bite` (`slicesLeft`, `panFinished`, `fromQuick`) | 토스트 문구 | 한 조각 먹은 결과 |
| `PizzaDef.traits` / `traitChips` / `cursorSpeedEff()` / `perfectWEff()` / `difficultyDotsEff()` | 굽기 오버레이, 피자 탭, 밸런스 작업 | 특성 표시·반영 값 |
| 저장 키 `pizzaSlices`, `quickPizzaId` | **P5**(백업 수집은 버전 무관 덤프라 자동 포함) | v7 세이브 |

## 10. 롤백

`GameState` 에서 v7 쓰기(`put("v", 7)` → `6`)와 두 키 직렬화만 되돌리면 **v6 호환 상태로 돌아간다**.
읽기 쪽 마이그레이션과 `reconcile` 은 남겨도 해가 없다(키가 없으면 무시된다).
`PizzaTraits.kt` / `PizzaSlices.kt` / `QuickPizzaOverlay.kt` 는 새 파일이라 삭제만으로 정리되고,
`Data.kt` 는 손을 대지 않았으므로 피자 정의 롤백은 필요 없다.
