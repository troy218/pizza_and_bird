package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.GameState
import com.pizzaandbird.game.MapBuilder
import com.pizzaandbird.game.Regions
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.T
import com.pizzaandbird.game.WorldScene
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * 벤치 앉기 연출 회귀 테스트 — "자전거에서 내려 실제로 걸어가 앉는다".
 *
 * 실제 Game/Input/WorldScene 코드에 키 입력을 넣어 다음을 확인한다.
 *   A. 자전거를 탄 채 A 를 누르면 자전거에서 내린다 (state.onBike == false)
 *   B. 벤치 앞까지 실제로 걸어간다 (state.px/py 가 목표로 수렴)
 *   C. 좌석 위에서 앉아 쉰다 (state.px/py == 벤치 타일)
 *   D. [BENCH_SIT_HOLD] 초가 지나면 벤치 앞에 서서 일어난다
 *   E. 걸어가는 도중 이동 입력을 주면 연출이 취소된다 (좌석에 앉지 않는다)
 *
 * 프리뷰 스텁과 게임 소스를 함께 컴파일한 뒤 실행:
 *   java -cp classes-preview:kotlin-stdlib.jar \
 *     com.pizzaandbird.preview.BenchSitSmoke [프레임PNG저장폴더]
 */
object BenchSitSmoke {

    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    private const val DT = 1f / 60f

    private fun frame(g: Game) {
        g.update(DT)
    }

    private fun key(g: Game, code: Int, down: Boolean) {
        g.input.onKeyEvent(code, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP)
    }

    /** A 버튼 한 번 누르기 (키보드 Z 와 동일) */
    private fun pressA(g: Game) {
        key(g, KeyEvent.KEYCODE_DPAD_CENTER, true)
        frame(g)
        key(g, KeyEvent.KEYCODE_DPAD_CENTER, false)
    }

