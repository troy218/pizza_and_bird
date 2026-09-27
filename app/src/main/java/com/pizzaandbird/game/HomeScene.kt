package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 우리 집 내부: 화덕(화덕피자 굽기), 가정용 오븐(일반 피자 굽기), 침대(수면), 이사 박스, 장식 칸.
 */
class HomeScene(game: Game) : Scene(game) {

    private val state = game.state
    val map: GameMap = MapBuilder.buildHome()
    private val player = Player()

    /** 집 안에서도 같은 카메라 리그를 쓴다 (걸음 출렁임 · 화덕 충격) */
    private val rig = ViewRig(game.state)

    /** 캔버스 원점에 대응하는 월드 좌표 */
    private var camX = 0f
    private var camY = 0f
    private var velX = 0f
    private var velY = 0f

    /** 탭 좌표 역변환용 — drawWorld가 쓰는 카메라 기준점 */
    override fun cameraOffset(): PointF = PointF(camX, camY)

    private val tinyPaint = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true).apply {
        color = 0xFF4A3728.toInt()
        textSize = 14f
    }
    private val uiFill = Paint()
    private val promptPaint = Paint().apply { color = 0xFFF2D06B.toInt() }
    private val ovenIllustrationBounds = RectF()
    private val aaFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aaStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fxPath = Path()
    private val rnd = Random(7331L)

    // 집 안 디테일 파티클 (화덕 불티 / 연기)
    private class Mote(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val max: Float, val col: Int, var size: Float, val grow: Float
    )
    private val motes = ArrayList<Mote>()
    private var emberT = 0f
    private var smokeT = 0f

    /** 창밖 날씨 — 우리 집이 있는 지역의 오늘 날씨 */
    private val homeWeather: Weather
        get() = state.weather()

    /** 창문 타일 (x, y) — MapBuilder.buildHome 의 WALL_WIN 과 1:1 */
    private val windowTiles = listOf(2 to 1, 6 to 1, 10 to 1, 13 to 1)

    // 상호작용 대상 위치 (월드 px)
    private val ovenX = 12.5f * 16f       // buildHome 화덕 타일 (12..13, 2..3) 중심 — 16x12 확장 레이아웃
    private val ovenY = 3.5f * 16f
    private val rangeX = 11.5f * 16f      // 가정용 오븐 (1x2, 화덕 왼쪽) — 일반 피자
    private val rangeY = 3.5f * 16f
    private val bedX = 3f * 16f           // 침대 타일 (2..3, 2..3) 중심
    private val bedY = 3f * 16f
    private val boxX = 2.5f * 16f
    private val boxY = 8.5f * 16f
    // 거실의 인테리어 카탈로그. A를 누르면 여러 디자인을 보고 구매한다.
    private val interiorX = 5f * 16f
    private val interiorY = 8.5f * 16f

    /**
     * 장식 칸 (인덱스, 월드 px) — MapBuilder.buildHome의 DECOR 타일과 1:1.
     * 예전 3칸은 0~2로 그대로 남겨 기존 집 배치를 잃지 않는다.
     */
    private val decorSpots = listOf(
        Triple(0, 5.5f * 16f, 3f * 16f),
        Triple(1, 7.5f * 16f, 3f * 16f),
        Triple(2, 11.5f * 16f, 6f * 16f),
        Triple(3, 9.5f * 16f, 6f * 16f),
        Triple(4, 4.5f * 16f, 5f * 16f),
        Triple(5, 6.5f * 16f, 5f * 16f),
        Triple(6, 6.5f * 16f, 7f * 16f),
        Triple(7, 8.5f * 16f, 7f * 16f)
    )
    /** 장식이 놓이는 타일 위치 (월드 px, 좌상단) */
    private val decorTiles = listOf(
        5f * 16f to 2f * 16f,
        7f * 16f to 2f * 16f,
        11f * 16f to 5f * 16f,
        9f * 16f to 5f * 16f,
        4f * 16f to 4f * 16f,
        6f * 16f to 4f * 16f,
        6f * 16f to 6f * 16f,
        8f * 16f to 6f * 16f
    )

    /** 상호작용 대상 (A버튼 반경 / 탭 반경). 화덕과 오븐이 나란히 있으므로 항상 가장 가까운 것을 고른다. */
    private class Spot(val key: String, val slot: Int, val x: Float, val y: Float, val reachA: Float, val reachTap: Float)

    private val spots: List<Spot> = listOf(
        Spot("oven", -1, ovenX, ovenY, 34f, 26f),
        Spot("range", -1, rangeX, rangeY, 30f, 30f),
        Spot("bed", -1, bedX, bedY, 30f, 24f),
        Spot("box", -1, boxX, boxY, 26f, 22f),
        Spot("interior", -1, interiorX, interiorY, 28f, 24f)
    ) + decorSpots.map { (idx, dx0, dy0) -> Spot("decor", idx, dx0, dy0, 22f, 18f) }

    init {
        state.inHome = true
        rig.snap(
            map.w * 8f, map.h * 8f, 1f,
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE
        )
        syncCamera()
        player.set(7.5f * 16f, 8.5f * 16f)   // 16x12 확장 룸 중앙
        state.px = player.x
        state.py = player.y

        game.hud.showControls = true
        game.hud.showPunch = false
        game.hud.punchHot = false
        game.hud.showStats = true
        game.hud.showMinimap = false
        game.hud.regionLabel = "우리 집"
        game.hud.photoModeHint = false
        game.hud.questLabel = null
        game.banner("우리 집")

        game.audio.playBgm(R.raw.bgm_home)   // 🎵 집의 잔잔함
        game.audio.stopAmb()
    }

    override fun camera(): ViewRig = rig

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) {
            game.audio.stopSteps()
            updateRig(dt, 0f, 0f, Gait.IDLE)   // 화덕 미니게임 뒤에서도 여운은 이어진다
            return
        }
        state.playSeconds += dt * 0.4f
        state.advanceClock(dt)
        updateMotes(dt)

        // 이동 (자전거 금지!)
        player.bike = false
        val input = game.input
        val dx = input.dirX
        val dy = input.dirY
        val moving = dx != 0f || dy != 0f
        player.moving = moving
        if (moving) {
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) player.facing = if (dx > 0) Dir.E else Dir.W
            else if (dy != 0f) player.facing = if (dy > 0) Dir.S else Dir.N
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            val vx = if (len > 0.01f) dx / len else 0f
            val vy = if (len > 0.01f) dy / len else 0f
            val speed = (if (state.hunger <= 0f) 34f else 55f) * input.moveScale
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

        // 발소리 (나무 바닥)
        game.audio.steps(if (moving) Audio.Steps.WOOD else Audio.Steps.NONE)

        // 현관문
        if (map.feetTile(player.x, player.y) == T.HOUSE_DOOR) {
            exitHome()
            return
        }

        // 카메라 — 방이 화면보다 작아 중앙 고정이지만, 걸음 출렁임은 그대로 살아 있다
        updateRig(dt, velX, velY, if (moving) Gait.WALK else Gait.IDLE)

        state.px = player.x
        state.py = player.y

        // 메인 버튼 맥락 아이콘 (근처 상호작용 대상)
        game.hud.contextIcon = nearestInteract()?.let { (target, _) ->
            when (target) {
                "oven" -> "fire"
                "range" -> "pizza"
                "bed" -> "house"
                "box" -> "box"
                "interior" -> "sparkle"
                else -> "plant"
            }
        }
    }

    private fun updateRig(dt: Float, vx: Float, vy: Float, gait: Gait) {
        rig.update(
            dt,
            map.w * 8f, map.h * 8f,          // 방 한가운데
            vx, vy, gait, 1f,
            map.w * 16f, map.h * 16f,
            game.virtW / WORLD_SCALE, game.virtH / WORLD_SCALE
        )
        syncCamera()
    }

    /** 캔버스 원점(0,0)에 대응하는 월드 좌표 (줌 보정 포함) */
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

    private fun exitHome() {
        SaveManager.save(game.context, state)
        game.audio.stopSteps()
        game.fadeTo {
            game.scene = WorldScene(game, state.homeRegion, SpawnKind.HOME)
        }
    }

    // -------------------------------------------------------------------
    // 상호작용
    // -------------------------------------------------------------------

    /** 가장 가까운 상호작용 대상 (대상, 장식 칸 인덱스) — 반경 안에 여러 개면 제일 가까운 것 */
    private fun nearestSpot(x: Float, y: Float, tap: Boolean): Spot? {
        var best: Spot? = null
        var bestD = Float.MAX_VALUE
        for (sp in spots) {
            val d = hypot(sp.x - x, sp.y - y)
            val reach = if (tap) sp.reachTap else sp.reachA
            if (d < reach && d < bestD) {
                best = sp
                bestD = d
            }
        }
        return best
    }

    private fun nearestInteract(): Pair<String, Int>? {
        val sp = nearestSpot(player.cx, player.cy, tap = false) ?: return null
        return sp.key to sp.slot
    }

    private fun interact(target: String, slot: Int) {
        when (target) {
            "oven" -> openOverlay(
                DialogOverlay(
                    this, "화덕",
                    "장작불이 활활 타오르는 화덕이에요. 얇은 도우의 화덕피자를 굽는 곳!\n" +
                            "뜨거워서 금방 타지만, 잘 구우면 효과가 커요. (도우는 무한! 힐링게임이니까요)",
                    listOf(
                        DialogOverlay.Choice("화덕피자 굽기!") {
                            it.scene.openOverlay(BakeOverlay(it.scene, PizzaKind.OVEN))
                        },
                        DialogOverlay.Choice("나중에")
                    )
                )
            )
            "range" -> openOverlay(
                DialogOverlay(
                    this, "오븐",
                    "익숙한 가정용 오븐이에요. 도톰하고 든든한 일반 피자를 굽는 곳!\n" +
                            "천천히 익어서 굽기 쉬워요. 치즈·페퍼로니·불고기·고구마…",
                    listOf(
                        DialogOverlay.Choice("일반 피자 굽기!") {
                            it.scene.openOverlay(BakeOverlay(it.scene, PizzaKind.REGULAR))
                        },
                        DialogOverlay.Choice("나중에")
                    )
                )
            )
            "bed" -> openOverlay(
                DialogOverlay(
                    this, "침대",
                    "포근한 침대예요. 잠들면 아침이 되고\n행운이 오르며 진행 상황이 저장돼요.",
                    listOf(
                        DialogOverlay.Choice("쿨쿨…") {
                            game.state.luck = (game.state.luck + 5f).coerceAtMost(100f)
                            game.state.sleepUntilMorning()
                            SaveManager.save(game.context, game.state)
                            game.toast("좋은 꿈을 꿨어요! 아침이 밝았다 (행운 +5)")
                            game.sfx(Audio.Sfx.SPARKLE, 0.7f)
                            game.sfx(Audio.Sfx.BIRD_CHIRP1, 0.4f)   // 아침 새소리
                        },
                        DialogOverlay.Choice("아직 안 졸려요")
                    )
                )
            )
            "box" -> openOverlay(
                RegionSelectOverlay(this) { picked ->
                    moveHome(picked)
                }
            )
            "interior" -> openOverlay(
                DialogOverlay(
                    this, "우리 집 꾸미기",
                    "8칸 배치 보드에서 소품을 한눈에 정리하고,\n서로 어울리는 컬렉션을 완성해 보세요!",
                    listOf(
                        DialogOverlay.Choice("배치 보드") { it.scene.openOverlay(HomeDecorOverlay(it.scene)) },
                        DialogOverlay.Choice("스타일 카탈로그") {
                            it.scene.openOverlay(HouseStyleOverlay(it.scene) { styleId ->
                                state.houseStyleId = styleId
                                SaveManager.save(game.context, state)
                                game.toast("${HouseStyles.of(styleId).name} 적용!")
                                game.sfx(Audio.Sfx.SUCCESS, 0.7f)
                            })
                        },
                        DialogOverlay.Choice("나중에")
                    )
                )
            )
            "decor" -> {
                if (state.decorOwned.isEmpty()) {
                    openOverlay(
                        DialogOverlay(
                            this, "장식 칸",
                            "아직 소유한 장식이 없어요.\n사진용품점의 '장식 코너'에서 소품을 구경해 보세요!",
                            listOf(DialogOverlay.Choice("다녀올게요!"))
                        )
                    )
                } else {
                    val idx = slot.coerceIn(0, state.decorSlots.lastIndex)
                    openOverlay(
                        DecorPickOverlay(this, idx) { picked ->
                            state.placeDecor(idx, picked)
                            SaveManager.save(game.context, state)
                            val name = Decors.of(picked)?.name ?: "빈 칸"
                            game.toast(if (picked < 0) "장식 칸을 비웠어요" else "장식 배치: $name")
                            game.sfx(Audio.Sfx.SUCCESS, 0.6f)
                        }
                    )
                }
            }
        }
    }

    private fun moveHome(picked: RegionDef) {
        val s = game.state
        if (picked.id == s.homeRegion) {
            game.toast("이미 여기가 우리 집이에요!")
            return
        }
        val houseCost = if (s.ownsHome(picked.id)) 0 else HousePrices.forRegion(picked.id)
        val totalCost = MOVE_COST + houseCost
        if (s.money < totalCost) {
            val detail = if (houseCost > 0) "집 매입 ${won(houseCost)} + 이사 ${won(MOVE_COST)}" else "이사 ${won(MOVE_COST)}"
            game.toast("돈이 부족해요… 필요한 금액: $detail")
            game.sfx(Audio.Sfx.FAIL, 0.5f)
            return
        }
        s.money -= totalCost
        s.ownedHomes.add(picked.id)
        s.homeRegion = picked.id
        s.region = picked.id
        if (picked.id !in s.visited) s.visited.add(picked.id)
        SaveManager.save(game.context, s)
        if (houseCost > 0) {
            game.toast("${picked.name} 집을 매입했어요! ${won(houseCost)} · 이사 완료")
        } else {
            game.toast("짐 싸기 완료! ${picked.name}의 우리 집으로 이사했어요 · ${won(MOVE_COST)}")
        }
        game.sfx(Audio.Sfx.BUY)
    }

    // -------------------------------------------------------------------
    // 입력
    // -------------------------------------------------------------------

    override fun handleInput(input: Input) {
        if (input.justBack) {
            openOverlay(MenuOverlay(this))
            return
        }
        if (input.justMenu) {
            openOverlay(MenuOverlay(this))
            return
        }
        if (input.justCam) {
            game.toast("집에선 쉬어도 돼요. 새는 밖에서!")
            return
        }
        if (input.justB) {
            game.toast("집 안에서 자전거는 위험해요!")
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
            val near = nearestInteract()
            if (near != null) interact(near.first, near.second)
            return
        }
        val tap = input.consumeTapWorld()
        if (tap != null) {
            val sp = nearestSpot(tap.x, tap.y, tap = true)
            if (sp != null) interact(sp.key, sp.slot)
        }
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFF3A3040.toInt())
        val camXv = camX * WORLD_SCALE
        val camYv = camY * WORLD_SCALE
        val hx = game.virtW / 2f
        val hy = game.virtH / 2f
        c.save()
        if (rig.roll != 0f) c.rotate(rig.roll, hx, hy)
        if (rig.zoom != 1f) c.scale(rig.zoom, rig.zoom, hx, hy)
        map.draw(c, game.assets, camXv, camYv, game.virtW, game.virtH, game.time)
        drawInteriorStyle(c)
        drawWindows(c, camXv, camYv)
        drawSunPatches(c, camXv, camYv)
        drawClock(c, camXv, camYv)

        // The house's focal point: a warm, gently flickering wood-fired oven.
        // (일러스트는 화덕 타일 9~10칸 위에 얹되, 오른쪽 11칸의 가정용 오븐 타일을 가리지 않도록 왼쪽으로 붙인다)
        val ovenScreenX = (ovenX - camX) * WORLD_SCALE - 16f
        val ovenScreenY = (ovenY - camY) * WORLD_SCALE
        val heat = (0.5f + 0.5f * sin(game.time * 4.2f)).coerceIn(0f, 1f)
        uiFill.color = Color.argb((14f + heat * 20f).toInt(), 255, 112, 48)
        c.drawCircle(ovenScreenX, ovenScreenY - 11f, 47f + heat * 4f, uiFill)
        ovenIllustrationBounds.set(
            (12f * 16f - camX) * WORLD_SCALE - 16f,
            (1f * 16f - camY) * WORLD_SCALE - 8f,
            (12f * 16f - camX) * WORLD_SCALE + 80f,
            (1f * 16f - camY) * WORLD_SCALE + 100f
        )
        game.illustrations.draw(c, "wood_fired_oven.svg", ovenIllustrationBounds)

        val a = game.assets

        // 장식 그리기
        for (i in decorTiles.indices) {
            val decorId = state.decorSlots[i]
            if (decorId < 0) continue
            val art = if (decorId < a.decorArt.size) a.decorArt[decorId] else continue
            val (tx, ty) = decorTiles[i]
            c.drawBitmap(art, (tx - camX) * WORLD_SCALE, (ty - camY) * WORLD_SCALE, a.sprPaint)
        }

        // 플레이어
        val sx = (player.x - camX) * WORLD_SCALE
        val sy = (player.y - camY) * WORLD_SCALE
        c.drawBitmap(a.softShadow, null, RectF(sx + 1f, sy + 21f, sx + 31f, sy + 34f), a.sprPaint)
        val ps = a.playerSet(state.gender, state.gearTier())
        val bmp = ps.clip(player.anim).frame(player.facing, player.frame)
        c.drawBitmap(bmp, sx, sy, a.sprPaint)
        // 집 안에서도 카메라는 목에 걸고 다닌다
        val camDir = when (player.facing) {
            Dir.E -> 2
            Dir.W -> 3
            Dir.N -> 1
            else -> 0
        }
        c.drawBitmap(a.camHeld(state.rig().look, camDir, false), sx, sy, a.sprPaint)

        // 화덕 불티 / 연기
        drawMotes(c, camXv, camYv)

        // 조명 (밤엔 화덕·스탠드 조명이 방을 밝힌다)
        drawHomeLighting(c, camXv, camYv)

        // 비네트
        c.drawBitmap(a.vignette, null, RectF(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat()), a.sprPaint)

        // 가까운 상호작용 대상 힌트
        val near = nearestInteract()
        if (near != null) {
            val pos = when (near.first) {
                "oven" -> ovenX to ovenY
                "range" -> rangeX to rangeY
                "bed" -> bedX to bedY
                "box" -> boxX to boxY
                "interior" -> interiorX to interiorY
                else -> {
                    val s = decorSpots[near.second.coerceIn(0, decorSpots.size - 1)]
                    s.second to s.third
                }
            }
            val bob = sin(game.time * 3f) * 2.5f
            val bx = (pos.first - camX) * WORLD_SCALE
            val by = (pos.second - camY) * WORLD_SCALE - 30f + bob
            c.drawCircle(bx, by, 9f, promptPaint)
            tinyPaint.textSize = 14f
            val tw = tinyPaint.measureText("!")
            c.drawText("!", bx - tw / 2, by + 5f, tinyPaint)
        }
        c.restore()
    }

    /** 선택한 스타일에 따라 바닥·벽·포인트를 다시 칠해 네 가지 집 분위기를 보여준다. */
    private fun drawInteriorStyle(c: Canvas) {
        val style = state.houseStyle()
        val p = uiFill
        for (y in 0 until map.h) {
            for (x in 0 until map.w) {
                val tile = map.t(x, y)
                val sx = (x * 16f - camX) * WORLD_SCALE
                val sy = (y * 16f - camY) * WORLD_SCALE
                when (tile) {
                    T.FLOOR -> {
                        p.color = Color.argb(82, Color.red(style.floorTint), Color.green(style.floorTint), Color.blue(style.floorTint))
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, p)
                    }
                    T.WALL_IN -> {
                        p.color = Color.argb(120, Color.red(style.wallTint), Color.green(style.wallTint), Color.blue(style.wallTint))
                        c.drawRect(sx, sy, sx + 32f, sy + 32f, p)
                    }
                    else -> Unit
                }
            }
        }
        // 스타일별 포인트 라인/패턴 — 룸(16x12 타일) 좌표계를 그대로 따른다.
        // (이전 구현은 누락된 좌표 변환 때문에 방 밖까지 그려지는 버그가 있었다)
        p.color = Color.argb(150, Color.red(style.accentTint), Color.green(style.accentTint), Color.blue(style.accentTint))
        fun rx(px: Float): Float = (px - camX) * WORLD_SCALE
        fun ry(py: Float): Float = (py - camY) * WORLD_SCALE
        val roomPx = map.w * 16f     // 방 폭 (월드 px)
        when (style.id) {
            "hanok" -> c.drawRect(rx(32f), ry(64f), rx(roomPx - 32f), ry(69f), p)
            "modern" -> c.drawRect(rx(32f), ry(190f), rx(roomPx - 32f), ry(195f), p)
            "garden" -> {
                c.drawCircle(rx(130f), ry(190f), 14f, p)
                c.drawCircle(rx(165f), ry(190f), 10f, p)
            }
            else -> c.drawRect(rx(32f), ry(202f), rx(roomPx - 32f), ry(206f), p)
        }
        // 인테리어 카탈로그 보드
        val bx = (interiorX - 10f - camX) * WORLD_SCALE
        val by = (interiorY - 15f - camY) * WORLD_SCALE
        p.color = 0xFFF8EFDC.toInt()
        c.drawRect(bx, by, bx + 20f, by + 24f, p)
        p.color = style.accentTint
        c.drawRect(bx + 4f, by + 5f, bx + 16f, by + 8f, p)
        c.drawRect(bx + 4f, by + 12f, bx + 16f, by + 15f, p)
    }

    // -------------------------------------------------------------------
    // 집 안 디테일
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

    /** 시각에 따른 하늘색 (새벽 분홍 → 낮 하늘 → 노을 → 밤) */
    private fun skyColor(h: Float): Int {
        val night = 0xFF1C2350.toInt()
        val dawn = 0xFFF2A07A.toInt()
        val day = 0xFF9FD4F0.toInt()
        val dusk = 0xFFF08A5A.toInt()
        return when {
            h < 4.5f -> night
            h < 6f -> lerpC(night, dawn, (h - 4.5f) / 1.5f)
            h < 7.5f -> lerpC(dawn, day, (h - 6f) / 1.5f)
            h < 17f -> day
            h < 18.5f -> lerpC(day, dusk, (h - 17f) / 1.5f)
            h < 20f -> lerpC(dusk, night, (h - 18.5f) / 1.5f)
            else -> night
        }
    }

    /** 창밖 풍경: 시각·날씨에 따라 하늘색이 변하고, 밤엔 별과 달, 비/눈 오는 날엔 빗줄기/눈송이 */
    private fun drawWindows(c: Canvas, camXv: Float, camYv: Float) {
        val h = state.worldTime
        val w = homeWeather
        val dl = daylight(h)
        var sky = skyColor(h)
        if (w != Weather.SUNNY) {
            val grey = if (w == Weather.SNOW) 0xFFC4CCD6.toInt() else 0xFF8C98A8.toInt()
            sky = lerpC(sky, grey, (when (w) { Weather.WIND -> 0.25f; Weather.CLOUDY -> 0.45f; else -> 0.7f }) * (0.25f + 0.75f * dl))
        }
        for ((i, wt) in windowTiles.withIndex()) {
            val sx = (wt.first * 16f - camX) * WORLD_SCALE
            val sy = (wt.second * 16f - camY) * WORLD_SCALE
            val l = sx + 7.4f; val t = sy + 7.4f; val r = sx + 24.6f; val b = sy + 21.6f
            uiFill.color = sky
            c.drawRect(l, t, r, b, uiFill)
            // 하늘 아래쪽은 살짝 밝게 (지평선)
            uiFill.color = Color.argb(50, 255, 255, 255)
            c.drawRect(l, b - 4f, r, b, uiFill)
            c.save()
            c.clipRect(l, t, r, b)
            if (dl < 0.25f && (w == Weather.SUNNY || w == Weather.CLOUDY)) {
                for (k in 0 until 3) {
                    val hx = hash2(i, k, 41)
                    val tw = (sin(game.time * (1.5f + k * 0.4f) + hx % 10) * 0.5f + 0.5f)
                    uiFill.color = Color.argb((120 + 120 * tw).toInt(), 255, 252, 230)
                    val px = l + 2f + (hx % 14)
                    val py = t + 2f + ((hx / 14) % 8)
                    c.drawRect(px, py, px + 1.2f, py + 1.2f, uiFill)
                }
                if (i == 1) {
                    aaFill.color = 0xFFF8F0C8.toInt()
                    c.drawCircle(r - 5f, t + 4.5f, 2.8f, aaFill)
                    aaFill.color = sky
                    c.drawCircle(r - 3.8f, t + 3.8f, 2.4f, aaFill)       // 초승달
                }
            } else if (dl > 0.5f && w == Weather.SUNNY) {
                // 흘러가는 뭉게구름
                aaFill.color = Color.argb(210, 255, 255, 255)
                val cx = l + ((game.time * 1.6f + i * 9f) % 30f) - 6f
                c.drawCircle(cx, t + 5f, 2.4f, aaFill)
                c.drawCircle(cx + 3f, t + 4.2f, 3f, aaFill)
                c.drawCircle(cx + 6f, t + 5.2f, 2.2f, aaFill)
            }
            when (w) {
                Weather.RAIN -> {
                    aaStroke.strokeWidth = 0.9f
                    aaStroke.color = Color.argb(170, 220, 232, 245)
                    for (k in 0 until 5) {
                        val px = l + ((k * 5.3f + game.time * 9f) % 20f)
                        val py = t + ((k * 7.1f + game.time * 46f) % 18f)
                        c.drawLine(px, py, px + 1.2f, py + 4f, aaStroke)
                    }
                }
                Weather.SNOW -> {
                    uiFill.color = Color.argb(230, 255, 255, 255)
                    for (k in 0 until 5) {
                        val px = l + ((k * 5.3f + sin(game.time + k) * 2f + 20f) % 17f)
                        val py = t + ((k * 6.1f + game.time * 6f) % 14f)
                        c.drawRect(px, py, px + 1.3f, py + 1.3f, uiFill)
                    }
                }
                else -> {}
            }
            c.restore()
            // 창틀 + 창턱 화분
            uiFill.color = 0xFFC9A87B.toInt()
            c.drawRect(sx + 15.4f, t, sx + 16.6f, b, uiFill)
            c.drawRect(l, sy + 13.6f, r, sy + 14.8f, uiFill)
            uiFill.color = 0xFFB5651D.toInt()
            c.drawRect(sx + 8.6f, b - 2.6f, sx + 12.4f, b, uiFill)
            uiFill.color = 0xFF6FAE57.toInt()
            c.drawRect(sx + 8.2f, b - 5.4f, sx + 10.2f, b - 2.6f, uiFill)
            c.drawRect(sx + 10.6f, b - 6.4f, sx + 12.6f, b - 2.6f, uiFill)
        }
    }

    /** 창으로 들어온 햇살이 바닥에 비치고, 그 속에서 먼지가 반짝인다 (해에 따라 기울기가 바뀐다) */
    private fun drawSunPatches(c: Canvas, camXv: Float, camYv: Float) {
        val h = state.worldTime
        val dl = daylight(h)
        if (dl <= 0.02f) return
        val wk = when (homeWeather) {
            Weather.SUNNY -> 1f
            Weather.WIND -> 0.8f
            Weather.CLOUDY -> 0.4f
            else -> 0.15f
        }
        val k = dl * wk
        if (k < 0.05f) return
        val t = ((h - 12f) / 6f).coerceIn(-1.1f, 1.1f)
        val warm = h < 8.5f || h > 16f
        val floorTop = (2 * 16f - camY) * WORLD_SCALE
        val len = 58f + abs(t) * 14f
        val shift = -t * 26f
        for ((i, wt) in windowTiles.withIndex()) {
            val sx = (wt.first * 16f - camX) * WORLD_SCALE
            fxPath.reset()
            fxPath.moveTo(sx + 7.4f, floorTop)
            fxPath.lineTo(sx + 24.6f, floorTop)
            fxPath.lineTo(sx + 24.6f + shift, floorTop + len)
            fxPath.lineTo(sx + 7.4f + shift, floorTop + len)
            fxPath.close()
            uiFill.color = if (warm) Color.argb((52 * k).toInt(), 255, 214, 150) else Color.argb((44 * k).toInt(), 255, 244, 200)
            c.drawPath(fxPath, uiFill)
            // 창살 그림자
            uiFill.color = Color.argb((26 * k).toInt(), 90, 70, 40)
            c.drawRect(sx + 15.4f + shift * 0.5f, floorTop + len * 0.45f, sx + 16.8f + shift * 0.55f, floorTop + len * 0.55f, uiFill)
            // 햇살 속 먼지
            for (m in 0 until 5) {
                val ph = hash2(i, m, 77) % 100 / 100f
                val fy = ((game.time * (3f + m * 0.7f) / len + ph) % 1f)
                val fx = sx + 9f + ((ph * 13f + sin(game.time * 0.6f + m) * 2.5f + 16f) % 15f) + shift * fy
                val twk = sin(game.time * 2.3f + m * 1.7f) * 0.5f + 0.5f
                uiFill.color = Color.argb((170 * k * twk).toInt(), 255, 250, 225)
                c.drawRect(fx, floorTop + fy * len, fx + 1.4f, floorTop + fy * len + 1.4f, uiFill)
            }
        }
    }

    /** 벽시계 — 게임 시각을 바늘로 보여준다 */
    private fun drawClock(c: Canvas, camXv: Float, camYv: Float) {
        val cx = (11 * 16f + 8f - camX) * WORLD_SCALE
        val cy = (1 * 16f + 2f - camY) * WORLD_SCALE
        aaFill.color = 0x40202020
        c.drawCircle(cx + 1.2f, cy + 1.6f, 10f, aaFill)
        aaFill.color = 0xFF6B431F.toInt()
        c.drawCircle(cx, cy, 10f, aaFill)
        aaFill.color = 0xFFFDF6E8.toInt()
        c.drawCircle(cx, cy, 8.2f, aaFill)
        aaFill.color = 0xFF8A6A4F.toInt()
        for (k in 0 until 12) {
            val ang = k / 12f * 6.2832f
            val rr = if (k % 3 == 0) 1.1f else 0.6f
            c.drawCircle(cx + sin(ang) * 6.6f, cy - cos(ang) * 6.6f, rr, aaFill)
        }
        val h = state.worldTime
        val hAng = (h % 12f) / 12f * 6.2832f
        val mAng = (h - h.toInt()) * 6.2832f
        aaStroke.color = 0xFF3A2A1E.toInt()
        aaStroke.strokeWidth = 1.8f
        c.drawLine(cx, cy, cx + sin(hAng) * 4.2f, cy - cos(hAng) * 4.2f, aaStroke)
        aaStroke.strokeWidth = 1.1f
        c.drawLine(cx, cy, cx + sin(mAng) * 6.4f, cy - cos(mAng) * 6.4f, aaStroke)
        aaFill.color = 0xFFE2574C.toInt()
        c.drawCircle(cx, cy, 1.2f, aaFill)
    }

    private fun updateMotes(dt: Float) {
        // 화덕 불티 (위로 톡톡 튀어 오른다)
        emberT -= dt
        if (emberT <= 0f) {
            emberT = 0.12f + rnd.nextFloat() * 0.1f
            if (motes.size < 60) {
                val col = if (rnd.nextBoolean()) Color.argb(255, 247, 206, 91) else Color.argb(255, 242, 145, 60)
                motes.add(Mote(9f * 16f + 4f + rnd.nextFloat() * 24f, 3f * 16f + 6f, (rnd.nextFloat() - 0.5f) * 8f,
                    -12f - rnd.nextFloat() * 10f, 0.9f + rnd.nextFloat() * 0.5f, 1.4f, col, 1.4f, 0f))
            }
        }
        // 화덕 연기
        smokeT -= dt
        if (smokeT <= 0f) {
            smokeT = 0.55f
            if (motes.size < 60) {
                motes.add(Mote(10f * 16f + (rnd.nextFloat() - 0.5f) * 6f, 2f * 16f + 2f, 2f + rnd.nextFloat() * 3f,
                    -7f, 2.6f, 2.6f, Color.argb(90, 220, 214, 206), 2.4f, 2.2f))
            }
        }
        val it = motes.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.x += m.vx * dt + (if (m.grow == 0f) sin(m.life * 9f) * 3f * dt else 0f)
            m.y += m.vy * dt
            m.size += m.grow * dt
            m.life -= dt
            if (m.life <= 0f) it.remove()
        }
    }

    private fun drawMotes(c: Canvas, camXv: Float, camYv: Float) {
        for (m in motes) {
            val k = (m.life / m.max).coerceIn(0f, 1f)
            val sx = m.x * WORLD_SCALE - camXv
            val sy = m.y * WORLD_SCALE - camYv
            val col = Color.argb((Color.alpha(m.col) * k).toInt(), Color.red(m.col), Color.green(m.col), Color.blue(m.col))
            if (m.grow > 0f) {
                aaFill.color = col
                c.drawCircle(sx, sy, m.size, aaFill)
            } else {
                uiFill.color = col
                c.drawRect(sx - m.size / 2, sy - m.size / 2, sx + m.size / 2, sy + m.size / 2, uiFill)
            }
        }
    }

    /** 집 안 조명: 밤이 되면 방이 어두워지고 화덕과 스탠드 조명, 플레이어 주변만 따뜻하게 밝다 */
    private fun drawHomeLighting(c: Canvas, camXv: Float, camYv: Float) {
        val dl = daylight(state.worldTime)
        val flick = 0.9f + sin(game.time * 9.1f) * 0.05f + sin(game.time * 15.7f) * 0.05f
        val ovx = (ovenX - camX) * WORLD_SCALE
        val ovy = (ovenY - camY) * WORLD_SCALE
        val dark = ((1f - dl) * 100f).toInt()
        if (dark > 4) {
            val k = dark / 100f
            // 월드 씬과 같은 크기의 라이트맵을 공유한다 (씬을 오갈 때 2MB 비트맵 재할당 방지).
            // 흔들림이 있어도 가장자리가 새지 않도록 LP 만큼 밀어서 덮는다.
            val lp = (WorldScene.LIGHT_W - game.virtW) / 2f
            val lpy = (WorldScene.LIGHT_H - game.virtH) / 2f
            val lm = LightMaps.get(WorldScene.LIGHT_W, WorldScene.LIGHT_H)
            lm.begin(Color.argb(dark, 16, 18, 46))
            lm.light(ovx + lp, ovy + lpy, 120f * flick, (255 * k).toInt())
            for (i in decorTiles.indices) {
                if (state.decorSlots[i] == 3) {
                    val (tx, ty) = decorTiles[i]
                    lm.light((tx - camX) * WORLD_SCALE + 16f + lp, (ty - camY) * WORLD_SCALE + 12f + lpy, 100f, (255 * k).toInt())
                }
            }
            lm.light((player.cx - camX) * WORLD_SCALE + lp, (player.cy - camY) * WORLD_SCALE - 8f + lpy, 56f, (110 * k).toInt())
            c.save()
            c.translate(-lp, -lpy)
            lm.end(c)
            c.restore()
        }
        // 화덕·가정용 오븐 불빛은 낮에도 은은하게
        Glow.draw(c, Glow.warm, ovx, ovy, 46f * flick, 38f * flick, (60 + 90 * (1f - dl)).toInt())
        Glow.draw(c, Glow.warm, (rangeX - camX) * WORLD_SCALE, (rangeY - camY) * WORLD_SCALE, 34f * flick, 28f * flick, (45 + 70 * (1f - dl)).toInt())
        for (i in decorTiles.indices) {
            if (state.decorSlots[i] == 3) {
                val (tx, ty) = decorTiles[i]
                Glow.draw(c, Glow.warm, (tx - camX) * WORLD_SCALE + 16f, (ty - camY) * WORLD_SCALE + 10f, 40f, 40f, (40 + 110 * (1f - dl)).toInt())
            }
        }
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
    }
}
