package com.pizzaandbird.game

import kotlin.math.ln

/**
 * [P02] 출현 가중치 테이블 — 계절 × 지역 × 시간대 출현/희귀도 고도화.
 *
 * 월드씬 스폰이 종 하나의 최종 가중치를 얻는 단일 진입점([SpawnTables.weight])과
 * "이 계절 이 지역의 대표 새"를 뽑는 [SpawnTables.highlights]를 제공한다.
 *
 * - 큐레이션 30종은 §5(계획서)의 계절 배율 초안을 종별 표로 오버라이드한다.
 *   숫자는 README 탐조지 표의 "추천 시기"를 그대로 옮긴 것이다.
 * - 나머지 공식종은 과(family)·이름 규칙 엔진이 순서대로 적용해 배율을 합성한다.
 * - 희귀(3★) 이상은 0.25~4.0, 그 외는 0.05~3.0으로 클램프해
 *   절대 확률 급등/소멸을 막는다 — "계절을 맞추면 찾기 쉬워진다"는 설계.
 * - 밤낮 조건은 기존 로직(`Birds.poolFor`의 active/timeWindows) 소관 —
 *   이 배율은 계절·지역에만 관여한다 (계약 시그니처의 `night`는 소비자 호환용).
 * - `Data.kt` · `BirdChecklist.kt`는 읽기만 한다 (충돌 방지 규칙 4).
 *
 * 소비자: WorldScene(`trySpawnBird`), P8(지역 대사용 `highlights`).
 */
object SpawnTables {

    /** 봄·여름·가을·겨울 4계절 배율 */
    private class Seasonal(val spring: Double, val summer: Double, val autumn: Double, val winter: Double) {
        fun of(s: Season): Double = when (s) {
            Season.SPRING -> spring
            Season.SUMMER -> summer
            Season.AUTUMN -> autumn
            Season.WINTER -> winter
        }
    }

    private fun seasonal(sp: Double, su: Double, au: Double, wi: Double) = Seasonal(sp, su, au, wi)

    // -----------------------------------------------------------------------
    // 1) 큐레이션 30종 오버라이드 표 (계획서 §5 초안)
    // -----------------------------------------------------------------------

    private val OVERRIDE: Map<String, Seasonal> = mapOf(
        // 두루미류 — 철원·순천만 월동지 11~2월
        "crane" to seasonal(0.15, 0.10, 0.60, 2.0),        // 두루미
        "redcrown" to seasonal(0.15, 0.10, 0.60, 2.0),     // 재두루미
        "hoodedcrane" to seasonal(0.15, 0.10, 0.60, 2.0),  // 흑두루미
        // 여름 번식조
        "pitta" to seasonal(0.3, 2.0, 0.4, 0.05),          // 팔색조
        // 도요·물떼새 — 봄·가을 나눔터·간월암 통과
        "greatknot" to seasonal(1.8, 0.7, 1.8, 0.4),       // 붉은어깨도요
        "oystercatcher" to seasonal(1.8, 0.7, 1.8, 0.4),   // 검은머리물떼새
        // 기러기·고니 — 겨울 월동
        "beangoose" to seasonal(0.2, 0.1, 0.8, 2.0),       // 큰기러기
        "whooperswan" to seasonal(0.2, 0.1, 0.8, 2.0),     // 큰고니
        "baikalteal" to seasonal(0.2, 0.1, 0.8, 2.0),      // 가창오리
        // 저어새 — 번식 여름~초가을
        "spoonbill" to seasonal(1.5, 1.2, 1.0, 0.3),
        // 겨울 오리 테마
        "spotduck" to seasonal(0.9, 0.5, 1.2, 1.5),        // 청둥오리
        "tufteduck" to seasonal(0.9, 0.5, 1.2, 1.5),       // 쇠오리
        "mandarin" to seasonal(0.9, 0.5, 1.2, 1.5),        // 원앙
        // 왕피천 가을 연어 — 물수리
        "osprey" to seasonal(0.6, 0.4, 1.6, 1.0),
        // 여름조
        "cuckoo" to seasonal(1.2, 1.8, 0.5, 0.05),         // 뻐꾸기
        "reedwarbler" to seasonal(1.2, 1.8, 0.5, 0.05),    // 개개비
        // 올빼미·부엉이류 — 텃새성
        "owl" to seasonal(1.0, 1.0, 1.0, 1.2),             // 올빼미
        "eagleowl" to seasonal(1.0, 1.0, 1.0, 1.2),        // 수리부엉이
        // 딱따구리류 — 텃새
        "woodpecker" to seasonal(1.2, 1.0, 1.0, 1.0),      // 까막딱다구리
        "greenwood" to seasonal(1.2, 1.0, 1.0, 1.0),       // 청딱다구리
        // 맹금류 — 겨울 관찰 용이
        "kestrel" to seasonal(1.0, 0.9, 1.1, 1.2),         // 황조롱이
        "kite" to seasonal(1.0, 0.9, 1.1, 1.2),            // 말똥가리
        "goshawk" to seasonal(1.0, 0.9, 1.1, 1.2),         // 참매
        // 흔함 텃새 — 기본 유지
        "sparrow" to seasonal(1.0, 1.0, 1.0, 1.0),         // 참새
        "bulbul" to seasonal(1.0, 1.0, 1.0, 1.0),          // 직박구리
        "magpie" to seasonal(1.0, 1.0, 1.0, 1.0),          // 까치
        "greattit" to seasonal(1.0, 1.0, 1.0, 1.0),        // 박새
        "dove" to seasonal(1.0, 1.0, 1.0, 1.0),            // 멧비둘기
        // 따오기·황새 — 우포·철원 복원 개체 암시
        "crestedibis" to seasonal(1.2, 0.8, 0.8, 1.3),     // 따오기
        "stork" to seasonal(1.2, 0.8, 0.8, 1.3)            // 황새
    )

