package com.pizzaandbird.game

/**
 * Pizza and Bird : 피자와 새 — 게임 데이터 정의
 *
 * 새 종류 / 피자 토핑 / 장식 / 지역은 이 파일의 데이터만 추가하면
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

/** 새 게임은 실제 여행의 출발점인 서울에서 시작한다. */
const val START_REGION_ID = "seoul"

/** 게임 안의 모든 금액을 대한민국 원으로 표시한다. */
fun won(v: Int): String = "₩${fmtMoney(v)}"

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

/** 새의 깃무늬. 같은 체형 안에서도 종마다 실루엣과 인상이 겹치지 않게 한다. */
object BirdPatterns {
    const val PLAIN = 0
    const val WING_BARS = 1
    const val STREAKED = 2
    const val BIB = 3
    const val DARK_CAP = 4
    const val SPOTTED = 5
    const val COLLAR = 6
    const val EYE_STRIPE = 7
    const val IRIDESCENT = 8
}

/**
 * 새 픽셀 아트 정보 (Assets.kt에서 실제 비트맵 생성).
 *
 * template:
 * 0=명금, 1=오리, 2=백로/두루미, 3=맹금, 4=올빼미,
 * 5=도요/물떼새, 6=바닷새, 7=딱다구리, 8=비둘기/두견이,
 * 9=물총새, 10=팔색조형, 11=꿩/뜸부기, 12=제비/칼새
 *
 * head/accent/pattern은 기존 팔레트만 바꾸던 방식에서 한 단계 더 나아가
 * 머리색, 볼·날개 포인트, 종별 깃무늬를 독립적으로 표현한다.
 */
class BirdArt(
    val template: Int,
    val body: Int, val belly: Int, val wing: Int,
    val beak: Int, val crest: Int, val leg: Int,
    val scale: Float = 1f,
    val head: Int = body,
    val accent: Int = crest,
    val pattern: Int = BirdPatterns.PLAIN
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
    val art: BirdArt,
    val active: String = "any",              // day / night / any (밤낮 출현 조건)
    val scientificName: String = "",
    val englishName: String = "",
    val orderName: String = "",
    val familyName: String = "",
    val category: String = "",
    val subspecies: List<String> = emptyList()
) {
    val activeLabel: String
        get() = when (active) {
            "night" -> "밤새"
            "day" -> "낮새"
            else -> "종일"
        }
}

object Birds {
    private fun c(v: Long) = v.toInt()

