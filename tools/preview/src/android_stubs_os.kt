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

/** [P05] Audio.kt 가 쓰는 백그라운드 로더 스레드 스텁 — 프리뷰에서는 같은 스레드에서 즉시 실행. */
class Looper

open class HandlerThread(val threadName: String) {
    val looper: Looper = Looper()
    fun start() {}
    fun quitSafely() {}
}

class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper) {
    fun post(r: Runnable): Boolean {
        r.run()
        return true
    }

    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        r.run()
        return true
    }

    fun removeCallbacksAndMessages(token: Any?) {}
}
