package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * 씬 베이스. 오버레이(대화상자 등)를 하나 가질 수 있다.
 */
abstract class Scene(val game: Game) {
    var overlay: Overlay? = null

    /** 이 씬의 시점 리그(화면 움직임). 없는 씬도 있다 — 타이틀/지역선택 등 */
    open fun camera(): ViewRig? = null

    open fun update(dt: Float) {}
    open fun drawWorld(c: Canvas) {}
    open fun drawHud(c: Canvas) {}
    open fun handleInput(input: Input) {}
    open fun onLayout() {}
    /** drawWorld에서 사용 중인 카메라의 논리 월드 좌표. 메뉴 씬은 (0, 0). */
    open fun cameraOffset(): PointF = PointF(0f, 0f)

    fun openOverlay(o: Overlay) {
        overlay = o
    }

    fun closeOverlay() {
        overlay = null
    }

    /** 천단위 콤마 금액(₩ 기호 없음) — "＋₩${won(n)}"처럼 기호는 부르는 쪽에서 붙인다 */
    fun won(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)
}

/**
 * 실내(집·랜드마크) 환경음 — 비 오는 날엔 지붕에 떨어지는 빗소리가 은은하게 들린다.
 * 창밖 풍경은 보이지 않아도 "비가 오고 있다"는 것이 소리로 전해진다. 비가 그치면 조용해진다.
 * (매 프레임 불러도 같은 트랙이면 다시 시작하지 않는다 — Audio.playAmb 의 페이드 규칙)
 */
/**
 * 실내(집·랜드마크) 환경음 — 비 오는 날엔 지붕 빗소리, 그 외엔 창밖 계절이 벽 너머로 살짝.
 * 겨울엔 화덕 장작, 여름 낮엔 멀리 매미, 가을 밤엔 귀뚜라미, 봄 밤엔 개구리.
 * 씬 update()에서 매 프레임 부른다 (날씨·계절·밤낮이 바뀌면 바로 반영).
 */
fun Scene.applyIndoorAmbience() {
    val state = game.state
    if (state.weather() == Weather.RAIN) {
        game.audio.playAmb(R.raw.amb_rain_roof, 0.20f)
        return
    }
    val night = state.isNight()
    when (state.season()) {
        Season.WINTER -> game.audio.playAmb(R.raw.amb_fire, 0.30f)
        Season.SUMMER -> if (!night) game.audio.playAmb(R.raw.amb_cicada, 0.16f)
        else game.audio.playAmb(R.raw.amb_night, 0.19f)
        Season.AUTUMN -> if (night) game.audio.playAmb(R.raw.amb_cricket, 0.21f)
        else game.audio.playAmb(R.raw.amb_birds, 0.10f)
        Season.SPRING -> if (night) game.audio.playAmb(R.raw.amb_frog, 0.19f)
        else game.audio.playAmb(R.raw.amb_birds, 0.13f)
    }
}

/**
 * 타이틀 화면 — 앱을 열면 가장 먼저 보이는 "시작하기" 화면.
 *
 * 구도(위 → 아래):
 *   · **제목 (화면 정중앙)**: PIZZA and BIRD(픽셀 폰트) + 피자와 새 + 태그라인
 *   · 시작 버튼 (시작하기 / 이어하기) — 저장이 있으면 두 개, 없으면 하나
 *   · 아래쪽 잔디 언덕에서 펼쳐지는 작은 장면: 피자 · 목화로즈 오븐 · 새들
 *     오븐에서는 연기가 피어오르고, 피자 주변으로 반짝이가 뜁니다.
 */
class TitleScene(game: Game) : Scene(game) {

    private var t = 0f
    private var startRect = RectF()
    private var contRect = RectF()
    private val titlePaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hazePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowRect = RectF()

    /** 제목 뒤의 부드러운 광채 — 레이아웃(화면 크기)이 바뀔 때만 재생성한다. */
    private var glowShader: Shader? = null
    private var glowW = 0
    private var glowH = 0

