package com.pizzaandbird.game

import kotlin.random.Random

/**
 * [P08] 조건부 대사 풀 — NPC 말은 여기서만 고른다. 게임 로직 무의존(순수 데이터+선택 함수).
 *
 * 말이 바뀌는 조건: 메인 진행 장(chapter) × 계절(Season, [P01]) × 날씨(Weather) × 밤낮(night).
 * 계획서 P08 스펙의 `Ctx.season: Season?`은 P1 미머지 대비 타입이었으나,
 * `Season.kt`([P01])가 이미 main에 머지되어 non-null `Season`으로 확정했다.
 *
 * 이동 보존 규칙: 기존 `WorldScene.talkTo`에 박혀 있던 대사 리터럴(storyHint 6분기, 꼬마 6줄,
 * 할머니 6줄)은 **한 글자도 바꾸지 않고** 이곳으로 이전했다. 신규 문구는 각 풀에 추가만 했다.
 */
object Dialogues {
    /** 대사 선택에 필요한 최소 문맥. WorldScene → [SideStories.ctx]로 만든다. */
    data class Ctx(
        val regionId: String,
        val chapter: Int,          // GameState.mainQuestStage (0..8)
        val season: Season,        // [P01] Season.kt — 계절 문장용
        val weather: Weather,
        val night: Boolean,
        val day: Int
    )

    private val rnd = Random.Default

    // -------------------------------------------------------------------
    // 이동 보존: 기존 문구 (수정 금지 — 기존 세계관 보존)
    // -------------------------------------------------------------------

    /** 메인 진행 장별 탐조 예절 힌트 — 기존 WorldScene.talkTo의 VILLAGER storyHint를 그대로 이전. */
    fun storyHint(chapter: Int): String = when (chapter) {
        0, 1 -> "멀리 가기 전에도 창밖의 새부터 천천히 보면 좋아요."
        2 -> "숲에서 나무 구멍을 발견해도 가까이 들여다보면 안 돼요. 둥지일 수 있거든요."
        3 -> "물가 새는 건너편에서 봐도 충분히 아름다워요."
        4 -> "철새가 쉬는 곳에서는 무리 쪽으로 걷지 않는 게 이 동네 약속이에요."
        5 -> "갯벌에는 사람 눈에 안 보이는 새들의 식탁이 아주 많대요."
        else -> "희귀새 위치를 바로 퍼뜨리기 전에 새가 안전할지 한 번 생각해 주세요."
    }

    /** 기존 WorldScene.talkTo의 KID 6줄 — 그대로 이전. */
    private val KID_BASE = listOf(
        "우와, 카메라 멋져요! 저도 크면 탐조할 거예요!",
        "저기요, 저 새 이름 알아요? 어… 까먹었어요.",
        "자전거 타면 빨리 가지만 금방 배고파져요!",
        "박사님이 낡은 새 수첩을 들고 찾고 있었어요. 가보실래요?",
        "새 둥지를 찾으면 비밀로 해 줘야 해요. 새끼가 놀라잖아요!",
        "저는 도감 숫자보다 새 이름을 하나 제대로 아는 게 더 좋아요."
    )

    /** 기존 WorldScene.talkTo의 ELDER 6줄 — 그대로 이전. */
    private val ELDER_BASE = listOf(
        "요즘 젊은이들은 참 부지런해요.",
        "옛날엔 이 동네에 두루미가 많이 왔었지…",
        "피자도 잘 먹고 다니게. 몸이 자본이야.",
        "해 지기 전에 들어가게. 밤엔 부엉이가 나온다네.",
        "자네 할머니도 새를 많이 보려 하기보다 오래 보려 했지.",
        "귀한 새를 봤다면 발자국을 남기지 않는 게 가장 좋은 자랑이라네."
    )

