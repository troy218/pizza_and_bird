package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
 * 캐릭터와 월드 타일을 코드로 생성하는 픽셀 아트 에셋 (v0.2 — 2배 해상도).
 * - 타일 32x32 / 캐릭터 32x32 / 새 13종 체형 + 9종 깃무늬 + 비행 2프레임
 * - 캐릭터/타일은 코드로 생성, 작은 장식 일러스트는 로컬 SVG — 네트워크·외부 라이브러리 없음
 */
class Assets {

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
    // ------- 고밀도 픽셀 텍스처 헬퍼 (v0.3 디테일 대업그레이드) -------

    /** 짧은 별칭: 픽셀 사각형 */
    private fun px(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float, col: Int) {
        p.color = col; c.drawRect(x, y, x + w, y + h, p)
    }

    /** 1px 점 */
    private fun dot(c: Canvas, p: Paint, x: Float, y: Float, col: Int) {
        p.color = col; c.drawRect(x, y, x + 1f, y + 1f, p)
    }

    /** 무작위 입자 노이즈 (minS~maxS 크기) */
    private fun noise(
        c: Canvas, p: Paint, r: Random,
        x0: Float, y0: Float, x1: Float, y1: Float,
        col: Int, n: Int, minS: Float = 1f, maxS: Float = 2.2f
    ) {
        p.color = col
        val w = x1 - x0
        val h = y1 - y0
        repeat(n) {
            val s = minS + r.nextFloat() * (maxS - minS)
            val x = x0 + r.nextFloat() * (w - s)
            val y = y0 + r.nextFloat() * (h - s)
            c.drawRect(x, y, x + s, y + s * 0.85f, p)
        }
    }

    /** 체커 디더링 — 두 색 사이 그라데이션 느낌 */
    private fun dither(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float,
        col: Int, step: Float = 2f
    ) {
        p.color = col
        var row = 0
        var y = y0
        while (y < y1) {
            val off = if (row % 2 == 0) 0f else step
            var x = x0 + off
            while (x < x1) {
                val w = minOf(step, x1 - x)
                val h = minOf(step, y1 - y)
                if (w > 0f && h > 0f) c.drawRect(x, y, x + w, y + h, p)
                x += step * 2f
            }
            y += step
            row++
        }
    }

    /** 수직 밴드 그라데이션 (디더 전환) */
    private fun vgrad(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float,
        top: Int, bot: Int, bands: Int = 5
    ) {
        val bh = (y1 - y0) / bands
        for (i in 0 until bands) {
            val t = if (bands <= 1) 0f else i / (bands - 1f)
            val col = lerpColor(top, bot, t)
            val yy0 = y0 + i * bh
            px(c, p, x0, yy0, x1 - x0, bh + 0.4f, col)
            if (i > 0) dither(c, p, x0, yy0 - bh * 0.28f, x1, yy0, lerpColor(top, bot, t - 0.12f), 2f)
        }
    }

    /** 두 색 선형 보간 */
    private fun lerpColor(c0: Int, c1: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            255,
            (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * tt).toInt().coerceIn(0, 255),
            (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * tt).toInt().coerceIn(0, 255),
            (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * tt).toInt().coerceIn(0, 255)
        )
    }

    /** 베벨: 위/왼쪽 하이라이트 + 아래/오른쪽 음영 (입체감) */
    private fun bevel(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float,
        hi: Int, lo: Int, th: Float = 1.2f
    ) {
        px(c, p, x0, y0, x1 - x0, th, hi)
        px(c, p, x0, y0, th, y1 - y0, hi)
        px(c, p, x0, y1 - th, x1 - x0, th, lo)
        px(c, p, x1 - th, y0, th, y1 - y0, lo)
    }

    /** 나무 그림자 (땅에 눕힌 부드러운 타원) — 전역 공용 */
    val softShadow: Bitmap by lazy {
        val b = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint()
        p.isAntiAlias = true
        for (i in 6 downTo 1) {
            val a = 10 + (6 - i) * 7
            p.color = Color.argb(a, 22, 34, 24)
            val inset = i * 2.2f
            cv.drawOval(RectF(inset, inset * 0.5f, 64f - inset, 32f - inset * 0.5f), p)
        }
        b
    }

    /** 화면 비네트 (가장자리 은은한 암부) — 전역 공용 */
    val vignette: Bitmap by lazy {
        val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint()
        p.isAntiAlias = true
        p.shader = RadialGradient(
            160f, 90f, 205f,
            intArrayOf(
                Color.argb(0, 12, 10, 22),
                Color.argb(0, 12, 10, 22),
                Color.argb(70, 12, 10, 22),
                Color.argb(120, 12, 10, 22)
            ),
            floatArrayOf(0f, 0.58f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, 320f, 180f, p)
        p.shader = null
        b
    }

    private fun grassTuft(c: Canvas, p: Paint, x: Float, y: Float, dark: Int, tip: Int) {
        p.color = dark
        c.drawRect(x, y + 1.4f, x + 1f, y + 4.4f, p)          // 왼쪽 가닥
        c.drawRect(x + 3f, y + 1.8f, x + 4f, y + 4.2f, p)     // 오른쪽 가닥
        c.drawRect(x + 1.5f, y, x + 2.5f, y + 4.6f, p)        // 중앙 가닥 (가장 큼)
        p.color = tip
        c.drawRect(x + 1.5f, y, x + 2.5f, y + 1.3f, p)        // 중앙 가닥 팁 하이라이트
    }

    private fun buildTiles() {
        // T 값마다 변형(또는 애니메이션 프레임) 목록을 모은다.
        // 예전처럼 순서에 의존하지 않고 enum 키로 담아 두므로 어긋날 수가 없다.
        val map = LinkedHashMap<T, ArrayList<Bitmap>>()
        var cur: T? = null
        fun begin(t: T) {
            cur = t
            map[t] = ArrayList()
        }
        fun add(vararg bmps: Bitmap) {
            map[cur ?: error("begin(T.…) 을 먼저 불러야 한다")]!!.addAll(bmps)
        }

        // GRASS (6종 변형 — 더 다채로운 초원 디테일)
        begin(T.GRASS)
        add(
            tilePainter { c, p, r ->   // 0: 잔디 기본
                grassBase(c, p, r)
            },
            tilePainter { c, p, r ->   // 1: 클로버 군락
                grassBase(c, p, r)
                val g = c(0xFF5D9E4B)
                val g2 = c(0xFF7FBF62)
                repeat(3) {
                    val x = 3f + r.nextInt(21)
                    val y = 3f + r.nextInt(21)
                    px(c, p, x, y, 2.4f, 2f, g)
                    px(c, p, x - 1.8f, y + 1.4f, 2f, 1.8f, g)
                    px(c, p, x + 2f, y + 1.4f, 2f, 1.8f, g)
                    dot(c, p, x + 0.6f, y + 0.2f, g2)
                }
            },
            tilePainter { c, p, r ->   // 2: 잔돌과 나뭇가지
                grassBase(c, p, r)
                px(c, p, 12f, 15f, 5.6f, 4.4f, c(0xFF7C8590))
                px(c, p, 12.8f, 15.6f, 3.8f, 2.6f, c(0xFFA5B0BA))
                px(c, p, 13.6f, 16.2f, 1.6f, 1.2f, c(0xFFC2CBD3))
                px(c, p, 12.4f, 18.4f, 4.6f, 1f, c(0xFF5D6772))
                px(c, p, 23f, 7f, 3.2f, 2.6f, c(0xFF8A949E))
                px(c, p, 23.4f, 7.4f, 1.8f, 1.3f, c(0xFFB0BAC2))
                px(c, p, 4f, 24f, 10f, 1.5f, c(0xFF8A5A33))
                px(c, p, 5f, 24f, 6f, 0.7f, c(0xFFA87B4F))
                px(c, p, 11.4f, 22.4f, 1.5f, 3.2f, c(0xFF7A4A2B))
                px(c, p, 8f, 25.6f, 2f, 1f, c(0xFF6B431F))
            },
            tilePainter { c, p, r ->   // 3: 민들레와 잡초
                grassBase(c, p, r)
                // 민들레 (씨앗 흰 솜털)
                px(c, p, 19f, 14f, 1.4f, 7f, c(0xFF5D8A4A))
                p.color = c(0xFFFDF6E8)
                c.drawCircle(19.8f, 12.8f, 3.2f, p)
                p.color = c(0xFFE8DFC8)
                c.drawCircle(19.8f, 12.8f, 1.4f, p)
                for (a in 0 until 6) {
                    val ang = a * 1.0472f
                    dot(c, p, 19.8f + 2.6f * Math.cos(ang.toDouble()).toFloat(), 12.8f + 2.6f * Math.sin(ang.toDouble()).toFloat(), c(0xFFFFFBEE))
                }
                // 노란 꽃 봉오리
                px(c, p, 7f, 20f, 3f, 2.6f, c(0xFFF2D06B))
                px(c, p, 7.8f, 20.4f, 1.4f, 1.4f, c(0xFFF7E08C))
                px(c, p, 7.6f, 22.6f, 1.2f, 3.6f, c(0xFF5D8A4A))
            },
            tilePainter { c, p, r ->   // 4: 작은 버섯
                grassBase(c, p, r)
                px(c, p, 8f, 13f, 7f, 4.6f, c(0xFFC9572E))
                px(c, p, 8.6f, 13.4f, 4f, 2f, c(0xFFE8823C))
                px(c, p, 9.8f, 13.8f, 1.6f, 1.2f, c(0xFFF2A3B3))
                px(c, p, 10.6f, 17.6f, 2.4f, 4f, c(0xFFF2E3C2))
                px(c, p, 21f, 22f, 5f, 3.4f, c(0xFFB23F44))
                px(c, p, 21.4f, 22.4f, 2.6f, 1.4f, c(0xFFD9534F))
                px(c, p, 22.8f, 25.4f, 1.8f, 3f, c(0xFFF2E3C2))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF6B431F), 3, 1f, 1.4f)
            },
            tilePainter { c, p, r ->   // 5: 야생화 한 줄기
                grassBase(c, p, r)
                px(c, p, 15f, 12f, 1.4f, 10f, c(0xFF5D8A4A))
                px(c, p, 13f, 15f, 2.6f, 1.6f, c(0xFF6FAE57))
                px(c, p, 16f, 18f, 2.6f, 1.6f, c(0xFF6FAE57))
                // 보라 꽃
                px(c, p, 13.4f, 8f, 5f, 4.6f, c(0xFFC3A3E8))
                px(c, p, 14.2f, 8.6f, 3f, 2.6f, c(0xFFD9C0F2))
                px(c, p, 15.2f, 9.4f, 1.6f, 1.6f, c(0xFFF2D06B))
                // 흰 꽃
                px(c, p, 24f, 22f, 3.6f, 3.2f, c(0xFFFDFDF8))
                px(c, p, 24.8f, 22.6f, 1.8f, 1.6f, c(0xFFF2D06B))
            }
        )

