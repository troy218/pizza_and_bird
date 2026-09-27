package com.pizzaandbird.game

/**
 * NPC 캐스팅 북 — **「한 사람은 한 장소에만」**.
 *
 * 예전에는 5명의 NPC가 **모든 지역 광장**에 똑같이 서 있었다(같은 얼굴·같은 자리·같은 대사).
 * 이제 사람은 지역과 함께 산다.
 *
 *  - 지역마다 **고유 캐릭터 1명 + 이웃 주민 1명** — 이름·별명·외형·자리·대사가 전부 다르다.
 *  - 기능 NPC도 한 집에 산다: **보리 박사**는 광릉숲 숲속 쉼터 하나, **카메라샵 사장**은 12개 도시 골목 각각 하나.
 *    박사와 가게는 퀘스트 카드/상태 창의 🚲 버튼으로 찾아간다 (`QuestNavigation.startPersonTrip`).
 *  - 자리는 좌표 하드코딩이 아니라 **그 지역의 랜드마크**로 잡는다 (`NpcSpot`).
 *    지도를 만들 때 실제 지형(호수 데크·갈대밭·갯벌·시장 골목…)을 찾아 세우고,
 *    그 자리가 막혔거나 갈 수 없으면 광장으로 옮긴다 (`MapBuilder.placeCast`).
 *
 * 사람을 추가하려면 아래 `CAST` 에 한 줄만 넣으면 된다 — 아트·이름표·대화·맵 배치에 자동 반영.
 */

/**
 * NPC가 서 있는 자리. 값 자체가 좌표가 아니라 **어떤 자리인지**를 말한다.
 * `MapBuilder` 가 지역 지형을 보고 실제 타일을 찾는다(못 찾으면 광장으로 대체).
 */
enum class NpcSpot(val label: String) {
    PLAZA_COMPASS("광장 나침반 문양 옆"),
    PLAZA_NORTH("광장 북쪽"),
    PLAZA_SOUTH("광장 남쪽 벤치 곁"),
    PLAZA_WEST("광장 서쪽 입구"),
    PLAZA_EAST("광장 동쪽 입구"),
    LAKE_DECK("호숫가 전망 데크"),
    LAKE_SHORE("호수 갈대 언저리"),
    RIVER_BANK("강가 여울"),
    REED_HIDE("갈대밭 관찰 자리"),
    TIDAL_FLAT("갯벌 둑길"),
    BEACH("해변 모래둑"),
    FOREST_REST("숲속 쉼터"),
    GROVE("나무 그늘"),
    FIELD_EDGE("논둑"),
    MARKET("건물 앞 골목"),
    AVENUE("가로수 아래"),
    TRAILHEAD("산길 들머리")
}

/**
 * 사람 한 명의 옷차림 — 같은 몸(바디)이라도 배색과 소품이 다르면 완전히 다른 사람이 된다.
 * 색은 `CharacterArt.Pal` 로 그대로 들어가고, cap/vest/scarf 는 탐조 장비와 같은 파츠를 쓴다.
 */
class NpcLook(
    val hair: Int,
    val top: Int,
    val top2: Int,
    val pants: Int,
    val pack: Int,
    val glasses: Boolean = false,
    val apron: Boolean = false,
    val cane: Boolean = false,
    val small: Boolean = false,
    val longHair: Boolean = false,
    val cap: Int? = null,
    val vest: Int? = null,
    val scarf: Int? = null
)

/** 사람 한 명. `regionId` 가 곧 "이 사람이 사는 곳"이라 다른 지역에는 절대 나타나지 않는다. */
class NpcPerson(
    val id: String,
    val name: String,
    /** 이름표에 작게 함께 뜨는 한 줄 (직함·동네 별명) */
    val title: String,
    val regionId: String,
    val kind: NpcKind,
    val look: NpcLook,
    val spot: NpcSpot,
    /** 이웃 주민인가 — 주민은 지역 소개(`RegionDef.villager`)와 장별 예절 이야기를 함께 한다 */
    val resident: Boolean = false,
    val lines: List<String>,
    /** 머리 위 말풍선 이모트 (null 이면 `NpcKind` 기본값) */
    val emotes: List<String>? = null
) {
    /** 이 사람이 하는 일 — 대화창 분기에 쓴다 */
    val isQuestGiver: Boolean get() = kind == NpcKind.PROFESSOR
    val isShop: Boolean get() = kind == NpcKind.SHOP
}

/** 옷장 — 지역 기후·직업에 어울리는 배색 16벌. 사람마다 하나씩 입힌다. */
private object Looks {
    private fun look(
        hair: Long, top: Long, top2: Long, pants: Long, pack: Long,
        glasses: Boolean = false, apron: Boolean = false, cane: Boolean = false,
        longHair: Boolean = false, cap: Long? = null, vest: Long? = null, scarf: Long? = null
    ) = NpcLook(
        hair.toInt(), top.toInt(), top2.toInt(), pants.toInt(), pack.toInt(),
        glasses, apron, cane, false, longHair,
        cap?.toInt(), vest?.toInt(), scarf?.toInt()
    )

