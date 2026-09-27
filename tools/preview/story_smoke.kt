package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.StubText
import com.pizzaandbird.game.*
import java.io.File

/**
 * [P08] 사이드 스토리 & 대사 시스템 헤드리스 E2E 스모크.
 *
 *  1) 조건부 대사 매트릭스: 4계절 × 5날씨 × 밤/낮 × 장(0/2/4/6) × 지역 — villager/kid/elder 전 조합 생성 검사
 *  2) 신규 조건부 대사 수량 60줄 이상 (DoD) + 이동 보존분 6+6줄 확인
 *  3) 12편 에피소드 전체 E2E: 도입 → 의뢰 → 심부름 미충족 nudge → 충족 → 마무리+보상 → 완료(재방문 무반복)
 *  4) 💬 마커: 담당 NPC에만 표시, 완료 후 소멸
 *  5) 숨은 에필로그 「수첩의 뒷장」 1회성 + 보상
 *  6) 대화 화면 캡처 8장 (docs/img/story/ 또는 실행 인자 폴더)
 *
 * 실행: tools/preview/README.md의 `입력 회귀 테스트`와 동일한 방식 (kotlinc + java).
 *   SRCS=$(find app/src/main/java/com/pizzaandbird/game -name '*.kt' ! -name 'MainActivity.kt' ! -name 'GameView.kt')
 *   kotlinc (tools/preview/src의 모든 스텁) tools/preview/story_smoke.kt $SRCS -d out/story-smoke -jvm-target 17
 *   java -cp "out/story-smoke:<kotlin-stdlib>" com.pizzaandbird.preview.StorySmoke [출력폴더]
 */
object StorySmoke {

    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    private var outDir = File("tools/preview/out/story_smoke")

    private fun overlayOf(g: Game): DialogOverlay? = g.scene.overlay as? DialogOverlay

    private fun privateField(o: Any, name: String): Any? {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(o)
    }

    private fun bodyOf(o: DialogOverlay): String = privateField(o, "body") as String
    private fun titleOf(o: DialogOverlay): String = privateField(o, "title") as String

    /** 선택지 클릭 (액션 실행 → 오버레이 자동 닫힘) */
    private fun pick(o: DialogOverlay, i: Int = 0) {
        val m = DialogOverlay::class.java.getDeclaredMethod("pick", Int::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(o, i)
    }

    /** private WorldScene.talkTo 호출 — 기존 대사 경로(=Dialogues 연결)를 실제로 통과시킨다 */
    private fun talk(g: Game, kind: NpcKind) {
        val w = g.scene as WorldScene
        val npc = w.map.npcs.first { it.kind == kind }
        val m = WorldScene::class.java.getDeclaredMethod("talkTo", Npc::class.java)
        m.isAccessible = true
        m.invoke(w, npc)
    }

    private fun render(g: Game, name: String) {
        // 오버레이 등장 애니메이션(Game.overlayAnimT, update에서만 진행)이 끝난 뒤 캡처한다
        repeat(6) { g.update(1f / 30f) }
        val bmp = Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888)
        g.render(Canvas(bmp))
        val f = File(outDir, "$name.png")
        javax.imageio.ImageIO.write(bmp.image, "png", f)
        println("  + ${f.path}")
    }

    private fun settle(g: Game, frames: Int = 30) {
        repeat(frames) { g.update(1f / 30f) }
    }

    private fun photoMatchForSmoke(def: BirdDef, goal: SideStories.Goal.Photo): Boolean {
        if (goal.birdId != null && def.id != goal.birdId) return false
        if (goal.habitat != null && goal.habitat !in def.habitats) return false
        if (goal.migration != null && !def.migrationLabel.contains(goal.migration)) return false
        return def.tier.star >= goal.tierMin
    }

    // -------------------------------------------------------------------

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("awt.headless", "true")
        outDir = File(args.getOrElse(0) { "tools/preview/out/story_smoke" })
        outDir.mkdirs()
        val fontDir = File("tools/preview/fonts")
        if (fontDir.exists()) StubText.loadFromDir(fontDir)

        // ---------------- 1) 조건부 대사 매트릭스 ----------------
        val seasons = Season.values()
        val weathers = Weather.values()
        var combos = 0
        for (s in seasons) for (w in weathers) for (night in listOf(false, true)) for (ch in intArrayOf(0, 2, 4, 6)) {
            for (region in listOf("seoul", "jeju")) {
                val c = Dialogues.Ctx(region, ch, s, w, night, if (s == Season.WINTER) 23 else 2)
                val v = Dialogues.villager(c)
                val k = Dialogues.kid(c)
                val e = Dialogues.elder(c)
                for (t in listOf(v, k, e)) {
                    check(t.startsWith("\"") && t.endsWith("\"")) { "따옴표 규칙 위반: $t" }
                    check("null" !in t) { "null 노출: $t" }
                }
                check(v.contains(Dialogues.storyHint(ch))) { "villager에 storyHint 누락 (장 $ch)" }
                check(!k.contains("\n")) { "꼬마는 한 줄이어야 한다: $k" }
                check(!e.contains("\n")) { "할머니는 한 줄이어야 한다: $e" }
                combos++
            }
        }
        println("① 대사 매트릭스 ${combos * 3}개 조합 생성 OK")

