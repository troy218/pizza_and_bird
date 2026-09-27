@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — 안드로이드 없이 게임 렌더링을 돌리기 위한 android.graphics 스텁(Java2D 구현).
 *
 * 이 파일은 프리뷰 파이프라인 전용이며 Android 빌드(app/)에는 포함되지 않는다.
 * 시그니처는 게임 코드가 사용하는 android.graphics API와 1:1로 일치시킨다.
 */
package android.graphics

import java.awt.BasicStroke
import java.awt.Color as JColor
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// GfxStats (성능 프로브용 카운터 — 프리뷰 파이프라인 전용)
// ---------------------------------------------------------------------------

/**
 * 한 프레임 동안 새로 만든 네이티브 객체와 드로우 콜을 센다.
 * perf_smoke(tools/preview/perf_smoke.kt) 이 버튼 입력 병목을 재는 데 쓴다.
 */
object Trace {
    @JvmStatic var on = false
}

object GfxStats {
    @JvmStatic var gradients = 0L
    @JvmStatic var dashes = 0L
    @JvmStatic var blurFilters = 0L
    @JvmStatic var bitmaps = 0L
    @JvmStatic var rectfs = 0L
    @JvmStatic var paths = 0L
    @JvmStatic var paints = 0L
    @JvmStatic var decodes = 0L
    /** 그리기/입력 스레드(=게임 스레드)에서 일어난 디코드만 — 병목의 핵심 지표 */
    @JvmStatic var decodesMain = 0L
    @JvmStatic var drawPath = 0L
    @JvmStatic var drawRoundRect = 0L
    @JvmStatic var drawRect = 0L
    @JvmStatic var drawCircle = 0L
    @JvmStatic var drawText = 0L
    @JvmStatic var drawBitmap = 0L
    @JvmStatic var drawLine = 0L

    @JvmStatic fun reset() {
        gradients = 0L; dashes = 0L; blurFilters = 0L; bitmaps = 0L; rectfs = 0L
        paths = 0L; paints = 0L; decodes = 0L; decodesMain = 0L
        drawPath = 0L; drawRoundRect = 0L; drawRect = 0L; drawCircle = 0L
        drawText = 0L; drawBitmap = 0L; drawLine = 0L
    }

    // ---- 디버그: 생성 지점 스택 기록 -------------------------------------
    @JvmStatic var on = false
    private val sites = LinkedHashMap<String, Int>()

    @JvmStatic fun site(what: String) {
        if (!on) return
        val st = Thread.currentThread().stackTrace
        val frames = st.filter { it.className.startsWith("com.pizzaandbird") && !it.className.contains("GfxStats") }
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        sites[what + "  @  " + frames] = (sites[what + "  @  " + frames] ?: 0) + 1
    }

    @JvmStatic fun dump() {
        for ((k, v) in sites.entries.sortedByDescending { it.value }) println("  $v x  $k")
        sites.clear()
    }

    /** [n]프레임 합계를 n 프레임 평균으로 나눈다 (measure() 가 누적값을 출력하므로). */
    @JvmStatic fun perFrame(n: Int): String {
        val d = n.coerceAtLeast(1).toDouble()
        val fmt =
            "grad=%.1f dash=%.1f bmp=%.1f rectf=%.1f path=%.1f decode=%.1f(main %.1f) | dPath=%.0f dRR=%.0f " +
                "dRect=%.0f dCir=%.0f dText=%.0f dBmp=%.0f dLine=%.0f"
        return fmt.format(
            gradients / d, dashes / d, bitmaps / d, rectfs / d, paths / d, decodes / d, decodesMain / d,
            drawPath / d, drawRoundRect / d, drawRect / d, drawCircle / d,
            drawText / d, drawBitmap / d, drawLine / d
        )
    }

    @JvmStatic fun line(): String =
        "grad=$gradients dash=$dashes blur=$blurFilters bmp=$bitmaps rectf=$rectfs " +
            "path=$paths paint=$paints decode=$decodes(main $decodesMain) | dPath=$drawPath dRR=$drawRoundRect " +
            "dRect=$drawRect dCir=$drawCircle dText=$drawText dBmp=$drawBitmap dLine=$drawLine"
}

// ---------------------------------------------------------------------------
// Color
// ---------------------------------------------------------------------------

object Color {
    @JvmStatic
    val WHITE: Int = 0xFFFFFFFF.toInt()

    @JvmStatic
    val BLACK: Int = 0xFF000000.toInt()

    @JvmStatic
    val TRANSPARENT: Int = 0

