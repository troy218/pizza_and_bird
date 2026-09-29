package com.pizzaandbird.game

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.sqrt

/** 가상 컨트롤 종류 */
enum class Ctrl { NONE, STICK, A, B, CAM, MENU, EAT, MAP, PUNCH, QUEST, QUEST_VIEW, STATS, DEX, ACHIEVE, SETTINGS }

/** 가방(☰)과 그 옆의 바로가기 버튼이 열 수 있는 창. */
enum class MenuTarget { BAG, DEX, ACHIEVEMENTS, SETTINGS, STATUS }

/**
 * 멀티터치 + 키보드 입력.
 * UI 스레드에서 이벤트를 큐잉하고, 게임 스레드에서 process()로 소비한다.
 */
class Input(private val game: Game) {

    private class QEv(val kind: Int, var x: Float, var y: Float, val id: Int, val keyCode: Int, val act: Int)

    private object K {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val KEY = 3
        const val CANCEL = 4
    }

    /**
     * 탭/드래그 구분 임계값 (실제 화면 px, 기기 밀도에 비례).
     *
     * 살짝 미끄러지는 손끝도 탭으로 인정해야 "여러 번 눌러야 반응"하는 느낌이 사라진다.
     * 드래그가 필요한 지도 창은 raw 터치를 따로 쓰므로 넓혀도 안전하다.
     */
    private val tapDragPx = 17f * game.density

    private val lock = Any()
    private val pointerPos = HashMap<Int, PointF>()
    private val pointerCtrl = HashMap<Int, Ctrl>()
    private val pointerDown = HashMap<Int, PointF>()
    private val pointerDragged = HashSet<Int>()
    private val stickTentative = HashSet<Int>()   // 아직 안 움직인 스틱 손가락 (떼면 탭으로)
    private val queue = ArrayList<QEv>()
    private val keys = HashMap<Int, Boolean>()
    private var tapScreen: PointF? = null

    // ----- 프레임 상태 (게임 스레드 전용) -----
    var dirX = 0f
    var dirY = 0f
    var moveMag = 0f            // 스틱을 민 정도 0~1
    var moveScale = 1f          // 이동 속도 배율 (아날로그 스틱이면 0.5~1.0)
    var stickEngaged = false    // 스틱 확정(탭 후보 아님) — HUD 애니메이션용
    var justA = false
    var justB = false
    var justCam = false
    var justMenu = false
    var justBack = false
    var justEat = false       // 간식 먹기 (🍕 버튼 / E 키) — [P11] 피자 **한 조각**
    var justEatPick = false   // [P11] 빠른 피자 창 (🍕 버튼 길게 누르기 / Q 키)
    var justMap = false       // 큰 지도 (미니맵 탭)
    var justPunch = false     // 펀치 (👊 버튼 / F 키) — 근처 고양이를 날려 보낸다
    var justQuest = false     // 의뢰 내용 상자 탭 — 의뢰 목표 위치로 자동 이동
    var justQuestView = false // 의뢰 칩(의뢰 · 제목) 탭 — 해당 의뢰 내용을 보여 준다
    var justStats = false     // 좌상단 레벨 패널 탭 — 내 상태 통합 창
    var menuTarget = MenuTarget.BAG   // justMenu와 함께: 어떤 창을 열지 (가방/도감/업적/설정)
    var isRun = false         // 달리기 홀드 (키보드 Shift)

    /** [P11] 🍕 버튼 홀드 진행도 0~1 — HUD가 버튼 주위의 링으로 보여 준다 */
    var eatHoldT = 0f
        private set

    // [P11] 🍕 버튼 홀드 추적: 손가락 id → 누른 시각 / 이미 '빠른 피자 창'을 띄웠는지
    private val eatHoldStart = HashMap<Int, Long>()
    private val eatHoldFired = HashSet<Int>()

