package com.pizzaandbird.game

/**
 * 메인 스토리, 탐조 등급, 한국 탐조식 "도장 깨기" 컬렉션.
 *
 * 라이퍼는 사진을 한 번 이상 남긴 서로 다른 종으로 계산한다. 실제 탐조에는 공인된
 * 실력 등급이 없으므로 아래 구간은 경쟁 랭킹이 아니라 게임 안의 성장 이정표다.
 * 종명은 한국조류학회 2025 v2.1 목록의 표준 국명을 따른다.
 */
object BirdingRanks {
    data class Rank(val name: String, val min: Int, val max: Int?, val note: String)

    val ALL = listOf(
        Rank("입문", 0, 29, "동네 새를 눈에 익히고 쌍안경과 카메라를 다루는 단계"),
        Rank("초보", 30, 99, "공원·하천의 텃새와 물새를 스스로 찾아내는 단계"),
        Rank("중수", 100, 199, "철새·도요물떼새를 계절, 소리, 실루엣으로 가르는 단계"),
        Rank("고수", 200, 299, "섬과 갯벌 원정에서 닮은 종의 세부 특징까지 읽는 단계"),
        Rank("초고수", 300, 399, "희귀 나그네새의 시기와 날씨를 예측해 기록하는 단계"),
        Rank("종새꾼", 400, null, "기록 검토와 서식지 보호까지 생각하는 베테랑 기록자")
    )

    fun of(lifers: Int): Rank = ALL.last { lifers >= it.min }
    fun next(lifers: Int): Rank? = ALL.firstOrNull { it.min > lifers }
}

/** 한국 탐조식 "도장 깨기" 컬렉션. */
object BirdingCollections {
    data class Collection(
        val name: String,
        val icon: String,
        val species: List<String>,
        val note: String
    ) {
        fun caught(state: GameState): Int = species.count { state.hasBirdName(it) }
        fun complete(state: GameState): Boolean = caught(state) == species.size
        fun progress(state: GameState): String = "${caught(state)}/${species.size}"
    }

