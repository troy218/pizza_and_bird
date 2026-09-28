package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF

/**
 * 월드(논리 px) -> 가상 화면(px) 배율. 타일 16px 논리 = 32px 렌더.
 *
 * 지면·길·구조물([Maps]), 풀([Grass]), 지면 이펙트·조명([Fx])은 모두
 * "타일 한 칸 = 렌더 32px" 좌표계로 작성돼 있다. 이 값이 이 좌표계의 기준이므로
 * 함부로 바꾸면 맵이 캐릭터와 어긋나 보인다(맵이 절반 크기로 밀려 보이는 사고).
 * 고해상도(8K) 가상 화면은 [VIRT_H]가 담당하고, 카메라 시야는 [CAM_BASE_ZOOM]이
 * 담당한다 — 월드 배율은 바꾸지 않는다.
 */
const val WORLD_SCALE = 2f

/**
 * 월드 캔버스 위 '화면 패스'(날씨·계절 입자·뷰파인더 등)의 설계 기준 해상도.
 * 이 UI들은 540p 시대의 절대 px 값으로 작성돼 있어서, 2160p 가상 캔버스에는
 * [UI_K]배로 확대해 그린다 (Rain/SeasonFx/SpeedStreaks/Viewfinder 참고).
 */
const val DESIGN_H = 540f

/**
 * 캐릭터·자전거 스프라이트의 '도트' 하나가 가상 화면에서 차지하는 px.
 * 스프라이트 한 장(32도트)이 월드 논리 16px(타일 1칸)과 같은 크기가 되는 배율로,
 * 화질 설정([Game.worldScale] 슈퍼샘플)과 무관하게 **항상 일정**해야 한다.
 * (예전에 슈퍼샘플 배율을 그대로 넘겨 기기마다 캐릭터 크기가 변했었다.)
 */
const val SPRITE_DOT_K = WORLD_SCALE / 2f

/**
 * 기본 카메라 높이(시야 배율).
 *
 * 값이 클수록 카메라가 지면에 가까이 내려와 보이는 범위가 좁아지고 캐릭터가 커진다.
 *
 * 5.0 = 타일 렌더 32px이 화면에서 160px(정수 5배)로 그려지는 배율.
 *       보이는 범위는 월드 논리 기준 가로 384px = 가로 24타일 × 세로 13.5타일
 *       (16:9 · 4:3은 가로 18타일) — 8K 가상 화면에서의 몰입 시점.
 *
 * 참고: [VIRT_H] 540→2160(4배) 이관 때 월드 좌표계(32px 타일)는 그대로 두고
 * 이 배율을 같이 2배(2.5→5.0) 올려 화면 구도를 유지한다.
 * WORLD_SCALE×CAM_BASE_ZOOM 곱이 실제 화면 배율이므로, 이 곱(10)이 바뀌면
 * 보이는 타일 수가 달라진다.
 */
const val CAM_BASE_ZOOM = 5f

/** 가상 렌더링 세로 기준 (8K 설계). 모든 화면비에서 이 높이를 유지한다. */
const val VIRT_H = 2160

/** 화면 패스 설계 px → 가상 화면 px 배율 (2160/540 = 4) */
const val UI_K = VIRT_H / DESIGN_H

/** 자동 화질이 내려갈 수 있는 최저 월드 배율 — 이보다 작으면 화면보다 지나치게 뭉개진다 */
const val AUTO_FLOOR = 0.5f

/** 가상 너비 클램프 — 초광폭/4:3까지 지원 (2160p 기준 4:3=2880, 21.3:9=3840) */
private const val VIRT_W_MIN = 2880
private const val VIRT_W_MAX = 3840

/** 월드 슈퍼샘플 비트맵 상한 (픽셀 수 — 메모리 가드, ≈800MB @ARGB8888, 8K@4× 허용) */
private const val WORLD_BITMAP_MAX_PIXELS = 50_000_000L

/**
 * 자동 화질은 2×/3×로 시작하지만 실제 프레임을 못 맞추면 한 단계 낮춘다.
 * 가끔 발생하는 GC/화면 전환 히치 한두 번에는 반응하지 않고, 90개의 *월드*
 * 프레임 중 25개 이상이 늦을 때만 변경한다. 복귀 시 재상향하지 않아 화질이
 * 계속 오르내리지 않는다 (앱 재실행/설정 변경/화면 크기 변경 시 재평가).
 */
