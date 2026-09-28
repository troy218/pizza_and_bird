package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 촬영 전용 좌표: x/z는 지도의 가로/세로(칸), y는 지면에서의 높이(칸). */
internal data class PhotoVertex(val x: Float, val y: Float, val z: Float)
internal data class PhotoPoint(val x: Float, val y: Float, val depth: Float)

/**
 * 수평을 유지하는 핀홀 카메라. 지도를 기울이거나 새를 위에서 내려다보지 않는다.
 * 플레이어 → 새의 실제 방위를 쓰되 렌즈 높이를 새의 눈높이로 낮추고 자동 프레이밍한다.
 * 같은 높이의 물체는 거리에 반비례해 작아지고 모든 지면선은 수평선으로 수렴한다.
 */
internal class PhotoCamera(
    val width: Int, val height: Int,
    fromX: Float, fromZ: Float,
    val targetX: Float, val targetZ: Float,
    val subjectHeight: Float, subjectAspect: Float, val lift: Float = 0f
) {
    private val distance = hypot(targetX - fromX, targetZ - fromZ)
    val forwardX = if (distance > 0.001f) (targetX - fromX) / distance else 0f
    val forwardZ = if (distance > 0.001f) (targetZ - fromZ) / distance else -1f
    val rightX = -forwardZ
    val rightZ = forwardX
    // 겹친 좌표/초근접 촬영도 0으로 나누거나 피사체를 near plane 뒤에 놓지 않는다.
    val subjectDepth = max(distance, 1.25f)
    val x = targetX - forwardX * subjectDepth
    val z = targetZ - forwardZ * subjectDepth
    val eyeHeight = subjectHeight * 0.56f + lift
    val horizon = height * 0.42f
    val focal = min(height * 0.50f, width * 0.40f / subjectAspect.coerceAtLeast(0.1f)) *
        subjectDepth / subjectHeight
    val near = 0.18f

    fun depth(wx: Float, wz: Float): Float = (wx - x) * forwardX + (wz - z) * forwardZ
    fun side(wx: Float, wz: Float): Float = (wx - x) * rightX + (wz - z) * rightZ

    fun project(v: PhotoVertex): PhotoPoint? {
        val d = depth(v.x, v.z)
        if (d < near) return null
        return PhotoPoint(width * 0.5f + focal * side(v.x, v.z) / d,
            horizon + focal * (eyeHeight - v.y) / d, d)
    }

    /** 지도에서 남쪽 = FRONT. 촬영 위치를 바꾸면 같은 방향의 새도 보이는 면이 달라진다. */
    fun facing(worldFacing: BirdFacing): BirdFacing {
        val dx = when (worldFacing) { BirdFacing.LEFT -> -1f; BirdFacing.RIGHT -> 1f; else -> 0f }
        val dz = when (worldFacing) { BirdFacing.FRONT -> 1f; BirdFacing.BACK -> -1f; else -> 0f }
        val sideways = dx * rightX + dz * rightZ
        val along = dx * forwardX + dz * forwardZ
        return if (abs(sideways) > abs(along)) {
            if (sideways < 0f) BirdFacing.LEFT else BirdFacing.RIGHT
        } else {
            if (along < 0f) BirdFacing.FRONT else BirdFacing.BACK
        }
    }

    /** near plane를 가로지르는 면도 잘라서 남긴다. 카메라 뒤의 면이 화면을 뒤집지 않게 한다. */
    fun clip(vertices: List<PhotoVertex>): List<PhotoVertex> {
        if (vertices.all { depth(it.x, it.z) >= near }) return vertices
        val out = ArrayList<PhotoVertex>(vertices.size + 1)
        var previous = vertices.last()
        var previousD = depth(previous.x, previous.z)
        for (v in vertices) {
            val d = depth(v.x, v.z)
            if ((d >= near) != (previousD >= near)) {
                // 작은 여유를 둬 부동소수점 반올림으로 project()에서 다시 탈락하지 않는다.
                val k = ((near + 0.0001f - previousD) / (d - previousD)).coerceIn(0f, 1f)
                out.add(PhotoVertex(previous.x + (v.x - previous.x) * k,
                    previous.y + (v.y - previous.y) * k, previous.z + (v.z - previous.z) * k))
            }
            if (d >= near) out.add(v)
            previous = v
            previousD = d
        }
        return out
    }
}

