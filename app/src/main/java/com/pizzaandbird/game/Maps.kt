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

/**
 * 맵 타일.
 *
 * - `solid`  : 걸을 수 없음
 * - `ground` : 지면 레이어로 깔리는 타일 (그 위에 포장/소품이 얹힌다)
 * - `prop`   : 배경이 투명한 소품 (지면을 깔고 그 위에 그린다)
 * - `bulk`   : 부피가 큰 구조물 (주변 바닥에 그늘을 드리운다)
 */
enum class T(
    val solid: Boolean,
    val ground: Boolean = false,
    val prop: Boolean = false,
    val bulk: Boolean = false
) {
    GRASS(false, ground = true),
    TALLGRASS(false, ground = true),
    FLOWER(false, ground = true),
    PATH(false, ground = true),             // 흙길 — 그림은 Roads.kt 오토타일
    PLAZA(false, ground = true),            // 석재 포장
    SAND(false, ground = true),
    WATER(true, ground = true),
    REED(false, ground = true),
    TREE(true, prop = true),
    ROCK(true, prop = true),
    MOUNTAIN(true, bulk = true),
    BLDG_WALL(true, bulk = true),
    BLDG_WIN(true, bulk = true),
    BLDG_ROOF(true, bulk = true),
    HOUSE_ROOF(true, bulk = true),
    HOUSE_WALL(true, bulk = true),
    HOUSE_WIN(true, bulk = true),
    HOUSE_DOOR(false),      // 우리 집 현관 (들어가기)
    TUNNEL(false, bulk = true),          // 지역 이동 터널!
    FLOOR(false, ground = true),
    WALL_IN(true, bulk = true),
    WALL_WIN(true, bulk = true),
    OVEN(true),             // 화덕 (화덕피자)
    BED(true),
    BOX(true),
    DECOR(false),           // 장식 슬롯
    SIGN(true, prop = true),             // 터널 이정표
    BENCH(true, prop = true),            // 벤치
    LAMP(true, prop = true),             // 가로등 (밤에 빛남)
    RANGE_TOP(true),        // 가정용 오븐 윗부분 (후드·선반)
    RANGE(true);            // 가정용 오븐 (일반 피자) — 2프레임 불빛

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
    private val tiles: Array<IntArray>,     // 논리 타일 (충돌/상호작용의 기준)
    private val ground: Array<IntArray>,    // 지면 레이어 (잔디/모래/물/꽃…)
    private val paving: Array<IntArray>,    // 포장 레이어 (Pave.NONE / DIRT / STONE)
    private val decals: Array<IntArray>,    // 광장 문양(1..9) / 빗물받이(10)
    val npcs: List<Npc>,
    val hasHouse: Boolean,
    val houseDoorX: Int,
    val houseDoorY: Int
) {
    fun t(x: Int, y: Int): T {
        if (x < 0 || y < 0 || x >= w || y >= h) return T.MOUNTAIN
        return T.ALL[tiles[y][x]]
    }

    /** 이 칸의 지면 (포장 아래에 깔린 자연 지형) */
    fun groundAt(x: Int, y: Int): T {
        if (x < 0 || y < 0 || x >= w || y >= h) return T.GRASS
        return T.ALL[ground[y][x]]
    }

    /** 포장 재질 (0 = 흙/잔디 그대로) */
    fun paveAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= w || y >= h) return Pave.NONE
        return paving[y][x]
    }

    /** 길 위인가 (자전거·발소리·새 스폰 판정에 쓸 수 있다) */
    fun onRoad(px: Float, py: Float): Boolean =
        paveAt(((px + 8f) / 16f).toInt(), ((py + 13f) / 16f).toInt()) != Pave.NONE

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

    /** 이웃 8칸의 포장 여부를 비트마스크로 (오토타일 키) */
    private fun paveMask(x: Int, y: Int): Int {
        var m = 0
        if (paveAt(x, y - 1) != 0) m = m or RoadArt.BN
        if (paveAt(x + 1, y) != 0) m = m or RoadArt.BE
        if (paveAt(x, y + 1) != 0) m = m or RoadArt.BS
        if (paveAt(x - 1, y) != 0) m = m or RoadArt.BW
        if (paveAt(x + 1, y - 1) != 0) m = m or RoadArt.BNE
        if (paveAt(x + 1, y + 1) != 0) m = m or RoadArt.BSE
        if (paveAt(x - 1, y + 1) != 0) m = m or RoadArt.BSW
        if (paveAt(x - 1, y - 1) != 0) m = m or RoadArt.BNW
        return m
    }

    private fun bulkAt(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= w || y >= h) return false
        return T.ALL[tiles[y][x]].bulk
    }

    /**
     * 타일 렌더링 (32px 타일, 카메라는 가상 해상도 좌표).
     *
     * 레이어 순서: 지면 -> 포장(오토타일) -> 데칼 -> 구조물/소품 -> 접지 그림자.
     * 물과 가정용 오븐(RANGE)은 애니메이션, 집 화덕은 HomeScene의 SVG 일러스트로 렌더링하며 물가에는 거품이 인다.
     */
    fun draw(
        c: Canvas, a: Assets, camX: Float, camY: Float, vw: Int, vh: Int, time: Float,
        sunDx: Float = 0f, sunLen: Float = 0f, sunAlpha: Int = 0
    ) {
        val x0 = (camX / 32f).toInt().coerceAtLeast(0)
        val y0 = (camY / 32f).toInt().coerceAtLeast(0)
        val x1 = ((camX + vw) / 32f).toInt().coerceAtMost(w - 1)
        val y1 = ((camY + vh) / 32f).toInt().coerceAtMost(h - 1)
        val waterFrame = ((time * 2.2f).toInt() % 4 + 4) % 4
        val ovenFrame = ((time * 3.4f).toInt() % 2 + 2) % 2      // 가정용 오븐 불빛 깜빡임

        for (y in y0..y1) {
            for (x in x0..x1) {
                val fx = x * 32f - camX
                val fy = y * 32f - camY
                val tv = tiles[y][x]
                val tile = T.ALL[tv]
                val pv = paving[y][x]

                // 1) 지면 — 포장/소품 아래에 깔린다 (불투명한 구조물 아래는 생략)
                if (pv != Pave.NONE || tile.ground || tile.prop || tile == T.OVEN) {
                    val gv = ground[y][x]
                    val gTile = T.ALL[gv]
                    val gBmp = if (gTile == T.WATER) a.tiles[gv][minOf(waterFrame, a.tiles[gv].size - 1)]
                    else a.tiles[gv][a.tileVariant(gv, x, y)]
                    c.drawBitmap(gBmp, fx, fy, a.sprPaint)

                    // 물가 거품 (물 타일 가장자리) — 출렁이는 포말 + 반짝임
                    if (gTile == T.WATER && pv == Pave.NONE) {
                        val ph = time * 2.8f + x * 1.15f + y * 0.85f
                        if (y > 0 && groundAt(x, y - 1) != T.WATER) foamEdge(c, fx, fy, fx + 32f, fy, ph)
                        if (y < h - 1 && groundAt(x, y + 1) != T.WATER) foamEdge(c, fx, fy + 32f, fx + 32f, fy + 32f, ph + 1.7f)
                        if (x > 0 && groundAt(x - 1, y) != T.WATER) foamEdgeV(c, fx, fy, fx, fy + 32f, ph + 0.9f)
                        if (x < w - 1 && groundAt(x + 1, y) != T.WATER) foamEdgeV(c, fx + 32f, fy, fx + 32f, fy + 32f, ph + 2.3f)
                        // 물 반짝임 (별 반짝임 십자)
                        if ((x * 7 + y * 13) % 6 == 0) {
                            val tw = (sin(time * 2.6f + x * 1.7f + y * 2.3f) + 1f) / 2f
                            if (tw > 0.62f) {
                                val k = (tw - 0.62f) / 0.38f
                                val sx = fx + 8f + ((x * 11 + y * 5) % 16)
                                val sy = fy + 7f + ((x * 3 + y * 9) % 18)
                                val al = (110 + 130 * k).toInt()
                                sparkle.color = Color.argb(al, 255, 255, 255)
                                c.drawRect(sx, sy - 2.2f, sx + 1.6f, sy + 3.8f, sparkle)
                                c.drawRect(sx - 2.2f, sy, sx + 3.8f, sy + 1.6f, sparkle)
                                sparkle.color = Color.argb(al / 2, 255, 255, 255)
                                c.drawRect(sx - 4f, sy, sx + 5.6f, sy + 1.2f, sparkle)
                            }
                        }
                    }
                }

                // 2) 포장면 (이웃 모양에 맞춰 자동 생성 + 캐시)
                if (pv != Pave.NONE) {
                    val sandy = T.ALL[ground[y][x]] == T.SAND
                    val variant = if (pv == Pave.STONE) (y and 1) else ((x * 5 + y * 11) % 3)
                    c.drawBitmap(a.roadTile(pv, paveMask(x, y), variant, sandy), fx, fy, a.sprPaint)
                }

                // 3) 데칼 (광장 문양 / 빗물받이)
                val d = decals[y][x]
                if (d in 1..9) c.drawBitmap(a.medallion[d - 1], fx, fy, a.sprPaint)
                else if (d == 10) c.drawBitmap(a.drain, fx, fy, a.sprPaint)

            }
        }

        // 3.5) 햇빛 그림자 — 해의 위치(시각)에 따라 나무·가로등·이정표의 긴 그림자가 돌아간다
        if (sunAlpha > 0 && sunLen > 0f) {
            sunPaint.color = Color.argb(sunAlpha, 18, 30, 22)
            val k = sunDx / sunLen
            // 화면 바로 위/옆의 소품도 그림자가 화면 안으로 드리울 수 있다
            for (y in (y0 - 2).coerceAtLeast(0)..y1) {
                for (x in (x0 - 1).coerceAtLeast(0)..(x1 + 1).coerceAtMost(w - 1)) {
                    val tile = T.ALL[tiles[y][x]]
                    if (tile != T.TREE && tile != T.LAMP && tile != T.SIGN && tile != T.ROCK) continue
                    val bx = x * 32f - camX + 16f
                    val by = y * 32f - camY + 29f
                    c.save()
                    c.translate(bx, by)
                    c.skew(k, 0f)
                    when (tile) {
                        T.TREE -> {
                            sunRect.set(-3f, -1f, 3f, sunLen * 0.45f)
                            c.drawRect(sunRect, sunPaint)                       // 줄기
                            sunRect.set(-11f, sunLen * 0.3f, 11f, sunLen * 1.05f + 4f)
                            c.drawOval(sunRect, sunPaint)                       // 수관
                        }
                        T.LAMP -> {
                            sunRect.set(-1.6f, -1f, 1.6f, sunLen * 1.2f)
                            c.drawRect(sunRect, sunPaint)
                            sunRect.set(-6f, sunLen * 1.15f, 6f, sunLen * 1.15f + 5f)
                            c.drawRect(sunRect, sunPaint)
                        }
                        T.SIGN -> {
                            sunRect.set(-1.4f, -1f, 1.4f, sunLen * 0.5f)
                            c.drawRect(sunRect, sunPaint)
                            sunRect.set(-10f, sunLen * 0.45f, 10f, sunLen * 0.8f)
                            c.drawRect(sunRect, sunPaint)
                        }
                        else -> {
                            sunRect.set(-9f, -3f, 9f, sunLen * 0.4f)
                            c.drawOval(sunRect, sunPaint)
                        }
                    }
                    c.restore()
                }
            }
        }

        for (y in y0..y1) {
            for (x in x0..x1) {
                val fx = x * 32f - camX
                val fy = y * 32f - camY
                val tv = tiles[y][x]
                val tile = T.ALL[tv]

                // 4) 구조물 / 소품
                if (!tile.ground && tile != T.OVEN) {
                    val bmp = if (tile == T.RANGE) a.tiles[tv][minOf(ovenFrame, a.tiles[tv].size - 1)]
                    else a.tiles[tv][a.tileVariant(tv, x, y)]
                    c.drawBitmap(bmp, fx, fy, a.sprPaint)
                }
            }
        }

        // 5) 접지 그림자 — 빛은 왼쪽 위에서 온다
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (T.ALL[tiles[y][x]].bulk) continue
                val fx = x * 32f - camX
                val fy = y * 32f - camY
                val up = bulkAt(x, y - 1)
                val left = bulkAt(x - 1, y)
                if (up) c.drawBitmap(a.castShadow[0], fx, fy, a.sprPaint)
                if (left) c.drawBitmap(a.castShadow[1], fx, fy, a.sprPaint)
                if (!up && !left && bulkAt(x - 1, y - 1)) c.drawBitmap(a.castShadow[2], fx, fy, a.sprPaint)
            }
        }
    }

    /** 물가 거품 (가로 변) — 기본 라인 위에 출렁이는 포말 */
    private fun foamEdge(c: Canvas, x0: Float, yEdge: Float, x1: Float, yEdge2: Float, ph: Float) {
        c.drawRect(x0, yEdge, x1, yEdge + 2.8f, foamPaint)
        for (i in 0 until 5) {
            val u = (i * 0.22f + (Math.sin(ph.toDouble() + i).toFloat() * 0.06f) + 0.12f) % 1f
            val bx = x0 + u * (x1 - x0)
            val bw = 4.2f + ((i * 3) % 3)
            foamDot.color = Color.argb(170, 240, 250, 255)
            c.drawRect(bx, yEdge + 1.2f, bx + bw, yEdge + 3.4f, foamDot)
            foamDot.color = Color.argb(110, 240, 250, 255)
            c.drawRect(bx - 1.2f, yEdge + 2.6f, bx + bw + 1.4f, yEdge + 4.2f, foamDot)
        }
    }

    /** 물가 거품 (세로 변) */
    private fun foamEdgeV(c: Canvas, xEdge: Float, y0: Float, xEdge2: Float, y1: Float, ph: Float) {
        c.drawRect(xEdge, y0, xEdge + 2.8f, y1, foamPaint)
        for (i in 0 until 5) {
            val u = (i * 0.22f + (Math.cos(ph.toDouble() + i).toFloat() * 0.06f) + 0.12f) % 1f
            val by = y0 + u * (y1 - y0)
            val bh = 4.2f + ((i * 3) % 3)
            foamDot.color = Color.argb(170, 240, 250, 255)
            c.drawRect(xEdge + 1.2f, by, xEdge + 3.4f, by + bh, foamDot)
            foamDot.color = Color.argb(110, 240, 250, 255)
            c.drawRect(xEdge + 2.6f, by - 1.2f, xEdge + 4.2f, by + bh + 1.4f, foamDot)
        }
    }

    companion object {
        private val foamPaint = Paint().apply {
            color = Color.argb(150, 226, 244, 250)
        }
        private val foamDot = Paint()
        private val sparkle = Paint()
        private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sunRect = android.graphics.RectF()
    }
}

