package com.pizzaandbird.game

/** Tile-space anchor for a regional landmark. */
data class MapPoint(val x: Int, val y: Int)

data class MapLake(
    val center: MapPoint,
    val radiusX: Int = 4,
    val radiusY: Int = 3
)

data class MapRiver(
    /** A north-up, east-right course through the 40x30 local map. */
    val course: List<MapPoint>,
    /** Half-width in tiles; banks are feathered into reeds / tall grass. */
    val halfWidth: Float = 1.0f,
    val bankWidth: Float = 1.5f,
    val reedBanks: Boolean = true
)

/** A climbable high point. The builder turns this into a small deck plus a ramp. */
data class ViewpointSpec(
    val point: MapPoint,
    val label: String,
    val height: Int = 2
)

/**
 * A region's native-looking sprite mix. The indices refer to the generated tile-art
 * variants in Assets.kt; choosing a family rather than a global modulo makes the same
 * grassland tile read as basalt, river gravel, pine scrub, or salt-marsh from region to region.
 */
data class NatureArtSet(
    val grass: List<Int> = (0..5).toList(),
    val tallGrass: List<Int> = listOf(0, 1),
    val flowers: List<Int> = listOf(0, 1, 2),
    val reeds: List<Int> = listOf(0, 1),
    val rocks: List<Int> = listOf(0, 1),
    val mountains: List<Int> = listOf(0, 1),
    val trees: List<Int> = listOf(0, 1, 2, 3)
) {
    /** Stable, well-mixed choice: adjacent tiles don't fall into a visible checkerboard. */
    fun variant(tile: T, x: Int, y: Int, regionId: String): Int? {
        val options = when (tile) {
            T.GRASS -> grass
            T.TALLGRASS -> tallGrass
            T.FLOWER -> flowers
            T.REED -> reeds
            T.ROCK -> rocks
            T.MOUNTAIN -> mountains
            T.TREE -> trees
            else -> return null
        }
        if (options.isEmpty()) return null
        var hash = x * 0x45D9F3B + y * 0x119DE1F3 + regionId.hashCode() * 31 + tile.ordinal * 0x27D4EB2D
        hash = (hash xor (hash ushr 16)) * 0x45D9F3B
        hash = hash xor (hash ushr 16)
        return options[Math.floorMod(hash, options.size)]
    }
}

/**
 * Art and landform settings for one local region map.
 * Tint values are MULTIPLY filters: they keep the pixel-art texture while shifting each biome.
 */
data class RegionMapStyle(
    val foliageFilter: Int,
    val waterFilter: Int,
    val shoreFilter: Int,
    val stoneFilter: Int,
    val seaDepth: Int = 3,
    val shoreDepth: Int = 2,
    val tidalReeds: Boolean = false,
    val lake: MapLake? = null,
    val rivers: List<MapRiver> = emptyList(),
    val fieldRows: Boolean = false,
    /** Move the northern city blocks up to leave room for Seoul's Han River. */
    val northBuildingY: Int = 5,
    /** Native-looking rocks, trees, and ground cover for this exact region. */
    val natureArt: NatureArtSet = NatureArtSet(),
    /** A regional high point: reached by a ramp, never a teleport-only landmark. */
    val viewpoint: ViewpointSpec? = null
)

/**
 * Local geography layered on top of Regions' real north/east/south/west exit data.
 * The coast always follows waterEdges/sandEdges in Regions; only color, shore character,
 * waterway placement, and small regional motifs are described here.
 */
object RegionMapStyles {
    private data class Palette(
        val foliage: Int,
        val water: Int,
        val shore: Int,
        val stone: Int,
        val seaDepth: Int = 3,
        val shoreDepth: Int = 2,
        val tidalReeds: Boolean = false
    )

