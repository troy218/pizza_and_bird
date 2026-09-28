package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 살아있는 풀 (Living Grass) — 듀오 애니메이션의 "내부 구조"를 픽셀 아트에 그대로 적용.
 *
 * 듀오(Rive/Lottie) 캐릭터가 "캐릭터 하나 = 뼈대 리그 + 작성된 상태들(IDLE/SWAY/...),
 * 상태 사이를 스프링으로 트윈"하는 구조와 동일하게 만든다.
 *
 * ```
 *  INPUT   바람 필드 wind(x, t)          — 돌풍이 실제로 +x 방향으로 이동한다
 *          ↓                              (풀잎마다 phase가 달라 전부 같이 움직이지 않음)
 *  RIG     풀잎 = ROOT → MID → TIP       — 정수 픽셀 오프셋 누적(휠러여도 픽셀 유지)
 *          ↓
 *  STATE   IDLE / SWAY / GUST / BEND / RECOVER   (상태마다 강성 k · 감쇠 d 다름)
 *          ↓                              RECOVER는 감쇠가 낮아 오버슛으로 흔들리며 일어섬
 *  POSE    (lean, curl) → Assets.grassPose 사전 조회 — 사전제작 프레임 1장 drawBitmap
 *          ↓
 *  DEPTH   밑동이 캐릭터 발보다 아래면 전경 레이어로 그려져서 진짜 밭을 헤치며 걷는다
 * ```
 *
 * [update]와 [draw]는 카메라 주변 풀잎만 처리한다. 화면 밖에서는 적분을 쉬고
 * 다시 들어올 때 그 시각의 바람 자세로 이어 준다.
 */
class GrassField(map: GameMap) {

    // -----------------------------------------------------------------------
    // 상태 머신
    // -----------------------------------------------------------------------

    companion object {
        const val S_IDLE = 0      // 잔잔함 — 아주 작은 호흡
        const val S_SWAY = 1      // 보통 바람
        const val S_GUST = 2      // 돌풍이 지나감 — 크게 눕고 오버슛
        const val S_BEND = 3      // 발/바퀴에 밟힘 — 이동 방향으로 눌림
        const val S_RECOVER = 4   // 일어서는 중 — 감쇠가 낮아 몇 번 흔들리며 복귀

        /** 상태별 (스프링 강성 k, 감쇠 d) — Lottie 키프레임 트윈과 같은 역할 */
        private val K = floatArrayOf(15f, 27f, 42f, 150f, 28f)
        private val D = floatArrayOf(6.4f, 5.6f, 4.1f, 9.0f, 3.2f)

        const val LAYER_BACK = 0
        const val LAYER_FRONT = 1

        private const val SWAY_TH = 0.22f       // SWAY 진입 바람 세기
        private const val GUST_TH = 0.56f       // GUST 진입 바람 세기
        private const val LEAN_AMP = 5.2f       // 바람 → 기짐 변환 배율
        private const val CURL_AMP = 1.6f
        private const val RECOVER_T = 0.6f      // 밟힘 해제 후 RECOVER 유지 시간

        private const val SQUALL_PERIOD = 7.2f // 돌풍 주기(초)
        private const val SQUALL_R = 210f       // 돌풍 영향 반경(월드 px)
        private const val SQUALL_AMP = 0.92f

        private const val COL_W = 8f           // 바람 필드를 8px 열 단위로 한 번만 계산
        private const val PHASE_LAG = 0.55f    // 풀잎별 진동 위상차 (돌풍에는 적용하지 않음)
        // 카메라 이동·줌·흔들림에도 화면 가장자리 풀잎이 미리 살아 있도록 하는 여유.
        private const val UPDATE_PAD = 80f

        private const val NO_GRASS = -2        // 이 지형에는 풀을 심지 않는다는 표식
    }

    private class Blade(
        val x: Float,          // 밑동(뿌리) 월드 좌표 — 32px 타일 좌표계
        val y: Float,
        val kind: Int,
        val phase01: Float,    // 0..1 → 바람 필드의 위상 (풀잎마다 다름)
        val stiff: Float,      // 개체별 강성 0.78~1.28
        var lean: Float = 0f, var leanV: Float = 0f,
        var curl: Float = 0f, var curlV: Float = 0f,
        /** 현재 상태 (IDLE~RECOVER). 상태 머신이 디버깅/튜닝에서 쓰는 현재 노드. */
        var state: Int = S_IDLE,
        /** RECOVER 잔여 시간 — 0이면 회복 완료. "방금 밟혔는가"를 기억하는 타이머. */
        var rec: Float = 0f,
        var lastUpdate: Float = 0f
    )

