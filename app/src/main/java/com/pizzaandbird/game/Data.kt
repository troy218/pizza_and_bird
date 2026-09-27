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

/**
 * 두 색을 섞는다. k=0 이면 원래 색, k=1 이면 대상 색.
 * 버튼을 눌렀을 때 살짝 어두워지는 피드백 등에 공통으로 쓴다.
 */
fun blendToward(color: Int, target: Int, k: Float): Int {
    val t = k.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val a = (color shr shift) and 0xFF
        val b = (target shr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
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
            setOf("seoul", "chuncheon", "incheon", "jeju", "cheorwon", "imjin", "junam", "suncheon", "gongneung"),
            BirdArt(2, c(0xFFF5F2EA), c(0xFFFFFFFF), c(0xFFD9D3C8), c(0xFF6B4F35), c(0xFFD9403A), c(0xFF6B4F35), 1.25f, pattern = BirdPatterns.DARK_CAP)
        ),
        // ---------------- 전설 ----------------
        BirdDef(
            "crane", "두루미", Tier.LEGEND, setOf("wetland", "water"), 1.2, 60000,
            "머리에 붉은 왕관을 얹은 격식 있는 겨울 손님. 만나면 한 해가 행복하다.",
            setOf("seoul", "chuncheon", "incheon", "jeju", "cheorwon", "imjin", "junam", "gongneung"),
            BirdArt(2, c(0xFFF5F2EA), c(0xFFFFFFFF), c(0xFFD9D3C8), c(0xFF6B4F35), c(0xFFD9403A), c(0xFF6B4F35), 1.3f, accent = c(0xFFD9403A), pattern = BirdPatterns.DARK_CAP)
        ),
        BirdDef(
            "stork", "황새", Tier.LEGEND, setOf("wetland", "water"), 1.0, 65000,
            "붉은 부리와 다리로 한 발로 서서 잠드는, 전설 속 아기를 물어다 주는 새.",
            setOf("chuncheon", "seoul", "jeju", "upo", "junam", "cheorwon", "suncheon"),
            BirdArt(2, c(0xFFFDFAF2), c(0xFFFFFFFF), c(0xFFE0DCC8), c(0xFFB03A30), c(0xFFFDFAF2), c(0xFFB03A30), 1.35f, pattern = BirdPatterns.DARK_CAP)
        ),

        // ============ 대표 탐조지에서 만나는 새들 ============
        BirdDef(
            "cormorant", "민물가마우지", Tier.COMMON, setOf("water", "coast", "wetland"), 20.0, 3500,
            "검은 잠수부. 물속을 헤엄쳐 물고기를 쫓고, 바위 위에서 날개를 펴 말린다.",
            null,
            BirdArt(1, c(0xFF2B2E36), c(0xFF3A3E47), c(0xFF1E2027), c(0xFFD9B06B), c(0xFF1E2027), c(0xFF3A3A44), 1.15f)
        ),
        BirdDef(
            "reedwarbler", "개개비", Tier.COMMON, setOf("wetland"), 22.0, 3400,
            "갈대밭의 수다쟁이. 개개개— 하고 온종일 노래해서 이름이 그대로 붙었다.",
            null,
            BirdArt(0, c(0xFFA98F6B), c(0xFFF0E6D2), c(0xFF8A7250), c(0xFF4A3728), c(0xFF8A7250), c(0xFFB98A4A), 0.95f)
        ),
        BirdDef(
            "whiteeye", "동박새", Tier.COMMON, setOf("forest"), 18.0, 4000,
            "눈가에 흰 테를 두른 연둣빛 작은 새. 동백꽃에 부리를 넣고 꿀을 마신다.",
            setOf("jeju", "hallasan", "hadori", "gwangju", "ulsan", "suncheon"),
            BirdArt(0, c(0xFF8FBF5A), c(0xFFF5F0D8), c(0xFF6E9E44), c(0xFF3A322C), c(0xFFFFFFFF), c(0xFF9AA0A8), 0.8f)
        ),
        BirdDef(
            "beangoose", "큰기러기", Tier.UNCOMMON, setOf("field", "wetland", "water"), 14.0, 8000,
            "V자 대형으로 줄지어 나는 겨울의 전령. 논에 내려 벼 이삭을 줍는다.",
            null,
            BirdArt(1, c(0xFF6E6152), c(0xFFDCD2BC), c(0xFF4F463A), c(0xFFE8A14E), c(0xFF4F463A), c(0xFFE8863C), 1.2f)
        ),
        BirdDef(
            "oystercatcher", "검은머리물떼새", Tier.UNCOMMON, setOf("coast", "wetland"), 10.0, 9500,
            "붉고 긴 부리로 조개를 여는 갯벌의 장인. 검은 등에 흰 배가 또렷하다.",
            null,
            BirdArt(0, c(0xFF23252B), c(0xFFF7F5EE), c(0xFF1A1C21), c(0xFFE24B3A), c(0xFF1A1C21), c(0xFFE2857A), 1.05f)
        ),
        BirdDef(
            "greatknot", "붉은어깨도요", Tier.UNCOMMON, setOf("coast", "wetland"), 11.0, 9000,
            "호주에서 시베리아까지 날아가는 장거리 여행자. 갯벌은 그 긴 여정의 주유소다.",
            null,
            BirdArt(0, c(0xFF9B8B76), c(0xFFF2EDE0), c(0xFF7A6A55), c(0xFF4A4238), c(0xFFB5714A), c(0xFF8A8F98), 0.95f)
        ),
        BirdDef(
            "baikalteal", "가창오리", Tier.RARE, setOf("water", "wetland"), 6.0, 24000,
            "수십만 마리가 해 질 녘 하늘에 거대한 군무를 그리는, 겨울의 장관 그 자체.",
            setOf("geumgang", "eulsukdo", "junam", "sihwa", "taean", "hwaseong"),
            BirdArt(1, c(0xFF6B5A3F), c(0xFFE8DCC0), c(0xFF3F6D5E), c(0xFF2B2B33), c(0xFFE0A63C), c(0xFFE8A14E), 1.05f)
        ),
        BirdDef(
            "whooperswan", "큰고니", Tier.RARE, setOf("water", "wetland"), 5.0, 26000,
            "노란 부리의 커다란 백조. 가족끼리 나팔 같은 소리로 대화하며 겨울을 난다.",
            setOf("sihwa", "eulsukdo", "junam", "taean", "upo", "chuncheon", "hadori", "geumgang"),
            BirdArt(2, c(0xFFFDFDF8), c(0xFFFFFFFF), c(0xFFEDEADF), c(0xFFF2C84B), c(0xFFFDFDF8), c(0xFF3A3A44), 1.35f)
        ),
        BirdDef(
            "spoonbill", "저어새", Tier.RARE, setOf("wetland", "coast"), 5.0, 28000,
            "주걱 부리를 물속에서 좌우로 저어 먹이를 찾는다. 전 세계 대부분이 서해에서 번식한다.",
            setOf("ganghwa", "songdo", "gochang", "hadori", "incheon", "sihwa", "hwaseong", "suncheon"),
            BirdArt(2, c(0xFFFDFDF8), c(0xFFFFFFFF), c(0xFFE8E4D8), c(0xFF2B2B33), c(0xFFF2E07A), c(0xFF2B2B33), 1.25f)
        ),
        BirdDef(
            "osprey", "물수리", Tier.RARE, setOf("water", "coast"), 4.5, 26000,
            "물 위를 맴돌다 발부터 내리꽂아 물고기를 낚는 어부 매.",
            setOf("wangpi", "eulsukdo", "geumgang", "sihwa", "hadori", "gongneung", "junam"),
            BirdArt(3, c(0xFF6B6459), c(0xFFF7F4EC), c(0xFF4A453C), c(0xFF23252B), c(0xFF4A453C), c(0xFF9AA0A8), 1.2f)
        ),
        BirdDef(
            "hoodedcrane", "흑두루미", Tier.LEGEND, setOf("wetland", "field"), 1.6, 52000,
            "잿빛 몸에 흰 머리를 얹은 두루미. 순천만 갈대밭 위를 가족 단위로 낮게 난다.",
            setOf("suncheon", "cheorwon", "imjin", "junam", "geumgang"),
            BirdArt(2, c(0xFF55585F), c(0xFF6B6E76), c(0xFF3F4248), c(0xFF6B4F35), c(0xFFF7F5EE), c(0xFF3A3A44), 1.3f)
        ),
        BirdDef(
            "whitetail", "흰꼬리수리", Tier.LEGEND, setOf("water", "coast", "wetland"), 1.4, 68000,
            "날개를 펴면 2m가 넘는 겨울 하늘의 왕. 흰 꼬리와 노란 부리가 위엄 있다.",
            setOf("taean", "cheorwon", "imjin", "eulsukdo", "geumgang", "sihwa", "junam"),
            BirdArt(3, c(0xFF6B5B45), c(0xFFE0D6C2), c(0xFF4F4233), c(0xFFF2C84B), c(0xFFDCD6C8), c(0xFFF2C84B), 1.4f)
        ),
        BirdDef(
            "crestedibis", "따오기", Tier.LEGEND, setOf("wetland", "water"), 0.9, 75000,
            "'보일 듯이 보일 듯이 보이지 않는' 그 새. 우포늪에서 되살아난 분홍빛 전설.",
            setOf("upo", "junam", "suncheon"),
            BirdArt(2, c(0xFFF7E6E4), c(0xFFFFF4F2), c(0xFFE8CFCB), c(0xFF4A3728), c(0xFFE2574C), c(0xFFE2574C), 1.3f, pattern = BirdPatterns.DARK_CAP)
        )
    )

    private val curatedByName: Map<String, BirdDef> = CURATED.associateBy { it.name }

    // (주의: ALL 초기화 때 tierFor가 사용하므로 반드시 ALL보다 먼저 선언할 것)
    private val COMMON_NAMES = setOf(
        "참새", "까치", "박새", "쇠박새", "곤줄박이", "직박구리", "멧비둘기", "흰뺨검둥오리",
        "청둥오리", "쇠오리", "괭이갈매기", "재갈매기", "왜가리", "중대백로", "쇠백로",
        "물닭", "제비", "붉은머리오목눈이", "동박새", "딱새", "검은등할미새", "알락할미새",
        "노랑턱멧새", "방울새", "오목눈이", "어치", "큰부리까마귀", "물까치", "찌르레기"
    )

    val ALL: List<BirdDef> = OfficialBirdChecklist.ALL.map { entry ->
        curatedByName[entry.koreanName]?.let { enrich(it, entry) } ?: generated(entry)
    }

    val byId: Map<String, BirdDef> = ALL.associateBy { it.id }
    val byName: Map<String, BirdDef> = ALL.associateBy { it.name }

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
// 피자 (화덕피자 / 일반 피자) / 카메라 / 장식
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

