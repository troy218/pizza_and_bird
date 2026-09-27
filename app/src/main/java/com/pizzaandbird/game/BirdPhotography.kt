package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 촬영 순간 새가 카메라에 보인 방향. 같은 종도 방향에 따라 다른 그림으로 남는다. */
enum class BirdFacing(val key: String, val label: String) {
    LEFT("left", "왼쪽 옆모습"),
    RIGHT("right", "오른쪽 옆모습"),
    FRONT("front", "정면"),
    BACK("back", "뒷모습");

    companion object {
        fun of(key: String): BirdFacing = values().firstOrNull { it.key == key } ?: LEFT
    }
}

/** 필드에서 자연스럽게 바뀌는 자세. */
enum class BirdPose(val key: String, val label: String) {
    PERCHED("perched", "쉬는 중"),
    ALERT("alert", "고개를 든 순간"),
    FEEDING("feeding", "먹이를 찾는 순간");

    companion object {
        fun of(key: String): BirdPose = values().firstOrNull { it.key == key } ?: PERCHED
    }
}

/** 실제 참고 사진에서 뽑은 색을 종별 도트 아트에 전달하는 팔레트. */
data class BirdRenderPalette(
    val body: Int,
    val belly: Int,
    val wing: Int,
    val head: Int,
    val accent: Int,
    val beak: Int,
    val leg: Int
)

/**
 * 598종 공통 고해상도 도트 리그.
 *
 * 기존 13개 작은 옆모습 아이콘을 그대로 뒤집는 대신, 생태 체형 13종을 바탕으로
 * 옆/정면/뒷면을 각각 다시 그린다. 실제 사진에서 읽은 종별 색과 BirdArt의 식별
 * 무늬를 함께 사용하고, 날개깃·꼬리깃·눈썹선·발가락까지 별도 레이어로 표현한다.
 */
object DetailedBirdRenderer {
    private data class Morph(
        val bodyW: Float, val bodyH: Float, val headR: Float,
        val neck: Float, val bill: Float, val tail: Float, val legs: Float,
        val wingLong: Float, val broad: Boolean = false
    )

    private fun morph(template: Int): Morph = when (template) {
        1 -> Morph(35f, 19f, 8f, 3f, 8f, 10f, 4f, 1.0f, true)   // 오리/기러기
        2 -> Morph(25f, 20f, 7f, 18f, 11f, 13f, 17f, 1.05f)     // 백로/두루미
        3 -> Morph(34f, 23f, 9f, 5f, 7f, 15f, 9f, 1.16f, true)  // 맹금
        4 -> Morph(26f, 29f, 11f, 1f, 4f, 7f, 7f, 0.82f, true)  // 올빼미
        5 -> Morph(29f, 16f, 7f, 7f, 13f, 9f, 14f, 1.02f)       // 도요
        6 -> Morph(34f, 18f, 7.5f, 4f, 10f, 14f, 7f, 1.12f)     // 바닷새
        7 -> Morph(23f, 27f, 7.5f, 3f, 8f, 13f, 8f, 0.92f)      // 딱다구리
        8 -> Morph(31f, 21f, 8.5f, 4f, 6f, 15f, 7f, 1.0f, true) // 비둘기/두견이
        9 -> Morph(28f, 18f, 8f, 3f, 14f, 10f, 7f, 0.94f)       // 물총새
        10 -> Morph(27f, 20f, 8f, 3f, 6f, 9f, 8f, 0.92f, true)  // 팔색조
        11 -> Morph(36f, 23f, 8f, 6f, 7f, 18f, 11f, 1.08f, true)// 꿩/뜸부기
        12 -> Morph(30f, 14f, 6.5f, 2f, 5f, 18f, 4f, 1.18f)     // 제비/칼새
        else -> Morph(28f, 19f, 8f, 3f, 6f, 13f, 8f, 1.0f)      // 명금
    }

