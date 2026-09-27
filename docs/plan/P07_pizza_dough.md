# P7. 피자 확장 — 도우 종류 · 지역 특산 토핑 · 재료 경제

> 웨이브 **W2** · 규모 L · 담당 1명
> 의존: 없음. ⚠️ `Data.kt`와 `HomeScene.kt`는 이 파트 전용 — 다른 파트가 건드리면 즉시 보고.

---

## 1. 목표

피자 만들기를 **① 도우 선택 → ② 피자 선택 → ③ 타이밍 굽기** 3단으로 확장하고,
각 지역 여행지에 **특산 재료 피자 8종**을 추가해 "지역별로 다른 집밥"을 만든다.
핵심 제약: **피자 id = 세이브 인덱스** 이므로 메뉴는 반드시 끝에 추가한다 (규칙 4).

## 2. 스토리 맥락

피자는 이 게임의 주인공이 세계와 맺는 가장 일상적인 계약이다 — 굽고, 싸 들고, 나눠 먹고, 길을 떠난다.
"두 할머니의 수첩" 이야기가 새를 낳았다면, 피자 이야기는 **산지와 사람**을 낳는다:
춘천에서 닭갈비를, 부산에서 어묵을 얹어 먹는 순간 플레이어는 그 지역을 **입으로 기억**한다.
P8의 지역 에피소드 중 하나는 이 특산 피자를 NPC에게 건네는 심부름이다(선택적 연동, §8).

## 3. 플레이어 경험

- 집에서 화덕/오븐에 말을 걸면 → **도우 선택(3종)** → 기존 피자 선택 + 아래에 **지역 특산 탭(현재 지역 한정)** → 기존 타이밍 굽기.
- 도우는 굽기 난이도와 결과 효과를 바꾼다 (§5 표).
- 지역 특산 피자는 **그 지역에 있을 때만 만들 수 있는 현지 메뉴** (해금 개념 없음 — 방문 자체가 해금).
- 팬트리(냉장고 느낌): 지역을 다니며 사 둔 특산 재료는 집에 보관되지만, **사용은 각 지역에서만** — 이 단순함이 기획과 코드를 모두 안전하게 한다.

## 4. 소유 파일

### 새 파일 (전액 소유)

**`app/src/main/java/com/pizzaandbird/game/Ingredients.kt`** — `[P07]` KDoc.

```kotlin
// [P07] 도우/특산 재료 — 저장은 기본 prefs feat_pantry_v1 키(규칙 3). 특산 재고는 재료 단위로 관리한다.
enum class Dough(val label: String, val icon: String, val price: Int, val gaugeSpeed: Double, val bonusLabel: String)
object Ingredients {
    data class ToppingDef(val id: String, val label: String, val icon: String,
                          val regionId: String,      // 이 재료를 쓸 수 있는 지역(town) id
                          val price: Int, val hungerBonus: Int, val luckBonus: Int,
                          val crustColors: Pair<Int, Int>)  // 아이콘 자동생성용 (기존 규칙)
    val TOPPINGS: List<ToppingDef>              // §5 8종 — id는 문자열(세이브 append 안정성)
    fun toppingsFor(regionId: String): List<ToppingDef>       // 특산 탭 채우기
    fun stock(ctx: Context, toppingId: String): Int
    fun buy(ctx: Context, toppingId: String): Boolean         // 골드 차감은 호출부
    fun consume(ctx: Context, toppingId: String): Boolean
}
```

**`app/src/main/java/com/pizzaandbird/game/DoughOverlay.kt`** — `[P07]` KDoc.

- `Overlay` 서브클스: ① 도우 3종 카드(아이콘·가격·설명·난이도 아이콘) → 선택 시 다음 단계로.
- ② 피자 선택(기존 12종 + 현재 지역 특산 탭)은 **기존 BakeOverlay의 선택 단계를 확장**하므로 이 파일은 ①만 담당하고, 선택 완료 시 `BakeOverlay`를 열어 넘긴다.
- 특산 재료 구매: 특산 탭에서 "재료 사들이기 ₩__" 표시, 재고가 있으면 재료 소모 표시.

### 패치 파일 (앵커 준수)

