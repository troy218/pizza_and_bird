@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — 스텁 보완 (android.os).
 *
 * Audio.kt가 오디오 로딩을 HandlerThread로 옮기면서 필요해진 최소 스텁.
 * 헤드리스에서는 post가 즉시 실행된다 (백그라운드 스레드 없음).
 */
package android.os

open class Looper {
    companion object {
        val myLooper: Looper = Looper()
        fun getMainLooper(): Looper = Looper()
    }
}

class HandlerThread(name: String?) : Thread(name) {
    private val looperField = Looper()
    val looper: Looper get() = looperField
    override fun run() { /* 헤드리스: 실제 루프 없음 */ }
    fun quitSafely(): Boolean = true
    fun quit(): Boolean = true
}

class Handler(looper: Looper? = null) {
    fun post(r: Runnable): Boolean { r.run(); return true }
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean { r.run(); return true }
    fun removeCallbacks(r: Runnable) {}
}
