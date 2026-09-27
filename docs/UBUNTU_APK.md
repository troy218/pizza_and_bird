# 내 Ubuntu 서버에서 APK 직접 만들기

이 저장소는 네이티브 Android Gradle 프로젝트입니다. **GitHub Actions, 배포 서비스, 배포 토큰 없이** 서버에서 APK를 만들 수 있습니다. 첫 빌드에는 Android SDK와 Gradle 의존성 다운로드를 위한 인터넷 접속이 필요합니다.

## 1. 서버 준비 (최초 1회)

Ubuntu 22.04/24.04 **x86_64(amd64)**, JDK 17, 여유 메모리 약 4 GB와 디스크 여유 6 GB 이상을 권장합니다. SDK/Gradle 캐시는 서버의 사용자 홈 디렉터리에 남아 다음 빌드에 재사용됩니다.

```bash
sudo apt-get update
sudo apt-get install -y git rsync curl unzip ca-certificates openjdk-17-jdk
java -version       # 17.x 확인
uname -m            # x86_64 확인
```

## 2. 소스 받기 → 빌드

이 저장소는 **비공개**입니다. 서버에서 GitHub에 로그인하지 않아도 되도록, **PC에 이미 있는 소스를 SSH로 복사**하는 방법을 먼저 안내합니다. PC에 `rsync`가 있어야 합니다 (Windows는 WSL에서 실행 가능). 서버 SSH 접속 정보는 본인 것으로 바꾸세요.

```bash
# PC: 이미 체크아웃한 pizza_and_bird 저장소 안에서 실행
SERVER=ubuntu@your-server.example.com
ssh "$SERVER" 'mkdir -p ~/pizza_and_bird'
rsync -az --exclude='.git/' --exclude='.gradle/' --exclude='build/' \
  --exclude='local.properties' --exclude='*.keystore' --exclude='*.jks' --exclude='*.p12' \
  ./ "$SERVER:pizza_and_bird/"

# 다음은 서버에 SSH로 접속해 실행
cd ~/pizza_and_bird
./tools/build_apk_ubuntu.sh            # 기본: 테스트용 debug APK
```

서버에 **이미 GitHub SSH 접근 권한이 설정돼 있다면**, 복사 대신 `git clone git@github.com:troy218/pizza_and_bird.git`으로 받을 수도 있습니다. 현재 작업 브랜치를 빌드하려면 그 브랜치가 원격에 반영된 후 서버에서 `git switch <브랜치명>` 하세요. GitHub 인증은 **비공개 소스를 내려받을 때만** 필요하며 APK 빌드에는 필요하지 않습니다.