/**
 * 피자 계열 — 집의 두 조리기구에서 각각 다른 계열을 굽는다.
 * - 화덕피자: 장작 화덕(🔥). 얇은 도우를 고온에서 순식간에 굽는다 → 커서가 빠르고 금방 타지만 효과(특히 행운)가 크다.
 * - 일반 피자: 가정용 오븐(🍕). 익숙한 도톰한 피자 → 굽기 쉽고 든든하다.
 */
enum class PizzaKind(
    val label: String,          // 계열 이름
    val emoji: String,
    val station: String,        // 굽는 곳 이름 (화덕 / 오븐)
    val desc: String,
    val goodW: Float,           // 게이지의 노랑(맛있는) 구간 폭 — 화덕은 뜨거워서 금방 탄다
    val tint: Int               // UI 포인트 색
) {
    OVEN("화덕피자", "🔥", "화덕", "장작불 400도에서 순식간에 굽는 얇은 도우. 어렵지만 행운이 쑥쑥!", 0.20f, 0xFFD9683A.toInt()),
    REGULAR("일반 피자", "🍕", "오븐", "가정용 오븐에서 천천히 굽는 도톰한 피자. 굽기 쉽고 든든해요.", 0.26f, 0xFFE8A93A.toInt());

    /** 이름 뒤에 붙는 말: "마르게리타 화덕피자" / "불고기 피자" */
    val suffix: String get() = if (this == OVEN) "화덕피자" else "피자"
}

/** 피자 한 종류 — 계열(화덕/일반), 먹었을 때 효과, 굽기 미니게임 난이도, 아이콘 색 */
class PizzaDef(
    val id: Int,
    val kind: PizzaKind,
    val name: String,
    val emoji: String,
    val hungerBonus: Int,          // 품질 기본치에 더해지는 배고픔 회복
    val luckBonus: Int,            // 품질 기본치에 더해지는 행운
    val cursorSpeed: Float,        // 커서 속도 배율 (높을수록 어려움)
    val perfectW: Float,           // 걸작(초록) 구간 폭 (0~1)
    val difficulty: Int,           // 표시용 난이도 1~5
    val desc: String,
    val baseColor: Int,            // 아이콘: 치즈/소스 바탕색
    val topColorA: Int,            // 아이콘: 토핑색 1
    val topColorB: Int             // 아이콘: 토핑색 2
) {
    /** "마르게리타 화덕피자" / "불고기 피자" */
    val fullName: String get() = "$name ${kind.suffix}"

    /** 난이도 표시 "●●●○○" */
    fun difficultyDots(): String = "●".repeat(difficulty.coerceIn(1, 5)) + "○".repeat(5 - difficulty.coerceIn(1, 5))
}

/**
 * 피자 메뉴. id는 세이브 데이터의 인덱스이므로 순서를 바꾸거나 중간에 끼워 넣지 말 것!
 * (0~2는 v0.2의 치즈/버섯/불고기 — 옛 세이브와 호환)
 */
object Pizzas {
    private fun c(v: Long): Int = v.toInt()

