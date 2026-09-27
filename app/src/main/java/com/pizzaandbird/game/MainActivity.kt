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

    /** 앱이 포그라운드에 있는지 (부팅 완료 콜백이 게임 스레드를 켤지 판단) */
    private var uiResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 스플래시를 먼저 띄운다 — Game/Assets 생성(비트맵 수백 장·폰트·세이브)이
        // 길어서, 예전처럼 여기서 다 만들면 앱을 켜고도 한동안 화면이 얼어 있었다.
        setContentView(BootView(this))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread({
            val game = Game(this)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val gv = GameView(this, game)
                if (uiResumed) gv.onResume()
                gameView = gv
                setContentView(gv)
                hideSystemUi()
            }
        }, "PizzaAndBirdBoot").start()
    }

    override fun onResume() {
        super.onResume()
        uiResumed = true
        gameView?.onResume()
        hideSystemUi()
    }

    override fun onPause() {
        uiResumed = false
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
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_E,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BACK -> true

        else -> false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gv = gameView
        if (gv != null && isGameKey(event.keyCode)) {
            // 키 반복 이벤트는 무시 (버튼을 꾹 누르고 있을 때 연타 방지)
            if (event.repeatCount > 0 && event.action == KeyEvent.ACTION_DOWN) return true
            gv.game.input.onKeyEvent(event.keyCode, event.action)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