    private val CURATED = listOf(
        // ---------------- 흔함 ----------------
        BirdDef(
            "sparrow", "참새", Tier.COMMON, setOf("city", "field"), 30.0, 3000,
            "동네 어디서나 만날 수 있는 우리의 친구. 수수 한 알에도 행복해한다.",
            null,
            BirdArt(0, c(0xFF9C7A54), c(0xFFEFE3CF), c(0xFF6B4A33), c(0xFF4A3728), c(0xFF6B4A33), c(0xFFB98A4A), pattern = BirdPatterns.STREAKED)
        ),
        BirdDef(
            "bulbul", "직박구리", Tier.COMMON, setOf("city", "forest"), 28.0, 3000,
            "머리에 흰 털이 특징. 목청이 무척 좋아 아침마다 알람 역할을 한다.",
            null,
            BirdArt(0, c(0xFF6D635A), c(0xFFE8DDC8), c(0xFF8A7D6D), c(0xFF3A322C), c(0xFF4F463F), c(0xFFB98A4A), head = c(0xFF4F463F), accent = c(0xFFF2EEE4), pattern = BirdPatterns.EYE_STRIPE)
        ),
        BirdDef(
            "magpie", "까치", Tier.COMMON, setOf("city", "field"), 26.0, 3200,
            "한국의 국조. 아침에 울면 반가운 소식이 온다는 좋은 새.",
            null,
            BirdArt(0, c(0xFF3C3F47), c(0xFFF2F2F0), c(0xFF23252B), c(0xFF23252B), c(0xFF3C3F47), c(0xFF3A3A44), accent = c(0xFF4E6F91), pattern = BirdPatterns.IRIDESCENT)
        ),
        BirdDef(
            "greattit", "박새", Tier.COMMON, setOf("forest", "city"), 26.0, 3200,
            "노란 배에 검은 넥타이를 맨 듯한 깔끔한 패션의 소유자.",
            null,
            BirdArt(0, c(0xFF4F6F52), c(0xFFF2D65A), c(0xFF3A5440), c(0xFF23252B), c(0xFF23252B), c(0xFFB98A4A), head = c(0xFF23252B), accent = c(0xFFF5F1DF), pattern = BirdPatterns.BIB)
        ),
        BirdDef(
            "dove", "멧비둘기", Tier.COMMON, setOf("city", "forest"), 22.0, 3000,
            "목에 무늬가 반짝이는 온화한 새. 구구… 구구…",
            null,
            BirdArt(8, c(0xFFB8A99A), c(0xFFE6DCCB), c(0xFF8F8072), c(0xFF6B5A48), c(0xFF9C8D7E), c(0xFFB0793F), 1.05f, accent = c(0xFF4A4A52), pattern = BirdPatterns.COLLAR)
        ),
        BirdDef(
            "jay", "어치", Tier.COMMON, setOf("forest", "city"), 20.0, 3400,
            "복숭아빛 깃털에 파란 날개. 도토리를 숨겨두고 잊어버리는 숲의 수호자.",
            null,
            BirdArt(0, c(0xFFD9825C), c(0xFFF2E3D0), c(0xFF4F6FA5), c(0xFF23252B), c(0xFFEFE8DC), c(0xFF6B5A48), head = c(0xFFD9825C), accent = c(0xFF69A7D8), pattern = BirdPatterns.WING_BARS)
        ),
        BirdDef(
            "gull", "괭이갈매기", Tier.COMMON, setOf("coast", "water"), 26.0, 3000,
            "바닷가의 주민. 괭이~ 괭이~ 울음소리가 이름이 되었다.",
            null,
            BirdArt(6, c(0xFFF2F2EE), c(0xFFFFFFFF), c(0xFFC9C9C2), c(0xFFF2A33C), c(0xFFD9D9D2), c(0xFFF2A33C), 1.05f, head = c(0xFFF7F7F2), accent = c(0xFF3B3C42), pattern = BirdPatterns.DARK_CAP)
        ),
        // ---------------- 보통 ----------------
        BirdDef(
            "tern", "쇠제비갈매기", Tier.UNCOMMON, setOf("coast", "water"), 12.0, 8000,
            "제비처럼 날카롭게 물위를 가르며 낚시를 즐기는 갈매기.",
            null,
            BirdArt(6, c(0xFFEEF2F4), c(0xFFFFFFFF), c(0xFF6B7D8A), c(0xFFE8B14E), c(0xFF23252B), c(0xFFE8863C), head = c(0xFFEEF2F4), accent = c(0xFF23252B), pattern = BirdPatterns.DARK_CAP)
        ),
        BirdDef(
            "spotduck", "청둥오리", Tier.UNCOMMON, setOf("water", "wetland"), 12.0, 8000,
            "연못과 호수의 단골. 머리의 초록빛이 은은하게 반짝인다.",
            null,
            BirdArt(1, c(0xFF7A6A52), c(0xFFCBB894), c(0xFF3F6D8E), c(0xFFE8B14E), c(0xFF5D6440), c(0xFFE8863C), 1.05f, head = c(0xFF28705B), accent = c(0xFFF2EEE0), pattern = BirdPatterns.COLLAR)
        ),
        BirdDef(
            "tufteduck", "쇠오리", Tier.UNCOMMON, setOf("water", "wetland"), 11.0, 8000,
            "뒤통수에 삐죽 머리를 세운 검은 오리. 물속에 머리를 박고 뒤집혀 먹이를 찾는다.",
            null,
            BirdArt(1, c(0xFF2B2B33), c(0xFFF5F2EA), c(0xFF1E1E26), c(0xFF5A5A66), c(0xFF1A1A20), c(0xFFE8A14E), 1.1f, head = c(0xFF191921), accent = c(0xFFF5F2EA), pattern = BirdPatterns.WING_BARS)
        ),
        BirdDef(
            "egret", "중대백로", Tier.UNCOMMON, setOf("water", "wetland", "coast"), 10.0, 8500,
            "흰 한복을 입은 듯 우아한 겨울 손님. 목을 S자로 접고 한가로이 서 있다.",
            null,
            BirdArt(2, c(0xFFFDFDF8), c(0xFFFFFFFF), c(0xFFE8E4D8), c(0xFFE8B14E), c(0xFFFDFDF8), c(0xFF4A3728), 1.15f)
        ),
        BirdDef(
            "woodpecker", "까막딱다구리", Tier.UNCOMMON, setOf("forest", "mountain"), 10.0, 8000,
            "울창한 숲의 목수. 나무를 두드리는 소리가 숲에 울려 퍼진다.",
            null,
            BirdArt(7, c(0xFF2B2B30), c(0xFF3A3A40), c(0xFF1E1E24), c(0xFFD9D3C8), c(0xFFD9403A), c(0xFF6B7280), 1.1f, head = c(0xFF202027), accent = c(0xFFD9403A), pattern = BirdPatterns.DARK_CAP)
        ),
        BirdDef(
            "greenwood", "청딱다구리", Tier.UNCOMMON, setOf("forest"), 9.0, 8500,
            "등이 청록색으로 빛나는 우리 숲의 딱따구리. 수컷의 정수리는 빨갛다.",
            null,
            BirdArt(7, c(0xFF5D9E5F), c(0xFFF2E8D0), c(0xFF3F6F44), c(0xFF23252B), c(0xFFD9403A), c(0xFF6B7280), head = c(0xFF758B64), accent = c(0xFFD9403A), pattern = BirdPatterns.DARK_CAP)
        ),
        BirdDef(
            "cuckoo", "뻐꾸기", Tier.UNCOMMON, setOf("forest", "field"), 9.0, 8000,
            "뻐꾹~ 뻐꾹~ 봄을 알리는 소리의 주인공. 남의 둥지에 알을 맡기는 야무진 전략가.",
            null,
            BirdArt(8, c(0xFF8A8F98), c(0xFFF2F0E8), c(0xFF5F646D), c(0xFF6B6455), c(0xFF6F747D), c(0xFF6B7280), 1.05f, pattern = BirdPatterns.STREAKED)
        ),
        BirdDef(
            "nightheron", "검은댕기해오라기", Tier.UNCOMMON, setOf("wetland", "water"), 7.0, 9000,
            "해 질 녘 습지에 어둠처럼 나타나는 새. 짧은 다리로 꼿꼿이 서서 기다린다.",
            null,
            BirdArt(2, c(0xFF3A3F4A), c(0xFFE8E4D8), c(0xFF262A33), c(0xFF23252B), c(0xFF23252B), c(0xFFE8A14E), head = c(0xFF23252B), accent = c(0xFFF1EEE4), pattern = BirdPatterns.DARK_CAP),
            "night"
        ),
        BirdDef(
            "owl", "올빼미", Tier.UNCOMMON, setOf("forest"), 8.0, 10000,
            "밤의 숲 지기. 머리를 거꾸로 270도나 돌릴 수 있다. 후~ 후~.",
            null,
            BirdArt(4, c(0xFF8A6F4F), c(0xFFE8D9B8), c(0xFF6B5438), c(0xFFD9A03C), c(0xFF6B5438), c(0xFFD9A03C), accent = c(0xFF3F3429), pattern = BirdPatterns.SPOTTED),
            "night"
        ),
        // ---------------- 희귀 ----------------
        BirdDef(
            "kestrel", "황조롱이", Tier.RARE, setOf("field", "mountain", "coast"), 5.0, 20000,
            "하늘에 떠서 들판을 살피는 작은 매. 바람 위에서 멈춰 서기도 한다.",
            null,
            BirdArt(3, c(0xFFB06A3C), c(0xFFE8D5B0), c(0xFF7A4A2B), c(0xFF4A3728), c(0xFF6B7D8A), c(0xFFF2D06B), 1.1f, pattern = BirdPatterns.STREAKED)
        ),
        BirdDef(
            "kite", "말똥가리", Tier.RARE, setOf("coast", "field", "city"), 5.0, 21000,
            "갈색 깃털에 가위꼬리를 가른 바다의 매. 항구 하늘을 빙빙 돌며 유영한다.",
            null,
            BirdArt(3, c(0xFF8A5A3C), c(0xFFF0DEC0), c(0xFF6B4430), c(0xFFD9A03C), c(0xFF6B4430), c(0xFFE8B14E), 1.2f, pattern = BirdPatterns.STREAKED)
        ),
        BirdDef(
            "goshawk", "참매", Tier.RARE, setOf("forest", "mountain"), 4.5, 22000,
            "숲의 사냥꾼. 노란 눈이 초롱초롱한 대형 맹금. 가까이서 보면 심장이 뛴다.",
            null,
            BirdArt(3, c(0xFF5F6B78), c(0xFFF2F0EA), c(0xFF4A5460), c(0xFFD9A03C), c(0xFF4A5460), c(0xFFE8B14E), 1.15f, pattern = BirdPatterns.STREAKED)
        ),
        BirdDef(
            "kingfisher", "물총새", Tier.RARE, setOf("water"), 5.0, 22000,
            "물가의 보석. 파란 번개처럼 스쳐 지나가 물고기를 낚는다.",
            null,
            BirdArt(9, c(0xFF3F8FB5), c(0xFFF2913C), c(0xFF2F6FA0), c(0xFF23252B), c(0xFF2F6FA0), c(0xFFE8863C), 0.85f, head = c(0xFF2876A4), accent = c(0xFF67C1D5), pattern = BirdPatterns.EYE_STRIPE)
        ),
        BirdDef(
            "pitta", "팔색조", Tier.RARE, setOf("forest"), 4.0, 20000,
            "무지개 빛깔 여덟 색을 두른 숲의 보석. 운이 좋아야 만날 수 있다.",
            setOf("gwangju", "jeju", "ulsan"),
            BirdArt(10, c(0xFF4F8F6A), c(0xFFE8E0C8), c(0xFF3F6FB0), c(0xFF23252B), c(0xFFD9403A), c(0xFFB98A4A), head = c(0xFF3A2D28), accent = c(0xFFD9403A), pattern = BirdPatterns.IRIDESCENT)
        ),
        BirdDef(
            "mandarin", "원앙", Tier.RARE, setOf("water", "forest"), 4.5, 24000,
            "무지개色 깃털의 오리. 부부 금실이 좋아 예부터 사랑의 상징이었다.",
            setOf("gwangju", "jeonju", "jeju", "ulsan"),
            BirdArt(1, c(0xFF6B4A5E), c(0xFFF5EBD0), c(0xFF4F7DAD), c(0xFFC9503A), c(0xFFE8863C), c(0xFFD97B4A), 1.05f, head = c(0xFF356D5A), accent = c(0xFFE8863C), pattern = BirdPatterns.IRIDESCENT)
        ),
        BirdDef(
            "eagleowl", "수리부엉이", Tier.RARE, setOf("mountain", "forest"), 3.5, 30000,
            "밤 산림의 왕. 귀깃을 세운 위엄 있는 얼굴로 어둠을 내려다본다.",
            setOf("sokcho", "jeju", "ulsan", "daegu"),
            BirdArt(4, c(0xFF9C7A54), c(0xFFE8D5B0), c(0xFF7A5A3A), c(0xFFD9A03C), c(0xFF7A5A3A), c(0xFFD9A03C), 1.3f, head = c(0xFF7A5A3A), accent = c(0xFF3F3429), pattern = BirdPatterns.STREAKED),
            "night"
        ),
        BirdDef(
            "redcrown", "재두루미", Tier.RARE, setOf("wetland", "water"), 3.0, 32000,
            "목덜미가 하얀 겨울 귀빈. 두루미보다 눈이 조금 더 검다.",
            setOf("seoul", "chuncheon", "incheon", "jeju"),
            BirdArt(2, c(0xFFF5F2EA), c(0xFFFFFFFF), c(0xFFD9D3C8), c(0xFF6B4F35), c(0xFFD9403A), c(0xFF6B4F35), 1.25f, pattern = BirdPatterns.DARK_CAP)
        ),
        // ---------------- 전설 ----------------
        BirdDef(
            "crane", "두루미", Tier.LEGEND, setOf("wetland", "water"), 1.2, 60000,
            "머리에 붉은 왕관을 얹은 격식 있는 겨울 손님. 만나면 한 해가 행복하다.",
            setOf("seoul", "chuncheon", "incheon", "jeju"),
            BirdArt(2, c(0xFFF5F2EA), c(0xFFFFFFFF), c(0xFFD9D3C8), c(0xFF6B4F35), c(0xFFD9403A), c(0xFF6B4F35), 1.3f, accent = c(0xFFD9403A), pattern = BirdPatterns.DARK_CAP)
        ),
        BirdDef(
            "stork", "황새", Tier.LEGEND, setOf("wetland", "water"), 1.0, 65000,
            "붉은 부리와 다리로 한 발로 서서 잠드는, 전설 속 아기를 물어다 주는 새.",
            setOf("chuncheon", "seoul", "jeju"),
            BirdArt(2, c(0xFFFDFAF2), c(0xFFFFFFFF), c(0xFFE0DCC8), c(0xFFB03A30), c(0xFFFDFAF2), c(0xFFB03A30), 1.35f, pattern = BirdPatterns.DARK_CAP)
        )
    )

