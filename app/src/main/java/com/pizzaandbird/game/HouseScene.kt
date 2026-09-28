package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.sin

/**
 * **방문한 집의 실내** — 지도의 문 앞에서 A(또는 문을 밟기)로 들어오는 곳.
 *
 * 예전에는 '우리 집'(정착지·매입한 집)만 들어갈 수 있었고, 도시 골목의 상가나
 * 시골 민가는 창문·벽만 있는 장식이었다(제보: "못 들어가는 집이 너무 많다").
 * 이제 모든 집이 들어갈 수 있고, 집마다 주인이 다르며 분위기도 조금씩 다르다.
 *
 *  - 🧑‍🌾 **집주인 인사** — 그 동네 사람이라 동네 이야기·철새 소식을 들려준다.
 *    처음 찾은 집은 손님 기념으로 행운이 오른다(집마다 한 번).
 *  - 🪑 **집 구경** — 마루·살림살이를 둘러본다. 집 종류(민가·상가·한옥)마다 다르다.
 *  - 🚪 문을 밟으면 밖(현관 앞)으로 나간다.
 *
 * 우리 집은 이 씬이 아니라 [HomeScene] 이 맡는다(화덕·침대·인테리어가 있는 곳).
 */
class HouseScene(
    game: Game,
    private val region: RegionDef,
    val house: HouseInfo
) : Scene(game) {

    private val state = game.state
    private val map: GameMap = MapBuilder.buildHouseRoom(house.kind, house.index)
    private val player = Player()
    private val rig = ViewRig(game.state)

    /** 이 집의 주인 — 그 지역 사람 중 하나. 지역에 사람이 없으면 박사가 소개해 준 집이 된다. */
    private val owner: NpcPerson = run {
        val cast = NpcRoster.forRegion(region.id)
        if (cast.isEmpty()) NpcRoster.professor else cast[house.index % cast.size]
    }

    private var camX = 0f
    private var camY = 0f
    private var velX = 0f
    private var velY = 0f
    private var questPathKey = ""
    private var questPath = emptyList<PointF>()
    private var questPathIndex = 0

    override fun cameraOffset(): PointF = PointF(camX, camY)

    private val uiFill = Paint()
    private val aa = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tinyPaint = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply {
        color = 0xFF4A3728.toInt(); textSize = 14f
    }
    private val promptPaint = Paint().apply { color = 0xFFF2D06B.toInt() }

    /** 주인 자리와 둘러볼 자리 (월드 px, 중심) */
    private val ownerX = 11f * 16f
    private val ownerY = 5.5f * 16f

    private class Spot(val key: String, val x: Float, val y: Float, val reachA: Float, val reachTap: Float)

    private val spots = listOf(
        Spot("owner", ownerX, ownerY, 34f, 30f),
        Spot("shelf", 4.5f * 16f, 4f * 16f, 28f, 26f),
        Spot("table", 7.5f * 16f, 7.5f * 16f, 26f, 26f)
    )

    /** 집 종류별 색 — 민가(따뜻한 목재) · 상가(푸른 진열) · 한옥(한지와 먹) */
    private val kindName: String = when (house.kind) {
        1 -> "가게 겸 집"
        2 -> "한옥집"
        3 -> "우리 집"
        else -> "민가"
    }
    private val floorTint: Int = when (house.kind) {
        1 -> 0xFFD8C9A8.toInt()
        2 -> 0xFFE6D5B4.toInt()
        else -> 0xFFDCC69C.toInt()
    }
    private val wallTint: Int = when (house.kind) {
        1 -> 0xFFA9C0CC.toInt()
        2 -> 0xFFBFA88A.toInt()
        else -> 0xFFC8A87C.toInt()
    }
    private val accentTint: Int = when (house.kind) {
        1 -> 0xFF5C7E9A.toInt()
        2 -> 0xFF9A5A46.toInt()
        else -> 0xFF8A6A46.toInt()
    }

    private val title = "${owner.name}의 $kindName"

    init {
        // 남의 집이므로 '집 안에 있다'로 저장하지 않는다 —
        // 앱을 껐다 켰을 때 남의 집 안에서 시작하면 안 된다.
        state.inHome = false
        rig.snap(
            map.w * 8f, map.h * 8f, 1f,
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE
        )
        syncCamera()
        player.set(7.5f * 16f, 9.4f * 16f)
        player.facing = Dir.N

        game.hud.showControls = true
        game.hud.showPunch = false
        game.hud.punchHot = false
        game.hud.showStats = true
        game.hud.showMinimap = false
        game.hud.regionLabel = title
        game.hud.photoModeHint = false
        updateQuestHud()
        game.hud.contextIcon = null
        game.banner(title)
        game.sfx(Audio.Sfx.BAG_OPEN, 0.45f)

        game.audio.playBgm(R.raw.bgm_home)
        applyIndoorAmbience()
        markVisited()
    }

    // -------------------------------------------------------------------
    // 방문 기록 — 집마다 첫 방문 기념(행운)
    // -------------------------------------------------------------------

    private fun visitedKey(): String = "${region.id}#${house.index}"

    private fun markVisited() {
        val seen = visitedHouses()
        if (seen.contains(visitedKey())) return
        seen.add(visitedKey())
        saveVisitedHouses(seen)
        state.luck = (state.luck + 3f).coerceAtMost(100f)
        game.toast("${owner.name}네 집에 처음 와 봤어요 · 행운 +3")
        game.sfx(Audio.Sfx.SPARKLE, 0.6f)
        Healing.unlock(state, "neighbor_home")?.let { m ->
            game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
        }
        SaveManager.save(game.context, state)
    }

    private fun visitedHouses(): MutableSet<String> {
        val h = state.healing
        val arr = h.optJSONArray("housesVisited") ?: return LinkedHashSet<String>()
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val v = arr.optString(i, "")
            if (v.isNotEmpty()) out.add(v)
        }
        return out
    }

    private fun saveVisitedHouses(set: Set<String>) {
        val arr = org.json.JSONArray()
        // 너무 길어지지 않게 최근 60곳만 남긴다
        set.toList().takeLast(60).forEach { arr.put(it) }
        state.healing.put("housesVisited", arr)
    }

    // -------------------------------------------------------------------

    override fun camera(): ViewRig = rig

    override fun update(dt: Float) {
        game.hud.update(dt)
        updateQuestHud()
        applyIndoorAmbience()
        if (overlay != null) {
            game.audio.stopSteps()
            updateRig(dt, 0f, 0f, Gait.IDLE)
            return
        }
        state.playSeconds += dt * 0.4f
        state.advanceClock(dt)

        player.bike = false
        val input = game.input
        val manualMoving = kotlin.math.abs(input.dirX) > 0.02f || kotlin.math.abs(input.dirY) > 0.02f
        if (state.questTravelPlan != null && manualMoving) {
            QuestNavigation.cancel(game, "직접 조작으로 길안내를 취소했어요")
            questPathKey = ""
        }
        val guide = if (state.questTravelPlan != null) guidedTravelDirection() else null
        val dx = guide?.first ?: input.dirX
        val dy = guide?.second ?: input.dirY
        val moving = kotlin.math.abs(dx) > 0.01f || kotlin.math.abs(dy) > 0.01f

        player.moving = moving
        if (moving) {
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) player.facing = if (dx > 0) Dir.E else Dir.W
            else if (dy != 0f) player.facing = if (dy > 0) Dir.S else Dir.N
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            val vx = if (len > 0.01f) dx / len else 0f
            val vy = if (len > 0.01f) dy / len else 0f
            val speed = (if (state.hunger <= 0f) 34f else 55f) * (if (guide != null) 1f else input.moveScale)
            moveBy(vx * speed * dt, 0f)
            moveBy(0f, vy * speed * dt)
            player.play(Anim.WALK, dt, (speed / 55f).coerceIn(0.5f, 1.6f))
            velX = vx * speed
            velY = vy * speed
        } else {
            player.play(Anim.IDLE, dt)
            velX = 0f
            velY = 0f
        }

        game.audio.steps(if (moving) Audio.Steps.WOOD else Audio.Steps.NONE)

        // 현관문을 밟으면 밖으로
        if (map.feetTile(player.x, player.y) == T.HOUSE_DOOR) {
            exitHouse()
            return
        }

        updateRig(dt, velX, velY, if (moving) Gait.WALK else Gait.IDLE)

        state.px = player.x
        state.py = player.y

        game.hud.contextIcon = nearestInteract()?.let {
            when (it.key) {
                "owner" -> "people"
                "shelf" -> "box"
                else -> "chair"
            }
        }
    }

    private fun updateQuestHud() {
        val tracker = QuestNavigation.tracker(state)
        game.hud.questLabel = tracker?.title
        game.hud.questObjective = tracker?.requirement
        game.hud.questProgress = tracker?.progress
        game.hud.questTravelLabel = state.questTravelPlan?.let { "🚲 현관으로 이동 · ${it.targetLabel}" }
    }

    private fun guidedTravelDirection(): Pair<Float, Float>? {
        val plan = state.questTravelPlan ?: run {
            questPathKey = ""
            questPath = emptyList()
            questPathIndex = 0
            return null
        }
        val key = "house:${region.id}:${house.index}:${plan.hashCode()}"
        if (questPathKey != key) {
            val goal = PointF(7.5f * 16f, (map.h - 1) * 16f - 1f)
            val path = QuestPathfinder.findPath(map, player.x, player.y, goal.x, goal.y)
            if (path == null) {
                QuestNavigation.cancel(game, "실내 출구까지 이어지는 길을 찾지 못했어요")
                questPathKey = ""
                return null
            }
            questPathKey = key
            questPath = path
            questPathIndex = 0
        }
        while (questPathIndex < questPath.size &&
            hypot(questPath[questPathIndex].x - player.x, questPath[questPathIndex].y - player.y) < 5f
        ) questPathIndex++
        val waypoint = questPath.getOrNull(questPathIndex) ?: return null
        return (waypoint.x - player.x) to (waypoint.y - player.y)
    }

    private fun updateRig(dt: Float, vx: Float, vy: Float, gait: Gait) {
        rig.update(
            dt,
            map.w * 8f, map.h * 8f,
            vx, vy, gait, 1f,
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE
        )
        syncCamera()
    }

    private fun syncCamera() {
        val z = rig.zoom
        val hx = game.virtW / 2f
        val hy = game.virtH / 2f
        camX = (rig.x * WORLD_SCALE - hx + game.virtW / (2f * z)) / WORLD_SCALE
        camY = (rig.y * WORLD_SCALE - hy + game.virtH / (2f * z)) / WORLD_SCALE
    }

    private fun moveBy(dx: Float, dy: Float) {
        val nx = player.x + dx
        val ny = player.y + dy
        if (!map.solidBox(nx, ny)) {
            player.x = nx
            player.y = ny
        }
    }

    private fun exitHouse() {
        state.px = player.x
        state.py = player.y
        SaveManager.save(game.context, state)
        game.audio.stopSteps()
        game.fadeTo {
            game.scene = WorldScene(game, region.id, SpawnKind.HOUSE_DOOR, doorX = house.doorX, doorY = house.doorY)
        }
    }

    // -------------------------------------------------------------------
    // 상호작용
    // -------------------------------------------------------------------

    private fun nearestSpot(x: Float, y: Float, tap: Boolean): Spot? {
        var best: Spot? = null
        var bestD = Float.MAX_VALUE
        for (sp in spots) {
            val d = hypot(sp.x - x, sp.y - y)
            val reach = if (tap) sp.reachTap else sp.reachA
            if (d < reach && d < bestD) { best = sp; bestD = d }
        }
        return best
    }

    private fun nearestInteract(): Spot? = nearestSpot(player.cx, player.cy, tap = false)

    /** 주인 대사 — 동네 사람이라 그 지역 이야기를 들려준다. */
    private fun ownerLine(): String {
        val extra = owner.lines.firstOrNull { it.isNotBlank() }
        val greeting = when (house.kind) {
            1 -> "가게이자 우리 집이에요. 자전거 바퀴는 여기서 빵빵하게 하고 가세요."
            2 -> "한옥이라 마루가 시원해요. 여름엔 여기가 제일 좋답니다."
            else -> "멀리서 오셨네요. 들어와서 좀 쉬다 가세요."
        }
        val regionLine = region.desc.ifBlank { "여긴 조용한 동네예요." }
        val tip = if (region.tip.isNotBlank()) "\n\n탐조 팁 · ${region.tip}" else ""
        val mine = if (extra != null && extra != greeting) "\n\n“$extra”" else ""
        return "$greeting\n\n$regionLine$mine$tip"
    }

    private fun interact(key: String) {
        when (key) {
            "owner" -> openOverlay(
                DialogOverlay(
                    this, "${owner.name} · ${owner.title}",
                    ownerLine(),
                    listOf(
                        DialogOverlay.Choice("동네 이야기 더 듣기") {
                            DialogOverlay(it.scene, owner.name,
                                "${region.name} 이야기 · ${region.tip.ifBlank { "새들은 아침 일찍 물가에 모여요." }}",
                                listOf(DialogOverlay.Choice("고마워요")))
                        },
                        DialogOverlay.Choice("잘 쉬다 갈게요")
                    )
                )
            )
            "shelf" -> openOverlay(
                DialogOverlay(
                    this, "살림살이",
                    when (house.kind) {
                        1 -> "장부와 물건이 가지런히 놓여 있어요. 오늘 장사는 좀 어땠을까요?"
                        2 -> "한지 문풍지가 바람을 막고, 마루엔 햇살이 내려앉아요."
                        else -> "장작이 쌓여 있고, 항아리엔 겨울 김장이 익어 가요."
                    },
                    listOf(DialogOverlay.Choice("그렇구나"))
                )
            )
            "table" -> openOverlay(
                DialogOverlay(
                    this, "마루",
                    "따뜻한 물 한 잔을 권해 주네요. 잠깐 앉아 숨을 고릅니다.",
                    listOf(
                        DialogOverlay.Choice("한 잔 마시기") {
                            state.hunger = (state.hunger + 4f).coerceAtMost(100f)
                            game.toast("따뜻한 물 한 잔 · 배고픔 +4")
                            game.sfx(Audio.Sfx.EAT, 0.5f)
                        },
                        DialogOverlay.Choice("괜찮아요")
                    )
                )
            )
        }
    }

    override fun handleInput(input: Input) {
        if (state.questTravelPlan != null && (input.justA || input.justEat)) {
            QuestNavigation.cancel(game, "직접 조작으로 길안내를 취소했어요")
            questPathKey = ""
        }
        if (input.justBack || input.justMenu) {
            openOverlay(MenuOverlay(this, MenuOverlay.TAB_BAG))
            return
        }
        if (input.justCam) {
            game.toast("남의 집에서 카메라는 예의가 아니에요!")
            return
        }
        if (input.justQuest) {
            if (!QuestNavigation.autoTravelTracked(game, this)) QuestNavigation.openTracker(this)
            return
        }
        if (input.justB) {
            game.toast("실내에서 자전거는 위험해요!")
            return
        }
        if (input.justEatPick) {
            openQuickPizza()
            return
        }
        if (input.justEat) {
            quickEatSlice()
            return
        }
        if (input.justA) {
            nearestInteract()?.let { interact(it.key) }
            return
        }
        val tap = input.consumeTapWorld()
        if (tap != null && state.questTravelPlan != null) {
            QuestNavigation.cancel(game, "직접 조작으로 길안내를 취소했어요")
            questPathKey = ""
        }
        if (tap != null) {
            nearestSpot(tap.x, tap.y, tap = true)?.let { interact(it.key) }
        }
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFF241E2C.toInt())
        val camXv = camX * WORLD_SCALE
        val camYv = camY * WORLD_SCALE
        val hx = game.virtW / 2f
        val hy = game.virtH / 2f
        c.save()
        if (rig.roll != 0f) c.rotate(rig.roll, hx, hy)
        if (rig.zoom != 1f) c.scale(rig.zoom, rig.zoom, hx, hy)

        map.draw(c, game.assets, camXv, camYv, game.virtW, game.virtH, game.time)
        drawRoomTint(c)
        drawVista(c)
        drawProps(c)
        drawOwner(c)
        drawPlayer(c)

        val a = game.assets
        c.drawBitmap(a.vignette, null, RectF(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat()), a.sprPaint)

        nearestInteract()?.let { sp ->
            val bob = sin(game.time * 3f) * 2.5f
            val bx = (sp.x - camX) * WORLD_SCALE
            val by = (sp.y - camY) * WORLD_SCALE - 30f + bob
            c.drawCircle(bx, by, 9f, promptPaint)
            tinyPaint.textSize = 14f
            val tw = tinyPaint.measureText("!")
            c.drawText("!", bx - tw / 2, by + 5f, tinyPaint)
        }
        c.restore()
    }

    /** 바닥·벽에 집마다 다른 색을 얹어 분위기를 가른다. */
    private fun drawRoomTint(c: Canvas) {
        val p = uiFill
        for (y in 0 until map.h) {
            for (x in 0 until map.w) {
                val tile = map.t(x, y)
                val sx = (x * 16f - camX) * WORLD_SCALE
                val sy = (y * 16f - camY) * WORLD_SCALE
                when (tile) {
                    T.FLOOR -> {
                        p.color = Color.argb(92, Color.red(floorTint), Color.green(floorTint), Color.blue(floorTint))
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, p)
                    }
                    T.WALL_IN -> {
                        p.color = Color.argb(124, Color.red(wallTint), Color.green(wallTint), Color.blue(wallTint))
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, p)
                    }
                    else -> Unit
                }
            }
        }
    }

    /** 창밖 풍경 — 지금 시각·계절·날씨를 그대로 비춘다 (집집마다 같은 하늘을 본다) */
    private fun drawVista(c: Canvas) {
        val windows = listOf(2, 6, 10, 13)
        val hour = state.worldTime
        val top = DayCycle.skyTopColor(hour, state.season())
        val bottom = DayCycle.skyColor(hour, state.season())
        for (wx in windows) {
            val sx = (wx * 16f - camX) * WORLD_SCALE
            val sy = (1f * 16f - camY) * WORLD_SCALE
            val gl = sx + 7f
            val gt = sy + 10f
            val gr = sx + 25f
            val gb = sy + 30f
            val steps = 6
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                aa.color = Color.argb(
                    160,
                    lerpCh(top, bottom, t, 0),
                    lerpCh(top, bottom, t, 1),
                    lerpCh(top, bottom, t, 2)
                )
                val yy0 = gt + (gb - gt) * (i / steps.toFloat())
                val yy1 = gt + (gb - gt) * ((i + 1) / steps.toFloat())
                c.drawRect(gl, yy0, gr, yy1, aa)
            }
            // 멀리 지나가는 새 실루엣
            aa.color = Color.argb(120, 60, 54, 60)
            val bx = gl + 3f + ((game.time * 6f + wx * 7f) % (gr - gl - 6f))
            val by = gt + 4f + sin(game.time * 1.6f + wx) * 2.5f
            c.drawLine(bx, by, bx + 2.4f, by - 1.2f, aa)
            c.drawLine(bx + 2.4f, by - 1.2f, bx + 4.8f, by, aa)
        }
    }

    private fun lerpCh(c0: Int, c1: Int, t: Float, ch: Int): Int {
        val a = when (ch) { 0 -> Color.red(c0); 1 -> Color.green(c0); else -> Color.blue(c0) }
        val b = when (ch) { 0 -> Color.red(c1); 1 -> Color.green(c1); else -> Color.blue(c1) }
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }

    /** 살림살이 — 집 종류마다 다르게 그린다. */
    private fun drawProps(c: Canvas) {
        val shelfX = 4.5f * 16f
        val shelfY = 4f * 16f
        run {
            val bx = (shelfX - camX) * WORLD_SCALE
            val by = (shelfY - camY) * WORLD_SCALE
            aa.color = Color.argb(70, 20, 24, 20)
            c.drawOval(RectF(bx - 30f, by + 18f, bx + 30f, by + 30f), aa)
            // 벽 선반
            aa.color = 0xFF7A5A3A.toInt()
            c.drawRect(bx - 30f, by - 24f, bx + 30f, by - 18f, aa)
            c.drawRect(bx - 30f, by - 2f, bx + 30f, by + 4f, aa)
            when (house.kind) {
                1 -> {
                    // 가게 진열 — 병과 상자
                    val cols = intArrayOf(0xFF5C7E9A.toInt(), 0xFFD9A46E.toInt(), 0xFF8FAF7A.toInt(), 0xFFE0A93C.toInt())
                    for (i in 0 until 4) {
                        aa.color = cols[i]
                        c.drawRect(bx - 26f + i * 13f, by - 18f, bx - 18f + i * 13f, by - 4f, aa)
                    }
                    aa.color = 0xFFB08A5C.toInt()
                    c.drawRect(bx - 24f, by - 2f, bx + 24f, by + 14f, aa)
                }
                2 -> {
                    // 한옥 — 항아리와 한지 등
                    aa.color = 0xFF8A6A46.toInt()
                    c.drawRect(bx - 26f, by - 18f, bx - 12f, by - 4f, aa)
                    aa.color = 0xFFC8B18A.toInt()
                    c.drawOval(RectF(bx - 6f, by - 18f, bx + 10f, by - 4f), aa)
                    aa.color = 0xFFF2E3C2.toInt()
                    c.drawRect(bx + 16f, by - 22f, bx + 28f, by - 4f, aa)
                }
                else -> {
                    // 민가 — 장작과 항아리
                    aa.color = 0xFF8A6A46.toInt()
                    for (i in 0 until 4) c.drawRect(bx - 28f + i * 8f, by - 20f, bx - 24f + i * 8f, by - 6f, aa)
                    aa.color = 0xFF6B4F35.toInt()
                    c.drawOval(RectF(bx + 4f, by - 4f, bx + 26f, by + 16f), aa)
                }
            }
        }

        // 마루(낮은 상) — 가운데
        val tableX = 7.5f * 16f
        val tableY = 7.5f * 16f
        run {
            val bx = (tableX - camX) * WORLD_SCALE
            val by = (tableY - camY) * WORLD_SCALE
            aa.color = Color.argb(70, 20, 24, 20)
            c.drawOval(RectF(bx - 22f, by + 10f, bx + 22f, by + 20f), aa)
            aa.color = 0xFF8A6A46.toInt()
            c.drawRect(bx - 20f, by - 4f, bx + 20f, by + 10f, aa)
            aa.color = 0xFFA8855C.toInt()
            c.drawRect(bx - 20f, by - 4f, bx + 20f, by + 1f, aa)
            // 다기 세트
            aa.color = 0xFFF2E3C2.toInt()
            c.drawOval(RectF(bx - 6f, by - 9f, bx + 4f, by - 1f), aa)
            aa.color = accentTint
            c.drawOval(RectF(bx + 6f, by - 7f, bx + 12f, by - 1f), aa)
        }
    }

    private fun drawOwner(c: Canvas) {
        val a = game.assets
        val sx = (ownerX - 8f - camX) * WORLD_SCALE
        val sy = (ownerY - 13f - camY) * WORLD_SCALE
        c.drawOval(RectF(sx + 8f, sy + 26f, sx + 24f, sy + 32f), a.shadowPaint)
        val bmp = a.npcBitmap(owner, game.time, house.index * 0.37f, game.hdSprites)
        a.drawPlayer(c, bmp, sx, sy, SPRITE_DOT_K)
        val bx = sx + 16f
        val by = sy - 12f
        aa.color = 0xFF8FC7F0.toInt()
        c.drawCircle(bx, by, 8f, aa)
        aa.style = Paint.Style.STROKE
        aa.strokeWidth = 1.6f
        aa.color = 0xFF2E5E6E.toInt()
        c.drawCircle(bx, by, 8f, aa)
        aa.style = Paint.Style.FILL
        UiKit.iconCenter(c, game, "note", bx, by, 10f)
    }

    private fun drawPlayer(c: Canvas) {
        val a = game.assets
        val sx = (player.x - camX) * WORLD_SCALE
        val sy = (player.y - camY) * WORLD_SCALE
        c.drawBitmap(a.softShadow, null, RectF(sx + 1f, sy + 21f, sx + 31f, sy + 34f), a.sprPaint)
        val hd = game.hdSprites
        val ps = a.playerSet(state.gender, state.gearTier(), hd)
        val clip = ps.clip(player.anim)
        val bmp = clip.frame(player.facing, player.frame)
        a.drawPlayer(c, bmp, sx, sy - clip.topPad, SPRITE_DOT_K)
        Charms.equipped(state)?.let { item ->
            Charms.draw(c, item, sx + if (player.facing == Dir.W) 8f else 24f,
                sy - clip.topPad + if (item.id == "rain") 12f else 22f, 8f, game.time)
        }
        val camDir = when (player.facing) {
            Dir.E -> 2
            Dir.W -> 3
            Dir.N -> 1
            else -> 0
        }
        a.drawPlayer(c, a.camHeld(state.rig().look, camDir, false, hd), sx, sy - clip.topPad, SPRITE_DOT_K)
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
