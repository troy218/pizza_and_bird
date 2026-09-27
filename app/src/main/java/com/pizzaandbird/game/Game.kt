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

/** 가상 렌더링 세로 기준 (2K 설계). 모든 화면비에서 이 높이를 유지한다. */
const val VIRT_H = 540

/** 가상 너비 클램프 — 초광폭/4:3까지 지원 (540p 기준 4:3=720, 21.3:9=1280) */
private const val VIRT_W_MIN = 720
private const val VIRT_W_MAX = 1280

/** 월드 슈퍼샘플 비트맵 상한 (픽셀 수 — 메모리 가드, ≈52MB @ARGB8888, 4K@2× 허용) */
private const val WORLD_BITMAP_MAX_PIXELS = 13_000_000L

/**
 * 게임 전역 컨텍스트: 씬 관리, 2K 기준 가상 해상도 스케일링, 페이드 전환.
 *
 * ## 2K 렌더링 아키텍처 (v0.4)
 * - **가상 해상도**: 세로 540 고정 · 가로는 화면 비율에 맞춰 720~1280으로 자동 확장
 *   (16:9=960 · 19.5:9=1170 · 20:9=1200 · 21:9=1260 — 레터박스 없이 넓게 보인다)
 * - **월드 슈퍼샘플링**: 월드는 `worldScale`(정수 1~3배) 비트맵에 렌더된 뒤 화면에 출력된다.
 *   스프라이트는 정수배로 커지므로 픽셀 아트 격자가 흐트러지지 않고,
 *   FHD=2×(1080p 네이티브) · QHD(2K)=2×(업스케일 1.33, 기존 2.67 대비 픽셀 굵기 절반) · 4K=3×
 * - **HUD/오버레이/텍스트**: 항상 실제 화면 해상도에 직접 렌더 — 2K에서도 글자가 선명하다
 * - 설정 › 화질에서 렌더 배율(자동/1×/2×/3×)과 화면 보간을 바꿀 수 있다
 */
class Game(val context: Context) {

    // 글꼴(roles·픽셀 폰트·dp 배율)을 먼저 준비한다 — 아래에서 그리는 모든 글자가 여기 의존한다.
    init { Type.init(context) }

    /** 가상 화면 크기 (세로 540 고정, 가로는 화면비 적응) */
    var virtW = 960
        private set
    val virtH = VIRT_H

    /** 월드 슈퍼샘플 배율 (정수 1~3). 월드 비트맵 = virt*worldScale */
    var worldScale = 1
        private set

    var worldBitmap: Bitmap = Bitmap.createBitmap(virtW, virtH, Bitmap.Config.ARGB_8888)
        private set
    var worldCanvas = Canvas(worldBitmap)
        private set

    val state: GameState = SaveManager.load(context)
    val assets = Assets(context)
    val illustrations = SvgIllustrations(context.assets)
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

    val density: Float = context.resources.displayMetrics.density

    init {
        // 첫 프레임에 렉이 걸리지 않도록 현재 캐릭터 동작 스프라이트를 미리 만들어 둔다
        assets.playerSet(state.gender, state.gearTier())
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        screenW = w
        screenH = h
        // 1) 가상 너비 = 세로 540 기준 화면 비율 (클램프로 극단 비율 대응)
        virtW = (VIRT_H.toFloat() * w / h.toFloat()).toInt().coerceIn(VIRT_W_MIN, VIRT_W_MAX)
        // 2) 월드 슈퍼샘플 배율 (설정 반영 + 메모리 가드)
        worldScale = computeWorldScale(w, h)
        rebuildWorldBitmap()
        // 3) 화면 매핑: 세로 길이에 정확히 맞춘다 (가로는 클램프 시에만 아주 작은 여백)
        viewScale = h.toFloat() / virtH.toFloat()
        viewOffX = (w - virtW * viewScale) / 2f
        viewOffY = 0f
        hud.layout(w, h)
        scene.onLayout()
    }

    /** 설정(state.renderScale)과 화면 크기로 월드 배율 결정 */
    private fun computeWorldScale(w: Int, h: Int): Int {
        val auto = (h / VIRT_H).coerceIn(1, 3)   // FHD=2 · QHD=2 · 4K=3
        val s = when (state.renderScale) {
            "1" -> 1
            "2" -> 2
            "3" -> 3
            else -> auto
        }
        // 가드: 비트맵 픽셀 수 상한 초과 시 배율을 줄인다
        var k = s
        while (k > 1 && virtW.toLong() * k * virtH.toLong() * k > WORLD_BITMAP_MAX_PIXELS) k--
        return k
    }

    /** 월드 비트맵 재생성 (배율/화면 크기 변경 시) */
    private fun rebuildWorldBitmap() {
        val bw = (virtW * worldScale).coerceAtLeast(1)
        val bh = (virtH * worldScale).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        worldBitmap = bmp
        worldCanvas = Canvas(bmp)
    }

    /** 화질 설정 변경 후 호출 — 배율/보간을 다시 적용한다 */
    fun applyRenderQuality() {
        assets.pxPaint.isFilterBitmap = state.smoothScreen
        if (screenW > 0 && screenH > 0) {
            val newScale = computeWorldScale(screenW, screenH)
            if (newScale != worldScale) {
                worldScale = newScale
                rebuildWorldBitmap()
            }
        }
    }

    /** 실제 터치 좌표 -> 가상 화면 좌표 (여백 포함). */
    fun screenToVirtual(p: PointF): PointF = PointF(
        (p.x - viewOffX) / viewScale,
        (p.y - viewOffY) / viewScale
    )

    /** 실제 터치 좌표 -> 현재 씬의 절대 월드 좌표 (렌더링 카메라 오프셋 포함). */
    fun screenToWorld(p: PointF): PointF {
        val v = screenToVirtual(p)
        val camera = scene.cameraOffset()
        return PointF(v.x / WORLD_SCALE + camera.x, v.y / WORLD_SCALE + camera.y)
    }

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
        if (ov == null && input.rawMode) input.rawMode = false
        if (ov != null) {
            ov.handleInput(input)
            ov.update(dt)
            // 오버레이가 스스로 닫힘을 요청하면 다음 프레임부터 씬 입력을 받는다.
            // 대화에서 새 오버레이를 연 경우(오버레이 체이닝)에는 새 오버레이를 보존한다.
            if (ov.finished && scene.overlay === ov) scene.closeOverlay()
        } else {
            scene.handleInput(input)
        }
        scene.update(dt)
        input.endFrame()
    }

    fun render(c: Canvas) {
        // 월드: 가상 좌표계로 그리고 worldScale배 슈퍼샘플 비트맵에 기록
        val wc = worldCanvas
        wc.save()
        wc.scale(worldScale.toFloat(), worldScale.toFloat())
        scene.drawWorld(wc)
        wc.restore()
        // 화면 합성: 월드 비트맵(고해상도) + HUD/오버레이(네이티브 해상도)
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
