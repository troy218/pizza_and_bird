# P3. 사진 앨범 & 사진 판매

> 웨이브 **W2** · 규모 L · 담당 1명
> 의존: 없음(P1 머지 후 착수하면 앨범 캡션에 계절을 넣을 수 있음). 통합 QA 밸런스 항목.

---

## 1. 목표

촬영한 사진이 **앨범에 차곡차곡 쌓이고**, 다시 꺼내 볼 수 있고(같은 폴아라이드 연출로 재현),
사진용품점 주인에게 **판매해 돈을 벌** 수 있게 한다. 경제 두 번째 수입원이자 수집 동기의 완성.

## 2. 스토리 맥락

할머니의 수첩이 날개(새 그림)로 채워졌다면, 플레이어의 수첩은 **사진**으로 채워진다.
"보리 박사의 의뢰"가 일거리라면, 앨범은 **나의 기록** — 메인 스토리가 끝난 뒤에도 계속 쌓이는 개인사다.
사진용품점 주인(기존 NPC)은 "좋은 사진은 동네 게시판에 걸어 손님을 불러온다"는 설정으로 판매 상대가 된다
(신규 NPC·신규 아트를 만들지 않는 범위).

## 3. 플레이어 경험

- 촬영 성공 시 자동으로 앨범에 사진이 저장된다 (설정 없이 기본 동작).
- 메뉴(☰)에 새 탭 **「앨범 🖼」** — 그리드로 썸네일 나열, 탭하면 기존 폴아라이드 결과 카드 연출로 크게 보기.
- 앨범 최대 **200장**. 초과하면 가장 오래된 사진부터 자동 판매(또는 경고 후 삭제) — 결정은 제작 시 표 준수.
- 사진용품점 주인 대화에 **「사진 판매」** 선택지 — 등급×별점 기반 가격 표, 판매하면 앨범에서 제거(즉시 골드).
- 각 사진에는 촬영 지역·날짜(게임일/계절)·거리·침대에 걸 수 있는 "대표 사진" 같은 메타가 함께 보인다.

## 4. 소유 파일

### 새 파일 (전액 소유)

**`app/src/main/java/com/pizzaandbird/game/Album.kt`** — `[P03]` KDoc.

```kotlin
// [P03] 사진 앨범 — 저장은 기본 SharedPreferences의 feat_album_v1 키(규칙 3). GameState 무수정.
object Album {
    data class PhotoRec(
        val birdId: String, val stars: Int, val regionId: String,
        val day: Int, val seasonLabel: String,  // P1 머지 전이라면 로컬에서 동일 규칙 계산 — 아래 'P1 연동' 참고
        val distTiles: Float, val cameraName: String, val night: Boolean,
        val sold: Boolean = false, val pinned: Boolean = false,
    )
    const val CAP = 200

    fun record(ctx: Context, r: PhotoRec)          // 촬영 직후 호출 (WorldScene이 호출)
    fun all(ctx: Context): List<PhotoRec>          // 최신 순
    fun best(ctx: Context, n: Int): List<PhotoRec> // [계약] P10 스크린샷·P8 에필로그용
    fun price(r: PhotoRec): Int                    // 판매가 계산식 §5
    fun sell(ctx: Context, r: PhotoRec): Int       // 판매 처리, 지급액 반환
    fun pin(ctx: Context, r: PhotoRec, on: Boolean)
}
```

저장 규칙:
- 기본 prefs 키 **`feat_album_v1`** (규칙 3 — P5 백업이 자동 수집).
- JSON 배열, 200장 cap. 초과 시 **pinned이 아닌 가장 오래된 사진을 자동 판매 가격의 50%로 자동 판매**하고 토스트 안내(데이터 유실 방지).

**`app/src/main/java/com/pizzaandbird/game/AlbumOverlay.kt`** — `[P03]` KDoc.

