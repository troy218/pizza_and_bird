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

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        xs.add(x3); ys.add(y3)
    }

    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        xs.add(x2); ys.add(y2)
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

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
    fun isEmpty(): Boolean = width() <= 0 || height() <= 0
}

class Matrix {
    fun setScale(sx: Float, sy: Float) {}
}

class Bitmap private constructor(val width: Int, val height: Int) {
    enum class Config { ARGB_8888, RGB_565 }
    enum class CompressFormat { JPEG, PNG, WEBP }
    val byteCount: Int get() = width * height * 4
    private val px = IntArray(width * height)
    fun getPixel(x: Int, y: Int): Int = px.getOrElse(y * width + x) { 0 }
    fun setPixel(x: Int, y: Int, color: Int) { val i = y * width + x; if (i in px.indices) px[i] = color }
    fun compress(format: CompressFormat, quality: Int, stream: java.io.OutputStream): Boolean = true
    companion object {
        fun createBitmap(w: Int, h: Int, config: Config): Bitmap = Bitmap(w, h)
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap = Bitmap(w, h)
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int, m: Matrix?, filter: Boolean): Bitmap = Bitmap(w, h)
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap = Bitmap(w, h)
    }
}

object BitmapFactory {
    fun decodeFile(path: String): Bitmap? = null
    fun decodeStream(stream: java.io.InputStream): Bitmap? = null
}

class Canvas(private val target: Bitmap? = null) {
    constructor() : this(null)
    fun save(): Int = 0
    fun restore() {}
    fun translate(dx: Float, dy: Float) {}
    fun scale(sx: Float, sy: Float) {}
    fun rotate(deg: Float) {}
    fun rotate(deg: Float, px: Float, py: Float) {}
    fun drawOval(l: Float, t: Float, r: Float, b: Float, paint: Paint) {}
    fun drawOval(oval: RectF, paint: Paint) {}
    fun drawRect(r: RectF, paint: Paint) {}
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, paint: Paint) {}
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {}
    fun drawBitmap(bm: Bitmap, l: Float, t: Float, paint: Paint?) {}
    fun drawPath(path: Path, paint: Paint) {}
    fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) {}
    fun drawColor(color: Int) {}
}
