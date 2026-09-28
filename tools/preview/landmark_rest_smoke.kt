package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.LandmarkScene
import com.pizzaandbird.game.Regions
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot

/**
 * 랜드마크 휴게 공간 "앉기" 동작 스모크.
 *
 * 요구사항(사용자): "앉거나 하는 동작시에는 자전거에 내려서 실제로 걸어가서 벤치에 앉게 해줘."
 * 랜드마크 안에는 자전거가 없지만, A 로 쉬기를 고르면
 *   자리까지 실제로 걸어간 뒤 → 앉은 자세 → 쉬는 효과 → 시간이 지나면 일어섬
 * 이 되어야 한다. 월드 벤치(WorldScene)와 같은 규칙.
 *
 * 검증:
 *   A) "잠시 쉬기" 선택 후 좌석 위치로 걸어가 앉는다 (순간이동 X — 도보로 수렴)
 *   B) 앉으면 쉬는 효과 (배부름 +8, 행운 +2 — 업스트림 밸런스)
 *   C) 3.2초 뒤 벤치 앞에 일어선다
 *   D) 앉아 있는 동안 A 재입력 무시 / B 로 일어나기
 *   E) 걸어가는 도중 이동 입력 → 연출 취소
 */
object LandmarkRestSmoke {

    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    private var failed = false

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = args.getOrNull(0)?.let { File(it).apply { mkdirs() } }

        // 좌석/서는 자리 좌표 (LandmarkScene.rest() 와 동일한 계산식)
        val restX = 3f * 16f
        val restY = 8.2f * 16f
        val seatX = restX - 8f
        val seatY = restY - 11.25f
        val standX = restX - 8f
        val standY = restY + 13f

        println("landmark rest smoke — seat=($seatX,$seatY) stand=($standX,$standY)")

        // ─────────────────────────────────────────────────────────────
        // A~C: 쉬기 선택 → 걸어가 앉기 → 효과 → 시간 지나 일어남
        // ─────────────────────────────────────────────────────────────
        run {
            val g = newLandmark()
            g.state.hunger = 50f              // 배부름 절반부터 시작 (회복량 검증)
            walkNearRest(g)
            val luck0 = g.state.luck
            val hunger0 = g.state.hunger

            // A → "휴게 공간" 대화상자 → (등장 연출 대기) A → "잠시 쉬기"
            pressA(g)
            repeat(10) { frame(g) }        // 오버레이 등장 연출(0.09초) 대기
            pressA(g)

            // 걸어간 뒤 앉기: 1.5초면 충분
            repeat(90) { frame(g) }
            if (near(g.state.px, seatX) && near(g.state.py, seatY)) {
                println("OK   쉬기를 고르면 자리까지 걸어가 앉았다 (${g.state.px.toInt()},${g.state.py.toInt()})")
            } else {
                println("FAIL 자리에 앉지 않았다 (${g.state.px},${g.state.py} != $seatX,$seatY)")
                failed = true
            }
            render(g, outDir, "rest_01_sitting.png")

            if (g.state.luck >= luck0 + 1.9f && g.state.hunger >= hunger0 + 7f) {
                println("OK   앉으며 쉬는 효과 (배부름 +8 · 행운 +2)")
            } else {
                println("FAIL 쉬는 효과가 없다 (luck ${luck0}->${g.state.luck}, hunger ${hunger0}->${g.state.hunger})")
                failed = true
            }

            // 앉아 있는 동안 A 재입력 무시
            val px = g.state.px
            val py = g.state.py
            pressA(g)
            repeat(5) { frame(g) }
            if (near(g.state.px, px) && near(g.state.py, py)) {
                println("OK   앉아 있는 동안 A 재입력 무시 (제자리)")
            } else {
                println("FAIL 앉아 있는데 위치가 바뀌었다 (${g.state.px},${g.state.py})")
                failed = true
            }

            // 3.2초 앉기 + 여유 → 벤치 앞에 일어선다
            repeat(180) { frame(g) }
            if (near(g.state.px, standX) && near(g.state.py, standY)) {
                println("OK   시간이 지나 벤치 앞에 일어섰다 (${g.state.px.toInt()},${g.state.py.toInt()})")
            } else {
                println("FAIL 일어나지 않았다 (${g.state.px},${g.state.py} != $standX,$standY)")
                failed = true
            }
            render(g, outDir, "rest_02_stood_up.png")
        }