begin(T.TALLGRASS)
        add(
            tilePainter { c, p, r ->   // 0: 얕은 풀숲
                grassBase(c, p, r, c(0xFF8CC46C))
                val dark = c(0xFF5D8A4A)
                val mid = c(0xFF6FAE57)
                val light = c(0xFF8CC46C)
                for (k in 0 until 9) {
                    val x = 1 + r.nextInt(28)
                    val h = 8 + r.nextInt(10)
                    val y = 32 - h
                    px(c, p, x.toFloat(), y.toFloat(), 2f, h.toFloat(), dark)
                    px(c, p, x + 1.6f, y + 2f, 1f, h - 2.4f, mid)
                }
                for (k in 0 until 5) {
                    val x = 2 + r.nextInt(27)
                    val h = 6 + r.nextInt(8)
                    px(c, p, x.toFloat(), (32 - h).toFloat(), 1.6f, h.toFloat(), light)
                    dot(c, p, x.toFloat(), (32 - h).toFloat(), c(0xFFB0D98C))
                }
            },
            tilePainter { c, p, r ->   // 1: 키 큰 풀숲 + 씨앗 이삭
                grassBase(c, p, r, c(0xFF8CC46C))
                val dark = c(0xFF547A44)
                val mid = c(0xFF6FAE57)
                for (k in 0 until 11) {
                    val x = 1 + r.nextInt(28)
                    val h = 12 + r.nextInt(12)
                    val y = 32 - h
                    px(c, p, x.toFloat(), y.toFloat(), 2.2f, h.toFloat(), dark)
                    px(c, p, x + 1.8f, y + 3f, 1f, h - 3.4f, mid)
                    if (k % 3 == 0) {
                        px(c, p, x - 0.4f, y - 3.4f, 3f, 3.8f, c(0xFFB0793F))
                        px(c, p, x + 0.2f, y - 2.8f, 1.6f, 2f, c(0xFFC89B6A))
                    }
                }
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF547A44), 6, 1f, 1.6f)
            }
        )

begin(T.FLOWER)
        val flowerCols = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFFDFDF8))
        add(*Array(3) { i ->
            tilePainter { c, p, r ->
                grassBase(c, p, r)
                repeat(5) {
                    val x = 3f + r.nextInt(22)
                    val y = 4f + r.nextInt(20)
                    // 줄기와 잎
                    px(c, p, x + 1.6f, y + 2.6f, 1.2f, 4.4f, c(0xFF5D8A4A))
                    px(c, p, x + 0.2f, y + 4.4f, 1.6f, 1.2f, c(0xFF6FAE57))
                    px(c, p, x + 2.8f, y + 5f, 1.6f, 1.2f, c(0xFF6FAE57))
                    // 꽃잎 (5매 + 하이라이트)
                    val col = flowerCols[(i + r.nextInt(3)) % 3]
                    val lite = shade(col, 1.18f)
                    val dark = shade(col, 0.8f)
                    px(c, p, x + 1f, y - 1.2f, 2.4f, 2f, col)
                    px(c, p, x - 1f, y + 0.6f, 2.4f, 2f, col)
                    px(c, p, x + 3.2f, y + 0.6f, 2.4f, 2f, col)
                    px(c, p, x + 1f, y + 2.4f, 2.4f, 1.8f, dark)
                    px(c, p, x + 0.2f, y + 0.4f, 4.2f, 2.2f, col)
                    px(c, p, x + 1f, y + 0.2f, 2f, 1.2f, lite)
                    // 수술
                    px(c, p, x + 1.6f, y + 0.8f, 1.6f, 1.6f, c(0xFFF7CE5B))
                    dot(c, p, x + 2f, y + 1f, c(0xFFE8A75C))
                }
            }
        })

begin(T.PATH)
        for (i in 0 until 3) add(RoadArt.tile(Pave.DIRT, 255, i, false))
        // PLAZA (석재 포장)
        begin(T.PLAZA)
        for (i in 0 until 2) add(RoadArt.tile(Pave.STONE, 255, i, false))
        // SAND (3종)
        begin(T.SAND)
        add(
            tilePainter { c, p, r ->   // 0: 모래
                fill(c, p, c(0xFFF2E1B0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE4CF96), 16, 1f, 2.2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFF8ECC8), 12, 1f, 2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFD9BF82), 6, 1f, 1.6f)
            },
            tilePainter { c, p, r ->   // 1: 물결 + 조개
                fill(c, p, c(0xFFF2E1B0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE4CF96), 12, 1f, 2f)
                // 모래 물결
                for (k in 0 until 3) {
                    val y = 5f + k * 9f
                    p.color = c(0xFFE4CF96)
                    for (x in 0 until 30 step 3) {
                        c.drawRect(x.toFloat(), y + Math.sin(x * 0.45 + k).toFloat() * 1.2f, x + 2.2f, y + 1.4f + Math.sin(x * 0.45 + k).toFloat() * 1.2f, p)
                    }
                }
                // 조개
                px(c, p, 12f, 17f, 8f, 5.4f, c(0xFFFDF6E8))
                px(c, p, 12.8f, 17.6f, 6.4f, 3.4f, c(0xFFF5E8D2))
                px(c, p, 15.4f, 18.2f, 1.2f, 3.4f, c(0xFFE8B14E))
                px(c, p, 13.6f, 18.6f, 1f, 2.4f, c(0xFFE8B14E))
                px(c, p, 17.4f, 18.6f, 1f, 2.4f, c(0xFFE8B14E))
                px(c, p, 12.6f, 21.4f, 6.8f, 1f, c(0xFFD9C4A0))
            },
            tilePainter { c, p, r ->   // 2: 불가사리 + 자국
                fill(c, p, c(0xFFF2E1B0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE4CF96), 14, 1f, 2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFF8ECC8), 8, 1f, 1.8f)
                // 불가사리
                val star = c(0xFFE88B7A)
                px(c, p, 13f, 12f, 6f, 8f, star)
                px(c, p, 11f, 14f, 10f, 4.6f, star)
                px(c, p, 12f, 10f, 2.6f, 4f, star)
                px(c, p, 17.6f, 10f, 2.6f, 4f, star)
                px(c, p, 10f, 19f, 3.4f, 3f, star)
                px(c, p, 19f, 19f, 3.4f, 3f, star)
                px(c, p, 14f, 13.4f, 4f, 4.4f, shade(star, 1.15f))
                noise(c, p, r, 11f, 10f, 22f, 22f, shade(star, 0.85f), 6, 1f, 1.2f)
                // 작은 돌
                px(c, p, 25f, 25f, 3f, 2.4f, c(0xFFD9C4A0))
                px(c, p, 25.4f, 25.4f, 1.6f, 1.2f, c(0xFFF0E2C4))
            }
        )

begin(T.WATER)
        add(*Array(4) { f ->
            tilePainter { c, p, r ->
                // 깊이감 있는 수직 그라데이션
                vgrad(c, p, 0f, 0f, 32f, 32f, c(0xFF63B8E4), c(0xFF3E8FC4), 6)
                // 잔물결 밴드
                val off = f * 2.4f
                p.color = c(0xFF4FA8D8)
                for ((i, by) in listOf(3f, 12.5f, 22f).withIndex()) {
                    for (x in -4 until 34 step 7) {
                        val xx = ((x + off * (1f + i * 0.3f)) % 36f + 36f) % 36f - 2f
                        c.drawRect(xx, by, xx + 5f, by + 1.8f, p)
                    }
                }
                // 물결 하이라이트 (이동)
                p.color = c(0xFF93D4EF)
                for ((i, by) in listOf(2.2f, 11.8f, 21.2f).withIndex()) {
                    for (x in -4 until 34 step 11) {
                        val xx = ((x + off * (1.6f + i * 0.25f)) % 34f + 34f) % 34f - 1f
                        c.drawRect(xx, by, xx + 3.4f, by + 1.2f, p)
                    }
                }
                // 반짝임 (프레임별 위치)
                p.color = c(0xFFC9ECF8)
                val sx = floatArrayOf(6f, 21f, 13f, 27f)[f]
                val sy = floatArrayOf(7f, 26f, 28f, 15f)[f]
                c.drawRect(sx, sy, sx + 2.2f, sy + 1.2f, p)
                c.drawRect(sx + 0.5f, sy - 1f, sx + 1.2f, sy + 3f, p)
                // 은은한 수초 그림자
                p.color = Color.argb(34, 20, 70, 90)
                c.drawRect(0f, 27f, 32f, 32f, p)
            }
        })