    private val blades = ArrayList<Blade>()
    private val grassPaint = Paint().apply {
        isFilterBitmap = false
        colorFilter = PorterDuffColorFilter(map.mapStyle.foliageFilter, PorterDuff.Mode.MULTIPLY)
    }
    private val windOscA: FloatArray    // 진동 성분 @ t
    private val windOscB: FloatArray    // 진동 성분 @ t + PHASE_LAG (풀잎별 위상용)
    private val windSquall: FloatArray  // 돌풍 성분 — 전선이 또렷해야 하므로 위상 분리를 하지 않는다
    private val span: Float

    init {
        val r = Random(map.region.id.hashCode().toLong() * 31L + 0x9E3779B9L)
        build(map, r)
        blades.sortBy { it.y }   // y 순 → 자연스러운 앞뒤 정렬
        span = map.w * 32f
        val cols = (span / COL_W).toInt() + 6
        windOscA = FloatArray(cols)
        windOscB = FloatArray(cols)
        windSquall = FloatArray(cols)
    }

    /**
     * 타일 하나에 심을 풀 계획 = (다발 수, 강제 풀잎 종류)
     * 강제 종류가 -1 이면 종류를 자유롭게 섞고, NO_GRASS 면 심지 않는다.
     *
     * 「풀이 나면 안 되는 자리」를 타일보다 먼저 걸러 낸다 —
     *  · **석재 포장**(광장·도심 대로·돌다리·랜드마크 문턱) 위에는 절대 심지 않는다.
     *  · **흙길에는 시골에서만**, 아주 드물게(7%) 갓길 잡초를 허용한다.
     *    도심 샛길까지 잡초가 나면 관리가 안 된 인상이라 도시에서는 심지 않는다.
     *  · **물**(강·호수·바다) 위에는 절대 심지 않는다.
     *    (둑길·다리 아래로 지면이 모래로 바뀐 칸은 물이 아니지만, 포장이 먼저 걸러 준다)
     */
    private fun planFor(map: GameMap, tx: Int, ty: Int, tile: T, r: Random): Pair<Int, Int> {
        when (map.paveAt(tx, ty)) {
            Pave.STONE -> return 0 to NO_GRASS                    // 돌바닥 틈풀 금지
            Pave.DIRT -> return if (!map.region.city && r.nextFloat() < 0.07f) 1 to 0 else 0 to NO_GRASS
        }
        if (map.groundAt(tx, ty) == T.WATER) return 0 to NO_GRASS // 물 위엔 풀이 없다
        return when (tile) {
            T.GRASS -> (if (r.nextFloat() < 0.35f) 2 else 1) to -1
            T.FLOWER -> 1 to -1                        // 꽃이 보이도록 성기게
            T.TALLGRASS -> (2 + r.nextInt(2)) to 2
            T.REED -> 2 to 4
            T.SAND -> if (r.nextFloat() > 0.22f) (0 to NO_GRASS) else (1 to 3)   // 모래밭엔 드물게
            else -> 0 to NO_GRASS
        }
    }

    /** 타일 종류마다 풀을 다르게 심는다 (결정적 — 같은 지역은 항상 같은 밭) */
    private fun build(map: GameMap, r: Random) {
        for (ty in 0 until map.h) {
            for (tx in 0 until map.w) {
                val tile = map.t(tx, ty)
                val plan = planFor(map, tx, ty, tile, r)
                if (plan.second == NO_GRASS) continue
                val forced = plan.second

                repeat(plan.first) {
                    // 55%는 타일 경계에 몰아 심어 타일 이음새에 자연스러운 덩어리를 만든다
                    val cx = if (r.nextFloat() < 0.55f) {
                        if (r.nextBoolean()) tx * 32f + r.nextInt(4).toFloat()
                        else (tx + 1) * 32f - 1f - r.nextInt(4).toFloat()
                    } else {
                        tx * 32f + 1f + r.nextInt(30).toFloat()
                    }
                    val cy = ty * 32f + 20f + r.nextInt(11).toFloat()   // 타일 아래쪽에 뿌리를 둔다
                    val n = 1 + r.nextInt(2)
                    repeat(n) {
                        val kind = if (forced >= 0 && r.nextFloat() < 0.74f) forced
                        else pickKind(tile, r)
                        blades.add(
                            Blade(
                                cx + r.nextInt(5) - 2f,
                                cy + r.nextInt(4) - 1f,
                                kind,
                                r.nextFloat(),
                                0.78f + r.nextFloat() * 0.50f
                            )
                        )
                    }
                }
            }
        }
    }

