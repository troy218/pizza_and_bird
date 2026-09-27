package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
lateinit var playerSideL: Array<Bitmap>      // 왼쪽 방향 (플립)
    lateinit var femaleDown: Array<Bitmap>
    lateinit var femaleUp: Array<Bitmap>
    lateinit var femaleSide: Array<Bitmap>
    lateinit var femaleSideL: Array<Bitmap>
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
        val fp = pl.copy(hair = c(0xFF6A3155), hair2 = c(0xFF4A203D), top = c(0xFFDB6B9A), top2 = c(0xFFB84D7B), pants = c(0xFF66529B), pants2 = c(0xFF4D3C7C))
        femaleDown = Array(3) { person(0, it, fp) }
        femaleUp = Array(3) { person(1, it, fp) }
        femaleSide = Array(3) { person(2, it, fp) }
        femaleSideL = Array(3) { flipH(femaleSide[it]) }

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

    private fun grassBase(c: Canvas, p: Paint, r: Random, base: Int = c(0xFF96D07A)) {
        fill(c, p, base)
        specks(c, p, r, shade(base, 0.9f), 7)
        specks(c, p, r, shade(base, 1.08f), 6)
        // 풀잎
        p.color = shade(base, 0.82f)
        repeat(5) {
            val x = r.nextInt(29)
            val y = r.nextInt(24)
            c.drawRect(x.toFloat(), y.toFloat(), x + 1.4f, y + 3.5f, p)
        }
        p.color = shade(base, 1.14f)
        repeat(3) {
            val x = r.nextInt(29)
            val y = r.nextInt(24)
            c.drawRect(x.toFloat(), y.toFloat(), x + 1.4f, y + 2.8f, p)
        }
    }

    private fun buildTiles() {
        val list = ArrayList<Array<Bitmap>>()
        fun add(vararg bmps: Bitmap) {
            list.add(if (bmps.size == 1) arrayOf(bmps[0]) else bmps.toList().toTypedArray())
        }

        // GRASS (4종 변형)
        for (i in 0 until 4) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r)
                if (i == 1) {   // 넝쿨
                    p.color = c(0xFF6FAE57)
                    c.drawRect(5f, 6f, 9f, 7.2f, p)
                    c.drawRect(7f, 5f, 8.2f, 9f, p)
                    c.drawRect(22f, 20f, 26f, 21.2f, p)
                    c.drawRect(24f, 19f, 25.2f, 23f, p)
                }
                if (i == 2) {   // 잔돌
                    p.color = c(0xFFA8B0A0)
                    c.drawRect(14f, 18f, 17f, 20f, p)
                    c.drawRect(14.8f, 17.4f, 16.2f, 20.6f, p)
                }
                if (i == 3) {   // 민들레
                    p.color = c(0xFFFDF6E8)
                    c.drawRect(20f, 9f, 22f, 11f, p)
                    c.drawRect(19.4f, 9.6f, 22.6f, 10.4f, p)
                }
            })
        }
        // TALLGRASS (2종)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r, c(0xFF8CC46C))
                p.color = c(0xFF6FAE57)
                for (k in 0 until 6 + i) {
                    val x = 1 + r.nextInt(28)
                    val h = 9 + r.nextInt(9)
                    c.drawRect(x.toFloat(), (32 - h).toFloat(), x + 2f, 32f, p)
                }
                p.color = c(0xFF8CC46C)
                repeat(4) {
                    val x = 1 + r.nextInt(28)
                    val h = 7 + r.nextInt(7)
                    c.drawRect(x.toFloat(), (32 - h).toFloat(), x + 1.6f, 32f, p)
                }
                p.color = c(0xFF5D8A4A)
                c.drawRect(6f, 8f, 7.4f, 18f, p)
                c.drawRect(22f, 6f, 23.4f, 20f, p)
            })
        }
        // FLOWER (3색)
        val flowerCols = intArrayOf(c(0xFFF2A3B3), c(0xFFF2D06B), c(0xFFFDFDF8))
        for (i in 0 until 3) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r)
                repeat(4) {
                    val x = 3 + r.nextInt(23)
                    val y = 3 + r.nextInt(22)
                    p.color = c(0xFF5D8A4A)
                    c.drawRect((x + 1.6f), (y + 2.4f), (x + 2.6f), (y + 5.2f), p)
                    val col = flowerCols[(i + r.nextInt(3)) % 3]
                    p.color = col
                    c.drawRect(x.toFloat(), y.toFloat(), x + 4.2f, y + 2.6f, p)
                    c.drawRect(x + 1f, y - 1f, x + 3.2f, y + 3.6f, p)
                    p.color = c(0xFFF7CE5B)
                    c.drawRect(x + 1.4f, y + 0.4f, x + 2.8f, y + 1.8f, p)
                }
            })
        }
        // PATH (3종)
        for (i in 0 until 3) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFFE5D3A0))
                specks(c, p, r, c(0xFFD6BF87), 9)
                specks(c, p, r, c(0xFFF0E2B8), 6)
                if (i == 1) {
                    p.color = c(0xFFC9B582)
                    c.drawRect(4f, 5f, 8f, 6.4f, p)
                    c.drawRect(20f, 22f, 25f, 23.4f, p)
                }
                if (i == 2) {
                    p.color = c(0xFFC9B582)
                    c.drawRect(9f, 14f, 12f, 15.4f, p)
                    c.drawRect(11f, 13.4f, 10f, 16f, p)
                }
            })
        }
        // PLAZA (2종)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFFD9C9A7))
                p.color = c(0xFFC6B58F)
                c.drawRect(0f, 0f, 32f, 1.6f, p)
                c.drawRect(0f, 0f, 1.6f, 32f, p)
                c.drawRect(0f, 15.5f, 32f, 17f, p)
                c.drawRect(15.5f, 0f, 17f, 32f, p)
                p.color = c(0xFFE9DCBC)
                c.drawRect(2.4f, 2.4f, 14.8f, 14.8f, p)
                c.drawRect(18.2f, 18.2f, 30f, 30f, p)
                if (i == 1) {
                    p.color = c(0xFFC6B58F)
                    c.drawRect(18.2f, 8f, 28f, 9.4f, p)
                    c.drawRect(6f, 20f, 9f, 21.2f, p)
                }
            })
        }
        // SAND (3종)
        for (i in 0 until 3) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFFF2E1B0))
                specks(c, p, r, c(0xFFE4CF96), 9)
                specks(c, p, r, c(0xFFF8ECC8), 7)
                if (i == 1) {   // 조개껍데기
                    p.color = c(0xFFFDF6E8)
                    c.drawRect(13f, 17f, 17f, 19.4f, p)
                    p.color = c(0xFFE8B14E)
                    c.drawRect(14.4f, 18f, 15.6f, 19f, p)
                }
                if (i == 2) {   // 물결 무늬
                    p.color = c(0xFFE4CF96)
                    c.drawRect(3f, 12f, 12f, 13.2f, p)
                    c.drawRect(18f, 24f, 28f, 25.2f, p)
                }
            })
        }
        // WATER (4프레임 애니메이션)
        for (f in 0 until 4) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFF4FA8D8))
                p.color = c(0xFF63B8E4)
                c.drawRect(0f, 3f, 32f, 6f, p)
                c.drawRect(0f, 14f, 32f, 16f, p)
                c.drawRect(0f, 25f, 32f, 27f, p)
                val off = f * 3f
                p.color = c(0xFF93D4EF)
                c.drawRect((2f + off) % 28f, 4.4f, (2f + off) % 28f + 7f, 5.8f, p)
                c.drawRect((18f + off) % 26f, 15f, (18f + off) % 26f + 8f, 16.4f, p)
                c.drawRect((8f + off) % 26f, 25.6f, (8f + off) % 26f + 7f, 27f, p)
                p.color = c(0xFFC9ECF8)
                c.drawRect((3f + off) % 28f, 4.6f, (3f + off) % 28f + 2.4f, 5.6f, p)
                c.drawRect((19f + off) % 26f, 15.2f, (19f + off) % 26f + 2.4f, 16.2f, p)
            })
        }
        // REED (2종)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r, c(0xFF8CC46C))
                val xs = if (i == 0) intArrayOf(5, 12, 20, 27) else intArrayOf(8, 15, 24)
                for (x in xs) {
                    p.color = c(0xFF7D9C4F)
                    c.drawRect(x.toFloat(), (if (x % 2 == 0) 4f else 1f), x + 2.2f, 32f, p)
                    p.color = c(0xFFB0793F)
                    val ty = if (x % 2 == 0) 4f else 1f
                    c.drawRect((x - 0.8f), ty, (x + 3f), ty + 7f, p)
                    p.color = c(0xFF8A5A33)
                    c.drawRect(x.toFloat(), ty + 1.6f, x + 1.2f, ty + 5f, p)
                }
                p.color = c(0xFF6FAE57)
                c.drawRect(10f, 22f, 14f, 23.2f, p)
            })
        }
        // TREE (2종: 활엽수 + 침엽수)
        add(tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = c(0xFF5D3A20)
            c.drawRect(14f, 18f, 18f, 31f, p)
            p.color = c(0xFF7A4E2B)
            c.drawRect(14.6f, 18f, 16.2f, 31f, p)
            p.color = c(0xFF33602F)
            c.drawCircle(16f, 12f, 11.4f, p)
            p.color = c(0xFF3F7D46)
            c.drawCircle(16f, 11f, 10.2f, p)
            p.color = c(0xFF4F9E57)
            c.drawCircle(14f, 8.6f, 6.4f, p)
            c.drawCircle(21f, 12.6f, 4.6f, p)
            p.color = c(0xFF6BBA72)
            c.drawCircle(12.4f, 7f, 3.4f, p)
            p.color = c(0xFF2C5429)
            c.drawRect(9f, 17.4f, 24f, 18.6f, p)
        })
        add(tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = c(0xFF5D3A20)
            c.drawRect(14.6f, 24f, 17.4f, 31f, p)
            val path = Path()
            p.color = c(0xFF2C5A34)
            path.moveTo(16f, 0f); path.lineTo(25f, 13f); path.lineTo(7f, 13f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF3A7044)
            path.reset()
            path.moveTo(16f, 7f); path.lineTo(27f, 21f); path.lineTo(5f, 21f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF2C5A34)
            path.reset()
            path.moveTo(16f, 14f); path.lineTo(29f, 28f); path.lineTo(3f, 28f); path.close()
            c.drawPath(path, p)
            p.color = c(0xFF4F9E57)
            c.drawRect(12.4f, 9f, 15f, 10.4f, p)
            c.drawRect(8f, 22f, 10.6f, 23.4f, p)
        })
        // ROCK (2종)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                grassBase(c, p, r)
                if (i == 0) {
                    p.color = c(0xFF5A626C)
                    c.drawRect(6f, 10f, 26f, 28f, p)
                    p.color = c(0xFF7C8590)
                    c.drawRect(7.4f, 8.6f, 24.6f, 26f, p)
                    p.color = c(0xFF9AA3AD)
                    c.drawRect(9f, 10f, 16f, 15f, p)
                    p.color = c(0xFFB5BDC6)
                    c.drawRect(10.4f, 11f, 13f, 13f, p)
                    p.color = c(0xFF4A5158)
                    c.drawRect(9f, 24f, 24f, 26f, p)
                } else {
                    p.color = c(0xFF7C8590)
                    c.drawRect(10f, 16f, 22f, 26f, p)
                    p.color = c(0xFF9AA3AD)
                    c.drawRect(11f, 14.6f, 20.6f, 24f, p)
                    p.color = c(0xFFB5BDC6)
                    c.drawRect(12.4f, 15.6f, 15f, 18f, p)
                    p.color = c(0xFF6FAE57)
                    c.drawRect(10f, 24f, 13f, 26f, p)
                }
            })
        }
        // MOUNTAIN (2종)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFF77848F))
                p.color = c(0xFF8D9AA8)
                c.drawRect(0f, 0f, 32f, 6f, p)
                c.drawRect(0f, 12f, 12f, 20f, p)
                c.drawRect(20f, 8f, 32f, 18f, p)
                c.drawRect(0f, 24f, 8f, 32f, p)
                p.color = c(0xFFA5B2BD)
                c.drawRect(4f, 2f, 10f, 4f, p)
                c.drawRect(22f, 10f, 28f, 12f, p)
                p.color = c(0xFF5D6772)
                c.drawRect(0f, 6f, 32f, 8f, p)
                c.drawRect(0f, 20f, 32f, 22f, p)
                c.drawRect(0f, 30f, 32f, 32f, p)
                if (i == 1) {
                    p.color = c(0xFFE8EEF2)
                    c.drawRect(2f, 9f, 8f, 11f, p)
                    c.drawRect(24f, 23f, 29f, 25f, p)
                }
            })
        }
        // BLDG_WALL
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFE9E2D3))
            p.color = c(0xFFD8CFBA)
            c.drawRect(0f, 8f, 32f, 9.6f, p)
            c.drawRect(0f, 20f, 32f, 21.6f, p)
            c.drawRect(0f, 30f, 32f, 32f, p)
            p.color = c(0xFFF4EFDF)
            c.drawRect(0f, 0f, 32f, 1.4f, p)
            p.color = c(0xFFCCC2AA)
            c.drawRect(15f, 0f, 16.4f, 8f, p)
            c.drawRect(15f, 9.6f, 16.4f, 20f, p)
        })
        // BLDG_WIN (2종)
        val curtains = intArrayOf(c(0xFFF2D06B), c(0xFFC3A3E8))
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFFE9E2D3))
                p.color = c(0xFFD8CFBA)
                c.drawRect(0f, 30f, 32f, 32f, p)
                p.color = c(0xFFC9BFA8)
                c.drawRect(6f, 6f, 26f, 24f, p)
                p.color = c(0xFF7FB6D9)
                c.drawRect(7.4f, 7.4f, 24.6f, 22.6f, p)
                p.color = c(0xFFB9DDF0)
                c.drawRect(7.4f, 7.4f, 13f, 13f, p)
                p.color = c(0xFFC9BFA8)
                c.drawRect(15.4f, 7.4f, 16.6f, 22.6f, p)
                c.drawRect(7.4f, 14.6f, 24.6f, 15.8f, p)
                p.color = curtains[i]
                c.drawRect(7.4f, 7.4f, 10f, 22.6f, p)
                c.drawRect(22f, 7.4f, 24.6f, 22.6f, p)
            })
        }
        // BLDG_ROOF
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFC96A4D))
            p.color = c(0xFFB2583F)
            c.drawRect(0f, 8f, 32f, 9.6f, p)
            c.drawRect(0f, 20f, 32f, 21.6f, p)
            c.drawRect(8f, 0f, 9.6f, 8f, p)
            c.drawRect(24f, 9.6f, 25.6f, 20f, p)
            c.drawRect(8f, 21.6f, 9.6f, 32f, p)
            p.color = c(0xFFDB8266)
            c.drawRect(0f, 0f, 32f, 1.6f, p)
            c.drawRect(4f, 11f, 12f, 12.4f, p)
            c.drawRect(18f, 25f, 26f, 26.4f, p)
        })
        // HOUSE_ROOF
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD4694A))
            p.color = c(0xFFB55338)
            c.drawRect(0f, 6f, 32f, 7.6f, p)
            c.drawRect(0f, 16f, 32f, 17.6f, p)
            c.drawRect(0f, 26f, 32f, 27.6f, p)
            c.drawRect(10f, 0f, 11.4f, 6f, p)
            c.drawRect(10f, 17.6f, 11.4f, 26f, p)
            p.color = c(0xFFE08A67)
            c.drawRect(0f, 0f, 32f, 1.4f, p)
            c.drawRect(4f, 10f, 14f, 11.2f, p)
            c.drawRect(18f, 20f, 28f, 21.2f, p)
        })
        // HOUSE_WALL
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 14f, 32f, 16f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 2f, 32f, p)
            c.drawRect(30f, 0f, 32f, 32f, p)
            c.drawRect(0f, 28f, 32f, 32f, p)
            p.color = c(0xFFD9B98C)
            c.drawRect(13f, 0f, 15f, 14f, p)
        })
        // HOUSE_WIN (꽃상자 있는 창문)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFC9A87B)
            c.drawRect(6f, 5f, 26f, 22f, p)
            p.color = c(0xFF9FD0E8)
            c.drawRect(7.4f, 6.4f, 24.6f, 20.6f, p)
            p.color = c(0xFFC3E4F2)
            c.drawRect(7.4f, 6.4f, 14f, 12f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(15.4f, 6.4f, 16.6f, 20.6f, p)
            c.drawRect(7.4f, 12.6f, 24.6f, 13.8f, p)
            // 꽃상자
            p.color = c(0xFF8A5A33)
            c.drawRect(6f, 22f, 26f, 27f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(7f, 23f, 25f, 26f, p)
            p.color = c(0xFFF2A3B3)
            c.drawRect(8f, 20.4f, 10.4f, 22.4f, p)
            p.color = c(0xFFF2D06B)
            c.drawRect(14f, 20f, 16.4f, 22.4f, p)
            p.color = c(0xFFC3A3E8)
            c.drawRect(21f, 20.6f, 23.4f, 22.4f, p)
        })
        // HOUSE_DOOR
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF6E7C6))
            p.color = c(0xFFC9A87B)
            c.drawRect(2f, 3f, 30f, 32f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(3.4f, 4.4f, 28.6f, 32f, p)
            p.color = c(0xFF7A4A2B)
            c.drawRect(5f, 8f, 27f, 10f, p)
            c.drawRect(5f, 16f, 27f, 18f, p)
            p.color = c(0xFF9FD0E8)
            c.drawRect(11f, 19.6f, 21f, 26f, p)
            p.color = c(0xFFC3E4F2)
            c.drawRect(11f, 19.6f, 15f, 23f, p)
            p.color = c(0xFFF2D06B)
            c.drawRect(23f, 14f, 26f, 16.6f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 28f, 32f, 32f, p)
        })
        // TUNNEL
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFF77848F))
            p.color = c(0xFF8D9AA8)
            c.drawRect(0f, 0f, 32f, 8f, p)
            p.color = c(0xFFA5B2BD)
            c.drawRect(4f, 1f, 12f, 3f, p)
            p.color = c(0xFF6B4F35)
            c.drawRect(4f, 4f, 28f, 32f, p)
            p.color = c(0xFF5A3F28)
            c.drawRect(2f, 8f, 6f, 32f, p)
            c.drawRect(26f, 8f, 30f, 32f, p)
            p.color = c(0xFF191921)
            c.drawRect(8f, 8f, 24f, 32f, p)
            p.color = c(0xFF2E2E3A)
            c.drawRect(10f, 10f, 22f, 32f, p)
            // 입구 등
            p.color = c(0xFFF2D06B)
            c.drawRect(14f, 2f, 18f, 6f, p)
            p.color = c(0xFFF7E9A8)
            c.drawRect(15f, 3f, 17f, 5f, p)
            // 노면
            p.color = c(0xFF8A8074)
            c.drawRect(8f, 28f, 24f, 32f, p)
        })
        // FLOOR (2종)
        for (i in 0 until 2) {
            add(tilePainter { c, p, r ->
                val base = if (i == 0) c(0xFFCDA775) else c(0xFFC49E6C)
                fill(c, p, base)
                p.color = c(0xFFB98F5E)
                c.drawRect(0f, 10f, 32f, 11.6f, p)
                c.drawRect(0f, 21f, 32f, 22.6f, p)
                val seam = if (i == 0) 10f else 22f
                c.drawRect(seam, 0f, seam + 1.4f, 10f, p)
                c.drawRect(32f - seam, 11.6f, 33.4f - seam, 21f, p)
                p.color = c(0xFFDBB684)
                c.drawRect(0f, 0f, 32f, 1.2f, p)
                c.drawRect(0f, 11.6f, 32f, 12.6f, p)
                c.drawRect(0f, 22.6f, 32f, 23.6f, p)
                p.color = c(0xFFA87B4F)
                c.drawRect(3f, 4f, 4f, 5f, p)
                c.drawRect(27f, 25f, 28f, 26f, p)
            })
        }
        // WALL_IN
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFFE8D5AE)
            c.drawRect(5f, 4f, 7f, 26f, p)
            c.drawRect(14f, 4f, 16f, 26f, p)
            c.drawRect(23f, 4f, 25f, 26f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 32f, 3f, p)
            c.drawRect(0f, 26f, 32f, 27.4f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 27.4f, 32f, 32f, p)
        })
        // WALL_WIN
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFF2E3C2))
            p.color = c(0xFFC9A87B)
            c.drawRect(0f, 0f, 32f, 3f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(6f, 6f, 26f, 23f, p)
            p.color = c(0xFFA8D8E8)
            c.drawRect(7.4f, 7.4f, 24.6f, 21.6f, p)
            p.color = c(0xFFC9E8F2)
            c.drawRect(7.4f, 7.4f, 15f, 13f, p)
            p.color = c(0xFF8CC46C)
            c.drawRect(7.4f, 16f, 12f, 19f, p)
            c.drawRect(18f, 13f, 20f, 18f, p)
            p.color = c(0xFFC9A87B)
            c.drawRect(15.4f, 7.4f, 16.6f, 21.6f, p)
            c.drawRect(7.4f, 13.6f, 24.6f, 14.8f, p)
            p.color = c(0xFFE0C9A2)
            c.drawRect(0f, 23f, 32f, 24f, p)
            c.drawRect(0f, 27.4f, 32f, 32f, p)
        })
        // OVEN (2프레임 — 불꽃 애니메이션)
        for (f in 0 until 2) {
            add(tilePainter { c, p, r ->
                fill(c, p, c(0xFF8F8F99))
                p.color = c(0xFF7A7A85)
                c.drawRect(0f, 0f, 32f, 2.4f, p)
                c.drawRect(0f, 14f, 32f, 16f, p)
                c.drawRect(15.4f, 0f, 17f, 14f, p)
                c.drawRect(6f, 16f, 8f, 32f, p)
                c.drawRect(24f, 16f, 26f, 32f, p)
                p.color = c(0xFFA8A8B2)
                c.drawRect(0f, 2.4f, 32f, 3.6f, p)
                // 아치 화구
                p.color = c(0xFF23232B)
                c.drawCircle(16f, 21f, 9.6f, p)
                p.color = c(0xFF33333D)
                c.drawCircle(16f, 21f, 8.6f, p)
                // 불꽃
                p.color = c(0xFFE2574C)
                c.drawRect(9f, 18f, 23f, 28f, p)
                p.color = c(0xFFF2913C)
                c.drawRect(11f, 16f + (if (f == 0) 0f else 1.6f), 21f, 24f, p)
                p.color = c(0xFFF7CE5B)
                c.drawRect(13f, 15f + (if (f == 0) 1.6f else 0f), 19f, 21f, p)
                p.color = c(0xFFFDF6E8)
                c.drawRect(15f, 18f + (if (f == 0) 0f else 1.4f), 17f, 21f, p)
                p.color = c(0xFF6B6B78)
                c.drawRect(9f, 29f, 23f, 30.6f, p)
            })
        }
        // BED
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFF8A5A33)
            c.drawRect(2f, 2f, 30f, 30f, p)
            p.color = c(0xFFB5651D)
            c.drawRect(3.4f, 3.4f, 28.6f, 28.6f, p)
            p.color = c(0xFFE8867A)
            c.drawRect(4.6f, 8f, 27.4f, 27.4f, p)
            p.color = c(0xFFD96C64)
            c.drawRect(4.6f, 16f, 27.4f, 18f, p)
            c.drawRect(12f, 18f, 14f, 27.4f, p)
            p.color = c(0xFFF5EFE0)
            c.drawRect(5.6f, 3.8f, 15f, 9.4f, p)
            p.color = c(0xFFE0D8C4)
            c.drawRect(5.6f, 8f, 15f, 9.4f, p)
            p.color = c(0xFFF7B2A8)
            c.drawRect(18f, 11f, 27.4f, 14f, p)
        })
        // BOX
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFFC89B6A)
            c.drawRect(2f, 4f, 30f, 30f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(2f, 4f, 30f, 6.4f, p)
            c.drawRect(2f, 17f, 30f, 19f, p)
            c.drawRect(2f, 4f, 4f, 30f, p)
            c.drawRect(28f, 4f, 30f, 30f, p)
            p.color = c(0xFFD9C39A)
            c.drawRect(12f, 6.4f, 20f, 17f, p)
            p.color = c(0xFF7A5A33)
            c.drawRect(14f, 9f, 18f, 10.4f, p)
            c.drawRect(14.8f, 10.4f, 16f, 13f, p)
            c.drawRect(16.8f, 12f, 18f, 13f, p)
            p.color = c(0xFFE8D5A3)
            c.drawRect(13f, 20f, 19f, 26f, p)
            p.color = c(0xFF8A6A4F)
            c.drawRect(13.6f, 21f, 18.4f, 22f, p)
        })
        // DECOR (장식 칸 — 점선 표시)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFCDA775))
            p.color = c(0xFFB98F5E)
            c.drawRect(0f, 10f, 32f, 11.6f, p)
            c.drawRect(0f, 21f, 32f, 22.6f, p)
            p.color = c(0xFFB08A5C)
            // 점선 사각형
            for (i in 0 until 13) {
                c.drawRect((4 + i * 2).toFloat(), 4f, (5 + i * 2).toFloat(), 5.4f, p)
                c.drawRect((4 + i * 2).toFloat(), 26.6f, (5 + i * 2).toFloat(), 28f, p)
                c.drawRect(4f, (4 + i * 2).toFloat(), 5.4f, (5 + i * 2).toFloat(), p)
                c.drawRect(26.6f, (4 + i * 2).toFloat(), 28f, (5 + i * 2).toFloat(), p)
            }
            p.color = c(0xFFE8D5A3)
            c.drawRect(14.6f, 14.6f, 17.4f, 17.4f, p)
        })
        // SIGN (터널 이정표)
        add(tilePainter { c, p, r ->
            grassBase(c, p, r)
            p.color = c(0xFF6B431F)
            c.drawRect(14.6f, 10f, 17.4f, 30f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(15f, 10f, 16f, 30f, p)
            // 판
            p.color = c(0xFF6B431F)
            c.drawRect(5f, 4f, 27f, 15f, p)
            p.color = c(0xFFC89B6A)
            c.drawRect(6.2f, 5.2f, 25.8f, 13.8f, p)
            p.color = c(0xFF8A6A4F)
            c.drawRect(7.4f, 6.4f, 18f, 7.8f, p)
            c.drawRect(7.4f, 9.4f, 16f, 10.8f, p)
            c.drawRect(7.4f, 12.4f, 18f, 13.2f, p)
            // 화살표
            val path = Path()
            p.color = c(0xFF4A3728)
            path.moveTo(20f, 7f); path.lineTo(24f, 9.6f); path.lineTo(20f, 12.2f)
            path.close()
            c.drawPath(path, p)
            c.drawRect(16.4f, 8.8f, 20.4f, 10.4f, p)
        })
        // BENCH (벤치)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            p.color = c(0xFFC6B58F)
            c.drawRect(0f, 0f, 32f, 1.6f, p)
            c.drawRect(0f, 15.5f, 32f, 17f, p)
            p.color = c(0xFFE9DCBC)
            c.drawRect(2.4f, 2.4f, 14.8f, 14.8f, p)
            c.drawRect(18.2f, 18.2f, 30f, 30f, p)
            // 등받이
            p.color = c(0xFF6B431F)
            c.drawRect(3f, 3f, 29f, 5.4f, p)
            p.color = c(0xFF8A5A33)
            c.drawRect(4f, 4f, 28f, 5f, p)
            c.drawRect(6f, 3.4f, 8f, 12f, p)
            c.drawRect(24f, 3.4f, 26f, 12f, p)
            // 좌석
            p.color = c(0xFF6B431F)
            c.drawRect(2.4f, 14f, 29.6f, 19f, p)
            p.color = c(0xFFA87B4F)
            c.drawRect(3.4f, 15f, 28.6f, 18f, p)
            p.color = c(0xFFC89B6A)
            c.drawRect(3.4f, 15f, 28.6f, 16f, p)
            // 다리
            p.color = c(0xFF4A3320)
            c.drawRect(4f, 19f, 6.4f, 27f, p)
            c.drawRect(25.6f, 19f, 28f, 27f, p)
        })
        // LAMP (가로등)
        add(tilePainter { c, p, r ->
            fill(c, p, c(0xFFD9C9A7))
            p.color = c(0xFFC6B58F)
            c.drawRect(0f, 0f, 32f, 1.6f, p)
            c.drawRect(0f, 15.5f, 32f, 17f, p)
            // 기둥
            p.color = c(0xFF3A3F4A)
            c.drawRect(14.4f, 6f, 17.6f, 30f, p)
            p.color = c(0xFF5A626C)
            c.drawRect(15.2f, 6f, 16.2f, 30f, p)
            p.color = c(0xFF3A3F4A)
            c.drawRect(8f, 29f, 24f, 31f, p)
            // 머리
            p.color = c(0xFF3A3F4A)
            c.drawRect(9f, 2f, 23f, 7f, p)
            p.color = c(0xFFF7E9A8)
            c.drawRect(10.4f, 3.4f, 21.6f, 6f, p)
            p.color = c(0xFFFFFBE0)
            c.drawRect(12f, 4f, 20f, 5.4f, p)
            p.color = c(0xFF23232B)
            c.drawRect(15.4f, 7f, 16.6f, 8.4f, p)
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
