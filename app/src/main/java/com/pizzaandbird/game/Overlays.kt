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

    /**
     * true 면 이 오버레이가 화면 대부분을 덮어, 뒤 월드가 두드러지지 않는다
     * (가방·지도·상점처럼 큰 패널이 뜨는 화면).
     *
     * true 인 동안 Game.render() 는 마지막 월드 비트맵을 재사용한다.
     * 월드 업데이트도 오버레이가 열려 있으면 멈추므로, 메뉴 입력 도중 수천 번의
     * drawBitmap 이 간헐적으로 끼어들지 않는다. 닫힌 첫 프레임에는 다시 그린다.
     * (부분창 대화상자처럼 뒤 화면이 그대로 보이는 오버레이는 false 로 둔다)
     */
    open val coversWorld: Boolean get() = false

    /** 지도·백업처럼 드래그를 직접 처리하는 창만 raw 터치를 받는다. */
    open val usesRawTouch: Boolean get() = false

    open fun update(dt: Float) {}
    open fun handleInput(input: Input) {}
    open fun draw(c: Canvas) {}

    /** 등장 애니메이션 진행도 0..1 */
    fun enterT(): Float = UiKit.enter(bornAt)
    /** 등장 시 위로 올라오는 오프셋(px) */
    fun enterShift(): Float = UiKit.enterShift(scene.game, bornAt)
}

// 공통 페인트 (서브 UI 직접 그리기용)
private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
// 화면마다 dp 를 직접 지정하는 곳에서 쓰는 공용 텍스트 페인트.
// 글꼴은 Type 이 관리한다(assets 에 둥근 한글 폰트를 넣으면 그대로 적용).
private val textP = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply { letterSpacing = 0.01f }

private fun dp(scene: Scene, v: Float): Float = v * scene.game.density
private fun textDp(scene: Scene, v: Float): Float = TypeScale.px(dp(scene, v))

/** 프리미엄 패널 그리기 (섀도우 + 그라데이션 + 이중 테두리) */
private fun panel(c: Canvas, r: RectF, scene: Scene) {
    UiKit.panel(c, scene.game, r)
}

/** 프리미엄 딤 (등장 페이드 포함) */
private fun dim(c: Canvas, scene: Scene, alpha: Int = 130) {
    UiKit.dim(c, scene.game, alpha, scene.overlay?.bornAt ?: 0L)
}

/** 프리미엄 버튼 그리기 (베이스 색상에서 그라데이션을 자동 파생) */
private fun drawButton(
    c: Canvas, scene: Scene, r: RectF, label: String,
    fillCol: Int, textCol: Int, textSize: Float
) {
    UiKit.button(c, scene.game, r, label, fillCol, textCol, textSize)
}

/** 프리미엄 카드 행 */
private fun drawCard(
    c: Canvas, scene: Scene, r: RectF,
    selected: Boolean = false, borderColor: Int = 0xFFC9A87B.toInt(), borderW: Float = 1.5f
) {
    UiKit.card(c, scene.game, r, 10f, selected, borderColor, borderW)
}

/** 텍스트가 maxW 안에 들어가도록 글자 크기를 줄여 그린다 (최소 minSize) */
private fun drawFitText(c: Canvas, scene: Scene, text: String, x: Float, y: Float, maxW: Float, size: Float, minSize: Float = 7.5f) {
    var sz = size
    textP.textSize = textDp(scene, sz)
    while (UiKit.iconTextWidth(text, textP) > maxW && sz > minSize) {
        sz -= 0.5f
        textP.textSize = textDp(scene, sz)
    }
    UiKit.drawIconText(c, scene.game, text, x, y, textP)
}

/** 조리기구 일러스트 — 화덕은 SVG, 가정용 오븐은 타일 아트(RANGE, 불빛 깜빡임) */
private fun drawStationArt(c: Canvas, scene: Scene, kind: PizzaKind, bounds: RectF) {
    val g = scene.game
    if (kind == PizzaKind.OVEN) {
        g.illustrations.draw(c, "wood_fired_oven.svg", bounds)
    } else {
        val a = g.assets
        val frames = a.tiles[T.RANGE.ordinal]
        val bmp = frames[((g.time * 3.4f).toInt() % frames.size + frames.size) % frames.size]
        val sz = minOf(bounds.width(), bounds.height())
        val dst = RectF(bounds.centerX() - sz / 2f, bounds.centerY() - sz / 2f, bounds.centerX() + sz / 2f, bounds.centerY() + sz / 2f)
        c.drawBitmap(bmp, null, dst, a.sprPaint)
    }
}

/** 피자 아이콘을 폭 w에 맞춰 (비율 유지) 그린다 */
private fun drawPizzaArt(c: Canvas, scene: Scene, pizzaId: Int, cx: Float, cy: Float, w: Float) {
    val a = scene.game.assets
    val bmp = a.pizzaArt(pizzaId)
    val h = w * bmp.height / bmp.width
    c.drawBitmap(bmp, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), a.sprPaint)
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
    private var drawnShift = 0f

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            // 버튼은 등장 애니메이션 동안 아래에 보인다. 마지막 렌더의 이동량으로 판정한다.
            val y = tap.y - drawnShift
            for (i in choiceRects.indices) {
                if (choiceRects[i].contains(tap.x, y)) {
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

        // 본문 줄 수에 딱 맞춰 패널 높이를 잡는다 — 짧은 대사에 군더더기 공간이
        // 남지 않고, 긴 대사는 줄 수만큼 늘어나 선택지와 겹치지도 않는다.
        //   상단 여백(제목 칩 + 숨 쉴 틈) 32 + 본문 블록 + 본문·버튼 사이 12
        //   + 선택지 버튼 30 + 하단 여백 12
        // 본문 블록은 제목 칩과 버튼 사이 공간의 세로 가운데에 둔다 (가로는 좌측 정렬).
        // 한 줄짜리 짧은 대사는 1.6줄 분량의 여유를 줘서 위쪽에 달라붙지 않게 한다.
        val bodyPaint = Type.paint(Role.BODY, Type.INK)
        val maxW = w - margin * 2f - dp(scene, 28f)
        val lineH = Type.lineHeight(Role.BODY)
        val wrapped = Type.wrap(body, bodyPaint, maxW)
        val chromeH = dp(scene, 32f + 12f + 30f + 12f)
        // 아무리 길어도 화면의 78%까지만 — 넘치는 줄은 … 로 마무리
        val maxLines = (((h * 0.78f - margin - chromeH) / lineH).toInt() + 1).coerceAtLeast(1)
        var lines = if (wrapped.size > maxLines) wrapped.take(maxLines) else wrapped
        if (wrapped.size > lines.size && lines.isNotEmpty()) {
            var last = lines.last()
            while (last.isNotEmpty() && bodyPaint.measureText("$last…") > maxW) last = last.dropLast(1)
            lines = lines.dropLast(1) + listOf("$last…")
        }
        val zoneH = maxOf(lineH * lines.size, lineH * 1.6f)
        val panelH = chromeH + zoneH
        val r = RectF(margin, h - margin - panelH, w - margin, h - margin)

        // 등장: 아래에서 위로 부드럽게
        c.save()
        drawnShift = enterShift()
        c.translate(0f, drawnShift)
        panel(c, r, scene)

        if (title.isNotEmpty()) {
            val tw = textP.measureText(title)
            val chip = RectF(r.left + dp(scene, 12f), r.top - dp(scene, 11f), r.left + dp(scene, 12f) + tw + dp(scene, 16f), r.top + dp(scene, 11f))
            // 다크 칩 (골드 테두리 느낌의 프리미엄 뱃지)
            UiKit.button(c, g, chip, title, 0xFF6B4F35.toInt(), 0xFFF8EFDC.toInt(), 11f)
        }

        // 본문 — 제목 칩과 버튼 사이 공간의 세로 가운데에, 가로는 좌측 정렬로.
        // 한 줄에 Type.lineHeight 만큼만 내려간다(줄 간격 통일).
        val textTop = r.top + dp(scene, 32f)
        val textBottom = r.bottom - dp(scene, 12f + 30f + 12f)
        val blockH = lineH * lines.size
        val blockTop = textTop + maxOf(0f, (textBottom - textTop) - blockH) / 2f
        var ty = blockTop + (lineH - bodyPaint.descent() - bodyPaint.ascent()) / 2f
        val tx = r.left + dp(scene, 14f)
        for (ln in lines) {
            c.drawText(ln, tx, ty, bodyPaint)
            ty += lineH
        }

        // 선택지 — 첫 번째(긍정) 버튼은 골드 프라이머리, 나머지는 크림 세컨더리
        val n = choices.size
        val bw = (r.width() - dp(scene, 10f) * (n + 1)) / n
        val bty = r.bottom - dp(scene, 42f)
        val rects = ArrayList<RectF>()
        for (i in 0 until n) {
            val br = RectF(
                r.left + dp(scene, 10f) + i * (bw + dp(scene, 10f)), bty,
                r.left + dp(scene, 10f) + i * (bw + dp(scene, 10f)) + bw, bty + dp(scene, 30f)
            )
            val primary = (n == 1) || (i == 0)
            if (primary) {
                drawButton(c, scene, br, choices[i].label, 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 12.5f)
            } else {
                drawButton(c, scene, br, choices[i].label, 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12.5f)
            }
            rects.add(br)
        }
        choiceRects = rects
        c.restore()
    }
}

// ---------------------------------------------------------------------------
// 「할머니의 수첩」 — 이야기가 남긴 기록 (v0.5 「수첩을 다시 펴다」)
// ---------------------------------------------------------------------------

/**
 * 수첩 두 겹을 다시 읽는 화면. 이야기를 끝낼 때마다 **실제로 한 줄이 남는다** 는 감각을 준다.
 *
 *  - 앞장 = 할머니가 남긴 쪽지 (`MainStory.RELICS`, 메인 장을 끝낼 때마다 한 장 넘어온다)
 *  - 뒷장 = 동네에서 배운 한 줄 (`SideStories.journal`, 에피소드 마무리 막에서 기록된다)
 *
 * 아직 읽지 않은 자리는 점선 카드로 비워 둔다 — 채워 갈 이유가 화면에 남아야 하니까.
 * 탭을 하나 더 늘리는 대신 도감 헤더의 📔 · 에필로그 선택지에서 이 창을 부른다.
 */
class NotebookOverlay(scene: Scene) : Overlay(scene) {
    override val coversWorld: Boolean get() = true

    private var tab = 0                     // 0 = 앞장(할머니) / 1 = 뒷장(동네)
    private var page = 0
    private var shift = 0f
    private var panelR = RectF()
    private val hitRects = ArrayList<Triple<RectF, String, () -> Unit>>()

    init {
        scene.game.sfx(Audio.Sfx.BAG_OPEN, 0.5f)
    }

    private val perPage = 4

    private fun pages(): Int {
        val total = if (tab == 0) MainStory.RELICS.size else SideStories.EPISODES.size
        return ((total + perPage - 1) / perPage).coerceAtLeast(1)
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            val y = tap.y - shift
            for ((rect, _, action) in hitRects) {
                if (rect.contains(tap.x, y)) {
                    scene.game.sfx(Audio.Sfx.TAP, 0.5f)
                    action()
                    return
                }
            }
        }
        if (input.justA) {
            page = (page + 1) % pages()
            scene.game.sfx(Audio.Sfx.TAP, 0.4f)
            return
        }
        if (input.justB || input.justBack) finished = true
    }

    override fun draw(c: Canvas) {
        dim(c, scene)
        val g = scene.game
        val st = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        c.save()
        shift = enterShift()
        c.translate(0f, shift)
        panelR = RectF(dp(scene, 12f), h * 0.08f, w - dp(scene, 12f), h * 0.90f)
        panel(c, panelR, scene)
        hitRects.clear()

        val relicsRead = if (st.mainQuestFinished) MainStory.RELICS.size else st.mainQuestStage
        val entries = SideStories.journal(g.context).associateBy { it.regionId }

        // ---- 머리글 ----
        textP.textSize = textDp(scene, 13f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("할머니의 수첩", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 22f), textP)
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        val echoes = SideStories.echoesLeft(g.context)
        val sub = "앞장 ${relicsRead}/${MainStory.RELICS.size}장 · 뒷장 ${entries.size}/${SideStories.EPISODES.size}줄" +
            (if (echoes > 0) " · 읽지 않은 뒷장 ${echoes}개" else "")
        val subW = textP.measureText(sub)
        c.drawText(sub, panelR.centerX() - subW / 2f, panelR.top + dp(scene, 22f), textP)

        // ---- 닫기 ----
        val closeR = RectF(panelR.right - dp(scene, 40f), panelR.top + dp(scene, 8f),
            panelR.right - dp(scene, 10f), panelR.top + dp(scene, 28f))
        UiKit.cuteButton(c, g, closeR, "close 닫기", UiKit.CREAM, 0xFF6B4F35.toInt(), 9.5f, depthDp = 1.2f)
        hitRects.add(Triple(closeR, "close") { finished = true })

        // ---- 앞장 / 뒷장 ----
        val tabY = panelR.top + dp(scene, 32f)
        val tabW = (panelR.width() - dp(scene, 40f)) / 2f
        for (i in 0 until 2) {
            val tr = RectF(
                panelR.left + dp(scene, 14f) + i * (tabW + dp(scene, 12f)), tabY,
                panelR.left + dp(scene, 14f) + i * (tabW + dp(scene, 12f)) + tabW, tabY + dp(scene, 20f)
            )
            val on = i == tab
            UiKit.cuteButton(
                c, g, tr,
                if (on) "book 앞장 · 할머니의 한 줄" else "book 뒷장 · 동네의 한 줄",
                if (on) UiKit.PASTEL_SAND else Color.argb(120, 214, 204, 186),
                if (on) UiKit.INK else Color.argb(150, 74, 55, 40), 9.5f, depthDp = 1.2f
            )
            hitRects.add(Triple(tr, "tab$i") { tab = i; page = 0 })
        }

        // ---- 본문 행 ----
        val bodyTop = tabY + dp(scene, 26f)
        val bodyBottom = panelR.bottom - dp(scene, 34f)
        val rowH = (bodyBottom - bodyTop) / perPage
        val left = panelR.left + dp(scene, 14f)
        val right = panelR.right - dp(scene, 14f)
        val bodyPaint = Type.paint(Role.BODY, Type.INK)
        page = page.coerceIn(0, pages() - 1)
        for (slot in 0 until perPage) {
            val idx = page * perPage + slot
            val y = bodyTop + rowH * slot
            val cardR = RectF(left, y + dp(scene, 2f), right, y + rowH - dp(scene, 5f))
            if (tab == 0) {
                val relic = MainStory.RELICS.getOrNull(idx)
                if (relic == null) break
                val opened = idx < relicsRead
                UiKit.stitchCard(c, g, cardR,
                    if (opened) 0xFFFFF9EC.toInt() else 0xFFF3EADA.toInt(),
                    if (opened) UiKit.BROWN_LINE else Color.argb(70, 150, 128, 100), 1.2f,
                    stitched = !opened)
                textP.textSize = textDp(scene, 9f)
                textP.color = if (opened) 0xFF8A6B4A.toInt() else 0xFFB3A292.toInt()
                c.drawText("${idx + 1}장째 · ${relic.first}", left + dp(scene, 10f), y + dp(scene, 16f), textP)
                if (opened) {
                    bodyPaint.textSize = textDp(scene, 11.5f)
                    var ty = y + dp(scene, 32f)
                    for (ln in Type.wrap("\"" + relic.second + "\"", bodyPaint, right - left - dp(scene, 24f)).take(3)) {
                        c.drawText(ln, left + dp(scene, 10f), ty, bodyPaint)
                        ty += Type.lineHeight(Role.BODY)
                    }
                } else {
                    textP.textSize = textDp(scene, 9.5f)
                    textP.color = 0xFFA08E7C.toInt()
                    c.drawText("아직 수첩의 사이에 끼워 있다 · 장을 하나 더 끝내면 넘어온다",
                        left + dp(scene, 10f), cardR.centerY() + dp(scene, 4f), textP)
                }
            } else {
                val ep = SideStories.EPISODES.getOrNull(idx)
                if (ep == null) break
                val rec = entries[ep.regionId]
                UiKit.stitchCard(c, g, cardR,
                    if (rec != null) 0xFFF4F8F0.toInt() else 0xFFF3EADA.toInt(),
                    if (rec != null) 0xFF8FA98C.toInt() else Color.argb(70, 150, 128, 100), 1.2f,
                    stitched = rec == null)
                val regionName = Regions.byId[ep.regionId]?.name ?: ep.regionId
                textP.textSize = textDp(scene, 9f)
                textP.color = if (rec != null) 0xFF5E7A5B.toInt() else 0xFFB3A292.toInt()
                c.drawText("$regionName · ${ep.title}", left + dp(scene, 10f), y + dp(scene, 16f), textP)
                if (rec != null) {
                    bodyPaint.textSize = textDp(scene, 11.5f)
                    var ty = y + dp(scene, 32f)
                    for (ln in Type.wrap("'" + rec.line + "'", bodyPaint, right - left - dp(scene, 24f)).take(3)) {
                        c.drawText(ln, left + dp(scene, 10f), ty, bodyPaint)
                        ty += Type.lineHeight(Role.BODY)
                    }
                    textP.textSize = textDp(scene, 8f)
                    textP.color = 0xFF8A7360.toInt()
                    val when0 = "${rec.day}일차에 적음"
                    c.drawText(when0, right - textP.measureText(when0) - dp(scene, 12f),
                        cardR.bottom - dp(scene, 6f), textP)
                } else {
                    textP.textSize = textDp(scene, 9.5f)
                    textP.color = 0xFFA08E7C.toInt()
                    c.drawText("이야기를 끝내면 이 자리에 한 줄이 남는다",
                        left + dp(scene, 10f), cardR.centerY() + dp(scene, 4f), textP)
                }
            }
        }

        // ---- 페이지 넘김 ----
        val navY = panelR.bottom - dp(scene, 28f)
        val label = "${page + 1} / ${pages()}"
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(label, panelR.centerX() - textP.measureText(label) / 2f, navY + dp(scene, 14f), textP)
        val prevR = RectF(left, navY, left + dp(scene, 56f), navY + dp(scene, 22f))
        val nextR = RectF(right - dp(scene, 56f), navY, right, navY + dp(scene, 22f))
        UiKit.cuteButton(c, g, prevR, "arrow_left 이전", if (page > 0) UiKit.CREAM else Color.argb(110, 214, 204, 186),
            if (page > 0) UiKit.INK else Color.argb(120, 74, 55, 40), 9.5f, depthDp = 1.2f)
        UiKit.cuteButton(c, g, nextR, "arrow_right 다음", if (page < pages() - 1) UiKit.CREAM else Color.argb(110, 214, 204, 186),
            if (page < pages() - 1) UiKit.INK else Color.argb(120, 74, 55, 40), 9.5f, depthDp = 1.2f)
        if (page > 0) hitRects.add(Triple(prevR, "prev") { page-- })
        if (page < pages() - 1) hitRects.add(Triple(nextR, "next") { page++ })
        c.restore()
    }
}

// ---------------------------------------------------------------------------
// 메뉴 (상태 / 피자 / 도감 / 설정)
// ---------------------------------------------------------------------------