begin(T.REED)
        add(
            tilePainter { c, p, r ->   // 0: 갈대 군락
                grassBase(c, p, r, c(0xFF8CC46C))
                val xs = intArrayOf(4, 11, 19, 27)
                for ((i, x) in xs.withIndex()) {
                    val ty = if (i % 2 == 0) 3f else 0f
                    px(c, p, x.toFloat(), ty, 2.4f, 32f - ty, c(0xFF7D9C4F))
                    px(c, p, x + 0.6f, ty + 2f, 1.2f, 30f - ty, c(0xFFA3C26B))
                    // 곤충 씨앗 이삭
                    px(c, p, x - 1f, ty, 4.6f, 8f, c(0xFFB0793F))
                    px(c, p, x - 0.2f, ty + 1.4f, 3f, 5f, c(0xFFC89B6A))
                    px(c, p, x + 0.4f, ty + 2f, 1.4f, 3f, c(0xFF8A5A33))
                }
                px(c, p, 9f, 22f, 6f, 1.6f, c(0xFF6FAE57))
            },
            tilePainter { c, p, r ->   // 1: 물가 갈대 + 부들
                grassBase(c, p, r, c(0xFF8CC46C))
                for (x in intArrayOf(6, 14, 22)) {
                    px(c, p, x.toFloat(), 2f, 2f, 30f, c(0xFF6B8F45))
                    px(c, p, x + 1.4f, 5f, 1f, 25f, c(0xFFA3C26B))
                }
                // 부들 (갈대 머리)
                px(c, p, 5.2f, 2f, 3.6f, 9f, c(0xFF8A5A33))
                px(c, p, 6f, 3f, 1.8f, 6f, c(0xFFB0793F))
                px(c, p, 21.4f, 5f, 3.6f, 8f, c(0xFF8A5A33))
                px(c, p, 22.2f, 6f, 1.8f, 5f, c(0xFFB0793F))
                // 잎
                px(c, p, 8f, 14f, 8f, 1.6f, c(0xFF6FAE57))
                px(c, p, 16f, 20f, 9f, 1.6f, c(0xFF5D8A4A))
            }
        )

begin(T.TREE)
        add(
            tilePainter { c, p, r ->   // 0: 참나무
            grassBase(c, p, r)
            p.color = Color.argb(58, 26, 46, 28)
            c.drawOval(RectF(5f, 24f, 29f, 31f), p)
            // 기둥
            px(c, p, 13f, 16f, 7f, 15f, c(0xFF5D3A20))
            px(c, p, 14.2f, 16f, 3.4f, 15f, c(0xFF7A4E2B))
            px(c, p, 14.8f, 17f, 1.2f, 11f, c(0xFF9A6A3E))
            px(c, p, 12f, 28f, 3f, 3.4f, c(0xFF5D3A20))
            px(c, p, 18.4f, 28f, 3f, 3.4f, c(0xFF5D3A20))
            noise(c, p, r, 13f, 17f, 20f, 30f, c(0xFF4A2D18), 5, 1f, 1.5f)
            // 수관 (3층)
            p.color = c(0xFF2E5D33)
            c.drawCircle(16f, 12f, 12f, p)
            p.color = c(0xFF3F7D46)
            c.drawCircle(14.5f, 10.5f, 10f, p)
            c.drawCircle(21f, 13.5f, 7.4f, p)
            p.color = c(0xFF57A55F)
            c.drawCircle(11.5f, 8f, 6.2f, p)
            c.drawCircle(19f, 9f, 5f, p)
            p.color = c(0xFF6FBF72)
            c.drawCircle(10f, 6.6f, 3.2f, p)
            c.drawCircle(13.5f, 8f, 2.2f, p)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFF2C5429), 12, 1f, 1.8f)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFF7FD184), 8, 1f, 1.6f)
            // 도토리
            px(c, p, 22f, 17f, 2f, 2.4f, c(0xFFB0793F))
            px(c, p, 22f, 17f, 2f, 1f, c(0xFF8A5A33))
            },
            tilePainter { c, p, r ->   // 1: 소나무
            grassBase(c, p, r)
            p.color = Color.argb(58, 26, 46, 28)
            c.drawOval(RectF(6f, 25f, 28f, 31f), p)
            px(c, p, 14.4f, 20f, 4.4f, 11f, c(0xFF5D3A20))
            px(c, p, 15.2f, 20f, 2f, 11f, c(0xFF7A4E2B))
            val path = Path()
            p.color = c(0xFF24512F)
            path.moveTo(16f, -1f); path.lineTo(27f, 13f); path.lineTo(5f, 13f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF2F6B3B)
            path.reset()
            path.moveTo(16f, 6f); path.lineTo(29f, 21f); path.lineTo(3f, 21f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF24512F)
            path.reset()
            path.moveTo(16f, 13f); path.lineTo(31f, 29f); path.lineTo(1f, 29f); path.close()
            c.drawPath(path, p)
            // 눈/빛 팁
            px(c, p, 12f, 7f, 8f, 1.6f, c(0xFF4F9E57))
            px(c, p, 8f, 15f, 8f, 1.6f, c(0xFF4F9E57))
            px(c, p, 18f, 22f, 8f, 1.6f, c(0xFF4F9E57))
            noise(c, p, r, 3f, 2f, 29f, 28f, c(0xFF1D4427), 10, 1f, 1.6f)
            noise(c, p, r, 3f, 2f, 29f, 28f, c(0xFF67B572), 6, 1f, 1.4f)
            },
            tilePainter { c, p, r ->   // 2: 벚나무 (꽃)
            grassBase(c, p, r)
            p.color = Color.argb(52, 26, 46, 28)
            c.drawOval(RectF(5f, 24f, 29f, 31f), p)
            px(c, p, 14f, 17f, 6f, 14f, c(0xFF5D3A20))
            px(c, p, 15f, 17f, 2.6f, 14f, c(0xFF8A5A33))
            px(c, p, 12f, 22f, 2f, 2.2f, c(0xFF5D3A20))
            px(c, p, 19.4f, 24f, 2f, 2f, c(0xFF5D3A20))
            p.color = c(0xFFD97F99)
            c.drawCircle(15f, 11f, 11.4f, p)
            p.color = c(0xFFF2A3B3)
            c.drawCircle(13.5f, 9.5f, 9.4f, p)
            c.drawCircle(21f, 13f, 6.6f, p)
            p.color = c(0xFFFFC9D6)
            c.drawCircle(11f, 7.5f, 5.4f, p)
            c.drawCircle(17f, 8.5f, 4.2f, p)
            noise(c, p, r, 4f, 2f, 28f, 21f, c(0xFFFFE0E8), 14, 1f, 1.8f)
            noise(c, p, r, 4f, 2f, 28f, 21f, c(0xFFC96B87), 8, 1f, 1.5f)
            // 지는 꽃잎
            px(c, p, 6f, 26f, 2f, 1.4f, c(0xFFF2A3B3))
            px(c, p, 24f, 28f, 2f, 1.4f, c(0xFFFFC9D6))
            px(c, p, 12f, 30f, 1.6f, 1.2f, c(0xFFF2A3B3))
            },
            tilePainter { c, p, r ->   // 3: 단풍나무
            grassBase(c, p, r)
            p.color = Color.argb(52, 26, 46, 28)
            c.drawOval(RectF(5f, 24f, 29f, 31f), p)
            px(c, p, 13.6f, 16f, 6.4f, 15f, c(0xFF5D3A20))
            px(c, p, 14.8f, 16f, 2.8f, 15f, c(0xFF7A4E2B))
            px(c, p, 12.6f, 28f, 2.8f, 3.4f, c(0xFF5D3A20))
            px(c, p, 18.2f, 28f, 2.8f, 3.4f, c(0xFF5D3A20))
            p.color = c(0xFFB23F44)
            c.drawCircle(16f, 11.5f, 11.6f, p)
            p.color = c(0xFFD9534F)
            c.drawCircle(14.5f, 10f, 9.6f, p)
            c.drawCircle(21.5f, 13.5f, 6.8f, p)
            p.color = c(0xFFE8823C)
            c.drawCircle(11.5f, 8f, 5.8f, p)
            c.drawCircle(18f, 8.6f, 4.6f, p)
            p.color = c(0xFFF2A34E)
            c.drawCircle(10.2f, 6.6f, 3.2f, p)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFF9A2F35), 10, 1f, 1.8f)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFFF7CE5B), 7, 1f, 1.5f)
            px(c, p, 7f, 27f, 2f, 1.4f, c(0xFFE8823C))
            px(c, p, 23f, 29f, 2f, 1.4f, c(0xFFD9534F))
        })