    val ALL: List<PizzaDef> = listOf(
        // ---- 일반 피자 (가정용 오븐) — 쉽고 든든 ----
        PizzaDef(0, PizzaKind.REGULAR, "치즈", "🧀", 0, 0, 1.00f, 0.26f, 1,
            "쭉 늘어나는 기본 치즈 피자. 처음 굽기에 딱!", c(0xFFF7CE5B), c(0xFFF2B63C), c(0xFFE8A75C)),
        PizzaDef(1, PizzaKind.REGULAR, "버섯", "🍄", -4, 6, 1.15f, 0.32f, 2,
            "향긋한 양송이 듬뿍. 숲의 기운이 행운을 불러요.", c(0xFFF7CE5B), c(0xFFB8926A), c(0xFF8A6A4A)),
        PizzaDef(2, PizzaKind.REGULAR, "불고기", "🥩", 8, 2, 1.38f, 0.20f, 3,
            "달콤짭짤한 불고기가 한가득. 한국식 피자의 정석.", c(0xFFF2C24E), c(0xFF8A4A2E), c(0xFF6FAE57)),
        PizzaDef(3, PizzaKind.REGULAR, "페퍼로니", "🍕", 6, 1, 1.10f, 0.26f, 1,
            "짭짤한 페퍼로니가 빼곡. 실패가 없는 맛.", c(0xFFF7CE5B), c(0xFFC8392B), c(0xFFA32E22)),
        PizzaDef(4, PizzaKind.REGULAR, "고구마", "🍠", 4, 5, 1.22f, 0.26f, 2,
            "달콤한 고구마 무스와 옥수수. 꼬마들이 제일 좋아해요.", c(0xFFF2B84A), c(0xFFB8702C), c(0xFFFFF0A0)),
        PizzaDef(5, PizzaKind.REGULAR, "콤비네이션", "🥓", 10, 3, 1.30f, 0.22f, 3,
            "페퍼로니·피망·양파·올리브 총출동. 든든함 최고!", c(0xFFF7CE5B), c(0xFFC8392B), c(0xFF5E9E4A)),

        // ---- 화덕피자 (장작 화덕) — 어렵지만 효과 큼 ----
        PizzaDef(6, PizzaKind.OVEN, "마르게리타", "🍅", 4, 8, 1.45f, 0.22f, 3,
            "토마토·모차렐라·바질. 화덕피자의 기본이자 완성.", c(0xFFD9503F), c(0xFFFDF6E8), c(0xFF4F8F3F)),
        PizzaDef(7, PizzaKind.OVEN, "마리나라", "🌿", 0, 10, 1.40f, 0.24f, 3,
            "치즈 없이 토마토와 마늘, 오레가노만. 담백한 행운의 맛.", c(0xFFC94A3A), c(0xFFEFE2BC), c(0xFF5C8F3F)),
        PizzaDef(8, PizzaKind.OVEN, "콰트로 포르마지", "🧀", 10, 6, 1.55f, 0.20f, 4,
            "네 가지 치즈가 부글부글. 고소함이 배를 든든히 채워요.", c(0xFFF5E3A3), c(0xFF6B7FA3), c(0xFFE8A75C)),
        PizzaDef(9, PizzaKind.OVEN, "고르곤졸라", "🍯", 2, 14, 1.60f, 0.18f, 4,
            "꿀을 콕 찍어 먹는 고르곤졸라. 새들도 반할 행운의 피자.", c(0xFFF2E6C0), c(0xFF7A8BB0), c(0xFFE8B923)),
        PizzaDef(10, PizzaKind.OVEN, "디아볼라", "🌶", 12, 4, 1.70f, 0.18f, 5,
            "매콤한 살라미가 불타오르는 악마의 피자. 든든함이 남달라요.", c(0xFFD9503F), c(0xFF8F2B1E), c(0xFFF7CE5B)),
        PizzaDef(11, PizzaKind.OVEN, "루꼴라 프로슈토", "🥗", 8, 12, 1.75f, 0.16f, 5,
            "갓 구운 도우 위에 생햄과 루꼴라를 산처럼. 최고의 한 판!", c(0xFFF5E3A3), c(0xFFE88A8A), c(0xFF4F8F3F))
    )

    val byId: Map<Int, PizzaDef> = ALL.associateBy { it.id }

    fun of(id: Int): PizzaDef = byId[id] ?: ALL[0]

    /** 계열별 메뉴 (표시 순서 유지) */
    fun ofKind(kind: PizzaKind): List<PizzaDef> = ALL.filter { it.kind == kind }

    /** 계열의 대표 피자 (아이콘 등에 사용) */
    fun representative(kind: PizzaKind): PizzaDef = ofKind(kind).firstOrNull() ?: ALL[0]
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
        "jeju" to 48000000,
        // 탐조지 — 한적한 시골집이라 도시보다 저렴하다
        "ganghwa" to 18000000,
        "cheorwon" to 12000000,
        "eulsukdo" to 26000000,
        "gongneung" to 20000000,
        "gwangneung" to 24000000,
        "songdo" to 40000000,
        "sihwa" to 19000000,
        "hwaseong" to 17000000,
        "ansan" to 23000000,
        "maehyang" to 14000000,
        "junam" to 13000000,
        "suncheon" to 16000000,
        "geumgang" to 15000000,
        "gochang" to 11000000,
        "taean" to 16000000,
        "upo" to 12000000,
        "hadori" to 22000000,
        "hallasan" to 25000000,
        "imjin" to 14000000,
        "wangpi" to 10000000
    )

    fun forRegion(regionId: String): Int = byRegion[regionId] ?: 30000000
}

/**
 * 카메라 장비는 Cameras.kt(컴팩트 / 바디 + 렌즈 조합)로 분리되었다.
 * 예전 세이브의 `cameraLevel`은 GameState.fromJSON에서 자동 변환된다.
 */

// ---------------------------------------------------------------------------
// 자전거 — 탈것은 자전거뿐! (모델 · 도색 · 부속품 커스텀)
// ---------------------------------------------------------------------------

/**
 * 자전거 모델. 이 게임의 탈것은 자전거가 유일하며,
 * 종류마다 속도·배고픔 소모·새가 놀라는 정도가 달라진다.
 * - speed  : 기본 자전거 대비 주행 속도 배율
 * - hunger : 자전거 주행 시 배고픔 소모 배율
 * - scare  : 탑승 중 새 도망 반경 배율 (기본 자전거는 1.4배 — 좀 시끄럽다)
 * - luck   : 탑승만 해도 얻는 행운 보너스
 */
object Bikes {
    class Bike(
        val id: String,
        val name: String,
        val emoji: String,
        val cost: Int,
        val speed: Float,
        val hunger: Float,
        val scare: Float,
        val luck: Int,
        val desc: String
    )

