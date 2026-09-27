package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Random

/**
 * 픽셀 아트 에셋 (v0.3).
 * - 캐릭터/새/도로/부속물: 코드로 생성 (티어 장비·13종 체형·오토타일)
 * - NPC/고양이/타일/아이콘/데코: SVG 마스터 -> VectorDrawable (art/svg, tools/build_art.py)
 * - 타일 32x32 / 캐릭터 32x32 / 새 13종 체형 + 9종 깃무늬 + 비행 2프레임
 * - 캐릭터/타일은 코드로 생성, 작은 장식 일러스트는 로컬 SVG — 네트워크·외부 라이브러리 없음
 */
class Assets(private val context: Context) {

    private fun c(v: Long): Int = v.toInt()

    // 그리기용 공유 페인트
    val sprPaint = Paint()                                  // 스프라이트 (최근접 샘플링)
    val pxPaint = Paint()                                   // 화면 업스케일 (픽셀 느낌 유지)
    val shadowPaint = Paint().apply { color = Color.argb(70, 30, 40, 30); isAntiAlias = true }

    // 플레이어 --------------------------------------------------------------
    // 레벨 등급(0~3)별 걷기 스프라이트 세트 — 겉모습(장비)이 좋아진다.
    class PlayerSet(
        val down: Array<Bitmap>,   // [0] 서있기 [1][2] 걷기
        val up: Array<Bitmap>,
        val side: Array<Bitmap>,   // 오른쪽 방향
        val sideL: Array<Bitmap>   // 왼쪽 방향 (플립)
    )

    // 성별 × 레벨 등급별 스프라이트 세트
    lateinit var playerTiers: Array<PlayerSet>   // 남자, gearTier로 인덱싱
    lateinit var femaleTiers: Array<PlayerSet>   // 여자

    // 기본(남자 0등급) 접근자 — 기존 코드 호환용
    val playerDown: Array<Bitmap> get() = playerTiers[0].down
    val playerUp: Array<Bitmap> get() = playerTiers[0].up
    val playerSide: Array<Bitmap> get() = playerTiers[0].side
    val playerSideL: Array<Bitmap> get() = playerTiers[0].sideL
    val femaleDown: Array<Bitmap> get() = femaleTiers[0].down
    val femaleUp: Array<Bitmap> get() = femaleTiers[0].up
    val femaleSide: Array<Bitmap> get() = femaleTiers[0].side
    val femaleSideL: Array<Bitmap> get() = femaleTiers[0].sideL

    /** 레벨 등급으로 스프라이트 세트 선택 (남자) */
    fun playerSet(tier: Int): PlayerSet = playerTiers[tier.coerceIn(0, playerTiers.size - 1)]

    /** 성별 + 레벨 등급으로 스프라이트 세트 선택 */
    fun playerSet(gender: String, tier: Int): PlayerSet {
        val tiers = if (gender == "female") femaleTiers else playerTiers
        return tiers[tier.coerceIn(0, tiers.size - 1)]
    }

    lateinit var bikeDown: Bitmap
    lateinit var bikeUp: Bitmap
    lateinit var bikeSide: Bitmap
    lateinit var bikeSideL: Bitmap

    // NPC -------------------------------------------------------------------
    lateinit var npcProfessor: Bitmap
    lateinit var npcShop: Bitmap
    lateinit var npcVillager: Bitmap
    lateinit var npcKid: Bitmap
    lateinit var npcElder: Bitmap

    // 고양이 -----------------------------------------------------------------
    lateinit var catFrames: Array<Bitmap>       // [0] 앉음 [1][2] 걷기
    lateinit var catFramesL: Array<Bitmap>      // 오른쪽 바라봄

    // 새 ---------------------------------------------------------------------
    lateinit var birds: Map<String, Bitmap>                  // 앉은 자세
    private val birdFlights = LinkedHashMap<String, Array<Bitmap>>() // 필요할 때 생성
    private var birdsFlipped: Map<String, Bitmap> = emptyMap()
    private val birdFlightsFlipped = LinkedHashMap<String, Array<Bitmap>>()

    // 타일 (32x32) ------------------------------------------------------------
    lateinit var tiles: Array<Array<Bitmap>>    // [T.ordinal][variant 또는 프레임]

    // 길 (오토타일 — Roads.kt) --------------------------------------------------
    private val roadCache = HashMap<Int, Bitmap>()
    lateinit var medallion: Array<Bitmap>       // 광장 문양 3x3
    lateinit var drain: Bitmap                  // 빗물받이
    lateinit var castShadow: Array<Bitmap>      // [위, 왼쪽, 왼쪽위] 접지 그림자

    /** 포장 타일 (이웃 비트마스크로 모양이 정해지고 캐시된다) */
    fun roadTile(mat: Int, mask: Int, variant: Int, sandy: Boolean): Bitmap {
        val key = (mat shl 13) or (mask shl 5) or (variant shl 1) or (if (sandy) 1 else 0)
        var b = roadCache[key]
        if (b == null) {
            b = RoadArt.tile(mat, mask, variant, sandy)
            roadCache[key] = b
        }
        return b
    }

    // 아이콘 ------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // 공용 헬퍼
    // -----------------------------------------------------------------------

    private fun sprite(rows: List<String>, pal: Map<Char, Int>): Bitmap {
        val w = rows.maxOf { it.length }
        val h = rows.size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in rows.indices) {
            val r = rows[y]
            for (x in 0 until r.length) {
                val col = pal[r[x]] ?: continue
                bmp.setPixel(x, y, col)
            }
        }
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

    // -----------------------------------------------------------------------
    // SVG 마스터 아트 로더 (art/svg/*.svg -> res/drawable/art_*.xml)
    // tools/build_art.py 가 변환한 VectorDrawable을 래스터화한다.
    // -----------------------------------------------------------------------

    /** 아트 리소스: (drawableId, viewportW, viewportH) */
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
     * 최근접 다운스케일 — AA가 1px 계단처럼 닫히며 선명한 픽셀 모양.
     */
    private fun renderPixel(name: String, w: Int, h: Int): Bitmap {
        val (res, vw, vh) = artIds[name] ?: error("아트 정의 없음: art_$name")
        val big = Bitmap.createBitmap(vw * 2, vh * 2, Bitmap.Config.ARGB_8888)
        val d = context.getDrawable(res) ?: error("리소스 없음: art_$name")
        d.setBounds(0, 0, vw * 2, vh * 2)
        d.draw(Canvas(big))
        return Bitmap.createScaledBitmap(big, w, h, false)
    }

    /** 소형 HUD 아이콘: 목표 크기로 직접 래스터화 (부드러운 엣지) */
    private fun renderIcon(name: String, w: Int, h: Int): Bitmap {
        val (res, vw, vh) = artIds[name] ?: error("아트 정의 없음: art_$name")
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val d = context.getDrawable(res) ?: error("리소스 없음: art_$name")
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }

    // -----------------------------------------------------------------------
    // 사람 (32x32, 절차 생성: 외곽선+음영)
    // dir: 0=아래(정면) 1=위(뒤) 2=오른쪽(측면) / frame: 0 서있기 1,2 걷기
    // -----------------------------------------------------------------------

    private data class Pal(
        val hair: Int, val hair2: Int, val skin: Int, val skin2: Int,
        val top: Int, val top2: Int, val pants: Int, val pants2: Int,
        val shoe: Int, val line: Int, val pack: Int, val pack2: Int,
        val eye: Int, val blush: Int
    )

