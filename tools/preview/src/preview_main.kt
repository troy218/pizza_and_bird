@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — 헤드리스 프리뷰 렌더러.
 *
 * 실제 게임 코드(Assets/Maps/Scenes/WorldScene/HomeScene/Hud/Overlays)를
 * android.graphics 스텁(Java2D) 위에서 그대로 실행해 스크린샷을 뽑는다.
 *
 * 사용: java -jar preview.jar [출력폴더=preview_out]
 */
package com.pizzaandbird.preview

import android.content.Context
import android.content.SharedPreferences
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.StubText
import android.view.KeyEvent
import com.pizzaandbird.game.Ach
import com.pizzaandbird.game.AchievementDetailOverlay
import com.pizzaandbird.game.Assets
import com.pizzaandbird.game.Backup
import com.pizzaandbird.game.BackupOverlay
import com.pizzaandbird.game.BakeOverlay
import com.pizzaandbird.game.CameraGear
import com.pizzaandbird.game.Birds
import com.pizzaandbird.game.DecorPickOverlay
import com.pizzaandbird.game.DecorShopOverlay
import com.pizzaandbird.game.BikeColors
import com.pizzaandbird.game.BikeStyle
import com.pizzaandbird.game.CharacterArt
import com.pizzaandbird.game.CharacterSelectScene
import com.pizzaandbird.game.DialogOverlay
import com.pizzaandbird.game.FieldBird
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.HomeScene
import com.pizzaandbird.game.LevelUpOverlay
import com.pizzaandbird.game.MapOverlay
import com.pizzaandbird.game.MenuOverlay
import com.pizzaandbird.game.NpcKind
import com.pizzaandbird.game.NpcRoster
import com.pizzaandbird.game.PhotoResultOverlay
import com.pizzaandbird.game.PizzaKind
import com.pizzaandbird.game.Pizzas
import com.pizzaandbird.game.Player
import com.pizzaandbird.game.RegionSelectScene
import com.pizzaandbird.game.SaveManager
import com.pizzaandbird.game.Scene
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.StatsOverlay
import com.pizzaandbird.game.T
import com.pizzaandbird.game.TitleScene
import com.pizzaandbird.game.WorldScene
import java.io.File
import javax.imageio.ImageIO

// ---------------------------------------------------------------------------
// 리플렉션 헬퍼 (private 필드 접근)
// ---------------------------------------------------------------------------

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
        }
    }
    error("field '$name' not found on ${obj.javaClass.name}")
}

@Suppress("UNCHECKED_CAST")
private fun <T> getField(obj: Any, name: String): T {
    var c: Class<*>? = obj.javaClass
    while (c != null) {
        try {
            val f = c.getDeclaredField(name)
            f.isAccessible = true
            return f.get(obj) as T
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        }
    }
    error("field '$name' not found on ${obj.javaClass.name}")
}

// ---------------------------------------------------------------------------
// 가짜 안드로이드 컨텍스트
// ---------------------------------------------------------------------------

private class FakeResources(d: Float) : Resources() {
    override val displayMetrics: android.content.DisplayMetrics =
        android.content.DisplayMetrics().apply { density = d }
}

private class FakeContext(density: Float) : Context() {
    override val resources: Resources = FakeResources(density)

    // [P05] 세이브·백업 코드가 실제로 읽히고 쓰여야 하므로 in-memory prefs 를 그대로 쓴다.
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        android.content.InMemorySharedPreferences(name)
}

// ---------------------------------------------------------------------------
// 프리뷰 본체
// ---------------------------------------------------------------------------

object PreviewMain {

