package com.pizzaandbird.game

/**
 * 도시 카메라샵 — 「장비는 도시에서만 산다」 (v0.5)
 *
 * 옛날에는 사진용품점이 서울 한 곳뿐이었다. 이제 카메라샵은 **12개 도시의 골목**에 하나씩 있다.
 * 습지·갯벌·산속 탐조지에는 가게가 없다 — 장비가 필요하면 도시로 나와야 한다.
 *
 *  - **12개 도시 = 12개 카메라샵.** 각 지점은 사장 한 명과 특화 코너를 갖고, 진열대도 도시마다 다르다.
 *  - **서울은 본점** — 전 라인업 + 자전거/장식 코너. 다른 도시는 특화 품목 위주로 진열한다.
 *  - **진열대 밖의 장비는 살 수 없다.** "이건 부산 지점에 있어요" — 판매 도구를 화면이 대신 알려 준다.
 *  - **특화 코너는 10% 할인** (천원 단위 반올림) — 어느 도시에서 사느냐가 곧 전략이 된다.
 *
 * 사람의 이름·옷·자리까지 이 한 장에 담았다. `NpcRoster` 는 [CameraShops.ALL] 을 읽어 캐스팅에 얹기만
 * 하므로, 장비를 늘리거나 진열을 고칠 때는 이 파일만 손보면 된다.
 */
class CityShop(
    val regionId: String,
    /** 간판 (지도·상태창·대화머리에 나온다) */
    val shopName: String,
    /** 사장 이름 — 도시마다 다르다 (`NpcRoster` 의 이름표로 쓰인다) */
    val keeper: String,
    /** 사장 이름표 두 번째 줄 (직함) */
    val keeperTitle: String,
    /** 특화 코너 한 줄 */
    val specialty: String,
    /** 가게 소개 (지도 카드·안내 문장) */
    val blurb: String,
    /** 입구 인사 — 대화창 첫 줄 */
    val greeting: String,
    /** 사장 잡담 풀 — 이 도시에서만 나오는 말 (대화할 때마다 하나씩 골라 말한다) */
    val lines: List<String> = emptyList(),
    val look: NpcLook,
    val spot: NpcSpot,
    /** true = 본점 (서울) — 자전거·장식 코너까지 함께 본다 */
    val flagship: Boolean = false,
    /** null = 전 제품 진열. 그 외엔 이 집 진열대 id 목록 */
    private val stockIds: Set<String>? = null,
    /** 이 도시에서 10% 할인하는 장비 종류 */
    val saleKinds: Set<GearKind> = emptySet()
) {
    /** 이 집이 진열한 장비 id (본점은 전체) */
    val stock: Set<String> get() = stockIds ?: CameraGear.ALL.mapTo(LinkedHashSet()) { it.id }

    fun carries(gearId: String): Boolean = stockIds == null || gearId in stockIds

    /** 특화 할인 여부 */
    fun onSale(gear: CamGear): Boolean = gear.kind in saleKinds && gear.price > 0

    /** 이 집 가격 (할인은 천원 단위로 반올림) */
    fun priceOf(gear: CamGear): Int {
        if (!onSale(gear)) return gear.price
        val cut = (gear.price * (1f - SALE_RATE)).toInt()
        return (cut / 1000) * 1000
    }

    /** 할인 문장 (특화 코너가 없으면 빈 문자열) */
    val saleLabel: String
        get() = if (saleKinds.isEmpty()) ""
        else "특화 코너 ${SALE_LABEL} 할인 · " + saleKinds.joinToString("/") { it.label }

    companion object {
        /** 특화 코너 할인율 */
        const val SALE_RATE = 0.1f
        const val SALE_LABEL = "10%"

        /** 사장 옷차림 — `NpcLook` 은 색 5개 + 소품 플래그다 (도시별 배색은 아래 표 참조) */
        fun look(
            hair: Long, top: Long, top2: Long, pants: Long, pack: Long,
            glasses: Boolean = false, apron: Boolean = true, cane: Boolean = false,
            longHair: Boolean = false, cap: Long? = null, vest: Long? = null, scarf: Long? = null
        ) = NpcLook(
            hair.toInt(), top.toInt(), top2.toInt(), pants.toInt(), pack.toInt(),
            glasses, apron, cane, false, longHair,
            cap?.toInt(), vest?.toInt(), scarf?.toInt()
        )
    }
}

