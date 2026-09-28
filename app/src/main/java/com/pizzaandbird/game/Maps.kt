package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
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
    LM_ROOF(true, bulk = true),          // 지역 랜드마크 지붕
    LM_WALL(true, bulk = true),          // 지역 랜드마크 외벽
    LM_WIN(true, bulk = true),           // 지역 랜드마크 창(아치)
    LANDMARK_DOOR(false),   // 지역 랜드마크 입구 (들어가기)
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

/** 지면 캐시 한 장의 크기: 8×8 타일 = 256×256 px (ARGB8888 256 KiB). */
private const val GROUND_CHUNK_TILES = 8

data class TunnelInfo(
    val dir: Dir,
    val targetId: String,
    val tileX: Int,
    val tileY: Int,
    val cx: Float,
    val cy: Float,
    val number: Int
)

data class ViewpointInfo(
    val tileX: Int,
    val tileY: Int,
    val label: String,
    val height: Int
)

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
    val houseDoorY: Int,
    val mapStyle: RegionMapStyle = RegionMapStyles.forRegion(region),
    val tunnels: List<TunnelInfo> = emptyList(),
    val hasLandmark: Boolean = false,
    val landmarkDoorX: Int = -1,
    val landmarkDoorY: Int = -1,
    /** Climbable contour height in tile steps. Negative values are low wet ground. */
    val elevation: Array<IntArray> = Array(h) { IntArray(w) },
    val viewpoints: List<ViewpointInfo> = emptyList()
) {
    /**
     * 현재 계절 — 나무(벚꽃·단풍·눈)/풀빛/물빛이 계절마다 바뀐다.
     * 바뀌면 지면 캐시를 버리고(틴트가 바뀌었으므로) 다시 굽는다.
     */
    var season: Season = Season.SPRING
        set(value) {
            if (field == value) return
            field = value
            refreshSeasonPaints()
            groundChunks.fill(null)
        }

    private val foliagePaint = Paint().apply { isFilterBitmap = false }
    private val waterPaint = Paint().apply { isFilterBitmap = false }
    private val shorePaint = Paint().apply { isFilterBitmap = false }
    private val stonePaint = Paint().apply { isFilterBitmap = false }
    private val elevationSidePaint = Paint().apply { isAntiAlias = false }
    private val elevationTopPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val viewpointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val viewpointStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f }
    private val viewpointText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3C5D58.toInt()
        textSize = 11f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun refreshSeasonPaints() {
        foliagePaint.colorFilter = PorterDuffColorFilter(
            multiplyTint(mapStyle.foliageFilter, seasonFoliageTint(season)), PorterDuff.Mode.MULTIPLY)
        waterPaint.colorFilter = PorterDuffColorFilter(
            multiplyTint(mapStyle.waterFilter, seasonWaterTint(season)), PorterDuff.Mode.MULTIPLY)
        if (shorePaint.colorFilter == null) {
            shorePaint.colorFilter = PorterDuffColorFilter(mapStyle.shoreFilter, PorterDuff.Mode.MULTIPLY)
            stonePaint.colorFilter = PorterDuffColorFilter(mapStyle.stoneFilter, PorterDuff.Mode.MULTIPLY)
        }
    }

    // 지면·포장·데칼은 맵이 만들어진 후 변하지 않는다. 물이 없는 청크만 처음 보일 때
    // 래스터화해 두면 매 프레임 수백 장의 타일 대신 화면당 몇 장만 그리면 된다.
    // 물이 있는 청크는 원래 코드로 그려 물결·포말·반짝임 애니메이션을 보존한다.
    // 40×30 맵 전체를 방문해도 캐시는 약 4.7 MiB (씬 교체 시 함께 해제).
    private val groundChunkCols = (w + GROUND_CHUNK_TILES - 1) / GROUND_CHUNK_TILES
    private val groundChunks = arrayOfNulls<Bitmap>(groundChunkCols * ((h + GROUND_CHUNK_TILES - 1) / GROUND_CHUNK_TILES))
    private val animatedGroundChunks = BooleanArray(groundChunks.size) { index ->
        val sx = index % groundChunkCols * GROUND_CHUNK_TILES
        val sy = index / groundChunkCols * GROUND_CHUNK_TILES
        var containsWater = false
        for (y in sy until minOf(sy + GROUND_CHUNK_TILES, h)) {
            for (x in sx until minOf(sx + GROUND_CHUNK_TILES, w)) {
                if (ground[y][x] == T.WATER.ordinal) containsWater = true
            }
        }
        containsWater
    }

    init {
        refreshSeasonPaints()
    }

    private fun terrainPaint(tile: T, fallback: Paint): Paint = when (tile) {
        T.GRASS, T.TALLGRASS, T.FLOWER, T.REED, T.TREE -> foliagePaint
        T.WATER -> waterPaint
        T.SAND -> shorePaint
        T.ROCK, T.MOUNTAIN -> stonePaint
        else -> fallback
    }

    /**
     * A region chooses its own silhouettes; non-nature tiles keep the global tile pattern.
     * 지역 표가 아트 수를 넘어섰더라도 크래시하지 않도록 항상 실제 범위로 눌러 준다.
     */
    private fun artVariant(a: Assets, tile: T, x: Int, y: Int): Int {
        val picked = mapStyle.natureArt.variant(tile, x, y, region.id) ?: a.tileVariant(tile.ordinal, x, y)
        return picked.coerceIn(0, a.tiles[tile.ordinal].size - 1)
    }

    /** 이 칸에 그려질 바위 변형 — 숨김 판정이 함께 쓴다. */
    fun rockLook(x: Int, y: Int): Int =
        mapStyle.natureArt.variant(T.ROCK, x, y, region.id)
            ?.coerceIn(0, PropLooks.ROCK_COUNT - 1) ?: 0

    private val exits: Map<Dir, String> = Regions.exits(region.id)

    /** 이정표 표시 기준점(지도 렌더 좌표, 32px 타일). NaN이면 항상 보인다. */
    var signViewerX = Float.NaN
    var signViewerY = Float.NaN

    /** 이정표 불투명도 0..1 — 가까이 가야만 나타나고, 멀어지면 서서히 사라진다. */
    fun signAlpha(x: Int, y: Int): Float {
        if (signViewerX.isNaN() || signViewerY.isNaN()) return 1f
        val dx = x * 32f + 16f - signViewerX
        val dy = y * 32f + 24f - signViewerY
        val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        return ((SIGN_FADE_FAR - d) / (SIGN_FADE_FAR - SIGN_FADE_NEAR)).coerceIn(0f, 1f)
    }

    fun t(x: Int, y: Int): T {
        if (x < 0 || y < 0 || x >= w || y >= h) return T.MOUNTAIN
        return T.ALL[tiles[y][x]]
    }

    /** 터널 타일이 실제로 연결되는 지도 방향 (북쪽 위 · 동쪽 오른쪽 기준). */
    fun tunnelDirectionAt(x: Int, y: Int): Dir? {
        if (t(x, y) != T.TUNNEL) return null
        return when {
            y == 0 && Dir.N in exits -> Dir.N
            y == h - 1 && Dir.S in exits -> Dir.S
            x == 0 && Dir.W in exits -> Dir.W
            x == w - 1 && Dir.E in exits -> Dir.E
            else -> null
        }
    }

    private fun signDirectionAt(x: Int, y: Int): Dir? = when {
        y <= 2 && Dir.N in exits -> Dir.N
        y >= h - 5 && Dir.S in exits -> Dir.S
        x <= 3 && Dir.W in exits -> Dir.W
        x >= w - 4 && Dir.E in exits -> Dir.E
        else -> null
    }

    /** 이 칸의 지면 (포장 아래에 깔린 자연 지형) */
    fun groundAt(x: Int, y: Int): T {
        if (x < 0 || y < 0 || x >= w || y >= h) return T.GRASS
        return T.ALL[ground[y][x]]
    }

    /** 지형의 상대 높이. 0은 평지, 양수는 단차가 있는 둔덕·데크, 음수는 저지대다. */
    fun elevationAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= w || y >= h) return 0
        return elevation[y][x]
    }

    /** 계단/경사로를 벗어나 한 번에 절벽을 오르지 못하게 한다. */
    fun canTraverse(fromPx: Float, fromPy: Float, toPx: Float, toPy: Float): Boolean {
        val fromX = ((fromPx + 8f) / 16f).toInt()
        val fromY = ((fromPy + 13f) / 16f).toInt()
        val toX = ((toPx + 8f) / 16f).toInt()
        val toY = ((toPy + 13f) / 16f).toInt()
        if (fromX == toX && fromY == toY) return true
        return kotlin.math.abs(elevationAt(fromX, fromY) - elevationAt(toX, toY)) <= 1
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

    /**
     * 한 칸의 시야 차폐 품질 0..1 — 지형지물마다 은폐(숨을 곳) 특성이 다르다.
     * (생태학 근거·출처는 docs/BIRD_ECOLOGY.md "지형지물 은엄폐" — 로벨 시야차단량,
     *  숨을 곳/도피 커버 구분, 플라이트 이니시에이션 디스턴스와 커버의 관계)
     *
     * - 갈대 군락(0.95): 줄기가 밀생한 수생식생 — 새 눈높이에서 시야 차단이 거의 완전하다.
     *   뜸부기·개개비류가 갈대 뒤에서 가장 가까이 접근을 허용하는 전형적인 숨을 곳.
     * - 산·건물 등 부피 구조물(0.9): 샘플된 시선을 완전히 끊는 단단한 덩어리.
     * - 나무(0.85): 수관 + 굵은 줄기 — 몸통은 가리지만 줄기 사이 틈이 남는 도피 커버.
     * - 바위: 크기급(`PropSize`)마다 다르다 — 큰 바위(0.8)는 지면 높이의 단단한 수평
     *   차폐(머리 위로는 틈이 있어 내려다보는 맹금류의 시선에는 약하고), 허리 높이 낮은
     *   돌(0.55)은 웅크려야 부분 차폐, **발목만 넘는 자갈(PropSize.PEBBLE, 0) 뒤에는
     *   숨지 못한다** — 자갈 더미를 피해 새에게 다가가야 한다.
     * - 키 큰 풀(0.4): 로벨폴 기준 중간 수준의 부분 차폐 — 웅크린 자세만 가린다.
     * - 벤치·가로등·이정표·흙길·광장: 0 — 숨기엔 키가 낮거나 틈이 없지 않다.
     */
    fun concealmentAt(x: Int, y: Int): Float = when (t(x, y)) {
        T.REED -> 0.95f
        T.TREE -> 0.85f
        T.ROCK -> when (PropLooks.rockSize(rockLook(x, y))) {
            PropSize.PEBBLE -> 0f
            PropSize.LOW -> 0.55f
            PropSize.TALL -> 0.8f
        }
        T.TALLGRASS -> 0.4f
        else -> if (t(x, y).bulk) 0.9f else 0f
    }

    /** 시야를 가리는 지형지물 칸인가 (차폐 품질이 0보다 크면 몸을 숨길 수 있다) */
    fun occludesSight(x: Int, y: Int): Boolean = concealmentAt(x, y) > 0f

    /**
     * 두 월드 좌표(16px 논리 좌표) 사이 시선의 총 차폐 품질 0..1.
     * 새 → 플레이어 사이에 지형지물이 있으면 플레이어는 '숨은' 상태가 된다.
     *
     * 여러 지형지물이 겹치면 시야가 완전히 끊길 확률이 커진다 — 화면을 통과하는 빛의
     * 비율(1−차폐)을 칸마다 곱해, 남은 틈을 다시 차폐로 환산한다(투과율 합성).
     * 한 칸은 시선이 여러 번 지나가도 한 번만 센다.
     */
    fun concealmentAlong(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 12f) return 0f
        val steps = (dist / 5f).toInt().coerceAtLeast(2)
        var light = 1f
        var lastTx = Int.MIN_VALUE
        var lastTy = Int.MIN_VALUE
        for (i in 1 until steps) {
            val f = i.toFloat() / steps
            val tx = ((x0 + dx * f) / 16f).toInt()
            val ty = ((y0 + dy * f) / 16f).toInt()
            if (tx == lastTx && ty == lastTy) continue   // 한 칸은 한 번만 센다 (시선은 격자를 단조로 지나간다)
            lastTx = tx; lastTy = ty
            val q = concealmentAt(tx, ty)
            if (q > 0f) {
                light *= 1f - q
                if (light <= 0.02f) return 1f
            }
        }
        return 1f - light
    }

    /**
     * 두 월드 좌표 사이에 시야를 가리는 지형지물이 있는지 확인한다.
     * 바위·나무·갈대 같은 지형지물이 사이에 있으면 플레이어는 '숨은' 상태가 된다.
     */
    fun isOccluded(x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
        concealmentAlong(x0, y0, x1, y1) > 0f

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

    /** 정적인 타일에는 래스터화된 청크를, 애니메이션 물이 있는 청크에는 기존 타일 패스를 사용. */
    private fun drawGround(c: Canvas, a: Assets, camX: Float, camY: Float,
                           x0: Int, y0: Int, x1: Int, y1: Int, time: Float, waterFrame: Int) {
        if (x0 > x1 || y0 > y1) return
        for (cy in y0 / GROUND_CHUNK_TILES..y1 / GROUND_CHUNK_TILES) {
            for (cx in x0 / GROUND_CHUNK_TILES..x1 / GROUND_CHUNK_TILES) {
                val index = cy * groundChunkCols + cx
                val sx = cx * GROUND_CHUNK_TILES
                val sy = cy * GROUND_CHUNK_TILES
                if (animatedGroundChunks[index]) {
                    for (y in maxOf(y0, sy)..minOf(y1, sy + GROUND_CHUNK_TILES - 1)) {
                        for (x in maxOf(x0, sx)..minOf(x1, sx + GROUND_CHUNK_TILES - 1)) {
                            drawGroundTile(c, a, x, y, camX, camY, time, waterFrame)
                        }
                    }
                } else {
                    val bmp = groundChunks[index] ?: buildGroundChunk(a, sx, sy).also { groundChunks[index] = it }
                    c.drawBitmap(bmp, sx * 32f - camX, sy * 32f - camY, a.sprPaint)
                }
            }
        }
    }

    private fun buildGroundChunk(a: Assets, sx: Int, sy: Int): Bitmap {
        val xEnd = minOf(sx + GROUND_CHUNK_TILES, w)
        val yEnd = minOf(sy + GROUND_CHUNK_TILES, h)
        val bmp = Bitmap.createBitmap((xEnd - sx) * 32, (yEnd - sy) * 32, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (y in sy until yEnd) for (x in sx until xEnd) {
            drawGroundTile(canvas, a, x, y, sx * 32f, sy * 32f, 0f, 0)
        }
        return bmp
    }

    /**
     * 촬영용 3D 지면 텍스처. 구조물은 사진 렌더러가 높이를 주어 따로 세우므로
     * 건물 밑 지면까지 채운다. 도로 오토타일·물가·지역 팔레트는 월드와 공유한다.
     * 16px/칸으로 제한해 셔터 한 번에 전체 해상도 월드 비트맵을 만들지 않는다.
     */
    internal fun photoGroundTexture(a: Assets, time: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(w * 16, h * 16, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(0.5f, 0.5f)
        val waterFrame = ((time * 2.2f).toInt() % 4 + 4) % 4
        for (y in 0 until h) for (x in 0 until w) {
            drawGroundTile(canvas, a, x, y, 0f, 0f, time, waterFrame, coveredGround = true)
        }
        return bitmap
    }

    /** 지면 → 포장 → 데칼 순서는 캐시와 움직이는 물 모두 동일해야 한다. */
    private fun drawGroundTile(c: Canvas, a: Assets, x: Int, y: Int,
                               camX: Float, camY: Float, time: Float, waterFrame: Int,
                               coveredGround: Boolean = false) {
        val fx = x * 32f - camX
        val fy = y * 32f - camY
        val tv = tiles[y][x]
        val tile = T.ALL[tv]
        val pv = paving[y][x]

        // 1) 지면 — 포장/소품 아래에 깔린다 (불투명한 구조물 아래는 생략)
        if (coveredGround || pv != Pave.NONE || tile.ground || tile.prop || tile == T.OVEN) {
            // 겨울엔 꽃밭이 진다 — 마른 잔디로 읽힌다 (눈은 WorldFx가 덮는다)
            var gv = ground[y][x]
            var gTile = T.ALL[gv]
            if (season == Season.WINTER && gTile == T.FLOWER) {
                gv = T.GRASS.ordinal
                gTile = T.GRASS
            }
            val gBmp = if (gTile == T.WATER) a.tiles[gv][minOf(waterFrame, a.tiles[gv].size - 1)]
            else a.tiles[gv][artVariant(a, gTile, x, y)]
            c.drawBitmap(gBmp, fx, fy, terrainPaint(gTile, a.sprPaint))

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

    /**
     * Raised decks and wet lowlands use a small orthographic lip instead of a flat
     * color change. It stays in the tile plane so existing sprites and collision
     * coordinates remain compatible with old saves.
     */
    private fun drawElevationRelief(c: Canvas, camX: Float, camY: Float, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0..y1) for (x in x0..x1) {
            val level = elevationAt(x, y)
            val fx = x * 32f - camX
            val fy = y * 32f - camY
            if (level > 0) {
                val lip = (level * 4f).coerceAtMost(12f)
                val below = elevationAt(x, y + 1)
                if (below < level) {
                    elevationSidePaint.color = Color.argb(34 + level * 12, 48, 67, 62)
                    c.drawRect(fx + 2f, fy + 27f, fx + 30f, fy + 32f + lip, elevationSidePaint)
                    elevationTopPaint.color = Color.argb(125, 250, 247, 218)
                    c.drawLine(fx + 2f, fy + 27f, fx + 30f, fy + 27f, elevationTopPaint)
                }
                val right = elevationAt(x + 1, y)
                if (right < level) {
                    elevationSidePaint.color = Color.argb(24 + level * 9, 42, 61, 57)
                    c.drawRect(fx + 27f, fy + 3f, fx + 32f + lip, fy + 29f, elevationSidePaint)
                }
            } else if (level < 0 && elevationAt(x, y - 1) >= 0) {
                // A soft darker rim makes floodplain / tidal flats read as lower ground.
                elevationSidePaint.color = Color.argb(32, 65, 116, 110)
                c.drawRect(fx + 1f, fy + 1f, fx + 31f, fy + 4f, elevationSidePaint)
            }
        }
    }

    /** Small railings and a flag make the destination of a climb legible from below. */
    private fun drawViewpointMarkers(c: Canvas, camX: Float, camY: Float, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (view in viewpoints) {
            if (view.tileX !in x0 - 1..x1 + 1 || view.tileY !in y0 - 1..y1 + 1) continue
            val sx = view.tileX * 32f - camX
            val sy = view.tileY * 32f - camY
            viewpointPaint.color = 0xFF6A8E83.toInt()
            c.drawRect(sx + 5f, sy + 13f, sx + 27f, sy + 16f, viewpointPaint)
            viewpointStroke.color = 0xFFD8E9D5.toInt()
            for (px in 7..25 step 6) c.drawLine(sx + px, sy + 7f, sx + px, sy + 15f, viewpointStroke)
            c.drawLine(sx + 7f, sy + 7f, sx + 25f, sy + 7f, viewpointStroke)
            viewpointPaint.color = 0xFF4F746A.toInt()
            c.drawRect(sx + 15f, sy + 3f, sx + 16.5f, sy + 14f, viewpointPaint)
            viewpointPaint.color = 0xFFF2B63C.toInt()
            val flag = Path()
            flag.moveTo(sx + 16f, sy + 3f); flag.lineTo(sx + 26f, sy + 6f); flag.lineTo(sx + 16f, sy + 9f); flag.close()
            c.drawPath(flag, viewpointPaint)
            if (view.height >= 2) {
                viewpointText.color = Color.argb(210, 53, 82, 76)
                c.drawText("전망", sx + 16f, sy - 3f, viewpointText)
            }
        }
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

        drawGround(c, a, camX, camY, x0, y0, x1, y1, time, waterFrame)
        drawElevationRelief(c, camX, camY, x0, y0, x1, y1)
        drawViewpointMarkers(c, camX, camY, x0, y0, x1, y1)

        // 3.5) 햇빛 그림자 — 해의 위치(시각)에 따라 나무·가로등·이정표의 긴 그림자가 돌아간다
        if (sunAlpha > 0 && sunLen > 0f) {
            sunPaint.color = Color.argb(sunAlpha, 18, 30, 22)
            val k = sunDx / sunLen
            // 화면 바로 위/옆의 소품도 그림자가 화면 안으로 드리울 수 있다
            for (y in (y0 - 2).coerceAtLeast(0)..y1) {
                for (x in (x0 - 1).coerceAtLeast(0)..(x1 + 1).coerceAtMost(w - 1)) {
                    val tile = T.ALL[tiles[y][x]]
                    if (tile != T.TREE && tile != T.LAMP && tile != T.SIGN && tile != T.ROCK) continue
                    val shadowK = if (tile == T.SIGN) signAlpha(x, y) else 1f
                    if (shadowK <= 0f) continue
                    sunPaint.alpha = (sunAlpha * shadowK).toInt()
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
                    // 나무는 계절마다 벚꽃·푸른 잎·단풍·눈으로 갈아입는다
                    val bmp = if (tile == T.RANGE) a.tiles[tv][minOf(ovenFrame, a.tiles[tv].size - 1)]
                    else if (tile == T.TREE) {
                        val base = artVariant(a, tile, x, y)
                        a.tiles[tv][a.seasonTreeIndex(base, season, x, y)]
                    } else a.tiles[tv][artVariant(a, tile, x, y)]
                    if (tile == T.SIGN) {
                        val k = signAlpha(x, y)
                        if (k <= 0f) continue
                        signPaint.alpha = (255 * k).toInt()
                        c.drawBitmap(bmp, fx, fy, signPaint)
                        signDirectionAt(x, y)?.let { drawSignArrow(c, fx, fy, it, k) }
                        continue
                    }
                    when (tile) {
                        T.TUNNEL -> {
                            val edge = tunnelDirectionAt(x, y)
                            if (edge == null) c.drawBitmap(bmp, fx, fy, a.sprPaint)
                            else drawTunnel(c, bmp, fx, fy, edge, a.sprPaint)
                        }
                        else -> c.drawBitmap(bmp, fx, fy, terrainPaint(tile, a.sprPaint))
                    }
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
        drawCoastLabels(c, camX, camY)
    }

    /** 원본 터널 아치는 남쪽(화면 아래)을 향한다. 진입로 안쪽으로 입구를 돌려 놓는다. */
    private fun drawTunnel(c: Canvas, bmp: android.graphics.Bitmap, x: Float, y: Float, edge: Dir, paint: Paint) {
        val rotation = when (edge) {
            Dir.N -> 0f      // 북쪽 경계: 입구는 남쪽을 향해 마을 안으로
            Dir.E -> 90f     // 동쪽 경계: 서쪽을 향해
            Dir.S -> 180f    // 남쪽 경계: 북쪽을 향해
            Dir.W -> -90f    // 서쪽 경계: 동쪽을 향해
        }
        c.save()
        c.rotate(rotation, x + 16f, y + 16f)
        c.drawBitmap(bmp, x, y, paint)
        c.restore()
    }

    /** 이정표는 세워 둔 채, 판자의 화살표만 연결 터널 방향으로 돌린다. */
    private fun drawSignArrow(c: Canvas, x: Float, y: Float, direction: Dir, alpha: Float = 1f) {
        val ai = (255 * alpha).toInt()
        signArrowPaint.alpha = ai
        signArrowLinePaint.alpha = ai
        val (dx, dy) = when (direction) {
            Dir.N -> 0f to -1f
            Dir.E -> 1f to 0f
            Dir.S -> 0f to 1f
            Dir.W -> -1f to 0f
        }
        val cx = x + 22f
        val cy = y + 9f
        c.drawLine(cx - dx * 4f, cy - dy * 4f, cx + dx * 1.8f, cy + dy * 1.8f, signArrowLinePaint)
        val tipX = cx + dx * 4.8f
        val tipY = cy + dy * 4.8f
        val baseX = cx + dx * 1.1f
        val baseY = cy + dy * 1.1f
        val px = -dy * 2.3f
        val py = dx * 2.3f
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(baseX + px, baseY + py)
        arrowPath.lineTo(baseX - px, baseY - py)
        arrowPath.close()
        c.drawPath(arrowPath, signArrowPaint)
    }

    /** 실제 바다 방향이 눈에 들어오도록 가장자리 바다에 낮은 대비로 이름을 새긴다. */
    private fun drawCoastLabels(c: Canvas, camX: Float, camY: Float) {
        if (region.waterEdges.isEmpty()) return
        for (edge in region.waterEdges) {
            val label = when (edge) {
                Dir.W -> when {
                    region.id == "jeju" -> "서쪽 바다"
                    "wetland" in region.habitats -> "서해 갯벌"
                    else -> "서해"
                }
                Dir.E -> if (region.id == "jeju" || region.id == "hadori") "동쪽 바다" else "동해"
                Dir.S -> when {
                    region.id == "jeju" -> "남쪽 바다"
                    Dir.W in region.waterEdges -> "남쪽 해안"
                    else -> "남해"
                }
                Dir.N -> if (region.id == "jeju") "제주해협" else "바다"
            }
            val centerX: Float
            val centerY: Float
            when (edge) {
                Dir.W -> { centerX = 48f; centerY = h * 32f * 0.38f }
                Dir.E -> { centerX = w * 32f - 48f; centerY = h * 32f * 0.38f }
                Dir.N -> { centerX = w * 32f * 0.72f; centerY = 48f }
                Dir.S -> { centerX = w * 32f * 0.72f; centerY = h * 32f - 48f }
            }
            val screenX = centerX - camX
            val screenY = centerY - camY
            val halfW = seaLabelPaint.measureText(label) / 2f + 7f
            seaBadgeRect.set(screenX - halfW, screenY - 10f, screenX + halfW, screenY + 10f)
            c.drawRoundRect(seaBadgeRect, 8f, 8f, seaBadgePaint)
            c.drawText(label, screenX, screenY + 4.5f, seaLabelPaint)
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
        private val foamPaint by lazy { Paint().apply { color = Color.argb(150, 226, 244, 250) } }
        private val foamDot by lazy { Paint() }
        private val sparkle by lazy { Paint() }
        private val sunPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val sunRect by lazy { RectF() }
        private val signPaint by lazy { Paint() }
        /** 이 거리(지도 렌더 px) 안에서는 이정표가 완전히 보인다 — 약 2.5칸 */
        private const val SIGN_FADE_NEAR = 80f
        /** 이 거리 밖에서는 이정표가 보이지 않는다 — 약 4칸 */
        private const val SIGN_FADE_FAR = 128f
        private val signArrowPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4A3728.toInt()
            style = Paint.Style.FILL
        } }
        private val signArrowLinePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4A3728.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
        } }
        private val arrowPath by lazy { Path() }
        private val seaBadgePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(92, 223, 244, 246)
        } }
        private val seaBadgeRect by lazy { RectF() }
        private val seaLabelPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(188, 44, 83, 100)
            textSize = 13f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } }
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
     * [ownedHomeRegions]에는 이미 매입한 지역 집을 모두 넘긴다. 예전에는
     * [homeRegion]의 집만 맵에 세워서, 다른 지역에 매입해 둔 집은 문 자체가
     * 생기지 않아 들어갈 수 없었다. 기본값은 기존 호출부/테스트 호환을 위해
     * 현재 정착지 한 채만 가진 것으로 둔다.
     *
     * 길 설계
     *  - 남북/동서로 **2칸 폭 간선도로**가 지나고, 가운데에서 팔각 광장으로 모인다.
     *  - 간선은 완전한 직선이 아니라 구간마다 살짝 사행(蛇行)한다. 다만 터널·광장
     *    진입부는 반드시 직선으로 들어가 길이 어색하게 꺾이지 않는다.
     *  - 막다른 방향(출구가 없는 쪽)은 회차 공간(컬드삭)으로 마무리한다.
     *  - 건물 정문·호숫가 데크·숲속 쉼터까지 1칸 폭 샛길이 뻗는다.
     *  - 중심선을 따라 가로수와 가로등이 번갈아 도열한다.
     */
    fun build(
        region: RegionDef,
        homeRegion: String,
        ownedHomeRegions: Set<String> = setOf(homeRegion)
    ): GameMap {
        val w = region.mapW
        val h = region.mapH
        val t = Array(h) { IntArray(w) { T.GRASS.ordinal } }
        val base = Array(h) { IntArray(w) { T.GRASS.ordinal } }
        val pave = Array(h) { IntArray(w) }
        val deco = Array(h) { IntArray(w) }
        val elevation = Array(h) { IntArray(w) }
        val reserved = Array(h) { BooleanArray(w) }
        val structure = Array(h) { BooleanArray(w) }      // 길이 뚫고 지나갈 수 없는 칸
        val rnd = Random(region.id.hashCode().toLong())
        val exits = Regions.exits(region.id)
        val mapStyle = RegionMapStyles.forRegion(region)

        fun inb(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h
        fun setGround(x: Int, y: Int, tile: T) {
            t[y][x] = tile.ordinal
            base[y][x] = tile.ordinal
        }

        // 1. 바다/모래 가장자리 — 방향을 그대로 반영: 서쪽은 서해, 동쪽은 동해.
        // 바다는 지도 바깥쪽, 모래·갯벌은 육지 쪽에 놓아 해안의 순서가 뒤집히지 않게 한다.
        fun edgeBand(d: Dir, offset: Int, depth: Int, tile: T) {
            when (d) {
                Dir.N -> for (y in offset until (offset + depth).coerceAtMost(h)) for (x in 0 until w) setGround(x, y, tile)
                Dir.S -> for (y in (h - offset - depth).coerceAtLeast(0) until h - offset) for (x in 0 until w) setGround(x, y, tile)
                Dir.W -> for (x in offset until (offset + depth).coerceAtMost(w)) for (y in 0 until h) setGround(x, y, tile)
                Dir.E -> for (x in (w - offset - depth).coerceAtLeast(0) until w - offset) for (y in 0 until h) setGround(x, y, tile)
            }
        }
        for (d in region.waterEdges) edgeBand(d, 0, mapStyle.seaDepth, T.WATER)
        for (d in region.sandEdges) {
            val inlandOffset = if (d in region.waterEdges) mapStyle.seaDepth else 0
            edgeBand(d, inlandOffset, mapStyle.shoreDepth, T.SAND)
        }

        // 2. 육지 가장자리 (산맥/수림) -----------------------------------------
        val borderTile = if (region.rockDensity >= 0.08) T.MOUNTAIN else T.TREE
        for (y in 0 until h) for (x in 0 until w) {
            val onEdge = x < 2 || y < 2 || x >= w - 2 || y >= h - 2
            if (onEdge && t[y][x] == T.GRASS.ordinal) t[y][x] = borderTile.ordinal
        }

        // 3. 호수·하천·습지 — 지역별 실제 지형의 방향과 위치를 반영한다. --------
        var lakeShoreX = -1
        var lakeShoreY = -1
        val lake = mapStyle.lake
        if (lake != null) {
            val cx = lake.center.x
            val cy = lake.center.y
            val rx = lake.radiusX
            val ry = lake.radiusY
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

        fun segmentDistanceSq(px: Float, py: Float, a: MapPoint, b: MapPoint): Float {
            val dx = (b.x - a.x).toFloat()
            val dy = (b.y - a.y).toFloat()
            val len2 = dx * dx + dy * dy
            val u = if (len2 <= 0.001f) 0f else (((px - a.x) * dx + (py - a.y) * dy) / len2).coerceIn(0f, 1f)
            val ex = px - (a.x + dx * u)
            val ey = py - (a.y + dy * u)
            return ex * ex + ey * ey
        }

        for (river in mapStyle.rivers) {
            if (river.course.size < 2) continue
            for (y in 2 until h - 2) for (x in 2 until w - 2) {
                if (reserved[y][x] && t[y][x] != T.WATER.ordinal) continue
                var distance = Float.MAX_VALUE
                for (i in 0 until river.course.lastIndex) {
                    distance = minOf(distance, segmentDistanceSq(x.toFloat(), y.toFloat(), river.course[i], river.course[i + 1]))
                }
                val waterRadius2 = river.halfWidth * river.halfWidth
                val bankRadius = river.halfWidth + river.bankWidth
                when {
                    distance <= waterRadius2 -> {
                        setGround(x, y, T.WATER)
                        reserved[y][x] = true
                    }
                    distance <= bankRadius * bankRadius && t[y][x] == T.GRASS.ordinal -> {
                        setGround(x, y, if (river.reedBanks) T.REED else T.TALLGRASS)
                        reserved[y][x] = true
                    }
                }
            }
        }

        fun isRiverWater(x: Int, y: Int): Boolean = mapStyle.rivers.any { river ->
            if (river.course.size < 2) false
            else {
                val radius2 = river.halfWidth * river.halfWidth
                (0 until river.course.lastIndex).any { i ->
                    segmentDistanceSq(x.toFloat(), y.toFloat(), river.course[i], river.course[i + 1]) <= radius2
                }
            }
        }

        // 서해 갯벌과 하구는 모래 안쪽에 갈대 띠가 이어진다. 간선도로가 지나갈 칸은 남긴다.
        if (mapStyle.tidalReeds) {
            for (edge in region.waterEdges) {
                for (along in 5 until (if (edge == Dir.N || edge == Dir.S) w - 5 else h - 5)) {
                    val (rx, ry) = when (edge) {
                        Dir.N -> (along to mapStyle.seaDepth + mapStyle.shoreDepth)
                        Dir.S -> (along to h - mapStyle.seaDepth - mapStyle.shoreDepth - 1)
                        Dir.W -> (mapStyle.seaDepth + mapStyle.shoreDepth to along)
                        Dir.E -> (w - mapStyle.seaDepth - mapStyle.shoreDepth - 1 to along)
                    }
                    val cityBlock = region.city && (rx in 5..9 || rx in 30..34)
                    val nearBlockRows = ry in 3..10 || ry in 23..27
                    if (inb(rx, ry) && !(cityBlock && nearBlockRows) && !reserved[ry][rx] &&
                        t[ry][rx] == T.GRASS.ordinal && (along * 7 + rx * 3 + ry) % 4 != 0
                    ) {
                        setGround(rx, ry, T.REED)
                        reserved[ry][rx] = true
                    }
                }
            }
        }

        // 3.5 각 도시의 실제 지형 — 한눈에 다른 땅모양 ----------------------------
        // 중앙 광장(17..25, 12..18)과 십자 간선(19,20 / 15,16), 도시 블록·집을 비워 둔다.
        val northY0 = mapStyle.northBuildingY
        fun isPlazaOrRoad(x: Int, y: Int): Boolean =
            (x in PLAZA_X0 - 1..PLAZA_X1 + 1 && y in PLAZA_Y0 - 1..PLAZA_Y1 + 1) ||
                (x in 19..20 && y in 2..27) || (y in 15..16 && x in 2..37) ||
                // 도시 4블록 자리 (건물이 들어갈 곳)
                (x in 6..8 && y in northY0..northY0 + 2) ||
                (x in 31..33 && y in northY0..northY0 + 2) ||
                (x in 6..8 && y in 24..26) ||
                (x in 31..33 && y in 24..26) ||
                // 우리 집 자리 (서울 시작)
                (x in 20..26 && y in 8..12)

        fun placeMountain(x: Int, y: Int) {
            if (!inb(x, y) || isPlazaOrRoad(x, y)) return
            if (base[y][x] == T.WATER.ordinal) return
            t[y][x] = T.MOUNTAIN.ordinal
            structure[y][x] = true
            reserved[y][x] = true
        }
        fun placeRock(x: Int, y: Int) {
            if (!inb(x, y) || isPlazaOrRoad(x, y)) return
            if (base[y][x] == T.WATER.ordinal) return
            if (t[y][x] == T.MOUNTAIN.ordinal) return
            t[y][x] = T.ROCK.ordinal
            reserved[y][x] = true
        }
        /**
         * 바위 옆에 동행 돌을 하나 얹는다 — 3~4칸짜리 돌무더기로 읽히게 한다.
         * 한 칸짜리 바위를 등간격으로 뿌리면 '붙여놓은 돌' 같아서 동물을 숨길 때도
         * 주변이 어중간한 얼룩이 된다. 크기가 다른 바위가 겹치면 하나의 노두로 보인다.
         */
        val ROCK_OFFSETS = arrayOf(
            intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1),
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
        )
        fun rockBuddy(x: Int, y: Int) {
            if (rnd.nextFloat() > 0.42f) return
            val step = ROCK_OFFSETS[rnd.nextInt(8)]
            val bx = x + step[0]
            val by = y + step[1]
            if (bx < 2 || by < 2 || bx >= w - 2 || by >= h - 2) return
            if (reserved[by][bx] || t[by][bx] != T.GRASS.ordinal) return
            if (base[by][bx] == T.WATER.ordinal || pave[by][bx] != Pave.NONE) return
            placeRock(bx, by)
        }
        fun placeTree(x: Int, y: Int) {
            if (!inb(x, y) || isPlazaOrRoad(x, y)) return
            if (base[y][x] == T.WATER.ordinal) return
            if (t[y][x] != T.GRASS.ordinal) return
            t[y][x] = T.TREE.ordinal
            reserved[y][x] = true
        }
        fun placeSandGround(x: Int, y: Int) {
            if (!inb(x, y) || isPlazaOrRoad(x, y)) return
            setGround(x, y, T.SAND)
            reserved[y][x] = true
        }
        fun hillCone(cx: Int, cy: Int, rad: Int) {
            for (y in cy - rad - 1..cy + rad + 1) for (x in cx - rad - 1..cx + rad + 1) {
                if (!inb(x, y) || isPlazaOrRoad(x, y)) continue
                if (base[y][x] == T.WATER.ordinal) continue
                val dx = x - cx; val dy = y - cy
                val d2 = dx * dx + dy * dy
                when {
                    d2 <= rad * rad * 0.35f -> placeMountain(x, y)
                    d2 <= rad * rad * 0.75f -> placeRock(x, y)
                    d2 <= (rad + 1) * (rad + 1) -> if (t[y][x] == T.GRASS.ordinal) placeTree(x, y)
                }
            }
        }
        fun islandInSea(cx: Int, cy: Int, rad: Int) {
            for (y in cy - rad - 1..cy + rad + 1) for (x in cx - rad - 1..cx + rad + 1) {
                if (!inb(x, y)) continue
                val dx = x - cx; val dy = y - cy
                val d2 = dx * dx + dy * dy
                if (d2 <= rad * rad) {
                    if (d2 <= rad * rad * 0.28f) {
                        t[y][x] = T.ROCK.ordinal; base[y][x] = T.SAND.ordinal; reserved[y][x] = true
                    } else {
                        setGround(x, y, T.SAND); // base already SAND
                        if (d2 > rad * rad * 0.75f) {
                            // 해변가 풀 한두 개 — 자연스럽게
                            if ((x + y) % 5 == 0) { t[y][x] = T.TALLGRASS.ordinal; base[y][x] = T.TALLGRASS.ordinal }
                        }
                    }
                }
            }
        }
        fun ridgeLine(points: List<Pair<Int, Int>>, width: Int) {
            for ((x, y) in points) for (dy in -width..width) for (dx in -width..width) {
                if (dx * dx + dy * dy <= width * width) {
                    if (rnd.nextFloat() < 0.82f) placeMountain(x + dx, y + dy)
                    else placeRock(x + dx, y + dy)
                }
            }
        }
        fun forestBelt(x0: Int, x1: Int, y0: Int, y1: Int, density: Double) {
            for (y in y0..y1) for (x in x0..x1) {
                if (isPlazaOrRoad(x, y)) continue
                if (!inb(x, y) || t[y][x] != T.GRASS.ordinal || base[y][x] == T.WATER.ordinal) continue
                if (reserved[y][x]) continue
                if (rnd.nextDouble() < density) placeTree(x, y)
            }
        }
        fun inlandCoastalForest(density: Double) {
            val edges = (region.waterEdges + region.sandEdges).distinct()
            val inland = mapStyle.seaDepth + mapStyle.shoreDepth + 1
            for (edge in edges) when (edge) {
                Dir.W -> forestBelt(inland, (inland + 5).coerceAtMost(w - 4), 4, h - 5, density)
                Dir.E -> forestBelt((w - inland - 6).coerceAtLeast(3), w - inland - 1, 4, h - 5, density)
                Dir.N -> forestBelt(4, w - 5, inland, (inland + 4).coerceAtMost(h - 4), density)
                Dir.S -> forestBelt(4, w - 5, (h - inland - 5).coerceAtLeast(3), h - inland - 1, density)
            }
        }

        when (region.id) {
            "seoul" -> {
                // 북한산 — 북서쪽 봉우리 2개가 한강을 굽어본다
                hillCone(7, 4, 3); hillCone(11, 5, 2)
                // 남산 — 한강 바로 남쪽, 도심 한가운데 동그란 숲 언덕 (광장 바로 남쪽)
                hillCone(22, 19, 2)
                hillCone(27, 13, 2) // 동남쪽 능선
                hillCone(33, 7, 2) // 아차산·용마산 동쪽
                // 관악산 — 남서쪽 큰 산괴
                hillCone(8, 26, 4)
                hillCone(12, 27, 3)
                // 한강변 북쪽 능선 소나무 숲과 강남 구릉 가로수 벨트
                forestBelt(4, 13, 4, 9, 0.38)
                forestBelt(28, 36, 22, 26, 0.28)
            }
            "incheon" -> {
                // 서해 갯벌 위 섬들 — 영종·무의도 느낌의 작은 모래섬 3개
                islandInSea(4, 9, 2)
                islandInSea(5, 18, 2)
                islandInSea(3, 26, 2)
                // 내륙 계양산 — 동북쪽 봉우리
                hillCone(33, 6, 3)
                forestBelt(28, 36, 4, 12, 0.32)
            }
            "chuncheon" -> {
                // 호반도시 — 서쪽 의암호 외에 동북쪽 소양호 쪽 능선과 물길 주변 산
                hillCone(32, 6, 3)
                hillCone(34, 11, 2)
                hillCone(5, 7, 3)
                hillCone(6, 26, 3)
                // 북한강 협곡을 따라 삼나무 숲 벨트
                forestBelt(4, 10, 10, 19, 0.36)
                forestBelt(26, 36, 14, 20, 0.30)
                // 호수 주변 완만한 구릉 — 바위와 소나무 섞음
                hillCone(14, 22, 2)
            }
            "gangneung" -> {
                // 태백산맥 — 서쪽에 길게 늘어선 능선 (대관령)
                ridgeLine(listOf(6 to 6, 7 to 10, 7 to 14, 8 to 18, 7 to 22, 6 to 26), 2)
                // 소나무 해안림 — 동해 모래 바로 안쪽 솔숲
                forestBelt(34, 37, 4, 26, 0.45)
                forestBelt(9, 14, 4, 12, 0.42)
                // 경포호 주변 갈대·소나무 혼합
                hillCone(27, 19, 2)
            }
            "sokcho" -> {
                // 설악산 — 서쪽을 두껍게 막는 암벽
                ridgeLine(listOf(5 to 4, 6 to 8, 7 to 12, 6 to 16, 7 to 20, 6 to 24), 3)
                // 울산바위 느낌의 뾰족한 암봉 2개
                hillCone(9, 8, 2); hillCone(10, 15, 2)
                // 해안 소나무 + 청초호 주변 갈대
                forestBelt(32, 37, 5, 19, 0.38)
            }
            "daejeon" -> {
                // 계족산·식장산 — 동쪽과 남쪽을 두르는 낮은 산
                hillCone(34, 7, 3)
                hillCone(35, 24, 3)
                hillCone(5, 26, 3)
                // 갑천 합류점 주변 둔치 숲
                forestBelt(9, 16, 12, 20, 0.34)
                // 도심 남쪽 보문산 구릉
                hillCone(15, 26, 2)
            }
            "jeonju" -> {
                // 전주 한옥마을 — 북동쪽 기와 능선을 HOUSE_ROOF로 표현 (건물 대신 지형으로)
                // 대신 남쪽 논두렁과 서쪽 건지산 구릉을 지형으로
                hillCone(8, 6, 2)
                hillCone(32, 6, 2)
                // 남쪽 만경평야 — 논둑 풀결은 fieldRows로 이미 있지만, 외곽에 낮은 구릉
                forestBelt(28, 36, 6, 11, 0.26)
                // 모악산 남서
                hillCone(7, 26, 3)
            }
            "daegu" -> {
                // 분지 도시 — 북쪽 팔공산, 남쪽 비슬산/앞산이 분지를 둘러싼다
                ridgeLine(listOf(7 to 4, 13 to 3, 19 to 3, 26 to 4, 32 to 5), 2) // 북쪽 능선
                ridgeLine(listOf(7 to 26, 14 to 27, 21 to 28, 28 to 27, 33 to 26), 2) // 남쪽 능선
                // 동쪽 영천 구릉
                hillCone(35, 13, 2)
                // 분지 안은 건조 — 바위와 드문 소나무, 꽃밭 조금
                for (y in 14..22) for (x in 16..26) if (rnd.nextDouble() < 0.08 && t[y][x] == T.GRASS.ordinal) placeRock(x, y)
                // 낙동 정맥 동쪽에 사과밭 느낌 꽃 군락 (기성 flowerDensity 외 추가)
                forestBelt(22, 30, 20, 24, 0.18)
            }
            "gwangju" -> {
                // 무등산 — 동쪽 거대한 산괴
                hillCone(33, 8, 4)
                hillCone(35, 13, 3)
                hillCone(31, 16, 2)
                // 서쪽 평야와 광주천 둔치 숲
                forestBelt(6, 13, 14, 24, 0.32)
                forestBelt(18, 24, 5, 10, 0.22)
            }
            "ulsan" -> {
                // 태화강 상류 가지산·신불산 — 서북쪽 산군
                hillCone(7, 6, 3)
                hillCone(11, 8, 2)
                hillCone(6, 22, 2)
                // 동해안 대왕암 방파제 느낌 — 남동 해안에 바위 띠
                for (x in 35..37) for (y in 18..24) if (inb(x, y) && base[y][x] != T.WATER.ordinal && rnd.nextFloat() < 0.72f) placeRock(x, y)
                // 태화강 십리대숲 — 강변 대나무 대신 소나무·갈대 혼합 벨트
                forestBelt(8, 18, 23, 27, 0.30)
            }
            "busan" -> {
                // 금정산·장산 — 북쪽과 동쪽을 둘러싼 산
                hillCone(10, 5, 3)
                hillCone(14, 4, 2)
                hillCone(33, 8, 3)
                hillCone(35, 15, 2)
                // 해운대·광안리 모래 해변은 이미 sandEdges로, 남쪽 항만 방파제 바위 추가 (남쪽 모래띠 y 25~26)
                for (x in 16..26) for (y in 25..26) if (inb(x, y) && base[y][x] == T.SAND.ordinal && rnd.nextFloat() < 0.52f) placeRock(x, y)
                // 동쪽 해안 솔숲
                forestBelt(34, 37, 4, 18, 0.32)
                // 낙동강 하구 갈대섬
                for (y in 22..26) for (x in 13..18) if (inb(x, y) && t[y][x] == T.GRASS.ordinal && rnd.nextFloat() < 0.22f) {
                    setGround(x, y, T.REED); reserved[y][x] = true
                }
            }
            "jeju" -> {
                // 한라산 — 섬 중앙 약간 남쪽의 거대한 방패 화산
                hillCone(20, 14, 5)
                hillCone(20, 15, 4)
                // 오름 7개 — 전역에 퍼진 작은 화산체
                hillCone(12, 7, 2)
                hillCone(28, 9, 2)
                hillCone(30, 16, 2)
                hillCone(26, 23, 2)
                hillCone(10, 23, 2)
                hillCone(14, 17, 2)
                hillCone(33, 21, 2)
                // 곶자왈 — 중산간 숲 벨트 (동서로)
                forestBelt(10, 30, 11, 13, 0.38)
                forestBelt(8, 32, 17, 19, 0.34)
                // 현무암 들판 — 섬 전역에 검은 바위 자갈
                for (y in 4..26) for (x in 4..36) {
                    if (isPlazaOrRoad(x, y)) continue
                    if (t[y][x] != T.GRASS.ordinal) continue
                    if (base[y][x] == T.WATER.ordinal) continue
                    if (rnd.nextDouble() < 0.09) placeRock(x, y)
                }
                // 해안 주상절리 — 남쪽 모래띠(y 23~25)에 돌출 현무암
                for (x in 18..28) for (y in 23..25) if (inb(x, y) && base[y][x] == T.SAND.ordinal) if (rnd.nextFloat() < 0.72f) placeRock(x, y)
                // 북쪽 해협 쪽 모래에도 자갈
                for (x in 12..24) for (y in 4..6) if (inb(x, y) && base[y][x] == T.SAND.ordinal) if (rnd.nextFloat() < 0.45f) placeRock(x, y)
            }
            else -> {
                // 그 외 탐조지 등은 기존 고도 팔레트와 grove 생성으로 충분하지만,
                // 해안·습지·산지에 따라 가볍게 한두 개 언덕을 얹어 단조로움을 깬다.
                when (region.kind) {
                    RegionKind.MOUNTAIN -> {
                        hillCone(7, 7, 2); hillCone(33, 24, 2)
                    }
                    RegionKind.COAST -> {
                        // 바다와 해변의 실제 방향을 따라 곰솔림을 안쪽에 둔다 (서해/동해 뒤집힘 방지).
                        inlandCoastalForest(0.34)
                    }
                    RegionKind.WETLAND -> {
                        // 습지는 갈대 외에 낮은 둑 풀섶 하나
                        forestBelt(5, 13, 5, 8, 0.20)
                    }
                    else -> {}
                }
            }
        }

        // 4. 도시 건물 (길보다 먼저 — 정문 앞으로 샛길을 내기 위해) ---------------
        val buildingFronts = ArrayList<Pair<Int, Int>>()
        if (region.city) {
            val northY = mapStyle.northBuildingY
            for ((bx, by) in listOf(6 to northY, 31 to northY, 6 to 24, 31 to 24)) {
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

        // 4.5 도시별 건물 밀도 — 실제 도시 규모를 살린다
        if (region.city) {
            fun tryExtra(bx: Int, by: Int) {
                if (inb(bx, by) && inb(bx + 2, by + 2)) {
                    var ok = true
                    for (y in by until by + 3) for (x in bx until bx + 3) {
                        if (x !in 2 until w - 2 || y !in 2 until h - 2 || t[y][x] != T.GRASS.ordinal) ok = false
                    }
                    if (!ok) return
                    for (y in by until by + 3) for (x in bx until bx + 3) {
                        t[y][x] = when {
                            y == by -> T.BLDG_ROOF
                            (x + y) % 2 == 0 -> T.BLDG_WIN
                            else -> T.BLDG_WALL
                        }.ordinal
                        structure[y][x] = true; reserved[y][x] = true
                    }
                    buildingFronts.add(if (by + 3 <= AVE_Y) (bx + 1 to by + 3) else (bx + 1 to by - 1))
                }
            }
            when (region.id) {
                "seoul" -> {
                    // 서울 — 북촌·강남에 빽빽한 빌딩
                    tryExtra(12, northY0); tryExtra(14, 24); tryExtra(24, 24)
                }
                "busan" -> {
                    // 부산 — 항만 뒤 고층 빌딩 밀집
                    tryExtra(12, 5); tryExtra(9, 24); tryExtra(26, 24)
                }
                "daegu" -> {
                    // 대구 — 분지 안 중밀도
                    tryExtra(14, northY0)
                }
                "incheon" -> {
                    // 인천 — 신도시 고층 2동 추가
                    tryExtra(12, 5); tryExtra(14, 24)
                }
                "daejeon" -> tryExtra(26, 5)
                "gwangju" -> tryExtra(24, 24)
                "ulsan" -> tryExtra(10, 24)
                "jeonju" -> {
                    // 한옥마을 느낌 — 북동쪽에 기와집 2채 (HOUSE 타일로)
                    for ((hx, hy) in listOf(28 to 5, 30 to 8)) {
                        if (!inb(hx, hy) || t[hy][hx] != T.GRASS.ordinal) continue
                        var ok = true
                        for (y in hy until hy + 3) for (x in hx until hx + 3) if (t[y][x] != T.GRASS.ordinal) ok = false
                        if (!ok) continue
                        for (y in hy until hy + 3) for (x in hx until hx + 3) {
                            t[y][x] = when {
                                y == hy -> T.HOUSE_ROOF.ordinal
                                y == hy + 1 && (x == hx + 1) -> T.HOUSE_WIN.ordinal
                                y == hy + 1 -> T.HOUSE_WALL.ordinal
                                else -> T.HOUSE_WALL.ordinal
                            }
                            structure[y][x] = true; reserved[y][x] = true
                        }
                    }
                }
                else -> {}
            }
        }

        // 5. 우리 집 -------------------------------------------------------------
        var houseDoorX = -1
        var houseDoorY = -1
        // 정착 중인 집뿐 아니라 이전에 매입해 둔 지역 집도 현관을 유지한다.
        // homeRegion을 함께 검사해, 오래된 세이브/호출부가 소유 목록을 넘기지 않아도
        // 현재 정착지의 현관이 사라지지 않게 한다.
        val hasHouse = region.id == homeRegion || region.id in ownedHomeRegions
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

        // 5.5 지역 랜드마크 ------------------------------------------------------
        // 지역마다 하나씩, 광장 근처에 들어갈 수 있는 랜드마크 건물을 세운다.
        // 5칸 폭 × 4줄(지붕 2줄 + 벽 + 문). 문 앞으로 샛길을 이어 준다.
        var landmarkDoorX = -1
        var landmarkDoorY = -1
        val landmarkFronts = ArrayList<Pair<Int, Int>>()
        if (Landmarks.byRegion.containsKey(region.id)) {
            // 광장 주변을 우선으로, 지형에 걸리지 않는 첫 자리를 고른다.
            val candidates = listOf(
                11 to 8, 26 to 8, 11 to 20, 26 to 20,
                6 to 9, 29 to 9, 6 to 22, 29 to 22,
                13 to 8, 24 to 20, 8 to 8, 27 to 8
            )
            fun footprintFree(bx: Int, by: Int): Boolean {
                if (bx < 2 || by < 2 || bx + 4 > w - 3 || by + 4 > h - 3) return false
                // 중앙 광장(NPC 자리)·간선도로와 겹치면 안 된다.
                for (x in bx..bx + 4) if (x in AVE_X - 1..AVE_X + 2) return false
                for (y in by..by + 4) if (y in AVE_Y - 1..AVE_Y + 2) return false
                for (y in by..by + 3) for (x in bx..bx + 4) {
                    if (x in PLAZA_X0..PLAZA_X1 && y in PLAZA_Y0..PLAZA_Y1) return false
                    if (t[y][x] != T.GRASS.ordinal || structure[y][x] || reserved[y][x]) return false
                    if (base[y][x] == T.WATER.ordinal) return false
                }
                // 문 앞(내려가는 방향) 한 칸도 지날 수 있어야 한다.
                val fx = bx + 2
                val fy = by + 4
                if (!inb(fx, fy) || structure[fy][fx] || base[fy][fx] == T.WATER.ordinal) return false
                return true
            }
            var anchor = candidates.firstOrNull { footprintFree(it.first, it.second) }
            if (anchor == null) {
                // 미리 정한 자리가 모두 막혔으면 맵 전체를 훑어 광장에서 가장 가까운 빈 자리를 쓴다.
                var best: Pair<Int, Int>? = null
                var bestD = Int.MAX_VALUE
                for (by in 3..(h - 8)) for (bx in 3..(w - 8)) {
                    if (!footprintFree(bx, by)) continue
                    val ddx = (bx + 2) - 21
                    val ddy = (by + 2) - 15
                    val d = ddx * ddx + ddy * ddy
                    if (d < bestD) { bestD = d; best = bx to by }
                }
                anchor = best
            }
            val chosen = anchor
            if (chosen != null) {
                val (bx, by) = chosen
                // 간선도로 남쪽에 자리 잡으면 문을 북쪽(길 쪽)으로 낸다 —
                // 남향 그대로 두면 정문 앞이 등지고 선 벽이라 샛길이 이을 수 없다.
                val south = by > AVE_Y
                val roofRows = if (south) listOf(by + 2, by + 3) else listOf(by, by + 1)
                val winY = if (south) by + 1 else by + 2
                val doorY = if (south) by else by + 3
                // 지붕 2줄
                for (yy in roofRows) for (x in bx..bx + 4) {
                    t[yy][x] = T.LM_ROOF.ordinal; structure[yy][x] = true; reserved[yy][x] = true
                }
                // 벽 + 아치 창 (가운데 3칸이 창)
                for (x in bx..bx + 4) {
                    t[winY][x] = (if (x in bx + 1..bx + 3) T.LM_WIN else T.LM_WALL).ordinal
                    structure[winY][x] = true; reserved[winY][x] = true
                }
                // 문 줄: 벽 · 벽 · 문 · 벽 · 벽
                for (x in bx..bx + 4) {
                    val isDoor = x == bx + 2
                    t[doorY][x] = (if (isDoor) T.LANDMARK_DOOR else T.LM_WALL).ordinal
                    structure[doorY][x] = !isDoor
                    reserved[doorY][x] = true
                }
                landmarkDoorX = bx + 2
                landmarkDoorY = doorY
                // 문턱 포장
                pave[doorY][bx + 2] = Pave.STONE
                val frontY = if (south) doorY - 1 else doorY + 1
                if (inb(bx + 2, frontY)) pave[frontY][bx + 2] = Pave.STONE
                landmarkFronts.add(bx + 2 to frontY)
            }
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
                val crossingWater = base[yy][xx] == T.WATER.ordinal
                if (crossingWater) base[yy][xx] = T.SAND.ordinal
                // 하천·호수를 가로지르는 길은 돌다리로 읽히도록 석재 포장을 쓴다.
                val roadMat = if (crossingWater && mat == Pave.DIRT) Pave.STONE else mat
                t[yy][xx] = if (roadMat == Pave.DIRT) T.PATH.ordinal else T.PLAZA.ordinal
                pave[yy][xx] = roadMat
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
        fun pathClear(points: List<Pair<Int, Int>>, allowRiverBridge: Boolean = false): List<Pair<Int, Int>>? {
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
                if (!inb(x, y) || structure[y][x]) return null
                val blockedWater = base[y][x] == T.WATER.ordinal && !(allowRiverBridge && isRiverWater(x, y))
                if (blockedWater) return null
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

        // 8.5 터널 번호 부여 — 북·동·남·서 시계방향, 맵별로 다르게 -------------------
        val tunnelList = ArrayList<TunnelInfo>()
        var tunnelNo = 1
        for (d in listOf(Dir.N, Dir.E, Dir.S, Dir.W)) {
            val targetId = exits[d] ?: continue
            val tx: Int
            val ty: Int
            val cx: Float
            val cy: Float
            when (d) {
                Dir.N -> { tx = 19; ty = 0; cx = 320f; cy = 8f }
                Dir.S -> { tx = 19; ty = h - 1; cx = 320f; cy = (h - 1) * 16f + 8f }
                Dir.W -> { tx = 0; ty = 15; cx = 8f; cy = 256f }
                Dir.E -> { tx = w - 1; ty = 15; cx = (w - 1) * 16f + 8f; cy = 256f }
            }
            tunnelList.add(TunnelInfo(d, targetId, tx, ty, cx, cy, tunnelNo))
            tunnelNo++
        }

        // 9. 샛길 -------------------------------------------------------------------
        for ((fx, fy) in buildingFronts) {
            val targetY = if (fy < AVE_Y) AVE_Y else AVE_Y + 1
            val seq = pathClear(listOf(fx to fy, fx to targetY), allowRiverBridge = mapStyle.rivers.isNotEmpty()) ?: continue
            for ((x, y) in seq) stamp(x, y, 1, Pave.DIRT)
        }
        // 랜드마크 정문 앞 샛길 (간선도로까지 이어 준다)
        //     문턱 포장 2칸은 이미 깔려 있으므로 그 **바깥**에서부터 길을 잇는다 —
        //     포장된 칸이 줄에 섞이면 pathClear 가 포기해서 정문이 고아 길이 된다.
        for ((fx, fy) in landmarkFronts) {
            val targetY = if (fy < AVE_Y) AVE_Y else AVE_Y + 1
            val startY = if (fy < AVE_Y) fy + 1 else fy - 1
            val seq = pathClear(listOf(fx to startY, fx to targetY), allowRiverBridge = mapStyle.rivers.isNotEmpty())
                ?: pathClear(listOf(fx to startY, fx to AVE_Y + 1), allowRiverBridge = true)
            if (seq != null) for ((x, y) in seq) stamp(x, y, 1, Pave.DIRT)
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
        val eastRestBlocked = mapStyle.rivers.any { river ->
            river.course.any { it.x in 27..33 && it.y in 16..24 }
        }
        val restX = when {
            lakeShoreX >= 0 && lakeShoreX < w / 2 -> w - 10
            lakeShoreX >= 0 -> 9
            eastRestBlocked -> 9
            else -> 30
        }
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
            // 이정표 앞에 플레이어가 설 칸 보장 — 지형(현무암·산·숲)이 에워싸면 읽을 수 없게 된다
            val (ix, iy) = when (d) {
                Dir.N -> sx to sy + 1
                Dir.S -> sx to sy - 1
                Dir.W -> sx + 1 to sy
                Dir.E -> sx - 1 to sy
            }
            if (inb(ix, iy) && T.ALL[t[iy][ix]].solid) {
                base[iy][ix] = T.GRASS.ordinal
                t[iy][ix] = T.GRASS.ordinal
                reserved[iy][ix] = true
            }
        }

        // 10.5 논·갈대 들판 — 논둑처럼 평행한 풀결을 지역 일부에만 얹는다. --------
        if (mapStyle.fieldRows && !region.city) {
            for ((x0, x1, y0) in listOf(Triple(5, 15, 5), Triple(25, 35, 23))) {
                for (row in y0..(y0 + 2)) for (x in x0..x1) {
                    if (!inb(x, row) || reserved[row][x] || pave[row][x] != Pave.NONE || t[row][x] != T.GRASS.ordinal) continue
                    if ((x + row) % 5 != 0) {
                        val cover = if ((x + row) % 3 == 0) T.FLOWER else T.TALLGRASS
                        setGround(x, row, cover)
                        reserved[row][x] = true
                    }
                }
            }
        }

        // 11. 광장 시설 (벤치 -> 모서리 가로등/그늘나무) ------------------------------
        //     사람은 자연물까지 다 자란 뒤(14번)에 세운다 — 자리가 나무에 묻히지 않게.
        fun putProp(x: Int, y: Int, tile: T): Boolean {
            if (!inb(x, y) || structure[y][x]) return false
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

        /** 불규칙한 가장자리로 식생/암석을 모아, 지역마다 알아볼 수 있는 자연 군락을 만든다. */
        fun naturalPatch(cx: Int, cy: Int, rx: Int, ry: Int, tile: T, density: Float, salt: Int) {
            val x0 = (cx - rx).coerceAtLeast(2)
            val x1 = (cx + rx).coerceAtMost(w - 3)
            val y0 = (cy - ry).coerceAtLeast(2)
            val y1 = (cy + ry).coerceAtMost(h - 3)
            for (y in y0..y1) for (x in x0..x1) {
                if (isPlazaOrRoad(x, y) || reserved[y][x] || pave[y][x] != Pave.NONE) continue
                if (base[y][x] == T.WATER.ordinal || t[y][x] != T.GRASS.ordinal) continue
                val dx = (x - cx) / rx.toFloat()
                val dy = (y - cy) / ry.toFloat()
                val seed = x * 73856093 xor (y * 19349663) xor region.id.hashCode() xor (salt * 83492791)
                val roughEdge = Math.floorMod(seed ushr 3, 4) * 0.11f
                if (dx * dx + dy * dy > 1f + roughEdge) continue
                if (Math.floorMod(seed, 1000) / 1000f > density) continue
                when (tile) {
                    T.ROCK -> placeRock(x, y)
                    T.MOUNTAIN -> placeMountain(x, y)
                    T.TREE -> placeTree(x, y)
                    else -> if (tile.ground) {
                        setGround(x, y, tile)
                        reserved[y][x] = true
                    }
                }
            }
        }

        // 지역 대표 자연물: 강원 바위 능선, 갯벌 갈대섬, 제주 현무암처럼
        // 무작위 장식만으로는 안 드러나는 지형 실루엣을 산책로 가장자리에 더한다.
        when (region.id) {
            "seoul" -> {
                naturalPatch(30, 22, 3, 2, T.TREE, 0.66f, 1)       // 남산 공원 수목
                naturalPatch(10, 20, 3, 1, T.FLOWER, 0.82f, 2)     // 한강변 풀꽃
            }
            "incheon", "ganghwa", "songdo" -> {
                naturalPatch(10, 8, 4, 2, T.REED, 0.92f, 3)        // 서해 갯골의 갈대섬
                naturalPatch(10, 21, 3, 1, T.ROCK, 0.78f, 4)       // 낮은 조개·퇴적암 둔덕
            }
            "chuncheon" -> {
                naturalPatch(13, 22, 3, 2, T.ROCK, 0.78f, 5)       // 호숫가 둥근 자갈
                naturalPatch(29, 10, 2, 4, T.TREE, 0.72f, 6)       // 소양강변 버드나무
            }
            "gangneung" -> {
                naturalPatch(32, 19, 3, 2, T.ROCK, 0.84f, 7)       // 동해 물결 바위
                naturalPatch(12, 8, 2, 3, T.TREE, 0.70f, 8)        // 해안 곰솔
            }
            "sokcho", "gwangneung", "hallasan" -> {
                naturalPatch(11, 9, 3, 3, T.ROCK, 0.78f, 9)        // 화강암/현무암 암괴
                naturalPatch(28, 22, 4, 2, T.TREE, 0.76f, 10)      // 고산 상록수 숲
            }
            "daejeon", "gongneung", "geumgang", "imjin", "wangpi" -> {
                naturalPatch(12, 18, 3, 2, T.ROCK, 0.78f, 11)       // 강바닥 둥근 돌
                naturalPatch(29, 9, 3, 2, T.REED, 0.78f, 12)       // 여울가 물억새
            }
            "jeonju", "cheorwon", "daegu" -> {
                naturalPatch(12, 21, 5, 2, T.FLOWER, 0.78f, 13)    // 논둑 야생화
                naturalPatch(29, 8, 4, 1, T.TALLGRASS, 0.80f, 14)  // 들판의 억새 띠
            }
            "gwangju" -> {
                naturalPatch(29, 9, 3, 2, T.TREE, 0.75f, 15)       // 무등산 동백 숲
                naturalPatch(11, 21, 3, 2, T.ROCK, 0.72f, 16)      // 광주천 이끼 바위
            }
            "ulsan" -> {
                naturalPatch(11, 23, 4, 2, T.TREE, 0.82f, 17)      // 태화강 십리대숲
                naturalPatch(34, 21, 2, 3, T.ROCK, 0.85f, 18)      // 대왕암 해식 바위
            }
            "busan" -> {
                naturalPatch(34, 20, 2, 4, T.TREE, 0.76f, 19)      // 해안 곰솔
                naturalPatch(23, 24, 4, 1, T.ROCK, 0.76f, 20)      // 방파제 현무암
            }
            "jeju", "hadori" -> {
                naturalPatch(29, 20, 3, 2, T.ROCK, 0.88f, 21)      // 검은 용암석
                naturalPatch(11, 9, 4, 2, T.TREE, 0.70f, 22)       // 바람 센 동백·곰솔
            }
            "eulsukdo", "suncheon", "ansan", "sihwa", "hwaseong", "junam", "upo" -> {
                naturalPatch(11, 20, 4, 2, T.REED, 0.92f, 23)      // 습지 갈대밭
                naturalPatch(29, 9, 3, 2, T.TALLGRASS, 0.82f, 24)  // 물가 풀섶
            }
            "maehyang", "gochang", "taean" -> {
                naturalPatch(10, 21, 4, 2, T.ROCK, 0.80f, 25)      // 갯바위·사구 돌
                naturalPatch(29, 8, 3, 2, T.TREE, 0.72f, 26)       // 해풍 맞은 소나무
            }
        }

        // 큰 자연물 군락: 같은 종류를 뭉치되 군락끼리는 충분히 떨어뜨린다.
        val groveCount = when {
            isMountain -> 7
            isWet -> 6
            isCoast -> 5
            isRiver -> 5
            region.city -> 4
            else -> 4
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
                isWet && r < 0.28 -> { t[y][x] = T.REED.ordinal; base[y][x] = T.REED.ordinal }
                isRiver && r < 0.17 -> { t[y][x] = T.ROCK.ordinal; rockBuddy(x, y) }
                isCoast && r < 0.16 -> { t[y][x] = T.ROCK.ordinal; rockBuddy(x, y) }
                isMountain && r < 0.15 -> { t[y][x] = T.ROCK.ordinal; rockBuddy(x, y) }
                // 도시 공원·하천 산책로에는 꽃밭을 조금 더 자주 만든다.
                region.city && r < 0.19 -> { t[y][x] = T.FLOWER.ordinal; base[y][x] = T.FLOWER.ordinal }
                r < region.treeDensity -> t[y][x] = T.TREE.ordinal
                r < region.treeDensity + region.flowerDensity -> {
                    t[y][x] = T.FLOWER.ordinal
                    base[y][x] = T.FLOWER.ordinal
                }
                r < region.treeDensity + region.flowerDensity + region.rockDensity -> {
                    t[y][x] = T.ROCK.ordinal; rockBuddy(x, y)
                }
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

        // 13.5 높낮이와 전망 데크 ----------------------------------------------------
        // Water edges, tidal flats, reeds, and rice fields sit slightly lower.
        // A single regional high point is reached through a real, one-step-at-a-time ramp.
        for (y in 0 until h) for (x in 0 until w) {
            elevation[y][x] = when {
                base[y][x] == T.WATER.ordinal -> -1
                t[y][x] == T.SAND.ordinal || t[y][x] == T.REED.ordinal ||
                    t[y][x] == T.TALLGRASS.ordinal && (isWet || isCoast) -> -1
                else -> 0
            }
        }

        val viewpoints = ArrayList<ViewpointInfo>()
        val viewSpec = mapStyle.viewpoint
        if (viewSpec != null) {
            fun freeDeck(x: Int, y: Int): Boolean =
                inb(x, y) && x in 3 until w - 3 && y in 3 until h - 3 &&
                    !reserved[y][x] && !structure[y][x] && base[y][x] != T.WATER.ordinal &&
                    !T.ALL[t[y][x]].solid

            var deck: Pair<Int, Int>? = null
            val candidates = ArrayList<Pair<Int, Int>>()
            candidates.add(viewSpec.point.x to viewSpec.point.y)
            for (radius in 1..6) for (dy in -radius..radius) for (dx in -radius..radius) {
                if (kotlin.math.abs(dx) != radius && kotlin.math.abs(dy) != radius) continue
                candidates.add(viewSpec.point.x + dx to viewSpec.point.y + dy)
            }
            for (candidate in candidates) if (freeDeck(candidate.first, candidate.second)) {
                deck = candidate; break
            }

            if (deck != null) {
                // BFS finds a walkable connection to an existing path, not a decorative
                // staircase that ends in a tree or a river. Prefer enough run-up for the height.
                val startKey = deck.second * 100 + deck.first
                val parent = HashMap<Int, Int>()
                val distance = HashMap<Int, Int>()
                val q = ArrayDeque<Int>()
                q.add(startKey); distance[startKey] = 0
                var targetKey: Int? = null
                while (q.isNotEmpty()) {
                    val key = q.removeFirst()
                    val cy = key / 100; val cx = key - cy * 100
                    val d = distance[key] ?: 0
                    if (d >= viewSpec.height && pave[cy][cx] != Pave.NONE) { targetKey = key; break }
                    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val nx = cx + dx; val ny = cy + dy
                        if (!inb(nx, ny) || nx !in 2 until w - 2 || ny !in 2 until h - 2) continue
                        val nk = ny * 100 + nx
                        if (nk in distance || structure[ny][nx] || (reserved[ny][nx] && pave[ny][nx] == Pave.NONE) || T.ALL[t[ny][nx]].solid || base[ny][nx] == T.WATER.ordinal) continue
                        distance[nk] = d + 1; parent[nk] = key; q.add(nk)
                    }
                }
                if (targetKey != null) {
                    val route = ArrayList<Pair<Int, Int>>()
                    var key = targetKey!!
                    while (true) {
                        val cy = key / 100; val cx = key - cy * 100
                        route.add(cx to cy)
                        if (key == startKey) break
                        key = parent[key] ?: break
                    }
                    route.reverse()
                    val actualHeight = minOf(viewSpec.height, route.lastIndex.coerceAtLeast(1))
                    for (i in route.indices) {
                        val (x, y) = route[i]
                        val level = kotlin.math.round(actualHeight * (1f - i.toFloat() / route.lastIndex.coerceAtLeast(1))).toInt()
                        elevation[y][x] = level
                        if (i == 0) {
                            t[y][x] = T.PLAZA.ordinal
                            base[y][x] = T.GRASS.ordinal
                            pave[y][x] = Pave.STONE
                        } else {
                            t[y][x] = T.PATH.ordinal
                            base[y][x] = T.GRASS.ordinal
                            pave[y][x] = Pave.STONE
                        }
                        reserved[y][x] = true
                    }
                    viewpoints.add(ViewpointInfo(deck!!.first, deck!!.second, viewSpec.label, actualHeight))
                }
            }
        }

        // 14. 지역 사람 배치 — 「한 사람은 한 장소에만」 (`NpcRoster`) ------------------
        //     자연물까지 다 자란 마지막에 세운다. 그래야 나무·바위에 자리가 묻히지 않고,
        //     "여기까지 걸어갈 수 있는가"를 완성된 지도로 검사할 수 있다.
        val npcs = placeCast(
            region = region, cast = NpcRoster.forRegion(region.id),
            w = w, h = h, t = t, base = base, pave = pave, structure = structure,
            lakeShoreX = lakeShoreX, lakeShoreY = lakeShoreY,
            restX = restX, restY = restY,
            buildingFronts = buildingFronts,
            lake = mapStyle.lake,
            fieldRows = mapStyle.fieldRows,
            houseDoorX = houseDoorX, houseDoorY = houseDoorY,
            tunnels = tunnelList
        )

        return GameMap(
            region, w, h, t, base, pave, deco, npcs, hasHouse, houseDoorX, houseDoorY,
            mapStyle, tunnelList,
            hasLandmark = landmarkDoorX >= 0, landmarkDoorX = landmarkDoorX, landmarkDoorY = landmarkDoorY,
            elevation = elevation, viewpoints = viewpoints
        )
    }

    /**
     * 지역 사람(`NpcPerson`)을 **그 지역의 실제 랜드마크**에 세운다.
     *
     * 자리는 좌표가 아니라 [NpcSpot](호숫가 데크·갈대밭·갯벌·시장 골목·산길 들머리…)로만 정해 두고,
     * 방금 완성된 지도에서 그 조건에 맞는 타일을 찾아 검증한 뒤 세운다.
     *
     * 검증 조건
     *  1. 발밑과 앞칸이 고체가 아니고 물도 아니다 (이름표·몸이 안 걸린다)
     *  2. 도착 지점(광장 스폰·현관 앞·터널 진입로)을 막지 않는다
     *  3. 이미 선 사람과 3칸 이상 떨어진다 (한 자리에 몰리지 않는다)
     *  4. 광장 중심에서 **걸어서** 갈 수 있다 (물 건너·섬 배치 금지)
     *
     * 조건을 만족하는 자리가 없으면 ① 길 옆 → ② 광장 → ③ 광장 주변 순으로 옮긴다.
     */
    private fun placeCast(
        region: RegionDef,
        cast: List<NpcPerson>,
        w: Int,
        h: Int,
        t: Array<IntArray>,
        base: Array<IntArray>,
        pave: Array<IntArray>,
        structure: Array<BooleanArray>,
        lakeShoreX: Int,
        lakeShoreY: Int,
        restX: Int,
        restY: Int,
        buildingFronts: List<Pair<Int, Int>>,
        lake: MapLake?,
        fieldRows: Boolean,
        houseDoorX: Int,
        houseDoorY: Int,
        tunnels: List<TunnelInfo>
    ): List<Npc> {
        if (cast.isEmpty()) return emptyList()
        val rnd = Random(region.id.hashCode().toLong() xor 0x4E504943L)   // 사람 자리도 지역마다 결정적

        fun inb(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h
        fun tileAt(x: Int, y: Int): T = if (inb(x, y)) T.ALL[t[y][x]] else T.MOUNTAIN
        fun waterAt(x: Int, y: Int): Boolean = inb(x, y) && base[y][x] == T.WATER.ordinal
        fun pavedAt(x: Int, y: Int): Boolean = inb(x, y) && pave[y][x] != Pave.NONE
        fun inPlaza(x: Int, y: Int): Boolean =
            x in PLAZA_X0..PLAZA_X1 && y in PLAZA_Y0..PLAZA_Y1

        fun countNear(x: Int, y: Int, d: Int, pred: (Int, Int) -> Boolean): Int {
            var n = 0
            for (yy in y - d..y + d) for (xx in x - d..x + d) {
                if (xx == x && yy == y) continue
                if (inb(xx, yy) && pred(xx, yy)) n++
            }
            return n
        }

        /** 호수 둘레 띠(물 바로 바깥 ~ 갈대밭 끝)에 있는가 — `RegionMapStyle.lake` 기준 */
        fun lakeBand(x: Int, y: Int): Boolean {
            val lk = lake ?: return false
            val dx = (x - lk.center.x) / (lk.radiusX + 1.5f)
            val dy = (y - lk.center.y) / (lk.radiusY + 1.5f)
            val d2 = dx * dx + dy * dy
            return d2 > 0.55f && d2 < 2.2f && !waterAt(x, y)
        }

        /** 근처에 물이 있는가. `inland` 이면 지도 가장자리(바다 띠)의 물은 세지 않는다. */
        fun waterNear(x: Int, y: Int, d: Int, inland: Boolean = false): Boolean =
            countNear(x, y, d) { a, b ->
                waterAt(a, b) && (!inland || (a in 4 until w - 4 && b in 4 until h - 4))
            } > 0

        // ---- 사람이 서면 안 되는 자리: 도착 지점·현관 앞·터널 진입로 ----------------
        val keepClear = HashSet<Int>()
        fun keep(cx: Int, cy: Int, r: Int) {
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) keepClear.add(y * 100 + x)
        }
        keep(18, 14, 1)                                  // 첫 지역 진입 시 중앙 광장 스폰 자리
        for (info in tunnels) keep(info.tileX, info.tileY, 2)
        if (houseDoorX >= 0) {
            keep(houseDoorX, houseDoorY + 1, 2)          // 현관 앞
            for (y in 8..13) for (x in 19..27) keepClear.add(y * 100 + x)   // 집 마당
        }

        fun standable(x: Int, y: Int): Boolean {
            if (!inb(x, y) || !inb(x, y + 1)) return false
            if (structure[y][x] || structure[y + 1][x]) return false
            if (keepClear.contains(y * 100 + x)) return false
            val here = tileAt(x, y)
            val front = tileAt(x, y + 1)
            if (here.solid || front.solid) return false
            if (here == T.TUNNEL || here == T.SIGN || here == T.HOUSE_DOOR) return false
            if (front == T.TUNNEL || front == T.HOUSE_DOOR) return false
            if (waterAt(x, y) || waterAt(x, y + 1)) return false
            return true
        }

        /**
         * 플레이어가 대화하려고 설 수 있는 자리인가 (인사 자리·퀘스트 길안내 도착 지점용).
         *
         * 발판 박스(`GameMap.solidBox`)는 서 있는 칸 **아래 칸**까지 검사하므로
         * 아래 칸이 고체면 그 자리에 설 수 없다.
         */
        fun playerCanStand(x: Int, y: Int): Boolean {
            if (!inb(x, y) || !inb(x, y + 1)) return false
            if (structure[y][x] || structure[y + 1][x]) return false
            val tile = tileAt(x, y)
            if (tile.solid || tile == T.TUNNEL || tile == T.SIGN) return false
            if (tileAt(x, y + 1).solid) return false
            return !waterAt(x, y)
        }

        /** 광장 중심에서 걸어갈 수 있는가 — 완성된 지도로 BFS */
        fun reachable(x: Int, y: Int): Boolean {
            val seen = HashSet<Int>()
            val q = ArrayDeque<Int>()
            fun blocked(a: Int, b: Int): Boolean {
                if (!inb(a, b)) return true
                val tile = tileAt(a, b)
                return tile.solid || tile == T.TUNNEL || waterAt(a, b)
            }
            val sx = (PLAZA_X0 + PLAZA_X1) / 2
            val sy = (PLAZA_Y0 + PLAZA_Y1) / 2
            if (blocked(sx, sy) || blocked(x, y)) return false
            q.add(sy * 100 + sx); seen.add(sy * 100 + sx)
            while (q.isNotEmpty()) {
                val key = q.removeFirst()
                val cy = key / 100
                val cx = key - cy * 100
                if (cx == x && cy == y) return true
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = cx + dx; val ny = cy + dy
                    if (!inb(nx, ny)) continue
                    val nk = ny * 100 + nx
                    if (nk in seen || blocked(nx, ny)) continue
                    seen.add(nk); q.add(nk)
                }
            }
            return false
        }

        // ---- 후보 자리 만들기 -------------------------------------------------------
        fun around(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>> {
            if (cx < 0 || cy < 0) return emptyList()
            val out = ArrayList<Pair<Int, Int>>()
            for (d in 1..r) {
                for (y in cy - d..cy + d) for (x in cx - d..cx + d) {
                    if (maxOf(kotlin.math.abs(x - cx), kotlin.math.abs(y - cy)) != d) continue
                    out.add(x to y)
                }
            }
            return out
        }

        fun scan(pred: (Int, Int) -> Boolean): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>()
            for (y in 2 until h - 2) for (x in 2 until w - 2) if (pred(x, y)) out.add(x to y)
            return out
        }

        /**
         * 후보 자리 정렬.
         *
         * 같은 종류 안에서는 광장에서 가까운 순서로 고르되, 지역 시드로 섞어 지역마다 다른 자리를 쓴다.
         * `wild` = 자연 자리(갈대밭·숲·갯벌)는 **길이 깔리지 않은 야생 칸**을 우선한다 —
         * 갯벌 지킴이가 아스팔트 위에 서 있으면 그 자리의 맛이 사라지니까.
         */
        fun ordered(list: List<Pair<Int, Int>>, wild: Boolean = false): List<Pair<Int, Int>> {
            val shuffled = ArrayList(list)
            java.util.Collections.shuffle(shuffled, rnd)
            val cx = (PLAZA_X0 + PLAZA_X1) / 2f
            val cy = (PLAZA_Y0 + PLAZA_Y1) / 2f
            return shuffled.sortedWith(
                compareBy<Pair<Int, Int>> { if (wild) (if (pavedAt(it.first, it.second)) 1 else 0) else 0 }
                    .thenBy {
                        val dx = it.first - cx
                        val dy = it.second - cy
                        kotlin.math.sqrt(dx * dx + dy * dy).toDouble()
                    }
            )
        }

        val fieldBlocks = listOf(Triple(5, 15, 5), Triple(25, 35, 23))

        fun candidatesFor(spot: NpcSpot): List<Pair<Int, Int>> = when (spot) {
            // 광장 — 나침반 문양을 밟지 않으면서 문양 옆에 선다
            NpcSpot.PLAZA_COMPASS -> listOf(
                19 to 15, 23 to 15, 21 to 13, 21 to 17, 19 to 13, 23 to 17, 20 to 12, 22 to 18
            )
            NpcSpot.PLAZA_NORTH -> (18..24).map { it to 12 } + (18..24).map { it to 13 }
            NpcSpot.PLAZA_SOUTH -> (18..24).map { it to 18 } + (18..24).map { it to 17 }
            NpcSpot.PLAZA_WEST -> (13..17).map { 17 to it } + (13..17).map { 18 to it }
            NpcSpot.PLAZA_EAST -> (13..17).map { 25 to it } + (13..17).map { 24 to it }

            // 물가 — 데크·갈대·여울 (광장 안은 "물가"가 아니므로 뺀다)
            NpcSpot.LAKE_DECK -> around(lakeShoreX, lakeShoreY, 3)
            NpcSpot.LAKE_SHORE -> {
                // 호수가 있으면 그 둘레(갈대 띠 바깥)에서, 없으면 내륙 물가 갈대에서 찾는다
                val lakeRing = if (lake != null) scan { x, y ->
                    !inPlaza(x, y) && lakeBand(x, y)
                } else emptyList()
                if (lakeRing.isNotEmpty()) ordered(lakeRing, wild = true)
                else ordered(scan { x, y ->
                    !inPlaza(x, y) && waterNear(x, y, 2, inland = true) &&
                        (tileAt(x, y) == T.REED || base[y][x] == T.REED.ordinal)
                }, wild = true)
            }
            NpcSpot.RIVER_BANK -> ordered(
                scan { x, y -> !inPlaza(x, y) && waterNear(x, y, 2, inland = true) }, wild = true)
            NpcSpot.REED_HIDE -> ordered(scan { x, y ->
                !inPlaza(x, y) && base[y][x] == T.REED.ordinal && waterNear(x, y, 3)
            }, wild = true)

            // 바다 — 갯벌 둑길·해변
            NpcSpot.TIDAL_FLAT -> ordered(scan { x, y ->
                !inPlaza(x, y) && base[y][x] == T.SAND.ordinal &&
                    (waterNear(x, y, 4) || countNear(x, y, 2) { a, b ->
                        base[b][a] == T.REED.ordinal
                    } > 0)
            })
            NpcSpot.BEACH -> ordered(scan { x, y ->
                !inPlaza(x, y) && base[y][x] == T.SAND.ordinal && waterNear(x, y, 5)
            })

            // 숲·산 — 쉼터, 나무 그늘, 들머리
            NpcSpot.FOREST_REST -> around(restX, restY, 3)
            NpcSpot.GROVE -> ordered(scan { x, y ->
                !inPlaza(x, y) && tileAt(x, y) == T.GRASS &&
                    countNear(x, y, 1) { a, b -> tileAt(a, b) == T.TREE } >= 2
            }, wild = true)
            NpcSpot.TRAILHEAD -> {
                val rocky = scan { x, y ->
                    !inPlaza(x, y) && countNear(x, y, 1) { a, b -> tileAt(a, b) == T.MOUNTAIN } >= 1
                }
                if (rocky.isNotEmpty()) ordered(rocky, wild = true)
                else ordered(scan { x, y ->
                    !inPlaza(x, y) && (x < 8 || y < 8 || x >= w - 8 || y >= h - 8) &&
                        countNear(x, y, 1) { a, b -> tileAt(a, b) == T.TREE } >= 2
                }, wild = true)
            }

            // 사람 사는 자리 — 논둑, 건물 앞 골목, 가로수 아래
            NpcSpot.FIELD_EDGE -> {
                val out = ArrayList<Pair<Int, Int>>()
                if (fieldRows) {
                    for ((x0, x1, y0) in fieldBlocks) {
                        for (row in y0..y0 + 2) for (x in x0..x1) if (!inPlaza(x, row)) out.add(x to row)
                    }
                }
                out += scan { x, y ->
                    !inPlaza(x, y) && countNear(x, y, 1) { a, b ->
                        tileAt(a, b) == T.TALLGRASS || tileAt(a, b) == T.FLOWER
                    } >= 3
                }
                ordered(out, wild = true)
            }
            NpcSpot.MARKET -> ordered(buildingFronts.flatMap { around(it.first, it.second, 2) })
            NpcSpot.AVENUE -> ordered(scan { x, y ->
                !inPlaza(x, y) && countNear(x, y, 1) { a, b -> pavedAt(a, b) } > 0
            })
        }

        /** 자리를 찾지 못했을 때 — ① 길 옆 ② 광장 ③ 광장 주변 */
        val fallbackPools = listOf(
            ordered(scan { x, y -> !inPlaza(x, y) && countNear(x, y, 1) { a, b -> pavedAt(a, b) } > 0 }),
            ordered(scan { x, y -> inPlaza(x, y) }),
            around((PLAZA_X0 + PLAZA_X1) / 2, (PLAZA_Y0 + PLAZA_Y1) / 2, 7)
        )

        val out = ArrayList<Npc>()
        fun apart(x: Int, y: Int): Boolean =
            out.none { kotlin.math.abs(it.tileX - x) + kotlin.math.abs(it.tileY - y) < 3 }

        // 대화는 32px(=2칸) 안에서만 된다 — 대각선 한 칸(≈22px)까지는 괜찮지만 두 칸은 안 된다.
        // 그래서 "바로 옆에 플레이어가 설 수 있는 자리"가 아닌 곳에는 아예 사람을 세우지 않는다.
        val ring8 = listOf(0 to 1, 1 to 1, -1 to 1, 1 to 0, -1 to 0, 0 to -1, 1 to -1, -1 to -1)
        fun greetSpot(x: Int, y: Int): Pair<Int, Int>? {
            for ((dx, dy) in ring8) {
                val nx = x + dx; val ny = y + dy
                if (playerCanStand(nx, ny) && !keepClear.contains(ny * 100 + nx)) return nx to ny
            }
            return null
        }

        fun accept(x: Int, y: Int): Boolean =
            standable(x, y) && apart(x, y) && greetSpot(x, y) != null && reachable(x, y)

        for (person in cast) {
            var tile: Pair<Int, Int>? = null
            for ((x, y) in candidatesFor(person.spot)) {
                if (accept(x, y)) { tile = x to y; break }
            }
            if (tile == null) {
                for (pool in fallbackPools) {
                    for ((x, y) in pool) if (accept(x, y)) { tile = x to y; break }
                    if (tile != null) break
                }
            }
            val (px, py) = tile ?: continue
            val npc = Npc(person, px, py)
            // 인사 자리 — 남쪽(정면 대화)을 선호하는, 플레이어가 설 수 있는 바로 옆칸.
            // accept() 에서 옆칸이 있는 자리만 골랐으므로 항상 찾아진다.
            val (gx, gy) = greetSpot(px, py) ?: (px to (py + 1))
            npc.greetX = gx
            npc.greetY = gy
            out.add(npc)
        }
        return out
    }

    /** 집 내부 맵 (13x9) */
    fun buildHome(): GameMap {
        // 2K 화질 업그레이드: 집 내부를 13x9 -> 16x12로 넓혀 넓은 화면비(20:9)에서도 가득 차 보이게.
        val w = 16
        val h = 12
        val t = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        // 벽
        for (x in 0 until w) { t[0][x] = T.WALL_IN.ordinal; t[1][x] = T.WALL_IN.ordinal }
        for (y in 0 until h) { t[y][0] = T.WALL_IN.ordinal; t[y][w - 1] = T.WALL_IN.ordinal }
        // 현관문 (아래쪽 중앙)
        for (x in 0 until w) t[h - 1][x] = T.WALL_IN.ordinal
        t[h - 1][7] = T.HOUSE_DOOR.ordinal
        t[h - 1][8] = T.HOUSE_DOOR.ordinal
        // 창문
        t[1][2] = T.WALL_WIN.ordinal
        t[1][6] = T.WALL_WIN.ordinal
        t[1][10] = T.WALL_WIN.ordinal
        t[1][13] = T.WALL_WIN.ordinal
        // 화덕 (기본 제공!) — 오른쪽 상단 (화덕피자)
        t[2][12] = T.OVEN.ordinal; t[2][13] = T.OVEN.ordinal
        t[3][12] = T.OVEN.ordinal; t[3][13] = T.OVEN.ordinal
        // 가정용 오븐 (화덕 옆 주방 코너) — 일반 피자
        t[2][11] = T.RANGE_TOP.ordinal
        t[3][11] = T.RANGE.ordinal
        // 침대 — 왼쪽 상단
        t[2][2] = T.BED.ordinal; t[2][3] = T.BED.ordinal
        t[3][2] = T.BED.ordinal
        // 이사 박스
        t[6][2] = T.BOX.ordinal
        // 장식 슬롯 (DECOR 0~7 순서로 HomeScene과 매칭). 예전 세 칸은 앞에 유지한다.
        t[2][5] = T.DECOR.ordinal
        t[2][7] = T.DECOR.ordinal
        t[5][11] = T.DECOR.ordinal
        t[5][9] = T.DECOR.ordinal
        t[4][4] = T.DECOR.ordinal
        t[4][6] = T.DECOR.ordinal
        t[6][6] = T.DECOR.ordinal
        t[6][8] = T.DECOR.ordinal
        val home = Regions.byId["seoul"]!! // 내부맵은 지역 무관 (더미)
        val base = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        val pave = Array(h) { IntArray(w) }
        val deco = Array(h) { IntArray(w) }
        return GameMap(home, w, h, t, base, pave, deco, emptyList(), true, 7, h - 1)
    }

    /** 지역 랜드마크 내부 맵 (16x12) — 상호작용(전시/휴식/안내)은 LandmarkScene이 담당한다. */
    fun buildLandmark(region: RegionDef): GameMap {
        val w = 16
        val h = 12
        val t = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        // 벽 (위 2줄 + 좌우 + 아래)
        for (x in 0 until w) { t[0][x] = T.WALL_IN.ordinal; t[1][x] = T.WALL_IN.ordinal }
        for (y in 0 until h) { t[y][0] = T.WALL_IN.ordinal; t[y][w - 1] = T.WALL_IN.ordinal }
        for (x in 0 until w) t[h - 1][x] = T.WALL_IN.ordinal
        // 출구 (아래쪽 중앙) — 다시 지역으로
        t[h - 1][7] = T.LANDMARK_DOOR.ordinal
        t[h - 1][8] = T.LANDMARK_DOOR.ordinal
        // 큰 아치 창 — 전망을 위해 넉넉하게
        t[1][3] = T.WALL_WIN.ordinal
        t[1][6] = T.WALL_WIN.ordinal
        t[1][9] = T.WALL_WIN.ordinal
        t[1][12] = T.WALL_WIN.ordinal
        val base = Array(h) { IntArray(w) { T.FLOOR.ordinal } }
        val pave = Array(h) { IntArray(w) }
        val deco = Array(h) { IntArray(w) }
        return GameMap(
            region, w, h, t, base, pave, deco, emptyList(), false, -1, -1,
            hasLandmark = true, landmarkDoorX = 7, landmarkDoorY = h - 1
        )
    }
}

// ---------------------------------------------------------------------------
// 엔티티
// ---------------------------------------------------------------------------

/**
 * NPC 몸(바디) — 대기 동작과 대화 행동이 여기서 갈린다.
 * 이름·별명·옷차림·대사·사는 지역은 [NpcPerson](NpcRoster.kt)이 갖는다.
 */
enum class NpcKind { PROFESSOR, SHOP, VILLAGER, KID, ELDER }

/**
 * 지도에 서 있는 사람 한 명.
 *
 * 「한 사람은 한 장소에만」 — 정체성은 [NpcPerson] 이 들고 있고(어느 지역에 사는 누구인지),
 * 이 클래스는 그 사람이 **이 지도의 어느 타일에** 서 있는지만 나타낸다.
 */
class Npc(val person: NpcPerson, val tileX: Int, val tileY: Int) {
    val kind: NpcKind get() = person.kind
    val name: String get() = person.name
    /** 이름표에 작게 함께 뜨는 한 줄 (직함·동네 별명) */
    val title: String get() = person.title

    val x: Float get() = tileX * 16f
    val y: Float get() = tileY * 16f
    val cx: Float get() = x + 8f
    val cy: Float get() = y + 13f

    /** 인사 자리 — 말을 걸 때 플레이어가 서 있어야 할 타일 (퀘스트 길안내 목적지로도 쓴다) */
    var greetX: Int = tileX
    var greetY: Int = tileY + 1
    val greetCx: Float get() = greetX * 16f + 8f
    val greetCy: Float get() = greetY * 16f + 13f

    // 머리 위 말풍선 이모트 (♪, …, 💤 등) — WorldScene이 갱신
    var emote: String? = null
    var emoteT = 0f
    var emoteCd = 3f + ((person.id.hashCode() and 0x7FFFFFFF) % 7)
}

/**
 * 골목을 거니는 고양이.
 * 새를 살금살금 쫓다가, 펀치를 맞으면 빙글 돌며 하늘로 날아간다.
 */
class Cat(var x: Float, var y: Float) {
    var state = 0                 // 0 앉아있기, 1 걷기, 2 날아감
    var animT = (Math.random() * 3f).toFloat()   // 대기 동작 위상 (고양이마다 다르게)
    var idleT = 1.5f
    var fromX = 0f; var fromY = 0f
    var toX = 0f; var toY = 0f
    var hopT = 0f
    var faceLeft = true

    /** 새를 노리는 중 */
    var stalking = false
    var pouncing = false
    var pounceCued = false
    var pounceT = 0f
    /** 새를 놀라게 한 뒤나 길이 막힌 뒤 잠시 쉬는 시간 */
    var calmT = 0f
    /** 지금 노리는 새 (알림이 같은 새에 반복되지 않게) */
    var preyId: String? = null
    var stuckT = 0f

    // 펀치 — 날아가는 동안의 물리
    var launched = false
    var vx = 0f
    var vy = 0f
    var spin = 0f
    var spinV = 0f
    var launchT = 0f
    var air = 0f

    val cx: Float get() = x + 14f
    val cy: Float get() = y + 12f

    /** 플레이어가 민 방향으로 퉁겨 보낸다. dir 은 정규화하지 않아도 된다. */
    fun launch(dirX: Float, dirY: Float, spinSign: Float) {
        val len = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.001f)
        vx = dirX / len * 460f
        vy = dirY / len * 460f
        spin = 0f
        spinV = spinSign * 840f
        launchT = 0f
        air = 0f
        launched = true
        stalking = false
        pouncing = false
        pounceCued = false
        preyId = null
        calmT = 0f
        state = 2
        if (dirX != 0f) faceLeft = dirX < 0f
    }

    fun update(dt: Float, map: GameMap) {
        if (launched) {
            val step = dt.coerceAtMost(0.05f)
            launchT += step
            val drag = (1f - 0.55f * step).coerceAtLeast(0.9f)
            vx *= drag
            vy *= drag
            x += vx * step
            y += vy * step
            spin += spinV * step
            val u = (launchT / LAUNCH_TIME).coerceIn(0f, 1f)
            air = sin((u * Math.PI).toFloat()) * 34f
            animT += step
            return
        }
        if (calmT > 0f) calmT -= dt
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

    /**
     * 새를 향해 다가간다.
     * @return 1 추적 중, 2 잡았다, 0 길이 막힘
     */
    fun chase(tx: Float, ty: Float, dt: Float, map: GameMap): Int {
        calmT = 0f
        val dx = tx - cx
        val dy = ty - cy
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val wasPouncing = pouncing
        pouncing = dist < 28f
        if (pouncing && !wasPouncing) pounceCued = false
        if (!pouncing) pounceCued = false
        if (pouncing) pounceT += dt * 7f else pounceT = 0f
        val catchR = if (pouncing) 13f else 10f
        if (dist <= catchR) return 2
        val speed = if (pouncing) 128f else 36f
        val step = if (speed * dt < dist) speed * dt else dist
        val nx = x + dx / dist * step
        val ny = y + dy / dist * step
        val txx = ((nx + 14f) / 16f).toInt()
        val tyy = ((ny + 14f) / 16f).toInt()
        if (!map.walkableTile(txx, tyy)) return 0
        x = nx
        y = ny
        state = 1
        hopT = (hopT + dt / 0.24f) % 1f
        if (dx != 0f) faceLeft = dx < 0f
        animT += dt
        return if (dist - step <= catchR) 2 else 1
    }

    val walking: Boolean get() = state == 1 || launched
    /** 현재 동작의 진행도 0~1 (걸을 때는 한 칸 이동이 한 사이클, 날 때는 다리가 허우적) */
    val phase: Float get() = when {
        launched -> (launchT * 8f) % 1f
        state == 1 -> hopT % 1f
        else -> (animT / 3.4f) % 1f
    }
    val lift: Float get() = when {
        pouncing -> sin((pounceT * Math.PI).toFloat()).coerceAtLeast(0f) * 5f
        state == 1 && !launched -> sin((hopT * Math.PI).toFloat()) * 1.4f
        else -> 0f
    }
    val gone: Boolean get() = launched && launchT >= LAUNCH_TIME
    /** 날아가는 막판에 점점 옅어진다 */
    val fade: Float get() = if (!launched || launchT < 0.86f) 1f else ((LAUNCH_TIME - launchT) / (LAUNCH_TIME - 0.86f)).coerceIn(0f, 1f)

    companion object {
        const val LAUNCH_TIME = 1.18f
    }
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
    // 0 대기, 1 짧은 걸음/깡충, 2 플레이어에게서 도망, 3 짧은 활공
    var state = 0
    private val movement = BirdMovement.profile(def)
    private val random = Random(System.nanoTime() xor def.id.hashCode().toLong() xor x.toBits().toLong() xor y.toBits().toLong())
    var idleT = movement.nextRest(random)
    private var residenceLeft = 55f + random.nextFloat() * 65f
    var hopFromX = 0f; var hopFromY = 0f
    var hopToX = 0f; var hopToY = 0f
    var hopT = 0f
    var fleeVx = 0f; var fleeVy = 0f
    var fleeT = 0f
    var fleeCued = false             // 도망 효과음 재생 여부 (WorldScene에서 사용)
    /** 지형지물 뒤 — 새가 플레이어를 보지 못하는 상태 (매 갱신마다 다시 판정) */
    var hiddenFromPlayer = false
    /** 실제 적용된 은폐 품질 0..1 (지형지물 차폐 × 종군별 시각 능력) — 뷰파인더 표시 강도에 쓴다 */
    var coverQuality = 0f
    var facing = BirdFacing.LEFT     // 월드 방향 — 사진에서는 촬영자의 방위에 맞게 변환한다
    var renderPose = BirdPose.PERCHED
    /** 비행 스프라이트 호환용. 정면/뒷면일 때는 마지막 가로 방향을 유지한다. */
    var faceLeft: Boolean
        get() = facing != BirdFacing.RIGHT
        set(value) { facing = if (value) BirdFacing.LEFT else BirdFacing.RIGHT }
    var sprW = 24                    // 고정밀 스프라이트 크기 (생성 시 Assets에서 갱신)
    var sprH = 24

    val cx: Float get() = x + sprW / 2f
    val cy: Float get() = y + sprH * 0.72f
    val flightFrame: Int get() = if (state == 3) ((hopT * 8f).toInt() and 1) else ((fleeT * 11f).toInt() and 1)

    /** 새가 놀랐을 때의 탈출 방향과 속도도 종별 비행 특성에 맞춘다. */
    fun startFlee(awayX: Float, awayY: Float) {
        state = 2
        val len = sqrt(awayX * awayX + awayY * awayY).coerceAtLeast(0.01f)
        val dx = awayX / len
        val dy = awayY / len
        fleeVx = dx * movement.fleeSpeed * movement.horizontalBias
        fleeVy = dy * movement.fleeSpeed * 0.55f - movement.fleeLift
        facing = if (kotlin.math.abs(fleeVx) >= kotlin.math.abs(fleeVy)) {
            if (fleeVx < 0f) BirdFacing.LEFT else BirdFacing.RIGHT
        } else {
            if (fleeVy < 0f) BirdFacing.BACK else BirdFacing.FRONT
        }
        renderPose = BirdPose.ALERT
        fleeT = 0f
    }

    fun update(dt: Float, playerCx: Float, playerCy: Float, onBike: Boolean, sneaking: Boolean, map: GameMap, calmFactor: Float = 1f, bikeScare: Float = 1.4f) {
        // 새마다 다른 체류 시간. 가만히 기다려도 공간이 영구히 점유되지 않는다.
        residenceLeft -= dt
        if (residenceLeft <= 0f && state == 0) {
            startFlee(if (faceLeft) -1f else 1f, -0.25f)
        }
        val fleeTiles = when (def.tier) {
            Tier.COMMON -> 1.7f
            Tier.UNCOMMON -> 2.3f
            Tier.RARE -> 3.0f
            Tier.LEGEND -> 3.8f
        } * (if (sneaking) 0.6f else 1f) * (if (onBike) bikeScare else 1f) * calmFactor

        val fleeR = fleeTiles * 16f
        val dxPlayer = playerCx - cx
        val dyPlayer = playerCy - cy
        val dToPlayer = sqrt(dxPlayer * dxPlayer + dyPlayer * dyPlayer)
        // 지형지물의 시야 차폐 × 종군별 시각 능력 = 실제 은폐 품질(0..1).
        // 맹금류는 시력이 뛰어나 엄폐가 잘 통하지 않고, 물가·지상 무리는 은폐한 접근에 훨씬 둔감해진다.
        val cover = (map.concealmentAlong(playerCx, playerCy, cx, cy) * movement.coverEffect)
            .coerceIn(0f, 1f)
        coverQuality = cover
        hiddenFromPlayer = dToPlayer < fleeR && cover >= COVER_HIDDEN_MIN
        // 은폐가 완전할수록 도망 반경이 HIDDEN_FLEE_K(0.45)까지 줄어든다 — 부분 은폐는 그만큼만 줄인다.
        val coverK = 1f - (1f - HIDDEN_FLEE_K) * cover
        val effFleeR = if (cover > 0f) (fleeR * coverK).coerceAtLeast(9f) else fleeR

        when (state) {
            0 -> {
                if (dToPlayer < effFleeR) {
                    startFlee(cx - playerCx, cy - playerCy)
                    return
                }
                if (hiddenFromPlayer && dToPlayer < fleeR) renderPose = BirdPose.ALERT
                idleT -= dt
                if (idleT <= 0f) beginCharacteristicMove(map, playerCx, playerCy)
            }
            1, 3 -> {
                hopT = (hopT + dt / movement.stepSeconds).coerceAtMost(1f)
                x = hopFromX + (hopToX - hopFromX) * hopT
                y = hopFromY + (hopToY - hopFromY) * hopT
                if (hopT >= 1f) {
                    x = hopToX; y = hopToY
                    state = 0
                    idleT = movement.nextRest(random)
                    renderPose = when (movement.style) {
                        BirdMovementStyle.SONG_BIRD -> if (random.nextFloat() < 0.62f) BirdPose.FEEDING else BirdPose.PERCHED
                        BirdMovementStyle.WADER -> if (random.nextFloat() < 0.55f) BirdPose.FEEDING else BirdPose.PERCHED
                        BirdMovementStyle.WATERFOWL -> if (random.nextFloat() < 0.25f) BirdPose.FEEDING else BirdPose.PERCHED
                        BirdMovementStyle.RAPTOR -> if (random.nextFloat() < 0.55f) BirdPose.ALERT else BirdPose.PERCHED
                        BirdMovementStyle.AERIAL -> BirdPose.PERCHED
                        BirdMovementStyle.OWL -> if (random.nextFloat() < 0.7f) BirdPose.ALERT else BirdPose.PERCHED
                        BirdMovementStyle.GROUNDFORAGER -> if (random.nextFloat() < 0.5f) BirdPose.FEEDING else BirdPose.PERCHED
                    }
                }
            }
            2 -> {
                x += fleeVx * dt
                y += fleeVy * dt
                fleeT += dt
            }
        }
    }

    private fun beginCharacteristicMove(map: GameMap, playerCx: Float, playerCy: Float) {
        val dirs = when (movement.style) {
            BirdMovementStyle.WATERFOWL -> {
                if (random.nextFloat() < 0.72f) listOf(1 to 0, -1 to 0) else listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
            }
            BirdMovementStyle.AERIAL, BirdMovementStyle.RAPTOR -> listOf(
                1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to 1, 1 to -1, -1 to -1
            )
            else -> listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        }
        val shuffledDirs = dirs.toMutableList().also { java.util.Collections.shuffle(it, random) }

        for ((ddx, ddy) in shuffledDirs) {
            val nx = x + ddx * 16f * movement.stepTiles
            val ny = y + ddy * 16f * movement.stepTiles
            val tx = ((nx + sprW / 2f) / 16f).toInt()
            val ty = ((ny + sprH) / 16f).toInt()
            val nextCx = nx + sprW / 2f
            val nextCy = ny + sprH * 0.72f
            val nearPlayer = sqrt((nextCx - playerCx) * (nextCx - playerCx) + (nextCy - playerCy) * (nextCy - playerCy)) < 40f
            if (nearPlayer || BirdEcology.suitability(def, map, tx, ty) <= 0.0) continue

            hopFromX = x; hopFromY = y
            hopToX = nx; hopToY = ny
            hopT = 0f
            // Swallows and raptors glide between perches; ground and water birds stay low.
            state = if (movement.style == BirdMovementStyle.AERIAL || movement.style == BirdMovementStyle.RAPTOR) 3 else 1
            facing = when {
                ddx < 0 -> BirdFacing.LEFT
                ddx > 0 -> BirdFacing.RIGHT
                ddy < 0 -> BirdFacing.BACK
                else -> BirdFacing.FRONT
            }
            renderPose = BirdPose.ALERT
            return
        }

        // 웅크린 올빼미도 가끔 방향을 바꿔 주위를 살핀다.
        if (movement.style == BirdMovementStyle.OWL && random.nextFloat() < 0.5f) {
            facing = listOf(BirdFacing.LEFT, BirdFacing.RIGHT, BirdFacing.FRONT, BirdFacing.BACK)[random.nextInt(4)]
            renderPose = BirdPose.ALERT
        }
        idleT = movement.nextRest(random) * 0.45f
    }

    val gone: Boolean get() = state == 2 && fleeT > movement.leaveAfter

    /** 종별로 다른 걸음/활공 높이 */
    val hopLift: Float
        get() = if (state == 1 || state == 3) (kotlin.math.sin((hopT * Math.PI).toFloat()) * movement.lift) else 0f

    companion object {
        /** 완전 은폐(차폐 품질 1)일 때의 도망 반경 배율 — 평소보다 훨씬 가까이 다가갈 수 있다. */
        const val HIDDEN_FLEE_K = 0.45f
        /** '숨어있음'으로 인정하는 최소 은폐 품질. 이보다 약한 부분 은폐는 도망 감소 효과만 조금 낸다. */
        const val COVER_HIDDEN_MIN = 0.25f
    }
}
