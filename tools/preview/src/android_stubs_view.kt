@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.view 이벤트 스텁. Input.kt 컴파일용 (실제 이벤트는 발생시키지 않는다). */
package android.view

class MotionEvent(
    val actionMasked: Int = ACTION_DOWN,
    val actionIndex: Int = 0,
    private val pointers: List<Triple<Int, Float, Float>> = listOf(Triple(0, 0f, 0f))
) {
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
    }

    val pointerCount: Int get() = pointers.size

    fun getX(index: Int): Float = pointers[index].second
    fun getY(index: Int): Float = pointers[index].third
    fun getPointerId(index: Int): Int = pointers[index].first
}

class KeyEvent {
    companion object {
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
}

/** tools/preview — android.view.View 스텁 (BootView 컴파일용). 실제 화면에는 뜨지 않는다. */
open class View(val context: android.content.Context) {
    var width: Int = 0
    var height: Int = 0
    val isAttachedToWindow: Boolean = false
    open fun onDraw(c: android.graphics.Canvas) {}
    fun postInvalidateDelayed(delayMillis: Long) {}
    fun postInvalidate() {}
    fun invalidate() {}
}
