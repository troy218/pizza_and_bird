package com.pizzaandbird.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import com.pizzaandbird.game.KoreaMap
import java.awt.geom.Area
import java.awt.geom.Line2D
import java.awt.geom.PathIterator

/** Shared map geometry: low-detail silhouette, intact DMZ, and all rendering styles. */
object KoreaMapSmoke {
    private data class Point(val x: Float, val y: Float)

    private fun point(lon: Float, lat: Float) = Point(KoreaMap.nx(lon), KoreaMap.ny(lat))

    private fun vertices(path: Path, closed: Boolean = true): List<Point> {
        val points = ArrayList<Point>()
        val coords = FloatArray(6)
        val iterator = path.p2d.getPathIterator(null)
        var closes = 0
        while (!iterator.isDone) {
            when (iterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    check(points.isEmpty()) { "Silhouette must have a single contour" }
                    points.add(Point(coords[0], coords[1]))
                }
                PathIterator.SEG_LINETO -> points.add(Point(coords[0], coords[1]))
                PathIterator.SEG_CLOSE -> closes++
                else -> error("Map outline must use straight segments")
            }
            iterator.next()
        }
        check(closes == if (closed) 1 else 0) { "Unexpected open/closed contour" }
        return points
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val north = vertices(KoreaMap.northPath)
        val south = vertices(KoreaMap.southPath)
        check(north.size <= south.size * 1.2f) { "North is too detailed: ${north.size} vs ${south.size}" }
        check(north.distinct().size == north.size) { "Duplicate coast vertices" }

        val dmz = listOf(point(126.68f, 37.98f), point(126.98f, 38.12f),
            point(127.50f, 38.31f), point(128.35f, 38.61f))
        check(vertices(KoreaMap.dmzPath, closed = false) == dmz)
        for (coast in listOf(north, south)) {
            check(listOf(coast.first()) + coast.takeLast(3).reversed() == dmz) {
                "North and South must meet along the same DMZ segments"
            }
        }
        val overlap = Area(KoreaMap.northPath.p2d)
        overlap.intersect(Area(KoreaMap.southPath.p2d))
        check(overlap.isEmpty) { "North and South must not overlap" }

        // Keep the geographic envelope, especially Hwanghae's coast below 38 degrees.
        check(north.minOf { it.x } == KoreaMap.nx(124.32f))
        check(north.maxOf { it.x } == KoreaMap.nx(130.70f))
        check(north.minOf { it.y } == KoreaMap.ny(43.00f))
        check(north.maxOf { it.y } == KoreaMap.ny(37.67f))
        check(point(125.34f, 37.67f) in north && point(126.14f, 37.73f) in north)

        for (i in north.indices) for (j in i + 1 until north.size) {
            if (j == i + 1 || (i == 0 && j == north.lastIndex)) continue
            val a = north[i]
            val b = north[(i + 1) % north.size]
            val c = north[j]
            val d = north[(j + 1) % north.size]
            check(!Line2D.linesIntersect(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(),
                c.x.toDouble(), c.y.toDouble(), d.x.toDouble(), d.y.toDouble())) {
                "Self-intersecting north coast: edges $i and $j"
            }
        }

        // Exercise minimap/large-map scales, day/night, and the paper compass style.
        for (unit in listOf(80f, 400f, 1600f)) for (detail in 0..2) for (style in 0..2) {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(64f - KoreaMap.nx(125.75f) * unit, 64f - KoreaMap.ny(39.04f) * unit)
            canvas.scale(unit, unit)
            KoreaMap.drawLand(canvas, unit, detail, night = style == 1, analog = style == 2)
            check(bitmap.getPixel(64, 64) ushr 24 != 0) { "North silhouette was not rendered" }
        }
        println("Korea map smoke OK: ${north.size}/${south.size} vertices, shared DMZ, " +
            "no overlap or self-intersections, preserved bounds, 27 render cases")
    }
}