    val SEA_SALT = look(0xFF3A3F4A, 0xFFEAF3F6, 0xFFCBD9E0, 0xFF4A6FA5, 0xFF8A6A4F, cap = 0xFF3F6FB0)
    val REED = look(0xFF5B4632, 0xFFC9B47E, 0xFFA99460, 0xFF6B6A4F, 0xFF8A6A4F, vest = 0xFF7A8A5A)
    val CAMELLIA = look(0xFF2E2620, 0xFFE2574C, 0xFFB23F44, 0xFF3F4A6F, 0xFFF2B63C, scarf = 0xFFE2574C)
    val PINE = look(0xFF3F3A2E, 0xFF6FAE57, 0xFF4F7D3F, 0xFF5D6470, 0xFF8A5A33, vest = 0xFF4F7D3F)
    val MIST = look(0xFFCFD2D8, 0xFFDCE6EC, 0xFFB9C6CE, 0xFF7A8894, 0xFF9AA3AD, scarf = 0xFFEAF3F6)
    val EARTH = look(0xFF4A2F1D, 0xFFC89B6A, 0xFFA97C50, 0xFF6B5A48, 0xFF4F7D3F, apron = true)
    val DUSK = look(0xFF2E2620, 0xFFC3A3E8, 0xFF9F7FC8, 0xFF4A6FA5, 0xFF8A5A33)
    val SUNSET = look(0xFF5B3A29, 0xFFF2A15C, 0xFFD07F3E, 0xFF6B4F35, 0xFFE2574C, cap = 0xFFF2B63C)
    val BLUE_HAZE = look(0xFF2A2F3A, 0xFF6FA8C8, 0xFF4F87A8, 0xFF3A4A5A, 0xFFCFD2D8)
    val BUCKWHEAT = look(0xFFE8E4DC, 0xFFF5F2EA, 0xFFD8D2C4, 0xFF8A7360, 0xFFC89B6A, longHair = true, cane = true)
    val HYDRANGEA = look(0xFF3A2E2A, 0xFF8FB8E8, 0xFF6F97C8, 0xFF5D6470, 0xFFB23F44)
    val TANGERINE = look(0xFF4A3728, 0xFFF2B63C, 0xFFD09A2C, 0xFF4F7D3F, 0xFF8A5A33, cap = 0xFFE2853C)
    val INK = look(0xFF26221E, 0xFF5D6470, 0xFF454B56, 0xFF33383F, 0xFF8A7360, glasses = true)
    val CRIMSON = look(0xFF3A2A22, 0xFFD05A6A, 0xFFA8404F, 0xFF4A4A6A, 0xFFF2B63C, longHair = true)
    val MOSS = look(0xFF4A4A33, 0xFF7A9E4F, 0xFF5C7C3A, 0xFF6B5A48, 0xFF9AA3AD, vest = 0xFF5C7C3A)
    val SNOW = look(0xFFEDEAE2, 0xFFF8F6F0, 0xFFDCD8CE, 0xFF8A93A0, 0xFFB23F44, cane = true, scarf = 0xFFB23F44)
    val PROFESSOR = look(0xFFCFD2D8, 0xFFF5F2EA, 0xFFD8D2C4, 0xFF5D6470, 0xFF9AA3AD, glasses = true)
    // (본점 사장 남기택의 옷차림은 `CameraShops` 에 있다 — 진열대와 사람이 한 장에 담기도록)
}

object NpcRoster {

    /** 보리 박사가 상주하는 곳 — 메인 이야기 보고·사진 의뢰는 여기까지 와야 한다 */
    const val PROFESSOR_REGION = "gwangneung"

    /** 카메라샵 **본점**이 있는 곳 — 자전거·장식 코너는 본점에서만 판다 (`CameraShops`) */
    const val SHOP_REGION = "seoul"

    private fun local(
        region: String, name: String, title: String, kind: NpcKind, look: NpcLook, spot: NpcSpot,
        emotes: List<String>? = null, vararg lines: String
    ) = NpcPerson("$region:local", name, title, region, kind, look, spot, false, lines.toList(), emotes)

    private fun resident(
        region: String, name: String, title: String, kind: NpcKind, look: NpcLook, spot: NpcSpot,
        emotes: List<String>? = null, vararg lines: String
    ) = NpcPerson("$region:resident", name, title, region, kind, look, spot, true, lines.toList(), emotes)

    /** 도시마다 한 명씩 서는 카메라샵 사장들 — Cast 순서가 아니라 **출생지 목록**이다 */
    private val SHOP_CAST: List<NpcPerson> = CameraShops.ALL.map { shop ->
        NpcPerson(
            "shop:" + shop.regionId, shop.keeper, shop.keeperTitle, shop.regionId,
            NpcKind.SHOP, shop.look, shop.spot,
            false, shop.lines, listOf("📷", "✨", "💰")
        )
    }

