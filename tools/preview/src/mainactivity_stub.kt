@file:Suppress("unused")

/**
 * tools/preview — MainActivity 스텁.
 * Game.kt가 `(context as? MainActivity)` 로 캐스팅하므로 프리뷰 컴파일에만 필요.
 * (실제 MainActivity.kt는 Android 빌드에만 포함)
 */
package com.pizzaandbird.game

import android.content.Context

open class MainActivity : Context() {
    open fun runOnUiThread(action: Runnable) {
        action.run()
    }

    open fun finish() {}
}
