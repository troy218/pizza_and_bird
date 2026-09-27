package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 지역 카드 공용 UI (새 게임 정착 화면 + 이사 오버레이)
 */
object RegionCards {

    fun cardRects(area: RectF, count: Int, gap: Float): List<RectF> {
        val cols = 3
        val rows = (count + cols - 1) / cols
        val cw = (area.width() - gap * (cols - 1)) / cols
        val chh = (area.height() - gap * (rows - 1)) / rows
        val rects = ArrayList<RectF>()
        for (i in 0 until count) {
            val col = i % cols
            val row = i / cols
            rects.add(
                RectF(
                    area.left + col * (cw + gap), area.top + row * (chh + gap),
                    area.left + col * (cw + gap) + cw, area.top + row * (chh + gap) + chh
                )
            )
        }
        return rects
    }

    fun draw(c: Canvas, game: Game, rects: List<RectF>, regions: List<RegionDef>, selectedId: String?) {
        val dp = game.density
        val fill = Paint()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

        for ((i, reg) in regions.withIndex()) {
            if (i >= rects.size) break
            val r = rects[i]
            val sel = reg.id == selectedId
            fill.color = if (sel) 0xFFFDF3D8.toInt() else 0xFFF8EFDC.toInt()
            c.drawRoundRect(r, dp * 10, dp * 10, fill)
            stroke.color = if (sel) 0xFFE2574C.toInt() else 0xFF6B4F35.toInt()
            stroke.strokeWidth = dp * (if (sel) 3f else 2f)
            c.drawRoundRect(r, dp * 10, dp * 10, stroke)

            val x = r.left + dp * 10
            var y = r.top + dp * 16
            tp.textSize = dp * 14f
            tp.color = 0xFF4A3728.toInt()
            c.drawText(reg.name, x, y, tp)
            val nameW = tp.measureText(reg.name)
            tp.textSize = dp * 8.5f
            tp.color = 0xFF8A7360.toInt()
            c.drawText(reg.english, x + nameW + dp * 6f, y, tp)

            y += dp * 13f
            tp.textSize = dp * 9.5f
            tp.color = 0xFF6FAE6F.toInt()
            c.drawText(reg.habitatLabels, x, y, tp)

            y += dp * 12f
            tp.textSize = dp * 9.5f
            tp.color = 0xFF6B4F35.toInt()
            val sig = "대표 새: " + Regions.signatureBirds(reg).joinToString(", ") { it.name }
            c.drawText(sig, x, y, tp)

            y += dp * 12f
            tp.textSize = dp * 9.5f
            tp.color = 0xFF8A7360.toInt()
            val descLines = game.hud.wrapText(reg.desc, tp, r.width() - dp * 20f).take(2)
            for (ln in descLines) {
                c.drawText(ln, x, y, tp)
                y += dp * 11f
            }
        }
    }

    fun hit(rects: List<RectF>, regions: List<RegionDef>, x: Float, y: Float): RegionDef? {
        for ((i, r) in rects.withIndex()) {
            if (i < regions.size && r.contains(x, y)) return regions[i]
        }
        return null
    }
}

/**
 * 새 게임: 정착할 지역 선택
 */
class RegionSelectScene(game: Game) : Scene(game) {

    private var selected: RegionDef? = null
    private var cardRects: List<RectF> = emptyList()
    private var area = RectF()
    private var confirmRect = RectF()
    private var cancelRect = RectF()
    private val regions = Regions.ALL

    init {
        game.hud.showControls = false
        game.hud.showStats = false
        game.hud.showMinimap = false
        game.hud.questLabel = null
        game.hud.photoModeHint = false
    }

    override fun drawWorld(c: Canvas) {
        val p = Paint()
        p.color = 0xFFBCE0DC.toInt()
        c.drawRect(0f, 0f, 480f, 270f, p)
        p.color = 0xFFD8ECDC.toInt()
        c.drawCircle(90f, 290f, 120f, p)
        c.drawCircle(390f, 300f, 140f, p)
        val t = game.time
        val a = game.assets
        val birds = listOf("sparrow", "gull", "greattit")
        for (i in 0 until 3) {
            val bx = (t * (14f + i * 5f) + i * 160f) % 560f - 40f
            val by = 30f + i * 26f + sin(t * 1.8f + i * 2f) * 8f
            val frame = ((t * 6f + i).toInt() % 2)
            c.drawBitmap(a.bird(birds[i], frame), bx, by, a.sprPaint)
        }
        val pz = a.pizzaIcon
        c.drawBitmap(pz, 20f, 240f + sin(t * 2f) * 2f, a.sprPaint)
        c.drawBitmap(a.bird("magpie"), 450f, 236f + sin(t * 2.4f) * 2f, a.sprPaint)
    }