    /**
     * 등장 인물 전체. 지역 순서는 `Regions.ALL` 과 맞췄다.
     * 한 지역 = 고유 캐릭터 1 + 이웃 주민 1 (+ 보리 박사는 광릉숲, 카메라샵 사장은 12개 도시).
     */
    private val CAST: List<NpcPerson> = listOf(
        // ===================== 도시 =====================
        local("seoul", "주하", "한강 라이더", NpcKind.VILLAGER, Looks.SUNSET, NpcSpot.RIVER_BANK,
            listOf("🚲", "♪", "🐦"),
            "한강엔 자전거길 말고 새 길도 따로 있어요. 갈대 쪽으로 난 흙길요.",
            "겨울에 밤섬 쪽 오리떼 봤어요? 페달이 저절로 멈춰요.",
            "달리면서 보면 다 날아가 버려요. 새는 서서 봐야 해요…"),
        resident("seoul", "말순", "망원동 이웃", NpcKind.ELDER, Looks.BUCKWHEAT, NpcSpot.PLAZA_SOUTH,
            null,
            "이 동네 살면서 알았어. 콘크리트 같아도 사계절 새는 다 있더라고.",
            "한강이 가까워서 물새도 곧잘 올라온다네.",
            "사진 찍을 때 베란다 쪽은 살짝 피해 주게. 이웃들이 놀라."),

        local("incheon", "해순", "소래포구 젓갈", NpcKind.VILLAGER, Looks.SEA_SALT, NpcSpot.TIDAL_FLAT,
            listOf("🦐", "✨", "…"),
            "젓갈은 썰물 때가 제일 좋아. 새들도 그때 밥 먹으러 오거든.",
            "여기 갯벌에 저어새가 와. 부리가 주걱 같아서 멀리서도 알아봐.",
            "비린내 난다고 얼굴 찌푸리지 말게. 이게 다 바다 냄새야."),
        resident("incheon", "기섭", "공항동 이웃", NpcKind.ELDER, Looks.INK, NpcSpot.MARKET,
            null,
            "비행기 뜨기 전에 이 길로 철새가 먼저 떴어.",
            "물이 차오르면 새가 발밑까지 밀려와. 그때가 기회야.",
            "갯벌은 밟는 데가 아니라 들여다보는 데여."),

        local("chuncheon", "노을", "의암호 카누", NpcKind.VILLAGER, Looks.MIST, NpcSpot.LAKE_DECK,
            listOf("🛶", "🌫", "♪"),
            "아침 물안개 낄 때 노를 멈추면 백로가 먼저 인사해요.",
            "호수 한가운데로 들어가면 소리가 없어져요. 새 소리만 남고…",
            "카누는 새를 안 놀라게 해서 좋아요. 물결 소리밖에 안 나거든요."),
        resident("chuncheon", "순영", "닭갈비 골목 꼬마", NpcKind.KID, Looks.CAMELLIA, NpcSpot.PLAZA_SOUTH,
            listOf("♪", "🦋", "✨"),
            "겨울에 호수에 새가 잔뜩 와! 나 매일 세는데 자꾸 빼먹어.",
            "닭갈비 먹고 나오면 참새가 기다리고 있어. 진짜라니까!",
            "엄마가 그랬어. 옛날엔 이 호수에 고니가 더 많았대."),

        local("gangneung", "다인", "경포호 지킴이", NpcKind.VILLAGER, Looks.BLUE_HAZE, NpcSpot.LAKE_SHORE,
            null,
            "경포호는 바다랑 붙어 있어요. 민물새랑 바닷새가 같이 보입니다.",
            "고니는 목이 길어서 물속 자갈까지 집어 먹어요. 소리도 크고요.",
            "호수 둘레를 한 바퀴 걸으면 계절이 바뀌는 게 보여요."),
        resident("gangneung", "보경", "안목 해변 카페", NpcKind.VILLAGER, Looks.CRIMSON, NpcSpot.BEACH,
            listOf("☕", "♪", "…"),
            "커피 한 잔 들고 바다 보는 게 여기 사는 재미죠.",
            "겨울엔 해변에 갈매기가 엄청나요. 과자는 뺏기지 마세요.",
            "소나무 뒤로 해가 질 때가 제일 예뻐요. 카메라 준비하세요."),

        local("sokcho", "준호", "설악 등산 안내", NpcKind.VILLAGER, Looks.MOSS, NpcSpot.TRAILHEAD,
            null,
            "산새는 소리부터 들어요. 보이지 않아도 거기 있어요.",
            "오색딱다구리는 계곡 쪽 나무를 좋아합니다. 붉은 배가 보여요.",
            "정상까지 안 가도 돼요. 들머리 30분이 제일 볼거리 많아요."),
        resident("sokcho", "옥녀", "청초호 어르신", NpcKind.ELDER, Looks.SNOW, NpcSpot.BEACH,
            null,
            "이 바다는 겨울에 더 시끄러워. 새가 많아서 말이야.",
            "호수와 바다가 붙어 있으니 물새가 쉬어 가기 좋지.",
            "바람 세게 부는 날은 목도리 꼭 하게."),

        local("daejeon", "유진", "갑천 해설사", NpcKind.VILLAGER, Looks.HYDRANGEA, NpcSpot.RIVER_BANK,
            listOf("📖", "🐦", "♪"),
            "갑천은 도시 한가운데를 흐르는데도 물총새가 살아요.",
            "여울과 소가 번갈아 나오는 구간이 제일 재밌습니다.",
            "해설을 원하시면 말씀하세요. 새 이름은 아는 만큼 보여요."),
        resident("daejeon", "복희", "중앙시장 떡집", NpcKind.ELDER, Looks.EARTH, NpcSpot.MARKET,
            listOf("…", "🍵", "✨"),
            "시장 골목에도 참새가 잔뜩 살아. 사람 사는 데는 다 있어.",
            "떡 하나 들고 하천변 걸어 봐. 기분 좋아져.",
            "요즘 젊은이들 카메라가 참 크더라. 새가 놀라지 않나 몰라."),

        local("jeonju", "서준", "전주천 사진가", NpcKind.VILLAGER, Looks.INK, NpcSpot.RIVER_BANK,
            listOf("📷", "…", "🐦"),
            "전주천은 도심 하천인데도 물새가 꽤 들어와요.",
            "사진은 다가가는 게 아니라 기다리는 겁니다… 삼각이 먼저죠.",
            "한옥 지붕 위로 까치가 지나가는 장면, 그게 전주예요."),
        resident("jeonju", "갑순", "한옥골목 이웃", NpcKind.ELDER, Looks.CAMELLIA, NpcSpot.GROVE,
            null,
            "골목 담장 위에 참새가 줄지어 앉는 걸 좋아했어.",
            "기와 사이로 제비가 들던 시절이 있었지…",
            "천천히 걷게. 이 동네는 빨리 보면 남는 게 없어."),

        local("daegu", "태양", "팔공산 안내원", NpcKind.VILLAGER, Looks.PINE, NpcSpot.TRAILHEAD,
            null,
            "팔공산 자락은 참새보다 박새가 많아요. 겨울엔 혼성군으로 다녀요.",
            "능선보다 계곡이 낫습니다. 물이 있으면 새가 있어요.",
            "더울 땐 아침 일찍 오르세요. 새도 사람도 그때 움직여요."),
        resident("daegu", "분희", "서문시장 골목대장", NpcKind.KID, Looks.TANGERINE, NpcSpot.MARKET,
            listOf("!", "", "✨"),
            "시장 지붕 밑에 제비집이 몇 개인지 내가 다 알아!",
            "국밥 먹고 가! 배고프면 사진도 안 잘 나온다?",
            "여름엔 새도 그늘에 숨어. 나랑 같이 찾자!"),

        local("gwangju", "산들", "무등산 관찰원", NpcKind.VILLAGER, Looks.MOSS, NpcSpot.GROVE,
            null,
            "무등산은 소리와 깃으로 찾아요. 팔색조는 보여 주기 전에 울어요.",
            "여름 숲은 둥지가 많아요. 나무 구멍은 들여다보지 않습니다.",
            "정상 욕심 버리고 능선에서 한 시간 서 있어 보세요."),
        resident("gwangju", "영철", "영산강변 주민", NpcKind.ELDER, Looks.EARTH, NpcSpot.LAKE_SHORE,
            null,
            "강변에 살면 계절을 새가 알려줘.",
            "물이 빠진 자리에서 도요새가 바쁘게 뛰어다녀.",
            "비가 온 다음 날이 제일 맑아. 멀리까지 보여."),

        local("ulsan", "푸름", "태화강 지킴이", NpcKind.VILLAGER, Looks.PINE, NpcSpot.RIVER_BANK,
            listOf("🎋", "🐦", "♪"),
            "태화강 대숲엔 여름에 백로가 몇 천 마리 들어와요.",
            "죽순을 밟지 않는 게 여기 약속입니다. 새도 같은 약속이고요.",
            "저녁에 대숲으로 들어가는 흰줄박이오목눈이 떼를 보세요."),
        resident("ulsan", "영순", "방어진 어민", NpcKind.ELDER, Looks.SEA_SALT, NpcSpot.BEACH,
            null,
            "고기 따라 새가 오고, 새 따라 우리가 오지.",
            "방파제 끝에 갈매기가 줄 서 있는 게 보기 좋아.",
            "파도 소리 큰 날은 새가 안 보여. 귀만 시끄럽지."),

        local("busan", "알록", "감천골목 화가", NpcKind.VILLAGER, Looks.CRIMSON, NpcSpot.AVENUE,
            listOf("🎨", "♪", "…"),
            "골목 끝에서 바다가 보여요. 그 위에 갈매기 한 마리, 그게 그림이죠.",
            "색은 새가 먼저 써요. 나는 따라 그릴 뿐이고…",
            "계단이 많으니 편한 신발 신어요. 새도 걸어 다니는 건 아니지만."),
        resident("busan", "옥분", "자갈치 골목대장", NpcKind.KID, Looks.TANGERINE, NpcSpot.TIDAL_FLAT,
            listOf("!", "🐦", ""),
            "겨울에 하구 가면 오리떼가 진짜 많아! 내가 아는 자리 알려줄까?",
            "시장통 직박구리는 나랑 친해. 맨날 먼저 인사해.",
            "부산 바다는 바람이 세! 모자 날아가면 내가 잡아줄게."),

        local("jeju", "바람", "올레길 지킴이", NpcKind.VILLAGER, Looks.PINE, NpcSpot.BEACH,
            listOf("🌬", "♪", "🐦"),
            "돌담 위에 앉는 동박새를 보세요. 동백 필 때가 제일 좋아요.",
            "올레길은 화살표를 따라가면 돼요. 새는 어디든 있어요.",
            "제주 바람이 세면 새들도 낮게 날아요. 그만큼 잘 보입니다."),
        resident("jeju", "고운", "돌담마을 이웃", NpcKind.ELDER, Looks.MIST, NpcSpot.PLAZA_SOUTH,
            null,
            "이 섬엔 사철 새가 있어. 겨울엔 바다에서 오는 손님도 많고.",
            "밭담 사이로 직박구리가 들락날락해.",
            "천천히 걷게. 제주에선 빨리 가는 게 손해여."),

        // ===================== 갯벌 · 습지 =====================
        local("ganghwa", "두레", "저어새 지킴이", NpcKind.VILLAGER, Looks.REED, NpcSpot.TIDAL_FLAT,
            listOf("🥄", "🐦", "…"),
            "저어새는 부리를 물속에 넣고 저으면서 먹어요. 그래서 이름이 이래요.",
            "전 세계 저어새의 상당수가 이 앞 갯벌에서 겨울을 납니다.",
            "썰물에 드러난 개펄을 따라 걸으면 도요새가 앞서 달려요."),
        resident("ganghwa", "순덕", "인삼밭 아주머니", NpcKind.ELDER, Looks.EARTH, NpcSpot.FIELD_EDGE,
            null,
            "밭일하다 보면 기러기 떼가 북쪽에서 넘어와.",
            "흙이 좋으면 새도 알아. 지렁이가 많거든.",
            "바닷바람에 인삼이 단단해져. 사람도 마찬가지고."),

        local("cheorwon", "설원", "두루미 지킴이", NpcKind.VILLAGER, Looks.SNOW, NpcSpot.FIELD_EDGE,
            listOf("🕊", "…", "❄"),
            "겨울 논에 물을 조금 남겨 두면 두루미가 잠자리로 씁니다.",
            "아침에 논에서 올라오는 물안개 사이로 나는 모습이 제일 좋아요.",
            "멀리서 보세요. 두루미는 사람이 가까이 오면 밥을 못 먹어요."),
        resident("cheorwon", "만석", "철원 농부", NpcKind.ELDER, Looks.MOSS, NpcSpot.LAKE_SHORE,
            null,
            "벼 베고 나면 기러기가 논에 내려앉아. 우리도 그때 쉬지.",
            "저수지 얼면 오리들이 물 열린 데로 몰려.",
            "평야는 바람이 세. 모자 단단히 눌러 써."),

        local("eulsukdo", "고니", "을숙도 탐조대", NpcKind.VILLAGER, Looks.REED, NpcSpot.REED_HIDE,
            null,
            "하구는 민물과 바닷물이 섞여서 먹을 게 많아요. 새가 몰리는 이유죠.",
            "탐조대에선 앉아서 보세요. 서 있으면 새가 알아챕니다.",
            "갈대 사이로 큰고니가 목을 빼는 순간이 있어요. 그게 명장면입니다."),
        resident("eulsukdo", "길만", "하구 어민", NpcKind.ELDER, Looks.SEA_SALT, NpcSpot.TIDAL_FLAT,
            null,
            "물이 빠지면 우리도 쉬고 새도 쉰다.",
            "하구 모래톱엔 발자국만 남기고 가야 해.",
            "재첩 잡다 보면 왜가리가 옆에 서 있어. 기다리는 거지."),

        local("gongneung", "여울", "공릉천 지킴이", NpcKind.VILLAGER, Looks.BLUE_HAZE, NpcSpot.RIVER_BANK,
            listOf("🌿", "🐦", "♪"),
            "공릉천은 좁은데 새가 참 많아요. 갈대 언저리를 따라 걸으면 돼요.",
            "겨울에 독수리가 전봇대 위에 앉는 걸 봤어요. 진짜예요.",
            "천변 논은 겨울에 물을 빼요. 그래도 도요새는 남아요."),
        resident("gongneung", "재호", "천변 산책 이웃", NpcKind.ELDER, Looks.MIST, NpcSpot.AVENUE,
            null,
            "아침마다 여기 걸어요. 새가 날 알아보는 것 같아.",
            "물빛이 좋은 날엔 왜가리가 오래 서 있지.",
            "천천히 걷는 게 이 동네 속도야."),

        // ===================== 광릉숲 — 보리 박사의 동네 =====================
        NpcPerson(
            "professor", "보리 박사", "조류학자", PROFESSOR_REGION,
            NpcKind.PROFESSOR, Looks.PROFESSOR, NpcSpot.FOREST_REST,
            false,
            listOf(
                "자네 할머니도 이 쉼터에서 같은 자리를 좋아했네.",
                "수첩은 기록이지 자랑이 아니야. 날짜와 날씨를 꼭 적게.",
                "멀리 갈 필요 없네. 오늘 창밖을 스친 새부터 이름을 물어보게."
            ),
            listOf("🔍", "📖", "🐦")
        ),
        local("gwangneung", "솔잎", "광릉숲 해설사", NpcKind.VILLAGER, Looks.MOSS, NpcSpot.GROVE,
            null,
            "광릉숲은 오백 년을 지켜 온 숲이에요. 크낙새가 살던 자리죠.",
            "숲에서는 소리를 낮추면 새가 먼저 다가와요.",
            "박사님은 저쪽 쉼터에서 늘 수첩을 펴 놓고 계세요."),
        resident("gwangneung", "만복", "숲마을 이장", NpcKind.ELDER, Looks.EARTH, NpcSpot.PLAZA_NORTH,
            null,
            "마을에 박사가 눌러앉으면서 새 보러 오는 사람이 늘었어.",
            "숲 가장자리 논밭엔 겨울에 멧새 떼가 와.",
            "길 잃으면 광장으로 돌아오게. 여기 사람들은 다 알아."),

        local("songdo", "나루", "송도 갯벌 지킴이", NpcKind.VILLAGER, Looks.BLUE_HAZE, NpcSpot.TIDAL_FLAT,
            listOf("🔭", "🐦", "…"),
            "신도시 옆에 남은 갯벌이에요. 그래서 더 소중하죠.",
            "물때를 보고 오세요. 만조 두 시간 전이 제일 좋아요.",
            "도요새는 다리가 짧아서 얕은 물만 골라 다녀요."),
        resident("songdo", "하늘", "신도시 이웃", NpcKind.KID, Looks.TANGERINE, NpcSpot.MARKET,
            listOf("♪", "!", "😆"),
            "이사 온 지 얼마 안 됐는데 갯벌에 새가 진짜 많아요!",
            "학교에서 탐조 동아리 만들려고요. 카메라 있어요?",
            "저기요, 저 새 이름 알아요? 어… 까먹었어요."),

        local("sihwa", "물결", "시화호 관찰원", NpcKind.VILLAGER, Looks.MIST, NpcSpot.REED_HIDE,
            null,
            "시화호는 갈대가 넓어요. 갈대 끝을 보면 새가 먼저 움직입니다.",
            "방조제 안쪽이라 바람이 덜해요. 겨울 관찰에 딱입니다.",
            "물닭은 갈대 뿌리 쪽을 좋아해요. 빨간 부리가 보여요."),
        resident("sihwa", "만호", "방조제 낚시꾼", NpcKind.ELDER, Looks.INK, NpcSpot.AVENUE,
            listOf("🎣", "…", "💤"),
            "고기는 안 물려도 새는 계속 지나가. 그래서 앉아 있지.",
            "낚싯대 옆에 왜가리가 서면 그날은 같이 기다리는 거야.",
            "바람 불면 찌가 흔들려. 인생도 그렇고."),

        local("hwaseong", "갯버들", "화성습지 지킴이", NpcKind.VILLAGER, Looks.REED, NpcSpot.REED_HIDE,
            null,
            "화성습지는 도요물떼새의 중간 기착지예요. 쉬어 가는 자리죠.",
            "이동철엔 하루가 다릅니다. 어제 없던 새가 오늘 있어요.",
            "갯벌에 발자국 남기는 것도 조심해야 해요. 밥상이거든요."),
        resident("hwaseong", "영팔", "궁평항 어민", NpcKind.ELDER, Looks.SEA_SALT, NpcSpot.BEACH,
            null,
            "항구에 갈매기가 많지. 그놈들이 제일 배짱이 좋아.",
            "소나무 방풍림 뒤로 해가 지는 게 볼만해.",
            "물때 보고 움직여야 해. 새도 우리도 똑같지."),

        local("ansan", "갈잎", "갈대습지 안내", NpcKind.VILLAGER, Looks.PINE, NpcSpot.REED_HIDE,
            null,
            "도시 한복판 갈대습지예요. 여기서 물총새를 봤다는 사람도 있어요.",
            "관찰로는 데크로만 다니세요. 갈대 밑이 둥지일 수 있어요.",
            "가을에 갈대가 노래지면 사진이 정말 잘 나옵니다."),
        resident("ansan", "민혁", "안산 이웃", NpcKind.KID, Looks.HYDRANGEA, NpcSpot.MARKET,
            listOf("!", "😆", "🦋"),
            "학교 체험학습으로 갈대습지 갔다가 새에 빠졌어요!",
            "저 쌍안경 빌려 쓰다가 나중에 제 걸 샀어요.",
            "여기서 찍은 사진으로 학교 자랑할 거예요!"),

        local("maehyang", "소라", "매향리 화가", NpcKind.VILLAGER, Looks.CRIMSON, NpcSpot.BEACH,
            listOf("🎨", "🐚", "♪"),
            "오래 아팠던 바닷가예요. 지금은 새가 먼저 돌아왔어요.",
            "갯벌 색이 하루에 몇 번씩 바뀌어요. 다 그리고 싶어요.",
            "여기선 큰 소리 내지 않아요. 조용한 게 예의 같아서…"),
        resident("maehyang", "순례", "매향리 어르신", NpcKind.ELDER, Looks.BUCKWHEAT, NpcSpot.PLAZA_SOUTH,
            null,
            "이 바다를 오래 봤어. 새가 돌아오니 마음이 놓여.",
            "썰물 때 뻘이 반짝이는 게 아직도 예뻐.",
            "바람 차다. 겉옷 여미고 다녀."),

        local("junam", "연잎", "주남 탐조대", NpcKind.VILLAGER, Looks.REED, NpcSpot.LAKE_DECK,
            null,
            "주남은 겨울 재두루미와 기러기의 마을이에요.",
            "탐조대에서는 앉아서, 그리고 천천히 둘러보세요.",
            "논과 저수지가 붙어 있어서 밥상과 잠자리가 한꺼번에 해결돼요."),
        resident("junam", "병수", "동읍 농부", NpcKind.ELDER, Looks.MOSS, NpcSpot.FIELD_EDGE,
            null,
            "겨울 논에 새가 내려앉으면 마음이 느긋해져.",
            "벼 그루터기 사이에 떨어진 낟알이 새 밥이야.",
            "물이 얼면 오리들이 한쪽으로 몰려. 거기만 봐도 돼."),

        local("suncheon", "갈숲", "순천만 갈대지기", NpcKind.VILLAGER, Looks.REED, NpcSpot.REED_HIDE,
            listOf("🌾", "…", "🐦"),
            "순천만 갈대는 키가 커요. 그래서 흑두루미가 숨기 좋죠.",
            "해 질 때 갈대가 붉어지는 순간을 기다려 보세요.",
            "용산 전망대까지는 천천히 걸어야 새가 놀라지 않아요."),
        resident("suncheon", "덕순", "뻘배 어민", NpcKind.ELDER, Looks.SEA_SALT, NpcSpot.TIDAL_FLAT,
            null,
            "뻘배는 한 발로 밀고 다녀. 새랑 같은 속도여.",
            "짱뚱어가 뛰면 게도 뛰고, 새도 뛰지.",
            "갯벌은 미끄러워. 넘어져도 웃고 일어나면 돼."),

        local("geumgang", "가창", "금강 하구 관찰원", NpcKind.VILLAGER, Looks.BLUE_HAZE, NpcSpot.REED_HIDE,
            listOf("🔭", "🦆", "…"),
            "겨울에 가창오리 떼가 하늘을 덮어요. 해 질 무렵이 최고입니다.",
            "하굿둑 안쪽은 민물, 바깥은 바닷물. 둘 다 새가 옵니다.",
            "무리가 한 번에 뜨는 그 순간을 기다리는 게 하구 탐조예요."),
        resident("geumgang", "판수", "군산항 어민", NpcKind.ELDER, Looks.INK, NpcSpot.TIDAL_FLAT,
            null,
            "항구엔 새가 많아. 그물 옆에서 같이 기다리지.",
            "겨울 바람이 매서우니 장갑 끼고 봐.",
            "물이 빠진 자리에 발자국만 남기고 가세."),

        local("gochang", "복순", "바지락 아주머니", NpcKind.ELDER, Looks.TANGERINE, NpcSpot.TIDAL_FLAT,
            listOf("🐚", "✨", "…"),
            "바지락 캐다 보면 도요새가 옆에 와서 서 있어. 기다리는 거지.",
            "갯벌에 호미 자국 내는 것도 조심해야 해. 다 밥상이니까.",
            "썰물 시간 맞춰 오게. 물 들어오면 아무것도 안 보여."),
        resident("gochang", "용철", "고창 읍내 이발사", NpcKind.ELDER, Looks.INK, NpcSpot.PLAZA_WEST,
            null,
            "읍내에서 갯벌까지 자전거로 삼십 분이야.",
            "머리 깎는 손님 중에 탐조하는 사람이 늘었어.",
            "이 동네는 느려. 새 보기엔 그게 딱 좋아."),

        local("taean", "해송", "천수만 관찰원", NpcKind.VILLAGER, Looks.PINE, NpcSpot.BEACH,
            null,
            "천수만은 간척지라 논과 바다가 붙어 있어요. 새에겐 최고죠.",
            "소나무 방풍림 뒤로 노을이 지면 물새가 온통 실루엣이 됩니다.",
            "겨울엔 큰기러기 떼가 논에 내려앉아요. 소리가 웅장합니다."),
        resident("taean", "만수", "어촌계장", NpcKind.ELDER, Looks.SEA_SALT, NpcSpot.LAKE_SHORE,
            null,
            "기름 사고 나서 바다를 오래 돌봤어. 이제 새가 먼저 알려줘.",
            "갯벌이 살아 있으면 사람도 살아.",
            "물때표는 꼭 보고 다니게."),

        local("upo", "물안개", "우포늪 뱃사공", NpcKind.VILLAGER, Looks.MIST, NpcSpot.LAKE_SHORE,
            listOf("🛶", "🌫", "…"),
            "우포는 아침 안개가 유명해요. 안개 속에서 물닭이 나와요.",
            "쪽배는 노를 저을 때 소리가 거의 없어요. 새가 놀라지 않죠.",
            "가시연꽃 자리는 여름에만 볼 수 있어요. 조용히 봐 주세요."),
        resident("upo", "분녀", "우포 마을 이장", NpcKind.ELDER, Looks.BUCKWHEAT, NpcSpot.GROVE,
            null,
            "늪 옆에서 평생을 살았어. 새 이름은 절반쯤 알아.",
            "늪은 밟는 데가 아니야. 들여다보는 데지.",
            "마을길 따라 걸으면 뜸부기 소리가 들려."),

        local("hadori", "바당", "하도리 해녀", NpcKind.ELDER, Looks.SEA_SALT, NpcSpot.TIDAL_FLAT,
            listOf("🌊", "…", "✨"),
            "바다에서 오래 일했어. 새가 어디 앉는지 다 알지.",
            "저수지와 바다가 붙어 있어서 물새도 바닷새도 와.",
            "겨울엔 저어새가 보여. 부리가 주걱 같아. 귀여워."),
        resident("hadori", "영자", "철새도래지 이웃", NpcKind.ELDER, Looks.CAMELLIA, NpcSpot.LAKE_SHORE,
            null,
            "이 마을은 겨울에 손님이 많아. 사람 말고 새 손님 말이야.",
            "밭일하다 고개 들면 오리떼가 지나가.",
            "바람 세면 돌담 뒤에서 봐. 거기선 잘 보여."),

        local("hallasan", "동백", "한라산 안내원", NpcKind.VILLAGER, Looks.PINE, NpcSpot.TRAILHEAD,
            null,
            "고도에 따라 새가 달라져요. 천천히 올라가며 귀를 기울이세요.",
            "동백이 필 때 동박새가 꽃에 부리를 넣고 꿀을 먹어요.",
            "어치는 소리가 시끄러워요. 그 소리를 따라가면 만납니다."),
        resident("hallasan", "순옥", "산마을 할머니", NpcKind.ELDER, Looks.SNOW, NpcSpot.GROVE,
            null,
            "산 아래 살아서 새 소리로 시간을 알아.",
            "눈 오면 새들이 먹이를 못 찾아. 그래서 겨울이 어려워.",
            "산길은 해 지기 전에 내려와야 해."),

        local("imjin", "새벽", "임진강 지킴이", NpcKind.VILLAGER, Looks.MIST, NpcSpot.RIVER_BANK,
            listOf("🕊", "…", "❄"),
            "사람이 못 가는 강 건너가 새에겐 제일 안전한 잠자리예요.",
            "여울이 얼지 않는 자리에서 두루미가 밤을 보냅니다.",
            "맹금류는 낮에 강 위를 돌아요. 하늘을 먼저 보세요."),
        resident("imjin", "만길", "DMZ 마을 이장", NpcKind.ELDER, Looks.MOSS, NpcSpot.FIELD_EDGE,
            null,
            "이 마을은 밤에 불을 많이 안 켜. 새 때문이기도 해.",
            "겨울 논에 재두루미가 내려앉으면 온 동네가 조용해져.",
            "논둑 길은 미끄러워. 조심해서 걷게."),

        local("wangpi", "맑은", "왕피천 안내원", NpcKind.VILLAGER, Looks.BLUE_HAZE, NpcSpot.RIVER_BANK,
            listOf("🐟", "♪", "…"),
            "물이 하도 맑아서 바닥 자갈이 보여요. 물수리가 그걸 노리죠.",
            "가을엔 연어가 올라와요. 그걸 기다리는 새들도 있고요.",
            "계곡은 소리가 커서 오히려 새가 사람을 늦게 알아채요."),
        resident("wangpi", "돌샘", "계곡마을 어르신", NpcKind.ELDER, Looks.SNOW, NpcSpot.TRAILHEAD,
            null,
            "계곡에 살면 물소리가 자장가지.",
            "산새는 아침 일찍이 제일 많아.",
            "바위 길은 미끄러워. 발밑 보고 걷게."),

        // ===================== 12개 도시 카메라샵 — 장비는 도시에서만 =====================
        // 사진용품점은 서울 한 곳만 운영하던 옛 규칙을 버리고, **12개 도시 골목**에 카메라샵을 심었다
        // (`CameraShops.kt`). 사람 이름·옷·자리·대사는 그 파일이 유일한 출처이고, 여기는 캐스팅에 얹기만 한다.
        // 습지·갯벌·산속 탐조지에는 가게가 없다 — 장비가 필요하면 도시로 나와야 한다.
        *SHOP_CAST.toTypedArray()
    )

