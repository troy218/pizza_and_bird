# 피자와 새 — 「사계」 개발 계획 (v0.4.x · 10개 파트)

> 이 문서는 **10명이 동시에 작업할 수 있도록** 설계된 병렬 개발 계획의 색인입니다.
> 각 작업자에게는 `P01.md` ~ `P10.md` 중 **하나의 파일만** 전달하세요.
> 각 스펙에는 목표, 스토리 맥락, 구현 상세, **소유 파일(신규/패치 + 코드 앵커)**, 완료 조건이 들어 있습니다.
>
> ⚠️ 작업 전 반드시 이 문서의 **§3. 충돌 방지 규칙 10계명**과 **§4. 웨이브/머지 순서**를 읽을 것.

> 📌 버전 참고: main에는 이미 별도 v0.4.x 작업(조작 개편·몰입캠·전반 폴리시)이 머지되어 있다. 이 계획 시즌의 실제 버전 라벨은 **통합 담당이 다음 시리즈(v0.4.2 이후)로 부여**한다.
> 이 계획 시즌의 실제 버전 라벨은 **통합 담당이 다음 시리즈(v0.4.2 이후)로 부여**한다.
---

## 1. v0.4.0 방향 — 코드네임 「사계(四季)」

### 스토리/콘텐츠 지향점

메인 스토리 「함께 사는 지도」 8장은 할머니의 낡은 수첩을 완성하는 이야기로 Lv.25에서 끝난다.
v0.4.0은 **수첩의 뒷장** — 완결 이후에도 세계가 계속 살아 움직이도록 만드는 시즌이다.

서사 축 3개:

| 축 | 내용 | 담당 파트 |
|---|---|---|
| 🍃 **자연의 순환** | 게임 내 날짜(`GameState.day`)가 계절을 만들고, 계절이 새·날씨·풍경·사람들의 말을 바꾼다 | P1, P2, P8 |
| 📖 **기록의 가치** | 찍은 사진이 앨범에 쌓이고, 평가받고, 팔린다. 플레이어가 자기 수첩을 쓴다 | P3, P6 |
| 🏘 **사람들의 이야기** | 12개 도시 주민들의 에피소드와 계절 대사. 탐조 예절을 자연스럽게 전한다 | P8 |

완성 후의 플레이어 경험:
> 봄에 꽃잎이 흩날리는 춘천에서 닭갈비 피자를 굽고, 여름 밤 반딧불 속에서 지역별 BGM을 들으며
> 올빼미를 찍어 앨범을 채우고, 겨울 철원 평야에서 두루미 3성 사진을 팔아 대박을 내고,
> 백업 코드로 주민등록 같은 나만의 세이브를 금고에 보관한 뒤, 플레이스토어에 올라간 게임으로 친구가 옆에서 같은 세계를 걷는다.

### 파트 → 로드맵 매핑

| README 로드맵 항목 | 파트 |
|---|---|
| 계절 순환 | **P1** |
| 계절별 출현/지역별 희귀도 세분화 | **P2** |
| 사진 앨범 & 사진 평점 (돈 벌기) | **P3** |
| 사운드/음악 (지역별 테마) | **P4** |
| 클라우드 없는 백업(내보내기 코드) | **P5** |
| 업적 시스템, 통계 | **P6** |
| 피자 확장 (도우 종류, 지역 특산 토핑) | **P7** |
| 스토리 강화 (사이드 스토리, 계절 대사) | **P8** |
| 태블릿/폴더블 대응, 접근성 | **P9** |
| 플레이스토어 출시 | **P10** |

---

## 2. 파트 목록 (한눈에)