// ---------------------------------------------------------------------------
// 지역 맵 빌더 (결정적 절차 생성 — 지역 id 시드)
// ---------------------------------------------------------------------------

object MapBuilder {

    // 마을 기준 좌표 -----------------------------------------------------------
    private const val PLAZA_X0 = 17     // 중앙 광장 (팔각형)
    private const val PLAZA_X1 = 25
    private const val PLAZA_Y0 = 12
    private const val PLAZA_Y1 = 18
    private const val AVE_X = 19        // 남북 간선도로 기준 x (2칸 폭 -> 19,20)
    private const val AVE_Y = 15        // 동서 간선도로 기준 y (2칸 폭 -> 15,16)

    /**
     * 지역 월드맵 생성 (결정적 절차 생성 — 지역 id 시드).
     *
     * 길 설계
     *  - 남북/동서로 **2칸 폭 간선도로**가 지나고, 가운데에서 팔각 광장으로 모인다.
     *  - 간선은 완전한 직선이 아니라 구간마다 살짝 사행(蛇行)한다. 다만 터널·광장
     *    진입부는 반드시 직선으로 들어가 길이 어색하게 꺾이지 않는다.
     *  - 막다른 방향(출구가 없는 쪽)은 회차 공간(컬드삭)으로 마무리한다.
     *  - 건물 정문·호숫가 데크·숲속 쉼터까지 1칸 폭 샛길이 뻗는다.
     *  - 중심선을 따라 가로수와 가로등이 번갈아 도열한다.
     */
    fun build(region: RegionDef, homeRegion: String): GameMap {
        val w = region.mapW
        val h = region.mapH
        val t = Array(h) { IntArray(w) { T.GRASS.ordinal } }
        val base = Array(h) { IntArray(w) { T.GRASS.ordinal } }
        val pave = Array(h) { IntArray(w) }
        val deco = Array(h) { IntArray(w) }
        val reserved = Array(h) { BooleanArray(w) }
        val structure = Array(h) { BooleanArray(w) }      // 길이 뚫고 지나갈 수 없는 칸
        val rnd = Random(region.id.hashCode().toLong())
        val exits = Regions.exits(region.id)

        fun inb(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h
        fun setGround(x: Int, y: Int, tile: T) {
            t[y][x] = tile.ordinal
            base[y][x] = tile.ordinal
        }

        // 1. 바다/모래 가장자리 ------------------------------------------------
        fun waterBand(d: Dir, depth: Int, tile: T) {
            when (d) {
                Dir.N -> for (y in 0 until depth) for (x in 0 until w) setGround(x, y, tile)
                Dir.S -> for (y in h - depth until h) for (x in 0 until w) setGround(x, y, tile)
                Dir.W -> for (x in 0 until depth) for (y in 0 until h) setGround(x, y, tile)
                Dir.E -> for (x in w - depth until w) for (y in 0 until h) setGround(x, y, tile)
            }
        }
        for (d in region.waterEdges) waterBand(d, 3, T.WATER)
        for (d in region.sandEdges) waterBand(d, 2, T.SAND)
        // 물 가장자리의 갈대 띠: 습지·강 지역의 특징을 맵 구조에도 드러낸다.
        if (region.kind == RegionKind.WETLAND || region.kind == RegionKind.RIVER) {
            for (d in region.waterEdges) when (d) {
                Dir.N -> for (x in 2 until w - 2) if (rnd.nextBoolean()) setGround(x, 3, T.REED)
                Dir.S -> for (x in 2 until w - 2) if (rnd.nextBoolean()) setGround(x, h - 4, T.REED)
                Dir.W -> for (y in 2 until h - 2) if (rnd.nextBoolean()) setGround(3, y, T.REED)
                Dir.E -> for (y in 2 until h - 2) if (rnd.nextBoolean()) setGround(w - 4, y, T.REED)
            }
        }

        // 2. 육지 가장자리 (산맥/수림) -----------------------------------------
        val borderTile = if (region.rockDensity >= 0.08) T.MOUNTAIN else T.TREE
        for (y in 0 until h) for (x in 0 until w) {
            val onEdge = x < 2 || y < 2 || x >= w - 2 || y >= h - 2
            if (onEdge && t[y][x] == T.GRASS.ordinal) t[y][x] = borderTile.ordinal
        }

        // 3. 호수/습지 (길보다 먼저 — 길이 호숫가를 피해 돌아가도록) -------------
        var lakeShoreX = -1
        var lakeShoreY = -1
        if (region.lake) {
            val cx = 30; val cy = 22; val rx = 4; val ry = 3
            for (y in cy - ry - 1..cy + ry + 1) for (x in cx - rx - 1..cx + rx + 1) {
                if (x !in 2 until w - 2 || y !in 2 until h - 2) continue
                val dx = (x - cx) / rx.toFloat()
                val dy = (y - cy) / ry.toFloat()
                val d2 = dx * dx + dy * dy
                if (d2 <= 1f) {
                    setGround(x, y, T.WATER); reserved[y][x] = true
                } else if (d2 <= 1.6f && t[y][x] == T.GRASS.ordinal) {
                    setGround(x, y, T.REED); reserved[y][x] = true
                }
            }
            lakeShoreX = cx
            lakeShoreY = cy - ry - 1
        }

        // 4. 도시 건물 (길보다 먼저 — 정문 앞으로 샛길을 내기 위해) ---------------
        val buildingFronts = ArrayList<Pair<Int, Int>>()
        if (region.city) {
            for ((bx, by) in listOf(6 to 5, 31 to 5, 6 to 24, 31 to 24)) {
                var ok = true
                for (y in by until by + 3) for (x in bx until bx + 3) {
                    if (x !in 2 until w - 2 || y !in 2 until h - 2 || t[y][x] != T.GRASS.ordinal) ok = false
                }
                if (!ok) continue
                for (y in by until by + 3) for (x in bx until bx + 3) {
                    t[y][x] = when {
                        y == by -> T.BLDG_ROOF
                        (x + y) % 2 == 0 -> T.BLDG_WIN
                        else -> T.BLDG_WALL
                    }.ordinal
                    structure[y][x] = true
                    reserved[y][x] = true
                }
                // 정문은 간선도로를 바라보는 쪽에
                buildingFronts.add(if (by + 3 <= AVE_Y) (bx + 1 to by + 3) else (bx + 1 to by - 1))
            }
        }

        // 5. 우리 집 -------------------------------------------------------------
        var houseDoorX = -1
        var houseDoorY = -1
        val hasHouse = homeRegion == region.id
        if (hasHouse) {
            for (x in 21..25) for (y in 8..9) {
                t[y][x] = T.HOUSE_ROOF.ordinal
                structure[y][x] = true
            }
            val row10 = arrayOf(T.HOUSE_WALL, T.HOUSE_WIN, T.HOUSE_WALL, T.HOUSE_WIN, T.HOUSE_WALL)
            for (i in 0 until 5) {
                t[10][21 + i] = row10[i].ordinal
                structure[10][21 + i] = true
            }
            val row11 = arrayOf(T.HOUSE_WALL, T.HOUSE_WALL, T.HOUSE_DOOR, T.HOUSE_DOOR, T.HOUSE_WALL)
            for (i in 0 until 5) {
                t[11][21 + i] = row11[i].ordinal
                structure[11][21 + i] = row11[i] != T.HOUSE_DOOR
            }
            for (y in 8..12) for (x in 20..26) reserved[y][x] = true
            houseDoorX = 23
            houseDoorY = 11
            // 현관 문턱은 광장과 같은 석재 포장
            for (x in 23..24) pave[11][x] = Pave.STONE
        }

        // 6. 중앙 광장 (팔각형) ---------------------------------------------------
        for (y in PLAZA_Y0..PLAZA_Y1) for (x in PLAZA_X0..PLAZA_X1) {
            if (structure[y][x]) continue
            t[y][x] = T.PLAZA.ordinal
            pave[y][x] = Pave.STONE
            base[y][x] = T.GRASS.ordinal
            reserved[y][x] = true
        }
        val plazaCorners = listOf(
            PLAZA_X0 to PLAZA_Y0, PLAZA_X1 to PLAZA_Y0,
            PLAZA_X0 to PLAZA_Y1, PLAZA_X1 to PLAZA_Y1
        )
        for ((cx0, cy0) in plazaCorners) {
            if (structure[cy0][cx0]) continue
            t[cy0][cx0] = T.GRASS.ordinal
            base[cy0][cx0] = T.GRASS.ordinal
            pave[cy0][cx0] = Pave.NONE
        }
        // 한가운데 문양 (3x3) + 빗물받이 2개
        val mx = (PLAZA_X0 + PLAZA_X1) / 2 - 1
        val my = (PLAZA_Y0 + PLAZA_Y1) / 2 - 1
        for (dy in 0 until 3) for (dx in 0 until 3) deco[my + dy][mx + dx] = 1 + dy * 3 + dx
        deco[PLAZA_Y0][PLAZA_X0 + 1] = 10
        deco[PLAZA_Y1][PLAZA_X1 - 1] = 10

        // 7. 간선 도로 -------------------------------------------------------------
        fun canPave(x: Int, y: Int): Boolean {
            if (!inb(x, y) || structure[y][x]) return false
            return x in 2 until w - 2 && y in 2 until h - 2   // 가장자리 링은 터널 진입로에서만
        }

        fun stamp(x: Int, y: Int, size: Int, mat: Int) {
            for (yy in y until y + size) for (xx in x until x + size) {
                if (!canPave(xx, yy)) continue
                if (pave[yy][xx] == Pave.STONE) continue
                if (base[yy][xx] == T.WATER.ordinal) base[yy][xx] = T.SAND.ordinal    // 물 위를 지나면 모래 둑길
                t[yy][xx] = if (mat == Pave.DIRT) T.PATH.ordinal else T.PLAZA.ordinal
                pave[yy][xx] = mat
                reserved[yy][xx] = true
            }
        }

        /** control point 를 1칸씩 이어가며 브러시를 찍는다 (연결 보장). 중심선을 돌려준다. */
        fun walk(points: List<Pair<Int, Int>>, size: Int): List<Pair<Int, Int>> {
            var px = points[0].first
            var py = points[0].second
            val line = ArrayList<Pair<Int, Int>>()
            line.add(px to py)
            stamp(px, py, size, Pave.DIRT)
            for (i in 1 until points.size) {
                val tx = points[i].first
                val ty = points[i].second
                while (px != tx || py != ty) {
                    if (kotlin.math.abs(tx - px) >= kotlin.math.abs(ty - py) && px != tx) {
                        px += if (tx > px) 1 else -1
                    } else if (py != ty) {
                        py += if (ty > py) 1 else -1
                    } else {
                        px += if (tx > px) 1 else -1
                    }
                    stamp(px, py, size, Pave.DIRT)
                    line.add(px to py)
                }
            }
            return line
        }

        /** 컬드삭(회차 공간) — 막다른 길 끝의 원형 마당 */
        fun stampRound(cx: Int, cy: Int, rad: Int) {
            for (y in cy - rad..cy + rad) for (x in cx - rad..cx + rad) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= rad * rad + 1) stamp(x, y, 1, Pave.DIRT)
            }
        }

