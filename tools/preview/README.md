# tools/preview — 그래픽 미리보기 도구 모음 🔬

안드로이드 기기/APK 없이 게임 그래픽을 눈으로 확인하기 위한 개발 도구다.
두 가지 파이프라인이 있으며 목적이 다르다.

| 파이프라인 | 언어 | 무엇을 보여주나 |
|---|---|---|
| **JVM 스크린샷 파이프라인** (`src/`, `ci_render.sh`) | Kotlin/JVM | 실제 게임 렌더링 코드를 그대로 실행 → 모든 화면의 스크린샷 |
| **파이썬 맵 미리보기** (`*.py`) | Python | 맵 레이아웃·타일 아트 알고리즘을 파이썬으로 이식 → 빠른 길/맵 설계 확인 |

---

## 1) JVM 스크린샷 파이프라인

게임의 실제 렌더링 코드(Assets / Maps / Roads / Scenes / WorldScene / HomeScene / Hud /
Overlays)를 **한 글자도 수정하지 않고** 안드로이드 없이 JVM에서 실행해
모든 화면의 스크린샷을 PNG로 뽑는다. android.graphics 스텁(Java2D 구현)이
android API 시그니처를 1:1로 제공한다. (MainActivity.kt / GameView.kt만 제외 —
SurfaceView/Activity 의존이라 프리뷰 불필요)

### 구조

```
tools/preview/
├── ci_render.sh        CI 전용 실행 스크립트 (컴파일 → 렌더링 → preview/ 커밋)
├── src/
│   ├── android_stubs_graphics.kt   android.graphics 스텁 (Java2D 구현)
│   ├── android_stubs_content.kt    android.content 스텁
│   ├── android_stubs_os.kt         android.os 스텁
│   ├── android_stubs_view.kt       android.view 이벤트 스텁
│   ├── json_stubs.kt               org.json 최소 구현
│   ├── mainactivity_stub.kt        MainActivity 스텁 (Game.kt 컴파일용)
│   └── preview_main.kt             프리뷰 렌더러 (모든 화면 스크린샷)
├── fonts/              NotoSansKR — 글꼴 대체(폴백)용 (자동 다운로드, git 미포함)
└── out/                렌더링 결과 (git 미포함)
```

### 글꼴

스크린샷은 **게임에 내장된 글꼴 그대로** 나온다. `Typeface.createFromAsset` 스텁이
`app/src/main/assets/font/*.ttf` 를 실제로 읽고(주아·고운돋움), 자간(letterSpacing)도
기기와 같게 적용한다. 글꼴에 없는 글자(이모지 등)는 안드로이드(Minikin)처럼
`tools/preview/fonts/NotoSansKR-*.ttf` 로 자동 대체된다 — 이 폴백 글꼴만 자동 다운로드한다.

### CI 자동 실행

루트 `gradlew`의 훅이 **arena 세션 브랜치 push 시에만** APK 빌드 뒤에
`tools/preview/ci_render.sh`를 실행한다. 결과:

- `preview/*.png` — 모든 화면 스크린샷 (같은 브랜치에 자동 커밋, `[skip ci]`)
- `preview/build.log` — 프리뷰 컴파일/렌더링 로그 (실패 원인 확인용)

다른 브랜치/로컬 빌드에서는 동작하지 않는다.

### 로컬 실행 (JDK 17 + kotlinc)

```bash
SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
  ! -name 'MainActivity.kt' ! -name 'GameView.kt')
kotlinc tools/preview/src/*.kt $SRCS -d tools/preview/out/classes-preview -jvm-target 17
java -cp tools/preview/out/classes-preview:$KOTLIN_HOME/lib/kotlin-stdlib.jar \
  com.pizzaandbird.preview.PreviewMain tools/preview/out
```

### 입력 회귀 테스트

동일한 프리뷰 스텁으로 실제 `Input`/`Game`/`Scene`을 구동해 **타이틀 → 캐릭터 선택 → 서울 시작 → 월드**, 레터박스 좌표·집/월드 카메라·모달 터치·일시정지 입력 해제를 검사합니다 (JDK 17 + kotlinc 필요).