| # | 파트 | 규모 | 웨이브 | 새 파일 (전액 소유) | 패치하는 공유 파일 |
|---|---|---|---|---|---|
| **P1** | 계절 시스템 코어 + 계절 연출 | M | **W1** | `Seasons.kt` | Fx.kt, Weather.kt, WorldScene.kt(`updateWeather`), Overlays.kt(`MapOverlay`) |
| **P2** | 출현/희귀도 고도화 (계절×지역×시간대) | M | **W2** (P1 필요) | `SpawnTables.kt` | WorldScene.kt(`trySpawnBird`) |
| **P3** | 사진 앨범 & 사진 판매 | L | **W2** | `Album.kt`, `AlbumOverlay.kt` | WorldScene.kt(`snap`), Overlays.kt(`MenuOverlay` 탭) |
| **P4** | 지역별 BGM + 환경음 확장 | S(코드)/에셋 별도 | **W1** | `BgmPick.kt`, res/raw 신규 8개 | WorldScene.kt(`playBgm` 1줄, `updateAmbience`) |
| **P5** | 세이브 백업/복원 코드 | M | **W3** | `Backup.kt`, `BackupOverlay.kt` | GameState.kt(끝에 함수 추가), Overlays.kt(설정 탭) |
| **P6** | 업적 & 통계 | M | **W1** | `Achievements.kt`, `StatsOverlay.kt` | WorldScene.kt(`update`,`updatePlayer`), Overlays.kt(`Tab` enum) |
| **P7** | 피자 확장 (도우·지역 특산) | L | **W2** | `Ingredients.kt`, `DoughOverlay.kt` | Data.kt(**append-only**), Overlays.kt(`BakeOverlay`), HomeScene.kt |
| **P8** | 사이드 스토리 & 대사 시스템 | L(글 중심) | **W2** (P1 필요) | `SideStories.kt`, `Dialogues.kt`, `docs/STORY.md` | WorldScene.kt(`talkTo`) |
| **P9** | 태블릿/폴더블 & 글자 크기 대응 | M | **W1** | (없음 — 패치 전용) | Game.kt, Input.kt, Hud.kt, UiKit.kt, Type.kt, AndroidManifest.xml, Overlays.kt(설정 탭) |
| **P10** | 출시 패키지 (스토어·릴리스 자동화) | M | **W1** 착수 / **W3** 마감 | `.github/workflows/release-aab.yml`, `store/*`, `docs/RELEASE.md` | app/build.gradle.kts |

규모: S = 반나절~1일 / M = 1~2일 / L = 2~4일 (에이전트 1세션 기준 대략치)

---

## 3. ⚠️ 충돌 방지 규칙 10계명 (전 파트 공통)

모든 작업자가 이 규칙만 지키면 **동일 라인 편집은 구조적으로 발생하지 않는다.**

1. **신규 기능은 반드시 스펙에 명시된 신규 파일에 구현한다.** 파일명을 바꾸지 않는다 (다른 파트와의 계약 이름).
2. **공유 파일은 스펙의 앵커 위치에서만, 명시된 줄 수 예산 안에서만 고친다.** 앵커는 문자열 검색으로 찾는다 (라인 번호는 머지 상황에 따라 달라진다). 자기 앵커가 아닌 코드는 고치지 않는다 — 버그를 발견해도 이슈 코멘트로 남긴다.
3. **세이브 확장은 `GameState.kt`를 건드리지 않는다.** 각 기능은 기본 SharedPreferences에 `feat_xxx_v1` 키(파트마다 스펙에 지정)로 JSON 문자열을 따로 저장한다. 이 접두사(`feat_`)는 **P5 백업**이 전체를 자동 훑는 데 필요하므로 반드시 지킨다. (예외: P5만 GameState.kt에 함수를 '끝에 추가'할 수 있다.)
4. **`Data.kt`는 P7 전용, 끝에 추가(append-only)만 허용한다.** 피자 id = 세이브 인덱스라 중간 삽입은 세이브를 깬다. 다른 파트는 `Data.kt`를 절대 수정하지 않는다 — 읽기 전용 참조만.
5. **메뉴 탭(`MenuOverlay.Tab` enum) 추가는 웨이브당 1개 파트만.** W1 = P6(업적 탭), W2 = P3(앨범 탭). enum 목록 **맨 끝**에만 추가한다.
6. **오버레이 UI는 신규 파일로 만들고, `Overlays.kt`에는 스펙이 지정한 라우팅/버튼 줄만 넣는다.** 기존 오버레이 클래스(특히 `PhotoResultOverlay`, `DialogOverlay`)의 생성자 시그니처는 절대 변경하지 않는다.
7. **`res/raw/`는 새 파일 추가만.** 기존 음원 파일명/내용 수정 금지. 새 음원 파일명은 스펙의 표를 그대로 따른다.
8. **공용 문서는 지정 섹션만 수정한다.** `README.md` 로드맵 체크박스와 버전 뱃지는 **통합 담당자가 머지 시점에** 갱신한다. 파트 작업자는 자기 파트의 문서 섹션(DESIGN.md 지정 위치)만 고친다.
9. **제출 전 게이트:** `./gradlew assembleDebug` 성공 + `python3 tools/kt_check.py` 통과 + (맵/스폰 로직을 건드린 경우) `tools/MapTest.kt` 통과. 실패한 PR은 머지하지 않는다.
10. **신규 공개 API에는 파일 상단 KDoc에 `[Pxx]` 태그를 단다.** 다른 파트가 사용하는 API는 §5 인터페이스 표와 정확히 일치시킨다 (이름/타입 변경 시 표부터 고칠 것).

