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
        versionCode = 2
        versionName = "0.1.1-beta01"
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
