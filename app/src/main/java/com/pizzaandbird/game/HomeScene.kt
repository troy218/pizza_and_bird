package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 우리 집 내부: 화덕(피자 굽기), 침대(수면), 이사 박스, 장식 칸.
 */
class HomeScene(game: Game) : Scene(game) {

    private val state = game.state
    val map: GameMap = MapBuilder.buildHome()
    private val player = Player()

    private var camX = 0f
    private var camY = 0f

    private val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        color = 0xFF4A3728.toInt()
        textSize = 14f
    }
    private val uiFill = Paint()

    // 상호작용 대상 위치 (월드 px)
    private val ovenX = 12.5f * 16f       // buildHome 오븐 타일 (12..13, 2..3) 중심
    private val ovenY = 3.5f * 16f
    private val bedX = 3f * 16f           // 침대 타일 (2..3, 2..3) 중심
    private val bedY = 3f * 16f
    private val boxX = 2.5f * 16f
    private val boxY = 8.5f * 16f
    // 거실의 인테리어 카탈로그. A를 누르면 여러 디자인을 보고 구매한다.
    private val interiorX = 5f * 16f
    private val interiorY = 8.5f * 16f

    /** 장식 칸 (인덱스, 월드 px) — MapBuilder.buildHome의 DECOR 타일과 1:1 */
    private val decorSpots = listOf(
        Triple(0, 6.5f * 16f, 3f * 16f),
        Triple(1, 9.5f * 16f, 3f * 16f),
        Triple(2, 13.5f * 16f, 8f * 16f)
    )
    /** 장식이 놓이는 타일 위치 (월드 px, 좌상단) */
    private val decorTiles = listOf(
        6f * 16f to 2f * 16f,
        9f * 16f to 2f * 16f,
        13f * 16f to 7f * 16f
    )

    init {
        state.inHome = true
        player.set(7.5f * 16f, 8.5f * 16f)

        game.hud.showControls = true
        game.hud.showStats = true
        game.hud.showMinimap = false
        game.hud.regionLabel = "우리 집"
        game.hud.photoModeHint = false
        game.hud.questLabel = null
        game.banner("🏠 우리 집")

        game.audio.playBgm(R.raw.bgm_home)   // 🎵 신비로운 탐험
        game.audio.stopAmb()
    }

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) {
            game.audio.stopSteps()
            return   // 대화상자/메뉴 중에는 정지
        }
        state.playSeconds += dt * 0.4f
        state.worldTime = (state.worldTime + dt * 24f / DAY_SECONDS) % 24f

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
            val speed = if (state.hunger <= 0f) 34f else 55f
            moveBy(vx * speed * dt, 0f)
            moveBy(0f, vy * speed * dt)
            player.animT += dt
        } else {
            player.animT = 0f
        }

        // 발소리 (나무 바닥)
        game.audio.steps(if (moving) Audio.Steps.WOOD else Audio.Steps.NONE)

        // 현관문
        if (map.feetTile(player.x, player.y) == T.HOUSE_DOOR) {
            exitHome()
            return
        }

        // 카메라 (작은 맵 중앙 고정)
        camX = (map.w * 16f - game.virtW / WORLD_SCALE) / 2f
        camY = (map.h * 16f - game.virtH / WORLD_SCALE) / 2f

        state.px = player.x
        state.py = player.y
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

    /** (대상, 장식 칸 인덱스) */
    private fun nearestInteract(): Pair<String, Int>? {
        if (hypot(ovenX - player.cx, ovenY - player.cy) < 34f) return "oven" to -1
        if (hypot(bedX - player.cx, bedY - player.cy) < 30f) return "bed" to -1
        if (hypot(boxX - player.cx, boxY - player.cy) < 26f) return "box" to -1
        if (hypot(interiorX - player.cx, interiorY - player.cy) < 28f) return "interior" to -1
        for ((idx, dx0, dy0) in decorSpots) {
            if (hypot(dx0 - player.cx, dy0 - player.cy) < 22f) return "decor" to idx
        }
        return null
    }

    private fun interact(target: String, slot: Int) {
        when (target) {
            "oven" -> openOverlay(
                DialogOverlay(
                    this, "화덕 🔥",
                    "따끈한 화덕이 준비됐어요. 어떤 피자를 구워볼까요?\n(도우는 무한! 힐링게임이니까요)",
                    listOf(
                        DialogOverlay.Choice("피자 굽기!") {
                            it.scene.openOverlay(BakeOverlay(it.scene))
                        },
                        DialogOverlay.Choice("나중에")
                    )
                )
            )
            "bed" -> openOverlay(
                DialogOverlay(
                    this, "침대 🛏",
                    "포근한 침대예요. 잠들면 아침이 되고\n행운이 오르며 진행 상황이 저장돼요.",
                    listOf(
                        DialogOverlay.Choice("쿨쿨…") {
                            game.state.luck = (game.state.luck + 5f).coerceAtMost(100f)
                            game.state.worldTime = 7.2f
                            SaveManager.save(game.context, game.state)
                            game.toast("좋은 꿈을 꿨어요! 아침이 밝았다 ☀️ (행운 +5)")
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
            "interior" -> openOverlay(HouseStyleOverlay(this) { styleId ->
                state.houseStyleId = styleId
                SaveManager.save(game.context, state)
                game.toast("${HouseStyles.of(styleId).emoji} ${HouseStyles.of(styleId).name} 적용!")
                game.sfx(Audio.Sfx.SUCCESS, 0.7f)
            })
            "decor" -> {
                if (state.decorOwned.isEmpty()) {
                    openOverlay(
                        DialogOverlay(
                            this, "장식 칸",
                            "아직 소유한 장식이 없어요.\n사진용품점의 '장식 코너'에서 소품을 구경해 보세요! 🧳",
                            listOf(DialogOverlay.Choice("다녀올게요!"))
                        )
                    )
                } else {
                    val idx = slot.coerceIn(0, 2)
                    openOverlay(
                        DecorPickOverlay(this, idx) { picked ->
                            state.decorSlots[idx] = picked
                            SaveManager.save(game.context, state)
                            val name = Decors.of(picked)?.name ?: "장식"
                            game.toast("장식 배치: $name ${Decors.of(picked)?.emoji ?: ""}")
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
            game.toast("${picked.name} 집을 매입했어요! ${won(houseCost)} · 이사 완료 📦")
        } else {
            game.toast("짐 싸기 완료! ${picked.name}의 우리 집으로 이사했어요 📦 · ${won(MOVE_COST)}")
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
            game.toast("집에선 쉬어도 돼요. 새는 밖에서! 🐦")
            return
        }
        if (input.justB) {
            game.toast("집 안에서 자전거는 위험해요!")
            return
        }
        if (input.justEat) {
            val tId = state.eatBest()
            if (tId == null) {
                game.toast("피자가 없어요! 화덕에서 구워요 🍕")
                game.sfx(Audio.Sfx.FAIL, 0.45f)
            } else {
                val t = Toppings.of(tId)
                game.toast("냠냠! ${t.emoji} ${t.name} 피자")
                game.sfx(Audio.Sfx.EAT, 0.9f)
            }
            return
        }
        if (input.justA) {
            val near = nearestInteract()
            if (near != null) {
                interact(near.first, near.second)
            } else {
                game.toast("화덕·침대·인테리어 보드·이사박스에 다가가서 A를 눌러보세요!")
            }
            return
        }
        val tap = input.consumeTapWorld()
        if (tap != null) {
            if (hypot(ovenX - tap.x, ovenY - tap.y) < 26f) interact("oven", -1)
            else if (hypot(bedX - tap.x, bedY - tap.y) < 24f) interact("bed", -1)
            else if (hypot(boxX - tap.x, boxY - tap.y) < 22f) interact("box", -1)
            else if (hypot(interiorX - tap.x, interiorY - tap.y) < 24f) interact("interior", -1)
            else {
                for ((idx, dx0, dy0) in decorSpots) {
                    if (hypot(dx0 - tap.x, dy0 - tap.y) < 18f) {
                        interact("decor", idx)
                        break
                    }
                }
            }
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
        drawInteriorStyle(c)

        // The house's focal point: a warm, gently flickering wood-fired oven.
        val ovenScreenX = (ovenX - camX) * WORLD_SCALE
        val ovenScreenY = (ovenY - camY) * WORLD_SCALE
        val heat = (0.5f + 0.5f * sin(game.time * 4.2f)).coerceIn(0f, 1f)
        uiFill.color = Color.argb((14f + heat * 20f).toInt(), 255, 112, 48)
        c.drawCircle(ovenScreenX, ovenScreenY - 11f, 47f + heat * 4f, uiFill)
        game.illustrations.draw(
            c, "wood_fired_oven.svg",
            RectF(
                (12f * 16f - camX) * WORLD_SCALE - 16f,
                (1f * 16f - camY) * WORLD_SCALE - 8f,
                (12f * 16f - camX) * WORLD_SCALE + 80f,
                (1f * 16f - camY) * WORLD_SCALE + 100f
            )
        )

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
        c.drawOval(RectF(sx + 6f, sy + 24f, sx + 26f, sy + 32f), a.shadowPaint)
        val frame = if (player.moving) ((player.animT / 0.14f).toInt() % 3) else 0
        val ps = a.playerSet(state.gender, state.gearTier())
        val bmp = when (player.facing) {
            Dir.E -> ps.side[frame]
            Dir.W -> ps.sideL[frame]
            Dir.N -> ps.up[frame]
            else -> ps.down[frame]
        }
        c.drawBitmap(bmp, sx, sy, a.sprPaint)

        // 밤: 창문 틴트 + 스탠드 조명 빛
        if (state.worldTime >= 18.5f || state.worldTime < 5f) {
            uiFill.color = Color.argb(30, 20, 26, 60)
            c.drawRect(0f, 0f, game.virtW.toFloat(), game.virtH.toFloat(), uiFill)
            for (wx in listOf(2, 6, 10, 13)) {
                val wxx = (wx * 16f - camX) * WORLD_SCALE
                val wyy = (1 * 16f - camY) * WORLD_SCALE
                uiFill.color = Color.argb(90, 24, 32, 80)
                c.drawRect(wxx + 8f, wyy + 14f, wxx + 26f, wyy + 44f, uiFill)
                uiFill.color = Color.argb(160, 250, 250, 255)
                c.drawRect(wxx + 12f, wyy + 20f, wxx + 14f, wyy + 22f, uiFill)
                c.drawRect(wxx + 20f, wyy + 30f, wxx + 22f, wyy + 32f, uiFill)
            }
        }
        // 스탠드 조명 배치 시 따뜻한 빛
        for (i in decorTiles.indices) {
            if (state.decorSlots[i] == 3) {
                val (tx, ty) = decorTiles[i]
                val lx = (tx - camX) * WORLD_SCALE + 16f
                val ly = (ty - camY) * WORLD_SCALE + 10f
                uiFill.color = Color.argb(46, 255, 214, 120)
                c.drawCircle(lx, ly, 26f, uiFill)
                uiFill.color = Color.argb(28, 255, 214, 120)
                c.drawCircle(lx, ly, 44f, uiFill)
            }
        }

        // 가까운 상호작용 대상 힌트
        val near = nearestInteract()
        if (near != null) {
            val pos = when (near.first) {
                "oven" -> ovenX to ovenY
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
            val p = Paint()
            p.color = 0xFFF2D06B.toInt()
            c.drawCircle(bx, by, 9f, p)
            tinyPaint.textSize = 14f
            val tw = tinyPaint.measureText("!")
            c.drawText("!", bx - tw / 2, by + 5f, tinyPaint)
        }
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

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
        // 조작 힌트
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        tp.textSize = 11f * game.density
        tp.color = 0x99F8EFDC.toInt()
        val hint = "A: 상호작용 · 🍕: 간식 · 메뉴(≡): 피자/도감/설정"
        val w = game.screenW.toFloat()
        c.drawText(hint, w / 2f - tp.measureText(hint) / 2, game.screenH - game.density * 10f, tp)
    }
}
