package com.pizzaandbird.game

import java.util.Random
import kotlin.math.ln

/** Habitat associations are qualitative; weights/seconds are game tuning, not survey estimates.
 * See docs/BIRD_ECOLOGY.md. No network or device-clock dependency.
 */
object BirdEcology {
    private val natural = setOf(T.GRASS, T.TALLGRASS, T.FLOWER, T.REED, T.SAND)
    private val swimmers = setOf("오리과", "논병아리과", "아비과", "바다오리과", "가마우지과")
    private val waders = setOf("백로과", "저어새과", "황새과", "도요과", "물떼새과", "검은머리물떼새과", "장다리물떼새과")
    private val regionHintCache = HashMap<String, List<RegionDef>>()

    /**
     * A curated range is authoritative; otherwise the region must advertise at least one
     * broad habitat for the species. Fine-scale terrain is checked separately by suitability.
     */
    fun regionAllows(def: BirdDef, region: RegionDef): Boolean =
        def.onlyRegions?.let { region.id in it }
            ?: def.habitats.any { it in region.habitats }

    /**
     * Returns the best places to look, ordered by the same regional factors used by spawning.
     * This is a relative in-game encounter ranking, not a field-survey probability.
     */
    fun recommendedRegions(def: BirdDef, limit: Int = 3): List<RegionDef> {
        if (limit <= 0) return emptyList()
        val ranked = regionHintCache.getOrPut(def.id) {
            val seasons = Season.values().filter { BirdSeason.valueOf(it.name) in def.seasons }
            val windows = BirdTimeWindow.values().filter { window ->
                window in def.timeWindows && when (def.active) {
                    "night" -> window == BirdTimeWindow.NIGHT
                    "day" -> window != BirdTimeWindow.NIGHT
                    else -> true
                }
            }
            Regions.ALL.mapNotNull { region ->
                if (!regionAllows(def, region)) return@mapNotNull null
                var bestWeight = 0.0
                for (season in seasons) for (window in windows) {
                    val day = season.ordinal * Season.DAYS_PER_SEASON + 1
                    val hour = when (window) {
                        BirdTimeWindow.DAWN -> 6f
                        BirdTimeWindow.DAY -> 9f
                        BirdTimeWindow.DUSK -> 18f
                        BirdTimeWindow.NIGHT -> 22f
                    }
                    val weight = Birds.spawnWeight(def, region, day, hour) *
                            seasonBirdMultiplier(def, season, Weather.SUNNY) *
                            SpawnTables.weight(def, region.id, region.habitats, season, window == BirdTimeWindow.NIGHT)
                    if (weight > bestWeight) bestWeight = weight
                }
                if (bestWeight > 0.0) region to bestWeight else null
            }
                .sortedWith(compareByDescending<Pair<RegionDef, Double>> { it.second }.thenBy { it.first.name })
                .map { it.first }
        }
        return ranked.take(limit)
    }

    /** Bird feet, not the sprite's top-left, are the terrain anchor. Water is bird-only. */
    fun suitability(def: BirdDef, map: GameMap, x: Int, y: Int): Double {
        if (!regionAllows(def, map.region)) return 0.0
        if (x !in 1 until map.w - 1 || y !in 1 until map.h - 1) return 0.0
        val tile = map.t(x, y)
        if (tile !in natural && tile != T.WATER && tile != T.PATH && tile != T.PLAZA) return 0.0
        fun near(radius: Int, predicate: (T) -> Boolean): Boolean {
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until map.w && ny in 0 until map.h && predicate(map.t(nx, ny))) return true
            }
            return false
        }
        val water = near(2) { it == T.WATER }
        val bank = near(1) { it in natural }
        val trees = near(2) { it == T.TREE }
        val reeds = near(2) { it == T.REED }
        val coast = "coast" in map.region.habitats
        // Specialist rules take precedence over the broad regional habitat tags.
        if (def.familyName in swimmers || def.name == "물닭") {
            return when {
                tile == T.WATER -> 1.0
                tile in natural && water -> 0.35
                else -> 0.0
            }
        }
        if (def.familyName in waders || def.name == "물총새") {
            return when {
                tile == T.WATER && bank -> 0.65
                tile in natural && water -> 1.0
                else -> 0.0
            }
        }
        if (def.familyName == "두루미과") {
            return if (tile in setOf(T.GRASS, T.TALLGRASS, T.REED) && (water || reeds)) 1.0 else 0.0
        }
        if (def.familyName == "갈매기과") {
            return if (tile == T.WATER || (tile == T.SAND && coast) || (tile in natural && water)) 1.0 else 0.0
        }
        if (tile == T.WATER) return 0.0
        if (def.familyName in setOf("박새과", "딱다구리과", "동고비과", "나무발발이과", "오목눈이과", "동박새과", "팔색조과")) {
            return if (tile in natural && trees) 1.0 else 0.0
        }
        if (def.familyName in setOf("개개비과", "휘파람새과", "붉은머리오목눈이과")) {
            return if (tile in setOf(T.REED, T.TALLGRASS) || (tile in natural && reeds)) 1.0 else 0.0
        }
        var score = 0.0
        for (habitat in def.habitats) {
            val k = when (habitat) {
                "forest" -> if (tile in natural && trees) 1.0 else 0.0
                "mountain" -> if (tile in natural && (trees || near(2) { it == T.ROCK || it == T.MOUNTAIN })) 0.8 else 0.0
                "wetland" -> if (tile in natural && (water || reeds)) 0.9 else 0.0
                "water" -> if (tile in natural && water) 0.8 else 0.0
                "coast" -> if (coast && tile == T.SAND) 0.9 else 0.0
                "field" -> if (tile in setOf(T.GRASS, T.TALLGRASS, T.FLOWER)) 0.7 else 0.0
                "city" -> if (tile in setOf(T.GRASS, T.FLOWER, T.PATH, T.PLAZA)) 0.65 else 0.0
                else -> 0.0
            }
            score = maxOf(score, k)
        }
        return score
    }

    /** A large checklist must not make rare species collectively outnumber common birds. */
    fun tierMass(tier: Tier): Double = when (tier) {
        Tier.COMMON -> 86.0
        Tier.UNCOMMON -> 12.0
        Tier.RARE -> 1.8
        Tier.LEGEND -> 0.2
    }

    fun meanInterval(hour: Float, weather: Weather): Float {
        val base = when (BirdTimeWindow.ofHour(hour)) {
            BirdTimeWindow.DAWN -> 12f
            BirdTimeWindow.DUSK -> 17f
            BirdTimeWindow.DAY -> if (hour in 11f..15f) 28f else 19f
            BirdTimeWindow.NIGHT -> 48f
        }
        return base * when (weather) {
            Weather.RAIN -> 1.7f
            Weather.WIND -> 1.9f
            Weather.SNOW -> 1.5f
            else -> 1f
        }
    }

    /** Irregular quiet spells, with a minimum gap; sampled once per attempt. */
    fun nextInterval(hour: Float, weather: Weather, random: Random): Float =
        4f + (-ln(1.0 - random.nextDouble()) * (meanInterval(hour, weather) - 4f)).toFloat()
}