    // -----------------------------------------------------------------------
    // 2) 일반종 규칙 엔진 — 오버라이드가 없으면 순서대로 첫 매치를 적용
    // -----------------------------------------------------------------------

    /** 나그네새(도요·물떼새) — 봄·가을 ×1.6 */
    private val SHORE_FAMILIES = setOf("도요과", "물떼새과", "검은머리물떼새과", "장다리물떼새과")

    /** 오리·기러기·고니류 — 겨울 ×1.8, 여름 ×0.25 (셋 다 오리과 — 별도 과명은 없다) */
    private val WATERFOWL_FAMILIES = setOf("오리과")

    /**
     * 여름조 — 여름 ×1.6, 겨울 ×0.1 (제비·꾀꼬이류가 원형).
     * 값은 P1(Season.kt)이 실제 체크리스트에서 검증한 과 이름을 따른다.
     */
    private val SUMMER_MIGRANT_FAMILIES = setOf(
        "제비과", "꾀꼬리과", "두견이과", "파랑새과", "물총새과", "팔색조과",
        "솔딱새과", "휘파람새과", "개개비과", "때까치과", "쏙독새과",
        "칼새과", "긴꼬리딱새과"
    )

    /** 규칙 엔진 — 표에 없는 공식종의 계절 배율 (일치하는 규칙이 없으면 사계 1.0: 텃새·올빼미류) */
    private fun ruleSeasonal(def: BirdDef, s: Season): Double {
        val fam = def.familyName
        val nm = def.name
        return when {
            fam in SHORE_FAMILIES || nm.contains("도요") || nm.contains("물떼새") ->
                if (s == Season.SPRING || s == Season.AUTUMN) 1.6 else 1.0
            fam in WATERFOWL_FAMILIES || nm.contains("기러기") || nm.contains("고니") ->
                when (s) {
                    Season.WINTER -> 1.8
                    Season.SUMMER -> 0.25
                    else -> 1.0
                }
            fam in SUMMER_MIGRANT_FAMILIES ->
                when (s) {
                    Season.SUMMER -> 1.6
                    Season.WINTER -> 0.1
                    else -> 1.0
                }
            else -> 1.0
        }
    }

    // -----------------------------------------------------------------------
    // 3) 지역 상성 배율 (계획서 §5: 미세 조정 — 예: 수리부엉이 = 산/숲 ×1.5, 도시 ×0.3)
    // -----------------------------------------------------------------------