        /** 1칸 샛길 예정 경로가 전부 지나갈 수 있는지 미리 검사 */
        fun pathClear(points: List<Pair<Int, Int>>): List<Pair<Int, Int>>? {
            var px = points[0].first
            var py = points[0].second
            val seq = ArrayList<Pair<Int, Int>>()
            seq.add(px to py)
            for (i in 1 until points.size) {
                val tx = points[i].first
                val ty = points[i].second
                while (px != tx || py != ty) {
                    if (kotlin.math.abs(tx - px) >= kotlin.math.abs(ty - py) && px != tx) {
                        px += if (tx > px) 1 else -1
                    } else if (py != ty) {
                        py += if (ty > py) 1 else -1
                    } else {
                        px += if (tx > px) 1 else -1
                    }
                    seq.add(px to py)
                }
            }
            for ((x, y) in seq) {
                if (!inb(x, y) || structure[y][x] || base[y][x] == T.WATER.ordinal) return null
            }
            return seq
        }

        // 사행(蛇行)은 1칸까지만 — 2칸을 한 번에 꺾으면 길이 뭉개져 보인다
        val bendN = -rnd.nextInt(2)         // 북쪽 구간 (-1..0 — 집을 피해 서쪽으로만)
        val bendS = rnd.nextInt(2)          // 남쪽 구간 (0..1)
        val bendW = rnd.nextInt(3) - 1      // 서쪽 구간 (-1..1)
        val bendE = rnd.nextInt(3) - 1      // 동쪽 구간 (-1..1)

