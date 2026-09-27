package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs

/**
 * 모든 아트는 SVG 마스터 → VectorDrawable 파이프라인으로 생성되는 픽셀 아트 (v0.3).
 *
 * - 원본 마스터:  `art/svg/*.svg`  (`<symbol id="art_*">` 단위, 64px 그리드)
 * - 변환:         `tools/build_art.py` → `app/src/main/res/drawable/art_*.xml` (VectorDrawable)
 * - 런타임:       VectorDrawable을 2배 해상도로 래스터화한 뒤 최근접 다운스케일하여
 *               기존 픽셀 아트(타일·캐릭터 32px 등)와 동일한 크기의 비트맵으로 제공한다.
 * - 새(bird) 템플릿만 플레이스홀더 색상(아래 [BIRD_KEY_RGB])으로 레스터화하고
 *   종별 팔레트로 픽셀 교체(@[recolor]) 한다.
 */
class Assets(private val context: Context) {

    // 그리기용 공유 페인트 (스프라이트 확대는 최근접 — 필터 없음)
    val sprPaint = Paint()
    val pxPaint = Paint()
    val shadowPaint = Paint().apply { color = Color.argb(70, 30, 40, 30); isAntiAlias = true }

    // 플레이어 (32x32) -------------------------------------------------
    lateinit var playerDown: Array<Bitmap>      // [0] 서있기 [1][2] 걷기
    lateinit var playerUp: Array<Bitmap>
    lateinit var playerSide: Array<Bitmap>      // 오른쪽 방향
    lateinit var playerSideL: Array<Bitmap>     // 왼쪽 방향 (플립)
    lateinit var bikeDown: Bitmap
    lateinit var bikeUp: Bitmap
    lateinit var bikeSide: Bitmap
    lateinit var bikeSideL: Bitmap

    // NPC (32x32) ------------------------------------------------------
    lateinit var npcProfessor: Bitmap
    lateinit var npcShop: Bitmap
    lateinit var npcVillager: Bitmap
    lateinit var npcKid: Bitmap
    lateinit var npcElder: Bitmap

    // 고양이 (32x26) ---------------------------------------------------
    lateinit var catFrames: Array<Bitmap>       // [0] 앉음 [1][2] 걷기 (왼쪽 바라봄)
    lateinit var catFramesL: Array<Bitmap>      // 오른쪽 바라봄

    // 새 (종별, 템플릿 팔레트 교체) -------------------------------------
    lateinit var birds: Map<String, Bitmap>
    private var birdsFlipped: Map<String, Bitmap> = emptyMap()

    // 타일: [T.ordinal][variant] — WATER 는 4프레임, OVEN 은 2프레임 애니메이션
    lateinit var tiles: Array<Array<Bitmap>>

    // 아이콘 & 데코
    lateinit var pizzaIcon: Bitmap
    lateinit var pizzaIconBig: Bitmap
    lateinit var cloverIcon: Bitmap
    lateinit var cameraIcon: Bitmap
    lateinit var houseIcon: Bitmap
    lateinit var sunIcon: Bitmap
    lateinit var moonIcon: Bitmap
    lateinit var decorArt: Array<Bitmap>        // Decors.ALL 순서

    init {
        buildPlayers()
        buildNpcs()
        buildCat()
        buildBirds()
        buildTiles()
        buildIcons()
        buildDecorArt()
    }

    // ------------------------------------------------------------------
    // VectorDrawable 래스터화
    // ------------------------------------------------------------------

