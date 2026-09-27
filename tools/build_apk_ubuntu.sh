#!/usr/bin/env bash
# Ubuntu x86_64에서 GitHub Actions 없이 APK 빌드. Android Studio 불필요.
set -euo pipefail

fail() { printf '오류: %s\n' "$*" >&2; exit 1; }

if (( $# > 1 )); then
    fail "사용법: $0 [debug|release]"
fi
variant=${1:-debug}
case "$variant" in
    debug)   task=assembleDebug;   apk=app/build/outputs/apk/debug/app-debug.apk ;;
    release) task=assembleRelease; apk=app/build/outputs/apk/release/app-release.apk ;;
    *) fail "사용법: $0 [debug|release]" ;;
esac

[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] ||
    fail "이 스크립트는 Ubuntu/Linux x86_64(amd64)용입니다."
for command in curl unzip sha256sum; do
    command -v "$command" >/dev/null 2>&1 ||
        fail "'$command' 명령이 없습니다. sudo apt-get install -y openjdk-17-jdk curl unzip ca-certificates"
done
# Gradle wrapper는 JAVA_HOME이 있으면 PATH의 java보다 이를 우선 사용한다.
if [[ -n ${JAVA_HOME:-} ]]; then
    java_bin="$JAVA_HOME/bin/java"
    javac_bin="$JAVA_HOME/bin/javac"
else
    java_bin=java
    javac_bin=javac
fi
for command in "$java_bin" "$javac_bin"; do
    command -v "$command" >/dev/null 2>&1 ||
        fail "'$command' 명령이 없습니다. sudo apt-get install -y openjdk-17-jdk curl unzip ca-certificates"
done
java_version=$("$java_bin" -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')
[[ $java_version =~ ^[0-9]+$ ]] && (( java_version >= 17 )) ||
    fail "JDK 17 이상이 필요합니다 (권장: openjdk-17-jdk; 현재 JAVA_HOME/PATH 확인)."

if [[ -n ${ANDROID_HOME:-} && -n ${ANDROID_SDK_ROOT:-} &&
      ${ANDROID_HOME%/} != "${ANDROID_SDK_ROOT%/}" ]]; then
    fail "ANDROID_HOME과 ANDROID_SDK_ROOT가 서로 다른 위치를 가리킵니다."
fi
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/Android/Sdk"}}
[[ $sdk_root == /* ]] || fail "ANDROID_HOME은 절대 경로여야 합니다: $sdk_root"
export ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root"

# https://developer.android.com/studio#command-tools (Linux zip + 공식 SHA-256)
# 버전을 고정하여 매번 같은 도구를 내려받고, 다운로드 무결성을 검증한다.
tools_url=https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
tools_sha256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x $sdkmanager ]]; then
    # 기존 SDK에서 latest 대신 버전 번호로 설치했을 수도 있다.
    for candidate in "$sdk_root"/cmdline-tools/*/bin/sdkmanager; do
        if [[ -x $candidate ]]; then sdkmanager=$candidate; break; fi
    done
fi
if [[ ! -x $sdkmanager ]]; then
    [[ ! -e $sdk_root/cmdline-tools/latest ]] ||
        fail "불완전한 SDK 설치가 있습니다: $sdk_root/cmdline-tools/latest (직접 확인 후 재실행하세요)."
    tmp_dir=$(mktemp -d)
    trap 'rm -rf -- "$tmp_dir"' EXIT
    printf 'Android 명령줄 도구 다운로드 → %s\n' "$sdk_root"
    curl --fail --location --show-error --silent --retry 3 --output "$tmp_dir/tools.zip" "$tools_url"
    printf '%s  %s\n' "$tools_sha256" "$tmp_dir/tools.zip" | sha256sum --check --status ||
        fail "SDK 도구 ZIP SHA-256 검증 실패. 다운로드 파일을 사용하지 않습니다."
    unzip -q "$tmp_dir/tools.zip" -d "$tmp_dir"
    [[ -x $tmp_dir/cmdline-tools/bin/sdkmanager ]] || fail "SDK 도구 ZIP 내부 구조가 예상과 다릅니다."
    mkdir -p "$sdk_root/cmdline-tools"
    mv "$tmp_dir/cmdline-tools" "$sdk_root/cmdline-tools/latest"
    sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
fi

printf 'Android SDK 라이선스를 검토하고 동의할 경우 안내에 따라 y를 입력하세요.\n'
"$sdkmanager" --sdk_root="$sdk_root" --licenses
"$sdkmanager" --sdk_root="$sdk_root" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ $variant == release ]]; then
    printf '%s\n' '주의: pbKeystore 등 서명 속성 4개가 없으면 release APK도 디버그 키로 서명됩니다. Play Store 업로드에 사용하지 마세요.'
fi
(cd "$repo_root" && ./gradlew "$task")
[[ -s $repo_root/$apk ]] || fail "빌드는 끝났지만 APK를 찾지 못했습니다: $repo_root/$apk"
printf '\nAPK 생성 완료: %s\n' "$repo_root/$apk"
ls -lh "$repo_root/$apk"
sha256sum "$repo_root/$apk"