    @JvmStatic
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @JvmStatic
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)

    @JvmStatic
    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    @JvmStatic
    fun red(color: Int): Int = (color shr 16) and 0xFF

    @JvmStatic
    fun green(color: Int): Int = (color shr 8) and 0xFF

    @JvmStatic
    fun blue(color: Int): Int = color and 0xFF

    /** 프리뷰용: #RRGGBB / #AARRGGBB / 이름색 일부 */
    @JvmStatic
    fun parseColor(colorString: String): Int {
        val s = colorString.trim()
        if (s.startsWith("#")) {
            val v = s.substring(1).toLongOrNull(16) ?: 0L
            return when (s.length) {
                7 -> (0xFF000000L or v).toInt()
                9 -> {
                    val a = ((v ushr 24) and 0xFF).toInt(); val rgb = (v and 0xFFFFFFL).toInt()
                    (a shl 24) or rgb
                }
                4 -> { // #RGB
                    val r = s[1].digitToInt(16) * 17; val g = s[2].digitToInt(16) * 17; val b = s[3].digitToInt(16) * 17
                    argb(255, r, g, b)
                }
                else -> v.toInt()
            }
        }
        return when (s.lowercase()) {
            "white" -> argb(255, 255, 255, 255)
            "black" -> argb(255, 0, 0, 0)
            "red" -> argb(255, 255, 0, 0)
            "green" -> argb(255, 0, 128, 0)
            "blue" -> argb(255, 0, 0, 255)
            "transparent" -> 0
            else -> argb(255, 0, 0, 0)
        }
    }
}

// ---------------------------------------------------------------------------
// 기하 헬퍼
// ---------------------------------------------------------------------------

class PointF(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float) {
        x = nx; y = ny
    }

    fun length(): Float = kotlin.math.sqrt(x * x + y * y)
}

class RectF {
    init { GfxStats.rectfs++ }
    var left: Float
    var top: Float
    var right: Float
    var bottom: Float

    constructor() : this(0f, 0f, 0f, 0f)

    constructor(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom
    }

    constructor(r: RectF) : this(r.left, r.top, r.right, r.bottom)

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    fun width(): Float = right - left
    fun height(): Float = bottom - top

    fun inset(dx: Float, dy: Float) {
        left += dx; top += dy; right -= dx; bottom -= dy
    }

    fun offset(dx: Float, dy: Float) {
        left += dx; top += dy; right += dx; bottom += dy
    }

    fun set(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
    }

    fun set(src: RectF) {
        left = src.left; top = src.top; right = src.right; bottom = src.bottom
    }

    fun union(r: RectF) {
        left = min(left, r.left); top = min(top, r.top)
        right = max(right, r.right); bottom = max(bottom, r.bottom)
    }
}

class Rect {
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0

    constructor()

    constructor(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom
    }

    fun width(): Int = right - left
    fun height(): Int = bottom - top
    fun centerX(): Int = (left + right) / 2
    fun centerY(): Int = (top + bottom) / 2
    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom

    fun set(l: Int, t: Int, r: Int, b: Int) {
        left = l; top = t; right = r; bottom = b
    }

    fun set(l: Float, t: Float, r: Float, b: Float) {
        left = l.toInt(); top = t.toInt(); right = r.toInt(); bottom = b.toInt()
    }
}

class Matrix {
    internal val tx = AffineTransform()

    fun setScale(sx: Float, sy: Float) {
        tx.setToIdentity()
        tx.scale(sx.toDouble(), sy.toDouble())
    }

    fun postScale(sx: Float, sy: Float) {
        tx.scale(sx.toDouble(), sy.toDouble())
    }

    fun postScale(sx: Float, sy: Float, px: Float, py: Float) {
        tx.translate(px.toDouble(), py.toDouble())
        tx.scale(sx.toDouble(), sy.toDouble())
        tx.translate(-px.toDouble(), -py.toDouble())
    }

    fun postTranslate(dx: Float, dy: Float) {
        tx.translate(dx.toDouble(), dy.toDouble())
    }

    fun reset() {
        tx.setToIdentity()
    }
}

// ---------------------------------------------------------------------------
// Path
// ---------------------------------------------------------------------------

class Path {
    init { GfxStats.paths++ }
    enum class Direction { CW, CCW }

    internal val p2d = Path2D.Float(Path2D.WIND_NON_ZERO)