스크립트는 실행 중인 사용자의 `${ANDROID_HOME:-$HOME/Android/Sdk}`에 Android 명령줄 도구를 설치(없을 때만), SHA-256 검사, SDK 라이선스 동의 요청, API 35/Build Tools 35.0.0 설치 후 저장소의 **Gradle wrapper 8.10.2**로 빌드합니다. 명령줄 도구는 [Android 공식 다운로드 페이지](https://developer.android.com/studio#command-tools)의 Linux 패키지와 SHA-256을 사용합니다. **`sudo`로 빌드 스크립트를 실행하지 마세요** (SDK/Gradle 캐시 권한 문제가 생깁니다). 이미 SDK가 있다면 `ANDROID_HOME=/절대/경로 ./tools/build_apk_ubuntu.sh`로 지정할 수 있습니다.

APK 결과: `app/build/outputs/apk/debug/app-debug.apk` (설치 가능, 디버그 키로 서명됨). 서명/빌드 결과를 확인하려면:

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
"${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/35.0.0/apksigner" verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

새 코드를 빌드할 때는 소스를 가져온 방법에 따라 **PC에서 위 `rsync`를 다시 실행**하거나, GitHub SSH로 clone했다면 서버에서 `git pull --ff-only` 한 후 `./tools/build_apk_ubuntu.sh`를 재실행하세요. `rsync`로 복사한 폴더에는 `.git`이 없으므로 `git pull`이 동작하지 않습니다.

## 3. APK 가져오기·설치

**서버가 아니라 본인 PC 터미널에서** 서버의 계정·경로에 맞게 실행합니다. APK를 웹에 공개하지 않고 SSH로 복사합니다.

```bash
SERVER=ubuntu@your-server.example.com  # 자신의 SSH 접속 정보로 교체
scp "$SERVER:pizza_and_bird/app/build/outputs/apk/debug/app-debug.apk" ./app-debug.apk
# Android SDK Platform Tools가 PC에 설치돼 있고 USB 디버깅을 켰다면:
adb install -r ./app-debug.apk
```

휴대폰으로 파일을 복사해 직접 설치해도 됩니다. 이때 휴대폰에서 해당 파일 관리 앱의 '알 수 없는 앱 설치' 허용이 필요할 수 있습니다. Android 7.0(API 24) 이상 지원. 서버에서 만든 디버그 키(`~/.android/debug.keystore`)가 바뀌면 기존 설치본 위에 업데이트가 되지 않을 수 있으니 테스트 APK라도 같은 기기에서 계속 업데이트할 계획이면 키를 안전하게 백업하세요. 서명이 달라져 삭제 후 재설치할 때 앱 데이터가 지워질 수 있습니다.

## 정식 서명이 필요한 경우 (선택)

```bash
./tools/build_apk_ubuntu.sh release
# 결과: app/build/outputs/apk/release/app-release.apk
```

**주의: 키스토어 설정이 없으면 release APK도 디버그 키로 서명됩니다.** Play Console 제출용이 아닙니다. 정식 배포에 사용할 키가 이미 있다면 그 키를 사용하고, 키스토어 파일과 비밀번호를 **저장소 밖**에 보관하세요. 새 키가 필요하면 서버에서 다음을 실행하세요.

```bash
mkdir -p "$HOME/.local/share/pizza-and-bird"
chmod 700 "$HOME/.local/share/pizza-and-bird"
keytool -genkeypair -keystore "$HOME/.local/share/pizza-and-bird/upload.keystore" \
  -alias pizzaandbird -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$HOME/.local/share/pizza-and-bird/upload.keystore"
```

`~/.gradle/gradle.properties`에 다음 네 속성을 설정하면 기존 `app/build.gradle.kts`가 이를 사용합니다 (**pbKeystore에는 `$HOME`이나 `~` 대신 실제 절대 경로**를 넣고, 이 파일은 커밋하지 마세요).

```properties
pbKeystore=/home/ubuntu/.local/share/pizza-and-bird/upload.keystore
pbStorePassword=<키스토어 비밀번호>
pbKeyAlias=pizzaandbird
pbKeyPassword=<키 비밀번호>
```

```bash
mkdir -p ~/.gradle
chmod 700 ~/.gradle
touch ~/.gradle/gradle.properties
chmod 600 ~/.gradle/gradle.properties
nano ~/.gradle/gradle.properties   # 위 4개 속성을 실제 값으로 입력
```

키스토어도 안전한 곳에 별도 백업하세요. 비밀번호를 명령행 `-P`에 적으면 셸 기록/프로세스 목록에 노출될 수 있습니다. 아래 명령으로 APK 인증서 지문을 확인하고 업로드 키 지문과 비교하세요.

```bash
"${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/35.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore "$HOME/.local/share/pizza-and-bird/upload.keystore" -alias pizzaandbird
```

기본 버전은 `versionCode=9`, `versionName=0.4.2-beta01`입니다. 새 APK로 기존 설치본을 업데이트할 때는 **같은 서명 키**와 이전보다 높은 `versionCode`가 필요합니다. 예를 들어 SDK 설치가 끝난 다음 서버에서 아래처럼 버전을 올려 직접 빌드할 수 있습니다 (비밀번호는 위 `~/.gradle/gradle.properties`에서 읽습니다).

```bash
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ./gradlew assembleRelease \
  -PVERSION_CODE=10 -PVERSION_NAME=0.4.2-beta02
```

스토어 배포 절차는 [RELEASE.md](RELEASE.md)를 참고하세요.

## 오류가 나면

- `SDK location not found`: `ANDROID_HOME`을 SDK 절대 경로로 지정하고 `local.properties`가 다른 SDK를 가리키지 않는지 확인하세요.
- `License ... not accepted`: 스크립트를 다시 실행해 SDK 라이선스에 동의하세요. 직접 확인하려면 `$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses`.
- Java/Gradle 오류: `java -version`, `echo "$JAVA_HOME"`로 JDK 17 사용 여부를 확인하세요.
- SDK·Gradle 다운로드 오류: 서버에서 `dl.google.com`, `services.gradle.org`, `maven.google.com`, `repo.maven.apache.org`로 HTTPS 접속이 되는지 확인하세요. **오프라인 빌드는 첫 다운로드 후 캐시가 채워져야만 가능**합니다.
- 권한 오류: `sudo ./gradlew` / `sudo ./tools/build_apk_ubuntu.sh`를 사용하지 마세요. 이미 root로 실행했다면 생성된 SDK/Gradle 캐시 소유자를 확인하세요.
