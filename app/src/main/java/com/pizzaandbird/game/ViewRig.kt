package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 몰입형 시점 리그 (Camera Movement / Motion).
 *
 * ※ 촬영 장비(바디·렌즈)를 다루는 `Cameras.kt`의 `CameraRig` 와는 다른 것이다.
 *   이쪽은 "화면이 어떻게 움직이는가"만 담당한다.
 *
 * 2D 탑다운 픽셀 화면에서 "그 공간에 실제로 있는 느낌"을 만들기 위한 다섯 가지 연출을 한곳에 모았다.
 *
 *  1) 카메라 셰이크   — 셔터·충돌·날갯짓·레벨업 같은 충격을 trauma(0~1)로 누적해 흔든다.
 *  2) 헤드 밥/바디 스웨이 — 걷기·달리기·자전거의 보폭 주기에 맞춰 화면이 위아래·좌우로 출렁인다.
 *  3) 다이내믹 시야각(FOV) — 달리거나 자전거를 타면 살짝 줌아웃(=시야 확대)해 속도감을,
 *                            카메라 모드에서는 등급만큼 줌인(=망원)해 집중감을 준다.
 *  4) 레이트 트래킹    — 임계 감쇠 스프링으로 살짝 늦게 따라오고, 진행 방향 앞쪽을 미리 보여준다.
 *  5) 다이내믹 포커싱  — (씬에서 사용) 초점 반경 밖을 어둡게 눌러 시선을 통제한다.
 *
 * 모든 연출은 설정(메뉴 › 설정 › 화면 연출)에서 끌 수 있다. 멀미 방지를 위한 필수 장치다.
 * 좌표 단위는 "월드 논리 px"(타일 16px)이며, 화면에는 WORLD_SCALE 배로 그려진다.
 */

/** 이동 상태 — 보폭 주기·시야각·예측 거리의 기준 */
enum class Gait { IDLE, WALK, RUN, BIKE }

/** 화면 연출 강도 설정 헬퍼 */
object CamFx {
    val SHAKE_LABELS = arrayOf("끔", "약하게", "보통", "강하게")
    private val SHAKE_MULT = floatArrayOf(0f, 0.55f, 1f, 1.6f)

    fun shakeMult(s: GameState): Float = SHAKE_MULT[s.camShake.coerceIn(0, SHAKE_MULT.size - 1)]
    fun shakeLabel(s: GameState): String = SHAKE_LABELS[s.camShake.coerceIn(0, SHAKE_LABELS.size - 1)]
    fun onOff(v: Boolean): String = if (v) "켬" else "끔"

    /** 프레임 독립 보간 계수 (tau = 목표에 63% 다가가는 시간) */
    fun smoothK(dt: Float, tau: Float): Float =
        if (tau <= 0.0001f) 1f else (1f - exp(-dt / tau)).coerceIn(0f, 1f)

    /**
     * 부드러운 의사 노이즈 (-1~1).
     * 난수를 그대로 쓰면 화면이 '지직'거리므로 주파수가 다른 사인파를 겹쳐 물결처럼 흔든다.
     */
    fun noise(t: Float, seed: Float): Float =
        sin(t * 1.93f + seed * 7.13f) * 0.55f +
            sin(t * 4.71f + seed * 3.31f) * 0.31f +
            sin(t * 9.37f + seed * 11.7f) * 0.14f
}

/** 임계 감쇠 스무스댐프 1축 — 오버슈트 없이 목표를 부드럽게 따라간다 (레이트 트래킹용) */
private class Smooth1D {
    var pos = 0f
    var vel = 0f

    fun snap(v: Float) {
        pos = v
        vel = 0f
    }

    fun step(target: Float, smoothTime: Float, dt: Float) {
        val st = max(0.0001f, smoothTime)
        val omega = 2f / st
        val xv = omega * dt
        val e = 1f / (1f + xv + 0.48f * xv * xv + 0.235f * xv * xv * xv)
        val change = pos - target
        val temp = (vel + omega * change) * dt
        vel = (vel - omega * temp) * e
        pos = target + (change + temp) * e
    }
}

