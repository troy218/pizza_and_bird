# P4. 지역별 BGM + 환경음 확장

> 웨이브 **W1** · 규모 S(코드) + 음원 제작 별도 · 담당 1명
> 의존: 없음 (동시 착수 가능)

---

## 1. 목표

현재 월드 전역 공통인 `bgm_world` 한 곡을 **지역 분위기별 BGM**으로 교체하고,
비·여름밤 환경음을 추가해 세계의 밀도를 높인다. 코드 변경은 최소 — 대부분은 음원 제작과 매핑 데이터다.

## 2. 스토리 맥락

피자와 새의 세계는 "여행"이 주제다. 춘천 호숫가와 부산 해안, 한라산 자락이 같은 음악이면 여행이 아니라 복사다.
음악이 바뀌는 순간 플레이어는 **터널을 넘었다는 감각**을 얻고, 지역별 정체성(§4 매핑)이 생긴다.
P1의 계절과는 의도적으로 결합하지 않는다(작업 분리) — 계절 변주는 v0.5 아이디어로 남긴다.

## 3. 플레이어 경험

- 터널로 지역을 이동하면 지역 분위기에 맞는 BGM으로 **기존 페이드 전환과 함께** 자연스럽게 바뀐다.
- 비가 오면 빗소리 환경음이 깔린다. 여름밤 숲/습지에는 풀벌레 소리가 깔린다.
- 집 안(`bgm_home`)과 타이틀(`bgm_title`)은 그대로 — 익숙한 귀환감 유지.
- 설정의 음악/효과음 토글은 기존 그대로 동작.

## 4. 소유 파일

### 새 파일 (전액 소유)

**`app/src/main/java/com/pizzaandbird/game/BgmPick.kt`** — `[P04]` KDoc.

```kotlin
// [P04] 지역 → BGM 매핑. Data.kt를 수정하지 않고 여기서 switch로 해결한다 (규칙: Data.kt는 P7 전용).
object BgmPick {
    /** 지역 id/서식지로 재생할 raw 리소스를 고른다. */
    fun forRegion(regionId: String, habitats: Set<String>): Int
}
```

매핑 표 (지역 32곳 전부 커버 — `Regions.ALL`의 id/서식지로 판정, 누락 시 `R.raw.bgm_world` 폴더):

| 그룹 | 음원 (신규 파일) | 해당 지역 예 | 무드 가이드 |
|---|---|---|---|
| 도시·마을 | `bgm_town.m4a` | 서울·대전·전주·대구·광주·울산 등 도시형 | 어쿠스틱 기타+피아노, 산책 템포 |
| 물가·호수·습지 | `bgm_wetland.m4a` | 춘천·공릉천·안산·우포늪·주남저수지 | 잔잔한 패드+물방울 퍼커션 |
| 갯벌·바다 | `bgm_coast.m4a` | 인천·강릉·부산·강화도·낙동강 하구·고창 | 파도 감성의 스트링+목관 |
| 숲 | `bgm_forest.m4a` | 광릉숲·시화호? (→wetland 우선) 등 숲 중심 | 새소리 어우러지는 미니멀 |
| 산 | `bgm_mountain.m4a` | 속초·한라산·왕피천·파주 | 코랭글·저음현, 광활 |
| 제주·기후 특수 | `bgm_jeju.m4a` | 제주·하도리 | 오보에/우쿨렐레 느낌, 섬 바람 |

- 판정 우선순위: 제주 id 정확 매치 > 산 포함 > 바다/갯벌 포함 > 습지/물 포함 > 숲 포함 > 도시 폴더(=town), 그 외 `bgm_world` 폴더.
- 같은 곡 연속 재생 시 재시작하지 않는다 (이동해도 곡이 같으면 유지 — `Audio.playBgm`이 같은 res면 무시하는지 확인, 아니면 BgmPick 측에서 캐시).

**신규 음원 (`app/src/main/res/raw/` — 파일 추가만, 규칙 7):**

| 파일 | 용도 | 분량/형식 |
|---|---|---|
| `bgm_town.m4a` / `bgm_wetland.m4a` / `bgm_coast.m4a` / `bgm_forest.m4a` / `bgm_mountain.m4a` / `bgm_jeju.m4a` | 지역 BGM 6 | 각 90~150초 루프, 128~160kbps |
| `amb_rain.mp3` | 비 환경음 | 60초 루프 |
| `amb_insects.mp3` | 여름밤 풀벌레 (P1 무관 — 밤+숲/습지 조건으로) | 60초 루프 |