    /** 아트 리소스: (drawableId, viewportW, viewportH) — tools/build_art.py 출력과 동일한 이름 */
    private val artIds: Map<String, Triple<Int, Int, Int>> = mapOf(
        "bike_down" to Triple(R.drawable.art_bike_down, 64, 64),
        "bike_side" to Triple(R.drawable.art_bike_side, 64, 64),
        "bike_up" to Triple(R.drawable.art_bike_up, 64, 64),
        "bird_owl" to Triple(R.drawable.art_bird_owl, 38, 42),
        "bird_raptor" to Triple(R.drawable.art_bird_raptor, 56, 46),
        "bird_songbird" to Triple(R.drawable.art_bird_songbird, 48, 36),
        "bird_wader" to Triple(R.drawable.art_bird_wader, 44, 48),
        "bird_waterfowl" to Triple(R.drawable.art_bird_waterfowl, 52, 34),
        "camera" to Triple(R.drawable.art_camera, 40, 32),
        "cat_sit" to Triple(R.drawable.art_cat_sit, 64, 52),
        "cat_walk_1" to Triple(R.drawable.art_cat_walk_1, 64, 52),
        "cat_walk_2" to Triple(R.drawable.art_cat_walk_2, 64, 52),
        "clover" to Triple(R.drawable.art_clover, 28, 28),
        "decor_bookshelf" to Triple(R.drawable.art_decor_bookshelf, 64, 64),
        "decor_cactus" to Triple(R.drawable.art_decor_cactus, 64, 64),
        "decor_lamp" to Triple(R.drawable.art_decor_lamp, 64, 64),
        "decor_radio" to Triple(R.drawable.art_decor_radio, 64, 64),
        "decor_rug" to Triple(R.drawable.art_decor_rug, 64, 64),
        "decor_trophy" to Triple(R.drawable.art_decor_trophy, 64, 64),
        "house" to Triple(R.drawable.art_house, 28, 28),
        "moon" to Triple(R.drawable.art_moon, 28, 28),
        "npc_elder" to Triple(R.drawable.art_npc_elder, 64, 64),
        "npc_kid" to Triple(R.drawable.art_npc_kid, 64, 64),
        "npc_professor" to Triple(R.drawable.art_npc_professor, 64, 64),
        "npc_shop" to Triple(R.drawable.art_npc_shop, 64, 64),
        "npc_villager" to Triple(R.drawable.art_npc_villager, 64, 64),
        "pizza" to Triple(R.drawable.art_pizza, 44, 28),
        "player_down_0" to Triple(R.drawable.art_player_down_0, 64, 64),
        "player_down_1" to Triple(R.drawable.art_player_down_1, 64, 64),
        "player_down_2" to Triple(R.drawable.art_player_down_2, 64, 64),
        "player_side_0" to Triple(R.drawable.art_player_side_0, 64, 64),
        "player_side_1" to Triple(R.drawable.art_player_side_1, 64, 64),
        "player_side_2" to Triple(R.drawable.art_player_side_2, 64, 64),
        "player_up_0" to Triple(R.drawable.art_player_up_0, 64, 64),
        "player_up_1" to Triple(R.drawable.art_player_up_1, 64, 64),
        "player_up_2" to Triple(R.drawable.art_player_up_2, 64, 64),
        "sun" to Triple(R.drawable.art_sun, 32, 32),
        "tile_bed" to Triple(R.drawable.art_tile_bed, 64, 64),
        "tile_bench" to Triple(R.drawable.art_tile_bench, 64, 64),
        "tile_bldg_roof" to Triple(R.drawable.art_tile_bldg_roof, 64, 64),
        "tile_bldg_wall" to Triple(R.drawable.art_tile_bldg_wall, 64, 64),
        "tile_bldg_win_0" to Triple(R.drawable.art_tile_bldg_win_0, 64, 64),
        "tile_bldg_win_1" to Triple(R.drawable.art_tile_bldg_win_1, 64, 64),
        "tile_box" to Triple(R.drawable.art_tile_box, 64, 64),
        "tile_decor" to Triple(R.drawable.art_tile_decor, 64, 64),
        "tile_floor_0" to Triple(R.drawable.art_tile_floor_0, 64, 64),
        "tile_floor_1" to Triple(R.drawable.art_tile_floor_1, 64, 64),
        "tile_flower_0" to Triple(R.drawable.art_tile_flower_0, 64, 64),
        "tile_flower_1" to Triple(R.drawable.art_tile_flower_1, 64, 64),
        "tile_flower_2" to Triple(R.drawable.art_tile_flower_2, 64, 64),
        "tile_grass_0" to Triple(R.drawable.art_tile_grass_0, 64, 64),
        "tile_grass_1" to Triple(R.drawable.art_tile_grass_1, 64, 64),
        "tile_grass_2" to Triple(R.drawable.art_tile_grass_2, 64, 64),
        "tile_grass_3" to Triple(R.drawable.art_tile_grass_3, 64, 64),
        "tile_house_door" to Triple(R.drawable.art_tile_house_door, 64, 64),
        "tile_house_roof" to Triple(R.drawable.art_tile_house_roof, 64, 64),
        "tile_house_wall" to Triple(R.drawable.art_tile_house_wall, 64, 64),
        "tile_house_win" to Triple(R.drawable.art_tile_house_win, 64, 64),
        "tile_lamp" to Triple(R.drawable.art_tile_lamp, 64, 64),
        "tile_mountain_0" to Triple(R.drawable.art_tile_mountain_0, 64, 64),
        "tile_mountain_1" to Triple(R.drawable.art_tile_mountain_1, 64, 64),
        "tile_oven_0" to Triple(R.drawable.art_tile_oven_0, 64, 64),
        "tile_oven_1" to Triple(R.drawable.art_tile_oven_1, 64, 64),
        "tile_path_0" to Triple(R.drawable.art_tile_path_0, 64, 64),
        "tile_path_1" to Triple(R.drawable.art_tile_path_1, 64, 64),
        "tile_path_2" to Triple(R.drawable.art_tile_path_2, 64, 64),
        "tile_plaza_0" to Triple(R.drawable.art_tile_plaza_0, 64, 64),
        "tile_plaza_1" to Triple(R.drawable.art_tile_plaza_1, 64, 64),
        "tile_reed_0" to Triple(R.drawable.art_tile_reed_0, 64, 64),
        "tile_reed_1" to Triple(R.drawable.art_tile_reed_1, 64, 64),
        "tile_rock_0" to Triple(R.drawable.art_tile_rock_0, 64, 64),
        "tile_rock_1" to Triple(R.drawable.art_tile_rock_1, 64, 64),
        "tile_sand_0" to Triple(R.drawable.art_tile_sand_0, 64, 64),
        "tile_sand_1" to Triple(R.drawable.art_tile_sand_1, 64, 64),
        "tile_sand_2" to Triple(R.drawable.art_tile_sand_2, 64, 64),
        "tile_sign" to Triple(R.drawable.art_tile_sign, 64, 64),
        "tile_tallgrass_0" to Triple(R.drawable.art_tile_tallgrass_0, 64, 64),
        "tile_tallgrass_1" to Triple(R.drawable.art_tile_tallgrass_1, 64, 64),
        "tile_tree_0" to Triple(R.drawable.art_tile_tree_0, 64, 64),
        "tile_tree_1" to Triple(R.drawable.art_tile_tree_1, 64, 64),
        "tile_tunnel" to Triple(R.drawable.art_tile_tunnel, 64, 64),
        "tile_wall_in" to Triple(R.drawable.art_tile_wall_in, 64, 64),
        "tile_wall_win" to Triple(R.drawable.art_tile_wall_win, 64, 64),
        "tile_water_0" to Triple(R.drawable.art_tile_water_0, 64, 64),
        "tile_water_1" to Triple(R.drawable.art_tile_water_1, 64, 64),
        "tile_water_2" to Triple(R.drawable.art_tile_water_2, 64, 64),
        "tile_water_3" to Triple(R.drawable.art_tile_water_3, 64, 64),
    )