    private val westTidal = Palette(
        0xFFE9EBCB.toInt(), 0xFFD5E3E8.toInt(), 0xFFF1E3C6.toInt(), 0xFFE0DDD0.toInt(),
        seaDepth = 2, shoreDepth = 4, tidalReeds = true
    )
    private val westCoast = Palette(
        0xFFE5EFD0.toInt(), 0xFFC9DFEC.toInt(), 0xFFFFEBCB.toInt(), 0xFFE0DED0.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val eastCoast = Palette(
        0xFFD8EFE0.toInt(), 0xFFC0E3FA.toInt(), 0xFFFFF3DF.toInt(), 0xFFD9E0D9.toInt(),
        seaDepth = 3, shoreDepth = 1
    )
    private val southCoast = Palette(
        0xFFEAF0CC.toInt(), 0xFFCEE8EF.toInt(), 0xFFFFE7C7.toInt(), 0xFFE4DDD0.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val island = Palette(
        0xFFE6EAC4.toInt(), 0xFFC6E4F0.toInt(), 0xFFFFE9C9.toInt(), 0xFFD4D3C8.toInt(),
        seaDepth = 4, shoreDepth = 2
    )
    private val freshwater = Palette(
        0xFFD9EFCF.toInt(), 0xFFC5E9E9.toInt(), 0xFFECE6C7.toInt(), 0xFFD8DDCE.toInt()
    )
    private val marsh = Palette(
        0xFFE4E9C2.toInt(), 0xFFCBDDD5.toInt(), 0xFFECE1C4.toInt(), 0xFFDADBC8.toInt(),
        tidalReeds = true
    )
    private val highland = Palette(
        0xFFD1E8D1.toInt(), 0xFFBEDDEB.toInt(), 0xFFE9E5D0.toInt(), 0xFFD0DAD6.toInt()
    )
    private val cityPark = Palette(
        0xFFE5F1D4.toInt(), 0xFFCDE8EF.toInt(), 0xFFF2E8D3.toInt(), 0xFFE6E1D4.toInt()
    )
    private val field = Palette(
        0xFFF3EBC6.toInt(), 0xFFD0E4D8.toInt(), 0xFFE8DAB5.toInt(), 0xFFE0D8C4.toInt()
    )
    private val woodland = Palette(
        0xFFDCEBCF.toInt(), 0xFFC7E4EA.toInt(), 0xFFECE4CE.toInt(), 0xFFD9DED2.toInt()
    )
    // ── 도시별 특화 팔레트 (실제 지형의 색감을 살림) ──
    private val seoulUrban = Palette(
        0xFFE0E5D0.toInt(), 0xFFC9DDE9.toInt(), 0xFFF0E7D3.toInt(), 0xFFE6E0D2.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val daeguBasin = Palette(
        0xFFFFDEA0.toInt(), 0xFFD4E8F2.toInt(), 0xFFF6E5C0.toInt(), 0xFFE8D3B8.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val daejeonValley = Palette(
        0xFFE9F1C8.toInt(), 0xFFCDE8F0.toInt(), 0xFFF1E6D0.toInt(), 0xFFE1D8C6.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val jeonjuHanok = Palette(
        0xFFF5E2A8.toInt(), 0xFFC7E9E8.toInt(), 0xFFF0E2B8.toInt(), 0xFFE4D1B8.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val chuncheonLakes = Palette(
        0xFFD4E9D5.toInt(), 0xFFC6E9E9.toInt(), 0xFFECE6C7.toInt(), 0xFFD9DDD2.toInt(),
        seaDepth = 3, shoreDepth = 2
    )
    private val gangneungPine = Palette(
        0xFFC9E3D6.toInt(), 0xFFBFE3F8.toInt(), 0xFFFFF0D8.toInt(), 0xFFD6DDD6.toInt(),
        seaDepth = 3, shoreDepth = 1
    )
    private val jejuVolcanic = Palette(
        0xFFDEE0B2.toInt(), 0xFFC2DEEC.toInt(), 0xFFEEE1C0.toInt(), 0xFFAAA99E.toInt(),
        seaDepth = 4, shoreDepth = 3
    )

    // ── 바위 묶음 ─────────────────────────────────────────────────────────
    // 번호는 PropLooks.ROCKS(바위 28종) 의 인덱스다. 묶음마다 **암종·크기·비율**이
    // 달라서, 지역마다 다른 돌무더기가 서야 한다. (갯벌엔 사구돌, 제주엔 현무암 기둥,
    // 전주엔 한옥 돌담, 속초엔 설악의 화강암 첨탑.)
    //   0 화강암노두 · 1 설악암괴 · 2 노두자갈 · 3 이끼화강암
    //   4 현무암기둥 · 5 용암성벽 · 6 현무암자갈 · 7 방패바위
    //   8 동해층리 · 9 해식절벽 · 10 사암블록 · 11 갯바위선반
    //   12 석회암 · 13 석회암절벽 · 14 석회암자갈
    //   15 사암단층 · 16 사암절벽 · 17 사암조각돌
    //   18 방파제블록 · 19 옹벽돌담 · 20 화단석 · 21 가비온
    //   22 강자갈 · 23 숲이끼바위 · 24 갯벌사구돌 · 25 조개자갈 · 26 대왕암첨탑 · 27 도라돌
    private val graniteNature = NatureArtSet(
        grass = listOf(0, 1, 2, 4), tallGrass = listOf(0, 1), flowers = listOf(2, 3, 5),
        reeds = listOf(0, 1), rocks = listOf(0, 1, 2, 3, 23), mountains = listOf(0, 2), trees = listOf(1, 8, 11, 15, 42)  // 고산 침엽 — 소나무·자작나무·전나무·가문비나무·오리나무
    )
    private val basaltNature = NatureArtSet(
        grass = listOf(0, 2, 4, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(3, 5),
        reeds = listOf(2, 3), rocks = listOf(4, 5, 6, 7), mountains = listOf(3, 2), trees = listOf(6, 7, 11, 19, 34)  // 화산·아열대 — 동백·곰솔·전나무·후박나무·감귤
    )
    /** 동해 절벽 — 층리암과 갯바위가 주를 이룬다. */
    private val seaStackNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(0, 3, 5),
        reeds = listOf(0, 2), rocks = listOf(8, 9, 11, 26, 10), mountains = listOf(0, 2), trees = listOf(1, 7, 18, 19)  // 해안 곰솔 숲 — 소나무·곰솔·노간주나무·후박나무
    )
    /** 서해 갯벌 — 사구돌과 조개자갈, 물러난 물길의 도라돌. */
    private val tidalFlatNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(1, 4, 5),
        reeds = listOf(1, 2, 3), rocks = listOf(24, 25, 11, 27, 10), mountains = listOf(0, 2), trees = listOf(1, 7, 18, 19)  // 갯벌 뒤 바닷바람 숲 — 소나무·곰솔·노간주나무·후박나무
    )
    /** 갈대습지 — 도라돌과 사구돌, 제방의 가비온. */
    private val reedMarshNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(1, 4, 5),
        reeds = listOf(1, 2, 3), rocks = listOf(27, 24, 22, 21, 11), mountains = listOf(0, 2), trees = listOf(4, 8, 29)  // 습지 둑 — 버드나무·자작나무·물오리나무
    )
    /** 하천 — 물속에 둥글게 닳은 자갈과 여울의 도라돌. */
    private val riverNature = NatureArtSet(
        grass = listOf(0, 1, 2, 4), tallGrass = listOf(0, 2, 3), flowers = listOf(0, 1, 5),
        reeds = listOf(0, 1, 3), rocks = listOf(22, 27, 23, 10), mountains = listOf(0, 2), trees = listOf(0, 4, 8, 25, 29)  // 강변 — 참나무·버드나무·자작나무·물푸레나무·물오리나무
    )
    /** 들판·논둑 — 따뜻한 사암과 돌담. */
    private val fieldNature = NatureArtSet(
        grass = listOf(0, 1, 3, 4), tallGrass = listOf(0, 2, 3), flowers = listOf(1, 3, 4),
        reeds = listOf(0, 2), rocks = listOf(15, 17, 19, 0), mountains = listOf(0, 2), trees = listOf(0, 9, 21, 28)  // 들판·마을 — 참나무·은행나무·신갈나무·감나무
    )
    /** 도시 — 다듬은 화단석과 옹벽. */
    private val urbanParkNature = NatureArtSet(
        grass = listOf(0, 1, 3, 4), tallGrass = listOf(0, 2), flowers = listOf(0, 2, 3),
        reeds = listOf(0, 1), rocks = listOf(20, 19, 0, 22), mountains = listOf(0, 2), trees = listOf(0, 2, 9, 24, 27, 30, 39)  // 도심 공원 가로수 — 참나무·벚나무·은행나무·느티나무·아까시나무·회화나무·느티나무 가로수
    )
    /** 항구 — 방파제 블록과 바다에서 올라온 사암. */
    private val harbourNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(0, 3, 5),
        reeds = listOf(0, 2), rocks = listOf(18, 22, 8, 11), mountains = listOf(0, 2), trees = listOf(1, 7, 18, 19)  // 항구 해안숲 — 소나무·곰솔·노간주나무·후박나무
    )
    /** 서안 석회암 지대 — 전주의 담과 영치면의 갯바위. */
    private val limestoneNature = NatureArtSet(
        grass = listOf(0, 1, 3, 4), tallGrass = listOf(0, 2, 3), flowers = listOf(1, 3, 5),
        reeds = listOf(0, 1), rocks = listOf(12, 13, 14, 19), mountains = listOf(0, 2), trees = listOf(0, 2, 9, 24)  // 서안 석회암 마을 — 참나무·벚나무·은행나무·느티나무
    )
    /** 호수 — 물가 자갈과 이끼 낀 바위. */
    private val lakeShoreNature = NatureArtSet(
        grass = listOf(0, 1, 2, 4), tallGrass = listOf(0, 2), flowers = listOf(0, 1, 5),
        reeds = listOf(0, 3), rocks = listOf(22, 23, 27, 7), mountains = listOf(0, 2), trees = listOf(1, 4, 8, 11, 29)  // 호숫가 — 소나무·버드나무·자작나무·전나무·물오리나무
    )

    /** Per-place accents on top of its broader habitat, so nearby wetlands don't all look alike. */
    /**
     * 지역마다 실제 지질에 맞는 돌무더기와 나무를 고른다.
     *
     * 묶음(위 `graniteNature` 등)만으로는 한눈에 구분되지 않으므로, 도시 12곳과
     * 명소 20곳은 **그 장소의 바위 번호**로 직접 지정한다. (예: 부산은 방파제 블록,
     * 인천은 갯벌 사구돌, 대구는 팔공산의 화강암, 부산·인천이 같은 돌을 쓰면 안 된다.)
     */
    private fun natureArtFor(region: RegionDef): NatureArtSet = when (region.id) {
        // ── 도시 12곳 ────────────────────────────────────────────────────
        // 서울 — 한강 자갈, 도심 화단석, 한복판 옹벽
        "seoul" -> urbanParkNature.copy(rocks = listOf(22, 20, 19, 0, 27))
        // 인천 — 갯벌의 사구돌과 조개자갈, 월미호 선사장 바깥의 갯바위
        "incheon" -> tidalFlatNature.copy(rocks = listOf(24, 25, 11, 10), trees = listOf(1, 7, 18, 10))  // 소나무·곰솔·노간주나무·과수원
        // 춘천 — 의암호·소양강 물에 닳은 자갈과 이끼 낀 바위
        "chuncheon" -> lakeShoreNature.copy(rocks = listOf(22, 23, 27, 7))
        // 강릉 — 경포호 자갈, 동해 층리 바위, 해안선반과 첨탑 바위
        "gangneung" -> seaStackNature.copy(
            rocks = listOf(8, 11, 26, 10, 22),
            // 소나무·곰솔·전나무·잣나무·오리나무
            trees = listOf(1, 7, 11, 16, 42)
        )
        // 속초 — 설악산의 화강암 첨탑과 노두 자갈이 곧 이 지역의 표상
        "sokcho" -> graniteNature.copy(
            rocks = listOf(1, 0, 2, 3), mountains = listOf(2, 1),
            // 소나무·자작나무·전나무·가문비나무·오리나무
            trees = listOf(1, 8, 11, 15, 42)
        )
        // 대전 — 갑천 자갈, 유등산 화강암, 도심 화단석
        "daejeon" -> riverNature.copy(
            rocks = listOf(22, 0, 20, 27),
            // 참나무·버드나무·물푸레나무·물오리나무·메타세콰이어·메타세콰이어 원뿔
            trees = listOf(0, 4, 25, 29, 31, 40)
        )
        // 전주 — 한옥마을 돌담과 따뜻한 사암, 서안 석회암
        "jeonju" -> limestoneNature.copy(
            rocks = listOf(19, 15, 12, 17, 13),
            // 참나무·벚나무·은행나무·느티나무·감나무
            trees = listOf(0, 2, 9, 24, 28)
        )
        // 대구 — 팔공산 암괴와 분지 바닥의 사암
        "daegu" -> fieldNature.copy(
            grass = listOf(0, 2, 3, 5), rocks = listOf(0, 1, 15, 2),
            // 소나무·곰솔·신갈나무·아까시나무·감나무
            trees = listOf(1, 7, 21, 27, 28)
        )
        // 광주 — 무등산 이끼 바위와 광주천 자갈
        "gwangju" -> riverNature.copy(
            rocks = listOf(23, 0, 22, 27), flowers = listOf(0, 3, 5),
            // 참나무·버드나무·동백나무·전나무·후박나무·물오리나무
            trees = listOf(0, 4, 6, 11, 19, 29)
        )
        // 울산 — 대왕암의 바위 첨탑과 태화강 십리대숲
        "ulsan" -> harbourNature.copy(
            rocks = listOf(26, 9, 8, 22),
            // 소나무·대나무·곰솔·노간주나무·후박나무
            trees = listOf(1, 5, 7, 18, 19)
        )
        // 부산 — 방파제 계기초 블록, 낙동강 자갈, 해운대 갯바위
        "busan" -> harbourNature.copy(
            rocks = listOf(18, 22, 11, 8, 26),
            // 소나무·버드나무·곰솔·후박나무·붉가시나무
            trees = listOf(1, 4, 7, 19, 20)
        )
        // 제주 — 현무암 기둥과 용암 성벽
        "jeju" -> basaltNature.copy(trees = listOf(6, 7, 11, 19, 33, 34, 41))  // 동백나무·곰솔·전나무·후박나무·진달래·감귤나무·제주 야자수
        // ── 갯벌·습지·강 명소 ─────────────────────────────────────────────
        // 강화도 갯벌 — 사구돌과 조개자갈이 깔린 거대한 썰물 길
        "ganghwa" -> tidalFlatNature.copy(rocks = listOf(24, 25, 11, 27), trees = listOf(1, 4, 7, 18))  // 소나무·버드나무·곰솔·노간주나무
        // 철원 평야 — 논둑의 사암 조각돌과 흙담
        "cheorwon" -> fieldNature.copy(
            grass = listOf(0, 1, 3, 4, 5), flowers = listOf(1, 4, 5), rocks = listOf(17, 15, 22, 19),
            // 참나무·자작나무·과수원·신갈나무
            trees = listOf(0, 8, 10, 21)
        )
        // 을숙도 하구 — 도라돌이 자갈밭처럼 흩어지고 갯벌 사구돌이 섞인다
        "eulsukdo" -> reedMarshNature.copy(
            reeds = listOf(1, 2, 3), rocks = listOf(27, 22, 24, 11),
            // 버드나무·자작나무·과수원·물오리나무
            trees = listOf(4, 8, 10, 29)
        )
        // 공릉천 — 수제 백로가 서는 여울의 자갈
        "gongneung" -> riverNature.copy(
            rocks = listOf(22, 27, 23, 10),
            // 참나무·버드나무·자작나무·물오리나무
            trees = listOf(0, 4, 8, 29)
        )
        // 광릉숲 — 수 голос지 화강암과 이끼 낀 바위, 향나무 숲
        "gwangneung" -> graniteNature.copy(
            rocks = listOf(0, 3, 23, 2, 1), flowers = listOf(2, 3, 5),
            // 참나무·자작나무·전나무·가문비나무·오리나무
            trees = listOf(0, 8, 11, 15, 42)
        )
        // 송도 갯벌 — 인공 방파제 옆 갯벌의 사구돌
        "songdo" -> tidalFlatNature.copy(rocks = listOf(24, 25, 18, 11), trees = listOf(1, 7, 10, 19))  // 소나무·곰솔·과수원·후박나무
        // 시화호 — 제방에 세워 둔 호안석 가비온
        "sihwa" -> reedMarshNature.copy(
            rocks = listOf(21, 27, 22, 24),
            // 버드나무·곰솔·자작나무·물오리나무
            trees = listOf(4, 7, 8, 29)
        )
        // 화성 습지 — 갯벌과 논이 맞닿는 물길
        "hwaseong" -> tidalFlatNature.copy(
            grass = listOf(0, 2, 3, 5), rocks = listOf(24, 25, 27, 11))
        // 안산 갈대습지 — 도라돌과 갯벌 사구돌
        "ansan" -> reedMarshNature.copy(rocks = listOf(27, 22, 21, 24), trees = listOf(4, 8, 10, 29))  // 버드나무·자작나무·과수원·물오리나무
        // 매향리 해안 — 갯바위 선반 위에 하얗게 마른 이끼
        "maehyang" -> seaStackNature.copy(rocks = listOf(11, 10, 8, 26), flowers = listOf(0, 3, 5),
            // 소나무·곰솔·노간주나무·후박나무
            trees = listOf(1, 7, 18, 19)
        )
        // 주남저수지 — 물가 자갈과 이끼 낀 바위
        "junam" -> lakeShoreNature.copy(
            grass = listOf(0, 1, 3, 4), rocks = listOf(22, 27, 23, 17),
            // 참나무·버드나무·과수원·느티나무
            trees = listOf(0, 4, 10, 24)
        )
        // 순천만 — 갈대밭에 섞인 갯벌 사구돌과 도라돌
        "suncheon" -> reedMarshNature.copy(
            reeds = listOf(1, 2, 3), rocks = listOf(24, 27, 25, 11),
            // 버드나무·곰솔·과수원·물오리나무
            trees = listOf(4, 7, 10, 29)
        )
        // 금강 하구 — 하굿물이 사암을 갈아 만든 자갈
        "geumgang" -> riverNature.copy(rocks = listOf(22, 10, 27, 8), trees = listOf(0, 4, 8, 29))  // 참나무·버드나무·자작나무·물오리나무
        // 고창 갯벌 — 사구돌과 조개자갈, 제방의 가비온
        "gochang" -> tidalFlatNature.copy(
            rocks = listOf(24, 25, 21, 11), flowers = listOf(1, 3, 5)
        )
        // 태안 천수만 — 방포 갯벌과 소나무 방파제
        "taean" -> seaStackNature.copy(
            rocks = listOf(24, 11, 18, 25),
            // 소나무·곰솔·전나무·노간주나무
            trees = listOf(1, 7, 11, 18)
        )
        // 우포늪 — 내륙습지의 도라돌과 갈대 뿌리
        "upo" -> reedMarshNature.copy(rocks = listOf(27, 22, 24, 23), trees = listOf(4, 8, 11, 29))  // 버드나무·자작나무·전나무·물오리나무
        // 제주 하도리 — 현무암과 갯벌의 만남
        "hadori" -> basaltNature.copy(
            rocks = listOf(4, 5, 6, 7), reeds = listOf(2, 3), flowers = listOf(3, 5),
            // 동백나무·곰솔·버드나무·후박나무
            trees = listOf(6, 7, 4, 19)
        )
        // 한라산 국립공원 — 현무암 성벽 위로 화강암이 어우러진다
        "hallasan" -> basaltNature.copy(
            rocks = listOf(5, 4, 1, 0), mountains = listOf(3, 2),
            // 동백나무·자작나무·전나무·가문비나무·잣나무
            trees = listOf(6, 8, 11, 15, 16)
        )
        // 임진강 — 강가 도라돌과 이끼 낀 바위
        "imjin" -> riverNature.copy(rocks = listOf(22, 27, 10, 23), trees = listOf(1, 0, 4, 25))  // 소나무·참나무·버드나무·물푸레나무
        // 왕피천 — 계곡의 화강암 노두와 물밑 자갈
        "wangpi" -> graniteNature.copy(
            rocks = listOf(0, 3, 2, 22),
            // 전나무·소나무·참나무·가문비나무·오리나무
            trees = listOf(11, 1, 0, 15, 42)
        )
        else -> when {
            region.kind == RegionKind.MOUNTAIN || "mountain" in region.habitats -> graniteNature
            region.kind == RegionKind.COAST || "coast" in region.habitats -> seaStackNature
            region.kind == RegionKind.WETLAND || "wetland" in region.habitats -> reedMarshNature
            region.kind == RegionKind.RIVER || "water" in region.habitats -> riverNature
            "field" in region.habitats -> fieldNature
            else -> urbanParkNature
        }
    }

    /**
     * One low-key high point per map. Coordinates are only an anchor: MapBuilder moves
     * it to the nearest free patch when a river, building, or regional landmark occupies
     * the exact tile. The names keep the silhouette tied to the real place.
     */
    private fun viewpointFor(region: RegionDef): ViewpointSpec {
        val data = when (region.id) {
            "seoul" -> MapPoint(29, 21) to "남산 한강 전망 데크"
            "incheon" -> MapPoint(13, 11) to "서해 갯벌 관찰 데크"
            "chuncheon" -> MapPoint(14, 22) to "의암호 호반 전망대"
            "gangneung" -> MapPoint(17, 12) to "대관령 솔숲 전망대"
            "sokcho" -> MapPoint(13, 18) to "설악 계곡 전망대"
            "daejeon" -> MapPoint(16, 23) to "갑천 둔치 전망 데크"
            "jeonju" -> MapPoint(27, 11) to "한옥마을 기와 전망대"
            "daegu" -> MapPoint(27, 10) to "팔공산 자락 전망대"
            "gwangju" -> MapPoint(27, 13) to "무등산 숲 전망대"
            "ulsan" -> MapPoint(22, 21) to "태화강 대숲 전망 데크"
            "busan" -> MapPoint(29, 12) to "용두산 항구 전망대"
            "jeju" -> MapPoint(24, 9) to "오름 바람 전망대"
            "ganghwa" -> MapPoint(15, 11) to "강화 갯벌 둑 전망대"
            "cheorwon" -> MapPoint(29, 18) to "철원 평야 두루미 데크"
            "eulsukdo" -> MapPoint(25, 10) to "을숙도 철새 전망대"
            "gongneung" -> MapPoint(24, 7) to "공릉천 둑 전망대"
            "gwangneung" -> MapPoint(13, 10) to "광릉숲 나무 전망대"
            "songdo" -> MapPoint(13, 12) to "송도 갯벌 탐조 데크"
            "sihwa" -> MapPoint(19, 10) to "시화호 갈대 전망대"
            "hwaseong" -> MapPoint(25, 18) to "화성호 도요새 데크"
            "ansan" -> MapPoint(25, 13) to "안산 갈대 전망대"
            "maehyang" -> MapPoint(19, 18) to "매향리 해안 전망대"
            "junam" -> MapPoint(17, 9) to "주남저수지 물새 데크"
            "suncheon" -> MapPoint(29, 12) to "순천만 용산 전망대"
            "geumgang" -> MapPoint(19, 8) to "금강 하구 군무 전망대"
            "gochang" -> MapPoint(14, 10) to "고창 갯벌 둑 전망대"
            "taean" -> MapPoint(24, 11) to "안면도 솔숲 전망대"
            "upo" -> MapPoint(25, 12) to "우포늪 물안개 데크"
            "hadori" -> MapPoint(26, 12) to "하도리 철새 데크"
            "hallasan" -> MapPoint(21, 9) to "한라산 숲 전망대"
            "imjin" -> MapPoint(25, 10) to "임진강 두루미 전망대"
            "wangpi" -> MapPoint(26, 10) to "왕피천 계곡 전망대"
            else -> MapPoint(28, 11) to "지역 전망 데크"
        }
        val height = when {
            region.kind == RegionKind.MOUNTAIN || "mountain" in region.habitats -> 3
            region.kind == RegionKind.WETLAND || region.kind == RegionKind.RIVER -> 1
            else -> 2
        }
        return ViewpointSpec(data.first, data.second, height)
    }

    fun forRegion(region: RegionDef): RegionMapStyle {
        // ── 도시 12곳: 각각의 실제 지형을 담은 전용 팔레트 ──
        val palette = when (region.id) {
            "seoul" -> seoulUrban
            "incheon" -> westTidal
            "chuncheon" -> chuncheonLakes
            "gangneung" -> gangneungPine
            "sokcho" -> highland
            "daejeon" -> daejeonValley
            "jeonju" -> jeonjuHanok
            "daegu" -> daeguBasin
            "gwangju" -> freshwater
            "ulsan" -> southCoast
            "busan" -> southCoast
            "jeju" -> jejuVolcanic
            else -> {
                val edges = region.waterEdges
                when {
                    Dir.S in edges && (Dir.E in edges || Dir.W in edges) -> southCoast
                    Dir.W in edges && ("wetland" in region.habitats || region.kind == RegionKind.WETLAND) -> westTidal
                    Dir.W in edges -> westCoast
                    Dir.E in edges && "wetland" in region.habitats -> marsh.copy(
                        water = 0xFFC5E5EA.toInt(), shore = 0xFFF1E8D2.toInt(), seaDepth = 3, shoreDepth = 2
                    )
                    Dir.E in edges -> eastCoast
                    Dir.S in edges && "wetland" in region.habitats -> marsh
                    Dir.S in edges -> southCoast
                    region.kind == RegionKind.MOUNTAIN || ("mountain" in region.habitats && region.rockDensity >= 0.07) -> highland
                    region.kind == RegionKind.RIVER || region.lake -> freshwater
                    region.kind == RegionKind.WETLAND || "wetland" in region.habitats -> marsh
                    region.city -> cityPark
                    "field" in region.habitats -> field
                    else -> woodland
                }
            }
        }

        // A lake/river is placed where the named landscape actually sits in the local map,
        // rather than repeating the same lower-right pond in every region.
        val lake = when (region.id) {
            "chuncheon" -> MapLake(MapPoint(9, 21), 5, 3)        // 의암호(서남) — 소양강과 북한강이 만나는 호반도시
            "gangneung" -> MapLake(MapPoint(30, 21), 4, 3)        // 경포호, 동해 바로 안쪽 석호
            "sokcho" -> MapLake(MapPoint(30, 21), 3, 3)           // 청초호, 동해와 설악 사이 석호
            "cheorwon" -> MapLake(MapPoint(29, 21), 4, 3)        // 평야의 저수지와 논습지
            "gwangju" -> MapLake(MapPoint(30, 22), 4, 3)
            "eulsukdo", "geumgang", "gongneung", "imjin", "suncheon", "wangpi" -> null
            "sihwa" -> MapLake(MapPoint(11, 21), 4, 4)           // 시화호 서쪽 석호
            "hwaseong" -> MapLake(MapPoint(30, 22), 4, 3)
            "ansan" -> MapLake(MapPoint(30, 21), 4, 3)
            "junam" -> MapLake(MapPoint(10, 21), 5, 3)           // 주남 개방수면 서쪽
            "taean" -> MapLake(MapPoint(29, 21), 4, 3)           // 천수만/간월호
            "upo" -> MapLake(MapPoint(29, 21), 5, 4)
            "hadori" -> MapLake(MapPoint(30, 20), 4, 3)           // 해안 뒤 민물습지
            "busan" -> null                                      // 낙동강 하구는 강으로 표현
            "jeju" -> null                                       // 화산섬 — 호수 대신 오름과 분화구
            else -> if (region.lake) {
                // 모든 지방 저수지를 우하단에 복제하지 않고, 지도별로 호숫가 위치를 바꾼다.
                val coves = listOf(MapPoint(10, 8), MapPoint(29, 8), MapPoint(10, 21), MapPoint(29, 21))
                val slot = Math.floorMod(region.id.hashCode(), coves.size)
                MapLake(coves[slot], if (slot % 2 == 0) 4 else 3, if (slot % 2 == 0) 3 else 2)
            } else null
        }

        val rivers = when (region.id) {
            // 서울 — 한강이 도시를 가로지른다. 도심을 남북으로 가르며 서해로 빠진다.
            // 실제 한강처럼 중앙보다 약간 북쪽(남산 남쪽)을 동서로 흐르게 하고,
            // 중랑천이 북동에서 합류한다.
            "seoul" -> listOf(
                MapRiver(
                    listOf(MapPoint(37, 10), MapPoint(30, 10), MapPoint(23, 11), MapPoint(15, 10), MapPoint(5, 11)),
                    halfWidth = 1.25f, bankWidth = 1.0f, reedBanks = false
                ),
                MapRiver( // 중랑천 지류 — 북동에서 한강으로
                    listOf(MapPoint(26, 3), MapPoint(24, 6), MapPoint(22, 9)),
                    halfWidth = 0.6f, bankWidth = 0.8f, reedBanks = false
                )
            )
            // 춘천 — 북한강·소양강이 의암호에서 만난다. 호수에서 북동쪽 산골까지 물길이 길게 이어진다.
            "chuncheon" -> listOf(
                MapRiver(
                    listOf(MapPoint(27, 4), MapPoint(24, 8), MapPoint(18, 12), MapPoint(13, 17), MapPoint(9, 20)),
                    halfWidth = 0.9f, bankWidth = 1.4f, reedBanks = false
                )
            )
            // 강릉 — 남대천이 태백산에서 동해로 짧게 빠지며 경포호와 만난다.
            "gangneung" -> listOf(
                MapRiver(
                    listOf(MapPoint(8, 6), MapPoint(16, 10), MapPoint(22, 15), MapPoint(27, 19), MapPoint(30, 21)),
                    halfWidth = 0.8f, bankWidth = 1.2f, reedBanks = false
                )
            )
            // 속초 — 설악 계곡물이 청초호로 흘러든다.
            "sokcho" -> listOf(
                MapRiver(
                    listOf(MapPoint(8, 5), MapPoint(14, 9), MapPoint(19, 14), MapPoint(25, 18), MapPoint(30, 21)),
                    halfWidth = 0.7f, bankWidth = 1.0f, reedBanks = false
                )
            )
            // 전주 — 전주천이 서쪽 녹지대를 따라 남으로 흐른다.
            "jeonju" -> listOf(
                MapRiver(
                    listOf(MapPoint(11, 4), MapPoint(13, 8), MapPoint(11, 13), MapPoint(14, 18), MapPoint(12, 25)),
                    halfWidth = 0.8f, bankWidth = 1.3f, reedBanks = false
                )
            )
            // 대구 — 신천이 도심을 남북으로 가르고 동쪽에서 금호강이 합류하는 분지 도시.
            "daegu" -> listOf(
                MapRiver(
                    listOf(MapPoint(13, 3), MapPoint(12, 8), MapPoint(14, 12), MapPoint(13, 17), MapPoint(15, 23), MapPoint(14, 26)),
                    halfWidth = 0.9f, bankWidth = 1.4f, reedBanks = false
                ),
                MapRiver( // 금호강 합류 — 동쪽 팔공산에서 서쪽으로
                    listOf(MapPoint(33, 6), MapPoint(26, 9), MapPoint(20, 11), MapPoint(14, 12)),
                    halfWidth = 0.7f, bankWidth = 1.1f, reedBanks = false
                )
            )
            // 광주 — 광주천과 영산강 수계가 무등산 기슭에서 남으로 흐른다.
            "gwangju" -> listOf(
                MapRiver(
                    listOf(MapPoint(12, 4), MapPoint(14, 8), MapPoint(12, 12), MapPoint(15, 16), MapPoint(13, 21), MapPoint(14, 26)),
                    halfWidth = 0.9f, bankWidth = 1.4f
                )
            )
            // 울산 — 태화강이 서쪽 내륙에서 동해로 S자를 그리며 빠진다.
            "ulsan" -> listOf(
                MapRiver(
                    listOf(MapPoint(5, 25), MapPoint(12, 25), MapPoint(19, 26), MapPoint(26, 25), MapPoint(33, 22), MapPoint(36, 18)),
                    halfWidth = 1.0f, bankWidth = 1.4f, reedBanks = false
                )
            )
            // 부산 — 낙동강 하구가 남해로 빠지고 온천천이 동쪽 도심을 지난다. 광장 동쪽을 피해 흐른다.
            "busan" -> listOf(
                MapRiver( // 낙동강 하구 — 서부산에서 남해로
                    listOf(MapPoint(11, 3), MapPoint(12, 8), MapPoint(15, 13), MapPoint(16, 18), MapPoint(18, 24), MapPoint(20, 28)),
                    halfWidth = 1.4f, bankWidth = 1.6f, reedBanks = false
                ),
                MapRiver( // 온천천/수영강 — 동부산 도심 동쪽을 남으로
                    listOf(MapPoint(28, 3), MapPoint(27, 9), MapPoint(28, 14), MapPoint(27, 19), MapPoint(28, 24)),
                    halfWidth = 0.7f, bankWidth = 1.0f, reedBanks = false
                )
            )
            // 대전 — 갑천이 북동에서 서쪽을 돌아 남으로, 대전천·유등천이 Y자로 합류하는 분지.
            "daejeon" -> listOf(
                MapRiver( // 갑천 본류 — 동북에서 서쪽으로 휘어 남하
                    listOf(MapPoint(36, 5), MapPoint(32, 8), MapPoint(27, 9), MapPoint(14, 11), MapPoint(10, 17), MapPoint(8, 24)),
                    halfWidth = 0.9f, bankWidth = 1.4f, reedBanks = false
                ),
                MapRiver( // 유등천/대전천 합류 — 남동에서 북서로 갑천으로
                    listOf(MapPoint(26, 26), MapPoint(22, 20), MapPoint(18, 15), MapPoint(14, 11)),
                    halfWidth = 0.7f, bankWidth = 1.1f, reedBanks = false
                )
            )
            // Gongneungcheon: a soft northeast-to-southwest meander through reed banks.
            "gongneung" -> listOf(
                MapRiver(
                    listOf(MapPoint(31, 5), MapPoint(27, 9), MapPoint(21, 11), MapPoint(16, 15), MapPoint(10, 18), MapPoint(5, 24)),
                    halfWidth = 1.0f, bankWidth = 1.7f
                )
            )
            // Geumgang and the Imjin both widen toward the west; the river mouth meets the Yellow Sea.
            "geumgang" -> listOf(
                MapRiver(
                    listOf(MapPoint(36, 8), MapPoint(29, 9), MapPoint(22, 11), MapPoint(15, 12), MapPoint(8, 15), MapPoint(4, 16)),
                    halfWidth = 1.25f, bankWidth = 1.8f
                )
            )
            "imjin" -> listOf(
                MapRiver(
                    listOf(MapPoint(14, 4), MapPoint(12, 8), MapPoint(14, 11), MapPoint(12, 15), MapPoint(9, 19), MapPoint(5, 22)),
                    halfWidth = 1.1f, bankWidth = 1.7f
                )
            )
            // Nakdong Estuary flows down into the southern sea; Suncheon Bay is a tidal river mouth.
            "eulsukdo" -> listOf(
                MapRiver(
                    listOf(MapPoint(20, 4), MapPoint(18, 9), MapPoint(21, 14), MapPoint(19, 20), MapPoint(20, 26)),
                    halfWidth = 1.6f, bankWidth = 2.0f
                )
            )
            "suncheon" -> listOf(
                MapRiver(
                    listOf(MapPoint(15, 4), MapPoint(20, 8), MapPoint(17, 13), MapPoint(23, 18), MapPoint(24, 25)),
                    halfWidth = 1.1f, bankWidth = 1.8f
                )
            )
            // 서해 갯골은 얕은 물길이 모래톱과 갈대섬 사이를 굽이돈다.
            "ganghwa" -> listOf(
                MapRiver(
                    listOf(MapPoint(36, 8), MapPoint(29, 10), MapPoint(22, 13), MapPoint(15, 17), MapPoint(7, 20)),
                    halfWidth = 0.65f, bankWidth = 2.0f
                )
            )
            "songdo" -> listOf(
                MapRiver(
                    listOf(MapPoint(36, 6), MapPoint(30, 9), MapPoint(25, 13), MapPoint(18, 17), MapPoint(9, 20)),
                    halfWidth = 0.55f, bankWidth = 1.8f
                )
            )
            "sihwa" -> listOf(
                MapRiver(
                    listOf(MapPoint(35, 5), MapPoint(32, 10), MapPoint(27, 14), MapPoint(21, 18), MapPoint(16, 23)),
                    halfWidth = 0.75f, bankWidth = 1.6f
                )
            )
            "hwaseong" -> listOf(
                MapRiver(
                    listOf(MapPoint(35, 5), MapPoint(31, 9), MapPoint(28, 14), MapPoint(23, 19), MapPoint(18, 24)),
                    halfWidth = 0.6f, bankWidth = 1.8f
                )
            )
            "ansan" -> listOf(
                MapRiver(
                    listOf(MapPoint(36, 7), MapPoint(32, 11), MapPoint(31, 16), MapPoint(30, 21)),
                    halfWidth = 0.55f, bankWidth = 1.5f
                )
            )
            "maehyang" -> listOf(
                MapRiver(
                    listOf(MapPoint(7, 25), MapPoint(13, 23), MapPoint(18, 20), MapPoint(22, 17)),
                    halfWidth = 0.55f, bankWidth = 1.8f
                )
            )
            "junam" -> listOf(
                MapRiver(
                    listOf(MapPoint(31, 5), MapPoint(24, 9), MapPoint(18, 13), MapPoint(13, 17), MapPoint(10, 20)),
                    halfWidth = 0.7f, bankWidth = 1.8f
                )
            )
            "gochang" -> listOf(
                MapRiver(
                    listOf(MapPoint(8, 4), MapPoint(11, 9), MapPoint(9, 14), MapPoint(7, 19), MapPoint(5, 24)),
                    halfWidth = 0.55f, bankWidth = 1.6f
                )
            )
            "upo" -> listOf(
                MapRiver(
                    listOf(MapPoint(35, 7), MapPoint(32, 11), MapPoint(29, 15), MapPoint(28, 19), MapPoint(29, 21)),
                    halfWidth = 0.55f, bankWidth = 1.8f
                )
            )
            "hadori" -> listOf(
                MapRiver(
                    listOf(MapPoint(35, 12), MapPoint(33, 15), MapPoint(31, 18), MapPoint(29, 20)),
                    halfWidth = 0.5f, bankWidth = 1.4f
                )
            )
            "wangpi" -> listOf(
                MapRiver(
                    listOf(MapPoint(28, 3), MapPoint(22, 8), MapPoint(25, 13), MapPoint(18, 18), MapPoint(14, 25)),
                    halfWidth = 0.8f, bankWidth = 1.7f, reedBanks = false
                )
            )
            else -> emptyList()
        }

        val fieldRows = region.id in setOf("cheorwon", "hwaseong", "maehyang", "junam", "suncheon", "geumgang", "gochang", "taean", "imjin", "daegu", "jeonju")
        return RegionMapStyle(
            foliageFilter = palette.foliage,
            waterFilter = palette.water,
            shoreFilter = palette.shore,
            stoneFilter = palette.stone,
            seaDepth = palette.seaDepth,
            shoreDepth = palette.shoreDepth,
            tidalReeds = palette.tidalReeds,
            lake = lake,
            rivers = rivers,
            fieldRows = fieldRows,
            northBuildingY = when (region.id) {
                "seoul" -> 3   // 한강 북쪽 — 광화문·북촌 블록을 위로 올린다
                "busan" -> 5
                "daegu" -> 6   // 분지 — 북쪽 팔공산 기슭에 블록이 바짝 붙지 않게
                else -> 5
            },
            natureArt = natureArtFor(region),
            viewpoint = viewpointFor(region)
        )
    }
}