---

## 4. 웨이브 & 머지 순서

```
W1 (동시 착수 가능 — 파일 완전 분리)
   P1 계절 코어 ──┐
   P4 사운드     ├─→ 전부 main 머지 ─────────────────┐
   P6 업적/통계  ┤                                   │
   P9 화면 대응  ┤                                   │
   P10 스캐폴드 ─┘                                   │
                                                     ▼
W2 (W1 머지 완료 후 착수 — P1의 Seasons.kt를 import해서 사용)
   P2 스폰 고도화 ─┐
   P7 피자 확장   ├─→ 권장 머지 순서: P2 → P7 → P3 → P8
   P3 앨범       ┤    (서로 다른 함수/앵커라 순서 무관하게 자동 머지되지만,
   P8 스토리     ─┘     PR 리뷰 편의를 위한 권장 순서)
                                                     │
                                                     ▼
W3 (모든 기능이 main에 들어간 뒤)
   P5 백업 코드 (모든 feat_* 저장소가 확정된 뒤여야 백업이 완전해짐)
   → 통합 QA (크로스 기능 시나리오)
   → P10 마감 (스크린샷 촬영·AAB·버전 태그 v0.4.0)
```

### 왜 이렇게 나눴는가 (충돌 분석)

공유 파일 중 위험한 것은 **WorldScene.kt**(1559줄), **Overlays.kt**(2706줄), **Data.kt**, **GameState.kt** 네 개다.

| 공유 파일 | 건드리는 파트 | 충돌 판정 |
|---|---|---|
| WorldScene.kt | P1(`updateWeather`) · P2(`trySpawnBird`) · P3(`snap`) · P4(`playBgm` 1줄, `updateAmbience`) · P6(`update`,`updatePlayer`) · P8(`talkTo`) | ✅ 전부 **서로 다른 함수**, 같은 함수라도 다른 앵커 라인. 동시 편집 OK |
| Overlays.kt | P1(`MapOverlay`) · P3(`Tab` enum+라우팅) · P5(설정 탭 버튼) · P6(`Tab` enum=W1 단독) · P7(`BakeOverlay`) · P9(설정 탭 글자 크기) | ⚠️동일 탭 enum은 규칙 5로 시간 분리. P5·P9 모두 설정 탭이나 W1↔W3 직렬이라 안전. 나머지는 서로 다른 클래스 |
| Data.kt | P7 전용 | ✅ 단독 |
| GameState.kt | P5 전용(W3) | ✅ 단독 + 규칙 3 |
| HomeScene.kt | P7 전용 | ✅ 단독 |
| Game.kt / Input.kt / Hud.kt / UiKit.kt / Type.kt / Manifest | P9 전용 | ✅ 단독 |
| Fx.kt / Weather.kt | P1 전용 | ✅ 단독 |
| Audio.kt | P4 전용 | ✅ 단독 |
| res/raw | P4 추가만 | ✅ 추가만 |
| .github/workflows | P10 (새 파일만) | ✅ 기존 워크플로 미수정 |
| BirdChecklist.kt | 아무도 건드리지 않음 (생성 코드, 변경 시 갱신 절차 별도) | ✅ |

---

## 5. 파트 간 공개 API 인터페이스 (계약)

> 이 표가 파트 간의 유일한 결합점이다. 시그니처를 바꾸고 싶으면 표부터 갱신하고 해당 파트에 통지한다.

