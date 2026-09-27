package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.GfxStats
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.MenuOverlay
import com.pizzaandbird.game.Scene
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.WorldScene

/**
 * "가리는 오버레이가 떠 있는 동안 월드를 적게 다시 그린다" 최적화의 정합성 프로브.
 *
 * Game.render() 는 화면을 덮는 오버레이(가방·지도·상점…)가 떠 있는 동안 월드
 * 비트맵을 매 프레임 다시 그리지 않는다. 월드는 프레임당 drawBitmap 이 약 2000회라
 * 이게 버튼이 붙는 지의 주원인이었고, 결과는 세 가지라 각각 확인한다.
 *
 *   A. 가방이 떠 있는 동안 월드가 매 프레임 그려지지 않는다 (병목이 실제로 줄었다)
 *   B. 가방을 닫은 다음 프레임에 월드가 다시 그려진다 (오래된 화면을 붙여 보여주지 않는다)
 *   C. 그 뒤에도 월드가 계속 갱신된다 (멈춘 월드가 아니다)
 *
 * 판정 기준은 월드 redraw 를 유추할 수 있는 GfxStats.drawBitmap 횟수다
 * (월드를 그리면 약 2000회, 월드만 건너뛰면 HUD 정도만 그려진다).
 */
object WorldResumeSmoke {

    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    /** [2340,1080] 기기 크기로 게임을 만들어 서울 월드까지 진입한다. */
    private fun newSeoulGame(): Pair<Game, Canvas> {
        val g = Game(TestContext())
        g.onSurfaceChanged(2340, 1080)
        val c = Canvas(Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888))
        g.scene = WorldScene(g, "seoul", SpawnKind.HOME)
        repeat(60) { g.update(1f / 60f) }
        g.render(c)
        return g to c
    }

    private fun oneFrameDrawBitmaps(g: Game, c: Canvas): Double {
        g.update(1f / 60f)
        GfxStats.reset()
        g.render(c)
        return GfxStats.drawBitmap.toDouble()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        var failed = false

        // ---- 기준값: 오버레이 없이 월드를 그리는 프레임 --------------------
        val (g, c) = newSeoulGame()
        var worldFrameDrops = 0
        repeat(5) { worldFrameDrops += oneFrameDrawBitmaps(g, c).toInt() }
        val worldPerFrame = worldFrameDrops / 5.0

        // ---- A. 가방이 떠 있는 동안 월드를 매 프레임 그리지 않는다 ---------
        val menu = MenuOverlay(g.scene as Scene)
        (g.scene as Scene).openOverlay(menu)
        repeat(30) { g.update(1f / 60f) }        // 등장 연출 포함 0.5초
        var coveredDrops = 0
        repeat(10) { coveredDrops += oneFrameDrawBitmaps(g, c).toInt() }
        val coveredPerFrame = coveredDrops / 10.0

        if (coveredPerFrame < worldPerFrame * 0.6) {
            println("OK   가방이 떠 있는 동안 월드 재생성 감소 ${"%.0f".format(coveredPerFrame)} < " +
                "${"%.0f".format(worldPerFrame)} (drawBitmap/frame)")
        } else {
            println("FAIL 가방이 떠 있어도 월드를 매 프레임 그린다 " +
                "(${"%.0f".format(coveredPerFrame)} vs ${"%.0f".format(worldPerFrame)})")
            failed = true
        }

        // ---- B. 닫은 다음 프레임에 월드가 다시 그려진다 --------------------
        (g.scene as Scene).closeOverlay()
        val closeFrame = oneFrameDrawBitmaps(g, c)
        if (closeFrame > worldPerFrame * 0.6) {
            println("OK   가방을 닫자 곧바로 월드가 다시 그려짐 (${"%.0f".format(closeFrame)} drawBitmap)")
        } else {
            println("FAIL 가방을 닫아도 월드가 즉시 갱신되지 않는다 ($closeFrame drawBitmap) " +
                "— 오버레이를 열기 전 화면이 그대로 보인다")
            failed = true
        }

        // ---- C. 이후에도 매 프레임 다시 그려진다 ---------------------------
        val steady = oneFrameDrawBitmaps(g, c)
        if (steady > worldPerFrame * 0.6) {
            println("OK   가방 닫힌 뒤에도 매 프레임 월드 갱신 (${"%.0f".format(steady)} drawBitmap)")
        } else {
            println("FAIL 가방 닫힌 뒤 월드가 멈췄다 ($steady drawBitmap)")
            failed = true
        }

        if (failed) {
            println("world resume smoke FAILED")
            System.exit(1)
        }
        println("world resume smoke OK")
    }
}
