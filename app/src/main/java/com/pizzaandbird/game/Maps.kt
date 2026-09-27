package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Random
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 타일
// ---------------------------------------------------------------------------

/** 맵 타일. solid = 걸을 수 없음 (표시 순서 = Assets.tiles 순서!) */
enum class T(val solid: Boolean) {
    GRASS(false),
    TALLGRASS(false),
    FLOWER(false),
    PATH(false),
    PLAZA(false),
    SAND(false),
    WATER(true),
    REED(false),
    TREE(true),
    ROCK(true),
    MOUNTAIN(true),
    BLDG_WALL(true),
    BLDG_WIN(true),
    BLDG_ROOF(true),
    HOUSE_ROOF(true),
    HOUSE_WALL(true),
    HOUSE_WIN(true),
    HOUSE_DOOR(false),      // 우리 집 현관 (들어가기)
    TUNNEL(false),          // 지역 이동 터널!
    FLOOR(false),
    WALL_IN(true),
    WALL_WIN(true),
    OVEN(true),             // 화덕
    BED(true),
    BOX(true),
    DECOR(false),           // 장식 슬롯
    SIGN(true),             // 터널 이정표
    BENCH(true),            // 벤치
    LAMP(true);             // 가로등 (밤에 빛남)

    companion object {
        val ALL = values()
    }
}

// ---------------------------------------------------------------------------
// 지도
// ---------------------------------------------------------------------------

class GameMap(
    val region: RegionDef,
    val w: Int,
    val h: Int,
    private val tiles: Array<IntArray>,
    val npcs: List<Npc>,
    val hasHouse: Boolean,
    val houseDoorX: Int,
    val houseDoorY: Int
) {
    fun t(x: Int, y: Int): T {
        if (x < 0 || y < 0 || x >= w || y >= h) return T.MOUNTAIN
        return T.ALL[tiles[y][x]]
    }

    fun solidTile(x: Int, y: Int): Boolean = t(x, y).solid

    /** 스프라이트 좌상단 기준 충돌 검사 (발판 박스) */
    fun solidBox(px: Float, py: Float): Boolean {
        val l = px + 3f
        val r = px + 13f
        val top = py + 10f
        val bot = py + 16f
        return solidTile((l / 16f).toInt(), (top / 16f).toInt()) ||
                solidTile((r / 16f).toInt(), (top / 16f).toInt()) ||
                solidTile((l / 16f).toInt(), (bot / 16f).toInt()) ||
                solidTile((r / 16f).toInt(), (bot / 16f).toInt())
    }

    fun walkableTile(x: Int, y: Int): Boolean = !t(x, y).solid && t(x, y) != T.TUNNEL

    /** 발(스프라이트 좌상단+13px)이 밟고 있는 타일 */
    fun feetTile(px: Float, py: Float): T = t(((px + 8f) / 16f).toInt(), ((py + 13f) / 16f).toInt())

    /**
     * 타일 렌더링 (32px 타일, 카메라는 가상 해상도 좌표).
     * 물/화덕은 애니메이션, 물가에는 거품이 인다.
     */
    fun draw(c: Canvas, a: Assets, camX: Float, camY: Float, vw: Int, vh: Int, time: Float) {
        val x0 = (camX / 32f).toInt().coerceAtLeast(0)
        val y0 = (camY / 32f).toInt().coerceAtLeast(0)
        val x1 = ((camX + vw) / 32f).toInt().coerceAtMost(w - 1)
        val y1 = ((camY + vh) / 32f).toInt().coerceAtMost(h - 1)
        val waterFrame = ((time * 2.2f).toInt() % 4 + 4) % 4
        val ovenFrame = ((time * 3.4f).toInt() % 2 + 2) % 2
        for (y in y0..y1) {
            for (x in x0..x1) {
                val tv = tiles[y][x]
                val tile = T.ALL[tv]
                val bmp = when (tile) {
                    T.WATER -> a.tiles[tv][minOf(waterFrame, a.tiles[tv].size - 1)]
                    T.OVEN -> a.tiles[tv][minOf(ovenFrame, a.tiles[tv].size - 1)]
                    else -> a.tiles[tv][a.tileVariant(tv, x, y)]
                }
                c.drawBitmap(bmp, x * 32f - camX, y * 32f - camY, a.sprPaint)

                // 물가 거품 (물 타일 가장자리)
                if (tile == T.WATER) {
                    val fx = x * 32f - camX
                    val fy = y * 32f - camY
                    if (y > 0 && t(x, y - 1) != T.WATER) c.drawRect(fx, fy, fx + 32f, fy + 3.2f, foamPaint)
                    if (y < h - 1 && t(x, y + 1) != T.WATER) c.drawRect(fx, fy + 28.8f, fx + 32f, fy + 32f, foamPaint)
                    if (x > 0 && t(x - 1, y) != T.WATER) c.drawRect(fx, fy, fx + 3.2f, fy + 32f, foamPaint)
                    if (x < w - 1 && t(x + 1, y) != T.WATER) c.drawRect(fx + 28.8f, fy, fx + 32f, fy + 32f, foamPaint)
                }
            }
        }
    }

    companion object {
        private val foamPaint = Paint().apply {
            color = Color.argb(150, 226, 244, 250)
        }
    }
}