begin(T.ROCK)
        add(
            tilePainter { c, p, r ->   // 0: 큰 바위
                grassBase(c, p, r)
                p.color = Color.argb(56, 26, 46, 28)
                c.drawOval(RectF(4f, 24f, 28f, 31f), p)
                // 바위 본체
                px(c, p, 5f, 11f, 22f, 16f, c(0xFF5A626C))
                px(c, p, 6f, 9f, 18f, 16f, c(0xFF7C8590))
                px(c, p, 7.4f, 8f, 13f, 12f, c(0xFF9AA3AD))
                px(c, p, 9f, 9f, 8f, 6f, c(0xFFB5BDC6))
                px(c, p, 10.4f, 9.6f, 4f, 2.6f, c(0xFFD0D7DE))
                // 단면 음영
                px(c, p, 5f, 22f, 22f, 5f, c(0xFF4A5158))
                px(c, p, 21f, 12f, 6f, 14f, c(0xFF4A5158))
                // 균열
                px(c, p, 14f, 12f, 1.2f, 8f, c(0xFF3E454C))
                px(c, p, 15f, 18f, 1.2f, 5f, c(0xFF3E454C))
                px(c, p, 11f, 19f, 4f, 1.2f, c(0xFF3E454C))
                // 이끼
                px(c, p, 7f, 10f, 5f, 2.2f, c(0xFF6FAE57))
                px(c, p, 8.4f, 8.6f, 3f, 1.6f, c(0xFF8CC46C))
                noise(c, p, r, 6f, 9f, 22f, 22f, c(0xFF6B747E), 6, 1f, 1.6f)
                // 밑에 자갈
                px(c, p, 25f, 25f, 3f, 2.4f, c(0xFF8A949E))
                px(c, p, 25.4f, 25.4f, 1.6f, 1.2f, c(0xFFB0BAC2))
            },
            tilePainter { c, p, r ->   // 1: 바위 무리
                grassBase(c, p, r)
                p.color = Color.argb(52, 26, 46, 28)
                c.drawOval(RectF(6f, 23f, 26f, 30f), p)
                px(c, p, 8f, 15f, 16f, 11f, c(0xFF6B747E))
                px(c, p, 9f, 13f, 13f, 11f, c(0xFF8A949E))
                px(c, p, 10.4f, 14f, 8f, 5f, c(0xFFA5B0BA))
                px(c, p, 11.6f, 14.6f, 4f, 2.2f, c(0xFFC2CBD3))
                px(c, p, 8f, 22f, 16f, 4f, c(0xFF4A5158))
                // 작은 바위
                px(c, p, 22f, 21f, 6f, 6f, c(0xFF7C8590))
                px(c, p, 23f, 21.6f, 3.4f, 2.6f, c(0xFFA5B0BA))
                px(c, p, 3f, 22f, 5f, 5f, c(0xFF6B747E))
                px(c, p, 3.8f, 22.6f, 2.6f, 2f, c(0xFF9AA3AD))
                // 이끼
                px(c, p, 9f, 14f, 4f, 1.8f, c(0xFF6FAE57))
                px(c, p, 18f, 19f, 3f, 1.6f, c(0xFF5D8A4A))
                noise(c, p, r, 8f, 13f, 24f, 24f, c(0xFF5D6772), 5, 1f, 1.4f)
            }
        )

begin(T.MOUNTAIN)
        add(
            tilePainter { c, p, r ->   // 0: 바위 절벽 (층리)
                fill(c, p, c(0xFF77848F))
                vgrad(c, p, 0f, 0f, 32f, 32f, c(0xFF8D9AA8), c(0xFF5D6772), 5)
                // 지층 밴드
                px(c, p, 0f, 7f, 32f, 1.6f, c(0xFF6B7580))
                px(c, p, 0f, 15.6f, 32f, 1.8f, c(0xFF6B7580))
                px(c, p, 0f, 24.6f, 32f, 1.6f, c(0xFF525B66))
                px(c, p, 0f, 8.6f, 32f, 1f, c(0xFFA5B2BD))
                px(c, p, 0f, 17.4f, 32f, 1f, c(0xFFA5B2BD))
                // 절단면 하이라이트
                px(c, p, 3f, 2f, 9f, 3f, c(0xFFB5BDC6))
                px(c, p, 21f, 11f, 7f, 2.6f, c(0xFFB5BDC6))
                px(c, p, 6f, 19f, 6f, 2.2f, c(0xFF9AA3AD))
                // 음영 (오른쪽)
                p.color = Color.argb(52, 20, 26, 34)
                c.drawRect(22f, 0f, 32f, 32f, p)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF68727E), 12, 1f, 2.2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFA0ACB8), 8, 1f, 1.8f)
                // 자갈
                px(c, p, 4f, 28f, 3f, 2.2f, c(0xFF8A949E))
                px(c, p, 14f, 29f, 2.4f, 1.8f, c(0xFF6B7580))
            },
            tilePainter { c, p, r ->   // 1: 눈 덮인 봉우리
                fill(c, p, c(0xFF77848F))
                vgrad(c, p, 0f, 0f, 32f, 32f, c(0xFF8D9AA8), c(0xFF5D6772), 5)
                px(c, p, 0f, 12f, 32f, 1.8f, c(0xFF6B7580))
                px(c, p, 0f, 21f, 32f, 1.6f, c(0xFF525B66))
                px(c, p, 0f, 13.8f, 32f, 1f, c(0xFFA5B2BD))
                // 눈
                px(c, p, 2f, 1f, 28f, 7f, c(0xFFE8EEF2))
                px(c, p, 0f, 4f, 32f, 5f, c(0xFFDCE5EC))
                dither(c, p, 0f, 8f, 32f, 11f, c(0xFFE8EEF2), 2f)
                px(c, p, 4f, 2f, 10f, 2.4f, c(0xFFF8FBFD))
                px(c, p, 20f, 5f, 8f, 2f, c(0xFFF8FBFD))
                // 바위 노출부
                px(c, p, 6f, 12f, 8f, 6f, c(0xFF9AA3AD))
                px(c, p, 19f, 16f, 9f, 5f, c(0xFF8A949E))
                p.color = Color.argb(52, 20, 26, 34)
                c.drawRect(23f, 0f, 32f, 32f, p)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF68727E), 10, 1f, 2f)
            }
        )

begin(T.BLDG_WALL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFC9BFA8))
            val brickTones = intArrayOf(c(0xFFE0D2B4), c(0xFFE9E2D3), c(0xFFD8CFBA), c(0xFFDDD2B8))
            for (row in 0 until 5) {
                val y = row * 6.6f
                val off = if (row % 2 == 0) 0f else -8f
                var x = off
                while (x < 32f) {
                    val t = brickTones[(r.nextInt(brickTones.size))]
                    val w = 15.2f
                    px(c, p, x + 0.6f, y + 0.6f, w, 5.4f, t)
                    px(c, p, x + 0.6f, y + 0.6f, w, 1f, shade(t, 1.08f))
                    px(c, p, x + 0.6f, y + 5.2f, w, 0.8f, shade(t, 0.88f))
                    x += w + 1.2f
                }
            }
            // 하단 그을음 / 상단 하이라이트
            p.color = Color.argb(30, 60, 50, 30)
            c.drawRect(0f, 27f, 32f, 32f, p)
            p.color = Color.argb(24, 255, 250, 230)
            c.drawRect(0f, 0f, 32f, 2f, p)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFB5A88F), 6, 1f, 1.4f)
        })

begin(T.BLDG_WIN)
        val curtains = intArrayOf(c(0xFFF2D06B), c(0xFFC3A3E8))
        add(*Array(2) { i ->
            tilePainter { c, p, r ->
                // 벽돌 배경 (작게)
                fill(c, p, c(0xFFD8CFBA))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE0D2B4), 10, 1f, 2f)
                px(c, p, 0f, 30f, 32f, 2f, c(0xFFC9BFA8))
                // 창 프레임
                px(c, p, 5f, 4f, 22f, 23f, c(0xFF8A7B62))
                px(c, p, 6f, 5f, 20f, 21f, c(0xFFB0A188))
                // 유리 (하늘 반사 그라데이션)
                vgrad(c, p, 7f, 6f, 25f, 25f, c(0xFF8FC3E2), c(0xFF5E9FC8), 4)
                // 반사 스티크
                px(c, p, 8f, 7f, 5f, 5f, c(0xFFC3E4F2))
                px(c, p, 14f, 6.4f, 3f, 12f, Color.argb(90, 255, 255, 255))
                px(c, p, 19f, 8f, 2f, 9f, Color.argb(70, 255, 255, 255))
                // 커튼
                val cur = curtains[i]
                px(c, p, 7f, 6f, 3.6f, 19f, cur)
                px(c, p, 7f, 6f, 1.4f, 19f, shade(cur, 1.16f))
                px(c, p, 9.4f, 6f, 1.2f, 19f, shade(cur, 0.82f))
                px(c, p, 21.4f, 6f, 3.6f, 19f, cur)
                px(c, p, 21.4f, 6f, 1.2f, 19f, shade(cur, 0.82f))
                // 창살
                px(c, p, 15.2f, 5f, 1.6f, 21f, c(0xFF8A7B62))
                px(c, p, 7f, 14.6f, 18f, 1.6f, c(0xFF8A7B62))
                // 창틀 하이라이트
                px(c, p, 5f, 4f, 22f, 1.2f, c(0xFFD9C9A7))
                px(c, p, 5f, 25.8f, 22f, 1.2f, c(0xFF6B5E48))
                // 화분
                px(c, p, 24.4f, 22f, 5f, 4f, c(0xFFB5673F))
                px(c, p, 25f, 23f, 3.8f, 2.6f, c(0xFF9A5232))
                px(c, p, 25.4f, 19.6f, 3f, 2.6f, c(0xFF6FAE57))
                px(c, p, 26.4f, 18.2f, 1.6f, 1.8f, c(0xFFF2A3B3))
            }
        })

begin(T.BLDG_ROOF)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF5D6772))
            // 슐레이트 행 (겹침)
            for (row in 0 until 4) {
                val y = row * 8f
                val off = if (row % 2 == 0) 0f else -8f
                var x = off
                while (x < 32f) {
                    val t = lerpColor(c(0xFF77848F), c(0xFF525B66), r.nextFloat())
                    px(c, p, x + 0.4f, y + 0.4f, 15.2f, 7.2f, t)
                    px(c, p, x + 0.4f, y + 0.4f, 15.2f, 1.2f, shade(t, 1.22f))
                    px(c, p, x + 0.4f, y + 6.2f, 15.2f, 1.4f, shade(t, 0.72f))
                    x += 15.6f
                }
            }
            // 이끼 자국
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF6E8262), 5, 1f, 1.8f)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF8A949E), 7, 1f, 1.5f)
        })

begin(T.HOUSE_ROOF)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFB2583F))
            for (row in 0 until 4) {
                val y = row * 8f
                val off = if (row % 2 == 0) 0f else -6f
                var x = off
                while (x < 32f) {
                    val t = lerpColor(c(0xFFD4694A), c(0xFFC96A4D), r.nextFloat())
                    px(c, p, x + 0.4f, y + 0.6f, 11.2f, 7f, t)
                    px(c, p, x + 0.4f, y + 0.6f, 11.2f, 1.2f, shade(t, 1.18f))
                    px(c, p, x + 0.4f, y + 6.4f, 11.2f, 1.2f, shade(t, 0.76f))
                    px(c, p, x + 8.4f, y + 2f, 1.2f, 5.6f, shade(t, 0.88f))
                    x += 11.6f
                }
            }
            // 초록 이끼 + 하이라이트
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF8FA86B), 4, 1f, 1.6f)
            px(c, p, 0f, 0f, 32f, 1.2f, c(0xFFE08A67))
        })

