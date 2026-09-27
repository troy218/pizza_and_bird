@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — 스텁 보완 (android.graphics.BitmapFactory).
 *
 * Assets.kt의 조류 대도감 실사 사진 로딩(decodeStream/decodeFile)이 컴파일되도록 하는 스텁.
 * 헤드리스 프리뷰에서는 디코딩하지 않고 null을 돌려준다 — Assets.birdPhoto()의
 * null 처리 분기가 그대로 검증된다.
 */
package android.graphics

object BitmapFactory {
    /** 실제 Android BitmapFactory.Options와 동일한 최소 필드만 제공한다. */
    class Options {
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config? = null
    }

    fun decodeStream(stream: java.io.InputStream): Bitmap? = null
    fun decodeStream(stream: java.io.InputStream, outPadding: Rect?, opts: Options?): Bitmap? = null
    fun decodeFile(path: String): Bitmap? = null
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = null
}
