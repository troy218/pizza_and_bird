package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
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

/** 공통 버튼 그리기/등록 */
private fun drawButton(
    c: Canvas, scene: Scene, r: RectF, label: String,
    fillCol: Int, textCol: Int, textSize: Float
) {
    fillP.color = fillCol
    c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
    strokeP.color = 0xFFB5651D.toInt()
    strokeP.strokeWidth = dp(scene, 1.8f)
    c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)
    textP.textSize = dp(scene, textSize)
    textP.color = textCol
    c.drawText(label, r.centerX() - textP.measureText(label) / 2, r.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
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
        val lines = g.hud.wrapText(body, textP, maxW).take(8)
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
        for (ln in lines) {
            c.drawText(ln, r.left + dp(scene, 14f), ty, textP)
            ty += dp(scene, 17f)
        }

        // 선택지
        val n = choices.size
        val bw = (r.width() - dp(scene, 10f) * (n + 1)) / n
        val bty = r.bottom - dp(scene, 40f)
        val rects = ArrayList<RectF>()
        for (i in 0 until n) {
            val br = RectF(
                r.left + dp(scene, 10f) + i * (bw + dp(scene, 10f)), bty,
                r.left + dp(scene, 10f) + i * (bw + dp(scene, 10f)) + bw, bty + dp(scene, 30f)
            )
            drawButton(c, scene, br, choices[i].label, 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12.5f)
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
        if (tap == null) return
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
        val subs = when {
            def.subspecies.isEmpty() -> "아종: —"
            def.subspecies.size <= 3 -> "아종: ${def.subspecies.joinToString(", ")}"
            else -> "아종: ${def.subspecies.take(3).joinToString(", ")} 외 ${def.subspecies.size - 3}개"
        }
        val seenLine = if (n > 0) {
            "촬영: ${n}회 · 최고 ${"★".repeat(s.bestStars[def.id] ?: 1)}"
        } else {
            "미촬영 · ${def.tier.label} ${def.tier.starText()}"
        }
        val meta = listOfNotNull(
            seenLine,
            if (def.scientificName.isNotBlank()) "학명: ${def.scientificName}" else null,
            if (def.englishName.isNotBlank()) "영명: ${def.englishName}" else null,
            if (def.orderName.isNotBlank() || def.familyName.isNotBlank()) "분류: ${def.orderName} · ${def.familyName}" else null,
            if (def.category.isNotBlank()) "범주: ${def.category}" else null,
            subs,
            "출현: ${def.activeLabel} · 서식: ${def.habitats.joinToString("·") { HabitatLabels[it] ?: it }}"
        ).joinToString("\n")
        val body = if (n > 0) "${def.desc}\n\n$meta" else meta
        scene.openOverlay(DialogOverlay(scene, "${def.name} ${def.tier.starText()}", body))
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene)

        val pw = w * 0.9f
        val ph = h * 0.88f
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
        val mins = (s.playSeconds / 60f).toInt()
        val timeStr = if (mins >= 60) "${mins / 60}시간 ${mins % 60}분" else "${mins}분"

        val cam = CameraDefs.LEVELS[(s.cameraLevel - 1).coerceIn(0, CameraDefs.LEVELS.size - 1)]
        val nextCam = if (s.cameraLevel < CameraDefs.LEVELS.size) CameraDefs.LEVELS[s.cameraLevel] else null
        val decorLuck = s.decorLuck()

        val lines = listOf(
            "지갑: ₩ ${fmtMoney(s.money)}",
            "배고픔: ${s.hunger.toInt()}/100   행운: ${s.luck.toInt()}/100" + (if (decorLuck > 0) " (+${decorLuck} 장식)" else ""),
            "카메라: ${cam.name} (촬영 반경 ${cam.rangeTiles}칸)" +
                    (if (nextCam != null) "\n     다음 업그레이드: ${nextCam.name} ₩${fmtMoney(nextCam.cost)}" else "\n     최고 등급 달성!"),
            "시각: ${s.timeLabel()} ${s.timeEmoji()}   찍은 사진: ${s.photos}장",
            "우리 집: ${Regions.byId[s.homeRegion]?.name ?: "?"}",
            "지금: ${Regions.byId[s.region]?.name ?: "?"}   방문 지역: ${s.visited.size}/${Regions.ALL.size}",
            "도감: ${s.birdCounts.size}/${Birds.ALL.size}종",
            "박사 의뢰: ${s.questBird?.let { Birds.byId[it]?.name } ?: "없음"}",
            "플레이 시간: $timeStr",
            "들고 다니는 피자: ${s.pizzaCount}개",
            "설치한 장식: ${s.decorSlots.count { it >= 0 }}/3 (행운 +$decorLuck)"
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
        var ty = contentTop() + dp(scene, 10f)
        val x = panelR.left + dp(scene, 18f)

        textP.textSize = dp(scene, 12.5f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("화덕에서 토핑을 골라 구울 수 있어요. (최대 ${PIZZA_CAP}개)", x, ty, textP)
        ty += dp(scene, 20f)

        for (t in Toppings.ALL) {
            // 토핑 아이콘(이모지)
            textP.textSize = dp(scene, 20f)
            c.drawText(t.emoji, x, ty + dp(scene, 24f), textP)

            val tx = x + dp(scene, 32f)
            textP.textSize = dp(scene, 13.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText("${t.name} 피자", tx, ty + dp(scene, 13f), textP)
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF8A7360.toInt()
            val bonusTxt = "배고픔 ${if (t.hungerBonus >= 0) "+" else ""}${t.hungerBonus} · 행운 ${if (t.luckBonus >= 0) "+" else ""}${t.luckBonus}" +
                    " · 난이도 " + "●".repeat(((t.cursorSpeed - 0.95f) * 10f).toInt().coerceIn(1, 3))
            c.drawText(bonusTxt, tx, ty + dp(scene, 27f), textP)
            val counts = "걸작×${s.pizzaCountOf(t.id, 2)}  맛있는×${s.pizzaCountOf(t.id, 1)}  탄×${s.pizzaCountOf(t.id, 0)}"
            c.drawText(counts, tx, ty + dp(scene, 41f), textP)

            // 먹기 버튼
            val br = RectF(panelR.right - dp(scene, 100f), ty + dp(scene, 6f), panelR.right - dp(scene, 18f), ty + dp(scene, 34f))
            val enabled = s.pizzaCountOf(t.id) > 0 && s.hunger < 100f
            if (enabled) {
                drawButton(c, scene, br, "냠냠", 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 12f)
                btnRects.add(Triple(br, t.name) {
                    val eaten = scene.game.state.eat(t.id)
                    if (eaten != null) {
                        scene.game.toast("냠냠! ${t.emoji} ${t.name} ${eaten.label} (배고픔 +${eaten.hunger + t.hungerBonus}, 행운 +${eaten.luck + t.luckBonus})")
                    }
                })
            } else {
                drawButton(c, scene, br, "냠냠", Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 12f)
            }
            ty += dp(scene, 52f)
        }
    }

    private var bookRects: Map<String, RectF> = emptyMap()
    private var bookPage = 0

    private fun bookCell(def: BirdDef): RectF? = bookRects[def.id]

    private fun drawBook(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        val cols = 3
        val rowsPerPage = 4
        val pageSize = cols * rowsPerPage
        val totalPages = ((Birds.ALL.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        bookPage = bookPage.coerceIn(0, totalPages - 1)

        val areaLeft = panelR.left + dp(scene, 14f)
        val areaTop = contentTop() + dp(scene, 8f)
        val areaW = panelR.width() - dp(scene, 28f)
        val pagerH = dp(scene, 30f)
        val areaH = contentBottom() - areaTop - pagerH
        val gap = dp(scene, 6f)
        val cw = (areaW - gap * (cols - 1)) / cols
        val chh = (areaH - gap * (rowsPerPage - 1)) / rowsPerPage

        textP.textSize = dp(scene, 11.2f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText(
            "도감 ${s.birdCounts.size}/${Birds.ALL.size}종 — ${OfficialBirdChecklist.SOURCE_TITLE}",
            areaLeft, contentTop() - dp(scene, 2f), textP
        )

        val rects = LinkedHashMap<String, RectF>()
        val startIndex = bookPage * pageSize
        val pageItems = Birds.ALL.drop(startIndex).take(pageSize)
        for ((i, def) in pageItems.withIndex()) {
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
            c.drawRoundRect(r, dp(scene, 7f), dp(scene, 7f), fillP)
            strokeP.color = tierColor
            strokeP.strokeWidth = if (seen) dp(scene, 2f) else dp(scene, 1f)
            c.drawRoundRect(r, dp(scene, 7f), dp(scene, 7f), strokeP)

            val bmp = a.bird(def.id)
            val maxH = chh - dp(scene, 8f)
            val k = (maxH / bmp.height).coerceAtMost(dp(scene, 0.95f))
            val bx = r.left + dp(scene, 4f)
            val by = r.centerY() - bmp.height * k / 2f
            if (seen) {
                c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)
            } else {
                fillP.color = Color.argb(95, 150, 140, 130)
                c.drawCircle(bx + bmp.width * k / 2f, r.centerY(), dp(scene, 7f), fillP)
                textP.textSize = dp(scene, 9.5f)
                textP.color = 0xFFF8EFDC.toInt()
                c.drawText("?", bx + bmp.width * k / 2f - textP.measureText("?") / 2f, r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            }

            val tx = bx + bmp.width * k + dp(scene, 5f)
            textP.textSize = dp(scene, 10.4f)
            textP.color = if (seen) 0xFF4A3728.toInt() else Color.argb(175, 74, 55, 40)
            c.drawText(def.name, tx, r.top + dp(scene, 12f), textP)
            textP.textSize = dp(scene, 8.4f)
            textP.color = 0xFF8A7360.toInt()
            val baseLine2 = if (seen) {
                val best = s.bestStars[def.id] ?: 1
                "📸${s.birdCounts[def.id]} 최고★$best"
            } else {
                "미촬영 · ${def.tier.label}"
            }
            val line2 = baseLine2 + if (def.active == "night") " 🌙" else ""
            c.drawText(line2, tx, r.top + dp(scene, 24f), textP)
            textP.textSize = dp(scene, 7.8f)
            textP.color = Color.argb(170, 94, 76, 58)
            val familyLabel = if (def.familyName.isBlank()) "공식 목록" else def.familyName
            c.drawText(familyLabel, tx, r.top + dp(scene, 35f), textP)
            rects[def.id] = r
        }
        bookRects = rects

        fun pagerButton(rect: RectF, label: String, enabled: Boolean, action: () -> Unit) {
            if (enabled) {
                drawButton(c, scene, rect, label, 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 11.5f)
                btnRects.add(Triple(rect, label, action))
            } else {
                drawButton(c, scene, rect, label, Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 11.5f)
            }
        }

        val py = contentBottom() - dp(scene, 24f)
        val prev = RectF(areaLeft, py, areaLeft + dp(scene, 76f), py + dp(scene, 24f))
        val next = RectF(areaLeft + areaW - dp(scene, 76f), py, areaLeft + areaW, py + dp(scene, 24f))
        pagerButton(prev, "이전", bookPage > 0) { bookPage-- }
        pagerButton(next, "다음", bookPage < totalPages - 1) { bookPage++ }
        val pageText = "${bookPage + 1}/$totalPages"
        textP.textSize = dp(scene, 11.5f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText(pageText, panelR.centerX() - textP.measureText(pageText) / 2f, py + dp(scene, 16f), textP)
    }

    private fun drawSettings(c: Canvas) {
        val g = scene.game
        val x = panelR.left + dp(scene, 18f)
        var ty = contentTop() + dp(scene, 16f)

        fun button(label: String, action: () -> Unit) {
            val br = RectF(x, ty, x + dp(scene, 170f), ty + dp(scene, 32f))
            drawButton(c, scene, br, label, 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12.5f)
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
        c.drawText("Pizza and Bird v0.2.0-beta01", x, ty, textP)
        ty += dp(scene, 15f)
        c.drawText("완전 오프라인 힐링 게임 · 저장은 자동으로 돼요", x, ty, textP)
        ty += dp(scene, 15f)
        c.drawText("낮과 밤이 흐르고, 밤에는 올빼미가 나와요!", x, ty, textP)
    }
}

// ---------------------------------------------------------------------------
// 장식 상점 (사진용품점 장식 코너)
// ---------------------------------------------------------------------------

class DecorShopOverlay(scene: Scene) : Overlay(scene) {

    private var buyRects = ArrayList<Pair<RectF, Int>>()
    private var closeRect = RectF()
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, id) in buyRects) {
            if (r.contains(tap.x, tap.y)) {
                buy(id)
                return
            }
        }
    }

    private fun buy(id: Int) {
        val g = scene.game
        val s = g.state
        val d = Decors.of(id) ?: return
        if (id in s.decorOwned) {
            g.toast("이미 갖고 있는 장식이에요!")
            return
        }
        if (s.money < d.cost) {
            g.toast("돈이 부족해요… (₩${fmtMoney(d.cost)})")
            return
        }
        s.money -= d.cost
        s.decorOwned.add(id)
        SaveManager.save(g.context, s)
        g.toast("${d.emoji} ${d.name} 구매! 집의 장식 칸에 놓아보세요")
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val pw = minOf(w * 0.86f, dp(scene, 420f))
        val ph = minOf(h * 0.86f, dp(scene, 420f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = dp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🧺 장식 코너", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 30f), textP)
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("집에 놓으면 행운이 오르는 소책들이에요 · 보유 ₩${fmtMoney(s.money)}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 46f), textP)

        buyRects = ArrayList()
        var ty = panelR.top + dp(scene, 58f)
        for (d in Decors.ALL) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + dp(scene, 54f))
            fillP.color = 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)

            textP.textSize = dp(scene, 20f)
            c.drawText(d.emoji, r.left + dp(scene, 10f), r.centerY() + dp(scene, 7f), textP)
            textP.textSize = dp(scene, 13f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(d.name, r.left + dp(scene, 44f), r.top + dp(scene, 18f), textP)
            textP.textSize = dp(scene, 10f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(d.desc, r.left + dp(scene, 44f), r.top + dp(scene, 33f), textP)
            c.drawText("행운 +${d.luck}", r.left + dp(scene, 44f), r.top + dp(scene, 46f), textP)

            val owned = d.id in s.decorOwned
            val br = RectF(r.right - dp(scene, 88f), r.centerY() - dp(scene, 15f), r.right - dp(scene, 10f), r.centerY() + dp(scene, 15f))
            if (owned) {
                drawButton(c, scene, br, "보유중", Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 11.5f)
            } else {
                drawButton(c, scene, br, "₩${fmtMoney(d.cost)}", 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 11.5f)
                buyRects.add(br to d.id)
            }
            ty += dp(scene, 60f)
        }
    }
}

// ---------------------------------------------------------------------------
// 장식 배치 (집 장식 칸)
// ---------------------------------------------------------------------------

class DecorPickOverlay(
    scene: Scene,
    private val slot: Int,
    private val onPick: (Int) -> Unit
) : Overlay(scene) {

    private var pickRects = ArrayList<Pair<RectF, Int>>()
    private var closeRect = RectF()
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, id) in pickRects) {
            if (r.contains(tap.x, tap.y)) {
                finished = true
                onPick(id)
                return
            }
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val pw = minOf(w * 0.8f, dp(scene, 360f))
        val ph = dp(scene, 90f) + dp(scene, 58f) * (s.decorOwned.size + 1)
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = dp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("장식 칸 ${slot + 1}에 뭘 놓을까요?", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 30f), textP)

        pickRects = ArrayList()
        var ty = panelR.top + dp(scene, 46f)

        fun row(id: Int, emoji: String, name: String, sub: String) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + dp(scene, 50f))
            fillP.color = 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)
            textP.textSize = dp(scene, 20f)
            c.drawText(emoji, r.left + dp(scene, 10f), r.centerY() + dp(scene, 7f), textP)
            textP.textSize = dp(scene, 13f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(name, r.left + dp(scene, 44f), r.top + dp(scene, 19f), textP)
            textP.textSize = dp(scene, 10f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, r.left + dp(scene, 44f), r.top + dp(scene, 35f), textP)
            pickRects.add(r to id)
            ty += dp(scene, 58f)
        }

        for (did in s.decorOwned) {
            val d = Decors.of(did) ?: continue
            val placed = s.decorSlots.contains(did)
            row(did, d.emoji, d.name, "행운 +${d.luck}" + (if (placed) " · 이미 다른 칸에" else ""))
        }
        row(-1, "🫙", "빈 칸으로 두기", "장식을 치웁니다")
    }
}

// ---------------------------------------------------------------------------
// 피자 굽기 미니게임 (화덕) — 토핑 선택 + 타이밍
// ---------------------------------------------------------------------------

class BakeOverlay(scene: Scene) : Overlay(scene) {

    private var step = 0                 // 0 토핑 선택, 1 타이밍, 2 결과
    private var topping = 0
    private var t = 0.6f
    private var stopped = false
    private var resultQ = -1
    private var lostPizza = false
    private val toppingRects = ArrayList<Pair<RectF, Int>>()
    private var cancelRect = RectF()

    private fun cursorPos(): Float = 0.5f + 0.5f * sin(t * 2.6f)

    override fun update(dt: Float) {
        if (step == 1 && !stopped) t += dt * Toppings.of(topping).cursorSpeed
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        when (step) {
            0 -> {
                if (input.justB || input.justBack) { finished = true; return }
                if (tap == null) return
                for ((r, id) in toppingRects) {
                    if (r.contains(tap.x, tap.y)) {
                        topping = id
                        step = 1
                        t = 0.6f
                        return
                    }
                }
                if (cancelRect.contains(tap.x, tap.y)) finished = true
            }
            1 -> {
                if (input.justA || tap != null) stopBake()
            }
            2 -> {
                if (input.justA || input.justB || input.justBack || tap != null) finished = true
            }
        }
    }

    private fun stopBake() {
        stopped = true
        step = 2
        val pos = cursorPos()
        val w = Toppings.of(topping).perfectW
        val half = w / 2f
        resultQ = when {
            pos < 0.5f - half - 0.26f || pos > 0.5f + half + 0.26f -> 0
            abs(pos - 0.5f) <= half -> 2
            else -> 1
        }
        lostPizza = !scene.game.state.addPizza(topping, resultQ)
        SaveManager.save(scene.game.context, scene.game.state)
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        dim(c, scene)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val cw = minOf(w * 0.8f, dp(scene, 460f))
        val chh = dp(scene, 240f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val a = g.assets

        when (step) {
            0 -> {
                textP.textSize = dp(scene, 16f)
                textP.color = 0xFF4A3728.toInt()
                val title = "🍕 토핑을 골라요"
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.top + dp(scene, 26f), textP)

                toppingRects.clear()
                val cardW = (r.width() - dp(scene, 16f) * 4) / 3f
                for ((i, tp) in Toppings.ALL.withIndex()) {
                    val cr = RectF(
                        r.left + dp(scene, 16f) + i * (cardW + dp(scene, 16f)), r.top + dp(scene, 44f),
                        r.left + dp(scene, 16f) + i * (cardW + dp(scene, 16f)) + cardW, r.top + dp(scene, 150f)
                    )
                    fillP.color = 0xFFFDF6E8.toInt()
                    c.drawRoundRect(cr, dp(scene, 10f), dp(scene, 10f), fillP)
                    strokeP.color = 0xFFB5651D.toInt()
                    strokeP.strokeWidth = dp(scene, 2f)
                    c.drawRoundRect(cr, dp(scene, 10f), dp(scene, 10f), strokeP)

                    textP.textSize = dp(scene, 26f)
                    c.drawText(tp.emoji, cr.centerX() - textP.measureText(tp.emoji) / 2, cr.top + dp(scene, 34f), textP)
                    textP.textSize = dp(scene, 13f)
                    textP.color = 0xFF4A3728.toInt()
                    c.drawText(tp.name, cr.centerX() - textP.measureText(tp.name) / 2, cr.top + dp(scene, 56f), textP)
                    textP.textSize = dp(scene, 10f)
                    textP.color = 0xFF8A7360.toInt()
                    val b1 = "배고픔 ${if (tp.hungerBonus >= 0) "+" else ""}${tp.hungerBonus} · 행운 ${if (tp.luckBonus >= 0) "+" else ""}${tp.luckBonus}"
                    c.drawText(b1, cr.centerX() - textP.measureText(b1) / 2, cr.top + dp(scene, 74f), textP)
                    val diff = ((tp.cursorSpeed - 0.95f) * 10f).toInt().coerceIn(1, 3)
                    val b2 = "구우기 난이도 " + "●".repeat(diff) + "○".repeat(3 - diff)
                    c.drawText(b2, cr.centerX() - textP.measureText(b2) / 2, cr.top + dp(scene, 90f), textP)
                    val pz = a.pizzaIcon
                    val psz = dp(scene, 26f)
                    c.drawBitmap(pz, null, RectF(cr.centerX() - psz / 2, cr.top + dp(scene, 100f), cr.centerX() + psz / 2, cr.top + dp(scene, 100f) + psz), a.sprPaint)
                    toppingRects.add(cr to tp.id)
                }

                cancelRect = RectF(r.centerX() - dp(scene, 60f), r.bottom - dp(scene, 40f), r.centerX() + dp(scene, 60f), r.bottom - dp(scene, 12f))
                drawButton(c, scene, cancelRect, "그만두기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12f)
            }
            1 -> {
                val tp = Toppings.of(topping)
                textP.textSize = dp(scene, 16f)
                textP.color = 0xFF4A3728.toInt()
                val title = "${tp.emoji} ${tp.name} 피자 굽기"
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.top + dp(scene, 26f), textP)

                // 게이지
                val gx = r.left + dp(scene, 26f)
                val gy = r.centerY() - dp(scene, 6f)
                val gw = r.width() - dp(scene, 52f)
                val gh = dp(scene, 28f)
                val half = tp.perfectW / 2f
                val zones = listOf(
                    Triple(0f, 0.5f - half - 0.26f, 0xFFE2574C.toInt()),
                    Triple(0.5f - half - 0.26f, 0.5f - half, 0xFFF2D06B.toInt()),
                    Triple(0.5f - half, 0.5f + half, 0xFF6FBA6B.toInt()),
                    Triple(0.5f + half, 0.5f + half + 0.26f, 0xFFF2D06B.toInt()),
                    Triple(0.5f + half + 0.26f, 1f, 0xFFE2574C.toInt())
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
                strokeP.strokeWidth = dp(scene, 3.5f)
                c.drawLine(cx, gy - dp(scene, 9f), cx, gy + gh + dp(scene, 9f), strokeP)

                // 라벨
                textP.textSize = dp(scene, 10.5f)
                textP.color = 0xFF4A3728.toInt()
                c.drawText("살짝 탐", gx, gy + gh + dp(scene, 18f), textP)
                val mid = "걸작!"
                c.drawText(mid, gx + gw / 2f - textP.measureText(mid) / 2f, gy - dp(scene, 14f), textP)
                val right = "살짝 탐"
                c.drawText(right, gx + gw - textP.measureText(right), gy + gh + dp(scene, 18f), textP)

                textP.textSize = dp(scene, 12.5f)
                textP.color = 0xFF8A7360.toInt()
                val hint = "초록 칸에서 멈춰보세요! (아무 곳이나 탭)"
                c.drawText(hint, r.centerX() - textP.measureText(hint) / 2, r.bottom - dp(scene, 18f), textP)
            }
            else -> {
                val q = PizzaQ.of(resultQ)
                val tp = Toppings.of(topping)
                val title = if (lostPizza) "${tp.emoji} ${q.label}… 그런데 두 손이 가득!" else "${tp.emoji} ${q.label} 완성!"
                textP.textSize = dp(scene, 16f)
                textP.color = if (resultQ == 2) 0xFFB5651D.toInt() else 0xFF4A3728.toInt()
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.centerY() - dp(scene, 30f), textP)

                val k = dp(scene, 3.4f)
                val bmp = a.pizzaIcon
                val bx = r.centerX() - bmp.width * k / 2f
                val by = r.centerY() - bmp.height * k / 2f + dp(scene, 24f)
                c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)

                textP.textSize = dp(scene, 12.5f)
                textP.color = 0xFF8A7360.toInt()
                val info = if (lostPizza) "피자 가방이 가득해서 못 챙겼어요…"
                else "먹으면 배고픔 +${q.hunger + tp.hungerBonus} · 행운 +${q.luck + tp.luckBonus}"
                c.drawText(info, r.centerX() - textP.measureText(info) / 2, r.bottom - dp(scene, 18f), textP)
                textP.textSize = dp(scene, 10.5f)
                val hint2 = "탭해서 닫기"
                c.drawText(hint2, r.centerX() - textP.measureText(hint2) / 2, r.bottom - dp(scene, 4f), textP)
            }
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

    private var t = 0f

    override fun update(dt: Float) {
        t += dt
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justA || input.justB || input.justBack || tap != null) finished = true
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()

        // 셔터 플래시
        if (t < 0.22f) {
            fillP.color = Color.argb((200 * (1f - t / 0.22f)).toInt(), 255, 255, 255)
            c.drawRect(0f, 0f, w, h, fillP)
        }

        dim(c, scene)
        val cw = minOf(w * 0.66f, dp(scene, 380f))
        val chh = dp(scene, 230f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val a = g.assets
        val bmp = a.bird(def.id)
        val k = dp(scene, 4.2f)
        val bx = r.centerX() - bmp.width * k / 2f
        val by = r.top + dp(scene, 50f)
        c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)

        // 프레임 (사진 느낌)
        strokeP.color = 0xFF6B4F35.toInt()
        strokeP.strokeWidth = dp(scene, 2f)
        c.drawRect(RectF(bx - dp(scene, 5f), by - dp(scene, 5f), bx + bmp.width * k + dp(scene, 5f), by + bmp.height * k + dp(scene, 5f)), strokeP)

        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        var s = def.name
        if (isNew) s += "  ✨첫 발견!"
        c.drawText(s, r.centerX() - textP.measureText(s) / 2, r.top + dp(scene, 28f), textP)

        textP.textSize = dp(scene, 14f)
        textP.color = 0xFFB5651D.toInt()
        val starTxt = "★".repeat(stars) + "☆".repeat(3 - stars)
        val y1 = by + bmp.height * k + dp(scene, 24f)
        c.drawText(starTxt, r.centerX() - textP.measureText(starTxt) / 2, y1, textP)

        textP.textSize = dp(scene, 11.5f)
        textP.color = 0xFF8A7360.toInt()
        val best = g.state.bestStars[def.id] ?: stars
        val cnt = "촬영 횟수: ${count}회 · ${def.tier.label} · 최고 ★$best"
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
        val t2 = "🏠 우리 집   🔴 현재 위치   방문 ${g.state.visited.size}/${Regions.ALL.size}   (탭해서 닫기)"
        c.drawText(t2, w / 2f - textP.measureText(t2) / 2, h - dp(scene, 20f), textP)
    }
}