    private val curatedByName: Map<String, BirdDef> = CURATED.associateBy { it.name }

    val ALL: List<BirdDef> = OfficialBirdChecklist.ALL.map { entry ->
        curatedByName[entry.koreanName]?.let { enrich(it, entry) } ?: generated(entry)
    }

    val byId: Map<String, BirdDef> = ALL.associateBy { it.id }

    /** 지역 서식지 + 밤낮에 맞는 새 풀 */
    fun poolFor(region: RegionDef, night: Boolean = false): List<BirdDef> = ALL.filter { def ->
        def.habitats.intersect(region.habitats).isNotEmpty() &&
                (def.onlyRegions == null || region.id in def.onlyRegions) &&
                (def.active == "any" || (if (night) def.active == "night" else def.active == "day"))
    }

    private fun enrich(def: BirdDef, entry: BirdChecklistEntry): BirdDef = BirdDef(
        id = def.id,
        name = def.name,
        tier = def.tier,
        habitats = def.habitats,
        weight = def.weight,
        reward = def.reward,
        desc = def.desc,
        onlyRegions = def.onlyRegions,
        art = def.art,
        active = def.active,
        scientificName = entry.scientificName,
        englishName = entry.englishName,
        orderName = entry.orderName,
        familyName = entry.familyName,
        category = entry.category,
        subspecies = entry.subspecies
    )