    fun moveTo(x: Float, y: Float) = p2d.moveTo(x, y)
    fun lineTo(x: Float, y: Float) = p2d.lineTo(x, y)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        p2d.curveTo(x1, y1, x2, y2, x3, y3)
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) =
        p2d.quadTo(x1, y1, x2, y2)


    fun close() = p2d.closePath()
    fun reset() = p2d.reset()

    fun addCircle(x: Float, y: Float, radius: Float, dir: Direction) {
        // 화면 좌표(y 아래+)에서 각도 증가 = 시계방향(화면 기준) = Android Direction.CW
        val n = 40
        val step = if (dir == Direction.CW) 1 else -1
        var a = 0.0
        p2d.moveTo(x + radius, y)
        for (i in 1..n) {
            a = Math.PI * 2.0 * i / n
            p2d.lineTo(x + radius * Math.cos(step * a).toFloat(), y + radius * Math.sin(step * a).toFloat())
        }
        p2d.closePath()
    }

    fun addOval(oval: RectF, dir: Direction) = addCircle(oval.centerX(), oval.centerY(), min(oval.width(), oval.height()) / 2f, dir)

    fun addRect(r: RectF, dir: Direction) {
        if (dir == Direction.CW) {
            p2d.moveTo(r.left, r.top); p2d.lineTo(r.right, r.top)
            p2d.lineTo(r.right, r.bottom); p2d.lineTo(r.left, r.bottom)
        } else {
            p2d.moveTo(r.left, r.top); p2d.lineTo(r.left, r.bottom)
            p2d.lineTo(r.right, r.bottom); p2d.lineTo(r.right, r.top)
        }
        p2d.closePath()
    }

    fun arcTo(oval: RectF, startAngle: Float, sweepAngle: Float) {
        val arc = Arc2D.Float(oval.left, oval.top, oval.width(), oval.height(), startAngle, sweepAngle, Arc2D.OPEN)
        p2d.append(arc, true)
    }

    fun addRoundRect(rect: RectF, rx: Float, ry: Float, dir: Direction) {
        // 클리핑 용도라 실루엣만 정확하면 된다 (방향 무관)
        val r = RoundRectangle2D.Float(
            rect.left, rect.top, rect.width(), rect.height(),
            min(rx * 2f, rect.width()), min(ry * 2f, rect.height())
        )
        p2d.append(r, false)
    }

    fun computeBounds(bounds: RectF, exact: Boolean) {
        val b = p2d.bounds2D
        bounds.set(b.x.toFloat(), b.y.toFloat(), (b.x + b.width).toFloat(), (b.y + b.height).toFloat())
    }
}

// ---------------------------------------------------------------------------
// Shader / Effect
// ---------------------------------------------------------------------------

open class Shader {
    /** Android API 동일: Shader.TileMode */
    enum class TileMode { CLAMP, REPEAT, MIRROR }

    open fun setLocalMatrix(matrix: Matrix?) {}
}

typealias TileMode = Shader.TileMode

class LinearGradient : Shader {
    init { GfxStats.gradients++; GfxStats.site("LinearGradient") }
    internal val gp: java.awt.Paint
    override fun setLocalMatrix(matrix: Matrix?) {}

    constructor(
        x0: Float, y0: Float, x1: Float, y1: Float,
        color0: Int, color1: Int, tileMode: TileMode = TileMode.CLAMP
    ) : super() {
        gp = GradientPaint(x0, y0, JColor(color0, true), x1, y1, JColor(color1, true), tileMode == TileMode.REPEAT)
    }

    /** Android 동급 생성자: 다중 색 + 위치 배열 (프리뷰는 awt 다중 그라데이션으로 렌더) */
    constructor(
        x0: Float, y0: Float, x1: Float, y1: Float,
        colors: IntArray, positions: FloatArray?, tileMode: TileMode = TileMode.CLAMP
    ) : super() {
        gp = java.awt.LinearGradientPaint(
            java.awt.geom.Point2D.Float(x0, y0), java.awt.geom.Point2D.Float(x1, y1),
            fractionsOf(positions, colors.size),
            colors.map { JColor(it, true) }.toTypedArray(),
            cycle(tileMode)
        )
    }
}

class RadialGradient : Shader {
    internal val rgp: java.awt.Paint
    override fun setLocalMatrix(matrix: Matrix?) {}

    constructor(
        centerX: Float, centerY: Float, radius: Float,
        color0: Int, color1: Int, tileMode: TileMode = TileMode.CLAMP
    ) : super() {
        rgp = java.awt.RadialGradientPaint(
            centerX, centerY, max(radius, 0.01f), floatArrayOf(0f, 1f),
            arrayOf<JColor>(JColor(color0, true), JColor(color1, true))
        )
    }

    /** Android 동급 생성자: 다중 색 + 위치 배열 */
    constructor(
        centerX: Float, centerY: Float, radius: Float,
        colors: IntArray, positions: FloatArray?, tileMode: TileMode = TileMode.CLAMP
    ) : super() {
        rgp = java.awt.RadialGradientPaint(
            centerX, centerY, max(radius, 0.01f),
            fractionsOf(positions, colors.size),
            colors.map { JColor(it, true) }.toTypedArray(),
            cycle(tileMode)
        )
    }
}

private fun fractionsOf(positions: FloatArray?, n: Int): FloatArray =
    positions?.copyOf() ?: FloatArray(n) { it.toFloat() / max(n - 1, 1) }

private fun cycle(tileMode: TileMode): java.awt.MultipleGradientPaint.CycleMethod = when (tileMode) {
    TileMode.REPEAT -> java.awt.MultipleGradientPaint.CycleMethod.REPEAT
    TileMode.MIRROR -> java.awt.MultipleGradientPaint.CycleMethod.REFLECT
    TileMode.CLAMP -> java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE
}

