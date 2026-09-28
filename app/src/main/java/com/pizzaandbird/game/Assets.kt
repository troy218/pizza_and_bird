package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import java.util.Random
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.sqrt

// 살아있는 풀 리그 상수 (파일 최상위 — 클래스 본문 안에서는 const val 을 쓸 수 없다)
private const val GRASS_KINDS = 5
private const val GRASS_LEAN_MAX = 5      // 좌우 기움 -5..+5
private const val GRASS_CURL_MAX = 3      // 휨(곡률) -3..+3
private const val GRASS_W = 18            // 포즈 비트맵 폭
private const val GRASS_CX = 9            // 비트맵 안에서 밑동(뿌리) 열
private const val GRASS_LEAN_UNIT = 1.5f
private const val GRASS_CURL_UNIT = 1.25f

/** 자전거 페달 애니메이션 프레임 수 */
const val BIKE_FRAMES = 8

// T.TREE 변형 인덱스 — buildTiles() 의 추가 순서와 일치해야 한다.
//   0..3   기본 4종 (참나무·소나무·벚나무·단풍)
//   4..11  extraTreeArt 8종 (버드나무·대나무·동백·곰솔·자작나무·은행·과수원·전나무)
//   12..14 계절 전용 3종 (앙상한 나무·눈 덮인 활엽수·눈 덮인 소나무)
//   15..36 speciesTreeArt — 한반도에 실제로 서식하는 나무 21종 + 메타세콰이아(가을)
//   37..38 계절 전용 2종 (눈 덮인 가문비나무·눈 덮인 진달래)
//   39..42 regionalTreeArt — 지역 가로수·특수 수종 4종 (느티나무 가로수·메타세콰이어 원뿔·제주 야자수·오리나무)
private const val TREE_OAK = 0
private const val TREE_PINE = 1
private const val TREE_CHERRY = 2
private const val TREE_MAPLE = 3
private const val TREE_WILLOW = 4
private const val TREE_BAMBOO = 5
private const val TREE_CAMELLIA = 6
private const val TREE_SEAPINE = 7
private const val TREE_BIRCH = 8
private const val TREE_GINKGO = 9
private const val TREE_ORCHARD = 10
private const val TREE_FIR = 11
private const val TREE_BARE = 12
private const val TREE_SNOWLEAF = 13
private const val TREE_SNOWPINE = 14
// ── 실제 나무 21종 (speciesTreeArt) ────────────────────────────────────────
private const val TREE_SPRUCE = 15          // 가문비나무 — 키 큰 침엽, 처지는 가지층
private const val TREE_KPINE = 16           // 잣나무 — 연한 청록색 긴 침엽, 트인 수관
private const val TREE_YEW = 17             // 주목 — 납작한 짙은 수관 + 붉은 가시아
private const val TREE_JUNIPER = 18         // 노간주나무 — 바람에 비틀린 작은 침엽
private const val TREE_MACHILUS = 19        // 후박나무 — 남해안 상록, 녹색 새순
private const val TREE_GREENOAK = 20        // 붉가시나무 — 남부 상록참나무
private const val TREE_MONGOAK = 21         // 신갈나무 — 거친 나무껍질의 큰 활엽
private const val TREE_ACORNOAK = 22        // 상수리나무 — 갈라진 잎 + 도토리
private const val TREE_BIGLEAF = 23         // 떡갈나무 — 한반도 최대 잎, 낮고 넓은 수관
private const val TREE_ZELKOVA = 24         // 느티나무 — 마을 당산나무, 곧게 큰 원통형
private const val TREE_ASH = 25             // 물푸레나무 — 마주난 가지의 키 큰 활엽
private const val TREE_CHESTNUT = 26        // 밤나무 — 넓은 수관 + 밤송이
private const val TREE_ACACIA = 27          // 아까시나무 — 성긴 겹잎 + 흰 꽃차례
private const val TREE_PERSIMMON = 28       // 감나무 — 꼬부란 줄기 + 주황 열매
private const val TREE_WINGNUT = 29         // 물오리나무 — 강변, 긴 깃모양 잎
private const val TREE_PLANETREE = 30       // 회화나무(플라타너스) — 도심 가로수
private const val TREE_METASEQUOIA = 31     // 메타세콰이아 — 아주 곧고 키 큰 낙침
private const val TREE_WILDCHERRY = 32      // 산벚나무 — 작은 야생 벚꽃
private const val TREE_AZALEA = 33          // 진달래 — 분홍 꽃이 덮이는 작은 관목
private const val TREE_CITRUS = 34          // 감귤나무 — 제주 상록 + 귤 열매
private const val TREE_PALM = 35            // 야자나무 — 가는 줄기에 부채꼴 잎
private const val TREE_METASEQUOIA_A = 36   // 메타세콰이아(가을) — 잎이 붉게 물듦
// ── 계절 전용 ────────────────────────────────────────────────────────────
private const val TREE_SNOWSPRUCE = 37      // 눈 덮인 가문비나무
private const val TREE_SNOWBUSH = 38        // 눈 덮인 진달래

/** T.TREE 변형 한 종의 정체 — [treeKinds] 의 인덱스는 위 TREE_* 상수와 같다. */
data class TreeKind(
    /** 사람이 읽는 이름 (프리뷰 나무 도감·주석용) */
    val name: String,
    /** 수형 — 타일 안에서 실제로 차지하는 크기/실루엣 요약 */
    val form: String,
    /** 상록수 — 사계절 같은 그림을 쓰고 겨울에도 잎이 남아 있다 */
    val evergreen: Boolean,
    /**
     * 수관(잎이 달린 부분)의 타일 내 범위 — 가운데 점(crownX, crownY)과 반지름.
     * 눈이 쌓이는 연출([Fx.drawSnowOnTile])이 나무 크기에 맞는 자리에 얹히고,
     * "이 나무가 타일에서 얼마나 큰가" 를 숫자로 남긴다.
     */
    val crownX: Float,
    val crownY: Float,
    val crownRx: Float,
    val crownRy: Float
)

/**
 * T.TREE 변형 39종의 명찰. [Assets.buildTiles] 에서 T.TREE 아트를 추가하는 순서와
 * 1:1로 맞춰야 하며, [Assets.seasonTreeIndex] 가 계절별로 갈아입을 때 쓴다.
 *
 * `form` 은 한 타일(32px) 안에서 그 나무가 차지하는 크기를 뜻한다 —
 * "아주 키 큰"은 타일 위쪽까지 꽉 차고, "작은"은 1/2 타일 높이의 관목이다.
 */
val treeKinds: List<TreeKind> = listOf(
    TreeKind("참나무", "중형 활엽", false, 16f, 11f, 12f, 11f),
    TreeKind("소나무", "중형 침엽", true, 16f, 9f, 13f, 9f),
    TreeKind("벚나무", "중형 활엽", false, 16f, 9f, 10f, 8f),
    TreeKind("단풍나무", "중형 활엽", false, 16f, 9f, 11f, 9f),
    TreeKind("버드나무", "큰 활엽", false, 16f, 9f, 10f, 8f),
    TreeKind("대나무", "키 큰 죽관", true, 16f, 12f, 7f, 10f),
    TreeKind("동백나무", "중형 상록", true, 16f, 11f, 11f, 9f),
    TreeKind("곰솔", "중형 침엽", true, 16f, 11f, 13f, 11f),
    TreeKind("자작나무", "중형 활엽", false, 16f, 9f, 8f, 7f),
    TreeKind("은행나무", "큰 활엽", false, 16f, 10f, 11f, 9f),
    TreeKind("과수원", "중형 활엽", false, 16f, 10f, 10f, 8f),
    TreeKind("전나무", "큰 침엽", true, 16f, 10f, 13f, 10f),
    TreeKind("겨울 나무", "앙상한 가지", false, 16f, 7f, 11f, 7f),
    TreeKind("눈 덮인 나무", "앙상한 가지", false, 16f, 7f, 11f, 7f),
    TreeKind("눈 덮인 소나무", "중형 침엽", true, 16f, 9f, 13f, 9f),
    TreeKind("가문비나무", "아주 키 큰 침엽", true, 16f, 10f, 11f, 10f),
    TreeKind("잣나무", "큰 침엽", true, 16f, 9f, 9f, 7f),
    TreeKind("주목", "넓고 납작한 상록", true, 16f, 12f, 12f, 8f),
    TreeKind("노간주나무", "비틀린 작은 침엽", true, 16f, 12f, 8f, 7f),
    TreeKind("후박나무", "중형 상록", true, 16f, 11f, 10f, 8f),
    TreeKind("붉가시나무", "중형 상록", true, 16f, 11f, 10f, 8f),
    TreeKind("신갈나무", "큰 활엽", false, 16f, 10f, 12f, 10f),
    TreeKind("상수리나무", "중형 활엽", false, 16f, 11f, 9f, 8f),
    TreeKind("떡갈나무", "아주 넓은 활엽", false, 16f, 13f, 14f, 8f),
    TreeKind("느티나무", "아주 키 큰 활엽", false, 16f, 7f, 12f, 6f),
    TreeKind("물푸레나무", "키 큰 활엽", false, 16f, 12f, 10f, 10f),
    TreeKind("밤나무", "넓은 활엽", false, 16f, 10f, 11f, 9f),
    TreeKind("아까시나무", "중형 활엽", false, 16f, 11f, 10f, 9f),
    TreeKind("감나무", "중형 활엽", false, 16f, 10f, 9f, 8f),
    TreeKind("물오리나무", "큰 활엽", false, 16f, 11f, 11f, 9f),
    TreeKind("회화나무", "큰 활엽", false, 16f, 10f, 11f, 9f),
    TreeKind("메타세콰이아", "아주 키 큰 낙침", false, 16f, 12f, 6f, 11f),
    TreeKind("산벚나무", "작은 활엽", false, 16f, 12f, 7f, 6f),
    TreeKind("진달래", "작은 관목", false, 16f, 14f, 7f, 6f),
    TreeKind("감귤나무", "작은 상록", true, 16f, 14f, 7f, 6f),
    TreeKind("야자나무", "아주 키 큰", true, 16f, 10f, 11f, 8f),
    TreeKind("메타세콰이아(가을)", "아주 키 큰 낙침", false, 16f, 12f, 6f, 11f),
    TreeKind("눈 덮인 가문비나무", "아주 키 큰 침엽", true, 16f, 10f, 11f, 10f),
    TreeKind("눈 덮인 진달래", "작은 관목", false, 16f, 14f, 7f, 6f),
    TreeKind("느티나무(가로수)", "중형 활엽", false, 16f, 10f, 12f, 10f),
    TreeKind("메타세콰이어(원뿔)", "키 큰 낙침", false, 16f, 13f, 9f, 12f),
    TreeKind("야자나무(제주)", "아주 키 큰", true, 16f, 10f, 11f, 8f),
    TreeKind("오리나무", "중형 활엽", false, 16f, 9f, 9f, 8f),
)
// ── 지역 수종 4종 (regionalTreeArt) — 인덱스 39..42 ───────────────────────
private const val TREE_ZELKOVA_STREET = 39   // 느티나무(가로수) — 거치미처럼 벌어진 줄기, 얼룩배기
private const val TREE_METASEQUOIA_CONE = 40 // 메타세콰이어(원뿔) — 붉은 나무껍질의 곧은 원뿔
private const val TREE_PALM_JEJU = 41        // 야자나무(제주 해안) — 소노베 실루엣
private const val TREE_ALDER = 42            // 오리나무 — 하얀 곧은 줄기의 강원 산지 나무
/**
 * 플레이어 캐릭터 스프라이트를 그리는 해상도(px, 한 변).
 *
 * 32(= [CharacterArt.SIZE])의 정수배여야 픽셀 격자가 어긋나지 않는다.
 * 96 = 3배 — 월드 슈퍼샘플 배율([Game.worldScale])이 2~3일 때 원래 크기(32 도트)로
 * 줄여 그리므로 픽셀 굵기는 예전 그대로면서, 옷 주름·머리 윤기·눈 반짝임·AA 윤곽이
 * 살아남는다. 프레임 한 장이 4KB → 36KB 로 늘어나므로 캐시 개수를 LRU 로 묶어 둔다.
 */
const val CHARACTER_PX = CharacterArt.SIZE * 3

/** 자전거(라이더 포함) 스프라이트 해상도 — [CHARACTER_PX] 와 같은 규칙 */
const val BIKE_PX = CHARACTER_PX

/**
 * 레벨업 축하 동작(만세!) 해상도.
 *
 * 이 화면은 캐릭터를 화면 가득(약 300px) 띄우므로 3배로는 부족하다 —
 * 정면 8프레임만 크게 그려 두면 "확대한 도트"가 아니라 진짜 그림이 된다.
 */
const val CHEER_PX = CharacterArt.SIZE * 8

/** 캐릭터 선택 카드용 미리보기 해상도 (카드는 화면에서 3배쯤 크게 그려진다) */
const val AVATAR_PX = CharacterArt.SIZE * 4

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
 * SIT   : 벤치에 앉기 (숨쉬며 두리번 — 상단 여백 SIT_TOPPAD 사용)
 */
enum class Anim(val frames: Int, val frameTime: Float) {
    IDLE(12, 0.17f),
    WALK(8, 0.085f),
    RUN(8, 0.062f),
    SNEAK(8, 0.13f),
    AIM(4, 0.2f),
    SIT(8, 0.17f);

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
    // 앉은 자세 스프라이트는 종별로 *처음 필요할 때* 만들어 캐시한다.
    // (예전엔 시작 시 598종을 전부 만들어 앱이 켜질 때까지 한참 걸렸다)
    private val birdCache = LinkedHashMap<String, Bitmap>()
    private val birdCacheWithPhoto = HashSet<String>()
    private data class BirdPoseKey(val id: String, val facing: BirdFacing, val pose: BirdPose)
    private val birdPoseCache = LinkedHashMap<BirdPoseKey, Bitmap>()
    private val birdPoseCacheWithPhoto = HashSet<BirdPoseKey>()
    private val birdFlights = LinkedHashMap<String, Array<Bitmap>>() // 필요할 때 생성
    private val birdFlightsFlipped = LinkedHashMap<String, Array<Bitmap>>()
    // 새 사진 기준색 캐시 — 미리 읽기 스레드와 게임 스레드가 함께 본다.
    private val birdReferencePalettes = java.util.concurrent.ConcurrentHashMap<String, BirdRenderPalette>()

    /**
     * 새 사진 기준색을 미리 계산해 두는 전용 스레드.
     *
     * 사진(jpg)을 읽고 기준색을 구하는 일은 여기서만 한다. 게임 스레드는 그동안
     * BirdArt 기본색으로 그렸다가 기준색이 준비되면 캐시를 교체한다.
     * 도감 페이지/필드 스폰 첫 프레임에 JPEG 디코드가 끼지 않도록 한다.
     */
    private val birdPaletteQueue = LinkedBlockingQueue<String>()
    private val birdPalettePending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val birdPaletteThread = Thread({
        while (true) {
            val id = birdPaletteQueue.take()
            try {
                Birds.byId[id]?.let { computeBirdReferencePalette(it) }
            } catch (_: Exception) {
            } finally {
                birdPalettePending.remove(id)
            }
        }
    }, "PizzaAndBirdPalette").apply { isDaemon = true; start() }


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

    // 살아있는 풀 리그 (§ 아래 buildGrassRig) ------------------------------------
    // 주의: 아래 init 블록에서 buildGrassRig()가 이 배열들을 채우므로,
    //       반드시 init 보다 *앞쪽*에 선언해야 한다 (Kotlin은 선언 순서대로 실행).
    private lateinit var grassPoses: Array<Array<Array<Bitmap>>>   // [종][lean][curl]
    val grassOx = IntArray(GRASS_KINDS)                            // 그릴 때 빼는 X
    val grassOy = IntArray(GRASS_KINDS)                            // 그릴 때 빼는 Y

    // 아이콘 ------------------------------------------------------------------
    lateinit var pizzaIcon: Bitmap
    lateinit var pizzaIconBig: Bitmap
    lateinit var pizzaArts: Array<Bitmap>       // Pizzas.ALL 순서(id) — 피자 종류별 아이콘
    lateinit var cloverIcon: Bitmap
    lateinit var cameraIcon: Bitmap
    lateinit var houseIcon: Bitmap
    lateinit var sunIcon: Bitmap
    lateinit var moonIcon: Bitmap
    lateinit var decorArt: Array<Bitmap>        // Decors.ALL 순서

    // 카메라 장비 아트 캐시 (init 보다 먼저 만들어져야 한다)
    private val camIconCache = HashMap<CamLook, Bitmap>()
    private val camProfileCache = HashMap<CamLook, Bitmap>()
    private val camHeldCache = SpriteLru<HeldKey, Bitmap>(24)

    private data class HeldKey(val look: CamLook, val dir: Int, val raised: Boolean, val px: Int)