// ---------------------------------------------------------------------------
// 지역 맵 빌더 (결정적 절차 생성 — 지역 id 시드)
// ---------------------------------------------------------------------------

object MapBuilder {

    /** 지역 월드맵 생성 */
    fun build(region: RegionDef, homeRegion: String): GameMap {
        val w = region.mapW
        val h = region.mapH
        val t = Array(h) { IntArray(w) { T.GRASS.ordinal } }
        val reserved = Array(h) { BooleanArray(w) }
        val rnd = Random(region.id.hashCode().toLong())
        val exits = Regions.exits(region.id)

        // 1. 바다 가장자리
        fun waterBand(d: Dir, depth: Int, tile: T) {
            when (d) {
                Dir.N -> for (y in 0 until depth) for (x in 0 until w) t[y][x] = tile.ordinal
                Dir.S -> for (y in h - depth until h) for (x in 0 until w) t[y][x] = tile.ordinal
                Dir.W -> for (x in 0 until depth) for (y in 0 until h) t[y][x] = tile.ordinal
                Dir.E -> for (x in w - depth until w) for (y in 0 until h) t[y][x] = tile.ordinal
            }
        }
        for (d in region.waterEdges) waterBand(d, 3, T.WATER)
        for (d in region.sandEdges) waterBand(d, 2, T.SAND)

        // 2. 육지 가장자리(산맥/수림)
        val borderTile = if (region.rockDensity >= 0.08) T.MOUNTAIN else T.TREE
        for (y in 0 until h) for (x in 0 until w) {
            val onEdge = x < 2 || y < 2 || x >= w - 2 || y >= h - 2
            if (onEdge && t[y][x] == T.GRASS.ordinal) t[y][x] = borderTile.ordinal
        }

        // 3. 십자 길 (남북: x=19,20 / 동서: y=15,16)
        fun openLand(x: Int, y: Int): Boolean {
            val e = T.ALL[t[y][x]]
            return e == T.GRASS || e == T.SAND || e == T.FLOWER || e == T.TALLGRASS ||
                    e == T.REED || e == T.PATH || e == T.PLAZA
        }
        for (y in 2 until h - 2) for (x in intArrayOf(19, 20)) {
            if (openLand(x, y)) { t[y][x] = T.PATH.ordinal; reserved[y][x] = true }
        }
        for (x in 2 until w - 2) for (y in intArrayOf(15, 16)) {
            if (openLand(x, y)) { t[y][x] = T.PATH.ordinal; reserved[y][x] = true }
        }

        // 4. 광장 (집 현관 앞까지 여유 있게 9칸 폭)
        for (y in 12..18) for (x in 17..25) {
            t[y][x] = T.PLAZA.ordinal
            reserved[y][x] = true
        }

        // 5. 터널 개통 (출구 방향 가장자리 중앙)
        for ((d, _) in exits) {
            when (d) {
                Dir.N -> {
                    for (x in 19..20) {
                        t[0][x] = T.TUNNEL.ordinal; t[1][x] = T.PATH.ordinal
                        reserved[0][x] = true; reserved[1][x] = true
                    }
                }
                Dir.S -> {
                    for (x in 19..20) {
                        t[h - 1][x] = T.TUNNEL.ordinal; t[h - 2][x] = T.PATH.ordinal; t[h - 3][x] = T.PATH.ordinal
                        reserved[h - 1][x] = true; reserved[h - 2][x] = true; reserved[h - 3][x] = true
                    }
                }
                Dir.W -> {
                    for (y in 15..16) {
                        t[y][0] = T.TUNNEL.ordinal; t[y][1] = T.PATH.ordinal; t[y][2] = T.PATH.ordinal
                        reserved[y][0] = true; reserved[y][1] = true; reserved[y][2] = true
                    }
                }
                Dir.E -> {
                    for (y in 15..16) {
                        t[y][w - 1] = T.TUNNEL.ordinal; t[y][w - 2] = T.PATH.ordinal; t[y][w - 3] = T.PATH.ordinal
                        reserved[y][w - 1] = true; reserved[y][w - 2] = true; reserved[y][w - 3] = true
                    }
                }
            }
        }

        // 6. 우리 집 (홈 지역에만 — 세로 도로 x=19,20을 피해 우측에 배치)
        var houseDoorX = -1
        var houseDoorY = -1
        val hasHouse = homeRegion == region.id
        if (hasHouse) {
            for (x in 21..25) {
                t[8][x] = T.HOUSE_ROOF.ordinal
                t[9][x] = T.HOUSE_ROOF.ordinal
            }
            t[10][21] = T.HOUSE_WALL.ordinal; t[10][22] = T.HOUSE_WIN.ordinal
            t[10][23] = T.HOUSE_WALL.ordinal; t[10][24] = T.HOUSE_WIN.ordinal; t[10][25] = T.HOUSE_WALL.ordinal
            t[11][21] = T.HOUSE_WALL.ordinal; t[11][22] = T.HOUSE_WALL.ordinal
            t[11][23] = T.HOUSE_DOOR.ordinal; t[11][24] = T.HOUSE_DOOR.ordinal
            t[11][25] = T.HOUSE_WALL.ordinal
            for (y in 8..12) for (x in 20..26) reserved[y][x] = true
            houseDoorX = 23
            houseDoorY = 11
        }

        // 7. NPC 배치 (광장)
        val npcs = listOf(
            Npc(NpcKind.PROFESSOR, 17, 13),
            Npc(NpcKind.SHOP, 23, 13),
            Npc(NpcKind.VILLAGER, 20, 18),
            Npc(NpcKind.KID, 18, 17),
            Npc(NpcKind.ELDER, 25, 15)
        )
        for (n in npcs) {
            reserved[n.tileY][n.tileX] = true
            if (n.tileY + 1 < h - 2) reserved[n.tileY + 1][n.tileX] = true
        }

        // 8. 도시 건물
        if (region.city) {
            for ((bx, by) in listOf(6 to 5, 31 to 5, 6 to 24, 31 to 24)) {
                for (y in by until by + 3) for (x in bx until bx + 3) {
                    if (x in 2 until w - 2 && y in 2 until h - 2 && !reserved[y][x] && t[y][x] == T.GRASS.ordinal) {
                        t[y][x] = when {
                            y == by -> T.BLDG_ROOF
                            (x + y) % 2 == 0 -> T.BLDG_WIN
                            else -> T.BLDG_WALL
                        }.ordinal
                        reserved[y][x] = true
                    }
                }
            }
        }

        // 9. 호수/습지
        if (region.lake) {
            val cx = 30; val cy = 22; val rx = 4; val ry = 3
            for (y in cy - ry - 1..cy + ry + 1) for (x in cx - rx - 1..cx + rx + 1) {
                if (x !in 2 until w - 2 || y !in 2 until h - 2) continue
                if (reserved[y][x]) continue
                val dx = (x - cx) / rx.toFloat()
                val dy = (y - cy) / ry.toFloat()
                val d = dx * dx + dy * dy
                if (d <= 1f) {
                    t[y][x] = T.WATER.ordinal; reserved[y][x] = true
                } else if (d <= 1.6f && t[y][x] == T.GRASS.ordinal) {
                    t[y][x] = T.REED.ordinal; reserved[y][x] = true
                }
            }
        }

        // 9.5 광장 시설 — 벤치 / 가로등(도시) / 터널 이정표
        for ((bx, by) in listOf(18 to 12, 24 to 18)) {
            t[by][bx] = T.BENCH.ordinal; reserved[by][bx] = true
        }
        if (region.city) {
            for ((lx, ly) in listOf(17 to 12, 25 to 12, 17 to 17, 25 to 17)) {
                t[ly][lx] = T.LAMP.ordinal; reserved[ly][lx] = true
            }
        }
        for ((d, _) in exits) {
            when (d) {
                Dir.N -> { t[1][22] = T.SIGN.ordinal; reserved[1][22] = true; reserved[1][21] = true; reserved[1][23] = true }
                Dir.S -> { t[h - 4][22] = T.SIGN.ordinal; reserved[h - 4][22] = true; reserved[h - 4][21] = true; reserved[h - 4][23] = true; reserved[h - 5][22] = true }
                Dir.W -> { t[13][2] = T.SIGN.ordinal; reserved[13][2] = true; reserved[13][3] = true; reserved[12][2] = true }
                Dir.E -> { t[13][w - 3] = T.SIGN.ordinal; reserved[13][w - 3] = true; reserved[13][w - 4] = true; reserved[12][w - 3] = true }
            }
        }

        // 9.7 나무 숲 (작은 그루브)
        repeat(3) {
            val gx = 5 + rnd.nextInt(w - 10)
            val gy = 5 + rnd.nextInt(h - 10)
            val gr = 2 + rnd.nextInt(2)
            for (y in gy - gr..gy + gr) for (x in gx - gr..gx + gr) {
                if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) continue
                if (reserved[y][x] || t[y][x] != T.GRASS.ordinal) continue
                val dx = x - gx; val dy = y - gy
                if (dx * dx + dy * dy <= gr * gr && rnd.nextFloat() < 0.8f) {
                    t[y][x] = T.TREE.ordinal
                    reserved[y][x] = true
                }
            }
        }

