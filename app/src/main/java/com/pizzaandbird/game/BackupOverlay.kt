package com.pizzaandbird.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * [P05] 백업 코드 화면 — 클라우드 없이 "텍스트 한 장"으로 세이브를 옮긴다.
 *
 * 픽셀 UI에는 글자를 직접 치는 입력란이 어울리지 않으므로 **클립보드 전용**으로 설계했다.
 *  - [Mode.CREATE] : 코드를 만들어 보여주고(스크롤) 「클립보드에 복사」/「앱으로 보내기」
 *  - [Mode.RESTORE]: 클립보드를 읽어 검증 → 확인 → 복원 → 그 자리에서 즉시 게임으로
 *
 * 코드 생성·검증은 백그라운드 스레드에서 돌린다(세이브가 크면 압축에 수십 ms가 걸린다).
 * 화면은 [Input.rawMode]로 raw 터치를 받아 코드 영역을 손가락으로 드래그해 스크롤한다.
 */
class BackupOverlay(
    scene: Scene,
    private val mode: Mode = Mode.CREATE,
    /** 닫힐 때 실행 — 설정에서 왔으니 여행 가방(메뉴)을 다시 열어 준다 */
    private val onClosed: () -> Unit = {}
) : Overlay(scene) {

    enum class Mode { CREATE, RESTORE }

    private companion object {
        const val LABEL = "피자와 새 백업 코드"
        /** 복원 완료 메시지를 보여주는 시간(초) — 이후 자동으로 게임에 돌아간다 */
        const val DONE_HOLD = 1.5f
    }

    private val g: Game get() = scene.game

    private fun dp(v: Float): Float = v * g.density

    // ---- 레이아웃 캐시 (매 프레임 계산) ----
    private var panelR = RectF()
    private var drawnShift = 0f
    private var closeRect = RectF()
    private var codeBox = RectF()
    private val btnRects = ArrayList<Pair<RectF, () -> Unit>>()

    // ---- 코드 표시용 등폭 페인트 (게임 한글 폰트가 아닌 코드 전용) ----
    private val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = 0xFFE8DCC4.toInt()
    }
    private val codeBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = 0xFFF6E9C8.toInt()
    }

    // ---- 상태 (백그라운드 스레드가 쓰므로 volatile) ----
    @Volatile
    private var busy = false
    private var busyText = "…"
    private var worker: Thread? = null

    /** 내보내기 결과 */
    private var code: String = ""
    private var codeLines: List<String> = emptyList()
    private var codeWidest = 0f
    private var failMsg: String? = null

    /** 불러오기 단계 */
    @Volatile
    private var step = Step.IDLE
    private enum class Step { IDLE, CONFIRM, ERROR, DONE }

    private var pendingCode = ""
    private var info: Backup.Info? = null
    private var errorMsg = ""
    private var doneMsg = ""
    private var doneT = 0f

    // 스크롤 (코드 영역)
    private var scroll = 0f
    private var scrollMax = 0f
    private var flingV = 0f

    // raw 터치 추적
    private val downY = HashMap<Int, Float>()
    private val lastY = HashMap<Int, Float>()
    private val dragged = HashSet<Int>()
    private var dragId = -1
    /** 지금 눌린 자리 (버튼 눌림 표시용) — 드래그가 되면 null */
    private var pressPoint: android.graphics.PointF? = null

    init {
        // 코드 영역을 손가락으로 끌어서 스크롤하려면 raw 터치가 필요하다
        g.input.rawMode = true
        if (mode == Mode.CREATE) startExport()
    }

    // ------------------------------------------------------------------
    // 작업 (백그라운드)
    // ------------------------------------------------------------------

    private fun startExport() {
        busy = true
        busyText = "수첩을 베껴 적는 중… 📜"
        val ctx = g.context
        val snapshot = g.state
        worker = Thread {
            val r = Backup.export(ctx, snapshot)
            r.onSuccess { c ->
                code = c
                codeLines = Backup.formatCode(c).split('\n')
                codeWidest = 0f
                failMsg = null
            }.onFailure { e ->
                failMsg = e.message ?: "백업 코드를 만들지 못했어요."
            }
            busy = false
        }
        worker?.isDaemon = true
        worker?.start()
    }

    private fun startInspect(text: String?) {
        if (text == null || Backup.normalize(text).isEmpty()) {
            step = Step.ERROR
            errorMsg = "클립보드가 비어 있어요.\n먼저 「백업 코드 만들기」에서 코드를 복사해 주세요."
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        busy = true
        busyText = "코드를 확인하는 중… 🔎"
        worker = Thread {
            Backup.inspect(text)
                .onSuccess { i ->
                    pendingCode = text
                    info = i
                    step = Step.CONFIRM
                    failMsg = null
                    errorMsg = ""
                }
                .onFailure { e ->
                    step = Step.ERROR
                    errorMsg = e.message ?: "코드를 읽을 수 없어요."
                }
            busy = false
        }
        worker?.isDaemon = true
        worker?.start()
    }

    private fun startApply() {
        val ctx = g.context
        val game = g
        val raw = pendingCode
        busy = true
        busyText = "수첩을 옮겨 적는 중… ✍"
        worker = Thread {
            Backup.import(ctx, raw, game)
                .onSuccess { msg ->
                    doneMsg = msg
                    step = Step.DONE
                    doneT = 0f
                }
                .onFailure { e ->
                    step = Step.ERROR
                    errorMsg = e.message ?: "복원에 실패했어요."
                }
            busy = false
        }
        worker?.isDaemon = true
        worker?.start()
    }

    /** 복원 완료 — 백업에 담긴 위치로 곧바로 돌아간다 (저장이 없으면 타이틀). */
    private fun backToGame() {
        val s = g.state
        finished = true
        g.input.rawMode = false
        g.scene.closeOverlay()
        g.audio.stopAmb()
        g.sfx(Audio.Sfx.SUCCESS, 0.7f)
        g.toast("백업에서 복원했어요 🗄")
        if (s.started) {
            g.fadeTo {
                g.scene = if (s.inHome) HomeScene(g) else WorldScene(g, s.region, SpawnKind.SAVED)
            }
        } else {
            g.fadeTo { g.scene = TitleScene(g) }
        }
    }

    private fun close() {
        finished = true
        g.input.rawMode = false
        worker?.interrupt()
        worker = null
        // 닫히면 왔던 자리(여행 가방 › 설정 탭)로 돌아간다
        onClosed()
    }

    // ------------------------------------------------------------------
    // 클립보드 / 공유
    // ------------------------------------------------------------------

    private fun clipboard(): ClipboardManager? =
        g.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private fun copyCode() {
        if (code.isEmpty()) return
        var ok = false
        try {
            val cm = clipboard()
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(LABEL, code))
                ok = true
            }
        } catch (_: Exception) {
            ok = false
        }
        if (ok) {
            g.sfx(Audio.Sfx.SUCCESS, 0.6f)
            g.haptic()
            g.toast("백업 코드를 클립보드에 복사했어요 📋")
        } else {
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            g.toast("복사하지 못했어요. 대신 「앱으로 보내기」를 써 보세요")
        }
    }

    private fun shareCode() {
        if (code.isEmpty()) return
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, LABEL)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "$LABEL\n만든 날짜: ${todayLabel()} · 버전 ${Backup.APP_VERSION}\n" +
                        "다른 기기의 「설정 › 📥 코드에서 불러오기」에 붙여넣으면 이어서 할 수 있어요.\n\n" + code
                )
            }
            g.context.startActivity(Intent.createChooser(i, "백업 코드 보내기").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            g.sfx(Audio.Sfx.TAP, 0.5f)
        } catch (e: Exception) {
            g.sfx(Audio.Sfx.FAIL, 0.5f)
            g.toast("공유할 앱을 찾지 못했어요 — 클립보드 복사를 써 주세요")
        }
    }

    private fun readClipboard(): String? = try {
        val cm = clipboard() ?: return null
        val clip = cm.primaryClip ?: return null
        val sb = StringBuilder()
        for (i in 0 until clip.itemCount) {
            val t = clip.getItemAt(i)?.coerceToText(g.context)?.toString() ?: continue
            sb.append(t).append('\n')
        }
        sb.toString().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    private fun todayLabel(): String =
        java.text.SimpleDateFormat("yyyy년 M월 d일", java.util.Locale.KOREA).format(java.util.Date())

    // ------------------------------------------------------------------
    // 입력
    // ------------------------------------------------------------------

    override fun handleInput(input: Input) {
        if (input.justB || input.justBack) {
            if (step == Step.DONE) backToGame() else close()
            return
        }

        // raw 터치: 드래그 = 스크롤, 움직이지 않고 떼면 = 탭(버튼)
        val events = ArrayList(input.rawEvents)
        for (ev in events) {
            when (ev.kind) {
                Input.RawEv.DOWN -> {
                    downY[ev.id] = ev.y
                    lastY[ev.id] = ev.y
                    dragged.remove(ev.id)
                    flingV = 0f
                    pressPoint = android.graphics.PointF(ev.x, ev.y)
                }
                Input.RawEv.MOVE -> {
                    val prev = lastY[ev.id] ?: ev.y
                    val dy = ev.y - prev
                    lastY[ev.id] = ev.y
                    val start = downY[ev.id] ?: ev.y
                    if (ev.id !in dragged && abs(ev.y - start) > dp(9f)) {
                        dragged.add(ev.id)
                        dragId = ev.id
                    }
                    if (ev.id in dragged) {
                        pressPoint = null
                        if (scrollMax > 0f) {
                            scroll = (scroll - dy).coerceIn(0f, scrollMax)
                            flingV = -dy * 55f
                        }
                    } else {
                        pressPoint?.set(ev.x, ev.y)
                    }
                }
                Input.RawEv.UP, Input.RawEv.CANCEL -> {
                    val wasDrag = ev.id in dragged
                    downY.remove(ev.id)
                    lastY.remove(ev.id)
                    dragged.remove(ev.id)
                    pressPoint = null
                    if (dragId == ev.id) dragId = -1
                    if (!wasDrag) tapAt(ev.x, ev.y)
                }
            }
        }
    }

    private fun tapAt(x: Float, screenY: Float) {
        val y = screenY - drawnShift
        if (closeRect.contains(x, y)) {
            g.sfx(Audio.Sfx.TAP, 0.5f)
            if (step == Step.DONE) backToGame() else close()
            return
        }
        for ((r, action) in btnRects) {
            if (r.contains(x, y)) {
                g.sfx(Audio.Sfx.TAP, 0.5f)
                g.haptic()
                action()
                return
            }
        }
    }

    override fun update(dt: Float) {
        // 관성 스크롤
        if (dragId == -1 && abs(flingV) > 1f && scrollMax > 0f) {
            scroll = (scroll + flingV * dt).coerceIn(0f, scrollMax)
            flingV *= 0.90f
        } else if (dragId == -1) {
            flingV = 0f
        }
        if (step == Step.DONE && !busy) {
            doneT += dt
            if (doneT >= DONE_HOLD) backToGame()
        }
    }

    // ------------------------------------------------------------------
    // 그리기
    // ------------------------------------------------------------------

    override fun draw(c: Canvas) {
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        UiKit.dim(c, g, 165, bornAt)

        val pw = minOf(w * 0.94f, dp(620f))
        val ph = minOf(h * 0.92f, dp(470f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)

        c.save()
        drawnShift = enterShift()
        c.translate(0f, drawnShift)
        UiKit.panel(c, g, panelR, 14f)
        btnRects.clear()

        drawHeader(c)

        val left = panelR.left + dp(14f)
        val right = panelR.right - dp(14f)
        val top = panelR.top + dp(46f)
        val bottom = panelR.bottom - dp(14f)

        if (mode == Mode.CREATE) drawCreate(c, left, right, top, bottom)
        else drawRestore(c, left, right, top, bottom)

        drawPressFeedback(c)
        c.restore()
    }

    /** raw 모드에서는 [Input.isPressedIn]이 채워지지 않으므로, 눌린 버튼을 직접 어둡게 덮는다. */
    private fun drawPressFeedback(c: Canvas) {
        val p = pressPoint ?: return
        val y = p.y - drawnShift
        val fill = Paint()
        for ((r, _) in btnRects) {
            if (r.contains(p.x, y)) {
                fill.color = Color.argb(46, 40, 26, 12)
                c.drawRoundRect(r, dp(6f), dp(6f), fill)
            }
        }
        if (closeRect.contains(p.x, y)) {
            fill.color = Color.argb(46, 40, 26, 12)
            c.drawCircle(closeRect.centerX(), closeRect.centerY(), dp(11f), fill)
        }
    }

    /** 상단: 이름표 스티커 + 모드 뱃지 + 닫기 단추 */
    private fun drawHeader(c: Canvas) {
        val flapR = RectF(
            panelR.left + dp(6f), panelR.top + dp(6f),
            panelR.right - dp(6f), panelR.top + dp(36f)
        )
        if (mode == Mode.CREATE) {
            UiKit.pixelFillGradient(c, flapR, dp(2f), 0xFFD9A46E.toInt(), 0xFFBF8552.toInt())
        } else {
            UiKit.pixelFillGradient(c, flapR, dp(2f), 0xFFA9C2D6.toInt(), 0xFF7E97AD.toInt())
        }
        UiKit.pixelStroke(c, flapR, dp(2f), UiKit.OUTLINE, dp(1.6f))

        val title = if (mode == Mode.CREATE) "🗄 백업 코드 만들기" else "📥 코드에서 불러오기"
        val tagW = UiKit.nameTagWidth(g, title, 14f)
        val tagR = RectF(
            flapR.left + dp(10f), flapR.centerY() - dp(12f),
            flapR.left + dp(10f) + tagW, flapR.centerY() + dp(12f)
        )
        UiKit.nameTag(c, g, tagR, title, 14f)

        val closeCx = flapR.right - dp(19f)
        val closeCy = flapR.centerY()
        val closeRr = dp(11f)
        closeRect = RectF(
            closeCx - closeRr - dp(7f), closeCy - closeRr - dp(7f),
            closeCx + closeRr + dp(7f), closeCy + closeRr + dp(7f)
        )
        UiKit.circleButton(c, g, closeCx, closeCy, closeRr, "✕", 11f, 0xFFFFF3DC.toInt(), 0xFF8A4A2A.toInt())
    }

    // ---- 내보내기 화면 ----

    private fun drawCreate(c: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        // 안내 카드 — 코드가 아주 길면 안내 문구 자리에 경고가 대신 들어간다(카드 높이는 고정)
        val tooLong = code.isNotEmpty() && code.length > Backup.LONG_CODE_CHARS
        val infoH = dp(if (busy || failMsg != null) 46f else 62f)
        val infoR = RectF(left, top, right, top + infoH)
        UiKit.stitchCard(c, g, infoR, if (tooLong) 0xFFFFF0DC.toInt() else UiKit.PASTEL_SAND)
        var ty = infoR.top + dp(18f)
        UiKit.drawText(
            c, g,
            if (tooLong) "⚠ 코드가 길어요 — 공유 앱에서는 잘릴 수 있어요" else "클라우드 없이, 텍스트 한 장으로 수첩을 옮긴다",
            left + dp(12f), ty, 12f, if (tooLong) UiKit.RED_DEEP else UiKit.BROWN
        )
        ty += dp(17f)
        drawWrapped(
            c,
            if (tooLong) "「클립보드에 복사」로 옮기는 걸 권해요. 그래도 다 안 옮겨지면 「앱으로 보내기」를 써 보세요."
            else "복사해 두거나 메신저로 보내세요. 다른 기기에서 「설정 › 📥 코드에서 불러오기」를 누르면 그대로 이어집니다.",
            left + dp(12f), ty, infoR.width() - dp(24f), 10f, if (tooLong) UiKit.GOLD_DARK else UiKit.MUTED, 2
        )

        // 본문: 코드 창 또는 상태 메시지
        val btnH = dp(34f)
        val gap = dp(9f)
        val boxTop = infoR.bottom + gap
        val boxBottom = bottom - btnH - gap
        codeBox = RectF(left, boxTop, right, boxBottom)
        UiKit.stitchCard(c, g, codeBox, 0xFF3A2C22.toInt(), UiKit.OUTLINE, 1.8f, stitched = false)

        when {
            busy -> drawBusy(c, codeBox, busyText)
            failMsg != null -> drawMessage(c, codeBox, failMsg!!, UiKit.RED_DEEP, "😿")
            code.isEmpty() -> drawMessage(c, codeBox, "아직 코드가 없어요.", UiKit.MUTED, "🗄")
            else -> drawCode(c, codeBox)
        }

        // 하단 버튼 — 코드가 있으면 복사/공유, 실패했으면 다시 만들기
        val by = boxBottom + gap
        val half = (right - left - gap) / 2f
        val copyR = RectF(left, by, left + half, by + btnH)
        val shareR = RectF(left + half + gap, by, right, by + btnH)
        val off = Color.argb(120, 214, 204, 186)
        val offInk = Color.argb(150, 74, 55, 40)
        if (code.isNotEmpty()) {
            UiKit.cuteButton(c, g, copyR, "📋 클립보드에 복사", UiKit.GOLD, UiKit.INK, 12.5f)
            btnRects.add(copyR to { copyCode() })
            UiKit.cuteButton(c, g, shareR, "📤 앱으로 보내기", UiKit.PASTEL_MINT, UiKit.INK, 12.5f)
            btnRects.add(shareR to { shareCode() })
        } else if (failMsg != null && !busy) {
            val retryR = RectF(left, by, right, by + btnH)
            UiKit.cuteButton(c, g, retryR, "↻ 다시 만들기", UiKit.PASTEL_PEACH, UiKit.INK, 12.5f)
            btnRects.add(retryR to {
                failMsg = null
                scroll = 0f
                startExport()
            })
        } else {
            UiKit.cuteButton(c, g, copyR, "📋 클립보드에 복사", off, offInk, 12.5f)
            UiKit.cuteButton(c, g, shareR, "📤 앱으로 보내기", off, offInk, 12.5f)
        }
    }

    /** 코드 창 — 등폭 글씨로 줄 단위로 그리고 손가락 드래그로 스크롤한다 */
    private fun drawCode(c: Canvas, box: RectF) {
        val pad = dp(10f)
        val sizePx = dp(9.5f)
        val lineH = sizePx * 1.42f
        codePaint.textSize = sizePx
        codeBold.textSize = sizePx

        if (codeWidest <= 0f) {
            codeWidest = 0f
            for (ln in codeLines) codeWidest = max(codeWidest, codePaint.measureText(ln))
        }

        val viewH = box.height() - pad * 2f
        val contentH = lineH * codeLines.size
        scrollMax = max(0f, contentH - viewH)
        scroll = scroll.coerceIn(0f, scrollMax)

        c.save()
        c.clipRect(box.left + dp(2f), box.top + dp(2f), box.right - dp(2f), box.bottom - dp(2f))
        val first = ((scroll - pad) / lineH).toInt().coerceAtLeast(0)
        val last = (((scroll + viewH) / lineH).toInt() + 1).coerceAtMost(codeLines.size - 1)
        var y = box.top + pad - scroll + lineH * 0.82f + first * lineH
        for (i in first..last) {
            val ln = codeLines[i]
            // 머리글(PBSAVE1.날짜.체크섬)은 밝은 색으로 — 사용자가 버전/날짜를 바로 확인한다
            val head = i == 0
            val p = if (head) codeBold else codePaint
            c.drawText(ln, box.left + pad, y, p)
            y += lineH
        }
        c.restore()

        // 스크롤 엄지
        if (scrollMax > 0f) {
            val trackTop = box.top + dp(4f)
            val trackH = box.height() - dp(8f)
            val thumbH = max(dp(16f), trackH * viewH / contentH)
            val thumbY = trackTop + (trackH - thumbH) * (scroll / scrollMax)
            val tx = box.right - dp(5f)
            val fill = Paint()
            fill.color = Color.argb(70, 255, 240, 210)
            c.drawRect(tx - dp(2.5f), trackTop, tx + dp(1f), trackTop + trackH, fill)
            fill.color = Color.argb(200, 246, 226, 170)
            c.drawRect(tx - dp(2.5f), thumbY, tx + dp(1f), thumbY + thumbH, fill)
        }

        // 코드 요약 칩 (창 아래 왼쪽 위에 겹쳐 붙이는 스티커)
        val meta = "${Backup.describe(code)} · ${codeLines.size}줄 · 위/아래로 드래그"
        val mp = Type.paintAt(9.5f, false, 0f, 0xFFD9C6A3.toInt())
        val mw = mp.measureText(meta) + dp(16f)
        UiKit.badge(c, g, RectF(box.left + dp(8f), box.bottom - dp(10f), box.left + dp(8f) + mw, box.bottom + dp(10f)),
            meta, 0xFF6B4F35.toInt(), 0xFFFDF6E4.toInt(), 9.5f)
    }

    // ---- 불러오기 화면 ----

    private fun drawRestore(c: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        val gap = dp(9f)

        when {
            busy -> {
                val box = RectF(left, top, right, bottom - dp(43f) - gap)
                UiKit.stitchCard(c, g, box, UiKit.CARD_HI)
                drawBusy(c, box, busyText)
                bottomButtons(c, left, right, bottom, listOf("닫기" to { close() }), enabled = listOf(false))
            }

            step == Step.DONE -> {
                val box = RectF(left, top, right, bottom - dp(43f) - gap)
                UiKit.stitchCard(c, g, box, UiKit.PASTEL_MINT)
                drawMessage(c, box, doneMsg, UiKit.GREEN_DEEP, "🎉", 12f)
                bottomButtons(c, left, right, bottom, listOf("게임으로 돌아가기 ▶" to { backToGame() }), enabled = listOf(true))
            }

            step == Step.CONFIRM -> {
                val i = info
                val boxH = dp(100f)
                val box = RectF(left, top, right, min(top + boxH, bottom - dp(43f) - gap))
                UiKit.stitchCard(c, g, box, UiKit.PASTEL_SKY)
                var ty = box.top + dp(20f)
                UiKit.drawText(c, g, "✅ 읽을 수 있는 코드예요", left + dp(12f), ty, 12.5f, UiKit.BLUE)
                ty += dp(19f)
                val meta = "만든 날짜 ${i?.dateLabel ?: "?"} · 앱 ${i?.app ?: "?"} · 저장 항목 ${i?.keyCount ?: 0}개"
                drawWrapped(c, meta, left + dp(12f), ty, box.width() - dp(24f), 10.5f, UiKit.BROWN, 1)
                ty += dp(16f)
                drawWrapped(
                    c,
                    if (i?.hasSave == true) "본 세이브(진행 상황)가 들어 있어요." else "본 세이브는 없고 부가 저장(앨범·업적 등)만 들어 있어요.",
                    left + dp(12f), ty, box.width() - dp(24f), 10.5f, UiKit.MUTED, 1
                )
                if (i != null && i.warning.isNotEmpty()) {
                    ty += dp(17f)
                    drawWrapped(c, "⚠ ${i.warning}", left + dp(12f), ty, box.width() - dp(24f), 10.5f, UiKit.GOLD_DARK, 2)
                }

                val warnTop = box.bottom + gap
                val warnR = RectF(left, warnTop, right, bottom - dp(43f) - gap)
                if (warnR.height() > dp(28f)) {
                    UiKit.stitchCard(c, g, warnR, 0xFFFFE6E1.toInt(), 0xFFE2574C.toInt(), 1.8f)
                    drawWrapped(
                        c,
                        "지금 이 기기의 진행 상황을 백업 내용으로 덮어써요. 되돌릴 수 없으니, 걱정이 되면 먼저 「백업 코드 만들기」로 현재 상태를 복사해 두세요.",
                        left + dp(12f), warnR.top + dp(16f), warnR.width() - dp(24f), 10.5f, UiKit.RED_DEEP, 4
                    )
                }
                bottomButtons(
                    c, left, right, bottom,
                    listOf("복원하기 🗄" to { startApply() }, "취소" to { step = Step.IDLE }),
                    enabled = listOf(true, true), primary = 0
                )
            }

            step == Step.ERROR -> {
                val box = RectF(left, top, right, bottom - dp(43f) - gap)
                UiKit.stitchCard(c, g, box, 0xFFFFF0EC.toInt(), 0xFFE2574C.toInt(), 1.8f)
                drawMessage(c, box, errorMsg, UiKit.RED_DEEP, "🙈", 11.5f)
                bottomButtons(
                    c, left, right, bottom,
                    listOf("다시 읽어오기 📋" to { startInspect(readClipboard()) }, "취소" to { step = Step.IDLE }),
                    enabled = listOf(true, true)
                )
            }

            else -> {
                val box = RectF(left, top, right, bottom - dp(43f) - gap)
                UiKit.stitchCard(c, g, box, UiKit.CARD_HI)
                var ty = box.top + dp(22f)
                UiKit.iconCircle(c, g, box.centerX(), ty + dp(14f), dp(20f), "📥", 18f, UiKit.PASTEL_SKY)
                ty += dp(52f)
                drawWrapped(
                    c,
                    "1) 옛 기기에서 「설정 › 🗄 백업 코드 만들기」 → 복사\n" +
                        "2) 새 기기에서 이 화면의 아래 버튼을 누르기\n" +
                        "3) 클립보드의 코드를 확인하고 복원하면 끝!",
                    box.left + dp(16f), ty, box.width() - dp(32f), 11f, UiKit.BROWN, 6
                )
                ty += dp(16f) * 4
                drawWrapped(
                    c,
                    "클립보드만 읽어요. 인터넷·권한은 쓰지 않아요.",
                    box.left + dp(16f), ty, box.width() - dp(32f), 10f, UiKit.MUTED, 1
                )
                bottomButtons(
                    c, left, right, bottom,
                    listOf("📋 클립보드에서 읽기" to { startInspect(readClipboard()) }),
                    enabled = listOf(true), primary = 0
                )
            }
        }
    }

    /** 하단 버튼 열 (1~2개). `enabled=false`면 납작한 회색. */
    private fun bottomButtons(
        c: Canvas, left: Float, right: Float, bottom: Float,
        items: List<Pair<String, () -> Unit>>, enabled: List<Boolean>, primary: Int = -1
    ) {
        val btnH = dp(34f)
        val gap = dp(9f)
        val n = items.size
        val bw = (right - left - gap * (n - 1)) / n
        val by = bottom - btnH
        for (i in 0 until n) {
            val r = RectF(left + i * (bw + gap), by, left + i * (bw + gap) + bw, by + btnH)
            val on = enabled.getOrElse(i) { true }
            val base = when {
                !on -> Color.argb(120, 214, 204, 186)
                i == primary -> UiKit.GOLD
                else -> UiKit.PASTEL_PEACH
            }
            val col = if (on) UiKit.INK else Color.argb(150, 74, 55, 40)
            UiKit.cuteButton(c, g, r, items[i].first, base, col, 12.5f)
            if (on) btnRects.add(r to items[i].second)
        }
    }

    // ---- 공통 그리기 도우미 ----

    private fun drawBusy(c: Canvas, box: RectF, text: String) {
        val t = g.time
        val p = Type.paintAt(12.5f, true, 0.02f, UiKit.BROWN_MID)
        val dot = ".".repeat(1 + (t * 2f).toInt() % 3)
        val s = text + dot
        c.drawText(s, box.centerX() - p.measureText(s) / 2f, box.centerY() + dp(4f), p)
        // 바쁘게 도는 픽셀 점 4개
        for (i in 0 until 4) {
            val a = t * 3.2f + i * (Math.PI / 2).toFloat()
            val px = box.centerX() + kotlin.math.cos(a) * dp(16f)
            val py = box.centerY() + dp(24f) + kotlin.math.sin(a) * dp(5f)
            val fill = Paint()
            fill.color = Color.argb(190, 242, 182, 60)
            c.drawRect(px - dp(2f), py - dp(2f), px + dp(2f), py + dp(2f), fill)
        }
    }

    private fun drawMessage(c: Canvas, box: RectF, msg: String, col: Int, icon: String, sizeDp: Float = 11.5f) {
        val iconP = Type.paintAt(22f, false, 0f, UiKit.BROWN)
        c.drawText(icon, box.centerX() - iconP.measureText(icon) / 2f, box.top + dp(38f), iconP)
        val lines = Type.wrap(msg, Type.paintAt(sizeDp, true, 0.01f, col), box.width() - dp(32f))
        var ty = box.top + dp(62f)
        for (ln in lines.take(9)) {
            val p = Type.paintAt(sizeDp, true, 0.01f, col)
            c.drawText(ln, box.centerX() - p.measureText(ln) / 2f, ty, p)
            ty += dp(sizeDp + 4.5f)
        }
    }

    /** 여러 줄 안내 문구 — width 안에서 자동 줄바꿈, maxLines 만큼만. */
    private fun drawWrapped(
        c: Canvas, text: String, x: Float, y: Float, width: Float,
        sizeDp: Float, col: Int, maxLines: Int
    ): Float {
        val p = Type.paintAt(sizeDp, false, 0f, col)
        var ty = y
        for (ln in Type.wrap(text, p, width).take(maxLines)) {
            c.drawText(ln, x, ty, p)
            ty += dp(sizeDp + 4f)
        }
        return ty
    }
}
