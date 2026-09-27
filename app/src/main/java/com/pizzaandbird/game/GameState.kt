package com.pizzaandbird.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 플레이어 진행 상황. 오프라인 저장(JSON in SharedPreferences).
 *
 * 세이브 형식 v5: 8칸 집 꾸미기 레이아웃/세트 효과와 방향·자세·지역·시간·날씨가
 * 포함된 촬영 사진집 메타데이터를 추가했다. 자전거 모델·도색·부속품 커스텀 + 메인 스토리 진행도/완료 상태 +
 *   화면 연출(몰입 카메라) 설정 + 피자 배열 확장([피자id*3 + 품질], 12종 = 화덕피자 6 + 일반 피자 6).
 *   (v2/v3의 9칸 피자 배열 = 치즈/버섯/불고기 → 같은 id를 유지하므로 앞 9칸에 그대로 들어간다)
 * v3: 인테리어 스타일·지역별 집 소유권과 탐조가 레벨/경험치/숙련 포인트/스킬을 추가했다.
 * v2 (v0.2.0): 피자 토핑/장식/낮밤 시각/최고 별점 추가.
 * v1~v3 세이브는 자동으로 마이그레이션된다. (없는 필드는 기본값)
 */
class GameState {

    var started = false            // 첫 집 선택 완료(=세이브 존재)
    var gender = "male"          // 플레이어 캐릭터: male / female
    var inHome = false             // 현재 집 안에 있는지
    var money = 0                  // 용돈(원)
    var hunger = 100f              // 배고픔 수치 (100 = 포만, 0 = 배고픔)
    var luck = 50f                 // 행운 수치 (높을수록 희귀새 출현)
    val pizzas = IntArray(Pizzas.ALL.size * 3)   // [피자id*3 + 품질] 피자 개수

    // 카메라 장비 -------------------------------------------------------
    val ownedGear = LinkedHashSet<String>()     // 구매한 장비 id 전체
    var useIlc = false                          // true = 렌즈교환식, false = 컴팩트
    var compactId = CameraGear.STARTER          // 장착한 컴팩트
    var bodyId: String? = null                  // 장착한 바디
    var lensId: String? = null                  // 장착한 렌즈
    var tcId: String? = null                    // 장착한 텔레컨버터
    val birdCounts = LinkedHashMap<String, Int>()   // 도감: 새별 촬영 횟수
    val bestStars = LinkedHashMap<String, Int>()    // 도감: 새별 최고 별점
    val photoAlbum = ArrayList<BirdPhotoRecord>()   // 사진집: 실제 지형+방향+자세가 남은 촬영본
    val visited = LinkedHashSet<String>()           // 방문한 지역
    val landmarksSeen = LinkedHashSet<String>()     // 관람을 마친 지역 랜드마크
    val ownedHomes = LinkedHashSet<String>()        // 매입한 지역별 집
    val ownedHouseStyles = LinkedHashSet<String>()  // 구매한 인테리어 스타일

    var homeRegion = START_REGION_ID       // 집이 있는 지역
    var region = START_REGION_ID           // 현재 지역
    var houseStyleId = "cozy"              // 현재 집 인테리어
    var px = 0f                    // 월드 좌표(px)
    var py = 0f
    var onBike = false

    var questBird: String? = null  // 박사 사진 의뢰(서브퀘스트): 촬영할 새
    var questReward = 0

    // 메인 스토리. 사진 의뢰와 독립적이므로 어느 쪽이든 언제든 진행할 수 있다.
    var mainQuestStarted = false
    var mainQuestStage = 0
    var mainQuestFinished = false

    var playSeconds = 0f
    var photos = 0                 // 누적 촬영 장수
    var worldTime = 8.5f           // 게임 내 시각 (0.0~24.0, 8.5=오전 8시반)
    var day = 1                    // 게임 내 날짜 (자정을 넘기거나 잠들면 +1 — 날씨가 바뀐다)
    var weatherId = Weather.SUNNY.id // 게임 전체 날씨
    var weatherSeconds = 55f         // 다음 날씨 변화까지 남은 시간

    // 탐조가 성장 --------------------------------------------------------
    var level = 1                  // 캐릭터 레벨 (1~MAX_LEVEL)
    var exp = 0                    // 현재 레벨에서 쌓은 경험치
    var skillPoints = 0            // 사용 가능한 숙련 포인트(SP)
    val skills = LinkedHashMap<String, Int>()   // 스킬id -> 랭크

    var musicOn = true             // 설정: 배경 음악
    var sfxOn = true               // 설정: 효과음/환경음

    // 화질 설정 (2K 렌더링) ------------------------------------------------
    /** 월드 렌더 배율: "auto"(화면 높이에 맞춤, 최대 3×) / "1" / "2" / "3" */
    var renderScale = "auto"
    /** 화면 출력 보간 — false: 픽셀 느낌(선명, 기본) · true: 부드러운 보간 */
    var smoothScreen = false

