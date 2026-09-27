@file:Suppress("unused")

/**
 * tools/preview — android.content.res 스텁 (에셋 로딩).
 */
package android.content.res

/** 프리뷰용 에셋 매니저 — 실제 앱의 app/src/main/assets 를 연다. */
open class AssetManager {
    fun open(fileName: String): java.io.InputStream {
        val candidates = listOf(
            java.io.File("app/src/main/assets/$fileName"),
            java.io.File("src/main/assets/$fileName"),
            java.io.File(fileName)
        )
        for (f in candidates) if (f.isFile) return java.io.FileInputStream(f)
        throw java.io.FileNotFoundException("프리뷰 에셋 없음: $fileName")
    }

    fun list(fileName: String): Array<String>? {
        val dirs = listOf(
            java.io.File("app/src/main/assets/$fileName"),
            java.io.File("src/main/assets/$fileName"),
            java.io.File(fileName)
        )
        for (d in dirs) if (d.isDirectory) return d.list() ?: emptyArray()
        return emptyArray()
    }
}