    override fun drawHud(c: Canvas) {
        val dp = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        tp.textSize = dp * 20f
        tp.color = 0xFF4A3728.toInt()
        val t1 = "어디에 살아볼까요?"
        c.drawText(t1, w / 2f - tp.measureText(t1) / 2, dp * 30f, tp)
        tp.textSize = dp * 11.5f
        tp.color = 0xFF5A6B5A.toInt()
        val t2 = "집은 나중에 이사할 수 있어요. 모든 지역에 화덕이 있는 집을 지어드려요!"
        c.drawText(t2, w / 2f - tp.measureText(t2) / 2, dp * 48f, tp)

        area = RectF(
            dp * 16f, dp * 60f, w - dp * 16f, h - dp * 74f
        )
        cardRects = RegionCards.cardRects(area, regions.size, dp * 8f)
        RegionCards.draw(c, game, cardRects, regions, selected?.id)

        // 하단 확인 바
        val barH = dp * 56f
        val bar = RectF(dp * 16f, h - barH - dp * 12f, w - dp * 16f, h - dp * 12f)
        val fill = Paint()
        fill.color = 0xFFF8EFDC.toInt()
        c.drawRoundRect(bar, dp * 12f, dp * 12f, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp * 2.5f
            color = 0xFF6B4F35.toInt()
        }
        c.drawRoundRect(bar, dp * 12f, dp * 12f, stroke)

        tp.textSize = dp * 13f
        tp.color = 0xFF4A3728.toInt()
        val info = if (selected != null) "${selected!!.name}에 집을 짓고 여행을 시작할게요"
        else "마음에 드는 지역을 선택해 주세요"
        c.drawText(info, bar.left + dp * 16f, bar.centerY() - (tp.descent() + tp.ascent()) / 2, tp)

        val bw = dp * 130f
        confirmRect = RectF(bar.right - bw - dp * 12f, bar.top + dp * 10f, bar.right - dp * 12f, bar.bottom - dp * 10f)
        cancelRect = if (selected != null)
            RectF(confirmRect.left - bw - dp * 10f, bar.top + dp * 10f, confirmRect.left - dp * 10f, bar.bottom - dp * 10f)
        else RectF()
        val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp * 2f
            color = 0xFFB5651D.toInt()
        }
        if (selected != null) {
            fill.color = 0xFFF2B63C.toInt()
            c.drawRoundRect(confirmRect, dp * 10f, dp * 10f, fill)
            c.drawRoundRect(confirmRect, dp * 10f, dp * 10f, btnStroke)
            tp.textSize = dp * 14f
            tp.color = 0xFF4A3728.toInt()
            val label = "정착하기!"
            c.drawText(label, confirmRect.centerX() - tp.measureText(label) / 2, confirmRect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)

            fill.color = 0xFFF2E3C2.toInt()
            c.drawRoundRect(cancelRect, dp * 10f, dp * 10f, fill)
            c.drawRoundRect(cancelRect, dp * 10f, dp * 10f, btnStroke)
            tp.textSize = dp * 13f
            val label2 = "다시 고르기"
            c.drawText(label2, cancelRect.centerX() - tp.measureText(label2) / 2, cancelRect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
        }
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen() ?: run {
            if (input.justBack) game.scene = TitleScene(game)
            return
        }
        val hit = RegionCards.hit(cardRects, regions, tap.x, tap.y)
        if (hit != null) {
            selected = hit
            return
        }
        if (selected != null && cancelRect.contains(tap.x, tap.y)) {
            selected = null
            return
        }
        if (selected != null && confirmRect.contains(tap.x, tap.y)) {
            startGame(selected!!)
        }
        if (input.justBack) game.scene = TitleScene(game)
    }

    private fun startGame(r: RegionDef) {
        game.state.reset(r.id)
        SaveManager.save(game.context, game.state)
        game.fadeTo {
            game.scene = WorldScene(game, r.id, SpawnKind.HOME)
        }
    }
}

/**
 * 이사 오버레이 (집 안의 이사 박스)
 */
