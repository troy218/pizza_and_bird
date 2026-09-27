package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 지역 랜드마크 내부.
 *
 * 우리 집처럼 문으로 들어와 돌아다니며,
 *  🧭 안내인과 대화(그 지역 이야기 · 탐조 팁)
 *  🖼 전시/전망 관람(첫 관람 보상 + 행운)
 *  ☕ 휴게 공간에서 쉬기(배고픔 회복 + 행운)
 * 를 할 수 있다. 분위기(바닥·벽·창밖 전망)는 랜드마크 테마에 따라 달라진다.
 */
class LandmarkScene(game: Game, private val region: RegionDef) : Scene(game) {

    private val state = game.state
    private val landmark: Landmark = Landmarks.forRegion(region.id)
        ?: Landmark(
            region.id, "${region.name} 안내소", region.emoji, LandmarkTheme.NATURE_CENTER,
            region.desc, "안내원", "${region.name}에 오신 걸 환영해요!",
            region.desc, region.tip
        )
    private val theme = landmark.theme
    val map: GameMap = MapBuilder.buildLandmark(region)
    private val player = Player()
    private val rig = ViewRig(game.state)

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

    // 상호작용 대상 위치 (월드 px, 중심)
    private val exhibitX = 7.5f * 16f
    private val exhibitY = 4f * 16f
    private val restX = 3f * 16f
    private val restY = 8.2f * 16f
    private val docentX = 12f * 16f
    private val docentY = 7.2f * 16f

    private class Spot(val key: String, val x: Float, val y: Float, val reachA: Float, val reachTap: Float)

    private val spots = listOf(
        Spot("exhibit", exhibitX, exhibitY, 34f, 30f),
        Spot("rest", restX, restY, 30f, 26f),
        Spot("docent", docentX, docentY, 32f, 28f)
    )

    init {
        state.inHome = false
        rig.snap(
            map.w * 8f, map.h * 8f, 1f,
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE
        )
        syncCamera()
        player.set(7.5f * 16f, 9.4f * 16f)
        player.facing = Dir.N
        state.px = player.x
        state.py = player.y

        game.hud.showControls = true
        game.hud.showPunch = false
        game.hud.punchHot = false
        game.hud.showStats = true
        game.hud.showMinimap = false
        game.hud.regionLabel = landmark.name
        game.hud.photoModeHint = false
        updateQuestHud()
        game.hud.contextIcon = null
        game.banner(landmark.name)

        game.audio.playBgm(R.raw.bgm_home)
        applyIndoorAmbience()   // 비 오는 날엔 지붕 빗소리, 맑으면 창밖 계절 소리
    }

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

        // 출구
        if (map.feetTile(player.x, player.y) == T.LANDMARK_DOOR) {
            exitLandmark()
            return
        }

        updateRig(dt, velX, velY, if (moving) Gait.WALK else Gait.IDLE)

        state.px = player.x
        state.py = player.y

