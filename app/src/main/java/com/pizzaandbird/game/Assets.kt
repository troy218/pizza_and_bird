package com.pizzaandbird.game

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

    // 카메라 장비 아트 캐시 (init 보다 먼저 만들어져야 한다)
    private val camIconCache = HashMap<CamLook, Bitmap>()
    private val camProfileCache = HashMap<CamLook, Bitmap>()
    private val camHeldCache = HashMap<HeldKey, Bitmap>()

    private data class HeldKey(val look: CamLook, val dir: Int, val raised: Boolean)

    private val camOutline = c(0xFF191920)
    private val camGlass = c(0xFF3E6B8C)
    private val camGlassHi = c(0xFFBFE4F5)
    private val camStrap = c(0xFF7A4A2B)
    private val camSkin = c(0xFFFFD9B0)
    private val camGold = c(0xFFF2D06B)
    private val camWhiteLens = c(0xFFE8E4D8)

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
        fun add(bmp: Bitmap) {
            map[cur ?: error("begin(T.…) 을 먼저 불러야 한다")]!!.add(bmp)
        }

        // GRASS (6종 변형 — 더 다채로운 초원 디테일)
        begin(T.GRASS)
        for (i in 0 until 6) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r)
                when (i) {
                    1 -> {   // 넝쿨
                        p.color = c(0xFF6FAE57)
                        c.drawRect(5f, 6f, 9f, 7.2f, p)
                        c.drawRect(7f, 5f, 8.2f, 9f, p)
                        c.drawRect(22f, 20f, 26f, 21.2f, p)
                        c.drawRect(24f, 19f, 25.2f, 23f, p)
                    }
                    2 -> {   // 잔돌 (입체 음영)
                        p.color = c(0xFF6B747E)
                        c.drawRect(14f, 18.4f, 17.4f, 20.6f, p)
                        p.color = c(0xFFA8B0A0)
                        c.drawRect(14.4f, 17.6f, 16.8f, 19.8f, p)
                        p.color = c(0xFFC8CFD6)
                        c.drawRect(14.8f, 17.8f, 15.8f, 18.6f, p)
                    }
                    3 -> {   // 민들레 (줄기 + 홀씨)
                        p.color = c(0xFF6FAE57)
                        c.drawRect(20.6f, 10.6f, 21.4f, 14f, p)
                        p.color = c(0xFFFDF6E8)
                        c.drawCircle(21f, 9.6f, 2.1f, p)
                        p.color = c(0xFFF0EAE0)
                        c.drawCircle(21f, 9.6f, 0.9f, p)
                    }
                    4 -> {   // 토끼풀 (클로버 3잎)
                        p.color = c(0xFF5D8A4A)
                        c.drawRect(9.4f, 14.4f, 10.2f, 17.4f, p)
                        p.color = c(0xFF4FA25A)
                        c.drawCircle(8.4f, 13.6f, 1.7f, p)
                        c.drawCircle(11f, 13.6f, 1.7f, p)
                        c.drawCircle(9.7f, 11.8f, 1.7f, p)
                        p.color = c(0xFF6BBA72)
                        c.drawCircle(9.7f, 12.8f, 0.8f, p)
                    }
                    5 -> {   // 작은 들버섯 한 쌍
                        p.color = c(0xFFB0793F)
                        c.drawRect(23.6f, 22.6f, 24.4f, 24.4f, p)
                        p.color = c(0xFFE2574C)
                        c.drawRect(22.4f, 20.6f, 25.6f, 23f, p)
                        p.color = c(0xFFFDF6E8)
                        c.drawRect(23f, 21f, 23.7f, 21.6f, p)
                        c.drawRect(24.6f, 21.6f, 25.2f, 22.2f, p)
                    }
                }
            })
        }
        // TALLGRASS (3종 — 잎끝 하이라이트 + 살짝 휘어진 형태로 자연스러움 강화)
        begin(T.TALLGRASS)
        for (i in 0 until 3) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r, c(0xFF8CC46C))
                // 뒤쪽 어두운 긴 풀 (기울어진 줄기)
                p.color = c(0xFF5D8A4A)
                for (k in 0 until 5 + i) {
                    val x = 1 + r.nextInt(28)
                    val h = 10 + r.nextInt(10)
                    val bend = if (k % 2 == 0) 1.3f else -1.3f
                    c.drawRect(x.toFloat(), (32 - h).toFloat(), x + 2.2f, 32f, p)
                    c.drawRect(x + bend, (32 - h).toFloat(), x + bend + 1.6f, (32 - h + 3f), p)
                }
                // 앞쪽 밝은 풀 + 팁 하이라이트 (그라데이션 느낌)
                repeat(5 + i) {
                    val x = 1 + r.nextInt(28)
                    val h = 8 + r.nextInt(8)
                    val top = (32 - h).toFloat()
                    p.color = c(0xFF6FAE57)
                    c.drawRect(x.toFloat(), top, x + 1.8f, 32f, p)
                    p.color = c(0xFFB7E08C)
                    c.drawRect(x.toFloat(), top, x + 1.8f, top + 2.2f, p)
                }
                // 굵은 갈대성 줄기 2개 포인트
                p.color = c(0xFF5D8A4A)
                c.drawRect(6f, 8f, 7.6f, 18f, p)
                c.drawRect(22f, 6f, 23.6f, 20f, p)
                p.color = c(0xFF8CC46C)
                c.drawRect(6f, 8f, 7f, 10f, p)
                c.drawRect(22f, 6f, 22.8f, 8f, p)
            })
        }
        // FLOWER (4색 — 둥근 4장 꽃잎 + 잎사귀로 훨씬 화사하게)
        begin(T.FLOWER)
        val flowerCols = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFFDFDF8), c(0xFFC9A8E8))
        for (i in 0 until 4) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r)
                repeat(4) {
                    val x = 4f + r.nextInt(21)
                    val y = 5f + r.nextInt(18)
                    // 줄기 + 잎사귀
                    p.color = c(0xFF5D8A4A)
                    c.drawRect(x + 1.7f, y + 2.6f, x + 2.5f, y + 6f, p)
                    p.color = c(0xFF6FAE57)
                    c.drawRect(x + 0.4f, y + 3.8f, x + 2f, y + 5f, p)
                    // 둥근 꽃잎 4장 (십자 대칭 배치)
                    val col = flowerCols[(i + r.nextInt(4)) % 4]
                    p.color = col
                    c.drawCircle(x + 2.1f, y - 0.3f, 1.8f, p)
                    c.drawCircle(x + 2.1f, y + 2.5f, 1.8f, p)
                    c.drawCircle(x + 0.5f, y + 1.1f, 1.8f, p)
                    c.drawCircle(x + 3.7f, y + 1.1f, 1.8f, p)
                    // 꽃술
                    p.color = c(0xFFF7CE5B)
                    c.drawCircle(x + 2.1f, y + 1.1f, 1.3f, p)
                    p.color = c(0xFFE8B14E)
                    c.drawCircle(x + 2.1f, y + 1.1f, 0.6f, p)
                }
            })
        }
        // PATH (길) — 실제 화면에서는 Roads.kt 오토타일이 그린다.
        // 여기 있는 것은 "사방이 모두 길" 인 안쪽 조각 (미니맵/예비용).
        begin(T.PATH)
        for (i in 0 until 3) add(RoadArt.tile(Pave.DIRT, 255, i, false))
        // PLAZA (석재 포장)
        begin(T.PLAZA)
        for (i in 0 until 2) add(RoadArt.tile(Pave.STONE, 255, i, false))
        // SAND (3종)
        begin(T.SAND)
        for (i in 0 until 3) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFFF2E1B0))
                specks(c, p, r, c(0xFFE4CF96), 9)
                specks(c, p, r, c(0xFFF8ECC8), 7)
                if (i == 1) {   // 조개껍데기
                    p.color = c(0xFFFDF6E8)
                    c.drawRect(13f, 17f, 17f, 19.4f, p)
                    p.color = c(0xFFE8B14E)
                    c.drawRect(14.4f, 18f, 15.6f, 19f, p)
                }
                if (i == 2) {   // 물결 무늬
                    p.color = c(0xFFE4CF96)
                    c.drawRect(3f, 12f, 12f, 13.2f, p)
                    c.drawRect(18f, 24f, 28f, 25.2f, p)
                }
            })
        }
        // WATER (4프레임 애니메이션)
        begin(T.WATER)
        for (f in 0 until 4) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFF4FA8D8))
                p.color = c(0xFF63B8E4)
                c.drawRect(0f, 3f, 32f, 6f, p)
                c.drawRect(0f, 14f, 32f, 16f, p)
                c.drawRect(0f, 25f, 32f, 27f, p)
                val off = f * 3f
                p.color = c(0xFF93D4EF)
                c.drawRect((2f + off) % 28f, 4.4f, (2f + off) % 28f + 7f, 5.8f, p)
                c.drawRect((18f + off) % 26f, 15f, (18f + off) % 26f + 8f, 16.4f, p)
                c.drawRect((8f + off) % 26f, 25.6f, (8f + off) % 26f + 7f, 27f, p)
                p.color = c(0xFFC9ECF8)
                c.drawRect((3f + off) % 28f, 4.6f, (3f + off) % 28f + 2.4f, 5.6f, p)
                c.drawRect((19f + off) % 26f, 15.2f, (19f + off) % 26f + 2.4f, 16.2f, p)
            })
        }
        // REED (2종)
        begin(T.REED)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r, c(0xFF8CC46C))
                val xs = if (i == 0) intArrayOf(5, 12, 20, 27) else intArrayOf(8, 15, 24)
                for (x in xs) {
                    p.color = c(0xFF7D9C4F)
                    c.drawRect(x.toFloat(), (if (x % 2 == 0) 4f else 1f), x + 2.2f, 32f, p)
                    p.color = c(0xFFB0793F)
                    val ty = if (x % 2 == 0) 4f else 1f
                    c.drawRect((x - 0.8f), ty, (x + 3f), ty + 7f, p)
                    p.color = c(0xFF8A5A33)
                    c.drawRect(x.toFloat(), ty + 1.6f, x + 1.2f, ty + 5f, p)
                }
                p.color = c(0xFF6FAE57)
                c.drawRect(10f, 22f, 14f, 23.2f, p)
            })
        }
        // TREE (2종: 활엽수 + 침엽수)
        begin(T.TREE)
        add(tilePainter { c, p, r ->
            propShadow(c, p, 16f, 29.5f, 9.5f, 3.4f)
            p.color = c(0xFF5D3A20)
            c.drawRect(14f, 18f, 18f, 31f, p)
            p.color = c(0xFF7A4E2B)
            c.drawRect(14.6f, 18f, 16.2f, 31f, p)
            p.color = c(0xFF33602F)
            c.drawCircle(16f, 12f, 11.4f, p)
            p.color = c(0xFF3F7D46)
            c.drawCircle(16f, 11f, 10.2f, p)
            p.color = c(0xFF4F9E57)
            c.drawCircle(14f, 8.6f, 6.4f, p)
            c.drawCircle(21f, 12.6f, 4.6f, p)
            p.color = c(0xFF6BBA72)
            c.drawCircle(12.4f, 7f, 3.4f, p)
            p.color = c(0xFF2C5429)
            c.drawRect(9f, 17.4f, 24f, 18.6f, p)
        })
        add(tilePainter { c, p, r ->
            propShadow(c, p, 16f, 29.5f, 9.5f, 3.4f)
            p.color = c(0xFF5D3A20)
            c.drawRect(14.6f, 24f, 17.4f, 31f, p)
            val path = Path()
            p.color = c(0xFF2C5A34)
            path.moveTo(16f, 0f); path.lineTo(25f, 13f); path.lineTo(7f, 13f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF3A7044)
            path.reset()
            path.moveTo(16f, 7f); path.lineTo(27f, 21f); path.lineTo(5f, 21f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF2C5A34)
            path.reset()
            path.moveTo(16f, 14f); path.lineTo(29f, 28f); path.lineTo(3f, 28f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF4F9E57)
            c.drawRect(12.4f, 9f, 15f, 10.4f, p)
            c.drawRect(8f, 22f, 10.6f, 23.4f, p)
        })
        // ROCK (2종)
        begin(T.ROCK)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                propShadow(c, p, 16f, 26.5f, 9f, 3f)
                if (i == 0) {
                    p.color = c(0xFF5A626C)
                    c.drawRect(6f, 10f, 26f, 28f, p)
                    p.color = c(0xFF7C8590)
                    c.drawRect(7.4f, 8.6f, 24.6f, 26f, p)
                    p.color = c(0xFF9AA3AD)
                    c.drawRect(9f, 10f, 16f, 15f, p)
                    p.color = c(0xFFB5BDC6)
                    c.drawRect(10.4f, 11f, 13f, 13f, p)
                    p.color = c(0xFF4A5158)
                    c.drawRect(9f, 24f, 24f, 26f, p)
                } else {
                    p.color = c(0xFF7C8590)
                    c.drawRect(10f, 16f, 22f, 26f, p)
                    p.color = c(0xFF9AA3AD)
                    c.drawRect(11f, 14.6f, 20.6f, 24f, p)
                    p.color = c(0xFFB5BDC6)
                    c.drawRect(12.4f, 15.6f, 15f, 18f, p)
                    p.color = c(0xFF6FAE57)
                    c.drawRect(10f, 24f, 13f, 26f, p)
                }
            })
        }
        // MOUNTAIN (2종)
        begin(T.MOUNTAIN)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFF77848F))
                p.color = c(0xFF8D9AA8)
                c.drawRect(0f, 0f, 32f, 6f, p)
                c.drawRect(0f, 12f, 12f, 20f, p)
                c.drawRect(20f, 8f, 32f, 18f, p)
                c.drawRect(0f, 24f, 8f, 32f, p)
                p.color = c(0xFFA5B2BD)
                c.drawRect(4f, 2f, 10f, 4f, p)
                c.drawRect(22f, 10f, 28f, 12f, p)
                p.color = c(0xFF5D6772)
                c.drawRect(0f, 6f, 32f, 8f, p)
                c.drawRect(0f, 20f, 32f, 22f, p)
                c.drawRect(0f, 30f, 32f, 32f, p)
                if (i == 1) {
                    p.color = c(0xFFE8EEF2)
                    c.drawRect(2f, 9f, 8f, 11f, p)
                    c.drawRect(24f, 23f, 29f, 25f, p)
                }
            })
        }
        // BLDG_WALL
        begin(T.BLDG_WALL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFE9E2D3))
            p.color = c(0xFFD8CFBA)
            c.drawRect(0f, 8f, 32f, 9.6f, p)
            c.drawRect(0f, 20f, 32f, 21.6f, p)
            c.drawRect(0f, 30f, 32f, 32f, p)
            p.color = c(0xFFF4EFDF)
            c.drawRect(0f, 0f, 32f, 1.4f, p)
            p.color = c(0xFFCCC2AA)
            c.drawRect(15f, 0f, 16.4f, 8f, p)
            c.drawRect(15f, 9.6f, 16.4f, 20f, p)
        })
        // BLDG_WIN (2종)
        begin(T.BLDG_WIN)
        val curtains = intArrayOf(c(0xFFF2D06B), c(0xFFC3A3E8))
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFFE9E2D3))
                p.color = c(0xFFD8CFBA)
                c.drawRect(0f, 30f, 32f, 32f, p)
                p.color = c(0xFFC9BFA8)
                c.drawRect(6f, 6f, 26f, 24f, p)
                p.color = c(0xFF7FB6D9)
                c.drawRect(7.4f, 7.4f, 24.6f, 22.6f, p)
                p.color = c(0xFFB9DDF0)
                c.drawRect(7.4f, 7.4f, 13f, 13f, p)
                p.color = c(0xFFC9BFA8)
                c.drawRect(15.4f, 7.4f, 16.6f, 22.6f, p)
                c.drawRect(7.4f, 14.6f, 24.6f, 15.8f, p)
                p.color = curtains[i]
                c.drawRect(7.4f, 7.4f, 10f, 22.6f, p)
                c.drawRect(22f, 7.4f, 24.6f, 22.6f, p)
            })
        }
        // BLDG_ROOF
        begin(T.BLDG_ROOF)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFC96A4D))
            p.color = c(0xFFB2583F)
            c.drawRect(0f, 8f, 32f, 9.6f, p)
            c.drawRect(0f, 20f, 32f, 21.6f, p)
            c.drawRect(8f, 0f, 9.6f, 8f, p)
            c.drawRect(24f, 9.6f, 25.6f, 20f, p)
            c.drawRect(8f, 21.6f, 9.6f, 32f, p)
            p.color = c(0xFFDB8266)
            c.drawRect(0f, 0f, 32f, 1.6f, p)
            c.drawRect(4f, 11f, 12f, 12.4f, p)
            c.drawRect(18f, 25f, 26f, 26.4f, p)
        })
        // HOUSE_ROOF
        begin(T.HOUSE_ROOF)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD4694A))
            p.color = c(0xFFB55338)
            c.drawRect(0f, 6f, 32f, 7.6f, p)
            c.drawRect(0f, 16f, 32f, 17.6f, p)
            c.drawRect(0f, 26f, 32f, 27.6f, p)
            c.drawRect(10f, 0f, 11.4f, 6f, p)
            c.drawRect(10f, 17.6f, 11.4f, 26f, p)
            p.color = c(0xFFE08A67)
            c.drawRect(0f, 0f, 32f, 1.4f, p)
            c.drawRect(4f, 10f, 14f, 11.2f, p)
            c.drawRect(18f, 20f, 28f, 21.2f, p)
        })
        // HOUSE_WALL
        begin(T.HOUSE_WALL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 14f, 32f, 16f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 2f, 32f, p)
            c.drawRect(30f, 0f, 32f, 32f, p)
            c.drawRect(0f, 28f, 32f, 32f, p)
            p.color = c(0xFFD9B98C)
            c.drawRect(13f, 0f, 15f, 14f, p)
        })
        // HOUSE_WIN (꽃상자 있는 창문)
        begin(T.HOUSE_WIN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFC9A87B)
            c.drawRect(6f, 5f, 26f, 22f, p)
            p.color = c(0xFF9FD0E8)
            c.drawRect(7.4f, 6.4f, 24.6f, 20.6f, p)
            p.color = c(0xFFC3E4F2)
            c.drawRect(7.4f, 6.4f, 14f, 12f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(15.4f, 6.4f, 16.6f, 20.6f, p)
            c.drawRect(7.4f, 12.6f, 24.6f, 13.8f, p)
            // 꽃상자
            p.color = c(0xFF8A5A33)
            c.drawRect(6f, 22f, 26f, 27f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(7f, 23f, 25f, 26f, p)
            p.color = c(0xFFF2A3B3)
            c.drawRect(8f, 20.4f, 10.4f, 22.4f, p)
            p.color = c(0xFFF2D06B)
            c.drawRect(14f, 20f, 16.4f, 22.4f, p)
            p.color = c(0xFFC3A3E8)
            c.drawRect(21f, 20.6f, 23.4f, 22.4f, p)
        })
        // HOUSE_DOOR
        begin(T.HOUSE_DOOR)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFC9A87B)
            c.drawRect(2f, 3f, 30f, 32f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(3.4f, 4.4f, 28.6f, 32f, p)
            p.color = c(0xFF7A4A2B)
            c.drawRect(5f, 8f, 27f, 10f, p)
            c.drawRect(5f, 16f, 27f, 18f, p)
            p.color = c(0xFF9FD0E8)
            c.drawRect(11f, 19.6f, 21f, 26f, p)
            p.color = c(0xFFC3E4F2)
            c.drawRect(11f, 19.6f, 15f, 23f, p)
            p.color = c(0xFFF2D06B)
            c.drawRect(23f, 14f, 26f, 16.6f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 28f, 32f, 32f, p)
        })
        // TUNNEL
        begin(T.TUNNEL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF77848F))
            p.color = c(0xFF8D9AA8)
            c.drawRect(0f, 0f, 32f, 8f, p)
            p.color = c(0xFFA5B2BD)
            c.drawRect(4f, 1f, 12f, 3f, p)
            p.color = c(0xFF6B4F35)
            c.drawRect(4f, 4f, 28f, 32f, p)
            p.color = c(0xFF5A3F28)
            c.drawRect(2f, 8f, 6f, 32f, p)
            c.drawRect(26f, 8f, 30f, 32f, p)
            p.color = c(0xFF191921)
            c.drawRect(8f, 8f, 24f, 32f, p)
            p.color = c(0xFF2E2E3A)
            c.drawRect(10f, 10f, 22f, 32f, p)
            // 입구 등
            p.color = c(0xFFF2D06B)
            c.drawRect(14f, 2f, 18f, 6f, p)
            p.color = c(0xFFF7E9A8)
            c.drawRect(15f, 3f, 17f, 5f, p)
            // 노면
            p.color = c(0xFF8A8074)
            c.drawRect(8f, 28f, 24f, 32f, p)
        })
        // FLOOR (2종)
        begin(T.FLOOR)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                val base = if (i == 0) c(0xFFCDA775) else c(0xFFC49E6C)
                fill(c, p, base)
                p.color = c(0xFFB98F5E)
                c.drawRect(0f, 10f, 32f, 11.6f, p)
                c.drawRect(0f, 21f, 32f, 22.6f, p)
                val seam = if (i == 0) 10f else 22f
                c.drawRect(seam, 0f, seam + 1.4f, 10f, p)
                c.drawRect(32f - seam, 11.6f, 33.4f - seam, 21f, p)
                p.color = c(0xFFDBB684)
                c.drawRect(0f, 0f, 32f, 1.2f, p)
                c.drawRect(0f, 11.6f, 32f, 12.6f, p)
                c.drawRect(0f, 22.6f, 32f, 23.6f, p)
                p.color = c(0xFFA87B4F)
                c.drawRect(3f, 4f, 4f, 5f, p)
                c.drawRect(27f, 25f, 28f, 26f, p)
            })
        }
        // WALL_IN
        begin(T.WALL_IN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFFE8D5AE)
            c.drawRect(5f, 4f, 7f, 26f, p)
            c.drawRect(14f, 4f, 16f, 26f, p)
            c.drawRect(23f, 4f, 25f, 26f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 32f, 3f, p)
            c.drawRect(0f, 26f, 32f, 27.4f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 27.4f, 32f, 32f, p)
        })
        // WALL_WIN
        begin(T.WALL_WIN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 32f, 3f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(6f, 6f, 26f, 23f, p)
            p.color = c(0xFFA8D8E8)
            c.drawRect(7.4f, 7.4f, 24.6f, 21.6f, p)
            p.color = c(0xFFC9E8F2)
            c.drawRect(7.4f, 7.4f, 15f, 13f, p)
            p.color = c(0xFF8CC46C)
            c.drawRect(7.4f, 16f, 12f, 19f, p)
            c.drawRect(18f, 13f, 20f, 18f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(15.4f, 7.4f, 16.6f, 21.6f, p)
            c.drawRect(7.4f, 13.6f, 24.6f, 14.8f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 23f, 32f, 24f, p)
            c.drawRect(0f, 27.4f, 32f, 32f, p)
        })
        // OVEN (2프레임 — 불꽃 애니메이션)
        begin(T.OVEN)
        for (f in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFF8F8F99))
                p.color = c(0xFF7A7A85)
                c.drawRect(0f, 0f, 32f, 2.4f, p)
                c.drawRect(0f, 14f, 32f, 16f, p)
                c.drawRect(15.4f, 0f, 17f, 14f, p)
                c.drawRect(6f, 16f, 8f, 32f, p)
                c.drawRect(24f, 16f, 26f, 32f, p)
                p.color = c(0xFFA8A8B2)
                c.drawRect(0f, 2.4f, 32f, 3.6f, p)
                // 아치 화구
                p.color = c(0xFF23232B)
                c.drawCircle(16f, 21f, 9.6f, p)
                p.color = c(0xFF33333D)
                c.drawCircle(16f, 21f, 8.6f, p)
                // 불꽃
                p.color = c(0xFFE2574C)
                c.drawRect(9f, 18f, 23f, 28f, p)
                p.color = c(0xFFF2913C)
                c.drawRect(11f, 16f + (if (f == 0) 0f else 1.6f), 21f, 24f, p)
                p.color = c(0xFFF7CE5B)
                c.drawRect(13f, 15f + (if (f == 0) 1.6f else 0f), 19f, 21f, p)
                p.color = c(0xFFFDF6E8)
                c.drawRect(15f, 18f + (if (f == 0) 0f else 1.4f), 17f, 21f, p)
                p.color = c(0xFF6B6B78)
                c.drawRect(9f, 29f, 23f, 30.6f, p)
            })
        }
        // BED
        begin(T.BED)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFF8A5A33)
            c.drawRect(2f, 2f, 30f, 30f, p)
            p.color = c(0xFFB5651D)
            c.drawRect(3.4f, 3.4f, 28.6f, 28.6f, p)
            p.color = c(0xFFE8867A)
            c.drawRect(4.6f, 8f, 27.4f, 27.4f, p)
            p.color = c(0xFFD96C64)
            c.drawRect(4.6f, 16f, 27.4f, 18f, p)
            c.drawRect(12f, 18f, 14f, 27.4f, p)
            p.color = c(0xFFF5EFE0)
            c.drawRect(5.6f, 3.8f, 15f, 9.4f, p)
            p.color = c(0xFFE0D8C4)
            c.drawRect(5.6f, 8f, 15f, 9.4f, p)
            p.color = c(0xFFF7B2A8)
            c.drawRect(18f, 11f, 27.4f, 14f, p)
        })
        // BOX
        begin(T.BOX)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFFC89B6A)
            c.drawRect(2f, 4f, 30f, 30f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(2f, 4f, 30f, 6.4f, p)
            c.drawRect(2f, 17f, 30f, 19f, p)
            c.drawRect(2f, 4f, 4f, 30f, p)
            c.drawRect(28f, 4f, 30f, 30f, p)
            p.color = c(0xFFD9C39A)
            c.drawRect(12f, 6.4f, 20f, 17f, p)
            p.color = c(0xFF7A5A33)
            c.drawRect(14f, 9f, 18f, 10.4f, p)
            c.drawRect(14.8f, 10.4f, 16f, 13f, p)
            c.drawRect(16.8f, 12f, 18f, 13f, p)
            p.color = c(0xFFE8D5A3)
            c.drawRect(13f, 20f, 19f, 26f, p)
            p.color = c(0xFF8A6A4F)
            c.drawRect(13.6f, 21f, 18.4f, 22f, p)
        })
        // DECOR (장식 칸 — 점선 표시)
        begin(T.DECOR)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFFB98F5E)
            c.drawRect(0f, 10f, 32f, 11.6f, p)
            c.drawRect(0f, 21f, 32f, 22.6f, p)
            p.color = c(0xFFB08A5C)
            // 점선 사각형
            for (i in 0 until 13) {
                c.drawRect((4 + i * 2).toFloat(), 4f, (5 + i * 2).toFloat(), 5.4f, p)
                c.drawRect((4 + i * 2).toFloat(), 26.6f, (5 + i * 2).toFloat(), 28f, p)
                c.drawRect(4f, (4 + i * 2).toFloat(), 5.4f, (5 + i * 2).toFloat(), p)
                c.drawRect(26.6f, (4 + i * 2).toFloat(), 28f, (5 + i * 2).toFloat(), p)
            }
            p.color = c(0xFFE8D5A3)
            c.drawRect(14.6f, 14.6f, 17.4f, 17.4f, p)
        })
        // SIGN (터널 이정표)
        begin(T.SIGN)
        add(tilePainter { c, p, r ->
            propShadow(c, p, 16f, 29.5f, 6.5f, 2.4f)
            p.color = c(0xFF6B431F)
            c.drawRect(14.6f, 10f, 17.4f, 30f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(15f, 10f, 16f, 30f, p)
            // 판
            p.color = c(0xFF6B431F)
            c.drawRect(5f, 4f, 27f, 15f, p)
            p.color = c(0xFFC89B6A)
            c.drawRect(6.2f, 5.2f, 25.8f, 13.8f, p)
            p.color = c(0xFF8A6A4F)
            c.drawRect(7.4f, 6.4f, 18f, 7.8f, p)
            c.drawRect(7.4f, 9.4f, 16f, 10.8f, p)
            c.drawRect(7.4f, 12.4f, 18f, 13.2f, p)
            // 화살표
            val path = Path()
            p.color = c(0xFF4A3728)
            path.moveTo(20f, 7f); path.lineTo(24f, 9.6f); path.lineTo(20f, 12.2f)
            path.close()
            c.drawPath(path, p)
            c.drawRect(16.4f, 8.8f, 20.4f, 10.4f, p)
        })
        // BENCH (벤치)
        begin(T.BENCH)
        add(tilePainter { c, p, r ->
            propShadow(c, p, 16f, 27f, 12f, 3.2f)
            // 등받이
            p.color = c(0xFF6B431F)
            c.drawRect(3f, 3f, 29f, 5.4f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(4f, 4f, 28f, 5f, p)
            c.drawRect(6f, 3.4f, 8f, 12f, p)
            c.drawRect(24f, 3.4f, 26f, 12f, p)
            // 좌석
            p.color = c(0xFF6B431F)
            c.drawRect(2.4f, 14f, 29.6f, 19f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(3.4f, 15f, 28.6f, 18f, p)
            p.color = c(0xFFC89B6A)
            c.drawRect(3.4f, 15f, 28.6f, 16f, p)
            // 다리
            p.color = c(0xFF4A3320)
            c.drawRect(4f, 19f, 6.4f, 27f, p)
            c.drawRect(25.6f, 19f, 28f, 27f, p)
        })
        // LAMP (가로등)
        begin(T.LAMP)
        add(tilePainter { c, p, r ->
            propShadow(c, p, 16f, 30f, 8f, 2.4f)
            // 기둥
            p.color = c(0xFF3A3F4A)
            c.drawRect(14.4f, 6f, 17.6f, 30f, p)
            p.color = c(0xFF5A626C)
            c.drawRect(15.2f, 6f, 16.2f, 30f, p)
            p.color = c(0xFF3A3F4A)
            c.drawRect(8f, 29f, 24f, 31f, p)
            // 머리
            p.color = c(0xFF3A3F4A)
            c.drawRect(9f, 2f, 23f, 7f, p)
            p.color = c(0xFFF7E9A8)
            c.drawRect(10.4f, 3.4f, 21.6f, 6f, p)
            p.color = c(0xFFFFFBE0)
            c.drawRect(12f, 4f, 20f, 5.4f, p)
            p.color = c(0xFF23232B)
            c.drawRect(15.4f, 7f, 16.6f, 8.4f, p)
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

        // 기본 카메라 아이콘 — 실제 HUD는 장착한 장비 모양(camIcon)을 쓴다.
        cameraIcon = camIcon(CameraGear.COMPACTS.first().look)

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
    // 카메라 장비 아트 (Cameras.kt의 CamLook으로 생성 — 조합이 바뀌면 모양도 바뀐다)
    //   camIcon    : 정면 아이콘 (22x18) — HUD / 카메라 버튼 / 목록
    //   camProfile : 측면 아이콘 (32x20) — 경통 길이가 한눈에 보이는 상점용
    //   camHeld    : 인게임 스프라이트 (32x32) — 플레이어 위에 겹쳐 그린다
    // -----------------------------------------------------------------------

    /** 색을 밝게(k>1) 또는 어둡게(k<1) */
    private fun tone(col: Int, k: Float): Int {
        val a = (col ushr 24) and 0xFF
        var r = (col shr 16) and 0xFF
        var g = (col shr 8) and 0xFF
        var b = col and 0xFF
        if (k >= 1f) {
            val t = (k - 1f).coerceIn(0f, 1f)
            r += ((255 - r) * t).toInt()
            g += ((255 - g) * t).toInt()
            b += ((255 - b) * t).toInt()
        } else {
            r = (r * k).toInt()
            g = (g * k).toInt()
            b = (b * k).toInt()
        }
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun camIcon(look: CamLook): Bitmap = camIconCache.getOrPut(look) { buildCamIcon(look) }

    fun camProfile(look: CamLook): Bitmap = camProfileCache.getOrPut(look) { buildCamProfile(look) }

    /** dir: 0 정면 / 1 뒤 / 2 오른쪽 / 3 왼쪽 */
    fun camHeld(look: CamLook, dir: Int, raised: Boolean): Bitmap {
        if (dir == 3) {
            return camHeldCache.getOrPut(HeldKey(look, 3, raised)) { flipH(camHeld(look, 2, raised)) }
        }
        return camHeldCache.getOrPut(HeldKey(look, dir, raised)) { buildCamHeld(look, dir, raised) }
    }

    private fun buildCamIcon(lk: CamLook): Bitmap {
        val bmp = Bitmap.createBitmap(22, 18, Bitmap.Config.ARGB_8888)
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

        val big = lk.style == 3 || lk.style == 4 || lk.style == 5
        val pro = lk.style == 4
        val top = if (big) 4.2f else 5.0f
        val bot = if (pro) 17.0f else 16.4f

        // 펜타프리즘 / EVF / 팝업 플래시
        if (big) {
            o(7.4f, 0.8f, 14.6f, 5.2f, 1.2f, camOutline)
            o(8.0f, 1.4f, 14.0f, 5.0f, 1.0f, lk.bodyCol)
            r(9.0f, 2.0f, 13.0f, 3.0f, tone(lk.bodyCol, 1.25f))
        } else if (lk.evf) {
            o(2.0f, 2.0f, 8.0f, 5.6f, 1.1f, camOutline)
            o(2.6f, 2.6f, 7.4f, 5.2f, 0.9f, lk.bodyCol)
        } else if (lk.flash) {
            o(2.2f, 2.4f, 6.4f, 5.4f, 0.9f, camOutline)
            o(2.7f, 2.9f, 5.9f, 5.0f, 0.7f, c(0xFFF7EFD2))
        }

        // 바디
        o(0.4f, top - 0.6f, 21.6f, bot + 0.8f, 2.8f, camOutline)
        o(1.0f, top, 21.0f, bot, 2.4f, lk.bodyCol)
        r(1.6f, top + 0.8f, 20.4f, top + 1.8f, tone(lk.bodyCol, 1.18f))
        o(16.0f, top + 1.2f, 20.6f, bot - 0.6f, 1.8f, lk.bodyDark)
        r(17.0f, top + 2.4f, 19.6f, bot - 2.0f, tone(lk.bodyDark, 0.8f))
        r(2.0f, top - 1.8f, 4.2f, top + 0.2f, lk.accent)
        if (pro) r(1.0f, bot - 2.4f, 21.0f, bot, lk.bodyDark)

        // 렌즈 (정면에서는 지름이 존재감)
        val lr = (3.2f + lk.barrelDia * 0.52f + lk.barrelLen * 0.13f).coerceAtMost(6.6f)
        val cx = 10.2f
        val cy = (top + bot) / 2f + 0.4f
        if (lk.hood) {
            cir(cx, cy, lr + 1.6f, camOutline)
            cir(cx, cy, lr + 1.0f, tone(lk.barrelCol, 0.85f))
        }
        cir(cx, cy, lr + 0.9f, camOutline)
        cir(cx, cy, lr, lk.barrelCol)
        cir(cx, cy, lr * 0.72f, tone(lk.barrelCol, 0.6f))
        cir(cx, cy, lr * 0.58f, camGlass)
        cir(cx - lr * 0.26f, cy - lr * 0.28f, lr * 0.26f, camGlassHi)
        return bmp
    }

    private fun buildCamProfile(lk: CamLook): Bitmap {
        val bmp = Bitmap.createBitmap(32, 20, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
            p.color = col; cv.drawRect(l, t, rr, b, p)
        }
        fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
            p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
        }
        fun ov(l: Float, t: Float, rr: Float, b: Float, col: Int) {
            p.color = col; cv.drawOval(RectF(l, t, rr, b), p)
        }

        val big = lk.style == 3 || lk.style == 4 || lk.style == 5
        val pro = lk.style == 4
        val top = if (big) 4.6f else 5.4f
        val bot = if (pro) 18.0f else 17.2f
        val bodyL = 1.0f
        val bodyR = 11.6f
        val cy = (top + bot) / 2f + 0.3f

        if (big) {
            o(3.4f, 0.8f, 10.2f, 5.6f, 1.2f, camOutline)
            o(4.0f, 1.4f, 9.6f, 5.4f, 1.0f, lk.bodyCol)
        } else if (lk.evf) {
            o(1.6f, 2.4f, 6.8f, 6.0f, 1.0f, camOutline)
            o(2.2f, 3.0f, 6.2f, 5.8f, 0.8f, lk.bodyCol)
        } else if (lk.flash) {
            o(2.0f, 2.6f, 5.6f, 5.6f, 0.8f, camOutline)
            o(2.5f, 3.1f, 5.1f, 5.2f, 0.6f, c(0xFFF7EFD2))
        }

        o(bodyL - 0.6f, top - 0.6f, bodyR + 0.6f, bot + 0.8f, 2.4f, camOutline)
        o(bodyL, top, bodyR, bot, 2.0f, lk.bodyCol)
        r(bodyL + 0.6f, top + 0.8f, bodyR - 0.6f, top + 1.7f, tone(lk.bodyCol, 1.18f))
        o(bodyL, top + 1.0f, bodyL + 3.2f, bot, 1.6f, lk.bodyDark)
        r(bodyL + 4.6f, top - 1.6f, bodyL + 6.4f, top + 0.2f, lk.accent)
        if (pro) r(bodyL, bot - 2.2f, bodyR, bot, lk.bodyDark)

        // 경통
        val ln = (2.6f + lk.barrelLen * 1.9f).coerceAtMost(19.4f)
        val dia = (5.0f + lk.barrelDia * 1.15f).coerceAtMost(14.6f)
        val x0 = bodyR - 0.6f
        val x1 = x0 + ln
        val t0 = cy - dia / 2f
        val b0 = cy + dia / 2f
        r(x0 - 0.6f, t0 - 1.0f, x0 + 1.4f, b0 + 1.0f, camOutline)
        r(x0 - 0.2f, t0 - 0.6f, x0 + 1.2f, b0 + 0.6f, tone(lk.bodyCol, 1.5f))
        r(x0 + 1.2f, t0 - 0.7f, x1 + 0.7f, b0 + 0.7f, camOutline)
        r(x0 + 1.2f, t0, x1, b0, lk.barrelCol)
        r(x0 + 1.2f, t0, x1, t0 + 1.2f, tone(lk.barrelCol, 1.2f))
        r(x0 + 1.2f, b0 - 1.0f, x1, b0, tone(lk.barrelCol, 0.78f))
        val ring = tone(lk.barrelCol, 0.66f)
        r(x0 + 1.2f + ln * 0.30f, t0, x0 + 1.2f + ln * 0.42f, b0, ring)
        if (ln > 9f) r(x0 + 1.2f + ln * 0.58f, t0, x0 + 1.2f + ln * 0.68f, b0, ring)
        if (lk.barrelCol == camWhiteLens) r(x0 + 1.6f, t0, x0 + 2.6f, b0, camGold)
        if (dia > 10f) r(x0 + 3.0f, b0, x0 + 7.0f, b0 + 1.8f, lk.bodyDark)
        if (lk.hood) {
            r(x1 - 2.4f, t0 - 1.6f, x1 + 0.8f, b0 + 1.6f, camOutline)
            r(x1 - 2.2f, t0 - 1.2f, x1 + 0.2f, b0 + 1.2f, tone(lk.barrelCol, 0.88f))
        }
        val gx1 = if (lk.hood) x1 - 1.6f else x1 - 0.4f
        ov(gx1 - 2.4f, cy - dia * 0.34f, gx1, cy + dia * 0.34f, camGlass)
        ov(gx1 - 2.0f, cy - dia * 0.2f, gx1 - 1.0f, cy + dia * 0.02f, camGlassHi)
        return bmp
    }

    private fun buildCamHeld(lk: CamLook, dir: Int, raised: Boolean): Bitmap {
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

        val ln = (2.0f + lk.barrelLen * 0.95f).coerceAtMost(12.0f)
        val dia = (3.4f + lk.barrelDia * 0.62f).coerceAtMost(8.0f)
        val big = lk.style == 3 || lk.style == 4 || lk.style == 5

        if (raised) {
            when (dir) {
                0 -> {   // 정면 — 얼굴 앞으로 들어올린 카메라
                    o(10.2f, 7.0f, 21.8f, 15.4f, 1.6f, camOutline)
                    o(10.8f, 7.6f, 21.2f, 14.8f, 1.4f, lk.bodyCol)
                    r(11.4f, 8.2f, 20.6f, 9.2f, lk.bodyDark)
                    if (big || lk.evf) {
                        r(13.6f, 5.6f, 18.4f, 7.4f, camOutline)
                        r(14.2f, 6.0f, 17.8f, 7.4f, lk.bodyCol)
                    }
                    r(19.4f, 6.4f, 20.8f, 7.6f, lk.accent)
                    val gr = dia * 0.42f + 1.4f
                    if (lk.hood) {
                        cir(16f, 11.4f, gr + 1.8f, camOutline)
                        cir(16f, 11.4f, gr + 1.2f, tone(lk.barrelCol, 0.85f))
                    }
                    cir(16f, 11.4f, gr + 0.9f, camOutline)
                    cir(16f, 11.4f, gr, lk.barrelCol)
                    cir(16f, 11.4f, gr * 0.68f, camGlass)
                    cir(15.2f, 10.6f, gr * 0.28f, camGlassHi)
                    r(8.6f, 12.4f, 11.0f, 15.6f, camSkin)
                    r(21.0f, 12.4f, 23.4f, 15.6f, camSkin)
                }
                1 -> {   // 뒤 — 카메라 뒷면(액정)과 팔꿈치
                    o(11.6f, 7.4f, 20.4f, 14.2f, 1.4f, camOutline)
                    o(12.2f, 8.0f, 19.8f, 13.6f, 1.2f, lk.bodyDark)
                    r(13.2f, 9.0f, 18.8f, 12.6f, c(0xFF6E8FA6))
                    r(8.8f, 11.6f, 11.6f, 15.0f, camSkin)
                    r(20.4f, 11.6f, 23.2f, 15.0f, camSkin)
                }
                else -> { // 측면 — 경통이 정면(오른쪽)으로 뻗는다
                    o(12.6f, 7.6f, 19.6f, 14.6f, 1.5f, camOutline)
                    o(13.2f, 8.2f, 19.0f, 14.0f, 1.3f, lk.bodyCol)
                    if (big || lk.evf) {
                        r(14.0f, 6.0f, 17.6f, 7.8f, camOutline)
                        r(14.4f, 6.4f, 17.2f, 7.8f, lk.bodyCol)
                    }
                    val x0 = 19.0f
                    val x1 = (19.0f + ln).coerceAtMost(31.0f)
                    val ccy = 11.2f
                    r(x0, ccy - dia / 2f - 0.7f, x1 + 0.7f, ccy + dia / 2f + 0.7f, camOutline)
                    r(x0, ccy - dia / 2f, x1, ccy + dia / 2f, lk.barrelCol)
                    r(x0 + (x1 - x0) * 0.4f, ccy - dia / 2f, x0 + (x1 - x0) * 0.52f, ccy + dia / 2f, tone(lk.barrelCol, 0.66f))
                    if (lk.barrelCol == camWhiteLens) r(x0 + 0.8f, ccy - dia / 2f, x0 + 1.6f, ccy + dia / 2f, camGold)
                    if (lk.hood) {
                        r(x1 - 2.2f, ccy - dia / 2f - 1.2f, x1 + 0.7f, ccy + dia / 2f + 1.2f, camOutline)
                        r(x1 - 2.0f, ccy - dia / 2f - 0.8f, x1, ccy + dia / 2f + 0.8f, tone(lk.barrelCol, 0.88f))
                    }
                    cir(x1 - 1.3f, ccy, dia * 0.3f + 0.7f, camGlass)
                    cir(x1 - 1.8f, ccy - 0.6f, dia * 0.14f + 0.3f, camGlassHi)
                    r(11.4f, 12.0f, 13.8f, 15.2f, camSkin)
                }
            }
        } else {
            when (dir) {
                0 -> {   // 정면 — 가슴에 매달린 카메라
                    r(12.0f, 15.6f, 13.2f, 17.8f, camStrap)
                    r(19.0f, 15.6f, 20.2f, 17.8f, camStrap)
                    o(12.0f, 17.4f, 20.2f, 22.6f, 1.3f, camOutline)
                    o(12.5f, 17.9f, 19.7f, 22.1f, 1.1f, lk.bodyCol)
                    r(13.0f, 18.3f, 19.2f, 19.1f, lk.bodyDark)
                    val gr = dia * 0.3f + 0.9f
                    cir(16.1f, 20.2f, gr + 0.7f, camOutline)
                    cir(16.1f, 20.2f, gr, lk.barrelCol)
                    cir(16.1f, 20.2f, gr * 0.6f, camGlass)
                    r(18.6f, 17.0f, 19.6f, 17.9f, lk.accent)
                }
                1 -> {   // 뒤 — 어깨 위 스트랩과 옆구리의 카메라
                    r(11.8f, 15.4f, 13.2f, 19.2f, camStrap)
                    r(19.0f, 15.4f, 20.4f, 19.2f, camStrap)
                    o(18.8f, 18.6f, 23.0f, 22.4f, 1.2f, camOutline)
                    o(19.3f, 19.1f, 22.5f, 21.9f, 1.0f, lk.bodyDark)
                }
                else -> { // 측면 — 옆구리에 걸친 카메라
                    r(14.6f, 15.2f, 15.8f, 18.4f, camStrap)
                    o(13.8f, 18.0f, 20.0f, 22.6f, 1.3f, camOutline)
                    o(14.3f, 18.5f, 19.5f, 22.1f, 1.1f, lk.bodyCol)
                    val x0 = 19.2f
                    val x1 = (19.2f + ln * 0.55f).coerceAtMost(27.0f)
                    val ccy = 20.3f
                    val hd = dia * 0.42f
                    r(x0, ccy - hd - 0.6f, x1 + 0.6f, ccy + hd + 0.6f, camOutline)
                    r(x0, ccy - hd, x1, ccy + hd, lk.barrelCol)
                    cir(x1 - 1.0f, ccy, hd * 0.75f, camGlass)
                }
            }
        }
        return bmp
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
