@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.os / android.view 스텁 (프리뷰 파이프라인 전용) */
package android.os

open class Vibrator {
    open fun vibrate(milliseconds: Long) {}
}

/**
 * 프리뷰용 Handler/HandlerThread — Audio.kt 가 컴파일되도록 최소 동작만 제공한다.
 * 실제 스레드를 만들지 않고 블록을 즉시 실행한다 (프리뷰는 소리를 내지 않는다).
 */
open class Looper

open class Handler(val looper: Looper? = null) {
    fun post(r: Runnable): Boolean { r.run(); return true }
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean { r.run(); return true }
    fun removeCallbacks(r: Runnable) {}
    fun removeCallbacksAndMessages(token: Any?) {}
}

open class HandlerThread(name: String) : java.lang.Thread(name) {
    val looper: Looper = Looper()
    fun quit() {}
    fun quitSafely() {}
}

object SystemClock {
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
}