    /** 실제 목록에 없는 별칭은 표준 종명으로 바로잡았다. */
    val ALL = listOf(
        Collection("동네 첫 만남", "🏘", listOf("참새", "까치", "직박구리", "박새", "멧비둘기"),
            "가까운 공원에서 시작하는 가장 좋은 다섯 종"),
        Collection("딱다구리 기본 3종", "🌳", listOf("쇠딱다구리", "오색딱다구리", "청딱다구리"),
            "크기, 배 무늬, 등 색을 비교하는 숲 탐조의 첫 도장"),
        Collection("딱다구리 상급 5종", "🪵", listOf("쇠딱다구리", "오색딱다구리", "청딱다구리", "큰오색딱다구리", "까막딱다구리"),
            "깊은 숲의 까막딱다구리까지 잇는 상급 세트"),
        Collection("박새 네 친구", "🌰", listOf("박새", "곤줄박이", "쇠박새", "진박새"),
            "겨울 혼성군에서 만나는 작고 부지런한 산새들"),
        Collection("물총새과 4종", "💎", listOf("물총새", "호반새", "청호반새", "뿔호반새"),
            "청도요는 도요류다. 물총새과 표준 종명 네 종을 모은 세트"),
        Collection("여름 숲의 세 보석", "🌈", listOf("팔색조", "긴꼬리딱새", "호반새"),
            "번식지를 방해하지 않고 멀리서 기록해야 하는 여름 숲의 상징"),
        Collection("푸른 깃 삼총사", "🔵", listOf("파랑새", "큰유리새", "바다직박구리"),
            "숲, 계곡, 바위 해안에서 만나는 서로 다른 푸른빛"),
        Collection("겨울 오리 삼색", "🦆", listOf("원앙", "청머리오리", "가창오리"),
            "수컷의 번식깃과 무리의 행동을 함께 관찰하는 겨울 세트"),
        Collection("두루미 3종", "🌾", listOf("두루미", "재두루미", "흑두루미"),
            "철원과 순천만을 잇는 대표 월동 두루미"),
        Collection("겨울 수리 3종", "🦅", listOf("흰꼬리수리", "독수리", "참수리"),
            "큰 날개와 꼬리 모양으로 구분하는 겨울 맹금 도전"),
        Collection("밤의 네 목소리", "🌙", listOf("올빼미", "수리부엉이", "쇠부엉이", "솔부엉이"),
            "플래시와 소리 재생 없이 기다려야 하는 야간 탐조 세트"),
        Collection("도요 입문 4종", "👣", listOf("민물도요", "좀도요", "붉은어깨도요", "청다리도요"),
            "부리, 다리, 크기와 먹이 행동을 차근차근 비교하는 갯벌 입문"),
        Collection("보전의 깃발", "🤍", listOf("저어새", "노랑부리백로", "검은머리물떼새"),
            "서해 갯벌을 지키는 마음으로 남기는 세 종의 기록"),
        Collection("뜸부기과 입문", "🌿", listOf("뜸부기", "물닭", "쇠물닭"),
            "논과 습지의 가장자리에서 조용히 기다리는 세트"),
        Collection("바다 원정", "🌊", listOf("바다오리", "뿔바다오리", "아비", "회색머리아비"),
            "안전한 거리와 기상을 먼저 살피는 해상 탐조 도전"),
        Collection("도심의 생존자들", "🏙", listOf("큰부리까마귀", "까마귀", "까치", "멧비둘기"),
            "빌딩 숲과 가로수 사이에서 살아가는 지혜로운 이웃들"),
        Collection("하천의 날렵한 발걸음", "💧", listOf("알락할미새", "검은등할미새", "노랑할미새", "긴발톱할미새"),
            "물가 자갈밭에서 꽁지를 흔들며 걷는 할미새 무리"),
        Collection("연못의 작은 잠수부", "🫧", listOf("논병아리", "뿔논병아리", "귀뿔논병아리"),
            "수면 아래로 순식간에 사라지는 귀여운 잠수 장인들"),
        Collection("하천의 백로와 왜가리", "🏞", listOf("왜가리", "중대백로", "중백로", "쇠백로"),
            "물가 얕은 여울에 가만히 서서 먹이를 기다리는 긴 다리들"),
        Collection("갈대밭의 은둔자", "🌾", listOf("해오라기", "덤불해오라기", "알락해오라기"),
            "해질녘 갈대숲 속에서 비밀스럽게 움직이는 야행성 백로류"),
        Collection("갯벌의 긴 부리 탐험가", "🦀", listOf("마도요", "알락꼬리마도요", "중부리도요"),
            "곡선으로 휜 긴 부리로 갯벌 속 게를 찾아내는 나그네새"),
        Collection("모래톱의 빠른 발", "🏖", listOf("흰목물떼새", "꼬마물떼새", "흰물떼새", "왕눈물떼새"),
            "해변과 자갈밭을 잰걸음으로 뛰어다니는 작은 물떼새들"),
        Collection("도요 중급 4종", "🪶", listOf("장다리물떼새", "뒷부리장다리물떼새", "흑꼬리도요", "큰뒷부리도요"),
            "긴 다리와 위로 휜 부리, 독특한 실루엣을 자랑하는 도요류"),
        Collection("겨울 하천의 수면오리", "🦆", listOf("청둥오리", "흰뺨검둥오리", "쇠오리", "고방오리"),
            "물구나무서듯 자맥질하며 수초를 뜯는 친숙한 겨울 오리들"),
        Collection("깊은 물의 잠수오리", "🌊", listOf("댕기흰죽지", "검은머리흰죽지", "흰죽지", "비오리"),
            "호수 한가운데 깊은 물속까지 잠수해 물고기를 잡는 오리들"),
        Collection("겨울 바다오리 삼총사", "❄️", listOf("바다비오리", "흰줄박이오리", "검은목논병아리"),
            "거친 겨울 파도를 두려워하지 않는 바다의 사냥꾼들"),
        Collection("겨울 들판의 기러기 떼", "🌾", listOf("큰기러기", "쇠기러기", "개리"),
            "V자를 그리며 하늘을 건너와 낙곡을 줍는 대형 기러기들"),
        Collection("백색의 거인 고니 3종", "🦢", listOf("고니", "큰고니", "혹고니"),
            "우아한 긴 목과 순백의 날개로 겨울 호수를 채우는 백조들"),
        Collection("소형 맹금 매과 3종", "⚡", listOf("황조롱이", "매", "새호리기"),
            "공중정지비행과 급강하 사냥 기술을 뽐내는 하늘의 곡예사들"),
        Collection("하늘의 사냥꾼 수리과", "🦅", listOf("말똥가리", "참매", "새매", "잿빛개구리매"),
            "숲과 들판의 기류를 타며 은밀하게 먹이를 노리는 맹금들"),
        Collection("밤의 작은 파수꾼", "🦉", listOf("소쩍새", "큰소쩍새", "솔부엉이"),
            "여름밤 처마 밑과 고목 옹이에서 소쩍소쩍 우는 작은 올빼미들"),
        Collection("여름 숲의 화려한 손님", "🍃", listOf("꾀꼬리", "파랑새", "물총새", "숲새"),
            "신록의 계절에 찾아와 눈부신 색채와 노랫소리를 선사하는 여름새"),
        Collection("숲속의 딱새 삼총사", "🍂", listOf("딱새", "검은딱새", "유리딱새"),
            "나뭇가지 끝에 앉아 꽁지를 까딱이는 숲속의 신사들"),
        Collection("땅을 거니는 지빠귀들", "🌱", listOf("호랑지빠귀", "되지빠귀", "흰배지빠귀", "붉은배지빠귀"),
            "낙엽 밑을 뒤집으며 곤충을 찾는 아름다운 음색의 노래꾼들"),
        Collection("덤불의 작은 친구들", "🐥", listOf("오목눈이", "붉은머리오목눈이", "상모솔새"),
            "솜뭉치처럼 무리 지어 다니며 덤불을 톡톡 튀어 다니는 새들"),
        Collection("울음소리 맑은 숲새", "🎶", listOf("휘파람새", "솔잣새", "동박새"),
            "모습은 숨겨도 맑고 청아한 노랫소리로 숲을 채우는 명가수들"),
        Collection("들판의 깃털 모자 멧새과", "🌾", listOf("노랑턱멧새", "쑥새", "쇠붉은뺨멧새", "검은머리쑥새"),
            "머리의 독특한 깃을 세우며 풀씨를 쪼아먹는 들새들"),
        Collection("화려한 겨울 손님 여새", "🍒", listOf("황여새", "홍여새"),
            "겨울철 겨우살이와 마가목 열매를 찾아 무리 지어 날아오는 새들"),
        Collection("제주의 남쪽 날개", "🍊", listOf("동박새", "흑비둘기", "바다직박구리", "팔색조"),
            "동백꽃 꿀을 먹고 따뜻한 난대림과 곶자왈에 깃든 남쪽의 새들"),
        Collection("해안의 대형 갈매기", "⚓", listOf("재갈매기", "괭이갈매기", "한국재갈매기", "큰재갈매기"),
            "포구와 방파제 상공을 선회하는 늠름한 날개의 바다새들"),
        Collection("물가의 소형 갈매기", "⛵", listOf("붉은부리갈매기", "쇠제비갈매기", "검은머리갈매기"),
            "하천과 갯벌 상공을 날카롭게 가르며 물고기를 낚는 소형 갈매기들"),
        Collection("바위섬의 가마우지과", "🪨", listOf("가마우지", "민물가마우지", "쇠가마우지"),
            "잠수 후 바위에 날개를 활짝 펴고 햇볕에 깃을 말리는 새들"),
        Collection("여름 하늘의 날개", "⛅", listOf("제비", "귀제비", "칼새", "바늘꼬리칼새"),
            "처마 밑 둥지와 푸른 하늘을 오가며 해충을 잡는 비행의 명수"),
        Collection("천연기념물 보전 5종", "👑", listOf("원앙", "노랑부리저어새", "두루미", "수리부엉이", "황조롱이"),
            "우리 땅에서 영원히 지켜나가야 할 소중한 자연유산"),
        Collection("멸종위기 야생생물 I급", "🛡", listOf("저어새", "노랑부리백로", "흰꼬리수리", "참수리"),
            "생존의 벼랑 끝에 선 가장 귀하고 조심스러운 날개들"),
        Collection("멸종위기 야생생물 II급", "🌱", listOf("개리", "큰기러기", "흑두루미", "검은머리물떼새"),
            "서식지 파괴를 멈추고 공존의 길을 열어야 할 이웃들"),
        Collection("들판의 사냥꾼 때까치류", "🍂", listOf("때까치", "칡때까치", "물때까치"),
            "가시덤불에 먹이를 꽂아두는 습성을 가진 작은 맹금성 조류"),
        Collection("겨울 자작나무 숲의 핀치", "🌲", listOf("방울새", "양진이", "멋쟁이새", "되새"),
            "혹한의 눈 덮인 숲에서 열매와 씨앗을 나누어 먹는 겨울새들"),
        Collection("숲과 들의 은둔 닭목", "🍁", listOf("꿩", "들꿩", "메추라기"),
            "화려한 꽁지와 보호색으로 덤불 속에 몸을 숨기는 텃새들"),
        Collection("여름의 목소리 두견이과", "🌿", listOf("뻐꾸기", "벙어리뻐꾸기", "두견이"),
            "초여름 산등성이를 울리는 독특한 울음소리의 주인공들"),
        Collection("먼바다의 날개 슴새류", "🛳", listOf("슴새", "바다제비", "바다비오리"),
            "육지에서 멀리 떨어진 외딴 무인도와 먼바다를 누비는 여행자들"),
        Collection("갯벌의 소형 도요 4종", "🐚", listOf("송곳부리도요", "붉은가슴도요", "꼬까도요", "붉은갯도요"),
            "봄가을 갯벌 흙탕물 속에서 부지런히 에너지를 채우는 작은 나그네새"),
        Collection("고산 침엽수림의 은자", "⛰", listOf("잣까마귀", "솔딱새", "굴뚝새"),
            "높은 산 정상부 가문비나무 숲에서만 들려오는 깊은 소리"),
        Collection("갈대밭의 숨은 소리꾼", "🌾", listOf("개개비", "큰개개비", "쇠개개비"),
            "여름 갈대 줄기에 매달려 쉼 없이 목청껏 노래하는 갈대밭의 가수들"),
        Collection("숲의 멋쟁이 긴 꼬리", "✨", listOf("긴꼬리딱새", "물까치", "어치"),
            "바람에 나부끼는 긴 꼬리깃과 독특한 울음소리로 숲을 지키는 새들"),
        Collection("순천만 습지의 겨울", "🌅", listOf("흑두루미", "검은머리갈매기", "큰고니", "노랑부리저어새"),
            "갈대군락과 드넓은 갯벌이 품어 안은 세계적인 철새 낙원"),
        Collection("철원 DMZ의 겨울 독수리", "❄️", listOf("독수리", "두루미", "재두루미", "쇠기러기"),
            "민간인 통제선의 평화로운 논밭에서 겨울을 나는 대형 조류들"),
        Collection("우포늪의 아침 안개", "🌫", listOf("따오기", "큰고니", "쇠물닭", "노랑부리저어새"),
            "태고의 신비를 간직한 국내 최대 내륙습지의 대표 생명들"),
        Collection("낙동강 하구의 삼각주", "🌊", listOf("고니", "쇠제비갈매기", "노랑발도요", "괭이갈매기"),
            "강과 바다가 만나는 기수역 모래톱의 활기찬 새들"),
        Collection("소나무 숲의 솔잣새류", "🌲", listOf("솔잣새", "흰죽지솔잣새", "동박새"),
            "교차된 특이한 부리로 솔방울 씨앗을 능숙하게 빼먹는 솔숲의 장인"),
        Collection("붉은빛의 매혹 4종", "🌺", listOf("긴꼬리홍양진이", "양진이", "홍여새", "붉은배새매"),
            "눈 덮인 겨울 숲에 붉은 꽃잎처럼 화사하게 피어나는 깃털"),
        Collection("하늘 높이 솟는 종다리", "☀️", listOf("종다리", "뿔종다리", "쇠종다리"),
            "푸른 봄 하늘 높이 솟아올라 지저귀는 들판의 상징"),
        Collection("봄을 여는 첫 나그네", "🌸", listOf("노랑눈썹솔새", "유리딱새", "제비", "노랑할미새"),
            "얼어붙은 땅이 녹고 따뜻한 봄바람과 함께 제일 먼저 찾아오는 손님"),
        Collection("가을 억새밭의 씨앗 사냥", "🌾", listOf("검은머리쑥새", "멧새", "쑥새", "무당새"),
            "가을바람에 흔들리는 은빛 억새밭에서 씨앗을 모으는 작은 멧새들"),
        Collection("물속을 걷는 잠수새", "💧", listOf("물까마귀", "쇠물닭", "논병아리"),
            "계곡 물살 속을 걸어 다니며 수생곤충을 사냥하는 신비한 물새"),
        Collection("도심 공원의 아침 합창", "🌳", listOf("직박구리", "박새", "곤줄박이", "붉은머리오목눈이"),
            "출근길 아침 우리 곁에서 맑은 하루를 열어주는 도심 텃새 4총사"),
        Collection("한반도 마스터 탐조 4종", "🏆", listOf("팔색조", "따오기", "저어새", "참수리"),
            "평생 한 번 마주하기도 어려운 한국 최고의 진귀한 날개들"),
        Collection("계곡의 물총새와 친구들", "🏞", listOf("물총새", "물까마귀", "할미새사촌"),
            "맑은 청정 계곡 물줄기를 따라 날아다니는 시원한 날갯짓"),
        Collection("서해 고도 흑산·어청도", "🏝", listOf("팔색조", "긴꼬리딱새", "붉은배지빠귀", "노랑눈썹솔새"),
            "먼 바다를 건너다 섬 숲에서 지친 날개를 쉬어가는 나그네새들"),
        Collection("숲속의 도토리 지킴이", "🌰", listOf("어치", "곤줄박이", "쇠박새"),
            "가을마다 숲 바닥에 도토리를 묻어 참나무 숲을 번성시키는 농부 새들")
    )
}