/**
 * 감쇠 스프링 임펄스 — 한 번 '툭' 밀렸다가 제자리로 돌아온다 (킥 / 줌 펀치용).
 *
 * 오일러 적분은 프레임이 흔들리면 진폭까지 달라지므로 **감쇠 진동의 해석해**로 적분한다.
 * 덕분에 30fps로 떨어져도 60fps와 똑같은 크기로 튄다.
 */
private class Impulse(k: Float, c: Float) {
    var v = 0f
    var dv = 0f

    private val w = sqrt(k)
    private val z = (c / (2f * sqrt(k))).coerceIn(0f, 0.999f)
    private val wd = sqrt(k) * sqrt(1f - (c / (2f * sqrt(k))).coerceIn(0f, 0.999f).let { it * it })

    /** hit(peak)가 실제로 peak 만큼 튀도록 임펄스 응답의 최댓값으로 정규화해 둔다 */
    private val gain: Float

    init {
        val tPeak = atan2(sqrt(1f - z * z), z) / wd
        val peak = (1f / wd) * exp(-z * w * tPeak) * sin(wd * tPeak)
        gain = if (peak > 0.0001f) 1f / peak else 1f
    }

    fun hit(peak: Float) {
        dv += peak * gain
    }

    fun step(dt: Float) {
        if (v == 0f && dv == 0f) return
        val e = exp(-z * w * dt)
        val cs = cos(wd * dt)
        val sn = sin(wd * dt)
        val x0 = v
        val v0 = dv
        v = e * (x0 * cs + (v0 + z * w * x0) / wd * sn)
        dv = e * (v0 * cs - (w * w * x0 + z * w * v0) / wd * sn)
        if (abs(v) < 0.0004f && abs(dv) < 0.0004f) {
            v = 0f
            dv = 0f
        }
    }

    fun reset() {
        v = 0f
        dv = 0f
    }
}

class ViewRig(private val state: GameState) {

    // ---------- 렌더러가 읽는 결과값 ----------
    /** 뷰포트 좌상단 (월드 논리 px) */
    var x = 0f
        private set
    var y = 0f
        private set

    /** 현재 시야각 배율 (1 = 기본, <1 줌아웃/광각, >1 줌인/망원) */
    var zoom = 1f
        private set

    /** 화면 기울기(도) — 큰 충격에만 아주 살짝 */
    var roll = 0f
        private set

    /** 현재 뷰포트 크기 (월드 논리 px) */
    var viewW = 0f
        private set
    var viewH = 0f
        private set

    /** 셔터 플래시 0~1 */
    var flash = 0f
        private set

    /** 속도 연출(잔상·속도선) 강도 0~1 */
    var speedFx = 0f
        private set

    /** 남은 히트스톱(초) — 씬이 이 값으로 월드 dt를 늦춘다 */
    var hitstop = 0f
        private set

    /** 이번 프레임에 발이 땅에 닿았는가 (발먼지·발소리용) */
    var stepped = false
        private set

    /** 헤드밥 위상 (0~2π) — 스프라이트 연출에도 쓸 수 있다 */
    var bobPhase = 0f
        private set

    // ---------- 내부 상태 ----------
    private val trackX = Smooth1D()
    private val trackY = Smooth1D()
    private val kickX = Impulse(230f, 17f)
    private val kickY = Impulse(230f, 17f)
    private val punch = Impulse(190f, 13f)

    private var leadX = 0f
    private var leadY = 0f
    private var trauma = 0f
    private var shakeT = 0f
    private var bobAmt = 0f
    private var zoomBase = 1f
    private var t = 0f
    private var stepIdx = 0

    /** 마지막으로 계산한 흔들림/밥 오프셋 (디버그·잔상 계산용) */
    var fxOffX = 0f
        private set
    var fxOffY = 0f
        private set

    // ---------- 충격 입력 API ----------

    /** 충격 누적 (0~1). trauma²로 감쇠해 작은 충격은 은은하게, 큰 충격은 확실하게 흔들린다. */
    fun shake(amount: Float) {
        if (state.camShake <= 0) return
        trauma = (trauma + amount).coerceIn(0f, 1f)
    }