    // -------------------------------------------------------------------
    // 신규: 계절 문장 풀 [P08] — 총 62줄 (스펙 DoD: 신규 조건부 대사 60줄 이상)
    //   KID 계절 8 + 날씨 8 + 밤 3 = 19
    //   ELDER 계절 12 + 날씨 4 + 밤 4 = 20
    //   VILLAGER 계절 12 + 날씨 8 + 밤 3 = 23
    // -------------------------------------------------------------------

    private val KID_SEASON = mapOf(
        Season.SPRING to listOf(
            "벚꽃 나무에 참새가 세 마리나 앉았었어요! 꽃잎이 새 먹이인 줄 알았어요.",
            "돌아온 제비 보셨어요? 저는 제비를 보면 여름 준비하랬는데… 아직 멀었나?"
        ),
        Season.SUMMER to listOf(
            "너무 더워요. 새들은 그늘에서 낮잠 자는데 저만 뛰어다녔어요.",
            "장마엔 물가에 새가 많대요! 우산보다 쌍안경이 먼저예요."
        ),
        Season.AUTUMN to listOf(
            "나뭇잎이 떨어지니까 새가 더 잘 보여요. 나무가 옷을 벗었거든요!",
            "기러기가 가위 모양으로 지나갔어요. 어디 가냐고 물어봤는데 안 대답했어요."
        ),
        Season.WINTER to listOf(
            "손이 얼어 붙네요. 새들은 왜 안 추워요? 배가 불러서 그런가요?",
            "언 강가에 새들이 모여 있었어요. 물은 숨고 새는 안 숨어요."
        )
    )

    private val KID_WEATHER = mapOf(
        Weather.SUNNY to listOf("햇살이 좋아서 깃털이 반짝반짝 빛나요. 금빛 새인 줄 알았어요!"),
        Weather.CLOUDY to listOf("흐린 날엔 새도 사진 찍기 좋아한대요? 얼굴이 그늘에 안 가려져요!"),
        Weather.RAIN to listOf(
            "비 오는 날 물가엔 새가 잔뜩 와요. 우산 쓰고 뛰었더니 신발이 다 젖었어요.",
            "빗방울 맞고도 노래하는 새 보셨어요? 저라면 큰소리 냈을 거예요."
        ),
        Weather.WIND to listOf("바람이 세서 새가 제자리에서 맴돌아요. 헤엄치는 것 같아요! 완전 물고기예요!"),
        Weather.SNOW to listOf(
            "첫눈에 발자국 개구멍마다 새 발자국이었어요! 걸음걸이가 거의 춤이에요.",
            "눈 오는 날 창밖만 봤어요. 새가 눈송이랑 헷갈려서 눈이 아파요."
        )
    )

    private val KID_NIGHT = listOf(
        "하품이 나요… 근데 올빼미는 지금이 출근 시간이라며요? 새도 야근이 있어요?",
        "어른들은 밤엔 다 집에 가는데, 밤새들은 어디서 자요? 궁금해서 잠이 안 와요.",
        "낮에 본 새 이름이 벌써 생각 안 나요… 내일 아침에 또 볼래요. 꼭."
    )

    private val ELDER_SEASON = mapOf(
        Season.SPRING to listOf(
            "봄비가 그치면 뻐꾸기가 먼저 아는 척하더군. 사람은 달력을 봐야 하지.",
            "연두빛 나무숲엔 첫손님이 빨리 온다네. 새도 집들이를 좋아하나 봐.",
            "옛날엔 봄이 오면 처마 밑에 돌아온 제비 수를 세곤 했지. 몇 해나 잊지 않았는가."
        ),
        Season.SUMMER to listOf(
            "여름 숲은 소란해. 새끼들 밥값을 벌어야 하니 어른새들도 서두르지.",
            "장마철엔 창문 닫는 소리보다 물소리가 먼저다. 새들은 그걸 기다려.",
            "한여름 정오엔 새도 그늘을 아는구나. 사람은 왜 그늘을 잊는가."
        ),
        Season.AUTUMN to listOf(
            "가을 하늘은 텅 비어 보이지만, 저 위엔 여행자들이 줄을 서 있어.",
            "떠나는 새를 배웅하는 계절이지. 배웅을 알면 만남도 잘하게 된다네.",
            "낙엽 밟는 소리에 새들이 놀라지 않는군. 다들 저마다 바쁜 때라 그런가."
        ),
        Season.WINTER to listOf(
            "겨울 손님은 예의 바르다네. 왔다 가는 통지도 없이 조용히 왔다네.",
            "눈이 오면 세상이 하얘서 새가 도장 찍은 것 같지. 붉은 가슴이 특히.",
            "겨울엔 나무도 새도 뼈대가 보인다네. 꾸밈이 없으면 정이 드는 법이야."
        )
    )

