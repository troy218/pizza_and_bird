# P6. 업적 & 통계

> 웨이브 **W1** · 규모 M · 담당 1명
> 의존: 없음 (동시 착수 가능)

---

## 1. 목표

플레이의 발자취를 남기는 **모험 기록(통계)**과 장기 목표가 되는 **업적** 시스템.
메뉴(☰)에 「업적 🏅」 탭을 추가하고, 기존 도장 깨기(컬렉션 15세트)와 역할을 나눈다:
컬렉션은 "새를 얼마나 모았나", 업적은 "어떻게 플레이했나".

## 2. 스토리 맥락

탐조가의 성장에는 레벨·칭호가 있고, 도감 완성에는 라이퍼 등급이 있다. 업적은 그 사이의 **에피소드 메달** — 
"밤비를 뚫고 새를 찍은 밤", "피자 100판을 구운 손목". 힐링 게임의 업적은 경쟁이 아니라 회상이어야 해서
문구 톤을 회상형("어느새 걸음이 마라톤 하나를 걸었다")으로 통일한다.

## 3. 플레이어 경험

- 메뉴에 새 탭 **「업적」** — 위에 통계 요약(누적 거리, 촬영 수, 방문 지역 수, 구운 피자 수, 플레이 일수…), 아래에 업적 리스트(해금 날짜 표시).
- 업적 해금 순간 화면 상단 토스트 + 기존 `Sfx.REWARD` 효과음.
- 통계는 도감처럼 세이브에 보존, 일일 목표·순위표 같은 경쟁 요소는 넣지 않는다.

## 4. 소유 파일

### 새 파일 (전액 소유)

**`app/src/main/java/com/pizzaandbird/game/Achievements.kt`** — `[P06]` KDoc.

```kotlin
// [P06] 업적/통계 — 저장은 기본 prefs의 feat_stats_v1 키(규칙 3). GameState 무수정.
object Ach {
    data class Def(val id: String, val title: String, val desc: String, val icon: String, val hidden: Boolean = false)
    val ALL: List<Def>                         // §5 표
    val total: Int get() = ALL.size

    // ---- WorldScene이 호출하는 훅 (모두 이 파일 낸에서 feat_stats_v1에 누적) ----
    fun onGameStart(ctx: Context)              // 세션 시작 시 상태 스냡샷(델타 계산 초기화)
    fun tick(ctx: Context, s: GameState)       // update() 매 프레임 대신 낸부 스로틀(1초) — state 폴더 해금 검사
    fun onMove(ctx: Context, px: Float, bike: Boolean)  // 이동량 누적 (걷기/달리기/자전거 구분)

    // ---- 조회 ----
    fun stats(ctx: Context): Stats             // 누적 거리, 평균 일수, 최다 촬영 지역 등
    fun unlocked(ctx: Context): Set<String>
    fun isUnlocked(ctx: Context, id: String): Boolean
    data class Stats(val distWalkPx: Long, val distBikePx: Long, val daysPlayed: Int, ...)
}
```

해금 검사 설계 (훅 최소화 — 성능·충돌 모두 방어):
- **폴더 방식**: `tick`이 1초 간격으로 `GameState`의 파생값을 읽어 해금 검사 (촬영 수 = `s.photos`, 종 수 = `birdCounts` 합, 3성 수 = `bestStars`, 골드 = `s.money`, 레벨 = `s.level`, 날짜 = `s.day`, 도감 퍼센트…). 훅 2개(`tick`,`onMove`)면 거의 모든 업적이 커버된다.
- **누적형**(이동 거리): `onMove`가 px를 누적 저장.
- 해금 즉시 `game.toast("🏅 업적 — …")`는 호출 맥락(scene 접근)이 없으므로, 토스트는 `tick` 낸에서 scene 접근 없이 **대기열에 넣고** StatsOverlay에서 표시하게 하지 말고 — 간단하게 `game.toast`를 쓸 수 있게 훅 시그니처에 `game: Game`을 넘기도록 조정 가능(`tick(game, s)`). 최종 시그니처는 구현 시 WorldScene과 합의해 한 가지로 통일하고 OVERVIEW §5에 맞출 것.

**`app/src/main/java/com/pizzaandbird/game/StatsOverlay.kt`** — `[P06]` KDoc.

- `Overlay` 서브클스 두 개를 한 파일에: `StatsOverlay`(통계 상세 — 탭의 "통계 보기" 버튼으로 연다)와 업적 상세 팝업용 소형 다이얼로그.
- 목록 행: 아이콘 + 제목 + 설명 + (해금 시) 흐릿한 금색 프레임과 해금 날짜(`day`). 미해금은 잠금 아이콘+설명(히든은 "???").

### 패치 파일 (앵커 준수)

#### ① `WorldScene.kt` — tick/이동 훅 (예산 8줄)
- 앵커 A: `override fun update(dt: Float) {` — **함수 맨 앞 줄**에 삽입:
  ```kotlin
  Ach.tick(this, state)   // [P06] 1초 스로틀 낸부 처리
  ```
- 앵커 B (updatePlayer 낸, 문자열 검색):
  ```kotlin
  if (len > 0.01f) { vx /= len; vy /= len }
  ```
  그 직후 실제 이동 적용 블록에서 이동량을 `Ach.onMove(game.context, 이동px, player.bike)`로 전달 — **이동/충돌 로직 자체는 무수정**, 삽입만.
- `update(`와 `updatePlayer(` 외 함수 무수정 (snap=P3, trySpawnBird=P2, talkTo=P8, updateWeather=P1, updateAmbience/playBgm=P4).

