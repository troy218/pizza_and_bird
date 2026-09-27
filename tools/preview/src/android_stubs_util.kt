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

object Log {
    @JvmStatic
    fun v(tag: String, msg: String): Int = 0

    @JvmStatic
    fun d(tag: String, msg: String): Int = 0

    @JvmStatic
    fun i(tag: String, msg: String): Int = 0

    @JvmStatic
    fun w(tag: String, msg: String): Int = 0

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable?): Int = 0

    @JvmStatic
    fun e(tag: String, msg: String): Int = 0

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?): Int = 0
}

// ---------------------------------------------------------------------------
// LruCache (프리뷰: 접근순 LRU — 도감 사진/썸네일 캐시)
// ---------------------------------------------------------------------------

open class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    @Synchronized
    open fun get(key: K): V? = map[key]

    @Synchronized
    open fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    open fun remove(key: K): V? = map.remove(key)

    @Synchronized
    open fun evictAll() = map.clear()

    @Synchronized
    open fun size(): Int = map.size

    open fun maxSize(): Int = maxSize
}
