# P8. 사이드 스토리 & 대사 시스템 — 「사람들의 계절」

> 웨이브 **W2** · 규모 L (글 비중 높음) · 담당 1명
> 의존: **P1 필요** (`Season`, `Seasons.label` 사용 — 계절 대사)
> 선택 의존: P2 `SpawnTables.highlights`(지역 대사의 실시간 힌트), P7 `toppingsFor` (납품 에피소드) — 없어도 동작하게 설계.

---

## 1. 목표

메인 8장(할머니의 수첩)이 끝난 세계를 사람의 온기로 채우는 두 가지:

1. **조건부 대사 시스템** — 기존 NPC 5명(보리 박사·상점주인·동네 주민·꼬마·할머니)의 대사가 **메인 진행 장 × 계절 × 날씨 × 밤낮**에 따라 바뀐다 (기존 랜덤 대사 풀 확장).
2. **지역 사이드 스토리 12편** — 12개 도시에 각각 3막(도입→심부름→마무리) 미니 에피소드. 기존 NPC만 출연(신규 아트/캐릭터 없음), 목표는 "지정 사진 촬영/납품/장소 대화"의 3가지 틀로 통일해 시스템을 단순하게 유지.

## 2. 스토리 맥락 & 집필 원칙

v0.4.0 컨셉 「사계」의 세 번째 축: **사람들의 이야기**. 메인에서 플레이어는 "새를 아는 법"을 배웠고,
사이드에서는 **그 배움이 이웃에게 어떻게 쓰이는가**를 보여 준다. 톤은 힐링 — 경쟁·패배·긴장 없음.

**작성 원칙** (`docs/STORY.md`에 전문 기록):
- 탐조 예절(둥지 보호·관찰 거리·위치 공유 금지)을 설교가 아니라 사건 속에서 자연스럽게.
- 지역 서사는 실제 지역 정체성 존중(춘천=호수·닭갈비, 전주=한옥 등 README 표 정합).
- 새 NPC·새 아트·새 맵 금지 — 기존 5 NPC의 "인간관계 확장"으로 해결 (예: 철원의 주민은 철새 탐조 예찬론자).
- 메인 8장·기존 NPC 성격(박사: 고지식+온화 / 꼬마: 순수+질문형 / 할머니: 여유+회상 / 주민: 지역 자부심 / 상점주인: 장사꾼+덕담)과 모순 금지.
- 모든 대사는 기존 화법(따옴표 `"…"`, 이모지 최소, 말끝 흐리는 … 사용)과 일치.
- 새 대사 양 산출 목표: NPC 조건부 대사 **60줄 이상**, 에피소드 대화 12편 × 평균 10초치.

## 3. 플레이어 경험

- 같은 할머니도 봄/겨울, 비/눈, 밤에 말을 걸면 다른 말을 한다 (기존 대사 앞에 계절·날씨 문장이 붙거나 갈아끼워짐).
- 지역 첫 방문 시 해당 지역 에피소드가 NPC 머리 위 `💬`로 표시된다 (기존 `!` 마커 패턴 재사용 — 이모트 시스템이 이미 말풍선 서포트).
- 3막 진행 중에는 메뉴의 퀘스트 탭이 아니라 **대화로만 현재 막을 안내**한다 (메인 퀘스트 시스템 무수정).
- 12편 전부 완료하면 숨은 에필로그 대사("수첩 뒷장에 이름을 적어두세요" 류) + 소소한 보상.

## 4. 소유 파일

### 새 파일 (전액 소유)

**`docs/STORY.md`** — 스토리 바이블:
- 세계관 요약 + 메인 8장 리캡(한 장당 3줄), NPC 페르소나 시트, 화법 규칙, 금기 목록, 계절별 감성 키워드(봄=시작·연두 / 겨울=기다림·따뜻한 빛 등), 12편 에피소드 각 5줄 요지(도입/심부름/마무리/보상), 이 게임이 말하지 않는 것(실존 인물·재난·정치·동물 위해 묘사 금지).

**`app/src/main/java/com/pizzaandbird/game/Dialogues.kt`** — `[P08]` KDoc.

