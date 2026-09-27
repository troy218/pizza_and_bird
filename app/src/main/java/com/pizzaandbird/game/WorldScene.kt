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
 * 지역 월드 씬: 걷기/자전거/달리기, 터널 이동, 새 스폰/촬영, 낮밤, 파티클, NPC/고양이.
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
    private val cats = ArrayList<Cat>()
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
    private var dustT = 0f
    private var ambientT = 0f

    // 파티클
    private class Pt(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, var max: Float, var col: Int, var size: Float, var sway: Boolean
    )

    private val particles = ArrayList<Pt>()

    private val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        color = 0xFF4A3728.toInt()
        textSize = 13f
    }
    private val bubbleFill = Paint()
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF6B4F35.toInt()
        strokeWidth = 1.6f
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(200, 255, 250, 235)
        strokeWidth = 2.4f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    private val cloudPaint = Paint().apply { color = Color.argb(26, 18, 30, 56); isAntiAlias = true }
    private val uiFill = Paint()
    private val uiStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val uiText = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

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
        spawnCats()

        // 카메라 초기 스냅
        updateCamera(snap = true)

        game.hud.showControls = true
        game.hud.showStats = true
        game.hud.showMinimap = true
        game.hud.regionLabel = region.name
        game.hud.photoModeHint = false
        game.banner("${region.emoji}  ${region.name}")
    }

    // -------------------------------------------------------------------
    // 업데이트
    // -------------------------------------------------------------------

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) return   // 대화상자/메뉴 중에는 세계 정지
        state.playSeconds += dt
        state.worldTime = (state.worldTime + dt * 24f / DAY_SECONDS) % 24f

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

        // 고양이
        for (cat in cats) cat.update(dt, map)

        // 새 스폰
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            trySpawnBird()
            spawnTimer = (if (state.isNight()) 2f else 2.5f) + rnd.nextFloat() * 3.5f
        }

        // 파티클
        updateParticles(dt)
        spawnAmbient(dt)
        if (player.bike && player.moving) {
            dustT -= dt
            if (dustT <= 0f) {
                dustT = 0.16f
                addParticle(player.x + 8f, player.y + 14f, (rnd.nextFloat() - 0.5f) * 10f, -6f, 0.5f, Color.argb(120, 148, 128, 96), 3f, false)
            }
        }

        // 카메라
        updateCamera(snap = false, dt = dt)

        // 상태 동기화 & 주기 저장
        state.px = player.x
        state.py = player.y
        state.onBike = player.bike
        game.hud.questLabel = state.questBird?.let { "의뢰: ${Birds.byId[it]?.name ?: "?"} 사진" }

        // 메인 버튼 맥락 아이콘 (근처 상호작용 대상 — A 버튼 동작과 동일한 우선순위)
        game.hud.contextIcon = when {
            nearestNpc() != null -> "💬"
            nearTile(T.SIGN) != null -> "🪧"
            nearTile(T.BENCH) != null -> "☕"
            nearestCat() != null -> "🐈"
            map.hasHouse && hypot((map.houseDoorX * 16f + 16f) - player.cx, (map.houseDoorY * 16f + 8f) - player.cy) < 30f -> "🚪"
            else -> null
        }

        saveT -= dt
        if (saveT <= 0f) {
            saveT = 25f
            SaveManager.save(game.context, state)
        }
    }

    private fun updateCamera(snap: Boolean, dt: Float = 0f) {
        val halfW = game.virtW / (2f * WORLD_SCALE)
        val halfH = game.virtH / (2f * WORLD_SCALE)
        val mapW = map.w * 16f
        val mapH = map.h * 16f
        val tx = (player.cx - halfW).coerceIn(0f, (mapW - halfW * 2f).coerceAtLeast(0f))
        val ty = (player.cy - halfH).coerceIn(0f, (mapH - halfH * 2f).coerceAtLeast(0f))
        if (snap) {
            camX = tx; camY = ty
        } else {
            val k = (dt * 8f).coerceIn(0f, 1f)
            camX += (tx - camX) * k
            camY += (ty - camY) * k
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
            val sprint = input.isRun && !player.bike
            var speed = if (player.bike) 97f else 55f
            if (sprint) speed *= 1.45f
            if (state.hunger <= 0f) speed *= 0.55f
            moveBy(vx * speed * dt, 0f)
            moveBy(0f, vy * speed * dt)
            player.animT += dt * (if (sprint) 1.4f else 1f)
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
        val sprinting = game.input.isRun && !player.bike && moving
        val hungerRate = when {
            sprinting -> 0.24f
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
                    (player.y.toInt() / 16) <= 1 -> Dir.N
                    (player.y.toInt() / 16) >= map.h - 2 -> Dir.S
                    (player.x.toInt() / 16) <= 1 -> Dir.W
                    else -> Dir.E
                }
                goThroughTunnel(edge)
            }
            T.HOUSE_DOOR -> if (map.hasHouse) enterHome()
            else -> {}
        }
    }

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
        1.0 + (state.effectiveLuck() / 100.0) * (def.tier.star - 1) * 1.4

    private fun regionPool(): List<BirdDef> {
        val n = state.isNight()
        val pool = Birds.poolFor(map.region, n)
        return if (pool.isEmpty()) Birds.poolFor(map.region, !n) else pool
    }

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
            if (def.tier.star >= 3) game.toast("✨ 조심하세요… ${def.name}가 나타났어요!")
            if (state.questBird == def.id) game.toast("📋 의뢰의 새 ${def.name} 등장! 📷")
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
        if (stars < 3 && rnd.nextDouble() < state.effectiveLuck() / 520.0) stars++

        val prev = state.birdCounts[b.def.id] ?: 0
        val isNew = prev == 0
        state.birdCounts[b.def.id] = prev + 1
        val prevBest = state.bestStars[b.def.id] ?: 0
        if (stars > prevBest) state.bestStars[b.def.id] = stars
        state.photos += 1
        if (isNew) state.luck = (state.luck + 4f).coerceAtMost(100f)

        var questLine: String? = null
        if (state.questBird == b.def.id) {
            val bonus = if (stars >= 3) (state.questReward * 0.3f).toInt() else 0
            val total = state.questReward + bonus
            state.money += total
            questLine = "의뢰 완료! +${won(total)}" + if (bonus > 0) " (3성 보너스)" else ""
            state.questBird = null
            state.questReward = 0
        }

        // 깃털 파티클
        for (i in 0 until 4) {
            addParticle(
                b.cx, b.cy,
                (rnd.nextFloat() - 0.5f) * 24f, -18f - rnd.nextFloat() * 14f,
                0.7f, Color.argb(210, 250, 248, 240), 3f, true
            )
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
    // 파티클
    // -------------------------------------------------------------------

    private fun addParticle(x: Float, y: Float, vx: Float, vy: Float, life: Float, col: Int, size: Float, sway: Boolean) {
        if (particles.size > 60) return
        particles.add(Pt(x, y, vx, vy, life, life, col, size, sway))
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.sway) p.x += sin((p.max - p.life) * 3f) * 4f * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }

    private fun ambientKind(): String = when {
        region.id == "sokcho" || region.id == "jeju" -> "snow"
        "coast" in region.habitats -> "sparkle"
        "wetland" in region.habitats -> if (state.isNight()) "firefly" else "petal"
        "forest" in region.habitats -> "leaf"
        else -> "petal"
    }

    private fun spawnAmbient(dt: Float) {
        ambientT -= dt
        if (ambientT > 0f) return
        ambientT = 0.28f
        val ambientCount = particles.count { it.max > 1.5f }
        if (ambientCount >= 22) return
        val halfW = game.virtW / (2f * WORLD_SCALE)
        val halfH = game.virtH / (2f * WORLD_SCALE)
        when (ambientKind()) {
            "leaf" -> addParticle(
                camX + rnd.nextFloat() * halfW * 2f, camY - 8f,
                (rnd.nextFloat() - 0.5f) * 6f, 10f + rnd.nextFloat() * 6f, 4f,
                if (rnd.nextBoolean()) Color.argb(170, 111, 174, 87) else Color.argb(170, 200, 140, 70), 3f, true
            )
            "petal" -> addParticle(
                camX + rnd.nextFloat() * halfW * 2f, camY - 8f,
                8f + rnd.nextFloat() * 8f, 6f + rnd.nextFloat() * 5f, 4.5f,
                Color.argb(150, 242, 163, 179), 3f, true
            )
            "snow" -> addParticle(
                camX + rnd.nextFloat() * halfW * 2f, camY - 8f,
                (rnd.nextFloat() - 0.5f) * 6f, 8f + rnd.nextFloat() * 5f, 5f,
                Color.argb(190, 240, 246, 252), 2.6f, true
            )
            "sparkle" -> addParticle(
                camX + rnd.nextFloat() * halfW * 2f, camY + rnd.nextFloat() * halfH * 2f,
                0f, -3f, 1.8f, Color.argb(160, 250, 250, 255), 2.2f, false
            )
            "firefly" -> addParticle(
                camX + rnd.nextFloat() * halfW * 2f, camY + rnd.nextFloat() * halfH * 2f,
                (rnd.nextFloat() - 0.5f) * 8f, (rnd.nextFloat() - 0.5f) * 6f, 3f,
                Color.argb(220, 247, 222, 96), 2.6f, true
            )
        }
    }

    // -------------------------------------------------------------------
    // 고양이
    // -------------------------------------------------------------------

    private fun spawnCats() {
        val n = 1 + (rnd.nextInt(2))
        repeat(n) {
            for (i in 0 until 24) {
                val tx = 2 + rnd.nextInt(map.w - 4)
                val ty = 2 + rnd.nextInt(map.h - 4)
                if (!map.walkableTile(tx, ty)) continue
                val x0 = tx * 16f
                val y0 = ty * 16f
                if (hypot(x0 - player.cx, y0 - player.cy) < 56f) continue
                cats.add(Cat(x0, y0))
                break
            }
        }
    }

    private fun nearestCat(rangePx: Float = 28f): Cat? {
        var best: Cat? = null
        var bestD = Float.MAX_VALUE
        for (cat in cats) {
            val d = hypot(cat.cx - player.cx, cat.cy - player.cy)
            if (d < rangePx && d < bestD) { best = cat; bestD = d }
        }
        return best
    }

    // -------------------------------------------------------------------
    // 이정표 / 벤치
    // -------------------------------------------------------------------

    /** 플레이어 주변 3x3 타일에서 특정 타일 찾기 */
    private fun nearTile(type: T): Pair<Int, Int>? {
        val ptx = (player.cx / 16f).toInt()
        val pty = ((player.y + 13f) / 16f).toInt()
        for (dy in -1..1) for (dx in -1..1) {
            val x = ptx + dx
            val y = pty + dy
            if (map.t(x, y) == type) return x to y
        }
        return null
    }

    private fun signTarget(sx: Int, sy: Int): RegionDef? {
        val dir = when {
            sy <= 2 -> Dir.N
            sy >= map.h - 5 -> Dir.S
            sx <= 3 -> Dir.W
            else -> Dir.E
        }
        val targetId = Regions.exits(map.region.id)[dir] ?: return null
        return Regions.byId[targetId]
    }

    private fun tapSign(vx: Float, vy: Float) {
        val tx = (vx / 16f).toInt()
        val ty = (vy / 16f).toInt()
        if (map.t(tx, ty) == T.SIGN) {
            val target = signTarget(tx, ty)
            if (target != null) game.toast("🪧 이 터널 → ${target.name}")
        }
    }

    private fun restAtBench() {
        state.luck = (state.luck + 2f).coerceAtMost(100f)
        game.toast("벤치에 앉아 쉬었다~ 구름 구경 ☘️+2")
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
            NpcKind.KID -> {
                val lines = listOf(
                    "우와, 카메라 멋져요! 저도 크면 탐조할 거예요!",
                    "저기요, 저 새 이름 알아요? 어… 까먹었어요.",
                    "자전거 타면 빨리 가지만 금방 배고파져요!",
                    "박사님이 뭔가 찾고 있었어요. 가보실래요?"
                )
                openOverlay(
                    DialogOverlay(
                        this, npc.name, "\"${lines[rnd.nextInt(lines.size)]}\"",
                        listOf(DialogOverlay.Choice("ㅎㅎ 귀엽다"))
                    )
                )
            }
            NpcKind.ELDER -> {
                val lines = listOf(
                    "요즘 젊은이들은 참 부지런해요.",
                    "옛날엔 이 동네에 두루미가 많이 왔었지…",
                    "피자도 잘 먹고 다니게. 몸이 자본이야.",
                    "해 지기 전에 들어가게. 밤엔 부엉이가 나온다네."
                )
                openOverlay(
                    DialogOverlay(
                        this, npc.name, "\"${lines[rnd.nextInt(lines.size)]}\"",
                        listOf(DialogOverlay.Choice("다녀오겠습니다"))
                    )
                )
            }
        }
    }

    private fun talkProfessor() {
        val cur = state.questBird
        if (cur == null) {
            val pool = Birds.poolFor(map.region, false)   // 의뢰는 언제든 찍을 수 있는 낮새 위주
            if (pool.isEmpty()) return
            val unphoto = pool.filter { (state.birdCounts[it.id] ?: 0) == 0 && it.tier.star <= 2 }
            val candidates = if (unphoto.isNotEmpty() && rnd.nextDouble() < 0.55) unphoto else pool
            val def = candidates[rnd.nextInt(candidates.size)]
            openOverlay(
                DialogOverlay(
                    this, "보리 박사",
                    "\"반가워! 나는 조류학자 보리 박사네.\n이 지역에 ${def.name}가 나타났다는 소문이 있어.\n사진 한 장 부탁하네! 보수는 ${won(def.reward)}.\"",
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
        openOverlay(
            DialogOverlay(
                this, "사진용품점",
                if (lvl >= CameraDefs.LEVELS.size)
                    "\"이미 최고의 장비를 갖췄구먼! 부럽다니까.\""
                else {
                    val next = CameraDefs.LEVELS[lvl]
                    "\"요즘 장비 어때? ${next.name}(으)로 바꾸면\n더 멀리서 새를 찍을 수 있을걸?\n가격은 ${won(next.cost)}야.\""
                },
                buildList {
                    if (lvl < CameraDefs.LEVELS.size) {
                        val next = CameraDefs.LEVELS[lvl]
                        add(
                            DialogOverlay.Choice("업그레이드 (${won(next.cost)})") {
                                if (game.state.money >= next.cost) {
                                    game.state.money -= next.cost
                                    game.state.cameraLevel = lvl + 1
                                    SaveManager.save(game.context, game.state)
                                    game.toast("카메라가 ${next.name}(으)로 업그레이드됐어요! 📷✨")
                                } else {
                                    game.toast("돈이 부족해요… 박사 의뢰를 해볼까요?")
                                }
                            }
                        )
                    }
                    add(DialogOverlay.Choice("장식 코너 보기") {
                        it.finished = true
                        it.scene.openOverlay(DecorShopOverlay(it.scene))
                    })
                    add(DialogOverlay.Choice("그냥 볼게요"))
                }
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
        if (input.justMap) {
            openOverlay(MapOverlay(this))
            return
        }
        if (input.justEat) {
            quickEat()
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
            nearTile(T.SIGN)?.let { (sx, sy) ->
                val target = signTarget(sx, sy)
                if (target != null) {
                    game.toast("🪧 이 터널 → ${target.name}")
                    return
                }
            }
            if (nearTile(T.BENCH) != null) {
                restAtBench()
                return
            }
            nearestCat()?.let { cat ->
                state.luck = (state.luck + 1f).coerceAtMost(100f)
                for (i in 0 until 3) {
                    addParticle(
                        cat.cx, cat.cy - 6f,
                        (rnd.nextFloat() - 0.5f) * 10f, -12f, 1f,
                        Color.argb(220, 242, 130, 160), 3.4f, true
                    )
                }
                game.toast("야옹~ 🐈 좋은 기운이 든다 (행운+1)")
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
            game.toast("주민·이정표·벤치·고양이에게 다가가 육각 메인 버튼을 눌러보세요!")
            return
        }
        val tap = input.consumeTapWorld()
        if (tap != null) {
            if (photoMode) {
                trySnapAt(tap.x, tap.y)
                return
            }
            // 이정표 탭
            tapSign(tap.x, tap.y)
            // NPC 탭
            val npc = nearestNpc(46f)
            if (npc != null && hypot(npc.cx - tap.x, npc.cy - tap.y) < 18f) {
                talkTo(npc)
                return
            }
            // 고양이 탭
            for (cat in cats) {
                if (hypot(cat.cx - tap.x, cat.cy - tap.y) < 14f) {
                    game.toast("야옹~ 🐈")
                    return
                }
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

    private fun quickEat() {
        val tId = state.eatBest()
        if (tId == null) {
            game.toast("피자가 없어요! 집의 화덕에서 구워요 🍕")
        } else {
            val t = Toppings.of(tId)
            game.toast("냠냠! ${t.emoji} ${t.name} 피자")
        }
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFF3A3040.toInt())
        val camXv = camX * WORLD_SCALE
        val camYv = camY * WORLD_SCALE
        map.draw(c, game.assets, camXv, camYv, game.virtW, game.virtH, game.time)
        drawCloudShadows(c, camXv, camYv)

        // 엔티티 (y 정렬)
        val ents = ArrayList<Any>(map.npcs.size + birds.size + cats.size + 1)
        ents.addAll(map.npcs)
        ents.addAll(cats)
        ents.addAll(birds)
        ents.add(player)
        ents.sortBy { sortY(it) }
        for (e in ents) drawEntity(c, e)

        drawParticles(c, camXv, camYv)
        drawDayNight(c)
        drawNightGlow(c, camXv, camYv)
        if (photoMode) drawPhotoOverlay(c)
    }

    private fun sortY(e: Any): Float = when (e) {
        is Npc -> e.y + 14f
        is Cat -> e.y + 12f
        is FieldBird -> e.y + game.assets.bird(e.def.id).height / WORLD_SCALE
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
                    NpcKind.KID -> a.npcKid
                    NpcKind.ELDER -> a.npcElder
                }
                val bob = if ((sin(game.time * 2.4f + e.tileX).toInt() % 2) == 0) -1.5f else 0f
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE + bob
                c.drawOval(
                    RectF(sx + 8f, sy + 26f, sx + 24f, sy + 32f),
                    a.shadowPaint
                )
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
                // 의뢰 가능 표시
                if (e.kind == NpcKind.PROFESSOR && state.questBird == null) {
                    val bx = sx + 16f
                    val by = sy - 12f
                    bubbleFill.color = 0xFFF2D06B.toInt()
                    c.drawCircle(bx, by, 9f, bubbleFill)
                    c.drawCircle(bx, by, 9f, bubbleStroke)
                    tinyPaint.textSize = 14f
                    val tw = tinyPaint.measureText("!")
                    c.drawText("!", bx - tw / 2, by + 5f, tinyPaint)
                }
            }
            is Cat -> {
                val bmp = if (e.faceLeft) a.catFrames[e.frame] else a.catFramesL[e.frame]
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE - e.lift * WORLD_SCALE
                c.drawOval(RectF(sx + 8f, (e.cy - camY) * WORLD_SCALE + 6f, sx + 24f, (e.cy - camY) * WORLD_SCALE + 12f), a.shadowPaint)
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
            }
            is FieldBird -> {
                val flying = e.state == 2
                val bmp = if (flying) {
                    val wingFrame = ((e.fleeT * 11f).toInt() and 1)
                    a.birdFlight(e.def.id, wingFrame, e.faceLeft)
                } else if (e.faceLeft) {
                    a.bird(e.def.id)
                } else {
                    a.birdFlipped(e.def.id)
                }
                val bx = (e.x - camX) * WORLD_SCALE
                val by = (e.y - camY) * WORLD_SCALE - e.hopLift * WORLD_SCALE
                // 날아오르면 땅의 그림자가 빠르게 작아져 입체감이 생긴다.
                if (!flying || e.fleeT < 0.32f) {
                    val shadowK = if (flying) (1f - e.fleeT / 0.32f).coerceIn(0.2f, 1f) else 1f
                    val shadowCx = bx + bmp.width * 0.5f
                    val shadowHalf = bmp.width * 0.4f * shadowK
                    c.drawOval(
                        RectF(
                            shadowCx - shadowHalf, (e.cy - camY) * WORLD_SCALE + 7f,
                            shadowCx + shadowHalf, (e.cy - camY) * WORLD_SCALE + 12f
                        ),
                        a.shadowPaint
                    )
                }
                if (flying) {
                    val alpha = (255 * (1f - (e.fleeT / 1.5f).coerceIn(0f, 1f))).toInt()
                    a.sprPaint.alpha = alpha
                    c.drawBitmap(bmp, bx, by, a.sprPaint)
                    a.sprPaint.alpha = 255
                } else {
                    c.drawBitmap(bmp, bx, by, a.sprPaint)
                }
            }
            is Player -> {
                val frame = if (player.moving) ((player.animT / 0.14f).toInt() % 3) else 0
                val bmp: android.graphics.Bitmap = when {
                    player.bike && player.facing == Dir.E -> a.bikeSide
                    player.bike && player.facing == Dir.W -> a.bikeSideL
                    player.bike && player.facing == Dir.N -> a.bikeUp
                    player.bike && player.facing == Dir.S -> a.bikeDown
                    player.facing == Dir.E -> if (state.gender == "female") a.femaleSide[frame] else a.playerSide[frame]
                    player.facing == Dir.W -> if (state.gender == "female") a.femaleSideL[frame] else a.playerSideL[frame]
                    player.facing == Dir.N -> if (state.gender == "female") a.femaleUp[frame] else a.playerUp[frame]
                    else -> if (state.gender == "female") a.femaleDown[frame] else a.playerDown[frame]
                }
                val sx = (player.x - camX) * WORLD_SCALE
                val sy = (player.y - camY) * WORLD_SCALE
                c.drawOval(RectF(sx + 6f, sy + 24f, sx + 26f, sy + 32f), a.shadowPaint)
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
            }
        }
    }

    private fun drawCloudShadows(c: Canvas, camXv: Float, camYv: Float) {
        for (i in 0 until 3) {
            val speed = 7f + i * 3.5f
            val w = 250f + i * 70f
            val span = map.w * 32f + 800f
            val cxw = ((game.time * speed + i * 430f) % span) - 400f
            val cyw = 110f + i * 200f + sin(game.time * 0.13f + i * 2f) * 50f
            c.drawOval(RectF(cxw - camXv - w / 2f, cyw - camYv - 60f, cxw - camXv + w / 2f, cyw - camYv + 60f), cloudPaint)
        }
    }

    private fun drawParticles(c: Canvas, camXv: Float, camYv: Float) {
        for (p in particles) {
            val k = (p.life / p.max).coerceIn(0f, 1f)
            uiFill.color = Color.argb(
                (Color.alpha(p.col) * k).toInt().coerceIn(0, 255),
                Color.red(p.col), Color.green(p.col), Color.blue(p.col)
            )
            val sx = p.x * WORLD_SCALE - camXv
            val sy = p.y * WORLD_SCALE - camYv
            c.drawRect(sx, sy, sx + p.size, sy + p.size, uiFill)
        }
    }

    // -------------------------------------------------------------------
    // 낮/밤
    // -------------------------------------------------------------------

    private fun lerpC(c0: Int, c1: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(c0) + (Color.alpha(c1) - Color.alpha(c0)) * tt).toInt(),
            (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * tt).toInt(),
            (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * tt).toInt(),
            (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * tt).toInt()
        )
    }

    private fun ambientColor(): Int {
        val h = state.worldTime
        val night = Color.argb(96, 24, 28, 66)
        val dawn = Color.argb(64, 255, 166, 92)
        val dusk = Color.argb(80, 240, 120, 60)
        val day = Color.argb(0, 0, 0, 0)
        return when {
            h < 4f -> night
            h < 6f -> lerpC(night, dawn, (h - 4f) / 2f)
            h < 7.5f -> lerpC(dawn, day, (h - 6f) / 1.5f)
            h < 17f -> day
            h < 18.5f -> lerpC(day, dusk, (h - 17f) / 1.5f)
            h < 20f -> lerpC(dusk, night, (h - 18.5f) / 1.5f)
            else -> night
        }
    }

    private fun drawDayNight(c: Canvas) {
        val col = ambientColor()
        if (Color.alpha(col) == 0) return
        uiFill.color = col
        c.drawRect(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat(), uiFill)
    }

    /** 밤 — 가로등/창문 은은한 빛 */
    private fun drawNightGlow(c: Canvas, camXv: Float, camYv: Float) {
        val twilight = state.worldTime >= 17.5f || state.worldTime < 5.5f
        if (!twilight) return
        val x0 = (camXv / 32f).toInt().coerceAtLeast(0)
        val y0 = (camYv / 32f).toInt().coerceAtLeast(0)
        val x1 = ((camXv + game.virtW) / 32f).toInt().coerceAtMost(map.w - 1)
        val y1 = ((camYv + game.virtH) / 32f).toInt().coerceAtMost(map.h - 1)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val tile = map.t(x, y)
                val sx = x * 32f - camXv
                val sy = y * 32f - camYv
                if (tile == T.LAMP) {
                    uiFill.color = Color.argb(46, 255, 214, 120)
                    c.drawCircle(sx + 16f, sy + 8f, 15f, uiFill)
                    uiFill.color = Color.argb(30, 255, 214, 120)
                    c.drawCircle(sx + 16f, sy + 10f, 26f, uiFill)
                    uiFill.color = Color.argb(16, 255, 214, 120)
                    c.drawCircle(sx + 16f, sy + 12f, 38f, uiFill)
                } else if (tile == T.HOUSE_WIN || tile == T.BLDG_WIN || tile == T.WALL_WIN) {
                    uiFill.color = Color.argb(80, 255, 200, 110)
                    c.drawRect(sx + 8f, sy + 8f, sx + 24f, sy + 24f, uiFill)
                    uiFill.color = Color.argb(34, 255, 200, 110)
                    c.drawRect(sx + 2f, sy + 2f, sx + 30f, sy + 30f, uiFill)
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // 카메라 모드 UI
    // -------------------------------------------------------------------

    private fun drawPhotoOverlay(c: Canvas) {
        val vw = game.virtW.toFloat()
        val vh = game.virtH.toFloat()
        uiFill.color = Color.argb(88, 20, 16, 28)
        c.drawRect(0f, 0f, vw, 42f, uiFill)
        c.drawRect(0f, vh - 48f, vw, vh, uiFill)
        c.drawRect(0f, 0f, 34f, vh, uiFill)
        c.drawRect(vw - 34f, 0f, vw, vh, uiFill)

        // 비네트
        uiFill.color = Color.argb(36, 16, 12, 24)
        c.drawRect(0f, 0f, vw, 14f, uiFill)
        c.drawRect(0f, vh - 14f, vw, vh, uiFill)
        c.drawRect(0f, 0f, 12f, vh, uiFill)
        c.drawRect(vw - 12f, 0f, vw, vh, uiFill)

        // 뷰파인더 코너
        uiStroke.strokeWidth = 3f
        uiStroke.color = Color.argb(220, 255, 250, 235)
        val m = 64f
        val l = 26f
        val path = Path()
        path.moveTo(m, m + l); path.lineTo(m, m); path.lineTo(m + l, m)
        path.moveTo(vw - m - l, m); path.lineTo(vw - m, m); path.lineTo(vw - m, m + l)
        path.moveTo(vw - m, vh - m - l); path.lineTo(vw - m, vh - m); path.lineTo(vw - m - l, vh - m)
        path.moveTo(m + l, vh - m); path.lineTo(m, vh - m); path.lineTo(m, vh - m - l)
        c.drawPath(path, uiStroke)

        // 촬영 반경
        val range = CameraDefs.range(state.cameraLevel) * 16f * WORLD_SCALE
        c.drawCircle((player.cx - camX) * WORLD_SCALE, (player.cy - camY) * WORLD_SCALE, range, dashPaint)

        // 새별 거리 힌트
        uiText.isFakeBoldText = true
        for (b in birds) {
            if (b.state == 2) continue
            val distPx = hypot(b.cx - player.cx, b.cy - player.cy)
            val r = CameraDefs.range(state.cameraLevel)
            if (distPx > r * 16f) continue
            val ratio = (distPx / 16f) / r
            val (label, col) = when {
                ratio < 0.34f -> "가까움" to 0xFF6FBA6B.toInt()
                ratio < 0.67f -> "좋음" to 0xFFF2B63C.toInt()
                else -> "멀어요" to 0xFFE2574C.toInt()
            }
            val bx = (b.cx - camX) * WORLD_SCALE
            val by = (b.y - camY) * WORLD_SCALE - 16f
            uiText.textSize = 12f
            uiText.color = 0xFFF8EFDC.toInt()
            val tw = uiText.measureText(label)
            uiFill.color = Color.argb(190, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRoundRect(RectF(bx - tw / 2 - 6f, by - 10f, bx + tw / 2 + 6f, by + 5f), 5f, 5f, uiFill)
            c.drawText(label, bx - tw / 2, by + 2f, uiText)
        }
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
