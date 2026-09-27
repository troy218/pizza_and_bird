@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview/dialog_audit.kt — "상자 대비 글자" 배치 감사 도구.
 *
 * 실제 게임 오버레이(DialogOverlay · GearPick · DecorPick · HouseStyle …)를
 * 스텁 Canvas 위에 그리면서 모든 상자/글자 draw 를 기록해
 * 화면 폭 대비 패널 크기 · 글자 블록이 차지하는 비율 · 내부 빈 띠(dp)를
 * PNG + TSV 로 남긴다. "글자 양에 비해 상자가 무식하게 큰 놈"을 수치로 찾는다.
 *
 * 사용:
 *   java -cp classes-audit:kotlin-stdlib.jar com.pizzaandbird.preview.DialogAuditMain out_dir
 */
package com.pizzaandbird.preview

import android.content.Context
import android.content.Resources
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.StubText
import com.pizzaandbird.game.Birds
import com.pizzaandbird.game.CameraGear
import com.pizzaandbird.game.DecorPickOverlay
import com.pizzaandbird.game.DialogOverlay
import com.pizzaandbird.game.Game
import com.pizzaandbird.game.GearBagOverlay
import com.pizzaandbird.game.GearKind
import com.pizzaandbird.game.GearPickOverlay
import com.pizzaandbird.game.HomeScene
import com.pizzaandbird.game.HouseStyleOverlay
import com.pizzaandbird.game.Scene
import com.pizzaandbird.game.SpawnKind
import com.pizzaandbird.game.WorldScene
import java.io.File
import javax.imageio.ImageIO

// ---------------------------------------------------------------------------
// 가짜 안드로이드 컨텍스트 (preview_main.kt 와 같은 구성, 이름만 다름)
// ---------------------------------------------------------------------------

private class AuditResources(d: Float) : Resources() {
    override val displayMetrics: android.content.DisplayMetrics =
        android.content.DisplayMetrics().apply { density = d }
}

private class AuditContext(density: Float) : Context() {
    override val resources: Resources = AuditResources(density)

    // [P05] 프리뷰 파이프라인의 in-memory prefs 를 그대로 쓴다(세이브·백업 실동작).
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        android.content.InMemorySharedPreferences(name)
}

// ---------------------------------------------------------------------------
// 기록기
// ---------------------------------------------------------------------------

/** 리플렉션으로 private 필드 주입 (preview_main.kt 와 같은 기법) */
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

/** 접근성 글자 배율(TypeScale.scale)을 강제로 바꾼다 */
private fun setScale(v: Float) {
    val f = Class.forName("com.pizzaandbird.game.TypeScale").getDeclaredField("scale")
    f.isAccessible = true
    f.set(null, v)
}

private class AuditRecorder : Canvas.DrawAudit {
    class Ev(
        val kind: String, val l: Float, val t: Float, val r: Float, val b: Float,
        val color: Int, val alpha: Int, val style: String, val shader: Boolean,
        val textSize: Float, val text: String?
    )

    val events = ArrayList<Ev>()

    override fun onDraw(
        kind: String, l: Float, t: Float, r: Float, b: Float,
        paint: Paint, text: String?
    ) {
        events.add(
            Ev(
                kind, l, t, r, b, paint.color, paint.alpha,
                paint.style.name, paint.shader != null, paint.textSize, text
            )
        )
    }
}

// ---------------------------------------------------------------------------
// 감사 본체
// ---------------------------------------------------------------------------

object DialogAuditMain {

    private const val SW = 2560
    private const val SH = 1440
    private const val DENSITY = 2.6f

    private lateinit var outDir: File
    private lateinit var tsv: java.io.PrintWriter

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("awt.headless", "true")
        outDir = File(args.getOrElse(0) { "audit_out" })
        outDir.mkdirs()
        tsv = File(outDir, "events.tsv").printWriter()

        val fontDir = File("tools/preview/fonts")
        if (fontDir.exists()) StubText.loadFromDir(fontDir)

