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

/** 펀치가 고양이에 닿는 거리 (논리 px) */
private const val PUNCH_RANGE = 46f

/** 펀치 동작 길이(초). 이 동안은 다시 치지 않는다. */
private const val PUNCH_DUR = 0.34f

/**
 * 지역 월드 씬: 걷기/자전거/달리기, 터널 이동, 새 스폰/촬영, 낮밤, 파티클, NPC/고양이.
 * 고양이는 새를 노리고, 펀치를 맞으면 날아간다.
 */
class WorldScene(
    game: Game,
    regionId: String,
    private val spawnKind: SpawnKind = SpawnKind.SAVED,
    private val spawnDir: Dir = Dir.S
) : Scene(game) {

    private val state = game.state
    val region: RegionDef = Regions.byId[regionId] ?: Regions.ALL.first()
    // 매입한 지역마다 현관을 남긴다. homeRegion은 현재 정착지일 뿐,
    // ownedHomes에 있는 이전 집도 여행 중 다시 들어갈 수 있어야 한다.
    val map: GameMap = MapBuilder.build(region, state.homeRegion, state.ownedHomes)
    private val grass = GrassField(map)
    private val player = Player()
    private val birds = ArrayList<FieldBird>()
    private val cats = ArrayList<Cat>()
    private var catsToRespawn = 0
    private var catRespawnT = 0f
    /** 펀치 모션 경과. 0이면 치지 않는 중. */
    private var punchT = 0f
    private var punchDir = Dir.S
    private var impactT = 0f
    private var impactX = 0f
    private var impactY = 0f
    // 재사용 정렬 버퍼: 매 프레임 엔티티 목록을 새로 만들지 않아 GC 부하를 줄인다.
    private val drawEntities = ArrayList<Any>(map.npcs.size + 16)
    private val drawEntityOrder = Comparator<Any> { a, b -> sortY(a).compareTo(sortY(b)) }
    private val rnd = Random(region.id.hashCode().toLong() + 7L)
    private val viewfinder = Viewfinder(game)

    // 디테일 연출 (날씨·물·발자국·작은 생물·조명) — Fx.kt
    private val fx = WorldFx(map, region.id.hashCode().toLong() + 31L)
    private val atmosphere = CinematicAtmosphere()
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
    private var spawnTimer = 8f
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
    // [P84 이후] 고양이 간식(생선) 아이콘용 페인트 — HomeScene 의 aaFill 과 같은 역할.
    // (main 쪽에서 HomeScene private 필드를 그대로 참조해 컴파일이 깨졌던 것을 WorldScene 에 정의)
    private val aaFill = Paint(Paint.ANTI_ALIAS_FLAG)
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

    // 매 프레임 할당을 없애기 위한 공용 스크래치 — 그리기 중에만 쓰고 보관하지 않는다.
    private val scratchRect = RectF()
    private val scratchPath = Path()
    // 새벽/노을 빛줄기 그라디언트 — 높이가 변할 때만 다시 만든다.
    private var shaftShader: LinearGradient? = null
    private var shaftShaderH = -1f

    init {
        state.region = region.id
        state.inHome = false

        // 빠른 이동 도착 지점 — 보리 박사가 있는 지역이면 박사 옆(인사 자리), 아니면 중앙 광장.
        val professorHere = map.npcs.firstOrNull { it.kind == NpcKind.PROFESSOR }
        val (sx, sy) = when (spawnKind) {
            SpawnKind.SAVED -> state.px to state.py
            SpawnKind.HOME -> 376f to 12.2f * 16f
            SpawnKind.FAST -> professorHere?.let { it.greetX * 16f to it.greetY * 16f }
                ?: (18f * 16f to 14f * 16f)   // 중앙 광장
            SpawnKind.LANDMARK ->                      // 랜드마크에서 나오면 정문 바로 앞
                if (map.landmarkDoorX >= 0) map.landmarkDoorX * 16f to (map.landmarkDoorY + 1) * 16f
                else 18f * 16f to 14f * 16f
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
        player.facing = when {
            spawnKind == SpawnKind.TUNNEL -> Regions.opposite(spawnDir)
            spawnKind == SpawnKind.FAST && professorHere != null -> {
                // 박사 바로 옆에 내려놓으니, 도착하자마자 얼굴을 마주 보게 한다
                val dx = professorHere.cx - player.cx
                val dy = professorHere.cy - player.cy
                if (abs(dx) > abs(dy)) (if (dx > 0) Dir.E else Dir.W) else (if (dy > 0) Dir.S else Dir.N)
            }
            spawnKind == SpawnKind.FAST -> Dir.N      // 광장 한가운데를 바라본다
            else -> Dir.S
        }
        // 저장 위치 복귀·터널 이동 시에는 자전거 탑승 상태 유지 (터널을 지나도 내리지 않는다)
        player.bike = (spawnKind == SpawnKind.SAVED || spawnKind == SpawnKind.TUNNEL) && state.onBike

        if (region.id !in state.visited) {
            state.visited.add(region.id)
            game.hud.toast("첫 방문! ${region.name}")
        }

        spawnTimer = BirdEcology.nextInterval(state.worldTime, state.weather(), rnd)
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
        Ach.tick(this, state)
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

        // 고양이 — 새를 노리고, 펀치를 맞으면 날아간다
        updateCats(wdt)
        if (impactT > 0f) impactT -= dt

        // 새 스폰
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            trySpawnBird()
            spawnTimer = BirdEcology.nextInterval(state.worldTime, weather, rnd)
        }

        // 파티클
        updateParticles(dt)

        // 살아있는 풀 (바람 필드 + 풀잎 상태 머신 + 밟힘 반응)
        // 풀은 32px 렌더 좌표, 캐릭터는 16px 논리 좌표를 사용한다.
        grass.update(
            dt, game.time, (player.x + 8f) * WORLD_SCALE, (player.y + 13f) * WORLD_SCALE, player.bike,
            viewRig.x * WORLD_SCALE, viewRig.y * WORLD_SCALE,
            viewRig.viewW * WORLD_SCALE, viewRig.viewH * WORLD_SCALE
        )
        spawnAmbient(dt)
        // 🌸 힐링 파티클 — 작은 생물(나비/잠자리/반딧불/먼 갈매기/철새 떼) + 계절 향
        Healing.updateCritters(dt, state, region, rnd, viewRig.viewW, viewRig.viewH, viewRig.x, viewRig.y) { m ->
            game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
            game.sfx(Audio.Sfx.NOTIFY, 0.45f, 1.3f)
        }
        Healing.updateScents(dt, state.season(), player.cx, player.cy, rnd)
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
            state.activeQuests.isNotEmpty() -> {
                val q = state.activeQuests.first()
                "의뢰 · [${q.category.label}] ${q.title} (${q.progressText})"
            }
            state.questBird != null -> "서브 · ${Birds.byId[state.questBird!!]?.name ?: "?"} 사진"
            state.mainQuestFinished -> null
            state.mainQuestStarted -> MainStory.current(state)?.let { "메인 · ${it.title}" }
            else -> "메인 · ${NpcRoster.professorRegionName} 보리 박사 만나기"
        }

        // 메인 버튼 맥락 아이콘 (근처 상호작용 대상 — A 버튼 동작과 동일한 우선순위)
        game.hud.contextIcon = when {
            nearestNpc() != null -> "note"
            nearTile(T.SIGN) != null -> "map"
            nearTile(T.BENCH) != null -> "coffee"
            nearFlowerTile() != null && Healing.pickableHerbs(state.season(), region.habitats).isNotEmpty() -> "leaf"
            map.hasHouse && hypot((map.houseDoorX * 16f + 16f) - player.cx, (map.houseDoorY * 16f + 8f) - player.cy) < 30f -> "house"
            nearLandmarkDoor() -> "pin"
            nearestCat() != null -> "fist"
            else -> null
        }
        game.hud.showPunch = !photoMode
        game.hud.punchHot = nearestCat() != null
        if (!game.catPunchHintShown && game.hud.punchHot) {
            game.catPunchHintShown = true
            game.toast("고양이는 새를 노린다. 주먹 버튼으로 날려 보내자")
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
            // 🌸 작은 기념
            if (chosen == Weather.SNOW) Healing.unlock(state, "first_snow")?.let { m ->
                game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
            }
        }
        if ("coast" in region.habitats) Healing.unlock(state, "sea_breeze")
        if (state.worldTime < 5.5f) Healing.unlock(state, "quiet_morning")
        if (state.visited.size >= Regions.ALL.size) Healing.unlock(state, "all_regions")
        if (state.luck >= 99.9f) Healing.unlock(state, "lucky_100")
        if (state.decorSlots.any { it >= 0 }) Healing.unlock(state, "home_decorated")
        if (state.onBike && state.visited.size >= 3) Healing.unlock(state, "bicycle_ride")
    }

    private fun updatePlayer(dt: Float) {
        val input = game.input
        val dx = input.dirX
        val dy = input.dirY
        val moving = abs(dx) > 0.01f || abs(dy) > 0.01f
        player.moving = moving
        if (bumpCd > 0f) bumpCd -= dt
        var blocked = false
        if (punchT > 0f && !player.bike) player.facing = punchDir
        if (moving) {
            if (punchT <= 0f || player.bike) {
                if (abs(dx) > abs(dy)) player.facing = if (dx > 0) Dir.E else Dir.W
                else if (abs(dy) > 0.01f) player.facing = if (dy > 0) Dir.S else Dir.N
            }

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
            val moveStartX = player.x
            val moveStartY = player.y
            if (!moveBy(vx * speed * dt, 0f)) blocked = true
            if (!moveBy(0f, vy * speed * dt)) blocked = true
            val movedPx = hypot(player.x - moveStartX, player.y - moveStartY)
            if (movedPx > 0f) Ach.onMove(game.context, movedPx, player.bike)
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
        if (punchT > 0f) {
            punchT += dt
            if (punchT >= PUNCH_DUR) punchT = 0f
        }
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
                game.toast("배고파요… 집에서 피자를 구워 먹어요!")
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
            T.LANDMARK_DOOR -> if (map.hasLandmark) enterLandmark()
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
        state.onBike = player.bike   // 터널을 지나도 자전거 탑승 상태 유지
        SaveManager.save(game.context, state)
        if (viaSea) game.toast("해저 터널을 지나~")
        // 터널로 빨려 들어가는 느낌 — 살짝 광각으로 벌어지며 흔들린다
        viewRig.punchZoom(-0.055f)
        viewRig.shake(0.2f)
        game.sfx(Audio.Sfx.WHOOSH, 0.8f)
        game.audio.stopSteps()
        game.fadeTo {
            game.scene = WorldScene(game, targetId, SpawnKind.TUNNEL, Regions.opposite(edge))
        }
    }

    /** 이 지역의 매입한 집으로 들어간다. 나올 때도 같은 지역 현관 앞으로 돌아온다. */
    private fun enterHome() {
        state.px = player.x
        state.py = player.y
        state.region = region.id
        SaveManager.save(game.context, state)
        game.audio.stopSteps()
        game.fadeTo {
            game.scene = HomeScene(game, region.id)
        }
    }

    /** 랜드마크 정문 앞에 서 있는지 */
    private fun nearLandmarkDoor(): Boolean {
        if (!map.hasLandmark) return false
        val ddx = (map.landmarkDoorX * 16f + 16f) - player.cx
        val ddy = (map.landmarkDoorY * 16f + 8f) - player.cy
        return hypot(ddx, ddy) < 30f
    }

    private fun enterLandmark() {
        if (!map.hasLandmark) return
        state.px = player.x
        state.py = player.y
        SaveManager.save(game.context, state)
        game.audio.stopSteps()
        game.sfx(Audio.Sfx.TAP, 0.6f)
        game.fadeTo {
            game.scene = LandmarkScene(game, map.region)
        }
    }

    // -------------------------------------------------------------------
    // 새 스폰/촬영
    // -------------------------------------------------------------------

    private fun regionPool(): List<BirdDef> {
        // No opposite-time/season fallback: an empty habitat is a valid encounter outcome.
        return Birds.poolFor(map.region, state.isNight(), state.day, state.worldTime)
    }

    private fun trySpawnBird() {
        if (birds.size >= 3) return
        // Roll rarity first, independently of how many species the checklist contains.
        // Missing tiers leave a quiet interval rather than promoting a rarity to certainty.
        val tierWeights = Tier.values().associateWith {
            BirdEcology.tierMass(it) * (1.0 + state.effectiveLuck().coerceIn(0f, 100f) / 100.0 * (it.star - 1) * 0.25)
        }
        var tierRoll = rnd.nextDouble() * tierWeights.values.sum()
        val tier = Tier.values().firstOrNull {
            tierRoll -= tierWeights.getValue(it)
            tierRoll < 0.0
        } ?: Tier.COMMON
        val pool = regionPool().filter { it.tier == tier }
        if (pool.isEmpty()) return

        val currentWeather = state.weather()
        val currentSeason = state.season()
        val weights = pool.map {
            Birds.spawnWeight(it, map.region, state.day, state.worldTime) *
                    weatherBirdMultiplier(it, currentWeather) *
                    seasonBirdMultiplier(it, currentSeason, currentWeather) *
                    SpawnTables.weight(it, map.region.id, map.region.habitats, currentSeason, state.isNight()) // [P02] 계절×지역×시간대
        }
        var roll = rnd.nextDouble() * weights.sum()
        var def = pool[pool.size - 1]
        for (i in pool.indices) {
            roll -= weights[i]
            if (roll <= 0) { def = pool[i]; break }
        }

        for (i in 0 until 80) {
            val tx = 2 + rnd.nextInt(map.w - 4)
            val ty = 2 + rnd.nextInt(map.h - 4)
            val suitability = BirdEcology.suitability(def, map, tx, ty)
            if (suitability <= 0.0 || rnd.nextDouble() >= suitability) continue
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
            announceSpawn(def)
            return
        }
    }

    /**
     * 나타난 새의 등급(Tier)에 따라 알림 연출을 다르게 준다.
     * 흔함일수록 조용하게, 희귀·전설일수록 화면·소리·문구가 점점 극적으로 커진다.
     */
    private fun announceSpawn(def: BirdDef) {
        when (def.tier) {
            Tier.COMMON -> {
                // 흔한 새 — 주의를 끌지 않게 가벼운 지저귐만 (토스트 없음)
                game.sfx(Audio.Sfx.BIRD_CHIRP1, 0.45f)
            }
            Tier.UNCOMMON -> {
                // 보통 새 — 짧은 토스트와 지저귐
                game.toast("${def.name} 발견 — 등급 ${def.tier.label}")
                game.sfx(Audio.Sfx.BIRD_CHIRP2, 0.65f)
                viewRig.punchZoom(0.015f)
            }
            Tier.RARE -> {
                // 희귀새 — 숨을 죽이듯 화면이 살짝 당겨지고 알림음
                viewRig.punchZoom(0.03f)
                game.toast("조심하세요… 희귀한 ${def.name}가 나타났어요!")
                game.sfx(Audio.Sfx.NOTIFY, 0.8f)
            }
            Tier.LEGEND -> {
                // 전설 — 배너 + 반짝 + 화면 당김/떨림으로 최대한 극적으로
                viewRig.punchZoom(0.06f)
                viewRig.shake(0.28f)
                game.banner("전설의 ${def.name} 출현!")
                game.toast("전설급 ${def.name} — 절대 놓치지 마세요!")
                game.sfx(Audio.Sfx.SPARKLE, 1f)
                game.sfx(Audio.Sfx.NOTIFY, 0.9f)
            }
        }
        if (state.questBird == def.id) {
            game.toast("의뢰의 새 ${def.name} 등장!")
            // 흔함·보통이라 알림음이 약했다면 의뢰 알림음을 확실히 준다
            if (def.tier.star < 3) game.sfx(Audio.Sfx.NOTIFY, 0.7f)
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
        // 🌸 탐조 일기 카운터
        Healing.bumpToday(state, "photosToday")
        if (isNew) {
            Healing.bumpToday(state, "newBirdsToday")
            state.luck = (state.luck + 4f).coerceAtMost(100f)
        }
        if (state.isNight() && b.def.habitats.contains("forest")) {
            Healing.unlock(state, "night_owl")?.let { m ->
                game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
            }
        }
        if (state.season() == Season.WINTER && b.def.name.contains("두루미")) {
            Healing.unlock(state, "winter_crane")?.let { m ->
                game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
            }
        }

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

        // 다종 퀘스트(서식지 탐사, 3성 촬영, 야간 탐조, 비행 포착, 신규 종 발굴 등) 판정
        val isAction = movingShot || fastShot || b.fleeT > 0f || b.state == 2
        val completedQuests = QuestManager.onPhotoTaken(
            state, b.def, stars, isNew, state.isNight(), isAction
        )
        if (completedQuests.isNotEmpty()) {
            if (questLine == null) questLine = completedQuests.first()
            for (line in completedQuests) {
                game.toast(line)
            }
            game.sfx(Audio.Sfx.SPARKLE, 0.9f)
            game.sfx(Audio.Sfx.NOTIFY, 0.9f)
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
                if (!miss) game.toast("연사로 겨우 건졌어요!")
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
        val n = 1 + rnd.nextInt(2)
        repeat(n) { spawnOneCat(true) }
    }

    private fun spawnOneCat(avoidPlayer: Boolean): Boolean {
        repeat(24) {
            val tx = 2 + rnd.nextInt(map.w - 4)
            val ty = 2 + rnd.nextInt(map.h - 4)
            if (!map.walkableTile(tx, ty)) return@repeat
            val x0 = tx * 16f
            val y0 = ty * 16f
            if (avoidPlayer && hypot(x0 - player.cx, y0 - player.cy) < 110f) return@repeat
            if (cats.any { hypot(it.x - x0, it.y - y0) < 48f }) return@repeat
            if (birds.any { hypot(it.cx - (x0 + 14f), it.cy - (y0 + 12f)) < 56f }) return@repeat
            cats.add(Cat(x0, y0))
            return true
        }
        return false
    }

    private fun nearestCat(rangePx: Float = PUNCH_RANGE): Cat? {
        var best: Cat? = null
        var bestD = Float.MAX_VALUE
        for (cat in cats) {
            if (cat.launched) continue
            val d = hypot(cat.cx - player.cx, cat.cy - player.cy)
            if (d < rangePx && d < bestD) { best = cat; bestD = d }
        }
        return best
    }

    /** 고양이가 노릴 수 있는 가장 가까운 새. 이미 달아나는 새는 제외. */
    private fun preyFor(cat: Cat): FieldBird? {
        var best: FieldBird? = null
        var bestD = 88f
        for (b in birds) {
            if (b.state == 2) continue
            val d = hypot(b.cx - cat.cx, b.cy - cat.cy)
            if (d < bestD) { best = b; bestD = d }
        }
        return best
    }

    private fun updateCats(dt: Float) {
        val it = cats.iterator()
        while (it.hasNext()) {
            val cat = it.next()
            if (cat.launched) {
                cat.update(dt, map)
                if (rnd.nextFloat() < 0.65f) {
                    addParticle(
                        cat.cx, cat.cy - cat.air,
                        -cat.vx * 0.04f + (rnd.nextFloat() - 0.5f) * 16f,
                        -cat.vy * 0.04f - 8f,
                        0.28f,
                        Color.argb(150, 255, 244, 220),
                        2.6f,
                        false
                    )
                }
                if (cat.gone) {
                    it.remove()
                    catsToRespawn++
                    if (catRespawnT <= 0f) catRespawnT = 8f
                }
                continue
            }
            if (cat.calmT > 0f) {
                cat.stalking = false
                cat.pouncing = false
                cat.update(dt, map)
                continue
            }
            // 화면 밖에서 몰래 잡아먹지 않는다 — 가까이 와서 보는 위협이어야 막을 수 있다
            if (hypot(cat.cx - player.cx, cat.cy - player.cy) > 210f) {
                cat.stalking = false
                cat.pouncing = false
                cat.preyId = null
                cat.update(dt, map)
                continue
            }
            val prey = preyFor(cat)
            if (prey == null) {
                cat.stalking = false
                cat.pouncing = false
                cat.preyId = null
                cat.stuckT = 0f
                cat.update(dt, map)
                continue
            }
            cat.stalking = true
            if (cat.preyId != prey.def.id) {
                cat.preyId = prey.def.id
                if (prey.def.tier == Tier.RARE || prey.def.tier == Tier.LEGEND) {
                    game.toast("고양이가 ${obj(prey.def.name)} 노린다!")
                    game.sfx(Audio.Sfx.NOTIFY, 0.65f)
                }
            }
            when (cat.chase(prey.cx, prey.cy, dt, map)) {
                2 -> catchBird(cat, prey)
                0 -> {
                    cat.stuckT += dt
                    if (cat.stuckT > 0.7f) {
                        cat.stuckT = 0f
                        cat.stalking = false
                        cat.pouncing = false
                        cat.preyId = null
                        cat.calmT = 1.1f
                    }
                }
                else -> cat.stuckT = 0f
            }
            if (cat.pouncing && !cat.pounceCued) {
                cat.pounceCued = true
                game.sfx(Audio.Sfx.WHOOSH, 0.35f, 1.55f)
            }
        }
        if (catsToRespawn > 0) {
            catRespawnT -= dt
            if (catRespawnT <= 0f) {
                if (spawnOneCat(true)) catsToRespawn--
                catRespawnT = if (catsToRespawn > 0) 2.4f else 0f
            }
        }
    }

    private fun catchBird(cat: Cat, bird: FieldBird) {
        if (!birds.remove(bird)) return
        val name = bird.def.name
        repeat(9) {
            addParticle(
                bird.cx, bird.cy,
                (rnd.nextFloat() - 0.5f) * 46f,
                -16f - rnd.nextFloat() * 22f,
                0.75f,
                if (rnd.nextBoolean()) Color.argb(230, 248, 244, 236) else Color.argb(220, 186, 140, 86),
                3.4f,
                true
            )
        }
        cat.calmT = 3.2f
        cat.stalking = false
        cat.pouncing = false
        cat.pounceCued = false
        cat.preyId = null
        cat.state = 0
        cat.idleT = 2.4f
        game.sfx(Audio.Sfx.BIRD_FLEE, 0.8f)
        game.sfx(Audio.Sfx.FAIL, 0.32f, 0.75f)
        game.toast("고양이가 ${obj(name)} 잡아먹었다…")
        viewRig.shake(0.16f)
    }

    /** 한글 목적격 조사. 종성 없으면 를, 있으면 을. */
    private fun obj(name: String): String {
        val c = name.lastOrNull() ?: return name
        val v = c.code - 0xAC00
        if (v !in 0..11171) return "${name}를"
        return if (v % 28 == 0) "${name}를" else "${name}을"
    }

    private fun petCat(cat: Cat) {
        viewRig.kick(0f, 1f, 0.5f)
        state.luck = (state.luck + 1f).coerceAtMost(100f)
        game.sfx(Audio.Sfx.SPARKLE, 0.5f, 1.15f)
        repeat(3) {
            addParticle(
                cat.cx, cat.cy - 6f,
                (rnd.nextFloat() - 0.5f) * 10f, -12f, 1f,
                Color.argb(220, 242, 130, 160), 3.4f, true
            )
        }
        Healing.bumpToday(state, "catsPetToday")
        if (Healing.catLove(state) >= 40) Healing.unlock(state, "cat_love_40")?.let { m ->
            game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
        } else {
            val treats = Healing.catTreats(state)
            val treatMsg = if (treats > 0) " | 간식 주려면 고양이 몸통을 꾹~" else ""
            game.toast("쓰다듬었다~ 야옹 🐈 행운+1$treatMsg")
        }
    }

    private fun facingUnit(dir: Dir): Pair<Float, Float> = when (dir) {
        Dir.E -> 1f to 0f
        Dir.W -> -1f to 0f
        Dir.N -> 0f to -1f
        else -> 0f to 1f
    }

    private fun faceToward(dx: Float, dy: Float) {
        if (hypot(dx, dy) < 1f) return
        player.facing = when {
            abs(dx) >= abs(dy) && dx >= 0f -> Dir.E
            abs(dx) > abs(dy) -> Dir.W
            dy >= 0f -> Dir.S
            else -> Dir.N
        }
        punchDir = player.facing
    }

    /**
     * 펀치. 사거리 안 고양이가 있으면 펀치 방향으로 날려 보낸다.
     * preferred 를 넘기면 그 고양이를 친다 (탭). 없으면 가장 가까운 고양이.
     * 빈 주먹이면 헛스윙만 한다.
     */
    private fun tryPunch(preferred: Cat? = null) {
        if (punchT > 0f) return
        val cat = when {
            preferred != null && preferred.launched -> null
            preferred != null -> {
                val d = hypot(preferred.cx - player.cx, preferred.cy - player.cy)
                if (d > PUNCH_RANGE + 6f) {
                    game.toast("더 가까이 가서 👊")
                    return
                }
                preferred
            }
            else -> nearestCat()
        }
        val dx: Float
        val dy: Float
        if (cat != null) {
            dx = cat.cx - player.cx
            dy = cat.cy - player.cy
            faceToward(dx, dy)
        } else {
            val (fx, fy) = facingUnit(player.facing)
            dx = fx
            dy = fy
            punchDir = player.facing
        }
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = dx / len
        val ny = dy / len
        punchT = 0.001f
        if (!player.bike) {
            moveBy(nx * 5f, 0f)
            moveBy(0f, ny * 5f)
        }
        viewRig.kick(nx, ny, if (cat != null) 5.4f else 1.3f)
        viewRig.punchZoom(if (cat != null) 0.05f else 0.016f)
        if (cat == null) {
            viewRig.shake(0.08f)
            game.sfx(Audio.Sfx.WHOOSH, 0.32f, 1.2f)
            return
        }
        val saving = cat.stalking || cat.pouncing
        cat.x += nx * 3f
        cat.y += ny * 3f
        cat.launch(nx, ny, if (rnd.nextBoolean()) 1f else -1f)
        impactT = 0.3f
        impactX = cat.cx
        impactY = cat.cy
        viewRig.shake(0.72f)
        viewRig.freeze(0.08f)
        game.sfx(Audio.Sfx.WHOOSH, 0.95f, 0.82f)
        game.sfx(Audio.Sfx.TAP, 0.85f, 0.52f)
        repeat(8) {
            addParticle(
                cat.cx, cat.cy - 4f,
                nx * 40f + (rnd.nextFloat() - 0.5f) * 56f,
                ny * 24f - 18f - rnd.nextFloat() * 28f,
                0.48f,
                if (it % 2 == 0) Color.argb(235, 255, 228, 96) else Color.argb(230, 255, 250, 236),
                3.6f,
                false
            )
        }
        if (saving) {
            state.luck = (state.luck + 1f).coerceAtMost(100f)
            game.sfx(Audio.Sfx.SUCCESS, 0.7f)
            game.toast("새를 지켰다! 냥—!!  ☘️+1")
        } else {
            game.toast("펀치! 냥—!!")
        }
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
            if (target != null) game.toast("${directionName(signDirection(tx, ty))} 터널 · ${target.name}")
        }
    }

    private fun restAtBench() {
        state.luck = (state.luck + 2f).coerceAtMost(100f)
        Healing.unlock(state, "bench_sunset")?.let { m ->
            game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward + 2}")
        } ?: game.toast("벤치에 앉아 쉬었다~ 구름 구경 ☘️+2")
        game.sfx(Audio.Sfx.SPARKLE, 0.55f)
        // 주변 풍경 파티클 추가로 띄워주기
        repeat(6) {
            addParticle(viewRig.x + rnd.nextFloat()*viewRig.viewW, viewRig.y - 6f,
                (rnd.nextFloat()-0.5f)*12f, 8f + rnd.nextFloat()*6f, 5f,
                Color.argb(160, 255, 236, 180), 3f, true)
        }
        if (state.isNight()) Healing.unlock(state, "full_moon")
        if (state.season() == Season.SPRING) Healing.unlock(state, "spring_picnic")
        if (state.season() == Season.AUTUMN) Healing.unlock(state, "autumn_maple")
        if (state.weather() == Weather.RAIN) Healing.unlock(state, "rain_walk")
        // 벤치 옆에 고양이가 있으면 벤치 위 고양이 기념
        if (nearestCat() != null) Healing.unlock(state, "bench_cat")
    }

    /** 플레이어 발밑 FLOWER 타일 — 허브를 주울 수 있는지 */
    private fun nearFlowerTile(): Pair<Int, Int>? {
        val ptx = (player.cx / 16f).toInt()
        val pty = ((player.y + 13f) / 16f).toInt()
        for (dy in -1..1) for (dx in -1..1) {
            val x = ptx + dx; val y = pty + dy
            if (map.groundAt(x, y) == T.FLOWER) return x to y
        }
        return null
    }

    /** 지금 주울 수 있는 허브 중 무작위 하나 (지역·계절 맞춤). 3초에 한 번만 주워지게 확률로 제한. */
    private var lastHerbPick = -999f
    private fun pickNearHerb(): Healing.Herb? {
        if (game.time - lastHerbPick < 1.2f) return null
        val here = nearFlowerTile() ?: return null
        val options = Healing.pickableHerbs(state.season(), region.habitats)
        if (options.isEmpty()) return null
        // 55% 확률로 성공 (매번 주울 수 있으면 허브가 남아나지 않아)
        if (rnd.nextFloat() > 0.55f) return null
        lastHerbPick = game.time
        return options[rnd.nextInt(options.size)]
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
        when {
            npc.person.isQuestGiver -> talkProfessor()
            npc.person.isShop -> talkShop()
            else -> talkNeighbor(npc)
        }
    }

    /**
     * 동네 사람 잡담 — 이 지역, 이 자리에서만 하는 말이다.
     *
     *  - 이웃 주민(`resident`)은 [P08] 조건부 대사(지역 소개 + 장별 관찰 예절 + 계절/날씨/밤)
     *    에 그 사람 자신의 한마디를 잇는다.
     *  - 고유 캐릭터는 자기 자리(호숫가 데크·갈대밭·시장 골목…)에 어울리는 이야기를 한다.
     *  - 보고할 메인 기록이 있는데 보리 박사가 다른 지역에 있으면 🚲 이동 택지를 붙여 준다
     *    (박사는 광릉숲에만 산다 — `NpcRoster`).
     */
    private fun talkNeighbor(npc: Npc) {
        val person = npc.person
        val own = person.lines[rnd.nextInt(person.lines.size)]
        val text = if (person.resident) {
            "${Dialogues.villager(SideStories.ctx(this))}\n\n\"$own\""
        } else {
            "\"$own\""
        }
        val choices = buildList {
            add(DialogOverlay.Choice(if (person.resident) "기억할게요" else "고마워요"))
            if (professorTripNeeded()) {
                add(
                    DialogOverlay.Choice("🚲 ${NpcRoster.professorRegionName} 박사에게") {
                        game.toast("🚲 ${NpcRoster.professorRegionName} ${NpcRoster.professor.spot.label}로 출발!")
                        fastTravel(game, NpcRoster.PROFESSOR_REGION)
                    }
                )
            }
        }
        openOverlay(
            DialogOverlay(
                this,
                if (person.title.isEmpty()) npc.name else "${npc.name} · ${person.title}",
                text,
                choices
            )
        )
    }

    /** 메인 이야기를 보고(또는 시작)해야 하는데 보리 박사가 다른 지역에 있는가 */
    private fun professorTripNeeded(): Boolean {
        if (state.mainQuestFinished) return false
        if (NpcRoster.hasProfessor(state.region)) return false
        if (!state.mainQuestStarted) return true
        return MainStory.current(state)?.isComplete(state) == true
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
        // [P08+] 계절·날씨·밤에 따른 보리 박사의 한 마디 — 반복 대화가 매번 다르게 느껴지도록
        val flavor = Dialogues.professorFlavor(
            Dialogues.Ctx(map.region.id, state.mainQuestStage, state.season(), weather, state.isNight(), state.day)
        )
        openOverlay(
            DialogOverlay(
                this, "보리 박사 · ${NpcRoster.professor.title}",
                when {
                    state.mainQuestFinished -> "\"우리의 지도는 완성됐지만 새들의 계절은 계속되지. 사진 의뢰도, 도장 깨기도 언제든 찾아오게.\""
                    !state.mainQuestStarted -> "\"마침 잘 왔네. 자네 가족이 남긴 낡은 탐조 수첩에 관한 이야기가 있어. 물론 급한 일은 아니니 사진 의뢰부터 해도 좋고.\""
                    else -> "\"메인 기록과 사진 의뢰는 서로 별개일세. 마음 가는 순서대로 천천히 하게.\"" + flavor
                } + "\n\n(나는 늘 ${NpcRoster.professorRegionName} ${NpcRoster.professor.spot.label}에 있네. 보고할 일이 있으면 여기까지 와 주게.)",
                buildList {
                    add(DialogOverlay.Choice(mainLabel) { showMainStory() })
                    add(DialogOverlay.Choice(sideLabel) { showSideQuest() })
                    if (!NpcRoster.hasShop(state.region)) {
                        add(DialogOverlay.Choice("🏬 ${NpcRoster.shopRegionName} 상점") {
                            game.toast("🚲 ${NpcRoster.shopTravelHint}")
                            fastTravel(game, NpcRoster.SHOP_REGION)
                        })
                    }
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
            if (adv.alreadyThere) "\n${adv.tip}" else "\n추천 장소: ${adv.regionName}"
        } ?: ""
        openOverlay(
            DialogOverlay(
                this, chapter.title,
                "\"${chapter.intro}\"\n\n목표: $objective" +
                    (if (ready) "\n기록을 정리할 준비가 됐어요." else "") + adviceLine,
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
                            add(DialogOverlay.Choice("이동하기") { fastTravel(game, adv.regionId) })
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

    /**
     * 진행 중 의뢰 칩(HUD 좌상단)을 눌러 의뢰 내용을 다시 읽어 본다.
     * 보상 지급·수락 없이 열람만 한다. 칩 표시 우선순위(의뢰 게시판 → 서브 사진 → 메인)를 그대로 따른다.
     */
    private fun showQuestLog() {
        if (photoMode) return
        // 1) 진행 중인 탐조 의뢰(게시판) — 칩이 가장 먼저 보여 주는 내용
        val active = state.activeQuests
        if (active.isNotEmpty()) {
            val body = active.joinToString("\n\n") { q ->
                "[${q.category.label}] ${q.title} (${q.progressText})\n${q.description}\n" +
                    "보상 ${won(q.rewardMoney)} · 경험치 +${q.rewardExp}" +
                    if (q.rewardLuck > 0) " · 행운 +${q.rewardLuck}" else ""
            }
            openOverlay(
                DialogOverlay(
                    this, "진행 중인 탐조 의뢰 (${active.size}/3)",
                    body,
                    listOf(DialogOverlay.Choice("계속할게요"))
                )
            )
            return
        }
        val questBird = state.questBird
        if (questBird != null) {
            val def = Birds.byId[questBird]
            openOverlay(
                DialogOverlay(
                    this, "진행 중인 사진 의뢰",
                    "\"${def?.name ?: "그 새"} 사진을 찍어 오게.\n보수는 ${won(state.questReward)}일세.\"\n\n" +
                        "메인 이야기와는 별개의 의뢰예요. 시간 제한은 없으니 원하는 때에 담아 오면 됩니다.",
                    listOf(DialogOverlay.Choice("계속할게요"))
                )
            )
            return
        }
        if (state.mainQuestFinished) {
            openOverlay(
                DialogOverlay(
                    this, "메인 이야기 (완료)",
                    "‘함께 사는 새 지도’를 모두 완성했어요. 이제 사진 의뢰와 도장 깨기를 자유롭게 즐겨 보세요.",
                    listOf(DialogOverlay.Choice("좋아요"))
                )
            )
            return
        }
        if (!state.mainQuestStarted) {
            openOverlay(
                DialogOverlay(
                    this, "메인 이야기 시작",
                    "${NpcRoster.professorRegionName}의 보리 박사를 찾아가 낡은 탐조 수첩 이야기를 들어보세요.",
                    listOf(DialogOverlay.Choice("알겠어요"))
                )
            )
            return
        }
        val chapter = MainStory.current(state) ?: return
        val objective = chapter.objective(state)
        val ready = chapter.isComplete(state)
        val advice = MainQuestAdvisor.advise(state)
        val adviceLine = advice?.let { adv ->
            if (adv.alreadyThere) "\n${adv.tip}" else "\n추천 장소: ${adv.regionName}"
        } ?: ""
        openOverlay(
            DialogOverlay(
                this, chapter.title,
                "\"${chapter.intro}\"\n\n목표: $objective" +
                    (if (ready) "\n기록을 정리할 준비가 됐어요. 보리 박사를 찾아가 보고하세요." else "") + adviceLine,
                buildList {
                    advice?.let { adv ->
                        if (!adv.alreadyThere && adv.regionId != state.region) {
                            add(DialogOverlay.Choice("이동하기") { fastTravel(game, adv.regionId) })
                        }
                    }
                    add(DialogOverlay.Choice("닫기"))
                }
            )
        )
    }

    /** 다양한 퀘스트 종류(지정 촬영, 서식지 탐사, 3성 촬영, 야간 탐조 등)를 선택할 수 있는 탐조 의뢰 게시판 */
    private fun showSideQuest() {
        QuestManager.ensureDailyQuests(state)
        val active = state.activeQuests
        if (active.size >= 3) {
            val lines = active.mapIndexed { idx, q ->
                "${idx + 1}. [${q.category.label}] ${q.title} (${q.progressText})"
            }.joinToString("\n")
            openOverlay(
                DialogOverlay(
                    this, "진행 중인 탐조 의뢰 (3/3)",
                    "\"현재 진행 중인 의뢰가 가득 찼네(3/3):\n$lines\n의뢰를 완료하거나 포기한 뒤 새 의뢰를 받아보게나.\"",
                    listOf(
                        DialogOverlay.Choice("계속할게요"),
                        DialogOverlay.Choice("첫 번째 의뢰 포기") {
                            val removed = state.activeQuests.removeAt(0)
                            if (removed.category == QuestCategory.BIRD_SPECIES && state.questBird == removed.targetKey) {
                                state.questBird = null
                                state.questReward = 0
                            }
                            SaveManager.save(game.context, state)
                            game.toast("[${removed.category.label}] ${removed.title} 의뢰를 포기했어요.")
                        },
                        DialogOverlay.Choice("모든 의뢰 포기") {
                            state.activeQuests.clear()
                            state.questBird = null
                            state.questReward = 0
                            SaveManager.save(game.context, state)
                            game.toast("진행 중인 모든 의뢰를 포기했어요.")
                        }
                    )
                )
            )
            return
        }

        // 새 의뢰 목록 생성 (서로 다른 4가지 종류)
        val candidates = QuestManager.generateBoardQuests(state, 4)
        val statusText = if (active.isNotEmpty()) {
            "현재 진행 중: ${active.size}/3개\n" + active.joinToString(", ") { "[${it.category.label}] ${it.title}" } + "\n\n"
        } else ""

        val choices = ArrayList<DialogOverlay.Choice>()
        for (q in candidates) {
            val label = "[${q.category.label}] ${q.title} (+₩${won(q.rewardMoney)})"
            choices.add(DialogOverlay.Choice(label) {
                state.activeQuests.add(q)
                if (q.category == QuestCategory.BIRD_SPECIES) {
                    state.questBird = q.targetKey
                    state.questReward = q.rewardMoney
                }
                SaveManager.save(game.context, state)
                game.toast("의뢰 수락: [${q.category.label}] ${q.title}")
                game.sfx(Audio.Sfx.NOTIFY, 0.8f)
            })
        }
        if (active.isNotEmpty()) {
            choices.add(DialogOverlay.Choice("진행 중인 의뢰 포기하기") {
                state.activeQuests.clear()
                state.questBird = null
                state.questReward = 0
                SaveManager.save(game.context, state)
                game.toast("진행 중인 의뢰를 정리했어요.")
            })
        }
        choices.add(DialogOverlay.Choice("다음에 할게요"))

        openOverlay(
            DialogOverlay(
                this, "보리 박사의 탐조 의뢰 게시판",
                "\"${statusText}탐조 협회와 지역 주민들이 맡긴 다양한 의뢰가 들어와 있네.\n원하는 조사를 골라 보게나! (최대 3개 동시 진행 가능)\"",
                choices
            )
        )
    }

    private fun talkShop() {
        val rig = state.rig()
        val lines = listOf(
            "\"어서 와! 지금 장비는 ${rig.title},\n환산 ${rig.teleMm}mm에 촬영 반경 ${rig.reach.fmt1()}칸이구먼.\n바디랑 렌즈는 따로 팔아. 천천히 골라 봐.\"",
            "\"새를 크게 찍고 싶으면 답은 하나야. 초점거리!\n다만 무거운 렌즈는 배가 금방 고파진다네.\"",
            "\"센서가 크면 어두운 새벽에도 깨끗하지.\n대신 지갑이 어두워지지만 말이야. 허허.\"",
            "\"허허, 내 첫 손님이 카메라를 들던 소년이었다네.\n피자 한 판 시키면서 숲새 얘기를 하던 게 어제 같은데.\"",
            "\"비 오는 날엔 렌즈에 물방울이 맺히기 쉽다네.\n레인 커버 하나가 오래 보는 비결이야.\"",
            "\"카메라는 어깨에 매는 거지만, 기록은 가슴에 남는 법이야.\n무거운 건 어깨에, 가벼운 건 가슴에 두고 다니게.\""
        )
        openOverlay(
            DialogOverlay(
                this, "사진용품점 · ${NpcRoster.shopkeeper.title}",
                lines[rnd.nextInt(lines.size)] + "\n\n(이 가게는 ${NpcRoster.shopRegionName} ${NpcRoster.shopkeeper.spot.label}에 하나뿐이야. 장비는 여기서만 살 수 있어.)",
                listOf(
                    DialogOverlay.Choice("카메라 진열대") {
                        it.scene.openOverlay(CameraShopOverlay(it.scene))
                    },
                    DialogOverlay.Choice("장비 가방(조립)") {
                        it.scene.openOverlay(GearBagOverlay(it.scene))
                    },
                    DialogOverlay.Choice("자전거 상점") {
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
        if (input.justQuest) {
            showQuestLog()
            return
        }
        if (input.justEat) {
            quickEat()
            return
        }
        if (input.justPunch) {
            tryPunch()
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
                if (player.bike) "${state.bike().name} 탔다! 쌩~"
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
                    game.toast("${directionName(signDirection(sx, sy))} 터널 · ${target.name}")
                    return
                }
            }
            if (nearTile(T.BENCH) != null) {
                restAtBench()
                return
            }
            // 🌸 허브 줍기: FLOWER 타일 밟은 채로 A를 누르면 계절 허브 하나를 주운다
            pickNearHerb()?.let { herb ->
                Healing.addHerb(state, herb.id)
                Healing.bumpToday(state, "herbsPickedToday")
                game.sfx(Audio.Sfx.SPARKLE, 0.4f, 1.4f)
                game.toast("${herb.emoji} ${herb.name}을(를) 주웠다 — ${herb.note}")
                Healing.unlock(state, "ten_herbs")?.let { m ->
                    game.toast("${m.emoji} ${m.line} ☘️+${m.luckReward}")
                }
                repeat(5) {
                    addParticle(player.cx + (rnd.nextFloat()-0.5f)*8f, player.cy - 8f,
                        (rnd.nextFloat()-0.5f)*14f, -20f - rnd.nextFloat()*10f, 1.6f,
                        herb.color, 3f, true)
                }
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
            if (nearLandmarkDoor()) {
                enterLandmark()
                return
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
            // 고양이 탭 — 하트는 쓰다듬기, 몸/주먹은 펀치, 간식이 있으면 길게 탭으로 주기
            for (cat in cats) {
                if (cat.launched) continue
                val near = hypot(cat.cx - player.cx, cat.cy - player.cy) <= PUNCH_RANGE + 6f
                val heartX = cat.x - 1f
                val heartY = cat.y - 3f - cat.lift
                val fistX = cat.x + 8f
                val fistY = cat.y - 7f - cat.lift
                if (near && !cat.stalking && hypot(heartX - tap.x, heartY - tap.y) < 8f) {
                    petCat(cat)
                    return
                }
                val fishX = cat.x - 8f
                val fishY = cat.y - 7f - cat.lift
                val onFish = Healing.catTreats(state) > 0 && hypot(fishX - tap.x, fishY - tap.y) < 10f
                if (onFish) {
                    if (Healing.feedCat(state)) {
                        game.sfx(Audio.Sfx.SPARKLE, 0.6f, 1.2f)
                        repeat(6) {
                            addParticle(cat.cx + (rnd.nextFloat()-0.5f)*6f, cat.cy - 4f,
                                (rnd.nextFloat()-0.5f)*18f, -22f - rnd.nextFloat()*10f, 1.2f,
                                Color.argb(230, 255, 180, 120), 3f, true)
                        }
                        val follow = when (Healing.catFollowLevel(state)) {
                            2 -> "이제 내 뒤를 졸졸 따라올 것만 같다."
                            1 -> "꼬리가 하늘로 올라갔다."
                            else -> "간식을 받아먹고 야옹~"
                        }
                        game.toast("🐈 생선 간식 냠! $follow")
                    }
                    return
                }
                val onBody = hypot(cat.cx - tap.x, cat.cy - tap.y) < 16f
                val onFist = hypot(fistX - tap.x, fistY - tap.y) < 10f
                if (onBody || onFist) {
                    tryPunch(cat)
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
            game.toast("피자가 없어요!")
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
        for (e in drawEntities) {
            if (e is Cat && e.launched) continue
            drawEntity(c, e)
        }

        // 살아있는 풀 — 지면에 고정된 전경으로 발목을 가린다. 엔티티에 풀을 붙여 그리지 않는다.
        c.save()
        c.translate(-padX, -padY)
        grass.draw(c, game.assets, camXv - padX, camYv - padY, padW.toFloat(), padH.toFloat(), feetY, GrassField.LAYER_FRONT)
        c.restore()
        // 날아가는 고양이는 풀 위를 지난다
        for (cat in cats) if (cat.launched) drawEntity(c, cat)

        drawParticles(c, camXv, camYv)
        c.save()
        c.translate(-padX, -padY)
        fx.drawAir(c, camXv - padX, camYv - padY, padW, padH)
        c.restore()
        drawLighting(c, camXv, camYv)
        drawWarmShafts(c)
        drawVignette(c)
        drawNpcOverlays(c)
        drawCatOverlays(c)
        drawImpact(c)
        drawTunnelOverlays(c)
        c.restore()

        // ---- 스크린 패스: 날씨 · 속도 연출 · 심도 · 뷰파인더 (UI는 흔들지 않는다) ----
        seasonFx.draw(c, state.season(), game.virtW.toFloat(), game.virtH.toFloat())
        fx.drawWeather(c)
        atmosphere.draw(c, vw, vh, hour, weather, game.time)
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
            a.drawPlayer(c, bmp, gx, gy, game.worldScale.toFloat(), alpha)
        }
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
                // 사람마다 옷차림이 다르고, 대기 동작 위상도 어긋나게 한다
                val bmp = a.npcBitmap(e.person, game.time, e.tileX * 0.37f + e.tileY * 0.71f, game.hdSprites)
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE
                scratchRect.set(sx + 8f, sy + 26f, sx + 24f, sy + 32f)
                c.drawOval(scratchRect, a.shadowPaint)
                a.drawPlayer(c, bmp, sx, sy, game.worldScale.toFloat())
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
                } else if (SideStories.hasMarker(game.context, map.region.id, e.person)) {
                    // [P08] 사이드 스토리 대기/진행 중인 NPC 머리 위 💬 마커 (에피소드 완료 시 사라짐)
                    val bx = sx + 16f
                    val by = sy - 12f
                    bubbleFill.color = 0xFF8FC7F0.toInt()
                    c.drawCircle(bx, by, 9f, bubbleFill)
                    c.drawCircle(bx, by, 9f, bubbleStroke)
                    UiKit.iconCenter(c, game, "note", bx, by, 14f)
                }
            }
            is Cat -> {
                val bmp = a.catBitmap(e.walking, e.phase, e.faceLeft)
                val sx = (e.x - camX) * WORLD_SCALE
                val sy = (e.y - camY) * WORLD_SCALE - e.lift * WORLD_SCALE - e.air * WORLD_SCALE
                val fade = e.fade
                val shadowK = if (e.launched) (1f - e.air / 42f).coerceIn(0.18f, 1f) * fade else 1f
                val shadowA = a.shadowPaint.alpha
                a.shadowPaint.alpha = (shadowA * shadowK).toInt().coerceIn(0, 255)
                val groundY = (e.cy - camY) * WORLD_SCALE
                val sh = 8f * shadowK.coerceAtLeast(0.35f)
                scratchRect.set(sx + 16f - sh, groundY + 4f, sx + 16f + sh, groundY + 10f)
                c.drawOval(scratchRect, a.shadowPaint)
                a.shadowPaint.alpha = shadowA
                val oldA = a.sprPaint.alpha
                a.sprPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                if (e.launched) {
                    val cxp = sx + bmp.width / 2f
                    val cyp = sy + bmp.height / 2f
                    c.save()
                    c.rotate(e.spin, cxp, cyp)
                    val z = 1f + e.air / 78f
                    c.scale(z, z, cxp, cyp)
                    c.drawBitmap(bmp, sx, sy, a.sprPaint)
                    c.restore()
                    val sp = hypot(e.vx, e.vy).coerceAtLeast(1f)
                    uiStroke.color = Color.argb((140 * fade).toInt(), 255, 248, 230)
                    uiStroke.strokeWidth = 1.6f
                    val bx = sx + bmp.width / 2f
                    val by = sy + bmp.height / 2f
                    val lx = -e.vx / sp * 16f
                    val ly = -e.vy / sp * 16f
                    c.drawLine(bx + lx * 0.4f, by + ly * 0.4f, bx + lx, by + ly, uiStroke)
                    c.drawLine(bx + lx * 0.2f + 4f, by + ly * 0.2f, bx + lx * 0.8f + 4f, by + ly * 0.7f, uiStroke)
                    if (e.launchT < 0.55f) {
                        tinyPaint.textSize = 14f
                        tinyPaint.color = Color.argb((230 * fade).toInt(), 255, 246, 224)
                        c.drawText("냥!", sx + 4f, sy - 6f, tinyPaint)
                        tinyPaint.color = 0xFF4A3728.toInt()
                    }
                } else {
                    c.drawBitmap(bmp, sx, sy, a.sprPaint)
                }
                a.sprPaint.alpha = oldA
                // 밤에 웅크린 고양이는 쿨쿨
                if (e.state == 0 && !e.launched && !e.stalking && state.isNight()) {
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
                    scratchRect.set(
                        shadowCx - shadowHalf, groundY - 2f,
                        shadowCx + shadowHalf, groundY + 4f
                    )
                    c.drawOval(scratchRect, a.shadowPaint)
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
                val punching = punchT > 0f && !player.bike
                val hd = game.hdSprites
                val set = a.playerSet(state.gender, state.gearTier(), hd)
                val bmp: android.graphics.Bitmap = if (player.bike) {
                    a.bikeBitmap(state.gender, state.gearTier(), player.facing, player.pedal, state.bikeStyle(), hd)
                } else if (punching) {
                    val frame = ((punchT / PUNCH_DUR) * set.punch.count).toInt().coerceIn(0, set.punch.count - 1)
                    set.punch.frame(punchDir, frame)
                } else {
                    set.clip(player.anim).frame(player.facing, player.frame)
                }
                var sx = (player.x - camX) * WORLD_SCALE
                var sy = (player.y - camY) * WORLD_SCALE
                if (punchT > 0f) {
                    val u = (punchT / PUNCH_DUR).coerceIn(0f, 1f)
                    val lk = if (u < 0.42f) u / 0.42f else (1f - (u - 0.42f) / 0.58f).coerceAtLeast(0f)
                    val (lx, ly) = facingUnit(punchDir)
                    sx += lx * lk * 6f
                    sy += ly * lk * 6f
                }
                // 뛰거나 페달을 밟을 때 그림자도 함께 호흡한다
                val k = when {
                    player.bike -> 1f - 0.06f * sin(player.pedal * 6.2832f)
                    player.anim == Anim.RUN -> 1f - 0.16f * abs(sin(player.phase * 6.2832f))
                    player.anim == Anim.WALK -> 1f - 0.07f * abs(sin(player.phase * 6.2832f))
                    else -> 1f
                }
                val half = 10f * k
                scratchRect.set(sx + 16f - half, sy + 25f - 1f * k, sx + 16f + half, sy + 31f + 1f * k)
                c.drawOval(scratchRect, a.shadowPaint)
                drawGhosts(c, bmp)
                a.drawPlayer(c, bmp, sx, sy, game.worldScale.toFloat())
                if (punchT > 0f) drawPunchFist(c, sx, sy)

                // 장착한 카메라를 몸에 겹쳐 그린다 (촬영 모드면 눈높이로 들어올린다)
                // 펀치 중에는 주먹이 가려지지 않게 카메라를 잠시 내린다 (자전거는 그대로)
                if (punchT <= 0f || player.bike) {
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
                    a.drawPlayer(c, a.camHeld(look, camDir, raised, hd), sx, sy + bob + lift + ride, game.worldScale.toFloat())

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
    }

    /** 펀치 타점의 별 폭발 */
    private fun drawImpact(c: Canvas) {
        if (impactT <= 0f) return
        val k = (impactT / 0.3f).coerceIn(0f, 1f)
        val ix = (impactX - camX) * WORLD_SCALE
        val iy = (impactY - camY) * WORLD_SCALE - 8f
        val r = 5f + (1f - k) * 20f
        uiStroke.color = Color.argb((210 * k).toInt(), 255, 244, 210)
        uiStroke.strokeWidth = 2f
        c.drawCircle(ix, iy, r, uiStroke)
        uiStroke.color = Color.argb((255 * k).toInt(), 255, 214, 72)
        uiStroke.strokeWidth = 2.4f
        c.save()
        c.translate(ix, iy)
        c.rotate((1f - k) * 50f)
        repeat(6) {
            c.drawLine(3f, 0f, r + 4f, 0f, uiStroke)
            c.rotate(60f)
        }
        c.restore()
        tinyPaint.textSize = 13f + (1f - k) * 5f
        tinyPaint.color = Color.argb((255 * k).toInt(), 255, 248, 230)
        val label = "팍!"
        c.drawText(label, ix - tinyPaint.measureText(label) / 2f, iy - r - 2f, tinyPaint)
        tinyPaint.color = 0xFF4A3728.toInt()
    }

    /** 주먹이 앞으로 뻗는 연출. 정면/뒷면 포즈만으로는 펀치가 잘 안 보여서 따로 그린다. */
    private fun drawPunchFist(c: Canvas, sx: Float, sy: Float) {
        val u = (punchT / PUNCH_DUR).coerceIn(0f, 1f)
        val (dx, dy) = facingUnit(punchDir)
        val reach = when {
            u < 0.16f -> -2f
            u < 0.40f -> -2f + (u - 0.16f) / 0.24f * 22f
            else -> 20f * (1f - ((u - 0.40f) / 0.60f).coerceIn(0f, 1f))
        }
        val alpha = (255 * (1f - (u - 0.78f).coerceAtLeast(0f) / 0.22f)).toInt().coerceIn(0, 255)
        if (alpha < 12) return
        val fx = sx + 16f + dx * reach
        val fy = sy + 14f + dy * reach
        uiFill.color = Color.argb((alpha * 0.4f).toInt(), 40, 28, 20)
        c.drawCircle(fx + 1.2f, fy + 1.6f, 6.2f, uiFill)
        uiFill.color = Color.argb(alpha, 92, 58, 42)
        c.drawCircle(fx, fy, 5.6f, uiFill)
        uiFill.color = Color.argb(alpha, 244, 198, 164)
        c.drawCircle(fx - dx * 0.6f, fy - dy * 0.6f, 4.5f, uiFill)
        uiFill.color = Color.argb(alpha, 226, 160, 128)
        val px = -dy
        val py = dx
        c.drawCircle(fx + px * 2.3f - dx * 1.6f, fy + py * 2.3f - dy * 1.6f, 1.5f, uiFill)
        c.drawCircle(fx - dx * 2f, fy - dy * 2f, 1.5f, uiFill)
        c.drawCircle(fx - px * 2.3f - dx * 1.6f, fy - py * 2.3f - dy * 1.6f, 1.4f, uiFill)
    }

    /** 사거리 안 고양이 위의 펀치/쓰다듬기 표식, 사냥 중 !! */
    private fun drawCatOverlays(c: Canvas) {
        for (cat in cats) {
            if (cat.launched) continue
            val sx = (cat.x - camX) * WORLD_SCALE
            val sy = (cat.y - camY) * WORLD_SCALE - cat.lift * WORLD_SCALE
            if (sx < -40f || sx > game.virtW + 40f || sy < -40f || sy > game.virtH + 40f) continue
            if (cat.stalking) {
                val pulse = 0.65f + 0.35f * sin(game.time * 8f)
                tinyPaint.textSize = 12f + pulse * 3f
                tinyPaint.color = Color.argb((230 * pulse).toInt(), 226, 58, 48)
                val mark = if (cat.pouncing) "냥!" else "!!"
                c.drawText(mark, sx + 20f, sy - 2f, tinyPaint)
                tinyPaint.color = 0xFF4A3728.toInt()
            }
            val near = hypot(cat.cx - player.cx, cat.cy - player.cy) <= PUNCH_RANGE
            if (!near) continue
            val bx = sx + 16f
            val by = sy - 14f + sin(game.time * 4f) * 1.2f
            bubbleFill.color = 0xFFE2574C.toInt()
            c.drawCircle(bx, by, 8f, bubbleFill)
            c.drawCircle(bx, by, 8f, bubbleStroke)
            UiKit.iconCenter(c, game, "fist", bx, by, 12f)
            if (!cat.stalking) {
                val hx = sx - 2f
                val hy = sy - 6f
                bubbleFill.color = 0xFFE25B78.toInt()
                c.drawCircle(hx - 2.4f, hy - 1.2f, 3.1f, bubbleFill)
                c.drawCircle(hx + 2.4f, hy - 1.2f, 3.1f, bubbleFill)
                val heart = Path()
                heart.moveTo(hx - 5.2f, hy - 0.2f)
                heart.lineTo(hx, hy + 5.4f)
                heart.lineTo(hx + 5.2f, hy - 0.2f)
                heart.close()
                c.drawPath(heart, bubbleFill)
                c.drawCircle(hx - 2.4f, hy - 1.2f, 3.1f, bubbleStroke)
                c.drawCircle(hx + 2.4f, hy - 1.2f, 3.1f, bubbleStroke)
            }
            // 간식 버튼 — 생선 간식이 있을 때만 고양이 왼쪽 위에 표시 (작은 물고기 그림)
            if (Healing.catTreats(state) > 0 && !cat.stalking) {
                val fx = sx - 16f
                val fy = sy - 14f + sin(game.time * 4f + 1.3f) * 1.2f
                bubbleFill.color = 0xFF6BAE75.toInt()
                c.drawCircle(fx, fy, 8f, bubbleFill)
                c.drawCircle(fx, fy, 8f, bubbleStroke)
                aaFill.color = 0xFFFEF8E6.toInt()
                aaFill.style = Paint.Style.FILL
                // 물고기 몸 + 꼬리
                c.drawOval(fx - 5f, fy - 2.5f, fx + 4f, fy + 2.5f, aaFill)
                val tail = Path()
                tail.moveTo(fx + 3f, fy)
                tail.lineTo(fx + 7f, fy - 4f)
                tail.lineTo(fx + 7f, fy + 4f)
                tail.close()
                c.drawPath(tail, aaFill)
                aaFill.color = 0xFF3A3530.toInt()
                c.drawCircle(fx - 3f, fy - 0.8f, 0.9f, aaFill)
                aaFill.style = Paint.Style.FILL
            }
            tinyPaint.color = 0xFF4A3728.toInt()
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
            scratchRect.set(cxw - camXv - w / 2f, cyw - camYv - 60f, cxw - camXv + w / 2f, cyw - camYv + 60f)
            c.drawOval(scratchRect, cloudPaint)
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
        // 🌸 힐링 파티클 — 계절 향 + 작은 생물 (모두 월드좌표 → 렌더좌표 스케일)
        c.save()
        c.translate(-camXv, -camYv)
        c.scale(WORLD_SCALE, WORLD_SCALE)
        Healing.drawScents(c, aaFill)
        Healing.drawCritters(c, aaFill)
        c.restore()
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
                    T.HOUSE_WIN, T.BLDG_WIN, T.WALL_WIN, T.LM_WIN -> lm.light(sx + 16f, sy + 20f, 40f, 34f, (205 * k).toInt())
                    T.LANDMARK_DOOR -> lm.light(sx + 16f, sy + 18f, 26f, 30f, (180 * k).toInt())
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
                    T.HOUSE_WIN, T.BLDG_WIN, T.WALL_WIN, T.LM_WIN -> {
                        uiFill.color = Color.argb((110 * k).toInt(), 255, 206, 120)
                        c.drawRect(sx + 8f, sy + 8f, sx + 24f, sy + 22f, uiFill)
                        Glow.draw(c, Glow.warm, sx + 16f, sy + 16f, 24f, 20f, (90 * k).toInt())
                    }
                    T.LANDMARK_DOOR -> Glow.draw(c, Glow.warm, sx + 16f, sy + 16f, 18f, 22f, (120 * k * flicker).toInt())
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
            // 그라디언트는 한 번만 만들고, 밝기는 페인트 알파로 조절한다 —
            // 새벽/노을 동안 매 프레임 LinearGradient를 새로 만들지 않는다.
            var sh = shaftShader
            if (sh == null || shaftShaderH != vh) {
                sh = LinearGradient(
                    0f, 0f, 0f, vh * 0.72f,
                    Color.argb(255, 255, 178, 96), Color.argb(0, 255, 178, 96),
                    Shader.TileMode.CLAMP
                )
                shaftShader = sh
                shaftShaderH = vh
            }
            glowFill.shader = sh
            glowFill.alpha = a
            c.drawRect(0f, 0f, vw, vh, glowFill)
            glowFill.alpha = 255
            glowFill.shader = null
        }
    }

    /** 비네트 — 화면 가장자리를 은은하게 어둡게 */
    private fun drawVignette(c: Canvas) {
        scratchRect.set(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat())
        c.drawBitmap(game.assets.vignette, null, scratchRect, vignettePaint)
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
            // 사람마다 자기 이모트를 갖고 있으면 그것을 쓴다 (라이더 · 화가 · 낚시꾼 …)
            val opts = n.person.emotes ?: when (n.kind) {
                NpcKind.VILLAGER -> if (rainy) listOf("rain", "sparkle", "…") else listOf("music", "sparkle", "bird")
                NpcKind.KID -> if (night) listOf("moon", "sparkle") else if (rainy) listOf("rain", "sparkle") else listOf("music", "!", "sparkle", "bird")
                NpcKind.ELDER -> if (night) listOf("moon", "sparkle") else if (rainy) listOf("rain", "coffee") else listOf("…", "coffee", "sun")
                NpcKind.SHOP -> listOf("camera", "sparkle", "coin")
                NpcKind.PROFESSOR -> listOf("search", "book", "bird")
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
                // 이름표 (월드 캔버스라 px 단위) — 이름 아래에 별명(직함)을 한 줄 더 단다.
                // 이제 사람은 지역마다 한 명뿐이라 "누구인지"를 알려 주는 게 중요해졌다.
                val np = Type.paintPx(11f, true, 0.02f, 0xFFF8EFDC.toInt())
                val tp = Type.paintPx(8.5f, false, 0.02f, 0xFFDCD2C0.toInt())
                val nameW = np.measureText(n.name)
                val titleW = if (n.title.isEmpty()) 0f else tp.measureText(n.title)
                val plateW = maxOf(nameW, titleW)
                val plateH = if (titleW > 0f) 25f else 15f
                val cx = sx + 16f
                uiFill.color = Color.argb(200, 58, 52, 74)
                c.drawRoundRect(
                    RectF(cx - plateW / 2 - 6f, top - plateH, cx + plateW / 2 + 6f, top),
                    7f, 7f, uiFill
                )
                if (titleW > 0f) {
                    c.drawText(n.name, cx - nameW / 2, top - 14f, np)
                    c.drawText(n.title, cx - titleW / 2, top - 3.5f, tp)
                } else {
                    c.drawText(n.name, cx - nameW / 2, top - 4f, np)
                }
                top -= plateH + 3f
            }
            val em = n.emote ?: continue
            val appear = ((2.6f - n.emoteT) / 0.2f).coerceIn(0f, 1f)
            val fade = (n.emoteT / 0.3f).coerceIn(0f, 1f)
            val a = (255 * minOf(appear, fade)).toInt()
            val cx = sx + 16f
            val by = top - 4f - (1f - appear) * 4f + sin(game.time * 3f) * 1.2f
            uiFill.color = Color.argb(a, 253, 250, 240)
            scratchRect.set(cx - 12f, by - 18f, cx + 12f, by)
            c.drawRoundRect(scratchRect, 7f, 7f, uiFill)
            scratchPath.reset()
            scratchPath.moveTo(cx - 4f, by - 1f); scratchPath.lineTo(cx, by + 5f); scratchPath.lineTo(cx + 4f, by - 1f); scratchPath.close()
            c.drawPath(scratchPath, uiFill)
            uiStroke.strokeWidth = 1.4f
            uiStroke.color = Color.argb(a, 107, 79, 53)
            c.drawRoundRect(scratchRect, 7f, 7f, uiStroke)
            if (UiKit.iconName(em) != null) {
                UiKit.iconCenter(c, game, em, cx, by - 10f, 15f)
            } else {
                val ep = Type.paintPx(12f, false, 0f, Color.argb(a, 74, 55, 40))
                val ew = ep.measureText(em)
                c.drawText(em, cx - ew / 2, by - 5f, ep)
            }
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
            c.drawText(numTxt, badgeCx - tw / 2f, badgeCy - (np.descent() + np.ascent()) / 2f, np)

            // 가까우면 목적지 라벨도
            val distToPlayer = hypot(tunnel.cx - player.cx, tunnel.cy - player.cy)
            if (distToPlayer < 160f) {
                val dirArrow = Regions.dirArrow(tunnel.dir)
                val label = "$dirArrow $targetName"
                val lp = Type.paintPx(10f, true, 0.01f, 0xFFF8EFDC.toInt())
                val lw = lp.measureText(label)
                uiFill.color = Color.argb(200, 58, 52, 74)
                scratchRect.set(badgeCx - lw / 2 - 6f, badgeCy + 12f, badgeCx + lw / 2 + 6f, badgeCy + 26f)
                c.drawRoundRect(scratchRect, 6f, 6f, uiFill)
                c.drawText(label, badgeCx - lw / 2, scratchRect.centerY() - (lp.descent() + lp.ascent()) / 2f, lp)
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