        // 10. 자연물 흩뿌리기
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            if (reserved[y][x] || t[y][x] != T.GRASS.ordinal) continue
            val r = rnd.nextDouble()
            when {
                r < region.treeDensity -> t[y][x] = T.TREE.ordinal
                r < region.treeDensity + region.flowerDensity -> t[y][x] = T.FLOWER.ordinal
                r < region.treeDensity + region.flowerDensity + region.rockDensity -> t[y][x] = T.ROCK.ordinal
            }
        }

        // 11. 풀숲 뭉치
        repeat(4) {
            val bx = 4 + rnd.nextInt(w - 8)
            val by = 4 + rnd.nextInt(h - 8)
            val br = 1 + rnd.nextInt(2)
            for (y in by - br..by + br) for (x in bx - br..bx + br) {
                if (x in 2 until w - 2 && y in 2 until h - 2 && !reserved[y][x] &&
                    t[y][x] == T.GRASS.ordinal && rnd.nextFloat() < 0.75f
                ) {
                    t[y][x] = T.TALLGRASS.ordinal
                }
            }
        }

        return GameMap(region, w, h, t, npcs, hasHouse, houseDoorX, houseDoorY)
    }

    /** 집 내부 맵 (13x9) */
    fun buildHome(): GameMap {
        val w = 13
        val h = 9
        val t = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        // 벽
        for (x in 0 until w) { t[0][x] = T.WALL_IN.ordinal; t[1][x] = T.WALL_IN.ordinal }
        for (y in 0 until h) { t[y][0] = T.WALL_IN.ordinal; t[y][w - 1] = T.WALL_IN.ordinal }
        for (y in 2 until h) t[y][w - 1] = T.WALL_IN.ordinal
        // 현관문 (아래쪽 중앙)
        for (x in 0 until w) t[h - 1][x] = T.WALL_IN.ordinal
        t[h - 1][6] = T.HOUSE_DOOR.ordinal
        // 창문
        t[1][2] = T.WALL_WIN.ordinal
        t[1][5] = T.WALL_WIN.ordinal
        t[1][8] = T.WALL_WIN.ordinal
        // 화덕 (기본 제공!)
        t[2][9] = T.OVEN.ordinal; t[2][10] = T.OVEN.ordinal
        t[3][9] = T.OVEN.ordinal; t[3][10] = T.OVEN.ordinal
        // 침대
        t[2][2] = T.BED.ordinal; t[2][3] = T.BED.ordinal
        // 이사 박스
        t[6][2] = T.BOX.ordinal
        // 장식 슬롯 (DECOR 0,1,2 순서로 HomeScene과 매칭)
        t[2][5] = T.DECOR.ordinal
        t[2][7] = T.DECOR.ordinal
        t[5][11] = T.DECOR.ordinal
        val home = Regions.byId["seoul"]!! // 내부맵은 지역 무관 (더미)
        return GameMap(home, w, h, t, emptyList(), true, 6, h - 1)
    }
}

