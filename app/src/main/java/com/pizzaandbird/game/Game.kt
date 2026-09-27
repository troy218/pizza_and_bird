package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF

/** 월드(논리 px) -> 가상 화면(px) 배율. 타일 16px 논리 = 32px 렌더 */
const val WORLD_SCALE = 2f

/**
 * 게임 전역 컨텍스트: 씬 관리, 가상 해상도(960x540) 스케일링, 페이드 전환.
 */
class Game(val context: Context) {

    val virtW = 960
    val virtH = 540

    val worldBitmap: Bitmap = Bitmap.createBitmap(virtW, virtH, Bitmap.Config.ARGB_8888)
    val worldCanvas = Canvas(worldBitmap)

    val state: GameState = SaveManager.load(context)
    val assets = Assets(context)
    val hud = Hud(this)
    val input = Input(this)

    var scene: Scene = TitleScene(this)
    var transition: Transition? = null

    var screenW = 0
    var screenH = 0
    var viewScale = 1f
    var viewOffX = 0f
    var viewOffY = 0f
    var time = 0f

    val density: Float = context.resources.displayMetrics.density

    fun onSurfaceChanged(w: Int, h: Int) {
        screenW = w
        screenH = h
        viewScale = minOf(w.toFloat() / virtW, h.toFloat() / virtH)
        viewOffX = (w - virtW * viewScale) / 2f
        viewOffY = (h - virtH * viewScale) / 2f
        hud.layout(w, h)
        scene.onLayout()
    }

    /** 화면 좌표 -> 월드 논리 좌표 (월드는 WORLD_SCALE배로 그려진다) */
    fun screenToWorld(p: PointF): PointF =
        PointF((p.x - viewOffX) / viewScale / WORLD_SCALE, (p.y - viewOffY) / viewScale / WORLD_SCALE)

    /** 짧은 햅틱 피드백 (버튼 누름 등) */
    @Suppress("DEPRECATION")
    fun haptic() {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(10L)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------------

    fun update(dt: Float) {
        time += dt
        input.process()
        val tr = transition
        if (tr != null) {
            tr.update(dt)
            if (tr.finished) transition = null
            input.endFrame()
            return
        }
        val ov = scene.overlay
        if (ov != null) {
            ov.handleInput(input)
            ov.update(dt)
        } else {
            scene.handleInput(input)
        }
        scene.update(dt)
        input.endFrame()
    }

    fun render(c: Canvas) {
        scene.drawWorld(worldCanvas)
        c.drawColor(0xFF2E2A3A.toInt())
        val dst = RectF(
            viewOffX, viewOffY,
            viewOffX + virtW * viewScale, viewOffY + virtH * viewScale
        )
        c.drawBitmap(worldBitmap, null, dst, assets.pxPaint)
        scene.drawHud(c)
        scene.overlay?.draw(c)
        transition?.draw(c, screenW.toFloat(), screenH.toFloat())
    }

    /** 페이드 전환 (액션은 화면이 완전히 어두워진 순간 실행) */
    fun fadeTo(action: () -> Unit) {
        if (transition == null) transition = Transition(action)
    }

    fun toast(msg: String) = hud.toast(msg)

    /** 지역 도착 배너 */
    fun banner(msg: String) = hud.banner(msg)

    /** 게임 종료 확인 */
    fun openExitConfirm() {
        val sc = scene
        if (sc.overlay != null) return
        sc.openOverlay(
            DialogOverlay(
                sc, "", "게임을 종료할까요?\n진행 상황은 자동으로 저장돼 있어요.",
                listOf(
                    DialogOverlay.Choice("종료하기") { _ ->
                        SaveManager.save(context, state)
                        (context as? MainActivity)?.runOnUiThread {
                            (context as? MainActivity)?.finish()
                        }
                    },
                    DialogOverlay.Choice("계속하기")
                ),
                disableKeys = true
            )
        )
    }
}

/** 검은 페이드 인/아웃 */
class Transition(private val action: () -> Unit) {
    private var t = 0f
    private var phase = 0
    private var doneFlag = false

    fun update(dt: Float) {
        t += dt / 0.3f
        if (t >= 1f) {
            if (phase == 0) {
                action()
                phase = 1
                t = 0f
            } else {
                doneFlag = true
            }
        }
    }

    val finished: Boolean get() = doneFlag

    fun draw(c: Canvas, w: Float, h: Float) {
        val a = (if (phase == 0) t else 1f - t).coerceIn(0f, 1f)
        fadePaint.color = Color.argb((255 * a).toInt(), 18, 14, 26)
        c.drawRect(0f, 0f, w, h, fadePaint)
    }

    companion object {
        private val fadePaint = Paint()
    }
}
