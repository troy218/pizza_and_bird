package com.pizzaandbird.game

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * 게임 뷰: SurfaceView + 게임 스레드 (고정 가상 해상도 960x540).
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val game = Game(context)

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
                } catch (_: Exception) {
                    // 프레임 스킵
                } finally {
                    if (canvas != null) {
                        try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                    }
                }
            }
            // 60fps 패이싱
            val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000f
            if (elapsedMs < 15.5f) {
                try {
                    Thread.sleep((16f - elapsedMs).toLong().coerceAtLeast(0L))
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }
}