    val ALL = listOf(
        Bike(
            "basic", "오래된 기본 자전거", "🚲", 0, 1.00f, 1.00f, 1.40f, 0,
            "고장 없이 쌩쌩한 첫 자전거. 모든 여행의 시작이에요."
        ),
        Bike(
            "city", "클래식 시티 바이크", "🚲", 120000, 1.06f, 0.95f, 1.34f, 0,
            "편안한 자세로 오래 탈 수 있는 단정한 시티 자전거."
        ),
        Bike(
            "minivelo", "미니벨로", "🚲", 260000, 1.10f, 0.92f, 1.28f, 0,
            "작고 가벼운 바퀴가 매력. 좁은 길도 술술 빠져나가요."
        ),
        Bike(
            "mtb", "산악 자전거 MTB", "🚵", 420000, 1.14f, 1.02f, 1.48f, 0,
            "두꺼운 타이어로 비포장 길도 거뜬! 흙먼지가 멋져요."
        ),
        Bike(
            "road", "로드 레이서", "🚴", 780000, 1.28f, 1.18f, 1.58f, 0,
            "바람을 가르는 속도의 즐거움. 대신 새가 깜짝 놀랍니다."
        ),
        Bike(
            "cruiser", "비치 크루저", "🌴", 560000, 0.98f, 0.78f, 1.22f, 1,
            "푹신한 안장과 통통한 바퀴. 여유로운 라이딩의 정석."
        ),
        Bike(
            "bmx", "BMX 스트리트", "🤸", 340000, 1.12f, 1.06f, 1.52f, 0,
            "작고 단단한 프레임. 도시의 턱도 가볍게 넘나들어요."
        ),
        Bike(
            "fixie", "픽시 (고정기어)", "⚙", 620000, 1.20f, 1.08f, 1.50f, 0,
            "간결한 프레임과 조용한 체인. 도시 라이더의 로망."
        ),
        Bike(
            "ebike", "전기 자전거", "🔋", 2400000, 1.42f, 0.70f, 1.18f, 0,
            "페달을 도와주는 전기 모터! 오르막도 편하고 아주 조용해요."
        ),
        Bike(
            "tandem", "탠덤 자전거", "💞", 1500000, 1.08f, 1.12f, 1.42f, 2,
            "두 사람이 함께 타는 자전거. 혼자 타도 마음이 넉넉해져요."
        ),
        Bike(
            "vintage", "빈티지 하이휠", "🎩", 3200000, 0.96f, 1.05f, 1.30f, 3,
            "앞바퀴가 커다란 옛날 자전거. 타면 행운이 따르는 전설의 모델!"
        )
    )

    val byId: Map<String, Bike> = ALL.associateBy { it.id }

    fun of(id: String): Bike = byId[id] ?: ALL[0]
}

/** 자전거 도색 — 프레임 / 바퀴 / 안장·그립 색상 */
object BikeColors {
    class BikeColor(val id: String, val name: String, val argb: Int)

    /** 프레임 도색 (기본은 오래된 빨간 자전거 색) */
    val FRAME = listOf(
        BikeColor("red", "레드", 0xFFC9503A.toInt()),
        BikeColor("orange", "오렌지", 0xFFE2874C.toInt()),
        BikeColor("yellow", "옐로우", 0xFFF2B63C.toInt()),
        BikeColor("green", "그린", 0xFF4F8F6A.toInt()),
        BikeColor("mint", "민트", 0xFF6FB6C9.toInt()),
        BikeColor("blue", "블루", 0xFF3F6FA0.toInt()),
        BikeColor("navy", "네이비", 0xFF2F4A6B.toInt()),
        BikeColor("purple", "퍼플", 0xFF9F7FC8.toInt()),
        BikeColor("pink", "핑크", 0xFFDB6B9A.toInt()),
        BikeColor("brown", "브라운", 0xFF8A5A33.toInt()),
        BikeColor("black", "블랙", 0xFF3A3A44.toInt()),
        BikeColor("ivory", "아이보리", 0xFFF3EDE2.toInt())
    )

    /** 타이어/휠 색 */
    val TIRE = listOf(
        BikeColor("black", "블랙", 0xFF3A3A44.toInt()),
        BikeColor("brown", "브라운", 0xFF6B431F.toInt()),
        BikeColor("ivory", "아이보리", 0xFFE8E4DC.toInt()),
        BikeColor("red", "레드", 0xFFB23F44.toInt()),
        BikeColor("blue", "블루", 0xFF4A6FA5.toInt()),
        BikeColor("gray", "그레이", 0xFF8A8F9A.toInt())
    )

    /** 안장·핸들그립 색 */
    val SADDLE = listOf(
        BikeColor("black", "블랙", 0xFF33241C.toInt()),
        BikeColor("brown", "브라운", 0xFF8A5A33.toInt()),
        BikeColor("red", "레드", 0xFFB23F44.toInt()),
        BikeColor("ivory", "아이보리", 0xFFF0E6D2.toInt()),
        BikeColor("blue", "블루", 0xFF3F6FA0.toInt()),
        BikeColor("green", "그린", 0xFF4F8F6A.toInt())
    )

    fun frame(i: Int): BikeColor = FRAME[i.coerceIn(0, FRAME.size - 1)]
    fun tire(i: Int): BikeColor = TIRE[i.coerceIn(0, TIRE.size - 1)]
    fun saddle(i: Int): BikeColor = SADDLE[i.coerceIn(0, SADDLE.size - 1)]
}

/** 자전거 부속품 — 구매하면 바로 장착된다 (외형 + 효과) */
object BikeParts {
    class Part(
        val id: String,
        val name: String,
        val emoji: String,
        val cost: Int,
        val pizza: Int,        // 피자 소지 한도 증가
        val luck: Int,         // 행운 보너스
        val scareMult: Float,  // 새 도망 반경 배율
        val desc: String
    )

    val ALL = listOf(
        Part(
            "basket", "라탄 바구니", "🧺", 40000, 1, 0, 1f,
            "앞에 달린 바구니. 피자 한 판을 더 담아요. (피자 +1)"
        ),
        Part(
            "rack", "뒷바퀴 짐받이", "📦", 45000, 1, 0, 1f,
            "튼튼한 짐받이. 피자 상자를 안전하게 실어요. (피자 +1)"
        ),
        Part(
            "light", "전조등", "🔦", 60000, 0, 0, 0.85f,
            "밤길을 환히 밝히는 전조등. 빛에 새가 덜 놀라요. (새 놀람 -15%)"
        ),
        Part(
            "streamers", "바람개비 스트리머", "🎀", 25000, 0, 1, 1f,
            "손잡이에서 나풀나풀. 탈 때마다 기분이 좋아져요. (행운 +1)"
        ),
        Part(
            "bell", "예쁜 방울", "🔔", 35000, 0, 1, 1f,
            "딸랑~ 울리는 예쁜 방울. 좋은 일이 생길 것 같아요. (행운 +1)"
        )
    )

    val byId: Map<String, Part> = ALL.associateBy { it.id }

    fun of(id: String): Part? = byId[id]
}

/** 자전거 외형 (모델 + 도색 + 부속품) — 스프라이트 생성에 쓰인다 */
class BikeStyle(
    val modelId: String,
    val frame: BikeColors.BikeColor,
    val tire: BikeColors.BikeColor,
    val saddle: BikeColors.BikeColor,
    val basket: Boolean,
    val rack: Boolean,
    val light: Boolean,
    val streamers: Boolean,
    val bell: Boolean
) {
    val cacheKey: String =
        "$modelId|${frame.argb}|${tire.argb}|${saddle.argb}|" +
            listOf(basket, rack, light, streamers, bell).joinToString("")
}

/** 이사 용달·중개 수수료 (대한민국 원) */
const val MOVE_COST = 300000

/** 들고 다닐 수 있는 피자 기본 최대 개수 (넉넉한 배낭 스킬로 늘어남) */
const val PIZZA_CAP = 6