object CameraShops {

    /** 카메라샵 진열대가 공통으로 내세우는 규칙 — 어디서나 이 한 줄로 설명한다 */
    const val CITY_RULE_LINE = "장비는 도시의 카메라샵에서만 팔아요. 갯벌·숲·습지에는 가게가 없거든요."

    /** 사진 액세서리 몇 개는 모든 도시 진열대에 오른다 (입문자가 헤매지 않게) */
    private val BASIC = setOf("c_start", "c_pocket", "acc_strap", "acc_raincover")

    // ------------------------------------------------------------------
    // 12개 도시 카메라샵 — 순서는 `Regions.ALL` 의 도시 순서(서울→제주)를 따른다
    // ------------------------------------------------------------------

    private val SEOUL = CityShop(
        regionId = "seoul",
        shopName = "한강 사진상회 · 본점",
        keeper = "남기택",
        keeperTitle = "본점 사장",
        specialty = "전 라인업 · 모든 마운트 시연",
        blurb = "남대문 골목 끝, 간판이 가장 낡은 집이 본점이다. 없는 장비는 이 골목에도 없다.",
        greeting = "어서 와! 여기는 본점이라 진열대가 제일 길어. 없는 것 빼고는 다 있지.",
        lines = listOf(
            "카메라는 비싼 게 아니라 손에 맞는 게 좋은 거야.",
            "장비 가방에서 바디랑 렌즈는 언제든 다시 조립할 수 있어.",
            "무거운 렌즈는 배가 금방 고파져. 피자도 같이 사 가게.",
            "분점이 열두 동네에 생겨도 본점 진열대는 안 줄어. 구경하고 가."
        ),
        look = CityShop.look(0xFF4A2F1D, 0xFF6FAE57, 0xFF4F7D3F, 0xFF8A6A4F, 0xFFC89B6A),
        spot = NpcSpot.AVENUE,
        stockIds = null,                                  // 본점 = 전 제품
        saleKinds = emptySet(),                             // 본점은 정가 — 분점이 특화로 싸게 판다
        flagship = true
    )

    private val INCHEON = CityShop(
        regionId = "incheon",
        shopName = "바다 빛 카메라",
        keeper = "최항",
        keeperTitle = "공항 사진관",
        specialty = "여행 경량 · 방수 컴팩트",
        blurb = "비행기 소리가 들리는 골목. 한 손에 들어오는 장비만 뽑아 진열해 둔다.",
        greeting = "짐 부치기 전에 카메라부터 챙기는 사람이 많지. 우리는 가벼운 순서대로 진열해 뒀어.",
        lines = listOf(
            "기내 반입 생각하면 컴팩트가 답이야. 부쳐도 되지만 잃어버리면 답이 없고.",
            "바람 세니 끈부터 채워. 한강보다 여기가 카메라 덜 흔들어, 바다가 막 주니까.",
            "방수 컴팩트는 갯벌에서 진가야. 물 튀어도 닦으면 끝이지.",
            "무게는 숫자 두 개면 돼. 적은 게 좋아 — 400g과 900g은 어깨가 알고 있어."
        ),
        look = CityShop.look(0xFF2E2620, 0xFFC3A3E8, 0xFF9F7FC8, 0xFF4A6FA5, 0xFF8A5A33),
        spot = NpcSpot.AVENUE,
        stockIds = BASIC + setOf("c_tough", "c_zoom20", "c_travel", "b_m43", "l_m43_1260", "l_m43_100400"),
        saleKinds = setOf(GearKind.COMPACT)
    )

