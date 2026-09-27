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
     * HD 디테일(옷 주름·머리카락 윤기·눈 반짝임)을 넣기 시작하는 배율.
     *
     * 32px 도트에서는 이런 요소가 1px도 되지 않아 뭉개지므로 [HD_DETAIL_SCALE] 배 이상의
     * 고해상도 렌더에서만 그린다.
     */
    const val HD_DETAIL_SCALE = 4

    // HD 전용 색 — 아주 옅은 겹칠이라 도트로 줄이면 사라진다
    private val HD_FOLD = Color.argb(32, 58, 42, 34)        // 옷 주름
    private val HD_SHADE = Color.argb(26, 40, 30, 38)       // 아랫단·밑창 음영
    private val HD_HILITE = Color.argb(40, 255, 250, 232)   // 머리카락·모자 윤기
    private val HD_CHIN = Color.argb(28, 94, 60, 38)        // 턱·목 그림자
    private val HD_SPARK = Color.argb(238, 255, 255, 255)   // 눈 반짝임
    private val HD_METAL = Color.argb(64, 255, 252, 240)    // 타이어·림 광택

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

    // ------------------------------------------------------------------
    // 사람 겉모습 — 색만 다른 게 아니라 **실루엣부터 다르게** 그린다.
    // 같은 몸에 색만 바꾸면 멀리서는 다 똑같아 보이므로, 체형·헤어·모자·
    // 수염·치마·소품까지 사람마다 다른 파츠를 조합한다 (NpcRoster가 지정).
    // ------------------------------------------------------------------

    // 체형
    const val BODY_STANDARD = 0
    const val BODY_TALL = 1      // 키 크고 늘씬 (관찰원·안내원)
    const val BODY_STOCKY = 2    // 어깨 넓고 다부짐 (어민·농부)
    const val BODY_SLIM = 3      // 호리호리 (화가·카누)
    const val BODY_ROUND = 4     // 키 작고 둥글둥글 (시장 아주머니·할머니)
    const val BODY_HUNCH = 5     // 허리 굽음 (연로한 어르신)

    // 헤어스타일 (정면 기준 — 옆/뒷모습은 SHORT/LONG 으로 퉁친다)
    const val HAIR_SHORT = 0
    const val HAIR_BUZZ = 1      // 스포츠형 짧은 머리
    const val HAIR_BOB = 2       // 단발 보브
    const val HAIR_LONG = 3      // 긴 생머리
    const val HAIR_PONY = 4      // 옆으로 넘긴 포니테일
    const val HAIR_BUN = 5       // 정수리 동그란 상투
    const val HAIR_PIGTAIL = 6    // 양갈래 (꼬마)
    const val HAIR_BALD = 7      // 민머리 + 옆머리
    const val HAIR_UPDO = 8      // 올린 쪽머리 (할머니)
    const val HAIR_PERM = 9      // 파마 · 곱슬
    const val HAIR_SWEEP = 10    // 옆으로 넘긴 가르마
    const val HAIR_BRAID = 11    // 어깨 위 땋은 머리

    // 수염
    const val BEARD_NONE = 0
    const val BEARD_MUSTACHE = 1
    const val BEARD_FULL = 2
    const val BEARD_GOATEE = 3

    // 모자 — 탐조가 장비(Gear.cap)와 별개로 NPC 전용 실루엣
    const val HAT_NONE = 0
    const val HAT_CAP = 1        // 볼캡
    const val HAT_BUCKET = 2     // 털모자/버킷햇
    const val HAT_STRAW = 3      // 밀짚모자 (챙 넓음)
    const val HAT_BEANIE = 4     // 털모자 + 방울
    const val HAT_BANDANA = 5    // 반다나 두건
    const val HAT_HEADSCARF = 6  // 수건 쓴 아주머니·해녀
    const val HAT_FISHER = 7     // 방수 어부 모자 (뒷챙 긺)
    const val HAT_VISOR = 8      // 썬바이저 (정수리 뚫림)

    // 하의
    const val BOTTOM_PANTS = 0
    const val BOTTOM_SKIRT = 1
    const val BOTTOM_OVERALLS = 2

    // 소품 — 손에 들거나 몸에 걸친다 (직업이 한눈에 보인다)
    const val PROP_NONE = 0
    const val PROP_BINOCS = 1    // 목에 건 쌍안경
    const val PROP_CAMERA = 2    // 목에 건 카메라
    const val PROP_ROD = 3       // 낚싯대 (손에)
    const val PROP_BASKET = 4    // 바구니 (손에)
    const val PROP_BOOK = 5      // 책 (두 손에)
    const val PROP_BRUSH = 6     // 팔레트 + 붓 (화가)
    const val PROP_PADDLE = 7    // 어깨에 멘 노
    const val PROP_CUP = 8       // 찻잔 (손에)
    const val PROP_NET = 9       // 어깨에 멘 뜰채

    // 피부톤
    const val SKIN_FAIR = 0
    const val SKIN_NORMAL = 1
    const val SKIN_TAN = 2
    const val SKIN_DEEP = 3

    // NPC 소동작 — 같은 바디라도 소품·성격에 따라 다르게 논다
    const val FLAVOR_NONE = 0
    const val FLAVOR_BOUNCE = 1  // 깡충깡충 (개구쟁이)
    const val FLAVOR_PAINT = 2   // 붓질 (화가)
    const val FLAVOR_SIP = 3     // 찻잔 홀짝 (다방·카페)
    const val FLAVOR_SCAN = 4    // 쌍안경으로 두리번 (관찰원)
    const val FLAVOR_READ = 5    // 책 읽기 (해설사·이장)
    const val FLAVOR_NOD = 6     // 끄덕끄덕 (어르신)
    const val FLAVOR_SWAY = 7    // 갯바람에 살랑 (어민·뱃사공)

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
        val pack: Boolean = true,
        // --- 아래는 NPC 개성 파츠 (기본값 = 예전과 똑같은 모습) ---
        val body: Int = BODY_STANDARD,
        /** HAIR_* — SHORT + longHair=true 면 예전처럼 LONG 으로 그린다 */
        val hairStyle: Int = HAIR_SHORT,
        val beard: Int = BEARD_NONE,
        /** HAT_* — HAT_NONE 이면 탐조가 장비(Gear.cap)만 그린다 */
        val hat: Int = HAT_NONE,
        /** 모자 색 (0이면 머리색·옷색에서 자동) */
        val hatColor: Int = 0,
        val bottom: Int = BOTTOM_PANTS,
        /** PROP_* — 손·목·어깨 소품 */
        val prop: Int = PROP_NONE,
        /** 소품 보조색 (0이면 자동) */
        val propColor: Int = 0,
        /** 나이테 주름 (어르신) */
        val wrinkles: Boolean = false,
        /** 볼 주근깨 (꼬마) */
        val freckles: Boolean = false,
        val keepsake: String? = null
    ) {
        /** 실제 그릴 헤어스타일 — 옛 longHair 플래그를 새 번호로 매핑 */
        val hair: Int get() = if (hairStyle == HAIR_SHORT && longHair) HAIR_LONG else hairStyle
    }

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

    /**
     * 그리기 헬퍼.
     *
     * @param k 출력 배율 — 1이면 32px 도트 원본, 3이면 96px HD.
     *   그리는 좌표계(0~32)는 모든 배율에서 똑같고, 캔버스에 이 배율을 걸어 두면
     *   도형이 그만큼 촘촘하게 래스터화된다.
     *   1보다 크면 안티앨리어싱을 켠다 — 도트 시절의 계단 대신 부드러운 윤곽이 남는다.
     */
    private class G(val cv: Canvas, val k: Float = 1f) {
        init {
            if (k != 1f) cv.scale(k, k)
        }

        val p = Paint().apply { isAntiAlias = k > 1.0001f }
        val path = Path()
        val rf = RectF()

        /** 한 도트보다 가는 디테일(실밥·단추·눈 반짝임) — 3배에서 약 1.3px */
        val fine: Float get() = 0.42f

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

    /** NPC 성격별 대기 동작 — 예전 시그니처(소품·시드 없이) */
    fun npcPose(kind: Int, phase: Float): Pose = npcPose(kind, phase, FLAVOR_NONE, 0f)

    /**
     * NPC 성격별 대기 동작.
     *
     * @param flavor FLAVOR_* — 소품·직업에 맞는 소동작 (붓질·홀짝·두리번…).
     *   같은 VILLAGER 라도 쌍안경 든 관찰원과 붓 든 화가가 다르게 논다.
     * @param seed 사람마다 다른 난수(0~1) — 숨결 크기·깜빡임 타이밍·두리번
     *   속도가 조금씩 어긋나서, 옆에 서 있어도 박자가 겹치지 않는다.
     */
    fun npcPose(kind: Int, phase: Float, flavor: Int, seed: Float): Pose {
        val sd = ((seed % 1f) + 1f) % 1f
        val p = ((phase + sd * 0.61f) % 1f + 1f) % 1f
        val energy = 0.82f + 0.36f * frac(sd * 7.31f)
        val base = idlePose(p)
        // 사람마다 눈 깜빡임 타이밍이 다르다
        val blinkAt = 0.80f + 0.12f * frac(sd * 13.7f)
        val blink = if (p >= blinkAt && p < blinkAt + 0.04f) 1f
        else if (p >= blinkAt + 0.04f && p < blinkAt + 0.06f) 0.5f else base.blink
        val posed = when (kind) {
            NPC_PROFESSOR -> {
                val push = bump(p, 0.55f, 0.85f)
                base.copy(
                    headY = base.headY + 0.55f * sin(2f * TAU * p) * energy,
                    tilt = base.tilt + 1.4f * sin(TAU * p) * energy,
                    armR = -150f * push + 3f,
                    elbowR = 8f + 112f * push,
                    brow = 0.5f * push,
                    blink = if (p >= 0.60f && p < 0.64f) 1f else blink
                )
            }
            NPC_SHOP -> {
                val wave = bump(p, 0.10f, 0.62f)
                base.copy(
                    armR = -20f - 128f * wave,
                    elbowR = 12f + 26f * wave + 22f * wave * sin(TAU * 3f * p),
                    mouth = 0.5f * wave,
                    headX = base.headX + 0.3f * wave,
                    tilt = base.tilt - 1.2f * wave,
                    blink = blink
                )
            }
            NPC_VILLAGER -> {
                val look = sin(TAU * p)
                base.copy(
                    turn = 0.9f * look * energy, headX = 0.9f * look * energy,
                    tilt = -1.8f * look * energy, bodyX = 0.5f * look,
                    blink = blink
                )
            }
            NPC_KID -> {
                val hop = Math.pow(max(0f, sin(TAU * 2f * p)).toDouble(), 0.8).toFloat()
                base.copy(
                    bodyY = -3.2f * hop * energy,
                    kneeL = 16f + 26f * (1f - hop), kneeR = 16f + 26f * (1f - hop),
                    hipL = -9f * hop, hipR = 9f * hop,
                    footL = -18f * hop, footR = -18f * hop,
                    armL = -26f - 52f * hop, armR = 26f + 52f * hop,
                    elbowL = 18f, elbowR = 18f,
                    mouth = 0.8f,
                    hairSway = -1.4f * hop,
                    headY = -0.5f * hop,
                    blink = blink
                )
            }
            NPC_ELDER -> {
                val tap = bump(p, 0.44f, 0.60f)
                base.copy(
                    bodyY = base.bodyY + 0.5f + 0.25f * sin(TAU * p) * energy,
                    lean = 8f, crouch = 0.35f,
                    armR = 16f + 10f * tap, elbowR = 14f,
                    tilt = base.tilt + 1f,
                    headY = base.headY + 0.6f,
                    blink = blink
                )
            }
            else -> base.copy(blink = blink)
        }
        if (flavor == FLAVOR_NONE) return posed
        return when (flavor) {
            FLAVOR_BOUNCE -> {
                // 개구쟁이 — 폴짝폴짝 + 팔 흔들
                val hop = Math.pow(max(0f, sin(TAU * 2f * p)).toDouble(), 0.7).toFloat()
                posed.copy(
                    bodyY = posed.bodyY - 2.4f * hop * energy,
                    kneeL = posed.kneeL + 14f * (1f - hop), kneeR = posed.kneeR + 14f * (1f - hop),
                    armL = posed.armL - 34f * hop, armR = posed.armR + 34f * hop,
                    mouth = max(posed.mouth, 0.7f * hop),
                    hairSway = posed.hairSway - 1.2f * hop
                )
            }
            FLAVOR_PAINT -> {
                // 화가 — 오른팔로 슥슥 붓질 + 고개 갸웃
                val stroke = sin(TAU * 2f * p)
                posed.copy(
                    armR = -64f + 26f * stroke, elbowR = 34f,
                    armL = posed.armL - 18f, elbowL = 52f,
                    tilt = posed.tilt + 2.2f * sin(TAU * p),
                    brow = max(posed.brow, 0.4f),
                    mouth = max(posed.mouth, 0.25f)
                )
            }
            FLAVOR_SIP -> {
                // 찻잔 홀짝 — 주기적으로 잔을 입으로
                val sip = bump(p, 0.42f, 0.72f)
                posed.copy(
                    armR = posed.armR * (1f - sip) + -118f * sip,
                    elbowR = posed.elbowR * (1f - sip) + 96f * sip,
                    headY = posed.headY - 0.5f * sip,
                    tilt = posed.tilt - 1.6f * sip,
                    mouth = max(posed.mouth, 0.55f * sip),
                    blink = max(posed.blink, 0.5f * sip)
                )
            }
            FLAVOR_SCAN -> {
                // 관찰원 — 쌍안경 든 채 좌우를 훑는다
                val scan = sin(TAU * p * 0.75f + sd)
                posed.copy(
                    turn = 1.1f * scan, headX = posed.headX + 1.2f * scan,
                    tilt = posed.tilt - 0.8f + 0.5f * sin(TAU * 2f * p),
                    headY = posed.headY - 0.6f,
                    armL = posed.armL - 24f, elbowL = 46f,
                    armR = posed.armR + 24f, elbowR = 46f,
                    brow = max(posed.brow, 0.5f)
                )
            }
            FLAVOR_READ -> {
                // 책 읽기 — 고개 숙이고 양손으로 책
                val page = bump(p, 0.60f, 0.78f)
                posed.copy(
                    headY = posed.headY + 1.1f,
                    tilt = posed.tilt + 0.8f,
                    armL = -52f - 8f * page, elbowL = 74f,
                    armR = 52f + 8f * page, elbowR = 74f,
                    brow = max(posed.brow, 0.35f)
                )
            }
            FLAVOR_NOD -> {
                // 어르신 끄덕임 — 느리고 깊게
                val nod = 0.5f - 0.5f * cos(TAU * p)
                posed.copy(
                    headY = posed.headY + 0.9f * nod,
                    tilt = posed.tilt + 2.4f * nod,
                    bodyY = posed.bodyY + 0.4f * nod,
                    mouth = max(posed.mouth, 0.3f * nod)
                )
            }
            FLAVOR_SWAY -> {
                // 갯바람 — 몸을 좌우로 크게 실랑실랑
                val sway = sin(TAU * p * 0.9f + sd * 2f)
                posed.copy(
                    bodyX = posed.bodyX + 0.9f * sway,
                    tilt = posed.tilt - 2.6f * sway,
                    headX = posed.headX + 0.8f * sway,
                    clothSway = posed.clothSway + 1.2f * sway,
                    hairSway = posed.hairSway + 1f * sway
                )
            }
            else -> posed
        }
    }

    private fun frac(x: Float): Float = x - kotlin.math.floor(x.toDouble()).toFloat()

    // -----------------------------------------------------------------------
    // 사람 렌더링
    // -----------------------------------------------------------------------

    fun render(direction: Int, pose: Pose, look: Look): Bitmap = render(direction, pose, look, SIZE)

    /**
     * 캐릭터를 그린다.
     *
     * @param size 출력 비트맵 한 변(px). [SIZE]면 원래의 32px 도트,
     *   [SIZE]의 정수배(= 슈퍼샘플 · HD)면 비율은 그대로이면서
     *   코드로 그리는 벡터 도형이 그만큼 촘촘하게 래스터화되어 훨씬 부드럽다.
     *   [HD_DETAIL_SCALE] 배 이상일 때만 옷 주름·윤기·눈 반짝임 같은 HD 디테일을 더한다.
     *
     * `tools/preview/people.py` 미리보기는 32px(=기본값)라 도트 결과가 같고,
     * 프리뷰 스크린샷 파이프라인은 4배(128px)로 HD 결과를 확인한다.
     */
    fun render(direction: Int, pose: Pose, look: Look, size: Int): Bitmap {
        val k = size.toFloat() / SIZE
        val hd = k >= HD_DETAIL_SCALE - 0.001f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val g = G(Canvas(bmp), k)
        val pal = look.pal
        val sc = if (look.small) 0.86f else 1f

        // 체형 — 키·어깨·머리·팔다리 굵기가 사람마다 다르다
        val legMul: Float; val torsoHMul: Float; val torsoWMul: Float
        val headMul: Float; val lw: Float; val hunch: Float
        when (look.body) {
            BODY_TALL -> {
                legMul = 1.12f; torsoHMul = 1.08f; torsoWMul = 0.94f
                headMul = 0.94f; lw = 0.95f; hunch = 0f
            }
            BODY_STOCKY -> {
                legMul = 0.94f; torsoHMul = 0.96f; torsoWMul = 1.26f
                headMul = 1.07f; lw = 1.22f; hunch = 0f
            }
            BODY_SLIM -> {
                legMul = 1.04f; torsoHMul = 1.02f; torsoWMul = 0.80f
                headMul = 0.94f; lw = 0.84f; hunch = 0f
            }
            BODY_ROUND -> {
                legMul = 0.86f; torsoHMul = 0.90f; torsoWMul = 1.36f
                headMul = 1.12f; lw = 1.05f; hunch = 0f
            }
            BODY_HUNCH -> {
                legMul = 0.92f; torsoHMul = 0.94f; torsoWMul = 1.08f
                headMul = 1.0f; lw = 1.0f; hunch = 1f
            }
            else -> {
                legMul = 1f; torsoHMul = 1f; torsoWMul = 1f
                headMul = 1f; lw = 1f; hunch = 0f
            }
        }

        val ground = 31.4f
        val footH = 1.7f * sc
        val legLen = 8.3f * sc * legMul
        val thigh = 4.2f * sc * legMul
        val shin = 3.9f * sc * legMul
        val torsoH = 6.1f * sc * torsoHMul
        val headR = 6.5f * sc * headMul
        val headGap = headR * 0.97f

        var hipY = ground - footH - legLen + pose.bodyY + pose.crouch * 2.4f * sc
        if (look.small) hipY += 0.6f
        val hipX = 16f + pose.bodyX
        val leanPx = pose.lean * 0.085f * (if (direction == SIDE) 1f else 0f)
        val shoulderY = hipY - torsoH + pose.crouch * 0.6f + hunch * 0.8f
        val shoulderX = hipX + leanPx
        val headCx = shoulderX + pose.headX + leanPx * 0.8f + pose.tilt * 0.12f
        val headCy = shoulderY - headGap + pose.headY - pose.breath * 0.25f + hunch * 1.3f

        val depth = if (direction == SIDE) 1f else 0.32f
        val face = if (direction != BACK) 1f else -1f

        val tL: Float; val tR: Float
        if (direction == SIDE) {
            val hw = 5f * sc * (0.75f + 0.25f * torsoWMul)
            tL = shoulderX - hw; tR = shoulderX + hw
        } else {
            tL = shoulderX - 6.2f * sc * torsoWMul; tR = shoulderX + 6.2f * sc * torsoWMul
        }
        val hipHalf = 2.45f * sc * (0.7f + 0.3f * torsoWMul)
        val shHalf = (if (direction == SIDE) 5f else 7f) * sc * (0.6f + 0.4f * torsoWMul)

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
            // 치마 차림은 정면에서 맨다리 (종아리만 피부색)
            val bare = look.bottom == BOTTOM_SKIRT && direction == FRONT
            val shinCol = if (bare) (if (back) pal.skin2 else pal.skin) else pants
            // 관절(FK)은 hipY 에서 시작하지만, 눈에 보이는 허벅지는 몸통 밑단에서
            // 시작하게 잘라 낸다 — 몸통 위에 그려지는 앞다리가 상체 한가운데서
            // 튀어나와 보이던 문제 방지.
            val legTop = hipY + 1.7f * sc
            val t = if (ky - hipY > 0.001f) ((legTop - hipY) / (ky - hipY)).coerceIn(0f, 0.85f) else 0f
            val sx = x0 + (kx - x0) * t
            val sy = hipY + (ky - hipY) * t
            g.seg(sx, sy, kx, ky, 2.15f * sc * lw, 1.8f * sc * lw, pal.line)
            g.seg(kx, ky, ax, ay, 1.75f * sc * lw, 1.45f * sc * lw, pal.line)
            g.seg(sx, sy - 0.3f, kx, ky, 1.7f * sc * lw, 1.4f * sc * lw, pants)
            g.seg(kx, ky, ax, ay, 1.35f * sc * lw, 1.1f * sc * lw, shinCol)
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
                if (hd) {
                    // 밑창 라인 + 발끝 광 — 신발이 한 덩어리로 뭉개지지 않게
                    g.rect(ax - fw + 0.3f, ay + fh - 0.5f, ax + fw - 0.3f, ay + fh, HD_SHADE)
                    g.rect(ax - fw + 0.4f, ay - 0.1f, ax + fw - 0.4f, ay + 0.2f, HD_HILITE)
                }
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
            g.seg(x0, y0, ex, ey, 1.85f * sc * lw, 1.5f * sc * lw, pal.line)
            g.seg(ex, ey, hx, hy, 1.45f * sc * lw, 1.2f * sc * lw, pal.line)
            g.seg(x0, y0, ex, ey, 1.4f * sc * lw, 1.1f * sc * lw, sleeve)
            g.seg(ex, ey, hx, hy, 1f * sc * lw, 0.85f * sc * lw, skin)
            g.circ(hx, hy, 1.25f * sc * lw, pal.line)
            g.circ(hx, hy, 0.95f * sc * lw, skin)
            if (hd) {
                // 소매 끝 동그랗게 마감 + 손등 광 — 팔이 막대기처럼 보이지 않게
                val m = 0.85f
                val ux = (ex - x0) / max(hypot(ex - x0, ey - y0), 0.001f)
                val uy = (ey - y0) / max(hypot(ex - x0, ey - y0), 0.001f)
                g.seg(ex - ux * m, ey - uy * m, ex, ey, 1.45f * sc, 1.3f * sc, shade(sleeve, 0.88f))
                g.circ(hx - 0.35f, hy - 0.4f, g.fine, HD_HILITE)
            }
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
            if (direction != BACK) {
                // Collar and bright buttons give the torso a readable structure.
                g.rect(shoulderX - 2.2f, top + 0.2f, shoulderX + 2.2f, top + 1.1f, pal.skin2)
                g.rect(shoulderX - 0.4f, top + 2.3f, shoulderX + 0.4f, top + 3.1f, 0xFFFFEAD0.toInt())
                g.rect(shoulderX - 0.4f, top + 4.2f, shoulderX + 0.4f, top + 5f, 0xFFFFEAD0.toInt())
            }
            if (look.apron) {
                g.rrect(tL + 1.6f, top + 2.4f, tR - 1.6f, bot - 0.6f, 2.4f, 0xFFFDF6E8.toInt())
                g.rect(tL + 3f, top + 2.4f, tR - 3f, top + 3.7f, 0xFFE8DFC8.toInt())
                g.rect(tL + 4f, top + 7.6f, tR - 4f, top + 8.9f, 0xFFE8DFC8.toInt())
            }
            if (look.bottom == BOTTOM_OVERALLS && direction != BACK) {
                // 멜빵바지 — 가슴판 + 어깨끈 + 놋쇠 단추
                val mid = (tL + tR) / 2f
                val bibL = mid - 3.2f
                val bibR = mid + 3.2f
                g.rect(bibL + 1f, top - 0.6f, bibL + 2.4f, top + 1.8f, pal.pants)
                g.rect(bibR - 2.4f, top - 0.6f, bibR - 1f, top + 1.8f, pal.pants)
                g.rrect(bibL, top + 1.2f, bibR, top + 6.6f, 1f, pal.line)
                g.rrect(bibL + 0.4f, top + 1.6f, bibR - 0.4f, top + 6.2f, 0.8f, pal.pants)
                g.circ(bibL + 1.2f, top + 2.7f, 0.55f, 0xFFF2D06B.toInt())
                g.circ(bibR - 1.2f, top + 2.7f, 0.55f, 0xFFF2D06B.toInt())
                if (hd) g.rect(bibL + 0.6f, top + 5.4f, bibR - 0.6f, top + 5.8f, HD_SHADE)
            }
            if (hd) {
                // 옷 주름 2~3줄 + 어깨 하이라이트 — 4배(128px)에서 비로소 보이는 결
                val foldX = tL + (tR - tL) * 0.42f
                g.rect(foldX, top + 1.6f, foldX + 0.42f, bot - 1.2f, HD_FOLD)
                val foldX2 = tL + (tR - tL) * 0.62f
                g.rect(foldX2, top + 3.2f, foldX2 + 0.34f, bot - 2.2f, HD_FOLD)
                g.rect(tL + 0.2f, top + 0.4f, tR - 0.2f, top + 0.9f, HD_HILITE)
                g.rect(tL - 0.5f, bot - 0.9f, tR + 0.5f, bot - 0.4f, HD_SHADE)
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
            if (hd) {
                // 버클 + 가죽 끈 + 주머니 테두리 — 배낭이 밋밋한 사각형이 되지 않게
                val bw = 0.9f
                g.rrect(hipX - bw, shoulderY + 3.6f + by, hipX + bw, shoulderY + 5.6f + by, 0.3f, 0xFFD9A03C.toInt())
                g.rect(hipX - 0.6f, shoulderY + 4f + by, hipX + 0.6f, shoulderY + 4.7f + by, 0xFF7A5A24.toInt())
                g.rect(hipX - 4.1f, shoulderY + 8.1f + by, hipX + 4.1f, shoulderY + 8.4f + by, HD_SHADE)
                g.rect(hipX - 4.1f, shoulderY + 2.1f + by, hipX - 3.7f, hipY + by, HD_HILITE)
            }
        }

        // ---- 앞모습 헤어스타일 — 사람마다 실루엣이 다르다 ---------------------------
        fun drawFrontHair(cx: Float, cy: Float, sway: Float, capBot: Float, hs: Int) {
            fun bangs() {
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
            }
            fun sideLocks(len: Float = 4.4f) {
                g.rrect(cx - headR * 0.98f, cy - 2.6f, cx - headR * 0.44f, cy + len, 0.9f, pal.hair)
                g.rrect(cx + headR * 0.44f, cy - 2.6f, cx + headR * 0.98f, cy + len, 0.9f, pal.hair)
            }
            fun topCap(bot: Float = capBot) {
                g.oval(
                    cx - headR - 0.15f + sway * 0.18f, cy - headR - 0.35f,
                    cx + headR + 0.15f + sway * 0.18f, bot, pal.hair
                )
            }
            fun longStrands() {
                g.rrect(cx - headR - 0.7f, cy - 1.2f, cx - headR + 1.5f, cy + 7.4f + sway * 0.3f, 1.1f, pal.hair2)
                g.rrect(cx + headR - 1.5f, cy - 1.2f, cx + headR + 0.7f, cy + 7.4f - sway * 0.3f, 1.1f, pal.hair2)
                if (hd) {
                    g.rect(cx - headR - 0.2f, cy + 0.4f, cx - headR + 0.1f, cy + 6.6f + sway * 0.3f, HD_HILITE)
                    g.rect(cx + headR - 0.1f, cy + 0.4f, cx + headR + 0.2f, cy + 6.6f - sway * 0.3f, HD_HILITE)
                }
            }
            when (hs) {
                HAIR_BUZZ -> {
                    // 스포츠형 — 윗머리만 얇게, 관자놀이는 피부
                    g.oval(cx - headR * 0.80f, cy - headR - 0.3f, cx + headR * 0.80f, capBot - 2.2f, pal.hair)
                }
                HAIR_BOB -> {
                    // 턱선까지 둥글게 내려오는 단발
                    topCap()
                    g.rrect(cx - headR - 0.7f, cy - 2.2f, cx - headR + 2.3f, cy + 5.8f, 2f, pal.hair)
                    g.rrect(cx + headR - 2.3f, cy - 2.2f, cx + headR + 0.7f, cy + 5.8f, 2f, pal.hair)
                    g.rect(cx - 4.4f + sway * 0.5f, capBot - 1.8f, cx + 4.4f + sway * 0.5f, capBot + 0.7f, pal.hair)
                }
                HAIR_LONG -> {
                    topCap(); sideLocks(); bangs(); longStrands()
                }
                HAIR_PONY -> {
                    topCap(); sideLocks()
                    g.poly(
                        pal.hair,
                        cx - 4.2f + sway, capBot - 1.4f,
                        cx + 4.3f + sway, capBot - 1.4f,
                        cx + 0.8f + sway, capBot + 0.9f,
                        cx - 0.6f + sway, capBot + 0.9f
                    )
                    // 오른쪽으로 넘긴 포니테일 — 걸음에 흔들린다
                    val px = cx + headR - 0.4f + sway * 0.9f
                    g.seg(cx + headR - 1.6f, cy - 2f, px + 0.6f, cy + 6.4f + sway * 0.5f, 1.7f, 1f, pal.hair2)
                    g.circ(cx + headR - 1.6f, cy - 2f, 1f, pal.blush)
                }
                HAIR_BUN -> {
                    topCap(capBot - 0.6f); sideLocks(); bangs()
                    // 정수리 동그란 상투
                    g.circ(cx + sway * 0.3f, cy - headR - 1.6f, 2.6f, pal.line)
                    g.circ(cx + sway * 0.3f, cy - headR - 1.6f, 1.9f, pal.hair)
                    g.circ(cx - 0.5f + sway * 0.3f, cy - headR - 2.2f, 0.7f, pal.hair2)
                }
                HAIR_PIGTAIL -> {
                    topCap(); bangs()
                    // 양갈래 — 좌우로 삐죽 + 리본 끈
                    val lift = 0.4f * sway
                    g.circ(cx - headR - 1.2f, cy - 0.6f + lift, 2.2f, pal.line)
                    g.circ(cx - headR - 1.2f, cy - 0.6f + lift, 1.6f, pal.hair)
                    g.circ(cx + headR + 1.2f, cy - 0.6f - lift, 2.2f, pal.line)
                    g.circ(cx + headR + 1.2f, cy - 0.6f - lift, 1.6f, pal.hair)
                    g.circ(cx - headR + 0.4f, cy - 1.4f, 0.9f, pal.blush)
                    g.circ(cx + headR - 0.4f, cy - 1.4f, 0.9f, pal.blush)
                }
                HAIR_BALD -> {
                    // 민머리 — 정수리 광 + 옆머리만
                    if (hd) g.oval(cx - headR * 0.5f, cy - headR * 0.7f, cx + headR * 0.5f, cy - headR * 0.1f, HD_HILITE)
                    g.rrect(cx - headR * 0.98f, cy + 0.4f, cx - headR * 0.44f, cy + 4.4f, 0.9f, pal.hair)
                    g.rrect(cx + headR * 0.44f, cy + 0.4f, cx + headR * 0.98f, cy + 4.4f, 0.9f, pal.hair)
                }
                HAIR_UPDO -> {
                    // 쪽머리 — 위로 올려 묶음 (앞머리 없음, 이마 훤함)
                    g.oval(cx - headR - 0.1f, cy - headR - 0.3f, cx + headR + 0.1f, capBot - 1.8f, pal.hair)
                    g.circ(cx, cy - headR - 1.2f, 3f, pal.line)
                    g.circ(cx, cy - headR - 1.2f, 2.3f, pal.hair2)
                    g.rect(cx - 1.2f, cy - headR - 2.2f, cx + 1.2f, cy - headR - 1.6f, pal.hair)
                    g.rrect(cx - headR * 0.95f, cy - 1.6f, cx - headR * 0.5f, cy + 3.4f, 0.9f, pal.hair)
                    g.rrect(cx + headR * 0.5f, cy - 1.6f, cx + headR * 0.95f, cy + 3.4f, 0.9f, pal.hair)
                }
                HAIR_PERM -> {
                    // 파마 — 동그란 곱슬 뭉치
                    val oxs = floatArrayOf(-0.72f, -0.30f, 0.18f, 0.62f, -0.88f, 0.88f, -0.80f, 0.80f)
                    val oys = floatArrayOf(-0.72f, -0.92f, -0.90f, -0.68f, -0.28f, -0.28f, 0.22f, 0.22f)
                    for (i in oxs.indices) {
                        g.circ(cx + headR * oxs[i] + sway * 0.2f, cy + headR * oys[i], 2.3f, pal.line)
                        g.circ(cx + headR * oxs[i] + sway * 0.2f, cy + headR * oys[i], 1.7f, pal.hair)
                    }
                    g.oval(cx - headR * 0.9f, cy - headR * 0.9f, cx + headR * 0.9f, capBot - 0.6f, pal.hair)
                }
                HAIR_SWEEP -> {
                    // 옆 가르마 — 이마를 가로지르는 앞머리
                    topCap(capBot - 0.4f)
                    sideLocks()
                    g.poly(
                        pal.hair,
                        cx - headR * 0.7f + sway * 0.4f, capBot - 2.6f,
                        cx + headR * 0.75f + sway * 0.6f, capBot - 0.6f,
                        cx + headR * 0.5f + sway * 0.6f, capBot + 1.2f,
                        cx - headR * 0.55f + sway * 0.4f, capBot - 0.8f
                    )
                }
                HAIR_BRAID -> {
                    topCap(); sideLocks(); bangs()
                    // 왼쪽은 길게, 오른쪽은 땋아 앞으로 넘김
                    g.rrect(cx - headR - 0.7f, cy - 1.2f, cx - headR + 1.5f, cy + 7.4f + sway * 0.3f, 1.1f, pal.hair2)
                    var brY = cy + 2.4f
                    val bx = cx + headR - 0.8f + sway * 0.4f
                    repeat(3) {
                        g.rrect(bx - 1.6f, brY, bx + 1.6f, brY + 2.5f, 1f, if (it % 2 == 0) pal.hair else pal.hair2)
                        brY += 2.3f
                    }
                    g.circ(bx, brY + 0.5f, 1f, pal.blush)
                }
                else -> {
                    // SHORT — 예전과 동일한 기본형
                    topCap(); sideLocks(); bangs()
                }
            }
        }

        // ---- 옆모습 헤어 — 앞모습 특징만 살짝 얹는다 (NPC는 정면만 쓰므로 간략히) ----
        fun drawSideHair(cx: Float, cy: Float, sway: Float, capBot: Float, hs: Int) {
            if (hs == HAIR_BALD) {
                g.rrect(cx - headR - 0.2f, cy + 1.4f, cx - headR + 2.7f, cy + 5f, 1.2f, pal.hair)
                return
            }
            if (hs == HAIR_BUZZ) {
                g.oval(cx - headR * 0.8f, cy - headR - 0.3f, cx + headR * 0.8f, capBot - 2f, pal.hair)
                return
            }
            g.oval(
                cx - headR - 0.15f + sway * 0.18f, cy - headR - 0.35f,
                cx + headR + 0.15f + sway * 0.18f, capBot, pal.hair
            )
            val backLen = if (hs == HAIR_LONG || hs == HAIR_BRAID) 7.4f else 5f
            g.rrect(cx - headR - 0.2f, cy - 2.6f, cx - headR + 2.7f, cy + backLen, 1.2f, pal.hair)
            g.rect(cx - headR + 1.2f, cy + 1.4f, cx - headR + 2.7f, cy + backLen + sway * 0.35f, pal.hair2)
            g.poly(
                pal.hair,
                cx + 0.4f, capBot - 2.2f,
                cx + headR + 0.9f + sway, capBot - 0.4f,
                cx + headR - 1.8f, capBot + 1.3f,
                cx + 0.6f, capBot + 0.4f
            )
            if (hs == HAIR_BUN || hs == HAIR_UPDO) {
                g.circ(cx - 1f, cy - headR - 1.4f, 2.2f, pal.line)
                g.circ(cx - 1f, cy - headR - 1.4f, 1.6f, if (hs == HAIR_UPDO) pal.hair2 else pal.hair)
            }
            if (hs == HAIR_PONY) {
                g.seg(cx - headR + 0.4f, cy - 2f, cx - headR - 1.6f + sway * 0.8f, cy + 5.6f, 1.6f, 0.9f, pal.hair2)
            }
        }

        // ---- 머리 ------------------------------------------------------------
        fun drawHead() {
            val cx = headCx
            val cy = headCy
            val tilt = pose.tilt
            val turn = pose.turn * face
            g.circ(cx, cy, headR + 0.85f, pal.line)
            if (direction == BACK) {
                val hs = look.hair
                g.circ(cx, cy, headR, if (hs == HAIR_BALD) pal.skin else pal.hair)
                if (hd) {
                    // 뒤통수 윤기 — 뒷모습도 머리카락으로 보이게
                    g.oval(cx - headR * 0.68f, cy - headR * 0.72f, cx + headR * 0.68f, cy - headR * 0.05f, HD_HILITE)
                }
                if (hs != HAIR_BALD) {
                    g.rect(cx - headR * 0.85f, cy + 1.9f, cx + headR * 0.85f, cy + 3.7f, pal.hair2)
                    g.rect(
                        cx - headR * 0.6f + pose.hairSway, cy - headR - 0.4f,
                        cx + headR * 0.6f + pose.hairSway, cy - headR + 1.1f, pal.hair2
                    )
                }
                if (hs == HAIR_LONG || hs == HAIR_BRAID || look.longHair) {
                    g.rrect(cx - 4.2f, cy + 2f, cx + 4.2f, cy + 8.4f, 2.4f, pal.hair2)
                }
                if (hs == HAIR_BUN || hs == HAIR_UPDO) {
                    g.circ(cx, cy - headR - 1f, 2.6f, pal.line)
                    g.circ(cx, cy - headR - 1f, 1.9f, if (hs == HAIR_UPDO) pal.hair2 else pal.hair)
                }
                if (hs == HAIR_PONY) {
                    g.seg(cx + 1f, cy - headR + 1f, cx + 2.6f + pose.hairSway, cy + 5.6f, 1.7f, 1f, pal.hair2)
                }
                return
            }

            g.circ(cx, cy, headR, pal.skin)
            val sway = pose.hairSway
            val capBot = cy - 0.5f
            if (hd) {
                // 턱·목 그림자 — 머리가 얼굴 판때기처럼 붙지 않게 살짝 얹는다
                g.oval(cx - headR * 0.72f, cy + headR * 0.45f, cx + headR * 0.72f, cy + headR + 1.1f, HD_CHIN)
            }
            // 수건 두건은 머리카락을 통째로 감싸므로 헤어를 그리지 않는다
            if (look.hat != HAT_HEADSCARF) {
                if (direction == SIDE) drawSideHair(cx, cy, sway, capBot, look.hair)
                else drawFrontHair(cx, cy, sway, capBot, look.hair)
            }
            if (hd) {
                // 앞머리 윤기 한 줄 — 앞모습·옆모습 모두
                g.oval(
                    cx - headR * 0.62f + sway * 0.18f, cy - headR * 0.55f,
                    cx + headR * 0.62f + sway * 0.18f, cy - headR * 0.05f, HD_HILITE
                )
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
                    if (hd) {
                        // 눈동자 한가운데 반짝임 — 표정이 살아난다
                        g.circ(lx + 0.95f, ey - 0.05f, g.fine, HD_SPARK)
                        g.circ(rx + 0.95f, ey - 0.05f, g.fine, HD_SPARK)
                        g.rect(lx - 0.1f, ey - 1.35f, lx + 1.6f, ey - 1.1f, pal.hair2)
                        g.rect(rx - 0.1f, ey - 1.35f, rx + 1.6f, ey - 1.1f, pal.hair2)
                    }
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

            // 수염 — 입 위에 얹는다 (입은 수염 뒤로 숨는다)
            if (direction != BACK) {
                when (look.beard) {
                    BEARD_MUSTACHE -> {
                        if (direction == SIDE) {
                            g.rect(ex + 3.4f, ey + 1.1f, ex + 6f, ey + 2.1f, pal.hair2)
                        } else {
                            g.rrect(ex - 2.6f, ey + 1.8f, ex - 0.2f, ey + 2.9f, 0.5f, pal.hair2)
                            g.rrect(ex + 0.2f, ey + 1.8f, ex + 2.6f, ey + 2.9f, 0.5f, pal.hair2)
                        }
                    }
                    BEARD_FULL -> {
                        if (direction == SIDE) {
                            g.rrect(ex + 0.4f, ey + 1.6f, ex + 6.2f, ey + 6.4f, 1.6f, pal.hair)
                            g.rect(ex + 3.4f, ey + 1.1f, ex + 6f, ey + 2.1f, pal.hair2)
                        } else {
                            g.rrect(ex - headR * 0.72f, ey + 1.9f, ex + headR * 0.72f, ey + 6.6f, 2.6f, pal.hair)
                            g.rrect(ex - headR * 0.5f, ey + 2.6f, ex + headR * 0.5f, ey + 5.6f, 2f, pal.hair2)
                            g.rrect(ex - 2.6f, ey + 1.8f, ex + 2.6f, ey + 2.9f, 0.5f, pal.hair2)
                        }
                    }
                    BEARD_GOATEE -> {
                        if (direction == SIDE) {
                            g.rect(ex + 3.8f, ey + 2.6f, ex + 5.6f, ey + 4.6f, pal.hair2)
                        } else {
                            g.rrect(ex - 1.6f, ey + 3.2f, ex + 1.6f, ey + 5.4f, 0.8f, pal.hair2)
                        }
                    }
                }
            }
            if (direction == FRONT) {
                if (look.wrinkles) {
                    // 나이테 — 이마 주름 2줄 + 눈가 주름
                    g.rect(ex - 3.4f, ey - 3.6f, ex + 3.6f, ey - 3.2f, pal.skin2)
                    g.rect(ex - 2.8f, ey - 4.6f, ex + 3f, ey - 4.2f, pal.skin2)
                    g.rect(ex - 4.4f, ey - 0.4f, ex - 3.4f, ey, pal.skin2)
                    g.rect(ex + 3.6f, ey - 0.4f, ex + 4.6f, ey, pal.skin2)
                }
                if (look.freckles) {
                    // 볼 주근깨
                    val fk = 0xFFC98A5E.toInt()
                    g.rect(ex - 4.1f, ey + 0.6f, ex - 3.5f, ey + 1.2f, fk)
                    g.rect(ex - 3.2f, ey + 1.1f, ex - 2.6f, ey + 1.7f, fk)
                    g.rect(ex + 2.8f, ey + 1.1f, ex + 3.4f, ey + 1.7f, fk)
                    g.rect(ex + 3.7f, ey + 0.6f, ex + 4.3f, ey + 1.2f, fk)
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
                if (hd) g.seg(shoulderX - 2f, ny + 0.6f, shoulderX - 2.7f + pose.clothSway * 1.6f, ny + 4.1f, 0.4f, 0.3f, shade(scarf, 1.35f))
            } else {
                g.rrect(shoulderX - 4.6f, ny - 0.9f, shoulderX + 4.6f, ny + 1.6f, 1.2f, scarf)
                g.seg(shoulderX + 2.4f, ny + 1f, shoulderX + 3f + pose.clothSway * 1.6f, ny + 5f, 1.1f, 0.8f, scarf)
                if (hd) {
                    g.seg(shoulderX + 2.1f, ny + 1.1f, shoulderX + 2.6f + pose.clothSway * 1.6f, ny + 4.4f, 0.4f, 0.3f, shade(scarf, 1.35f))
                    g.rect(shoulderX - 4.4f, ny - 0.7f, shoulderX + 4.4f, ny - 0.2f, HD_HILITE)
                }
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
                if (hd) {
                    g.rect(tR - 1.4f, top + 0.8f, tR - 0.9f, bot - 0.3f, HD_HILITE)
                    g.rect(tL + 1f, bot - 0.8f, tR - 0.5f, bot - 0.35f, HD_SHADE)
                }
            } else {
                g.rrect(tL - 0.2f, top + 0.6f, tL + 3.4f, bot, 1.4f, vest)
                g.rrect(tR - 3.4f, top + 0.6f, tR + 0.2f, bot, 1.4f, vest)
                g.rect(tL + 3f, top + 0.2f, tR - 3f, top + 2f, vest)
                g.rect(tL - 0.2f, top + 0.6f, tL + 0.7f, bot, gear.vestDark)
                g.rect(tR - 0.7f, top + 0.6f, tR + 0.2f, bot, gear.vestDark)
                if (hd) {
                    // 조끼 주머니 2개 + 단추 — 탐조 조끼의 상징
                    val py0 = top + 4.6f
                    g.rrect(tL + 0.2f, py0, tL + 2.6f, py0 + 3.2f, 0.4f, shade(vest, 0.88f))
                    g.rrect(tR - 2.6f, py0, tR - 0.2f, py0 + 3.2f, 0.4f, shade(vest, 0.88f))
                    g.rect(tL + 2.8f, top + 2.3f, tR - 2.8f, top + 2.65f, HD_SHADE)
                    g.circ(tR - 3.6f, top + 3.4f, 0.42f, 0xFFF2D06B.toInt())
                    g.circ(tL + 3.6f, top + 3.4f, 0.42f, 0xFFF2D06B.toInt())
                }
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
            if (hd) {
                // 모자 윤기 + 정면 금색 배지 — 4배에서 장비 등급 차이가 한눈에 보인다
                g.rect(cx - cw + 0.9f, top + 0.6f, cx + cw - 0.9f, top + 1.1f, HD_HILITE)
                if (direction == FRONT) {
                    g.circ(cx + 0.1f, bot - 2.1f, 0.85f, 0xFFF2D06B.toInt())
                    g.circ(cx + 0.1f, bot - 2.1f, 0.42f, 0xFFB8862A.toInt())
                }
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
            if (hd) {
                // 다이얼 눈금 + 렌즈 광 — 카메라가 검은 네모로 보이지 않는다
                g.rect(cx - 2.1f, cy - 1.15f, cx - 0.9f, cy - 0.9f, HD_HILITE)
                g.circ(lensX + 0.35f, cy - 0.45f, g.fine, Color.argb(210, 255, 255, 255))
                g.rect(cx + 1.2f, cy - 1.3f, cx + 2f, cy + 1.1f, Color.argb(46, 255, 250, 236))
            }
        }

        fun drawCane() {
            val hx = hipX + (if (direction != SIDE) 4.6f else 5.2f)
            g.rect(hx, shoulderY + 0.5f, hx + 1.4f, ground - 0.4f, 0xFF8A5A33.toInt())
            g.rect(hx - 1.2f, shoulderY - 0.6f, hx + 2.2f, shoulderY + 1f, 0xFF6B431F.toInt())
        }

        // ---- 치마 — 허리에서 무릎까지 (정면만, 옆모습은 바지) ----------------------
        fun drawSkirt() {
            if (look.bottom != BOTTOM_SKIRT || direction != FRONT) return
            val waistY = hipY - 1.2f
            val hemY = hipY + 5.6f + pose.clothSway * 0.3f
            val cx = (tL + tR) / 2f
            val hwTop = (tR - tL) * 0.32f
            val hwBot = (tR - tL) * 0.52f + 1.2f
            g.poly(
                pal.line,
                cx - hwTop - 0.7f, waistY - 0.5f, cx + hwTop + 0.7f, waistY - 0.5f,
                cx + hwBot + 0.7f, hemY + 0.5f, cx - hwBot - 0.7f, hemY + 0.5f
            )
            g.poly(
                pal.pants,
                cx - hwTop, waistY, cx + hwTop, waistY,
                cx + hwBot, hemY, cx - hwBot, hemY
            )
            g.poly(
                pal.pants2,
                cx + hwTop * 0.4f, waistY + 0.6f, cx + hwTop, waistY + 0.6f,
                cx + hwBot, hemY - 0.4f, cx + hwBot * 0.55f, hemY - 0.4f
            )
            if (hd) {
                g.rect(cx - hwBot + 1f, hemY - 0.9f, cx + hwBot - 1f, hemY - 0.4f, HD_SHADE)
                g.rect(cx - hwTop + 0.4f, waistY + 0.4f, cx + hwTop - 0.4f, waistY + 0.8f, HD_HILITE)
            }
        }

        // 손 위치 — 소품을 손에 쥐여 주려고 팔 FK 를 그대로 다시 계산한다
        fun handPos(side: Float): Pair<Float, Float> {
            val x0 = armRootX(side)
            val y0 = armRootY(side)
            val d = depth * face
            val upper = 3.6f * sc
            val fore = 3.4f * sc
            val ang = if (side > 0) pose.armR else pose.armL
            val elbow = if (side > 0) pose.elbowR else pose.elbowL
            val ex = fkX(x0, ang, upper, d)
            val ey = fkY(y0, ang, upper)
            val foreAng = ang + elbow
            return fkX(ex, foreAng, fore, d) to fkY(ey, foreAng, fore)
        }

        // ---- 몸에 걸치는 소품 — 쌍안경·카메라·노·뜰채 (팔보다 먼저) ---------------
        fun drawWornProps() {
            if (direction != FRONT) return
            val pc = if (look.propColor != 0) look.propColor else pal.pack
            when (look.prop) {
                PROP_BINOCS, PROP_CAMERA -> {
                    // 목걸이 — 끈 + 가슴팍 장비
                    val ny = shoulderY + 0.6f
                    val by = shoulderY + 4.6f + pose.packBob * 0.4f
                    val strap = shade(pc, 0.6f)
                    g.seg(hipX - 3.4f, ny, hipX - 1.6f, by - 1f, 0.5f, 0.5f, strap)
                    g.seg(hipX + 3.4f, ny, hipX + 1.6f, by - 1f, 0.5f, 0.5f, strap)
                    if (look.prop == PROP_BINOCS) {
                        g.rrect(hipX - 2.8f, by - 1.4f, hipX - 0.2f, by + 1.2f, 0.7f, pal.line)
                        g.rrect(hipX + 0.2f, by - 1.4f, hipX + 2.8f, by + 1.2f, 0.7f, pal.line)
                        g.rrect(hipX - 2.4f, by - 1f, hipX - 0.6f, by + 0.8f, 0.6f, 0xFF3A3F49.toInt())
                        g.rrect(hipX + 0.6f, by - 1f, hipX + 2.4f, by + 0.8f, 0.6f, 0xFF3A3F49.toInt())
                        g.circ(hipX - 1.5f, by - 0.1f, 0.55f, 0xFFBFE6FF.toInt())
                        g.circ(hipX + 1.5f, by - 0.1f, 0.55f, 0xFFBFE6FF.toInt())
                    } else {
                        g.rrect(hipX - 2.9f, by - 1.5f, hipX + 2.9f, by + 1.3f, 0.8f, pal.line)
                        g.rrect(hipX - 2.5f, by - 1.1f, hipX + 2.5f, by + 0.9f, 0.6f, 0xFF3A3F49.toInt())
                        g.circ(hipX + 0.6f, by - 0.1f, 1.5f, pal.line)
                        g.circ(hipX + 0.6f, by - 0.1f, 1.05f, 0xFF2B3038.toInt())
                        g.circ(hipX + 0.9f, by - 0.4f, 0.45f, 0xFFBFE6FF.toInt())
                    }
                }
                PROP_PADDLE -> {
                    // 어깨에 멘 노 — 오른쪽 위로 길게
                    val bx = shoulderX + 4.6f
                    val by = shoulderY + 1f
                    val tx = bx + 5.5f + pose.clothSway * 0.7f
                    val ty = by - 13.5f
                    g.seg(bx - 1f, by + 3f, tx, ty + 3f, 0.8f, 0.7f, 0xFF8A5A33.toInt())
                    g.rrect(tx - 1.7f, ty - 1.2f, tx + 1.7f, ty + 3.4f, 1.2f, pal.line)
                    g.rrect(tx - 1.2f, ty - 0.7f, tx + 1.2f, ty + 2.9f, 1f, pc)
                }
                PROP_NET -> {
                    // 어깨에 멘 뜰채 — 왼쪽에 둥근 망
                    val nx = shoulderX - 7.6f
                    val ny = shoulderY + 2.4f
                    g.seg(shoulderX - 3f, shoulderY, nx + 1.6f, ny - 1.6f, 0.8f, 0.7f, 0xFF8A5A33.toInt())
                    g.circ(nx, ny, 3.4f, pal.line)
                    g.circ(nx, ny, 2.8f, 0xFFDCE6EC.toInt())
                    g.rect(nx - 2.6f, ny - 0.3f, nx + 2.6f, ny + 0.3f, 0xFF9AA3AD.toInt())
                    g.rect(nx - 0.3f, ny - 2.6f, nx + 0.3f, ny + 2.6f, 0xFF9AA3AD.toInt())
                    g.circ(nx, ny, 1.2f, 0xFFDCE6EC.toInt())
                }
            }
        }

        // ---- 손에 든 소품 — 낚싯대·바구니·책·붓·찻잔 (팔보다 나중에) ---------------
        fun drawHeldProps() {
            if (direction != FRONT) return
            val pc = if (look.propColor != 0) look.propColor else pal.pack
            when (look.prop) {
                PROP_ROD -> {
                    val (hx, hy) = handPos(1f)
                    // 오른손에 쥔 낚싯대 — 위로 길게 + 낚싯줄
                    val tipX = hx + 6.5f + pose.clothSway * 0.8f
                    val tipY = hy - 15f
                    g.seg(hx, hy + 1.5f, tipX, tipY, 0.7f, 0.35f, 0xFF8A5A33.toInt())
                    g.seg(tipX, tipY, tipX + 1.2f, tipY + 4.5f, 0.25f, 0.2f, 0xFFDCE6EC.toInt())
                    g.circ(hx + 0.9f, hy - 1.2f, 0.9f, 0xFF9AA0AD.toInt())
                }
                PROP_BASKET -> {
                    val (hx, hy) = handPos(-1f)
                    // 왼손에 든 바구니 — 손 아래로 매달린다
                    val bw = 3.4f
                    val top = hy + 0.6f
                    g.poly(
                        0xFF8A5A33.toInt(),
                        hx - bw - 0.4f, top, hx + bw + 0.4f, top,
                        hx + bw - 0.6f, top + 4.6f, hx - bw + 0.6f, top + 4.6f
                    )
                    g.poly(
                        pc,
                        hx - bw, top + 0.4f, hx + bw, top + 0.4f,
                        hx + bw - 0.8f, top + 4.2f, hx - bw + 0.8f, top + 4.2f
                    )
                    g.rect(hx - bw + 0.4f, top + 1.8f, hx + bw - 0.4f, top + 2.4f, 0xFFB08840.toInt())
                    g.seg(hx - bw, top + 0.4f, hx, top - 2.2f, 0.5f, 0.5f, 0xFF8A5A33.toInt())
                    g.seg(hx + bw, top + 0.4f, hx, top - 2.2f, 0.5f, 0.5f, 0xFF8A5A33.toInt())
                }
                PROP_BOOK -> {
                    val (lx, ly) = handPos(-1f)
                    val (rx, ry) = handPos(1f)
                    val bx = (lx + rx) / 2f
                    val by = (ly + ry) / 2f - 0.6f
                    // 두 손에 펼쳐 든 책
                    g.rrect(bx - 3.4f, by - 1.8f, bx + 3.4f, by + 1.8f, 0.7f, pal.line)
                    g.rrect(bx - 3f, by - 1.4f, bx - 0.2f, by + 1.4f, 0.5f, 0xFFFDF6E8.toInt())
                    g.rrect(bx + 0.2f, by - 1.4f, bx + 3f, by + 1.4f, 0.5f, 0xFFFDF6E8.toInt())
                    g.rect(bx - 2.4f, by - 0.6f, bx - 0.8f, by, shade(pc, 1f))
                    g.rect(bx + 0.8f, by - 0.6f, bx + 2.4f, by, shade(pc, 1f))
                }
                PROP_BRUSH -> {
                    val (lx, ly) = handPos(-1f)
                    val (rx, ry) = handPos(1f)
                    // 왼손 팔레트 + 오른손 붓
                    g.circ(lx, ly, 2.4f, pal.line)
                    g.circ(lx, ly, 1.9f, 0xFFF3EDE2.toInt())
                    g.circ(lx - 0.7f, ly - 0.5f, 0.55f, 0xFFE2574C.toInt())
                    g.circ(lx + 0.7f, ly - 0.4f, 0.55f, 0xFF3F6FA0.toInt())
                    g.circ(lx, ly + 0.7f, 0.55f, 0xFFF2B63C.toInt())
                    g.seg(rx, ry, rx + 1.8f, ry - 4.2f, 0.55f, 0.4f, 0xFFC9A05C.toInt())
                    g.circ(rx + 1.9f, ry - 4.5f, 0.8f, 0xFFE2574C.toInt())
                }
                PROP_CUP -> {
                    val (hx, hy) = handPos(1f)
                    // 오른손 찻잔 + 모락모락 김
                    g.rrect(hx - 1.4f, hy - 1.6f, hx + 1.4f, hy + 0.8f, 0.5f, pal.line)
                    g.rrect(hx - 1f, hy - 1.2f, hx + 1f, hy + 0.4f, 0.4f, 0xFFFDF6E8.toInt())
                    g.rect(hx - 1f, hy - 1.2f, hx + 1f, hy - 0.6f, 0xFF8A5A33.toInt())
                    val sw = pose.breath * 0.5f
                    g.seg(hx - 0.3f + sw, hy - 1.8f, hx + 0.2f - sw, hy - 3.4f, 0.3f, 0.2f, 0xAAFFFFFF.toInt())
                }
            }
        }

        // ---- NPC 모자 — 탐조가 장비 모자와 별개 실루엣 8종 --------------------------
        fun drawHat() {
            if (look.hat == HAT_NONE || direction == BACK) return
            val cx = headCx
            val cy = headCy
            val hc = if (look.hatColor != 0) look.hatColor else pal.top2
            val hd2 = shade(hc, 0.72f)
            val tilt = pose.tilt
            val cw = headR + 0.6f
            when (look.hat) {
                HAT_CAP -> {
                    val top = cy - headR - 2f
                    val bot = cy - headR * 0.18f
                    g.rrect(cx - cw - 0.5f + tilt * 0.12f, top - 0.5f, cx + cw + 0.5f + tilt * 0.12f, bot + 0.4f, 3.4f, pal.line)
                    g.rrect(cx - cw + tilt * 0.12f, top, cx + cw + tilt * 0.12f, bot, 3f, hc)
                    g.rect(cx - cw + 0.7f + tilt * 0.12f, top + 0.5f, cx + cw - 0.7f + tilt * 0.12f, top + 2.4f, hd2)
                    if (direction == SIDE) {
                        g.rect(cx + 2.2f, bot - 1.5f, cx + cw + 2.6f, bot - 0.1f, pal.line)
                        g.rect(cx + 2.2f, bot - 1.4f, cx + cw + 2.3f, bot - 0.4f, hd2)
                    } else {
                        g.rect(cx - cw + 0.5f, bot - 1.1f, cx + cw - 0.5f, bot + 0.6f, pal.line)
                        g.rect(cx - cw + 0.9f, bot - 1f, cx + cw - 0.9f, bot + 0.3f, hd2)
                    }
                }
                HAT_BUCKET -> {
                    // 버킷햇 — 둥근 통 + 중간 챙
                    val top = cy - headR - 2.6f
                    val bot = cy - headR * 0.30f
                    g.rrect(cx - cw + 0.6f, top, cx + cw - 0.6f, bot, 2.6f, pal.line)
                    g.rrect(cx - cw + 1.2f, top + 0.6f, cx + cw - 1.2f, bot, 2.2f, hc)
                    g.rect(cx - cw + 1.2f, bot - 2.6f, cx + cw - 1.2f, bot - 1.6f, hd2)
                    if (direction == SIDE) {
                        g.rrect(cx - cw - 1.2f, bot - 1.2f, cx + cw + 1.6f, bot + 0.7f, 0.9f, pal.line)
                        g.rrect(cx - cw - 0.8f, bot - 1.1f, cx + cw + 1.2f, bot + 0.4f, 0.8f, hc)
                    } else {
                        g.rrect(cx - cw - 2.2f, bot - 1.2f, cx + cw + 2.2f, bot + 0.7f, 1f, pal.line)
                        g.rrect(cx - cw - 1.8f, bot - 1.1f, cx + cw + 1.8f, bot + 0.4f, 0.9f, hc)
                    }
                }
                HAT_STRAW -> {
                    // 밀짚모자 — 납작한 넓은 챙 + 낮은 머리 + 리본 끈
                    val bot = cy - headR * 0.35f
                    g.rrect(cx - cw + 1.4f, bot - 4.4f, cx + cw - 1.4f, bot - 0.6f, 2f, pal.line)
                    g.rrect(cx - cw + 2f, bot - 3.8f, cx + cw - 2f, bot - 0.6f, 1.8f, hc)
                    g.rect(cx - cw + 2f, bot - 2f, cx + cw - 2f, bot - 1.2f, pal.blush)
                    if (direction == SIDE) {
                        g.rrect(cx - cw - 2.6f, bot - 1f, cx + cw + 2.8f, bot + 0.8f, 0.8f, pal.line)
                        g.rrect(cx - cw - 2.2f, bot - 0.9f, cx + cw + 2.4f, bot + 0.5f, 0.7f, hc)
                    } else {
                        g.rrect(cx - cw - 3.4f, bot - 1f, cx + cw + 3.4f, bot + 0.8f, 0.9f, pal.line)
                        g.rrect(cx - cw - 3f, bot - 0.9f, cx + cw + 3f, bot + 0.5f, 0.8f, hc)
                    }
                }
                HAT_BEANIE -> {
                    // 털모자 — 접은 단 + 방울
                    val bot = cy - headR * 0.25f
                    g.rrect(cx - cw + 0.2f, bot - 6.4f, cx + cw - 0.2f, bot - 0.4f, 3f, pal.line)
                    g.rrect(cx - cw + 0.8f, bot - 5.8f, cx + cw - 0.8f, bot - 0.4f, 2.6f, hc)
                    g.rect(cx - cw + 0.8f, bot - 2.6f, cx + cw - 0.8f, bot - 0.4f, hd2)
                    g.circ(cx + pose.hairSway * 0.4f, bot - 7f, 1.6f, pal.line)
                    g.circ(cx + pose.hairSway * 0.4f, bot - 7f, 1.1f, 0xFFFDF6E8.toInt())
                    if (direction == FRONT) {
                        g.rect(cx - 2f, bot - 5.2f, cx - 1.2f, bot - 2.8f, hd2)
                        g.rect(cx + 1.2f, bot - 5.2f, cx + 2f, bot - 2.8f, hd2)
                    }
                }
                HAT_BANDANA -> {
                    // 반다나 — 삼각 두건 + 옆 매듭 + 땡땡이
                    g.poly(
                        hc,
                        cx - cw + 0.4f, cy - headR * 0.2f,
                        cx, cy - headR - 3.4f,
                        cx + cw - 0.4f, cy - headR * 0.2f,
                        cx, cy - headR * 0.5f
                    )
                    g.rrect(cx - cw + 0.2f, cy - headR * 0.45f, cx + cw - 0.2f, cy - headR * 0.2f + 1.2f, 0.8f, hd2)
                    g.circ(cx + cw - 0.2f, cy - headR * 0.3f + 0.6f, 1.2f, hd2)
                    g.seg(cx + cw - 0.2f, cy - headR * 0.3f + 1.2f, cx + cw + 1.2f + pose.hairSway * 0.5f, cy - headR * 0.3f + 3f, 0.8f, 0.5f, hd2)
                    if (direction == FRONT) {
                        g.circ(cx - 1.6f, cy - headR - 0.6f, 0.7f, 0xFFFDF6E8.toInt())
                        g.circ(cx + 1.4f, cy - headR - 1.4f, 0.7f, 0xFFFDF6E8.toInt())
                    }
                }
                HAT_HEADSCARF -> {
                    // 수건 — 얼굴만 내놓고 머리를 감싼다 (헤어는 그리지 않음)
                    g.oval(cx - headR - 0.9f, cy - headR - 1.1f, cx + headR + 0.9f, cy - 0.6f, pal.line)
                    g.oval(cx - headR - 0.3f, cy - headR - 0.5f, cx + headR + 0.3f, cy - 1.1f, hc)
                    if (direction == SIDE) {
                        g.rrect(cx - headR - 1.2f, cy - 1f, cx - headR + 1.8f, cy + 6.4f, 1.4f, hc)
                        g.rrect(cx - headR - 1.2f, cy - 1f, cx - headR + 0.2f, cy + 6.4f, 1.2f, hd2)
                    } else {
                        g.rrect(cx - headR - 1.1f, cy - 1f, cx - headR + 1.6f, cy + 6.2f, 1.3f, hc)
                        g.rrect(cx + headR - 1.6f, cy - 1f, cx + headR + 1.1f, cy + 6.2f, 1.3f, hc)
                        g.circ(cx, cy + headR + 0.2f, 1.5f, hd2)
                    }
                }
                HAT_FISHER -> {
                    // 방수 어부 모자 — 앞챙 짧고 뒷목 가리개 김
                    val bot = cy - headR * 0.30f
                    g.rrect(cx - cw + 0.8f, bot - 5f, cx + cw - 0.8f, bot, 2.4f, pal.line)
                    g.rrect(cx - cw + 1.4f, bot - 4.4f, cx + cw - 1.4f, bot, 2f, hc)
                    if (direction == SIDE) {
                        g.rrect(cx - cw - 2.4f, bot - 1.2f, cx + cw + 1.8f, bot + 0.8f, 1f, pal.line)
                        g.rrect(cx - cw - 2f, bot - 1.1f, cx + cw + 1.4f, bot + 0.5f, 0.9f, hc)
                        g.rect(cx - cw - 2f, bot + 0.5f, cx - cw + 0.6f, bot + 3.4f, hc)
                    } else {
                        g.rrect(cx - cw - 2.4f, bot - 1.2f, cx + cw + 2.4f, bot + 0.8f, 1f, pal.line)
                        g.rrect(cx - cw - 2f, bot - 1.1f, cx + cw + 2f, bot + 0.5f, 0.9f, hc)
                        g.rect(cx - cw - 2f, bot - 0.1f, cx + cw + 2f, bot + 0.5f, hd2)
                    }
                }
                HAT_VISOR -> {
                    // 썬바이저 — 띠 + 앞챙, 정수리는 머리 그대로
                    val bot = cy - headR * 0.35f
                    g.rrect(cx - cw + 0.2f, bot - 1.8f, cx + cw - 0.2f, bot + 0.2f, 0.9f, pal.line)
                    g.rrect(cx - cw + 0.6f, bot - 1.4f, cx + cw - 0.6f, bot - 0.2f, 0.7f, hc)
                    if (direction == SIDE) {
                        g.rect(cx + 1.6f, bot - 1f, cx + cw + 2.8f, bot + 0.6f, pal.line)
                        g.rect(cx + 1.6f, bot - 0.9f, cx + cw + 2.5f, bot + 0.2f, hc)
                    } else {
                        g.rrect(cx - cw - 0.6f, bot - 0.4f, cx + cw + 0.6f, bot + 1.6f, 0.8f, pal.line)
                        g.rrect(cx - cw - 0.2f, bot - 0.3f, cx + cw + 0.2f, bot + 1.3f, 0.7f, hc)
                    }
                }
            }
            if (hd && look.hat != HAT_NONE) {
                // 모자 윤기 한 줄
                g.rect(cx - cw + 1.2f, cy - headR - 0.4f, cx + cw - 1.2f, cy - headR + 0.1f, HD_HILITE)
            }
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
            drawHat()
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
            drawSkirt()
            drawWornProps()
            drawArm(-1f, pose.armL, pose.elbowL, !backIsRight)
            drawArm(1f, pose.armR, pose.elbowR, backIsRight)
            drawHeldProps()
            drawScarf()
            drawHead()
            drawCap()
            drawHat()
        }

        // High-contrast keepsakes remain legible even at the base sprite size.
        look.keepsake?.let { id ->
            Charms.of(id)?.let { item ->
                Charms.draw(g.cv, item,
                    shoulderX + if (direction == SIDE) 3.8f else -4.2f,
                    shoulderY + 3.5f + pose.clothSway * 0.08f, 5.5f)
            }
        }
        if (pose.hold == HOLD_CAMERA) drawCamera()
        if (look.cane) drawCane()
        return refine(bmp, size, hd)
    }

    // -----------------------------------------------------------------------
    // 자전거 (페달을 밟는 라이더)
    // -----------------------------------------------------------------------

    private val RIM = 0xFF23232B.toInt()
    private val METAL = 0xFF9AA0AD.toInt()

    /** direction: SIDE(오른쪽)/FRONT/BACK, phase: 0~1 페달 한 바퀴, style: 모델·도색·부속품 */
    fun renderBike(direction: Int, phase: Float, look: Look, style: BikeStyle): Bitmap =
        renderBike(direction, phase, look, style, SIZE)

    /**
     * 자전거 + 라이더를 그린다.
     *
     * @param size 출력 비트맵 한 변(px) — [render] 와 같은 규칙.
     *   [SIZE]의 정수배면 바퀴 살·프레임 도색 선이 그만큼 촘촘하게 래스터화되고,
     *   [HD_DETAIL_SCALE] 배 이상에서 타이어 광·스포크·로고 같은 디테일이 더해진다.
     */
    fun renderBike(direction: Int, phase: Float, look: Look, style: BikeStyle, size: Int): Bitmap {
        val k = size.toFloat() / SIZE
        val hd = k >= HD_DETAIL_SCALE - 0.001f
        // 도색: 프레임 / 바퀴 / 안장·그립
        val BIKE_COL = style.frame.argb
        val BIKE_DARK = shade(BIKE_COL, 0.68f)
        val TIRE = style.tire.argb
        val TIRE_IN = shade(TIRE, 1.55f)
        val LEATHER = style.saddle.argb
        val kind = style.modelId
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val g = G(Canvas(bmp), k)
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
            if (hd) {
                // 안쪽 림 광 + 스포크 4가닥 — 바퀴가 검은 도넛으로 뭉개지지 않게
                ring(cx, cy, r - ti * 0.55f, 0.3f, HD_METAL, 16)
                for (i in 0 until 4) {
                    val a = spin + i * (PI.toFloat() / 4f)
                    g.seg(
                        cx - cos(a) * (r - 2.2f), cy - sin(a) * (r - 2.2f),
                        cx + cos(a) * (r - 2.2f), cy + sin(a) * (r - 2.2f), 0.18f, 0.18f, METAL
                    )
                }
            }
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
                if (hd) {
                    // 프레임 윤기 + 크랭크 하우스 — 도색이 살아 있는 금속처럼 보이게
                    g.seg(rear + 1.2f, wy - 0.4f, sadX - 0.6f, sadY + 1.2f, 0.24f, 0.22f, HD_HILITE)
                    g.seg(sadX + 1.2f, sadY + 1.4f, barX - 1f, barY + 1.4f, 0.22f, 0.2f, HD_HILITE)
                    g.circ(crankX, crankY, 1.25f, RIM)
                    g.circ(crankX, crankY, 0.6f, METAL)
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
            if (hd) {
                // 안장 가죽 광 + 바구니 엮음 + 등받이 끈 — 부속품이 도드라진다
                g.rect(sadX - sadW + 0.4f, sadY - 0.1f, sadX + 1.6f, sadY + 0.2f, HD_HILITE)
                if (style.basket) {
                    g.rect(barX + 1.6f, barY + 2.15f, barX + 6.2f, barY + 2.5f, 0xFFB08840.toInt())
                    g.rect(barX + 1.6f, barY + 5.35f, barX + 6.2f, barY + 5.7f, 0xFFB08840.toInt())
                    for (i in 0 until 4) {
                        val bx = barX + 2.2f + i * 1.2f
                        g.rect(bx, barY + 1.6f, bx + 0.22f, barY + 5.6f, HD_SHADE)
                    }
                }
                g.rect(barX - 2.4f, barY - 0.3f, barX + 1.4f, barY - 0.05f, HD_HILITE)
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
        if (hd) {
            // 정면/뒷면 디테일 — 앞바구니 엮음 · 핸들 그립 광 · 벨 · 헬멧끈 · 프레임 윤기
            if (style.basket && direction == FRONT) {
                g.rect(12.5f, 20.2f, 19.5f, 20.55f, 0xFFB08840.toInt())
                for (i in 0 until 6) {
                    val bx = 12.2f + i * 1.4f
                    g.rect(bx, 20.1f, bx + 0.2f, 23.2f, HD_SHADE)
                }
            }
            g.rect(16f - hw - 1.1f, 17.9f + bob - tilt, 16f - hw + 1.1f, 18.2f + bob - tilt, HD_HILITE)
            g.rect(16f + hw - 1.1f, 17.9f + bob + tilt, 16f + hw + 1.1f, 18.2f + bob + tilt, HD_HILITE)
            if (style.bell) g.circ(16f + hw - 1.9f, 16.9f + bob + tilt, g.fine, HD_SPARK)
            if (direction == FRONT) {
                // 헬멧 대신 쓴 탐조 모자 끈 + 볼 광
                g.rect(hx - 4.6f, hy + 3.6f, hx - 3.4f, hy + 5.2f, shade(pal.hair2, 0.9f))
                g.rect(hx + 3.4f, hy + 3.6f, hx + 4.6f, hy + 5.2f, shade(pal.hair2, 0.9f))
                g.circ(hx - 1.1f, hy + 1.5f, g.fine, HD_SPARK)
                g.circ(hx + 1.1f, hy + 1.5f, g.fine, HD_SPARK)
                g.rect(hx - 4.3f, hy - 1.6f, hx + 4.3f, hy - 1.15f, HD_HILITE)
            } else {
                g.oval(hx - 3.4f, hy - 4.4f, hx + 3.4f, hy - 1.4f, HD_HILITE)
                g.rect(cx - 4.4f, top + 1.6f, cx + 4.4f, top + 2f, shade(pal.pack2, 0.85f))
                g.rect(cx - 1f, top + 3.2f, cx + 1f, top + 4.6f, 0xFFD9A03C.toInt())
            }
            g.rect(cx - 4.9f, top + 1.4f, cx + 4.9f, top + 1.75f, HD_HILITE)
            g.rect(cx - 5.1f, hipYb + 0.35f, cx + 5.1f, hipYb + 0.75f, HD_SHADE)
        }
        return refine(bmp, size, hd)
    }

    /**
     * 원본 픽셀에 또렷한 대비를 한 번 더 준다 (HD 렌더 전용 후처리).
     *
     * 벡터 도형을 4배로 래스터화하면 경계가 **부드러운 회색**으로 번지는데,
     * 그대로 화면에 크게 띄우면 "흐릿하게 확대한 도트"처럼 보인다. 그래서
     *  1) 밝은 쪽은 더 밝게, 어두운 쪽은 더 어둡게(부드러운 S자 곡선),
     *  2) 세로 1px·가로 1px만 섞는 아주 약한 샤픈
     * 을 걸어 윤곽을 세운다. 32px 도트(size = SIZE)는 손대지 않으므로
     * 예전과 픽셀 단위로 같은 결과가 유지된다.
     */
    private fun refine(src: Bitmap, size: Int, hd: Boolean): Bitmap {
        if (!hd || size <= SIZE) return src
        return try {
            val w = src.width
            val h = src.height
            val n = w * h
            val px = IntArray(n)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val out = IntArray(n)
            // 256단계 대비 곡선 — 미리 계산해 두고 화소마다 조회만 한다
            val curve = IntArray(256)
            for (i in 0 until 256) {
                val t = i / 255f
                val s = (t + 0.30f * sin(TAU * (t - 0.5f))).coerceIn(0f, 1f)
                curve[i] = (s * 255f + 0.5f).toInt()
            }
            fun at(x: Int, y: Int): Int = px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]

            /** 채널 하나: 언샤프 마스크(경계 강조) → 대비 곡선 */
            fun chan(raw: Int, neighSum: Int): Int {
                val delta = (raw * 4 - neighSum) * 22 / 400      // (자기값 - 이웃평균) × 0.22
                return curve[(raw + delta).coerceIn(0, 255)]
            }

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = px[y * w + x]
                    val a = (c ushr 24) and 0xFF
                    if (a == 0) continue                            // 투명 픽셀은 그대로
                    val cN = at(x, y - 1); val cS = at(x, y + 1)
                    val cW = at(x - 1, y); val cE = at(x + 1, y)
                    val r = chan((c ushr 16) and 0xFF, ((cN ushr 16) and 0xFF) + ((cS ushr 16) and 0xFF) + ((cW ushr 16) and 0xFF) + ((cE ushr 16) and 0xFF))
                    val g = chan((c ushr 8) and 0xFF, ((cN ushr 8) and 0xFF) + ((cS ushr 8) and 0xFF) + ((cW ushr 8) and 0xFF) + ((cE ushr 8) and 0xFF))
                    val b = chan(c and 0xFF, (cN and 0xFF) + (cS and 0xFF) + (cW and 0xFF) + (cE and 0xFF))
                    out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            dst.setPixels(out, 0, w, 0, 0, w, h)
            dst
        } catch (_: Exception) {
            src     // 후처리 실패는 치명적이지 않다 — 원본을 그대로 쓴다
        }
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