// ---------------------------------------------------------------------------
// 탐조가 성장 (레벨 / 경험치 / 칭호)
// ---------------------------------------------------------------------------

/**
 * 캐릭터(탐조가) 레벨 시스템.
 * - 새를 찍고 의뢰를 완료하면 경험치를 얻어 레벨이 오른다.
 * - 레벨업마다 숙련 포인트(SP)를 얻어 능력을 강화할 수 있다.
 * - 레벨 구간마다 칭호와 겉모습(장비)이 바뀐다.
 */
object Progression {
    const val MAX_LEVEL = 25

    /** 현재 레벨에서 다음 레벨까지 필요한 경험치 */
    fun expToNext(level: Int): Int {
        if (level >= MAX_LEVEL) return 0
        val l = level.coerceAtLeast(1)
        return 50 + (l - 1) * 40 + (l - 1) * (l - 1) * 6
    }

    /** 레벨업 시 지급되는 숙련 포인트 (5레벨마다 보너스) */
    fun skillPointsFor(newLevel: Int): Int = if (newLevel % 5 == 0) 2 else 1

    /** 겉모습 등급 (0~3) — 레벨이 오를수록 장비가 좋아진다 */
    fun gearTier(level: Int): Int = when {
        level >= 19 -> 3
        level >= 12 -> 2
        level >= 6 -> 1
        else -> 0
    }

    /** 레벨 칭호 */
    fun title(level: Int): String = when {
        level >= 25 -> "전설의 탐조가"
        level >= 20 -> "탐조 명인"
        level >= 15 -> "베테랑 탐조가"
        level >= 12 -> "숙련 탐조가"
        level >= 10 -> "능숙한 탐조가"
        level >= 6 -> "어엿한 탐조가"
        level >= 3 -> "초보 탐조가"
        else -> "새내기 탐조인"
    }

    /** 새 촬영 경험치: 등급 × 별점 (+ 첫 발견 보너스는 별도) */
    fun photoExp(tier: Tier, stars: Int): Int {
        val base = when (tier) {
            Tier.COMMON -> 8
            Tier.UNCOMMON -> 16
            Tier.RARE -> 34
            Tier.LEGEND -> 60
        }
        val starMul = when (stars) {
            3 -> 1.7f
            2 -> 1.3f
            else -> 1.0f
        }
        return (base * starMul).toInt().coerceAtLeast(1)
    }

    /** 첫 발견(도감 신규) 보너스 경험치 */
    fun newSpeciesExp(tier: Tier): Int = 20 + tier.star * 8

    /** 박사 의뢰 완료 보너스 경험치 */
    fun questExp(reward: Int): Int = (reward / 60).coerceIn(15, 90)
}

/**
 * 능력(스킬) 정의 — 숙련 포인트(SP)로 강화. 각 랭크마다 효과가 누적된다.
 */
object Skills {
    class Skill(
        val id: String,
        val name: String,
        val emoji: String,
        val maxRank: Int,
        val desc: String,
        val perRank: String
    )

    val ALL = listOf(
        Skill("legs", "튼튼한 다리", "🦵", 4, "걷기·달리기·자전거가 조금씩 빨라져요.", "이동 속도 +6%"),
        Skill("pack", "넉넉한 배낭", "🎒", 3, "피자를 더 많이 넣어 다닐 수 있어요.", "피자 소지 +1"),
        Skill("quiet", "고요한 발걸음", "🤫", 3, "새가 덜 놀라 더 가까이 다가갈 수 있어요.", "도망 반경 -8%"),
        Skill("stamina", "튼튼한 체력", "🍙", 3, "덜 배고파져 더 오래 돌아다녀요.", "배고픔 감소 -10%"),
        Skill("lucky", "타고난 행운", "🍀", 3, "행운이 천천히 줄고 기본 행운이 높아져요.", "행운 감소 -20%, 하한 +5"),
        Skill("sharp", "매의 눈", "👁️", 3, "사진에서 별을 하나 더 얻을 확률이 올라요.", "추가 별 확률 +7%")
    )

    val byId: Map<String, Skill> = ALL.associateBy { it.id }

    fun of(id: String): Skill? = byId[id]
}

/** 하루(낮+밤 한 사이클) 길이 — 실제 초 */
const val DAY_SECONDS = 300f

// ---------------------------------------------------------------------------
// 지역 데이터 (실제 한국 지역)
// ---------------------------------------------------------------------------

/**
 * 지역 정의.
 *
 * lon/lat 은 실제 경위도(대한민국). 지도(KoreaMap)는 이 좌표를 그대로 투영해서
 * 한반도 모양 위에 지역을 배치한다.
 */
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
    val lon: Float,      // 경도 (실제 좌표)
    val lat: Float,      // 위도 (실제 좌표)
    val emoji: String,   // 지역 대표 이모지
    val kind: RegionKind = RegionKind.TOWN,
    val season: String = "사계절",     // 추천 방문 시기
    val tip: String = ""               // 탐조 팁
) {
    val habitatLabels: String
        get() = habitats.joinToString(" · ") { HabitatLabels[it] ?: it }

    /** 원형 미니맵/지도용 정규화 좌표 (-1..1) */
    val mmX: Float get() = KoreaMap.nx(lon)
    val mmY: Float get() = KoreaMap.ny(lat)
}

/** 지역 성격 — 지도 아이콘/색이 달라진다 */
enum class RegionKind(val label: String, val color: Int) {
    TOWN("도시 · 마을", 0xFFF2B63C.toInt()),
    WETLAND("습지 · 갯벌", 0xFF57B894.toInt()),
    RIVER("강 · 호수", 0xFF4F9BD9.toInt()),
    MOUNTAIN("산 · 숲", 0xFF7A9E4F.toInt()),
    COAST("해안 · 섬", 0xFFE28A4C.toInt())
}

val HabitatLabels = mapOf(
    "city" to "도시", "forest" to "숲", "field" to "들판",
    "water" to "물가", "wetland" to "습지", "coast" to "바다", "mountain" to "산"
)

