package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Locale
import kotlin.math.min

/** [P06] 업적 탭에서 여는 누적 통계 화면. */
class StatsOverlay(
    scene: Scene,
    private val onReturn: () -> Unit
) : Overlay(scene) {
    private data class Metric(val icon: String, val label: String, val value: String, val tint: Int)

    private var panelR = RectF()
    private var closeRect = RectF()
    private var prevRect = RectF()
    private var nextRect = RectF()
    private var page = 0
    private var pageCount = 1

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) {
            returnToMenu()
            return
        }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) {
            returnToMenu()
            return
        }
        if (prevRect.contains(tap.x, tap.y) && page > 0) {
            page--
            scene.game.sfx(Audio.Sfx.TAP, 0.4f)
            return
        }
        if (nextRect.contains(tap.x, tap.y) && page < pageCount - 1) {
            page++
            scene.game.sfx(Audio.Sfx.TAP, 0.4f)
        }
    }

    private fun returnToMenu() {
        finished = true
        onReturn()
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val stats = Ach.stats(g.context)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val d = g.density
        fun dp(v: Float) = v * d
        fun textDp(v: Float) = TypeScale.px(dp(v))
        val p = statsText

        UiKit.dim(c, g, 154, scene.overlay?.bornAt ?: 0L)
        val pw = min(w * 0.92f, dp(560f))
        val ph = min(h * 0.92f, dp(560f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        UiKit.panel(c, g, panelR)

        val closeCx = panelR.right - dp(23f)
        val closeCy = panelR.top + dp(22f)
        closeRect = RectF(closeCx - dp(20f), closeCy - dp(20f), closeCx + dp(20f), closeCy + dp(20f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(12f), "✕", 11f)

        p.textSize = textDp(16f)
        p.color = 0xFF4A3728.toInt()
        UiKit.drawIconText(c, g, "📊 모험 통계", panelR.left + dp(16f), panelR.top + dp(29f), p)
        p.textSize = textDp(9.5f)
        p.color = 0xFF8A7360.toInt()
        c.drawText("이 여행에서 쌓인 발자국과 셔터의 기록", panelR.left + dp(16f), panelR.top + dp(46f), p)
        UiKit.divider(c, g, panelR.left + dp(14f), panelR.right - dp(14f), panelR.top + dp(57f))

        val playHours = stats.totalPlaySeconds / 3600L
        val playMinutes = stats.totalPlaySeconds % 3600L / 60L
        val metrics = listOf(
            Metric("👟", "걸은 거리", String.format(Locale.US, "%.1f km", stats.walkKm), 0xFFD9EED7.toInt()),
            Metric("🚲", "자전거 거리", String.format(Locale.US, "%.1f km", stats.bikeKm), 0xFFD9EAF4.toInt()),
            Metric("📷", "누적 촬영", "${stats.photos}장", 0xFFFFE7C3.toInt()),
            Metric("🐦", "발견한 새", "${stats.discoveredSpecies}/${Birds.ALL.size}종", 0xFFE9DDF2.toInt()),
            Metric("✨", "3성으로 만난 새", "${stats.threeStarSpecies}종", 0xFFFFE7C3.toInt()),
            Metric("🗺", "방문 지역", "${stats.visitedRegions}/${Regions.ALL.size}곳", 0xFFD9EAF4.toInt()),
            Metric("🌙", "밤의 사진", "${stats.nightPhotos}장", 0xFFE2DFF5.toInt()),
            Metric("🌧", "빗속의 사진", "${stats.rainPhotos}장", 0xFFD9EAF4.toInt()),
            Metric("❄️", "눈 속 3성 사진", "${stats.snowThreeStarPhotos}장", 0xFFDDEEF5.toInt()),
            Metric("🍕", "구워 낸 피자", "약 ${stats.pizzasProduced}판", 0xFFFFE7C3.toInt()),
            Metric("🌅", "게임 날짜", "${stats.daysPlayed}일 · 최고 ${stats.maxDay}일", 0xFFFFE7C3.toInt()),
            Metric("☕", "함께한 시간", "${playHours}시간 ${playMinutes}분", 0xFFD9EED7.toInt()),
            Metric("💰", "최고 잔고", won(stats.maxMoney), 0xFFFFE7C3.toInt()),
            Metric("🌱", "최고 레벨", "Lv.${stats.maxLevel}", 0xFFD9EED7.toInt()),
            Metric("📍", "사진을 남긴 지역", "${stats.photosByRegion.size}/${Regions.ALL.size}곳", 0xFFD9EAF4.toInt()),
            Metric("🏅", "해금한 업적", "${stats.unlockedCount}/${Ach.total}", 0xFFFFE7C3.toInt())
        )

        val pageSize = 8
        pageCount = (metrics.size + pageSize - 1) / pageSize
        page = page.coerceIn(0, pageCount - 1)
        val shown = metrics.drop(page * pageSize).take(pageSize)
        val left = panelR.left + dp(12f)
        val right = panelR.right - dp(12f)
        val top = panelR.top + dp(68f)
        val bottom = panelR.bottom - dp(49f)
        val gapX = dp(7f)
        val gapY = dp(6f)
        val colW = (right - left - gapX) / 2f
        val rows = (shown.size + 1) / 2
        val rowH = ((bottom - top - gapY * (rows - 1)) / rows).coerceAtLeast(dp(28f))

        for ((index, metric) in shown.withIndex()) {
            val col = index % 2
            val row = index / 2
            val x = left + col * (colW + gapX)
            val y = top + row * (rowH + gapY)
            val r = RectF(x, y, x + colW, y + rowH)
            UiKit.card(c, g, r, 8f, borderColor = 0xFFD7C7AA.toInt(), borderWidthDp = 1.2f)
            val iconR = min(dp(11f), rowH * 0.26f)
            val iconX = r.left + dp(8f) + iconR
            UiKit.iconCircle(c, g, iconX, r.centerY(), iconR, metric.icon, 12f, metric.tint)
            val tx = iconX + iconR + dp(7f)
            val maxW = (r.right - tx - dp(5f)).coerceAtLeast(dp(16f))
            p.color = 0xFF8A7360.toInt()
            p.textSize = textDp(8.5f)
            drawFitted(c, metric.label, tx, r.top + rowH * 0.42f, maxW, p, textDp(7f))
            p.color = 0xFF4A3728.toInt()
            p.textSize = textDp(11f)
            drawFitted(c, metric.value, tx, r.top + rowH * 0.78f, maxW, p, textDp(8f))
        }

        val navY = panelR.bottom - dp(37f)
        prevRect = RectF(panelR.left + dp(14f), navY, panelR.left + dp(62f), navY + dp(24f))
        nextRect = RectF(panelR.right - dp(62f), navY, panelR.right - dp(14f), navY + dp(24f))
        UiKit.cuteButton(c, g, prevRect, "‹ 이전", if (page > 0) UiKit.PASTEL_SAND else 0xFFD8D1C5.toInt(), UiKit.INK, 9.5f)
        UiKit.cuteButton(c, g, nextRect, "다음 ›", if (page < pageCount - 1) UiKit.PASTEL_SAND else 0xFFD8D1C5.toInt(), UiKit.INK, 9.5f)
        p.textSize = textDp(9f)
        p.color = 0xFF8A7360.toInt()
        val pageText = "${page + 1} / $pageCount"
        c.drawText(pageText, panelR.centerX() - p.measureText(pageText) / 2f, navY + dp(16f), p)
    }

    private fun drawFitted(c: Canvas, text: String, x: Float, baseline: Float, maxW: Float, p: Paint, minSize: Float) {
        var label = text
        while (label.length > 1 && p.measureText(label) > maxW) label = label.dropLast(1)
        if (label != text && label.length > 1) label = label.dropLast(1) + "…"
        val old = p.textSize
        while (p.measureText(label) > maxW && p.textSize > minSize) p.textSize -= 0.5f
        c.drawText(label, x, baseline, p)
        p.textSize = old
    }
}

/** [P06] 업적 한 건의 잠금 상태·설명·해금 날짜를 보여주는 팝업. */
class AchievementDetailOverlay(
    scene: Scene,
    private val def: Ach.Def,
    private val onReturn: () -> Unit
) : Overlay(scene) {
    private var closeRect = RectF()
    private var returnRect = RectF()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack || (tap != null && (closeRect.contains(tap.x, tap.y) || returnRect.contains(tap.x, tap.y)))) {
            finished = true
            onReturn()
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val d = g.density
        fun dp(v: Float) = v * d
        fun textDp(v: Float) = TypeScale.px(dp(v))
        val unlocked = Ach.isUnlocked(g.context, def.id)
        val day = Ach.unlockDay(g.context, def.id)
        val title = if (def.hidden && !unlocked) "???" else def.title
        val desc = if (def.hidden && !unlocked) "아직은 조용히 기다리는 순간이에요." else def.desc
        val p = statsText

        UiKit.dim(c, g, 162, scene.overlay?.bornAt ?: 0L)
        val pw = min(w * 0.88f, dp(420f))
        val ph = min(h * 0.72f, dp(300f))
        val panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        UiKit.panel(c, g, panelR)

        val closeCx = panelR.right - dp(23f)
        val closeCy = panelR.top + dp(22f)
        closeRect = RectF(closeCx - dp(20f), closeCy - dp(20f), closeCx + dp(20f), closeCy + dp(20f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(12f), "✕", 11f)

        UiKit.iconCircle(c, g, panelR.centerX(), panelR.top + dp(63f), dp(24f), if (unlocked) def.icon else if (def.hidden) "❔" else "🔒", 23f,
            if (unlocked) 0xFFFFE7C3.toInt() else 0xFFD8D1C5.toInt())
        p.textSize = textDp(15f)
        p.color = 0xFF4A3728.toInt()
        c.drawText(title, panelR.centerX() - p.measureText(title) / 2f, panelR.top + dp(105f), p)
        p.textSize = textDp(10.5f)
        p.color = 0xFF8A7360.toInt()
        val descLines = Type.wrap(desc, p, panelR.width() - dp(36f)).take(3)
        var y = panelR.top + dp(132f)
        for (line in descLines) {
            c.drawText(line, panelR.centerX() - p.measureText(line) / 2f, y, p)
            y += dp(16f)
        }
        p.textSize = textDp(10f)
        p.color = if (unlocked) 0xFF9A6A1F.toInt() else 0xFF9B9182.toInt()
        val status = if (unlocked) "해금 · Day ${day ?: 1}" else "잠겨 있어요"
        c.drawText(status, panelR.centerX() - p.measureText(status) / 2f, panelR.bottom - dp(66f), p)

        returnRect = RectF(panelR.centerX() - dp(72f), panelR.bottom - dp(48f), panelR.centerX() + dp(72f), panelR.bottom - dp(16f))
        UiKit.cuteButton(c, g, returnRect, "업적 목록으로", UiKit.PASTEL_SAND, UiKit.INK, 10f)
    }
}

private val statsText = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true)
