package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ===========================================================================
// 디테일 연출 모음 (v0.3.1)
//
//  - Weather 연출 배율     : 그림자 세기·새 경계·스폰 간격 (날씨 자체는 Weather.kt)
//  - Glow / LightMap      : 부드러운 방사형 조명 (밤에 가로등·창문·반딧불이 어둠을 밝힌다)
//  - WorldFx              : 필드의 살아있는 디테일 — 물 깊이/반짝임, 발자국, 물웅덩이,
//                           나비·잠자리·나방, 물고기 파문, 머리 위 철새 그림자, 굴뚝 연기,
//                           비/눈/강풍 화면 효과
//
// 모든 좌표 규칙은 기존 코드와 같다: 월드 논리 px(타일 16) × WORLD_SCALE = 가상 화면 px.
// ===========================================================================

// 날씨 자체(종류·변화·새 출현 가중치)는 Weather.kt 가 담당한다. 여기서는 연출/체감 배율만 붙인다.

/** 햇빛 그림자 세기 (맑을수록 진하다) */
val Weather.shadowK: Float
    get() = when (this) {
        Weather.SUNNY -> 1f
        Weather.WIND -> 0.8f
        Weather.CLOUDY -> 0.45f
        Weather.SNOW -> 0.3f
        Weather.RAIN -> 0.12f
    }

/** 새 경계 거리 배율 — 빗소리에 발소리가 묻혀 살짝 덜 경계한다 */
val Weather.fleeK: Float
    get() = if (this == Weather.RAIN) 0.92f else 1f

/** 새 스폰 간격 배율 — 비/눈 오는 날은 조금 뜸하다 */
val Weather.spawnK: Float
    get() = when (this) {
        Weather.RAIN -> 1.15f
        Weather.SNOW -> 1.1f
        else -> 1f
    }

// ---------------------------------------------------------------------------
// 부드러운 빛 스프라이트
// ---------------------------------------------------------------------------

object Glow {
    private fun radial(r: Int, g: Int, b: Int): Bitmap {
        val n = 64
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            n / 2f, n / 2f, n / 2f,
            intArrayOf(Color.argb(255, r, g, b), Color.argb(150, r, g, b), Color.argb(46, r, g, b), Color.argb(0, r, g, b)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawCircle(n / 2f, n / 2f, n / 2f, p)
        return bmp
    }

    /** 흰색 (어둠 지우기/안개) */
    val soft: Bitmap by lazy { radial(255, 255, 255) }
    /** 전구색 (가로등/창문/화덕) */
    val warm: Bitmap by lazy { radial(255, 204, 120) }
    /** 연두빛 (반딧불) */
    val green: Bitmap by lazy { radial(214, 255, 130) }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    /** 가산 느낌의 빛 번짐 한 점 */
    fun draw(c: Canvas, sprite: Bitmap, x: Float, y: Float, rx: Float, ry: Float, alpha: Int) {
        if (alpha <= 0) return
        paint.alpha = alpha.coerceIn(0, 255)
        rect.set(x - rx, y - ry, x + rx, y + ry)
        c.drawBitmap(sprite, null, rect, paint)
    }
}

/**
 * 조명 맵: 화면 크기의 어둠 레이어에 빛 모양으로 구멍을 뚫은 뒤 한 번에 덮는다.
 * 단순히 반투명 원을 겹치던 방식보다 빛 가장자리가 훨씬 부드럽고, 밤이 더 밤답다.
 */
class LightMap(val w: Int, val h: Int) {
    private val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val cv = Canvas(bmp)
    private val punch = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val rect = RectF()

    fun begin(color: Int) {
        bmp.eraseColor(color)
    }

    /** (x, y) 중심 반경 r 의 빛. strength 0..255 */
    fun light(x: Float, y: Float, r: Float, strength: Int) = light(x, y, r, r, strength)

    fun light(x: Float, y: Float, rx: Float, ry: Float, strength: Int) {
        if (strength <= 0) return
        if (x + rx < 0f || y + ry < 0f || x - rx > w || y - ry > h) return
        punch.alpha = strength.coerceIn(0, 255)
        rect.set(x - rx, y - ry, x + rx, y + ry)
        cv.drawBitmap(Glow.soft, null, rect, punch)
    }

    fun end(c: Canvas) {
        c.drawBitmap(bmp, 0f, 0f, null)
    }
}

/** 화면 크기 조명 맵 하나를 씬끼리 돌려쓴다 (씬마다 2MB 비트맵을 새로 만들지 않도록) */
object LightMaps {
    private var lm: LightMap? = null

    fun get(w: Int, h: Int): LightMap {
        val cur = lm
        if (cur != null && cur.w == w && cur.h == h) return cur
        val made = LightMap(w, h)
        lm = made
        return made
    }
}

/**
 * 시각(0~24) → 햇빛 세기 0..1.
 *
 * 태양 고도를 기준으로 매 프레임 연속적으로 변한다(계절에 따라 일출·일몰도 이동).
 * 실제 곡선은 [DayCycle] 이 갖고 있고, 여기서는 기존 호출부를 위한 얇은 창구다.
 */
fun daylight(hour: Float): Float = DayCycle.daylight(hour)

/** 좌표 해시 (결정적 난수) */
fun hash2(x: Int, y: Int, salt: Int = 0): Int {
    var h = x * 374761393 + y * 668265263 + salt * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    return (h xor (h ushr 16)) and 0x7fffffff
}

// ---------------------------------------------------------------------------
// 월드 디테일
// ---------------------------------------------------------------------------

class WorldFx(private val map: GameMap, seed: Long) {

    var weather = Weather.SUNNY
    /** 현재 계절 — 겨울 적설 유지·얼음·곤충 출현에 쓴다. WorldScene이 매 프레임 동기화. */
    var season: Season = Season.SPRING
    private val rnd = Random(seed)

    /** 물 깊이 (0 = 물 아님, 1 = 물가, 2, 3 = 깊음) */
    private val depth = Array(map.h) { ByteArray(map.w) }
    /** 뭍 칸의 물 이웃 방향 비트 (N1 E2 S4 W8) */
    private val shore = Array(map.h) { ByteArray(map.w) }

    /** 비가 온 정도 (0..1) — 비가 그쳐도 한동안 물웅덩이가 남는다. 눈이면 쌓인 정도 */
    var wet = 0f
        private set
    var snowCover = 0f
        private set