    /** 집 장식 칸 (장식 id, -1 = 빈칸). v5부터 8칸이며, 예전 3칸 세이브는 앞 칸에 그대로 옮긴다. */
    val decorSlots = IntArray(Decors.SLOT_COUNT) { -1 }
    val decorOwned = ArrayList<Int>()     // 소유한 장식 id 목록

    // 🌸 힐링 컨텐츠 상태 (v0.4.2 「따뜻한 바람」) — 기본 JSONObject로 세이브/로드가 투명하다.
    var healing = JSONObject()


    // 자전거 (탈것은 자전거만!) ------------------------------------------
    val ownedBikes = LinkedHashSet<String>()      // 소유한 자전거 모델 id
    var bikeId = "basic"                          // 장착 중인 자전거 모델
    var bikeFrameColor = 0                        // 프레임 도색 (BikeColors.FRAME 인덱스)
    var bikeTireColor = 0                         // 바퀴 색 (BikeColors.TIRE 인덱스)
    var bikeSaddleColor = 0                       // 안장·그립 색 (BikeColors.SADDLE 인덱스)
    val ownedBikeParts = LinkedHashSet<String>()  // 장착한 부속품 id (구매=장착)

    // ----- 조작 설정 (진행 상황이 아닌 개인 설정 — '처음부터 다시' 해도 유지) -----
    var floatStick = true        // 움직이는 조이스틱: 왼쪽 아래를 드래그하면 그 자리에 스틱
    var analogStick = true       // 아날로그 이동: 스틱을 민 만큼 속도 조절

    // 화면 연출 (몰입 카메라) — 멀미(3D Motion Sickness)에 민감하면 끌 수 있다 ----------
    var camShake = 2           // 카메라 흔들림 0 끔 / 1 약하게 / 2 보통 / 3 강하게
    var camBob = true          // 헤드 밥 & 바디 스웨이 (걸음 주기 출렁임)
    var camBlur = true         // 잔상 & 속도선 (모션 블러 느낌)
    var camFov = true          // 다이내믹 시야각 (달리기 광각 / 카메라 모드 망원)
    var camDof = true          // 다이내믹 포커싱 (심도 — 초점 밖 어둡게)
    var camLead = true         // 예측 배치 (진행 방향 앞쪽을 더 보여주기)

    // ------------------------------------------------------------------

    val pizzaCount: Int get() = pizzas.sum()

    private fun pizzaIdx(pizzaId: Int, quality: Int): Int =
        pizzaId.coerceIn(0, Pizzas.ALL.size - 1) * 3 + quality.coerceIn(0, 2)

    /** 특정 피자(품질 무관) 개수 */
    fun pizzaCountOf(pizzaId: Int): Int {
        var n = 0
        for (q in 0 until 3) n += pizzas[pizzaIdx(pizzaId, q)]
        return n
    }

    fun pizzaCountOf(pizzaId: Int, quality: Int): Int = pizzas[pizzaIdx(pizzaId, quality)]

    /** 계열(화덕피자/일반 피자)별 개수 */
    fun pizzaCountOfKind(kind: PizzaKind): Int {
        var n = 0
        for (p in Pizzas.ALL) if (p.kind == kind) n += pizzaCountOf(p.id)
        return n
    }

    fun addPizza(pizzaId: Int, quality: Int): Boolean {
        if (pizzaCount >= pizzaCapEff()) return false
        pizzas[pizzaIdx(pizzaId, quality)]++
        return true
    }

    /** 특정 피자의 가장 좋은 품질부터 먹기 (없으면 null) */
    fun eat(pizzaId: Int): PizzaQ? {
        val p = Pizzas.of(pizzaId)
        for (q in 2 downTo 0) {
            val idx = pizzaIdx(p.id, q)
            if (pizzas[idx] > 0) {
                pizzas[idx]--
                val def = PizzaQ.of(q)
                hunger = (hunger + def.hunger + p.hungerBonus).coerceIn(0f, 100f)
                luck = (luck + def.luck + p.luckBonus).coerceIn(0f, 100f)
                return def
            }
        }
        return null
    }

    /**
     * 아무 피자나 가장 좋은 것부터 먹기 (먹은 피자 id 반환, 없으면 null).
     * 같은 품질이면 배고픔 회복이 큰 피자를 먼저 먹는다 (간식 버튼용).
     */
    fun eatBest(): Int? {
        for (q in 2 downTo 0) {
            var best: PizzaDef? = null
            for (p in Pizzas.ALL) {
                if (pizzas[pizzaIdx(p.id, q)] > 0 && (best == null || p.hungerBonus > best.hungerBonus)) best = p
            }
            if (best != null) {
                eat(best.id)
                return best.id
            }
        }
        return null
    }

    // ------------------ 카메라 장비 ------------------

    private var rigCache: CameraRig? = null
    private var rigKey: String = ""