```kotlin
// [P08] 조건부 대사 풀 — NPC 말은 여기서만 고른다. 게임 로직 무의존(순수 데이터+선택 함수).
object Dialogues {
    data class Ctx(val regionId: String, val chapter: Int, val season: Season?, // [P01] 미머지 시 null
                   val weather: Weather, val night: Boolean, val day: Int)
    fun villager(c: Ctx): String      // 기존 map.region.villager + 장별 storyHint + 계절/날씨 문장 조합
    fun kid(c: Ctx): String           // 기존 6줄 + 계절 6줄 + 날씨 4줄 풀
    fun elder(c: Ctx): String         // 기존 6줄 + 계절 6줄 + 밤 3줄
}
```

규칙: 기존 `talkTo`에 박혀 있던 대사 리터럴들을 이 파일로 **이전**한다 (삭제 금지 — 이동). 이동한 문구 금자 한 글자도 바꾸지 않는다(기존 세계관 보존) + 신규 문구는 아래에 추가.

**`app/src/main/java/com/pizzaandbird/game/SideStories.kt`** — `[P08]` KDoc.

```kotlin
// [P08] 지역 사이드 스토리 — 진행은 기본 prefs feat_story_v1 키(규칙 3).
object SideStories {
    data class Episode(val id: String, val regionId: String, val npc: NpcKind,
                       val acts: List<Act>, val reward: Pair<Int, Int>)  // (골드, 행운)
    data class Act(val goal: Goal, val lines: List<String>)
    sealed class Goal { data class Photo(val birdId: String?, val tierMin: Int) : Goal()
                        data class Deliver(val pizzaIdMin: Int) : Goal()        // 특산 피자 id 12~19 임의 1개
                        object Talk : Goal() }                                   // 장소 없이 대화만

    val EPISODES: List<Episode>      // 12편 — §5 표
    fun progress(ctx: Context, regionId: String): Int      // 0=미시작, 1..3=막, 4=완료
    fun current(ctx: Context, regionId: String): Episode?  // 이 지역에서 받을 수 있는 에피소드
    /** WorldScene.talkTo 맨 앞에서 호출 — true면 대사를 표시하고 소비(기존 대화 스킵). */
    fun intercept(scene: Scene, npc: Npc): Boolean
}
```

목표 판정은 **대화 시점에만 검사**한다(별도 진행 훅 없음):
- Photo: 해당 지역 도감(`state.birdCounts`)에 조건 충족 새가 있으면 성공 → 다음 막 대사.
- Deliver: `state.pizzas` 인벤토리에 특산 피자(id≥12, 품질 무관)가 있으면 1개 소모하고 진행 (P7 무존재 시에는 Deliver 목표를 사용하지 않음 — 에피소드 표의 "납품형"은 P7 머지 확인 후 활성화 여부를 PR에 명시).
- Talk: 대사만.

### 패치 파일 (앵커 준수)

#### `WorldScene.kt` — talkTo만 (예산 30줄 — 대사 데이터 이동 제외)
- 앵커 A: `private fun talkTo(npc: Npc) {` — **함수 맨 앞**에 1줄:
  ```kotlin
  if (SideStories.intercept(this, npc)) return   // [P08] 사이드 스토리 진행 중이면 우선
  ```
- 앵커 B: `NpcKind.VILLAGER`, `KID`, `ELDER` 분기의 **대사 선택 코드**를 `Dialogues.villager(ctx)` 등으로 교체 (기존 `lines` 리터럴 블록은 Dialogues.kt로 이동).
- talkProfessor·talkShop 분기는 **무수정** (상점 Choice 추가는 하지 않는다 — P3과의 충돌 차단).
- 막 마커(`💬`) 표시: 에피소드가 진행/대기 중인 NPC 머리 아이콘 — 월드 그리기 경로의 NPC 마커 지점(기존 `!` 표시와 동일 함수)에 SideStories 조회 2~3줄 — 동일 함수를 P3이 건드리지 않으므로 충돌 없음.

### 절대 건드리면 안 되는 것

- 메인 퀘스트(`Quests.kt` MainStory 8장) 로직·문구 — 사이드는 **그 뒷장**이지 수정이 아니다
- 컬렉션/라이퍼 표기 (`BirdingCollections`·`BirdingRanks` 문구 재사용 시 그대로 인용)
- `talkProfessor`/`talkShop` 함수, `Overlays.kt` 전체
- 기존 NPC 5명의 이름탭·외형·애니메이션 (CharacterArt 무수정)
- 도감·피자 세이브 스키마

