@file:Suppress("unused")

/** tools/preview — android.content.res.AssetManager 스텁 (게임 저장소의 assets/ 폴더를 그대로 읽는다) */
package android.content.res

class AssetManager private constructor() {
    companion object {
        private val ASSET_ROOT = java.io.File("app/src/main/assets")
        val INSTANCE = AssetManager()
    }

    fun open(fileName: String): java.io.InputStream = java.io.File(ASSET_ROOT, fileName).inputStream()
}
