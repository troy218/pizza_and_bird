package com.pizzaandbird.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * [P08] 지역 사이드 스토리 — 12개 도시 각각 3막(도입→심부름→마무리) 미니 에피소드.
 *
 * 진행은 기본 prefs `feat_story_v1` 키(규칙 3)로 저장한다 — P5 백업이 feat_* 키를 자동 수집한다.
 * 목표 판정은 **대화 시점에만** 검사한다(별도 진행 훅 없음). 메인 퀘스트(Quests.kt)는 무수정이며,
 * 진행 안내는 메뉴 탭이 아니라 대화로만 이루어진다.
 *
 * 막 진행 의미: 0=미시작 / 1=도입 본 상태 / 2=심부름 중 / 4=완료 (완료 후 재방문 시 반복 없음)
 * 12편 전부 완료하면 숨은 에필로그 「수첩의 뒷장」이 아무 NPC와의 대화에서 1회 열린다.
 */
object SideStories {
    const val SAVE_KEY = "feat_story_v1"
    private const val PREFS = "pizza_and_bird_save"   // SaveManager와 동일한 기본 prefs (규칙 3)

    data class Episode(
        val id: String,
        val regionId: String,
        /** 담당 주민의 역할군 — 대사는 반드시 이 주민의 목소리(§3 페르소나)로 쓴다 */
        val npc: NpcKind,
        val title: String,
        val acts: List<Act>,
        val nudge: String,
        val reward: Pair<Int, Int>,     // (골드 ₩, 행운)
        /**
         * 「수첩에 한 줄」 — 마무리 막이 끝나면 플레이어 수첩에 **실제로 기록되는** 문장.
         * 예전에는 대사 속에서만 있던 부탁을, 이제는 수첩이 진짜로 받아 적는다
         * (`NotebookOverlay`, `feat_story_v1` 의 `j` 배열에 남은다).
         */
        val journal: String = "",
        /** 「뒷장」 — 본편 완료 후 **다시 왔을 때** 한 번만 열리는 후일담 3줄 (작은 보상 포함) */
        val echo: List<String> = emptyList(),
        val echoReward: Pair<Int, Int> = 0 to 0
    ) {
        val hasEcho: Boolean get() = echo.isNotEmpty()
    }

    data class Act(val goal: Goal, val lines: List<String>)

    sealed class Goal {
        /** 지정 사진 촬영 — birdId(정확한 종) 또는 서식지/철새 구분/등급 조건 중 하나 이상. */
        data class Photo(
            val birdId: String? = null,
            val tierMin: Int = 1,
            val habitat: String? = null,     // water/forest/coast/mountain/wetland…
            val migration: String? = null    // "텃새"/"겨울철새"/"여름철새"/"나그네새" (migrationLabel 포함 검사)
        ) : Goal()

        /** 특산 피자 납품 — 특산 피자(id≥12, 품질 무관) 1개 소모. [P07] 머지 시에만 자동 사용. */
        data class Deliver(val pizzaIdMin: Int) : Goal()

        /** 장소 없이 대화만으로 완수. */
        object Talk : Goal()
    }

    // -------------------------------------------------------------------
    // 에피소드 카탈로그 (12편 — docs/STORY.md §7과 정합)
    // -------------------------------------------------------------------

    /** [P07] 특산 피자(id≥12)가 존재하면 납품형, 아니면 숲새 사진형으로 자동 대체된다. */
    private val gangneungGoal: Goal =
        if (Pizzas.ALL.any { it.id >= 12 }) Goal.Deliver(12) else Goal.Photo(habitat = "forest")

    // 강릉 담당은 안목 해변 카페의 보경(VILLAGER) — 상점 주인의 말투가 아니라 그녀의 목소리로 쓴다
    private fun gangneungActs(): Triple<List<String>, List<String>, List<String>> =
        if (gangneungGoal is Goal.Deliver) Triple(
            listOf(
                "안목 카페 거리 소나무 그늘 아래가 제 자리예요. 가게 문 열기 전, 거기 앉아 바다를 봐요.",
                "첫 장사날 기억나요. 카메라 든 소년이 숲새 한 마리 찍고 와서 피자 한 판을 시키더라고요.",
                "그때 오븐이 막 뜨거울 때였죠. 갓 구운 특산 피자 한 판, 그 소년처럼 가져다주실래요?"
            ),
            listOf(
                "특산 피자면 더 좋고요. 식어도 마음은 뜨거우니까 천천히 오세요.",
                "그 소년이 가르쳐 줬어요. 따뜻할 때 나눠 먹는 게 제일 맛있다고요.",
                "아, 새한테는 주면 안 돼요. 새는 구경만, 피자는 사람끼리 나눠 먹는 거예요."
            ),
            listOf(
                "잘 가져오셨네요. 그 소년 덕에 시작한 가게가, 이제 피자로 이어져요.",
                "그 소년 말인가요? 지금은 광릉숲에서 수첩을 펴 놓고 계신 박사님이세요. 세월이 이렇게 이어지나 봐요.",
                "수첩에 적어 두세요. '강릉의 소나무 그늘 아래엔 늘 한 자리가 비어 있다'고요."
            )
        ) else Triple(
            listOf(
                "안목 카페 거리 소나무 그늘 아래가 제 자리예요. 가게 문 열기 전, 거기 앉아 바다를 봐요.",
                "첫 장사날 기억나요. 카메라 든 소년이 숲새 한 마리 찍고 와서 피자 한 판을 시키더라고요.",
                "숲새 한 마리 기록해 오실래요? 그 소년이 찍던 것처럼, 그늘에서요."
            ),
            listOf(
                "숲이 조용하면 딱따구리 소리부터 들려요. 그 소리를 따라가 보세요.",
                "숲새 한 마리 기록이면 돼요. 소나무 숲이 기다리고 있어요.",
                "사진 한 장이면 충분해요. 요즘 손님들도 다들 한 장씩 간직하더라고요."
            ),
            listOf(
                "보기 좋네요. 그 소년 덕에 시작한 가게라니까요, 정말로.",
                "그 소년은 이제 광릉숲의 박사님이시고요. 오래 보는 일은 끝내 사람으로 남나 봐요.",
                "수첩에 적어 두세요. '강릉의 소나무 그늘 아래엔 늘 한 자리가 비어 있다'고요."
            )
        )