    private val ELDER_WEATHER = mapOf(
        Weather.RAIN to listOf(
            "비가 오는 물가가 제일 재밌다네. 새들이 식탁을 차리니까.",
            "젊을 땐 비 맞으며 새 보던 게 낭만인 줄 알았지. 지금은 창가가 낭만이라네."
        ),
        Weather.SNOW to listOf(
            "눈 오는 날은 새 발자국이 일기장이 되지. 어디를 다녔는지 다 적혀 있어.",
            "눈송이 사이로 날아가는 새를 본 적 있는가. 그건 종이에 못 적는 글씨라네."
        )
    )

    private val ELDER_NIGHT = listOf(
        "밤공기는 새 소리를 잘 전해 준다네. 부엉이 한 마디면 하루의 마침표지.",
        "불 끄기 전에 창밖 한 번 보게. 밤에도 세상은 영업 중이라네.",
        "밤새는 어둠을 무서워하지 않아. 나도 젊을 땐 그랬지… 지금은 이불이 좋아.",
        "내일 새벽이 제일 새가 많다고? 그럼 오늘은 일찍 자게. 약속은 지키는 것."
    )

    private val VILLAGER_SEASON = mapOf(
        Season.SPRING to listOf(
            "봄이 오면 이 동네 풍경이 하루에 한 번씩 갈아입혀요. 아침과 저녁이 다른 옷이에요.",
            "꽃이 피는 길은 새가 먼저 아네요. 저도 그 길로 출근해요.",
            "겨우내 비웃던 나뭇가지가 요즘 인사를 하네요. 봄엔 다들 부지런해요."
        ),
        Season.SUMMER to listOf(
            "여름엔 이 지역 특산물이 제일이죠. 새들이 먼저 알고 와요, 우리 시장으로.",
            "장마 뒤 하늘이 제일 파래요. 젖은 날갯짓도 다 마른답니다.",
            "무더위엔 새나 사람나 그늘이 보약이에요. 천천히 걸으세요."
        ),
        Season.AUTUMN to listOf(
            "가을엔 길가에 여행자들이 앉아 쉬어요. 날개 달린 여행자들 말이에요.",
            "곡식 다 걷은 들판이 새들에겐 잔치상이래요. 뒷마당도 관광지예요.",
            "지금 이 달이 제일 예뻐요. 다들 저MRI 가을 하늘 사진 하나쯤 있죠."
        ),
        Season.WINTER to listOf(
            "겨울 강변은 사람보다 새가 많아요. 우리 동네 겨울 명물이에요.",
            "추운 날엔 새들도 동네 어귀로 모여요. 마트 앞 난로 같은 곳이 있거든요.",
            "눈 오는 아침 출근길이 제일 조용해요. 새도 사람도 발소리를 줄여요."
        )
    )

