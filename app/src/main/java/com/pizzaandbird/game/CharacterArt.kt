package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 캐릭터 골격(관절) 애니메이션 — 32x32 픽셀 아트를 코드로 생성한다.
 *
 * 프레임마다 좌표를 손으로 박아 넣는 대신 **관절 각도(포즈)** 를 계산해서 그리기 때문에
 * 손·발·머리·머리카락·옷자락까지 자연스럽게 따로 움직인다.
 *
 * - 각도 단위는 도(°). 0° = 아래쪽, + = 캐릭터가 바라보는 앞쪽.
 * - 그리기 원시요소는 drawRect / drawCircle / drawOval / drawPath 네 가지뿐이라
 *   `tools/preview/people.py` 미리보기와 픽셀 단위로 같은 그림이 나온다.
 */
object CharacterArt {

    const val SIZE = 32

    /**
     * 앉은 자세는 엉덩이를 좌석 높이로 올리므로 머리·모자가 프레임 위로 넘친다.
     * `render(..., topPad = SIT_TOPPAD)` 로 상단 여백을 두고 그릴 때 y 에서 이만큼 뺀다.
     */
    const val SIT_TOPPAD = 9

    // 방향
    const val FRONT = 0
    const val BACK = 1
    const val SIDE = 2          // 오른쪽을 바라봄

    // 손에 든 것
    const val HOLD_NONE = 0
    const val HOLD_CAMERA = 1

    // 걷기 계열 스타일
    const val WALK = 0
    const val RUN = 1
    const val SNEAK = 2

    // NPC 성격별 대기 동작
    const val NPC_PROFESSOR = 0
    const val NPC_SHOP = 1
    const val NPC_VILLAGER = 2
    const val NPC_KID = 3
    const val NPC_ELDER = 4

    private const val TAU = (PI * 2).toFloat()

    fun shade(color: Int, f: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    // -----------------------------------------------------------------------
    // 배색 / 외형
    // -----------------------------------------------------------------------

    data class Pal(
        val hair: Int, val hair2: Int, val skin: Int, val skin2: Int,
        val top: Int, val top2: Int, val pants: Int, val pants2: Int,
        val shoe: Int, val line: Int, val pack: Int, val pack2: Int,
        val eye: Int, val blush: Int
    )

    /** 탐조가 장비 (레벨 등급에 따라 겉모습이 좋아진다) */
    class Gear(
        val cap: Int? = null,
        val capDark: Int = 0,
        val vest: Int? = null,
        val vestDark: Int = 0,
        val scarf: Int? = null,
        val brim: Boolean = false,
        val feather: Int? = null
    )

    class Look(
        val pal: Pal,
        val gear: Gear? = null,
        val glasses: Boolean = false,
        val apron: Boolean = false,
        val cane: Boolean = false,
        val small: Boolean = false,
        val longHair: Boolean = false,
        val pack: Boolean = true
    )

    /** 관절 포즈 — 모든 각도는 도(°) */
    data class Pose(
        val bodyY: Float = 0f, val bodyX: Float = 0f,
        val lean: Float = 0f, val crouch: Float = 0f,
        val hipL: Float = 0f, val kneeL: Float = 0f, val footL: Float = 0f,
        val hipR: Float = 0f, val kneeR: Float = 0f, val footR: Float = 0f,
        val armL: Float = 0f, val elbowL: Float = 8f,
        val armR: Float = 0f, val elbowR: Float = 8f,
        val shoulderL: Float = 0f, val shoulderR: Float = 0f,
        val headY: Float = 0f, val headX: Float = 0f,
        val tilt: Float = 0f, val turn: Float = 0f,
        val blink: Float = 0f, val mouth: Float = 0f,
        val brow: Float = 0f, val breath: Float = 0f,
        val hairSway: Float = 0f, val clothSway: Float = 0f, val packBob: Float = 0f,
        val hold: Int = HOLD_NONE, val holdT: Float = 0f
    )

    // -----------------------------------------------------------------------
    // 그리기 헬퍼
    // -----------------------------------------------------------------------

    private class G(val cv: Canvas) {
        val p = Paint()
        val path = Path()
        val rf = RectF()

        fun rect(l: Float, t: Float, r: Float, b: Float, col: Int) {
            p.color = col; cv.drawRect(l, t, r, b, p)
        }

        fun circ(x: Float, y: Float, rad: Float, col: Int) {
            p.color = col; cv.drawCircle(x, y, rad, p)
        }

        fun oval(l: Float, t: Float, r: Float, b: Float, col: Int) {
            p.color = col; rf.set(l, t, r, b); cv.drawOval(rf, p)
        }

        fun poly(col: Int, vararg pts: Float) {
            p.color = col
            path.reset()
            path.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) {
                path.lineTo(pts[i], pts[i + 1]); i += 2
            }
            path.close()
            cv.drawPath(path, p)
        }

        fun rrect(l: Float, t: Float, r: Float, b: Float, radIn: Float, col: Int) {
            val rad = min(radIn, min((r - l) / 2f, (b - t) / 2f))
            rect(l + rad, t, r - rad, b, col)
            rect(l, t + rad, r, b - rad, col)
            circ(l + rad, t + rad, rad, col)
            circ(r - rad, t + rad, rad, col)
            circ(l + rad, b - rad, rad, col)
            circ(r - rad, b - rad, rad, col)
        }

        /** 관절 마디 — 끝이 둥근 사다리꼴 */
        fun seg(x0: Float, y0: Float, x1: Float, y1: Float, w0: Float, w1: Float, col: Int) {
            val dx = x1 - x0
            val dy = y1 - y0
            val ln = hypot(dx, dy)
            if (ln < 0.001f) {
                circ(x0, y0, w0, col); return
            }
            val nx = -dy / ln
            val ny = dx / ln
            poly(
                col,
                x0 + nx * w0, y0 + ny * w0,
                x1 + nx * w1, y1 + ny * w1,
                x1 - nx * w1, y1 - ny * w1,
                x0 - nx * w0, y0 - ny * w0
            )
            circ(x0, y0, w0, col)
            circ(x1, y1, w1, col)
        }
    }

    private fun fkX(x: Float, ang: Float, len: Float, depth: Float): Float =
        x + sin(ang * PI.toFloat() / 180f) * len * depth

    private fun fkY(y: Float, ang: Float, len: Float): Float =
        y + cos(ang * PI.toFloat() / 180f) * len

    /** 2관절 IK — 뿌리에서 목표까지, 무릎/팔꿈치 좌표 (sign 으로 굽는 방향 선택) */
    private fun ikX(hx: Float, hy: Float, tx: Float, ty: Float, l1: Float, l2: Float, sign: Float): Float {
        val dx = tx - hx; val dy = ty - hy
        val d = min(max(hypot(dx, dy), 0.001f), l1 + l2 - 0.02f)
        val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val h = sqrt(max(0f, l1 * l1 - a * a))
        val ux = dx / d; val uy = dy / d
        return hx + ux * a + (-uy) * h * sign
    }

    private fun ikY(hx: Float, hy: Float, tx: Float, ty: Float, l1: Float, l2: Float, sign: Float): Float {
        val dx = tx - hx; val dy = ty - hy
        val d = min(max(hypot(dx, dy), 0.001f), l1 + l2 - 0.02f)
        val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val h = sqrt(max(0f, l1 * l1 - a * a))
        val ux = dx / d; val uy = dy / d
        return hy + uy * a + ux * h * sign
    }

    // -----------------------------------------------------------------------
    // 포즈 생성
    // -----------------------------------------------------------------------

    /** a~b 구간에서 0 -> 1 -> 0 으로 솟는 함수 */
    private fun bump(x: Float, a: Float, b: Float): Float {
        if (x < a || x > b) return 0f
        val t = (x - a) / (b - a)
        val s = sin(PI.toFloat() * t)
        return s * s
    }

    /** 한쪽 다리 위상 p(0=접지, 0.5=발끝 떼기) */
    private fun legHip(p: Float) = cos(TAU * p)

    private fun legKnee(p: Float): Float {
        val swing = max(0f, sin(TAU * (p - 0.5f)))
        val stance = max(0f, sin(TAU * p)) * 0.18f
        return Math.pow(swing.toDouble(), 1.15).toFloat() + stance
    }

    private fun legFoot(p: Float): Float =
        -cos(TAU * p) + 1.15f * max(0f, sin(TAU * (p - 0.25f)))