        game.hud.contextIcon = nearestInteract()?.let {
            when (it.key) {
                "exhibit" -> "photo"
                "rest" -> "coffee"
                "docent" -> "note"
                else -> "pin"
            }
        }
    }

    private fun updateQuestHud() {
        val tracker = QuestNavigation.tracker(state)
        game.hud.questLabel = tracker?.title
        game.hud.questObjective = tracker?.requirement
        game.hud.questProgress = tracker?.progress
        game.hud.questTravelLabel = state.questTravelPlan?.let { "🚲 ${it.targetLabel} · 현관으로 이동 중" }
    }

    private fun guidedTravelDirection(): Pair<Float, Float>? {
        val plan = state.questTravelPlan ?: run {
            questPathKey = ""
            questPath = emptyList()
            questPathIndex = 0
            return null
        }
        val key = "landmark:${region.id}:${plan.hashCode()}"
        if (questPathKey != key) {
            val goal = PointF(map.landmarkDoorX * 16f + 8f, map.landmarkDoorY * 16f - 1f)
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

    private fun exitLandmark() {
        SaveManager.save(game.context, state)
        game.audio.stopSteps()
        game.sfx(Audio.Sfx.TAP, 0.55f)
        game.fadeTo {
            game.scene = WorldScene(game, region.id, SpawnKind.LANDMARK)
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

    private fun interact(key: String) {
        when (key) {
            "exhibit" -> openOverlay(
                DialogOverlay(
                    this, landmark.name, landmark.exhibit,
                    listOf(
                        DialogOverlay.Choice("관람하기") { viewExhibit() },
                        DialogOverlay.Choice("닫기")
                    )
                )
            )
            "rest" -> openOverlay(
                DialogOverlay(
                    this, "휴게 공간",
                    "창밖 풍경을 보며 잠시 쉬어 갈 수 있어요.\n따뜻한 차 한 잔에 몸도 마음도 가벼워져요.",
                    listOf(
                        DialogOverlay.Choice("잠시 쉬기") { rest() },
                        DialogOverlay.Choice("괜찮아요")
                    )
                )
            )
            "docent" -> openOverlay(
                DialogOverlay(
                    this, landmark.docent,
                    landmark.greeting +
                        "\n\n" + landmark.highlight +
                        (if (landmark.tagline.isNotBlank()) "\n\n“${landmark.tagline}”" else "") +
                        (if (region.tip.isNotBlank()) "\n\n탐조 팁 · ${region.tip}" else ""),
                    listOf(DialogOverlay.Choice("고마워요"))
                )
            )
        }
    }

    private var lastExhibitT = -999f
    private var lastRestT = -999f

    private fun viewExhibit() {
        if (region.id !in state.landmarksSeen) {
            state.landmarksSeen.add(region.id)
            val reward = 30000
            state.money += reward
            state.luck = (state.luck + 8f).coerceAtMost(100f)
            game.toast("첫 관람 기념! ${won(reward)} · 행운 +8")
            game.sfx(Audio.Sfx.SPARKLE, 0.85f)
        } else {
            // 재관람 연타로 행운을 무한정 올리지 못하게 쿨다운
            if (game.time - lastExhibitT < 60f) {
                game.toast("조금 전에 감상했어요~ 천천히 둘러보세요")
                return
            }
            lastExhibitT = game.time
            state.luck = (state.luck + 3f).coerceAtMost(100f)
            game.toast("전망을 감상했다 · 행운 +3")
            game.sfx(Audio.Sfx.SPARKLE, 0.6f)
        }
        SaveManager.save(game.context, state)
    }

    private fun rest() {
        // 차 한 잔은 배를 살짝 채울 뿐 — 무한 식량 창고가 되지 않게 쿨다운
        if (game.time - lastRestT < 30f) {
            game.toast("조금 전에 쉬었어요~ 창밖 풍경을 눈에 담아 보세요")
            return
        }
        lastRestT = game.time
        state.hunger = (state.hunger + 8f).coerceAtMost(100f)
        state.luck = (state.luck + 2f).coerceAtMost(100f)
        game.toast("따뜻한 차 한 잔 · 배부름 조금 회복 · 행운 +2")
        game.sfx(Audio.Sfx.SPARKLE, 0.5f)
    }

    override fun handleInput(input: Input) {
        if (state.questTravelPlan != null && (input.justA || input.justEat)) {
            QuestNavigation.cancel(game, "직접 조작으로 길안내를 취소했어요")
            questPathKey = ""
        }
        if (input.justBack || input.justMenu) {
            openOverlay(MenuOverlay(this))
            return
        }
        if (input.justCam) {
            game.toast("실내에선 쉬어도 돼요. 새는 밖에서!")
            return
        }
        if (input.justQuest) {
            QuestNavigation.openTracker(this)
            return
        }
        if (input.justB) {
            game.toast("실내에서 자전거는 위험해요!")
            return
        }
        if (input.justEat) {
            val pid = state.eatBest()
            if (pid == null) {
                game.toast("피자가 없어요!")
                game.sfx(Audio.Sfx.FAIL, 0.45f)
            } else {
                val p = Pizzas.of(pid)
                game.toast("냠냠! ${p.fullName}")
                game.sfx(Audio.Sfx.EAT, 0.9f)
            }
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
        drawThemeTint(c)
        drawVista(c)
        drawProps(c)
        drawDocent(c)
        drawPlayer(c)

        val a = game.assets
        c.drawBitmap(a.vignette, null, RectF(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat()), a.sprPaint)

        // 상호작용 힌트 (! 말풍선)
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

    /** 바닥·벽에 테마 색을 반투명으로 얹어 분위기를 낸다. */
    private fun drawThemeTint(c: Canvas) {
        val p = uiFill
        for (y in 0 until map.h) {
            for (x in 0 until map.w) {
                val tile = map.t(x, y)
                val sx = (x * 16f - camX) * WORLD_SCALE
                val sy = (y * 16f - camY) * WORLD_SCALE
                when (tile) {
                    T.FLOOR -> {
                        p.color = Color.argb(96, Color.red(theme.floorTint), Color.green(theme.floorTint), Color.blue(theme.floorTint))
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, p)
                    }
                    T.WALL_IN -> {
                        p.color = Color.argb(130, Color.red(theme.wallTint), Color.green(theme.wallTint), Color.blue(theme.wallTint))
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, p)
                    }
                    else -> Unit
                }
            }
        }
        // 바닥 러너(붉은/포인트 융단) — 문에서 전시대까지
        p.color = Color.argb(70, Color.red(theme.accentTint), Color.green(theme.accentTint), Color.blue(theme.accentTint))
        val rx = (7f * 16f - camX) * WORLD_SCALE
        val ry0 = (2f * 16f - camY) * WORLD_SCALE
        val ry1 = (11f * 16f - camY) * WORLD_SCALE
        c.drawRect(rx, ry0, rx + 64f, ry1, p)
    }

    /** 창밖 전망 — 창유리 영역에 테마 하늘/바다/숲 그라데이션을 얹는다. */
    private fun drawVista(c: Canvas) {
        val windows = listOf(3, 6, 9, 12)
        for (wx in windows) {
            val sx = (wx * 16f - camX) * WORLD_SCALE
            val sy = (1f * 16f - camY) * WORLD_SCALE
            // 창유리 안쪽 영역 (타일 32px 중 안쪽)
            val gl = sx + 8f
            val gt = sy + 10f
            val gr = sx + 24f
            val gb = sy + 30f
            val steps = 6
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                aa.color = Color.argb(150, lerpCh(theme.vistaTop, theme.vistaBottom, t, 0),
                    lerpCh(theme.vistaTop, theme.vistaBottom, t, 1),
                    lerpCh(theme.vistaTop, theme.vistaBottom, t, 2))
                val yy0 = gt + (gb - gt) * (i / steps.toFloat())
                val yy1 = gt + (gb - gt) * ((i + 1) / steps.toFloat())
                c.drawRect(gl, yy0, gr, yy1, aa)
            }
            // 멀리 나는 새 실루엣 한둘
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

    /** 전시대와 휴게 소품 (코드 픽셀 도형) */
    private fun drawProps(c: Canvas) {
        // 전시대(포디움) + 안내판
        run {
            val bx = (exhibitX - camX) * WORLD_SCALE
            val by = (exhibitY - camY) * WORLD_SCALE
            // 접지 그림자
            aa.color = Color.argb(70, 20, 24, 20)
            c.drawOval(RectF(bx - 26f, by + 16f, bx + 26f, by + 28f), aa)
            // 받침
            aa.color = 0xFF5A4632.toInt()
            c.drawRect(bx - 22f, by + 6f, bx + 22f, by + 22f, aa)
            aa.color = 0xFF6E5740.toInt()
            c.drawRect(bx - 22f, by + 6f, bx + 22f, by + 10f, aa)
            // 안내판(액자)
            aa.color = darker(theme.accentTint, 0.55f)
            c.drawRect(bx - 26f, by - 30f, bx + 26f, by + 8f, aa)
            aa.color = theme.accentTint
            c.drawRect(bx - 22f, by - 26f, bx + 22f, by + 4f, aa)
            // 사진(테마 하늘)
            aa.color = theme.vistaBottom
            c.drawRect(bx - 18f, by - 22f, bx + 18f, by - 2f, aa)
            aa.color = theme.vistaTop
            c.drawRect(bx - 18f, by - 22f, bx + 18f, by - 12f, aa)
            // 대표 아이콘 (전시 사진 위)
            UiKit.iconCenter(c, game, "photo", bx, by - 12f, 20f)
        }
        // 휴게 소품 (등받이 없는 쿠션 벤치 + 화분)
        run {
            val bx = (restX - camX) * WORLD_SCALE
            val by = (restY - camY) * WORLD_SCALE
            aa.color = Color.argb(70, 20, 24, 20)
            c.drawOval(RectF(bx - 20f, by + 14f, bx + 20f, by + 24f), aa)
            aa.color = 0xFF6B4A2E.toInt()
            c.drawRect(bx - 18f, by + 2f, bx + 18f, by + 18f, aa)
            aa.color = darker(theme.accentTint, 0.8f)
            c.drawRect(bx - 18f, by - 6f, bx + 18f, by + 4f, aa)  // 쿠션
            aa.color = theme.accentTint
            c.drawRect(bx - 18f, by - 6f, bx + 18f, by - 2f, aa)
            // 화분
            aa.color = 0xFF9A6A3E.toInt()
            c.drawRect(bx + 20f, by + 2f, bx + 30f, by + 16f, aa)
            aa.color = 0xFF4E7A3A.toInt()
            c.drawCircle(bx + 25f, by - 2f, 7f, aa)
            aa.color = 0xFF6FA24C.toInt()
            c.drawCircle(bx + 22f, by - 5f, 4f, aa)
        }
    }

    private fun darker(col: Int, f: Float): Int = Color.argb(
        255,
        (Color.red(col) * f).toInt().coerceIn(0, 255),
        (Color.green(col) * f).toInt().coerceIn(0, 255),
        (Color.blue(col) * f).toInt().coerceIn(0, 255)
    )

    private fun drawDocent(c: Canvas) {
        val a = game.assets
        val sx = (docentX - 8f - camX) * WORLD_SCALE
        val sy = (docentY - 13f - camY) * WORLD_SCALE
        c.drawOval(RectF(sx + 8f, sy + 26f, sx + 24f, sy + 32f), a.shadowPaint)
        val bmp = a.docentBitmap(theme, game.time, game.hdSprites)
        a.drawPlayer(c, bmp, sx, sy, game.worldScale.toFloat())
        // 머리 위 💬 마커
        val bx = sx + 16f
        val by = sy - 12f
        aa.color = 0xFF8FC7F0.toInt()
        c.drawCircle(bx, by, 8f, aa)
        aa.color = 0xFF2E5E6E.toInt()
        aa.style = Paint.Style.STROKE; aa.strokeWidth = 1.6f
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
        val bmp = ps.clip(player.anim).frame(player.facing, player.frame)
        a.drawPlayer(c, bmp, sx, sy, game.worldScale.toFloat())
        Charms.equipped(state)?.let { item ->
            Charms.draw(c, item, sx + if (player.facing == Dir.W) 8f else 24f,
                sy + if (item.id == "rain") 12f else 22f, 8f, game.time)
        }
        val camDir = when (player.facing) {
            Dir.E -> 2
            Dir.W -> 3
            Dir.N -> 1
            else -> 0
        }
        a.drawPlayer(c, a.camHeld(state.rig().look, camDir, false, hd), sx, sy, game.worldScale.toFloat())
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
