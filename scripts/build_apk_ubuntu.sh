#!/usr/bin/env bash
# =============================================================================
# Pizza and Bird — 우분투 서버에서 APK 빌드 자동화 스크립트
#
#   용도: 깨끗한 Ubuntu 서버(22.04/24.04, x86_64/ARM64)에서
#         JDK 17 + Android SDK를 설치하고 debug/release APK를 빌드한다.
#
#   사용법:
#     1) 저장소를 이미 clone한 경우:
#          bash scripts/build_apk_ubuntu.sh
#     2) clone부터 자동으로 하려면:
#          bash <(curl -fsSL <raw 스크립트 주소>)    # 또는 파일 복사 후 실행
#
#   결과물:
#     app/build/outputs/apk/debug/app-debug.apk
#     app/build/outputs/apk/release/app-release-unsigned.apk  (키스토어 없으면 디버그 키 서명)
# =============================================================================
set -euo pipefail

REPO_URL="https://github.com/troy218/pizza_and_bird.git"
BRANCH="${BRANCH:-main}"
SDK_ROOT="${ANDROID_HOME:-$HOME/android-sdk}"
BUILD_RELEASE="${BUILD_RELEASE:-1}"        # 0이면 debug만 빌드

log() { printf '\n\033[1;32m▶ %s\033[0m\n' "$*"; }

# ---------------------------------------------------------------- 0. 사전 도구
log "필수 패키지 설치 (git, unzip, wget, openjdk-17)"
sudo apt-get update -y
sudo apt-get install -y git unzip wget openjdk-17-jdk
export JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")

# ---------------------------------------------------------------- 1. 소스 받기
if [ -d .git ] && git remote get-url origin 2>/dev/null | grep -q pizza_and_bird; then
  log "이미 저장소 안임 — 최신 커밋으로 갱신"
  git pull --ff-only || true
elif [ ! -f app/build.gradle.kts ]; then
  log "GitHub에서 저장소 clone"
  git clone --depth 1 --branch "$BRANCH" "$REPO_URL" pizza_and_bird
  cd pizza_and_bird
fi

# ---------------------------------------------------------------- 2. Android SDK
if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "Android SDK commandline-tools 설치 → $SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  TMP_ZIP=$(mktemp /tmp/cmdtools-XXXX.zip)
  wget -q -O "$TMP_ZIP" \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$TMP_ZIP" -d "$SDK_ROOT/cmdline-tools"
  mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -f "$TMP_ZIP"
fi
export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

log "SDK 라이선스 동의 + 플랫폼 설치 (android-35, build-tools 35)"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

# ---------------------------------------------------------------- 3. 빌드
chmod +x ./gradlew

log "debug APK 빌드"
./gradlew --no-daemon assembleDebug

if [ "$BUILD_RELEASE" = "1" ]; then
  # 키스토어 환경변수(또는 -P 옵션)가 있으면 정식 서명, 없으면 디버그 키 서명.
  # 자세한 서명 옵션은 .github/workflows/android-apk.yml 상단 주석 참고.
  log "release APK 빌드 (키스토어 없으면 디버그 키 서명 — 베타 배포용)"
  ./gradlew --no-daemon assembleRelease
fi

# ---------------------------------------------------------------- 4. 결과 안내
log "완료! 생성된 APK:"
find app/build/outputs/apk -name '*.apk' -exec ls -lh {} \;

cat <<'EOF'

서버에서 스마트폰으로 APK 옮기기 예시:
  scp user@server:~/pizza_and_bird/app/build/outputs/apk/debug/app-debug.apk .
  adb install app-debug.apk        # 또는 파일 전송 후 설치

서버에서 폰으로 직접 설치(폰이 adb 네트워크 모드일 때):
  adb connect <폰-IP>:5555 && adb install app/build/outputs/apk/debug/app-debug.apk
EOF
