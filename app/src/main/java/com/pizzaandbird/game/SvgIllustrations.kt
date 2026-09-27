package com.pizzaandbird.game

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import kotlin.math.ceil
import org.xmlpull.v1.XmlPullParser

/**
 * Tiny offline SVG renderer for the small, shape-based illustrations in assets/.
 * Keeping the illustration as SVG makes the art reusable/editable while drawing
 * it directly into the game's Canvas without a WebView or third-party library.
 */
class SvgIllustrations(private val assets: AssetManager) {
    private val cache = HashMap<String, SvgDocument>()

    fun draw(canvas: Canvas, name: String, bounds: RectF) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val document = cache[name] ?: load(name).also { cache[name] = it }
        document.draw(canvas, bounds)
    }

    private fun load(name: String): SvgDocument {
        assets.open(name).use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            var viewWidth = 128f
            var viewHeight = 128f
            val shapes = ArrayList<SvgShape>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "svg" -> {
                            val viewBox = parser.getAttributeValue(null, "viewBox")
                                ?.trim()?.split(Regex("[\\s,]+"))
                                ?.mapNotNull { it.toFloatOrNull() }
                            if (viewBox != null && viewBox.size == 4) {
                                viewWidth = viewBox[2].coerceAtLeast(1f)
                                viewHeight = viewBox[3].coerceAtLeast(1f)
                            } else {
                                viewWidth = parser.getAttributeValue(null, "width")?.number() ?: viewWidth
                                viewHeight = parser.getAttributeValue(null, "height")?.number() ?: viewHeight
                            }
                        }
                        "rect", "circle", "ellipse", "line", "polygon", "polyline" -> {
                            val type = parser.name
                            val values = when (type) {
                                "rect" -> floatArrayOf(
                                    parser.attr("x"), parser.attr("y"), parser.attr("width"), parser.attr("height"),
                                    parser.attr("rx"), parser.attr("ry", parser.attr("rx"))
                                )
                                "circle" -> floatArrayOf(parser.attr("cx"), parser.attr("cy"), parser.attr("r"))
                                "ellipse" -> floatArrayOf(parser.attr("cx"), parser.attr("cy"), parser.attr("rx"), parser.attr("ry"))
                                "line" -> floatArrayOf(parser.attr("x1"), parser.attr("y1"), parser.attr("x2"), parser.attr("y2"))
                                else -> parser.getAttributeValue(null, "points")
                                    ?.numberList()?.toFloatArray() ?: floatArrayOf()
                            }
                            val fill = parser.getAttributeValue(null, "fill") ?: "#000000"
                            val stroke = parser.getAttributeValue(null, "stroke")
                            val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1f
                            val fillOpacity = (parser.getAttributeValue(null, "fill-opacity")?.toFloatOrNull() ?: 1f) * opacity
                            val strokeOpacity = (parser.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull() ?: 1f) * opacity
                            val strokeWidth = parser.getAttributeValue(null, "stroke-width")?.number() ?: 1f
                            shapes += SvgShape(
                                type = type,
                                values = values,
                                fill = fill.svgColor(fillOpacity),
                                stroke = stroke?.svgColor(strokeOpacity),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                }
                event = parser.next()
            }
            return SvgDocument(viewWidth, viewHeight, shapes)
        }
    }

    private fun XmlPullParser.attr(name: String, default: Float = 0f): Float =
        getAttributeValue(null, name)?.number() ?: default

    private fun String.number(): Float = trim().removeSuffix("px").toFloatOrNull() ?: 0f

    private fun String.numberList(): List<Float> =
        Regex("[-+]?(?:\\d*\\.)?\\d+(?:[eE][-+]?\\d+)?").findAll(this)
            .mapNotNull { it.value.toFloatOrNull() }.toList()

    private fun String.svgColor(opacity: Float): Int? {
        if (this == "none") return null
        val color = Color.parseColor(this)
        val alpha = (Color.alpha(color) * opacity.coerceIn(0f, 1f)).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private data class SvgShape(
        val type: String,
        val values: FloatArray,
        val fill: Int?,
        val stroke: Int?,
        val strokeWidth: Float
    )

    private class SvgDocument(
        private val viewWidth: Float,
        private val viewHeight: Float,
        shapes: List<SvgShape>
    ) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val bitmap: Bitmap

        init {
            // SVG 도형은 정적 일러스트이므로 매 프레임 Path/RectF를 만들지 않게 한 번만 래스터화한다.
            val width = ceil(viewWidth).toInt().coerceIn(1, 1024)
            val height = ceil(viewHeight).toInt().coerceIn(1, 1024)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val raster = Canvas(bitmap)
            raster.scale(width / viewWidth, height / viewHeight)
            for (shape in shapes) drawShape(raster, shape)
        }

        fun draw(canvas: Canvas, bounds: RectF) {
            canvas.drawBitmap(bitmap, null, bounds, bitmapPaint)
        }

        private fun drawShape(canvas: Canvas, shape: SvgShape) {
            val v = shape.values
            val fill = shape.fill
            if (fill != null) {
                fillPaint.color = fill
                when (shape.type) {
                    "rect" -> {
                        val rect = RectF(v[0], v[1], v[0] + v[2], v[1] + v[3])
                        if (v[4] > 0f || v[5] > 0f) {
                            canvas.drawRoundRect(rect, v[4], v[5], fillPaint)
                        } else canvas.drawRect(rect, fillPaint)
                    }
                    "circle" -> canvas.drawCircle(v[0], v[1], v[2], fillPaint)
                    "ellipse" -> canvas.drawOval(RectF(v[0] - v[2], v[1] - v[3], v[0] + v[2], v[1] + v[3]), fillPaint)
                    "polygon", "polyline" -> if (shape.type == "polygon") {
                        canvas.drawPath(pointsPath(v, true), fillPaint)
                    }
                }
            }
            val stroke = shape.stroke
            if (stroke != null && shape.strokeWidth > 0f) {
                strokePaint.color = stroke
                strokePaint.strokeWidth = shape.strokeWidth
                when (shape.type) {
                    "rect" -> canvas.drawRoundRect(
                        RectF(v[0], v[1], v[0] + v[2], v[1] + v[3]), v[4], v[5], strokePaint
                    )
                    "circle" -> canvas.drawCircle(v[0], v[1], v[2], strokePaint)
                    "ellipse" -> canvas.drawOval(RectF(v[0] - v[2], v[1] - v[3], v[0] + v[2], v[1] + v[3]), strokePaint)
                    "line" -> canvas.drawLine(v[0], v[1], v[2], v[3], strokePaint)
                    "polygon" -> canvas.drawPath(pointsPath(v, true), strokePaint)
                    "polyline" -> canvas.drawPath(pointsPath(v, false), strokePaint)
                }
            }
        }

        private fun pointsPath(points: FloatArray, close: Boolean): Path {
            val path = Path()
            if (points.size >= 2) {
                path.moveTo(points[0], points[1])
                var i = 2
                while (i + 1 < points.size) {
                    path.lineTo(points[i], points[i + 1])
                    i += 2
                }
                if (close) path.close()
            }
            return path
        }
    }
}