- `Overlay` 서브클스(Overlays.kt의 `abstract class Overlay` 패턴 준수 — 이 파일은 신규 파일이라 추가 충돌 없음).
- 1페이지 3×2 그리드: 썸네일(폴아라이드 미니) + 새 이름 + 별점 + 날짜. 페이지 넘김.
- 썸네일 렌더: 새 비트맵(`game.assets.bird(birdId)`) + 폴아라이드 프레임을 축소해 직접 그린다(기존 `PhotoResultOverlay` 낸부 로직을 복사해 축소 변형 — `PhotoResultOverlay` 자체는 손대지 않음).
- 상세 보기: 썸네일 탭 → **`PhotoResultOverlay` 인스턴스를 생성해 그대로 `scene.openOverlay(...)`** (시그니처 변경 금지, 규칙 6). `expGain=0, levelsGained=0, questLine=null` 등 중립값 사용. 사진 풍경(서식지·낮밤)이 그대로 재생된다.
- 핀(대표 사진) 토글, 판매 버튼(가격 표시).

### 패치 파일 (앵커 준수)

#### ① `WorldScene.kt` — 촬영 기록 훅 (예산 6줄)
- 앵커 (snap() 낸, 문자열 검색):
  ```kotlin
  state.photos += 1
  ```
- **그 바로 다음 줄**에 3~4줄 삽입:
  ```kotlin
  Album.record(game.context, Album.PhotoRec(   // [P03] 앨범에 자동 수집
      b.def.id, stars, region.id, state.day, Seasons.label(state.day),  // P1 미머지 시 "봄" 고정 placeholder OK
      distPx / 16f, CameraDefs.name(state.cameraLevel), state.isNight()))
  ```
- ⚠️ 앵커 주변 코드(도감 갱신, 경험치, 퀘스트완료, `PhotoResultOverlay` 생성)는 **한 글자도 수정 금지**. `snap()` 외 함수 무수정 (특히 `update`,`updatePlayer`=P6 / `trySpawnBird`=P2 / `talkTo`=P8).

#### ② `Overlays.kt` — 메뉴 탭 1개 + 라우팅 (예산 25줄)
- 앵커 (문자열 검색):
  ```kotlin
  STATUS("상태", "📊"), QUEST("퀘스트", "🗺"), GROW("성장", "🌱"), PIZZA("피자", "🍕"), BOOK("도감", "📚"), SETTINGS("설정", "⚙")
  ```
- enum 맨 끝(**SETTINGS 뒤**)에 `, ALBUM("앨범", "🖼")` 추가 — W2 유일의 탭 추가권(규칙 5).
- `MenuOverlay`의 `when (tab)` 본문 분기 탐색해서 `Tab.ALBUM -> { ... }` 추가: 본문 영역에 "앨범 열기" 버튼 하나 그리고 `btnRects`에 액션 등록 — 액션은 `scene.openOverlay(AlbumOverlay(scene))`. "장식 코너 보기" Choice의 패턴과 동일.
- 다른 탭 본문/버튼 무수정. `Tab` enum 외 다른 enum 무수정.

#### ③ `Overlays.kt` — 사진용품점 판매 진입 (예산 10줄)
- 현재 상점 대화 생성부는 `WorldScene.talkShop()`(953행 근처)다. **판매 Choice는 talkShop에 넣지 말고** — talkShop은 이 파트 금지(P8과 동시 편집 위험 차단) — 대신 MenuOverlay 라우팅과 같은 방식으로 **AlbumOverlay 낸부에서 "판매 모드"** 를 제공하고, 상점 주인 역할성은 다음과 같이 확보:
  - 합격안 A (권장): 판매는 앨범 탭 낸 "일괄 판매/선택 판매" 버튼으로 처리하고, 판매 시 토스트 문구를 상점주인 대사체로 ("사진용품점 주인: \"이 사진은 게시판에 걸어둘 값어치가 있네! +₩12,000\""). **WorldScene/대화 시스템 무수정** — 가장 충돌 안전.
  - (비권장) talkShop에 Choice 추가 — P8과 talkTo/talkShop 인접 충돌 위험. 하지 않는다.