#### ① `Data.kt` — Pizzas 끝에 8종 append (⚠️ 절대 중간 삽입 금지)
- 앵커: `object Pizzas` 내 `val ALL = listOf(...)` 리터럴의 **마지막 항목 뒤** (현재 id 11 루꼬라 프로슈토).
- id **12~19**로 8종 추가: id·순서 확정 후 **다시는 바꾸지 않는다** (세이브 `pizzas[id*3+품질]` 인덱스 고정).
- 특산 피자도 결국 피자 목록에 있어야 아이콘/도감/간식 버튼이 작동한다. 단, 메뉴 카탈로그 탭(Overlays 피자 탭)에는 특산 전용 소분류를 추가하지 말고 **🔥/🍕 서브탭 속에 "✈ 특산" 뱃지**로 표시 (피자 탭 enum/구조 무수정, 카드 그리기 라벨만 조건부 — 예산 8줄).
- 아이콘: 기존 자동 생성 규약(바탕+토핑 색 2종)에 맞춰 색상만 지정 — `Assets.kt` **무수정**.

#### ② `Overlays.kt` — BakeOverlay 시그니처 확장 (예산 60줄, 해당 클래스 한정)
- 앵커:
  ```kotlin
  class BakeOverlay(scene: Scene, private val kind: PizzaKind = PizzaKind.OVEN) : Overlay(scene) {
  ```
- 생성자에 기본값 있는 파라미터만 **뒤에** 추가: `private val dough: Dough = Dough.CLASSIC, private val topping: Ingredients.ToppingDef? = null`
  (기본값 덕에 기존 호출(`BakeOverlay(scene, kind)`)이 깨지지 않는다 — 시그니처 하위 호환 유지, 규칙 6의 "기존 클래스 생성자 변경 금지"의 예외로 이 파트에 한해 허용되며 **파라미터 뒤추가+기본값** 형식만 가능).
- 타이밍 게이지 속도·판정 폭에 `dough.gaugeSpeed`·특산 별도 보정 적용, 결과 계산에 `topping` 별도 토핑 별 setText.
- 특산 피자를 선택했는데 `topping == null`(재료 0)이면 굽기 시작 불가 토스트.
- 이 클래스 외 Overlays 코드(다른 오버레이·메뉴 탭 본문)는 무수정 — 피자 카탈로그 탭의 "✈ 특산" 표시만 위 ①에 조건부로 허용.

#### ③ `HomeScene.kt` — 굽기 진입을 도우 선택으로 교체 (예산 40줄, 해당 함수 한정)
- 앵커: 화덕/오븐 상호작용 처리부 (검색어 `BakeOverlay(` — 현재 `openOverlay(BakeOverlay(this, kind))` 형태).
- 교체: `openOverlay(DoughOverlay(this, kind))` — DoughOverlay가 ①도우→②BakeOverlay(기존 선택 단계) 순서로 이어 붙인다. `BakeOverlay` 선택 단계에 특산 탭은 ②에 포함.
- 재료 구매 진입: 특산 탭에서 구매 → 즉 차감·재고 증가 (상점 UI 불필요).
- HomeScene의 나머지(침대·인테리어·이사)와 `playBgm` 라인 무수정.

### 절대 건드리면 안 되는 것

- 기존 피자 id 0~11, 기존 `Pizzas` 항목 값(가격·난이도·효과) — 재조정은 통합 QA 과제로만
- `Assets.kt`(아이콘 파이프라인), `Audio.kt`
- `WorldScene.kt` 전체(간식 버튼·경험치 등은 기존 Pizzas.ALL 순회가 자동 커버)
- 세이브 스키마 — `pizzas` 배열은 기존 v4 포맷 그대로, 특산 종은 id 12~19로 자연 확장

## 5. 데이터 표 (초안 — Q3 결정 후 확정)

### 도우 3종

| id | 도우 | 가격 | 게이지 속도 | 효과 |
|---|---|---:|---:|---|
| classic | 🍞 클래식 도우 | ₩0 (기본) | ×1.0 | 기존 밸런스 그대로 |
| thin | 🫓 얇은 화덕 도우 | ₩1,500 | ×1.12 | 행운 별도 +3, 탐구간 소폭 축소 |
| whole | 🌾 통밀 도우 | ₩2,000 | ×0.88 | 배고픔 회복 +8, 탐구간 소폭 확대 |

### 지역 특산 토핑/피자 8종 (id 12~19 — 절대 순서 변경 금지)