    private fun gearKey(): String =
        "$useIlc|$compactId|$bodyId|$lensId|$tcId|" + ownedGear.filter { it.startsWith("acc_") }.sorted().joinToString(",")

    /** 소유한 액세서리 목록 */
    fun accessories(): Set<String> =
        ownedGear.filterTo(LinkedHashSet()) { CameraGear.accessory(it) != null }

    fun ownsGear(id: String): Boolean = id in ownedGear

    fun hasAdapter(): Boolean = CameraGear.ACC_ADAPTER in ownedGear

    /** 현재 장착 중인 카메라(조합)의 종합 성능 */
    fun rig(): CameraRig {
        val key = gearKey()
        val cached = rigCache
        if (cached != null && key == rigKey) return cached
        val acc = accessories()
        val body = CameraGear.body(bodyId)
        val lens = CameraGear.lens(lensId)
        var tc = CameraGear.tc(tcId)
        if (lens != null && !lens.tcOk) tc = null
        val rig = if (useIlc && body != null && lens != null &&
            CameraGear.canMount(body, lens, hasAdapter())
        ) {
            CameraRigs.fromIlc(body, lens, tc, acc)
        } else {
            val cam = CameraGear.compact(compactId) ?: CameraGear.COMPACTS.first()
            CameraRigs.fromCompact(cam, acc)
        }
        rigCache = rig
        rigKey = key
        return rig
    }

    /** 장비 변경 후 캐시 무효화 */
    fun invalidateRig() {
        rigCache = null
        rigKey = ""
    }

    /** 렌즈교환식 조합이 실제로 성립하는지 */
    fun ilcReady(): Boolean {
        val b = CameraGear.body(bodyId) ?: return false
        val l = CameraGear.lens(lensId) ?: return false
        return CameraGear.canMount(b, l, hasAdapter())
    }

    /** 장비 총 무게(g) */
    fun gearWeight(): Int = rig().weightG

    /** 무게로 인한 이동 속도 배율 (무거울수록 느려진다) */
    fun gearSpeedMult(): Float {
        val w = gearWeight()
        val relief = if (CameraGear.ACC_STRAP in ownedGear) 0.8f else 1f
        return (1f - ((w - 500).coerceAtLeast(0) / 14000f) * relief).coerceIn(0.78f, 1f)
    }

    /** 무게로 인한 배고픔 가중치 */
    fun gearHungerMult(): Float {
        val w = gearWeight()
        val relief = if (CameraGear.ACC_STRAP in ownedGear) 0.8f else 1f
        return (1f + ((w - 500).coerceAtLeast(0) / 7000f) * relief).coerceIn(1f, 1.75f)
    }

    /** 위장 블라인드 — 새가 덜 도망간다 */
    fun gearFleeMult(): Float = if (CameraGear.ACC_BLIND in ownedGear) 0.82f else 1f

    /** 장비가 주는 행운 보너스 */
    fun gearLuck(): Int = rig().luck

    /**
     * 지금 얼마나 어두운가 (0 = 한낮, 1 = 한밤중).
     * 저조도 노이즈·셔터 속도 판정과 뷰파인더 EXIF 표시에 함께 쓰인다.
     */
    fun darkness(): Float {
        val h = worldTime
        var d = when {
            h >= 7f && h < 16.5f -> 0f
            h >= 6f && h < 7f -> 0.35f
            h >= 16.5f && h < 18f -> 0.35f
            h >= 18f && h < 19.5f -> 0.65f
            h >= 4.5f && h < 6f -> 0.6f
            else -> 1f
        }
        d += when (weather()) {
            Weather.RAIN -> 0.32f
            Weather.SNOW -> 0.26f
            Weather.CLOUDY -> 0.18f
            Weather.WIND -> 0.08f
            else -> 0f
        }
        return d.coerceIn(0f, 1f)
    }

    // ------------------ 탐조가 성장 ------------------

    /** 현재 레벨에서 다음 레벨까지 필요한 경험치 */
    fun expToNext(): Int = Progression.expToNext(level)

    /** 다음 레벨까지의 진행도 (0~1) */
    fun expProgress(): Float {
        val need = expToNext()
        return if (need <= 0) 1f else (exp.toFloat() / need).coerceIn(0f, 1f)
    }

    fun title(): String = Progression.title(level)

    fun gearTier(): Int = Progression.gearTier(level)

    /**
     * 경험치를 더한다. 레벨업이 발생하면 오른 레벨 수를 반환(없으면 0).
     * 레벨업 시 숙련 포인트가 자동 지급된다.
     */
    fun addExp(amount: Int): Int {
        if (amount <= 0 || level >= Progression.MAX_LEVEL) return 0
        exp += amount
        var gained = 0
        while (level < Progression.MAX_LEVEL) {
            val need = Progression.expToNext(level)
            if (need <= 0 || exp < need) break
            exp -= need
            level++
            gained++
            skillPoints += Progression.skillPointsFor(level)
        }
        if (level >= Progression.MAX_LEVEL) exp = 0
        return gained
    }