object Regions {
    val ALL = listOf(
        // ===================== 도시 12곳 =====================
        RegionDef(
            "seoul", "서울", "Seoul", setOf("city", "water", "field"),
            "한강이 흐르는 도시. 강가를 걷다 보면 물새들과 마주친다.",
            "서울은 넓어요. 강변을 따라 자전거를 타면 기분이 좋아져요.",
            40, 30, emptySet(), emptySet(), 0.06, 0.02, 0.07, true, false,
            126.98f, 37.57f, "🏙", RegionKind.TOWN, "사계절",
            "밤섬·중랑천 합류부에서 겨울 오리떼를 만나기 좋아요."
        ),
        RegionDef(
            "incheon", "인천", "Incheon", setOf("city", "coast", "water", "wetland"),
            "서해의 갯벌과 갈대밭. 철새들이 머무는 쉼터다.",
            "썰물 때 갯벌에 새들이 잔뜩 내려앉아요. 셔터를 준비하세요!",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.05, 0.02, 0.06, true, false,
            126.70f, 37.46f, "✈", RegionKind.TOWN, "봄 · 가을",
            "만조 두 시간 전후로 새들이 가까이 밀려와요."
        ),
        RegionDef(
            "chuncheon", "춘천", "Chuncheon", setOf("water", "wetland", "forest"),
            "호수의 도시. 맑은 물 위에 물새들이 한가로이 떠 있다.",
            "춘천엔 호수가 많아요. 물새 구경엔 여기만 한 곳이 없죠.",
            40, 30, emptySet(), emptySet(), 0.10, 0.02, 0.06, false, true,
            127.73f, 37.87f, "🏞", RegionKind.RIVER, "겨울",
            "의암호 안개 낀 아침, 물안개 속 백로가 일품이에요."
        ),
        RegionDef(
            "gangneung", "강릉", "Gangneung", setOf("coast", "forest", "city"),
            "푸른 동해와 소나무 숲. 커피와 파도 소리의 도시.",
            "바닷바람에 소나무가 살랑살랑. 숲에도 새가 많아요.",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.09, 0.02, 0.06, true, false,
            128.90f, 37.75f, "🌊", RegionKind.COAST, "겨울",
            "경포호와 솔숲을 함께 도세요. 아비류가 바다에 떠 있어요."
        ),
        RegionDef(
            "sokcho", "속초", "Sokcho", setOf("mountain", "forest", "coast"),
            "설악산과 동해가 만나는 곳. 산새의 천국.",
            "설악산 새소리는 약이에요. 등산 삼아 새 구경 어때요?",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.13, 0.09, 0.05, false, false,
            128.59f, 38.20f, "⛰", RegionKind.MOUNTAIN, "봄 · 여름",
            "새벽 계곡길이 최고. 딱따구리 드러밍 소리를 따라가세요."
        ),
        RegionDef(
            "daejeon", "대전", "Daejeon", setOf("city", "field", "forest"),
            "한반도의 한가운데. 어디로 떠나기 좋은 교통 요지.",
            "대전에서라면 어느 지역이든 하루면 다녀올 수 있어요.",
            40, 30, emptySet(), emptySet(), 0.07, 0.02, 0.06, true, false,
            127.38f, 36.35f, "🚄", RegionKind.TOWN, "사계절",
            "갑천 산책로만 걸어도 물총새를 볼 수 있어요."
        ),
        RegionDef(
            "jeonju", "전주", "Jeonju", setOf("city", "field"),
            "한옥의 고장. 굴뚝 연기와 피자 향이 어우러진 낭만의 도시.",
            "한옥마을 골목을 걷다 보면 지붕 위에 새들이 앉아 있어요.",
            40, 30, emptySet(), emptySet(), 0.08, 0.02, 0.09, true, false,
            127.15f, 35.82f, "🏯", RegionKind.TOWN, "사계절",
            "전주천 징검다리 근처에 원앙이 자주 나와요."
        ),
        RegionDef(
            "daegu", "대구", "Daegu", setOf("city", "mountain"),
            "팔공산 자락의 사과 도시. 산새와 골목의 어치가 반갑다.",
            "팔공산 바람 좋을 때 올라가 보세요. 큰 새들이 빙글빙글 돌아요.",
            40, 30, emptySet(), emptySet(), 0.07, 0.05, 0.06, true, false,
            128.60f, 35.87f, "🍎", RegionKind.TOWN, "가을",
            "능선 위로 솟아오르는 맹금의 이동을 관찰해 보세요."
        ),
        RegionDef(
            "gwangju", "광주", "Gwangju", setOf("city", "forest", "wetland"),
            "산과 습지가 가까운 예술의 고장.",
            "남쪽은 새도 색이 곱지 뭐예요. 팔색조를 기대해 보세요.",
            40, 30, emptySet(), emptySet(), 0.08, 0.02, 0.06, true, true,
            126.85f, 35.16f, "🎨", RegionKind.TOWN, "여름",
            "무등산 계곡 숲에서 여름 철새의 노래를 들어 보세요."
        ),
        RegionDef(
            "ulsan", "울산", "Ulsan", setOf("coast", "mountain", "city"),
            "동해 최남단, 고래의 도시. 간절곶에서 해돋이 새소리를 듣는다.",
            "바다 위로 매가 떠요. 등대 아래에서 사진 한 장 어때요?",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.09, 0.06, 0.05, true, false,
            129.31f, 35.54f, "🐳", RegionKind.COAST, "봄 · 가을",
            "태화강 대숲의 저녁 떼까마귀 군무가 유명해요."
        ),
        RegionDef(
            "busan", "부산", "Busan", setOf("coast", "water", "mountain", "city"),
            "해운대와 낙동강 하구. 철새 여행의 끝자락.",
            "남쪽엔 해저 터널이 하나 있어요. 무모한 자전거 여행자를 위해서죠!",
            40, 30, setOf(Dir.E, Dir.S), setOf(Dir.E, Dir.S), 0.06, 0.05, 0.05, true, false,
            129.07f, 35.18f, "🌉", RegionKind.COAST, "겨울",
            "바다와 강이 만나 갈매기와 오리를 한 번에 볼 수 있어요."
        ),
        RegionDef(
            "jeju", "제주", "Jeju", setOf("coast", "mountain", "forest", "wetland"),
            "바람의 섬, 오름의 섬. 귀한 새들이 머무는 곳.",
            "제주엔 없는 게 없어요. 돌하르방처럼 어여쁜 새들도요.",
            40, 30, setOf(Dir.S, Dir.E, Dir.W), setOf(Dir.S, Dir.E, Dir.W), 0.07, 0.12, 0.05, false, false,
            126.53f, 33.50f, "🏝", RegionKind.COAST, "사계절",
            "해안도로를 따라 달리면 섬새와 물새가 계속 나타나요."
        ),

        // ================= 대한민국 대표 탐조지 20선 =================
        RegionDef(
            "ganghwa", "강화도 갯벌", "Ganghwa Tidal Flat", setOf("coast", "wetland", "field"),
            "저어새의 번식지이자 세계적인 갯벌 생태계. 썰물마다 새들의 식당이 열린다.",
            "저어새가 숟가락 부리를 좌우로 휘저으며 먹이를 찾아요. 조용히 지켜봐 주세요.",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.05, 0.02, 0.05, false, false,
            126.42f, 37.69f, "🥄", RegionKind.WETLAND, "4~9월 (저어새 번식기)",
            "만조 1~2시간 전, 새들이 갯벌 가장자리로 밀려올 때가 기회예요."
        ),
        RegionDef(
            "cheorwon", "철원 평야", "Cheorwon Plain", setOf("field", "wetland", "water"),
            "두루미와 재두루미가 내려앉는 겨울 들녘. 세계적인 두루미 월동지.",
            "해 뜰 무렵 논에 두루미 가족이 내려와요. 차 안에서 조용히 보는 게 예의랍니다.",
            40, 30, emptySet(), emptySet(), 0.04, 0.02, 0.04, false, true,
            127.31f, 38.20f, "🕊", RegionKind.WETLAND, "11~2월 (겨울)",
            "일출 직후와 일몰 전, 잠자리와 논을 오가는 두루미가 가장 잘 보여요."
        ),
        RegionDef(
            "eulsukdo", "낙동강 하구", "Nakdong Estuary", setOf("water", "wetland", "coast"),
            "을숙도 — 대한민국 최대의 철새도래지. 오리류와 고니류가 강을 덮는다.",
            "여기가 '새가 많고 물이 맑은 섬' 을숙도예요. 겨울엔 강이 새로 가득 차죠.",
            40, 30, setOf(Dir.S), setOf(Dir.S), 0.05, 0.02, 0.05, false, true,
            128.95f, 35.10f, "🦆", RegionKind.RIVER, "11~3월 (겨울)",
            "탐조대에서 망원경으로 보면 고니 무리를 편하게 셀 수 있어요."
        ),
        RegionDef(
            "gongneung", "공릉천", "Gongneungcheon", setOf("water", "wetland", "field"),
            "물줄기를 따라 텃새와 철새가 나란히. 소박하지만 알찬 하천 습지.",
            "천변 둑길만 걸어도 하루 40종은 만나요. 장화를 신으면 더 좋고요!",
            40, 30, emptySet(), emptySet(), 0.07, 0.02, 0.08, false, true,
            126.78f, 37.76f, "🏞", RegionKind.RIVER, "가을 · 겨울",
            "하류 합수부의 모래톱에 도요새가 쉬어 가요."
        ),
        RegionDef(
            "gwangneung", "광릉숲", "Gwangneung Forest", setOf("forest", "mountain"),
            "500년을 이어 온 국립수목원의 숲. 크낙새의 전설이 남아 있는 곳.",
            "이 숲은 500년 동안 도끼가 들어오지 않았대요. 나무가 아주 커요.",
            40, 30, emptySet(), emptySet(), 0.20, 0.05, 0.04, false, false,
            127.17f, 37.75f, "🌳", RegionKind.MOUNTAIN, "4~6월 (번식기)",
            "이른 아침이 숲새들의 합창 시간. 소리부터 익히면 쉬워요."
        ),
        RegionDef(
            "songdo", "송도 갯벌", "Songdo Tidal Flat", setOf("coast", "wetland", "city"),
            "고층 빌딩 옆의 갯벌. 저어새와 도요물떼새가 찾는 수도권의 쉼터.",
            "빌딩 숲 사이에 저어새가 있다니 신기하죠? 도시와 새의 공존이에요.",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.04, 0.02, 0.05, true, false,
            126.68f, 37.38f, "🏢", RegionKind.WETLAND, "봄 · 가을",
            "시흥 갯골까지 이어서 돌면 도요물떼새를 더 많이 만나요."
        ),
        RegionDef(
            "sihwa", "시화호", "Sihwa Lake", setOf("water", "wetland", "coast"),
            "죽음의 호수에서 되살아난 큰 호수. 이제는 큰고니의 겨울 궁전.",
            "예전엔 물이 썩었대요. 지금은 고니가 오니까… 자연은 대단하죠.",
            40, 30, emptySet(), emptySet(), 0.05, 0.02, 0.05, false, true,
            126.73f, 37.28f, "🦢", RegionKind.RIVER, "11~2월 (겨울)",
            "상류 습지 쪽이 수심이 얕아 물새가 모여요."
        ),
        RegionDef(
            "hwaseong", "화성 습지", "Hwaseong Wetland", setOf("wetland", "coast", "field"),
            "간척지에 남은 갯벌과 화성호. 도요새들의 국제 휴게소.",
            "여긴 도요새 고속도로 휴게소예요. 호주에서 시베리아까지 가는 길이래요.",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.04, 0.02, 0.05, false, true,
            126.78f, 37.10f, "🌾", RegionKind.WETLAND, "4~5월, 8~10월",
            "밀물 때 쉬는 자리(고조 휴식지)를 찾으면 수천 마리를 한눈에!"
        ),
        RegionDef(
            "ansan", "안산 갈대습지", "Ansan Reed Marsh", setOf("wetland", "water", "city"),
            "산책로가 잘 닦인 갈대 습지공원. 초보 탐조인에게 가장 친절한 곳.",
            "여긴 산책하다가 새를 만나요. 처음이라면 여기부터 시작해 보세요!",
            40, 30, emptySet(), emptySet(), 0.06, 0.02, 0.07, true, true,
            126.83f, 37.32f, "🌿", RegionKind.WETLAND, "사계절",
            "탐조 데크에 서서 기다리면 새가 알아서 다가와요."
        ),
        RegionDef(
            "maehyang", "매향리 해안", "Maehyangri Coast", setOf("coast", "wetland"),
            "옛 사격장이 쉼터가 된 바다. 다양한 도요물떼새를 만나는 해양 탐조 명소.",
            "아픈 역사가 있던 바다예요. 지금은 새들이 제일 먼저 돌아왔죠.",
            40, 30, setOf(Dir.W, Dir.S), setOf(Dir.W, Dir.S), 0.04, 0.03, 0.04, false, false,
            126.72f, 37.02f, "🐚", RegionKind.COAST, "봄 · 가을",
            "물때표를 꼭 확인하세요. 탐조는 물때가 8할이에요."
        ),
        RegionDef(
            "junam", "주남저수지", "Junam Reservoir", setOf("water", "wetland", "field"),
            "창원의 너른 저수지. 가마우지·기러기·고니가 아침마다 날아오른다.",
            "해 뜰 때 기러기가 한꺼번에 날아올라요. 그 소리, 잊지 못할걸요.",
            40, 30, emptySet(), emptySet(), 0.05, 0.02, 0.06, false, true,
            128.68f, 35.29f, "🪿", RegionKind.RIVER, "11~2월 (겨울)",
            "일출 비상(飛上)과 일몰 귀환, 하루 두 번이 절정이에요."
        ),
        RegionDef(
            "suncheon", "순천만 습지", "Suncheonman Bay", setOf("wetland", "coast", "field"),
            "끝없는 갈대밭과 S자 물길. 흑두루미가 겨울을 나는 세계적인 연안 습지.",
            "갈대밭 사이로 흑두루미가 걸어가요. 전봇대를 뽑아 새 길을 내준 마을이랍니다.",
            40, 30, setOf(Dir.S), setOf(Dir.S), 0.05, 0.02, 0.05, false, true,
            127.51f, 34.88f, "🌾", RegionKind.WETLAND, "11~2월 (흑두루미)",
            "용산 전망대에 오르면 갈대밭과 새 떼를 함께 볼 수 있어요."
        ),
        RegionDef(
            "geumgang", "금강 하구", "Geumgang Estuary", setOf("water", "wetland", "coast"),
            "군산과 서천 사이. 가창오리 수십만 마리의 군무가 하늘을 그리는 곳.",
            "해 질 녘에 가창오리가 한꺼번에 날아요. 하늘에 검은 파도가 치는 것 같아요.",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.05, 0.02, 0.05, false, true,
            126.70f, 35.98f, "🌅", RegionKind.RIVER, "12~2월 (가창오리)",
            "군무는 일몰 20~40분 전후. 삼각대를 미리 세워 두세요."
        ),
        RegionDef(
            "gochang", "고창 갯벌", "Gochang Tidal Flat", setOf("coast", "wetland"),
            "세계자연유산 갯벌. 저어새와 검은머리물떼새가 사는 조용한 바다.",
            "여긴 유네스코가 지켜 주는 갯벌이에요. 조개도 새도 한 가족이죠.",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.05, 0.02, 0.05, false, false,
            126.49f, 35.47f, "🦪", RegionKind.WETLAND, "봄 · 가을",
            "갯벌에 들어가지 말고 둑길에서 관찰하는 게 안전해요."
        ),
        RegionDef(
            "taean", "태안 천수만", "Taean · Cheonsuman", setOf("coast", "forest", "wetland"),
            "안면도 솔숲과 간월호. 수만 마리 기러기와 맹금류가 겨울을 나는 들녘.",
            "간월호 논에 기러기가 새까맣게 앉아요. 그 위로 흰꼬리수리가 지나가죠.",
            40, 30, setOf(Dir.W), setOf(Dir.W), 0.12, 0.03, 0.05, false, true,
            126.35f, 36.60f, "🌲", RegionKind.COAST, "10~2월 (겨울)",
            "논둑길 드라이브 탐조가 편해요. 차가 곧 이동식 은신처랍니다."
        ),
        RegionDef(
            "upo", "우포늪", "Upo Wetland", setOf("wetland", "water", "forest"),
            "1억 4천만 년을 흘러온 국내 최대 자연 내륙습지. 따오기가 돌아온 늪.",
            "여긴 공룡보다 오래된 늪이에요. 아침 물안개가 정말 곱답니다.",
            40, 30, emptySet(), emptySet(), 0.09, 0.02, 0.06, false, true,
            128.42f, 35.56f, "🪷", RegionKind.WETLAND, "사계절 (겨울 최고)",
            "새벽 물안개 속 실루엣 사진이 명작이 돼요."
        ),
        RegionDef(
            "hadori", "제주 하도리", "Hado-ri Wetland", setOf("wetland", "coast", "water"),
            "철새들의 낙원이라 불리는 제주 동쪽 철새도래지.",
            "바다와 저수지가 붙어 있어서 바닷새와 민물새를 한 번에 봐요.",
            40, 30, setOf(Dir.E), setOf(Dir.E), 0.05, 0.04, 0.05, false, true,
            126.90f, 33.52f, "🌴", RegionKind.WETLAND, "11~3월 (겨울)",
            "저어새와 오리류가 함께 쉬는 모습을 볼 수 있어요."
        ),
        RegionDef(
            "hallasan", "한라산 국립공원", "Hallasan N.P.", setOf("mountain", "forest"),
            "섬의 지붕. 동박새와 어치 등 제주의 숲 텃새를 만나는 곳.",
            "동박새는 동백꽃에 부리를 넣고 꿀을 먹어요. 꽃 필 때 꼭 오세요.",
            40, 30, emptySet(), emptySet(), 0.18, 0.14, 0.05, false, false,
            126.53f, 33.36f, "🌋", RegionKind.MOUNTAIN, "겨울 · 봄 (동백)",
            "고도에 따라 새가 달라져요. 천천히 올라가며 귀를 기울여 보세요."
        ),
        RegionDef(
            "imjin", "파주 임진강·DMZ", "Imjin River · DMZ", setOf("field", "water", "wetland"),
            "사람의 발길이 닿지 않은 청정 지대. 두루미와 맹금류의 겨울 왕국.",
            "저 너머는 사람이 못 가요. 그래서 새들에겐 세상에서 제일 안전한 곳이죠.",
            40, 30, emptySet(), emptySet(), 0.06, 0.03, 0.05, false, true,
            126.80f, 37.94f, "🕊", RegionKind.RIVER, "11~2월 (겨울)",
            "강가 여울의 두루미 잠자리는 멀리서 조용히 관찰하세요."
        ),
        RegionDef(
            "wangpi", "왕피천", "Wangpicheon", setOf("water", "forest", "mountain"),
            "울진의 맑은 계곡. 연어와 은어를 노리는 맹금류와 물새가 찾아온다.",
            "물이 하도 맑아서 바닥 자갈이 다 보여요. 물수리가 그걸 노리죠.",
            40, 30, emptySet(), emptySet(), 0.16, 0.10, 0.05, false, false,
            129.35f, 36.95f, "🐟", RegionKind.RIVER, "가을 (연어 회귀)",
            "여울목 바위에 앉는 물수리를 기다려 보세요."
        )
    )

