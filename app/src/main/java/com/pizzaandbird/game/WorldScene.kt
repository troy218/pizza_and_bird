package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 지역 월드 씬: 걷기/자전거, 터널 이동, 새 스폰/촬영, NPC.
 */
class WorldScene(
    game: Game,
    regionId: String,
    private val spawnKind: SpawnKind = SpawnKind.SAVED,
    private val spawnDir: Dir = Dir.S
) : Scene(game) {

    private val state = game.state
    val region: RegionDef = Regions.byId[regionId] ?: Regions.ALL.first()
    val map: GameMap = MapBuilder.build(region, state.homeRegion)
    private val player = Player()
    private val birds = ArrayList<FieldBird>()
    private val rnd = Random(region.id.hashCode().toLong() + 7L)

    var photoMode = false
        private set

    private var camX = 0f
    private var camY = 0f
    private var spawnTimer = 1.5f
    private var hungerAcc = 0f
    private var luckAcc = 0f
    private var hungerWarnT = 0f
    private var saveT = 20f

    private val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        color = 0xFF4A3728.toInt()
        textSize = 9f
    }
    private val bubbleFill = Paint()
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF6B4F35.toInt()
        strokeWidth = 1.2f
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(200, 255, 250, 235)
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }

    init {
        state.region = region.id
        state.inHome = false

        val (sx, sy) = when (spawnKind) {
            SpawnKind.SAVED -> state.px to state.py
            SpawnKind.HOME -> 376f to 12.2f * 16f
            SpawnKind.TUNNEL -> when (spawnDir) {
                Dir.N -> 312f to 3f * 16f
                Dir.S -> 312f to (map.h - 4f) * 16f
                Dir.W -> 3f * 16f to 15.5f * 16f
                Dir.E -> (map.w - 4f) * 16f to 15.5f * 16f
            }
        }
        player.set(sx, sy)
        player.facing = when (spawnKind) {
            SpawnKind.TUNNEL -> Regions.opposite(spawnDir)
            else -> Dir.S
        }
        player.bike = spawnKind == SpawnKind.SAVED && state.onBike

        if (region.id !in state.visited) {
            state.visited.add(region.id)
            game.hud.toast("첫 방문! ${region.name} 🎉")
        }

        repeat(2) { trySpawnBird() }

        game.hud.showControls = true
        game.hud.showStats = true
        game.hud.showMinimap = true
        game.hud.regionLabel = region.name
        game.hud.photoModeHint = false
    }

    // -------------------------------------------------------------------
    // 업데이트
    // -------------------------------------------------------------------

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) return   // 대화상자/메뉴 중에는 세계 정지
        state.playSeconds += dt

        updatePlayer(dt)
        updateStats(dt)
        checkTileTriggers()

        // 새
        val birdDt = if (photoMode) dt * 0.35f else dt
        val it = birds.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.update(birdDt, player.cx, player.cy, player.bike, photoMode, map)
            if (b.gone) it.remove()
        }

        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            trySpawnBird()
            spawnTimer = 2.5f + rnd.nextFloat() * 3.5f
        }

        // 카메라
        val mapW = map.w * 16f
        val mapH = map.h * 16f
        camX = (player.cx - game.virtW / 2f).coerceIn(0f, (mapW - game.virtW).coerceAtLeast(0f))
        camY = (player.cy - game.virtH / 2f).coerceIn(0f, (mapH - game.virtH).coerceAtLeast(0f))

        // 상태 동기화 & 주기 저장
        state.px = player.x
        state.py = player.y
        state.onBike = player.bike
        game.hud.questLabel = state.questBird?.let { "의뢰: ${Birds.byId[it]?.name ?: "?"} 사진" }

        saveT -= dt
        if (saveT <= 0f) {
            saveT = 25f
            SaveManager.save(game.context, state)
        }
    }

    private fun updatePlayer(dt: Float) {
        val input = game.input
        var dx = input.dirX
        var dy = input.dirY
        if (photoMode) { dx *= 0.5f; dy *= 0.5f }
        val moving = abs(dx) > 0.01f || abs(dy) > 0.01f
        player.moving = moving
        if (moving) {
            if (abs(dx) > abs(dy)) player.facing = if (dx > 0) Dir.E else Dir.W
            else if (abs(dy) > 0.01f) player.facing = if (dy > 0) Dir.S else Dir.N

            var vx = dx
            var vy = dy
            val len = sqrt(vx * vx + vy * vy)
            if (len > 0.01f) { vx /= len; vy /= len }
            var speed = if (player.bike) 97f else 55f
            if (state.hunger <= 0f) speed *= 0.55f
            moveBy(vx * speed * dt, 0f)
            moveBy(0f, vy * speed * dt)
            player.animT += dt
        } else {
            player.animT = 0f
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val nx = player.x + dx
        val ny = player.y + dy
        if (!map.solidBox(nx, ny)) {
            player.x = nx
            player.y = ny
        }
    }

    private fun updateStats(dt: Float) {
        val moving = player.moving
        val hungerRate = when {
            player.bike && moving -> 0.22f
            moving -> 0.14f
            else -> 0.035f
        }
        hungerAcc += dt * hungerRate
        while (hungerAcc >= 1f) {
            hungerAcc -= 1f
            state.hunger = (state.hunger - 1f).coerceAtLeast(0f)
        }
        luckAcc += dt * 0.05f
        while (luckAcc >= 1f) {
            luckAcc -= 1f
            state.luck = (state.luck - 1f).coerceAtLeast(0f)
        }
        if (state.hunger <= 0f) {
            hungerWarnT -= dt
            if (hungerWarnT <= 0f) {
                hungerWarnT = 45f
                game.toast("배고파요… 집에서 피자를 구워 먹어요! 🍕")
            }
        }
    }

    /** 발 밟 타일 이벤트 (터널/현관) */
    private fun checkTileTriggers() {
        val ft = map.feetTile(player.x, player.y)
        when (ft) {
            T.TUNNEL -> {
                val edge = when {
                    (player.py().toInt()) <= 1 -> Dir.N
                    (player.py().toInt()) >= map.h - 2 -> Dir.S
                    (player.px().toInt()) <= 1 -> Dir.W
                    else -> Dir.E
                }
                goThroughTunnel(edge)
            }
            T.HOUSE_DOOR -> if (map.hasHouse) enterHome()
            else -> {}
        }
    }

    private fun Player.px(): Float = x / 16f
    private fun Player.py(): Float = y / 16f

    // -------------------------------------------------------------------
    // 지역/집 이동
    // -------------------------------------------------------------------

    private fun goThroughTunnel(edge: Dir) {
        val targetId = Regions.exits(map.region.id)[edge] ?: return
        val target = Regions.byId[targetId] ?: return
        val viaSea = targetId == "jeju" || map.region.id == "jeju"
        state.px = player.x
        state.py = player.y
        SaveManager.save(game.context, state)
        if (viaSea) game.toast("해저 터널을 지나~ 🚲💨")
        game.fadeTo {
            game.scene = WorldScene(game, targetId, SpawnKind.TUNNEL, Regions.opposite(edge))
        }
    }

    private fun enterHome() {
        state.px = player.x
        state.py = player.y
        SaveManager.save(game.context, state)
        game.fadeTo {
            game.scene = HomeScene(game)
        }
    }

    // -------------------------------------------------------------------
    // 새 스폰/촬영
    // -------------------------------------------------------------------

    private fun luckBoost(def: BirdDef): Double =
        1.0 + (state.luck / 100.0) * (def.tier.star - 1) * 1.4

    private fun regionPool(): List<BirdDef> = Birds.poolFor(map.region)

    private fun trySpawnBird() {
        if (birds.size >= 3) return
        val pool = regionPool()
        if (pool.isEmpty()) return

        val weights = pool.map { it.weight * luckBoost(it) }
        var roll = rnd.nextDouble() * weights.sum()
        var def = pool[pool.size - 1]
        for (i in pool.indices) {
            roll -= weights[i]
            if (roll <= 0) { def = pool[i]; break }
        }

        for (i in 0 until 30) {
            val tx = 2 + rnd.nextInt(map.w - 4)
            val ty = 2 + rnd.nextInt(map.h - 4)
            if (!map.walkableTile(tx, ty)) continue
            val bx = tx * 16f + 1f
            val by = ty * 16f + 3f
            val dPlayer = hypot(bx - player.cx, by - player.cy)
            if (dPlayer < 7f * 16f || dPlayer > 20f * 16f) continue
            var tooClose = false
            for (b in birds) {
                if (hypot(b.x - bx, b.y - by) < 40f) { tooClose = true; break }
            }
            if (tooClose) continue
            birds.add(FieldBird(def, bx, by))
            return
        }
    }

    private fun snap(b: FieldBird) {
        val a = game.assets
        val bmp = a.bird(b.def.id)
        val distPx = hypot(b.cx - player.cx, b.cy - player.cy)
        val range = CameraDefs.range(state.cameraLevel)
        val ratio = (distPx / 16f) / range
        var stars = when {
            ratio < 0.34f -> 3
            ratio < 0.67f -> 2
            else -> 1
        }
        val cam = CameraDefs.LEVELS[(state.cameraLevel - 1).coerceIn(0, CameraDefs.LEVELS.size - 1)]
        if (stars < 3 && rnd.nextDouble() < 0.13 * cam.qualityBonus) stars++
        if (stars < 3 && rnd.nextDouble() < state.luck / 520.0) stars++

        val prev = state.birdCounts[b.def.id] ?: 0
        val isNew = prev == 0
        state.birdCounts[b.def.id] = prev + 1
        if (isNew) state.luck = (state.luck + 4f).coerceAtMost(100f)

        var questLine: String? = null
        if (state.questBird == b.def.id) {
            val bonus = if (stars >= 3) (state.questReward * 0.3f).toInt() else 0
            val total = state.questReward + bonus
            state.money += total
            questLine = "의뢰 완료! +₩${fmtMoney(total)}" + if (bonus > 0) " (3성 보너스)" else ""
            state.questBird = null
            state.questReward = 0
        }

        b.state = 2
        b.fleeVx = 60f
        b.fleeVy = -75f
        b.fleeT = 0f

        SaveManager.save(game.context, state)
        openOverlay(PhotoResultOverlay(this, b.def, stars, isNew, prev + 1, questLine))
    }

    private fun trySnapAt(vx: Float, vy: Float) {
        var best: FieldBird? = null
        var bestD = Float.MAX_VALUE
        for (b in birds) {
            if (b.state == 2) continue
            val d = hypot(b.cx - vx, b.cy - vy)
            if (d < 14f && d < bestD) { best = b; bestD = d }
        }
        val target = best
        if (target == null) {
            game.toast("그곳엔 새가 없어요… 새 근처를 탭해 주세요")
            return
        }
        val range = CameraDefs.range(state.cameraLevel)
        val distPx = hypot(target.cx - player.cx, target.cy - player.cy)
        if (distPx > range * 16f) {
            game.toast("너무 멀어요! 조금 더 가까이 가볼까요?")
            return
        }
        snap(target)
    }

    // -------------------------------------------------------------------
    // NPC 상호작용
    // -------------------------------------------------------------------

    private fun nearestNpc(rangePx: Float = 32f): Npc? {
        var best: Npc? = null
        var bestD = Float.MAX_VALUE
        for (n in map.npcs) {
            val d = hypot(n.cx - player.cx, n.cy - player.cy)
            if (d < rangePx && d < bestD) { best = n; bestD = d }
        }
        return best
    }

    private fun talkTo(npc: Npc) {
        when (npc.kind) {
            NpcKind.PROFESSOR -> talkProfessor()
            NpcKind.SHOP -> talkShop()
            NpcKind.VILLAGER -> openOverlay(
                DialogOverlay(
                    this, npc.name, "\"${map.region.villager}\"",
                    listOf(DialogOverlay.Choice("안녕하세요!"))
                )
            )
        }
    }

    private fun talkProfessor() {
        val cur = state.questBird
        if (cur == null) {
            val pool = regionPool()
            if (pool.isEmpty()) return
            val unphoto = pool.filter { (state.birdCounts[it.id] ?: 0) == 0 && it.tier.star <= 2 }
            val candidates = if (unphoto.isNotEmpty() && rnd.nextDouble() < 0.55) unphoto else pool
            val def = candidates[rnd.nextInt(candidates.size)]
            openOverlay(
                DialogOverlay(
                    this, "보리 박사",
                    "\"반가워! 나는 조류학자 보리 박사네.\n이 지역에 ${def.name}가 나타났다는 소문이 있어.\n사진 한 장 부탁하네! 보수는 ₩${fmtMoney(def.reward)}.\"",
                    listOf(
                        DialogOverlay.Choice("맡겨주세요!") {
                            state.questBird = def.id
                            state.questReward = def.reward
                            SaveManager.save(game.context, state)
                            game.toast("의뢰 접수: ${def.name} 사진 📷")
                        },
                        DialogOverlay.Choice("다음에요…")
                    )
                )
            )
        } else {
            val def = Birds.byId[cur]
            openOverlay(
                DialogOverlay(
                    this, "보리 박사",
                    "\"아직 ${def?.name ?: "그 새"} 사진인가?\n카메라를 들고 조용히 다가가 보게.\n의뢰 포기는 아래 버튼으로 하게나.\"",
                    listOf(
                        DialogOverlay.Choice("열심히 찍어볼게요!"),
                        DialogOverlay.Choice("의뢰 포기하기") {
                            state.questBird = null
                            state.questReward = 0
                            game.toast("의뢰를 포기했어요…")
                        }
                    )
                )
            )
        }
    }

    private fun talkShop() {
        val lvl = state.cameraLevel
        if (lvl >= CameraDefs.LEVELS.size) {
            openOverlay(
                DialogOverlay(
                    this, "사진용품점",
                    "\"이미 최고의 장비를 갖췄구먼! 부럽다니까.\"",
                    listOf(DialogOverlay.Choice("그럼 이만!"))
                )
            )
            return
        }
        val next = CameraDefs.LEVELS[lvl]
        openOverlay(
            DialogOverlay(
                this, "사진용품점",
                "\"요즘 장비 어때? ${next.name}(으)로 바꾸면\n더 멀리서 새를 찍을 수 있을걸?\n가격은 ₩${fmtMoney(next.cost)}야.\"",
                listOf(
                    DialogOverlay.Choice("업그레이드하기 (₩${fmtMoney(next.cost)})") {
                        if (game.state.money >= next.cost) {
                            game.state.money -= next.cost
                            game.state.cameraLevel = lvl + 1
                            SaveManager.save(game.context, game.state)
                            game.toast("카메라가 ${next.name}(으)로 업그레이드됐어요! 📷✨")
                        } else {
                            game.toast("돈이 부족해요… 박사 의뢰를 해볼까요?")
                        }
                    },
                    DialogOverlay.Choice("그냥 볼게요")
                )
            )
        )
    }

    // -------------------------------------------------------------------
    // 입력
    // -------------------------------------------------------------------

    override fun handleInput(input: Input) {
        if (input.justBack) {
            if (photoMode) {
                photoMode = false
                game.hud.photoModeHint = false
            } else {
                openOverlay(MenuOverlay(this))
            }
            return
        }
        if (input.justMenu) {
            openOverlay(MenuOverlay(this))
            return
        }
        if (input.justCam) {
            photoMode = !photoMode
            game.hud.photoModeHint = photoMode
            if (photoMode) game.toast("카메라 모드! 새를 탭해서 찍어요 📷")
            return
        }
        if (input.justB) {
            player.bike = !player.bike
            state.onBike = player.bike
            game.toast(if (player.bike) "자전거 탔다! 쌩~ 🚲" else "자전거에서 내렸어요")
            return
        }
        if (input.justA) {
            val npc = nearestNpc()
            if (npc != null) {
                talkTo(npc)
                return
            }
            if (map.hasHouse) {
                val ddx = (map.houseDoorX * 16f + 16f) - player.cx
                val ddy = (map.houseDoorY * 16f + 8f) - player.cy
                if (hypot(ddx, ddy) < 30f) {
                    enterHome()
                    return
                }
            }
            game.toast("주민들에게 말을 걸어보세요! (가까이 가서 A)")
            return
        }
        val tap = input.consumeTapWorld()
        if (tap != null) {
            if (photoMode) {
                trySnapAt(tap.x, tap.y)
                return
            }
            // NPC 탭
            val npc = nearestNpc(46f)
            if (npc != null && hypot(npc.cx - tap.x, npc.cy - tap.y) < 18f) {
                talkTo(npc)
                return
            }
            // 새 탭 (힌트)
            for (b in birds) {
                if (b.state != 2 && hypot(b.cx - tap.x, b.cy - tap.y) < 14f) {
                    game.toast("카메라 버튼을 누르고 찍어보세요! 📷")
                    return
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFF3A3040.toInt())
        map.draw(c, game.assets, camX, camY, game.virtW, game.virtH, game.time)

        // 엔티티 (y 정렬)
        val ents = ArrayList<Any>(map.npcs.size + birds.size + 1)
        ents.addAll(map.npcs)
        ents.addAll(birds)
        ents.add(player)
        ents.sortBy { sortY(it) }
        for (e in ents) drawEntity(c, e)

        if (photoMode) drawPhotoOverlay(c)
    }

    private fun sortY(e: Any): Float = when (e) {
        is Npc -> e.y + 14f
        is FieldBird -> e.y + game.assets.bird(e.def.id).height
        is Player -> e.y + 14f
        else -> 0f
    }

    private fun drawEntity(c: Canvas, e: Any) {
        val a = game.assets
        when (e) {
            is Npc -> {
                val bmp = when (e.kind) {
                    NpcKind.PROFESSOR -> a.npcProfessor
                    NpcKind.SHOP -> a.npcShop
                    NpcKind.VILLAGER -> a.npcVillager
                }
                val bob = if ((sin(game.time * 2.4f + e.tileX).toInt() % 2) == 0) -1f else 0f
                c.drawBitmap(bmp, e.x - camX, e.y - camY + bob, a.sprPaint)
                // 의뢰 가능 표시
                if (e.kind == NpcKind.PROFESSOR && state.questBird == null) {
                    val bx = e.cx - camX
                    val by = e.y - camY - 8f + bob
                    bubbleFill.color = 0xFFF2D06B.toInt()
                    c.drawCircle(bx, by, 5.5f, bubbleFill)
                    c.drawCircle(bx, by, 5.5f, bubbleStroke)
                    tinyPaint.textSize = 9f
                    val tw = tinyPaint.measureText("!")
                    c.drawText("!", bx - tw / 2, by + 3f, tinyPaint)
                }
            }
            is FieldBird -> {
                val bmp = if (e.faceLeft) a.bird(e.def.id) else a.birdFlipped(e.def.id)
                val bx = e.x - camX
                val by = e.y - camY - e.hopLift
                c.drawOval(
                    RectF(e.x - camX + 1f, e.cy - camY + 3f, e.x - camX + 13f, e.cy - camY + 7f),
                    a.shadowPaint
                )
                if (e.state == 2) {
                    val alpha = (255 * (1f - (e.fleeT / 1.5f).coerceIn(0f, 1f))).toInt()
                    a.sprPaint.alpha = alpha
                    c.drawBitmap(bmp, bx, by, a.sprPaint)
                    a.sprPaint.alpha = 255
                } else {
                    c.drawBitmap(bmp, bx, by, a.sprPaint)
                }
            }
            is Player -> {
                val frame = if (player.moving) ((player.animT / 0.16f).toInt() % 2) else 0
                val bmp: android.graphics.Bitmap = when {
                    player.bike && player.facing == Dir.E -> a.bikeSide
                    player.bike && player.facing == Dir.W -> a.bikeSideL
                    player.bike && player.facing == Dir.N -> a.bikeUp
                    player.bike && player.facing == Dir.S -> a.bikeDown
                    player.facing == Dir.E -> a.playerSide[frame]
                    player.facing == Dir.W -> a.playerSideL[frame]
                    player.facing == Dir.N -> a.playerUp[frame]
                    else -> a.playerDown[frame]
                }
                c.drawOval(
                    RectF(player.x - camX + 3f, player.y - camY + 12f, player.x - camX + 13f, player.y - camY + 16f),
                    a.shadowPaint
                )
                c.drawBitmap(bmp, player.x - camX, player.y - camY, a.sprPaint)
            }
        }
    }

    private fun drawPhotoOverlay(c: Canvas) {
        val p = Paint()
        p.color = Color.argb(80, 20, 16, 28)
        c.drawRect(0f, 0f, 480f, 22f, p)
        c.drawRect(0f, 248f, 480f, 270f, p)
        c.drawRect(0f, 0f, 18f, 270f, p)
        c.drawRect(462f, 0f, 480f, 270f, p)

        // 뷰파인더 코너
        val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(220, 255, 250, 235)
        }
        val m = 34f
        val l = 14f
        val path = Path()
        path.moveTo(m, m + l); path.lineTo(m, m); path.lineTo(m + l, m)
        path.moveTo(480f - m - l, m); path.lineTo(480f - m, m); path.lineTo(480f - m, m + l)
        path.moveTo(480f - m, 270f - m - l); path.lineTo(480f - m, 270f - m); path.lineTo(480f - m - l, 270f - m)
        path.moveTo(m + l, 270f - m); path.lineTo(m, 270f - m); path.lineTo(m, 270f - m - l)
        c.drawPath(path, s)

        // 촬영 반경
        val range = CameraDefs.range(state.cameraLevel) * 16f
        c.drawCircle(player.cx - camX, player.cy - camY, range, dashPaint)
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