    val ALL: List<NpcPerson> = CAST

    val byId: Map<String, NpcPerson> = CAST.associateBy { it.id }

    /** 지역 → 그 지역에 사는 사람들 (고유 캐릭터 + 이웃 주민, 박사와 상점은 각자 지역에만) */
    private val byRegion: Map<String, List<NpcPerson>> = CAST.groupBy { it.regionId }

    fun forRegion(regionId: String): List<NpcPerson> = byRegion[regionId] ?: emptyList()

    val professor: NpcPerson = CAST.first { it.kind == NpcKind.PROFESSOR }

    /** 본점 사장 (서울) — 옛 코드/미리보기가 "상점 사람" 하나를 찾을 때 쓰는 대표 인물 */
    val shopkeeper: NpcPerson = CAST.first { it.kind == NpcKind.SHOP }

    /** 그 동네 카메라샵 사장 — 도시가 아니면 null */
    fun shopkeeperFor(regionId: String): NpcPerson? =
        SHOP_CAST.firstOrNull { it.regionId == regionId }

    /**
     * 그 바디의 대표 인물 — 미리보기 도구(`tools/preview`)와 사람 단위를 모르는 옛 코드 경로에서 쓴다.
     * 게임 안에서는 항상 [npcFrames(person)] 으로 "그 사람"을 그린다.
     */
    fun representative(kind: NpcKind): NpcPerson = when (kind) {
        NpcKind.PROFESSOR -> professor
        NpcKind.SHOP -> shopkeeper
        else -> CAST.first { it.kind == kind && it.id != professor.id && it.id != shopkeeper.id }
    }

    val professorRegionName: String get() = Regions.byId[PROFESSOR_REGION]?.name ?: "광릉숲"
    val shopRegionName: String get() = Regions.byId[SHOP_REGION]?.name ?: "서울"

    /** 지금 지역에 보리 박사가 있는가 */
    fun hasProfessor(regionId: String): Boolean = regionId == PROFESSOR_REGION

    /** 지금 지역에 카메라샵이 있는가 — **도시(12곳)에만** 있다 */
    fun hasShop(regionId: String): Boolean = CameraShops.has(regionId)

    /** 박사를 만나러 가는 안내 문장 (퀘스트 카드·대화에서 공유) */
    val professorTravelHint: String
        get() = "보리 박사는 $professorRegionName ${professor.spot.label}에 있어요"

    /** 상점을 찾아가는 안내 문장 (본점 기준) */
    val shopTravelHint: String
        get() = "본점은 $shopRegionName ${shopkeeper.spot.label} · ${CameraShops.flagship.shopName}"

    /** 이 지역 사람들은 누구인지 (지역 정보·도감류 UI에서 쓸 수 있는 한 줄) */
    fun castLabel(regionId: String): String =
        forRegion(regionId).joinToString(" · ") { it.name }
}