```bash
SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
  ! -name 'MainActivity.kt' ! -name 'GameView.kt')
kotlinc tools/preview/src/*.kt tools/preview/input_smoke.kt $SRCS \
  -d tools/preview/out/classes-smoke -jvm-target 17
java -cp "tools/preview/out/classes-smoke:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.InputSmoke
```

### UI 불투명도 회귀 테스트

그림자용 `Paint.alpha`가 창·카드·버튼·게이지·힌트 배경에 남지 않는지 검사합니다.
검정/흰 배경에서 본문 픽셀이 같은지, 비활성 버튼의 의도적인 반투명도는 유지되는지,
픽셀 숫자의 색상/페이드가 정상인지 확인합니다. 프리뷰 스텁도 Android처럼
셰이더에 `Paint.alpha`를 곱하고 `SRC_IN` 비트맵 틴트를 적용합니다.

```bash
SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
  ! -name 'MainActivity.kt' ! -name 'GameView.kt')
kotlinc tools/preview/src/*.kt tools/preview/ui_opacity_smoke.kt $SRCS \
  -d tools/preview/out/classes-ui -jvm-target 17
java -cp "tools/preview/out/classes-ui:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.UiOpacitySmoke
```

### 업적 거리/저장 및 UI 스모크 테스트

실제 `Ach.onMove`·`Ach.tick`으로 거리 업적 해금, `GameState` 무변경,
`feat_stats_v1` 저장/재로딩을 확인하고, 메뉴 탭·통계 페이지·상세 팝업·목록 페이지 이동을 터치로 검증합니다.

```bash
PREVIEW_STUBS=$(find tools/preview/src -maxdepth 1 -name '*.kt' ! -name 'android_stubs_missing_*.kt')
SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
  ! -name 'MainActivity.kt' ! -name 'GameView.kt')
kotlinc $PREVIEW_STUBS tools/preview/achievement_smoke.kt \
  tools/preview/achievement_ui_smoke.kt $SRCS \
  -d tools/preview/out/classes-ach -jvm-target 17
java -cp "tools/preview/out/classes-ach:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.AchievementSmoke
java -cp "tools/preview/out/classes-ach:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.AchievementUiSmoke
```

### 성능 프로브 (버튼 입력 병목)

`perf_smoke.kt` 는 실제 게임 코드를 그대로 돌리면서 **버튼을 눌렀을 때의 프레임 비용**을
재고, 한 프레임에 새로 만들어 나는 네이티브 객체(`android.graphics` 스텁의 `GfxStats`)와
드로우 콜을 센다. 보려는 지점은 "누르면 멈칫한다"는 지점 — 가방(메뉴) 8탭, 가방 ✕ 버튼,
도감 페이지 넘김(사진 디코드), 상세 도감 넘김, HUD 버튼 연타 등.

주의: 이 파이프라인은 Java2D 스텁이라 절대 시간은 기기와 다르다. **프레임당 몇 ms**보다
**프레임당 몇 번의 네이티브 객체 생성과 드로우 콜**이 기기 성능에 그대로 옮겨가는 지표다.
디코드는 `GfxStats.decodes` 중 게임 스레드에서 일어난 것(`decodesMain`)만 따로 세므로,
사진(에셋 스트림과 사진집 파일 모두)을 게임 스레드에서 디코드하는지 바로 볼 수 있다.
`latency_smoke` 는 `GfxStats.compressionsMain` 으로 촬영 시 파일 압축도 검사한다.

```bash
SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
  ! -name 'MainActivity.kt' ! -name 'GameView.kt')
kotlinc tools/preview/src/*.kt tools/preview/perf_smoke.kt \
  tools/preview/world_resume_smoke.kt tools/preview/latency_smoke.kt $SRCS \
  -d tools/preview/out/classes-perf -jvm-target 17
java -cp "tools/preview/out/classes-perf:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.LatencySmoke
java -cp "tools/preview/out/classes-perf:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.WorldResumeSmoke
java -cp "tools/preview/out/classes-perf:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.PerfSmoke
```