    private val CHUNCHEON = CityShop(
        regionId = "chuncheon",
        shopName = "호수 카메라",
        keeper = "김의암",
        keeperTitle = "삼각대 장인",
        specialty = "삼각대 · 장초원 정박",
        blurb = "호수 쪽으로 난 언덕길 초입. 잠깐 멈춰 서서 기다리는 사람들에게 장비를 빌려 준다.",
        greeting = "호수는 기다리는 사람 사진이 제일 좋아. 자리부터 잡고 보자, 뭘 먼저 살지.",
        lines = listOf(
            "물가엔 습기야. 비닐 커버보다 극세섬유가 먼저라고, 몇 번을 말해도들 안 들어.",
            "삼각대는 다리보다 헤드야. 머리 좋은 거 사면 십 년은 편해.",
            "오리 찍는 데 천리 렌즈 필요 없어. 호숫가에선 300mm면 코까지 보여.",
            "춘천에서 삼각대 사 가면 봉평터널 바람에 잘 말라. 내가 그걸 제일 좋아해."
        ),
        look = CityShop.look(0xFF5B4632, 0xFFC9B47E, 0xFFA99460, 0xFF6B6A4F, 0xFF8A6A4F, vest = 0xFF7A8A5A),
        spot = NpcSpot.MARKET,
        stockIds = BASIC + setOf(
            "c_zoom20", "b_dslr_entry", "b_apsc_entry", "l_kit1855", "l_55210",
            "l_70300", "l_150600c", "acc_tripod", "acc_strap"
        ),
        saleKinds = setOf(GearKind.ACCESSORY)
    )

    private val GANGNEUNG = CityShop(
        regionId = "gangneung",
        shopName = "눈안경 카메라",
        keeper = "송정호",
        keeperTitle = "소나무 아래 사장",
        specialty = "중급 바디 · 견습자의 첫 풀프레임",
        blurb = "카페 골목 소나무 아래. 바다 냄새 배긴 진열대에 중급 바디만 걸려 있다.",
        greeting = "우리는 비싼 것 안 팔아. 딱 한 계단 올라가는 데 필요한 것만 놓지.",
        lines = listOf(
            "처음부터 플래그십 사면 무서워서 못 들고 나가. 그게 제일 아까운 일이지.",
            "강릉 손님은 대개 여기서 바디를 바꿔. 첫 풀프레임은 여기서 시작해.",
            "소나무 그늘에서 카메라 식히다 보면 새가 내려와. 우리 단골 코스야.",
            "바람에 모래 잘 들어가. 가방 옆주머니에 극세섬유 하나 넣고 다니게."
        ),
        look = CityShop.look(0xFF4A2F1D, 0xFFC89B6A, 0xFFA97C50, 0xFF6B5A48, 0xFF4F7D3F),
        spot = NpcSpot.AVENUE,
        stockIds = BASIC + setOf(
            "b_apsc_mid", "b_dslr_mid", "b_ff_entry", "l_2470f28", "l_100400",
            "l_1635f4", "l_70300"
        ),
        saleKinds = setOf(GearKind.BODY)
    )

    private val SOKCHO = CityShop(
        regionId = "sokcho",
        shopName = "설악초점",
        keeper = "박설악",
        keeperTitle = "초망원 산장",
        specialty = "마이크로포서드 초망원",
        blurb = "설악으로 오르는 입구. 무거운 걸 싫어하는 사람이 모이는 곳이라 망원만 길다.",
        greeting = "여긴 다 초점 길이 재는 사람뿐. 두루미 찍으려면 이쪽 진열대를 봐.",
        lines = listOf(
            "작은 센서는 두 배 당겨. 300mm가 600mm가 되니 어깨가 살아 있어.",
            "산에선 그램이 곧 오르막이야. 400g 차이로 정상까지 가나 못 가나가 갈려.",
            "컨버터 물리면 조리개 두 칸 잃어. 대신 높이 두 배 가지게 — 장부가 맞아.",
            "속초 손님은 대개 12월에 와. 그땐 초망원이 다 나가서 재고가 없어, 미안."
        ),
        look = CityShop.look(0xFF4A3728, 0xFFF2B63C, 0xFFD09A2C, 0xFF4F7D3F, 0xFF8A5A33, cap = 0xFFE2853C),
        spot = NpcSpot.MARKET,
        stockIds = BASIC + setOf(
            "c_bridge60", "b_m43", "l_m43_300f4", "l_m43_100400", "l_150600c", "acc_tripod", "tc_14"
        ),
        saleKinds = setOf(GearKind.LENS)
    )