begin(T.HOUSE_WALL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            // 스터코 노이즈
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDE0C0), 14, 1f, 1.8f)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFFBF3DC), 10, 1f, 1.6f)
            // 수직 사이딩 라인
            for (x in 0 until 32 step 8) {
                px(c, p, x.toFloat(), 0f, 1.2f, 32f, c(0xFFE0C9A2))
                px(c, p, x + 1.2f, 0f, 0.8f, 32f, c(0xFFFBF3DC))
            }
            // 상단 트림 / 하단 기초
            px(c, p, 0f, 0f, 32f, 2.2f, c(0xFFC9A87B))
            px(c, p, 0f, 2.2f, 32f, 1f, c(0xFFE8D5AE))
            px(c, p, 0f, 28f, 32f, 4f, c(0xFFC9A87B))
            px(c, p, 0f, 28f, 32f, 1.2f, c(0xFF8A6A4F))
            noise(c, p, r, 0f, 28f, 32f, 32f, c(0xFFB08A5C), 5, 1f, 1.5f)
        })

begin(T.HOUSE_WIN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDE0C0), 10, 1f, 1.8f)
            px(c, p, 0f, 0f, 32f, 2.2f, c(0xFFC9A87B))
            // 나무 창틀
            px(c, p, 5f, 3.6f, 22f, 19f, c(0xFF8A5A33))
            px(c, p, 6f, 4.6f, 20f, 17f, c(0xFFA87B4F))
            vgrad(c, p, 7f, 5.6f, 25f, 21f, c(0xFFB9DDF0), c(0xFF7FB6D9), 4)
            px(c, p, 8f, 6.4f, 6f, 5f, c(0xFFE0F2FA))
            px(c, p, 14.2f, 5f, 1.4f, 16.4f, c(0xFF8A5A33))
            px(c, p, 7f, 12.6f, 18f, 1.4f, c(0xFF8A5A33))
            px(c, p, 5f, 22.6f, 22f, 1.6f, c(0xFFC9A87B))
            // 꽃상자
            px(c, p, 4.6f, 24f, 22.8f, 5.4f, c(0xFF8A5A33))
            px(c, p, 5.8f, 25f, 20.4f, 3.2f, c(0xFFA87B4F))
            px(c, p, 4.6f, 24f, 22.8f, 1.2f, c(0xFF6B431F))
            val fcols = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFC3A3E8), c(0xFFFDFDF8))
            for (k in 0 until 5) {
                val fx = 6f + k * 4.2f
                val fc = fcols[k % 4]
                px(c, p, fx, 21.2f, 2.8f, 2.8f, fc)
                px(c, p, fx + 0.6f, 21.6f, 1.4f, 1.2f, shade(fc, 1.2f))
                px(c, p, fx + 1f, 23.8f, 1f, 1.6f, c(0xFF5D8A4A))
            }
        })

begin(T.HOUSE_DOOR)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDE0C0), 8, 1f, 1.8f)
            // 문 프레임
            px(c, p, 3f, 1.6f, 26f, 30f, c(0xFF6B431F))
            px(c, p, 4.2f, 2.8f, 23.6f, 28.8f, c(0xFF8A5A33))
            // 문짝
            px(c, p, 5.6f, 4f, 20.8f, 27.4f, c(0xFFA87B4F))
            px(c, p, 6.8f, 5.2f, 18.4f, 25f, c(0xFFC89B6A))
            // 패널
            px(c, p, 8.4f, 7f, 15.2f, 8.4f, c(0xFFA87B4F))
            px(c, p, 9.2f, 7.8f, 13.6f, 6.8f, c(0xFFB98A5C))
            px(c, p, 8.4f, 17.4f, 15.2f, 9.6f, c(0xFFA87B4F))
            px(c, p, 9.2f, 18.2f, 13.6f, 8f, c(0xFFB98A5C))
            // 유리창
            px(c, p, 10.4f, 8.6f, 11.2f, 4.6f, c(0xFF9FD0E8))
            px(c, p, 10.4f, 8.6f, 5f, 2.2f, c(0xFFC3E4F2))
            // 손잡이
            px(c, p, 21.4f, 16f, 2.6f, 2.6f, c(0xFFF2D06B))
            px(c, p, 22f, 16.4f, 1.2f, 1.2f, c(0xFFFFFBE0))
            // 현관 매트 + 발판
            px(c, p, 6f, 29f, 20f, 3f, c(0xFFB23F44))
            noise(c, p, r, 6f, 29f, 26f, 32f, c(0xFF8A2F35), 5, 1f, 1.3f)
        })

begin(T.TUNNEL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF77848F))
            vgrad(c, p, 0f, 0f, 32f, 10f, c(0xFF8D9AA8), c(0xFF68727E), 3)
            noise(c, p, r, 0f, 0f, 32f, 9f, c(0xFF9AA3AD), 8, 1f, 2f)
            // 아치 석조
            p.color = c(0xFF6B4F35)
            c.drawRect(3f, 8f, 29f, 32f, p)
            val path = Path()
            p.color = c(0xFF5A3F28)
            path.moveTo(16f, 2f); path.lineTo(29f, 12f); path.lineTo(29f, 32f)
            path.lineTo(3f, 32f); path.lineTo(3f, 12f); path.close()
            c.drawPath(path, p)
            // 아치 voussoirs (쐐기돌)
            p.color = c(0xFF8A5A33)
            for (k in 0 until 7) {
                val ang = Math.PI * (0.12 + k * 0.126)
                val cx = 16f + 12f * Math.cos(ang).toFloat()
                val cy = 12f + 9.5f * Math.sin(ang).toFloat()
                c.drawRect(cx - 1.8f, cy - 2.2f, cx + 1.8f, cy + 2.2f, p)
            }
            px(c, p, 3f, 12f, 3.6f, 20f, c(0xFF8A5A33))
            px(c, p, 25.4f, 12f, 3.6f, 20f, c(0xFF8A5A33))
            px(c, p, 4f, 12f, 1.2f, 20f, c(0xFFA87B4F))
            // 내부 어둠 (그라데이션)
            vgrad(c, p, 7.4f, 9f, 24.6f, 32f, c(0xFF2E2E3A), c(0xFF0E0E16), 4)
            // 도로
            px(c, p, 7.4f, 27f, 17.2f, 5f, c(0xFF8A8074))
            px(c, p, 7.4f, 27f, 17.2f, 1.2f, c(0xFFA39888))
            px(c, p, 15f, 28.4f, 2f, 2.4f, c(0xFFE8DFC8))
            // 입구 등
            px(c, p, 13.4f, 2f, 5.2f, 4.6f, c(0xFF3A3F4A))
            px(c, p, 14.2f, 2.8f, 3.6f, 3f, c(0xFFF7E9A8))
            px(c, p, 15f, 3.2f, 2f, 2f, c(0xFFFFFBE0))
        })

begin(T.FLOOR)
        add(*Array(2) { i ->
            tilePainter { c, p, r ->
                val base = if (i == 0) c(0xFFCDA775) else c(0xFFC49E6C)
                fill(c, p, base)
                // 널빤지 2줄
                val seam = if (i == 0) 14f else 20f
                for (row in 0..1) {
                    val y0 = row * 16f
                    val off = if (row == 0) 0f else seam
                    px(c, p, 0f, y0, 32f, 15.2f, shade(base, 1f + row * 0.02f))
                    // 나뭇결
                    for (k in 0 until 4) {
                        val gy = y0 + 2.2f + k * 3.4f + r.nextFloat() * 1.4f
                        p.color = shade(base, 0.9f)
                        c.drawRect(0f, gy, 32f, gy + 0.9f, p)
                        p.color = shade(base, 1.1f)
                        c.drawRect(0f, gy + 0.9f, 32f, gy + 1.5f, p)
                    }
                    // 옹이
                    px(c, p, off + 5f, y0 + 5f, 2.6f, 1.8f, shade(base, 0.82f))
                    px(c, p, off + 21f, y0 + 9f, 2f, 1.4f, shade(base, 0.82f))
                    // 널빤지 이음새
                    px(c, p, 0f, y0 + 15.2f, 32f, 1.4f, shade(base, 0.78f))
                    px(c, p, 0f, y0 + 15.2f, 32f, 0.6f, shade(base, 1.12f))
                    px(c, p, off + 15f, y0, 1.2f, 15.2f, shade(base, 0.8f))
                }
            }
        })

begin(T.WALL_IN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            // 벽지 줄무늬
            for (x in 0 until 32 step 6) {
                px(c, p, x.toFloat(), 3f, 2.6f, 23f, c(0xFFE8D5AE))
                px(c, p, x + 2.6f, 3f, 1f, 23f, c(0xFFFBF3DC))
            }
            noise(c, p, r, 0f, 3f, 32f, 26f, c(0xFFEDE0C0), 8, 1f, 1.4f)
            // 천장 몰딩 / 의자 레일 / 걸레받이
            px(c, p, 0f, 0f, 32f, 3.2f, c(0xFFC9A87B))
            px(c, p, 0f, 2.2f, 32f, 1f, c(0xFFE8D5AE))
            px(c, p, 0f, 22f, 32f, 1.8f, c(0xFFC9A87B))
            px(c, p, 0f, 23.8f, 32f, 0.8f, c(0xFF8A6A4F))
            px(c, p, 0f, 26f, 32f, 6f, c(0xFFE0C9A2))
            px(c, p, 0f, 26f, 32f, 1.2f, c(0xFFB08A5C))
        })