    private val VILLAGER_WEATHER = mapOf(
        Weather.SUNNY to listOf(
            "맑은 날은 실루엣만 봐도 누군지 알아요. 옆집 아저씨도 그렇고, 까치도요.",
            "이런 날엔 사진이 잘 나온대요. 상점 주인이 자꾸 사라고 재촉해요."
        ),
        Weather.CLOUDY to listOf(
            "흐린 날 새들은 얌전해요. 사진 찍기 딱 좋은 분위기죠.",
            "구름이 낮을수록 새도 낮게 날아요. 고개 숙이고 걷게 되네요."
        ),
        Weather.RAIN to listOf(
            "비 오는 날 갯벌·물가가 제일 시끄러워요. 좋은 뜻이에요, 식탁이 열렸으니까.",
            "우산 챙기셨어요? 새들은 비 오면 목욕한다고 바빠요."
        ),
        Weather.WIND to listOf(
            "바람 세게 부는 날엔 독수리·제비 같은 큰 날개가 하늘을 차지해요.",
            "풀숲이 다 한 방향으로 눕죠. 이런 날 새 소리를 따라가기가 쉬워요."
        ),
        Weather.SNOW to listOf(
            "눈 오는 날엔 발도 조심하고 새 것도 조심하세요. 다들 미끄러워요.",
            "첫눈 오는 날 우체국 앞에 새들이 줄 서 있었어요. 편지보다 빠르더군요."
        )
    )

    private val VILLAGER_NIGHT = listOf(
        "이 시간엔 가로등 아래가 제일 밝아요. 새는 이미 자고, 밤새가 근무 중이에요.",
        "밤 공기가 좋죠. 어느 집이든 불 한 켠 켜놓고 기다려요.",
        "야경 좋아하시면 강변 가세요. 밤새들이 저녁 운동을 하거든요."
    )

    // -------------------------------------------------------------------
    // 선택 함수
    // -------------------------------------------------------------------

    /** 동네 주민 — 지역 소개(기존 map.region.villager) + 장별 storyHint + 계절/날씨/밤 문장 조합. */
    fun villager(c: Ctx): String {
        val sb = StringBuilder("\"")
        Regions.byId[c.regionId]?.let { sb.append(it.villager).append('\n') }
        sb.append(storyHint(c.chapter))
        // 계절 문장은 항상, 날씨/밤 문장은 확률적으로 한 줄 더 얹는다 (매번 다른 이웃)
        val extra = StringBuilder()
        pickFrom(VILLAGER_SEASON[c.season])?.let { extra.append('\n').append(it) }
        val nightOrWeather = if (c.night) pickFrom(VILLAGER_NIGHT)
        else pickFrom(VILLAGER_WEATHER[c.weather])
        if (nightOrWeather != null && rnd.nextFloat() < 0.55f) extra.append('\n').append(nightOrWeather)
        sb.append(extra)
        return sb.append('"').toString()
    }

    /** 꼬마 — 기존 6줄 + 계절 6+줄 + (밤이면 밤줄, 아니면 날씨줄) 풀에서 한 줄. */
    fun kid(c: Ctx): String {
        val pools = ArrayList<List<String>>(4)
        pools.add(KID_BASE)
        KID_SEASON[c.season]?.let { pools.add(it) }
        if (c.night) {
            pools.add(KID_NIGHT)
        } else {
            KID_WEATHER[c.weather]?.let { pools.add(it) }
        }
        return "\"${pools[rnd.nextInt(pools.size)].random(rnd)}\""
    }

    /** 할머니 — 기존 6줄 + 계절 + (밤이면 밤줄, 비/눈이면 날씨줄) 풀에서 한 줄. */
    fun elder(c: Ctx): String {
        val pools = ArrayList<List<String>>(4)
        pools.add(ELDER_BASE)
        ELDER_SEASON[c.season]?.let { pools.add(it) }
        if (c.night) {
            pools.add(ELDER_NIGHT)
        } else {
            ELDER_WEATHER[c.weather]?.let { pools.add(it) }
        }
        return "\"${pools[rnd.nextInt(pools.size)].random(rnd)}\""
    }

    private fun <T> pickFrom(list: List<T>?): T? =
        if (list.isNullOrEmpty()) null else list[rnd.nextInt(list.size)]
}