    private val DAEJEON = CityShop(
        regionId = "daejeon",
        shopName = "기차역 광학상회",
        keeper = "허갑천",
        keeperTitle = "어댑터 달인",
        specialty = "입문 세트 · 오래된 렌즈 맞추기",
        blurb = "역 광장 쪽 골목. 누가 뭘 물어도 먼저 싼 걸 권하는 집 — 시작은 다 거기서 한다.",
        greeting = "첫 카메라 사러 왔지? 잘 왔어. 우리는 비싼 것부터 권하지 않아.",
        lines = listOf(
            "번들 렌즈로도 오각형 무늬는 나와. 새는 렌즈를 안 보고 사람이 보니까.",
            "옛날 렌즈에 어댑터 물리면 값이 절반, 재미는 두 배야. 초점은 손으로 맞춰.",
            "대전은 사통팔달이라 배송이 빨라. 없는 건 오늘 시켜서 모레 받지.",
            "학생에겐 SP보다 싼 바디야. 먼저 찍고, 나중에 바꾸면 돼."
        ),
        look = CityShop.look(0xFF5B3A29, 0xFFF2A15C, 0xFFD07F3E, 0xFF6B4F35, 0xFFE2574C, cap = 0xFFF2B63C),
        spot = NpcSpot.AVENUE,
        stockIds = BASIC + setOf(
            "b_dslr_entry", "b_apsc_entry", "l_kit1855", "l_55210", "l_35f18", "acc_adapter"
        ),
        saleKinds = setOf(GearKind.BODY)
    )

    private val JEONJU = CityShop(
        regionId = "jeonju",
        shopName = "한벽 서점 카메라코너",
        keeper = "오기완",
        keeperTitle = "느린 셔터",
        specialty = "단렌즈 · 중형의 느림",
        blurb = "서점 안쪽, 책 냄새 배긴 진열대. 오래 보는 사람을 위한 짧은 초점 거리뿐이다.",
        greeting = "여기선 천천히 봐야 해. 책은 읽는 거고, 사진은 기다리는 거니.",
        lines = listOf(
            "중형 한 장이 디지털 백 장보다 오래 간직돼. 셔터 누르는 횟수가 줄거든.",
            "단렌즈 하나 달고 하루를 걷는 사람이 결국 사진이 달라져.",
            "한옥 처마 사이 새는 접사보다 단렌즈가 잘 받아. 배경이 묽어지거든.",
            "전주 손님은 렌즈를 물어보고 삼십 분을 서 있어. 그게 우리 골목 예의야."
        ),
        look = CityShop.look(0xFF3A2A22, 0xFFD05A6A, 0xFFA8404F, 0xFF4A4A6A, 0xFFF2B63C, longHair = true),
        spot = NpcSpot.MARKET,
        stockIds = BASIC + setOf(
            "c_retro", "c_apsc_prime", "b_mf", "l_35f18", "l_90macro", "l_mf_55", "l_mf_100200"
        ),
        saleKinds = setOf(GearKind.LENS)
    )

    private val DAEGU = CityShop(
        regionId = "daegu",
        shopName = "사문진 사진가게",
        keeper = "이팔공",
        keeperTitle = "튼튼 전문",
        specialty = "견실한 DSLR · 시장 먼지 대비",
        blurb = "시장 입구, 방수 테이프가 두른 진열대. 넘어져도 되는 장비만 판다.",
        greeting = "시장은 먼지가 먼저야. 튼튼한 거로 가야 해, 우리 집엔 그게 다 있고.",
        lines = listOf(
            "시장 골목에선 그립이 방수보다 중요해. 손에 땀 나면 미끄러지거든.",
            "DSLR 무겁다고들 하지만, 배터리가 두 배야. 한겨울에 그 차이가 큼니다.",
            "팔공산 오르려면 배낭이 먼저지. 카메라는 그다음이야, 순서가 있어.",
            "대구 손님은 AS를 물어. 우리 진열대는 다 고쳐 쓸 수 있는 놈뿐이야."
        ),
        look = CityShop.look(0xFF2E2620, 0xFFE2574C, 0xFFB23F44, 0xFF3F4A6F, 0xFFF2B63C, scarf = 0xFFE2574C),
        spot = NpcSpot.AVENUE,
        stockIds = BASIC + setOf(
            "b_dslr_entry", "b_dslr_mid", "l_70300", "l_100400", "l_200600", "acc_tripod", "acc_blind"
        ),
        saleKinds = setOf(GearKind.BODY)
    )