internal class AutoRenderBudget {
    private var frames = 0
    private var late = 0

    fun reset() { frames = 0; late = 0 }

    fun observe(workNanos: Long, intervalNanos: Long): Boolean {
        if (workNanos <= 0L || intervalNanos <= 0L) return false
        if (workNanos > 250_000_000L || intervalNanos > 250_000_000L) {
            reset() // 일시정지/화면 전환 등은 성능 표본이 아니다
            return false
        }
        frames++
        // 게임 업데이트+그리기 작업이 예산을 넘거나, 프레임 간격이 늘어지면서
        // 렌더도 무거웠을 때만 센다. Surface 제출/vsync 대기만 길어진 경우는 제외.
        if (workNanos >= 17_000_000L ||
            (workNanos >= 12_000_000L && intervalNanos >= 25_000_000L)) late++
        if (frames < 90) return false
        val overloaded = late >= 25
        reset()
        return overloaded
    }
}

/**
 * 게임 전역 컨텍스트: 씬 관리, 2160p 기준 가상 해상도 스케일링, 페이드 전환.
 *
 * ## 8K 렌더링 아키텍처 (v0.5)
 * - **가상 해상도**: 세로 2160 고정 · 가로는 화면 비율에 맞춰 2880~3840으로 자동 조정
 *   (16:9=3840 · 4:3=2880 — 초광폭은 클램프, 4:3보다 좁은 창은 상하 여백)
 * - **월드 좌표계**: 월드 논리 px(타일 16px) × WORLD_SCALE(=2) = 가상 화면 px.
 *   타일은 가상 화면에서 32px로 그려지고, 카메라 줌([CAM_BASE_ZOOM]=5)으로
 *   화면을 당겨 본다(타일당 화면 160px).
 * - **월드 슈퍼샘플링**: 월드는 `worldScale`배(화면 해상도에 맞춰 0.5~3, FHD≈0.58× ·
 *   4K=1.15× · 8K=2.3×) 비트맵에 렌더된 뒤 화면에 출력된다. 비트맵이 화면보다 크게
 *   과해지면 프레임당 래스터 비용이 폭증해 터치 반응까지 늦어지므로 화면 +15% 여유가 기준.
 *   자동 모드는 실제 프레임이 늦으면 20%씩 낮춘다
 * - **HUD/오버레이/텍스트**: 항상 실제 화면 해상도에 직접 렌더 — 월드 배율을 내려도 글자가 선명하다
 * - 설정 › 화질에서 렌더 배율(자동/1×/2×/3×)과 화면 보간을 바꿀 수 있다
 */
class Game(val context: Context) {

    /** 고양이 펀치 안내를 이번 실행에서 이미 보여 줬는가 */
    var catPunchHintShown = false

    // 글꼴(roles·픽셀 폰트·dp 배율)을 먼저 준비한다 — 아래에서 그리는 모든 글자가 여기 의존한다.
    init { Type.init(context) }

    /** 가상 화면 크기 (세로 2160 고정, 가로는 화면비 적응) */
    var virtW = 960
        private set
    val virtH = VIRT_H

    /**
     * 월드 슈퍼샘플 배율. 월드 비트맵 = virt × worldScale.
     *
     * 기본(자동)은 **화면에 1:1로 딱 맞는 배율(=viewScale)에서 1.15배 여유**만 둔다 —
     * 비트맵이 화면보다 지나치게 크면 프레임당 래스터 비용이 폭증해 입력까지 늦어진다.
     * (과거 버그: 가상 캔버스(2160p)의 정수배만 쓰도록 해 두고 2×를 고정해
     *   모든 기기에서 7680×4320 = 3,300만 px을 매 프레임 그려 ~1fps가 나왔다.)
     * 저해상 기기에서는 1 미만(예: FHD 0.58×)이 되고, 8K 기기에서는 2.3×까지 올라간다.
     */
    var worldScale = 1f
        private set

