package com.pizzaandbird.game

/**
 * 도감의 **검색 · 정렬 · 모아 보기** (v0.5 「도감에 검색을 넣다」).
 *
 * 598종을 눈으로 넘겨 찾는 건 게임 초까지만 견디는 일이다. 할머니 수첩을 이어 쓴 사람이라면
 * "이 새가 뭐였지"로 돌아오기 마련이고, 그때 필요한 건 키보드 두 번이다.
 *
 *  - [DexSort]  — 목록 순서 (정렬 방법)
 *  - [DexGroup] — 속성으로 묶어 보는 기준 (모아 보기)
 *  - [BirdIndex] — 검색 매칭과 결과 자르기. **상태가 바뀔 때만** 다시 계산한다 (매 프레임 598회 필터 금지)
 *  - [JamoInput] — 병음 없이 한글을 입력하기 위한 초성/중성/종성 조합기 (하단 키보드가 쓴다)
 *
 * 검색은 ` 이름 / 영문 / 학명 / 목 · 과 / id` 부분일치 **그리고 초성 일치**를 본다.
 * "ㅊㅅ" → 참새, "물총" → 물총새·청물총새… 처럼, 수첩에 대충 적어 둔 소리만으로도 찾게 하는 게 목적.
 */
enum class DexSort(val label: String, val hint: String) {
    /** 공식 목록 번호 (기본) */
    NUM("목록", "학술 목록 순서대로"),
    /** 한국어 이름 가나다 */
    NAME("가나다", "이름 순서대로"),
    /** 등급 높은 순 → 목록 순 */
    TIER("등급", "전설 → 흔함"),
    /** 많이 찍은 순 (미촬영은 뒤) */
    PHOTOS("촬영", "셔터를 누른 횟수"),
    /** 최고 별점 순 */
    STARS("별점", "가장 잘 찍은 사진"),
    /** 목 · 과 이름순 */
    FAMILY("목·과", "같은 가족끼리")
}

/** 도감을 속성으로 묶는 기준. 키를 고르면 그 속성만 남긴다. */
enum class DexGroup(val label: String) {
    NONE("전체"),
    TIER("등급"),
    HABITAT("서식지"),
    MIGRATION("철새"),
    SEASON("계절"),
    TIME("낮·밤"),
    REGION("이 동네"),
    UNSEEN("미촬영")
}

/** 검색어에 걸러지고 정렬된 도감 목록 + 각 묶음의 개수. */
object BirdIndex {

    /** id → 검색에 쓸 텍스트(소문자) / id → 초성만 뽑은 텍스트 */
    private val haystack = HashMap<String, String>()
    private val initials = HashMap<String, String>()

    /** 직전 요청 키와 결과가 같으면 재계산하지 않는다 (도감 탭은 매 프레임 그려진다). */
    private var memoKey = ""
    private var memoResult: List<BirdDef> = emptyList()

    private fun build() {
        if (haystack.isNotEmpty()) return
        for (def in Birds.ALL) {
            val hay = listOf(
                def.name, def.englishName, def.scientificName,
                def.familyName, def.orderName, def.category,
                def.migrationLabel, def.tier.label, def.id, def.birdNum.toString(),
                def.habitats.joinToString(" ") { HabitatLabels[it] ?: it }
            ).joinToString(" ").lowercase()
            haystack[def.id] = hay
            initials[def.id] = Jamo.initialKey(def.name) + " " + Jamo.initialKey(def.englishName.lowercase())
        }
    }

    /** [def] 가 검색어 [q] 에 걸리는가. 빈 검색어는 항상 참. */
    fun matches(def: BirdDef, q: String): Boolean {
        if (q.isBlank()) return true
        build()
        val needle = q.trim().lowercase()
        if ((haystack[def.id] ?: "").contains(needle)) return true
        // 초성 검색 — "ㅊㅅ" · "ㅅㅁㅇ" 같은 입력
        val ik = Jamo.initialKey(needle)
        return ik.isNotEmpty() && (initials[def.id] ?: "").contains(ik)
    }

    /** 이 속성으로 묶었을 때의 키 목록 (키, 표시 이름) — 도감 전체에서 실제로 나타나는 순서대로. */
    fun keysOf(group: DexGroup, regionId: String = "seoul"): List<Pair<String, String>> = when (group) {
        DexGroup.NONE -> emptyList()
        DexGroup.TIER -> Tier.values().map { it.name to it.label }
        DexGroup.HABITAT -> listOf(
            "city" to "도시", "forest" to "숲", "field" to "들판", "water" to "물가",
            "wetland" to "습지", "coast" to "바다", "mountain" to "산"
        )
        DexGroup.MIGRATION -> Birds.ALL.map { it.migrationLabel }
            .distinct().sortedWith(compareBy { it })
            .map { it to it }
        DexGroup.SEASON -> BirdSeason.ALL.toList().map { it.name to it.label }
        DexGroup.TIME -> listOf("day" to "낮새", "night" to "밤새", "any" to "종일")
        DexGroup.REGION -> listOf("__here__" to (Regions.byId[regionId]?.name ?: "이 동네"))
        DexGroup.UNSEEN -> listOf("__unseen__" to "아직 못 찍은 새")
    }