/** 메인 스토리: 보리 박사와 함께 '함께 사는 새 지도'의 빈 페이지를 채운다. */

/**
 * 다양한 퀘스트 종류(유형)를 정의하는 카테고리.
 * 기존의 단순 새 1종 촬영에서 벗어나 서식지 탐사, 별점 촬영, 야간 탐조,
 * 분류군 연구, 기상 탐조, 비행 포착, 피자 배달 등 다채로운 임무를 제공한다.
 */
enum class QuestCategory(
    val label: String,
    val icon: String,
    val tagColor: Long,
    val note: String
) {
    BIRD_SPECIES("지정 조류", "search", 0xFF6BA2D4, "지정된 특정 종의 사진을 선명하게 기록합니다"),
    HABITAT_SURVEY("서식지 탐사", "tree", 0xFF5FA868, "숲, 물가, 갯벌, 산 등 특정 환경의 새들을 관찰합니다"),
    STAR_QUALITY("3성 촬영", "sparkle", 0xFFE5A93C, "초근접 거리에서 생동감 넘치는 3성 명품 사진을 남깁니다"),
    NIGHT_EXPEDITION("야간 야조", "moon", 0xFF8368B2, "달빛 아래 밤에 활동하는 야행성 조류의 비밀을 기록합니다"),
    FAMILY_RESEARCH("분류군 연구", "feather", 0xFFC6724E, "오리과, 딱다구리과, 맹금류 등 특정 무리의 생태를 조사합니다"),
    WEATHER_EXPEDITION("기상 탐조", "cloud", 0xFF5B96B2, "비나 눈이 내리는 악천후 속 생명의 날갯짓을 관찰합니다"),
    LIFER_DISCOVERY("새로운 종", "book", 0xFFD46882, "아직 도감에 기록되지 않은 새로운 라이퍼를 찾아냅니다"),
    IN_FLIGHT_ACTION("비행 포착", "wind", 0xFF4EADA5, "하늘을 날아오르거나 빠르게 이동하는 결정적 순간을 담습니다"),
    PIZZA_DELIVERY("피자 심부름", "pizza", 0xFFD88939, "따뜻하게 구운 피자로 탐조 여행자 및 주민과 교류합니다")
}

/** 서브 의뢰(탐조 협회/보리 박사 의뢰) 데이터 */
data class QuestData(
    val id: String,
    val category: QuestCategory,
    val title: String,
    val description: String,
    val targetKey: String = "",       // birdId, habitat("forest"), family, etc.
    val targetCount: Int = 1,
    var currentProgress: Int = 0,
    val rewardMoney: Int = 5000,
    val rewardExp: Int = 40,
    val rewardLuck: Int = 1,
    var completed: Boolean = false,
    val targetRegionId: String = ""
) {
    val isComplete: Boolean get() = currentProgress >= targetCount
    val progressText: String get() = "${currentProgress.coerceAtMost(targetCount)}/$targetCount"

    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("category", category.name)
        put("title", title)
        put("description", description)
        put("targetKey", targetKey)
        put("targetCount", targetCount)
        put("currentProgress", currentProgress)
        put("rewardMoney", rewardMoney)
        put("rewardExp", rewardExp)
        put("rewardLuck", rewardLuck)
        put("completed", completed)
        put("targetRegionId", targetRegionId)
    }

    companion object {
        fun fromJson(j: org.json.JSONObject): QuestData? {
            val id = j.optString("id", "").ifEmpty { return null }
            val catName = j.optString("category", "BIRD_SPECIES")
            val cat = try { QuestCategory.valueOf(catName) } catch (_: Exception) { QuestCategory.BIRD_SPECIES }
            return QuestData(
                id = id,
                category = cat,
                title = j.optString("title", "탐조 의뢰"),
                description = j.optString("description", ""),
                targetKey = j.optString("targetKey", ""),
                targetCount = j.optInt("targetCount", 1).coerceAtLeast(1),
                currentProgress = j.optInt("currentProgress", 0),
                rewardMoney = j.optInt("rewardMoney", 5000),
                rewardExp = j.optInt("rewardExp", 40),
                rewardLuck = j.optInt("rewardLuck", 1),
                completed = j.optBoolean("completed", false),
                targetRegionId = j.optString("targetRegionId", "")
            )
        }
    }
}