// ---------------------------------------------------------------------------
// 엔티티
// ---------------------------------------------------------------------------

enum class NpcKind { PROFESSOR, SHOP, VILLAGER, KID, ELDER }

class Npc(val kind: NpcKind, val tileX: Int, val tileY: Int) {
    val x: Float get() = tileX * 16f
    val y: Float get() = tileY * 16f
    val cx: Float get() = x + 8f
    val cy: Float get() = y + 13f

    val name: String
        get() = when (kind) {
            NpcKind.PROFESSOR -> "보리 박사"
            NpcKind.SHOP -> "사진용품점"
            NpcKind.VILLAGER -> "동네 주민"
            NpcKind.KID -> "꼬마"
            NpcKind.ELDER -> "할머니"
        }
}

/** 골목을 거니는 고양이 */
class Cat(var x: Float, var y: Float) {
    var state = 0                 // 0 앉아있기, 1 걷기
    var idleT = 1.5f
    var fromX = 0f; var fromY = 0f
    var toX = 0f; var toY = 0f
    var hopT = 0f
    var faceLeft = true

    val cx: Float get() = x + 14f
    val cy: Float get() = y + 12f

    fun update(dt: Float, map: GameMap) {
        when (state) {
            0 -> {
                idleT -= dt
                if (idleT <= 0f) {
                    val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                    val (ddx, ddy) = dirs[(Math.random() * dirs.size).toInt()]
                    val nx = x + ddx * 16f
                    val ny = y + ddy * 16f
                    val tx = ((nx + 14f) / 16f).toInt()
                    val ty = ((ny + 14f) / 16f).toInt()
                    if (map.walkableTile(tx, ty)) {
                        fromX = x; fromY = y
                        toX = nx; toY = ny
                        hopT = 0f
                        state = 1
                        if (ddx != 0) faceLeft = ddx < 0
                    } else {
                        idleT = 1f
                    }
                }
            }
            1 -> {
                hopT += dt / 0.55f
                if (hopT >= 1f) {
                    x = toX; y = toY
                    state = 0
                    idleT = 1.5f + (Math.random() * 3f).toFloat()
                } else {
                    x = fromX + (toX - fromX) * hopT
                    y = fromY + (toY - fromY) * hopT
                }
            }
        }
    }

