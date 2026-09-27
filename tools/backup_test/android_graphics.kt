// [P05] JVM 검증용 android.graphics 스텁 — Data.kt 가 참조하는 KoreaMap.kt 를
// **실제 소스 그대로** 컴파일하기 위해 필요한 최소 그림 API.
// Path 는 좌표를 실제로 기록해서 computeBounds 가 진짜 경계를 돌려준다
// (그래야 KoreaMap 의 init 블록이 기기에서와 같은 값을 낸다).
package android.graphics

class Color {
    companion object {
        fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            (a shl 24) or (r shl 16) or (g shl 8) or b

        fun alpha(c: Int): Int = c ushr 24
        fun red(c: Int): Int = (c shr 16) and 0xFF
        fun green(c: Int): Int = (c shr 8) and 0xFF
        fun blue(c: Int): Int = c and 0xFF
    }
}

class RectF(
    @JvmField var left: Float = 0f,
    @JvmField var top: Float = 0f,
    @JvmField var right: Float = 0f,
    @JvmField var bottom: Float = 0f
) {
    fun set(o: RectF) {
        left = o.left; top = o.top; right = o.right; bottom = o.bottom
    }

    fun set(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
    }

    fun union(o: RectF) {
        left = minOf(left, o.left)
        top = minOf(top, o.top)
        right = maxOf(right, o.right)
        bottom = maxOf(bottom, o.bottom)
    }

    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    fun isEmpty(): Boolean = left >= right || top >= bottom
}

class PointF(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f) {
    fun set(x: Float, y: Float) {
        this.x = x; this.y = y
    }
}

class Path {
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()

    fun moveTo(x: Float, y: Float) {
        xs.add(x); ys.add(y)
    }

    fun lineTo(x: Float, y: Float) {
        xs.add(x); ys.add(y)
    }

    fun close() {}

    fun reset() {
        xs.clear(); ys.clear()
    }

    fun computeBounds(bounds: RectF, exact: Boolean) {
        if (xs.isEmpty()) {
            bounds.set(0f, 0f, 0f, 0f)
            return
        }
        bounds.set(xs.min(), ys.min(), xs.max(), ys.max())
    }
}

open class PathEffect

class DashPathEffect(intervals: FloatArray, phase: Float) : PathEffect()

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }

    var color: Int = 0
    var alpha: Int = 255
    var style: Style = Style.FILL
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var pathEffect: PathEffect? = null
    var isAntiAlias: Boolean = false
    var isFilterBitmap: Boolean = false
    var textSize: Float = 0f
    var letterSpacing: Float = 0f
    var isFakeBoldText: Boolean = false

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
    }
}

class Canvas {
    fun save(): Int = 0
    fun restore() {}
    fun translate(dx: Float, dy: Float) {}
    fun scale(sx: Float, sy: Float) {}
    fun drawPath(path: Path, paint: Paint) {}
    fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) {}
    fun drawColor(color: Int) {}
}