begin(T.WALL_WIN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            for (x in 0 until 32 step 6) {
                px(c, p, x.toFloat(), 3f, 2.6f, 20f, c(0xFFE8D5AE))
            }
            px(c, p, 0f, 0f, 32f, 3.2f, c(0xFFC9A87B))
            // 창
            px(c, p, 5.4f, 5f, 21.2f, 18f, c(0xFF8A5A33))
            px(c, p, 6.6f, 6.2f, 18.8f, 15.6f, c(0xFFA8D8E8))
            vgrad(c, p, 7.2f, 6.8f, 24.8f, 21.2f, c(0xFFC9E8F2), c(0xFF8CC46C), 3)
            px(c, p, 8f, 7.4f, 6f, 4f, c(0xFFE8F6FA))
            px(c, p, 14.4f, 6f, 1.4f, 16f, c(0xFF8A5A33))
            px(c, p, 7f, 13.4f, 18f, 1.4f, c(0xFF8A5A33))
            // 화분
            px(c, p, 8f, 17.6f, 5.4f, 3.6f, c(0xFFB5673F))
            px(c, p, 8.8f, 15.2f, 3.8f, 2.6f, c(0xFF6FAE57))
            px(c, p, 9.8f, 13.8f, 1.8f, 1.8f, c(0xFFF2A3B3))
            // 커튼
            px(c, p, 4.4f, 5f, 2.6f, 17f, c(0xFFE8867A))
            px(c, p, 4.4f, 5f, 1f, 17f, c(0xFFF2A3B3))
            px(c, p, 25f, 5f, 2.6f, 17f, c(0xFFE8867A))
            px(c, p, 26.6f, 5f, 1f, 17f, c(0xFFD96C64))
            px(c, p, 0f, 23f, 32f, 9f, c(0xFFE0C9A2))
            px(c, p, 0f, 23f, 32f, 1.2f, c(0xFFB08A5C))
        })

begin(T.OVEN)
        add(*Array(2) { f ->
            tilePainter { c, p, r ->
                // 벽돌 화덕
                fill(c, p, c(0xFF8F8F99))
                for (row in 0 until 3) {
                    val y = row * 5.4f
                    val off = if (row % 2 == 0) 0f else -6f
                    var x = off
                    while (x < 32f) {
                        val t = lerpColor(c(0xFFA8A8B2), c(0xFF7A7A85), r.nextFloat())
                        px(c, p, x + 0.4f, y + 0.4f, 10.8f, 4.6f, t)
                        px(c, p, x + 0.4f, y + 0.4f, 10.8f, 0.9f, shade(t, 1.18f))
                        px(c, p, x + 0.4f, y + 4.3f, 10.8f, 0.9f, shade(t, 0.78f))
                        x += 11.2f
                    }
                }
                px(c, p, 0f, 16f, 32f, 16f, c(0xFF7A7A85))
                for (row in 0 until 3) {
                    val y = 17f + row * 5f
                    val off = if (row % 2 == 0) 0f else -5f
                    var x = off
                    while (x < 32f) {
                        val t = lerpColor(c(0xFF9A9AA6), c(0xFF6B6B78), r.nextFloat())
                        px(c, p, x + 0.4f, y + 0.4f, 9.6f, 4.2f, t)
                        px(c, p, x + 0.4f, y + 0.4f, 9.6f, 0.9f, shade(t, 1.16f))
                        x += 10f
                    }
                }
                // 아치 화구
                p.color = c(0xFF23232B)
                c.drawCircle(16f, 22f, 10.2f, p)
                p.color = c(0xFF33333D)
                c.drawCircle(16f, 22f, 8.8f, p)
                // 불꽃 (프레임 애니메이션)
                val jig = if (f == 0) 0f else 1.4f
                px(c, p, 9.4f, 19f, 13.2f, 8f, c(0xFFE2574C))
                px(c, p, 11f, 17f + jig, 10f, 8f, c(0xFFF2913C))
                px(c, p, 12.6f, 15.6f - jig, 6.8f, 7.4f, c(0xFFF7CE5B))
                px(c, p, 14.2f, 17.4f + jig * 0.6f, 3.6f, 5f, c(0xFFFDF6E8))
                // 불티
                dot(c, p, 12f - jig, 13f, c(0xFFF2913C))
                dot(c, p, 19.4f + jig, 14.4f, c(0xFFF7CE5B))
                dot(c, p, 16.6f, 11.6f - jig, c(0xFFE2574C))
                // 장작
                px(c, p, 8.6f, 26.4f, 14.8f, 2.6f, c(0xFF6B431F))
                px(c, p, 9.6f, 26.8f, 12.8f, 1.2f, c(0xFF8A5A33))
                // 따뜻한 빛 (입구)
                p.color = Color.argb(46, 255, 160, 80)
                c.drawCircle(16f, 22f, 7f, p)
                // 테두리
                bevel(c, p, 0f, 0f, 32f, 32f, c(0xFFA8A8B2), c(0xFF5A5A66), 1.2f)
            }
        })

begin(T.BED)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            // 프레임
            px(c, p, 1.6f, 1.6f, 28.8f, 28.8f, c(0xFF6B431F))
            px(c, p, 2.8f, 2.8f, 26.4f, 26.4f, c(0xFFB5651D))
            px(c, p, 2.8f, 2.8f, 26.4f, 1.4f, c(0xFFD9884A))
            // 이불 (패치워크)
            px(c, p, 4.2f, 8f, 23.6f, 19.4f, c(0xFFE8867A))
            for (gy in 0 until 3) {
                for (gx in 0 until 4) {
                    val t = if ((gx + gy) % 2 == 0) c(0xFFE8867A) else c(0xFFD96C64)
                    px(c, p, 4.2f + gx * 6f, 8f + gy * 6.6f, 5.6f, 6.2f, t)
                    px(c, p, 4.2f + gx * 6f, 8f + gy * 6.6f, 5.6f, 1f, shade(t, 1.12f))
                }
            }
            // 이불 주름
            px(c, p, 4.2f, 14f, 23.6f, 1.2f, c(0xFFB23F44))
            px(c, p, 4.2f, 21f, 23.6f, 1.2f, c(0xFFB23F44))
            // 베개
            px(c, p, 5f, 3.8f, 11f, 6f, c(0xFFF5EFE0))
            px(c, p, 5.8f, 4.4f, 9.4f, 4.2f, c(0xFFFFFBF2))
            px(c, p, 6.6f, 6.4f, 7.8f, 1f, c(0xFFE0D8C4))
            // 쿠션
            px(c, p, 18f, 10.6f, 9.4f, 3.4f, c(0xFFF7B2A8))
            px(c, p, 18.6f, 11f, 8.2f, 1.6f, c(0xFFFFD0C8))
        })

begin(T.BOX)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFB98F5E), 6, 1f, 1.6f)
            // 박스
            px(c, p, 2f, 3.6f, 28f, 26.4f, c(0xFFC89B6A))
            px(c, p, 2f, 3.6f, 28f, 1.6f, c(0xFFD9B182))
            px(c, p, 2f, 28.4f, 28f, 1.6f, c(0xFFA87B4F))
            px(c, p, 2f, 3.6f, 1.6f, 26.4f, c(0xFFD9B182))
            px(c, p, 28.4f, 3.6f, 1.6f, 26.4f, c(0xFF9A6E42))
            // 테이프
            px(c, p, 13f, 3.6f, 6f, 26.4f, c(0xFFE8D5A3))
            px(c, p, 13f, 3.6f, 1.2f, 26.4f, c(0xFFD9C48E))
            px(c, p, 2f, 14f, 28f, 3.2f, c(0xFFE8D5A3))
            // 라벨
            px(c, p, 18f, 18f, 9.4f, 7f, c(0xFFF5EFE0))
            px(c, p, 19f, 19.4f, 6.4f, 1f, c(0xFF8A6A4F))
            px(c, p, 19f, 21.2f, 7.2f, 1f, c(0xFF8A6A4F))
            px(c, p, 19f, 23f, 4.6f, 1f, c(0xFF8A6A4F))
            // 취급주의 화살표
            px(c, p, 5.6f, 19f, 1.4f, 6f, c(0xFF7A5A33))
            px(c, p, 4.6f, 19f, 3.4f, 1.4f, c(0xFF7A5A33))
            px(c, p, 7f, 20.6f, 3.4f, 1.2f, c(0xFF7A5A33))
            px(c, p, 5.6f, 8f, 1.4f, 6f, c(0xFF7A5A33))
            px(c, p, 4.6f, 8f, 3.4f, 1.4f, c(0xFF7A5A33))
            px(c, p, 7f, 9.6f, 3.4f, 1.2f, c(0xFF7A5A33))
        })

begin(T.DECOR)
        add(tilePainter { c, p, r ->
            val base = c(0xFFCDA775)
            fill(c, p, base)
            for (row in 0..1) {
                val y0 = row * 16f
                for (k in 0 until 4) {
                    val gy = y0 + 2.2f + k * 3.4f
                    p.color = shade(base, 0.9f)
                    c.drawRect(0f, gy, 32f, gy + 0.9f, p)
                    p.color = shade(base, 1.1f)
                    c.drawRect(0f, gy + 0.9f, 32f, gy + 1.5f, p)
                }
                px(c, p, 0f, y0 + 15.2f, 32f, 1.4f, shade(base, 0.78f))
            }
            // 부드러운 스팟라이트
            p.color = Color.argb(30, 255, 250, 220)
            c.drawCircle(16f, 16f, 13f, p)
            p.color = Color.argb(22, 255, 250, 220)
            c.drawCircle(16f, 16f, 9f, p)
            // 점선 슬롯
            p.color = c(0xFFB08A5C)
            for (i2 in 0 until 13) {
                c.drawRect((4 + i2 * 2).toFloat(), 4f, (5 + i2 * 2).toFloat(), 5.4f, p)
                c.drawRect((4 + i2 * 2).toFloat(), 26.6f, (5 + i2 * 2).toFloat(), 28f, p)
                c.drawRect(4f, (4 + i2 * 2).toFloat(), 5.4f, (5 + i2 * 2).toFloat(), p)
                c.drawRect(26.6f, (4 + i2 * 2).toFloat(), 28f, (5 + i2 * 2).toFloat(), p)
            }
            // 모서리 브래킷
            px(c, p, 3f, 3f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 3f, 3f, 1.4f, 4f, c(0xFFE8D5A3))
            px(c, p, 25f, 3f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 27.6f, 3f, 1.4f, 4f, c(0xFFE8D5A3))
            px(c, p, 3f, 27.6f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 3f, 25f, 1.4f, 4f, c(0xFFE8D5A3))
            px(c, p, 25f, 27.6f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 27.6f, 25f, 1.4f, 4f, c(0xFFE8D5A3))
        })