    private val gangneungLines = gangneungActs()

    private fun birdIdOf(name: String): String? =
        Birds.byId.values.firstOrNull { it.name == name }?.id

    val EPISODES: List<Episode> = listOf(
        Episode("seoul_first_window", "seoul", NpcKind.VILLAGER, "창밖의 첫 수업",
            listOf(
                Act(Goal.Talk, listOf(
                    "아, 수첩 주인 아니었나? 그때 이 동네서 처음 배운 게 뭐였는지 기억나요?",
                    "저도 참새부터 시작이었어요. 창밖에 오는 이웃부터 이름을 붙이니까 동네가 달라 보이더라고요.",
                    "부탁 하나 해도 될까요… 기록된 새 아무나 한 마리. 그 첫 배움의 증거를 보여주시면 좋겠어요."
                )),
                Act(Goal.Photo(), listOf(
                    "참새든 까치든 좋아요. 오래 바라본 새라면 충분해요.",
                    "처음엔 다들 흔한 새부터 배우더라고요. 멀리 가기 전에 창밖부터… 그게 이 동네 약속이에요.",
                    "서두르지 마세요. 수첩은 빨리 채우는 게 아니라 오래 남기는 거니까요."
                )),
                Act(Goal.Talk, listOf(
                    "이 기록… 창밖 수업의 성적표 같네요. 저도 요즘 창가에 이름표를 붙였어요. 내가 아는 이웃들 자리에요.",
                    "가르쳐 줘서 고마워요. 배움은 남에게 쓸 때 제일 빛나는 것 같아요.",
                    "수첩에 오늘 이야기 한 줄 적어 두세요. 나중에 펼쳤을 때 이 동네가 떠오를 거예요."
                ))
            ),
            "기록된 새 아무나 한 마리면 돼요. 참새도, 까치도 좋아요. 천천히 오세요.",
            20000 to 5,
            "서울의 첫 수업은 창밖에서 시작됐다",
            listOf(
                "자네가 적어 준 그 한 줄, 우리 골목 문구점 유리창에도 붙였네. '창밖을 보게'라는 말이지.",
                "손님 둘이 그걸 보고 참새 사진을 찍었다더군. 빈 수첩을 들고 왔더라네.",
                "가르친 건 자넨데, 고맙다는 인사는 내가 다 하고 있어. 부끄러울 정도야."
            ),
            10000 to 2
        ),
        Episode("incheon_mudflat_road", "incheon", NpcKind.ELDER, "갯뻘이 걸어온 길",
            listOf(
                Act(Goal.Talk, listOf(
                    "썰물이 진다네. 이 갯벌이 젊을 땐 그저 진흙탕이었지… 굶던 해엔 이 진흙이 우리를 살렸어.",
                    "갯벌은 겉보단 속이 두둑한 곳이라네. 사람도 새도 같이 걸어온 길이야.",
                    "물가의 새 한 마리 기록을 보여주게. 식탁이 아직 살아 있다는 걸 확인하고 싶어."
                )),
                Act(Goal.Photo(habitat = "wetland"), listOf(
                    "도요새든 갈매기든 좋아. 갯벌에 내려앉은 새는 멀리서 보는 게 예의란다.",
                    "만조 두 시간 전후가 절기라네. 서두르지 말게, 갯벌은 기다려 주는 법이니까.",
                    "사람이 먹던 걸 새도 먹고, 새가 남긴 자리에 다시 조개가 자란다네. 그게 이 길의 순서야."
                )),
                Act(Goal.Talk, listOf(
                    "이 사진을 보니 그 시절 식탁이 생각나네… 고맙네, 젊은이.",
                    "갯벌은 사람도 새도 같이 살게 해 주는 길이야. 이 길을 아는 사람이 한 명 늘었으니 됐다.",
                    "수첩에 적어 두게. '인천의 갯벌은 진흙이 아니라 식탁이라네.' 나중에 자네도 누군가에게 그 말을 해 주게."
                ))
            ),
            "물가의 새 한 마리 기록만 보면 된다네. 급할 것 없어, 썰물은 기다려 주는 법이지.",
            35000 to 7,
            "인천의 갯벌은 진흙이 아니라 식탁이다",
            listOf(
                "그 문장 적어 뒀지? 이제 관광객한테도 우리가 먼저 그 말을 해 준다네. '밥상에 손을 대지 마시오' 하고.",
                "만조 전에 둑길에 팻말도 세웠어. 새가 먼저 밥 먹게 기다리는 거지, 사람이 먼저 밟으면 안 되지.",
                "우리 손자도 수첩 사 달라고 난리여. 배운 게 이렇게 돌아다니는 걸 보니 신기하네."
            ),
            12000 to 2
        ),
        Episode("chuncheon_lake_me", "chuncheon", NpcKind.KID, "호수에 비친 나",
            listOf(
                Act(Goal.Talk, listOf(
                    "저기요! 할머니들이 호수에서 가만히 앉아 있으면 새가 온대요. 진짜예요?",
                    "저도 해 보고 싶은데요, 3분도 안 지나면 심심해져요. 꼼짝 않는 법을 알려주세요!",
                    "대신 약속할게요. 물새 한 마리 기록되면, 그게 제가 기다렸다는 증거예요!"
                )),
                Act(Goal.Photo(habitat = "water"), listOf(
                    "물새라면 뭐든 좋아요! 오리, 갈매기… 뭐든요!",
                    "기다리는 동안에는 숨소리만 줄이기! 저 벌써 연습 중이에요.",
                    "물에 비친 제 모습도 같이 찍히면 어떡하죠? …그것도 나라고 봐야 하는 건가요?"
                )),
                Act(Goal.Talk, listOf(
                    "우와… 진짜 왔네요. 저 조용했던 시간이 이 사진 속에 다 들어 있어요!",
                    "기다리면 오는구나. 다음엔 저도 할머니처럼 아무렇지 않게 앉아 있을 거예요!",
                    "수첩에 '춘천의 호수는 기다림의 거울'이라고 적어 주세요. 제가 쓴 말 중에 제일 마음에 들어요!"
                ))
            ),
            "물새 한 마리 기록이요! 호수는 도망 안 가니까 천천히 하셔도 돼요.",
            30000 to 6,
            "춘천의 호수는 기다림의 거울이다",
            listOf(
                "저 오늘 30분 앉아 있었어요! 3분이 아니라 30분이요! 근데 오리가 진짜 왔어요!",
                "엄마가 그 문장을 수첩 첫 장에 써 뒀어요. 물에 비친 오리 사진이랑 같이요!",
                "다음엔 친구를 데려올게요. 기다리는 법을 제가 가르칠 거예요, 제가 선생님이니까요!"
            ),
            9000 to 2
        ),
        Episode("gangneung_pine_guest", "gangneung", NpcKind.VILLAGER, "소나무 아래 손님",
            listOf(
                Act(Goal.Talk, gangneungLines.first),
                Act(gangneungGoal, gangneungLines.second),
                Act(Goal.Talk, gangneungLines.third)
            ),
            if (gangneungGoal is Goal.Deliver) "갓 구운 특산 피자 한 판이면 돼요. 식어도 정은 뜨겁죠."
            else "숲새 한 마리 기록이면 돼요. 소나무 숲이 기다리고 있어요.",
            45000 to 8,
            // v0.5 — 「수첩에 한 줄」과 「뒷장」 (말투는 main 의 다듬은 문장을 따랐다)
            "강릉의 소나무 그늘 아래엔 늘 한 자리가 비어 있다",
            listOf(
                "사장님 가게 그 자리, 아직도 비어 있어요. 어제 누가 거기 앉아 소나무 사진만 찍고 갔어요.",
                "피자 한 판 들고 그늘에 앉아 있으면 새가 먼저 와요. 우리가 자리를 잡는 게 아니라니까요.",
                "수첩에 적어 두신 그 한 줄, 저도 카페 메뉴판 옆에 붙여 뒀어요. 손님들이 먼저 읽거든요."
            ),
            12000 to 3
        ),
        Episode("sokcho_snow_window", "sokcho", NpcKind.ELDER, "눈 내리는 창",
            listOf(
                Act(Goal.Talk, listOf(
                    "첫눈 오던 날 기억나네. 창가에서 봤는데, 들녘에 두루미가 내려앉아 있었지.",
                    "눈은 오는 것도 조용하고 가는 것도 조용해. 그 사이에 손님이 왔다 가더라.",
                    "겨울손님 한 마리 기록을 보여주게. 눈 내리는 창의 풍경이란다."
                )),
                Act(Goal.Photo(migration = "겨울"), listOf(
                    "겨울철새면 좋아. 멀리서 온 손님은 발자국도 조심스럽지.",
                    "무리 쪽으로 걷지 말게. 그건 이 동네의 예의이자 손님의 쉼표란다.",
                    "추운 날일수록 새도 에너지를 아낀다네. 멀리서 보는 게 돕는 거야."
                )),
                Act(Goal.Talk, listOf(
                    "고마워… 눈은 찾아오는 것도 보내주는 것도 조용하다니까, 손님도 그렇고.",
                    "내년 겨울에도 이 창에서 손님을 기다리겠네. 그때도 자네 기록이 곁에 있길.",
                    "수첩에 적어 두게. '속초의 첫눈엔 두루미가 내려앉는다'고. 그게 우리 동네의 연말이야."
                ))
            ),
            "겨울손님 한 마리면 돼. 눈이 기다려 주지는 않지만, 마음은 느긋하게.",
            50000 to 9,
            "속초의 첫눈엔 두루미가 내려앉는다",
            listOf(
                "올해 첫눈엔 내가 먼저 창가에 섰다네. 자네가 적어 둔 그 문장대로 말이야.",
                "무리 쪽으로 걸어가지 않았지. 그랬더니 발자국 두 개가 내 사진 뒤까지 오더라네.",
                "내년엔 자네 사진을 동네 안내판에 붙이자고 했다네. 허락 받으러 갈 테니 미리 말해 두네."
            ),
            14000 to 3
        ),
        Episode("daejeon_crossroad", "daejeon", NpcKind.VILLAGER, "사거리의 나침반",
            listOf(
                Act(Goal.Talk, listOf(
                    "우리 동네 사거리는 길이 다섯으로 갈리는데, 옛날에 길 잃은 탐조인을 데려다 준 적이 있어요.",
                    "밤새 한 마리 보겠다고 소문 없는 길로 들어선 걸 보고는, 가슴이 얼얼했죠.",
                    "그래서 약속 하나 했어요. 다시 만나면 이야기를 끝까지 듣기로요."
                )),
                Act(Goal.Talk, listOf(
                    "다시 만나서 이야기를 듣는 것 — 그게 오늘의 심부름이에요. 웃기죠, 심부름이 대화라니.",
                    "길 잃은 사람의 나침반이 되는 건 멀리 갈 필요 없어요. 자리에서 한마디면 되죠.",
                    "사거리 다섯 갈래 길 끝엔 다 다른 풍경이 있어요. 누굴 만나느냐에 따라 길이 달라지죠."
                )),
                Act(Goal.Talk, listOf(
                    "들어 줘서 고마워요. 아, 그 탐조인은 지금 훌륭한 안내자가 됐어요.",
                    "길이 모이는 곳엔 사람도 모이는 법이죠. 우리 동네 별명이 사거리의 나침반이랍니다.",
                    "수첩에 적어 두세요. '대전에선 길을 물으면 사람이 답해 준다'고요."
                ))
            ),
            "편하게 앉아만 계세요. 오늘의 심부름은 대화, 거의 다 끝났어요.",
            25000 to 5,
            "대전에선 길을 물으면 사람이 답해 준다",
            listOf(
                "사거리 떡집 앞이 이제 안내판이 됐어. 누가 길 물어보면 내가 먼저 새 이야기를 한단다.",
                "그 탐조인 동생이 왔더라. 고맙다며 찹쌀떡 한 꾸러미를 두고 갔네.",
                "길은 알려 주는 순간 두 개가 된다더군. 걸어 본 사람이 하나, 물어본 사람이 하나."
            ),
            10000 to 2
        ),
        Episode("jeonju_eaves", "jeonju", NpcKind.ELDER, "한옥의 처마 끝",
            listOf(
                Act(Goal.Talk, listOf(
                    "한옥 살 때는 처마 끝이 최고의 자리였지. 참새도 직박구리도 거기서 하루를 시작하니까.",
                    "천천히 오래 보는 집이 한옥이었어. 서두르면 처마 밑 물방울도 못 보지.",
                    "떠나지 않는 이웃, 텃새 한 마리 기록을 보여주게."
                )),
                Act(Goal.Photo(migration = "텃새"), listOf(
                    "텃새라면 좋아. 사계절 내내 이웃인 셈이지.",
                    "먹이는 내밀지 말게. 거리를 지키는 게 진짜 반갑다는 뜻이란다.",
                    "떠나지 않는 새를 오래 보려면, 나도 떠나지 말고 이 자리에 있어야 하거든."
                )),
                Act(Goal.Talk, listOf(
                    "기와 위에 해바라기 씨앗 두 알이 있었다네. 한 알은 참새가, 한 알은 다음 해 꽃이 됐지.",
                    "오래 볼수록 처마는 넓어진다네. 자네 덕에 오늘도 좀 더 넓어졌어.",
                    "수첩에 적어 두게. '전주의 처마는 천천히 오래 보는 집이다'라고."
                ))
            ),
            "텃새 한 마리 기록이면 된다네. 떠나지 않는 이웃이니 천천히 찾아도 돼.",
            35000 to 7,
            "전주의 처마는 천천히 오래 보는 집이다",
            listOf(
                "기와 사이 해바라기가 올여름 정말로 피었네. 한 알은 꽃이 된다더니 거짓이 없었어.",
                "손님이 처마 밑에서 두 시간 서 있다가 갔어. 아무것도 안 찍었다면서 웃더라네.",
                "자네가 적어 둔 그 문장, 우리 대문 옆에 붙여 뒀네. 천천히 오라는 초상이야."
            ),
            12000 to 3
        ),
        Episode("daegu_mountain_real", "daegu", NpcKind.KID, "팔공산 코알라?",
            listOf(
                Act(Goal.Talk, listOf(
                    "저기요, 큰일 났어요! 친구가 팔공산에 코알라가 산대요! 코알라요, 한국에서요!",
                    "저는 그게 새는 아니지만 새처럼 나무에 산다는… 아무튼 이상하다고 생각했어요.",
                    "산새 한 마리 기록해 주세요! 진짜 팔공산엔 뭐가 사는지 증명해 줘요!"
                )),
                Act(Goal.Photo(habitat = "mountain"), listOf(
                    "산새면 좋아요! 이름도 기억할게요. 코알라 말고 진짜 이름으로요!",
                    "산은 조용해서 소리가 멀리 가요. 그래서 귀가 더 중요하대요.",
                    "소문은 금방 퍼지는데 기록은 천천히 쌓여요. 그게 제가 오늘 배운 거예요!"
                )),
                Act(Goal.Talk, listOf(
                    "역시 코알라는 없었고, 진짜가 훨씬 멋졌어요! 소문은 소문, 기록은 기록!",
                    "다음엔 제가 친구를 직접 데려올 거예요. 이제 저도 안내자예요!",
                    "수첩에 '팔공산엔 코알라 대신 딱다구리가 산다'고 적어 주세요. 친구한테 보여 줄 거예요!"
                ))
            ),
            "산새 한 마리 기록이면 돼요! 코알라 말고요, 진짜 이름으로 부탁해요!",
            30000 to 6,
            "팔공산엔 코알라 대신 딱다구리가 산다",
            listOf(
                "친구한테 그 문장 보여 줬더니! 코알라는 인도에 산대요! 제가 알아낸 거예요!",
                "이제 시장 지붕 제비 집도 제가 기록해요. 날짜랑 날씨도 적어요, 박사님처럼요!",
                "다음엔 친구가 카메라를 빌려 온대요. 근데 새가 놀라지 않게, 제가 먼저 가르쳐 줄게요!"
            ),
            10000 to 2
        ),
        Episode("gwangju_mudeung_wind", "gwangju", NpcKind.VILLAGER, "무등의 바람",
            listOf(
                Act(Goal.Talk, listOf(
                    "무등산 숲엔 보석이 살아요. 여름 숲의 세 보석이라고, 웬만하면 얼굴을 안 보여줘요.",
                    "보석이니까 주머니에 넣는 게 아니라 기록에 넣는 거예요.",
                    "웬만해선 안 보이는 희귀새 한 마리 기록을 보여주시면, 오늘 바람의 기운을 드릴게요."
                )),
                Act(Goal.Photo(tierMin = 3), listOf(
                    "희귀새는 인내의 상대예요. 가까이 가는 게 아니라 오래 기다리는 걸로 이기는 거죠.",
                    "번식철엔 자리를 비켜가는 것도 보석 예절이에요.",
                    "보석을 본 사람은 조용해진대요. 자랑할 게 아니라 고마운 마음이 더 크거든요."
                )),
                Act(Goal.Talk, listOf(
                    "이 기록… 주머니에 넣은 게 아니라 기록에 넣었군요. 제대로 보셨네요.",
                    "무등의 바람이 아무래도 오늘 기분이 좋아 보여요. 고마워요.",
                    "수첩에 적어 두세요. '무등산의 보석은 세 종, 그리고 기다리는 사람'이라고요."
                ))
            ),
            "희귀새 한 마리면 돼요. 도망가는 게 아니라 기다리는 새니까, 마음은 느긋하게.",
            60000 to 10,
            "무등산의 보석은 세 종, 그리고 기다리는 사람",
            listOf(
                "강변에서 기다리는 사람 얘기 나왔어. 셋이 두 시간 서 있다 아무것도 못 보고 돌아왔다더라.",
                "그래도 얼굴들이 밝더군. 기다린 사람은 자꾸 오거든. 우리 동네가 그렇다니까.",
                "보석은 캐는 게 아니라 지켜 주는 거라더군. 자네 문장을 소리 내어 읽어 봤네."
            ),
            15000 to 3
        ),
        Episode("ulsan_ganjeon_boat", "ulsan", NpcKind.ELDER, "간절곶 첫 배",
            listOf(
                Act(Goal.Talk, listOf(
                    "간절곶에선 해가 우리나라에서 제일 먼저 뜬다네. 날도 맨 먼저 밝지.",
                    "근데 첫 배는 아무나 타는 게 아니라네. 기다린 사람이 타는 거지.",
                    "바닷새 한 마리 기록을 가져다주게. 그게 오늘의 조업 일지야."
                )),
                Act(Goal.Photo(habitat = "coast"), listOf(
                    "바닷새는 날씨랑 친하다네. 바람 부는 날이 오히려 큰 날갯짓엔 기회야.",
                    "갯바위는 함부로 밟지 말게. 손님들 현관이니까.",
                    "해 뜨는 걸 서두르면 놓치고, 기다리면 마주친다네. 바다가 그렇게 가르쳐 줬어."
                )),
                Act(Goal.Talk, listOf(
                    "기다린 사람이 첫 기록을 얻는 법이지. 보기 좋네.",
                    "해는 맨날 뜨니까 기회도 맨날 있다네. 다음 일지도 기대하네.",
                    "수첩에 적어 두게. '울산의 아침은 기다린 사람에게 먼저 뜬다'고."
                ))
            ),
            "바닷새 한 마리 기록이면 된다네. 바다는 매일 아침 새 손님을 맞는다네.",
            40000 to 8,
            "울산의 아침은 기다린 사람에게 먼저 뜬다",
            listOf(
                "오늘 그물 걷는데 해가 딱 뜨더라. 기다린 사람 말, 우리 배도 그 규칙대로 움직여.",
                "대숲 백로는 아직이야. 유월쯤 오면 사진 찍으러 와. 우리 자리 알려 줄게.",
                "사장네 진열대 얘기 들었어. 빌려 쓰는 것도 배운 거래. 그래, 그게 정답이지."
            ),
            13000 to 3
        ),
        Episode("busan_gull_dance", "busan", NpcKind.KID, "갈매기 따라 춤을",
            listOf(
                Act(Goal.Talk, listOf(
                    "저저저, 큰일 났어요! 시장에서 괭이갈매기한테 춤을 춰 봤거든요…",
                    "갈매기가 팔짝팔짝 걸어서 저도 따라 했는데, 삼촌들이 다 웃었어요.",
                    "괭이갈매기 기록 부탁해요! 그 새가 웃으면서 걷는지 진지하게 걷는지 확인하고 싶어요!"
                )),
                Act(Goal.Photo(birdId = birdIdOf("괭이갈매기"), habitat = "coast"), listOf(
                    "괭이갈매기는 발이 물갈퀴라며요? 그럼 헤엄도 잘하겠네요!",
                    "먹이로 부르기는 절대 안 돼요. 춤은 제 몸으로만 춰요!",
                    "시장 아주머니가 그러시더라고요. 갈매기는 왔다 가는 손님이라 예의를 지켜야 한대요!"
                )),
                Act(Goal.Talk, listOf(
                    "헐, 진짜 멋있게 날아요. 저게 춤이 아니라 날갯짓이었구나… 제가 뭘 따라 했던 거지?",
                    "다음부턴 새 따라 하지 않고 새 따라가 볼게요. 기록은 기록대로!",
                    "수첩에 '부산 갈매기는 춤추지 않는다. 대신 날아온다'고 적어 주세요!"
                ))
            ),
            "괭이갈매기 한 마리 기록이요! 춤추는 게 아니라 걷는 거예요, 아마도!",
            35000 to 7,
            "부산 갈매기는 춤추지 않는다. 대신 날아온다",
            listOf(
                "시장 아주머니들이 그 문장 보고 웃으셨어요! 근데 진짜예요, 갈매기는 안 추워요! 날아요!",
                "저 이번엔 과자 안 들고 나갔어요. 그랬더니 더 많이 왔어요. 신기하지 않아요?",
                "하구 쪽 가면 오리의 것도 보여요. 다음엔 제가 안내할게요. 세 걸음 물러서서요!"
            ),
            12000 to 3
        ),
        Episode("jeju_stone_wall_winter", "jeju", NpcKind.ELDER, "돌담의 겨울 손님",
            listOf(
                Act(Goal.Talk, listOf(
                    "돌담이 낮아서 손님 얼굴이 바로 보이지. 겨울엔 낯선 날갯소리가 섬에 와 닿아.",
                    "겨울손님은 예의 바르게 왔다네. 가까이 가지 않아도 목례가 오간단다.",
                    "겨울손님 중 얼굴이 또렷한 한 마리 기록을 보여주게."
                )),
                Act(Goal.Photo(migration = "겨울", tierMin = 2), listOf(
                    "섬을 나는 손님은 배고프면 힘들어. 그래서 더 가까이 가지 않는 게 예의란다.",
                    "겨울을 나는 손님 중 좋은 얼굴 한 마리면 된다네.",
                    "돌담이 낮은 건 숨으려는 게 아니라, 목례를 나누려는 거야."
                )),
                Act(Goal.Talk, listOf(
                    "떠나지 않는 새와 사람이 섬을 만든다네. 오늘 자네가 그 문장에 한 사람 늘었어.",
                    "고맙네. 내년 겨울에도 돌담에서 손님을 맞겠어.",
                    "수첩에 적어 두게. '제주에선 겨울도 떠나지 않는 이웃이 된다'고."
                ))
            ),
            "겨울손님 중 좋은 얼굴 한 마리면 된다네. 돌담은 기다리는 법을 아는지라.",
            55000 to 9,
            "제주에선 겨울도 떠나지 않는 이웃이 된다",
            listOf(
                "돌담에 앉은 손님 얼굴을 이제 내가 먼저 알아본다네. 자네가 적어 둔 대로 말야.",
                "마을 회관에 그 문장을 붙였어. 아이들도 읽는다네, '떠나지 않는 이웃'이라는 걸.",
                "섬은 떠나는 사람이 많은 데라 남는 말이 귀하지. 자네 문장이 하나 남았네."
            ),
            16000 to 3
        )
    )

