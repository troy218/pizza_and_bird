@file:Suppress("unused")

/** tools/preview — android.util.LruCache 스텁 (도감 사진 캐시용). */
package android.util

open class LruCache<K : Any, V : Any>(private val maxSize: Int) {

    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    open fun sizeOf(key: K, value: V): Int = 1

    open fun get(key: K): V? = map[key]

    open fun put(key: K, value: V): V? = map.put(key, value)

    open fun remove(key: K): V? = map.remove(key)

    open fun size(): Int = map.size

    open fun evictAll() {
        map.clear()
    }
}