    private lateinit var outDir: File
    private val density = 2.6f
    private const val SW = 2560
    private const val SH = 1440

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 11f
        isFakeBoldText = true
    }
    private val labelBackPaint = Paint().apply { color = 0xCC222222.toInt() }
    private val sheetBgPaint = Paint().apply { color = 0xFF3A3648.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x40FFFFFF
    }

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("awt.headless", "true")
        outDir = File(args.getOrElse(0) { "preview_out" })
        outDir.mkdirs()

        val fontDir = File("tools/preview/fonts")
        if (fontDir.exists()) {
            StubText.loadFromDir(fontDir)
        }

        val game = Game(FakeContext(density))
        game.onSurfaceChanged(SW, SH)
        enrichState(game)

        // ---------------- 아트 시트 ----------------
        val assets = game.assets
        drawTilesSheet(assets)
        drawSpritesSheet(assets)
        drawCharacterHdSheet(assets)
        drawBirdsSheet(assets)

        // ---------------- 타이틀 ----------------
        simulate(game, 2.2f)
        renderScreen(game, "04_title")

        // ---------------- 지역 선택 ----------------
        game.scene = RegionSelectScene(game)
        simulate(game, 1.5f)
        renderScreen(game, "05_region_select")

        // ---------------- 월드 씬 ----------------
        worldShot(game, "seoul", 12.5f, "06_world_seoul_day")
        worldShot(game, "seoul", 18.4f, "07_world_seoul_sunset")
        worldShot(game, "seoul", 22.0f, "08_world_seoul_night")
        worldShot(game, "sokcho", 13.0f, "09_world_sokcho")
        worldShot(game, "chuncheon", 10.0f, "10_world_chuncheon")
        worldShot(game, "busan", 16.0f, "11_world_busan")
        worldShot(game, "jeju", 12.0f, "12_world_jeju")

        // 사진 모드
        photoModeShot(game)

        // ---------------- 집 내부 ----------------
        game.state.decorSlots[0] = 1
        game.state.decorSlots[1] = 3
        game.state.worldTime = 12.0f
        game.scene = HomeScene(game)
        simulate(game, 1.0f)
        renderScreen(game, "14_home_day")
        game.state.worldTime = 22.5f
        renderScreen(game, "15_home_night")

        // ---------------- 캐릭터 선택 ----------------
        game.scene = CharacterSelectScene(game)
        simulate(game, 1.2f)
        renderScreen(game, "38_character_select")

        // ---------------- 오버레이 ----------------
        overlayShots(game)

        // ---------------- 레벨업 축하 (캐릭터를 가장 크게 띄우는 순간) ----------------
        levelUpShots(game)

        // ---------------- 화면비 적응 검증 ----------------
        ultrawideShots(game)

        println("preview done -> ${outDir.absolutePath}")
    }

    // ------------------------------------------------------------------

    private fun enrichState(game: Game) {
        val s = game.state
        s.money = 128400
        s.hunger = 74f
        s.luck = 66f
        // 중급 렌즈교환식 조합(APS-C 바디 + 100-400mm)을 장착한 상태로 프리뷰
        s.ownedGear.addAll(listOf("b_apsc_mid", "l_100400", CameraGear.ACC_STRAP))
        s.bodyId = "b_apsc_mid"
        s.lensId = "l_100400"
        s.useIlc = true
        s.invalidateRig()
        s.photos = 42
        s.playSeconds = 3720f
        s.visited.clear()
        s.visited.addAll(listOf("seoul", "incheon", "chuncheon", "daejeon"))
        s.questBird = "egret"
        s.questReward = 850
        s.birdCounts.clear()
        s.birdCounts["sparrow"] = 12
        s.birdCounts["magpie"] = 5
        s.birdCounts["greattit"] = 3
        s.birdCounts["gull"] = 7
        s.birdCounts["egret"] = 2
        s.birdCounts["crane"] = 1
        s.bestStars.clear()
        s.bestStars["sparrow"] = 3
        s.bestStars["magpie"] = 2
        s.bestStars["greattit"] = 2
        s.bestStars["gull"] = 3
        s.bestStars["egret"] = 2
        s.bestStars["crane"] = 3
        s.decorOwned.clear()
        s.decorOwned.addAll(listOf(0, 2, 3))
    }

    /** 초 단위 시뮬레이션 (게임 업데이트 루프를 그대로 돌린다) */
    private fun simulate(game: Game, seconds: Float, dt: Float = 1f / 30f) {
        var t = 0f
        while (t < seconds) {
            game.update(dt)
            // 등장 연출(Overlay.bornAt · UiKit.enter)도 게임 시간으로 — 같은 소스를 돌리면
            // 화면이 매번 똑같이 나오도록(프리뷰 SystemClock 는 시뮬레이션 시간만 흐른다)
            android.os.SystemClock.advance(dt)
            t += dt
        }
    }

    /**
     * @param settlePhotos 사진(도감)이 있는 화면이면 true. 백그라운드 로더 스레드가
     *   채운 사진을 기다렸다가 다시 그려야 실제 화면(사진이 다 올라온 상태)이 찍힌다.
     */
    private fun renderScreen(game: Game, name: String, settlePhotos: Boolean = false) {
        val bmp = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (settlePhotos) {
            game.render(c)                 // 첫 그림이 로더에 요청을 넣어준다
            game.assets.awaitImages()      // 로더 스레드가 다 채우기를 기다린다
        }
        game.render(c)
        ImageIO.write(bmp.image, "png", File(outDir, "$name.png"))
        println("  + $name.png")
    }

    private fun renderScreen(game: Game, name: String, w: Int, h: Int) {
        game.onSurfaceChanged(w, h)
        // 실제 기기와 동일하게: 리사이즈 후 몇 프레임 업데이트로 카메라를 재클램프/수렴
        simulate(game, 0.6f)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        game.render(c)
        ImageIO.write(bmp.image, "png", File(outDir, "$name.png"))
        println("  + $name.png")
        game.onSurfaceChanged(SW, SH)
    }

    // ------------------------------------------------------------------
    // 월드
    // ------------------------------------------------------------------

    private fun worldShot(game: Game, regionId: String, hour: Float, name: String) {
        val s = game.state
        s.worldTime = hour
        // 광장이 잘 보이는 위치에서 시작
        s.px = 21f * 16f
        s.py = 14f * 16f
        s.onBike = false
        game.scene = WorldScene(game, regionId, SpawnKind.SAVED)
        simulate(game, 2.0f)
        s.worldTime = hour
        // 프레임에 새가 확실히 들어오도록 배치
        val birds: MutableList<FieldBird> = getField(game.scene as Any, "birds")
        val player: com.pizzaandbird.game.Player = getField(game.scene as Any, "player")
        birds.clear()
        val showcase = listOf("sparrow", "greattit", "egret", "crane", "gull", "mandarin")
        for ((i, id) in showcase.withIndex()) {
            val def = Birds.byId[id] ?: continue
            val bx = player.x + (-110f + i * 62f)
            val by = player.y + (-46f + (if (i % 2 == 0) 0f else 34f))
            birds.add(FieldBird(def, bx, by))
        }
        simulate(game, 0.25f)
        s.worldTime = hour
        renderScreen(game, name)
    }

    /**
     * 겨울 눈보라 샷 — 날씨를 눈에 고정하고 눈이 쌓일 때까지 시뮬레이션한다.
     * moving=true면 Shift+D(달리기)로 이동하며 카메라·줌이 요동치는 동안에도
     * 눈송이가 화면 전체에 그대로 남는지 확인한다.
     */
    private fun snowShot(game: Game, moving: Boolean, name: String) {
        val s = game.state
        s.day = 25                                // 겨울 (Season.forDay: 22~28일)
        s.weatherId = "snow"
        s.weatherSeconds = 9999f
        s.worldTime = 11.0f
        s.px = 21f * 16f
        s.py = 14f * 16f
        s.onBike = false
        game.scene = WorldScene(game, "sokcho", SpawnKind.SAVED)
        s.weatherSeconds = 9999f                  // 시뮬레이션 내내 눈에 고정
        simulate(game, 10.0f)                     // 눈이 소복이 쌓일 시간
        if (moving) {
            game.input.onKeyEvent(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN)
            game.input.onKeyEvent(KeyEvent.KEYCODE_D, KeyEvent.ACTION_DOWN)
            simulate(game, 1.8f)                   // 달리며 이동 (줌·카메라 변화)
            game.input.onKeyEvent(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP)
            game.input.onKeyEvent(KeyEvent.KEYCODE_D, KeyEvent.ACTION_UP)
            simulate(game, 0.4f)
        } else {
            simulate(game, 0.5f)
        }
        s.worldTime = 11.0f
        s.weatherSeconds = 9999f
        renderScreen(game, name)
    }

    private fun photoModeShot(game: Game) {
        val s = game.state
        s.worldTime = 12.5f
        s.px = 21f * 16f
        s.py = 14f * 16f
        game.scene = WorldScene(game, "chuncheon", SpawnKind.SAVED)
        simulate(game, 1.0f)
        val birds: MutableList<FieldBird> = getField(game.scene as Any, "birds")
        val player: com.pizzaandbird.game.Player = getField(game.scene as Any, "player")
        birds.clear()
        birds.add(FieldBird(Birds.byId["crane"]!!, player.x + 76f, player.y - 40f))
        birds.add(FieldBird(Birds.byId["sparrow"]!!, player.x - 60f, player.y + 30f))
        birds.add(FieldBird(Birds.byId["kingfisher"]!!, player.x - 140f, player.y - 60f))
        setField(game.scene as Any, "photoMode", true)
        (game.scene as WorldScene).let {
            val hudQuest = it.game.hud
            // photoModeHint 노출
            runCatching { setField(hudQuest, "photoModeHint", true) }
        }
        simulate(game, 0.2f)
        s.worldTime = 12.5f
        renderScreen(game, "13_photo_mode")
    }

    // ------------------------------------------------------------------
    // 오버레이
    // ------------------------------------------------------------------

    private fun overlayShots(game: Game) {
        val s = game.state
        s.worldTime = 12.0f
        s.px = 21f * 16f
        s.py = 14f * 16f
        game.scene = WorldScene(game, "seoul", SpawnKind.SAVED)
        simulate(game, 1.2f)
        val scene = game.scene as Scene

        // 대화상자
        scene.openOverlay(
            DialogOverlay(
                scene, "보리 박사",
                "\"반가워! 나는 조류학자 보리 박사네.\n이 지역에 중대백로가 나타났다는 소문이 있어.\n사진 한 장 부탁하네! 보수는 ₩850이야.\"",
                listOf(
                    DialogOverlay.Choice("맡겨주세요!"),
                    DialogOverlay.Choice("다음에요…")
                )
            )
        )
        simulate(game, 0.8f)
        renderScreen(game, "16_dialog")

        // 메뉴 탭
        scene.closeOverlay()
        val menu = MenuOverlay(scene)
        scene.openOverlay(menu)
        val tabCls = Class.forName("com.pizzaandbird.game.MenuOverlay\$Tab")
        val tabs = tabCls.enumConstants
        for ((i, tab) in tabs.withIndex()) {
            setField(menu, "tab", tab)
            renderScreen(game, "17_menu_tab${i + 1}", settlePhotos = true)
        }

        // [P06] 업적의 상세 통계·해금 팝업·실제 tick 경유 토스트 캡처.
        scene.closeOverlay()
        scene.openOverlay(StatsOverlay(scene) {})
        simulate(game, 0.25f)
        renderScreen(game, "33_achievement_stats")
        scene.closeOverlay()
        scene.openOverlay(AchievementDetailOverlay(scene, Ach.ALL.first()) {})
        renderScreen(game, "34_achievement_detail")
        scene.closeOverlay()
        getField<MutableList<*>>(game.hud, "messages").clear()
        s.photos = 100
        Thread.sleep(1_050L) // Ach.tick 의 1초 스로틀 다음에 shot_100 을 해금한다.
        Ach.tick(game.scene as WorldScene, s)
        game.hud.update(0.3f) // 토스트 등장 페이드
        renderScreen(game, "35_achievement_unlock_toast")

        // 피자 굽기 (v0.4: 피자 12종 — 화덕/일반 계열)
        scene.closeOverlay()
        val bake = BakeOverlay(scene)
        scene.openOverlay(bake)
        renderScreen(game, "21_bake_topping")
        setField(bake, "pizzaId", Pizzas.representative(PizzaKind.OVEN).id)
        setField(bake, "step", 1)
        setField(bake, "t", 1.15f)
        renderScreen(game, "22_bake_gauge")
        setField(bake, "step", 2)
        setField(bake, "resultQ", 2)
        setField(bake, "pizzaId", Pizzas.representative(PizzaKind.REGULAR).id)
        setField(bake, "stopped", true)
        setField(bake, "lostPizza", false)
        simulate(game, 0.6f)
        renderScreen(game, "23_bake_result")

        // 사진 결과
        scene.closeOverlay()
        val photo = PhotoResultOverlay(scene, Birds.byId["crane"]!!, 3, true, 2, "의뢰 완료! +₩7,800 (3성 보너스)")
        scene.openOverlay(photo)
        simulate(game, 0.9f)
        renderScreen(game, "24_photo_result", settlePhotos = true)

        // 큰 지도
        scene.closeOverlay()
        scene.openOverlay(MapOverlay(scene))
        renderScreen(game, "25_map_overlay")

        // 장식 상점 / 배치
        scene.closeOverlay()
        val shop = DecorShopOverlay(scene)
        scene.openOverlay(shop)
        renderScreen(game, "26_decor_shop")
        scene.closeOverlay()
        scene.openOverlay(DecorPickOverlay(scene, 0) {})
        renderScreen(game, "27_decor_pick")
        scene.closeOverlay()

        // [P05] 클라우드 없는 백업 — 코드 만들기 / 코드에서 불러오기
        backupShots(game, scene)
    }

    /** 설정 › 백업 코드 만들기 · 코드에서 불러오기 화면 (실제 Backup 로직을 그대로 돌린다) */
    private fun backupShots(game: Game, scene: Scene) {
        // 진행 상황을 prefs 에 저장해 두고(설정 › 💾 저장하기와 같은 경로) 코드를 만든다.
        game.state.started = true
        SaveManager.save(game.context, game.state)
        val prefs = game.context.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
        prefs.edit()
            .putString("feat_album_v1", "{\"photos\":[{\"id\":1,\"stars\":5}]}")
            .putInt("feat_ach_count_v1", 13)
            .commit()

        val export = BackupOverlay(scene, BackupOverlay.Mode.CREATE)
        scene.openOverlay(export)
        simulate(game, 0.7f)      // 백그라운드 스레드가 코드를 만드는 동안 프레임 진행
        renderScreen(game, "30_backup_code")

        // 방금 만든 코드를 클립보드에 넣은 상황 → 불러오기 확인 카드
        val code = getField<String>(export, "code")
        android.content.PreviewClipboard.text = code
        scene.closeOverlay()
        val restore = BackupOverlay(scene, BackupOverlay.Mode.RESTORE)
        scene.openOverlay(restore)
        simulate(game, 0.3f)
        renderScreen(game, "31_backup_restore_idle")

        setField(restore, "pendingCode", code)
        setField(restore, "info", Backup.inspect(code).getOrNull())
        setField(restore, "step", Class.forName("com.pizzaandbird.game.BackupOverlay\$Step")
            .enumConstants.first { it.toString() == "CONFIRM" })
        simulate(game, 0.35f)
        renderScreen(game, "32_backup_restore_confirm")

        scene.closeOverlay()
    }

    /** 화면비 적응 검증: 20:9 · 16:10 울트라와이드 샷 */
    private fun ultrawideShots(game: Game) {
        game.state.worldTime = 12.5f
        game.scene = TitleScene(game)
        simulate(game, 2.2f)
        renderScreen(game, "28_ultrawide_title_20x9", 2400, 1080)

        game.state.worldTime = 12.5f
        game.scene = WorldScene(game, "seoul", SpawnKind.SAVED)
        simulate(game, 2.0f)
        renderScreen(game, "29_ultrawide_world_20x9", 2400, 1080)
    }

    // ------------------------------------------------------------------
    // 아트 시트
    // ------------------------------------------------------------------

    private fun saveSheet(bmp: Bitmap, name: String) {
        ImageIO.write(bmp.image, "png", File(outDir, "$name.png"))
        println("  + $name.png")
    }

    private fun label(c: Canvas, text: String, x: Float, y: Float) {
        val w = labelPaint.measureText(text)
        c.drawRect(x, y - 11f, x + w + 8f, y + 3f, labelBackPaint)
        labelPaint.color = 0xFFFFFFFF.toInt()
        c.drawText(text, x + 4f, y, labelPaint)
    }

    private fun drawTilesSheet(a: Assets) {
        val cols = 12
        val cell = 72f
        val rows = ((T.ALL.size + cols - 1) / cols)
        val bmp = Bitmap.createBitmap((cols * cell).toInt() + 8, (rows * (cell + 16f)).toInt() + 8, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), sheetBgPaint)
        for ((ti, t) in T.ALL.withIndex()) {
            val col = ti % cols
            val row = ti / cols
            val x = 4f + col * cell
            val y = 4f + row * (cell + 16f)
            val variants = a.tiles[ti]
            for ((vi, v) in variants.withIndex()) {
                val vx = x + vi * 4f
                val vy = y + vi * 4f
                c.drawBitmap(v, null, RectF(vx, vy, vx + 64f, vy + 64f), a.sprPaint)
            }
            label(c, t.name, x, y + cell + 6f)
        }
        saveSheet(bmp, "01_tiles")
    }

    private fun drawSpritesSheet(a: Assets) {
        val bmp = Bitmap.createBitmap(960, 560, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, 960f, 560f, sheetBgPaint)

        var x = 8f
        var y = 8f
        fun row(title: String, draw: (Float, Float) -> Unit) {
            label(c, title, x, y + 10f)
            draw(x, y + 16f)
            y += 16f + 74f
            if (y > 560 - 90) {
                y = 8f
                x += 200f
            }
        }

        row("player down") { px, py ->
            for (i in 0..2) c.drawBitmap(a.playerDown[i], px + i * 36f, py + 14f, a.sprPaint)
        }
        row("player up") { px, py ->
            for (i in 0..2) c.drawBitmap(a.playerUp[i], px + i * 36f, py + 14f, a.sprPaint)
        }
        row("player side R/L") { px, py ->
            for (i in 0..2) {
                c.drawBitmap(a.playerSide[i], px + i * 36f, py + 14f, a.sprPaint)
                c.drawBitmap(a.playerSideL[i], px + i * 36f + 108f, py + 14f, a.sprPaint)
            }
        }
        row("bike D/U/S/S") { px, py ->
            c.drawBitmap(a.bikeDown, px, py + 14f, a.sprPaint)
            c.drawBitmap(a.bikeUp, px + 36f, py + 14f, a.sprPaint)
            c.drawBitmap(a.bikeSide, px + 72f, py + 14f, a.sprPaint)
            c.drawBitmap(a.bikeSideL, px + 108f, py + 14f, a.sprPaint)
        }
        // 「한 사람은 한 장소에만」 — 같은 5명이 아니라, 자리가 서로 다른 실제 캐스팅을 뽑아 그린다
        row("npcs") { px, py ->
            val cast = listOf(
                NpcRoster.professor,                        // 광릉숲 숲속 쉼터 · 보리 박사
                NpcRoster.shopkeeper,                       // 서울 골목 · 사진용품점 남기택
                NpcRoster.forRegion("chuncheon").first(),   // 의암호 전망 데크 · 노을
                NpcRoster.forRegion("incheon").first(),     // 소래포구 갯벌 둑길 · 해순
                NpcRoster.representative(NpcKind.KID),      // 송도/안산 이웃 꼬마
                NpcRoster.forRegion("hallasan").last()      // 한라산 그루브 · 순옥
            )
            for ((i, p) in cast.withIndex()) {
                c.drawBitmap(a.npcBitmap(p, 0f, i * 0.7f), px + i * 36f, py + 14f, a.sprPaint)
            }
        }
        row("cat") { px, py ->
            for (i in 0..2) {
                c.drawBitmap(a.catFrames[i], px + i * 40f, py + 16f, a.sprPaint)
                c.drawBitmap(a.catFramesL[i], px + i * 40f + 120f, py + 16f, a.sprPaint)
            }
        }
        row("decor") { px, py ->
            for ((i, b) in a.decorArt.withIndex()) c.drawBitmap(b, null, RectF(px + i * 38f, py + 14f, px + i * 38f + 36f, py + 14f + 36f), a.sprPaint)
        }

        // 아이콘 모음 (4x)
        label(c, "icons", x, y + 10f)
        val icons = listOf(
            a.pizzaIcon, a.cloverIcon, a.cameraIcon, a.houseIcon, a.sunIcon, a.moonIcon
        )
        for ((i, ic) in icons.withIndex()) {
            c.drawBitmap(ic, null, RectF(x + i * 72f, y + 18f, x + i * 72f + ic.width * 2.5f, y + 18f + ic.height * 2.5f), a.sprPaint)
            c.drawRect(x + i * 72f, y + 18f, x + i * 72f + ic.width * 2.5f, y + 18f + ic.height * 2.5f, gridPaint)
        }
        y += 110f
        label(c, "pizza big", x, y + 10f)
        c.drawBitmap(a.pizzaIconBig, x, y + 18f, a.sprPaint)

        saveSheet(bmp, "02_sprites")
    }

    /**
     * 캐릭터 화질 비교 시트 — **같은 크기로 나란히** 놓고 본다.
     *
     * 왼쪽: 32px 도트를 8배로 키운 것(예전 방식) · 가운데: HD(96px)를 같은 크기로 줄인 것
     * 오른쪽: HD 원본(96px, 1:1) · 아래줄: 레벨업 축하용 256px 만세 프레임
     * (도트로 그린 옛 그림과 같은 포즈·같은 배색이라 무엇이 좋아졌는지 바로 보인다)
     */
    private fun drawCharacterHdSheet(a: Assets) {
        val bmp = Bitmap.createBitmap(1160, 520, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, 1160f, 520f, sheetBgPaint)

        val dot = a.playerSet("male", 0, false).idle.down[2]      // 32px 도트
        val hd = a.playerSet("male", 0, true).idle.down[2]        // 96px HD
        val size = 256f
        val smooth = Paint(Paint.ANTI_ALIAS_FLAG)
        val crisp = Paint().apply { isFilterBitmap = false }

        label(c, "32px 도트 ×8 (예전)", 16f, 30f)
        c.drawBitmap(dot, null, RectF(16f, 40f, 16f + size, 40f + size), crisp)

        label(c, "HD 96px → 같은 크기 (지금)", 300f, 30f)
        c.drawBitmap(hd, null, RectF(300f, 40f, 300f + size, 40f + size), smooth)

        label(c, "HD 원본 1:1 (96px)", 584f, 30f)
        c.drawBitmap(hd, 584f, 40f, smooth)

        // NPC도 같은 파이프라인 — 왼쪽이 예전 도트, 오른쪽이 HD(같은 화면 크기)
        label(c, "NPC 도트 ×5 (예전)", 700f, 30f)
        val npcDot = a.npcFrames(NpcKind.PROFESSOR, false)[0]
        c.drawBitmap(npcDot, null, RectF(700f, 40f, 700f + 160f, 40f + 160f), crisp)
        val npcHd = a.npcFrames(NpcKind.PROFESSOR, true)[0]
        c.drawBitmap(npcHd, null, RectF(890f, 40f, 890f + 160f, 40f + 160f), smooth)

        label(c, "레벨업 만세 — 256px (크게 띄우는 순간 전용)", 16f, 330f)
        val cheer = a.cheerFrames("male", 3)
        for (i in 0 until 4) {
            c.drawBitmap(cheer[i], null, RectF(16f + i * 152f, 344f, 16f + i * 152f + 144f, 344f + 144f), smooth)
        }
        label(c, "걷기 (HD 96px, 1:1)", 640f, 330f)
        val walk = a.playerSet("female", 2, true).walk
        for (i in 0 until 4) {
            c.drawBitmap(walk.down[i * 2], 640f + i * 100f, 344f, smooth)
        }
        label(c, "자전거 (HD 96px, 1:1)", 640f, 460f)
        val style = BikeStyle(
            "basic", BikeColors.frame(0), BikeColors.tire(0), BikeColors.saddle(0),
            basket = true, rack = true, light = true, streamers = true, bell = true
        )
        val bike = a.bikeSet("male", 3, style, true)
        for (i in 0 until 4) {
            c.drawBitmap(bike.side[i * 2], null, RectF(640f + i * 100f, 430f, 640f + i * 100f + 96f, 430f + 96f), smooth)
        }
        saveSheet(bmp, "02b_character_hd")
    }

    /**
     * 레벨업 축하 화면 — "레벨업할 때 캐릭터 화질"이 가장 크게 드러나는 순간.
     * 장비 등급이 바뀌는 레벨(6 → 새 장비)도 함께 찍는다.
     */
    private fun levelUpShots(game: Game) {
        val s = game.state
        s.worldTime = 12.0f
        game.scene = WorldScene(game, "seoul", SpawnKind.SAVED)
        simulate(game, 0.6f)
        val scene = game.scene as Scene

        s.level = 6
        s.skillPoints = 3
        s.exp = 8
        scene.openOverlay(LevelUpOverlay(scene, 5, 6))
        simulate(game, 0.42f)          // 만세 동작 중간 프레임
        renderScreen(game, "36_levelup")

        scene.closeOverlay()
        s.level = 12
        s.skillPoints = 6
        s.exp = 40
        scene.openOverlay(LevelUpOverlay(scene, 11, 12))
        simulate(game, 0.9f)
        renderScreen(game, "37_levelup_gear")
        scene.closeOverlay()
    }

    private fun drawBirdsSheet(a: Assets) {
        val bmp = Bitmap.createBitmap(1120, 640, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, 1120f, 640f, sheetBgPaint)
        var x = 12f
        var y = 14f
        var rowTier = -1
        for (def in Birds.ALL) {
            if (def.tier.star != rowTier) {
                rowTier = def.tier.star
                val col = when (rowTier) {
                    1 -> 0xFF9AA3AD.toInt()
                    2 -> 0xFF6FAE57.toInt()
                    3 -> 0xFF3F6FB0.toInt()
                    else -> 0xFFE2574C.toInt()
                }
                labelPaint.color = col
                c.drawText("— ${def.tier.label} —", x, y + 12f, labelPaint)
                y += 24f
            }
            val b = a.bird(def.id)
            val scale = if (b.height > 40f) 1f else 1.6f
            val w = b.width * scale
            val h = b.height * scale
            c.drawBitmap(b, null, RectF(x, y, x + w, y + h), a.sprPaint)
            label(c, def.name, x, y + h + 12f)
            x += w + 26f
            if (x > 1000f) {
                x = 12f
                y += 86f
            }
        }
        saveSheet(bmp, "03_birds")
    }
}