class MenuOverlay(scene: Scene, private val showAchievements: Boolean = false) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true

    init {
        scene.game.sfx(Audio.Sfx.BAG_OPEN, 0.6f)   // 🎒 가방 지퍼 열리는 소리
    }

    /** 탭마다 파스텔 색이 다르다 — 가방 속 색색의 인덱스 탭처럼 */
    private enum class Tab(val label: String, val icon: String, val tint: Int) {
        STATUS("상태", "note", UiKit.PASTEL_PEACH),
        QUEST("퀘스트", "map", UiKit.PASTEL_SKY),
        GROW("성장", "leaf", UiKit.PASTEL_MINT),
        PIZZA("피자", "pizza", UiKit.PASTEL_LEMON),
        BOOK("도감", "book", UiKit.PASTEL_LILAC),
        ALBUM("사진집", "camera", UiKit.PASTEL_SKY),
        SETTINGS("설정", "gear", UiKit.PASTEL_SAND),
        ACHIEVE("업적", "🏅", UiKit.PASTEL_LEMON)
    }

    // ---- 가방 속 전용 그리기 도우미 (아기자기 키트) ----
    private fun cuteBtn(c: Canvas, r: RectF, label: String, base: Int, textCol: Int, size: Float) =
        UiKit.cuteButton(c, scene.game, r, label, base, textCol, size)

    private fun cuteCard(
        c: Canvas, r: RectF, tint: Int = UiKit.CARD_HI,
        border: Int = UiKit.BROWN_LINE, borderW: Float = 1.6f, stitched: Boolean = true, selected: Boolean = false
    ) = UiKit.stitchCard(c, scene.game, r, tint, border, borderW, stitched, selected)

    /** 비활성 버튼 (납작한 회색 천) */
    private fun cuteBtnOff(c: Canvas, r: RectF, label: String, size: Float) =
        UiKit.cuteButton(c, scene.game, r, label, Color.argb(120, 214, 204, 186), Color.argb(150, 74, 55, 40), size)

    private var tab = if (showAchievements) Tab.ACHIEVE else Tab.STATUS
    private val tabRects = ArrayList<Pair<RectF, Tab>>()
    private val btnRects = ArrayList<Triple<RectF, String, () -> Unit>>()
    private var closeRect = RectF()
    private var resetArmed = false
    private var statusPage = 0
    private var questPage = 0
    private var questSubTab = 0
    private var questTaskPage = 0
    private var achievementPage = 0
    private val albumPhotoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val g = scene.game
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) {
            // 도감에서 키보드·드롭다운이 열려 있으면 그 칸부터 닫는다 (메뉴가 통째로 꺼지면 당황스럽다)
            if (tab == Tab.BOOK && (dexKeyboard || dexDrop >= 0)) {
                if (dexDrop >= 0) dexDrop = -1
                else {
                    val add = dexInput.commit()
                    if (add.isNotEmpty()) dexQuery += add
                    dexKeyboard = false
                }
                return
            }
            finished = true
            return
        }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) {
            g.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        for ((r, t) in tabRects) {
            if (r.contains(tap.x, tap.y)) {
                // 📚 도감 탭은 책장 넘기는 소리로 열린다
                if ((t == Tab.BOOK || t == Tab.ALBUM) && tab != t) g.sfx(Audio.Sfx.BOOK_OPEN, 0.7f)
                else g.sfx(Audio.Sfx.TAP, 0.45f)
                tab = t
                resetArmed = false
                return
            }
        }
        for ((r, _, action) in btnRects) {
            if (r.contains(tap.x, tap.y)) {
                g.sfx(Audio.Sfx.TAP, 0.5f)
                action()
                return
            }
        }
        if (tab == Tab.BOOK && panelR.contains(tap.x, tap.y)) {
            // 도감 셀 탭 -> 새 정보
            for (def in Birds.ALL) {
                val cell = bookCell(def) ?: continue
                if (cell.contains(tap.x, tap.y)) {
                    g.sfx(Audio.Sfx.TAP, 0.5f)
                    showBirdInfo(def)
                    return
                }
            }
        }
    }

    private fun showBirdInfo(def: BirdDef) {
        scene.openOverlay(BirdDetailOverlay(scene, def.birdNum))
    }

    /** 퀘스트 카드 탭 — 보리 박사/NPC 또는 실제 탐조 자리까지 자전거로 안내한다. */
    private fun autoGoMainQuest() {
        if (MainStory.current(scene.game.state) == null) return
        QuestNavigation.startMainQuest(scene.game, scene)
        finished = true
    }

    /**
     * 카메라샵 안내 — 가게는 **12개 도시 골목**에만 있다 (`CameraShops`).
     * 이 동네가 도시면 그 도시 진열대로, 아니라면 가장 가까운 도시로 자전거를 태워 준다.
     * (자전거·장식 코너는 서울 본점 전용 — 본점 위치는 `NpcRoster.SHOP_REGION`)
     */
    private fun openShopTrip() {
        val g = scene.game
        val s = g.state
        val here = CameraShops.shop(s.region)
        if (here != null) {
            val sale = if (here.saleLabel.isNotEmpty()) "\n\n${here.saleLabel}" else ""
            scene.openOverlay(
                DialogOverlay(
                    scene, "${here.shopName} · 사장 ${here.keeper}",
                    "\"${here.greeting}\"" + "\n\n특화 ${here.specialty} · ${here.spot.label}$sale",
                    shopChoices(scene, here)
                )
            )
            return
        }
        val near = CameraShops.nearestShop(s.region)
        val nearName = Regions.byId[near.regionId]?.name ?: near.regionId
        val hops = CameraShops.hopsToShop(s.region).coerceAtLeast(1)
        scene.openOverlay(
            DialogOverlay(
                scene, "카메라샵 · $nearName",
                "\"${CameraShops.CITY_RULE_LINE}\"\n\n" +
                    "가장 가까운 가게 — $nearName ${near.shopName} · ${near.spot.label} (터널 ${hops}칸)\n" +
                    "\"${near.greeting}\"",
                listOf(
                    DialogOverlay.Choice("🚲 $nearName ${near.spot.label}로 이동") {
                        finished = true
                        QuestNavigation.startRegionTrip(g, scene, near.regionId, "카메라샵")
                    },
                    DialogOverlay.Choice("🧭 걸어서 $nearName 가게까지") {
                        val keeper = NpcRoster.shopkeeperFor(near.regionId)
                        if (keeper != null) QuestNavigation.startPersonTrip(g, scene, keeper)
                        finished = true
                    },
                    DialogOverlay.Choice("다음에 갈게요")
                )
            )
        )
    }

    /** 상태 창 '카메라샵' 칸 값 — 이 동네 진열대 / 가장 가까운 도시까지의 거리 */
    private fun cameraShopCellText(regionId: String): String {
        val here = CameraShops.shop(regionId)
        if (here != null) return "지금 이 동네 · ${here.specialty}"
        val near = CameraShops.nearestShop(regionId)
        val name = Regions.byId[near.regionId]?.name ?: near.regionId
        return "$name · 터널 ${CameraShops.hopsToShop(regionId).coerceAtLeast(1)}칸"
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

        // ---- 헤더: 가방 덮개(가죽 플랩) + 이름표 + 닫기 단추 ----
        val flapR = RectF(panelR.left + dp(scene, 6f), panelR.top + dp(scene, 6f), panelR.right - dp(scene, 6f), panelR.top + dp(scene, 38f))
        UiKit.pixelFillGradient(c, flapR, dp(scene, 2f), 0xFFD9A46E.toInt(), 0xFFBF8552.toInt())
        // 플랩 아래 그림자 한 줄 (덮개가 살짝 떠 있는 느낌)
        fillP.color = Color.argb(60, 60, 34, 12)
        c.drawRect(flapR.left + dp(scene, 4f), flapR.bottom - dp(scene, 1.5f), flapR.right - dp(scene, 4f), flapR.bottom + dp(scene, 1.5f), fillP)
        UiKit.pixelStroke(c, flapR, dp(scene, 2f), UiKit.OUTLINE, dp(scene, 1.6f))
        UiKit.stitch(c, g, RectF(flapR.left + dp(scene, 4f), flapR.top + dp(scene, 4f), flapR.right - dp(scene, 4f), flapR.bottom - dp(scene, 4f)),
            dp(scene, 1.4f), 0xFFFFE9C4.toInt(), 175)

        // 이름표 스티커 (제목)
        val titleTxt = "backpack 여행 가방"
        val tagW = UiKit.nameTagWidth(g, titleTxt, 15f)
        val tagR = RectF(flapR.left + dp(scene, 10f), flapR.centerY() - dp(scene, 13f), flapR.left + dp(scene, 10f) + tagW, flapR.centerY() + dp(scene, 13f))
        UiKit.nameTag(c, g, tagR, titleTxt, 15f)
        // 반짝이 두 개 (숨쉬듯 깜빡)
        UiKit.sparkle(c, tagR.right + dp(scene, 9f), tagR.top + dp(scene, 4f), dp(scene, 7f), 0xFFFFF1C2.toInt(), g.time * 3.1f)
        UiKit.sparkle(c, tagR.right + dp(scene, 17f), tagR.bottom - dp(scene, 5f), dp(scene, 5f), 0xFFFFF1C2.toInt(), g.time * 3.1f + 2.1f)
        // 서브 타이틀 (넓을 때만 — 닫기 단추와 겹침 방지)
        if (panelR.width() > dp(scene, 420f)) {
            textP.textSize = textDp(scene, 10f)
            textP.color = 0xFFFFF3DC.toInt()
            UiKit.drawIconText(c, scene.game, "지금 펼친 칸 · ${tab.icon} ${tab.label}", tagR.right + dp(scene, 30f), flapR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        }

        val closeCx = flapR.right - dp(scene, 20f)
        val closeCy = flapR.centerY()
        val closeRr = dp(scene, 12f)
        closeRect = RectF(
            closeCx - closeRr - dp(scene, 6f), closeCy - closeRr - dp(scene, 6f),
            closeCx + closeRr + dp(scene, 6f), closeCy + closeRr + dp(scene, 6f)
        )
        UiKit.circleButton(c, g, closeCx, closeCy, closeRr, "close", 11f, 0xFFFFF3DC.toInt(), 0xFF8A4A2A.toInt())

        // ---- 탭: 색색의 인덱스 탭. 고른 탭은 위로 톡 튀어나온다 ----
        tabRects.clear()
        val nTabs = Tab.values().size
        val tabGap = dp(scene, 7f)
        val tabW = (panelR.width() - dp(scene, 24f) - tabGap * (nTabs - 1)) / nTabs
        val tabTop = panelR.top + dp(scene, 45f)
        val tabH = dp(scene, 26f)
        for ((i, t) in Tab.values().withIndex()) {
            val x0 = panelR.left + dp(scene, 12f) + i * (tabW + tabGap)
            val hit = RectF(x0, tabTop - dp(scene, 4f), x0 + tabW, tabTop + tabH + dp(scene, 4f))
            val label = if (tabW < dp(scene, 90f)) t.label else "${t.icon} ${t.label}"
            if (t == tab) {
                val r = RectF(x0, tabTop - dp(scene, 3f), x0 + tabW, tabTop + tabH - dp(scene, 1f))
                cuteBtn(c, r, label, t.tint, UiKit.INK, 12.5f)
                UiKit.sparkle(c, r.right - dp(scene, 4f), r.top - dp(scene, 1f), dp(scene, 6f), 0xFFFFFFFF.toInt(), g.time * 4f + i)
            } else {
                val r = RectF(x0 + dp(scene, 1.5f), tabTop + dp(scene, 2f), x0 + tabW - dp(scene, 1.5f), tabTop + tabH - dp(scene, 1f))
                UiKit.cuteButton(c, g, r, label, UiKit.lighten(t.tint, 26), UiKit.MUTED, 11.5f, depthDp = 2f)
            }
            tabRects.add(hit to t)
        }
        UiKit.stitchLine(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 78f))

        btnRects.clear()
        when (tab) {
            Tab.STATUS -> drawStatus(c)
            Tab.QUEST -> drawQuest(c)
            Tab.GROW -> drawGrow(c)
            Tab.PIZZA -> drawPizza(c)
            Tab.BOOK -> drawBook(c)
            Tab.ALBUM -> drawAlbum(c)
            Tab.SETTINGS -> drawSettings(c)
            Tab.ACHIEVE -> drawAchievements(c)
        }
    }

    private fun contentTop(): Float = panelR.top + dp(scene, 84f)
    private fun contentBottom(): Float = panelR.bottom - dp(scene, 14f)

    private fun drawStatus(c: Canvas) {
        val g = scene.game
        val s = g.state
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)

        val mins = (s.playSeconds / 60f).toInt()
        val timeStr = if (mins >= 60) "${mins / 60}시간 ${mins % 60}분" else "${mins}분"
        val rig = s.rig()
        val decorLuck = s.decorLuck()

        var y = contentTop()

        // 1) 지갑 — 레몬색 동전 주머니 카드 + 금색 아일렛
        val walletH = dp(scene, 32f)
        val walletR = RectF(left, y, right, y + walletH)
        cuteCard(c, walletR, UiKit.PASTEL_LEMON, UiKit.GOLD_DEEP, 1.8f)
        UiKit.iconCircle(c, g, left + dp(scene, 17f), walletR.centerY(), dp(scene, 11f), "coin", 12f, UiKit.GOLD)
        textP.textSize = textDp(scene, 12f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("내 지갑", left + dp(scene, 34f), walletR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        textP.textSize = textDp(scene, 14f)
        textP.color = 0xFF4A2E12.toInt()
        val moneyTxt = won(s.money)
        c.drawText(moneyTxt, right - dp(scene, 12f) - textP.measureText(moneyTxt), walletR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        UiKit.sparkle(c, right - dp(scene, 18f) - textP.measureText(moneyTxt), walletR.top + dp(scene, 7f), dp(scene, 5f), 0xFFFFFFFF.toInt(), g.time * 3.6f)
        y += walletH + dp(scene, 6f)

        // 2) 컨디션 카드 — 배고픔/행운 그라데이션 바
        val vitH = dp(scene, 52f)
        val vitR = RectF(left, y, right, y + vitH)
        cuteCard(c, vitR)
        val barX = left + dp(scene, 88f)
        val valX = right - dp(scene, 10f)
        val barW = (valX - dp(scene, 34f) - barX).coerceAtLeast(dp(scene, 40f))
        // 배고픔 행
        var ry = y + dp(scene, 18f)
        textP.textSize = textDp(scene, 11f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("배고픔", left + dp(scene, 10f), ry, textP)
        val hungerCols = if (s.hunger < 25f) (0xFFF28B82.toInt() to 0xFFE2574C.toInt()) else (0xFFFFB35C.toInt() to 0xFFF2913C.toInt())
        UiKit.bar(c, g, barX, ry - dp(scene, 10f), barW, dp(scene, 12f), s.hunger / 100f, hungerCols.first, hungerCols.second)
        textP.textSize = textDp(scene, 10.5f)
        val hungerTxt = "${s.hunger.toInt()}"
        c.drawText(hungerTxt, valX - textP.measureText(hungerTxt), ry, textP)
        // 행운 행
        ry = y + dp(scene, 40f)
        textP.textSize = textDp(scene, 11f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("행운", left + dp(scene, 10f), ry, textP)
        UiKit.bar(c, g, barX, ry - dp(scene, 10f), barW, dp(scene, 12f), s.effectiveLuck() / 100f, 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        textP.textSize = textDp(scene, 10.5f)
        val luckBonus = decorLuck + s.bikeLuck()
        val luckTxt = "${s.effectiveLuck().toInt()}" + (if (luckBonus > 0) "(+$luckBonus)" else "")
        c.drawText(luckTxt, valX - textP.measureText(luckTxt), ry, textP)
        y += vitH + dp(scene, 6f)

        // 2.5) 탐조가 레벨 스트립 — 레벨/칭호 + 미니 경험치 바
        val lvH = dp(scene, 28f)
        val lvR = RectF(left, y, right, y + lvH)
        cuteCard(c, lvR, UiKit.PASTEL_MINT, 0xFF7FB37A.toInt(), 1.6f, stitched = false)
        textP.textSize = textDp(scene, 11f)
        val lvCy = lvR.centerY() - (textP.descent() + textP.ascent()) / 2f
        textP.color = 0xFF4A3728.toInt()
        var lvTxt = "Lv.${s.level} 「${s.title()}」" + (if (s.skillPoints > 0) " · SP ${s.skillPoints}" else "")
        val expBarW = dp(scene, 86f)
        val maxLvW = (right - left) - dp(scene, 24f) - expBarW - dp(scene, 8f)
        if (textP.measureText(lvTxt) > maxLvW && maxLvW > dp(scene, 40f)) {
            while (lvTxt.length > 1 && textP.measureText("$lvTxt…") > maxLvW) lvTxt = lvTxt.dropLast(1)
            lvTxt = "$lvTxt…"
        }
        c.drawText(lvTxt, left + dp(scene, 10f), lvCy, textP)
        if (s.level >= Progression.MAX_LEVEL) {
            textP.textSize = textDp(scene, 10f)
            textP.color = 0xFFB5651D.toInt()
            val maxT = "MAX"
            c.drawText(maxT, right - dp(scene, 10f) - textP.measureText(maxT), lvCy, textP)
        } else {
            UiKit.bar(
                c, g, right - dp(scene, 10f) - expBarW, lvR.centerY() - dp(scene, 5f), expBarW, dp(scene, 10f),
                s.expProgress(), 0xFF8FD694.toInt(), 0xFF4E9A51.toInt()
            )
        }
        y += lvH + dp(scene, 6f)

        // 3) 카메라 스트립 — 지금 장비(조합)와 촬영 반경. 누르면 장비 가방이 열린다.
        val camH = dp(scene, 34f)
        val camR = RectF(left, y, right, y + camH)
        cuteCard(c, camR, UiKit.PASTEL_SKY, 0xFF6F9FC2.toInt(), 1.6f)
        val camIconW = dp(scene, 44f)
        c.drawBitmap(
            g.assets.camProfile(rig.look), null,
            RectF(left + dp(scene, 6f), camR.centerY() - camIconW * 10f / 32f, left + dp(scene, 6f) + camIconW, camR.centerY() + camIconW * 10f / 32f),
            g.assets.sprPaint
        )
        val camTx = left + dp(scene, 54f)
        textP.textSize = textDp(scene, 11f)
        textP.color = 0xFF4A3728.toInt()
        var camName = rig.title
        val camMaxW = (right - camTx) - dp(scene, 92f)
        if (textP.measureText(camName) > camMaxW && camMaxW > dp(scene, 40f)) {
            while (camName.length > 1 && textP.measureText("$camName…") > camMaxW) camName = camName.dropLast(1)
            camName = "$camName…"
        }
        c.drawText(camName, camTx, camR.top + dp(scene, 15f), textP)
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("환산 ${rig.teleMm}mm · 반경 ${rig.reach.fmt1()}칸 · ${rig.sensor.label}", camTx, camR.top + dp(scene, 27f), textP)
        val bagR = RectF(right - dp(scene, 84f), camR.centerY() - dp(scene, 12f), right - dp(scene, 7f), camR.centerY() + dp(scene, 10f))
        cuteBtn(c, bagR, "backpack 장비·장신구", UiKit.GOLD, 0xFF4A2E12.toInt(), 9.5f)
        btnRects.add(Triple(bagR, "gearbag") { scene.openOverlay(DialogOverlay(scene, "여행 가방", "카메라를 조립하거나 행운 장신구를 착용하세요.", listOf(
            DialogOverlay.Choice("카메라 장비") { scene.openOverlay(GearBagOverlay(scene)) },
            DialogOverlay.Choice("행운 장신구") { scene.openOverlay(CharmOverlay(scene)) },
            DialogOverlay.Choice("닫기")
        ))) })
        y += camH + dp(scene, 6f)

        // 4) 정보 그리드 — 남은 높이에 맞춰 자동 배분 (2열 x 5행)
        val gap = dp(scene, 5f)
        val gridH = contentBottom() - y
        val paged = gridH < dp(scene, 18f) * 5 + gap * 4
        val gridBottom = contentBottom() - if (paged) dp(scene, 24f) else 0f
        val rows = ((gridBottom - y + gap) / (dp(scene, 18f) + gap)).toInt().coerceIn(1, 5)
        val rowH = ((gridBottom - y - gap * (rows - 1)) / rows).coerceAtLeast(dp(scene, 18f))
        val colW = (right - left - gap) / 2f
        // 자주 쓰는 바로가기는 작은 화면에서도 첫 페이지에 남긴다.
        val cells = listOf(
            Triple("search", "퀘스트", s.questBird?.let { Birds.byId[it]?.name } ?: "퀘스트 열기 ›"),
            Triple("camera", "카메라샵", cameraShopCellText(s.region)),
            Triple("calendar", "시각", "${s.timeLabel()} · 사진 ${s.photos}장"),
            Triple("house", "우리 집", Regions.byId[s.homeRegion]?.name ?: "?"),
            Triple("pin", "위치", "${Regions.byId[s.region]?.name ?: "?"} · ${s.visited.size}/${Regions.ALL.size}"),
            Triple("house", "주택", "${s.ownedHomes.size}채 · 인테리어 ${s.ownedHouseStyles.size}/${HouseStyles.ALL.size}"),
            Triple("book", "도감", "${s.birdCounts.size}/${Birds.ALL.size}종"),
            Triple("search", "의뢰", if (s.activeQuests.isEmpty()) "없음" else "${s.activeQuests.size}/3 · ${s.activeQuests.first().title}"),
            Triple("calendar", "플레이", timeStr),
            Triple("bike", "자전거", "${s.bike().name} · ${s.ownedBikes.size}대" + (if (s.ownedBikeParts.isNotEmpty()) " · 부속품 ${s.ownedBikeParts.size}" else "")),
            Triple("pizza", "피자", "${s.pizzaCount}개 · 화덕 ${s.pizzaCountOfKind(PizzaKind.OVEN)} · 일반 ${s.pizzaCountOfKind(PizzaKind.REGULAR)} · 장식 ${s.placedDecorIds().size}/${s.decorSlots.size} · 행운+${s.decorLuck()}"),
            // 카메라샵은 12개 도시에만 있다 — 탭하면 위치를 알려 주고, 도시가 아니면 태워 준다.
            Triple("camera", "카메라샵", cameraShopCellText(s.region))
        )
        val perPage = rows * 2
        val pages = (cells.size + perPage - 1) / perPage
        statusPage = statusPage.coerceIn(0, pages - 1)
        val shownCells = cells.drop(statusPage * perPage).take(perPage)
        for (i in shownCells.indices) {
            val col = i % 2
            val row = i / 2
            val cr = RectF(
                left + col * (colW + gap), y + row * (rowH + gap),
                left + col * (colW + gap) + colW, y + row * (rowH + gap) + rowH
            )
            // 격자무늬 천처럼 두 색을 번갈아 (가로세로 체크)
            val checker = (col + row) % 2 == 0
            cuteCard(c, cr, if (checker) UiKit.CARD_HI else 0xFFFFF4E2.toInt(), stitched = rowH >= dp(scene, 24f))
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            val iconToken = shownCells[i].first
            val label = shownCells[i].second
            UiKit.icon(c, g, iconToken, RectF(cr.left + dp(scene, 7f), cr.centerY() - dp(scene, 8f), cr.left + dp(scene, 23f), cr.centerY() + dp(scene, 8f)))
            val ty = cr.centerY() - (textP.descent() + textP.ascent()) / 2f
            c.drawText(label, cr.left + dp(scene, 27f), ty, textP)
            val labelW = textP.measureText(label)
            // 값은 오른쪽 정렬 + 넘치면 말줄임
            textP.textSize = textDp(scene, 10.5f)
            textP.color = 0xFF4A3728.toInt()
            var value = shownCells[i].third
            val maxVW = cr.width() - dp(scene, 16f) - labelW - dp(scene, 6f)
            if (textP.measureText(value) > maxVW && maxVW > dp(scene, 20f)) {
                while (value.length > 1 && textP.measureText("$value…") > maxVW) value = value.dropLast(1)
                value = "$value…"
            }
            // 상점 셀은 눌린다 — 어디 있는지 알려 주고, 다른 지역이면 자전거로 태워 준다
            if (label == "카메라샵") {
                btnRects.add(Triple(cr, "shop_trip") { openShopTrip() })
            }
            val vty = cr.centerY() - (textP.descent() + textP.ascent()) / 2f
            c.drawText(value, cr.right - dp(scene, 8f) - textP.measureText(value), vty, textP)
        }
        if (pages > 1) {
            val py = contentBottom() - dp(scene, 20f)
            val prev = RectF(left, py, left + dp(scene, 66f), contentBottom())
            val next = RectF(right - dp(scene, 66f), py, right, contentBottom())
            if (statusPage > 0) {
                cuteBtn(c, prev, "‹ 이전", UiKit.PASTEL_SKY, UiKit.INK, 10f)
                btnRects.add(Triple(prev, "status_prev") { statusPage-- })
            }
            if (statusPage < pages - 1) {
                cuteBtn(c, next, "다음 ›", UiKit.PASTEL_SKY, UiKit.INK, 10f)
                btnRects.add(Triple(next, "status_next") { statusPage++ })
            }
            textP.textSize = textDp(scene, 10f)
            textP.color = UiKit.MUTED
            val label = "정보 ${statusPage + 1}/$pages"
            c.drawText(label, panelR.centerX() - textP.measureText(label) / 2f, py + dp(scene, 14f), textP)
        }
    }

    private fun drawQuest(c: Canvas) {
        val s = scene.game.state
        val left = panelR.left + dp(scene, 16f)
        val right = panelR.right - dp(scene, 16f)
        var y = contentTop() + dp(scene, 5f)

        // 메인 퀘스트와 시간 제한 없는 서브 의뢰
        // 카드 자체 = 자동 진행 버튼: 누르면 어드바이저가 정한 추천 지역으로 이동한다.
        val chapter = MainStory.current(s)
        val advice = chapter?.let { MainQuestAdvisor.advise(s) }
        val mainH = if (advice != null) dp(scene, 94f) else dp(scene, 72f)
        val mainR = RectF(left, y, right, y + mainH)
        val mainDone = chapter?.isComplete(s) == true
        val mainGo = advice != null && !advice.alreadyThere
        cuteCard(c, mainR, if (mainDone || mainGo) UiKit.PASTEL_LEMON else 0xFFFFF7E6.toInt(),
            if (mainDone || mainGo) UiKit.GOLD_DEEP else UiKit.BROWN_LINE, if (mainDone || mainGo) 2f else 1.6f,
            selected = mainDone || mainGo)
        // 왼쪽 위 작은 책갈피 리본
        UiKit.pixelFill(c, RectF(mainR.right - dp(scene, 26f), mainR.top - dp(scene, 2f), mainR.right - dp(scene, 14f), mainR.top + dp(scene, 16f)), dp(scene, 1.2f), if (mainDone) UiKit.GOLD else 0xFFE2857A.toInt())
        UiKit.pixelStroke(c, RectF(mainR.right - dp(scene, 26f), mainR.top - dp(scene, 2f), mainR.right - dp(scene, 14f), mainR.top + dp(scene, 16f)), dp(scene, 1.2f), UiKit.OUTLINE, dp(scene, 1.2f))
        textP.textSize = textDp(scene, 13f)
        textP.color = 0xFF6B4F35.toInt()
        val mainTitle = when {
            s.mainQuestFinished -> "메인 완결 · 함께 사는 지도"
            !s.mainQuestStarted -> "! 메인 · ${NpcRoster.professorRegionName} 보리 박사에게 낡은 수첩 묻기"
            else -> "메인 ${s.mainQuestStage}/${MainStory.CHAPTERS.size - 1} · ${chapter?.title ?: ""}"
        }
        c.drawText(mainTitle, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 18f), textP)
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFF796653.toInt()
        val objective = when {
            s.mainQuestFinished -> "Lv.${Progression.MAX_LEVEL}에서 이야기는 멈춤 · 아래 컬렉션과 사진 의뢰는 계속 가능"
            !s.mainQuestStarted -> "카드를 누르면 자전거를 타고 박사가 있는 곳까지 직접 달려가요."
            else -> chapter?.objective(s) ?: ""
        }
        val objectiveLines = scene.game.hud.wrapText(objective, textP, mainR.width() - dp(scene, 20f)).take(2)
        objectiveLines.forEachIndexed { i, line ->
            c.drawText(line, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 36f + i * 12f), textP)
        }
        val missingMainSpecies = if (s.mainQuestStarted) {
            chapter?.collectionDef()?.species?.filterNot { s.hasBirdName(it) }.orEmpty()
        } else emptyList()
        if (missingMainSpecies.isNotEmpty()) {
            textP.textSize = textDp(scene, 9.2f)
            textP.color = 0xFF8A5A33.toInt()
            var missingLine = "모을 새: ${missingMainSpecies.take(4).joinToString("·")}" +
                if (missingMainSpecies.size > 4) " 외" else ""
            val missingMaxW = mainR.width() - dp(scene, 20f)
            while (missingLine.length > 4 && textP.measureText(missingLine) > missingMaxW) missingLine = missingLine.dropLast(1)
            c.drawText(missingLine, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 61f), textP)
        }
        advice?.let { adv ->
            // 추천 위치 한 줄 — "어디로 가야 하는지"를 카드에 직접 보여준다
            val here = adv.alreadyThere || adv.regionId == s.region
            var rec = if (here) {
                "${adv.regionName} · ${adv.reason}"
            } else {
                "${adv.regionName} · ${adv.reason}"
            }
            textP.textSize = dp(scene, 10f)
            textP.color = if (here) 0xFF397547.toInt() else 0xFFB5651D.toInt()
            val maxRecW = mainR.width() - dp(scene, 20f)
            while (rec.length > 4 && textP.measureText(rec) > maxRecW) rec = rec.dropLast(1)
            c.drawText(rec, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 74f), textP)
            btnRects.add(Triple(mainR, "main_auto") { autoGoMainQuest() })
        }
        val side = if (s.activeQuests.isEmpty()) {
            "탐조 의뢰: 없음 · ${NpcRoster.professorRegionName} 보리 박사에게서 수락 (최대 3개)"
        } else {
            val first = s.activeQuests.first()
            val extra = if (s.activeQuests.size > 1) " 외 ${s.activeQuests.size - 1}개" else ""
            "탐조 의뢰(${s.activeQuests.size}/3): ${first.title}$extra"
        }
        c.drawText(side, mainR.left + dp(scene, 10f), mainR.bottom - dp(scene, 7f), textP)
        y = mainR.bottom + dp(scene, 7f)

        // 라이퍼 기반 탐조 이정표. 실제 자격제도가 아님을 UI에서 명시한다.
        val lifers = s.birdCounts.size
        val rank = BirdingRanks.of(lifers)
        val next = BirdingRanks.next(lifers)
        textP.textSize = textDp(scene, 12f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("라이퍼 ${lifers}종 · 게임 탐조 등급 「${rank.name}」", left, y + dp(scene, 13f), textP)
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        val rankHint = if (next != null) "다음 ${next.name}까지 ${next.min - lifers}종 · 공식 자격이 아닌 수집 이정표" else "400종 이상 · 공식 자격이 아닌 수집 이정표"
        c.drawText(rankHint, right - textP.measureText(rankHint), y + dp(scene, 13f), textP)
        y += dp(scene, 19f)

        // 서브탭 전환 버튼: [도장 깨기 (70)] / [탐조 의뢰 & 일일 미션]
        val tabW = (right - left - dp(scene, 6f)) / 2f
        val tabH = dp(scene, 20f)
        val tab0R = RectF(left, y, left + tabW, y + tabH)
        val tab1R = RectF(left + tabW + dp(scene, 6f), y, right, y + tabH)

        val activeCount = s.activeQuests.size + s.dailyQuests.count { !it.completed }
        if (questSubTab == 0) {
            cuteBtn(c, tab0R, "도장 깨기 (${BirdingCollections.ALL.size})", UiKit.PASTEL_SKY, UiKit.INK, 10f)
            UiKit.cuteButton(c, scene.game, tab1R, "의뢰 · 일일 ($activeCount)", UiKit.PASTEL_SAND, UiKit.MUTED, 10f, depthDp = 1.5f)
        } else {
            UiKit.cuteButton(c, scene.game, tab0R, "도장 깨기 (${BirdingCollections.ALL.size})", UiKit.PASTEL_SAND, UiKit.MUTED, 10f, depthDp = 1.5f)
            cuteBtn(c, tab1R, "의뢰 · 일일 ($activeCount)", UiKit.PASTEL_SKY, UiKit.INK, 10f)
        }
        btnRects.add(Triple(tab0R, "quest_sub_0") { questSubTab = 0 })
        btnRects.add(Triple(tab1R, "quest_sub_1") { questSubTab = 1 })
        y += tabH + dp(scene, 5f)

        if (questSubTab == 0) {
            val rowsBottom = contentBottom() - dp(scene, 26f)
            val perPage = ((rowsBottom - y) / dp(scene, 38f)).toInt().coerceIn(1, 5)
            val pages = (BirdingCollections.ALL.size + perPage - 1) / perPage
            questPage = questPage.coerceIn(0, pages - 1)
            val sets = BirdingCollections.ALL.drop(questPage * perPage).take(perPage)
            val rowH = (rowsBottom - y) / perPage
            for (set in sets) {
                val done = set.complete(s)
                val r = RectF(left, y, right, y + rowH - dp(scene, 4f))
                cuteCard(c, r, if (done) UiKit.PASTEL_MINT else 0xFFFBF1DE.toInt(),
                    if (done) 0xFF7FB37A.toInt() else UiKit.BROWN_LINE, 1.5f, stitched = rowH >= dp(scene, 30f))
                if (done) {
                    // 완료 도장 (빨간 동그라미 스탬프)
                    strokeP.color = Color.argb(180, 226, 87, 76)
                    strokeP.strokeWidth = dp(scene, 1.6f)
                    c.drawCircle(r.right - dp(scene, 18f), r.centerY(), dp(scene, 9f), strokeP)
                    textP.textSize = textDp(scene, 9f)
                    textP.color = Color.argb(200, 226, 87, 76)
                    c.drawText("완료", r.right - dp(scene, 18f) - textP.measureText("완료") / 2f, r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
                }
                textP.textSize = textDp(scene, 11.5f)
                textP.color = if (done) 0xFF397547.toInt() else 0xFF5D4938.toInt()
                UiKit.icon(c, scene.game, set.icon, RectF(r.left + dp(scene, 7f), r.top + dp(scene, 4f), r.left + dp(scene, 21f), r.top + dp(scene, 18f)))
                if (done) UiKit.icon(c, scene.game, "check", RectF(r.left + dp(scene, 22f), r.top + dp(scene, 5f), r.left + dp(scene, 32f), r.top + dp(scene, 15f)))
                c.drawText("${set.name}  ${set.progress(s)}", r.left + dp(scene, if (done) 35f else 25f), r.top + dp(scene, 15f), textP)
                textP.textSize = textDp(scene, 8.8f)
                textP.color = 0xFF8A7360.toInt()
                val missing = set.species.filterNot { s.hasBirdName(it) }
                val detail = if (missing.isEmpty()) set.note else "남은 새: ${missing.take(4).joinToString("·")}" + if (missing.size > 4) " 외" else ""
                c.drawText(detail, r.left + dp(scene, 8f), r.bottom - dp(scene, 6f), textP)
                y += rowH
            }

            val prevR = RectF(left, contentBottom() - dp(scene, 24f), left + dp(scene, 80f), contentBottom())
            val nextR = RectF(right - dp(scene, 80f), contentBottom() - dp(scene, 24f), right, contentBottom())
            if (questPage > 0) {
                cuteBtn(c, prevR, "‹ 이전", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
                btnRects.add(Triple(prevR, "quest_prev") { questPage-- })
            }
            textP.textSize = textDp(scene, 10f)
            textP.color = 0xFF8A7360.toInt()
            val pageText = "도장 깨기 ${questPage + 1}/$pages"
            c.drawText(pageText, panelR.centerX() - textP.measureText(pageText) / 2, contentBottom() - dp(scene, 7f), textP)
            if (questPage < pages - 1) {
                cuteBtn(c, nextR, "다음 ›", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
                btnRects.add(Triple(nextR, "quest_next") { questPage++ })
            }
        } else {
            // 고정 4행에 밀어 넣거나 take(2)로 세 번째 의뢰를 숨기지 않는다.
            QuestManager.ensureDailyQuests(s)
            data class TaskRow(val title: String, val progress: String, val description: String,
                               val done: Boolean, val buttonId: String, val action: (() -> Unit)?)
            val tasks = s.activeQuests.map { q ->
                TaskRow("서브 · [${q.category.label}] ${q.title}",
                    "${q.progressText} · +₩${fmtMoney(q.rewardMoney)}", QuestNavigation.requirementText(q),
                    q.isComplete, "quest_${q.id}", {
                        QuestNavigation.startQuest(scene.game, scene, q)
                        finished = true
                    })
            } + s.dailyQuests.map { q ->
                TaskRow("일일 · ${q.title}",
                    "${q.progressText} · +₩${fmtMoney(q.rewardMoney)}", QuestNavigation.requirementText(q),
                    q.isComplete, "daily_${q.id}", if (q.completed) null else ({
                        QuestNavigation.startDailyQuest(scene.game, scene, q)
                        finished = true
                    }))
            }
            val availableH = contentBottom() - dp(scene, 26f) - y
            val perPage = (availableH / dp(scene, 42f)).toInt().coerceIn(1, 6)
            val pages = ((tasks.size + perPage - 1) / perPage).coerceAtLeast(1)
            questTaskPage = questTaskPage.coerceIn(0, pages - 1)
            val rowH = minOf(dp(scene, 56f), availableH / perPage)
            for (task in tasks.drop(questTaskPage * perPage).take(perPage)) {
                val r = RectF(left, y, right, y + rowH - dp(scene, 3f))
                cuteCard(c, r, if (task.done) UiKit.PASTEL_MINT else UiKit.CARD_HI,
                    if (task.done) 0xFF7FB37A.toInt() else UiKit.BROWN_LINE, 1.3f)
                textP.color = if (task.done) 0xFF397547.toInt() else UiKit.INK
                drawFitText(c, scene, task.title, left + dp(scene, 8f), r.top + dp(scene, 13f), r.width() - dp(scene, 16f), 10.5f)
                textP.color = 0xFFB5651D.toInt()
                drawFitText(c, scene, (if (task.done) "완료! · " else "") + task.progress,
                    left + dp(scene, 8f), r.top + dp(scene, 25f), r.width() - dp(scene, 16f), 9.5f)
                textP.color = UiKit.MUTED
                drawFitText(c, scene, task.description, left + dp(scene, 8f), r.bottom - dp(scene, 4f), r.width() - dp(scene, 16f), 8.5f)
                task.action?.let { btnRects.add(Triple(r, task.buttonId, it)) }
                y += rowH
            }
            val prev = RectF(left, contentBottom() - dp(scene, 24f), left + dp(scene, 80f), contentBottom())
            val next = RectF(right - dp(scene, 80f), prev.top, right, contentBottom())
            if (questTaskPage > 0) {
                cuteBtn(c, prev, "‹ 이전", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
                btnRects.add(Triple(prev, "task_prev") { questTaskPage-- })
            }
            if (questTaskPage < pages - 1) {
                cuteBtn(c, next, "다음 ›", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
                btnRects.add(Triple(next, "task_next") { questTaskPage++ })
            }
            textP.textSize = textDp(scene, 9.5f)
            textP.color = UiKit.MUTED
            val label = "서브 ${s.activeQuests.size}/3 · 일일 ${s.dailyQuests.size} · ${questTaskPage + 1}/$pages"
            c.drawText(label, panelR.centerX() - textP.measureText(label) / 2f, contentBottom() - dp(scene, 7f), textP)
        }
    }


    private fun drawGrow(c: Canvas) {
        val g = scene.game
        val s = g.state
        val left = panelR.left + dp(scene, 16f)
        val right = panelR.right - dp(scene, 16f)
        var ty = contentTop() + dp(scene, 6f)

        // 레벨 / 칭호 — 아바타는 HD 세트(96px)를 32 도트 크기로 되돌려 그린다
        val a = g.assets
        val ps = a.playerSet(s.gender, s.gearTier(), true)
        val avatar = ps.idle.frame(Dir.S, (g.time / Anim.IDLE.frameTime).toInt())
        val ak = dp(scene, 2.2f) * CharacterArt.SIZE
        a.drawPlayerIn(c, avatar, RectF(left, ty, left + ak, ty + ak))

        val tx = left + ak + dp(scene, 12f)
        textP.textSize = textDp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("Lv.${s.level}  ${s.title()}", tx, ty + dp(scene, 16f), textP)

        // 경험치 바
        val bx = tx
        val bw = right - tx
        val byBar = ty + dp(scene, 24f)
        val bh = dp(scene, 11f)
        if (s.level < Progression.MAX_LEVEL) {
            UiKit.bar(c, g, bx, byBar, bw, bh, s.expProgress(), 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        } else {
            UiKit.bar(c, g, bx, byBar, bw, bh, 1f, 0xFFFFD97A.toInt(), 0xFFF2B63C.toInt())
        }
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val expTxt = if (s.level >= Progression.MAX_LEVEL) "최고 레벨 달성!" else "경험치 ${s.exp} / ${s.expToNext()}"
        c.drawText(expTxt, bx, byBar + bh + dp(scene, 12f), textP)

        // 숙련 포인트
        textP.textSize = textDp(scene, 12.5f)
        textP.color = if (s.skillPoints > 0) 0xFF3F6FB0.toInt() else 0xFF8A7360.toInt()
        val spTxt = "숙련 포인트(SP): ${s.skillPoints}"
        c.drawText(spTxt, tx, byBar + bh + dp(scene, 28f), textP)

        // 돈으로 SP 구매 (탐조 강습)
        val tc = s.trainingCost()
        val trR = RectF(right - dp(scene, 150f), byBar + bh + dp(scene, 16f), right, byBar + bh + dp(scene, 40f))
        if (s.money >= tc) {
            cuteBtn(c, trR, "trophy 탐조 강습 ₩${fmtMoney(tc)}", UiKit.GOLD, 0xFF4A2E12.toInt(), 10.5f)
            btnRects.add(Triple(trR, "train") {
                val cost = scene.game.state.trainingCost()
                if (scene.game.state.money >= cost) {
                    scene.game.state.money -= cost
                    scene.game.state.skillPoints += 1
                    SaveManager.save(scene.game.context, scene.game.state)
                    scene.game.toast("탐조 강습 수료! 숙련 포인트 +1")
                } else {
                    scene.game.toast("돈이 부족해요!")
                }
            })
        } else {
            cuteBtnOff(c, trR, "강습 ₩${fmtMoney(tc)}", 10.5f)
        }

        ty = byBar + bh + dp(scene, 46f)

        // 스킬 목록 (가로로 넉넉한 1줄 카드)
        val rowH = ((contentBottom() - ty) / Skills.ALL.size).coerceIn(dp(scene, 28f), dp(scene, 46f))
        for (def in Skills.ALL) {
            val rank = s.skillRank(def.id)
            val r = RectF(left, ty, right, ty + rowH - dp(scene, 5f))
            val maxed = rank >= def.maxRank
            cuteCard(c, r, if (maxed) UiKit.PASTEL_LEMON else 0xFFFDF6E8.toInt(),
                if (maxed) UiKit.GOLD_DEEP else UiKit.BROWN_LINE, 1.5f, stitched = rowH >= dp(scene, 34f))

            val midY = r.centerY() - (textP.descent() + textP.ascent()) / 2f

            // 스킬 아이콘은 민트 동그라미 배지 안에
            UiKit.iconCircle(c, g, r.left + dp(scene, 19f), r.centerY(), dp(scene, 11.5f), def.emoji, 12.5f,
                if (maxed) UiKit.GOLD else UiKit.PASTEL_MINT)

            textP.textSize = textDp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(def.name, r.left + dp(scene, 38f), midY, textP)

            textP.textSize = textDp(scene, 10f)
            textP.color = 0xFF6FAE6F.toInt()
            c.drawText("+${def.perRank}", r.left + dp(scene, 130f), midY, textP)

            // 랭크 표시 — 픽셀 하트 칸 (채운 칸 / 빈 칸)
            val pip = dp(scene, 9f)
            val pipGap = dp(scene, 3f)
            val pipsW = def.maxRank * pip + (def.maxRank - 1) * pipGap
            val pipsX = r.right - dp(scene, 96f) - pipsW - dp(scene, 8f)
            for (k in 0 until def.maxRank) {
                val pr = RectF(pipsX + k * (pip + pipGap), r.centerY() - pip / 2f, pipsX + k * (pip + pipGap) + pip, r.centerY() + pip / 2f)
                if (k < rank) {
                    UiKit.pixelFillGradient(c, pr, dp(scene, 1.2f), 0xFFFFD97A.toInt(), 0xFFE9A33C.toInt())
                    UiKit.pixelStroke(c, pr, dp(scene, 1.2f), UiKit.OUTLINE, dp(scene, 1.1f))
                    fillP.color = Color.argb(190, 255, 255, 255)
                    c.drawRect(pr.left + dp(scene, 1.5f), pr.top + dp(scene, 1.5f), pr.left + dp(scene, 3f), pr.top + dp(scene, 3f), fillP)
                } else {
                    UiKit.pixelFill(c, pr, dp(scene, 1.2f), 0xFFEADFC8.toInt())
                    UiKit.pixelStroke(c, pr, dp(scene, 1.2f), Color.argb(150, 180, 150, 110), dp(scene, 1f))
                }
            }

            // 강화 버튼
            val br = RectF(r.right - dp(scene, 92f), r.centerY() - dp(scene, 13f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 11f))
            when {
                maxed -> UiKit.badge(c, g, RectF(br.left, br.centerY() - dp(scene, 10f), br.right, br.centerY() + dp(scene, 10f)), "MAX", UiKit.GOLD, 0xFF4A2E12.toInt(), 11f)
                s.skillPoints <= 0 -> cuteBtnOff(c, br, "SP 필요", 10.5f)
                else -> {
                    cuteBtn(c, br, "강화 SP1", UiKit.PASTEL_MINT, UiKit.INK, 11f)
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

    /** 피자 탭에서 보고 있는 계열 (화덕피자 / 일반 피자) — 메뉴를 닫았다 열어도 유지 */
    private var pizzaKindTab: PizzaKind = lastPizzaKindTab

    private fun drawPizza(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        var ty = contentTop()
        val kind = pizzaKindTab

        // 헤더 — 피자 가방 게이지 + 계열 서브탭 + 선택한 계열의 조리기구 일러스트
        val headH = dp(scene, 44f)
        val headR = RectF(left, ty, right, ty + headH)
        cuteCard(c, headR, UiKit.lighten(kind.tint, 70), UiKit.darken(kind.tint, 20), 1.6f)
        textP.textSize = textDp(scene, 12f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("피자 가방", left + dp(scene, 10f), ty + dp(scene, 16f), textP)
        textP.textSize = textDp(scene, 11f)
        textP.color = 0xFFB5651D.toInt()
        val capEff = s.pizzaCapEff()
        val capTxt = "${s.pizzaCount}/$capEff"
        val gaugeW = dp(scene, 120f)
        c.drawText(capTxt, left + dp(scene, 10f) + gaugeW - textP.measureText(capTxt), ty + dp(scene, 16f), textP)
        UiKit.bar(c, g, left + dp(scene, 10f), ty + dp(scene, 22f), gaugeW, dp(scene, 9f),
            s.pizzaCount / capEff.toFloat(), 0xFFFFD97A.toInt(), 0xFFF2A33C.toInt())
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        run {
            var descTxt = "${kind.station}에서 구워요 · ${kind.desc}"
            val maxDescW = gaugeW + dp(scene, 4f)
            textP.textSize = textDp(scene, 9f)
            if (textP.measureText(descTxt) > maxDescW) {
                while (descTxt.length > 1 && textP.measureText("$descTxt…") > maxDescW) descTxt = descTxt.dropLast(1)
                descTxt = "$descTxt…"
            }
            c.drawText(descTxt, left + dp(scene, 10f), ty + dp(scene, 41f), textP)
        }
        // 계열 서브탭 (가운데)
        val kinds = PizzaKind.values()
        val tabX0 = left + dp(scene, 10f) + gaugeW + dp(scene, 16f)
        val tabX1 = right - dp(scene, 62f)
        val tabW = ((tabX1 - tabX0) - dp(scene, 6f) * (kinds.size - 1)) / kinds.size
        for ((i, k) in kinds.withIndex()) {
            val r = RectF(tabX0 + i * (tabW + dp(scene, 6f)), ty + dp(scene, 7f), tabX0 + i * (tabW + dp(scene, 6f)) + tabW, ty + headH - dp(scene, 7f))
            val sel = k == kind
            val label = "${k.emoji} ${k.label} ${s.pizzaCountOfKind(k)}"
            if (sel) cuteBtn(c, r, label, k.tint, 0xFFFFFBF0.toInt(), 11f)
            else UiKit.cuteButton(c, g, r, label, UiKit.PASTEL_SAND, UiKit.MUTED, 11f, depthDp = 2f)
            btnRects.add(Triple(r, k.label) {
                pizzaKindTab = k
                lastPizzaKindTab = k
            })
        }
        drawStationArt(c, scene, kind, RectF(right - dp(scene, 52f), ty + dp(scene, 2f), right - dp(scene, 6f), ty + dp(scene, 42f)))
        ty += headH + dp(scene, 6f)

        // 피자 카드 — 2열 × 3행, 남은 높이에 맞춰 자동 배분
        val list = Pizzas.ofKind(kind)
        val cols = 2
        val rows = (list.size + cols - 1) / cols
        val gap = dp(scene, 6f)
        val cellW = (right - left - gap * (cols - 1)) / cols
        val cellH = ((contentBottom() - ty - gap * (rows - 1)) / rows).coerceAtLeast(dp(scene, 46f))
        for ((i, p) in list.withIndex()) {
            val col = i % cols
            val row = i / cols
            val r = RectF(
                left + col * (cellW + gap), ty + row * (cellH + gap),
                left + col * (cellW + gap) + cellW, ty + row * (cellH + gap) + cellH
            )
            val total = s.pizzaCountOf(p.id)
            cuteCard(c, r, if (total > 0) UiKit.lighten(kind.tint, 84) else UiKit.CARD_HI,
                if (total > 0) kind.tint else UiKit.BROWN_LINE, if (total > 0) 2f else 1.5f, stitched = cellH >= dp(scene, 44f))

            // 피자 아이콘 (종류별) — 칸이 크면 피자도 크게, 보유 중이면 접시 후광
            val artW = minOf(dp(scene, 60f), cellH - dp(scene, 12f)).coerceAtLeast(dp(scene, 30f))
            if (total > 0) {
                fillP.color = Color.argb(70, 255, 255, 255)
                c.drawCircle(r.left + dp(scene, 8f) + artW / 2f, r.centerY(), artW * 0.56f, fillP)
            }
            drawPizzaArt(c, scene, p.id, r.left + dp(scene, 8f) + artW / 2f, r.centerY(), artW)

            val tx = r.left + dp(scene, 8f) + artW + dp(scene, 8f)
            val btnW = dp(scene, 58f)
            val textW = r.right - dp(scene, 10f) - btnW - tx
            // 글 블록(약 46dp)을 칸 세로 가운데에 맞춘다
            val top0 = r.centerY() - dp(scene, 23f)
            // 이름 + 보유 뱃지
            textP.color = if (total > 0) 0xFF4A3728.toInt() else 0xFF8A7360.toInt()
            val nameTxt = "${p.emoji} ${p.name}"
            textP.textSize = textDp(scene, 9.5f)
            val badgeTxt = Ingredients.toppingOfPizza(p.id)?.let { "✈ ${it.regionName} 특산" } ?: "×$total"   // [P07]
            val badgeW = textP.measureText(badgeTxt) + dp(scene, 12f)
            drawFitText(c, scene, nameTxt, tx, top0 + dp(scene, 16f), textW - badgeW - dp(scene, 6f), 12f)
            val nameW = textP.measureText(nameTxt)
            UiKit.badge(
                c, g, RectF(tx + nameW + dp(scene, 6f), top0 + dp(scene, 5f), tx + nameW + dp(scene, 6f) + badgeW, top0 + dp(scene, 20f)),
                badgeTxt, if (total > 0) 0xFFF2B63C.toInt() else 0xFFCFC4B4.toInt(),
                if (total > 0) 0xFF4A2E12.toInt() else 0xFF6B5A48.toInt(), 9.5f
            )
            // 효과 + 난이도
            textP.color = 0xFF8A7360.toInt()
            val bonusTxt = "배고픔 ${if (p.hungerBonus >= 0) "+" else ""}${p.hungerBonus} · 행운 ${if (p.luckBonus >= 0) "+" else ""}${p.luckBonus} · ${p.difficultyDots()}"
            drawFitText(c, scene, bonusTxt, tx, top0 + dp(scene, 29f), textW, 9.5f)
            // 품질별 보유 — 등급 색상 텍스트
            if (cellH >= dp(scene, 50f)) {
                textP.textSize = textDp(scene, 8.5f)
                var cx = tx
                val cy = top0 + dp(scene, 41f)
                val segs = listOf(
                    "걸작×${s.pizzaCountOf(p.id, 2)}" to 0xFFB5651D.toInt(),
                    "맛있는×${s.pizzaCountOf(p.id, 1)}" to 0xFF4E8A4E.toInt(),
                    "탄×${s.pizzaCountOf(p.id, 0)}" to 0xFF9AA0A8.toInt()
                )
                for ((si, seg) in segs.withIndex()) {
                    textP.color = seg.second
                    c.drawText(seg.first, cx, cy, textP)
                    cx += textP.measureText(seg.first) + dp(scene, 7f)
                    if (si < 2) {
                        textP.color = 0xFFC9B896.toInt()
                        c.drawText("·", cx - dp(scene, 4.5f), cy, textP)
                    }
                }
            }

            // 먹기 버튼
            val br = RectF(r.right - dp(scene, 8f) - btnW, r.centerY() - dp(scene, 13f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 11f))
            val enabled = total > 0 && s.hunger < 100f
            if (enabled) {
                cuteBtn(c, br, "pizza 냠냠", UiKit.PASTEL_TOMATO, 0xFF4A2E12.toInt(), 11f)
                btnRects.add(Triple(br, p.name) {
                    val eaten = scene.game.state.eat(p.id)
                    if (eaten != null) {
                        scene.game.toast("냠냠! ${p.emoji} ${p.fullName} · ${eaten.label} (배고픔 +${eaten.hunger + p.hungerBonus}, 행운 +${eaten.luck + p.luckBonus})")
                    }
                })
            } else {
                cuteBtnOff(c, br, if (total <= 0) "없음" else "든든해", 10.5f)
            }
        }
    }

    companion object {
        /** 마지막으로 보던 피자 계열 탭 (메뉴를 닫았다 열어도 유지) */
        private var lastPizzaKindTab: PizzaKind = PizzaKind.OVEN
        /** 도감 표시 모드 (사진 모드 vs 도트 모드) */
        private var bookPhotoMode: Boolean = true

        /**
         * 도감 검색·정렬·묶기 상태. 탭을 나가도 유지된다 — "찾던 새"는 다음에 다시 열었을 때
         * 그대로 남아 있어야 하니까. (결정 로직은 전부 `DexQuery.kt` 에 있다)
         */
        private var dexQuery = ""
        private var dexSort = DexSort.NUM
        private var dexGroup = DexGroup.NONE
        private var dexKey: String? = null
        private var dexKeyboard = false
        /** 0 = 초성 / 1 = 중성 / 2 = 종성 / 3 = 영어 */
        private var dexKbPage = 0
        /** -1 = 닫힘 / 0 = 정렬 / 1 = 묶음 기준 / 2 = 묶음 키 */
        private var dexDrop = -1
        /** 속성으로 묶는 기준의 선택지 (모아 보기 드롭다운) */
        private val dexGroups = listOf(
            DexGroup.NONE, DexGroup.TIER, DexGroup.HABITAT, DexGroup.MIGRATION,
            DexGroup.SEASON, DexGroup.TIME, DexGroup.REGION, DexGroup.UNSEEN
        )
        private val dexInput = JamoInput()
    }

    private var bookRects: Map<String, RectF> = emptyMap()
    private var bookPage = 0
    private var albumPage = 0

    private fun bookCell(def: BirdDef): RectF? = bookRects[def.id]

    /** 도감 본문 위에 얹는 검색줄의 높이 — 드롭다운은 격자 위에 흐르므로 자리를 차지하지 않는다 */
    private fun dexCtrlH(): Float = dp(scene, 21f)

    /** 도감 하단 한글/영문 키보드 높이 — 열려 있을 때만 본문을 이만큼 깎는다 */
    private fun dexKbH(): Float = if (dexKeyboard) dp(scene, 92f) else 0f

    /** 도감 필터 칩 — 작게 붙는 선택 버튼. 글자가 넘치면 크기를 줄여 한 줄에 넣는다. */
    private fun dexChip(c: Canvas, r: RectF, label: String, on: Boolean, maxDp: Float = 9f) {
        var sz = maxDp
        textP.textSize = textDp(scene, sz)
        val maxW = r.width() - dp(scene, 8f)
        while (textP.measureText(label) > maxW && sz > 6.4f) {
            sz -= 0.3f
            textP.textSize = textDp(scene, sz)
        }
        UiKit.cuteButton(
            c, scene.game, r, label,
            if (on) UiKit.PASTEL_LEMON else Color.argb(118, 214, 204, 186),
            if (on) UiKit.INK else Color.argb(160, 74, 55, 40),
            sz, depthDp = 1.1f
        )
    }

    private fun dexKeyLabel(group: DexGroup, key: String): String =
        BirdIndex.keysOf(group, scene.game.state.region).firstOrNull { it.first == key }?.second ?: key

    /**
     * 도감 본문 위의 검색줄. **한 줄만** 차지하고, 선택지는 아래로 흘러내리는 드롭다운으로 연다
     * — 격자를 계속 찌그러뜨리지 않기 위한 선택.
     *
     * @return 이 줄이 차지한 높이
     */
    private fun drawDexControls(c: Canvas, y0: Float): Float {
        val g = scene.game
        val s = g.state
        val left = panelR.left + dp(scene, 14f)
        val areaW = panelR.width() - dp(scene, 28f)
        val right = left + areaW
        val h = dp(scene, 19f)
        val gap = dp(scene, 4f)
        var y = y0

        // ---- 한 줄: [검색] [정렬] [모아 보기] [지우기] ----
        val resetW = if (dexQuery.isEmpty() && dexGroup == DexGroup.NONE && dexSort == DexSort.NUM) 0f else dp(scene, 30f)
        val searchW = areaW * 0.36f
        val sortW = dp(scene, 58f)
        val groupW = (right - resetW - (if (resetW > 0f) gap else 0f)) - (left + searchW + gap + sortW + gap)
        val searchR = RectF(left, y, left + searchW, y + h)
        val sortR = RectF(searchR.right + gap, y, searchR.right + gap + sortW, y + h)
        val groupR = RectF(sortR.right + gap, y, sortR.right + gap + groupW, y + h)
        val resetR = if (resetW > 0f) RectF(right - resetW, y, right, y + h) else null

        val pill = if (dexQuery.isEmpty()) "search 이름·학명·초성" else "search $dexQuery${dexInput.preview}"
        dexChip(c, searchR, pill, dexQuery.isNotEmpty() || dexKeyboard)
        dexChip(c, sortR, "정렬 ${dexSort.label}", dexDrop == 0)
        val groupLabel = if (dexGroup == DexGroup.NONE) "모아 보기"
        else "${dexGroup.label}${dexKey?.let { " · " + dexKeyLabel(dexGroup, it) } ?: ""}"
        dexChip(c, groupR, groupLabel, dexDrop >= 1)
        if (resetR != null) {
            dexChip(c, resetR, "close", false)
            btnRects.add(Triple(resetR, "dex_reset") {
                dexQuery = ""; dexInput.clear(); dexGroup = DexGroup.NONE; dexKey = null
                dexSort = DexSort.NUM; dexDrop = -1; bookPage = 0
            })
        }
        btnRects.add(Triple(searchR, "dex_search") {
            dexKeyboard = !dexKeyboard
            dexDrop = -1
            if (!dexKeyboard) {
                val add = dexInput.commit()
                if (add.isNotEmpty()) dexQuery += add
                bookPage = 0
            }
        })
        btnRects.add(Triple(sortR, "dex_sort") { dexDrop = if (dexDrop == 0) -1 else 0 })
        btnRects.add(Triple(groupR, "dex_group") { dexDrop = if (dexDrop >= 1) -1 else 1 })
        y += h + dp(scene, 2f)

        // ---- 드롭다운 ----
        if (dexDrop >= 0) {
            val items: List<Pair<String, String>> = when (dexDrop) {
                0 -> DexSort.values().map { it.name to it.label + " · " + it.hint }
                1 -> dexGroups.map { it.name to it.label }
                else -> BirdIndex.keysOf(dexGroup, s.region)
            }
            if (items.isEmpty()) {
                dexDrop = -1
            } else {
                val rowH = dp(scene, 20f)
                val dropW = maxOf(areaW * 0.62f, dp(scene, 190f)).coerceAtMost(areaW)
                val dropR = RectF(left, y - dp(scene, 2f), left + dropW, y - dp(scene, 2f) + rowH * items.size + dp(scene, 4f))
                // 바깥을 누르면 닫힌다 — 먼저 넣어야(= 나중에 그려야) 항목 탭이 이긴다
                btnRects.add(Triple(RectF(panelR.left, y0, panelR.right, panelR.bottom), "dex_drop_bg") { dexDrop = -1 })
                UiKit.panel(c, g, dropR, 10f)
                val counts: Map<String, Int> = if (dexDrop == 2)
                    BirdIndex.counts(s, dexGroup, dexQuery, s.region) else emptyMap()
                var iy = dropR.top + dp(scene, 2f)
                for ((key, label) in items) {
                    val ir = RectF(dropR.left + dp(scene, 4f), iy, dropR.right - dp(scene, 4f), iy + rowH - dp(scene, 1f))
                    val on = when (dexDrop) {
                        0 -> dexSort.name == key
                        1 -> dexGroup.name == key
                        else -> dexKey == key
                    }
                    if (on) UiKit.stitchCard(c, g, ir, UiKit.PASTEL_LEMON, UiKit.BROWN_LINE, 1.1f, stitched = false, selected = true)
                    textP.textSize = textDp(scene, 9f)
                    textP.color = if (on) UiKit.INK else 0xFF6B5A48.toInt()
                    c.drawText(label, ir.left + dp(scene, 7f), ir.centerY() + dp(scene, 3.4f), textP)
                    if (dexDrop == 2) {
                        val cnt = (counts[key] ?: 0).toString()
                        textP.color = 0xFF8A7360.toInt()
                        c.drawText(cnt, ir.right - dp(scene, 8f) - textP.measureText(cnt), ir.centerY() + dp(scene, 3.4f), textP)
                    }
                    val kk = key
                    btnRects.add(Triple(ir, "dex_pick$kk") {
                        when (dexDrop) {
                            0 -> dexSort = DexSort.values().first { it.name == kk }
                            1 -> {
                                val gr = dexGroups.first { it.name == kk }
                                if (gr == DexGroup.NONE) { dexGroup = DexGroup.NONE; dexKey = null; dexDrop = -1 }
                                else { dexGroup = gr; dexKey = null; dexDrop = 2 }
                            }
                            else -> {
                                dexKey = if (dexKey == kk) null else kk
                                dexDrop = -1
                            }
                        }
                        bookPage = 0
                    })
                    iy += rowH
                }
            }
        }
        return h + dp(scene, 2f)
    }

    /**
     * 도감 전용 **자판 키보드** — 안드로이드 IME(EditText)를 쓰지 않는다.
     * 이 게임은 뷰를 전부 Canvas 에 그리고 있어, 입력창을 띄우면 포커스·회전이 얽힌다.
     * 그래서 초성→중성→종성 순서로 두드리면 한 글자가 조합되도록 직접 그린다 (`JamoInput`).
     */
    private fun drawDexKeyboard(c: Canvas) {
        val g = scene.game
        val left = panelR.left + dp(scene, 14f)
        val areaW = panelR.width() - dp(scene, 28f)
        val right = left + areaW
        val top = contentBottom() - dp(scene, 30f) - dexKbH()
        val kbR = RectF(left, top, right, top + dexKbH())
        UiKit.stitchCard(c, g, kbR, 0xFFF7EEDC.toInt(), UiKit.BROWN_LINE, 1.2f, stitched = true)

        val gap = dp(scene, 3f)
        val rowH = dp(scene, 24f)
        // ---- 위 칸: 입력 전환 + 미확정 글자 + 지우기/삭제/완료 ----
        val headH = dp(scene, 17f)
        val tabW = dp(scene, 34f)
        val tabs = listOf("초성", "중성", "종성", "Aa")
        var tx = kbR.left + dp(scene, 4f)
        for ((i, tl) in tabs.withIndex()) {
            val tr = RectF(tx, kbR.top + dp(scene, 3f), tx + tabW, kbR.top + dp(scene, 3f) + headH)
            dexChip(c, tr, tl, dexKbPage == i, 8.4f)
            btnRects.add(Triple(tr, "dex_kbtab$i") { dexKbPage = i })
            tx += tabW + gap
        }
        // 미리보기 (조합 중인 글자)
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF6B5A48.toInt()
        val pv = if (dexInput.preview.isEmpty()) "입력 중…" else "'" + dexInput.preview + "'"
        c.drawText("검색어 " + (if (dexQuery.isEmpty()) "(빈 칸)" else dexQuery) + " $pv",
            tx + dp(scene, 4f), kbR.top + dp(scene, 15.5f), textP)
        // 우측 기능키
        val doneW = dp(scene, 40f)
        val backW = dp(scene, 26f)
        val clrW = dp(scene, 26f)
        val doneR = RectF(kbR.right - doneW, kbR.top + dp(scene, 3f), kbR.right - dp(scene, 4f), kbR.top + dp(scene, 3f) + headH)
        val backR = RectF(doneR.left - gap - backW, doneR.top, doneR.left - gap, doneR.bottom)
        val clrR = RectF(backR.left - gap - clrW, doneR.top, backR.left - gap, doneR.bottom)
        dexChip(c, doneR, "완료", true, 8.4f)
        dexChip(c, backR, "arrow_left", false, 8.4f)
        dexChip(c, clrR, "close", false, 8.4f)
        btnRects.add(Triple(doneR, "dex_done") {
            val add = dexInput.commit()
            if (add.isNotEmpty()) dexQuery += add
            dexKeyboard = false
            bookPage = 0
        })
        btnRects.add(Triple(backR, "dex_back") {
            val n = dexInput.backspace()
            if (n > 0 && dexQuery.isNotEmpty()) dexQuery = dexQuery.dropLast(n)
            bookPage = 0
        })
        btnRects.add(Triple(clrR, "dex_clrall") { dexInput.clear(); dexQuery = ""; bookPage = 0 })

        // ---- 자판 2줄 ----
        val keys: String = when (dexKbPage) {
            0 -> Jamo.CHOSEONG
            1 -> Jamo.JUNGSEONG
            2 -> Jamo.JONGSEONG
            else -> "abcdefghijklmnopqrstuvwxyz"
        }
        val splitAt = when (dexKbPage) {
            0 -> 10
            1 -> 11
            2 -> 14
            else -> 13
        }
        val rows = listOf(keys.take(splitAt), keys.drop(splitAt))
        var ky = kbR.top + dp(scene, 3f) + headH + dp(scene, 4f)
        for ((ri, row) in rows.withIndex()) {
            if (row.isEmpty()) continue
            val n = row.length
            val kw = (areaW - dp(scene, 8f) - gap * (n - 1)) / n
            var kx = kbR.left + dp(scene, 4f)
            for ((ci, ch) in row.withIndex()) {
                val kr = RectF(kx, ky, kx + kw, ky + rowH)
                UiKit.cuteButton(c, g, kr, ch.toString(), UiKit.CREAM, UiKit.INK, if (n > 12) 9f else 11f, depthDp = 1.6f)
                val idx = ri * splitAt + ci
                btnRects.add(Triple(kr, "dex_key$idx") {
                    val add = when (dexKbPage) {
                        0 -> dexInput.tapCho(idx)
                        1 -> dexInput.tapJung(idx)
                        2 -> dexInput.tapJong(idx)
                        else -> dexInput.tapLatin(ch)
                    }
                    if (add != null) dexQuery += add
                    bookPage = 0
                })
                kx += kw + gap
            }
            ky += rowH + dp(scene, 3f)
        }
    }

    private fun drawBook(c: Canvas) {
        val g = scene.game
        val s = g.state
        val a = g.assets
        val cols = 3

        // 검색 · 정렬 · 모아 보기로 잘려 나간 목록 — 이 뷰가 곧 도감이 그리는 순서다
        val view = BirdIndex.view(s, dexQuery, dexSort, dexGroup, dexKey, s.region)
        val filtering = dexQuery.isNotEmpty() || dexGroup != DexGroup.NONE || dexSort != DexSort.NUM

        // 화면이 낮으면 4→3→2줄로 자동 전환 (셀이 찌그러지지 않게), 키보드가 열리면 한 번 더 줄인다
        val rawAreaH = contentBottom() - (contentTop() + dp(scene, 38f) + dexCtrlH()) -
            dp(scene, 30f) - dexKbH()
        val rowsPerPage = when {
            rawAreaH < dp(scene, 100f) -> 2
            rawAreaH < dp(scene, 152f) -> 3
            else -> 4
        }
        val pageSize = cols * rowsPerPage
        val totalPages = ((view.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        bookPage = bookPage.coerceIn(0, totalPages - 1)

        val areaLeft = panelR.left + dp(scene, 14f)
        val areaW = panelR.width() - dp(scene, 28f)

        // 헤더 — 도감 완성도 게이지
        val done = s.birdCounts.size
        val total = Birds.ALL.size
        textP.textSize = textDp(scene, 11.5f)
        textP.color = 0xFF4A3728.toInt()
        val headLine = if (filtering) "도감 $done/${total}종 · 결과 ${view.size}종" else "도감 $done/${total}종"
        c.drawText(headLine, areaLeft, contentTop() + dp(scene, 8f), textP)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFFB5651D.toInt()
        val pctTxt = "${(done * 100f / total).toInt()}% 완성!"
        c.drawText(pctTxt, areaLeft + areaW - textP.measureText(pctTxt), contentTop() + dp(scene, 8f), textP)
        UiKit.bar(c, g, areaLeft, contentTop() + dp(scene, 13f), areaW, dp(scene, 8f),
            done / total.toFloat(), 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        textP.textSize = textDp(scene, 8.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(OfficialBirdChecklist.SOURCE_TITLE, areaLeft, contentTop() + dp(scene, 33f), textP)

        // 도감 모드 전환 버튼 (사진 썸네일 vs 도트 스프라이트)
        val modeLabel = if (bookPhotoMode) "photo 사진 모드" else "bird 도트 모드"
        textP.textSize = textDp(scene, 8.2f)
        val modeBtnW = textP.measureText(modeLabel) + dp(scene, 16f)
        val modeBtnH = dp(scene, 17f)
        val modeBtnR = RectF(areaLeft + areaW - modeBtnW, contentTop() + dp(scene, 21f), areaLeft + areaW, contentTop() + dp(scene, 21f) + modeBtnH)
        drawButton(c, scene, modeBtnR, modeLabel, 0xFFF2E3C2.toInt(), 0xFF5A4430.toInt(), 8.5f)
        btnRects.add(Triple(modeBtnR, modeLabel) {
            bookPhotoMode = !bookPhotoMode
            g.sfx(Audio.Sfx.TAP, 0.45f)
        })

        val areaTop0 = contentTop() + dp(scene, 38f)
        val areaTop = areaTop0 + drawDexControls(c, areaTop0)
        val pagerH = dp(scene, 30f)
        val areaH = contentBottom() - areaTop - pagerH - dexKbH()
        val gap = dp(scene, 6f)
        val cw = (areaW - gap * (cols - 1)) / cols
        val chh = ((areaH - gap * (rowsPerPage - 1)) / rowsPerPage).coerceAtLeast(dp(scene, 26f))

        val rects = LinkedHashMap<String, RectF>()
        val startIndex = bookPage * pageSize
        // 검색·정렬·묶기를 통과한 목록 (`BirdIndex`) 그대로 페이지를 자른다.
        // "촬영한 종을 먼저" 는 정렬 방법 `DexSort.PHOTO_FIRST` 로 살아 있다 (v0.5).
        val pageItems = view.drop(startIndex).take(pageSize)
        // 이 페이지의 사진을 미리 받는다 (디코드는 로더 스레드가, 여기는 그리기만)
        for (def in pageItems) a.prefetchBirdThumb(def.birdNum)
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
            cuteCard(c, r, if (seen) UiKit.lighten(tierColor, 118) else UiKit.CARD_HI,
                if (seen) tierColor else Color.argb(200, 201, 168, 123), if (seen) 2f else 1.2f, stitched = false)

            var tx = r.left + dp(scene, 8f)

            if (bookPhotoMode) {
                // [사진 모드]: 각 셀에 사진 썸네일 액자
                val thumbBoxSize = chh - dp(scene, 8f)
                val thumbR = RectF(
                    r.left + dp(scene, 4f), r.top + dp(scene, 4f),
                    r.left + dp(scene, 4f) + thumbBoxSize, r.top + dp(scene, 4f) + thumbBoxSize
                )
                fillP.color = 0xFF2A231D.toInt()
                c.drawRoundRect(thumbR, dp(scene, 4f), dp(scene, 4f), fillP)

                val thumb = a.birdThumb(def.birdNum)
                if (thumb != null) {
                    val saveCount = c.save()
                    c.clipRect(thumbR)
                    val scale = Math.max(thumbR.width() / thumb.width.toFloat(), thumbR.height() / thumb.height.toFloat())
                    val tw = thumb.width * scale
                    val th = thumb.height * scale
                    val tdx = thumbR.left + (thumbR.width() - tw) / 2f
                    val tdy = thumbR.top + (thumbR.height() - th) / 2f
                    c.drawBitmap(thumb, null, RectF(tdx, tdy, tdx + tw, tdy + th), a.sprPaint)

                    // 미촬영 종은 부드러운 딤 레이어 + ? 표시 (실루엣 보존)
                    if (!seen) {
                        fillP.color = Color.argb(145, 30, 25, 20)
                        c.drawRect(thumbR, fillP)
                        textP.textSize = textDp(scene, 9.5f)
                        textP.color = 0xFFF8EFDC.toInt()
                        c.drawText("?", thumbR.centerX() - textP.measureText("?") / 2f, thumbR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
                    }
                    c.restoreToCount(saveCount)
                } else {
                    val bmp = a.bird(def.id)
                    val k = (thumbBoxSize / bmp.height).coerceAtMost(dp(scene, 0.85f))
                    val bx = thumbR.centerX() - bmp.width * k / 2f
                    val by = thumbR.centerY() - bmp.height * k / 2f
                    c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)
                }

                strokeP.color = if (seen) Color.argb(160, 218, 175, 110) else Color.argb(70, 100, 90, 80)
                strokeP.strokeWidth = dp(scene, 1f)
                c.drawRoundRect(thumbR, dp(scene, 4f), dp(scene, 4f), strokeP)

                tx = thumbR.right + dp(scene, 6f)
            } else {
                // [도트 모드]: 기존 픽셀 도트 스프라이트
                val bmp = a.bird(def.id)
                val maxH = chh - dp(scene, 8f)
                val k = (maxH / bmp.height).coerceAtMost(dp(scene, 0.95f))
                val bx = r.left + dp(scene, 4f)
                val by = r.centerY() - bmp.height * k / 2f
                if (seen) {
                    fillP.color = Color.argb(40, Color.red(tierColor), Color.green(tierColor), Color.blue(tierColor))
                    c.drawCircle(bx + bmp.width * k / 2f, r.centerY(), dp(scene, 15f), fillP)
                    c.drawBitmap(bmp, null, RectF(bx, by, bx + bmp.width * k, by + bmp.height * k), a.sprPaint)
                } else {
                    fillP.color = Color.argb(95, 150, 140, 130)
                    c.drawCircle(bx + bmp.width * k / 2f, r.centerY(), dp(scene, 7f), fillP)
                    textP.textSize = textDp(scene, 9.5f)
                    textP.color = 0xFFF8EFDC.toInt()
                    c.drawText("?", bx + bmp.width * k / 2f - textP.measureText("?") / 2f, r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
                }
                tx = bx + bmp.width * k + dp(scene, 5f)
            }

            val top0 = r.centerY() - dp(scene, 20f)
            textP.textSize = textDp(scene, 10.2f)
            textP.color = if (seen) 0xFF4A3728.toInt() else Color.argb(175, 74, 55, 40)
            c.drawText(def.name, tx, top0 + dp(scene, 12f), textP)
            textP.textSize = textDp(scene, 8.2f)
            textP.color = 0xFF8A7360.toInt()
            val baseLine2 = if (seen) {
                val best = s.bestStars[def.id] ?: 1
                "사진 ${s.birdCounts[def.id]}회 · 최고 ${best}점"
            } else {
                "미촬영 · ${def.tier.label}"
            }
            val line2 = baseLine2 + if (def.active == "night") " · 야간" else ""
            c.drawText(line2, tx, top0 + dp(scene, 24f), textP)
            textP.textSize = textDp(scene, 7.8f)
            textP.color = Color.argb(170, 94, 76, 58)
            val familyLabel = if (def.familyName.isBlank()) "공식 목록" else def.familyName
            c.drawText(familyLabel, tx, top0 + dp(scene, 35f), textP)
            rects[def.id] = r
        }
        bookRects = rects

        fun pagerButton(rect: RectF, label: String, enabled: Boolean, action: () -> Unit) {
            if (enabled) {
                cuteBtn(c, rect, label, UiKit.PASTEL_LILAC, UiKit.INK, 11.5f)
                btnRects.add(Triple(rect, label, action))
            } else {
                cuteBtnOff(c, rect, label, 11.5f)
            }
        }

        if (view.isEmpty()) {
            val er = RectF(areaLeft, areaTop, areaLeft + areaW, contentBottom() - pagerH - dp(scene, 6f))
            UiKit.stitchCard(c, g, er, UiKit.CREAM, UiKit.BROWN_LINE, 1.3f, stitched = false)
            textP.textSize = textDp(scene, 11f)
            textP.color = 0xFF6B5A48.toInt()
            val why = when {
                dexQuery.isNotEmpty() -> "\'" + dexQuery + "\' 에 걸리는 새가 없어요"
                dexKey != null -> "이 속성엔 아직 기록할 새가 없어요"
                else -> "도감이 비어 있어요"
            }
            c.drawText(why, er.centerX() - textP.measureText(why) / 2f, er.centerY() - dp(scene, 6f), textP)
            textP.textSize = textDp(scene, 9f)
            textP.color = 0xFF8A7360.toInt()
            val how = "왼쪽 위 \'지우기\' 를 누르면 598종 전체로 돌아갑니다"
            c.drawText(how, er.centerX() - textP.measureText(how) / 2f, er.centerY() + dp(scene, 11f), textP)
        }

        if (dexKeyboard) drawDexKeyboard(c)

        val py = contentBottom() - dp(scene, 25f)
        val prev = RectF(areaLeft, py, areaLeft + dp(scene, 82f), py + dp(scene, 22f))
        val next = RectF(areaLeft + areaW - dp(scene, 82f), py, areaLeft + areaW, py + dp(scene, 22f))
        pagerButton(prev, "arrow_left 이전", bookPage > 0) { bookPage-- }
        pagerButton(next, "arrow_right 다음", bookPage < totalPages - 1) { bookPage++ }
        val pageText = "${bookPage + 1} / $totalPages"
        textP.textSize = textDp(scene, 11.5f)
        val pillW = textP.measureText(pageText) + dp(scene, 22f)
        UiKit.badge(
            c, g, RectF(panelR.centerX() - pillW / 2f, py, panelR.centerX() + pillW / 2f, py + dp(scene, 22f)),
            pageText, 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 11.5f
        )
    }

    /** 촬영할 때마다 실제 월드 배경과 방향별 새가 자동으로 쌓이는 사진집. */
    private fun drawAlbum(c: Canvas) {
        val g = scene.game
        val records = g.state.photoAlbum.asReversed()
        val left = panelR.left + dp(scene, 14f)
        val right = panelR.right - dp(scene, 14f)
        val width = right - left

        textP.textSize = dp(scene, 13f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("나의 새 사진집", left, contentTop() + dp(scene, 10f), textP)
        textP.textSize = dp(scene, 9.2f)
        textP.color = 0xFF8A7360.toInt()
        val countText = "${records.size}장 · 최대 ${PhotoArchive.MAX_PHOTOS}장 · 지형과 촬영 방향까지 보존"
        c.drawText(countText, right - textP.measureText(countText), contentTop() + dp(scene, 10f), textP)

        if (records.isEmpty()) {
            val emptyR = RectF(left, contentTop() + dp(scene, 28f), right, contentBottom() - dp(scene, 8f))
            cuteCard(c, emptyR, UiKit.PASTEL_SKY, UiKit.BROWN_LINE, 1.4f)
            textP.textSize = dp(scene, 34f)
            UiKit.iconCenter(c, g, "photo", emptyR.centerX(), emptyR.centerY() - dp(scene, 22f), dp(scene, 30f))
            textP.textSize = dp(scene, 14f)
            textP.color = 0xFF4A3728.toInt()
            val msg = "아직 인화한 사진이 없어요"
            c.drawText(msg, emptyR.centerX() - textP.measureText(msg) / 2f, emptyR.centerY() + dp(scene, 15f), textP)
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF8A7360.toInt()
            val sub = "필드에서 카메라를 열고 새를 찍으면 이곳에 자동으로 저장돼요"
            c.drawText(sub, emptyR.centerX() - textP.measureText(sub) / 2f, emptyR.centerY() + dp(scene, 34f), textP)
            return
        }

        val cols = 3
        val rows = 2
        val pageSize = cols * rows
        val totalPages = ((records.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        albumPage = albumPage.coerceIn(0, totalPages - 1)
        val gridTop = contentTop() + dp(scene, 24f)
        val pagerH = dp(scene, 28f)
        val gridBottom = contentBottom() - pagerH
        val gap = dp(scene, 7f)
        val cw = (width - gap * (cols - 1)) / cols
        val ch = (gridBottom - gridTop - gap * (rows - 1)) / rows
        val page = records.drop(albumPage * pageSize).take(pageSize)

        for ((i, record) in page.withIndex()) {
            val col = i % cols
            val row = i / cols
            val r = RectF(
                left + col * (cw + gap), gridTop + row * (ch + gap),
                left + col * (cw + gap) + cw, gridTop + row * (ch + gap) + ch
            )
            cuteCard(c, r, 0xFFFFFCF4.toInt(), 0xFFD8B77D.toInt(), 1.4f, stitched = false)
            val photoR = RectF(r.left + dp(scene, 4f), r.top + dp(scene, 4f), r.right - dp(scene, 4f), r.bottom - dp(scene, 27f))
            fillP.color = 0xFF25252B.toInt()
            c.drawRoundRect(photoR, dp(scene, 3f), dp(scene, 3f), fillP)
            val bmp = PhotoArchive.image(g.context, record.fileName) // 없으면 백그라운드 로딩 중
            if (bmp != null) {
                // 썸네일에서도 수평선/양옆 배경을 잘라내지 않는다.
                val k = minOf(photoR.width() / bmp.width.toFloat(), photoR.height() / bmp.height.toFloat())
                val dw = bmp.width * k
                val dh = bmp.height * k
                c.save(); c.clipRect(photoR)
                c.drawBitmap(bmp, null, RectF(photoR.centerX() - dw / 2f, photoR.centerY() - dh / 2f,
                    photoR.centerX() + dw / 2f, photoR.centerY() + dh / 2f), albumPhotoPaint)
                c.restore()
            } else {
                val def = Birds.byId[record.birdId]
                if (def != null) {
                    val bird = g.assets.birdPose(def.id, record.facing, record.pose)
                    val k = minOf(photoR.width() * 0.52f / bird.width, photoR.height() * 0.72f / bird.height)
                    c.drawBitmap(bird, null, RectF(photoR.centerX() - bird.width * k / 2f, photoR.centerY() - bird.height * k / 2f,
                        photoR.centerX() + bird.width * k / 2f, photoR.centerY() + bird.height * k / 2f), g.assets.sprPaint)
                }
            }
            strokeP.color = Color.argb(75, 40, 30, 22)
            strokeP.strokeWidth = dp(scene, 1f)
            c.drawRoundRect(photoR, dp(scene, 3f), dp(scene, 3f), strokeP)

            val def = Birds.byId[record.birdId]
            val regionName = Regions.byId[record.regionId]?.name ?: record.regionId
            textP.textSize = dp(scene, 10.2f)
            textP.color = 0xFF3B2F24.toInt()
            c.drawText(def?.name ?: "새 사진", r.left + dp(scene, 7f), r.bottom - dp(scene, 10f), textP)
            textP.textSize = dp(scene, 8.4f)
            textP.color = 0xFF8A7360.toInt()
            val meta = "별점 ${record.stars} · $regionName · ${record.facing.label}"
            c.drawText(meta, r.right - dp(scene, 7f) - textP.measureText(meta), r.bottom - dp(scene, 10f), textP)

            btnRects.add(Triple(r, def?.name ?: "사진") {
                scene.openOverlay(PhotoAlbumViewerOverlay(scene, record.id))
            })
        }

        fun pager(rect: RectF, label: String, enabled: Boolean, action: () -> Unit) {
            if (enabled) {
                cuteBtn(c, rect, label, UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
                btnRects.add(Triple(rect, label, action))
            } else cuteBtnOff(c, rect, label, 10.5f)
        }
        val py = contentBottom() - dp(scene, 23f)
        val prev = RectF(left, py, left + dp(scene, 78f), py + dp(scene, 21f))
        val next = RectF(right - dp(scene, 78f), py, right, py + dp(scene, 21f))
        pager(prev, "arrow_left 이전", albumPage > 0) { albumPage-- }
        pager(next, "arrow_right 다음", albumPage < totalPages - 1) { albumPage++ }
        val pageText = "${albumPage + 1} / $totalPages"
        textP.textSize = dp(scene, 10.5f)
        val pw = textP.measureText(pageText) + dp(scene, 22f)
        UiKit.badge(c, g, RectF(panelR.centerX() - pw / 2f, py, panelR.centerX() + pw / 2f, py + dp(scene, 21f)),
            pageText, 0xFFDDEEF5.toInt(), 0xFF4A6070.toInt(), 10.5f)
    }

    /** [P06] 진행 요약과 페이지식 업적 목록 — 각 행을 누르면 해금 정보를 살펴본다. */
    private fun drawAchievements(c: Canvas) {
        val g = scene.game
        val ctx = g.context
        val stats = Ach.stats(ctx)
        val unlocked = Ach.unlocked(ctx)
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        val width = right - left
        val top = contentTop() + dp(scene, 2f)
        val summaryH = dp(scene, 80f)
        val summaryR = RectF(left, top, right, top + summaryH)
        cuteCard(c, summaryR, UiKit.PASTEL_SKY, 0xFF8BAEBB.toInt(), 1.5f, stitched = false)

        textP.textSize = textDp(scene, 11.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("여정 요약", left + dp(scene, 9f), top + dp(scene, 18f), textP)
        val statsBtn = RectF(right - dp(scene, 101f), top + dp(scene, 5f), right - dp(scene, 7f), top + dp(scene, 27f))
        cuteBtn(c, statsBtn, "통계 자세히", UiKit.PASTEL_SAND, UiKit.INK, 9f)
        btnRects.add(Triple(statsBtn, "stats-detail") {
            scene.openOverlay(StatsOverlay(scene) { scene.openOverlay(MenuOverlay(scene, showAchievements = true)) })
        })

        val summaries = listOf(
            "🚶 ${String.format(java.util.Locale.US, "%.1f", stats.walkKm)} km  ·  🚲 ${String.format(java.util.Locale.US, "%.1f", stats.bikeKm)} km",
            "📷 ${stats.photos}장  ·  🐦 ${stats.discoveredSpecies}종  ·  🗺 ${stats.visitedRegions}/${Regions.ALL.size}곳",
            "🍕 ${stats.pizzasProduced}판  ·  🌅 ${stats.daysPlayed}일째  ·  해금 ${unlocked.size}/${Ach.total}"
        )
        var summaryY = top + dp(scene, 39f)
        for (line in summaries) {
            drawFitText(c, scene, line, left + dp(scene, 9f), summaryY, width - dp(scene, 18f), 9.4f, 7f)
            summaryY += dp(scene, 13f)
        }

        val navH = dp(scene, 26f)
        val navY = contentBottom() - navH
        val headingY = summaryR.bottom + dp(scene, 18f)
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("업적", left + dp(scene, 2f), headingY, textP)
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        val countLabel = "${unlocked.size} / ${Ach.total} 해금"
        c.drawText(countLabel, right - dp(scene, 2f) - textP.measureText(countLabel), headingY, textP)

        val rowTop = headingY + dp(scene, 7f)
        val listBottom = navY - dp(scene, 5f)
        val rowGap = dp(scene, 4f)
        val pageSize = (((listBottom - rowTop + rowGap) / (dp(scene, 34f) + rowGap)).toInt()).coerceIn(1, 5)
        val rowH = ((listBottom - rowTop - rowGap * (pageSize - 1)) / pageSize).coerceAtLeast(dp(scene, 25f))
        val pageCount = (Ach.ALL.size + pageSize - 1) / pageSize
        achievementPage = achievementPage.coerceIn(0, pageCount - 1)
        val defs = Ach.ALL.drop(achievementPage * pageSize).take(pageSize)

        for ((i, def) in defs.withIndex()) {
            val y = rowTop + i * (rowH + rowGap)
            val r = RectF(left, y, right, y + rowH)
            val isUnlocked = def.id in unlocked
            val tint = if (isUnlocked) 0xFFFFF4D6.toInt() else UiKit.CARD_HI
            val border = if (isUnlocked) UiKit.GOLD_DEEP else UiKit.BROWN_LINE
            cuteCard(c, r, tint, border, if (isUnlocked) 1.8f else 1.2f, stitched = rowH >= dp(scene, 34f))
            val iconR = minOf(dp(scene, 11f), rowH * 0.28f)
            val iconX = r.left + dp(scene, 8f) + iconR
            val icon = if (isUnlocked) def.icon else if (def.hidden) "❔" else "🔒"
            UiKit.iconCircle(c, g, iconX, r.centerY(), iconR, icon, 12f, if (isUnlocked) UiKit.PASTEL_LEMON else UiKit.PASTEL_SAND)
            val tx = iconX + iconR + dp(scene, 8f)
            val maxTextW = (r.right - tx - dp(scene, 8f)).coerceAtLeast(dp(scene, 20f))
            val title = if (def.hidden && !isUnlocked) "???" else def.title
            textP.textSize = textDp(scene, 10.5f)
            textP.color = 0xFF4A3728.toInt()
            drawFitText(c, scene, title, tx, r.top + minOf(dp(scene, 15f), rowH * 0.43f), maxTextW, 10.5f, 7f)
            if (rowH >= dp(scene, 34f)) {
                val sub = when {
                    isUnlocked -> "해금 · Day ${Ach.unlockDay(ctx, def.id) ?: 1}"
                    def.hidden -> "숨겨진 순간"
                    else -> def.desc
                }
                textP.color = if (isUnlocked) 0xFF9A6A1F.toInt() else 0xFF8A7360.toInt()
                drawFitText(c, scene, sub, tx, r.bottom - dp(scene, 6f), maxTextW, 8.4f, 6.5f)
            }
            btnRects.add(Triple(r, def.id) {
                scene.openOverlay(AchievementDetailOverlay(scene, def) {
                    scene.openOverlay(MenuOverlay(scene, showAchievements = true))
                })
            })
        }

        val prev = RectF(left, navY, left + dp(scene, 66f), navY + navH)
        val next = RectF(right - dp(scene, 66f), navY, right, navY + navH)
        cuteBtn(c, prev, "‹ 이전", if (achievementPage > 0) UiKit.PASTEL_SAND else 0xFFD8D1C5.toInt(), UiKit.INK, 9f)
        cuteBtn(c, next, "다음 ›", if (achievementPage < pageCount - 1) UiKit.PASTEL_SAND else 0xFFD8D1C5.toInt(), UiKit.INK, 9f)
        val pageLabel = "${achievementPage + 1} / $pageCount"
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(pageLabel, panelR.centerX() - textP.measureText(pageLabel) / 2f, navY + dp(scene, 17f), textP)
        if (achievementPage > 0) btnRects.add(Triple(prev, "ach-prev") { achievementPage-- })
        if (achievementPage < pageCount - 1) btnRects.add(Triple(next, "ach-next") { achievementPage++ })
    }

    private fun drawSettings(c: Canvas) {
        val g = scene.game
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        val top = contentTop() + dp(scene, 4f)
        val bottom = contentBottom()

        // 설정이 늘어나도 패널 밖으로 밀려나지 않도록 먼저 항목을 모은 뒤,
        // 현재 화면의 가로·세로 여유에 맞춰 한 화면짜리 그리드로 배치한다.
        data class SettingItem(
            val icon: () -> String,
            val label: () -> String,
            val sub: String,
            val danger: Boolean = false,
            val switch: (() -> Boolean)? = null,
            val action: () -> Unit
        )

        val items = arrayListOf(
            SettingItem(
                { "music" },
                { "음악: " + if (g.state.musicOn) "켜짐" else "꺼짐" },
                "배경 음악을 켜고 꺼요",
                action = {
                    g.state.musicOn = !g.state.musicOn
                    g.audio.setMusic(g.state.musicOn)
                    SaveManager.save(g.context, g.state)
                }
            ),
            SettingItem(
                { "music" },
                { "효과음: " + if (g.state.sfxOn) "켜짐" else "꺼짐" },
                "새 소리와 버튼음을 켜고 꺼요",
                action = {
                    g.state.sfxOn = !g.state.sfxOn
                    g.audio.setSfx(g.state.sfxOn)
                    SaveManager.save(g.context, g.state)
                }
            ),
            SettingItem({ "note" }, { "글자 크기: ${TypeScale.label()}" }, "대화와 메뉴 글자를 더 크게 표시해요", action = {
                val level = TypeScale.cycle(g.context)
                g.toast("글자 크기: ${listOf("보통", "크게", "아주 크게")[level]}")
            }),
            SettingItem(
                { "gear" },
                { "움직이는 조이스틱: " + if (g.state.floatStick) "켜짐" else "꺼짐" },
                "왼쪽 아래를 끌면 그 자리에 스틱이 생겨요",
                switch = { g.state.floatStick },
                action = {
                    g.state.floatStick = !g.state.floatStick
                    g.hud.releaseStick()
                    SaveManager.save(g.context, g.state)
                    g.toast(if (g.state.floatStick) "움직이는 스틱 켬" else "고정 스틱만 쓸게요")
                }
            ),
            SettingItem(
                { "gear" },
                { "민 만큼 속도: " + if (g.state.analogStick) "켜짐" else "꺼짐" },
                "스틱을 살짝 밀면 살금살금, 끝까지 밀면 쌩쌩",
                switch = { g.state.analogStick },
                action = {
                    g.state.analogStick = !g.state.analogStick
                    SaveManager.save(g.context, g.state)
                    g.toast(if (g.state.analogStick) "아날로그 이동 켬 — 스틱을 민 만큼 걸어요" else "일정 속도로 걸어요")
                }
            ),
            SettingItem({ "camera" }, { "화면 연출 (몰입감)" }, "흔들림·헤드밥·잔상·심도 설정", action = {
                scene.openOverlay(CameraFxOverlay(scene))
            }),
            SettingItem(
                { "photo" },
                { "화질: " + when (g.state.renderScale) {
                    "1" -> "1배 (성능 우선)"
                    "2" -> "2배 (고화질)"
                    "3" -> "3배 (최고 화질)"
                    else -> "자동 (${g.worldScale}배 · 프레임 우선)"
                } },
                "버벅이면 월드만 낮춰요 (글씨·버튼은 선명하게)",
                action = {
                    g.state.renderScale = when (g.state.renderScale) {
                        "auto" -> "1"; "1" -> "2"; "2" -> "3"; else -> "auto"
                    }
                    g.applyRenderQuality()
                    SaveManager.save(g.context, g.state)
                }
            ),
            SettingItem(
                { "sparkle" },
                { "화면 보간: " + if (g.state.smoothScreen) "부드럽게" else "끔 (픽셀 선명)" },
                "픽셀 아트를 부드럽게 확대해 보여요",
                action = {
                    g.state.smoothScreen = !g.state.smoothScreen
                    g.applyRenderQuality()
                    SaveManager.save(g.context, g.state)
                }
            ),
            SettingItem({ "note" }, { "저장하기" }, "지금까지의 여행을 안전하게 보관해요", action = {
                SaveManager.save(g.context, g.state)
                g.toast("저장 완료!")
            }),
            SettingItem({ "house" }, { "타이틀로 가기" }, "저장 후 타이틀 화면으로 돌아가요", action = {
                SaveManager.save(g.context, g.state)
                finished = true
                g.fadeTo { g.scene = TitleScene(g) }
            }),
            if (resetArmed) {
                SettingItem({ "warning" }, { "정말 처음부터 시작할까요?" }, "되돌릴 수 없어요! 다시 누르면 초기화돼요", danger = true, action = {
                    SaveManager.clear(g.context)
                    g.state.reset("seoul")
                    g.state.started = false
                    finished = true
                    g.fadeTo { g.scene = TitleScene(g) }
                })
            } else {
                SettingItem({ "box" }, { "처음부터 다시 시작" }, "저장 데이터를 모두 지우고 새로 시작해요", action = {
                    resetArmed = true
                })
            },
            // [P05] 클라우드 없는 백업 — 코드 한 장으로 세이브를 다른 기기로 옮긴다.
            // 닫히면 왔던 자리(여행 가방 메뉴)를 다시 열어 준다.
            SettingItem({ "🗄" }, { "백업 코드 만들기" }, "진행 상황을 텍스트 코드로 복사해 다른 기기로", action = {
                scene.openOverlay(BackupOverlay(scene, BackupOverlay.Mode.CREATE) { scene.openOverlay(MenuOverlay(scene)) })
            }),
            SettingItem({ "📥" }, { "코드에서 불러오기" }, "복사해 둔 백업 코드를 클립보드에서 읽어 복원", action = {
                scene.openOverlay(BackupOverlay(scene, BackupOverlay.Mode.RESTORE) { scene.openOverlay(MenuOverlay(scene)) })
            })
        )

        val gap = dp(scene, 6f)
        val contentW = right - left
        val contentH = (bottom - top).coerceAtLeast(dp(scene, 1f))
        // 카드가 지나치게 좁아지지 않는 범위에서 열 수를 늘린다. 일반 휴대폰은
        // 3열×4행, 4:3/분할 화면은 2열×6행, 넓은 태블릿은 4열×3행이다.
        val maxColumnsByWidth = ((contentW + gap) / (dp(scene, 170f) + gap)).toInt().coerceIn(2, 4)
        val columns = minOf(items.size, maxColumnsByWidth)
        val rows = (items.size + columns - 1) / columns
        val preferredRowGap = if (rows >= 6) dp(scene, 4f) else gap
        // 아주 낮은 분할 화면에서도 간격 때문에 높이가 음수가 되지 않게 한다.
        val rowGap = minOf(preferredRowGap, contentH / rows / 4f)
        val colW = (contentW - gap * (columns - 1)) / columns
        val rowH = ((contentH - rowGap * (rows - 1)) / rows).coerceAtMost(dp(scene, 46f))
        val gridH = rowH * rows + rowGap * (rows - 1)

        fun fittedText(raw: String, maxW: Float, preferred: Float, minimum: Float): String {
            var size = preferred
            textP.textSize = textDp(scene, size)
            while (textP.measureText(raw) > maxW && size > minimum) {
                size -= 0.5f
                textP.textSize = textDp(scene, size)
            }
            if (textP.measureText(raw) <= maxW) return raw
            var out = raw
            while (out.length > 1 && textP.measureText("$out…") > maxW) out = out.dropLast(1)
            return "$out…"
        }

        items.forEachIndexed { index, item ->
            val col = index % columns
            val row = index / columns
            val rx = left + col * (colW + gap)
            val yy = top + row * (rowH + rowGap)
            val r = RectF(rx, yy, rx + colW, yy + rowH)
            val isOn = item.switch?.invoke()
            cuteCard(
                c, r,
                if (item.danger) 0xFFFFE6E1.toInt() else UiKit.CARD_HI,
                if (item.danger) 0xFFE2574C.toInt() else UiKit.BROWN_LINE,
                if (item.danger) 2f else 1.6f,
                stitched = rowH >= dp(scene, 34f)
            )

            val iconR = minOf(dp(scene, 12f), rowH * 0.31f)
            val iconX = rx + dp(scene, 7f) + iconR
            UiKit.iconCircle(
                c, g, iconX, r.centerY(), iconR, item.icon(),
                if (rowH < dp(scene, 34f)) 11f else 13f,
                if (item.danger) 0xFFF28B82.toInt() else if (isOn == true) UiKit.PASTEL_MINT else UiKit.PASTEL_PEACH
            )

            val endReserve = if (item.switch != null) dp(scene, 45f) else dp(scene, 25f)
            val tx = iconX + iconR + dp(scene, 6f)
            val maxTextW = (r.right - endReserve - tx).coerceAtLeast(dp(scene, 18f))
            val spacious = rowH >= dp(scene, 42f) && colW >= dp(scene, 235f)
            textP.color = if (item.danger) 0xFFB03A30.toInt() else 0xFF4A3728.toInt()
            val label = fittedText(item.label(), maxTextW, if (spacious) 12.5f else 11.5f, 7.5f)
            val labelY = if (spacious) r.centerY() - dp(scene, 1f) else r.centerY() - (textP.descent() + textP.ascent()) / 2f
            c.drawText(label, tx, labelY, textP)
            if (spacious) {
                textP.color = 0xFF8A7360.toInt()
                val sub = fittedText(item.sub, maxTextW, 8.8f, 7f)
                c.drawText(sub, tx, r.centerY() + dp(scene, 12f), textP)
            }

            if (item.switch != null && rowH >= dp(scene, 28f)) {
                UiKit.cuteToggle(c, g, r.right - dp(scene, 7f), r.centerY(), isOn == true)
            } else {
                val arrowW = minOf(dp(scene, 20f), rowH * 0.55f)
                val ar = RectF(r.right - arrowW - dp(scene, 5f), r.centerY() - arrowW / 2f,
                    r.right - dp(scene, 5f), r.centerY() + arrowW / 2f)
                UiKit.cuteButton(
                    c, g, ar, "›",
                    if (item.danger) 0xFFF28B82.toInt() else UiKit.PASTEL_PEACH,
                    if (item.danger) 0xFF7A1E14.toInt() else UiKit.INK,
                    if (rowH < dp(scene, 34f)) 10f else 12f, depthDp = 1.5f
                )
            }
            btnRects.add(Triple(r, item.label(), item.action))
        }

        // 큰 태블릿처럼 그리드 아래 여백이 충분할 때만 부가 정보를 보여 준다.
        // 작은 화면에서는 설정 버튼의 터치 영역을 우선해 푸터가 절대 겹치지 않는다.
        val footerTop = top + gridH + dp(scene, 7f)
        if (bottom - footerTop >= dp(scene, 34f)) {
            val footR = RectF(left, footerTop, right, bottom)
            cuteCard(c, footR, UiKit.PASTEL_SAND)
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF6B4F35.toInt()
            c.drawText("Pizza and Bird v0.4.2-beta01 · 2K", left + dp(scene, 12f), footerTop + dp(scene, 16f), textP)
            if (footR.height() >= dp(scene, 54f)) {
                textP.textSize = dp(scene, 8f)
                textP.color = 0xFF8A7360.toInt()
                c.drawText("글꼴: 주아 · 고운돋움 — SIL Open Font License 1.1", left + dp(scene, 12f), footerTop + dp(scene, 38f), textP)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 화면 연출 (몰입 카메라) — 멀미 대응 설정
// ---------------------------------------------------------------------------

/**
 * 카메라 연출 6종을 켜고 끄는 화면.
 * 3D 멀미(모션 시크니스)에 민감한 사람도 편하게 놀 수 있도록,
 * 흔들림은 4단계로 줄이거나 완전히 끌 수 있다. 바꾸면 바로 저장된다.
 */
class CameraFxOverlay(scene: Scene) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true



    private val rowRects = ArrayList<Pair<RectF, () -> Unit>>()
    private var closeRect = RectF()
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, action) in rowRects) {
            if (r.contains(tap.x, tap.y)) {
                action()
                SaveManager.save(scene.game.context, scene.game.state)
                scene.game.haptic()
                scene.game.sfx(Audio.Sfx.TAP, 0.6f)
                return
            }
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val st = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val pw = minOf(w * 0.86f, dp(scene, 430f))
        val ph = minOf(h * 0.94f, dp(scene, 480f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "close", 11f)

        textP.textSize = textDp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("화면 연출", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("화면이 살아 움직이게 하는 연출들이에요", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 44f), textP)
        UiKit.divider(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 56f))

        rowRects.clear()
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        val top0 = panelR.top + dp(scene, 62f)
        val footH = dp(scene, 34f)
        val gap = dp(scene, 5f)
        val rowH = (((panelR.bottom - dp(scene, 10f) - footH) - top0 - gap * 5f) / 6f)
            .coerceIn(dp(scene, 34f), dp(scene, 50f))
        var ty = top0

        fun row(icon: String, label: String, sub: String, value: String, on: Boolean, action: () -> Unit) {
            val r = RectF(left, ty, right, ty + rowH)
            UiKit.card(c, g, r, 10f, false, if (on) 0xFF8FBF7F.toInt() else 0xFFC9A87B.toInt(), 1.5f)
            UiKit.iconCircle(
                c, g, left + dp(scene, 22f), r.centerY(), dp(scene, 13f), icon, 14f,
                if (on) 0xFF6FBA6B.toInt() else 0xFFB9AA95.toInt()
            )
            textP.textSize = textDp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(label, left + dp(scene, 42f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = textDp(scene, 9f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, left + dp(scene, 42f), r.centerY() + dp(scene, 12f), textP)

            textP.textSize = textDp(scene, 10.5f)
            val vw = textP.measureText(value) + dp(scene, 20f)
            UiKit.badge(
                c, g,
                RectF(right - dp(scene, 10f) - vw, r.centerY() - dp(scene, 10f), right - dp(scene, 10f), r.centerY() + dp(scene, 10f)),
                value, if (on) 0xFF6FBA6B.toInt() else 0xFFC0B2A0.toInt(), 0xFFFDF8EC.toInt(), 10.5f
            )
            rowRects.add(r to action)
            ty += rowH + gap
        }

        row("camera", "카메라 흔들림", "타격·충돌·셔터의 충격을 화면으로", CamFx.shakeLabel(st), st.camShake > 0) {
            st.camShake = (st.camShake + 1) % CamFx.SHAKE_LABELS.size
            g.shake(0.5f)                       // 바꾼 강도를 바로 체감해 볼 수 있게
        }
        row("bird", "헤드 밥 · 바디 스웨이", "걸음 리듬에 맞춰 화면이 출렁여요", CamFx.onOff(st.camBob), st.camBob) {
            st.camBob = !st.camBob
        }
        row("wind", "잔상 · 속도선", "달리기·자전거에서 속도가 느껴지게", CamFx.onOff(st.camBlur), st.camBlur) {
            st.camBlur = !st.camBlur
        }
        row("lens", "시야각 변동 (FOV)", "빠르면 넓게, 카메라 모드에선 망원으로", CamFx.onOff(st.camFov), st.camFov) {
            st.camFov = !st.camFov
            if (st.camFov) g.punchZoom(0.06f)
        }
        row("moon", "심도 · 초점 흐림", "카메라 모드에서 초점 밖을 어둡게", CamFx.onOff(st.camDof), st.camDof) {
            st.camDof = !st.camDof
        }
        row("map", "예측 배치", "가는 방향의 앞쪽을 더 보여줘요", CamFx.onOff(st.camLead), st.camLead) {
            st.camLead = !st.camLead
        }

        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("3D 멀미가 있다면 흔들림을 '끔'으로 두세요. 바로 저장돼요.", left + dp(scene, 2f), ty + dp(scene, 14f), textP)
    }
}

// ---------------------------------------------------------------------------
// 장식 상점 (서울 본점의 장식 코너)
// ---------------------------------------------------------------------------

class DecorShopOverlay(scene: Scene) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true



    private var buyRects = ArrayList<Pair<RectF, Int>>()
    private var closeRect = RectF()
    private var panelR = RectF()
    private var catalogPage = 0
    private var catalogTabs = ArrayList<Pair<RectF, Int>>()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        for ((r, page) in catalogTabs) {
            if (r.contains(tap.x, tap.y)) {
                catalogPage = page
                scene.game.sfx(Audio.Sfx.TAP, 0.35f)
                return
            }
        }
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

        // 닫기 + 헤더 + 보유금 뱃지
        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "close", 11f)

        textP.textSize = textDp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("장식 코너", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("집에 놓으면 행운이 오르는 소품들이에요", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 44f), textP)
        textP.textSize = textDp(scene, 10.5f)
        val moneyTxt = "₩ ${won(s.money)}"
        val moneyW = textP.measureText(moneyTxt) + dp(scene, 18f)
        UiKit.badge(
            c, g, RectF(panelR.right - dp(scene, 44f) - moneyW, panelR.top + dp(scene, 30f), panelR.right - dp(scene, 44f), panelR.top + dp(scene, 50f)),
            moneyTxt, 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 10.5f
        )
        UiKit.divider(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 56f))

        // 두 카탈로그로 나누어 작은 화면에서도 상품 행이 눌리지 않게 한다.
        catalogTabs = ArrayList()
        val tabTop = panelR.top + dp(scene, 62f)
        val tabGap = dp(scene, 6f)
        val tabW = (panelR.width() - dp(scene, 24f) - tabGap) / Decors.SHOP_GROUPS.size
        for (i in Decors.SHOP_GROUPS.indices) {
            val tr = RectF(panelR.left + dp(scene, 12f) + i * (tabW + tabGap), tabTop,
                panelR.left + dp(scene, 12f) + i * (tabW + tabGap) + tabW, tabTop + dp(scene, 24f))
            val selected = i == catalogPage
            drawButton(c, scene, tr, Decors.SHOP_GROUPS[i],
                if (selected) 0xFF6FBA6B.toInt() else 0xFFF2E3C2.toInt(),
                if (selected) 0xFFFFF8E8.toInt() else 0xFF6B4F35.toInt(), 10f)
            catalogTabs.add(tr to i)
        }

        // 상품 행 — 현재 카탈로그의 수에 맞춰 자동 배분
        val items = Decors.shopItems(catalogPage)
        buyRects = ArrayList()
        val gap = dp(scene, 5f)
        val top0 = tabTop + dp(scene, 31f)
        val avail = panelR.bottom - dp(scene, 10f) - top0
        val rowH = ((avail - gap * (items.size - 1)) / items.size).coerceIn(dp(scene, 36f), dp(scene, 56f))
        var ty = top0
        for (d in items) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH)
            drawCard(c, scene, r)

            UiKit.iconCircle(c, g, r.left + dp(scene, 22f), r.centerY(), dp(scene, 13f), d.emoji, 15f)
            val tx = r.left + dp(scene, 42f)
            // 이름 + 행운 뱃지
            textP.textSize = textDp(scene, 12f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(d.name, tx, r.top + dp(scene, 17f), textP)
            val luckTxt = "행운+${d.luck}"
            textP.textSize = textDp(scene, 9f)
            val luckW = textP.measureText(luckTxt) + dp(scene, 11f)
            val nameW = run { textP.textSize = textDp(scene, 12f); textP.measureText(d.name) }
            UiKit.badge(
                c, g, RectF(tx + nameW + dp(scene, 6f), r.top + dp(scene, 6f), tx + nameW + dp(scene, 6f) + luckW, r.top + dp(scene, 21f)),
                luckTxt, 0xFF6FBA6B.toInt(), 0xFFFFF8E8.toInt(), 9f
            )
            // 설명 (말줄임)
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            var desc = d.desc
            val btnLeft = r.right - dp(scene, 88f)
            val maxDW = btnLeft - tx - dp(scene, 6f)
            if (maxDW > dp(scene, 30f)) {
                while (desc.length > 1 && textP.measureText(desc) > maxDW) desc = desc.dropLast(1)
                if (desc != d.desc) desc = desc.dropLast(1) + "…"
                c.drawText(desc, tx, r.top + dp(scene, 31f), textP)
            }

            val owned = d.id in s.decorOwned
            val br = RectF(r.right - dp(scene, 82f), r.centerY() - dp(scene, 12f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 12f))
            if (owned) {
                drawButton(c, scene, br, "check 보유중", Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 10.5f)
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
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true


    private var pickRects = ArrayList<Pair<RectF, Int>>()
    private var closeRect = RectF()
    private var prevRect = RectF()
    private var nextRect = RectF()
    private var panelR = RectF()
    private var page = 0
    private val pageSize = 6

    /** 다른 칸에 이미 놓인 소품은 여기서 빼서 복제 배치를 막는다. */
    private fun availableIds(): List<Int> {
        val s = scene.game.state
        val usedElsewhere = s.decorSlots.withIndex()
            .filter { it.index != slot }
            .map { it.value }
            .toSet()
        return s.decorOwned.distinct().filter { Decors.of(it) != null && it !in usedElsewhere }.sorted()
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        val pages = ((availableIds().size + pageSize - 1) / pageSize).coerceAtLeast(1)
        if (prevRect.contains(tap.x, tap.y) && page > 0) {
            page--
            scene.game.sfx(Audio.Sfx.TAP, 0.35f)
            return
        }
        if (nextRect.contains(tap.x, tap.y) && page < pages - 1) {
            page++
            scene.game.sfx(Audio.Sfx.TAP, 0.35f)
            return
        }
        for ((r, id) in pickRects) {
            if (r.contains(tap.x, tap.y)) {
                scene.game.sfx(Audio.Sfx.TAP, 0.5f)
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
        val available = availableIds()
        val pages = ((available.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val current = available.drop(page * pageSize).take(pageSize)
        dim(c, scene, 150)

        val rowCount = current.size + 1 // 마지막은 항상 비우기
        val pw = minOf(w * 0.84f, dp(scene, 360f))
        // 행 수에 맞는 높이 — 82dp 헤더 + 행 48dp씩 + 간격 5dp + 하단 여백 10dp
        val ph = minOf(
            dp(scene, 92f) + dp(scene, 48f) * rowCount + dp(scene, 5f) * (rowCount - 1),
            h * 0.9f
        )
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "close", 11f)

        textP.textSize = textDp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("장식 칸 ${slot + 1}에 뭘 놓을까요?", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 29f), textP)
        UiKit.divider(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 52f))

        // 소품이 많아져도 6개씩 넘긴다.
        val navY = panelR.top + dp(scene, 57f)
        prevRect = RectF(panelR.left + dp(scene, 14f), navY, panelR.left + dp(scene, 42f), navY + dp(scene, 20f))
        nextRect = RectF(panelR.left + dp(scene, 46f), navY, panelR.left + dp(scene, 74f), navY + dp(scene, 20f))
        val canPrev = page > 0
        val canNext = page < pages - 1
        drawButton(c, scene, prevRect, "‹", if (canPrev) 0xFFF2E3C2.toInt() else 0xFFD9CFC0.toInt(), 0xFF4A3728.toInt(), 13f)
        drawButton(c, scene, nextRect, "›", if (canNext) 0xFFF2E3C2.toInt() else 0xFFD9CFC0.toInt(), 0xFF4A3728.toInt(), 13f)
        textP.textSize = dp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("${page + 1}/$pages · 배치 가능 ${available.size}개", panelR.left + dp(scene, 82f), navY + dp(scene, 14f), textP)

        pickRects = ArrayList()
        val gap = dp(scene, 5f)
        val top0 = panelR.top + dp(scene, 82f)
        val availH = panelR.bottom - dp(scene, 10f) - top0
        val rowH = ((availH - gap * (rowCount - 1)) / rowCount).coerceIn(dp(scene, 38f), dp(scene, 50f))
        var ty = top0

        fun row(id: Int, emoji: String, name: String, sub: String, accent: Int = 0xFFF2B63C.toInt()) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH)
            drawCard(c, scene, r)
            UiKit.iconCircle(c, g, r.left + dp(scene, 24f), r.centerY(), dp(scene, 14f), emoji, 16f, accent)
            textP.textSize = textDp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(name, r.left + dp(scene, 46f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, r.left + dp(scene, 46f), r.centerY() + dp(scene, 13f), textP)
            textP.textSize = textDp(scene, 15f)
            textP.color = 0xFFB5651D.toInt()
            c.drawText("›", r.right - dp(scene, 20f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            pickRects.add(r to id)
            ty += rowH + gap
        }

        for (did in current) {
            val d = Decors.of(did) ?: continue
            val here = s.decorSlots.getOrNull(slot) == did
            row(did, d.emoji, d.name, "행운 +${d.luck}" + if (here) " · 이 칸에 배치 중" else "")
        }
        row(-1, "box", "빈 칸으로 두기", "장식을 치웁니다", 0xFFD9CFC0.toInt())
    }
}

// ---------------------------------------------------------------------------
// 집 꾸미기 보드 — 8칸 레이아웃, 자동 정리, 컬렉션 효과를 한 화면에서 관리
// ---------------------------------------------------------------------------

class HomeDecorOverlay(scene: Scene) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true



    private var closeRect = RectF()
    private var autoRect = RectF()
    private var clearRect = RectF()
    private val slotRects = ArrayList<Pair<RectF, Int>>()

    override fun handleInput(input: Input) {
        val g = scene.game
        val s = g.state
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) {
            g.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        if (autoRect.contains(tap.x, tap.y)) {
            val count = s.autoArrangeDecors()
            SaveManager.save(g.context, s)
            // 한글이 바로 붙으면 식별자로 먹히므로 ${} 로 감싼다
            g.toast("보유 소품 ${count}개를 순서대로 정리했어요")
            g.sfx(Audio.Sfx.SUCCESS, 0.6f)
            return
        }
        if (clearRect.contains(tap.x, tap.y)) {
            for (i in s.decorSlots.indices) s.decorSlots[i] = -1
            SaveManager.save(g.context, s)
            g.toast("배치 보드를 비웠어요. 소품은 그대로 보유 중이에요")
            g.sfx(Audio.Sfx.TAP, 0.45f)
            return
        }
        for ((r, slot) in slotRects) {
            if (!r.contains(tap.x, tap.y)) continue
            g.sfx(Audio.Sfx.TAP, 0.4f)
            scene.openOverlay(DecorPickOverlay(scene, slot) { picked ->
                s.placeDecor(slot, picked)
                SaveManager.save(g.context, s)
                val d = Decors.of(picked)
                g.toast(if (d == null) "장식 칸을 비웠어요" else "${d.emoji} ${d.name} 배치 완료!")
                g.sfx(Audio.Sfx.SUCCESS, 0.6f)
                // 선택 후에도 다시 보드로 돌아와 여러 칸을 연달아 꾸밀 수 있다.
                scene.openOverlay(HomeDecorOverlay(scene))
            })
            return
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 158)

        val pw = minOf(w * 0.9f, dp(scene, 470f))
        val ph = minOf(h * 0.9f, dp(scene, 450f))
        val panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "close", 11f)

        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("집 꾸미기 보드", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val filled = s.placedDecorIds().size
        c.drawText("배치 $filled/${s.decorSlots.size} · 소품 +${s.decorItemLuck()} · 세트 +${s.decorSetBonus()}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 45f), textP)
        UiKit.divider(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 54f))

        // 2 × 4 레이아웃 카드
        slotRects.clear()
        val cols = 2
        val gap = dp(scene, 7f)
        val gridLeft = panelR.left + dp(scene, 13f)
        val gridRight = panelR.right - dp(scene, 13f)
        val cardW = (gridRight - gridLeft - gap) / cols
        val gridTop = panelR.top + dp(scene, 63f)
        val setArea = dp(scene, 76f)
        val buttonsArea = dp(scene, 38f)
        val gridBottom = panelR.bottom - dp(scene, 12f) - setArea - buttonsArea
        // 남는 높이를 네 줄에 정확히 나눠 작은 화면에서도 세트 안내와 겹치지 않게 한다.
        val cardH = ((gridBottom - gridTop - gap * 3f) / 4f).coerceAtLeast(dp(scene, 24f))
        for (slot in s.decorSlots.indices) {
            val col = slot % cols
            val row = slot / cols
            val left = gridLeft + col * (cardW + gap)
            val top = gridTop + row * (cardH + gap)
            val r = RectF(left, top, left + cardW, top + cardH)
            val id = s.decorSlots[slot]
            val d = Decors.of(id)
            UiKit.card(c, g, r, 9f, d != null, if (d != null) 0xFF6FBA6B.toInt() else 0xFFC9A87B.toInt(), if (d != null) 2f else 1f)
            UiKit.iconCircle(c, g, r.left + dp(scene, 19f), r.centerY(), dp(scene, 11f), d?.emoji ?: "plus", 13f,
                if (d != null) 0xFF6FBA6B.toInt() else 0xFFE9DDC7.toInt())
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText("${slot + 1}. ${d?.name ?: "비어 있음"}", r.left + dp(scene, 37f), r.centerY() - dp(scene, 1f), textP)
            if (d != null) {
                textP.textSize = dp(scene, 8.5f)
                textP.color = 0xFF8A7360.toInt()
                c.drawText("행운 +${d.luck}", r.left + dp(scene, 37f), r.centerY() + dp(scene, 11f), textP)
            }
            slotRects.add(r to slot)
        }

        // 다음에 완성할 수 있는 컬렉션을 제안한다.
        val placed = s.placedDecorIds()
        val completed = Decors.placedSets(placed)
        val next = Decors.SETS.filter { it !in completed }.maxByOrNull { set ->
            set.members.count { it in placed }.toFloat() / set.required
        }
        val infoTop = gridBottom + dp(scene, 9f)
        val info = RectF(gridLeft, infoTop, gridRight, infoTop + dp(scene, 31f))
        UiKit.card(c, g, info, 8f, completed.isNotEmpty(), 0xFFF2B63C.toInt(), 1.2f)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF4A3728.toInt()
        val infoText = if (next == null) {
            "모든 컬렉션 완성! 세트 효과 +${s.decorSetBonus()}"
        } else {
            val n = next.members.count { it in placed }
            "${next.emoji} 다음: ${next.name}  $n/${next.required} · 완성 시 행운 +${next.bonus}"
        }
        c.drawText(infoText, info.left + dp(scene, 10f), info.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)

        autoRect = RectF(gridLeft, panelR.bottom - dp(scene, 40f), gridLeft + (gridRight - gridLeft - gap) * 0.62f, panelR.bottom - dp(scene, 10f))
        clearRect = RectF(autoRect.right + gap, autoRect.top, gridRight, autoRect.bottom)
        drawButton(c, scene, autoRect, "sparkle 보유 소품 자동 정리", 0xFF6FBA6B.toInt(), 0xFFFFF8E8.toInt(), 10.5f)
        drawButton(c, scene, clearRect, "비우기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 10.5f)
    }
}

// ---------------------------------------------------------------------------
// 집 인테리어 카탈로그 — 스타일 구매/적용
// ---------------------------------------------------------------------------

class HouseStyleOverlay(
    scene: Scene,
    private val onApply: (String) -> Unit
) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true


    private val styleRects = ArrayList<Pair<RectF, String>>()
    private var closeRect = RectF()
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val g = scene.game
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) {
            g.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
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
        // 목록(인테리어 4종) 행 수에 딱 맞는 높이 — 아래쪽 빈자리 없이
        val nStyles = HouseStyles.ALL.size
        val ph = minOf(
            h * 0.92f,
            dp(scene, 62f) + dp(scene, 60f) * nStyles + dp(scene, 6f) * (nStyles - 1) + dp(scene, 10f)
        )
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "close", 11f)

        textP.textSize = textDp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("우리 집 인테리어", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        textP.textSize = textDp(scene, 10.5f)
        val moneyTxt = "₩ ${won(s.money)}"
        val moneyW = textP.measureText(moneyTxt) + dp(scene, 18f)
        UiKit.badge(
            c, g, RectF(panelR.right - dp(scene, 44f) - moneyW, panelR.top + dp(scene, 30f), panelR.right - dp(scene, 44f), panelR.top + dp(scene, 50f)),
            moneyTxt, 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 10.5f
        )
        UiKit.divider(c, g, panelR.left + dp(scene, 14f), panelR.right - dp(scene, 14f), panelR.top + dp(scene, 56f))

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

            UiKit.iconCircle(c, g, r.left + dp(scene, 24f), r.centerY(), dp(scene, 14f), style.emoji, 16f, style.accentTint)
            textP.textSize = textDp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(style.name, r.left + dp(scene, 46f), r.centerY() - dp(scene, 1f), textP)
            // 설명 말줄임
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            var desc = style.desc
            val maxDW = r.right - dp(scene, 100f) - (r.left + dp(scene, 46f))
            if (maxDW > dp(scene, 30f)) {
                while (desc.length > 1 && textP.measureText(desc) > maxDW) desc = desc.dropLast(1)
                if (desc != style.desc) desc = desc.dropLast(1) + "…"
                c.drawText(desc, r.left + dp(scene, 46f), r.centerY() + dp(scene, 13f), textP)
            }

            val br = RectF(r.right - dp(scene, 90f), r.centerY() - dp(scene, 14f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 14f))
            if (applied) {
                drawButton(c, scene, br, "check 적용중", 0xFFD7E3C3.toInt(), 0xFF4A3728.toInt(), 10.5f)
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
// 자전거 상점 — 모델 구매/교체 · 도색 · 부속품 (탈것은 자전거뿐!)
// ---------------------------------------------------------------------------

class BikeShopOverlay(scene: Scene) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true



    private enum class Tab(val label: String) {
        MODEL("모델"), PAINT("도색"), PARTS("부속품")
    }

    private var tab = Tab.MODEL
    private var page = 0

    private val tabRects = ArrayList<Pair<RectF, Tab>>()
    private val modelRects = ArrayList<Pair<RectF, String>>()
    private val swatchRects = ArrayList<Pair<RectF, IntArray>>()   // [슬롯(0~2), 색인덱스]
    private val partRects = ArrayList<Pair<RectF, String>>()
    private val pageRects = ArrayList<Pair<RectF, Int>>()
    private var closeRect = RectF()
    private var panelR = RectF()
    // 자전거 미리보기 — HD 스프라이트(96px)를 상점 칸 크기로 줄여 붙이므로 보간을 켠다
    private val pixelPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = true }

    /** 월드용 자전거 세트를 마지막으로 배경 준비한 도색 조합 */
    private var warmedBikeKey = ""

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, t) in tabRects) {
            if (r.contains(tap.x, tap.y)) {
                tab = t; page = 0
                scene.game.sfx(Audio.Sfx.TAP, 0.4f)
                return
            }
        }
        for ((r, p) in pageRects) {
            if (r.contains(tap.x, tap.y)) { page = p; return }
        }
        for ((r, id) in modelRects) {
            if (r.contains(tap.x, tap.y)) { tapModel(id); return }
        }
        for ((r, s) in swatchRects) {
            if (r.contains(tap.x, tap.y)) { tapSwatch(s[0], s[1]); return }
        }
        for ((r, id) in partRects) {
            if (r.contains(tap.x, tap.y)) { tapPart(id); return }
        }
    }

    private fun tapModel(id: String) {
        val g = scene.game
        val s = g.state
        val bike = Bikes.of(id)
        if (id in s.ownedBikes) {
            if (s.bikeId == id) {
                g.toast("이미 타고 있는 자전거예요!")
                return
            }
            s.bikeId = id
            SaveManager.save(g.context, s)
            g.toast("${bike.emoji} ${bike.name}(으)로 바꿨어요!")
            g.sfx(Audio.Sfx.BIKE_BELL, 0.7f)
        } else {
            if (s.money < bike.cost) {
                g.toast("돈이 부족해요… (${won(bike.cost)})")
                g.sfx(Audio.Sfx.FAIL, 0.5f)
                return
            }
            s.money -= bike.cost
            s.ownedBikes.add(id)
            s.bikeId = id
            SaveManager.save(g.context, s)
            g.toast("${bike.emoji} ${bike.name} 구매! 바로 타볼까요?")
            g.sfx(Audio.Sfx.BUY)
        }
    }

    private fun tapSwatch(slot: Int, index: Int) {
        val g = scene.game
        val s = g.state
        when (slot) {
            0 -> s.bikeFrameColor = index
            1 -> s.bikeTireColor = index
            2 -> s.bikeSaddleColor = index
        }
        SaveManager.save(g.context, s)
        g.sfx(Audio.Sfx.TAP, 0.5f, 1.25f)
    }

    private fun tapPart(id: String) {
        val g = scene.game
        val s = g.state
        val part = BikeParts.of(id) ?: return
        if (id in s.ownedBikeParts) {
            g.toast("이미 장착한 부속품이에요!")
            return
        }
        if (s.money < part.cost) {
            g.toast("돈이 부족해요… (${won(part.cost)})")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        s.money -= part.cost
        s.ownedBikeParts.add(id)
        SaveManager.save(g.context, s)
        g.toast("${part.emoji} ${part.name} 장착 완료!")
        g.sfx(Audio.Sfx.BUY)
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val pw = minOf(w * 0.92f, dp(scene, 440f))
        val ph = minOf(h * 0.94f, dp(scene, 432f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 8f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 34f))
        textP.textSize = textDp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("close", closeRect.centerX() - textP.measureText("close") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = textDp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("자전거 상점", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 30f), textP)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(
            "탈것은 자전거뿐! 모델을 고르고 도색·부속품으로 꾸며보세요 · 보유 ${won(s.money)}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 46f), textP
        )

        // 탭
        tabRects.clear()
        val tabW = dp(scene, 88f)
        var tx = panelR.centerX() - (tabW * 3f + dp(scene, 12f)) / 2f
        for (t in Tab.values()) {
            val r = RectF(tx, panelR.top + dp(scene, 54f), tx + tabW, panelR.top + dp(scene, 78f))
            val on = t == tab
            drawButton(
                c, scene, r, t.label,
                if (on) 0xFFF2B63C.toInt() else Color.argb(80, 200, 190, 175),
                if (on) 0xFF4A3728.toInt() else Color.argb(160, 74, 55, 40),
                11.5f
            )
            tabRects.add(r to t)
            tx += tabW + dp(scene, 6f)
        }

        // 보이지 않는 이전 탭의 버튼이 현재 탭의 터치를 가로채지 않도록 한꺼번에 비운다.
        modelRects.clear()
        swatchRects.clear()
        partRects.clear()
        pageRects.clear()
        when (tab) {
            Tab.MODEL -> drawModels(c)
            Tab.PAINT -> drawPaint(c)
            Tab.PARTS -> drawParts(c)
        }
    }

    private fun pctText(v: Float): String {
        val n = (v * 100).toInt()
        return if (n >= 0) "+$n%" else "$n%"
    }

    private fun drawModels(c: Canvas) {
        val g = scene.game
        val s = g.state
        // 짧은 가로 화면에서는 행을 화면 밖에 그리지 않고 페이지 수를 늘린다.
        val perPage = ((panelR.height() - dp(scene, 124f)) / dp(scene, 58f)).toInt().coerceIn(1, 5)
        val totalPages = (Bikes.ALL.size + perPage - 1) / perPage
        page = page.coerceIn(0, totalPages - 1)
        val pageIdx = page


        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        var ty = panelR.top + dp(scene, 88f)
        val from = pageIdx * perPage
        val items = Bikes.ALL.subList(from, minOf(from + perPage, Bikes.ALL.size))
        for (bike in items) {
            val r = RectF(left, ty, right, ty + dp(scene, 52f))
            val equipped = bike.id == s.bikeId
            val owned = bike.id in s.ownedBikes
            fillP.color = if (equipped) 0xFFFDF3D8.toInt() else 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = if (equipped) 0xFFD9A03C.toInt() else 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, if (equipped) 2.5f else 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)

            // 모델 미리보기 (현재 도색·부속품 그대로) — 옆모습 한 장만 그린다(HD)
            val iw = dp(scene, 44f)
            c.drawBitmap(
                g.assets.bikePreviewFrame(s.gender, s.gearTier(), s.bikeStyleOf(bike.id)), null,
                RectF(r.left + dp(scene, 4f), r.top + dp(scene, 4f), r.left + dp(scene, 4f) + iw, r.top + dp(scene, 4f) + iw),
                pixelPaint
            )

            textP.textSize = textDp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            UiKit.drawIconText(c, scene.game, "${bike.emoji} ${bike.name}", r.left + dp(scene, 54f), r.top + dp(scene, 17f), textP)
            textP.textSize = textDp(scene, 9.2f)
            textP.color = 0xFF8A7360.toInt()
            val stat = "속도 ${pctText(bike.speed - 1f)} · 배고픔 ${pctText(bike.hunger - 1f)} · 새 놀람 ${pctText((bike.scare - 1.4f) / 1.4f)}" +
                (if (bike.luck > 0) " · 행운 +${bike.luck}" else "")
            c.drawText(stat, r.left + dp(scene, 54f), r.top + dp(scene, 32f), textP)
            textP.textSize = textDp(scene, 8.6f)
            textP.color = 0xFFA08A72.toInt()
            c.drawText(bike.desc, r.left + dp(scene, 54f), r.top + dp(scene, 45f), textP)

            val br = RectF(r.right - dp(scene, 88f), r.centerY() - dp(scene, 15f), r.right - dp(scene, 10f), r.centerY() + dp(scene, 15f))
            if (equipped) {
                drawButton(c, scene, br, "장착중", 0xFFD7E3C3.toInt(), 0xFF4A3728.toInt(), 11f)
            } else if (owned) {
                drawButton(c, scene, br, "장착", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 11.5f)
            } else {
                drawButton(c, scene, br, won(bike.cost), 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10f)
            }
            modelRects.add(r to bike.id)
            ty += dp(scene, 58f)
        }

        // 페이지 이동
        if (totalPages > 1) {
            val py = panelR.bottom - dp(scene, 32f)
            val prev = RectF(left, py, left + dp(scene, 72f), py + dp(scene, 24f))
            val next = RectF(right - dp(scene, 72f), py, right, py + dp(scene, 24f))
            drawButton(c, scene, prev, "arrow_left 이전", if (pageIdx > 0) 0xFFF2B63C.toInt() else Color.argb(90, 200, 190, 175), 0xFF4A3728.toInt(), 11f)
            drawButton(c, scene, next, "arrow_right 다음", if (pageIdx < totalPages - 1) 0xFFF2B63C.toInt() else Color.argb(90, 200, 190, 175), 0xFF4A3728.toInt(), 11f)
            if (pageIdx > 0) pageRects.add(prev to pageIdx - 1)
            if (pageIdx < totalPages - 1) pageRects.add(next to pageIdx + 1)
            textP.textSize = textDp(scene, 11f)
            textP.color = 0xFF6B4F35.toInt()
            val pt = "${pageIdx + 1}/$totalPages"
            c.drawText(pt, panelR.centerX() - textP.measureText(pt) / 2f, py + dp(scene, 16.5f), textP)
        }
    }

    private fun drawPaint(c: Canvas) {
        val g = scene.game
        val s = g.state

        // 큰 미리보기 — 지금 고른 도색·부속품을 바로 보여 준다.
        // (여기서도 한 장만 그린다. 타는 데 필요한 전체 세트는 배경에서 미리 만들어 둔다)
        val pv = dp(scene, 96f)
        val px = panelR.left + dp(scene, 20f)
        val py = panelR.top + dp(scene, 92f)
        c.drawBitmap(
            g.assets.bikePreviewFrame(s.gender, s.gearTier(), s.bikeStyle()), null,
            RectF(px, py, px + pv, py + pv), pixelPaint
        )
        // 도색을 바꾸면 월드에서 쓸 세트도 새로 필요하다 — 바뀐 순간에만 배경 준비를 건다
        val warmKey = "${s.bikeFrameColor}|${s.bikeTireColor}|${s.bikeSaddleColor}|${s.bikeId}"
        if (warmKey != warmedBikeKey) {
            warmedBikeKey = warmKey
            g.assets.warmSprites {
                g.assets.bikeSet(s.gender, s.gearTier(), s.bikeStyle())
            }
        }
        textP.textSize = textDp(scene, 12.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText(s.bike().name, px + pv + dp(scene, 16f), py + dp(scene, 26f), textP)

        var ty = minOf(panelR.top + dp(scene, 206f), panelR.bottom - dp(scene, 136f))
        drawSwatchRow(c, "프레임", 0, BikeColors.FRAME, s.bikeFrameColor, ty)
        ty += dp(scene, 44f)
        drawSwatchRow(c, "바퀴", 1, BikeColors.TIRE, s.bikeTireColor, ty)
        ty += dp(scene, 44f)
        drawSwatchRow(c, "안장·그립", 2, BikeColors.SADDLE, s.bikeSaddleColor, ty)
    }

    private fun drawSwatchRow(c: Canvas, label: String, slot: Int, colors: List<BikeColors.BikeColor>, sel: Int, ty: Float) {
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText(label, panelR.left + dp(scene, 16f), ty + dp(scene, 16f), textP)
        var x = panelR.left + dp(scene, 86f)
        for (i in colors.indices) {
            val cx = x + dp(scene, 10f)
            val cy = ty + dp(scene, 11f)
            fillP.color = colors[i].argb
            c.drawCircle(cx, cy, dp(scene, 9.5f), fillP)
            strokeP.color = if (i == sel) 0xFF4A3728.toInt() else 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, if (i == sel) 2.5f else 1.2f)
            c.drawCircle(cx, cy, dp(scene, 9.5f), strokeP)
            swatchRects.add(
                RectF(cx - dp(scene, 12f), cy - dp(scene, 12f), cx + dp(scene, 12f), cy + dp(scene, 12f)) to
                    intArrayOf(slot, i)
            )
            x += dp(scene, 23f)
        }
    }

    private fun drawParts(c: Canvas) {
        val g = scene.game
        val s = g.state
        var ty = panelR.top + dp(scene, 88f)
        val rowH = minOf(dp(scene, 60f), (panelR.bottom - dp(scene, 12f) - ty) / BikeParts.ALL.size)
        for (part in BikeParts.ALL) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH - dp(scene, 6f))
            fillP.color = 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)

            textP.textSize = textDp(scene, 20f)
            UiKit.drawIconText(c, scene.game, part.emoji, r.left + dp(scene, 10f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            textP.textSize = textDp(scene, 13f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(part.name, r.left + dp(scene, 44f), r.top + dp(scene, 18f), textP)
            textP.textSize = textDp(scene, 9.6f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(part.desc, r.left + dp(scene, 44f), r.top + dp(scene, 34f), textP)

            val owned = part.id in s.ownedBikeParts
            val br = RectF(r.right - dp(scene, 88f), r.centerY() - dp(scene, 15f), r.right - dp(scene, 10f), r.centerY() + dp(scene, 15f))
            if (owned) {
                drawButton(c, scene, br, "장착중", Color.argb(90, 200, 190, 175), Color.argb(140, 74, 55, 40), 11f)
            } else {
                drawButton(c, scene, br, won(part.cost), 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
                partRects.add(br to part.id)
            }
            ty += rowH
        }
    }
}

// ---------------------------------------------------------------------------
// 피자 굽기 미니게임 — 계열(화덕피자/일반 피자)별 메뉴 선택 + 타이밍
//   화덕(🔥): 화덕피자 — 커서가 빠르고 노랑 구간이 좁아 금방 탄다. 대신 효과가 크다.
//   오븐(🍕): 일반 피자 — 느긋하게 익는다. 굽기 쉽고 든든하다.
// ---------------------------------------------------------------------------

class BakeOverlay(
    scene: Scene,
    private val kind: PizzaKind = PizzaKind.OVEN,
    private val dough: Dough = Dough.CLASSIC,
    private val topping: Ingredients.ToppingDef? = null
) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true



    private var step = 0                 // 0 메뉴 선택, 1 타이밍, 2 결과
    private var pizzaId = Pizzas.representative(kind).id
    private var t = 0.6f
    private var stopped = false
    private var resultQ = -1
    private var lostPizza = false
    private val menuRects = ArrayList<Pair<RectF, Int>>()
    private var cancelRect = RectF()

    // [P07] ② 피자 선택 확장 — 특산 탭(현재 지역 재료) + 선택한 특산 재료 + 도우 보너스 문구
    private var specialTab = false
    private var activeTopping: Ingredients.ToppingDef? = topping
    private var doughBonusTxt: String? = null
    private val specialRects = ArrayList<Pair<RectF, Int>>()   // 0 = 특산 피자 카드, 1 = 재료 사들이기
    private val kindTabRects = ArrayList<Pair<RectF, Boolean>>()

    // [P07] 일반 메뉴는 기본 12종 — 특산 피자(id 12~19)는 아래 "✈ 특산" 탭에서만 고른다
    private val menu: List<PizzaDef> = Pizzas.ofKind(kind).filter { !Ingredients.isSpecial(it.id) }
    private val availableMenu: List<PizzaDef> get() = menu.filter { scene.game.state.isPizzaUnlocked(it.id) }

    /** [P07] 지금 굽는 피자 (특산 탭에서 고르면 특산 피자) */
    private val def: PizzaDef get() = if (activeTopping != null) activeTopping!!.pizza else Pizzas.of(pizzaId)

    /** [P07] 이 조리기구에서 현재 지역 특산 재료를 살 수 있는지 (특산은 전부 화덕피자) */
    private val localTopping: Ingredients.ToppingDef?
        get() = if (kind == PizzaKind.OVEN) Ingredients.toppingsFor(scene.game.state.region).firstOrNull() else null

    /** [P07] 도우가 커서 속도에 곱해진다 */
    private val speedF: Float get() = def.cursorSpeed * dough.speedF

    /** [P07] 도우가 판정 폭(걸작/맛있는)을 좁히거나 넓힌다 */
    private val perfectHalf: Float get() = def.perfectW / 2f * dough.zoneScale
    private val goodW: Float get() = kind.goodW * dough.zoneScale

    private fun cursorPos(): Float = 0.5f + 0.5f * sin(t * 2.6f)

    override fun update(dt: Float) {
        if (step == 1 && !stopped) t += dt * speedF
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        when (step) {
            0 -> {
                if (input.justB || input.justBack) { cancelBake(); return }
                if (tap == null) return
                // [P07] 하단 서브탭: 🍕 메뉴 / ✈ 특산
                for ((r, special) in kindTabRects) {
                    if (r.contains(tap.x, tap.y)) {
                        if (specialTab != special) { specialTab = special; scene.game.sfx(Audio.Sfx.TAP, 0.6f) }
                        return
                    }
                }
                // [P07] 특산 탭 — 피자 굽기 / 재료 사들이기
                if (specialTab) {
                    val tp = localTopping
                    if (tp != null) {
                        for ((r, which) in specialRects) {
                            if (!r.contains(tap.x, tap.y)) continue
                            if (which == 1) buyTopping(tp) else startBake(tp)
                            return
                        }
                    }
                } else {
                    for ((r, id) in menuRects) {
                        if (r.contains(tap.x, tap.y)) { startBake(null, id); return }
                    }
                }
                if (cancelRect.contains(tap.x, tap.y)) {
                    cancelBake()
                }
            }
            1 -> {
                if (input.justA || tap != null) stopBake()
            }
            2 -> {
                if (input.justA || input.justB || input.justBack || tap != null) finished = true
            }
        }
    }

    /** 굽기 전 취소 — 도우는 아직 불에 들어가지 않았으니 결제금을 돌려준다 */
    private fun cancelBake() {
        val g = scene.game
        if (dough.price > 0) {
            g.state.money += dough.price
            SaveManager.save(g.context, g.state)
            g.toast("${dough.icon} ${dough.label} 취소 — ${won(dough.price)} 환불됐어요")
        }
        g.sfx(Audio.Sfx.TAP, 0.5f)
        finished = true
    }

    /** [P07] 굽기 시작 — 특산 재료는 재고를 먼저 소모하고, 없으면 막는다 */
    private fun startBake(tp: Ingredients.ToppingDef?, id: Int = -1) {
        val g = scene.game
        // 가방이 가득이면 굽기 전에 막는다 — 재료만 날리고 피자를 잃는 일 방지
        if (g.state.pizzaCount >= g.state.pizzaCapEff()) {
            g.toast("피자 가방이 가득 찼어요! 먼저 한 판 먹고 오세요 🍕")
            g.sfx(Audio.Sfx.FAIL, 0.55f)
            return
        }
        val selectedId = tp?.pizzaId ?: id
        if (!g.state.isPizzaUnlocked(selectedId)) {
            g.toast("🔒 아직 발견하지 못한 피자 레시피예요. 메인 이야기를 진행해 보세요!")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        if (tp != null) {
            if (!Ingredients.consume(g.context, tp.id)) {
                g.toast("${tp.icon} ${tp.label} 재료가 없어요! 아래 '재료 사들이기'로 먼저 사 오세요 (${won(tp.price)})")
                g.sfx(Audio.Sfx.FAIL, 0.55f)
                return
            }
            activeTopping = tp
            pizzaId = tp.pizzaId
        } else {
            activeTopping = null
            pizzaId = id
        }
        step = 1
        t = 0.6f
        g.sfx(Audio.Sfx.TAP, 0.6f)
        // 🔥 화덕은 장작불 소리 크게, 가정용 오븐은 은은하게
        g.audio.playAmb(R.raw.amb_fire, if (kind == PizzaKind.OVEN) 0.5f else 0.25f)
    }

    /** [P07] 특산 재료 구매 — 골드는 여기에서 차감 (Ingredients.buy는 재고만) */
    private fun buyTopping(tp: Ingredients.ToppingDef) {
        val g = scene.game
        val s = g.state
        if (s.money < tp.price) {
            g.toast("돈이 부족해요… ${tp.icon} ${tp.label}은(는) ${won(tp.price)}")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        s.money -= tp.price
        Ingredients.buy(g.context, tp.id)
        SaveManager.save(g.context, s)
        g.toast("${tp.icon} ${tp.label} 재료 팬트리에 보관! (남은 돈 ${won(s.money)})")
        g.sfx(Audio.Sfx.BUY)
    }

    private fun stopBake() {
        stopped = true
        step = 2
        val g = scene.game
        val pos = cursorPos()
        val half = perfectHalf
        val gw = goodW
        resultQ = when {
            pos < 0.5f - half - gw || pos > 0.5f + half + gw -> 0
            abs(pos - 0.5f) <= half -> 2
            else -> 1
        }
        lostPizza = !g.state.addPizza(pizzaId, resultQ)
        // [P07] 도우 보너스는 굽자마자 바로 (피자에 붙어 다니지 않는다)
        doughBonusTxt = Ingredients.applyDoughBonus(g.state, dough)
        if (!lostPizza) {
            Healing.bumpToday(g.state, "pizzasBakedToday")
            if (resultQ == 2) Healing.unlock(g.state, "pizza_master")
        }
        // 화덕의 충격을 몸으로 — 걸작일수록 크게 울린다
        when (resultQ) {
            2 -> { g.shake(0.3f); g.punchZoom(0.05f) }
            1 -> g.shake(0.16f)
            else -> g.kick(0f, 1f, 1.4f)
        }
        SaveManager.save(g.context, g.state)
        g.audio.stopAmb()   // 불 소리 끄기
        when (resultQ) {
            2 -> g.sfx(Audio.Sfx.SPARKLE)          // 걸작!
            1 -> g.sfx(Audio.Sfx.SUCCESS, 0.8f)    // 맛있는
            else -> g.sfx(Audio.Sfx.FAIL, 0.6f)    // 살짝 탐
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        dim(c, scene)
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val cw = minOf(w * 0.84f, dp(scene, 500f))
        // [P07] ①도우 → ②선택 3단 구성이라 선택 화면은 서브탭/팬트리 줄 만큼 더 높다
        val chh = if (step == 0) minOf(h - dp(scene, 16f), dp(scene, 306f)) else dp(scene, 240f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val a = g.assets
        drawStationArt(c, scene, kind, RectF(r.right - dp(scene, 52f), r.top + dp(scene, 3f), r.right - dp(scene, 8f), r.top + dp(scene, 45f)))

        when (step) {
            0 -> {
                textP.textSize = textDp(scene, 16f)
                textP.color = 0xFF4A3728.toInt()
                val title = "${kind.emoji} ${kind.label} — 무엇을 구울까요?"
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.top + dp(scene, 24f), textP)
                textP.textSize = textDp(scene, 9.5f)
                textP.color = kind.tint
                val descW = minOf(textP.measureText(kind.desc), r.width() - dp(scene, 130f))
                drawFitText(c, scene, kind.desc, r.centerX() - descW / 2, r.top + dp(scene, 38f), r.width() - dp(scene, 130f), 9.5f)

                // 3열 × 2행 메뉴 카드 ([P07] ✈ 특산 탭이면 현재 지역 특산 1종 + 팬트리)
                menuRects.clear()
                specialRects.clear()
                val tp = localTopping
                val spec = specialTab && tp != null

                val cols = 3
                val gapX = dp(scene, 10f)
                val gapY = dp(scene, 8f)
                val top = r.top + dp(scene, 48f)
                val bottomArea = r.bottom - dp(scene, 62f)
                val list = if (spec) listOf(tp!!.pizza) else availableMenu
                if (list.isEmpty()) {
                    textP.textSize = textDp(scene, 12f)
                    textP.color = 0xFF8A7360.toInt()
                    c.drawText("📖 메인 이야기를 진행하면 새 레시피를 발견해요!", r.left + dp(scene, 18f), r.centerY(), textP)
                }
                val rows = ((list.size + cols - 1) / cols).coerceAtLeast(1)
                val cardW = (r.width() - dp(scene, 14f) * 2 - gapX * (cols - 1)) / cols
                val cardH = (((bottomArea - top) - gapY * (rows - 1)) / rows).coerceAtMost(dp(scene, 96f))
                // [P07] 행이 적으면(특산 1종) 세로 가운데로 — 윗공간만 비어 보이는 것 방지
                val gridH = (cardH * rows + gapY * (rows - 1)).coerceAtMost(bottomArea - top)
                val gridTop = top + ((bottomArea - top) - gridH) / 2f
                for ((i, p) in list.withIndex()) {
                    val col = i % cols
                    val row = i / cols
                    val cr = RectF(
                        r.left + dp(scene, 14f) + col * (cardW + gapX), gridTop + row * (cardH + gapY),
                        r.left + dp(scene, 14f) + col * (cardW + gapX) + cardW, gridTop + row * (cardH + gapY) + cardH
                    )
                    UiKit.card(c, g, cr, 10f, spec, kind.tint, 2f)

                    // 아이콘 (왼쪽) + 이름/효과/난이도 (오른쪽)
                    val isz = minOf(dp(scene, 36f), cardH - dp(scene, 14f))
                    drawPizzaArt(c, scene, p.id, cr.left + dp(scene, 8f) + isz / 2f, cr.centerY(), isz)
                    val tx = cr.left + dp(scene, 8f) + isz + dp(scene, 6f)
                    val textW = cr.right - dp(scene, 6f) - tx
                    // 보유 수 배지 (오른쪽 위) — [P07] 특산은 "✈ 특산" 뱃지
                    val have = g.state.pizzaCountOf(p.id)
                    var badgeW = 0f
                    run {
                        val badge = if (spec && !g.state.isPizzaUnlocked(p.id)) "🔒 이야기 해금" else if (spec) "✈ 특산" else if (have > 0) "×$have" else null
                        if (badge != null) {
                            textP.textSize = textDp(scene, 9f)
                            badgeW = textP.measureText(badge) + dp(scene, 10f)
                            UiKit.badge(c, g, RectF(cr.right - dp(scene, 6f) - badgeW, cr.top + dp(scene, 5f), cr.right - dp(scene, 6f), cr.top + dp(scene, 19f)),
                                badge, if (spec) 0xFF57B894.toInt() else 0xFFF2B63C.toInt(), if (spec) 0xFFFFFBF0.toInt() else 0xFF4A2E12.toInt(), 9f)
                            badgeW += dp(scene, 4f)
                        }
                    }
                    textP.color = 0xFF4A3728.toInt()
                    drawFitText(c, scene, "${p.emoji} ${p.name}", tx, cr.top + dp(scene, 17f), textW - badgeW, 12f)
                    textP.color = 0xFF8A7360.toInt()
                    drawFitText(c, scene, "배고픔 ${if (p.hungerBonus >= 0) "+" else ""}${p.hungerBonus} · 행운 ${if (p.luckBonus >= 0) "+" else ""}${p.luckBonus}", tx, cr.top + dp(scene, 31f), textW, 9.5f)
                    // 난이도 — 라벨 + 컬러 도트
                    textP.textSize = textDp(scene, 9.5f)
                    val dLabel = "구우기 "
                    textP.color = 0xFF8A7360.toInt()
                    c.drawText(dLabel, tx, cr.top + dp(scene, 44f), textP)
                    textP.color = 0xFFE8830C.toInt()
                    c.drawText(p.difficultyDots(), tx + textP.measureText(dLabel), cr.top + dp(scene, 44f), textP)
                    if (spec) { if (g.state.isPizzaUnlocked(p.id)) specialRects.add(cr to 0) } else menuRects.add(cr to p.id)
                }

                // [P07] 특산 탭 — 카드 옆에 팬트리(재료 사들이기) 패널
                if (spec) {
                    val pr = RectF(r.left + dp(scene, 14f) + cardW + gapX, gridTop, r.right - dp(scene, 14f), gridTop + cardH)
                    UiKit.stitchCard(c, g, pr, UiKit.CREAM_HI, UiKit.THREAD, 1.6f)
                    val stock = Ingredients.stock(g.context, tp.id)
                    textP.textSize = dp(scene, 12f)
                    textP.color = 0xFF4A3728.toInt()
                    UiKit.drawIconText(c, g, "🧺 ${Regions.byId[tp.regionId]?.name ?: tp.regionId} 팬트리", pr.left + dp(scene, 12f), pr.top + dp(scene, 20f), textP)
                    textP.textSize = dp(scene, 9.5f)
                    textP.color = 0xFF8A7360.toInt()
                    val ing = "${tp.icon} ${tp.label} 재료 ×$stock · 여행지에서 사 두면 집 화덕에서도 구워요"
                    drawFitText(c, scene, ing, pr.left + dp(scene, 12f), pr.top + dp(scene, 36f), pr.width() - dp(scene, 24f), 9.5f)
                    val canBuy = g.state.money >= tp.price
                    val br = RectF(pr.left + dp(scene, 12f), pr.bottom - dp(scene, 34f), pr.left + dp(scene, 150f), pr.bottom - dp(scene, 10f))
                    drawButton(c, scene, br, "재료 사들이기 ${won(tp.price)}",
                        if (canBuy) 0xFFF2B63C.toInt() else Color.argb(120, 200, 190, 175),
                        if (canBuy) 0xFF4A2E12.toInt() else Color.argb(150, 74, 55, 40), 10f)
                    specialRects.add(br to 1)
                    textP.textSize = dp(scene, 9f)
                    textP.color = if (stock > 0) 0xFF4E8A4E.toInt() else 0xFF8A7360.toInt()
                    val useTxt = if (!g.state.isPizzaUnlocked(tp.pizzaId)) "메인 이야기를 진행하면 레시피 해금!" else if (stock > 0) "왼쪽 카드를 탭하면 재료 1개 사용!" else "재료가 있어야 구울 수 있어요"
                    c.drawText(useTxt, br.right + dp(scene, 10f), pr.bottom - dp(scene, 18f), textP)
                    textP.textSize = dp(scene, 9f)
                    textP.color = 0xFF8A7360.toInt()
                    val moneyTxt = "💰 ${won(g.state.money)}"
                    c.drawText(moneyTxt, pr.right - dp(scene, 12f) - textP.measureText(moneyTxt), pr.top + dp(scene, 20f), textP)
                }

                // [P07] 하단 — 서브탭(🍕 메뉴 / ✈ 특산) + 그만두기 + 도우·팬트리 정보
                kindTabRects.clear()
                if (tp != null) {
                    val labels = listOf(false to "${kind.emoji} 메뉴", true to "✈ 특산")
                    var tbx = r.left + dp(scene, 14f)
                    for ((special, lbl) in labels) {
                        textP.textSize = dp(scene, 10.5f)
                        val tw = textP.measureText(lbl) + dp(scene, 18f)
                        val tr = RectF(tbx, r.bottom - dp(scene, 38f), tbx + tw, r.bottom - dp(scene, 12f))
                        if (specialTab == special) UiKit.cuteButton(c, g, tr, lbl, kind.tint, 0xFFFFFBF0.toInt(), 10.5f, depthDp = 2f)
                        else UiKit.cuteButton(c, g, tr, lbl, UiKit.PASTEL_SAND, UiKit.MUTED, 10.5f, depthDp = 2f)
                        kindTabRects.add(tr to special)
                        tbx += tw + dp(scene, 6f)
                    }
                }
                cancelRect = RectF(r.centerX() - dp(scene, 60f), r.bottom - dp(scene, 40f), r.centerX() + dp(scene, 60f), r.bottom - dp(scene, 12f))
                drawButton(c, scene, cancelRect, "그만두기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12f)
                textP.textSize = textDp(scene, 9f)
                textP.color = 0xFF8A7360.toInt()
                val bag = "가방 ${g.state.pizzaCount}/${g.state.pizzaCapEff()} · ${dough.icon} ${dough.label} · 팬트리 ${Ingredients.stockTotal(g.context)}개"
                val bagW = textP.measureText(bag)
                val bagX = r.right - dp(scene, 14f) - bagW
                drawFitText(c, scene, bag, bagX, r.bottom - dp(scene, 46f), bagW, 9f)
                textP.textSize = dp(scene, 8.5f)
                textP.color = kind.tint
                val dInfo = "도우 ×${"%.2f".format(dough.gaugeSpeed)} · ${dough.bonusLabel}"
                c.drawText(dInfo, r.right - dp(scene, 14f) - textP.measureText(dInfo), r.bottom - dp(scene, 22f), textP)
            }
            1 -> {
                val p = def
                textP.textSize = textDp(scene, 16f)
                textP.color = 0xFF4A3728.toInt()
                // [P07] 특산 피자 이름에는 지역명이 이미 들어 있다 ("춘천 닭갈비 화덕피자")
                val at = activeTopping
                val title = "${p.emoji} ${p.fullName} 굽기"
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.top + dp(scene, 26f), textP)
                textP.textSize = textDp(scene, 9.5f)
                textP.color = kind.tint
                // [P07] 도우 정보 — 고른 도우가 속도/판정/보너스를 바꾼다
                val sub = "${dough.icon} ${dough.label} · 속도 ×${"%.2f".format(dough.gaugeSpeed)} · ${dough.bonusLabel}" +
                    (if (at != null) " · ${at.icon} 재료" else "")
                drawFitText(c, scene, sub, r.centerX() - minOf(textP.measureText(sub), r.width() - dp(scene, 24f)) / 2,
                    r.top + dp(scene, 41f), r.width() - dp(scene, 24f), 9.5f)

                // 게이지 — 글로스 + 걸작존 글로우 + 프리미엄 커서
                val gx = r.left + dp(scene, 26f)
                val gy = r.centerY() - dp(scene, 2f)
                val gw = r.width() - dp(scene, 52f)
                val gh = dp(scene, 28f)
                val half = perfectHalf        // [P07] 도우 보정 포함
                val goodW = this.goodW        // [P07] 도우 보정 포함
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
                    Triple(0f, 0.5f - half - goodW, 0xFFE2574C.toInt()),
                    Triple(0.5f - half - goodW, 0.5f - half, 0xFFF2D06B.toInt()),
                    Triple(0.5f - half, 0.5f + half, 0xFF6FBA6B.toInt()),
                    Triple(0.5f + half, 0.5f + half + goodW, 0xFFF2D06B.toInt()),
                    Triple(0.5f + half + goodW, 1f, 0xFFE2574C.toInt())
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
                // 삼각 포인터 (위)
                fillP.color = 0xFF2E2118.toInt()
                val triS = dp(scene, 5f)
                val triTop = Path().apply {
                    moveTo(cx, gy - dp(scene, 8f) - triS)
                    lineTo(cx - triS * 0.8f, gy - dp(scene, 8f) - triS * 2.2f)
                    lineTo(cx + triS * 0.8f, gy - dp(scene, 8f) - triS * 2.2f)
                    close()
                }
                c.drawPath(triTop, fillP)

                // 라벨 — 걸작 뱃지 + 양옆 탄 라벨
                textP.textSize = textDp(scene, 10.5f)
                textP.color = 0xFF8A7360.toInt()
                c.drawText("살짝 탐", gx, gy + gh + dp(scene, 18f), textP)
                val rightLbl = "살짝 탐"
                c.drawText(rightLbl, gx + gw - textP.measureText(rightLbl), gy + gh + dp(scene, 18f), textP)
                val midTxt = "걸작!"
                textP.textSize = textDp(scene, 10.5f)
                val midW = textP.measureText(midTxt) + dp(scene, 16f)
                UiKit.badge(
                    c, g, RectF(gx + gw / 2f - midW / 2f, gy - dp(scene, 38f), gx + gw / 2f + midW / 2f, gy - dp(scene, 20f)),
                    midTxt, 0xFF6FBA6B.toInt(), 0xFFFFF8E8.toInt(), 10.5f
                )

            }
            else -> {
                val q = PizzaQ.of(resultQ)
                val p = def
                val perfect = resultQ == 2
                val title = if (lostPizza) "${p.emoji} ${q.label}… 그런데 두 손이 가득!" else "${p.emoji} ${p.name} ${q.label} 완성!"
                textP.textSize = textDp(scene, 16f)
                textP.color = if (perfect) 0xFFB5651D.toInt() else 0xFF4A3728.toInt()
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.centerY() - dp(scene, 52f), textP)
                // 품질 별점 + 계열
                textP.textSize = textDp(scene, 14f)
                textP.color = 0xFFE8A33C.toInt()
                val starTxt = "★".repeat(q.stars) + "☆".repeat(3 - q.stars)
                val starW = textP.measureText(starTxt)
                textP.textSize = textDp(scene, 10f)
                val kl = "  ${kind.emoji} ${kind.label}"
                val klW = textP.measureText(kl)
                val sx0 = r.centerX() - (starW + klW) / 2f
                textP.textSize = textDp(scene, 14f)
                c.drawText(starTxt, sx0, r.centerY() - dp(scene, 32f), textP)
                textP.textSize = textDp(scene, 10f)
                textP.color = kind.tint
                c.drawText(kl, sx0 + starW, r.centerY() - dp(scene, 32f), textP)

                // 피자(종류별 아이콘) + 후광
                val k = dp(scene, 3.4f)
                val bmp = a.pizzaArt(p.id)
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

                // [P07] 도우/특산 재료 정보 — 도우 보너스는 구운 순간 바로 적용된다
                run {
                    val at = activeTopping
                    val extra = buildString {
                        append("${dough.icon} ${dough.label}")
                        if (doughBonusTxt != null) append(" · $doughBonusTxt")
                        if (at != null) append(" · ${at.icon} ${at.regionName} 특산 재료")
                    }
                    textP.textSize = dp(scene, 10f)
                    textP.color = kind.tint
                    drawFitText(c, scene, extra, r.centerX() - minOf(textP.measureText(extra), r.width() - dp(scene, 24f)) / 2,
                        r.bottom - dp(scene, 48f), r.width() - dp(scene, 24f), 10f)
                }
                val info = if (lostPizza) "피자 가방이 가득해서 못 챙겼어요… (최대 ${g.state.pizzaCapEff()}개)"
                else "먹으면 배고픔 +${q.hunger + p.hungerBonus} · 행운 +${q.luck + p.luckBonus}"
                textP.textSize = textDp(scene, 12f)
                val infoW = (textP.measureText(info) + dp(scene, 24f)).coerceAtMost(r.width() - dp(scene, 24f))
                UiKit.badge(
                    c, g, RectF(r.centerX() - infoW / 2f, r.bottom - dp(scene, 40f), r.centerX() + infoW / 2f, r.bottom - dp(scene, 18f)),
                    info, 0xFF6B4F35.toInt(), 0xFFF8EFDC.toInt(), 12f
                )
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
    private val prevLevel: Int = 1,
    private val reachTiles: Float = 0f,
    private val exif: String = "",
    private val notes: List<String> = emptyList(),
    private val capturedPhoto: Bitmap? = null,
    private val birdFacing: BirdFacing = BirdFacing.LEFT,
    private val birdPose: BirdPose = BirdPose.PERCHED
) : Overlay(scene) {

    private var t = 0f
    private var cuedStars = false
    private var cuedNew = false
    private var cuedQuest = false
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)

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

        val inset = dp(scene, 11f)
        val landscape = capturedPhoto != null
        val photoAspect = capturedPhoto?.let { it.height.toFloat() / it.width } ?: 0.74f
        val maxW = if (landscape) minOf(w * 0.82f, dp(scene, 520f)) else minOf(w * 0.62f, dp(scene, 340f))
        val maxH = h * 0.76f
        // 원근 풍경의 양옆을 잘라내지 않는 가로 인화지. 캡션/별점 공간은 그대로 확보한다.
        val footer = dp(scene, 96f)
        val cardW = if (landscape) minOf(maxW, (maxH - inset - footer) / photoAspect + inset * 2f)
            else minOf(maxW, maxH / 1.26f)
        val cardH = if (landscape) (cardW - inset * 2f) * photoAspect + inset + footer else cardW * 1.26f
        val cx = w / 2f
        val cy = h * 0.5f - dp(scene, 6f)

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
        val photoH = minOf(photoW * photoAspect, cardH - inset - footer)
        val photo = RectF(card.left + inset, card.top + inset, card.left + inset + photoW, card.top + inset + photoH)
        drawPhoto(c, photo)
        // EXIF 스트립 — 어떤 설정으로 찍혔는지
        if (exif.isNotEmpty()) {
            val stripH = dp(scene, 13f)
            fillP.color = Color.argb(160, 20, 18, 26)
            c.drawRect(photo.left, photo.bottom - stripH, photo.right, photo.bottom, fillP)
            textP.textSize = textDp(scene, 8.5f)
            textP.color = 0xFFF2E3C2.toInt()
            c.drawText(exif, photo.left + dp(scene, 5f), photo.bottom - dp(scene, 4f), textP)
        }
        strokeP.color = Color.argb(46, 40, 32, 24)
        strokeP.strokeWidth = dp(scene, 1f)
        c.drawRoundRect(photo, 0f, 0f, strokeP)

        // 캡션: 이름
        val capTop = photo.bottom + dp(scene, 12f)
        textP.textSize = textDp(scene, 17f)
        textP.color = 0xFF3B2F24.toInt()
        val nm = def.name
        c.drawText(nm, card.centerX() - textP.measureText(nm) / 2, capTop + dp(scene, 12f), textP)

        // 캡션: 등급 · 영문명
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFF8A7360.toInt()
        val tierColor = when (def.tier) {
            Tier.COMMON -> 0xFF777F82.toInt()
            Tier.UNCOMMON -> 0xFF5E934F.toInt()
            Tier.RARE -> 0xFF3F6FB0.toInt()
            Tier.LEGEND -> 0xFFB65342.toInt()
        }
        val numPrefix = if (def.birdNum > 0) "No. ${String.format("%03d", def.birdNum)} · " else ""
        val sub = "$numPrefix${def.tier.label} · ${def.seasonLabel} · ${def.timeWindowLabel}" +
                (if (def.englishName.isNotBlank()) " · ${def.englishName}" else "")
        c.drawText(sub, card.centerX() - textP.measureText(sub) / 2, capTop + dp(scene, 28f), textP)
        fillP.color = tierColor
        c.drawCircle(card.centerX() - textP.measureText(sub) / 2 - dp(scene, 8f), capTop + dp(scene, 25f), dp(scene, 2.6f), fillP)

        // 별점 (하나씩 톡톡 튀어나온다)
        val starR = dp(scene, 13f)
        val gap = dp(scene, 34f)
        val starY = capTop + dp(scene, 50f)
        for (i in 0 until 3) {
            val p = ((t - 0.34f - i * 0.09f) / 0.16f).coerceIn(0f, 1f)
            val k = 0.5f + 0.5f * easeOutBack(p)
            val sx = card.centerX() + (i - 1) * gap
            if (i < stars) {
                fillP.color = Color.argb((255 * p).toInt().coerceIn(0, 255), 242, 190, 66)
                c.drawPath(starPath(sx, starY, starR * k), fillP)
                fillP.color = Color.argb((200 * p).toInt().coerceIn(0, 255), 255, 232, 158)
                c.drawPath(starPath(sx, starY, starR * k * 0.55f), fillP)
            } else {
                strokeP.color = Color.argb(150, 190, 178, 158)
                strokeP.strokeWidth = dp(scene, 1.6f)
                c.drawPath(starPath(sx, starY, starR * 0.86f), strokeP)
            }

        }

        // 캡션: 촬영 정보
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFF9A8570.toInt()
        var camTxt = cameraTxt
        val camMaxW = cardW - dp(scene, 130f)
        if (camTxt.isNotBlank() && textP.measureText(camTxt) > camMaxW && camMaxW > dp(scene, 40f)) {
            while (camTxt.length > 1 && textP.measureText("$camTxt…") > camMaxW) camTxt = camTxt.dropLast(1)
            camTxt = "$camTxt…"
        }
        var info = buildString {
            if (camTxt.isNotBlank()) append("$camTxt · ")
            if (distTiles > 0f) {
                append(String.format("%.1f", distTiles))
                if (reachTiles > 0f) append(String.format("/%.1f", reachTiles))
                append("칸 · ")
            }
            if (timeTxt.isNotBlank()) append("$timeTxt · ")
            append("${birdFacing.label} · ${birdPose.label} · 촬영 ${count}회")
        }
        val infoMaxW = cardW - dp(scene, 20f)
        if (textP.measureText(info) > infoMaxW) {
            while (info.length > 1 && textP.measureText("$info…") > infoMaxW) info = info.dropLast(1)
            info += "…"
        }
        c.drawText(info, card.centerX() - textP.measureText(info) / 2, card.bottom - dp(scene, 12f), textP)

        // 첫 발견 스티커
        if (isNew) {
            val sw = dp(scene, 74f)
            val sh = dp(scene, 24f)
            val sCx = card.right - dp(scene, 58f)
            val sCy = card.top + dp(scene, 22f)
            c.save()
            c.rotate(-9f, sCx, sCy)
            fillP.color = 0xFFE2574C.toInt()
            c.drawRoundRect(RectF(sCx - sw / 2f, sCy - sh / 2f, sCx + sw / 2f, sCy + sh / 2f), dp(scene, 4f), dp(scene, 4f), fillP)
            strokeP.color = 0xFFFFF3E2.toInt()
            strokeP.strokeWidth = dp(scene, 1.4f)
            c.drawRoundRect(RectF(sCx - sw / 2f + dp(scene, 2.5f), sCy - sh / 2f + dp(scene, 2.5f), sCx + sw / 2f - dp(scene, 2.5f), sCy + sh / 2f - dp(scene, 2.5f)), dp(scene, 3f), dp(scene, 3f), strokeP)
            textP.textSize = textDp(scene, 11f)
            textP.color = 0xFFFFF3E2.toInt()
            val st = "NEW 첫 발견"
            c.drawText(st, sCx - textP.measureText(st) / 2, sCy - (textP.descent() + textP.ascent()) / 2f, textP)
            c.restore()
        }

        c.restore()

        // 의뢰 보수 & 경험치
        var badgeY = cy + cardH / 2f + dp(scene, 24f)
        if (questLine != null) {
            textP.textSize = textDp(scene, 12.5f)
            val qw = textP.measureText(questLine) + dp(scene, 26f)
            val qr = RectF(cx - qw / 2f, badgeY - dp(scene, 14f), cx + qw / 2f, badgeY + dp(scene, 14f))
            fillP.color = Color.argb(220, 43, 38, 58)
            c.drawRoundRect(qr, dp(scene, 14f), dp(scene, 14f), fillP)
            strokeP.color = 0xFFF2D06B.toInt()
            strokeP.strokeWidth = dp(scene, 1.6f)
            c.drawRoundRect(qr, dp(scene, 14f), dp(scene, 14f), strokeP)
            textP.color = 0xFFF7E9A8.toInt()
            c.drawText(questLine, qr.centerX() - textP.measureText(questLine) / 2, qr.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            badgeY += dp(scene, 32f)
        }

        if (expGain > 0) {
            val expTxt = if (levelsGained > 0) "경험치 +  · 레벨 업!" else "경험치 +"
            textP.textSize = textDp(scene, 12f)
            val ew = textP.measureText(expTxt) + dp(scene, 24f)
            val er = RectF(cx - ew / 2f, badgeY - dp(scene, 13f), cx + ew / 2f, badgeY + dp(scene, 13f))
            fillP.color = Color.argb(215, 34, 48, 38)
            c.drawRoundRect(er, dp(scene, 13f), dp(scene, 13f), fillP)
            strokeP.color = 0xFF7EC87E.toInt()
            strokeP.strokeWidth = dp(scene, 1.4f)
            c.drawRoundRect(er, dp(scene, 13f), dp(scene, 13f), strokeP)
            textP.color = 0xFFB4F0B4.toInt()
            c.drawText(expTxt, er.centerX() - textP.measureText(expTxt) / 2, er.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        }

        // 촬영 노트 — 장비가 사진에 어떤 영향을 줬는지
        if (notes.isNotEmpty() && t > 0.5f) {
            textP.textSize = textDp(scene, 10f)
            var ny = badgeY + (if (expGain > 0) dp(scene, 30f) else dp(scene, 4f))
            for (n in notes.take(3)) {
                val bad = n.contains("노이즈") || n.contains("흔들") || n.contains("물방울")
                textP.color = Color.argb(235, if (bad) 246 else 176, if (bad) 176 else 232, if (bad) 156 else 176)
                val nt = (if (bad) "· " else "· ") + n
                c.drawText(nt, cx - textP.measureText(nt) / 2, ny, textP)
                ny += dp(scene, 13f)
            }
        }

    }

    /** 인화지 속 풍경 + 새 */
    private fun drawPhoto(c: Canvas, r: RectF) {
        val a = scene.game.assets
        // 새 눈높이에서 원근 투영한 촬영 원본. 수평선과 양옆 풍경까지 사진집과 똑같이 보존한다.
        val saved = capturedPhoto
        if (saved != null) {
            val scale = minOf(r.width() / saved.width.toFloat(), r.height() / saved.height.toFloat())
            val dw = saved.width * scale
            val dh = saved.height * scale
            val dx = r.centerX() - dw / 2f
            val dy = r.centerY() - dh / 2f
            c.save()
            c.clipRect(r)
            fillP.color = 0xFF27383D.toInt()
            c.drawRect(r, fillP)
            c.drawBitmap(saved, null, RectF(dx, dy, dx + dw, dy + dh), photoPaint)
            c.restore()
            return
        }

        val hab = def.habitats.firstOrNull() ?: "field"

        // 이전 세이브/파일 실패 때의 절차적 폴백 풍경
        // 하늘 (6단 밴드 — 이음새가 보이지 않게 겹쳐 그린다)
        val sky = when {
            night -> when (hab) {
                "coast", "water", "wetland" -> intArrayOf(
                    0xFF0E1630.toInt(), 0xFF162041.toInt(), 0xFF1F2A50.toInt(),
                    0xFF2A3560.toInt(), 0xFF374370.toInt(), 0xFF485480.toInt()
                )
                "city" -> intArrayOf(
                    0xFF151230.toInt(), 0xFF1D1940.toInt(), 0xFF272351.toInt(),
                    0xFF332E63.toInt(), 0xFF413B74.toInt(), 0xFF524C86.toInt()
                )
                else -> intArrayOf(
                    0xFF0C1526.toInt(), 0xFF132038.toInt(), 0xFF1B2C48.toInt(),
                    0xFF243857.toInt(), 0xFF304566.toInt(), 0xFF3E5275.toInt()
                )
            }
            hab == "coast" || hab == "water" -> intArrayOf(
                0xFF87CFE6.toInt(), 0xFF9EDCEA.toInt(), 0xFFB6E6EF.toInt(),
                0xFFCDEEF1.toInt(), 0xFFDFF4EF.toInt(), 0xFFEDF8EE.toInt()
            )
            hab == "city" -> intArrayOf(
                0xFF9CC2E4.toInt(), 0xFFB0D0EA.toInt(), 0xFFC4DCEF.toInt(),
                0xFFD6E5EE.toInt(), 0xFFE5EBE8.toInt(), 0xFFF1EFE4.toInt()
            )
            hab == "forest" || hab == "mountain" -> intArrayOf(
                0xFF83C3E8.toInt(), 0xFF9BD3EC.toInt(), 0xFFB5E0EF.toInt(),
                0xFFCBE9E9.toInt(), 0xFFDDF1E4.toInt(), 0xFFECF8EC.toInt()
            )
            else -> intArrayOf(
                0xFF8BCEEC.toInt(), 0xFFA4DCF0.toInt(), 0xFFBEE5F0.toInt(),
                0xFFD2EEE8.toInt(), 0xFFE2F4E2.toInt(), 0xFFF0F8E0.toInt()
            )
        }
        val skyH = r.height() * 0.64f
        val bandH = skyH / sky.size
        for (i in sky.indices) {
            fillP.color = sky[i]
            c.drawRect(r.left, r.top + bandH * i, r.right, r.top + bandH * (i + 1) + 1f, fillP)
        }
        // 지평선 안개 (하늘과 땅을 부드럽게 잇는다)
        fillP.color = Color.argb(150, 255, 255, 250)
        c.drawRect(r.left, r.top + skyH - bandH * 1.15f, r.right, r.top + skyH + r.height() * 0.012f, fillP)
        fillP.color = Color.argb(90, 255, 255, 250)
        c.drawRect(r.left, r.top + skyH - bandH * 2.0f, r.right, r.top + skyH - bandH * 1.15f, fillP)

        // 밤하늘 별 / 낮 구름
        if (night) {
            fillP.color = Color.argb(235, 252, 250, 236)
            for (i in 0 until 16) {
                val sx = r.left + r.width() * ((i * 37 % 100) / 100f)
                val sy = r.top + r.height() * 0.08f + r.height() * 0.34f * ((i * 61 % 100) / 100f)
                c.drawRect(sx, sy, sx + dp(scene, 1.6f), sy + dp(scene, 1.6f), fillP)
            }
        } else {
            fillP.color = Color.argb(230, 255, 255, 255)
            cloud(c, r.left + r.width() * 0.18f, r.top + r.height() * 0.17f, r.width() * 0.13f)
            cloud(c, r.left + r.width() * 0.66f, r.top + r.height() * 0.27f, r.width() * 0.17f)
        }

        // 해 / 달 (이모지는 폰트에 따라 깨질 수 있어 직접 그린다)
        val discX = r.left + r.width() * 0.78f
        val discY = r.top + r.height() * 0.2f
        if (night) {
            val mr = r.width() * 0.062f
            fillP.color = Color.argb(30, 247, 233, 168)
            c.drawCircle(discX, discY, mr * 2.3f, fillP)
            fillP.color = Color.argb(20, 247, 233, 168)
            c.drawCircle(discX, discY, mr * 3.3f, fillP)
            fillP.color = 0xFFF7E9A8.toInt()
            c.drawCircle(discX, discY, mr, fillP)
            fillP.color = Color.argb(150, 233, 222, 168)
            c.drawCircle(discX - mr * 0.34f, discY + mr * 0.2f, mr * 0.2f, fillP)
            c.drawCircle(discX + mr * 0.3f, discY - mr * 0.34f, mr * 0.14f, fillP)
        } else {
            val sr = r.width() * 0.05f
            fillP.color = Color.argb(46, 247, 206, 91)
            c.drawCircle(discX, discY, sr * 2.2f, fillP)
            fillP.color = Color.argb(30, 247, 206, 91)
            c.drawCircle(discX, discY, sr * 3.1f, fillP)
            fillP.color = 0xFFF7CE5B.toInt()
            c.drawCircle(discX, discY, sr, fillP)
            fillP.color = 0xFFFCEFC0.toInt()
            c.drawCircle(discX, discY, sr * 0.7f, fillP)
        }

        // 지형
        val groundY = r.bottom - r.height() * 0.24f
        when (hab) {
            "coast", "water" -> {
                fillP.color = 0xFF6FB7D2.toInt()
                c.drawRect(r.left, groundY, r.right, r.bottom, fillP)
                fillP.color = Color.argb(120, 255, 255, 255)
                for (i in 0 until 4) {
                    val wy = groundY + r.height() * (0.05f + i * 0.05f)
                    c.drawRect(r.left + r.width() * (0.08f + i * 0.07f), wy, r.left + r.width() * (0.34f + i * 0.07f), wy + dp(scene, 1.4f), fillP)
                }
                fillP.color = 0xFFE4D8B4.toInt()
                c.drawRect(r.left, r.bottom - r.height() * 0.09f, r.right, r.bottom, fillP)
            }
            "city" -> {
                fillP.color = 0xFFB9BCB4.toInt()
                c.drawRect(r.left, groundY, r.right, r.bottom, fillP)
                for (i in 0 until 5) {
                    val bw = r.width() * (0.11f + (i % 2) * 0.04f)
                    val bh = r.height() * (0.12f + (i * 7 % 5) * 0.045f)
                    val bx = r.left + r.width() * (0.06f + i * 0.19f)
                    fillP.color = if (i % 2 == 0) 0xFF9EA29B.toInt() else 0xFF8C918B.toInt()
                    c.drawRect(bx, groundY - bh, bx + bw, groundY, fillP)
                    fillP.color = if (night) Color.argb(230, 255, 208, 120) else Color.argb(120, 255, 255, 255)
                    for (wy in 0 until 3) {
                        for (wx in 0 until 2) {
                            c.drawRect(
                                bx + bw * (0.2f + wx * 0.42f), groundY - bh + bh * (0.16f + wy * 0.26f),
                                bx + bw * (0.2f + wx * 0.42f) + bw * 0.2f, groundY - bh + bh * (0.16f + wy * 0.26f) + bh * 0.13f,
                                fillP
                            )
                        }
                    }
                }
                fillP.color = 0xFF7E8478.toInt()
                c.drawRect(r.left, r.bottom - r.height() * 0.1f, r.right, r.bottom, fillP)
            }
            else -> {
                val farC = when (hab) {
                    "forest" -> 0xFF4F7C50.toInt()
                    "mountain" -> 0xFF5E7A63.toInt()
                    "wetland" -> 0xFF6A9A6A.toInt()
                    else -> 0xFF7FAE70.toInt()
                }
                fillP.color = Color.argb(95, Color.red(farC), Color.green(farC), Color.blue(farC))
                c.drawRect(r.left, groundY - r.height() * 0.045f, r.right, groundY + r.height() * 0.03f, fillP)
                fillP.color = farC
                c.drawCircle(r.left + r.width() * 0.20f, groundY + r.height() * 0.055f, r.width() * 0.21f, fillP)
                c.drawCircle(r.left + r.width() * 0.70f, groundY + r.height() * 0.085f, r.width() * 0.24f, fillP)
                c.drawCircle(r.left + r.width() * 0.98f, groundY + r.height() * 0.065f, r.width() * 0.19f, fillP)
                val nearC = when (hab) {
                    "forest" -> 0xFF6BA05F.toInt()
                    "mountain" -> 0xFF7C9A76.toInt()
                    "wetland" -> 0xFF87B57F.toInt()
                    else -> 0xFFA6CC7C.toInt()
                }
                fillP.color = nearC
                c.drawRect(r.left, groundY, r.right, r.bottom, fillP)
                // 풀 포기 (지면선 아래에만 — 언덕 실루엣을 해치지 않게)
                fillP.color = Color.argb(105, 56, 86, 48)
                val groundH = r.bottom - groundY
                for (i in 0 until 11) {
                    val gx = r.left + r.width() * ((i * 17 % 100) / 100f)
                    val gh2 = groundH * (0.34f + (i % 3) * 0.22f)
                    c.drawRect(gx, groundY, gx + dp(scene, 2f), groundY + gh2, fillP)
                }
            }
        }

        // 새
        val bmp = a.bird(def.id)
        val maxBW = r.width() * 0.66f
        val maxBH = r.height() * 0.46f
        // 멀리서 찍었을수록 새가 작게 나온다 (망원 컷 느낌)
        val rel = if (distTiles > 0f) {
            val reach = if (reachTiles > 0f) reachTiles else scene.game.state.rig().reach
            (distTiles / reach).coerceIn(0f, 1f)
        } else 0.4f
        val k = minOf(maxBW / bmp.width, maxBH / bmp.height) * (1f - 0.28f * rel)
        val bw = bmp.width * k
        val bh = bmp.height * k
        val bx = r.centerX() - bw / 2f + r.width() * 0.06f
        val by = groundY - bh + r.height() * 0.055f
        fillP.color = Color.argb(60, 34, 40, 32)
        c.drawOval(RectF(bx + bw * 0.14f, groundY + r.height() * 0.02f, bx + bw * 0.86f, groundY + r.height() * 0.07f), fillP)
        c.drawBitmap(bmp, null, RectF(bx, by, bx + bw, by + bh), a.sprPaint)

        // 인화 느낌: 비네트 + 그레인
        for (i in 0 until 4) {
            val d = r.width() * 0.035f * (4 - i)
            fillP.color = Color.argb(16, 30, 24, 20)
            c.drawRect(r.left + d, r.top, r.left + d + r.width() * 0.035f, r.bottom, fillP)
            c.drawRect(r.right - d - r.width() * 0.035f, r.top, r.right - d, r.bottom, fillP)
            c.drawRect(r.left, r.top + d, r.right, r.top + d + r.height() * 0.035f, fillP)
            c.drawRect(r.left, r.bottom - d - r.height() * 0.035f, r.right, r.bottom - d, fillP)
        }
        val gr = grainBmp
        if (gr != null) {
            c.save()
            c.clipRect(r)
            var y = r.top
            while (y < r.bottom) {
                var x = r.left
                while (x < r.right) {
                    c.drawBitmap(gr, x, y, grainPaintOverlay)
                    x += 256f
                }
                y += 256f
            }
            c.restore()
        }
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float) {
        fillP.color = Color.argb(225, 255, 255, 255)
        c.drawRoundRect(RectF(x, y, x + s * 1.8f, y + s * 0.5f), s * 0.25f, s * 0.25f, fillP)
        c.drawCircle(x + s * 0.5f, y + s * 0.05f, s * 0.42f, fillP)
        c.drawCircle(x + s * 1.1f, y + s * 0.14f, s * 0.32f, fillP)
    }

    private fun starPath(cx: Float, cy: Float, r: Float): Path {
        val p = Path()
        val inner = r * 0.44f
        for (i in 0 until 10) {
            val ang = (-90.0 + i * 36.0) * Math.PI / 180.0
            val rad = if (i % 2 == 0) r else inner
            val x = cx + (Math.cos(ang) * rad).toFloat()
            val y = cy + (Math.sin(ang) * rad).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    companion object {
        private val grainPaintOverlay = Paint().apply { alpha = 40 }

        private val grainBmp: Bitmap? by lazy {
            try {
                val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                val cv = Canvas(b)
                val p = Paint()
                val rnd = java.util.Random(4242L)
                repeat(2600) {
                    val bright = rnd.nextBoolean()
                    p.color = if (bright) Color.argb(30, 255, 250, 236) else Color.argb(34, 26, 20, 32)
                    val x = rnd.nextInt(256).toFloat()
                    val y = rnd.nextInt(256).toFloat()
                    cv.drawRect(x, y, x + 1f, y + 1f, p)
                }
                b
            } catch (_: Exception) {

                null
            }
        }
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
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true


    private var t = 0f

    init {
        // 🎉 팡파레 — 만세 모션과 함께. (축하 화면은 여기서 한 번만 뜨므로 init 이 맞다)
        scene.game.sfx(Audio.Sfx.LEVELUP, 0.9f)
    }

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
        // 높이는 캐릭터(32 도트 × 3.4dp)와 아래 3줄(레벨·칭호·숙련 포인트)에
        // 장비 안내 한 줄까지 겹치지 않고 들어가는 값이다 (244dp 였을 때는 겹쳤다)
        val chh = dp(scene, 268f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val ringA = (0.4f + 0.6f * abs(sin(t * 3.2f)))
        strokeP.color = Color.argb((200 * ringA).toInt(), 242, 208, 107)
        strokeP.strokeWidth = dp(scene, 3f)
        c.drawRoundRect(RectF(r.left + dp(scene, 4f), r.top + dp(scene, 4f), r.right - dp(scene, 4f), r.bottom - dp(scene, 4f)), dp(scene, 10f), dp(scene, 10f), strokeP)

        textP.textSize = textDp(scene, 22f)
        textP.color = 0xFFB5651D.toInt()
        val t1 = "레벨 업!"
        c.drawText(t1, r.centerX() - textP.measureText(t1) / 2, r.top + dp(scene, 42f), textP)

        val a = g.assets
        // 레벨업 축하 — 폴짝폴짝 뛰며 만세!
        // 여기는 캐릭터를 화면 가득(약 300px) 띄우는 순간이라 [CHEER_PX]=256px 로
        // 따로 그려 둔 프레임을 쓴다 — "확대한 도트"가 아니라 진짜 그림이 된다.
        val cheer = a.cheerFrames(s.gender, s.gearTier())
        val bmp = cheer[(g.time / 0.1f).toInt() % cheer.size]
        val k = dp(scene, 3.4f) * CharacterArt.SIZE          // 32 도트 기준 크기(화면 px)
        val bx = r.centerX() - k / 2f
        val by = r.top + dp(scene, 58f)
        c.drawOval(RectF(bx + dp(scene, 6f), by + k - dp(scene, 4f), bx + k - dp(scene, 6f), by + k + dp(scene, 4f)), a.shadowPaint)
        c.drawBitmap(bmp, null, RectF(bx, by, bx + k, by + k), a.sprPaint)

        textP.textSize = textDp(scene, 18f)
        textP.color = 0xFF4A3728.toInt()
        val lv = "Lv.$fromLevel  to  Lv.$toLevel"
        c.drawText(lv, r.centerX() - textP.measureText(lv) / 2, by + k + dp(scene, 26f), textP)

        textP.textSize = textDp(scene, 13f)
        textP.color = 0xFF6FAE6F.toInt()
        val title = "「 ${Progression.title(toLevel)} 」"
        c.drawText(title, r.centerX() - textP.measureText(title) / 2, by + k + dp(scene, 46f), textP)

        textP.textSize = textDp(scene, 11.5f)
        textP.color = 0xFF3F6FB0.toInt()
        val sp = "숙련 포인트 +${s.skillPoints}  (메뉴 › 성장 에서 능력 강화!)"
        c.drawText(sp, r.centerX() - textP.measureText(sp) / 2, by + k + dp(scene, 64f), textP)

        if (Progression.gearTier(fromLevel) != Progression.gearTier(toLevel)) {
            textP.textSize = textDp(scene, 11f)
            textP.color = 0xFFB5651D.toInt()
            val gearMsg = "새 탐조 장비를 갖췄어요!"
            c.drawText(gearMsg, r.centerX() - textP.measureText(gearMsg) / 2, r.bottom - dp(scene, 14f), textP)
        }

        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()

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
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true



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

    override val usesRawTouch: Boolean get() = true

    private fun close() {
        finished = true
    }

    private fun sx(nx: Float) = offX + nx * scale
    private fun sy(ny: Float) = offY + ny * scale

    /**
     * 지역 마커 = 랜드마크 배지 지름. 각 지역마다 하나씩 놓인 랜드마크를
     * 지도 위에서 크게 읽히도록 그린다 (확대할수록 더 커진다).
     */
    private fun landmarkBadgeSize(): Float {
        val zoom = if (fitScale > 0f) (scale / fitScale).coerceAtLeast(1f) else 1f
        return dp(scene, (22f + 7f * (zoom - 1f)).coerceAtMost(44f))
    }

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
        // 랜드마크 배지 안쪽을 눌러야 고르도록 — 배지 크기에 맞춘다
        var bestD = landmarkBadgeSize() / 2f + dp(scene, 14f)
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
                Input.RawEv.CANCEL -> {
                    pts.remove(ev.id)
                    if (pts.size == 1) startPinch()
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
        if (closeR.contains(x, y)) {
            g.sfx(Audio.Sfx.TAP, 0.5f)
            close()
            return
        }
        if (zoomInR.contains(x, y)) {
            g.sfx(Audio.Sfx.TAP, 0.45f)
            zoomAt(1.4f, mapR.centerX(), mapR.centerY())
            return
        }
        if (zoomOutR.contains(x, y)) {
            g.sfx(Audio.Sfx.TAP, 0.45f)
            zoomAt(1f / 1.4f, mapR.centerX(), mapR.centerY())
            return
        }
        if (resetR.contains(x, y)) {
            g.sfx(Audio.Sfx.TAP, 0.45f)
            scale = fitScale
            centerOn(KoreaMap.southBounds.centerX(), KoreaMap.southBounds.centerY())
            selected = null
            return
        }
        if (homeR.contains(x, y)) {
            g.sfx(Audio.Sfx.TAP, 0.45f)
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

        // 상단 헤더 바
        val headerR = RectF(dp(scene, 10f), dp(scene, 6f), w - dp(scene, 10f), dp(scene, 40f))
        fillP.color = Color.argb(205, 46, 40, 58)
        c.drawRoundRect(headerR, dp(scene, 10f), dp(scene, 10f), fillP)
        strokeP.color = Color.argb(150, 233, 196, 106)
        strokeP.strokeWidth = dp(scene, 1.4f)
        c.drawRoundRect(headerR, dp(scene, 10f), dp(scene, 10f), strokeP)
        textP.textSize = textDp(scene, 14f)
        val titleTxt = "대한민국 탐조 지도"
        val titleW = textP.measureText(titleTxt)
        textP.color = 0xFFF8EFDC.toInt()
        c.drawText(titleTxt, dp(scene, 22f), dp(scene, 29f), textP)
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFFE9C46A.toInt()
        var sub = "방문 ${s.visited.size}/${Regions.ALL.size}"
        val maxSW = w - dp(scene, 22f) - titleW - dp(scene, 12f) - dp(scene, 108f)
        if (maxSW > dp(scene, 40f)) {
            while (sub.length > 4 && textP.measureText(sub) > maxSW) sub = sub.dropLast(1)
        } else {
            sub = "${s.visited.size}/${Regions.ALL.size}"
        }
        c.drawText(sub, dp(scene, 22f) + titleW + dp(scene, 12f), dp(scene, 28f), textP)

        drawButtons(c)
        drawLegend(c)
        // 현재 위치 출구 가이드는 항상 표시 (번호 = 월드 터널 번호)
        if (selected == null || selected?.id == scene.game.state.region) {
            drawExitGuide(c)
        }
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
        val currentId = s.region
        val currentExitByTarget = Regions.exitNumbered(currentId).associateBy { it.targetId }

        for ((a, b) in Regions.allLinks()) {
            val ra = Regions.byId[a] ?: continue
            val rb = Regions.byId[b] ?: continue
            val known = a in s.visited || b in s.visited
            val isCurrentLink = (a == currentId && b in currentExitByTarget) || (b == currentId && a in currentExitByTarget)

            if (isCurrentLink) {
                // 현재 위치에서 나가는 길은 더 굵고 노란빛으로 — 지도에서 추론 가능하게
                strokeP.color = Color.argb(230, 242, 182, 60)
                strokeP.strokeWidth = dp(scene, 3.2f)
                c.drawLine(sx(ra.mmX), sy(ra.mmY), sx(rb.mmX), sy(rb.mmY), strokeP)
                // 안쪽 흰 하이라이트
                strokeP.color = Color.argb(170, 255, 252, 240)
                strokeP.strokeWidth = dp(scene, 1.2f)
                c.drawLine(sx(ra.mmX), sy(ra.mmY), sx(rb.mmX), sy(rb.mmY), strokeP)
            } else {
                strokeP.color = if (known) Color.argb(190, 255, 255, 255) else Color.argb(70, 255, 255, 255)
                strokeP.strokeWidth = dp(scene, if (known) 1.8f else 1.1f)
                c.drawLine(sx(ra.mmX), sy(ra.mmY), sx(rb.mmX), sy(rb.mmY), strokeP)
            }

            // 현재 위치 기준 터널 번호 뱃지 — 길 중간보다 현재 지역에 가깝게 (30%)
            if (isCurrentLink) {
                val targetId = if (a == currentId) b else a
                val exitInfo = currentExitByTarget[targetId] ?: continue
                val raX = sx(ra.mmX)
                val raY = sy(ra.mmY)
                val rbX = sx(rb.mmX)
                val rbY = sy(rb.mmY)
                val t = if (a == currentId) 0.32f else 0.68f
                val mx = raX + (rbX - raX) * t
                val my = raY + (rbY - raY) * t

                // 번호 원
                fillP.color = Color.argb(40, 20, 14, 10)
                c.drawCircle(mx, my + dp(scene, 1.5f), dp(scene, 11f), fillP)
                fillP.color = 0xFFF2B63C.toInt()
                c.drawCircle(mx, my, dp(scene, 10f), fillP)
                strokeP.color = 0xFF4A2E12.toInt()
                strokeP.strokeWidth = dp(scene, 1.4f)
                c.drawCircle(mx, my, dp(scene, 10f), strokeP)

                textP.textSize = textDp(scene, 10f)
                textP.color = 0xFF4A2E12.toInt()
                val numTxt = exitInfo.number.toString()
                c.drawText(numTxt, mx - textP.measureText(numTxt) / 2, my - (textP.descent() + textP.ascent()) / 2, textP)

                // 방향 화살표 작게
                textP.textSize = textDp(scene, 9f)
                textP.color = 0xFFF2B63C.toInt()
                val arrow = Regions.dirArrow(exitInfo.dir)
                c.drawText(arrow, mx + dp(scene, 12f), my - (textP.descent() + textP.ascent()) / 2, textP)
            }
        }
    }

    private fun drawRegions(c: Canvas) {
        val g = scene.game
        val s = g.state
        val showAllNames = scale > fitScale * 1.5f
        // 메인 퀘스트 자동 진행 — 추천 지역 위에 금색 ★ 표시
        val mainAdv = MainQuestAdvisor.advise(s)
        // 1차: 랜드마크 배지만 — 뒤 지역이 앞 지역 라벨을 덮지 않도록 배지를 먼저 깐다
        for (reg in Regions.ALL) {
            val x = sx(reg.mmX)
            val y = sy(reg.mmY)
            val outside = x < mapR.left - dp(scene, 56f) || x > mapR.right + dp(scene, 56f) ||
                    y < mapR.top - dp(scene, 56f) || y > mapR.bottom + dp(scene, 56f)
            if (outside) continue

            val visited = reg.id in s.visited
            val isCurrent = reg.id == s.region
            val lm = Landmarks.forRegion(reg.id)
            val bd = landmarkBadgeSize()
            val r = bd / 2f

            if (isCurrent) {
                strokeP.color = Color.argb(150, 226, 87, 76)
                strokeP.strokeWidth = dp(scene, 2f)
                c.drawCircle(x, y, r + dp(scene, 4f) + dp(scene, 2f) * sin(g.time * 4f), strokeP)
            }

            if (lm != null) {
                // 각 지역마다 하나씩 세운 랜드마크 — 배지에 담아 크게 표현한다
                fillP.color = Color.argb(66, 30, 22, 12)
                c.drawCircle(x, y + dp(scene, 2f), r, fillP)
                fillP.color = if (visited) 0xFFFFF8E8.toInt() else 0xFFE7E2D6.toInt()
                c.drawCircle(x, y, r, fillP)
                // 지역 성격 링 (선택 시 진한 갈색 테두리로 강조)
                strokeP.color = when {
                    selected?.id == reg.id -> 0xFF4A3728.toInt()
                    visited -> reg.kind.color
                    else -> Color.argb(235, 158, 154, 144)
                }
                strokeP.strokeWidth = dp(scene, if (visited) 2.2f else 1.8f)
                c.drawCircle(x, y, r - strokeP.strokeWidth / 2f, strokeP)
                // 랜드마크 아이콘
                UiKit.iconCenter(c, g, lm.emoji, x, y, bd * 0.68f)
                // 미방문은 흐리게 — 방문해야 제 모습을 드러낸다
                if (!visited) {
                    fillP.color = Color.argb(105, 74, 70, 62)
                    c.drawCircle(x, y, r, fillP)
                }
            } else {
                // (랜드마크 없는 지역 — 예전 점 마커 유지)
                fillP.color = when {
                    isCurrent -> 0xFFE2574C.toInt()
                    visited -> reg.kind.color
                    else -> Color.argb(150, 120, 116, 108)
                }
                c.drawCircle(x, y, r, fillP)
                strokeP.color = if (selected?.id == reg.id) 0xFF4A3728.toInt() else Color.argb(220, 255, 255, 255)
                strokeP.strokeWidth = dp(scene, if (selected?.id == reg.id) 2.6f else 1.4f)
                c.drawCircle(x, y, r, strokeP)
            }

            // 현재 위치 — 빨간 고리는 배지 바깥에 두어 아이콘을 가리지 않는다
            if (isCurrent && lm != null) {
                strokeP.color = 0xFFE2574C.toInt()
                strokeP.strokeWidth = dp(scene, 2.4f)
                c.drawCircle(x, y, r + dp(scene, 1.2f), strokeP)
            }
        }

        // 2차: 라벨·집·별 — 배지 위에 얹어 어떤 지역이든 이름이 가리지 않게 한다
        for (reg in Regions.ALL) {
            val x = sx(reg.mmX)
            val y = sy(reg.mmY)
            val outside = x < mapR.left - dp(scene, 56f) || x > mapR.right + dp(scene, 56f) ||
                    y < mapR.top - dp(scene, 56f) || y > mapR.bottom + dp(scene, 56f)
            if (outside) continue

            val visited = reg.id in s.visited
            val isCurrent = reg.id == s.region
            val hasOwnedHome = s.ownsHome(reg.id)
            val r = landmarkBadgeSize() / 2f

            if (hasOwnedHome) {
                textP.textSize = textDp(scene, 11f)
                textP.color = 0xFF4A3728.toInt()
                UiKit.iconCenter(c, g, "house", x, y - r - dp(scene, 10f), dp(scene, 16f))
            }

            // 메인 퀘스트 자동 진행 — 추천 지역: 금색 별 + 펄스 링 (현재 위치와 겹치면 생략)
            if (mainAdv != null && !mainAdv.alreadyThere && reg.id == mainAdv.regionId && !isCurrent) {
                val pulse = (g.time * 1.4f) % 1f
                strokeP.color = Color.argb(((1f - pulse) * 200f).toInt(), 242, 182, 60)
                strokeP.strokeWidth = dp(scene, 1.6f)
                c.drawCircle(x, y, r + dp(scene, 3f) + pulse * dp(scene, 7f), strokeP)
                textP.textSize = dp(scene, 15f)
                textP.color = 0xFF8A5A12.toInt()
                // 집 아이콘이랑 겹치지 않게 별은 더 위로
                UiKit.iconCenter(c, g, "star", x, y - r - dp(scene, if (hasOwnedHome) 24f else 10f), dp(scene, 18f))
            }

            // 출구 번호 뱃지는 링크(터널) 위에만 표시 — 현재 지역 점 주변(마을 중앙)에는 그리지 않는다

            if (showAllNames || isCurrent || hasOwnedHome || reg.kind == RegionKind.TOWN) {
                val nm = if (visited) reg.name else "? ${reg.name}"
                textP.textSize = textDp(scene, if (isCurrent) 11.5f else 10.5f)
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
        closeR = RectF(w - dp(scene, 16f) - dp(scene, 82f), dp(scene, 10f), w - dp(scene, 16f), dp(scene, 10f) + dp(scene, 26f))
        zoomInR = RectF(mapR.right - dp(scene, 12f) - bs, mapR.bottom - dp(scene, 12f) - bs * 2 - dp(scene, 8f), mapR.right - dp(scene, 12f), mapR.bottom - dp(scene, 12f) - bs - dp(scene, 8f))
        zoomOutR = RectF(mapR.right - dp(scene, 12f) - bs, mapR.bottom - dp(scene, 12f) - bs, mapR.right - dp(scene, 12f), mapR.bottom - dp(scene, 12f))
        resetR = RectF(mapR.left + dp(scene, 12f), mapR.bottom - dp(scene, 12f) - bs, mapR.left + dp(scene, 12f) + dp(scene, 76f), mapR.bottom - dp(scene, 12f))
        homeR = RectF(resetR.right + dp(scene, 8f), resetR.top, resetR.right + dp(scene, 8f) + dp(scene, 76f), resetR.bottom)

        drawButton(c, scene, closeR, "close 닫기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12f)
        drawButton(c, scene, zoomInR, "plus", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 17f)
        drawButton(c, scene, zoomOutR, "minus", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 17f)
        drawButton(c, scene, resetR, "전체 보기", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 11.5f)
        drawButton(c, scene, homeR, "pin 내 위치", 0xFFF8EFDC.toInt(), 0xFF4A3728.toInt(), 11.5f)
    }

    private fun drawLegend(c: Canvas) {
        val kinds = RegionKind.values()
        val rowH = dp(scene, 14f)
        val dotR = dp(scene, 4f)
        val gap = dp(scene, 6f)
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF4A3728.toInt()
        // 점 + 간격 + 가장 긴 라벨 = 한 줄 내용 폭 (점 열을 맞춘 채 블록 전체를 가운데 정렬)
        val maxTextW = kinds.maxOf { textP.measureText(it.label) }
        val contentW = dotR * 2f + gap + maxTextW
        val contentH = rowH * kinds.size
        val lw = maxOf(dp(scene, 102f), contentW + dp(scene, 16f))
        val lh = contentH + dp(scene, 10f)
        val left = mapR.left + dp(scene, 6f)
        val top = mapR.top + dp(scene, 4f)
        val legendR = RectF(left, top, left + lw, top + lh)
        fillP.color = Color.argb(55, 20, 16, 30)
        c.drawRoundRect(RectF(legendR.left, legendR.top + dp(scene, 2f), legendR.right, legendR.bottom + dp(scene, 2f)), dp(scene, 8f), dp(scene, 8f), fillP)
        fillP.color = Color.argb(225, 255, 252, 240)
        c.drawRoundRect(legendR, dp(scene, 8f), dp(scene, 8f), fillP)
        strokeP.color = Color.argb(160, 107, 79, 53)
        strokeP.strokeWidth = dp(scene, 1.4f)
        c.drawRoundRect(legendR, dp(scene, 8f), dp(scene, 8f), strokeP)

        // 가로·세로 모두 상자 중앙에 오도록 시작점 계산
        val startX = legendR.centerX() - contentW / 2f
        val startY = legendR.centerY() - contentH / 2f
        val fm = textP.fontMetrics
        val baselineOff = -(fm.ascent + fm.descent) / 2f   // 글자 세로 중심 → 줄 중심
        for ((i, k) in kinds.withIndex()) {
            val cy = startY + rowH * (i + 0.5f)
            fillP.color = k.color
            c.drawCircle(startX + dotR, cy, dotR, fillP)
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(k.label, startX + dotR * 2f + gap, cy + baselineOff, textP)
        }
    }

    private fun drawInfo(c: Canvas, reg: RegionDef) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val mainAdv = MainQuestAdvisor.advise(s)
        val isMainPick = mainAdv != null && !mainAdv.alreadyThere && reg.id == mainAdv.regionId
        val cardW = minOf(dp(scene, 320f), mapR.width() - dp(scene, 24f))
        val numberedForSize = Regions.exitNumbered(reg.id)
        val extra = if (numberedForSize.isEmpty()) 0f else 14f + numberedForSize.size * 13f
        // 메인 퀘스트 추천 일줄이 있으면 +14dp 확보
        val cardH = dp(scene, (if (isMainPick) 134f else 120f) + extra)
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
        var y = r.top + dp(scene, 20f)
        textP.textSize = textDp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        UiKit.icon(c, g, reg.emoji, RectF(x, y - dp(scene, 13f), x + dp(scene, 16f), y + dp(scene, 3f)))
        c.drawText(reg.name, x + dp(scene, 20f), y, textP)
        val tw = textP.measureText(reg.name) + dp(scene, 20f)
        textP.textSize = textDp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(reg.english, x + tw + dp(scene, 6f), y, textP)
        val badgeTxt = when {
            reg.id == s.region -> "현재 위치"
            reg.id == s.homeRegion -> "우리 집"
            s.ownsHome(reg.id) -> "보유한 집"
            reg.id in s.visited -> "방문함"
            else -> "미방문"
        }
        textP.textSize = textDp(scene, 9.5f)
        val badgeW = textP.measureText(badgeTxt) + dp(scene, 12f)
        val badgeBg = when {
            reg.id == s.region -> 0xFFE2574C.toInt()
            reg.id == s.homeRegion || s.ownsHome(reg.id) -> 0xFF6FBA6B.toInt()
            reg.id in s.visited -> 0xFF3F6FB0.toInt()
            else -> 0xFF9AA0A8.toInt()
        }
        UiKit.badge(
            c, g, RectF(r.right - dp(scene, 12f) - badgeW, y - dp(scene, 14f), r.right - dp(scene, 12f), y + dp(scene, 4f)),
            badgeTxt, badgeBg, 0xFFFFF8E8.toInt(), 9.5f
        )

        y += dp(scene, 15f)
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF6FAE6F.toInt()
        c.drawText("${reg.kind.label} · ${reg.habitatLabels}", x, y, textP)

        y += dp(scene, 14f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("대표 새: " + Regions.signatureBirds(reg).joinToString(", ") { it.name }, x, y, textP)

        // 이 지역에 사는 사람들 — 사람은 한 장소에만 살므로 여기 적힌 사람만 여기서 만날 수 있다.
        y += dp(scene, 14f)
        textP.color = 0xFF9A6B4F.toInt()
        var castTxt = "👥 동네 사람: " + NpcRoster.forRegion(reg.id).joinToString(" · ") {
            if (it.title.isEmpty()) it.name else "${it.name}(${it.title})"
        }
        val castMaxW = r.width() - dp(scene, 24f)
        if (textP.measureText(castTxt) > castMaxW) {
            while (castTxt.length > 8 && textP.measureText("$castTxt…") > castMaxW) castTxt = castTxt.dropLast(1)
            castTxt += "…"
        }
        c.drawText(castTxt, x, y, textP)

        y += dp(scene, 14f)
        textP.color = 0xFF3F6FB0.toInt()
        c.drawText("추천 시기: ${reg.season}", x, y, textP)

        if (isMainPick) {
            y += dp(scene, 14f)
            textP.color = 0xFFB5651D.toInt()
            var rec = "메인 퀘스트 추천: ${mainAdv!!.reason}"
            val maxRecW = r.width() - dp(scene, 24f)
            while (rec.length > 6 && textP.measureText(rec) > maxRecW) rec = rec.dropLast(1)
            c.drawText(rec, x, y, textP)
        }

        y += dp(scene, 14f)
        textP.color = 0xFF8A7360.toInt()
        for (ln in g.hud.wrapText(if (reg.tip.isNotEmpty()) reg.tip else reg.desc, textP, r.width() - dp(scene, 24f)).take(2)) {
            c.drawText(ln, x, y, textP)
            y += dp(scene, 12f)
        }

        y += dp(scene, 2f)
        // 터널 번호와 방향을 함께 표시 — 지도 보고 월드 터널 번호 추론 가능하게
        val numbered = Regions.exitNumbered(reg.id)
        if (numbered.isEmpty()) {
            textP.color = 0xFF8A7360.toInt()
            val ex = "연결: - (막다른 길)"
            for (ln in g.hud.wrapText(ex, textP, r.width() - dp(scene, 24f)).take(2)) {
                c.drawText(ln, x, y, textP)
                y += dp(scene, 12f)
            }
        } else {
            // 현재 위치면 강조
            val isCurrent = reg.id == s.region
            textP.color = if (isCurrent) 0xFF4A2E12.toInt() else 0xFF8A7360.toInt()
            textP.textSize = textDp(scene, 9.5f)
            val header = if (isCurrent) "터널 번호 (지도와 월드 동일, 맵별 다름):" else "연결:"
            c.drawText(header, x, y, textP)
            y += dp(scene, 12f)

            for (exit in numbered) {
                val target = Regions.byId[exit.targetId]
                val targetName = target?.name ?: exit.targetId
                val dirArrow = Regions.dirArrow(exit.dir)
                val dirLabel = Regions.dirLabel(exit.dir)
                val line = "${exit.number}. $dirLabel · $targetName"

                // 번호 원
                fillP.color = 0xFFF2B63C.toInt()
                c.drawCircle(x + dp(scene, 6f), y - dp(scene, 3f), dp(scene, 6f), fillP)
                strokeP.color = 0xFF4A2E12.toInt()
                strokeP.strokeWidth = dp(scene, 1f)
                c.drawCircle(x + dp(scene, 6f), y - dp(scene, 3f), dp(scene, 6f), strokeP)
                textP.textSize = textDp(scene, 8f)
                textP.color = 0xFF4A2E12.toInt()
                val nt = exit.number.toString()
                c.drawText(nt, x + dp(scene, 6f) - textP.measureText(nt) / 2, y, textP)

                // 텍스트
                textP.textSize = textDp(scene, 9.5f)
                textP.color = if (isCurrent) 0xFF3A2A24.toInt() else 0xFF8A7360.toInt()
                c.drawText(line, x + dp(scene, 16f), y, textP)
                y += dp(scene, 12f)
                if (y > r.bottom - dp(scene, 6f)) break
            }
        }
    }

    private fun drawExitGuide(c: Canvas) {
        // 현재 위치 기준 출구 요약 — 지도 하단에 항상 보이게 (추론 돕기)
        val g = scene.game
        val s = g.state
        val cur = Regions.byId[s.region] ?: return
        val numbered = Regions.exitNumbered(cur.id)
        if (numbered.isEmpty()) return

        val cardW = minOf(dp(scene, 340f), mapR.width() - dp(scene, 24f))
        val cardH = dp(scene, 18f + numbered.size * 14f)
        val r = RectF(
            mapR.left + dp(scene, 12f), mapR.bottom - dp(scene, 12f) - cardH - dp(scene, 40f),
            mapR.left + dp(scene, 12f) + cardW, mapR.bottom - dp(scene, 12f) - dp(scene, 40f)
        )
        fillP.color = Color.argb(205, 46, 40, 58)
        c.drawRoundRect(r, dp(scene, 10f), dp(scene, 10f), fillP)
        strokeP.color = Color.argb(150, 233, 196, 106)
        strokeP.strokeWidth = dp(scene, 1.4f)
        c.drawRoundRect(r, dp(scene, 10f), dp(scene, 10f), strokeP)

        var y = r.top + dp(scene, 14f)
        textP.textSize = textDp(scene, 10.5f)
        textP.color = 0xFFF8EFDC.toInt()
        c.drawText("${cur.name} 현재 위치 출구 (번호 = 월드 터널 번호)", r.left + dp(scene, 10f), y, textP)
        y += dp(scene, 12f)
        for (exit in numbered) {
            val target = Regions.byId[exit.targetId] ?: continue
            val line = "${exit.number}. ${Regions.dirLabel(exit.dir)} · ${target.name}"
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFFE9C46A.toInt()
            c.drawText(line, r.left + dp(scene, 10f), y, textP)
            y += dp(scene, 12f)
        }
    }
}

// ---------------------------------------------------------------------------
// 카메라 상점 (도시 카메라샵 진열대) — 컴팩트 / 바디 / 렌즈 / 액세서리
// ---------------------------------------------------------------------------

/** 장비 카드 한 줄을 그리는 공용 코드 (상점·장비 가방에서 함께 쓴다) */
private fun drawGearCard(
    c: Canvas, scene: Scene, r: RectF, gear: CamGear,
    look: CamLook?, spec: String, sub: String, highlight: Boolean
) {
    drawCard(c, scene, r, highlight, if (highlight) 0xFFB5651D.toInt() else 0xFFC9A87B.toInt(), if (highlight) 2.2f else 1.5f)

    // 장비 그림 (렌즈 길이가 보이는 측면 아이콘)
    val iconW = dp(scene, 52f)
    val iconH = iconW * 20f / 32f
    if (look != null) {
        val bmp = scene.game.assets.camProfile(look)
        c.drawBitmap(
            bmp, null,
            RectF(r.left + dp(scene, 6f), r.centerY() - iconH / 2f, r.left + dp(scene, 6f) + iconW, r.centerY() + iconH / 2f),
            scene.game.assets.sprPaint
        )
    } else {
        textP.textSize = textDp(scene, 22f)
        textP.color = 0xFF6B4F35.toInt()
        UiKit.drawIconText(c, scene.game, gear.kind.emoji, r.left + dp(scene, 18f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
    }

    val tx = r.left + dp(scene, 62f)
    // 이름 + 등급 칩
    textP.textSize = textDp(scene, 12.5f)
    textP.color = 0xFF4A3728.toInt()
    c.drawText(gear.fullName, tx, r.top + dp(scene, 16f), textP)
    val gLabel = gear.grade.label
    textP.textSize = textDp(scene, 8.5f)
    val gw = textP.measureText(gLabel) + dp(scene, 10f)
    val gr = RectF(r.right - gw - dp(scene, 96f), r.top + dp(scene, 5f), r.right - dp(scene, 96f), r.top + dp(scene, 19f))
    fillP.color = gear.grade.color
    c.drawRoundRect(gr, dp(scene, 7f), dp(scene, 7f), fillP)
    textP.color = 0xFFFFF8E8.toInt()
    c.drawText(gLabel, gr.centerX() - textP.measureText(gLabel) / 2, gr.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

    textP.textSize = textDp(scene, 9.5f)
    textP.color = 0xFF3F6FB0.toInt()
    c.drawText(spec, tx, r.top + dp(scene, 29f), textP)
    textP.color = 0xFF8A7360.toInt()
    c.drawText(sub, tx, r.top + dp(scene, 41f), textP)
}

/** 장비 목록에 쓰는 한 줄 요약 */
private fun gearSpecLine(gear: CamGear): String = when (gear) {
    is CompactCam -> gear.specLine
    is CamBody -> gear.specLine
    is CamLens -> gear.specLine
    is TeleConv -> gear.specLine
    is CamAccessory -> gear.effect
}

/** 장비 목록에 쓰는 보조 설명 */
private fun gearSubLine(gear: CamGear): String = when (gear) {

    is CompactCam ->
        "줌 ${gear.zoomX.fmt1()}배 · 반경 ${CameraRigs.reachFor(gear.teleMm).fmt1()}칸 · ${gear.burst.fmt1()}fps · ${gear.weightG}g" +
            (if (gear.weatherProof) " · 방진방적" else "")
    is CamBody ->
        "AF ${gear.afScore.fmt1()} · IBIS ${gear.ibis.fmt1()} · 크롭 ${gear.sensor.crop.fmt1()}× · ${gear.weightG}g" +
            (if (gear.birdAf) " · 조류 AF" else "")
    is CamLens ->
        gear.mounts.joinToString("/") + " · OS ${gear.os.fmt1()} · 해상력 ${gear.sharp.fmt1()}" +
            (if (gear.tcOk) " · TC 가능" else "")
    is TeleConv -> gear.desc
    is CamAccessory -> gear.desc
}

/**
 * 가게 주인과 상태 창이 함께 여는 코너 목록 — 진열대와 장비 가방은 어디나 열리지만,
 * 자전거·장식·행운 장신구는 **서울 본점 전용** 코너다 (`CityShop.flagship`).
 */
fun shopChoices(scene: Scene, shop: CityShop? = null): List<DialogOverlay.Choice> {
    val out = ArrayList<DialogOverlay.Choice>(6)
    out.add(DialogOverlay.Choice("카메라 진열대") { scene.openOverlay(CameraShopOverlay(scene)) })
    out.add(DialogOverlay.Choice("장비 가방(조립)") { scene.openOverlay(GearBagOverlay(scene)) })
    if (shop == null || shop.flagship) {
        out.add(DialogOverlay.Choice("자전거 상점") { scene.openOverlay(BikeShopOverlay(scene)) })
        out.add(DialogOverlay.Choice("장식 코너") { scene.openOverlay(DecorShopOverlay(scene)) })
        out.add(DialogOverlay.Choice("행운 장신구") { scene.openOverlay(CharmOverlay(scene, shop = true)) })
    } else {
        val near = CameraShops.flagship
        val nearName = Regions.byId[near.regionId]?.name ?: "서울"
        out.add(
            DialogOverlay.Choice("자전거·장식은 $nearName 본점") {
                scene.game.toast("🚲 " + nearName + " " + near.spot.label + " — 본점에 있는 코너야")
            }
        )
    }
    out.add(DialogOverlay.Choice("그냥 볼게요"))
    return out
}

class CameraShopOverlay(scene: Scene, startTab: Int = 0, startPage: Int = 0) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true


    private enum class Tab(val label: String) {
        COMPACT("컴팩트"), BODY("바디"), LENS("렌즈"), ACC("액세서리")
    }

    private var tab = Tab.values()[startTab.coerceIn(0, Tab.values().size - 1)]
    private var page = startPage.coerceAtLeast(0)
    private val tabRects = ArrayList<Pair<RectF, Tab>>()
    private val buyRects = ArrayList<Pair<RectF, CamGear>>()
    private val infoRects = ArrayList<Pair<RectF, CamGear>>()
    private var prevRect = RectF()
    private var nextRect = RectF()
    private var closeRect = RectF()
    private var panelR = RectF()

    private val perPage = 4

    /** 이 가게 (도시가 아니면 본점 안내만 보여주는 빈 진열대) */
    private val shop: CityShop? get() = CameraShops.shop(scene.game.state.region)

    /** 진열대는 그 도시가 고른 것만 — 서울 본점은 전 라인업 (`CameraShops`) */
    private fun items(): List<CamGear> =
        CameraShops.tabItems(scene.game.state.region, tabKind(tab))

    private fun tabKind(t: Tab): GearKind = when (t) {
        Tab.COMPACT -> GearKind.COMPACT
        Tab.BODY -> GearKind.BODY
        Tab.LENS -> GearKind.LENS
        Tab.ACC -> GearKind.ACCESSORY
    }

    private fun price(gear: CamGear): Int = CameraShops.priceOf(scene.game.state.region, gear)

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, t) in tabRects) {
            if (r.contains(tap.x, tap.y)) { tab = t; page = 0; return }
        }
        if (prevRect.contains(tap.x, tap.y)) { if (page > 0) page--; return }
        if (nextRect.contains(tap.x, tap.y)) {
            if ((page + 1) * perPage < items().size) page++
            return
        }
        for ((r, g) in buyRects) {
            if (r.contains(tap.x, tap.y)) { buy(g); return }
        }
        for ((r, g) in infoRects) {
            if (r.contains(tap.x, tap.y)) { showInfo(g); return }
        }
    }

    private fun showInfo(gear: CamGear) {
        val s = scene.game.state
        val backTab = tab.ordinal
        val backPage = page
        val body = StringBuilder()
        body.append(gear.desc).append("\n")
        body.append(gearSpecLine(gear)).append("\n")
        body.append(gearSubLine(gear)).append("\n")
        when (gear) {
            is CompactCam -> {
                body.append("최단 촬영 ${CameraRigs.minDistFor(gear.wideMm, false).fmt1()}칸 · 손떨림 보정 ${gear.stab.fmt1()}\n")
                if (gear.luck > 0) body.append("찍는 재미 보너스: 행운 +${gear.luck}\n")
            }
            is CamBody -> {
                body.append("${Mounts.label(gear.mount)} · ${if (gear.weatherProof) "방진방적" else "실내·맑은 날 권장"}\n")
                body.append("같은 렌즈를 써도 환산 초점거리가 ${gear.sensor.crop.fmt1()}배가 돼요\n")
                if (gear.ibis <= 0f) {
                    body.append("바디 손떨림 보정이 없어요 — 손떨방(OS) 붙은 렌즈가 유리해요\n")
                }
                body.append("💡 같은 예산이면 바디 급을 조금 낮추고 렌즈에 투자하는 편이 결과물이 좋아요\n")
            }
            is CamLens -> {
                val cur = CameraGear.body(s.bodyId)
                if (cur != null) {
                    val problem = CameraGear.mountProblem(cur, gear, s.hasAdapter())
                    if (problem == null) {
                        val eq = (gear.teleMm * cur.sensor.crop).toInt()
                        body.append("지금 바디(${cur.name})에 물리면 환산 ${eq}mm\n")
                    } else {
                        body.append("주의: $problem\n")
                    }
                }
                body.append(if (gear.macro) "접사 렌즈 — 아주 가까이서 찍을 수 있어요\n" else "")
                body.append("💡 렌즈는 바디를 바꿔도 계속 쓸 수 있어요. 망원·조리개에 먼저 투자해 보세요\n")
            }
            is TeleConv -> body.append("망원 단렌즈·고급 줌에만 물릴 수 있어요\n")
            is CamAccessory -> body.append("효과: ${gear.effect}\n")
        }
        val price = price(gear)
        val sale = CameraShops.onSale(s.region, gear)
        body.append(
            "가격 ${won(price)}" + (if (sale) " (여기 특화 할인 · 정가 ${won(gear.price)})" else "") +
                " · 보유 ${won(s.money)}"
        )
        val hereShop = shop          // 커스텀 getter → 스마트 캐스트가 안 된다 (지역 변수로 잡는다)
        if (hereShop != null && !hereShop.carries(gear.id)) {
            body.append("\n이 동네 진열대엔 없어요 · 판매 도시: ${CameraShops.soldInLabels(gear.id)}")
        }
        scene.openOverlay(
            DialogOverlay(
                scene, gear.fullName, body.toString(),
                listOf(
                    DialogOverlay.Choice(if (gear.id in s.ownedGear) "보유중" else "구매 ${won(price(gear))}") {
                        buy(gear)
                        it.scene.openOverlay(CameraShopOverlay(it.scene, backTab, backPage))
                    },
                    DialogOverlay.Choice("닫기") {
                        it.scene.openOverlay(CameraShopOverlay(it.scene, backTab, backPage))
                    }
                )
            )
        )
    }

    private fun buy(gear: CamGear) {
        val g = scene.game
        val s = g.state
        if (gear.id in s.ownedGear) {
            g.toast("이미 갖고 있는 장비예요!")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        // 도시에 없는 가게에서는 살 수 없다 — 진열대 밖이면 어디에 있는지부터 알려 준다
        if (!CameraShops.has(s.region) || shop?.carries(gear.id) == false) {
            g.toast("🏬 ${CameraShops.notSoldHereLine(s.region)}")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        val price = price(gear)
        if (s.money < price) {
            g.toast("돈이 부족해요… (${won(price)})")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        g.sfx(Audio.Sfx.BUY)
        s.money -= price
        s.ownedGear.add(gear.id)
        // 산 장비는 가능하면 바로 장착해 준다
        when (gear) {
            is CompactCam -> {
                s.compactId = gear.id
                s.useIlc = false
                g.toast("${gear.name} 구매! 바로 목에 걸었어요")
            }
            is CamBody -> {
                s.bodyId = gear.id
                val lens = CameraGear.lens(s.lensId)
                s.useIlc = lens != null && CameraGear.canMount(gear, lens, s.hasAdapter())
                g.toast(
                    if (s.useIlc) "${gear.name} 구매! 렌즈를 물려 장착했어요"
                    else "${gear.name} 구매! 이제 마운트가 맞는 렌즈가 필요해요"
                )
            }
            is CamLens -> {
                val body = CameraGear.body(s.bodyId)
                if (body != null && CameraGear.canMount(body, gear, s.hasAdapter())) {
                    s.lensId = gear.id
                    if (!gear.tcOk) s.tcId = null
                    s.useIlc = true
                    g.toast("${gear.name} 구매! ${body.name}에 물렸어요")
                } else {
                    s.lensId = s.lensId ?: gear.id
                    g.toast("${gear.name} 구매! 맞는 바디에 물려 보세요")
                }
            }
            is TeleConv -> {
                val lens = CameraGear.lens(s.lensId)
                if (lens != null && lens.tcOk) {
                    s.tcId = gear.id
                    g.toast("${gear.name} 장착! 초점거리가 ×${gear.mul.fmt1()} 늘어났어요")
                } else {
                    g.toast("${gear.name} 구매! TC를 지원하는 렌즈에 물려 보세요")
                }
            }
            is CamAccessory -> g.toast("${gear.name} 구매! 효과가 바로 적용돼요")
        }
        s.invalidateRig()
        SaveManager.save(g.context, s)
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val pw = minOf(w * 0.94f, dp(scene, 520f))
        val ph = minOf(h * 0.92f, dp(scene, 430f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 6f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 32f))
        textP.textSize = textDp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("close", closeRect.centerX() - textP.measureText("close") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        val here = shop
        val cityName = Regions.byId[s.region]?.name ?: s.region
        textP.textSize = textDp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText(
            if (here != null) "${here.shopName} ($cityName)" else "카메라샵 없는 동네 · 진열대 미리보기",
            panelR.left + dp(scene, 14f), panelR.top + dp(scene, 24f), textP
        )
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val headerSub = if (here == null) {
            "보유 ${won(s.money)} · ${CameraShops.CITY_RULE_LINE}"
        } else {
            "사장 ${here.keeper} · 특화 ${here.specialty} · 보유 ${won(s.money)}" +
                (if (here.saleLabel.isNotEmpty()) " · ${here.saleLabel}" else "")
        }
        val hsw = panelR.width() - dp(scene, 60f)
        var sub = headerSub
        textP.textSize = textDp(scene, 10f)
        if (textP.measureText(sub) > hsw) {
            while (sub.length > 4 && textP.measureText("$sub…") > hsw) sub = sub.dropLast(1)
            sub = "$sub…"
        }
        c.drawText(sub, panelR.left + dp(scene, 14f), panelR.top + dp(scene, 38f), textP)


        // 탭
        tabRects.clear()
        val nTabs = Tab.values().size
        val gap = dp(scene, 5f)
        val tabW = (panelR.width() - dp(scene, 24f) - gap * (nTabs - 1)) / nTabs
        for ((i, t) in Tab.values().withIndex()) {
            val r = RectF(
                panelR.left + dp(scene, 12f) + i * (tabW + gap), panelR.top + dp(scene, 44f),
                panelR.left + dp(scene, 12f) + i * (tabW + gap) + tabW, panelR.top + dp(scene, 68f)
            )
            fillP.color = if (t == tab) 0xFF6B4F35.toInt() else 0xFFF2E3C2.toInt()
            c.drawRoundRect(r, dp(scene, 7f), dp(scene, 7f), fillP)
            textP.textSize = textDp(scene, 11.5f)
            textP.color = if (t == tab) 0xFFF8EFDC.toInt() else 0xFF6B4F35.toInt()
            c.drawText(t.label, r.centerX() - textP.measureText(t.label) / 2, r.centerY() - (textP.descent() + textP.ascent()) / 2, textP)
            tabRects.add(r to t)
        }

        // 목록
        buyRects.clear()
        infoRects.clear()
        val all = items()
        val maxPage = ((all.size - 1) / perPage).coerceAtLeast(0)
        if (page > maxPage) page = maxPage
        val from = page * perPage
        val shown = all.subList(from, minOf(from + perPage, all.size))

        var ty = panelR.top + dp(scene, 74f)
        val rowH = dp(scene, 50f)
        for (gear in shown) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH - dp(scene, 5f))
            val look = when (gear) {
                is CompactCam -> gear.look
                is CamBody -> gear.look
                is CamLens -> CamLook(
                    2, 0xFF3A3A44.toInt(), 0xFF23232B.toInt(), 0xFF6FA8DC.toInt(),
                    gear.barrelLen, gear.barrelDia, gear.barrelCol, gear.hood, false, false
                )
                else -> null
            }
            val owned = gear.id in s.ownedGear
            drawGearCard(c, scene, r, gear, look, gearSpecLine(gear), gearSubLine(gear), owned)

            val br = RectF(r.right - dp(scene, 96f), r.centerY() - dp(scene, 14f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 14f))
            val price = price(gear)
            val onSale = CameraShops.onSale(s.region, gear)
            if (owned) {
                drawButton(c, scene, br, "보유중", Color.argb(90, 200, 190, 175), Color.argb(150, 74, 55, 40), 11f)
            } else if (s.money >= price) {
                drawButton(c, scene, br, (if (onSale) "할인 " else "") + won(price), 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
                buyRects.add(br to gear)
            } else {
                drawButton(c, scene, br, won(price), Color.argb(110, 200, 190, 175), Color.argb(170, 74, 55, 40), 10.5f)
            }
            infoRects.add(RectF(r.left, r.top, r.right - dp(scene, 100f), r.bottom) to gear)
            ty += rowH
        }
        if (shown.isEmpty()) {
            // 이 탭엔 진열된 게 없다 — 어디 파는지까지 알려 준다 (비어 있는 400dp 상자가 아니다)
            val cy = panelR.centerY() + dp(scene, 10f)
            textP.textSize = textDp(scene, 11f)
            textP.color = 0xFF6B5A48.toInt()
            val emptyMsg = "이 동네 진열대엔 ${tab.label}이(가) 없어요"
            c.drawText(emptyMsg, panelR.centerX() - textP.measureText(emptyMsg) / 2f, cy, textP)
            textP.textSize = textDp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            val hint = CameraShops.notSoldHereLine(s.region).ifEmpty { "다른 도시 카메라샵이나 서울 본점을 찾아 보게" }
            c.drawText(hint, panelR.centerX() - textP.measureText(hint) / 2f, cy + dp(scene, 15f), textP)
        }

        // 페이지 버튼
        val by = panelR.bottom - dp(scene, 32f)
        prevRect = RectF(panelR.left + dp(scene, 14f), by, panelR.left + dp(scene, 74f), by + dp(scene, 24f))
        nextRect = RectF(panelR.right - dp(scene, 74f), by, panelR.right - dp(scene, 14f), by + dp(scene, 24f))
        drawButton(c, scene, prevRect, "arrow_left 이전", if (page > 0) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
        drawButton(c, scene, nextRect, "arrow_right 다음", if (page < maxPage) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val pg = "${page + 1} / ${maxPage + 1}  ·  항목을 누르면 자세한 성능"
        c.drawText(pg, panelR.centerX() - textP.measureText(pg) / 2, by + dp(scene, 16f), textP)
    }
}

// ---------------------------------------------------------------------------
// 장비 가방 — 컴팩트/바디/렌즈/TC 조립 & 성능 확인
// ---------------------------------------------------------------------------

class GearBagOverlay(scene: Scene) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true


    init {
        scene.game.sfx(Audio.Sfx.BAG_OPEN, 0.7f)   // 🎒 장비 가방 열기
    }

    private val btnRects = ArrayList<Triple<RectF, String, () -> Unit>>()
    private var closeRect = RectF()
    private var panelR = RectF()

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { finished = true; return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { finished = true; return }
        for ((r, _, action) in btnRects) {
            if (r.contains(tap.x, tap.y)) { action(); return }
        }
    }

    private fun statBar(c: Canvas, x: Float, y: Float, w: Float, label: String, v: Float, max: Float, col: Int) {
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF6B5A48.toInt()
        c.drawText(label, x, y + dp(scene, 8f), textP)
        val bx = x + dp(scene, 46f)
        val bw = w - dp(scene, 46f)
        val bh = dp(scene, 8f)
        fillP.color = 0xFFD6C5A4.toInt()
        c.drawRoundRect(RectF(bx, y + dp(scene, 1f), bx + bw, y + dp(scene, 1f) + bh), bh / 2, bh / 2, fillP)
        val k = (v / max).coerceIn(0f, 1f)
        if (k > 0.02f) {
            fillP.color = col
            c.drawRoundRect(RectF(bx, y + dp(scene, 1f), bx + bw * k, y + dp(scene, 1f) + bh), bh / 2, bh / 2, fillP)
        }
        textP.textSize = textDp(scene, 8.5f)
        textP.color = 0xFF8A7360.toInt()
        val t = v.fmt1()
        c.drawText(t, bx + bw + dp(scene, 4f), y + dp(scene, 8f), textP)
    }

    private fun pick(kind: GearKind) {
        scene.openOverlay(GearPickOverlay(scene, kind))
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 150)

        val pw = minOf(w * 0.94f, dp(scene, 520f))
        // 내용물(제목·현재 조합 카드·모드 버튼·슬롯 4개·액세서리 줄)의 실제
        // 높이에 맞춘다 — 예전 고정 420dp 아래쪽엔 항상 100dp쯤 빈 자리가 남았다.
        val accs = CameraGear.ACCESSORIES.filter { it.id in s.ownedGear }
        val accTxt = if (accs.isEmpty()) "보유 액세서리: 없음 (상점 액세서리 탭)"
            else "보유 액세서리: " + accs.joinToString(", ") { it.name }
        textP.textSize = textDp(scene, 10f)
        val accLines = g.hud.wrapText(accTxt, textP, pw - dp(scene, 28f)).take(2)
        val ph = minOf(
            h * 0.92f,
            dp(scene, 306f) + dp(scene, 12f) * (accLines.size - 1).coerceAtLeast(0)
        )
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)
        btnRects.clear()

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 6f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 32f))
        textP.textSize = textDp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("close", closeRect.centerX() - textP.measureText("close") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        val rig = s.rig()
        textP.textSize = textDp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("backpack 장비 가방", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 24f), textP)

        // 현재 조합 카드
        val cardR = RectF(panelR.left + dp(scene, 12f), panelR.top + dp(scene, 32f), panelR.right - dp(scene, 12f), panelR.top + dp(scene, 116f))
        drawCard(c, scene, cardR, true, 0xFFB5651D.toInt(), 2f)

        val iconW = dp(scene, 92f)
        val iconH = iconW * 20f / 32f
        c.drawBitmap(
            g.assets.camProfile(rig.look), null,
            RectF(cardR.left + dp(scene, 8f), cardR.top + dp(scene, 8f), cardR.left + dp(scene, 8f) + iconW, cardR.top + dp(scene, 8f) + iconH),
            g.assets.sprPaint
        )
        textP.textSize = textDp(scene, 12.5f)
        textP.color = 0xFF4A3728.toInt()
        val tx = cardR.left + dp(scene, 108f)
        c.drawText(rig.title, tx, cardR.top + dp(scene, 18f), textP)
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF3F6FB0.toInt()
        c.drawText(rig.specLine(), tx, cardR.top + dp(scene, 32f), textP)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(
            "${rig.reachLabel()} · ${rig.weightG}g" + (if (rig.luck > 0) " · 행운 +${rig.luck}" else ""),
            tx, cardR.top + dp(scene, 44f), textP
        )
        if (rig.tags.isNotEmpty()) {
            textP.color = 0xFF5E934F.toInt()
            c.drawText("# " + rig.tags.joinToString(" # "), tx, cardR.top + dp(scene, 56f), textP)
        }

        // 성능 바
        val colW = (cardR.width() - dp(scene, 24f)) / 3f
        val sy = cardR.top + dp(scene, 62f)
        statBar(c, cardR.left + dp(scene, 10f), sy, colW, "화질", rig.iq, 10f, 0xFF6FBA6B.toInt())
        statBar(c, cardR.left + dp(scene, 10f) + colW, sy, colW, "저조도", rig.lowLight, 10f, 0xFF3F6FB0.toInt())
        statBar(c, cardR.left + dp(scene, 10f) + colW * 2, sy, colW, "AF", rig.af, 10f, 0xFFE2574C.toInt())
        val sy2 = sy + dp(scene, 13f)
        statBar(c, cardR.left + dp(scene, 10f), sy2, colW, "흔들림", rig.steady, 10f, 0xFFB05AC0.toInt())
        statBar(c, cardR.left + dp(scene, 10f) + colW, sy2, colW, "사거리", rig.reach, 13f, 0xFFF2B63C.toInt())
        statBar(c, cardR.left + dp(scene, 10f) + colW * 2, sy2, colW, "연사", rig.burst.coerceAtMost(30f), 30f, 0xFF5AA0B0.toInt())

        // 모드 전환
        var y = cardR.bottom + dp(scene, 8f)
        val halfW = (panelR.width() - dp(scene, 32f)) / 2f
        val modeA = RectF(panelR.left + dp(scene, 12f), y, panelR.left + dp(scene, 12f) + halfW, y + dp(scene, 26f))
        val modeB = RectF(modeA.right + dp(scene, 8f), y, modeA.right + dp(scene, 8f) + halfW, y + dp(scene, 26f))
        drawButton(
            c, scene, modeA, "일체형 컴팩트",
            if (!s.useIlc) 0xFF6FBA6B.toInt() else 0xFFF2E3C2.toInt(),
            if (!s.useIlc) 0xFFFFF8E8.toInt() else 0xFF6B4F35.toInt(), 11f
        )
        btnRects.add(Triple(modeA, "compact") {
            s.useIlc = false
            s.invalidateRig()
            SaveManager.save(g.context, s)
            g.sfx(Audio.Sfx.TAP)
            g.toast("일체형 컴팩트를 꺼냈어요")
        })
        val canIlc = s.ilcReady()
        drawButton(
            c, scene, modeB, "렌즈교환식",
            if (s.useIlc) 0xFF6FBA6B.toInt() else if (canIlc) 0xFFF2E3C2.toInt() else Color.argb(80, 200, 190, 175),
            if (s.useIlc) 0xFFFFF8E8.toInt() else 0xFF6B4F35.toInt(), 11f
        )
        btnRects.add(Triple(modeB, "ilc") {
            if (s.ilcReady()) {
                s.useIlc = true
                s.invalidateRig()
                SaveManager.save(g.context, s)
                g.sfx(Audio.Sfx.TAP)
                g.toast("바디 + 렌즈를 조립했어요")
            } else {
                g.sfx(Audio.Sfx.FAIL, 0.5f)
                g.toast("마운트가 맞는 바디와 렌즈가 모두 필요해요")
            }
        })

        // 슬롯 4개
        y += dp(scene, 32f)
        val slotH = dp(scene, 30f)
        fun slot(label: String, value: String, kind: GearKind, enabled: Boolean) {
            val r = RectF(panelR.left + dp(scene, 12f), y, panelR.right - dp(scene, 12f), y + slotH - dp(scene, 4f))
            drawCard(c, scene, r, enabled, if (enabled) 0xFFB5651D.toInt() else 0xFFC9A87B.toInt(), 1.3f)
            textP.textSize = textDp(scene, 10.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(label, r.left + dp(scene, 10f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            textP.textSize = textDp(scene, 11.5f)
            textP.color = if (enabled) 0xFF4A3728.toInt() else 0xFF9A8B7A.toInt()
            c.drawText(value, r.left + dp(scene, 74f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            val br = RectF(r.right - dp(scene, 66f), r.centerY() - dp(scene, 11f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 11f))
            drawButton(c, scene, br, "바꾸기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 10f)
            btnRects.add(Triple(br, label) { pick(kind) })
            y += slotH
        }

        slot("컴팩트", CameraGear.compact(s.compactId)?.name ?: "없음", GearKind.COMPACT, !s.useIlc)
        slot("바디", CameraGear.body(s.bodyId)?.name ?: "없음", GearKind.BODY, s.useIlc)
        val lensName = CameraGear.lens(s.lensId)?.let { l ->
            val b = CameraGear.body(s.bodyId)
            val bad = b != null && !CameraGear.canMount(b, l, s.hasAdapter())
            l.name + if (bad) "  마운트 불일치" else ""
        } ?: "없음"
        slot("렌즈", lensName, GearKind.LENS, s.useIlc)
        slot("텔레컨버터", CameraGear.tc(s.tcId)?.name ?: "없음", GearKind.TELECONV, s.useIlc)

        // 액세서리 — 패널 높이 계산에 쓴 것과 같은 줄을 그대로 쓴다
        y += dp(scene, 2f)
        textP.textSize = textDp(scene, 10f)
        textP.color = 0xFF6B5A48.toInt()
        for (ln in accLines) {
            c.drawText(ln, panelR.left + dp(scene, 14f), y + dp(scene, 10f), textP)
            y += dp(scene, 12f)
        }
    }
}

// ---------------------------------------------------------------------------
// 장비 선택 (가방에서 슬롯 교체)
// ---------------------------------------------------------------------------

class GearPickOverlay(scene: Scene, private val kind: GearKind) : Overlay(scene) {
    /** 화면 대부분을 덮는 동안 뒤 월드 비트맵을 재사용한다. */
    override val coversWorld: Boolean get() = true


    private val pickRects = ArrayList<Pair<RectF, String>>()
    private var closeRect = RectF()
    private var panelR = RectF()
    private var page = 0
    private var prevRect = RectF()
    private var nextRect = RectF()
    private val perPage = 4

    private fun owned(): List<CamGear> {
        val s = scene.game.state
        return CameraGear.ALL.filter { it.kind == kind && it.id in s.ownedGear }.sortedBy { it.price }
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (input.justB || input.justBack) { back(); return }
        if (tap == null) return
        if (closeRect.contains(tap.x, tap.y)) { back(); return }
        if (prevRect.contains(tap.x, tap.y)) { if (page > 0) page--; return }
        if (nextRect.contains(tap.x, tap.y)) {
            if ((page + 1) * perPage < owned().size + 1) page++
            return
        }
        for ((r, id) in pickRects) {
            if (r.contains(tap.x, tap.y)) { equip(id); return }
        }
    }

    private fun back() {
        scene.openOverlay(GearBagOverlay(scene))
    }

    private fun equip(id: String) {
        val g = scene.game
        val s = g.state
        when (kind) {
            GearKind.COMPACT -> {
                s.compactId = id
                s.useIlc = false
                g.toast("${CameraGear.compact(id)?.name}을(를) 꺼냈어요")
            }
            GearKind.BODY -> {
                s.bodyId = id
                val body = CameraGear.body(id)
                val lens = CameraGear.lens(s.lensId)
                if (body != null && lens != null && !CameraGear.canMount(body, lens, s.hasAdapter())) {
                    g.toast("바디를 바꿨어요. 이 렌즈와는 마운트가 달라요")
                } else {
                    s.useIlc = s.ilcReady()
                    g.toast("${body?.name}을(를) 장착했어요")
                }
            }
            GearKind.LENS -> {
                val body = CameraGear.body(s.bodyId)
                val lens = CameraGear.lens(id)
                if (body != null && lens != null && !CameraGear.canMount(body, lens, s.hasAdapter())) {
                    g.toast(CameraGear.mountProblem(body, lens, s.hasAdapter()) ?: "결합할 수 없어요")
                } else {
                    s.lensId = id
                    if (lens != null && !lens.tcOk) s.tcId = null
                    s.useIlc = s.ilcReady()
                    g.toast("${lens?.name}을(를) 물렸어요")
                }
            }
            GearKind.TELECONV -> {
                val lens = CameraGear.lens(s.lensId)
                if (id.isEmpty()) {
                    s.tcId = null
                    g.toast("텔레컨버터를 뺐어요")
                } else if (lens == null || !lens.tcOk) {
                    g.toast("지금 렌즈는 텔레컨버터를 지원하지 않아요")
                } else {
                    s.tcId = id
                    g.toast("텔레컨버터 장착! 초점거리가 늘어났어요")
                }
            }
            else -> {}
        }
        if (kind == GearKind.COMPACT && id.isEmpty()) s.compactId = CameraGear.STARTER
        s.invalidateRig()
        SaveManager.save(g.context, s)
        back()
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val s = g.state
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        dim(c, scene, 160)

        pickRects.clear()
        val list = ArrayList<CamGear?>()
        list.addAll(owned())
        if (kind == GearKind.TELECONV) list.add(null)   // "빼기" 항목

        val maxPage = ((list.size - 1) / perPage).coerceAtLeast(0)
        if (page > maxPage) page = maxPage
        val from = page * perPage
        val shown = list.subList(from, minOf(from + perPage, list.size))

        // 패널 높이를 목록 길이에 맞춘다 — 항목 1개(또는 0개)에 400dp짜리
        // 빈 상자가 뜨는 일이 없게. 이 페이지에 실제로 보이는 행 수만큼만
        // 높이를 쓴다(마지막 페이지가 짧으면 상자도 같이 짧아진다).
        val listTop = dp(scene, 46f)
        val rowH = dp(scene, 50f)
        val hasPages = maxPage > 0
        val rowsNeeded = when {
            list.isEmpty() -> 2                                  // 안내 문구 2줄 몫
            else -> shown.size.coerceAtLeast(1)
        }
        val footerH = if (hasPages) dp(scene, 44f) else dp(scene, 12f)
        // 마지막 행은 행 간격 5dp를 빼고 끝난다(행 rect가 rowH-5dp라서)
        val ph = (listTop + rowsNeeded * rowH - dp(scene, 5f) + footerH)
            .coerceIn(dp(scene, 104f), minOf(h * 0.9f, dp(scene, 400f)))
        val pw = minOf(w * 0.92f, dp(scene, 480f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 6f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 32f))
        textP.textSize = textDp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("close", closeRect.centerX() - textP.measureText("close") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = textDp(scene, 13.5f)
        textP.color = 0xFF4A3728.toInt()
        UiKit.drawIconText(c, scene.game, "${kind.emoji} ${kind.label} 선택", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 24f), textP)
        textP.textSize = textDp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(
            "가진 장비만 보여요 · ${CameraShops.buyHint(s.region)}에서 더 살 수 있어요",
            panelR.left + dp(scene, 14f), panelR.top + dp(scene, 38f), textP
        )

        var ty = panelR.top + listTop
        for (gear in shown) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH - dp(scene, 5f))
            if (gear == null) {
                drawCard(c, scene, r)
                textP.textSize = textDp(scene, 12f)
                textP.color = 0xFF4A3728.toInt()
                c.drawText("텔레컨버터 빼기", r.left + dp(scene, 14f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
                pickRects.add(r to "")
            } else {
                val look = when (gear) {
                    is CompactCam -> gear.look
                    is CamBody -> gear.look
                    is CamLens -> CamLook(
                        2, 0xFF3A3A44.toInt(), 0xFF23232B.toInt(), 0xFF6FA8DC.toInt(),
                        gear.barrelLen, gear.barrelDia, gear.barrelCol, gear.hood, false, false
                    )
                    else -> null
                }
                val equipped = when (kind) {
                    GearKind.COMPACT -> gear.id == s.compactId
                    GearKind.BODY -> gear.id == s.bodyId
                    GearKind.LENS -> gear.id == s.lensId
                    GearKind.TELECONV -> gear.id == s.tcId
                    else -> false
                }
                drawGearCard(c, scene, r, gear, look, gearSpecLine(gear), gearSubLine(gear), equipped)
                if (equipped) {
                    textP.textSize = textDp(scene, 9f)
                    textP.color = 0xFF6FBA6B.toInt()
                    c.drawText("장착중", r.right - dp(scene, 44f), r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
                }
                pickRects.add(r to gear.id)
            }
            ty += rowH
        }

        if (list.isEmpty()) {
            // 빈 목록 — 큰 아이콘과 두 줄 안내를 콘텐츠 영역 가운데에
            // 바짝 모아 놓는다(예전엔 400dp 상자 한가운데 글줄 하나만 둥둥 떠 있었다).
            val cy = panelR.top + listTop + (panelR.height() - listTop - footerH) / 2f
            val cx = panelR.centerX()
            textP.textSize = textDp(scene, 24f)
            textP.color = 0xFFC9A87B.toInt()
            UiKit.iconCenter(c, scene.game, kind.emoji, cx, cy - dp(scene, 10f) - textP.textSize * 0.3f, textP.textSize)
            textP.textSize = textDp(scene, 12.5f)
            textP.color = 0xFF6B5A48.toInt()
            val msg = "가진 ${kind.label}이(가) 없어요"
            c.drawText(msg, cx - textP.measureText(msg) / 2f, cy + dp(scene, 12f), textP)
            textP.textSize = textDp(scene, 10f)
            textP.color = 0xFF8A7360.toInt()
            val sub = "${CameraShops.buyHint(s.region)}에서 먼저 사 보세요!"
            c.drawText(sub, cx - textP.measureText(sub) / 2f, cy + dp(scene, 30f), textP)
        }

        if (hasPages) {
            val by = panelR.bottom - dp(scene, 32f)
            prevRect = RectF(panelR.left + dp(scene, 14f), by, panelR.left + dp(scene, 74f), by + dp(scene, 24f))
            nextRect = RectF(panelR.right - dp(scene, 74f), by, panelR.right - dp(scene, 14f), by + dp(scene, 24f))
            drawButton(c, scene, prevRect, "arrow_left 이전", if (page > 0) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
            drawButton(c, scene, nextRect, "arrow_right 다음", if (page < maxPage) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
            textP.textSize = textDp(scene, 10f)
            textP.color = 0xFF8A7360.toInt()
            val pg = "${page + 1} / ${maxPage + 1}"
            c.drawText(pg, panelR.centerX() - textP.measureText(pg) / 2, by + dp(scene, 16f), textP)
        } else {
            // 한 페이지면 넘김 단추가 필요 없다 — 쓸데없는 발판을 치운다
            prevRect = RectF()
            nextRect = RectF()
        }
    }
}