    private val GWANGJU = CityShop(
        regionId = "gwangju",
        shopName = "무등산 Optical",
        keeper = "윤무등",
        keeperTitle = "저조도 연구실",
        specialty = "저조도 · 은신막",
        blurb = "도심 밖 등산로 초입, 어두운 데 강한 것만 걸어 둔 진열대.",
        greeting = "빛 없는 데서 얼마나 열어 주나, 그게 우리 동네 실력이야.",
        lines = listOf(
            "어두운 숲에선 조리개야. 300mm F4 와 5.6 사이가 사진 한 장과 빈 카드의 차이지.",
            "은신막은 아침 일곱 시에 쳐 두고 열 시에 걷어. 그 시간이 제일 붐벼.",
            "노이즈는 이기는 게 아니라 데리고 가는 거야. 감도를 올리고 셔터를 빨리 가자.",
            "광주 손님은 대개 밤에 와. 가게 불 켜 두니 찾더라, 고맙지."
        ),
        look = CityShop.look(0xFF2A2F3A, 0xFF6FA8C8, 0xFF4F87A8, 0xFF3A4A5A, 0xFFCFD2D8),
        spot = NpcSpot.MARKET,
        stockIds = BASIC + setOf(
            "c_one_fast", "c_ff", "b_ff_entry", "b_ff_hires", "l_2470f28", "l_1635f4", "acc_blind"
        ),
        saleKinds = setOf(GearKind.ACCESSORY)
        )

    private val ULSAN = CityShop(
        regionId = "ulsan",
        shopName = "공업 카메라 계측실",
        keeper = "강태화",
        keeperTitle = "고화소 담당",
        specialty = "고화소 · 크게 자르기",
        blurb = "공단 쪽 유리 건물 1층. 재단에서 쓰는 렌즈를 탐조용으로도 함께 진열한다.",
        greeting = "우리 동네는 크게 찍는 데 강해. 자르면 되니까, 화소가 많으면 돼.",
        lines = listOf(
            "6000만 화소는 잘라 쓰기 위한 거야. 백 걸음 떨어져도 두루미 눈이 보여.",
            "공단에선 방진 먼저 물어. 먼지 들어가면 센서가 아니라 접점이 먼저 죽어.",
            "간절곶 아침 찍으러 오는 사람 위해 새벽 여섯 시엔 불을 켜 둬.",
            "대숲 백로는 여기서 삼십 분이야. 렌즈는 빌려 주고, 자리까지는 못 데려가지."
        ),
        look = CityShop.look(0xFF4A4A33, 0xFF7A9E4F, 0xFF5C7C3A, 0xFF6B5A48, 0xFF9AA3AD, vest = 0xFF5C7C3A),
        spot = NpcSpot.AVENUE,
        stockIds = BASIC + setOf("c_bridge_pro", "b_ff_hires", "b_flagship", "l_800f63", "tc_20", "acc_tripod"),
        saleKinds = setOf(GearKind.BODY)
    )