    // -------------------------------------------------------------------
    // 진행 저장/조회 (feat_story_v1)
    // -------------------------------------------------------------------

    private var cache: JSONObject? = null

    private fun read(ctx: Context): JSONObject {
        cache?.let { return it }
        val o = try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(SAVE_KEY, null)?.let { JSONObject(it) } ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        cache = o
        return o
    }

    private fun write(ctx: Context, o: JSONObject) {
        cache = o
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(SAVE_KEY, o.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun progressOf(s: JSONObject, regionId: String): Int = s.optJSONObject("p")?.optInt(regionId, 0) ?: 0

    /**
     * 0=미시작, 1=도입 본 상태, 2=심부름 중, 4=완료, 5=완료 + 「뒷장」까지 읽음.
     * 4를 넘기면 `current`/`hasMarker`/`allComplete` 는 모두 이미 완료로 본다 — 마커가 되살아나지 않는다.
     */
    fun progress(ctx: Context, regionId: String): Int = progressOf(read(ctx), regionId)

    /** 아직 안 읽은 「뒷장」의 수 (본편은 끝났는데 후일담이 남은 동네) */
    fun echoesLeft(ctx: Context): Int =
        EPISODES.count { it.hasEcho && progressOf(read(ctx), it.regionId) == 4 }

    // -------------------------------------------------------------------
    // 「수첩에 한 줄」 — 에피소드를 끝낼 때 실제로 남기는 기록 (`NotebookOverlay` 의 뒷장)
    // -------------------------------------------------------------------

    data class JournalEntry(
        val regionId: String,
        val episodeId: String,
        val title: String,
        val line: String,
        val day: Int
    )

    /** 동네에서 배운 한 줄 — 기록한 순서대로. 같은 동네는 두 번 적지 않는다. */
    fun journal(ctx: Context): List<JournalEntry> {
        val arr = read(ctx).optJSONArray("j") ?: return emptyList()
        val out = ArrayList<JournalEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                JournalEntry(
                    o.optString("r"), o.optString("e"), o.optString("t"),
                    o.optString("l"), o.optInt("d")
                )
            )
        }
        return out
    }