class RegionSelectOverlay(
    scene: Scene,
    private val onPick: (RegionDef) -> Unit
) : Overlay(scene) {

    private var selected: RegionDef? = null
    private var cardRects: List<RectF> = emptyList()
    private var area = RectF()
    private var confirmRect = RectF()
    private var cancelRect = RectF()
    private var panelR = RectF()
    private val regions: List<RegionDef>
        get() = Regions.ALL.filter { it.id in scene.game.state.visited }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap == null) {
            if (input.justB || input.justBack) finished = true
            return
        }
        if (selected == null) {
            val hit = RegionCards.hit(cardRects, regions, tap.x, tap.y)
            if (hit != null) selected = hit
            if (input.justB || input.justBack) finished = true
            return
        }
        if (cancelRect.contains(tap.x, tap.y)) {
            selected = null
            return
        }
        if (confirmRect.contains(tap.x, tap.y)) {
            val r = selected
            selected = null
            if (r != null) {
                finished = true
                onPick(r)
            }
        }
    }

    override fun draw(c: Canvas) {
        val game = scene.game
        val dp = game.density
        val w = game.screenW.toFloat()
        val h = game.screenH.toFloat()

        fillDim.color = Color.argb(150, 20, 16, 28)
        c.drawRect(0f, 0f, w, h, fillDim)

        panelR = RectF(dp * 14f, dp * 14f, w - dp * 14f, h - dp * 14f)
        panelFill.color = 0xFFF8EFDC.toInt()
        c.drawRoundRect(panelR, dp * 12f, dp * 12f, panelFill)
        panelStroke.color = 0xFF6B4F35.toInt()
        panelStroke.strokeWidth = dp * 2.5f
        c.drawRoundRect(panelR, dp * 12f, dp * 12f, panelStroke)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        tp.textSize = dp * 16f
        tp.color = 0xFF4A3728.toInt()
        val t1 = "📦 이사갈 곳을 골라요"
        c.drawText(t1, panelR.left + dp * 16f, panelR.top + dp * 26f, tp)
        tp.textSize = dp * 11f
        tp.color = 0xFF8A7360.toInt()
        val t2 = "이사 비용 ₩${fmtMoney(MOVE_COST)} · 방문한 적 있는 지역으로만 이사할 수 있어요"
        c.drawText(t2, panelR.left + dp * 16f, panelR.top + dp * 44f, tp)

        area = RectF(
            panelR.left + dp * 12f, panelR.top + dp * 56f,
            panelR.right - dp * 12f, panelR.bottom - dp * 66f
        )
        cardRects = RegionCards.cardRects(area, regions.size, dp * 8f)
        RegionCards.draw(c, game, cardRects, regions, selected?.id)

        if (selected == null) return

        // 확인 바
        val bar = RectF(panelR.left + dp * 12f, panelR.bottom - dp * 58f, panelR.right - dp * 12f, panelR.bottom - dp * 10f)
        tp.textSize = dp * 12.5f
        tp.color = 0xFF4A3728.toInt()
        val cur = scene.game.state
        val info = if (selected!!.id == cur.homeRegion) "지금 사는 곳이에요!"
        else "${selected!!.name}(으)로 이사… 이사 비용 ₩${fmtMoney(MOVE_COST)} (보유 ₩${fmtMoney(cur.money)})"
        c.drawText(info, bar.left + dp * 8f, bar.centerY() - (tp.descent() + tp.ascent()) / 2, tp)

        val bw = dp * 110f
        confirmRect = RectF(bar.right - bw - dp * 10f, bar.top + dp * 8f, bar.right - dp * 10f, bar.bottom - dp * 8f)
        cancelRect = RectF(confirmRect.left - bw - dp * 8f, bar.top + dp * 8f, confirmRect.left - dp * 8f, bar.bottom - dp * 8f)

        panelFill.color = 0xFFF2B63C.toInt()
        c.drawRoundRect(confirmRect, dp * 10f, dp * 10f, panelFill)
        c.drawRoundRect(confirmRect, dp * 10f, dp * 10f, panelStroke)
        tp.textSize = dp * 13f
        tp.color = 0xFF4A3728.toInt()
        var label = "이사!"
        c.drawText(label, confirmRect.centerX() - tp.measureText(label) / 2, confirmRect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)

        panelFill.color = 0xFFF2E3C2.toInt()
        c.drawRoundRect(cancelRect, dp * 10f, dp * 10f, panelFill)
        c.drawRoundRect(cancelRect, dp * 10f, dp * 10f, panelStroke)
        label = "취소"
        c.drawText(label, cancelRect.centerX() - tp.measureText(label) / 2, cancelRect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
    }

    companion object {
        private val fillDim = Paint()
        private val panelFill = Paint()
        private val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }
}