    private val BUSAN = CityShop(
        regionId = "busan",
        shopName = "감천만 렌즈상회",
        keeper = "차락동",
        keeperTitle = "프로 진열대",
        specialty = "프로급 초망원 500 · 600",
        blurb = "언덕 아래 색색의 골목. 돈 많은 손님과 정말 찍는 손님이 같은 계단을 오른다.",
        greeting = "여기 진열대엔 값비싼 게 있어. 그래도 구경은 공짜니 천천히 보게.",
        lines = listOf(
            "500mm F4 는 손이 아니라 몸으로 버티는 거야. 스트랩 먼저 조절하고.",
            "감천 여기선 갈매기가 렌즈 앞에 먼저 와. 프로급 사도 입문용 써도 바다는 같지.",
            "빌려 쓰는 것도 배운 거래. 우리 집은 시세보다 싸게 내주는 대신 하루 두 장만 찍게 해.",
            "감천만 위에서 내려다보면 바다 위에 새가 보여. 그거 찍으려고 서울에서 오는 사람도 있다네."
        ),
        look = CityShop.look(0xFFEDEAE2, 0xFFF8F6F0, 0xFFDCD8CE, 0xFF8A93A0, 0xFFB23F44, cane = true, scarf = 0xFFB23F44),
        spot = NpcSpot.MARKET,
        stockIds = BASIC + setOf(
            "b_flagship", "b_ff_bird", "l_500f4", "l_600f4", "l_300f28", "tc_14", "tc_20", "acc_blind"
        ),
        saleKinds = setOf(GearKind.LENS)
    )

    private val JEJU = CityShop(
        regionId = "jeju",
        shopName = "바람 카메라",
        keeper = "부강순",
        keeperTitle = "섬 진열대",
        specialty = "방수 · 경량 · 돌담용",
        blurb = "돌담 길 끝 작은 간판. 소금바람 때문에 장비는 다 주머니에 넣고 다닌다고 조언한다.",
        greeting = "섬엔 바람이 제일 무서워. 방수 커버부터 권한다네, 렌즈는 그다음이고.",
        lines = listOf(
            "염분이 렌즈 코팅을 갉아. 밤엔 꼭 실내로 들여 닦고 자게.",
            "가벼운 게 최고야. 올레길 열흘 걸으면 중량표가 죄다 되거든.",
            "겨울손님 찍으러 오는 사람이 늘었어. 물새는 바다 쪽, 밭엔 땅에 붙는 새가 많다네.",
            "섬에서 렌즈 바꾸다 먼지 들어가면 끝이야. 몸에서 떼지 말고 돌리며 빠르게, 짧게."
        ),
        look = CityShop.look(0xFF3A3F4A, 0xFFEAF3F6, 0xFFCBD9E0, 0xFF4A6FA5, 0xFF8A6A4F, cap = 0xFF3F6FB0),
        spot = NpcSpot.MARKET,
        stockIds = BASIC + setOf("c_tough", "c_zoom20", "b_m43", "l_m43_1260", "l_m43_100400", "acc_raincover"),
        saleKinds = setOf(GearKind.COMPACT)
    )

    /** 진열 순서 = 도시 순서 (`Regions.ALL` 의 도시 12곳과 동일) */
    val ALL: List<CityShop> = listOf(
        SEOUL, INCHEON, CHUNCHEON, GANGNEUNG, SOKCHO, DAEJEON, JEONJU, DAEGU,
        GWANGJU, ULSAN, BUSAN, JEJU
    )

    val byRegion: Map<String, CityShop> = ALL.associateBy { it.regionId }

    /** 본점 (전 라인업 + 자전거·장식 코너) */
    val flagship: CityShop get() = SEOUL

    /** 카메라샵이 있는 지역 id 전체 — 「도시에서만 판다」는 규칙의 목록 */
    val CITY_IDS: Set<String> = ALL.mapTo(LinkedHashSet()) { it.regionId }

    /** 이 도시에 카메라샵이 있는가 — 판정 지점은 여기 하나 */
    fun has(regionId: String?): Boolean = regionId != null && byRegion.containsKey(regionId)

    fun shop(regionId: String?): CityShop? = if (regionId == null) null else byRegion[regionId]

    /** 지금 지역 진열대 (도시가 아니면 빈 목록) */
    fun catalog(regionId: String): List<CamGear> {
        val shop = byRegion[regionId] ?: return emptyList()
        return CameraGear.ALL.filter { shop.carries(it.id) }
    }

