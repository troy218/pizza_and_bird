package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF

/** 월드(논리 px) -> 가상 화면(px) 배율. 타일 16px 논리 = 32px 렌더 */
const val WORLD_SCALE = 2f

/** 오버레이(메뉴/대화) 등장 연출 시간(초) */
private const val OVERLAY_ENTER_SEC = 0.16f

/**
 * 게임 전역 컨텍스트: 씬 관리, 가상 해상도(960x540) 스케일링, 페이드 전환.
 */
class Game(val context: Context) {

    val virtW = 960
    val virtH = 540

    val worldBitmap: Bitmap = Bitmap.createBitmap(virtW, virtH, Bitmap.Config.ARGB_8888)
    val worldCanvas = Canvas(worldBitmap)

    val state: GameState = SaveManager.load(context)
    val assets = Assets()
    val audio = Audio(context).apply {
        musicOn = state.musicOn
        sfxOn = state.sfxOn
    }
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

    // 오버레이 등장 연출 (잠깐 입력을 막아 실수 입력을 방지하기도 한다)
    private var overlayAnimRef: Overlay? = null
    private var overlayAnimT = 0f
    private var overlayLayer: Bitmap? = null
    private var overlayLayerCanvas: Canvas? = null
    private val overlayFadePaint = Paint(Paint.FILTER_BITMAP_FLAG)

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

    /** 짧은 햅틱 피드백 (버튼 누름 등) — 탭 효과음도 함께 */
    @Suppress("DEPRECATION")
    fun haptic() {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(10L)
        } catch (_: Exception) {
        }
        audio.play(Audio.Sfx.TAP, 0.5f)
    }

    /** 효과음 재생 (편의 함수) */
    fun sfx(s: Audio.Sfx, vol: Float = 1f, rate: Float = 1f) = audio.play(s, vol, rate)

    // ---------------------------------------------------------------------

    fun update(dt: Float) {
        time += dt
        audio.update(dt)   // BGM/환경음 페이드 진행
        input.process()
        val tr = transition
        if (tr != null) {
            tr.update(dt)
            if (tr.finished) transition = null
            input.endFrame()
            return
        }
        val ov = scene.overlay
        if (ov !== overlayAnimRef) {        // 새 오버레이가 열렸다면 등장 연출 시작
            overlayAnimRef = ov
            overlayAnimT = 0f
        }
        if (ov == null && input.rawMode) input.rawMode = false
        if (ov != null) {
            overlayAnimT += dt
            // 등장 연출이 끝나기 전에는 입력을 받아 치지 않아 오발을 막는다
            if (overlayAnimT >= OVERLAY_ENTER_SEC) ov.handleInput(input)
            ov.update(dt)
            // 오버레이가 스스로 닫힘을 요청하면 다음 프레임부터 씬 입력을 받는다.
            // 대화에서 새 오버레이를 연 경우(오버레이 체이닝)에는 새 오버레이를 보존한다.
            if (ov.finished && scene.overlay === ov) scene.closeOverlay()
            // 체이닝으로 오버레이가 바뀌었다면 새 오버레이의 등장 연출을 처음부터 시작
            if (scene.overlay !== overlayAnimRef) {
                overlayAnimRef = scene.overlay
                overlayAnimT = 0f
            }
        } else {
            scene.handleInput(input)
            if (scene.overlay !== overlayAnimRef) {
                overlayAnimRef = scene.overlay
                overlayAnimT = 0f
            }
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
        drawOverlay(c)
        transition?.draw(c, screenW.toFloat(), screenH.toFloat())
    }

    /** 오버레이를 그린다 — 열리는 순간 살짝 줄어들며 페이드인하는 연출을 붙인다. */
    private fun drawOverlay(c: Canvas) {
        val ov = scene.overlay ?: return
        val prog = (overlayAnimT / OVERLAY_ENTER_SEC).coerceIn(0f, 1f)
        if (prog >= 1f || screenW <= 0 || screenH <= 0) {
            ov.draw(c)
            return
        }
        val bmp: Bitmap
        val lc: Canvas
        val existing = overlayLayer
        val existingC = overlayLayerCanvas
        if (existing != null && existingC != null && existing.width == screenW && existing.height == screenH) {
            bmp = existing
            lc = existingC
        } else {
            val nb = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            val nbc = Canvas(nb)
            overlayLayer = nb
            overlayLayerCanvas = nbc
            bmp = nb
            lc = nbc
        }
        lc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        ov.draw(lc)

        // 살짝 크게 시작해서 제자리로 안착 — 테두리가 잘리지 않도록 1보다 크게 시작
        val inv = 1f - prog
        val eased = 1f - inv * inv * inv
        val alpha = (255 * (prog / 0.55f).coerceAtMost(1f)).toInt()
        val k = 1.035f - 0.035f * eased
        val cx = screenW / 2f
        val cy = screenH / 2f
        c.save()
        c.translate(cx, cy)
        c.scale(k, k)
        c.translate(-cx, -cy)
        overlayFadePaint.alpha = alpha
        c.drawBitmap(bmp, 0f, 0f, overlayFadePaint)
        c.restore()
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
                    // A 버튼의 기본값은 안전한 "계속하기" (실수 방지)
                    DialogOverlay.Choice("계속하기"),
                    DialogOverlay.Choice("종료하기") { _ ->
                        SaveManager.save(context, state)
                        (context as? MainActivity)?.runOnUiThread {
                            (context as? MainActivity)?.finish()
                        }
                    }
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
        val raw = (if (phase == 0) t else 1f - t).coerceIn(0f, 1f)
        // 억지로 튀지 않도록 smoothstep 곡선을 쓴다
        val a = raw * raw * (3f - 2f * raw)
        fadePaint.color = Color.argb((255 * a).toInt(), 18, 14, 26)
        c.drawRect(0f, 0f, w, h, fadePaint)
    }

    companion object {
        private val fadePaint = Paint()
    }
}
