package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sin

/**
 * 씬 위에 뜨는 UI 오버레이 (대화상자/메뉴/미니게임 등).
 */
abstract class Overlay(val scene: Scene) {
    var finished = false
    /** 등장 애니메이션 기준 시각 */
    val bornAt: Long = SystemClock.uptimeMillis()

    open fun update(dt: Float) {}
    open fun handleInput(input: Input) {}
    open fun draw(c: Canvas) {}

    /** 등장 애니메이션 진행도 0..1 */
    fun enterT(): Float = UiKit.enter(bornAt)
    /** 등장 시 위로 올라오는 오프셋(px) */
    fun enterShift(): Float = UiKit.enterShift(scene.game, bornAt)
}

// 공통 페인트 (서브 UI 직접 그리기용)
// 화면마다 dp 를 직접 지정하는 곳에서 쓰는 공용 텍스트 페인트.
// 글꼴은 Type 이 관리한다(assets 에 둥근 한글 폰트를 넣으면 그대로 적용).
private val textP = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply { letterSpacing = 0.01f }
private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

// 글자는 Type.text / Type.paint 와 Role 로 그린다 — 크기·굵기·자간이 역할마다 고정되고,
// 라틴/숫자만 모인 문장은 자동으로 5x7 픽셀 폰트로 넘어간다.

private fun dp(scene: Scene, v: Float): Float = v * scene.game.density

/** 프리미엄 패널 그리기 (섀도우 + 그라데이션 + 이중 테두리) */
private fun panel(c: Canvas, r: RectF, scene: Scene) {
    UiKit.panel(c, scene.game, r)
}

/** 프리미엄 딤 (등장 페이드 포함) */
private fun dim(c: Canvas, scene: Scene, alpha: Int = 130) {
    UiKit.dim(c, scene.game, alpha, scene.overlay?.bornAt ?: 0L)
}

/** 닫기(✕) 글자 */
private fun drawCloseX(c: Canvas, scene: Scene, r: RectF) {
    Type.text(c, "✕", r.centerX(), Type.midBaseline(Role.HEADING, r.centerY(), "✕", Type.CARAMEL), Role.HEADING, Type.CARAMEL, 0.5f)
}

/** 프리미엄 카드 행(선택 상태 지원) */
private fun drawCard(
    c: Canvas, scene: Scene, r: RectF,
    selected: Boolean = false, borderColor: Int = 0xFFC9A87B.toInt(), borderW: Float = 1.5f
) {
    UiKit.card(c, scene.game, r, 10f, selected, borderColor, borderW)
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
    // 버튼 글자: 살짝 벌린 자간 + 아주 옅은 그림자(연한 버튼 위에서도 읽히게)
    val p = Type.paintAt(textSize, true, 0.035f, textCol)
    val ty = r.centerY() - (p.descent() + p.ascent()) / 2
    if (Color.alpha(textCol) > 200) {
        c.drawText(label, r.centerX() - p.measureText(label) / 2, ty + dp(scene, 1.2f), Type.paintAt(textSize, true, 0.035f, Type.DROP))
    }
    c.drawText(label, r.centerX() - p.measureText(label) / 2, ty, p)
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

        // 본문 줄 수에 맞춰 패널 높이 결정 (본문은 Role.BODY — 보통 두께가 읽기 편하다)
        val bodyPaint = Type.paint(Role.BODY, Type.INK)
        val maxW = w - margin * 2f - dp(scene, 28f)
        val lines = g.hud.wrapText(body, bodyPaint, maxW).take(8)
        val panelH = dp(scene, 108f) + Type.lineHeight(Role.BODY) * (lines.size - 3).coerceAtLeast(0)
        val r = RectF(margin, h - margin - panelH, w - margin, h - margin)

        // 등장: 아래에서 위로 부드럽게
        c.save()
        c.translate(0f, enterShift())
        panel(c, r, scene)

        // 말주머니 제목 칩
        if (title.isNotEmpty()) {
            val tw = Type.width(Role.LABEL, title, Type.CREAM)
            val chip = RectF(r.left + dp(scene, 12f), r.top - dp(scene, 11f), r.left + dp(scene, 12f) + tw + dp(scene, 18f), r.top + dp(scene, 11f))
            fillP.color = Type.BROWN
            c.drawRoundRect(chip, dp(scene, 9f), dp(scene, 9f), fillP)
            Type.text(c, title, chip.centerX(), Type.midBaseline(Role.LABEL, chip.centerY(), title, Type.CREAM), Role.LABEL, Type.CREAM, 0.5f)
        }

        // 본문
        var ty = r.top + dp(scene, 25f)
        for (ln in lines) {
            c.drawText(ln, r.left + dp(scene, 14f), ty, bodyPaint)
            ty += Type.lineHeight(Role.BODY)
        }

        // 선택지 — 첫 번째(긍정) 버튼은 골드 프라이머리, 나머지는 크림 세컨더리
        val n = choices.size
        val bw = (r.width() - dp(scene, 10f) * (n + 1)) / n
        val bty = r.bottom - dp(scene, 40f)
        val rects = ArrayList<RectF>()
        for (i in 0 until n) {
            val br = RectF(
                r.left + dp(scene, 10f) + i * (bw + dp(scene, 10f)), bty,
                r.left + dp(scene, 10f) + i * (bw + dp(scene, 10f)) + bw, bty + dp(scene, 30f)
            )
            drawButton(c, scene, br, choices[i].label, 0xFFF2E3C2.toInt(), Type.BROWN, 12.5f)
            rects.add(br)
        }
        choiceRects = rects
        c.restore()
    }
}

// ---------------------------------------------------------------------------
// 메뉴 (상태 / 피자 / 도감 / 설정)
// ---------------------------------------------------------------------------

class MenuOverlay(scene: Scene) : Overlay(scene) {

    private enum class Tab(val label: String, val icon: String) {
        STATUS("상태", "📊"), QUEST("퀘스트", "🗺"), GROW("성장", "🌱"), PIZZA("피자", "🍕"), BOOK("도감", "📚"), SETTINGS("설정", "⚙")
    }

    private var tab = Tab.STATUS
    private val tabRects = ArrayList<Pair<RectF, Tab>>()
    private val btnRects = ArrayList<Triple<RectF, String, () -> Unit>>()
    private var closeRect = RectF()
    private var resetArmed = false
    private var questPage = 0
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
        drawCloseX(c, scene, closeRect)

