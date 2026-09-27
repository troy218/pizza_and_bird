@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — 스텁 보완 (android.view.View).
 *
 * BootView.kt(부팅 스플래시)가 View를 상속하면서 필요해진 최소 스텁.
 * 프리뷰는 MainActivity/GameView를 제외하므로 View는 컴파일만 통과하면 된다.
 */
package android.view

import android.content.Context
import android.graphics.Canvas

abstract class View(val context: Context) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var isAttachedToWindow: Boolean = false
        private set

    open fun onDraw(c: Canvas) {}

    fun invalidate() {}
    fun postInvalidate() {}
    fun postInvalidateDelayed(delayMilliseconds: Long) {}
    fun requestLayout() {}
}
