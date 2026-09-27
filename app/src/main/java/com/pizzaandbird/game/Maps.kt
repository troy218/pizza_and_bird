package com.pizzaandbird.game

import android.graphics.Canvas
import java.util.Random
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 타일
// ---------------------------------------------------------------------------

/** 맵 타일. solid = 걸을 수 없음 */
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
    DECOR(false),           // 장식 슬롯 (추후 소품)
    RUG(false),
    SHELF(true),
    PLANT(true),
    CABINET(true);

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
    private val shorePaint = Paint().apply { color = 0x66D9F5F2 }

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

    fun draw(c: Canvas, a: Assets, camX: Float, camY: Float, vw: Int, vh: Int, time: Float) {
        val x0 = (camX / 16f).toInt().coerceAtLeast(0)
        val y0 = (camY / 16f).toInt().coerceAtLeast(0)
        val x1 = ((camX + vw) / 16f).toInt().coerceAtMost(w - 1)
        val y1 = ((camY + vh) / 16f).toInt().coerceAtMost(h - 1)
        val waterTick = (time * 1.8f).toInt()
        for (y in y0..y1) {
            for (x in x0..x1) {
                val tv = tiles[y][x]
                val variants = a.tiles[tv]
                val variant = when (T.ALL[tv]) {
                    // Neighboring water tiles ripple out of phase instead of flashing in unison.
                    T.WATER -> ((waterTick + x * 3 + y * 5) % variants.size + variants.size) % variants.size
                    else -> a.tileVariant(tv, x, y)
                }
                val px = x * 16f - camX
                val py = y * 16f - camY
                c.drawBitmap(variants[variant], px, py, a.sprPaint)
                if (T.ALL[tv] == T.WATER) {
                    // Thin, broken-looking highlights make shorelines legible without a hard contour.
                    if (y > 0 && tiles[y - 1][x] != T.WATER.ordinal) c.drawRect(px, py, px + 16f, py + 1f, shorePaint)
                    if (x > 0 && tiles[y][x - 1] != T.WATER.ordinal) c.drawRect(px, py, px + 1f, py + 16f, shorePaint)
                    if (y < h - 1 && tiles[y + 1][x] != T.WATER.ordinal) c.drawRect(px, py + 15f, px + 16f, py + 16f, shorePaint)
                    if (x < w - 1 && tiles[y][x + 1] != T.WATER.ordinal) c.drawRect(px + 15f, py, px + 16f, py + 16f, shorePaint)
                }
            }
        }
    }

    /** Invoke for every visible tree; canopies are composited at their proper depth. */
    fun forEachVisibleTree(camX: Float, camY: Float, vw: Int, vh: Int, action: (Int, Int) -> Unit) {
        val x0 = (camX / 16f).toInt().coerceAtLeast(0)
        val y0 = (camY / 16f).toInt().coerceAtLeast(0)
        val x1 = ((camX + vw) / 16f).toInt().coerceAtMost(w - 1)
        val y1 = ((camY + vh) / 16f).toInt().coerceAtMost(h - 1)
        for (y in y0..y1) for (x in x0..x1) {
            if (tiles[y][x] == T.TREE.ordinal) action(x, y)
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
        val centerX = w / 2
        val centerY = h / 2
        val crossX = intArrayOf(centerX - 1, centerX)
        val crossY = intArrayOf(centerY - 1, centerY)
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

        // 3. 십자 길: 길과 광장은 맵 크기에 맞춰 항상 중앙에 놓는다.
        fun openLand(x: Int, y: Int): Boolean {
            val e = T.ALL[t[y][x]]
            return e == T.GRASS || e == T.SAND || e == T.FLOWER || e == T.TALLGRASS ||
                    e == T.REED || e == T.PATH || e == T.PLAZA
        }
        for (y in 2 until h - 2) for (x in crossX) {
            if (openLand(x, y)) { t[y][x] = T.PATH.ordinal; reserved[y][x] = true }
        }
        for (x in 2 until w - 2) for (y in crossY) {
            if (openLand(x, y)) { t[y][x] = T.PATH.ordinal; reserved[y][x] = true }
        }

        // 4. 널찍한 중앙 광장 (9×7 타일)
        for (y in centerY - 3..centerY + 3) for (x in centerX - 3..centerX + 5) {
            t[y][x] = T.PLAZA.ordinal
            reserved[y][x] = true
        }

        // 5. 터널 개통 (출구 방향 가장자리 중앙)
        for ((d, _) in exits) {
            when (d) {
                Dir.N -> {
                    for (x in crossX) {
                        t[0][x] = T.TUNNEL.ordinal; t[1][x] = T.PATH.ordinal
                        reserved[0][x] = true; reserved[1][x] = true
                    }
                }
                Dir.S -> {
                    for (x in crossX) {
                        t[h - 1][x] = T.TUNNEL.ordinal; t[h - 2][x] = T.PATH.ordinal; t[h - 3][x] = T.PATH.ordinal
                        reserved[h - 1][x] = true; reserved[h - 2][x] = true; reserved[h - 3][x] = true
                    }
                }
                Dir.W -> {
                    for (y in crossY) {
                        t[y][0] = T.TUNNEL.ordinal; t[y][1] = T.PATH.ordinal; t[y][2] = T.PATH.ordinal
                        reserved[y][0] = true; reserved[y][1] = true; reserved[y][2] = true
                    }
                }
                Dir.E -> {
                    for (y in crossY) {
                        t[y][w - 1] = T.TUNNEL.ordinal; t[y][w - 2] = T.PATH.ordinal; t[y][w - 3] = T.PATH.ordinal
                        reserved[y][w - 1] = true; reserved[y][w - 2] = true; reserved[y][w - 3] = true
                    }
                }
            }
        }

        // 6. 우리 집 (중앙 광장 오른쪽 가장자리)
        var houseDoorX = -1
        var houseDoorY = -1
        val hasHouse = homeRegion == region.id
        if (hasHouse) {
            for (x in centerX + 1..centerX + 5) {
                t[centerY - 7][x] = T.HOUSE_ROOF.ordinal
                t[centerY - 6][x] = T.HOUSE_ROOF.ordinal
            }
            t[centerY - 5][centerX + 1] = T.HOUSE_WALL.ordinal
            t[centerY - 5][centerX + 2] = T.HOUSE_WIN.ordinal
            t[centerY - 5][centerX + 3] = T.HOUSE_WALL.ordinal
            t[centerY - 5][centerX + 4] = T.HOUSE_WIN.ordinal
            t[centerY - 5][centerX + 5] = T.HOUSE_WALL.ordinal
            t[centerY - 4][centerX + 1] = T.HOUSE_WALL.ordinal
            t[centerY - 4][centerX + 2] = T.HOUSE_WALL.ordinal
            t[centerY - 4][centerX + 3] = T.HOUSE_DOOR.ordinal
            t[centerY - 4][centerX + 4] = T.HOUSE_DOOR.ordinal
            t[centerY - 4][centerX + 5] = T.HOUSE_WALL.ordinal
            for (y in centerY - 7..centerY - 3) for (x in centerX..centerX + 6) reserved[y][x] = true
            houseDoorX = centerX + 3
            houseDoorY = centerY - 4
        }

        // 7. NPC 배치 (중앙 광장)
        val npcs = listOf(
            Npc(NpcKind.PROFESSOR, centerX - 3, centerY - 2),
            Npc(NpcKind.SHOP, centerX + 3, centerY - 2),
            Npc(NpcKind.VILLAGER, centerX, centerY + 3)
        )
        for (n in npcs) {
            reserved[n.tileY][n.tileX] = true
            if (n.tileY + 1 < h - 2) reserved[n.tileY + 1][n.tileX] = true
        }

        // 8. 도시 건물 — 외곽에 작은 블록 단위로 모아 마을 실루엣을 만든다.
        if (region.city) {
            for (by in 5 until h - 5 step 10) for (bx in 5 until w - 5 step 12) {
                if (kotlin.math.abs(bx - centerX) < 10 && kotlin.math.abs(by - centerY) < 8) continue
                if (rnd.nextFloat() > 0.72f) continue
                val bw = 2 + rnd.nextInt(2)
                val bh = 3
                val inside = bx + bw < w - 2 && by + bh < h - 2
                if (!inside) continue
                val clear = (by until by + bh).all { y ->
                    (bx until bx + bw).all { x -> !reserved[y][x] && t[y][x] == T.GRASS.ordinal }
                }
                if (!clear) continue
                for (y in by until by + bh) for (x in bx until bx + bw) {
                    t[y][x] = when {
                        y == by -> T.BLDG_ROOF
                        (x + y) % 2 == 0 -> T.BLDG_WIN
                        else -> T.BLDG_WALL
                    }.ordinal
                    reserved[y][x] = true
                }
            }
        }

        // 9. 호수/습지
        if (region.lake) {
            val cx = w - 10; val cy = h - 8; val rx = 4; val ry = 3
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

        // 10. 자연물은 낱개 확률 대신 작은 군락으로 배치해 넓은 맵에도 자연스러운 리듬을 만든다.
        val treePatches = (w * h * region.treeDensity / 20.0).toInt().coerceAtLeast(1)
        repeat(treePatches) {
            val bx = 4 + rnd.nextInt(w - 8)
            val by = 4 + rnd.nextInt(h - 8)
            val rx = 2 + rnd.nextInt(4)
            val ry = 2 + rnd.nextInt(3)
            for (y in by - ry..by + ry) for (x in bx - rx..bx + rx) {
                if (x !in 2 until w - 2 || y !in 2 until h - 2 || reserved[y][x] ||
                    t[y][x] != T.GRASS.ordinal
                ) continue
                val nx = (x - bx) / rx.toFloat()
                val ny = (y - by) / ry.toFloat()
                if (nx * nx + ny * ny <= 1f && rnd.nextFloat() < 0.68f) t[y][x] = T.TREE.ordinal
            }
        }

        val flowerPatches = (w * h * region.flowerDensity / 8.0).toInt().coerceAtLeast(1)
        repeat(flowerPatches) {
            val bx = 3 + rnd.nextInt(w - 6)
            val by = 3 + rnd.nextInt(h - 6)
            val rx = 1 + rnd.nextInt(3)
            val ry = 1 + rnd.nextInt(2)
            for (y in by - ry..by + ry) for (x in bx - rx..bx + rx) {
                if (x !in 2 until w - 2 || y !in 2 until h - 2 || reserved[y][x] ||
                    t[y][x] != T.GRASS.ordinal
                ) continue
                val nx = (x - bx) / rx.toFloat()
                val ny = (y - by) / ry.toFloat()
                if (nx * nx + ny * ny <= 1f && rnd.nextFloat() < 0.72f) t[y][x] = T.FLOWER.ordinal
            }
        }

        // Rock scatter and meadow tufts provide a smaller contrasting texture.
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            if (reserved[y][x] || t[y][x] != T.GRASS.ordinal) continue
            if (rnd.nextDouble() < region.rockDensity) t[y][x] = T.ROCK.ordinal
        }

        val grassPatches = (w * h / 280).coerceAtLeast(6)
        repeat(grassPatches) {
            val bx = 4 + rnd.nextInt(w - 8)
            val by = 4 + rnd.nextInt(h - 8)
            val br = 1 + rnd.nextInt(2)
            for (y in by - br..by + br) for (x in bx - br..bx + br) {
                if (x in 2 until w - 2 && y in 2 until h - 2 && !reserved[y][x] &&
                    t[y][x] == T.GRASS.ordinal && rnd.nextFloat() < 0.75f
                ) t[y][x] = T.TALLGRASS.ordinal
            }
        }

        return GameMap(region, w, h, t, npcs, hasHouse, houseDoorX, houseDoorY)
    }

    /** 넓어진 집 내부 맵 (19x13) — 폰 화면에서도 방이 시원하게 보인다. */
    fun buildHome(): GameMap {
        val w = 19
        val h = 13
        val t = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        // 따뜻한 벽체와 걸레받이
        for (x in 0 until w) { t[0][x] = T.WALL_IN.ordinal; t[1][x] = T.WALL_IN.ordinal }
        for (y in 0 until h) { t[y][0] = T.WALL_IN.ordinal; t[y][w - 1] = T.WALL_IN.ordinal }
        for (x in 0 until w) t[h - 1][x] = T.WALL_IN.ordinal
        // 현관문 (아래쪽 중앙)
        val doorX = w / 2
        t[h - 1][doorX] = T.HOUSE_DOOR.ordinal
        // 창문과 생활 가구
        t[1][5] = T.WALL_WIN.ordinal; t[1][11] = T.WALL_WIN.ordinal
        t[4][12] = T.OVEN.ordinal; t[4][13] = T.OVEN.ordinal
        t[5][12] = T.OVEN.ordinal; t[5][13] = T.OVEN.ordinal
        t[4][5] = T.BED.ordinal; t[4][6] = T.BED.ordinal
        t[8][5] = T.BOX.ordinal
        // 카펫, 책장, 화분, 수납장으로 공간에 깊이와 생활감을 더한다.
        for (y in 7..8) for (x in 7..11) t[y][x] = T.RUG.ordinal
        t[3][8] = T.SHELF.ordinal; t[3][9] = T.SHELF.ordinal
        t[6][4] = T.PLANT.ordinal
        t[6][14] = T.PLANT.ordinal
        t[7][12] = T.CABINET.ordinal
        t[4][8] = T.DECOR.ordinal; t[4][10] = T.DECOR.ordinal
        t[8][14] = T.DECOR.ordinal
        val home = Regions.byId["seoul"]!! // 내부맵은 지역 무관 (더미)
        return GameMap(home, w, h, t, emptyList(), true, doorX, h - 1)
    }
}

