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
        game.onSurfaceChanged(width, height)
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
    // 게임 루프
    // ---------------------------------------------------------------------

    override fun run() {
        var lastErrorLog = 0L
        var last = System.nanoTime()
        while (running) {
            val frameStart = System.nanoTime()
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
            // 프레임 목표를 정확히 60Hz로 맞춘다. 15~16ms 경계에서 0ms sleep으로
            // 바쁜 대기 루프가 되는 것을 막아 CPU 점유와 발열을 낮춘다.
            val remainingNanos = 16_666_667L - (System.nanoTime() - frameStart)
            if (remainingNanos > 0L) {
                try {
                    Thread.sleep(remainingNanos / 1_000_000L, (remainingNanos % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }
}
