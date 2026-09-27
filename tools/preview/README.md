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
├── fonts/              NotoSansKR (자동 다운로드, git 미포함)
└── out/                렌더링 결과 (git 미포함)
```

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

### 출력물

| 파일 | 내용 |
|---|---|
| `01_tiles.png` | 전체 타일 아틀라스 (변형 포함) |
| `02_sprites.png` | 플레이어/자전거/NPC/고양이/장식/아이콘 |
| `03_birds.png` | 새 전체 컬렉션 |
| `04_title.png` | 타이틀 화면 |
| `05_region_select.png` | 정착 지역 선택 |
| `06~12_world_*.png` | 지역별 월드 (낮/노을/밤 포함) |
| `13_photo_mode.png` | 카메라(탐조) 모드 |
| `14~15_home_*.png` | 집 내부 (낮/밤) |
| `16~27_*.png` | 대화/메뉴 4탭/피자 굽기 3단계/사진 결과/지도/장식 상점 |

> 게임 동작의 기준은 어디까지나 Kotlin 쪽 코드다. 이 파이프라인은 실제 코드를
> 실행하므로 화면은 실기기와 동일한 알고리즘으로 그려진다.

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

# 3) 맵 한 장 렌더링
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
| `tiles_legacy.py` | `Assets.kt` 의 기존 타일 아트를 옮겨 온 **자동 생성** 파일 |
| `_gen_tiles_legacy.py` | 위 파일을 `Assets.kt` 에서 다시 만들어 내는 스크립트 |

> 이 파이썬 도구는 "빠른 눈 확인"용이며, 길 규칙을 바꿀 때는 `mapgen.py` 와
> `Maps.kt` 를 **같이** 고쳐야 한다.
