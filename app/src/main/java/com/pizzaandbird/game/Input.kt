package com.pizzaandbird.game

import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.sqrt

/** 가상 컨트롤 종류 */
enum class Ctrl { NONE, STICK, A, B, CAM, MENU, RUN, EAT, MAP }

/**
 * 멀티터치 + 키보드 입력.
 * UI 스레드에서 이벤트를 큐잉하고, 게임 스레드에서 process()로 소비한다.
 */
class Input(private val game: Game) {

    private class QEv(val kind: Int, val x: Float, val y: Float, val id: Int, val keyCode: Int, val act: Int)

    private object K {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val KEY = 3
        const val CANCEL = 4
    }

    /** 탭/드래그 구분 임계값 (실제 화면 px) */
    private val tapDragPx = 22f

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
    var justEat = false       // 간식 먹기 (🍕 버튼 / E 키)
    var justMap = false       // 큰 지도 (미니맵 탭)
    var isRun = false         // 달리기 홀드 (🏃 버튼 / Shift 키)

    fun onTouchEvent(e: MotionEvent): Boolean {
        synchronized(lock) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex
                    queue.add(QEv(K.DOWN, e.getX(i), e.getY(i), e.getPointerId(i), 0, 0))
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        queue.add(QEv(K.MOVE, e.getX(i), e.getY(i), e.getPointerId(i), 0, 0))
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

    fun onKeyEvent(keyCode: Int, action: Int) {
        synchronized(lock) { queue.add(QEv(K.KEY, 0f, 0f, -1, keyCode, action)) }
    }

    /** 게임 스레드: 이번 프레임 이벤트 소비 */
    fun process() {
        val evs: List<QEv>
        synchronized(lock) {
            evs = ArrayList(queue)
            queue.clear()
        }
        for (ev in evs) {
            when (ev.kind) {
                K.DOWN -> {
                    pointerPos[ev.id] = PointF(ev.x, ev.y)
                    val ctrl = game.hud.controlAt(ev.x, ev.y)
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
                        // 버튼류는 누른 순간에 반응
                        press(ctrl)
                    } else {
                        // 월드 탭은 떼는 순간에 반응 (드래그와 구분)
                        pointerDown[ev.id] = PointF(ev.x, ev.y)
                        pointerDragged.remove(ev.id)
                    }
                }
                K.MOVE -> {
                    pointerPos[ev.id]?.set(ev.x, ev.y)
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
                    if (ctrl == Ctrl.STICK) {
                        game.hud.releaseStick()
                        // 움직이지 않고 떼면 월드 탭으로 처리 (조이스틱 구역에서도 상호작용 유지)
                        if (ev.id in stickTentative && ev.id !in pointerDragged) {
                            tapScreen = PointF(ev.x, ev.y)
                        }
                        stickTentative.remove(ev.id)
                    }
                    if (ctrl == Ctrl.NONE && ev.id in pointerDown && ev.id !in pointerDragged) {
                        tapScreen = PointF(ev.x, ev.y)
                    }
                    pointerPos.remove(ev.id)
                    pointerCtrl.remove(ev.id)
                    pointerDown.remove(ev.id)
                    pointerDragged.remove(ev.id)
                }
                K.CANCEL -> {
                    if (pointerCtrl[ev.id] == Ctrl.STICK) game.hud.releaseStick()
                    stickTentative.remove(ev.id)
                    pointerPos.remove(ev.id)
                    pointerCtrl.remove(ev.id)
                    pointerDown.remove(ev.id)
                    pointerDragged.remove(ev.id)
                }
                K.KEY -> {
                    if (ev.act == KeyEvent.ACTION_DOWN) {
                        keys[ev.keyCode] = true
                        when (ev.keyCode) {
                            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_DPAD_CENTER -> justA = true

                            KeyEvent.KEYCODE_X -> justB = true
                            KeyEvent.KEYCODE_C -> justCam = true
                            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_MENU -> justMenu = true
                            KeyEvent.KEYCODE_E -> justEat = true
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

        // 달리기 홀드 (버튼 또는 Shift)
        isRun = Ctrl.RUN in activeControls() ||
                keys[KeyEvent.KEYCODE_SHIFT_LEFT] == true ||
                keys[KeyEvent.KEYCODE_SHIFT_RIGHT] == true
    }

    private fun press(ctrl: Ctrl) {
        when (ctrl) {
            Ctrl.A -> { justA = true; game.haptic() }
            Ctrl.B -> { justB = true; game.haptic() }
            Ctrl.CAM -> { justCam = true; game.haptic() }
            Ctrl.MENU -> { justMenu = true; game.haptic() }
            Ctrl.EAT -> { justEat = true; game.haptic() }
            Ctrl.MAP -> { justMap = true; game.haptic() }
            Ctrl.RUN -> game.haptic()
            else -> {}
        }
    }

    /** 화면 좌표 탭 (오버레이가 소비) */
    fun consumeTapScreen(): PointF? {
        val t = tapScreen
        tapScreen = null
        return t
    }

    /** 가상 월드 좌표 탭 (씬이 소비) */
    fun consumeTapWorld(): PointF? {
        val t = tapScreen
        tapScreen = null
        return t?.let { game.screenToWorld(it) }
    }

    /** 프레임 끝: 엣지 트리거 초기화 */
    fun endFrame() {
        justA = false
        justB = false
        justCam = false
        justMenu = false
        justBack = false
        justEat = false
        justMap = false
        tapScreen = null
    }

    /** 현재 눌린 컨트롤 목록 (시각 피드백용) */
    fun activeControls(): Set<Ctrl> {
        synchronized(lock) { return pointerCtrl.values.toSet() }
    }

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
