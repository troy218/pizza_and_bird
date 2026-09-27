package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
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
    // 재사용 정렬 버퍼: 매 프레임 엔티티 목록을 새로 만들지 않아 GC 부하를 줄인다.
    private val drawEntities = ArrayList<Any>(map.npcs.size + 16)
    private val drawEntityOrder = Comparator<Any> { a, b -> sortY(a).compareTo(sortY(b)) }
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

    /** 화면 움직임 리그 — 셰이크 · 헤드밥 · 시야각 · 레이트 트래킹 (촬영 장비 rig 와 별개) */
    val viewRig = ViewRig(state)
    private val streaks = SpeedStreaks()
    private val speedVignette = SpeedVignette()
    private val focusMask = RadialMask()

    /** 캔버스 원점에 대응하는 월드 좌표 (줌·흔들림·헤드밥이 모두 반영된 그리기 기준) */
    private var camX = 0f
    private var camY = 0f
    /** 탭 좌표 역변환용 — drawWorld가 쓰는 카메라 기준점 (줌은 Game.screenToWorld가 함께 반영) */
    override fun cameraOffset(): PointF = PointF(camX, camY)

    // 이동 속도 (논리 px/s) — 예측 배치 · 속도 연출 · 잔상에 쓰인다
    private var velX = 0f
    private var velY = 0f
    private var prevSpeed = 0f
    private var lastDirX = 0f
    private var lastDirY = 1f
    private var bumpCd = 0f

    // 카메라 모드 피사체 추적 (다이내믹 포커싱)
    private var focusBird: FieldBird? = null
    private var subjX = 0f
    private var subjY = 0f
    private var focusK = 0f
    private var focusR = 0f

    // 잔상(모션 블러)용 위치 링버퍼
    private val ghostX = FloatArray(GHOSTS)
    private val ghostY = FloatArray(GHOSTS)
    private var ghostHead = 0
    private var ghostFill = 0
    private var ghostT = 0f
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
    private val cloudPaint = Paint().apply { color = Color.argb(26, 18, 30, 56); isAntiAlias = true }
    private val uiFill = Paint()
    private val glowFill = Paint()
    private val vignettePaint = Paint()
    private val uiStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        state.region = region.id
        state.inHome = false

        val (sx, sy) = when (spawnKind) {
            SpawnKind.SAVED -> state.px to state.py
            SpawnKind.HOME -> 376f to 12.2f * 16f
            SpawnKind.FAST -> 18f * 16f to 14f * 16f   // 중앙 광장 — 보리 박사 바로 옆
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
            SpawnKind.FAST -> Dir.N      // 광장 한가운데(박사 방향)을 바라본다
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
        // 카메라 초기 스냅 (보간 없이 바로 제자리)
        subjX = player.cx
        subjY = player.cy
        for (i in 0 until GHOSTS) {
            ghostX[i] = player.x
            ghostY[i] = player.y
        }
        viewRig.snap(
            player.cx, player.cy, 1f,
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE
        )
        syncCamera()

        game.hud.showControls = true
        game.hud.showStats = true
        game.hud.showMinimap = true
        fx.weather = weather
        game.hud.regionLabel = region.name
        game.hud.photoModeHint = false
        game.banner("${region.emoji}  ${region.name}")

        game.audio.playBgm(regionBgm())       // 🎵 지역 분위기에 맞는 곡
        updateAmbience()
    }

    /**
     * 지역 성격에 맞는 BGM 고르기.
     *  - 산·숲    → 🎵 강원도 산 (bgm_mountain)
     *  - 해안·섬·갯벌 → 🎵 바다 (bgm_sea)
     *  - 그 밖(도시·강·들판) → 🎵 새가 날아가는 길 (bgm_world)
     */
    private fun regionBgm(): Int = when {
        region.kind == RegionKind.MOUNTAIN || "mountain" in region.habitats -> R.raw.bgm_mountain
        region.kind == RegionKind.COAST || "coast" in region.habitats -> R.raw.bgm_sea
        region.kind == RegionKind.WETLAND -> R.raw.bgm_sea
        else -> R.raw.bgm_world
    }

    /**
     * 지역 성격 + 시간대에 맞는 환경음 루프.
     *
     *   강풍     -> 바람 소리     (amb_wind)
     *   밤       -> 풀벌레 우는 밤 (amb_night)
     *   바닷가   -> 파도와 갈매기 (amb_sea)
     *   숲       -> 숲속 새소리   (amb_forest)
     *   산       -> 낮은 허밍     (amb_hum)
     *   그 외 낮 -> 들판 새소리   (amb_birds)
     */
    private fun updateAmbience() {
        when {
            weather == Weather.WIND -> game.audio.playAmb(R.raw.amb_wind, 0.22f)
            state.isNight() -> game.audio.playAmb(R.raw.amb_night, 0.24f)
            "coast" in region.habitats -> game.audio.playAmb(R.raw.amb_sea, 0.26f)
            "forest" in region.habitats -> game.audio.playAmb(R.raw.amb_forest, 0.24f)
            "mountain" in region.habitats -> game.audio.playAmb(R.raw.amb_hum, 0.2f)
            else -> game.audio.playAmb(R.raw.amb_birds, 0.26f)
        }
    }

    /** 지역에 어울리는 지저귐 한 소리 — 숲·산에선 뻐꾸기가 섞인다 */
    private fun randomChirp(): Audio.Sfx {
        val woods = "forest" in region.habitats || "mountain" in region.habitats
        if (woods && rnd.nextFloat() < 0.4f) {
            return if (rnd.nextBoolean()) Audio.Sfx.CUCKOO1 else Audio.Sfx.CUCKOO2
        }
        return if (rnd.nextBoolean()) Audio.Sfx.BIRD_CHIRP1 else Audio.Sfx.BIRD_CHIRP2
    }

    // -------------------------------------------------------------------
    // 업데이트
    // -------------------------------------------------------------------

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) {
            game.audio.stopSteps()
            idleCamera(dt)   // 세계는 멈춰도 카메라 여운은 이어진다
            return
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
        updateSeason()
        updateWeather(dt)
        fx.weather = weather
        seasonFx.update(dt, state.season(), weather, game.virtW.toFloat(), game.virtH.toFloat())

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
                // 가끔은 부엉이 대신 까마귀가 밤공기를 가른다
                if (rnd.nextFloat() < 0.3f) game.sfx(Audio.Sfx.CROW, 0.42f)
                else game.sfx(Audio.Sfx.OWL, 0.5f)
            }
        } else if (birds.isNotEmpty()) {
            chirpT -= dt
            if (chirpT <= 0f) {
                chirpT = 7f + rnd.nextFloat() * 9f
                game.sfx(randomChirp(), 0.45f)
            }
        }

        // 히트스톱 — 결정적인 순간(셔터)엔 세상만 잠깐 느려진다
        val wdt = viewRig.worldDt(dt)

        // 새
        val birdDt = if (photoMode) wdt * 0.35f else wdt
        val it = birds.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.update(birdDt, player.cx, player.cy, player.bike, photoMode, map, state.fleeMult() * weather.fleeK, state.bikeScareMult())
            if (b.state == 2 && !b.fleeCued) {
                b.fleeCued = true
                game.sfx(Audio.Sfx.BIRD_FLEE, 0.65f)   // 푸드덕! 도망
                onBirdFlush(b)                          // 가까우면 놀라서 화면도 흔들린다
            }
            if (b.gone) it.remove()
        }

        // 고양이
        for (cat in cats) cat.update(wdt, map)

        // 새 스폰
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            trySpawnBird()
            spawnTimer = ((if (state.isNight()) 2f else 2.5f) + rnd.nextFloat() * 3.5f) * weather.spawnK
        }

        // 파티클
        updateParticles(dt)

        // 살아있는 풀 (바람 필드 + 풀잎 상태 머신 + 밟힘 반응)
        // 풀은 32px 렌더 좌표, 캐릭터는 16px 논리 좌표를 사용한다.
        grass.update(dt, game.time, (player.x + 8f) * WORLD_SCALE, (player.y + 13f) * WORLD_SCALE, player.bike)
        spawnAmbient(dt)
        if (player.bike && player.moving) {
            dustT -= dt
            if (dustT <= 0f) {
                dustT = 0.16f
                addParticle(player.x + 8f, player.y + 14f, (rnd.nextFloat() - 0.5f) * 10f, -6f, 0.5f, Color.argb(120, 148, 128, 96), 3f, false)
            }
        }

        // ---- 카메라 (몰입 리그) ----
        updateFocus(dt)
        updateGhosts(dt)
        viewRig.update(
            dt,
            focusX(), focusY(),
            velX, velY,
            gait(),
            teleZoom(),
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE,
            allowRoll = !photoMode
        )
        syncCamera()
        streaks.update(dt, velX, velY, viewRig.speedFx, game.virtW.toFloat(), game.virtH.toFloat())

        // 디테일 연출 & NPC 말풍선 (보이는 영역은 카메라 리그가 알려 준다)
        // 화면 크기는 따로 등록한다 — 화면 공간 비/눈 입자가 월드 시야·줌에 끌려다니지 않게
        fx.setScreen(game.virtW.toFloat(), game.virtH.toFloat())
        fx.update(
            dt, game.time, state.worldTime, viewRig.x, viewRig.y,
            viewRig.viewW, viewRig.viewH, player.cx, player.cy
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

    // -------------------------------------------------------------------
    // 카메라 리그 보조 (Camera.kt)
    // -------------------------------------------------------------------

    override fun camera(): ViewRig = viewRig

    /** 오버레이가 열린 동안: 흔들림·줌 펀치만 잦아들게 굴린다 */
    private fun idleCamera(dt: Float) {
        viewRig.update(
            dt, player.cx, player.cy, 0f, 0f, Gait.IDLE, teleZoom(),
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE,
            allowRoll = !photoMode
        )
        syncCamera()
    }

    /** 현재 이동 상태 — 헤드밥 주기·시야각·예측 거리를 정한다 */
    private fun gait(): Gait = when {
        !player.moving -> Gait.IDLE
        player.bike -> Gait.BIKE
        game.input.isRun -> Gait.RUN
        else -> Gait.WALK
    }

    /**
     * 카메라 모드 망원 배율 — 실제로 장착한 렌즈의 환산 초점거리를 따라간다.
     * 50mm 표준이면 거의 그대로, 600mm 초망원이면 확실히 당겨진다 (로그 곡선).
     */
    private fun teleZoom(): Float {
        if (!photoMode) return 1f
        val mm = state.rig().teleMm.coerceIn(20, 1600).toFloat()
        val k = ((ln(mm) - ln(50f)) / (ln(1200f) - ln(50f))).coerceIn(0f, 1f)
        return 1.03f + 0.19f * k
    }

    /** 카메라가 바라보는 지점 — 카메라 모드에서는 피사체 쪽으로 살짝 치우친다 */
    private fun focusX(): Float = player.cx + (subjX - player.cx) * 0.34f * focusK
    private fun focusY(): Float = player.cy + (subjY - player.cy) * 0.34f * focusK

    /**
     * 캔버스 원점(0,0)에 대응하는 월드 좌표를 다시 계산한다.
     * 월드는 화면 중앙 기준으로 zoom 배율만큼 확대/축소되므로,
     * 보이는 영역의 좌상단이 화면 좌상단에 오도록 보정해 둔다.
     */
    private fun syncCamera() {
        val z = viewRig.zoom
        val hx = game.virtW / 2f
        val hy = game.virtH / 2f
        camX = (viewRig.x * WORLD_SCALE - hx + game.virtW / (2f * z)) / WORLD_SCALE
        camY = (viewRig.y * WORLD_SCALE - hy + game.virtH / (2f * z)) / WORLD_SCALE
    }

    /** 확대 전 캔버스 좌표 -> 실제 화면 좌표 */
    private fun projX(ux: Float): Float {
        val hx = game.virtW / 2f
        return hx + (ux - hx) * viewRig.zoom
    }

    private fun projY(uy: Float): Float {
        val hy = game.virtH / 2f
        return hy + (uy - hy) * viewRig.zoom
    }

    /**
     * 다이내믹 포커싱 — 사거리 안에서 가장 가까운 새를 자동으로 붙잡고(트래킹),
     * 초점 반경을 부드럽게 좁힌다(포커스 풀).
     */
    private fun updateFocus(dt: Float) {
        var best: FieldBird? = null
        var bestD = Float.MAX_VALUE
        if (photoMode) {
            val range = state.rig().reach * 16f
            for (b in birds) {
                if (b.state == 2) continue
                val d = hypot(b.cx - player.cx, b.cy - player.cy)
                if (d <= range && d < bestD) {
                    best = b
                    bestD = d
                }
            }
        }
        focusBird = best
        // 새로 붙잡는 순간에는 그 자리에서 시작해야 초점이 화면을 가로질러 날아가지 않는다
        if (best != null && focusK < 0.02f) {
            subjX = best.cx
            subjY = best.cy
        }
        val tx = best?.cx ?: player.cx
        val ty = best?.cy ?: player.cy
        val k = CamFx.smoothK(dt, 0.2f)
        subjX += (tx - subjX) * k
        subjY += (ty - subjY) * k
        focusK += ((if (photoMode && best != null) 1f else 0f) - focusK) * CamFx.smoothK(dt, 0.3f)

        val rTarget = when {
            !photoMode -> 0f
            best == null -> 150f
            else -> 30f + (bestD / 16f) * 6f
        }
        focusR += (rTarget - focusR) * CamFx.smoothK(dt, 0.26f)
    }

    /** 잔상(모션 블러)용 과거 위치 기록 */
    private fun updateGhosts(dt: Float) {
        ghostT -= dt
        if (ghostT > 0f) return
        ghostT = 0.035f
        ghostX[ghostHead] = player.x
        ghostY[ghostHead] = player.y
        ghostHead = (ghostHead + 1) % GHOSTS
        if (ghostFill < GHOSTS) ghostFill++
    }

    /** 새가 푸드덕 날아오르는 순간 — 가까울수록 놀라서 화면이 흔들린다 */
    private fun onBirdFlush(b: FieldBird) {
        val d = hypot(b.cx - player.cx, b.cy - player.cy)
        val k = (1f - d / 80f).coerceIn(0f, 1f)
        if (k <= 0.02f) return
        viewRig.shake(0.08f + 0.17f * k)
        viewRig.kick(player.cx - b.cx, player.cy - b.cy, 1.2f * k)
    }

    /** 셔터 — 플래시 + 손떨림 킥 + 짧은 히트스톱 + 줌 펀치 */
    private fun shutterFx() {
        viewRig.flashScreen(0.55f)      // 뷰파인더 셔터막 연출과 겹치므로 은은하게
        viewRig.shake(0.3f)
        viewRig.kick(0f, -1f, 1.6f)
        viewRig.punchZoom(0.05f)
        viewRig.freeze(0.09f)
    }

    private val seasonFx = SeasonFx()
    private var lastSeason: Season? = null

    /** 계절이 바뀌면 배너로 알리고 날씨를 곧 새로 뽑는다. */
    private fun updateSeason() {
        val now = state.season()
        val prev = lastSeason
        lastSeason = now
        if (prev != null && prev != now) {
            game.hud.banner("${now.icon} ${now.label}이 왔어요 — ${now.description}")
            state.weatherSeconds = minOf(state.weatherSeconds, 3f)
        }
    }

    private fun updateWeather(dt: Float) {
        state.weatherSeconds -= dt
        if (state.weatherSeconds > 0f) return

        val old = state.weather()
        // 계절별 확률표 (여름 장마, 겨울 눈) — Season.kt
        val season = state.season()
        val next = rollSeasonWeather(season, kotlin.random.Random(rnd.nextLong()))
        // 눈은 산·북부에서 더 자연스럽다. 겨울이 아니면 남부/평지 눈은 흐림으로 바뀐다.
        val snowSkip = if (season == Season.WINTER) 0.25f else 0.65f
        val chosen = if (next == Weather.SNOW && region.id != "sokcho" && !("mountain" in region.habitats) && rnd.nextFloat() < snowSkip) Weather.CLOUDY else next
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
        if (bumpCd > 0f) bumpCd -= dt
        var blocked = false
        if (moving) {
            if (abs(dx) > abs(dy)) player.facing = if (dx > 0) Dir.E else Dir.W
            else if (abs(dy) > 0.01f) player.facing = if (dy > 0) Dir.S else Dir.N

            var vx = dx
            var vy = dy
            val len = sqrt(vx * vx + vy * vy)
            if (len > 0.01f) { vx /= len; vy /= len }
            val sprint = input.isRun && !player.bike
            var speed = if (player.bike) 97f * state.bikeSpeedMult() else 55f
            if (sprint) speed *= 1.45f
            if (photoMode) speed *= 0.5f        // 카메라 모드에선 살금살금
            speed *= state.speedMult()          // 튼튼한 다리 스킬
            if (state.hunger <= 0f) speed *= 0.55f
            speed *= input.moveScale            // 스틱을 민 만큼 (아날로그 설정)
            if (!moveBy(vx * speed * dt, 0f)) blocked = true
            if (!moveBy(0f, vy * speed * dt)) blocked = true
            lastSpeed = speed
            velX = vx * speed
            velY = vy * speed
            lastDirX = vx
            lastDirY = vy

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
            velX = 0f
            velY = 0f
        }

        // ---- 카메라에 전달할 '몸으로 느끼는' 사건들 ----
        val sp = hypot(velX, velY)
        // 1) 벽·바위에 부딪힘 — 자전거일수록 크게 '쿵'
        if (blocked && sp > 30f && bumpCd <= 0f) {
            bumpCd = 0.42f
            viewRig.shake(if (player.bike) 0.34f else 0.14f)
            viewRig.kick(-lastDirX, -lastDirY, if (player.bike) 2.6f else 1.1f)
            if (player.bike) {
                game.sfx(Audio.Sfx.BIKE_BRAKE, 0.5f)
                for (i in 0 until 4) {
                    addParticle(
                        player.cx, player.y + 15f,
                        (rnd.nextFloat() - 0.5f) * 26f, -12f - rnd.nextFloat() * 8f,
                        0.45f, Color.argb(120, 150, 132, 104), 3f, false
                    )
                }
            }
        }
        // 2) 급정거 — 관성으로 몸이 앞으로 쏠린다
        if (prevSpeed > 72f && sp < 6f) {
            viewRig.kick(lastDirX, lastDirY, 1.7f)
            viewRig.shake(0.08f)
        }
        prevSpeed = sp

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

    /** 이동 시도. 벽에 막히면 false */
    private fun moveBy(dx: Float, dy: Float): Boolean {
        if (dx == 0f && dy == 0f) return true
        val nx = player.x + dx
        val ny = player.y + dy
        if (!map.solidBox(nx, ny)) {
            player.x = nx
            player.y = ny
            return true
        }
        return false
    }

    private fun updateStats(dt: Float) {
        val moving = player.moving
        val sprinting = game.input.isRun && !player.bike && moving
        val hungerRate = when {
            sprinting -> 0.24f
            player.bike && moving -> 0.22f * state.bikeHungerMult()
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
        // 터널로 빨려 들어가는 느낌 — 살짝 광각으로 벌어지며 흔들린다
        viewRig.punchZoom(-0.055f)
        viewRig.shake(0.2f)
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
        val pool = Birds.poolFor(map.region, n, state.day, state.worldTime)
        return if (pool.isEmpty()) Birds.poolFor(map.region, !n) else pool
    }

    private fun trySpawnBird() {
        if (birds.size >= 3) return
        val pool = regionPool()
        if (pool.isEmpty()) return

        val currentWeather = state.weather()
        val currentSeason = state.season()
        val weights = pool.map {
            Birds.spawnWeight(it, map.region, state.day, state.worldTime) *
                    luckBoost(it) *
                    weatherBirdMultiplier(it, currentWeather) *
                    seasonBirdMultiplier(it, currentSeason, currentWeather)
        }
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
            val fieldBird = FieldBird(def, bx, by)
            val firstPose = game.assets.birdPose(def.id, fieldBird.facing, fieldBird.renderPose)
            fieldBird.sprW = (firstPose.width / WORLD_SCALE).toInt().coerceAtLeast(12)
            fieldBird.sprH = (firstPose.height / WORLD_SCALE).toInt().coerceAtLeast(12)
            // 커진 정밀 스프라이트도 기존 타일의 발 위치/중심에 정확히 착지시킨다.
            fieldBird.x = bx + 8f - fieldBird.sprW / 2f
            fieldBird.y = by + 9f - fieldBird.sprH
            birds.add(fieldBird)
            if (def.tier.star >= 3) {
                viewRig.punchZoom(0.03f)              // 희귀새 등장 — 숨을 죽이듯 살짝 당겨진다
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
        val rig = state.rig()
        val distPx = hypot(b.cx - player.cx, b.cy - player.cy)
        val distTiles = distPx / 16f
        val ratio = distTiles / rig.reach
        var stars = when {
            ratio < 0.38f -> 3
            ratio < 0.72f -> 2
            else -> 1
        }

        val notes = ArrayList<String>()
        val dark = state.darkness()
        val wet = state.weather() == Weather.RAIN || state.weather() == Weather.SNOW
        val movingShot = player.moving
        val fastShot = player.bike || (game.input.isRun && player.moving)

        // 화질 — 센서/화소/렌즈 해상력
        if (stars < 3 && rnd.nextFloat() < rig.iqChance()) {
            stars++
            notes.add("${rig.sensor.label} 센서의 디테일이 살았어요")
        }
        // 저조도 — 어두우면 조리개와 고감도 성능이 갈린다
        if (dark > 0.25f) {
            val risk = rig.noiseRisk(dark)
            if (stars > 1 && rnd.nextFloat() < risk) {
                stars--
                notes.add("어두워서 ISO가 올라가 노이즈가 꼈어요")
            } else if (rig.lowLight >= 6.5f) {
                notes.add("밝은 렌즈 덕에 셔터 속도를 확보했어요")
            }
        }
        // 손떨림 — 움직이면서 초망원을 들면 흔들린다
        if (movingShot) {
            val risk = rig.shakeRisk(true, fastShot)
            if (stars > 1 && rnd.nextFloat() < risk) {
                stars--
                notes.add("움직이면서 찍어 화면이 흔들렸어요")
            } else if (rig.steady >= 6f) {
                notes.add("손떨림 보정이 흔들림을 잡아 줬어요")
            }
        }
        // 비·눈 — 방진방적이 없으면 렌즈에 물방울
        if (wet && !rig.weatherProof) {
            if (stars > 1 && rnd.nextFloat() < 0.28f) {
                stars--
                notes.add("렌즈에 물방울이 맺혔어요 (방진방적 없음)")
            }
        }
        // 행운 / 매의 눈
        if (stars < 3 && rnd.nextDouble() < state.effectiveLuck() / 520.0) stars++
        if (stars < 3 && rnd.nextDouble() < state.extraStarChance()) stars++   // 매의 눈 스킬
        stars = stars.coerceIn(1, 3)

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
        if (levelsGained > 0) {
            viewRig.punchZoom(0.06f)
            viewRig.shake(0.22f)
        }

        // 셔터 순간의 방향/자세와 현재 지형을 한 프레임으로 굳혀 사진집에 저장한다.
        // 이후 새가 날아가거나 다른 지역으로 이동해도 이 사진은 그대로 남는다.
        val photographedFacing = b.facing
        val photographedPose = b.renderPose
        val capturedPhoto = captureHabitatPhoto(b, photographedFacing, photographedPose)
        val photoId = "${System.currentTimeMillis()}_${state.photos}"
        val photoFile = PhotoArchive.save(game.context, photoId, capturedPhoto)
        val photoRecord = BirdPhotoRecord(
            id = photoId,
            birdId = b.def.id,
            stars = stars,
            regionId = region.id,
            day = state.day,
            time = state.worldTime,
            weatherId = state.weatherId,
            facing = photographedFacing,
            pose = photographedPose,
            fileName = photoFile,
            camera = rig.title,
            distance = distTiles
        )
        state.photoAlbum.add(photoRecord)
        while (state.photoAlbum.size > PhotoArchive.MAX_PHOTOS) {
            val removed = state.photoAlbum.removeAt(0)
            PhotoArchive.delete(game.context, removed.fileName)
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

        // 셔터가 닫힌 뒤(0.15초) 결과 카드(폴라로이드)가 뜬다
        viewfinder.shot()
        shutterFx()                 // 손떨림 · 히트스톱 · 줌 펀치
        game.haptic()
        pendingOverlay = PhotoResultOverlay(
            this, b.def, stars, isNew, prev + 1, questLine,
            distTiles = distTiles,
            timeTxt = state.timeLabel(),
            cameraTxt = rig.title,
            night = state.isNight(),
            expGain = expGain,
            levelsGained = levelsGained,
            prevLevel = prevLevel,
            reachTiles = rig.reach,
            exif = rig.exifLine(dark),
            notes = notes,
            capturedPhoto = capturedPhoto,
            birdFacing = photographedFacing,
            birdPose = photographedPose
        )
        snapDelay = 0.15f
    }

    /**
     * 로데오 스템피드식 기념사진: 피사체는 중앙에 크게, 셔터를 누른 실제 타일/도로/물가/
     * 건물/나무는 그대로 배경에 담는다. 결과 비트맵은 사진집 파일로 보존된다.
     */
    private fun captureHabitatPhoto(b: FieldBird, facing: BirdFacing, pose: BirdPose): Bitmap {
        val w = 720
        val h = 405
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint().apply { isAntiAlias = false }
        val camPhotoX = b.cx * WORLD_SCALE - w * 0.5f
        val camPhotoY = b.cy * WORLD_SCALE - h * 0.60f

        c.drawColor(0xFF8FC9DF.toInt())
        val hour = state.worldTime
        val dl = daylight(hour)
        val sunT = ((hour - 12f) / 6f).coerceIn(-1.1f, 1.1f)
        map.draw(
            c, game.assets, camPhotoX, camPhotoY, w, h, game.time,
            sunDx = sunT * 20f,
            sunLen = 11f + kotlin.math.abs(sunT) * 13f,
            sunAlpha = (50f * dl * weather.shadowK).toInt()
        )
        fx.drawGround(c, camPhotoX, camPhotoY, w, h)
        grass.draw(
            c, game.assets, camPhotoX, camPhotoY, w.toFloat(), h.toFloat(),
            b.cy * WORLD_SCALE, GrassField.LAYER_BACK
        )

        // 시간대와 날씨도 촬영 당시 모습으로 굳힌다.
        val dark = state.darkness()
        if (dark > 0.02f) {
            p.color = Color.argb((dark * 142f).toInt().coerceIn(0, 142), 12, 20, 48)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        when (weather) {
            Weather.CLOUDY -> {
                p.color = Color.argb(35, 82, 91, 105); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            Weather.RAIN -> {
                p.color = Color.argb(34, 54, 72, 92); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.color = Color.argb(150, 205, 225, 240)
                for (i in 0 until 92) {
                    val x = ((i * 83 + b.def.birdNum * 17) % (w + 50)).toFloat() - 25f
                    val y = ((i * 47 + state.day * 23) % h).toFloat()
                    c.drawRect(x, y, x + 1.4f, y + 12f, p)
                }
            }
            Weather.SNOW -> {
                p.color = Color.argb(210, 250, 252, 255)
                for (i in 0 until 74) {
                    val x = ((i * 97 + b.def.birdNum * 13) % w).toFloat()
                    val y = ((i * 53 + state.day * 29) % h).toFloat()
                    val rr = 1.3f + (i % 4) * 0.55f
                    c.drawCircle(x, y, rr, p)
                }
            }
            Weather.WIND -> {
                p.color = Color.argb(65, 238, 244, 242)
                for (i in 0 until 9) {
                    val y = 32f + i * 39f
                    c.drawRect(30f + (i % 3) * 54f, y, 188f + (i % 3) * 54f, y + 1.2f, p)
                }
            }
            else -> Unit
        }

        // 큰 피사체 아래에도 원래 지면이 충분히 보이도록 반투명 접지 그림자만 얹는다.
        val subject = game.assets.birdPose(b.def.id, facing, pose)
        val maxW = w * 0.43f
        val maxH = h * 0.54f
        val scale = minOf(maxW / subject.width, maxH / subject.height)
        val sw = subject.width * scale
        val sh = subject.height * scale
        val sx = w / 2f - sw / 2f
        val sy = h * 0.53f - sh / 2f
        p.color = Color.argb(76, 24, 30, 24)
        c.drawOval(RectF(w / 2f - sw * 0.34f, sy + sh * 0.86f, w / 2f + sw * 0.34f, sy + sh * 0.99f), p)
        game.assets.sprPaint.alpha = 255
        c.drawBitmap(subject, null, RectF(sx, sy, sx + sw, sy + sh), game.assets.sprPaint)

        // 렌즈 비네트. 배경은 보존하되 중앙의 새로 시선이 모인다.
        for (i in 0 until 6) {
            val band = 10f + i * 7f
            p.color = Color.argb(10 + i * 3, 18, 16, 24)
            c.drawRect(0f, band, 8f, h - band, p)
            c.drawRect(w - 8f, band, w.toFloat(), h - band, p)
            c.drawRect(band, 0f, w - band, 6f, p)
            c.drawRect(band, h - 6f, w - band, h.toFloat(), p)
        }
        return bitmap
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
            game.toast("그곳엔 새가 없어요…")
            return
        }
        val rig = state.rig()
        val distTiles = hypot(target.cx - player.cx, target.cy - player.cy) / 16f
        if (distTiles > rig.reach) {
            game.toast("너무 멀어요! (${rig.teleMm}mm 사거리 ${rig.reach.fmt1()}칸) 조금 더 가까이…")
            game.sfx(Audio.Sfx.FAIL, 0.4f)
            return
        }
        if (distTiles < rig.minDist) {
            game.toast("너무 가까워요! ${rig.teleMm}mm 화각엔 다 안 들어와요 (최소 ${rig.minDist.fmt1()}칸)")
            game.sfx(Audio.Sfx.FAIL, 0.4f)
            return
        }
        // AF — 움직이는 새는 초점을 놓칠 수 있다 (연사가 빠르면 한 번 더 기회)
        if (target.state == 1) {
            var miss = rnd.nextFloat() < rig.afMissChance(target.def.tier.star)
            if (miss && rig.burstRetry()) {
                miss = rnd.nextFloat() < rig.afMissChance(target.def.tier.star) * 0.5f
                if (!miss) game.toast("연사로 겨우 건졌어요! 📸")
            }
            if (miss) {
                game.toast("초점을 놓쳤어요… 움직이는 새엔 빠른 AF가 필요해요")
                game.sfx(Audio.Sfx.SHUTTER, 0.7f)
                game.sfx(Audio.Sfx.FAIL, 0.5f)
                target.state = 2
                target.fleeVx = 60f
                target.fleeVy = -75f
                target.fleeT = 0f
                return
            }
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

    // 입자 좌표 소유권 — 혼동 금지!
    //  - 월드 공간(카메라를 따라 움직임): 꽃잎·낙엽·반딧불·바람결 등 여기(spawnAmbient)의 입자
    //  - 화면 공간(캐릭터 이동과 무관): 비·눈 — WorldFx(drawWeather)가 화면 전체에 직접 그린다.
    //    비/눈을 월드 입자로 옮기면 카메라에 붙어 같이 밀리므로 절대 옮기지 않는다.
    private fun ambientKind(): String = when {
        weather == Weather.RAIN || weather == Weather.SNOW -> "none"
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
        val viewX = viewRig.x
        val viewY = viewRig.y
        val viewW = viewRig.viewW
        val viewH = viewRig.viewH
        when (ambientKind()) {
            "wind" -> addParticle(
                viewX - 8f, viewY + rnd.nextFloat() * viewH,
                55f + rnd.nextFloat() * 35f, -8f + rnd.nextFloat() * 16f, 3f,
                Color.argb(150, 210, 220, 205), 2f, true
            )
            "leaf" -> addParticle(
                viewX + rnd.nextFloat() * viewW, viewY - 8f,
                (rnd.nextFloat() - 0.5f) * 6f, 10f + rnd.nextFloat() * 6f, 4f,
                if (rnd.nextBoolean()) Color.argb(170, 111, 174, 87) else Color.argb(170, 200, 140, 70), 3f, true
            )
            "petal" -> addParticle(
                viewX + rnd.nextFloat() * viewW, viewY - 8f,
                8f + rnd.nextFloat() * 8f, 6f + rnd.nextFloat() * 5f, 4.5f,
                Color.argb(150, 242, 163, 179), 3f, true
            )
            "sparkle" -> addParticle(
                viewX + rnd.nextFloat() * viewW, viewY + rnd.nextFloat() * viewH,
                0f, -3f, 1.8f, Color.argb(160, 250, 250, 255), 2.2f, false
            )
            "firefly" -> addParticle(
                viewX + rnd.nextFloat() * viewW, viewY + rnd.nextFloat() * viewH,
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

    private fun signDirection(sx: Int, sy: Int): Dir = when {
        sy <= 2 -> Dir.N
        sy >= map.h - 5 -> Dir.S
        sx <= 3 -> Dir.W
        else -> Dir.E
    }

    private fun directionName(dir: Dir): String = when (dir) {
        Dir.N -> "북쪽"
        Dir.E -> "동쪽"
        Dir.S -> "남쪽"
        Dir.W -> "서쪽"
    }

    private fun signTarget(sx: Int, sy: Int): RegionDef? {
        val targetId = Regions.exits(map.region.id)[signDirection(sx, sy)] ?: return null
        return Regions.byId[targetId]
    }

    private fun tapSign(vx: Float, vy: Float) {
        val tx = (vx / 16f).toInt()
        val ty = (vy / 16f).toInt()
        if (map.t(tx, ty) == T.SIGN) {
            val target = signTarget(tx, ty)
            if (target != null) game.toast("🪧 ${directionName(signDirection(tx, ty))} 터널 → ${target.name}")
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
        if (SideStories.intercept(this, npc)) return   // [P08] 사이드 스토리 진행 중이면 우선
        val dlg = Dialogues.Ctx(map.region.id, state.mainQuestStage, state.season(), weather, state.isNight(), state.day)   // [P08]
        when (npc.kind) {
            NpcKind.PROFESSOR -> talkProfessor()
            NpcKind.SHOP -> talkShop()
            NpcKind.VILLAGER -> {
                openOverlay(DialogOverlay(this, npc.name,
                    Dialogues.villager(dlg),
                    listOf(DialogOverlay.Choice("기억할게요"))))
            }
            NpcKind.KID -> {
                openOverlay(
                    DialogOverlay(
                        this, npc.name, Dialogues.kid(dlg),
                        listOf(DialogOverlay.Choice("ㅎㅎ 귀엽다"))
                    )
                )
            }
            NpcKind.ELDER -> {
                openOverlay(
                    DialogOverlay(
                        this, npc.name, Dialogues.elder(dlg),
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
        val advice = MainQuestAdvisor.advise(state)
        val adviceLine = advice?.let { adv ->
            if (adv.alreadyThere) "\n📍 ${adv.tip}" else "\n📍 추천 장소: ${adv.regionName}"
        } ?: ""
        openOverlay(
            DialogOverlay(
                this, chapter.title,
                "\"${chapter.intro}\"\n\n목표: $objective" +
                    (if (ready) "\n✓ 기록을 정리할 준비가 됐어요." else "") + adviceLine,
                buildList {
                    if (!state.mainQuestStarted) {
                        add(DialogOverlay.Choice("수첩을 이어 쓸게요") { completeMainChapter(chapter) })
                    } else if (ready) {
                        add(DialogOverlay.Choice("기록을 보여드릴게요") { completeMainChapter(chapter) })
                    } else {
                        add(DialogOverlay.Choice("목표를 기억할게요"))
                    }
                    advice?.let { adv ->
                        if (!adv.alreadyThere && adv.regionId != state.region) {
                            add(DialogOverlay.Choice("🚲 이동하기") { fastTravel(game, adv.regionId) })
                        }
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
            val n = state.isNight()
            val pool = Birds.poolFor(map.region, n, state.day, state.worldTime).ifEmpty { Birds.poolFor(map.region, !n) }
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
        val rig = state.rig()
        val lines = listOf(
            "\"어서 와! 지금 장비는 ${rig.title},\n환산 ${rig.teleMm}mm에 촬영 반경 ${rig.reach.fmt1()}칸이구먼.\n바디랑 렌즈는 따로 팔아. 천천히 골라 봐.\"",
            "\"새를 크게 찍고 싶으면 답은 하나야. 초점거리!\n다만 무거운 렌즈는 배가 금방 고파진다네.\"",
            "\"센서가 크면 어두운 새벽에도 깨끗하지.\n대신 지갑이 어두워지지만 말이야. 허허.\""
        )
        openOverlay(
            DialogOverlay(
                this, "사진용품점",
                lines[rnd.nextInt(lines.size)],
                listOf(
                    DialogOverlay.Choice("카메라 진열대") {
                        it.scene.openOverlay(CameraShopOverlay(it.scene))
                    },
                    DialogOverlay.Choice("장비 가방(조립)") {
                        it.scene.openOverlay(GearBagOverlay(it.scene))
                    },
                    DialogOverlay.Choice("자전거 상점 🚲") {
                        it.scene.openOverlay(BikeShopOverlay(it.scene))
                    },
                    DialogOverlay.Choice("장식 코너") {
                        it.scene.openOverlay(DecorShopOverlay(it.scene))
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
            // 올라타고 내릴 때의 체중 이동
            viewRig.kick(0f, if (player.bike) -1f else 1f, 1.2f)
            viewRig.punchZoom(if (player.bike) -0.02f else 0.02f)
            game.sfx(if (player.bike) Audio.Sfx.BIKE_BELL else Audio.Sfx.BIKE_BRAKE, 0.8f)
            game.toast(
                if (player.bike) "${state.bike().name} 탔다! 쌩~ 🚲"
                else "${state.bike().name}에서 내렸어요"
            )
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
                    game.toast("🪧 ${directionName(signDirection(sx, sy))} 터널 → ${target.name}")
                    return
                }
            }
            if (nearTile(T.BENCH) != null) {
                restAtBench()
                return
            }
            nearestCat()?.let { cat ->
                viewRig.kick(0f, 1f, 0.5f)
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
            return
        }
        // 카메라 오프셋·망원 배율을 모두 역변환한 월드 좌표 (Game.screenToWorld)
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
        }
    }

    /** 카메라 모드 전환 — 뷰파인더 연출과 HUD 정리까지 한 번에 */
    private fun setPhotoMode(on: Boolean) {
        if (photoMode == on) return
        photoMode = on
        // 뷰파인더에 눈을 붙이는 느낌 — 망원으로 당겨지며 초점이 잡힌다
        viewRig.punchZoom(if (on) 0.022f else -0.022f)
        streaks.clear()
        game.hud.photoModeHint = on
        game.hud.showStats = !on
        game.hud.showMinimap = !on
        if (on) {
            game.hud.questLabel = null
            viewfinder.onEnter()
            game.haptic()
        } else {
            game.hud.showStats = true
            game.hud.showMinimap = true
            viewfinder.release()
        }
    }

    private fun quickEat() {
        val pid = state.eatBest()
        if (pid == null) {
            game.toast("피자가 없어요! 🍕")
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
        val vw = game.virtW.toFloat()
        val vh = game.virtH.toFloat()
        val hx = vw / 2f
        val hy = vh / 2f
        val z = viewRig.zoom
        val camXv = camX * WORLD_SCALE
        val camYv = camY * WORLD_SCALE
        val seeW = vw / z              // 실제로 보이는 가상 px 폭 (줌아웃하면 더 넓다)
        val seeH = vh / z
        // 보이는 폭이 화면과 다른 만큼(줌 인/아웃) + 흔들림·기울기 여유(PAD)까지 더 그린다
        val padX = abs(seeW - vw) / 2f + PAD
        val padY = abs(seeH - vh) / 2f + PAD
        val padW = (seeW + padX * 2f).toInt()
        val padH = (seeH + padY * 2f).toInt()

        // ---- 월드 패스: 시야각(줌) + 기울기(셰이크)를 화면 중앙 기준으로 적용 ----
        c.save()
        if (viewRig.roll != 0f) c.rotate(viewRig.roll, hx, hy)
        if (z != 1f) c.scale(z, z, hx, hy)

        // 햇빛 그림자: 아침엔 서쪽, 저녁엔 동쪽으로 길게 (흐리거나 비 오면 옅게)
        val hour = state.worldTime
        val dl = daylight(hour)
        val sunT = ((hour - 12f) / 6f).coerceIn(-1.1f, 1.1f)
        val sunAlpha = (54f * dl * weather.shadowK).toInt()

        // 지면은 흔들림·기울기로 가장자리가 비지 않게 PAD 만큼 넓게 그린다
        c.save()
        c.translate(-padX, -padY)
        // 이정표는 가까이 갔을 때만 보인다 — 발 위치 기준(지도 렌더 좌표 = 월드 × WORLD_SCALE)
        map.signViewerX = player.cx * WORLD_SCALE
        map.signViewerY = (player.y + 13f) * WORLD_SCALE
        map.draw(
            c, game.assets, camXv - padX, camYv - padY, padW, padH, game.time,
            sunDx = sunT * 22f, sunLen = 12f + abs(sunT) * 14f, sunAlpha = sunAlpha
        )
        fx.drawGround(c, camXv - padX, camYv - padY, padW, padH)

        // 살아있는 풀 — 뒤쪽 레이어(캐릭터보다 위). 밑동이 발보다 위인 풀잎들.
        val feetY = (player.y + 13f) * WORLD_SCALE
        grass.draw(c, game.assets, camXv - padX, camYv - padY, padW.toFloat(), padH.toFloat(), feetY, GrassField.LAYER_BACK)
        c.restore()
        drawCloudShadows(c, camXv, camYv)

        // 엔티티 (y 정렬)
        drawEntities.clear()
        drawEntities.addAll(map.npcs)
        drawEntities.addAll(cats)
        drawEntities.addAll(birds)
        drawEntities.add(player)
        drawEntities.sortWith(drawEntityOrder)
        for (e in drawEntities) drawEntity(c, e)

        // 살아있는 풀 — 지면에 고정된 전경으로 발목을 가린다. 엔티티에 풀을 붙여 그리지 않는다.
        c.save()
        c.translate(-padX, -padY)
        grass.draw(c, game.assets, camXv - padX, camYv - padY, padW.toFloat(), padH.toFloat(), feetY, GrassField.LAYER_FRONT)
        c.restore()

        drawParticles(c, camXv, camYv)
        c.save()
        c.translate(-padX, -padY)
        fx.drawAir(c, camXv - padX, camYv - padY, padW, padH)
        c.restore()
        drawLighting(c, camXv, camYv)
        drawWarmShafts(c)
        drawVignette(c)
        drawNpcOverlays(c)
        drawTunnelOverlays(c)
        drawExitHints(c)
        c.restore()

        // ---- 스크린 패스: 날씨 · 속도 연출 · 심도 · 뷰파인더 (UI는 흔들지 않는다) ----
        seasonFx.draw(c, state.season(), game.virtW.toFloat(), game.virtH.toFloat())
        fx.drawWeather(c)
        if (viewRig.speedFx > 0.02f) {
            speedVignette.draw(c, vw, vh, viewRig.speedFx)
            streaks.draw(c, velX, velY, viewRig.speedFx)
        }
        if (photoMode) {
            drawDepthOfField(c, vw, vh)
            viewfinder.draw(c, birds, player.cx, player.cy, camX, camY, z)
            viewfinder.drawShutter(c)
        }
        if (viewRig.flash > 0.001f) {
            uiFill.color = Color.argb((235 * viewRig.flash).toInt().coerceIn(0, 255), 255, 252, 244)
            c.drawRect(0f, 0f, vw, vh, uiFill)
        }
    }

    /**
     * 다이내믹 포커싱(심도) — 초점 반경 밖을 부드럽게 눌러 시선을 피사체로 모은다.
     *  255), 255, 252, 244)
            c.drawRect(0f, 0f, vw, vh, uiFill)
        }
    }

    /**
     * 다이내믹 포커싱(심도) — 초점 반경 밖을 부드럽게 눌러 시선을 피사체로 모은다.
     * 렌즈(카메라 등급)가 좋을수록 심도가 얕아진다.
     */
    private fun drawDepthOfField(c: Canvas, vw: Float, vh: Float) {
        if (!state.camDof) return
        // 밝은 렌즈(작은 F값)일수록 얕은 심도 — 배경이 더 많이 날아간다
        val bokeh = ((8f - state.rig().apTele) / 6f).coerceIn(0f, 1f)
        val hasSubject = focusBird != null
        val cx = if (hasSubject) projX((subjX - camX) * WORLD_SCALE) else projX((player.cx - camX) * WORLD_SCALE)
        val cy = if (hasSubject) projY((subjY - camY) * WORLD_SCALE) else projY((player.cy - camY) * WORLD_SCALE)
        val r = if (hasSubject) {
            (focusR * WORLD_SCALE * viewRig.zoom * (2.8f - 0.5f * bokeh)).coerceIn(90f, 520f)
        } else {
            430f
        }
        val a = (64f + 52f * bokeh) * (0.45f + 0.55f * focusK)
        focusMask.draw(
            c, cx, cy, r, vw, vh,
            Color.argb(0, 9, 9, 18),
            Color.argb(a.toInt().coerceIn(0, 255), 9, 9, 18),
            0.44f
        )
    }

    /** 잔상(모션 블러) — 빠르게 움직일 때 지나온 자리에 옅은 분신을 남긴다 */
    private fun drawGhosts(c: Canvas, bmp: android.graphics.Bitmap) {
        val k = viewRig.speedFx
        if (!state.camBlur || k < 0.08f || ghostFill < GHOSTS) return
        val a = game.assets
        for (i in 1..2) {
            val idx = ((ghostHead - i * 2) % GHOSTS + GHOSTS) % GHOSTS
            val gx = (ghostX[idx] - camX) * WORLD_SCALE
            val gy = (ghostY[idx] - camY) * WORLD_SCALE
            val alpha = ((78 - i * 26) * k).toInt().coerceIn(0, 255)
            if (alpha <= 3) continue
            a.sprPaint.alpha = alpha
            c.drawBitmap(bmp, gx, gy, a.sprPaint)
        }
        a.sprPaint.alpha = 255
    }

    private fun sortY(e: Any): Float = when (e) {
        is Npc -> e.y + 14f
        is Cat -> e.y + 12f
        is FieldBird -> e.y + e.sprH
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
                } else if (SideStories.hasMarker(game.context, map.region.id, e.kind)) {
                    // [P08] 사이드 스토리 대기/진행 중인 NPC 머리 위 💬 마커 (에피소드 완료 시 사라짐)
                    val bx = sx + 16f
                    val by = sy - 12f
                    bubbleFill.color = 0xFF8FC7F0.toInt()
                    c.drawCircle(bx, by, 9f, bubbleFill)
                    c.drawCircle(bx, by, 9f, bubbleStroke)
                    tinyPaint.textSize = 13f
                    val tw = tinyPaint.measureText("💬")
                    c.drawText("💬", bx - tw / 2, by + 5f, tinyPaint)
                }
            }
            is Cat -> {
                val bmp = a.catBitmap(e.walking, e.phase, e.faceLeft)
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE - e.lift * WORLD_SCALE
                c.drawOval(RectF(sx + 8f, (e.cy - camY) * WORLD_SCALE + 6f, sx + 24f, (e.cy - camY) * WORLD_SCALE + 12f), a.shadowPaint)
                c.drawBitmap(bmp, sx, sy, a.sprPaint)
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
                } else {
                    a.birdPose(e.def.id, e.facing, e.renderPose)
                }
                // 자세마다 투명 여백/크기가 달라도 몸 중심과 발 위치는 고정한다.
                // 덕분에 정면↔옆면 전환 때 새가 순간이동하거나 땅에 파묻히지 않는다.
                val bx = (e.cx - camX) * WORLD_SCALE - bmp.width / 2f
                val by = (e.y + e.sprH - camY) * WORLD_SCALE - bmp.height - e.hopLift * WORLD_SCALE
                // 날아오르면 땅의 그림자가 빠르게 작아져 입체감이 생긴다.
                if (!flying || e.fleeT < 0.32f) {
                    val shadowK = if (flying) (1f - e.fleeT / 0.32f).coerceIn(0.2f, 1f) else 1f
                    val shadowCx = bx + bmp.width * 0.5f
                    val shadowHalf = bmp.width * 0.4f * shadowK
                    val groundY = (e.y + e.sprH - camY) * WORLD_SCALE
                    c.drawOval(
                        RectF(
                            shadowCx - shadowHalf, groundY - 2f,
                            shadowCx + shadowHalf, groundY + 4f
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
                val bmp: android.graphics.Bitmap = if (player.bike) {
                    a.bikeBitmap(state.gender, state.gearTier(), player.facing, player.pedal, state.bikeStyle())
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
                drawGhosts(c, bmp)
                c.drawBitmap(bmp, sx, sy, a.sprPaint)

                // 장착한 카메라를 몸에 겹쳐 그린다 (촬영 모드면 눈높이로 들어올린다)
                val look = state.rig().look
                val camDir = when (player.facing) {
                    Dir.E -> 2
                    Dir.W -> 3
                    Dir.N -> 1
                    else -> 0
                }
                val raised = photoMode
                // 걸을 때의 위아래 흔들림에 카메라도 같이 호흡한다
                val bob = if (raised) 0f else when {
                    player.bike -> sin(player.pedal * 6.2832f) * 0.5f
                    player.anim == Anim.RUN -> sin(player.phase * 6.2832f) * 0.9f
                    player.anim == Anim.WALK -> sin(player.phase * 6.2832f) * 0.6f
                    else -> 0f
                }
                val lift = if (raised) -1f else 0f
                val ride = if (player.bike) 1.5f else 0f
                c.drawBitmap(a.camHeld(look, camDir, raised), sx, sy + bob + lift + ride, a.sprPaint)

                // 촬영 모드: 렌즈 앞알이 반짝인다
                if (raised && camDir != 1) {
                    val t = (sin(game.time * 6f) * 0.5f + 0.5f)
                    uiFill.color = Color.argb((38 + 26 * t).toInt(), 255, 244, 214)
                    val ex = sx + when (camDir) {
                        2 -> 25f
                        3 -> 7f
                        else -> 16f
                    }
                    c.drawCircle(ex, sy + 11.4f, 3.0f + t * 1.2f, uiFill)
                }
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
     * 낮밤 조명. 시각에 맞는 어둠(새벽 주황 -> 낮 -> 노을 -> 밤 남색)을 조명 맵으로 깔고,
     * 가로등·창문·터널 등·반딧불·플레이어 주변에 부드러운 빛 구멍을 낸 뒤 전구색 번짐을 얹는다.
     */
    private fun drawLighting(c: Canvas, camXv: Float, camYv: Float) {
        val col = ambientColor()
        val alpha = Color.alpha(col)
        if (alpha == 0) return
        val k = (alpha / 124f).coerceIn(0f, 1f)
        // 줌·기울기로 가장자리가 새지 않게 라이트맵도 PAD 만큼 크게 잡고 -PAD 위치에 덮는다.
        // 크기는 최대 줌아웃 기준으로 '고정'한다 — 매 프레임 크기가 변하면 비트맵을 새로 만들게 된다.
        val lm = LightMaps.get(LIGHT_W, LIGHT_H)
        val lw = LIGHT_W
        val lh = LIGHT_H
        val lpx = (LIGHT_W - game.virtW) / 2f     // 화면보다 큰 만큼 가운데 정렬
        val lpy = (LIGHT_H - game.virtH) / 2f
        lm.begin(col)
        c.save()
        c.translate(-lpx, -lpy)
        val lcx = camXv - lpx          // 라이트맵 좌상단에 대응하는 월드(가상px) 좌표
        val lcy = camYv - lpy

        val x0 = (lcx / 32f).toInt().coerceAtLeast(0)
        val y0 = (lcy / 32f).toInt().coerceAtLeast(0)
        val x1 = ((lcx + lw) / 32f).toInt().coerceAtMost(map.w - 1)
        val y1 = ((lcy + lh) / 32f).toInt().coerceAtMost(map.h - 1)
        val ly0 = (y0 - 2).coerceAtLeast(0)
        val ly1 = (y1 + 2).coerceAtMost(map.h - 1)
        val lx0 = (x0 - 2).coerceAtLeast(0)
        val lx1 = (x1 + 2).coerceAtMost(map.w - 1)
        val flicker = 0.94f + sin(game.time * 7.3f) * 0.03f + sin(game.time * 13.1f) * 0.03f

        // 1) 빛 구멍
        for (y in ly0..ly1) {
            for (x in lx0..lx1) {
                val sx = x * 32f - lcx
                val sy = y * 32f - lcy
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
        val px = player.cx * WORLD_SCALE - lcx
        val py = player.cy * WORLD_SCALE - lcy - 8f
        lm.light(px, py, 66f, (120 * k).toInt())
        fx.fireflies(c, lm, true, lcx, lcy, lw, lh)
        lm.end(c)
        c.restore()

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

    /** 새벽/노을: 하늘에서 내려오는 따뜻한 빛줄기 */
    private fun drawWarmShafts(c: Canvas) {
        val vw = game.virtW.toFloat()
        val vh = game.virtH.toFloat()
        val h = state.worldTime
        val warm = when {
            h >= 5.5f && h < 7.5f -> (1f - abs(h - 6.5f))          // 새벽
            h >= 17f && h < 19f -> (1f - abs(h - 18f))              // 노을
            else -> 0f
        }
        if (warm > 0.02f) {
            val a = (52f * warm).toInt().coerceIn(0, 255)
            glowFill.shader = LinearGradient(
                0f, 0f, 0f, vh * 0.72f,
                Color.argb(a, 255, 178, 96), Color.argb(0, 255, 178, 96),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, vw, vh, glowFill)
            glowFill.shader = null
        }
    }

    /** 비네트 — 화면 가장자리를 은은하게 어둡게 */
    private fun drawVignette(c: Canvas) {
        c.drawBitmap(
            game.assets.vignette, null,
            RectF(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat()),
            vignettePaint
        )
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

    /** 터널 위에 지하철 출입구처럼 번호 뱃지를 표시 — 맵별 번호와 동일 */
    private fun drawTunnelOverlays(c: Canvas) {
        if (map.tunnels.isEmpty()) return
        for (tunnel in map.tunnels) {
            val sx = (tunnel.cx - camX) * WORLD_SCALE
            val sy = (tunnel.cy - camY) * WORLD_SCALE
            if (sx < -140f || sx > game.virtW + 140f || sy < -140f || sy > game.virtH + 140f) continue
            val target = Regions.byId[tunnel.targetId]
            val targetName = target?.name ?: tunnel.targetId

            // 터널 입구 중앙보다 살짝 위 — 번호판
            val badgeCx = sx + 16f
            val badgeCy = sy - 18f

            // 그림자
            bubbleFill.color = Color.argb(80, 20, 14, 10)
            c.drawCircle(badgeCx, badgeCy + 2f, 14f, bubbleFill)

            // 노란 원 — 지하철 출입구 번호 느낌
            bubbleFill.color = 0xFFF2B63C.toInt()
            c.drawCircle(badgeCx, badgeCy, 13f, bubbleFill)
            bubbleStroke.color = 0xFF4A2E12.toInt()
            bubbleStroke.strokeWidth = 1.8f
            c.drawCircle(badgeCx, badgeCy, 13f, bubbleStroke)

            // 번호
            val np = Type.paintPx(12f, true, 0.02f, 0xFF4A2E12.toInt())
            val numTxt = tunnel.number.toString()
            val tw = np.measureText(numTxt)
            c.drawText(numTxt, badgeCx - tw / 2f, badgeCy + 4f, np)

            // 가까우면 목적지 라벨도
            val distToPlayer = hypot(tunnel.cx - player.cx, tunnel.cy - player.cy)
            if (distToPlayer < 160f) {
                val dirArrow = Regions.dirArrow(tunnel.dir)
                val label = "$dirArrow $targetName"
                val lp = Type.paintPx(10f, true, 0.01f, 0xFFF8EFDC.toInt())
                val lw = lp.measureText(label)
                uiFill.color = Color.argb(200, 58, 52, 74)
                c.drawRoundRect(RectF(badgeCx - lw / 2 - 6f, badgeCy + 12f, badgeCx + lw / 2 + 6f, badgeCy + 26f), 6f, 6f, uiFill)
                c.drawText(label, badgeCx - lw / 2, badgeCy + 21f, lp)
            }
        }
    }

    /** 광장 근처에서만 전체 출구 안내판 표시 (캐릭터 옆 터널 방향 힌트는 띄우지 않는다) */
    private fun drawExitHints(c: Canvas) {
        if (map.tunnels.isEmpty()) return
        // 광장 근처에서는 전체 출구 안내판 (지하철 출입구 종합 안내처럼)
        val plazaCx = 21f * 16f + 8f
        val plazaCy = 15f * 16f + 8f
        val distPlaza = hypot(player.cx - plazaCx, player.cy - plazaCy)
        if (distPlaza < 140f) {
            val sx = (plazaCx - camX) * WORLD_SCALE + 16f
            val sy = (plazaCy - camY) * WORLD_SCALE - 42f
            var curY = sy
            for (tunnel in map.tunnels) {
                val target = Regions.byId[tunnel.targetId] ?: continue
                val dirArrow = Regions.dirArrow(tunnel.dir)
                val dirLabel = Regions.dirLabel(tunnel.dir)
                val line = "${tunnel.number} $dirArrow $dirLabel -> ${target.name}"
                val lp = Type.paintPx(10f, true, 0.01f, 0xFFF8EFDC.toInt())
                val lw = lp.measureText(line)
                uiFill.color = Color.argb(210, 58, 52, 74)
                c.drawRoundRect(RectF(sx - lw / 2 - 8f, curY - 12f, sx + lw / 2 + 8f, curY + 2f), 6f, 6f, uiFill)
                bubbleFill.color = 0xFFF2B63C.toInt()
                c.drawCircle(sx - lw / 2 - 4f, curY - 5f, 8f, bubbleFill)
                val np = Type.paintPx(9f, true, 0.02f, 0xFF4A2E12.toInt())
                val nt = tunnel.number.toString()
                c.drawText(nt, sx - lw / 2 - 4f - np.measureText(nt) / 2, curY - 1.5f, np)
                c.drawText(line, sx - lw / 2 + 10f, curY, lp)
                curY += 18f
            }
        }
    }

    companion object {
        /** 흔들림·기울기로 화면 가장자리가 비지 않도록 한 타일만큼 더 그리는 여유분(가상 px) */
        private const val PAD = 32f

        /** 잔상용 위치 링버퍼 길이 */
        private const val GHOSTS = 6

        /** 조명 맵은 최대 줌아웃(0.9)까지 덮을 수 있게 고정 크기로 잡는다 */
        const val LIGHT_W = 1130
        const val LIGHT_H = 664
    }
    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