        val nEnd = 2
        val sEnd = h - 3
        val wEnd = 2
        val eEnd = w - 3
        val centerlines = ArrayList<Pair<Boolean, List<Pair<Int, Int>>>>()   // (세로인가, 중심선)
        centerlines.add(true to walk(
            listOf(AVE_X to nEnd, AVE_X to 4, AVE_X + bendN to 6, AVE_X + bendN to 9, AVE_X to 10, AVE_X to 11), 2))
        centerlines.add(true to walk(
            listOf(AVE_X to PLAZA_Y1 - 1, AVE_X to 20, AVE_X + bendS to 22, AVE_X + bendS to 24,
                AVE_X to sEnd - 2, AVE_X to sEnd), 2))
        centerlines.add(false to walk(
            listOf(wEnd to AVE_Y, 6 to AVE_Y, 9 to AVE_Y + bendW, 12 to AVE_Y + bendW,
                PLAZA_X0 - 3 to AVE_Y, PLAZA_X0 - 1 to AVE_Y), 2))
        centerlines.add(false to walk(
            listOf(PLAZA_X1 to AVE_Y, 28 to AVE_Y, 31 to AVE_Y + bendE, 34 to AVE_Y + bendE,
                eEnd - 2 to AVE_Y, eEnd to AVE_Y), 2))

