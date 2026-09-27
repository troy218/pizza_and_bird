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
 * 기본 카메라 높이(시야 배율).
 *
 * 1.0 = 예전처럼 높은 하늘에서 내려다보는 시점(가로 30타일이 한눈에 보인다).
 * 값이 클수록 카메라가 지면에 가까이 내려와 보이는 범위가 좁아지고 캐릭터가 커진다.
 *
 * 1.5 = 타일 32px이 48px로 그려지는 정수 배(픽셀이 뭉개지지 않는다).
 *       보이는 범위는 가로 20타일 × 세로 11.25타일 — 집(13×9타일)은 여전히 한 화면에 들어온다.
 */
const val CAM_BASE_ZOOM = 1.5f

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
 * - **가상 해상도**: 세로 540 고정 · 가로는 화면 비율에 맞춰 720~1280으로 자동 조정
 *   (16:9=960 · 19.5:9=1170 · 20:9=1200 · 21:9=1260 — 초광폭은 좌우, 4:3보다 좁은 창은 상하 여백)
 * - **월드 슈퍼샘플링**: 월드는 `worldScale`(정수 1~3배) 비트맵에 렌더된 뒤 화면에 출력된다.
 *   스프라이트는 정수배로 커지므로 픽셀 아트 격자가 흐트러지지 않고,
 *   FHD=2×(1080p 네이티브) · QHD(2K)=2×(업스케일 1.33, 기존 2.67 대비 픽셀 굵기 절반) · 4K=3×
 * - **HUD/오버레이/텍스트**: 항상 실제 화면 해상도에 직접 렌더 — 2K에서도 글자가 선명하다
 * - 설정 › 화질에서 렌더 배율(자동/1×/2×/3×)과 화면 보간을 바꿀 수 있다
 */
class Game(val context: Context) {

    /** 고양이 펀치 안내를 이번 실행에서 이미 보여 줬는가 */
    var catPunchHintShown = false

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

    /** 렌더 합성용 스크래치 사각형 — 프레임마다 할당하지 않도록 재사용 */
    private val screenDstRect = RectF()
    // 크기/화질 변경으로 비트맵을 새로 만들었을 때, 덮는 오버레이 뒤라도 한 번 그린다.
    private var worldStale = true

    val density: Float = context.resources.displayMetrics.density

    init {
        illustrations.preloadUiIcons()
        // 스프라이트 생성 비용을 부팅(백그라운드 스레드)에서 미리 치른다.
        //  - 현재 캐릭터 동작 세트: 첫 프레임 렉 방지
        //  - 양 성별 0티어: 캐릭터 선택 화면이 열리는 순간 다른 성별 세트(프레임 120장)를
        //    만들며 얼던 것을 방지 — 카드 두 장(남/여 0티어)이 바로 움직인다.
        val other = if (state.gender == "female") "male" else "female"
        assets.playerSet(state.gender, state.gearTier())
        assets.playerSet(other, state.gearTier())
        assets.playerSet("male", 0)
        assets.playerSet("female", 0)
        // 타이틀/지역선택의 새는 부팅 스레드에서 사진 기준색까지 읽어 둔다.
        // 나머지 598종은 게임 스레드에서 JPEG를 풀지 않고 백그라운드에 요청한다.
        assets.preloadBird("sparrow")
        assets.birdFlipped("sparrow")
        assets.preloadBird("crane")
        assets.preloadBird("owl")
        assets.preloadBird("gull")
        assets.preloadBird("greattit")
        assets.preloadBird("egret")
        assets.preloadBird("magpie")
        assets.birdFlipped("magpie")
    }

