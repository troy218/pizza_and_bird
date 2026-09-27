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
// Color
// ---------------------------------------------------------------------------

object Color {
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

    /** #RGB / #RRGGBB / #AARRGGBB 문자열을 색상 정수로 */
    @JvmStatic
    fun parseColor(colorString: String): Int {
        val s = colorString.trim()
        if (!s.startsWith("#")) throw IllegalArgumentException("unknown color format: $colorString")
        val hex = s.substring(1)
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16); val g = hex[1].digitToInt(16); val b = hex[2].digitToInt(16)
                argb(255, r * 17, g * 17, b * 17)
            }
            6 -> java.lang.Long.parseLong(hex, 16).toInt() or 0xFF000000.toInt()
            8 -> java.lang.Long.parseLong(hex, 16).toInt()
            else -> throw IllegalArgumentException("unknown color format: $colorString")
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
}

class Matrix {
    internal val tx = AffineTransform()

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
    enum class Direction { CW, CCW }

    internal val p2d = Path2D.Float(Path2D.WIND_NON_ZERO)

    fun moveTo(x: Float, y: Float) = p2d.moveTo(x, y)
    fun lineTo(x: Float, y: Float) = p2d.lineTo(x, y)

    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = p2d.quadTo(x1, y1, x2, y2)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        p2d.curveTo(x1, y1, x2, y2, x3, y3)

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

open class Shader

enum class TileMode { CLAMP, REPEAT, MIRROR }

class LinearGradient(
    x0: Float, y0: Float, x1: Float, y1: Float,
    color0: Int, color1: Int, tileMode: TileMode = TileMode.CLAMP
) : Shader() {
    internal val gp = GradientPaint(x0, y0, JColor(color0, true), x1, y1, JColor(color1, true), tileMode == TileMode.REPEAT)
}

class RadialGradient(
    centerX: Float, centerY: Float, radius: Float,
    color0: Int, color1: Int, tileMode: TileMode = TileMode.CLAMP
) : Shader() {
    internal val rgp = java.awt.RadialGradientPaint(
        centerX, centerY, max(radius, 0.01f), floatArrayOf(0f, 1f),
        arrayOf<JColor>(JColor(color0, true), JColor(color1, true))
    )
}

open class PathEffect

open class MaskFilter

class BlurMaskFilter(radius: Float, style: Blur) : MaskFilter() {
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

open class Xfermode

object PorterDuff {
    enum class Mode {
        CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT,
        SRC_ATOP, DST_ATOP, XOR, DARKEN, LIGHTEN, MULTIPLY, SCREEN
    }
}

class PorterDuffXfermode(mode: PorterDuff.Mode) : Xfermode()

class DashPathEffect(intervals: FloatArray, phase: Float) : PathEffect() {
    internal val intervals: FloatArray = intervals.copyOf()
    internal val phase: Float = phase
}

// ---------------------------------------------------------------------------
// 텍스트(폰트) 지원
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Typeface — 게임 번들 글꼴(assets/fonts/*.ttf)을 실제로 로드해 프리뷰에 반영한다
// ---------------------------------------------------------------------------

open class Typeface(val stubName: String) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        @JvmField val DEFAULT = Typeface("sans-serif")
        @JvmField val DEFAULT_BOLD = Typeface("sans-serif-bold")
        @JvmField val SANS_SERIF = Typeface("sans-serif")
        @JvmField val SERIF = Typeface("serif")
        @JvmField val MONOSPACE = Typeface("monospace")

        fun create(familyName: String?, style: Int): Typeface = Typeface(familyName ?: "sans-serif")
        fun create(family: Typeface?, style: Int): Typeface = family ?: DEFAULT

        /** 프리뷰 구현: 리포지터리 앱 에셋에서 TTF를 실제로 읽어 등록한다. */
        fun createFromAsset(assets: android.content.res.AssetManager, path: String): Typeface {
            StubText.registerAssetFont(path)
            return Typeface("asset:$path")
        }
    }
}

object StubText {
    @Volatile var regular: Font? = null
    @Volatile var bold: Font? = null

    private val fontCache = ConcurrentHashMap<String, Font>()
    private val metricsCache = ConcurrentHashMap<Font, FontMetrics>()
    private val assetFonts = ConcurrentHashMap<String, Font>()
    private val scratch: BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

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

    /** 게임 에셋 글꼴 등록 (경로: app/src/main/assets 기준) */
    fun registerAssetFont(path: String) {
        assetFonts.getOrPut(path) {
            try {
                val f = File("app/src/main/assets/$path")
                if (f.exists()) Font.createFont(Font.TRUETYPE_FONT, f).deriveFont(12f)
                else Font(Font.SANS_SERIF, Font.PLAIN, 12)
            } catch (_: Exception) {
                Font(Font.SANS_SERIF, Font.PLAIN, 12)
            }
        }
    }

    fun fontFor(size: Float, bold: Boolean): Font = fontFor(size, bold, null)

    fun fontFor(size: Float, bold: Boolean, tf: Typeface?): Font {
        if (tf != null) {
            val key = tf.stubName + ":" + size
            return fontCache.getOrPut(key) {
                when {
                    tf.stubName == "monospace" -> Font(Font.MONOSPACED, Font.PLAIN, size.toInt().coerceAtLeast(1))
                    tf.stubName == "serif" -> Font(Font.SERIF, Font.PLAIN, size.toInt().coerceAtLeast(1))
                    tf.stubName.startsWith("asset:") -> {
                        val base = assetFonts[tf.stubName.removePrefix("asset:")]
                        base?.deriveFont(size) ?: fallbackFont(size, bold)
                    }
                    tf.stubName == "sans-serif-bold" -> {
                        (this.bold ?: regular)?.deriveFont(size) ?: Font(Font.SANS_SERIF, Font.BOLD, size.toInt().coerceAtLeast(1))
                    }
                    else -> fallbackFont(size, bold)
                }
            }
        }
        return fallbackFont(size, bold)
    }