/** 매일 갱신되는 일일 탐조 미션 데이터 */
data class DailyQuestData(
    val id: String,
    val category: QuestCategory,
    val title: String,
    val description: String,
    val targetKey: String = "",
    val targetCount: Int = 1,
    var currentProgress: Int = 0,
    val rewardMoney: Int = 4000,
    val rewardExp: Int = 30,
    val rewardLuck: Int = 1,
    var completed: Boolean = false,
    val targetRegionId: String = ""
) {
    val isComplete: Boolean get() = currentProgress >= targetCount
    val progressText: String get() = "${currentProgress.coerceAtMost(targetCount)}/$targetCount"

    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("category", category.name)
        put("title", title)
        put("description", description)
        put("targetKey", targetKey)
        put("targetCount", targetCount)
        put("currentProgress", currentProgress)
        put("rewardMoney", rewardMoney)
        put("rewardExp", rewardExp)
        put("rewardLuck", rewardLuck)
        put("completed", completed)
        put("targetRegionId", targetRegionId)
    }

    companion object {
        fun fromJson(j: org.json.JSONObject): DailyQuestData? {
            val id = j.optString("id", "").ifEmpty { return null }
            val catName = j.optString("category", "HABITAT_SURVEY")
            val cat = try { QuestCategory.valueOf(catName) } catch (_: Exception) { QuestCategory.HABITAT_SURVEY }
            return DailyQuestData(
                id = id,
                category = cat,
                title = j.optString("title", "일일 탐조 미션"),
                description = j.optString("description", ""),
                targetKey = j.optString("targetKey", ""),
                targetCount = j.optInt("targetCount", 1).coerceAtLeast(1),
                currentProgress = j.optInt("currentProgress", 0),
                rewardMoney = j.optInt("rewardMoney", 4000),
                rewardExp = j.optInt("rewardExp", 30),
                rewardLuck = j.optInt("rewardLuck", 1),
                completed = j.optBoolean("completed", false),
                targetRegionId = j.optString("targetRegionId", "")
            )
        }
    }
}

/**
 * 퀘스트 매니저: 다양한 종류의 퀘스트 생성, 일일 미션 루틴, 촬영/행동 이벤트 판정.
 */
object QuestManager {
    private val rnd = java.util.Random()

    /** 보리 박사 또는 의뢰 게시판에서 선택할 수 있는 서로 다른 종류의 의뢰 목록 4종 생성 */
    fun generateBoardQuests(s: GameState, count: Int = 4): List<QuestData> {
        val list = ArrayList<QuestData>()
        val reg = Regions.byId[s.region] ?: Regions.ALL.first()
        val pool = (Birds.poolFor(reg, false) + Birds.poolFor(reg, true)).ifEmpty { Birds.ALL }

        // 1. 지정 조류 기록 (미기록 새 우선 추천)
        val unrecorded = pool.filter { (s.birdCounts[it.id] ?: 0) == 0 && it.tier.star <= 2 }
        val targetBird = if (unrecorded.isNotEmpty() && rnd.nextDouble() < 0.6) {
            unrecorded[rnd.nextInt(unrecorded.size)]
        } else {
            pool[rnd.nextInt(pool.size)]
        }
        val birdReward = (targetBird.reward * 1.15f).toInt()
        val birdExp = (birdReward / 55).coerceIn(20, 100)
        list.add(
            QuestData(
                id = "bird_${targetBird.id}_${System.currentTimeMillis()}",
                category = QuestCategory.BIRD_SPECIES,
                title = "${targetBird.name} 사진 기록",
                description = "${reg.name} 일대에서 발견되는 ${targetBird.name}의 선명한 사진을 촬영해 오세요.",
                targetKey = targetBird.id,
                targetCount = 1,
                targetRegionId = reg.id,
                rewardMoney = birdReward,
                rewardExp = birdExp,
                rewardLuck = 1
            )
        )

        // 2. 서식지 생태 조사 (숲/물가/갯벌/산/도심 중 하나)
        val habitats = listOf(
            Triple("forest", "깊은 숲 생태 조사", "숲 서식지 새 3종을 렌즈에 담아 기록하세요."),
            Triple("water", "하천과 습지 조사", "물가나 하천 주변에 서식하는 새 3종을 촬영하세요."),
            Triple("coast", "해안과 갯벌 생태 기록", "바다나 갯벌을 찾는 새 2종을 관찰하고 기록하세요."),
            Triple("city", "도심 공원 탐조", "공원과 가로수에서 살아가는 이웃 새 3종을 촬영하세요.")
        )
        val hab = habitats[rnd.nextInt(habitats.size)]
        val habCount = if (hab.first == "coast") 2 else 3
        list.add(
            QuestData(
                id = "hab_${hab.first}_${System.currentTimeMillis()}",
                category = QuestCategory.HABITAT_SURVEY,
                title = hab.second,
                description = hab.third,
                targetKey = hab.first,
                targetCount = habCount,
                rewardMoney = 12000 + habCount * 2000,
                rewardExp = 60 + habCount * 15,
                rewardLuck = 2
            )
        )

        // 3. 3성 고품질 별점 촬영
        list.add(
            QuestData(
                id = "star_${System.currentTimeMillis()}",
                category = QuestCategory.STAR_QUALITY,
                title = "완벽한 거리의 미학",
                description = "적정 초점거리 이내로 조심스럽게 다가가 3성 이상의 명품 사진을 2장 완성하세요.",
                targetKey = "star3",
                targetCount = 2,
                rewardMoney = 16000,
                rewardExp = 85,
                rewardLuck = 2
            )
        )

        // 4. 특별 탐사 (야간 야조 / 비행 포착 / 신규 종 / 분류군 중 1종)
        val specials = listOf(
            QuestData(
                id = "night_${System.currentTimeMillis()}",
                category = QuestCategory.NIGHT_EXPEDITION,
                title = "달빛 야간 탐조",
                description = "어둠이 내린 밤(19시 이후)에 활동하는 야행성 조류 2종을 은밀하게 관찰하세요.",
                targetKey = "night",
                targetCount = 2,
                rewardMoney = 18000,
                rewardExp = 90,
                rewardLuck = 2
            ),
            QuestData(
                id = "flight_${System.currentTimeMillis()}",
                category = QuestCategory.IN_FLIGHT_ACTION,
                title = "바람을 타는 날개",
                description = "날아오르거나 빠르게 활공하는 새의 역동적인 순간을 2회 포착하세요.",
                targetKey = "flight",
                targetCount = 2,
                rewardMoney = 15000,
                rewardExp = 80,
                rewardLuck = 2
            ),
            QuestData(
                id = "lifer_${System.currentTimeMillis()}",
                category = QuestCategory.LIFER_DISCOVERY,
                title = "미지의 새 첫 발견",
                description = "아직 자네의 도감에 없는 새로운 미기록 종(라이퍼) 2종을 찾아 사진으로 남기게.",
                targetKey = "lifer",
                targetCount = 2,
                rewardMoney = 22000,
                rewardExp = 110,
                rewardLuck = 3
            ),
            QuestData(
                id = "family_woodpecker_${System.currentTimeMillis()}",
                category = QuestCategory.FAMILY_RESEARCH,
                title = "딱다구리과 심층 조사",
                description = "나무를 두드리는 숲의 목수, 딱다구리과 새 1종의 세부 깃털을 기록하세요.",
                targetKey = "딱다구리",
                targetCount = 1,
                rewardMoney = 14000,
                rewardExp = 75,
                rewardLuck = 2
            )
        )
        list.add(specials[rnd.nextInt(specials.size)])
        return list.take(count)
    }