    /** 스킬 현재 랭크 */
    fun skillRank(id: String): Int = skills[id] ?: 0

    /** 스킬 강화 (SP 소모). 성공하면 true */
    fun upgradeSkill(id: String): Boolean {
        val def = Skills.of(id) ?: return false
        val cur = skillRank(id)
        if (cur >= def.maxRank) return false
        if (skillPoints <= 0) return false
        skillPoints--
        skills[id] = cur + 1
        return true
    }

    // 스킬 효과 --------------------------------------------------------
    /** 이동 속도 배율 (튼튼한 다리 + 장비 무게) */
    fun speedMult(): Float = (1f + 0.06f * skillRank("legs")) * gearSpeedMult()

    /** 피자 최대 소지 개수 (넉넉한 배낭 + 자전거 부속품) */
    fun pizzaCapEff(): Int = PIZZA_CAP + skillRank("pack") + bikePizzaBonus()

    /** 새 도망 반경 배율 (고요한 발걸음 + 위장 블라인드) — 작을수록 가까이 갈 수 있음 */
    fun fleeMult(): Float =
        ((1f - 0.08f * skillRank("quiet")) * gearFleeMult()).coerceAtLeast(0.42f)

    /** 배고픔 감소 배율 (튼튼한 체력 + 장비 무게) */
    fun hungerMult(): Float =
        ((1f - 0.10f * skillRank("stamina")).coerceAtLeast(0.4f)) * gearHungerMult()

    /** 행운 자연 감소 배율 (타고난 행운) */
    fun luckDecayMult(): Float = (1f - 0.20f * skillRank("lucky")).coerceAtLeast(0.2f)

    /** 행운 하한 (타고난 행운) */
    fun luckFloor(): Float = 5f * skillRank("lucky")

    /** 사진에서 별 하나 더 얻을 추가 확률 (매의 눈) */
    fun extraStarChance(): Double = 0.07 * skillRank("sharp")

    /** 지금까지 획득한 숙련 포인트 총량 (사용 + 보유) */
    fun skillInvested(): Int = skillPoints + skills.values.sum()

    /** 사진용품점 '탐조 강습' 비용 — 살수록 비싸진다 (돈으로 SP 구매) */
    fun trainingCost(): Int = 800 + skillInvested() * 500

    // ------------------ 낮/밤 ------------------

    /** 게임 시계를 dt초만큼 진행 (자정을 넘기면 날짜 +1) */
    fun advanceClock(dt: Float) {
        worldTime += dt * 24f / DAY_SECONDS
        while (worldTime >= 24f) {
            worldTime -= 24f
            day += 1
        }
    }

    /** 침대에서 자고 아침 7:12에 일어남 (자정 전에 잤다면 다음 날) */
    fun sleepUntilMorning() {
        if (worldTime > 7.2f) day += 1
        worldTime = 7.2f
    }

    /** 밤(올빼미 등 밤새 출현) 여부 */
    fun isNight(): Boolean = worldTime >= 19.5f || worldTime < 4.5f

    fun timeLabel(): String {
        val h = worldTime.toInt().coerceIn(0, 23)
        val m = (((worldTime - h) * 60f).toInt().coerceIn(0, 59))
        return String.format("%02d:%02d", h, m)
    }

    fun timeEmoji(): String = when {
        worldTime >= 7f && worldTime < 17f -> "☀"
        worldTime >= 17f && worldTime < 19.5f -> "🌆"
        else -> if (worldTime >= 4.5f) "🌅" else "🌙"
    }

    // ------------------ 장식 ------------------

    /** 현재 적용 중인 인테리어 스타일 */
    fun houseStyle(): HouseStyle = HouseStyles.of(houseStyleId)

    fun ownsHome(regionId: String): Boolean = regionId in ownedHomes

    /** 현재 배치된 장식 id. 중복·알 수 없는 id를 배제해 오래된 세이브도 안전하게 처리한다. */
    fun placedDecorIds(): Set<Int> = decorSlots.filter { Decors.of(it) != null }.toSet()

    /** 소품 자체가 주는 행운. */
    fun decorItemLuck(): Int = placedDecorIds().sumOf { Decors.of(it)?.luck ?: 0 }

    /** 컬렉션을 완성해 얻는 추가 행운. */
    fun decorSetBonus(): Int = Decors.placedSets(placedDecorIds()).sumOf { it.bonus }

    /** 설치된 소품 + 완성한 컬렉션의 행운 보너스 합. */
    fun decorLuck(): Int = decorItemLuck() + decorSetBonus()

    /** 한 소품은 한 칸에만 놓을 수 있다. 이미 놓여 있던 자리에서는 자동으로 들어 올린다. */
    fun placeDecor(slot: Int, decorId: Int) {
        if (slot !in decorSlots.indices) return
        if (decorId < 0) {
            decorSlots[slot] = -1
            return
        }
        if (decorId !in decorOwned || Decors.of(decorId) == null) return
        for (i in decorSlots.indices) if (i != slot && decorSlots[i] == decorId) decorSlots[i] = -1
        decorSlots[slot] = decorId
    }