// ---------------------------------------------------------------------------
// 엔티티
// ---------------------------------------------------------------------------

enum class NpcKind { PROFESSOR, SHOP, VILLAGER }

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
        }
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

    /** Advance the gait by actual distance, not elapsed time; blocked movement won't animate or drain food. */
    fun advanceGait(distance: Float): Int {
        if (distance <= 0.01f) return 0
        val before = animT
        val next = before + distance / (if (bike) 40f else 22f)
        val contacts = ((next * 2f).toInt() - (before * 2f).toInt()).coerceAtLeast(0)
        animT = next % 1f
        return contacts
    }

    val gaitFrame: Int get() = if (moving) (animT * 4f).toInt().coerceIn(0, 3) else 0
    val bodyLift: Float
        get() = if (moving) abs(sin(animT * 6.2831853f)) * (if (bike) 0.7f else 1.35f) else 0f

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

    val cx: Float get() = x + 7f
    val cy: Float get() = y + 6f

    fun update(dt: Float, playerCx: Float, playerCy: Float, onBike: Boolean, sneaking: Boolean, map: GameMap) {
        val fleeTiles = when (def.tier) {
            Tier.COMMON -> 1.7f
            Tier.UNCOMMON -> 2.3f
            Tier.RARE -> 3.0f
            Tier.LEGEND -> 3.8f
        } * (if (sneaking) 0.6f else 1f) * (if (onBike) 1.4f else 1f)

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