        // 막다른 방향은 회차 공간으로 마무리
        if (!exits.containsKey(Dir.N)) stampRound(AVE_X, 3, 1)
        if (!exits.containsKey(Dir.S)) stampRound(AVE_X, h - 4, 1)
        if (!exits.containsKey(Dir.W)) stampRound(3, AVE_Y, 1)
        if (!exits.containsKey(Dir.E)) stampRound(w - 4, AVE_Y, 1)

        // 광장 진입부 나팔목 (길이 넓어지며 광장으로 이어진다)
        for ((fx, fy) in listOf(
            AVE_X - 1 to PLAZA_Y0 - 1, AVE_X + 2 to PLAZA_Y0 - 1,
            AVE_X - 1 to PLAZA_Y1 + 1, AVE_X + 2 to PLAZA_Y1 + 1,
            PLAZA_X0 - 1 to AVE_Y - 1, PLAZA_X0 - 1 to AVE_Y + 2,
            PLAZA_X1 + 1 to AVE_Y - 1, PLAZA_X1 + 1 to AVE_Y + 2
        )) stamp(fx, fy, 1, Pave.DIRT)

        // 8. 터널 & 진입로 (가장자리 링을 뚫고 나간다) -------------------------------
        fun openTunnel(x: Int, y: Int) {
            t[y][x] = T.TUNNEL.ordinal
            if (base[y][x] == T.WATER.ordinal) base[y][x] = T.SAND.ordinal
            structure[y][x] = false
            reserved[y][x] = true
        }
        fun approach(x: Int, y: Int) {
            t[y][x] = T.PATH.ordinal
            pave[y][x] = Pave.DIRT
            if (base[y][x] == T.WATER.ordinal) base[y][x] = T.SAND.ordinal   // 바다를 건너면 모래 둑길
            reserved[y][x] = true
        }
        for (d in exits.keys) {
            when (d) {
                Dir.N -> for (x in 19..20) { openTunnel(x, 0); approach(x, 1); approach(x, 2) }
                Dir.S -> for (x in 19..20) { openTunnel(x, h - 1); approach(x, h - 2); approach(x, h - 3) }
                Dir.W -> for (y in 15..16) { openTunnel(0, y); approach(1, y); approach(2, y) }
                Dir.E -> for (y in 15..16) { openTunnel(w - 1, y); approach(w - 2, y); approach(w - 3, y) }
            }
        }

