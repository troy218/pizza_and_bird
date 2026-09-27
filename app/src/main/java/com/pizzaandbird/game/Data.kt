package com.pizzaandbird.game

/**
 * Pizza and Bird : 피자와 새 — 게임 데이터 정의
 *
 * 새 종류 / 피자 종류 / 장식 요소는 이 파일의 데이터만 추가하면
 * 게임에 자동으로 반영되는 구조입니다. (도감, 스폰, 박사 의뢰 자동 연동)
 */

// ---------------------------------------------------------------------------
// 공통 유틸
// ---------------------------------------------------------------------------

/** 1234567 -> "1,234,567" */
fun fmtMoney(v: Int): String {
    if (v < 0) return "-" + fmtMoney(-v)
    if (v < 1000) return v.toString()
    val sb = StringBuilder()
    var n = v
    var c = 0
    while (n > 0) {
        sb.append(('0' + (n % 10)))
        c++
        n /= 10
        if (c % 3 == 0 && n > 0) sb.append(',')
    }
    return sb.reverse().toString()
}

/** 이동 방향 (지도 기준) */
enum class Dir { N, E, S, W }

// ---------------------------------------------------------------------------
// 조류(새) 데이터
// ---------------------------------------------------------------------------

/** 등급: 스폰 가중치와 보수에 영향 */
enum class Tier(val star: Int, val label: String) {
    COMMON(1, "흔함"),
    UNCOMMON(2, "보통"),
    RARE(3, "희귀"),
    LEGEND(4, "전설");

    fun starText(): String = "★".repeat(star) + "☆".repeat(4 - star)
}

/** 새 픽셀 아트 정보 (Assets.kt에서 실제 비트맵 생성) */
class BirdArt(
    val template: Int,                       // 0: 소형 명금, 1: 물오리형
    val body: Int, val belly: Int, val wing: Int,
    val beak: Int, val crest: Int, val leg: Int
)

class BirdDef(
    val id: String,
    val name: String,
    val tier: Tier,
    val habitats: Set<String>,               // city/forest/field/water/wetland/coast/mountain
    val weight: Double,                      // 기본 스폰 가중치
    val reward: Int,                         // 박사 의뢰 보수(원)
    val desc: String,
    val onlyRegions: Set<String>? = null,    // 특정 지역에서만 출현 (null이면 서식지 기준)
    val art: BirdArt
)

object Birds {
    private fun c(v: Long) = v.toInt()