#### ② `Overlays.kt` — 메뉴 탭 1개 (예산 15줄)
- 앵커:
  ```kotlin
  STATUS("상태", "📊"), QUEST("퀘스트", "🗺"), GROW("성장", "🌱"), PIZZA("피자", "🍕"), BOOK("도감", "📚"), SETTINGS("설정", "⚙")
  ```
- **SETTINGS 뒤 맨 끝**에 `, ACHIEVE("업적", "🏅")` 추가 — **W1 유일의 탭 추가권** (규칙 5. W2의 P3이 그 다음에 ALBUM을 끝에 붙인다).
- `when (tab)` 분기에 `ACHIEVE` 본문: 통계 요약 4~6줄 + "자세히 보기" 버튼→`StatsOverlay` + 업적 리스트(스크롤) — 본문 구현은 `MenuOverlay` 낸에 두되, 그리기 헬퍼는 신규 파일로 빼도 됨(이 경우 MenuOverlay 수정은 10줄 이내).

### 절대 건드리면 안 되는 것

- `GameState.kt` (통계는 feat_stats_v1에), `Data.kt`
- `Hud.kt` (알림은 기존 `game.toast` 사용), `Audio.kt` (기존 REWARD sfx 호출만)
- 도장 깨기 15세트·라이퍼 등급 (`Quests.kt` — 무수정, 이름·역할 중복 주의)

## 5. 업적 표 (초안 24개 — ★=히든)

| id | 제목 | 조건 (모두 state/통계로 판정) |
|---|---|---|
| first_shot | 첫 셔터 | 촬영 1회 |
| shot_100 | 백 장의 계절 | 누적 촬영 100 |
| shot_1000 | 천 장의 지도 | 누적 촬영 1000 |
| star3_10 | 초점의 장인 | 3성 사진 10종 |
| star3_50 | ★ 보이지 않는 순간 | 3성 사진 50종 |
| dex_100 | 백 종의 이웃 | 발견 종 100 |
| dex_300 | 날개의 수집가 | 발견 종 300 |
| dex_all | ★ 한반도 598 | 발견 종 598 (라이퍼 '종새꾼' 상징) |
| walk_marathon | 걸어서 마라톤 | 걷기+달리기 누적 42.195km 환산 |
| bike_1000k | 바퀴로 천 리 | 자전거 누적 1,000km 환산 |
| region_5 | 다섯 고을 손님 | 방문 지역 5 |
| region_all | 서른두 곳 지도 | 방문 지역 32 전부 |
| night_owl | 부엉이의 친구 | 밤(19:30~04:30) 촬영 20회 |
| rain_day | 빗속의 망원경 | 비 날씨 촬영 10회 (tick 시 weather·photos 델타) |
| snow_day | ★ 눈 속의 증거 | 눈 날씨 3성 촬영 |
| money_1m | 첫 백만장자 | 현재 골드 ₩1,000,000 달성 |
| money_10m | 동네 부자 | ₩10,000,000 |
| house_own | 두 번째 지붕 | 소유 주택 2채 이상 (`HouseStyles`/소유 목록 파생) |
| level_10 | 어엿한 걸음 | 레벨 10 |
| level_25 | ★ 전설의 탐조가 | 레벨 25 (메인 완결과 일치) |
| pizza_100 | 화덕의 벗 | 피자 보유 총생산 델타 100 (tick이 pizzas 합계 증가를 누적) |
| day_100 | 백 번째 아침 | 게임일 100 |
| snooze_cat | ★ 골목골목 | 고양이 쓰다듬기 횟수 — ⚠️ 소스 없음: 구현 불가 시 23개로 확정하고 이 행 제외 (WorldScene 고양이 훅 추가는 P8 영역 인접이라 이번 범위 밖) |

- 지구상 최소 20개, 숨김 5개 내외로 확정. 조건 소스가 애매한 항목(고양이·벤치)은 **추가 훅 없이 가능한 것만** 남긴다 (규칙: 훅 2개 제한).

## 6. 테스트 / 검증

1. 빌드 + `kt_check.py` 통과.
2. 수동:
   - 서울에서 5분 플레이 → `first_shot` 해금 토스트 + 탭에 해금 표시.
   - 이동 거리 누적이 앱 재시작 후에도 유지.
   - 탭 스크롤·상세 팝업·히든 표시 확인.
   - 디버그 치팅(코드 임시 수정으로 state 조작)으로 money_1m/level_25 해금 확인 → 커밋 전 치트 제거.
3. 성능: `tick`이 60fps 프레임에 raf(read-after-free)없이 1초 스로틀되는지 프로파일(프레임 저하 없음).

## 7. 완료 조건 (DoD)

- [ ] `Achievements.kt`/`StatsOverlay.kt` 신규, `[P06]` KDoc
- [ ] WorldScene 훅 2곳(삽입만, 로직 무수정) — diff로 증명
- [ ] 메뉴 `ACHIEVE` 탭(enum 맨 끝) + 본문 동작
- [ ] 업적 ≥20개 구현, 조건 전부 `state` 파생 또는 `onMove` 누적
- [ ] 저장 `feat_stats_v1`로 보존·재시작 후 유지
- [ ] 빌드/kt_check 통과, 탭 스크린샷 + 해금 토스트 캡처 PR 첨부

## 8. 인터페이스

| API | 소비자 | 의미 |
|---|---|---|
| `Ach.unlocked(ctx)` / `Ach.total` | 통합 QA | 해금 현황 |
| 저장 키 `feat_stats_v1` | **P5** | 백업 자동 수집 |
| 훅: `Ach.tick`, `Ach.onMove` | WorldScene (자기 파트) | 1초 폴더·거리 누적 |