open class Xfermode

/** 프리뷰용 타입페이스 — 실제 폰트 선택은 StubText 가 담당 */
class Typeface internal constructor(val name: String) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        @JvmStatic
        val DEFAULT: Typeface = Typeface("default")

        @JvmStatic
        val DEFAULT_BOLD: Typeface = Typeface("default-bold")

        @JvmStatic
        val SANS_SERIF: Typeface = Typeface("sans-serif")

        @JvmStatic
        val SERIF: Typeface = Typeface("serif")

        @JvmStatic
        val MONOSPACE: Typeface = Typeface("monospace")

        @JvmStatic
        fun create(family: String?, style: Int): Typeface = Typeface(family ?: "default")

        @JvmStatic
        fun create(asset: Typeface?, style: Int): Typeface = asset ?: DEFAULT

        @JvmStatic
        fun createFromAsset(mgr: android.content.res.AssetManager, path: String): Typeface? = Typeface(path)
    }
}

open class ColorFilter

class PorterDuffColorFilter(val color: Int, val mode: PorterDuff.Mode) : ColorFilter()

open class MaskFilter

class BlurMaskFilter(val radius: Float, val blur: Blur) : MaskFilter() {
    init { GfxStats.blurFilters++ }
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

object PorterDuff {
    enum class Mode { SRC, SRC_OVER, SRC_IN, DST_IN, DST_OUT, DST_OVER, CLEAR, MULTIPLY }
}

class PorterDuffXfermode(val mode: PorterDuff.Mode) : Xfermode()

open class PathEffect

class DashPathEffect(intervals: FloatArray, phase: Float) : PathEffect() {
    init { GfxStats.dashes++; GfxStats.site("Dash") }
    internal val intervals: FloatArray = intervals.copyOf()
    internal val phase: Float = phase
}

// ---------------------------------------------------------------------------
// 텍스트(폰트) 지원
// ---------------------------------------------------------------------------

object StubText {
    @Volatile var regular: Font? = null
    @Volatile var bold: Font? = null

    private val fontCache = ConcurrentHashMap<String, Font>()
    private val assetCache = ConcurrentHashMap<String, Font>()
    private val metricsCache = ConcurrentHashMap<Font, FontMetrics>()
    private val scratch: BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    /** assets 를 찾을 위치 — [android.content.res.AssetManager] 와 같은 규칙. */
    @Volatile var assetRoot: File = File("app/src/main/assets")

    fun loadFromDir(dir: File) {
        try {
            val reg = File(dir, "NotoSansKR-Regular.ttf")
            val bold = File(dir, "NotoSansKR-Bold.ttf")
            if (reg.exists()) regular = Font.createFont(Font.TRUETYPE_FONT, reg)
            if (bold.exists()) this.bold = Font.createFont(Font.TRUETYPE_FONT, bold)
            // deriveFont를 한 번 호출해 글리프 초기화
            regular?.deriveFont(12f)
            this.bold?.deriveFont(12f)
        } catch (_: Exception) {
        }
    }

    /**
     * assets 폴더에 든 게임 글꼴(app/src/main/assets/font 의 ttf)을 실제로 읽어 온다.
     * 이게 있어야 프리뷰 스크린샷이 기기와 같은 글꼴로 나온다.
     * 없으면 null → 폴백(NotoSansKR)으로 그린다.
     */
    fun assetFont(path: String): Font? = assetCache.getOrPut(path) {
        val f = listOf(File(assetRoot, path), File("app/src/main/assets/$path"), File(path))
            .firstOrNull { it.isFile } ?: return null
        try {
            Font.createFont(Font.TRUETYPE_FONT, f)
        } catch (_: Exception) {
            return null
        }
    }

    fun fontFor(size: Float, bold: Boolean): Font = fontFor(size, bold, null, 0f)

    /**
     * 페인트가 요구하는 글꼴 — 커스텀 typeface(assets) > 폴백 폰트 순.
     * [track] 은 안드로이드 letterSpacing(em)과 같은 의미로 자간을 준다.
     */
    fun fontFor(size: Float, bold: Boolean, typeface: Typeface?, track: Float): Font {
        val asset = typeface?.name?.let { if (it.endsWith(".ttf", true) || it.endsWith(".otf", true)) assetFont(it) else null }
        val fake = asset == null && bold && this.bold == null
        val base = asset ?: (if (bold) this.bold else null) ?: regular ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)
        val key = "${typeface?.name ?: if (bold) "B" else "R"}|$size|$fake|$track"
        return fontCache.getOrPut(key) {
            var f = if (fake) base.deriveFont(Font.BOLD, size) else base.deriveFont(size)
            if (track != 0f) {
                @Suppress("UNCHECKED_CAST")
                f = f.deriveFont(mapOf(java.awt.font.TextAttribute.TRACKING to track)
                    as Map<java.awt.font.TextAttribute, Any>)
            }
            f
        }
    }