    private fun generated(entry: BirdChecklistEntry): BirdDef {
        val tier = tierFor(entry)
        val habitats = habitatsFor(entry)
        return BirdDef(
            id = generatedId(entry),
            name = entry.koreanName,
            tier = tier,
            habitats = habitats,
            weight = weightFor(tier),
            reward = rewardFor(tier),
            desc = officialDesc(entry),
            onlyRegions = null,
            art = artFor(entry, habitats),
            active = activeFor(entry),
            scientificName = entry.scientificName,
            englishName = entry.englishName,
            orderName = entry.orderName,
            familyName = entry.familyName,
            category = entry.category,
            subspecies = entry.subspecies
        )
    }

    private fun generatedId(entry: BirdChecklistEntry): String {
        val sb = StringBuilder("bird_")
        var lastUnderscore = false
        for (ch in entry.scientificName.lowercase()) {
            val ok = (ch in 'a'..'z') || (ch in '0'..'9')
            if (ok) {
                sb.append(ch)
                lastUnderscore = false
            } else if (!lastUnderscore) {
                sb.append('_')
                lastUnderscore = true
            }
        }
        while (sb.length > 5 && sb[sb.length - 1] == '_') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    private val COMMON_NAMES = setOf(
        "참새", "까치", "박새", "쇠박새", "곤줄박이", "직박구리", "멧비둘기", "흰뺨검둥오리",
        "청둥오리", "쇠오리", "괭이갈매기", "재갈매기", "왜가리", "중대백로", "쇠백로",
        "물닭", "제비", "붉은머리오목눈이", "동박새", "딱새", "검은등할미새", "알락할미새",
        "노랑턱멧새", "방울새", "오목눈이", "어치", "큰부리까마귀", "물까치", "찌르레기"
    )

    private fun tierFor(entry: BirdChecklistEntry): Tier {
        val name = entry.koreanName
        val family = entry.familyName
        val text = "$name ${entry.englishName} ${entry.scientificName}"
        if (entry.category == "가-2") return Tier.LEGEND
        if (name in COMMON_NAMES) return Tier.COMMON
        if (hasAny(text, "원앙사촌", "알바트로스", "군함조", "큰홍학", "뿔제비갈매기", "크낙새", "초원멧새")) {
            return Tier.LEGEND
        }
        if (hasAny(text, "아메리카", "미국", "북미", "자바", "히말라야", "흰매", "검독수리", "수리", "황새", "저어새", "두루미", "팔색조", "물수리", "홍학", "뜸부기", "바다오리", "바위")) {
            return Tier.RARE
        }
        if (family in setOf("오리과", "백로과", "갈매기과", "박새과", "까마귀과", "직박구리과", "제비과", "할미새과", "멧새과", "되새과", "지빠귀과", "솔새과", "뜸부기과", "딱다구리과", "비둘기과")) {
            return Tier.UNCOMMON
        }
        return Tier.RARE
    }

    private fun habitatsFor(entry: BirdChecklistEntry): Set<String> {
        val name = entry.koreanName
        val family = entry.familyName
        val order = entry.orderName
        val text = "$name $family $order ${entry.englishName}"
        if (family == "오리과" || order == "논병아리목" || order == "아비목") return setOf("water", "wetland", "coast")
        if (family in setOf("도요과", "물떼새과", "검은머리물떼새과", "장다리물떼새과", "호사도요과", "물꿩과", "제비물떼새과")) return setOf("coast", "wetland", "water")
        if (family in setOf("갈매기과", "도둑갈매기과", "바다오리과", "알바트로스과", "바다제비과", "슴새과", "군함조과", "얼가니새과", "가마우지과")) return setOf("coast", "water")
        if (family in setOf("황새과", "저어새과", "백로과", "사다새과", "홍학과", "뜸부기과", "두루미과")) return setOf("wetland", "water", "field")
        if (order == "수리목" || order == "매목") return setOf("field", "mountain", "coast")
        if (order == "올빼미목" || order == "쏙독새목") return setOf("forest", "mountain")
        if (order == "닭목" || order == "사막꿩목" || order == "느시목") return setOf("field", "mountain")
        if (order == "비둘기목") return setOf("city", "forest", "field")
        if (order == "두견이목") return setOf("forest", "field")
        if (order == "파랑새목") return if (name.contains("물총새")) setOf("water", "forest") else setOf("forest", "field")
        if (order == "딱다구리목" || order == "코뿔새목") return setOf("forest", "mountain")
        if (order == "칼새목") return setOf("city", "coast", "field")
        if (order == "참새목") {
            if (hasAny(text, "까마귀", "까치", "직박구리", "참새", "제비", "찌르레기")) return setOf("city", "field", "forest")
            if (hasAny(text, "종다리", "멧새", "할미새", "때까치", "되새", "방울새", "양진이")) return setOf("field", "city")
            if (hasAny(text, "물까마귀", "물딱새", "개개비", "덤불")) return setOf("water", "wetland", "forest")
            return setOf("forest", "field")
        }
        return setOf("field", "forest")
    }

    private fun activeFor(entry: BirdChecklistEntry): String = when (entry.orderName) {
        "올빼미목", "쏙독새목" -> "night"
        else -> "any"
    }

    private fun officialDesc(entry: BirdChecklistEntry): String {
        val subs = if (entry.subspecies.isEmpty()) {
            "기록 아종 없음"
        } else {
            "기록 아종 ${entry.subspecies.size}개: " + entry.subspecies.take(2).joinToString(", ") +
                    if (entry.subspecies.size > 2) " 외" else ""
        }
        return "${entry.orderName} ${entry.familyName}의 공식 기록종(${entry.category}). " +
                "학명 ${entry.scientificName}, 영명 ${entry.englishName}. $subs."
    }

    private fun weightFor(tier: Tier): Double = when (tier) {
        Tier.COMMON -> 18.0
        Tier.UNCOMMON -> 8.0
        Tier.RARE -> 2.4
        Tier.LEGEND -> 0.65
    }

    private fun rewardFor(tier: Tier): Int = when (tier) {
        Tier.COMMON -> 350
        Tier.UNCOMMON -> 900
        Tier.RARE -> 2400
        Tier.LEGEND -> 6500
    }

    private fun artFor(entry: BirdChecklistEntry, habitats: Set<String>): BirdArt {
        val family = entry.familyName
        val order = entry.orderName
        val text = "${entry.koreanName} ${entry.englishName} $family $order"

        // 598종을 단순 5개 체형에 끼워 넣지 않고 생태적 체형에 맞는 13개 실루엣으로 분류한다.
        val template = when {
            order == "올빼미목" -> 4
            order == "수리목" || order == "매목" -> 3
            family in setOf("황새과", "저어새과", "백로과", "사다새과", "홍학과", "두루미과") -> 2
            family in setOf("도요과", "물떼새과", "검은머리물떼새과", "장다리물떼새과", "호사도요과", "물꿩과", "제비물떼새과") -> 5
            family in setOf("갈매기과", "도둑갈매기과", "바다오리과", "알바트로스과", "바다제비과", "슴새과", "군함조과", "얼가니새과", "가마우지과") -> 6
            family == "오리과" || order == "논병아리목" || order == "아비목" || hasAny(text, "오리", "기러기", "고니") -> 1
            order == "딱다구리목" || order == "코뿔새목" -> 7
            order == "비둘기목" || order == "두견이목" -> 8
            order == "파랑새목" -> 9
            family == "팔색조과" -> 10
            order in setOf("닭목", "사막꿩목", "느시목") || family == "뜸부기과" -> 11
            order == "칼새목" || family == "제비과" -> 12
            else -> 0
        }

        var body = hashedColor(entry, 0)
        var belly = lighten(body)
        var wing = darken(body)
        var beak = c(0xFF6B4A33)
        var crest = wing
        var leg = c(0xFFB98A4A)

        if (hasAny(text, "흰", "백", "White", "Swan", "Egret")) {
            body = c(0xFFF2F2EE); belly = c(0xFFFFFFFF); wing = c(0xFFD6D6CE); crest = body
        }
        if (hasAny(text, "검은", "Black")) {
            body = c(0xFF2B2B30); belly = c(0xFF4A4A52); wing = c(0xFF1E1E24); crest = wing
        }
        if (hasAny(text, "노랑", "Yellow")) {
            body = c(0xFFD8B84E); belly = c(0xFFF2E38A); wing = c(0xFF7A6A38); crest = body
        }
        if (hasAny(text, "붉", "홍", "Red", "Rufous")) {
            body = c(0xFFB45A3C); belly = c(0xFFE8C0A0); wing = c(0xFF7A3F30); crest = body
        }
        if (hasAny(text, "파랑", "청", "Blue", "Kingfisher")) {
            body = c(0xFF3F7FB0); belly = c(0xFFE8D8A8); wing = c(0xFF2F5F90); crest = body
        }
        if (hasAny(text, "녹색", "Green")) {
            body = c(0xFF4F8F5E); belly = c(0xFFE2E8C8); wing = c(0xFF356F45); crest = body
        }
        if (family == "갈매기과") {
            body = c(0xFFF2F2EE); belly = c(0xFFFFFFFF); wing = c(0xFFC9C9C2)
            beak = c(0xFFF2A33C); leg = beak; crest = c(0xFF3B3C42)
        }
        if (template == 3) {
            val raptorBodies = intArrayOf(c(0xFF8A5A3C), c(0xFF9B6848), c(0xFF6A625C), c(0xFF79513D))
            body = raptorBodies[(entry.scientificName.hashCode() and 0x7fffffff) % raptorBodies.size]
            belly = lighten(body); wing = darken(body); beak = c(0xFFD9A03C); leg = c(0xFFF2D06B); crest = wing
        }
        if (template == 4) {
            body = c(0xFF8A6F4F); belly = c(0xFFE8D9B8); wing = c(0xFF6B5438)
            beak = c(0xFFD9A03C); crest = wing; leg = c(0xFFD9A03C)
        }
        if (habitats.contains("water") || habitats.contains("wetland")) {
            beak = if (hasAny(text, "오리", "기러기", "고니")) c(0xFFE8B14E) else beak
            leg = c(0xFFE8863C)
        }

        var head = body
        var accent = crest
        if (template == 1) {
            head = if (hasAny(text, "청둥", "Mallard", "원앙", "Mandarin")) c(0xFF28705B) else darken(body)
            accent = if (head != body) c(0xFFF2EEE0) else lighten(wing)
        }
        if (template == 6) {
            head = body
            accent = if (hasAny(text, "제비갈매기", "Tern", "괭이", "Black-tailed")) c(0xFF303139) else wing
        }
        if (template == 7 && hasAny(text, "붉은", "홍", "Red", "청딱")) accent = c(0xFFD9403A)
        if (template == 9) accent = lighten(body)

        var pattern = when (template) {
            1 -> BirdPatterns.COLLAR
            2 -> if (hasAny(text, "두루미", "황새", "Crane", "Stork")) BirdPatterns.DARK_CAP else BirdPatterns.PLAIN
            3 -> BirdPatterns.STREAKED
            4 -> BirdPatterns.SPOTTED
            5 -> BirdPatterns.EYE_STRIPE
            6 -> BirdPatterns.DARK_CAP
            7 -> BirdPatterns.WING_BARS
            8 -> BirdPatterns.COLLAR
            9 -> BirdPatterns.EYE_STRIPE
            10 -> BirdPatterns.IRIDESCENT
            11 -> BirdPatterns.SPOTTED
            12 -> BirdPatterns.COLLAR
            else -> when (family) {
                "지빠귀과", "딱새과" -> BirdPatterns.SPOTTED
                "참새과", "멧새과", "되새과", "종다리과" -> BirdPatterns.STREAKED
                "박새과", "할미새과" -> BirdPatterns.BIB
                "까마귀과", "찌르레기과" -> BirdPatterns.IRIDESCENT
                "직박구리과" -> BirdPatterns.DARK_CAP
                "동박새과", "솔새과", "휘파람새과" -> BirdPatterns.EYE_STRIPE
                else -> 1 + ((entry.scientificName.hashCode() and 0x7fffffff) % 7)
            }
        }
        if (hasAny(text, "점박", "알락", "Spotted", "Speckled")) pattern = BirdPatterns.SPOTTED
        if (hasAny(text, "검은머리", "검은이마", "Black-headed", "Black-capped")) pattern = BirdPatterns.DARK_CAP
        if (hasAny(text, "흰눈썹", "Eyebrow", "White-eye")) pattern = BirdPatterns.EYE_STRIPE

        val scale = when (template) {
            2 -> 1.12f
            3 -> 1.08f
            4 -> 1.12f
            5 -> 0.96f
            6 -> 1.04f
            7 -> 1.02f
            8 -> 1.04f
            9 -> 0.92f
            10 -> 0.96f
            11 -> 1.08f
            12 -> 0.92f
            else -> 1f
        }
        return BirdArt(template, body, belly, wing, beak, crest, leg, scale, head, accent, pattern)
    }

    private fun hashedColor(entry: BirdChecklistEntry, salt: Int): Int {
        val palette = intArrayOf(
            c(0xFF9C7A54), c(0xFF7A6A52), c(0xFF6D635A), c(0xFF8A7D6D),
            c(0xFFB06A3C), c(0xFF5F6B78), c(0xFF4F6F52), c(0xFFD9825C)
        )
        val h = entry.scientificName.hashCode() xor (salt * 1103515245)
        return palette[(h and 0x7fffffff) % palette.size]
    }

    private fun lighten(color: Int): Int {
        val r = ((color shr 16) and 0xFF)
        val g = ((color shr 8) and 0xFF)
        val b = (color and 0xFF)
        return c(0xFF000000) or (((r + 80).coerceAtMost(255)) shl 16) or (((g + 80).coerceAtMost(255)) shl 8) or (b + 80).coerceAtMost(255)
    }

    private fun darken(color: Int): Int {
        val r = ((color shr 16) and 0xFF)
        val g = ((color shr 8) and 0xFF)
        val b = (color and 0xFF)
        return c(0xFF000000) or ((r * 65 / 100) shl 16) or ((g * 65 / 100) shl 8) or (b * 65 / 100)
    }

    private fun hasAny(text: String, vararg words: String): Boolean = words.any { text.contains(it) }

}

// ---------------------------------------------------------------------------
// 피자 (토핑) / 카메라 / 장식
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

/** 피자 토핑 — 굽기 미니게임의 난이도와 효과가 달라진다 */
object Toppings {
    class Topping(
        val id: Int, val name: String, val emoji: String,
        val hungerBonus: Int, val luckBonus: Int,
        val cursorSpeed: Float,        // 커서 속도 배율 (높을수록 어려움)
        val perfectW: Float            // 걸작(초록) 구간 폭 (0~1)
    )

