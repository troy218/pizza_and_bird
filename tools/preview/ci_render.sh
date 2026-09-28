#!/usr/bin/env bash
# tools/preview/ci_render.sh — 그래픽 프리뷰 파이프라인 (CI 전용 스크립트)
#
# gradlew 의 CI 훅이 빌드 종료 후(성공/실패 무관) 실행한다. ($1 = gradle 종료코드)
# 1) kotlinc 다운로드(캐시) → 2) 한글 폰트 다운로드 → 3) 게임 렌더링 코드를
#    스텁과 함께 컴파일 → 4) 헤드리스 실행으로 모든 화면 스크린샷 생성 →
#    5) preview/ 폴더에 커밋&푸시 (PNG + build.log)
#
# 어떤 단계가 실패해도 최대한 계속 진행해서 원인을 preview/build.log 로 남긴다.
set -u
cd "$(dirname "$0")/../.."
ROOT="$PWD"
OUT="tools/preview/out"
GRADLE_EXIT="${1:-unknown}"
mkdir -p "$OUT" preview

SHA=$(git rev-parse HEAD 2>/dev/null || echo "?")

# 같은 커밋에 대해 이미 성공적으로 렌더링했다면 스킵 (assembleRelease 등 2회째 호출)
if [ -f "$OUT/.last_ok" ] && [ "$(cat "$OUT/.last_ok" 2>/dev/null)" = "$SHA" ] \
    && ls preview/*.png >/dev/null 2>&1; then
  echo "preview: 이미 렌더링됨 ($SHA) — 스킵"
  exit 0
fi

{
  echo "# preview build $(date -u '+%Y-%m-%d %H:%M:%SZ')"
  echo "# commit: $SHA"
  echo "# gradle exit code: $GRADLE_EXIT"
} > preview/build.log

# ---------------------------------------------------------------- kotlinc
KC="tools/preview/.kotlinc"
KCBIN="$KC/kotlinc/bin/kotlinc"
if [ ! -x "$KCBIN" ]; then
  echo ">> kotlin 컴파일러 다운로드 중..." >> preview/build.log
  rm -rf "$KC"
  mkdir -p "$KC"
  if curl -fsSL --retry 3 -o /tmp/kc.zip \
      https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip; then
    unzip -q /tmp/kc.zip -d "$KC" && rm -f /tmp/kc.zip \
      || echo "ERROR: kotlinc 압축 해제 실패" >> preview/build.log
  else
    echo "ERROR: kotlinc 다운로드 실패" >> preview/build.log
  fi
fi

# ---------------------------------------------------------------- fonts
mkdir -p tools/preview/fonts
if [ ! -s "tools/preview/fonts/NotoSansKR-Regular.ttf" ] || [ ! -s "tools/preview/fonts/NotoSansKR-Bold.ttf" ]; then
  curl -fsSL --retry 3 -o /tmp/font.tgz \
    "https://registry.npmjs.org/@expo-google-fonts/noto-sans-kr/-/noto-sans-kr-0.4.3.tgz" \
    && tar xzf /tmp/font.tgz -C tools/preview/fonts --strip-components=2 \
        package/400Regular/NotoSansKR_400Regular.ttf package/700Bold/NotoSansKR_700Bold.ttf \
    && mv -f tools/preview/fonts/NotoSansKR_400Regular.ttf tools/preview/fonts/NotoSansKR-Regular.ttf \
    && mv -f tools/preview/fonts/NotoSansKR_700Bold.ttf tools/preview/fonts/NotoSansKR-Bold.ttf \
    && rm -f /tmp/font.tgz \
    || echo "WARN: 폰트 다운로드 실패 (npm @expo-google-fonts/noto-sans-kr)" >> preview/build.log
fi

# ---------------------------------------------------------------- compile & render
STATUS="ok"
if [ -x "$KCBIN" ]; then
  SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' \
    ! -name 'MainActivity.kt' ! -name 'GameView.kt' \
    ! -name 'PostProcessing.kt')
  echo ">> 프리뷰 컴파일 (게임 소스 + 스텁)..." >> preview/build.log
  if "$KCBIN" tools/preview/src/*.kt $SRCS \
      -d "$OUT/classes-preview" -jvm-target 17 > "$OUT/render.log" 2>&1; then
    echo ">> 프리뷰 렌더링..." >> preview/build.log
    rm -f "$OUT"/*.png
    if java -cp "$OUT/classes-preview:$KC/kotlinc/lib/kotlin-stdlib.jar" \
      com.pizzaandbird.preview.PreviewMain "$OUT" >> "$OUT/render.log" 2>&1; then
      if compgen -G "$OUT/*.png" > /dev/null; then
        echo "$SHA" > "$OUT/.last_ok"
      else
        STATUS="render-empty"
      fi
    else
      STATUS="render-failed"
    fi
  else
    STATUS="compile-failed"
  fi
else
  STATUS="no-compiler"
fi

echo "# status: $STATUS" >> preview/build.log

# ---------------------------------------------------------------- commit
# 프리뷰 생성에 성공한 경우에만 기존 이미지를 교체한다. 컴파일러/렌더러 오류로
# 저장소의 마지막 정상 프리뷰까지 지워지는 일을 막는다.
if [ "$STATUS" = "ok" ] && compgen -G "$OUT/*.png" > /dev/null; then
  rm -f preview/*.png
  cp "$OUT"/*.png preview/
else
  echo "WARN: 프리뷰 갱신 실패($STATUS) — 기존 스크린샷을 보존합니다." >> preview/build.log
fi
{
  echo
  echo "----- gradle build log (error lines + last 120) -----"
  { grep -n -E "error:|e: |FAILURE|Exception|Caused by" "$OUT/gradle_build.log" 2>/dev/null | head -60; \
    echo "..."; tail -n 120 "$OUT/gradle_build.log" 2>/dev/null; } | head -200
  echo
  echo "----- preview render log (last 200 lines) -----"
  tail -n 200 "$OUT/render.log" 2>/dev/null
} >> preview/build.log

git config user.name "arena-preview-bot"
git config user.email "arena-preview-bot@users.noreply.github.com"
git add preview
if git diff --cached --quiet; then
  echo "preview: 변경사항 없음"
else
  git commit -m "preview: update rendered screenshots [skip ci]" >> "$OUT/commit.log" 2>&1
  git push >> "$OUT/commit.log" 2>&1 || echo "WARN: push 실패 (다음 push에서 재시도)" >> preview/build.log
fi
exit 0
