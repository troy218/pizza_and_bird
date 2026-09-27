@file:Suppress("unused")

/**
 * tools/preview — android.graphics.drawable 스텁.
 * res/drawable/art_*.xml (VectorDrawable) 을 파싱해 Canvas 에 그린다.
 * (게임의 SVG 마스터 아트 파이프라인 프리뷰 렌더링용)
 */
package android.graphics.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

open class Drawable {
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0

    fun setBounds(l: Int, t: Int, r: Int, b: Int) {
        left = l; top = t; right = r; bottom = b
    }

    open fun draw(c: Canvas) {}
}

class VectorArtDrawable private constructor(
    private val viewportW: Float,
    private val viewportH: Float,
    private val paths: List<Pair<Int, Path>>
) : Drawable() {

    companion object {
        fun load(name: String): VectorArtDrawable? {
            val candidates = listOf(
                java.io.File("app/src/main/res/drawable/$name.xml"),
                java.io.File("src/main/res/drawable/$name.xml"),
                java.io.File("$name.xml")
            )
            val f = candidates.firstOrNull { it.isFile } ?: return null
            val text = f.readText(Charsets.UTF_8)
            val vpW = Regex("""android:viewportWidth="([^"]+)"""").find(text)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: 24f
            val vpH = Regex("""android:viewportHeight="([^"]+)"""").find(text)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: 24f
            val out = ArrayList<Pair<Int, Path>>()
            for (m in Regex("<path\\b[^>]*?/>").findAll(text)) {
                val tag = m.value
                val color = Regex("""android:fillColor="([^"]+)"""").find(tag)
                    ?.groupValues?.get(1)?.let { Color.parseColor(it) } ?: 0xFF000000.toInt()
                val data = Regex("""android:pathData="([^"]+)"""").find(tag)
                    ?.groupValues?.get(1) ?: continue
                out += color to parsePath(data)
            }
            return VectorArtDrawable(vpW, vpH, out)
        }

        private fun parsePath(data: String): Path {
            val path = Path()
            val tokens = Regex("[A-Za-z]|[-+]?(?:\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?")
                .findAll(data).map { it.value }.toList()
            var i = 0
            var cx = 0f
            var cy = 0f
            var sx = 0f
            var sy = 0f
            var cmd = 'M'
            fun next(): Float = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
            while (i < tokens.size) {
                val t = tokens[i]
                if (t.length == 1 && t[0].isLetter()) {
                    cmd = t[0]
                    i++
                }
                when (cmd) {
                    'M' -> { cx = next(); cy = next(); path.moveTo(cx, cy); sx = cx; sy = cy; cmd = 'L' }
                    'm' -> { cx += next(); cy += next(); path.moveTo(cx, cy); sx = cx; sy = cy; cmd = 'l' }
                    'L' -> { cx = next(); cy = next(); path.lineTo(cx, cy) }
                    'l' -> { cx += next(); cy += next(); path.lineTo(cx, cy) }
                    'H' -> { cx = next(); path.lineTo(cx, cy) }
                    'h' -> { cx += next(); path.lineTo(cx, cy) }
                    'V' -> { cy = next(); path.lineTo(cx, cy) }
                    'v' -> { cy += next(); path.lineTo(cx, cy) }
                    'C' -> {
                        val x1 = next(); val y1 = next(); val x2 = next(); val y2 = next()
                        cx = next(); cy = next(); path.cubicTo(x1, y1, x2, y2, cx, cy)
                    }
                    'c' -> {
                        val x1 = cx + next(); val y1 = cy + next(); val x2 = cx + next(); val y2 = cy + next()
                        cx += next(); cy += next(); path.cubicTo(x1, y1, x2, y2, cx, cy)
                    }
                    'Q' -> {
                        val x1 = next(); val y1 = next()
                        cx = next(); cy = next(); path.quadTo(x1, y1, cx, cy)
                    }
                    'q' -> {
                        val x1 = cx + next(); val y1 = cy + next()
                        cx += next(); cy += next(); path.quadTo(x1, y1, cx, cy)
                    }
                    'Z', 'z' -> { path.close(); cx = sx; cy = sy }
                    'A', 'a' -> {
                        next(); next(); next(); next(); next()
                        val x = next(); val y = next()
                        if (cmd == 'A') { cx = x; cy = y } else { cx += x; cy += y }
                        path.lineTo(cx, cy)
                    }
                    else -> { /* 알 수 없는 토큰은 무시 */ }
                }
            }
            return path
        }
    }

    override fun draw(c: Canvas) {
        if (viewportW <= 0f || viewportH <= 0f || paths.isEmpty()) return
        val sx = (right - left) / viewportW
        val sy = (bottom - top) / viewportH
        val save = c.save()
        c.translate(left.toFloat(), top.toFloat())
        c.scale(sx, sy)
        val paint = Paint()
        for ((color, path) in paths) {
            paint.color = color
            c.drawPath(path, paint)
        }
        c.restoreToCount(save)
    }
}