begin(T.SIGN)
        add(tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = Color.argb(56, 26, 46, 28)
            c.drawOval(RectF(8f, 26f, 26f, 31f), p)
            // 기둥
            px(c, p, 14f, 12f, 5f, 18f, c(0xFF6B431F))
            px(c, p, 15f, 12f, 2.4f, 18f, c(0xFF8A5A33))
            px(c, p, 15.6f, 13f, 1f, 15f, c(0xFFA87B4F))
            // 판자
            px(c, p, 3.6f, 3f, 25.8f, 12.4f, c(0xFF6B431F))
            px(c, p, 4.8f, 4.2f, 23.4f, 10f, c(0xFFC89B6A))
            px(c, p, 4.8f, 4.2f, 23.4f, 1.2f, c(0xFFDBB684))
            px(c, p, 4.8f, 13f, 23.4f, 1.2f, c(0xFF8A5A33))
            // 나뭇결
            for (k in 0 until 3) {
                val gy = 6f + k * 2.4f
                px(c, p, 6f, gy, 20f, 0.8f, c(0xFFB08A5C))
            }
            // 글씨 줄
            px(c, p, 7f, 6.4f, 11f, 1.6f, c(0xFF4A3728))
            px(c, p, 7f, 9.6f, 8f, 1.4f, c(0xFF4A3728))
            // 화살표
            val path = Path()
            p.color = c(0xFF4A3728)
            path.moveTo(21f, 6.6f); path.lineTo(25.6f, 9.6f); path.lineTo(21f, 12.4f)
            path.close()
            c.drawPath(path, p)
            px(c, p, 18.4f, 8.6f, 3.4f, 2f, c(0xFF4A3728))
            // 못
            dot(c, p, 5.6f, 5f, c(0xFF33241C))
            dot(c, p, 26.4f, 5f, c(0xFF33241C))
            dot(c, p, 5.6f, 12.4f, c(0xFF33241C))
            dot(c, p, 26.4f, 12.4f, c(0xFF33241C))
            // 이끼
            px(c, p, 4f, 13.4f, 5f, 1.2f, c(0xFF6FAE57))
        })

begin(T.BENCH)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            // 석판 배경 (광장)
            for (gx in 0 until 2) {
                for (gy in 0 until 2) {
                    val x = gx * 16f + 1.6f
                    val y = gy * 16f + 1.6f
                    px(c, p, x, y, 12.8f, 12.8f, c(0xFFE9DCBC))
                    bevel(c, p, x, y, x + 12.8f, y + 12.8f, c(0xFFF1E6C8), c(0xFFB8A87F), 1.2f)
                }
            }
            // 그림자
            p.color = Color.argb(50, 40, 36, 24)
            c.drawOval(RectF(2f, 22f, 30f, 30f), p)
            // 등받이
            px(c, p, 3f, 4f, 26f, 2.6f, c(0xFF6B431F))
            px(c, p, 3.8f, 4.4f, 24.4f, 1.6f, c(0xFFC89B6A))
            px(c, p, 3.8f, 4.4f, 24.4f, 0.8f, c(0xFFDBB684))
            px(c, p, 3f, 8.4f, 26f, 2.2f, c(0xFF6B431F))
            px(c, p, 3.8f, 8.8f, 24.4f, 1.3f, c(0xFFA87B4F))
            // 등받이 기둥
            px(c, p, 5.4f, 3.6f, 2.6f, 12f, c(0xFF4A3320))
            px(c, p, 24f, 3.6f, 2.6f, 12f, c(0xFF4A3320))
            // 좌석
            px(c, p, 2f, 13.6f, 28f, 6f, c(0xFF6B431F))
            px(c, p, 2.8f, 14.2f, 26.4f, 4.2f, c(0xFFA87B4F))
            px(c, p, 2.8f, 14.2f, 26.4f, 1.2f, c(0xFFDBB684))
            // 나뭇결
            for (k in 0 until 3) {
                px(c, p, 4f, 15.2f + k * 1.4f, 24f, 0.7f, c(0xFF8A5A33))
            }
            // 다리 + 볼트
            px(c, p, 4f, 19.6f, 3.2f, 8f, c(0xFF4A3320))
            px(c, p, 24.8f, 19.6f, 3.2f, 8f, c(0xFF4A3320))
            dot(c, p, 5f, 15f, c(0xFF33241C))
            dot(c, p, 26f, 15f, c(0xFF33241C))
        })