    private val RAPTOR_NAMES = setOf(
        "황조롱이", "말똥가리", "참매", "수리부엉이", "흰꼬리수리",
        "독수리", "참수리", "조롱이", "물수리"
    )

    private fun isRaptor(def: BirdDef): Boolean = def.name in RAPTOR_NAMES

    private fun isWaterbird(def: BirdDef): Boolean =
        def.familyName in SHORE_FAMILIES || def.familyName in WATERFOWL_FAMILIES ||
            def.familyName == "두루미과" ||
            def.familyName == "뜸부기과" || def.familyName == "논병아리과" ||
            def.name.contains("두루미") || def.name.contains("해오라기") ||
            def.name.contains("백로") || def.name.contains("가마우지") ||
            def.name.contains("갈매기") || def.name.contains("황새") ||
            def.name.contains("따오기")

    private fun regionAffinity(def: BirdDef, region: RegionDef?): Double {
        if (region == null) return 1.0
        return when {
            isRaptor(def) -> when (region.kind) {
                RegionKind.MOUNTAIN -> 1.5
                RegionKind.TOWN -> 0.3
                else -> 1.0
            }
            isWaterbird(def) -> when (region.kind) {
                RegionKind.WETLAND, RegionKind.RIVER, RegionKind.COAST -> 1.3
                RegionKind.TOWN -> 0.6
                else -> 1.0
            }
            else -> 1.0
        }
    }

    // -----------------------------------------------------------------------
    // 4) 공개 API
    // -----------------------------------------------------------------------

    /**
     * 월드씬 스폰이 종 하나의 최종 가중치를 얻는 단일 진입점. [WorldScene.trySpawnBird]가
     * `def.weight × 행운 × 날씨 × 계절(철새) × 이 배율` 로 곱해 쓴다.
     *
     * @param def 종 (Data.kt는 읽기만)
     * @param regionId Regions id
     * @param habitats 해당 지역 서식지 풀 (regionPool 형태와 호환)
     * @param season [P01] 계절
     * @param night 밤낮은 poolFor/active가 처리 — 이 배율은 계절·지역 전용 (계약 시그니처 유지)
     * @return 최종 스폰 배율 (0.05~3.0, 희귀+는 0.25~4.0 클램프)
     */
    fun weight(
        def: BirdDef,
        regionId: String,
        habitats: Set<String>,
        season: Season,
        night: Boolean
    ): Double {
        // 계절: 종별 오버라이드 표 → 없으면 규칙 엔진
        var m = OVERRIDE[def.id]?.of(season) ?: ruleSeasonal(def, season)

        // 서식지 상성: 지역 풀에 없는 서식지 전속 종은 ×0.2 (완전 0은 아니게 — 예외적 만남)
        if (def.habitats.none { it in habitats }) m *= 0.2

        // 지역 상성 (산/숲 맹금, 물가 새 미세 조정)
        m *= regionAffinity(def, Regions.byId[regionId])

        // 희귀도 보호 + 보정 상한
        return if (def.tier.star >= 3) m.coerceIn(0.25, 4.0) else m.coerceIn(0.05, 3.0)
    }

    /**
     * 이 계절 이 지역의 대표 새 (P8 대사·추후 도감 힌트용).
     * 계절 배율 × 등급 × 기본 희귀도(로그) 순으로 정렬해 상위 4종을 돌려준다.
     */
    fun highlights(regionId: String, s: Season): List<BirdDef> {
        val region = Regions.byId[regionId] ?: return emptyList()
        val pool = Birds.poolFor(region, night = false)
        if (pool.isEmpty()) return emptyList()
        return pool
            .map { def ->
                // 계절 배율 × 등급(제곱 — 대표 새는 희귀할수록) × 기본 희귀도(로그)
                def to (weight(def, regionId, region.habitats, s, false) * def.tier.star * def.tier.star * ln(def.weight + 1.0))
            }
            .sortedWith(
                compareByDescending<Pair<BirdDef, Double>> { it.second }
                    .thenByDescending { it.first.tier.star }
            )
            .map { it.first }
            .take(4)
    }
}
