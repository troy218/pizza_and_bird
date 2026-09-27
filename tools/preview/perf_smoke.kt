package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.GfxStats
import android.view.MotionEvent
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.Input
import com.pizzaandbird.game.Overlay
import com.pizzaandbird.game.MenuOverlay
import com.pizzaandbird.game.Scene
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.WorldScene
import java.lang.management.ManagementFactory
import kotlin.math.max

/**
 * 버튼 입력 병목 프로브 — 실제 게임 코드를 그대로 돌려 프레임 비용을 잰다.
 *
 * 측정 대상(사용자가 "누르면 렉이 걸린다"고 느낀 지점):
 *   1) 월드          — 오버레이가 없을 때의 기준 프레임
 *   2) 가방(메뉴)      — 8개 탭 각각의 프레임 비용 + 매 프레임 새로 만들어 나는 네이티브 객체
 *   3) 엑스 버튼     — 터치 → 오버레이가 닫힐 때까지 몇 프레임/얼마나 걸리는가
 *   4) 탭 전환         — 도감(사진) 탭을 여는 프레임 (JPEG 디코드 포함)
 *   5) 상세 도감 페이징 — 새 화살표 버튼을 누를 때의 고화질 사진 디코드 비용
 *
 * 주의: 이 파이프라인은 Java2D 스텁이라 절대 시간은 안드로이드 기기와 다르다.
 * "프레임당 몇 ms"보다 "프레임당 몇 번의 네이티브 객체 생성과 드로우 콜"이
 * 기기 성능에 그대로 옮겨가는 지표라서, 그 수치를 본다.
 *
 * 사용법은 tools/preview/README.md 의 `성능 프로브` 절 참고.
 */
object PerfSmoke {

    // ---- 측정용 컨텍스트 -------------------------------------------------
    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    // ---- 스레드 할당량(GC 압력) ------------------------------------------
    private val threadMx = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean

    private fun allocatedBytes(): Long {
        val mx = threadMx ?: return 0L
        if (!mx.isThreadAllocatedMemoryEnabled) mx.isThreadAllocatedMemoryEnabled = true
        return mx.getThreadAllocatedBytes(Thread.currentThread().id)
    }

    // ---- 프레임 유틸 -----------------------------------------------------
    private lateinit var g: Game
    private lateinit var screenCanvas: Canvas

    private fun frame() = g.update(1f / 60f)

    /** 오버레이는 한 번 닫히면 finished 가Stick 되므로 다시 열려면 되돌려 둔다. */
    private fun openOverlay(o: Overlay) {
        setField(o, "finished", false)
        (g.scene as Scene).openOverlay(o)
        repeat(10) { frame(); render() }
    }
    private fun render() = g.render(screenCanvas)

