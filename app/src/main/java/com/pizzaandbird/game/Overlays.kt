package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 씬 위에 뜨는 UI 오버레이 (대화상자/메뉴/미니게임 등).
 */
abstract class Overlay(val scene: Scene) {
    var finished = false

    open fun update(dt: Float) {}
    open fun handleInput(input: Input) {}
    open fun draw(c: Canvas) {}
}

// 공통 페인트
private val fillP = Paint()
private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

private fun dp(scene: Scene, v: Float): Float = v * scene.game.density

/** 패널 그리기 */
private fun panel(c: Canvas, r: RectF, scene: Scene) {
    fillP.color = 0xFFF8EFDC.toInt()
    c.drawRoundRect(r, dp(scene, 12f), dp(scene, 12f), fillP)
    strokeP.color = 0xFF6B4F35.toInt()
    strokeP.strokeWidth = dp(scene, 2.5f)
    c.drawRoundRect(r, dp(scene, 12f), dp(scene, 12f), strokeP)
}

private fun dim(c: Canvas, scene: Scene, alpha: Int = 130) {
    fillP.color = Color.argb(alpha, 20, 16, 28)
    c.drawRect(0f, 0f, scene.game.screenW.toFloat(), scene.game.screenH.toFloat(), fillP)
}

// ---------------------------------------------------------------------------
// 대화 상자
// ---------------------------------------------------------------------------