    /** 방향성 킥 — 충격 방향으로 화면을 peak(논리 px)만큼 밀었다가 되돌린다. */
    fun kick(dirX: Float, dirY: Float, peakPx: Float, respectSetting: Boolean = true) {
        val len = hypot(dirX, dirY)
        if (len < 0.0001f) return
        val m = if (respectSetting) CamFx.shakeMult(state) else 1f
        if (m <= 0f) return
        kickX.hit(dirX / len * peakPx * m)
        kickY.hit(dirY / len * peakPx * m)
    }

    /** 시야각 펀치 (+ 확대 / - 축소). 셔터·레벨업 같은 순간 강조용. */
    fun punchZoom(amount: Float) {
        if (!state.camFov) return
        punch.hit(amount)
    }

    /** 화이트 플래시 (셔터) */
    fun flashScreen(amount: Float) {
        flash = max(flash, amount.coerceIn(0f, 1f))
    }

    /** 히트스톱 — 결정적 순간에 세상을 아주 잠깐 멈춘다 */
    fun freeze(sec: Float) {
        hitstop = max(hitstop, sec)
    }

    // ---------- 갱신 ----------

    /**
     * @param focusX/focusY 따라갈 지점 (보통 플레이어 중심, 카메라 모드에서는 피사체 쪽으로 치우친 점)
     * @param velX/velY     실제 이동 속도 (논리 px/s) — 예측 배치와 속도 연출에 쓰인다
     * @param teleZoom      카메라 모드 망원 배율 (1 = 평상시)
     * @param baseViewW/H   줌 1일 때의 뷰포트 크기 (논리 px)
     */
    fun update(
        dt: Float,
        focusX: Float,
        focusY: Float,
        velX: Float,
        velY: Float,
        gait: Gait,
        teleZoom: Float,
        mapW: Float,
        mapH: Float,
        baseViewW: Float,
        baseViewH: Float,
        allowRoll: Boolean = true
    ) {
        t += dt
        stepped = false
        if (hitstop > 0f) hitstop = max(0f, hitstop - dt)
        if (flash > 0f) flash = max(0f, flash - dt * 6.4f)

        val speed = hypot(velX, velY)
        val ref = refSpeed(gait)

        // ---------- 1) 다이내믹 시야각 (FOV) ----------
        var zTarget = 1f
        if (state.camFov) {
            zTarget = when (gait) {
                Gait.BIKE -> 0.925f          // 자전거: 시야가 넓어지며 풍경이 뒤로 밀린다
                Gait.RUN -> 0.955f           // 달리기: 살짝 광각
                else -> 1f
            }
            zTarget *= teleZoom              // 카메라 모드: 망원으로 좁고 깊게
        }
        zoomBase += (zTarget - zoomBase) * CamFx.smoothK(dt, if (teleZoom > 1.001f) 0.24f else 0.40f)
        punch.step(dt)
        zoom = (zoomBase * (1f + punch.v)).coerceIn(0.7f, 2.2f)

        // ---------- 2) 카메라 셰이크 ----------
        // trauma^1.5 — 작은 충격은 은은하게, 큰 충격은 확실하게 (선형보다 덜 산만하다)
        trauma = max(0f, trauma - dt * 1.4f)
        shakeT += dt
        val sMul = CamFx.shakeMult(state)
        val s = trauma * sqrt(trauma) * sMul
        var shX = 0f
        var shY = 0f
        if (s > 0.0001f) {
            shX = CamFx.noise(shakeT * 13.5f, 1.7f) * 16f * s
            shY = CamFx.noise(shakeT * 12.1f, 5.3f) * 14f * s
            roll = if (allowRoll) CamFx.noise(shakeT * 9.4f, 9.1f) * 1.4f * s else 0f
        } else {
            roll = 0f
        }
        kickX.step(dt)
        kickY.step(dt)

        // ---------- 3) 헤드 밥 & 바디 스웨이 ----------
        val moving = gait != Gait.IDLE && speed > 1f
        val speedK = if (ref > 0f) (speed / ref).coerceIn(0f, 1.5f) else 0f
        val strideHz = when (gait) {
            Gait.RUN -> 2.85f
            Gait.WALK -> 2.05f
            Gait.BIKE -> 1.45f
            else -> 0f
        } * (0.78f + 0.22f * speedK)

        val ampTarget = if (!state.camBob || !moving) 0f else when (gait) {
            Gait.RUN -> 1f
            Gait.BIKE -> 0.8f
            else -> 0.62f
        }
        bobAmt += (ampTarget - bobAmt) * CamFx.smoothK(dt, 0.18f)
        if (moving) bobPhase = (bobPhase + TAU * strideHz * dt) % TAU

        // 한 걸음(=위상 π)마다 발이 땅에 닿는다 → 발먼지/미세 흔들림 트리거
        val idx = (bobPhase / PI_F).toInt()
        if (idx != stepIdx) {
            stepIdx = idx
            if (moving && bobAmt > 0.25f) {
                stepped = true
                // 착지 충격: 화면이 아주 살짝 내려앉는다
                if (state.camBob) {
                    val dip = when (gait) {
                        Gait.RUN -> 0.30f
                        Gait.BIKE -> 0.10f
                        else -> 0.14f
                    }
                    kickY.hit(dip)
                }
            }
        }

        val bobV = -abs(sin(bobPhase)) * bobVertAmp(gait) * bobAmt
        val bobH = sin(bobPhase * 0.5f) * bobHorzAmp(gait) * bobAmt
        // 자전거 노면 진동 + 정지 중 숨결(화면이 완전히 굳어 보이지 않게)
        val rumble = if (gait == Gait.BIKE && state.camBob) CamFx.noise(t * 26f, 3.3f) * 0.5f * bobAmt else 0f
        val breath = if (state.camBob) sin(t * 0.85f) * 0.45f * (1f - bobAmt) else 0f

        // ---------- 4) 레이트 트래킹 + 예측 배치 ----------
        val leadDist = if (!state.camLead) 0f else when (gait) {
            Gait.BIKE -> 34f
            Gait.RUN -> 22f
            Gait.WALK -> 14f
            else -> 0f
        }
        var ltx = 0f
        var lty = 0f
        if (speed > 1f && leadDist > 0f) {
            val k = speedK.coerceAtMost(1f)
            ltx = velX / speed * leadDist * k
            lty = velY / speed * leadDist * k * 0.72f    // 화면이 가로로 넓어 세로는 조금 덜
        }
        leadX += (ltx - leadX) * CamFx.smoothK(dt, 0.55f)
        leadY += (lty - leadY) * CamFx.smoothK(dt, 0.55f)

        var tgtX = focusX + leadX
        var tgtY = focusY + leadY
        // 데드존 — 미세한 흔들림에는 카메라가 반응하지 않는다
        val ddx = tgtX - trackX.pos
        val ddy = tgtY - trackY.pos
        val dd = hypot(ddx, ddy)
        val dz = 2.2f
        if (dd <= dz) {
            tgtX = trackX.pos
            tgtY = trackY.pos
        } else {
            val f = (dd - dz) / dd
            tgtX = trackX.pos + ddx * f
            tgtY = trackY.pos + ddy * f
        }
        val smoothTime = when (gait) {
            Gait.BIKE -> 0.19f
            Gait.RUN -> 0.22f
            else -> 0.26f
        }
        trackX.step(tgtX, smoothTime, dt)
        trackY.step(tgtY, smoothTime, dt)

        // ---------- 5) 속도 연출 강도 (잔상/속도선) ----------
        val fxBase = when (gait) {
            Gait.BIKE -> 1f
            Gait.RUN -> 0.75f
            else -> 0f
        }
        val fxTarget = if (!state.camBlur) 0f else (fxBase * ((speedK - 0.5f) / 0.5f).coerceIn(0f, 1f))
        speedFx += (fxTarget - speedFx) * CamFx.smoothK(dt, 0.25f)

        // ---------- 최종 합성 ----------
        fxOffX = shX + kickX.v + bobH
        fxOffY = shY + kickY.v + bobV + rumble + breath
        viewW = baseViewW / zoom
        viewH = baseViewH / zoom
        x = place(trackX.pos - viewW / 2f, fxOffX, mapW, viewW)
        y = place(trackY.pos - viewH / 2f, fxOffY, mapH, viewH)
    }

