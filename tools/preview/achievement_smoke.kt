package com.pizzaandbird.preview

import android.content.Context
import com.pizzaandbird.game.Ach
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.SaveManager
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.WorldScene

/** Headless smoke test for P06 distance accumulation, unlocks, and prefs reload. */
object AchievementSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        val context = Context()
        val game = Game(context)
        game.onSurfaceChanged(2400, 1080)
        val state = game.state.apply {
            photos = 0
            money = 0
            level = 1
            day = 1
            playSeconds = 0f
            birdCounts.clear()
            bestStars.clear()
            visited.clear()
            visited.add("seoul")
            ownedHomes.clear()
        }
        val scene = WorldScene(game, "seoul", SpawnKind.SAVED)
        game.scene = scene

        Ach.tick(scene, state) // seed the baseline snapshot
        val stateBefore = state.toJSON().toString()
        Ach.onMove(context, 421_950f, bike = false)
        Ach.onMove(context, Float.NaN, bike = false) // invalid movement must be ignored
        Thread.sleep(1_050L) // allow the internal one-second throttle to elapse
        Ach.tick(scene, state)

        check(Ach.isUnlocked(context, "walk_marathon")) { "walk_marathon was not unlocked" }
        check(Ach.stats(context).distWalkPx == 421_950L) { "walk distance was not accumulated" }
        check(state.toJSON().toString() == stateBefore) { "P06 must not mutate GameState" }
        check(Ach.unlocked() > 0) { "public unlocked() API returned no progress" }

        val prefs = context.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
        val raw = prefs.getString("feat_stats_v1", null)
        check(raw != null && raw.contains("walk_marathon")) { "feat_stats_v1 did not persist the unlock" }

        // Simulate a process-local cache loss and reload from a fresh Context instance.
        for (fieldName in listOf("progress", "prefsCache", "cachedJson")) {
            val field = Ach.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(Ach, null)
        }
        val tickField = Ach.javaClass.getDeclaredField("lastTickMs")
        tickField.isAccessible = true
        tickField.setLong(Ach, Long.MIN_VALUE)
        val afterRestart = Context()
        check(Ach.isUnlocked(afterRestart, "walk_marathon")) { "unlock did not reload from prefs" }
        check(Ach.stats(afterRestart).distWalkPx == 421_950L) { "distance did not reload from prefs" }
        println("Achievement smoke OK: movement, threshold, GameState isolation, feat_stats_v1 reload")
    }
}
