// [P05] JVM 검증용 android.util.Base64 스텁 — 실제 안드로이드 구현과 같은 규칙
// (URL_SAFE: '-'/'_' · NO_WRAP: 줄바꿈 없음 · NO_PADDING: '=' 없음).
package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    const val NO_PADDING = 1

    private val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encodeToString(input: ByteArray, flags: Int): String {
        val table = if (flags and URL_SAFE != 0) URL else STD
        val pad = flags and NO_PADDING == 0
        val sb = StringBuilder()
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1
            sb.append(table[b0 ushr 2])
            sb.append(table[((b0 and 0x03) shl 4) or (if (b1 < 0) 0 else b1 ushr 4)])
            if (b1 < 0) { if (pad) sb.append('=') } else {
                sb.append(table[((b1 and 0x0F) shl 2) or (if (b2 < 0) 0 else b2 ushr 6)])
                if (b2 < 0) { if (pad) sb.append('=') } else sb.append(table[b2 and 0x3F])
            }
            i += 3
        }
        return sb.toString()
    }

    fun decode(str: String, flags: Int): ByteArray {
        val clean = str.replace("\n", "").replace("\r", "")
        val rev = HashMap<Char, Int>()
        for (t in listOf(STD, URL)) for (i in t.indices) rev[t[i]] = i
        val out = ArrayList<Byte>()
        var buf = 0
        var bits = 0
        for (ch in clean) {
            if (ch == '=') break
            val v = rev[ch] ?: throw IllegalArgumentException("bad base-64: '$ch'")
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
