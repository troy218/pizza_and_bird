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
     * true 인 동안 Game.render() 는 월드 비트맵을 매 프레임 다시 그리지 않고
     * 몇 프레임에 한 번만 갱신한다. 월드는 프레임마다 수천 번 drawBitmap 을 하므로,
     * 메뉴가 떠 있는 동안 이것을 줄이는 것이 버튼이 붙는 지를 살리는 핵심이다.
     * (부분창 대화상자처럼 뒤 화면이 그대로 보이는 오버레이는 false 로 둔다)
     */
    open val coversWorld: Boolean get() = false

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
    textP.textSize = dp(scene, sz)
    while (textP.measureText(text) > maxW && sz > minSize) {
        sz -= 0.5f
        textP.textSize = dp(scene, sz)
    }
    c.drawText(text, x, y, textP)
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

        // 본문 줄 수에 맞춰 패널 높이 결정 (본문 = 보통 두께가 읽기 편하다)
        val bodyPaint = Type.paint(Role.BODY, Type.INK)
        val maxW = w - margin * 2f - dp(scene, 28f)
        val lines = Type.wrap(body, bodyPaint, maxW).take(8)
        val panelH = dp(scene, 108f) + dp(scene, 17f) * (lines.size - 3).coerceAtLeast(0)
        val r = RectF(margin, h - margin - panelH, w - margin, h - margin)

        // 등장: 아래에서 위로 부드럽게
        c.save()
        c.translate(0f, enterShift())
        panel(c, r, scene)

        if (title.isNotEmpty()) {
            val tw = textP.measureText(title)
            val chip = RectF(r.left + dp(scene, 12f), r.top - dp(scene, 11f), r.left + dp(scene, 12f) + tw + dp(scene, 16f), r.top + dp(scene, 11f))
            // 다크 칩 (골드 테두리 느낌의 프리미엄 뱃지)
            UiKit.button(c, g, chip, title, 0xFF6B4F35.toInt(), 0xFFF8EFDC.toInt(), 11f)
        }

        // 본문 — 한 줄에 Type.lineHeight 만큼만 내려간다(줄 간격 통일)
        var ty = r.top + dp(scene, 24f)
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
// 메뉴 (상태 / 피자 / 도감 / 설정)
// ---------------------------------------------------------------------------

