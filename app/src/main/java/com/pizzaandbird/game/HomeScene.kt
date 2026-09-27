package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 우리 집 내부: 화덕(피자 굽기), 침대(수면), 이사 박스, 장식 슬롯.
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
        textSize = 9f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        color = 0xFFF2D06B.toInt()
        textSize = 8f
    }

    // 상호작용 대상 위치 (월드 px)
    private val ovenX = 10f * 16f
    private val ovenY = 4f * 16f
    private val bedX = 3f * 16f
    private val bedY = 3f * 16f
    private val boxX = 2.5f * 16f
    private val boxY = 6.5f * 16f
    private val decorSpots = listOf(
        5.5f * 16f to 3f * 16f,
        7.5f * 16f to 3f * 16f,
        11.5f * 16f to 6f * 16f
    )

    init {
        state.inHome = true
        player.set(6 * 16f + 4f, 6 * 16f)

        game.hud.showControls = true
        game.hud.showStats = true
        game.hud.showMinimap = false
        game.hud.regionLabel = "우리 집"
        game.hud.photoModeHint = false
        game.hud.questLabel = null
    }

    override fun update(dt: Float) {
        game.hud.update(dt)
        if (overlay != null) return   // 대화상자/메뉴 중에는 정지
        state.playSeconds += dt * 0.4f

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
            val vx = dx / len
            val vy = dy / len
            val speed = if (state.hunger <= 0f) 34f else 55f
            moveBy(vx * speed * dt, 0f)
            moveBy(0f, vy * speed * dt)
            player.animT += dt
        } else {
            player.animT = 0f
        }

        // 현관문
        if (map.feetTile(player.x, player.y) == T.HOUSE_DOOR) {
            exitHome()
            return
        }

        // 카메라 (작은 맵 중앙 고정)
        camX = (map.w * 16f - game.virtW) / 2f
        camY = (map.h * 16f - game.virtH) / 2f

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
        game.fadeTo {
            game.scene = WorldScene(game, state.homeRegion, SpawnKind.HOME)
        }
    }

    // -------------------------------------------------------------------
    // 상호작용
    // -------------------------------------------------------------------

    private fun nearestInteract(): String? {
        if (hypot(ovenX - player.cx, ovenY - player.cy) < 30f) return "oven"
        if (hypot(bedX - player.cx, bedY - player.cy) < 28f) return "bed"
        if (hypot(boxX - player.cx, boxY - player.cy) < 26f) return "box"
        for ((dx0, dy0) in decorSpots) {
            if (hypot(dx0 - player.cx, dy0 - player.cy) < 20f) return "decor"
        }
        return null
    }

    private fun interact(target: String) {
        when (target) {
            "oven" -> openOverlay(
                DialogOverlay(
                    this, "화덕 🔥",
                    "따끈한 화덕이 준비됐어요. 피자를 구워볼까요? (도우는 무한! 힐링게임이니까요)",
                    listOf(
                        DialogOverlay.Choice("피자 굽기!") {
                            it.finished = true
                            it.scene.openOverlay(BakeOverlay(it.scene))
                        },
                        DialogOverlay.Choice("나중에")
                    )
                )
            )
            "bed" -> openOverlay(
                DialogOverlay(
                    this, "침대 🛏",
                    "포근한 침대예요. 잠들면 행운이 조금 오르고 진행 상황이 저장돼요.",
                    listOf(
                        DialogOverlay.Choice("쿨쿨…") {
                            game.state.luck = (game.state.luck + 5f).coerceAtMost(100f)
                            SaveManager.save(game.context, game.state)
                            game.toast("좋은 꿈을 꿨어요! 행운 +5 (저장 완료) ☘️")
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
            "decor" -> openOverlay(
                DialogOverlay(
                    this, "장식 칸",
                    "아직 꾸밀 소품이 없어요.\n여행지에서 얻는 소품으로 집을 꾸미는 기능은 곧 추가될 예정이에요! 🧳",
                    listOf(DialogOverlay.Choice("기대되는걸!"))
                )
            )
        }
    }

    private fun moveHome(picked: RegionDef) {
        val s = game.state
        if (picked.id == s.homeRegion) {
            game.toast("이미 여기가 우리 집이에요!")
            return
        }
        if (s.money < MOVE_COST) {
            game.toast("이사 비용이 부족해요… (₩${fmtMoney(MOVE_COST)})")
            return
        }
        s.money -= MOVE_COST
        s.homeRegion = picked.id
        s.region = picked.id
        if (picked.id !in s.visited) s.visited.add(picked.id)
        SaveManager.save(game.context, s)
        game.toast("짐 싸기 완료! 이사 끝~ 📦 → ${picked.name}이(가) 우리 새 집!")
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
        if (input.justA) {
            val t = nearestInteract()
            if (t != null) interact(t)
            else game.toast("화덕·침대·이사박스에 다가가서 A를 눌러보세요!")
            return
        }
        val tap = input.consumeTapWorld()
        if (tap != null) {
            if (hypot(ovenX - tap.x, ovenY - tap.y) < 24f) interact("oven")
            else if (hypot(bedX - tap.x, bedY - tap.y) < 22f) interact("bed")
            else if (hypot(boxX - tap.x, boxY - tap.y) < 20f) interact("box")
        }
    }

    // -------------------------------------------------------------------
    // 그리기
    // -------------------------------------------------------------------

    override fun drawWorld(c: Canvas) {
        c.drawColor(0xFF3A3040.toInt())
        map.draw(c, game.assets, camX, camY, game.virtW, game.virtH, game.time)

        val a = game.assets
        c.drawOval(
            android.graphics.RectF(player.x - camX + 3f, player.y - camY + 12f, player.x - camX + 13f, player.y - camY + 16f),
            a.shadowPaint
        )
        val frame = if (player.moving) ((player.animT / 0.16f).toInt() % 2) else 0
        val bmp = when (player.facing) {
            Dir.E -> a.playerSide[frame]
            Dir.W -> a.playerSideL[frame]
            Dir.N -> a.playerUp[frame]
            else -> a.playerDown[frame]
        }
        c.drawBitmap(bmp, player.x - camX, player.y - camY, a.sprPaint)

        // 가까운 상호작용 대상 힌트
        val t = nearestInteract()
        if (t != null) {
            val pos = when (t) {
                "oven" -> ovenX to ovenY
                "bed" -> bedX to bedY
                "box" -> boxX to boxY
                else -> decorSpots[0]
            }
            val bob = sin(game.time * 3f) * 2f
            val bx = pos.first - camX
            val by = pos.second - camY - 18f + bob
            val p = Paint()
            p.color = 0xFFF2D06B.toInt()
            c.drawCircle(bx, by, 5.5f, p)
            tinyPaint.textSize = 9f
            val tw = tinyPaint.measureText("!")
            c.drawText("!", bx - tw / 2, by + 3f, tinyPaint)
        }
    }

    override fun drawHud(c: Canvas) {
        game.hud.draw(c)
        // 조작 힌트
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
        tp.textSize = 11f * game.density
        tp.color = 0x99F8EFDC.toInt()
        val hint = "A: 상호작용 · 메뉴(≡): 피자 먹기/도감"
        val w = game.screenW.toFloat()
        c.drawText(hint, w / 2f - tp.measureText(hint) / 2, game.screenH - game.density * 10f, tp)
    }
}
