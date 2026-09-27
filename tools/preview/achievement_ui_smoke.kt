package com.pizzaandbird.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.pizzaandbird.game.*

/** [P06] Exercises the actual menu tab, statistics navigation, detail dialog, and list paging. */
object AchievementUiSmoke {
    private fun field(obj: Any, name: String): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        error("missing field $name")
    }

    private fun render(game: Game) = game.render(
        Canvas(Bitmap.createBitmap(game.screenW, game.screenH, Bitmap.Config.ARGB_8888))
    )

    private fun advance(game: Game, frames: Int = 30) {
        repeat(frames) { game.update(1f / 60f) }
        render(game)
    }

    private fun tap(game: Game, x: Float, y: Float) {
        val point = listOf(Triple(0, x, y))
        game.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_DOWN, pointers = point))
        game.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_UP, pointers = point))
        game.update(1f / 60f)
    }

    private fun center(rect: RectF): Pair<Float, Float> =
        (rect.left + rect.right) / 2f to (rect.top + rect.bottom) / 2f

    @JvmStatic
    fun main(args: Array<String>) {
        val game = Game(Context())
        game.onSurfaceChanged(2400, 1080)
        val scene = WorldScene(game, "seoul", SpawnKind.SAVED)
        game.scene = scene
        val menu = MenuOverlay(scene)
        scene.openOverlay(menu)
        advance(game)

        val tabs = field(menu, "tabRects") as List<*>
        check(tabs.size == 8) { "expected 8 menu tabs, got ${tabs.size}" }
        val achieve = tabs.mapNotNull { it as? Pair<*, *> }.first { it.second.toString() == "ACHIEVE" }
        center(achieve.first as RectF).let { tap(game, it.first, it.second) }
        check(field(menu, "tab").toString() == "ACHIEVE") { "ACHIEVE tab did not open" }
        render(game)

        val menuButtons = field(menu, "btnRects") as List<*>
        val statsButton = menuButtons.mapNotNull { it as? Triple<*, *, *> }
            .first { it.second == "stats-detail" }
        center(statsButton.first as RectF).let { tap(game, it.first, it.second) }
        check(scene.overlay is StatsOverlay) { "statistics detail button did not open StatsOverlay" }
        advance(game)

        val stats = scene.overlay as StatsOverlay
        center(field(stats, "nextRect") as RectF).let { tap(game, it.first, it.second) }
        check(field(stats, "page") == 1) { "statistics next-page button did not work" }
        center(field(stats, "closeRect") as RectF).let { tap(game, it.first, it.second) }
        check(scene.overlay is MenuOverlay) { "statistics close did not return to achievements" }
        advance(game)

        val menuAfterStats = scene.overlay as MenuOverlay
        check(field(menuAfterStats, "tab").toString() == "ACHIEVE") { "return did not preserve ACHIEVE tab" }
        val rows = (field(menuAfterStats, "btnRects") as List<*>).mapNotNull { it as? Triple<*, *, *> }
        val firstAchievement = rows.first { it.second == "first_shot" }
        center(firstAchievement.first as RectF).let { tap(game, it.first, it.second) }
        check(scene.overlay is AchievementDetailOverlay) { "achievement row did not open detail" }
        advance(game)

        val detail = scene.overlay as AchievementDetailOverlay
        center(field(detail, "returnRect") as RectF).let { tap(game, it.first, it.second) }
        check(scene.overlay is MenuOverlay) { "detail return did not reopen the list" }
        advance(game)

        val menuAfterDetail = scene.overlay as MenuOverlay
        val pageButtons = (field(menuAfterDetail, "btnRects") as List<*>).mapNotNull { it as? Triple<*, *, *> }
        val nextPage = pageButtons.first { it.second == "ach-next" }
        center(nextPage.first as RectF).let { tap(game, it.first, it.second) }
        check(field(menuAfterDetail, "achievementPage") == 1) { "achievement page navigation failed" }
        println("Achievement UI smoke OK: tab, stats, detail, and achievement pagination")
    }
}
