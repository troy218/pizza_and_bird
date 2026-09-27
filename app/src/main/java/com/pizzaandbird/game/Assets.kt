package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random

/**
 * 모든 그래픽은 코드로 생성하는 순수 픽셀 아트 에셋 (v0.2 — 2배 해상도).
 * - 타일 32x32 / 캐릭터 32x32 / 새 5종 템플릿
 * - 외부 이미지 파일 없음 — APK 크기 최소화 & 오프라인
 */
class Assets {

    private fun c(v: Long): Int = v.toInt()

    // 그리기용 공유 페인트
    val sprPaint = Paint()                                  // 스프라이트 (최근접 샘플링)
    val pxPaint = Paint()                                   // 화면 업스케일 (픽셀 느낌 유지)
    val shadowPaint = Paint().apply { color = Color.argb(70, 30, 40, 30); isAntiAlias = true }

    // 플레이어 --------------------------------------------------------------
    lateinit var playerDown: Array<Bitmap>      // [0] 서있기 [1][2] 걷기
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
    lateinit var npcKid: Bitmap
    lateinit var npcElder: Bitmap

    // 고양이 -----------------------------------------------------------------
    lateinit var catFrames: Array<Bitmap>       // [0] 앉음 [1][2] 걷기
    lateinit var catFramesL: Array<Bitmap>      // 오른쪽 바라봄

    // 새 ---------------------------------------------------------------------
    lateinit var birds: Map<String, Bitmap>
    private var birdsFlipped: Map<String, Bitmap> = emptyMap()

    // 타일 (32x32) ------------------------------------------------------------
    lateinit var tiles: Array<Array<Bitmap>>    // [T.ordinal][variant 또는 프레임]

    // 아이콘 ------------------------------------------------------------------
    lateinit var pizzaIcon: Bitmap
    lateinit var pizzaIconBig: Bitmap
    lateinit var cloverIcon: Bitmap
    lateinit var cameraIcon: Bitmap
    lateinit var houseIcon: Bitmap
    lateinit var sunIcon: Bitmap
    lateinit var moonIcon: Bitmap
    lateinit var decorArt: Array<Bitmap>        // Decors.ALL 순서

    init {
        buildPlayers()
        buildNpcs()
        buildCat()
        buildBirds()
        buildTiles()
        buildIcons()
        buildDecorArt()
    }

    // -----------------------------------------------------------------------
    // 공용 헬퍼
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