    /** 날짜가 바뀌면 매일 3가지 일일 미션 자동 생성 */
    fun ensureDailyQuests(s: GameState) {
        if (s.dailyQuests.isNotEmpty() && s.lastDailyDay == s.day) return
        s.dailyQuests.clear()
        s.lastDailyDay = s.day

        val day = s.day
        // 미션 1: 서식지 일일 탐조
        val habs = listOf(
            Triple("forest", "[일일] 숲새의 아침 노래", "숲 서식지에 사는 새 2종 촬영"),
            Triple("water", "[일일] 물가의 찰랑이는 물결", "하천이나 호수 주변 새 2종 촬영"),
            Triple("field", "[일일] 들판의 활기찬 날개", "들판이나 공원의 새 2종 촬영")
        )
        val h = habs[day % habs.size]
        s.dailyQuests.add(
            DailyQuestData(
                id = "daily_hab_${day}",
                category = QuestCategory.HABITAT_SURVEY,
                title = h.second,
                description = h.third,
                targetKey = h.first,
                targetCount = 2,
                rewardMoney = 7000,
                rewardExp = 40,
                rewardLuck = 1
            )
        )

        // 미션 2: 촬영 기술 미션 (3성 또는 비행)
        if (day % 2 == 0) {
            s.dailyQuests.add(
                DailyQuestData(
                    id = "daily_star_${day}",
                    category = QuestCategory.STAR_QUALITY,
                    title = "[일일] 명품 구도의 한 컷",
                    description = "조심스럽게 접근하여 3성 고화질 사진 1장 촬영",
                    targetKey = "star3",
                    targetCount = 1,
                    rewardMoney = 8000,
                    rewardExp = 45,
                    rewardLuck = 1
                )
            )
        } else {
            s.dailyQuests.add(
                DailyQuestData(
                    id = "daily_flight_${day}",
                    category = QuestCategory.IN_FLIGHT_ACTION,
                    title = "[일일] 날갯짓의 찰나",
                    description = "날아가거나 도주하는 새의 순간 포착 1회 성공",
                    targetKey = "flight",
                    targetCount = 1,
                    rewardMoney = 8000,
                    rewardExp = 45,
                    rewardLuck = 1
                )
            )
        }

        // 미션 3: 일상 & 피자 / 야간 탐조
        if (day % 2 == 0) {
            s.dailyQuests.add(
                DailyQuestData(
                    id = "daily_pizza_${day}",
                    category = QuestCategory.PIZZA_DELIVERY,
                    title = "[일일] 탐조가의 든든한 식사",
                    description = "화덕에서 맛있는 피자 1판 굽기",
                    targetKey = "bake",
                    targetCount = 1,
                    rewardMoney = 6000,
                    rewardExp = 35,
                    rewardLuck = 1
                )
            )
        } else {
            s.dailyQuests.add(
                DailyQuestData(
                    id = "daily_night_${day}",
                    category = QuestCategory.NIGHT_EXPEDITION,
                    title = "[일일] 밤을 밝히는 눈동자",
                    description = "야간 시간대에 활동하는 새 1종 촬영",
                    targetKey = "night",
                    targetCount = 1,
                    rewardMoney = 7500,
                    rewardExp = 40,
                    rewardLuck = 1
                )
            )
        }
    }

    /** 사진 촬영 시 활성 의뢰 및 일일 미션 달성 검사 */
    fun onPhotoTaken(
        s: GameState,
        birdDef: BirdDef,
        stars: Int,
        isNew: Boolean,
        isNight: Boolean,
        isAction: Boolean
    ): List<String> {
        val completedLines = ArrayList<String>()

        // 1. 활성 서브 의뢰 검사
        for (q in s.activeQuests) {
            if (q.completed) continue
            var matched = false
            when (q.category) {
                QuestCategory.BIRD_SPECIES -> if (birdDef.id == q.targetKey) matched = true
                QuestCategory.HABITAT_SURVEY -> if (birdDef.habitats.contains(q.targetKey)) matched = true
                QuestCategory.STAR_QUALITY -> if (stars >= 3) matched = true
                QuestCategory.NIGHT_EXPEDITION -> if (isNight) matched = true
                QuestCategory.FAMILY_RESEARCH -> if (birdDef.name.contains(q.targetKey)) matched = true
                QuestCategory.WEATHER_EXPEDITION -> if (s.weatherId == Weather.RAIN.id || s.weatherId == Weather.SNOW.id) matched = true
                QuestCategory.LIFER_DISCOVERY -> if (isNew) matched = true
                QuestCategory.IN_FLIGHT_ACTION -> if (isAction) matched = true
                QuestCategory.PIZZA_DELIVERY -> {}
            }
            if (matched) {
                q.currentProgress++
                if (q.isComplete) {
                    q.completed = true
                    // 지정 조류 의뢰를 3성으로 완수하면 30% 보너스 (구 단일의뢰 시절 규칙 유지)
                    var pay = q.rewardMoney
                    var bonus = 0
                    if (q.category == QuestCategory.BIRD_SPECIES && stars >= 3) {
                        bonus = (q.rewardMoney * 0.3f).toInt()
                        pay += bonus
                    }
                    s.money += pay
                    s.addExp(q.rewardExp)
                    s.luck += q.rewardLuck
                    completedLines.add("의뢰 완수! [${q.category.label}] ${q.title} (+₩${won(pay)})" + if (bonus > 0) " (3성 보너스)" else "")
                    if (q.category == QuestCategory.BIRD_SPECIES && s.questBird == q.targetKey) {
                        s.questBird = null
                        s.questReward = 0
                    }
                }
            }
        }
        s.activeQuests.removeAll { it.completed }

        // 2. 일일 탐조 미션 검사
        for (dq in s.dailyQuests) {
            if (dq.completed) continue
            var matched = false
            when (dq.category) {
                QuestCategory.HABITAT_SURVEY -> if (birdDef.habitats.contains(dq.targetKey)) matched = true
                QuestCategory.STAR_QUALITY -> if (stars >= 3) matched = true
                QuestCategory.NIGHT_EXPEDITION -> if (isNight) matched = true
                QuestCategory.IN_FLIGHT_ACTION -> if (isAction) matched = true
                QuestCategory.BIRD_SPECIES -> if (birdDef.id == dq.targetKey) matched = true
                QuestCategory.LIFER_DISCOVERY -> if (isNew) matched = true
                else -> {}
            }
            if (matched) {
                dq.currentProgress++
                if (dq.isComplete) {
                    dq.completed = true
                    s.money += dq.rewardMoney
                    s.addExp(dq.rewardExp)
                    s.luck += dq.rewardLuck
                    completedLines.add("일일 미션 달성! ${dq.title} (+₩${won(dq.rewardMoney)})")
                }
            }
        }

        return completedLines
    }

    /** 피자 굽기 시 퀘스트 검사 */
    fun onPizzaBaked(s: GameState, pizzaDef: PizzaDef): List<String> {
        val completedLines = ArrayList<String>()
        for (q in s.activeQuests) {
            if (q.completed) continue
            if (q.category == QuestCategory.PIZZA_DELIVERY) {
                q.currentProgress++
                if (q.isComplete) {
                    q.completed = true
                    s.money += q.rewardMoney
                    s.addExp(q.rewardExp)
                    s.luck += q.rewardLuck
                    completedLines.add("의뢰 완수! [${q.category.label}] ${q.title} (+₩${won(q.rewardMoney)})")
                }
            }
        }
        s.activeQuests.removeAll { it.completed }

        for (dq in s.dailyQuests) {
            if (dq.completed) continue
            if (dq.category == QuestCategory.PIZZA_DELIVERY) {
                dq.currentProgress++
                if (dq.isComplete) {
                    dq.completed = true
                    s.money += dq.rewardMoney
                    s.addExp(dq.rewardExp)
                    s.luck += dq.rewardLuck
                    completedLines.add("일일 미션 달성! ${dq.title} (+₩${won(dq.rewardMoney)})")
                }
            }
        }
        return completedLines
    }