    fun metrics(font: Font): FontMetrics =
        metricsCache.getOrPut(font) { scratch.createGraphics().getFontMetrics(font) }

    /**
     * 안드로이드(Minikin)와 같은 글꼴 대체 — 커스텀 글꼴에 없는 글자는 시스템 글꼴로 그린다.
     * 문자열을 "그릴 수 있는 글꼴"이 같은 구간으로 쪼개 돌려준다.
     */
    fun runs(text: String, primary: Font, size: Float, bold: Boolean): List<Pair<String, Font>> {
        if (text.isEmpty()) return emptyList()
        if (primary.canDisplayUpTo(text) < 0) return listOf(text to primary)
        val fb = fontFor(size, bold, null, 0f)
        val out = ArrayList<Pair<String, Font>>()
        val sb = StringBuilder()
        var cur = primary
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            val f = if (primary.canDisplay(cp)) primary else fb
            if (f !== cur && sb.isNotEmpty()) {
                out.add(sb.toString() to cur)
                sb.setLength(0)
            }
            cur = f
            sb.appendCodePoint(cp)
            i += n
        }
        if (sb.isNotEmpty()) out.add(sb.toString() to cur)
        return out
    }
}

// ---------------------------------------------------------------------------
// Paint
// ---------------------------------------------------------------------------

class Paint {
    init { GfxStats.paints++ }
    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }

    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    var color: Int = 0xFF000000.toInt()
    var textSize: Float = 12f
    var strokeWidth: Float = 1f
    var isAntiAlias: Boolean = false
    var isFakeBoldText: Boolean = false
    var isFilterBitmap: Boolean = false
    var style: Style = Style.FILL
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var pathEffect: PathEffect? = null
    var shader: Shader? = null
    var xfermode: Xfermode? = null
    var typeface: Typeface? = null
    var maskFilter: MaskFilter? = null
    var colorFilter: ColorFilter? = null
    var letterSpacing: Float = 0f
    var textAlign: Align = Align.LEFT

    constructor()

    constructor(flags: Int) {
        if (flags and ANTI_ALIAS_FLAG != 0) isAntiAlias = true
    }

    constructor(paint: Paint) {
        color = paint.color
        textSize = paint.textSize
        strokeWidth = paint.strokeWidth
        isAntiAlias = paint.isAntiAlias
        isFakeBoldText = paint.isFakeBoldText
        isFilterBitmap = paint.isFilterBitmap
        style = paint.style
        strokeCap = paint.strokeCap
        strokeJoin = paint.strokeJoin
        pathEffect = paint.pathEffect
        shader = paint.shader
        xfermode = paint.xfermode
        typeface = paint.typeface
        maskFilter = paint.maskFilter
        colorFilter = paint.colorFilter
        letterSpacing = paint.letterSpacing
    }

    /** 안드로이드처럼 alpha는 색상의 알파 채널과 동일하게 취급 */
    var alpha: Int
        get() = (color ushr 24) and 0xFF
        set(value) {
            color = ((value and 0xFF) shl 24) or (color and 0x00FFFFFF)
        }

    /** 이 페인트가 실제로 쓰는 AWT 폰트 (typeface · 자간까지 반영) */
    internal fun awtFont(): Font = StubText.fontFor(textSize, isFakeBoldText, typeface, letterSpacing)

    fun measureText(text: String): Float {
        val f = awtFont()
        val runs = StubText.runs(text, f, textSize, isFakeBoldText)
        if (runs.size <= 1) return StubText.metrics(f).stringWidth(text).toFloat()
        var w = 0f
        for ((part, font) in runs) w += StubText.metrics(font).stringWidth(part).toFloat()
        return w
    }

    fun ascent(): Float = -StubText.metrics(awtFont()).ascent.toFloat()

    fun descent(): Float = StubText.metrics(awtFont()).descent.toFloat()

    fun getFontMetrics(): FontMetrics = StubText.metrics(awtFont())
}

// ---------------------------------------------------------------------------
// Bitmap
// ---------------------------------------------------------------------------

class Bitmap internal constructor(val image: BufferedImage) {
    enum class Config { ARGB_8888 }

    val width: Int get() = image.width
    val height: Int get() = image.height
    val isRecycled: Boolean = false

    fun setPixel(x: Int, y: Int, c: Int) {
        if (x in 0 until width && y in 0 until height) image.setRGB(x, y, c)
    }

    fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)

    fun copy(config: Config, isMutable: Boolean): Bitmap {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        out.setData(image.copyData(null))
        return Bitmap(out)
    }

    fun eraseColor(c: Int) {
        val g = image.createGraphics()
        g.color = JColor(c, true)
        g.fillRect(0, 0, width, height)
        g.dispose()
    }

    companion object {
        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            GfxStats.bitmaps++
            return Bitmap(BufferedImage(max(width, 1), max(height, 1), BufferedImage.TYPE_INT_ARGB))
        }

        @JvmStatic
        fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config): Bitmap {
            val out = BufferedImage(max(width, 1), max(height, 1), BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    if (idx < colors.size) out.setRGB(x, y, colors[idx])
                }
            }
            return Bitmap(out)
        }

        @JvmStatic
        fun createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap {
            val base = src.image.getSubimage(x, y, max(width, 1), max(height, 1))
            val at = m?.tx ?: AffineTransform()
            val bounds = at.createTransformedShape(Rectangle2D.Float(0f, 0f, base.width.toFloat(), base.height.toFloat())).bounds2D
            val out = BufferedImage(max(bounds.width.toInt() + 2, 1), max(bounds.height.toInt() + 2, 1), BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.transform(at)
            g.drawImage(base, 0, 0, null)
            g.dispose()
            return Bitmap(out)
        }

        @JvmStatic
        fun createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
            val w = max(width, 1); val h = max(height, 1)
            val sub = src.image.getSubimage(x, y, w, h)
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            out.setData(sub.copyData(null))
            return Bitmap(out)
        }

        @JvmStatic
        fun createScaledBitmap(src: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap {
            val out = BufferedImage(max(width, 1), max(height, 1), BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                if (filter) RenderingHints.VALUE_INTERPOLATION_BILINEAR else RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            )
            g.drawImage(src.image, 0, 0, width, height, null)
            g.dispose()
            return Bitmap(out)
        }
    }
}

// ---------------------------------------------------------------------------
// BitmapFactory
// ---------------------------------------------------------------------------

/** JPEG/PNG 디코딩 스텁 — 실제 기기와 같은 자원을 같은 비트맵으로 풀어 준다. */
object BitmapFactory {
    class Options {
        @JvmField var inSampleSize: Int = 1
        @JvmField var inPreferredConfig: Bitmap.Config? = null
    }

    @JvmStatic
    fun decodeStream(stream: java.io.InputStream): Bitmap? = decodeStream(stream, null)

