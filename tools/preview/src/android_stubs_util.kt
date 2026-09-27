@file:Suppress("unused")

/**
 * tools/preview — android.util 스텁 (Xml · Log · LruCache).
 */
package android.util

import org.xmlpull.v1.MiniXmlPullParser
import org.xmlpull.v1.XmlPullParser

object Xml {
    @JvmStatic
    fun newPullParser(): XmlPullParser = MiniXmlPullParser()
}

/** 프리뷰에서는 콘솔로만 흘려보낸다 */
object Log {
    @JvmStatic fun d(tag: String, msg: String): Int = println("D/$tag: $msg").let { 0 }
    @JvmStatic fun i(tag: String, msg: String): Int = println("I/$tag: $msg").let { 0 }
    @JvmStatic fun w(tag: String, msg: String): Int = println("W/$tag: $msg").let { 0 }
    @JvmStatic fun e(tag: String, msg: String): Int = println("E/$tag: $msg").let { 0 }
    @JvmStatic fun d(tag: String, msg: String, tr: Throwable?): Int = d(tag, msg)
    @JvmStatic fun i(tag: String, msg: String, tr: Throwable?): Int = i(tag, msg)
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable?): Int = w(tag, msg)
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int = e(tag, msg)
}

/** 도감 사진/썸네일 캐시용 최소 구현 (LRU 동작은 프리뷰에서 중요하지 않다) */
open class LruCache<K, V>(maxSize: Int) {
    private val map = LinkedHashMap<K, V>()

    operator fun get(key: K): V? = map[key]

    fun put(key: K, value: V): V? = map.put(key, value)

    fun remove(key: K): V? = map.remove(key)

    fun evictAll() = map.clear()

    fun size(): Int = map.size

    fun resize(maxSize: Int) {}
}