    /** 탐조가 장비 (레벨 등급에 따라 겉모습이 좋아진다) */
    private class Gear(
        val cap: Int? = null,        // 탐조 모자 색 (null이면 없음)
        val capDark: Int = 0,
        val vest: Int? = null,       // 탐조 조끼 색
        val vestDark: Int = 0,
        val scarf: Int? = null,      // 목도리 색
        val brim: Boolean = false,   // 챙 넓은 모자
        val feather: Int? = null     // 모자 깃털 장식
    )

    private fun person(
        dir: Int, frame: Int, pl: Pal,
        glasses: Boolean = false, apron: Boolean = false,
        cane: Boolean = false, small: Boolean = false,
        gear: Gear? = null
    ): Bitmap {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
            p.color = col; cv.drawRect(l, t, rr, b, p)
        }
        fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
            p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
        }
        fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
            p.color = col; cv.drawCircle(cx, cy, rad, p)
        }

        val oy = if (small) 3f else 0f                 // 아이는 키가 작다
        val legTop = if (small) 26f else 24.5f

        // ----- 다리 (모든 방향 공통 프레임) -----
        fun legs(xL: Float, xR: Float, wide: Float) {
            val liftL = frame == 1
            val liftR = frame == 2
            // 바지
            if (!liftL) {
                r(xL - 1, legTop - 1, xL + wide + 1, 30.5f, pl.line)
                r(xL, legTop, xL + wide, 30f, pl.pants)
            } else {
                r(xL - 1, legTop - 1, xL + wide + 1, 28.5f, pl.line)
                r(xL, legTop, xL + wide, 28f, pl.pants)
            }
            if (!liftR) {
                r(xR - 1, legTop - 1, xR + wide + 1, 30.5f, pl.line)
                r(xR, legTop, xR + wide, 30f, pl.pants)
            } else {
                r(xR - 1, legTop - 1, xR + wide + 1, 28.5f, pl.line)
                r(xR, legTop, xR + wide, 28f, pl.pants)
            }
            r(xL + wide / 2f - 0.6f, legTop, xL + wide / 2f + 0.6f, 30f, pl.pants2)
            // 신발
            val shoeL = if (liftL) 27.6f else 29.2f
            val shoeR = if (liftR) 27.6f else 29.2f
            r(xL - 1f, shoeL, xL + wide + 1.4f, shoeL + 2.2f, pl.shoe)
            r(xR - 1.4f, shoeR, xR + wide + 1f, shoeR + 2.2f, pl.shoe)
        }

        // ----- 몸통 -----
        fun torso(left: Float, right: Float, topY: Float) {
            o(left - 1, topY - 1, right + 1, legTop + 1.5f, 4f, pl.line)
            o(left, topY, right, legTop + 0.5f, 3.5f, pl.top)
            r(right - (right - left) * 0.32f, topY + 1, right - 1, legTop - 0.5f, pl.top2)
            r(left + (right - left) * 0.28f, topY, left + (right - left) * 0.44f, topY + 2.2f, pl.top2)
            if (apron) {
                o(left + 1.5f, topY + 2.5f, right - 1.5f, legTop - 1f, 2.5f, c(0xFFFDF6E8))
                r(left + 3f, topY + 2.5f, right - 3f, topY + 4f, c(0xFFE8DFC8))
                r(left + 4f, topY + 8f, right - 4f, topY + 9.5f, c(0xFFE8DFC8))
            }
        }

        when (dir) {
            0 -> { // 정면
                torso(9.5f, 22.5f, 15.5f + oy)
                // 팔
                val armY = if (frame == 1) -1f else if (frame == 2) 1f else 0f
                r(7.6f, 16.5f + oy, 10.2f, 22.5f + oy + armY, pl.top2)
                r(21.8f, 16.5f + oy, 24.4f, 22.5f + oy - armY, pl.top2)
                r(8.0f, 22.2f + oy + armY, 9.8f, 24.4f + oy + armY, pl.skin)
                r(22.2f, 22.2f + oy - armY, 24.0f, 24.4f + oy - armY, pl.skin)
                legs(11.2f, 17.4f, 3.6f)
                // 머리
                cir(16f, 9.2f + oy, 7.4f, pl.line)
                cir(16f, 9.2f + oy, 6.6f, pl.skin)
                cir(16f, 7.6f + oy, 6.3f, pl.hair)
                r(10f, 8.4f + oy, 22f, 9.6f + oy, pl.hair)
                r(10f, 8.6f + oy, 11.8f, 14.6f + oy, pl.hair)
                r(20.2f, 8.6f + oy, 22f, 14.6f + oy, pl.hair)
                r(13f, 8.6f + oy, 15f, 10.2f + oy, pl.hair)
                r(17f, 8.6f + oy, 19f, 10.2f + oy, pl.hair)
                r(12.2f, 9.8f + oy, 19.8f, 13.4f + oy, pl.skin)
                // 눈/볼
                r(12.6f, 10.6f + oy, 14f, 12.4f + oy, pl.eye)
                r(18f, 10.6f + oy, 19.4f, 12.4f + oy, pl.eye)
                r(11.2f, 12.6f + oy, 12.8f, 13.8f + oy, pl.blush)
                r(19.2f, 12.6f + oy, 20.8f, 13.8f + oy, pl.blush)
                if (glasses) {
                    p.style = Paint.Style.STROKE; p.strokeWidth = 1.1f
                    p.color = pl.line
                    cv.drawCircle(13.3f, 11.5f + oy, 2.4f, p)
                    cv.drawCircle(18.7f, 11.5f + oy, 2.4f, p)
                    cv.drawLine(15.5f, 11.4f + oy, 16.5f, 11.4f + oy, p)
                    p.style = Paint.Style.FILL
                }
            }
            1 -> { // 뒤
                torso(9.5f, 22.5f, 15.5f + oy)
                val armY = if (frame == 1) -1f else if (frame == 2) 1f else 0f
                r(7.6f, 16.5f + oy, 10.2f, 22.5f + oy + armY, pl.top2)
                r(21.8f, 16.5f + oy, 24.4f, 22.5f + oy - armY, pl.top2)
                legs(11.2f, 17.4f, 3.6f)
                // 머리(뒤통수)
                cir(16f, 9.2f + oy, 7.4f, pl.line)
                cir(16f, 9.2f + oy, 6.6f, pl.hair)
                r(10.5f, 11.5f + oy, 21.5f, 13.6f + oy, pl.hair2)
                r(12f, 6f + oy, 20f, 7.4f + oy, pl.hair2)
                // 배낭
                o(10.4f, 16.2f + oy, 21.6f, 24.2f + oy, 3f, pl.line)
                o(11.2f, 17f + oy, 20.8f, 23.4f + oy, 2.5f, pl.pack)
                r(13f, 18.6f + oy, 19f, 22.2f + oy, pl.pack2)
                r(12.2f, 17.4f + oy, 19.8f, 18.2f + oy, pl.pack2)
            }
            else -> { // 오른쪽 측면
                torso(11.5f, 21.5f, 15.5f + oy)
                // 앞 팔 (흔들림)
                val swing = if (frame == 1) -2f else if (frame == 2) 2f else 0f
                r(14.5f, 17f + oy, 17.5f, 22.5f + oy + swing, pl.top2)
                r(14.9f, 22.2f + oy + swing, 16.9f, 24.2f + oy + swing, pl.skin)
                // 측면 다리(보폭)
                if (frame == 0) {
                    r(13.5f, legTop - 1, 19.5f, 30.5f, pl.line)
                    r(14.5f, legTop, 18.5f, 30f, pl.pants)
                    r(13.8f, 29.2f, 19.6f, 31.2f, pl.shoe)
                } else if (frame == 1) {
                    r(16f, legTop - 1, 20.5f, 29.5f, pl.line)
                    r(17f, legTop, 19.5f, 29f, pl.pants)
                    r(17.6f, 28.4f, 22.4f, 30.4f, pl.shoe)
                    r(10.5f, legTop - 1, 15f, 29.5f, pl.line)
                    r(11.5f, legTop, 14f, 29f, pl.pants2)
                    r(9.8f, 28.2f, 14.4f, 30.2f, pl.shoe)
                } else {
                    r(14f, legTop - 1, 19f, 30f, pl.line)
                    r(15f, legTop, 18f, 29.5f, pl.pants)
                    r(14.4f, 28.6f, 19.8f, 30.6f, pl.shoe)
                    r(12f, legTop, 15f, 28.5f, pl.pants2)
                    r(11.4f, 27.6f, 15.6f, 29.6f, pl.shoe)
                }
                // 머리(측면)
                cir(17f, 9.2f + oy, 7f, pl.line)
                cir(17f, 9.2f + oy, 6.2f, pl.skin)
                p.color = pl.hair
                cv.drawArc(RectF(11f, 3f + oy, 23f, 11.8f + oy), 180f, 180f, true, p)
                r(11f, 8f + oy, 13.6f, 14.8f + oy, pl.hair)
                r(12.4f, 11.8f + oy, 13.6f, 14.6f + oy, pl.hair2)
                r(18.6f, 7.8f + oy, 21.2f, 9.8f + oy, pl.hair)
                // 눈/코/볼
                r(19.6f, 10.2f + oy, 21f, 12f + oy, pl.eye)
                r(22.6f, 11.2f + oy, 23.6f, 12.4f + oy, pl.skin2)
                r(19.8f, 12.8f + oy, 21.2f, 13.8f + oy, pl.blush)
            }
        }

        // ----- 탐조가 장비 (레벨 등급별 겉모습) -----
        if (gear != null) {
            // 목도리 (목 언저리 밴드)
            gear.scarf?.let { sc ->
                when (dir) {
                    0 -> {
                        r(11.6f, 14.2f + oy, 20.4f, 16.4f + oy, sc)
                        r(18.2f, 16f + oy, 20.2f, 20.2f + oy, sc)   // 늘어진 자락
                    }
                    1 -> r(11.4f, 14f + oy, 20.6f, 16f + oy, sc)
                    else -> {
                        r(13.6f, 14.2f + oy, 20.6f, 16.4f + oy, sc)
                        r(13.4f, 16f + oy, 15.4f, 19.6f + oy, sc)
                    }
                }
            }
            // 조끼 (앞면/측면만 — 뒷면은 배낭이 가림)
            gear.vest?.let { vs ->
                when (dir) {
                    0 -> {
                        r(9.6f, 16.4f + oy, 12.8f, legTop + 0.2f, vs)
                        r(19.2f, 16.4f + oy, 22.4f, legTop + 0.2f, vs)
                        r(13.2f, 15.8f + oy, 18.8f, 17.6f + oy, vs)
                        r(9.6f, 16.4f + oy, 10.4f, legTop + 0.2f, gear.vestDark)
                        r(21.6f, 16.4f + oy, 22.4f, legTop + 0.2f, gear.vestDark)
                    }
                    2 -> {
                        r(14.6f, 16.4f + oy, 20.6f, legTop + 0.2f, vs)
                        r(14.6f, 16.4f + oy, 15.4f, legTop + 0.2f, gear.vestDark)
                    }
                    else -> {}
                }
            }
            // 모자 (탐조 캡 / 챙 넓은 모자)
            gear.cap?.let { cp ->
                when (dir) {
                    0, 1 -> {
                        // 돔
                        o(10.2f, 2.4f + oy, 21.8f, 8.4f + oy, 3.2f, pl.line)
                        o(10.8f, 2.8f + oy, 21.2f, 8f + oy, 3f, cp)
                        r(11.4f, 3.2f + oy, 20.6f, 5.2f + oy, gear.capDark)
                        // 챙 (정면만, 넓은 모자는 더 크게)
                        if (dir == 0) {
                            if (gear.brim) {
                                o(7.2f, 7.4f + oy, 24.8f, 9.6f + oy, 2f, pl.line)
                                o(7.6f, 7.6f + oy, 24.4f, 9.2f + oy, 1.6f, cp)
                            } else {
                                r(9f, 7.6f + oy, 21.2f, 9.2f + oy, pl.line)
                                r(9.4f, 7.8f + oy, 20.8f, 8.9f + oy, gear.capDark)
                            }
                        }
                    }
                    else -> {
                        o(11.2f, 2.4f + oy, 22.8f, 8.4f + oy, 3.2f, pl.line)
                        o(11.8f, 2.8f + oy, 22.2f, 8f + oy, 3f, cp)
                        r(12.4f, 3.2f + oy, 21.6f, 5.2f + oy, gear.capDark)
                        // 옆 챙 (오른쪽으로)
                        if (gear.brim) {
                            o(19.6f, 7f + oy, 27.2f, 9f + oy, 1.8f, cp)
                        } else {
                            r(20.2f, 7.2f + oy, 26.4f, 8.6f + oy, cp)
                        }
                    }
                }
                // 깃털 장식
                gear.feather?.let { ft ->
                    when (dir) {
                        0, 1 -> {
                            r(20.4f, 1.6f + oy, 21.6f, 5.4f + oy, ft)
                            r(21.2f, 2.2f + oy, 22.4f, 4.2f + oy, ft)
                        }
                        else -> {
                            r(12.2f, 1.4f + oy, 13.4f, 5.2f + oy, ft)
                            r(11.4f, 2f + oy, 12.6f, 4f + oy, ft)
                        }
                    }
                }
            }
        }

        if (cane) {
            r(23.8f, 15f + oy, 25.2f, 30.5f, c(0xFF8A5A33))
            r(22.6f, 14f + oy, 26f, 15.6f, c(0xFF6B431F))
        }
        return bmp
    }

    // -----------------------------------------------------------------------
    // 플레이어 & 자전거
    // -----------------------------------------------------------------------

    private fun buildPlayers() {
        val pl = Pal(
            hair = c(0xFF4A2F1D), hair2 = c(0xFF382314), skin = c(0xFFFFD9B0), skin2 = c(0xFFE8B88C),
            top = c(0xFFF2B63C), top2 = c(0xFFD99B26), pants = c(0xFF4A6FA5), pants2 = c(0xFF3A5A8A),
            shoe = c(0xFF7A4A2B), line = c(0xFF33241C), pack = c(0xFFD9534F), pack2 = c(0xFFB23F44),
            eye = c(0xFF2E2620), blush = c(0xFFF2A58C)
        )
        // 레벨 등급별 장비: 0=새내기, 1=견습(캡), 2=숙련(캡+조끼), 3=명인(챙모자+조끼+목도리+깃털)
        val gearTiers = arrayOf<Gear?>(
            null,
            Gear(cap = c(0xFF4F8F6A), capDark = c(0xFF3C6E50)),
            Gear(
                cap = c(0xFF3F6FA0), capDark = c(0xFF2F5580),
                vest = c(0xFF6B8E4E), vestDark = c(0xFF52703B)
            ),
            Gear(
                cap = c(0xFF8A5A2B), capDark = c(0xFF6E4620),
                vest = c(0xFF3E6B57), vestDark = c(0xFF2C4E3F),
                scarf = c(0xFFD9534F), brim = true, feather = c(0xFFF2D06B)
            )
        )
        fun buildTiers(pal: Pal): Array<PlayerSet> = Array(gearTiers.size) { tier ->
            val g = gearTiers[tier]
            val down = Array(3) { person(0, it, pal, gear = g) }
            val up = Array(3) { person(1, it, pal, gear = g) }
            val side = Array(3) { person(2, it, pal, gear = g) }
            val sideL = Array(3) { flipH(side[it]) }
            PlayerSet(down, up, side, sideL)
        }
        playerTiers = buildTiers(pl)
        // 여자 팔레트(머리·상의·바지색만 다름) — 장비는 동일하게 진화
        val fp = pl.copy(hair = c(0xFF6A3155), hair2 = c(0xFF4A203D), top = c(0xFFDB6B9A), top2 = c(0xFFB84D7B), pants = c(0xFF66529B), pants2 = c(0xFF4D3C7C))
        femaleTiers = buildTiers(fp)

        val bikeCol = c(0xFFC9503A)
        val bikeDark = c(0xFF8A3326)
        val tire = c(0xFF3A3A44)
        val tireIn = c(0xFF5A5A66)
        val metal = c(0xFF9AA0AD)

        fun bikeBase(cv: Canvas, p: Paint) {
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            // 바퀴
            cir(8f, 26f, 6f, c(0xFF23232B)); cir(8f, 26f, 5.1f, tire); cir(8f, 26f, 2f, tireIn)
            cir(24f, 26f, 6f, c(0xFF23232B)); cir(24f, 26f, 5.1f, tire); cir(24f, 26f, 2f, tireIn)
            cir(8f, 26f, 1f, metal); cir(24f, 26f, 1f, metal)
            // 프레임
            r(8.5f, 19.5f, 23.5f, 22f, bikeCol)
            r(12f, 14.5f, 14.5f, 20f, bikeCol)
            r(20.5f, 13.5f, 23f, 20.5f, bikeCol)
            r(8.5f, 21.4f, 23.5f, 22.4f, bikeDark)
        }

        // 옆모습 (오른쪽)
        run {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            bikeBase(cv, p)
            // 안장/핸들
            r(10.2f, 13.6f, 15.6f, 15.6f, c(0xFF33241C))
            r(19f, 11.8f, 25.2f, 13.8f, c(0xFF33241C))
            r(23.4f, 11.2f, 25.6f, 14.4f, c(0xFF23232B))
            // 페달
            cir(15f, 22.2f, 1.7f, metal)
            r(13.8f, 23.2f, 16.6f, 24.6f, c(0xFF23232B))
            // 라이더 — 다리
            r(12.5f, 15.5f, 16.2f, 21.5f, pl.pants)
            r(13.2f, 20.8f, 17.2f, 23.2f, pl.shoe)
            // 몸통(살짝 숙임)
            o(12.8f, 6.8f, 19.8f, 17f, 3.5f, pl.top)
            r(16.5f, 8f, 19.4f, 16.2f, pl.top2)
            // 팔
            r(16.5f, 9.5f, 19f, 11.5f, pl.top2)
            r(19f, 10.2f, 21.6f, 11.8f, pl.skin)
            // 머리 + 헬멧
            cir(18.2f, 5f, 5f, pl.line)
            cir(18.2f, 5f, 4.3f, pl.skin)
            p.color = c(0xFFD9534F)
            cv.drawArc(RectF(13.6f, 0.4f, 22.8f, 7.4f), 180f, 180f, true, p)
            r(13.8f, 4.2f, 23f, 5.6f, c(0xFFB23F44))
            r(15.4f, 5.4f, 21f, 6.2f, pl.skin)
            bikeSide = bmp
            bikeSideL = flipH(bmp)
        }

        // 뒤모습
        run {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            // 뒷바퀴
            cir(16f, 28.5f, 4.4f, c(0xFF23232B)); cir(16f, 28.5f, 3.6f, tire); cir(16f, 28.5f, 1.4f, tireIn)
            // 라이더(뒤)
            cir(16f, 8.2f, 5.6f, pl.line)
            cir(16f, 8.2f, 4.9f, pl.hair)
            r(11.5f, 10.6f, 20.5f, 12.4f, pl.hair2)
            o(10.6f, 14.6f, 21.4f, 23.6f, 3f, pl.top)
            o(11.4f, 15.4f, 20.6f, 22.8f, 2.5f, pl.pack)
            r(13.2f, 17f, 18.8f, 21.4f, pl.pack2)
            // 팔(핸들쪽)
            r(8.4f, 16.5f, 11.6f, 19.5f, pl.top2)
            r(20.4f, 16.5f, 23.6f, 19.5f, pl.top2)
            r(6.8f, 16.2f, 9.2f, 18.6f, c(0xFF23232B))
            r(22.8f, 16.2f, 25.2f, 18.6f, c(0xFF23232B))
            // 다리
            r(11.6f, 23.6f, 15f, 27.6f, pl.pants)
            r(17f, 23.6f, 20.4f, 27.6f, pl.pants)
            r(11f, 26.6f, 15.4f, 28.8f, pl.shoe)
            r(16.6f, 26.6f, 21f, 28.8f, pl.shoe)
            bikeUp = bmp
        }

        // 정면
        run {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            // 앞바퀴
            cir(16f, 28.5f, 4.4f, c(0xFF23232B)); cir(16f, 28.5f, 3.6f, tire); cir(16f, 28.5f, 1.4f, tireIn)
            // 핸들바(정면)
            r(7f, 17.2f, 25f, 19.4f, c(0xFF33241C))
            r(6.4f, 16.6f, 8.6f, 19.8f, c(0xFF23232B))
            r(23.4f, 16.6f, 25.6f, 19.8f, c(0xFF23232B))
            // 라이더(정면)
            cir(16f, 8.2f, 5.6f, pl.line)
            cir(16f, 8.2f, 4.9f, pl.skin)
            p.color = c(0xFFD9534F)
            cv.drawArc(RectF(10.6f, 3.4f, 21.4f, 9.6f), 180f, 180f, true, p)
            r(10.8f, 6.8f, 21.2f, 8.2f, c(0xFFB23F44))
            r(12.8f, 8.6f, 14.4f, 10.6f, pl.eye)
            r(17.6f, 8.6f, 19.2f, 10.6f, pl.eye)
            r(14.6f, 11.4f, 17.4f, 12.4f, pl.blush)
            o(10.6f, 14.6f, 21.4f, 23.6f, 3f, pl.top)
            r(18f, 15.4f, 21f, 23f, pl.top2)
            r(8.4f, 16.5f, 11.6f, 19.5f, pl.top2)
            r(20.4f, 16.5f, 23.6f, 19.5f, pl.top2)
            r(11.6f, 23.6f, 15f, 27.6f, pl.pants)
            r(17f, 23.6f, 20.4f, 27.6f, pl.pants)
            r(11f, 26.6f, 15.4f, 28.8f, pl.shoe)
            r(16.6f, 26.6f, 21f, 28.8f, pl.shoe)
            bikeDown = bmp
        }
    }

    // -----------------------------------------------------------------------
    // NPC
    // -----------------------------------------------------------------------

    private fun npcPal(
        hair: Long, top: Long, top2: Long, pants: Long, pack: Long
    ): Pal = Pal(
        hair = c(hair), hair2 = shade(c(hair), 0.75f),
        skin = c(0xFFFFD9B0), skin2 = c(0xFFE8B88C),
        top = c(top), top2 = c(top2), pants = c(pants), pants2 = shade(c(pants), 0.75f),
        shoe = c(0xFF3A3A44), line = c(0xFF33241C), pack = c(pack), pack2 = shade(c(pack), 0.75f),
        eye = c(0xFF2E2620), blush = c(0xFFF2A58C)
    )

    private fun buildNpcs() {
        npcProfessor = renderPixel("npc_professor", 32, 32)
        npcShop = renderPixel("npc_shop", 32, 32)
        npcVillager = renderPixel("npc_villager", 32, 32)
        npcKid = renderPixel("npc_kid", 32, 32)
        npcElder = renderPixel("npc_elder", 32, 32)
    }

    // -----------------------------------------------------------------------
    // 고양이 (32x26)
    // -----------------------------------------------------------------------

    private fun buildCat() {
        val sit = renderPixel("cat_sit", 32, 26)
        val walk1 = renderPixel("cat_walk_1", 32, 26)
        val walk2 = renderPixel("cat_walk_2", 32, 26)
        catFrames = arrayOf(sit, walk1, walk2)
        catFramesL = arrayOf(flipH(sit), flipH(walk1), flipH(walk2))
    }

    // -----------------------------------------------------------------------
    // 새 (13종 실루엣 + 종별 머리색/깃무늬 + 도주 비행 2프레임)
    // -----------------------------------------------------------------------

    private data class BirdAnchors(
        val wingL: Float, val wingT: Float, val wingR: Float, val wingB: Float,
        val chestX: Float, val chestY: Float,
        val eyeX: Float, val eyeY: Float
    )

    private fun buildBirds() {
        // 모든 스프라이트는 왼쪽을 바라본다. h=머리, a=포인트 컬러.
        val songbird = listOf(
            "..........................",
            "...cc.....................",
            "..chhh....................",
            ".chheEh...................",
            "kkhhhhBB..................",
            "kkhhhBBBBtt...............",
            ".hhhHBBBtttttt............",
            ".hhBBBBttTTTtttt...........",
            "..BBBBttTTTTTtttttt.......",
            "..BBBWWtTTTTTTtttttttt....",
            "..BBWWWWttTTTttttttttttt..",
            "...WWWWWWtttttttttttttttt.",
            "....WWWWWWWWWWWWWtttt......",
            ".....WWWWWWWWWWW...........",
            "......WWWWWWWWW............",
            ".......ll...ll.............",
            ".......ll...ll.............",
            "......lll...lll............"
        )
        val waterfowl = listOf(
            "............................",
            "....cc......................",
            "...chhhh....................",
            "..chheEh....................",
            ".kkkhhhh....................",
            ".kkkhhhhBBB.................",
            "....hhhhBBBBtttt............",
            ".....BBBBBBttttttttt........",
            "....HBBBBBttTTTTTtttttt.....",
            "...HHBBBBttTTTTTTTtttttttt..",
            "..HBBBBBBttTTTTTTtttttttttt.",
            "..BBBBBWWttTTTTtttttttttttt",
            "...BBWWWWWWtttttttttttttt...",
            "....WWWWWWWWWWWWWWWWWW......",
            ".....WWWWWWWWWWWWWWWW.......",
            "..vvvvvvvvvvvvvvvvvvvvvv....",
            ".v.vvvv...vvvvv....vvvv....."
        )
        val wader = listOf(
            "........................",
            "....chhh................",
            "...chhhhh...............",
            "...hheEhh...............",
            "kkkkhhhhh...............",
            "..kkhhhh................",
            "....hhhB................",
            ".....hBBB...............",
            ".....BBBB...............",
            ".....BBBBB..............",
            "......BBBBB.............",
            "......BBBBBBttttt.......",
            ".....HBBBBBttttttttt....",
            ".....HBBBttTTTTttttttt..",
            ".....HBBBttTTTTTTtttttt.",
            ".....HBBWWtTTTTttttttttt",
            "......BWWWWtttttttttttt..",
            "......WWWWWWWWWWWWWW.....",
            ".......WWWWWWWWWWW.......",
            "........ll...ll...........",
            "........ll...ll...........",
            "........ll...ll...........",
            "........ll...ll...........",
            ".......lll...lll.........."
        )
        val raptor = listOf(
            "..............................",
            "......cc......................",
            ".....chhhh....................",
            "....chheEh....................",
            "...kkkhhhhh...................",
            "...kkhhhhBB...................",
            ".....hhhBBBB..................",
            "......BBBBBBttt...............",
            ".....HBBBBBttttttt............",
            ".....HBBBBttTTTTTtttt.........",
            ".....HBBBttTTTTTTTtttttt......",
            ".....HBBBttTTTTTTTTtttttttt...",
            "......BBBttTTTTTTTttttttttttt.",
            "......BBWWttTTTTtttttttttttttt",
            "......BWWWWttTTTtttttttttttt..",
            "......WWWWWWttttttttttttt.....",
            "......WWWWWWWWWWWWWWW.........",
            ".......WWWWWWWWWWWW...........",
            "........ll....ll...............",
            "........ll....ll...............",
            ".......lll....lll..............",
            ".......lll....lll.............."
        )
        val owl = listOf(
            "...cc.......cc.....",
            "..cccc.....cccc....",
            "..hhhhhhhhhhhhhh...",
            ".hhhhhhhhhhhhhhhh..",
            ".hheeEhhhhhhEeehh..",
            ".hheeEhhhhhhEeehh..",
            ".hhhhhhhkkhhhhhhh..",
            ".bhhhhhkkkhhhhhhb..",
            ".bBBBBBBBBBBBBBBb..",
            ".bbBBBBBBBBBBBBbb..",
            ".bbBtBBBBBBtBBBbb..",
            ".bbBBtBBBBtBBBBbb..",
            "..bbBBBBBBBBBBbb...",
            "..bbWWWWWWWWWWbb...",
            "..bWWWWWWWWWWWWb...",
            "..WWWwWWWWWWwWWWW..",
            "...WWWWWWWWWWWW.....",
            "...WWWWWWWWWWWW.....",
            ".....ll....ll........",
            ".....ll....ll........",
            "....lll....lll......."
        )
        // 도요·물떼새: 낮은 몸, 긴 부리와 다리
        val shorebird = listOf(
            "..............................",
            ".....chhh.....................",
            "....chhhhh....................",
            "kkkkkhheEh....................",
            "..kkkkhhhhBB..................",
            "......hhhBBBB.................",
            ".......BBBBBBttttt............",
            "......HBBBBBttTTTtttttt.......",
            "......HBBBWWtTTTTtttttttttt...",
            "......BBWWWWWtttttttttttttttt.",
            ".......WWWWWWWWWWWWWWWWW......",
            "........WWWWWWWWWWWW..........",
            ".........ll.....ll.............",
            ".........ll.....ll.............",
            ".........ll.....ll.............",
            "........lll.....lll............"
        )
        // 갈매기·바닷새: 긴 날개와 쐐기꼬리
        val seabird = listOf(
            "...............................",
            "....chhhh......................",
            "...chheEhh.....................",
            "kkkkhhhhBBB....................",
            ".kkkhhhBBBBBtttt...............",
            "....HBBBBBBttTTtttttttt........",
            "...HHBBBBBttTTTTTtttttttttt....",
            "...HBBBBWWtTTTTTTtttttttttttt..",
            "...BBBWWWWWttTTttttttttttttttt.",
            "....WWWWWWWWtttttttttttttttttt",
            ".....WWWWWWWWWWWWWWWWWWttt.....",
            ".......WWWWWWWWWWWWW...tt.......",
            ".........ll....ll................",
            "........lll....lll..............."
        )
        // 딱다구리: 세로로 선 몸과 단단한 꼬리
        val woodpecker = listOf(
            "........................",
            "....aac.................",
            "...ahhhh................",
            "..ahheEhh...............",
            "kkkkhhhhh...............",
            "..kkhhhBB...............",
            "....hBBBBB..............",
            "....HBBBttt.............",
            "....HBBttTTt............",
            "....HBBtTTTt............",
            "....HBBtTTTt............",
            "....HBBWTTTt............",
            ".....BWWttt.............",
            ".....WWWWW..............",
            "......WWWWt.............",
            ".......WWWtt............",
            "........WWttt...........",
            "........ll.tt...........",
            "........ll..t...........",
            ".......lll.............."
        )
        // 비둘기·두견이: 둥근 가슴, 긴 꼬리
        val dove = listOf(
            "............................",
            "....chhhh...................",
            "...chheEhh..................",
            "..kkhhhhBBB.................",
            "..kkhhhBBBBBtt..............",
            "....HBBBBBBtttttt...........",
            "...HHBBBBBttTTTTtttt........",
            "...HBBBBWWtTTTTTTtttttt.....",
            "...BBBWWWWWttTTTTttttttttt..",
            "....WWWWWWWWttttttttttttttt.",
            ".....WWWWWWWWWWWWWtttttttttt",
            ".......WWWWWWWWWWW...tttt....",
            "........ll....ll..............",
            "........ll....ll..............",
            ".......lll....lll............."
        )
        // 물총새·파랑새: 머리와 부리가 크고 몸은 짧다
        val kingfisher = listOf(
            "............................",
            ".....cchhh..................",
            "....chhhhhhh................",
            "kkkkkkhheEhh................",
            ".kkkkkhhhhhhB................",
            "......hhhBBBBttt.............",
            "......HBBBBBttTTtttt.........",
            "......HBBBWWtTTTTtttttt......",
            ".......BBWWWWtttttttttttt....",
            "........WWWWWWWWWWWtttttt....",
            ".........WWWWWWWWW...tt......",
            "..........ll...ll............",
            ".........lll...lll..........."
        )
        // 팔색조형: 통통한 몸, 짧은 꼬리
        val pitta = listOf(
            ".........................",
            "....aahhhh...............",
            "...aahheEhh..............",
            "..kkkhhhhhh..............",
            "...khhhBBBBB.............",
            "....HBBBBBtttt...........",
            "...HHBBBBttTTTtt.........",
            "...HBBBWWtTTTTTtttt......",
            "...BBWWWWWttTTtttttt.....",
            "....WWWWWWWWttttttttt....",
            ".....WWWWWWWWWWWWtt......",
            "......WWWWWWWWWWW.........",
            ".......ll....ll...........",
            "......lll....lll.........."
        )
        // 꿩·뜸부기: 묵직한 몸, 땅을 걷는 긴 발
        val gamebird = listOf(
            "...............................",
            "....chhhh......................",
            "...chheEhh.....................",
            "..kkhhhhBBB....................",
            "...khhhBBBBBtt.................",
            "....HBBBBBBtttttt..............",
            "...HHBBBBBttTTTTtttt...........",
            "...HBBBBWWtTTTTTTtttttt........",
            "...BBBWWWWWttTTTTtttttttttt....",
            "....WWWWWWWWttttttttttttttttt.",
            ".....WWWWWWWWWWWWWWttttttttttt",
            "......WWWWWWWWWWWWW....tttt....",
            ".......lll.....lll..............",
            ".......lll.....lll..............",
            "......llll....llll.............."
        )
        // 제비·칼새: 날렵한 가슴과 깊게 갈라진 꼬리
        val aerial = listOf(
            "...............................",
            "....chhh.......................",
            "...chheEh......................",
            ".kkkhhhhBBB....................",
            "...hhhBBBBtttttt...............",
            "....HBBBttTTTTTtttttt..........",
            "....HBBWWtTTTTTTtttttttt.......",
            ".....BWWWWttTTtttttttttttttt...",
            "......WWWWWWWWWWWWWttttttttttt.",
            "........WWWWWWWWW....tttt...ttt",
            ".........ll...ll........tt.tt...",
            "........lll...lll.........t....."
        )

        val templates = arrayOf(
            songbird, waterfowl, wader, raptor, owl, shorebird, seabird,
            woodpecker, dove, kingfisher, pitta, gamebird, aerial
        )
        val perched = LinkedHashMap<String, Bitmap>()
        for (d in Birds.ALL) {
            val pal = mapOf(
                'B' to d.art.body, 'b' to shade(d.art.body, 0.72f), 'H' to shade(d.art.body, 1.18f),
                'h' to d.art.head, 'a' to d.art.accent,
                'W' to d.art.belly, 'w' to shade(d.art.belly, 0.82f),
                't' to d.art.wing, 'T' to shade(d.art.wing, 0.72f),
                'k' to d.art.beak, 'c' to d.art.crest, 'l' to d.art.leg,
                'e' to c(0xFFFDFDF8), 'E' to c(0xFF17151A),
                'v' to c(0xFF8FD4EA)
            )
            val rows = templates[d.art.template.coerceIn(0, templates.lastIndex)]
            var bmp = decorateBird(sprite(rows, pal), d)
            if (d.art.scale != 1f) {
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * d.art.scale).toInt().coerceAtLeast(1),
                    (bmp.height * d.art.scale).toInt().coerceAtLeast(1),
                    false
                )
            }
            perched[d.id] = bmp
        }
        birds = perched
    }

    /** 체형마다 안전한 앵커에 1px 깃무늬를 더해 작은 화면에서도 종을 구분한다. */
    private fun decorateBird(src: Bitmap, def: BirdDef): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val cv = Canvas(bmp)
        val p = Paint()
        val a = when (def.art.template) {
            1 -> BirdAnchors(11f, 7f, 20f, 12f, 6f, 9f, 6f, 3f)
            2 -> BirdAnchors(10f, 12f, 18f, 17f, 7f, 13f, 6f, 3f)
            3 -> BirdAnchors(11f, 8f, 20f, 15f, 7f, 11f, 7f, 3f)
            4 -> BirdAnchors(4f, 9f, 15f, 16f, 9f, 10f, 5f, 5f)
            5 -> BirdAnchors(12f, 6f, 21f, 10f, 8f, 8f, 8f, 3f)
            6 -> BirdAnchors(11f, 5f, 21f, 9f, 7f, 7f, 6f, 2f)
            7 -> BirdAnchors(8f, 7f, 13f, 14f, 6f, 10f, 6f, 3f)
            8 -> BirdAnchors(11f, 5f, 20f, 10f, 7f, 8f, 6f, 2f)
            9 -> BirdAnchors(12f, 6f, 20f, 9f, 8f, 7f, 8f, 3f)
            10 -> BirdAnchors(10f, 5f, 17f, 10f, 7f, 8f, 7f, 2f)
            11 -> BirdAnchors(12f, 6f, 21f, 10f, 8f, 8f, 6f, 2f)
            12 -> BirdAnchors(11f, 4f, 20f, 8f, 7f, 6f, 6f, 2f)
            else -> BirdAnchors(10f, 6f, 18f, 12f, 6f, 9f, 6f, 3f)
        }
        val dark = shade(def.art.body, 0.48f)
        val pale = if (Color.red(def.art.accent) + Color.green(def.art.accent) + Color.blue(def.art.accent) > 540)
            def.art.accent else shade(def.art.belly, 1.06f)
        fun rect(l: Float, t: Float, r: Float, b: Float, col: Int) {
            p.color = col; cv.drawRect(l, t, r, b, p)
        }
        fun dot(x: Float, y: Float, col: Int) = rect(x, y, x + 1.2f, y + 1.2f, col)

        when (def.art.pattern) {
            BirdPatterns.WING_BARS -> {
                rect(a.wingL + 1f, a.wingT + 1f, a.wingR - 1f, a.wingT + 2f, pale)
                rect(a.wingL + 3f, a.wingT + 3.5f, a.wingR, a.wingT + 4.5f, def.art.accent)
            }
            BirdPatterns.STREAKED -> {
                rect(a.chestX, a.chestY, a.chestX + 1f, a.chestY + 4f, dark)
                rect(a.chestX + 2.2f, a.chestY + 1f, a.chestX + 3.2f, a.chestY + 5f, dark)
                dot(a.wingL + 3f, a.wingT + 2f, pale)
                dot(a.wingL + 6f, a.wingT + 4f, pale)
            }
            BirdPatterns.BIB -> {
                rect(a.chestX - 1f, a.chestY - 1f, a.chestX + 3f, a.chestY + 2f, dark)
                rect(a.chestX + 0.2f, a.chestY + 1f, a.chestX + 1.5f, a.chestY + 5f, dark)
            }
            BirdPatterns.DARK_CAP -> {
                rect(a.eyeX - 2f, a.eyeY - 2.5f, a.eyeX + 3f, a.eyeY - 1f, def.art.accent)
                rect(a.eyeX - 1f, a.eyeY - 1.2f, a.eyeX + 3.5f, a.eyeY, def.art.accent)
            }
            BirdPatterns.SPOTTED -> {
                dot(a.wingL + 2f, a.wingT + 2f, pale)
                dot(a.wingL + 5f, a.wingT + 4f, pale)
                dot(a.wingL + 8f, a.wingT + 2f, pale)
                dot(a.chestX + 1f, a.chestY + 2f, dark)
                dot(a.chestX + 3f, a.chestY + 4f, dark)
            }
            BirdPatterns.COLLAR -> {
                rect(a.eyeX + 2.5f, a.eyeY + 2f, a.eyeX + 4f, a.eyeY + 6f, pale)
                rect(a.eyeX + 4f, a.eyeY + 4.5f, a.eyeX + 6f, a.eyeY + 6f, pale)
            }
            BirdPatterns.EYE_STRIPE -> {
                rect(a.eyeX - 2f, a.eyeY - 0.4f, a.eyeX + 3.5f, a.eyeY + 1f, dark)
                rect(a.eyeX - 1.5f, a.eyeY - 1.6f, a.eyeX + 1.8f, a.eyeY - 0.6f, pale)
                dot(a.eyeX, a.eyeY, c(0xFF17151A))
            }
            BirdPatterns.IRIDESCENT -> {
                rect(a.wingL + 1f, a.wingT + 1f, a.wingR - 1f, a.wingT + 2.2f, def.art.accent)
                rect(a.wingL + 3f, a.wingT + 3f, a.wingR, a.wingT + 4.2f, shade(def.art.accent, 1.16f))
                dot(a.eyeX + 2f, a.eyeY + 2f, def.art.accent)
            }
        }
        return bmp
    }

    /** 도망칠 때 실제로 날개를 펄럭이도록 위/아래 2프레임 비행 스프라이트를 만든다. */
    private fun buildFlightBird(def: BirdDef, wingsUp: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(32, 26, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        val art = def.art
        val outline = shade(art.body, 0.43f)
        fun oval(l: Float, t: Float, r: Float, b: Float, col: Int) {
            p.color = col; cv.drawOval(RectF(l, t, r, b), p)
        }
        fun path(col: Int, vararg pts: Float) {
            val q = Path(); q.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { q.lineTo(pts[i], pts[i + 1]); i += 2 }
            q.close(); p.color = col; cv.drawPath(q, p)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, col: Int) {
            p.color = col; cv.drawRect(l, t, r, b, p)
        }

        val longNeck = art.template == 2
        val longBill = art.template in setOf(2, 5, 6, 9)
        val plump = art.template in setOf(1, 4, 8, 10, 11)
        val bodyTop = if (plump) 8f else 9f
        val bodyBottom = if (plump) 19f else 18f

        // 꼬리와 뒤쪽 날개
        path(outline, 21f, 11f, 31f, 7f, 28f, 13f, 31f, 18f, 21f, 16f)
        path(art.wing, 21f, 12f, 29f, 9f, 27f, 13f, 29f, 16f, 21f, 15f)
        if (wingsUp) {
            path(outline, 12f, 12f, 13f, 2f, 17f, 0f, 21f, 12f)
            path(art.wing, 13f, 11f, 14.5f, 3f, 16.5f, 2f, 19.5f, 12f)
        } else {
            path(outline, 12f, 13f, 16f, 25f, 20f, 23f, 21f, 13f)
            path(art.wing, 13.5f, 13f, 16.5f, 23f, 19f, 21.5f, 19.5f, 12f)
        }

        // 몸과 배
        oval(7f, bodyTop, 25f, bodyBottom, outline)
        oval(8f, bodyTop + 1f, 24f, bodyBottom - 1f, art.body)
        oval(9f, 13f, 22f, bodyBottom - 1f, art.belly)
        path(art.wing, 12f, 10f, 22f, 11f, 20f, 16f, 13f, 15f)
        if (art.pattern == BirdPatterns.WING_BARS || art.pattern == BirdPatterns.IRIDESCENT) {
            rect(15f, 11f, 21f, 12f, art.accent)
            rect(16f, 13f, 20f, 14f, shade(art.accent, 1.12f))
        }

        // 백로·두루미는 목을 길게 뻗고, 나머지는 둥근 머리
        if (longNeck) {
            rect(6f, 8f, 13f, 12f, outline)
            rect(7f, 8.5f, 13f, 11f, art.head)
            oval(4f, 6f, 11f, 13f, outline)
            oval(5f, 7f, 10f, 12f, art.head)
        } else {
            oval(3f, 7f, 12f, 16f, outline)
            oval(4f, 8f, 11f, 15f, art.head)
        }
        if (art.template == 4) { // 부엉이 귀깃
            path(art.accent, 4f, 9f, 4f, 4f, 7f, 8f)
            path(art.accent, 9f, 8f, 12f, 4f, 11f, 10f)
        }

        // 부리
        if (longBill) {
            path(outline, 5f, 10f, 0f, 12f, 5f, 13f)
            path(art.beak, 5f, 10.8f, 0.8f, 12f, 5f, 12.2f)
        } else {
            path(outline, 4.5f, 10f, 0.5f, 12f, 4.5f, 13.5f)
            path(art.beak, 4.5f, 10.8f, 1.5f, 12f, 4.5f, 12.8f)
        }
        rect(5.5f, 9.5f, 7f, 11f, c(0xFFFDFDF8))
        rect(6f, 10f, 7f, 11f, c(0xFF17151A))

        // 긴 다리는 비행 중 뒤로 모은다.
        if (art.template in setOf(2, 5)) {
            rect(22f, 16f, 31f, 17f, art.leg)
            rect(21f, 18f, 30f, 19f, art.leg)
        }

        val flightScale = (0.96f + (art.scale - 1f) * 0.55f).coerceIn(0.86f, 1.16f)
        return if (flightScale == 1f) bmp else Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * flightScale).toInt().coerceAtLeast(1),
            (bmp.height * flightScale).toInt().coerceAtLeast(1),
            false
        )
    }

    // -----------------------------------------------------------------------
    // 타일 (32x32)
    // -----------------------------------------------------------------------

    private var tileSeed = 9017

    private fun tilePainter(paint: (Canvas, Paint, Random) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        paint(cv, Paint(), Random(tileSeed.toLong()))
        tileSeed += 1013
        return b
    }

    /** 소품용 접지 그림자 (소품 타일은 배경이 투명하므로 그림자를 직접 얹는다) */
    private fun propShadow(cv: Canvas, p: Paint, cx: Float, cy: Float, rx: Float, ry: Float) {
        p.color = c(0x40202C20)
        cv.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
    }

    private fun fill(c: Canvas, p: Paint, color: Int) {
        p.color = color
        c.drawRect(0f, 0f, 32f, 32f, p)
    }

    private fun specks(c: Canvas, p: Paint, r: Random, color: Int, n: Int) {
        p.color = color
        repeat(n) {
            val x = r.nextInt(31)
            val y = r.nextInt(31)
            c.drawRect(x.toFloat(), y.toFloat(), x + 2f, y + 1.5f, p)
        }
    }

    private fun grassBase(c: Canvas, p: Paint, r: Random, base: Int = c(0xFF96D07A)) {
        fill(c, p, base)
        // 체커 디더링 노이즈 — 클래식 픽셀아트 잔디 특유의 잔물결 질감
        // (방향성 있는 그라데이션 밴드는 타일이 맵 전체에 반복 배치될 때 줄무늬로 보이므로 사용하지 않음)
        p.color = shade(base, 0.88f)
        repeat(20) {
            val x = r.nextInt(32); val y = r.nextInt(32)
            if ((x + y) % 2 == 0) c.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, p)
        }
        p.color = shade(base, 1.16f)
        repeat(14) {
            val x = r.nextInt(32); val y = r.nextInt(32)
            if ((x + y) % 2 == 1) c.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, p)
        }
        specks(c, p, r, shade(base, 0.9f), 3)
        // 풀잎 다발 (좌·중·우 3가닥 + 밝은 팁) — 단순 사각형 대신 자연스러운 tuft 모양
        repeat(4) {
            val bx = 2f + r.nextInt(27)
            val by = 3f + r.nextInt(20)
            grassTuft(c, p, bx, by, shade(base, 0.74f), shade(base, 0.9f))
        }
        repeat(2) {
            val bx = 2f + r.nextInt(27)
            val by = 3f + r.nextInt(20)
            grassTuft(c, p, bx, by, shade(base, 1.22f), shade(base, 1.38f))
        }
    }

    /** 풀잎 다발 하나: 좌/중/우 3가닥이 살짝 벌어진 형태 + 중앙 가닥 하이라이트 팁 */
    private fun grassTuft(c: Canvas, p: Paint, x: Float, y: Float, dark: Int, tip: Int) {
        p.color = dark
        c.drawRect(x, y + 1.4f, x + 1f, y + 4.4f, p)          // 왼쪽 가닥
        c.drawRect(x + 3f, y + 1.8f, x + 4f, y + 4.2f, p)     // 오른쪽 가닥
        c.drawRect(x + 1.5f, y, x + 2.5f, y + 4.6f, p)        // 중앙 가닥 (가장 큼)
        p.color = tip
        c.drawRect(x + 1.5f, y, x + 2.5f, y + 1.3f, p)        // 중앙 가닥 팁 하이라이트
    }

    private fun buildTiles() {
        // 지면 타일은 SVG 마스터(VectorDrawable) 아트를 쓴다.
        // 단 PATH/PLAZA 는 Roads.kt 오토타일(RoadArt)과 디자인을 맞추기 위해
        // 풀마스크(포장 완료 셀) 절차 생성을 그대로 사용한다.
        val map = LinkedHashMap<T, Array<Bitmap>>()
        fun put(t: T, vararg names: String) {
            map[t] = Array(names.size) { renderPixel(names[it], 32, 32) }
        }

        put(T.GRASS, "tile_grass_0", "tile_grass_1", "tile_grass_2", "tile_grass_3")
        put(T.TALLGRASS, "tile_tallgrass_0", "tile_tallgrass_1")
        put(T.FLOWER, "tile_flower_0", "tile_flower_1", "tile_flower_2")
        map[T.PATH] = Array(3) { RoadArt.tile(Pave.DIRT, 255, it, false) }
        map[T.PLAZA] = Array(2) { RoadArt.tile(Pave.STONE, 255, it, false) }
        put(T.SAND, "tile_sand_0", "tile_sand_1", "tile_sand_2")
        put(T.WATER, "tile_water_0", "tile_water_1", "tile_water_2", "tile_water_3")
        put(T.REED, "tile_reed_0", "tile_reed_1")
        put(T.TREE, "tile_tree_0", "tile_tree_1")
        put(T.ROCK, "tile_rock_0", "tile_rock_1")
        put(T.MOUNTAIN, "tile_mountain_0", "tile_mountain_1")
        put(T.BLDG_WALL, "tile_bldg_wall")
        put(T.BLDG_WIN, "tile_bldg_win_0", "tile_bldg_win_1")
        put(T.BLDG_ROOF, "tile_bldg_roof")
        put(T.HOUSE_ROOF, "tile_house_roof")
        put(T.HOUSE_WALL, "tile_house_wall")
        put(T.HOUSE_WIN, "tile_house_win")
        put(T.HOUSE_DOOR, "tile_house_door")
        put(T.TUNNEL, "tile_tunnel")
        put(T.FLOOR, "tile_floor_0", "tile_floor_1")
        put(T.WALL_IN, "tile_wall_in")
        put(T.WALL_WIN, "tile_wall_win")
        put(T.OVEN, "tile_oven_0", "tile_oven_1")      // 화덕 불꽃 2프레임
        put(T.BED, "tile_bed")
        put(T.BOX, "tile_box")
        put(T.DECOR, "tile_decor")
        put(T.SIGN, "tile_sign")
        put(T.BENCH, "tile_bench")
        put(T.LAMP, "tile_lamp")

        // 도로 부속물 (Roads.kt 자원)
        medallion = RoadArt.medallion(3)
        drain = RoadArt.drain()
        castShadow = RoadArt.castShadows()

        tiles = T.ALL.map { map[it] ?: error("타일 누락: $it") }.toTypedArray()
    }

    // -----------------------------------------------------------------------
    // 아이콘
    // -----------------------------------------------------------------------

    private fun buildIcons() {
        pizzaIcon = renderPixel("pizza", 22, 14)
        pizzaIconBig = Bitmap.createScaledBitmap(
            pizzaIcon, pizzaIcon.width * 4, pizzaIcon.height * 4, false
        )
        cloverIcon = renderIcon("clover", 14, 14)
        cameraIcon = renderIcon("camera", 20, 16)
        houseIcon = renderIcon("house", 14, 14)
        sunIcon = renderIcon("sun", 16, 16)
        moonIcon = renderIcon("moon", 14, 14)
    }

    // -----------------------------------------------------------------------
    // 장식 아트 (32x32)
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------

    /** 새 비트맵 (안전 접근) */
    fun bird(id: String): Bitmap = birds[id] ?: birds.values.first()

    /** 오른쪽을 바라보는 새 (플립, 지연 생성) */
    fun birdFlipped(id: String): Bitmap {
        birdsFlipped[id]?.let { return it }
        val f = flipH(bird(id))
        birdsFlipped = birdsFlipped + (id to f)
        return f
    }

    /** 도주 비행 프레임. 종별 팔레트와 체형을 유지하며 좌우 방향도 지원한다. */
    fun birdFlight(id: String, frame: Int, faceLeft: Boolean): Bitmap {
        val def = Birds.byId[id] ?: Birds.ALL.first()
        val frames = birdFlights[def.id] ?: arrayOf(
            buildFlightBird(def, true),
            buildFlightBird(def, false)
        ).also { birdFlights[def.id] = it }
        val i = frame.coerceIn(0, 1)
        if (faceLeft) return frames[i]
        val flipped = birdFlightsFlipped[def.id] ?: frames.map(::flipH).toTypedArray().also {
            birdFlightsFlipped[def.id] = it
        }
        return flipped[i]
    }

    fun birdW(id: String): Float = bird(id).width.toFloat()
    fun birdH(id: String): Float = bird(id).height.toFloat()
}