## 5. 12편 에피소드 표 (초안 — 각 3막, 보상 = 소액 골드+행운)

| 지역 | NPC | 제목(감성) | 목표형 | 한 줄 요지 |
|---|---|---|---|---|
| 서울 | 동네 주민 | 창밖의 첫 수업 | Photo(아무 뒤/Talk) | 수첩 주인의 첫 배움 회상 |
| 인천 | 할머니 | 갯뻘이 걸어온 길 | Photo(도요/갈매기 계열) | 갯벌이 사람을 살린 이야기 |
| 춘천 | 꼬마 | 호수에 비친 나 | Photo(물새) | 호수에서 기다리는 법 |
| 강릉 | 상점주인 | 소나무 아래 손님 | Deliver(13 감자) or Photo(숲새) | 장사는 사진에서 시작됐다 |
| 속초 | 할머니 | 눈 내리는 창 | Photo(겨울 계열) | 첫눈과 두루미의 계절 |
| 대전 | 동네 주민 | 사거리의 나침반 | Talk(2회 대화) | 길이 모이는 곳의 약속 |
| 전주 | 할머니 | 한옥의 처마 끝 | Photo(텃새) | 천천히 오래 보는 집 |
| 대구 | 꼬마 | 팔공산 코알라? | Photo(야생 소동물은 아니지만 산새) | 헷갈린 이름의 정정 놀이 |
| 광주 | 동네 주민 | 무등의 바람 | Photo(팔색조 계열 희귀) | 여름 숲의 세 보석 |
| 울산 | 상점주인 | 간절곶 첫 배 | Photo(바닷새) | 가장 먼저 뜨는 태양 이야기 |
| 부산 | 꼬마 | 갈매기 따라 춤을 | Photo(괭이갈매기) | 시장에서 춤 추는 이유 |
| 제주 | 할머니 | 돌담의 겨울 손님 | Photo(제주/겨울) | 섬을 떠나지 않는 새와 사람 |

- 각 에피소드 보상: ₩20,000~60,000 + 행운 +5~10 정도 (진행 비용 0원 설계 — Deliver형은 재료가 제외하면).

## 6. 테스트 / 검증

1. 빌드 + `kt_check.py` 통과.
2. 대사 검증 표: NPC×조건 매트릭스로 실제 화면 캡처 8장 이상 PR 첨부 (계절/날씨/밤 바꿔가며).
3. 에피소드 E2E: 제주 에피소드 하나를 처음부터 끝까지 수행(목표 충족→대사→보상→완료 표시→재방문 시 반복 없음 확인).
4. 회귀: 메인 퀘스트 진행 장에 따라 VILLAGER의 storyHint가 기존과 동일하게 작동(장 0,1/2/3/4/5/else 각 1회), 기존 6줄 대사 풀이 빠짐없이 돌아가는지.
5. `docs/STORY.md`의 페르소나와 코드 대사가 모순되지 않는지 자체 교정 체크리스트.

## 7. 완료 조건 (DoD)

- [ ] `docs/STORY.md` 작성 (바이블 + 12편 요지 + 금기)
- [ ] `Dialogues.kt`/`SideStories.kt` 신규, `[P08]` KDoc, 진행 저장 `feat_story_v1`
- [ ] talkTo 패치 = 인터셉트 1줄 + 3개 분기 대사 소스 교체만, talkProfessor/talkShop 무수정 — diff로 증명
- [ ] 기존 대사 리터럴 전량 Dialogues.kt로 이동보존(내용 수정 0) + 신규 조건부 대사 60줄 이상
- [ ] 12편 에피소드 데이터 완비 + 최소 1편 E2E 시연 캡처
- [ ] P1 미머지 시 season=null 폴더(계절 문장 생략) — 머지 후엔 자동 활성화
- [ ] 빌드/kt_check 통과

## 8. 인터페이스

| API | 소비자 | 의미 |
|---|---|---|
| 저장 키 `feat_story_v1` | **P5** | 백업 수집 |
| `SideStories.EPISODES` | 통합 QA, P10(스토어 문구에 "지역 이야기 12편" 소재) | 에피소드 카탈로그 |
| `Dialogues` | — (자기 파트 낸부) | 대사 풀 |
