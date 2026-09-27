@file:Suppress("unused")

/** tools/backup_test — android.util 최소 스텁 (BirdPhotography의 LruCache 등). */
package android.util

open class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    @Synchronized open fun sizeOf(key: K, value: V): Int = 1
    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V): V? = map.put(key, value)
    @Synchronized fun remove(key: K): V? = map.remove(key)
    fun evictAll() { map.clear() }
}

object Log {
    fun w(tag: String, msg: String): Int = 0
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = 0
    fun i(tag: String, msg: String): Int = 0
    fun d(tag: String, msg: String): Int = 0
}
