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

/** 자전거 페달 애니메이션 프레임 수 */
const val BIKE_FRAMES = 8

/** NPC 대기 애니메이션 프레임 수 / 프레임 길이(초) */
const val NPC_FRAMES = 12

/** 고양이 애니메이션 프레임 수 */
const val CAT_SIT_FRAMES = 8
const val CAT_WALK_FRAMES = 6
const val NPC_FRAME_TIME = 0.2f

/**
 * 캐릭터 동작 종류 — 프레임 수와 프레임 길이(초).
 *
 * IDLE  : 숨쉬기 · 무게중심 이동 · 눈 깜빡임 · 두리번거리기
 * WALK  : 걷기 (팔다리 교차 + 상하 바운스 + 머리카락 흔들림)
 * RUN   : 달리기 (큰 보폭 · 앞으로 기울인 자세 · 굽힌 팔)
 * SNEAK : 살금살금 (카메라 모드에서 이동할 때)
 * AIM   : 카메라 조준 (숨죽인 미세한 흔들림)
 */
enum class Anim(val frames: Int, val frameTime: Float) {
    IDLE(12, 0.17f),
    WALK(8, 0.085f),
    RUN(8, 0.062f),
    SNEAK(8, 0.13f),
    AIM(4, 0.2f);

    /** 한 바퀴 도는 데 걸리는 시간(초) */
    val cycle: Float get() = frames * frameTime
}

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

    // 플레이어 / NPC --------------------------------------------------------
    // 동작 스프라이트는 CharacterArt 골격 애니메이션으로 필요할 때 생성한다.
    // (클래스 정의와 접근자는 아래 "사람 — 골격 애니메이션" 절에 있다)

    // 고양이 -----------------------------------------------------------------
    lateinit var catSit: Array<Bitmap>          // 앉아서 꼬리 살랑 (왼쪽 바라봄)
    lateinit var catSitL: Array<Bitmap>         // 오른쪽 바라봄
    lateinit var catWalk: Array<Bitmap>         // 사뿐사뿐 걷기
    lateinit var catWalkL: Array<Bitmap>
    lateinit var catFrames: Array<Bitmap>       // 호환용 (= catSit)
    lateinit var catFramesL: Array<Bitmap>

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
    // 사람 — 골격 애니메이션 (CharacterArt.kt)
    // 프레임은 필요할 때 만들어 캐시한다 (성별 x 레벨 등급 조합이 많기 때문).
    // -----------------------------------------------------------------------

    private fun pal(
        hair: Long, hair2: Long, skin: Long, skin2: Long, top: Long, top2: Long,
        pants: Long, pants2: Long, shoe: Long, line: Long, pack: Long, pack2: Long,
        eye: Long, blush: Long
    ) = CharacterArt.Pal(
        c(hair), c(hair2), c(skin), c(skin2), c(top), c(top2),
        c(pants), c(pants2), c(shoe), c(line), c(pack), c(pack2), c(eye), c(blush)
    )

    private val malePal = pal(
        0xFF4A2F1D, 0xFF382314, 0xFFFFD9B0, 0xFFE8B88C, 0xFFF2B63C, 0xFFD99B26,
        0xFF4A6FA5, 0xFF3A5A8A, 0xFF7A4A2B, 0xFF33241C, 0xFFD9534F, 0xFFB23F44,
        0xFF2E2620, 0xFFF2A58C
    )
    private val femalePal = malePal.copy(
        hair = c(0xFF6A3155), hair2 = c(0xFF4A203D),
        top = c(0xFFDB6B9A), top2 = c(0xFFB84D7B),
        pants = c(0xFF66529B), pants2 = c(0xFF4D3C7C)
    )

    /** 레벨 등급별 탐조 장비: 0 새내기 · 1 견습(캡) · 2 숙련(캡+조끼) · 3 명인(챙모자+조끼+목도리+깃털) */
    private val gearTiers = arrayOf<CharacterArt.Gear?>(
        null,
        CharacterArt.Gear(cap = c(0xFF4F8F6A), capDark = c(0xFF3C6E50)),
        CharacterArt.Gear(
            cap = c(0xFF3F6FA0), capDark = c(0xFF2F5580),
            vest = c(0xFF6B8E4E), vestDark = c(0xFF52703B)
        ),
        CharacterArt.Gear(
            cap = c(0xFF8A5A2B), capDark = c(0xFF6E4620),
            vest = c(0xFF3E6B57), vestDark = c(0xFF2C4E3F),
            scarf = c(0xFFD9534F), brim = true, feather = c(0xFFF2D06B)
        )
    )

    val tierCount: Int get() = gearTiers.size

    private fun look(gender: String, tier: Int): CharacterArt.Look {
        val female = gender == "female"
        return CharacterArt.Look(
            pal = if (female) femalePal else malePal,
            gear = gearTiers[tier.coerceIn(0, gearTiers.size - 1)],
            longHair = female
        )
    }

    /** 방향별 프레임 묶음 */
    class Clip(
        val down: Array<Bitmap>,
        val up: Array<Bitmap>,
        val side: Array<Bitmap>,
        val sideL: Array<Bitmap>
    ) {
        val count: Int get() = down.size

        fun frame(dir: Dir, i: Int): Bitmap {
            val k = ((i % count) + count) % count
            return when (dir) {
                Dir.E -> side[k]
                Dir.W -> sideL[k]
                Dir.N -> up[k]
                else -> down[k]
            }
        }
    }

    /** 플레이어 동작 세트 (한 성별 x 한 등급) */
    class PlayerSet(
        val idle: Clip, val walk: Clip, val run: Clip, val sneak: Clip, val aim: Clip
    ) {
        fun clip(anim: Anim): Clip = when (anim) {
            Anim.IDLE -> idle
            Anim.WALK -> walk
            Anim.RUN -> run
            Anim.SNEAK -> sneak
            Anim.AIM -> aim
        }

        // 기존 코드 호환 (아바타 썸네일 등) — 서 있는 자세
        val down: Array<Bitmap> get() = idle.down
        val up: Array<Bitmap> get() = idle.up
        val side: Array<Bitmap> get() = idle.side
        val sideL: Array<Bitmap> get() = idle.sideL
    }

    private val playerCache = HashMap<Int, PlayerSet>()

    private fun buildClip(look: CharacterArt.Look, frames: Int, pose: (Float) -> CharacterArt.Pose): Clip {
        val down = Array(frames) { CharacterArt.render(CharacterArt.FRONT, pose(it / frames.toFloat()), look) }
        val up = Array(frames) { CharacterArt.render(CharacterArt.BACK, pose(it / frames.toFloat()), look) }
        val side = Array(frames) { CharacterArt.render(CharacterArt.SIDE, pose(it / frames.toFloat()), look) }
        val sideL = Array(frames) { flipH(side[it]) }
        return Clip(down, up, side, sideL)
    }

    /** 성별 + 레벨 등급으로 동작 세트 얻기 (첫 사용 시 생성) */
    fun playerSet(gender: String, tier: Int): PlayerSet {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = (if (gender == "female") 1 else 0) * 64 + t
        playerCache[key]?.let { return it }
        val lk = look(gender, t)
        val set = PlayerSet(
            idle = buildClip(lk, Anim.IDLE.frames) { CharacterArt.idlePose(it) },
            walk = buildClip(lk, Anim.WALK.frames) { CharacterArt.walkPose(it, CharacterArt.WALK) },
            run = buildClip(lk, Anim.RUN.frames) { CharacterArt.walkPose(it, CharacterArt.RUN) },
            sneak = buildClip(lk, Anim.SNEAK.frames) { CharacterArt.walkPose(it, CharacterArt.SNEAK) },
            aim = buildClip(lk, Anim.AIM.frames) { CharacterArt.aimPose(it) }
        )
        playerCache[key] = set
        return set
    }

    /** 레벨 등급으로 (남자) */
    fun playerSet(tier: Int): PlayerSet = playerSet("male", tier)

    private val cheerCache = HashMap<Int, Array<Bitmap>>()

    /** 레벨업 축하 동작 (정면 8프레임) */
    fun cheerFrames(gender: String, tier: Int): Array<Bitmap> {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = (if (gender == "female") 1 else 0) * 64 + t
        cheerCache[key]?.let { return it }
        val lk = look(gender, t)
        val frames = Array(8) {
            CharacterArt.render(CharacterArt.FRONT, CharacterArt.cheerPose(it / 8f), lk)
        }
        cheerCache[key] = frames
        return frames
    }

    /** 애니메이션 시간 -> 스프라이트 */
    fun playerBitmap(gender: String, tier: Int, anim: Anim, dir: Dir, animT: Float): Bitmap {
        val clip = playerSet(gender, tier).clip(anim)
        return clip.frame(dir, (animT / anim.frameTime).toInt())
    }

    // -----------------------------------------------------------------------
    // 자전거 (페달을 밟는 라이더) — 8프레임
    // -----------------------------------------------------------------------

    class BikeSet(
        val down: Array<Bitmap>, val up: Array<Bitmap>,
        val side: Array<Bitmap>, val sideL: Array<Bitmap>
    ) {
        val count: Int get() = down.size

        fun frame(dir: Dir, i: Int): Bitmap {
            val k = ((i % count) + count) % count
            return when (dir) {
                Dir.E -> side[k]
                Dir.W -> sideL[k]
                Dir.N -> up[k]
                else -> down[k]
            }
        }
    }

    private val bikeCache = HashMap<Int, BikeSet>()

    fun bikeSet(gender: String, tier: Int): BikeSet {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = (if (gender == "female") 1 else 0) * 64 + t
        bikeCache[key]?.let { return it }
        val lk = look(gender, t)
        val n = BIKE_FRAMES
        val side = Array(n) { CharacterArt.renderBike(CharacterArt.SIDE, it / n.toFloat(), lk) }
        val set = BikeSet(
            down = Array(n) { CharacterArt.renderBike(CharacterArt.FRONT, it / n.toFloat(), lk) },
            up = Array(n) { CharacterArt.renderBike(CharacterArt.BACK, it / n.toFloat(), lk) },
            side = side,
            sideL = Array(n) { flipH(side[it]) }
        )
        bikeCache[key] = set
        return set
    }

    /** 자전거 스프라이트 (pedalT: 페달 위상 초) */
    fun bikeBitmap(gender: String, tier: Int, dir: Dir, pedalPhase: Float): Bitmap {
        val set = bikeSet(gender, tier)
        val f = (pedalPhase * set.count).toInt()
        return set.frame(dir, f)
    }

    // 기존 코드 호환 접근자
    val playerTiers: Array<PlayerSet> get() = Array(gearTiers.size) { playerSet("male", it) }
    val playerDown: Array<Bitmap> get() = playerSet("male", 0).down
    val playerUp: Array<Bitmap> get() = playerSet("male", 0).up
    val playerSide: Array<Bitmap> get() = playerSet("male", 0).side
    val playerSideL: Array<Bitmap> get() = playerSet("male", 0).sideL
    val femaleDown: Array<Bitmap> get() = playerSet("female", 0).down
    val femaleUp: Array<Bitmap> get() = playerSet("female", 0).up
    val femaleSide: Array<Bitmap> get() = playerSet("female", 0).side
    val femaleSideL: Array<Bitmap> get() = playerSet("female", 0).sideL
    val bikeDown: Bitmap get() = bikeSet("male", 0).down[0]
    val bikeUp: Bitmap get() = bikeSet("male", 0).up[0]
    val bikeSide: Bitmap get() = bikeSet("male", 0).side[0]
    val bikeSideL: Bitmap get() = bikeSet("male", 0).sideL[0]

    // -----------------------------------------------------------------------
    // NPC — 성격이 드러나는 대기 동작 (12프레임)
    // -----------------------------------------------------------------------

    private fun npcPal(
        hair: Long, top: Long, top2: Long, pants: Long, pack: Long
    ): CharacterArt.Pal = CharacterArt.Pal(
        hair = c(hair), hair2 = shade(c(hair), 0.75f),
        skin = c(0xFFFFD9B0), skin2 = c(0xFFE8B88C),
        top = c(top), top2 = c(top2), pants = c(pants), pants2 = shade(c(pants), 0.75f),
        shoe = c(0xFF3A3A44), line = c(0xFF33241C), pack = c(pack), pack2 = shade(c(pack), 0.75f),
        eye = c(0xFF2E2620), blush = c(0xFFF2A58C)
    )

    private fun npcLook(kind: NpcKind): CharacterArt.Look = when (kind) {
        NpcKind.PROFESSOR -> CharacterArt.Look(
            npcPal(0xFFCFD2D8, 0xFFF5F2EA, 0xFFD8D2C4, 0xFF5D6470, 0xFF9AA3AD), glasses = true
        )
        NpcKind.SHOP -> CharacterArt.Look(
            npcPal(0xFF4A2F1D, 0xFF6FAE57, 0xFF4F7D3F, 0xFF8A6A4F, 0xFFC89B6A), apron = true
        )
        NpcKind.VILLAGER -> CharacterArt.Look(
            npcPal(0xFF2E2620, 0xFFC3A3E8, 0xFF9F7FC8, 0xFF4A6FA5, 0xFF8A5A33)
        )
        NpcKind.KID -> CharacterArt.Look(
            npcPal(0xFF5B3A29, 0xFFE2574C, 0xFFB23F44, 0xFF3F6FB0, 0xFFF2B63C), small = true
        )
        NpcKind.ELDER -> CharacterArt.Look(
            npcPal(0xFFE8E4DC, 0xFF8A7360, 0xFF6B5A48, 0xFF5D6470, 0xFF4F463F),
            cane = true, longHair = true
        )
    }

    private fun npcArtKind(kind: NpcKind): Int = when (kind) {
        NpcKind.PROFESSOR -> CharacterArt.NPC_PROFESSOR
        NpcKind.SHOP -> CharacterArt.NPC_SHOP
        NpcKind.VILLAGER -> CharacterArt.NPC_VILLAGER
        NpcKind.KID -> CharacterArt.NPC_KID
        NpcKind.ELDER -> CharacterArt.NPC_ELDER
    }

    private val npcCache = HashMap<NpcKind, Array<Bitmap>>()

    /** NPC 대기 애니메이션 프레임 (12장, 약 2.4초 루프) */
    fun npcFrames(kind: NpcKind): Array<Bitmap> {
        npcCache[kind]?.let { return it }
        val lk = npcLook(kind)
        val art = npcArtKind(kind)
        val frames = Array(NPC_FRAMES) {
            CharacterArt.render(CharacterArt.FRONT, CharacterArt.npcPose(art, it / NPC_FRAMES.toFloat()), lk)
        }
        npcCache[kind] = frames
        return frames
    }

    /** 시간 -> NPC 스프라이트 (offset 으로 NPC마다 위상을 다르게) */
    fun npcBitmap(kind: NpcKind, time: Float, offset: Float = 0f): Bitmap {
        val frames = npcFrames(kind)
        val i = (((time + offset) / NPC_FRAME_TIME).toInt() % frames.size + frames.size) % frames.size
        return frames[i]
    }

    val npcProfessor: Bitmap get() = npcFrames(NpcKind.PROFESSOR)[0]
    val npcShop: Bitmap get() = npcFrames(NpcKind.SHOP)[0]
    val npcVillager: Bitmap get() = npcFrames(NpcKind.VILLAGER)[0]
    val npcKid: Bitmap get() = npcFrames(NpcKind.KID)[0]
    val npcElder: Bitmap get() = npcFrames(NpcKind.ELDER)[0]


    // -----------------------------------------------------------------------
    // 고양이 (32x26) — 꼬리/귀/눈/네 다리가 따로 움직인다 (CharacterArt.renderCat)
    // -----------------------------------------------------------------------

    private fun buildCat() {
        catSit = Array(CAT_SIT_FRAMES) { CharacterArt.renderCat(false, it / CAT_SIT_FRAMES.toFloat()) }
        catWalk = Array(CAT_WALK_FRAMES) { CharacterArt.renderCat(true, it / CAT_WALK_FRAMES.toFloat()) }
        catSitL = Array(catSit.size) { flipH(catSit[it]) }
        catWalkL = Array(catWalk.size) { flipH(catWalk[it]) }
        catFrames = catSit
        catFramesL = catSitL
    }

    /** 고양이 스프라이트 — walking 여부와 위상(0~1)으로 고른다 */
    fun catBitmap(walking: Boolean, phase: Float, faceLeft: Boolean): Bitmap {
        val set = if (walking) {
            if (faceLeft) catWalk else catWalkL
        } else {
            if (faceLeft) catSit else catSitL
        }
        val i = ((phase * set.size).toInt() % set.size + set.size) % set.size
        return set[i]
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