    init {
        // 물 깊이: 뭍에서 멀어질수록 깊다 (BFS, 최대 3)
        val qx = IntArray(map.w * map.h)
        val qy = IntArray(map.w * map.h)
        var head = 0
        var tail = 0
        for (y in 0 until map.h) for (x in 0 until map.w) {
            if (!isWater(x, y)) continue
            var nearLand = false
            for ((dx, dy) in NEI4) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until map.w && ny in 0 until map.h && !isWater(nx, ny)) nearLand = true
            }
            if (nearLand) {
                depth[y][x] = 1
                qx[tail] = x; qy[tail] = y; tail++
            }
        }
        while (head < tail) {
            val x = qx[head]; val y = qy[head]; head++
            val d = depth[y][x]
            if (d >= 3) continue
            for ((dx, dy) in NEI4) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until map.w || ny !in 0 until map.h) continue
                if (!isWater(nx, ny) || depth[ny][nx].toInt() != 0) continue
                depth[ny][nx] = (d + 1).toByte()
                qx[tail] = nx; qy[tail] = ny; tail++
            }
        }
        // 가장자리 바다처럼 뭍과 닿지 않은 물은 깊은 물
        for (y in 0 until map.h) for (x in 0 until map.w) {
            if (isWater(x, y) && depth[y][x].toInt() == 0) depth[y][x] = 3
        }
        // 물가 (젖은 흙 띠)
        for (y in 0 until map.h) for (x in 0 until map.w) {
            if (isWater(x, y) || map.t(x, y).bulk) continue
            var m = 0
            if (isWater(x, y - 1)) m = m or 1
            if (isWater(x + 1, y)) m = m or 2
            if (isWater(x, y + 1)) m = m or 4
            if (isWater(x - 1, y)) m = m or 8
            shore[y][x] = m.toByte()
        }
    }

    private fun isWater(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= map.w || y >= map.h) return false
        return map.groundAt(x, y) == T.WATER && map.paveAt(x, y) == Pave.NONE && map.t(x, y) == T.WATER
    }

    // -------------------------------------------------------------------
    // 파티클 / 데칼 / 생물
    // -------------------------------------------------------------------

    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val max: Float, val col: Int,
        var size: Float, val grow: Float, val gravity: Float
    )

    /** 바닥에 남는 자국: 0 모래 발자국, 1 자전거 바퀴 자국, 2 젖은 발자국, 3 눈 발자국 */
    private class Decal(val x: Float, val y: Float, val kind: Int, val horiz: Boolean, var life: Float, val max: Float)

    /** 파문 (물고기/빗방울) */
    private class Ripple(val x: Float, val y: Float, var t: Float, val max: Float, val big: Boolean, val fish: Boolean)

    /** 나비(0)/잠자리(1) */
    private class Critter(
        val kind: Int, var x: Float, var y: Float,
        var tx: Float, var ty: Float, val col: Int,
        var t: Float, var life: Float, var restT: Float, var alt: Float
    )

    /** 머리 위를 지나가는 철새 무리 (그림자) */
    private class Flock(var x: Float, var y: Float, val vx: Float, val vy: Float, val n: Int, val phase: Float)

    private val parts = ArrayList<P>()
    private val decals = ArrayList<Decal>()
    private val ripples = ArrayList<Ripple>()
    private val critters = ArrayList<Critter>()
    private val flocks = ArrayList<Flock>()

    private var critterT = 1f
    private var rippleT = 0.5f
    private var flockT = 12f
    private var smokeT = 0f
    private var stepSide = false

    // -------------------------------------------------------------------
    // 화면 공간 날씨 입자 (비/눈) — 카메라와 완전히 분리된다.
    //
    // 월드 입자(연기·풀잎·나비)와 달리 화면(가상 px) 좌표를 쓰므로 캐릭터가
    // 움직여도 함께 밀리지 않는다. 영역 크기는 월드 시야(viewW/viewH)가 아니라
    // 실제 화면(game.virtW/virtH)을 기준으로 삼는다 — 줌(달리기·자전거·망원)이
    // 바뀌어도 비/눈 영역이 늘어나거나 줄지 않는다.
    //
    // ※ 과거 버그: viewW*WORLD_SCALE(= virtW/zoom)을 영역으로 쓰는 바람에
    //    눈이 화면 왼쪽 위 1/zoom²에만 내리고, 이동(줌 변화)할 때마다 눈 영역이
    //    같이 움직였다. setScreen/drawWeather가 화면 크기를 단일 소유한다.
    // -------------------------------------------------------------------
    private var scrW = 960f
    private var scrH = 540f
    private val dropX = FloatArray(120)
    private val dropY = FloatArray(120)
    private val dropV = FloatArray(120)
    private val flakeX = FloatArray(80)
    private val flakeY = FloatArray(80)
    private val flakeV = FloatArray(80)
    private val flakePh = FloatArray(80)
    private val splashX = FloatArray(26)
    private val splashY = FloatArray(26)
    private val splashT = FloatArray(26)
    private var weatherInit = false

    /** 화면 크기 등록 — 바뀌면 입자를 비례 재배치한다 (폴더블/분할화면 대응) */
    fun setScreen(w: Float, h: Float) {
        val nw = w.coerceAtLeast(1f)
        val nh = h.coerceAtLeast(1f)
        if (weatherInit && nw == scrW && nh == scrH) return
        if (weatherInit && scrW > 0f && scrH > 0f) {
            val kx = nw / scrW
            val ky = nh / scrH
            for (i in dropX.indices) { dropX[i] *= kx; dropY[i] *= ky }
            for (i in flakeX.indices) { flakeX[i] *= kx; flakeY[i] *= ky }
            for (i in splashT.indices) { splashX[i] *= kx; splashY[i] *= ky }
        }
        scrW = nw
        scrH = nh
        if (!weatherInit) initWeatherParticles()
    }

    private fun initWeatherParticles() {
        weatherInit = true
        for (i in dropX.indices) {
            dropX[i] = rnd.nextFloat() * scrW
            dropY[i] = rnd.nextFloat() * scrH
            dropV[i] = 420f + rnd.nextFloat() * 220f
        }
        for (i in flakeX.indices) {
            flakeX[i] = rnd.nextFloat() * scrW
            flakeY[i] = rnd.nextFloat() * scrH
            flakeV[i] = flakeSpeed(i)
            flakePh[i] = rnd.nextFloat() * 6f
        }
        for (i in splashT.indices) {
            splashT[i] = rnd.nextFloat()
            splashX[i] = rnd.nextFloat() * scrW
            splashY[i] = rnd.nextFloat() * scrH
        }
    }

    /** 눈송이 깊이 등급 (0 = 먼/작음 · 2 = 가까운/큼) — 크고 가까울수록 빠르고 진하다 */
    private fun flakeTier(i: Int): Int = i % 3
    private fun flakeSize(tier: Int): Float = when (tier) { 0 -> 1.6f; 1 -> 2.4f; else -> 3.2f }
    private fun flakeAlpha(tier: Int): Int = when (tier) { 0 -> 150; 1 -> 195; else -> 235 }
    private fun flakeSpeed(i: Int): Float = when (flakeTier(i)) {
        0 -> 20f + rnd.nextFloat() * 12f
        1 -> 30f + rnd.nextFloat() * 14f
        else -> 44f + rnd.nextFloat() * 18f
    }

    private val fill = Paint()
    private val aa = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val rect = RectF()

    // 카메라 (논리 px) — update에서 갱신
    private var camX = 0f
    private var camY = 0f
    private var viewW = 480f
    private var viewH = 270f
    private var hour = 12f
    private var time = 0f

    private fun addP(x: Float, y: Float, vx: Float, vy: Float, life: Float, col: Int, size: Float, grow: Float = 0f, gravity: Float = 0f) {
        if (parts.size >= 90) return
        parts.add(P(x, y, vx, vy, life, life, col, size, grow, gravity))
    }

    // -------------------------------------------------------------------
    // 발걸음
    // -------------------------------------------------------------------

    /**
     * 발을 디딜 때마다 호출. (fx, fy) = 발 위치 (논리 px)
     * 지형에 따라 모래 발자국·바퀴 자국·풀잎·흙먼지·물 튀김이 생긴다.
     */
    fun onStep(fx: Float, fy: Float, facing: Dir, bike: Boolean, running: Boolean) {
        val tx = (fx / 16f).toInt()
        val ty = (fy / 16f).toInt()
        val tile = map.t(tx, ty)
        val paved = map.paveAt(tx, ty) != Pave.NONE
        val ground = map.groundAt(tx, ty)
        val horiz = facing == Dir.E || facing == Dir.W
        stepSide = !stepSide
        val side = if (stepSide) 2.2f else -2.2f
        val ox = if (horiz) 0f else side
        val oy = if (horiz) side * 0.6f else 0f

        fun decal(kind: Int, life: Float) {
            if (decals.size >= 70) decals.removeAt(0)
            decals.add(Decal(fx + (if (bike) 0f else ox), fy + (if (bike) 0f else oy), kind, horiz, life, life))
        }

        val puddle = wet > 0.25f && paved && hash2(tx, ty, 5) % 5 == 0
        when {
            snowCover > 0.3f && !paved && tile != T.WATER -> {
                decal(if (bike) 1 else 3, 10f)
                if (running || bike) addP(fx, fy, (rnd.nextFloat() - 0.5f) * 14f, -10f, 0.5f, Color.argb(220, 248, 250, 255), 1.6f, gravity = 40f)
            }
            puddle || (weather == Weather.RAIN && rnd.nextFloat() < 0.5f) -> {
                // 찰박! 물 튀김
                repeat(if (puddle) 4 else 2) {
                    addP(fx, fy, (rnd.nextFloat() - 0.5f) * 26f, -14f - rnd.nextFloat() * 10f, 0.4f,
                        Color.argb(200, 200, 225, 245), 1.4f, gravity = 90f)
                }
                if (puddle) ripples.add(Ripple(fx, fy, 0f, 0.6f, false, false))
                if (paved) decal(2, 4f)
            }
            (tile == T.SAND || (ground == T.SAND && paved)) -> {
                decal(if (bike) 1 else 0, if (bike) 6f else 8f)
                if (running || bike) addP(fx, fy, (rnd.nextFloat() - 0.5f) * 10f, -6f, 0.5f, Color.argb(150, 226, 204, 150), 2f, grow = 2f)
            }
            tile == T.TALLGRASS || tile == T.REED -> {
                // 사각사각 — 풀잎이 튄다
                repeat(2) {
                    addP(fx + (rnd.nextFloat() - 0.5f) * 8f, fy - 4f, (rnd.nextFloat() - 0.5f) * 18f, -16f - rnd.nextFloat() * 8f, 0.6f,
                        if (tile == T.REED) Color.argb(220, 176, 160, 96) else Color.argb(220, 111, 174, 87), 1.8f, gravity = 50f)
                }
            }
            tile == T.GRASS || tile == T.FLOWER -> {
                if (rnd.nextFloat() < (if (running) 0.45f else 0.18f)) {
                    addP(fx, fy - 1f, (rnd.nextFloat() - 0.5f) * 12f, -10f, 0.45f,
                        if (tile == T.FLOWER && rnd.nextBoolean()) Color.argb(220, 242, 163, 179) else Color.argb(210, 132, 196, 104), 1.5f, gravity = 40f)
                }
            }
            paved -> {
                if (running) addP(fx, fy, (rnd.nextFloat() - 0.5f) * 8f, -5f, 0.55f, Color.argb(110, 170, 150, 115), 2.2f, grow = 3f)
            }
        }
    }

    // -------------------------------------------------------------------
    // 업데이트
    // -------------------------------------------------------------------

    fun update(dt: Float, t: Float, hourNow: Float, cx: Float, cy: Float, vw: Float, vh: Float, playerCx: Float, playerCy: Float) {
        camX = cx; camY = cy; viewW = vw; viewH = vh; hour = hourNow; time = t

        // 비/눈 누적 — 겨울엔 눈이 오지 않아도 쌓인 눈이 유지된다
        wet = if (weather == Weather.RAIN) (wet + dt / 25f).coerceAtMost(1f) else (wet - dt / 70f).coerceAtLeast(0f)
        val snowTarget = when {
            weather == Weather.SNOW -> 1f
            season == Season.WINTER -> 0.72f
            else -> 0f
        }
        snowCover = if (snowTarget > snowCover) (snowCover + dt / 40f).coerceAtMost(snowTarget)
        else (snowCover - dt / 90f).coerceAtLeast(snowTarget)
        if (weather == Weather.RAIN) snowCover = (snowCover - dt / 30f).coerceAtLeast(0f)

        // 파티클
        run {
            val it = parts.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.vy += p.gravity * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.size += p.grow * dt
                p.life -= dt
                if (p.life <= 0f) it.remove()
            }
        }
        run {
            val it = decals.iterator()
            while (it.hasNext()) {
                val d = it.next()
                d.life -= dt
                if (d.life <= 0f) it.remove()
            }
        }
        run {
            val it = ripples.iterator()
            while (it.hasNext()) {
                val r = it.next()
                r.t += dt
                if (r.t >= r.max) it.remove()
            }
        }

        updateRipples(dt)
        updateCritters(dt, playerCx, playerCy)
        updateFlocks(dt)
        updateSmoke(dt)
        updateWeatherParticles(dt)
    }

    private fun randomVisibleTile(): Pair<Int, Int> {
        val tx = ((camX + rnd.nextFloat() * viewW) / 16f).toInt().coerceIn(0, map.w - 1)
        val ty = ((camY + rnd.nextFloat() * viewH) / 16f).toInt().coerceIn(0, map.h - 1)
        return tx to ty
    }

    private fun updateRipples(dt: Float) {
        rippleT -= dt
        if (rippleT > 0f) return
        rippleT = if (weather == Weather.RAIN) 0.06f else 0.45f + rnd.nextFloat() * 0.6f
        if (ripples.size > 40) return
        for (i in 0 until 6) {
            val (tx, ty) = randomVisibleTile()
            val onWater = isWater(tx, ty)
            val onPuddle = wet > 0.3f && map.paveAt(tx, ty) != Pave.NONE && hash2(tx, ty, 5) % 5 == 0
            if (!onWater && !(onPuddle && weather == Weather.RAIN)) continue
            val x = tx * 16f + 3f + rnd.nextFloat() * 10f
            val y = ty * 16f + 3f + rnd.nextFloat() * 10f
            if (weather == Weather.RAIN) {
                ripples.add(Ripple(x, y, 0f, 0.5f, false, false))
            } else {
                val fish = onWater && depth[ty][tx] >= 2 && rnd.nextFloat() < 0.18f
                ripples.add(Ripple(x, y, 0f, if (fish) 1.6f else 1.3f, true, fish))
                if (fish) {
                    repeat(4) {
                        addP(x, y, (rnd.nextFloat() - 0.5f) * 30f, -22f - rnd.nextFloat() * 12f, 0.55f,
                            Color.argb(220, 230, 245, 252), 1.5f, gravity = 90f)
                    }
                }
            }
            break
        }
    }

    private fun updateCritters(dt: Float, pcx: Float, pcy: Float) {
        val day = daylight(hour)
        val calm = weather == Weather.SUNNY || weather == Weather.CLOUDY
        critterT -= dt
        if (critterT <= 0f) {
            critterT = 1.1f
            val butterflies = critters.count { it.kind == 0 }
            val flies = critters.count { it.kind == 1 }
            if (day > 0.6f && calm) {
                // 나비는 봄·여름에만, 잠자리는 여름(파랑)·가을(빨강 고추잠자리)에만
                val butterflySeason = season == Season.SPRING || season == Season.SUMMER
                val dragonflySeason = season == Season.SUMMER || season == Season.AUTUMN
                val maxB = if (season == Season.SPRING) 5 else if (weather == Weather.SUNNY) 4 else 2
                val maxF = if (season == Season.AUTUMN) 4 else 2
                for (i in 0 until 6) {
                    val (tx, ty) = randomVisibleTile()
                    val tile = map.t(tx, ty)
                    if (butterflySeason && butterflies < maxB && (tile == T.FLOWER || (tile == T.GRASS && rnd.nextFloat() < 0.08f))) {
                        val cols = intArrayOf(0xFFF8F4E8.toInt(), 0xFFF6D860.toInt(), 0xFFF0A046.toInt(), 0xFFA8CCF2.toInt(), 0xFFF2A3B3.toInt())
                        val x = tx * 16f + 8f
                        val y = ty * 16f + 8f
                        critters.add(Critter(0, x, y, x, y, cols[rnd.nextInt(cols.size)], rnd.nextFloat() * 5f, 25f + rnd.nextFloat() * 20f, 0f, 10f))
                        break
                    }
                    if (dragonflySeason && flies < maxF && (tile == T.REED || isWater(tx, ty))) {
                        // 여름엔 파란 잠자리, 가을엔 빨간 고추잠자리
                        val cols = if (season == Season.AUTUMN)
                            intArrayOf(0xFFD8553C.toInt(), 0xFFE8823C.toInt(), 0xFFD8553C.toInt())
                        else
                            intArrayOf(0xFF3FA7B8.toInt(), 0xFF5C8FD6.toInt(), 0xFF3FA7B8.toInt())
                        val x = tx * 16f + 8f
                        val y = ty * 16f + 8f
                        critters.add(Critter(1, x, y, x, y, cols[rnd.nextInt(cols.size)], 0f, 20f + rnd.nextFloat() * 15f, 0.5f, 12f))
                        break
                    }
                }
            }
        }
        val it = critters.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t += dt
            c.life -= dt
            // 해가 지거나 비가 오거나 제철이 아니면 슬슬 사라진다
            val inSeason = if (c.kind == 0) (season == Season.SPRING || season == Season.SUMMER)
            else (season == Season.SUMMER || season == Season.AUTUMN)
            if (day < 0.4f || !calm || !inSeason) c.life = minOf(c.life, 1.5f)
            if (c.life <= 0f) { it.remove(); continue }
            val dp = hypot(c.x - pcx, c.y - pcy)
            if (c.kind == 0) {
                // 나비: 목표점 주변을 팔랑팔랑, 사람이 오면 멀리
                if (dp < 26f) {
                    val k = 40f / (dp + 1f)
                    c.tx = c.x + (c.x - pcx) * k
                    c.ty = c.y + (c.y - pcy) * k - 10f
                } else if (hypot(c.tx - c.x, c.ty - c.y) < 4f || rnd.nextFloat() < dt * 0.5f) {
                    c.tx = c.x + (rnd.nextFloat() - 0.5f) * 60f
                    c.ty = c.y + (rnd.nextFloat() - 0.5f) * 40f
                }
                val dx = c.tx - c.x
                val dy = c.ty - c.y
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val sp = if (dp < 26f) 38f else 16f
                c.x += dx / len * sp * dt + sin(c.t * 3.1f) * 6f * dt
                c.y += dy / len * sp * dt + cos(c.t * 4.3f) * 8f * dt
                c.alt = 9f + sin(c.t * 2.2f) * 3f
            } else {
                // 잠자리: 정지 비행 → 휙 이동
                c.restT -= dt
                if (c.restT <= 0f || dp < 22f) {
                    c.tx = c.x + (rnd.nextFloat() - 0.5f) * 70f
                    c.ty = c.y + (rnd.nextFloat() - 0.5f) * 50f
                    c.restT = 0.7f + rnd.nextFloat() * 1.4f
                }
                val k = (dt * 7f).coerceAtMost(1f)
                c.x += (c.tx - c.x) * k + sin(c.t * 23f) * 0.25f
                c.y += (c.ty - c.y) * k
                c.alt = 12f + sin(c.t * 5f) * 1.2f
            }
            // 화면에서 너무 멀어지면 제거
            if (c.x < camX - 80f || c.x > camX + viewW + 80f || c.y < camY - 80f || c.y > camY + viewH + 80f) it.remove()
        }
    }

    private fun updateFlocks(dt: Float) {
        flockT -= dt
        if (flockT <= 0f) {
            flockT = 22f + rnd.nextFloat() * 26f
            if (weather != Weather.RAIN && weather != Weather.SNOW && flocks.size < 2) {
                val fromLeft = rnd.nextBoolean()
                val vx = (if (fromLeft) 1f else -1f) * (46f + rnd.nextFloat() * 18f)
                val vy = (rnd.nextFloat() - 0.5f) * 24f
                val x = if (fromLeft) camX - 90f else camX + viewW + 90f
                val y = camY + viewH * (0.2f + rnd.nextFloat() * 0.6f)
                flocks.add(Flock(x, y, vx, vy, 5 + rnd.nextInt(4), rnd.nextFloat() * 6f))
            }
        }
        val it = flocks.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.x += f.vx * dt
            f.y += f.vy * dt
            if (f.x < camX - 260f || f.x > camX + viewW + 260f) it.remove()
        }
    }

    private fun updateSmoke(dt: Float) {
        if (!map.hasHouse) return
        smokeT -= dt
        if (smokeT > 0f) return
        smokeT = if (weather == Weather.RAIN) 0.7f else 0.42f
        val (cx, cy) = chimneyTop()
        if (cx < camX - 40f || cx > camX + viewW + 40f || cy < camY - 80f || cy > camY + viewH + 40f) return
        val night = daylight(hour) < 0.3f
        val g = if (night) 150 else 232
        addP(cx + (rnd.nextFloat() - 0.5f) * 2f, cy, 5f + rnd.nextFloat() * 4f, -9f - rnd.nextFloat() * 4f, 3.2f,
            Color.argb(120, g, g, (g + 10).coerceAtMost(255)), 2.6f, grow = 2.4f, gravity = -1.5f)
    }

    /** 우리 집 굴뚝 꼭대기 (논리 px) */
    private fun chimneyTop(): Pair<Float, Float> = (24 * 16f + 9f) to (8 * 16f - 3f)

    private fun updateWeatherParticles(dt: Float) {
        // 화면 공간 입자 — 월드 시야(viewW/viewH)가 아닌 화면 크기(scrW/scrH)로만
        // 굴린다. 카메라 이동·줌과 무관하므로 비/눈이 캐릭터를 따라다니지 않는다.
        if (!weatherInit) initWeatherParticles()
        val vw = scrW
        val vh = scrH
        when (weather) {
            Weather.RAIN -> {
                for (i in dropX.indices) {
                    dropY[i] += dropV[i] * dt
                    dropX[i] += dropV[i] * 0.22f * dt
                    if (dropY[i] > vh + 20f || dropX[i] > vw + 20f) {
                        dropY[i] = -20f - rnd.nextFloat() * 40f
                        dropX[i] = rnd.nextFloat() * (vw + 120f) - 120f
                    }
                }
                for (i in splashT.indices) {
                    splashT[i] += dt * 2.6f
                    if (splashT[i] >= 1f) {
                        splashT[i] = 0f
                        splashX[i] = rnd.nextFloat() * vw
                        splashY[i] = rnd.nextFloat() * vh
                    }
                }
            }
            Weather.SNOW -> {
                for (i in flakeX.indices) {
                    flakeY[i] += flakeV[i] * dt
                    // 위상별 흔들림 + 완만한 동풍 — 캐릭터 이동과 무관한 고유 운동
                    flakeX[i] += sin(time * 1.3f + flakePh[i]) * 14f * dt + 5f * dt
                    if (flakeY[i] > vh + 4f) {
                        flakeY[i] = -4f
                        flakeX[i] = rnd.nextFloat() * vw
                        flakeV[i] = flakeSpeed(i)
                    }
                    // 좌우 순환 — 흔들림에 밀려 화면 밖으로 나가도 반대편에서 돌아온다
                    if (flakeX[i] > vw + 4f) flakeX[i] -= vw + 8f
                    else if (flakeX[i] < -4f) flakeX[i] += vw + 8f
                }
            }
            else -> {}
        }
    }

    // -------------------------------------------------------------------
    // 그리기 — 지면 (타일 직후, 엔티티 이전)
    // -------------------------------------------------------------------

    fun drawGround(c: Canvas, camXv: Float, camYv: Float, vw: Int, vh: Int) {
        val x0 = (camXv / 32f).toInt().coerceAtLeast(0)
        val y0 = (camYv / 32f).toInt().coerceAtLeast(0)
        val x1 = ((camXv + vw) / 32f).toInt().coerceAtMost(map.w - 1)
        val y1 = ((camYv + vh) / 32f).toInt().coerceAtMost(map.h - 1)
        val dayK = daylight(hour)

        for (y in y0..y1) {
            for (x in x0..x1) {
                val sx = x * 32f - camXv
                val sy = y * 32f - camYv
                val d = depth[y][x].toInt()
                if (d > 0) {
                    // 1) 깊은 물은 더 짙게
                    if (d >= 2) {
                        fill.color = Color.argb(if (d == 2) 26 else 50, 16, 64, 128)
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, fill)
                    }
                    // 겨울엔 물이 언다 — 얼음판 + 금 + 가장자리 눈
                    if (season == Season.WINTER) {
                        fill.color = Color.argb(168, 208, 230, 242)
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, fill)
                        val h = hash2(x, y, 41)
                        line.strokeWidth = 1f
                        line.color = Color.argb(150, 255, 255, 255)
                        c.drawLine(sx + 4f + (h % 18), sy + 5f, sx + 12f + (h % 12), sy + 25f, line)
                        c.drawLine(sx + 22f, sy + 4f + ((h / 7) % 16), sx + 29f, sy + 21f, line)
                        line.color = Color.argb(90, 160, 190, 215)
                        c.drawLine(sx + 6f + (h % 14), sy + 8f, sx + 10f + (h % 10), sy + 27f, line)
                        if ((h / 13) % 3 == 0) {
                            aa.color = Color.argb(220, 246, 249, 255)
                            rect.set(sx + 20f, sy + 22f, sx + 30f, sy + 29f)
                            c.drawOval(rect, aa)
                        }
                    }
                    // 2) 물가에서 찰랑이는 두 번째 거품 줄 (언 물엔 파도가 없다)
                    val lap = 4.5f + sin(time * 1.7f + x * 0.9f + y * 0.6f) * 2.2f
                    val frozen = season == Season.WINTER
                    if (!frozen) {
                        fill.color = Color.argb(95, 236, 248, 252)
                        if (!isWater(x, y - 1) && y > 0) c.drawRect(sx + 2f, sy + lap, sx + 30f, sy + lap + 1.3f, fill)
                        if (!isWater(x, y + 1) && y < map.h - 1) c.drawRect(sx + 2f, sy + 32f - lap - 1.3f, sx + 30f, sy + 32f - lap, fill)
                        if (!isWater(x - 1, y) && x > 0) c.drawRect(sx + lap, sy + 2f, sx + lap + 1.3f, sy + 30f, fill)
                        if (!isWater(x + 1, y) && x < map.w - 1) c.drawRect(sx + 32f - lap - 1.3f, sy + 2f, sx + 32f - lap, sy + 30f, fill)
                        // 3) 반짝이는 윤슬 (맑은 낮엔 햇빛, 밤엔 달빛)
                        val h = hash2(x, y, 3)
                        val tw = sin(time * (1.3f + (h % 7) * 0.12f) + (h % 628) / 100f)
                        val gate = if (weather == Weather.SUNNY) 0.86f else 0.95f
                        if (tw > gate) {
                            val a = ((tw - gate) / (1f - gate) * (if (dayK > 0.3f) 230 else 150)).toInt()
                            val gx = sx + 5f + (h % 22)
                            val gy = sy + 5f + ((h / 22) % 22)
                            fill.color = Color.argb(a, 255, 255, 250)
                            c.drawRect(gx - 2.4f, gy - 0.6f, gx + 2.4f, gy + 0.6f, fill)
                            c.drawRect(gx - 0.6f, gy - 2.4f, gx + 0.6f, gy + 2.4f, fill)
                        }
                    } // if (!frozen)
                } else {
                    // 4) 물가 흙은 촉촉하게
                    val m = shore[y][x].toInt()
                    if (m != 0) {
                        val sand = map.groundAt(x, y) == T.SAND
                        fill.color = if (sand) Color.argb(70, 150, 120, 70) else Color.argb(48, 40, 70, 50)
                        if (m and 1 != 0) c.drawRect(sx, sy, sx + 32f, sy + 4f, fill)
                        if (m and 2 != 0) c.drawRect(sx + 28f, sy, sx + 32f, sy + 32f, fill)
                        if (m and 4 != 0) c.drawRect(sx, sy + 28f, sx + 32f, sy + 32f, fill)
                        if (m and 8 != 0) c.drawRect(sx, sy, sx + 4f, sy + 32f, fill)
                    }
                    // 5) 비 온 뒤 물웅덩이 (포장길 위)
                    if (wet > 0.05f && map.paveAt(x, y) != Pave.NONE && hash2(x, y, 5) % 5 == 0 && !map.t(x, y).solid) {
                        val h = hash2(x, y, 9)
                        val px = sx + 8f + (h % 10)
                        val py = sy + 10f + ((h / 10) % 10)
                        val rw = 5f + wet * 6f + (h % 3)
                        val rh = 2.5f + wet * 3f
                        aa.color = Color.argb((110 * wet).toInt(), 70, 92, 120)
                        rect.set(px - rw, py - rh, px + rw, py + rh)
                        c.drawOval(rect, aa)
                        aa.color = Color.argb((120 * wet).toInt(), 200, 222, 240)
                        rect.set(px - rw * 0.6f, py - rh * 0.7f, px - rw * 0.1f, py - rh * 0.3f)
                        c.drawOval(rect, aa)
                    }
                    // 6) 쌓인 눈
                    if (snowCover > 0.05f) drawSnowOnTile(c, x, y, sx, sy)
                }
            }
        }

        // 발자국 / 바퀴 자국
        for (d in decals) {
            val k = (d.life / d.max).coerceIn(0f, 1f)
            val sx = d.x * WORLD_SCALE - camXv
            val sy = d.y * WORLD_SCALE - camYv
            when (d.kind) {
                1 -> {
                    fill.color = if (snowCover > 0.3f) Color.argb((110 * k).toInt(), 150, 160, 180) else Color.argb((80 * k).toInt(), 120, 95, 60)
                    if (d.horiz) c.drawRect(sx - 7f, sy - 0.8f, sx + 7f, sy + 0.8f, fill)
                    else c.drawRect(sx - 0.8f, sy - 7f, sx + 0.8f, sy + 7f, fill)
                }
                else -> {
                    fill.color = when (d.kind) {
                        0 -> Color.argb((85 * k).toInt(), 150, 118, 70)
                        2 -> Color.argb((70 * k).toInt(), 60, 70, 80)
                        else -> Color.argb((120 * k).toInt(), 150, 165, 190)
                    }
                    if (d.horiz) {
                        rect.set(sx - 2.6f, sy - 1.4f, sx + 2.6f, sy + 1.4f)
                    } else {
                        rect.set(sx - 1.4f, sy - 2.6f, sx + 1.4f, sy + 2.6f)
                    }
                    c.drawOval(rect, fill)
                }
            }
        }

        // 파문
        line.strokeWidth = 1.3f
        for (r in ripples) {
            val k = r.t / r.max
            val sx = r.x * WORLD_SCALE - camXv
            val sy = r.y * WORLD_SCALE - camYv
            val rad = (if (r.big) 3f + k * 14f else 1.5f + k * 6f)
            line.color = Color.argb((170 * (1f - k)).toInt(), 230, 244, 250)
            rect.set(sx - rad, sy - rad * 0.5f, sx + rad, sy + rad * 0.5f)
            c.drawOval(rect, line)
            if (r.big && k > 0.2f) {
                val r2 = rad * 0.55f
                rect.set(sx - r2, sy - r2 * 0.5f, sx + r2, sy + r2 * 0.5f)
                c.drawOval(rect, line)
            }
            // 물고기 점프: 은빛 물고기가 포물선을 그린다
            if (r.fish && r.t < 0.55f) {
                val f = r.t / 0.55f
                val jx = sx + (f - 0.5f) * 14f
                val jy = sy - sin(f * Math.PI.toFloat()) * 16f
                fill.color = 0xFFB8C8D4.toInt()
                c.drawRect(jx - 3f, jy - 1.2f, jx + 3f, jy + 1.2f, fill)
                fill.color = 0xFF7F93A3.toInt()
                c.drawRect(jx + (if (f < 0.5f) 3f else -5f), jy - 1.6f, jx + (if (f < 0.5f) 5f else -3f), jy + 1.6f, fill)
            }
        }

        // 굴뚝 (우리 집)
        if (map.hasHouse) {
            val (cx, cy) = chimneyTop()
            val bx = cx * WORLD_SCALE - camXv
            val by = cy * WORLD_SCALE - camYv
            fill.color = 0xFF7A3F2C.toInt()
            c.drawRect(bx - 7f, by, bx + 7f, by + 20f, fill)
            fill.color = 0xFF9C5238.toInt()
            c.drawRect(bx - 6f, by + 1f, bx + 6f, by + 19f, fill)
            fill.color = 0xFF6A3424.toInt()
            c.drawRect(bx - 6f, by + 7f, bx + 6f, by + 8.4f, fill)
            c.drawRect(bx - 6f, by + 13f, bx + 6f, by + 14.4f, fill)
            c.drawRect(bx - 1f, by + 1f, bx + 0.4f, by + 7f, fill)
            fill.color = 0xFF5A5058.toInt()
            c.drawRect(bx - 8.5f, by - 3f, bx + 8.5f, by + 1f, fill)
            fill.color = 0xFF2A2228.toInt()
            c.drawRect(bx - 5f, by - 2.6f, bx + 5f, by - 0.4f, fill)
            if (snowCover > 0.1f) {
                fill.color = Color.argb((230 * snowCover).toInt(), 248, 250, 255)
                c.drawRect(bx - 8.5f, by - 5f, bx + 8.5f, by - 2.6f, fill)
            }
        }

        // 머리 위를 지나가는 철새 무리의 그림자
        drawFlockShadows(c, camXv, camYv)
    }

    private fun drawSnowOnTile(c: Canvas, x: Int, y: Int, sx: Float, sy: Float) {
        val tile = map.t(x, y)
        val a = (200 * snowCover).toInt()
        when (tile) {
            T.GRASS, T.TALLGRASS, T.FLOWER, T.REED, T.SAND -> {
                val h = hash2(x, y, 11)
                aa.color = Color.argb((a * 0.85f).toInt(), 246, 249, 255)
                val n = 2 + (h % 3)
                for (i in 0 until n) {
                    val hx = hash2(x, y, 20 + i)
                    val px = sx + 3f + (hx % 26)
                    val py = sy + 3f + ((hx / 26) % 26)
                    val rw = 3f + snowCover * 5f + (hx % 3)
                    rect.set(px - rw, py - rw * 0.55f, px + rw, py + rw * 0.55f)
                    c.drawOval(rect, aa)
                }
                if (snowCover > 0.6f) {
                    fill.color = Color.argb(((snowCover - 0.6f) * 150).toInt(), 244, 248, 255)
                    c.drawRect(sx, sy, sx + 32f, sy + 32f, fill)
                }
            }
            T.HOUSE_ROOF, T.BLDG_ROOF, T.LM_ROOF -> {
                fill.color = Color.argb(a, 248, 250, 255)
                c.drawRect(sx, sy, sx + 32f, sy + 6f + snowCover * 6f, fill)
            }
            T.TREE -> {
                aa.color = Color.argb(a, 248, 250, 255)
                rect.set(sx + 7f, sy + 2f, sx + 23f, sy + 8f)
                c.drawOval(rect, aa)
            }
            else -> {}
        }
    }

    private fun drawFlockShadows(c: Canvas, camXv: Float, camYv: Float) {
        if (flocks.isEmpty()) return
        val dayK = daylight(hour)
        val alpha = (48 * weather.shadowK.coerceAtLeast(0.4f) * (0.35f + 0.65f * dayK)).toInt()
        line.strokeWidth = 2.2f
        line.color = Color.argb(alpha, 20, 26, 34)
        for (f in flocks) {
            val dirX = if (f.vx > 0) 1f else -1f
            for (i in 0 until f.n) {
                // V자 대형: 선두 + 좌우로 뒤처지는 날개
                val rank = (i + 1) / 2
                val sideSign = if (i % 2 == 0) 1f else -1f
                val bx = f.x - dirX * rank * 11f
                val by = f.y + (if (i == 0) 0f else sideSign * rank * 8f)
                val flap = sin(time * 9f + f.phase + i * 0.7f) * 2.4f
                val sx = bx * WORLD_SCALE - camXv
                val sy = by * WORLD_SCALE - camYv
                path.reset()
                path.moveTo(sx - 6f, sy - 2f - flap)
                path.lineTo(sx, sy + 1.5f)
                path.lineTo(sx + 6f, sy - 2f - flap)
                c.drawPath(path, line)
            }
        }
    }

    // -------------------------------------------------------------------
    // 그리기 — 공중 (엔티티 이후)
    // -------------------------------------------------------------------

    fun drawAir(c: Canvas, camXv: Float, camYv: Float, vw: Int, vh: Int) {
        // 파티클 (연기/풀잎/물 튀김)
        for (p in parts) {
            val k = (p.life / p.max).coerceIn(0f, 1f)
            val sx = p.x * WORLD_SCALE - camXv
            val sy = p.y * WORLD_SCALE - camYv
            fill.color = Color.argb((Color.alpha(p.col) * k).toInt(), Color.red(p.col), Color.green(p.col), Color.blue(p.col))
            if (p.grow > 0f) {
                aa.color = fill.color
                c.drawCircle(sx, sy, p.size, aa)
            } else {
                c.drawRect(sx - p.size / 2, sy - p.size / 2, sx + p.size / 2, sy + p.size / 2, fill)
            }
        }

        // 나비 / 잠자리
        for (cr in critters) {
            val fade = cr.life.coerceIn(0f, 1f)
            val sx = cr.x * WORLD_SCALE - camXv
            val gy = cr.y * WORLD_SCALE - camYv
            val sy = gy - cr.alt * WORLD_SCALE
            // 땅 그림자
            aa.color = Color.argb((40 * fade).toInt(), 20, 30, 20)
            rect.set(sx - 3f, gy - 1f, sx + 3f, gy + 1f)
            c.drawOval(rect, aa)
            if (cr.kind == 0) {
                val flap = abs(sin(cr.t * 15f))
                val ww = 1.2f + flap * 3.6f
                val col = cr.col
                fill.color = Color.argb((255 * fade).toInt(), Color.red(col), Color.green(col), Color.blue(col))
                c.drawRect(sx - 0.8f - ww, sy - 3.4f, sx - 0.8f, sy + 0.2f, fill)       // 윗날개
                c.drawRect(sx + 0.8f, sy - 3.4f, sx + 0.8f + ww, sy + 0.2f, fill)
                c.drawRect(sx - 0.8f - ww * 0.7f, sy + 0.2f, sx - 0.8f, sy + 2.6f, fill) // 아랫날개
                c.drawRect(sx + 0.8f, sy + 0.2f, sx + 0.8f + ww * 0.7f, sy + 2.6f, fill)
                fill.color = Color.argb((255 * fade).toInt(), 60, 44, 40)
                c.drawRect(sx - 0.7f, sy - 3.2f, sx + 0.7f, sy + 2.8f, fill)             // 몸통
            } else {
                val flick = if (((cr.t * 30f).toInt() and 1) == 0) 1f else 0.6f
                fill.color = Color.argb((140 * fade * flick).toInt(), 235, 245, 255)
                c.drawRect(sx - 6f, sy - 2.2f, sx - 0.6f, sy - 0.8f, fill)
                c.drawRect(sx + 0.6f, sy - 2.2f, sx + 6f, sy - 0.8f, fill)
                c.drawRect(sx - 5f, sy + 0.2f, sx - 0.6f, sy + 1.4f, fill)
                c.drawRect(sx + 0.6f, sy + 0.2f, sx + 5f, sy + 1.4f, fill)
                val col = cr.col
                fill.color = Color.argb((255 * fade).toInt(), Color.red(col), Color.green(col), Color.blue(col))
                c.drawRect(sx - 0.8f, sy - 3.4f, sx + 0.8f, sy + 6f, fill)
                fill.color = Color.argb((255 * fade).toInt(), 30, 36, 40)
                c.drawRect(sx - 1.2f, sy - 4.2f, sx + 1.2f, sy - 2.6f, fill)
            }
        }

        // 가로등 불빛에 모여드는 나방 (밤)
        if (daylight(hour) < 0.35f) {
            val x0 = (camXv / 32f).toInt().coerceAtLeast(0)
            val y0 = (camYv / 32f).toInt().coerceAtLeast(0)
            val x1 = ((camXv + vw) / 32f).toInt().coerceAtMost(map.w - 1)
            val y1 = ((camYv + vh) / 32f).toInt().coerceAtMost(map.h - 1)
            fill.color = Color.argb(210, 238, 230, 210)
            for (y in y0..y1) for (x in x0..x1) {
                if (map.t(x, y) != T.LAMP) continue
                val lx = x * 32f - camXv + 16f
                val ly = y * 32f - camYv + 8f
                for (i in 0 until 3) {
                    val ph = time * (2.2f + i * 0.7f) + i * 2.1f + x
                    val r = 7f + i * 3f + sin(time * 3f + i) * 2f
                    val mx = lx + cos(ph) * r
                    val my = ly + sin(ph * 1.3f) * r * 0.6f
                    val wing = if (((time * 24f + i).toInt() and 1) == 0) 1.6f else 0.8f
                    c.drawRect(mx - wing, my - 0.8f, mx + wing, my + 0.8f, fill)
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // 그리기 — 날씨 (화면 공간, 조명 이전)
    // -------------------------------------------------------------------

    /**
     * 날씨 그리기 (화면 공간 — 카메라 변환 밖에 있어야 한다).
     * 크기는 setScreen()으로 등록된 화면 크기를 쓴다. 호출부가 월드 시야 크기를
     * 실수로 넘겨 비/눈 영역이 어긋나던 일을 구조적으로 막는다.
     */
    fun drawWeather(c: Canvas) {
        val w = scrW
        val h = scrH
        when (weather) {
            Weather.CLOUDY -> {
                fill.color = Color.argb(30, 70, 78, 96)
                c.drawRect(0f, 0f, w, h, fill)
            }
            Weather.RAIN -> {
                fill.color = Color.argb(58, 44, 54, 76)
                c.drawRect(0f, 0f, w, h, fill)
                line.strokeWidth = 1.3f
                line.color = Color.argb(120, 206, 220, 238)
                for (i in dropX.indices) {
                    val len = 7f + dropV[i] * 0.018f
                    c.drawLine(dropX[i], dropY[i], dropX[i] + len * 0.22f, dropY[i] + len, line)
                }
                line.strokeWidth = 1f
                for (i in splashT.indices) {
                    val k = splashT[i]
                    line.color = Color.argb((150 * (1f - k)).toInt(), 220, 232, 246)
                    val r = 1f + k * 5f
                    rect.set(splashX[i] - r, splashY[i] - r * 0.4f, splashX[i] + r, splashY[i] + r * 0.4f)
                    c.drawOval(rect, line)
                }
            }
            Weather.WIND -> {
                // 휙휙 지나가는 바람결 (가는 곡선 두 줄)
                line.strokeWidth = 1.4f
                for (i in 0 until 7) {
                    val span = w + 320f
                    val sx = ((time * (360f + i * 45f) + i * 213f) % span) - 160f
                    val sy = h * (0.08f + i * 0.13f) + sin(time * 1.3f + i * 1.7f) * 12f
                    val len = 46f + (i % 3) * 18f
                    line.color = Color.argb(70, 236, 242, 236)
                    c.drawLine(sx, sy, sx + len, sy - 3f, line)
                    c.drawLine(sx + len * 0.35f, sy + 6f, sx + len * 0.9f, sy + 4f, line)
                }
                line.strokeWidth = 1f
            }
            Weather.SNOW -> {
                fill.color = Color.argb(22, 210, 222, 240)
                c.drawRect(0f, 0f, w, h, fill)
                for (i in flakeX.indices) {
                    val tier = flakeTier(i)
                    fill.color = Color.argb(flakeAlpha(tier), 250, 252, 255)
                    val s = flakeSize(tier)
                    c.drawRect(flakeX[i], flakeY[i], flakeX[i] + s, flakeY[i] + s, fill)
                }
            }
            Weather.SUNNY -> {}
        }
    }

    /**
     * 반딧불 — 밤의 갈대/풀숲에서 깜빡인다.
     * lightPass=true 이면 조명 맵에 빛 구멍만 내고, false 이면 (조명 맵을 덮은 뒤) 빛 번짐과 점을 그린다.
     */
    fun fireflies(c: Canvas, lm: LightMap?, lightPass: Boolean, camXv: Float, camYv: Float, vw: Int, vh: Int) {
        if (daylight(hour) > 0.2f || weather == Weather.RAIN || weather == Weather.SNOW) return
        // 반딧불은 여름밤(습지), 봄밤(조금)에만
        if (season != Season.SUMMER && season != Season.SPRING) return
        val x0 = (camXv / 32f).toInt().coerceAtLeast(0)
        val y0 = (camYv / 32f).toInt().coerceAtLeast(0)
        val x1 = ((camXv + vw) / 32f).toInt().coerceAtMost(map.w - 1)
        val y1 = ((camYv + vh) / 32f).toInt().coerceAtMost(map.h - 1)
        var n = 0
        for (y in y0..y1) for (x in x0..x1) {
            val t = map.t(x, y)
            if (t != T.REED && t != T.TALLGRASS) continue
            val hsh = hash2(x, y, 17)
            if (hsh % 3 != 0) continue
            if (++n > 18) return
            val ph = (hsh % 628) / 100f
            val fx = x * 32f - camXv + 16f + sin(time * 0.7f + ph) * 14f
            val fy = y * 32f - camYv + 10f + cos(time * 0.9f + ph * 1.7f) * 9f
            val pulse = (sin(time * 2.4f + ph * 3f) * 0.5f + 0.5f)
            if (pulse < 0.15f) continue
            if (lightPass) {
                lm?.light(fx, fy, 14f, (170 * pulse).toInt())
            } else {
                Glow.draw(c, Glow.green, fx, fy, 9f, 9f, (200 * pulse).toInt())
                fill.color = Color.argb((255 * pulse).toInt(), 250, 255, 190)
                c.drawRect(fx - 1f, fy - 1f, fx + 1f, fy + 1f, fill)
            }
        }
    }

    companion object {
        private val NEI4 = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
    }
}