class MenuOverlay(scene: Scene) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
    override val coversWorld: Boolean get() = true



    init {
        scene.game.sfx(Audio.Sfx.BAG_OPEN, 0.6f)   // 🎒 가방 지퍼 열리는 소리
    }

    /** 탭마다 파스텔 색이 다르다 — 가방 속 색색의 인덱스 탭처럼 */
    private enum class Tab(val label: String, val icon: String, val tint: Int) {
        STATUS("상태", "📊", UiKit.PASTEL_PEACH),
        QUEST("퀘스트", "🗺", UiKit.PASTEL_SKY),
        GROW("성장", "🌱", UiKit.PASTEL_MINT),
        PIZZA("피자", "🍕", UiKit.PASTEL_LEMON),
        BOOK("도감", "📚", UiKit.PASTEL_LILAC),
        SETTINGS("설정", "⚙", UiKit.PASTEL_SAND)
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
        if (closeRect.contains(tap.x, tap.y)) {
            g.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
            return
        }
        for ((r, t) in tabRects) {
            if (r.contains(tap.x, tap.y)) {
                // 📚 도감 탭은 책장 넘기는 소리로 열린다
                if (t == Tab.BOOK && tab != Tab.BOOK) g.sfx(Audio.Sfx.BOOK_OPEN, 0.7f)
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

    /**
     * 메인 퀘스트 자동 진행 — 카드 탭 시 어드바이저의 추천 지역으로 바로 이동.
     * 이미 추천 위치에 있으면 "도착 후 할 일" 팁으로 답한다.
     */
    private fun autoGoMainQuest() {
        val g = scene.game
        val s = g.state
        if (MainStory.current(s) == null) return
        val adv = MainQuestAdvisor.advise(s) ?: return
        if (adv.alreadyThere || adv.regionId == s.region) {
            // 이미 추천 지역 안 — 이동 대신 "그냥 여기" 안내
            g.toast("📍 ${adv.regionName} · ${adv.reason}")
            g.toast(adv.tip)
            return
        }
        g.toast("🚲 ${adv.regionName}으로 출발! · ${adv.reason}")
        g.toast(adv.tip)
        finished = true
        fastTravel(g, adv.regionId)
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
        val titleTxt = "🎒 여행 가방"
        val tagW = UiKit.nameTagWidth(g, titleTxt, 15f)
        val tagR = RectF(flapR.left + dp(scene, 10f), flapR.centerY() - dp(scene, 13f), flapR.left + dp(scene, 10f) + tagW, flapR.centerY() + dp(scene, 13f))
        UiKit.nameTag(c, g, tagR, titleTxt, 15f)
        // 반짝이 두 개 (숨쉬듯 깜빡)
        UiKit.sparkle(c, tagR.right + dp(scene, 9f), tagR.top + dp(scene, 4f), dp(scene, 7f), 0xFFFFF1C2.toInt(), g.time * 3.1f)
        UiKit.sparkle(c, tagR.right + dp(scene, 17f), tagR.bottom - dp(scene, 5f), dp(scene, 5f), 0xFFFFF1C2.toInt(), g.time * 3.1f + 2.1f)
        // 서브 타이틀 (넓을 때만 — 닫기 단추와 겹침 방지)
        if (panelR.width() > dp(scene, 420f)) {
            textP.textSize = dp(scene, 10f)
            textP.color = 0xFFFFF3DC.toInt()
            c.drawText("지금 펼친 칸 · ${tab.icon} ${tab.label}", tagR.right + dp(scene, 30f), flapR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        }

        val closeCx = flapR.right - dp(scene, 20f)
        val closeCy = flapR.centerY()
        val closeRr = dp(scene, 12f)
        closeRect = RectF(
            closeCx - closeRr - dp(scene, 6f), closeCy - closeRr - dp(scene, 6f),
            closeCx + closeRr + dp(scene, 6f), closeCy + closeRr + dp(scene, 6f)
        )
        UiKit.circleButton(c, g, closeCx, closeCy, closeRr, "✕", 11f, 0xFFFFF3DC.toInt(), 0xFF8A4A2A.toInt())

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
            if (t == tab) {
                val r = RectF(x0, tabTop - dp(scene, 3f), x0 + tabW, tabTop + tabH - dp(scene, 1f))
                cuteBtn(c, r, "${t.icon} ${t.label}", t.tint, UiKit.INK, 12.5f)
                UiKit.sparkle(c, r.right - dp(scene, 4f), r.top - dp(scene, 1f), dp(scene, 6f), 0xFFFFFFFF.toInt(), g.time * 4f + i)
            } else {
                val r = RectF(x0 + dp(scene, 1.5f), tabTop + dp(scene, 2f), x0 + tabW - dp(scene, 1.5f), tabTop + tabH - dp(scene, 1f))
                UiKit.cuteButton(c, g, r, "${t.icon} ${t.label}", UiKit.lighten(t.tint, 26), UiKit.MUTED, 11.5f, depthDp = 2f)
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
            Tab.SETTINGS -> drawSettings(c)
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
        UiKit.iconCircle(c, g, left + dp(scene, 17f), walletR.centerY(), dp(scene, 11f), "💰", 12f, UiKit.GOLD)
        textP.textSize = dp(scene, 12f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText("내 지갑", left + dp(scene, 34f), walletR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        textP.textSize = dp(scene, 14f)
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
        textP.textSize = dp(scene, 11f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🍕 배고픔", left + dp(scene, 10f), ry, textP)
        val hungerCols = if (s.hunger < 25f) (0xFFF28B82.toInt() to 0xFFE2574C.toInt()) else (0xFFFFB35C.toInt() to 0xFFF2913C.toInt())
        UiKit.bar(c, g, barX, ry - dp(scene, 10f), barW, dp(scene, 12f), s.hunger / 100f, hungerCols.first, hungerCols.second)
        textP.textSize = dp(scene, 10.5f)
        val hungerTxt = "${s.hunger.toInt()}"
        c.drawText(hungerTxt, valX - textP.measureText(hungerTxt), ry, textP)
        // 행운 행
        ry = y + dp(scene, 40f)
        textP.textSize = dp(scene, 11f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("☘️ 행운", left + dp(scene, 10f), ry, textP)
        UiKit.bar(c, g, barX, ry - dp(scene, 10f), barW, dp(scene, 12f), s.effectiveLuck() / 100f, 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        textP.textSize = dp(scene, 10.5f)
        val luckBonus = decorLuck + s.bikeLuck()
        val luckTxt = "${s.effectiveLuck().toInt()}" + (if (luckBonus > 0) "(+$luckBonus)" else "")
        c.drawText(luckTxt, valX - textP.measureText(luckTxt), ry, textP)
        y += vitH + dp(scene, 6f)

        // 2.5) 탐조가 레벨 스트립 — 레벨/칭호 + 미니 경험치 바
        val lvH = dp(scene, 28f)
        val lvR = RectF(left, y, right, y + lvH)
        cuteCard(c, lvR, UiKit.PASTEL_MINT, 0xFF7FB37A.toInt(), 1.6f, stitched = false)
        textP.textSize = dp(scene, 11f)
        val lvCy = lvR.centerY() - (textP.descent() + textP.ascent()) / 2f
        textP.color = 0xFF4A3728.toInt()
        var lvTxt = "🌱 Lv.${s.level} 「${s.title()}」" + (if (s.skillPoints > 0) " · SP ${s.skillPoints}" else "")
        val expBarW = dp(scene, 86f)
        val maxLvW = (right - left) - dp(scene, 24f) - expBarW - dp(scene, 8f)
        if (textP.measureText(lvTxt) > maxLvW && maxLvW > dp(scene, 40f)) {
            while (lvTxt.length > 1 && textP.measureText("$lvTxt…") > maxLvW) lvTxt = lvTxt.dropLast(1)
            lvTxt = "$lvTxt…"
        }
        c.drawText(lvTxt, left + dp(scene, 10f), lvCy, textP)
        if (s.level >= Progression.MAX_LEVEL) {
            textP.textSize = dp(scene, 10f)
            textP.color = 0xFFB5651D.toInt()
            val maxT = "MAX ★"
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
        textP.textSize = dp(scene, 11f)
        textP.color = 0xFF4A3728.toInt()
        var camName = rig.title
        val camMaxW = (right - camTx) - dp(scene, 92f)
        if (textP.measureText(camName) > camMaxW && camMaxW > dp(scene, 40f)) {
            while (camName.length > 1 && textP.measureText("$camName…") > camMaxW) camName = camName.dropLast(1)
            camName = "$camName…"
        }
        c.drawText(camName, camTx, camR.top + dp(scene, 15f), textP)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("환산 ${rig.teleMm}mm · 반경 ${rig.reach.fmt1()}칸 · ${rig.sensor.label}", camTx, camR.top + dp(scene, 27f), textP)
        val bagR = RectF(right - dp(scene, 84f), camR.centerY() - dp(scene, 12f), right - dp(scene, 7f), camR.centerY() + dp(scene, 10f))
        cuteBtn(c, bagR, "🎒 장비 가방", UiKit.GOLD, 0xFF4A2E12.toInt(), 9.5f)
        btnRects.add(Triple(bagR, "gearbag") { scene.openOverlay(GearBagOverlay(scene)) })
        y += camH + dp(scene, 6f)

        // 4) 정보 그리드 — 남은 높이에 맞춰 자동 배분 (2열 x 5행)
        val gap = dp(scene, 5f)
        val rows = 5
        val gridH = contentBottom() - y
        val rowH = ((gridH - gap * (rows - 1)) / rows).coerceAtLeast(dp(scene, 18f))
        val colW = (right - left - gap) / 2f
        val cells = listOf(
            "🕐 시각" to "${s.timeLabel()} ${s.timeEmoji()} · 📸${s.photos}",
            "🏠 우리 집" to (Regions.byId[s.homeRegion]?.name ?: "?"),
            "📍 위치" to "${Regions.byId[s.region]?.name ?: "?"} · ${s.visited.size}/${Regions.ALL.size}",
            "🏘 주택" to "${s.ownedHomes.size}채 · 🎨${s.ownedHouseStyles.size}/${HouseStyles.ALL.size}",
            "📚 도감" to "${s.birdCounts.size}/${Birds.ALL.size}종",
            "🔍 의뢰" to (s.questBird?.let { Birds.byId[it]?.name } ?: "없음"),
            "⏱ 플레이" to timeStr,
            "🚲 자전거" to "${s.bike().name} · ${s.ownedBikes.size}대" + (if (s.ownedBikeParts.isNotEmpty()) " · 부속품 ${s.ownedBikeParts.size}" else ""),
            "🍕 피자" to "${s.pizzaCount}개 (🔥${s.pizzaCountOfKind(PizzaKind.OVEN)} · 🍕${s.pizzaCountOfKind(PizzaKind.REGULAR)}) · 🧺${s.placedDecorIds().size}/${s.decorSlots.size} · 행운+${s.decorLuck()}"
        )
        for (i in cells.indices) {
            val col = i % 2
            val row = i / 2
            val cr = RectF(
                left + col * (colW + gap), y + row * (rowH + gap),
                left + col * (colW + gap) + colW, y + row * (rowH + gap) + rowH
            )
            if (cr.top > contentBottom() - dp(scene, 10f)) continue
            // 격자무늬 천처럼 두 색을 번갈아 (가로세로 체크)
            val checker = (col + row) % 2 == 0
            cuteCard(c, cr, if (checker) UiKit.CARD_HI else 0xFFFFF4E2.toInt(), stitched = rowH >= dp(scene, 24f))
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            val label = cells[i].first
            val labelW = textP.measureText(label)
            val ty = cr.centerY() - (textP.descent() + textP.ascent()) / 2f
            c.drawText(label, cr.left + dp(scene, 8f), ty, textP)
            // 값은 오른쪽 정렬 + 넘치면 말줄임
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF4A3728.toInt()
            var value = cells[i].second
            val maxVW = cr.width() - dp(scene, 16f) - labelW - dp(scene, 6f)
            if (textP.measureText(value) > maxVW && maxVW > dp(scene, 20f)) {
                while (value.length > 1 && textP.measureText("$value…") > maxVW) value = value.dropLast(1)
                value = "$value…"
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
        advice?.let { adv ->
            // 추천 위치 한 줄 — "어디로 가야 하는지"를 카드에 직접 보여준다
            val here = adv.alreadyThere || adv.regionId == s.region
            var rec = if (here) {
                "📍 ${adv.regionName} · ${adv.reason} · 카드 탭하면 힌트"
            } else {
                "📍 ${adv.regionName} · ${adv.reason} · 카드 탭하면 이동"
            }
            textP.textSize = dp(scene, 10f)
            textP.color = if (here) 0xFF397547.toInt() else 0xFFB5651D.toInt()
            val maxRecW = mainR.width() - dp(scene, 20f)
            while (rec.length > 4 && textP.measureText(rec) > maxRecW) rec = rec.dropLast(1)
            c.drawText(rec, mainR.left + dp(scene, 10f), mainR.top + dp(scene, 70f), textP)
            btnRects.add(Triple(mainR, "main_auto") { autoGoMainQuest() })
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
            cuteCard(c, r, if (done) UiKit.PASTEL_MINT else 0xFFFBF1DE.toInt(),
                if (done) 0xFF7FB37A.toInt() else UiKit.BROWN_LINE, 1.5f, stitched = rowH >= dp(scene, 30f))
            if (done) {
                // 완료 도장 (빨간 동그라미 스탬프)
                strokeP.color = Color.argb(180, 226, 87, 76)
                strokeP.strokeWidth = dp(scene, 1.6f)
                c.drawCircle(r.right - dp(scene, 18f), r.centerY(), dp(scene, 9f), strokeP)
                textP.textSize = dp(scene, 9f)
                textP.color = Color.argb(200, 226, 87, 76)
                c.drawText("완료", r.right - dp(scene, 18f) - textP.measureText("완료") / 2f, r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
            }
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
            cuteBtn(c, prevR, "‹ 이전", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
            btnRects.add(Triple(prevR, "quest_prev") { questPage-- })
        }
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val pageText = "도장 깨기 ${questPage + 1}/$pages"
        c.drawText(pageText, panelR.centerX() - textP.measureText(pageText) / 2, contentBottom() - dp(scene, 7f), textP)
        if (questPage < pages - 1) {
            cuteBtn(c, nextR, "다음 ›", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
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

        val tx = left + avatar.width * ak + dp(scene, 12f)
        textP.textSize = dp(scene, 16f)
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
            cuteBtn(c, trR, "🎓 탐조 강습 ₩${fmtMoney(tc)}", UiKit.GOLD, 0xFF4A2E12.toInt(), 10.5f)
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

            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(def.name, r.left + dp(scene, 38f), midY, textP)

            textP.textSize = dp(scene, 10f)
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
                maxed -> UiKit.badge(c, g, RectF(br.left, br.centerY() - dp(scene, 10f), br.right, br.centerY() + dp(scene, 10f)), "MAX ★", UiKit.GOLD, 0xFF4A2E12.toInt(), 11f)
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
        textP.textSize = dp(scene, 12f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🍕 피자 가방", left + dp(scene, 10f), ty + dp(scene, 16f), textP)
        textP.textSize = dp(scene, 11f)
        textP.color = 0xFFB5651D.toInt()
        val capEff = s.pizzaCapEff()
        val capTxt = "${s.pizzaCount}/$capEff"
        val gaugeW = dp(scene, 120f)
        c.drawText(capTxt, left + dp(scene, 10f) + gaugeW - textP.measureText(capTxt), ty + dp(scene, 16f), textP)
        UiKit.bar(c, g, left + dp(scene, 10f), ty + dp(scene, 22f), gaugeW, dp(scene, 9f),
            s.pizzaCount / capEff.toFloat(), 0xFFFFD97A.toInt(), 0xFFF2A33C.toInt())
        textP.textSize = dp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        run {
            var descTxt = "${kind.station}에서 구워요 · ${kind.desc}"
            val maxDescW = gaugeW + dp(scene, 4f)
            textP.textSize = dp(scene, 9f)
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
            textP.textSize = dp(scene, 9.5f)
            val badgeTxt = "×$total"
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
                textP.textSize = dp(scene, 8.5f)
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
                cuteBtn(c, br, "냠냠 😋", UiKit.PASTEL_TOMATO, 0xFF4A2E12.toInt(), 11f)
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
        val rawAreaH = contentBottom() - (contentTop() + dp(scene, 38f)) - dp(scene, 30f)
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
        c.drawText("📚 도감 $done/${total}종", areaLeft, contentTop() + dp(scene, 8f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFFB5651D.toInt()
        val pctTxt = "${(done * 100f / total).toInt()}% 완성!"
        c.drawText(pctTxt, areaLeft + areaW - textP.measureText(pctTxt), contentTop() + dp(scene, 8f), textP)
        UiKit.bar(c, g, areaLeft, contentTop() + dp(scene, 13f), areaW, dp(scene, 8f),
            done / total.toFloat(), 0xFF8FD694.toInt(), 0xFF4E9A51.toInt())
        textP.textSize = dp(scene, 8.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(OfficialBirdChecklist.SOURCE_TITLE, areaLeft, contentTop() + dp(scene, 33f), textP)

        // 도감 모드 전환 버튼 (사진 썸네일 vs 도트 스프라이트)
        val modeLabel = if (bookPhotoMode) "🖼 사진 모드" else "👾 도트 모드"
        textP.textSize = dp(scene, 8.2f)
        val modeBtnW = textP.measureText(modeLabel) + dp(scene, 16f)
        val modeBtnH = dp(scene, 17f)
        val modeBtnR = RectF(areaLeft + areaW - modeBtnW, contentTop() + dp(scene, 21f), areaLeft + areaW, contentTop() + dp(scene, 21f) + modeBtnH)
        drawButton(c, scene, modeBtnR, modeLabel, 0xFFF2E3C2.toInt(), 0xFF5A4430.toInt(), 8.5f)
        btnRects.add(Triple(modeBtnR, modeLabel) {
            bookPhotoMode = !bookPhotoMode
            g.sfx(Audio.Sfx.TAP, 0.45f)
        })

        val areaTop = contentTop() + dp(scene, 38f)
        val pagerH = dp(scene, 30f)
        val areaH = contentBottom() - areaTop - pagerH
        val gap = dp(scene, 6f)
        val cw = (areaW - gap * (cols - 1)) / cols
        val chh = (areaH - gap * (rowsPerPage - 1)) / rowsPerPage

        val rects = LinkedHashMap<String, RectF>()
        val startIndex = bookPage * pageSize
        val pageItems = Birds.ALL.drop(startIndex).take(pageSize)
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
                        textP.textSize = dp(scene, 9.5f)
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
                    textP.textSize = dp(scene, 9.5f)
                    textP.color = 0xFFF8EFDC.toInt()
                    c.drawText("?", bx + bmp.width * k / 2f - textP.measureText("?") / 2f, r.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
                }
                tx = bx + bmp.width * k + dp(scene, 5f)
            }

            val top0 = r.centerY() - dp(scene, 20f)
            textP.textSize = dp(scene, 10.2f)
            textP.color = if (seen) 0xFF4A3728.toInt() else Color.argb(175, 74, 55, 40)
            c.drawText(def.name, tx, top0 + dp(scene, 12f), textP)
            textP.textSize = dp(scene, 8.2f)
            textP.color = 0xFF8A7360.toInt()
            val baseLine2 = if (seen) {
                val best = s.bestStars[def.id] ?: 1
                "📸${s.birdCounts[def.id]} 최고★$best"
            } else {
                "미촬영 · ${def.tier.label}"
            }
            val line2 = baseLine2 + if (def.active == "night") " 🌙" else ""
            c.drawText(line2, tx, top0 + dp(scene, 24f), textP)
            textP.textSize = dp(scene, 7.8f)
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

        val py = contentBottom() - dp(scene, 25f)
        val prev = RectF(areaLeft, py, areaLeft + dp(scene, 82f), py + dp(scene, 22f))
        val next = RectF(areaLeft + areaW - dp(scene, 82f), py, areaLeft + areaW, py + dp(scene, 22f))
        pagerButton(prev, "◀ 이전", bookPage > 0) { bookPage-- }
        pagerButton(next, "다음 ▶", bookPage < totalPages - 1) { bookPage++ }
        val pageText = "${bookPage + 1} / $totalPages"
        textP.textSize = dp(scene, 11.5f)
        val pillW = textP.measureText(pageText) + dp(scene, 22f)
        UiKit.badge(
            c, g, RectF(panelR.centerX() - pillW / 2f, py, panelR.centerX() + pillW / 2f, py + dp(scene, 22f)),
            pageText, 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 11.5f
        )
    }

    private fun drawSettings(c: Canvas) {
        val g = scene.game
        val left = panelR.left + dp(scene, 12f)
        val right = panelR.right - dp(scene, 12f)
        // 가로가 넉넉하면 2열 배치 (세로 공간 절약)
        val wide = panelR.width() > dp(scene, 560f)
        val colW = if (wide) (right - left - dp(scene, 12f)) / 2f else right - left
        val yCol = floatArrayOf(contentTop() + dp(scene, 4f), contentTop() + dp(scene, 4f))

        fun rowAt(
            col: Int, icon: String, label: String, sub: String,
            danger: Boolean, switch: Boolean?, action: () -> Unit
        ) {
            val rx = if (col == 0) left else left + colW + dp(scene, 12f)
            val yy = yCol[col]
            val rh = dp(scene, 46f)
            val r = RectF(rx, yy, rx + colW, yy + rh)
            cuteCard(c, r, if (danger) 0xFFFFE6E1.toInt() else UiKit.CARD_HI,
                if (danger) 0xFFE2574C.toInt() else UiKit.BROWN_LINE, if (danger) 2f else 1.6f)
            UiKit.iconCircle(c, g, rx + dp(scene, 24f), r.centerY(), dp(scene, 14f), icon, 15f,
                if (danger) 0xFFF28B82.toInt() else if (switch == true) UiKit.PASTEL_MINT else UiKit.PASTEL_PEACH)
            textP.textSize = dp(scene, 13f)
            textP.color = if (danger) 0xFFB03A30.toInt() else 0xFF4A3728.toInt()
            c.drawText(label, rx + dp(scene, 46f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, rx + dp(scene, 46f), r.centerY() + dp(scene, 13f), textP)
            if (switch != null) {
                // 픽셀 토글 스위치
                UiKit.cuteToggle(c, g, r.right - dp(scene, 14f), r.centerY(), switch)
            } else {
                // 작은 화살표 단추
                val ar = RectF(r.right - dp(scene, 34f), r.centerY() - dp(scene, 11f), r.right - dp(scene, 10f), r.centerY() + dp(scene, 9f))
                UiKit.cuteButton(c, g, ar, "›", if (danger) 0xFFF28B82.toInt() else UiKit.PASTEL_PEACH,
                    if (danger) 0xFF7A1E14.toInt() else UiKit.INK, 14f, depthDp = 2f)
            }
            btnRects.add(Triple(r, label, action))
            yCol[col] = yy + rh + dp(scene, 8f)
        }

        // 1열: 사운드 + 조이스틱 설정
        rowAt(0, if (g.state.musicOn) "🎵" else "🔇", "음악: " + if (g.state.musicOn) "켜짐" else "꺼짐", "배경 음악을 켜고 꺼요", false, null) {
            g.state.musicOn = !g.state.musicOn
            g.audio.setMusic(g.state.musicOn)
            SaveManager.save(g.context, g.state)
        }
        rowAt(0, if (g.state.sfxOn) "🔊" else "🔈", "효과음: " + if (g.state.sfxOn) "켜짐" else "꺼짐", "새 소리와 버튼음을 켜고 꺼요", false, null) {
            g.state.sfxOn = !g.state.sfxOn
            g.audio.setSfx(g.state.sfxOn)
            SaveManager.save(g.context, g.state)
        }
        rowAt(0, "🕹️", "움직이는 조이스틱: " + if (g.state.floatStick) "켜짐" else "꺼짐",
            "왼쪽 아래를 끌면 그 자리에 스틱이 생겨요", false, g.state.floatStick) {
            g.state.floatStick = !g.state.floatStick
            g.hud.releaseStick()
            SaveManager.save(g.context, g.state)
            g.toast(if (g.state.floatStick) "움직이는 스틱 켬 🕹️" else "고정 스틱만 쓸게요")
        }
        rowAt(0, "🎚️", "민 만큼 속도: " + if (g.state.analogStick) "켜짐" else "꺼짐",
            "스틱을 살짝 밀면 살금살금, 끝까지 밀면 쌩쌩", false, g.state.analogStick) {
            g.state.analogStick = !g.state.analogStick
            SaveManager.save(g.context, g.state)
            g.toast(if (g.state.analogStick) "아날로그 이동 켬 — 틱을 민 만큼 걸어요" else "일정 속도로 걸어요")
        }

        rowAt(0, "🎥", "화면 연출 (몰입감)",
            "흔들림·헤드밥·잔상·심도 — 멀미가 있다면 여기서 꺼요", false, null) {
            scene.openOverlay(CameraFxOverlay(scene))
        }

        // 2열(화면이 좁으면 1열 이어서): 화질/보간/저장/타이틀/초기화
        val c2 = if (wide) 1 else 0
        rowAt(c2, "🖥", "화질: " + when (g.state.renderScale) {
            "1" -> "1배 (성능 우선)"
            "2" -> "2배 (고화질)"
            "3" -> "3배 (최고 화질)"
            else -> "자동 (2K 기준)"
        }, "월드 렌더 해상도 — 높을수록 또렷해요", false, null) {
            g.state.renderScale = when (g.state.renderScale) {
                "auto" -> "1"; "1" -> "2"; "2" -> "3"; else -> "auto"
            }
            g.applyRenderQuality()
            SaveManager.save(g.context, g.state)
        }
        rowAt(c2, "🎨", "화면 보간: " + if (g.state.smoothScreen) "부드럽게" else "끔 (픽셀 선명)",
            "픽셀 아트를 부드럽게 확대해 보여요", false, null) {
            g.state.smoothScreen = !g.state.smoothScreen
            g.applyRenderQuality()
            SaveManager.save(g.context, g.state)
        }
        rowAt(c2, "💾", "저장하기", "지금까지의 여행을 안전하게 보관해요", false, null) {
            SaveManager.save(g.context, g.state)
            g.toast("저장 완료! ✨")
        }
        rowAt(c2, "🏠", "타이틀로 가기", "저장 후 타이틀 화면으로 돌아가요", false, null) {
            SaveManager.save(g.context, g.state)
            finished = true
            g.fadeTo { g.scene = TitleScene(g) }
        }
        if (resetArmed) {
            rowAt(c2, "⚠️", "정말 처음부터 시작할까요?", "되돌릴 수 없어요! 다시 누르면 초기화돼요", true, null) {
                SaveManager.clear(g.context)
                g.state.reset("seoul")
                g.state.started = false
                finished = true
                g.fadeTo { g.scene = TitleScene(g) }
            }
        } else {
            rowAt(c2, "🗑", "처음부터 다시 시작", "저장 데이터를 모두 지우고 새로 시작해요", false, null) {
                resetArmed = true
            }
        }

        // 푸터 정보 카드
        val ty = maxOf(yCol[0], yCol[1])
        val footR = RectF(left, ty, right, contentBottom())
        if (footR.height() > dp(scene, 40f)) {
            cuteCard(c, footR, UiKit.PASTEL_SAND)
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF6B4F35.toInt()
            c.drawText("🍕 Pizza and Bird v0.4.2-beta01 · 2K", left + dp(scene, 12f), ty + dp(scene, 18f), textP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText("완전 오프라인 힐링 게임 · 저장은 자동으로 돼요", left + dp(scene, 12f), ty + dp(scene, 33f), textP)
            if (footR.height() > dp(scene, 62f)) {
                c.drawText("조이스틱은 왼쪽 아래 어디든 잡으면 그 자리에 생겨요!", left + dp(scene, 12f), ty + dp(scene, 47f), textP)
            }
            // 내장 글꼴 출처 표기 (SIL Open Font License 1.1 — assets/font/OFL.txt)
            if (footR.height() > dp(scene, 76f)) {
                textP.textSize = dp(scene, 9f)
                c.drawText("글꼴: 주아(Jua) · 고운돋움(Gowun Dodum) — SIL Open Font License 1.1",
                    left + dp(scene, 12f), ty + dp(scene, 61f), textP)
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
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "✕", 11f)

        textP.textSize = dp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🎥 화면 연출", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = dp(scene, 10f)
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
            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(label, left + dp(scene, 42f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = dp(scene, 9f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, left + dp(scene, 42f), r.centerY() + dp(scene, 12f), textP)

            textP.textSize = dp(scene, 10.5f)
            val vw = textP.measureText(value) + dp(scene, 20f)
            UiKit.badge(
                c, g,
                RectF(right - dp(scene, 10f) - vw, r.centerY() - dp(scene, 10f), right - dp(scene, 10f), r.centerY() + dp(scene, 10f)),
                value, if (on) 0xFF6FBA6B.toInt() else 0xFFC0B2A0.toInt(), 0xFFFDF8EC.toInt(), 10.5f
            )
            rowRects.add(r to action)
            ty += rowH + gap
        }

        row("📳", "카메라 흔들림", "타격·충돌·셔터의 충격을 화면으로", CamFx.shakeLabel(st), st.camShake > 0) {
            st.camShake = (st.camShake + 1) % CamFx.SHAKE_LABELS.size
            g.shake(0.5f)                       // 바꾼 강도를 바로 체감해 볼 수 있게
        }
        row("🚶", "헤드 밥 · 바디 스웨이", "걸음 리듬에 맞춰 화면이 출렁여요", CamFx.onOff(st.camBob), st.camBob) {
            st.camBob = !st.camBob
        }
        row("💨", "잔상 · 속도선", "달리기·자전거에서 속도가 느껴지게", CamFx.onOff(st.camBlur), st.camBlur) {
            st.camBlur = !st.camBlur
        }
        row("🔭", "시야각 변동 (FOV)", "빠르면 넓게, 카메라 모드에선 망원으로", CamFx.onOff(st.camFov), st.camFov) {
            st.camFov = !st.camFov
            if (st.camFov) g.punchZoom(0.06f)
        }
        row("🌙", "심도 · 초점 흐림", "카메라 모드에서 초점 밖을 어둡게", CamFx.onOff(st.camDof), st.camDof) {
            st.camDof = !st.camDof
        }
        row("🧭", "예측 배치", "가는 방향의 앞쪽을 더 보여줘요", CamFx.onOff(st.camLead), st.camLead) {
            st.camLead = !st.camLead
        }

        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("💡 3D 멀미가 있다면 흔들림을 '끔'으로 두세요. 바로 저장돼요.", left + dp(scene, 2f), ty + dp(scene, 14f), textP)
    }
}

// ---------------------------------------------------------------------------
// 장식 상점 (사진용품점 장식 코너)
// ---------------------------------------------------------------------------

class DecorShopOverlay(scene: Scene) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "✕", 11f)

        textP.textSize = dp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🧺 장식 코너", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("집에 놓으면 행운이 오르는 소품들이에요", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 44f), textP)
        textP.textSize = dp(scene, 10.5f)
        val moneyTxt = "💰 ${won(s.money)}"
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
            textP.textSize = dp(scene, 12f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(d.name, tx, r.top + dp(scene, 17f), textP)
            val luckTxt = "행운+${d.luck}"
            textP.textSize = dp(scene, 9f)
            val luckW = textP.measureText(luckTxt) + dp(scene, 11f)
            val nameW = run { textP.textSize = dp(scene, 12f); textP.measureText(d.name) }
            UiKit.badge(
                c, g, RectF(tx + nameW + dp(scene, 6f), r.top + dp(scene, 6f), tx + nameW + dp(scene, 6f) + luckW, r.top + dp(scene, 21f)),
                luckTxt, 0xFF6FBA6B.toInt(), 0xFFFFF8E8.toInt(), 9f
            )
            // 설명 (말줄임)
            textP.textSize = dp(scene, 9.5f)
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
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
        val ph = minOf(dp(scene, 118f) + dp(scene, 48f) * rowCount, h * 0.9f)
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "✕", 11f)

        textP.textSize = dp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("✨ 장식 칸 ${slot + 1}에 뭘 놓을까요?", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 29f), textP)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("소품 하나는 한 칸에만 배치할 수 있어요", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 44f), textP)
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
            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(name, r.left + dp(scene, 46f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(sub, r.left + dp(scene, 46f), r.centerY() + dp(scene, 13f), textP)
            textP.textSize = dp(scene, 15f)
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
        row(-1, "🫙", "빈 칸으로 두기", "장식을 치웁니다", 0xFFD9CFC0.toInt())
    }
}

// ---------------------------------------------------------------------------
// 집 꾸미기 보드 — 8칸 레이아웃, 자동 정리, 컬렉션 효과를 한 화면에서 관리
// ---------------------------------------------------------------------------

class HomeDecorOverlay(scene: Scene) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
            g.toast("보유 소품 ${count}개를 순서대로 정리했어요 ✨")
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
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "✕", 11f)

        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🪴 집 꾸미기 보드", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
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
            UiKit.iconCircle(c, g, r.left + dp(scene, 19f), r.centerY(), dp(scene, 11f), d?.emoji ?: "＋", 13f,
                if (d != null) 0xFF6FBA6B.toInt() else 0xFFE9DDC7.toInt())
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText("${slot + 1}. ${d?.name ?: "비어 있음"}", r.left + dp(scene, 37f), r.centerY() - dp(scene, 1f), textP)
            textP.textSize = dp(scene, 8.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(if (d != null) "행운 +${d.luck}" else "탭해서 소품 놓기", r.left + dp(scene, 37f), r.centerY() + dp(scene, 11f), textP)
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
            "🏅 모든 컬렉션 완성! 세트 효과 +${s.decorSetBonus()}"
        } else {
            val n = next.members.count { it in placed }
            "${next.emoji} 다음: ${next.name}  $n/${next.required} · 완성 시 행운 +${next.bonus}"
        }
        c.drawText(infoText, info.left + dp(scene, 10f), info.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)

        autoRect = RectF(gridLeft, panelR.bottom - dp(scene, 40f), gridLeft + (gridRight - gridLeft - gap) * 0.62f, panelR.bottom - dp(scene, 10f))
        clearRect = RectF(autoRect.right + gap, autoRect.top, gridRight, autoRect.bottom)
        drawButton(c, scene, autoRect, "✨ 보유 소품 자동 정리", 0xFF6FBA6B.toInt(), 0xFFFFF8E8.toInt(), 10.5f)
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
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
        val ph = minOf(h * 0.92f, dp(scene, 360f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        val closeCx = panelR.right - dp(scene, 25f)
        val closeCy = panelR.top + dp(scene, 23f)
        closeRect = RectF(closeCx - dp(scene, 18f), closeCy - dp(scene, 18f), closeCx + dp(scene, 18f), closeCy + dp(scene, 18f))
        UiKit.circleButton(c, g, closeCx, closeCy, dp(scene, 12f), "✕", 11f)

        textP.textSize = dp(scene, 16f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🏠 우리 집 인테리어", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 28f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("구매한 스타일은 이사 후에도 계속 사용할 수 있어요", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 44f), textP)
        textP.textSize = dp(scene, 10.5f)
        val moneyTxt = "💰 ${won(s.money)}"
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
            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(style.name, r.left + dp(scene, 46f), r.centerY() - dp(scene, 1f), textP)
            // 설명 말줄임
            textP.textSize = dp(scene, 9.5f)
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
// 자전거 상점 — 모델 구매/교체 · 도색 · 부속품 (탈것은 자전거뿐!)
// ---------------------------------------------------------------------------

class BikeShopOverlay(scene: Scene) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
    override val coversWorld: Boolean get() = true



    private enum class Tab(val label: String) {
        MODEL("모델"), PAINT("도색"), PARTS("부속품")
    }

    private var tab = Tab.MODEL
    private var page = 0

    private var tabRects = ArrayList<Pair<RectF, Tab>>()
    private var modelRects = ArrayList<Pair<RectF, String>>()
    private var swatchRects = ArrayList<Pair<RectF, IntArray>>()   // [슬롯(0~2), 색인덱스]
    private var partRects = ArrayList<Pair<RectF, String>>()
    private var pageRects = ArrayList<Pair<RectF, Int>>()
    private var closeRect = RectF()
    private var panelR = RectF()
    private val pixelPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

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
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = dp(scene, 15f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🚲 자전거 상점", panelR.left + dp(scene, 16f), panelR.top + dp(scene, 30f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(
            "탈것은 자전거뿐! 모델을 고르고 도색·부속품으로 꾸며보세요 · 보유 ${won(s.money)}",
            panelR.left + dp(scene, 16f), panelR.top + dp(scene, 46f), textP
        )

        // 탭
        tabRects = ArrayList()
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
        val perPage = 5
        val totalPages = (Bikes.ALL.size + perPage - 1) / perPage
        val pageIdx = page.coerceIn(0, totalPages - 1)

        modelRects = ArrayList()
        pageRects = ArrayList()

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

            // 모델 미리보기 (현재 도색·부속품 그대로)
            val set = g.assets.bikeSet(s.gender, s.gearTier(), s.bikeStyleOf(bike.id))
            val iw = dp(scene, 44f)
            c.drawBitmap(set.side[0], null, RectF(r.left + dp(scene, 4f), r.top + dp(scene, 4f), r.left + dp(scene, 4f) + iw, r.top + dp(scene, 4f) + iw), pixelPaint)

            textP.textSize = dp(scene, 12.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText("${bike.emoji} ${bike.name}", r.left + dp(scene, 54f), r.top + dp(scene, 17f), textP)
            textP.textSize = dp(scene, 9.2f)
            textP.color = 0xFF8A7360.toInt()
            val stat = "속도 ${pctText(bike.speed - 1f)} · 배고픔 ${pctText(bike.hunger - 1f)} · 새 놀람 ${pctText((bike.scare - 1.4f) / 1.4f)}" +
                (if (bike.luck > 0) " · 행운 +${bike.luck}" else "")
            c.drawText(stat, r.left + dp(scene, 54f), r.top + dp(scene, 32f), textP)
            textP.textSize = dp(scene, 8.6f)
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
            drawButton(c, scene, prev, "◀ 이전", if (pageIdx > 0) 0xFFF2B63C.toInt() else Color.argb(90, 200, 190, 175), 0xFF4A3728.toInt(), 11f)
            drawButton(c, scene, next, "다음 ▶", if (pageIdx < totalPages - 1) 0xFFF2B63C.toInt() else Color.argb(90, 200, 190, 175), 0xFF4A3728.toInt(), 11f)
            if (pageIdx > 0) pageRects.add(prev to pageIdx - 1)
            if (pageIdx < totalPages - 1) pageRects.add(next to pageIdx + 1)
            textP.textSize = dp(scene, 11f)
            textP.color = 0xFF6B4F35.toInt()
            val pt = "${pageIdx + 1}/$totalPages"
            c.drawText(pt, panelR.centerX() - textP.measureText(pt) / 2f, py + dp(scene, 16.5f), textP)
        }
    }

    private fun drawPaint(c: Canvas) {
        val g = scene.game
        val s = g.state
        swatchRects = ArrayList()

        // 큰 미리보기
        val set = g.assets.bikeSet(s.gender, s.gearTier(), s.bikeStyle())
        val pv = dp(scene, 96f)
        val px = panelR.left + dp(scene, 20f)
        val py = panelR.top + dp(scene, 92f)
        c.drawBitmap(set.side[0], null, RectF(px, py, px + pv, py + pv), pixelPaint)
        textP.textSize = dp(scene, 12.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText(s.bike().name, px + pv + dp(scene, 16f), py + dp(scene, 26f), textP)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("도색은 언제든 무료예요.", px + pv + dp(scene, 16f), py + dp(scene, 44f), textP)
        c.drawText("색을 누르면 바로 바뀌어요!", px + pv + dp(scene, 16f), py + dp(scene, 58f), textP)

        var ty = panelR.top + dp(scene, 206f)
        drawSwatchRow(c, "프레임", 0, BikeColors.FRAME, s.bikeFrameColor, ty)
        ty += dp(scene, 44f)
        drawSwatchRow(c, "바퀴", 1, BikeColors.TIRE, s.bikeTireColor, ty)
        ty += dp(scene, 44f)
        drawSwatchRow(c, "안장·그립", 2, BikeColors.SADDLE, s.bikeSaddleColor, ty)
    }

    private fun drawSwatchRow(c: Canvas, label: String, slot: Int, colors: List<BikeColors.BikeColor>, sel: Int, ty: Float) {
        textP.textSize = dp(scene, 10.5f)
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
        partRects = ArrayList()
        var ty = panelR.top + dp(scene, 88f)
        for (part in BikeParts.ALL) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + dp(scene, 54f))
            fillP.color = 0xFFFDF6E8.toInt()
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), fillP)
            strokeP.color = 0xFFC9A87B.toInt()
            strokeP.strokeWidth = dp(scene, 1.5f)
            c.drawRoundRect(r, dp(scene, 9f), dp(scene, 9f), strokeP)

            textP.textSize = dp(scene, 20f)
            c.drawText(part.emoji, r.left + dp(scene, 10f), r.centerY() + dp(scene, 7f), textP)
            textP.textSize = dp(scene, 13f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(part.name, r.left + dp(scene, 44f), r.top + dp(scene, 18f), textP)
            textP.textSize = dp(scene, 9.6f)
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
            ty += dp(scene, 60f)
        }
    }
}

// ---------------------------------------------------------------------------
// 피자 굽기 미니게임 — 계열(화덕피자/일반 피자)별 메뉴 선택 + 타이밍
//   화덕(🔥): 화덕피자 — 커서가 빠르고 노랑 구간이 좁아 금방 탄다. 대신 효과가 크다.
//   오븐(🍕): 일반 피자 — 느긋하게 익는다. 굽기 쉽고 든든하다.
// ---------------------------------------------------------------------------

class BakeOverlay(scene: Scene, private val kind: PizzaKind = PizzaKind.OVEN) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
    override val coversWorld: Boolean get() = true



    private var step = 0                 // 0 메뉴 선택, 1 타이밍, 2 결과
    private var pizzaId = Pizzas.representative(kind).id
    private var t = 0.6f
    private var stopped = false
    private var resultQ = -1
    private var lostPizza = false
    private val menuRects = ArrayList<Pair<RectF, Int>>()
    private var cancelRect = RectF()

    private val menu: List<PizzaDef> = Pizzas.ofKind(kind)
    private val def: PizzaDef get() = Pizzas.of(pizzaId)

    private fun cursorPos(): Float = 0.5f + 0.5f * sin(t * 2.6f)

    override fun update(dt: Float) {
        if (step == 1 && !stopped) t += dt * def.cursorSpeed
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        when (step) {
            0 -> {
                if (input.justB || input.justBack) { finished = true; return }
                if (tap == null) return
                for ((r, id) in menuRects) {
                    if (r.contains(tap.x, tap.y)) {
                        pizzaId = id
                        step = 1
                        t = 0.6f
                        scene.game.sfx(Audio.Sfx.TAP, 0.6f)
                        // 🔥 화덕은 장작불 소리 크게, 가정용 오븐은 은은하게
                        scene.game.audio.playAmb(R.raw.amb_fire, if (kind == PizzaKind.OVEN) 0.5f else 0.25f)
                        return
                    }
                }
                if (cancelRect.contains(tap.x, tap.y)) {
                    scene.game.sfx(Audio.Sfx.TAP, 0.5f)
                    finished = true
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

    private fun stopBake() {
        stopped = true
        step = 2
        val pos = cursorPos()
        val half = def.perfectW / 2f
        val goodW = kind.goodW
        resultQ = when {
            pos < 0.5f - half - goodW || pos > 0.5f + half + goodW -> 0
            abs(pos - 0.5f) <= half -> 2
            else -> 1
        }
        lostPizza = !scene.game.state.addPizza(pizzaId, resultQ)
        // 화덕의 충격을 몸으로 — 걸작일수록 크게 울린다
        when (resultQ) {
            2 -> { scene.game.shake(0.3f); scene.game.punchZoom(0.05f) }
            1 -> scene.game.shake(0.16f)
            else -> scene.game.kick(0f, 1f, 1.4f)
        }
        SaveManager.save(scene.game.context, scene.game.state)
        scene.game.audio.stopAmb()   // 불 소리 끄기
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
        val cw = minOf(w * 0.84f, dp(scene, 500f))
        val chh = if (step == 0) minOf(h - dp(scene, 16f), dp(scene, 290f)) else dp(scene, 240f)
        val r = RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f)
        panel(c, r, scene)

        val a = g.assets
        drawStationArt(c, scene, kind, RectF(r.right - dp(scene, 52f), r.top + dp(scene, 3f), r.right - dp(scene, 8f), r.top + dp(scene, 45f)))

        when (step) {
            0 -> {
                textP.textSize = dp(scene, 16f)
                textP.color = 0xFF4A3728.toInt()
                val title = "${kind.emoji} ${kind.label} — 무엇을 구울까요?"
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.top + dp(scene, 24f), textP)
                textP.textSize = dp(scene, 9.5f)
                textP.color = kind.tint
                val descW = minOf(textP.measureText(kind.desc), r.width() - dp(scene, 130f))
                drawFitText(c, scene, kind.desc, r.centerX() - descW / 2, r.top + dp(scene, 38f), r.width() - dp(scene, 130f), 9.5f)

                // 3열 × 2행 메뉴 카드
                menuRects.clear()
                val cols = 3
                val gapX = dp(scene, 10f)
                val gapY = dp(scene, 8f)
                val top = r.top + dp(scene, 48f)
                val bottomArea = r.bottom - dp(scene, 50f)
                val rows = (menu.size + cols - 1) / cols
                val cardW = (r.width() - dp(scene, 14f) * 2 - gapX * (cols - 1)) / cols
                val cardH = ((bottomArea - top) - gapY * (rows - 1)) / rows
                for ((i, p) in menu.withIndex()) {
                    val col = i % cols
                    val row = i / cols
                    val cr = RectF(
                        r.left + dp(scene, 14f) + col * (cardW + gapX), top + row * (cardH + gapY),
                        r.left + dp(scene, 14f) + col * (cardW + gapX) + cardW, top + row * (cardH + gapY) + cardH
                    )
                    UiKit.card(c, g, cr, 10f, false, kind.tint, 2f)

                    // 아이콘 (왼쪽) + 이름/효과/난이도 (오른쪽)
                    val isz = minOf(dp(scene, 36f), cardH - dp(scene, 14f))
                    drawPizzaArt(c, scene, p.id, cr.left + dp(scene, 8f) + isz / 2f, cr.centerY(), isz)
                    val tx = cr.left + dp(scene, 8f) + isz + dp(scene, 6f)
                    val textW = cr.right - dp(scene, 6f) - tx
                    // 보유 수 배지 (오른쪽 위)
                    val have = g.state.pizzaCountOf(p.id)
                    var badgeW = 0f
                    if (have > 0) {
                        val badge = "×$have"
                        textP.textSize = dp(scene, 9f)
                        badgeW = textP.measureText(badge) + dp(scene, 10f)
                        UiKit.badge(c, g, RectF(cr.right - dp(scene, 6f) - badgeW, cr.top + dp(scene, 5f), cr.right - dp(scene, 6f), cr.top + dp(scene, 19f)),
                            badge, 0xFFF2B63C.toInt(), 0xFF4A2E12.toInt(), 9f)
                        badgeW += dp(scene, 4f)
                    }
                    textP.color = 0xFF4A3728.toInt()
                    drawFitText(c, scene, "${p.emoji} ${p.name}", tx, cr.top + dp(scene, 17f), textW - badgeW, 12f)
                    textP.color = 0xFF8A7360.toInt()
                    drawFitText(c, scene, "배고픔 ${if (p.hungerBonus >= 0) "+" else ""}${p.hungerBonus} · 행운 ${if (p.luckBonus >= 0) "+" else ""}${p.luckBonus}", tx, cr.top + dp(scene, 31f), textW, 9.5f)
                    // 난이도 — 라벨 + 컬러 도트
                    textP.textSize = dp(scene, 9.5f)
                    val dLabel = "구우기 "
                    textP.color = 0xFF8A7360.toInt()
                    c.drawText(dLabel, tx, cr.top + dp(scene, 44f), textP)
                    textP.color = 0xFFE8830C.toInt()
                    c.drawText(p.difficultyDots(), tx + textP.measureText(dLabel), cr.top + dp(scene, 44f), textP)
                    menuRects.add(cr to p.id)
                }

                cancelRect = RectF(r.centerX() - dp(scene, 60f), r.bottom - dp(scene, 40f), r.centerX() + dp(scene, 60f), r.bottom - dp(scene, 12f))
                drawButton(c, scene, cancelRect, "그만두기", 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 12f)
                textP.textSize = dp(scene, 9.5f)
                textP.color = 0xFF8A7360.toInt()
                val bag = "가방 ${g.state.pizzaCount}/${g.state.pizzaCapEff()}"
                c.drawText(bag, r.right - dp(scene, 14f) - textP.measureText(bag), r.bottom - dp(scene, 22f), textP)
            }
            1 -> {
                val p = def
                textP.textSize = dp(scene, 16f)
                textP.color = 0xFF4A3728.toInt()
                val title = "${p.emoji} ${p.fullName} 굽기"
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.top + dp(scene, 26f), textP)
                textP.textSize = dp(scene, 9.5f)
                textP.color = kind.tint
                val sub = if (kind == PizzaKind.OVEN) "🔥 장작불 400도 — 순식간에 익어요! 커서가 빨라요" else "🍕 가정용 오븐 — 느긋하게 익어요"
                c.drawText(sub, r.centerX() - textP.measureText(sub) / 2, r.top + dp(scene, 41f), textP)

                // 게이지 — 글로스 + 걸작존 글로우 + 프리미엄 커서
                val gx = r.left + dp(scene, 26f)
                val gy = r.centerY() - dp(scene, 2f)
                val gw = r.width() - dp(scene, 52f)
                val gh = dp(scene, 28f)
                val half = p.perfectW / 2f
                val goodW = kind.goodW
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
                textP.textSize = dp(scene, 10.5f)
                textP.color = 0xFF8A7360.toInt()
                c.drawText("살짝 탐", gx, gy + gh + dp(scene, 18f), textP)
                val rightLbl = "살짝 탐"
                c.drawText(rightLbl, gx + gw - textP.measureText(rightLbl), gy + gh + dp(scene, 18f), textP)
                val midTxt = "걸작!"
                textP.textSize = dp(scene, 10.5f)
                val midW = textP.measureText(midTxt) + dp(scene, 16f)
                UiKit.badge(
                    c, g, RectF(gx + gw / 2f - midW / 2f, gy - dp(scene, 38f), gx + gw / 2f + midW / 2f, gy - dp(scene, 20f)),
                    midTxt, 0xFF6FBA6B.toInt(), 0xFFFFF8E8.toInt(), 10.5f
                )

                val hint = "초록 칸에서 멈춰보세요! (아무 곳이나 탭)"
                textP.textSize = dp(scene, 12f)
                val hintW = textP.measureText(hint) + dp(scene, 24f)
                UiKit.badge(
                    c, g, RectF(r.centerX() - hintW / 2f, r.bottom - dp(scene, 34f), r.centerX() + hintW / 2f, r.bottom - dp(scene, 12f)),
                    hint, 0xFF6B4F35.toInt(), 0xFFF8EFDC.toInt(), 12f
                )
            }
            else -> {
                val q = PizzaQ.of(resultQ)
                val p = def
                val perfect = resultQ == 2
                val title = if (lostPizza) "${p.emoji} ${q.label}… 그런데 두 손이 가득!" else "${p.emoji} ${p.name} ${q.label} 완성!"
                textP.textSize = dp(scene, 16f)
                textP.color = if (perfect) 0xFFB5651D.toInt() else 0xFF4A3728.toInt()
                c.drawText(title, r.centerX() - textP.measureText(title) / 2, r.centerY() - dp(scene, 52f), textP)
                // 품질 별점 + 계열
                textP.textSize = dp(scene, 14f)
                textP.color = 0xFFE8A33C.toInt()
                val starTxt = "★".repeat(q.stars) + "☆".repeat(3 - q.stars)
                val starW = textP.measureText(starTxt)
                textP.textSize = dp(scene, 10f)
                val kl = "  ${kind.emoji} ${kind.label}"
                val klW = textP.measureText(kl)
                val sx0 = r.centerX() - (starW + klW) / 2f
                textP.textSize = dp(scene, 14f)
                c.drawText(starTxt, sx0, r.centerY() - dp(scene, 32f), textP)
                textP.textSize = dp(scene, 10f)
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

                val info = if (lostPizza) "피자 가방이 가득해서 못 챙겼어요… (최대 ${g.state.pizzaCapEff()}개)"
                else "먹으면 배고픔 +${q.hunger + p.hungerBonus} · 행운 +${q.luck + p.luckBonus}"
                textP.textSize = dp(scene, 12f)
                val infoW = (textP.measureText(info) + dp(scene, 24f)).coerceAtMost(r.width() - dp(scene, 24f))
                UiKit.badge(
                    c, g, RectF(r.centerX() - infoW / 2f, r.bottom - dp(scene, 40f), r.centerX() + infoW / 2f, r.bottom - dp(scene, 18f)),
                    info, 0xFF6B4F35.toInt(), 0xFFF8EFDC.toInt(), 12f
                )
                textP.textSize = dp(scene, 10.5f)
                textP.color = 0xFF8A7360.toInt()
                val hint2 = "탭해서 닫기"
                c.drawText(hint2, r.centerX() - textP.measureText(hint2) / 2, r.bottom - dp(scene, 5f), textP)
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
    private val notes: List<String> = emptyList()
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

        val inset = dp(scene, 11f)
        val maxW = minOf(w * 0.62f, dp(scene, 340f))
        val maxH = h * 0.76f
        val cardW = minOf(maxW, maxH / 1.26f)
        val cardH = cardW * 1.26f
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
        val photoH = minOf(photoW * 0.74f, cardH - inset - dp(scene, 96f))
        val photo = RectF(card.left + inset, card.top + inset, card.left + inset + photoW, card.top + inset + photoH)
        drawPhoto(c, photo)
        // EXIF 스트립 — 어떤 설정으로 찍혔는지
        if (exif.isNotEmpty()) {
            val stripH = dp(scene, 13f)
            fillP.color = Color.argb(160, 20, 18, 26)
            c.drawRect(photo.left, photo.bottom - stripH, photo.right, photo.bottom, fillP)
            textP.textSize = dp(scene, 8.5f)
            textP.color = 0xFFF2E3C2.toInt()
            c.drawText(exif, photo.left + dp(scene, 5f), photo.bottom - dp(scene, 4f), textP)
        }
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
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFF9A8570.toInt()
        var camTxt = cameraTxt
        val camMaxW = cardW - dp(scene, 130f)
        if (camTxt.isNotBlank() && textP.measureText(camTxt) > camMaxW && camMaxW > dp(scene, 40f)) {
            while (camTxt.length > 1 && textP.measureText("$camTxt…") > camMaxW) camTxt = camTxt.dropLast(1)
            camTxt = "$camTxt…"
        }
        val info = buildString {
            if (camTxt.isNotBlank()) append("$camTxt · ")
            if (distTiles > 0f) {
                append(String.format("%.1f", distTiles))
                if (reachTiles > 0f) append(String.format("/%.1f", reachTiles))
                append("칸 · ")
            }
            if (timeTxt.isNotBlank()) append("$timeTxt · ")
            append("촬영 ${count}회")
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
            textP.textSize = dp(scene, 11f)
            textP.color = 0xFFFFF3E2.toInt()
            val st = "NEW 첫 발견"
            c.drawText(st, sCx - textP.measureText(st) / 2, sCy - (textP.descent() + textP.ascent()) / 2f, textP)
            c.restore()
        }

        c.restore()

        // 의뢰 보수 & 경험치
        var badgeY = cy + cardH / 2f + dp(scene, 24f)
        if (questLine != null) {
            textP.textSize = dp(scene, 12.5f)
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
            val expTxt = if (levelsGained > 0) "경험치 +  · 레벨 업! ✨" else "경험치 +"
            textP.textSize = dp(scene, 12f)
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
            textP.textSize = dp(scene, 10f)
            var ny = badgeY + (if (expGain > 0) dp(scene, 30f) else dp(scene, 4f))
            for (n in notes.take(3)) {
                val bad = n.contains("노이즈") || n.contains("흔들") || n.contains("물방울")
                textP.color = Color.argb(235, if (bad) 246 else 176, if (bad) 176 else 232, if (bad) 156 else 176)
                val nt = (if (bad) "· " else "· ") + n
                c.drawText(nt, cx - textP.measureText(nt) / 2, ny, textP)
                ny += dp(scene, 13f)
            }
        }

        // 안내 (깜빡임)
        val blink = 0.55f + 0.45f * sin(t * 3.4f).coerceIn(0f, 1f)
        textP.textSize = dp(scene, 11.5f)
        textP.color = Color.argb((215 * blink).toInt().coerceIn(0, 255), 240, 236, 226)
        val hint = if (levelsGained > 0) "화면을 탭해 계속" else "화면을 탭해 탐조를 계속해요"
        c.drawText(hint, cx - textP.measureText(hint) / 2, h - dp(scene, 18f), textP)
    }

    /** 인화지 속 풍경 + 새 */
    private fun drawPhoto(c: Canvas, r: RectF) {
        val a = scene.game.assets
        val hab = def.habitats.firstOrNull() ?: "field"

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
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
    override val coversWorld: Boolean get() = true


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
        // 레벨업 축하 — 폴짝폴짝 뛰며 만세!
        val cheer = a.cheerFrames(s.gender, s.gearTier())
        val bmp = cheer[(g.time / 0.1f).toInt() % cheer.size]
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
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
        textP.textSize = dp(scene, 14f)
        val titleTxt = "🗺 대한민국 탐조 지도"
        val titleW = textP.measureText(titleTxt)
        textP.color = 0xFFF8EFDC.toInt()
        c.drawText(titleTxt, dp(scene, 22f), dp(scene, 29f), textP)
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFFE9C46A.toInt()
        var sub = if (w < dp(scene, 620f)) "방문 ${s.visited.size}/${Regions.ALL.size}"
        else "방문 ${s.visited.size}/${Regions.ALL.size} · 두 손가락 확대 · 드래그 이동 · 지역 탭하면 정보"
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

                textP.textSize = dp(scene, 10f)
                textP.color = 0xFF4A2E12.toInt()
                val numTxt = exitInfo.number.toString()
                c.drawText(numTxt, mx - textP.measureText(numTxt) / 2, my - (textP.descent() + textP.ascent()) / 2, textP)

                // 방향 화살표 작게
                textP.textSize = dp(scene, 9f)
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
        // 현재 지역 출구 번호 — 지도에서 지하철 출입구처럼 표시
        val currentExits = Regions.exitNumbered(s.region)
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

            // 메인 퀘스트 자동 진행 — 추천 지역: 금색 별 + 펄스 링 (현재 위치와 겹치면 생략)
            if (mainAdv != null && !mainAdv.alreadyThere && reg.id == mainAdv.regionId && !isCurrent) {
                val pulse = (g.time * 1.4f) % 1f
                strokeP.color = Color.argb(((1f - pulse) * 200f).toInt(), 242, 182, 60)
                strokeP.strokeWidth = dp(scene, 1.6f)
                c.drawCircle(x, y, r + dp(scene, 3f) + pulse * dp(scene, 7f), strokeP)
                textP.textSize = dp(scene, 15f)
                textP.color = 0xFF8A5A12.toInt()
                val star = "★"
                c.drawText(star, x - textP.measureText(star) / 2f, y - r - dp(scene, 6f), textP)
            }

            // 현재 위치에서는 각 방향 출구 번호를 주변에 표시 — 지하철 출입구처럼
            if (isCurrent) {
                for (exit in currentExits) {
                    val dirX = when (exit.dir) {
                        Dir.N -> 0f
                        Dir.S -> 0f
                        Dir.E -> 1f
                        Dir.W -> -1f
                    }
                    val dirY = when (exit.dir) {
                        Dir.N -> -1f
                        Dir.S -> 1f
                        Dir.E -> 0f
                        Dir.W -> 0f
                    }
                    val bx = x + dirX * dp(scene, 28f)
                    val by = y + dirY * dp(scene, 28f)

                    // 번호 원
                    fillP.color = Color.argb(40, 20, 14, 10)
                    c.drawCircle(bx, by + dp(scene, 1f), dp(scene, 9f), fillP)
                    fillP.color = 0xFFF2B63C.toInt()
                    c.drawCircle(bx, by, dp(scene, 8f), fillP)
                    strokeP.color = 0xFF4A2E12.toInt()
                    strokeP.strokeWidth = dp(scene, 1.2f)
                    c.drawCircle(bx, by, dp(scene, 8f), strokeP)

                    textP.textSize = dp(scene, 9f)
                    textP.color = 0xFF4A2E12.toInt()
                    val numTxt = exit.number.toString()
                    c.drawText(numTxt, bx - textP.measureText(numTxt) / 2, by - (textP.descent() + textP.ascent()) / 2, textP)
                }
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
        val legendR = RectF(x0 - dp(scene, 6f), y - dp(scene, 12f), x0 + lw, y - dp(scene, 12f) + lh)
        fillP.color = Color.argb(55, 20, 16, 30)
        c.drawRoundRect(RectF(legendR.left, legendR.top + dp(scene, 2f), legendR.right, legendR.bottom + dp(scene, 2f)), dp(scene, 8f), dp(scene, 8f), fillP)
        fillP.color = Color.argb(225, 255, 252, 240)
        c.drawRoundRect(legendR, dp(scene, 8f), dp(scene, 8f), fillP)
        strokeP.color = Color.argb(160, 107, 79, 53)
        strokeP.strokeWidth = dp(scene, 1.4f)
        c.drawRoundRect(legendR, dp(scene, 8f), dp(scene, 8f), strokeP)
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
        textP.textSize = dp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        val title = "${reg.emoji} ${reg.name}"
        c.drawText(title, x, y, textP)
        val tw = textP.measureText(title)
        textP.textSize = dp(scene, 9f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(reg.english, x + tw + dp(scene, 6f), y, textP)
        val badgeTxt = when {
            reg.id == s.region -> "📍 현재 위치"
            reg.id == s.homeRegion -> "🏠 우리 집"
            reg.id in s.visited -> "방문함 ✓"
            else -> "미방문"
        }
        textP.textSize = dp(scene, 9.5f)
        val badgeW = textP.measureText(badgeTxt) + dp(scene, 12f)
        val badgeBg = when {
            reg.id == s.region -> 0xFFE2574C.toInt()
            reg.id == s.homeRegion -> 0xFF6FBA6B.toInt()
            reg.id in s.visited -> 0xFF3F6FB0.toInt()
            else -> 0xFF9AA0A8.toInt()
        }
        UiKit.badge(
            c, g, RectF(r.right - dp(scene, 12f) - badgeW, y - dp(scene, 14f), r.right - dp(scene, 12f), y + dp(scene, 4f)),
            badgeTxt, badgeBg, 0xFFFFF8E8.toInt(), 9.5f
        )

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

        if (isMainPick) {
            y += dp(scene, 14f)
            textP.color = 0xFFB5651D.toInt()
            var rec = "📌 메인 퀘스트 추천: ${mainAdv!!.reason}"
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
            val ex = "🚲 연결: - (막다른 길)"
            for (ln in g.hud.wrapText(ex, textP, r.width() - dp(scene, 24f)).take(2)) {
                c.drawText(ln, x, y, textP)
                y += dp(scene, 12f)
            }
        } else {
            // 현재 위치면 강조
            val isCurrent = reg.id == s.region
            textP.color = if (isCurrent) 0xFF4A2E12.toInt() else 0xFF8A7360.toInt()
            textP.textSize = dp(scene, 9.5f)
            val header = if (isCurrent) "🚲 터널 번호 (지도 ↔ 월드 동일, 맵별 다름):" else "🚲 연결:"
            c.drawText(header, x, y, textP)
            y += dp(scene, 12f)

            for (exit in numbered) {
                val target = Regions.byId[exit.targetId]
                val targetName = target?.name ?: exit.targetId
                val dirArrow = Regions.dirArrow(exit.dir)
                val dirLabel = Regions.dirLabel(exit.dir)
                val line = "${exit.number}. $dirArrow $dirLabel → $targetName"

                // 번호 원
                fillP.color = 0xFFF2B63C.toInt()
                c.drawCircle(x + dp(scene, 6f), y - dp(scene, 3f), dp(scene, 6f), fillP)
                strokeP.color = 0xFF4A2E12.toInt()
                strokeP.strokeWidth = dp(scene, 1f)
                c.drawCircle(x + dp(scene, 6f), y - dp(scene, 3f), dp(scene, 6f), strokeP)
                textP.textSize = dp(scene, 8f)
                textP.color = 0xFF4A2E12.toInt()
                val nt = exit.number.toString()
                c.drawText(nt, x + dp(scene, 6f) - textP.measureText(nt) / 2, y, textP)

                // 텍스트
                textP.textSize = dp(scene, 9.5f)
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
        textP.textSize = dp(scene, 10.5f)
        textP.color = 0xFFF8EFDC.toInt()
        c.drawText("📍 ${cur.name} 현재 위치 출구 (번호 = 월드 터널 번호)", r.left + dp(scene, 10f), y, textP)
        y += dp(scene, 12f)
        for (exit in numbered) {
            val target = Regions.byId[exit.targetId] ?: continue
            val line = "${exit.number}. ${Regions.dirArrow(exit.dir)} ${Regions.dirLabel(exit.dir)} → ${target.name} ${target.emoji}"
            textP.textSize = dp(scene, 9.5f)
            textP.color = 0xFFE9C46A.toInt()
            c.drawText(line, r.left + dp(scene, 10f), y, textP)
            y += dp(scene, 12f)
        }
    }
}

// ---------------------------------------------------------------------------
// 카메라 상점 (사진용품점 진열대) — 컴팩트 / 바디 / 렌즈 / 액세서리
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
        textP.textSize = dp(scene, 22f)
        textP.color = 0xFF6B4F35.toInt()
        c.drawText(gear.kind.emoji, r.left + dp(scene, 18f), r.centerY() + dp(scene, 8f), textP)
    }

    val tx = r.left + dp(scene, 62f)
    // 이름 + 등급 칩
    textP.textSize = dp(scene, 12.5f)
    textP.color = 0xFF4A3728.toInt()
    c.drawText(gear.fullName, tx, r.top + dp(scene, 16f), textP)
    val gLabel = gear.grade.label
    textP.textSize = dp(scene, 8.5f)
    val gw = textP.measureText(gLabel) + dp(scene, 10f)
    val gr = RectF(r.right - gw - dp(scene, 96f), r.top + dp(scene, 5f), r.right - dp(scene, 96f), r.top + dp(scene, 19f))
    fillP.color = gear.grade.color
    c.drawRoundRect(gr, dp(scene, 7f), dp(scene, 7f), fillP)
    textP.color = 0xFFFFF8E8.toInt()
    c.drawText(gLabel, gr.centerX() - textP.measureText(gLabel) / 2, gr.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

    textP.textSize = dp(scene, 9.5f)
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

class CameraShopOverlay(scene: Scene, startTab: Int = 0, startPage: Int = 0) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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

    private fun items(): List<CamGear> = when (tab) {
        Tab.COMPACT -> CameraGear.COMPACTS.sortedBy { it.price }
        Tab.BODY -> CameraGear.BODIES.sortedBy { it.price }
        Tab.LENS -> CameraGear.LENSES.sortedBy { it.price }
        Tab.ACC -> (CameraGear.TELECONVS + CameraGear.ACCESSORIES).sortedBy { it.price }
    }

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
            }
            is CamLens -> {
                val cur = CameraGear.body(s.bodyId)
                if (cur != null) {
                    val problem = CameraGear.mountProblem(cur, gear, s.hasAdapter())
                    if (problem == null) {
                        val eq = (gear.teleMm * cur.sensor.crop).toInt()
                        body.append("지금 바디(${cur.name})에 물리면 환산 ${eq}mm\n")
                    } else {
                        body.append("⚠ $problem\n")
                    }
                }
                body.append(if (gear.macro) "접사 렌즈 — 아주 가까이서 찍을 수 있어요\n" else "")
            }
            is TeleConv -> body.append("망원 단렌즈·고급 줌에만 물릴 수 있어요\n")
            is CamAccessory -> body.append("효과: ${gear.effect}\n")
        }
        body.append("가격 ${won(gear.price)} · 보유 ${won(s.money)}")
        scene.openOverlay(
            DialogOverlay(
                scene, gear.fullName, body.toString(),
                listOf(
                    DialogOverlay.Choice(if (gear.id in s.ownedGear) "보유중" else "구매 ${won(gear.price)}") {
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
        if (s.money < gear.price) {
            g.toast("돈이 부족해요… (${won(gear.price)})")
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        g.sfx(Audio.Sfx.BUY)
        s.money -= gear.price
        s.ownedGear.add(gear.id)
        // 산 장비는 가능하면 바로 장착해 준다
        when (gear) {
            is CompactCam -> {
                s.compactId = gear.id
                s.useIlc = false
                g.toast("${gear.name} 구매! 바로 목에 걸었어요 📷")
            }
            is CamBody -> {
                s.bodyId = gear.id
                val lens = CameraGear.lens(s.lensId)
                s.useIlc = lens != null && CameraGear.canMount(gear, lens, s.hasAdapter())
                g.toast(
                    if (s.useIlc) "${gear.name} 구매! 렌즈를 물려 장착했어요 📷"
                    else "${gear.name} 구매! 이제 마운트가 맞는 렌즈가 필요해요 🔭"
                )
            }
            is CamLens -> {
                val body = CameraGear.body(s.bodyId)
                if (body != null && CameraGear.canMount(body, gear, s.hasAdapter())) {
                    s.lensId = gear.id
                    if (!gear.tcOk) s.tcId = null
                    s.useIlc = true
                    g.toast("${gear.name} 구매! ${body.name}에 물렸어요 🔭")
                } else {
                    s.lensId = s.lensId ?: gear.id
                    g.toast("${gear.name} 구매! 맞는 바디에 물려 보세요 🔩")
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
            is CamAccessory -> g.toast("${gear.name} 구매! 효과가 바로 적용돼요 🎒")
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
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = dp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("📷 사진용품점 진열대", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 24f), textP)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val rig = s.rig()
        c.drawText(
            "보유 ${won(s.money)} · 지금 장비: ${rig.title} (환산 ${rig.teleMm}mm)",
            panelR.left + dp(scene, 14f), panelR.top + dp(scene, 38f), textP
        )

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
            textP.textSize = dp(scene, 11.5f)
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

            val br = RectF(r.right - dp(scene, 88f), r.centerY() - dp(scene, 14f), r.right - dp(scene, 8f), r.centerY() + dp(scene, 14f))
            if (owned) {
                drawButton(c, scene, br, "보유중", Color.argb(90, 200, 190, 175), Color.argb(150, 74, 55, 40), 11f)
            } else if (s.money >= gear.price) {
                drawButton(c, scene, br, won(gear.price), 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
                buyRects.add(br to gear)
            } else {
                drawButton(c, scene, br, won(gear.price), Color.argb(110, 200, 190, 175), Color.argb(170, 74, 55, 40), 10.5f)
            }
            infoRects.add(RectF(r.left, r.top, r.right - dp(scene, 92f), r.bottom) to gear)
            ty += rowH
        }

        // 페이지 버튼
        val by = panelR.bottom - dp(scene, 32f)
        prevRect = RectF(panelR.left + dp(scene, 14f), by, panelR.left + dp(scene, 74f), by + dp(scene, 24f))
        nextRect = RectF(panelR.right - dp(scene, 74f), by, panelR.right - dp(scene, 14f), by + dp(scene, 24f))
        drawButton(c, scene, prevRect, "◀ 이전", if (page > 0) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
        drawButton(c, scene, nextRect, "다음 ▶", if (page < maxPage) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val pg = "${
page + 1} / ${maxPage + 1}  ·  항목을 누르면 자세한 성능"
        c.drawText(pg, panelR.centerX() - textP.measureText(pg) / 2, by + dp(scene, 16f), textP)
    }
}

// ---------------------------------------------------------------------------
// 장비 가방 — 컴팩트/바디/렌즈/TC 조립 & 성능 확인
// ---------------------------------------------------------------------------

class GearBagOverlay(scene: Scene) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
        textP.textSize = dp(scene, 9.5f)
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
        textP.textSize = dp(scene, 8.5f)
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
        val ph = minOf(h * 0.92f, dp(scene, 420f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)
        btnRects.clear()

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 6f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 32f))
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        val rig = s.rig()
        textP.textSize = dp(scene, 14f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🎒 장비 가방", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 24f), textP)

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
        textP.textSize = dp(scene, 12.5f)
        textP.color = 0xFF4A3728.toInt()
        val tx = cardR.left + dp(scene, 108f)
        c.drawText(rig.title, tx, cardR.top + dp(scene, 18f), textP)
        textP.textSize = dp(scene, 9.5f)
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
            g.toast("일체형 컴팩트를 꺼냈어요 📷")
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
                g.toast("바디 + 렌즈를 조립했어요 🔭")
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
            textP.textSize = dp(scene, 10.5f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(label, r.left + dp(scene, 10f), r.centerY() + dp(scene, 4f), textP)
            textP.textSize = dp(scene, 11.5f)
            textP.color = if (enabled) 0xFF4A3728.toInt() else 0xFF9A8B7A.toInt()
            c.drawText(value, r.left + dp(scene, 74f), r.centerY() + dp(scene, 4f), textP)
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
            l.name + if (bad) "  ⚠ 마운트 불일치" else ""
        } ?: "없음"
        slot("렌즈", lensName, GearKind.LENS, s.useIlc)
        slot("텔레컨버터", CameraGear.tc(s.tcId)?.name ?: "없음", GearKind.TELECONV, s.useIlc)

        // 액세서리
        y += dp(scene, 2f)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF6B5A48.toInt()
        val accs = CameraGear.ACCESSORIES.filter {
 it.id in s.ownedGear }
        val accTxt = if (accs.isEmpty()) "보유 액세서리: 없음 (상점 액세서리 탭)"
            else "보유 액세서리: " + accs.joinToString(", ") { it.name }
        for (ln in g.hud.wrapText(accTxt, textP, panelR.width() - dp(scene, 28f)).take(2)) {
            c.drawText(ln, panelR.left + dp(scene, 14f), y + dp(scene, 10f), textP)
            y += dp(scene, 12f)
        }
    }
}

// ---------------------------------------------------------------------------
// 장비 선택 (가방에서 슬롯 교체)
// ---------------------------------------------------------------------------

class GearPickOverlay(scene: Scene, private val kind: GearKind) : Overlay(scene) {
    /** 전체 화면 패널 — 뒤 월드 갱신은 20Hz 로 낮춰도 된다 */
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
                g.toast("${CameraGear.compact(id)?.name}을(를) 꺼냈어요 📷")
            }
            GearKind.BODY -> {
                s.bodyId = id
                val body = CameraGear.body(id)
                val lens = CameraGear.lens(s.lensId)
                if (body != null && lens != null && !CameraGear.canMount(body, lens, s.hasAdapter())) {
                    g.toast("바디를 바꿨어요. 이 렌즈와는 마운트가 달라요 ⚠")
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
                    g.toast("${lens?.name}을(를) 물렸어요 🔭")
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

        val pw = minOf(w * 0.92f, dp(scene, 480f))
        val ph = minOf(h * 0.9f, dp(scene, 400f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        panel(c, panelR, scene)

        closeRect = RectF(panelR.right - dp(scene, 34f), panelR.top + dp(scene, 6f), panelR.right - dp(scene, 8f), panelR.top + dp(scene, 32f))
        textP.textSize = dp(scene, 16f)
        textP.color = 0xFFB5651D.toInt()
        c.drawText("✕", closeRect.centerX() - textP.measureText("✕") / 2, closeRect.centerY() - (textP.descent() + textP.ascent()) / 2, textP)

        textP.textSize = dp(scene, 13.5f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("${kind.emoji} ${kind.label} 선택", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 24f), textP)
        textP.textSize = dp(scene, 9.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("가진 장비만 보여요 · 상점에서 더 살 수 있어요", panelR.left + dp(scene, 14f), panelR.top + dp(scene, 38f), textP)

        pickRects.clear()
        val list = ArrayList<CamGear?>()
        list.addAll(owned())
        if (kind == GearKind.TELECONV) list.add(null)   // "빼기" 항목

        val maxPage = ((list.size - 1) / perPage).coerceAtLeast(0)
        if (page > maxPage) page = maxPage
        val from = page * perPage
        val shown = list.subList(from, minOf(from + perPage, list.size))

        var ty = panelR.top + dp(scene, 46f)
        val rowH = dp(scene, 50f)
        for (gear in shown) {
            val r = RectF(panelR.left + dp(scene, 12f), ty, panelR.right - dp(scene, 12f), ty + rowH - dp(scene, 5f))
            if (gear == null) {
                drawCard(c, scene, r)
                textP.textSize = dp(scene, 12f)
                textP.color = 0xFF4A3728.toInt()
                c.drawText("텔레컨버터 빼기", r.left + dp(scene, 14f), r.centerY() + dp(scene, 4f), textP)
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
                    textP.textSize = dp(scene, 9f)
                    textP.color = 0xFF6FBA6B.toInt()
                    c.drawText("장착중", r.right - dp(scene, 44f), r.centerY() + dp(scene, 4f), textP)
                }
                pickRects.add(r to gear.id)
            }
            ty += rowH
        }

        if (list.isEmpty()) {
            textP.textSize = dp(scene, 11.5f)
            textP.color = 0xFF8A7360.toInt()
            val msg = "가진 ${kind.label}이(가) 없어요. 사진용품점에서 먼저 사 보세요!"
            c.drawText(msg, panelR.centerX() - textP.measureText(msg) / 2, panelR.centerY(), textP)
        }

        val by = panelR.bottom - dp(scene, 32f)
        prevRect = RectF(panelR.left + dp(scene, 14f), by, panelR.left + dp(scene, 74f), by + dp(scene, 24f))
        nextRect = RectF(panelR.right - dp(scene, 74f), by, panelR.right - dp(scene, 14f), by + dp(scene, 24f))
        drawButton(c, scene, prevRect, "◀ 이전", if (page > 0) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
        drawButton(c, scene, nextRect, "다음 ▶", if (page < maxPage) 0xFFF2E3C2.toInt() else Color.argb(70, 200, 190, 175), 0xFF6B4F35.toInt(), 10.5f)
        textP.textSize = dp(scene, 10f)
        textP.color = 0xFF8A7360.toInt()
        val pg = "${page + 1} / ${maxPage + 1}"
        c.drawText(pg, panelR.centerX() - textP.measureText(pg) / 2, by + dp(scene, 16f), textP)
    }
}

