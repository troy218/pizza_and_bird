# P1. 계절 시스템 코어 + 계절 연출

> 웨이브 **W1** · 규모 M · 담당 1명
> 의존: 없음 (다른 W1 파트와 동시 착수 가능)

---

## 1. 목표

게임 내 날짜(`GameState.day`, 침대에서 자거나 자정을 넘기면 +1)를 **4계절**로 해석해,
월드 연출(식물 색조·파티클)과 날씨 빈도가 계절에 따라 바뀌게 한다.
이 파트는 **코어(계절 판정·가중치 데이터)와 연출만** 담당한다 — 새 출현 테이블은 P2, 계절 대사는 P8이 이 코어를 import해서 만든다.

## 2. 스토리 맥락

할머니의 수첩 마지막 장은 "함께 사는 지도"로 끝났지만, 지도는 시간이 지나면 다시 그려져야 한다.
봄에 본 벚꽃길이 가을엔 은행길이 되고, 겨울 철원 평야는 두루미의 계절이다. **1년이 도는 세계**를 만들어
"언제 와도 다른 게임"이라는 인상을 주는 것이 목표. 동시에 도감의 새들이 "추천 시기"(README 탐조지 표)와
실제 게임에서 만나는 시기가 일치하도록 만드는 첫 단계다.

## 3. 플레이어 경험

- 잠들거나 자정을 넘기면서 날짜가 바뀌면 계절이 서서히 진행된다 (기본: 5게임일 = 1계절, 20게임일 = 1년).
- 광장 나무·풀·꽃의 색조가 계절에 따라 변한다 (봄 연두+분홍포인트 / 여름 짙은 초록 / 가을 주황·노랑 / 겨울 잎 없는 가지 + 눈).
- 계절 파티클: 봄 꽃잎, 여름 반딧불(밤), 가을 낙엽, 겨울 추가 눈송이(눈 날씨와 무관한 미세 강설은 선택).
- 날씨 빈도가 계절을 반영한다: 봄=맑음↑, 여름=비↑(소나기 느낌), 가을=강풍·맑음↑, 겨울=눈↑.
- 큰 지도(MapOverlay) 상단에 현재 계절과 며칠째인지 표시된다.

## 4. 소유 파일

### 새 파일 (전액 소유)

**`app/src/main/java/com/pizzaandbird/game/Seasons.kt`** — 파일 상단 KDoc에 `[P01]` 명시.

```kotlin
// [P01] 계절 코어 — P2(스폰)·P8(대사)가 이 파일의 API를 import한다. (OVERVIEW §5 계약)
enum class Season(val label: String, val icon: String) { SPRING, SUMMER, AUTUMN, WINTER }

object Seasons {
    const val DAYS_PER_SEASON = 5            // 5게임일 = 1계절 (OVERVIEW Q1 기본값)
    fun of(day: Int): Season                 // day 1..5 → SPRING ... 16..20 → WINTER, 21부터 반복
    fun label(day: Int): String              // 예: "가을 3일째"
    fun dayInSeason(day: Int): Int

    // ▼ WorldScene.updateWeather가 사용 (values() 순서와 동일한 Double 리스트)
    fun weatherWeights(s: Season): List<Double>

    // ▼ Fx 연출용 계절 파라미터 (색상·파티클 종류는 이 코어가 소유)
    data class SeasonMood(...)               // 잔디 hue shift, 나뭇잎 팔레트, 파티클 종류 등
    fun mood(s: Season): SeasonMood
}
```

규칙:
- 계절 판정은 **항상 `GameState.day`에서 파생**한다. 세이브에 계절 필드를 두지 않는다 (규칙 3 준수 — GameState 무수정, 저장 자체가 불필요).
- `Seasons.kt`는 다른 게임 코드에 의존하지 않는 **순수 로직 파일**로 유지한다 (단위 테스트·프리뷰 도구 이식이 쉬움). 색상 상수는 `android.graphics.Color` 상수로.

### 패치 파일 (앵커 준수)

#### ① `Fx.kt` — 계절 틴트·파티클 연결
- 앵커: `Fx` 클래스의 렌더/업데이트 경로 중 식물(잔디·나무) 색을 결정하는 지점과 앰비언트 파티클 스폰 지점 (검색어 `낙엽`, `반딧불`, `ambient`).
- 변경 예산: **60줄 이내**.
- `Seasons.mood(state.day → Season)`를 받아 기존 색 계산을 틴트하는 방식으로 구한다. 기존 색 팔레트를 지우지 말고, 계절 팔레트로의 **블렌드 계수**를 추가하는 방식으로 (비주얼 회귀 방지).
- 겨울: 나뭇잎 레이어 알파↓ + 기존 눈 쌓임 로직(`Fx`의 snow 축적)이 날씨와 무관하게 아주 얕게 상시 유지되도록(선택).

#### ② `Weather.kt` — 계절 가중치 훅
- 앵커: `fun weatherBirdMultiplier` **아래 쪽에 새 함수 추가만** (기존 enum/함수 무수정).
- 추가: `fun rollWeather(rnd: Random, season: Season): Weather` — `Seasons.weatherWeights(season)`으로 가중 추첨. 맑음/흐림/비/강풍/눈 순서 = `Weather.values()` 순서.

