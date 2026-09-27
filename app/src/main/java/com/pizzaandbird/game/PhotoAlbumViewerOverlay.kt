package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/** 사진집 한 장 크게 보기. 좌우로 넘기며 촬영 방향·자세·장소·장비를 확인한다. */
class PhotoAlbumViewerOverlay(
    scene: Scene,
    initialId: String
) : Overlay(scene) {
    private var index = scene.game.state.photoAlbum.indexOfFirst { it.id == initialId }
        .let { if (it < 0) scene.game.state.photoAlbum.lastIndex else it }
    private var panel = RectF()
    private var closeR = RectF()
    private var prevR = RectF()
    private var nextR = RectF()
    private var navArmed = true

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Type.bind(Paint(Paint.ANTI_ALIAS_FLAG), true)
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun dp(v: Float): Float = v * scene.game.density
    private fun records(): List<BirdPhotoRecord> = scene.game.state.photoAlbum

    /** 넘길 다음/이전 사진은 미리 디코드해 둔다 — 버튼을 누른 프레임을 지켜야 한다. */
    private fun prefetchNeighbours() {
        val all = records()
        if (all.isEmpty()) return
        for (i in (index - 1)..(index + 1)) {
            if (i in all.indices) PhotoArchive.prefetch(scene.game.context, all[i].fileName)
        }
    }

    init { prefetchNeighbours() }

    private fun prev() {
        if (index > 0) {
            index--
            scene.game.sfx(Audio.Sfx.TAP, 0.45f)
            prefetchNeighbours()
        }
    }

    private fun next() {
        if (index < records().lastIndex) {
            index++
            scene.game.sfx(Audio.Sfx.TAP, 0.45f)
            prefetchNeighbours()
        }
    }

    override fun handleInput(input: Input) {
        if (input.justB || input.justBack || records().isEmpty()) {
            finished = true
            return
        }
        val tap = input.consumeTapScreen()
        if (tap != null) {
            when {
                closeR.contains(tap.x, tap.y) -> finished = true
                prevR.contains(tap.x, tap.y) -> prev()
                nextR.contains(tap.x, tap.y) -> next()
            }
        }
        val dx = input.dirX
        if (navArmed && kotlin.math.abs(dx) > 0.6f) {
            navArmed = false
            if (dx < 0f) prev() else next()
        } else if (kotlin.math.abs(dx) < 0.3f) {
            navArmed = true
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val all = records()
        if (all.isEmpty()) { finished = true; return }
        index = index.coerceIn(0, all.lastIndex)
        val record = all[index]
        val def = Birds.byId[record.birdId]
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()

        UiKit.dim(c, g, 178, bornAt)
        val pw = (w * 0.93f).coerceAtMost(dp(920f))
        val ph = (h * 0.91f).coerceAtMost(dp(520f))
        panel = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)

        c.save()
        c.translate(0f, enterShift())
        UiKit.panel(c, g, panel, 14f)

        val pad = dp(14f)
        val titleY = panel.top + dp(24f)
        text.textSize = dp(16f)
        text.color = 0xFF3B2F24.toInt()
        val title = def?.name ?: "새 사진"
        UiKit.icon(c, g, "camera", RectF(panel.left + pad, titleY - dp(14f), panel.left + pad + dp(16f), titleY + dp(2f)))
        c.drawText(title, panel.left + pad + dp(21f), titleY, text)
        text.textSize = dp(10f)
        text.color = 0xFF8A7360.toInt()
        val count = "${index + 1} / ${all.size}"
        c.drawText(count, panel.centerX() - text.measureText(count) / 2f, titleY, text)

        val ccx = panel.right - dp(23f)
        val ccy = panel.top + dp(20f)
        closeR = RectF(ccx - dp(18f), ccy - dp(18f), ccx + dp(18f), ccy + dp(18f))
        UiKit.circleButton(c, g, ccx, ccy, dp(11.5f), "close", 11f)

        val photoTop = panel.top + dp(36f)
        val footerH = dp(82f)
        val photoBox = RectF(panel.left + pad, photoTop, panel.right - pad, panel.bottom - footerH)
        fill.color = 0xFF232329.toInt()
        c.drawRoundRect(photoBox, dp(7f), dp(7f), fill)

        val bmp = PhotoArchive.load(g.context, record.fileName)
        if (bmp != null) {
            // 배경 지형을 자르지 않도록 크게 보기에서는 aspect-fit을 사용한다.
            val k = min(photoBox.width() / bmp.width.toFloat(), photoBox.height() / bmp.height.toFloat())
            val dw = bmp.width * k
            val dh = bmp.height * k
            val dst = RectF(photoBox.centerX() - dw / 2f, photoBox.centerY() - dh / 2f,
                photoBox.centerX() + dw / 2f, photoBox.centerY() + dh / 2f)
            c.drawBitmap(bmp, null, dst, photoPaint)
        } else if (def != null) {
            val bird = g.assets.birdPose(def.id, record.facing, record.pose)
            val k = min(photoBox.width() * 0.38f / bird.width, photoBox.height() * 0.64f / bird.height)
            c.drawBitmap(bird, null, RectF(
                photoBox.centerX() - bird.width * k / 2f, photoBox.centerY() - bird.height * k / 2f,
                photoBox.centerX() + bird.width * k / 2f, photoBox.centerY() + bird.height * k / 2f
            ), g.assets.sprPaint)
        }
        stroke.color = Color.argb(95, 255, 246, 226)
        stroke.strokeWidth = dp(1f)
        c.drawRoundRect(photoBox, dp(7f), dp(7f), stroke)

        // 필름 아래 메타데이터: 이 정보가 사진의 방향 차이를 명확히 보여 준다.
        val region = Regions.byId[record.regionId]?.name ?: record.regionId
        val weather = Weather.fromId(record.weatherId)
        val hour = record.time.toInt().coerceIn(0, 23)
        val minute = ((record.time - hour) * 60f).toInt().coerceIn(0, 59)
        val timeLabel = String.format("%02d:%02d", hour, minute)
        val metaY = photoBox.bottom + dp(20f)

        text.textSize = dp(12.5f)
        text.color = 0xFF4A3728.toInt()
        UiKit.starRow(c, g, panel.left + pad, metaY + dp(3f), record.stars, 3, dp(9f))
        c.drawText("${record.facing.label} · ${record.pose.label}", panel.left + pad + dp(42f), metaY, text)

        text.textSize = dp(9.8f)
        text.color = 0xFF7A6855.toInt()
        val line2 = "$region · ${record.day}일차 $timeLabel · ${weather.label} · 거리 ${String.format("%.1f", record.distance)}칸"
        c.drawText(line2, panel.left + pad, metaY + dp(17f), text)
        var camera = record.camera.ifBlank { "카메라 기록 없음" }
        val cameraMax = photoBox.width() - dp(4f)
        if (text.measureText(camera) > cameraMax) {
            while (camera.length > 1 && text.measureText("$camera…") > cameraMax) camera = camera.dropLast(1)
            camera += "…"
        }
        c.drawText(camera, panel.left + pad, metaY + dp(33f), text)

        val by = panel.bottom - dp(29f)
        prevR = RectF(panel.left + pad, by, panel.left + pad + dp(92f), by + dp(22f))
        nextR = RectF(panel.right - pad - dp(92f), by, panel.right - pad, by + dp(22f))
        if (index > 0) UiKit.button(c, g, prevR, "arrow_left 이전 사진", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
        else UiKit.button(c, g, prevR, "arrow_left 이전 사진", Color.argb(80, 190, 190, 190), Color.argb(120, 74, 55, 40), 10.5f)
        if (index < all.lastIndex) UiKit.button(c, g, nextR, "arrow_right 다음 사진", UiKit.PASTEL_SKY, UiKit.INK, 10.5f)
        else UiKit.button(c, g, nextR, "arrow_right 다음 사진", Color.argb(80, 190, 190, 190), Color.argb(120, 74, 55, 40), 10.5f)

        text.textSize = dp(8.6f)
        text.color = 0xFF9A8570.toInt()
        val hint = "좌우 방향키/스틱으로 사진 넘기기 · B/ESC 닫기"
        c.drawText(hint, panel.centerX() - text.measureText(hint) / 2f, panel.bottom - dp(9f), text)
        c.restore()
    }
}
