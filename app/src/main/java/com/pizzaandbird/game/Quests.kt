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

    /** 실제 목록에 없는 별칭(삼색딱새, 흰머리오목눈이 등)은 표준 종명으로 바로잡았다. */
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
            "안전한 거리와 기상을 먼저 살피는 해상 탐조 도전")
    )
}

/** 메인 스토리: 보리 박사와 함께 '함께 사는 새 지도'의 빈 페이지를 채운다. */
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
            if (minLifers > 0) parts += "라이퍼 ${s.birdCounts.size}/$minLifers종"
            if (minVisited > 1) parts += "방문 ${s.visited.size}/$minVisited곳"
            collectionDef()?.let { parts += "${it.name} ${it.progress(s)}" }
            if (minThreeStars > 0) parts += "3성 기록 ${s.bestStars.values.count { it >= 3 }}/$minThreeStars종"
            return if (parts.isEmpty()) "보리 박사에게 말을 걸기" else parts.joinToString(" · ")
        }
    }

    val CHAPTERS = listOf(
        Chapter("프롤로그 · 빈 도감", "할머니의 낡은 수첩", rewardMoney = 5000, rewardExp = 25,
            intro = "이 수첩은 자네 할머니가 남긴 탐조 기록일세. 마지막 장에는 이렇게 쓰여 있지. ‘새를 안다는 건 함께 살 방법을 배우는 일.’ 나와 이 지도를 이어 보겠나?",
            complete = "좋아. 종수 경쟁이 아니라, 만난 장소와 마음까지 적는 거야. 첫 장은 우리 동네 새들로 시작하지."),
        Chapter("1장 · 창밖의 이웃", "이름을 알면 풍경이 달라진다", minLevel = 2, minLifers = 5,
            collection = "동네 첫 만남", rewardMoney = 12000, rewardExp = 55,
            intro = "멀리 떠나기 전에 매일 스쳐 가던 이웃부터 만나게. 참새와 까치도 오래 바라보면 저마다 다른 하루를 살고 있지.",
            complete = "자네 사진엔 흔한 새가 아니라 ‘이웃’이 찍혔군. 그런데 수첩 사이에서 나무 두드리는 소리를 적은 쪽지가 나왔네."),
        Chapter("2장 · 숲의 모스 부호", "딱다구리 세 종의 두드림", minLevel = 4,
            collection = "딱다구리 기본 3종", rewardMoney = 30000, rewardExp = 110,
            intro = "쇠딱다구리, 오색딱다구리, 청딱다구리. 세 목수의 크기와 무늬를 비교해 보게. 둥지 가까이 가지 말고 떨어진 나무에서 기다리는 걸세.",
            complete = "두드림은 숲이 살아 있다는 전보였어. 하지만 숲만으로는 지도를 완성할 수 없지. 물길을 따라가 보세."),
        Chapter("3장 · 물길의 기억", "강과 습지가 이어 주는 길", minLevel = 7, minLifers = 30, minVisited = 5,
            collection = "물총새과 4종", rewardMoney = 65000, rewardExp = 190,
            intro = "물총새과 네 종을 찾아 물길의 색을 기록하게. 청도요는 이름과 달리 도요류라는 것도 기억하고. 정확한 이름이 정확한 보호의 시작이니까.",
            complete = "서울의 하천과 남쪽 계곡이 새들의 길로 이어졌군. 수첩 여백에 할머니가 ‘계절은 날개를 타고 온다’고 적었네."),
        Chapter("4장 · 계절의 날개", "한반도를 건너는 여행자들", minLevel = 11, minLifers = 70, minVisited = 10,
            collection = "겨울 오리 삼색", minThreeStars = 5, rewardMoney = 120000, rewardExp = 300,
            intro = "가창오리 무리는 밤하늘을 강처럼 흐르지. 원앙과 청머리오리까지 기록하고, 열 곳의 풍경을 이어 철새의 길을 그려 보게.",
            complete = "지도 위 점들이 하나의 이동 경로가 됐어. 이제 비슷해 보이는 작은 새도 서두르지 않고 읽을 눈이 필요하네."),
        Chapter("5장 · 갯벌의 쉼표", "작은 차이를 읽는 눈", minLevel = 15, minLifers = 110, minVisited = 16,
            collection = "도요 입문 4종", minThreeStars = 12, rewardMoney = 220000, rewardExp = 440,
            intro = "도요물떼새에게 갯벌은 긴 여행문의 쉼표일세. 부리와 다리만 보지 말고 먹이 행동과 무리 간 거리까지 천천히 기록하게.",
            complete = "이젠 자네가 내 사진의 동정 오류를 찾아내겠군. 그러나 귀한 새를 찾는 것보다 더 어려운 건 그 자리를 지키는 일이야."),
        Chapter("6장 · 지켜 보는 사람", "발견보다 먼저 배워야 할 거리", minLevel = 20, minLifers = 160, minVisited = 24,
            collection = "보전의 깃발", minThreeStars = 25, rewardMoney = 400000, rewardExp = 650,
            intro = "저어새와 노랑부리백로의 사진 한 장보다 번식지의 평온이 먼저일세. 위치를 함부로 퍼뜨리지 않고, 길을 벗어나지 않는 관찰자가 되어 주게.",
            complete = "좋은 기록자는 새를 소유하지 않아. 물러설 때를 아는 사람이야. 이제 수첩의 마지막 빈 장만 남았네."),
        Chapter("마지막 장 · 함께 사는 지도", "전설의 탐조가가 남길 문장", minLevel = Progression.MAX_LEVEL,
            rewardMoney = 1000000, rewardExp = 0,
            intro = "마지막 장은 내가 정해 줄 수 없네. 지금까지 만난 풍경과 새를 되새기며, 만렙에 오른 자네가 직접 답을 써 주게.",
            complete = "‘우리가 새를 바라보는 동안, 새도 살아갈 내일을 얻기를.’ 훌륭하군. 메인 이야기는 여기서 멈추지만, 계절과 새의 이야기는 끝나지 않아. 의뢰와 도장 깨기는 언제든 계속하게.")
    )

    fun current(s: GameState): Chapter? = if (s.mainQuestFinished) null else CHAPTERS.getOrNull(s.mainQuestStage)
}

fun GameState.hasBirdName(name: String): Boolean {
    val def = Birds.byName[name] ?: return false
    return (birdCounts[def.id] ?: 0) > 0
}