#### ③ `WorldScene.kt` — updateWeather에 계절 적용
- 앵커: `private fun updateWeather(dt: Float)` 낸 날씨를 새로 고르는 지점.
- 변경: 기존 무작위 추첨을 `rollWeather(rnd, Seasons.of(state.day))` 호출로 교체. **예산 10줄 이내, 이 함수 외 수정 금지.**
- 계절 전환 토스트 1줄 추가 허용: 날짜가 바뀌어 계절이 바뀐 순간 `game.toast("${season.icon} ${season.label}이(가) 찾아왔어요")` (앵커: day가 증가하는 지점 — 없으면 부분 스킵하고 이슈로 기록).

#### ④ `Overlays.kt` — MapOverlay에 계절 표시 (예산 15줄)
- 앵커: `class MapOverlay`의 `override fun draw` (검색어 `class MapOverlay`).
- 지도 상단 바 근처에 `${Seasons.label(day)}` + 계절 아이콘 1줄 그리기. `Seasons.kt` import만 추가.

### 절대 건드리면 안 되는 것

- `GameState.kt` (계절 저장 불필요 — day에서 파생)
- `Data.kt`, `BirdChecklist.kt`, `Maps.kt`, `Grass.kt`
- `WorldScene.kt`의 `updateWeather` 이외 함수 — 스폰/촬영/대화는 P2·P3·P8 영역
- `Hud.kt` (계절 표시는 MapOverlay에서만 — Hud는 P9 영역)

## 5. 파라미터 표 (초안 — 밸런스는 통합 QA에서 조정)

| 계절 | 맑음 | 흐림 | 비 | 강풍 | 눈 | 식물 무드 | 파티클 |
|---|---:|---:|---:|---:|---:|---|---|
| 봄 🌸 | 0.42 | 0.22 | 0.18 | 0.14 | 0.04 | 연두색 잔디(+밝기), 나무 연두+연분홍 | 꽃잎 |
| 여름 ☀ | 0.45 | 0.18 | 0.25 | 0.10 | 0.02 | 짙은 초록(채도+) | 낮 나비↑ / 밤 반딧불↑ |
| 가을 🍂 | 0.40 | 0.20 | 0.12 | 0.24 | 0.04 | 주황·노랑·적갈색 잎 | 낙엽↑ |
| 겨울 ❄ | 0.30 | 0.22 | 0.06 | 0.12 | 0.30 | 잎 적음, 잔디 올리브·회색 | 미세 눈송이 |

- 눈 확률이 올라가도 기존 `Weather` 체계(새 가중치·연출)가 그대로 따라오므로 추가 로직 불필요.
- `DAYS_PER_SEASON`은 상수 하나로 조정 가능해야 함 (Q1 결정에 따라 변경).

## 6. 테스트 / 검증

1. `./gradlew assembleDebug` 성공.
2. `python3 tools/kt_check.py` 통과.
3. 수동 시나리오:
   - 침대에서 반복 취침 → `day` 증가 → MapOverlay의 계절 표시 갱신.
   - 봄/여름/가을/겨울 각 상태에서 광장 스크린샷 1장씩 —식물 색·파티클이 표와 일치하는지.
   - 겨울에 50~105초 날씨 주기로 눈이 자주 오는지 (확률표와 큰 모순 없을 것).
   - 밤 조명·그림자 등 기존 연출 회귀가 없는지 (비교 스크린샷).
4. (선택) `tools/preview/` 계열 파이썬 프리뷰에 계절 모드를 얹는다 — 게임 코드와 무관해서 파일 소유 충돌 없음.

## 7. 완료 조건 (DoD)

- [ ] `Seasons.kt` 신규, `[P01]` KDoc, 순수 로직 유지 (WorldScene 등 무참조)
- [ ] `Seasons.of/label/weatherWeights/mood` 구현 + 표의 가중치 반영
- [ ] Fx 계절 틴트 + 계절 파티클 4종 동작, 기존 연출 무회귀
- [ ] 날씨 추첨이 계절 가중치 사용 (`updateWeather` 10줄 이내 수정)
- [ ] MapOverlay 계절 표시
- [ ] 토스트: 계절 전환 안내 (가능한 경우)
- [ ] 빌드/kt_check 통과, PR에 계절별 스크린샷 4장 첨부
- [ ] 인터페이스 계약(OVERVIEW §5)과 API 시그니처 일치 확인 — P2/P8이 `Season`/`Seasons.of/label`을 기다리고 있음

## 8. 인터페이스 (내가 남기는 것)

| API | 의미 |
|---|---|
| `enum Season { SPRING, SUMMER, AUTUMN, WINTER }` | 계절 |
| `Seasons.of(day): Season` | 날짜→계절 |
| `Seasons.label(day): String` | "가을 3일째" |
| `Seasons.weatherWeights(s): List<Double>` | 날씨 추첨 가중치 |
| P8 힌트 | 계절별 감성 키워드가 대사에 필요하면 `Season.label`/`icon` 조합으로 충분 — 별도 API 불요 |
