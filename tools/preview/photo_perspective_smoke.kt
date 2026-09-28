package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.GfxStats
import android.graphics.Paint
import android.graphics.RectF
import com.pizzaandbird.game.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot

/** 실제 촬영 경로와 눈높이 투영/배경/방위/인화/사진집 저장 회귀 검사 + 지역별 시각 샘플. */
object PhotoPerspectiveSmoke {
    private class TestContext : Context() {
        override val filesDir = File("tools/preview/out/photo-test-files").apply { mkdirs() }
        override val resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 1.5f }
        }
    }

    private fun near(a: Float, b: Float) = check(abs(a - b) < 0.01f) { "$a != $b" }

    private fun projectionChecks() {
        val camera = PhotoCamera(720, 405, 0f, 5f, 0f, 0f, 1f, 1f)
        val nearBase = camera.project(PhotoVertex(0f, 0f, 0f))!!
        val nearTop = camera.project(PhotoVertex(0f, 1f, 0f))!!
        val farBase = camera.project(PhotoVertex(0f, 0f, -5f))!!
        val farTop = camera.project(PhotoVertex(0f, 1f, -5f))!!
        near(nearBase.y - nearTop.y, (farBase.y - farTop.y) * 2f)
        near(nearBase.x, 360f)
        check(farBase.y < nearBase.y && farBase.y > camera.horizon)
        for (z in listOf(0f, -5f, -20f))
            near(camera.project(PhotoVertex(0f, camera.eyeHeight, z))!!.y, camera.horizon)
        val leftNear = camera.project(PhotoVertex(-1f, 0f, 0f))!!
        val leftFar = camera.project(PhotoVertex(-1f, 0f, -15f))!!
        check(abs(leftFar.x - 360f) < abs(leftNear.x - 360f)) { "지면선이 소실점으로 수렴하지 않음" }
        check(camera.project(PhotoVertex(0f, 0f, 6f)) == null)
        check(camera.facing(BirdFacing.FRONT) == BirdFacing.FRONT)
        check(PhotoCamera(720, 405, 0f, -5f, 0f, 0f, 1f, 1f).facing(BirdFacing.FRONT) == BirdFacing.BACK)
        check(PhotoCamera(720, 405, 5f, 0f, 0f, 0f, 1f, 1f).facing(BirdFacing.RIGHT) == BirdFacing.FRONT)
        check(PhotoCamera(720, 405, -5f, 0f, 0f, 0f, 1f, 1f).facing(BirdFacing.RIGHT) == BirdFacing.BACK)
        val clipped = camera.clip(listOf(PhotoVertex(-1f, 0f, 6f), PhotoVertex(1f, 0f, 0f), PhotoVertex(-1f, 2f, 0f)))
        check(clipped.size >= 3 && clipped.all { camera.project(it) != null })
        check(camera.clip(listOf(PhotoVertex(-1f, 0f, 6f), PhotoVertex(1f, 0f, 7f), PhotoVertex(0f, 1f, 8f))).isEmpty())
        for (aspect in listOf(0.4f, 1f, 3.5f)) {
            val framed = PhotoCamera(720, 405, 0f, 8f, 0f, 0f, 1f, aspect)
            val size = framed.project(PhotoVertex(0f, 0f, 0f))!!.y - framed.project(PhotoVertex(0f, 1f, 0f))!!.y
            check(size <= 405f * 0.50f + 0.01f && size * aspect <= 720f * 0.40f + 0.01f)
        }
        for (distance in listOf(0f, 0.0001f, 0.5f, 1.5f, 12f)) {
            val close = PhotoCamera(720, 405, 0f, distance, 0f, 0f, 0.7f, 2.8f)
            val p = close.project(PhotoVertex(0f, 0f, 0f))!!
            check(p.x.isFinite() && p.y.isFinite() && p.depth > close.near)
        }
        println("Projection: eye-level horizon / inverse depth / vanishing point / all bearings / near clipping OK")
    }

    private fun birdAt(g: Game, id: String, x: Float, z: Float, facing: BirdFacing = BirdFacing.LEFT): FieldBird {
        val def = Birds.byId.getValue(id)
        val sprite = g.assets.birdPose(id, facing, BirdPose.PERCHED)
        return FieldBird(def, 0f, 0f).apply {
            sprW = (sprite.width * 0.5f / WORLD_SCALE).toInt().coerceAtLeast(8)
            sprH = (sprite.height * 0.5f / WORLD_SCALE).toInt().coerceAtLeast(8)
            this.x = x * 16f - sprW / 2f
            this.y = z * 16f - sprH
            this.facing = facing
            idleT = 100f
        }
    }

    private fun signature(b: Bitmap): Long {
        var code = 1L
        for (y in 0 until b.height step 5) for (x in 0 until b.width step 5)
            code = code * 31 + b.getPixel(x, y)
        return code
    }

    private fun sceneryChecks(g: Game) {
        fun field(treeZ: Int?): GameMap {
            val tiles = Array(18) { IntArray(18) { T.GRASS.ordinal } }
            if (treeZ != null) tiles[treeZ][8] = T.TREE.ordinal
            return GameMap(Regions.byId.getValue("seoul"), 18, 18, tiles,
                Array(18) { IntArray(18) { T.GRASS.ordinal } },
                Array(18) { IntArray(18) }, Array(18) { IntArray(18) }, emptyList(), false, -1, -1)
        }
        val bird = birdAt(g, "sparrow", 8.5f, 8.5f)
        val alphaBefore = g.assets.sprPaint.alpha
        fun shot(treeZ: Int?) = PerspectivePhoto.capture(g.assets, field(treeZ), bird,
            8.5f * 16f, 13.5f * 16f, 12f, Weather.SUNNY, Season.SPRING, 3f).bitmap
        val empty = shot(null)
        val cover = shot(11)
        val behind = shot(4)
        check(signature(empty) == signature(cover)) { "가까운 엄폐물이 사진 전체/새를 가림" }
        check(signature(empty) != signature(behind)) { "새 뒤 실제 지형물이 사진에 반영되지 않음" }
        for (y in 199..205) for (x in 357..363)
            check(empty.getPixel(x, y) == behind.getPixel(x, y)) { "뒤쪽 메시가 새 위에 그려짐" }
        check(g.assets.sprPaint.alpha == alphaBefore && bird.facing == BirdFacing.LEFT && bird.state == 0)
        val left = g.assets.birdPose("sparrow", BirdFacing.LEFT)
        val right = g.assets.birdPose("sparrow", BirdFacing.RIGHT)
        check(left.width == right.width && left.height == right.height)
        for (y in 0 until left.height) for (x in 0 until left.width)
            check(left.getPixel(x, y) == right.getPixel(left.width - 1 - x, y)) { "오른쪽 새가 비거나 잘림" }
        println("Scenery: actual map / cover / depth sorting / shared state / mirrored sprite OK")
    }

    private fun overview(out: File, images: List<Bitmap>) {
        val sheet = Bitmap.createBitmap(1512, 1040, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFFF4F3EC.toInt())
        val text = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true)
        text.color = 0xFF2C4338.toInt(); text.textSize = 34f
        c.drawText("새의 눈높이에서 찍는 3D 사진", 24f, 48f, text)
        text.color = 0xFF66756D.toInt(); text.textSize = 19f
        c.drawText("실제 촬영 방향 · 높이를 가진 지형 · 멀어지는 배경과 수평선", 24f, 80f, text)
        val captions = listOf("서울 · 도시 산책로", "제주 · 해안과 수평선", "광릉숲 · 나무 사이", "순천만 · 갈대 습지")
        val picks = listOf(0, 2, 3, 4)
        for (i in picks.indices) {
            val x = 24f + (i % 2) * 744f
            val y = 108f + (i / 2) * 467f
            c.drawBitmap(images[picks[i]], x, y, null)
            text.color = 0xFF374F42.toInt(); text.textSize = 21f
            c.drawText(captions[i], x, y + 434f, text)
        }
        ImageIO.write(sheet.image, "png", File(out, "14_perspective_overview.png"))
    }

    private fun framingCheck(scene: Scene) {
        val marked = Bitmap.createBitmap(720, 405, Bitmap.Config.ARGB_8888)
        val ink = Paint().apply { color = 0xFFE34444.toInt() }
        val source = Canvas(marked)
        source.drawColor(0xFF759C88.toInt())
        source.drawRect(0f, 0f, 24f, 405f, ink)
        ink.color = 0xFF3E6ACB.toInt()
        source.drawRect(696f, 0f, 720f, 405f, ink)
        val overlay = PhotoResultOverlay(scene, Birds.byId.getValue("sparrow"), 3, false, 1, null, capturedPhoto = marked)
        val target = Bitmap.createBitmap(300, 220, Bitmap.Config.ARGB_8888)
        overlay.javaClass.getDeclaredMethod("drawPhoto", Canvas::class.java, RectF::class.java)
            .apply { isAccessible = true }.invoke(overlay, Canvas(target), RectF(10f, 10f, 290f, 210f))
        check(target.getPixel(12, 110) == 0xFFE34444.toInt() && target.getPixel(288, 110) == 0xFF3E6ACB.toInt()) {
            "인화지가 16:9 풍경의 양옆을 잘라냄"
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("awt.headless", "true")
        projectionChecks()
        val out = File(args.getOrElse(0) { "tools/preview/out/perspective" }).apply { mkdirs() }
        val ctx = TestContext()
        val g = Game(ctx)
        g.onSurfaceChanged(1280, 720)
        // 실제 종별 색상 로더가 준비된 뒤 스프라이트를 고정한다.
        for (id in listOf("sparrow", "kingfisher", "gull", "greattit", "crane", "owl")) {
            g.assets.preloadBird(id) // 테스트/부팅에서만 기준색을 동기 준비한다.
        }

        sceneryChecks(g)

        data class Sample(val name: String, val region: String, val bird: String,
            val x: Int, val z: Int, val dx: Int, val dz: Int,
            val hour: Float = 12f, val weather: Weather = Weather.SUNNY, val season: Season = Season.SPRING)
        val samples = listOf(
            Sample("01_seoul", "seoul", "sparrow", 20, 15, 0, -1),
            Sample("02_lake", "chuncheon", "kingfisher", 12, 20, -1, 0),
            Sample("03_coast", "jeju", "gull", 35, 18, 1, 0),
            Sample("04_forest", "gwangneung", "greattit", 15, 20, 0, -1),
            Sample("05_wetland", "suncheon", "crane", 25, 23, 0, 1),
            Sample("06_mountain", "hallasan", "greattit", 19, 18, 0, -1),
            Sample("07_night", "seoul", "owl", 20, 15, 0, -1, 23f),
            Sample("08_rain", "chuncheon", "kingfisher", 12, 20, -1, 0, 15f, Weather.RAIN),
            Sample("09_snow", "sokcho", "crane", 26, 20, 1, 0, 11f, Weather.SNOW, Season.WINTER)
        )
        val images = ArrayList<Bitmap>()
        for (s in samples) {
            val map = MapBuilder.build(Regions.byId.getValue(s.region), "seoul")
            val def = Birds.byId.getValue(s.bird)
            val positions = (2 until map.h - 2).flatMap { z -> (2 until map.w - 2).map { x -> x to z } }
                .filter { (x, z) -> BirdEcology.suitability(def, map, x, z) > 0.0 &&
                    x - s.dx * 5 in 1 until map.w - 1 && z - s.dz * 5 in 1 until map.h - 1 &&
                    map.walkableTile(x - s.dx * 5, z - s.dz * 5) }
            val (x, z) = positions.minByOrNull { (x, z) -> hypot((x - s.x).toFloat(), (z - s.z).toFloat()) }
                ?: (s.x to s.z)
            val b = birdAt(g, s.bird, x + 0.5f, z + 0.65f)
            val start = System.nanoTime()
            GfxStats.reset()
            val shot = PerspectivePhoto.capture(g.assets, map, b,
                (x + 0.5f - s.dx * 5) * 16f, (z + 0.65f - s.dz * 5) * 16f,
                s.hour, s.weather, s.season, 3f)
            val renderMs = (System.nanoTime() - start) / 1_000_000
            check(GfxStats.decodesMain == 0L && GfxStats.compressionsMain == 0L)
            check(shot.bitmap.width == 720 && shot.bitmap.height == 405)
            for (yy in 0 until 405 step 7) for (xx in 0 until 720 step 7)
                check(Color.alpha(shot.bitmap.getPixel(xx, yy)) == 255) { "투명한 지면/지도 경계" }
            ImageIO.write(shot.bitmap.image, "png", File(out, "${s.name}.png"))
            images.add(shot.bitmap)
            println("${s.name}: bird ($x,$z), ${renderMs}ms render")
        }
        check(images.map { signature(it) }.distinct().size == images.size) { "서식지/날씨 사진이 모두 같음" }
        overview(out, images)

        // 같은 지점 반대편에서 찍으면 배경과 보이는 면이 함께 달라져야 한다.
        val map = MapBuilder.build(Regions.byId.getValue("jeju"), "seoul")
        val b = birdAt(g, "gull", 34.5f, 19.5f, BirdFacing.RIGHT)
        val east = PerspectivePhoto.capture(g.assets, map, b, 30f * 16f, 19.5f * 16f, 12f, Weather.SUNNY, Season.SPRING, 3f)
        val west = PerspectivePhoto.capture(g.assets, map, b, 38f * 16f, 19.5f * 16f, 12f, Weather.SUNNY, Season.SPRING, 3f)
        check(east.facing == BirdFacing.BACK && west.facing == BirdFacing.FRONT)
        check(signature(east.bitmap) != signature(west.bitmap))
        val repeatedShot = PerspectivePhoto.capture(g.assets, map, b, 30f * 16f, 19.5f * 16f, 12f, Weather.SUNNY, Season.SPRING, 3f)
        check(signature(east.bitmap) == signature(repeatedShot.bitmap)) { "고정된 촬영 상태의 렌더가 비결정적" }
        ImageIO.write(east.bitmap.image, "png", File(out, "10_toward_sea.png"))
        ImageIO.write(west.bitmap.image, "png", File(out, "11_toward_land.png"))
        // 바깥 지도/0거리에서도 투영이 유한하고 크래시하지 않아야 한다.
        for ((x, z) in listOf(0f to 0f, 39.8f to 29.8f)) {
            val edgeBird = birdAt(g, "sparrow", x, z)
            PerspectivePhoto.capture(g.assets, map, edgeBird, x * 16f, z * 16f, 12f, Weather.SUNNY, Season.SPRING, 3f)
        }

        // 실제 WorldScene.snap → 인화 카드 → 비동기 파일 → 사진집 상세를 통과시킨다.
        g.state.started = true
        g.state.worldTime = 12f
        g.state.weatherId = "sunny"
        g.state.px = 20f * 16f
        g.state.py = 16f * 16f
        g.state.photoAlbum.clear()
        val scene = WorldScene(g, "seoul")
        g.scene = scene
        framingCheck(scene)
        val capturedBird = birdAt(g, "sparrow", 20.5f, 12.5f, BirdFacing.RIGHT)
        GfxStats.reset()
        scene.javaClass.getDeclaredMethod("snap", FieldBird::class.java).apply { isAccessible = true }.invoke(scene, capturedBird)
        check(GfxStats.compressionsMain == 0L && GfxStats.decodesMain == 0L)
        val record = g.state.photoAlbum.single()
        check(record.regionId == "seoul" && record.pose == BirdPose.PERCHED)
        val saved = PhotoArchive.image(ctx, record.fileName)!!
        val frozen = signature(saved)
        capturedBird.x += 100f
        g.state.worldTime = 23f
        repeat(65) { g.update(1f / 60f) }
        val screen = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        g.render(Canvas(screen))
        check(scene.overlay is PhotoResultOverlay)
        val overlayBitmap = scene.overlay!!.javaClass.getDeclaredField("capturedPhoto").apply { isAccessible = true }.get(scene.overlay)
        check(overlayBitmap === saved) { "결과 카드와 사진집의 원본이 다름" }
        ImageIO.write(screen.image, "png", File(out, "12_result_card.png"))
        PhotoArchive.awaitPendingWrites(3000L)
        check(File(ctx.filesDir, "bird_photos/${record.fileName}").isFile)
        check(signature(PhotoArchive.image(ctx, record.fileName)!!) == frozen)
        val restored = SaveManager.load(ctx).photoAlbum.single()
        check(restored == record) { "사진 메타데이터 저장/불러오기 불일치" }
        scene.openOverlay(PhotoAlbumViewerOverlay(scene, record.id))
        repeat(30) { g.update(1f / 60f) }
        g.render(Canvas(screen))
        ImageIO.write(screen.image, "png", File(out, "13_album.png"))
        PhotoArchive.delete(ctx, record.fileName)
        println("Photo perspective: scenery / lighting / weather / stable frame / real snap / result / archive / reload OK")
    }
}