    private fun won(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)
}


object MainStory {
    data class Chapter(
        val title: String,
        val subtitle: String,
        val minLevel: Int = 1,
        val minLifers: Int = 0,
        val minVisited: Int = 1,
        val collection: String? = null,
        val minThreeStars: Int = 0,
        val rewardMoney: Int,
        val rewardExp: Int,
        val intro: String,
        val complete: String
    ) {
        fun collectionDef() = BirdingCollections.ALL.firstOrNull { it.name == collection }

        fun isComplete(s: GameState): Boolean =
            s.level >= minLevel && s.birdCounts.size >= minLifers && s.visited.size >= minVisited &&
                s.bestStars.values.count { it >= 3 } >= minThreeStars &&
                (collectionDef()?.complete(s) != false)

        fun objective(s: GameState): String {
            val parts = ArrayList<String>()
            if (minLevel > 1) parts += "레벨 ${s.level}/$minLevel"
            if (minLifers > 0) parts += "라이퍼 ${s.birdCounts.size}/${minLifers}종"
            if (minVisited > 1) parts += "방문 ${s.visited.size}/${minVisited}곳"
            collectionDef()?.let { parts += "${it.name} ${it.progress(s)}" }
            if (minThreeStars > 0) parts += "3성 기록 ${s.bestStars.values.count { it >= 3 }}/${minThreeStars}종"
            return if (parts.isEmpty()) "보리 박사에게 말을 걸기" else parts.joinToString(" · ")
        }
    }

    val CHAPTERS = listOf(
        Chapter("프롤로그 · 빈 도감", "할머니의 낡은 수첩", rewardMoney = 5000, rewardExp = 25,
            intro = "이 수첩은 자네 할머니가 남긴 탐조 기록일세. 나와 그이는 이십 년 넘게 같은 쌍안경 가방을 나눠 메고 다녔지. 귀퉁이는 접히고 군데군데 잉크가 번졌지만, 장 사이마다 오래된 기름때가 배어 있네. 그녀가 화덕피자를 구울 때면 창문을 활짝 열어 두셨거든. 김이 밖으로 나가면 참새가 모여 들었지. 마지막 장에는 또렷하게 이렇게 쓰여 있네. ‘새를 안다는 건 함께 살 방법을 배우는 일.’ 그녀가 떠난 뒤로 나는 이 수첩을 한 번도 펼쳐 보지 못했네. 자네가 이 빈 페이지를 이어 준다면, 나도 다시 용기를 낼 수 있을 것 같군.",
            complete = "좋아, 시작해 보세. 이건 종수를 다투는 시합이 아니라, 만난 장소와 그때 든 마음까지 함께 적는 기록일세. 서두르지 말고 오늘 아침 자네 창밖을 스쳐 간 새들부터 이름을 물어보게. …배가 고프면 집의 화덕에 불을 붙이게. 할머니도 늘 출발 전에 한 판을 구우셨거든."),
        Chapter("1장 · 창밖의 이웃", "이름을 알면 풍경이 달라진다", minLevel = 2, minLifers = 5,
            collection = "동네 첫 만남", rewardMoney = 12000, rewardExp = 55,
            intro = "멀리 떠나기 전에 매일 스쳐 가던 이웃부터 만나게. 참새와 까치, 직박구리와 박새, 멧비둘기까지. 흔하다고 지나쳤던 새들에게도 저마다 다른 하루가 있다네. 이름 하나를 알고 나면 늘 걷던 골목이 조금 다르게 보이기 시작할 걸세. 서두르지 않아도 되네. 이웃은 어디 가지 않으니까.",
            complete = "자네 사진엔 흔한 새가 아니라 ‘이웃’이 찍혔군. 그런데 수첩을 정리하다 사이에 끼워 둔 낡은 쪽지를 하나 발견했네. 나무를 두드리는 소리에 대해 적어 둔 모양인데, 함께 확인하러 가 보겠나? …할머니의 글씨는 작았지만 힘이 있었지."),
        Chapter("2장 · 숲의 모스 부호", "딱다구리 세 종의 두드림", minLevel = 4,
            collection = "딱다구리 기본 3종", rewardMoney = 30000, rewardExp = 110,
            intro = "쪽지가 가리킨 곳은 뒷산 숲이었네. 쇠딱다구리, 오색딱다구리, 청딱다구리. 세 목수의 크기와 배 무늬, 등 색을 천천히 비교해 보게. 두드리는 소리가 들리는 나무 밑동에 바짝 다가서지는 말게. 그 안이 둥지일 수도 있으니까. 숲은 소리로 먼저 인사를 건네는 곳이라네.",
            complete = "두드림은 숲이 살아 있다는 전보 같은 거였어. 나도 오랜만에 그 소리를 들으니 마음이 놓이는군. …솔직히 말하면, 나는 한동안 그 소리를 피하고 다녔네. 사정은 물길을 따라가 보면 조금은 알게 될 걸세. 이번엔 강과 습지로 가 보세."),
        Chapter("3장 · 물길의 기억", "강과 습지가 이어 주는 길", minLevel = 7, minLifers = 30, minVisited = 5,
            collection = "물총새과 4종", rewardMoney = 65000, rewardExp = 190,
            intro = "물총새과 네 종을 찾아 물길의 색을 기록해 보게. 물총새, 호반새, 청호반새, 뿔호반새. 참, 이름만 비슷한 청도요는 사실 물총새가 아니라 도요류라네. 정확한 이름을 아는 일이 정확한 보호의 시작이라고, 자네 할머니는 늘 말했었지. 물길은 서로 다른 새를 같은 언어로 이어 주는 길이라네.",
            complete = "서울의 하천과 남쪽 계곡이 새들의 길로 이어졌군. 수첩 여백을 다시 보니 할머니가 작은 글씨로 이렇게 적어 두셨네. ‘계절은 날개를 타고 온다.’ 다음 장은 그 말이 무슨 뜻인지 몸으로 알게 될 걸세. …겨울이 오기 전에, 날개를 먼저 만나러 가 보세."),
        Chapter("4장 · 계절의 날개", "한반도를 건너는 여행자들", minLevel = 11, minLifers = 70, minVisited = 10,
            collection = "겨울 오리 삼색", minThreeStars = 5, rewardMoney = 120000, rewardExp = 300,
            intro = "가창오리 무리는 밤하늘을 강처럼 흐르지. 원앙과 청머리오리까지 함께 기록하며, 열 곳의 풍경을 이어 철새들이 그리는 커다란 길을 그려 보게. 한 마리가 아니라 무리 전체가 한 계절을 옮기고 있다는 걸 느껴 보게나. 떠나는 새에게는 작별 인사가 필요 없네. 길을 비켜 주는 것이 예의니까.",
            complete = "지도 위에 흩어져 있던 점들이 마침내 하나의 이동 경로로 이어졌군. 굉장한 풍경이었을 걸세. 이제부터는 서두르지 않고, 비슷해 보이는 작은 새들의 미세한 차이까지 읽어 낼 눈이 필요하네. …할머니가 그러셨지. ‘급한 마음은 새를 작게 만든다’고."),
        Chapter("5장 · 갯벌의 쉼표", "작은 차이를 읽는 눈", minLevel = 15, minLifers = 110, minVisited = 16,
            collection = "도요 입문 4종", minThreeStars = 12, rewardMoney = 220000, rewardExp = 440,
            intro = "도요물떼새에게 갯벌은 긴 여행길의 쉼표 같은 곳일세. 부리 길이와 다리 색만 볼 게 아니라, 먹이를 찾는 걸음걸이와 무리 사이의 거리까지 천천히 기록해 보게. 서두르는 눈에는 다 똑같아 보이지만, 기다리는 눈에는 전부 다르게 보인다네. 작은 차이를 읽는다는 건, 그만큼 오래 보고 싶다는 마음이니까.",
            complete = "허, 이젠 자네가 내 사진의 동정 오류까지 짚어내는군. 몰라볼 만큼 늘었네. 하지만 귀한 새를 찾아내는 일보다 훨씬 어려운 게 하나 남아 있지. 바로 그 자리를 지키는 일일세. …미안하기도 하군. 그게 얼마나 어려운 일인지, 나는 너무 늦게 배웠거든. 다음 장에서 그 이야기를 하겠네."),
        Chapter("6장 · 지켜 보는 사람", "발견보다 먼저 배워야 할 거리", minLevel = 20, minLifers = 160, minVisited = 24,
            collection = "보전의 깃발", minThreeStars = 25, rewardMoney = 400000, rewardExp = 650,
            intro = "저어새와 노랑부리백로의 사진 한 장보다 번식지의 평온이 언제나 먼저일세. 위치를 함부로 퍼뜨리지 말고, 정해진 길에서 벗어나지 말게. 자네가 찍고 싶은 마음보다 새가 살아야 할 자리를 먼저 생각하는 사람이 되어 주게. …사실 나도 한동안은 그 거리를 지키지 못해 카메라를 내려놓았던 시절이 있었네. 젊은 날엔 희귀새 한 마리가 걸음걸이보다 빨랐지. 그 뒤로 몇 해를 숲 앞에서 망설이다가, 자네 할머니가 이렇게 말해 주었네. ‘물러설 줄 아는 사람만이 오래 볼 수 있다’고.",
            complete = "좋은 기록자는 새를 소유하지 않고, 물러설 때를 아는 사람이야. …나에게 그걸 가르쳐 준 건 자네 할머니였네. 이제 수첩엔 마지막 빈 장 하나만 남았군. 그 한 장은 내가 대신 채워 줄 수 없는 자리야. 자네가 직접 써 주게."),
        Chapter("마지막 장 · 함께 사는 지도", "전설의 탐조가가 남길 문장", minLevel = Progression.MAX_LEVEL,
            rewardMoney = 1000000, rewardExp = 0,
            intro = "마지막 장은 내가 대신 써 줄 수 없다네. 동네 골목에서 시작해 숲과 물길, 철새의 큰 길과 갯벌의 작은 차이, 그리고 지켜야 할 거리까지. 지금까지 걸어온 길을 되새기며 자네가 직접 문장을 남겨 주게. 만렙에 오른 지금이라면 그 답을 이미 알고 있을지도 모르지. …천천히 해도 좋네. 수첩은 기다리는 법을 알고 있으니까.",
            complete = "‘우리가 새를 바라보는 동안, 새도 살아갈 내일을 얻기를.’ 훌륭하군. 자네 할머니가 남긴 첫 문장에서 시작해, 자네가 마지막 문장을 완성했네. …그녀가 즐겨 하시던 말이 떠오르는군. ‘피자는 먹을 때가 가장 좋고, 새는 보낼 때가 가장 값진 법이다.’ 메인 이야기는 여기서 멈추지만 계절과 새의 이야기, 그리고 이 동네 사람들의 이야기는 끝나지 않아. 의뢰와 도장 깨기는 언제든 계속하게.")
    )