    val frame: Int get() = if (state == 1) 1 + ((hopT * 3f).toInt() % 2) else 0
    val lift: Float get() = if (state == 1) (sin((hopT * Math.PI).toFloat()) * 2f) else 0f
}

/** 플레이어 */
class Player {
    var x = 0f
    var y = 0f
    var facing = Dir.S
    var moving = false
    var bike = false
    var animT = 0f

    val cx: Float get() = x + 8f
    val cy: Float get() = y + 13f

    fun set(px: Float, py: Float) { x = px; y = py }
}

/** 필드에 나타난 새 */
class FieldBird(val def: BirdDef, var x: Float, var y: Float) {
    var state = 0                    // 0 대기, 1 깡충, 2 도망
    var idleT = 0.8f
    var hopFromX = 0f; var hopFromY = 0f
    var hopToX = 0f; var hopToY = 0f
    var hopT = 0f
    var fleeVx = 0f; var fleeVy = 0f
    var fleeT = 0f
    var faceLeft = true
    var sprW = 16                    // 스프라이트 크기 (생성 시 Assets에서 설정)
    var sprH = 15

    val cx: Float get() = x + sprW / 2f
    val cy: Float get() = y + sprH * 0.45f

    fun update(dt: Float, playerCx: Float, playerCy: Float, onBike: Boolean, sneaking: Boolean, map: GameMap, calmFactor: Float = 1f) {
        val fleeTiles = when (def.tier) {
            Tier.COMMON -> 1.7f
            Tier.UNCOMMON -> 2.3f
            Tier.RARE -> 3.0f
            Tier.LEGEND -> 3.8f
        } * (if (sneaking) 0.6f else 1f) * (if (onBike) 1.4f else 1f) * calmFactor

        when (state) {
            0 -> {
                val d = sqrt((playerCx - cx) * (playerCx - cx) + (playerCy - cy) * (playerCy - cy))
                if (d < fleeTiles * 16f) {
                    state = 2
                    val dx = if (cx - playerCx == 0f) 0.01f else cx - playerCx
                    val dy = if (cy - playerCy == 0f) -0.01f else cy - playerCy
                    val len = sqrt(dx * dx + dy * dy)
                    fleeVx = dx / len * 85f
                    fleeVy = dy / len * 85f - 35f
                    faceLeft = fleeVx < 0f
                    fleeT = 0f
                    return
                }
                idleT -= dt
                if (idleT <= 0f) {
                    // 무작위 방향으로 폴짝
                    val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                    val (ddx, ddy) = dirs[(Math.random() * dirs.size).toInt()]
                    val nx = x + ddx * 16f
                    val ny = y + ddy * 16f
                    val tx = ((nx + 7f) / 16f).toInt()
                    val ty = ((ny + 8f) / 16f).toInt()
                    val nearPlayer = sqrt((nx - playerCx) * (nx - playerCx) + (ny - playerCy) * (ny - playerCy)) < 40f
                    if (map.walkableTile(tx, ty) && !nearPlayer) {
                        hopFromX = x; hopFromY = y
                        hopToX = nx; hopToY = ny
                        hopT = 0f
                        state = 1
                        if (ddx != 0) faceLeft = ddx < 0
                    } else {
                        idleT = 0.6f
                    }
                }
            }
            1 -> {
                hopT += dt / 0.22f
                if (hopT >= 1f) {
                    x = hopToX; y = hopToY
                    state = 0
                    idleT = 0.7f + (Math.random() * 1.6f).toFloat()
                } else {
                    x = hopFromX + (hopToX - hopFromX) * hopT
                    y = hopFromY + (hopToY - hopFromY) * hopT
                }
            }
            2 -> {
                x += fleeVx * dt
                y += fleeVy * dt
                fleeT += dt
            }
        }
    }

    val gone: Boolean get() = state == 2 && fleeT > 1.5f

    /** 점프 중 살짝 들리는 높이 */
    val hopLift: Float
        get() = if (state == 1) (kotlin.math.sin((hopT * Math.PI).toFloat()) * 5f) else 0f
}