begin(T.LAMP)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            for (gx in 0 until 2) {
                for (gy in 0 until 2) {
                    val x = gx * 16f + 1.6f
                    val y = gy * 16f + 1.6f
                    px(c, p, x, y, 12.8f, 12.8f, c(0xFFE9DCBC))
                    bevel(c, p, x, y, x + 12.8f, y + 12.8f, c(0xFFF1E6C8), c(0xFFB8A87F), 1.2f)
                }
            }
            // 그림자
            p.color = Color.argb(50, 40, 36, 24)
            c.drawOval(RectF(8f, 27f, 24f, 31f), p)
            // 기둥
            px(c, p, 14f, 6f, 4.4f, 23f, c(0xFF3A3F4A))
            px(c, p, 15f, 6f, 1.6f, 23f, c(0xFF5A626C))
            px(c, p, 15.4f, 7f, 0.9f, 20f, c(0xFF7C8590))
            // 밑받침
            px(c, p, 11f, 28f, 10f, 2.6f, c(0xFF3A3F4A))
            px(c, p, 12f, 26.6f, 8f, 1.8f, c(0xFF4A5158))
            dot(c, p, 12f, 28.6f, c(0xFF23232B))
            dot(c, p, 19f, 28.6f, c(0xFF23232B))
            // 랜턴 머리
            px(c, p, 8.6f, 1.6f, 15f, 2f, c(0xFF3A3F4A))
            px(c, p, 9.8f, 3.2f, 12.6f, 4.6f, c(0xFF4A5158))
            px(c, p, 10.8f, 3.8f, 10.6f, 3.2f, c(0xFFF7E9A8))
            px(c, p, 12.4f, 4.2f, 7.4f, 2.2f, c(0xFFFFFBE0))
            px(c, p, 15.2f, 7.6f, 1.8f, 1.8f, c(0xFF23232B))
            // 유리창살
            px(c, p, 13.6f, 3.6f, 1f, 3.8f, c(0xFF3A3F4A))
            px(c, p, 17.6f, 3.6f, 1f, 3.8f, c(0xFF3A3F4A))
            px(c, p, 8.6f, 1f, 15f, 1.2f, c(0xFF23232B))
        })

        medallion = RoadArt.medallion(3)
        drain = RoadArt.drain()
        castShadow = RoadArt.castShadows()

        tiles = Array(T.ALL.size) { i ->
            val t = T.ALL[i]
            val v = map[t] ?: error("타일 아트 누락: $t")
            if (v.isEmpty()) error("타일 아트 비어 있음: $t")
            v.toTypedArray()
        }
    }

    /** 타일 좌표 기반 변형 선택 */
    fun tileVariant(tileOrdinal: Int, x: Int, y: Int): Int {
        val n = tiles[tileOrdinal].size
        if (n <= 1) return 0
        return ((x * 7 + y * 13) % n + n) % n
    }

    // -----------------------------------------------------------------------
    // 아이콘
    // -----------------------------------------------------------------------

    private fun buildIcons() {
        val pal = mapOf(
            'c' to c(0xFFE8A75C), 'C' to c(0xFFF7CE5B), 'R' to c(0xFFE2574C),
            'd' to c(0xFFD18F4A), 'b' to c(0xFF6FAE57), 'W' to c(0xFFFDF6E8)
        )
        val pizza = listOf(
            "......................",
            ".....cccccccccc.....",
            "...ccCCCCCCCCCCcc...",
            "..cCCCRCCRCCRCCRCc..",
            "..cCCCCCCCCCCCCCd...",
            ".cCCRCCCCRCCCCCCCd..",
            ".cCCCCRCCCRCCRCCd...",
            ".cCCCCCCCCCCCCCCd...",
            ".cCCRCCCCRCCCCRAd...",
            ".cCCCCCCCCCCCCCd....",
            ".cCCRCCCCRCCCCCd....",
            ".dCCCCCCCCCCCAAd....",
            "..ddddddddddd......",
            "...dddddddddd......",
            "......................"
        )
        val pizzaBmp = sprite(pizza, pal + ('A' to c(0xFF7D9C4F)))
        pizzaIcon = pizzaBmp
        pizzaIconBig = Bitmap.createScaledBitmap(pizzaBmp, pizzaBmp.width * 4, pizzaBmp.height * 4, false)

        cloverIcon = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.isAntiAlias = true
            p.color = c(0xFF3F7D46)
            cv.drawCircle(4.4f, 4.4f, 3.6f, p)
            cv.drawCircle(9.6f, 4.4f, 3.6f, p)
            cv.drawCircle(4.4f, 9.6f, 3.6f, p)
            cv.drawCircle(9.6f, 9.6f, 3.6f, p)
            p.color = c(0xFF4F9E57)
            cv.drawCircle(4f, 4f, 2.8f, p)
            cv.drawCircle(9.6f, 7.6f, 2.8f, p)
            cv.drawCircle(7f, 10f, 2.6f, p)
            p.color = c(0xFF6BBA72)
            cv.drawCircle(3.4f, 3.4f, 1.4f, p)
            cv.drawCircle(9.8f, 9.2f, 1.2f, p)
        }

        cameraIcon = Bitmap.createBitmap(20, 16, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFF4A4A55)
            cv.drawRect(0f, 4f, 20f, 16f, p)
            cv.drawRect(6f, 1f, 13f, 4f, p)
            p.color = c(0xFF6B6B78)
            cv.drawRect(1f, 5f, 19f, 7f, p)
            p.color = c(0xFF23232B)
            cv.drawCircle(10f, 10f, 4.6f, p)
            p.color = c(0xFF8FC3E3)
            cv.drawCircle(10f, 10f, 3.4f, p)
            p.color = c(0xFFC3E4F2)
            cv.drawCircle(9f, 9f, 1.4f, p)
            p.color = c(0xFFF2D06B)
            cv.drawRect(16f, 8f, 18f, 10f, p)
            p.color = c(0xFFE2574C)
            cv.drawRect(2f, 8f, 4f, 10f, p)
        }

        houseIcon = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFFB55338)
            cv.drawRect(1f, 0f, 13f, 5f, p)
            p.color = c(0xFFD4694A)
            cv.drawRect(1f, 0f, 13f, 2f, p)
            p.color = c(0xFFF6E7C6)
            cv.drawRect(1f, 5f, 13f, 14f, p)
            p.color = c(0xFF9FD0E8)
            cv.drawRect(3f, 6f, 6f, 9f, p)
            p.color = c(0xFF8A5A33)
            cv.drawRect(6f, 9f, 9f, 14f, p)
        }

        sunIcon = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.isAntiAlias = true
            p.color = c(0xFFF7CE5B)
            cv.drawCircle(8f, 8f, 4.6f, p)
            p.color = c(0xFFF7E9A8)
            cv.drawCircle(7f, 7f, 3f, p)
            p.color = c(0xFFF2B63C)
            p.strokeWidth = 1.6f
            for (i in 0 until 8) {
                val a = Math.PI * 2 * i / 8.0
                cv.drawLine(
                    (8 + Math.cos(a) * 5.6).toFloat(), (8 + Math.sin(a) * 5.6).toFloat(),
                    (8 + Math.cos(a) * 7.4).toFloat(), (8 + Math.sin(a) * 7.4).toFloat(), p
                )
            }
        }

        moonIcon = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.isAntiAlias = true
            val path = Path()
            path.addCircle(7f, 7f, 6f, Path.Direction.CCW)
            path.addCircle(9.8f, 5.4f, 5f, Path.Direction.CW)
            p.color = c(0xFFF7E9A8)
            cv.drawPath(path, p)
            p.color = c(0xFFDFD08A)
            cv.drawCircle(5.4f, 8.4f, 1f, p)
            cv.drawCircle(4.6f, 5.8f, 0.7f, p)
        }
    }

    // -----------------------------------------------------------------------
    // 장식 아트 (32x32)
    // -----------------------------------------------------------------------

    private fun buildDecorArt() {
        val arts = ArrayList<Bitmap>()

        fun art(paint: (Canvas, Paint) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            paint(Canvas(b), Paint())
            return b
        }
        fun r(c: Canvas, p: Paint, l: Float, t: Float, rr: Float, b: Float, col: Int) {
            p.color = col; c.drawRect(l, t, rr, b, p)
        }

        // 0) 선인장 화분
        arts.add(art { c, p ->
            r(c, p, 10f, 21f, 22f, 29f, c(0xFFB5673F))
            r(c, p, 9f, 20f, 23f, 22f, c(0xFFC97B50))
            r(c, p, 11f, 22f, 21f, 28f, c(0xFFA85834))
            r(c, p, 13.4f, 9f, 18.6f, 21f, c(0xFF4F9E57))
            r(c, p, 14.4f, 10f, 16.2f, 20f, c(0xFF6BBA72))
            r(c, p, 9f, 12f, 12.4f, 18f, c(0xFF4F9E57))
            r(c, p, 9.8f, 12.8f, 11f, 17f, c(0xFF6BBA72))
            r(c, p, 19.6f, 14f, 23f, 20f, c(0xFF4F9E57))
            r(c, p, 21f, 15f, 22.2f, 19f, c(0xFF6BBA72))
            r(c, p, 14.8f, 5.4f, 17.2f, 8f, c(0xFFF2A3B3))
            r(c, p, 15.4f, 6f, 16.6f, 7.4f, c(0xFFFDF6E8))
        })
        // 1) 원목 책장
        arts.add(art { c, p ->
            r(c, p, 5f, 3f, 27f, 30f, c(0xFF6B431F))
            r(c, p, 6.4f, 4.4f, 25.6f, 28.6f, c(0xFF8A5A33))
            r(c, p, 6.4f, 12.4f, 25.6f, 14f, c(0xFF6B431F))
            r(c, p, 6.4f, 21f, 25.6f, 22.6f, c(0xFF6B431F))
            // 책들
            r(c, p, 8f, 8f, 10.4f, 12.4f, c(0xFFE2574C))
            r(c, p, 10.8f, 8.6f, 13.2f, 12.4f, c(0xFF3F6FB0))
            r(c, p, 13.6f, 8f, 15.4f, 12.4f, c(0xFFF2D06B))
            r(c, p, 16f, 8.8f, 19f, 12.4f, c(0xFF4F9E57))
            r(c, p, 20f, 8f, 21.6f, 12.4f, c(0xFFC3A3E8))
            r(c, p, 8f, 16.6f, 11.4f, 21f, c(0xFF3F6FB0))
            r(c, p, 11.8f, 17.2f, 14.4f, 21f, c(0xFFF2D06B))
            r(c, p, 15f, 16.6f, 18.6f, 21f, c(0xFFE2574C))
            r(c, p, 19.6f, 17.4f, 22.4f, 21f, c(0xFF4F9E57))
            r(c, p, 8.6f, 25f, 16f, 28.6f, c(0xFFC89B6A))
            r(c, p, 17f, 25.6f, 23.4f, 28.6f, c(0xFFA87B4F))
        })
        // 2) 러그
        arts.add(art { c, p ->
            val p2 = Paint()
            p2.isAntiAlias = true
            p2.color = c(0xFFB55338)
            c.drawOval(RectF(3f, 6f, 29f, 28f), p2)
            p2.color = c(0xFFD96C64)
            c.drawOval(RectF(5f, 8f, 27f, 26f), p2)
            p2.color = c(0xFFF2D06B)
            c.drawOval(RectF(7.4f, 10.2f, 24.6f, 23.8f), p2)
            p2.color = c(0xFFD96C64)
            c.drawOval(RectF(10f, 12.6f, 22f, 21.4f), p2)
            p2.color = c(0xFFFDF6E8)
            c.drawOval(RectF(13f, 15f, 19f, 19f), p2)
        })
        // 3) 스탠드 조명
        arts.add(art { c, p ->
            r(c, p, 14.8f, 11f, 17.2f, 26f, c(0xFF5A626C))
            r(c, p, 15.6f, 11f, 16.4f, 26f, c(0xFF7C8590))
            r(c, p, 10f, 26f, 22f, 29f, c(0xFF3A3F4A))
            r(c, p, 9f, 4f, 23f, 11.4f, c(0xFFE8A75C))
            r(c, p, 10.4f, 5.4f, 21.6f, 10.6f, c(0xFFF7CE5B))
            r(c, p, 12f, 6.6f, 20f, 9.4f, c(0xFFFFFBE0))
            r(c, p, 9f, 11f, 23f, 12.4f, c(0xFFC97B50))
        })
        // 4) 트로피
        arts.add(art { c, p ->
            r(c, p, 9f, 5f, 23f, 15f, c(0xFFF2B63C))
            r(c, p, 10.4f, 6.4f, 21.6f, 13.6f, c(0xFFF7CE5B))
            r(c, p, 5.4f, 6f, 8.4f, 12f, c(0xFFD99B26))
            r(c, p, 23.6f, 6f, 26.6f, 12f, c(0xFFD99B26))
            r(c, p, 14f, 15f, 18f, 20f, c(0xFFD99B26))
            r(c, p, 10f, 20f, 22f, 23f, c(0xFFB0793F))
            r(c, p, 8f, 23f, 24f, 27f, c(0xFF6B431F))
            r(c, p, 13f, 8f, 15f, 12f, c(0xFFFFFBE0))
            r(c, p, 16.6f, 9f, 18.6f, 11f, c(0xFFE8863C))
        })
        // 5) 빈티지 라디오
        arts.add(art { c, p ->
            r(c, p, 4f, 9f, 28f, 27f, c(0xFF8A5A33))
            r(c, p, 5.4f, 10.4f, 26.6f, 25.6f, c(0xFFA87B4F))
            r(c, p, 7f, 12f, 18f, 16f, c(0xFFD9C39A))
            r(c, p, 7.6f, 12.6f, 17.4f, 15.4f, c(0xFF23232B))
            r(c, p, 8.4f, 13.4f, 16f, 14.6f, c(0xFF4A4A55))
            r(c, p, 20f, 12f, 25f, 16f, c(0xFFF2D06B))
            r(c, p, 20.8f, 12.8f, 24.2f, 15.2f, c(0xFFFFFBE0))
            r(c, p, 7f, 18.4f, 25f, 24f, c(0xFFC89B6A))
            r(c, p, 8f, 19.4f, 24f, 20f, c(0xFF7A5A33))
            r(c, p, 8f, 21.4f, 24f, 22f, c(0xFF7A5A33))
            r(c, p, 12f, 5.4f, 20f, 9f, c(0xFF6B431F))
            r(c, p, 13f, 6.4f, 19f, 8.4f, c(0xFF5A626C))
        })

        decorArt = arts.toTypedArray()
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
