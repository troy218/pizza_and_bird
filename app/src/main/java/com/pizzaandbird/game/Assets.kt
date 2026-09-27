package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.util.Random
import kotlin.math.min

/**
 * 모든 그래픽은 코드로 생성하는 순수 픽셀 아트 에셋.
 * (외부 이미지 파일 없음 — APK 크기 최소화 & 오프라인)
 */
class Assets {

    private fun c(v: Long): Int = v.toInt()

    // 그리기용 공유 페인트
    val sprPaint = Paint()                                  // 스프라이트 (최근접 샘플링)
    val pxPaint = Paint()                                   // 화면 업스케일 (픽셀 느낌 유지)
    val shadowPaint = Paint().apply { color = Color.argb(70, 30, 40, 30); isAntiAlias = true }

    // 플레이어 --------------------------------------------------------------
    lateinit var playerDown: Array<Bitmap>      // [0] 함께모음 [1] 벌림
    lateinit var playerUp: Array<Bitmap>
    lateinit var playerSide: Array<Bitmap>      // 오른쪽 방향
    lateinit var playerSideL: Array<Bitmap>     // 왼쪽 방향 (플립)
    lateinit var bikeDown: Bitmap
    lateinit var bikeUp: Bitmap
    lateinit var bikeSide: Bitmap
    lateinit var bikeSideL: Bitmap

    // NPC -------------------------------------------------------------------
    lateinit var npcProfessor: Bitmap
    lateinit var npcShop: Bitmap
    lateinit var npcVillager: Bitmap

    // 새 ---------------------------------------------------------------------
    lateinit var birds: Map<String, Bitmap>
    private var birdsFlipped: Map<String, Bitmap> = emptyMap()

    // 타일 -------------------------------------------------------------------
    lateinit var tiles: Array<Array<Bitmap>>    // [T.ordinal][variant]
    lateinit var treeCanopies: Array<Bitmap>      // 전경 레이어의 색/실루엣 변형

    // 아이콘 ------------------------------------------------------------------
    lateinit var pizzaIcon: Bitmap
    lateinit var pizzaIconBig: Bitmap
    lateinit var cloverIcon: Bitmap
    lateinit var cameraIcon: Bitmap
    lateinit var houseIcon: Bitmap

    init {
        buildPlayers()
        buildNpcs()
        buildBirds()
        buildTiles()
        buildIcons()
    }

    // -----------------------------------------------------------------------
    // 스프라이트 빌더
    // -----------------------------------------------------------------------

