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
    val northBuildingY: Int = 5
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

    fun forRegion(region: RegionDef): RegionMapStyle {
        val edges = region.waterEdges
        val palette = when {
            region.id == "jeju" -> island
            region.id == "sokcho" -> highland
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

        // A lake/river is placed where the named landscape actually sits in the local map,
        // rather than repeating the same lower-right pond in every region.
        val lake = when (region.id) {
            "chuncheon" -> MapLake(MapPoint(9, 21), 5, 3)        // Uiam Lake lies west/southwest
            "gangneung" -> MapLake(MapPoint(30, 21), 4, 3)        // Gyeongpo Lagoon, just inland of the east coast
            "sokcho" -> MapLake(MapPoint(30, 21), 3, 3)           // Cheongcho Lake beside the sea
            "cheorwon" -> MapLake(MapPoint(29, 21), 4, 3)        // reservoirs and wet fields
            "gwangju" -> MapLake(MapPoint(30, 22), 4, 3)
            "eulsukdo", "geumgang", "gongneung", "imjin", "suncheon", "wangpi" -> null
            "sihwa" -> MapLake(MapPoint(11, 21), 4, 4)           // lagoon behind the western shore
            "hwaseong" -> MapLake(MapPoint(30, 22), 4, 3)
            "ansan" -> MapLake(MapPoint(30, 21), 4, 3)
            "junam" -> MapLake(MapPoint(10, 21), 5, 3)           // Junam's open water to the west
            "taean" -> MapLake(MapPoint(29, 21), 4, 3)           // Cheonsu Bay / Ganwol reservoir
            "upo" -> MapLake(MapPoint(29, 21), 5, 4)
            "hadori" -> MapLake(MapPoint(30, 20), 4, 3)           // freshwater wetland just inland of east coast
            else -> if (region.lake) MapLake(MapPoint(30, 22), 4, 3) else null
        }

        val rivers = when (region.id) {
            // Han River runs east-to-west across Seoul, south of the northern neighbourhoods.
            "seoul" -> listOf(
                MapRiver(
                    listOf(MapPoint(37, 7), MapPoint(30, 7), MapPoint(23, 7), MapPoint(15, 7), MapPoint(5, 7)),
                    halfWidth = 0.9f, bankWidth = 0.8f, reedBanks = false
                )
            )
            // Jeonjucheon and Sincheon thread through the western green belts of their cities.
            "jeonju" -> listOf(
                MapRiver(
                    listOf(MapPoint(11, 4), MapPoint(13, 8), MapPoint(11, 13), MapPoint(14, 18), MapPoint(12, 25)),
                    halfWidth = 0.8f, bankWidth = 1.3f, reedBanks = false
                )
            )
            "daegu" -> listOf(
                MapRiver(
                    listOf(MapPoint(13, 3), MapPoint(12, 8), MapPoint(14, 12), MapPoint(13, 17), MapPoint(15, 23), MapPoint(14, 26)),
                    halfWidth = 0.9f, bankWidth = 1.4f, reedBanks = false
                )
            )
            "gwangju" -> listOf(
                MapRiver(
                    listOf(MapPoint(12, 4), MapPoint(14, 8), MapPoint(12, 12), MapPoint(15, 16), MapPoint(13, 21), MapPoint(14, 26)),
                    halfWidth = 0.9f, bankWidth = 1.4f
                )
            )
            "ulsan" -> listOf(
                MapRiver(
                    listOf(MapPoint(5, 25), MapPoint(12, 25), MapPoint(19, 26), MapPoint(26, 25), MapPoint(33, 22), MapPoint(36, 18)),
                    halfWidth = 1.0f, bankWidth = 1.4f, reedBanks = false
                )
            )
            // Gapcheon bends around Daejeon's west side before turning south.
            "daejeon" -> listOf(
                MapRiver(
                    listOf(MapPoint(36, 5), MapPoint(32, 8), MapPoint(27, 9), MapPoint(14, 11), MapPoint(10, 17), MapPoint(8, 24)),
                    halfWidth = 0.9f, bankWidth = 1.4f, reedBanks = false
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
            // Wangpi stream follows a narrow mountain valley.
            "wangpi" -> listOf(
                MapRiver(
                    listOf(MapPoint(28, 3), MapPoint(22, 8), MapPoint(25, 13), MapPoint(18, 18), MapPoint(14, 25)),
                    halfWidth = 0.8f, bankWidth = 1.7f, reedBanks = false
                )
            )
            else -> emptyList()
        }

        val fieldRows = region.id in setOf("cheorwon", "hwaseong", "maehyang", "junam", "suncheon", "geumgang", "gochang", "taean", "imjin")
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
            northBuildingY = if (region.id == "seoul") 3 else 5
        )
    }
}
