@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — android.graphics.drawable 스텁.
 *
 * res/drawable/art_*.xml (VectorDrawable, path fillColor+pathData 만 사용)을
 * Java2D로 래스터링한다. 프리뷰 파이프라인 전용이며 Android 빌드에는 포함되지 않는다.
 */
package android.graphics.drawable

import android.graphics.Canvas
import android.graphics.Rect
import android.util.Xml
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

open class Drawable {
    internal var left = 0
    internal var top = 0
    internal var right = 0
    internal var bottom = 0

    open fun setBounds(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    fun setBounds(bounds: Rect) = setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    open fun draw(c: Canvas) {}
}

/** art_*.xml (vector + path[fillColor/pathData]) 렌더러 */
class VectorArtDrawable private constructor(
    private val vw: Float,
    private val vh: Float,
    private val paths: List<Art>
) : Drawable() {

    class Art(val argb: Int, val shape: java.awt.geom.Path2D.Float)

    override fun draw(c: Canvas) {
        if (right <= left || bottom <= top || vw <= 0f || vh <= 0f) return
        val sx = (right - left) / vw
        val sy = (bottom - top) / vh
        val at = java.awt.geom.AffineTransform(sx.toDouble(), 0.0, 0.0, sy.toDouble(), left.toDouble(), top.toDouble())
        for (art in paths) {
            c.fillAwt(at.createTransformedShape(art.shape), art.argb)
        }
    }

    companion object {
        private val cache = HashMap<String, VectorArtDrawable?>()

        /** res/drawable/<artName>.xml 을 읽어 드로어블로 만든다 (없으면 null) */
        fun fromResource(artName: String): Drawable? {
            if (cache.containsKey(artName)) return cache[artName]
            val dirs = listOf(
                File("app/src/main/res/drawable"),
                File("res/drawable")
            )
            var d: VectorArtDrawable? = null
            for (dir in dirs) {
                val f = File(dir, "$artName.xml")
                if (f.exists()) { d = parse(f); break }
            }
            cache[artName] = d
            return d
        }

        private fun parse(f: File): VectorArtDrawable? {
            return try {
                f.inputStream().use { stream ->
                    val pp = Xml.newPullParser()
                    pp.setInput(stream, "UTF-8")
                    var vw = 64f
                    var vh = 64f
                    val arts = ArrayList<Art>()
                    var ev = pp.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        if (ev == XmlPullParser.START_TAG) {
                            when (pp.name) {
                                "vector" -> {
                                    pp.getAttributeValue(null, "viewportWidth")?.toFloatOrNull()?.let { vw = it }
                                    pp.getAttributeValue(null, "viewportHeight")?.toFloatOrNull()?.let { vh = it }
                                }
                                "path" -> {
                                    val col = parseColor(pp.getAttributeValue(null, "fillColor"))
                                    val pd = pp.getAttributeValue(null, "pathData")
                                    if (col != 0 && pd != null) {
                                        val shape = path2d(pd)
                                        if (shape != null) arts.add(Art(col, shape))
                                    }
                                }
                            }
                        }
                        ev = pp.next()
                    }
                    if (arts.isEmpty()) null else VectorArtDrawable(vw, vh, arts)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parseColor(s: String?): Int {
            if (s == null) return 0
            val hex = s.removePrefix("#")
            return when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                else -> 0
            }
        }

        // ---- SVG pathData → Path2D ----
        private val TOKEN = Regex("[MLCQZAmlcqza]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?")

        private fun path2d(d: String): java.awt.geom.Path2D.Float? {
            val ts = TOKEN.findAll(d).map { it.value }.toList()
            if (ts.isEmpty()) return null
            val p = java.awt.geom.Path2D.Float()
            var i = 0
            var cmd = ' '
            var cx = 0f
            var cy = 0f
            var sx = 0f
            var sy = 0f

            fun num(): Float { val v = ts[i].toFloat(); i++; return v }

            while (i < ts.size) {
                val t = ts[i]
                if (t.length == 1 && t[0].isLetter()) { cmd = t[0]; i++ }
                when (cmd) {
                    'M', 'm' -> {
                        if (i + 2 > ts.size) break
                        val x = num(); val y = num()
                        if (cmd == 'm') { cx += x; cy += y } else { cx = x; cy = y }
                        p.moveTo(cx, cy); sx = cx; sy = cy
                        cmd = if (cmd == 'M') 'L' else 'l'
                    }
                    'L', 'l' -> {
                        if (i + 2 > ts.size) break
                        val x = num(); val y = num()
                        if (cmd == 'l') { cx += x; cy += y } else { cx = x; cy = y }
                        p.lineTo(cx, cy)
                    }
                    'C', 'c' -> {
                        if (i + 6 > ts.size) break
                        val rel = cmd == 'c'
                        val x1 = num(); val y1 = num(); val x2 = num(); val y2 = num(); val x = num(); val y = num()
                        val ax1 = if (rel) x1 + cx else x1
                        val ay1 = if (rel) y1 + cy else y1
                        val ax2 = if (rel) x2 + cx else x2
                        val ay2 = if (rel) y2 + cy else y2
                        val ax = if (rel) x + cx else x
                        val ay = if (rel) y + cy else y
                        p.curveTo(ax1, ay1, ax2, ay2, ax, ay)
                        cx = ax; cy = ay
                    }
                    'Q', 'q' -> {
                        if (i + 4 > ts.size) break
                        var x1 = num(); var y1 = num(); val x = num(); val y = num()
                        if (cmd == 'q') { x1 += cx; y1 += cy; x += cx; y += cy }
                        p.quadTo(x1, y1, x, y)
                        cx = x; cy = y
                    }
                    'A', 'a' -> {
                        if (i + 7 > ts.size) break
                        val rx = num(); val ry = num(); val rot = num()
                        val large = num() != 0f; val sweep = num() != 0f
                        val x = num(); val y = num()
                        val fx = if (cmd == 'a') x + cx else x
                        val fy = if (cmd == 'a') y + cy else y
                        arcTo(p, cx, cy, abs(rx), abs(ry), rot, large, sweep, fx, fy)
                        cx = fx; cy = fy
                    }
                    'Z', 'z' -> {
                        p.closePath()
                        cx = sx; cy = sy
                    }
                    else -> { i++ }
                }
            }
            return p
        }

        /** SVG 원호 → 중심 매개변수화 → 90° 이하 3차 베지어 분할 */
        private fun arcTo(
            p: java.awt.geom.Path2D.Float,
            x1: Float, y1: Float, rx0: Float, ry0: Float, rotDeg: Float,
            large: Boolean, sweep: Boolean, x2: Float, y2: Float
        ) {
            var rx = rx0.toDouble(); var ry = ry0.toDouble()
            if (rx < 1e-6 || ry < 1e-6) { p.lineTo(x2, y2); return }
            val phi = Math.toRadians(rotDeg.toDouble())
            val cosP = cos(phi); val sinP = sin(phi)
            val dx2 = (x1 - x2) / 2.0
            val dy2 = (y1 - y2) / 2.0
            val x1p = cosP * dx2 + sinP * dy2
            val y1p = -sinP * dx2 + cosP * dy2
            val lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
            if (lam > 1.0) { val s = sqrt(lam); rx *= s; ry *= s }
            val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
            val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
            var co = sqrt(max(0.0, num / (if (den == 0.0) 1.0 else den)))
            if (large == sweep) co = -co
            val cxp = co * rx * y1p / ry
            val cyp = -co * ry * x1p / rx
            val cx0 = cosP * cxp - sinP * cyp + (x1 + x2) / 2.0
            val cy0 = sinP * cxp + cosP * cyp + (y1 + y2) / 2.0

            fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double {
                val dot = ux * vx + uy * vy
                val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
                var a = acos((dot / (if (len == 0.0) 1.0 else len)).coerceIn(-1.0, 1.0))
                if (ux * vy - uy * vx < 0) a = -a
                return a
            }

            val th1 = ang(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
            var dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
            if (!sweep && dth > 0) dth -= 2 * Math.PI
            if (sweep && dth < 0) dth += 2 * Math.PI

            val segments = max(1, Math.ceil(abs(dth) / (Math.PI / 2)).toInt())
            val delta = dth / segments
            val t = 4.0 / 3.0 * Math.tan(delta / 4.0)

            fun pos(cs: Double, sn: Double): DoubleArray = doubleArrayOf(
                rx * cs * cosP - ry * sn * sinP + cx0,
                rx * cs * sinP + ry * sn * cosP + cy0
            )
            fun der(cs: Double, sn: Double): DoubleArray = doubleArrayOf(
                -rx * sn * cosP - ry * cs * sinP,
                -rx * sn * sinP + ry * cs * cosP
            )

            var th = th1
            for (k in 0 until segments) {
                val c1 = pos(cos(th), sin(th)); val d1 = der(cos(th), sin(th))
                val c2 = pos(cos(th + delta), sin(th + delta)); val d2 = der(cos(th + delta), sin(th + delta))
                p.curveTo(
                    c1[0] + t * d1[0], c1[1] + t * d1[1],
                    c2[0] - t * d2[0], c2[1] - t * d2[1],
                    c2[0], c2[1]
                )
                th += delta
            }
        }
    }
}