        // ─────────────────────────────────────────────────────────────
        // D: 앉아 있는 동안 B → 일어나기
        // ─────────────────────────────────────────────────────────────
        run {
            val g = newLandmark()
            walkNearRest(g)
            pressA(g); repeat(10) { frame(g) }; pressA(g)
            repeat(90) { frame(g) }        // 앉은 상태
            pressB(g)
            repeat(5) { frame(g) }
            if (near(g.state.px, standX) && near(g.state.py, standY)) {
                println("OK   B 로 앉은 자세에서 일어섰다 (${g.state.px.toInt()},${g.state.py.toInt()})")
            } else {
                println("FAIL B 로 일어서지 않았다 (${g.state.px},${g.state.py})")
                failed = true
            }
        }

        // ─────────────────────────────────────────────────────────────
        // E: 걸어가는 도중 이동 입력 → 취소
        // ─────────────────────────────────────────────────────────────
        run {
            val g = newLandmark()
            walkNearRest(g)
            pressA(g); repeat(10) { frame(g) }; pressA(g)
            frame(g)
            key(g, KeyEvent.KEYCODE_DPAD_RIGHT, true)
            var touchedSeat = false
            repeat(120) {
                frame(g)
                if (near(g.state.px, seatX) && near(g.state.py, seatY)) touchedSeat = true
            }
            key(g, KeyEvent.KEYCODE_DPAD_RIGHT, false)
            if (!touchedSeat && (!near(g.state.px, seatX) || !near(g.state.py, seatY))) {
                println("OK   이동 입력이 앉기 연출을 취소했다 (좌석에 앉지 않음)")
            } else {
                println("FAIL 이동 입력이 앉기 연출을 취소하지 못했다 (${g.state.px},${g.state.py})")
                failed = true
            }
        }

        if (failed) {
            println("landmark rest smoke FAILED")
            kotlin.system.exitProcess(1)
        }
        println("landmark rest smoke OK")
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private fun newLandmark(): Game {
        val g = Game(TestContext())
        g.onSurfaceChanged(2340, 1080)
        g.state.started = true
        g.scene = LandmarkScene(g, Regions.byId["seoul"]!!)
        repeat(30) { frame(g) }
        return g
    }

    /** 휴게 공간(rest) 반경(30px) 안으로 걸어간다 — 상호작용은 플레이어 중심(cx,cy) 기준 */
    private fun walkNearRest(g: Game) {
        val restX = 3f * 16f
        val restY = 8.2f * 16f
        repeat(400) {
            val dx = restX - (g.state.px + 8f)
            val dy = restY - (g.state.py + 13f)
            if (hypot(dx, dy) < 26f) {
                releaseDirs(g)
                return
            }
            key(g, KeyEvent.KEYCODE_DPAD_LEFT, dx < -2f)
            key(g, KeyEvent.KEYCODE_DPAD_RIGHT, dx > 2f)
            key(g, KeyEvent.KEYCODE_DPAD_UP, dy < -2f)
            key(g, KeyEvent.KEYCODE_DPAD_DOWN, dy > 2f)
            frame(g)
        }
        releaseDirs(g)
        println("FAIL 휴게 공간 앞까지 걷지 못했다 (${g.state.px},${g.state.py})")
        failed = true
    }

    private fun releaseDirs(g: Game) {
        key(g, KeyEvent.KEYCODE_DPAD_LEFT, false)
        key(g, KeyEvent.KEYCODE_DPAD_RIGHT, false)
        key(g, KeyEvent.KEYCODE_DPAD_UP, false)
        key(g, KeyEvent.KEYCODE_DPAD_DOWN, false)
    }

    private fun frame(g: Game) {
        g.update(1f / 60f)
    }

    private fun key(g: Game, code: Int, down: Boolean) {
        g.input.onKeyEvent(code, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP)
    }

    private fun pressA(g: Game) {
        key(g, KeyEvent.KEYCODE_DPAD_CENTER, true)
        frame(g)
        key(g, KeyEvent.KEYCODE_DPAD_CENTER, false)
    }

    private fun pressB(g: Game) {
        key(g, KeyEvent.KEYCODE_X, true)
        frame(g)
        key(g, KeyEvent.KEYCODE_X, false)
    }

    private fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < 1.2f

    private fun render(g: Game, dir: File?, name: String) {
        if (dir == null) return
        val bmp = Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888)
        g.render(Canvas(bmp))
        ImageIO.write(bmp.image, "png", File(dir, name))
        println("  + $name")
    }
}
