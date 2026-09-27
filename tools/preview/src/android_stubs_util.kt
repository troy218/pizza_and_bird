@file:Suppress("unused")

/**
 * tools/preview — android.util.Xml / LruCache 스텁.
 */
package android.util

import org.xmlpull.v1.MiniXmlPullParser
import org.xmlpull.v1.XmlPullParser

object Log {
    @JvmStatic fun v(tag: String, msg: String): Int { println("V/$tag: $msg"); return 0 }
    @JvmStatic fun d(tag: String, msg: String): Int { println("D/$tag: $msg"); return 0 }
    @JvmStatic fun i(tag: String, msg: String): Int { println("I/$tag: $msg"); return 0 }
    @JvmStatic fun w(tag: String, msg: String): Int { println("W/$tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String): Int { println("E/$tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int { println("E/$tag: $msg"); return 0 }
}

object Xml {
    @JvmStatic
    fun newPullParser(): XmlPullParser = MiniXmlPullParser()
}

/** android.util.LruCache 스텁 (기기와 동일하게 모든 메서드가 동기화된다). */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val prev = map.put(key, value)
        trim()
        return prev
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun maxSize(): Int = maxSize

    /** 크기 기준(바이트) 캐시 — 하위 클래스가 규칙을 정한다. 기본은 항목 수. */
    open fun sizeOf(key: K, value: V): Int = 1

    @Synchronized
    fun evictAll() = map.clear()

    private fun trim() {
        val it = map.keys.iterator()
        while (map.size > maxSize && it.hasNext()) {
            it.next()
            it.remove()
        }
    }
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
