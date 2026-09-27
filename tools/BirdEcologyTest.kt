import com.pizzaandbird.game.*
import java.util.Random
import kotlin.math.abs

/** Run with compiled game classes, android.jar and kotlin-stdlib on the JVM. */
fun main() {
    val size = 20
    val tiles = Array(size) { IntArray(size) { T.GRASS.ordinal } }
    for (y in 2..7) for (x in 2..7) tiles[y][x] = T.WATER.ordinal
    tiles[10][10] = T.TREE.ordinal
    tiles[15][15] = T.PLAZA.ordinal
    tiles[15][16] = T.HOUSE_DOOR.ordinal
    val map = GameMap(Regions.ALL.first(), size, size, tiles,
        Array(size) { IntArray(size) }, Array(size) { IntArray(size) },
        Array(size) { IntArray(size) }, emptyList(), false, 0, 0)
    fun score(name: String, x: Int, y: Int) = BirdEcology.suitability(Birds.byName.getValue(name), map, x, y)
    check(score("흰뺨검둥오리", 4, 4) > 0)
    check(score("흰뺨검둥오리", 15, 15) == 0.0)
    check(score("왜가리", 2, 2) > 0)
    check(score("왜가리", 4, 4) == 0.0) // no shallow-water proxy in the centre
    check(score("왜가리", 15, 15) == 0.0)
    check(score("참새", 4, 4) == 0.0)
    check(score("참새", 15, 15) > 0)
    check(score("참새", 16, 15) == 0.0)
    check(score("참새", -1, 4) == 0.0)
    check(score("곤줄박이", 10, 11) > 0)
    check(score("곤줄박이", 15, 15) == 0.0)
    check(Birds.byName.getValue("흰뺨검둥오리").seasons == BirdSeason.ALL)
    check(Tier.values().sumOf { BirdEcology.tierMass(it) } == 100.0)
    check(BirdEcology.meanInterval(5f, Weather.SUNNY) < BirdEcology.meanInterval(13f, Weather.SUNNY))
    check(BirdEcology.meanInterval(13f, Weather.SUNNY) < BirdEcology.meanInterval(23f, Weather.SUNNY))
    check(BirdEcology.meanInterval(13f, Weather.WIND) > BirdEcology.meanInterval(13f, Weather.SUNNY))
    val rng = Random(42)
    val samples = List(100000) { BirdEcology.nextInterval(9f, Weather.SUNNY, rng).toDouble() }
    check(samples.all { it >= 4.0 && it.isFinite() })
    check(abs(samples.average() - 19.0) < 0.2)
    check(samples.any { it > 60 }) // genuine quiet spells, not a fixed periodic refill
    println("Bird ecology: habitat exclusions, residents, rarity budget and 100000 interval samples passed")
}