    /** 묶음별 개수 — 헤더 칩에 "등급 · 흔함 142" 처럼 보여 준다. */
    fun counts(s: GameState, group: DexGroup, q: String, regionId: String): Map<String, Int> {
        if (group == DexGroup.NONE) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (key in keysOf(group, regionId).map { it.first }) out[key] = 0
        for (def in Birds.ALL) {
            if (!matches(def, q)) continue
            for (k in groupKeysOf(def, group, s)) out[k] = (out[k] ?: 0) + 1
        }
        return out
    }

    /** 이 새가 속한 묶음 키들 (하나의 새가 여러 묶음에 들어갈 수 있다: 계절·서식지). */
    private fun groupKeysOf(def: BirdDef, group: DexGroup, s: GameState): List<String> = when (group) {
        DexGroup.NONE -> emptyList()
        DexGroup.TIER -> listOf(def.tier.name)
        DexGroup.HABITAT -> def.habitats.toList()
        DexGroup.MIGRATION -> listOf(def.migrationLabel)
        DexGroup.SEASON -> def.seasons.map { it.name }
        DexGroup.TIME -> listOf(def.active)
        DexGroup.REGION -> if (appearsIn(def, s.region)) listOf("__here__") else emptyList()
        DexGroup.UNSEEN -> if ((s.birdCounts[def.id] ?: 0) == 0) listOf("__unseen__") else emptyList()
    }

    /** 그 동네(서식지 · 한정 지역)에서 볼 수 있는 새인가 — 계절·시간은 보지 않는다 (도감이므로). */
    fun appearsIn(def: BirdDef, regionId: String): Boolean {
        val r = Regions.byId[regionId] ?: return false
        return def.habitats.intersect(r.habitats).isNotEmpty() &&
            (def.onlyRegions == null || regionId in def.onlyRegions)
    }

    /**
     * 도감이 실제로 그리는 목록. 같은 입력이면前回 결과를 그대로 쓴다.
     *
     * @param regionId 현재 지역 (`DexGroup.REGION` 판정용)
     */
    fun view(s: GameState, q: String, sort: DexSort, group: DexGroup, key: String?, regionId: String): List<BirdDef> {
        val mk = listOf(
            q, sort.name, group.name, key ?: "-", s.birdCounts.size, s.bestStars.size,
            s.level, regionId
        ).joinToString("|")
        if (mk == memoKey) return memoResult
        val filtered = Birds.ALL.filter { def ->
            matches(def, q) && (group == DexGroup.NONE || key == null || key in groupKeysOf(def, group, s))
        }
        val sorted = when (sort) {
            DexSort.NUM -> filtered
            DexSort.NAME -> filtered.sortedWith(compareBy({ it.name }, { it.birdNum }))
            DexSort.TIER -> filtered.sortedWith(
                compareByDescending<BirdDef> { it.tier.star }.thenBy { it.birdNum }
            )
            DexSort.PHOTOS -> filtered.sortedWith(
                compareByDescending<BirdDef> { s.birdCounts[it.id] ?: 0 }.thenBy { it.birdNum }
            )
            DexSort.STARS -> filtered.sortedWith(
                compareByDescending<BirdDef> { s.bestStars[it.id] ?: 0 }
                    .thenByDescending { s.birdCounts[it.id] ?: 0 }
                    .thenBy { it.birdNum }
            )
            DexSort.FAMILY -> filtered.sortedWith(
                compareBy({ it.orderName }, { it.familyName }, { it.birdNum })
            )
        }
        // 묶어 보기 중이면 묶음별로 모은다 (같은 속성끼리 붙어 있어야 "모았다"는 느낌이 난다)
        val result = if (group == DexGroup.NONE || key != null) sorted else sorted.groupBy {
            groupKeysOf(it, group, s).firstOrNull() ?: ""
        }.let { groups ->
            val out = ArrayList<BirdDef>(sorted.size)
            val order = keysOf(group, regionId)
            for ((k, _) in order) out += groups[k] ?: emptyList()
            for ((k, v) in groups) if (order.none { it.first == k }) out += v
            out
        }
        memoKey = mk
        memoResult = result
        return result
    }

    /** 지역·계절이 바뀌면(= "이 동네" 그룹의 의미가 달라지면) 결과를 버린다. */
    fun invalidate() {
        memoKey = ""
        memoResult = emptyList()
    }
}

/**
 * 한글 자모 — 키보드용 표와 조합기.
 *
 * QWERTY 키보드에서 ㅏㅓ ㄱㄴ 을 직접 입력 받는 대신, **초성 → 중성 → 종성** 순서로
 * 탭하면 한 글자가 만들어진다. 완성되지 않은 글자는 키보드 위에 미리보기로 떠야 한다.
 */