    private fun sprite(rows: List<String>, pal: Map<Char, Int>): Bitmap {
        val w = rows.maxOf { it.length }
        val h = rows.size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in rows.indices) {
            val r = rows[y]
            for (x in 0 until r.length) {
                val col = pal[r[x]] ?: continue
                bmp.setPixel(x, y, col)
            }
        }
        return bmp
    }

    private fun flipH(src: Bitmap): Bitmap {
        val m = Matrix()
        m.postScale(-1f, 1f, src.width / 2f, 0f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }

    // -----------------------------------------------------------------------
    // 플레이어 & 자전거
    // -----------------------------------------------------------------------

    private fun buildPlayers() {
        val pal = mapOf(
            'h' to c(0xFF5B3A29), 's' to c(0xFFFFD9B0), 'e' to c(0xFF3A2A24), 'r' to c(0xFFF2A58C),
            'b' to c(0xFFF2B63C), 'B' to c(0xFFD99B26), 'k' to c(0xFFD9534F), 'K' to c(0xFFB23F44),
            'p' to c(0xFF4A6FA5), 'o' to c(0xFF7A4A2B), 'f' to c(0xFF9AA0AD), 'w' to c(0xFF3A3A44),
            'g' to c(0xFF6B4F35)
        )
        val headDown = listOf(
            "................",
            ".....hhhhhh.....",
            "....hhhhhhhh....",
            "....hhhhhhhh....",
            "....ssessess....",
            "....srssssrs....",
            ".....bbbbbb.....",
            "....bbbbbbbb....",
            "...sbkkkkbbs....",
            "....bbbbbbbb....",
            "....pppppppp...."
        )
        val legs0 = listOf(
            "....pp....pp....",
            "....pp....pp....",
            "....oo....oo....",
            "................",
            "................"
        )
        val legs1 = listOf(
            "....ppp..ppp....",
            "...pp......pp...",
            "...oo......oo...",
            "................",
            "................"
        )
        val legs2 = listOf(
            "...pp......pp...",
            "..pp........pp..",
            "..oo........oo..",
            "................",
            "................"
        )
        val legs3 = listOf(
            ".....pp..pp.....",
            "....pp....pp....",
            "....oo....oo....",
            "................",
            "................"
        )
        val headUp = listOf(
            "................",
            ".....hhhhhh.....",
            "....hhhhhhhh....",
            "....hhhhhhhh....",
            "....hhhhhhhh....",
            "....hhhhhhhh....",
            "....bbbbbbbb....",
            "...sbkkkkkkbs...",
            "...sbkkkkkkbs...",
            "....bkkkkkkb....",
            "....pppppppp...."
        )
        val headSide = listOf(
            "................",
            "......hhhhhh....",
            ".....hhhhhhhh...",
            ".....hhhhhhhh...",
            ".....hsssses....",
            ".....hssssss....",
            "......bbbbbb....",
            "......bbbbbb....",
            "......sbbbbb....",
            "......pppp......",
            "......pppp......"
        )
        val sideLegs0 = listOf(
            "......pppp......",
            "......pp.pp.....",
            "......oo.oo.....",
            "................",
            "................"
        )
        val sideLegs1 = listOf(
            "......pppp......",
            ".....pp...pp....",
            ".....oo...oo....",
            "................",
            "................"
        )
        val sideLegs2 = listOf(
            "......pppp......",
            ".......pp..pp...",
            ".......oo..oo...",
            "................",
            "................"
        )
        val sideLegs3 = listOf(
            "......pppp......",
            ".....pp..pp.....",
            ".....oo..oo.....",
            "................",
            "................"
        )
        playerDown = arrayOf(
            sprite(headDown + legs0, pal), sprite(headDown + legs1, pal),
            sprite(headDown + legs2, pal), sprite(headDown + legs3, pal)
        )
        playerUp = arrayOf(
            sprite(headUp + legs0, pal), sprite(headUp + legs1, pal),
            sprite(headUp + legs2, pal), sprite(headUp + legs3, pal)
        )
        playerSide = arrayOf(
            sprite(headSide + sideLegs0, pal), sprite(headSide + sideLegs1, pal),
            sprite(headSide + sideLegs2, pal), sprite(headSide + sideLegs3, pal)
        )
        playerSideL = playerSide.map { flipH(it) }.toTypedArray()

        // 자전거
        val bikeSideRows = listOf(
            "................",
            "......hhhhhh....",
            ".....hhhhhhhh...",
            ".....hhhhhhhh...",
            ".....hsssses....",
            ".....hssssss....",
            "......bbbbbb....",
            "......bbbbbb....",
            "......sbbbbbg...",
            "......pppp..f...",
            ".....ppppp......",
            "...f.......f....",
            ".www......www...",
            "w...w....w...w..",
            "w...w....w...w..",
            ".www......www..."
        )
        bikeSide = sprite(bikeSideRows, pal)
        bikeSideL = flipH(bikeSide)

        val bikeDownRows = headDown.subList(0, 10) + listOf(
            "....gggggggg....",
            "...ff......ff...",
            "...ww......ww...",
            "..w..w....w..w..",
            "..w..w....w..w..",
            "...ww......ww..."
        )
        bikeDown = sprite(bikeDownRows, pal)

        val bikeUpRows = headUp.subList(0, 10) + listOf(
            "....gggggggg....",
            "...ff......ff...",
            "...ww......ww...",
            "..w..w....w..w..",
            "..w..w....w..w..",
            "...ww......ww..."
        )
        bikeUp = sprite(bikeUpRows, pal)
    }

    // -----------------------------------------------------------------------
    // NPC
    // -----------------------------------------------------------------------

    private fun buildNpcs() {
        val body = listOf(
            "................",
            ".....hhhhhh.....",
            "....hhhhhhhh....",
            "....hhhhhhhh....",
            "....ssessess....",
            "....ssssssss....",
            ".....bbbbbb.....",
            "....bbbbbbbb....",
            "...sbkkkkbbs....",
            "....bbbbbbbb....",
            "....pppppppp....",
            "....pp....pp....",
            "....pp....pp....",
            "....oo....oo....",
            "................",
            "................"
        )
        fun npc(h: Long, b: Long, k: Long, p: Long, o: Long): Bitmap = sprite(body, mapOf(
            'h' to c(h), 's' to c(0xFFFFD9B0), 'e' to c(0xFF3A2A24),
            'b' to c(b), 'k' to c(k), 'p' to c(p), 'o' to c(o)
        ))
        npcProfessor = npc(0xFFCFD2D8, 0xFFF5F2EA, 0xFF9AA3AD, 0xFF5D6470, 0xFF3A3A44)
        npcShop = npc(0xFFE2574C, 0xFF6FAE57, 0xFF4F7D3F, 0xFF8A6A4F, 0xFF3A3A44)
        npcVillager = npc(0xFF2E2620, 0xFFC3A3E8, 0xFF9F7FC8, 0xFF4A6FA5, 0xFF7A4A2B)
    }

    // -----------------------------------------------------------------------
    // 새 (템플릿 + 종별 색상)
    // -----------------------------------------------------------------------

    private fun buildBirds() {
        val song = listOf(
            "..cc..........",
            ".BBBB.........",
            ".BeBB.........",
            "bBBBBtttt.....",
            "BBBBBttttttt..",
            "BBBBBttttttt..",
            "BWWWBBtttttt..",
            ".BWWWWWWBBt...",
            "..BWWWWWWW....",
            "...ll...ll....",
            "...ll...ll...."
        )
        val water = listOf(
            ".....BBB........",
            "....BBBBB.......",
            "....BeBBB.......",
            "...bBBBB........",
            "....BBBBBBB.....",
            "...BBBBBBBBBtt..",
            "..BWWWWBBBBBtt..",
            "..BWWWWWWWWWt...",
            "...WWWWWWWWW....",
            "....ll....ll....",
            "....ll....ll...."
        )
        val m = LinkedHashMap<String, Bitmap>()
        for (d in Birds.ALL) {
            val pal = mapOf(
                'B' to d.art.body, 'W' to d.art.belly, 't' to d.art.wing,
                'b' to d.art.beak, 'c' to d.art.crest, 'l' to d.art.leg, 'e' to c(0xFF2E2620)
            )
            m[d.id] = sprite(if (d.art.template == 1) water else song, pal)
        }
        birds = m
    }

    // -----------------------------------------------------------------------
    // 타일 (16x16)
    // -----------------------------------------------------------------------

    private var tileSeed = 9017L

    private fun tilePainter(paint: (Canvas, Paint, Random) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        // Each variant gets its own deterministic texture, while builds stay reproducible.
        paint(cv, Paint(), Random(tileSeed++))
        return b
    }

    private fun fill(c: Canvas, p: Paint, color: Int) {
        p.color = color
        c.drawRect(0f, 0f, 16f, 16f, p)
    }

    private fun specks(c: Canvas, p: Paint, r: Random, color: Int, n: Int) {
        p.color = color
        repeat(n) {
            val x = r.nextInt(16)
            val y = r.nextInt(16)
            c.drawPoint(x.toFloat(), y.toFloat(), p)
            c.drawPoint((x + 1) % 16.toFloat(), y.toFloat(), p)
        }
    }

    private fun grassBase(c: Canvas, p: Paint, r: Random, base: Int = c(0xFF96D07A)) {
        fill(c, p, base)
        specks(c, p, r, c(0xFF79B963), 7)
        specks(c, p, r, c(0xFFA9DC90), 5)
        p.color = c(0xFFB9E59A)
        c.drawPoint((2 + r.nextInt(12)).toFloat(), (2 + r.nextInt(12)).toFloat(), p)
    }

    private fun buildTiles() {
        val list = ArrayList<Array<Bitmap>>()

        fun add(vararg bmps: Bitmap) {
            list.add(if (bmps.size == 1) arrayOf(bmps[0]) else bmps.toList().toTypedArray())
        }

        // GRASS (3종 변형) — one enum slot, three actual visual variants.
        add(*Array(3) { tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = c(0xFF78B963)
            repeat(2) {
                val x = 2 + r.nextInt(12)
                val y = 4 + r.nextInt(10)
                c.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 3).coerceAtMost(15).toFloat(), p)
            }
        } })
        // TALLGRASS
        add(tilePainter { c, p, r ->
            grassBase(c, p, r, c(0xFF8CC46C))
            p.color = c(0xFF6FAE57)
            for (i in 0 until 5) {
                val x = 1 + r.nextInt(14)
                val h = 4 + r.nextInt(5)
                c.drawRect(x.toFloat(), (16 - h).toFloat(), (x + 1).toFloat(), 16f, p)
            }
            p.color = c(0xFF8CC46C)
            c.drawRect(3f, 4f, 4f, 9f, p)
            c.drawRect(11f, 5f, 12f, 10f, p)
        })
        // FLOWER (2종 변형)
        add(*Array(2) { tilePainter { c, p, r ->
                grassBase(c, p, r)
                val petals = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFC3A3E8), c(0xFFFFFFFF))
                repeat(3) {
                    val x = 2 + r.nextInt(11)
                    val y = 2 + r.nextInt(11)
                    val col = petals[r.nextInt(petals.size)]
                    p.color = c(0xFF5D8A4A)
                    c.drawRect((x + 1).toFloat(), (y + 2).toFloat(), (x + 2).toFloat(), (y + 4).toFloat(), p)
                    p.color = col
                    c.drawRect(x.toFloat(), y.toFloat(), (x + 2).toFloat(), (y + 2).toFloat(), p)
                    p.color = c(0xFFFFF6D8)
                    c.drawPoint((x + 1).toFloat(), (y + 1).toFloat(), p)
                }
            } })
        // PATH (2종 변형)
        add(*Array(2) { tilePainter { c, p, r ->
            fill(c, p, c(0xFFE5D3A0))
            specks(c, p, r, c(0xFFD6BF87), 7)
            specks(c, p, r, c(0xFFF0E2B8), 4)
            p.color = c(0xFFC4AA78)
            c.drawRect(0f, 0f, 16f, 1f, p)
            c.drawRect(0f, 15f, 16f, 16f, p)
        } })
        // PLAZA
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            p.color = c(0xFFC6B58F)
            c.drawRect(0f, 0f, 16f, 1f, p)
            c.drawRect(0f, 0f, 1f, 16f, p)
            c.drawRect(0f, 8f, 16f, 9f, p)
            c.drawRect(8f, 0f, 9f, 16f, p)
            p.color = c(0xFFE5D8B8)
            c.drawRect(2f, 2f, 7f, 7f, p)
            c.drawRect(10f, 10f, 15f, 15f, p)
        })
        // SAND (2종 변형)
        add(*Array(2) { tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E1B0))
            specks(c, p, r, c(0xFFE4CF96), 7)
            specks(c, p, r, c(0xFFF8ECC8), 5)
            p.color = c(0xFFE9D49E)
            repeat(2) {
                val x = r.nextInt(13)
                val y = r.nextInt(14)
                c.drawRect(x.toFloat(), y.toFloat(), (x + 2).toFloat(), (y + 1).toFloat(), p)
            }
        } })
        // WATER (4 ripple frames): short broken highlights travel at different speeds.
        add(*Array(4) { frame -> tilePainter { c, p, r ->
            fill(c, p, c(0xFF4EADD7))
            p.color = c(0xFF78C4E3)
            val x1 = (1 + frame * 3) % 16
            val x2 = (8 + frame * 2) % 16
            val x3 = (4 + frame * 4) % 16
            c.drawRect(x1.toFloat(), 3f, (x1 + 4).toFloat(), 4f, p)
            c.drawRect(x2.toFloat(), 9f, (x2 + 3).toFloat(), 10f, p)
            c.drawRect(x3.toFloat(), 13f, (x3 + 2).toFloat(), 14f, p)
            p.color = c(0xFFC3ECF5)
            c.drawPoint(((x1 + 3) % 16).toFloat(), 3f, p)
            c.drawPoint(((x2 + 2) % 16).toFloat(), 9f, p)
        } })
        // REED
        add(tilePainter { c, p, r ->
            grassBase(c, p, r, c(0xFF8CC46C))
            p.color = c(0xFF7D9C4F)
            c.drawRect(3f, 2f, 4f, 16f, p)
            c.drawRect(8f, 4f, 9f, 16f, p)
            c.drawRect(13f, 1f, 14f, 16f, p)
            p.color = c(0xFFB0793F)
            c.drawRect(2f, 2f, 5f, 5f, p)
            c.drawRect(12f, 1f, 15f, 4f, p)
        })
        // TREE base: shadow and trunk stay on the ground layer.
        add(tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = c(0x302D5035)
            c.drawOval(android.graphics.RectF(3f, 12f, 13f, 16f), p)
            p.color = c(0xFF8A5A33)
            c.drawRect(7f, 8f, 10f, 16f, p)
            p.color = c(0xFF6B431F)
            c.drawRect(7f, 9f, 8f, 15f, p)
            p.color = c(0xFFB7824D)
            c.drawRect(9f, 10f, 10f, 14f, p)
        })
        // Transparent tree crowns are separate, depth-sorted sprites. Three palettes avoid a cloned forest.
        val crownShadow = intArrayOf(0xFF315F3B.toInt(), 0xFF4B5735.toInt(), 0xFF2E6252.toInt())
        val crownMid = intArrayOf(0xFF3F7D46.toInt(), 0xFF648548.toInt(), 0xFF398064.toInt())
        val crownLight = intArrayOf(0xFF58A85F.toInt(), 0xFF91A956.toInt(), 0xFF59A47D.toInt())
        val crownSpark = intArrayOf(0xFF84C875.toInt(), 0xFFBED278.toInt(), 0xFF8DC6A4.toInt())
        treeCanopies = Array(3) { variant ->
            tilePainter { c, p, r ->
                p.isAntiAlias = false
                p.color = crownShadow[variant]
                c.drawCircle(8f, 5.7f, 7f, p)
                p.color = crownMid[variant]
                c.drawCircle(8f, 5.4f, 6.1f, p)
                c.drawCircle(4.5f, 7f, 3.7f, p)
                c.drawCircle(11.5f, 7f, 3.7f, p)
                p.color = crownLight[variant]
                c.drawCircle(if (variant == 1) 10f else 5.5f, 3.8f, 3.1f, p)
                c.drawCircle(if (variant == 2) 6f else 9.5f, 3.6f, 2.7f, p)
                p.color = crownSpark[variant]
                c.drawRect(4f, 2f, 7f, 3f, p)
                c.drawRect(9f, 2f, 11f, 3f, p)
                p.color = crownShadow[variant]
                c.drawRect(12f, 8f, 14f, 10f, p)
            }
        }
        // ROCK (2종 변형)
        add(*Array(2) { tilePainter { c, p, r ->
                grassBase(c, p, r)
                p.color = c(0xFF7C8590)
                c.drawRect(3f, 5f, 13f, 14f, p)
                p.color = c(0xFF9AA3AD)
                c.drawRect(4f, 4f, 12f, 12f, p)
                p.color = c(0xFFB5BDC6)
                c.drawRect(5f, 5f, 8f, 7f, p)
            } })
        // MOUNTAIN
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF77848F))
            p.color = c(0xFF8D9AA8)
            c.drawRect(0f, 0f, 16f, 3f, p)
            c.drawRect(0f, 6f, 6f, 10f, p)
            c.drawRect(10f, 4f, 16f, 9f, p)
            c.drawRect(0f, 12f, 4f, 16f, p)
            p.color = c(0xFFA5B2BD)
            c.drawRect(2f, 1f, 5f, 2f, p)
            c.drawRect(11f, 5f, 13f, 6f, p)
            p.color = c(0xFF5D6772)
            c.drawRect(0f, 3f, 16f, 4f, p)
            c.drawRect(0f, 15f, 16f, 16f, p)
        })
        // BLDG_WALL
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFE9E2D3))
            p.color = c(0xFFD8CFBA)
            c.drawRect(0f, 4f, 16f, 5f, p)
            c.drawRect(0f, 9f, 16f, 10f, p)
            c.drawRect(0f, 14f, 16f, 15f, p)
        })
        // BLDG_WIN
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFE9E2D3))
            p.color = c(0xFFD8CFBA)
            c.drawRect(0f, 14f, 16f, 15f, p)
            p.color = c(0xFFC9BFA8)
            c.drawRect(3f, 3f, 13f, 12f, p)
            p.color = c(0xFF8FC3E3)
            c.drawRect(4f, 4f, 12f, 11f, p)
            p.color = c(0xFFB9DDF0)
            c.drawRect(4f, 4f, 7f, 7f, p)
            p.color = c(0xFFC9BFA8)
            c.drawRect(7f, 4f, 8f, 11f, p)
            c.drawRect(4f, 7f, 12f, 8f, p)
        })
        // BLDG_ROOF
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFC96A4D))
            p.color = c(0xFFB2583F)
            c.drawRect(0f, 4f, 16f, 5f, p)
            c.drawRect(0f, 10f, 16f, 11f, p)
            c.drawRect(4f, 0f, 5f, 4f, p)
            c.drawRect(12f, 5f, 13f, 10f, p)
            c.drawRect(4f, 11f, 5f, 16f, p)
            p.color = c(0xFFDB8266)
            c.drawRect(0f, 0f, 16f, 1f, p)
        })
        // HOUSE_ROOF
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD4694A))
            p.color = c(0xFFB55338)
            c.drawRect(0f, 3f, 16f, 4f, p)
            c.drawRect(0f, 8f, 16f, 9f, p)
            c.drawRect(0f, 13f, 16f, 14f, p)
            p.color = c(0xFFE08A67)
            c.drawRect(0f, 0f, 16f, 1f, p)
            c.drawRect(2f, 5f, 6f, 6f, p)
            c.drawRect(9f, 10f, 13f, 11f, p)
        })
        // HOUSE_WALL
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFD9C39A)
            c.drawRect(0f, 7f, 16f, 8f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 1f, 16f, p)
            c.drawRect(15f, 0f, 16f, 16f, p)
        })
        // HOUSE_WIN
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFC9A87B)
            c.drawRect(3f, 3f, 13f, 12f, p)
            p.color = c(0xFF9FD0E8)
            c.drawRect(4f, 4f, 12f, 11f, p)
            p.color = c(0xFFC3E4F2)
            c.drawRect(4f, 4f, 7f, 7f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(7f, 4f, 8f, 11f, p)
            c.drawRect(4f, 7f, 12f, 8f, p)
            p.color = c(0xFFF2A3B3)
            c.drawRect(4f, 4f, 5f, 11f, p)
        })
        // HOUSE_DOOR (집 현관 — 들어가는 곳)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFC9A87B)
            c.drawRect(1f, 2f, 15f, 16f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(2f, 3f, 14f, 16f, p)
            p.color = c(0xFF7A4A2B)
            c.drawRect(3f, 5f, 13f, 6f, p)
            c.drawRect(3f, 9f, 13f, 10f, p)
            p.color = c(0xFFF2D06B)
            c.drawRect(11f, 8f, 13f, 10f, p)
        })
        // TUNNEL (터널 입구 — 다른 지역으로!)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF77848F))
            p.color = c(0xFF8D9AA8)
            c.drawRect(0f, 0f, 16f, 4f, p)
            p.color = c(0xFF6B4F35)
            c.drawRect(2f, 2f, 14f, 16f, p)
            p.color = c(0xFF23232B)
            c.drawRect(4f, 4f, 12f, 16f, p)
            p.color = c(0xFFF2D06B)
            c.drawRect(7f, 1f, 9f, 3f, p)
        })
        // FLOOR (집 바닥): staggered honey-oak planks with grain highlights.
        add(*Array(3) { tilePainter { c, p, r ->
            fill(c, p, c(0xFFD8B887))
            p.color = c(0xFFBE9B68)
            c.drawRect(0f, 5f, 16f, 6f, p)
            c.drawRect(0f, 11f, 16f, 12f, p)
            val seam = r.nextInt(5) + 3
            c.drawRect(seam.toFloat(), 0f, (seam + 1).toFloat(), 5f, p)
            c.drawRect(((seam + 7) % 13 + 2).toFloat(), 6f, ((seam + 8) % 13 + 2).toFloat(), 11f, p)
            c.drawRect(((seam + 3) % 13 + 2).toFloat(), 12f, ((seam + 4) % 13 + 2).toFloat(), 16f, p)
            p.color = c(0xFFE8D09D)
            c.drawRect(1f, 2f, 4f, 3f, p)
            c.drawRect(10f, 8f, 13f, 9f, p)
        } })
        // WALL_IN (집 벽)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 16f, 3f, p)
            c.drawRect(0f, 11f, 16f, 12f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 12f, 16f, 16f, p)
        })
        // WALL_WIN (집 창문)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 16f, 3f, p)
            p.color = c(0xFFA8D8E8)
            c.drawRect(3f, 4f, 13f, 11f, p)
            p.color = c(0xFFC9E8F2)
            c.drawRect(3f, 4f, 8f, 7f, p)
            p.color = c(0xFF6B4F35)
            c.drawRect(3f, 4f, 13f, 5f, p)
            c.drawRect(3f, 10f, 13f, 11f, p)
            c.drawRect(7f, 4f, 8f, 11f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 12f, 16f, 16f, p)
        })
        // OVEN (화덕)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF8F8F99))
            p.color = c(0xFF7A7A85)
            c.drawRect(0f, 0f, 16f, 1f, p)
            c.drawRect(0f, 7f, 16f, 8f, p)
            c.drawRect(8f, 0f, 9f, 7f, p)
            c.drawRect(4f, 8f, 5f, 16f, p)
            c.drawRect(12f, 8f, 13f, 16f, p)
            p.color = c(0xFF23232B)
            c.drawCircle(8f, 11f, 4.6f, p)
            p.color = c(0xFFE2574C)
            c.drawRect(5f, 10f, 11f, 14f, p)
            p.color = c(0xFFF2913C)
            c.drawRect(6f, 9f, 10f, 12f, p)
            p.color = c(0xFFF7CE5B)
            c.drawRect(7f, 9f, 9f, 10f, p)
        })
        // BED (침대)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFFB5651D)
            c.drawRect(1f, 1f, 15f, 15f, p)
            p.color = c(0xFFE8867A)
            c.drawRect(2f, 4f, 14f, 14f, p)
            p.color = c(0xFFD96C64)
            c.drawRect(2f, 8f, 14f, 9f, p)
            p.color = c(0xFFF5EFE0)
            c.drawRect(3f, 2f, 8f, 6f, p)
        })
        // BOX (이사 센터 박스)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFFC89B6A)
            c.drawRect(1f, 2f, 15f, 15f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(1f, 2f, 15f, 3f, p)
            c.drawRect(1f, 8f, 15f, 9f, p)
            p.color = c(0xFFD9C39A)
            c.drawRect(6f, 2f, 10f, 15f, p)
            p.color = c(0xFF7A5A33)
            c.drawRect(7f, 4f, 9f, 5f, p)
            c.drawRect(7f, 5f, 8f, 7f, p)
            c.drawRect(8f, 6f, 9f, 7f, p)
        })
        // DECOR (장식 슬롯 — 추후 소품 배치용)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD8B887))
            p.color = c(0xFFB98F5E)
            c.drawRect(0f, 5f, 16f, 6f, p)
            c.drawRect(0f, 11f, 16f, 12f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(3f, 3f, 4f, 4f, p)
            c.drawRect(12f, 3f, 13f, 4f, p)
            c.drawRect(3f, 12f, 4f, 13f, p)
            c.drawRect(12f, 12f, 13f, 13f, p)
            p.color = c(0xFFE8D5A3)
            c.drawRect(7f, 7f, 9f, 9f, p)
        })
        // RUG — repeating woven medallion so adjacent tiles form one broad carpet.
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF4F817A))
            p.color = c(0xFFE4B779)
            c.drawRect(3f, 3f, 13f, 4f, p); c.drawRect(3f, 12f, 13f, 13f, p)
            c.drawRect(3f, 5f, 4f, 11f, p); c.drawRect(12f, 5f, 13f, 11f, p)
            p.color = c(0xFFB85F50)
            c.drawRect(5f, 5f, 11f, 11f, p)
            p.color = c(0xFFFFD58A)
            c.drawRect(7f, 4f, 9f, 12f, p); c.drawRect(4f, 7f, 12f, 9f, p)
            p.color = c(0xFF386A68)
            c.drawRect(7f, 7f, 9f, 9f, p)
        })
        // SHELF — warm wood with books and a small ceramic pot.
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFF754C35); c.drawRect(1f, 10f, 15f, 13f, p)
            p.color = c(0xFF9A6945); c.drawRect(2f, 9f, 14f, 11f, p)
            p.color = c(0xFFE2574C); c.drawRect(3f, 5f, 5f, 9f, p)
            p.color = c(0xFF6F8D62); c.drawRect(6f, 4f, 8f, 9f, p)
            p.color = c(0xFF597A98); c.drawRect(9f, 5f, 11f, 9f, p)
            p.color = c(0xFFF2D06B); c.drawRect(12f, 6f, 14f, 9f, p)
            p.color = c(0xFFB9D990); c.drawRect(12f, 4f, 14f, 6f, p)
        })
        // PLANT — terracotta pot, layered leaves.
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD8B887))
            p.color = c(0xFF4F8B58); c.drawRect(7f, 2f, 9f, 10f, p)
            c.drawRect(4f, 4f, 7f, 7f, p); c.drawRect(9f, 3f, 12f, 6f, p)
            p.color = c(0xFF78B96B); c.drawRect(5f, 2f, 7f, 5f, p)
            c.drawRect(9f, 1f, 11f, 4f, p)
            p.color = c(0xFFB9664C); c.drawRect(4f, 9f, 12f, 12f, p)
            p.color = c(0xFFE39169); c.drawRect(5f, 9f, 11f, 10f, p)
            p.color = c(0xFF8C4B3D); c.drawRect(5f, 12f, 11f, 13f, p)
        })
        // CABINET — counter with drawer, tiny lamp highlight.
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD8B887))
            p.color = c(0xFF80553A); c.drawRect(1f, 4f, 15f, 14f, p)
            p.color = c(0xFFB67A4E); c.drawRect(1f, 4f, 15f, 7f, p)
            p.color = c(0xFFD79B62); c.drawRect(2f, 5f, 14f, 6f, p)
            p.color = c(0xFF9A6442); c.drawRect(3f, 8f, 8f, 13f, p); c.drawRect(9f, 8f, 13f, 13f, p)
            p.color = c(0xFFE7C47C); c.drawRect(5f, 10f, 6f, 11f, p); c.drawRect(10f, 10f, 11f, 11f, p)
        })

        tiles = list.toTypedArray()
    }

    /** 타일 좌표 기반 변형 선택 */
    fun tileVariant(tileOrdinal: Int, x: Int, y: Int): Int {
        val n = tiles[tileOrdinal].size
        if (n <= 1) return 0
        return ((x * 7 + y * 13) % n + n) % n
    }

    fun treeCanopyVariant(x: Int, y: Int): Int =
        ((x * 7 + y * 13) % treeCanopies.size + treeCanopies.size) % treeCanopies.size

    // -----------------------------------------------------------------------
    // 아이콘
    // -----------------------------------------------------------------------

    private fun buildIcons() {
        val pal = mapOf(
            'c' to c(0xFFE8A75C), 'C' to c(0xFFF7CE5B), 'R' to c(0xFFE2574C), 'd' to c(0xFFD18F4A)
        )
        val pizza = listOf(
            "............",
            "...cccccc...",
            "..cCCCCCCc..",
            ".cCCRCCRRCc.",
            ".cCCCCCCCCd.",
            ".cCCRCCCCCd.",
            ".cCCCCCCRRd.",
            ".cCCRCCCCCd.",
            ".dCCCCCCCd..",
            "..ddddddd...",
            "............",
            "............"
        )
        pizzaIcon = sprite(pizza, pal)
        pizzaIconBig = Bitmap.createScaledBitmap(pizzaIcon, pizzaIcon.width * 3, pizzaIcon.height * 3, false)

        cloverIcon = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFF4F9E57)
            cv.drawCircle(4f, 4f, 3.2f, p)
            cv.drawCircle(8f, 4f, 3.2f, p)
            cv.drawCircle(4f, 8f, 3.2f, p)
            cv.drawCircle(8f, 8f, 3.2f, p)
            p.color = c(0xFF6BBA72)
            cv.drawCircle(3.4f, 3.4f, 1.4f, p)
            cv.drawCircle(8.6f, 7.4f, 1.4f, p)
        }

        cameraIcon = Bitmap.createBitmap(14, 12, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFF4A4A55)
            cv.drawRect(0f, 3f, 14f, 12f, p)
            cv.drawRect(4f, 1f, 9f, 3f, p)
            p.color = c(0xFF6B6B78)
            cv.drawRect(1f, 4f, 13f, 5f, p)
            p.color = c(0xFF8FC3E3)
            cv.drawCircle(7f, 8f, 2.6f, p)
            p.color = c(0xFFF2D06B)
            cv.drawRect(11f, 6f, 12f, 7f, p)
        }

        houseIcon = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFFD4694A)
            cv.drawRect(1f, 0f, 11f, 4f, p)
            p.color = c(0xFFF6E7C6)
            cv.drawRect(1f, 4f, 11f, 11f, p)
            p.color = c(0xFF8A5A33)
            cv.drawRect(5f, 7f, 8f, 11f, p)
        }
    }

    /** 새 비트맵 (안전 접근) */
    fun bird(id: String): Bitmap = birds[id] ?: birds.values.first()

    /** 오른쪽을 바라보는 새 (플립, 지연 생성) */
    fun birdFlipped(id: String): Bitmap {
        birdsFlipped[id]?.let { return it }
        val f = flipH(bird(id))
        birdsFlipped = birdsFlipped + (id to f)
        return f
    }

    fun birdW(id: String): Float = bird(id).width.toFloat()
    fun birdH(id: String): Float = bird(id).height.toFloat()
}
