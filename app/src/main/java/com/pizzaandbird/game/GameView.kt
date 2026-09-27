package com.pizzaandbird.game

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * 게임 뷰: SurfaceView + 게임 스레드 (고정 가상 해상도 960x540).
 *
 * [game]은 백그라운드 부팅 스레드에서 미리 만들어 넘겨준다
 * (MainActivity가 스플래시를 띄운 뒤 비동기로 초기화한다).
 */
class GameView(context: Context, val game: Game) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var surfaceReady = false

    @Volatile
    private var resumed = false

    init {
        holder.addCallback(this)
    }

    fun onResume() {
        resumed = true
        startThread()
        game.audio.onResume()
    }

    fun onPause() {
        resumed = false
        stopThread()
        game.input.releaseHeld()
        game.audio.onPause()
        SaveManager.save(context, game.state)
    }

    // ---------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // SurfaceView may briefly report zero dimensions during freeform/split-screen
        // transitions. Game keeps the previous valid viewport until the next callback.
        if (width > 0 && height > 0) game.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopThread()
    }

    private fun startThread() {
        if (running || !surfaceReady || !resumed) return
        running = true
        thread = Thread(this, "PizzaAndBirdLoop").also { it.start() }
    }

    private fun stopThread() {
        running = false
        thread?.let {
            try { it.join(2500) } catch (_: InterruptedException) { }
        }
        thread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        game.input.onTouchEvent(event)
        return true
    }

    // ---------------------------------------------------------------------
    // 게임 루프 — 60Hz 고정 그리드 페이싱
    // ---------------------------------------------------------------------

    override fun run() {
        var lastErrorLog = 0L
        var last = System.nanoTime()
        // 프레임 목표 시각을 '절대 그리드'에 박아 둔다. 프레임마다 처음부터
        // 16.7ms를 세는 옛 방식은 sleep 오차가 그대로 프레임 길이가 되어
        // 60Hz 위상에서 어긋나 들쭉날쭉했다. 그리드 방식은 한 프레임이 밀린 만큼
        // 다음 프레임 대기 시간이 줄어들어 오차가 누적되지 않고 제자리로 돌아온다.
        var nextFrame = System.nanoTime()
        while (running) {
            if (holder.surface.isValid) {
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        val now = System.nanoTime()
                        var dt = (now - last) / 1_000_000_000f
                        last = now
                        if (dt > 0.05f) dt = 0.05f
                        if (dt < 0f) dt = 0.016f
                        game.update(dt)
                        game.render(canvas)
                    }
                } catch (e: Exception) {
                    // 반복 오류는 로그 폭주를 막되, 원인은 숨기지 않는다.
                    val now = System.nanoTime()
                    if (now - lastErrorLog > 5_000_000_000L) {
                        Log.e("PizzaAndBird", "게임 프레임 처리 실패", e)
                        lastErrorLog = now
                    }
                } finally {
                    if (canvas != null) {
                        try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                    }
                }
            }
            nextFrame += FRAME_PERIOD_NANOS
            val now = System.nanoTime()
            if (nextFrame > now) {
                if (!waitUntil(nextFrame)) return        // 인터럽트 → 스레드 종료
            } else if (now - nextFrame > FRAME_PERIOD_NANOS) {
                // 한 프레임 분량 이상 밀린 상황(로딩 직후·GC 정지 등)에서는
                // 그리드를 다시 맞춘다 — 벌어진 시간을 쫓아가려다 연속 드롭을 만들지 않는다.
                nextFrame = now
            }
        }
    }

    /**
     * until 시각까지 정밀하게 기다린다.
     *
     * Thread.sleep은 밀리초 단위라 남은 시간을 통째로 맡기면 1~2ms씩 자꾸 넘어
     * 60Hz에서 어긋난다. 그래서 대부분은 sleep으로 저부하로 보내고,
     * 마지막 0.6ms만 스핀 대기로 정확히 지킨다(프레임당 최대 코어의 ~4% 수준으로 짧다).
     *
     * @return false = 인터럽트로 중단됨
     */
    private fun waitUntil(until: Long): Boolean {
        val spinNanos = 600_000L                       // 마지막 0.6ms만 스핀
        val remaining = until - System.nanoTime()
        if (remaining > spinNanos + 1_000_000L) {      // 졸다가 넘어가는 일이 없게 1ms 이상일 때만 sleep
            try {
                val coarse = remaining - spinNanos
                Thread.sleep(coarse / 1_000_000L, (coarse % 1_000_000L).toInt())
            } catch (_: InterruptedException) {
                return false
            }
        }
        while (running && System.nanoTime() < until) {
            // 스핀 꼬리 — 일시정지 되면 즉시 빠져나온다.
        }
        return true
    }

    companion object {
        /** 프레임 간격 — 60Hz (16,666,666ns) */
        private const val FRAME_PERIOD_NANOS = 16_666_666L
    }
}
