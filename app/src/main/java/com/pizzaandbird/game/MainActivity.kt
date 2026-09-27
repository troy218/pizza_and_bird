package com.pizzaandbird.game

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

class MainActivity : Activity() {

    private var gameView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        gameView?.onResume()
        hideSystemUi()
    }

    override fun onPause() {
        gameView?.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { ctl ->
                ctl.hide(WindowInsets.Type.systemBars())
                ctl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }
    }

    private fun isGameKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S,
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_M,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BACK -> true

        else -> false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gv = gameView
        if (gv != null && isGameKey(event.keyCode)) {
            gv.game.input.onKeyEvent(event.keyCode, event.action)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