    /**
     * 캐릭터·자전거를 HD 스프라이트로 그릴지.
     *
     * 비트맵 도트가 1.5px 이상이면 HD 그림을 원래 크기로 줄여 그려도 디테일이 살아난다.
     * 비트맵이 화면보다 작아지는(다운스케일) 구간에서도 LD 32px 도트를 3배 이상
     * 부풀리는 것보다 HD를 1:1로 맞추는 편이 깔끔하다. 화질 1×(저사양·픽셀 룩)만
     * 예전처럼 32px 도트를 그대로 쓴다.
     */
    val hdSprites: Boolean get() = worldScale >= 1.5f || (worldScale >= 0.5f && state.renderScale != "1")

    var worldBitmap: Bitmap = Bitmap.createBitmap(virtW, virtH, Bitmap.Config.ARGB_8888)
        private set
    var worldCanvas = Canvas(worldBitmap)
        private set

    val state: GameState = SaveManager.load(context)
    // 'auto'에서만 적용되는 세션 내 상한. 유저가 직접 고른 1×/2×/3×는 건드리지 않는다.
    private var autoScaleCap = 3f
    private var lastRenderScaleSetting = state.renderScale
    private val autoBudget = AutoRenderBudget()
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

    /** 백그라운드 준비를 이미 요청한 장비 등급 (중복 요청 방지) */
    private var warmedGearTier = -1

    var screenW = 0
    var screenH = 0
    var viewScale = 1f
    var viewOffX = 0f
    var viewOffY = 0f
    var time = 0f

    /** 다음 장비 등급 준비 확인 타이머(초) */
    private var warmCheck = 0f

    /** 렌더 합성용 스크래치 사각형 — 프레임마다 할당하지 않도록 재사용 */
    private val screenDstRect = RectF()
    // 크기/화질 변경으로 비트맵을 새로 만들었을 때, 덮는 오버레이 뒤라도 한 번 그린다.
    private var worldStale = true

    val density: Float = context.resources.displayMetrics.density