    /** 마무리 막에서 한 줄을 수첩에 남긴다. 이미 그 동네 기록이 있으면 조용히 넘긴다. */
    private fun recordJournal(app: Context, ep: Episode, day: Int) {
        if (ep.journal.isEmpty()) return
        val cur = read(app)
        val arr0 = cur.optJSONArray("j")
        if (arr0 != null) {
            for (i in 0 until arr0.length()) {
                if (arr0.optJSONObject(i)?.optString("r") == ep.regionId) return
            }
        }
        val done = JSONObject(cur.toString())
        val arr = done.optJSONArray("j") ?: JSONArray().also { done.put("j", it) }
        arr.put(
            JSONObject()
                .put("r", ep.regionId).put("e", ep.id).put("t", ep.title)
                .put("l", ep.journal).put("d", day)
        )
        write(app, done)
    }

    /** 이 지역에서 아직 진행 중(완료 아님)인 에피소드. 없으면 null. */
    fun current(ctx: Context, regionId: String): Episode? {
        val ep = EPISODES.firstOrNull { it.regionId == regionId } ?: return null
        return if (progressOf(read(ctx), regionId) >= 4) null else ep
    }

    /**
     * 월드 그리기 경로용 💬 마커 조회 — 매 프레임 호출되므로 메모리 캐시만 건드린다.
     * 에피소드는 그 지역의 **이웃 주민(지역 이야기 담당)** 이 담당한다 — 사람은 한 장소에만 살므로
     * (`NpcRoster`) 종류(kind) 대신 사람을 곧바로 지정한다. `Episode.npc` 는 대사 목소리 참고용.
     */
    fun hasMarker(ctx: Context, regionId: String, person: NpcPerson): Boolean =
        current(ctx, regionId) != null && person.resident && person.regionId == regionId