    /** 보유 소품을 id 순서로 한 번에 정리한다. 반환값은 채워진 칸 수. */
    fun autoArrangeDecors(): Int {
        val owned = decorOwned.distinct().filter { Decors.of(it) != null }.sorted()
        for (i in decorSlots.indices) decorSlots[i] = if (i < owned.size) owned[i] else -1
        return minOf(owned.size, decorSlots.size)
    }

    /** 로드 직후 레이아웃을 정리한다. v4에서는 중복 배치가 가능했기 때문에 필요하다. */
    fun normalizeDecorLayout() {
        val seen = HashSet<Int>()
        for (i in decorSlots.indices) {
            val id = decorSlots[i]
            if (id !in decorOwned || Decors.of(id) == null || !seen.add(id)) decorSlots[i] = -1
        }
    }

    /** 희귀새 출현 계산에 쓰는 실효 행운 (장식 + 자전거 + 감성 장비) */
    fun effectiveLuck(): Float = (luck + decorLuck() + bikeLuck() + gearLuck()).coerceAtMost(100f)

    // ------------------ 자전거 ------------------

    /** 장착 중인 자전거 모델 */
    fun bike(): Bikes.Bike = Bikes.of(bikeId)

    /** 자전거 외형 (모델 + 도색 + 부속품) */
    fun bikeStyle(): BikeStyle = bikeStyleOf(bikeId)

    /** 특정 모델을 현재 도색·부속품으로 꾸민 외형 (상점 미리보기용) */
    fun bikeStyleOf(modelId: String): BikeStyle = BikeStyle(
        modelId,
        BikeColors.frame(bikeFrameColor),
        BikeColors.tire(bikeTireColor),
        BikeColors.saddle(bikeSaddleColor),
        basket = "basket" in ownedBikeParts,
        rack = "rack" in ownedBikeParts,
        light = "light" in ownedBikeParts,
        streamers = "streamers" in ownedBikeParts,
        bell = "bell" in ownedBikeParts
    )

    /** 자전거 주행 속도 배율 */
    fun bikeSpeedMult(): Float = bike().speed

    /** 자전거 주행 시 배고픔 소모 배율 */
    fun bikeHungerMult(): Float = bike().hunger

    /** 자전거 탑승 시 새 도망 반경 배율 (전조등 등 부속품 효과 포함) */
    fun bikeScareMult(): Float {
        var m = bike().scare
        for (id in ownedBikeParts) m *= BikeParts.of(id)?.scareMult ?: 1f
        return m
    }

    /** 자전거가 주는 행운 보너스 (모델 + 부속품) */
    fun bikeLuck(): Int {
        var n = bike().luck
        for (id in ownedBikeParts) n += BikeParts.of(id)?.luck ?: 0
        return n
    }

    /** 자전거 부속품이 늘려 주는 피자 소지 한도 */
    fun bikePizzaBonus(): Int {
        var n = 0
        for (id in ownedBikeParts) n += BikeParts.of(id)?.pizza ?: 0
        return n
    }

    // ------------------------------------------------------------------

    /** 새로운 게임 시작: 선택한 지역에 집 정착 */
    fun reset(@Suppress("UNUSED_PARAMETER") homeRegionId: String = START_REGION_ID) {
        started = true
        inHome = false
        // 첫 정착지는 항상 서울. 다른 지역은 여행 후 집을 매입한다.
        money = 30000
        hunger = 100f
        luck = 50f
        for (i in pizzas.indices) pizzas[i] = 0
        // 첫 장비는 물려받은 컴팩트 카메라 한 대
        ownedGear.clear()
        ownedGear.add(CameraGear.STARTER)
        useIlc = false
        compactId = CameraGear.STARTER
        bodyId = null
        lensId = null
        tcId = null
        invalidateRig()
        birdCounts.clear()
        bestStars.clear()
        photoAlbum.clear()
        visited.clear()
        landmarksSeen.clear()
        homeRegion = START_REGION_ID
        region = START_REGION_ID
        visited.add(START_REGION_ID)
        ownedHomes.clear()
        ownedHomes.add(START_REGION_ID)
        ownedHouseStyles.clear()
        ownedHouseStyles.add("cozy")
        houseStyleId = "cozy"
        px = 0f
        py = 0f
        onBike = false
        questBird = null
        questReward = 0
        mainQuestStarted = false
        mainQuestStage = 0
        mainQuestFinished = false
        playSeconds = 0f
        photos = 0
        worldTime = 8.5f
        day = 1
        weatherId = Weather.SUNNY.id
        weatherSeconds = 55f
        for (i in decorSlots.indices) decorSlots[i] = -1
        decorOwned.clear()
        ownedBikes.clear()
        ownedBikes.add("basic")
        bikeId = "basic"
        bikeFrameColor = 0
        bikeTireColor = 0
        bikeSaddleColor = 0
        ownedBikeParts.clear()
        level = 1
        exp = 0
        skillPoints = 0
        skills.clear()
        // 화면 연출(camShake/camBob/...)은 플레이어 취향이라 새 게임에서도 유지한다.
    }