    // ----- 로우 터치 (확대/이동 가능한 지도 같은 전체화면 오버레이용) -----
    /** 현재 창의 입력 방식만 따른다. 닫힘/교체/씬 전환 후 raw 상태가 남지 않는다. */
    val rawMode: Boolean get() = game.scene.overlay?.usesRawTouch == true
    class RawEv(val kind: Int, val id: Int, val x: Float, val y: Float) {
        companion object { const val DOWN = 0; const val MOVE = 1; const val UP = 2; const val CANCEL = 3 }
    }
    val rawEvents = ArrayList<RawEv>()

    fun onTouchEvent(e: MotionEvent): Boolean {
        synchronized(lock) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex
                    queue.add(QEv(K.DOWN, e.getX(i), e.getY(i), e.getPointerId(i), 0, 0))
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        val x = e.getX(i)
                        val y = e.getY(i)
                        val id = e.getPointerId(i)
                        if (rawMode) queue.add(QEv(K.MOVE, x, y, id, 0, 0))
                        else enqueueMove(x, y, id)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val i = e.actionIndex
                    queue.add(QEv(K.UP, e.getX(i), e.getY(i), e.getPointerId(i), 0, 0))
                }
                MotionEvent.ACTION_CANCEL -> {
                    for (i in 0 until e.pointerCount) {
                        queue.add(QEv(K.CANCEL, e.getX(i), e.getY(i), e.getPointerId(i), 0, 0))
                    }
                }
            }
        }
        return true
    }

    /**
     * 게임 프레임이 밀릴 때 ACTION_MOVE가 큐를 채워 탭/버튼 입력까지 늦추지 않도록
     * 포인터별 미처리 이동은 최신 좌표 하나로 합친다. DOWN/UP/CANCEL 경계는 유지해
     * 탭 판정과 멀티터치 순서는 바꾸지 않는다. 로우 터치 지도/백업 창은 원본 샘플을 보존한다.
     * 호출자는 onTouchEvent의 lock 안에 있다.
     */
    private fun enqueueMove(x: Float, y: Float, id: Int) {
        for (i in queue.lastIndex downTo 0) {
            val pending = queue[i]
            if (pending.id != id) continue
            if (pending.kind == K.MOVE) {
                pending.x = x
                pending.y = y
                return
            }
            // 이 포인터의 DOWN/UP/CANCEL 뒤로는 별도 이동 구간이다.
            break
        }
        queue.add(QEv(K.MOVE, x, y, id, 0, 0))
    }

    fun onKeyEvent(keyCode: Int, action: Int) {
        synchronized(lock) { queue.add(QEv(K.KEY, 0f, 0f, -1, keyCode, action)) }
    }

    /** 일시정지 중에는 ACTION_UP이 오지 않을 수 있다. 홀드 상태와 미처리 입력을 버린다. */
    fun releaseHeld() {
        synchronized(lock) {
            queue.clear()
            keys.clear()
            for ((id, p) in pointerPos) queue.add(QEv(K.CANCEL, p.x, p.y, id, 0, 0))
        }
        // [P11] 피자 버튼 홀드도 함께 잊는다 (재개 후 갑자기 창이 열리면 안 된다)
        eatHoldStart.clear()
        eatHoldFired.clear()
        eatHoldT = 0f
    }

    /** 게임 스레드: 이번 프레임 이벤트 소비 */
    fun process() {
        val evs: List<QEv>
        synchronized(lock) {
            evs = ArrayList(queue)
            queue.clear()
        }
        loop@ for (ev in evs) {
            when (ev.kind) {
                K.DOWN -> {
                    pointerPos[ev.id] = PointF(ev.x, ev.y)
                    if (rawMode) {
                        pointerCtrl[ev.id] = Ctrl.NONE
                        rawEvents.add(RawEv(RawEv.DOWN, ev.id, ev.x, ev.y))
                        continue@loop
                    }
                    // 모달 오버레이가 열려 있으면 뒤에 깔린 HUD 버튼이 터치를 가로채면 안 된다.
                    val ctrl = if (game.scene.overlay == null) game.hud.controlAt(ev.x, ev.y) else Ctrl.NONE
                    pointerCtrl[ev.id] = ctrl
                    if (ctrl != Ctrl.NONE) {
                        // 조이스틱: 손을 댄 자리가 베이스가 된다 (듀랑고식 플로팅)
                        if (ctrl == Ctrl.STICK) {
                            game.hud.grabStick(ev.x, ev.y)
                            // 아직 안 움직였으면 탭 후보로 유지 (떼면 월드 탭으로 처리)
                            stickTentative.add(ev.id)
                            pointerDown[ev.id] = PointF(ev.x, ev.y)
                            pointerDragged.remove(ev.id)
                        }
                        // [P11] 피자 버튼만 '떼는 순간'에 반응한다 — 길게 누르면 빠른 피자 창
                        if (ctrl == Ctrl.EAT) {
                            eatHoldStart[ev.id] = SystemClock.uptimeMillis()
                            game.haptic()
                        } else {
                            // 버튼류는 누른 순간에 반응
                            press(ctrl)
                        }
                    } else {
                        // 탭은 **누른 순간**에 반응한다. 떼는 순간 판정하면 손끝이
                        // 조금만 미끄러져도 버튼이 먹통처럼 보여 몇 번씩 눌러야 했다.
                        // (드래그가 필요한 화면은 rawMode — 지도/백업 — 라 이 경로를 안 쓴다)
                        pointerDown[ev.id] = PointF(ev.x, ev.y)
                        pointerDragged.remove(ev.id)
                        tapFiredIds.add(ev.id)
                        tapScreen = PointF(ev.x, ev.y)
                    }
                }
                K.MOVE -> {
                    pointerPos[ev.id]?.set(ev.x, ev.y)
                    if (rawMode) {
                        rawEvents.add(RawEv(RawEv.MOVE, ev.id, ev.x, ev.y))
                        continue@loop
                    }
                    if (ev.id in stickTentative) {
                        val d = pointerDown[ev.id]
                        if (d != null) {
                            val ddx = ev.x - d.x
                            val ddy = ev.y - d.y
                            if (ddx * ddx + ddy * ddy > tapDragPx * tapDragPx) {
                                // 스틱 확정 — 이제부턴 이동 입력 (탭 아님)
                                stickTentative.remove(ev.id)
                                pointerDown.remove(ev.id)
                                pointerDragged.remove(ev.id)
                                game.haptic()
                            }
                        }
                    } else if (pointerCtrl[ev.id] == Ctrl.NONE) {
                        val d = pointerDown[ev.id]
                        if (d != null) {
                            val ddx = ev.x - d.x
                            val ddy = ev.y - d.y
                            if (ddx * ddx + ddy * ddy > tapDragPx * tapDragPx) pointerDragged.add(ev.id)
                        }
                    }
                }
                K.UP -> {
                    val ctrl = pointerCtrl[ev.id] ?: Ctrl.NONE
                    // [P11] 피자 버튼 — 길게 누르기가 이미 발동했다면 먹지 않는다
                    if (ctrl == Ctrl.EAT) {
                        if (ev.id !in eatHoldFired) justEat = true
                        eatHoldStart.remove(ev.id)
                        eatHoldFired.remove(ev.id)
                    }
                    if (ctrl == Ctrl.STICK) {
                        game.hud.releaseStick()
                        // 움직이지 않고 떼면 월드 탭으로 처리 (조이스틱 구역에서도 상호작용 유지)
                        if (ev.id in stickTentative && ev.id !in pointerDragged) {
                            // 탭 위치는 "누른 자리" 기준 — 떼는 순간 손끝이 몇 px 밀려도
                            // 처음 겨냥한 버튼이 눌리도록 한다.
                            val aim = pointerDown[ev.id]
                            tapScreen = if (aim != null) PointF(aim.x, aim.y) else PointF(ev.x, ev.y)
                        }
                        stickTentative.remove(ev.id)
                    }
                    val down = pointerDown[ev.id]
                    if (!rawMode && ctrl == Ctrl.NONE && down != null && ev.id !in pointerDragged &&
                        ev.id !in tapFiredIds
                    ) {
                        val dx = ev.x - down.x
                        val dy = ev.y - down.y
                        if (dx * dx + dy * dy <= tapDragPx * tapDragPx) {
                            // 누운 순간 탭이 이미 처리되지 않은 예외적 경우만 뗄 때 보정한다.
                            tapScreen = PointF(down.x, down.y)
                        }
                    }
                    tapFiredIds.remove(ev.id)
                    pointerPos.remove(ev.id)
                    pointerCtrl.remove(ev.id)
                    pointerDown.remove(ev.id)
                    pointerDragged.remove(ev.id)
                    if (rawMode) rawEvents.add(RawEv(RawEv.UP, ev.id, ev.x, ev.y))
                }
                K.CANCEL -> {
                    if (pointerCtrl[ev.id] == Ctrl.STICK) game.hud.releaseStick()
                    // [P11] 터치가 끊긴 피자 버튼은 아무 일도 일어나지 않게 홀드만 정리한다
                    eatHoldStart.remove(ev.id)
                    eatHoldFired.remove(ev.id)
                    stickTentative.remove(ev.id)
                    tapFiredIds.remove(ev.id)
                    pointerPos.remove(ev.id)
                    pointerCtrl.remove(ev.id)
                    pointerDown.remove(ev.id)
                    pointerDragged.remove(ev.id)
                    if (rawMode) rawEvents.add(RawEv(RawEv.CANCEL, ev.id, ev.x, ev.y))
                }
                K.KEY -> {
                    if (ev.act == KeyEvent.ACTION_DOWN) {
                        keys[ev.keyCode] = true
                        when (ev.keyCode) {
                            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_DPAD_CENTER -> justA = true

                            KeyEvent.KEYCODE_X -> justB = true
                            KeyEvent.KEYCODE_C -> justCam = true
                            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_MENU -> {
                                justMenu = true; menuTarget = MenuTarget.BAG
                            }
                            KeyEvent.KEYCODE_E -> justEat = true
                            KeyEvent.KEYCODE_Q -> justEatPick = true   // [P11] 빠른 피자 창
                            KeyEvent.KEYCODE_F -> justPunch = true
                            KeyEvent.KEYCODE_BACK -> justBack = true
                        }
                    } else if (ev.act == KeyEvent.ACTION_UP) {
                        keys.remove(ev.keyCode)
                    }
                }
            }
        }

        // 방향 (플로팅 조이스틱 터치 + 키보드)
        var dx = 0f
        var dy = 0f
        var engaged = false
        for ((id, p) in pointerPos) {
            if (pointerCtrl[id] == Ctrl.STICK && id !in stickTentative) {
                val v = game.hud.stickVector(p)
                dx += v.x
                dy += v.y
                engaged = true
            }
        }
        stickEngaged = engaged
        if (keys[KeyEvent.KEYCODE_DPAD_LEFT] == true || keys[KeyEvent.KEYCODE_A] == true) dx -= 1f
        if (keys[KeyEvent.KEYCODE_DPAD_RIGHT] == true || keys[KeyEvent.KEYCODE_D] == true) dx += 1f
        if (keys[KeyEvent.KEYCODE_DPAD_UP] == true || keys[KeyEvent.KEYCODE_W] == true) dy -= 1f
        if (keys[KeyEvent.KEYCODE_DPAD_DOWN] == true || keys[KeyEvent.KEYCODE_S] == true) dy += 1f
        val len = sqrt(dx * dx + dy * dy)
        if (len > 1f) { dx /= len; dy /= len }
        dirX = dx
        dirY = dy
        moveMag = len.coerceIn(0f, 1f)
        // 아날로그 스틱: 살짝 밀면 살살, 끝까지 밀면 최고 속도
        moveScale = if (game.state.analogStick && moveMag > 0f) 0.5f + 0.5f * moveMag else 1f

        // 달리기 홀드 (터치 HUD에서는 버튼을 덜어내고, 키보드 Shift만 유지)
        isRun = keys[KeyEvent.KEYCODE_SHIFT_LEFT] == true ||
                keys[KeyEvent.KEYCODE_SHIFT_RIGHT] == true
        // [P11] 피자 버튼 홀드 — 오래 누르면 '빠른 피자 창'이 열린다 (진행 링은 HUD가 그린다)
        eatHoldT = eatHoldProgress()

        // 그리기(버튼 눌림 표시)가 볼 스냅샷 — 이 프레임의 마지막에 한 번만 만든다
        refreshPressSnapshot()
    }

    /**
     * [P11] 피자 버튼을 누른 손가락의 홀드 진행도 0~1.
     * [EAT_HOLD_MS]를 넘기는 순간 한 번만 `justEatPick`을 세운다 (그 손가락은 떼어도 먹지 않는다).
     */
    private fun eatHoldProgress(): Float {
        if (eatHoldStart.isEmpty()) return 0f
        val now = SystemClock.uptimeMillis()
        var t = 0f
        for ((id, start) in eatHoldStart) {
            val el = now - start
            if (el >= EAT_HOLD_MS && id !in eatHoldFired) {
                eatHoldFired.add(id)
                justEatPick = true
                game.haptic()
            }
            t = maxOf(t, (el.toFloat() / EAT_HOLD_MS).coerceIn(0f, 1f))
        }
        return t
    }

    companion object {
        /** [P11] 피자 버튼을 이만큼(ms) 이상 누르고 있으면 '빠른 피자 창'이 열린다 */
        const val EAT_HOLD_MS = 420L
    }

    private fun press(ctrl: Ctrl) {
        when (ctrl) {
            Ctrl.A -> { justA = true; game.haptic() }
            Ctrl.B -> { justB = true; game.haptic() }
            Ctrl.CAM -> { justCam = true; game.haptic() }
            Ctrl.MENU -> { justMenu = true; menuTarget = MenuTarget.BAG; game.haptic() }
            // [P11] Ctrl.EAT 은 여기로 오지 않는다 — 누르기/떼기/길게 누르기를 위에서 따로 처리
            Ctrl.MAP -> { justMap = true; game.haptic() }
            Ctrl.PUNCH -> { justPunch = true; game.haptic() }
            Ctrl.QUEST -> { justQuest = true; game.haptic() }
            Ctrl.QUEST_VIEW -> { justQuestView = true; game.haptic() }
            Ctrl.STATS -> { justStats = true; game.haptic() }
            Ctrl.DEX -> { justMenu = true; menuTarget = MenuTarget.DEX; game.haptic() }
            Ctrl.ACHIEVE -> { justMenu = true; menuTarget = MenuTarget.ACHIEVEMENTS; game.haptic() }
            Ctrl.SETTINGS -> { justMenu = true; menuTarget = MenuTarget.SETTINGS; game.haptic() }
            else -> {}
        }
    }

    // ---------------------------------------------------------------------
    // 그리기용 눌림 스냅샷
    //
    //  버튼·카드마다 isPressedIn() 을 부르면 그릴 때마다 잠금을 잡고 UI 스레드의
    //  터치 큐잉을 막는다(가방 화면은 프레임당 200회 이상). process() 맨 끝에
    //  눌린 손가락 좌표를 한 번만 모아 두고, 그리는 쪽은 그것만 본다.
    // ---------------------------------------------------------------------
    private class PressSnapshot(val xs: FloatArray, val ys: FloatArray, val n: Int) {
        fun inRect(r: RectF): Boolean {
            for (i in 0 until n) if (r.contains(xs[i], ys[i])) return true
            return false
        }

        fun inCircle(cx: Float, cy: Float, radius: Float): Boolean {
            val r2 = radius * radius
            for (i in 0 until n) {
                val dx = xs[i] - cx
                val dy = ys[i] - cy
                if (dx * dx + dy * dy <= r2) return true
            }
            return false
        }
    }

    @Volatile
    private var pressSnap = PressSnapshot(FloatArray(0), FloatArray(0), 0)

    @Volatile
    private var ctrlSnap: Set<Ctrl> = emptySet()

    /** process() 끝에서 눌린 포인터를 한 번만 모아 둔다 (그리기 전용 스냅샷) */
    private fun refreshPressSnapshot() {
        var n = 0
        for ((id, p) in pointerPos) {
            if (pointerCtrl[id] == Ctrl.NONE) n++
        }
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var i = 0
        for ((id, p) in pointerPos) {
            if (pointerCtrl[id] != Ctrl.NONE) continue
            xs[i] = p.x
            ys[i] = p.y
            i++
        }
        pressSnap = PressSnapshot(xs, ys, n)
        ctrlSnap = HashSet(pointerCtrl.values)
    }

    /** 누운 손가락별로 "탭을 이미 뗄 때 말고 누른 순간에 처리했는지" 기록 (중복 발동 방지) */
    private val tapFiredIds = HashSet<Int>()

    /** 화면 좌표 탭 (오버레이가 소비) */
    fun consumeTapScreen(): PointF? {
        val t = tapScreen
        tapScreen = null
        return t
    }

    /**
     * 가상 월드 좌표 탭 (씬이 소비).
     * 레터박스 바깥 터치는 월드 입력으로 흘리지 않고, 화면 오프셋/배율은
     * Game.screenToWorld가 카메라 오프셋과 망원 배율까지 함께 역변환한다.
     */
    fun consumeTapWorld(): PointF? {
        val t = tapScreen
        tapScreen = null
        if (t == null) return null
        if (!game.isInsideVirtualViewport(t)) return null
        return game.screenToWorld(t)
    }

    /** 프레임 끝: 엣지 트리거 초기화 */
    fun endFrame() {
        justA = false
        justB = false
        justCam = false
        justMenu = false
        justBack = false
        justEat = false
        justEatPick = false
        justMap = false
        justPunch = false
        justQuest = false
        justQuestView = false
        justStats = false
        menuTarget = MenuTarget.BAG
        tapScreen = null
        rawEvents.clear()
    }

    /**
     * 화면 좌표 영역이 지금 눌려 있는지 (시각 피드백용).
     * 오버레이 버튼이 손끝에서 눌리는 순간 어두워지도록 공통으로 쓴다.
     * (잠그지 않고 process() 가 만들어 둔 스냅샷만 본다)
     */
    fun isPressedIn(r: RectF): Boolean = pressSnap.inRect(r)

    /** 현재 눌린 위치가 원 안인지 (원형 버튼 시각 피드백용) */
    fun isPressedInCircle(cx: Float, cy: Float, radius: Float): Boolean =
        pressSnap.inCircle(cx, cy, radius)

    /** 현재 눌린 컨트롤 목록 (시각 피드백용) */
    fun activeControls(): Set<Ctrl> = ctrlSnap

    /** 조이스틱을 잡은 포인터 위치 (없으면 베이스 위치) */
    fun stickTouchPoint(): PointF {
        synchronized(lock) {
            for ((id, p) in pointerPos) {
                if (pointerCtrl[id] == Ctrl.STICK) return PointF(p.x, p.y)
            }
        }
        return PointF(game.hud.stickBaseX, game.hud.stickBaseY)
    }
}
