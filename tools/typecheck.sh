#!/usr/bin/env bash
# 안드로이드 SDK 없이 Kotlin 소스만 빠르게 타입체크한다 (개발 샌드박스용).
#   JDK      : /tmp/kt/jdkwheel/jdk4py/java-runtime   (pip install jdk4py)
#   kotlinc  : /tmp/kt/k2/package                     (npm pack kotlin-compiler@2.0.21)
#   android  : /tmp/kt/ap/android-33/android.jar      (github.com/Sable/android-platforms)
set -u
export JAVA_HOME=/tmp/kt/jdkwheel/jdk4py/java-runtime
export PATH="$JAVA_HOME/bin:$PATH"
export LANG=C.UTF-8
cd "$(dirname "$0")/.."
/tmp/kt/k2/package/bin/kotlinc \
  -cp /tmp/kt/ap/android-33/android.jar \
  -jvm-target 17 -nowarn -d /tmp/kt/out \
  app/src/main/java/com/pizzaandbird/game/*.kt 2>&1 | grep -v "^warning:" | head -40