    init {
        illustrations.preloadUiIcons()
        // 스프라이트 생성 비용을 부팅(백그라운드 스레드)에서 미리 치른다.
        //  - 현재 캐릭터 동작 세트(HD · 도트 두 벌): 첫 프레임 렉 방지
        //  - 캐릭터 선택 카드(남/여): 카드가 열리는 순간 얼지 않게
        //    (HD 세트를 통째로 만들면 프레임 176장 × 2 이라 무겁다 — 카드용은
        //     정면 12프레임짜리 작은 세트를 따로 쓴다)
        val other = if (state.gender == "female") "male" else "female"
        assets.playerSet(state.gender, state.gearTier(), true)
        assets.playerSet(state.gender, state.gearTier(), false)
        assets.playerAvatarFrames(other, 0)
        assets.playerAvatarFrames(state.gender, 0)
        // 지금 타는 자전거 세트도 백그라운드에서 준비해 둔다 — 자전거를 처음 타는
        // 순간(페달 첫 프레임)에 32프레임을 만들며 멈추지 않게.
        warmNextGear()
        assets.warmSprites { assets.bikeSet(state.gender, state.gearTier(), state.bikeStyle()) }
        // 타이틀/지역선택의 새는 부팅 스레드에서 사진 기준색까지 읽어 둔다.
        // 나머지 새의 JPEG는 게임 스레드에서 풀지 않고 백그라운드에 요청한다.
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
        // 분할 화면/해상도 변경 시 새 화면 크기에 맞춰 자동 화질을 다시 측정한다.
        autoScaleCap = 3f
        autoBudget.reset()
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
    private fun computeWorldScale(w: Int, h: Int): Float {
        // 자동: 월드 비트맵이 화면에 1:1로 딱 맞는 배율(viewScale)에서 1.15배 여유만 둔다.
        // 가상 캔버스(2160p)가 화면보다 큰 기기(FHD·QHD)에선 1 미만이 되고, 4K=1.15,
        // 8K(4320p)=2.3처럼 화면이 클수록 커진다.
        val cover = minOf(w.toFloat() / virtW.toFloat(), h.toFloat() / VIRT_H.toFloat())
        val auto = (cover * 1.15f).coerceIn(AUTO_FLOOR, 3f).coerceAtMost(autoScaleCap)
        val s = when (state.renderScale) {
            "1" -> 1f
            "2" -> 2f
            "3" -> 3f
            else -> auto
        }
        // 가드: 비트맵 픽셀 수 상한 초과 시 배율을 줄인다
        var k = s
        while (k > AUTO_FLOOR && virtW.toFloat() * k * VIRT_H * k > WORLD_BITMAP_MAX_PIXELS) k = (k - 0.05f).coerceAtLeast(AUTO_FLOOR)
        return k
    }

    /** 월드 비트맵 재생성 (배율/화면 크기 변경 시) */
    private fun rebuildWorldBitmap() {
        val bw = (virtW * worldScale).toInt().coerceAtLeast(1)
        val bh = (virtH * worldScale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val old = worldBitmap
        worldBitmap = bmp
        worldCanvas = Canvas(bmp)
        old.recycle() // 설정/자동 조절 때 2K·4K 버퍼가 GC까지 중복 상주하지 않게 한다
        // 새 비트맵은 비어 있다 — 오버레이가 떠 있어도 이 프레임은 반드시 월드를 그린다
        worldStale = true
    }

    /**
     * 다음 장비 등급(레벨 6·12·19·25)의 캐릭터 세트와 만세 동작을 **미리** 만들어 둔다.
     *
     * 레벨업 화면은 캐릭터를 가장 크게 띄우는 순간이라, 그때 처음 세트를 만들면
     * 화면이 그대로 멈춘다(HD 한 벌 ≈ 0.2초). 다음 레벨에서 등급이 바뀌는 순간에
     * 백그라운드로 준비해 두면 레벨업이 곧바로 뜬다.
     */
    private fun warmNextGear() {
        val lv = state.level
        if (lv >= Progression.MAX_LEVEL) return
        val next = Progression.gearTier(lv + 1)
        if (next == Progression.gearTier(lv) || next == warmedGearTier) return
        warmedGearTier = next
        val gender = state.gender
        assets.warmSprites {
            assets.playerSet(gender, next, true)
            assets.playerSet(gender, next, false)
        }
        assets.warmSprites { assets.cheerFrames(gender, next) }
    }

    /** 화질 설정 변경 후 호출 — 배율/보간을 다시 적용한다 */
    fun applyRenderQuality() {
        if (state.renderScale != lastRenderScaleSetting) {
            lastRenderScaleSetting = state.renderScale
            autoScaleCap = 3f
            autoBudget.reset()
        }
        assets.pxPaint.isFilterBitmap = state.smoothScreen
        if (screenW > 0 && screenH > 0) {
            val newScale = computeWorldScale(screenW, screenH)
            if (newScale != worldScale) {
                worldScale = newScale
                rebuildWorldBitmap()
            }
        }
    }

    /** SurfaceView에서 한 프레임을 제출한 직후 호출. 실제 기기에서만 자동 배율을 조절한다. */
    @Synchronized
    fun onFrameRendered(workNanos: Long, intervalNanos: Long) {
        if (state.renderScale != "auto" || worldScale <= AUTO_FLOOR || screenW <= 0 || screenH <= 0 ||
            transition != null || scene.overlay != null ||
            (scene !is WorldScene && scene !is HomeScene && scene !is LandmarkScene)) {
            autoBudget.reset()
            return
        }
        if (!autoBudget.observe(workNanos, intervalNanos)) return
        // 프레임이 계속 밀리면 배율을 20%씩 내려 바닥(AUTO_FLOOR)까지 따라 내려간다.
        autoScaleCap = (worldScale * 0.8f).coerceAtLeast(AUTO_FLOOR)
        val nextScale = computeWorldScale(screenW, screenH)
        if (nextScale != worldScale) {
            worldScale = nextScale
            rebuildWorldBitmap()
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
        // 다음 장비 등급 그림이 필요해지기 전에 미리 준비해 둔다 (1초에 한 번만 확인)
        warmCheck -= dt
        if (warmCheck <= 0f) {
            warmCheck = 1f
            warmNextGear()
        }
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
            val saveCount = wc.save()
            try {
                wc.scale(worldScale, worldScale)
                scene.drawWorld(wc)
                worldStale = false
            } finally {
                // 씬 렌더가 예외를 내도 다음 프레임의 Canvas 변환이 누적되지 않게 한다.
                wc.restoreToCount(saveCount)
            }
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