    val ALL = listOf(
        Topping(0, "치즈", "🧀", 0, 0, 1.0f, 0.26f),
        Topping(1, "버섯", "🍄", -4, 6, 1.15f, 0.32f),
        Topping(2, "불고기", "🥩", 8, 2, 1.38f, 0.20f)
    )

    val byId: Map<Int, Topping> = ALL.associateBy { it.id }

    fun of(id: Int): Topping = byId[id] ?: ALL[0]
}

/** 집 장식 소품 — 구매 후 칸에 배치하면 행운 보너스 */
object Decors {
    class Decor(
        val id: Int, val name: String, val emoji: String,
        val cost: Int, val luck: Int, val desc: String
    )

    val ALL = listOf(
        Decor(0, "선인장 화분", "🌵", 40000, 1, "작지만 튼튼한 친구. 물은 아껴 주세요."),
        Decor(1, "원목 책장", "📚", 120000, 2, "조류 도감과 여행 수첩이 가득한 책장."),
        Decor(2, "포근한 러그", "🧶", 80000, 2, "맨발로 밟으면 기분이 좋아지는 러그."),
        Decor(3, "스탠드 조명", "💡", 150000, 2, "따뜻한 불빛. 밤에 집 안을 환히 밝혀요."),
        Decor(4, "탐조 트로피", "🏆", 250000, 3, "첫 사진 콘테스트 입상 기념품!"),
        Decor(5, "빈티지 라디오", "📻", 100000, 2, "드르륵 돌리면 새소리 방송이 나와요.")
    )