    private fun shade(color: Int, k: Float): Int {
        fun ch(v: Int) = (v * k).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    fun render(def: BirdDef, facing: BirdFacing, pose: BirdPose, pal: BirdRenderPalette): Bitmap {
        if (facing == BirdFacing.RIGHT) {
            val left = render(def, BirdFacing.LEFT, pose, pal)
            val m = Matrix().apply { setScale(-1f, 1f) }
            return Bitmap.createBitmap(left, 0, 0, left.width, left.height, m, false)
        }

        val raw = Bitmap.createBitmap(76, 76, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        if (facing == BirdFacing.FRONT || facing == BirdFacing.BACK) {
            drawFrontBack(c, def, pose, pal, facing == BirdFacing.BACK)
        } else {
            drawSide(c, def, pose, pal)
        }
        val cropped = crop(raw, 2)
        if (def.art.scale == 1f) return cropped
        return Bitmap.createScaledBitmap(
            cropped,
            (cropped.width * def.art.scale).toInt().coerceAtLeast(1),
            (cropped.height * def.art.scale).toInt().coerceAtLeast(1),
            false
        )
    }

    private fun drawSide(c: Canvas, def: BirdDef, pose: BirdPose, p: BirdRenderPalette) {
        val m = morph(def.art.template)
        val paint = Paint().apply { isAntiAlias = false }
        val outline = shade(p.body, 0.38f)
        val deep = shade(p.wing, 0.58f)
        val pale = shade(p.belly, 1.08f)
        val bodyCx = 39f
        val bodyCy = if (m.legs > 12f) 43f else 42f
        val feeding = pose == BirdPose.FEEDING
        val alert = pose == BirdPose.ALERT
        val longNeck = def.art.template == 2
        val upright = def.art.template == 7

        fun oval(l: Float, t: Float, r: Float, b: Float, col: Int) {
            paint.color = col; c.drawOval(RectF(l, t, r, b), paint)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, col: Int) {
            paint.color = col; c.drawRect(l, t, r, b, paint)
        }
        fun path(col: Int, vararg pts: Float) {
            val q = Path(); q.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { q.lineTo(pts[i], pts[i + 1]); i += 2 }
            q.close(); paint.color = col; c.drawPath(q, paint)
        }

        // 꼬리는 깃을 세 장으로 나눠 끝 모양까지 보이게 한다.
        val tailY = bodyCy + m.bodyH * 0.04f
        val tailX = bodyCx + m.bodyW * 0.37f
        val tailEnd = (tailX + m.tail).coerceAtMost(72f)
        path(outline, tailX - 1f, tailY - 5f, tailEnd, tailY - 3f, tailEnd - 2f, tailY + 2f, tailX, tailY + 5f)
        path(p.wing, tailX, tailY - 4f, tailEnd - 1f, tailY - 2f, tailEnd - 4f, tailY, tailX, tailY + 1f)
        path(deep, tailX, tailY, tailEnd - 3f, tailY + 1f, tailEnd - 5f, tailY + 4f, tailX, tailY + 4f)
        rect(tailX + 4f, tailY - 0.5f, tailEnd - 3f, tailY + 0.6f, shade(p.accent, 0.86f))

        // 다리 관절과 발가락. 물새/도요/두루미는 체형에 맞게 길어진다.
        val footY = (bodyCy + m.bodyH / 2f + m.legs).coerceAtMost(72f)
        val legTop = bodyCy + m.bodyH * 0.34f
        val legXs = floatArrayOf(bodyCx - m.bodyW * 0.16f, bodyCx + m.bodyW * 0.14f)
        for ((i, lx) in legXs.withIndex()) {
            val knee = legTop + m.legs * 0.52f
            rect(lx - 1.4f, legTop, lx + 1.3f, knee, shade(p.leg, if (i == 0) 0.82f else 1f))
            rect(lx - 0.2f, knee - 1f, lx + 1.7f, footY, p.leg)
            rect(lx - 4.2f, footY - 0.7f, lx + 4.5f, footY + 1.1f, shade(p.leg, 0.82f))
            rect(lx - 3.6f, footY + 0.8f, lx + 0.2f, footY + 1.8f, shade(p.leg, 0.72f))
        }

        // 몸통 4단 명암과 배의 둥근 볼륨.
        oval(bodyCx - m.bodyW / 2f - 1.5f, bodyCy - m.bodyH / 2f - 1.5f,
            bodyCx + m.bodyW / 2f + 1.5f, bodyCy + m.bodyH / 2f + 1.5f, outline)
        oval(bodyCx - m.bodyW / 2f, bodyCy - m.bodyH / 2f,
            bodyCx + m.bodyW / 2f, bodyCy + m.bodyH / 2f, p.body)
        oval(bodyCx - m.bodyW * 0.42f, bodyCy + m.bodyH * 0.01f,
            bodyCx + m.bodyW * 0.30f, bodyCy + m.bodyH * 0.46f, p.belly)
        path(shade(p.body, 1.15f),
            bodyCx - m.bodyW * 0.37f, bodyCy - m.bodyH * 0.30f,
            bodyCx + m.bodyW * 0.18f, bodyCy - m.bodyH * 0.42f,
            bodyCx + m.bodyW * 0.34f, bodyCy - m.bodyH * 0.23f,
            bodyCx - m.bodyW * 0.18f, bodyCy - m.bodyH * 0.16f)

        // 접힌 날개: 덮깃과 초/차열풍절을 개별 선으로 구분한다.
        val wx0 = bodyCx - m.bodyW * 0.05f
        val wy0 = bodyCy - m.bodyH * 0.32f
        val wx1 = bodyCx + m.bodyW * 0.45f
        val wy1 = bodyCy + m.bodyH * 0.32f
        path(outline, wx0 - 1f, wy0, wx1, bodyCy - 1f, wx1 - 4f, wy1 + 2f, wx0 - 3f, wy1 - 1f)
        path(p.wing, wx0, wy0 + 1f, wx1 - 1f, bodyCy, wx1 - 5f, wy1, wx0 - 3f, wy1 - 2f)
        path(shade(p.wing, 1.14f), wx0 + 2f, wy0 + 2f, wx1 - 4f, bodyCy,
            wx1 - 8f, bodyCy + 3f, wx0 + 1f, bodyCy + 1f)
        for (i in 0 until 4) {
            val yy = bodyCy + 2f + i * 2.2f
            rect(wx0 + 5f + i, yy, wx1 - 4f - i * 1.2f, yy + 1f, if (i % 2 == 0) deep else shade(p.wing, 0.78f))
        }
        rect(wx0 + 3f, wy0 + 4f, wx1 - 4f, wy0 + 5.4f, shade(p.accent, 1.02f))

        // 목과 머리 위치는 자세에 따라 실제로 이동한다.
        var headX = bodyCx - m.bodyW * 0.43f - m.headR * 0.38f
        var headY = bodyCy - m.bodyH * 0.36f - m.neck - m.headR * 0.2f
        if (upright) { headX += 4f; headY -= 4f }
        if (alert) headY -= 3f
        if (feeding) { headX -= 2f; headY += if (longNeck) 19f else 10f }

        if (longNeck) {
            val neckTop = headY + m.headR * 0.45f
            val neckBottom = bodyCy - m.bodyH * 0.15f
            val neckW = 5.2f
            // S자 목을 두 겹의 굵은 계단선으로 표현한다.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = neckW + 3f
            paint.strokeCap = Paint.Cap.SQUARE
            paint.color = outline
            val q = Path().apply {
                moveTo(headX + 2f, neckTop)
                cubicTo(headX + 8f, (neckTop + neckBottom) * 0.45f, headX - 1f, (neckTop + neckBottom) * 0.68f, bodyCx - 8f, neckBottom + 4f)
            }
            c.drawPath(q, paint)
            paint.strokeWidth = neckW
            paint.color = p.head
            c.drawPath(q, paint)
            paint.style = Paint.Style.FILL
        } else if (m.neck > 4f) {
            path(outline, headX + m.headR * 0.35f, headY + 2f,
                bodyCx - m.bodyW * 0.30f, bodyCy - m.bodyH * 0.28f,
                bodyCx - m.bodyW * 0.10f, bodyCy + 2f,
                headX + m.headR * 0.10f, headY + m.headR)
            path(p.head, headX + m.headR * 0.30f, headY + 3f,
                bodyCx - m.bodyW * 0.29f, bodyCy - m.bodyH * 0.25f,
                bodyCx - m.bodyW * 0.12f, bodyCy,
                headX + m.headR * 0.12f, headY + m.headR - 1f)
        }

        // 머리, 볼, 귀깃/볏.
        oval(headX - m.headR - 1.4f, headY - m.headR - 1.4f,
            headX + m.headR + 1.4f, headY + m.headR + 1.4f, outline)
        oval(headX - m.headR, headY - m.headR, headX + m.headR, headY + m.headR, p.head)
        oval(headX - m.headR * 0.72f, headY + m.headR * 0.05f,
            headX + m.headR * 0.35f, headY + m.headR * 0.78f, shade(p.belly, 1.04f))
        path(shade(p.head, 1.18f), headX - m.headR * 0.72f, headY - m.headR * 0.55f,
            headX + m.headR * 0.42f, headY - m.headR * 0.72f,
            headX + m.headR * 0.68f, headY - m.headR * 0.22f,
            headX - m.headR * 0.45f, headY - m.headR * 0.12f)
        if (def.art.template == 4) {
            path(outline, headX - 7f, headY - 6f, headX - 6f, headY - 13f, headX - 1f, headY - 7f)
            path(p.accent, headX - 6f, headY - 7f, headX - 5.5f, headY - 11f, headX - 2f, headY - 6f)
            path(outline, headX + 2f, headY - 7f, headX + 7f, headY - 13f, headX + 7f, headY - 5f)
            path(p.accent, headX + 3f, headY - 6f, headX + 6f, headY - 11f, headX + 6f, headY - 4f)
        } else if (def.art.crest != def.art.body || pose == BirdPose.ALERT) {
            path(outline, headX - 2f, headY - m.headR + 1f, headX + 2f, headY - m.headR - 5f,
                headX + 4f, headY - m.headR + 2f)
            path(p.accent, headX - 1f, headY - m.headR, headX + 2f, headY - m.headR - 3f,
                headX + 3f, headY - m.headR + 1f)
        }

        drawSidePattern(c, paint, def.art.pattern, headX, headY, m, bodyCx, bodyCy, p)

        // 부리는 위·아래 부리와 콧구멍이 분리된다.
        val billY = headY + if (feeding) 3f else 1f
        val billEndX = (headX - m.headR - m.bill).coerceAtLeast(1f)
        path(outline, headX - m.headR + 1f, billY - 2.8f, billEndX - 1f, billY,
            headX - m.headR + 1f, billY + 3.4f)
        path(p.beak, headX - m.headR, billY - 1.8f, billEndX, billY,
            headX - m.headR, billY + 1.3f)
        rect(billEndX + 2f, billY - 0.2f, headX - m.headR - 1f, billY + 0.7f, shade(p.beak, 0.58f))
        rect(headX - m.headR + 2f, billY - 1.2f, headX - m.headR + 3.2f, billY - 0.2f, shade(p.beak, 0.38f))

        // 눈 3픽셀: 테두리·홍채·캐치라이트.
        val eyeX = headX - m.headR * 0.38f
        val eyeY = headY - m.headR * 0.25f
        oval(eyeX - 2.2f, eyeY - 2.2f, eyeX + 2.2f, eyeY + 2.2f, shade(p.head, 0.25f))
        rect(eyeX - 1.2f, eyeY - 1.2f, eyeX + 1.2f, eyeY + 1.2f, 0xFF17151A.toInt())
        rect(eyeX - 0.8f, eyeY - 0.8f, eyeX + 0.2f, eyeY + 0.2f, 0xFFFFFFFF.toInt())
    }

    private fun drawSidePattern(
        c: Canvas, paint: Paint, pattern: Int, hx: Float, hy: Float, m: Morph,
        bx: Float, by: Float, p: BirdRenderPalette
    ) {
        val dark = shade(p.body, 0.42f)
        val pale = shade(p.belly, 1.08f)
        fun rect(l: Float, t: Float, r: Float, b: Float, col: Int) { paint.color = col; c.drawRect(l, t, r, b, paint) }
        when (pattern) {
            BirdPatterns.WING_BARS -> {
                rect(bx + 2f, by - 4f, bx + m.bodyW * 0.38f, by - 2f, pale)
                rect(bx + 4f, by + 1f, bx + m.bodyW * 0.42f, by + 3f, p.accent)
            }
            BirdPatterns.STREAKED -> for (i in -2..2) {
                val x = bx - 8f + i * 3f
                rect(x, by + 2f + abs(i), x + 1.2f, by + 8f, dark)
            }
            BirdPatterns.BIB -> {
                rect(hx - 1f, hy + 5f, hx + 5f, hy + 9f, dark)
                rect(bx - m.bodyW * 0.34f, by - 1f, bx - m.bodyW * 0.20f, by + 8f, dark)
            }
            BirdPatterns.DARK_CAP -> {
                rect(hx - m.headR * 0.72f, hy - m.headR * 0.72f, hx + m.headR * 0.72f, hy - m.headR * 0.38f, p.accent)
                rect(hx - m.headR * 0.55f, hy - m.headR * 0.36f, hx + m.headR * 0.72f, hy - m.headR * 0.16f, p.accent)
            }
            BirdPatterns.SPOTTED -> for (i in 0 until 7) {
                val x = bx - 8f + (i * 7 % 18)
                val y = by - 3f + (i * 5 % 12)
                rect(x, y, x + 1.7f, y + 1.7f, if (i % 2 == 0) pale else dark)
            }
            BirdPatterns.COLLAR -> {
                rect(hx + 3f, hy + 4f, hx + 6f, hy + 9f, p.accent)
                rect(hx + 5f, hy + 6f, hx + 8f, hy + 8f, pale)
            }
            BirdPatterns.EYE_STRIPE -> {
                rect(hx - m.headR * 0.9f, hy - 3f, hx + m.headR * 0.65f, hy - 1.2f, dark)
                rect(hx - m.headR * 0.82f, hy - 5f, hx + m.headR * 0.25f, hy - 3.5f, pale)
            }
            BirdPatterns.IRIDESCENT -> {
                rect(bx + 1f, by - 5f, bx + m.bodyW * 0.38f, by - 2.8f, p.accent)
                rect(bx + 5f, by - 2f, bx + m.bodyW * 0.42f, by, shade(p.accent, 1.22f))
                rect(hx + 2f, hy + 4f, hx + 5f, hy + 7f, p.accent)
            }
        }
    }

    private fun drawFrontBack(c: Canvas, def: BirdDef, pose: BirdPose, p: BirdRenderPalette, back: Boolean) {
        val m = morph(def.art.template)
        val paint = Paint().apply { isAntiAlias = false }
        val outline = shade(p.body, 0.36f)
        val bodyCx = 38f
        val bodyCy = if (m.legs > 12f) 43f else 42f
        val bodyW = m.bodyW * if (m.broad) 0.90f else 0.76f
        val headY = bodyCy - m.bodyH * 0.54f - m.neck * 0.55f -
                (if (pose == BirdPose.ALERT) 3f else 0f) +
                (if (pose == BirdPose.FEEDING) if (m.neck > 8f) 13f else 7f else 0f)
        val headR = m.headR * 0.92f

        fun oval(l: Float, t: Float, r: Float, b: Float, col: Int) { paint.color = col; c.drawOval(RectF(l, t, r, b), paint) }
        fun rect(l: Float, t: Float, r: Float, b: Float, col: Int) { paint.color = col; c.drawRect(l, t, r, b, paint) }
        fun path(col: Int, vararg pts: Float) {
            val q = Path(); q.moveTo(pts[0], pts[1]); var i = 2
            while (i < pts.size) { q.lineTo(pts[i], pts[i + 1]); i += 2 }
            q.close(); paint.color = col; c.drawPath(q, paint)
        }

        // 정면/후면에서는 좌우 꼬리깃과 두 날개가 대칭으로 드러난다.
        path(outline, bodyCx - 7f, bodyCy + m.bodyH * 0.35f, bodyCx - 4f, bodyCy + m.bodyH * 0.5f + m.tail,
            bodyCx, bodyCy + m.bodyH * 0.43f, bodyCx + 4f, bodyCy + m.bodyH * 0.5f + m.tail,
            bodyCx + 7f, bodyCy + m.bodyH * 0.35f)
        path(p.wing, bodyCx - 5f, bodyCy + m.bodyH * 0.34f, bodyCx - 3f, bodyCy + m.bodyH * 0.45f + m.tail * 0.78f,
            bodyCx, bodyCy + m.bodyH * 0.38f, bodyCx + 3f, bodyCy + m.bodyH * 0.45f + m.tail * 0.78f,
            bodyCx + 5f, bodyCy + m.bodyH * 0.34f)

        val footY = (bodyCy + m.bodyH / 2f + m.legs).coerceAtMost(72f)
        for (lx in floatArrayOf(bodyCx - bodyW * 0.20f, bodyCx + bodyW * 0.20f)) {
            rect(lx - 1.2f, bodyCy + m.bodyH * 0.28f, lx + 1.2f, footY, p.leg)
            rect(lx - 4f, footY - 0.5f, lx + 4f, footY + 1.2f, shade(p.leg, 0.8f))
        }

        oval(bodyCx - bodyW / 2f - 1.5f, bodyCy - m.bodyH / 2f - 1.5f,
            bodyCx + bodyW / 2f + 1.5f, bodyCy + m.bodyH / 2f + 1.5f, outline)
        oval(bodyCx - bodyW / 2f, bodyCy - m.bodyH / 2f,
            bodyCx + bodyW / 2f, bodyCy + m.bodyH / 2f, if (back) p.body else p.belly)

        // 좌우 날개판과 깃 결.
        for (side in intArrayOf(-1, 1)) {
            val inner = bodyCx + side * bodyW * 0.08f
            val outer = bodyCx + side * bodyW * 0.46f
            val left = min(inner, outer)
            val right = max(inner, outer)
            oval(left, bodyCy - m.bodyH * 0.34f, right, bodyCy + m.bodyH * 0.34f, p.wing)
            for (i in 0 until 4) {
                val yy = bodyCy - 2f + i * 2.7f
                rect(left + 2f, yy, right - 1f, yy + 0.9f, shade(p.wing, 0.67f + i * 0.06f))
            }
        }

        if (!back) {
            // 정면 가슴 무늬는 좌우 대칭으로 보여야 한다.
            when (def.art.pattern) {
                BirdPatterns.BIB -> {
                    oval(bodyCx - 5f, bodyCy - 8f, bodyCx + 5f, bodyCy + 1f, shade(p.body, 0.35f))
                    rect(bodyCx - 1.4f, bodyCy, bodyCx + 1.4f, bodyCy + 9f, shade(p.body, 0.35f))
                }
                BirdPatterns.STREAKED -> for (i in -2..2) rect(bodyCx + i * 3f, bodyCy - 2f + abs(i), bodyCx + i * 3f + 1f, bodyCy + 8f, shade(p.body, 0.4f))
                BirdPatterns.SPOTTED -> for (i in 0 until 6) {
                    val x = bodyCx - 6f + (i * 5 % 13); val y = bodyCy - 4f + (i * 4 % 12)
                    rect(x, y, x + 1.7f, y + 1.7f, shade(p.body, 0.4f))
                }
            }
        } else {
            // 등 중앙선과 견갑깃.
            rect(bodyCx - 1f, bodyCy - 7f, bodyCx + 1f, bodyCy + 8f, shade(p.body, 0.60f))
            rect(bodyCx - 7f, bodyCy - 6f, bodyCx + 7f, bodyCy - 4.5f, shade(p.accent, 0.95f))
        }

        if (m.neck > 5f) {
            oval(bodyCx - 4.5f, headY, bodyCx + 4.5f, bodyCy - m.bodyH * 0.20f, if (back) p.head else p.belly)
        }
        oval(bodyCx - headR - 1.4f, headY - headR - 1.4f, bodyCx + headR + 1.4f, headY + headR + 1.4f, outline)
        oval(bodyCx - headR, headY - headR, bodyCx + headR, headY + headR, p.head)

        if (back) {
            // 뒷머리의 후두부/목깃. 눈과 부리는 보이지 않는다.
            oval(bodyCx - headR * 0.72f, headY - headR * 0.62f,
                bodyCx + headR * 0.72f, headY + headR * 0.28f, shade(p.head, 1.13f))
            if (def.art.pattern == BirdPatterns.DARK_CAP || def.art.pattern == BirdPatterns.COLLAR) {
                rect(bodyCx - headR * 0.75f, headY - headR * 0.72f,
                    bodyCx + headR * 0.75f, headY - headR * 0.35f, p.accent)
            }
        } else {
            // 두 눈과 정면 부리.
            for (ex in floatArrayOf(bodyCx - headR * 0.42f, bodyCx + headR * 0.42f)) {
                oval(ex - 2f, headY - 2.5f, ex + 2f, headY + 1.5f, shade(p.head, 0.22f))
                rect(ex - 0.8f, headY - 1.7f, ex + 0.2f, headY - 0.7f, 0xFFFFFFFF.toInt())
            }
            path(shade(p.beak, 0.45f), bodyCx - 3.5f, headY + 2f, bodyCx, headY + 2f + m.bill * 0.56f, bodyCx + 3.5f, headY + 2f)
            path(p.beak, bodyCx - 2.5f, headY + 2f, bodyCx, headY + 2f + m.bill * 0.43f, bodyCx + 2.5f, headY + 2f)
            if (def.art.pattern == BirdPatterns.EYE_STRIPE || def.art.pattern == BirdPatterns.DARK_CAP) {
                rect(bodyCx - headR * 0.75f, headY - headR * 0.58f,
                    bodyCx + headR * 0.75f, headY - headR * 0.35f, p.accent)
            }
        }
    }

    /** 투명 여백을 제거해 필드 충돌 중심과 사진 확대 중심이 실제 새에 맞게 한다. */
    private fun crop(src: Bitmap, pad: Int): Bitmap {
        var left = src.width; var top = src.height; var right = -1; var bottom = -1
        for (y in 0 until src.height) for (x in 0 until src.width) {
            if (Color.alpha(src.getPixel(x, y)) > 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return src
        left = (left - pad).coerceAtLeast(0); top = (top - pad).coerceAtLeast(0)
        right = (right + pad).coerceAtMost(src.width - 1); bottom = (bottom + pad).coerceAtMost(src.height - 1)
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1)
    }
}

/** 사진집에 남기는 한 장의 촬영 기록. */
data class BirdPhotoRecord(
    val id: String,
    val birdId: String,
    val stars: Int,
    val regionId: String,
    val day: Int,
    val time: Float,
    val weatherId: String,
    val facing: BirdFacing,
    val pose: BirdPose,
    val fileName: String,
    val camera: String,
    val distance: Float
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("id", id); put("bird", birdId); put("stars", stars)
        put("region", regionId); put("day", day); put("time", time.toDouble())
        put("weather", weatherId); put("facing", facing.key); put("pose", pose.key)
        put("file", fileName); put("camera", camera); put("distance", distance.toDouble())
    }

    companion object {
        fun fromJSON(j: JSONObject): BirdPhotoRecord? {
            val id = j.optString("id", "")
            val bird = j.optString("bird", "")
            if (id.isBlank() || bird.isBlank()) return null
            return BirdPhotoRecord(
                id, bird, j.optInt("stars", 1).coerceIn(1, 3),
                j.optString("region", START_REGION_ID), j.optInt("day", 1).coerceAtLeast(1),
                j.optDouble("time", 12.0).toFloat().coerceIn(0f, 24f),
                j.optString("weather", Weather.SUNNY.id),
                BirdFacing.of(j.optString("facing", "left")),
                BirdPose.of(j.optString("pose", "perched")),
                j.optString("file", ""), j.optString("camera", ""),
                j.optDouble("distance", 0.0).toFloat().coerceAtLeast(0f)
            )
        }
    }
}

/** 앱 내부 저장소의 사진집 이미지 파일. 네트워크·외부 저장소 권한이 필요 없다. */
object PhotoArchive {
    const val MAX_PHOTOS = 120
    private const val DIR = "bird_photos"
    private val cache = object : LruCache<String, Bitmap>(14 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // 사진집 그리기/페이지 넘김 경로는 절대로 JPEG를 동기 디코딩하지 않는다.
    // 빠른 페이지 넘김에도 파일마다 스레드를 만들지 않도록 단일 작업자로 처리한다.
    private data class ReadJob(val filesDir: File, val name: String, val generation: Long)
    private val reads = java.util.concurrent.LinkedBlockingDeque<ReadJob>()
    private val pending = java.util.concurrent.ConcurrentHashMap<String, ReadJob>()
    private val missing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cacheLock = Any()
    private var generation = 0L

    // 셔터 프레임에는 비트맵만 캐시한다. JPEG 압축과 파일 쓰기는 한 작업자에게 맡긴다.
    // 임시 파일을 완성한 뒤 이름을 바꿔, 사진집에서 미완성 JPEG를 읽지 않게 한다.
    private data class WriteJob(val folder: File, val name: String, val bitmap: Bitmap, val serial: Long)
    private val pendingWrites = HashMap<String, WriteJob>() // cacheLock 아래에서만 접근
    private val writeSerial = java.util.concurrent.atomic.AtomicLong()
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "PizzaAndBirdPhotoSave").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    private val reader = Thread({
        while (true) {
            val job = reads.takeFirst()
            try {
                if (job.generation != synchronized(cacheLock) { generation }) continue
                val bmp = try {
                    BitmapFactory.decodeFile(File(File(job.filesDir, DIR), job.name).absolutePath)
                } catch (_: Exception) {
                    null
                }
                synchronized(cacheLock) {
                    // 초기화/삭제 중이던 사진이 느린 디코드 뒤에 다시 캐시에 들어오면 안 된다.
                    if (job.generation == generation && cache.get(job.name) == null) {
                        if (bmp != null) cache.put(job.name, bmp) else missing.add(job.name)
                    }
                }
            } finally {
                pending.remove(job.name, job)
            }
        }
    }, "PizzaAndBirdAlbum").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 사진은 즉시 보여주고, JPEG 인코딩/디스크 쓰기는 셔터 프레임 밖에서 처리한다. */
    fun save(context: Context, id: String, bitmap: Bitmap): String {
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val name = "photo_$safe.jpg"
        val folder = try { dir(context) } catch (_: Exception) { return "" }
        if (!folder.isDirectory) return ""
        val job = WriteJob(folder, name, bitmap, writeSerial.incrementAndGet())
        return try {
            synchronized(cacheLock) {
                pendingWrites[name] = job
                missing.remove(name)
                cache.put(name, bitmap)
                writer.execute { write(job) }
            }
            name
        } catch (_: Exception) {
            synchronized(cacheLock) {
                if (pendingWrites[name] === job) {
                    pendingWrites.remove(name)
                    cache.remove(name)
                }
            }
            ""
        }
    }

    private fun write(job: WriteJob) {
        val temporary = File(job.folder, "${job.name}.${job.serial}.tmp")
        try {
            if (synchronized(cacheLock) { pendingWrites[job.name] !== job }) return
            val encoded = temporary.outputStream().buffered().use {
                job.bitmap.compress(Bitmap.CompressFormat.JPEG, 91, it)
            }
            synchronized(cacheLock) {
                if (pendingWrites[job.name] !== job) return
                if (encoded && temporary.renameTo(File(job.folder, job.name))) {
                    missing.remove(job.name)
                }
                pendingWrites.remove(job.name)
            }
        } catch (_: Exception) {
            synchronized(cacheLock) {
                if (pendingWrites[job.name] === job) pendingWrites.remove(job.name)
            }
        } finally {
            temporary.delete()
        }
    }

    /** 화면이 꺼지거나 앱이 백그라운드로 갈 때, 저장 큐가 끝나기를 제한 시간 동안 기다린다. */
    fun awaitPendingWrites(timeoutMs: Long = 1500L) {
        val done = java.util.concurrent.CountDownLatch(1)
        writer.execute { done.countDown() }
        try {
            done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 캐시된 사진만 즉시 반환하고, 없으면 현재 화면 사진을 우선해서 읽는다. */
    fun image(context: Context, fileName: String): Bitmap? {
        if (fileName.isBlank()) return null
        cache.get(fileName)?.let { return it }
        enqueue(context, fileName, urgent = true)
        return null
    }

    /** 사진집의 이웃 사진 미리 읽기. 현재 화면 사진보다 뒤에서 처리한다. */
    fun prefetch(context: Context, fileName: String) = enqueue(context, fileName, urgent = false)

    private fun enqueue(context: Context, fileName: String, urgent: Boolean) {
        if (fileName.isBlank() || cache.get(fileName) != null || fileName in missing) return
        synchronized(cacheLock) {
            if (cache.get(fileName) != null || fileName in missing || fileName in pendingWrites) return
            val job = ReadJob(context.filesDir, fileName, generation)
            val queued = pending.putIfAbsent(fileName, job)
            if (queued == null) {
                if (urgent) reads.offerFirst(job) else reads.offerLast(job)
            } else if (urgent && queued.generation == generation && reads.remove(queued)) {
                // 이웃 미리 읽기로 줄 서 있던 사진을 현재 화면에서 요청하면 앞으로 옮긴다.
                reads.offerFirst(queued)
            }
        }
    }

    fun delete(context: Context, fileName: String) {
        if (fileName.isBlank()) return
        val file = File(dir(context), fileName)
        synchronized(cacheLock) {
            generation++
            reads.clear()
            pending.clear()
            pendingWrites.remove(fileName) // 인코딩 중이어도 완성본을 다시 만들지 않는다.
            cache.remove(fileName)
            missing.remove(fileName)
            try { file.delete() } catch (_: Exception) { }
        }
    }

    fun clear(context: Context) {
        val folder = dir(context)
        synchronized(cacheLock) {
            generation++
            reads.clear()
            pending.clear()
            pendingWrites.clear()
            missing.clear()
            cache.evictAll()
            try { folder.listFiles()?.forEach { it.delete() } } catch (_: Exception) { }
        }
    }
}
