plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pizzaandbird.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pizzaandbird.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.1-beta01"
    }

    // ---------------------------------------------------------------
    // 릴리스 서명: CI에서 -PpbKeystore=... 옵션으로 키스토어를 전달하면
    // 해당 키로 서명하고, 없으면 디버그 키로 서명된 release APK가 생성됨.
    // (베타 테스트용. 스토어 배포 전에는 전용 키스토어를 시크릿에 등록할 것)
    // ---------------------------------------------------------------
    val ksPath = providers.gradleProperty("pbKeystore").orNull
    val ksPass = providers.gradleProperty("pbStorePassword").orNull
    val ksAlias = providers.gradleProperty("pbKeyAlias").orNull
    val ksKeyPass = providers.gradleProperty("pbKeyPassword").orNull
    val hasReleaseSigning = ksPath != null && ksPass != null && ksAlias != null && ksKeyPass != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // 외부 의존성 없음 — 완전 오프라인 게임 (android.jar 기본 API만 사용)
}

// ---------------------------------------------------------------
// CI 진단: Kotlin 컴파일 오류(e: …)를 GitHub Actions annotation(::error::)으로도 출력한다.
// 워크플로 로그를 내려받지 못하는 환경(에이전트/모바일)에서도 실패 원인을 바로 볼 수 있게 함.
// 로컬 빌드에는 영향 없음 (GITHUB_ACTIONS 환경에서만 동작).
// ---------------------------------------------------------------
if (System.getenv("GITHUB_ACTIONS") == "true") {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        val pending = StringBuilder()
        var reported = 0
        logging.addStandardErrorListener(object : org.gradle.api.logging.StandardOutputListener {
            override fun onOutput(output: CharSequence) {
                pending.append(output)
                var nl = pending.indexOf("\n")
                while (nl >= 0) {
                    val line = pending.substring(0, nl).trimEnd()
                    pending.delete(0, nl + 1)
                    // "e: file:///…:줄:칸 메시지" 형태의 컴파일러 진단만 (데몬 재시작 안내 등은 제외)
                    if (line.startsWith("e: file:") && reported < 10) {
                        reported++
                        println("::error::$line")
                    }
                    nl = pending.indexOf("\n")
                }
            }
        })
    }
}