        // 탭 — 아이콘 + 선택 탭 골드 밑줄
        tabRects.clear()
        val nTabs = Tab.values().size
        val tabGap = dp(scene, 8f)
        val tabW = (panelR.width() - dp(scene, 24f) - tabGap * (nTabs - 1)) / nTabs
        val tabTop = panelR.top + dp(scene, 38f)
        val tabH = dp(scene, 28f)
        for ((i, t) in Tab.values().withIndex()) {
            val r = RectF(
                panelR.left + dp(scene, 12f) + i * (tabW + tabGap), tabTop,
                panelR.left + dp(scene, 12f) + i * (tabW + tabGap) + tabW, tabTop + tabH
            )
            val sel = t == tab
            fillP.color = if (sel) Type.BROWN else 0xFFF2E3C2.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            val tcol = if (sel) Type.CREAM else Type.BROWN
            Type.text(c, t.label, r.centerX(), Type.midBaseline(Role.LABEL, r.centerY(), t.label, tcol), Role.LABEL, tcol, 0.5f)
            tabRects.add(r to t)
        }
        UiKit.divider(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 71f))

        btnRects.clear()
        when (tab) {
            Tab.STATUS -> drawStatus(c)
            Tab.QUEST -> drawQuest(c)
            Tab.GROW -> drawGrow(c)
            Tab.PIZZA -> drawPizza(c)
            Tab.BOOK -> drawBook(c)
            Tab.SETTINGS -> drawSettings(c)
        }
    }

    private fun contentTop(): Float = panelR.top + dp(scene, 78f)
    private fun contentBottom(): Float = panelR.bottom - dp(scene, 14f)

    private fun drawStatus(c: Canvas) {
        val g = scene.game
        val s = g.state
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)

        val mins = (s.playSeconds / 60f).toInt()
        val timeStr = if (mins >= 60) "${mins / 60}시간 ${mins % 60}분" else "${mins}분"
        val cam = CameraDefs.LEVELS[(s.cameraLevel - 1).coerceIn(0, CameraDefs.LEVELS.size - 1)]
        val nextCam = if (s.cameraLevel < CameraDefs.LEVELS.size) CameraDefs.LEVELS[s.cameraLevel] else null
        val decorLuck = s.decorLuck()

        val lines = listOf(
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
        // 상태 목록은 읽기 편하게 보통 두께로
        val st = Type.paint(Role.BODY, Type.INK)
        var ty = contentTop() + dp(scene, 14f)
        val x = panelR.left + dp(scene, 18f)
        for (ln in lines) {
            for (sub in ln.split("\n")) {
                c.drawText(sub, x, ty, st)
                ty += Type.lineHeight(Role.BODY)
            }
            val vty = cr.centerY() - (textP.descent() + textP.ascent()) / 2f
            c.drawText(value, cr.right - dp(scene, 8f) - textP.measureText(value), vty, textP)
        }
    }

    private fun drawQuest(c: Canvas) {
        val s = scene.game.state
        val left = panelR.left + dp(scene, 16f)
        val right = panelR.right - dp(scene, 16f)
        var y = contentTop() + dp(scene, 5f)

        // 메인 퀘스트와 시간 제한 없는 서브 의뢰
        val chapter = MainStory.current(s)
        fillP.color = 0xFFFFF7E6.toInt()
        val mainR = RectF(left, y, right, y + dp(scene, 72f))
        c.drawRoundRect(mainR, dp(scene, 9f), dp(scene, 9f), fillP)
        strokeP.color = if (chapter?.isComplete(s) == true) 0xFFF2B63C.toInt() else 0xFFC9A87B.toInt()
        strokeP.strokeWidth = dp(scene, 1.4f)
        c.drawRoundRect(mainR, dp(scene, 9f), dp(scene, 9f), strokeP)
        textP.textSize = dp(scene, 13f)
        textP.color = 0xFF6B4F35.toInt()
        val mainTitle = when {
            s.mainQuestFinished -> "✓ 메인 완결 · 함께 사는 지도"
            !s.mainQuestStarted -> "! 메인 · 보리 박사에게 낡은 수첩 묻기"
            else -> "메인 ${s.mainQuestStage}/${MainStory.CHAPTERS.size - 1} · ${chapter?.title ?: ""}"
        }
        c.drawText(mainTitle, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 18f), textP)
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF796653.toInt()
        val objective = when {
            s.mainQuestFinished -> "Lv.${Progression.MAX_LEVEL}에서 이야기는 멈춤 · 아래 컬렉션과 사진 의뢰는 계속 가능"
            !s.mainQuestStarted -> "메인과 사진 의뢰는 독립적이며 원하는 순서로 진행할 수 있어요."
            else -> chapter?.objective(s) ?: ""
        }
        val objectiveLines = scene.game.hud.wrapText(objective, textP, mainR.width() - dp(scene, 20f)).take(2)
        objectiveLines.forEachIndexed { i, line ->
            c.drawText(line, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 36f + i * 13f), textP)
        }
        val side = s.questBird?.let { Birds.byId[it]?.name }?.let { "서브 사진 의뢰: $it (시간 제한 없음)" }
            ?: "서브 사진 의뢰: 없음 · 어느 지역 보리 박사에게서 언제든 수락"
        c.drawText(side, mainR.left + dp(scene, 10f), mainR.bottom - dp(scene, 7f), textP)
        y = mainR.bottom + dp(scene, 7f)

        // 라이퍼 기반 탐조 이정표. 실제 자격제도가 아님을 UI에서 명시한다.
        val lifers = s.birdCounts.size
        val rank = BirdingRanks.of(lifers)
        val next = BirdingRanks.next(lifers)
        textP.textSize = dp(scene, 12f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("라이퍼 ${lifers}종 · 게임 탐조 등급 「${rank.name}」", left, y + dp(scene, 13f), textP)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        val rankHint = if (next != null) "다음 ${next.name}까지 ${next.min - lifers}종 · 공식 자격이 아닌 수집 이정표" else "400종 이상 · 공식 자격이 아닌 수집 이정표"
        c.drawText(rankHint, right - textP.measureText(rankHint), y + dp(scene, 13f), textP)
        y += dp(scene, 23f)

        val perPage = 5
        val pages = (BirdingCollections.ALL.size + perPage - 1) / perPage
        questPage = questPage.coerceIn(0, pages - 1)
        val sets = BirdingCollections.ALL.drop(questPage * perPage).take(perPage)
        val rowsBottom = contentBottom() - dp(scene, 29f)
        val rowH = (rowsBottom - y) / perPage
        for (set in sets) {
            val done = set.complete(s)
            val r = RectF(left, y, right, y + rowH - dp(scene, 4f))
            fillP.color = if (done) 0xFFE5F4DD.toInt() else 0xFFF7EBD5.toInt()
            c.drawRoundRect(r, dp(scene, 7f), dp(scene, 7f), fillP)
            textP.textSize = dp(scene, 11.5f)
            textP.color = if (done) 0xFF397547.toInt() else 0xFF5D4938.toInt()
            c.drawText("${set.icon} ${if (done) "✓ " else ""}${set.name}  ${set.progress(s)}", r.left + dp(scene, 8f), r.top + dp(scene, 15f), textP)
            textP.textSize = dp(scene, 8.8f)
            textP.color = 0xFF8A7360.toInt()
            val missing = set.species.filterNot { s.hasBirdName(it) }
            val detail = if (missing.isEmpty()) set.note else "남은 새: ${missing.take(4).joinToString("·")}" + if (missing.size > 4) " 외" else ""
            c.drawText(detail, r.left + dp(scene, 8f), r.bottom - dp(scene, 6f), textP)
            y += rowH
        }

        val prevR = RectF(left, contentBottom() - dp(scene, 24f), left + dp(scene, 80f), contentBottom())
        val nextR = RectF(right - dp(scene, 80f), contentBottom() - dp(scene, 24f), right, contentBottom())
        if (questPage > 0) {
            drawButton(c, scene, prevR, "‹ 이전", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 10.5f)
            btnRects.add(Triple(prevR, "quest_prev") { questPage-- })
        }
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val pageText = "도장 깨기 ${questPage + 1}/$pages"
        c.drawText(pageText, panelR.centerX() - textP.measureText(pageText) / 2, contentBottom() - dp(scene, 7f), textP)
        if (questPage < pages - 1) {
            drawButton(c, scene, nextR, "다음 ›", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 10.5f)
            btnRects.add(Triple(nextR, "quest_next") { questPage++ })
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
        val avatar = ps.idle.frame(Dir.S, (g.time / Anim.IDLE.frameTime).toInt())
        val ak = dp(scene, 2.2f)
        c.drawBitmap(avatar, null, RectF(left, ty, left + avatar.width * ak, ty + avatar.height * ak), a.sprPaint)

        c.drawText("화덕에서 토핑을 골라 구울 수 있어요. (최대 ${PIZZA_CAP}개)", x, ty, Type.paint(Role.CAPTION, Type.BROWN))
        ty += dp(scene, 20f)

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
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        var ty = contentTop()

        // 헤더 — 피자 가방 게이지 + 장작 화덕 일러스트
        val headH = dp(scene, 44f)
        val headR = RectF(left, ty, right, ty + headH)
        drawCard(c, scene, headR)
        textP.textSize = dp(scene, 12f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🍕 피자 가방", left + dp(scene, 10f), ty + dp(scene, 16f), textP)
        textP.textSize = dp(scene, 11f)
        textP.color = 0xFFB5651D.toInt()
        val capEff = s.pizzaCapEff()
        val capTxt = "${s.pizzaCount}/$capEff"
        c.drawText(capTxt, right - dp(scene, 58f) - textP.measureText(capTxt), ty + dp(scene, 16f), textP)
        UiKit.bar(c, g, left + dp(scene, 10f), ty + dp(scene, 22f), right - left - dp(scene, 68f), dp(scene, 9f),
            s.pizzaCount / capEff.toFloat(), 0xFFFFD97A.toInt(), 0xFFF2A33C.toInt())
        textP.textSize = dp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("장작 화덕에서 토핑을 골라 구워요", left + dp(scene, 10f), ty + dp(scene, 41f), textP)
        g.illustrations.draw(
            c, "wood_fired_oven.svg",
            RectF(right - dp(scene, 52f), ty + dp(scene, 2f), right - dp(scene, 6f), ty + dp(scene, 42f))
        )
        ty += headH + dp(scene, 6f)

        // 토핑 카드 — 남은 높이에 맞춰 자동 배분
        val gap = dp(scene, 6f)
        val rowH = ((contentBottom() - ty - gap * 2f) / 3f).coerceAtLeast(dp(scene, 50f))
        for (t in Toppings.ALL) {
            // 토핑 아이콘(이모지)
            Type.text(c, t.emoji, x, ty + dp(scene, 24f), Role.EMOJI, Type.INK)

            val tx = x + dp(scene, 32f)
            Type.text(c, "${t.name} 피자", tx, ty + dp(scene, 13f), Role.HEADING, Type.INK)
            val bonusTxt = "배고픔 ${if (t.hungerBonus >= 0) "+" else ""}${t.hungerBonus} · 행운 ${if (t.luckBonus >= 0) "+" else ""}${t.luckBonus}" +
                    " · 난이도 " + "●".repeat(((t.cursorSpeed - 0.95f) * 10f).toInt().coerceIn(1, 3))
            c.drawText(bonusTxt, tx, ty + dp(scene, 27f), Type.paint(Role.CAPTION, Type.SOFT))
            val counts = "걸작×${s.pizzaCountOf(t.id, 2)}  맛있는×${s.pizzaCountOf(t.id, 1)}  탄×${s.pizzaCountOf(t.id, 0)}"
            c.drawText(counts, tx, ty + dp(scene, 41f), Type.paint(Role.CAPTION, Type.SOFT))

            // 먹기 버튼
            val br = RectF(right - dp(scene, 92f), r.centerY() - dp(scene, 14f), right - dp(scene, 10f), r.centerY() + dp(scene, 14f))
            val enabled = s.pizzaCountOf(t.id) > 0 && s.hunger < 100f
            if (enabled) {
                drawButton(c, scene, br, "냠냠 😋", 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 12f)
                btnRects.add(Triple(br, t.name) {
                    val eaten = scene.game.state.eat(t.id)
                    if (eaten != null) {
                        scene.game.toast("냠냠! ${t.emoji} ${t.name} ${eaten.label} (배고픔 +${eaten.hunger + t.hungerBonus}, 행운 +${eaten.luck + t.luckBonus})")
                    }
                })
            } else {
                val noPizza = s.pizzaCountOf(t.id) <= 0
                drawButton(c, scene, br, if (noPizza) "없음" else "든든해", Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 11.5f)
            }
            ty += rowH + gap
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
        // 화면이 낮으면 3줄로 자동 전환 (셀이 찌그러지지 않게)
        val rawAreaH = contentBottom() - (contentTop() + dp(scene, 34f)) - dp(scene, 30f)
        val rowsPerPage = if (rawAreaH < dp(scene, 190f)) 3 else 4
        val pageSize = cols * rowsPerPage
        val totalPages = ((Birds.ALL.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        bookPage = bookPage.coerceIn(0, totalPages - 1)

        val areaLeft = panelR.left + dp(scene, 14f)
        val areaW = panelR.width() - dp(scene, 28f)

        // 헤더 — 도감 완성도 게이지
        val done = s.birdCounts.size
        val total = Birds.ALL.size
        textP.textSize = dp(scene, 11.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("📚 도감 $done/${total}종", areaLeft, contentTop() + dp(scene, 4f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFFB5651D.toInt()
        val pctTxt = "${(done * 100f / total).toInt()}% 완성!"
        c.drawText(pctTxt, areaLeft + areaW - textP.measureText(pctTxt), contentTop() + dp(scene, 4f), textP)
        UiKit.bar(c, g, areaLeft, contentTop() + dp(scene, 9f), areaW, dp(scene, 8f),
            done / total.toFloat(), 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        textP.textSize = dp(scene, 8.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(OfficialBirdChecklist.SOURCE_TITLE, areaLeft, contentTop() + dp(scene, 29f), textP)

        val areaTop = contentTop() + dp(scene, 34f)
        val pagerH = dp(scene, 30f)
        val areaH = contentBottom() - areaTop - pagerH
        val gap = dp(scene, 6f)
        val cw = (areaW - gap * (cols - 1)) / cols
        val chh = (areaH - gap * (rowsPerPage - 1)) / rowsPerPage

        c.drawText(
            "도감 ${s.birdCounts.size}/${Birds.ALL.size}종 — ${OfficialBirdChecklist.SOURCE_TITLE}",
            areaLeft, contentTop() - dp(scene, 2f), Type.paint(Role.CAPTION, Type.BROWN)
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
            val tierColor = UiKit.tierColor(def.tier)
            // 전설 포획 셀은 황금빛으로 빛난다
            if (seen && def.tier == Tier.LEGEND) {
                strokeP.color = Color.argb(130, 242, 182, 60)
                strokeP.strokeWidth = dp(scene, 3.5f)
                c.drawRoundRect(
                    RectF(r.left - dp(scene, 1.5f), r.top - dp(scene, 1.5f), r.right + dp(scene, 1.5f), r.bottom + dp(scene, 1.5f)),
                    dp(scene, 8f), dp(scene, 8f), strokeP
                )
            }
            UiKit.card(c, g, r, 7f, selected = false, borderColor = tierColor, borderWidthDp = if (seen) 2f else 1f)

            val bmp = a.bird(def.id)
            val maxH = chh - dp(scene, 8f)
            val k = (maxH / bmp.height).coerceAtMost(dp(scene, 0.95f))
            val bx = r.left + dp(scene, 4f)
            val by = r.centerY() - bmp.height * k / 2f
            if (seen) {
                // 등급 색상의 은은한 후광
                fillP.color = Color.argb(40, Color.red(tierColor), Color.green(tierColor), Color.blue(tierColor))
                c.drawCircle(bx + bmp.width * k / 2f, r.centerY(), dp(scene, 15f), fillP)
                c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)
            } else {
                fillP.color = Color.argb(95, 150, 140, 130)
                c.drawCircle(bx + bmp.width * k / 2f, r.centerY(), dp(scene, 7f), fillP)
                Type.text(c, "?", bx + bmp.width * k / 2f, Type.midBaseline(Role.CAPTION, r.centerY(), "?", Type.CREAM), Role.CAPTION, Type.CREAM, 0.5f)
            }

            val tx = bx + bmp.width * k + dp(scene, 5f)
            val nameCol = if (seen) Type.INK else Color.argb(175, 74, 55, 40)
            c.drawText(def.name, tx, r.top + dp(scene, 12f), Type.paintAt(10.4f, true, 0.02f, nameCol))
            val subPaint = Type.paint(Role.MICRO, Type.SOFT)
            val baseLine2 = if (seen) {
                val best = s.bestStars[def.id] ?: 1
                "📸${s.birdCounts[def.id]} 최고★$best"
            } else {
                "미촬영 · ${def.tier.label}"
            }
            val line2 = baseLine2 + if (def.active == "night") " 🌙" else ""
            c.drawText(line2, tx, r.top + dp(scene, 24f), subPaint)
            val familyLabel = if (def.familyName.isBlank()) "공식 목록" else def.familyName
            c.drawText(familyLabel, tx, r.top + dp(scene, 35f), Type.paint(Role.MICRO, Color.argb(170, 94, 76, 58)))
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
        // "1/3" 처럼 라틴만 있으면 자동으로 픽셀 폰트
        Type.text(c, "${bookPage + 1}/$totalPages", panelR.centerX(), py + dp(scene, 16f), Role.LABEL, Type.BROWN, 0.5f)
    }

    private fun drawSettings(c: Canvas) {
        val g = scene.game
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        var ty = contentTop() + dp(scene, 4f)

        fun row(icon: String, label: String, sub: String, danger: Boolean, action: () -> Unit) {
            val rh = dp(scene, 46f)
            val r = RectF(left, ty, right, ty + rh)
            UiKit.card(c, g, r, 10f, false,
                if (danger) 0xFFE2574C.toInt() else 0xFFC9A87B.toInt(),
                if (danger) 2f else 1.5f)
            UiKit.iconCircle(c, g, left + dp(scene, 24f), r.centerY(), dp(scene, 14f), icon, 15f,
                if (danger) 0xFFF28B82.toInt() else 0xFFF2B63C.toInt())
            textP.textSize = dp(scene, 13f)
            textP.color = if (danger) 0xFFB03A30.toInt() else 0xFF4A3728.toInt()
            c.drawText(label, left + dp(scene, 46f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, left + dp(scene, 46f), r.centerY() + dp(scene, 13f), textP)
            textP.textSize = dp(scene, 15f)
            textP.color = if (danger) 0xFFE2574C.toInt() else 0xFFB5651D.toInt()
            c.drawText("›", right - dp(scene, 20f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            btnRects.add(Triple(r, label, action))
            ty += rh + dp(scene, 8f)
        }

        row(if (g.state.musicOn) "🎵" else "🔇", "음악: " + if (g.state.musicOn) "켜짐" else "꺼짐", "배경 음악을 켜고 꺼요", false) {
            g.state.musicOn = !g.state.musicOn
            g.audio.setMusic(g.state.musicOn)
            SaveManager.save(g.context, g.state)
        }
        row(if (g.state.sfxOn) "🔊" else "🔈", "효과음: " + if (g.state.sfxOn) "켜짐" else "꺼짐", "새 소리와 버튼음을 켜고 꺼요", false) {
            g.state.sfxOn = !g.state.sfxOn
            g.audio.setSfx(g.state.sfxOn)
            SaveManager.save(g.context, g.state)
        }
        row("💾", "저장하기", "지금까지의 여행을 안전하게 보관해요", false) {
            SaveManager.save(g.context, g.state)
            g.toast("저장 완료! ✨")
        }
        row("🏠", "타이틀로 가기", "저장 후 타이틀 화면으로 돌아가요", false) {
            SaveManager.save(g.context, g.state)
            finished = true
            g.scene = TitleScene(g)
        }
        if (resetArmed) {
            row("⚠️", "정말 처음부터 시작할까요?", "되돌릴 수 없어요! 다시 누르면 초기화돼요", true) {
                SaveManager.clear(g.context)
                g.state.reset("seoul")
                g.state.started = false
                finished = true
                g.scene = TitleScene(g)
            }
        } else {
            row("🗑", "처음부터 다시 시작", "저장 데이터를 모두 지우고 새로 시작해요", false) {
                resetArmed = true
            }
        }

        val foot = Type.paint(Role.CAPTION, Type.SOFT)
        ty += dp(scene, 6f)
        Type.text(c, "Pizza and Bird v0.2.1-beta01", x, ty, Role.CAPTION, Type.SOFT)
        ty += dp(scene, 15f)
        c.drawText("완전 오프라인 힐링 게임 · 저장은 자동으로 돼요", x, ty, foot)
        ty += dp(scene, 15f)
        c.drawText("낮과 밤이 흐르고, 밤에는 올빼미가 나와요!", x, ty, foot)
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
        val ph = minOf(h * 0.92f, dp(scene, 470f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))
        drawCloseX(c, scene, closeRect)

        Type.text(c, "🧺 장식 코너", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 32f), Role.TITLE, Type.INK)
        c.drawText("집에 놓으면 행운이 오르는 소품들이에요 · 보유 ${won(s.money)}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 48f), Type.paint(Role.CAPTION, Type.SOFT))

        // 상품 행 — 패널 높이에 맞춰 자동 배분
        buyRects = ArrayList()
        val gap = dp(scene, 5f)
        val top0 = panelR.top + dp(scene, 62f)
        val avail = panelR.bottom - dp(scene, 10f) - top0
        val rowH = ((avail - gap * (Decors.ALL.size - 1)) / Decors.ALL.size).coerceIn(dp(scene, 36f), dp(scene, 56f))
        var ty = top0
        for (d in Decors.ALL) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH)
            drawCard(c, scene, r)

            Type.textCentered(c, d.emoji, r.left + dp(scene, 10f), r.centerY(), Role.EMOJI, Type.INK)
            Type.text(c, d.name, r.left + dp(scene, 44f), r.top + dp(scene, 19f), Role.HEADING, Type.INK)
            val cap = Type.paint(Role.CAPTION, Type.SOFT)
            c.drawText(d.desc, r.left + dp(scene, 44f), r.top + dp(scene, 34f), cap)
            c.drawText("행운 +${d.luck}", r.left + dp(scene, 44f), r.top + dp(scene, 47f), cap)

            val owned = d.id in s.decorOwned
            val br = RectF(r.right - dp(scene, 82f), r.centerY() - dp(scene, 12f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 12f))
            if (owned) {
                drawButton(c, scene, br, "보유중 ✓", Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 10.5f)
            } else {
                drawButton(c, scene, br, won(d.cost), 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 10.5f)
                buyRects.add(br to d.id)
            }
            ty += rowH + gap
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

        val rowCount = s.decorOwned.size + 1
        val pw = minOf(w * 0.8f, dp(scene, 360f))
        val ph = minOf(dp(scene, 96f) + dp(scene, 58f) * rowCount, h * 0.9f)
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))
        drawCloseX(c, scene, closeRect)

        Type.text(c, "장식 칸 ${slot + 1}에 뭘 놓을까요?", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 32f), Role.TITLE, Type.INK)

        pickRects = ArrayList()
        var ty = panelR.top + dp(scene, 46f)

        fun row(id: Int, emoji: String, name: String, sub: String) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + dp(scene, 50f))
            fillP.color = 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)
            Type.textCentered(c, emoji, r.left + dp(scene, 10f), r.centerY(), Role.EMOJI, Type.INK)
            Type.text(c, name, r.left + dp(scene, 44f), r.top + dp(scene, 20f), Role.HEADING, Type.INK)
            c.drawText(sub, r.left + dp(scene, 44f), r.top + dp(scene, 36f), Type.paint(Role.CAPTION, Type.SOFT))
            pickRects.add(r to id)
            ty += rowH + gap
        }

        for (did in s.decorOwned) {
            val d = Decors.of(did) ?: continue
            val placed = s.decorSlots.contains(did)
            row(did, d.emoji, d.name, "행운 +${d.luck}" + (if (placed) " · 이미 다른 칸에" else ""))
        }
        row(-1, "🫙", "빈 칸으로 두기", "장식을 치웁니다", 0xFFD9CFC0.toInt())
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
        val ph = minOf(h * 0.92f, dp(scene, 360f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "✕", 11f)

        Type.text(c, "🏠 우리 집 인테리어", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 32f), Role.TITLE, Type.INK)
        c.drawText("스타일을 구매하면 이사 후에도 계속 사용할 수 있어요 · 보유 ${won(s.money)}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 49f), Type.paint(Role.CAPTION, Type.SOFT))
        drawCloseX(c, scene, closeRect)

        styleRects.clear()
        val gap = dp(scene, 6f)
        val top0 = panelR.top + dp(scene, 62f)
        val avail = panelR.bottom - dp(scene, 10f) - top0
        val rowH = ((avail - gap * (HouseStyles.ALL.size - 1)) / HouseStyles.ALL.size)
            .coerceIn(dp(scene, 48f), dp(scene, 60f))
        var y = top0
        for (style in HouseStyles.ALL) {
            val r = RectF(panelR.left + dp(scene, 12f), y, panelR.right - dp(scene, 12f), y + rowH)
            val owned = style.id in s.ownedHouseStyles
            val applied = style.id == s.houseStyleId
            UiKit.card(c, g, r, 10f, applied, if (applied) style.accentTint else 0xFFC9A87B.toInt(), if (applied) 2.5f else 1.5f)

            Type.textCentered(c, style.emoji, r.left + dp(scene, 10f), r.centerY(), Role.EMOJI, Type.INK)
            Type.text(c, style.name, r.left + dp(scene, 44f), r.top + dp(scene, 20f), Role.HEADING, Type.INK)
            c.drawText(style.desc, r.left + dp(scene, 44f), r.top + dp(scene, 37f), Type.paint(Role.CAPTION, Type.SOFT))

            val br = RectF(r.right - dp(scene, 90f), r.centerY() - dp(scene, 14f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 14f))
            if (applied) {
                drawButton(c, scene, br, "적용중 ✓", 0xFFD7E3C3.toInt(), 0xFF4A3728.toInt(), 10.5f)
            } else if (owned) {
                drawButton(c, scene, br, "적용하기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 11f)
            } else {
                drawButton(c, scene, br, won(style.price), 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 10f)
            }
            styleRects.add(r to style.id)
            y += rowH + gap
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
        g.illustrations.draw(
            c, "wood_fired_oven.svg",
            RectF(r.right - dp(scene, 52f), r.top + dp(scene, 3f), r.right - dp(scene, 8f), r.top + dp(scene, 45f))
        )

        when (step) {
            0 -> {
                val title = "🍕 토핑을 골라요"
                Type.text(c, title, r.centerX(), r.top + dp(scene, 28f), Role.TITLE, Type.INK, 0.5f)

                toppingRects.clear()
                val cardW = (r.width() - dp(scene, 16f) * 4) / 3f
                for ((i, tp) in Toppings.ALL.withIndex()) {
                    val cr = RectF(
                        r.left + dp(scene, 16f) + i * (cardW + dp(scene, 16f)), r.top + dp(scene, 44f),
                        r.left + dp(scene, 16f) + i * (cardW + dp(scene, 16f)) + cardW, r.top + dp(scene, 156f)
                    )
                    fillP.color = 0xFFFDF6E8.toInt()
                    c.drawRoundRect(cr, dp(scene, 10f), dp(scene, 10f), fillP)
                    strokeP.color = 0xFFB5651D.toInt()
                    strokeP.strokeWidth = dp(scene, 2f)
                    c.drawRoundRect(cr, dp(scene, 10f), dp(scene, 10f), strokeP)

                    Type.text(c, tp.emoji, cr.centerX(), cr.top + dp(scene, 36f), Role.EMOJI, Type.INK, 0.5f)
                    Type.text(c, tp.name, cr.centerX(), cr.top + dp(scene, 58f), Role.HEADING, Type.INK, 0.5f)
                    val b1 = "배고픔 ${if (tp.hungerBonus >= 0) "+" else ""}${tp.hungerBonus} · 행운 ${if (tp.luckBonus >= 0) "+" else ""}${tp.luckBonus}"
                    val b2 = "구우기 난이도 " + "●".repeat(((tp.cursorSpeed - 0.95f) * 10f).toInt().coerceIn(1, 3)) + "○".repeat(3 - ((tp.cursorSpeed - 0.95f) * 10f).toInt().coerceIn(1, 3))
                    Type.text(c, b1, cr.centerX(), cr.top + dp(scene, 76f), Role.CAPTION, Type.SOFT, 0.5f)
                    Type.text(c, b2, cr.centerX(), cr.top + dp(scene, 92f), Role.CAPTION, Type.SOFT, 0.5f)
                    val pz = a.pizzaIcon
                    val psz = dp(scene, 22f)
                    c.drawBitmap(pz, null, RectF(cr.centerX() - psz / 2, cr.top + dp(scene, 99f), cr.centerX() + psz / 2, cr.top + dp(scene, 99f) + psz), a.sprPaint)
                    toppingRects.add(cr to tp.id)
                }

                cancelRect = RectF(r.centerX() - dp(scene, 60f), r.bottom - dp(scene, 40f), r.centerX() + dp(scene, 60f), r.bottom - dp(scene, 12f))
                drawButton(c, scene, cancelRect, "그만두기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12f)
            }
            1 -> {
                val tp = Toppings.of(topping)
                Type.text(c, "${tp.emoji} ${tp.name} 피자 굽기", r.centerX(), r.top + dp(scene, 28f), Role.TITLE, Type.INK, 0.5f)

                // 게이지 — 글로스 + 걸작존 글로우 + 프리미엄 커서
                val gx = r.left + dp(scene, 26f)
                val gy = r.centerY() - dp(scene, 6f)
                val gw = r.width() - dp(scene, 52f)
                val gh = dp(scene, 28f)
                val half = tp.perfectW / 2f
                // 바 섀도우
                fillP.color = Color.argb(60, 50, 32, 14)
                c.drawRoundRect(RectF(gx, gy + dp(scene, 2.5f), gx + gw, gy + gh + dp(scene, 2.5f)), dp(scene, 8f), dp(scene, 8f), fillP)
                // 걸작존 글로우
                fillP.color = Color.argb(90, 111, 186, 107)
                c.drawRoundRect(
                    RectF(gx + gw * (0.5f - half) - dp(scene, 3f), gy - dp(scene, 4f), gx + gw * (0.5f + half) + dp(scene, 3f), gy + gh + dp(scene, 4f)),
                    dp(scene, 8f), dp(scene, 8f), fillP
                )
                val zones = listOf(
                    Triple(0f, 0.5f - half - 0.26f, 0xFFE2574C.toInt()),
                    Triple(0.5f - half - 0.26f, 0.5f - half, 0xFFF2D06B.toInt()),
                    Triple(0.5f - half, 0.5f + half, 0xFF6FBA6B.toInt()),
                    Triple(0.5f + half, 0.5f + half + 0.26f, 0xFFF2D06B.toInt()),
                    Triple(0.5f + half + 0.26f, 1f, 0xFFE2574C.toInt())
                )
                for ((z0, z1, col) in zones) {
                    val zr = RectF(gx + gw * z0, gy, gx + gw * z1, gy + gh)
                    fillP.color = col
                    c.drawRoundRect(zr, dp(scene, 4f), dp(scene, 4f), fillP)
                    // 글로스
                    fillP.color = Color.argb(70, 255, 255, 255)
                    c.drawRoundRect(RectF(zr.left + dp(scene, 1f), zr.top + dp(scene, 1.5f), zr.right - dp(scene, 1f), zr.top + gh * 0.42f), dp(scene, 3f), dp(scene, 3f), fillP)
                }
                // 존 경계선
                strokeP.color = Color.argb(120, 74, 55, 40)
                strokeP.strokeWidth = dp(scene, 1f)
                for ((z0, z1, _) in zones) {
                    if (z0 > 0.01f) c.drawLine(gx + gw * z0, gy + dp(scene, 3f), gx + gw * z0, gy + gh - dp(scene, 3f), strokeP)
                    if (z1 < 0.99f) c.drawLine(gx + gw * z1, gy + dp(scene, 3f), gx + gw * z1, gy + gh - dp(scene, 3f), strokeP)
                }
                strokeP.color = 0xFF6B4F35.toInt()
                strokeP.strokeWidth = dp(scene, 2.2f)
                c.drawRoundRect(RectF(gx, gy, gx + gw, gy + gh), dp(scene, 8f), dp(scene, 8f), strokeP)

                // 커서 — 글로우 + 흰 코어 + 삼각 포인터
                val pos = cursorPos()
                val cx = gx + gw * pos
                fillP.color = Color.argb(80, 255, 255, 255)
                c.drawRoundRect(RectF(cx - dp(scene, 5f), gy - dp(scene, 10f), cx + dp(scene, 5f), gy + gh + dp(scene, 10f)), dp(scene, 4f), dp(scene, 4f), fillP)
                strokeP.color = 0xFF2E2118.toInt()
                strokeP.strokeWidth = dp(scene, 3.5f)
                c.drawLine(cx, gy - dp(scene, 8f), cx, gy + gh + dp(scene, 8f), strokeP)
                strokeP.color = 0xFFFFF8E8.toInt()
                strokeP.strokeWidth = dp(scene, 1.4f)
                c.drawLine(cx, gy - dp(scene, 8f), cx, gy + gh + dp(scene, 8f), strokeP)
                // 삼각 포인터 (위/아래)
                fillP.color = 0xFF2E2118.toInt()
                val triS = dp(scene, 5f)
                val triTop = Path().apply {
                    moveTo(cx, gy - dp(scene, 8f) - triS)
                    lineTo(cx - triS * 0.8f, gy - dp(scene, 8f) - triS * 2.2f)
                    lineTo(cx + triS * 0.8f, gy - dp(scene, 8f) - triS * 2.2f)
                    close()
                }
                c.drawPath(triTop, fillP)

                // 라벨
                Type.text(c, "살짝 탐", gx, gy + gh + dp(scene, 18f), Role.CAPTION, Type.INK)
                Type.text(c, "걸작!", gx + gw / 2f, gy - dp(scene, 14f), Role.CAPTION, Type.LEAF, 0.5f)
                Type.text(c, "살짝 탐", gx + gw, gy + gh + dp(scene, 18f), Role.CAPTION, Type.INK, 1f)

                val hint = "초록 칸에서 멈춰보세요! (아무 곳이나 탭)"
                Type.text(c, hint, r.centerX(), r.bottom - dp(scene, 18f), Role.BODY, Type.SOFT, 0.5f)
            }
            else -> {
                val q = PizzaQ.of(resultQ)
                val tp = Toppings.of(topping)
                val perfect = resultQ == 2
                val title = if (lostPizza) "${tp.emoji} ${q.label}… 그런데 두 손이 가득!" else "${tp.emoji} ${q.label} 완성!"
                val tcol = if (resultQ == 2) Type.CARAMEL else Type.INK
                Type.sticker(c, title, r.centerX(), r.centerY() - dp(scene, 30f), Role.TITLE, tcol)

                val k = dp(scene, 3.4f)
                val bmp = a.pizzaIcon
                val bw = bmp.width * k
                val bh = bmp.height * k
                val bx = r.centerX() - bw / 2f
                val by = r.centerY() - bh / 2f + dp(scene, 22f)
                val glowR = maxOf(bw, bh) * 0.75f
                val pulse = if (perfect) (1f + 0.08f * sin(g.time * 5f)) else 1f
                fillP.color = Color.argb(if (perfect) 90 else 45, 242, 182, 60)
                c.drawCircle(r.centerX(), by + bh / 2f, glowR * pulse, fillP)
                fillP.color = Color.argb(if (perfect) 45 else 22, 242, 182, 60)
                c.drawCircle(r.centerX(), by + bh / 2f, glowR * 1.35f * pulse, fillP)
                c.drawBitmap(bmp, null, RectF(bx, by, bx + bw, by + bh), a.sprPaint)
                // 걸작 반짝이
                if (perfect) {
                    fillP.color = Color.argb(200, 255, 220, 130)
                    for (i in 0 until 6) {
                        val ang = g.time * 1.5f + i * 1.047f
                        val sx = r.centerX() + kotlin.math.cos(ang) * glowR * 1.5f
                        val syy = by + bh / 2f + sin(ang) * glowR * 0.9f
                        c.drawCircle(sx, syy, dp(scene, 2f), fillP)
                    }
                }

                val info = if (lostPizza) "피자 가방이 가득해서 못 챙겼어요…"
                else "먹으면 배고픔 +${q.hunger + tp.hungerBonus} · 행운 +${q.luck + tp.luckBonus}"
                Type.text(c, info, r.centerX(), r.bottom - dp(scene, 20f), Role.BODY, Type.SOFT, 0.5f)
                Type.text(c, "탭해서 닫기", r.centerX(), r.bottom - dp(scene, 6f), Role.CAPTION, Type.SOFT, 0.5f)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 사진 결과 (폴라로이드 인화)
// ---------------------------------------------------------------------------

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val v = x - 1f
    return 1f + c3 * v * v * v + c1 * v * v
}

class PhotoResultOverlay(
    scene: Scene,
    private val def: BirdDef,
    private val stars: Int,
    private val isNew: Boolean,
    private val count: Int,
    private val questLine: String?,
    private val distTiles: Float = 0f,
    private val timeTxt: String = "",
    private val cameraTxt: String = "",
    private val night: Boolean = false,
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
        // 열린 직후 0.3초는 플래시/카드 등장 연출 보호 + 실수 방지 (입력 소비만)
        if (t < 0.3f) {
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

        // 셔터 플래시 잔상
        if (t < 0.3f) {
            fillP.color = Color.argb((215 * (1f - t / 0.3f)).toInt().coerceIn(0, 255), 255, 253, 246)
            c.drawRect(0f, 0f, w, h, fillP)
        }

        dim(c, scene, 172)

        // 이름 + 첫 발견 리본 — 이름은 스티커 처리(화면이 잠깐 멈추는 순간이라 화려해도 괜찮다)
        Type.sticker(c, def.name, r.centerX(), r.top + dp(scene, 28f), Role.TITLE, Type.INK)
        if (isNew) {
            val ribbon = "NEW · 첫 발견"
            val rw = Type.width(Role.CAPTION, ribbon, Type.PAPER) + dp(scene, 18f)
            val rr = RectF(r.right - rw - dp(scene, 10f), r.top + dp(scene, 9f), r.right - dp(scene, 10f), r.top + dp(scene, 29f))
            fillP.color = Type.BERRY
            c.drawRoundRect(rr, dp(scene, 10f), dp(scene, 10f), fillP)
            Type.text(c, ribbon, rr.centerX(), Type.midBaseline(Role.CAPTION, rr.centerY(), ribbon, Type.PAPER), Role.CAPTION, Type.PAPER, 0.5f)
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

        val pop = easeOutBack((t / 0.36f).coerceIn(0f, 1f))
        val scale = 0.9f + 0.1f * pop

        c.save()
        c.rotate(-2.1f, cx, cy)
        c.scale(scale, scale, cx, cy)

        val card = RectF(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, cy + cardH / 2f)

        // 종이 그림자
        fillP.color = Color.argb(58, 8, 6, 14)
        c.drawRoundRect(
            RectF(card.left + dp(scene, 5f), card.top + dp(scene, 8f), card.right + dp(scene, 7f), card.bottom + dp(scene, 10f)),
            dp(scene, 5f), dp(scene, 5f), fillP
        )
        fillP.color = Color.argb(38, 8, 6, 14)
        c.drawRoundRect(
            RectF(card.left + dp(scene, 10f), card.top + dp(scene, 15f), card.right + dp(scene, 13f), card.bottom + dp(scene, 19f)),
            dp(scene, 7f), dp(scene, 7f), fillP
        )

        // 폴라로이드 흰 종이
        fillP.color = 0xFFFDFBF3.toInt()
        c.drawRoundRect(card, dp(scene, 3f), dp(scene, 3f), fillP)

        // 사진
        val photoW = cardW - inset * 2f
        val photoH = minOf(photoW * 0.74f, cardH - inset - dp(scene, 96f))
        val photo = RectF(card.left + inset, card.top + inset, card.left + inset + photoW, card.top + inset + photoH)
        drawPhoto(c, photo)
        strokeP.color = Color.argb(46, 40, 32, 24)
        strokeP.strokeWidth = dp(scene, 1f)
        c.drawRoundRect(photo, 0f, 0f, strokeP)

        // 캡션: 이름
        val capTop = photo.bottom + dp(scene, 12f)
        textP.textSize = dp(scene, 17f)
        textP.color = 0xFF3B2F24.toInt()
        val nm = def.name
        c.drawText(nm, card.centerX() - textP.measureText(nm) / 2, capTop + dp(scene, 12f), textP)

        // 캡션: 등급 · 영문명
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF8A7360.toInt()
        val tierColor = when (def.tier) {
            Tier.COMMON -> 0xFF777F82.toInt()
            Tier.UNCOMMON -> 0xFF5E934F.toInt()
            Tier.RARE -> 0xFF3F6FB0.toInt()
            Tier.LEGEND -> 0xFFB65342.toInt()
        }
        val tierLabel = "${def.tier.label} · ${def.activeLabel}"
        val tierW = Type.width(Role.CAPTION, tierLabel, Type.PAPER) + dp(scene, 16f)
        val tierR = RectF(photoR.left + dp(scene, 7f), photoR.top + dp(scene, 7f), photoR.left + dp(scene, 7f) + tierW, photoR.top + dp(scene, 25f))
        fillP.color = Color.argb(220, Color.red(tierColor), Color.green(tierColor), Color.blue(tierColor))
        c.drawRoundRect(tierR, dp(scene, 9f), dp(scene, 9f), fillP)
        Type.text(c, tierLabel, tierR.centerX(), Type.midBaseline(Role.CAPTION, tierR.centerY(), tierLabel, Type.PAPER), Role.CAPTION, Type.PAPER, 0.5f)

        // 별점은 라틴 기호만이라 픽셀 폰트로 그려진다
        val y1 = r.top + dp(scene, 174f)
        Type.text(c, "★".repeat(stars) + "☆".repeat(3 - stars), r.centerX(), y1, Role.DISPLAY, Type.CARAMEL, 0.5f)

        val best = g.state.bestStars[def.id] ?: stars
        val cnt = "촬영 ${count}회 · 최고 ★$best" + if (def.englishName.isNotBlank()) " · ${def.englishName}" else ""
        Type.text(c, cnt, r.centerX(), y1 + dp(scene, 20f), Role.CAPTION, Type.SOFT, 0.5f)

        c.restore()

        // 의뢰 보수 & 경험치
        var badgeY = cy + cardH / 2f + dp(scene, 24f)
        if (questLine != null) {
            Type.text(c, questLine, r.centerX(), y1 + dp(scene, 38f), Role.CAPTION, Type.SKY, 0.5f)
        }

        Type.text(c, "화면을 탭해 탐조를 계속해요", r.centerX(), r.bottom - dp(scene, 12f), Role.CAPTION, Type.SOFT, 0.5f)
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
        val t1 = "🗺 대한민국 탐조 지도"
        Type.text(c, t1, dp(scene, 14f), dp(scene, 30f), Role.TITLE, Type.CREAM)
        val t2 = "방문 ${s.visited.size}/${Regions.ALL.size} · 두 손가락으로 확대 · 끌어서 이동 · 지역을 누르면 정보"
        Type.text(c, t2, dp(scene, 170f) + Type.width(Role.TITLE, t1, Type.CREAM), dp(scene, 29f), Role.CAPTION, Type.CREAM)

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
                Type.text(c, "🏠", x, y - r - dp(scene, 3f), Role.LABEL, Type.INK, 0.5f)
            }

            if (showAllNames || isCurrent || isHome || reg.kind == RegionKind.TOWN) {
                val nm = if (visited) reg.name else "? ${reg.name}"
                val role = if (isCurrent) Role.CAPTION else Role.MICRO
                val col = if (isCurrent) Type.BERRY else if (visited) Type.INK else Color.argb(170, 58, 42, 36)
                val tw = Type.width(role, nm, col)
                fillP.color = Color.argb(170, 255, 252, 240)
                c.drawRoundRect(
                    RectF(x - tw / 2 - dp(scene, 4f), y + r + dp(scene, 1f), x + tw / 2 + dp(scene, 4f), y + r + dp(scene, 14f)),
                    dp(scene, 3f), dp(scene, 3f), fillP
                )
                Type.text(c, nm, x, y + r + dp(scene, 11f), role, col, 0.5f)
            }
        }
    }

    private fun drawButtons(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val bs = dp(scene, 34f)
        closeR = RectF(w - dp(scene, 16f) - dp(scene, 82f), dp(scene, 10f), w - dp(scene, 16f), dp(scene, 10f) + dp(scene, 26f))
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
        val lw = dp(scene, 96f)
        val lh = dp(scene, 14f) * RegionKind.values().size + dp(scene, 10f)
        c.drawRoundRect(RectF(x0 - dp(scene, 6f), y - dp(scene, 12f), x0 + lw, y - dp(scene, 12f) + lh), dp(scene, 6f), dp(scene, 6f), fillP)
        val leg = Type.paint(Role.MICRO, Type.INK)
        for (k in RegionKind.values()) {
            fillP.color = k.color
            c.drawCircle(x0 + dp(scene, 2f), y - dp(scene, 3f), dp(scene, 4f), fillP)
            c.drawText(k.label, x0 + dp(scene, 10f), y, leg)
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
        fillP.color = Color.argb(60, 20, 14, 10)
        c.drawRoundRect(RectF(r.left, r.top + dp(scene, 3f), r.right, r.bottom + dp(scene, 4f)), dp(scene, 12f), dp(scene, 12f), fillP)
        UiKit.card(c, g, r, 12f, false, reg.kind.color, 2.5f)
        // 상단 포인트 스트립
        fillP.color = reg.kind.color
        c.drawRoundRect(
            RectF(r.left + dp(scene, 12f), r.top + dp(scene, 6f), r.left + dp(scene, 64f), r.top + dp(scene, 10f)),
            dp(scene, 2f), dp(scene, 2f), fillP
        )

        val x = r.left + dp(scene, 12f)
        var y = r.top + dp(scene, 22f)
        val title = "${reg.emoji} ${reg.name}"
        c.drawText(title, x, y, Type.paint(Role.HEADING, Type.INK))
        val tw = Type.width(Role.HEADING, title, Type.INK)
        c.drawText(reg.english, x + tw + dp(scene, 6f), y, Type.paint(Role.MICRO, Type.SOFT))
        val badge = when {
            reg.id == s.region -> "현재 위치"
            reg.id == s.homeRegion -> "우리 집"
            reg.id in s.visited -> "방문함"
            else -> "미방문"
        }
        Type.text(c, badge, r.right - dp(scene, 12f), y, Role.CAPTION,
            if (reg.id == s.region) Type.BERRY else Type.BROWN, 1f)

        y += dp(scene, 16f)
        c.drawText("${reg.kind.label} · ${reg.habitatLabels}", x, y, Type.paint(Role.CAPTION, Type.LEAF))

        y += dp(scene, 14f)
        c.drawText("🐦 " + Regions.signatureBirds(reg).joinToString(", ") { it.name }, x, y, Type.paint(Role.CAPTION, Type.BROWN))

        y += dp(scene, 14f)
        c.drawText("📅 추천 시기: ${reg.season}", x, y, Type.paint(Role.CAPTION, Type.SKY))

        // 설명은 보통 두께로 (가독성)
        val tip = Type.paint(Role.CAPTION, Type.SOFT)
        y += dp(scene, 14f)
        for (ln in g.hud.wrapText(if (reg.tip.isNotEmpty()) reg.tip else reg.desc, tip, r.width() - dp(scene, 24f)).take(2)) {
            c.drawText(ln, x, y, tip)
            y += dp(scene, 12f)
        }

        y += dp(scene, 2f)
        val exits = Regions.exits(reg.id).values.mapNotNull { Regions.byId[it]?.name }
        val ex = "🚲 연결: " + if (exits.isEmpty()) "-" else exits.joinToString(", ")
        for (ln in g.hud.wrapText(ex, tip, r.width() - dp(scene, 24f)).take(2)) {
            c.drawText(ln, x, y, tip)
            y += dp(scene, 12f)
        }
    }
}

