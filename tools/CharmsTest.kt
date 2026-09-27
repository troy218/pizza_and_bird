import com.pizzaandbird.game.*
import org.json.JSONObject

/** Run against compiled game classes, with tools/backup_test JVM graphics/JSON stubs first. */
fun main() {
    val s = GameState()
    check(Charms.all.map { it.id }.distinct().size == 4)
    check(!Charms.buy(s, "unknown"))
    for (item in Charms.all) {
        s.money = item.price - 1
        check(!Charms.buy(s, item.id))
        check(item.id !in s.ownedCharms)
        s.money = item.price
        check(Charms.buy(s, item.id))
        check(s.money == 0 && Charms.equipped(s) == item)
        check(!Charms.buy(s, item.id))
    }
    s.worldTime = 12f
    s.weatherId = Weather.SUNNY.id
    check(Charms.of("clover")!!.luck(s) == 8)
    check(Charms.of("moon")!!.luck(s) == 3)
    s.worldTime = 0f
    check(Charms.of("moon")!!.luck(s) == 15)
    s.worldTime = 12f
    check(Charms.of("moon")!!.luck(s) == 3)
    for (weather in Weather.values()) {
        s.weatherId = weather.id
        check(Charms.of("rain")!!.luck(s) == if (weather in listOf(Weather.RAIN, Weather.SNOW)) 16 else 2)
        check(Charms.of("feather")!!.luck(s) == if (weather == Weather.WIND) 18 else 2)
    }
    val drops = Regions.ALL.map { Charms.all[Math.floorMod(it.id.hashCode(), Charms.all.size)].id }.toSet()
    check(drops == Charms.all.map { it.id }.toSet()) // every charm can be found in a region
    s.charmId = ""
    val without = s.effectiveLuck()
    s.charmId = "clover"
    check(s.effectiveLuck() == (without + 8f).coerceAtMost(100f))
    s.luck = 100f
    check(s.effectiveLuck() == 100f)
    s.luck = 50f
    val loaded = GameState.fromJSON(JSONObject(s.toJSON().toString()))
    check(loaded.ownedCharms == s.ownedCharms && loaded.charmId == "clover")
    check(Charms.equipped(loaded)!!.luck(loaded) == 8) // owned items never stack
    loaded.charmId = ""
    check(Charms.equipped(loaded) == null)
    val old = GameState.fromJSON(JSONObject())
    check(old.ownedCharms.isEmpty() && Charms.equipped(old) == null)
    val bad = GameState.fromJSON(JSONObject().put("charmId", "moon"))
    check(bad.charmId.isEmpty())
    println("Charms: purchase, duplicate protection, conditions, equip, save round-trip and legacy saves passed")
}
