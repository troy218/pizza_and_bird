// Pizza and Bird : 피자와 새 — 루트 빌드 스크립트
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// ---------------------------------------------------------------------------
// 그래픽 프리뷰 파이프라인 (tools/preview)
//
// GitHub Actions에서 arena 세션 브랜치 push 시 APK 빌드(assembleDebug) 뒤에
// ciRenderPreview 가 실행된다: 게임 렌더링 코드를 kotlinc로 컴파일해 JVM에서
// 실행 → 모든 화면의 스크린샷을 preview/*.png 로 같은 브랜치에 커밋한다.
// 그래픽 작업을 "눈으로 보며" 반복하기 위한 개발 파이프라인.
// (로컬 빌드/다른 브랜치에서는 동작하지 않음 — tools/preview/README.md 참고)
// ---------------------------------------------------------------------------
val previewBranch = "refs/heads/arena/01a0e0d9-pizza-and-bird"
val inPreviewCi = System.getenv("GITHUB_ACTIONS") == "true" &&
        System.getenv("GITHUB_EVENT_NAME") == "push" &&
        System.getenv("GITHUB_REF") == previewBranch

if (inPreviewCi) {
    // 빌드 실패 원인을 Actions 로그 대신 preview/build.log 로 남긴다
    val failures = ArrayList<String>()
    gradle.taskExecutionListener(object : TaskExecutionListener {
        override fun beforeExecute(task: org.gradle.api.Task) {}
        override fun afterExecute(task: org.gradle.api.Task, state: TaskState) {
            state.failure?.let {
                failures.add(
                    "### 태스크 실패: ${task.path}\n$it\n${it.stackTraceToString().take(6000)}"
                )
            }
        }
    })
    gradle.buildFinished {
        if (failures.isNotEmpty()) {
            runCatching {
                val f = File(rootDir, "tools/preview/out/gradle_failures.log")
                f.parentFile.mkdirs()
                f.writeText(failures.joinToString("\n\n"))
            }
        }
    }

    tasks.register<Exec>("ciRenderPreview") {
        group = "preview"
        description = "그래픽 프리뷰 렌더링 + preview/ 커밋 (CI 전용)"
        workingDir = rootDir
        commandLine("bash", "tools/preview/ci_render.sh")
        isIgnoreExitValue = true
    }

    gradle.projectsEvaluated {
        project(":app").tasks.matching { it.name == "assembleDebug" }.configureEach {
            finalizedBy(":ciRenderPreview")
        }
    }
}