    private val camOutline = c(0xFF191920)
    private val camGlass = c(0xFF3E6B8C)
    private val camGlassHi = c(0xFFBFE4F5)
    private val camStrap = c(0xFF7A4A2B)
    private val camSkin = c(0xFFFFD9B0)
    private val camGold = c(0xFFF2D06B)
    private val camWhiteLens = c(0xFFE8E4D8)

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
        val sideL: Array<Bitmap>,
        /** 프레임 상단 여백(px) — 앉은 자세처럼 머리가 넘칠 때 그릴 y 에서 이만큼 뺀다 */
        val topPad: Int = 0
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
        val idle: Clip, val walk: Clip, val run: Clip, val sneak: Clip, val aim: Clip,
        val sit: Clip,
        val punch: Clip
    ) {
        fun clip(anim: Anim): Clip = when (anim) {
            Anim.IDLE -> idle
            Anim.WALK -> walk
            Anim.RUN -> run
            Anim.SNEAK -> sneak
            Anim.AIM -> aim
            Anim.SIT -> sit
        }

        // 기존 코드 호환 (아바타 썸네일 등) — 서 있는 자세
        val down: Array<Bitmap> get() = idle.down
        val up: Array<Bitmap> get() = idle.up
        val side: Array<Bitmap> get() = idle.side
        val sideL: Array<Bitmap> get() = idle.sideL
    }

    /**
     * 생성 비용이 큰 스프라이트 세트를 담아 두는 LRU 캐시.
     *
     * HD 캐릭터 한 세트는 프레임 176장(약 6MB)이라 무한정 쌓아 두면 안 된다.
     * 가장 오래 안 쓴 것부터 버린다 — 동시에 필요한 세트는 언제나 1~2개뿐이다
     * (플레이 중 1개, 캐릭터 선택 화면에서 남녀 2개).
     */
    private class SpriteLru<K, V>(private val max: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
    }

    private val playerCache = SpriteLru<Int, PlayerSet>(4)

    // -----------------------------------------------------------------------
    // 백그라운드 스프라이트 준비(warm-up)
    //
    // HD 프레임 한 장은 도트보다 5~10배 비싸다(캐릭터 한 벌 ≈ 0.2초, 만세 8장 ≈ 25ms).
    // 그 벌을 **처음 필요해진 순간** 만들면 화면이 그대로 멈춘다 — 특히 레벨업은
    // 장비 등급이 바뀌는 순간이라 정확히 그 화면에서 새 세트를 만들게 된다.
    // 그래서 "곧 필요해질" 세트(다음 등급·현재 자전거)를 미리 전용 스레드에서 만들어 둔다.
    //
    // 캐시는 [synchronized] 로만 만지고, 무거운 생성은 **잠금 밖에서** 한다 —
    // 준비 스레드가 0.2초 동안 그리는 동안 게임 스레드는 이미 있는 세트를 계속 쓴다.
    // -----------------------------------------------------------------------

    private val warmQueue = LinkedBlockingQueue<() -> Unit>()

    private val warmThread = Thread({
        while (true) {
            val job = try {
                warmQueue.take()
            } catch (_: InterruptedException) {
                continue
            }
            try {
                job()
            } catch (_: Exception) {
                // 준비 실패는 치명적이지 않다 — 필요해지면 그때 만든다
            }
        }
    }, "PizzaAndBirdSpriteWarm").apply { isDaemon = true; start() }

    /** 곧 쓸 스프라이트를 백그라운드에서 미리 만들어 둔다(이미 밀려 있으면 무시) */
    fun warmSprites(job: () -> Unit) {
        if (warmQueue.size >= 4) return
        warmQueue.offer(job)
    }

    /** 지금 준비 큐가 비어 있는가 (프리뷰/테스트에서 "다 됐는지" 확인용) */
    fun warmIdle(): Boolean = warmQueue.isEmpty()

    private fun buildClip(
        look: CharacterArt.Look, frames: Int, px: Int, topPad: Int = 0,
        pose: (Float) -> CharacterArt.Pose
    ): Clip {
        val down = Array(frames) { CharacterArt.render(CharacterArt.FRONT, pose(it / frames.toFloat()), look, px, topPad) }
        val up = Array(frames) { CharacterArt.render(CharacterArt.BACK, pose(it / frames.toFloat()), look, px, topPad) }
        val side = Array(frames) { CharacterArt.render(CharacterArt.SIDE, pose(it / frames.toFloat()), look, px, topPad) }
        val sideL = Array(frames) { flipH(side[it]) }
        return Clip(down, up, side, sideL, topPad)
    }

    /**
     * 성별 + 레벨 등급으로 동작 세트 얻기 (첫 사용 시 생성).
     *
     * @param hd true = HD([CHARACTER_PX]) — 월드 슈퍼샘플 2배 이상 · UI 확대용.
     *   false = 원본 32px 도트 — 화질 1×(저사양·저해상 기기)에서 예전 느낌 그대로.
     */
    fun playerSet(gender: String, tier: Int, hd: Boolean = true): PlayerSet {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = (if (gender == "female") 1 else 0) * 64 + t + (if (hd) 0 else 32)
        synchronized(playerCache) { playerCache[key]?.let { return it } }
        val lk = look(gender, t)
        val px = if (hd) CHARACTER_PX else CharacterArt.SIZE
        val set = PlayerSet(
            idle = buildClip(lk, Anim.IDLE.frames, px) { CharacterArt.idlePose(it) },
            walk = buildClip(lk, Anim.WALK.frames, px) { CharacterArt.walkPose(it, CharacterArt.WALK) },
            run = buildClip(lk, Anim.RUN.frames, px) { CharacterArt.walkPose(it, CharacterArt.RUN) },
            sneak = buildClip(lk, Anim.SNEAK.frames, px) { CharacterArt.walkPose(it, CharacterArt.SNEAK) },
            aim = buildClip(lk, Anim.AIM.frames, px) { CharacterArt.aimPose(it) },
            sit = buildClip(lk, Anim.SIT.frames, px, CharacterArt.SIT_TOPPAD) {
                CharacterArt.sitPose(it / Anim.SIT.frames.toFloat())
            },
            punch = buildClip(lk, 4, px) { CharacterArt.punchPose(it) }
        )
        synchronized(playerCache) {
            playerCache[key]?.let { return it }      // 그 사이 다른 스레드가 만들었으면 그걸 쓴다
            playerCache[key] = set
        }
        return set
    }

    /** 레벨 등급으로 (남자) */
    fun playerSet(tier: Int): PlayerSet = playerSet("male", tier)

    private val cheerCache = SpriteLru<Int, Array<Bitmap>>(2)

    /**
     * 레벨업 축하 동작 (정면 8프레임 · [CHEER_PX] = 256px).
     *
     * 이 화면은 캐릭터를 화면 가득 띄우므로 여기만 아주 크게 그린다.
     * 프레임 8장뿐이라 메모리 부담도 작다(장당 256KB).
     */
    fun cheerFrames(gender: String, tier: Int): Array<Bitmap> {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = (if (gender == "female") 1 else 0) * 64 + t
        synchronized(cheerCache) { cheerCache[key]?.let { return it } }
        val lk = look(gender, t)
        val frames = Array(8) {
            CharacterArt.render(CharacterArt.FRONT, CharacterArt.cheerPose(it / 8f), lk, CHEER_PX)
        }
        synchronized(cheerCache) {
            cheerCache[key]?.let { return it }
            cheerCache[key] = frames
        }
        return frames
    }

    private val avatarCache = SpriteLru<Int, Array<Bitmap>>(4)

    /**
     * 캐릭터 선택 카드용 — 정면 서 있는 동작 12프레임만 [AVATAR_PX] 로 그려 둔다.
     *
     * (세트 전체는 프레임 176장이라 카드 두 장 때문에 만들기엔 너무 무겁다.
     *  정면 12프레임이면 한 벌 1.5MB 로 끝나고 카드도 그대로 움직인다)
     */
    fun playerAvatarFrames(gender: String, tier: Int): Array<Bitmap> {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = (if (gender == "female") 1 else 0) * 64 + t
        synchronized(avatarCache) { avatarCache[key]?.let { return it } }
        val lk = look(gender, t)
        val frames = Array(Anim.IDLE.frames) {
            try {
                CharacterArt.render(CharacterArt.FRONT, CharacterArt.idlePose(it / Anim.IDLE.frames.toFloat()), lk, AVATAR_PX)
            } catch (_: Exception) {
                // 못 만들면 서 있는 세트의 같은 프레임으로 대체한다(화면이 비지 않게)
                playerSet(gender, t).idle.down[it % playerSet(gender, t).idle.down.size]
            }
        }
        synchronized(avatarCache) {
            avatarCache[key]?.let { return it }
            avatarCache[key] = frames
        }
        return frames
    }

    /** 애니메이션 시간 -> 스프라이트 */
    fun playerBitmap(gender: String, tier: Int, anim: Anim, dir: Dir, animT: Float): Bitmap {
        val clip = playerSet(gender, tier).clip(anim)
        return clip.frame(dir, (animT / anim.frameTime).toInt())
    }

    /**
     * 캐릭터·자전거처럼 **32 도트 격자** 위에 그려진 스프라이트를 월드에 그린다.
     *
     * 스프라이트가 원본 도트(32px)든 HD([CHARACTER_PX])든 화면에서 차지하는 크기는
     * 언제나 똑같이 32 도트 — 즉 HD 그림은 살짝 줄여서 붙기 때문에 픽셀 크기는 그대로고
     * 옷 주름·머리 윤기·눈 반짝임 같은 결만 새로 보인다.
     * (레벨업 축하 화면처럼 크게 띄우는 곳에서 "확대한 도트"가 아니라 "진짜 그림"이 된다)
     *
     * @param dotScale 월드 비트맵에서 **도트 하나가 차지하는 기기 픽셀 수** (= [Game.worldScale]).
     */
    fun drawPlayer(
        c: Canvas, bmp: Bitmap, x: Float, y: Float,
        dotScale: Float, alpha: Int = 255
    ) {
        val k = dotScale * CharacterArt.SIZE / bmp.width
        // 정수배 확대만 픽셀 느낌(최근접)으로, 그 외(축소·어중간한 배율)는 보간으로 그린다.
        // HD 스프라이트는 축소되므로 필터가 켜져야 디테일이 살아남는다.
        val p = if (isIntegerScale(k)) pxPaint else sprPaint
        if (alpha != 255) {
            val keep = p.alpha
            p.alpha = alpha
            drawScaled(c, bmp, x, y, k, p)
            p.alpha = keep
        } else {
            drawScaled(c, bmp, x, y, k, p)
        }
    }

    private fun isIntegerScale(k: Float): Boolean =
        k > 0.999f && kotlin.math.abs(k - k.toInt()) < 0.02f

    /** 확대(또는 축소)해서 그린다 — 크기가 1:1이면 비트맵을 그대로 붙인다 */
    private fun drawScaled(c: Canvas, bmp: Bitmap, x: Float, y: Float, k: Float, p: Paint) {
        if (k == 1f) {
            c.drawBitmap(bmp, x, y, p)
        } else {
            c.drawBitmap(
                bmp, null,
                RectF(x, y, x + bmp.width * k, y + bmp.height * k), p
            )
        }
    }

    /** UI(가방 · 레벨업 · 캐릭터 선택)에서 캐릭터를 지정한 사각형에 꽉 차게 그린다 */
    fun drawPlayerIn(c: Canvas, bmp: Bitmap, dst: RectF) {
        c.drawBitmap(bmp, null, dst, sprPaint)
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

    /**
     * 자전거 세트 캐시 — 프레임 한 장이 HD(96px)라 40세트면 46MB까지 불어난다.
     * 자전거 상점은 한 페이지에 5대를 보여 주고 월드는 한 대만 쓰므로 12세트면 충분하다.
     */
    private val bikeCache = SpriteLru<String, BikeSet>(12)

    /**
     * 자전거 세트 (성별·레벨 등급·자전거 스타일(모델/도색/부속품)별 프레임 생성·캐시)
     * @param hd [playerSet] 과 같은 규칙 — 라이더가 캐릭터와 같은 화질로 그려진다.
     */
    fun bikeSet(gender: String, tier: Int, style: BikeStyle, hd: Boolean = true): BikeSet {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = "${if (gender == "female") 1 else 0}|$t|${style.cacheKey}|$hd"
        synchronized(bikeCache) { bikeCache[key]?.let { return it } }
        val lk = look(gender, t)
        val n = BIKE_FRAMES
        val px = if (hd) BIKE_PX else CharacterArt.SIZE
        val side = Array(n) { CharacterArt.renderBike(CharacterArt.SIDE, it / n.toFloat(), lk, style, px) }
        val set = BikeSet(
            down = Array(n) { CharacterArt.renderBike(CharacterArt.FRONT, it / n.toFloat(), lk, style, px) },
            up = Array(n) { CharacterArt.renderBike(CharacterArt.BACK, it / n.toFloat(), lk, style, px) },
            side = side,
            sideL = Array(n) { flipH(side[it]) }
        )
        synchronized(bikeCache) {
            bikeCache[key]?.let { return it }
            bikeCache[key] = set
        }
        return set
    }

    private val bikePreviewCache = SpriteLru<String, Bitmap>(16)

    /**
     * 자전거 상점 썸네일 — **옆모습 첫 프레임 한 장만** HD로 그린다.
     *
     * 세트를 통째로 만들면 32프레임이라 상점 한 쪽(5대)을 열 때 0.2초가 걸린다.
     * 썸네일은 어차피 [Dir] 한 방향만 보여 주므로 한 장이면 충분하다.
     */
    fun bikePreviewFrame(gender: String, tier: Int, style: BikeStyle): Bitmap {
        val t = tier.coerceIn(0, gearTiers.size - 1)
        val key = "${if (gender == "female") 1 else 0}|$t|${style.cacheKey}"
        synchronized(bikePreviewCache) { bikePreviewCache[key]?.let { return it } }
        val bmp = CharacterArt.renderBike(CharacterArt.SIDE, 0f, look(gender, t), style, BIKE_PX)
        synchronized(bikePreviewCache) { bikePreviewCache[key] = bmp }
        return bmp
    }

    /** 자전거 스프라이트 (pedalPhase: 페달 위상) */
    fun bikeBitmap(
        gender: String, tier: Int, dir: Dir, pedalPhase: Float, style: BikeStyle, hd: Boolean = true
    ): Bitmap {
        val set = bikeSet(gender, tier, style, hd)
        val f = (pedalPhase * set.count).toInt()
        return set.frame(dir, f)
    }

    // 기존 코드 호환 접근자
    val playerTiers: Array<PlayerSet> get() = Array(gearTiers.size) { playerSet("male", it) }
    val playerDown: Array<Bitmap> get() = playerSet("male", 0, false).down
    val playerUp: Array<Bitmap> get() = playerSet("male", 0, false).up
    // 호환 접근자는 원본 32px 도트를 돌려준다(HD는 playerSet(gender, tier, hd=true) 로).
    val playerSide: Array<Bitmap> get() = playerSet("male", 0, false).side
    val playerSideL: Array<Bitmap> get() = playerSet("male", 0, false).sideL
    val femaleDown: Array<Bitmap> get() = playerSet("female", 0, false).down
    val femaleUp: Array<Bitmap> get() = playerSet("female", 0, false).up
    val femaleSide: Array<Bitmap> get() = playerSet("female", 0, false).side
    val femaleSideL: Array<Bitmap> get() = playerSet("female", 0, false).sideL
    /** 기본 자전거 외형 (구 코드 호환 접근자용) */
    private val defaultBikeStyle = BikeStyle(
        "basic", BikeColors.frame(0), BikeColors.tire(0), BikeColors.saddle(0),
        basket = false, rack = false, light = false, streamers = false, bell = false
    )

    val bikeDown: Bitmap get() = bikeSet("male", 0, defaultBikeStyle, false).down[0]
    val bikeUp: Bitmap get() = bikeSet("male", 0, defaultBikeStyle, false).up[0]
    val bikeSide: Bitmap get() = bikeSet("male", 0, defaultBikeStyle, false).side[0]
    val bikeSideL: Bitmap get() = bikeSet("male", 0, defaultBikeStyle, false).sideL[0]

    // -----------------------------------------------------------------------
    // NPC — 사람마다 다른 옷차림 + 성격이 드러나는 대기 동작 (12프레임)
    //   누구인지(이름·옷·사는 지역)는 NpcRoster.kt, 자리는 MapBuilder.placeCast 가 정한다.
    // -----------------------------------------------------------------------

    /** 사람 겉모습([NpcLook]) -> 렌더용 Look. 체형·헤어·모자·수염·소품까지 그대로 간다. */
    private fun npcLook(kind: NpcKind, look: NpcLook): CharacterArt.Look {
        val (skin, skin2) = when (look.skin) {
            CharacterArt.SKIN_FAIR -> c(0xFFFFE3C4) to c(0xFFF0C39E)
            CharacterArt.SKIN_TAN -> c(0xFFF5B885) to c(0xFFE09A6E)
            CharacterArt.SKIN_DEEP -> c(0xFFD99A6B) to c(0xFFB87A52)
            else -> c(0xFFFFD9B0) to c(0xFFE8B88C)
        }
        val pal = CharacterArt.Pal(
            hair = c(look.hair.toLong()), hair2 = shade(c(look.hair.toLong()), 0.75f),
            skin = skin, skin2 = skin2,
            top = c(look.top.toLong()), top2 = c(look.top2.toLong()),
            pants = c(look.pants.toLong()), pants2 = shade(c(look.pants.toLong()), 0.75f),
            shoe = c(0xFF3A3A44), line = c(0xFF33241C),
            pack = c(look.pack.toLong()), pack2 = shade(c(look.pack.toLong()), 0.75f),
            eye = c(0xFF2E2620), blush = c(0xFFF2A58C)
        )
        // 새 모자 파츠를 쓰면 탐조가 장비 모자는 비운다 (둘 다 그리면 겹친다).
        // 모자 색은 명시색 → 옛 cap 색 → 옷색 순으로 정해진다.
        val useHat = look.hat != CharacterArt.HAT_NONE
        val gear = if (!useHat && look.cap == null && look.vest == null && look.scarf == null) null
        else CharacterArt.Gear(
            cap = if (useHat) null else look.cap,
            capDark = shade(look.cap ?: 0, 0.72f),
            vest = look.vest, vestDark = shade(look.vest ?: 0, 0.75f),
            scarf = look.scarf,
            brim = look.cap != null
        )
        return CharacterArt.Look(
            pal,
            gear = gear,
            glasses = look.glasses,
            apron = look.apron,
            cane = look.cane,
            small = look.small || kind == NpcKind.KID,
            longHair = look.longHair,
            body = look.body,
            hairStyle = look.hairStyle,
            beard = look.beard,
            hat = look.hat,
            hatColor = look.hatColor ?: look.cap ?: 0,
            bottom = look.bottom,
            prop = look.prop,
            propColor = look.propColor ?: 0,
            wrinkles = look.wrinkles || kind == NpcKind.ELDER,
            freckles = look.freckles,
            keepsake = when (kind) {
                NpcKind.PROFESSOR -> "feather"
                NpcKind.SHOP -> "clover"
                NpcKind.ELDER -> "moon"
                NpcKind.KID -> "rain"
                else -> if (look.scarf != null) "moon" else "clover"
            }
        )
    }

    private fun npcArtKind(kind: NpcKind): Int = when (kind) {
        NpcKind.PROFESSOR -> CharacterArt.NPC_PROFESSOR
        NpcKind.SHOP -> CharacterArt.NPC_SHOP
        NpcKind.VILLAGER -> CharacterArt.NPC_VILLAGER
        NpcKind.KID -> CharacterArt.NPC_KID
        NpcKind.ELDER -> CharacterArt.NPC_ELDER
    }

    /**
     * 사람별 대기 애니메이션 캐시.
     *
     * 지역마다 다른 사람이 서 있으므로 키는 "바디"가 아니라 **사람 id** 다.
     * 12프레임 × 32×32 ≈ 49KB 라서, 오래 안 본 사람은 밀어내는 LRU(24명)로 묶어 둔다.
     */
    private val npcCache = LruCache<String, Array<Bitmap>>(24)

    /**
     * HD NPC 캐시 — 한 사람이 12프레임 × 36KB ≈ 432KB라 24명을 다 들고 있으면 10MB다.
     * 지금 서 있는 지역의 사람(보통 2~4명)만 들고 있으면 충분하므로 8명으로 묶는다.
     */
    private val npcHdCache = SpriteLru<String, Array<Bitmap>>(8)

    /**
     * 소품·바디에서 소동작을 정한다 — 같은 VILLAGER 라도 쌍안경 든 관찰원은
     * 두리번거리고, 붓 든 화가는 붓질하고, 찻잔 든 주인은 홀짝인다.
     */
    private fun flavorFor(person: NpcPerson): Int = when (person.look.prop) {
        CharacterArt.PROP_BRUSH -> CharacterArt.FLAVOR_PAINT
        CharacterArt.PROP_CUP -> CharacterArt.FLAVOR_SIP
        CharacterArt.PROP_BINOCS -> CharacterArt.FLAVOR_SCAN
        CharacterArt.PROP_BOOK -> CharacterArt.FLAVOR_READ
        CharacterArt.PROP_ROD, CharacterArt.PROP_NET, CharacterArt.PROP_PADDLE ->
            CharacterArt.FLAVOR_SWAY
        else -> when (person.kind) {
            NpcKind.KID -> CharacterArt.FLAVOR_BOUNCE
            NpcKind.ELDER -> CharacterArt.FLAVOR_NOD
            else -> CharacterArt.FLAVOR_NONE
        }
    }

    /**
     * 사람 한 명의 대기 애니메이션 프레임 (12장, 약 2.4초 루프).
     * @param hd 플레이어와 같은 화질([CHARACTER_PX])로 그릴지 — 월드가 2배 이상 슈퍼샘플일 때 true.
     */
    fun npcFrames(person: NpcPerson, hd: Boolean = false): Array<Bitmap> {
        synchronized(npcHdCache) {
            if (hd) npcHdCache[person.id]?.let { return it } else npcCache.get(person.id)?.let { return it }
        }
        val lk = npcLook(person.kind, person.look)
        val art = npcArtKind(person.kind)
        val flavor = flavorFor(person)
        // 사람마다 박자가 어긋나게 — 옆에 서 있어도 숨결·깜빡임이 겹치지 않는다
        val seed = ((person.id.hashCode() and 0x7FFFFFFF) % 1000) / 1000f
        val px = if (hd) CHARACTER_PX else CharacterArt.SIZE
        val frames = Array(NPC_FRAMES) {
            CharacterArt.render(
                CharacterArt.FRONT,
                CharacterArt.npcPose(art, it / NPC_FRAMES.toFloat(), flavor, seed), lk, px
            )
        }
        if (hd) synchronized(npcHdCache) { npcHdCache[person.id] = frames }
        else npcCache.put(person.id, frames)
        return frames
    }

    /** 바디만 알고 있을 때(미리보기 도구·구 코드) — 그 바디의 대표 인물로 그린다 */
    fun npcFrames(kind: NpcKind, hd: Boolean = false): Array<Bitmap> =
        npcFrames(NpcRoster.representative(kind), hd)

    /** 시간 -> NPC 스프라이트 (offset 으로 사람마다 위상을 다르게) */
    fun npcBitmap(person: NpcPerson, time: Float, offset: Float = 0f, hd: Boolean = false): Bitmap {
        val frames = npcFrames(person, hd)
        val i = (((time + offset) / NPC_FRAME_TIME).toInt() % frames.size + frames.size) % frames.size
        return frames[i]
    }

    /** 바디만 알고 있을 때(미리보기 도구·구 코드) */
    fun npcBitmap(kind: NpcKind, time: Float, offset: Float = 0f, hd: Boolean = false): Bitmap {
        val frames = npcFrames(kind, hd)
        val i = (((time + offset) / NPC_FRAME_TIME).toInt() % frames.size + frames.size) % frames.size
        return frames[i]
    }

    /** 랜드마크 안내인 — 테마마다 다른 얼굴 ([NpcRoster.docentFor]) */
    fun docentBitmap(theme: LandmarkTheme, time: Float, hd: Boolean = false): Bitmap =
        npcBitmap(NpcRoster.docentFor(theme), time, 1.3f, hd)

    val npcProfessor: Bitmap get() = npcFrames(NpcRoster.professor)[0]
    val npcShop: Bitmap get() = npcFrames(NpcRoster.shopkeeper)[0]
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

    /** 13종 체형 도트맵 — 스프라이트는 전부 왼쪽을 바라본다. h=머리, a=포인트 컬러. */
    private val birdTemplates: Array<List<String>> by lazy {
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

        arrayOf(
            songbird, waterfowl, wader, raptor, owl, shorebird, seabird,
            woodpecker, dove, kingfisher, pitta, gamebird, aerial
        )
    }

    /**
     * 새 한 종의 기본 스프라이트 생성.
     * 실제 사진에서 뽑은 색 + 종별 BirdArt 식별색을 섞은 고정밀 4방향 리그를 사용한다.
     */
    private fun buildBird(d: BirdDef): Bitmap =
        DetailedBirdRenderer.render(d, BirdFacing.LEFT, BirdPose.PERCHED, birdReferencePalette(d))

    private fun buildBirdPose(d: BirdDef, facing: BirdFacing, pose: BirdPose): Bitmap =
        DetailedBirdRenderer.render(d, facing, pose, birdReferencePalette(d))

    /** 그리기 경로: 사진이 아직 준비되지 않았으면 기본색을 쓰고 백그라운드에 요청한다. */
    private fun birdReferencePalette(d: BirdDef): BirdRenderPalette {
        birdReferencePalettes[d.id]?.let { return it }
        prefetchBirdPalette(d.id)
        return BirdRenderPalette(d.art.body, d.art.belly, d.art.wing, d.art.head,
            d.art.accent, d.art.beak, d.art.leg)
    }

    /**
     * 로더(또는 부팅 스레드) 전용: assets/birds/{번호}.jpg의 중앙 피사체 색 군집을 읽는다.
     * 배경색 오염을 줄이기 위해 BirdArt 기준색과 가까운 상위 군집을 고르고 34%만 혼합한다.
     */
    private fun computeBirdReferencePalette(d: BirdDef): BirdRenderPalette {
        birdReferencePalettes[d.id]?.let { return it }
        val bases = intArrayOf(d.art.body, d.art.belly, d.art.wing, d.art.head, d.art.accent)
        val counts = HashMap<Int, Int>()
        if (d.birdNum > 0) {
            try {
                val opt = BitmapFactory.Options().apply {
                    inSampleSize = 8
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val small = context.assets.open("birds/${d.birdNum}.jpg").use { BitmapFactory.decodeStream(it, null, opt) }
                if (small != null) {
                    val cx = (small.width - 1) / 2f
                    val cy = (small.height - 1) / 2f
                    val rx = (small.width * 0.43f).coerceAtLeast(1f)
                    val ry = (small.height * 0.43f).coerceAtLeast(1f)
                    for (y in 0 until small.height) for (x in 0 until small.width) {
                        val dx = (x - cx) / rx
                        val dy = (y - cy) / ry
                        if (dx * dx + dy * dy > 1f) continue
                        val col = small.getPixel(x, y)
                        val r = Color.red(col); val g = Color.green(col); val b = Color.blue(col)
                        // 5bit RGB 군집. 과노출/완전 암부도 흰새·검은새에 필요하므로 버리지 않는다.
                        val q = Color.rgb(r and 0xF8, g and 0xF8, b and 0xF8)
                        val centerWeight = if (dx * dx + dy * dy < 0.35f) 3 else 1
                        counts[q] = (counts[q] ?: 0) + centerWeight
                    }
                    small.recycle()
                }
            } catch (_: Exception) { }
        }
        val ranked = counts.entries.sortedByDescending { it.value }.take(28)
        val maxCount = ranked.firstOrNull()?.value?.coerceAtLeast(1) ?: 1

        fun distance(a: Int, b: Int): Int {
            val dr = Color.red(a) - Color.red(b)
            val dg = Color.green(a) - Color.green(b)
            val db = Color.blue(a) - Color.blue(b)
            return dr * dr * 3 + dg * dg * 4 + db * db * 2
        }
        fun blend(base: Int): Int {
            if (ranked.isEmpty()) return base
            val picked = ranked.minByOrNull { e ->
                // 자주 나온 색은 최대 약 65 RGB-distance만큼 우대한다.
                distance(base, e.key) - e.value * 4200 / maxCount
            }!!.key
            fun ch(a: Int, b: Int) = (a * 0.66f + b * 0.34f).roundToInt().coerceIn(0, 255)
            return Color.rgb(ch(Color.red(base), Color.red(picked)), ch(Color.green(base), Color.green(picked)), ch(Color.blue(base), Color.blue(picked)))
        }

        val mapped = bases.map(::blend)
        return BirdRenderPalette(
            body = mapped[0], belly = mapped[1], wing = mapped[2],
            head = mapped[3], accent = mapped[4], beak = d.art.beak, leg = d.art.leg
        ).also { birdReferencePalettes[d.id] = it }
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

    // -----------------------------------------------------------------------
    // 살아있는 풀 — 풀잎 리그 (Living Grass RIG)
    //
    // 듀오 애니메이션의 "내부 구조"를 그대로 픽셀에 적용한다.
    //  · RIG  : 풀잎 하나 = ROOT → MID → TIP 3단 뼈대. 각 단은 *정수 픽셀 오프셋*으로
    //            누적되기 때문에 바람에 휜다고 앤티앨리어싱이 생기지 않는다.
    //            (float로 회전시킨 뒤 스케일링하면 픽셀이 뭉개진다 — 이게 함정)
    //  · POSE : 가능한 자세(lean × curl)를 미리 구워 bitmap 사전으로 들고 있다가
    //            상태 머신이 인덱스만 고른다 = Lottie/Rive의 "작성된 상태 + 트윈".
    //  · 내부 디테일: 3톤 명암(밝은 면 / 중간 / 그림자) + 그림자쪽 1px 잎맥 점선 +
    //            가운데 접힘 하이라이트 + 끝 2행 팁 하이라이트 + 기저부 접지 그림자.
    // -----------------------------------------------------------------------

    private class GrassKind(
        val h: Int, val bh: Int, val oy: Int,        // 높이 / 비트맵 높이 / 밑동 행
        val wBase: Int, val wMid: Int,               // 기저부·중간부 두께
        val deep: Int, val body: Int, val lit: Int, val hi: Int, val vein: Int,
        val restLean: Int, val restCurl: Int,         // 이 종 고유의 기본 자세
        val seed: Int,                                // 이삭 색 (0 = 없음)
        val fold: Boolean                             // 접힘 하이라이트 여부
    )

    private fun grassKinds() = listOf(
        // 0 새순 — 낮고 촘촘한 싹. 초원 대부분을 담당
        GrassKind(
            8, 12, 10, 2, 2,
            c(0xFF2E5A2A), c(0xFF4E8A3C), c(0xFF7FBE5E), c(0xFFA8DA7E), c(0xFF3D7033),
            0, 0, 0, false
        ),
        // 1 기본 풀잎 — 라운드 2px 잎
        GrassKind(
            12, 16, 14, 2, 2,
            c(0xFF35632F), c(0xFF4E8A3C), c(0xFF6FAE57), c(0xFF8CC46C), c(0xFF447C34),
            0, 0, 0, true
        ),
        // 2 긴 풀 — 3px 두께, 풀숲(TALLGRASS)의 주역
        GrassKind(
            17, 21, 19, 3, 2,
            c(0xFF3A6B33), c(0xFF5B9A45), c(0xFF8CC46C), c(0xFFB7E08C), c(0xFF4C8539),
            0, 0, 0, false
        ),
        // 3 마른 풀 — 올리브 톤, 기본 자세가 이미 오른쪽으로 눕는다
        GrassKind(
            14, 18, 16, 3, 2,
            c(0xFF4A5A2A), c(0xFF7E9440), c(0xFFA8BC5E), c(0xFFC9D88A), c(0xFF6A7F34),
            2, 1, 0, true
        ),
        // 4 이삭/갈대 — 끝에 이삭穗. 갈대습지(REED)
        GrassKind(
            16, 24, 22, 2, 2,
            c(0xFF3E6B4A), c(0xFF5F9A5E), c(0xFF8FCB8C), c(0xFFC3E6B0), c(0xFF4C7F4C),
            -1, 0, c(0xFFD9C58A), false
        )
    )

    /**
     * 풀잎 한 자세를 픽셀로 새긴다.
     * 뿌리(0,0)는 절대 움직이지 않고, 끝점만 (lean, curl)만큼 간다.
     * 세로줄을 1px씩 내려가며 x를 정수로 누적해서 그리기 때문에
     * 어떤 각도에서도 결과가 "계단식 픽셀 선"으로 남아 깔끔하다.
     */
    private fun grassPose(k: GrassKind, lean: Int, curl: Int): Bitmap {
        val bmp = Bitmap.createBitmap(GRASS_W, k.bh, Bitmap.Config.ARGB_8888)
        val h = k.h
        val tipX = (lean + k.restLean) * GRASS_LEAN_UNIT
        val arc = (curl + k.restCurl) * GRASS_CURL_UNIT
        // 이차 베지어: 첫 조점은 거의 수직(뿌리가 박혀 있음), 둘째 조점이 휨을 만든다
        val c1 = tipX * 0.12f
        val c2 = tipX * 0.60f + arc
        var tipPx = GRASS_CX

        for (i in 0..h) {
            val t = i / h.toFloat()
            val u = 1f - t
            val x = 3f * u * u * t * c1 + 3f * u * t * t * c2 + t * t * t * tipX
            val px = GRASS_CX + x.roundToInt()
            if (i == h) tipPx = px
            val y = k.oy - i
            if (y < 0) break

            // 잎폭 테이퍼 — 풀답게 끝 40%를 가늘게 (1px로 긴 첨부)
            val w = when {
                t < 0.14f -> k.wBase
                t < 0.58f -> k.wMid
                else -> 1
            }
            val tipLight = t > 0.84f     // 끝 2~3행: 가장 밝은 하이라이트
            val rootDark = t < 0.11f     // 기저부: 접지감
            val veinOn = w >= 3 && t > 0.14f && t < 0.55f && (i % 3 == 0)   // 내부 잎맥 점선
            val foldOn = k.fold && i in (h * 0.38f).toInt()..(h * 0.38f).toInt() + 1

            for (j in 0 until w) {
                val col = px - ((w - 1) / 2) + j
                val isLeft = j == 0
                val isRight = j == w - 1
                var color = when {
                    tipLight -> k.hi
                    w == 1 -> k.body
                    isRight -> k.deep
                    isLeft -> if (foldOn) k.hi else k.lit
                    else -> if (veinOn) k.vein else k.body
                }
                if (rootDark) color = shade(color, 0.80f)
                if (col in 0 until GRASS_W) bmp.setPixel(col, y, color)
            }
        }

        // 이삭穗 — 줄기 끝 위로 좁아지는穗
        if (k.seed != 0) {
            for (j in 1..4) {
                val y2 = k.oy - h - j
                if (y2 < 0) break
                val drift = (tipX * 0.07f * j).roundToInt()
                val halfW = if (j <= 2) 1 else 0
                for (dx in -halfW..halfW) {
                    val x2 = tipPx + drift + dx
                    if (x2 in 0 until GRASS_W) {
                        bmp.setPixel(x2, y2, if (dx == 0) k.seed else shade(k.seed, 0.76f))
                    }
                }
            }
        }
        return bmp
    }

    private fun buildGrassRig() {
        val kinds = grassKinds()
        grassPoses = Array(GRASS_KINDS) { ki ->
            val k = kinds[ki]
            grassOx[ki] = GRASS_CX
            grassOy[ki] = k.oy
            Array(GRASS_LEAN_MAX * 2 + 1) { li ->
                Array(GRASS_CURL_MAX * 2 + 1) { ci ->
                    grassPose(k, li - GRASS_LEAN_MAX, ci - GRASS_CURL_MAX)
                }
            }
        }
    }

    /** 상태 머신이 부르는 포즈 조회 (범위는 여기서 클램프) */
    fun grassPose(kind: Int, lean: Int, curl: Int): Bitmap {
        val k = if (kind in 0 until GRASS_KINDS) kind else 1
        val li = (lean + GRASS_LEAN_MAX).coerceIn(0, GRASS_LEAN_MAX * 2)
        val ci = (curl + GRASS_CURL_MAX).coerceIn(0, GRASS_CURL_MAX * 2)
        return grassPoses[k][li][ci]
    }

    // ── 바위 아트 키트 ──────────────────────────────────────────────────────
    // 게임의 빛은 왼쪽 위(9시)에서 온다. 모든 바위가 같은 조명 규칙을 쓰도록
    // 하이라이트는 좌상단, 어두운 테두리는 우하단에 둔다.
    // 32x32 타일에 **다양한 크기**로 그린다 — [PropLooks] 의 세 크기급과 1:1.

    // 암종 팔레트 — [그늘, 중간, 빛, 하이라이트]
    private val P_GRAN_D = c(0xFF4C5158)
    private val P_GRAN_M = c(0xFF8B9298)
    private val P_GRAN_L = c(0xFFC3C7C7)
    private val P_GRAN_H = c(0xFFEAE7DE)
    private val GRANITE = intArrayOf(P_GRAN_D, P_GRAN_M, P_GRAN_L, P_GRAN_H)

    private val P_BAS_D = c(0xFF1E2326)
    private val P_BAS_M = c(0xFF454D50)
    private val P_BAS_L = c(0xFF6E777A)
    private val P_BAS_H = c(0xFF9BA3A2)
    private val BASALT = intArrayOf(P_BAS_D, P_BAS_M, P_BAS_L, P_BAS_H)

    private val P_SED_D = c(0xFF4A555C)
    private val P_SED_M = c(0xFF7C8892)
    private val P_SED_L = c(0xFFA3AFB7)
    private val P_SED_H = c(0xFFC9D2D6)
    private val P_SED_A = c(0xFF5E8A4E)
    private val SEDIMENT = intArrayOf(P_SED_D, P_SED_M, P_SED_L, P_SED_H)

    private val P_LIM_D = c(0xFF6B6F63)
    private val P_LIM_M = c(0xFFA6AC9C)
    private val P_LIM_L = c(0xFFC9CCB7)
    private val P_LIM_H = c(0xFFEDEBDA)
    private val LIMESTONE = intArrayOf(P_LIM_D, P_LIM_M, P_LIM_L, P_LIM_H)

    private val P_SAN_D = c(0xFF6A5646)
    private val P_SAN_M = c(0xFFA5825F)
    private val P_SAN_L = c(0xFFD0AC7F)
    private val P_SAN_H = c(0xFFEBD2A6)
    private val SANDSTONE = intArrayOf(P_SAN_D, P_SAN_M, P_SAN_L, P_SAN_H)

    private val P_CON_D = c(0xFF5F656B)
    private val P_CON_M = c(0xFF8E959B)
    private val P_CON_L = c(0xFFB8BFC3)
    private val P_CON_H = c(0xFFD8DDDE)
    private val CONCRETE = intArrayOf(P_CON_D, P_CON_M, P_CON_L, P_CON_H)

    private val GABION = intArrayOf(c(0xFF3B4145), c(0xFF6E757A), c(0xFF949BA0), c(0xFFBCC2C4))
    private val STONES_IN_CAGE = intArrayOf(c(0xFF525A5F), c(0xFF8B9399), c(0xFFB2B9BC), c(0xFFD2D8DA))

    private val P_COB_D = c(0xFF525C63)
    private val P_COB_M = c(0xFF818C92)
    private val P_COB_L = c(0xFFA8B2B5)
    private val P_COB_H = c(0xFFD2DADA)
    private val COBBLE = intArrayOf(P_COB_D, P_COB_M, P_COB_L, P_COB_H)

    /**
     * 색에 불투명도를 곱한다 (0~255).
     * `c()`·`pal()` 은 Long 을 받지만 이건 Int 를 받는다 — `0xFFRRGGBB` 리터럴은 Long 이므로
     * 호출할 때 `.toInt()` 를 붙여야 한다 (Kotlin 은 8자리 16진수 리터럴을 Int 로 좁혀 주지 않는다).
     */
    private fun fade(col: Int, a: Int): Int = Color.argb(
        a.coerceIn(0, 255), Color.red(col), Color.green(col), Color.blue(col)
    )

    /** 0xFF…… 리터럴(Long) 편의 오버로드 */
    private fun fade(col: Long, a: Int): Int = fade(col.toInt(), a)

    /** [pts] = x,y,x,y,… 닫힌 다각형 경로. */
    private fun polyPath(pts: FloatArray): Path {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        return path
    }

    /** 다각형이 한 행에서 차지하는 가장 바깥 [x0, x1) 스팬. 없으면 null. */
    private fun rowSpan(pts: FloatArray, yc: Float): FloatArray? {
        val n = pts.size / 2
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        var hits = 0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val y0 = pts[i * 2 + 1]
            val y1 = pts[j * 2 + 1]
            if ((y0 <= yc && yc < y1) || (y1 <= yc && yc < y0)) {
                val t = (yc - y0) / (y1 - y0)
                val x = pts[i * 2] + t * (pts[j * 2] - pts[i * 2])
                if (x < lo) lo = x
                if (x > hi) hi = x
                hits++
            }
        }
        return if (hits >= 2) floatArrayOf(lo, hi) else null
    }

    /** [pts] 의 바운딩 박스. */
    private fun bounds(pts: FloatArray): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 2
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /**
     * 불규칙 타원 실루엣 정점 — 매개 변위로 모양을 다르게 뽑는다(결정적 시드).
     * [rough] 가 클수록 각진다.
     */
    private fun lump(
        r: Random, cx: Float, cy: Float, rx: Float, ry: Float, n: Int, rough: Float
    ): FloatArray {
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * 2f * Math.PI.toFloat()
            val k = 1f + (r.nextFloat() - 0.5f) * rough
            out[i * 2] = cx + kotlin.math.cos(a) * rx * k
            out[i * 2 + 1] = cy + kotlin.math.sin(a) * ry * k
        }
        return out
    }

    /** 바위 실루엣 안에만 사각형을 그린다. */
    private fun inRock(cv: Canvas, p: Paint, path: Path, x: Float, y: Float, w: Float, h: Float, col: Int) {
        cv.save(); cv.clipPath(path); px(cv, p, x, y, w, h, col); cv.restore()
    }

    /** 바위 실루엣 안에만 타원을 그린다. */
    private fun inRockOval(cv: Canvas, p: Paint, path: Path, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        cv.save(); cv.clipPath(path)
        p.color = col
        cv.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
        cv.restore()
    }

    /** 바위 실루엣 안에만 선을 긋는다(균열·층리 경계). */
    private fun inRockLine(
        cv: Canvas, p: Paint, path: Path,
        x0: Float, y0: Float, x1: Float, y1: Float, col: Int, w: Float
    ) {
        val steps = (maxOf(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2f).toInt() + 1
        cv.save(); cv.clipPath(path)
        p.color = col
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            cv.drawRect(x - w / 2f, y - w / 2f, x + w / 2f, y + w / 2f, p)
        }
        cv.restore()
    }

    /**
     * 바위 본체 하나 — 실루엣 + 윗빛/아랫그늘 디더 그라데이션 + 표면 알갱이 + 테두리.
     *
     * @param pal [그늘, 중간, 빛, 하이라이트] — 암종마다 다른 4단계 회색조
     * @param topBand 실루엣 위쪽 비율만큼 하이라이트까지 밝힌다 (윗면이 빛을 받는다)
     * @return 이후 이끼·조개·층리를 덧그릴 때 쓰는 클립 경로
     */
    private fun rockBody(
        cv: Canvas, p: Paint, r: Random, pts: FloatArray, pal: IntArray,
        bands: Int = 5, speck: Int = 0, speckCol: Int? = null,
        rim: Boolean = true, topBand: Float = 0.15f
    ): Path {
        val path = polyPath(pts)
        val bb = bounds(pts)
        val minX = bb[0]; val minY = bb[1]; val maxX = bb[2]; val maxY = bb[3]
        val h = (maxY - minY).coerceAtLeast(1f)
        val w = (maxX - minX).coerceAtLeast(1f)

        cv.save()
        cv.clipPath(path)
        // 행마다 위→아래 그라데이션. 밴드 경계는 짝수 행에서 한 칸 밀어 계단처럼 읽힌다.
        var y = minY
        var row = 0
        while (y < maxY + 1f) {
            val t = ((y + 0.5f) - minY) / h
            var col: Int
            var k = 0f
            if (t < topBand) {
                col = lerpColor(pal[3], pal[2], t / topBand)
            } else {
                k = (t - topBand) / (1f - topBand)
                col = lerpColor(pal[2], pal[0], k)
            }
            val frac = k * bands
            val bi = frac.toInt()
            if (frac - bi < 0.5f && bi < bands - 1 && row % 2 == 0) {
                col = lerpColor(pal[2], pal[0], (bi + 1) / bands.toFloat())
            }
            px(cv, p, minX - 1f, y, w + 2f, 1.05f, col)
            y += 1f
            row++
        }
        // 좌상단 능선 하이라이트 / 우하단 그늘
        p.color = fade(pal[3], 78)
        cv.drawOval(RectF(minX + w * 0.08f, minY + h * 0.08f, minX + w * 0.60f, minY + h * 0.36f), p)
        p.color = fade(pal[0], 120)
        cv.drawOval(RectF(maxX - w * 0.58f, maxY - h * 0.50f, maxX + w * 0.14f, maxY + h * 0.10f), p)
        if (speck > 0) {
            val sc = speckCol ?: shade(pal[0], 0.86f)
            repeat(speck) {
                val sx = minX + r.nextFloat() * w
                val sy = minY + r.nextFloat() * h
                val s = 1f + r.nextFloat() * 1.6f
                px(cv, p, sx, sy, s, s * 0.85f, sc)
            }
        }
        cv.restore()

        // 빛이 좌상단이므로 어두운 테두리는 오른쪽·아래에만 얹는다
        if (rim) {
            val dark = fade(pal[0], 150)
            var lastY = -1f
            var lastX0 = 0f
            var lastX1 = 0f
            var ry = minY
            while (ry <= maxY) {
                val sp = rowSpan(pts, ry + 0.5f)
                if (sp != null) {
                    px(cv, p, sp[1] - 1f, ry, 1f, 1.05f, dark)
                    lastY = ry; lastX0 = sp[0]; lastX1 = sp[1]
                }
                ry += 1f
            }
            if (lastY >= 0f) px(cv, p, lastX0, lastY, lastX1 - lastX0, 1.05f, dark)
        }
        return path
    }

    /** 소품용 접지 그림자 (바닥 타일은 GameMap 이 깔아 준다). */
    private fun rockShadow(cv: Canvas, p: Paint, cx: Float, cy: Float, rx: Float, ry: Float, a: Int = 0x40) {
        p.color = fade(0x202C20, a)
        cv.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
    }

    /** 바위 위에 이끼·풀이 앉은 자국. */
    private fun mossCap(cv: Canvas, p: Paint, path: Path, cx: Float, cy: Float, rx: Float, ry: Float, lite: Boolean) {
        inRockOval(cv, p, path, cx, cy, rx, ry, c(0xFF5F824E))
        inRockOval(cv, p, path, cx - rx * 0.22f, cy - ry * 0.28f, rx * 0.58f, ry * 0.6f,
            if (lite) c(0xFF8CC46C) else c(0xFF7FB15B))
    }

    /** 해면선에 붙는 깃물·이끼 톱니 띠. */
    private fun weedFringe(cv: Canvas, p: Paint, path: Path, x: Float, y: Float, w: Float, h: Float, col: Int) {
        var i = 0
        while (i < w.toInt()) {
            inRock(cv, p, path, x + i, y + (i % 2) * 1.2f, 1f, h, col)
            i++
        }
    }

    /**
     * 바위 소품 28종 — [PropLooks.ROCKS] 순서와 1:1.
     *
     * 암종(화강암·현무암·해식퇴적암·석회암·사암·인공석재·자연쇳돌)과 크기급
     * (자갈·낮은 돌·큰 바위)을 모두 다르게 두어, 지역마다 다른 돌무더기가 서게 한다.
     */
    private fun rockArt(look: Int): Bitmap = tilePainter { cv, p, r ->
        when (look) {
            // ═══ 화강암 — 설악·광릉·왕피의 비고지 ═══════════════════════════
            0 -> { // 화강암 노두 — 각진 면 3개가 지는 암괴
                rockShadow(cv, p, 16f, 28.5f, 11.5f, 3.2f)
                val body = floatArrayOf(
                    3f, 28f, 5f, 18f, 8f, 15f, 14f, 14f, 19f, 15f, 22f, 17f, 27f, 19f,
                    29f, 27f, 19f, 30f, 8f, 30f
                )
                val k = rockBody(cv, p, r, body, GRANITE, speck = 8, topBand = 0.12f)
                inRockLine(cv, p, k, 9f, 15.6f, 18f, 14.4f, fade(P_GRAN_H, 170), 1f)
                inRockLine(cv, p, k, 18f, 14.4f, 22f, 17.4f, fade(P_GRAN_D, 150), 1f)
                inRockLine(cv, p, k, 22f, 17.4f, 19f, 29.4f, fade(P_GRAN_D, 110), 1f)
                inRockOval(cv, p, k, 10f, 21f, 4f, 4f, fade(P_GRAN_L, 90))
                inRockOval(cv, p, k, 24f, 24f, 3.4f, 3f, fade(P_GRAN_D, 110))
                for (d in intArrayOf(0, 1, 2, 3, 4)) {
                    val dx = floatArrayOf(8f, 15f, 20f, 12f, 25f)[d]
                    val dy = floatArrayOf(19f, 17f, 22f, 25f, 21f)[d]
                    val s = floatArrayOf(2f, 1.6f, 2.2f, 1.4f, 1.6f)[d]
                    inRock(cv, p, k, dx, dy, s, s, fade(P_GRAN_H, 180))
                }
                crackIn(cv, p, k, 18f, 15f, 1.1f, 2.4f, fade(P_GRAN_D, 190), 4)
                crackIn(cv, p, k, 24f, 19f, -1.3f, -2.2f, fade(P_GRAN_D, 150), 3)
            }
            1 -> { // 설악 암괴 — 주 절리면이 지는 첨탑
                rockShadow(cv, p, 16f, 29.5f, 12f, 3.4f)
                val body = floatArrayOf(7f, 30f, 9f, 12f, 14f, 3f, 19f, 9f, 25f, 14f, 27f, 30f, 16f, 31f)
                val k = rockBody(cv, p, r, body, GRANITE, bands = 6, speck = 12)
                inRock(cv, p, k, 14f, 6f, 2.6f, 22f, fade(P_GRAN_D, 110))
                inRock(cv, p, k, 15.4f, 6f, 1f, 22f, fade(P_GRAN_H, 80))
                inRock(cv, p, k, 22f, 16f, 2.2f, 13f, fade(P_GRAN_D, 100))
                for (d in intArrayOf(0, 1, 2, 3)) {
                    val dx = floatArrayOf(11f, 17f, 21f, 12f)[d]
                    val dy = floatArrayOf(12f, 9f, 20f, 23f)[d]
                    inRock(cv, p, k, dx, dy, 1.6f, 1.6f, fade(P_GRAN_H, 150))
                }
                mossCap(cv, p, k, 12f, 28.4f, 5f, 1.8f, true)
                crackIn(cv, p, k, 18f, 3f, 1.2f, 2.6f, fade(P_GRAN_D, 170), 5)
            }
            2 -> { // 노두 자갈 — 흘러내린 부순 사면
                rockShadow(cv, p, 16f, 28.5f, 9f, 2.4f)
                val cx = floatArrayOf(3f, 6f, 10f, 14f, 18f, 22f, 26f, 7f, 12f, 17f, 22f, 10f, 15f, 19f, 14f, 17f)
                val cy = floatArrayOf(27f, 26f, 27f, 28f, 27f, 26f, 27f, 23f, 24f, 23f, 23f, 20f, 21f, 20f, 17.5f, 18f)
                val cw = floatArrayOf(2.6f, 3f, 2.8f, 2.4f, 3f, 2.6f, 2.4f, 3.4f, 3f, 3.6f, 3f, 2.8f, 2.4f, 2.6f, 2f, 1.8f)
                val ch = floatArrayOf(2f, 2.4f, 2.2f, 2f, 2.2f, 2f, 2f, 2.6f, 2.4f, 2.8f, 2.4f, 2.2f, 2f, 2f, 1.8f, 1.6f)
                for (i in cx.indices) {
                    rockBody(cv, p, r, lump(r, cx[i] + cw[i] / 2f, cy[i] + ch[i] / 2f, cw[i], ch[i], 6, 0.5f),
                        GRANITE, bands = 2, speck = 1, rim = false, topBand = 0.32f)
                }
            }
            3 -> { // 이끼 화강암 — 숲 바닥의 반원형 바위
                rockShadow(cv, p, 16f, 28.5f, 10f, 3f)
                val k = rockBody(cv, p, r, lump(r, 16f, 22f, 11f, 6.4f, 11, 0.26f), GRANITE, speck = 6)
                mossCap(cv, p, k, 13f, 17f, 7f, 3f, true)
                mossCap(cv, p, k, 20f, 15.2f, 3.8f, 1.8f, false)
                for (i in 0..2) {
                    inRock(cv, p, k, 4f + i * 1.9f, 24f - i * 1.8f, 1.1f, 4f + i * 1.6f, c(0xFF4F824A))
                    inRock(cv, p, k, 3.4f + i * 1.9f, 24f - i * 1.8f, 1.8f, 1f, c(0xFF7FB15B))
                }
            }
            // ═══ 현무암 — 제주·하도리·한라 ════════════════════════════════
            4 -> { // 현무암 기둥 — 서로 어긋난 육각 기둥 무리
                rockShadow(cv, p, 16f, 29.5f, 12f, 3.2f)
                val cx = floatArrayOf(4f, 10f, 18f, 24f)
                val ct = floatArrayOf(12f, 4f, 8f, 15f)
                val cw = floatArrayOf(6.2f, 7.4f, 6.4f, 5.4f)
                for (i in cx.indices) {
                    val x = cx[i]; val top = ct[i]; val w = cw[i]
                    val body = floatArrayOf(x, 30f, x + 0.6f, top + 2f, x + w / 2f, top,
                        x + w - 0.6f, top + 2.4f, x + w, 30f)
                    val k = rockBody(cv, p, r, body, BASALT, bands = 4, speck = 3)
                    inRock(cv, p, k, x + w * 0.42f, top + 2f, 1f, 26f, fade(P_BAS_H, 90))
                    inRock(cv, p, k, x + w * 0.62f, top + 3f, 1f, 25f, fade(P_BAS_D, 150))
                }
                for (x in floatArrayOf(9.6f, 17.2f, 23.4f)) {
                    p.color = fade(0xFF101416.toInt(), 190)
                    cv.drawRect(x, 8f, x + 1.4f, 30f, p)
                }
            }
            5 -> { // 용암 성벽 — 기둥상 단열이 선 검은 암벽
                rockShadow(cv, p, 16f, 30f, 12f, 3f)
                val body = floatArrayOf(2f, 30f, 3f, 12f, 7f, 7f, 13f, 10f, 19f, 4f, 25f, 9f, 29f, 14f, 30f, 30f)
                val k = rockBody(cv, p, r, body, BASALT, bands = 6, speck = 6)
                for (i in 0..4) {
                    val x = floatArrayOf(6f, 11f, 17f, 23f, 27f)[i]
                    val y0 = floatArrayOf(8f, 11f, 5f, 10f, 15f)[i]
                    inRock(cv, p, k, x, y0, 1.3f, 30f - y0, fade(0xFF14181A.toInt(), 170))
                    inRock(cv, p, k, x + 1.4f, y0 + 1f, 1.1f, 29f - y0, fade(P_BAS_L, 120))
                    inRock(cv, p, k, x + 2.6f, y0 + 2f, 0.8f, 28f - y0, fade(P_BAS_H, 70))
                }
                inRock(cv, p, k, 3f, 27f, 26f, 3f, fade(0xFF2C3234.toInt(), 200))
            }
            6 -> { // 현무암 자갈 — 기공이 구멍난 검은 부순 돌
                rockShadow(cv, p, 16f, 28.5f, 9f, 2.2f)
                val cx = intArrayOf(4, 8, 12, 16, 21, 25, 7, 12, 17, 22, 14, 19)
                val cy = floatArrayOf(26f, 24f, 26f, 24f, 25f, 23f, 21f, 21.5f, 20f, 21f, 18f, 18.5f)
                val cw = floatArrayOf(3f, 3.6f, 3f, 3.4f, 3f, 3.2f, 3f, 3.4f, 3f, 2.8f, 2.4f, 2.2f)
                val ch = floatArrayOf(2.6f, 3f, 2.4f, 3f, 2.6f, 2.8f, 2.4f, 2.6f, 2.4f, 2.2f, 2f, 1.8f)
                for (i in cx.indices) {
                    val k = rockBody(cv, p, r, lump(r, cx[i] + cw[i] / 2f, cy[i] + ch[i] / 2f, cw[i], ch[i], 7, 0.45f),
                        BASALT, bands = 2, speck = 1, rim = false, topBand = 0.36f)
                    inRock(cv, p, k, cx[i] + cw[i] * 0.35f, cy[i] + ch[i] * 0.32f, 1f, 1f, fade(0xFF0C0F10.toInt(), 200))
                    inRock(cv, p, k, cx[i] + cw[i] * 0.58f, cy[i] + ch[i] * 0.5f, 1f, 1f, fade(0xFF0C0F10.toInt(), 170))
                }
            }
            7 -> { // 현무암 방패바위 — 물에 닳은 매끈한 검은 바위
                rockShadow(cv, p, 16f, 28.5f, 10.5f, 3f)
                val k = rockBody(cv, p, r, lump(r, 16f, 21f, 11f, 7f, 12, 0.2f), BASALT, bands = 5, speck = 4)
                inRockOval(cv, p, k, 11f, 18f, 4f, 2f, fade(P_BAS_H, 80))
                for (i in 0..3) {
                    val dx = floatArrayOf(19f, 22f, 13f, 17f)[i]
                    val dy = floatArrayOf(17f, 21f, 25f, 23f)[i]
                    inRock(cv, p, k, dx, dy, 1f, 1f, fade(0xFF0C0F10.toInt(), 170))
                }
            }
            // ═══ 해식 퇴적암 — 동해안 ═══════════════════════════════════
            8 -> { // 동해 층리 바위 — 얇은 지층이 줄눈을 이루는 바위
                rockShadow(cv, p, 16f, 28.5f, 11f, 3f)
                val body = floatArrayOf(
                    3f, 28f, 5f, 18f, 11f, 15f, 21f, 16f, 28f, 21f, 30f, 27f, 20f, 30f, 8f, 30f
                )
                val k = rockBody(cv, p, r, body, SEDIMENT, bands = 5, speck = 4)
                for (i in 0..2) {
                    val y = floatArrayOf(19f, 22.4f, 25.4f)[i]
                    val hh = floatArrayOf(1.4f, 1.2f, 1.6f)[i]
                    inRock(cv, p, k, 4f, y, 25f, hh, fade(P_SED_D, 120))
                    inRock(cv, p, k, 4f, y + hh, 25f, 0.9f, fade(P_SED_H, 110))
                }
                inRockLine(cv, p, k, 18f, 17f, 20f, 28f, fade(P_SED_D, 110), 1f)
                weedFringe(cv, p, k, 22f, 24.4f, 7f, 2.4f, fade(P_SED_A, 170))
            }
            9 -> { // 해식 절벽 — 밑동에 파식 홈이 파인 절벽
                rockShadow(cv, p, 16f, 30f, 12f, 3f)
                val body = floatArrayOf(6f, 30f, 8f, 12f, 11f, 5f, 17f, 3f, 24f, 6f, 27f, 14f, 28f, 30f, 15f, 31f)
                val k = rockBody(cv, p, r, body, SEDIMENT, bands = 6, speck = 5)
                for (i in 0..2) {
                    val y = floatArrayOf(11f, 15.5f, 19.5f)[i]
                    inRock(cv, p, k, 6f, y, 22f, 1.2f, fade(P_SED_D, 95))
                    inRock(cv, p, k, 6f, y + 1.2f, 22f, 0.9f, fade(P_SED_H, 80))
                }
                inRock(cv, p, k, 5f, 23.4f, 24f, 3.2f, fade(0xFF2B343A.toInt(), 200))
                inRock(cv, p, k, 5f, 22.6f, 24f, 1f, fade(P_SED_H, 140))
                inRock(cv, p, k, 5f, 26.6f, 24f, 3.4f, fade(P_SED_D, 90))
                weedFringe(cv, p, k, 7f, 26.4f, 19f, 2.2f, fade(P_SED_A, 150))
            }
            10 -> { // 해안 사암 블록 — 깨진 모서리가 각진 블록
                rockShadow(cv, p, 16f, 28.5f, 9.5f, 2.8f)
                val body = floatArrayOf(5f, 28f, 6f, 18f, 8f, 15f, 16f, 16f, 21f, 14f, 26f, 18f, 27f, 27f, 15f, 30f)
                val k = rockBody(cv, p, r, body, SEDIMENT, bands = 4, speck = 4)
                inRockLine(cv, p, k, 8f, 15.4f, 16f, 16.4f, fade(P_SED_H, 190), 1f)
                inRockLine(cv, p, k, 21f, 14.4f, 26f, 18.4f, fade(P_SED_H, 190), 1f)
                inRock(cv, p, k, 6f, 21f, 21f, 1.4f, fade(P_SED_D, 140))
                inRock(cv, p, k, 6f, 22.4f, 21f, 0.9f, fade(P_SED_H, 120))
                inRock(cv, p, k, 6f, 25.4f, 21f, 1.2f, fade(P_SED_D, 130))
                inRockLine(cv, p, k, 12f, 18f, 10f, 30f, fade(P_SED_D, 150), 1f)
                inRockOval(cv, p, k, 20f, 23f, 3f, 2.6f, fade(P_SED_D, 90))
            }
            11 -> { // 갯바위 선반 — 파도에 밀려 눕는 넓적한 바위
                rockShadow(cv, p, 16f, 27.5f, 12f, 2.6f)
                val body = floatArrayOf(
                    2f, 24f, 5f, 19f, 13f, 18f, 22f, 19f, 30f, 23f, 30f, 26f, 16f, 28f, 3f, 26f
                )
                val k = rockBody(cv, p, r, body, SEDIMENT, bands = 4, speck = 5)
                inRock(cv, p, k, 2f, 22f, 30f, 1.4f, fade(0xFF3A4A50.toInt(), 200))
                weedFringe(cv, p, k, 4f, 24.4f, 24f, 2.4f, fade(P_SED_A, 190))
                for (i in 0..3) {
                    val dx = floatArrayOf(9f, 18f, 24f, 14f)[i]
                    val dy = floatArrayOf(24f, 25f, 23.6f, 22.4f)[i]
                    inRock(cv, p, k, dx, dy, 1.6f, 1.2f, c(0xFFF0E4CB))
                }
                inRockOval(cv, p, k, 16f, 21f, 8f, 1.4f, fade(P_SED_H, 90))
            }
            // ═══ 석회암 — 전주·광주 서안 ═════════════════════════════════
            12 -> { // 밝은 석회암 — 용식 구멍이 패인 연한 바위
                rockShadow(cv, p, 16f, 28.5f, 10f, 3f)
                val body = floatArrayOf(5f, 28f, 6f, 16f, 12f, 13f, 23f, 15f, 28f, 21f, 28f, 28f, 16f, 30f)
                val k = rockBody(cv, p, r, body, LIMESTONE, bands = 5, speck = 6)
                for (i in 0..2) {
                    val cx = floatArrayOf(11f, 20f, 23f)[i]
                    val cy = floatArrayOf(20f, 18f, 24f)[i]
                    val rad = floatArrayOf(1.9f, 1.5f, 1.7f)[i]
                    inRockOval(cv, p, k, cx, cy, rad, rad * 0.9f, fade(0xFF4A5247.toInt(), 200))
                    inRockOval(cv, p, k, cx - 0.5f, cy - 0.6f, rad * 0.45f, rad * 0.4f, fade(P_LIM_H, 190))
                }
                inRock(cv, p, k, 6f, 26f, 22f, 1.6f, fade(P_LIM_D, 120))
            }
            13 -> { // 석회암 절벽 — 구멍이 뚫린 밝은 절벽면
                rockShadow(cv, p, 16f, 30f, 11.5f, 3f)
                val body = floatArrayOf(7f, 30f, 8f, 10f, 13f, 4f, 22f, 6f, 27f, 13f, 27f, 30f, 15f, 31f)
                val k = rockBody(cv, p, r, body, LIMESTONE, bands = 6, speck = 8)
                for (i in 0..3) {
                    val cx = floatArrayOf(13f, 21f, 16f, 23f)[i]
                    val cy = floatArrayOf(14f, 19f, 24f, 9f)[i]
                    val rad = floatArrayOf(2.2f, 1.8f, 1.6f, 1.4f)[i]
                    inRockOval(cv, p, k, cx, cy, rad, rad, fade(0xFF515949.toInt(), 180))
                    inRockOval(cv, p, k, cx - 0.6f, cy - 0.7f, rad * 0.4f, rad * 0.4f, fade(P_LIM_H, 170))
                }
                inRock(cv, p, k, 8f, 27f, 19f, 2f, fade(P_LIM_D, 110))
            }
            14 -> { // 석회암 자갈 — 갯바위 밑에 모인 밝은 자갈
                rockShadow(cv, p, 16f, 28.5f, 7.5f, 2f)
                val cx = intArrayOf(5, 9, 14, 19, 24, 27, 7, 12, 17, 22, 14)
                val cy = floatArrayOf(26f, 24f, 26f, 24f, 25f, 27f, 21f, 21.5f, 20f, 21f, 18f)
                val cw = floatArrayOf(3.2f, 3.6f, 3f, 3.4f, 3f, 2.2f, 3f, 3.4f, 3f, 2.6f, 2.4f)
                val ch = floatArrayOf(2.6f, 3f, 2.4f, 2.8f, 2.4f, 1.8f, 2.4f, 2.6f, 2.4f, 2.2f, 2f)
                for (i in cx.indices) {
                    val k = rockBody(cv, p, r, lump(r, cx[i] + cw[i] / 2f, cy[i] + ch[i] / 2f, cw[i], ch[i], 7, 0.4f),
                        LIMESTONE, bands = 2, speck = 1, rim = false, topBand = 0.36f)
                    inRock(cv, p, k, cx[i] + cw[i] * 0.3f, cy[i] + ch[i] * 0.34f, 1f, 1f, fade(0xFF4A5247.toInt(), 200))
                }
            }
            // ═══ 사암 — 철원 평야·대구 분지 ═════════════════════════════
            15 -> { // 사암 단층 — 따뜻한 색의 어긋난 층
                rockShadow(cv, p, 16f, 28.5f, 10.5f, 3f)
                val body = floatArrayOf(4f, 28f, 6f, 15f, 13f, 12f, 24f, 13f, 29f, 19f, 29f, 28f, 17f, 30f)
                val k = rockBody(cv, p, r, body, SANDSTONE, bands = 5, speck = 5)
                for (i in 0..2) {
                    val y = floatArrayOf(17f, 20f, 23.5f)[i]
                    val hh = floatArrayOf(1.6f, 1.4f, 1.6f)[i]
                    inRock(cv, p, k, 5f, y, 25f, hh, fade(P_SAN_D, 140))
                    inRock(cv, p, k, 5f, y + hh, 25f, 0.9f, fade(P_SAN_H, 110))
                }
                crackIn(cv, p, k, 20f, 13f, 1.4f, 2.4f, fade(P_SAN_D, 200), 4)
            }
            16 -> { // 사암 절벽 — 사교층리가 비스듬히 지는 벽
                rockShadow(cv, p, 16f, 30f, 12f, 3f)
                val body = floatArrayOf(6f, 30f, 7f, 9f, 14f, 3f, 23f, 6f, 28f, 13f, 28f, 30f, 15f, 31f)
                val k = rockBody(cv, p, r, body, SANDSTONE, bands = 6, speck = 7)
                for (i in 0..3) {
                    val y = floatArrayOf(7f, 11f, 15f, 19f)[i]
                    inRock(cv, p, k, 6f, y, 23f, 1.6f, fade(P_SAN_D, 130))
                    inRock(cv, p, k, 6f, y + 1.6f, 23f, 1f, fade(P_SAN_H, 100))
                }
                for (i in 0..1) {
                    val y = floatArrayOf(13f, 20f)[i]
                    inRockLine(cv, p, k, 7f, y + 2.4f, 28f, y, fade(P_SAN_H, 85), 1f)
                }
            }
            17 -> { // 사암 조각돌 — 논둑에 흩어진 따뜻한 자갈
                rockShadow(cv, p, 16f, 28.5f, 7.5f, 2f)
                val cx = floatArrayOf(4f, 8f, 12f, 17f, 22f, 26f, 7f, 12f, 17f, 22f, 14f, 19f)
                val cy = floatArrayOf(26f, 25f, 27f, 26f, 26f, 27f, 22f, 22f, 22f, 22f, 19f, 19f)
                val cw = floatArrayOf(3f, 3.2f, 2.8f, 3f, 2.8f, 2.2f, 3.2f, 3.4f, 3f, 3.2f, 2.6f, 2.4f)
                val ch = floatArrayOf(2.4f, 2.6f, 2.2f, 2.4f, 2.2f, 1.8f, 2.6f, 2.8f, 2.4f, 2.6f, 2.2f, 2f)
                for (i in cx.indices) {
                    val k = rockBody(cv, p, r, lump(r, cx[i] + cw[i] / 2f, cy[i] + ch[i] / 2f, cw[i], ch[i], 7, 0.42f),
                        SANDSTONE, bands = 2, speck = 1, rim = false, topBand = 0.34f)
                    inRock(cv, p, k, cx[i].toFloat(), cy[i] + ch[i] * 0.5f, cw[i], 0.9f, fade(P_SAN_D, 110))
                }
            }
            // ═══ 인공 석재 — 항구 방파제·한옥 돌담·도시 화단 ═══════════════
            18 -> { // 방파제 블록 — 계기초 블록이 이중으로 쌓인 방파제
                rockShadow(cv, p, 16f, 28.5f, 12f, 3f)
                val bx = floatArrayOf(3f, 11f, 18f, 25f, 5f, 12f, 19f, 9f, 16f, 22f)
                val by = floatArrayOf(23f, 23.5f, 23f, 24f, 16.5f, 17f, 16.5f, 11f, 11.5f, 13f)
                val bw = floatArrayOf(8f, 7.5f, 8f, 5f, 7f, 7f, 7.5f, 6f, 6.5f, 5f)
                val bh = floatArrayOf(6f, 5.5f, 6f, 5f, 6.5f, 6f, 6.5f, 5.5f, 5f, 5f)
                for (i in bx.indices) {
                    val x = bx[i]; val y = by[i]; val w = bw[i]; val h = bh[i]
                    val body = floatArrayOf(x, y + h, x + 0.4f, y + 0.8f, x + w * 0.45f, y,
                        x + w, y + 1.2f, x + w, y + h - 0.8f)
                    val k = rockBody(cv, p, r, body, CONCRETE, bands = 3, speck = 2, topBand = 0.3f)
                    inRock(cv, p, k, x + 0.4f, y + 0.8f, w * 0.5f, 1f, fade(P_CON_H, 170))
                    inRock(cv, p, k, x + 0.4f, y + h - 1.6f, w - 0.8f, 1f, fade(P_CON_D, 150))
                }
            }
            19 -> { // 옹벽 돌담 — 마른돌로 쌓은 돌담
                rockShadow(cv, p, 16f, 30f, 11f, 2.8f)
                val ry = floatArrayOf(5f, 4f, 5f, 4f, 7f)
                val rx = floatArrayOf(6f, 4f, 5f, 4f, 7f)
                val rw = floatArrayOf(20f, 24f, 22f, 25f, 19f)
                val rh = floatArrayOf(5f, 5f, 5f, 5f, 4.4f)
                for (rowI in ry.indices) {
                    val y = ry[rowI]; val w = rw[rowI]
                    val n = maxOf(2, (w / 7f).toInt())
                    val bw = w / n
                    for (i in 0 until n) {
                        val x = rx[rowI] + i * bw + (if (rowI % 2 == 0) 0f else 0.6f)
                        val body = floatArrayOf(x, y + rh[rowI], x + 0.5f, y + 0.8f,
                            x + bw - 1f, y, x + bw - 0.4f, y + rh[rowI] - 0.6f)
                        val k = rockBody(cv, p, r, body, CONCRETE, bands = 3, speck = 1)
                        inRock(cv, p, k, x + 0.5f, y + 0.8f, bw - 2f, 0.9f, fade(P_CON_H, 130))
                    }
                }
                p.color = fade(0xFF5F824E.toInt(), 170)
                for (i in 0..4) cv.drawRect(5f + i * 4.4f, 20f, 7.4f + i * 4.4f, 21.6f, p)
            }
            20 -> { // 조경 화단석 — 다듬어 네모난 화단 돌
                rockShadow(cv, p, 16f, 28f, 7f, 1.8f)
                val bx = floatArrayOf(7f, 16f, 11f, 18f)
                val by = floatArrayOf(23f, 22f, 18f, 17f)
                val bw = floatArrayOf(8f, 9f, 6f, 6f)
                val bh = floatArrayOf(5f, 5.4f, 4.4f, 4.4f)
                for (i in bx.indices) {
                    val x = bx[i]; val y = by[i]; val w = bw[i]; val h = bh[i]
                    val body = floatArrayOf(x, y + h, x + 0.5f, y + 0.6f, x + w - 0.8f, y, x + w, y + h - 0.5f)
                    rockBody(cv, p, r, body, CONCRETE, bands = 2)
                }
                p.color = fade(0xFFB4B9B4.toInt(), 220)
                cv.drawRect(4f, 28f, 28f, 29.6f, p)
            }
            21 -> { // 호안석 가비온 — 철망에 꿰매 돌을 채운 제방
                rockShadow(cv, p, 16f, 28.5f, 11f, 3f)
                val body = floatArrayOf(4f, 28f, 5f, 15f, 12f, 12f, 24f, 14f, 28f, 20f, 28f, 28f, 16f, 30f)
                val k = rockBody(cv, p, r, body, GABION, bands = 4, speck = 10, speckCol = c(0xFF525A5F))
                for (i in 0..3) {
                    val y = floatArrayOf(15.6f, 19.6f, 23.6f, 27.4f)[i]
                    inRock(cv, p, k, 4f, y, 25f, 1f, fade(0xFF2E363A.toInt(), 205))
                }
                var wx = 4f
                while (wx < 29f) {
                    inRock(cv, p, k, wx, 12f, 1f, 16f, fade(0xFF2E363A.toInt(), 205))
                    wx += 3f
                }
                val gx = intArrayOf(7, 14, 21, 9, 17, 24)
                val gy = floatArrayOf(17f, 17.4f, 16.6f, 21f, 21.4f, 21f)
                val gw = floatArrayOf(4f, 4.6f, 4f, 4.4f, 4f, 3.4f)
                val gh = floatArrayOf(3.4f, 3.2f, 3.4f, 3.6f, 3.2f, 3f)
                for (i in gx.indices) {
                    rockBody(cv, p, r, lump(r, gx[i] + gw[i] / 2f, gy[i] + gh[i] / 2f, gw[i], gh[i], 7, 0.35f),
                        STONES_IN_CAGE, bands = 2, speck = 1, rim = false, topBand = 0.34f)
                }
                inRock(cv, p, k, 6f, 12.6f, 20f, 1.4f, fade(0xFF8E959B.toInt(), 190))
            }
            // ═══ 자연 쇳돌 — 강가·갯벌·숲·해안 ═════════════════════════════
            22 -> { // 강 자갈 더미 — 물에 둥글게 닳은 자갈
                rockShadow(cv, p, 16f, 28.5f, 9f, 2.4f)
                val cx = intArrayOf(5, 12, 19, 24, 9, 16)
                val cy = floatArrayOf(24f, 22f, 24f, 21f, 18f, 17f)
                val cw = floatArrayOf(6f, 7f, 6f, 5f, 5f, 6f)
                val ch = floatArrayOf(4.4f, 5f, 4.4f, 4f, 3.6f, 4f)
                for (i in cx.indices) {
                    val k = rockBody(cv, p, r, lump(r, cx[i] + cw[i] / 2f, cy[i] + ch[i] / 2f, cw[i], ch[i], 9, 0.18f),
                        COBBLE, bands = 3, speck = 1, rim = false)
                    inRock(cv, p, k, cx[i] + cw[i] * 0.28f, cy[i] + ch[i] * 0.24f, cw[i] * 0.34f, 1f, fade(P_COB_H, 140))
                }
            }
            23 -> { // 숲 이끼 바위 — 이끼와 고사리가 앉은 바위
                rockShadow(cv, p, 16f, 28.5f, 10f, 3f)
                val k = rockBody(cv, p, r, lump(r, 16f, 21f, 11f, 7.4f, 12, 0.2f), COBBLE, bands = 5, speck = 4)
                mossCap(cv, p, k, 14f, 16f, 7.4f, 2.8f, true)
                mossCap(cv, p, k, 20f, 14.4f, 3.6f, 1.6f, false)
                inRockOval(cv, p, k, 8f, 19f, 2.6f, 1.4f, c(0xFF4E7040))
                for (i in 0..2) {
                    inRock(cv, p, k, 3.5f + i * 2f, 23.4f - i * 1.7f, 1.1f, 4.4f + i * 1.5f, c(0xFF4F824A))
                    inRock(cv, p, k, 2.9f + i * 2f, 23.4f - i * 1.7f, 1.8f, 1f, c(0xFF7FB15B))
                }
            }
            24 -> { // 갯벌 사구돌 — 갯벌에 박힌 사구(토기알) 돌
                rockShadow(cv, p, 16f, 28.5f, 8f, 2.2f)
                val cx = intArrayOf(8, 14, 21, 25, 11)
                val cy = floatArrayOf(25f, 22f, 24f, 21f, 19f)
                val rad = floatArrayOf(3.2f, 4f, 3.4f, 2.6f, 2.6f)
                for (i in cx.indices) {
                    val k = rockBody(cv, p, r, lump(r, cx[i].toFloat(), cy[i], rad[i], rad[i] * 0.86f, 9, 0.3f),
                        SANDSTONE, bands = 3, speck = 2, rim = false)
                    inRockOval(cv, p, k, cx[i].toFloat(), cy[i], rad[i] * 0.5f, rad[i] * 0.4f, fade(0xFF8A6A4E.toInt(), 150))
                }
            }
            25 -> { // 조개 자갈 — 조개껍데기가 부서진 하얀 자갈밭
                rockShadow(cv, p, 16f, 28.5f, 9f, 2.2f)
                val cx = intArrayOf(4, 8, 13, 18, 23, 27, 6, 11, 16, 21, 25, 13, 19, 10)
                val cy = floatArrayOf(27f, 25f, 27f, 26f, 27f, 25f, 22f, 22f, 21f, 22f, 21f, 18.5f, 18f, 19f)
                val cw = floatArrayOf(3.4f, 3.8f, 3.2f, 3.4f, 3f, 2.6f, 3.4f, 3.6f, 3.2f, 3.4f, 2.8f, 2.8f, 2.6f, 2.2f)
                val ch = floatArrayOf(2.4f, 2.8f, 2.2f, 2.4f, 2f, 2.2f, 2.6f, 2.8f, 2.4f, 2.6f, 2.2f, 2.2f, 2f, 1.8f)
                for (i in cx.indices) {
                    val x = cx[i].toFloat(); val y = cy[i]; val w = cw[i]; val h = ch[i]
                    p.color = if (i % 3 == 0) c(0xFFFBF4E2) else c(0xFFF0E4CD)
                    cv.drawOval(RectF(x, y, x + w, y + h), p)
                    p.color = c(0xFFD6C3A0)
                    cv.drawOval(RectF(x + 0.3f, y + h - 1.2f, x + w - 0.3f, y + h), p)
                    var j = 0
                    while (j < 2) {
                        val sx = x + 0.6f + j * (w - 1.6f) / 2f
                        p.color = c(0xFFE3D2B2)
                        cv.drawRect(sx, y + 0.4f, sx + 0.9f, y + h - 1.4f, p)
                        j++
                    }
                }
            }
            26 -> { // 대왕암 첨탑 — 바다 한가운데 선 돌기둥
                rockShadow(cv, p, 16f, 30f, 9f, 2.8f)
                val body = floatArrayOf(10f, 30f, 11f, 14f, 14f, 3f, 19f, 6f, 22f, 16f, 22f, 30f, 15f, 31f)
                val k = rockBody(cv, p, r, body, SEDIMENT, bands = 6, speck = 5)
                for (i in 0..4) {
                    val y = 8f + i * 5f
                    inRock(cv, p, k, 10f, y, 13f, 1.4f, fade(P_SED_D, 140))
                }
                inRock(cv, p, k, 9f, 24f, 15f, 3f, fade(0xFF2F383E.toInt(), 170))
                weedFringe(cv, p, k, 10f, 27f, 11f, 2f, fade(P_SED_A, 170))
                inRock(cv, p, k, 9f, 28.4f, 15f, 2f, fade(0xFF3E4A50.toInt(), 200))
            }
            else -> { // 갈대 곁 도라돌 — 물가 갈대 사이에 놓인 도라돌
                rockShadow(cv, p, 16f, 28f, 8f, 2.2f)
                for (i in 0..2) {
                    val x = floatArrayOf(9f, 18f, 13f)[i]
                    val y = floatArrayOf(23f, 22f, 20f)[i]
                    val w = floatArrayOf(8f, 7f, 6f)[i]
                    val h = floatArrayOf(6f, 5.4f, 4.4f)[i]
                    rockBody(cv, p, r, lump(r, x + w / 2f, y + h / 2f, w, h, 10, 0.22f),
                        COBBLE, bands = 3, speck = 2, rim = false)
                }
                for (i in 0..4) {
                    val x = floatArrayOf(5f, 7.5f, 25f, 27f, 22f)[i]
                    val hh = floatArrayOf(9f, 12f, 10f, 8f, 6f)[i]
                    p.color = c(0xFF8FAE5C)
                    cv.drawRect(x, 28f - hh, x + 1.2f, 28f, p)
                    p.color = c(0xFFC4C877)
                    cv.drawRect(x - 0.9f, 28f - hh + 2.2f, x + 2.1f, 28f - hh + 3.4f, p)
                }
                for (i in 0..2) {
                    val x = floatArrayOf(6.2f, 26.2f, 24.5f)[i]
                    val hh = floatArrayOf(14f, 13f, 9f)[i]
                    p.color = c(0xFF5F8249)
                    cv.drawRect(x, 28f - hh, x + 1f, 28f, p)
                }
            }
        }
    }

    /** 균열 — 바위 실루엣 안에만 찍는다. */
    private fun crackIn(
        cv: Canvas, p: Paint, path: Path, x: Float, y: Float, dx: Float, dy: Float, col: Int, steps: Int
    ) {
        var i = 0
        while (i < steps) {
            inRock(cv, p, path, x + dx * i, y + dy * i, 1f, 1f, col)
            i++
        }
    }

    /** 지역별로 실루엣과 잎색을 바꾼 나무 소품 8종. */
    private fun extraTreeArt(look: Int): Bitmap = tilePainter { cv, p, r ->
        propShadow(cv, p, 16f, 28f, 10.5f, 3f)
        when (look) {
            0 -> { // 버드나무 — 긴 늘어진 가지
                px(cv, p, 14f, 14f, 5f, 17f, c(0xFF65452E)); px(cv, p, 15.4f, 14f, 2f, 17f, c(0xFF9A7046))
                p.color = c(0xFF315E3D); cv.drawCircle(16f, 10f, 10f, p)
                p.color = c(0xFF5F9860); cv.drawCircle(12f, 8f, 6f, p); cv.drawCircle(21f, 10f, 6f, p)
                for (i in 0..5) {
                    val x = 6f + i * 4f
                    val h = 7f + (i % 3) * 2f
                    px(cv, p, x, 10f + (i % 2) * 2f, 1.5f, h, if (i % 2 == 0) c(0xFF477B4D) else c(0xFF76A65B))
                }
            }
            1 -> { // 대숲 — 마디가 선명한 가는 대나무 여러 대
                for ((x, h) in listOf(7 to 19, 12 to 26, 18 to 22, 24 to 28)) {
                    px(cv, p, x.toFloat(), (30 - h).toFloat(), 2.2f, h.toFloat(), c(0xFF397343))
                    px(cv, p, x + 0.7f, (31 - h).toFloat(), 0.7f, h - 2f, c(0xFF8CB45B))
                    for (y in (30 - h + 5)..28 step 6) px(cv, p, x - 0.5f, y.toFloat(), 3.2f, 1.2f, c(0xFF285C37))
                    px(cv, p, x - 4f, (30 - h + 4).toFloat(), 5f, 1.2f, c(0xFF4A884C))
                    px(cv, p, x + 2f, (30 - h + 8).toFloat(), 5f, 1.2f, c(0xFF4A884C))
                }
            }
            2 -> { // 동백나무 — 짙은 잎과 붉은 꽃
                px(cv, p, 14f, 16f, 5f, 15f, c(0xFF60432D))
                p.color = c(0xFF234D36); cv.drawCircle(16f, 12f, 12f, p)
                p.color = c(0xFF36744A); cv.drawCircle(11f, 9f, 7f, p); cv.drawCircle(21f, 13f, 7f, p)
                p.color = c(0xFFD94B45)
                for ((x, y) in listOf(8f to 9f, 20f to 7f, 23f to 15f, 12f to 17f)) cv.drawRect(x, y, x + 3f, y + 3f, p)
                px(cv, p, 8f, 9f, 1f, 2f, c(0xFFFFB06A)); px(cv, p, 20f, 7f, 1f, 2f, c(0xFFFFB06A))
            }
            3 -> { // 해풍에 한쪽으로 눕는 곰솔
                px(cv, p, 13f, 19f, 5f, 12f, c(0xFF5D3A20))
                val a = Path().apply { moveTo(16f, 1f); lineTo(30f, 13f); lineTo(24f, 14f); lineTo(31f, 21f); lineTo(20f, 19f); lineTo(25f, 29f); lineTo(7f, 28f); lineTo(14f, 19f); lineTo(5f, 20f); lineTo(12f, 12f); close() }
                p.color = c(0xFF214D39); cv.drawPath(a, p)
                px(cv, p, 13f, 9f, 9f, 1.4f, c(0xFF4A8553)); px(cv, p, 10f, 17f, 12f, 1.4f, c(0xFF4A8553))
            }
            4 -> { // 자작나무 — 가는 흰 줄기와 짙은 작은 수관
                px(cv, p, 13f, 13f, 6f, 19f, c(0xFFF1EBD8)); px(cv, p, 15f, 13f, 2f, 19f, c(0xFFCEC8B5))
                for ((x, y) in listOf(13f to 19f, 16f to 22f, 12f to 27f)) px(cv, p, x, y, 4f, 1.3f, c(0xFF494B45))
                p.color = c(0xFF315E3D); cv.drawCircle(16f, 8f, 8f, p)
                p.color = c(0xFF5F9655); cv.drawCircle(11f, 9f, 5f, p); cv.drawCircle(21f, 10f, 5f, p)
            }
            5 -> { // 은행나무 — 부채꼴처럼 펼쳐지는 노란 잎
                px(cv, p, 14f, 16f, 5f, 16f, c(0xFF68482D))
                p.color = c(0xFF8C8B36); cv.drawCircle(16f, 10f, 11f, p)
                p.color = c(0xFFD2B849); cv.drawCircle(11f, 8f, 7f, p); cv.drawCircle(21f, 11f, 7f, p)
                p.color = c(0xFFE9D46A)
                for ((x, y) in listOf(7f to 7f, 13f to 4f, 20f to 6f, 22f to 13f, 11f to 14f)) cv.drawRect(x, y, x + 2.5f, y + 2f, p)
                px(cv, p, 9f, 15f, 4f, 1f, c(0xFFA88D3E)); px(cv, p, 20f, 17f, 3f, 1f, c(0xFFA88D3E))
            }
            6 -> { // 과수원 — 둥근 잎 사이로 붉은 열매
                px(cv, p, 14f, 16f, 5f, 16f, c(0xFF65442E))
                p.color = c(0xFF315F38); cv.drawCircle(16f, 11f, 11f, p)
                p.color = c(0xFF61914A); cv.drawCircle(11f, 9f, 6f, p); cv.drawCircle(21f, 12f, 6f, p)
                p.color = c(0xFFD7483F)
                for ((x, y) in listOf(8f to 12f, 19f to 8f, 22f to 15f, 13f to 7f)) cv.drawRect(x, y, x + 2.4f, y + 2.4f, p)
                px(cv, p, 9f, 12f, 1f, 1f, c(0xFFFFB66A)); px(cv, p, 20f, 8f, 1f, 1f, c(0xFFFFB66A))
            }
            else -> { // 전나무 — 층층이 뾰족한 고산 상록수
                px(cv, p, 14f, 18f, 4f, 14f, c(0xFF5E442E))
                val path = Path()
                p.color = c(0xFF1C4938)
                path.moveTo(16f, 0f); path.lineTo(25f, 13f); path.lineTo(21f, 12f); path.lineTo(29f, 22f); path.lineTo(23f, 20f); path.lineTo(31f, 30f); path.lineTo(1f, 30f); path.lineTo(9f, 20f); path.lineTo(3f, 22f); path.lineTo(11f, 12f); path.lineTo(7f, 13f); path.close()
                cv.drawPath(path, p)
                px(cv, p, 12f, 9f, 7f, 1.2f, c(0xFF3E7950)); px(cv, p, 9f, 18f, 14f, 1.2f, c(0xFF3E7950)); px(cv, p, 6f, 27f, 20f, 1.2f, c(0xFF3E7950))
            }
        }
    }

    /**
     * 지역 수종 4종 — T.TREE 변형 인덱스 15·16·17·18.
     * 가로수(느티나무)·공원 침엽수(향나무)·제주 야자수·강원 오리나무로,
     * "나무도 그 지역이어야 한다"는 같은 원칙을 따른다.
     */
    private fun regionalTreeArt(look: Int): Bitmap = tilePainter { cv, p, r ->
        propShadow(cv, p, 16f, 28f, 10.5f, 3f)
        when (look) {
            0 -> { // 느티나무 — 거치미처럼 벌어진 가로수, 얼룩배기
                px(cv, p, 14.2f, 15f, 5.4f, 16f, c(0xFF9A8C79))
                px(cv, p, 15.2f, 15f, 2.4f, 16f, c(0xFFBCAF9B))
                // 두 갈래로 갈라진 줄기
                for (i in 0..3) {
                    px(cv, p, 12.4f - i * 0.5f, 12f - i * 3.4f, 2.4f - i * 0.3f, 4f, c(0xFF9A8C79))
                    px(cv, p, 18.2f + i * 0.4f, 11f - i * 3.1f, 2.2f - i * 0.3f, 3.6f, c(0xFF8E806E))
                }
                // 얼룩배기 — 느티나무 특유
                px(cv, p, 14.6f, 22f, 1.6f, 2.2f, c(0xFFD8CFBE))
                px(cv, p, 15f, 17f, 2f, 1.4f, c(0xFFD8CFBE))
                px(cv, p, 14.4f, 27f, 2f, 1.2f, c(0xFFD8CFBE))
                // 벌어진 수관
                p.color = c(0xFF3B6B41); cv.drawCircle(16f, 10f, 11.5f, p)
                p.color = c(0xFF4E8A52); cv.drawCircle(11f, 8f, 7f, p); cv.drawCircle(21f, 9f, 7f, p)
                p.color = c(0xFF66A868); cv.drawCircle(9f, 12f, 4.4f, p); cv.drawCircle(23f, 12f, 4.4f, p)
                p.color = c(0xFF7EC07E); cv.drawCircle(12f, 6f, 3.2f, p); cv.drawCircle(20f, 6.6f, 2.6f, p)
                noise(cv, p, r, 4f, 1f, 28f, 21f, c(0xFF34603A), 10, 1f, 1.8f)
            }
            1 -> { // 향나무(메타세쿼oia) — 빨간나무 껍질의 곧은 원뿔
                px(cv, p, 14.4f, 19f, 4.6f, 12f, c(0xFF7A4A34))
                px(cv, p, 15.2f, 19f, 1.6f, 12f, c(0xFF9E6446))
                noise(cv, p, r, 14.6f, 19f, 18.4f, 30f, c(0xFF5E3624), 5, 1f, 1.4f)
                // 깃털처럼 얇은 가지층
                for (i in 0 until 6) {
                    val y = 3f + i * 4.4f
                    val half = 3.2f + i * 2.1f
                    p.color = c(0xFF1E4C36)
                    px(cv, p, 16f - half, y + 1.6f, half * 2f, 2.4f, p.color)
                    p.color = c(0xFF2E6B48)
                    px(cv, p, 16f - half + 0.8f, y, half * 2f - 1.6f, 1.8f, p.color)
                    p.color = c(0xFF4B8C5C)
                    for (d in 0 until 4) {
                        val fx = 16f - half + 1.4f + d * (half * 2f - 2.8f) / 3f
                        px(cv, p, fx, y - 0.6f, 1.2f, 1.2f, p.color)
                    }
                }
                px(cv, p, 15f, 0f, 2.4f, 4f, c(0xFF2E6B48))
            }
            2 -> { // 야자수(소노베) — 제주 해안 가로수의 고유한 실루엣
                // 고리 모양 줄기
                for (i in 0 until 6) {
                    val y = 12f + i * 3.1f
                    px(cv, p, 15.2f - i * 0.25f, y, 3.6f, 3.1f, c(0xFF7A5A38))
                    px(cv, p, 15.6f - i * 0.25f, y + 0.4f, 2.2f, 2.2f, c(0xFF93704A))
                    px(cv, p, 15f - i * 0.25f, y + 2.6f, 4.4f, 0.8f, c(0xFF5E432A))
                }
                // 바깥으로 젖혀진 잎
                for (i in 0 until 7) {
                    val a = -160f + i * 47f
                    val len = 11f - i % 2 * 1.5f
                    val dx = kotlin.math.cos(Math.toRadians(a.toDouble())).toFloat() * len
                    val dy = kotlin.math.sin(Math.toRadians(a.toDouble())).toFloat() * len * 0.72f
                    p.color = if (i % 2 == 0) c(0xFF2E7A46) else c(0xFF3E9455)
                    p.strokeWidth = 2.4f
                    cv.drawLine(16f, 11f, 16f + dx, 11f + dy, p)
                    p.strokeWidth = 1f
                    // 잎 몸통 — 갈대뻑질처럼 가늘게 뻗은 소엽
                    var t = 0.35f
                    while (t < 1.05f) {
                        val lx = 16f + dx * t
                        val ly = 11f + dy * t
                        val pl = 2.4f * (1f - (t - 0.35f) * 0.6f)
                        px(cv, p, lx, ly - pl, 1.1f, pl * 2f, if (i % 2 == 0) c(0xFF4B9E60) else c(0xFF63B46F))
                        t += 0.16f
                    }
                }
                // 야자수 열매 송이
                p.color = c(0xFF8A6A2E); cv.drawCircle(16f, 13.5f, 2.6f, p)
                p.color = c(0xFFB98C3A)
                cv.drawCircle(15f, 12.8f, 1f, p); cv.drawCircle(17f, 14.2f, 0.9f, p)
            }
            else -> { // 오리나무 — 강원 산지 가는 곧은 줄기의 하얀 나무
                px(cv, p, 14.6f, 12f, 3.6f, 19f, c(0xFFD9DCC8))
                px(cv, p, 15.4f, 12f, 1.4f, 19f, c(0xFFB4B9A4))
                // 검은 눈무늬(애벌레 먹은 자리)
                for ((x, y) in listOf(15f to 16f, 16.4f to 21f, 15f to 26f, 17.2f to 18f)) {
                    px(cv, p, x, y, 2.2f, 1.4f, c(0xFF4A4F43))
                }
                for (i in 0..2) {
                    px(cv, p, 15f - i * 0.4f, 14f - i * 4.4f, 1.6f, 5f, c(0xFFD9DCC8))
                    px(cv, p, 16.6f + i * 0.3f, 13f - i * 4f, 1.4f, 4.4f, c(0xFFC6CAB4))
                }
                // 좁고 곧은 수관
                p.color = c(0xFF3A6B40); cv.drawCircle(16f, 8f, 8.4f, p)
                p.color = c(0xFF4F8B55); cv.drawCircle(13f, 7f, 5.4f, p); cv.drawCircle(19f, 8f, 5f, p)
                p.color = c(0xFF6BA96C); cv.drawCircle(12f, 5f, 3f, p); cv.drawCircle(20f, 5.6f, 2.6f, p)
                noise(cv, p, r, 7f, 1f, 25f, 17f, c(0xFF335C36), 8, 1f, 1.5f)

            }
        }
    }

    /**
     * 한반도에 실제로 서그는 나무 21종 + 메타세콰이아(가을) — T.TREE 변형 인덱스 15..36.
     *
     * 종마다 **수형(크기)·잎 모양·열매·나무껍질**이 모두 다르게 찍힌다.
     * 타일 하나(32px) 안에서 키가 위쪽까지 꽉 차는 나무(가문비나무·느티나무·야자나무·
     * 메타세콰이아), 수관이 옆으로 넓게 퍼지는 나무(떡갈나무·주목·회화나무),
     * 절반 타일 높이의 관목(진달래·감귤나무·산벚나무)이 섞이므로
     * 같은 '나무 한 칸' 이라도 크기가 제각각으로 읽힌다.
     *
     * 인덱스는 위 TREE_* 상수 · [treeKinds] 와 같은 순서다.
     */
    private fun speciesTreeArt(look: Int): Bitmap = tilePainter { cv, p, r ->
        val bark = c(0xFF5D3A20)
        val barkMid = c(0xFF7A4E2B)
        val barkLite = c(0xFF9A6A3E)

        // ── 공통 모티브 ──────────────────────────────────────────────────
        /** 처지는 침엽 층 하나 (가문비나무) — 끝이 아래로 살짝 꺾인 실루엣 */
        fun tier(apex: Float, baseY: Float, hw: Float, dark: Int, mid: Int, lite: Int) {
            val path = Path()
            p.color = dark
            path.moveTo(16f, apex)
            path.lineTo(16f + hw, baseY)
            path.lineTo(16f + hw * 0.6f, baseY + 2.8f)
            path.lineTo(16f - hw * 0.6f, baseY + 2.8f)
            path.lineTo(16f - hw, baseY)
            path.close()
            cv.drawPath(path, p)
            p.color = mid
            path.reset()
            path.moveTo(16f, apex)
            path.lineTo(16f + hw * 0.52f, baseY)
            path.lineTo(16f - hw * 0.52f, baseY)
            path.close()
            cv.drawPath(path, p)
            px(cv, p, 15.1f, apex, 1.8f, 1.6f, lite)
        }

        /** 침엽 다발 — 잣나무처럼 한 점에서 바늘이 여러 개 퍼지는 모양 */
        fun needles(cx: Float, cy: Float, len: Float, col: Int, n: Int = 5) {
            for (i in 0 until n) {
                val a = -1.4f + i * (2.8f / (n - 1).coerceAtLeast(1))
                var t = 1f
                while (t <= len) {
                    px(cv, p, cx + cos(a) * t - 0.55f, cy + sin(a) * t - 0.55f, 1.1f, 1.1f, col)
                    t += 1.1f
                }
            }
        }

        /** 잎 한 장 — 타원 + 중맥 */
        fun leaf(cx: Float, cy: Float, w: Float, h: Float, col: Int, rib: Int) {
            p.color = col
            cv.drawOval(RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), p)
            px(cv, p, cx - w / 2f + 0.6f, cy - 0.4f, w - 1.2f, 0.9f, rib)
        }

        /** 깃모양 잎사슬 — 겹잎(물푸레·물오리)의 작은 잎들을 가지 따라 늘어놓는다 */
        fun pinnate(x0: Float, y0: Float, x1: Float, y1: Float, n: Int, col: Int, rib: Int, size: Float = 2.6f) {
            for (i in 0 until n) {
                val t = i / (n - 1f)
                leaf(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, size, size * 0.72f, col, rib)
            }
        }

        /** 수관 블롭 — 어두운 바닥 → 중간 → 윗면 하이라이트 순으로 겹쳐 입체감을 만든다 */
        fun crown(cx: Float, cy: Float, rad: Float, dark: Int, mid: Int, lite: Int) {
            p.color = dark; cv.drawCircle(cx, cy, rad, p)
            p.color = mid; cv.drawCircle(cx - rad * 0.32f, cy - rad * 0.36f, rad * 0.72f, p)
            cv.drawCircle(cx + rad * 0.4f, cy + rad * 0.22f, rad * 0.55f, p)
            p.color = lite; cv.drawCircle(cx - rad * 0.42f, cy - rad * 0.52f, rad * 0.38f, p)
        }

        /** 열매 한 알 — 본체 + 하이라이트 */
        fun fruit(cx: Float, cy: Float, rad: Float, col: Int, hi: Int) {
            p.color = col; cv.drawCircle(cx, cy, rad, p)
            p.color = hi; cv.drawCircle(cx - rad * 0.3f, cy - rad * 0.35f, rad * 0.42f, p)
        }

        when (look) {
            0 -> { // 가문비나무 — 아주 키 큰 침엽, 층층이 처지는 가지
                propShadow(cv, p, 16f, 28.6f, 6.5f, 2.4f)
                px(cv, p, 14.9f, 22f, 2.6f, 8.5f, bark)
                px(cv, p, 15.7f, 22f, 1.1f, 8.5f, barkMid)
                tier(1.5f, 12f, 4.6f, c(0xFF1D4636), c(0xFF2C6344), c(0xFF3F8054))
                tier(7.5f, 18f, 7.4f, c(0xFF1D4636), c(0xFF2C6344), c(0xFF3F8054))
                tier(14f, 24f, 10.2f, c(0xFF1D4636), c(0xFF2C6344), c(0xFF3F8054))
                tier(21f, 30f, 12.8f, c(0xFF1D4636), c(0xFF2C6344), c(0xFF3F8054))
                noise(cv, p, r, 5f, 2f, 27f, 28f, c(0xFF153627), 8, 1f, 1.6f)
                noise(cv, p, r, 5f, 2f, 27f, 28f, c(0xFF4A9463), 6, 1f, 1.4f)
            }
            1 -> { // 잣나무 — 연한 청록색 긴 침엽이 다발로 트인 수관
                propShadow(cv, p, 16f, 28.6f, 8.5f, 2.8f)
                px(cv, p, 14.4f, 15f, 3.4f, 14f, c(0xFF6B5334))
                px(cv, p, 15.4f, 15f, 1.4f, 14f, c(0xFF8A6B42))
                noise(cv, p, r, 14f, 15f, 18f, 29f, c(0xFF54402A), 5, 1f, 1.4f)
                // 마디에서 세 방향으로 뻗는 굵은 가지 — 수관이 트여 보이게
                for ((bx, by) in listOf(11f to 9f, 21f to 8f, 16f to 4f)) {
                    val steps = 6
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        px(cv, p, 16f + (bx - 16f) * t, 15f + (by - 15f) * t, 1.2f, 1.2f, c(0xFF6B5334))
                    }
                }
                // 침엽 다발 — 한 점에서 바늘이 여러 개 퍼진다
                for ((cx, cy, len) in listOf(
                    Triple(11f, 9f, 4.4f), Triple(21f, 8f, 4.4f), Triple(16f, 4f, 4f),
                    Triple(8f, 14f, 3.2f), Triple(24f, 13f, 3.2f),
                    Triple(13.5f, 12f, 2.6f), Triple(19f, 12f, 2.6f)
                )) {
                    needles(cx, cy, len, c(0xFF6FA05F))
                    needles(cx, cy, len * 0.7f, c(0xFF93BE74))
                }
                noise(cv, p, r, 6f, 2f, 26f, 20f, c(0xFFB9D28C), 6, 1f, 1.3f)
            }
            2 -> { // 주목 — 납작하게 넓게 퍼지는 짙은 수관 + 붉은 씨눈(가시아)
                propShadow(cv, p, 16f, 28.6f, 12f, 3.2f)
                px(cv, p, 14.6f, 20f, 3f, 9f, bark)
                px(cv, p, 15.5f, 20f, 1.2f, 9f, barkMid)
                p.color = c(0xFF1E3D2A); cv.drawOval(RectF(3.5f, 6f, 28.5f, 20f), p)
                p.color = c(0xFF2E5738); cv.drawCircle(12f, 11f, 7.5f, p); cv.drawCircle(21f, 13f, 7.5f, p)
                p.color = c(0xFF417A46); cv.drawCircle(16f, 8f, 5.5f, p)
                noise(cv, p, r, 4f, 6f, 28f, 19f, c(0xFF16301F), 10, 1f, 1.6f)
                noise(cv, p, r, 4f, 6f, 28f, 19f, c(0xFF54986A), 6, 1f, 1.4f)
                for ((fx, fy) in listOf(9f to 12f, 22f to 10f, 17f to 15f, 12f to 17f, 24f to 16f)) {
                    fruit(fx, fy, 1.5f, c(0xFFD9453F), c(0xFFF08A7E))
                }
            }
            3 -> { // 노간주나무 — 바람에 비틀린 줄기, 비늘 같은 잎, 청회색 열매
                propShadow(cv, p, 16f, 28.4f, 8f, 2.6f)
                val steps = listOf(16f to 30f, 15f to 27f, 16.5f to 24f, 14.5f to 21f, 13f to 18f, 12f to 15.5f)
                for ((i, st) in steps.withIndex()) {
                    val w = 3.4f - i * 0.35f
                    px(cv, p, st.first - w / 2f, st.second, w, 3.4f, if (i % 2 == 0) bark else barkMid)
                }
                noise(cv, p, r, 10f, 14f, 20f, 30f, c(0xFF4A2D18), 5, 1f, 1.4f)
                p.color = c(0xFF3D6B4A)
                cv.drawCircle(8f, 13f, 3.4f, p); cv.drawCircle(17f, 9f, 3f, p); cv.drawCircle(24f, 15f, 3.2f, p)
                p.color = c(0xFF598A5C)
                cv.drawCircle(7f, 12f, 2f, p); cv.drawCircle(18f, 8f, 1.8f, p); cv.drawCircle(25f, 14f, 1.8f, p)
                for ((bx, by) in listOf(6f to 17f, 20f to 12f, 26f to 19f)) fruit(bx, by, 1.2f, c(0xFF8FA3C4), c(0xFFC3D2E8))
            }
            4 -> { // 후박나무 — 남해안 상록수, 녹색을 띤 새순이 돋는다
                propShadow(cv, p, 16f, 28.6f, 9.5f, 3f)
                px(cv, p, 14.2f, 18f, 3.8f, 11f, bark)
                px(cv, p, 15.2f, 18f, 1.6f, 11f, barkMid)
                noise(cv, p, r, 14f, 18f, 18f, 29f, c(0xFF4A2D18), 4, 1f, 1.3f)
                crown(16f, 12f, 10.5f, c(0xFF1F4A33), c(0xFF2F6B41), c(0xFF41784A))
                for ((nx, ny) in listOf(9f to 9f, 22f to 8f, 7f to 15f, 24f to 15f, 13f to 5f, 19f to 17f)) {
                    px(cv, p, nx, ny, 2.2f, 1.2f, c(0xFFB5642E))
                    px(cv, p, nx + 0.3f, ny + 1.1f, 1.6f, 0.8f, c(0xFFD08A44))
                }
            }
            5 -> { // 붉가시나무 — 남부 상록참나무, 광택 있는 톱니 잎
                propShadow(cv, p, 16f, 28.6f, 9.5f, 3f)
                px(cv, p, 14.2f, 19f, 3.6f, 10f, bark)
                px(cv, p, 15.3f, 19f, 1.5f, 10f, barkMid)
                crown(16f, 12f, 10.5f, c(0xFF24513A), c(0xFF37714A), c(0xFF54996A))
                // 톱니 잎맥 — 수관 가장자리를 들쑥날쑥하게 깎아 광택 잎처럼 읽히게
                for (i in 0 until 14) {
                    val a = i * (6.2832f / 14)
                    val ex = 16f + cos(a) * 10.2f
                    val ey = 12f + sin(a) * 10.2f
                    px(cv, p, ex, ey, 1.6f, 1.6f, if (i % 2 == 0) c(0xFF173B2A) else c(0xFF6FB87E))
                }
                fruit(21f, 19f, 1.4f, c(0xFF9A6A3E), c(0xFFC08E56))
                fruit(11f, 20f, 1.3f, c(0xFF9A6A3E), c(0xFFC08E56))
            }
            6 -> { // 신갈나무 — 거친 나무껍질에 큰 둥근 수관
                propShadow(cv, p, 16f, 28.6f, 10.5f, 3.2f)
                px(cv, p, 13f, 14f, 6f, 15f, bark)
                px(cv, p, 14.2f, 14f, 3.2f, 15f, barkMid)
                px(cv, p, 15f, 15f, 1.2f, 13f, barkLite)
                noise(cv, p, r, 13f, 14f, 19f, 29f, c(0xFF4A2D18), 8, 1f, 1.8f)
                px(cv, p, 11.6f, 28f, 3.4f, 2.6f, bark)
                px(cv, p, 17.6f, 28f, 3.4f, 2.6f, bark)
                crown(16f, 10f, 12f, c(0xFF2E5D33), c(0xFF3F7D46), c(0xFF57A55F))
                noise(cv, p, r, 5f, 2f, 27f, 21f, c(0xFF2C5429), 12, 1f, 1.8f)
                noise(cv, p, r, 5f, 2f, 27f, 21f, c(0xFF7FD184), 8, 1f, 1.6f)
            }
            7 -> { // 상수리나무 — 갈라진 잎사귀 + 도토리
                propShadow(cv, p, 16f, 28.6f, 9f, 3f)
                px(cv, p, 14.2f, 18f, 3.6f, 11f, bark)
                px(cv, p, 15.2f, 18f, 1.5f, 11f, barkMid)
                p.color = c(0xFF2E5D33); cv.drawCircle(16f, 13f, 9.5f, p)
                p.color = c(0xFF3F7D46)
                cv.drawCircle(10f, 8f, 4.4f, p); cv.drawCircle(22f, 8f, 4.4f, p)
                cv.drawCircle(8f, 15f, 4f, p); cv.drawCircle(24f, 15f, 4f, p)
                cv.drawCircle(16f, 5f, 4.4f, p)
                p.color = c(0xFF57A55F); cv.drawCircle(16f, 11f, 4f, p)
                p.color = c(0xFF6FBF72); cv.drawCircle(13f, 8f, 2f, p)
                noise(cv, p, r, 6f, 4f, 26f, 21f, c(0xFF2C5429), 8, 1f, 1.5f)
                // 도토리 — 도토리껍질 + 열매
                for ((ax, ay) in listOf(9f to 19f, 22f to 18f)) {
                    px(cv, p, ax, ay + 1.4f, 2.4f, 1.2f, c(0xFF6B4A2A))
                    fruit(ax + 1.2f, ay, 1.3f, c(0xFFB0793F), c(0xFFD8A566))
                }
            }
            8 -> { // 떡갈나무 — 한반도 최대의 잎, 낮고 아주 넓게 퍼지는 수관
                propShadow(cv, p, 16f, 28.4f, 13.5f, 3.2f)
                px(cv, p, 14.4f, 22f, 3.4f, 7.5f, bark)
                px(cv, p, 15.3f, 22f, 1.4f, 7.5f, barkMid)
                p.color = c(0xFF2A5530); cv.drawOval(RectF(1.5f, 6f, 30.5f, 21f), p)
                p.color = c(0xFF3A7442); cv.drawOval(RectF(3.5f, 8f, 28.5f, 19.5f), p)
                p.color = c(0xFF57A55F); cv.drawCircle(9f, 8f, 4.5f, p); cv.drawCircle(22f, 9f, 4.5f, p)
                // 큼직한 잎 — 중맥이 길게 들어간 타원을 수관 위에 얹는다
                leaf(8f, 13f, 9f, 6f, c(0xFF3F7D46), c(0xFF7FD184))
                leaf(22f, 12f, 10f, 6.4f, c(0xFF3F7D46), c(0xFF7FD184))
                leaf(15f, 17f, 8f, 5.4f, c(0xFF4E9455), c(0xFF93DA93))
                noise(cv, p, r, 4f, 7f, 28f, 20f, c(0xFF2C5429), 10, 1f, 1.7f)
                // 떨어진 잎
                px(cv, p, 5f, 29f, 3f, 1.4f, c(0xFF6FAE57))
                px(cv, p, 25f, 29.6f, 3f, 1.4f, c(0xFF6FAE57))
            }
            9 -> { // 느티나무 — 마을 당산나무, 곧게 솟은 줄기에 위가 퍼진 수관
                propShadow(cv, p, 16f, 28.6f, 7f, 2.6f)
                px(cv, p, 14.6f, 9f, 2.9f, 20.5f, bark)
                px(cv, p, 15.5f, 9f, 1.2f, 20.5f, barkMid)
                px(cv, p, 15.9f, 11f, 0.9f, 17f, barkLite)
                px(cv, p, 13.4f, 27f, 2.6f, 3f, bark)
                px(cv, p, 16.6f, 27f, 2.6f, 3f, bark)
                noise(cv, p, r, 14f, 9f, 18f, 29f, c(0xFF4A2D18), 6, 1f, 1.5f)
                val path = Path()
                p.color = c(0xFF33602F)
                path.moveTo(14.8f, 15f)
                path.lineTo(4.5f, 6f); path.quadTo(2.5f, 1.5f, 8f, 2f)
                path.lineTo(24f, 2f); path.quadTo(29.5f, 1.5f, 27.5f, 6f)
                path.lineTo(17.2f, 15f)
                path.close()
                cv.drawPath(path, p)
                p.color = c(0xFF3F7D46)
                cv.drawCircle(11f, 6f, 4.6f, p); cv.drawCircle(21f, 6f, 4.6f, p); cv.drawCircle(16f, 4f, 4.2f, p)
                p.color = c(0xFF57A55F)
                cv.drawCircle(10f, 4f, 2.6f, p); cv.drawCircle(22f, 5f, 2.2f, p); cv.drawCircle(16f, 3f, 2.4f, p)
                noise(cv, p, r, 4f, 2f, 28f, 14f, c(0xFF2C5429), 10, 1f, 1.5f)
                noise(cv, p, r, 4f, 2f, 28f, 14f, c(0xFF7FD184), 6, 1f, 1.4f)
            }
            10 -> { // 물푸레나무 — 마주난 가지에 겹잎을 다는 키 큰 활엽
                propShadow(cv, p, 16f, 28.6f, 7.5f, 2.6f)
                px(cv, p, 14.7f, 11f, 2.8f, 18.5f, bark)
                px(cv, p, 15.6f, 11f, 1.1f, 18.5f, barkMid)
                noise(cv, p, r, 14f, 11f, 18f, 29f, c(0xFF4A2D18), 5, 1f, 1.4f)
                // 마주난 가지 3쌍
                for ((by, spread) in listOf(15f to 6.5f, 19.5f to 8.5f, 24f to 6f)) {
                    px(cv, p, 15.9f, by, 0.9f, 0.9f, barkMid)
                    px(cv, p, 16f - spread - 2f, by - 1.2f, spread + 2f, 1f, barkMid)
                    px(cv, p, 16f, by - 1.2f, spread + 2f, 1f, barkMid)
                }
                pinnate(9.5f, 14f, 5f, 6f, 5, c(0xFF4E8C4E), c(0xFF6FAE5B))
                pinnate(22.5f, 14f, 27f, 6f, 5, c(0xFF4E8C4E), c(0xFF6FAE5B))
                pinnate(8f, 19f, 4f, 14f, 4, c(0xFF5C9A54), c(0xFF86C072))
                pinnate(24f, 19f, 28f, 14f, 4, c(0xFF5C9A54), c(0xFF86C072))
                pinnate(16f, 10f, 16f, 4f, 4, c(0xFF6FAE5B), c(0xFF93C97E))
            }
            11 -> { // 밤나무 — 넓은 수관 + 밤송이
                propShadow(cv, p, 16f, 28.6f, 11f, 3.2f)
                px(cv, p, 13.8f, 18f, 4.4f, 11f, bark)
                px(cv, p, 14.9f, 18f, 1.9f, 11f, barkMid)
                noise(cv, p, r, 14f, 18f, 18f, 29f, c(0xFF4A2D18), 6, 1f, 1.6f)
                crown(16f, 11f, 11f, c(0xFF2F6033), c(0xFF3F7D46), c(0xFF57A55F))
                noise(cv, p, r, 5f, 3f, 27f, 21f, c(0xFF2C5429), 10, 1f, 1.7f)
                // 길고 톱니진 잎
                leaf(9f, 8f, 7f, 4.4f, c(0xFF4E9455), c(0xFF8FD68F))
                leaf(22f, 9f, 7.5f, 4.6f, c(0xFF4E9455), c(0xFF8FD68F))
                leaf(15f, 4f, 6.5f, 4f, c(0xFF57A55F), c(0xFF9BE09B))
                // 밤송이 — 가시가 달린 겉껍질
                for ((bx, by) in listOf(8f to 19f, 23f to 18f)) {
                    p.color = c(0xFF6B4A2A); cv.drawCircle(bx, by, 2.2f, p)
                    p.color = c(0xFF8A6B42)
                    px(cv, p, bx - 2.6f, by - 1f, 1.2f, 1.2f, p.color)
                    px(cv, p, bx + 1.6f, by - 1.4f, 1.2f, 1.2f, p.color)
                    px(cv, p, bx - 0.6f, by + 1.6f, 1.2f, 1.2f, p.color)
                    px(cv, p, bx - 1.6f, by + 0.6f, 1.2f, 1.2f, p.color)
                    px(cv, p, bx + 1.4f, by + 0.8f, 1.2f, 1.2f, p.color)
                }
            }
            12 -> { // 아까시나무 — 성긴 겹잎 사이로 흰 꽃차례가 늘어진다
                propShadow(cv, p, 16f, 28.6f, 8.5f, 2.8f)
                px(cv, p, 14.4f, 17f, 3.4f, 12f, bark)
                px(cv, p, 15.3f, 17f, 1.4f, 12f, barkMid)
                val twigs = listOf(
                    Triple(15.6f, 16f, 15.6f to 5f), Triple(15.2f, 15f, 7f to 11f),
                    Triple(16.2f, 15f, 25f to 11f), Triple(15.4f, 18f, 10f to 20f),
                    Triple(16.2f, 18f, 22f to 20f)
                )
                for ((x0, y0, end) in twigs) {
                    val steps = 9
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val bow = sin(t * 3.1416f) * (if (end.first > x0) 1.6f else -1.6f)
                        px(cv, p, x0 + (end.first - x0) * t + bow - 0.4f, y0 + (end.second - y0) * t, 0.9f, 0.9f, barkMid)
                    }
                }
                for ((x0, y0, end) in twigs) {
                    pinnate(x0, y0, end.first, end.second, 5, c(0xFF4F8C4A), c(0xFF74AA5C), 2.2f)
                }
                // 흰 꽃차례 — 아래로 드리운 꽃알갱이
                for ((hx, hy) in listOf(9f to 12f, 23f to 12f)) {
                    for (i in 0 until 5) px(cv, p, hx + (i % 2) * 0.9f, hy + i * 1.5f, 1.3f, 1.3f, c(0xFFF6F2E2))
                }
                noise(cv, p, r, 5f, 5f, 27f, 22f, c(0xFF86C072), 8, 1f, 1.3f)
            }
            13 -> { // 감나무 — 꼬부란 줄기에 주황 감이 달린다
                propShadow(cv, p, 16f, 28.6f, 8f, 2.8f)
                val knot = listOf(16f to 29f, 15f to 26f, 17f to 23f, 15.5f to 20f, 14.5f to 17f)
                for ((i, st) in knot.withIndex()) {
                    val w = 3.8f - i * 0.3f
                    px(cv, p, st.first - w / 2f, st.second, w, 3.2f, if (i % 2 == 0) bark else barkMid)
                }
                px(cv, p, 12.6f, 25f, 2.4f, 2f, barkMid)   // 옆으로 뻗은 굵은 가지
                px(cv, p, 17.8f, 22f, 2.2f, 2f, barkMid)
                noise(cv, p, r, 12f, 16f, 20f, 29f, c(0xFF4A2D18), 7, 1f, 1.5f)
                crown(15f, 10f, 9f, c(0xFF2F6033), c(0xFF3F7D46), c(0xFF57A55F))
                noise(cv, p, r, 6f, 3f, 25f, 19f, c(0xFF2C5429), 9, 1f, 1.5f)
                for ((fx, fy) in listOf(8f to 12f, 12f to 8f, 21f to 11f, 19f to 15f)) fruit(fx, fy, 1.7f, c(0xFFE8843C), c(0xFFF7B45E))
            }
            14 -> { // 물오리나무 — 강변의 긴 깃모양 잎과 열매사슬
                propShadow(cv, p, 16f, 28.6f, 10f, 3f)
                px(cv, p, 14.2f, 18f, 3.8f, 11f, bark)
                px(cv, p, 15.2f, 18f, 1.6f, 11f, barkMid)
                crown(16f, 11f, 10f, c(0xFF2C5C33), c(0xFF3A7442), c(0xFF57A55F))
                pinnate(16f, 12f, 4f, 4f, 7, c(0xFF3F7D46), c(0xFF7FD184), 2.8f)
                pinnate(16f, 12f, 28f, 4f, 7, c(0xFF3F7D46), c(0xFF7FD184), 2.8f)
                pinnate(16f, 13f, 9f, 22f, 6, c(0xFF4E9455), c(0xFF8FD68F), 2.6f)
                pinnate(16f, 13f, 23f, 22f, 6, c(0xFF4E9455), c(0xFF8FD68F), 2.6f)
                noise(cv, p, r, 5f, 4f, 27f, 21f, c(0xFF2C5429), 9, 1f, 1.5f)
                // 열매串 — 아래로 늘어진 작은 알갱이 사슬
                for ((cx, cy) in listOf(5f to 17f, 27f to 18f)) {
                    for (i in 0 until 4) px(cv, p, cx + (i % 2) * 1f, cy + i * 1.4f, 1.2f, 1.2f, c(0xFFA88B4E))
                }
            }
            15 -> { // 회화나무(플라타너스) — 도심 가로수, 얼룩 덜룩한 껍질
                propShadow(cv, p, 16f, 28.6f, 10.5f, 3.2f)
                px(cv, p, 13.8f, 15f, 4.6f, 14f, c(0xFF9A9484))
                px(cv, p, 14.9f, 15f, 2.1f, 14f, c(0xFFB8B2A0))
                px(cv, p, 13.8f, 18f, 4.6f, 2.4f, c(0xFF7E8A62))     // 벗겨진 녹색
                px(cv, p, 15.6f, 23f, 2.6f, 2.2f, c(0xFFC8C4B0))
                px(cv, p, 14.2f, 12f, 3.4f, 2.4f, c(0xFF8A8474))
                px(cv, p, 16.4f, 8f, 2.2f, 3.4f, c(0xFF7E8A62))
                noise(cv, p, r, 13f, 15f, 19f, 29f, c(0xFF6E6A5E), 6, 1f, 1.5f)
                crown(16f, 10f, 11.5f, c(0xFF2F6636), c(0xFF3F7D46), c(0xFF57A55F))
                noise(cv, p, r, 5f, 3f, 27f, 20f, c(0xFF2C5429), 10, 1f, 1.6f)
                // 단풍처럼 갈라진 잎사귀
                leaf(9f, 7f, 6.4f, 4.2f, c(0xFF4E9455), c(0xFF8FD68F))
                leaf(23f, 8f, 6.4f, 4.2f, c(0xFF4E9455), c(0xFF8FD68F))
                leaf(16f, 4f, 6f, 4f, c(0xFF57A55F), c(0xFF9BE09B))
                // 구과(열매 공)
                for ((sx, sy) in listOf(9f to 19f, 23f to 18f)) {
                    p.color = c(0xFFB09A5E); cv.drawCircle(sx, sy, 1.7f, p)
                    p.color = c(0xFFD2C08A); cv.drawCircle(sx - 0.5f, sy - 0.6f, 0.7f, p)
                }
            }
            16 -> { // 메타세콰이아 — 아주 곧고 키 납작한 낙침, 가지가 수평으로 퍼진다
                propShadow(cv, p, 16f, 28.6f, 5.5f, 2.2f)
                px(cv, p, 14.9f, 6f, 2.4f, 23f, bark)
                px(cv, p, 15.7f, 6f, 1f, 23f, barkMid)
                px(cv, p, 13.6f, 26f, 5f, 3f, bark)      // 뿌리 받침
                px(cv, p, 14.2f, 27.5f, 4f, 2f, barkMid)
                noise(cv, p, r, 14f, 6f, 18f, 29f, c(0xFF4A2D18), 6, 1f, 1.5f)
                // 수평으로 퍼지는 가지 + 깃 모양 잎사슬
                for (row in 0 until 6) {
                    val y = 3f + row * 4.2f
                    val half = 3.2f + row * 1.7f
                    px(cv, p, 16f - half, y, half, 0.8f, barkMid)
                    px(cv, p, 16f, y, half, 0.8f, barkMid)
                    for (i in 0 until 4) {
                        val t = i / 3f
                        px(cv, p, 16f - half + half * t, y - 1.6f, 0.9f, 1.8f, c(0xFF6FAE5B))
                        px(cv, p, 16f + half * t, y - 1.6f, 0.9f, 1.8f, c(0xFF6FAE5B))
                        px(cv, p, 16f - half + half * t - 0.6f, y - 0.8f, 0.8f, 1.2f, c(0xFF8FC470))
                        px(cv, p, 16f + half * t - 0.4f, y - 0.8f, 0.8f, 1.2f, c(0xFF8FC470))
                    }
                }
            }
            17 -> { // 산벚나무 — 작은 야생 벚나무, 연한 분홍 꽃
                propShadow(cv, p, 16f, 28.6f, 7.5f, 2.6f)
                px(cv, p, 14.6f, 20f, 2.8f, 9f, bark)
                px(cv, p, 15.4f, 20f, 1.2f, 9f, barkMid)
                px(cv, p, 13.4f, 23f, 2.2f, 1.6f, barkMid)
                px(cv, p, 16.6f, 24f, 2.2f, 1.6f, barkMid)
                p.color = c(0xFFD97F99); cv.drawCircle(16f, 13f, 7.4f, p)
                p.color = c(0xFFF2A3B3); cv.drawCircle(13f, 11f, 5.2f, p); cv.drawCircle(20f, 14f, 4.8f, p)
                p.color = c(0xFFFFC9D6); cv.drawCircle(15f, 9f, 3.4f, p)
                noise(cv, p, r, 9f, 7f, 24f, 19f, c(0xFFFFE0E8), 12, 1f, 1.5f)
                noise(cv, p, r, 9f, 7f, 24f, 19f, c(0xFFC96B87), 7, 1f, 1.4f)
                px(cv, p, 8f, 26f, 2f, 1.2f, c(0xFFF2A3B3))
                px(cv, p, 23f, 28f, 2f, 1.2f, c(0xFFFFC9D6))
            }
            18 -> { // 진달래 — 절반 타일 크기의 관목, 분홍 꽃이 덮인다
                propShadow(cv, p, 16f, 28.4f, 9f, 2.6f)
                for ((sx, sy) in listOf(9f to 24f, 12f to 22f, 16f to 21f, 20f to 23f, 23f to 24f)) {
                    px(cv, p, sx, sy, 1.5f, 30f - sy, c(0xFF6B4A2A))
                }
                px(cv, p, 10f, 22f, 1.2f, 5f, c(0xFF3F7D46))     // 잎사귀 몇 점
                px(cv, p, 21f, 23f, 1.2f, 4f, c(0xFF3F7D46))
                p.color = c(0xFFE2779B); cv.drawCircle(16f, 16f, 7f, p)
                p.color = c(0xFFF2A9BE); cv.drawCircle(11.5f, 15f, 4.8f, p); cv.drawCircle(20.5f, 16f, 4.8f, p)
                p.color = c(0xFFFFCFDC); cv.drawCircle(16f, 11f, 3.8f, p); cv.drawCircle(13f, 13f, 2.6f, p)
                noise(cv, p, r, 9f, 9f, 24f, 21f, c(0xFFFFE3EC), 10, 1f, 1.4f)
                noise(cv, p, r, 9f, 9f, 24f, 21f, c(0xFFC4567E), 6, 1f, 1.3f)
            }
            19 -> { // 감귤나무 — 제주 상록수, 짙은 잎에 귤이 주렁주렁
                propShadow(cv, p, 16f, 28.6f, 8f, 2.6f)
                px(cv, p, 14.6f, 21f, 2.8f, 8.5f, bark)
                px(cv, p, 15.4f, 21f, 1.2f, 8.5f, barkMid)
                p.color = c(0xFF24513A); cv.drawCircle(16f, 15f, 8f, p)
                p.color = c(0xFF2F6B41); cv.drawCircle(12f, 14f, 5.4f, p); cv.drawCircle(20f, 16f, 5.2f, p)
                p.color = c(0xFF41784A); cv.drawCircle(16f, 11f, 3.8f, p)
                noise(cv, p, r, 8f, 8f, 25f, 21f, c(0xFF163B2A), 8, 1f, 1.4f)
                for ((fx, fy) in listOf(9f to 17f, 14f to 13f, 22f to 15f, 18f to 19f)) fruit(fx, fy, 1.7f, c(0xFFEE8B2E), c(0xFFF7BE6A))
                px(cv, p, 12f, 10f, 1.4f, 1.4f, c(0xFFF6F2E4))   // 귤꽃
                px(cv, p, 20f, 11f, 1.4f, 1.4f, c(0xFFF6F2E4))
            }
            20 -> { // 야자나무 — 가는 줄기에 부채꼴 잎이 펼쳐진다
                propShadow(cv, p, 16f, 28.4f, 6f, 2.4f)
                for (i in 0 until 9) {           // 마디 흔적이 남는 가는 줄기
                    val y = 6f + i * 2.7f
                    px(cv, p, 15.2f, y, 1.7f, 2.3f, c(0xFF8A7A52))
                    px(cv, p, 15.4f, y, 0.7f, 2.3f, c(0xFFA8976A))
                    px(cv, p, 14.9f, y + 1.5f, 2.4f, 0.8f, c(0xFF6E5F3C))
                }
                // 부채꼴 잎 7장 — 밖으로 뻗었다가 아래로 휘어진다
                for (i in 0 until 7) {
                    val a = -2.45f + i * (4.9f / 6f)
                    val reach = if (i == 0 || i == 6) 11f else 14.5f
                    var prevX = 16f
                    var prevY = 6f
                    var t = 2.5f
                    while (t <= reach) {
                        val nx = 16f + cos(a) * t
                        val ny = 6f + sin(a) * t + (t - 2.5f) * (t - 2.5f) * 0.17f
                        val col = if ((t.toInt()) % 3 == 0) c(0xFF57A862) else c(0xFF2F7A46)
                        px(cv, p, minOf(prevX, nx) - 0.8f, minOf(prevY, ny) - 0.8f,
                            abs(nx - prevX) + 1.6f, abs(ny - prevY) + 1.6f, col)
                        // 잎맥 — 잎대에 수직으로 짧게 세운 잎 조각
                        val dx = nx - prevX
                        val dy = ny - prevY
                        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.6f)
                        val nxu = -dy / len
                        val nyu = dx / len
                        for (s in intArrayOf(-1, 1)) {
                            px(cv, p, nx + nxu * 1.5f * s - 0.45f, ny + nyu * 1.5f * s - 0.45f,
                                0.9f, 0.9f, if (s > 0) c(0xFF6FBF72) else c(0xFF24593A))
                        }
                        prevX = nx; prevY = ny
                        t += 1.5f
                    }
                }
                // 왕관 아래 털북숭이 잔재
                for (i in 0 until 5) px(cv, p, 13f + i * 1.1f, 6.5f + (i % 2) * 0.8f, 0.8f, 1.6f, c(0xFF7A6B45))
            }
            else -> { // 메타세콰이아(가을) — 깃 모양 잎이 붉게 물든다
                propShadow(cv, p, 16f, 28.6f, 5.5f, 2.2f)
                px(cv, p, 14.9f, 6f, 2.4f, 23f, bark)
                px(cv, p, 15.7f, 6f, 1f, 23f, barkMid)
                px(cv, p, 13.6f, 26f, 5f, 3f, bark)
                px(cv, p, 14.2f, 27.5f, 4f, 2f, barkMid)
                noise(cv, p, r, 14f, 6f, 18f, 29f, c(0xFF4A2D18), 6, 1f, 1.5f)
                for (row in 0 until 6) {
                    val y = 3f + row * 4.2f
                    val half = 3.2f + row * 1.7f
                    px(cv, p, 16f - half, y, half, 0.8f, barkMid)
                    px(cv, p, 16f, y, half, 0.8f, barkMid)
                    for (i in 0 until 4) {
                        val t = i / 3f
                        val warm = if ((row + i) % 3 == 0) c(0xFFF2A34E) else if ((row + i) % 3 == 1) c(0xFFD9534F) else c(0xFFE8823C)
                        px(cv, p, 16f - half + half * t, y - 1.6f, 0.9f, 1.8f, warm)
                        px(cv, p, 16f + half * t, y - 1.6f, 0.9f, 1.8f, warm)
                        px(cv, p, 16f - half + half * t - 0.6f, y - 0.8f, 0.8f, 1.2f, c(0xFFF7CE5B))
                        px(cv, p, 16f + half * t - 0.4f, y - 0.8f, 0.8f, 1.2f, c(0xFFF7CE5B))
                    }
                }            }
        }
    }

    /**
     * 계절 전용 나무 5종 — T.TREE 변형 인덱스 12·13·14 · 37·38.
     * 0 앙상한 활엽수(가지뿐) · 1 눈 덮인 활엽수 · 2 눈 덮인 소나무 ·
     * 3 눈 덮인 가문비나무 · 4 눈 덮인 진달래.     */
    private fun seasonTreeArt(look: Int): Bitmap = tilePainter { cv, p, r ->
        propShadow(cv, p, 16f, 28f, 10.5f, 3f)
        val bark = c(0xFF5D3A20)
        val barkLite = c(0xFF7A4E2B)
        val snow = c(0xFFF4F8FC)
        val snowShade = c(0xFFD8E4EE)
        when (look) {
            0, 1 -> { // 앙상한 활엽수 (+ 눈)
                px(cv, p, 13.4f, 15f, 6.4f, 16f, bark)
                px(cv, p, 14.6f, 15f, 2.8f, 16f, barkLite)
                px(cv, p, 12.4f, 28f, 3f, 3.4f, bark)
                px(cv, p, 18.2f, 28f, 3f, 3.4f, bark)
                // 굵은 가지 — 위로 갈수록 가늘게
                fun branch(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
                    val dx = x1 - x0
                    val dy = y1 - y0
                    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    var u = 0f
                    while (u <= len) {
                        px(cv, p, x0 + dx * u / len - w / 2f, y0 + dy * u / len - w / 2f, w, w, bark)
                        u += w * 0.7f
                    }
                }
                branch(16f, 18f, 8f, 8f, 3f)
                branch(16f, 16f, 24f, 7f, 3f)
                branch(16f, 13f, 11f, 3f, 2.2f)
                branch(16f, 12f, 21f, 2f, 2.2f)
                branch(16f, 10f, 16f, 0f, 2f)
                branch(11f, 12f, 5f, 12f, 1.6f)
                branch(21f, 11f, 27f, 11f, 1.6f)
                branch(8f, 8f, 4f, 3f, 1.4f)
                branch(24f, 7f, 28f, 2f, 1.4f)
                if (look == 1) {
                    // 가지 위에 얹힌 눈
                    for ((x, y, w) in listOf(
                        Triple(6f, 6f, 5f), Triple(22f, 5f, 5f), Triple(9f, 1f, 4f),
                        Triple(19f, 1f, 4f), Triple(14f, 0f, 4f), Triple(4f, 11f, 3f),
                        Triple(25f, 10f, 3f)
                    )) {
                        px(cv, p, x, y, w, 1.8f, snow)
                        px(cv, p, x + 0.4f, y + 1.8f, w - 0.8f, 1f, snowShade)
                    }
                    // 밑동 눈더미
                    p.color = snow
                    cv.drawOval(RectF(5f, 27f, 27f, 32f), p)
                } else {
                    noise(cv, p, r, 13f, 16f, 20f, 30f, c(0xFF4A2D18), 5, 1f, 1.5f)
                }
            }
            2 -> { // 눈 덮인 소나무
                p.color = Color.argb(58, 26, 46, 28)
                cv.drawOval(RectF(6f, 25f, 28f, 31f), p)
                px(cv, p, 14.4f, 20f, 4.4f, 11f, bark)
                px(cv, p, 15.2f, 20f, 2f, 11f, barkLite)
                val path = Path()
                p.color = c(0xFF24512F)
                path.moveTo(16f, -1f); path.lineTo(27f, 13f); path.lineTo(5f, 13f); path.close()
                cv.drawPath(path, p)
                p.color = c(0xFF2F6B3B)
                path.reset()
                path.moveTo(16f, 6f); path.lineTo(29f, 21f); path.lineTo(3f, 21f); path.close()
                cv.drawPath(path, p)
                p.color = c(0xFF24512F)
                path.reset()
                path.moveTo(16f, 13f); path.lineTo(31f, 29f); path.lineTo(1f, 29f); path.close()
                cv.drawPath(path, p)
                // 층층이 쌓인 눈
                px(cv, p, 13f, 0f, 6f, 2.4f, snow)
                px(cv, p, 9f, 6f, 14f, 2.4f, snow)
                px(cv, p, 9.6f, 8.4f, 12.8f, 1.2f, snowShade)
                px(cv, p, 5f, 14f, 22f, 2.6f, snow)
                px(cv, p, 5.8f, 16.6f, 20.4f, 1.2f, snowShade)
                px(cv, p, 2f, 22f, 28f, 2.8f, snow)
                px(cv, p, 3f, 24.8f, 26f, 1.2f, snowShade)
                noise(cv, p, r, 3f, 2f, 29f, 28f, c(0xFF1D4427), 8, 1f, 1.6f)
                p.color = snow
                cv.drawOval(RectF(6f, 28f, 26f, 32f), p)
            }
            3 -> { // 눈 덮인 가문비나무 — 처지는 침엽 츱 위로 눈이 쌓인다
                p.color = Color.argb(58, 26, 46, 28)
                cv.drawOval(RectF(6f, 25f, 28f, 31f), p)
                px(cv, p, 14.9f, 22f, 2.6f, 8.5f, bark)
                px(cv, p, 15.7f, 22f, 1.1f, 8.5f, barkLite)
                val path = Path()
                fun snowyTier(apex: Float, baseY: Float, hw: Float) {
                    p.color = c(0xFF1D4636)
                    path.reset()
                    path.moveTo(16f, apex)
                    path.lineTo(16f + hw, baseY)
                    path.lineTo(16f + hw * 0.6f, baseY + 2.8f)
                    path.lineTo(16f - hw * 0.6f, baseY + 2.8f)
                    path.lineTo(16f - hw, baseY)
                    path.close()
                    cv.drawPath(path, p)
                    // 층 위에 쌓인 눈 — 아래로 처진 끝까지 덮는다
                    p.color = snow
                    path.reset()
                    path.moveTo(16f, apex)
                    path.lineTo(16f + hw * 0.78f, baseY + 1.2f)
                    path.lineTo(16f + hw * 0.6f, baseY + 2.8f)
                    path.lineTo(16f - hw * 0.6f, baseY + 2.8f)
                    path.lineTo(16f - hw * 0.78f, baseY + 1.2f)
                    path.close()
                    cv.drawPath(path, p)
                    p.color = snowShade
                    path.reset()
                    path.moveTo(16f, apex + 1.6f)
                    path.lineTo(16f + hw * 0.5f, baseY + 0.4f)
                    path.lineTo(16f - hw * 0.5f, baseY + 0.4f)
                    path.close()
                    cv.drawPath(path, p)
                }
                snowyTier(1.5f, 12f, 4.6f)
                snowyTier(7.5f, 18f, 7.4f)
                snowyTier(14f, 24f, 10.2f)
                snowyTier(21f, 30f, 12.8f)
                noise(cv, p, r, 5f, 2f, 27f, 28f, c(0xFF153627), 8, 1f, 1.6f)
                p.color = snow
                cv.drawOval(RectF(6f, 28f, 26f, 32f), p)
            }
            else -> { // 눈 덮인 진달래 — 작은 관목 위에 눈모자
                p.color = Color.argb(52, 26, 46, 28)
                cv.drawOval(RectF(7f, 27f, 25f, 31f), p)
                for ((sx, sy) in listOf(9f to 24f, 12f to 22f, 16f to 21f, 20f to 23f, 23f to 24f)) {
                    px(cv, p, sx, sy, 1.5f, 30f - sy, c(0xFF5D3A20))
                }
                p.color = c(0xFF2F4A34); cv.drawCircle(16f, 17f, 7f, p)      // 남은 잎
                p.color = c(0xFF3C5C40); cv.drawCircle(12f, 16f, 4.4f, p); cv.drawCircle(20f, 17f, 4.2f, p)
                p.color = snow
                cv.drawOval(RectF(8.5f, 11f, 23.5f, 17.5f), p)
                px(cv, p, 9.6f, 12.8f, 13f, 1.2f, snowShade)
                px(cv, p, 10f, 19f, 12f, 1.4f, snow)
                p.color = snow
                cv.drawOval(RectF(7f, 27f, 25f, 31.5f), p)
            }
        }
    }

    /** 층리 절벽 대신 쓸 수 있는 고산 화강암·검은 현무암 지형 타일. */
    private fun extraMountainArt(volcanic: Boolean): Bitmap = tilePainter { cv, p, r ->
        if (volcanic) {
            vgrad(cv, p, 0f, 0f, 32f, 32f, c(0xFF545653), c(0xFF282C2C), 5)
            for (x in intArrayOf(3, 9, 16, 23, 29)) {
                val top = 2 + (x * 7 % 8)
                px(cv, p, x.toFloat(), top.toFloat(), 3.4f, (32 - top).toFloat(), c(0xFF252928))
                px(cv, p, x + 0.8f, top + 1f, 1f, 20f, c(0xFF747873))
                px(cv, p, x + 2.3f, top + 4f, 1.1f, 24f, c(0xFF3B403E))
            }
            noise(cv, p, r, 0f, 0f, 32f, 32f, c(0xFF92948B), 9, 1f, 1.8f)
        } else {
            vgrad(cv, p, 0f, 0f, 32f, 32f, c(0xFF9EAAB1), c(0xFF535E68), 6)
            val ridge = Path().apply {
                moveTo(0f, 18f); lineTo(5f, 11f); lineTo(9f, 14f); lineTo(16f, 2f)
                lineTo(21f, 9f); lineTo(25f, 7f); lineTo(32f, 17f); lineTo(32f, 32f); lineTo(0f, 32f); close()
            }
            p.color = c(0xFF77848C); cv.drawPath(ridge, p)
            val face = Path().apply { moveTo(16f, 2f); lineTo(21f, 9f); lineTo(18f, 14f); lineTo(12f, 13f); close() }
            p.color = c(0xFFC5CED2); cv.drawPath(face, p)
            px(cv, p, 0f, 20f, 32f, 2f, c(0xFF5E6972))
            noise(cv, p, r, 0f, 0f, 32f, 32f, c(0xFFBEC7C8), 8, 1f, 1.8f)
        }
    }

    /** 계절·지형별 낮은 풀 무늬. */
    private fun extraGroundArt(tile: T, look: Int): Bitmap = tilePainter { cv, p, r ->
        when (tile) {
            T.TALLGRASS -> {
                grassBase(cv, p, r, c(0xFF94C36D))
                if (look == 0) { // 해안 모래언덕의 가는 사초
                    for ((x, h) in listOf(3 to 13, 7 to 18, 12 to 12, 18 to 20, 23 to 15, 28 to 18)) {
                        px(cv, p, x.toFloat(), (32 - h).toFloat(), 1.4f, h.toFloat(), c(0xFF718E49))
                        px(cv, p, x - 2f, (32 - h + 3).toFloat(), 3.4f, 1.2f, c(0xFFA9C56A))
                    }
                    px(cv, p, 6f, 12f, 2f, 3f, c(0xFFB99253)); px(cv, p, 24f, 9f, 2f, 3f, c(0xFFB99253))
                } else { // 바람에 흔들린 억새 이삭
                    for ((x, h) in listOf(4 to 20, 9 to 15, 15 to 22, 22 to 18, 28 to 14)) {
                        px(cv, p, x.toFloat(), (32 - h).toFloat(), 1.5f, h.toFloat(), c(0xFF5F8249))
                        px(cv, p, x - 1f, (32 - h - 3).toFloat(), 3f, 3f, c(0xFFD7C38A))
                        px(cv, p, x - 3f, (32 - h - 2).toFloat(), 2.6f, 1f, c(0xFFE9D8A7))
                    }
                }
            }
            T.FLOWER -> {
                grassBase(cv, p, r)
                val flower = when (look) { 0 -> c(0xFFE87580); 1 -> c(0xFFF2D06B); else -> c(0xFF9A7BC2) }
                val leaf = c(0xFF527C46)
                if (look == 1) { // 유채꽃 군락
                    for ((x, y) in listOf(4 to 11, 10 to 18, 16 to 8, 22 to 15, 28 to 10)) {
                        px(cv, p, x.toFloat(), y.toFloat(), 1.3f, 12f, leaf)
                        px(cv, p, x - 2f, y - 2f, 5f, 4f, flower)
                        px(cv, p, x - 1f, y - 3f, 3f, 1.2f, c(0xFFFFE99A))
                    }
                } else if (look == 2) { // 산나리·붓꽃의 별 모양 꽃
                    for ((x, y) in listOf(5 to 15, 14 to 9, 23 to 17)) {
                        px(cv, p, x.toFloat(), y.toFloat(), 1.4f, 10f, leaf)
                        px(cv, p, x - 3f, y - 2f, 7f, 3f, flower)
                        px(cv, p, x - 1f, y - 4f, 3f, 7f, flower)
                        px(cv, p, x + 1f, y - 1f, 1.2f, 1.2f, c(0xFFFFE89A))
                    }
                } else { // 동백과 진달래
                    for ((x, y) in listOf(6 to 12, 17 to 17, 25 to 10, 11 to 22)) {
                        px(cv, p, x.toFloat(), y.toFloat(), 1.2f, 7f, leaf)
                        px(cv, p, x - 2f, y - 2f, 5f, 4f, flower)
                        px(cv, p, x - 1f, y - 3f, 3f, 1.4f, c(0xFFFFC7BD))
                    }
                }
            }
            else -> grassBase(cv, p, r)
        }
    }

    /** 습지·연못을 위한 갈대 변형: 부들 군락과 둥근 수련잎. */
    private fun extraReedArt(lotus: Boolean): Bitmap = tilePainter { cv, p, r ->
        grassBase(cv, p, r, c(0xFF8DBD67))
        if (!lotus) {
            for ((x, h) in listOf(4 to 20, 10 to 27, 17 to 23, 24 to 29, 29 to 18)) {
                px(cv, p, x.toFloat(), (32 - h).toFloat(), 2f, h.toFloat(), c(0xFF577D42))
                px(cv, p, x + 0.7f, (32 - h + 2).toFloat(), 0.8f, h - 3f, c(0xFFA4BF64))
                px(cv, p, x - 0.5f, (32 - h - 4).toFloat(), 3f, 5f, c(0xFF81502F))
                px(cv, p, x.toFloat(), (32 - h - 3).toFloat(), 1.4f, 3f, c(0xFFB27945))   // main 컴파일 오류 수정(Int→Float)
            }
        } else {
            for ((x, y, s) in listOf(Triple(5, 22, 8), Triple(17, 25, 10), Triple(25, 19, 7))) {
                p.color = c(0xFF4D824C); cv.drawCircle(x.toFloat(), y.toFloat(), s.toFloat() / 2f, p)
                p.color = c(0xFF83A957); cv.drawCircle((x - 1).toFloat(), (y - 1).toFloat(), s.toFloat() / 3f, p)
                px(cv, p, x - 1f, y.toFloat(), 3f, 1f, c(0xFF527B47))
            }
            px(cv, p, 13f, 13f, 1.4f, 12f, c(0xFF648C4C))
            p.color = c(0xFFE78B92); cv.drawCircle(14f, 12f, 2.5f, p)
            px(cv, p, 13f, 10f, 3f, 1f, c(0xFFFFCED0))
        }
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

        add(extraGroundArt(T.TALLGRASS, 0), extraGroundArt(T.TALLGRASS, 1))

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

        add(extraGroundArt(T.FLOWER, 0), extraGroundArt(T.FLOWER, 1), extraGroundArt(T.FLOWER, 2))

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

        add(extraReedArt(lotus = false), extraReedArt(lotus = true))

begin(T.TREE)
        add(
            tilePainter { c, p, r ->   // 0: 참나무
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

        add(*Array(8) { i -> extraTreeArt(i) })

        // 계절 전용 — 겨울 앙상한 나무 / 눈 덮인 활엽수 / 눈 덮인 소나무 (인덱스 12·13·14)
        add(seasonTreeArt(0), seasonTreeArt(1), seasonTreeArt(2))

        // 지역 수종 — 느티나무 / 향나무 / 야자수 / 오리나무 (인덱스 15·16·17·18)
        add(*Array(4) { i -> regionalTreeArt(i) })

        // 실제 나무 21종 + 메타세콰이아(가을) (인덱스 15..36) — 종마다 크기·잎·열매가 다르다
        add(*Array(22) { i -> speciesTreeArt(i) })

        // 계절 전용 — 눈 덮인 가문비나무 / 눈 덮인 진달래 (인덱스 37·38)
        add(seasonTreeArt(3), seasonTreeArt(4))

begin(T.ROCK)
        add(
            tilePainter { c, p, r ->   // 0: 큰 바위
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
begin(T.ROCK)
        // 바위 28종 — 암종·크기급이 모두 다르다 (PropLooks.ROCKS 와 1:1)
        add(*Array(PropLooks.ROCK_COUNT) { i -> rockArt(i) })

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

        add(extraMountainArt(volcanic = false), extraMountainArt(volcanic = true))

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

        // 랜드마크 지붕 — 청기와(에메랄드) + 금빛 용마루로 격조 있게
        begin(T.LM_ROOF)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF2E6E63))
            for (row in 0 until 4) {
                val y = row * 8f
                val off = if (row % 2 == 0) 0f else -6f
                var x = off
                while (x < 32f) {
                    val tt = lerpColor(c(0xFF3E9184), c(0xFF2A6157), r.nextFloat())
                    px(c, p, x + 0.4f, y + 0.6f, 11.2f, 7f, tt)
                    px(c, p, x + 0.4f, y + 0.6f, 11.2f, 1.2f, shade(tt, 1.25f))
                    px(c, p, x + 0.4f, y + 6.4f, 11.2f, 1.2f, shade(tt, 0.72f))
                    px(c, p, x + 8.4f, y + 2f, 1.2f, 5.6f, shade(tt, 0.85f))
                    x += 11.6f
                }
            }
            // 금빛 용마루 + 하이라이트
            px(c, p, 0f, 0f, 32f, 2.4f, c(0xFFE9C56B))
            px(c, p, 0f, 0f, 32f, 1f, c(0xFFFBEBB0))
            noise(c, p, r, 0f, 4f, 32f, 32f, c(0xFF6FB4A6), 4, 1f, 1.6f)
        })

        // 랜드마크 외벽 — 밝은 석재 + 굵은 코너 트림
        begin(T.LM_WALL)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFEDE3CF))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE1D4B9), 16, 1f, 2f)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFFAF3E1), 10, 1f, 1.6f)
            // 석재 줄눈 (가로)
            for (y in 6 until 32 step 8) px(c, p, 0f, y.toFloat(), 32f, 1f, c(0xFFCDBE9C))
            // 상단 처마 트림 + 기초
            px(c, p, 0f, 0f, 32f, 2.6f, c(0xFFB79A6E))
            px(c, p, 0f, 2.6f, 32f, 1f, c(0xFFE8D9B6))
            px(c, p, 0f, 28f, 32f, 4f, c(0xFFB79A6E))
            px(c, p, 0f, 28f, 32f, 1.2f, c(0xFF8A6A4A))
        })

        // 랜드마크 창 — 아치형 큰 창(전망)
        begin(T.LM_WIN)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFEDE3CF))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE1D4B9), 12, 1f, 1.9f)
            px(c, p, 0f, 0f, 32f, 2.6f, c(0xFFB79A6E))
            // 아치 창틀
            px(c, p, 4f, 4f, 24f, 24f, c(0xFF9A7B4F))
            px(c, p, 5.4f, 5.4f, 21.2f, 22f, c(0xFFC7A876))
            // 유리 (하늘빛 그라데이션)
            vgrad(c, p, 6.8f, 7f, 25.2f, 26f, c(0xFFCBE8F5), c(0xFF7FB6D9), 5)
            // 아치 상단 둥근 느낌 + 창살
            px(c, p, 6.8f, 6.6f, 18.4f, 2f, c(0xFF9A7B4F))
            px(c, p, 15.2f, 6.6f, 1.6f, 19.4f, c(0xFF9A7B4F))
            px(c, p, 6.8f, 15.4f, 18.4f, 1.6f, c(0xFF9A7B4F))
            px(c, p, 7.6f, 8f, 5f, 4f, c(0xFFE6F4FB))
        })

        // 랜드마크 정문 — 격조 있는 아치 입구 + 현판
        begin(T.LANDMARK_DOOR)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFEDE3CF))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE1D4B9), 10, 1f, 1.9f)
            px(c, p, 0f, 0f, 32f, 2.6f, c(0xFFB79A6E))
            // 현판 (금빛)
            px(c, p, 6f, 3f, 20f, 3.4f, c(0xFF6B4A1F))
            px(c, p, 6.8f, 3.6f, 18.4f, 2.2f, c(0xFFE9C56B))
            // 아치 문틀
            px(c, p, 4.4f, 6.4f, 23.2f, 25.6f, c(0xFF7A5330))
            px(c, p, 5.8f, 7.6f, 20.4f, 24.4f, c(0xFF9A6A3E))
            // 문짝 (양쪽으로 열리는 큰 문)
            px(c, p, 7f, 9f, 18f, 23f, c(0xFFB98A5C))
            px(c, p, 15.4f, 9f, 1.2f, 23f, c(0xFF6B431F))
            px(c, p, 8.2f, 10.4f, 6.6f, 12f, c(0xFFC89B6A))
            px(c, p, 17.2f, 10.4f, 6.6f, 12f, c(0xFFC89B6A))
            // 손잡이
            px(c, p, 13.6f, 19f, 1.6f, 3.2f, c(0xFFF2D06B))
            px(c, p, 17.8f, 19f, 1.6f, 3.2f, c(0xFFF2D06B))
            // 붉은 융단
            px(c, p, 10f, 28f, 12f, 4f, c(0xFFB23F44))
            noise(c, p, r, 10f, 28f, 22f, 32f, c(0xFF8A2F35), 4, 1f, 1.3f)
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
            // 지도에서 실제 연결 방향(N/E/S/W)을 받아 그릴 수 있도록 판자 화살표 자리는 비워 둔다.
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

        // RANGE_TOP (가정용 오븐 윗부분 — 레인지 후드 + 백스플래시 + 조리도구 선반)
        begin(T.RANGE_TOP)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFF9A9AA6)
            c.drawRect(1f, 0f, 31f, 32f, p)
            p.color = c(0xFFEFEDE6)
            c.drawRect(2.4f, 0f, 29.6f, 32f, p)
            // 레인지 후드 (스틸)
            p.color = c(0xFFB8BCC6)
            c.drawRect(2.4f, 0f, 29.6f, 7.4f, p)
            p.color = c(0xFF8C919C)
            c.drawRect(2.4f, 5.6f, 29.6f, 7.4f, p)
            p.color = c(0xFFD5D8DF)
            c.drawRect(4f, 1.2f, 28f, 2.4f, p)
            p.color = c(0xFFF7E9A8)
            c.drawRect(13f, 6f, 19f, 7.4f, p)
            // 백스플래시 타일
            p.color = c(0xFFDCE8E6)
            c.drawRect(4f, 9f, 28f, 24f, p)
            p.color = c(0xFFC4D3D0)
            c.drawRect(4f, 13.8f, 28f, 14.6f, p)
            c.drawRect(4f, 18.8f, 28f, 19.6f, p)
            c.drawRect(9.6f, 9f, 10.4f, 24f, p)
            c.drawRect(15.6f, 9f, 16.4f, 24f, p)
            c.drawRect(21.6f, 9f, 22.4f, 24f, p)
            // 걸어 둔 나무 주걱 / 스틸 뒤집개
            p.color = c(0xFFB98F5E)
            c.drawRect(8f, 9.5f, 9.6f, 21f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(7f, 19f, 10.6f, 23f, p)
            p.color = c(0xFF6B6B78)
            c.drawRect(23f, 9.5f, 24.6f, 20f, p)
            p.color = c(0xFFB8BCC6)
            c.drawRect(21.6f, 19f, 26f, 23f, p)
            // 선반 + 토마토 소스·바질 병
            p.color = c(0xFFB98F5E)
            c.drawRect(3.4f, 25f, 28.6f, 26.6f, p)
            p.color = c(0xFFEFEDE6)
            c.drawRect(6f, 26.6f, 26f, 32f, p)
            p.color = c(0xFFE2574C)
            c.drawRect(11f, 21.5f, 14.4f, 25f, p)
            p.color = c(0xFF6B4F35)
            c.drawRect(11.8f, 20.4f, 13.6f, 21.6f, p)
            p.color = c(0xFF6FAE57)
            c.drawRect(15.6f, 22f, 18.6f, 25f, p)
            p.color = c(0xFF3F7D46)
            c.drawRect(16.2f, 21f, 18f, 22.2f, p)
        })
        // RANGE (가정용 오븐 — 쿡탑 + 오븐 창, 2프레임 불빛)
        begin(T.RANGE)
        add(*Array(2) { f -> tilePainter { c, p, r ->
                fill(c, p, c(0xFFCDA775))
                p.color = c(0xFF9A9AA6)
                c.drawRect(1f, 0f, 31f, 31f, p)
                p.color = c(0xFFEFEDE6)
                c.drawRect(2.4f, 1.2f, 29.6f, 29.6f, p)
                // 쿡탑 (윗면, 스틸) + 화구 2개
                p.color = c(0xFFB8BCC6)
                c.drawRect(2.4f, 1.2f, 29.6f, 10f, p)
                p.color = c(0xFF8C919C)
                c.drawRect(2.4f, 9f, 29.6f, 10.4f, p)
                p.color = c(0xFFD5D8DF)
                c.drawRect(3.6f, 2f, 28.4f, 2.8f, p)
                p.color = c(0xFF3A3F4A)
                c.drawCircle(10f, 5.8f, 3.2f, p)
                c.drawCircle(22f, 5.8f, 3.2f, p)
                p.color = c(0xFF5A626C)
                c.drawCircle(10f, 5.8f, 2f, p)
                c.drawCircle(22f, 5.8f, 2f, p)
                p.color = if (f == 0) c(0xFFF2913C) else c(0xFFE2574C)
                c.drawCircle(10f, 5.8f, 1.2f, p)
                // 노브 3개 + 오븐 손잡이
                p.color = c(0xFF4A4A55)
                c.drawRect(6.6f, 11.6f, 9.4f, 13.6f, p)
                c.drawRect(14.6f, 11.6f, 17.4f, 13.6f, p)
                c.drawRect(22.6f, 11.6f, 25.4f, 13.6f, p)
                p.color = c(0xFFB8BCC6)
                c.drawRect(4f, 15f, 28f, 16.6f, p)
                p.color = c(0xFF8C919C)
                c.drawRect(4f, 16.6f, 28f, 17.2f, p)
                // 오븐 창 (안에서 피자가 익는 중 — 프레임마다 불빛 밝기가 다름)
                p.color = c(0xFF23232B)
                c.drawRect(6f, 18.4f, 26f, 27.4f, p)
                p.color = c(0xFF3A2A28)
                c.drawRect(7.4f, 19.6f, 24.6f, 26.2f, p)
                p.color = if (f == 0) c(0xFFE07A2C) else c(0xFFF2913C)
                c.drawRect(8.4f, 21f, 23.6f, 26.2f, p)
                p.color = if (f == 0) c(0xFFF2B63C) else c(0xFFF7CE5B)
                c.drawRect(9.6f, 22f, 22.4f, 24.4f, p)
                p.color = c(0xFFE2574C)
                c.drawRect(11f, 22.6f, 13f, 23.6f, p)
                c.drawRect(17f, 23f, 19f, 24f, p)
                p.color = c(0xFF5A5A66)
                c.drawRect(8.4f, 19.6f, 24.6f, 20.4f, p)
                // 하단 받침
                p.color = c(0xFF6B6B78)
                c.drawRect(3f, 29.6f, 29f, 31.4f, p)
            }
        })

        // 도로 부속물 (Roads.kt 자원)
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

    /** 피자 종류별 아이콘 (id = Pizzas.ALL 인덱스) */
    fun pizzaArt(pizzaId: Int): Bitmap = pizzaArts[pizzaId.coerceIn(0, pizzaArts.size - 1)]

    /** 타일 좌표 기반 변형 선택 */
    fun tileVariant(tileOrdinal: Int, x: Int, y: Int): Int {
        val n = tiles[tileOrdinal].size
        if (n <= 1) return 0
        return ((x * 7 + y * 13) % n + n) % n
    }

    /**
     * 계절에 맞는 나무 변형 인덱스.
     *
     * - **상록수**(소나무·곰솔·잣나무·전나무·가문비나무·주목·노간주나무·동백·후박나무·
     *   붉가시나무·감귤·야자·대나무)는 사계절 같은 그림을 쓰고, 겨울에는 눈을 얹는 종만 갈아입는다.
     * - **낙엽수**는 봄·여름에 지역이 고른 종 그대로 잎을 물들이므로 지역의 실제 수종이
     *   살아나고, 가을엔 단풍·은행·메타세콰이아·감·참나무로 물들며, 겨울엔 앙상한 가지(+눈)가 된다.
     * - 같은 칸은 같은 계절에 항상 같은 나무 (결정적 해시).
     */
    fun seasonTreeIndex(base: Int, season: Season, x: Int, y: Int): Int {
        val kind = treeKinds.getOrNull(base)
        if (kind != null && kind.evergreen) {
            // 겨울 전용 변형은 다른 계절에 그대로 쓰면 안 된다 — 원래 상록수로 되돌린다
            return when {
                base == TREE_SNOWPINE && season != Season.WINTER -> TREE_PINE
                base == TREE_SNOWSPRUCE && season != Season.WINTER -> TREE_SPRUCE
                season != Season.WINTER -> base
                base == TREE_SPRUCE -> TREE_SNOWSPRUCE      // 가문비나무는 눈을 얹는다
                base == TREE_PINE || base == TREE_FIR -> TREE_SNOWPINE
                else -> base
            }        }
        // 잎이 무성한 낙엽수만 봄·여름에 자기 그림을 유지한다 — 겨울 전용 변형은 다른 계절에 쓰지 않는다
        val winterOnly = base == TREE_BARE || base == TREE_SNOWLEAF || base == TREE_SNOWBUSH ||
            base == TREE_SNOWPINE || base == TREE_SNOWSPRUCE
        val leafy = treeKinds.getOrNull(base) != null && !winterOnly
        var h = x * 0x45D9F3B + y * 0x119DE1F3 + season.ordinal * 0x27D4EB2D
        h = (h xor (h ushr 16)) * 0x45D9F3B
        h = h xor (h ushr 16)
        val pick = Math.floorMod(h, 100)
        // 봄·여름에 지역 종과 섞어 주는 낙엽수 후보 (잎이 무성한 종만)
        val leafyPool = intArrayOf(
            TREE_OAK, TREE_MONGOAK, TREE_ACORNOAK, TREE_BIGLEAF, TREE_ZELKOVA, TREE_ASH,
            TREE_CHESTNUT, TREE_ACACIA, TREE_PERSIMMON, TREE_WINGNUT, TREE_PLANETREE,
            TREE_BIRCH, TREE_ORCHARD, TREE_WILDCHERRY, TREE_WILLOW
        )
        val alt = leafyPool[Math.floorMod(h ushr 8, leafyPool.size)]
        return when (season) {
            Season.SPRING -> when {
                leafy && pick < 42 -> base          // 지역이 고른 종은 봄에도 그대로
                pick < 74 -> TREE_CHERRY            // 온 동네가 벚나무
                pick < 82 -> TREE_AZALEA            // 진달래
                pick < 90 -> TREE_WILDCHERRY         // 산벚나무
                else -> alt
            }
            Season.SUMMER -> when {
                leafy && pick < 68 -> base
                pick < 76 -> TREE_ACACIA
                pick < 84 -> TREE_PLANETREE
                else -> alt
            }
            Season.AUTUMN -> when {
                pick < 30 -> TREE_MAPLE              // 단풍
                pick < 48 -> TREE_GINKGO             // 은행 노랑
                pick < 58 -> TREE_METASEQUOIA_A      // 메타세콰이아가 붉게 물든다
                pick < 68 -> TREE_PERSIMMON          // 감나무에 주황 열매
                pick < 80 -> TREE_MONGOAK            // 신갈나무
                pick < 90 -> TREE_ACORNOAK           // 상수리나무
                else -> TREE_WINGNUT                 // 물오리나무
            }
            Season.WINTER -> when {
                pick < 50 -> TREE_BARE               // 앙상한 가지
                pick < 88 -> TREE_SNOWLEAF           // 눈 덮인 가지
                else -> TREE_SNOWBUSH                // 눈 덮인 진달래 관목
            }
        }.coerceIn(0, tiles[T.TREE.ordinal].size - 1)
    }

    // -----------------------------------------------------------------------
    // 아이콘
    // -----------------------------------------------------------------------

    private fun buildIcons() {
        pizzaIcon = renderPixel("pizza", 22, 14)
        pizzaIconBig = Bitmap.createScaledBitmap(
            pizzaIcon, pizzaIcon.width * 4, pizzaIcon.height * 4, false
        )
        // art/svg/items.svg #art_pizza 와 같은 디자인 언어 (tools/pizza_lab.py --dump-ascii 로 추출).
        //  c 크러스트 / d 크러스트 그늘 / h 크러스트 빛 / k 그을림 / T 토마토소스 링
        //  C 치즈(baseColor) / L·S 치즈 밝기·그늘(파생) / R·r·G 토핑1 면·테·윤 / A·b 토핑2 면·테
        val pizza = listOf(
            "..........hh..........",
            ".....chhhhhhhhhhc.....",
            "....hhTTCCCCCCRThh....",
            "..ckkCRLLLACCGRRCkkc..",
            "..hcCGRRLLLLCCrCCCch..",
            ".ccTCCrLLLRCCCACGCTcc.",
            ".ccSCGCCCGGRCCCRRRSkc.",
            ".ccTRRRCCCrCGGCCrCTcc.",
            "..ccSrCCbCCCRRrCCScc..",
            "..dcccSCCCCCCCCScccd..",
            "...dcccCcSSSSCCcccd...",
            ".....dcLcccccCCcd.....",
            ".......dddddddd.......",
            "......................"
        )
        // 피자 종류별 아이콘 — 같은 실루엣에 색만 바꾼다 (음영은 각 색에서 파생).
        //  일반 피자: 통통한 황금 크러스트(위 템플릿) / 화덕피자: 얇고 군데군데 그을린(k) 러스틱 크러스트 + 큼직한 토핑
        val pizzaOven = listOf(
            "......................",
            "........cckccc........",
            "......cCCCCCCCkc......",
            "....cCCLLLCCACCCkc....",
            "...hCCLLLLCCCRRRrkc...",
            "..cCCCCCACCCCRRRrCkc..",
            "..cCCRRCCCCACCRRRrkc..",
            "..ckCRRrCCACCCrCCCCc..",
            "...cCCCCCRRrCCCCCSd...",
            "...dCCCCCrrCCCCSSSd...",
            "....dCCCCCCSSSSSCd....",
            ".....dCCddkddddkd.....",
            ".......ddddkddd.......",
            "......................"
        )
        pizzaArts = Array(Pizzas.ALL.size) { i ->
            val def = Pizzas.ALL[i]
            val base = mapOf(
                'T' to c(0xFFD8453A),
                'C' to def.baseColor, 'L' to tone(def.baseColor, 1.18f), 'S' to tone(def.baseColor, 0.82f),
                'R' to def.topColorA, 'r' to tone(def.topColorA, 0.72f), 'G' to tone(def.topColorA, 1.3f),
                'A' to def.topColorB, 'b' to tone(def.topColorB, 0.72f)
            )
            if (def.kind == PizzaKind.OVEN) {
                sprite(
                    pizzaOven, base + mapOf(
                        'c' to c(0xFFE0B070), 'd' to c(0xFFB87A45),
                        'h' to c(0xFFEDC293), 'k' to c(0xFF5A3A2A)
                    )
                )
            } else {
                sprite(
                    pizza, base + mapOf(
                        'c' to c(0xFFE8A75C), 'd' to c(0xFFD18F4A),
                        'h' to c(0xFFF2C078), 'k' to c(0xFFBF7640)
                    )
                )
            }
        }

        cloverIcon = renderIcon("clover", 14, 14)
        cameraIcon = renderIcon("camera", 20, 16)
        houseIcon = renderIcon("house", 14, 14)
        sunIcon = renderIcon("sun", 16, 16)
        moonIcon = renderIcon("moon", 14, 14)
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

    /** dir: 0 정면 / 1 뒤 / 2 오른쪽 / 3 왼쪽 — hd 면 캐릭터와 같은 해상도로 그린다 */
    fun camHeld(look: CamLook, dir: Int, raised: Boolean, hd: Boolean = true): Bitmap {
        val px = if (hd) CHARACTER_PX else 32
        if (dir == 3) {
            return camHeldCache.getOrPut(HeldKey(look, 3, raised, px)) { flipH(camHeld(look, 2, raised, hd)) }
        }
        return camHeldCache.getOrPut(HeldKey(look, dir, raised, px)) { buildCamHeld(look, dir, raised, px) }
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

    private fun buildCamHeld(lk: CamLook, dir: Int, raised: Boolean, px: Int): Bitmap {
        val k = px.toFloat() / 32f
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        if (k != 1f) {
            // HD: 32 도트 좌표계를 그대로 쓰되 캔버스를 늘려 그린다.
            // 안티앨리어싱을 켜면 반 픽셀 단위 좌표가 부드럽게 래스터화되어
            // 캐릭터(HD)와 같은 결로 보인다.
            cv.scale(k, k)
        }
        val p = Paint().apply { isAntiAlias = k != 1f }
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
        // id 순서를 저장 데이터와 맞춘다. 기본 6개는 SVG, 새 카탈로그는 같은 32px
        // 팔레트로 코드 생성해 해상도와 설치 용량을 늘리지 않는다.
        decorArt = arrayOf(
            renderPixel("decor_cactus", 32, 32),       // 0
            renderPixel("decor_bookshelf", 32, 32),    // 1
            renderPixel("decor_rug", 32, 32),          // 2
            renderPixel("decor_lamp", 32, 32),         // 3
            renderPixel("decor_trophy", 32, 32),       // 4
            renderPixel("decor_radio", 32, 32),        // 5
            decorMonstera(),                            // 6
            decorBirdFrame(),                           // 7
            decorCampChair(),                           // 8
            decorPostcards(),                           // 9
            decorRecordPlayer(),                        // 10
            decorFieldNotebook()                        // 11
        )
    }

    /** 새 장식은 화면의 논리 32px에 맞춰 또렷하게 찍는다. */
    private fun blankDecor(draw: (Canvas, Paint) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), Paint().apply { isAntiAlias = false })
        return b
    }

    private fun block(c: Canvas, p: Paint, color: Int, l: Float, t: Float, r: Float, b: Float) {
        p.color = color
        c.drawRect(l, t, r, b, p)
    }

    private fun decorMonstera(): Bitmap = blankDecor { c, p ->
        block(c, p, 0x30000000, 6f, 27f, 27f, 30f)
        block(c, p, 0xFFB85F32.toInt(), 11f, 22f, 22f, 28f)
        block(c, p, 0xFFE18A4B.toInt(), 12f, 22f, 21f, 24f)
        block(c, p, 0xFF477B45.toInt(), 15f, 12f, 17f, 23f)
        block(c, p, 0xFF5CA45A.toInt(), 8f, 10f, 16f, 17f)
        block(c, p, 0xFF3C7B43.toInt(), 9f, 12f, 14f, 17f)
        block(c, p, 0xFF66AE60.toInt(), 16f, 6f, 25f, 15f)
        block(c, p, 0xFF417D45.toInt(), 17f, 7f, 21f, 14f)
        block(c, p, 0xFF5CA45A.toInt(), 14f, 14f, 23f, 21f)
        block(c, p, 0xFF3C7B43.toInt(), 15f, 15f, 20f, 20f)
    }

    private fun decorBirdFrame(): Bitmap = blankDecor { c, p ->
        block(c, p, 0x30000000, 5f, 26f, 28f, 29f)
        block(c, p, 0xFF744625.toInt(), 6f, 5f, 27f, 26f)
        block(c, p, 0xFFD79A4E.toInt(), 8f, 7f, 25f, 24f)
        block(c, p, 0xFF83C7DE.toInt(), 10f, 9f, 23f, 22f)
        block(c, p, 0xFF78A967.toInt(), 10f, 18f, 23f, 22f)
        block(c, p, 0xFF364358.toInt(), 14f, 13f, 19f, 16f)
        block(c, p, 0xFF364358.toInt(), 12f, 12f, 15f, 14f)
        block(c, p, 0xFFF6D265.toInt(), 19f, 13f, 21f, 14f)
        block(c, p, 0xFF364358.toInt(), 16f, 16f, 18f, 19f)
    }

    private fun decorCampChair(): Bitmap = blankDecor { c, p ->
        block(c, p, 0x30000000, 5f, 27f, 28f, 30f)
        block(c, p, 0xFF5B4030.toInt(), 8f, 23f, 10f, 29f)
        block(c, p, 0xFF5B4030.toInt(), 22f, 23f, 24f, 29f)
        block(c, p, 0xFF5B4030.toInt(), 8f, 26f, 24f, 28f)
        block(c, p, 0xFF4B6C72.toInt(), 9f, 10f, 23f, 23f)
        block(c, p, 0xFF6FA7A6.toInt(), 11f, 12f, 21f, 20f)
        block(c, p, 0xFFEFD585.toInt(), 11f, 12f, 21f, 14f)
        block(c, p, 0xFF5B4030.toInt(), 7f, 8f, 9f, 25f)
        block(c, p, 0xFF5B4030.toInt(), 23f, 8f, 25f, 25f)
    }

    private fun decorPostcards(): Bitmap = blankDecor { c, p ->
        block(c, p, 0x30000000, 5f, 27f, 27f, 30f)
        block(c, p, 0xFF8B5A38.toInt(), 7f, 6f, 25f, 26f)
        block(c, p, 0xFFF0DAB0.toInt(), 9f, 8f, 23f, 24f)
        block(c, p, 0xFFEAA35F.toInt(), 10f, 10f, 16f, 16f)
        block(c, p, 0xFF86C6E5.toInt(), 16f, 10f, 22f, 16f)
        block(c, p, 0xFF79A864.toInt(), 10f, 16f, 22f, 22f)
        block(c, p, 0xFFB95345.toInt(), 11f, 11f, 15f, 15f)
        block(c, p, 0xFF4A6C84.toInt(), 18f, 11f, 21f, 14f)
        block(c, p, 0xFFF6E6BB.toInt(), 12f, 4f, 20f, 8f)
    }

    private fun decorRecordPlayer(): Bitmap = blankDecor { c, p ->
        block(c, p, 0x30000000, 4f, 27f, 28f, 30f)
        block(c, p, 0xFF563A2B.toInt(), 6f, 17f, 26f, 27f)
        block(c, p, 0xFF8E6041.toInt(), 7f, 18f, 25f, 20f)
        p.color = 0xFF2B3037.toInt(); c.drawCircle(15f, 22f, 5f, p)
        p.color = 0xFF8DA1B5.toInt(); c.drawCircle(15f, 22f, 1.2f, p)
        block(c, p, 0xFFD8A84C.toInt(), 21f, 19f, 22f, 24f)
        block(c, p, 0xFFD8A84C.toInt(), 21f, 19f, 24f, 20f)
        block(c, p, 0xFF3E2B25.toInt(), 8f, 26f, 10f, 29f)
        block(c, p, 0xFF3E2B25.toInt(), 22f, 26f, 24f, 29f)
    }

    private fun decorFieldNotebook(): Bitmap = blankDecor { c, p ->
        block(c, p, 0x30000000, 6f, 27f, 27f, 30f)
        block(c, p, 0xFF415468.toInt(), 9f, 6f, 23f, 27f)
        block(c, p, 0xFF7DA3B4.toInt(), 11f, 7f, 24f, 25f)
        block(c, p, 0xFFF8F0D9.toInt(), 13f, 8f, 22f, 24f)
        block(c, p, 0xFF7A6250.toInt(), 14f, 12f, 21f, 13f)
        block(c, p, 0xFF7A6250.toInt(), 14f, 16f, 20f, 17f)
        block(c, p, 0xFF7A6250.toInt(), 14f, 20f, 19f, 21f)
        block(c, p, 0xFFD75B48.toInt(), 9f, 9f, 11f, 23f)
        block(c, p, 0xFFB67A37.toInt(), 22f, 4f, 25f, 8f)
    }

    // -----------------------------------------------------------------------

    /** 새 비트맵 (안전 접근) — 기본 왼쪽 옆모습. */
    fun bird(id: String): Bitmap {
        val def = Birds.byId[id] ?: Birds.ALL.first()
        val ready = birdReferencePalettes.containsKey(def.id)
        // 처음엔 기본색으로 바로 그리고, 사진 기준색이 로딩되면 다음 접근에서 교체한다.
        birdCache[def.id]?.let { if (!ready || def.id in birdCacheWithPhoto) return it }
        val bmp = buildBird(def)
        birdCache[def.id] = bmp
        val key = BirdPoseKey(def.id, BirdFacing.LEFT, BirdPose.PERCHED)
        birdPoseCache[key] = bmp
        if (ready) {
            birdCacheWithPhoto.add(def.id)
            birdPoseCacheWithPhoto.add(key)
        }
        return bmp
    }

    /** 방향과 행동이 모두 반영된 필드/촬영용 새. 598종 × 자세는 실제로 필요할 때만 생성한다. */
    fun birdPose(id: String, facing: BirdFacing, pose: BirdPose = BirdPose.PERCHED): Bitmap {
        val def = Birds.byId[id] ?: Birds.ALL.first()
        if (facing == BirdFacing.LEFT && pose == BirdPose.PERCHED) return bird(def.id)
        val key = BirdPoseKey(def.id, facing, pose)
        val ready = birdReferencePalettes.containsKey(def.id)
        birdPoseCache[key]?.let { if (!ready || key in birdPoseCacheWithPhoto) return it }
        return buildBirdPose(def, facing, pose).also {
            birdPoseCache[key] = it
            if (ready) birdPoseCacheWithPhoto.add(key)
        }
    }

    /** 부팅 스레드에서만: 첫 화면에 보이는 새는 정확한 사진 기준색으로 바로 준비한다. */
    fun preloadBird(id: String): Bitmap {
        val def = Birds.byId[id] ?: Birds.ALL.first()
        computeBirdReferencePalette(def)
        return bird(def.id)
    }

    /** 이 목록의 종을 미리 만들어 둔다 (장면 전환 뒤 스폰 렉을 막고 싶을 때) */
    fun prewarmBirds(defs: Collection<BirdDef>) {
        for (d in defs) bird(d.id)
    }

    /** 오른쪽을 바라보는 새 — 단순 반전이 아니라 방향 캐시의 실제 자세를 사용한다. */
    fun birdFlipped(id: String): Bitmap = birdPose(id, BirdFacing.RIGHT, BirdPose.PERCHED)

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

    // -----------------------------------------------------------------------
    // 조류 대도감 실제 사진 및 썸네일 — 백그라운드 로더 + LruCache
    //
    //  JPEG를 게임 스레드에서 푸는 순간 그 프레임이 10~30ms 흔들린다.
    //  도감 '다음' 을 누르면 한 프레임에 12장, 새 상세의 '다음' 을 누르면
    //  고화질 사진 한 장이 같이 디코드돼서 버튼이 눌린 직후 카드가 났다.
    //  로더 스레드가 미리 풀어두고, 그리는 쪽은 "있으면 그린다"만 한다
    //  (없는 동안은 도감 화면이 이미 그려 놓은 도트 스프라이트가 대신 나온다).
    // -----------------------------------------------------------------------
    private val photoLoader = AssetImageLoader("PizzaAndBirdPhoto", 24) { num ->
        context.assets.open("birds/$num.jpg").use { BitmapFactory.decodeStream(it) }
    }
    private val thumbLoader = AssetImageLoader("PizzaAndBirdThumb", 160) { num ->
        context.assets.open("birds_thumb/$num.jpg").use { BitmapFactory.decodeStream(it) }
            ?: context.assets.open("birds/$num.jpg").use { BitmapFactory.decodeStream(it) }
    }

    /** 조류 고화질 실제 사진 (assets/birds/{num}.jpg) — 없으면 로더에 부탁하고 null */
    fun birdPhoto(num: Int): Bitmap? {
        if (num <= 0) return null
        photoLoader.cached(num)?.let { return it }
        photoLoader.request(num)
        return null
    }

    /** 미리 받아두라고 알림만 한다 (버튼을 누르는 순간 미리 풀어두게) */
    fun prefetchBirdPhoto(num: Int) {
        if (num > 0 && photoLoader.cached(num) == null) photoLoader.request(num)
    }

    fun prefetchBirdThumb(num: Int) {
        if (num > 0 && thumbLoader.cached(num) == null) thumbLoader.request(num)
    }

    /** 새 사진 기준색을 미리 계산한다 (디코드는 전용 스레드). */
    fun prefetchBirdPalette(birdId: String) {
        if (birdId.isBlank() || birdReferencePalettes.containsKey(birdId)) return
        if (!birdPalettePending.add(birdId)) return
        birdPaletteQueue.put(birdId)
    }

    /** 도감 그리드용 최적화 썸네일 (assets/birds_thumb/{num}.jpg) — 없으면 로더에 부탁하고 null */
    fun birdThumb(num: Int): Bitmap? {
        if (num <= 0) return null
        thumbLoader.cached(num)?.let { return it }
        thumbLoader.request(num)
        return null
    }

    /**
     * 로더가 요청한 이미지를 다 풀 때까지 잠시 기다린다.
     * 프리뷰 스크린샷·회귀 테스트 전용 — 실제 게임 루프에서는 부르지 않는다.
     */
    fun awaitImages(timeoutMs: Long = 5000L): Boolean {
        val until = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < until) {
            if (!photoLoader.busy() && !thumbLoader.busy()) return true
            Thread.sleep(4L)
        }
        return false
    }

    /**
     * assets 의 이미지 하나를 낮은 우선순위 스레드에서 미리 푸는 로더.
     * 같은 번호는 한 번만 요청하고, 다 풀리면 LruCache 에 들어간다.
     */
    private class AssetImageLoader(
        threadName: String,
        cacheSize: Int,
        private val decode: (Int) -> Bitmap?
    ) {
        private val cache = LruCache<Int, Bitmap>(cacheSize)
        private val queued = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        private val jobs = LinkedBlockingQueue<Int>()
        @Volatile private var running = false

        init {
            Thread({ loop() }, threadName).apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                start()
            }
        }

        fun cached(num: Int): Bitmap? = cache.get(num)

        fun request(num: Int) {
            if (num <= 0) return
            if (cache.get(num) != null) return
            if (queued.add(num)) jobs.put(num)
        }

        fun busy(): Boolean = running || jobs.isNotEmpty() || queued.isNotEmpty()

        private fun loop() {
            while (true) {
                val num = try {
                    jobs.take()
                } catch (_: InterruptedException) {
                    return
                }
                running = true
                val bmp = try {
                    decode(num)
                } catch (_: Exception) {
                    null
                }
                if (bmp != null) cache.put(num, bmp)
                queued.remove(num)
                running = false
            }
        }
    }

    // 아트 빌더는 모든 데이터 필드(artIds 등) 선언 이후에 실행돼야 한다 —
    // 클래스 끝에 두어 초기화 순서 문제(Kotlin 프로퍼티 선언 순서)를 원천 차단한다.
    // 새는 여기서 만들지 않는다 — bird(id) 가 종별 지연 생성 (시작 시간 단축)
    init {
        BirdEncyclopedia.init(context)
        buildCat()
        buildGrassRig()
        buildTiles()
        buildIcons()
        buildDecorArt()
    }
}