`latency_smoke` 는 오버레이 등장 **첫 프레임의 탭**과 이동하는 버튼의 터치 좌표·
전체 화면 임시 비트맵 할당·첫 메뉴 아이콘 사전 생성·처음 본 새의 사진 기준색 로딩·
촬영 사진의 비동기 JPEG 저장/일시정지 전 완료·사진집/상세 보기의 비동기 로딩을 확인한다.
`world_resume_smoke` 는 가리는 오버레이(가방·지도·상점…)가 떠 있는 **모든 프레임**에서
월드 비트맵을 재사용하고, 닫은 **첫 프레임**에 최신 월드를 다시 그리며, 그 뒤에도
매 프레임 갱신하는지 확인한다. 판정은 `GfxStats.drawBitmap` 횟수(월드 약 2000회,
건너뛰면 HUD 정도만)로 한다.

`mobile_perf_smoke.kt` 는 자동 화질(지속 부하만 감지·수동 설정 존중·HUD 실해상도 유지),
지면 청크 캐시의 호출 감소, 물 애니메이션과 화면 밖 풀 culling/재진입을 확인한다.
위 컴파일 명령에 `tools/preview/mobile_perf_smoke.kt`를 추가하고
`com.pizzaandbird.preview.MobilePerfSmoke`를 실행하면 된다.

### 스크린샷 결정성

프리뷰의 `android.os.SystemClock` 는 벽시계가 아니라 **게임 시간(dt)만 흐르는 시계**다
(`PreviewMain.simulate()` 가 `SystemClock.advance(dt)` 로 전진시킨다). 오버레이 등장 연출
(`Overlay.bornAt`, `UiKit.enter`)이 실제 실행 속도에 따라 달라져 같은 소스를 돌려도
스크린샷이 매번 조금씩 달라지는 것을 막기 위해서다.
### 출력물

| 파일 | 내용 |
|---|---|
| `01_tiles.png` | 전체 타일 아틀라스 (변형 포함) |
| `02_sprites.png` | 플레이어/자전거/NPC/고양이/장식/아이콘 |
| `02b_character_hd.png` | **캐릭터 화질 비교 시트** — 32px 도트×8 / HD(96px) 축소 / HD 1:1 / 레벨업용 256px 만세 / HD 걷기·자전거 |
| `03_birds.png` | 새 전체 컬렉션 |
| `04_title.png` | 타이틀 화면 |
| `05_region_select.png` | 정착 지역 선택 |
| `06~12_world_*.png` | 지역별 월드 (낮/노을/밤 포함) |
| `13_photo_mode.png` | 카메라(탐조) 모드 |
| `14~15_home_*.png` | 집 내부 (낮/밤) |
| `16_dialog.png` | 대화 |
| `17_menu_tab1~8.png` | 메뉴 탭 (업적 포함) |
| `21~27_*.png` | 피자 굽기 3단계/사진 결과/지도/장식 상점 |
| `30~32_*.png` | 백업 코드 만들기/복원 |
| `33_achievement_stats.png` | 업적 통계 상세 |
| `34_achievement_detail.png` | 업적 상세 및 해금 날짜 |
| `35_achievement_unlock_toast.png` | 실제 업적 해금 토스트 |
| `36_levelup.png` | 레벨업 축하(만세) — 캐릭터를 가장 크게 띄우는 화면 |
| `37_levelup_gear.png` | 새 탐조 장비를 갖추는 레벨업 (장비 등급 3) |
| `38_character_select.png` | 캐릭터 선택 카드 |

> 게임 동작의 기준은 어디까지나 Kotlin 쪽 코드다. 이 파이프라인은 실제 코드를
> 실행하므로 화면은 실기기와 동일한 알고리즘으로 그려진다.

### 대화상자 배치 감사 (dialog audit)