    /** 씬 시작/순간이동: 보간 없이 즉시 맞춘다 */
    fun snap(
        focusX: Float,
        focusY: Float,
        teleZoom: Float,
        mapW: Float,
        mapH: Float,
        baseViewW: Float,
        baseViewH: Float
    ) {
        trackX.snap(focusX)
        trackY.snap(focusY)
        leadX = 0f
        leadY = 0f
        trauma = 0f
        roll = 0f
        bobAmt = 0f
        kickX.reset()
        kickY.reset()
        punch.reset()
        zoomBase = if (state.camFov) teleZoom else 1f
        zoom = zoomBase
        viewW = baseViewW / zoom
        viewH = baseViewH / zoom
        fxOffX = 0f
        fxOffY = 0f
        x = place(focusX - viewW / 2f, 0f, mapW, viewW)
        y = place(focusY - viewH / 2f, 0f, mapH, viewH)
    }

    /** 히트스톱을 반영한 월드 dt */
    fun worldDt(dt: Float): Float = if (hitstop > 0f) dt * 0.12f else dt

    /**
     * 맵 경계 처리.
     * 맵이 화면보다 크면 경계 밖이 보이지 않게 잠그고(가장자리에선 흔들림이 자연히 줄어든다),
     * 맵이 화면보다 작으면(집 안 등) 가운데 고정 + 흔들림만 그대로 살린다.
     */
    private fun place(base: Float, fx: Float, mapSize: Float, view: Float): Float =
        if (mapSize <= view) (mapSize - view) / 2f + fx
        else (base + fx).coerceIn(0f, mapSize - view)