        // ---------------- 2) 신규 문구 수량 (DoD: 60줄 이상) ----------------
        val d = Dialogues::class.java.getDeclaredField("INSTANCE").get(null)!!
        fun strList(name: String): List<*> {
            val f = Dialogues::class.java.getDeclaredField(name)
            f.isAccessible = true
            return f.get(d) as List<*>
        }
        @Suppress("UNCHECKED_CAST")
        fun seasonMap(name: String): Collection<List<*>> {
            val f = Dialogues::class.java.getDeclaredField(name)
            f.isAccessible = true
            return (f.get(d) as Map<Any, List<*>>).values
        }
        val newLines =
            strList("KID_NIGHT").size + strList("ELDER_NIGHT").size + strList("VILLAGER_NIGHT").size +
            seasonMap("KID_SEASON").sumOf { it.size } + seasonMap("ELDER_SEASON").sumOf { it.size } +
            seasonMap("VILLAGER_SEASON").sumOf { it.size } + seasonMap("KID_WEATHER").sumOf { it.size } +
            seasonMap("ELDER_WEATHER").sumOf { it.size } + seasonMap("VILLAGER_WEATHER").sumOf { it.size }
        check(newLines >= 60) { "신규 조건부 대사 ${newLines}줄 < 60 (DoD 미달)" }
        check(strList("KID_BASE").size == 6 && strList("ELDER_BASE").size == 6) { "이동 보존분 6줄씩 확인" }
        println("② 신규 조건부 대사 ${newLines}줄 (≥60) + 이동 보존 6+6줄 OK")

        // ---------------- 3) 조건부 대사 실제 talkTo 경로 캡처 (5장) ----------------
        // 에피소드 담당 NPC가 아닌 조합으로만 말을 걸어 기존 대사 경로를 검증한다.
        val cap = Game(TestContext())
        cap.onSurfaceChanged(1600, 900)
        cap.state.started = true
        fun dialogueShot(regionId: String, day: Int, hour: Float, weatherId: String, kind: NpcKind, name: String) {
            cap.state.day = day
            cap.state.worldTime = hour
            cap.state.weatherId = weatherId
            cap.state.px = 21f * 16f
            cap.state.py = 14f * 16f
            cap.scene = WorldScene(cap, regionId, SpawnKind.SAVED)
            settle(cap)
            talk(cap, kind)
            val ov = overlayOf(cap) ?: error("대화상자가 열리지 않음: $name")
            render(cap, name)
            pick(ov)
            cap.scene.closeOverlay()
        }
        dialogueShot("chuncheon", 2, 10f, Weather.SUNNY.id, NpcKind.VILLAGER, "01_villager_spring_day")
        dialogueShot("chuncheon", 23, 22.5f, Weather.SNOW.id, NpcKind.VILLAGER, "02_villager_winter_night_snow")
        dialogueShot("seoul", 9, 12f, Weather.RAIN.id, NpcKind.KID, "03_kid_summer_rain")
        dialogueShot("seoul", 16, 12f, Weather.WIND.id, NpcKind.ELDER, "04_elder_autumn_wind")
        dialogueShot("seoul", 23, 23f, Weather.SNOW.id, NpcKind.ELDER, "05_elder_winter_night")
        println("③ 조건부 대사 캡처 5장 OK")