    val byId: Map<Int, Decor> = ALL.associateBy { it.id }

    fun of(id: Int): Decor? = byId[id]
}

/**
 * 집 내부 인테리어 스타일.
 * 가격은 일회성 리모델링 비용이며, 한 번 산 스타일은 이사해도 계속 사용할 수 있다.
 * 색상은 HomeScene에서 실제 바닥/벽/포인트 렌더링에 사용된다.
 */
class HouseStyle(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val desc: String,
    val wallTint: Int,
    val floorTint: Int,
    val accentTint: Int
)

object HouseStyles {
    val ALL = listOf(
        HouseStyle(
            "cozy", "햇살 가득 아늑한 집", "🪵", 0,
            "나무 바닥과 크림색 벽의 기본 인테리어",
            0xFFE8D4B8.toInt(), 0xFFC99D6B.toInt(), 0xFFE9B44C.toInt()
        ),
        HouseStyle(
            "hanok", "서울 한옥 감성", "🏯", 5000000,
            "단정한 나무 기둥과 따뜻한 한지 벽",
            0xFFD9B892.toInt(), 0xFF9B633E.toInt(), 0xFFB83B35.toInt()
        ),
        HouseStyle(
            "modern", "모던 스튜디오", "🛋", 12000000,
            "차분한 회색 벽과 깔끔한 청록 포인트",
            0xFFD8E0E5.toInt(), 0xFF8B9AA5.toInt(), 0xFF4F9DA6.toInt()
        ),
        HouseStyle(
            "garden", "초록 정원형", "🪴", 20000000,
            "식물과 햇빛이 어울리는 편안한 초록 인테리어",
            0xFFD7E3C3.toInt(), 0xFF9AB27C.toInt(), 0xFF6FAE57.toInt()
        )
    )