    private fun tap(x: Float, y: Float) {
        val p = listOf(Triple(0, x, y))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_DOWN, pointers = p))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_UP, pointers = p))
    }

    /** 지정 프레임 수를 돌리면서 평균/최악 프레임 비용과 오브젝트 생성 수를 잰다. */
    private fun measure(label: String, frames: Int, warmup: Int = 3) {
        repeat(warmup) { frame(); render() }
        GfxStats.reset()
        val a0 = allocatedBytes()
        var worst = 0L
        var total = 0L
        repeat(frames) {
            val t0 = System.nanoTime()
            frame(); render()
            val dt = System.nanoTime() - t0
            total += dt
            if (dt > worst) worst = dt
        }
        val bytes = allocatedBytes() - a0
        println(
            "%-26s avg %6.2f ms  worst %6.2f ms  alloc/frame %7.1f KB".format(
                label, total / 1_000_000.0 / frames, worst / 1_000_000.0, bytes / 1024.0 / frames
            )
        )
        println("%-26s %s".format("", GfxStats.perFrame(frames)))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        g = Game(TestContext())
        g.onSurfaceChanged(2340, 1080)          // 19.5:9 휴대전화
        screenCanvas = Canvas(Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888))

        // 타이틀 → 캐릭터 → 지역 선택 → 서울 월드
        tap(1170f, 650f); repeat(40) { frame() }
        render()
        tap(g.viewOffX + 645f * 2f, 310f * 2f); repeat(40) { frame() }
        render()
        tap(g.viewOffX + 600f, g.screenH - 60f); repeat(40) { frame() }
        render()
        g.scene = WorldScene(g, "seoul", SpawnKind.HOME)
        repeat(30) { frame() }
        render()
        check(g.scene is WorldScene) { "월드 진입 실패" }

        println("=== 기준: 월드 (오버레이 없음) ===")
        measure("world idle", 20)

        // ---- 가방(메뉴) 8탭 -------------------------------------------
        val scene = g.scene as Scene
        val menu = MenuOverlay(scene)
        scene.openOverlay(menu)
        repeat(10) { frame(); render() }   // 등장 연출(0.09초) 동안 입력은 막힌다
        val tabs = Class.forName("com.pizzaandbird.game.MenuOverlay\$Tab").enumConstants
        println()
        println("=== 가방(메뉴) 탭별 프레임 ===")
        tabs.forEachIndexed { i, tab ->
            setField(menu, "tab", tab)
            measure("menu tab ${i + 1} ($tab)", 12)
        }

        // ---- 엑스 버튼: 누르면 몇 프레임 만에 닫히나 ---------------
        // 닫기 단추는 헤더 오른쪽 위(플랩 중앙 높이) — 실제 히트 영역을 그려서 찾는다.
        val closeHit = findCloseButton(scene, menu)
        println()
        println("=== 가방 ✕ 버튼 ===")
        println("닫기 단추 중심: $closeHit")
        // 상태 탭으로 되돌린 뒤 연속으로 눌러 본다
        setField(menu, "tab", tabs[0])
        repeat(6) { frame(); render() }
        var worstPress = 0L
        repeat(10) { trial ->
            openOverlay(menu)
            val t0 = System.nanoTime()
            tap(closeHit.first, closeHit.second)
            var n = 0
            while (scene.overlay === menu && n < 12) { frame(); render(); n++ }
            val dt = System.nanoTime() - t0
            if (dt > worstPress) worstPress = dt
            if (trial == 0) {
                println("누름 → 닫힘까지 ${n + 1}프레임, ${"%.2f".format(dt / 1_000_000.0)} ms")
            }
            if (scene.overlay === menu) scene.closeOverlay()
            repeat(4) { frame(); render() }
        }
        println("✕ 버튼 최악: ${"%.2f".format(worstPress / 1_000_000.0)} ms (12프레임 제한 내 닫힘: ${scene.overlay !== menu})")

        // ---- 도감 탭 전환 (JPEG 디코드) --------------------------------
        println()
        println("=== 도감(사진) 탭 전환 ===")
        openOverlay(menu)
        setField(menu, "tab", tabs[4])
        GfxStats.reset()
        var t0 = System.nanoTime()
        frame(); render()
        val first = System.nanoTime() - t0
        println("탭 전환 직후 첫 프레임: ${"%.2f".format(first / 1_000_000.0)} ms  ${GfxStats.line()}")
        measure("book steady", 12)

        // 다음 페이지 버튼을 눌렀을 때 (새 12장 사진 디코드)
        val next = findPager(scene, menu)
        println("다음 페이지 버튼: $next")
        if (next != null) {
            GfxStats.reset()
            t0 = System.nanoTime()
            tap(next.first, next.second)
            frame(); render()
            val dt = System.nanoTime() - t0
            println("페이지 넘김 프레임: ${"%.2f".format(dt / 1_000_000.0)} ms  ${GfxStats.line()}")
        }

        // ---- 새 상세 도감(고화질 사진) ----------------------------------
        println()
        println("=== 새 상세 도감 페이징 ===")
        val detail = com.pizzaandbird.game.BirdDetailOverlay(scene, 300)
        scene.openOverlay(detail)
        repeat(8) { frame(); render() }
        measure("bird detail", 8)
        val nextBtn = findDetailNext(scene, detail)
        println("다음 새 버튼: $nextBtn")
        if (nextBtn != null) {
            var worstPage = 0L
            repeat(6) { i ->
                GfxStats.reset()
                val t = System.nanoTime()
                tap(nextBtn.first, nextBtn.second)
                frame(); render()
                val dt = System.nanoTime() - t
                if (dt > worstPage) worstPage = dt
                if (i == 0) println("다음 새 프레임: ${"%.2f".format(dt / 1_000_000.0)} ms  ${GfxStats.line()}")
                repeat(3) { frame(); render() }
            }
            println("새 넘김 최악: ${"%.2f".format(worstPage / 1_000_000.0)} ms")
        }
        scene.closeOverlay()

        // ---- 여러 버튼 연타 (HUD) ---------------------------------------
        println()
        println("=== HUD 버튼 연타 (오버레이 없이) ===")
        scene.closeOverlay()
        repeat(8) { frame(); render() }
        GfxStats.reset()
        val a0 = allocatedBytes()
        t0 = System.nanoTime()
        val pts = listOf(
            g.hud.mainCx to g.hud.mainCy,
            g.hud.bikeCx to g.hud.bikeCy,
            g.hud.camBCx to g.hud.camBCy,
            g.hud.eatCx to g.hud.eatCy,
            g.hud.punchCx to g.hud.punchCy,
            g.hud.menuCx to g.hud.menuCy,
        )
        repeat(6) {
            for ((x, y) in pts) { tap(x, y); frame(); render() }
        }
        val dt2 = System.nanoTime() - t0
        println(
            "버튼 ${pts.size * 6}회: 총 ${"%.1f".format(dt2 / 1_000_000.0)} ms  " +
                "평균 ${"%.2f".format(dt2 / 1_000_000.0 / (pts.size * 6))} ms/버튼  " +
                "할당 ${(allocatedBytes() - a0) / 1024 / (pts.size * 6)} KB/버튼"
        )
        println(GfxStats.line())
        // ---- 세이브 비용 (구매·설정 변경 버튼이 동기 호출한다) -------------
        println()
        println("=== 세이브(버튼 액션이 동기 호출) ===")
        val all = com.pizzaandbird.game.Birds.ALL
        for (d in all) g.state.birdCounts[d.id] = 7
        g.state.bestStars.putAll(all.associate { it.id to 3 })
        g.state.started = true
        g.state.visited.addAll(com.pizzaandbird.game.Regions.ALL.map { it.id })
        val json = g.state.toJSON().toString()
        println("JSON ${json.length} chars")
        repeat(5) { com.pizzaandbird.game.SaveManager.save(g.context, g.state) }
        var worstSave = 0L
        var totalSave = 0L
        repeat(12) {
            val t = System.nanoTime()
            com.pizzaandbird.game.SaveManager.save(g.context, g.state)
            val dt = System.nanoTime() - t
            totalSave += dt
            if (dt > worstSave) worstSave = dt
        }
        println("SaveManager.save 평균 ${"%.2f".format(totalSave / 1_000_000.0 / 12)} ms  최악 ${"%.2f".format(worstSave / 1_000_000.0)} ms")

        println("Perf smoke: 완료 (프레임당 오브젝트 수를 tools/preview/README.md 와 비교해 보자)")
    }

    // ---- 리플렉션 헬퍼 (preview_main 과 동일) -----------------------------
    private fun setField(obj: Any, name: String, value: Any?) {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                f.set(obj, value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            } catch (_: Exception) {
                return
            }
        }
    }

    private fun getField(obj: Any?, name: String): Any? {
        if (obj == null) return null
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    private fun rectOf(o: Any?, field: String): android.graphics.RectF? = getField(o, field) as? android.graphics.RectF

    /** 가방 헤더의 ✕ 단추 중심을 그려진 화면에서 찾는다 (입력 히트박스와 같은 위치). */
    private fun findCloseButton(scene: Scene, menu: Any): Pair<Float, Float> {
        val r = rectOf(menu, "closeRect")
        if (r != null && r.width() > 0f) return r.centerX() to r.centerY()
        // 폴백: 헤더 오른쪽 위 (플랩 높이 38dp 기준)
        val d = scene.game.density
        val w = scene.game.screenW.toFloat()
        return (w - d * 32f) to (scene.game.screenH * 0.06f + d * 22f)
    }

    private fun findPager(scene: Scene, menu: Any): Pair<Float, Float>? {
        val rects = getField(menu, "btnRects") as? List<*> ?: return null
        val next = rects.firstOrNull {
            val t = it as? Triple<*, *, *> ?: return@firstOrNull false
            val label = t.second as? String
            label != null && label.contains("다음")
        } ?: return null
        val r = (next as Triple<*, *, *>).first as? android.graphics.RectF ?: return null
        return max(0f, r.centerX()) to r.centerY()
    }

    private fun findDetailNext(scene: Scene, detail: Any): Pair<Float, Float>? {
        val r = rectOf(detail, "nextRect") ?: return null
        if (r.width() <= 0f) return null
        return r.centerX() to r.centerY()
    }
}
