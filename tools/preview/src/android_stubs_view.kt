@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.view 이벤트 스텁. Input.kt 컴파일용 (실제 이벤트는 발생시키지 않는다). */
package android.view

object MotionEventConstants {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_CANCEL = 3
    const val ACTION_POINTER_DOWN = 5
    const val ACTION_POINTER_UP = 6
}

class MotionEvent {
    companion object {
        const val ACTION_DOWN = MotionEventConstants.ACTION_DOWN
        const val ACTION_UP = MotionEventConstants.ACTION_UP
        const val ACTION_MOVE = MotionEventConstants.ACTION_MOVE
        const val ACTION_CANCEL = MotionEventConstants.ACTION_CANCEL
        const val ACTION_POINTER_DOWN = MotionEventConstants.ACTION_POINTER_DOWN
        const val ACTION_POINTER_UP = MotionEventConstants.ACTION_POINTER_UP
    }

    val actionMasked: Int = 0
    val actionIndex: Int = 0
    val pointerCount: Int = 0

    fun getX(index: Int): Float = 0f
    fun getY(index: Int): Float = 0f
    fun getPointerId(index: Int): Int = 0
}

object KeyEventConstants {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_ENTER = 66
    const val KEYCODE_SPACE = 62
    const val KEYCODE_A = 29
    const val KEYCODE_B = 30
    const val KEYCODE_C = 31
    const val KEYCODE_D = 32
    const val KEYCODE_E = 33
    const val KEYCODE_M = 41
    const val KEYCODE_S = 47
    const val KEYCODE_W = 51
    const val KEYCODE_X = 52
    const val KEYCODE_Z = 54
    const val KEYCODE_MENU = 82
    const val KEYCODE_BACK = 4
    const val KEYCODE_SHIFT_LEFT = 59
    const val KEYCODE_SHIFT_RIGHT = 60
}

class KeyEvent {
    companion object {
        val ACTION_DOWN = KeyEventConstants.ACTION_DOWN
        val ACTION_UP = KeyEventConstants.ACTION_UP
        val KEYCODE_DPAD_LEFT = KeyEventConstants.KEYCODE_DPAD_LEFT
        val KEYCODE_DPAD_RIGHT = KeyEventConstants.KEYCODE_DPAD_RIGHT
        val KEYCODE_DPAD_UP = KeyEventConstants.KEYCODE_DPAD_UP
        val KEYCODE_DPAD_DOWN = KeyEventConstants.KEYCODE_DPAD_DOWN
        val KEYCODE_DPAD_CENTER = KeyEventConstants.KEYCODE_DPAD_CENTER
        val KEYCODE_ENTER = KeyEventConstants.KEYCODE_ENTER
        val KEYCODE_SPACE = KeyEventConstants.KEYCODE_SPACE
        val KEYCODE_A = KeyEventConstants.KEYCODE_A
        val KEYCODE_B = KeyEventConstants.KEYCODE_B
        val KEYCODE_C = KeyEventConstants.KEYCODE_C
        val KEYCODE_D = KeyEventConstants.KEYCODE_D
        val KEYCODE_E = KeyEventConstants.KEYCODE_E
        val KEYCODE_M = KeyEventConstants.KEYCODE_M
        val KEYCODE_S = KeyEventConstants.KEYCODE_S
        val KEYCODE_W = KeyEventConstants.KEYCODE_W
        val KEYCODE_X = KeyEventConstants.KEYCODE_X
        val KEYCODE_Z = KeyEventConstants.KEYCODE_Z
        val KEYCODE_MENU = KeyEventConstants.KEYCODE_MENU
        val KEYCODE_BACK = KeyEventConstants.KEYCODE_BACK
        val KEYCODE_SHIFT_LEFT = KeyEventConstants.KEYCODE_SHIFT_LEFT
        val KEYCODE_SHIFT_RIGHT = KeyEventConstants.KEYCODE_SHIFT_RIGHT
    }
}
