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

// ---------------------------------------------------------------------------
// 그래픽 프리뷰 파이프라인 (tools/preview) — CI 자동 실행 훅
//
// GitHub Actions에서 arena 세션 브랜치 push 시, 빌드가 끝나면(성공/실패 무관)
// tools/preview/ci_render.sh 를 실행한다:
//   게임 렌더링 코드를 kotlinc로 컴파일해 JVM(Java2D 스텁)에서 실행 →
//   모든 화면의 스크린샷을 preview/*.png 로 같은 브랜치에 커밋.
// 그래픽 작업을 "눈으로 보며" 반복하기 위한 개발 파이프라인이다.
// 빌드가 실패한 경우 그 원인도 preview/build.log 에 기록된다.
// (로컬/다른 브랜치에서는 동작하지 않음 — tools/preview/README.md 참고)
// ---------------------------------------------------------------------------
val previewBranchRef = "refs/heads/arena/01a0e0d9-pizza-and-bird"
val inPreviewCi = System.getenv("GITHUB_ACTIONS") == "true" &&
        System.getenv("GITHUB_EVENT_NAME") == "push" &&
        System.getenv("GITHUB_REF") == previewBranchRef

if (inPreviewCi) {
    gradle.buildFinished { result ->
        try {
            val outDir = java.io.File(rootDir, "tools/preview/out")
            outDir.mkdirs()
            val sb = StringBuilder()
            sb.append("# gradle build finished: ${if (result.failure != null) "FAILED" else "OK"}\n")
            result.failure?.let { f ->
                sb.append("----- build failure -----\n").append(f.toString())
                    .append("\n").append(f.stackTraceToString().take(8000)).append('\n')
            }
            java.io.File(outDir, "gradle_result.log").writeText(sb.toString())

            val proc = ProcessBuilder("bash", "tools/preview/ci_render.sh")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.readBytes()
            proc.waitFor()
        } catch (_: Throwable) {
            // 프리뷰 파이프라인은 빌드에 영향을 주면 안 된다
        }
    }
}
