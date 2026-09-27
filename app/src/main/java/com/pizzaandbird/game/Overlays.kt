package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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

    /** 선택지. action을 실행한 뒤 대화상자는 자동으로 닫힌다 (닫힘 처리를 따로 안 해도 됨). */
    class Choice(val label: String, val action: (DialogOverlay) -> Unit = {})

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
        if (i in choices.indices) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            choices[i].action(this)
            // 액션이 오버레이를 교체하지 않았다면 자동으로 닫기 (예: 피자 굽기로 교체되는 경우 유지)
            if (scene.overlay === this) finished = true
        }
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
        STATUS("상태"), GROW("성장"), PIZZA("피자"), BOOK("도감"), SETTINGS("설정")
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
        val nTabs = Tab.values().size
        val tabGap = dp(scene, 6f)
        val tabW = (panelR.width() - dp(scene, 24f) - tabGap * (nTabs - 1)) / nTabs
        for ((i, t) in Tab.values().withIndex()) {
            val r = RectF(
                panelR.left + dp(scene, 12f) + i * (tabW + tabGap), panelR.top + dp(scene, 10f),
                panelR.left + dp(scene, 12f) + i * (tabW + tabGap) + tabW, panelR.top + dp(scene, 38f)
            )
            fillP.color = if (t == tab) 0xFF6B4F35.toInt() else 0xFFF2E3C2.toInt()
            c.drawRoundRect(r, dp(scene, 8f), dp(scene, 8f), fillP)
            textP.textSize = dp(scene, 12f)
            textP.color = if (t == tab) 0xFFF8EFDC.toInt() else 0xFF6B4F35.toInt()
            c.drawText(t.label, r.centerX() - textP.measureText(t.label) / 2, r.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
            tabRects.add(r to t)
        }

        btnRects.clear()
        when (tab) {
            Tab.STATUS -> drawStatus(c)
            Tab.GROW -> drawGrow(c)
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

        val lvLine = if (s.level >= Progression.MAX_LEVEL)
            "탐조가 Lv.${s.level} 「${s.title()}」 (최고 레벨!)"
        else
            "탐조가 Lv.${s.level} 「${s.title()}」  경험치 ${s.exp}/${s.expToNext()}"
        val lines = listOf(
            lvLine + (if (s.skillPoints > 0) "  · 숙련포인트 ${s.skillPoints}" else ""),
            "지갑: ${won(s.money)}",
            "배고픔: ${s.hunger.toInt()}/100   행운: ${s.luck.toInt()}/100" + (if (decorLuck > 0) " (+${decorLuck} 장식)" else ""),
            "카메라: ${cam.name} (촬영 반경 ${cam.rangeTiles}칸)" +
                    (if (nextCam != null) "\n     다음 업그레이드: ${nextCam.name} ${won(nextCam.cost)}" else "\n     최고 등급 달성!"),
            "시각: ${s.timeLabel()} ${s.timeEmoji()}   찍은 사진: ${s.photos}장",
            "우리 집: ${Regions.byId[s.homeRegion]?.name ?: "?"} · ${s.houseStyle().name}",
            "지금: ${Regions.byId[s.region]?.name ?: "?"}   방문 지역: ${s.visited.size}/${Regions.ALL.size}",
            "보유 주택: ${s.ownedHomes.size}채 · 인테리어: ${s.ownedHouseStyles.size}/${HouseStyles.ALL.size}",
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

    private fun drawGrow(c: Canvas) {
        val g = scene.game
        val s = g.state
        val left = panelR.left + dp(scene, 16f)
        val right = panelR.right - dp(scene, 16f)
        var ty = contentTop() + dp(scene, 6f)

        // 레벨 / 칭호
        val a = g.assets
        val ps = a.playerSet(s.gender, s.gearTier())
        val avatar = ps.down[0]
        val ak = dp(scene, 2.2f)
        c.drawBitmap(avatar, null, RectF(left, ty, left + avatar.width * ak, ty + avatar.height * ak), a.sprPaint)

        val tx = left + avatar.width * ak + dp(scene, 12f)
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("Lv.${s.level}  ${s.title()}", tx, ty + dp(scene, 16f), textP)

        // 경험치 바
        val bx = tx
        val bw = right - tx
        val byBar = ty + dp(scene, 24f)
        val bh = dp(scene, 10f)
        fillP.color = 0xFFD6C5A4.toInt()
        c.drawRoundRect(RectF(bx, byBar, bx + bw, byBar + bh), bh / 2, bh / 2, fillP)
        if (s.level < Progression.MAX_LEVEL) {
            val prog = s.expProgress()
            if (prog > 0.01f) {
                fillP.color = 0xFF6FBA6B.toInt()
                c.drawRoundRect(RectF(bx, byBar, bx + bw * prog, byBar + bh), bh / 2, bh / 2, fillP)
            }
        } else {
            fillP.color = 0xFFF2D06B.toInt()
            c.drawRoundRect(RectF(bx, byBar, bx + bw, byBar + bh), bh / 2, bh / 2, fillP)
        }
        strokeP.color = 0xFF6B4F35.toInt()
        strokeP.strokeWidth = dp(scene, 1.4f)
        c.drawRoundRect(RectF(bx, byBar, bx + bw, byBar + bh), bh / 2, bh / 2, strokeP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val expTxt = if (s.level >= Progression.MAX_LEVEL) "최고 레벨 달성!" else "경험치 ${s.exp} / ${s.expToNext()}"
        c.drawText(expTxt, bx, byBar + bh + dp(scene, 12f), textP)

        // 숙련 포인트
        textP.textSize = dp(scene, 12.5f)
        textP.color = if (s.skillPoints > 0) 0xFF3F6FB0.toInt() else 0xFF8A7360.toInt()
        val spTxt = "숙련 포인트(SP): ${s.skillPoints}"
        c.drawText(spTxt, tx, byBar + bh + dp(scene, 28f), textP)

        // 돈으로 SP 구매 (탐조 강습)
        val tc = s.trainingCost()
        val trR = RectF(right - dp(scene, 150f), byBar + bh + dp(scene, 16f), right, byBar + bh + dp(scene, 40f))
        if (s.money >= tc) {
            drawButton(c, scene, trR, "탐조 강습 ₩${fmtMoney(tc)}", 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
            btnRects.add(Triple(trR, "train") {
                val cost = scene.game.state.trainingCost()
                if (scene.game.state.money >= cost) {
                    scene.game.state.money -= cost
                    scene.game.state.skillPoints += 1
                    SaveManager.save(scene.game.context, scene.game.state)
                    scene.game.toast("탐조 강습 수료! 숙련 포인트 +1 🎓")
                } else {
                    scene.game.toast("돈이 부족해요!")
                }
            })
        } else {
            drawButton(c, scene, trR, "강습 ₩${fmtMoney(tc)}", Color.argb(100, 200, 190, 175), Color.argb(150, 74, 55, 40), 10.5f)
        }

        ty = byBar + bh + dp(scene, 46f)

        // 스킬 목록 (가로로 넉넉한 1줄 카드)
        val rowH = ((contentBottom() - ty) / Skills.ALL.size).coerceIn(dp(scene, 28f), dp(scene, 46f))
        for (def in Skills.ALL) {
            val rank = s.skillRank(def.id)
            val r = RectF(left, ty, right, ty + rowH - dp(scene, 5f))
            fillP.color = 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 8f), dp(scene, 8f), fillP)
            strokeP.color = 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, 1.3f)
            c.drawRoundRect(r, dp(scene, 8f), dp(scene, 8f), strokeP)

            val midY = r.centerY() - (textP.descent() + textP.ascent()) / 2f

            textP.textSize = dp(scene, 17f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(def.emoji, r.left + dp(scene, 8f), r.centerY() + dp(scene, 6f), textP)

            textP.textSize = dp(scene, 12.5f)
            c.drawText(def.name, r.left + dp(scene, 34f), midY, textP)

            textP.textSize = dp(scene, 10f)
            textP.color = 0xFF6FAE6F.toInt()
            c.drawText("+${def.perRank}", r.left + dp(scene, 130f), midY, textP)

            // 랭크 표시 (●/○)
            val dots = "●".repeat(rank) + "○".repeat(def.maxRank - rank)
            textP.textSize = dp(scene, 12f)
            textP.color = 0xFFB5651D.toInt()
            val dotsW = textP.measureText(dots)
            val dotsX = r.right - dp(scene, 96f) - dotsW - dp(scene, 6f)
            c.drawText(dots, dotsX, r.centerY() + dp(scene, 4f), textP)

            // 강화 버튼
            val br = RectF(r.right - dp(scene, 92f), r.centerY() - dp(scene, 13f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 13f))
            when {
                rank >= def.maxRank -> drawButton(c, scene, br, "MAX", Color.argb(90, 200, 190, 175), Color.argb(150, 74, 55, 40), 11f)
                s.skillPoints <= 0 -> drawButton(c, scene, br, "SP 필요", Color.argb(110, 200, 190, 175), Color.argb(160, 74, 55, 40), 10.5f)
                else -> {
                    drawButton(c, scene, br, "강화 SP1", 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 11f)
                    val id = def.id
                    btnRects.add(Triple(br, "up_$id") {
                        if (scene.game.state.upgradeSkill(id)) {
                            SaveManager.save(scene.game.context, scene.game.state)
                            val nr = scene.game.state.skillRank(id)
                            scene.game.toast("${def.emoji} ${def.name} 강화! (랭크 $nr)")
                        } else {
                            scene.game.toast("숙련 포인트가 부족해요!")
                        }
                    })
                }
            }
            ty += rowH
        }
    }

    private fun drawPizza(c: Canvas) {
        val g = scene.game
        val s = g.state
        var ty = contentTop() + dp(scene, 10f)
        val x = panelR.left + dp(scene, 18f)

        textP.textSize = dp(scene, 12.5f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("화덕에서 토핑을 골라 구울 수 있어요. (최대 ${s.pizzaCapEff()}개)", x, ty, textP)
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

        button(if (g.state.musicOn) "🎵 음악: 켜짐" else "🎵 음악: 꺼짐") {
            g.state.musicOn = !g.state.musicOn
            g.audio.setMusic(g.state.musicOn)
            SaveManager.save(g.context, g.state)
        }
        button(if (g.state.sfxOn) "🔊 효과음: 켜짐" else "🔊 효과음: 꺼짐") {
            g.state.sfxOn = !g.state.sfxOn
            g.audio.setSfx(g.state.sfxOn)
            SaveManager.save(g.context, g.state)
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
        c.drawText("Pizza and Bird v0.3.0-beta01", x, ty, textP)
        ty += dp(scene, 15f)
        c.drawText("완전 오프라인 힐링 게임 · 저장은 자동으로 돼요", x, ty, textP)
        ty += dp(scene, 15f)
        c.drawText("새를 찍어 경험치를 모으면 탐조가 레벨이 올라요!", x, ty, textP)
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
            g.toast("돈이 부족해요… (${won(d.cost)})")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        s.money -= d.cost
        s.decorOwned.add(id)
        SaveManager.save(g.context, s)
        g.toast("${d.emoji} ${d.name} 구매! 집의 장식 칸에 놓아보세요")
        g.sfx(Audio.Sfx.BUY)
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
        c.drawText("집에 놓으면 행운이 오르는 소품들이에요 · 보유 ${won(s.money)}",
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
                drawButton(c, scene, br, won(d.cost), 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 11.5f)
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
// 집 인테리어 카탈로그 — 스타일 구매/적용
// ---------------------------------------------------------------------------

class HouseStyleOverlay(
    scene: Scene,
    private val onApply: (String) -> Unit
) : Overlay(scene) {

    private val styleRects = ArrayList<Pair<RectF, String>>()
    private var closeRect = RectF()
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val g = scene.game
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, id) in styleRects) {
            if (!r.contains(tap.x, tap.y)) continue
            val style = HouseStyles.of(id)
            if (id !in g.state.ownedHouseStyles) {
                if (g.state.money < style.price) {
                    g.toast("돈이 부족해요… 인테리어 비용 ${won(style.price)}")
                    g.sfx(Audio.Sfx.FAIL, 0.5f)
                    return
                }
                g.state.money -= style.price
                g.state.ownedHouseStyles.add(id)
                SaveManager.save(g.context, g.state)
                g.toast("${style.emoji} ${style.name} 구매 완료!")
                g.sfx(Audio.Sfx.BUY)
            }
            onApply(id)
            finished = true
            return
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 155)

        val pw = minOf(w * 0.86f, dp(scene, 450f))
        val ph = minOf(h * 0.9f, dp(scene, 350f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)
        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))

        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🏠 우리 집 인테리어", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 30f), textP)
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("스타일을 구매하면 이사 후에도 계속 사용할 수 있어요 · 보유 ${won(s.money)}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 47f), textP)
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2,
            closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        styleRects.clear()
        var y = panelR.top + dp(scene, 58f)
        for (style in HouseStyles.ALL) {
            val r = RectF(panelR.left + dp(scene, 12f), y, panelR.right - dp(scene, 12f), y + dp(scene, 58f))
            val owned = style.id in s.ownedHouseStyles
            fillP.color = if (style.id == s.houseStyleId) 0xFFFDF3D8.toInt() else 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = if (style.id == s.houseStyleId) style.accentTint else 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, if (style.id == s.houseStyleId) 2.5f else 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)

            textP.textSize = dp(scene, 20f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(style.emoji, r.left + dp(scene, 10f), r.centerY() + dp(scene, 7f), textP)
            textP.textSize = dp(scene, 12.5f)
            c.drawText(style.name, r.left + dp(scene, 44f), r.top + dp(scene, 19f), textP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(style.desc, r.left + dp(scene, 44f), r.top + dp(scene, 36f), textP)

            val br = RectF(r.right - dp(scene, 94f), r.centerY() - dp(scene, 15f), r.right - dp(scene, 9f), r.centerY() + dp(scene, 15f))
            if (style.id == s.houseStyleId) {
                drawButton(c, scene, br, "적용중", 0xFFD7E3C3.toInt(), 0xFF4A3728.toInt(), 10.5f)
            } else if (owned) {
                drawButton(c, scene, br, "적용", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 11.5f)
            } else {
                drawButton(c, scene, br, won(style.price), 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
            }
            styleRects.add(r to style.id)
            y += dp(scene, 64f)
        }
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
                        scene.game.sfx(Audio.Sfx.TAP, 0.6f)
                        scene.game.audio.playAmb(R.raw.amb_fire, 0.5f)   // 🔥 화덕 불 소리
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
        scene.game.audio.stopAmb()   // 화덕 불 소리 끄기
        when (resultQ) {
            2 -> scene.game.sfx(Audio.Sfx.SPARKLE)          // 걸작!
            1 -> scene.game.sfx(Audio.Sfx.SUCCESS, 0.8f)    // 맛있는
            else -> scene.game.sfx(Audio.Sfx.FAIL, 0.6f)    // 살짝 탐
        }
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
    private val questLine: String?,
    private val expGain: Int = 0,
    private val levelsGained: Int = 0,
    private val prevLevel: Int = 1
) : Overlay(scene) {

    private var t = 0f
    private var cuedStars = false
    private var cuedNew = false
    private var cuedQuest = false

    override fun update(dt: Float) {
        t += dt
        val g = scene.game
        // 연출에 맞춰 순차 재생: 별점 → 도감 신규 → 의뢰 보수
        if (!cuedStars && t >= 0.3f) {
            cuedStars = true
            g.sfx(Audio.Sfx.SUCCESS, 0.55f + stars * 0.15f)
        }
        if (isNew && !cuedNew && t >= 0.75f) {
            cuedNew = true
            g.sfx(Audio.Sfx.SPARKLE, 0.9f)
        }
        if (questLine != null && !cuedQuest && t >= 1.15f) {
            cuedQuest = true
            g.sfx(Audio.Sfx.REWARD, 0.9f)   // 의뢰 보수 ₩
        }
    }

    override fun handleInput(input: Input) {
        // 열린 직후 0.25초는 셔터 플래시 연출 보호 + 실수 방지 (입력 소비만)
        if (t < 0.25f) {
            input.consumeTapScreen()
            return
        }
        val tap = input.consumeTapScreen()
        if (input.justA || input.justB || input.justBack || tap != null) {
            if (t < 0.25f) return   // 셔터 직후 오발 방지
            if (levelsGained > 0) {
                // 레벨업 축하 화면으로 이어짐 (오버레이 체이닝)
                scene.openOverlay(LevelUpOverlay(scene, prevLevel, scene.game.state.level))
            } else {
                finished = true
            }
        }
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
        val cw = minOf(w * 0.7f, dp(scene, 390f))
        val chh = dp(scene, 246f + (if (questLine != null) 16f else 0f) + (if (expGain > 0) 44f else 0f))
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        // 이름 + 첫 발견 리본
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText(def.name, r.centerX() - textP.measureText(def.name) / 2, r.top + dp(scene, 25f), textP)
        if (isNew) {
            textP.textSize = dp(scene, 9.5f)
            val ribbon = "NEW · 첫 발견"
            val rw = textP.measureText(ribbon) + dp(scene, 14f)
            val rr = RectF(r.right - rw - dp(scene, 10f), r.top + dp(scene, 9f), r.right - dp(scene, 10f), r.top + dp(scene, 29f))
            fillP.color = 0xFFE2574C.toInt()
            c.drawRoundRect(rr, dp(scene, 9f), dp(scene, 9f), fillP)
            textP.color = 0xFFFFF8E8.toInt()
            c.drawText(ribbon, rr.centerX() - textP.measureText(ribbon) / 2, rr.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
        }

        // 새가 가장 돋보이는 작은 서식지 사진 카드
        val photoR = RectF(r.left + dp(scene, 22f), r.top + dp(scene, 38f), r.right - dp(scene, 22f), r.top + dp(scene, 148f))
        val habitat = def.habitats.firstOrNull() ?: "field"
        fillP.color = when (habitat) {
            "coast", "water", "wetland" -> 0xFFBFE3E8.toInt()
            "forest" -> 0xFFCFE1BB.toInt()
            "mountain" -> 0xFFD5DDD0.toInt()
            "city" -> 0xFFD9DDD8.toInt()
            else -> 0xFFE9DDAF.toInt()
        }
        c.drawRoundRect(photoR, dp(scene, 8f), dp(scene, 8f), fillP)
        // 원경과 지면은 단순한 픽셀 아트 실루엣으로 유지한다.
        val groundY = photoR.bottom - dp(scene, 28f)
        fillP.color = when (habitat) {
            "coast", "water" -> 0xFF74BED3.toInt()
            "wetland" -> 0xFF8FBF8B.toInt()
            "forest" -> 0xFF73A66D.toInt()
            "mountain" -> 0xFF829B82.toInt()
            "city" -> 0xFFA7AAA3.toInt()
            else -> 0xFFB7C982.toInt()
        }
        c.drawRect(photoR.left, groundY, photoR.right, photoR.bottom, fillP)
        if (habitat == "forest" || habitat == "mountain") {
            fillP.color = Color.argb(105, 55, 106, 64)
            c.drawCircle(photoR.left + dp(scene, 24f), groundY, dp(scene, 24f), fillP)
            c.drawCircle(photoR.right - dp(scene, 22f), groundY + dp(scene, 2f), dp(scene, 27f), fillP)
        } else if (habitat == "wetland" || habitat == "water") {
            fillP.color = Color.argb(145, 247, 250, 232)
            c.drawRect(photoR.left + dp(scene, 14f), groundY + dp(scene, 9f), photoR.left + dp(scene, 70f), groundY + dp(scene, 11f), fillP)
            c.drawRect(photoR.right - dp(scene, 82f), groundY + dp(scene, 18f), photoR.right - dp(scene, 20f), groundY + dp(scene, 20f), fillP)
        }

        val a = g.assets
        val bmp = a.bird(def.id)
        val maxBirdW = photoR.width() - dp(scene, 72f)
        val maxBirdH = photoR.height() - dp(scene, 30f)
        val k = minOf(maxBirdW / bmp.width, maxBirdH / bmp.height)
        val birdW = bmp.width * k
        val birdH = bmp.height * k
        val bx = photoR.centerX() - birdW / 2f
        val by = groundY - birdH + dp(scene, 8f)
        fillP.color = Color.argb(42, 40, 45, 38)
        c.drawOval(RectF(photoR.centerX() - birdW * 0.32f, groundY + dp(scene, 5f), photoR.centerX() + birdW * 0.32f, groundY + dp(scene, 11f)), fillP)
        c.drawBitmap(bmp, null, RectF(bx, by, bx + birdW, by + birdH), a.sprPaint)
        strokeP.color = 0xFF6B4F35.toInt()
        strokeP.strokeWidth = dp(scene, 1.6f)
        c.drawRoundRect(photoR, dp(scene, 8f), dp(scene, 8f), strokeP)

        // 희귀도 라벨
        val tierColor = when (def.tier) {
            Tier.COMMON -> 0xFF777F82.toInt()
            Tier.UNCOMMON -> 0xFF5E934F.toInt()
            Tier.RARE -> 0xFF3F6FB0.toInt()
            Tier.LEGEND -> 0xFFB65342.toInt()
        }
        textP.textSize = dp(scene, 9f)
        val tierLabel = "${def.tier.label} · ${def.activeLabel}"
        val tierW = textP.measureText(tierLabel) + dp(scene, 12f)
        val tierR = RectF(photoR.left + dp(scene, 7f), photoR.top + dp(scene, 7f), photoR.left + dp(scene, 7f) + tierW, photoR.top + dp(scene, 25f))
        fillP.color = Color.argb(220, Color.red(tierColor), Color.green(tierColor), Color.blue(tierColor))
        c.drawRoundRect(tierR, dp(scene, 8f), dp(scene, 8f), fillP)
        textP.color = 0xFFFFF8E8.toInt()
        c.drawText(tierLabel, tierR.centerX() - textP.measureText(tierLabel) / 2, tierR.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = dp(scene, 15f)
        textP.color = 0xFFB5651D.toInt()
        val starTxt = "★".repeat(stars) + "☆".repeat(3 - stars)
        val y1 = r.top + dp(scene, 171f)
        c.drawText(starTxt, r.centerX() - textP.measureText(starTxt) / 2, y1, textP)

        textP.textSize = dp(scene, 11.5f)
        textP.color = 0xFF8A7360.toInt()
        val best = g.state.bestStars[def.id] ?: stars
        val cnt = "촬영 ${count}회 · 최고 ★$best" + if (def.englishName.isNotBlank()) " · ${def.englishName}" else ""
        c.drawText(cnt, r.centerX() - textP.measureText(cnt) / 2, y1 + dp(scene, 19f), textP)

        var yq = y1 + dp(scene, 40f)
        if (questLine != null) {
            textP.textSize = dp(scene, 11.5f)
            textP.color = 0xFF3F6FB0.toInt()
            c.drawText(questLine, r.centerX() - textP.measureText(questLine) / 2, yq, textP)
            yq += dp(scene, 17f)
        }

        // 경험치 획득 + 경험치 바
        if (expGain > 0) {
            val s2 = g.state
            textP.textSize = dp(scene, 12f)
            textP.color = 0xFF6FAE6F.toInt()
            val expTxt = if (levelsGained > 0) "경험치 +$expGain  · 레벨 업! ✨" else "경험치 +$expGain"
            c.drawText(expTxt, r.centerX() - textP.measureText(expTxt) / 2, yq, textP)

            val ebx = r.left + dp(scene, 40f)
            val ebw = r.width() - dp(scene, 80f)
            val eby = yq + dp(scene, 8f)
            val ebh = dp(scene, 8f)
            fillP.color = 0xFFD6C5A4.toInt()
            c.drawRoundRect(RectF(ebx, eby, ebx + ebw, eby + ebh), ebh / 2, ebh / 2, fillP)
            val prog = s2.expProgress()
            if (prog > 0.01f) {
                fillP.color = 0xFF6FBA6B.toInt()
                c.drawRoundRect(RectF(ebx, eby, ebx + ebw * prog, eby + ebh), ebh / 2, ebh / 2, fillP)
            }
            strokeP.color = 0xFF6B4F35.toInt()
            strokeP.strokeWidth = dp(scene, 1.2f)
            c.drawRoundRect(RectF(ebx, eby, ebx + ebw, eby + ebh), ebh / 2, ebh / 2, strokeP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            val lvTxt = if (s2.level >= Progression.MAX_LEVEL) "Lv.${s2.level} MAX"
                else "Lv.${s2.level}  (${s2.exp}/${s2.expToNext()})"
            c.drawText(lvTxt, r.centerX() - textP.measureText(lvTxt) / 2, eby + ebh + dp(scene, 12f), textP)
        }

        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val hint = if (levelsGained > 0) "화면을 탭해 계속" else "화면을 탭해 탐조를 계속해요"
        c.drawText(hint, r.centerX() - textP.measureText(hint) / 2, r.bottom - dp(scene, 10f), textP)
    }
}

// ---------------------------------------------------------------------------
// 레벨업 축하
// ---------------------------------------------------------------------------

class LevelUpOverlay(
    scene: Scene,
    private val fromLevel: Int,
    private val toLevel: Int
) : Overlay(scene) {

    private var t = 0f

    override fun update(dt: Float) { t += dt }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (t < 0.3f) return
        if (input.justA || input.justB || input.justBack || tap != null) finished = true
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val cw = minOf(w * 0.62f, dp(scene, 360f))
        val chh = dp(scene, 244f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val ringA = (0.4f + 0.6f * abs(sin(t * 3.2f)))
        strokeP.color = Color.argb((200 * ringA).toInt(), 242, 208, 107)
        strokeP.strokeWidth = dp(scene, 3f)
        c.drawRoundRect(RectF(r.left + dp(scene, 4f), r.top + dp(scene, 4f), r.right - dp(scene, 4f), r.bottom - dp(scene, 4f)), dp(scene, 10f), dp(scene, 10f), strokeP)

        textP.textSize = dp(scene, 22f)
        textP.color = 0xFFB5651D.toInt()
        val t1 = "🎉 레벨 업! 🎉"
        c.drawText(t1, r.centerX() - textP.measureText(t1) / 2, r.top + dp(scene, 42f), textP)

        val a = g.assets
        val ps = a.playerSet(s.gender, s.gearTier())
        val bmp = ps.down[0]
        val k = dp(scene, 3.4f)
        val bx = r.centerX() - bmp.width * k / 2f
        val by = r.top + dp(scene, 58f)
        c.drawOval(RectF(bx + dp(scene, 6f), by + bmp.height * k - dp(scene, 4f), bx + bmp.width * k - dp(scene, 6f), by + bmp.height * k + dp(scene, 4f)), a.shadowPaint)
        c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)

        textP.textSize = dp(scene, 18f)
        textP.color = 0xFF4A3728.toInt()
        val lv = "Lv.$fromLevel  →  Lv.$toLevel"
        c.drawText(lv, r.centerX() - textP.measureText(lv) / 2, by + bmp.height * k + dp(scene, 26f), textP)

        textP.textSize = dp(scene, 13f)
        textP.color = 0xFF6FAE6F.toInt()
        val title = "「 ${Progression.title(toLevel)} 」"
        c.drawText(title, r.centerX() - textP.measureText(title) / 2, by + bmp.height * k + dp(scene, 46f), textP)

        textP.textSize = dp(scene, 11.5f)
        textP.color = 0xFF3F6FB0.toInt()
        val sp = "숙련 포인트 +${s.skillPoints}  (메뉴 › 성장 에서 능력 강화!)"
        c.drawText(sp, r.centerX() - textP.measureText(sp) / 2, by + bmp.height * k + dp(scene, 64f), textP)

        if (Progression.gearTier(fromLevel) != Progression.gearTier(toLevel)) {
            textP.textSize = dp(scene, 11f)
            textP.color = 0xFFB5651D.toInt()
            val gearMsg = "새 탐조 장비를 갖췄어요! 👒"
            c.drawText(gearMsg, r.centerX() - textP.measureText(gearMsg) / 2, r.bottom - dp(scene, 26f), textP)
        }

        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val hint = "탭해서 닫기"
        c.drawText(hint, r.centerX() - textP.measureText(hint) / 2, r.bottom - dp(scene, 10f), textP)
    }
}

// ---------------------------------------------------------------------------
// 큰 지도
// ---------------------------------------------------------------------------

/**
 * 전국 탐조 지도 — 실제 한반도 모양 위에 32개 지역을 표시한다.
 * 손가락으로 끌어 이동, 두 손가락으로 확대/축소, 지역을 누르면 상세 정보.
 */
class MapOverlay(scene: Scene) : Overlay(scene) {

    private var mapR = RectF()
    private var scale = 1f          // 정규화 1단위 -> 화면 px
    private var offX = 0f
    private var offY = 0f
    private var fitScale = 1f
    private var inited = false

    private var selected: RegionDef? = null

    // 버튼
    private var closeR = RectF()
    private var zoomInR = RectF()
    private var zoomOutR = RectF()
    private var resetR = RectF()
    private var homeR = RectF()

    // 터치 추적
    private class P(var x: Float, var y: Float, val sx: Float, val sy: Float, var moved: Boolean = false)

    private val pts = LinkedHashMap<Int, P>()
    private var pinchDist = 0f
    private var pinchMidX = 0f
    private var pinchMidY = 0f

    init {
        scene.game.input.rawMode = true
    }

    private fun close() {
        scene.game.input.rawMode = false
        finished = true
    }

    private fun sx(nx: Float) = offX + nx * scale
    private fun sy(ny: Float) = offY + ny * scale

    private fun layout() {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        mapR = RectF(dp(scene, 10f), dp(scene, 44f), w - dp(scene, 10f), h - dp(scene, 10f))
        if (!inited) {
            val b = KoreaMap.southBounds
            fitScale = minOf(
                (mapR.width() - dp(scene, 24f)) / b.width(),
                (mapR.height() - dp(scene, 24f)) / b.height()
            )
            scale = fitScale
            centerOn(b.centerX(), b.centerY())
            inited = true
        }
    }

    private fun centerOn(nx: Float, ny: Float) {
        offX = mapR.centerX() - nx * scale
        offY = mapR.centerY() - ny * scale
        clamp()
    }

    /** 지도가 화면 밖으로 완전히 빠져나가지 않도록 */
    private fun clamp() {
        val b = KoreaMap.southBounds
        val m = dp(scene, 60f)
        offX = offX.coerceIn(mapR.left + m - b.right * scale, mapR.right - m - b.left * scale)
        offY = offY.coerceIn(mapR.top + m - b.bottom * scale, mapR.bottom - m - b.top * scale)
    }

    private fun zoomAt(factor: Float, cx: Float, cy: Float) {
        val old = scale
        scale = (scale * factor).coerceIn(fitScale * 0.7f, fitScale * 16f)
        val k = scale / old
        offX = cx - (cx - offX) * k
        offY = cy - (cy - offY) * k
        clamp()
    }

    private fun regionAt(x: Float, y: Float): RegionDef? {
        var best: RegionDef? = null
        var bestD = dp(scene, 26f)
        for (reg in Regions.ALL) {
            val dx = sx(reg.mmX) - x
            val dy = sy(reg.mmY) - y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d < bestD) { bestD = d; best = reg }
        }
        return best
    }

    override fun handleInput(input: Input) {
        if (input.justB || input.justBack || input.justMap) { close(); return }
        if (!inited) return

        evloop@ for (ev in input.rawEvents) {
            when (ev.kind) {
                Input.RawEv.DOWN -> {
                    pts[ev.id] = P(ev.x, ev.y, ev.x, ev.y)
                    if (pts.size == 2) startPinch()
                }
                Input.RawEv.MOVE -> {
                    val p = pts[ev.id] ?: continue@evloop
                    val dx = ev.x - p.x
                    val dy = ev.y - p.y
                    p.x = ev.x; p.y = ev.y
                    if (kotlin.math.abs(ev.x - p.sx) + kotlin.math.abs(ev.y - p.sy) > dp(scene, 6f)) p.moved = true
                    if (pts.size == 1) {
                        offX += dx; offY += dy
                        clamp()
                    } else if (pts.size >= 2) {
                        val list = pts.values.toList()
                        val a = list[0]; val b = list[1]
                        val nd = kotlin.math.sqrt(
                            (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
                        )
                        val mx = (a.x + b.x) / 2f
                        val my = (a.y + b.y) / 2f
                        if (pinchDist > 1f && nd > 1f) zoomAt(nd / pinchDist, mx, my)
                        offX += mx - pinchMidX
                        offY += my - pinchMidY
                        pinchDist = nd; pinchMidX = mx; pinchMidY = my
                        clamp()
                        for (q in pts.values) q.moved = true
                    }
                }
                Input.RawEv.UP -> {
                    val p = pts.remove(ev.id)
                    if (pts.size == 1) startPinch()
                    if (p != null && !p.moved) onTap(ev.x, ev.y)
                }
            }
        }
    }

    private fun startPinch() {
        val list = pts.values.toList()
        if (list.size >= 2) {
            val a = list[0]; val b = list[1]
            pinchDist = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
            pinchMidX = (a.x + b.x) / 2f
            pinchMidY = (a.y + b.y) / 2f
        } else {
            pinchDist = 0f
        }
    }

    private fun onTap(x: Float, y: Float) {
        val g = scene.game
        if (closeR.contains(x, y)) { close(); return }
        if (zoomInR.contains(x, y)) { zoomAt(1.4f, mapR.centerX(), mapR.centerY()); return }
        if (zoomOutR.contains(x, y)) { zoomAt(1f / 1.4f, mapR.centerX(), mapR.centerY()); return }
        if (resetR.contains(x, y)) {
            scale = fitScale
            centerOn(KoreaMap.southBounds.centerX(), KoreaMap.southBounds.centerY())
            selected = null
            return
        }
        if (homeR.contains(x, y)) {
            val cur = Regions.byId[g.state.region]
            if (cur != null) {
                scale = (fitScale * 3.2f).coerceAtMost(fitScale * 16f)
                centerOn(cur.mmX, cur.mmY)
                selected = cur
            }
            return
        }
        if (mapR.contains(x, y)) {
            val hit = regionAt(x, y)
            selected = if (hit != null && hit == selected) null else hit
            if (hit != null) g.haptic()
        }
    }

    // ------------------------------------------------------------------

    override fun draw(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val s = g.state
        dim(c, scene, 190)
        layout()

        // 바다
        fillP.color = if (s.isNight()) 0xFF2A4A5E.toInt() else 0xFF9FD3E8.toInt()
        c.drawRoundRect(mapR, dp(scene, 10f), dp(scene, 10f), fillP)

        c.save()
        val clip = Path()
        clip.addRoundRect(mapR, dp(scene, 10f), dp(scene, 10f), Path.Direction.CW)
        c.clipPath(clip)

        drawGrid(c)

        // 육지
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        val detail = if (scale > fitScale * 1.8f) 2 else 1
        KoreaMap.drawLand(c, scale, detail, s.isNight())
        c.restore()

        drawLinks(c)
        drawRegions(c)

        c.restore()

        // 테두리
        strokeP.color = 0xFF6B4F35.toInt()
        strokeP.strokeWidth = dp(scene, 2.5f)
        c.drawRoundRect(mapR, dp(scene, 10f), dp(scene, 10f), strokeP)

        // 제목
        textP.textSize = dp(scene, 15f)
        textP.color = 0xFFF8EFDC.toInt()
        val t1 = "🗺 대한민국 탐조 지도"
        c.drawText(t1, dp(scene, 14f), dp(scene, 30f), textP)
        textP.textSize = dp(scene, 10.5f)
        val t2 = "방문 ${s.visited.size}/${Regions.ALL.size} · 두 손가락으로 확대 · 끌어서 이동 · 지역을 누르면 정보"
        c.drawText(t2, dp(scene, 170f), dp(scene, 29f), textP)

        drawButtons(c)
        drawLegend(c)
        selected?.let { drawInfo(c, it) }
    }

    private fun drawGrid(c: Canvas) {
        strokeP.color = Color.argb(45, 255, 255, 255)
        strokeP.strokeWidth = dp(scene, 0.8f)
        var lon = 124f
        while (lon <= 132f) {
            val x = sx(KoreaMap.nx(lon))
            if (x > mapR.left && x < mapR.right) c.drawLine(x, mapR.top, x, mapR.bottom, strokeP)
            lon += 1f
        }
        var lat = 33f
        while (lat <= 43f) {
            val y = sy(KoreaMap.ny(lat))
            if (y > mapR.top && y < mapR.bottom) c.drawLine(mapR.left, y, mapR.right, y, strokeP)
            lat += 1f
        }
    }

    private fun drawLinks(c: Canvas) {
        val s = scene.game.state
        for ((a, b) in Regions.allLinks()) {
            val ra = Regions.byId[a] ?: continue
            val rb = Regions.byId[b] ?: continue
            val known = a in s.visited || b in s.visited
            strokeP.color = if (known) Color.argb(190, 255, 255, 255) else Color.argb(70, 255, 255, 255)
            strokeP.strokeWidth = dp(scene, if (known) 1.8f else 1.1f)
            c.drawLine(sx(ra.mmX), sy(ra.mmY), sx(rb.mmX), sy(rb.mmY), strokeP)
        }
    }

    private fun drawRegions(c: Canvas) {
        val g = scene.game
        val s = g.state
        val showAllNames = scale > fitScale * 1.5f
        for (reg in Regions.ALL) {
            val x = sx(reg.mmX)
            val y = sy(reg.mmY)
            val outside = x < mapR.left - dp(scene, 40f) || x > mapR.right + dp(scene, 40f) ||
                    y < mapR.top - dp(scene, 40f) || y > mapR.bottom + dp(scene, 40f)
            if (outside) continue

            val visited = reg.id in s.visited
            val isCurrent = reg.id == s.region
            val isHome = reg.id == s.homeRegion
            val r = dp(scene, if (isCurrent) 8f else 6f)

            if (isCurrent) {
                strokeP.color = Color.argb(150, 226, 87, 76)
                strokeP.strokeWidth = dp(scene, 2f)
                c.drawCircle(x, y, r + dp(scene, 4f) + dp(scene, 2f) * sin(g.time * 4f), strokeP)
            }
            fillP.color = when {
                isCurrent -> 0xFFE2574C.toInt()
                visited -> reg.kind.color
                else -> Color.argb(150, 120, 116, 108)
            }
            c.drawCircle(x, y, r, fillP)
            strokeP.color = if (selected?.id == reg.id) 0xFF4A3728.toInt() else Color.argb(220, 255, 255, 255)
            strokeP.strokeWidth = dp(scene, if (selected?.id == reg.id) 2.6f else 1.4f)
            c.drawCircle(x, y, r, strokeP)

            if (isHome) {
                textP.textSize = dp(scene, 11f)
                textP.color = 0xFF4A3728.toInt()
                c.drawText("🏠", x - dp(scene, 6f), y - r - dp(scene, 3f), textP)
            }

            if (showAllNames || isCurrent || isHome || reg.kind == RegionKind.TOWN) {
                val nm = if (visited) reg.name else "? ${reg.name}"
                textP.textSize = dp(scene, if (isCurrent) 11.5f else 10.5f)
                val tw = textP.measureText(nm)
                fillP.color = Color.argb(170, 255, 252, 240)
                c.drawRoundRect(
                    RectF(x - tw / 2 - dp(scene, 3f), y + r + dp(scene, 1f), x + tw / 2 + dp(scene, 3f), y + r + dp(scene, 14f)),
                    dp(scene, 3f), dp(scene, 3f), fillP
                )
                textP.color = if (isCurrent) 0xFFD1372C.toInt() else if (visited) 0xFF3A2A24.toInt() else Color.argb(170, 58, 42, 36)
                c.drawText(nm, x - tw / 2, y + r + dp(scene, 11f), textP)
            }
        }
    }

    private fun drawButtons(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val bs = dp(scene, 34f)
        closeR = RectF(w - dp(scene, 14f) - bs, dp(scene, 6f), w - dp(scene, 14f), dp(scene, 6f) + bs * 0.9f)
        zoomInR = RectF(mapR.right - dp(scene, 12f) - bs, mapR.bottom - dp(scene, 12f) - bs * 2 - dp(scene, 8f), mapR.right - dp(scene, 12f), mapR.bottom - dp(scene, 12f) - bs - dp(scene, 8f))
        zoomOutR = RectF(mapR.right - dp(scene, 12f) - bs, mapR.bottom - dp(scene, 12f) - bs, mapR.right - dp(scene, 12f), mapR.bottom - dp(scene, 12f))
        resetR = RectF(mapR.left + dp(scene, 12f), mapR.bottom - dp(scene, 12f) - bs, mapR.left + dp(scene, 12f) + dp(scene, 76f), mapR.bottom - dp(scene, 12f))
        homeR = RectF(resetR.right + dp(scene, 8f), resetR.top, resetR.right + dp(scene, 8f) + dp(scene, 76f), resetR.bottom)

        drawButton(c, scene, closeR, "✕ 닫기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12f)
        drawButton(c, scene, zoomInR, "＋", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 17f)
        drawButton(c, scene, zoomOutR, "－", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 17f)
        drawButton(c, scene, resetR, "전체 보기", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 11.5f)
        drawButton(c, scene, homeR, "📍 내 위치", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 11.5f)
    }

    private fun drawLegend(c: Canvas) {
        val x0 = mapR.left + dp(scene, 12f)
        var y = mapR.top + dp(scene, 16f)
        fillP.color = Color.argb(190, 255, 252, 240)
        val lw = dp(scene, 92f)
        val lh = dp(scene, 14f) * RegionKind.values().size + dp(scene, 10f)
        c.drawRoundRect(RectF(x0 - dp(scene, 6f), y - dp(scene, 12f), x0 + lw, y - dp(scene, 12f) + lh), dp(scene, 6f), dp(scene, 6f), fillP)
        for (k in RegionKind.values()) {
            fillP.color = k.color
            c.drawCircle(x0 + dp(scene, 2f), y - dp(scene, 3f), dp(scene, 4f), fillP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(k.label, x0 + dp(scene, 10f), y, textP)
            y += dp(scene, 14f)
        }
    }

    private fun drawInfo(c: Canvas, reg: RegionDef) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val cardW = minOf(dp(scene, 320f), mapR.width() - dp(scene, 24f))
        val cardH = dp(scene, 132f)
        val r = RectF(
            mapR.right - dp(scene, 12f) - cardW, mapR.top + dp(scene, 12f),
            mapR.right - dp(scene, 12f), mapR.top + dp(scene, 12f) + cardH
        )
        fillP.color = Color.argb(245, 248, 239, 220)
        c.drawRoundRect(r, dp(scene, 10f), dp(scene, 10f), fillP)
        strokeP.color = reg.kind.color
        strokeP.strokeWidth = dp(scene, 2.5f)
        c.drawRoundRect(r, dp(scene, 10f), dp(scene, 10f), strokeP)

        val x = r.left + dp(scene, 12f)
        var y = r.top + dp(scene, 20f)
        textP.textSize = dp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        val title = "${reg.emoji} ${reg.name}"
        c.drawText(title, x, y, textP)
        val tw = textP.measureText(title)
        textP.textSize = dp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(reg.english, x + tw + dp(scene, 6f), y, textP)
        val badge = when {
            reg.id == s.region -> "현재 위치"
            reg.id == s.homeRegion -> "우리 집"
            reg.id in s.visited -> "방문함"
            else -> "미방문"
        }
        textP.textSize = dp(scene, 9.5f)
        textP.color = if (reg.id == s.region) 0xFFD1372C.toInt() else 0xFF6B4F35.toInt()
        c.drawText(badge, r.right - dp(scene, 12f) - textP.measureText(badge), y, textP)

        y += dp(scene, 15f)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF6FAE6F.toInt()
        c.drawText("${reg.kind.label} · ${reg.habitatLabels}", x, y, textP)

        y += dp(scene, 14f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("🐦 " + Regions.signatureBirds(reg).joinToString(", ") { it.name }, x, y, textP)

        y += dp(scene, 14f)
        textP.color = 0xFF3F6FB0.toInt()
        c.drawText("📅 추천 시기: ${reg.season}", x, y, textP)

        y += dp(scene, 14f)
        textP.color = 0xFF8A7360.toInt()
        for (ln in g.hud.wrapText(if (reg.tip.isNotEmpty()) reg.tip else reg.desc, textP, r.width() - dp(scene, 24f)).take(2)) {
            c.drawText(ln, x, y, textP)
            y += dp(scene, 12f)
        }

        y += dp(scene, 2f)
        val exits = Regions.exits(reg.id).values.mapNotNull { Regions.byId[it]?.name }
        textP.color = 0xFF8A7360.toInt()
        val ex = "🚲 연결: " + if (exits.isEmpty()) "-" else exits.joinToString(", ")
        for (ln in g.hud.wrapText(ex, textP, r.width() - dp(scene, 24f)).take(2)) {
            c.drawText(ln, x, y, textP)
            y += dp(scene, 12f)
        }
    }
}
