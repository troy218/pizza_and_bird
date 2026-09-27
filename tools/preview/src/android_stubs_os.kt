@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.os 스텁 (프리뷰 파이프라인 전용) */
package android.os

import java.util.concurrent.LinkedBlockingQueue

open class Vibrator {
    open fun vibrate(milliseconds: Long) {}
}

/** Looper — 기기와 동일하게 post 된 작업을 한 스레드에서 순서대로 실행한다. */
class Looper internal constructor(private val name: String) {
    private val queue = LinkedBlockingQueue<Runnable>()

    internal fun post(r: Runnable): Boolean = queue.offer(r)

    internal fun loop() {
        while (true) {
            val r = queue.take()
            r.run()
        }
    }

    fun quit() {
        // 프리뷰에서는 루퍼를 끝내지 않는다(게임 스레드가 기다릴 수 있음).
    }

    override fun toString(): String = "Looper($name)"
}

/** HandlerThread — 이름 붙은 전용 스레드 + Looper. */
class HandlerThread(name: String) {
    val looper: Looper = Looper(name)
    private val thread = Thread({ looper.loop() }, name).apply {
        isDaemon = true
        start()
    }

    fun start() = Unit
    fun quit() = Unit
    fun join(ms: Long) = thread.join(ms)
}

class Handler(looper: Looper) {
    private val looper = looper

    constructor() : this(Looper("preview-main"))

    fun post(r: Runnable): Boolean = looper.post(r)
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean = looper.post(r)
    fun removeCallbacksAndMessages(token: Any?) = Unit
}

/**
 * 프리뷰 전용 시계.
 *
 * 실제 기기의 벽시계로 두면 오버레이 등장 연출(enter/bornAt)이 "지금까지 실제로
 * 얼마나 걸렸나" 에 따라 달라져, 같은 소스를 돌려도 스크린샷이 매번 조금씩
 * 달라진다. 게임 시간(dt)만큼만 흐르는 시계로 두면 스크린샷이 결정적이 된다.
 */
object SystemClock {
    /** 시뮬레이션 시각(ms). PreviewMain.simulate() 이 dt 만큼 전진시킨다. */
    @JvmStatic var simMillis = 0L

    @JvmStatic fun advance(dt: Float) { simMillis += (dt * 1000f).toLong() }

    fun uptimeMillis(): Long = simMillis
    fun elapsedRealtime(): Long = simMillis
}
