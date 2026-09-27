@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — 스텁 보완 (android.util).
 *
 * main 병합 코드가 새로 쓰기 시작한 API 중 프리뷰 스텁에 없던 것들.
 * (Assets.kt의 LruCache 조류 사진 캐시, Audio.kt의 Log)
 * 헤드리스 프리뷰/스모크 컴파일용 최소 구현이다.
 */
package android.util

open class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    open fun sizeOf(key: K, value: V): Int = 1
    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V): V? = map.put(key, value)
    @Synchronized fun remove(key: K): V? = map.remove(key)
    @Synchronized fun evictAll() { map.clear() }
}

object Log {
    fun w(tag: String, msg: String): Int = 0
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = 0
    fun i(tag: String, msg: String): Int = 0
    fun d(tag: String, msg: String): Int = 0
}