    /** 하늘 그라데이션 앵커 (위 → 지평선) — 24단계로 보간해 밴드를 부드럽게 만든다. */
    private val skyAnchors = intArrayOf(
        0xFF4FB3E4.toInt(), 0xFF9BDCF2.toInt(), 0xFFCDEFF0.toInt(), 0xFFF2FADE.toInt()
    )
    private val skyBands: IntArray = run {
        val stops = floatArrayOf(0f, 0.45f, 0.78f, 1f)
        val out = IntArray(24)
        for (i in out.indices) {
            val u = i / (out.size - 1).toFloat()
            var s = 1
            while (s < stops.size - 1 && u > stops[s]) s++
            val s0 = stops[s - 1]
            val s1 = stops[s]
            val k = ((u - s0) / (s1 - s0)).coerceIn(0f, 1f)
            val a = skyAnchors[s - 1]
            val b = skyAnchors[s]
            out[i] = Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
            )
        }
        out
    }

    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        game.hud.questLabel = null
        game.hud.photoModeHint = false
        game.hud.regionLabel = ""
        game.audio.stopAmb()
        game.audio.playBgm(R.raw.bgm_title)   // 🎵 신비로운 세계
    }

    override fun update(dt: Float) {
        t += dt
        game.hud.update(dt)
    }

    // ------------------------------------------------------------------
    // 월드 캔버스 (가상 해상도): 하늘 · 언덕 · 잔디 · 작은 장면
    // ------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        val p = titlePaint
        val a = game.assets
        val W = game.virtW.toFloat()          // 화면비 적응 가상 너비
        val cx = W / 2f
        val span = W + 160f                   // 구름/새 반복 범위

        // 하늘 그라데이션 (24단계 보간 — 밴드 경계가 안 보일 만큼 부드럽게)
        val bandH = game.virtH.toFloat() / skyBands.size
        for (i in skyBands.indices) {
            p.color = skyBands[i]
            c.drawRect(0f, i * bandH, W, (i + 1) * bandH + 1f, p)
        }

        // 해 — 은은한 헤일(4단) + 천천히 도는 광선
        val sx = W - 170f
        val sy = 74f
        p.color = Color.argb(16, 250, 240, 190)
        c.drawCircle(sx, sy, 104f, p)
        p.color = Color.argb(24, 250, 240, 190)
        c.drawCircle(sx, sy, 82f, p)
        p.color = Color.argb(34, 250, 240, 190)
        c.drawCircle(sx, sy, 63f, p)
        p.color = Color.argb(48, 250, 240, 190)
        c.drawCircle(sx, sy, 49f, p)
        p.color = 0xFFF7EDB8.toInt()
        c.drawCircle(sx, sy, 44f, p)
        p.color = 0xFFFFFBE0.toInt()
        c.drawCircle(sx, sy, 32f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.argb(110, 251, 239, 168)
        val rot = t * 0.08f
        for (i in 0 until 12) {
            val ang = rot + i * (Math.PI / 6.0).toFloat()
            val ca = cos(ang)
            val sa = sin(ang)
            c.drawLine(sx + ca * 52f, sy + sa * 52f, sx + ca * 70f, sy + sa * 70f, p)
        }
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT

        // 구름 (가까운 흰 구름 + 먼 푸른 구름)
        p.color = Color.argb(225, 255, 255, 255)
        drawCloud(c, p, (t * 9f) % span - 120f, 66f, 1.4f)
        drawCloud(c, p, (t * 5f + 300f) % span - 120f, 128f, 1.0f)
        drawCloud(c, p, (t * 6.5f + 640f) % span - 120f, 48f, 1.2f)
        p.color = Color.argb(105, 214, 236, 244)
        drawCloud(c, p, (t * 3.5f + 120f) % span - 120f, 186f, 2.2f)
        drawCloud(c, p, (t * 4.5f + 760f) % span - 120f, 104f, 1.8f)
        drawCloud(c, p, (t * 3f + 480f) % span - 120f, 240f, 2.6f)

        // 날아가는 새 실루엣
        p.color = Color.argb(150, 90, 80, 90)
        p.strokeWidth = 2.4f
        for (i in 0 until 4) {
            val bx = (t * 26f + i * 240f) % span - 80f
            val by = 88f + i * 36f + sin(t * 2f + i) * 9f
            c.drawLine(bx, by, bx + 9f, by - 4f, p)
            c.drawLine(bx + 9f, by - 4f, bx + 18f, by, p)
        }
        // 오른쪽에서 지나가는 작은 V자 무리
        p.color = Color.argb(85, 90, 80, 90)
        p.strokeWidth = 1.6f
        val flockX = W + 60f - ((t * 15f) % (span + 240f))
        val flockY = 148f + sin(t * 1.3f) * 8f
        for (i in 0 until 3) {
            val fx = flockX - i * 15f
            val fy = flockY + (i % 2) * 7f
            c.drawLine(fx, fy, fx - 6f, fy - 3f, p)
            c.drawLine(fx - 6f, fy - 3f, fx - 12f, fy, p)
        }

        // 지평선 안개 띠 — 하늘과 언덕 사이 깊이를 더해 준다
        hazePaint.color = Color.argb(38, 255, 255, 255)
        c.drawRect(0f, 396f, W, 426f, hazePaint)

        // 언덕 (2겹 — 화면비에 따라 중심 대칭)
        p.color = 0xFF8FCF8E.toInt()
        c.drawCircle(0.16f * W, 472f, 205f, p)
        c.drawCircle(0.52f * W, 490f, 235f, p)
        c.drawCircle(0.88f * W, 474f, 210f, p)
        p.color = 0xFF79BC78.toInt()
        c.drawCircle(0.33f * W, 498f, 170f, p)
        c.drawCircle(0.74f * W, 496f, 185f, p)

        // 잔디 (앞쪽 3줄 — 예전보다 얇게, 화면비로 채운다)
        val grass = a.tiles[T.GRASS.ordinal]
        val flowers = a.tiles[T.FLOWER.ordinal]
        val maxCol = (game.virtW + 31) / 32
        for (row in 14..16) {
            for (col in 0 until maxCol) {
                val variant = a.tileVariant(T.GRASS.ordinal, col, row)
                c.drawBitmap(grass[variant], col * 32f, row * 32f, a.sprPaint)
            }
        }
        // 꽃은 드물게 — 잔디가 시끄럽지 않게
        val flowerFractions = floatArrayOf(0.035f, 0.115f, 0.215f, 0.30f, 0.435f, 0.575f, 0.66f, 0.795f, 0.885f, 0.965f)
        for (i in flowerFractions.indices) {
            val col = (flowerFractions[i] * maxCol).toInt().coerceIn(0, maxCol - 1)
            val row = if (i % 3 == 0) 16 else 15
            c.drawBitmap(flowers[i % flowers.size], col * 32f, row * 32f, a.sprPaint)
        }

        // 지평선 나무 (양쪽 끝 — 가운데는 장면/버튼을 위해 비워 둔다)
        val trees = a.tiles[T.TREE.ordinal]
        c.drawBitmap(trees[0], 0.045f * W, 352f, a.sprPaint)
        c.drawBitmap(trees[1], 0.15f * W, 358f, a.sprPaint)
        c.drawBitmap(trees[1], 0.85f * W, 358f, a.sprPaint)
        c.drawBitmap(trees[0], 0.955f * W, 352f, a.sprPaint)

        // ----------------------------------------------------------------
        // 작은 장면 — 왼쪽: 피자 + 새 / 오른쪽: 화덕 + 연기 / 새들
        // 가운데(버튼 자리)는 잔디만 남아 화면이 깔끔해진다.
        // ----------------------------------------------------------------
        val side = minOf(280f, W * 0.29f)
        val lx = cx - side
        val rx = cx + side

        // 그림자 (요소들이 잔디 위에 "앉아" 있게)
        shadowPaint.color = Color.argb(52, 45, 80, 40)
        c.drawOval(lx - 46f, 468f, lx + 46f, 484f, shadowPaint)
        c.drawOval(rx - 42f, 476f, rx + 42f, 492f, shadowPaint)

        // 피자 (부유하며 살랑살랑)
        val pizzaY = 410f + sin(t * 2.2f) * 5f
        c.drawBitmap(a.pizzaIconBig, lx - 44f, pizzaY, a.sprPaint)
        // 피자 위에 앉은 참새
        val sparrow = a.bird("sparrow")
        c.drawBitmap(sparrow, lx + 8f, pizzaY - a.birdH("sparrow") + 10f + sin(t * 2.4f) * 2f, a.sprPaint)
        // 피자 근처 반짝이
        UiKit.sparkle(c, lx + 52f, pizzaY + 8f, 10f, Color.argb((120 + 70 * sin(t * 2.6f)).toInt(), 255, 214, 110), t * 2.6f)
        UiKit.sparkle(c, lx - 58f, pizzaY + 26f, 7f, Color.argb((110 + 70 * sin(t * 2.6f + 2.1f)).toInt(), 255, 232, 150), t * 2.6f + 2.1f)

        // 목화로즈 오븐 + 피어오르는 연기 (크게 불며 사방으로 흐르는 연무)
        game.illustrations.draw(c, "wood_fired_oven.svg", RectF(rx - 34f, 398f, rx + 34f, 488f))
        for (i in 0 until 4) {
            val cyc = ((t * 13f + i * 23f) % 92f) / 92f          // 0..1 상승 진행
            val px = rx + 10f + sin(t * 1.6f + i * 1.9f) * (5f + cyc * 8f)
            val py = 398f - cyc * 92f
            smokePaint.color = Color.argb(((80 - cyc * 80).toInt().coerceAtLeast(0)), 255, 252, 244)
            val pr = 5f + cyc * 12f
            c.drawCircle(px, py, pr, smokePaint)
            c.drawCircle(px + 4f, py - 3f, pr * 0.7f, smokePaint)
        }

        // 새들 — 왼쪽 날아다니는 올빼미, 오른쪽 서 있는두루미
        val owl = a.bird("owl")
        c.drawBitmap(owl, lx - 128f, 336f + sin(t * 2.9f) * 5f, a.sprPaint)
        val crane = a.bird("crane")
        c.drawBitmap(crane, rx + 78f, 492f - a.birdH("crane") + sin(t * 1.7f) * 2f, a.sprPaint)
        val magpie = a.birdFlipped("magpie")
        c.drawBitmap(magpie, rx - 128f, 420f + sin(t * 2.2f + 1f) * 4f, a.sprPaint)
    }

    private fun drawCloud(c: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, 9f * s, p)
        c.drawCircle(x + 10f * s, y - 3f * s, 11f * s, p)
        c.drawCircle(x + 22f * s, y, 8f * s, p)
        c.drawRect(x - 8f * s, y, x + 28f * s, y + 9f * s, p)
    }

    // ------------------------------------------------------------------
    // HUD (실제 화면 해상도): 정중앙 제목 + 버튼
    // ------------------------------------------------------------------

    override fun drawHud(c: Canvas) {
        fun dp(v: Float): Float = v * game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        val cx = w / 2f
        val hasSave = game.state.started

        // 등장 연출 — 제목 → 태그라인 → 버튼 순서로 살짝 지연되며 들어온다
        val titleK = easeOutCubic(clamp01(t / 0.55f))
        val tagK = easeOutCubic(clamp01((t - 0.15f) / 0.55f))
        val btnK = easeOutCubic(clamp01((t - 0.35f) / 0.5f))

        // 제목 뒤 부드러운 광채 (화면이 바뀔 때만 셰이더 재생성)
        val gy = h * 0.355f
        val gr = minOf(w * 0.30f, h * 1.15f) * (0.85f + 0.15f * titleK)
        if (glowW != w.toInt() || glowH != h.toInt()) {
            glowW = w.toInt()
            glowH = h.toInt()
            glowShader = RadialGradient(
                cx, gy, minOf(w * 0.30f, h * 1.15f),
                intArrayOf(0x6BFFFFFF.toInt(), 0x00FFFFFF.toInt()),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        glowPaint.shader = glowShader
        c.drawCircle(cx, gy, gr, glowPaint)
        glowPaint.shader = null

        // 제목 — 화면 세로 정중앙
        c.save()
        c.translate(cx, h * 0.34f)
        val ts = 0.93f + 0.07f * titleK
        c.scale(ts, ts)
        c.translate(-cx, -h * 0.34f)
        val rise = (1f - titleK) * dp(16f)
        // 라틴 로고 — 5x7 픽셀 폰트 + 크림 테두리 (스티커 느낌)
        Type.sticker(c, "PIZZA and BIRD", cx, h * 0.295f + rise, Role.HERO, Type.INK)
        // 한글 타이틀 — 그 아래, 카라멜 색
        stickerAt(c, "피자와 새", cx, h * 0.415f + rise, 27f, Type.CARAMEL, Type.CREAM)
        c.restore()

        // 태그라인 + 반짝이 구분선
        val tagRise = (1f - tagK) * dp(10f)
        Type.text(c, "피자를 굽고, 자전거를 타고, 새를 찍는 힐링 여행", cx, h * 0.482f + tagRise, Role.CAPTION, Type.MUTED, 0.5f)
        UiKit.sparkle(c, cx - dp(48f), h * 0.524f, dp(8f), Color.argb(150, 226, 172, 60), t * 2.4f)
        UiKit.sparkle(c, cx, h * 0.522f, dp(11f), Color.argb(190, 226, 172, 60), t * 2.4f + 2.1f)
        UiKit.sparkle(c, cx + dp(48f), h * 0.524f, dp(8f), Color.argb(150, 226, 172, 60), t * 2.4f + 4.2f)

        // 시작 버튼 — 저장이 있으면 "시작하기 + 이어하기", 없으면 "시작하기" 하나만
        val bh = dp(46f)
        val byTop = h * 0.60f
        c.save()
        c.translate(cx, byTop + bh / 2)
        val bs = 0.8f + 0.2f * btnK
        c.scale(bs, bs)
        c.translate(-cx, -(byTop + bh / 2))
        val bRise = (1f - btnK) * dp(12f)
        if (hasSave) {
            val bw1 = dp(196f)
            val bw2 = dp(172f)
            val gap = dp(22f)
            val total = bw1 + gap + bw2
            startRect = RectF(cx - total / 2, byTop + bRise, cx - total / 2 + bw1, byTop + bh + bRise)
            contRect = RectF(cx - total / 2 + bw1 + gap, byTop + bRise, cx + total / 2, byTop + bh + bRise)
            UiKit.cuteButton(c, game, startRect, "시작하기", UiKit.GOLD, 0xFF3A2510.toInt(), 15.5f)
            UiKit.cuteButton(c, game, contRect, "이어하기", UiKit.CREAM, 0xFF5A422C.toInt(), 14.5f)
        } else {
            val bw1 = dp(210f)
            startRect = RectF(cx - bw1 / 2, byTop + bRise, cx + bw1 / 2, byTop + bh + bRise)
            contRect = RectF(0f, 0f, 0f, 0f)
            UiKit.cuteButton(c, game, startRect, "시작하기", UiKit.GOLD, 0xFF3A2510.toInt(), 15.5f)
        }
        c.restore()

        // 하단 정보
        val info = "v0.4.2 beta · 2K 렌더링 · 오프라인 · 한국 32곳 · 새 598종 · made with love & pizza"
        Type.text(c, info, cx, h - dp(10f), Role.CAPTION, Color.argb(175, 74, 55, 40), 0.5f)
    }

    /** 커스텀 크기 스티커 텍스트 (Type.sticker는 Role만 받으니 임의 크기용 헬퍼). */
    private fun stickerAt(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int, edgeColor: Int) {
        val p = Type.paintAt(sizeDp, true, 0.04f, color)
        val left = x - p.measureText(s) / 2f
        val off = sizeDp * game.density * 0.055f
        c.drawText(s, left + off, y + off, Type.paintAt(sizeDp, true, 0.04f, Type.DROP))
        // 테두리는 매 프레임 새로 만든다 — 캐시된 페인트의 스타일을 건드리면 다른 화면이 뒤틀린다
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Type.face(true)
            textSize = TypeScale.px(sizeDp * game.density)
            letterSpacing = 0.04f
            this.color = edgeColor
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = minOf(sizeDp * game.density * 0.11f, 3.2f * game.density)
        }
        c.drawText(s, left, y, edge)
        c.drawText(s, left, y, p)
    }

    private fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)
    private fun easeOutCubic(x: Float): Float {
        val u = 1f - x
        return 1f - u * u * u
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            when {
                contRect.contains(tap.x, tap.y) && game.state.started -> {
                    game.haptic()
                    continueGame()
                }
                startRect.contains(tap.x, tap.y) -> {
                    game.haptic()
                    game.fadeTo { game.scene = CharacterSelectScene(game) }
                }
            }
        }
        if (input.justA) {
            game.haptic()
            if (game.state.started) continueGame() else
                game.fadeTo { game.scene = CharacterSelectScene(game) }
        }
        if (input.justBack) {
            // 저장이 없어도 백 버튼으로 항상 나갈 수 있어야 한다
            game.openExitConfirm()
        }
    }

    private fun continueGame() {
        game.fadeTo {
            if (game.state.inHome) {
                game.scene = HomeScene(game)
            } else {
                game.scene = WorldScene(game, game.state.region, SpawnKind.SAVED)
            }
        }
    }
}