    fun walkPose(phase: Float, style: Int = WALK): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val amp: Float; val kneeAmp: Float; val armAmp: Float
        val elbow: Float; val lean: Float; val bounce: Float
        val crouch: Float; val headAmp: Float
        when (style) {
            RUN -> {
                amp = 40f; kneeAmp = 76f; armAmp = 34f; elbow = 62f
                lean = 10f; bounce = 1.15f; crouch = 0f; headAmp = 0.5f
            }
            SNEAK -> {
                amp = 18f; kneeAmp = 30f; armAmp = 10f; elbow = 46f
                lean = 15f; bounce = 0.34f; crouch = 1f; headAmp = 0.24f
            }
            else -> {
                amp = 27f; kneeAmp = 44f; armAmp = 20f; elbow = 16f
                lean = 2.5f; bounce = 0.72f; crouch = 0f; headAmp = 0.34f
            }
        }
        val pL = (p + 0.5f) % 1f
        val swingSign = cos(TAU * p)
        return Pose(
            bodyY = bounce * cos(2f * TAU * p),
            bodyX = 0.35f * sin(TAU * p) * (if (style == SNEAK) 0.4f else 1f),
            lean = lean,
            crouch = crouch,
            hipR = amp * legHip(p), kneeR = kneeAmp * legKnee(p), footR = 14f * legFoot(p),
            hipL = amp * legHip(pL), kneeL = kneeAmp * legKnee(pL), footL = 14f * legFoot(pL),
            armR = -armAmp * swingSign, elbowR = elbow + 10f * max(0f, -swingSign),
            armL = armAmp * swingSign, elbowL = elbow + 10f * max(0f, swingSign),
            shoulderR = -0.35f * swingSign, shoulderL = 0.35f * swingSign,
            headY = headAmp * cos(2f * TAU * (p - 0.07f)),
            headX = 0.3f * sin(TAU * p),
            tilt = 1.2f * sin(TAU * p),
            breath = 0.35f + 0.35f * sin(2f * TAU * p),
            mouth = if (style == RUN) 0.75f else 0f,
            brow = if (style == RUN) 0.6f else if (style == SNEAK) 0.4f else 0f,
            hairSway = -1.1f * sin(TAU * (p - 0.12f)) - (if (style == RUN) 0.55f else 0f),
            clothSway = -0.9f * sin(TAU * (p - 0.18f)),
            packBob = 0.5f * cos(2f * TAU * (p - 0.12f))
        )
    }

    /** 서 있기 — 숨쉬기 · 무게중심 이동 · 눈 깜빡임 · 두리번거리기 */
    fun idlePose(phase: Float): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val breath = 0.5f - 0.5f * cos(2f * TAU * p)
        val look = bump(p, 0.30f, 0.56f)
        val shift = sin(TAU * p)
        return Pose(
            bodyY = -0.35f * breath,
            bodyX = 0.4f * shift,
            hipR = 1.5f * shift, hipL = -1.5f * shift,
            kneeR = 3f + 2f * max(0f, shift), kneeL = 3f + 2f * max(0f, -shift),
            armR = 2.5f * shift - 1.2f * breath, elbowR = 7f + 3f * breath,
            armL = -2.5f * shift + 1.2f * breath, elbowL = 7f + 3f * breath,
            shoulderR = -0.45f * breath, shoulderL = -0.45f * breath,
            headY = -0.3f * breath + 0.25f * shift,
            headX = 0.5f * shift + 0.7f * look,
            tilt = -1.6f * shift,
            turn = 0.85f * look,
            blink = if (p >= 0.845f && p < 0.885f) 1f else if (p >= 0.885f && p < 0.905f) 0.5f else 0f,
            breath = breath,
            brow = 0.35f * look,
            hairSway = 0.5f * shift,
            clothSway = 0.4f * shift,
            packBob = -0.3f * breath
        )
    }

    /**
     * 벤치에 앉기 — 허리를 펴고 앉아 숨쉬며 두리번거린다.
     *
     * 앉으면 엉덩이가 좌석 높이(타일 아트 y≈16)로 올라가므로 머리·모자가 프레임 위로
     * 넘친다. 그래서 `render(..., topPad = SIT_TOPPAD)` 로 상단 여백을 두고,
     * 그릴 때 y 에서 SIT_TOPPAD 만큼 뺀다 (WorldScene/참조: Anim.SIT).
     * 정면 기준: 허벅지는 앞으로 벌리고 무릎 아래로 종아리가 살짝 흔들리며 내려간다.
     */
    fun sitPose(phase: Float): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val breath = 0.5f - 0.5f * cos(TAU * p)
        val look = bump(p, 0.30f, 0.56f)
        val shift = sin(TAU * p)
        val settle = (p * 8f).coerceAtMost(1f)      // 앉은 직후 1/8 사이클만 자세를 고정
        return Pose(
            bodyY = -5.2f - 0.22f * breath + 0.6f * (1f - settle),
            bodyX = 0.22f * shift,
            lean = -1.5f,
            // 허벅지는 앞으로(무릎 갈림), 종아리는 살짝 앞으로 내려가며 흔들
            hipR = 88f + 1.5f * shift, kneeR = 76f, footR = 2f,
            hipL = -88f + 1.5f * shift, kneeL = -76f, footL = -2f,
            armR = 34f + 2f * shift, elbowR = 46f,
            armL = -34f + 2f * shift, elbowL = 46f,
            shoulderR = -0.2f * breath, shoulderL = -0.2f * breath,
            headY = -0.2f * breath + 0.3f * (1f - settle),
            headX = 0.55f * shift + 0.7f * look,
            tilt = -1.4f * shift,
            turn = 0.8f * look,
            blink = if (p >= 0.845f && p < 0.885f) 1f else if (p >= 0.885f && p < 0.905f) 0.5f else 0f,
            breath = breath,
            brow = 0.3f * look,
            hairSway = 0.4f * shift,
            clothSway = 0.35f * shift,
            packBob = -0.25f * breath
        )
    }

    /** 카메라 조준 — 숨죽이고 미세하게 흔들린다 */
    fun aimPose(phase: Float): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val breath = 0.5f - 0.5f * cos(TAU * p)
        return Pose(
            bodyY = -0.25f * breath,
            bodyX = 0.18f * sin(TAU * p),
            lean = 4f,
            crouch = 0.25f,
            hipR = 6f, kneeR = 9f,
            hipL = -7f, kneeL = 13f, footL = -4f,
            armR = 132f + 3f * breath, elbowR = 108f,
            armL = -128f - 3f * breath, elbowL = 104f,
            shoulderR = -0.9f, shoulderL = -0.9f,
            headY = 0.35f - 0.2f * breath,
            headX = 0.2f * sin(TAU * p),
            tilt = 0.6f,
            blink = 0.85f,
            breath = breath,
            brow = 1f,
            hold = HOLD_CAMERA,
            holdT = breath
        )
    }

    /**
     * 펀치 — 팔을 뒤로 뺐다가 바라보는 쪽으로 쭉 뻗는다.
     * phase 0 와인드업, 0.5 근처가 타점, 이후 따라가기.
     */
    fun punchPose(phase: Float): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val windup = when {
            p < 0.20f -> 1f - p / 0.20f * 0.15f
            p < 0.36f -> 0.85f * (1f - (p - 0.20f) / 0.16f)
            else -> 0f
        }.coerceIn(0f, 1f)
        val extend = when {
            p < 0.12f -> 0f
            p < 0.40f -> (p - 0.12f) / 0.28f
            p < 0.62f -> 1f
            else -> 1f - (p - 0.62f) / 0.38f
        }.coerceIn(0f, 1f)
        return Pose(
            bodyX = 1.7f * extend - 0.9f * windup,
            bodyY = 0.4f * extend + 0.2f * windup,
            lean = 16f * extend - 8f * windup,
            hipR = 12f * extend,
            hipL = -5f * extend - 4f * windup,
            kneeR = 8f + 14f * extend,
            kneeL = 6f + 18f * windup,
            armR = -40f * windup + 98f * extend,
            elbowR = 10f + 74f * (1f - extend) + 8f * windup,
            armL = -14f - 30f * windup,
            elbowL = 18f + 24f * windup,
            shoulderR = -1.2f * extend,
            shoulderL = 0.35f * windup,
            headX = 0.55f * extend - 0.35f * windup,
            tilt = -3.2f * extend + 1.6f * windup,
            mouth = 0.9f * extend.coerceAtLeast(windup * 0.4f),
            brow = 1f,
            breath = 0.2f + 0.55f * extend,
            hairSway = -2f * extend + 0.7f * windup,
            clothSway = -1.4f * extend,
            packBob = 0.45f * extend
        )
    }

    /** 만세! — 레벨업 축하 (폴짝폴짝 뛰며 두 팔을 든다) */
    fun cheerPose(phase: Float): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val hop = Math.pow(max(0f, sin(TAU * p)).toDouble(), 0.7).toFloat()
        val land = 1f - hop
        return Pose(
            bodyY = -4.2f * hop,
            hipL = -7f * hop, hipR = 7f * hop,
            kneeL = 10f + 30f * land, kneeR = 10f + 30f * land,
            footL = -20f * hop, footR = -20f * hop,
            armL = -150f - 22f * hop, elbowL = 14f,
            armR = 150f + 22f * hop, elbowR = -14f,
            shoulderL = -1.2f * hop, shoulderR = -1.2f * hop,
            headY = -0.6f * hop,
            tilt = 1.6f * sin(TAU * 2f * p),
            mouth = 1f,
            brow = 0.8f,
            breath = 0.6f,
            hairSway = -1.8f * hop,
            clothSway = -1.4f * hop,
            packBob = 0.8f * hop
        )
    }

    /** NPC 성격별 대기 동작 */
    fun npcPose(kind: Int, phase: Float): Pose {
        val p = ((phase % 1f) + 1f) % 1f
        val base = idlePose(p)
        return when (kind) {
            NPC_PROFESSOR -> {
                val push = bump(p, 0.55f, 0.85f)
                base.copy(
                    headY = base.headY + 0.55f * sin(2f * TAU * p),
                    tilt = base.tilt + 1.4f * sin(TAU * p),
                    armR = -150f * push + 3f,
                    elbowR = 8f + 112f * push,
                    brow = 0.5f * push,
                    blink = if (p >= 0.60f && p < 0.64f) 1f else base.blink
                )
            }
            NPC_SHOP -> {
                val wave = bump(p, 0.10f, 0.62f)
                base.copy(
                    armR = -20f - 128f * wave,
                    elbowR = 12f + 26f * wave + 22f * wave * sin(TAU * 3f * p),
                    mouth = 0.5f * wave,
                    headX = base.headX + 0.3f * wave,
                    tilt = base.tilt - 1.2f * wave
                )
            }
            NPC_VILLAGER -> {
                val look = sin(TAU * p)
                base.copy(
                    turn = 0.9f * look, headX = 0.9f * look,
                    tilt = -1.8f * look, bodyX = 0.5f * look
                )
            }
            NPC_KID -> {
                val hop = Math.pow(max(0f, sin(TAU * 2f * p)).toDouble(), 0.8).toFloat()
                base.copy(
                    bodyY = -3.2f * hop,
                    kneeL = 16f + 26f * (1f - hop), kneeR = 16f + 26f * (1f - hop),
                    hipL = -9f * hop, hipR = 9f * hop,
                    footL = -18f * hop, footR = -18f * hop,
                    armL = -26f - 52f * hop, armR = 26f + 52f * hop,
                    elbowL = 18f, elbowR = 18f,
                    mouth = 0.8f,
                    hairSway = -1.4f * hop,
                    headY = -0.5f * hop
                )
            }
            NPC_ELDER -> {
                val tap = bump(p, 0.44f, 0.60f)
                base.copy(
                    bodyY = base.bodyY + 0.5f + 0.25f * sin(TAU * p),
                    lean = 8f, crouch = 0.35f,
                    armR = 16f + 10f * tap, elbowR = 14f,
                    tilt = base.tilt + 1f,
                    headY = base.headY + 0.6f
                )
            }
            else -> base
        }
    }

    // -----------------------------------------------------------------------
    // 사람 렌더링
    // -----------------------------------------------------------------------

    fun render(direction: Int, pose: Pose, look: Look, topPad: Int = 0): Bitmap {
        // topPad: 앉은 자세처럼 머리가 위로 넘칠 때 상단에 둘 여백(px).
        val bmp = Bitmap.createBitmap(SIZE, SIZE + topPad, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        if (topPad != 0) cv.translate(0f, topPad.toFloat())
        val g = G(cv)
        val pal = look.pal
        val sc = if (look.small) 0.86f else 1f

        val ground = 31.4f
        val footH = 1.7f * sc
        val legLen = 8.3f * sc
        val thigh = 4.2f * sc
        val shin = 3.9f * sc
        val torsoH = 6.1f * sc
        val headR = 6.5f * sc
        val headGap = 6.3f * sc

        var hipY = ground - footH - legLen + pose.bodyY + pose.crouch * 2.4f * sc
        if (look.small) hipY += 0.6f
        val hipX = 16f + pose.bodyX
        val leanPx = pose.lean * 0.085f * (if (direction == SIDE) 1f else 0f)
        val shoulderY = hipY - torsoH + pose.crouch * 0.6f
        val shoulderX = hipX + leanPx
        val headCx = shoulderX + pose.headX + leanPx * 0.8f + pose.tilt * 0.12f
        val headCy = shoulderY - headGap + pose.headY - pose.breath * 0.25f

        val depth = if (direction == SIDE) 1f else 0.32f
        val face = if (direction != BACK) 1f else -1f

        val tL: Float; val tR: Float
        if (direction == SIDE) {
            tL = shoulderX - 5f * sc; tR = shoulderX + 5f * sc
        } else {
            tL = shoulderX - 6.2f * sc; tR = shoulderX + 6.2f * sc
        }
        val hipHalf = 2.45f * sc
        val shHalf = (if (direction == SIDE) 5f else 7f) * sc

        fun legRoot(side: Float) = hipX + side * hipHalf * (if (direction == SIDE) 0.5f else 1f)
        fun armRootX(side: Float) = hipX + leanPx + side * shHalf * (if (direction == SIDE) 0.2f else 1f)
        fun armRootY(side: Float) =
            shoulderY + 1.3f * sc + (if (side > 0) pose.shoulderR else pose.shoulderL)

        // ---- 다리 ----------------------------------------------------------
        fun drawLeg(side: Float, hip: Float, knee: Float, foot: Float, back: Boolean) {
            val x0 = legRoot(side)
            val d = depth * face
            val kx = fkX(x0, hip, thigh, d)
            val ky = fkY(hipY, hip, thigh)
            val shinAng = hip - knee
            val ax = fkX(kx, shinAng, shin, d)
            val ay = fkY(ky, shinAng, shin)
            val pants = if (back) pal.pants2 else pal.pants
            val shoe = if (back) shade(pal.shoe, 0.8f) else pal.shoe
            g.seg(x0, hipY, kx, ky, 2.15f * sc, 1.8f * sc, pal.line)
            g.seg(kx, ky, ax, ay, 1.75f * sc, 1.45f * sc, pal.line)
            g.seg(x0, hipY - 0.3f, kx, ky, 1.7f * sc, 1.4f * sc, pants)
            g.seg(kx, ky, ax, ay, 1.35f * sc, 1.1f * sc, pants)
            if (direction == SIDE) {
                val pitch = foot * PI.toFloat() / 180f
                val fl = 3.5f * sc
                val tx = ax + cos(pitch) * fl * face
                val ty = ay + sin(pitch) * fl * 0.55f
                g.seg(ax - 0.7f * face, ay - 0.1f, tx, ty, 1.5f * sc, 1.05f * sc, pal.line)
                g.seg(ax - 0.6f * face, ay - 0.1f, tx, ty, 1.15f * sc, 0.75f * sc, shoe)
            } else {
                val lift = max(0f, (ground - footH * 0.5f) - ay)
                val fw = 1.95f * sc
                val fh = 1.55f * sc - 0.3f * min(1f, lift * 0.35f)
                g.rrect(ax - fw - 0.35f, ay - 0.45f, ax + fw + 0.35f, ay + fh + 0.4f, 0.9f, pal.line)
                g.rrect(ax - fw, ay - 0.2f, ax + fw, ay + fh, 0.8f, shoe)
            }
        }

        // ---- 팔 -------------------------------------------------------------
        fun drawArm(side: Float, ang: Float, elbow: Float, back: Boolean) {
            val x0 = armRootX(side)
            val y0 = armRootY(side)
            val d = depth * face
            val upper = 3.6f * sc
            val fore = 3.4f * sc
            val ex = fkX(x0, ang, upper, d)
            val ey = fkY(y0, ang, upper)
            val foreAng = ang + elbow
            val hx = fkX(ex, foreAng, fore, d)
            val hy = fkY(ey, foreAng, fore)
            val sleeve = if (back) shade(pal.top2, 0.88f) else pal.top2
            val skin = if (back) pal.skin2 else pal.skin
            g.seg(x0, y0, ex, ey, 1.85f * sc, 1.5f * sc, pal.line)
            g.seg(ex, ey, hx, hy, 1.45f * sc, 1.2f * sc, pal.line)
            g.seg(x0, y0, ex, ey, 1.4f * sc, 1.1f * sc, sleeve)
            g.seg(ex, ey, hx, hy, 1f * sc, 0.85f * sc, skin)
            g.circ(hx, hy, 1.25f * sc, pal.line)
            g.circ(hx, hy, 0.95f * sc, skin)
        }

        // ---- 몸통 ------------------------------------------------------------
        fun drawTorso() {
            val top = shoulderY - 0.6f
            val bot = hipY + 2.3f * sc
            val bw = pose.breath * 0.32f
            g.rrect(tL - 0.9f - bw, top - 0.9f, tR + 0.9f + bw, bot + 0.6f, 3.0f * sc, pal.line)
            g.rrect(tL - bw, top, tR + bw, bot, 2.6f * sc, pal.top)
            if (direction == SIDE) {
                g.rrect(tL + (tR - tL) * 0.45f, top + 0.8f, tR - 0.4f, bot - 0.5f, 2.2f * sc, pal.top2)
            } else {
                g.rrect(tR - (tR - tL) * 0.30f, top + 0.9f, tR - 0.5f, bot - 0.5f, 2.2f * sc, pal.top2)
                g.rect(tL + (tR - tL) * 0.30f, top, tL + (tR - tL) * 0.46f, top + 2.1f, pal.top2)
            }
            if (look.apron) {
                g.rrect(tL + 1.6f, top + 2.4f, tR - 1.6f, bot - 0.6f, 2.4f, 0xFFFDF6E8.toInt())
                g.rect(tL + 3f, top + 2.4f, tR - 3f, top + 3.7f, 0xFFE8DFC8.toInt())
                g.rect(tL + 4f, top + 7.6f, tR - 4f, top + 8.9f, 0xFFE8DFC8.toInt())
            }
        }

        fun drawPack() {
            val by = pose.packBob
            g.rect(hipX - 4.6f, shoulderY - 0.4f, hipX - 3.2f, hipY - 1f, shade(pal.pack, 0.7f))
            g.rect(hipX + 3.2f, shoulderY - 0.4f, hipX + 4.6f, hipY - 1f, shade(pal.pack, 0.7f))
            g.rrect(hipX - 4.9f, shoulderY + 1.3f + by, hipX + 4.9f, hipY + 0.8f + by, 2.6f, pal.line)
            g.rrect(hipX - 4.2f, shoulderY + 2f + by, hipX + 4.2f, hipY + 0.1f + by, 2.2f, pal.pack)
            g.rect(hipX - 2.4f, shoulderY + 3.2f + by, hipX + 2.4f, hipY - 1f + by, pal.pack2)
            g.rect(hipX - 3.2f, shoulderY + 2.3f + by, hipX + 3.2f, shoulderY + 3f + by, pal.pack2)
        }

        // ---- 머리 ------------------------------------------------------------
        fun drawHead() {
            val cx = headCx
            val cy = headCy
            val tilt = pose.tilt
            val turn = pose.turn * face
            g.circ(cx, cy, headR + 0.85f, pal.line)
            if (direction == BACK) {
                g.circ(cx, cy, headR, pal.hair)
                g.rect(cx - headR * 0.85f, cy + 1.9f, cx + headR * 0.85f, cy + 3.7f, pal.hair2)
                g.rect(
                    cx - headR * 0.6f + pose.hairSway, cy - headR - 0.4f,
                    cx + headR * 0.6f + pose.hairSway, cy - headR + 1.1f, pal.hair2
                )
                if (look.longHair) g.rrect(cx - 4.2f, cy + 2f, cx + 4.2f, cy + 8.4f, 2.4f, pal.hair2)
                return
            }

            g.circ(cx, cy, headR, pal.skin)
            val sway = pose.hairSway
            val capBot = cy - 0.5f
            g.oval(
                cx - headR - 0.15f + sway * 0.18f, cy - headR - 0.35f,
                cx + headR + 0.15f + sway * 0.18f, capBot, pal.hair
            )
            if (direction == SIDE) {
                g.rrect(cx - headR - 0.2f, cy - 2.6f, cx - headR + 2.7f, cy + 5f, 1.2f, pal.hair)
                g.rect(cx - headR + 1.2f, cy + 1.4f, cx - headR + 2.7f, cy + 5f + sway * 0.35f, pal.hair2)
                g.poly(
                    pal.hair,
                    cx + 0.4f, capBot - 2.2f,
                    cx + headR + 0.9f + sway, capBot - 0.4f,
                    cx + headR - 1.8f, capBot + 1.3f,
                    cx + 0.6f, capBot + 0.4f
                )
            } else {
                g.rrect(cx - headR * 0.98f, cy - 2.6f, cx - headR * 0.44f, cy + 4.4f, 0.9f, pal.hair)
                g.rrect(cx + headR * 0.44f, cy - 2.6f, cx + headR * 0.98f, cy + 4.4f, 0.9f, pal.hair)
                g.poly(
                    pal.hair,
                    cx - 4.2f + sway, capBot - 1.4f,
                    cx - 1.6f + sway * 1.4f, capBot + 1.5f,
                    cx - 0.6f + sway, capBot - 1.2f
                )
                g.poly(
                    pal.hair,
                    cx + 0.8f + sway, capBot - 1.2f,
                    cx + 2.6f + sway * 1.4f, capBot + 1.4f,
                    cx + 4.3f + sway, capBot - 1.4f
                )
                if (look.longHair) {
                    g.rrect(cx - headR - 0.7f, cy - 1.2f, cx - headR + 1.5f, cy + 7.4f + sway * 0.3f, 1.1f, pal.hair2)
                    g.rrect(cx + headR - 1.5f, cy - 1.2f, cx + headR + 0.7f, cy + 7.4f - sway * 0.3f, 1.1f, pal.hair2)
                }
            }

            val ex = cx + turn * 1.7f + tilt * 0.1f
            val ey = cy + 1.5f + tilt * 0.05f
            val openH = 1.85f * (1f - pose.blink) + 0.35f
            if (direction == SIDE) {
                g.rect(ex + 2.6f, ey - 0.9f, ex + 4f, ey - 0.9f + openH, pal.eye)
                g.rect(ex + 2.5f, ey - 2.1f - pose.brow * 0.6f, ex + 4.2f, ey - 1.5f - pose.brow * 0.6f, pal.hair2)
                g.rect(ex + 5.4f, ey + 0.1f, ex + 6.4f, ey + 1.3f, pal.skin2)
                g.rect(ex + 2.6f, ey + 1.9f, ex + 4.2f, ey + 2.9f, pal.blush)
                if (pose.mouth > 0.2f) {
                    g.rect(ex + 4.6f, ey + 1.4f, ex + 5.6f, ey + 1.4f + pose.mouth * 1.4f, 0xFF7A4A3A.toInt())
                }
            } else {
                val lx = ex - 2.9f
                val rx = ex + 1.5f
                g.rect(lx, ey - 0.9f, lx + 1.5f, ey - 0.9f + openH, pal.eye)
                g.rect(rx, ey - 0.9f, rx + 1.5f, ey - 0.9f + openH, pal.eye)
                if (pose.blink < 0.4f) {
                    g.rect(lx + 0.9f, ey - 0.7f, lx + 1.4f, ey, Color.WHITE)
                    g.rect(rx + 0.9f, ey - 0.7f, rx + 1.4f, ey, Color.WHITE)
                }
                if (pose.brow > 0.05f) {
                    g.rect(lx - 0.2f, ey - 2.3f - pose.brow * 0.7f, lx + 1.7f, ey - 1.7f - pose.brow * 0.7f, pal.hair2)
                    g.rect(rx - 0.2f, ey - 2.3f - pose.brow * 0.7f, rx + 1.7f, ey - 1.7f - pose.brow * 0.7f, pal.hair2)
                }
                g.rect(cx - headR * 0.88f, ey + 1.2f, cx - headR * 0.88f + 1.6f, ey + 2.4f, pal.blush)
                g.rect(cx + headR * 0.88f - 1.6f, ey + 1.2f, cx + headR * 0.88f, ey + 2.4f, pal.blush)
                if (pose.mouth > 0.2f) {
                    val mw = 0.7f + pose.mouth * 0.7f
                    g.rrect(ex - mw, ey + 2.2f, ex + mw, ey + 3f + pose.mouth * 1.1f, 0.5f, 0xFF7A4A3A.toInt())
                }
            }

            if (look.glasses) {
                if (direction == SIDE) {
                    val gx = ex + 2.6f
                    g.circ(gx + 0.7f, ey - 0.1f, 2.5f, pal.line)
                    g.circ(gx + 0.7f, ey - 0.1f, 1.8f, 0x66DCF0FF)
                    g.rect(gx - 2.4f, ey - 0.5f, gx - 0.8f, ey - 0.1f, pal.line)
                } else {
                    g.circ(ex - 2.1f, ey - 0.1f, 2.5f, pal.line)
                    g.circ(ex + 2.3f, ey - 0.1f, 2.5f, pal.line)
                    g.circ(ex - 2.1f, ey - 0.1f, 1.8f, 0x66DCF0FF)
                    g.circ(ex + 2.3f, ey - 0.1f, 1.8f, 0x66DCF0FF)
                    g.rect(ex - 0.4f, ey - 0.4f, ex + 0.5f, ey, pal.line)
                }
            }
        }

        // ---- 장비 -------------------------------------------------------------
        fun drawScarf() {
            val gear = look.gear ?: return
            val scarf = gear.scarf ?: return
            val ny = shoulderY - 1f
            if (direction == SIDE) {
                g.rrect(shoulderX - 3.4f, ny - 0.9f, shoulderX + 3.6f, ny + 1.5f, 1.1f, scarf)
                g.seg(shoulderX - 2.2f, ny + 0.8f, shoulderX - 3f + pose.clothSway * 1.6f, ny + 4.6f, 1.1f, 0.8f, scarf)
            } else {
                g.rrect(shoulderX - 4.6f, ny - 0.9f, shoulderX + 4.6f, ny + 1.6f, 1.2f, scarf)
                g.seg(shoulderX + 2.4f, ny + 1f, shoulderX + 3f + pose.clothSway * 1.6f, ny + 5f, 1.1f, 0.8f, scarf)
            }
        }

        fun drawVest() {
            val gear = look.gear ?: return
            val vest = gear.vest ?: return
            if (direction == BACK) return
            val top = shoulderY - 0.2f
            val bot = hipY + 0.9f
            if (direction == SIDE) {
                g.rrect(tL + 1.2f, top + 0.6f, tR - 0.6f, bot, 1.8f, vest)
                g.rect(tL + 1.2f, top + 0.6f, tL + 2f, bot, gear.vestDark)
            } else {
                g.rrect(tL - 0.2f, top + 0.6f, tL + 3.4f, bot, 1.4f, vest)
                g.rrect(tR - 3.4f, top + 0.6f, tR + 0.2f, bot, 1.4f, vest)
                g.rect(tL + 3f, top + 0.2f, tR - 3f, top + 2f, vest)
                g.rect(tL - 0.2f, top + 0.6f, tL + 0.7f, bot, gear.vestDark)
                g.rect(tR - 0.7f, top + 0.6f, tR + 0.2f, bot, gear.vestDark)
            }
        }

        fun drawCap() {
            val gear = look.gear ?: return
            val cap = gear.cap ?: return
            val cx = headCx
            val cy = headCy
            val tilt = pose.tilt
            val cw = headR + 0.6f
            val top = cy - headR - 2f
            val bot = cy - headR * 0.18f
            g.rrect(cx - cw - 0.5f + tilt * 0.12f, top - 0.5f, cx + cw + 0.5f + tilt * 0.12f, bot + 0.4f, 3.4f, pal.line)
            g.rrect(cx - cw + tilt * 0.12f, top, cx + cw + tilt * 0.12f, bot, 3f, cap)
            g.rect(cx - cw + 0.7f + tilt * 0.12f, top + 0.5f, cx + cw - 0.7f + tilt * 0.12f, top + 2.4f, gear.capDark)
            if (direction == SIDE) {
                if (gear.brim) {
                    g.rrect(cx - cw - 1.4f, bot - 1.5f, cx + cw + 2.6f, bot + 0.5f, 0.9f, pal.line)
                    g.rrect(cx - cw - 1f, bot - 1.4f, cx + cw + 2.2f, bot + 0.1f, 0.8f, cap)
                } else {
                    g.rect(cx + 2.2f, bot - 1.5f, cx + cw + 2.6f, bot - 0.1f, pal.line)
                    g.rect(cx + 2.2f, bot - 1.4f, cx + cw + 2.3f, bot - 0.4f, gear.capDark)
                }
            } else if (direction == FRONT) {
                if (gear.brim) {
                    g.rrect(cx - cw - 2.8f, bot - 1.2f, cx + cw + 2.8f, bot + 0.9f, 1.1f, pal.line)
                    g.rrect(cx - cw - 2.4f, bot - 1.1f, cx + cw + 2.4f, bot + 0.6f, 0.9f, cap)
                } else {
                    g.rect(cx - cw + 0.5f, bot - 1.1f, cx + cw - 0.5f, bot + 0.6f, pal.line)
                    g.rect(cx - cw + 0.9f, bot - 1f, cx + cw - 0.9f, bot + 0.3f, gear.capDark)
                }
            }
            gear.feather?.let { ft ->
                val fx = cx + (if (direction == SIDE) -cw + 1f else cw - 0.8f)
                val sway = pose.hairSway * 0.8f
                g.seg(fx, top + 1.2f, fx + sway - 0.8f, top - 3f, 0.75f, 0.45f, ft)
                g.circ(fx + sway - 0.9f, top - 3f, 0.7f, ft)
            }
        }

        fun drawCamera() {
            if (direction == BACK) return
            val cx = headCx + (if (direction == SIDE) 2.2f else 0f)
            val cy = headCy + 1.8f
            g.rrect(cx - 2.7f, cy - 1.7f, cx + 2.7f, cy + 1.6f, 1f, pal.line)
            g.rrect(cx - 2.3f, cy - 1.3f, cx + 2.3f, cy + 1.2f, 0.8f, 0xFF3A3F49.toInt())
            g.rect(cx - 1.8f, cy - 1.2f, cx + 0.4f, cy - 0.6f, 0xFF5A616E.toInt())
            val lensX = cx + (if (direction != SIDE) 1.9f else 2.7f)
            g.circ(lensX, cy - 0.1f, 1.7f, pal.line)
            g.circ(lensX, cy - 0.1f, 1.25f, 0xFF2B3038.toInt())
            g.circ(lensX + 0.3f, cy - 0.5f, 0.5f, 0xFFBFE6FF.toInt())
            if (pose.holdT > 0.7f) g.rect(cx - 1.4f, cy - 2.7f, cx - 0.4f, cy - 1.9f, 0xFFF2D06B.toInt())
        }

        fun drawCane() {
            val hx = hipX + (if (direction != SIDE) 4.6f else 5.2f)
            g.rect(hx, shoulderY + 0.5f, hx + 1.4f, ground - 0.4f, 0xFF8A5A33.toInt())
            g.rect(hx - 1.2f, shoulderY - 0.6f, hx + 2.2f, shoulderY + 1f, 0xFF6B431F.toInt())
        }

        // ---- 그리기 순서 --------------------------------------------------------
        val backIsRight =
            if (direction != BACK) pose.hipR < pose.hipL else pose.hipR > pose.hipL
        if (direction == SIDE) {
            drawLeg(-1f, pose.hipL, pose.kneeL, pose.footL, true)
            drawArm(-1f, pose.armL, pose.elbowL, true)
            drawTorso()
            drawVest()
            drawLeg(1f, pose.hipR, pose.kneeR, pose.footR, false)
            drawScarf()
            drawHead()
            drawCap()
            drawArm(1f, pose.armR, pose.elbowR, false)
        } else {
            if (backIsRight) drawLeg(1f, pose.hipR, pose.kneeR, pose.footR, true)
            else drawLeg(-1f, pose.hipL, pose.kneeL, pose.footL, true)
            drawTorso()
            if (direction == BACK) {
                if (look.pack) drawPack()
            } else {
                drawVest()
            }
            if (backIsRight) drawLeg(-1f, pose.hipL, pose.kneeL, pose.footL, false)
            else drawLeg(1f, pose.hipR, pose.kneeR, pose.footR, false)
            drawArm(-1f, pose.armL, pose.elbowL, !backIsRight)
            drawArm(1f, pose.armR, pose.elbowR, backIsRight)
            drawScarf()
            drawHead()
            drawCap()
        }

        if (pose.hold == HOLD_CAMERA) drawCamera()
        if (look.cane) drawCane()
        return bmp
    }

    // -----------------------------------------------------------------------
    // 자전거 (페달을 밟는 라이더)
    // -----------------------------------------------------------------------

    private val RIM = 0xFF23232B.toInt()
    private val METAL = 0xFF9AA0AD.toInt()

    /** direction: SIDE(오른쪽)/FRONT/BACK, phase: 0~1 페달 한 바퀴, style: 모델·도색·부속품 */
    fun renderBike(direction: Int, phase: Float, look: Look, style: BikeStyle): Bitmap {
        // 도색: 프레임 / 바퀴 / 안장·그립
        val BIKE_COL = style.frame.argb
        val BIKE_DARK = shade(BIKE_COL, 0.68f)
        val TIRE = style.tire.argb
        val TIRE_IN = shade(TIRE, 1.55f)
        val LEATHER = style.saddle.argb
        val kind = style.modelId
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val g = G(Canvas(bmp))
        val pal = look.pal
        val ang = TAU * (((phase % 1f) + 1f) % 1f)
        val bob = 0.35f * cos(2f * ang)
        val sway = 0.5f * sin(ang)

        fun ring(cx: Float, cy: Float, r: Float, w: Float, col: Int, n: Int = 14) {
            var px = cx + r
            var py = cy
            for (i in 1..n) {
                val a = TAU * i / n
                val nx = cx + cos(a) * r
                val ny = cy + sin(a) * r
                g.seg(px, py, nx, ny, w, w, col)
                px = nx; py = ny
            }
        }

        fun wheel(cx: Float, cy: Float, r: Float, spin: Float, ti: Float = 1.1f) {
            ring(cx, cy, r - 0.5f, 1f, RIM)
            ring(cx, cy, r - ti, (ti * 0.5f).coerceAtLeast(0.4f), TIRE)
            for (i in 0 until 4) {
                val a = spin + i * (PI.toFloat() / 4f)
                g.seg(
                    cx - cos(a) * (r - 1.8f), cy - sin(a) * (r - 1.8f),
                    cx + cos(a) * (r - 1.8f), cy + sin(a) * (r - 1.8f), 0.35f, 0.35f, METAL
                )
            }
            g.circ(cx, cy, 1.3f, RIM)
            g.circ(cx, cy, 0.7f, METAL)
        }

        /**
         * 라이더의 탐조 모자 — 자전거를 타도 걸을 때(render의 drawCap)와 같은 모습을
         * 유지한다. (헬멧으로 갈아끼우지 않는다: 캐릭터가 다른 사람처럼 변하는 것 방지)
         */
        fun drawRiderCap(hx: Float, hy: Float, headR: Float, dir: Int) {
            val gear = look.gear ?: return
            val cap = gear.cap ?: return
            val cw = headR + 0.7f
            val capTop = hy - headR - 1.4f
            val capBot = hy - headR * 0.18f
            g.rrect(hx - cw - 0.4f, capTop - 0.4f, hx + cw + 0.4f, capBot + 0.3f, 2.2f, pal.line)
            g.rrect(hx - cw, capTop, hx + cw, capBot, 2.1f, cap)
            g.rect(hx - cw + 0.6f, capTop + 0.4f, hx + cw - 0.6f, capTop + 1.8f, gear.capDark)
            if (dir == SIDE) {
                if (gear.brim) {
                    g.rrect(hx - cw - 1f, capBot - 1f, hx + cw + 2.1f, capBot + 0.4f, 0.8f, pal.line)
                    g.rrect(hx - cw - 0.7f, capBot - 1f, hx + cw + 1.8f, capBot + 0.1f, 0.7f, cap)
                } else {
                    g.rect(hx + 1.5f, capBot - 1f, hx + cw + 2.1f, capBot - 0.1f, pal.line)
                    g.rect(hx + 1.5f, capBot - 0.9f, hx + cw + 1.8f, capBot - 0.4f, gear.capDark)
                }
            } else if (dir == FRONT) {
                if (gear.brim) {
                    g.rrect(hx - cw - 1.7f, capBot - 1f, hx + cw + 1.7f, capBot + 0.6f, 0.9f, pal.line)
                    g.rrect(hx - cw - 1.3f, capBot - 1f, hx + cw + 1.3f, capBot + 0.3f, 0.8f, cap)
                } else {
                    g.rect(hx - cw + 0.5f, capBot - 1f, hx + cw - 0.5f, capBot + 0.5f, pal.line)
                    g.rect(hx - cw + 0.8f, capBot - 0.9f, hx + cw - 0.8f, capBot + 0.2f, gear.capDark)
                }
            }
            gear.feather?.let { ft ->
                val fx = hx + (if (dir == SIDE) -cw + 0.9f else cw - 0.9f)
                val fs = sway * 0.6f
                g.seg(fx, capTop + 1f, fx + fs - 0.7f, capTop - 1.7f, 0.65f, 0.4f, ft)
                g.circ(fx + fs - 0.75f, capTop - 1.7f, 0.55f, ft)
            }
        }

        if (direction == SIDE) {
            // 모델별 지오메트리 (탈것은 자전거만 — 종류에 따라 바퀴·프레임이 달라진다)
            val rear: Float; val front: Float; val wy: Float
            val wr: Float; val ti: Float
            var frontY = 0f; var frontR = 0f
            val crankX = 16f; val crankY = 24.2f; val pr = 2.7f
            var sadX = 11.6f; var sadY = 16.6f + bob
            var barX = 23f; var barY = 15.4f + bob
            when (kind) {
                "minivelo" -> { rear = 8.4f; front = 23.6f; wy = 26.3f; wr = 4.3f; ti = 1.0f }
                "bmx" -> { rear = 8f; front = 24f; wy = 26f; wr = 4.8f; ti = 1.5f; sadY -= 0.6f; barY -= 1.3f }
                "road", "fixie" -> { rear = 7.4f; front = 24.6f; wy = 25.6f; wr = 5.4f; ti = 0.9f; barY -= 0.7f }
                "mtb" -> { rear = 7.3f; front = 24.7f; wy = 25.5f; wr = 5.9f; ti = 1.7f }
                "cruiser" -> { rear = 7.1f; front = 24.9f; wy = 25.2f; wr = 6.1f; ti = 1.9f; sadY -= 0.3f; barY += 0.5f }
                "ebike" -> { rear = 7.4f; front = 24.6f; wy = 25.6f; wr = 5.6f; ti = 1.3f }
                "tandem" -> { rear = 6.2f; front = 25.8f; wy = 25.6f; wr = 5.5f; ti = 1.1f }
                "vintage" -> {
                    rear = 7.6f; front = 22.8f; wy = 25.9f; wr = 4.2f; ti = 1.0f
                    frontY = 21.6f; frontR = 8.2f
                    sadY -= 1.6f; barX = 24.2f; barY -= 3.2f
                }
                else -> { rear = 7.4f; front = 24.6f; wy = 25.6f; wr = 5.6f; ti = 1.1f } // basic/city
            }
            wheel(rear, wy, wr, -ang, ti)
            if (kind == "vintage") wheel(front, frontY, frontR, -ang, 1.3f)
            else wheel(front, wy, wr, -ang, ti)
            if (kind == "vintage") {
                // 대형 앞바퀴 + 작은 뒷바퀴의 백본 프레임
                g.seg(rear, wy, sadX + 1f, sadY + 1.3f, 0.9f, 0.8f, BIKE_COL)
                g.seg(sadX + 1f, sadY + 1.3f, barX - 1.2f, barY + 1.3f, 0.9f, 0.8f, BIKE_COL)
                g.seg(barX - 1.2f, barY + 1.3f, front, frontY, 0.9f, 0.8f, BIKE_COL)
                g.seg(rear, wy, crankX - 1.2f, crankY - 1f, 0.7f, 0.7f, BIKE_DARK)
            } else if (kind == "tandem") {
                // 두 사람이 타는 긴 프레임 + 뒤 탑승자 안장
                g.seg(rear, wy, crankX - 4.8f, crankY - 0.4f, 0.9f, 0.9f, BIKE_DARK)
                g.seg(rear, wy, sadX - 4.2f, sadY + 1.6f, 0.9f, 0.8f, BIKE_COL)
                g.seg(crankX - 4.8f, crankY - 0.4f, sadX - 4.2f, sadY + 1.6f, 1f, 0.9f, BIKE_COL)
                g.seg(crankX - 4.8f, crankY - 0.4f, crankX, crankY, 1f, 1f, BIKE_DARK)
                g.seg(crankX, crankY, sadX, sadY + 1.4f, 1f, 0.9f, BIKE_COL)
                g.seg(crankX, crankY, barX - 0.6f, barY + 1.6f, 1f, 0.8f, BIKE_COL)
                g.seg(sadX, sadY + 1.4f, barX - 0.6f, barY + 1.6f, 0.8f, 0.7f, BIKE_COL)
                g.seg(barX - 0.6f, barY + 1.6f, front, wy, 0.85f, 0.75f, METAL)
                g.rrect(sadX - 6.8f, sadY - 0.1f, sadX - 2.8f, sadY + 1.3f, 0.8f, LEATHER) // 뒤 안장
            } else {
                g.seg(rear, wy, crankX, crankY, 0.9f, 0.9f, BIKE_DARK)
                g.seg(rear, wy, sadX, sadY + 1.4f, 0.9f, 0.8f, BIKE_COL)
                g.seg(crankX, crankY, sadX, sadY + 1.4f, 1f, 0.9f, BIKE_COL)
                g.seg(crankX, crankY, barX - 0.6f, barY + 1.6f, 1f, 0.8f, BIKE_COL)
                g.seg(sadX, sadY + 1.4f, barX - 0.6f, barY + 1.6f, 0.8f, 0.7f, BIKE_COL)
                g.seg(barX - 0.6f, barY + 1.6f, front, wy, 0.85f, 0.75f, METAL)
                if (kind == "mtb") {
                    g.rrect(barX - 1.3f, barY + 1.6f, barX + 0.1f, barY + 5.4f, 0.7f, METAL) // 서스펜션 포크
                }
                if (kind == "ebike") {
                    g.rrect(13.6f, 21.2f, 19.2f, 24.2f, 1f, RIM)                   // 배터리
                    g.rrect(14f, 21.5f, 18.8f, 23.9f, 0.9f, 0xFF4A4A56.toInt())
                    g.rect(14.8f, 22.1f, 17.8f, 22.9f, 0xFF6FB6C9.toInt())
                }
            }
            val sadW = when (kind) {
                "cruiser" -> 3.2f
                "road", "fixie", "bmx" -> 1.9f
                else -> 2.6f
            }
            g.rrect(sadX - sadW, sadY - 0.2f, sadX + 2f, sadY + 1.4f, 0.8f, LEATHER)
            g.rrect(barX - 2.6f, barY - 0.4f, barX + 1.6f, barY + 1f, 0.7f, LEATHER)
            if (kind == "road" || kind == "fixie") {
                g.rrect(barX + 0.2f, barY + 0.4f, barX + 1.8f, barY + 2.8f, 0.9f, LEATHER) // 드롭바
            }
            g.circ(barX + 1.4f, barY + 0.3f, 1.1f, RIM)

            // 부속품 — 뒤쪽(짐받이)은 라이더보다 먼저
            if (style.rack) {
                g.rrect(rear - 1.4f, wy - wr - 2.4f, rear + 5f, wy - wr - 1.2f, 0.5f, METAL)
                g.seg(rear - 0.8f, wy - wr - 1.4f, rear - 0.4f, wy - wr + 1.2f, 0.5f, 0.5f, METAL)
                g.seg(rear + 4.2f, wy - wr - 1.4f, rear + 4.4f, wy - wr + 1.2f, 0.5f, 0.5f, METAL)
            }

            val hipXs = sadX + 0.6f
            val hipYs = sadY - 0.6f
            val shX = hipXs + 4.2f
            val shY = hipYs - 6.2f
            for (i in 0 until 2) {
                val a = ang + (if (i == 0) 0f else PI.toFloat())
                val fx = crankX + cos(a) * pr
                val fy = crankY + sin(a) * pr
                val kx = ikX(hipXs, hipYs, fx, fy, 4.4f, 4.4f, -1f)
                val ky = ikY(hipXs, hipYs, fx, fy, 4.4f, 4.4f, -1f)
                val pants = if (i == 0) pal.pants else pal.pants2
                val shoe = if (i == 0) pal.shoe else shade(pal.shoe, 0.8f)
                g.seg(hipXs, hipYs, kx, ky, 2f, 1.6f, pal.line)
                g.seg(kx, ky, fx, fy, 1.6f, 1.2f, pal.line)
                g.seg(hipXs, hipYs, kx, ky, 1.55f, 1.2f, pants)
                g.seg(kx, ky, fx, fy, 1.15f, 0.9f, pants)
                g.seg(crankX, crankY, fx, fy, 0.5f, 0.5f, METAL)
                g.seg(fx - 1f, fy + 0.4f, fx + 1.6f, fy + 0.4f, 1.05f, 0.9f, shoe)
                g.rect(fx - 1.4f, fy + 1f, fx + 1.8f, fy + 1.9f, RIM)
            }
            g.seg(hipXs, hipYs, shX, shY, 4.4f, 3.9f, pal.line)
            g.seg(hipXs, hipYs, shX, shY, 3.7f, 3.2f, pal.top)
            g.seg(hipXs + 1f, hipYs - 0.6f, shX + 0.8f, shY + 0.4f, 2f, 1.7f, pal.top2)
            look.gear?.vest?.let { vs ->
                g.seg(hipXs + 0.4f, hipYs - 0.4f, shX + 0.2f, shY + 0.6f, 2.4f, 2.1f, vs)
            }
            val handX = barX - 1f
            val handY = barY - 0.2f
            val ex = ikX(shX, shY, handX, handY, 3.6f, 3.4f, -1f)
            val ey = ikY(shX, shY, handX, handY, 3.6f, 3.4f, -1f)
            g.seg(shX, shY, ex, ey, 1.8f, 1.5f, pal.line)
            g.seg(ex, ey, handX, handY, 1.4f, 1.2f, pal.line)
            g.seg(shX, shY, ex, ey, 1.35f, 1.1f, pal.top2)
            g.seg(ex, ey, handX, handY, 0.95f, 0.85f, pal.skin)
            g.circ(handX, handY, 1.15f, pal.skin)

            // 목도리 — 달리는 바람에 뒤로 나부낌
            look.gear?.scarf?.let { sc ->
                g.rrect(shX - 2.4f, shY - 1.9f, shX + 2.8f, shY + 0.2f, 1f, sc)
                g.seg(shX - 1.8f, shY - 0.6f, shX - 5.4f, shY + 0.2f + sway * 1.2f, 1.1f, 0.65f, sc)
            }

            val hx = shX + 1.6f
            val hy = shY - 4.6f
            g.circ(hx, hy, 5f, pal.line)
            g.circ(hx, hy, 4.3f, pal.skin)
            // 머리카락 — 걸을 때와 같은 헤어스타일
            val hairBack = if (look.longHair) 5.2f else 3.1f
            g.rrect(hx - 4.6f, hy - 1.8f, hx - 2.4f, hy + hairBack, 0.9f, pal.hair)
            g.rect(hx - 4.1f, hy + 0.8f, hx - 2.9f, hy + hairBack + 0.4f + sway * 0.5f, pal.hair2)
            g.oval(hx - 4.4f, hy - 4.7f, hx + 4.4f, hy - 0.3f, pal.hair)
            g.poly(
                pal.hair,
                hx + 0.2f, hy - 2f,
                hx + 4.9f - sway * 0.4f, hy - 0.6f,
                hx + 3.1f, hy + 0.6f,
                hx + 0.4f, hy
            )
            g.rect(hx + 1.6f, hy + 0.2f, hx + 3f, hy + 1.8f, pal.eye)
            g.rect(hx + 4f, hy + 1.2f, hx + 4.8f, hy + 2.2f, pal.skin2)
            g.rect(hx + 1.4f, hy + 2.6f, hx + 2.8f, hy + 3.5f, pal.blush)
            drawRiderCap(hx, hy, 4.3f, SIDE)

            // 부속품 — 앞쪽(바구니/전조등/방울/스트리머)
            if (style.basket) {
                g.rrect(barX + 1.2f, barY + 1.2f, barX + 6.6f, barY + 5.8f, 1f, 0xFFC9A05C.toInt())
                g.rect(barX + 1.8f, barY + 2.6f, barX + 6f, barY + 3.2f, 0xFFB08840.toInt())
                g.rect(barX + 1.8f, barY + 4.2f, barX + 6f, barY + 4.8f, 0xFFB08840.toInt())
            }
            if (style.light) {
                g.circ(barX + 1.8f, barY - 1.3f, 1.3f, METAL)
                g.circ(barX + 2.4f, barY - 1.3f, 0.75f, 0xFFF2E3C2.toInt())
            }
            if (style.bell) {
                g.circ(barX - 1.3f, barY - 1.3f, 1.05f, 0xFFD9A03C.toInt())
                g.circ(barX - 1.3f, barY - 1.7f, 0.5f, 0xFFF2D06B.toInt())
            }
            if (style.streamers) {
                g.seg(barX - 1.2f, barY + 0.6f, barX - 4.2f, barY + 1.8f, 0.7f, 0.4f, 0xFFDB6B9A.toInt())
                g.seg(barX - 1.4f, barY + 1.5f, barX - 4.6f, barY + 3.2f, 0.6f, 0.35f, 0xFFF2D06B.toInt())
            }
            return bmp
        }

        // 정면 / 뒷면
        val cx = 16f + sway * 0.6f
        val wrF = when (kind) {
            "minivelo" -> 3.5f
            "bmx", "road", "fixie" -> 3.9f
            "cruiser", "mtb" -> 4.8f
            "vintage" -> 5f
            else -> 4.5f
        }
        val hw = when (kind) {          // 핸들바 반폭
            "road", "fixie" -> 6.6f
            "cruiser" -> 9.2f
            "bmx" -> 7.2f
            else -> 8f
        }
        wheel(16f, 27.4f, wrF, -ang, when (kind) {
            "cruiser", "mtb" -> 1.7f
            "road", "fixie" -> 0.9f
            else -> 1.1f
        })
        g.seg(16f, 19f + bob, 16f, 24f, 1.1f, 1f, BIKE_COL)
        g.seg(14.2f, 24f, 14.2f, 27.4f, 0.8f, 0.7f, METAL)
        g.seg(17.8f, 24f, 17.8f, 27.4f, 0.8f, 0.7f, METAL)
        g.rrect(12.6f, 23.2f, 19.4f, 24.8f, 0.8f, BIKE_DARK)
        if (style.rack) g.rrect(11.6f, 23.2f, 20.4f, 24.2f, 0.5f, METAL) // 짐받이(뒤에서 보임)
        val hipYb = 21.6f + bob
        for (i in 0 until 2) {
            val sx = if (i == 0) 1f else -1f
            val a = ang + (if (i == 0) 0f else PI.toFloat())
            val py = 25.4f + sin(a) * 2.2f
            val px = cx + sx * 3.6f + cos(a) * 0.5f
            val rootX = cx + sx * 2.4f
            val kx = ikX(rootX, hipYb, px, py, 3.6f, 3.6f, sx)
            val ky = ikY(rootX, hipYb, px, py, 3.6f, 3.6f, sx)
            val pants = if (sin(a) < 0f) pal.pants else pal.pants2
            g.seg(rootX, hipYb, kx, ky, 2f, 1.6f, pal.line)
            g.seg(kx, ky, px, py, 1.6f, 1.3f, pal.line)
            g.seg(rootX, hipYb, kx, ky, 1.5f, 1.2f, pants)
            g.seg(kx, ky, px, py, 1.15f, 0.95f, pants)
            g.rrect(px - 1.9f, py - 0.3f, px + 1.9f, py + 1.5f, 0.8f, pal.shoe)
        }
        val top = 13.6f + bob
        g.rrect(cx - 6f, top - 0.9f, cx + 6f, hipYb + 1.4f, 3.6f, pal.line)
        g.rrect(cx - 5.3f, top, cx + 5.3f, hipYb + 0.8f, 3.2f, pal.top)
        if (direction == BACK) {
            g.rrect(cx - 4.6f, top + 1f, cx + 4.6f, hipYb + 0.2f, 2.4f, pal.pack)
            g.rect(cx - 2.6f, top + 2.4f, cx + 2.6f, hipYb - 1f, pal.pack2)
        } else {
            g.rect(cx + 2.4f, top + 0.8f, cx + 5f, hipYb + 0.4f, pal.top2)
            look.gear?.vest?.let { vs ->
                g.rrect(cx - 5.3f, top + 0.6f, cx - 2.2f, hipYb + 0.6f, 1.3f, vs)
                g.rrect(cx + 2.2f, top + 0.6f, cx + 5.3f, hipYb + 0.6f, 1.3f, vs)
            }
        }
        val tilt = sway * 0.8f
        g.seg(16f - hw, 18.4f + bob - tilt, 16f + hw, 18.4f + bob + tilt, 0.9f, 0.9f, LEATHER)
        if (style.basket && direction == FRONT) {
            g.rrect(11.4f, 19.9f, 20.6f, 23.4f, 1f, 0xFFC9A05C.toInt()) // 앞바구니
            g.rect(12.2f, 21.1f, 19.8f, 21.7f, 0xFFB08840.toInt())
        }
        if (style.light) {
            g.circ(16f, 17.4f + bob, 1.3f, METAL)                        // 전조등
            g.circ(16f, 17.4f + bob, 0.75f, 0xFFF2E3C2.toInt())
        }
        if (style.bell) g.circ(16f + hw - 1.6f, 17.2f + bob + tilt, 1.05f, 0xFFD9A03C.toInt())
        for (i in 0 until 2) {
            val sx = if (i == 0) -1f else 1f
            val shx = cx + sx * 5f
            val shy = top + 2.2f
            val handX = if (sx < 0) 16f - hw + 0.4f else 16f + hw - 0.4f
            val handY = 18.4f + bob + sx * tilt
            val ex = ikX(shx, shy, handX, handY, 3.4f, 3.2f, sx)
            val ey = ikY(shx, shy, handX, handY, 3.4f, 3.2f, sx)
            g.seg(shx, shy, ex, ey, 1.8f, 1.5f, pal.line)
            g.seg(ex, ey, handX, handY, 1.45f, 1.2f, pal.line)
            g.seg(shx, shy, ex, ey, 1.35f, 1.1f, pal.top2)
            g.seg(ex, ey, handX, handY, 1f, 0.85f, pal.skin)
            g.circ(handX, handY, 1.15f, pal.skin)
        }
        g.circ(16f - hw, 18.4f + bob - tilt, 1.4f, RIM)
        g.circ(16f + hw, 18.4f + bob + tilt, 1.4f, RIM)
        if (style.streamers) {
            // 스트리머 — 손잡이에서 나풀나풀
            g.seg(16f - hw, 19.6f + bob, 16f - hw - 1.4f, 23f + bob, 0.7f, 0.4f, 0xFFDB6B9A.toInt())
            g.seg(16f - hw + 0.6f, 19.8f + bob, 16f - hw - 0.4f, 23.6f + bob, 0.6f, 0.35f, 0xFFF2D06B.toInt())
            g.seg(16f + hw, 19.6f + bob, 16f + hw + 1.4f, 23f + bob, 0.7f, 0.4f, 0xFFDB6B9A.toInt())
            g.seg(16f + hw - 0.6f, 19.8f + bob, 16f + hw + 0.4f, 23.6f + bob, 0.6f, 0.35f, 0xFFF2D06B.toInt())
        }
        // 목도리 — 목에 두르고 자락은 앞으로 남긴다
        look.gear?.scarf?.let { sc ->
            g.rrect(cx - 3.4f, top - 1.7f, cx + 3.4f, top + 0.3f, 1f, sc)
            g.seg(cx + 2.1f, top - 0.2f, cx + 3.1f + sway * 1.4f, top + 3.6f, 1f, 0.7f, sc)
        }
        val hx = cx + sway * 0.5f
        val hy = top - 5.6f
        g.circ(hx, hy, 5.4f, pal.line)
        g.circ(hx, hy, 4.7f, if (direction == FRONT) pal.skin else pal.hair)
        if (direction == FRONT) {
            // 머리카락 — 걸을 때(render의 drawHead)와 같은 헤어스타일
            g.oval(hx - 4.8f, hy - 5.1f, hx + 4.8f, hy - 0.5f, pal.hair)
            val hairLen = if (look.longHair) 5.8f else 3.4f
            g.rrect(hx - 4.8f, hy - 1.5f, hx - 3.1f, hy + hairLen, 0.8f, pal.hair)
            g.rrect(hx + 3.1f, hy - 1.5f, hx + 4.8f, hy + hairLen, 0.8f, pal.hair)
            g.poly(
                pal.hair,
                hx - 3.1f + sway * 0.4f, hy - 1.2f,
                hx - 1.2f, hy + 0.6f,
                hx - 0.4f + sway * 0.4f, hy - 1.2f
            )
            g.poly(
                pal.hair,
                hx + 0.6f + sway * 0.4f, hy - 1.1f,
                hx + 1.9f, hy + 0.6f,
                hx + 3.1f + sway * 0.4f, hy - 1.2f
            )
            g.rect(hx - 2.9f, hy + 0.4f, hx - 1.4f, hy + 2.2f, pal.eye)
            g.rect(hx + 1.4f, hy + 0.4f, hx + 2.9f, hy + 2.2f, pal.eye)
            g.rect(hx - 4.2f, hy + 2.6f, hx - 2.6f, hy + 3.7f, pal.blush)
            g.rect(hx + 2.6f, hy + 2.6f, hx + 4.2f, hy + 3.7f, pal.blush)
        } else {
            g.rect(hx - 3.6f, hy + 1.6f, hx + 3.6f, hy + 3.4f, pal.hair2)
            if (look.longHair) g.rrect(hx - 3f, hy + 1.6f, hx + 3f, hy + 6.2f, 1.8f, pal.hair2)
        }
        drawRiderCap(hx, hy, 4.7f, direction)
        return bmp
    }

    // -----------------------------------------------------------------------
    // 골목 고양이 (32x26) — 꼬리 · 귀 · 눈 · 네 다리가 따로 움직인다
    // -----------------------------------------------------------------------

    const val CAT_W = 32
    const val CAT_H = 26

    private val C_ORANGE = 0xFFE8944A.toInt()
    private val C_ORANGE2 = 0xFFC97430.toInt()
    private val C_CREAM = 0xFFFBEFD8.toInt()
    private val C_LINE = 0xFF33241C.toInt()
    private val C_EYE = 0xFF4F8F52.toInt()
    private val C_PINK = 0xFFF2A3B3.toInt()
    private val C_WHISKER = 0xCCFDF6E8.toInt()

    /** 왼쪽을 바라보는 고양이. walking=false 면 앉은 자세. */
    fun renderCat(walking: Boolean, phase: Float): Bitmap {
        val bmp = Bitmap.createBitmap(CAT_W, CAT_H, Bitmap.Config.ARGB_8888)
        val g = G(Canvas(bmp))
        val p = ((phase % 1f) + 1f) % 1f

        fun ear(cx: Float, cy: Float, twitch: Float) {
            g.poly(C_ORANGE, cx - 4.4f, cy - 2.6f, cx - 3.4f, cy - 6.8f - twitch, cx - 1f, cy - 3.4f)
            g.poly(C_ORANGE, cx + 1f, cy - 3.4f, cx + 3.2f, cy - 6.6f + twitch, cx + 4.2f, cy - 2.6f)
            g.poly(C_PINK, cx - 3.7f, cy - 3f, cx - 3.1f, cy - 5.6f - twitch * 0.8f, cx - 1.8f, cy - 3.6f)
            g.poly(C_PINK, cx + 1.8f, cy - 3.6f, cx + 3f, cy - 5.4f + twitch * 0.8f, cx + 3.6f, cy - 3f)
        }

        fun head(cx: Float, cy: Float, twitch: Float, blink: Float, look: Float) {
            g.circ(cx, cy, 5.2f, C_LINE)
            g.circ(cx, cy, 4.6f, C_ORANGE)
            ear(cx, cy, twitch)
            val eh = 1.6f * (1f - blink) + 0.25f
            g.rect(cx - 2.6f + look, cy - 1.4f, cx - 1.2f + look, cy - 1.4f + eh, C_EYE)
            g.rect(cx + 1.2f + look, cy - 1.4f, cx + 2.6f + look, cy - 1.4f + eh, C_EYE)
            g.rect(cx - 0.7f, cy + 1.4f, cx + 0.7f, cy + 2.6f, C_PINK)
            g.seg(cx - 4.6f, cy + 0.6f, cx - 8.4f, cy - 0.3f, 0.4f, 0.3f, C_WHISKER)
            g.seg(cx - 4.6f, cy + 1.8f, cx - 7.9f, cy + 2.4f, 0.4f, 0.3f, C_WHISKER)
            g.seg(cx + 4.6f, cy + 0.6f, cx + 8.4f, cy - 0.3f, 0.4f, 0.3f, C_WHISKER)
            g.seg(cx + 4.6f, cy + 1.8f, cx + 7.9f, cy + 2.4f, 0.4f, 0.3f, C_WHISKER)
        }

        if (!walking) {
            val swing = sin(TAU * p)
            val twitch = 1.2f * bump(p, 0.62f, 0.72f)
            val blink = if (p >= 0.86f && p < 0.90f) 1f else 0f
            val look = 0.6f * sin(TAU * (p - 0.15f))
            val breath = 0.35f * sin(2f * TAU * p)
            // 꼬리
            fun tailX(t: Float): Float {
                val a = -1.35f + t * (0.5f + 0.55f * swing)
                return 23f + cos(a) * (9.5f * t) * 0.75f + 0.6f * t
            }
            fun tailY(t: Float): Float {
                val a = -1.35f + t * (0.5f + 0.55f * swing)
                val r = 9.5f * t
                return 20f - kotlin.math.abs(sin(a)) * r * 0.1f - r * 0.92f
            }
            for (i in 0 until 4) {
                val t0 = i / 4f
                val t1 = (i + 1) / 4f
                g.seg(
                    tailX(t0), tailY(t0), tailX(t1), tailY(t1),
                    1.5f - 0.15f * i, 1.35f - 0.15f * i, if (i < 3) C_ORANGE2 else C_CREAM
                )
            }
            g.rrect(6.5f, 12.4f - breath * 0.3f, 24.5f, 24.6f, 6.5f, C_LINE)
            g.rrect(7.5f, 13.4f - breath * 0.3f, 23.5f, 23.6f, 5.8f, C_ORANGE)
            g.rect(11f, 13.6f, 13f, 22.6f, C_ORANGE2)
            g.rect(15.4f, 13.4f, 17.4f, 23f, C_ORANGE2)
            g.rect(19.6f, 13.8f, 21.6f, 22.4f, C_ORANGE2)
            g.rrect(9.5f, 15.5f, 19.5f, 22.5f, 3.5f, C_CREAM)
            g.rrect(9.5f, 22.4f, 13.4f, 24.9f, 1f, C_CREAM)
            g.rrect(15.8f, 22.4f, 19.6f, 24.9f, 1f, C_CREAM)
            head(14f, 9.4f - breath * 0.35f, twitch, blink, look)
            return bmp
        }

        val bob = 0.4f * cos(2f * TAU * p)
        val top = 10.4f + bob
        val swing = sin(TAU * p)
        fun tailX(t: Float): Float {
            val a = -1.5f + t * (0.35f + 0.5f * sin(TAU * (p - 0.2f)))
            return 24f + cos(a) * (10f * t) * 0.55f
        }
        fun tailY(t: Float): Float = top + 4f - (10f * t) * 0.95f
        for (i in 0 until 4) {
            val t0 = i / 4f
            val t1 = (i + 1) / 4f
            g.seg(
                tailX(t0), tailY(t0), tailX(t1), tailY(t1),
                1.45f - 0.15f * i, 1.3f - 0.15f * i, if (i < 3) C_ORANGE2 else C_CREAM
            )
        }
        fun leg(x: Float, ph: Float, back: Boolean) {
            val a = sin(TAU * (p + ph))
            val kx = x + a * 2.2f
            val ky = top + 9.4f
            val fx = x + a * 3.4f
            val fy = 24.4f - max(0f, cos(TAU * (p + ph))) * 1.8f
            val col = if (back) C_ORANGE2 else C_ORANGE
            g.seg(x, top + 7.2f, kx, ky, 1.5f, 1.2f, C_LINE)
            g.seg(kx, ky, fx, fy, 1.25f, 1f, C_LINE)
            g.seg(x, top + 7.2f, kx, ky, 1.1f, 0.85f, col)
            g.seg(kx, ky, fx, fy, 0.9f, 0.7f, col)
            g.rrect(fx - 1.5f, fy - 0.2f, fx + 1.5f, fy + 1.4f, 0.7f, if (back) C_ORANGE2 else C_CREAM)
        }
        leg(21f, 0.5f, true)
        leg(9f, 0f, true)
        g.rrect(3.5f, top, 26.5f, top + 10.2f, 5f, C_LINE)
        g.rrect(4.5f, top + 1f, 25.5f, top + 9.2f, 4.4f, C_ORANGE)
        g.rect(9f, top + 1.2f, 11f, top + 9f, C_ORANGE2)
        g.rect(14.6f, top + 1f, 16.6f, top + 9.2f, C_ORANGE2)
        g.rect(20f, top + 1.4f, 22f, top + 8.8f, C_ORANGE2)
        g.rrect(6.5f, top + 5f, 23.5f, top + 9f, 2.5f, C_CREAM)
        leg(22.6f, 0f, false)
        leg(10.6f, 0.5f, false)
        head(6.2f, 8.6f + bob, 1.1f * bump(p, 0.70f, 0.80f), 0f, -0.3f * swing)
        return bmp
    }
}
