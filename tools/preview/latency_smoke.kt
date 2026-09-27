package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.GfxStats
import android.graphics.RectF
import android.view.MotionEvent
import com.pizzaandbird.game.*
import java.io.File

/** 버튼 첫 입력과 사진집 페이지 렌더링이 게임 스레드를 막지 않는지 확인한다. */
object LatencySmoke {
    private class TestContext : Context() {
        override val filesDir = File(System.getProperty("java.io.tmpdir"), "pb_latency_${System.nanoTime()}")
            .apply { mkdirs() }
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    private fun tap(g: Game, x: Float, y: Float) {
        val p = listOf(Triple(0, x, y))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_DOWN, pointers = p))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_UP, pointers = p))
    }

    private fun rect(obj: Any, name: String): RectF {
        val field = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
        return field.get(obj) as RectF
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val ctx = TestContext()
        val g = Game(ctx)
        g.onSurfaceChanged(1280, 720)
        val screen = Canvas(Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888))
        fun frame() { g.update(1f / 60f); g.render(screen) }
        frame()

        // 열리던 첫 프레임부터 입력을 받을 수 있어야 한다 (이전에는 90ms 동안 버렸다).
        val scene = g.scene
        var received = 0
        val modal = object : Overlay(scene) {
            override val coversWorld = true
            override fun handleInput(input: Input) {
                if (input.consumeTapScreen() != null) { received++; finished = true }
            }
        }
        scene.openOverlay(modal)
        g.update(1f / 60f)
        GfxStats.reset()
        g.render(screen)
        check(GfxStats.bitmaps == 0L) { "오버레이 등장 때 전체 화면 비트맵이 생성됨" }
        tap(g, 640f, 350f)
        frame()
        check(received == 1 && scene.overlay == null) { "첫 탭이 오버레이 등장 연출 중 버려짐" }
        frame()
        check(received == 1) { "닫힌 오버레이로 입력이 재전달됨" }

        // 아래에서 올라오는 대화창도 보이는 버튼 좌표를 첫 프레임부터 받아야 한다.
        val oldClock = android.os.SystemClock.simMillis
        android.os.SystemClock.simMillis = 1_000L
        var chosen = 0
        val dialog = DialogOverlay(scene, "", "바로 누르기",
            listOf(DialogOverlay.Choice("확인") { chosen++ }))
        scene.openOverlay(dialog)
        frame()
        val choices = dialog.javaClass.getDeclaredField("choiceRects").apply { isAccessible = true }
            .get(dialog) as List<*>
        val button = choices.first() as RectF
        val shift = dialog.enterShift()
        check(shift > 0f) { "등장 중인 버튼의 좌표를 검증하지 못함" }
        tap(g, button.centerX(), button.centerY() + shift)
        frame()
        check(chosen == 1) { "등장 중 보이는 버튼을 눌렀는데 좌표가 어긋나 입력이 무시됨" }

        val detail = BirdDetailOverlay(scene, 2)
        scene.openOverlay(detail)
        frame()
        val nextBird = rect(detail, "nextRect")
        tap(g, nextBird.centerX(), nextBird.centerY() + detail.enterShift())
        frame()
        val currentNum = detail.javaClass.getDeclaredField("currentNum").apply { isAccessible = true }
            .getInt(detail)
        check(currentNum == 3) { "도감 등장 중 보이는 다음 버튼이 반응하지 않음" }
        scene.closeOverlay()

        val backup = BackupOverlay(scene, BackupOverlay.Mode.RESTORE)
        scene.openOverlay(backup)
        frame()
        val closeBackup = rect(backup, "closeRect")
        tap(g, closeBackup.centerX(), closeBackup.centerY() + backup.enterShift() + 12f)
        frame()
        check(backup.finished && scene.overlay == null) { "백업창 등장 중 닫기 버튼이 반응하지 않음" }
        android.os.SystemClock.simMillis = oldClock

        // 처음 본 종을 도감에 그려도 JPEG를 게임 스레드에서 풀지 않고,
        // 기준색 로딩 뒤에는 임시 기본색 스프라이트가 실제 사진색으로 교체돼야 한다.
        val newBird = Birds.ALL.last()
        GfxStats.reset()
        val firstBird = g.assets.bird(newBird.id)
        check(GfxStats.decodesMain == 0L) { "처음 본 새 스프라이트가 게임 스레드에서 사진을 디코드함" }
        var photoPaletteApplied = false
        for (i in 0 until 100) {
            if (g.assets.bird(newBird.id) !== firstBird) { photoPaletteApplied = true; break }
            Thread.sleep(10L)
        }
        check(photoPaletteApplied) { "사진색 로딩 후 새 스프라이트가 갱신되지 않음" }

        // 셔터는 비트맵을 곧장 보여 주되, JPEG 압축을 게임 스레드에서 하지 않아야 한다.
        val shot = Bitmap.createBitmap(720, 405, Bitmap.Config.ARGB_8888)
        shot.eraseColor(0xFF80B5D1.toInt())
        GfxStats.reset()
        val saved = PhotoArchive.save(ctx, "latency_save", shot)
        check(saved.isNotBlank() && PhotoArchive.image(ctx, saved) === shot)
        check(GfxStats.compressionsMain == 0L) { "촬영 버튼이 게임 스레드에서 JPEG를 압축함" }
        PhotoArchive.awaitPendingWrites(3_000L)
        val savedFile = File(ctx.filesDir, "bird_photos/$saved")
        check(savedFile.isFile && savedFile.length() > 0L &&
            android.graphics.BitmapFactory.decodeFile(savedFile.absolutePath) != null) {
            "비동기 사진 파일이 온전히 저장되지 않음"
        }
        PhotoArchive.delete(ctx, saved)
        check(!savedFile.exists()) { "사진 삭제 뒤 파일이 다시 생성됨" }
        val canceled = PhotoArchive.save(ctx, "latency_cancel", shot)
        PhotoArchive.delete(ctx, canceled) // 압축 작업이 대기 중이거나 실행 중일 때도 삭제가 우선
        PhotoArchive.awaitPendingWrites(3_000L)
        check(!File(ctx.filesDir, "bird_photos/$canceled").exists()) { "취소된 사진이 다시 저장됨" }
        val cleared = PhotoArchive.save(ctx, "latency_clear", shot)
        PhotoArchive.clear(ctx)
        PhotoArchive.awaitPendingWrites(3_000L)
        check(!File(ctx.filesDir, "bird_photos/$cleared").exists()) { "초기화된 사진이 다시 저장됨" }

        // 기존 사진을 파일에 쓰되 캐시에는 넣지 않는다 (새 앱에서 사진집을 처음 연 상황).
        val files = ArrayList<File>()
        try {
            val dir = File(ctx.filesDir, "bird_photos").apply { mkdirs() }
            val prefix = "latency_${System.nanoTime()}"
            val bmp = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0xFF80B5D1.toInt())
            repeat(8) { i ->
                val name = "$prefix-$i.jpg"
                val file = File(dir, name)
                file.outputStream().use { check(bmp.compress(Bitmap.CompressFormat.JPEG, 91, it)) }
                files.add(file)
                g.state.photoAlbum.add(BirdPhotoRecord("latency$i", "sparrow", 2, "seoul", 1,
                    12f, "sunny", BirdFacing.LEFT, BirdPose.PERCHED, name, "test", 3f))
            }
            val svgCacheField = g.illustrations.javaClass.getDeclaredField("cache").apply { isAccessible = true }
            val svgCache = svgCacheField.get(g.illustrations) as Map<*, *>
            val uiIcons = ctx.assets.list("ui").orEmpty().filter { it.endsWith(".svg") }
            check(uiIcons.isNotEmpty() && uiIcons.all { svgCache.containsKey("ui/$it") }) {
                "첫 메뉴 진입 전에 SVG 아이콘이 준비되지 않음"
            }
            val menu = MenuOverlay(scene)
            scene.openOverlay(menu)
            frame()
            // 실제 탭으로 사진집 탭으로 전환한다 (리플렉션은 테스트의 히트박스 조회에만 사용).
            val tabsField = menu.javaClass.getDeclaredField("tabRects").apply { isAccessible = true }
            val album = (tabsField.get(menu) as List<*>).filterIsInstance<Pair<*, *>>()
                .first { it.second.toString() == "ALBUM" }.first as RectF
            tap(g, album.centerX(), album.centerY())
            g.update(1f / 60f)
            GfxStats.reset()
            g.render(screen)
            check(GfxStats.decodesMain == 0L) { "사진집 탭 전환 중 게임 스레드 JPEG 디코드" }
            check(PhotoArchive.image(ctx, files[0].name) != null || waitForPhoto(ctx, files[0].name)) {
                "백그라운드에서 사진을 읽어오지 못함"
            }

            // 미리 안 읽힌 다음 페이지 사진을 상세 보기로 열어도 프레임을 막지 않는다.
            android.os.SystemClock.simMillis = 2_000L
            val viewer = PhotoAlbumViewerOverlay(scene, "latency7")
            check(viewer.coversWorld) { "사진 상세 화면이 뒤의 월드를 매 프레임 다시 그림" }
            scene.openOverlay(viewer)
            g.update(1f / 60f)
            GfxStats.reset()
            g.render(screen)
            check(GfxStats.decodesMain == 0L) { "사진 넘김 중 게임 스레드 JPEG 디코드" }
            check(waitForPhoto(ctx, files[7].name)) { "다음 페이지 사진 미리 읽기 실패" }
            val close = rect(viewer, "closeR")
            check(viewer.enterShift() > 0f) { "사진 상세 보기 등장 좌표를 검증하지 못함" }
            tap(g, close.centerX(), close.centerY() + viewer.enterShift() + 8f)
            frame()
            check(viewer.finished) { "사진 상세 보기 등장 중 닫기 버튼이 반응하지 않음" }
            android.os.SystemClock.simMillis = oldClock
        } finally {
            PhotoArchive.clear(ctx)
            ctx.filesDir.deleteRecursively()
        }
        println("Latency smoke: immediate/animated taps, no full-screen layer, preloaded icons, async photos OK")
    }

    private fun waitForPhoto(ctx: Context, name: String): Boolean {
        repeat(100) {
            if (PhotoArchive.image(ctx, name) != null) return true
            Thread.sleep(10L)
        }
        return false
    }
}
