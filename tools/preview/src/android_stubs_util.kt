@file:Suppress("unused")

/**
 * tools/preview — android.util.Xml 스텁.
 */
package android.util

import org.xmlpull.v1.MiniXmlPullParser
import org.xmlpull.v1.XmlPullParser

object Xml {
    @JvmStatic
    fun newPullParser(): XmlPullParser = MiniXmlPullParser()
}

/**
 * [P05] android.util.Base64 스텁 — 실제 안드로이드와 같은 규칙으로 동작한다.
 * (URL_SAFE: '-'/'_' · NO_WRAP: 줄바꿈 없음 · NO_PADDING: '=' 없음)
 * 백업 코드가 프리뷰에서도 실제로 만들어지고 되돌아오게 하려는 것.
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    const val NO_PADDING = 1

    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URLT = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        val table = if (flags and URL_SAFE != 0) URLT else STD
        val pad = flags and NO_PADDING == 0
        val sb = StringBuilder()
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1
            sb.append(table[b0 ushr 2])
            sb.append(table[((b0 and 0x03) shl 4) or (if (b1 < 0) 0 else b1 ushr 4)])
            if (b1 < 0) {
                if (pad) sb.append('=')
            } else {
                sb.append(table[((b1 and 0x0F) shl 2) or (if (b2 < 0) 0 else b2 ushr 6)])
                if (b2 < 0) {
                    if (pad) sb.append('=')
                } else {
                    sb.append(table[b2 and 0x3F])
                }
            }
            i += 3
        }
        return sb.toString()
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val rev = HashMap<Char, Int>()
        for (t in listOf(STD, URLT)) for (i in t.indices) rev[t[i]] = i
        val out = ArrayList<Byte>()
        var buf = 0
        var bits = 0
        for (ch in str) {
            if (ch == '=' || ch == '\n' || ch == '\r') {
                if (ch == '=') break else continue
            }
            val v = rev[ch] ?: throw IllegalArgumentException("bad base-64")
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buf shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
