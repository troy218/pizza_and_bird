#!/usr/bin/env bash
# [P05] 백업 코드 로직을 **안드로이드/에뮬레이터 없이** JVM에서 검증한다.
#
#   bash tools/backup_test/run.sh
#
# 실제 소스(Backup.kt · GameState.kt · Weather.kt · Data.kt · Cameras.kt · Quests.kt · Season.kt 등)를
# 이 폴더의 스텁(android.content / android.util.Base64 / org.json / Game·Context 가짜)과
# 함께 컴파일해 왕복·손상·구버전 시나리오를 돌린다.
# JDK/kotlinc 경로는 tools/typecheck.sh 와 같은 샌드박스 위치를 쓴다.
set -u
export JAVA_HOME=${JAVA_HOME:-/tmp/kt/jdkwheel/jdk4py/java-runtime}
export PATH="$JAVA_HOME/bin:$PATH"
export LANG=C.UTF-8
KOTLINC=${KOTLINC:-/tmp/kt/k2/package/bin/kotlinc}
cd "$(dirname "$0")/../.."
SRC=app/src/main/java/com/pizzaandbird/game
OUT=/tmp/kt/backup_test_out
rm -rf "$OUT" && mkdir -p "$OUT"

"$KOTLINC" -jvm-target 17 -nowarn -d "$OUT" \
  "$SRC/Backup.kt" "$SRC/GameState.kt" "$SRC/Weather.kt" "$SRC/Data.kt" \
  "$SRC/Cameras.kt" "$SRC/Quests.kt" "$SRC/KoreaMap.kt" \
  "$SRC/BirdChecklist.kt" "$SRC/BirdEncyclopedia.kt" \
  "$SRC/Season.kt" \
  tools/backup_test/*.kt 2>&1 | grep -v "^warning:" | head -40

KOTLIN_LIB="$(dirname "$(readlink -f "$KOTLINC")")/../lib"
"$JAVA_HOME/bin/java" -cp "$OUT:$KOTLIN_LIB/kotlin-stdlib.jar" com.pizzaandbird.game.TestKt