    private fun fallbackFont(size: Float, bold: Boolean): Font {
        val base = (if (bold) this.bold else null) ?: regular ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)
        val key = (if (bold && this.bold == null) "B:" else "R:") + size
        return fontCache.getOrPut(key) {
            if (bold && this.bold == null)
                base.deriveFont(Font.BOLD, size)
            else
                base.deriveFont(size)
        }
    }

    fun metrics(font: Font): FontMetrics =
        metricsCache.getOrPut(font) { scratch.createGraphics().getFontMetrics(font) }
}

// ---------------------------------------------------------------------------
// Paint
// ---------------------------------------------------------------------------

class Paint {
    companion object {
        const val ANTI_ALIAS_FLAG = 1
    }

    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }

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
    var typeface: Typeface? = null
    var maskFilter: MaskFilter? = null
    var xfermode: Xfermode? = null

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
        typeface = paint.typeface
        maskFilter = paint.maskFilter
        xfermode = paint.xfermode
    }

    /** 안드로이드처럼 alpha는 색상의 알파 채널과 동일하게 취급 */
    var alpha: Int
        get() = (color ushr 24) and 0xFF
        set(value) {
            color = ((value and 0xFF) shl 24) or (color and 0x00FFFFFF)
        }

    fun measureText(text: String): Float {
        val f = StubText.fontFor(textSize, isFakeBoldText, typeface)
        return StubText.metrics(f).stringWidth(text).toFloat()
    }

    fun ascent(): Float {
        val f = StubText.fontFor(textSize, isFakeBoldText, typeface)
        return -StubText.metrics(f).ascent.toFloat()
    }

    fun descent(): Float {
        val f = StubText.fontFor(textSize, isFakeBoldText, typeface)
        return StubText.metrics(f).descent.toFloat()
    }

    fun getFontMetrics(): FontMetrics = StubText.metrics(StubText.fontFor(textSize, isFakeBoldText, typeface))
}

// ---------------------------------------------------------------------------
// Bitmap
// ---------------------------------------------------------------------------

class Bitmap private constructor(val image: BufferedImage) {
    enum class Config { ARGB_8888 }

    val width: Int get() = image.width
    val height: Int get() = image.height

    /** 프리뷰에서는 비트맵을 해제하지 않으므로 항상 false */
    val isRecycled: Boolean get() = false

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
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
            Bitmap(BufferedImage(max(width, 1), max(height, 1), BufferedImage.TYPE_INT_ARGB))

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
            BasicStroke(p.strokeWidth, cap, join, 10f, dash.intervals, dash.phase)
        } else {
            BasicStroke(p.strokeWidth, cap, join)
        }
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
        colorize(paint)
        strokeOf(paint)
        g.draw(java.awt.geom.Line2D.Float(startX, startY, stopX, stopY))
    }

    fun drawPath(path: Path, paint: Paint) {
        colorize(paint)
        when (paint.style) {
            Paint.Style.FILL -> g.fill(path.p2d)
            Paint.Style.STROKE -> { strokeOf(paint); g.draw(path.p2d) }
            Paint.Style.FILL_AND_STROKE -> { strokeOf(paint); g.fill(path.p2d); g.draw(path.p2d) }
        }
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        colorize(paint)
        g.font = StubText.fontFor(paint.textSize, paint.isFakeBoldText, paint.typeface)
        g.drawString(text, x, y)
    }

    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint) {
        val alpha = paint.alpha / 255f
        val oldComp = g.composite
        if (alpha < 1f) g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha)
        g.drawImage(bitmap.image, AffineTransform.getTranslateInstance(left.toDouble(), top.toDouble()), null)
        g.composite = oldComp
    }

    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint) {
        val alpha = paint.alpha / 255f
        val oldComp = g.composite
        if (alpha < 1f) g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha)
        val at = AffineTransform.getTranslateInstance(dst.left.toDouble(), dst.top.toDouble())
        at.scale((dst.width() / bitmap.width).toDouble(), (dst.height() / bitmap.height).toDouble())
        if (src != null) {
            val sub = bitmap.image.getSubimage(src.left, src.top, src.width(), src.height())
            val sx = dst.width() / sub.width
            val sy = dst.height() / sub.height
            at.setToIdentity()
            at.translate(dst.left.toDouble(), dst.top.toDouble())
            at.scale(sx.toDouble(), sy.toDouble())
            g.drawImage(sub, at, null)
        } else {
            g.drawImage(bitmap.image, at, null)
        }
        g.composite = oldComp
    }

    fun save(): Int {
        stack.add(g)
        g = g.create() as Graphics2D
        return stack.size
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
        g.translate(px.toDouble(), py.toDouble())
        g.scale(sx.toDouble(), sy.toDouble())
        g.translate(-px.toDouble(), -py.toDouble())
    }

    fun rotate(degrees: Float) = g.rotate(Math.toRadians(degrees.toDouble()))

    fun rotate(degrees: Float, px: Float, py: Float) =
        g.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())

    fun skew(sx: Float, sy: Float) = g.shear(sx.toDouble(), sy.toDouble())

    fun clipRect(l: Float, t: Float, r: Float, b: Float) {
        g.clip(Rectangle2D.Float(l, t, r - l, b - t))
    }

    fun clipPath(path: Path) {
        g.clip(path.p2d)
    }
}