    private fun allComplete(s: JSONObject): Boolean =
        EPISODES.all { progressOf(s, it.regionId) >= 4 }

    private fun epilogueSeen(s: JSONObject): Boolean = s.optBoolean("epi", false)

    // -------------------------------------------------------------------
    // WorldScene 연결
    // -------------------------------------------------------------------

    /** WorldScene의 현재 문맥으로 [Dialogues.Ctx]를 만든다. */
    fun ctx(ws: WorldScene): Dialogues.Ctx = Dialogues.Ctx(
        ws.map.region.id,
        ws.game.state.mainQuestStage,
        ws.game.state.season(),      // [P01] Season.kt
        ws.game.state.weather(),
        ws.game.state.isNight(),
        ws.game.state.day
    )

    /**
     * WorldScene.talkTo 맨 앞에서 호출 — 사이드 스토리 진행 중이면 대사를 표시하고 소비(기존 대사 스킵).
     * 순서: ① 숨은 에필로그(12편 완료, 1회) ② 이 지역 에피소드의 현재 막 ③ 해당 없으면 false(기존 대사).
     */
    fun intercept(scene: Scene, npc: Npc): Boolean {
        val ws = scene as? WorldScene ?: return false
        val app = ws.game.context
        val state = ws.game.state
        val regionId = ws.map.region.id
        val s = read(app)

        // ① 숨은 에필로그 — 12편 완료 후 아무 NPC와의 첫 대화에서 1회
        if (allComplete(s) && !epilogueSeen(s)) {
            val done = JSONObject(s.toString())
            done.put("epi", true)
            write(app, done)
            ws.openOverlay(
                DialogOverlay(
                    ws, "수첩의 뒷장",
                    "\"수고 많았네. …수첩 뒷장을 펼쳐 보게.\n" +
                        "12개 동네 이야기가 다 적혀 있지. 서울의 창밖부터 제주의 돌담까지, 자네가 걸어온 길이라네.\n" +
                        "이제 자네 이름도 한 줄 적어 두게 — 새를 아는 법을 배운 사람의 자리라네.\n" +
                        "앞장은 할머니 것이고, 뒷장은 자네 것이야.\"",
                    listOf(
                        DialogOverlay.Choice("수첩을 펼쳐 본다") {
                            it.scene.openOverlay(NotebookOverlay(it.scene))
                        },
                        DialogOverlay.Choice("제 이름으로 채워 주세요") {
                            state.money += 30000
                            state.luck = (state.luck + 5f).coerceAtMost(100f)
                            SaveManager.save(ws.game.context, state)
                            ws.game.toast("수첩의 뒷장에 이름이 적혔어요 ${won(30000)} · 행운+5")
                            ws.game.sfx(Audio.Sfx.SPARKLE, 0.6f)
                        }
                    )
                )
            )
            return true
        }

        // ② 이 지역의 에피소드 — 그 지역 이웃 주민(이야기 담당)에게만 열린다
        val ep = EPISODES.firstOrNull { it.regionId == regionId } ?: return false
        if (!(npc.person.resident && npc.person.regionId == regionId)) return false
        val p = progressOf(s, regionId)
        if (p >= 4) {
            // 「뒷장」 — 본편을 끝낸 동네를 다시 찾았을 때 딱 한 번, 그 이웃의 후일담이 열린다
            if (p == 4 && ep.hasEcho) {
                showEcho(ws, app, ep)
                return true
            }
            return false
        }

        if (p == 0) {
            // 도입 — acts[0].goal은 항상 Talk(대화 자체가 첫 막)
            showBeat(ws, ep, 0)
            advance(app, regionId, 1)
            return true
        }

        // 1..2: 이전 막의 목표를 대화 시점에만 검사
        val prev = ep.acts[p - 1]
        if (!goalMet(state, prev.goal)) {
            showNudge(ws, ep)
            return true
        }
        consumeDeliverable(state, prev.goal)   // 납품형이면 여기서 1개 소모
        if (p == 2) {
            // 마무리 + 보상 + 완료 (재방문 시 반복 없음)
            showBeat(ws, ep, 2) {
                state.money += ep.reward.first
                state.luck = (state.luck + ep.reward.second).coerceAtMost(100f)
                recordJournal(app, ep, state.day)
                SaveManager.save(ws.game.context, state)
                val jotted = if (ep.journal.isNotEmpty()) " · 수첩에 한 줄 남김" else ""
                ws.game.toast("${ep.title} 완결! ${won(ep.reward.first)} · 행운+${ep.reward.second}$jotted")
                ws.game.sfx(Audio.Sfx.SPARKLE, 0.6f)
            }
            advance(app, regionId, 4)
        } else {
            showBeat(ws, ep, p)
            advance(app, regionId, p + 1)
        }
        return true
    }

