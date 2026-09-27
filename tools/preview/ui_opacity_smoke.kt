package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.*
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.UiKit

/** Regression checks for shadow alpha leaking into readable UI surfaces. */
object UiOpacitySmoke {
    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 1f }
        }
    }

    private fun image(background: Int, draw: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        draw(canvas)
        return bitmap
    }

    private fun pixel(bitmap: Bitmap, x: Int = 120, y: Int = 80) = bitmap.image.getRGB(x, y)

    @JvmStatic
    fun main(args: Array<String>) {
        // Verify the preview itself models Android's Paint alpha with shaders.
        for (shader in listOf(
            LinearGradient(0f, 0f, 240f, 0f, Color.WHITE, Color.WHITE, Shader.TileMode.CLAMP),
            RadialGradient(120f, 80f, 100f, Color.WHITE, Color.WHITE, Shader.TileMode.CLAMP)
        )) {
            val p = Paint().apply { color = Color.argb(64, 0, 0, 0); this.shader = shader }
            val result = image(Color.BLACK) { c ->
                c.drawRect(0f, 0f, 240f, 160f, p)
                c.drawRect(0f, 0f, 10f, 10f, Paint().apply { color = Color.WHITE })
            }
            check(Color.red(pixel(result)) in 63..65) { "Shader ignores Paint.alpha" }
            check(pixel(result, 5, 5) == Color.WHITE) { "Shader alpha leaked to a solid draw" }

            val white = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            Canvas(white).drawColor(Color.WHITE)
            for (mode in 0..2) {
                val bitmapResult = image(Color.BLACK) { c ->
                    c.drawRect(0f, 0f, 240f, 160f, p)
                    when (mode) {
                        0 -> c.drawBitmap(white, 0f, 0f, null)
                        1 -> c.drawBitmap(white, null, RectF(0f, 0f, 2f, 2f), null)
                        else -> c.drawBitmap(white, Matrix(), null)
                    }
                }
                check(pixel(bitmapResult, 0, 0) == Color.WHITE) { "Shader alpha leaked to bitmap $mode" }
            }
            val tint = 0xFF4A3728.toInt()
            val tinted = image(Color.BLACK) { c ->
                c.drawBitmap(white, 0f, 0f, Paint().apply {
                    colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
                })
            }
            check(pixel(tinted, 0, 0) == tint) { "Pixel-font tint missing" }
        }

        // PixelFont must keep its requested RGB visible and apply fading only once.
        for (alpha in listOf(255, 128, 0)) {
            val text = image(Color.BLACK) { c ->
                com.pizzaandbird.game.PixelFont.draw(
                    c, "128400", 20f, 80f, 3, Color.argb(alpha, 74, 55, 40)
                )
            }
            val maxRed = (0 until 160).maxOf { y ->
                (0 until 240).maxOf { x -> Color.red(pixel(text, x, y)) }
            }
            check(kotlin.math.abs(maxRed - 74 * alpha / 255) <= 1) {
                "Pixel-font tint/fade lost: alpha=$alpha, red=$maxRed"
            }
        }

        val game = Game(TestContext())
        game.onSurfaceChanged(960, 540)
        val rect = RectF(20f, 20f, 220f, 140f)
        val surfaces: List<Pair<String, (Canvas) -> Unit>> = listOf(
            "dialog / HUD panel" to { c -> UiKit.panel(c, game, rect) },
            "card" to { c -> UiKit.card(c, game, rect) },
            "selected card" to { c -> UiKit.card(c, game, rect, selected = true) },
            "button" to { c -> UiKit.button(c, game, rect, "", UiKit.GOLD, UiKit.INK, 12f) },
            "circle button" to { c -> UiKit.circleButton(c, game, 120f, 80f, 50f, "", 12f) },
            "badge" to { c -> UiKit.badge(c, game, rect, "", UiKit.GOLD, UiKit.INK, 12f) },
            "icon circle" to { c -> UiKit.iconCircle(c, game, 120f, 80f, 50f, "", 12f) },
            "dark hint chip" to { c -> UiKit.darkChip(c, game, 120f, 80f, "") }
        )
        // Interiors must be independent of world brightness, even after another UI draw.
        repeat(2) {
            for ((name, draw) in surfaces) {
                val dark = image(Color.BLACK, draw)
                val light = image(Color.WHITE, draw)
                check(pixel(dark) == pixel(light)) { "$name still reveals the world" }
                check(pixel(dark) != Color.BLACK && pixel(light) != Color.WHITE) { "$name not drawn" }
            }
        }
        // The bar already has an opaque track: check the fill color rather than backdrop leakage.
        val green = 0xFF228833.toInt()
        val bar = image(Color.BLACK) { c -> UiKit.bar(c, game, 20f, 50f, 200f, 60f, 1f, green, green) }
        check(pixel(bar, 120, 90) == green) { "Gauge fill inherited inner-shadow alpha" }

        // Explicitly translucent disabled buttons must keep their existing behavior.
        val disabled: (Canvas) -> Unit = { c ->
            UiKit.button(c, game, rect, "", Color.argb(120, 90, 80, 70), UiKit.INK, 12f)
        }
        check(pixel(image(Color.BLACK, disabled)) != pixel(image(Color.WHITE, disabled)))
        println("UI opacity smoke OK: shader alpha, bitmap isolation/tint, 8 surfaces, gauge, disabled button")
    }
}