    /** 각 장을 마친 뒤 할머니의 수첩에서 발견하는 레시피. 0장은 시작부터 사용 가능. */
    private val pizzaRecipes = listOf(
        listOf(6),                    // 처음엔 마르게리타 한 판
        listOf(0, 1, 7),             // 프롤로그
        listOf(3, 4, 12),            // 동네
        listOf(2, 8, 13),            // 숲
        listOf(5, 9, 14, 15),        // 물길
        listOf(10, 16, 17),          // 계절
        listOf(11, 18),              // 갯벌
        listOf(19)                   // 지켜 보는 사람
    )

    fun unlockedPizzas(stage: Int): Set<Int> =
        pizzaRecipes.take((stage + 1).coerceIn(1, pizzaRecipes.size)).flatten().toSet()

    fun newlyUnlockedPizzas(stage: Int): List<PizzaDef> =
        pizzaRecipes.getOrElse(stage) { emptyList() }.map(Pizzas::of)

    fun current(s: GameState): Chapter? = if (s.mainQuestFinished) null else CHAPTERS.getOrNull(s.mainQuestStage)

    /**
     * 「할머니의 수첩 한 장」 — 장(章) 하나를 마칠 때마다 그 장의 사이에 끼워 둔 쪽지가 나온다
     * (`NotebookOverlay` 의 앞장, v0.5 「수첩을 다시 펴다」).
     *
     *  - `RELICS[i]` 는 `CHAPTERS[i]` 를 끝냈을 때 넘어오는 장이다 — 순서가 어긋나면 내용이 꼬인다
     *    (MapTest·story_smoke 가 길이를 함께 검사한다).
     *  - **저장하지 않는다.** 이미 읽은 장의 수(`mainQuestStage`)만으로 얼마든지 복원되므로,
     *    세이브 포맷을 건드리지 않고 후일담을 늘릴 수 있다.
     *  - 큰따옴표 속 인용문은 할머니의 필체(한 줄 메모)다. 설명·지시문을 섞지 말 것.
     */
    val RELICS: List<Pair<String, String>> = listOf(
        "수첩의 첫 장" to "첫 장은 비워 두는 거야. 만난 순서대로 채우면 그것으로 충분하니까.",
        "창밖의 메모" to "흔한 새는 없어. 아직 이름을 불러 주지 못한 새가 있을 뿐이야.",
        "나무 밑동 쪽지" to "두드림이 들리면 멈춰 서게. 세 번을 더 기다리면 숲이 답을 준다.",
        "물길에 적은 한 줄" to "계절은 날개를 타고 온다.",
        "하늘을 본 기록" to "급한 마음은 새를 작게 만든다.",
        "갯벌의 쉼표" to "비슷해 보인다는 건, 아직 오래 좋아하지 않았다는 뜻이야.",
        "멀리서 본 마음" to "물러설 줄 아는 사람만이 오래 볼 수 있다.",
        "마지막 빈 장" to "피자는 먹을 때가 가장 좋고, 새는 보낼 때가 가장 값진 법이다."
    )

    /** 지금까지 읽은(완료한) 장이 꺼낸 수첩 쪽지 목록 — 오래된 순서대로. */
    fun relicsSoFar(stage: Int, finished: Boolean): List<Pair<String, String>> {
        val n = if (finished) RELICS.size else stage
        return RELICS.take(n.coerceIn(0, RELICS.size))
    }
}

