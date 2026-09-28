package com.pizzaandbird.game

/**
 * 지형 소품(바위·나무)의 **크기급**.
 *
 * 게임은 소품을 32px 타일에 1:1로 그린다. 크기감을 주려면 **아트 자체가 크기를 갖고
 * 있어야** 픽셀 격자가 흐트러지지 않는다(런타임 스케일은 도트 폭이 불규칙해진다).
 * 그래서 바위 아트는 28종을 세 단계로 나눠 그린다.
 *
 * [blocksSight] 는 새 → 플레이어 사이를 막아 '숨어서 접근'할 수 있게 하는지다.
 * 발목만 넘는 자갈 뒤에 새가 숨을 이유는 없으므로 PEBBLE 은 막지 않는다.
 */
enum class PropSize(val label: String, val blocksSight: Boolean) {
    PEBBLE("자갈", false),   // 발목 높이 — 지나갈 수 있다
    LOW("낮은 돌", true),    // 허리 높이
    TALL("큰 바위", true)    // 눈높이 이상
}

/** 바위 아트의 암종 — 지역 지질에 맞춰 고른다. */
enum class RockKind(val label: String) {
    GRANITE("화강암"),        // 설악·광릉·왕피의 비고지
    BASALT("현무암"),         // 제주·하도리·한라
    SEDIMENT("해식퇴적암"),   // 동해 절벽
    LIMESTONE("석회암"),      // 전주·광주 서안
    SANDSTONE("사암"),        // 철원 평야·대구 분지
    CONCRETE("인공석재"),     // 항구 방파제·한옥 돌담·도시 화단
    DEBRIS("자연 쇳돌")       // 강가 자갈·갯벌·숲
}

/**
 * 바위 소품 카탈로그 — `Assets.kt` 의 `rockArt()` 변형 순서와 **1:1**로 맞는다.
 *
 * 지역은 `NatureArtSet.rocks` 에서 이 번호들을 고른다. 바위가 곧 그 지역의 지질
 * 표본이 되도록 묶어 두었다(갯벌에는 사구돌, 제주에는 현무암 기둥, 전주에는 돌담).
 */
object PropLooks {
    class RockLook(val name: String, val size: PropSize, val kind: RockKind)

    val ROCKS: List<RockLook> = listOf(
        // ── 화강암 (설악·광릉·왕피) ──────────────────────────────────────
        RockLook("화강암 노두", PropSize.LOW, RockKind.GRANITE),
        RockLook("설악 암괴", PropSize.TALL, RockKind.GRANITE),
        RockLook("노두 자갈", PropSize.PEBBLE, RockKind.GRANITE),
        RockLook("이끼 화강암", PropSize.LOW, RockKind.GRANITE),
        // ── 현무암 (제주·하도리·한라) ────────────────────────────────────
        RockLook("현무암 기둥", PropSize.TALL, RockKind.BASALT),
        RockLook("용암 성벽", PropSize.TALL, RockKind.BASALT),
        RockLook("현무암 자갈", PropSize.PEBBLE, RockKind.BASALT),
        RockLook("현무암 방패바위", PropSize.LOW, RockKind.BASALT),
        // ── 해식 퇴적암 (동해안) ─────────────────────────────────────────
        RockLook("동해 층리 바위", PropSize.LOW, RockKind.SEDIMENT),
        RockLook("해식 절벽", PropSize.TALL, RockKind.SEDIMENT),
        RockLook("해안 사암 블록", PropSize.LOW, RockKind.SEDIMENT),
        RockLook("갯바위 선반", PropSize.LOW, RockKind.SEDIMENT),
        // ── 석회암 (전주·광주 서안) ──────────────────────────────────────
        RockLook("밝은 석회암", PropSize.LOW, RockKind.LIMESTONE),
        RockLook("석회암 절벽", PropSize.TALL, RockKind.LIMESTONE),
        RockLook("석회암 자갈", PropSize.PEBBLE, RockKind.LIMESTONE),
        // ── 사암 (철원 평야·대구 분지) ───────────────────────────────────
        RockLook("사암 단층", PropSize.LOW, RockKind.SANDSTONE),
        RockLook("사암 절벽", PropSize.TALL, RockKind.SANDSTONE),
        RockLook("사암 조각돌", PropSize.PEBBLE, RockKind.SANDSTONE),
        // ── 인공 석재 (항구·한옥·도시) ───────────────────────────────────
        RockLook("방파제 블록", PropSize.LOW, RockKind.CONCRETE),
        RockLook("옹벽 돌담", PropSize.TALL, RockKind.CONCRETE),
        RockLook("조경 화단석", PropSize.PEBBLE, RockKind.CONCRETE),
        RockLook("호안석 가비온", PropSize.LOW, RockKind.CONCRETE),
        // ── 자연 쇳돌 (강가·갯벌·숲·해안) ────────────────────────────────
        RockLook("강 자갈 더미", PropSize.PEBBLE, RockKind.DEBRIS),
        RockLook("숲 이끼 바위", PropSize.LOW, RockKind.DEBRIS),
        RockLook("갯벌 사구돌", PropSize.PEBBLE, RockKind.DEBRIS),
        RockLook("조개 자갈", PropSize.PEBBLE, RockKind.DEBRIS),
        RockLook("대왕암 첨탑", PropSize.TALL, RockKind.DEBRIS),
        RockLook("갈대 곁 도라돌", PropSize.PEBBLE, RockKind.DEBRIS)
    )

    /** 아트가 추가된 바위 변형 수와 카탈로그가 어긋나지 않았는지 확인한다. */
    val ROCK_COUNT: Int get() = ROCKS.size

    fun rock(look: Int): RockLook = ROCKS[((look % ROCKS.size) + ROCKS.size) % ROCKS.size]

    fun rockSize(look: Int): PropSize = rock(look).size

    /** 이 크기의 바위가 시야를 막아 '숨기'가 가능한가. */
    fun rockBlocksSight(look: Int): Boolean = rock(look).size.blocksSight
}