| id | 피자 | 지역 | 재료가 | 배고픔+ | 행운+ | 감성 |
|---:|---|---|---:|---:|---:|---|
| 12 | 🍗 춘천 닭갈비 피자 | 춘천 | ₩3,000 | +12 | +3 | 철판 볶음의 고향 |
| 13 | 🥔 강릉 감자 옹심이 피자 | 강릉 | ₩2,500 | +14 | +2 | 쫀득한 산간의 맛 |
| 14 | 🦑 속초 오징어 피자 | 속초 | ₩3,500 | +10 | +5 | 동해 바다 향기 |
| 15 | 🫘 전주 콩나물 비빔 피자 | 전주 | ₩2,800 | +11 | +4 | 밥심의 고장 |
| 16 | 🧀 대구 납작 치즈 피자 | 대구 | ₩2,600 | +9 | +4 | 납작만두 감성 |
| 17 | 🥬 광주 상추 육전 피자 | 광주 | ₩3,200 | +12 | +4 | 무등산 밥상 |
| 18 | 🐟 부산 어묵 꼬치 피자 | 부산 | ₩2,900 | +11 | +3 | 시장 골목 |
| 19 | 🐖 제주 흑돼지 피자 | 제주 | ₩5,000 | +16 | +6 | 돌담길 정식 |

- 특산 피자 굽기 난이도는 지역별 즐거움 정도에 맞춰 커서 속도 1.30~1.60 사이, 걸작 폭 0.18~0.24.
- 특산 피자는 일반/화덕 어느 쪽으로도 가능하다면 계열은 *화덕피자(kind=FIRE)* 로 통일해 "여행지 명물" 포지션을 분리 (구현 편의 + 메뉴 탭 심플).

## 6. 테스트 / 검증

1. 빌드 + `kt_check.py` 통과.
2. 세이브 호환(중요): 기존 세이브(피자 인벤토리 차 있는 상태)에서 업데이트 → 재고·품질 그대로 + 신규 8종이 메뉴에 표시.
3. 시나리오:
   - 서울 집 화덕 → 도우 선택 → 마르게리타 → 통밀 도우로 굽기 → 효과 +8 확인.
   - 춘천 도착 → 집(춘천 소유 필요? — **현재 지역 기준**: 각 지역 맵의 집이 아니라 어디서 굽는가는 HomeScene이므로, 특산 탭 표시 조건은 **현재 플레이어가 있는 지역**으로 한다) → 닭갈비 재료 구매 → 굽기 → 도감/간식 버튼에서 신규 아이콘 정상.
   - 지역 이동하면 이전 특산 탭은 숨음.
   - 재료 0일 때 특산 선택 → 차단 토스트.
4. 경제 체크: 특산 피자 단가가 의뢰 보수에 비해 과하지 않은지 (상한 제주 ₩5,000 ≈ 보통 의뢰 1.5회분).

## 7. 완료 조건 (DoD)

- [x] `Ingredients.kt`/`DoughOverlay.kt` 신규, `[P07]` KDoc
- [x] `Data.kt`는 `Pizzas.ALL` 끝 append(id 12~19)만 — 기존 항목 diff 0 (PR diff로 증명)
- [x] `BakeOverlay` 오버로드(뒤추가+기본값)만, 다른 Overlays 코드 무수정 (피자 탭 "✈ 특산" 뱃지는 스펙 ①이 허용한 조건부 표시)
- [x] HomeScene 진입 교체(굽기 경로)만, 나머지 무수정
- [x] 세이브 호환 확인(기존 재고 보존 — MapTest v4 36칸 마이그레이션 체크) + 아이콘 캡처 `docs/img/p07_special_pizzas.png`
- [x] feat_pantry_v1 키로 재고 저장, 재시작 후 유지 (프리뷰 스텁 prefs로 왕복 검증)
- [x] kt_check/MapTest 통과 + kotlinc 전체 타입체크 통과 (gradle assembleDebug 는 CI 게이트)

## 8. 인터페이스 (내가 남기는 것)

| API | 소비자 | 의미 |
|---|---|---|
| `Ingredients.toppingsFor(regionId)` | P8(피자 납품 에피소드의 지역 판정에 선택 사용) | 지역 특산 목록 |
| 저장 키 `feat_pantry_v1` | **P5** | 백업 수집 |
| 피자 id 12~19 | 통합 QA, P10(스토어 스크린샷 소재) | 특산 피자 |