    val byId: Map<String, HouseStyle> = ALL.associateBy { it.id }
    fun of(id: String): HouseStyle = byId[id] ?: ALL.first()
}

/** 지역별 작은 집 매매가(대한민국 원). 서울은 새 게임에서 이미 보유한다. */
object HousePrices {
    private val byRegion = mapOf(
        "seoul" to 85000000,
        "incheon" to 42000000,
        "chuncheon" to 30000000,
        "gangneung" to 38000000,
        "sokcho" to 36000000,
        "daejeon" to 32000000,
        "jeonju" to 25000000,
        "daegu" to 28000000,
        "gwangju" to 26000000,
        "ulsan" to 34000000,
        "busan" to 52000000,
        "jeju" to 48000000
    )

    fun forRegion(regionId: String): Int = byRegion[regionId] ?: 30000000
}

/** 카메라 등급 (상점에서 업그레이드) */
object CameraDefs {
    class Cam(val name: String, val rangeTiles: Float, val cost: Int, val qualityBonus: Int)

    val LEVELS = listOf(
        Cam("폰 카메라", 4.5f, 0, 0),
        Cam("컴팩트 카메라", 6.0f, 80000, 0),
        Cam("미러리스", 7.5f, 250000, 1),
        Cam("DSLR", 9.0f, 600000, 1),
        Cam("프리미엄 DSLR", 11.0f, 1500000, 2)
    )

