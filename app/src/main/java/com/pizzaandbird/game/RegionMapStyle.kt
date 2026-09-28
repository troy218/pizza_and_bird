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
    val natureArt: NatureArtSet = NatureArtSet()
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

    // Each family is deliberately made from visibly different silhouettes, not just a tint.
    private val urbanParkNature = NatureArtSet(
        grass = listOf(0, 1, 3, 4), tallGrass = listOf(0, 2), flowers = listOf(0, 2, 3),
        reeds = listOf(0, 1), rocks = listOf(0, 6, 8), mountains = listOf(0, 2), trees = listOf(0, 2, 9, 10)
    )
    private val tidalFlatNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(1, 4, 5),
        reeds = listOf(1, 2, 3), rocks = listOf(5, 8, 3), mountains = listOf(0, 2), trees = listOf(1, 7, 8)
    )
    private val lakeShoreNature = NatureArtSet(
        grass = listOf(0, 1, 2, 4), tallGrass = listOf(0, 2), flowers = listOf(0, 1, 5),
        reeds = listOf(0, 3), rocks = listOf(3, 6, 1), mountains = listOf(0, 2), trees = listOf(1, 4, 8, 11)
    )
    private val alpineNature = NatureArtSet(
        grass = listOf(0, 1, 2, 4), tallGrass = listOf(0, 1), flowers = listOf(2, 3, 5),
        reeds = listOf(0, 1), rocks = listOf(2, 0, 6), mountains = listOf(1, 2), trees = listOf(1, 8, 11)
    )
    private val coastalPineNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(0, 3, 5),
        reeds = listOf(0, 2), rocks = listOf(5, 2, 7), mountains = listOf(0, 2), trees = listOf(1, 7, 8)
    )
    private val riverNature = NatureArtSet(
        grass = listOf(0, 1, 2, 4), tallGrass = listOf(0, 2, 3), flowers = listOf(0, 1, 5),
        reeds = listOf(0, 1, 3), rocks = listOf(3, 6, 1), mountains = listOf(0, 2), trees = listOf(0, 4, 8, 11)
    )
    private val reedMarshNature = NatureArtSet(
        grass = listOf(0, 2, 3, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(1, 4, 5),
        reeds = listOf(1, 2, 3), rocks = listOf(3, 5, 8), mountains = listOf(0, 2), trees = listOf(4, 8, 10)
    )
    private val fieldNature = NatureArtSet(
        grass = listOf(0, 1, 3, 4), tallGrass = listOf(0, 2, 3), flowers = listOf(1, 3, 4),
        reeds = listOf(0, 2), rocks = listOf(7, 3, 0), mountains = listOf(0, 2), trees = listOf(0, 9, 10)
    )
    private val volcanicNature = NatureArtSet(
        grass = listOf(0, 2, 4, 5), tallGrass = listOf(1, 2, 3), flowers = listOf(3, 5),
        reeds = listOf(2, 3), rocks = listOf(4, 5, 2), mountains = listOf(3, 2), trees = listOf(6, 7, 11)
    )

    /** Per-place accents on top of its broader habitat, so nearby wetlands don't all look alike. */
    private fun natureArtFor(region: RegionDef): NatureArtSet = when (region.id) {
        "seoul" -> urbanParkNature
        "incheon" -> tidalFlatNature.copy(rocks = listOf(5, 8, 1), trees = listOf(1, 7, 10))
        "chuncheon" -> lakeShoreNature
        "gangneung" -> coastalPineNature.copy(rocks = listOf(5, 2, 6), trees = listOf(1, 7, 11))
        "sokcho" -> alpineNature.copy(rocks = listOf(2, 6, 0), mountains = listOf(2, 1))
        "daejeon" -> riverNature.copy(trees = listOf(0, 4, 9, 10), flowers = listOf(0, 1, 4))
        "jeonju" -> fieldNature.copy(rocks = listOf(7, 3, 8), trees = listOf(0, 2, 9, 10))
        "daegu" -> fieldNature.copy(grass = listOf(0, 2, 3, 5), rocks = listOf(7, 2, 0), trees = listOf(1, 7, 10))
        "gwangju" -> riverNature.copy(rocks = listOf(6, 0, 2), trees = listOf(0, 4, 6, 11), flowers = listOf(0, 3, 5))
        "ulsan" -> coastalPineNature.copy(rocks = listOf(5, 3, 2), trees = listOf(1, 5, 7))
        "busan" -> coastalPineNature.copy(rocks = listOf(5, 3, 8), trees = listOf(1, 7, 4))
        "jeju" -> volcanicNature
        "hadori" -> volcanicNature.copy(
            rocks = listOf(4, 5, 8), trees = listOf(6, 7, 4), reeds = listOf(2, 3), flowers = listOf(3, 5)
        )
        "ganghwa" -> tidalFlatNature.copy(rocks = listOf(5, 3, 8), trees = listOf(1, 7, 4))
        "cheorwon" -> fieldNature.copy(grass = listOf(0, 1, 3, 4, 5), flowers = listOf(1, 4, 5), trees = listOf(0, 8, 10))
        "eulsukdo" -> reedMarshNature.copy(reeds = listOf(1, 2, 3), rocks = listOf(3, 5, 8))
        "gongneung" -> riverNature.copy(rocks = listOf(3, 1, 6), trees = listOf(0, 4, 8))
        "gwangneung" -> alpineNature.copy(rocks = listOf(6, 0, 2), trees = listOf(0, 8, 11), flowers = listOf(2, 3, 5))
        "songdo" -> tidalFlatNature.copy(rocks = listOf(5, 8, 4), trees = listOf(1, 7, 10))
        "sihwa" -> reedMarshNature.copy(rocks = listOf(5, 3, 8), trees = listOf(4, 7, 8))
        "hwaseong" -> tidalFlatNature.copy(grass = listOf(0, 2, 3, 5), rocks = listOf(5, 8, 7))
        "ansan" -> reedMarshNature.copy(rocks = listOf(3, 6, 8), trees = listOf(4, 8, 10))
        "maehyang" -> coastalPineNature.copy(rocks = listOf(5, 8, 7), flowers = listOf(0, 3, 5))
        "junam" -> lakeShoreNature.copy(grass = listOf(0, 1, 3, 4), trees = listOf(0, 4, 10), reeds = listOf(0, 2, 3))
        "suncheon" -> reedMarshNature.copy(rocks = listOf(5, 3, 8), trees = listOf(4, 7, 10))
        "geumgang" -> riverNature.copy(rocks = listOf(3, 5, 1), trees = listOf(0, 4, 8))
        "gochang" -> tidalFlatNature.copy(rocks = listOf(5, 8, 3), flowers = listOf(1, 3, 5))
        "taean" -> coastalPineNature.copy(rocks = listOf(7, 5, 2), trees = listOf(1, 7, 11))
        "upo" -> reedMarshNature.copy(rocks = listOf(6, 3, 8), trees = listOf(4, 8, 11))
        "hallasan" -> volcanicNature.copy(rocks = listOf(4, 2, 6), mountains = listOf(3, 2), trees = listOf(6, 11, 8))
        "imjin" -> riverNature.copy(rocks = listOf(3, 2, 6), trees = listOf(1, 0, 4))
        "wangpi" -> riverNature.copy(rocks = listOf(3, 6, 2), trees = listOf(11, 1, 0))
        else -> when {
            region.kind == RegionKind.MOUNTAIN || "mountain" in region.habitats -> alpineNature
            region.kind == RegionKind.COAST || "coast" in region.habitats -> coastalPineNature
            region.kind == RegionKind.WETLAND || "wetland" in region.habitats -> reedMarshNature
            region.kind == RegionKind.RIVER || "water" in region.habitats -> riverNature
            "field" in region.habitats -> fieldNature
            else -> urbanParkNature
        }
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

        val fieldRows = region.id in setOf("cheorwon", "hwaseong", "maehyang", "junam", "suncheon", "geumgang", "gochang", "taean", "imjin", "gongneung", "chuncheon", "daegu", "jeonju")
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
            natureArt = natureArtFor(region)
        )
    }
}