    val byId: Map<String, RegionDef> = ALL.associateBy { it.id }

    /** 지역 연결: (from, to, from 기준 방향). 양방향으로 해석됨. */
    private data class Link(val a: String, val b: String, val dirFromA: Dir)

    private val LINKS = listOf(
        // 수도권 · 강원
        Link("seoul", "incheon", Dir.W),
        Link("seoul", "chuncheon", Dir.N),
        Link("seoul", "daejeon", Dir.S),
        Link("chuncheon", "gangneung", Dir.E),
        Link("gangneung", "sokcho", Dir.N),
        Link("incheon", "ganghwa", Dir.N),
        Link("ganghwa", "gongneung", Dir.E),
        Link("gongneung", "imjin", Dir.N),
        Link("gongneung", "gwangneung", Dir.E),
        Link("gwangneung", "chuncheon", Dir.E),
        Link("imjin", "cheorwon", Dir.E),
        Link("cheorwon", "chuncheon", Dir.S),
        // 서해안 남하 루트
        Link("incheon", "songdo", Dir.S),
        Link("songdo", "sihwa", Dir.S),
        Link("sihwa", "ansan", Dir.E),
        Link("ansan", "hwaseong", Dir.S),
        Link("hwaseong", "maehyang", Dir.S),
        Link("maehyang", "taean", Dir.S),
        Link("taean", "geumgang", Dir.S),
        Link("geumgang", "jeonju", Dir.S),
        // 호남
        Link("gwangju", "jeonju", Dir.E),
        Link("jeonju", "daejeon", Dir.E),
        Link("daejeon", "gwangju", Dir.S),
        Link("gwangju", "gochang", Dir.W),
        Link("gwangju", "suncheon", Dir.S),
        // 영남
        Link("daejeon", "daegu", Dir.E),
        Link("daegu", "ulsan", Dir.E),
        Link("ulsan", "busan", Dir.S),
        Link("suncheon", "junam", Dir.E),
        Link("junam", "upo", Dir.N),
        Link("upo", "daegu", Dir.N),
        Link("junam", "eulsukdo", Dir.S),
        Link("eulsukdo", "busan", Dir.E),
        // 동해안
        Link("gangneung", "wangpi", Dir.S),
        Link("wangpi", "daegu", Dir.S),
        // 제주 (해저 터널!)
        Link("busan", "jeju", Dir.S),
        Link("jeju", "hadori", Dir.E),
        Link("jeju", "hallasan", Dir.S)
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

    /** 모든 연결 목록 (지도 그리기용) */
    fun allLinks(): List<Pair<String, String>> = LINKS.map { it.a to it.b }

    /** 지역 소개용 대표 새 (등급 높은 순) */
    fun signatureBirds(r: RegionDef, count: Int = 3): List<BirdDef> =
        Birds.poolFor(r).sortedByDescending { it.tier.star }.take(count)
}
