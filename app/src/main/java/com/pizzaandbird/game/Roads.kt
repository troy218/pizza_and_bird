package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 길(도로) 아트 시스템 — v0.3
 *
 * 설계 요점
 * 1) **2레이어**: 지면(잔디/모래) 위에 "포장면"을 얹는다. 덕분에 길이 해변·물가·광장
 *    어디로 이어져도 자연스럽게 섞이고, 가로등·벤치·가로수가 길 위에 설 수 있다.
 * 2) **오토타일**: 이웃 8방향의 포장 여부를 비트마스크로 만들어 가장자리/모서리 모양을
 *    자동 생성한다. 직선·커브·삼거리·사거리·막다른 길이 전부 한 규칙에서 나온다.
 * 3) **캐시**: (재질, 마스크, 변형, 모래) 조합으로 만든 비트맵을 재사용하므로
 *    런타임 비용은 타일당 drawBitmap 1~2회뿐이다.
 * 4) **방향성 디테일**: 마스크로 길의 진행 방향을 알 수 있으므로 수레바퀴 자국을
 *    길을 따라 눕혀 그린다 (2칸 도로는 두 줄, 1칸 오솔길은 가운데 한 줄).
 */
object Pave {
    const val NONE = 0
    const val DIRT = 1      // 흙길 — 마을 간선도로 / 오솔길
    const val STONE = 2     // 석재 포장 — 광장 / 전망 데크
}

/** 32x32 ARGB 픽셀 버퍼 (Canvas 보다 픽셀 단위 마스킹에 적합) */
class PixBuf(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun set(x: Int, y: Int, col: Int) {
        if (x in 0 until w && y in 0 until h) px[y * w + x] = col
    }

    fun get(x: Int, y: Int): Int =
        if (x in 0 until w && y in 0 until h) px[y * w + x] else 0

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, col: Int) {
        var y = if (y0 < 0) 0 else y0
        val ye = if (y1 > h) h else y1
        while (y < ye) {
            var x = if (x0 < 0) 0 else x0
            val xe = if (x1 > w) w else x1
            while (x < xe) {
                px[y * w + x] = col
                x++
            }
            y++
        }
    }

    /** 이미 칠해진(불투명) 픽셀 위에만 알파 합성 — 실루엣 밖은 건드리지 않는다 */
    fun blend(x: Int, y: Int, col: Int, a: Float) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        val dst = px[y * w + x]
        if ((dst ushr 24) == 0) return
        val dr = dst shr 16 and 0xFF
        val dg = dst shr 8 and 0xFF
        val db = dst and 0xFF
        val r = (dr + ((col shr 16 and 0xFF) - dr) * a).toInt()
        val g = (dg + ((col shr 8 and 0xFF) - dg) * a).toInt()
        val b = (db + ((col and 0xFF) - db) * a).toInt()
        px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun toBitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

object RoadArt {

    // 이웃 비트 (포장면이면 1)
    const val BN = 1
    const val BE = 2
    const val BS = 4
    const val BW = 8
    const val BNE = 16
    const val BSE = 32
    const val BSW = 64
    const val BNW = 128

    const val VARIANTS = 3

    // -----------------------------------------------------------------------
    // 팔레트 — 잔디(#96D07A)·모래(#F2E1B0) 사이에서 또렷하게 읽히는 황토/화강암
    // -----------------------------------------------------------------------
    private val DIRT_BASE = 0xFFD6B983.toInt()
    private val DIRT_LIGHT = 0xFFE4CE9E.toInt()
    private val DIRT_PALE = 0xFFF0E2BE.toInt()
    private val DIRT_DARK = 0xFFBE9F6C.toInt()
    private val DIRT_DEEP = 0xFFA58555.toInt()
    private val DIRT_RIM = 0xFF93764B.toInt()
    private val DIRT_PEBBLE = 0xFFC6C0AC.toInt()
    private val DIRT_PEBBLE_HI = 0xFFE2DCC8.toInt()

