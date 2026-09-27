package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Cosmetic keepsakes: one equipped slot, no stacking from inventory. */
object Charms {
    data class Charm(val id: String, val name: String, val price: Int, val color: Int,
                     val description: String, val base: Int) {
        fun luck(s: GameState): Int = base + when (id) {
            "moon" -> if (s.isNight()) 12 else 0
            "rain" -> if (s.weather() == Weather.RAIN || s.weather() == Weather.SNOW) 14 else 0
            "feather" -> if (s.weather() == Weather.WIND) 16 else 0
            else -> 0
        }
    }
    val all = listOf(
        Charm("clover", "네잎클로버 브로치", 18000, 0xFF71CF8D.toInt(), "언제나 행운 +8", 8),
        Charm("moon", "초승달 펜던트", 24000, 0xFFF3CF70.toInt(), "행운 +3 · 밤에는 추가 +12", 3),
        Charm("rain", "빗방울 귀걸이", 22000, 0xFF70C9EF.toInt(), "행운 +2 · 비·눈에 추가 +14", 2),
        Charm("feather", "바람깃털 키링", 20000, 0xFFD5A1EC.toInt(), "행운 +2 · 강풍에 추가 +16", 2)
    )
    fun of(id: String?) = all.firstOrNull { it.id == id }
    fun equipped(s: GameState) = of(s.charmId)?.takeIf { it.id in s.ownedCharms }
    fun buy(s: GameState, id: String): Boolean {
        val item = of(id) ?: return false
        if (id in s.ownedCharms || s.money < item.price) return false
        s.money -= item.price
        s.ownedCharms.add(id)
        s.charmId = id
        return true
    }

    private val p by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val path by lazy { Path() }
    /** Same silhouette in the bag, on the ground and on a character. */
    fun draw(c: Canvas, item: Charm, x: Float, y: Float, size: Float, time: Float = 0f) {
        c.save()
        c.translate(x, y + sin(time * 3f) * size * 0.045f)
        c.scale(size / 20f, size / 20f)
        p.style = Paint.Style.FILL
        p.color = 0xFF3C3044.toInt()
        c.drawCircle(0f, 0f, 10f, p)
        p.color = 0xFFFFEED0.toInt()
        c.drawCircle(0f, 0f, 8.8f, p)
        p.color = item.color
        c.drawCircle(0f, 0f, 7.4f, p)
        p.color = 0xFF334454.toInt()
        when (item.id) {
            "clover" -> {
                for (dx in listOf(-2.5f, 2.5f)) for (dy in listOf(-2.5f, 2.5f))
                    c.drawCircle(dx, dy, 2.8f, p)
                p.strokeWidth = 1.5f
                c.drawLine(0f, 1f, 2f, 6f, p)
            }
            "moon" -> {
                c.drawCircle(-1f, 0f, 5f, p)
                p.color = item.color
                c.drawCircle(2f, -2f, 4.5f, p)
            }
            "rain" -> {
                path.reset(); path.moveTo(0f, -6f)
                path.cubicTo(-10f, 4f, 0f, 9f, 4f, 3f)
                path.quadTo(6f, 1f, 0f, -6f); path.close()
                c.drawPath(path, p)
            }
            else -> {
                c.save(); c.rotate(30f)
                c.drawOval(-2.8f, -6f, 2.8f, 4f, p)
                p.color = 0xFFFFF5E2.toInt(); p.strokeWidth = 1f
                c.drawLine(0f, -4f, 0f, 7f, p); c.restore()
            }
        }
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(-5f, -5f, 1.4f, p)
        c.restore()
    }
}

/** Large, touch-friendly collection cards. Coordinates use a fixed UI canvas. */
class CharmOverlay(scene: Scene, private val shop: Boolean = false) : Overlay(scene) {
    override val coversWorld = true
    private val p = Type.paintPx(14f, false, 0f, 0xFF493E39.toInt())
    private fun scale() = minOf(scene.game.screenW / 360f, scene.game.screenH / 540f)
    override fun handleInput(input: Input) {
        if (input.justB || input.justBack) { finished = true; return }
        val tap = input.consumeTapScreen() ?: return
        val k = scale()
        val x = (tap.x - (scene.game.screenW - 360f * k) / 2) / k
        val y = (tap.y - (scene.game.screenH - 540f * k) / 2) / k
        if (x !in 18f..342f) return
        if (y in 484f..528f) { finished = true; return }
        val index = ((y - 112f) / 86f).toInt()
        if (y < 112f || index !in Charms.all.indices || y > 112f + index * 86f + 78f) return
        val item = Charms.all[index]
        val g = scene.game
        val s = g.state
        if (item.id in s.ownedCharms) {
            s.charmId = if (s.charmId == item.id) "" else item.id
        } else if (shop) {
            if (!Charms.buy(s, item.id)) { g.toast("구매할 돈이 부족해요"); return }
            g.toast("${item.name} 구매 · 착용 완료!")
        } else {
            g.toast("주민 근처의 반짝이는 장신구를 줍거나 서울 상점에서 구매하세요")
            return
        }
        SaveManager.save(g.context, s)
    }
    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        c.drawColor(0xEF202735.toInt())
        val k = scale()
        c.save()
        c.translate((g.screenW - 360f * k) / 2, (g.screenH - 540f * k) / 2)
        c.scale(k, k)
        p.color = 0xFFF7EFDF.toInt()
        c.drawRoundRect(10f, 12f, 350f, 532f, 18f, 18f, p)
        fun text(t: String, x: Float, y: Float, size: Float, color: Int = 0xFF493E39.toInt()) {
            p.color = color; p.textSize = size; c.drawText(t, x, y, p)
        }
        text(if (shop) "여행자의 장신구 상점" else "나의 행운 장신구", 24f, 43f, 21f)
        text("한 개만 착용 · 현재 행운 +${Charms.equipped(s)?.luck(s) ?: 0}", 24f, 69f, 14f)
        text(if (shop) "보유 ${won(s.money)} · 구매하면 바로 착용" else "착용 중인 카드를 다시 누르면 해제", 24f, 93f, 12f)
        Charms.all.forEachIndexed { i, item ->
            val y = 112f + i * 86f
            val owned = item.id in s.ownedCharms
            val active = Charms.equipped(s)?.id == item.id
            p.color = if (active) 0xFFDEEDD7.toInt() else 0xFFFFFFFF.toInt()
            c.drawRoundRect(18f, y, 342f, y + 78f, 12f, 12f, p)
            Charms.draw(c, item, 49f, y + 37f, 42f)
            text(item.name, 80f, y + 23f, 16f)
            text(item.description, 80f, y + 44f, 12f)
            text(when { active -> "착용 중 · 지금 +${item.luck(s)}"
                owned -> "보유 · 눌러서 착용"
                shop -> "${won(item.price)} · 구매"
                else -> "미발견 · 필드 / 상점" }, 80f, y + 65f, 12f, 0xFF527454.toInt())
        }
        text("조건부 효과는 날씨와 시간에 맞춰 자동 적용돼요.", 22f, 474f, 12f)
        p.color = 0xFF455B55.toInt()
        c.drawRoundRect(18f, 484f, 342f, 523f, 12f, 12f, p)
        text("닫기", 164f, 510f, 16f, 0xFFFFFFFF.toInt())
        c.restore()
    }
}