    // -------------------------------------------------------------------
    // 목표 판정 (대화 시점에만)
    // -------------------------------------------------------------------

    private fun goalMet(s: GameState, g: Goal): Boolean = when (g) {
        is Goal.Photo -> s.birdCounts.any { (id, n) -> n > 0 && photoMatch(id, g) }
        is Goal.Deliver -> findDeliverable(s, g) >= 0
        Goal.Talk -> true
    }

    private fun photoMatch(id: String, g: Goal.Photo): Boolean {
        val def = Birds.byId[id] ?: return false
        if (g.birdId != null && id != g.birdId) return false
        if (g.habitat != null && g.habitat !in def.habitats) return false
        if (g.migration != null && !def.migrationLabel.contains(g.migration)) return false
        if (def.tier.star < g.tierMin) return false
        return true
    }

    /** 납품 가능한 특산 피자 슬롯(id≥12)의 인덱스. 없으면 -1. */
    private fun findDeliverable(s: GameState, g: Goal.Deliver): Int {
        val min = maxOf(12, g.pizzaIdMin)
        for (def in Pizzas.ALL) {
            if (def.id < min) continue
            for (q in 0..2) {
                val idx = def.id * 3 + q
                if (idx < s.pizzas.size && s.pizzas[idx] > 0) return idx
            }
        }
        return -1
    }