    // ------------------------------------------------------------------
    // 직렬화
    // ------------------------------------------------------------------

    fun toJSON(): JSONObject = JSONObject().apply {
        put("v", 5)
        put("started", started)
        put("gender", gender)
        put("inHome", inHome)
        put("money", money)
        put("hunger", hunger.toDouble())
        put("luck", luck.toDouble())
        put("ownedGear", JSONArray().apply { ownedGear.forEach { put(it) } })
        put("useIlc", useIlc)
        put("compactId", compactId)
        put("bodyId", bodyId ?: "")
        put("lensId", lensId ?: "")
        put("tcId", tcId ?: "")
        put("homeRegion", homeRegion)
        put("region", region)
        put("houseStyleId", houseStyleId)
        put("ownedHomes", JSONArray().apply { ownedHomes.forEach { put(it) } })
        put("ownedHouseStyles", JSONArray().apply { ownedHouseStyles.forEach { put(it) } })
        put("px", px.toDouble())
        put("py", py.toDouble())
        put("onBike", onBike)
        put("questBird", questBird ?: "")
        put("questReward", questReward)
        put("mainQuestStarted", mainQuestStarted)
        put("mainQuestStage", mainQuestStage)
        put("mainQuestFinished", mainQuestFinished)
        put("playSeconds", playSeconds.toDouble())
        put("photos", photos)
        put("worldTime", worldTime.toDouble())
        put("day", day)
        put("musicOn", musicOn)
        put("sfxOn", sfxOn)
        put("renderScale", renderScale)
        put("smoothScreen", smoothScreen)
        put("weatherId", weatherId)
        put("weatherSeconds", weatherSeconds.toDouble())
        put("level", level)
        put("exp", exp)
        put("skillPoints", skillPoints)
        put("skills", JSONObject(skills as Map<*, *>))
        put("pizzas", JSONArray().apply { pizzas.forEach { put(it) } })
        put("birdCounts", JSONObject(birdCounts as Map<*, *>))
        put("bestStars", JSONObject(bestStars as Map<*, *>))
        put("photoAlbum", JSONArray().apply { photoAlbum.forEach { put(it.toJSON()) } })
        put("visited", JSONArray().apply { visited.forEach { put(it) } })
        put("landmarksSeen", JSONArray().apply { landmarksSeen.forEach { put(it) } })
        put("decorSlots", JSONArray().apply { decorSlots.forEach { put(it) } })
        put("decorOwned", JSONArray().apply { decorOwned.forEach { put(it) } })
        put("ownedBikes", JSONArray().apply { ownedBikes.forEach { put(it) } })
        put("bikeId", bikeId)
        put("bikeFrameColor", bikeFrameColor)
        put("bikeTireColor", bikeTireColor)
        put("bikeSaddleColor", bikeSaddleColor)
        put("ownedBikeParts", JSONArray().apply { ownedBikeParts.forEach { put(it) } })
        put("floatStick", floatStick)
        put("analogStick", analogStick)
        put("camShake", camShake)
        put("camBob", camBob)
        put("camBlur", camBlur)
        put("camFov", camFov)
        put("camDof", camDof)
        put("camLead", camLead)
        put("healing", healing)
    }