        val game = Game(AuditContext(DENSITY))
        game.onSurfaceChanged(SW, SH)
        val s = game.state
        s.money = 128400
        s.hunger = 74f
        s.luck = 66f
        s.visited.clear()
        s.visited.addAll(listOf("seoul", "incheon", "chuncheon", "daejeon"))

        s.worldTime = 12.0f
        s.px = 21f * 16f
        s.py = 14f * 16f
        s.onBike = false
        game.scene = WorldScene(game, "seoul", SpawnKind.SAVED)
        simulate(game, 1.2f)
        val scene = game.scene as Scene

        // ---------------------------------------------------------------
        // 대화상자 — 실제 게임에서 쓰는 문구 그대로
        // ---------------------------------------------------------------
        dialogShot(game, scene, "dlg_kid_1line", "꼬마",
            "\"우와, 카메라 멋져요! 저도 크면 탐조할 거예요!\"",
            listOf(DialogOverlay.Choice("ㅎㅎ 귀엽다")))

        dialogShot(game, scene, "dlg_elder_1line", "할머니",
            "\"요즘 젊은이들은 참 부지런해요.\"",
            listOf(DialogOverlay.Choice("다녀오겠습니다")))

        dialogShot(game, scene, "dlg_exit_confirm", "",
            "게임을 종료할까요?\n진행 상황은 자동으로 저장돼 있어요.",
            listOf(DialogOverlay.Choice("계속하기"), DialogOverlay.Choice("종료")))

        dialogShot(game, scene, "dlg_bed", "침대 🛏",
            "포근한 침대예요. 잠들면 아침이 되고\n행운이 오르며 진행 상황이 저장돼요.",
            listOf(DialogOverlay.Choice("쿨쿨…"), DialogOverlay.Choice("아직 안 잘래")))

        dialogShot(game, scene, "dlg_map_end", "함께 사는 지도",
            "메인 퀘스트는 만렙에서 완결됐어요. 서브 의뢰와 컬렉션은 계속 자유롭게 즐길 수 있습니다.",
            listOf(DialogOverlay.Choice("기억할게요")))

        dialogShot(game, scene, "dlg_quest", "보리 박사의 사진 의뢰",
            "\"이 지역에 중대백로가 나타났다는 소문이 있어.\n메인 기록과 상관없이 사진 한 장 부탁하네!\n보수는 ₩850.\"",
            listOf(DialogOverlay.Choice("맡겨주세요!"), DialogOverlay.Choice("다른 일을 할게요")))

        dialogShot(game, scene, "dlg_villager_4line", "동네 주민",
            "\"서울은 복잵하고 사람 많지만, 한강과 하늘을 보면 마음이 트여요.\n" +
                "멀리 가기 전에도 창밖의 새부터 천천히 보면 좋아요.\n" +
                "봄이 오면 이 동네 풍경이 하루에 한 번씩 갈아입혀요. 아침과 저녁이 다른 옷이에요.\n" +
                "이 시간엔 가로등 아래가 제일 밝아요. 새는 이미 자고, 밤새가 근무 중이에요.\"",
            listOf(DialogOverlay.Choice("기억할게요")))

        dialogShot(game, scene, "dlg_oven", "오븐 🍕",
            "익숙한 가정용 오븐이에요. 도톰하고 든든한 일반 피자를 굽는 곳!\n" +
                "천천히 익어서 굽기 쉬워요. 치즈·페퍼로니·불고기·고구마…",
            listOf(DialogOverlay.Choice("피자 굽기"), DialogOverlay.Choice("닫기")))