    /**
     * 픽셀 아트 표준 경로: 벡터를 네이티브의 2배 크기로 래스터화 한 뒤
     * 최근접 다운스케일 → 안티앨리어싱이 1px 계단처럼 닫히며 선명한 픽셀 모양.
     */
    private fun renderPixel(name: String, w: Int, h: Int): Bitmap {
        val (res, vw, vh) = artIds[name] ?: error("아트 없음: art_$name")
        val big = Bitmap.createBitmap(vw * 2, vh * 2, Bitmap.Config.ARGB_8888)
        val d = context.getDrawable(res) ?: error("리소스 없음: art_$name")
        d.setBounds(0, 0, vw * 2, vh * 2)
        d.draw(Canvas(big))
        return Bitmap.createScaledBitmap(big, w, h, false)
    }

    /** 소형 HUD 아이콘: 목표 크기로 직접 래스터화 (부드러운 엣지) */
    private fun renderIcon(name: String, w: Int, h: Int): Bitmap {
        val (res, vw, vh) = artIds[name] ?: error("아트 없음: art_$name")
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val d = context.getDrawable(res) ?: error("리소스 없음: art_$name")
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun flipH(src: Bitmap): Bitmap {
        val m = Matrix()
        m.postScale(-1f, 1f, src.width / 2f, 0f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }

    /** 색 밝기 조절 (팔레트 음영 파생용) */
    private fun shade(color: Int, f: Float): Int = Color.argb(
        255,
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    // ------------------------------------------------------------------
    // 플레이어 & 자전거
    // ------------------------------------------------------------------

    private fun buildPlayers() {
        playerDown = arrayOf(
            renderPixel("player_down_0", 32, 32),
            renderPixel("player_down_1", 32, 32),
            renderPixel("player_down_2", 32, 32)
        )
        playerUp = arrayOf(
            renderPixel("player_up_0", 32, 32),
            renderPixel("player_up_1", 32, 32),
            renderPixel("player_up_2", 32, 32)
        )
        playerSide = arrayOf(
            renderPixel("player_side_0", 32, 32),
            renderPixel("player_side_1", 32, 32),
            renderPixel("player_side_2", 32, 32)
        )
        playerSideL = Array(3) { flipH(playerSide[it]) }

        bikeDown = renderPixel("bike_down", 32, 32)
        bikeUp = renderPixel("bike_up", 32, 32)
        bikeSide = renderPixel("bike_side", 32, 32)
        bikeSideL = flipH(bikeSide)
    }

    // ------------------------------------------------------------------
    // NPC
    // ------------------------------------------------------------------

    private fun buildNpcs() {
        npcProfessor = renderPixel("npc_professor", 32, 32)
        npcShop = renderPixel("npc_shop", 32, 32)
        npcVillager = renderPixel("npc_villager", 32, 32)
        npcKid = renderPixel("npc_kid", 32, 32)
        npcElder = renderPixel("npc_elder", 32, 32)
    }

    // ------------------------------------------------------------------
    // 고양이
    // ------------------------------------------------------------------

    private fun buildCat() {
        val sit = renderPixel("cat_sit", 32, 26)
        val walk1 = renderPixel("cat_walk_1", 32, 26)
        val walk2 = renderPixel("cat_walk_2", 32, 26)
        catFrames = arrayOf(sit, walk1, walk2)
        catFramesL = arrayOf(flipH(sit), flipH(walk1), flipH(walk2))
    }

    // ------------------------------------------------------------------
    // 새 — 템플릿(플레이스홀더 색)을 종별 팔레트로 픽셀 교체
    // ------------------------------------------------------------------

    /** 플레이스홀더 RGB (art/svg/creatures.svg 코멘트 참조) */
    private val birdKeyRgb: Map<Char, Int> = mapOf(
        'B' to 0xFF00FF, 'b' to 0xB800B8, 'H' to 0xFF6BFF,
        'W' to 0x00E5FF, 'w' to 0x00A3B8, 't' to 0xFFD500, 'T' to 0xB89500,
        'k' to 0x7CFF00, 'c' to 0xFF7A00, 'l' to 0x0094FF,
        'e' to 0xFFFFFF, 'E' to 0x101010, 'v' to 0xB44CFF
    )
    private val birdRgbToKey: Map<Int, Char> =
        birdKeyRgb.entries.associate { (k, v) -> v to k }

    /** ARGB 픽셀에서 RGB를 근사 프레임 키로 — 정확히 일치하거나 가장 가까운 키. */
    private fun recolor(template: Bitmap, pal: Map<Char, Int>): Bitmap {
        val w = template.width
        val h = template.height
        val px = IntArray(w * h)
        template.getPixels(px, 0, w, 0, 0, w, h)
        val keyCache = HashMap<Int, Char?>()         // rgb -> key (AA 혼합색 캐시)
        for (i in px.indices) {
            val col = px[i]
            val a = col ushr 24
            if (a == 0) { px[i] = 0; continue }
            val rgb = col and 0xFFFFFF
            var key = birdRgbToKey[rgb]
            if (key == null) {
                key = keyCache.getOrPut(rgb) { nearestBirdKey(rgb) }
                if (key == null) { px[i] = col; continue }
            }
            val mapped = pal.getValue(key)
            px[i] = (a shl 24) or (mapped and 0xFFFFFF)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    /** 가장 가까운 플레이스홀더 키 (벡터 AA 경계 혼합색 대응). 너무 멀면 null. */
    private fun nearestBirdKey(rgb: Int): Char? {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        var best: Char? = null
        var bestDist = Int.MAX_VALUE
        for ((key, ref) in birdKeyRgb) {
            val dr = abs(((ref shr 16) and 0xFF) - r)
            val dg = abs(((ref shr 8) and 0xFF) - g)
            val db = abs((ref and 0xFF) - b)
            val dist = dr + dg + db
            if (dist < bestDist) { bestDist = dist; best = key }
        }
        // 채널 합계 150 이상이면 배경/기타 색으로 보고 미교체
        return if (bestDist < 150) best else null
    }

    private fun buildBirds() {
        // 템플릿 원본 (플레이스홀더 색 그대로, 최종 크기 = 네이티브/2 = 종전 스프라이트 크기)
        val templates = intArrayOf(0, 1, 2, 3, 4).map { i ->
            when (i) {
                1 -> renderPixel("bird_waterfowl", 26, 17)
                2 -> renderPixel("bird_wader", 22, 24)
                3 -> renderPixel("bird_raptor", 28, 23)
                4 -> renderPixel("bird_owl", 19, 21)
                else -> renderPixel("bird_songbird", 24, 18)
            }
        }

        val m = LinkedHashMap<String, Bitmap>()
        for (d in Birds.ALL) {
            val pal = mapOf(
                'B' to d.art.body, 'b' to shade(d.art.body, 0.72f), 'H' to shade(d.art.body, 1.18f),
                'W' to d.art.belly, 'w' to shade(d.art.belly, 0.82f),
                't' to d.art.wing, 'T' to shade(d.art.wing, 0.72f),
                'k' to d.art.beak, 'c' to d.art.crest, 'l' to d.art.leg,
                'e' to 0xFFFDFDF8.toInt(), 'E' to 0xFF1A1611.toInt(),
                'v' to 0xFF8FD4EA.toInt()
            )
            val tpl = templates[d.art.template.coerceIn(0, 4)]
            var bmp = recolor(tpl, pal)
            if (d.art.scale != 1f) {
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * d.art.scale).toInt().coerceAtLeast(1),
                    (bmp.height * d.art.scale).toInt().coerceAtLeast(1),
                    false
                )
            }
            m[d.id] = bmp
        }
        birds = m
    }

    // ------------------------------------------------------------------
    // 타일 (32x32) — T enum 순서 (Maps.kt)
    // ------------------------------------------------------------------

    /** (이름, 변형 수) — 다변형은 "<name>_<i>" 리소스, 단일은 그대로 */
    private val tileDefs: List<Pair<String, Int>> = listOf(
        "tile_grass" to 4, "tile_tallgrass" to 2, "tile_flower" to 3,
        "tile_path" to 3, "tile_plaza" to 2, "tile_sand" to 3,
        "tile_water" to 4,            // 물결 4프레임
        "tile_reed" to 2, "tile_tree" to 2, "tile_rock" to 2,
        "tile_mountain" to 2, "tile_bldg_wall" to 1, "tile_bldg_win" to 2,
        "tile_bldg_roof" to 1, "tile_house_roof" to 1, "tile_house_wall" to 1,
        "tile_house_win" to 1, "tile_house_door" to 1, "tile_tunnel" to 1,
        "tile_floor" to 2, "tile_wall_in" to 1, "tile_wall_win" to 1,
        "tile_oven" to 2,             // 화덕 불꽃 2프레임
        "tile_bed" to 1, "tile_box" to 1, "tile_decor" to 1,
        "tile_sign" to 1, "tile_bench" to 1, "tile_lamp" to 1
    )

    private fun buildTiles() {
        if (tileDefs.size != T.ALL.size) error("타일 정의 수 불일치: ${tileDefs.size} vs ${T.ALL.size}")
        tiles = tileDefs.map { (name, n) ->
            Array(n) { i ->
                renderPixel(if (n == 1) name else "${name}_$i", 32, 32)
            }
        }.toTypedArray()
    }

    fun tileVariant(tileOrdinal: Int, x: Int, y: Int): Int {
        val n = tiles[tileOrdinal].size
        if (n <= 1) return 0
        return ((x * 7 + y * 13) % n + n) % n
    }

    // ------------------------------------------------------------------
    // 아이콘
    // ------------------------------------------------------------------

    private fun buildIcons() {
        pizzaIcon = renderPixel("pizza", 22, 14)
        pizzaIconBig = Bitmap.createScaledBitmap(pizzaIcon, pizzaIcon.width * 4, pizzaIcon.height * 4, false)
        cloverIcon = renderIcon("clover", 14, 14)
        cameraIcon = renderIcon("camera", 20, 16)
        houseIcon = renderIcon("house", 14, 14)
        sunIcon = renderIcon("sun", 16, 16)
        moonIcon = renderIcon("moon", 14, 14)
    }

    // ------------------------------------------------------------------
    // 데코 아이템 (Decors.ALL 순서)
    // ------------------------------------------------------------------

    private fun buildDecorArt() {
        decorArt = arrayOf(
            renderPixel("decor_cactus", 32, 32),
            renderPixel("decor_bookshelf", 32, 32),
            renderPixel("decor_rug", 32, 32),
            renderPixel("decor_lamp", 32, 32),
            renderPixel("decor_trophy", 32, 32),
            renderPixel("decor_radio", 32, 32)
        )
    }

    // ------------------------------------------------------------------

    /** 새 비트맵 (안전 접근) */
    fun bird(id: String): Bitmap = birds[id] ?: birds.values.first()

    /** 오른쪽을 바라보는 새 (플립, 지연 생성) */
    fun birdFlipped(id: String): Bitmap {
        birdsFlipped[id]?.let { return it }
        val f = flipH(bird(id))
        birdsFlipped = birdsFlipped + (id to f)
        return f
    }

    fun birdW(id: String): Float = bird(id).width.toFloat()
    fun birdH(id: String): Float = bird(id).height.toFloat()
}