        // ---------------- 4) 12편 에피소드 전체 E2E + 제주 시나리오 캡처 3장 ----------------
        val g = Game(TestContext())
        g.onSurfaceChanged(1600, 900)
        g.state.started = true
        var totalReward = 0
        for (ep in SideStories.EPISODES) {
            check(SideStories.progress(g.context, ep.regionId) == 0) { "${ep.id} 초기 진행 아님" }
            check(SideStories.current(g.context, ep.regionId) == ep)
            val w = WorldScene(g, ep.regionId, SpawnKind.SAVED)
            g.scene = w
            val npc = w.map.npcs.first { it.kind == ep.npc }
            check(SideStories.hasMarker(g.context, ep.regionId, ep.npc)) { "${ep.id} 💬 마커 없음" }
            check(!SideStories.hasMarker(g.context, ep.regionId, NpcKind.PROFESSOR))

            // 도입 (막 1)
            check(SideStories.intercept(w, npc)) { "${ep.id} 도입 인터셉트 실패" }
            var ov = overlayOf(g)!!
            check(titleOf(ov) == ep.title) { "${ep.id} 제목 불일치: ${titleOf(ov)}" }
            if (ep.regionId == "jeju") render(g, "06_jeju_intro")
            pick(ov)
            check(SideStories.progress(g.context, ep.regionId) == 1)

            // 의뢰 (막 2)
            check(SideStories.intercept(w, npc))
            ov = overlayOf(g)!!
            pick(ov)
            check(SideStories.progress(g.context, ep.regionId) == 2)

            // 심부름 판정 (대화 시점에만) — Photo/Deliver형은 미충족 시 nudge, Talk형은 대화로 바로 완수
            val goal = ep.acts[1].goal
            var tempBird: String? = null
            if (goal !is SideStories.Goal.Talk) {
                check(SideStories.intercept(w, npc))
                ov = overlayOf(g)!!
                check(bodyOf(ov) == "\"${ep.nudge}\"") { "${ep.id} nudge 불일치: ${bodyOf(ov)}" }
                if (ep.regionId == "jeju") render(g, "07_jeju_nudge")
                pick(ov)
            }
            if (goal is SideStories.Goal.Photo) {
                val match = Birds.byId.values.firstOrNull { photoMatchForSmoke(it, goal) }
                    ?: error("${ep.id} 목표를 만족할 수 있는 새가 없다: $goal")
                g.state.birdCounts[match.id] = 1
                tempBird = match.id
            }
            // [P7] 피자 확장 — Deliver형 목표: 전달용 특산 피자를 미리 구워 둔다.
            // (에피소드 완료 처리가 피자를 1판 소비하므로 별도 정리는 불필요)
            if (goal is SideStories.Goal.Deliver) {
                val min = maxOf(12, goal.pizzaIdMin)
                val deliverable = Pizzas.ALL.firstOrNull { it.id >= min }
                    ?: error("${ep.id} 전달 가능한 피자(>=$min)가 정의에 없다")
                check(g.state.addPizza(deliverable.id, 2)) { "피자 보관 한도 초과로 전달 피자를 못 넣었다" }
            }

            // 마무리 + 보상 (막 3)
            val money0 = g.state.money
            val luck0 = g.state.luck
            check(SideStories.intercept(w, npc))
            ov = overlayOf(g)!!
            check(titleOf(ov) == ep.title)
            if (ep.regionId == "jeju") render(g, "08_jeju_final")
            pick(ov)
            tempBird?.let { g.state.birdCounts.remove(it) }
            check(SideStories.progress(g.context, ep.regionId) == 4) { "${ep.id} 완료 아님" }
            check(g.state.money == money0 + ep.reward.first) { "${ep.id} 골드 보상 오차" }
            // 행운은 게임 전체 규칙상 100에서 캡된다 (restAtBench 등과 동일)
            check(g.state.luck == (luck0 + ep.reward.second).coerceAtMost(100f)) { "${ep.id} 행운 보상 오차" }
            totalReward += ep.reward.first

            // 완료 후: 마커 소멸. 단, 마지막(12번째) 편을 끝낸 직후 대화는 숨은 에필로그가 이어받는다(정상).
            check(!SideStories.hasMarker(g.context, ep.regionId, ep.npc))
            if (ep.regionId != SideStories.EPISODES.last().regionId) {
                check(!SideStories.intercept(w, npc)) { "${ep.id} 완료 후 반복" }
            }
        }
        println("④ 12편 에피소드 E2E OK (총 보상 ${won(totalReward)}, nudge 경로 포함)")

        // ---------------- 5) 숨은 에필로그 (12편 완료 후 1회) ----------------
        val w2 = WorldScene(g, "seoul", SpawnKind.SAVED)
        g.scene = w2
        val prof = w2.map.npcs.first { it.kind == NpcKind.PROFESSOR }
        val money0 = g.state.money
        check(SideStories.intercept(w2, prof)) { "에필로그가 열리지 않음" }
        val epi = overlayOf(g)!!
        check(titleOf(epi) == "수첩의 뒷장") { "에필로그 제목 불일치: ${titleOf(epi)}" }
        render(g, "09_epilogue")
        pick(epi)
        check(g.state.money == money0 + 30000) { "에필로그 보상 오차" }
        check(!SideStories.intercept(w2, prof)) { "에필로그 반복 노출" }
        // 에필로그 후 12편 전부 재방문 → 어디서도 반복되지 않는다(제주편의 '완료 후 무반복'까지 여기서 검증)
        for (ep in SideStories.EPISODES) {
            val w3 = WorldScene(g, ep.regionId, SpawnKind.SAVED)
            g.scene = w3
            val npc3 = w3.map.npcs.first { it.kind == ep.npc }
            check(!SideStories.intercept(w3, npc3)) { "${ep.id} 에필로그 후 반복" }
        }
        // 에필로그 후에는 다시 기존 대사(조건부)로 — 실제 talkTo 경로 확인
        talk(g, NpcKind.PROFESSOR)
        check(overlayOf(g) != null)
        println("⑤ 숨은 에필로그 1회성 + 보상 + 12편 재방문 무반복 OK")

        // ---------------- 6) 세이브 키 확인 (규칙 3 — feat_story_v1) ----------------
        val saved = g.context.getSharedPreferences("pizza_and_bird_save", Context.MODE_PRIVATE)
            .getString(SideStories.SAVE_KEY, null)
        check(saved != null && saved.contains("\"epi\":true") && saved.contains("\"jeju\":4")) { "feat_story_v1 저장 이상: $saved" }
        println("⑥ 세이브 키 ${SideStories.SAVE_KEY} OK")

        println("\n✅ StorySmoke 전체 통과 — 캡처 ${outDir.list()?.size ?: 0}장: $outDir")
    }
}
