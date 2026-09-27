@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.os / android.view 스텁 (프리뷰 파이프라인 전용) */
package android.os

open class Vibrator {
    open fun vibrate(milliseconds: Long) {}
}

object SystemClock {
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
}

/** [P05] Audio.kt background loader stubs — preview runs posted tasks immediately. */
class Looper

open class HandlerThread(val threadName: String) {
    val looper: Looper = Looper()
    fun start() {}
    fun quitSafely() {}
}

class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper) {
    fun post(r: Runnable): Boolean { r.run(); return true }
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean { r.run(); return true }
    fun removeCallbacksAndMessages(token: Any?) {}
}