/**
 * 메인 퀘스트 자동 진행 어드바이저.
 *
 * "위치도 모르는데" — 현재 장의 목표를 풀어서 어디로 가야 하는지(·왜 거기인지)를
 * 자동 계산한다. 메인 퀘스트 카드/대화를 누르면 자전거를 타고 실제 길을 따라 목표에 간다.
 *
 * 우선순위:
 *  1. 컬렉션 미완료 — 남은 새가 가장 많이 출현하는 지역
 *  2. 방문 부족 — 지역 루트 그래프(BFS) 기준 가장 가까운 미방문 지역
 *  3. 레벨·라이퍼·3성 부족 — 아직 못 찍은 새가 가장 많은 지역
 *
 * 목표 달성 시 정답은 **보리 박사가 사는 지역**이다 — 박사는 광릉숲 숲속 쉼터 한 곳에만 산다
 * (`NpcRoster.PROFESSOR_REGION`). 그래서 카드/대화의 🚲 버튼이 그 지역의 박사까지 안내한다.
 */
object MainQuestAdvisor {

    data class Advice(
        val regionId: String,
        val regionName: String,
        val reason: String,      // 왜 여기인지 (메뉴 카드·박사 대화에 표시)
        val tip: String,         // 도착하면 뭘 해야 하는지 (토스트/팁)
        val alreadyThere: Boolean = false
    )

    // 미니맵이 매 프레임 요청하므로 상태가 바뀔 때까지 결과를 재사용한다.
    private var cacheKey = ""
    private var cacheValue: Advice? = null

    fun advise(s: GameState): Advice? {
        val chapter = MainStory.current(s) ?: return null
        val key = listOf(
            s.mainQuestStage, s.mainQuestFinished, s.level, s.birdCounts.size,
            s.bestStars.values.count { it >= 3 }, s.visited.size, s.region
        ).joinToString("|")
        if (key == cacheKey) return cacheValue
        val advice = compute(s, chapter)
        cacheKey = key
        cacheValue = advice
        return advice
    }

    private fun compute(s: GameState, chapter: MainStory.Chapter): Advice {
        // 목표 달성 — 보고는 보리 박사에게. 박사는 한 곳(광릉숲)에만 상주한다.
        if (chapter.isComplete(s)) {
            val prof = Regions.byId[NpcRoster.PROFESSOR_REGION] ?: Regions.ALL.first()
            val here = s.region == prof.id
            val spot = NpcRoster.professor.spot.label
            return Advice(
                prof.id, prof.name,
                if (here) "목표 달성 · ${spot}의 보리 박사에게 보고하세요"
                else "목표 달성 · ${prof.name}의 보리 박사에게 보고하세요",
                if (here) "보리 박사는 ${spot}에 있어요 — 카드 탭하면 자전거 길안내 시작"
                else NpcRoster.professorTravelHint,
                alreadyThere = here
            )
        }

        // 지역별 새 풀 (낮+밤 통합 — 밤새도 여기서 나온다)
        val pools = HashMap<String, Set<String>>()
        for (r in Regions.ALL) {
            pools[r.id] = (Birds.poolFor(r, false) + Birds.poolFor(r, true))
                .mapTo(LinkedHashSet()) { it.id }
        }
        val freshCount = pools.mapValues { (_, ids) ->
            ids.count { id -> (s.birdCounts[id] ?: 0) == 0 }
        }

        // 1) 컬렉션 미완료 — 남은 새가 나가는 지역
        val col = chapter.collectionDef()
        val missing = col?.species?.filterNot { s.hasBirdName(it) } ?: emptyList()
        val missingDefs = missing.mapNotNull { Birds.byName[it] }
        if (missing.isNotEmpty()) {
            var best = Regions.ALL.first()
            var bestHit = -1
            var bestFresh = -1
            for (r in Regions.ALL) {
                val hit = missingDefs.count { it.id in pools[r.id]!! }
                val fresh = freshCount[r.id]!!
                if (hit > bestHit || (hit == bestHit && fresh > bestFresh)) {
                    bestHit = hit; bestFresh = fresh; best = r
                }
            }
            val hitDefs = missingDefs.filter { it.id in pools[best.id]!! }
            if (hitDefs.isNotEmpty()) {
                val names = hitDefs.take(3).joinToString("·") { it.name } +
                    (if (hitDefs.size > 3) " 외" else "")
                val habitats = hitDefs.flatMap { it.habitats }.distinct()
                    .joinToString("·") { HabitatLabels[it] ?: it }
                return Advice(
                    best.id, best.name,
                    "남은 새 ${missing.size}종 중 ${hitDefs.size}종이 여기 · $names",
                    "(${habitats}) 구역에서 천천히 기다리면 새가 와요"
                )
            }
            // (모든 지역에 남은 새가 나지 않는 이례적인 경우 — 3단계로 내려가 기록용 지역 추천)
        }

        // 2) 방문 부족 — 가장 가까운 미방문 지역
        if (s.visited.size < chapter.minVisited) {
            val nearest = nearestUnvisited(s, freshCount)
            if (nearest != null) {
                return Advice(
                    nearest.id, nearest.name,
                    "방문 ${s.visited.size}/${chapter.minVisited}곳 · 가장 가까운 미방문 지역",
                    nearest.tip.ifBlank { "도착만 해도 방문 기록이 돼요" }
                )
            }
        }

        // 3) 레벨·라이퍼·3성 — 못 찍은 새가 많은 지역
        var best = Regions.ALL.first()
        var bestFresh = -1
        for (r in Regions.ALL) {
            val fresh = freshCount[r.id]!!
            if (fresh > bestFresh) { bestFresh = fresh; best = r }
        }
        if (bestFresh <= 0) {
            // 새를 이미 전부 본 극소수 — 지역 이동의 이득이 없다
            val cur = Regions.byId[s.region] ?: Regions.ALL.first()
            return Advice(
                cur.id, cur.name,
                "모든 새를 이미 봤어요 · 촬영으로만 경험치가 남아요",
                "어디서든 계속 촬영하면 레벨이 오릅니다",
                alreadyThere = true
            )
        }
        val goals = buildList {
            if (chapter.minLevel > 1) add("Lv.${s.level}/${chapter.minLevel}")
            if (chapter.minLifers > 0 && s.birdCounts.size < chapter.minLifers)
                add("라이퍼 ${s.birdCounts.size}/${chapter.minLifers}종")
            if (chapter.minThreeStars > 0)
                add("3성 ${s.bestStars.values.count { it >= 3 }}/${chapter.minThreeStars}종")
        }.joinToString(" · ")
        return Advice(
            best.id, best.name,
            "${if (goals.isEmpty()) "기록을 늘리기엔" else goals} · 여길 못 본 새가 ${bestFresh}종",
            best.tip.ifBlank { "낯선 새를 하나씩 기록하면 레벨이 빠르게 올라요" }
        )
    }

    /** 지역 루트 그래프(터널 연결)에서 가장 가까운 미방문 지역. 동점엔 새 기록 기회 많은 쪽. */
    private fun nearestUnvisited(s: GameState, freshCount: Map<String, Int>): RegionDef? {
        val dist = HashMap<String, Int>()
        dist[s.region] = 0
        val queue = ArrayDeque<String>()
        queue.add(s.region)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = dist[cur]!!
            for ((_, nid) in Regions.exits(cur)) {
                if (nid !in dist) {
                    dist[nid] = d + 1
                    queue.add(nid)
                }
            }
        }
        var best: RegionDef? = null
        var bestD = Int.MAX_VALUE
        var bestFresh = -1
        for (r in Regions.ALL) {
            if (r.id in s.visited) continue
            val d = dist[r.id] ?: continue
            val fresh = freshCount[r.id]!!
            if (d < bestD || (d == bestD && fresh > bestFresh)) {
                bestD = d; bestFresh = fresh; best = r
            }
        }
        return best
    }
}

fun GameState.hasBirdName(name: String): Boolean {
    val def = Birds.byName[name] ?: return false
    return (birdCounts[def.id] ?: 0) > 0
}