class DialogOverlay(
    scene: Scene,
    private val title: String,
    private val body: String,
    choices: List<Choice> = emptyList(),
    private val disableKeys: Boolean = false
) : Overlay(scene) {

    class Choice(val label: String, val action: (DialogOverlay) -> Unit = { it.finished = true })

    private val choices: List<Choice> =
        if (choices.isEmpty()) listOf(Choice("확인")) else choices

    private var choiceRects: List<RectF> = emptyList()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            for (i in choiceRects.indices) {
                if (choiceRects[i].contains(tap.x, tap.y)) {
                    pick(i)
                    return
                }
            }
        }
        if (disableKeys) return
        // A: 첫 번째 선택지 / B·뒤로가기: 그냥 닫기 (실수 방지)
        if (input.justA) pick(0)
        if (input.justB || input.justBack) finished = true
    }

    private fun pick(i: Int) {
        if (i in choices.indices) choices[i].action(this)
    }

    override fun draw(c: Canvas) {
        dim(c, scene)
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val margin = dp(scene, 18f)

        // 본문 줄 수에 맞춰 패널 높이 결정
        textP.textSize = dp(scene, 13f)
        val maxW = w - margin * 2f - dp(scene, 28f)
        val lines = g.hud.wrapText(body, textP, maxW)
        val panelH = dp(scene, 108f) + dp(scene, 17f) * (lines.size - 3).coerceAtLeast(0)
        val r = RectF(margin, h - margin - panelH, w - margin, h - margin)
        panel(c, r, scene)

        textP.textSize = dp(scene, 11f)
        textP.color = 0xFF6FAE6F.toInt()
        if (title.isNotEmpty()) {
            val tw = textP.measureText(title)
            val chip = RectF(r.left + dp(scene, 12f), r.top - dp(scene, 10f), r.left + dp(scene, 12f) + tw + dp(scene, 14f), r.top + dp(scene, 10f))
            fillP.color = 0xFF6B4F35.toInt()
            c.drawRoundRect(chip, dp(scene, 9f), dp(scene, 9f), fillP)
            textP.color = 0xFFF8EFDC.toInt()
            c.drawText(title, chip.left + dp(scene, 7f), chip.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
        }

        // 본문
        textP.textSize = dp(scene, 13f)
        textP.color = 0xFF4A3728.toInt()
        var ty = r.top + dp(scene, 24f)
        for (ln in lines.take(6)) {
            c.drawText(ln, r.left + dp(scene, 14f), ty, textP)
            ty += dp(scene, 17f)
        }

        // 선택지
        val n = choices.size
        val bw = (r.width() - dp(scene, 12f) * (n + 1)) / n
        val bty = r.bottom - dp(scene, 40f)
        val rects = ArrayList<RectF>()
        for (i in 0 until n) {
            val br = RectF(
                r.left + dp(scene, 12f) + i * (bw + dp(scene, 12f)), bty,
                r.left + dp(scene, 12f) + i * (bw + dp(scene, 12f)) + bw, bty + dp(scene, 30f)
            )
            fillP.color = 0xFFF2E3C2.toInt()
            c.drawRoundRect(br, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFB5651D.toInt()
            strokeP.strokeWidth = dp(scene, 1.8f)
            c.drawRoundRect(br, dp(scene, 9f), dp(scene, 9f), strokeP)
            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF6B4F35.toInt()
            val label = choices[i].label
            c.drawText(label, br.centerX() - textP.measureText(label) / 2, br.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
            rects.add(br)
        }
        choiceRects = rects
    }
}

// ---------------------------------------------------------------------------
// 메뉴 (상태 / 피자 / 도감 / 설정)
// ---------------------------------------------------------------------------

class MenuOverlay(scene: Scene) : Overlay(scene) {

    private enum class Tab(val label: String) {
        STATUS("상태"), PIZZA("피자"), BOOK("도감"), SETTINGS("설정")
    }

    private var tab = Tab.STATUS
    private val tabRects = ArrayList<Pair<RectF, Tab>>()
    private val btnRects = ArrayList<Triple<RectF, String, () -> Unit>>()
    private var closeRect = RectF()
    private var resetArmed = false
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val g = scene.game
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) {
            if (input.justA && tab == Tab.SETTINGS) { /* A는 기본 동작 없음 */ }
            return
        }
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, t) in tabRects) {
            if (r.contains(tap.x, tap.y)) { tab = t; resetArmed = false; return }
        }
        for ((r, _, action) in btnRects) {
            if (r.contains(tap.x, tap.y)) { action(); return }
        }
        if (tab == Tab.BOOK && panelR.contains(tap.x, tap.y)) {
            // 도감 셀 탭 -> 새 정보
            for (def in Birds.ALL) {
                val cell = bookCell(def) ?: continue
                if (cell.contains(tap.x, tap.y)) {
                    showBirdInfo(def)
                    return
                }
            }
        }
    }

    private fun showBirdInfo(def: BirdDef) {
        val s = scene.game.state
        val n = s.birdCounts[def.id] ?: 0
        val body = if (n > 0) {
            "${def.desc}\n촬영한 횟수: ${n}회"
        } else {
            "아직 만나지 못한 새예요.\n${def.tier.label} · ${def.tier.starText()}"
        }
        scene.openOverlay(DialogOverlay(scene, "${def.name} ${def.tier.starText()}", body))
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene)

        val pw = w * 0.88f
        val ph = h * 0.86f
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        // 닫기 X
        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        // 탭
        tabRects.clear()
        val tabW = (panelR.width() - dp(scene, 48f)) / 4f
        for ((i, t) in Tab.values().withIndex()) {
            val r = RectF(
                panelR.left + dp(scene, 12f) + i * (tabW + dp(scene, 8f)), panelR.top + dp(scene, 10f),
                panelR.left + dp(scene, 12f) + i * (tabW + dp(scene, 8f)) + tabW, panelR.top + dp(scene, 38f)
            )
            fillP.color = if (t == tab) 0xFF6B4F35.toInt() else 0xFFF2E3C2.toInt()
            c.drawRoundRect(r, dp(scene, 8f), dp(scene, 8f), fillP)
            textP.textSize = dp(scene, 12.5f)
            textP.color = if (t == tab) 0xFFF8EFDC.toInt() else 0xFF6B4F35.toInt()
            c.drawText(t.label, r.centerX() - textP.measureText(t.label) / 2, r.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
            tabRects.add(r to t)
        }

        btnRects.clear()
        when (tab) {
            Tab.STATUS -> drawStatus(c)
            Tab.PIZZA -> drawPizza(c)
            Tab.BOOK -> drawBook(c)
            Tab.SETTINGS -> drawSettings(c)
        }
    }

    private fun contentTop(): Float = panelR.top + dp(scene, 52f)
    private fun contentBottom(): Float = panelR.bottom - dp(scene, 14f)

    private fun drawStatus(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        val mins = (s.playSeconds / 60f).toInt()
        val timeStr = if (mins >= 60) "${mins / 60}시간 ${mins % 60}분" else "${mins}분"

        val cam = CameraDefs.LEVELS[(s.cameraLevel - 1).coerceIn(0, CameraDefs.LEVELS.size - 1)]
        val nextCam = if (s.cameraLevel < CameraDefs.LEVELS.size) CameraDefs.LEVELS[s.cameraLevel] else null

        val lines = listOf(
            "지갑: ₩ ${fmtMoney(s.money)}",
            "배고픔: ${s.hunger.toInt()}/100   행운: ${s.luck.toInt()}/100",
            "카메라: ${cam.name} (촬영 반경 ${cam.rangeTiles}칸)" +
                    (if (nextCam != null) "\n     다음 업그레이드: ${nextCam.name} ₩${fmtMoney(nextCam.cost)}" else "\n     최고 등급 달성!"),
            "우리 집: ${Regions.byId[s.homeRegion]?.name ?: "?"}",
            "지금: ${Regions.byId[s.region]?.name ?: "?"}   방문 지역: ${s.visited.size}/9",
            "도감: ${s.birdCounts.size}/${Birds.ALL.size}종",
            "박사 의뢰: ${s.questBird?.let { Birds.byId[it]?.name } ?: "없음"}",
            "플레이 시간: $timeStr",
            "들고 다니는 피자: ${s.pizzaCount}개"
        )
        textP.textSize = dp(scene, 12.5f)
        textP.color = 0xFF4A3728.toInt()
        var ty = contentTop() + dp(scene, 14f)
        val x = panelR.left + dp(scene, 18f)
        for (ln in lines) {
            for (sub in ln.split("\n")) {
                c.drawText(sub, x, ty, textP)
                ty += dp(scene, 18f)
            }
        }
    }

    private fun drawPizza(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        var ty = contentTop() + dp(scene, 10f)
        val x = panelR.left + dp(scene, 18f)

        textP.textSize = dp(scene, 12.5f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("피자는 집의 화덕에서 구울 수 있어요. (최대 ${PIZZA_CAP}개)", x, ty, textP)
        ty += dp(scene, 22f)

        for (q in 0 until 3) {
            val def = PizzaQ.of(q)
            val count = s.pizzas[q]
            // 아이콘
            val iconSz = dp(scene, 22f)
            c.drawBitmap(a.pizzaIcon, null, RectF(x, ty, x + iconSz, ty + iconSz), a.sprPaint)
            textP.textSize = dp(scene, 13f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText("${def.label} ×$count", x + iconSz + dp(scene, 10f), ty + dp(scene, 15f), textP)
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText("배고픔 +${def.hunger} · 행운 +${def.luck}", x + iconSz + dp(scene, 10f), ty + dp(scene, 30f), textP)

            // 먹기 버튼
            val br = RectF(panelR.right - dp(scene, 96f), ty + dp(scene, 2f), panelR.right - dp(scene, 18f), ty + dp(scene, 30f))
            val enabled = count > 0 && s.hunger < 100f
            fillP.color = if (enabled) 0xFFF2B63C.toInt() else Color.argb(90, 200, 190, 175)
            c.drawRoundRect(br, dp(scene, 8f), dp(scene, 8f), fillP)
            textP.textSize = dp(scene, 12f)
            textP.color = if (enabled) 0xFF4A3728.toInt() else Color.argb(140, 74, 55, 40)
            val label = "냠냠"
            c.drawText(label, br.centerX() - textP.measureText(label) / 2, br.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
            if (enabled) {
                btnRects.add(Triple(br, def.label) {
                    val eaten = scene.game.state.eat(q)
                    if (eaten != null) {
                        scene.game.toast("냠냠! ${eaten.label} (배고픔 +${eaten.hunger}, 행운 +${eaten.luck})")
                    }
                })
            }
            ty += dp(scene, 42f)
        }
    }

    private var bookRects: Map<String, RectF> = emptyMap()

    private fun bookCell(def: BirdDef): RectF? = bookRects[def.id]

    private fun drawBook(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        val cols = 2
        val areaLeft = panelR.left + dp(scene, 14f)
        val areaTop = contentTop()
        val areaW = panelR.width() - dp(scene, 28f)
        val areaH = contentBottom() - areaTop
        val gap = dp(scene, 8f)
        val cw = (areaW - gap * (cols - 1)) / cols
        val rows = (Birds.ALL.size + cols - 1) / cols
        val chh = (areaH - gap * (rows - 1)) / rows

        textP.textSize = dp(scene, 12f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("도감 ${s.birdCounts.size}/${Birds.ALL.size}종 — 새를 누르면 정보가 나와요",
            areaLeft, areaTop - dp(scene, 6f), textP)

        val rects = LinkedHashMap<String, RectF>()
        for ((i, def) in Birds.ALL.withIndex()) {
            val col = i % cols
            val row = i / cols
            val r = RectF(
                areaLeft + col * (cw + gap), areaTop + row * (chh + gap),
                areaLeft + col * (cw + gap) + cw, areaTop + row * (chh + gap) + chh
            )
            val seen = (s.birdCounts[def.id] ?: 0) > 0
            val tierColor = when (def.tier) {
                Tier.COMMON -> 0xFF9AA3AD.toInt()
                Tier.UNCOMMON -> 0xFF6FAE57.toInt()
                Tier.RARE -> 0xFF3F6FB0.toInt()
                Tier.LEGEND -> 0xFFD9403A.toInt()
            }
            fillP.color = if (seen) Color.argb(230, 250, 243, 226) else Color.argb(180, 226, 218, 204)
            c.drawRoundRect(r, dp(scene, 8f), dp(scene, 8f), fillP)
            strokeP.color = tierColor
            strokeP.strokeWidth = if (seen) dp(scene, 2f) else dp(scene, 1f)
            c.drawRoundRect(r, dp(scene, 8f), dp(scene, 8f), strokeP)

            // 새 스프라이트
            val bmp = a.bird(def.id)
            val k = dp(scene, 1.4f)
            val bx = r.left + dp(scene, 6f)
            val by = r.centerY() - bmp.height * k / 2f
            if (seen) {
                c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)
            } else {
                fillP.color = Color.argb(150, 150, 140, 130)
                c.drawCircle(bx + bmp.width * k / 2f, r.centerY(), dp(scene, 8f), fillP)
                textP.textSize = dp(scene, 10f)
                textP.color = 0xFFF8EFDC.toInt()
                c.drawText("?", bx + bmp.width * k / 2f - textP.measureText("?") / 2f, r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            }

            // 이름/횟수
            val tx = bx + bmp.width * k + dp(scene, 8f)
            textP.textSize = dp(scene, 12f)
            textP.color = if (seen) 0xFF4A3728.toInt() else Color.argb(140, 74, 55, 40)
            c.drawText(if (seen) def.name else "???", tx, r.top + dp(scene, 14f), textP)
            textP.textSize = dp(scene, 10f)
            textP.color = 0xFF8A7360.toInt()
            if (seen) {
                c.drawText("📸 ${s.birdCounts[def.id]}회 · ${def.tier.label}", tx, r.top + dp(scene, 28f), textP)
            } else {
                c.drawText(def.tier.label, tx, r.top + dp(scene, 28f), textP)
            }
            rects[def.id] = r
        }
        bookRects = rects
    }

    private fun drawSettings(c: Canvas) {
        val g = scene.game
        val x = panelR.left + dp(scene, 18f)
        var ty = contentTop() + dp(scene, 16f)

        fun button(label: String, action: () -> Unit) {
            val br = RectF(x, ty, x + dp(scene, 150f), ty + dp(scene, 32f))
            fillP.color = 0xFFF2E3C2.toInt()
            c.drawRoundRect(br, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFB5651D.toInt()
            strokeP.strokeWidth = dp(scene, 1.8f)
            c.drawRoundRect(br, dp(scene, 9f), dp(scene, 9f), strokeP)
            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF6B4F35.toInt()
            c.drawText(label, br.centerX() - textP.measureText(label) / 2, br.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
            btnRects.add(Triple(br, label, action))
            ty += dp(scene, 42f)
        }

        button("저장하기") {
            SaveManager.save(g.context, g.state)
            g.toast("저장 완료!")
        }
        button("타이틀로 가기") {
            SaveManager.save(g.context, g.state)
            finished = true
            g.scene = TitleScene(g)
        }
        button(if (resetArmed) "정말 초기화할까요? (되돌릴 수 없어요)" else "처음부터 다시 시작") {
            if (!resetArmed) {
                resetArmed = true
            } else {
                SaveManager.clear(g.context)
                g.state.reset("seoul")
                g.state.started = false
                finished = true
                g.scene = TitleScene(g)
            }
        }

        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF8A7360.toInt()
        ty += dp(scene, 6f)
        c.drawText("Pizza and Bird v0.1.0-beta01", x, ty, textP)
        ty += dp(scene, 15f)
        c.drawText("완전 오프라인 힐링 게임 · 저장은 자동으로 돼요", x, ty, textP)
        ty += dp(scene, 15f)
        c.drawText("새 종류·피자 종류·집 꾸미기는 업데이트로 계속 추가될 예정!", x, ty, textP)
    }
}

// ---------------------------------------------------------------------------
// 피자 굽기 미니게임 (화덕)
// ---------------------------------------------------------------------------

class BakeOverlay(scene: Scene) : Overlay(scene) {

    private var t = 0.6f
    private var stopped = false
    private var resultQ = -1
    private var lostPizza = false

    private fun cursorPos(): Float = 0.5f + 0.5f * sin(t * 2.6f)

    override fun update(dt: Float) {
        if (!stopped) t += dt
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (!stopped) {
            if (input.justA || tap != null) stopBake()
        } else {
            if (input.justA || input.justB || input.justBack || tap != null) finished = true
        }
    }

    private fun stopBake() {
        stopped = true
        val pos = cursorPos()
        resultQ = when {
            pos < 0.12f || pos > 0.88f -> 0
            pos in 0.38f..0.62f -> 2
            else -> 1
        }
        lostPizza = !scene.game.state.addPizza(resultQ)
        SaveManager.save(scene.game.context, scene.game.state)
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        dim(c, scene)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val cw = minOf(w * 0.76f, dp(scene, 430f))
        val chh = dp(scene, 190f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val a = g.assets
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        var title = "🍕 화덕에 피자 굽기"
        var tw = textP.measureText(title)
        c.drawText(title, r.centerX() - tw / 2, r.top + dp(scene, 26f), textP)

        if (!stopped) {
            // 게이지
            val gx = r.left + dp(scene, 26f)
            val gy = r.centerY() - dp(scene, 10f)
            val gw = r.width() - dp(scene, 52f)
            val gh = dp(scene, 26f)

            // 구역: 탐(양끝) / 맛있음 / 걸작(중앙)
            val zones = listOf(
                Triple(0f, 0.12f, 0xFFE2574C.toInt()),
                Triple(0.88f, 1f, 0xFFE2574C.toInt()),
                Triple(0.12f, 0.38f, 0xFFF2D06B.toInt()),
                Triple(0.62f, 0.88f, 0xFFF2D06B.toInt()),
                Triple(0.38f, 0.62f, 0xFF6FBA6B.toInt())
            )
            for ((z0, z1, col) in zones) {
                fillP.color = Color.argb(190, Color.red(col), Color.green(col), Color.blue(col))
                val zr = RectF(gx + gw * z0, gy, gx + gw * z1, gy + gh)
                c.drawRoundRect(zr, dp(scene, 5f), dp(scene, 5f), fillP)
            }
            strokeP.color = 0xFF6B4F35.toInt()
            strokeP.strokeWidth = dp(scene, 2f)
            c.drawRoundRect(RectF(gx, gy, gx + gw, gy + gh), dp(scene, 5f), dp(scene, 5f), strokeP)

            // 커서
            val pos = cursorPos()
            val cx = gx + gw * pos
            strokeP.color = 0xFF4A3728.toInt()
            strokeP.strokeWidth = dp(scene, 3f)
            c.drawLine(cx, gy - dp(scene, 8f), cx, gy + gh + dp(scene, 8f), strokeP)

            // 라벨
            textP.textSize = dp(scene, 10f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText("살짝 탐", gx, gy + gh + dp(scene, 16f), textP)
            val mid = "걸작!"
            c.drawText(mid, gx + gw / 2f - textP.measureText(mid) / 2f, gy - dp(scene, 14f), textP)
            val right = "살짝 탐"
            c.drawText(right, gx + gw - textP.measureText(right), gy + gh + dp(scene, 16f), textP)

            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF8A7360.toInt()
            val hint = "초록 칸에서 멈춰보세요! (아무 곳이나 탭)"
            c.drawText(hint, r.centerX() - textP.measureText(hint) / 2, r.bottom - dp(scene, 18f), textP)
        } else {
            val q = PizzaQ.of(resultQ)
            title = if (lostPizza) "${q.label}… 그런데 두 손이 가득!" else "${q.label} 완성!"
            tw = textP.measureText(title)
            textP.textSize = dp(scene, 16f)
            textP.color = if (resultQ == 2) 0xFFB5651D.toInt() else 0xFF4A3728.toInt()
            c.drawText(title, r.centerX() - tw / 2, r.centerY() - dp(scene, 6f), textP)

            val k = dp(scene, 3f)
            val bmp = a.pizzaIcon
            val bx = r.centerX() - bmp.width * k / 2f
            val by = r.centerY() - bmp.height * k / 2f + dp(scene, 30f)
            c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)

            textP.textSize = dp(scene, 12f)
            textP.color = 0xFF8A7360.toInt()
            val info = if (lostPizza) "피자 가방이 가득해서 못 챙겼어요…"
            else "배고픔 +${q.hunger} · 행운 +${q.luck}  (메뉴에서 냠냠)"
            c.drawText(info, r.centerX() - textP.measureText(info) / 2, r.bottom - dp(scene, 18f), textP)
        }
    }
}

// ---------------------------------------------------------------------------
// 사진 결과
// ---------------------------------------------------------------------------

class PhotoResultOverlay(
    scene: Scene,
    private val def: BirdDef,
    private val stars: Int,
    private val isNew: Boolean,
    private val count: Int,
    private val questLine: String?
) : Overlay(scene) {

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justA || input.justB || input.justBack || tap != null) finished = true
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        dim(c, scene)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val cw = minOf(w * 0.66f, dp(scene, 360f))
        val chh = dp(scene, 210f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val a = g.assets
        val bmp = a.bird(def.id)
        val k = dp(scene, 4.2f)
        val bx = r.centerX() - bmp.width * k / 2f
        val by = r.top + dp(scene, 46f)
        c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)

        // 프레임 (사진 느낌)
        strokeP.color = 0xFF6B4F35.toInt()
        strokeP.strokeWidth = dp(scene, 2f)
        c.drawRect(RectF(bx - dp(scene, 5f), by - dp(scene, 5f), bx + bmp.width * k + dp(scene, 5f), by + bmp.height * k + dp(scene, 5f)), strokeP)

        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        var s = def.name
        if (isNew) s += "  ✨첫 발견!"
        c.drawText(s, r.centerX() - textP.measureText(s) / 2, r.top + dp(scene, 26f), textP)

        textP.textSize = dp(scene, 14f)
        textP.color = 0xFFB5651D.toInt()
        val starTxt = "★".repeat(stars) + "☆".repeat(3 - stars)
        val y1 = by + bmp.height * k + dp(scene, 22f)
        c.drawText(starTxt, r.centerX() - textP.measureText(starTxt) / 2, y1, textP)

        textP.textSize = dp(scene, 11.5f)
        textP.color = 0xFF8A7360.toInt()
        val cnt = "촬영 횟수: ${count}회 · ${def.tier.label}"
        c.drawText(cnt, r.centerX() - textP.measureText(cnt) / 2, y1 + dp(scene, 20f), textP)

        if (questLine != null) {
            textP.color = 0xFF3F6FB0.toInt()
            c.drawText(questLine, r.centerX() - textP.measureText(questLine) / 2, y1 + dp(scene, 40f), textP)
        }

        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF8A7360.toInt()
        val hint = "탭해서 닫기"
        c.drawText(hint, r.centerX() - textP.measureText(hint) / 2, r.bottom - dp(scene, 12f), textP)
    }
}

// ---------------------------------------------------------------------------
// 큰 지도
// ---------------------------------------------------------------------------

class MapOverlay(scene: Scene) : Overlay(scene) {

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justA || input.justB || input.justBack || tap != null) finished = true
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        dim(c, scene)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val r = minOf(w, h) * 0.36f
        g.hud.drawMinimap(c, w / 2f, h / 2f - dp(scene, 8f), r, true)

        textP.textSize = dp(scene, 13f)
        textP.color = 0xFFF8EFDC.toInt()
        val t1 = "한국 지도 — 자전거로 터널을 지나 이동해요"
        c.drawText(t1, w / 2f - textP.measureText(t1) / 2, h - dp(scene, 40f), textP)
        textP.textSize = dp(scene, 11f)
        val t2 = "🏠 우리 집   🔴 현재 위치   (탭해서 닫기)"
        c.drawText(t2, w / 2f - textP.measureText(t2) / 2, h - dp(scene, 20f), textP)
    }
}