/**
 * 오프라인 소프트웨어 3D 사진 렌더러. 탐색/조준용 탑다운 월드와 촬영 투영을 분리한다.
 * 실제 지면은 역원근 텍스처 투영, 나무/바위/건물은 높이와 명암을 가진 입체 메시,
 * 새는 기존 종별 정밀 리그를 카메라에 보이는 방향으로 세운다. GPU/외부 엔진 불필요.
 * 결과 비트맵 하나를 인화 카드와 사진집이 공유하므로 다시 열어도 구도가 바뀌지 않는다.
 */
object PerspectivePhoto {
    const val WIDTH = 720
    const val HEIGHT = 405
    data class Capture(val bitmap: Bitmap, val facing: BirdFacing, val pose: BirdPose)

    fun capture(
        assets: Assets, map: GameMap, bird: FieldBird,
        photographerX: Float, photographerY: Float,
        hour: Float, weather: Weather, season: Season, moment: Float
    ): Capture {
        // 기존 월드 렌더/스폰과 동일한 발 위치를 사용한다. cy(몸 중심)를 쓰면 물가가 어긋난다.
        val tx = bird.cx / 16f
        val tz = (bird.y + bird.sprH) / 16f
        val birdHeight = when (bird.def.art.template) {
            2 -> 1.35f
            3, 11 -> 0.95f
            4, 5, 7 -> 0.80f
            9, 10, 12 -> 0.58f
            else -> 0.70f
        } * bird.def.art.scale
        val aim = PhotoCamera(WIDTH, HEIGHT, photographerX / 16f, photographerY / 16f,
            tx, tz, birdHeight, 1f, bird.hopLift / 16f)
        val facing = aim.facing(bird.facing)
        val pose = bird.renderPose
        val sprite = assets.birdPose(bird.def.id, facing, pose)
        val camera = PhotoCamera(WIDTH, HEIGHT, photographerX / 16f, photographerY / 16f,
            tx, tz, birdHeight, sprite.width.toFloat() / sprite.height, bird.hopLift / 16f)
        return Capture(Frame(assets, map, camera, sprite, hour, weather, season, moment).render(), facing, pose)
    }