- 총용량 목표 **+25MB 이내** (APK 크기 보고 PR에 명시).
- 음원 제작 가이드: 기존 3곡은 Suno로 만들었다(README). 동일 파이프라인 권장 — 각 곡 프롬프트 초안:
  - town: "acoustic guitar and piano, gentle walking tempo, Korean indie game, cozy, loopable, no vocals"
  - wetland: "ambient pads, soft marimba like water drops, calm marsh morning, loopable, no vocals"
  - coast: "strings and woodwind, sea breeze, distant gulls mood (instrumental only), loopable"
  - forest: "minimal piano and birdsong-friendly silence gaps, forest walk, loopable"
  - mountain: "cor anglais, low strings, vast highland wind, loopable"
  - jeju: "oboe lead, ukulele rhythm, island breeze, warm, loopable"
- 환경음 2종은 효과음 생성/무료 이용 소스로 제작 (기존 amb_*와 레벨 맞춤).

### 패치 파일 (앵커 준수)

#### ① `WorldScene.kt` — BGM 선택 (예산 3줄)
- 앵커 (문자열 검색):
  ```kotlin
  game.audio.playBgm(R.raw.bgm_world)   // 🎵 새가 날아가는 길
  ```
- 교체:
  ```kotlin
  game.audio.playBgm(BgmPick.forRegion(region.id, region.habitats))   // [P04] 지역 테마
  ```
- 이 라인은 지역 맵이 로드될 때마다 실행되므로 지역 이동 시 자동 전환된다.

#### ② `WorldScene.kt` — 환경음 선택 (예산 8줄)
- 앵커: `private fun updateAmbience()` (현재 137행 근처 — 날은 amb_birds / 밤은 amb_wind 또는 amb_hum 등을 고르는 로직).
- 비:`state.weather() == Weather.RAIN`이면 `R.raw.amb_rain` 우선. 밤+서식지(forest/wetland)+여름이면 amb_insects. 그 외 **기존 로직 그대로**.
- 여름 판정: P1 머지 여부와 무관하게 `((state.day - 1) / 5) % 4 == 1` 인라인 계산 허용 (P1과 중복 로직이지만 한 줄 — P1 머지 후 `Seasons.of(day) == Season.SUMMER`로 치환하는 follow-up 코멘트를 남긴다. 두 파트가 같은 함수를 건드리지 않도록 하기 위한 임시 조치).

#### ③ `Audio.kt` — 신규 res 등록 (예산 10줄)
- `playBgm(res: Int, ...)`는 이미 임의 res를 받으므로 구조 변경 0. `Sfx` enum에는 추가 없음(환경음은 ambCh가 res를 직접 받는 구조면 그대로 사용, 아니면 최소 확장). 파일 상단 주석의 곡 배치 표에 8파일 행을 **끝에 추가**.

### 절대 건드리면 안 되는 것

- 기존 `bgm_title`/`bgm_home` 재생 라인 (Scenes.kt, HomeScene.kt) — 귀한 귀환감 유지 + 다른 파트 보호
- 기존 res/raw 파일 (수정·삭제·교체 금지 — 추가만)
- `Sfx` enum 기존 항목 순서
- Weather.kt / Fx.kt (P1 영역)

## 5. 테스트 / 검증

1. 빌드 + `kt_check.py` 통과.
2. 리소스 검증: `./gradlew assembleDebug`가 aapt 에러 없이 통과 = res 이름·형식 정상.
3. 수동 시나리오:
   - 서울(도시)→부산(해안) 터널 이동 시 BGM 크로스페이드 전환.
   - 비가 올 때 빗소리, 그치면 원래 환경음 복귀.
   - 여름밤 숲 지역에서 풀벌레, 낮엔 안 나옴.
   - BGM이 같은 그룹 지역 간 이동 시 곡이 끊기지 않는지 (재시작 없음).
   - 설정에서 음악 OFF → 신규 곡도 함께 OFF.
4. APK 크기 diff를 PR에 표기 (목표 +25MB 이내).

## 6. 완료 조건 (DoD)

- [ ] `BgmPick.kt` 신규, 매핑 표 32개 지역 전부 커버 + 폴더, `[P04]` KDoc
- [ ] res/raw 신규 8파일(파일명 표와 정확히 일치), 기존 파일 무수정
- [ ] WorldScene playBgm 1줄 교체 + updateAmbience 소폭 (그 외 무수정)
- [ ] Audio.kt 곡 배치 표에 신규 8파일 기록, 설정 토글 정상
- [ ] 기존 타이틀/집 BGM 그대로
- [ ] 빌드/kt_check 통과, APK 크기 diff와 각 곡 길이 표 PR에 첨부