object Jamo {
    const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val JUNGSEONG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"

    /** 종성 27자 + 없음(0) 인 28 슬롯 — Unicode 음절 계산이 이 순서를 전제로 한다. */
    const val JONGSEONG = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    /** 초성 문자(자음) → 초성 인덱스. 종성/중성이 섞여 있으면 -1. */
    fun choIndexOf(ch: Char): Int = CHOSEONG.indexOf(ch)

    fun compose(cho: Int, jung: Int, jong: Int): String {
        if (cho < 0 && jung < 0 && jong < 0) return ""
        if (cho < 0 || jung < 0) {
            // 초성·중성이 한쪽만 있으면 자모 그대로
            val sb = StringBuilder(2)
            if (cho >= 0) sb.append(CHOSEONG[cho])
            if (jung >= 0) sb.append(JUNGSEONG[jung])
            if (jong > 0) sb.append(JONGSEONG[jong - 1])
            return sb.toString()
        }
        val code = 0xAC00 + (cho * 21 + jung) * 28 + jong
        return String(Character.toChars(code))
    }

    /**
     * 한글 이름 → 초성만 이어 붙인 문자열. 예: "참새" → "ㅊㅅ", "흰뺨검둥오리" → "ㅎㄱㄷㅇㄹ".
     * 한글이 아닌 글자(라틴·숫자)는 소문자로 그대로 둔다 — "꿩" 대신 "Pheasant" 로도 찾게.
     */
    fun initialKey(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val c = ch.code
            if (c in 0xAC00..0xD7A3) {
                val cho = (c - 0xAC00) / 28 % 21
                sb.append(CHOSEONG[cho])
            } else if (c in 0x3131..0x318E) {
                // 이미 자모(초성 입력)인 경우
                val i = CHOSEONG.indexOf(ch)
                sb.append(if (i >= 0) CHOSEONG[i] else ch.lowercaseChar())
            } else if (ch.isLetterOrDigit()) {
                sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }
}

/**
 * 도감 검색창용 한글 입력기. 키보드가 "누른 자모"를 알려 주기만 하면,
 * 언제 확정해서 검색어에 넘길지(= 조합을 언제 닫을지)가 여기 결정된다.
 *
 * 확정 규칙은手机的 한글 키보드와 같이: **다음을 누르면 앞 글자가 확정**된다.
 * 반환값은 "검색어에 바로 붙여야 할 문자열" (아직 조합 중이면 null).
 */
class JamoInput {
    private var cho = -1
    private var jung = -1
    private var jong = 0
    private var latin = false

    /** 키보드 위에 띄울 미확정 글자 (없으면 "") */
    val preview: String
        get() = if (!composing) "" else Jamo.compose(cho, jung, jong)

    val composing: Boolean get() = cho >= 0 || jung >= 0 || jong > 0

    private fun flush(): String {
        if (!composing) return ""
        val out = Jamo.compose(cho, jung, jong)
        cho = -1; jung = -1; jong = 0
        return out
    }

    /** 초성 탭 */
    fun tapCho(i: Int): String? {
        val done = flush()
        cho = i
        latin = false
        return done.ifEmpty { null }
    }

    /** 중성 탭 — 초성 없이 누르면 자모 자체가 확정된다. */
    fun tapJung(i: Int): String? {
        if (cho < 0 && jung < 0 && jong == 0) return Jamo.JUNGSEONG[i].toString()
        if (jung >= 0 && jong > 0) {
            val done = flush()
            cho = -1
            jung = i
            return done
        }
        jung = i
        return null
    }

    /** 종성 탭 — 완성 글자 위에만 얹을 수 있다. */
    fun tapJong(i: Int): String? {
        if (cho < 0 || jung < 0) return Jamo.JONGSEONG[i].toString()
        jong = i + 1
        return null
    }

    /** 라틴 키 (영어 검색용) — 조합 중이라면 먼저 확정하고 붙인다. */
    fun tapLatin(ch: Char): String? {
        val done = flush()
        latin = true
        return done + ch
    }

    /**
     * ⌫ — 미확정 글자가 있으면 그것부터 지운다(그래야 한 번 더 누르면 지워진 기분이 안 든다).
     * @return 검색어에서 지울 글자 수 (0 = 검색어 건드리지 마)
     */
    fun backspace(): Int {
        if (jong > 0) { jong = 0; return 0 }
        if (jung >= 0) { jung = -1; return 0 }
        if (cho >= 0) { cho = -1; return 0 }
        return 1
    }

    /** 검색창을 닫거나 '완료'를 누를 때 — 남은 조합까지 넘긴다. */
    fun commit(): String = flush()

    fun clear() {
        cho = -1; jung = -1; jong = 0
    }

    /** 조합 중이지 않고 라틴 입력도 아니면 한글 패널(초성/중성/종성)을 쓴다. */
    val typingLatin: Boolean get() = latin
}