    /** 색 밝기 조절 (팔레트 음영 파생용) */
    private fun shade(color: Int, f: Float): Int = Color.argb(
        255,
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    // -----------------------------------------------------------------------
    // 사람 (32x32, 절차 생성: 외곽선+음영)
    // dir: 0=아래(정면) 1=위(뒤) 2=오른쪽(측면) / frame: 0 서있기 1,2 걷기
    // -----------------------------------------------------------------------

    private class Pal(
        val hair: Int, val hair2: Int, val skin: Int, val skin2: Int,
        val top: Int, val top2: Int, val pants: Int, val pants2: Int,
        val shoe: Int, val line: Int, val pack: Int, val pack2: Int,
        val eye: Int, val blush: Int
    )

    private fun person(
        dir: Int, frame: Int, pl: Pal,
        glasses: Boolean = false, apron: Boolean = false,
        cane: Boolean = false, small: Boolean = false
    ): Bitmap {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
            p.color = col; cv.drawRect(l, t, rr, b, p)
        }
        fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
            p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
        }
        fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
            p.color = col; cv.drawCircle(cx, cy, rad, p)
        }

        val oy = if (small) 3f else 0f                 // 아이는 키가 작다
        val legTop = if (small) 26f else 24.5f

        // ----- 다리 (모든 방향 공통 프레임) -----
        fun legs(xL: Float, xR: Float, wide: Float) {
            val liftL = frame == 1
            val liftR = frame == 2
            // 바지
            if (!liftL) {
                r(xL - 1, legTop - 1, xL + wide + 1, 30.5f, pl.line)
                r(xL, legTop, xL + wide, 30f, pl.pants)
            } else {
                r(xL - 1, legTop - 1, xL + wide + 1, 28.5f, pl.line)
                r(xL, legTop, xL + wide, 28f, pl.pants)
            }
            if (!liftR) {
                r(xR - 1, legTop - 1, xR + wide + 1, 30.5f, pl.line)
                r(xR, legTop, xR + wide, 30f, pl.pants)
            } else {
                r(xR - 1, legTop - 1, xR + wide + 1, 28.5f, pl.line)
                r(xR, legTop, xR + wide, 28f, pl.pants)
            }
            r(xL + wide / 2f - 0.6f, legTop, xL + wide / 2f + 0.6f, 30f, pl.pants2)
            // 신발
            val shoeL = if (liftL) 27.6f else 29.2f
            val shoeR = if (liftR) 27.6f else 29.2f
            r(xL - 1f, shoeL, xL + wide + 1.4f, shoeL + 2.2f, pl.shoe)
            r(xR - 1.4f, shoeR, xR + wide + 1f, shoeR + 2.2f, pl.shoe)
        }

        // ----- 몸통 -----
        fun torso(left: Float, right: Float, topY: Float) {
            o(left - 1, topY - 1, right + 1, legTop + 1.5f, 4f, pl.line)
            o(left, topY, right, legTop + 0.5f, 3.5f, pl.top)
            r(right - (right - left) * 0.32f, topY + 1, right - 1, legTop - 0.5f, pl.top2)
            r(left + (right - left) * 0.28f, topY, left + (right - left) * 0.44f, topY + 2.2f, pl.top2)
            if (apron) {
                o(left + 1.5f, topY + 2.5f, right - 1.5f, legTop - 1f, 2.5f, c(0xFFFDF6E8))
                r(left + 3f, topY + 2.5f, right - 3f, topY + 4f, c(0xFFE8DFC8))
                r(left + 4f, topY + 8f, right - 4f, topY + 9.5f, c(0xFFE8DFC8))
            }
        }

        when (dir) {
            0 -> { // 정면
                torso(9.5f, 22.5f, 15.5f + oy)
                // 팔
                val armY = if (frame == 1) -1f else if (frame == 2) 1f else 0f
                r(7.6f, 16.5f + oy, 10.2f, 22.5f + oy + armY, pl.top2)
                r(21.8f, 16.5f + oy, 24.4f, 22.5f + oy - armY, pl.top2)
                r(8.0f, 22.2f + oy + armY, 9.8f, 24.4f + oy + armY, pl.skin)
                r(22.2f, 22.2f + oy - armY, 24.0f, 24.4f + oy - armY, pl.skin)
                legs(11.2f, 17.4f, 3.6f)
                // 머리
                cir(16f, 9.2f + oy, 7.4f, pl.line)
                cir(16f, 9.2f + oy, 6.6f, pl.skin)
                cir(16f, 7.6f + oy, 6.3f, pl.hair)
                r(10f, 8.4f + oy, 22f, 9.6f + oy, pl.hair)
                r(10f, 8.6f + oy, 11.8f, 14.6f + oy, pl.hair)
                r(20.2f, 8.6f + oy, 22f, 14.6f + oy, pl.hair)
                r(13f, 8.6f + oy, 15f, 10.2f + oy, pl.hair)
                r(17f, 8.6f + oy, 19f, 10.2f + oy, pl.hair)
                r(12.2f, 9.8f + oy, 19.8f, 13.4f + oy, pl.skin)
                // 눈/볼
                r(12.6f, 10.6f + oy, 14f, 12.4f + oy, pl.eye)
                r(18f, 10.6f + oy, 19.4f, 12.4f + oy, pl.eye)
                r(11.2f, 12.6f + oy, 12.8f, 13.8f + oy, pl.blush)
                r(19.2f, 12.6f + oy, 20.8f, 13.8f + oy, pl.blush)
                if (glasses) {
                    p.style = Paint.Style.STROKE; p.strokeWidth = 1.1f
                    p.color = pl.line
                    cv.drawCircle(13.3f, 11.5f + oy, 2.4f, p)
                    cv.drawCircle(18.7f, 11.5f + oy, 2.4f, p)
                    cv.drawLine(15.5f, 11.4f + oy, 16.5f, 11.4f + oy, p)
                    p.style = Paint.Style.FILL
                }
            }
            1 -> { // 뒤
                torso(9.5f, 22.5f, 15.5f + oy)
                val armY = if (frame == 1) -1f else if (frame == 2) 1f else 0f
                r(7.6f, 16.5f + oy, 10.2f, 22.5f + oy + armY, pl.top2)
                r(21.8f, 16.5f + oy, 24.4f, 22.5f + oy - armY, pl.top2)
                legs(11.2f, 17.4f, 3.6f)
                // 머리(뒤통수)
                cir(16f, 9.2f + oy, 7.4f, pl.line)
                cir(16f, 9.2f + oy, 6.6f, pl.hair)
                r(10.5f, 11.5f + oy, 21.5f, 13.6f + oy, pl.hair2)
                r(12f, 6f + oy, 20f, 7.4f + oy, pl.hair2)
                // 배낭
                o(10.4f, 16.2f + oy, 21.6f, 24.2f + oy, 3f, pl.line)
                o(11.2f, 17f + oy, 20.8f, 23.4f + oy, 2.5f, pl.pack)
                r(13f, 18.6f + oy, 19f, 22.2f + oy, pl.pack2)
                r(12.2f, 17.4f + oy, 19.8f, 18.2f + oy, pl.pack2)
            }
            else -> { // 오른쪽 측면
                torso(11.5f, 21.5f, 15.5f + oy)
                // 앞 팔 (흔들림)
                val swing = if (frame == 1) -2f else if (frame == 2) 2f else 0f
                r(14.5f, 17f + oy, 17.5f, 22.5f + oy + swing, pl.top2)
                r(14.9f, 22.2f + oy + swing, 16.9f, 24.2f + oy + swing, pl.skin)
                // 측면 다리(보폭)
                if (frame == 0) {
                    r(13.5f, legTop - 1, 19.5f, 30.5f, pl.line)
                    r(14.5f, legTop, 18.5f, 30f, pl.pants)
                    r(13.8f, 29.2f, 19.6f, 31.2f, pl.shoe)
                } else if (frame == 1) {
                    r(16f, legTop - 1, 20.5f, 29.5f, pl.line)
                    r(17f, legTop, 19.5f, 29f, pl.pants)
                    r(17.6f, 28.4f, 22.4f, 30.4f, pl.shoe)
                    r(10.5f, legTop - 1, 15f, 29.5f, pl.line)
                    r(11.5f, legTop, 14f, 29f, pl.pants2)
                    r(9.8f, 28.2f, 14.4f, 30.2f, pl.shoe)
                } else {
                    r(14f, legTop - 1, 19f, 30f, pl.line)
                    r(15f, legTop, 18f, 29.5f, pl.pants)
                    r(14.4f, 28.6f, 19.8f, 30.6f, pl.shoe)
                    r(12f, legTop, 15f, 28.5f, pl.pants2)
                    r(11.4f, 27.6f, 15.6f, 29.6f, pl.shoe)
                }
                // 머리(측면)
                cir(17f, 9.2f + oy, 7f, pl.line)
                cir(17f, 9.2f + oy, 6.2f, pl.skin)
                p.color = pl.hair
                cv.drawArc(RectF(11f, 3f + oy, 23f, 11.8f + oy), 180f, 180f, true, p)
                r(11f, 8f + oy, 13.6f, 14.8f + oy, pl.hair)
                r(12.4f, 11.8f + oy, 13.6f, 14.6f + oy, pl.hair2)
                r(18.6f, 7.8f + oy, 21.2f, 9.8f + oy, pl.hair)
                // 눈/코/볼
                r(19.6f, 10.2f + oy, 21f, 12f + oy, pl.eye)
                r(22.6f, 11.2f + oy, 23.6f, 12.4f + oy, pl.skin2)
                r(19.8f, 12.8f + oy, 21.2f, 13.8f + oy, pl.blush)
            }
        }

        if (cane) {
            r(23.8f, 15f + oy, 25.2f, 30.5f, c(0xFF8A5A33))
            r(22.6f, 14f + oy, 26f, 15.6f, c(0xFF6B431F))
        }
        return bmp
    }

    // -----------------------------------------------------------------------
    // 플레이어 & 자전거
    // -----------------------------------------------------------------------

    private fun buildPlayers() {
        val pl = Pal(
            hair = c(0xFF4A2F1D), hair2 = c(0xFF382314), skin = c(0xFFFFD9B0), skin2 = c(0xFFE8B88C),
            top = c(0xFFF2B63C), top2 = c(0xFFD99B26), pants = c(0xFF4A6FA5), pants2 = c(0xFF3A5A8A),
            shoe = c(0xFF7A4A2B), line = c(0xFF33241C), pack = c(0xFFD9534F), pack2 = c(0xFFB23F44),
            eye = c(0xFF2E2620), blush = c(0xFFF2A58C)
        )
        playerDown = Array(3) { person(0, it, pl) }
        playerUp = Array(3) { person(1, it, pl) }
        playerSide = Array(3) { person(2, it, pl) }
        playerSideL = Array(3) { flipH(playerSide[it]) }

        val bikeCol = c(0xFFC9503A)
        val bikeDark = c(0xFF8A3326)
        val tire = c(0xFF3A3A44)
        val tireIn = c(0xFF5A5A66)
        val metal = c(0xFF9AA0AD)

        fun bikeBase(cv: Canvas, p: Paint) {
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            // 바퀴
            cir(8f, 26f, 6f, c(0xFF23232B)); cir(8f, 26f, 5.1f, tire); cir(8f, 26f, 2f, tireIn)
            cir(24f, 26f, 6f, c(0xFF23232B)); cir(24f, 26f, 5.1f, tire); cir(24f, 26f, 2f, tireIn)
            cir(8f, 26f, 1f, metal); cir(24f, 26f, 1f, metal)
            // 프레임
            r(8.5f, 19.5f, 23.5f, 22f, bikeCol)
            r(12f, 14.5f, 14.5f, 20f, bikeCol)
            r(20.5f, 13.5f, 23f, 20.5f, bikeCol)
            r(8.5f, 21.4f, 23.5f, 22.4f, bikeDark)
        }

        // 옆모습 (오른쪽)
        run {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            bikeBase(cv, p)
            // 안장/핸들
            r(10.2f, 13.6f, 15.6f, 15.6f, c(0xFF33241C))
            r(19f, 11.8f, 25.2f, 13.8f, c(0xFF33241C))
            r(23.4f, 11.2f, 25.6f, 14.4f, c(0xFF23232B))
            // 페달
            cir(15f, 22.2f, 1.7f, metal)
            r(13.8f, 23.2f, 16.6f, 24.6f, c(0xFF23232B))
            // 라이더 — 다리
            r(12.5f, 15.5f, 16.2f, 21.5f, pl.pants)
            r(13.2f, 20.8f, 17.2f, 23.2f, pl.shoe)
            // 몸통(살짝 숙임)
            o(12.8f, 6.8f, 19.8f, 17f, 3.5f, pl.top)
            r(16.5f, 8f, 19.4f, 16.2f, pl.top2)
            // 팔
            r(16.5f, 9.5f, 19f, 11.5f, pl.top2)
            r(19f, 10.2f, 21.6f, 11.8f, pl.skin)
            // 머리 + 헬멧
            cir(18.2f, 5f, 5f, pl.line)
            cir(18.2f, 5f, 4.3f, pl.skin)
            p.color = c(0xFFD9534F)
            cv.drawArc(RectF(13.6f, 0.4f, 22.8f, 7.4f), 180f, 180f, true, p)
            r(13.8f, 4.2f, 23f, 5.6f, c(0xFFB23F44))
            r(15.4f, 5.4f, 21f, 6.2f, pl.skin)
            bikeSide = bmp
            bikeSideL = flipH(bmp)
        }

        // 뒤모습
        run {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            // 뒷바퀴
            cir(16f, 28.5f, 4.4f, c(0xFF23232B)); cir(16f, 28.5f, 3.6f, tire); cir(16f, 28.5f, 1.4f, tireIn)
            // 라이더(뒤)
            cir(16f, 8.2f, 5.6f, pl.line)
            cir(16f, 8.2f, 4.9f, pl.hair)
            r(11.5f, 10.6f, 20.5f, 12.4f, pl.hair2)
            o(10.6f, 14.6f, 21.4f, 23.6f, 3f, pl.top)
            o(11.4f, 15.4f, 20.6f, 22.8f, 2.5f, pl.pack)
            r(13.2f, 17f, 18.8f, 21.4f, pl.pack2)
            // 팔(핸들쪽)
            r(8.4f, 16.5f, 11.6f, 19.5f, pl.top2)
            r(20.4f, 16.5f, 23.6f, 19.5f, pl.top2)
            r(6.8f, 16.2f, 9.2f, 18.6f, c(0xFF23232B))
            r(22.8f, 16.2f, 25.2f, 18.6f, c(0xFF23232B))
            // 다리
            r(11.6f, 23.6f, 15f, 27.6f, pl.pants)
            r(17f, 23.6f, 20.4f, 27.6f, pl.pants)
            r(11f, 26.6f, 15.4f, 28.8f, pl.shoe)
            r(16.6f, 26.6f, 21f, 28.8f, pl.shoe)
            bikeUp = bmp
        }

        // 정면
        run {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            fun cir(cx: Float, cy: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(cx, cy, rad, p)
            }
            // 앞바퀴
            cir(16f, 28.5f, 4.4f, c(0xFF23232B)); cir(16f, 28.5f, 3.6f, tire); cir(16f, 28.5f, 1.4f, tireIn)
            // 핸들바(정면)
            r(7f, 17.2f, 25f, 19.4f, c(0xFF33241C))
            r(6.4f, 16.6f, 8.6f, 19.8f, c(0xFF23232B))
            r(23.4f, 16.6f, 25.6f, 19.8f, c(0xFF23232B))
            // 라이더(정면)
            cir(16f, 8.2f, 5.6f, pl.line)
            cir(16f, 8.2f, 4.9f, pl.skin)
            p.color = c(0xFFD9534F)
            cv.drawArc(RectF(10.6f, 3.4f, 21.4f, 9.6f), 180f, 180f, true, p)
            r(10.8f, 6.8f, 21.2f, 8.2f, c(0xFFB23F44))
            r(12.8f, 8.6f, 14.4f, 10.6f, pl.eye)
            r(17.6f, 8.6f, 19.2f, 10.6f, pl.eye)
            r(14.6f, 11.4f, 17.4f, 12.4f, pl.blush)
            o(10.6f, 14.6f, 21.4f, 23.6f, 3f, pl.top)
            r(18f, 15.4f, 21f, 23f, pl.top2)
            r(8.4f, 16.5f, 11.6f, 19.5f, pl.top2)
            r(20.4f, 16.5f, 23.6f, 19.5f, pl.top2)
            r(11.6f, 23.6f, 15f, 27.6f, pl.pants)
            r(17f, 23.6f, 20.4f, 27.6f, pl.pants)
            r(11f, 26.6f, 15.4f, 28.8f, pl.shoe)
            r(16.6f, 26.6f, 21f, 28.8f, pl.shoe)
            bikeDown = bmp
        }
    }

    // -----------------------------------------------------------------------
    // NPC
    // -----------------------------------------------------------------------

    private fun npcPal(
        hair: Long, top: Long, top2: Long, pants: Long, pack: Long
    ): Pal = Pal(
        hair = c(hair), hair2 = shade(c(hair), 0.75f),
        skin = c(0xFFFFD9B0), skin2 = c(0xFFE8B88C),
        top = c(top), top2 = c(top2), pants = c(pants), pants2 = shade(c(pants), 0.75f),
        shoe = c(0xFF3A3A44), line = c(0xFF33241C), pack = c(pack), pack2 = shade(c(pack), 0.75f),
        eye = c(0xFF2E2620), blush = c(0xFFF2A58C)
    )

    private fun buildNpcs() {
        npcProfessor = person(0, 0, npcPal(0xFFCFD2D8, 0xFFF5F2EA, 0xFFD8D2C4, 0xFF5D6470, 0xFF9AA3AD), glasses = true)
        npcShop = person(0, 0, npcPal(0xFF4A2F1D, 0xFF6FAE57, 0xFF4F7D3F, 0xFF8A6A4F, 0xFFC89B6A), apron = true)
        npcVillager = person(0, 0, npcPal(0xFF2E2620, 0xFFC3A3E8, 0xFF9F7FC8, 0xFF4A6FA5, 0xFF8A5A33))
        npcKid = person(0, 0, npcPal(0xFF5B3A29, 0xFFE2574C, 0xFFB23F44, 0xFF3F6FB0, 0xFFF2B63C), small = true)
        npcElder = person(0, 0, npcPal(0xFFE8E4DC, 0xFF8A7360, 0xFF6B5A48, 0xFF5D6470, 0xFF4F463F), cane = true)
    }

    // -----------------------------------------------------------------------
    // 고양이 (32x26)
    // -----------------------------------------------------------------------

    private fun buildCat() {
        val orange = c(0xFFE8944A)
        val orange2 = c(0xFFC97430)
        val cream = c(0xFFFBEFD8)
        val line = c(0xFF33241C)
        fun base(): Triple<Bitmap, Canvas, Paint> {
            val bmp = Bitmap.createBitmap(32, 26, Bitmap.Config.ARGB_8888)
            return Triple(bmp, Canvas(bmp), Paint())
        }
        fun head(cv: Canvas, p: Paint, cx: Float, cy: Float) {
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun cir(x: Float, y: Float, rad: Float, col: Int) {
                p.color = col; cv.drawCircle(x, y, rad, p)
            }
            fun tri(a: Float, b2: Float, cc: Float, d: Float, e: Float, f: Float, col: Int) {
                p.color = col
                val path = Path()
                path.moveTo(a, b2); path.lineTo(cc, d); path.lineTo(e, f); path.close()
                cv.drawPath(path, p)
            }
            cir(cx, cy, 5.2f, line); cir(cx, cy, 4.6f, orange)
            tri(cx - 4.4f, cy - 2.6f, cx - 3.4f, cy - 6.8f, cx - 1f, cy - 3.4f, orange)
            tri(cx + 1f, cy - 3.4f, cx + 3.2f, cy - 6.6f, cx + 4.2f, cy - 2.6f, orange)
            tri(cx - 3.7f, cy - 3f, cx - 3.1f, cy - 5.6f, cx - 1.8f, cy - 3.6f, c(0xFFF2A3B3))
            tri(cx + 1.8f, cy - 3.6f, cx + 3f, cy - 5.4f, cx + 3.6f, cy - 3f, c(0xFFF2A3B3))
            r(cx - 2.6f, cy - 1.4f, cx - 1.2f, cy + 0.2f, c(0xFF4F8F52))
            r(cx + 1.2f, cy - 1.4f, cx + 2.6f, cy + 0.2f, c(0xFF4F8F52))
            r(cx - 0.7f, cy + 1.4f, cx + 0.7f, cy + 2.6f, c(0xFFF2A3B3))
            // 수염
            p.color = c(0xCCFDF6E8); p.strokeWidth = 0.9f
            cv.drawLine(cx - 4.6f, cy + 0.6f, cx - 8.4f, cy - 0.6f, p)
            cv.drawLine(cx - 4.6f, cy + 1.8f, cx - 8.2f, cy + 2.4f, p)
            cv.drawLine(cx + 4.6f, cy + 0.6f, cx + 8.4f, cy - 0.6f, p)
            cv.drawLine(cx + 4.6f, cy + 1.8f, cx + 8.2f, cy + 2.4f, p)
        }

        // [0] 앉은 자세
        val (b0, c0, p0) = base()
        run {
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p0.color = col; c0.drawRect(l, t, rr, b, p0)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p0.color = col; c0.drawRoundRect(RectF(l, t, rr, b), rad, rad, p0)
            }
            // 꼬리
            r(23.5f, 8f, 25.7f, 17f, orange2)
            r(21.8f, 5.6f, 26.2f, 8.2f, orange)
            r(23.2f, 5.9f, 25.2f, 7.9f, cream)
            // 몸
            o(6.5f, 12.4f, 24.5f, 24.6f, 6.5f, line)
            o(7.5f, 13.4f, 23.5f, 23.6f, 5.8f, orange)
            r(11f, 13.6f, 13f, 22.6f, orange2)
            r(15.4f, 13.4f, 17.4f, 23f, orange2)
            r(19.6f, 13.8f, 21.6f, 22.4f, orange2)
            o(9.5f, 15.5f, 19.5f, 22.5f, 3.5f, cream)
            // 앞발
            r(9.5f, 22.6f, 13.4f, 24.8f, cream)
            r(15.8f, 22.6f, 19.6f, 24.8f, cream)
            head(c0, p0, 14f, 9.4f)
        }

        // [1][2] 걷는 자세
        fun walking(frame: Int): Bitmap {
            val (bmp, cv, p) = base()
            fun r(l: Float, t: Float, rr: Float, b: Float, col: Int) {
                p.color = col; cv.drawRect(l, t, rr, b, p)
            }
            fun o(l: Float, t: Float, rr: Float, b: Float, rad: Float, col: Int) {
                p.color = col; cv.drawRoundRect(RectF(l, t, rr, b), rad, rad, p)
            }
            // 꼬리 (살짝 흔들림)
            val tw = if (frame == 1) 0f else 1.6f
            r(24.5f + tw, 4f, 26.7f + tw, 14f, orange2)
            r(23.4f + tw, 3.2f, 27f + tw, 5.4f, orange)
            // 몸
            o(3.5f, 10.4f, 26.5f, 20.6f, 5f, line)
            o(4.5f, 11.4f, 25.5f, 19.6f, 4.4f, orange)
            r(9f, 11.6f, 11f, 19.4f, orange2)
            r(14.6f, 11.4f, 16.6f, 19.6f, orange2)
            r(20f, 11.8f, 22f, 19.2f, orange2)
            o(6.5f, 15.4f, 23.5f, 19.4f, 2.5f, cream)
            // 다리 (프레임별)
            if (frame == 1) {
                r(6.6f, 19.4f, 9f, 24.2f, orange)
                r(19.8f, 19.4f, 22.2f, 24.2f, orange)
                r(6.2f, 23f, 9.6f, 25f, cream)
                r(19.4f, 23f, 22.8f, 25f, cream)
                r(13f, 19.6f, 15.4f, 21.8f, orange2)
            } else {
                r(10.6f, 19.4f, 13f, 24.2f, orange)
                r(23f, 19.4f, 25.4f, 24.2f, orange)
                r(10.2f, 23f, 13.6f, 25f, cream)
                r(22.6f, 23f, 26f, 25f, cream)
                r(6.4f, 19.6f, 8.8f, 21.8f, orange2)
            }
            head(cv, p, 6.2f, 8.6f)
            return bmp
        }
        catFrames = arrayOf(b0, walking(1), walking(2))
        catFramesL = arrayOf(flipH(b0), flipH(catFrames[1]), flipH(catFrames[2]))
    }

    // -----------------------------------------------------------------------
    // 새 (5종 템플릿 + 종별 팔레트, 음영 자동 파생)
    // -----------------------------------------------------------------------

    private fun buildBirds() {
        // 0: 소형 명금 (왼쪽 바라봄)
        val songbird = listOf(
            "........................",
            "...cc...................",
            "..cBBB..................",
            ".BBBeEB.................",
            "kkBBBBBttttt............",
            "kkBBBBBtttttttt.........",
            ".HBBBBttttttttttt.......",
            ".HBBBtttTTTTttttttt.....",
            ".HBBBttTTTTTTtttttttt...",
            ".BBBBttTTTTTTtttttttttt.",
            ".BBWWttTTTTTTtttttttttt.",
            ".BWWWWttTTTTttttttttt...",
            "..WWWWWtttttttttttt.....",
            "..WWWWWWWWWWWWWWW.......",
            "...WWWWWWWWWWW..........",
            "....WWWWWWWWW...........",
            ".....ll...ll............",
            ".....ll...ll............",
            "....lll...lll..........."
        )
        // 1: 물오리 (물 위)
        val waterfowl = listOf(
            "..........................",
            "....cc....................",
            "...BBBB...................",
            "..BBBeEB..................",
            ".kkBBBB...................",
            ".kkBBBBB..................",
            "...BBBBBB................",
            "...BBBBBBBBttttttt.......",
            "...HBBBBBBtttttttttttt...",
            "..HHBBBBBtttTTTTtttttttt.",
            "..HBBBBBBttTTTTTTtttttttt",
            ".BBBBBBBttTTTTTTtttttttt.",
            ".BWWWWWWWWttTTTTttttttt..",
            "..WWWWWWWWWWWWWWWWW......",
            "...WWWWWWWWWWWWWWWW......",
            "..vvvvvvvvvvvvvvvvvvv....",
            ".v.vvv...vvvv....vvv....."
        )
        // 2: 섬새/백로형 (긴 목+다리)
        val wader = listOf(
            "......................",
            "....BBB...............",
            "...BBBBB..............",
            "...BBeEB..............",
            "..kBBBB...............",
            ".kkBBBB...............",
            ".kkBBBB...............",
            "..kBBBB...............",
            "...BBBBB..............",
            "...BBBBB..............",
            "....BBBBB.............",
            "....BBBBBBttttttt.....",
            "...BBBBBtttttttttttt..",
            "...HBBBtttTTTTttttttt.",
            "...HBBBttTTTTTTttttttt",
            "...HBBtttTTTTTTttttttt",
            "....BBBtttTTTTttttttt.",
            "....BWWWWtttttttttt...",
            "....WWWWWWWWWWWWWW....",
            ".....WWWWWWWWWWW......",
            "......ll...ll.........",
            "......ll...ll.........",
            "......ll...ll.........",
            ".....lll...lll........"
        )
        // 3: 맹금 (앉은 매)
        val raptor = listOf(
            "............................",
            "......cc....................",
            ".....BBBB...................",
            "....BBeEB...................",
            "...kkBBBB...................",
            "...kkBBBB...................",
            "....BBBB....................",
            ".....BBBBB..................",
            ".....BBBBBBtttttt...........",
            "....HBBBBBtttttttttt........",
            "....HBBBBtttTTTTttttttt.....",
            "....HBBBttTTTTTTTtttttttt...",
            "....HBBBttTTTTTTTtttttttttt.",
            ".....BBBttTTTTTTtttttttttttt",
            ".....BBWWttTTTTTtttttttttt..",
            ".....BWWWWttTTTtttttttttt...",
            ".....WWWWWWttttttttttt......",
            ".....WWWWWWWWWWWWWWW........",
            "......WWWWWWWWWWWW..........",
            ".......ll....ll.............",
            ".......ll....ll.............",
            "......lll....lll............",
            "......lll....lll............"
        )
        // 4: 올빼미 (정면)
        val owl = listOf(
            "...cc.......cc.....",
            "..cccc.....cccc....",
            "..BBBBBBBBBBBBBB...",
            ".BBBBBBBBBBBBBBBB..",
            ".BBeeEBBBBBEeeBB...",
            ".BBeeEBBBBBEeeBB...",
            ".BBBBBBBkkBBBBBB...",
            ".bBBBBBkkkBBBBBb...",
            ".bBBBBBBBBBBBBBb...",
            ".bbBBBBBBBBBBBbb...",
            ".bbBtBBBBBBtBBbb...",
            ".bbBBtBBBBtBBBbb...",
            "..bbBBBBBBBBBbb....",
            "..bbWWWWWWWWWbb....",
            "..bWWWWWWWWWWWb....",
            "..WWWwWWWWwWWWW....",
            "...WWWWWWWWWW......",
            "...WWWWWWWWWW......",
            "....ll....ll.......",
            "....ll....ll.......",
            "...lll....lll......"
        )

        val m = LinkedHashMap<String, Bitmap>()
        for (d in Birds.ALL) {
            val pal = mapOf(
                'B' to d.art.body, 'b' to shade(d.art.body, 0.72f), 'H' to shade(d.art.body, 1.18f),
                'W' to d.art.belly, 'w' to shade(d.art.belly, 0.82f),
                't' to d.art.wing, 'T' to shade(d.art.wing, 0.72f),
                'k' to d.art.beak, 'c' to d.art.crest, 'l' to d.art.leg,
                'e' to c(0xFFFDFDF8), 'E' to c(0xFF1A1611),
                'v' to c(0xFF8FD4EA)
            )
            val rows = when (d.art.template) {
                1 -> waterfowl
                2 -> wader
                3 -> raptor
                4 -> owl
                else -> songbird
            }
            var bmp = sprite(rows, pal)
            if (d.art.scale != 1f) {
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * d.art.scale).toInt().coerceAtLeast(1),
                    (bmp.height * d.art.scale).toInt().coerceAtLeast(1),
                    false
                )
            }
            m[d.id] = bmp
        }
        birds = m
    }

    // -----------------------------------------------------------------------
    // 타일 (32x32)
    // -----------------------------------------------------------------------

    private var tileSeed = 9017

    private fun tilePainter(paint: (Canvas, Paint, Random) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        paint(cv, Paint(), Random(tileSeed.toLong()))
        tileSeed += 1013
        return b
    }

    private fun fill(c: Canvas, p: Paint, color: Int) {
        p.color = color
        c.drawRect(0f, 0f, 32f, 32f, p)
    }

    private fun specks(c: Canvas, p: Paint, r: Random, color: Int, n: Int) {
        p.color = color
        repeat(n) {
            val x = r.nextInt(31)
            val y = r.nextInt(31)
            c.drawRect(x.toFloat(), y.toFloat(), x + 2f, y + 1.5f, p)
        }
    }

    // ------- 고밀도 픽셀 텍스처 헬퍼 (v0.3 디테일 대업그레이드) -------

    /** 짧은 별칭: 픽셀 사각형 */
    private fun px(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float, col: Int) {
        p.color = col; c.drawRect(x, y, x + w, y + h, p)
    }

    /** 1px 점 */
    private fun dot(c: Canvas, p: Paint, x: Float, y: Float, col: Int) {
        p.color = col; c.drawRect(x, y, x + 1f, y + 1f, p)
    }

    /** 무작위 입자 노이즈 (minS~maxS 크기) */
    private fun noise(
        c: Canvas, p: Paint, r: Random,
        x0: Float, y0: Float, x1: Float, y1: Float,
        col: Int, n: Int, minS: Float = 1f, maxS: Float = 2.2f
    ) {
        p.color = col
        val w = x1 - x0
        val h = y1 - y0
        repeat(n) {
            val s = minS + r.nextFloat() * (maxS - minS)
            val x = x0 + r.nextFloat() * (w - s)
            val y = y0 + r.nextFloat() * (h - s)
            c.drawRect(x, y, x + s, y + s * 0.85f, p)
        }
    }

    /** 체커 디더링 — 두 색 사이 그라데이션 느낌 */
    private fun dither(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float,
        col: Int, step: Float = 2f
    ) {
        p.color = col
        var row = 0
        var y = y0
        while (y < y1) {
            val off = if (row % 2 == 0) 0f else step
            var x = x0 + off
            while (x < x1) {
                val w = minOf(step, x1 - x)
                val h = minOf(step, y1 - y)
                if (w > 0f && h > 0f) c.drawRect(x, y, x + w, y + h, p)
                x += step * 2f
            }
            y += step
            row++
        }
    }

    /** 수직 밴드 그라데이션 (디더 전환) */
    private fun vgrad(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float,
        top: Int, bot: Int, bands: Int = 5
    ) {
        val bh = (y1 - y0) / bands
        for (i in 0 until bands) {
            val t = if (bands <= 1) 0f else i / (bands - 1f)
            val col = lerpColor(top, bot, t)
            val yy0 = y0 + i * bh
            px(c, p, x0, yy0, x1 - x0, bh + 0.4f, col)
            if (i > 0) dither(c, p, x0, yy0 - bh * 0.28f, x1, yy0, lerpColor(top, bot, t - 0.12f), 2f)
        }
    }

    /** 두 색 선형 보간 */
    private fun lerpColor(c0: Int, c1: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            255,
            (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * tt).toInt().coerceIn(0, 255),
            (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * tt).toInt().coerceIn(0, 255),
            (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * tt).toInt().coerceIn(0, 255)
        )
    }

    /** 베벨: 위/왼쪽 하이라이트 + 아래/오른쪽 음영 (입체감) */
    private fun bevel(
        c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float,
        hi: Int, lo: Int, th: Float = 1.2f
    ) {
        px(c, p, x0, y0, x1 - x0, th, hi)
        px(c, p, x0, y0, th, y1 - y0, hi)
        px(c, p, x0, y1 - th, x1 - x0, th, lo)
        px(c, p, x1 - th, y0, th, y1 - y0, lo)
    }

    /** 나무 그림자 (땅에 눕힌 부드러운 타원) — 전역 공용 */
    val softShadow: Bitmap by lazy {
        val b = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint()
        p.isAntiAlias = true
        for (i in 6 downTo 1) {
            val a = 10 + (6 - i) * 7
            p.color = Color.argb(a, 22, 34, 24)
            val inset = i * 2.2f
            cv.drawOval(RectF(inset, inset * 0.5f, 64f - inset, 32f - inset * 0.5f), p)
        }
        b
    }

    /** 화면 비네트 (가장자리 은은한 암부) — 전역 공용 */
    val vignette: Bitmap by lazy {
        val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint()
        p.isAntiAlias = true
        p.shader = RadialGradient(
            160f, 90f, 205f,
            intArrayOf(
                Color.argb(0, 12, 10, 22),
                Color.argb(0, 12, 10, 22),
                Color.argb(70, 12, 10, 22),
                Color.argb(120, 12, 10, 22)
            ),
            floatArrayOf(0f, 0.58f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, 320f, 180f, p)
        p.shader = null
        b
    }

    private fun grassBase(c: Canvas, p: Paint, r: Random, base: Int = c(0xFF96D07A)) {
        fill(c, p, base)
        // 3톤 입자 노이즈
        noise(c, p, r, 0f, 0f, 32f, 32f, shade(base, 0.88f), 12, 1f, 2.4f)
        noise(c, p, r, 0f, 0f, 32f, 32f, shade(base, 1.07f), 10, 1f, 2f)
        noise(c, p, r, 0f, 0f, 32f, 32f, shade(base, 0.95f), 8, 1.2f, 3f)
        // 잔디 잎 (3방향)
        p.color = shade(base, 0.8f)
        repeat(8) {
            val x = r.nextInt(28)
            val y = r.nextInt(22)
            val h = 2.6f + r.nextFloat() * 3.4f
            c.drawRect(x.toFloat(), y.toFloat(), x + 1.2f, y + h, p)
        }
        p.color = shade(base, 1.12f)
        repeat(6) {
            val x = r.nextInt(28)
            val y = r.nextInt(22)
            val h = 2.2f + r.nextFloat() * 2.6f
            c.drawRect(x.toFloat(), y.toFloat(), x + 1.2f, y + h, p)
        }
        // 작은 클로버
        p.color = shade(base, 0.86f)
        repeat(2) {
            val x = 2f + r.nextInt(26)
            val y = 2f + r.nextInt(26)
            c.drawRect(x, y, x + 1.6f, y + 1.6f, p)
            c.drawRect(x - 1.4f, y + 1f, x + 3f, y + 2.2f, p)
        }
        // 이슬 하이라이트
        p.color = Color.argb(120, 240, 255, 235)
        repeat(2) {
            val x = r.nextInt(30)
            val y = r.nextInt(30)
            c.drawRect(x.toFloat(), y.toFloat(), x + 1.2f, y + 1.2f, p)
        }
    }

    private fun buildTiles() {
        val list = ArrayList<Array<Bitmap>>()
        fun add(vararg bmps: Bitmap) {
            list.add(if (bmps.size == 1) arrayOf(bmps[0]) else bmps.toList().toTypedArray())
        }

        // ================= GRASS (6종 변형) =================
        add(
            tilePainter { c, p, r ->   // 0: 잔디 기본
                grassBase(c, p, r)
            },
            tilePainter { c, p, r ->   // 1: 클로버 군락
                grassBase(c, p, r)
                val g = c(0xFF5D9E4B)
                val g2 = c(0xFF7FBF62)
                repeat(3) {
                    val x = 3f + r.nextInt(21)
                    val y = 3f + r.nextInt(21)
                    px(c, p, x, y, 2.4f, 2f, g)
                    px(c, p, x - 1.8f, y + 1.4f, 2f, 1.8f, g)
                    px(c, p, x + 2f, y + 1.4f, 2f, 1.8f, g)
                    dot(c, p, x + 0.6f, y + 0.2f, g2)
                }
            },
            tilePainter { c, p, r ->   // 2: 잔돌과 나뭇가지
                grassBase(c, p, r)
                px(c, p, 12f, 15f, 5.6f, 4.4f, c(0xFF7C8590))
                px(c, p, 12.8f, 15.6f, 3.8f, 2.6f, c(0xFFA5B0BA))
                px(c, p, 13.6f, 16.2f, 1.6f, 1.2f, c(0xFFC2CBD3))
                px(c, p, 12.4f, 18.4f, 4.6f, 1f, c(0xFF5D6772))
                px(c, p, 23f, 7f, 3.2f, 2.6f, c(0xFF8A949E))
                px(c, p, 23.4f, 7.4f, 1.8f, 1.3f, c(0xFFB0BAC2))
                px(c, p, 4f, 24f, 10f, 1.5f, c(0xFF8A5A33))
                px(c, p, 5f, 24f, 6f, 0.7f, c(0xFFA87B4F))
                px(c, p, 11.4f, 22.4f, 1.5f, 3.2f, c(0xFF7A4A2B))
                px(c, p, 8f, 25.6f, 2f, 1f, c(0xFF6B431F))
            },
            tilePainter { c, p, r ->   // 3: 민들레와 잡초
                grassBase(c, p, r)
                // 민들레 (씨앗 흰 솜털)
                px(c, p, 19f, 14f, 1.4f, 7f, c(0xFF5D8A4A))
                p.color = c(0xFFFDF6E8)
                c.drawCircle(19.8f, 12.8f, 3.2f, p)
                p.color = c(0xFFE8DFC8)
                c.drawCircle(19.8f, 12.8f, 1.4f, p)
                for (a in 0 until 6) {
                    val ang = a * 1.0472f
                    dot(c, p, 19.8f + 2.6f * Math.cos(ang.toDouble()).toFloat(), 12.8f + 2.6f * Math.sin(ang.toDouble()).toFloat(), c(0xFFFFFBEE))
                }
                // 노란 꽃 봉오리
                px(c, p, 7f, 20f, 3f, 2.6f, c(0xFFF2D06B))
                px(c, p, 7.8f, 20.4f, 1.4f, 1.4f, c(0xFFF7E08C))
                px(c, p, 7.6f, 22.6f, 1.2f, 3.6f, c(0xFF5D8A4A))
            },
            tilePainter { c, p, r ->   // 4: 작은 버섯
                grassBase(c, p, r)
                px(c, p, 8f, 13f, 7f, 4.6f, c(0xFFC9572E))
                px(c, p, 8.6f, 13.4f, 4f, 2f, c(0xFFE8823C))
                px(c, p, 9.8f, 13.8f, 1.6f, 1.2f, c(0xFFF2A3B3))
                px(c, p, 10.6f, 17.6f, 2.4f, 4f, c(0xFFF2E3C2))
                px(c, p, 21f, 22f, 5f, 3.4f, c(0xFFB23F44))
                px(c, p, 21.4f, 22.4f, 2.6f, 1.4f, c(0xFFD9534F))
                px(c, p, 22.8f, 25.4f, 1.8f, 3f, c(0xFFF2E3C2))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF6B431F), 3, 1f, 1.4f)
            },
            tilePainter { c, p, r ->   // 5: 야생화 한 줄기
                grassBase(c, p, r)
                px(c, p, 15f, 12f, 1.4f, 10f, c(0xFF5D8A4A))
                px(c, p, 13f, 15f, 2.6f, 1.6f, c(0xFF6FAE57))
                px(c, p, 16f, 18f, 2.6f, 1.6f, c(0xFF6FAE57))
                // 보라 꽃
                px(c, p, 13.4f, 8f, 5f, 4.6f, c(0xFFC3A3E8))
                px(c, p, 14.2f, 8.6f, 3f, 2.6f, c(0xFFD9C0F2))
                px(c, p, 15.2f, 9.4f, 1.6f, 1.6f, c(0xFFF2D06B))
                // 흰 꽃
                px(c, p, 24f, 22f, 3.6f, 3.2f, c(0xFFFDFDF8))
                px(c, p, 24.8f, 22.6f, 1.8f, 1.6f, c(0xFFF2D06B))
            }
        )

        // ================= TALLGRASS (2종) =================
        add(
            tilePainter { c, p, r ->   // 0: 얕은 풀숲
                grassBase(c, p, r, c(0xFF8CC46C))
                val dark = c(0xFF5D8A4A)
                val mid = c(0xFF6FAE57)
                val light = c(0xFF8CC46C)
                for (k in 0 until 9) {
                    val x = 1 + r.nextInt(28)
                    val h = 8 + r.nextInt(10)
                    val y = 32 - h
                    px(c, p, x.toFloat(), y.toFloat(), 2f, h.toFloat(), dark)
                    px(c, p, x + 1.6f, y + 2f, 1f, h - 2.4f, mid)
                }
                for (k in 0 until 5) {
                    val x = 2 + r.nextInt(27)
                    val h = 6 + r.nextInt(8)
                    px(c, p, x.toFloat(), (32 - h).toFloat(), 1.6f, h.toFloat(), light)
                    dot(c, p, x.toFloat(), (32 - h).toFloat(), c(0xFFB0D98C))
                }
            },
            tilePainter { c, p, r ->   // 1: 키 큰 풀숲 + 씨앗 이삭
                grassBase(c, p, r, c(0xFF8CC46C))
                val dark = c(0xFF547A44)
                val mid = c(0xFF6FAE57)
                for (k in 0 until 11) {
                    val x = 1 + r.nextInt(28)
                    val h = 12 + r.nextInt(12)
                    val y = 32 - h
                    px(c, p, x.toFloat(), y.toFloat(), 2.2f, h.toFloat(), dark)
                    px(c, p, x + 1.8f, y + 3f, 1f, h - 3.4f, mid)
                    if (k % 3 == 0) {
                        px(c, p, x - 0.4f, y - 3.4f, 3f, 3.8f, c(0xFFB0793F))
                        px(c, p, x + 0.2f, y - 2.8f, 1.6f, 2f, c(0xFFC89B6A))
                    }
                }
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF547A44), 6, 1f, 1.6f)
            }
        )

        // ================= FLOWER (3색) =================
        val flowerCols = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFFDFDF8))
        add(*Array(3) { i ->
            tilePainter { c, p, r ->
                grassBase(c, p, r)
                repeat(5) {
                    val x = 3f + r.nextInt(22)
                    val y = 4f + r.nextInt(20)
                    // 줄기와 잎
                    px(c, p, x + 1.6f, y + 2.6f, 1.2f, 4.4f, c(0xFF5D8A4A))
                    px(c, p, x + 0.2f, y + 4.4f, 1.6f, 1.2f, c(0xFF6FAE57))
                    px(c, p, x + 2.8f, y + 5f, 1.6f, 1.2f, c(0xFF6FAE57))
                    // 꽃잎 (5매 + 하이라이트)
                    val col = flowerCols[(i + r.nextInt(3)) % 3]
                    val lite = shade(col, 1.18f)
                    val dark = shade(col, 0.8f)
                    px(c, p, x + 1f, y - 1.2f, 2.4f, 2f, col)
                    px(c, p, x - 1f, y + 0.6f, 2.4f, 2f, col)
                    px(c, p, x + 3.2f, y + 0.6f, 2.4f, 2f, col)
                    px(c, p, x + 1f, y + 2.4f, 2.4f, 1.8f, dark)
                    px(c, p, x + 0.2f, y + 0.4f, 4.2f, 2.2f, col)
                    px(c, p, x + 1f, y + 0.2f, 2f, 1.2f, lite)
                    // 수술
                    px(c, p, x + 1.6f, y + 0.8f, 1.6f, 1.6f, c(0xFFF7CE5B))
                    dot(c, p, x + 2f, y + 1f, c(0xFFE8A75C))
                }
            }
        })

        // ================= PATH (3종) =================
        add(
            tilePainter { c, p, r ->   // 0: 자갈길
                fill(c, p, c(0xFFE5D3A0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFD6BF87), 16, 1.2f, 3f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFF0E2B8), 12, 1f, 2.4f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFC9B582), 8, 1f, 1.8f)
                // 밟힌 자국 (은은한 음영)
                p.color = Color.argb(28, 120, 90, 40)
                c.drawOval(RectF(6f, 9f, 18f, 16f), p)
                c.drawOval(RectF(17f, 20f, 29f, 27f), p)
            },
            tilePainter { c, p, r ->   // 1: 조경석 길 (둥근 돌)
                fill(c, p, c(0xFFCDB88E))
                val stones = arrayOf(
                    floatArrayOf(1f, 2f, 12f, 12f), floatArrayOf(13f, 0f, 25f, 10f),
                    floatArrayOf(26f, 3f, 32f, 13f), floatArrayOf(2f, 13f, 13f, 23f),
                    floatArrayOf(14f, 11f, 27f, 22f), floatArrayOf(28f, 14f, 32f, 25f),
                    floatArrayOf(0f, 24f, 11f, 32f), floatArrayOf(12f, 23f, 24f, 32f),
                    floatArrayOf(25f, 26f, 32f, 32f)
                )
                for (s in stones) {
                    val t = lerpColor(c(0xFFE9DCBC), c(0xFFD9C9A7), r.nextFloat())
                    px(c, p, s[0], s[1], s[2] - s[0], s[3] - s[1], t)
                    // 돌 상단 하이라이트 / 하단 음영
                    px(c, p, s[0] + 1f, s[1], s[2] - s[0] - 2f, 1.4f, shade(t, 1.12f))
                    px(c, p, s[0], s[3] - 1.6f, s[2] - s[0], 1.6f, shade(t, 0.84f))
                    px(c, p, s[2] - 1.4f, s[1] + 1f, 1.4f, s[3] - s[1] - 1f, shade(t, 0.88f))
                    noise(c, p, r, s[0] + 1f, s[1] + 1f, s[2] - 1f, s[3] - 1f, shade(t, 0.94f), 2, 1f, 1.6f)
                }
                // 이끼 낀 틈
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF8FA86B), 5, 1f, 1.6f)
            },
            tilePainter { c, p, r ->   // 2: 흙길 + 자국
                fill(c, p, c(0xFFE0CB98))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFCFB884), 14, 1.2f, 2.8f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDDFB2), 10, 1f, 2f)
                // 발자국
                p.color = Color.argb(52, 110, 82, 38)
                c.drawOval(RectF(7f, 6f, 12f, 13f), p)
                c.drawOval(RectF(19f, 18f, 24f, 25f), p)
                p.color = Color.argb(40, 255, 245, 210)
                c.drawOval(RectF(7.8f, 6.8f, 11.2f, 12f), p)
                // 작은 돌
                px(c, p, 26f, 8f, 3f, 2.2f, c(0xFFB0A88E))
                px(c, p, 26.4f, 8.4f, 1.6f, 1f, c(0xFFD0C8AC))
            }
        )

        // ================= PLAZA (2종) =================
        add(
            tilePainter { c, p, r ->   // 0: 석판 광장
                fill(c, p, c(0xFFC6B58F))
                // 4분할 석판
                for (gx in 0 until 2) {
                    for (gy in 0 until 2) {
                        val x = gx * 16f + 1.6f
                        val y = gy * 16f + 1.6f
                        val t = lerpColor(c(0xFFE9DCBC), c(0xFFDCCBA6), r.nextFloat())
                        px(c, p, x, y, 12.8f, 12.8f, t)
                        bevel(c, p, x, y, x + 12.8f, y + 12.8f, shade(t, 1.1f), shade(t, 0.82f), 1.2f)
                        noise(c, p, r, x + 1.4f, y + 1.4f, x + 11.4f, y + 11.4f, shade(t, 0.95f), 3, 1f, 1.8f)
                    }
                }
                // 틈새 이끼
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF9AA873), 4, 1f, 1.6f)
            },
            tilePainter { c, p, r ->   // 1: 패턴 석판 + 균열
                fill(c, p, c(0xFFC6B58F))
                px(c, p, 1.6f, 1.6f, 28.8f, 28.8f, c(0xFFE4D6B2))
                bevel(c, p, 1.6f, 1.6f, 30.4f, 30.4f, c(0xFFF1E6C8), c(0xFFB8A87F), 1.4f)
                // 대각 패턴
                px(c, p, 6f, 6f, 8f, 8f, c(0xFFD9C9A7))
                px(c, p, 18f, 18f, 8f, 8f, c(0xFFD9C9A7))
                px(c, p, 7f, 7f, 6f, 1.4f, c(0xFFEFE3C2))
                px(c, p, 19f, 19f, 6f, 1.4f, c(0xFFEFE3C2))
                // 균열
                px(c, p, 14f, 2f, 1f, 8f, c(0xFFA0906B))
                px(c, p, 14.8f, 9f, 1f, 5f, c(0xFFA0906B))
                px(c, p, 15.6f, 13f, 1f, 7f, c(0xFFA0906B))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFB0A178), 5, 1f, 1.4f)
            }
        )

        // ================= SAND (3종) =================
        add(
            tilePainter { c, p, r ->   // 0: 모래
                fill(c, p, c(0xFFF2E1B0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE4CF96), 16, 1f, 2.2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFF8ECC8), 12, 1f, 2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFD9BF82), 6, 1f, 1.6f)
            },
            tilePainter { c, p, r ->   // 1: 물결 + 조개
                fill(c, p, c(0xFFF2E1B0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE4CF96), 12, 1f, 2f)
                // 모래 물결
                for (k in 0 until 3) {
                    val y = 5f + k * 9f
                    p.color = c(0xFFE4CF96)
                    for (x in 0 until 30 step 3) {
                        c.drawRect(x.toFloat(), y + Math.sin(x * 0.45 + k).toFloat() * 1.2f, x + 2.2f, y + 1.4f + Math.sin(x * 0.45 + k).toFloat() * 1.2f, p)
                    }
                }
                // 조개
                px(c, p, 12f, 17f, 8f, 5.4f, c(0xFFFDF6E8))
                px(c, p, 12.8f, 17.6f, 6.4f, 3.4f, c(0xFFF5E8D2))
                px(c, p, 15.4f, 18.2f, 1.2f, 3.4f, c(0xFFE8B14E))
                px(c, p, 13.6f, 18.6f, 1f, 2.4f, c(0xFFE8B14E))
                px(c, p, 17.4f, 18.6f, 1f, 2.4f, c(0xFFE8B14E))
                px(c, p, 12.6f, 21.4f, 6.8f, 1f, c(0xFFD9C4A0))
            },
            tilePainter { c, p, r ->   // 2: 불가사리 + 자국
                fill(c, p, c(0xFFF2E1B0))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE4CF96), 14, 1f, 2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFF8ECC8), 8, 1f, 1.8f)
                // 불가사리
                val star = c(0xFFE88B7A)
                px(c, p, 13f, 12f, 6f, 8f, star)
                px(c, p, 11f, 14f, 10f, 4.6f, star)
                px(c, p, 12f, 10f, 2.6f, 4f, star)
                px(c, p, 17.6f, 10f, 2.6f, 4f, star)
                px(c, p, 10f, 19f, 3.4f, 3f, star)
                px(c, p, 19f, 19f, 3.4f, 3f, star)
                px(c, p, 14f, 13.4f, 4f, 4.4f, shade(star, 1.15f))
                noise(c, p, r, 11f, 10f, 22f, 22f, shade(star, 0.85f), 6, 1f, 1.2f)
                // 작은 돌
                px(c, p, 25f, 25f, 3f, 2.4f, c(0xFFD9C4A0))
                px(c, p, 25.4f, 25.4f, 1.6f, 1.2f, c(0xFFF0E2C4))
            }
        )

        // ================= WATER (4프레임 애니메이션) =================
        add(*Array(4) { f ->
            tilePainter { c, p, r ->
                // 깊이감 있는 수직 그라데이션
                vgrad(c, p, 0f, 0f, 32f, 32f, c(0xFF63B8E4), c(0xFF3E8FC4), 6)
                // 잔물결 밴드
                val off = f * 2.4f
                p.color = c(0xFF4FA8D8)
                for ((i, by) in listOf(3f, 12.5f, 22f).withIndex()) {
                    for (x in -4 until 34 step 7) {
                        val xx = ((x + off * (1f + i * 0.3f)) % 36f + 36f) % 36f - 2f
                        c.drawRect(xx, by, xx + 5f, by + 1.8f, p)
                    }
                }
                // 물결 하이라이트 (이동)
                p.color = c(0xFF93D4EF)
                for ((i, by) in listOf(2.2f, 11.8f, 21.2f).withIndex()) {
                    for (x in -4 until 34 step 11) {
                        val xx = ((x + off * (1.6f + i * 0.25f)) % 34f + 34f) % 34f - 1f
                        c.drawRect(xx, by, xx + 3.4f, by + 1.2f, p)
                    }
                }
                // 반짝임 (프레임별 위치)
                p.color = c(0xFFC9ECF8)
                val sx = floatArrayOf(6f, 21f, 13f, 27f)[f]
                val sy = floatArrayOf(7f, 26f, 28f, 15f)[f]
                c.drawRect(sx, sy, sx + 2.2f, sy + 1.2f, p)
                c.drawRect(sx + 0.5f, sy - 1f, sx + 1.2f, sy + 3f, p)
                // 은은한 수초 그림자
                p.color = Color.argb(34, 20, 70, 90)
                c.drawRect(0f, 27f, 32f, 32f, p)
            }
        })

        // ================= REED (2종) =================
        add(
            tilePainter { c, p, r ->   // 0: 갈대 군락
                grassBase(c, p, r, c(0xFF8CC46C))
                val xs = intArrayOf(4, 11, 19, 27)
                for ((i, x) in xs.withIndex()) {
                    val ty = if (i % 2 == 0) 3f else 0f
                    px(c, p, x.toFloat(), ty, 2.4f, 32f - ty, c(0xFF7D9C4F))
                    px(c, p, x + 0.6f, ty + 2f, 1.2f, 30f - ty, c(0xFFA3C26B))
                    // 곤충 씨앗 이삭
                    px(c, p, x - 1f, ty, 4.6f, 8f, c(0xFFB0793F))
                    px(c, p, x - 0.2f, ty + 1.4f, 3f, 5f, c(0xFFC89B6A))
                    px(c, p, x + 0.4f, ty + 2f, 1.4f, 3f, c(0xFF8A5A33))
                }
                px(c, p, 9f, 22f, 6f, 1.6f, c(0xFF6FAE57))
            },
            tilePainter { c, p, r ->   // 1: 물가 갈대 + 부들
                grassBase(c, p, r, c(0xFF8CC46C))
                for (x in intArrayOf(6, 14, 22)) {
                    px(c, p, x.toFloat(), 2f, 2f, 30f, c(0xFF6B8F45))
                    px(c, p, x + 1.4f, 5f, 1f, 25f, c(0xFFA3C26B))
                }
                // 부들 (갈대 머리)
                px(c, p, 5.2f, 2f, 3.6f, 9f, c(0xFF8A5A33))
                px(c, p, 6f, 3f, 1.8f, 6f, c(0xFFB0793F))
                px(c, p, 21.4f, 5f, 3.6f, 8f, c(0xFF8A5A33))
                px(c, p, 22.2f, 6f, 1.8f, 5f, c(0xFFB0793F))
                // 잎
                px(c, p, 8f, 14f, 8f, 1.6f, c(0xFF6FAE57))
                px(c, p, 16f, 20f, 9f, 1.6f, c(0xFF5D8A4A))
            }
        )

        // ================= TREE (4종: 참나무/소나무/벚나무/단풍나무) =================
        add(
            tilePainter { c, p, r ->   // 0: 참나무
            grassBase(c, p, r)
            p.color = Color.argb(58, 26, 46, 28)
            c.drawOval(RectF(5f, 24f, 29f, 31f), p)
            // 기둥
            px(c, p, 13f, 16f, 7f, 15f, c(0xFF5D3A20))
            px(c, p, 14.2f, 16f, 3.4f, 15f, c(0xFF7A4E2B))
            px(c, p, 14.8f, 17f, 1.2f, 11f, c(0xFF9A6A3E))
            px(c, p, 12f, 28f, 3f, 3.4f, c(0xFF5D3A20))
            px(c, p, 18.4f, 28f, 3f, 3.4f, c(0xFF5D3A20))
            noise(c, p, r, 13f, 17f, 20f, 30f, c(0xFF4A2D18), 5, 1f, 1.5f)
            // 수관 (3층)
            p.color = c(0xFF2E5D33)
            c.drawCircle(16f, 12f, 12f, p)
            p.color = c(0xFF3F7D46)
            c.drawCircle(14.5f, 10.5f, 10f, p)
            c.drawCircle(21f, 13.5f, 7.4f, p)
            p.color = c(0xFF57A55F)
            c.drawCircle(11.5f, 8f, 6.2f, p)
            c.drawCircle(19f, 9f, 5f, p)
            p.color = c(0xFF6FBF72)
            c.drawCircle(10f, 6.6f, 3.2f, p)
            c.drawCircle(13.5f, 8f, 2.2f, p)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFF2C5429), 12, 1f, 1.8f)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFF7FD184), 8, 1f, 1.6f)
            // 도토리
            px(c, p, 22f, 17f, 2f, 2.4f, c(0xFFB0793F))
            px(c, p, 22f, 17f, 2f, 1f, c(0xFF8A5A33))
            },
            tilePainter { c, p, r ->   // 1: 소나무
            grassBase(c, p, r)
            p.color = Color.argb(58, 26, 46, 28)
            c.drawOval(RectF(6f, 25f, 28f, 31f), p)
            px(c, p, 14.4f, 20f, 4.4f, 11f, c(0xFF5D3A20))
            px(c, p, 15.2f, 20f, 2f, 11f, c(0xFF7A4E2B))
            val path = Path()
            p.color = c(0xFF24512F)
            path.moveTo(16f, -1f); path.lineTo(27f, 13f); path.lineTo(5f, 13f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF2F6B3B)
            path.reset()
            path.moveTo(16f, 6f); path.lineTo(29f, 21f); path.lineTo(3f, 21f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF24512F)
            path.reset()
            path.moveTo(16f, 13f); path.lineTo(31f, 29f); path.lineTo(1f, 29f); path.close()
            c.drawPath(path, p)
            // 눈/빛 팁
            px(c, p, 12f, 7f, 8f, 1.6f, c(0xFF4F9E57))
            px(c, p, 8f, 15f, 8f, 1.6f, c(0xFF4F9E57))
            px(c, p, 18f, 22f, 8f, 1.6f, c(0xFF4F9E57))
            noise(c, p, r, 3f, 2f, 29f, 28f, c(0xFF1D4427), 10, 1f, 1.6f)
            noise(c, p, r, 3f, 2f, 29f, 28f, c(0xFF67B572), 6, 1f, 1.4f)
            },
            tilePainter { c, p, r ->   // 2: 벚나무 (꽃)
            grassBase(c, p, r)
            p.color = Color.argb(52, 26, 46, 28)
            c.drawOval(RectF(5f, 24f, 29f, 31f), p)
            px(c, p, 14f, 17f, 6f, 14f, c(0xFF5D3A20))
            px(c, p, 15f, 17f, 2.6f, 14f, c(0xFF8A5A33))
            px(c, p, 12f, 22f, 2f, 2.2f, c(0xFF5D3A20))
            px(c, p, 19.4f, 24f, 2f, 2f, c(0xFF5D3A20))
            p.color = c(0xFFD97F99)
            c.drawCircle(15f, 11f, 11.4f, p)
            p.color = c(0xFFF2A3B3)
            c.drawCircle(13.5f, 9.5f, 9.4f, p)
            c.drawCircle(21f, 13f, 6.6f, p)
            p.color = c(0xFFFFC9D6)
            c.drawCircle(11f, 7.5f, 5.4f, p)
            c.drawCircle(17f, 8.5f, 4.2f, p)
            noise(c, p, r, 4f, 2f, 28f, 21f, c(0xFFFFE0E8), 14, 1f, 1.8f)
            noise(c, p, r, 4f, 2f, 28f, 21f, c(0xFFC96B87), 8, 1f, 1.5f)
            // 지는 꽃잎
            px(c, p, 6f, 26f, 2f, 1.4f, c(0xFFF2A3B3))
            px(c, p, 24f, 28f, 2f, 1.4f, c(0xFFFFC9D6))
            px(c, p, 12f, 30f, 1.6f, 1.2f, c(0xFFF2A3B3))
            },
            tilePainter { c, p, r ->   // 3: 단풍나무
            grassBase(c, p, r)
            p.color = Color.argb(52, 26, 46, 28)
            c.drawOval(RectF(5f, 24f, 29f, 31f), p)
            px(c, p, 13.6f, 16f, 6.4f, 15f, c(0xFF5D3A20))
            px(c, p, 14.8f, 16f, 2.8f, 15f, c(0xFF7A4E2B))
            px(c, p, 12.6f, 28f, 2.8f, 3.4f, c(0xFF5D3A20))
            px(c, p, 18.2f, 28f, 2.8f, 3.4f, c(0xFF5D3A20))
            p.color = c(0xFFB23F44)
            c.drawCircle(16f, 11.5f, 11.6f, p)
            p.color = c(0xFFD9534F)
            c.drawCircle(14.5f, 10f, 9.6f, p)
            c.drawCircle(21.5f, 13.5f, 6.8f, p)
            p.color = c(0xFFE8823C)
            c.drawCircle(11.5f, 8f, 5.8f, p)
            c.drawCircle(18f, 8.6f, 4.6f, p)
            p.color = c(0xFFF2A34E)
            c.drawCircle(10.2f, 6.6f, 3.2f, p)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFF9A2F35), 10, 1f, 1.8f)
            noise(c, p, r, 4f, 2f, 28f, 22f, c(0xFFF7CE5B), 7, 1f, 1.5f)
            px(c, p, 7f, 27f, 2f, 1.4f, c(0xFFE8823C))
            px(c, p, 23f, 29f, 2f, 1.4f, c(0xFFD9534F))
        })

        // ================= ROCK (2종) =================
        add(
            tilePainter { c, p, r ->   // 0: 큰 바위
                grassBase(c, p, r)
                p.color = Color.argb(56, 26, 46, 28)
                c.drawOval(RectF(4f, 24f, 28f, 31f), p)
                // 바위 본체
                px(c, p, 5f, 11f, 22f, 16f, c(0xFF5A626C))
                px(c, p, 6f, 9f, 18f, 16f, c(0xFF7C8590))
                px(c, p, 7.4f, 8f, 13f, 12f, c(0xFF9AA3AD))
                px(c, p, 9f, 9f, 8f, 6f, c(0xFFB5BDC6))
                px(c, p, 10.4f, 9.6f, 4f, 2.6f, c(0xFFD0D7DE))
                // 단면 음영
                px(c, p, 5f, 22f, 22f, 5f, c(0xFF4A5158))
                px(c, p, 21f, 12f, 6f, 14f, c(0xFF4A5158))
                // 균열
                px(c, p, 14f, 12f, 1.2f, 8f, c(0xFF3E454C))
                px(c, p, 15f, 18f, 1.2f, 5f, c(0xFF3E454C))
                px(c, p, 11f, 19f, 4f, 1.2f, c(0xFF3E454C))
                // 이끼
                px(c, p, 7f, 10f, 5f, 2.2f, c(0xFF6FAE57))
                px(c, p, 8.4f, 8.6f, 3f, 1.6f, c(0xFF8CC46C))
                noise(c, p, r, 6f, 9f, 22f, 22f, c(0xFF6B747E), 6, 1f, 1.6f)
                // 밑에 자갈
                px(c, p, 25f, 25f, 3f, 2.4f, c(0xFF8A949E))
                px(c, p, 25.4f, 25.4f, 1.6f, 1.2f, c(0xFFB0BAC2))
            },
            tilePainter { c, p, r ->   // 1: 바위 무리
                grassBase(c, p, r)
                p.color = Color.argb(52, 26, 46, 28)
                c.drawOval(RectF(6f, 23f, 26f, 30f), p)
                px(c, p, 8f, 15f, 16f, 11f, c(0xFF6B747E))
                px(c, p, 9f, 13f, 13f, 11f, c(0xFF8A949E))
                px(c, p, 10.4f, 14f, 8f, 5f, c(0xFFA5B0BA))
                px(c, p, 11.6f, 14.6f, 4f, 2.2f, c(0xFFC2CBD3))
                px(c, p, 8f, 22f, 16f, 4f, c(0xFF4A5158))
                // 작은 바위
                px(c, p, 22f, 21f, 6f, 6f, c(0xFF7C8590))
                px(c, p, 23f, 21.6f, 3.4f, 2.6f, c(0xFFA5B0BA))
                px(c, p, 3f, 22f, 5f, 5f, c(0xFF6B747E))
                px(c, p, 3.8f, 22.6f, 2.6f, 2f, c(0xFF9AA3AD))
                // 이끼
                px(c, p, 9f, 14f, 4f, 1.8f, c(0xFF6FAE57))
                px(c, p, 18f, 19f, 3f, 1.6f, c(0xFF5D8A4A))
                noise(c, p, r, 8f, 13f, 24f, 24f, c(0xFF5D6772), 5, 1f, 1.4f)
            }
        )

        // ================= MOUNTAIN (2종) =================
        add(
            tilePainter { c, p, r ->   // 0: 바위 절벽 (층리)
                fill(c, p, c(0xFF77848F))
                vgrad(c, p, 0f, 0f, 32f, 32f, c(0xFF8D9AA8), c(0xFF5D6772), 5)
                // 지층 밴드
                px(c, p, 0f, 7f, 32f, 1.6f, c(0xFF6B7580))
                px(c, p, 0f, 15.6f, 32f, 1.8f, c(0xFF6B7580))
                px(c, p, 0f, 24.6f, 32f, 1.6f, c(0xFF525B66))
                px(c, p, 0f, 8.6f, 32f, 1f, c(0xFFA5B2BD))
                px(c, p, 0f, 17.4f, 32f, 1f, c(0xFFA5B2BD))
                // 절단면 하이라이트
                px(c, p, 3f, 2f, 9f, 3f, c(0xFFB5BDC6))
                px(c, p, 21f, 11f, 7f, 2.6f, c(0xFFB5BDC6))
                px(c, p, 6f, 19f, 6f, 2.2f, c(0xFF9AA3AD))
                // 음영 (오른쪽)
                p.color = Color.argb(52, 20, 26, 34)
                c.drawRect(22f, 0f, 32f, 32f, p)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF68727E), 12, 1f, 2.2f)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFA0ACB8), 8, 1f, 1.8f)
                // 자갈
                px(c, p, 4f, 28f, 3f, 2.2f, c(0xFF8A949E))
                px(c, p, 14f, 29f, 2.4f, 1.8f, c(0xFF6B7580))
            },
            tilePainter { c, p, r ->   // 1: 눈 덮인 봉우리
                fill(c, p, c(0xFF77848F))
                vgrad(c, p, 0f, 0f, 32f, 32f, c(0xFF8D9AA8), c(0xFF5D6772), 5)
                px(c, p, 0f, 12f, 32f, 1.8f, c(0xFF6B7580))
                px(c, p, 0f, 21f, 32f, 1.6f, c(0xFF525B66))
                px(c, p, 0f, 13.8f, 32f, 1f, c(0xFFA5B2BD))
                // 눈
                px(c, p, 2f, 1f, 28f, 7f, c(0xFFE8EEF2))
                px(c, p, 0f, 4f, 32f, 5f, c(0xFFDCE5EC))
                dither(c, p, 0f, 8f, 32f, 11f, c(0xFFE8EEF2), 2f)
                px(c, p, 4f, 2f, 10f, 2.4f, c(0xFFF8FBFD))
                px(c, p, 20f, 5f, 8f, 2f, c(0xFFF8FBFD))
                // 바위 노출부
                px(c, p, 6f, 12f, 8f, 6f, c(0xFF9AA3AD))
                px(c, p, 19f, 16f, 9f, 5f, c(0xFF8A949E))
                p.color = Color.argb(52, 20, 26, 34)
                c.drawRect(23f, 0f, 32f, 32f, p)
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF68727E), 10, 1f, 2f)
            }
        )

        // ================= BLDG_WALL (도시 건물 벽돌) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFC9BFA8))
            val brickTones = intArrayOf(c(0xFFE0D2B4), c(0xFFE9E2D3), c(0xFFD8CFBA), c(0xFFDDD2B8))
            for (row in 0 until 5) {
                val y = row * 6.6f
                val off = if (row % 2 == 0) 0f else -8f
                var x = off
                while (x < 32f) {
                    val t = brickTones[(r.nextInt(brickTones.size))]
                    val w = 15.2f
                    px(c, p, x + 0.6f, y + 0.6f, w, 5.4f, t)
                    px(c, p, x + 0.6f, y + 0.6f, w, 1f, shade(t, 1.08f))
                    px(c, p, x + 0.6f, y + 5.2f, w, 0.8f, shade(t, 0.88f))
                    x += w + 1.2f
                }
            }
            // 하단 그을음 / 상단 하이라이트
            p.color = Color.argb(30, 60, 50, 30)
            c.drawRect(0f, 27f, 32f, 32f, p)
            p.color = Color.argb(24, 255, 250, 230)
            c.drawRect(0f, 0f, 32f, 2f, p)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFB5A88F), 6, 1f, 1.4f)
        })

        // ================= BLDG_WIN (2종 창문) =================
        val curtains = intArrayOf(c(0xFFF2D06B), c(0xFFC3A3E8))
        add(*Array(2) { i ->
            tilePainter { c, p, r ->
                // 벽돌 배경 (작게)
                fill(c, p, c(0xFFD8CFBA))
                noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFE0D2B4), 10, 1f, 2f)
                px(c, p, 0f, 30f, 32f, 2f, c(0xFFC9BFA8))
                // 창 프레임
                px(c, p, 5f, 4f, 22f, 23f, c(0xFF8A7B62))
                px(c, p, 6f, 5f, 20f, 21f, c(0xFFB0A188))
                // 유리 (하늘 반사 그라데이션)
                vgrad(c, p, 7f, 6f, 25f, 25f, c(0xFF8FC3E2), c(0xFF5E9FC8), 4)
                // 반사 스티크
                px(c, p, 8f, 7f, 5f, 5f, c(0xFFC3E4F2))
                px(c, p, 14f, 6.4f, 3f, 12f, Color.argb(90, 255, 255, 255))
                px(c, p, 19f, 8f, 2f, 9f, Color.argb(70, 255, 255, 255))
                // 커튼
                val cur = curtains[i]
                px(c, p, 7f, 6f, 3.6f, 19f, cur)
                px(c, p, 7f, 6f, 1.4f, 19f, shade(cur, 1.16f))
                px(c, p, 9.4f, 6f, 1.2f, 19f, shade(cur, 0.82f))
                px(c, p, 21.4f, 6f, 3.6f, 19f, cur)
                px(c, p, 21.4f, 6f, 1.2f, 19f, shade(cur, 0.82f))
                // 창살
                px(c, p, 15.2f, 5f, 1.6f, 21f, c(0xFF8A7B62))
                px(c, p, 7f, 14.6f, 18f, 1.6f, c(0xFF8A7B62))
                // 창틀 하이라이트
                px(c, p, 5f, 4f, 22f, 1.2f, c(0xFFD9C9A7))
                px(c, p, 5f, 25.8f, 22f, 1.2f, c(0xFF6B5E48))
                // 화분
                px(c, p, 24.4f, 22f, 5f, 4f, c(0xFFB5673F))
                px(c, p, 25f, 23f, 3.8f, 2.6f, c(0xFF9A5232))
                px(c, p, 25.4f, 19.6f, 3f, 2.6f, c(0xFF6FAE57))
                px(c, p, 26.4f, 18.2f, 1.6f, 1.8f, c(0xFFF2A3B3))
            }
        })

        // ================= BLDG_ROOF (도시 옥상 슬레이트) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF5D6772))
            // 슐레이트 행 (겹침)
            for (row in 0 until 4) {
                val y = row * 8f
                val off = if (row % 2 == 0) 0f else -8f
                var x = off
                while (x < 32f) {
                    val t = lerpColor(c(0xFF77848F), c(0xFF525B66), r.nextFloat())
                    px(c, p, x + 0.4f, y + 0.4f, 15.2f, 7.2f, t)
                    px(c, p, x + 0.4f, y + 0.4f, 15.2f, 1.2f, shade(t, 1.22f))
                    px(c, p, x + 0.4f, y + 6.2f, 15.2f, 1.4f, shade(t, 0.72f))
                    x += 15.6f
                }
            }
            // 이끼 자국
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF6E8262), 5, 1f, 1.8f)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF8A949E), 7, 1f, 1.5f)
        })

        // ================= HOUSE_ROOF (기와집 지붕) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFB2583F))
            for (row in 0 until 4) {
                val y = row * 8f
                val off = if (row % 2 == 0) 0f else -6f
                var x = off
                while (x < 32f) {
                    val t = lerpColor(c(0xFFD4694A), c(0xFFC96A4D), r.nextFloat())
                    px(c, p, x + 0.4f, y + 0.6f, 11.2f, 7f, t)
                    px(c, p, x + 0.4f, y + 0.6f, 11.2f, 1.2f, shade(t, 1.18f))
                    px(c, p, x + 0.4f, y + 6.4f, 11.2f, 1.2f, shade(t, 0.76f))
                    px(c, p, x + 8.4f, y + 2f, 1.2f, 5.6f, shade(t, 0.88f))
                    x += 11.6f
                }
            }
            // 초록 이끼 + 하이라이트
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFF8FA86B), 4, 1f, 1.6f)
            px(c, p, 0f, 0f, 32f, 1.2f, c(0xFFE08A67))
        })

        // ================= HOUSE_WALL (집 외벽) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            // 스터코 노이즈
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDE0C0), 14, 1f, 1.8f)
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFFBF3DC), 10, 1f, 1.6f)
            // 수직 사이딩 라인
            for (x in 0 until 32 step 8) {
                px(c, p, x.toFloat(), 0f, 1.2f, 32f, c(0xFFE0C9A2))
                px(c, p, x + 1.2f, 0f, 0.8f, 32f, c(0xFFFBF3DC))
            }
            // 상단 트림 / 하단 기초
            px(c, p, 0f, 0f, 32f, 2.2f, c(0xFFC9A87B))
            px(c, p, 0f, 2.2f, 32f, 1f, c(0xFFE8D5AE))
            px(c, p, 0f, 28f, 32f, 4f, c(0xFFC9A87B))
            px(c, p, 0f, 28f, 32f, 1.2f, c(0xFF8A6A4F))
            noise(c, p, r, 0f, 28f, 32f, 32f, c(0xFFB08A5C), 5, 1f, 1.5f)
        })

        // ================= HOUSE_WIN (꽃상자 창문) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDE0C0), 10, 1f, 1.8f)
            px(c, p, 0f, 0f, 32f, 2.2f, c(0xFFC9A87B))
            // 나무 창틀
            px(c, p, 5f, 3.6f, 22f, 19f, c(0xFF8A5A33))
            px(c, p, 6f, 4.6f, 20f, 17f, c(0xFFA87B4F))
            vgrad(c, p, 7f, 5.6f, 25f, 21f, c(0xFFB9DDF0), c(0xFF7FB6D9), 4)
            px(c, p, 8f, 6.4f, 6f, 5f, c(0xFFE0F2FA))
            px(c, p, 14.2f, 5f, 1.4f, 16.4f, c(0xFF8A5A33))
            px(c, p, 7f, 12.6f, 18f, 1.4f, c(0xFF8A5A33))
            px(c, p, 5f, 22.6f, 22f, 1.6f, c(0xFFC9A87B))
            // 꽃상자
            px(c, p, 4.6f, 24f, 22.8f, 5.4f, c(0xFF8A5A33))
            px(c, p, 5.8f, 25f, 20.4f, 3.2f, c(0xFFA87B4F))
            px(c, p, 4.6f, 24f, 22.8f, 1.2f, c(0xFF6B431F))
            val fcols = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFC3A3E8), c(0xFFFDFDF8))
            for (k in 0 until 5) {
                val fx = 6f + k * 4.2f
                val fc = fcols[k % 4]
                px(c, p, fx, 21.2f, 2.8f, 2.8f, fc)
                px(c, p, fx + 0.6f, 21.6f, 1.4f, 1.2f, shade(fc, 1.2f))
                px(c, p, fx + 1f, 23.8f, 1f, 1.6f, c(0xFF5D8A4A))
            }
        })

        // ================= HOUSE_DOOR (현관) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFEDE0C0), 8, 1f, 1.8f)
            // 문 프레임
            px(c, p, 3f, 1.6f, 26f, 30f, c(0xFF6B431F))
            px(c, p, 4.2f, 2.8f, 23.6f, 28.8f, c(0xFF8A5A33))
            // 문짝
            px(c, p, 5.6f, 4f, 20.8f, 27.4f, c(0xFFA87B4F))
            px(c, p, 6.8f, 5.2f, 18.4f, 25f, c(0xFFC89B6A))
            // 패널
            px(c, p, 8.4f, 7f, 15.2f, 8.4f, c(0xFFA87B4F))
            px(c, p, 9.2f, 7.8f, 13.6f, 6.8f, c(0xFFB98A5C))
            px(c, p, 8.4f, 17.4f, 15.2f, 9.6f, c(0xFFA87B4F))
            px(c, p, 9.2f, 18.2f, 13.6f, 8f, c(0xFFB98A5C))
            // 유리창
            px(c, p, 10.4f, 8.6f, 11.2f, 4.6f, c(0xFF9FD0E8))
            px(c, p, 10.4f, 8.6f, 5f, 2.2f, c(0xFFC3E4F2))
            // 손잡이
            px(c, p, 21.4f, 16f, 2.6f, 2.6f, c(0xFFF2D06B))
            px(c, p, 22f, 16.4f, 1.2f, 1.2f, c(0xFFFFFBE0))
            // 현관 매트 + 발판
            px(c, p, 6f, 29f, 20f, 3f, c(0xFFB23F44))
            noise(c, p, r, 6f, 29f, 26f, 32f, c(0xFF8A2F35), 5, 1f, 1.3f)
        })

        // ================= TUNNEL (터널 입구) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF77848F))
            vgrad(c, p, 0f, 0f, 32f, 10f, c(0xFF8D9AA8), c(0xFF68727E), 3)
            noise(c, p, r, 0f, 0f, 32f, 9f, c(0xFF9AA3AD), 8, 1f, 2f)
            // 아치 석조
            p.color = c(0xFF6B4F35)
            c.drawRect(3f, 8f, 29f, 32f, p)
            val path = Path()
            p.color = c(0xFF5A3F28)
            path.moveTo(16f, 2f); path.lineTo(29f, 12f); path.lineTo(29f, 32f)
            path.lineTo(3f, 32f); path.lineTo(3f, 12f); path.close()
            c.drawPath(path, p)
            // 아치 voussoirs (쐐기돌)
            p.color = c(0xFF8A5A33)
            for (k in 0 until 7) {
                val ang = Math.PI * (0.12 + k * 0.126)
                val cx = 16f + 12f * Math.cos(ang).toFloat()
                val cy = 12f + 9.5f * Math.sin(ang).toFloat()
                c.drawRect(cx - 1.8f, cy - 2.2f, cx + 1.8f, cy + 2.2f, p)
            }
            px(c, p, 3f, 12f, 3.6f, 20f, c(0xFF8A5A33))
            px(c, p, 25.4f, 12f, 3.6f, 20f, c(0xFF8A5A33))
            px(c, p, 4f, 12f, 1.2f, 20f, c(0xFFA87B4F))
            // 내부 어둠 (그라데이션)
            vgrad(c, p, 7.4f, 9f, 24.6f, 32f, c(0xFF2E2E3A), c(0xFF0E0E16), 4)
            // 도로
            px(c, p, 7.4f, 27f, 17.2f, 5f, c(0xFF8A8074))
            px(c, p, 7.4f, 27f, 17.2f, 1.2f, c(0xFFA39888))
            px(c, p, 15f, 28.4f, 2f, 2.4f, c(0xFFE8DFC8))
            // 입구 등
            px(c, p, 13.4f, 2f, 5.2f, 4.6f, c(0xFF3A3F4A))
            px(c, p, 14.2f, 2.8f, 3.6f, 3f, c(0xFFF7E9A8))
            px(c, p, 15f, 3.2f, 2f, 2f, c(0xFFFFFBE0))
        })

        // ================= FLOOR (실내 나무 바닥 2종) =================
        add(*Array(2) { i ->
            tilePainter { c, p, r ->
                val base = if (i == 0) c(0xFFCDA775) else c(0xFFC49E6C)
                fill(c, p, base)
                // 널빤지 2줄
                val seam = if (i == 0) 14f else 20f
                for (row in 0..1) {
                    val y0 = row * 16f
                    val off = if (row == 0) 0f else seam
                    px(c, p, 0f, y0, 32f, 15.2f, shade(base, 1f + row * 0.02f))
                    // 나뭇결
                    for (k in 0 until 4) {
                        val gy = y0 + 2.2f + k * 3.4f + r.nextFloat() * 1.4f
                        p.color = shade(base, 0.9f)
                        c.drawRect(0f, gy, 32f, gy + 0.9f, p)
                        p.color = shade(base, 1.1f)
                        c.drawRect(0f, gy + 0.9f, 32f, gy + 1.5f, p)
                    }
                    // 옹이
                    px(c, p, off + 5f, y0 + 5f, 2.6f, 1.8f, shade(base, 0.82f))
                    px(c, p, off + 21f, y0 + 9f, 2f, 1.4f, shade(base, 0.82f))
                    // 널빤지 이음새
                    px(c, p, 0f, y0 + 15.2f, 32f, 1.4f, shade(base, 0.78f))
                    px(c, p, 0f, y0 + 15.2f, 32f, 0.6f, shade(base, 1.12f))
                    px(c, p, off + 15f, y0, 1.2f, 15.2f, shade(base, 0.8f))
                }
            }
        })

        // ================= WALL_IN (실내 벽지) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            // 벽지 줄무늬
            for (x in 0 until 32 step 6) {
                px(c, p, x.toFloat(), 3f, 2.6f, 23f, c(0xFFE8D5AE))
                px(c, p, x + 2.6f, 3f, 1f, 23f, c(0xFFFBF3DC))
            }
            noise(c, p, r, 0f, 3f, 32f, 26f, c(0xFFEDE0C0), 8, 1f, 1.4f)
            // 천장 몰딩 / 의자 레일 / 걸레받이
            px(c, p, 0f, 0f, 32f, 3.2f, c(0xFFC9A87B))
            px(c, p, 0f, 2.2f, 32f, 1f, c(0xFFE8D5AE))
            px(c, p, 0f, 22f, 32f, 1.8f, c(0xFFC9A87B))
            px(c, p, 0f, 23.8f, 32f, 0.8f, c(0xFF8A6A4F))
            px(c, p, 0f, 26f, 32f, 6f, c(0xFFE0C9A2))
            px(c, p, 0f, 26f, 32f, 1.2f, c(0xFFB08A5C))
        })

        // ================= WALL_WIN (실내 창) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            for (x in 0 until 32 step 6) {
                px(c, p, x.toFloat(), 3f, 2.6f, 20f, c(0xFFE8D5AE))
            }
            px(c, p, 0f, 0f, 32f, 3.2f, c(0xFFC9A87B))
            // 창
            px(c, p, 5.4f, 5f, 21.2f, 18f, c(0xFF8A5A33))
            px(c, p, 6.6f, 6.2f, 18.8f, 15.6f, c(0xFFA8D8E8))
            vgrad(c, p, 7.2f, 6.8f, 24.8f, 21.2f, c(0xFFC9E8F2), c(0xFF8CC46C), 3)
            px(c, p, 8f, 7.4f, 6f, 4f, c(0xFFE8F6FA))
            px(c, p, 14.4f, 6f, 1.4f, 16f, c(0xFF8A5A33))
            px(c, p, 7f, 13.4f, 18f, 1.4f, c(0xFF8A5A33))
            // 화분
            px(c, p, 8f, 17.6f, 5.4f, 3.6f, c(0xFFB5673F))
            px(c, p, 8.8f, 15.2f, 3.8f, 2.6f, c(0xFF6FAE57))
            px(c, p, 9.8f, 13.8f, 1.8f, 1.8f, c(0xFFF2A3B3))
            // 커튼
            px(c, p, 4.4f, 5f, 2.6f, 17f, c(0xFFE8867A))
            px(c, p, 4.4f, 5f, 1f, 17f, c(0xFFF2A3B3))
            px(c, p, 25f, 5f, 2.6f, 17f, c(0xFFE8867A))
            px(c, p, 26.6f, 5f, 1f, 17f, c(0xFFD96C64))
            px(c, p, 0f, 23f, 32f, 9f, c(0xFFE0C9A2))
            px(c, p, 0f, 23f, 32f, 1.2f, c(0xFFB08A5C))
        })

        // ================= OVEN (화덕 2프레임 불꽃) =================
        add(*Array(2) { f ->
            tilePainter { c, p, r ->
                // 벽돌 화덕
                fill(c, p, c(0xFF8F8F99))
                for (row in 0 until 3) {
                    val y = row * 5.4f
                    val off = if (row % 2 == 0) 0f else -6f
                    var x = off
                    while (x < 32f) {
                        val t = lerpColor(c(0xFFA8A8B2), c(0xFF7A7A85), r.nextFloat())
                        px(c, p, x + 0.4f, y + 0.4f, 10.8f, 4.6f, t)
                        px(c, p, x + 0.4f, y + 0.4f, 10.8f, 0.9f, shade(t, 1.18f))
                        px(c, p, x + 0.4f, y + 4.3f, 10.8f, 0.9f, shade(t, 0.78f))
                        x += 11.2f
                    }
                }
                px(c, p, 0f, 16f, 32f, 16f, c(0xFF7A7A85))
                for (row in 0 until 3) {
                    val y = 17f + row * 5f
                    val off = if (row % 2 == 0) 0f else -5f
                    var x = off
                    while (x < 32f) {
                        val t = lerpColor(c(0xFF9A9AA6), c(0xFF6B6B78), r.nextFloat())
                        px(c, p, x + 0.4f, y + 0.4f, 9.6f, 4.2f, t)
                        px(c, p, x + 0.4f, y + 0.4f, 9.6f, 0.9f, shade(t, 1.16f))
                        x += 10f
                    }
                }
                // 아치 화구
                p.color = c(0xFF23232B)
                c.drawCircle(16f, 22f, 10.2f, p)
                p.color = c(0xFF33333D)
                c.drawCircle(16f, 22f, 8.8f, p)
                // 불꽃 (프레임 애니메이션)
                val jig = if (f == 0) 0f else 1.4f
                px(c, p, 9.4f, 19f, 13.2f, 8f, c(0xFFE2574C))
                px(c, p, 11f, 17f + jig, 10f, 8f, c(0xFFF2913C))
                px(c, p, 12.6f, 15.6f - jig, 6.8f, 7.4f, c(0xFFF7CE5B))
                px(c, p, 14.2f, 17.4f + jig * 0.6f, 3.6f, 5f, c(0xFFFDF6E8))
                // 불티
                dot(c, p, 12f - jig, 13f, c(0xFFF2913C))
                dot(c, p, 19.4f + jig, 14.4f, c(0xFFF7CE5B))
                dot(c, p, 16.6f, 11.6f - jig, c(0xFFE2574C))
                // 장작
                px(c, p, 8.6f, 26.4f, 14.8f, 2.6f, c(0xFF6B431F))
                px(c, p, 9.6f, 26.8f, 12.8f, 1.2f, c(0xFF8A5A33))
                // 따뜻한 빛 (입구)
                p.color = Color.argb(46, 255, 160, 80)
                c.drawCircle(16f, 22f, 7f, p)
                // 테두리
                bevel(c, p, 0f, 0f, 32f, 32f, c(0xFFA8A8B2), c(0xFF5A5A66), 1.2f)
            }
        })

        // ================= BED (침대) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            // 프레임
            px(c, p, 1.6f, 1.6f, 28.8f, 28.8f, c(0xFF6B431F))
            px(c, p, 2.8f, 2.8f, 26.4f, 26.4f, c(0xFFB5651D))
            px(c, p, 2.8f, 2.8f, 26.4f, 1.4f, c(0xFFD9884A))
            // 이불 (패치워크)
            px(c, p, 4.2f, 8f, 23.6f, 19.4f, c(0xFFE8867A))
            for (gy in 0 until 3) {
                for (gx in 0 until 4) {
                    val t = if ((gx + gy) % 2 == 0) c(0xFFE8867A) else c(0xFFD96C64)
                    px(c, p, 4.2f + gx * 6f, 8f + gy * 6.6f, 5.6f, 6.2f, t)
                    px(c, p, 4.2f + gx * 6f, 8f + gy * 6.6f, 5.6f, 1f, shade(t, 1.12f))
                }
            }
            // 이불 주름
            px(c, p, 4.2f, 14f, 23.6f, 1.2f, c(0xFFB23F44))
            px(c, p, 4.2f, 21f, 23.6f, 1.2f, c(0xFFB23F44))
            // 베개
            px(c, p, 5f, 3.8f, 11f, 6f, c(0xFFF5EFE0))
            px(c, p, 5.8f, 4.4f, 9.4f, 4.2f, c(0xFFFFFBF2))
            px(c, p, 6.6f, 6.4f, 7.8f, 1f, c(0xFFE0D8C4))
            // 쿠션
            px(c, p, 18f, 10.6f, 9.4f, 3.4f, c(0xFFF7B2A8))
            px(c, p, 18.6f, 11f, 8.2f, 1.6f, c(0xFFFFD0C8))
        })

        // ================= BOX (이사 박스) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            noise(c, p, r, 0f, 0f, 32f, 32f, c(0xFFB98F5E), 6, 1f, 1.6f)
            // 박스
            px(c, p, 2f, 3.6f, 28f, 26.4f, c(0xFFC89B6A))
            px(c, p, 2f, 3.6f, 28f, 1.6f, c(0xFFD9B182))
            px(c, p, 2f, 28.4f, 28f, 1.6f, c(0xFFA87B4F))
            px(c, p, 2f, 3.6f, 1.6f, 26.4f, c(0xFFD9B182))
            px(c, p, 28.4f, 3.6f, 1.6f, 26.4f, c(0xFF9A6E42))
            // 테이프
            px(c, p, 13f, 3.6f, 6f, 26.4f, c(0xFFE8D5A3))
            px(c, p, 13f, 3.6f, 1.2f, 26.4f, c(0xFFD9C48E))
            px(c, p, 2f, 14f, 28f, 3.2f, c(0xFFE8D5A3))
            // 라벨
            px(c, p, 18f, 18f, 9.4f, 7f, c(0xFFF5EFE0))
            px(c, p, 19f, 19.4f, 6.4f, 1f, c(0xFF8A6A4F))
            px(c, p, 19f, 21.2f, 7.2f, 1f, c(0xFF8A6A4F))
            px(c, p, 19f, 23f, 4.6f, 1f, c(0xFF8A6A4F))
            // 취급주의 화살표
            px(c, p, 5.6f, 19f, 1.4f, 6f, c(0xFF7A5A33))
            px(c, p, 4.6f, 19f, 3.4f, 1.4f, c(0xFF7A5A33))
            px(c, p, 7f, 20.6f, 3.4f, 1.2f, c(0xFF7A5A33))
            px(c, p, 5.6f, 8f, 1.4f, 6f, c(0xFF7A5A33))
            px(c, p, 4.6f, 8f, 3.4f, 1.4f, c(0xFF7A5A33))
            px(c, p, 7f, 9.6f, 3.4f, 1.2f, c(0xFF7A5A33))
        })

        // ================= DECOR (장식 슬롯) =================
        add(tilePainter { c, p, r ->
            val base = c(0xFFCDA775)
            fill(c, p, base)
            for (row in 0..1) {
                val y0 = row * 16f
                for (k in 0 until 4) {
                    val gy = y0 + 2.2f + k * 3.4f
                    p.color = shade(base, 0.9f)
                    c.drawRect(0f, gy, 32f, gy + 0.9f, p)
                    p.color = shade(base, 1.1f)
                    c.drawRect(0f, gy + 0.9f, 32f, gy + 1.5f, p)
                }
                px(c, p, 0f, y0 + 15.2f, 32f, 1.4f, shade(base, 0.78f))
            }
            // 부드러운 스팟라이트
            p.color = Color.argb(30, 255, 250, 220)
            c.drawCircle(16f, 16f, 13f, p)
            p.color = Color.argb(22, 255, 250, 220)
            c.drawCircle(16f, 16f, 9f, p)
            // 점선 슬롯
            p.color = c(0xFFB08A5C)
            for (i2 in 0 until 13) {
                c.drawRect((4 + i2 * 2).toFloat(), 4f, (5 + i2 * 2).toFloat(), 5.4f, p)
                c.drawRect((4 + i2 * 2).toFloat(), 26.6f, (5 + i2 * 2).toFloat(), 28f, p)
                c.drawRect(4f, (4 + i2 * 2).toFloat(), 5.4f, (5 + i2 * 2).toFloat(), p)
                c.drawRect(26.6f, (4 + i2 * 2).toFloat(), 28f, (5 + i2 * 2).toFloat(), p)
            }
            // 모서리 브래킷
            px(c, p, 3f, 3f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 3f, 3f, 1.4f, 4f, c(0xFFE8D5A3))
            px(c, p, 25f, 3f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 27.6f, 3f, 1.4f, 4f, c(0xFFE8D5A3))
            px(c, p, 3f, 27.6f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 3f, 25f, 1.4f, 4f, c(0xFFE8D5A3))
            px(c, p, 25f, 27.6f, 4f, 1.4f, c(0xFFE8D5A3))
            px(c, p, 27.6f, 25f, 1.4f, 4f, c(0xFFE8D5A3))
        })

        // ================= SIGN (이정표) =================
        add(tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = Color.argb(56, 26, 46, 28)
            c.drawOval(RectF(8f, 26f, 26f, 31f), p)
            // 기둥
            px(c, p, 14f, 12f, 5f, 18f, c(0xFF6B431F))
            px(c, p, 15f, 12f, 2.4f, 18f, c(0xFF8A5A33))
            px(c, p, 15.6f, 13f, 1f, 15f, c(0xFFA87B4F))
            // 판자
            px(c, p, 3.6f, 3f, 25.8f, 12.4f, c(0xFF6B431F))
            px(c, p, 4.8f, 4.2f, 23.4f, 10f, c(0xFFC89B6A))
            px(c, p, 4.8f, 4.2f, 23.4f, 1.2f, c(0xFFDBB684))
            px(c, p, 4.8f, 13f, 23.4f, 1.2f, c(0xFF8A5A33))
            // 나뭇결
            for (k in 0 until 3) {
                val gy = 6f + k * 2.4f
                px(c, p, 6f, gy, 20f, 0.8f, c(0xFFB08A5C))
            }
            // 글씨 줄
            px(c, p, 7f, 6.4f, 11f, 1.6f, c(0xFF4A3728))
            px(c, p, 7f, 9.6f, 8f, 1.4f, c(0xFF4A3728))
            // 화살표
            val path = Path()
            p.color = c(0xFF4A3728)
            path.moveTo(21f, 6.6f); path.lineTo(25.6f, 9.6f); path.lineTo(21f, 12.4f)
            path.close()
            c.drawPath(path, p)
            px(c, p, 18.4f, 8.6f, 3.4f, 2f, c(0xFF4A3728))
            // 못
            dot(c, p, 5.6f, 5f, c(0xFF33241C))
            dot(c, p, 26.4f, 5f, c(0xFF33241C))
            dot(c, p, 5.6f, 12.4f, c(0xFF33241C))
            dot(c, p, 26.4f, 12.4f, c(0xFF33241C))
            // 이끼
            px(c, p, 4f, 13.4f, 5f, 1.2f, c(0xFF6FAE57))
        })

        // ================= BENCH (벤치) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            // 석판 배경 (광장)
            for (gx in 0 until 2) {
                for (gy in 0 until 2) {
                    val x = gx * 16f + 1.6f
                    val y = gy * 16f + 1.6f
                    px(c, p, x, y, 12.8f, 12.8f, c(0xFFE9DCBC))
                    bevel(c, p, x, y, x + 12.8f, y + 12.8f, c(0xFFF1E6C8), c(0xFFB8A87F), 1.2f)
                }
            }
            // 그림자
            p.color = Color.argb(50, 40, 36, 24)
            c.drawOval(RectF(2f, 22f, 30f, 30f), p)
            // 등받이
            px(c, p, 3f, 4f, 26f, 2.6f, c(0xFF6B431F))
            px(c, p, 3.8f, 4.4f, 24.4f, 1.6f, c(0xFFC89B6A))
            px(c, p, 3.8f, 4.4f, 24.4f, 0.8f, c(0xFFDBB684))
            px(c, p, 3f, 8.4f, 26f, 2.2f, c(0xFF6B431F))
            px(c, p, 3.8f, 8.8f, 24.4f, 1.3f, c(0xFFA87B4F))
            // 등받이 기둥
            px(c, p, 5.4f, 3.6f, 2.6f, 12f, c(0xFF4A3320))
            px(c, p, 24f, 3.6f, 2.6f, 12f, c(0xFF4A3320))
            // 좌석
            px(c, p, 2f, 13.6f, 28f, 6f, c(0xFF6B431F))
            px(c, p, 2.8f, 14.2f, 26.4f, 4.2f, c(0xFFA87B4F))
            px(c, p, 2.8f, 14.2f, 26.4f, 1.2f, c(0xFFDBB684))
            // 나뭇결
            for (k in 0 until 3) {
                px(c, p, 4f, 15.2f + k * 1.4f, 24f, 0.7f, c(0xFF8A5A33))
            }
            // 다리 + 볼트
            px(c, p, 4f, 19.6f, 3.2f, 8f, c(0xFF4A3320))
            px(c, p, 24.8f, 19.6f, 3.2f, 8f, c(0xFF4A3320))
            dot(c, p, 5f, 15f, c(0xFF33241C))
            dot(c, p, 26f, 15f, c(0xFF33241C))
        })

        // ================= LAMP (가로등) =================
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            for (gx in 0 until 2) {
                for (gy in 0 until 2) {
                    val x = gx * 16f + 1.6f
                    val y = gy * 16f + 1.6f
                    px(c, p, x, y, 12.8f, 12.8f, c(0xFFE9DCBC))
                    bevel(c, p, x, y, x + 12.8f, y + 12.8f, c(0xFFF1E6C8), c(0xFFB8A87F), 1.2f)
                }
            }
            // 그림자
            p.color = Color.argb(50, 40, 36, 24)
            c.drawOval(RectF(8f, 27f, 24f, 31f), p)
            // 기둥
            px(c, p, 14f, 6f, 4.4f, 23f, c(0xFF3A3F4A))
            px(c, p, 15f, 6f, 1.6f, 23f, c(0xFF5A626C))
            px(c, p, 15.4f, 7f, 0.9f, 20f, c(0xFF7C8590))
            // 밑받침
            px(c, p, 11f, 28f, 10f, 2.6f, c(0xFF3A3F4A))
            px(c, p, 12f, 26.6f, 8f, 1.8f, c(0xFF4A5158))
            dot(c, p, 12f, 28.6f, c(0xFF23232B))
            dot(c, p, 19f, 28.6f, c(0xFF23232B))
            // 랜턴 머리
            px(c, p, 8.6f, 1.6f, 15f, 2f, c(0xFF3A3F4A))
            px(c, p, 9.8f, 3.2f, 12.6f, 4.6f, c(0xFF4A5158))
            px(c, p, 10.8f, 3.8f, 10.6f, 3.2f, c(0xFFF7E9A8))
            px(c, p, 12.4f, 4.2f, 7.4f, 2.2f, c(0xFFFFFBE0))
            px(c, p, 15.2f, 7.6f, 1.8f, 1.8f, c(0xFF23232B))
            // 유리창살
            px(c, p, 13.6f, 3.6f, 1f, 3.8f, c(0xFF3A3F4A))
            px(c, p, 17.6f, 3.6f, 1f, 3.8f, c(0xFF3A3F4A))
            px(c, p, 8.6f, 1f, 15f, 1.2f, c(0xFF23232B))
        })

        tiles = list.toTypedArray()
    }

    /** 타일 좌표 기반 변형 선택 */
    fun tileVariant(tileOrdinal: Int, x: Int, y: Int): Int {
        val n = tiles[tileOrdinal].size
        if (n <= 1) return 0
        return ((x * 7 + y * 13) % n + n) % n
    }

    // -----------------------------------------------------------------------
    // 아이콘
    // -----------------------------------------------------------------------

    private fun buildIcons() {
        val pal = mapOf(
            'c' to c(0xFFE8A75C), 'C' to c(0xFFF7CE5B), 'R' to c(0xFFE2574C),
            'd' to c(0xFFD18F4A), 'b' to c(0xFF6FAE57), 'W' to c(0xFFFDF6E8)
        )
        val pizza = listOf(
            "......................",
            ".....cccccccccc.....",
            "...ccCCCCCCCCCCcc...",
            "..cCCCRCCRCCRCCRCc..",
            "..cCCCCCCCCCCCCCd...",
            ".cCCRCCCCRCCCCCCCd..",
            ".cCCCCRCCCRCCRCCd...",
            ".cCCCCCCCCCCCCCCd...",
            ".cCCRCCCCRCCCCRAd...",
            ".cCCCCCCCCCCCCCd....",
            ".cCCRCCCCRCCCCCd....",
            ".dCCCCCCCCCCCAAd....",
            "..ddddddddddd......",
            "...dddddddddd......",
            "......................"
        )
        val pizzaBmp = sprite(pizza, pal + ('A' to c(0xFF7D9C4F)))
        pizzaIcon = pizzaBmp
        pizzaIconBig = Bitmap.createScaledBitmap(pizzaBmp, pizzaBmp.width * 4, pizzaBmp.height * 4, false)

        cloverIcon = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.isAntiAlias = true
            p.color = c(0xFF3F7D46)
            cv.drawCircle(4.4f, 4.4f, 3.6f, p)
            cv.drawCircle(9.6f, 4.4f, 3.6f, p)
            cv.drawCircle(4.4f, 9.6f, 3.6f, p)
            cv.drawCircle(9.6f, 9.6f, 3.6f, p)
            p.color = c(0xFF4F9E57)
            cv.drawCircle(4f, 4f, 2.8f, p)
            cv.drawCircle(9.6f, 7.6f, 2.8f, p)
            cv.drawCircle(7f, 10f, 2.6f, p)
            p.color = c(0xFF6BBA72)
            cv.drawCircle(3.4f, 3.4f, 1.4f, p)
            cv.drawCircle(9.8f, 9.2f, 1.2f, p)
        }

        cameraIcon = Bitmap.createBitmap(20, 16, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFF4A4A55)
            cv.drawRect(0f, 4f, 20f, 16f, p)
            cv.drawRect(6f, 1f, 13f, 4f, p)
            p.color = c(0xFF6B6B78)
            cv.drawRect(1f, 5f, 19f, 7f, p)
            p.color = c(0xFF23232B)
            cv.drawCircle(10f, 10f, 4.6f, p)
            p.color = c(0xFF8FC3E3)
            cv.drawCircle(10f, 10f, 3.4f, p)
            p.color = c(0xFFC3E4F2)
            cv.drawCircle(9f, 9f, 1.4f, p)
            p.color = c(0xFFF2D06B)
            cv.drawRect(16f, 8f, 18f, 10f, p)
            p.color = c(0xFFE2574C)
            cv.drawRect(2f, 8f, 4f, 10f, p)
        }

        houseIcon = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.color = c(0xFFB55338)
            cv.drawRect(1f, 0f, 13f, 5f, p)
            p.color = c(0xFFD4694A)
            cv.drawRect(1f, 0f, 13f, 2f, p)
            p.color = c(0xFFF6E7C6)
            cv.drawRect(1f, 5f, 13f, 14f, p)
            p.color = c(0xFF9FD0E8)
            cv.drawRect(3f, 6f, 6f, 9f, p)
            p.color = c(0xFF8A5A33)
            cv.drawRect(6f, 9f, 9f, 14f, p)
        }

        sunIcon = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.isAntiAlias = true
            p.color = c(0xFFF7CE5B)
            cv.drawCircle(8f, 8f, 4.6f, p)
            p.color = c(0xFFF7E9A8)
            cv.drawCircle(7f, 7f, 3f, p)
            p.color = c(0xFFF2B63C)
            p.strokeWidth = 1.6f
            for (i in 0 until 8) {
                val a = Math.PI * 2 * i / 8.0
                cv.drawLine(
                    (8 + Math.cos(a) * 5.6).toFloat(), (8 + Math.sin(a) * 5.6).toFloat(),
                    (8 + Math.cos(a) * 7.4).toFloat(), (8 + Math.sin(a) * 7.4).toFloat(), p
                )
            }
        }

        moonIcon = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            val p = Paint()
            p.isAntiAlias = true
            val path = Path()
            path.addCircle(7f, 7f, 6f, Path.Direction.CCW)
            path.addCircle(9.8f, 5.4f, 5f, Path.Direction.CW)
            p.color = c(0xFFF7E9A8)
            cv.drawPath(path, p)
            p.color = c(0xFFDFD08A)
            cv.drawCircle(5.4f, 8.4f, 1f, p)
            cv.drawCircle(4.6f, 5.8f, 0.7f, p)
        }
    }

    // -----------------------------------------------------------------------
    // 장식 아트 (32x32)
    // -----------------------------------------------------------------------

    private fun buildDecorArt() {
        val arts = ArrayList<Bitmap>()

        fun art(paint: (Canvas, Paint) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            paint(Canvas(b), Paint())
            return b
        }
        fun r(c: Canvas, p: Paint, l: Float, t: Float, rr: Float, b: Float, col: Int) {
            p.color = col; c.drawRect(l, t, rr, b, p)
        }

        // 0) 선인장 화분
        arts.add(art { c, p ->
            r(c, p, 10f, 21f, 22f, 29f, c(0xFFB5673F))
            r(c, p, 9f, 20f, 23f, 22f, c(0xFFC97B50))
            r(c, p, 11f, 22f, 21f, 28f, c(0xFFA85834))
            r(c, p, 13.4f, 9f, 18.6f, 21f, c(0xFF4F9E57))
            r(c, p, 14.4f, 10f, 16.2f, 20f, c(0xFF6BBA72))
            r(c, p, 9f, 12f, 12.4f, 18f, c(0xFF4F9E57))
            r(c, p, 9.8f, 12.8f, 11f, 17f, c(0xFF6BBA72))
            r(c, p, 19.6f, 14f, 23f, 20f, c(0xFF4F9E57))
            r(c, p, 21f, 15f, 22.2f, 19f, c(0xFF6BBA72))
            r(c, p, 14.8f, 5.4f, 17.2f, 8f, c(0xFFF2A3B3))
            r(c, p, 15.4f, 6f, 16.6f, 7.4f, c(0xFFFDF6E8))
        })
        // 1) 원목 책장
        arts.add(art { c, p ->
            r(c, p, 5f, 3f, 27f, 30f, c(0xFF6B431F))
            r(c, p, 6.4f, 4.4f, 25.6f, 28.6f, c(0xFF8A5A33))
            r(c, p, 6.4f, 12.4f, 25.6f, 14f, c(0xFF6B431F))
            r(c, p, 6.4f, 21f, 25.6f, 22.6f, c(0xFF6B431F))
            // 책들
            r(c, p, 8f, 8f, 10.4f, 12.4f, c(0xFFE2574C))
            r(c, p, 10.8f, 8.6f, 13.2f, 12.4f, c(0xFF3F6FB0))
            r(c, p, 13.6f, 8f, 15.4f, 12.4f, c(0xFFF2D06B))
            r(c, p, 16f, 8.8f, 19f, 12.4f, c(0xFF4F9E57))
            r(c, p, 20f, 8f, 21.6f, 12.4f, c(0xFFC3A3E8))
            r(c, p, 8f, 16.6f, 11.4f, 21f, c(0xFF3F6FB0))
            r(c, p, 11.8f, 17.2f, 14.4f, 21f, c(0xFFF2D06B))
            r(c, p, 15f, 16.6f, 18.6f, 21f, c(0xFFE2574C))
            r(c, p, 19.6f, 17.4f, 22.4f, 21f, c(0xFF4F9E57))
            r(c, p, 8.6f, 25f, 16f, 28.6f, c(0xFFC89B6A))
            r(c, p, 17f, 25.6f, 23.4f, 28.6f, c(0xFFA87B4F))
        })
        // 2) 러그
        arts.add(art { c, p ->
            val p2 = Paint()
            p2.isAntiAlias = true
            p2.color = c(0xFFB55338)
            c.drawOval(RectF(3f, 6f, 29f, 28f), p2)
            p2.color = c(0xFFD96C64)
            c.drawOval(RectF(5f, 8f, 27f, 26f), p2)
            p2.color = c(0xFFF2D06B)
            c.drawOval(RectF(7.4f, 10.2f, 24.6f, 23.8f), p2)
            p2.color = c(0xFFD96C64)
            c.drawOval(RectF(10f, 12.6f, 22f, 21.4f), p2)
            p2.color = c(0xFFFDF6E8)
            c.drawOval(RectF(13f, 15f, 19f, 19f), p2)
        })
        // 3) 스탠드 조명
        arts.add(art { c, p ->
            r(c, p, 14.8f, 11f, 17.2f, 26f, c(0xFF5A626C))
            r(c, p, 15.6f, 11f, 16.4f, 26f, c(0xFF7C8590))
            r(c, p, 10f, 26f, 22f, 29f, c(0xFF3A3F4A))
            r(c, p, 9f, 4f, 23f, 11.4f, c(0xFFE8A75C))
            r(c, p, 10.4f, 5.4f, 21.6f, 10.6f, c(0xFFF7CE5B))
            r(c, p, 12f, 6.6f, 20f, 9.4f, c(0xFFFFFBE0))
            r(c, p, 9f, 11f, 23f, 12.4f, c(0xFFC97B50))
        })
        // 4) 트로피
        arts.add(art { c, p ->
            r(c, p, 9f, 5f, 23f, 15f, c(0xFFF2B63C))
            r(c, p, 10.4f, 6.4f, 21.6f, 13.6f, c(0xFFF7CE5B))
            r(c, p, 5.4f, 6f, 8.4f, 12f, c(0xFFD99B26))
            r(c, p, 23.6f, 6f, 26.6f, 12f, c(0xFFD99B26))
            r(c, p, 14f, 15f, 18f, 20f, c(0xFFD99B26))
            r(c, p, 10f, 20f, 22f, 23f, c(0xFFB0793F))
            r(c, p, 8f, 23f, 24f, 27f, c(0xFF6B431F))
            r(c, p, 13f, 8f, 15f, 12f, c(0xFFFFFBE0))
            r(c, p, 16.6f, 9f, 18.6f, 11f, c(0xFFE8863C))
        })
        // 5) 빈티지 라디오
        arts.add(art { c, p ->
            r(c, p, 4f, 9f, 28f, 27f, c(0xFF8A5A33))
            r(c, p, 5.4f, 10.4f, 26.6f, 25.6f, c(0xFFA87B4F))
            r(c, p, 7f, 12f, 18f, 16f, c(0xFFD9C39A))
            r(c, p, 7.6f, 12.6f, 17.4f, 15.4f, c(0xFF23232B))
            r(c, p, 8.4f, 13.4f, 16f, 14.6f, c(0xFF4A4A55))
            r(c, p, 20f, 12f, 25f, 16f, c(0xFFF2D06B))
            r(c, p, 20.8f, 12.8f, 24.2f, 15.2f, c(0xFFFFFBE0))
            r(c, p, 7f, 18.4f, 25f, 24f, c(0xFFC89B6A))
            r(c, p, 8f, 19.4f, 24f, 20f, c(0xFF7A5A33))
            r(c, p, 8f, 21.4f, 24f, 22f, c(0xFF7A5A33))
            r(c, p, 12f, 5.4f, 20f, 9f, c(0xFF6B431F))
            r(c, p, 13f, 6.4f, 19f, 8.4f, c(0xFF5A626C))
        })

        decorArt = arts.toTypedArray()
    }

    // -----------------------------------------------------------------------

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
