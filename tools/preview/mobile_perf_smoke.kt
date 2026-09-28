package com.pizzaandbird.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.GfxStats
import com.pizzaandbird.game.AutoRenderBudget
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.GrassField
import com.pizzaandbird.game.MapBuilder
import com.pizzaandbird.game.MenuOverlay
import com.pizzaandbird.game.Pave
import com.pizzaandbird.game.Regions
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.T
import com.pizzaandbird.game.WorldScene

/** Android 기기 없이 모바일 렌더 최적화의 정합성/회귀를 검사한다. */
object MobilePerfSmoke {
    @JvmStatic fun main(args: Array<String>) {
        // 1~2번의 느린 프레임은 화질 저하를 일으키지 않는다. 지속적인 부하만 감지한다.
        val budget = AutoRenderBudget()
        repeat(89) { check(!budget.observe(16_000_000L, 16_666_666L)) }
        check(!budget.observe(200_000_000L, 200_000_000L))
        repeat(89) { check(!budget.observe(28_000_000L, 33_333_333L)) }
        check(budget.observe(28_000_000L, 33_333_333L))
        repeat(89) { check(!budget.observe(5_000_000L, 33_333_333L)) }
        check(!budget.observe(5_000_000L, 33_333_333L)) // vsync 대기만 길면 화질 유지
        budget.reset()
        repeat(30) { budget.observe(28_000_000L, 33_333_333L) }
        check(!budget.observe(300_000_000L, 300_000_000L)) // 중단/복귀 프레임은 다시 측정
        repeat(89) { check(!budget.observe(5_000_000L, 16_666_666L)) }
        check(!budget.observe(5_000_000L, 16_666_666L))

        val g = Game(Context())
        g.onSurfaceChanged(2340, 1080) // S22+ 가로 화면 (FHD+)
        val virtW = g.virtW
        val hudX = g.hud.mainCx
        // 자동 배율 = 화면 1:1(viewScale 0.5) × 1.15 여유 = 0.575 — FHD에서 2를 고정하던
        // 옛 방식은 7680×4320(3,300만 px)을 매 프레임 그려 입력이 수백 ms 늦어졌다.
        check(g.worldScale == 0.575f) { "auto scale = ${g.worldScale}" }
        check(g.worldBitmap.width == (virtW * 0.575f).toInt()) // 비트맵 ≈ 화면 해상도
        g.scene = WorldScene(g, "seoul", SpawnKind.HOME)
        repeat(89) { g.onFrameRendered(28_000_000L, 33_333_333L) }
        check(g.worldScale == 0.575f)
        g.onFrameRendered(28_000_000L, 33_333_333L)
        // 프레임이 계속 밀리면 20%씩 내려 바닥(0.5)까지 내려간다
        check(g.worldScale == 0.5f) { "downscale = ${g.worldScale}" }
        check(g.hud.mainCx == hudX && g.screenW == 2340) // HUD는 실해상도 유지
        repeat(90) { g.onFrameRendered(28_000_000L, 33_333_333L) }
        check(g.worldScale == 0.5f) // 자동 모드는 화질을 다시 올리지 않아 흔들리지 않는다
        g.state.renderScale = "2"
        g.applyRenderQuality()
        repeat(90) { g.onFrameRendered(28_000_000L, 33_333_333L) }
        check(g.worldScale == 2f) // 수동 고화질은 강제로 낮추지 않는다
        g.state.renderScale = "auto"
        g.applyRenderQuality()
        val sc = g.scene as WorldScene
        sc.openOverlay(MenuOverlay(sc))
        repeat(90) { g.onFrameRendered(28_000_000L, 33_333_333L) }
        check(g.worldScale == 0.575f) // 무거운 메뉴가 월드 자동 화질에 영향을 주지 않는다
        sc.closeOverlay()
        repeat(90) { g.onFrameRendered(28_000_000L, 33_333_333L) }
        check(g.worldScale == 0.5f) { "second downscale = ${g.worldScale}" }

        // 청크는 첫 방문에만 만들고, 물이 있는 타일은 이후에도 애니메이션한다.
        val map = MapBuilder.build(Regions.byId.getValue("seoul"), "seoul")
        val canvas = Canvas(Bitmap.createBitmap(1214, 632, Bitmap.Config.ARGB_8888))
        fun drawGroundCount(): Long {
            GfxStats.reset()
            map.draw(canvas, g.assets, 145f, 183f, 1214, 632, 0.12f)
            return GfxStats.drawBitmap
        }
        val firstDraws = drawGroundCount()
        val cachedDraws = drawGroundCount()
        check(firstDraws > cachedDraws + 40) { "청크 캐시가 지면 호출을 줄이지 않음: $firstDraws -> $cachedDraws" }

        val coast = MapBuilder.build(Regions.byId.getValue("jeju"), "seoul")
        val waterY = (0 until coast.h).first { y -> (0 until coast.w).any { x ->
            coast.groundAt(x, y) == T.WATER && coast.paveAt(x, y) == Pave.NONE
        } }
        val waterX = (0 until coast.w).first { x -> coast.groundAt(x, waterY) == T.WATER && coast.paveAt(x, waterY) == Pave.NONE }
        val firstWater = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val secondWater = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        coast.draw(Canvas(firstWater), g.assets, waterX * 32f, waterY * 32f, 96, 96, 0f)
        coast.draw(Canvas(secondWater), g.assets, waterX * 32f, waterY * 32f, 96, 96, 0.5f)
        check((0 until 32).any { y -> (0 until 32).any { x ->
            firstWater.getPixel(x, y) != secondWater.getPixel(x, y)
        } }) { "물 청크가 정지함" }

        // 풀잎 y 정렬 인덱스: 화면 밖 풀은 적분하지 않고, 돌아왔을 때 즉시 재개한다.
        val grass = GrassField(map)
        val bladesField = grass.javaClass.getDeclaredField("blades").apply { isAccessible = true }
        val blades = bladesField.get(grass) as List<*>
        val lastTime = blades.first()!!.javaClass.getDeclaredField("lastUpdate").apply { isAccessible = true }
        grass.update(1f / 60f, 0.016f, 280f, 280f, false, 200f, 200f, 160f, 160f)
        val updated = blades.filter { lastTime.getFloat(it) > 0f }
        check(updated.isNotEmpty() && updated.size < blades.size / 2) { "보이지 않는 풀까지 적분 중" }
        val visibleBlade = updated.first()!!
        grass.update(1f / 60f, 3f, 850f, 700f, false, 760f, 640f, 160f, 160f)
        check(lastTime.getFloat(visibleBlade) == 0.016f)
        grass.update(1f / 60f, 4f, 280f, 280f, false, 200f, 200f, 160f, 160f)
        check(lastTime.getFloat(visibleBlade) == 4f)

        println("mobile perf smoke OK (ground bitmap draws $firstDraws -> $cachedDraws, updated grass ${updated.size}/${blades.size})")
    }
}