/** 스폰 위치 종류 */
enum class SpawnKind { SAVED, TUNNEL, HOME, LANDMARK }

/** 첫 플레이 시 아바타 선택 화면. 카드는 가상 캔버스(화면비 적응), 버튼은 실제 화면 좌표로 그린다. */
class CharacterSelectScene(game: Game) : Scene(game) {
    private val male: RectF
    private val female: RectF
    private var nextRect = RectF()
    private var backRect = RectF()
    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        // 카드 레이아웃은 가상 너비(화면비 적응) 중심으로 배치한다
        val cx = game.virtW / 2f
        male = RectF(cx - 300f, 245f, cx - 30f, 390f)
        female = RectF(cx + 30f, 245f, cx + 300f, 390f)
    }

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFFA4E4EE.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 이 화면은 가상 해상도 월드 캔버스에 그려지므로 px 단위로 지정한다 (가로는 화면비 적응)
        val cx = game.virtW / 2f
        val t1 = Type.paintPx(30f, true, 0.05f, Type.BROWN)
        val s1 = "여행할 캐릭터를 골라 주세요"
        c.drawText(s1, cx - t1.measureText(s1) / 2f, 115f, t1)
        fun card(r: RectF, label: String, selected: Boolean, bmp: android.graphics.Bitmap) {
            p.color = if (selected) 0xFFFFE0A3.toInt() else 0xFFF8EFDC.toInt()
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = if (selected) 5f else 2f
            p.color = 0xFF6B4F35.toInt()
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.FILL
            // 카드 미리보기는 고해상도(128px)를 64px 칸에 줄여 그린다 — 보간을 켜야 결이 산다
            p.isFilterBitmap = true
            c.drawBitmap(bmp, null, RectF(r.centerX()-32f, r.top+18f, r.centerX()+32f, r.top+82f), p)
            p.isFilterBitmap = false
            val lp = Type.paintPx(22f, true, 0.04f, Type.INK)
            c.drawText(label, r.centerX()-lp.measureText(label)/2f, r.bottom-25f, lp)
        }
        val t = game.time
        // 카드용 미리보기(정면 12프레임 · 128px) — 카드가 화면에서 3배로 그려져도 뭉개지지 않는다
        val maleIdle = game.assets.playerAvatarFrames("male", 0)
        val femaleIdle = game.assets.playerAvatarFrames("female", 0)
        card(male, "남자", game.state.gender == "male", maleIdle[((t / Anim.IDLE.frameTime).toInt() % maleIdle.size + maleIdle.size) % maleIdle.size])
        card(female, "여자", game.state.gender == "female", femaleIdle[(((t + 0.8f) / Anim.IDLE.frameTime).toInt() % femaleIdle.size + femaleIdle.size) % femaleIdle.size])
    }

    override fun drawHud(c: Canvas) {
        val d = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()
        val bw = minOf(d * 170f, w * 0.36f)
        val bh = d * 40f
        nextRect = RectF((w - bw) / 2f, h - d * 16f - bh, (w + bw) / 2f, h - d * 16f)
        backRect = RectF(d * 14f, d * 14f, d * 90f, d * 50f)
        UiKit.button(c, game, nextRect, "arrow_right 계속하기", 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 14f)
        UiKit.button(c, game, backRect, "arrow_left 뒤로", 0xFFF2E3C2.toInt(), 0xFF4A3728.toInt(), 12f)
    }

    override fun handleInput(input: Input) {
        if (input.justBack || input.justB) { game.fadeTo { game.scene = TitleScene(game) }; return }
        val tap = input.consumeTapScreen()
        if (tap != null) {
            if (backRect.contains(tap.x, tap.y)) {
                game.fadeTo { game.scene = TitleScene(game) }
                return
            }
            if (nextRect.contains(tap.x, tap.y)) {
                game.fadeTo { game.scene = RegionSelectScene(game) }
                return
            }
            val v = game.screenToVirtual(tap)
            when {
                male.contains(v.x, v.y) -> { game.state.gender = "male"; game.haptic() }
                female.contains(v.x, v.y) -> { game.state.gender = "female"; game.haptic() }
            }
        }
        if (input.justA) game.fadeTo { game.scene = RegionSelectScene(game) }
    }
}
