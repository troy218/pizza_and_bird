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

/tmp/kt/k2/package/bin/kotlinc \
  -cp /tmp/kt/ap/android-33/android.jar \
  -jvm-target 17 -nowarn -d /tmp/kt/out \
  app/src/main/java/com/pizzaandbird/game/*.kt "$STUB/R.kt" 2>&1 | grep -v "^warning:" | head -40