    private class Frame(
        private val assets: Assets, private val map: GameMap, private val camera: PhotoCamera,
        private val bird: Bitmap, private val hour: Float, private val weather: Weather,
        private val season: Season, private val moment: Float
    ) {
        private val w = camera.width.toFloat()
        private val h = camera.height.toFloat()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val spritePaint = Paint().apply { isFilterBitmap = false }
        // 방향별 리그의 투명 여백이 아니라 실제 발끝을 지면에 맞춘다.
        private val footPadding = run {
            var last = bird.height - 1
            while (last > 0 && (0 until bird.width).all { Color.alpha(bird.getPixel(it, last)) == 0 }) last--
            bird.height - 1 - last
        }
        private val path = Path()
        private val rect = RectF()
        // ---- 시간대 · 계절은 월드와 **같은 곡선**(`DayCycle`)에서 가져온다 --------
        // 예전에는 시각을 고정 상수(18.1시 / 6.0시)와 비교해 노을을 만들었다. 그래서
        // 계절·위도에 따라 해가 지는 시각이 다른데도 사진만 늘 같은 저녁 색을 썼고,
        // "지금 낮인데 사진은 저녁" 같은 어긋남이 생겼다. 이제 월드가 쓰는 태양 고도
        // 램프(SKY · SKY_TOP · SUNLIGHT)를 그대로 쓰므로 **찍는 순간의 하늘**이 나온다.
        private val solar = DayCycle.solarHour(hour, season)
        private val golden = DayCycle.golden(hour, season)      // 일출·일몰 직전후
        private val night = DayCycle.darkness(hour, season)     // 0(한낮) ~ 1(한밤)
        private val overcast = when (weather) {
            Weather.RAIN -> 0.65f; Weather.CLOUDY, Weather.SNOW -> 0.4f; Weather.WIND -> 0.15f; else -> 0f
        }
        private val dusk = golden * (1f - overcast)
        private val skyTop = mix(DayCycle.skyTopColor(hour, season), 0xFF9AA6B4.toInt(), overcast * 0.5f)
        private val skyHorizon = mix(DayCycle.skyColor(hour, season), 0xFFA8B6BC.toInt(), overcast * 0.75f)
        private val seed = map.region.id.hashCode()

        private data class Face(val points: List<PhotoPoint>, val color: Int, val depth: Float)
        private val faces = ArrayList<Face>()

        fun render(): Bitmap {
            val bitmap = Bitmap.createBitmap(camera.width, camera.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            drawSky(c)
            drawGround(bitmap)
            drawDistantLandscape(c)
            buildScenery()
            faces.sortByDescending { it.depth }
            var birdDrawn = false
            for (face in faces) {
                if (!birdDrawn && face.depth < camera.subjectDepth) {
                    drawBird(c)
                    birdDrawn = true
                }
                path.reset()
                path.moveTo(face.points[0].x, face.points[0].y)
                for (i in 1 until face.points.size) path.lineTo(face.points[i].x, face.points[i].y)
                path.close()
                paint.color = face.color
                c.drawPath(path, paint)
            }
            if (!birdDrawn) drawBird(c)
            drawWeather(c)
            paint.shader = RadialGradient(w * 0.5f, h * 0.48f, w * 0.66f,
                Color.TRANSPARENT, Color.argb(90, 12, 20, 29), Shader.TileMode.CLAMP)
            paint.alpha = 255
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            return bitmap
        }

        private fun drawSky(c: Canvas) {
            paint.shader = LinearGradient(0f, 0f, 0f, camera.horizon,
                mix(skyTop, skyHorizon, overcast * 0.4f), skyHorizon, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            if (night > 0.8f && overcast < 0.3f) {
                paint.color = Color.argb(170, 230, 239, 251)
                for (i in 0 until 48) {
                    val hash = hash2(i, seed, 9)
                    c.drawCircle((hash % camera.width).toFloat(),
                        (hash / 719 % (camera.horizon * 0.85f).toInt()).toFloat(), if (i % 5 == 0) 1.2f else 0.7f, paint)
                }
            }
            if (overcast < 0.5f) {
                // 해는 일출(왼쪽) → 남중(높이) → 일몰(오른쪽) 경로를 따른다.
                val dayT = ((solar - 6f) / 12f).coerceIn(0f, 1f)
                val alt = (1f - abs(solar - 12f) / 6f).coerceIn(0f, 1f)
                val sx = w * (0.12f + 0.74f * dayT)
                val sy = h * (0.34f - 0.24f * alt)
                val halo = mix(0xFFFFEABB.toInt(), 0xFFFFC078.toInt(), golden)
                paint.color = Color.argb(26, Color.red(halo), Color.green(halo), Color.blue(halo))
                c.drawCircle(sx, sy, 34f, paint)
                paint.color = if (night > 0.5f) 0xFFF2F0D7.toInt() else 0xFFFFF1C3.toInt()
                c.drawCircle(sx, sy, if (night > 0.5f) 9f else 13f, paint)
            }
            for (i in 0 until 5) {
                val hash = hash2(i, seed, 19)
                val cx = ((hash % 1000) / 1000f * 1.3f - 0.15f) * w
                val cy = h * (0.08f + (hash / 1000 % 100) / 850f)
                paint.color = Color.argb(if (night > 0.5f) 28 else 100, 238, 244, 243)
                rect.set(cx - 42f, cy, cx + 67f, cy + 15f)
                c.drawOval(rect, paint)
                rect.set(cx - 13f, cy - 9f, cx + 36f, cy + 15f)
                c.drawOval(rect, paint)
            }
        }

        /** 화면 한 줄마다 지면과의 교점을 푼다. 지도 이미지를 회전/사다리꼴 변형하는 방식이 아니다. */
        private fun drawGround(bitmap: Bitmap) {
            val texture = map.photoGroundTexture(assets, moment)
            val texW = texture.width
            val texH = texture.height
            val texels = IntArray(texW * texH)
            texture.getPixels(texels, 0, texW, 0, 0, texW, texH)
            texture.recycle()
            val firstY = floor(camera.horizon).toInt() + 1
            val rows = camera.height - firstY
            val pixels = IntArray(camera.width * rows)
            val waterColor = multiply(0xFF6CABB9.toInt(), map.mapStyle.waterFilter)
            val rippleColor = mix(waterColor, 0xFFE7F2E7.toInt(), 0.34f)
            val waveColor = shade(waterColor, 0.93f)
            val waterCells = BooleanArray(map.w * map.h) { i ->
                map.groundAt(i % map.w, i / map.w) == T.WATER && map.paveAt(i % map.w, i / map.w) == Pave.NONE
            }
            val seaW = Dir.W in map.region.waterEdges; val seaE = Dir.E in map.region.waterEdges
            val seaN = Dir.N in map.region.waterEdges; val seaS = Dir.S in map.region.waterEdges
            // 조명은 채널별, 안개는 행별로 일정하다. 픽셀마다 여러 번 색을 혼합하지 않는다.
            val lighting = IntArray(256) { lit(Color.rgb(it, it, it)) }
            val reds = IntArray(256); val greens = IntArray(256); val blues = IntArray(256)
            for (y in firstY until camera.height) {
                val d = camera.focal * camera.eyeHeight / (y + 0.5f - camera.horizon)
                val step = d / camera.focal
                val side = (0.5f - w * 0.5f) * step
                var wx = camera.x + camera.forwardX * d + camera.rightX * side
                var wz = camera.z + camera.forwardZ * d + camera.rightZ * side
                val haze = fog(d)
                for (i in 0..255) {
                    val graded = mix(lighting[i], skyHorizon, haze)
                    reds[i] = Color.red(graded) shl 16
                    greens[i] = Color.green(graded) shl 8
                    blues[i] = Color.blue(graded)
                }
                val waveRow = floor(d * 22f + moment * 1.4f).toInt()
                for (x in 0 until camera.width) {
                    val outsideSea = (wx < 0f && seaW) || (wx >= map.w && seaE) ||
                        (wz < 0f && seaN) || (wz >= map.h && seaS)
                    val u = (wx * 16f).toInt().coerceIn(0, texW - 1)
                    val v = (wz * 16f).toInt().coerceIn(0, texH - 1)
                    val water = outsideSea || waterCells[(v / 16) * map.w + u / 16]
                    // 탑다운 물 타일의 가로줄을 옆에서 보면 도로처럼 늘어진다.
                    // 실제 수면 영역은 유지하고 물결만 렌즈에 수평인 잔물결로 재구성한다.
                    val col = if (water) {
                        val wave = hash2(floor((side + x * step) * 3f).toInt(), waveRow / 3, seed)
                        when {
                            waveRow % 7 == 0 && wave % 5 < 3 -> rippleColor
                            waveRow % 5 == 1 && wave % 4 < 2 -> waveColor
                            else -> waterColor
                        }
                    } else texels[v * texW + u]
                    pixels[(y - firstY) * camera.width + x] = 0xFF000000.toInt() or
                        reds[Color.red(col)] or greens[Color.green(col)] or blues[Color.blue(col)]
                    wx += camera.rightX * step
                    wz += camera.rightZ * step
                }
            }
            bitmap.setPixels(pixels, 0, camera.width, 0, firstY, camera.width, rows)
        }

        /** 카메라가 실제로 바다가 놓인 지도 경계를 바라보는지 판정한다. */
        private fun lookingToSea(): Boolean {
            val crossings = ArrayList<Pair<Float, Dir>>()
            if (camera.forwardX > 0.001f) crossings.add((map.w - camera.x) / camera.forwardX to Dir.E)
            if (camera.forwardX < -0.001f) crossings.add(-camera.x / camera.forwardX to Dir.W)
            if (camera.forwardZ > 0.001f) crossings.add((map.h - camera.z) / camera.forwardZ to Dir.S)
            if (camera.forwardZ < -0.001f) crossings.add(-camera.z / camera.forwardZ to Dir.N)
            val edge = crossings.filter { it.first >= 0f }.minByOrNull { it.first }?.second
            return edge in map.region.waterEdges
        }

        private fun drawDistantLandscape(c: Canvas) {
            if (lookingToSea()) return // 바다 쪽 수평선을 숲/산 배경으로 막지 않는다.
            val alpine = map.region.kind == RegionKind.MOUNTAIN || "mountain" in map.region.habitats
            for (layer in 0..2) {
                val baseline = camera.horizon + 3f + layer * 2f
                path.reset()
                path.moveTo(0f, baseline)
                for (i in 0..32) {
                    val phase = i * 0.43f + seed % 71 + camera.forwardX * 2f + camera.forwardZ * 3f
                    val ridge = (sin(phase) * 0.5f + 0.5f) * (if (alpine) 31f else 12f) +
                        (sin(phase * 1.73f) * 0.5f + 0.5f) * 10f
                    path.lineTo(w * i / 32f, baseline - ridge * (1f - layer * 0.2f))
                }
                path.lineTo(w, baseline)
                path.close()
                paint.color = mix(lit(0xFF52796F.toInt()), skyHorizon, 0.78f - layer * 0.15f)
                c.drawPath(path, paint)
            }
        }

        private fun buildScenery() {
            val visited = BooleanArray(map.w * map.h)
            for (z in 0 until map.h) for (x in 0 until map.w) {
                val tile = map.t(x, z)
                val family = buildingFamily(tile)
                if (family != 0) {
                    if (!visited[z * map.w + x]) buildingGroup(x, z, family, visited)
                    continue
                }
                val wx = x + 0.5f
                val wz = z + 0.5f
                val d = camera.depth(wx, wz)
                if (!visible(wx, wz, 2.5f)) continue
                val hash = hash2(x, z, seed)
                val coverRadius = when (tile) { T.TREE -> 1f; T.MOUNTAIN -> 0.85f; else -> 0.5f }
                if ((tile.prop || tile.bulk) && blocksSubject(wx, wz, coverRadius)) continue
                when (tile) {
                    T.TREE -> tree(wx, wz, hash, x, z)
                    T.ROCK -> rock(wx, wz, 0.43f, 0.32f + (hash % 8) * 0.035f, hash)
                    T.MOUNTAIN -> rock(wx, wz, 0.85f, 1.2f + (hash % 7) * 0.18f, hash)
                    T.LAMP -> {
                        box(wx - 0.035f, wz - 0.035f, wx + 0.035f, wz + 0.035f, 0f, 1.8f, 0xFF586365.toInt())
                        box(wx - 0.17f, wz - 0.13f, wx + 0.17f, wz + 0.13f, 1.65f, 1.92f,
                            if (night > 0.3f) 0xFFFFD996.toInt() else 0xFFE1D8B1.toInt())
                    }
                    T.BENCH -> {
                        box(wx - 0.45f, wz - 0.18f, wx + 0.45f, wz + 0.18f, 0.32f, 0.43f, 0xFF9E7851.toInt())
                        box(wx - 0.45f, wz + 0.14f, wx + 0.45f, wz + 0.21f, 0.43f, 0.83f, 0xFFB08B5A.toInt())
                        for (dx in listOf(-0.33f, 0.33f))
                            box(wx + dx - 0.035f, wz - 0.14f, wx + dx + 0.035f, wz + 0.14f, 0f, 0.33f, 0xFF515D58.toInt())
                    }
                    T.SIGN -> {
                        box(wx - 0.035f, wz - 0.035f, wx + 0.035f, wz + 0.035f, 0f, 0.8f, 0xFF7C6346.toInt())
                        box(wx - 0.33f, wz - 0.05f, wx + 0.33f, wz + 0.05f, 0.58f, 0.92f, 0xFFD3B983.toInt())
                    }
                    else -> if (map.paveAt(x, z) == Pave.NONE && d < camera.subjectDepth + 18f) {
                        val ground = map.groundAt(x, z)
                        if (ground == T.REED || ground == T.TALLGRASS || ground == T.FLOWER ||
                            (ground == T.GRASS && hash % 3 == 0)) {
                            plants(wx, wz, ground, hash)
                        }
                    }
                }
            }
        }

        private fun visible(x: Float, z: Float, radius: Float): Boolean {
            val d = camera.depth(x, z)
            if (d + radius < camera.near || d - radius > 65f) return false
            return abs(camera.side(x, z)) - radius < max(d, camera.near) * w / (2f * camera.focal)
        }

        /**
         * 게임은 엄폐물 뒤에서도 촬영할 수 있다. 이때 렌즈를 가리는 근접 엄폐물만 제외하고
         * 화면 양옆의 전경은 남긴다. 반투명 메시를 겹쳐 새/하늘을 어둡게 덮지 않는다.
         */
        private fun blocksSubject(x: Float, z: Float, radius: Float): Boolean {
            val nearest = camera.depth(x, z) - radius
            val halfBird = camera.subjectHeight * bird.width / bird.height * 0.5f
            val focusCone = (halfBird + 0.12f) * max(nearest, camera.near) / camera.subjectDepth
            return nearest < camera.subjectDepth && abs(camera.side(x, z)) < radius + focusCone
        }

        private fun buildingFamily(tile: T): Int = when (tile) {
            T.BLDG_ROOF, T.BLDG_WALL, T.BLDG_WIN -> 1
            T.HOUSE_ROOF, T.HOUSE_WALL, T.HOUSE_WIN, T.HOUSE_DOOR -> 2
            T.LM_ROOF, T.LM_WALL, T.LM_WIN, T.LANDMARK_DOOR -> 3
            else -> 0
        }

        /** 한 건물의 지붕/벽 타일을 묶는다. 각 타일을 별도 탑으로 세우면 안 된다. */
        private fun buildingGroup(x: Int, z: Int, family: Int, visited: BooleanArray) {
            val queue = ArrayList<Int>()
            queue.add(z * map.w + x)
            visited[z * map.w + x] = true
            var index = 0
            var x0 = x; var x1 = x; var z0 = z; var z1 = z
            while (index < queue.size) {
                val cell = queue[index++]
                val cx = cell % map.w; val cz = cell / map.w
                x0 = min(x0, cx); x1 = max(x1, cx); z0 = min(z0, cz); z1 = max(z1, cz)
                for ((dx, dz) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val nx = cx + dx; val nz = cz + dz
                    if (nx !in 0 until map.w || nz !in 0 until map.h) continue
                    val next = nz * map.w + nx
                    if (!visited[next] && buildingFamily(map.t(nx, nz)) == family) {
                        visited[next] = true
                        queue.add(next)
                    }
                }
            }
            val cx = (x0 + x1 + 1f) / 2f
            val cz = (z0 + z1 + 1f) / 2f
            val radius = max(x1 - x0 + 1f, z1 - z0 + 1f)
            if (!visible(cx, cz, radius)) return
            if (blocksSubject(cx, cz, radius * 0.71f)) return
            val top = when (family) { 1 -> 2.4f; 3 -> 2.7f; else -> 1.7f }
            val wall = when (family) { 1 -> 0xFFCCC4B0.toInt(); 3 -> 0xFFD2B78E.toInt(); else -> 0xFFE1CDA9.toInt() }
            box(x0 + 0.04f, z0 + 0.04f, x1 + 0.96f, z1 + 0.96f, 0f, top, wall)
            val left = x0 - 0.12f; val right = x1 + 1.12f
            val front = z1 + 1.12f; val back = z0 - 0.12f
            val roof = if (map.region.id == "jeonju" || family == 3) 0xFF626C72.toInt() else 0xFFA16A54.toInt()
            face(roof, PhotoVertex(left, top, front), PhotoVertex(right, top, front),
                PhotoVertex(right, top + 0.65f, cz), PhotoVertex(left, top + 0.65f, cz))
            face(shade(roof, 0.77f), PhotoVertex(left, top + 0.65f, cz), PhotoVertex(right, top + 0.65f, cz),
                PhotoVertex(right, top, back), PhotoVertex(left, top, back))
            face(shade(wall, 0.85f), PhotoVertex(left, top, back), PhotoVertex(left, top + 0.65f, cz), PhotoVertex(left, top, front))
            face(wall, PhotoVertex(right, top, front), PhotoVertex(right, top + 0.65f, cz), PhotoVertex(right, top, back))
            val glass = if (night > 0.25f) 0xFFE3C07C.toInt() else 0xFF7498A6.toInt()
            for (xx in x0..x1) {
                for (zz in listOf(z0 + 0.025f, z1 + 0.975f)) {
                    face(glass, PhotoVertex(xx + 0.24f, 0.73f, zz), PhotoVertex(xx + 0.76f, 0.73f, zz),
                        PhotoVertex(xx + 0.76f, top - 0.3f, zz), PhotoVertex(xx + 0.24f, top - 0.3f, zz))
                }
            }
            for (zz in z0..z1) {
                for (xx in listOf(x0 + 0.025f, x1 + 0.975f)) {
                    face(shade(glass, 0.85f), PhotoVertex(xx, 0.73f, zz + 0.24f), PhotoVertex(xx, 0.73f, zz + 0.76f),
                        PhotoVertex(xx, top - 0.3f, zz + 0.76f), PhotoVertex(xx, top - 0.3f, zz + 0.24f))
                }
            }
        }

        private fun tree(x: Float, z: Float, hash: Int, tx: Int, tz: Int) {
            val variant = map.mapStyle.natureArt.variant(T.TREE, tx, tz, map.region.id) ?: 0
            val pine = variant == 1 || variant == 7 || variant == 8 || variant == 11
            val tall = 1.65f + hash % 9 * 0.10f
            val leaf = multiply(when {
                season == Season.AUTUMN && !pine -> 0xFFB9974F.toInt()
                season == Season.WINTER && !pine -> 0xFF8EAA91.toInt()
                pine -> 0xFF487B68.toInt()
                else -> 0xFF7EA15E.toInt()
            }, map.mapStyle.foliageFilter)
            box(x - 0.07f, z - 0.07f, x + 0.07f, z + 0.07f, 0f, tall * 0.67f, 0xFF816C50.toInt())
            if (pine) {
                for (i in 0..2) crown(x, z, tall * (0.32f + i * 0.20f), tall * (0.78f + i * 0.11f),
                    0.70f - i * 0.17f, shade(leaf, 0.86f + i * 0.09f), hash)
            } else {
                crown(x, z, tall * 0.40f, tall, 0.74f, leaf, hash)
                crown(x - 0.23f, z + 0.10f, tall * 0.48f, tall * 0.90f, 0.53f, shade(leaf, 1.10f), hash + 2)
            }
        }

        /** 팔각 수관/암석은 각 면의 명암과 원근으로 실제 부피를 가진다. */
        private fun crown(x: Float, z: Float, bottom: Float, top: Float, radius: Float, color: Int, hash: Int) {
            val ring = (0 until 7).map { i ->
                val angle = i * 6.283185f / 7f + (hash % 17) * 0.07f
                PhotoVertex(x + cos(angle) * radius, bottom + (top - bottom) * 0.24f, z + sin(angle) * radius)
            }
            for (i in ring.indices) {
                val col = shade(color, 0.78f + 0.24f * (cos(i * 0.897f - 1f) * 0.5f + 0.5f))
                face(col, ring[i], ring[(i + 1) % ring.size], PhotoVertex(x - radius * 0.1f, top, z))
                face(shade(col, 0.80f), PhotoVertex(x, bottom, z), ring[(i + 1) % ring.size], ring[i])
            }
        }

        private fun rock(x: Float, z: Float, radius: Float, tall: Float, hash: Int) {
            val base = multiply(if (map.region.id == "jeju" || map.region.id == "hallasan")
                0xFF677573.toInt() else 0xFF9AAB9F.toInt(), map.mapStyle.stoneFilter)
            crown(x, z, 0f, tall, radius, base, hash)
            if (season == Season.WINTER && tall > 1f)
                crown(x - radius * 0.04f, z, tall * 0.77f, tall * 1.02f, radius * 0.25f, 0xFFE2EADF.toInt(), hash)
        }

        private fun plants(x: Float, z: Float, tile: T, hash: Int) {
            // 중앙 발치에는 피사체가 보일 여유를 둔다. 풀은 전경/후경으로 나뉘어 지면에 선다.
            val depth = camera.depth(x, z)
            if (depth < camera.subjectDepth * 0.60f || blocksSubject(x, z, 0.35f) ||
                hypot(x - camera.targetX, z - camera.targetZ) < 0.55f) return
            val reed = tile == T.REED
            val tall = if (reed) 0.65f else if (tile == T.TALLGRASS) 0.30f else 0.13f
            val col = multiply(if (reed || season == Season.AUTUMN) 0xFF9C9B5B.toInt() else 0xFF70944E.toInt(), map.mapStyle.foliageFilter)
            val count = if (camera.depth(x, z) > camera.subjectDepth + 9f) 2 else 4
            for (i in 0 until count) {
                val bx = x + ((hash / (i + 1) % 13) - 6) * 0.052f
                val bz = z + ((hash / (i + 7) % 11) - 5) * 0.055f
                val height = tall * (0.70f + i * 0.17f)
                val lean = if (weather == Weather.WIND) 0.20f else 0.07f
                face(shade(col, 0.8f + i * 0.08f), PhotoVertex(bx - 0.025f, 0f, bz),
                    PhotoVertex(bx + 0.035f, 0f, bz), PhotoVertex(bx + lean, height, bz + 0.035f))
                if (reed) {
                    box(bx + lean - 0.025f, bz + 0.02f, bx + lean + 0.025f, bz + 0.05f,
                        height * 0.80f, height, 0xFF8E7650.toInt())
                } else if (tile == T.FLOWER && i == 0) {
                    crown(bx + lean, bz, height * 0.75f, height + 0.035f, 0.045f,
                        if (hash % 2 == 0) 0xFFF0D88E.toInt() else 0xFFE3A9B2.toInt(), hash)
                }
            }
        }

        private fun box(x0: Float, z0: Float, x1: Float, z1: Float, bottom: Float, top: Float, col: Int) {
            val a = PhotoVertex(x0, bottom, z0); val b = PhotoVertex(x1, bottom, z0)
            val d = PhotoVertex(x0, bottom, z1); val e = PhotoVertex(x1, bottom, z1)
            val at = PhotoVertex(x0, top, z0); val bt = PhotoVertex(x1, top, z0)
            val dt = PhotoVertex(x0, top, z1); val et = PhotoVertex(x1, top, z1)
            face(shade(col, 0.72f), a, b, bt, at)
            face(shade(col, 0.88f), b, e, et, bt)
            face(col, e, d, dt, et)
            face(shade(col, 0.82f), d, a, at, dt)
            face(shade(col, 1.12f), at, bt, et, dt)
        }

        private fun face(color: Int, vararg vertices: PhotoVertex) {
            val clipped = camera.clip(vertices.toList())
            if (clipped.size < 3) return
            val points = clipped.mapNotNull { camera.project(it) }
            if (points.size < 3 || points.all { it.x < 0f } || points.all { it.x > w } ||
                points.all { it.y < 0f } || points.all { it.y > h }) return
            val depth = points.sumOf { it.depth.toDouble() }.toFloat() / points.size
            val col = mix(lit(color), skyHorizon, fog(depth))
            faces.add(Face(points, col, depth))
        }

        private fun drawBird(c: Canvas) {
            val foot = camera.project(PhotoVertex(camera.targetX, camera.lift, camera.targetZ)) ?: return
            val sh = camera.focal * camera.subjectHeight / foot.depth
            val sw = sh * bird.width / bird.height
            val spriteBottom = foot.y + footPadding * sh / bird.height
            val water = map.groundAt(floor(camera.targetX).toInt(), floor(camera.targetZ).toInt()) == T.WATER
            val ground = camera.project(PhotoVertex(camera.targetX, 0f, camera.targetZ)) ?: return
            // 원근 지면에 붙는 그림자/물결. 점프할 때도 그림자는 공중에 따라 올라가지 않는다.
            path.reset()
            for (i in 0..24) {
                val angle = i * 6.283185f / 24f
                val p = camera.project(PhotoVertex(camera.targetX + cos(angle) * camera.subjectHeight * 0.40f,
                    0.005f, camera.targetZ + sin(angle) * camera.subjectHeight * 0.18f)) ?: continue
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            paint.color = if (water) Color.argb(110, 218, 234, 221) else Color.argb(62, 27, 41, 32)
            paint.style = if (water) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = 1.5f
            c.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            if (water && camera.lift < 0.01f) {
                spritePaint.alpha = 32
                c.save()
                c.scale(1f, -0.30f, foot.x, ground.y)
                rect.set(foot.x - sw / 2f, spriteBottom - sh, foot.x + sw / 2f, spriteBottom)
                c.drawBitmap(bird, null, rect, spritePaint)
                c.restore()
                spritePaint.alpha = 255
            }
            spritePaint.colorFilter = PorterDuffColorFilter(lit(Color.WHITE), PorterDuff.Mode.MULTIPLY)
            rect.set(foot.x - sw / 2f, spriteBottom - sh, foot.x + sw / 2f, spriteBottom)
            c.drawBitmap(bird, null, rect, spritePaint)
            spritePaint.colorFilter = null
        }

        private fun drawWeather(c: Canvas) {
            val salt = seed xor (moment * 7f).toInt()
            val count = when (weather) { Weather.RAIN -> 80; Weather.SNOW -> 64; Weather.WIND -> 10; else -> 0 }
            for (i in 0 until count) {
                val hash = hash2(i, salt, 27)
                val x = (hash % camera.width).toFloat()
                val y = (hash / 719 % camera.height).toFloat()
                when (weather) {
                    Weather.RAIN -> {
                        paint.color = Color.argb(70 + i % 4 * 20, 214, 232, 243)
                        paint.strokeWidth = 0.7f + i % 3 * 0.4f
                        c.drawLine(x, y, x - 3f, y + 8f + i % 4 * 3f, paint)
                    }
                    Weather.SNOW -> {
                        paint.color = Color.argb(130 + i % 4 * 25, 246, 249, 251)
                        c.drawCircle(x, y, 0.9f + i % 4 * 0.5f, paint)
                    }
                    Weather.WIND -> {
                        paint.color = Color.argb(46, 229, 239, 230)
                        paint.strokeWidth = 1f
                        c.drawLine(x, y, x + 34f, y - 3f, paint)
                    }
                    else -> Unit
                }
            }
        }

        private fun fog(depth: Float): Float = ((depth - camera.subjectDepth * 0.4f) /
            (camera.subjectDepth + 36f)).coerceIn(0f, 0.94f)

        private fun lit(color: Int): Int = mix(mix(mix(color, 0xFF8F9DA0.toInt(), overcast * 0.24f),
            0xFFE6B183.toInt(), dusk * 0.12f), multiply(color, 0xFF697D9E.toInt()), night * 0.78f)
    }

    private fun mix(a: Int, b: Int, amount: Float): Int {
        val k = amount.coerceIn(0f, 1f)
        return Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())
    }

    private fun multiply(a: Int, b: Int): Int = Color.rgb(Color.red(a) * Color.red(b) / 255,
        Color.green(a) * Color.green(b) / 255, Color.blue(a) * Color.blue(b) / 255)

    private fun shade(color: Int, k: Float): Int = Color.rgb((Color.red(color) * k).toInt().coerceIn(0, 255),
        (Color.green(color) * k).toInt().coerceIn(0, 255), (Color.blue(color) * k).toInt().coerceIn(0, 255))
}
