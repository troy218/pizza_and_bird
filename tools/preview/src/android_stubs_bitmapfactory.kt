@file:Suppress("unused")

/**
 * tools/preview — android.graphics.BitmapFactory / android.util.LruCache 스텁.
 *
 * 조류 대도감 사진(`assets/birds/` 안 jpg)을 프리뷰에서도 실제로 읽어 들이려고
 * Java2D(ImageIO)로 디코딩한다. `use { }`(kotlin.io Closeable 확장)도 그대로 동작한다.
 */
package android.graphics

import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

class BitmapFactory {
    class Options {
        var inSampleSize: Int = 1
        var inJustDecodeBounds: Boolean = false
        var outWidth: Int = 0
        var outHeight: Int = 0
    }

    companion object {
        @JvmStatic
        fun decodeStream(stream: InputStream): Bitmap? = decodeStream(stream, null, null)

        @JvmStatic
        fun decodeStream(stream: InputStream, outPadding: android.graphics.Rect?, opts: BitmapFactory.Options?): Bitmap? =
            try {
                val img = ImageIO.read(stream) ?: return null
                val scaled = scale(img, opts?.inSampleSize ?: 1)
                opts?.let {
                    it.outWidth = scaled.width
                    it.outHeight = scaled.height
                }
                if (opts?.inJustDecodeBounds == true) null else Bitmap.fromImage(scaled)
            } catch (_: Exception) {
                null
            }

        @JvmStatic
        fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
            decodeByteArray(data, offset, length, null)

        @JvmStatic
        fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: BitmapFactory.Options?): Bitmap? =
            try {
                decodeStream(java.io.ByteArrayInputStream(data, offset, length), null, opts)
            } catch (_: Exception) {
                null
            }

        @JvmStatic
        fun decodeFile(path: String, opts: BitmapFactory.Options? = null): Bitmap? =
            try {
                java.io.FileInputStream(path).use { decodeStream(it, null, opts) }
            } catch (_: Exception) {
                null
            }

        private fun scale(img: BufferedImage, sample: Int): BufferedImage {
            if (sample <= 1) return img
            val w = (img.width / sample).coerceAtLeast(1)
            val h = (img.height / sample).coerceAtLeast(1)
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.drawImage(img, 0, 0, w, h, null)
            g.dispose()
            return out
        }
    }
}
