#!/usr/bin/env bash
# =============================================================================
# build_apk_ubuntu.sh — 우분투(헤드리스) 서버에서 피자와 새 APK 빌드
#
#  사용법 (토큰은 터미널에서 바로 입력 — 파일에 저장하지 않음):
#     bash build_apk_ubuntu.sh                       # 토큰 물어봄(입력 숨김)
#     GITHUB_TOKEN=ghp_xxx bash build_apk_ubuntu.sh   # 환경변수로 전달
#
#  하는 일:
#     1) JDK 17 / git / unzip 설치 (없으면)
#     2) Android SDK command-line tools 설치 + 라이선스 동의 + API 35
#     3) 토큰으로 저장소 clone (또는 기존 디렉터리면 pull) — 끝나면 remote에서 토큰 제거
#     4) local.properties 생성 → ./gradlew assembleDebug (필요시 release)
#     5) APK 경로 출력
#
#  릴리스 서명까지 하려면 (선택):
#     PB_KEYSTORE=/path/release.keystore PB_KEY_ALIAS=pizzaandbird \
#     PB_KEY_PASSWORD=키비번 bash build_apk_ubuntu.sh
# =============================================================================
set -euo pipefail

OWNER_REPO="${OWNER_REPO:-troy218/pizza_and_bird}"
BRANCH="${BRANCH:-main}"
WORKDIR="${WORKDIR:-$HOME/pizza_and_bird}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# ------------------------------------------------------------------ 0) 토큰
if [ -z "${GITHUB_TOKEN:-}" ]; then
  printf 'GitHub 토큰(입력해도 화면에 안 보입니다): '
  read -rs GITHUB_TOKEN
  echo
fi
[ -n "$GITHUB_TOKEN" ] || { echo "토큰이 비어 있습니다."; exit 1; }

# ------------------------------------------------------------------ 1) 기본 패키지
log "기본 패키지 / JDK 17 확인"
if [ ! -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
  sudo apt-get update -y
  sudo apt-get install -y openjdk-17-jdk-headless unzip curl git
fi
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ------------------------------------------------------------------ 2) Android SDK
if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "Android command-line tools 설치 → $ANDROID_SDK_ROOT"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$tmp/cmdline-tools.zip"
  unzip -q "$tmp/cmdline-tools.zip" -d "$ANDROID_SDK_ROOT/cmdline-tools"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

log "SDK 라이선스 동의 + components 설치 (platforms;android-35, build-tools;35.0.0)"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

# ------------------------------------------------------------------ 3) 소스
log "저장소 가져오기 → $WORKDIR ($BRANCH)"
TOKEN_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${OWNER_REPO}.git"
CLEAN_URL="https://github.com/${OWNER_REPO}.git"

if [ -d "$WORKDIR/.git" ]; then
  git -C "$WORKDIR" remote set-url origin "$TOKEN_URL"
  git -C "$WORKDIR" fetch --depth 1 origin "$BRANCH"
  git -C "$WORKDIR" checkout -B "$BRANCH" FETCH_HEAD
else
  git clone --depth 1 --branch "$BRANCH" "$TOKEN_URL" "$WORKDIR"
fi
# .git/config 에 토큰을 남기지 않는다
git -C "$WORKDIR" remote set-url origin "$CLEAN_URL"
unset TOKEN_URL GITHUB_TOKEN
cd "$WORKDIR"

# ------------------------------------------------------------------ 4) 빌드
log "local.properties 생성"
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
chmod +x gradlew

log "debug APK 빌드"
./gradlew --no-daemon assembleDebug

if [ -n "${PB_KEYSTORE:-}" ]; then
  log "release APK 빌드 (정식 키스토어 서명)"
  ./gradlew --no-daemon assembleRelease \
    -PpbKeystore="$PB_KEYSTORE" \
    -PpbStorePassword="${PB_KEY_PASSWORD:?PB_KEY_PASSWORD 필요}" \
    -PpbKeyAlias="${PB_KEY_ALIAS:-pizzaandbird}" \
    -PpbKeyPassword="${PB_KEY_PASSWORD}"
else
  log "release APK 빌드 (키스토어 미지정 → debug 키 서명, 베타 배포용)"
  ./gradlew --no-daemon assembleRelease
fi

# ------------------------------------------------------------------ 5) 결과
log "완료 — APK 위치"
find "$WORKDIR/app/build/outputs/apk" -name '*.apk' -printf '%p  (%s bytes)\n'