    /** 탭별 진열 (이 도시 가격 순) */
    fun tabItems(regionId: String, tab: GearKind): List<CamGear> {
        val shop = byRegion[regionId] ?: return emptyList()
        val pool: List<CamGear> = when (tab) {
            GearKind.COMPACT -> CameraGear.COMPACTS
            GearKind.BODY -> CameraGear.BODIES
            GearKind.LENS -> CameraGear.LENSES
            GearKind.TELECONV, GearKind.ACCESSORY -> CameraGear.TELECONVS + CameraGear.ACCESSORIES
        }
        return pool.filter { shop.carries(it.id) }.sortedBy { shop.priceOf(it) }
    }

    /** 이 도시 가격 (할인 적용) */
    fun priceOf(regionId: String, gear: CamGear): Int = byRegion[regionId]?.priceOf(gear) ?: gear.price

    fun onSale(regionId: String, gear: CamGear): Boolean = byRegion[regionId]?.onSale(gear) == true

    /** 이 장비가 진열된 도시 이름 (아무 데도 없으면 본점 안내) */
    fun soldInLabels(gearId: String): String {
        val cities = ALL.filter { it.carries(gearId) }
        if (cities.isEmpty()) return "어느 도시에도 없는 장비"
        if (cities.size == ALL.size) return "12개 도시 전 진열대"
        return cities.joinToString("·") { Regions.byId[it.regionId]?.name ?: it.regionId }
    }

    /** 지금 동네에서 살 수 없다는 안내 한 줄 (도시면 빈 문자열) */
    fun notSoldHereLine(regionId: String): String {
        if (has(regionId)) return ""
        val near = nearestShop(regionId)
        val name = Regions.byId[near.regionId]?.name ?: near.regionId
        return "이 동네엔 카메라샵이 없어요. 가장 가까운 데는 $name ${near.shopName} · ${near.spot.label}"
    }

    /**
     * 가장 가까운 카메라샵 — 지역 연결 그래프(터널)를 따라 BFS 로 찾는다.
     * 지금 동네가 이미 도시면 자기 동네 가게를 돌려 준다. (모든 지역은 그래프상 도시와 붙어 있다)
     */
    fun nearestShop(fromRegion: String): CityShop {
        byRegion[fromRegion]?.let { return it }
        val seen = HashSet<String>()
        seen.add(fromRegion)
        val queue = ArrayDeque<String>()
        queue.add(fromRegion)
        while (queue.isNotEmpty()) {
            for (next in Regions.exits(queue.removeFirst()).values) {
                if (!seen.add(next)) continue
                shop(next)?.let { return it }
                queue.add(next)
            }
        }
        return flagship
    }

    /** 가게까지 걸리는 터널 칸 수 (지도·안내 문장용). 이미 도시면 0, 못 찾으면 -1 */
    fun hopsToShop(fromRegion: String): Int {
        if (has(fromRegion)) return 0
        val dist = HashMap<String, Int>()
        dist[fromRegion] = 0
        val queue = ArrayDeque<String>()
        queue.add(fromRegion)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = dist[cur] ?: continue
            for ((_, next) in Regions.exits(cur)) {
                if (next in dist) continue
                dist[next] = d + 1
                if (byRegion.containsKey(next)) return d + 1
                queue.add(next)
            }
        }
        return -1
    }

    /** 자전거로 찾아갈 때 쓰는 한 줄 안내 */
    fun travelHint(fromRegion: String): String {
        val here = byRegion[fromRegion]
        if (here != null) return "${Regions.byId[here.regionId]?.name} ${here.shopName} · ${here.spot.label}"
        val near = nearestShop(fromRegion)
        val hops = hopsToShop(fromRegion).coerceAtLeast(1)
        return "${Regions.byId[near.regionId]?.name} ${near.shopName} · 터널 $hops 칸"
    }

    /** "어디서 사나" 한 줄 — 장비 선택 창·장비 가방에서 공통으로 쓴다 */
    fun buyHint(regionId: String): String {
        val here = byRegion[regionId]
        if (here != null) return "${Regions.byId[here.regionId]?.name ?: here.regionId} ${here.shopName}"
        val near = nearestShop(regionId)
        val name = Regions.byId[near.regionId]?.name ?: near.regionId
        return "$name ${near.shopName}"
    }

    /** 이 동네에서 살 수 있는 장비 수 (상태창 한 줄) */
    fun carriedCount(regionId: String): Int = catalog(regionId).size
}