    val ALL = listOf(
        BirdDef(
            "sparrow", "참새", Tier.COMMON, setOf("city", "field"), 30.0, 300,
            "동네 어디서나 만날 수 있는 우리의 친구. 수수 한 알에도 행복해한다.",
            null,
            BirdArt(0, c(0xFF9C7A54), c(0xFFEFE3CF), c(0xFF6B4A33), c(0xFF4A3728), c(0xFF6B4A33), c(0xFFB98A4A))
        ),
        BirdDef(
            "bulbul", "직박구리", Tier.COMMON, setOf("city", "forest"), 28.0, 300,
            "머리에 흰 털이 특징. 목청이 무척 좋아 아침마다 알람 역할을 한다.",
            null,
            BirdArt(0, c(0xFF6D635A), c(0xFFE8DDC8), c(0xFF8A7D6D), c(0xFF3A322C), c(0xFF4F463F), c(0xFFB98A4A))
        ),
        BirdDef(
            "magpie", "까치", Tier.COMMON, setOf("city", "field"), 26.0, 320,
            "한국의 국조. 아침에 울면 반가운 소식이 온다는 좋은 새.",
            null,
            BirdArt(0, c(0xFF3C3F47), c(0xFFF2F2F0), c(0xFF23252B), c(0xFF23252B), c(0xFF3C3F47), c(0xFF3A3A44))
        ),
        BirdDef(
            "greattit", "박새", Tier.COMMON, setOf("forest", "city"), 26.0, 320,
            "노란 배에 검은 넥타이를 맨 듯한 깔끔한 패션의 소유자.",
            null,
            BirdArt(0, c(0xFF4F6F52), c(0xFFF2D65A), c(0xFF3A5440), c(0xFF23252B), c(0xFF23252B), c(0xFFB98A4A))
        ),
        BirdDef(
            "dove", "멧비둘기", Tier.COMMON, setOf("city", "forest"), 22.0, 300,
            "목에 무늬가 반짝이는 온화한 새. 구구… 구구…",
            null,
            BirdArt(0, c(0xFFB8A99A), c(0xFFE6DCCB), c(0xFF8F8072), c(0xFF6B5A48), c(0xFF9C8D7E), c(0xFFB0793F))
        ),
        BirdDef(
            "gull", "괭이갈매기", Tier.COMMON, setOf("coast", "water"), 26.0, 300,
            "바닷가의 주민. 괭이~ 괭이~ 울음소리가 이름이 되었다.",
            null,
            BirdArt(0, c(0xFFF2F2EE), c(0xFFFFFFFF), c(0xFFC9C9C2), c(0xFFF2A33C), c(0xFFD9D9D2), c(0xFFF2A33C))
        ),
        BirdDef(
            "tern", "쇠제비갈매기", Tier.UNCOMMON, setOf("coast", "water"), 12.0, 800,
            "제비처럼 날카롭게 물위를 가르며 낚시를 즐기는 갈매기.",
            null,
            BirdArt(0, c(0xFFEEF2F4), c(0xFFFFFFFF), c(0xFF6B7D8A), c(0xFFE8B14E), c(0xFF23252B), c(0xFFE8863C))
        ),
        BirdDef(
            "spotduck", "청둥오리", Tier.UNCOMMON, setOf("water", "wetland"), 12.0, 800,
            "연못과 호수의 단골. 머리의 초록빛이 은은하게 반짝인다.",
            null,
            BirdArt(1, c(0xFF7A6A52), c(0xFFCBB894), c(0xFF3F6D8E), c(0xFFE8B14E), c(0xFF5D6440), c(0xFFE8863C))
        ),
        BirdDef(
            "woodpecker", "까막딱따구리", Tier.UNCOMMON, setOf("forest", "mountain"), 10.0, 800,
            "울창한 숲의 목수. 나무를 두드리는 소리가 숲에 울려 퍼진다.",
            null,
            BirdArt(0, c(0xFF2B2B30), c(0xFF3A3A40), c(0xFF1E1E24), c(0xFFD9D3C8), c(0xFFD9403A), c(0xFF6B7280))
        ),
        BirdDef(
            "kestrel", "황조롱이", Tier.RARE, setOf("field", "mountain", "coast"), 5.0, 2000,
            "하늘에 떠서 들판을 살피는 작은 매. 바람 위에서 멈춰 서기도 한다.",
            null,
            BirdArt(0, c(0xFFB06A3C), c(0xFFE8D5B0), c(0xFF7A4A2B), c(0xFF4A3728), c(0xFF6B7D8A), c(0xFFF2D06B))
        ),
        BirdDef(
            "pitta", "팔색조", Tier.RARE, setOf("forest"), 4.0, 2000,
            "무지개 빛깔 여덟 색을 두른 숲의 보석. 운이 좋아야 만날 수 있다.",
            setOf("gwangju", "jeju"),
            BirdArt(0, c(0xFF4F8F6A), c(0xFFE8E0C8), c(0xFF3F6FB0), c(0xFF23252B), c(0xFFD9403A), c(0xFFB98A4A))
        ),
        BirdDef(
            "crane", "두루미", Tier.LEGEND, setOf("wetland", "water"), 1.2, 6000,
            "머리에 붉은 왕관을 얹은 격식 있는 겨울 손님. 만나면 한 해가 행복하다.",
            setOf("seoul", "chuncheon", "incheon", "jeju"),
            BirdArt(1, c(0xFFF5F2EA), c(0xFFFFFFFF), c(0xFFD9D3C8), c(0xFF6B4F35), c(0xFFD9403A), c(0xFF6B4F35))
        )
    )

    val byId: Map<String, BirdDef> = ALL.associateBy { it.id }

    /** 지역 서식지에 맞는 새 풀 */
    fun poolFor(region: RegionDef): List<BirdDef> = ALL.filter { def ->
        def.habitats.intersect(region.habitats).isNotEmpty() &&
                (def.onlyRegions == null || region.id in def.onlyRegions)
    }
}