| 제공 파트 | API (파일) | 사용 파트 | 용도 |
|---|---|---|---|
| P1 | `Seasons.of(day: Int): Season` (Seasons.kt) | P2, P8, (P3 표시용) | 현재 계절 |
| P1 | `Seasons.label(day: Int): String` (예: "봄 3일째") | P8, P3(앨범 캡션) | UI 표시 |
| P1 | `Seasons.weatherWeights(s: Season): List<Double>` (Weather 순서와 동일) | WorldScene.updateWeather 내부 | 계절별 날씨 빈도 |
| P2 | `SpawnTables.highlights(regionId: String, s: Season): List<BirdDef>` | P8(지역 대사), 추후 도감 힌트 | 이 계절의 대표 새 |
| P3 | `Album.all(): List<PhotoRec>` / `Album.best(n: Int): List<PhotoRec>` | P10(스크린샷 자료), P8(에필로그 분기) | 앨범 엑세스 |
| P6 | `Ach.unlocked(): Int` / `Ach.total: Int` | — (독립) | 업적 수 |
| P7 | `Ingredients.unlockedToppings(regionId): List<ToppingDef>` | P8(피자 납품 에피소드 보상 연동, 선택) | 특산 재료 |

---

## 6. 파트별 상세 스펙 링크

| 파일 | 파트 |
|---|---|
| [P01_seasons.md](P01_seasons.md) | 계절 시스템 코어 + 계절 연출 |
| [P02_spawn_tables.md](P02_spawn_tables.md) | 출현/희귀도 고도화 |
| [P03_photo_album.md](P03_photo_album.md) | 사진 앨범 & 사진 판매 |
| [P04_audio.md](P04_audio.md) | 지역별 BGM + 환경음 |
| [P05_backup.md](P05_backup.md) | 세이브 백업/복원 코드 |
| [P06_achievements.md](P06_achievements.md) | 업적 & 통계 |
| [P07_pizza_dough.md](P07_pizza_dough.md) | 피자 확장 (도우·지역 특산) |
| [P08_side_stories.md](P08_side_stories.md) | 사이드 스토리 & 대사 시스템 |
| [P09_display.md](P09_display.md) | 태블릿/폴더블 & 글자 크기 |
| [P10_release.md](P10_release.md) | 출시 패키지 |

---

## 7. 통합 완료 조건 (v0.4.0 릴리스 게이트)

1. 10개 파트 PR 전부 main 머지, CI(Android APK Build) 초록불.
2. `tools/MapTest.kt` + `tools/kt_check.py` 통과.
3. 크로스 시나리오 수동 점검 (통합 담당):
   - 겨울 + 눈 + 밤에 지역 BGM이 바뀐 상태에서 희귀새 촬영 → 앨범 기록 → 업적 토스트 → 사진 판매
   - 백업 코드 발급 → 데이터 초기화 → 복원 → 앨범/업적/스토리 진행/피자 팬트리까지 전부 살아 있는지
   - 계절이 바뀌는 밤(자정 경계) 크래시 없는지
   - 폴더블 펼침 화면에서 앨범·업적·굽기 오버레이 터치 박스가 정확한지
4. `README.md` 로드맵 체크 & 버전 뱃지 v0.4.0, `docs/DESIGN.md` §5 항목 이동(통합 담당).
5. `git tag v0.4.0` → AAB 릴리스(P10 절차).

---

## 8. 미결 사항 (시작 전에 결정 권장)

| # | 질문 | 권장 기본값 | 관련 파트 |
|---|---|---|---|
| Q1 | 계절 길이 | 게임 5일 = 실시간 25분 (1년 = 100분) | P1 |
| Q2 | 음악 제작 주체 | 기존처럼 Suno 생성 → 작업자가 가이드대로 생성해 PR에 첨부 | P4 |
| Q3 | 지역 특산 피자 8종 최종 네이밍 | P7 표의 초안 그대로 | P7 |
| Q4 | 스토어 언어 범위 | 한국어 기본 + 영어 리스팅 병기 | P10 |
| Q5 | 사진 판매가 밸런스 상한 | P3 표 준용, 통합 QA에서 조정 | P3 |
| Q6 | 릴리스 서명 키 | 저장소 시크릿 등록 여부 확인 필요 (없으면 debug 키 유지) | P10 |