        // 8줄짜리 긴 대사 — 현재 take(8) 절단/버튼 겹침 검증용
        dialogShot(game, scene, "dlg_long_8line", "동네 주민",
            "\"겨울 강변은 사람보다 새가 많아요. 우리 동네 겨울 명물이에요.\n" +
                "추운 날엔 새들도 동네 어귀로 모여요. 마트 앞 난로 같은 곳이 있거든요.\n" +
                "눈 오는 아침 출근길이 제일 조용해요. 새도 사람도 발소리를 줄여요.\n" +
                "희귀새 위치를 바로 퍼뜨리기 전에 새가 안전할지 한 번 생각해 주세요.\n" +
                "물가 새는 건너편에서 봐도 충분히 아름다워요.\n" +
                "갯벌에는 사람 눈에 안 보이는 새들의 식탁이 아주 많대요.\n" +
                "철새가 쉬는 곳에서는 무리 쪽으로 걷지 않는 게 이 동네 약속이에요.\n" +
                "오늘도 좋은 새 만나세요!\"",
            listOf(DialogOverlay.Choice("기억할게요")))

        // ---------------------------------------------------------------
        // 장비 선택 — 새 플레이어(컴팩트 1대) / 바디 0대
        // ---------------------------------------------------------------
        s.ownedGear.clear()
        s.ownedGear.add("c_start")           // 새 플레이어: 스타터 컴팩트 1대
        s.compactId = "c_start"
        s.useIlc = false
        overlayShot(game, scene, "gear_pick_compact_1") { GearPickOverlay(scene, GearKind.COMPACT) }
        overlayShot(game, scene, "gear_pick_body_0") { GearPickOverlay(scene, GearKind.BODY) }
        overlayShot(game, scene, "gear_pick_lens_0") { GearPickOverlay(scene, GearKind.LENS) }
        overlayShot(game, scene, "gear_pick_tc_0") { GearPickOverlay(scene, GearKind.TELECONV) }
        overlayShot(game, scene, "gearbag_1") { GearBagOverlay(scene) }

        // 렌즈 1개만 산 경우
        s.ownedGear.add("l_kit1855")
        s.bodyId = "b_apsc_entry"
        s.lensId = "l_kit1855"
        overlayShot(game, scene, "gear_pick_lens_1") { GearPickOverlay(scene, GearKind.LENS) }

        // 장비 5개 — 여러 페이지(2페이지에 1개): 높이가 흔들리지 않는지
        s.ownedGear.addAll(listOf("l_55210", "l_35f18", "l_70300", "l_90macro"))
        overlayShot(game, scene, "gear_pick_lens_5_p1") { GearPickOverlay(scene, GearKind.LENS) }
        val pick = GearPickOverlay(scene, GearKind.LENS)
        scene.openOverlay(pick)
        setField(pick, "page", 1)
        renderAndRecord(game, "gear_pick_lens_5_p2")
        scene.closeOverlay()

        // ---------------------------------------------------------------
        // 액세서리 3개 — 액세서리 줄이 2줄로 감싸지는 경우
        // ---------------------------------------------------------------
        s.ownedGear.addAll(listOf(CameraGear.ACC_STRAP, CameraGear.ACC_BLIND, CameraGear.ACC_ADAPTER))
        overlayShot(game, scene, "gearbag_acc2") { GearBagOverlay(scene) }

        // ---------------------------------------------------------------
        // 접근성 글자 배율 1.3x — 줄 간격·글자가 커져도 겹치지 않는지
        // ---------------------------------------------------------------
        setScale(1.3f)
        dialogShot(game, scene, "dlg13_villager_4line", "동네 주민",
            "\"서울은 복잡하고 사람 많지만, 한강과 하늘을 보면 마음이 트여요.\n" +
                "멀리 가기 전에도 창밖의 새부터 천천히 보면 좋아요.\n" +
                "봄이 오면 이 동네 풍경이 하루에 한 번씩 갈아입혀요. 아침과 저녁이 다른 옷이에요.\n" +
                "이 시간엔 가로등 아래가 제일 밝아요. 새는 이미 자고, 밤새가 근무 중이에요.\"",
            listOf(DialogOverlay.Choice("기억할게요")))
        dialogShot(game, scene, "dlg13_kid_1line", "꼬마",
            "\"우와, 카메라 멋져요! 저도 크면 탐조할 거예요!\"",
            listOf(DialogOverlay.Choice("ㅎㅎ 귀엽다")))
        setScale(1f)