    fun name(level: Int): String = LEVELS[(level - 1).coerceIn(0, LEVELS.size - 1)].name
    fun range(level: Int): Float = LEVELS[(level - 1).coerceIn(0, LEVELS.size - 1)].rangeTiles
}

/** 이사 용달·중개 수수료 (대한민국 원) */
const val MOVE_COST = 300000

/** 들고 다닐 수 있는 피자 최대 개수 */
const val PIZZA_CAP = 6

/** 하루(낮+밤 한 사이클) 길이 — 실제 초 */
const val DAY_SECONDS = 300f

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
    val mmY: Float,
    val emoji: String    // 지역 대표 이모지
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
            40, 30, emptySet(), emptySet(), 0.06, 0.02, 0.07, true, false, -0.05f, -0.30f, "🏙"
        ),
        RegionDef(
            "incheon", "인천", "Incheon", setOf("city", "coast", "water", "wetland"),
            "서해의 갯벌과 갈대밭. 철새들이 머무는 쉼터다.",
            "썰물 때 갯벌에 새들이 잔뜩 내려앉아요. 셔터를 준비하세요!",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.05, 0.02, 0.06, true, false, -0.25f, -0.32f, "✈"
        ),
        RegionDef(
            "chuncheon", "춘천", "Chuncheon", setOf("water", "wetland", "forest"),
            "호수의 도시. 맑은 물 위에 물새들이 한가로이 떠 있다.",
            "춘천엔 호수가 많아요. 물새 구경엔 여기만 한 곳이 없죠.",
            40, 30, emptySet(), emptySet(), 0.10, 0.02, 0.06, false, true, 0.22f, -0.42f, "🏞"
        ),
        RegionDef(
            "gangneung", "강릉", "Gangneung", setOf("coast", "forest", "city"),
            "푸른 동해와 소나무 숲. 커피와 파도 소리의 도시.",
            "바닷바람에 소나무가 살랑살랑. 숲에도 새가 많아요.",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.09, 0.02, 0.06, true, false, 0.48f, -0.28f, "🌊"
        ),
        RegionDef(
            "sokcho", "속초", "Sokcho", setOf("mountain", "forest", "coast"),
            "설악산과 동해가 만나는 곳. 산새의 천국.",
            "설악산 새소리는 약이에요. 등산 삼아 새 구경 어때요?",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.13, 0.09, 0.05, false, false, 0.55f, -0.50f, "⛰"
        ),
        RegionDef(
            "daejeon", "대전", "Daejeon", setOf("city", "field", "forest"),
            "한반도의 한가운데. 어디로 떠나기 좋은 교통 요지.",
            "대전에서라면 어느 지역이든 하루면 다녀올 수 있어요.",
            40, 30, emptySet(), emptySet(), 0.07, 0.02, 0.06, true, false, -0.02f, 0.02f, "🚄"
        ),
        RegionDef(
            "jeonju", "전주", "Jeonju", setOf("city", "field"),
            "한옥의 고장. 굴뚝 연기와 피자 향이 어우러진 낭만의 도시.",
            "한옥마을 골목을 걷다 보면 지붕 위에 새들이 앉아 있어요.",
            40, 30, emptySet(), emptySet(), 0.08, 0.02, 0.09, true, false, -0.38f, 0.04f, "🏯"
        ),
        RegionDef(
            "daegu", "대구", "Daegu", setOf("city", "mountain"),
            "팔공산 자락의 사과 도시. 산새와 골목의 어치가 반갑다.",
            "팔공산 바람 좋을 때 올라가 보세요. 큰 새들이 빙글빙글 돌아요.",
            40, 30, emptySet(), emptySet(), 0.07, 0.05, 0.06, true, false, 0.32f, 0.12f, "🍎"
        ),
        RegionDef(
            "gwangju", "광주", "Gwangju", setOf("city", "forest", "wetland"),
            "산과 습지가 가까운 예술의 고장.",
            "남쪽은 새도 색이 곱지 뭐예요. 팔색조를 기대해 보세요.",
            40, 30, emptySet(), emptySet(), 0.08, 0.02, 0.06, true, true, -0.30f, 0.20f, "🎨"
        ),
        RegionDef(
            "ulsan", "울산", "Ulsan", setOf("coast", "mountain", "city"),
            "동해 최남단, 고래의 도시. 간절곶에서 해돋이 새소리를 듣는다.",
            "바다 위로 매가 떠요. 등대 아래에서 사진 한 장 어때요?",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.09, 0.06, 0.05, true, false, 0.56f, -0.08f, "🐳"
        ),
        RegionDef(
            "busan", "부산", "Busan", setOf("coast", "water", "mountain", "city"),
            "해운대와 낙동강 하구. 철새 여행의 끝자락.",
            "남쪽엔 해저 터널이 하나 있어요. 무모한 자전거 여행자를 위해서죠!",
            40, 30, setOf(Dir.E, Dir.S), setOf(Dir.E, Dir.S), 0.06, 0.05, 0.05, true, false, 0.62f, 0.12f, "🌉"
        ),
        RegionDef(
            "jeju", "제주", "Jeju", setOf("coast", "mountain", "forest", "wetland"),
            "바람의 섬, 오름의 섬. 귀한 새들이 머무는 곳.",
            "제주엔 없는 게 없어요. 돌하르방처럼 어여쁜 새들도요.",
            40, 30, setOf(Dir.S, Dir.E, Dir.W), setOf(Dir.S, Dir.E, Dir.W), 0.07, 0.12, 0.05, false, false, -0.12f, 0.48f, "🏝"
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
        Link("gwangju", "jeonju", Dir.E),
        Link("jeonju", "daejeon", Dir.E),
        Link("daejeon", "gwangju", Dir.S),
        Link("daejeon", "daegu", Dir.E),
        Link("daegu", "ulsan", Dir.E),
        Link("ulsan", "busan", Dir.S),
        Link("busan", "jeju", Dir.S)       // 해저 터널!
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