"글자 양에 비해 상자가 무식하게 큰 놈"을 눈이 아니라 **수치**로 찾는 도구.
`android_stubs_graphics.kt` 의 Canvas 스텁에 기본 꺼진(non-null일 때만 동작)
기록 훅을 달아 두고, 대화상자·장비 선택·가방·인테리어 창 등을 실제 상태
(장비 0~5개, 글자 배율 1.3×, 800×400 화면 …)로 띄워 모든 상자/글자 draw를
TSV로 남긴다. 분석 스크립트가 프레임별로 **패널 크기(dp) · 세로 밴드 사용률 ·
내부 최대 빈 띠(dp) · 글자 면적 비율**을 계산해 낮은 순으로 랭킹한다.

```bash
# 1) 컴파일 + 감사 렌더 (PNG + events.tsv → tools/preview/out/audit)
kotlinc tools/preview/src/*.kt $SRCS -d tools/preview/out/classes-audit -jvm-target 17
java -cp "tools/preview/out/classes-audit:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  com.pizzaandbird.preview.DialogAuditMain tools/preview/out/audit

# 2) 수치 랭킹
python3 tools/preview/dialog_audit_analyze.py tools/preview/out/audit/events.tsv

# 3) (선택) 개선 전/후 디렉터리를 넣어 한 장 비교 시트 만들기
python3 tools/preview/dialog_audit_compare.py \
  tools/preview/out/audit_before tools/preview/out/audit docs/img/ui-fit-before-after.png
```

기준치: 세로 사용률 60% 미만이거나 내부 빈 띠 40dp 이상이면 "상자가 글자보다
크다"는 신호. `preview/16_dialog.png` 같은 스크린샷과 함께 보면 배치 확인이 빠르다.

---

## 2) 파이썬 맵 미리보기 (길 디자인)

맵 레이아웃과 타일 아트 알고리즘(`Assets.kt`, `Maps.kt`, `Roads.kt`)을 파이썬으로
옮겨 놓은 도구. 길 모양을 고칠 때 APK를 빌드하지 않고도 결과를 눈으로 확인할 수 있다.

```bash
pip3 install pillow numpy

# 1) 144개(지역 12 x 홈 12) 맵 조합의 규칙 검사 — tools/MapTest.kt 의 파이썬 판
python3 tools/preview/check.py

# 2) 캐릭터 애니메이션 시트 + GIF (docs/img/ 갱신)
python3 tools/preview/render_people.py docs/img

# 3) 자전거 스펙 시트 (models_side/paints/accessories — docs/img/ 갱신)
python3 tools/preview/render_bikes.py docs/img

# 4) 맵 한 장 렌더링
python3 - <<'PY'
import sys; sys.path.insert(0, 'tools/preview')
import render, mapgen
m = mapgen.build('seoul', 'seoul')
render.render(m.tile, m.base, m.pave, m.deco, m.w, m.h).save('/tmp/seoul.png')
PY
```

| 파일 | 역할 |
| --- | --- |
| `pixelcanvas.py` | `android.graphics.Canvas` / `java.util.Random` 의 픽셀 단위 클론 (안티에일리어싱 없음) |
| `roads.py` | `Roads.kt` 프로토타입 — 포장 실루엣·바퀴자국·판석·연석·문양 |
| `mapgen.py` | `MapBuilder.build()` 프로토타입 — 간선도로/샛길/광장 배치 |
| `render.py` | 지면 → 포장 → 데칼 → 구조물 → 그림자 순서로 합성 |
| `people.py` | `CharacterArt.kt` 프로토타입 — 사람/자전거/고양이 **관절 애니메이션** (포즈 수식이 게임과 동일) |
| `render_people.py` | 동작 스프라이트 시트 · GIF 출력 |
| `render_bikes.py` | 자전거 스펙 시트 출력 — 11종 모델 · 프레임/타이어/안장 색상표 · 액세서리 (cards의 스펙 표와 1:1) |
| `tiles_legacy.py` | `Assets.kt` 의 기존 타일 아트를 옮겨 온 **자동 생성** 파일 |
| `_gen_tiles_legacy.py` | 위 파일을 `Assets.kt` 에서 다시 만들어 내는 스크립트 |

> 이 파이썬 도구는 "빠른 눈 확인"용이며, 길 규칙을 바꿀 때는 `mapgen.py` 와
> `Maps.kt` 를 **같이** 고쳐야 한다.