    private fun render(g: Game, dir: File?, name: String) {
        if (dir == null) return
        val bmp = Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888)
        g.render(Canvas(bmp))
        ImageIO.write(bmp.image, "png", File(dir, name))
        if (System.getenv("PB_DBG") != null) {
            val cam = g.scene.cameraOffset()
            val sx = (g.state.px - cam.x) * 2f
            val sy = (g.state.py - cam.y) * 2f
            println("  [dbg] $name player world=(${g.state.px},${g.state.py}) screen=($sx,$sy)")
        }
        println("  + $name")
    }

    /** 벤치 타일과 그 앞(아래) 타일이 모두 쓸 만하고, NPC 가 A 를 가로채지 않을 자리를 찾는다 */
    private fun findBench(): BenchSpot {
        val home = GameState().homeRegion
        for (rid in listOf("seoul", "sokcho", "busan", "jeju", "chuncheon")) {
            val map = MapBuilder.build(Regions.byId[rid] ?: continue, home)
            for (y in 1 until map.h - 1) {
                for (x in 1 until map.w - 1) {
                    if (map.t(x, y) != T.BENCH || !map.walkableTile(x, y + 1)) continue
                    // A 버튼은 NPC 대화가 우선 — 64px 안에 주민이 없어야 벤치가 반응한다
                    val sx = x * 16f + 8f
                    val sy = (y + 1) * 16f + 13f
                    val npcNear = map.npcs.any {
                        kotlin.math.hypot((it.cx - sx).toDouble(), (it.cy - sy).toDouble()) < 64.0
                    }
                    if (npcNear) continue
                    println("bench at $rid ($x,$y)")
                    return BenchSpot(rid, x, y)
                }
            }
        }
        throw IllegalStateException("벤치를 찾지 못했다")
    }

    private class BenchSpot(val regionId: String, val bx: Int, val by: Int)

    private fun near(a: Float, b: Float): Boolean = abs(a - b) < 1.2f

    private fun newGameAt(spot: BenchSpot, onBike: Boolean): Game {
        val g = Game(TestContext())
        g.onSurfaceChanged(2340, 1080)
        val map = MapBuilder.build(Regions.byId[spot.regionId]!!, GameState().homeRegion)
        // 벤치 옆(트리거 3x3 안)이면서 벽에 끼지 않는 자리 — 걸어갈 여유가 남는다
        val candidates = listOf(-14f to 0f, -14f to -2f, -12f to 4f, 12f to 0f, 0f to 10f)
        val (ax, ay) = candidates.first { (dx, dy) ->
            !map.solidBox(spot.bx * 16f + dx, (spot.by + 1) * 16f + dy)
        }
        g.state.px = spot.bx * 16f + ax
        g.state.py = (spot.by + 1) * 16f + ay
        g.state.onBike = onBike
        g.state.started = true
        g.scene = WorldScene(g, spot.regionId, SpawnKind.SAVED)
        repeat(30) { frame(g) }
        return g
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = args.getOrNull(0)?.let { File(it).also { d -> d.mkdirs() } }
        var failed = false
        val spot = findBench()
        val bx = spot.bx
        val by = spot.by
        val seatX = bx * 16f
        val seatY = by * 16f
        val standX = bx * 16f
        val standY = (by + 1) * 16f

        // ---- A~D. 자전거 탄 채 앉기 → 내리기 → 걷기 → 앉기 → 일어나기 --------
        run {
            // 벤치 옆(트리거 3x3 안)에서 시작 — 앉기 연출이 걸어가는 여유를 남긴다
            var g = newGameAt(spot, onBike = true)
            check(abs(g.state.px - seatX) > 4f) { "시작 위치가 좌석 위여서 걷기 검증이 안 된다" }
            render(g, outDir, "sit_01_bike.png")
            check(g.state.onBike) { "시작 상태가 자전거가 아니다" }

            pressA(g)
            frame(g)
            if (g.state.onBike) {
                println("FAIL A 를 눌러도 자전거에서 내리지 않는다")
                failed = true
            } else {
                println("OK   자전거에서 내렸다 (state.onBike = false)")
            }

            // 1.5초 뒤엔 걸어간 뒤 앉아 있는 중이어야 한다 (걷기 ~0.5초 + 앉기 3.2초)
            repeat(90) { frame(g) }
            if (near(g.state.px, seatX) && near(g.state.py, seatY)) {
                println("OK   벤치까지 걸어가 좌석에 앉았다 (${g.state.px.toInt()},${g.state.py.toInt()})")
            } else {
                println("FAIL 좌석에 앉지 않았다 (${g.state.px},${g.state.py} != $seatX,$seatY)")
                failed = true
            }
            render(g, outDir, "sit_02_sitting.png")

            // 앉아 있는 동안 A 를 눌러도 다시 앉지 않는다 (제자리 유지)
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

            // D. 3.2초 + 여유 — 자동으로 일어나 벤치 앞에 선다
            repeat((3.2f / DT).toInt() + 30) { frame(g) }
            if (near(g.state.px, standX) && near(g.state.py, standY)) {
                println("OK   시간이 지나 벤치 앞에 일어섰다 (${g.state.px.toInt()},${g.state.py.toInt()})")
            } else {
                println("FAIL 일어나지 않았다 (${g.state.px},${g.state.py} != $standX,$standY)")
                failed = true
            }
            render(g, outDir, "sit_03_stood_up.png")
        }

        // ---- E. 걸어가는 도중 이동하면 연출 취소 ------------------------------
        run {
            val g = newGameAt(spot, onBike = true)
            pressA(g)
            frame(g)
            // 곧바로 오른쪽 이동 입력 — 걸어가는 연출이 끊기고 플레이어가 움직여야 한다
            key(g, KeyEvent.KEYCODE_DPAD_RIGHT, true)
            var touchedSeat = false
            repeat(150) {
                frame(g)
                if (near(g.state.px, seatX) && near(g.state.py, seatY)) touchedSeat = true
            }
            key(g, KeyEvent.KEYCODE_DPAD_RIGHT, false)
            if (!touchedSeat && (abs(g.state.px - standX) > 2f || abs(g.state.py - standY) > 2f)) {
                println("OK   이동 입력으로 앉기 연출 취소 (좌석에 앉지 않음)")
            } else {
                println("FAIL 이동 입력이 앉기 연출을 취소하지 못했다 (${g.state.px},${g.state.py})")
                failed = true
            }
        }

        // ---- 자전거 없이 앉기 (걷기만) ----------------------------------------
        run {
            val g = newGameAt(spot, onBike = false)
            pressA(g)
            repeat(90) { frame(g) }   // 1.5초 — 걸어간 뒤 앉아 있는 중
            if (near(g.state.px, seatX) && near(g.state.py, seatY)) {
                println("OK   걸어서 좌석에 앉았다")
            } else {
                println("FAIL 걸어서 앉지 않았다 (${g.state.px},${g.state.py})")
                failed = true
            }
        }

        if (failed) {
            println("bench sit smoke FAILED")
            System.exit(1)
        }
        println("bench sit smoke OK")
    }

    private fun hypotAbs(a: Float, b: Float): Float = kotlin.math.sqrt(a * a + b * b)
}