    private fun pickKind(tile: T, r: Random): Int = when (tile) {
        T.TALLGRASS -> if (r.nextFloat() < 0.65f) 2 else 1
        T.REED -> if (r.nextFloat() < 0.60f) 4 else 2
        T.FLOWER -> if (r.nextFloat() < 0.45f) 0 else 1
        T.SAND -> 3
        else -> if (r.nextFloat() < 0.42f) 0 else (if (r.nextFloat() < 0.78f) 1 else 2)
    }

    /** 밑동 y 순으로 정렬된 목록에서 [y] 이상 / 초과인 첫 풀잎. */
    private fun firstAtOrAfter(y: Float): Int {
        var lo = 0; var hi = blades.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (blades[mid].y < y) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun firstAfter(y: Float): Int {
        var lo = 0; var hi = blades.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (blades[mid].y <= y) lo = mid + 1 else hi = mid
        }
        return lo
    }

    // -----------------------------------------------------------------------
    // 바람 필드
    // -----------------------------------------------------------------------

    /**
     * 진동 성분 — 저주파 호흡 + 2겹 진행파(바람이 전파되는 모습).
     * 풀잎마다 다른 시점 값을 보간하므로, 전부 같은 타이밍에 출렁이지 않는다.
     */
    private fun oscAt(x: Float, t: Float): Float {
        val breathe = sin(t * 0.58f) * 0.15f
        val ripple = sin(t * 1.28f - x * 0.021f) * 0.33f
        val ripple2 = sin(t * 2.85f - x * 0.048f + 1.3f) * 0.12f
        val w = 0.10f + breathe + ripple + ripple2
        return if (w > 1.2f) 1.2f else if (w < -1.0f) -1.0f else w
    }

    /**
     * 돌풍 — 실제 바람의 돌풍처럼 *하나의 전선*으로 화면을 가로질러 지나간다.
     * (이것을 풀잎별 위상으로 흩뿌리면 전선이 흐려져 물결이 안 보인다)
     */
    private fun squallAt(x: Float, t: Float): Float {
        val p = t % SQUALL_PERIOD
        val head = (p / SQUALL_PERIOD) * (span + SQUALL_R * 2.2f) - SQUALL_R * 1.1f
        val d = abs(x - head)
        if (d >= SQUALL_R) return 0f
        val k = 1f - d / SQUALL_R
        return k * k * SQUALL_AMP
    }

    // -----------------------------------------------------------------------
    // 갱신 — 상태 머신
    // -----------------------------------------------------------------------