        // 9. 샛길 -------------------------------------------------------------------
        for ((fx, fy) in buildingFronts) {
            val targetY = if (fy < AVE_Y) AVE_Y else AVE_Y + 1
            val seq = pathClear(listOf(fx to fy, fx to targetY)) ?: continue
            for ((x, y) in seq) stamp(x, y, 1, Pave.DIRT)
        }

        // 호숫가 전망 데크
        if (lakeShoreX >= 0) {
            val seq = pathClear(listOf(lakeShoreX to AVE_Y + 1, lakeShoreX to lakeShoreY))
            if (seq != null) {
                for ((x, y) in seq) stamp(x, y, 1, Pave.DIRT)
                for (yy in lakeShoreY - 1..lakeShoreY) for (xx in lakeShoreX - 1..lakeShoreX) {
                    if (inb(xx, yy) && base[yy][xx] != T.WATER.ordinal && !structure[yy][xx]) {
                        stamp(xx, yy, 1, Pave.STONE)
                    }
                }
                if (inb(lakeShoreX - 1, lakeShoreY - 1) && pave[lakeShoreY - 1][lakeShoreX - 1] != Pave.NONE) {
                    t[lakeShoreY - 1][lakeShoreX - 1] = T.BENCH.ordinal
                }
            }
        }

        // 숲속 쉼터 (사진 찍기 좋은 자리)
        val restX = if (region.lake) 9 else 30
        val restY = 22
        val restSeq = pathClear(listOf(restX to AVE_Y + 2, restX to restY))
        if (restSeq != null) {
            for ((x, y) in restSeq) stamp(x, y, 1, Pave.DIRT)
            stamp(restX - 1, restY - 1, 3, Pave.DIRT)
            if (pave[restY - 1][restX - 1] != Pave.NONE) t[restY - 1][restX - 1] = T.BENCH.ordinal
        }

        // 10. 터널 이정표 -------------------------------------------------------------
        for (d in exits.keys) {
            val (sx, sy) = when (d) {
                Dir.N -> 22 to 1
                Dir.S -> 22 to h - 4
                Dir.W -> 2 to 13
                Dir.E -> w - 3 to 13
            }
            t[sy][sx] = T.SIGN.ordinal
            if (pave[sy][sx] == Pave.NONE) base[sy][sx] = T.GRASS.ordinal
            reserved[sy][sx] = true
            for ((ax, ay) in listOf(sx + 1 to sy, sx - 1 to sy, sx to sy + 1, sx to sy - 1)) {
                if (inb(ax, ay)) reserved[ay][ax] = true
            }
        }

        // 11. 광장 시설 (NPC 자리 확보 -> 벤치 -> 모서리 가로등/그늘나무) --------------
        val npcs = listOf(
            Npc(NpcKind.PROFESSOR, 17, 13),
            Npc(NpcKind.SHOP, 23, 13),
            Npc(NpcKind.VILLAGER, 20, 18),
            Npc(NpcKind.KID, 18, 17),
            Npc(NpcKind.ELDER, 25, 15)
        )
        val npcTiles = HashSet<Int>()
        for (n in npcs) {
            npcTiles.add(n.tileY * 100 + n.tileX)
            npcTiles.add((n.tileY + 1) * 100 + n.tileX)
            reserved[n.tileY][n.tileX] = true
            if (n.tileY + 1 < h) reserved[n.tileY + 1][n.tileX] = true
        }

        fun putProp(x: Int, y: Int, tile: T): Boolean {
            if (!inb(x, y) || structure[y][x] || npcTiles.contains(y * 100 + x)) return false
            val cur = T.ALL[t[y][x]]
            if (cur == T.TUNNEL || cur == T.SIGN || cur == T.HOUSE_DOOR) return false
            if (base[y][x] == T.WATER.ordinal) return false
            t[y][x] = tile.ordinal
            reserved[y][x] = true
            return true
        }

        for ((bx, by) in listOf(18 to 13, 24 to 17)) putProp(bx, by, T.BENCH)
        for ((cx0, cy0) in plazaCorners) putProp(cx0, cy0, if (region.city) T.LAMP else T.TREE)