// ---------------------------------------------------------------------------
// 피자 / 카메라
// ---------------------------------------------------------------------------

/** 피자 품질 (bake 미니게임 결과) */
enum class PizzaQ(val label: String, val hunger: Int, val luck: Int, val stars: Int) {
    BURNT("살짝 탄 피자", 20, 5, 1),
    GOOD("맛있는 피자", 38, 10, 2),
    PERFECT("걸작 피자", 60, 18, 3);

    companion object {
        fun of(q: Int): PizzaQ = values()[q.coerceIn(0, 2)]
    }
}

/** 카메라 등급 (상점에서 업그레이드) */
object CameraDefs {
    class Cam(val name: String, val rangeTiles: Float, val cost: Int, val qualityBonus: Int)

    val LEVELS = listOf(
        Cam("폰 카메라", 4.5f, 0, 0),
        Cam("컴팩트 카메라", 6.0f, 800, 0),
        Cam("미러리스", 7.5f, 2500, 1),
        Cam("DSLR", 9.0f, 6000, 1),
        Cam("프리미엄 DSLR", 11.0f, 15000, 2)
    )

    fun name(level: Int): String = LEVELS[(level - 1).coerceIn(0, LEVELS.size - 1)].name
    fun range(level: Int): Float = LEVELS[(level - 1).coerceIn(0, LEVELS.size - 1)].rangeTiles
}

/** 이사 비용 */
const val MOVE_COST = 300

/** 들고 다닐 수 있는 피자 최대 개수 */
const val PIZZA_CAP = 6

// ---------------------------------------------------------------------------
// 지역 데이터 (실제 한국 지역)
// ---------------------------------------------------------------------------

class RegionDef(
    val id: String,
    val name: String,
    val english: String,
    val habitats: Set<String>,
    val desc: String,
    val villager: String,
    val mapW: Int,
    val mapH: Int,
    val waterEdges: Set<Dir>,
    val sandEdges: Set<Dir>,
    val treeDensity: Double,
    val rockDensity: Double,
    val flowerDensity: Double,
    val city: Boolean,
    val lake: Boolean,
    val mmX: Float,      // 원형 미니맵 상 위치 (-1..1)
    val mmY: Float
) {
    val habitatLabels: String
        get() = habitats.joinToString(" · ") { HabitatLabels[it] ?: it }
}

val HabitatLabels = mapOf(
    "city" to "도시", "forest" to "숲", "field" to "들판",
    "water" to "물가", "wetland" to "습지", "coast" to "바다", "mountain" to "산"
)

