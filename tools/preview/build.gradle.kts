// tools/preview — 그래픽 프리뷰 파이프라인 (개발 전용)
//
// 게임 렌더링 코드(app/src/.../game, MainActivity/GameView 제외)를
// android.graphics 스텁(Java2D) 위에서 그대로 컴파일·실행해
// 모든 화면의 스크린샷을 렌더링한다. 자세한 설명은 tools/preview/README.md.
//
//   ./gradlew :tools-preview:renderPreview   # 로컬 렌더링 (JDK 17)
//   ./gradlew :tools-preview:ciRender        # CI 전용 (렌더링 + preview/ 커밋)

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 게임 소스 + 스텁을 함께 컴파일 (Android 전용 파일 2개 제외)
sourceSets.main {
    kotlin.srcDir("src")
    kotlin.srcDir(rootProject.projectDir.resolve("app/src/main/java/com/pizzaandbird/game"))
    kotlin.exclude("**/MainActivity.kt")
    kotlin.exclude("**/GameView.kt")
}

val fontsDir = layout.projectDirectory.dir("fonts")
val outDir = layout.projectDirectory.dir("out")

val downloadFonts = tasks.register("downloadPreviewFonts") {
    description = "프리뷰용 한글 폰트(NotoSansKR) 다운로드"
    outputs.dir(fontsDir)
    doLast {
        val files = mapOf(
            "NotoSansKR-Regular.ttf" to "https://notofonts.github.io/korean/fonts/NotoSansKR/hinted/ttf/NotoSansKR-Regular.ttf",
            "NotoSansKR-Bold.ttf" to "https://notofonts.github.io/korean/fonts/NotoSansKR/hinted/ttf/NotoSansKR-Bold.ttf"
        )
        for ((name, url) in files) {
            val f = java.io.File(fontsDir.asFile, name)
            if (f.exists() && f.length() > 10_000) continue
            f.parentFile.mkdirs()
            runCatching {
                java.net.URL(url).openStream().use { input ->
                    f.outputStream().use { output -> input.copyTo(output) }
                }
                println("font downloaded: $name (${f.length()} bytes)")
            }.onFailure {
                println("WARN: 폰트 다운로드 실패 ($name) — 미리보기 텍스트가 깨질 수 있음: $it")
            }
        }
    }
}

val renderPreview = tasks.register<JavaExec>("renderPreview") {
    group = "preview"
    description = "게임 렌더링 코드를 실행해 모든 화면의 스크린샷 생성"
    dependsOn(downloadFonts)
    mainClass.set("com.pizzaandbird.preview.PreviewMain")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    args = listOf(outDir.asFile.absolutePath)
    doFirst { outDir.asFile.mkdirs() }
}

val commitPreview = tasks.register<Exec>("commitPreview") {
    group = "preview"
    description = "렌더링된 스크린샷을 preview/ 폴더에 커밋·푸시 (CI 전용)"
    dependsOn(renderPreview)
    workingDir = rootProject.projectDir
    commandLine(
        "bash", "-c",
        """
        set -e
        git config user.name "arena-preview-bot"
        git config user.email "arena-preview-bot@users.noreply.github.com"
        mkdir -p preview
        rm -f preview/*.png
        cp tools/preview/out/*.png preview/
        git add preview
        if git diff --cached --quiet; then
          echo "no preview changes"
        else
          git commit -m "preview: update rendered screenshots [skip ci]"
          git push || echo "WARN: preview push failed (다음 push에서 다시 시도됨)"
        fi
        """.trimIndent()
    )
}

val ciRender = tasks.register("ciRender") {
    group = "preview"
    description = "CI 파이프라인: 폰트 다운로드 → 렌더링 → 커밋"
    dependsOn(commitPreview)
    onlyIf {
        System.getenv("GITHUB_ACTIONS") == "true" &&
                System.getenv("GITHUB_EVENT_NAME") == "push" &&
                System.getenv("GITHUB_REF") == "refs/heads/arena/01a0e0d9-pizza-and-bird"
    }
}