        // ---------------------------------------------------------------
        // 아주 작은 화면 + 긴 대사 — 줄이 잘리고 … 로 마무리되는 경로
        // ---------------------------------------------------------------
        game.onSurfaceChanged(800, 400)
        dialogShot(game, scene, "dlg_tiny_long", "동네 주민",
            "\"겨울 강변은 사람보다 새가 많아요. 우리 동네 겨울 명물이에요.\n" +
                "추운 날엔 새들도 동네 어귀로 모여요. 마트 앞 난로 같은 곳이 있거든요.\n" +
                "눈 오는 아침 출근길이 제일 조용해요. 새도 사람도 발소리를 줄여요.\n" +
                "오늘도 좋은 새 만나세요!\"",
            listOf(DialogOverlay.Choice("기억할게요")))
        game.onSurfaceChanged(SW, SH)

        // ---------------------------------------------------------------
        // 장식 선택 — 장식 1개 보유
        // ---------------------------------------------------------------
        s.decorOwned.clear()
        s.decorOwned.add(0)
        s.decorSlots.fill(0)
        overlayShot(game, scene, "decor_pick_1") { DecorPickOverlay(scene, 0) {} }
        overlayShot(game, scene, "house_style") { HouseStyleOverlay(scene) {} }

        // 장식 3개 보유
        s.decorOwned.addAll(listOf(2, 3))
        overlayShot(game, scene, "decor_pick_3") { DecorPickOverlay(scene, 1) {} }

        tsv.close()
        println("audit done -> ${outDir.absolutePath}")
    }

    // ------------------------------------------------------------------

    private fun simulate(game: Game, seconds: Float, dt: Float = 1f / 30f) {
        var t = 0f
        while (t < seconds) {
            game.update(dt)
            t += dt
        }
    }

    private fun dialogShot(
        game: Game, scene: Scene, name: String,
        title: String, body: String, choices: List<DialogOverlay.Choice>
    ) {
        scene.openOverlay(DialogOverlay(scene, title, body, choices))
        renderAndRecord(game, name)
        scene.closeOverlay()
    }

    private fun overlayShot(game: Game, scene: Scene, name: String, make: () -> com.pizzaandbird.game.Overlay) {
        scene.openOverlay(make())
        renderAndRecord(game, name)
        scene.closeOverlay()
    }

    private fun renderAndRecord(game: Game, name: String) {
        simulate(game, 1.5f)   // 등장 애니메이션(220ms)이 끝난 뒤 프레임
        // 실제 시간 기준 등장 애니메이션이 끝날 때까지 대기 (시뮬레이션이
        // 실시간보다 빨라서 enterShift 가 남아 있으면 배치 측정이 흔들린다)
        (game.scene as? Scene)?.overlay?.let { ov ->
            val deadline = System.nanoTime() + 2_000_000_000L
            while (com.pizzaandbird.game.UiKit.enter(ov.bornAt) < 1f &&
                System.nanoTime() < deadline
            ) Thread.sleep(8)
        }
        val rec = AuditRecorder()
        Canvas.audit = rec
        val bmp = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        game.render(c)
        Canvas.audit = null
        ImageIO.write(bmp.image, "png", File(outDir, "$name.png"))
        // 프레임 해상도 메타 — 분석기가 화면 크기 기준(패널 면적 비율)으로 쓴다
        tsv.println("$name\tscreen\t0\t0\t${game.screenW}\t${game.screenH}\t00000000\t255\tFILL\tfalse\t0\t")
        for (e in rec.events) {
            val text = (e.text ?: "").replace("\t", " ").replace("\n", "\\n")
            tsv.println(
                "$name\t${e.kind}\t${e.l}\t${e.t}\t${e.r}\t${e.b}\t" +
                    "%08X".format(e.color) + "\t${e.alpha}\t${e.style}\t${e.shader}\t${e.textSize}\t$text"
            )
        }
        println("  + $name.png (${rec.events.size} events)")
    }
}