    @JvmStatic
    fun decodeStream(stream: java.io.InputStream, opts: Options?): Bitmap? {
        GfxStats.decodes++
        if (Thread.currentThread().name == "main") GfxStats.decodesMain++
        GfxStats.site("Decode")
        return try {
            val img = javax.imageio.ImageIO.read(stream) ?: return null
            var src: BufferedImage = img
            var sample = opts?.inSampleSize ?: 1
            while (sample > 1) {
                val w = max(1, src.width / sample)
                val h = max(1, src.height / sample)
                val small = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val sg = small.createGraphics()
                sg.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                sg.drawImage(src, 0, 0, w, h, null)
                sg.dispose()
                src = small
                sample /= 2
            }
            val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
            val og = out.createGraphics()
            og.drawImage(src, 0, 0, null)
            og.dispose()
            Bitmap(out)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun decodeFile(path: String): Bitmap? = try {
        val img = javax.imageio.ImageIO.read(java.io.File(path))
        if (img == null) {
            null
        } else {
            val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.drawImage(img, 0, 0, null)
            g.dispose()
            Bitmap(out)
        }
    } catch (_: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------
// Canvas
// ---------------------------------------------------------------------------

class Canvas {
    private var g: Graphics2D
    private val stack = ArrayList<Graphics2D>()
    private val owner: Bitmap?

    val width: Int
    val height: Int

    constructor(bmp: Bitmap) {
        owner = bmp
        width = bmp.width
        height = bmp.height
        g = bmp.image.createGraphics()
        configureDefaults()
    }

    constructor(image: BufferedImage) {
        owner = null
        width = image.width
        height = image.height
        g = image.createGraphics()
        configureDefaults()
    }

    private fun configureDefaults() {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    private fun colorize(p: Paint) {
        if (p.isAntiAlias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        }
        val sh = p.shader
        // Android modulates shader pixels by Paint.alpha; solid colors already contain it.
        // Reset every draw so a translucent gradient cannot fade later text/shapes.
        g.composite = java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER,
            if (sh is LinearGradient || sh is RadialGradient) p.alpha / 255f else 1f
        )
        when (sh) {
            is LinearGradient -> g.paint = sh.gp
            is RadialGradient -> g.paint = sh.rgp
            else -> g.paint = JColor(p.color, true)
        }
    }

    private fun strokeOf(p: Paint) {
        val dash = p.pathEffect as? DashPathEffect
        val cap = when (p.strokeCap) {
            Paint.Cap.ROUND -> BasicStroke.CAP_ROUND
            Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE
            else -> BasicStroke.CAP_BUTT
        }
        val join = when (p.strokeJoin) {
            Paint.Join.ROUND -> BasicStroke.JOIN_ROUND
            Paint.Join.BEVEL -> BasicStroke.JOIN_BEVEL
            else -> BasicStroke.JOIN_MITER
        }
        g.stroke = if (dash != null) {
            // awt BasicStroke 는 음수 phase 를 받지 않는다 (Android 는 허용) — 0 으로 보정
            val intervals = dash.intervals.map { if (it <= 0f) 1f else it }.toFloatArray()
            BasicStroke(p.strokeWidth, cap, join, 10f, intervals, dash.phase.coerceAtLeast(0f))
        } else {
            BasicStroke(p.strokeWidth, cap, join)
        }
    }

    fun drawColor(color: Int, mode: PorterDuff.Mode) {
        // 프리뷰 근사: 모드와 무관하게 덮어그리기 (실제 합성은 Android)
        drawColor(color)
    }

    fun drawColor(color: Int) {
        val old = g.composite
        g.composite = java.awt.AlphaComposite.SrcOver
        g.paint = JColor(color, true)
        val b = g.clipBounds
        g.fillRect(b?.x ?: 0, b?.y ?: 0, b?.width ?: owner?.width ?: 1, b?.height ?: owner?.height ?: 1)
        g.composite = old
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        GfxStats.drawRect++
        colorize(paint)
        val shape = Rectangle2D.Float(min(left, right), min(top, bottom), kotlin.math.abs(right - left), kotlin.math.abs(bottom - top))
        when (paint.style) {
            Paint.Style.FILL -> g.fill(shape)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(shape) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(shape); g.draw(shape) }
        }
    }

    fun drawRect(r: RectF, paint: Paint) = drawRect(r.left, r.top, r.right, r.bottom, paint)

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
        GfxStats.drawRoundRect++
        colorize(paint)
        val r = max(rx, 0f); val rY = max(ry, 0f)
        val shape = RoundRectangle2D.Float(
            rect.left, rect.top, rect.width(), rect.height(),
            min(r * 2f, rect.width()), min(rY * 2f, rect.height())
        )
        when (paint.style) {
            Paint.Style.FILL -> g.fill(shape)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(shape) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(shape); g.draw(shape) }
        }
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        GfxStats.drawCircle++
        colorize(paint)
        val shape = Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f)
        when (paint.style) {
            Paint.Style.FILL -> g.fill(shape)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(shape) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(shape); g.draw(shape) }
        }
    }

    fun drawOval(oval: RectF, paint: Paint) {
        colorize(paint)
        val shape = Ellipse2D.Float(oval.left, oval.top, oval.width(), oval.height())
        when (paint.style) {
            Paint.Style.FILL -> g.fill(shape)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(shape) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(shape); g.draw(shape) }
        }
    }

    fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {
        colorize(paint)
        val shape = Arc2D.Float(
            oval.left, oval.top, oval.width(), oval.height(),
            startAngle, sweepAngle, if (useCenter) Arc2D.PIE else Arc2D.OPEN
        )
        when (paint.style) {
            Paint.Style.FILL -> g.fill(shape)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(shape) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(shape); g.draw(shape) }
        }
    }

    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        GfxStats.drawLine++
        colorize(paint)
        strokeOf(paint)
        g.draw(java.awt.geom.Line2D.Float(startX, startY, stopX, stopY))
    }

    fun drawPath(path: Path, paint: Paint) {
        GfxStats.drawPath++
        colorize(paint)
        when (paint.style) {
            Paint.Style.FILL -> g.fill(path.p2d)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(path.p2d) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(path.p2d); g.draw(path.p2d) }
        }
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        GfxStats.drawText++
        colorize(paint)
        val base = paint.awtFont()
        // 글꼴에 없는 글자(이모지·기호)는 기기와 똑같이 시스템 글꼴로 대체해 그린다
        val runs = StubText.runs(text, base, paint.textSize, paint.isFakeBoldText)
        if (runs.size > 1 && paint.style != Paint.Style.STROKE) {
            var cx = x
            for ((part, font) in runs) {
                g.font = font
                g.drawString(part, cx, y)
                cx += StubText.metrics(font).stringWidth(part).toFloat()
            }
            return
        }
        g.font = base
        if (paint.style == Paint.Style.STROKE) {
            // 스티커 글자의 테두리 — 글리프 외곽선을 따 와서 실제로 선을 긋는다
            strokeOf(paint)
            val gv = g.font.createGlyphVector(g.fontRenderContext, text)
            g.draw(AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble())
                .createTransformedShape(gv.outline))
            return
        }
        g.drawString(text, x, y)
    }

    // PixelFont uses a white bitmap tinted with SRC_IN. Preserve its real text color
    // so contrast checks in previews do not accidentally compare white text.
    private fun filteredImage(bitmap: Bitmap, paint: Paint?): BufferedImage {
        val filter = paint?.colorFilter as? PorterDuffColorFilter ?: return bitmap.image
        if (filter.mode != PorterDuff.Mode.SRC_IN) return bitmap.image
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.drawImage(bitmap.image, 0, 0, null)
        graphics.composite = java.awt.AlphaComposite.SrcIn
        graphics.color = JColor(filter.color, true)
        graphics.fillRect(0, 0, bitmap.width, bitmap.height)
        graphics.dispose()
        return image
    }

    fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        GfxStats.drawBitmap++
        val image = filteredImage(bitmap, paint)
        val oldComp = g.composite
        g.composite = compOf(paint, (paint?.alpha ?: 255) / 255f)
        g.drawImage(image, matrix.tx, null)
        g.composite = oldComp
    }

    /** xfermode → AWT 합성 규칙 (LightMap DST_OUT 등) */
    private fun compOf(paint: Paint?, alpha: Float): java.awt.Composite {
        val xf = paint?.xfermode as? PorterDuffXfermode
        val rule = when (xf?.mode) {
            PorterDuff.Mode.SRC -> java.awt.AlphaComposite.SRC
            PorterDuff.Mode.SRC_IN -> java.awt.AlphaComposite.SRC_IN
            PorterDuff.Mode.DST_IN -> java.awt.AlphaComposite.DST_IN
            PorterDuff.Mode.DST_OUT -> java.awt.AlphaComposite.DST_OUT
            PorterDuff.Mode.DST_OVER -> java.awt.AlphaComposite.DST_OVER
            PorterDuff.Mode.CLEAR -> java.awt.AlphaComposite.CLEAR
            PorterDuff.Mode.MULTIPLY -> java.awt.AlphaComposite.SRC_OVER
            else -> java.awt.AlphaComposite.SRC_OVER
        }
        return java.awt.AlphaComposite.getInstance(rule, alpha.coerceIn(0f, 1f))
    }

    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        GfxStats.drawBitmap++
        val image = filteredImage(bitmap, paint)
        val alpha = (paint?.alpha ?: 255) / 255f
        val oldComp = g.composite
        g.composite = compOf(paint, alpha)
        g.drawImage(image, AffineTransform.getTranslateInstance(left.toDouble(), top.toDouble()), null)
        g.composite = oldComp
    }

    fun drawBitmap(bitmap: Bitmap, src: Rect, dst: Rect, paint: Paint?) {
        GfxStats.drawBitmap++
        drawBitmap(bitmap, src, RectF(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat()), paint)
    }

    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        GfxStats.drawBitmap++
        val image = filteredImage(bitmap, paint)
        val alpha = (paint?.alpha ?: 255) / 255f
        val oldComp = g.composite
        g.composite = compOf(paint, alpha)
        val at = AffineTransform.getTranslateInstance(dst.left.toDouble(), dst.top.toDouble())
        at.scale((dst.width() / bitmap.width).toDouble(), (dst.height() / bitmap.height).toDouble())
        if (src != null) {
            val sub = image.getSubimage(src.left, src.top, src.width(), src.height())
            val sx = dst.width() / sub.width
            val sy = dst.height() / sub.height
            at.setToIdentity()
            at.translate(dst.left.toDouble(), dst.top.toDouble())
            at.scale(sx.toDouble(), sy.toDouble())
            g.drawImage(sub, at, null)
        } else {
            g.drawImage(image, at, null)
        }
        g.composite = oldComp
    }

    fun save(): Int {
        stack.add(g)
        g = g.create() as Graphics2D
        return stack.size - 1
    }

    fun restoreToCount(count: Int) {
        while (stack.size > count) restore()
    }

    fun restore() {
        if (stack.isNotEmpty()) {
            g.dispose()
            g = stack.removeAt(stack.size - 1)
        }
    }

    fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())

    fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())

    fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        g.translate(px.toDouble(), py.toDouble()); g.scale(sx.toDouble(), sy.toDouble()); g.translate(-px.toDouble(), -py.toDouble())
    }

    fun skew(sx: Float, sy: Float) = g.shear(sx.toDouble(), sy.toDouble())

    fun rotate(degrees: Float) = g.rotate(Math.toRadians(degrees.toDouble()))

    fun rotate(degrees: Float, px: Float, py: Float) {
        g.translate(px.toDouble(), py.toDouble()); g.rotate(Math.toRadians(degrees.toDouble())); g.translate(-px.toDouble(), -py.toDouble())
    }

    fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        drawOval(RectF(left, top, right, bottom), paint)

    fun clipRect(rect: RectF) = clipRect(rect.left, rect.top, rect.right, rect.bottom)

    fun clipRect(l: Float, t: Float, r: Float, b: Float) {
        g.clip(Rectangle2D.Float(l, t, r - l, b - t))
    }

    fun clipPath(path: Path) {
        g.clip(path.p2d)
    }
}