        // 12. 가로수 / 가로등 도열 + 길섶 꽃 ---------------------------------------------
        fun roadside(x: Int, y: Int): Boolean {
            if (!inb(x, y) || reserved[y][x] || structure[y][x]) return false
            if (t[y][x] != T.GRASS.ordinal || pave[y][x] != Pave.NONE) return false
            if (x in PLAZA_X0 - 1..PLAZA_X1 + 1 && y in PLAZA_Y0 - 1..PLAZA_Y1 + 1) return false
            if (inb(x, y - 1) && pave[y - 1][x] == Pave.DIRT) return true
            if (inb(x, y + 1) && pave[y + 1][x] == Pave.DIRT) return true
            if (inb(x - 1, y) && pave[y][x - 1] == Pave.DIRT) return true
            if (inb(x + 1, y) && pave[y][x + 1] == Pave.DIRT) return true
            return false
        }

        for ((vertical, line) in centerlines) {
            for (i in line.indices) {
                val (x, y) = line[i]
                val leftX = if (vertical) x - 1 else x
                val leftY = if (vertical) y else y - 1
                val rightX = if (vertical) x + 2 else x
                val rightY = if (vertical) y else y + 2
                val nearSide = (i / 6) % 2 == 0
                val sx = if (nearSide) leftX else rightX
                val sy = if (nearSide) leftY else rightY
                if (i % 6 == 3 && region.city) {
                    if (roadside(sx, sy)) {
                        t[sy][sx] = T.TREE.ordinal
                        reserved[sy][sx] = true
                    }
                } else if (i % 13 == 7) {
                    val ox = if (nearSide) rightX else leftX
                    val oy = if (nearSide) rightY else leftY
                    if (roadside(ox, oy)) {
                        t[oy][ox] = (if (region.city) T.LAMP else T.TREE).ordinal
                        reserved[oy][ox] = true
                    }
                }
            }
        }

        for (y in 3 until h - 3) for (x in 3 until w - 3) {
            if (roadside(x, y) && rnd.nextDouble() < 0.22) {
                t[y][x] = T.FLOWER.ordinal
                base[y][x] = T.FLOWER.ordinal
                reserved[y][x] = true
            }
        }

        // 13. 자연물 --------------------------------------------------------------------
        // 지역의 실제 경관을 반영한 작은 군락: 해안은 모래·바위, 습지는 갈대,
        // 강은 자갈과 물억새, 산지는 바위와 침엽수, 도시는 꽃과 가로수.
        // (참고: 한국문화원 관광 자료의 제주 화산암/곶자왈, 순천만 갈대 습지,
        // 우포늪 내륙습지 소개를 바탕으로 한 게임용 단순화.)
        val isWet = region.kind == RegionKind.WETLAND || "wetland" in region.habitats
        val isCoast = region.kind == RegionKind.COAST || "coast" in region.habitats
        val isMountain = region.kind == RegionKind.MOUNTAIN || "mountain" in region.habitats
        val isRiver = region.kind == RegionKind.RIVER || "water" in region.habitats

        // 큰 자연물 군락: 같은 종류를 뭉치되 군락끼리는 충분히 떨어뜨린다.
        val groveCount = when {
            isMountain -> 6
            isWet -> 4
            isCoast -> 3
            else -> 3
        }
        repeat(groveCount) {
            val gx = 5 + rnd.nextInt(w - 10)
            val gy = 5 + rnd.nextInt(h - 10)
            val gr = 2 + rnd.nextInt(2)
            for (y in gy - gr..gy + gr) for (x in gx - gr..gx + gr) {
                if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) continue
                if (reserved[y][x] || t[y][x] != T.GRASS.ordinal) continue
                val dx = x - gx; val dy = y - gy
                if (dx * dx + dy * dy <= gr * gr && rnd.nextFloat() < 0.8f) {
                    val groveTile = when {
                        isWet -> if (rnd.nextBoolean()) T.REED else T.TALLGRASS
                        isCoast -> if (rnd.nextBoolean()) T.ROCK else T.TALLGRASS
                        isRiver -> if (rnd.nextInt(3) == 0) T.REED else T.ROCK
                        else -> T.TREE
                    }
                    t[y][x] = groveTile.ordinal
                    if (groveTile == T.REED || groveTile == T.TALLGRASS) base[y][x] = groveTile.ordinal
                    reserved[y][x] = true
                }
            }
        }

        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            if (reserved[y][x] || t[y][x] != T.GRASS.ordinal) continue
            val r = rnd.nextDouble()
            when {
                // 습지·강은 갈대/물억새를 우선하고, 산은 바위와 숲을 우선한다.
                isWet && r < 0.22 -> { t[y][x] = T.REED.ordinal; base[y][x] = T.REED.ordinal }
                isRiver && r < 0.13 -> t[y][x] = T.ROCK.ordinal
                isCoast && r < 0.12 -> t[y][x] = T.ROCK.ordinal
                isMountain && r < 0.12 -> t[y][x] = T.ROCK.ordinal
                r < region.treeDensity -> t[y][x] = T.TREE.ordinal
                r < region.treeDensity + region.flowerDensity -> {
                    t[y][x] = T.FLOWER.ordinal
                    base[y][x] = T.FLOWER.ordinal
                }
                r < region.treeDensity + region.flowerDensity + region.rockDensity -> t[y][x] = T.ROCK.ordinal
            }
        }

        repeat(4) {
            val bx = 4 + rnd.nextInt(w - 8)
            val by = 4 + rnd.nextInt(h - 8)
            val br = 1 + rnd.nextInt(2)
            for (y in by - br..by + br) for (x in bx - br..bx + br) {
                if (x in 2 until w - 2 && y in 2 until h - 2 && !reserved[y][x] &&
                    t[y][x] == T.GRASS.ordinal && rnd.nextFloat() < 0.75f
                ) {
                    t[y][x] = T.TALLGRASS.ordinal
                    base[y][x] = T.TALLGRASS.ordinal
                }
            }
        }

        return GameMap(region, w, h, t, base, pave, deco, npcs, hasHouse, houseDoorX, houseDoorY)
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
        // 화덕 (기본 제공!) — 화덕피자
        t[2][9] = T.OVEN.ordinal; t[2][10] = T.OVEN.ordinal
        t[3][9] = T.OVEN.ordinal; t[3][10] = T.OVEN.ordinal
        // 가정용 오븐 (화덕 옆 주방 코너) — 일반 피자
        t[2][11] = T.RANGE_TOP.ordinal
        t[3][11] = T.RANGE.ordinal
        // 침대
        t[2][2] = T.BED.ordinal; t[2][3] = T.BED.ordinal
        // 이사 박스
        t[6][2] = T.BOX.ordinal
        // 장식 슬롯 (DECOR 0,1,2 순서로 HomeScene과 매칭)
        t[2][5] = T.DECOR.ordinal
        t[2][7] = T.DECOR.ordinal
        t[5][11] = T.DECOR.ordinal
        val home = Regions.byId["seoul"]!! // 내부맵은 지역 무관 (더미)
        val base = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        val pave = Array(h) { IntArray(w) }
        val deco = Array(h) { IntArray(w) }
        return GameMap(home, w, h, t, base, pave, deco, emptyList(), true, 6, h - 1)
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

    // 머리 위 말풍선 이모트 (♪, …, 💤 등) — WorldScene이 갱신
    var emote: String? = null
    var emoteT = 0f
    var emoteCd = 3f + ((tileX * 37 + tileY * 11) % 7)

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
    var animT = (Math.random() * 3f).toFloat()   // 대기 동작 위상 (고양이마다 다르게)
    var idleT = 1.5f
    var fromX = 0f; var fromY = 0f
    var toX = 0f; var toY = 0f
    var hopT = 0f
    var faceLeft = true

    val cx: Float get() = x + 14f
    val cy: Float get() = y + 12f

    fun update(dt: Float, map: GameMap) {
        animT += dt
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

    val walking: Boolean get() = state == 1
    /** 현재 동작의 진행도 0~1 (걸을 때는 한 칸 이동이 한 사이클) */
    val phase: Float get() = if (state == 1) hopT else (animT / 3.4f) % 1f
    val lift: Float get() = if (state == 1) (sin((hopT * Math.PI).toFloat()) * 1.4f) else 0f
}

