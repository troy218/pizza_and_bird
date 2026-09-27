pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PizzaAndBird"
include(":app")

// tools/preview — 그래픽 프리뷰 파이프라인 (개발 전용 모듈, APK 빌드에 포함되지 않음)
include(":tools-preview")
project(":tools-preview").projectDir = file("tools/preview")
