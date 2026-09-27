package com.pizzaandbird.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.GfxStats
import com.pizzaandbird.game.DayCycle
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.Healing
import com.pizzaandbird.game.Season
import com.pizzaandbird.game.SeasonFx
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.Weather
import com.pizzaandbird.game.WorldScene
import com.pizzaandbird.game.cherryBlossomIntensity

/** 꽃바람 시간표 + 실제 화면/월드/향 입자의 생성·정리 회귀 테스트. Android 기기 불필요. */
object CherryBlossomSmoke {
    private const val DT = 1f / 60f

    private fun field(obj: Any, name: String): Any =
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)

    private fun flakes(fx: SeasonFx): List<*> = field(fx, "flakes") as List<*>
    private fun screenPetals(fx: SeasonFx): Int = flakes(fx).count { field(it!!, "shape") == SeasonFx.S_PETAL }
    private fun particles(world: WorldScene): List<*> = field(world, "particles") as List<*>
    private fun worldPetals(world: WorldScene): Int = particles(world).count { field(it!!, "petal") == true }
    private fun scents(): List<*> = field(Healing, "scents") as List<*>

    private fun schedule() {
        // 두 해를 분 단위로 훑어 비개화기·휴지기·밤·비·눈에는 단 한 번도 켜지지 않는지 확인.
        for (day in 1..56) for (weather in Weather.values()) for (minute in 0 until 24 * 60) {
            val hour = minute / 60f
            val intensity = cherryBlossomIntensity(day, hour, weather)
            val expected = (day in 3..5 || day in 31..33) &&
                weather != Weather.RAIN && weather != Weather.SNOW &&
                ((minute > 9 * 60 && minute < 10 * 60) || (minute > 14 * 60 && minute < 15 * 60))
            check(intensity in 0f..1f && (intensity > 0f) == expected) {
                "꽃바람 시간표 불일치: day=$day hour=$hour weather=$weather intensity=$intensity"
            }
            if (intensity > 0f) check(DayCycle.sunAltitude(hour, Season.SPRING) > 0f)
        }
        for (start in listOf(9f, 14f)) {
            check(cherryBlossomIntensity(3, start, Weather.SUNNY) == 0f)
            check(cherryBlossomIntensity(3, start + 0.05f, Weather.SUNNY) in 0.3f..0.4f)
            check(cherryBlossomIntensity(3, start + 0.5f, Weather.SUNNY) == 1f)
            check(cherryBlossomIntensity(3, start + 0.95f, Weather.SUNNY) in 0.3f..0.4f)
            check(cherryBlossomIntensity(3, start + 1f, Weather.SUNNY) == 0f)
        }
        // 다른 씬이 전역 계절을 바꿔도 현재 날짜를 기준으로 판단해야 한다.
        DayCycle.season = Season.WINTER
        check(cherryBlossomIntensity(3, 9.5f, Weather.SUNNY) == 1f)
        DayCycle.season = Season.SPRING
        println("OK 꽃바람 시간표: 개화기·시간 경계·밤·강수·다음 해·페이드")
    }

    private fun screenEffects() {
        val fx = SeasonFx()
        val canvas = Canvas(Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888))
        fun update(night: Boolean = false, weather: Weather = Weather.SUNNY, intensity: Float = 1f) =
            fx.update(DT, Season.SPRING, weather, 960f, 540f, night, intensity)
        fun drawnPetals(): Long {
            GfxStats.reset()
            fx.draw(canvas, Season.SPRING, 960f, 540f)
            return GfxStats.drawCircle // 꽃잎 끝의 점은 꽃잎마다 한 번 그린다.
        }

        fx.update(DT, Season.SPRING, Weather.SUNNY, 960f, 540f)
        check(screenPetals(fx) == 0) // 연출 신호 없이는 봄이라는 이유만으로 생성하지 않는다.
        update()
        check(screenPetals(fx) == 30 && drawnPetals() == 30L)
        update(intensity = 0.2f)
        check(flakes(fx).all { field(it!!, "alpha") == 47 })
        for ((night, weather) in listOf(true to Weather.SUNNY, false to Weather.RAIN, false to Weather.SNOW)) {
            update()
            update(night, weather) // 잘못된 양수 강도가 전달돼도 밤·강수는 차단한다.
            check(flakes(fx).isEmpty() && drawnPetals() == 0L)
        }
        update()
        update(intensity = 0f)
        check(flakes(fx).isEmpty() && drawnPetals() == 0L)

        // 꽃잎 제한이 다른 계절의 기존 야간 입자까지 끄면 안 된다.
        for ((season, count) in listOf(Season.SUMMER to 5, Season.AUTUMN to 26, Season.WINTER to 22)) {
            fx.update(DT, season, Weather.SUNNY, 960f, 540f, night = true)
            check(flakes(fx).size == count && screenPetals(fx) == 0)
        }
        println("OK 화면 효과: 생성·페이드·즉시 제거·야간 렌더·다른 계절 보존")
    }

    private fun worldEffects() {
        val game = Game(Context())
        game.onSurfaceChanged(1280, 720)
        val state = game.state
        fun moment(day: Int = 3, hour: Float = 9.5f, weather: Weather = Weather.SUNNY) {
            state.day = day
            state.worldTime = hour
            state.weatherId = weather.id
            state.weatherSeconds = 1000f
        }
        moment()
        val world = WorldScene(game, "seoul", SpawnKind.HOME)
        game.scene = world
        val fx = field(world, "seasonFx") as SeasonFx
        val rest = world.javaClass.getDeclaredMethod("restAtBench").apply { isAccessible = true }
        fun hasPicnic(): Boolean {
            val moments = state.healing.optJSONArray("moments") ?: return false
            return (0 until moments.length()).any { moments.getString(it) == "spring_picnic" }
        }
        fun active() {
            moment()
            repeat(60) { game.update(DT) }
            check(screenPetals(fx) > 0 && worldPetals(world) > 0 && scents().isNotEmpty())
        }
        fun inactive(label: String) {
            game.update(DT)
            check(screenPetals(fx) == 0 && worldPetals(world) == 0 && scents().isEmpty()) {
                "$label 전환 후 꽃잎/분홍 향 입자가 남아 있다"
            }
        }

        active()
        moment(hour = 22f)
        inactive("밤")
        // 야간 벤치 기념에는 '벚꽃잎이 어깨에 내려앉았다'를 해금하지 않는다.
        rest.invoke(world)
        check(!hasPicnic())
        val benchParticles = particles(world).filter { field(it!!, "petal") == false }
        check(benchParticles.isNotEmpty())
        game.update(DT)
        check(particles(world).containsAll(benchParticles)) // 꽃잎 외 효과는 지우지 않는다.
        repeat(60) { game.update(DT) }
        check(screenPetals(fx) == 0 && worldPetals(world) == 0 && scents().isEmpty())

        for (weather in listOf(Weather.RAIN, Weather.SNOW)) {
            active()
            moment(weather = weather)
            inactive(weather.label)
        }
        for (hour in listOf(0f, 5.5f, 10f, 12f, 15f, 18.6f)) {
            active()
            moment(hour = hour)
            inactive("$hour 시")
        }
        for (day in listOf(1, 2, 6, 7, 29, 30, 34, 35)) {
            active()
            moment(day = day)
            inactive("$day 일째")
        }
        // 장면 재진입도 타이머를 새로 시작하지 않는다 (밤·휴지기·다음 해 개화기).
        for ((day, hour) in listOf(3 to 22f, 3 to 12f, 31 to 14.5f)) {
            moment(day, hour)
            val reentered = WorldScene(game, "seoul", SpawnKind.HOME)
            game.scene = reentered
            game.update(DT)
            val expected = state.cherryBlossomIntensity() > 0f
            check((screenPetals(field(reentered, "seasonFx") as SeasonFx) > 0) == expected)
            check((worldPetals(reentered) > 0) == expected)
        }
        game.scene = world
        // 벤치 보상에는 25초 쿨다운이 있다. 밤에 쉰 뒤 충분히 기다리고
        // 개화 시간으로 맞춰, 쿨다운이 아닌 꽃바람 조건을 검증한다.
        repeat(26 * 60) { game.update(DT) }
        moment()
        rest.invoke(world)
        check(hasPicnic())
        println("OK 실제 월드: 화면/월드/향 동기화·기존 꽃잎 제거·재진입·벤치 기념")
    }

    @JvmStatic fun main(args: Array<String>) {
        schedule()
        screenEffects()
        worldEffects()
        println("cherry blossom smoke OK")
    }
}
