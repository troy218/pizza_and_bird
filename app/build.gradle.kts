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
        versionCode = 6
        versionName = "0.3.3-beta01"
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

// ===========================================================================
// TEMP(디버그용, 나중에 삭제): CI 빌드 실행 시 컴파일 오류를 ci-errors.txt로
// 만들어 이 브랜치에 커밋·푸시한다. (샌드박스에서 Actions 로그 다운로드가
// 막혀 있어 오류 내용을 확인할 수 없어서 사용하는 임시 우회로)
// ===========================================================================
gradle.buildFinished { buildResult ->
    try {
        if (buildResult.failure == null) return@buildFinished
        if (System.getenv("CI") != "true") return@buildFinished
        val ref = System.getenv("GITHUB_REF")?.removePrefix("refs/heads/") ?: return@buildFinished

        // 실패한 빌드를 플레인 콘솔로 재실행해 오류 텍스트를 확보한다
        val proc = ProcessBuilder("./gradlew", "assembleDebug", "--console=plain")
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.readAllBytes().toString()
        proc.waitFor()

        val lines = out.lines()
        val errs = lines.filter { it.startsWith("e: ") || it.contains("error:") }
        val what = lines.dropWhile { !it.contains("What went wrong") }.take(15)
        val report = buildString {
            append("CI compile errors @ ").append(System.getenv("GITHUB_SHA")?.take(7)).append("\n\n")
            if (errs.isNotEmpty()) append(errs.take(60).joinToString("\n")).append("\n\n")
            append(what.joinToString("\n")).append("\n")
        }
        java.io.File(rootDir.parentFile, "ci-errors.txt").also {
            // 루트 프로젝트 기준으로 저장소 최상단에 기록
        }
        java.io.File("ci-errors.txt").writeText(report)

        val pushCmd = "git config user.name ci-reporter && git config user.email ci@local && " +
            "git add ci-errors.txt && git commit -m 'CI: 컴파일 오류 리포트 [skip ci]' && " +
            "git push origin HEAD:refs/heads/$ref"
        ProcessBuilder("bash", "-c", pushCmd).inheritIO().start().waitFor()
    } catch (t: Throwable) {
        println("ci-error-report failed: $t")
    }
}