    private val STONE_BASE = 0xFFCFC5AC.toInt()
    private val STONE_LIGHT = 0xFFDED5BE.toInt()
    private val STONE_PALE = 0xFFEAE2CE.toInt()
    private val STONE_DARK = 0xFFB9AE93.toInt()
    private val STONE_DEEP = 0xFFA1957A.toInt()
    private val STONE_MORTAR = 0xFFA79B7E.toInt()
    private val STONE_RIM = 0xFF8E8268.toInt()
    private val STONE_MOSS = 0xFF9FB081.toInt()
    private val CURB_TOP = 0xFFE3DAC4.toInt()
    private val CURB_SIDE = 0xFFB3A88D.toInt()

    private val GRASS_TUFT = 0xFF7FBE64.toInt()
    private val GRASS_TUFT2 = 0xFF6FAE57.toInt()
    private val SAND_TUFT = 0xFFE9D6A4.toInt()

    private fun mix(c0: Int, c1: Int, t: Float): Int {
        val r = ((c0 shr 16 and 0xFF) + ((c1 shr 16 and 0xFF) - (c0 shr 16 and 0xFF)) * t).toInt()
        val g = ((c0 shr 8 and 0xFF) + ((c1 shr 8 and 0xFF) - (c0 shr 8 and 0xFF)) * t).toInt()
        val b = ((c0 and 0xFF) + ((c1 and 0xFF) - (c0 and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 같은 입력이면 항상 같은 그림 (지역/세션과 무관하게 결정적) */
    private fun seedOf(mat: Int, mask: Int, variant: Int, sandy: Boolean): Long =
        (mat * 7717L + mask * 131L + variant * 29L + if (sandy) 1013L else 0L)

    // -----------------------------------------------------------------------
    // 1. 실루엣 (오토타일)
    // -----------------------------------------------------------------------

    /**
     * 가장자리 안쪽 여백 프로파일.
     * 타일 경계(첫/끝 2픽셀)는 기준값으로 고정 → 옆 타일과 윤곽선이 정확히 이어진다.
     */
    private fun profile(r: Random, mat: Int, n: Int): IntArray {
        val base = if (mat == Pave.STONE) 1 else 2
        val out = IntArray(n) { base }
        if (mat == Pave.STONE) return out
        var v = base
        for (i in 2 until n - 2) {
            val remain = (n - 3) - i
            if (remain <= kotlin.math.abs(v - base)) {
                v += if (v < base) 1 else -1
            } else if (r.nextInt(3) == 0) {
                v += if (r.nextBoolean()) 1 else -1
                if (v < base - 1) v = base - 1
                if (v > base + 1) v = base + 1
            }
            out[i] = v
        }
        return out
    }

    private fun silhouette(mat: Int, mask: Int, r: Random): Array<BooleanArray> {
        val n = mask and BN != 0
        val e = mask and BE != 0
        val s = mask and BS != 0
        val w = mask and BW != 0
        val ne = mask and BNE != 0
        val se = mask and BSE != 0
        val sw = mask and BSW != 0
        val nw = mask and BNW != 0

        val pN = profile(r, mat, 32)
        val pS = profile(r, mat, 32)
        val pW = profile(r, mat, 32)
        val pE = profile(r, mat, 32)

        val outer = if (mat == Pave.DIRT) 5.5f else 3.5f     // 바깥 모서리 둥글기
        val inner = if (mat == Pave.DIRT) 5.0f else 3.0f     // 안쪽 모서리 필렛

        val sol = Array(32) { BooleanArray(32) }
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                var ok = true
                if (!n && y < pN[x]) ok = false
                if (!s && (31 - y) < pS[x]) ok = false
                if (!w && x < pW[y]) ok = false
                if (!e && (31 - x) < pE[y]) ok = false
                if (ok) {
                    val fx = x.toFloat()
                    val fy = y.toFloat()
                    val rx = (31 - x).toFloat()
                    val ry = (31 - y).toFloat()
                    if (!n && !w && fx < outer && fy < outer &&
                        (fx - outer) * (fx - outer) + (fy - outer) * (fy - outer) > outer * outer
                    ) ok = false
                    if (!n && !e && rx < outer && fy < outer &&
                        (rx - outer) * (rx - outer) + (fy - outer) * (fy - outer) > outer * outer
                    ) ok = false
                    if (!s && !w && fx < outer && ry < outer &&
                        (fx - outer) * (fx - outer) + (ry - outer) * (ry - outer) > outer * outer
                    ) ok = false
                    if (!s && !e && rx < outer && ry < outer &&
                        (rx - outer) * (rx - outer) + (ry - outer) * (ry - outer) > outer * outer
                    ) ok = false
                    if (n && w && !nw && fx * fx + fy * fy < inner * inner) ok = false
                    if (n && e && !ne && rx * rx + fy * fy < inner * inner) ok = false
                    if (s && w && !sw && fx * fx + ry * ry < inner * inner) ok = false
                    if (s && e && !se && rx * rx + ry * ry < inner * inner) ok = false
                }
                sol[y][x] = ok
            }
        }
        return sol
    }

    /** 각 픽셀에서 포장면 경계까지의 거리 (0 = 가장 바깥 픽셀, 4 이상은 안쪽) */
    private fun edgeDistance(sol: Array<BooleanArray>): Array<IntArray> {
        val inf = 99
        val d = Array(32) { IntArray(32) { inf } }
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                if (!sol[y][x]) continue
                var border = false
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx > 31 || ny > 31) continue
                    if (!sol[ny][nx]) border = true
                }
                if (border) d[y][x] = 0
            }
        }
        for (step in 1..4) {
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    if (!sol[y][x] || d[y][x] < inf) continue
                    var near = false
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0..31 && ny in 0..31 && d[ny][nx] == step - 1) near = true
                    }
                    if (near) d[y][x] = step
                }
            }
        }
        return d
    }

    // -----------------------------------------------------------------------
    // 2. 표면 — 흙길
    // -----------------------------------------------------------------------

    private fun paintDirt(buf: PixBuf, sol: Array<BooleanArray>, r: Random, mask: Int) {
        val n = mask and BN != 0
        val e = mask and BE != 0
        val s = mask and BS != 0
        val w = mask and BW != 0

        for (y in 0 until 32) for (x in 0 until 32) if (sol[y][x]) buf.set(x, y, DIRT_BASE)

        // 다짐 정도가 다른 넓은 얼룩
        repeat(9) {
            val cx = r.nextInt(32)
            val cy = r.nextInt(32)
            val rad = 3 + r.nextInt(5)
            val col = if (r.nextBoolean()) DIRT_LIGHT else DIRT_DARK
            for (y in cy - rad..cy + rad) {
                for (x in cx - rad..cx + rad) {
                    if (x < 0 || y < 0 || x > 31 || y > 31 || !sol[y][x]) continue
                    val dx = (x - cx).toFloat()
                    val dy = (y - cy) * 1.35f
                    if (dx * dx + dy * dy <= (rad * rad).toFloat()) buf.blend(x, y, col, 0.45f)
                }
            }
        }

        // 수레바퀴 자국 — 길이 흐르는 방향으로
        val vertical = n && s && !(e && w)
        val horizontal = e && w && !(n && s)
        if (vertical) {
            val single = !e && !w
            val cx = when {
                e && !w -> 21
                w && !e -> 10
                else -> 16
            }
            rutVertical(buf, sol, r, cx, single)
        } else if (horizontal) {
            val single = !n && !s
            val cy = when {
                s && !n -> 21
                n && !s -> 10
                else -> 16
            }
            rutHorizontal(buf, sol, r, cy, single)
        }

        // 자갈·모래알
        repeat(26) {
            val x = r.nextInt(32)
            val y = r.nextInt(32)
            if (sol[y][x]) buf.blend(x, y, if (r.nextBoolean()) DIRT_PALE else DIRT_DARK, 0.55f)
        }
        // 박힌 돌멩이
        repeat(3) {
            val x = 2 + r.nextInt(27)
            val y = 2 + r.nextInt(27)
            if (!sol[y][x]) return@repeat
            buf.set(x, y, DIRT_PEBBLE)
            if (x + 1 < 32 && sol[y][x + 1]) buf.set(x + 1, y, DIRT_PEBBLE_HI)
            if (y + 1 < 32 && sol[y + 1][x]) buf.set(x, y + 1, DIRT_DEEP)
        }
    }

    private fun rutVertical(buf: PixBuf, sol: Array<BooleanArray>, r: Random, cxBase: Int, single: Boolean) {
        val half = if (single) 1 else 2
        val core = if (single) DIRT_DARK else DIRT_DEEP
        val strength = if (single) 0.30f else 0.55f
        var off = 0
        for (y in 0 until 32) {
            if (r.nextInt(5) == 0) {
                off += if (r.nextBoolean()) 1 else -1
                if (off < -1) off = -1
                if (off > 1) off = 1
            }
            val cx = cxBase + off
            for (x in cx - half..cx + half) {
                if (x in 0..31 && sol[y][x]) {
                    val edge = kotlin.math.abs(x - cx) == half
                    buf.blend(x, y, core, strength * (if (edge) 0.45f else 1f))
                }
            }
            val hx = cx + half + 1
            if (!single && hx in 0..31 && sol[y][hx]) buf.blend(hx, y, DIRT_LIGHT, 0.35f)
        }
    }

    private fun rutHorizontal(buf: PixBuf, sol: Array<BooleanArray>, r: Random, cyBase: Int, single: Boolean) {
        val half = if (single) 1 else 2
        val core = if (single) DIRT_DARK else DIRT_DEEP
        val strength = if (single) 0.30f else 0.55f
        var off = 0
        for (x in 0 until 32) {
            if (r.nextInt(5) == 0) {
                off += if (r.nextBoolean()) 1 else -1
                if (off < -1) off = -1
                if (off > 1) off = 1
            }
            val cy = cyBase + off
            for (y in cy - half..cy + half) {
                if (y in 0..31 && sol[y][x]) {
                    val edge = kotlin.math.abs(y - cy) == half
                    buf.blend(x, y, core, strength * (if (edge) 0.45f else 1f))
                }
            }
            val hy = cy + half + 1
            if (!single && hy in 0..31 && sol[hy][x]) buf.blend(x, hy, DIRT_LIGHT, 0.35f)
        }
    }

    // -----------------------------------------------------------------------
    // 3. 표면 — 석재 포장 (러닝본드 판석)
    // -----------------------------------------------------------------------

    private fun paintStone(buf: PixBuf, sol: Array<BooleanArray>, r: Random, variant: Int) {
        for (y in 0 until 32) for (x in 0 until 32) if (sol[y][x]) buf.set(x, y, STONE_MORTAR)

        val rowBase = variant * 4
        for (row in 0 until 4) {
            val y0 = row * 8
            val grow = rowBase + row
            val offset = if (Math.floorMod(grow, 2) != 0) 8 else 0
            var bx = -offset
            var colI = 0
            while (bx < 32) {
                val x0 = bx
                val x1 = bx + 16
                val tone = Math.floorMod(grow * 5 + colI * 3 + Math.floorDiv(x0, 16), 4)
                var base = when (tone) {
                    0 -> STONE_BASE
                    1 -> STONE_LIGHT
                    2 -> mix(STONE_BASE, STONE_DARK, 0.35f)
                    else -> mix(STONE_LIGHT, STONE_PALE, 0.5f)
                }
                if (Math.floorMod(grow * 7 + colI * 11, 23) == 0) base = mix(base, STONE_MOSS, 0.16f)
                for (y in y0 + 1 until y0 + 8) {
                    for (x in x0 + 1 until x1) {
                        if (x in 0..31 && y in 0..31 && sol[y][x]) buf.set(x, y, base)
                    }
                }
                for (x in x0 + 1 until x1) {
                    if (x in 0..31 && y0 + 1 <= 31 && sol[y0 + 1][x]) buf.blend(x, y0 + 1, STONE_PALE, 0.40f)
                    if (x in 0..31 && y0 + 7 <= 31 && sol[y0 + 7][x]) buf.blend(x, y0 + 7, STONE_DEEP, 0.30f)
                }
                bx += 16
                colI++
            }
        }

        // 풍화 — 잔금과 얼룩
        repeat(3) {
            val x = 1 + r.nextInt(29)
            val y = 1 + r.nextInt(29)
            val len = 2 + r.nextInt(4)
            for (k in 0 until len) {
                val xx = x + k
                val yy = y + (k / 2)
                if (xx in 0..31 && yy in 0..31 && sol[yy][xx]) buf.blend(xx, yy, STONE_DEEP, 0.30f)
            }
        }
        repeat(18) {
            val x = r.nextInt(32)
            val y = r.nextInt(32)
            if (sol[y][x]) buf.blend(x, y, if (r.nextBoolean()) STONE_PALE else STONE_DARK, 0.30f)
        }
    }

    // -----------------------------------------------------------------------
    // 4. 가장자리 마감 (연석 / 길섶)
    // -----------------------------------------------------------------------

    private fun paintEdges(buf: PixBuf, sol: Array<BooleanArray>, r: Random, mat: Int, sandy: Boolean) {
        val d = edgeDistance(sol)
        val rim = if (mat == Pave.DIRT) DIRT_RIM else STONE_RIM
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                if (!sol[y][x]) continue
                val dd = d[y][x]
                if (mat == Pave.STONE) {
                    when (dd) {
                        0 -> buf.set(x, y, CURB_TOP)
                        1 -> buf.set(x, y, CURB_SIDE)
                        2 -> buf.blend(x, y, STONE_DEEP, 0.35f)
                    }
                } else {
                    when (dd) {
                        0 -> buf.blend(x, y, rim, 0.62f)
                        1 -> buf.blend(x, y, rim, 0.30f)
                        2 -> buf.blend(x, y, rim, 0.12f)
                    }
                }
            }
        }
        if (mat != Pave.DIRT) return

        // 길섶 — 풀이 살짝 덮어온 자리와 밀려난 잔자갈
        val tuft = if (sandy) SAND_TUFT else GRASS_TUFT
        val tuft2 = if (sandy) SAND_TUFT else GRASS_TUFT2
        repeat(7) {
            val x = r.nextInt(32)
            val y = r.nextInt(32)
            if (!sol[y][x] || d[y][x] > 1) return@repeat
            buf.set(x, y, tuft)
            if (r.nextBoolean() && y + 1 < 32 && sol[y + 1][x]) buf.set(x, y + 1, tuft2)
        }
        repeat(6) {
            val x = r.nextInt(32)
            val y = r.nextInt(32)
            if (sol[y][x] && d[y][x] == 2) buf.blend(x, y, DIRT_PALE, 0.7f)
        }
    }

    /** 포장 타일 한 장 (바깥은 투명 — 지면 레이어가 비친다) */
    fun tile(mat: Int, mask: Int, variant: Int, sandy: Boolean): Bitmap {
        val r = Random(seedOf(mat, mask, variant, sandy))
        val sol = silhouette(mat, mask, r)
        val buf = PixBuf(32, 32)
        if (mat == Pave.STONE) paintStone(buf, sol, r, variant) else paintDirt(buf, sol, r, mask)
        paintEdges(buf, sol, r, mat, sandy)
        return buf.toBitmap()
    }

    // -----------------------------------------------------------------------
    // 5. 데칼 — 광장 문양 / 빗물받이
    // -----------------------------------------------------------------------

    /** 광장 한가운데 n x n 문양 (팔방 나침반). 반환 순서: 왼쪽 위 -> 오른쪽 아래 */
    fun medallion(n: Int): Array<Bitmap> {
        val size = n * 32
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        val cx = size / 2f
        val cy = size / 2f
        val k = size / 64f

        p.color = STONE_RIM
        cv.drawCircle(cx, cy, 28f * k, p)
        p.color = CURB_TOP
        cv.drawCircle(cx, cy, 26.5f * k, p)
        p.color = STONE_RIM
        cv.drawCircle(cx, cy, 23.5f * k, p)
        p.color = mix(STONE_LIGHT, STONE_PALE, 0.5f)
        cv.drawCircle(cx, cy, 22f * k, p)

        val path = Path()
        for (i in 0 until 8) {
            val long = i % 2 == 0
            val ang = i * Math.PI.toFloat() / 4f
            val length = (if (long) 21f else 13f) * k
            val halfw = (if (long) 4.2f else 3.0f) * k
            val ca = cos(ang)
            val sa = sin(ang)
            path.reset()
            path.moveTo(cx + ca * length, cy + sa * length)
            path.lineTo(cx - sa * halfw, cy + ca * halfw)
            path.lineTo(cx + sa * halfw, cy - ca * halfw)
            path.close()
            val light = if (long) (i / 2) % 2 == 0 else (i / 2) % 2 == 1
            p.color = if (light) STONE_DARK else STONE_DEEP
            cv.drawPath(path, p)
        }

        p.color = STONE_RIM
        cv.drawCircle(cx, cy, 5.2f * k, p)
        p.color = CURB_TOP
        cv.drawCircle(cx, cy, 3.6f * k, p)

        return Array(n * n) { idx ->
            Bitmap.createBitmap(bmp, (idx % n) * 32, (idx / n) * 32, 32, 32)
        }
    }

    /** 광장 빗물받이 */
    fun drain(): Bitmap {
        val buf = PixBuf(32, 32)
        buf.rect(9, 11, 23, 21, 0xFF6E6A5E.toInt())
        buf.rect(10, 12, 22, 20, 0xFF4A4740.toInt())
        for (i in 0 until 4) buf.rect(11 + i * 3, 13, 13 + i * 3, 19, 0xFF8A8579.toInt())
        buf.rect(9, 11, 23, 12, 0xFF8F8A7C.toInt())
        buf.rect(9, 20, 23, 21, 0xFF35332E.toInt())
        return buf.toBitmap()
    }

    // -----------------------------------------------------------------------
    // 6. 접지 그림자 — 빛은 왼쪽 위에서 온다
    // -----------------------------------------------------------------------

    /** [윗변(위에 구조물), 왼쪽변(왼쪽에 구조물), 왼쪽 위 모서리] */
    fun castShadows(): Array<Bitmap> {
        val depth = 8f

        fun make(kind: Int): Bitmap {
            val px = IntArray(32 * 32)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val t = when (kind) {
                        0 -> y.toFloat()
                        1 -> x.toFloat()
                        else -> sqrt((x * x + y * y).toFloat())
                    }
                    if (t < depth) {
                        val peak = if (kind == 2) 72f else 80f
                        val f = Math.pow((1f - t / depth).toDouble(), 1.8).toFloat()
                        val a = (f * peak).toInt().coerceIn(0, 255)
                        px[y * 32 + x] = (a shl 24) or (16 shl 16) or (24 shl 8) or 18
                    }
                }
            }
            return Bitmap.createBitmap(px, 32, 32, Bitmap.Config.ARGB_8888)
        }
        return arrayOf(make(0), make(1), make(2))
    }
}
