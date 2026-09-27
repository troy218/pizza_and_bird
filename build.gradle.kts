// Pizza and Bird : 피자와 새 — 루트 빌드 스크립트
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// ────────────────────────────────────────────────────────────────────────────
// TEMP(삭제 예정): 빌드 실패 원인을 브랜치로 되돌려보내는 임시 훅.
// 이 샌드박스에서는 Actions 로그 서버에 접근할 수 없어서 쓰는 임시 코드.
// ────────────────────────────────────────────────────────────────────────────

fun run(vararg cmd: String): Int = try {
    val p = ProcessBuilder(*cmd).directory(rootDir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    p.waitFor()
    if (out.isNotBlank()) println("[t] ${cmd.joinToString(" ")} -> ${out.take(400)}")
    p.exitValue()
} catch (t: Throwable) {
    println("[t] ${cmd.joinToString(" ")} 예외: $t")
    -1
}

fun report(name: String, body: String) {
    java.io.File(rootDir, name).writeText(body)
    run("git", "add", name)
    run("git", "-c", "user.name=ci-probe", "-c", "user.email=ci-probe@local", "commit", "-m", "ci: 진단 로그 [skip ci]")
    run("git", "push", "origin", "HEAD:arena/01a0e10c-pizza-and-bird")
}

report("ci-probe.txt", "스크립트 평가됨: ${System.currentTimeMillis()}")

gradle.buildFinished { result ->
    val ex = result.failure
    if (ex == null) return@buildFinished
    val sb = StringBuilder()
    val queue = ArrayDeque<Throwable>()
    queue.add(ex)
    var guard = 0
    while (queue.isNotEmpty() && guard++ < 40) {
        val e = queue.removeFirst()
        val m = e.message
        if (!m.isNullOrBlank() && !sb.contains(m.take(160))) {
            sb.appendLine("== ${e.javaClass.name} ==")
            sb.appendLine(m.take(6000))
        }
        e.cause?.let { queue.add(it) }
    }
    report("build-failure.txt", sb.toString().ifBlank { ex.stackTraceToString() })
}
