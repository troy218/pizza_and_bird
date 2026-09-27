package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
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
    private val grass = GrassField(map)
    private val player = Player()
    private val birds = ArrayList<FieldBird>()
    private val cats = ArrayList<Cat>()
    private val rnd = Random(region.id.hashCode().toLong() + 7L)
    private val viewfinder = Viewfinder(game)

    // 디테일 연출 (날씨·물·발자국·작은 생물·조명) — Fx.kt
    private val fx = WorldFx(map, region.id.hashCode().toLong() + 31L)
    private val weather: Weather get() = state.weather()
    private var stepT = 0f

    var photoMode = false
        private set

    /** 촬영 결과 카드 (셔터가 닫힌 뒤에 띄운다) */
    private var pendingOverlay: Overlay? = null
    private var snapDelay = 0f

    private var camX = 0f
    private var camY = 0f
    override fun cameraOffset(): PointF = PointF(camX, camY)
    private var spawnTimer = 1.5f
    private var hungerAcc = 0f
    private var luckAcc = 0f
    private var hungerWarnT = 0f
    private var saveT = 20f
    private var dustT = 0f
    private var ambientT = 0f
    private var lastSpeed = 0f
    private var chirpT = 4f + rnd.nextFloat() * 6f    // 새 지저귐 효과음 타이머
    private var owlT = 6f + rnd.nextFloat() * 10f     // 밤 부엉이 효과음 타이머

    // 파티클
    private class Pt(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, var max: Float, var col: Int, var size: Float, var sway: Boolean
    )

    private val particles = ArrayList<Pt>()

    private val tinyPaint = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply {
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
        state.px = sx
        state.py = sy
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
        fx.weather = weather
        game.hud.regionLabel = region.name
        game.hud.photoModeHint = false
        game.banner("${region.emoji}  ${region.name}")

        game.audio.playBgm(R.raw.bgm_world)   // 🎵 새가 날아가는 길
        updateAmbience()
    }

    /** 낮 → 새소리(숲 지역은 벌새 허밍), 밤 → 바람 환경음 루프 */
    private fun updateAmbience() {
        when {
            state.isNight() -> game.audio.playAmb(R.raw.amb_wind, 0.2f)
            "forest" in region.habitats -> game.audio.playAmb(R.raw.amb_hum, 0.2f)
            else -> game.audio.playAmb(R.raw.amb_birds, 0.26f)
        }
    }

    // -------------------------------------------------------------------
    // 업데이트
    // -------------------------------------------------------------------

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) {
            game.audio.stopSteps()
            return   // 대화상자/메뉴 중에는 세계 정지
        }

        // 촬영 결과 카드를 닫으면 닫혀 있던 셔터가 다시 열린다
        if (viewfinder.isClosed && pendingOverlay == null) viewfinder.release()
        if (photoMode) viewfinder.update(dt)
        val po = pendingOverlay
        if (po != null) {
            snapDelay -= dt
            if (snapDelay <= 0f) {
                pendingOverlay = null
                openOverlay(po)
            }
        }
        state.playSeconds += dt
        state.advanceClock(dt)
        updateWeather(dt)
        fx.weather = weather

        updatePlayer(dt)
        updateStats(dt)
        checkTileTriggers()

        // 발소리 (걸을 때만 — 자전거는 소리 없이 쌩~)
        val stepping = player.moving && !player.bike
        game.audio.steps(
            if (stepping) Audio.Steps.GRAVEL else Audio.Steps.NONE,
            run = stepping && game.input.isRun
        )

        // 환경음 + 랜덤 지저귐/부엉이
        updateAmbience()
        if (state.isNight()) {
            owlT -= dt
            if (owlT <= 0f) {
                owlT = 14f + rnd.nextFloat() * 18f
                game.sfx(Audio.Sfx.OWL, 0.5f)
            }
        } else if (birds.isNotEmpty()) {
            chirpT -= dt
            if (chirpT <= 0f) {
                chirpT = 7f + rnd.nextFloat() * 9f
                game.sfx(if (rnd.nextBoolean()) Audio.Sfx.BIRD_CHIRP1 else Audio.Sfx.BIRD_CHIRP2, 0.45f)
            }
        }

        // 새
        val birdDt = if (photoMode) dt * 0.35f else dt
        val it = birds.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.update(birdDt, player.cx, player.cy, player.bike, photoMode, map, state.fleeMult() * weather.fleeK)
            if (b.state == 2 && !b.fleeCued) {
                b.fleeCued = true
                game.sfx(Audio.Sfx.BIRD_FLEE, 0.65f)   // 푸드덕! 도망
            }
            if (b.gone) it.remove()
        }

        // 고양이
        for (cat in cats) cat.update(dt, map)

        // 새 스폰
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            trySpawnBird()
            spawnTimer = ((if (state.isNight()) 2f else 2.5f) + rnd.nextFloat() * 3.5f) * weather.spawnK
        }

        // 파티클
        updateParticles(dt)

        // 살아있는 풀 (바람 필드 + 풀잎 상태 머신 + 밟힘 반응)
        grass.update(dt, game.time, player.x + 8f, player.y + 13f, player.bike)
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

        // 디테일 연출 & NPC 말풍선
        fx.update(
            dt, game.time, state.worldTime, camX, camY,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE, player.cx, player.cy
        )
        updateNpcEmotes(dt)

        // 상태 동기화 & 주기 저장
        state.px = player.x
        state.py = player.y
        state.onBike = player.bike
        game.hud.questLabel = if (photoMode) null else when {
            state.questBird != null -> "서브: ${Birds.byId[state.questBird!!]?.name ?: "?"} 사진"
            state.mainQuestFinished -> null
            state.mainQuestStarted -> MainStory.current(state)?.let { "메인: ${it.title}" }
            else -> "메인: 보리 박사를 만나기"
        }

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

    private fun updateWeather(dt: Float) {
        state.weatherSeconds -= dt
        if (state.weatherSeconds > 0f) return

        val old = state.weather()
        val roll = rnd.nextFloat()
        val next = when {
            roll < 0.36f -> Weather.SUNNY
            roll < 0.57f -> Weather.CLOUDY
            roll < 0.76f -> Weather.RAIN
            roll < 0.91f -> Weather.WIND
            else -> Weather.SNOW
        }
        // 눈은 산·북부에서 더 자연스럽지만, 가끔 전국에 내릴 수 있다.
        val chosen = if (next == Weather.SNOW && region.id != "sokcho" && !("mountain" in region.habitats) && rnd.nextFloat() < 0.65f) Weather.CLOUDY else next
        state.weatherId = chosen.id
        state.weatherSeconds = 50f + rnd.nextFloat() * 55f
        if (chosen != old) {
            game.hud.banner("${chosen.icon} 날씨 변화: ${chosen.label}")
            game.hud.toast("${chosen.description} · ${chosen.label}")
        }
    }

    private fun updatePlayer(dt: Float) {
        val input = game.input
        val dx = input.dirX
        val dy = input.dirY
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
            if (photoMode) speed *= 0.5f        // 카메라 모드에선 살금살금
            speed *= state.speedMult()          // 튼튼한 다리 스킬
            if (state.hunger <= 0f) speed *= 0.55f
            speed *= input.moveScale            // 스틱을 민 만큼 (아날로그 설정)
            moveBy(vx * speed * dt, 0f)
            moveBy(0f, vy * speed * dt)
            lastSpeed = speed

            // 발걸음 (지형별 발자국·풀잎·물 튀김)
            stepT -= dt
            if (stepT <= 0f) {
                stepT = when {
                    player.bike -> 0.11f
                    sprint -> 0.2f
                    else -> 0.3f
                }
                fx.onStep(player.cx, player.y + 14.5f, player.facing, player.bike, sprint)
            }
        } else {
            lastSpeed = 0f
            stepT = 0f
        }
        updatePlayerAnim(dt)
    }

    /**
     * 동작 선택 — 서기 / 걷기 / 달리기 / 살금살금 / 카메라 조준 / 페달.
     * 재생 속도를 실제 이동 속도에 비례시켜 발이 미끄러지지 않게 한다.
     */
    private fun updatePlayerAnim(dt: Float) {
        val moving = player.moving
        val sprint = game.input.isRun && !player.bike && moving
        if (player.bike) {
            player.pedalBy(dt, if (moving) lastSpeed else 0f)
            player.play(if (moving) Anim.WALK else Anim.IDLE, dt, if (moving) 1f else 0.6f)
            return
        }
        val anim = when {
            moving && photoMode -> Anim.SNEAK
            moving && sprint -> Anim.RUN
            moving -> Anim.WALK
            photoMode -> Anim.AIM
            else -> Anim.IDLE
        }
        val rate = when (anim) {
            Anim.WALK -> (lastSpeed / 55f).coerceIn(0.55f, 2f)
            Anim.RUN -> (lastSpeed / 82f).coerceIn(0.6f, 2f)
            Anim.SNEAK -> (lastSpeed / 30f).coerceIn(0.5f, 2f)
            else -> 1f
        }
        player.play(anim, dt, rate)
        // 달릴 때는 발이 닿을 때마다 먼지가 폴폴
        if (player.footfall && anim == Anim.RUN) {
            addParticle(
                player.x + 8f + (rnd.nextFloat() - 0.5f) * 6f, player.y + 15f,
                (rnd.nextFloat() - 0.5f) * 8f, -5f, 0.32f,
                Color.argb(110, 170, 150, 115), 2f, false
            )
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
        } * state.hungerMult()                  // 튼튼한 체력 스킬
        hungerAcc += dt * hungerRate
        while (hungerAcc >= 1f) {
            hungerAcc -= 1f
            state.hunger = (state.hunger - 1f).coerceAtLeast(0f)
        }
        luckAcc += dt * 0.05f * state.luckDecayMult()   // 타고난 행운 스킬
        val luckFloor = state.luckFloor()
        while (luckAcc >= 1f) {
            luckAcc -= 1f
            state.luck = (state.luck - 1f).coerceAtLeast(luckFloor)
        }
        if (state.luck < luckFloor) state.luck = luckFloor
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
        game.sfx(Audio.Sfx.WHOOSH, 0.8f)
        game.audio.stopSteps()
        game.fadeTo {
            game.scene = WorldScene(game, targetId, SpawnKind.TUNNEL, Regions.opposite(edge))
        }
    }

    private fun enterHome() {
        state.px = player.x
        state.py = player.y
        SaveManager.save(game.context, state)
        game.audio.stopSteps()
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

        val currentWeather = state.weather()
        val weights = pool.map { it.weight * luckBoost(it) * weatherBirdMultiplier(it, currentWeather) }
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
            if (def.tier.star >= 3) {
                game.toast("✨ 조심하세요… ${def.name}가 나타났어요!")
                game.sfx(Audio.Sfx.NOTIFY, 0.7f)
            }
            if (state.questBird == def.id) {
                game.toast("📋 의뢰의 새 ${def.name} 등장! 📷")
                game.sfx(Audio.Sfx.NOTIFY, 0.7f)
            }
            return
        }
    }

    private fun snap(b: FieldBird) {
        game.sfx(Audio.Sfx.SHUTTER)   // 찰칵!
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
        if (stars < 3 && rnd.nextDouble() < state.extraStarChance()) stars++   // 매의 눈 스킬

        val prev = state.birdCounts[b.def.id] ?: 0
        val isNew = prev == 0
        state.birdCounts[b.def.id] = prev + 1
        val prevBest = state.bestStars[b.def.id] ?: 0
        if (stars > prevBest) state.bestStars[b.def.id] = stars
        state.photos += 1
        if (isNew) state.luck = (state.luck + 4f).coerceAtMost(100f)

        // ----- 경험치 -----
        var expGain = Progression.photoExp(b.def.tier, stars)
        if (isNew) expGain += Progression.newSpeciesExp(b.def.tier)

        var questLine: String? = null
        if (state.questBird == b.def.id) {
            val bonus = if (stars >= 3) (state.questReward * 0.3f).toInt() else 0
            val total = state.questReward + bonus
            state.money += total
            expGain += Progression.questExp(state.questReward)
            questLine = "의뢰 완료! +${won(total)}" + if (bonus > 0) " (3성 보너스)" else ""
            state.questBird = null
            state.questReward = 0
        }

        val prevLevel = state.level
        val levelsGained = state.addExp(expGain)

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

        // 셔터가 닫힌 뒤(0.15초) 결과 카드(폴라로이드)가 뜬다
        viewfinder.shot()
        game.haptic()
        pendingOverlay = PhotoResultOverlay(
            this, b.def, stars, isNew, prev + 1, questLine,
            distTiles = distPx / 16f,
            timeTxt = state.timeLabel(),
            cameraTxt = CameraDefs.name(state.cameraLevel),
            night = state.isNight(),
            expGain = expGain,
            levelsGained = levelsGained,
            prevLevel = prevLevel
        )
        snapDelay = 0.15f
    }

    private fun trySnapAt(vx: Float, vy: Float) {
        var best: FieldBird? = null
        var bestD = Float.MAX_VALUE
        for (b in birds) {
            if (b.state == 2) continue
            val d = hypot(b.cx - vx, b.cy - vy)
            // 카메라 모드에선 AF 박스를 살짝 빗나가게 탭해도 잡히도록 넉넉하게
            val tapR = if (photoMode) 21f else 14f
            if (d < tapR && d < bestD) { best = b; bestD = d }
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
        if (particles.size > 100) return
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
        weather == Weather.RAIN || weather == Weather.SNOW -> "none"     // 비/눈은 WorldFx가 화면 전체에 그린다
        weather == Weather.WIND -> "wind"
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
            "rain" -> addParticle(
                camX + rnd.nextFloat() * halfW * 2f, camY - 8f,
                -18f + rnd.nextFloat() * 8f, 80f + rnd.nextFloat() * 35f, 1.8f,
                Color.argb(150, 100, 160, 210), 1.5f, false
            )
            "wind" -> addParticle(
                camX - 8f, camY + rnd.nextFloat() * halfH * 2f,
                55f + rnd.nextFloat() * 35f, -8f + rnd.nextFloat() * 16f, 3f,
                Color.argb(150, 210, 220, 205), 2f, true
            )
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
        game.sfx(Audio.Sfx.SPARKLE, 0.55f)
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
            NpcKind.VILLAGER -> {
                val storyHint = when (state.mainQuestStage) {
                    0, 1 -> "멀리 가기 전에도 창밖의 새부터 천천히 보면 좋아요."
                    2 -> "숲에서 나무 구멍을 발견해도 가까이 들여다보면 안 돼요. 둥지일 수 있거든요."
                    3 -> "물가 새는 건너편에서 봐도 충분히 아름다워요."
                    4 -> "철새가 쉬는 곳에서는 무리 쪽으로 걷지 않는 게 이 동네 약속이에요."
                    5 -> "갯벌에는 사람 눈에 안 보이는 새들의 식탁이 아주 많대요."
                    else -> "희귀새 위치를 바로 퍼뜨리기 전에 새가 안전할지 한 번 생각해 주세요."
                }
                openOverlay(DialogOverlay(this, npc.name,
                    "\"${map.region.villager}\n$storyHint\"",
                    listOf(DialogOverlay.Choice("기억할게요"))))
            }
            NpcKind.KID -> {
                val lines = listOf(
                    "우와, 카메라 멋져요! 저도 크면 탐조할 거예요!",
                    "저기요, 저 새 이름 알아요? 어… 까먹었어요.",
                    "자전거 타면 빨리 가지만 금방 배고파져요!",
                    "박사님이 낡은 새 수첩을 들고 찾고 있었어요. 가보실래요?",
                    "새 둥지를 찾으면 비밀로 해 줘야 해요. 새끼가 놀라잖아요!",
                    "저는 도감 숫자보다 새 이름을 하나 제대로 아는 게 더 좋아요."
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
                    "해 지기 전에 들어가게. 밤엔 부엉이가 나온다네.",
                    "자네 할머니도 새를 많이 보려 하기보다 오래 보려 했지.",
                    "귀한 새를 봤다면 발자국을 남기지 않는 게 가장 좋은 자랑이라네."
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
        val main = MainStory.current(state)
        val mainLabel = when {
            state.mainQuestFinished -> "메인 이야기 (완료)"
            !state.mainQuestStarted -> "메인 이야기 시작"
            main?.isComplete(state) == true -> "메인 이야기 (보고!)"
            else -> "메인 이야기"
        }
        val sideLabel = if (state.questBird == null) "사진 의뢰 받기" else "사진 의뢰 확인"
        openOverlay(
            DialogOverlay(
                this, "보리 박사",
                when {
                    state.mainQuestFinished -> "\"우리의 지도는 완성됐지만 새들의 계절은 계속되지. 사진 의뢰도, 도장 깨기도 언제든 찾아오게.\""
                    !state.mainQuestStarted -> "\"마침 잘 왔네. 자네 가족이 남긴 낡은 탐조 수첩에 관한 이야기가 있어. 물론 급한 일은 아니니 사진 의뢰부터 해도 좋고.\""
                    else -> "\"메인 기록과 사진 의뢰는 서로 별개일세. 마음 가는 순서대로 천천히 하게.\""
                },
                buildList {
                    add(DialogOverlay.Choice(mainLabel) { showMainStory() })
                    add(DialogOverlay.Choice(sideLabel) { showSideQuest() })
                    add(DialogOverlay.Choice("다음에 올게요"))
                }
            )
        )
    }

    private fun showMainStory() {
        if (state.mainQuestFinished) {
            openOverlay(DialogOverlay(this, "함께 사는 지도",
                "메인 퀘스트는 만렙에서 완결됐어요. 서브 의뢰와 컬렉션은 계속 자유롭게 즐길 수 있습니다."))
            return
        }
        val chapter = MainStory.current(state) ?: return
        val ready = chapter.isComplete(state)
        val objective = chapter.objective(state)
        openOverlay(
            DialogOverlay(
                this, chapter.title,
                "\"${chapter.intro}\"\n\n목표: $objective" + if (ready) "\n✓ 기록을 정리할 준비가 됐어요." else "",
                buildList {
                    if (!state.mainQuestStarted) {
                        add(DialogOverlay.Choice("수첩을 이어 쓸게요") { completeMainChapter(chapter) })
                    } else if (ready) {
                        add(DialogOverlay.Choice("기록을 보여드릴게요") { completeMainChapter(chapter) })
                    } else {
                        add(DialogOverlay.Choice("목표를 기억할게요"))
                    }
                    add(DialogOverlay.Choice("사진 의뢰 보기") { showSideQuest() })
                }
            )
        )
    }

    private fun completeMainChapter(chapter: MainStory.Chapter) {
        // 열린 대화 도중 상태가 바뀌었더라도 중복 보상은 지급하지 않는다.
        if (MainStory.current(state) !== chapter) return
        if (state.mainQuestStarted && !chapter.isComplete(state)) return
        state.mainQuestStarted = true
        state.money += chapter.rewardMoney
        val levels = state.addExp(chapter.rewardExp)
        state.mainQuestStage++
        if (state.mainQuestStage >= MainStory.CHAPTERS.size) state.mainQuestFinished = true
        SaveManager.save(game.context, state)
        game.sfx(Audio.Sfx.REWARD, 0.9f)
        val reward = buildString {
            if (chapter.rewardMoney > 0) append("\n보상 ${won(chapter.rewardMoney)}")
            if (chapter.rewardExp > 0) append(" · 경험치 +${chapter.rewardExp}")
            if (levels > 0) append(" · 레벨 업!")
        }
        openOverlay(
            DialogOverlay(
                this, if (state.mainQuestFinished) "메인 퀘스트 완결" else "장 완료",
                "\"${chapter.complete}\"$reward",
                listOf(DialogOverlay.Choice(if (state.mainQuestFinished) "그래도 탐조는 계속된다" else "다음 장을 향해"))
            )
        )
    }

    /** 무작위 사진 의뢰는 메인 스토리 진행도와 무관하게 언제든 수락·포기할 수 있다. */
    private fun showSideQuest() {
        val cur = state.questBird
        if (cur == null) {
            val pool = Birds.poolFor(map.region, false)
            if (pool.isEmpty()) return
            val unphoto = pool.filter { (state.birdCounts[it.id] ?: 0) == 0 && it.tier.star <= 2 }
            val candidates = if (unphoto.isNotEmpty() && rnd.nextDouble() < 0.55) unphoto else pool
            val def = candidates[rnd.nextInt(candidates.size)]
            openOverlay(
                DialogOverlay(
                    this, "보리 박사의 사진 의뢰",
                    "\"이 지역에 ${def.name}가 나타났다는 소문이 있어.\n메인 기록과 상관없이 사진 한 장 부탁하네!\n보수는 ${won(def.reward)}.\"",
                    listOf(
                        DialogOverlay.Choice("맡겨주세요!") {
                            state.questBird = def.id
                            state.questReward = def.reward
                            SaveManager.save(game.context, state)
                            game.toast("서브 의뢰 접수: ${def.name} 사진 📷")
                            game.sfx(Audio.Sfx.NOTIFY, 0.8f)
                        },
                        DialogOverlay.Choice("다른 일을 할게요"))
                )
            )
        } else {
            val def = Birds.byId[cur]
            openOverlay(
                DialogOverlay(
                    this, "진행 중인 사진 의뢰",
                    "\"아직 ${def?.name ?: "그 새"} 사진인가?\n시간 제한은 없으니 원하는 때에 찍어 오게.\n포기해도 메인 이야기에는 영향이 없네.\"",
                    listOf(
                        DialogOverlay.Choice("계속할게요"),
                        DialogOverlay.Choice("의뢰 포기하기") {
                            state.questBird = null
                            state.questReward = 0
                            SaveManager.save(game.context, state)
                            game.toast("사진 의뢰를 포기했어요. 언제든 새 의뢰를 받을 수 있어요.")
                        })
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
                                    game.sfx(Audio.Sfx.BUY)
                                } else {
                                    game.toast("돈이 부족해요… 박사 의뢰를 해볼까요?")
                                    game.sfx(Audio.Sfx.FAIL, 0.5f)
                                }
                            }
                        )
                    }
                    add(DialogOverlay.Choice("장식 코너 보기") {
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
                setPhotoMode(false)
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
            setPhotoMode(!photoMode)
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
            game.sfx(if (player.bike) Audio.Sfx.BIKE_BELL else Audio.Sfx.BIKE_BRAKE, 0.8f)
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
                game.sfx(Audio.Sfx.SPARKLE, 0.5f, 1.15f)
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
                // 셔터가 닫히는 중이거나 결과 카드가 대기 중이면 무시
                if (!viewfinder.busy && pendingOverlay == null) trySnapAt(tap.x, tap.y)
                return
            }
            // 이정표 탭
            tapSign(tap.x, tap.y)
            // 근처 NPC가 여러 명이어도 실제로 탭한 주민과 대화한다.
            val npc = map.npcs.filter { hypot(it.cx - player.cx, it.cy - player.cy) < 46f }
                .minByOrNull { hypot(it.cx - tap.x, it.cy - tap.y) }
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

    /** 카메라 모드 전환 — 뷰파인더 연출과 HUD 정리까지 한 번에 */
    private fun setPhotoMode(on: Boolean) {
        if (photoMode == on) return
        photoMode = on
        game.hud.photoModeHint = on
        game.hud.showStats = !on
        game.hud.showMinimap = !on
        if (on) {
            game.hud.questLabel = null
            viewfinder.onEnter()
            game.haptic()
            game.toast("📷 카메라 모드 — 새를 탭해 촬영하세요")
        } else {
            game.hud.showStats = true
            game.hud.showMinimap = true
            viewfinder.release()
        }
    }

    private fun quickEat() {
        val pid = state.eatBest()
        if (pid == null) {
            game.toast("피자가 없어요! 집의 화덕이나 오븐에서 구워요 🍕")
            game.sfx(Audio.Sfx.FAIL, 0.45f)
        } else {
            val p = Pizzas.of(pid)
            game.toast("냠냠! ${p.emoji} ${p.fullName}")
            game.sfx(Audio.Sfx.EAT, 0.9f)
        }
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFF3A3040.toInt())
        val camXv = camX * WORLD_SCALE
        val camYv = camY * WORLD_SCALE
        // 햇빛 그림자: 아침엔 서쪽, 저녁엔 동쪽으로 길게 (흐리거나 비 오면 옅게)
        val hour = state.worldTime
        val dl = daylight(hour)
        val sunT = ((hour - 12f) / 6f).coerceIn(-1.1f, 1.1f)
        val sunAlpha = (54f * dl * weather.shadowK).toInt()
        map.draw(
            c, game.assets, camXv, camYv, game.virtW, game.virtH, game.time,
            sunDx = sunT * 22f, sunLen = 12f + abs(sunT) * 14f, sunAlpha = sunAlpha
        )
        fx.drawGround(c, camXv, camYv, game.virtW, game.virtH)

        // 살아있는 풀 — 뒤쪽 레이어(캐릭터보다 위). 밑동이 발보다 위인 풀잎들.
        val feetY = player.y + 13f
        grass.draw(c, game.assets, camXv, camYv, game.virtW.toFloat(), game.virtH.toFloat(), feetY, GrassField.LAYER_BACK)
        drawCloudShadows(c, camXv, camYv)

        // 엔티티 (y 정렬)
        val ents = ArrayList<Any>(map.npcs.size + birds.size + cats.size + 1)
        ents.addAll(map.npcs)
        ents.addAll(cats)
        ents.addAll(birds)
        ents.add(player)
        ents.sortBy { sortY(it) }
        for (e in ents) drawEntity(c, e)

        // 살아있는 풀 — 앞쪽 레이어. 캐릭터가 풀밭을 헤치며 걷는 깊이감
        grass.draw(c, game.assets, camXv, camYv, game.virtW.toFloat(), game.virtH.toFloat(), feetY, GrassField.LAYER_FRONT)

        drawParticles(c, camXv, camYv)
        fx.drawAir(c, camXv, camYv, game.virtW, game.virtH)
        fx.drawWeather(c, game.virtW, game.virtH)
        drawLighting(c, camXv, camYv)
        drawNpcOverlays(c)
        if (photoMode) {
            viewfinder.draw(c, birds, player.cx, player.cy, camX, camY)
            viewfinder.drawShutter(c)
        }
    }

    /** 발밑 타일이 풀숲이면 1(키 큰 풀) / 2(갈대), 아니면 0 */
    private fun grassKindAt(lx: Float, ly: Float): Int = when (map.t((lx / 16f).toInt(), (ly / 16f).toInt())) {
        T.TALLGRASS -> 1
        T.REED -> 2
        else -> 0
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
                // NPC마다 위상을 달리해 같은 동작이 겹치지 않게 한다
                val bmp = a.npcBitmap(e.kind, game.time, e.tileX * 0.37f + e.tileY * 0.71f)
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE
                c.drawOval(
                    RectF(sx + 8f, sy + 26f, sx + 24f, sy + 32f),
                    a.shadowPaint
                )
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
                // 메인 보고 가능 또는 새 서브 의뢰가 있으면 느낌표 표시
                if (e.kind == NpcKind.PROFESSOR) {
                    val mainReady = !state.mainQuestFinished &&
                        (!state.mainQuestStarted || MainStory.current(state)?.isComplete(state) == true)
                    if (mainReady || state.questBird == null) {
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
            }
            is Cat -> {
                val bmp = a.catBitmap(e.walking, e.phase, e.faceLeft)
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE - e.lift * WORLD_SCALE
                c.drawOval(RectF(sx + 8f, (e.cy - camY) * WORLD_SCALE + 6f, sx + 24f, (e.cy - camY) * WORLD_SCALE + 12f), a.shadowPaint)
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
                val gk = grassKindAt(e.cx, e.cy + 3f)
                if (gk != 0) fx.drawGrassOver(c, sx + 4f, sy + bmp.height, bmp.width - 8f, gk == 2, e.state == 1)
                // 밤에 웅크린 고양이는 쿨쿨
                if (e.state == 0 && state.isNight()) {
                    val zt = (game.time * 0.8f) % 1f
                    tinyPaint.textSize = 10f + zt * 4f
                    tinyPaint.color = Color.argb((230 * (1f - zt)).toInt(), 248, 239, 220)
                    c.drawText("z", sx + 22f + zt * 6f, sy + 4f - zt * 12f, tinyPaint)
                    tinyPaint.color = 0xFF4A3728.toInt()
                }
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
                    val gk = grassKindAt(e.cx, e.y + bmp.height / WORLD_SCALE - 1f)
                    if (gk != 0 && e.state == 0) fx.drawGrassOver(c, bx + 2f, by + bmp.height, bmp.width - 4f, gk == 2, false)
                }
            }
            is Player -> {
                val bmp: android.graphics.Bitmap = if (player.bike) {
                    a.bikeBitmap(state.gender, state.gearTier(), player.facing, player.pedal)
                } else {
                    a.playerSet(state.gender, state.gearTier())
                        .clip(player.anim).frame(player.facing, player.frame)
                }
                val sx = (player.x - camX) * WORLD_SCALE
                val sy = (player.y - camY) * WORLD_SCALE
                // 뛰거나 페달을 밟을 때 그림자도 함께 호흡한다
                val k = when {
                    player.bike -> 1f - 0.06f * sin(player.pedal * 6.2832f)
                    player.anim == Anim.RUN -> 1f - 0.16f * abs(sin(player.phase * 6.2832f))
                    player.anim == Anim.WALK -> 1f - 0.07f * abs(sin(player.phase * 6.2832f))
                    else -> 1f
                }
                val half = 10f * k
                c.drawOval(RectF(sx + 16f - half, sy + 25f - 1f * k, sx + 16f + half, sy + 31f + 1f * k), a.shadowPaint)
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
                // 풀숲에 들어가면 발목이 풀에 가려진다
                val gk = grassKindAt(player.cx, player.y + 13f)
                if (gk != 0) fx.drawGrassOver(c, sx + 3f, sy + 32f, 26f, gk == 2, player.moving)
            }
        }
    }

    private fun drawCloudShadows(c: Canvas, camXv: Float, camYv: Float) {
        // 맑으면 구름 3조각, 강풍 4조각(빠르게), 흐리면 6조각 — 비/눈은 하늘 전체가 흐려 그림자가 없다
        val n = when (weather) {
            Weather.SUNNY -> 3
            Weather.WIND -> 4
            Weather.CLOUDY -> 6
            else -> 0
        }
        val windK = if (weather == Weather.WIND) 3.2f else 1f   // 바람 부는 날엔 구름 그림자가 빠르게 지나간다
        if (n == 0) return
        cloudPaint.color = Color.argb(if (weather == Weather.CLOUDY) 34 else 26, 18, 30, 56)
        for (i in 0 until n) {
            val speed = (7f + (i % 3) * 3.5f + (i / 3) * 2f) * windK
            val w = 250f + (i % 3) * 70f
            val span = map.w * 32f + 800f
            val cxw = ((game.time * speed + i * 430f) % span) - 400f
            val cyw = 110f + (i % 3) * 200f + (i / 3) * 110f + sin(game.time * 0.13f + i * 2f) * 50f
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
        val night = Color.argb(124, 20, 24, 62)
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

    /**
     * 낮밤 조명. 시각에 맞는 어둠(새벽 주황 → 낮 → 노을 → 밤 남색)을 조명 맵으로 깔고,
     * 가로등·창문·터널 등·반딧불·플레이어 주변에 부드러운 빛 구멍을 낸 뒤 전구색 번짐을 얹는다.
     */
    private fun drawLighting(c: Canvas, camXv: Float, camYv: Float) {
        val col = ambientColor()
        val alpha = Color.alpha(col)
        if (alpha == 0) return
        val k = (alpha / 124f).coerceIn(0f, 1f)
        val lm = LightMaps.get(game.virtW, game.virtH)
        lm.begin(col)

        val x0 = (camXv / 32f).toInt().coerceAtLeast(0)
        val y0 = (camYv / 32f).toInt().coerceAtLeast(0)
        val x1 = ((camXv + game.virtW) / 32f).toInt().coerceAtMost(map.w - 1)
        val y1 = ((camYv + game.virtH) / 32f).toInt().coerceAtMost(map.h - 1)
        val ly0 = (y0 - 2).coerceAtLeast(0)
        val ly1 = (y1 + 2).coerceAtMost(map.h - 1)
        val lx0 = (x0 - 2).coerceAtLeast(0)
        val lx1 = (x1 + 2).coerceAtMost(map.w - 1)
        val flicker = 0.94f + sin(game.time * 7.3f) * 0.03f + sin(game.time * 13.1f) * 0.03f

        // 1) 빛 구멍
        for (y in ly0..ly1) {
            for (x in lx0..lx1) {
                val sx = x * 32f - camXv
                val sy = y * 32f - camYv
                when (map.t(x, y)) {
                    T.LAMP -> {
                        lm.light(sx + 16f, sy + 10f, 70f, (255 * k).toInt())
                        lm.light(sx + 16f, sy + 34f, 60f, 26f, (220 * k).toInt())      // 바닥 빛 웅덩이
                    }
                    T.HOUSE_WIN, T.BLDG_WIN, T.WALL_WIN -> lm.light(sx + 16f, sy + 20f, 40f, 34f, (205 * k).toInt())
                    T.TUNNEL -> lm.light(sx + 16f, sy + 6f, 30f, (170 * k).toInt())
                    else -> {}
                }
            }
        }
        // 플레이어 주변은 은은하게 (밤에도 캐릭터가 묻히지 않게)
        val px = (player.cx - camX) * WORLD_SCALE
        val py = (player.cy - camY) * WORLD_SCALE - 8f
        lm.light(px, py, 66f, (120 * k).toInt())
        fx.fireflies(c, lm, true, camXv, camYv, game.virtW, game.virtH)
        lm.end(c)

        // 2) 전구색 빛 번짐
        for (y in ly0..ly1) {
            for (x in lx0..lx1) {
                val sx = x * 32f - camXv
                val sy = y * 32f - camYv
                when (map.t(x, y)) {
                    T.LAMP -> {
                        Glow.draw(c, Glow.warm, sx + 16f, sy + 6f, 26f, 26f, (170 * k * flicker).toInt())
                        Glow.draw(c, Glow.warm, sx + 16f, sy + 36f, 40f, 16f, (70 * k).toInt())
                    }
                    T.HOUSE_WIN, T.BLDG_WIN, T.WALL_WIN -> {
                        uiFill.color = Color.argb((110 * k).toInt(), 255, 206, 120)
                        c.drawRect(sx + 8f, sy + 8f, sx + 24f, sy + 22f, uiFill)
                        Glow.draw(c, Glow.warm, sx + 16f, sy + 16f, 24f, 20f, (90 * k).toInt())
                    }
                    T.TUNNEL -> Glow.draw(c, Glow.warm, sx + 16f, sy + 4f, 12f, 12f, (150 * k * flicker).toInt())
                    else -> {}
                }
            }
        }
        fx.fireflies(c, null, false, camXv, camYv, game.virtW, game.virtH)
    }

    // -------------------------------------------------------------------
    // NPC 이름표 / 말풍선
    // -------------------------------------------------------------------

    private fun updateNpcEmotes(dt: Float) {
        val night = state.isNight()
        for (n in map.npcs) {
            if (n.emoteT > 0f) {
                n.emoteT -= dt
                if (n.emoteT <= 0f) n.emote = null
                continue
            }
            n.emoteCd -= dt
            if (n.emoteCd > 0f) continue
            n.emoteCd = 7f + rnd.nextFloat() * 8f
            if (n.kind == NpcKind.PROFESSOR && state.questBird == null) continue   // "!" 말풍선이 우선
            val rainy = weather == Weather.RAIN
            val opts = when (n.kind) {
                NpcKind.VILLAGER -> if (rainy) listOf("☔", "💧", "…") else listOf("♪", "🌸", "🐦")
                NpcKind.KID -> if (night) listOf("🥱", "🌙") else if (rainy) listOf("☔", "💦") else listOf("♪", "!", "🦋", "😆")
                NpcKind.ELDER -> if (night) listOf("💤", "🌙") else if (rainy) listOf("☔", "🍵") else listOf("…", "🍵", "☀️")
                NpcKind.SHOP -> listOf("📷", "✨", "💰")
                NpcKind.PROFESSOR -> listOf("🔍", "📖", "🐦")
            }
            n.emote = opts[rnd.nextInt(opts.size)]
            n.emoteT = 2.6f
        }
    }

    private fun drawNpcOverlays(c: Canvas) {
        for (n in map.npcs) {
            val sx = (n.x - camX) * WORLD_SCALE
            val sy = (n.y - camY) * WORLD_SCALE
            if (sx < -60f || sx > game.virtW + 60f || sy < -60f || sy > game.virtH + 60f) continue
            var top = if (n.kind == NpcKind.PROFESSOR && state.questBird == null) sy - 26f else sy - 4f
            val near = hypot(n.cx - player.cx, n.cy - player.cy) < 46f
            if (near) {
                // 이름표 (월드 캔버스라 px 단위)
                val np = Type.paintPx(11f, true, 0.02f, 0xFFF8EFDC.toInt())
                val tw = np.measureText(n.name)
                val cx = sx + 16f
                uiFill.color = Color.argb(200, 58, 52, 74)
                c.drawRoundRect(RectF(cx - tw / 2 - 6f, top - 15f, cx + tw / 2 + 6f, top), 7f, 7f, uiFill)
                c.drawText(n.name, cx - tw / 2, top - 4f, np)
                top -= 18f
            }
            val em = n.emote ?: continue
            val appear = ((2.6f - n.emoteT) / 0.2f).coerceIn(0f, 1f)
            val fade = (n.emoteT / 0.3f).coerceIn(0f, 1f)
            val a = (255 * minOf(appear, fade)).toInt()
            val cx = sx + 16f
            val by = top - 4f - (1f - appear) * 4f + sin(game.time * 3f) * 1.2f
            uiFill.color = Color.argb(a, 253, 250, 240)
            c.drawRoundRect(RectF(cx - 12f, by - 18f, cx + 12f, by), 7f, 7f, uiFill)
            val tail = Path()
            tail.moveTo(cx - 4f, by - 1f); tail.lineTo(cx, by + 5f); tail.lineTo(cx + 4f, by - 1f); tail.close()
            c.drawPath(tail, uiFill)
            uiStroke.strokeWidth = 1.4f
            uiStroke.color = Color.argb(a, 107, 79, 53)
            c.drawRoundRect(RectF(cx - 12f, by - 18f, cx + 12f, by), 7f, 7f, uiStroke)
            val ep = Type.paintPx(12f, false, 0f, Color.argb(a, 74, 55, 40))
            val ew = ep.measureText(em)
            c.drawText(em, cx - ew / 2, by - 5f, ep)
        }
    }

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
            // 월드 캔버스(가상 해상도)라 px 로 크기를 준다
            val lp = Type.paintPx(12f, true, 0.03f, Type.CREAM)
            val tw = lp.measureText(label)
            uiFill.color = Color.argb(190, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRoundRect(RectF(bx - tw / 2 - 6f, by - 10f, bx + tw / 2 + 6f, by + 5f), 5f, 5f, uiFill)
            c.drawText(label, bx - tw / 2, by + 2f, lp)
        }
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