    private fun consumeDeliverable(s: GameState, g: Goal) {
        if (g !is Goal.Deliver) return
        val idx = findDeliverable(s, g)
        if (idx >= 0) s.pizzas[idx] = s.pizzas[idx] - 1
    }

    // -------------------------------------------------------------------
    // 대사 표시
    // -------------------------------------------------------------------

    private fun showBeat(ws: WorldScene, ep: Episode, actIdx: Int, onDone: () -> Unit = {}) {
        val act = ep.acts[actIdx]
        val choiceLabel = when (actIdx) {
            0 -> "네, 이야기 들려주세요"
            1 -> "잘 다녀오겠습니다"
            else -> "감사합니다"
        }
        val jotted = if (actIdx == 2 && ep.journal.isNotEmpty()) "\n\n" +
            "(수첩에 한 줄 적었어요) '${ep.journal}'" else ""
        ws.openOverlay(
            DialogOverlay(
                ws, ep.title,
                "\"${act.lines.joinToString("\n")}\"$jotted",
                listOf(DialogOverlay.Choice(choiceLabel) { onDone() })
            )
        )
    }

    /**
     * 「뒷장」 — 에피소드를 끝낸 동네를 다시 찾았을 때 딱 한 번 여는 3줄 후일담.
     * 배운 한 줄이 동네에서 어떻게 쓰이는지까지 보여 주고, 본편보다 작은 답례가 따른다.
     * `progress` 를 4 → 5로 올려 다시 열리지 않게 한다 (완료·마커 판정은 그대로 4 이상).
     */
    private fun showEcho(ws: WorldScene, app: Context, ep: Episode) {
        val echoLines = ep.echo.joinToString("\n")
        ws.openOverlay(
            DialogOverlay(
                ws, ep.title + " · 뒷장",
                "\"" + echoLines + "\"",
                listOf(
                    DialogOverlay.Choice("또 올게요") {
                        val st = ws.game.state
                        if (ep.echoReward.first > 0 || ep.echoReward.second > 0) {
                            st.money += ep.echoReward.first
                            st.luck = (st.luck + ep.echoReward.second).coerceAtMost(100f)
                            SaveManager.save(ws.game.context, st)
                            ws.game.toast(
                                "동네의 답례 " + won(ep.echoReward.first) +
                                    " · 행운+" + ep.echoReward.second
                            )
                        }
                        ws.game.sfx(Audio.Sfx.REWARD, 0.55f)
                        advance(app, ep.regionId, 5)
                    }
                )
            )
        )
    }

    private fun showNudge(ws: WorldScene, ep: Episode) {
        ws.openOverlay(
            DialogOverlay(
                ws, ep.title,
                "\"${ep.nudge}\"",
                listOf(DialogOverlay.Choice("다시 올게요"))
            )
        )
    }

    private fun advance(app: Context, regionId: String, value: Int) {
        val s = read(app)
        val done = JSONObject(s.toString())
        val p = done.optJSONObject("p") ?: JSONObject().also { done.put("p", it) }
        p.put(regionId, value)
        write(app, done)
    }
}