object Regions {
    val ALL = listOf(
        RegionDef(
            "seoul", "서울", "Seoul", setOf("city", "water", "field"),
            "한강이 흐르는 도시. 강가를 걷다 보면 물새들과 마주친다.",
            "서울은 넓어요. 강변을 따라 자전거를 타면 기분이 좋아져요.",
            80, 60, emptySet(), emptySet(), 0.06, 0.02, 0.07, true, false, -0.05f, -0.30f
        ),
        RegionDef(
            "incheon", "인천", "Incheon", setOf("city", "coast", "water", "wetland"),
            "서해의 갯벌과 갈대밭. 철새들이 머무는 쉼터다.",
            "썰물 때 갯벌에 새들이 잔뜩 내려앉아요. 셔터를 준비하세요!",
            80, 60, setOf(Dir.W), setOf(Dir.W), 0.05, 0.02, 0.06, true, false, -0.25f, -0.32f
        ),
        RegionDef(
            "chuncheon", "춘천", "Chuncheon", setOf("water", "wetland", "forest"),
            "호수의 도시. 맑은 물 위에 물새들이 한가로이 떠 있다.",
            "춘천엔 호수가 많아요. 물새 구경엔 여기만 한 곳이 없죠.",
            80, 60, emptySet(), emptySet(), 0.10, 0.02, 0.06, false, true, 0.22f, -0.42f
        ),
        RegionDef(
            "gangneung", "강릉", "Gangneung", setOf("coast", "forest", "city"),
            "푸른 동해와 소나무 숲. 커피와 파도 소리의 도시.",
            "바닷바람에 소나무가 살랑살랑. 숲에도 새가 많아요.",
            80, 60, setOf(Dir.E), setOf(Dir.E), 0.09, 0.02, 0.06, true, false, 0.48f, -0.28f
        ),
        RegionDef(
            "sokcho", "속초", "Sokcho", setOf("mountain", "forest", "coast"),
            "설악산과 동해가 만나는 곳. 산새의 천국.",
            "설악산 새소리는 약이에요. 등산 삼아 새 구경 어때요?",
            80, 60, setOf(Dir.E), setOf(Dir.E), 0.13, 0.09, 0.05, false, false, 0.55f, -0.50f
        ),
        RegionDef(
            "daejeon", "대전", "Daejeon", setOf("city", "field", "forest"),
            "한반도의 한가운데. 어디로 떠나기 좋은 교통 요지.",
            "대전에서라면 어느 지역이든 하루면 다녀올 수 있어요.",
            80, 60, emptySet(), emptySet(), 0.07, 0.02, 0.06, true, false, -0.02f, 0.02f
        ),
        RegionDef(
            "gwangju", "광주", "Gwangju", setOf("city", "forest", "wetland"),
            "산과 습지가 가까운 예술의 고장.",
            "남쪽은 새도 색이 곱지 뭐예요. 팔색조를 기대해 보세요.",
            80, 60, emptySet(), emptySet(), 0.08, 0.02, 0.06, true, true, -0.30f, 0.18f
        ),
        RegionDef(
            "busan", "부산", "Busan", setOf("coast", "water", "mountain", "city"),
            "해운대와 낙동강 하구. 철새 여행의 끝자락.",
            "남쪽엔 해저 터널이 하나 있어요. 무모한 자전거 여행자를 위해서죠!",
            80, 60, setOf(Dir.E, Dir.S), setOf(Dir.E, Dir.S), 0.06, 0.05, 0.05, true, false, 0.62f, 0.10f
        ),
        RegionDef(
            "jeju", "제주", "Jeju", setOf("coast", "mountain", "forest", "wetland"),
            "바람의 섬, 오름의 섬. 귀한 새들이 머무는 곳.",
            "제주엔 없는 게 없어요. 돌하르방처럼 어여쁜 새들도요.",
            80, 60, setOf(Dir.S, Dir.E, Dir.W), setOf(Dir.S, Dir.E, Dir.W), 0.07, 0.12, 0.05, false, false, -0.12f, 0.48f
        )
    )

    val byId: Map<String, RegionDef> = ALL.associateBy { it.id }

    /** 지역 연결: (from, to, from 기준 방향). 양방향으로 해석됨. */
    private data class Link(val a: String, val b: String, val dirFromA: Dir)

    private val LINKS = listOf(
        Link("seoul", "incheon", Dir.W),
        Link("seoul", "chuncheon", Dir.N),
        Link("seoul", "daejeon", Dir.S),
        Link("chuncheon", "gangneung", Dir.E),
        Link("gangneung", "sokcho", Dir.N),
        Link("daejeon", "gwangju", Dir.W),
        Link("daejeon", "busan", Dir.E),
        Link("busan", "jeju", Dir.S)      // 해저 터널!
    )

    fun opposite(d: Dir): Dir = when (d) {
        Dir.N -> Dir.S
        Dir.S -> Dir.N
        Dir.E -> Dir.W
        Dir.W -> Dir.E
    }

    /** 지도상 방향 -> 연결된 지역 id */
    fun exits(id: String): Map<Dir, String> {
        val m = mutableMapOf<Dir, String>()
        for (l in LINKS) {
            if (l.a == id) m[l.dirFromA] = l.b
            if (l.b == id) m[opposite(l.dirFromA)] = l.a
        }
        return m
    }

    /** 두 지역이 직접 연결되어 있는가 */
    fun linked(a: String, b: String): Boolean =
        LINKS.any { (it.a == a && it.b == b) || (it.a == b && it.b == a) }

    /** 지역 소개용 대표 새 (등급 높은 순) */
    fun signatureBirds(r: RegionDef, count: Int = 3): List<BirdDef> =
        Birds.poolFor(r).sortedByDescending { it.tier.star }.take(count)
}
