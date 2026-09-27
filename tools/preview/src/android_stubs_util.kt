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
class LruCache<K : Any, V : Any>(private val maxSize: Int) {
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

    private fun trim() {
        val it = map.keys.iterator()
        while (map.size > maxSize && it.hasNext()) {
            it.next()
            it.remove()
        }
    }
}