    /**
     * 플레이어와 현재 카메라 주변의 풀만 적분한다 (단위: 32px 타일 월드 좌표).
     * 먼 풀잎은 그려지지도 밟히지도 않으므로 업데이트할 이유가 없다.
     */
    fun update(dt: Float, time: Float, fx: Float, fy: Float, bike: Boolean,
               camX: Float, camY: Float, viewW: Float, viewH: Float) {
        if (dt <= 0f) return
        val x0 = camX - UPDATE_PAD
        val x1 = camX + viewW + UPDATE_PAD
        val y0 = camY - UPDATE_PAD
        val y1 = camY + viewH + UPDATE_PAD
        val last = windSquall.size - 1

        // 바람 필드도 화면 근처 열만 계산. 카메라가 새 위치로 이동했을 때는
        // 아래에서 풀잎을 현 시각의 바람 자세로 옮겨 멈춰 있는 풀이 나타나지 않는다.
        val col0 = (x0 / COL_W).toInt().coerceIn(0, last)
        val col1 = (x1 / COL_W).toInt().coerceIn(0, last)
        for (i in col0..col1) {
            val wx = i * COL_W
            windSquall[i] = squallAt(wx, time)
            windOscA[i] = oscAt(wx, time)
            windOscB[i] = oscAt(wx, time + PHASE_LAG)
        }
        val rad = if (bike) 15.5f else 11f
        val rad2 = rad * rad
        val half = (dt * 0.5f).coerceAtMost(1f / 60f)   // 큰 dt에서도 스프링이 터지지 않게 분할 적분

        for (i in firstAtOrAfter(y0) until firstAfter(y1)) {
            val b = blades[i]
            if (b.x < x0 || b.x > x1) continue
            val ci = (b.x / COL_W).toInt().coerceIn(0, last)
            val oa = windOscA[ci]
            val w = windSquall[ci] + oa + (windOscB[ci] - oa) * b.phase01
            val m = abs(w)
            val elapsed = time - b.lastUpdate
            if (elapsed > 0.12f || elapsed < 0f) {
                // 화면 밖에서 보낸 시간만큼 RECOVER를 진행시키고 자연스러운 바람 자세로 복귀.
                b.rec = (b.rec - elapsed.coerceAtLeast(0f)).coerceAtLeast(0f)
                b.lean = w * LEAN_AMP
                b.curl = w * CURL_AMP
                b.leanV = 0f; b.curlV = 0f
            }
            b.lastUpdate = time

            var st = if (m > GUST_TH) S_GUST else if (m > SWAY_TH) S_SWAY else S_IDLE
            var tLean = w * LEAN_AMP
            var tCurl = w * CURL_AMP

            // 밟힘 — 이동 방향으로 눕는다
            val dx = b.x - fx
            val dy = b.y - fy
            val d2 = dx * dx + dy * dy
            if (d2 < rad2) {
                // 선형 감쇠: 가까울수록 세게, 근처에 머무는 동안 목표값이 빨리 꺼지지 않게
                val push = 1f - sqrt(d2) / rad
                val s = if (dx < 0f) -1f else 1f
                tLean = w * LEAN_AMP * 0.25f + s * push * 5.5f
                tCurl = w * CURL_AMP * 0.25f + s * push * 3.0f
                st = S_BEND
                b.rec = RECOVER_T
            } else if (b.rec > 0f) {
                // rec 타이머가 "방금 밟혔고 아직 회복 중"이라는 상태를 기억한다.
                // (이전 프레임 상태로만 판정하면 RECOVER가 한 프레임 만에 풀려버린다)
                st = S_RECOVER
            }
            if (st == S_RECOVER) {
                b.rec -= dt
                if (b.rec <= 0f) {
                    b.rec = 0f
                    st = if (m > GUST_TH) S_GUST else if (m > SWAY_TH) S_SWAY else S_IDLE
                }
            }
            b.state = st

            // 스프링 적분 (LEAN/CURL 두 축) — 半隐式 오일러
            val kk = K[st] * b.stiff
            val dd = D[st] * b.stiff
            var lean = b.lean; var leanV = b.leanV
            var curl = b.curl; var curlV = b.curlV
            var n = 0
            while (n < 2) {
                leanV += ((tLean - lean) * kk - leanV * dd) * half
                lean += leanV * half
                curlV += ((tCurl - curl) * kk * 0.8f - curlV * dd * 0.8f) * half
                curl += curlV * half
                n++
            }
            b.lean = lean; b.leanV = leanV
            b.curl = curl; b.curlV = curlV
        }
    }

    // -----------------------------------------------------------------------
    // 그리기
    // -----------------------------------------------------------------------

    /**
     * 카메라와 발끝은 모두 32px 타일 기준 렌더 월드 좌표. 호출자는 화면 패딩만 적용한다.
     * @param feetY 캐릭터 발끝의 월드 y — 풀잎 밑동이 이보다 아래면 전경(캐릭터 뒤)으로 그린다
     */
    fun draw(
        c: Canvas, a: Assets,
        camX: Float, camY: Float, vw: Float, vh: Float,
        feetY: Float, layer: Int
    ) {
        val x0 = camX - 22f
        val x1 = camX + vw + 22f
        val y0 = camY - 22f
        val y1 = camY + vh + 22f
        val p = grassPaint
        val split = firstAfter(feetY) // 뒤쪽은 발보다 위/같고, 앞쪽은 발보다 아래
        val from = if (layer == LAYER_FRONT) maxOf(firstAtOrAfter(y0), split) else firstAtOrAfter(y0)
        val to = if (layer == LAYER_FRONT) firstAfter(y1) else minOf(firstAfter(y1), split)
        for (i in from until to) {
            val b = blades[i]
            if (b.x < x0 || b.x > x1) continue
            val bmp = a.grassPose(b.kind, b.lean.roundToInt(), b.curl.roundToInt())
            c.drawBitmap(bmp, b.x - camX - a.grassOx[b.kind], b.y - camY - a.grassOy[b.kind], p)
        }
    }
}
