// Pizza and Bird : 피자와 새 — 루트 빌드 스크립트
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
}

// ---------------------------------------------------------------------------
// 그래픽 프리뷰 파이프라인 (tools/preview)
//
// GitHub Actions의 APK 빌드(arena 세션 브랜치 push)가 끝나면
// 실제 게임 렌더링 코드를 JVM에서 실행해 preview/*.png 스크린샷을
// 같은 브랜치에 자동 커밋한다. 그래픽 작업을 눈으로 보며 반복하기 위한 것.
// (로컬/다른 브랜치 빌드에서는 동작하지 않음)
// ---------------------------------------------------------------------------
val previewBranch = "refs/heads/arena/01a0e0d9-pizza-and-bird"
val inPreviewCi = System.getenv("GITHUB_ACTIONS") == "true" &&
        System.getenv("GITHUB_EVENT_NAME") == "push" &&
        System.getenv("GITHUB_REF") == previewBranch

if (inPreviewCi) {
    gradle.projectsEvaluated {
        project(":app").tasks.matching { it.name == "assembleDebug" }.configureEach {
            finalizedBy(":tools-preview:ciRender")
        }
    }
}