    @Synchronized
    fun onSurfaceChanged(w: Int, h: Int) {
        // Some devices briefly report a zero-sized surface while entering split-screen.
        // Keep the last valid frame/layout until Android supplies the replacement size.
        if (w <= 0 || h <= 0) return
        if (screenW == w && screenH == h) return
        screenW = w
        screenH = h
        // The current renderer keeps its 540px virtual height and adapts virtual width
        // to aspect ratio (720..1280). This preserves the game's 2K-era layout while
        // avoiding any stretch on 4:3 tablets/foldables and on ultrawide phones.
        val nextVirtW = (VIRT_H.toFloat() * w / h.toFloat()).toInt().coerceIn(VIRT_W_MIN, VIRT_W_MAX)
        val oldVirtW = virtW
        val oldWorldScale = worldScale
        virtW = nextVirtW
        val nextWorldScale = computeWorldScale(w, h)
        val bitmapChanged = nextVirtW != oldVirtW || nextWorldScale != oldWorldScale
        worldScale = nextWorldScale
        if (bitmapChanged) rebuildWorldBitmap()
        // Fit the aspect-adaptive virtual surface without distortion. If a narrow
        // foldable/split window falls below the supported 4:3 virtual minimum, fit by
        // width and letterbox vertically; ultrawide/clamped surfaces pillarbox instead.
        viewScale = minOf(w.toFloat() / virtW.toFloat(), h.toFloat() / virtH.toFloat())
        viewOffX = (w - virtW * viewScale) / 2f
        viewOffY = (h - virtH * viewScale) / 2f
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
        // 새 비트맵은 비어 있다 — 오버레이가 떠 있어도 이 프레임은 반드시 월드를 그린다
        worldStale = true
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
    @Synchronized
    fun screenToVirtual(p: PointF): PointF {
        val s = viewScale.takeIf { it > 0f } ?: return PointF(p.x, p.y)
        return PointF((p.x - viewOffX) / s, (p.y - viewOffY) / s)
    }

    @Synchronized
    fun isInsideVirtualViewport(screen: PointF): Boolean {
        val virtual = screenToVirtual(screen)
        return virtual.x in 0f..virtW.toFloat() && virtual.y in 0f..virtH.toFloat()
    }

    /** 실제 터치 좌표 -> 현재 씬의 절대 월드 좌표 (렌더링 카메라 오프셋 포함). */
    @Synchronized
    fun screenToWorld(p: PointF): PointF {
        val v = screenToVirtual(p)
        val camera = scene.cameraOffset()
        // 월드가 화면 중앙 기준으로 확대돼 있으면(카메라 모드 망원 등) 그만큼 되돌린다
        val z = scene.camera()?.zoom ?: 1f
        val hx = virtW / 2f
        val hy = virtH / 2f
        val ux = if (z == 1f) v.x else hx + (v.x - hx) / z
        val uy = if (z == 1f) v.y else hy + (v.y - hy) / z
        return PointF(ux / WORLD_SCALE + camera.x, uy / WORLD_SCALE + camera.y)
    }

    /** 짧은 햅틱 피드백 (버튼 누름 등) — 탭 효과음도 함께 */
    fun haptic() {
        audio.play(Audio.Sfx.TAP, 0.5f)
        // 진동은 시스템 서비스 호출이라 기기마다 1~수 ms 걸린다.
        // 버튼을 누른 그 프레임을 막지 않도록 전용 스레드로 뺀다.
        Haptics.tap(context)
    }

    /** 효과음 재생 (편의 함수) */
    fun sfx(s: Audio.Sfx, vol: Float = 1f, rate: Float = 1f) = audio.play(s, vol, rate)

    // ---------------------------------------------------------------------

    @Synchronized
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
            // 입력은 등장 애니메이션과 무관하게 즉시 받는다. 한 프레임의 입력은
            // endFrame()에서 지워지므로 메뉴를 연 터치가 새 오버레이에 다시 전달되지 않는다.
            // (기존 90ms 입력 금지는 빠른 탭을 통째로 버려 버튼이 먹통처럼 보였다.)
            ov.handleInput(input)
            ov.update(dt)
            // 대화에서 새 오버레이를 연 경우(오버레이 체이닝)에는 새 오버레이를 보존한다.
            if (ov.finished && scene.overlay === ov) scene.closeOverlay()
        } else {
            scene.handleInput(input)
        }
        scene.update(dt)
        input.endFrame()
    }

    @Synchronized
    fun render(c: Canvas) {
        // 월드 재렌더는 프레임당 비트맵 드로우 약 2000회. 전체 화면 오버레이가
        // 열린 동안 월드 씬의 업데이트도 멈추므로 기존 마지막 프레임을 재사용한다.
        // 3프레임마다 다시 그리던 방식은 메뉴 입력 프레임마다 주기적인 끊김을 만들었다.
        // 오버레이를 닫으면 !covered 로 그 프레임에 즉시 최신 월드를 다시 그린다.
        val ov = scene.overlay
        val covered = ov != null && ov.coversWorld && transition == null
        if (!covered || worldStale) {
            val wc = worldCanvas
            wc.save()
            wc.scale(worldScale.toFloat(), worldScale.toFloat())
            scene.drawWorld(wc)
            wc.restore()
            worldStale = false
        }
        // 화면 합성: 월드 비트맵(고해상도) + HUD/오버레이(네이티브 해상도)
        c.drawColor(0xFF2E2A3A.toInt())
        val dst = screenDstRect
        dst.set(
            viewOffX, viewOffY,
            viewOffX + virtW * viewScale, viewOffY + virtH * viewScale
        )
        c.drawBitmap(worldBitmap, null, dst, assets.pxPaint)
        // HUD는 오버레이 아래에도 보인다(조이스틱·액션 버튼·가방 바가 그대로 살아 있다)
        // 그래서 오버레이를 열어도 항상 그린다.
        scene.drawHud(c)
        // 오버레이 자체의 dim/enterShift 연출만 사용한다. 기존의 화면 크기 ARGB
        // 임시 비트맵 합성은 열 때마다 수 MB를 할당하고 첫 프레임을 늦췄다.
        ov?.draw(c)
        transition?.draw(c, screenW.toFloat(), screenH.toFloat())
    }

    /** 페이드 전환 (액션은 화면이 완전히 어두워진 순간 실행) */
    fun fadeTo(action: () -> Unit) {
        if (transition == null) transition = Transition(action)
    }

    // ---------------------------------------------------------------------
    // 화면 연출 단축 호출 — 오버레이/미니게임에서도 현재 씬의 카메라를 흔들 수 있다.
    // ---------------------------------------------------------------------

    /** 충격 (0~1) */
    fun shake(amount: Float) {
        scene.camera()?.shake(amount)
    }

    /** 시야각 펀치 (+확대 / -축소) */
    fun punchZoom(amount: Float) {
        scene.camera()?.punchZoom(amount)
    }

    /** 방향성 킥 */
    fun kick(dirX: Float, dirY: Float, peakPx: Float) {
        scene.camera()?.kick(dirX, dirY, peakPx)
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

    /**
     * [P05] 백업 코드에서 복원한 직후 — prefs를 다시 읽어 **살아 있는 `state` 객체**를 갱신한다.
     *
     * `state`는 val이라 통째로 갈아끼울 수 없고, 씬·HUD·Audio는 전부 이 한 객체를 붙잡고 있다.
     * 그래서 새로 로드한 상태의 필드를 지금 객체에 그대로 복사해 넣는다(참조 동일성 유지).
     * 세이브 포맷/스키마는 건드리지 않는다 — [SaveManager.load] 경로만 다시 탄다.
     *
     * @return 복원된 상태로 게임 시작 지점(`started`)이 참인지.
     *         거짓이면 타이틀에서 「새로 시작하기」를 눌러야 한다.
     */
    fun reloadState(): Boolean {
        val fresh = SaveManager.load(context)
        var k: Class<*> = GameState::class.java
        while (k != Any::class.java) {
            for (f in k.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    f.set(state, f.get(fresh))
                } catch (_: Throwable) {
                }
            }
            k = k.superclass ?: Any::class.java
        }
        // 복원된 설정을 곧바로 반영한다 (음소거/화질이 백업 기준으로 돌아온다)
        audio.setMusic(state.musicOn)
        audio.setSfx(state.sfxOn)
        hud.releaseStick()
        applyRenderQuality()
        return state.started
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

/**
 * 버튼 진동 피드백 전용 스레드.
 *
 * Vibrator.vibrate() 은 시스템 서비스 호출이라 게임 스레드에서 부르면
 * 버튼을 누른 프레임이 그대로 밀린다. 여기서는 30ms 안에 연속으로 들어온
 * 요청은 한 번으로 합쳐(연타해도 과하지 않게) 별도 스레드에서 처리한다.
 */
private object Haptics {
    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    @Suppress("DEPRECATION")
    fun tap(context: Context) {
        if (!started.compareAndSet(false, true)) {
            // 이미 스레드가 돌고 있으면 "한 번 더" 만 표시한다 (연타 Rate limit)
            pending.set(true)
            return
        }
        Thread({
            val vib = try {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            } catch (_: Exception) {
                null
            }
            if (vib != null) {
                while (true) {
                    try { vib.vibrate(10L) } catch (_: Exception) { }
                    if (!pending.getAndSet(false)) break
                    // 연속 입력은 30ms 간격으로만 울린다
                    try {
                        Thread.sleep(30L)
                    } catch (_: InterruptedException) {
                        started.set(false)
                        return@Thread
                    }
                }
            }
            started.set(false)
        }, "PizzaAndBirdHaptic").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }
}