- 따라서 **③은 채택시 코드 변경 0줄**. PR 명세에 "판매 UX = 앨범 탭 낸부" 명시.

### 절대 건드리면 안 되는 것

- `GameState.kt` (도감 카운트는 이미 저장됨 — 앨범은 별도 키), `Data.kt`
- `PhotoResultOverlay`·`DialogOverlay` 시그니처 (규칙 6 — 생성해서 쓰기만)
- `WorldScene.kt`의 snap() 외, `HomeScene.kt`, `Hud.kt`
- 도감(`birdCounts`/`bestStars`) 로직 — 앨범은 도감과 독립(판매해도 도감 기록은 남음)

## 5. 판매가 밸런스 (통합 QA 조정 대상, OVERVIEW Q5)

| 등급 | 기본가(★1) | ★2 | ★3 |
|---|---:|---:|---:|
| 흔함(★) | ₩400 | ₩800 | ₩1,400 |
| 보통(★★) | ₩1,200 | ₩2,400 | ₩4,200 |
| 희귀(★★★) | ₩4,000 | ₩8,000 | ₩14,000 |
| 전설(★★★★) | ₩12,000 | ₩24,000 | ₩42,000 |

- 첫 발견 사진은 판매 불가(pinned 자동) — 도감 개척의 기념은 팔지 않는다.
- 같은 종 5번째 이후 사진은 가격 ×0.6 (흔한 사진 덤핑 방지). 계산 근거는 `bestStars`/`birdCounts`가 아닌 **앨범 낸 잔여 매수**로 (도감과 무관하게).
- 자동 판매(cap 초과)는 표의 50%.

## 6. 테스트 / 검증

1. 빌드 + `kt_check.py` 통과.
2. 수동 시나리오:
   - 촬영 → 앨범 탭에서 즉시 1장 확인 → 상세 보기 폴아라이드가 촬영 시 카드와 동일 구도(서식지·낮밤 일치).
   - 200장 채우기(디버그: 연속 촬영) → 201장째 자동 판매 토스트 + 골드 증가 확인.
   - 판매 → 앨범에서 제거 + 도감 기록은 유지.
   - 앱 재시작 후 앨범 보존.
   - (P5 머지 후 W3에서) 백업/복원 시 앨범 유지 확인 — `feat_album_v1` 키로 저장했는지만 이번 파트에서 보증.
3. 경제 리그레션: 판매만으로 침대값(최상 침대라 ₩1.5M)을 버는 데 걸리는 시간이 의뢰 대비 지나치게 짧지 않은지 체감 기록.

## 7. 완료 조건 (DoD)

- [ ] `Album.kt`(저장 feat_album_v1, CAP=200) / `AlbumOverlay.kt` 신규, `[P03]` KDoc
- [ ] snap() 훅 = `state.photos += 1` 다음 줄 삽입만 (그 외 0 수정) — diff로 증명
- [ ] 메뉴 ALBUM 탭(enum 맨 끝) + 라우팅 버튼 동작
- [ ] 상세 보기 = `PhotoResultOverlay` 재사용(시그니처 무수정)
- [ ] 판매 가격표 적용 + 첫 발견 판매 불가 + 자동 판매 50% + 토스트 문구
- [ ] 앱 재시작·저장 후 보존 확인
- [ ] 빌드/kt_check 통과, PR에 앨범 그리드·상세·판매 토스트 스크린샷 첨부

## 8. 인터페이스 (내가 남기는 것)

| API | 소비자 | 의미 |
|---|---|---|
| `Album.all(ctx): List<PhotoRec>` | P8(에필로그 조건), 통합 QA | 전체 사진 |
| `Album.best(ctx, n): List<PhotoRec>` | P10(스토어 스크린샷 소재) | 별점 상위 사진 |
| 저장 키 `feat_album_v1` | **P5** | 백업 자동 수집 대상 |