    private fun refSpeed(gait: Gait): Float = when (gait) {
        Gait.BIKE -> 97f
        Gait.RUN -> 80f
        Gait.WALK -> 55f
        else -> 55f
    }

    private fun bobVertAmp(gait: Gait): Float = when (gait) {
        Gait.RUN -> 2.35f
        Gait.BIKE -> 0.85f
        else -> 1.25f
    }

    private fun bobHorzAmp(gait: Gait): Float = when (gait) {
        Gait.RUN -> 1.35f
        Gait.BIKE -> 1.9f            // 자전거는 좌우로 기우뚱하는 느낌이 크다
        else -> 0.85f
    }

    companion object {
        private const val PI_F = 3.1415927f
        private const val TAU = 6.2831855f
    }
}

// ---------------------------------------------------------------------------
// 모션 블러 대용 — 속도선 (스크린 공간)
// ---------------------------------------------------------------------------

/**
 * 고속 이동 시 시야 주변으로 흐르는 잔상 선.
 * 픽셀 아트에서 진짜 블러(프레임 누적)는 비싸고 지저분하므로,
 * '시각 잔상'을 선으로 표현해 같은 인상을 아주 싸게 만든다.
 */
class SpeedStreaks(seed: Long = 20250927L) {

    private class S(var x: Float, var y: Float, var life: Float, var max: Float, var w: Float, var len: Float)

    private val list = ArrayList<S>()
    private val rnd = Random(seed)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var acc = 0f

    fun update(dt: Float, dirX: Float, dirY: Float, intensity: Float, vw: Float, vh: Float) {
        val len = hypot(dirX, dirY)
        if (intensity > 0.02f && len > 0.001f) {
            val nx = dirX / len
            val ny = dirY / len
            acc += dt * (8f + 52f * intensity)
            while (acc >= 1f && list.size < 34) {
                acc -= 1f
                // 진행 방향 쪽 화면 가장자리에서 태어나 뒤로 흘러간다
                val edge = 0.62f + rnd.nextFloat() * 0.5f
                val ang = rnd.nextFloat() * 6.2831855f
                val px = vw / 2f + sin(ang) * vw * 0.5f * edge + nx * vw * 0.18f
                val py = vh / 2f + sin(ang + 1.57f) * vh * 0.5f * edge + ny * vh * 0.18f
                val life = 0.20f + rnd.nextFloat() * 0.18f
                list.add(
                    S(
                        px.coerceIn(-40f, vw + 40f), py.coerceIn(-40f, vh + 40f),
                        life, life,
                        1f + rnd.nextFloat() * 1.6f,
                        26f + rnd.nextFloat() * 54f * (0.4f + intensity)
                    )
                )
            }
        }
        val flowX = -dirX * (0.9f + 1.1f * intensity)
        val flowY = -dirY * (0.9f + 1.1f * intensity)
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += flowX * dt
            p.y += flowY * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }

    fun draw(c: Canvas, dirX: Float, dirY: Float, intensity: Float) {
        if (list.isEmpty() || intensity <= 0.02f) return
        val len = hypot(dirX, dirY)
        if (len < 0.001f) return
        val nx = dirX / len
        val ny = dirY / len
        for (p in list) {
            val k = (p.life / p.max).coerceIn(0f, 1f)
            val a = (96f * intensity * k).toInt().coerceIn(0, 255)
            if (a <= 2) continue
            paint.color = Color.argb(a, 255, 253, 246)
            paint.strokeWidth = p.w
            val l = p.len * (0.5f + 0.5f * intensity)
            c.drawLine(p.x, p.y, p.x - nx * l, p.y - ny * l, paint)
        }
    }

    fun clear() = list.clear()
}

// ---------------------------------------------------------------------------
// 다이내믹 포커싱 / 비네트 — 방사형 마스크
// ---------------------------------------------------------------------------

/**
 * 한 번의 drawRect로 "가운데는 또렷, 바깥은 어둡게"를 그린다.
 * 진짜 블러 대신 심도(DOF)의 인상을 만드는 값싼 방법 —
 * 초점 대상만 밝게 남겨 플레이어의 시선을 통제한다.
 */
class RadialMask {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var shader: RadialGradient? = null
    private var keyInner = 0
    private var keyOuter = 0
    private var keyStop = -1f

    fun draw(
        c: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        w: Float,
        h: Float,
        innerColor: Int,
        outerColor: Int,
        innerStop: Float
    ) {
        if (radius <= 1f || Color.alpha(outerColor) <= 0) return
        var sh = shader
        if (sh == null || keyInner != innerColor || keyOuter != outerColor || keyStop != innerStop) {
            sh = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(innerColor, innerColor, outerColor),
                floatArrayOf(0f, innerStop.coerceIn(0.01f, 0.95f), 1f),
                Shader.TileMode.CLAMP
            )
            shader = sh
            keyInner = innerColor
            keyOuter = outerColor
            keyStop = innerStop
        }
        matrix.reset()
        matrix.setScale(radius, radius)
        matrix.postTranslate(cx, cy)
        sh.setLocalMatrix(matrix)
        paint.shader = sh
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }
}

/**
 * 화면 가장자리를 눌러 속도감을 더하는 비네트 (달리기/자전거).
 * 이동 중에는 매 프레임 그려지므로 그라디언트 대신 값싼 띠(band) 여러 겹으로 만든다.
 */
class SpeedVignette {
    private val p = Paint()

    fun draw(c: Canvas, w: Float, h: Float, strength: Float) {
        if (strength <= 0.02f) return
        val bands = 5
        val depthX = w * 0.11f * (0.6f + 0.4f * strength)
        val depthY = h * 0.13f * (0.6f + 0.4f * strength)
        for (i in 0 until bands) {
            val k = (i + 1f) / bands
            val a = (30f * strength * k * k).toInt().coerceIn(0, 255)
            if (a <= 1) continue
            p.color = Color.argb(a, 8, 8, 16)
            val bx = depthX * (1f - i / bands.toFloat())
            val by = depthY * (1f - i / bands.toFloat())
            c.drawRect(0f, 0f, bx, h, p)
            c.drawRect(w - bx, 0f, w, h, p)
            c.drawRect(bx, 0f, w - bx, by, p)
            c.drawRect(bx, h - by, w - bx, h, p)
        }
    }
}