    companion object {
        fun fromJSON(j: JSONObject): GameState {
            val s = GameState()
            val v = j.optInt("v", 1)
            s.started = j.optBoolean("started", false)
            s.gender = j.optString("gender", "male")
            s.inHome = j.optBoolean("inHome", false)
            s.money = j.optInt("money", 0)
            s.hunger = j.optDouble("hunger", 100.0).toFloat()
            s.luck = j.optDouble("luck", 50.0).toFloat()
            // ---- 카메라 장비 ----
            val og = j.optJSONArray("ownedGear")
            if (og != null) {
                for (i in 0 until og.length()) {
                    val id = og.optString(i, "")
                    if (id in CameraGear.byId) s.ownedGear.add(id)
                }
            }
            if (s.ownedGear.isEmpty()) {
                // v1~v3 세이브: 카메라 등급(1~5)을 새 장비 시스템으로 옮긴다.
                for (id in CameraGear.migrateLegacy(j.optInt("cameraLevel", 1))) s.ownedGear.add(id)
            }
            s.ownedGear.add(CameraGear.STARTER)
            s.compactId = j.optString("compactId", CameraGear.STARTER)
                .let { if (CameraGear.compact(it) != null && it in s.ownedGear) it else CameraGear.STARTER }
            s.bodyId = j.optString("bodyId", "").ifEmpty { null }
                ?.let { if (CameraGear.body(it) != null && it in s.ownedGear) it else null }
            s.lensId = j.optString("lensId", "").ifEmpty { null }
                ?.let { if (CameraGear.lens(it) != null && it in s.ownedGear) it else null }
            s.tcId = j.optString("tcId", "").ifEmpty { null }
                ?.let { if (CameraGear.tc(it) != null && it in s.ownedGear) it else null }
            s.useIlc = j.optBoolean("useIlc", false) && s.ilcReady()
            if (!j.has("ownedGear")) {
                // 마이그레이션: 옮겨온 바디·렌즈가 있으면 그대로 장착해 준다.
                val b = s.ownedGear.firstOrNull { CameraGear.body(it) != null }
                val l = s.ownedGear.firstOrNull { CameraGear.lens(it) != null }
                val t = s.ownedGear.firstOrNull { CameraGear.tc(it) != null }
                val bestCompact = s.ownedGear.mapNotNull { CameraGear.compact(it) }.maxByOrNull { it.price }
                if (bestCompact != null) s.compactId = bestCompact.id
                if (b != null && l != null) {
                    s.bodyId = b
                    s.lensId = l
                    s.tcId = t
                    s.useIlc = true
                }
            }
            s.invalidateRig()
            s.homeRegion = j.optString("homeRegion", START_REGION_ID)
            s.region = j.optString("region", s.homeRegion)
            s.houseStyleId = j.optString("houseStyleId", "cozy")
            val oh = j.optJSONArray("ownedHomes")
            if (oh != null) {
                for (i in 0 until oh.length()) {
                    val id = oh.optString(i, "")
                    if (id in Regions.byId) s.ownedHomes.add(id)
                }
            }
            // v1/v2 세이브에는 소유 집 목록이 없었으므로 당시 집을 자동 보존한다.
            s.ownedHomes.add(s.homeRegion)
            val os = j.optJSONArray("ownedHouseStyles")
            if (os != null) {
                for (i in 0 until os.length()) {
                    val id = os.optString(i, "")
                    if (id in HouseStyles.byId) s.ownedHouseStyles.add(id)
                }
            }
            s.ownedHouseStyles.add("cozy")
            if (s.houseStyleId !in s.ownedHouseStyles) s.houseStyleId = "cozy"
            s.px = j.optDouble("px", 0.0).toFloat()
            s.py = j.optDouble("py", 0.0).toFloat()
            s.onBike = j.optBoolean("onBike", false)
            s.questBird = j.optString("questBird", "").ifEmpty { null }
            s.questReward = j.optInt("questReward", 0)
            s.mainQuestStarted = j.optBoolean("mainQuestStarted", false)
            s.mainQuestStage = j.optInt("mainQuestStage", 0).coerceIn(0, MainStory.CHAPTERS.size)
            s.mainQuestFinished = j.optBoolean("mainQuestFinished", false) || s.mainQuestStage >= MainStory.CHAPTERS.size
            s.playSeconds = j.optDouble("playSeconds", 0.0).toFloat()
            s.photos = j.optInt("photos", 0)
            s.worldTime = j.optDouble("worldTime", 8.5).toFloat().coerceIn(0f, 24f)
            s.day = j.optInt("day", 1).coerceAtLeast(1)
            s.musicOn = j.optBoolean("musicOn", true)
            s.sfxOn = j.optBoolean("sfxOn", true)
            s.renderScale = when (j.optString("renderScale", "auto")) {
                "1", "2", "3" -> j.optString("renderScale")
                else -> "auto"
            }
            s.smoothScreen = j.optBoolean("smoothScreen", false)
            s.weatherId = j.optString("weatherId", Weather.SUNNY.id)
            s.weatherSeconds = j.optDouble("weatherSeconds", 55.0).toFloat().coerceIn(0f, 120f)

            s.level = j.optInt("level", 1).coerceIn(1, Progression.MAX_LEVEL)
            s.exp = j.optInt("exp", 0).coerceAtLeast(0)
            s.skillPoints = j.optInt("skillPoints", 0).coerceAtLeast(0)
            val sk = j.optJSONObject("skills")
            if (sk != null) {
                val itSk = sk.keys()
                while (itSk.hasNext()) {
                    val k = itSk.next()
                    val def = Skills.of(k)
                    if (def != null) s.skills[k] = sk.optInt(k, 0).coerceIn(0, def.maxRank)
                }
            }

            val pz = j.optJSONArray("pizzas")
            if (pz != null) {
                if (v >= 2 || pz.length() >= 9) {
                    // v2/v3: 9칸(치즈·버섯·불고기 × 품질) / v4: 피자 12종 × 품질 — id가 같으므로 앞에서부터 그대로 복사
                    for (i in 0 until minOf(pz.length(), s.pizzas.size)) s.pizzas[i] = pz.optInt(i, 0).coerceAtLeast(0)
                } else {
                    // v1: 품질 3칸 배열 -> 치즈 피자로 마이그레이션
                    for (q in 0 until minOf(pz.length(), 3)) s.pizzas[q] = pz.optInt(q, 0).coerceAtLeast(0)
                }
            }
            val bc = j.optJSONObject("birdCounts")
            if (bc != null) {
                val it2 = bc.keys()
                while (it2.hasNext()) {
                    val k = it2.next()
                    s.birdCounts[k] = bc.optInt(k, 0)
                }
            }
            val bs = j.optJSONObject("bestStars")
            if (bs != null) {
                val it3 = bs.keys()
                while (it3.hasNext()) {
                    val k = it3.next()
                    s.bestStars[k] = bs.optInt(k, 0)
                }
            }
            val pa = j.optJSONArray("photoAlbum")
            if (pa != null) {
                val start = (pa.length() - PhotoArchive.MAX_PHOTOS).coerceAtLeast(0)
                for (i in start until pa.length()) {
                    BirdPhotoRecord.fromJSON(pa.optJSONObject(i) ?: continue)?.let { s.photoAlbum.add(it) }
                }
            }
            val vs = j.optJSONArray("visited")
            if (vs != null) {
                for (i in 0 until vs.length()) s.visited.add(vs.optString(i))
            }
            val lms = j.optJSONArray("landmarksSeen")
            if (lms != null) {
                for (i in 0 until lms.length()) s.landmarksSeen.add(lms.optString(i))
            }
            val ds = j.optJSONArray("decorSlots")
            if (ds != null) {
                // v4의 3칸 배치는 새 8칸 레이아웃의 앞 세 칸에 보존한다.
                for (i in 0 until minOf(ds.length(), s.decorSlots.size)) {
                    s.decorSlots[i] = ds.optInt(i, -1)
                }
            }
            val dwn = j.optJSONArray("decorOwned")
            if (dwn != null) {
                for (i in 0 until dwn.length()) {
                    val id = dwn.optInt(i, -1)
                    if (Decors.of(id) != null && id !in s.decorOwned) s.decorOwned.add(id)
                }
            }
            // 매우 오래된 세이브에서 장식을 먼저 배치했던 경우에도 소유권을 복구한다.
            for (id in s.decorSlots) if (Decors.of(id) != null && id !in s.decorOwned) s.decorOwned.add(id)
            s.normalizeDecorLayout()
            // 자전거 (v3 이하 세이브에는 기본 자전거만 있다)
            val ob = j.optJSONArray("ownedBikes")
            if (ob != null) {
                for (i in 0 until ob.length()) {
                    val id = ob.optString(i, "")
                    if (id in Bikes.byId) s.ownedBikes.add(id)
                }
            }
            s.ownedBikes.add("basic")   // 기본 자전거는 항상 보유
            s.bikeId = j.optString("bikeId", "basic")
            if (s.bikeId !in s.ownedBikes) s.bikeId = "basic"
            s.bikeFrameColor = j.optInt("bikeFrameColor", 0)
            s.bikeTireColor = j.optInt("bikeTireColor", 0)
            s.bikeSaddleColor = j.optInt("bikeSaddleColor", 0)
            val obp = j.optJSONArray("ownedBikeParts")
            if (obp != null) {
                for (i in 0 until obp.length()) {
                    val id = obp.optString(i, "")
                    if (id in BikeParts.byId) s.ownedBikeParts.add(id)
                }
            }
            // v0.4.1 조이스틱 설정 (없으면 새 기본값 = 켬)
            s.floatStick = j.optBoolean("floatStick", true)
            s.analogStick = j.optBoolean("analogStick", true)

            // 옛 세이브에는 화면 연출 설정이 없다 → 기본값(보통/전부 켬)으로 시작
            s.camShake = j.optInt("camShake", 2).coerceIn(0, 3)
            s.camBob = j.optBoolean("camBob", true)
            s.camBlur = j.optBoolean("camBlur", true)
            s.camFov = j.optBoolean("camFov", true)
            s.camDof = j.optBoolean("camDof", true)
            s.camLead = j.optBoolean("camLead", true)
            // 🌸 힐링 상태 (v0.4.2 — 없으면 빈 JSONObject)
            s.healing = j.optJSONObject("healing") ?: JSONObject()
            return s
        }
    }
}

/** 저장/로드 관리자 */
object SaveManager {
    private const val PREFS = "pizza_and_bird_save"
    private const val KEY = "state"

    fun save(ctx: Context, s: GameState) {
        if (!s.started) return
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, s.toJSON().toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context): GameState {
        return try {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            if (raw != null) GameState.fromJSON(JSONObject(raw)) else GameState()
        } catch (_: Exception) {
            GameState()
        }
    }

    fun hasSave(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        PhotoArchive.clear(ctx)
    }

    // [P05] 백업 코드가 같은 prefs를 읽을 수 있도록 노출 — 기존 save/load/has/clear 는 무수정
    fun prefsName(): String = PREFS
    fun saveKey(): String = KEY
}
