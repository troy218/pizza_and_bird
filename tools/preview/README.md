# tools/preview — 그래픽 프리뷰 파이프라인 🔬

게임의 실제 렌더링 코드(Assets / Maps / Scenes / WorldScene / HomeScene / Hud / Overlays)를
안드로이드 없이 JVM에서 그대로 실행해서 **모든 화면의 스크린샷을 PNG로 뽑는** 도구.
그래픽/디자인 작업을 "눈으로 보면서" 반복하기 위한 아트 파이프라인이다.

## 구조

```
tools/preview/
├── src/
│   ├── android_stubs_graphics.kt   android.graphics 스텁 (Java2D 구현)
│   ├── android_stubs_content.kt    android.content 스텁
│   ├── android_stubs_os.kt         android.os 스텁
│   ├── android_stubs_view.kt       android.view 이벤트 스텁
│   ├── json_stubs.kt               org.json 최소 구현
│   ├── mainactivity_stub.kt        MainActivity 스텁 (Game.kt 컴파일용)
│   └── preview_main.kt             프리뷰 렌더러 (모든 화면 스크린샷)
└── fonts/                          NotoSansKR (CI에서 자동 다운로드, git 미포함)
```

- 게임 코드는 **한 글자도 수정 없이** 그대로 컴파일된다 (스텁이 android API 시그니처를 1:1 제공).
- MainActivity.kt / GameView.kt만 제외 (SurfaceView/Activity 의존 — 프리뷰 불필요).

## 실행 (GitHub Actions — 자동)

루트 빌드의 `ciRender` 훅이 `arena/01a0e0d9-pizza-and-bird` 브랜치 CI에서만 동작한다.
APK 빌드(`assembleDebug`)가 끝나면 자동으로 렌더링해서 `preview/*.png`를
같은 브랜치에 커밋한다 (`[skip ci]` 커밋이라 무한 루프 없음).

## 로컬 실행 (JDK 17)

```bash
./gradlew :tools-preview:renderPreview
# 결과: tools/preview/out/*.png
```

## 출력물

| 파일 | 내용 |
|---|---|
| `01_tiles.png` | 전체 타일 아틀라스 (변형 포함) |
| `02_sprites.png` | 플레이어/자전거/NPC/고양이/장식/아이콘 |
| `03_birds.png` | 새 26종 전체 컬렉션 |
| `04_title.png` | 타이틀 화면 |
| `05_region_select.png` | 정착 지역 선택 |
| `06~12_world_*.png` | 지역별 월드 (낮/노을/밤 포함) |
| `13_photo_mode.png` | 카메라(탐조) 모드 |
| `14~15_home_*.png` | 집 내부 (낮/밤) |
| `16~27_*.png` | 대화/메뉴 4탭/피자 굽기 3단계/사진 결과/지도/장식 상점 |
