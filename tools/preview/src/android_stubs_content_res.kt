@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — android.content.res 스텁.
 * 프리뷰 파이프라인 전용이며 Android 빌드(app/)에는 포함되지 않는다.
 */
package android.content.res

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** 에셋 매니저 스텁 — 리포지터리의 app/src/main/assets 폴더를 직접 읽는다. */
class AssetManager {
    fun open(path: String): InputStream {
        val f = File("app/src/main/assets/$path")
        return if (f.exists()) FileInputStream(f) else ByteArrayInputStream(ByteArray(0))
    }

    fun list(path: String): Array<String>? {
        val dir = File("app/src/main/assets/$path")
        return if (dir.isDirectory) dir.list() else null
    }
}
