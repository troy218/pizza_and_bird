#!/usr/bin/env bash
# 안드로이드 SDK 없이 Kotlin 소스만 빠르게 타입체크한다 (개발 샌드박스용).
#   JDK      : /tmp/kt/jdkwheel/jdk4py/java-runtime   (pip download jdk4py)
#   kotlinc  : /tmp/kt/k2/package                     (npm pack kotlin-compiler@2.0.21)
#   android  : /tmp/kt/ap/android-33/android.jar      (github.com/Sable/android-platforms)
# R 클래스(res/raw·drawable)는 실제 aapt 대신 스텁을 만들어 넣는다.
set -u
export JAVA_HOME=/tmp/kt/jdkwheel/jdk4py/java-runtime
export PATH="$JAVA_HOME/bin:$PATH"
export LANG=C.UTF-8
cd "$(dirname "$0")/.."

STUB=/tmp/kt/rstub
mkdir -p "$STUB"
{
  echo "package com.pizzaandbird.game"
  echo "object R {"
  echo "    object raw {"
  i=1
  for f in app/src/main/res/raw/*; do
    n=$(basename "$f"); n="${n%%.*}"
    echo "        const val $n = $i"; i=$((i+1))
  done
  echo "    }"
  echo "    object drawable {"
  for f in app/src/main/res/drawable/*.xml; do
    n=$(basename "$f" .xml)
    echo "        const val $n = $i"; i=$((i+1))
  done
  echo "    }"
  echo "}"
} > "$STUB/R.kt"

KC=/tmp/kt/k2/package/bin/kotlinc
AP=/tmp/kt/ap/android-33/android.jar
# 컴파일러 힙 — 이 저장소 규모(5만 줄)는 기본 힙으로는 IR 단계에서 OOM 이 난다.
KOTLINC_OPTS="-J-Xmx1500m"

echo "== 게임 소스 타입체크 =="
"$KC" $KOTLINC_OPTS -cp "$AP" -jvm-target 17 -nowarn -d /tmp/kt/out \
  app/src/main/java/com/pizzaandbird/game/*.kt "$STUB/R.kt" 2>&1 | grep -v "^warning:" | head -40

# [P11] 검증 스크립트도 함께 타입체크한다.
#   tools/jvm_stub/Json.kt : org.json 의 JVM 구현 (테스트에서만 사용, 앱 빌드 미포함)
#   tools/MapTest.kt       : 맵/피자 회귀 스위트 (실행에는 실기기·Robolectric 필요 — Paint 스텁)
#   tools/PizzaTest.kt     : 피자 조각·빠른 피자·특성·세이브 로직 (Android 없이 JVM 실행 가능)
echo "== 테스트 스크립트 타입체크 =="
"$KC" $KOTLINC_OPTS -jvm-target 17 -nowarn -d /tmp/kt/jsonstub tools/jvm_stub/Json.kt 2>&1 | grep -v "^warning:" | head -20
"$KC" $KOTLINC_OPTS -cp "$AP:/tmp/kt/out" -jvm-target 17 -nowarn -d /tmp/kt/outtest \
  tools/MapTest.kt tools/PizzaTest.kt 2>&1 | grep -v "^warning:" | head -40

# ./tools/typecheck.sh --run  → PizzaTest 를 실제로 돌린다 (기대값: 통과 93 / 실패 0)
if [ "${1:-}" = "--run" ]; then
  echo "== PizzaTest 실행 =="
  java -Dfile.encoding=UTF-8 \
    -cp "/tmp/kt/jsonstub:$AP:/tmp/kt/out:/tmp/kt/outtest:/tmp/kt/k2/package/lib/kotlin-stdlib.jar" \
    PizzaTestKt
fi
