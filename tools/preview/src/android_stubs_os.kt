@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.os / android.view 스텁 (프리뷰 파이프라인 전용) */
package android.os

open class Vibrator {
    open fun vibrate(milliseconds: Long) {}
}

object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long = System.currentTimeMillis()

    @JvmStatic
    fun uptimeMillis(): Long = System.currentTimeMillis()
}
