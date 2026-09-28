package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import com.pizzaandbird.game.*
import java.io.File

/** Real DOWN/UP events, not action-lambda calls: regress menu/shop hit targets and navigation. */
object NavigationSmoke {
    private class TestContext(d: Float) : Context() {
        override val filesDir = File(System.getProperty("java.io.tmpdir"), "pb_navigation_${System.nanoTime()}")
            .apply { mkdirs() }
        override val resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = d }
        }
    }

    private fun field(obj: Any, name: String): Any = obj.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(obj)

    @Suppress("UNCHECKED_CAST")
    private fun pairs(obj: Any, name: String) = field(obj, name) as List<Pair<RectF, Any>>

    @Suppress("UNCHECKED_CAST")
    private fun buttons(obj: Any) = field(obj, "btnRects") as List<Triple<RectF, String, () -> Unit>>

    private lateinit var g: Game
    private lateinit var canvas: Canvas
    private lateinit var screen: Bitmap
    private var assertions = 0
    private fun verify(ok: Boolean, message: String) { check(ok) { message }; assertions++ }
    private fun render() = g.render(canvas)
    private fun frame() { g.update(1f / 60f); render() }
    private fun capture(name: String) {
        val dir = File("tools/preview/out/navigation").apply { mkdirs() }
        File(dir, "${g.screenW}x${g.screenH}-${g.density}-$name.png").outputStream().use {
            screen.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun tap(r: RectF) {
        verify(r.width() > 0 && r.height() > 0 && r.centerX() in 0f..g.screenW.toFloat() &&
            r.centerY() in 0f..g.screenH.toFloat(), "Unreachable button: $r")
        val point = listOf(Triple(0, r.centerX(), r.centerY()))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_DOWN, pointers = point))
        g.update(1f / 60f) // finger down and up in separate frames, like a physical tap
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_UP, pointers = point))
        frame()
    }
    private fun tab(o: Any, name: String) {
        tap(pairs(o, "tabRects").first { it.second.toString() == name }.first)
        verify(field(o, "tab").toString() == name, "Tab $name did not open")
    }
    private fun button(o: Any, name: String) = tap(buttons(o).first { it.second == name }.first)
    private fun open(o: Overlay) { g.scene.openOverlay(o); render() }
    private fun back() {
        g.input.onKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN)
        g.input.onKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP)
        frame()
    }
    private fun close(o: Any) { tap(field(o, "closeRect") as RectF); verify(g.scene.overlay == null, "Close failed") }
    private fun choice(o: DialogOverlay, index: Int) {
        val r = RectF((field(o, "choiceRects") as List<*>)[index] as RectF)
        r.offset(0f, field(o, "drawnShift") as Float)
        tap(r)
    }

    private fun menuTabs() {
        val m = MenuOverlay(g.scene)
        open(m)
        capture("status")
        var page = 0
        while (buttons(m).any { it.second == "status_next" }) {
            button(m, "status_next")
            verify(field(m, "statusPage") == ++page, "Status next page failed")
        }
        while (page > 0) {
            button(m, "status_prev")
            verify(field(m, "statusPage") == --page, "Status previous page failed")
        }
        for (name in listOf("QUEST", "GROW", "PIZZA", "BOOK", "ALBUM", "SETTINGS", "ACHIEVE", "STATUS")) tab(m, name)
        tab(m, "QUEST")
        capture("quests")
        button(m, "quest_next")
        verify(field(m, "questPage") == 1, "Quest next page failed")
        button(m, "quest_prev")
        verify(field(m, "questPage") == 0, "Quest previous page failed")
        repeat(3) {
            g.state.activeQuests.add(QuestData("navigation-$it", QuestCategory.BIRD_SPECIES,
                "테스트 의뢰 $it", "참새를 촬영해 보세요", "sparrow"))
        }
        button(m, "quest_sub_1")
        verify(field(m, "questSubTab") == 1, "Daily quest tab failed")
        capture("daily-quests")
        var taskPage = 0
        while (buttons(m).any { it.second == "task_next" }) {
            button(m, "task_next")
            verify(field(m, "questTaskPage") == ++taskPage, "Task next page failed")
        }
        verify(taskPage > 0 && g.state.dailyQuests.size == 3, "All active and daily tasks must be paginated")
        capture("last-daily-quest")
        while (taskPage > 0) {
            button(m, "task_prev")
            verify(field(m, "questTaskPage") == --taskPage, "Task previous page failed")
        }
        g.state.activeQuests.clear()
        button(m, "quest_sub_0")
        verify(field(m, "questSubTab") == 0, "Collection tab failed")
        close(m)
    }

    private fun shortcuts() {
        val m = MenuOverlay(g.scene)
        open(m)
        // 상태 탭에서 의뢰 탭으로 가는 바로가기 — 탭 UI로 동일 경로를 검증한다.
        // (예전 버전이 존재하지 않는 "quest" 바로가기 버튼을 기대했지만 그 버튼은
        //  만들어진 적이 없어 이 테스트만 늘 실패했다.)
        tab(m, "QUEST")
        tab(m, "STATUS")
        button(m, "shop_trip")
        verify(g.scene.overlay is DialogOverlay, "Shop shortcut only shows a hidden toast, not a shop menu")
        val hub = g.scene.overlay as DialogOverlay
        for ((index, expected) in listOf(CameraShopOverlay::class.java, GearBagOverlay::class.java,
            BikeShopOverlay::class.java, DecorShopOverlay::class.java, CharmOverlay::class.java).withIndex()) {
            open(hub)
            choice(hub, index)
            verify(expected.isInstance(g.scene.overlay), "Shop destination $expected did not open")
            back()
            verify(g.scene.overlay == null, "Back from shop failed")
        }
    }

    private fun remoteShop() {
        val originalRegion = g.state.region
        // 상점이 없는 지역이어야 '가장 가까운 가게로 이동' 대화가 뜬다.
        // (busan에는 이제 상점이 생겨 로컬 진열장 대화가 뜬다 — 선택지 4개, 이동 없음)
        g.state.region = "eulsukdo"
        val m = MenuOverlay(g.scene)
        open(m)
        button(m, "shop_trip")
        verify(g.scene.overlay is DialogOverlay, "Remote shop must offer travel, not silently do nothing")
        // 선택지: 0=자전거 이동 · 1=걸어서 · 2=다음에 갈게요(취소)
        choice(g.scene.overlay as DialogOverlay, 2)
        verify(g.state.region == "eulsukdo" && g.scene.overlay == null, "Canceling shop travel moved the player")
        g.state.region = originalRegion
    }

    private fun bikeTabs() {
        val s = g.state
        val bike = BikeShopOverlay(g.scene)
        open(bike)
        val modelsBefore = s.ownedBikes.toSet()
        val bikeBefore = s.bikeId
        val moneyBefore = s.money
        tab(bike, "PAINT")
        capture("bike-paint")
        val swatch = pairs(bike, "swatchRects").first { (it.second as IntArray).contentEquals(intArrayOf(0, 1)) }
        tap(swatch.first)
        verify(s.bikeFrameColor == 1, "Paint tap intercepted by an invisible model row")
        verify(s.money == moneyBefore && s.ownedBikes == modelsBefore && s.bikeId == bikeBefore,
            "Paint tap bought/equipped an invisible bicycle")
        tab(bike, "PARTS")
        capture("bike-parts")
        val part = pairs(bike, "partRects").first()
        tap(part.first)
        verify(part.second in s.ownedBikeParts, "Part purchase intercepted by an invisible model/swatch")
        verify(s.ownedBikes == modelsBefore && s.bikeId == bikeBefore, "Part tap changed bicycle model")
        tab(bike, "MODEL")
        verify(pairs(bike, "partRects").isEmpty() && pairs(bike, "swatchRects").isEmpty(), "Hidden tab hit targets survived")
        val page = pairs(bike, "pageRects").last()
        tap(page.first)
        verify(field(bike, "page") == page.second, "Bike next page failed")
        for ((r, _) in pairs(bike, "modelRects")) {
            verify(r.bottom <= (field(bike, "panelR") as RectF).bottom, "Bike model button outside panel")
        }
        close(bike)
    }

    private fun cameraTabs() {
        val shop = CameraShopOverlay(g.scene)
        open(shop)
        for (name in listOf("BODY", "LENS", "ACC", "COMPACT")) tab(shop, name)
        tap(field(shop, "nextRect") as RectF)
        verify(field(shop, "page") == 1, "Camera next page failed")
        tap(field(shop, "prevRect") as RectF)
        verify(field(shop, "page") == 0, "Camera previous page failed")
        val buy = pairs(shop, "buyRects").first()
        val gear = buy.second as CamGear
        val before = g.state.money
        tap(buy.first)
        verify(gear.id in g.state.ownedGear && g.state.money == before - gear.price, "Camera purchase failed")
        val info = pairs(shop, "infoRects").first()
        tap(info.first)
        verify(g.scene.overlay is DialogOverlay, "Camera details did not open")
        choice(g.scene.overlay as DialogOverlay, 1)
        verify(g.scene.overlay is CameraShopOverlay, "Camera details did not return to shop")
        close(g.scene.overlay!!)
    }

    private fun hudAndSettings() {
        val h = g.hud
        fun hudTap(x: Float, y: Float) = tap(RectF(x - 1, y - 1, x + 1, y + 1))
        hudTap(h.menuCx, h.menuCy)
        verify(g.scene.overlay is MenuOverlay, "HUD menu did not open")
        g.input.onKeyEvent(KeyEvent.KEYCODE_M, KeyEvent.ACTION_DOWN)
        g.input.onKeyEvent(KeyEvent.KEYCODE_M, KeyEvent.ACTION_UP)
        frame()
        verify(g.scene.overlay == null, "Menu key did not close the backpack")
        hudTap(h.bikeCx, h.bikeCy)
        verify(g.state.onBike, "Bicycle mount button failed")
        hudTap(h.bikeCx, h.bikeCy)
        verify(!g.state.onBike, "Bicycle dismount button failed")
        val world = g.scene as WorldScene
        hudTap(h.camBCx, h.camBCy)
        verify(world.photoMode, "Camera button did not enter photo mode")
        hudTap(h.camBCx, h.camBCy)
        verify(!world.photoMode, "Camera button did not leave photo mode")
        // 🍕 버튼은 '한 조각'을 먹는다 — 판 수(pizzaCount)가 아니라 조각 수(sliceCount)가 줄어든다
        g.state.addPizza(0, 1)
        g.state.hunger = 20f
        val slices = g.state.sliceCount
        hudTap(h.eatCx, h.eatCy)
        verify(g.state.sliceCount == slices - 1 && g.state.hunger > 20f, "Eat button failed")
        hudTap(h.punchCx, h.punchCy)
        verify((field(world, "punchT") as Float) > 0f, "Punch button failed")
        val questRect = h.javaClass.getDeclaredMethod("questChipRect").apply { isAccessible = true }.invoke(h) as RectF
        tap(questRect)
        verify(g.scene.overlay is DialogOverlay, "HUD quest chip did not open")
        back()
        hudTap(h.mmCx, h.mmCy)
        verify(g.scene.overlay is MapOverlay, "HUD minimap did not open map")
        val map = g.scene.overlay!!
        val scale = field(map, "scale") as Float
        tap(field(map, "zoomInR") as RectF)
        verify((field(map, "scale") as Float) > scale, "Map zoom in failed")
        tap(field(map, "zoomOutR") as RectF)
        tap(field(map, "resetR") as RectF)
        tap(field(map, "closeR") as RectF)
        verify(g.scene.overlay == null && !g.input.rawMode, "Map close button failed")

        // A must open an actual NPC dialog, then its shop choice must survive chaining.
        val npc = world.map.npcs.first { it.person.isShop }
        val player = field(world, "player") as Player
        player.x = npc.x
        player.y = npc.y
        frame()
        hudTap(h.mainCx, h.mainCy)
        verify(g.scene.overlay is DialogOverlay, "Interact button did not talk to shopkeeper")
        choice(g.scene.overlay as DialogOverlay, 0)
        verify(g.scene.overlay is CameraShopOverlay, "NPC camera shelf did not open")
        back()

        val m = MenuOverlay(g.scene)
        open(m)
        tab(m, "SETTINGS")
        val music = g.state.musicOn
        tap(buttons(m).first { it.second.startsWith("음악:") }.first)
        verify(g.state.musicOn != music, "Music toggle failed")
        val sfx = g.state.sfxOn
        tap(buttons(m).first { it.second.startsWith("효과음:") }.first)
        verify(g.state.sfxOn != sfx, "Sound toggle failed")
        button(m, "저장하기")
        button(m, "화면 연출 (몰입감)")
        verify(g.scene.overlay is CameraFxOverlay, "Camera settings did not open")
        back()
    }

    private fun decorShop() {
        val shop = DecorShopOverlay(g.scene)
        open(shop)
        for ((r, page) in pairs(shop, "catalogTabs").toList()) {
            tap(r)
            verify(field(shop, "catalogPage") == page, "Decor category failed")
        }
        val item = pairs(shop, "buyRects").first()
        tap(item.first)
        verify(item.second in g.state.decorOwned, "Decor purchase failed")
        close(shop)
    }

    private fun rawNavigation() {
        open(MapOverlay(g.scene))
        verify(g.input.rawMode, "Map must receive raw gestures")
        back()
        verify(!g.input.rawMode && g.scene.overlay == null, "Map did not release raw mode")
        // Constructing an unopened raw overlay must not change the current input mode.
        MapOverlay(g.scene)
        verify(!g.input.rawMode, "Unopened map changed global input routing")
        // Replacement/scene changes must not leave the next modal in the previous input mode.
        open(MapOverlay(g.scene))
        val menu = MenuOverlay(g.scene)
        open(menu)
        tab(menu, "QUEST")
        verify(!g.input.rawMode, "Map raw mode leaked into menu")
        close(menu)
        open(BackupOverlay(g.scene, BackupOverlay.Mode.RESTORE) { g.scene.openOverlay(MenuOverlay(g.scene)) })
        back()
        verify(g.scene.overlay is MenuOverlay && !g.input.rawMode, "Backup return did not restore normal input")
        tab(g.scene.overlay!!, "QUEST")
        back()
    }

    @JvmStatic fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: "all"
        for ((w, h, d) in listOf(Triple(2400, 1080, 2f), Triple(1280, 720, 2f), Triple(2400, 1080, 3f))) {
            android.content.PreviewPrefs.stores.clear()
            g = Game(TestContext(d))
            g.onSurfaceChanged(w, h)
            g.state.money = 100_000_000
            g.scene = WorldScene(g, "seoul", SpawnKind.HOME)
            screen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            canvas = Canvas(screen)
            frame()
            when (mode) {
                "bike" -> bikeTabs()
                "shortcuts" -> shortcuts()
                "raw" -> rawNavigation()
                else -> { menuTabs(); shortcuts(); remoteShop(); bikeTabs(); cameraTabs(); decorShop(); rawNavigation(); hudAndSettings() }
            }
            println("Navigation smoke $mode: ${w}x$h @ $d OK")
        }
        println("Navigation smoke: $assertions assertions passed")
    }
}