/** 플레이어 */
class Player {
    var x = 0f
    var y = 0f
    var facing = Dir.S
    var moving = false
    var bike = false

    // ---- 동작(애니메이션) 상태 ----
    var anim = Anim.IDLE
        private set
    var animT = 0f                    // 현재 클립 안에서의 시간(초)
    var pedal = 0f                    // 자전거 페달 위상 0~1
    private var lastPhase = 0f
    /** 이번 프레임에 발이 땅에 닿았는가 (먼지/발소리용) */
    var footfall = false
        private set

    val cx: Float get() = x + 8f
    val cy: Float get() = y + 13f

    fun set(px: Float, py: Float) { x = px; y = py }

    /** 현재 클립의 진행도 0~1 */
    val phase: Float get() = (animT / anim.cycle).coerceIn(0f, 1f)

    /** 현재 프레임 번호 */
    val frame: Int get() = (animT / anim.frameTime).toInt()

    /**
     * 동작을 재생한다.
     * - 걷기 <-> 달리기 <-> 살금살금 사이에서는 위상을 이어받아 발이 튀지 않는다.
     * - rate 는 실제 이동 속도에 비례시켜 발이 미끄러지지 않게 한다.
     */
    fun play(next: Anim, dt: Float, rate: Float = 1f) {
        if (next != anim) {
            val strideSet = anim == Anim.WALK || anim == Anim.RUN || anim == Anim.SNEAK
            val strideNext = next == Anim.WALK || next == Anim.RUN || next == Anim.SNEAK
            val keep = if (strideSet && strideNext) (animT / anim.cycle) % 1f else 0f
            anim = next
            animT = keep * next.cycle
            lastPhase = keep
        }
        val cyc = anim.cycle
        animT = (animT + dt * rate) % cyc
        val ph = animT / cyc
        // 걷기/달리기 사이클에서 발이 닿는 순간 (위상 0 과 0.5 통과)
        footfall = false
        if (anim == Anim.WALK || anim == Anim.RUN || anim == Anim.SNEAK) {
            if (crossed(lastPhase, ph, 0f) || crossed(lastPhase, ph, 0.5f)) footfall = true
        }
        lastPhase = ph
    }

    private fun crossed(a: Float, b: Float, m: Float): Boolean =
        if (b >= a) (m > a && m <= b) else (m > a || m <= b)

    /** 자전거 페달을 speed(px/s)에 맞춰 돌린다 */
    fun pedalBy(dt: Float, speed: Float) {
        pedal = (pedal + dt * (speed / 46f)) % 1f
    }
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
    var fleeCued = false             // 도망 효과음 재생 여부 (WorldScene에서 사용)
    var faceLeft = true
    var sprW = 16                    // 스프라이트 크기 (생성 시 Assets에서 설정)
    var sprH = 15

    val cx: Float get() = x + sprW / 2f
    val cy: Float get() = y + sprH * 0.45f

    fun update(dt: Float, playerCx: Float, playerCy: Float, onBike: Boolean, sneaking: Boolean, map: GameMap, calmFactor: Float = 1f, bikeScare: Float = 1.4f) {
        val fleeTiles = when (def.tier) {
            Tier.COMMON -> 1.7f
            Tier.UNCOMMON -> 2.3f
            Tier.RARE -> 3.0f
            Tier.LEGEND -> 3.8f
        } * (if (sneaking) 0.6f else 1f) * (if (onBike) bikeScare else 1f) * calmFactor

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
