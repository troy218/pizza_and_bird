@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")

/** tools/preview — android.os 스텁 (프리뷰 파이프라인 전용) */
package android.os

open class Vibrator {
    open fun vibrate(milliseconds: Long) {}
}

object SystemClock {
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
}

/** 오디오 백그라운드 스레드용 스텁 — 실행하지 않고 시그니처만 맞춘다 */
class Looper

open class Handler(val looper: Looper) {
    /**
     * 프리뷰에서는 아무 것도 실행하지 않는다 (오디오 로딩은 화면과 무관).
     * 실제 Android 는 Runnable 을 큐에 넣어 실행한다.
     */
    fun post(r: () -> Unit): Boolean = true

    fun postDelayed(r: () -> Unit, delayMillis: Long): Boolean = true

    fun removeCallbacksAndTokens(token: Any?) {}
}

open class HandlerThread(name: String) {
    val looper: Looper = Looper()
    fun start() {}
    fun quitSafely(): Boolean = true
}
