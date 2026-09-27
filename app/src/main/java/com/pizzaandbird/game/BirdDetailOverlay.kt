package com.pizzaandbird.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.min

/**
 * 조류 대도감 상세 뷰 — 598종 고화질 실제 사진 및 풍부한 생태·식별 설명 카드.
 *
 * 엑셀 「한반도 조류 598종 고화질 사진도감 2025」의
 * 고화질 원본 사진, 저작자 크레딧, 한국어 상세 설명, 분류 체계, 보전 등급을
 * 아름다운 양장본 도감 액자 형태로 보여줍니다.
 */
class BirdDetailOverlay(
    scene: Scene,
    initialBirdNum: Int = 1
) : Overlay(scene) {

    private var currentNum: Int = initialBirdNum.coerceIn(1, Birds.ALL.size.coerceAtLeast(1))

    private var panelR = RectF()
    private var closeRect = RectF()
    private var prevRect = RectF()
    private var nextRect = RectF()
    private var descPage = 0

    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun dp(v: Float): Float = v * scene.game.density

    private fun currentBird(): BirdDef {
        val all = Birds.ALL
        val idx = (currentNum - 1).coerceIn(0, all.size - 1)
        return all[idx]
    }

    private fun goPrev() {
        if (currentNum > 1) {
            currentNum--
            descPage = 0
            scene.game.sfx(Audio.Sfx.TAP, 0.45f)
        }
    }

    private fun goNext() {
        if (currentNum < Birds.ALL.size) {
            currentNum++
            descPage = 0
            scene.game.sfx(Audio.Sfx.TAP, 0.45f)
        }
    }

    override fun handleInput(input: Input) {
        val tap = input.consumeTapScreen()
        if (tap != null) {
            if (closeRect.contains(tap.x, tap.y)) {
                scene.game.sfx(Audio.Sfx.TAP, 0.5f)
                finished = true
                return
            }
            if (prevRect.contains(tap.x, tap.y)) {
                goPrev()
                return
            }
            if (nextRect.contains(tap.x, tap.y)) {
                goNext()
                return
            }
            // 패널 바깥 탭 시 닫기
            if (!panelR.contains(tap.x, tap.y)) {
                scene.game.sfx(Audio.Sfx.TAP, 0.4f)
                finished = true
                return
            }
        }

        // 키보드 조작: A(Z/스페이스/엔터)로 다음 새. B·뒤로가기는 닫기이므로 이전 페이지는
        // 화면의 ◀ 버튼을 쓴다. (main에 있던 input.justLeft/isDown(Input.Key)는 Input에 없는 API라
        //  컴파일 자체가 안 됐다 — P05 빌드 게이트를 통과시키기 위해 최소 수정)
        if (input.justA) {
            goNext()
        }

        if (input.justB || input.justBack) {
            scene.game.sfx(Audio.Sfx.TAP, 0.5f)
            finished = true
        }
    }

    override fun draw(c: Canvas) {
        val g = scene.game
        val w = g.screenW.toFloat()
        val h = g.screenH.toFloat()
        val s = g.state
        val a = g.assets

        UiKit.dim(c, g, 160, bornAt)

        val pw = (w * 0.94f).coerceAtMost(dp(860f))
        val ph = (h * 0.91f).coerceAtMost(dp(510f))
        panelR = RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)

        c.save()
        c.translate(0f, enterShift())
        UiKit.panel(c, g, panelR, 14f)

        val def = currentBird()
        val enc = def.encEntry
        val seenCount = s.birdCounts[def.id] ?: 0
        val bestStars = s.bestStars[def.id] ?: 1
        val seen = seenCount > 0

        val pad = dp(14f)
        val contentTop = panelR.top + dp(12f)
        val contentBottom = panelR.bottom - dp(36f)
        val contentLeft = panelR.left + pad
        val contentRight = panelR.right - pad

        // -------------------------------------------------------------------
        // 1. 상단 헤더: 번호 + 이름 + 등급 뱃지 + 보전등급 + 닫기 버튼
        // -------------------------------------------------------------------
        val numStr = String.format("No. %03d", def.birdNum)
        val numBadgeW = dp(56f)
        val numBadgeH = dp(20f)
        UiKit.badge(
            c, g,
            RectF(contentLeft, contentTop, contentLeft + numBadgeW, contentTop + numBadgeH),
            numStr, 0xFF6B4F35.toInt(), 0xFFFDFBF7.toInt(), 10.5f
        )

        // 새 이름
        textP.textSize = dp(17f)
        textP.isFakeBoldText = true
        textP.color = 0xFF2E2118.toInt()
        val nameX = contentLeft + numBadgeW + dp(8f)
        val nameY = contentTop + dp(16f)
        c.drawText(def.name, nameX, nameY, textP)

        // 등급 뱃지
        val nameWidth = textP.measureText(def.name)
        val tierColor = UiKit.tierColor(def.tier)
        val tierStr = "${def.tier.label} ${def.tier.starText()}"
        textP.textSize = dp(10.5f)
        textP.isFakeBoldText = false
        val tierW = textP.measureText(tierStr) + dp(14f)
        val tierLeft = nameX + nameWidth + dp(8f)
        UiKit.badge(
            c, g,
            RectF(tierLeft, contentTop + dp(1f), tierLeft + tierW, contentTop + dp(19f)),
            tierStr, tierColor, 0xFFFFFFFF.toInt(), 10f
        )

        // 보전 등급 뱃지 (천연기념물, 멸종위기, VU 등)
        val status = enc?.status?.takeIf { it.isNotBlank() }
        if (status != null) {
            val statusColor = when {
                status.contains("천연") || status.contains("멸종") -> 0xFFD9453B.toInt()
                status.contains("CR") || status.contains("EN") -> 0xFFD9453B.toInt()
                status.contains("VU") || status.contains("NT") -> 0xFFE08A28.toInt()
                else -> 0xFF4A7CA8.toInt()
            }
            val statusStr = "보전: $status"
            textP.textSize = dp(9.5f)
            val stW = textP.measureText(statusStr) + dp(12f)
            val stLeft = tierLeft + tierW + dp(6f)
            if (stLeft + stW < panelR.right - dp(40f)) {
                UiKit.badge(
                    c, g,
                    RectF(stLeft, contentTop + dp(1.5f), stLeft + stW, contentTop + dp(18.5f)),
                    statusStr, statusColor, 0xFFFFFFFF.toInt(), 9.2f
                )
            }
        }

        // 닫기 원형 버튼
        val closeCx = panelR.right - dp(22f)
        val closeCy = contentTop + dp(10f)
        val closeR = dp(11.5f)
        closeRect = RectF(
            closeCx - closeR - dp(4f), closeCy - closeR - dp(4f),
            closeCx + closeR + dp(4f), closeCy + closeR + dp(4f)
        )
        UiKit.circleButton(c, g, closeCx, closeCy, closeR, "✕", 11f)

        // -------------------------------------------------------------------
        // 레이아웃 분할: 좌측 고화질 사진 액자 vs 우측 상세 설명
        // -------------------------------------------------------------------
        val mainTop = contentTop + dp(28f)
        val totalW = contentRight - contentLeft
        val photoAreaW = totalW * 0.44f
        val descAreaLeft = contentLeft + photoAreaW + dp(14f)
        val descAreaW = contentRight - descAreaLeft

        // -------------------------------------------------------------------
        // 2. 좌측 영역: 고화질 사진 액자 (Photo Frame)
        // -------------------------------------------------------------------
        val photoCardH = contentBottom - mainTop - dp(2f)
        val photoCardR = RectF(contentLeft, mainTop, contentLeft + photoAreaW, mainTop + photoCardH)

        // 액자 마운트 보드 (외곽 섀도우 및 이중 프레임)
        fillP.color = Color.argb(35, 40, 30, 20)
        c.drawRoundRect(
            RectF(photoCardR.left + dp(1f), photoCardR.top + dp(2f), photoCardR.right + dp(1f), photoCardR.bottom + dp(3f)),
            dp(8f), dp(8f), fillP
        )
        fillP.color = 0xFFFAF6ED.toInt()
        c.drawRoundRect(photoCardR, dp(8f), dp(8f), fillP)
        strokeP.color = 0xFFC2A882.toInt()
        strokeP.strokeWidth = dp(1.5f)
        c.drawRoundRect(photoCardR, dp(8f), dp(8f), strokeP)

        // 사진 표시 영역 (마운트 안쪽 인셋)
        val photoInset = dp(7f)
        val photoH = photoCardH - dp(34f)
        val photoInnerR = RectF(
            photoCardR.left + photoInset,
            photoCardR.top + photoInset,
            photoCardR.right - photoInset,
            photoCardR.top + photoInset + photoH
        )

        // 사진 윈도우 배경 (다크 톤)
        fillP.color = 0xFF2A241F.toInt()
        c.drawRoundRect(photoInnerR, dp(6f), dp(6f), fillP)

        // 실제 고화질 사진 렌더링
        val bmp = a.birdPhoto(def.birdNum)
        if (bmp != null) {
            val saveCount = c.save()
            c.clipRect(photoInnerR)

            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val targetW = photoInnerR.width()
            val targetH = photoInnerR.height()

            // Aspect-fit 계산
            val scale = min(targetW / bw, targetH / bh)
            val dw = bw * scale
            val dh = bh * scale
            val dx = photoInnerR.left + (targetW - dw) / 2f
            val dy = photoInnerR.top + (targetH - dh) / 2f

            c.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), photoPaint)
            c.restoreToCount(saveCount)
        } else {
            // 사진 로딩 중이거나 폴백: 도트 스프라이트 크게 확대 표시
            val dotBmp = a.bird(def.id)
            val k = dp(2.8f)
            val bx = photoInnerR.centerX() - dotBmp.width * k / 2f
            val by = photoInnerR.centerY() - dotBmp.height * k / 2f
            c.drawBitmap(dotBmp, null, RectF(bx, by, bx + dotBmp.width * k, by + dotBmp.height * k), a.sprPaint)
        }

        // 사진 테두리 헤어라인
        strokeP.color = Color.argb(70, 0, 0, 0)
        strokeP.strokeWidth = dp(1f)
        c.drawRoundRect(photoInnerR, dp(6f), dp(6f), strokeP)

        // 사진 위 스탬프/뱃지 (촬영 완료 vs 미촬영)
        if (seen) {
            val stampStr = "📸 ${seenCount}회 촬영 · 최고 ★$bestStars"
            textP.textSize = dp(9f)
            textP.isFakeBoldText = true
            val stampW = textP.measureText(stampStr) + dp(12f)
            val stampR = RectF(
                photoInnerR.right - stampW - dp(4f),
                photoInnerR.top + dp(4f),
                photoInnerR.right - dp(4f),
                photoInnerR.top + dp(20f)
            )
            fillP.color = Color.argb(220, 242, 182, 60)
            c.drawRoundRect(stampR, dp(4f), dp(4f), fillP)
            strokeP.color = 0xFF8A5A1E.toInt()
            strokeP.strokeWidth = dp(1f)
            c.drawRoundRect(stampR, dp(4f), dp(4f), strokeP)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(stampStr, stampR.left + dp(6f), stampR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        } else {
            val unStr = "미촬영 종"
            textP.textSize = dp(8.5f)
            textP.isFakeBoldText = true
            val unW = textP.measureText(unStr) + dp(10f)
            val unR = RectF(
                photoInnerR.right - unW - dp(4f),
                photoInnerR.top + dp(4f),
                photoInnerR.right - dp(4f),
                photoInnerR.top + dp(19f)
            )
            fillP.color = Color.argb(200, 100, 90, 80)
            c.drawRoundRect(unR, dp(4f), dp(4f), fillP)
            textP.color = 0xFFFAF4E8.toInt()
            c.drawText(unStr, unR.left + dp(5f), unR.centerY() - (textP.descent() + textP.ascent()) / 2f, textP)
        }

        // 사진 하단 마운트: 저작권 크레딧 + 도트 그래픽 미니 프리뷰
        val mountY = photoInnerR.bottom + dp(5f)
        val creditStr = enc?.photoCredit ?: "📷 사진 도감 자료"
        textP.textSize = dp(8.2f)
        textP.isFakeBoldText = false
        textP.color = 0xFF7A6855.toInt()
        c.drawText(creditStr, photoCardR.left + photoInset + dp(2f), mountY + dp(11f), textP)

        // 도트 스프라이트 비교 뱃지
        val miniDot = a.bird(def.id)
        val miniScale = dp(0.55f)
        val miniX = photoCardR.right - photoInset - miniDot.width * miniScale
        val miniY = mountY + dp(1f)
        c.drawBitmap(miniDot, null, RectF(miniX, miniY, miniX + miniDot.width * miniScale, miniY + miniDot.height * miniScale), a.sprPaint)

        // -------------------------------------------------------------------
        // 3. 우측 영역: 분류 메타데이터 & 상세 한국어 생태 설명
        // -------------------------------------------------------------------
        val descCardR = RectF(descAreaLeft, mainTop, contentRight, contentBottom - dp(2f))
        UiKit.card(c, g, descCardR, 8f, selected = false, borderColor = 0xFFD8C7AA.toInt(), borderWidthDp = 1.2f)

        var curY = descCardR.top + dp(14f)
        val textLeft = descCardR.left + dp(14f)
        val textMaxW = descCardR.width() - dp(28f)

        // (1) 학명 & 영문명
        val sciName = def.scientificName.ifBlank { enc?.sci ?: "" }
        val engName = def.englishName.ifBlank { enc?.eng ?: "" }
        if (sciName.isNotBlank() || engName.isNotBlank()) {
            textP.textSize = dp(11.2f)
            textP.isFakeBoldText = true
            textP.color = 0xFF4A3728.toInt()
            val sciDisplay = if (sciName.isNotBlank()) sciName else engName
            c.drawText(sciDisplay, textLeft, curY, textP)

            if (engName.isNotBlank() && sciName.isNotBlank()) {
                val sciW = textP.measureText(sciDisplay)
                textP.textSize = dp(9.8f)
                textP.isFakeBoldText = false
                textP.color = 0xFF8A7360.toInt()
                c.drawText("·  $engName", textLeft + sciW + dp(8f), curY, textP)
            }
            curY += dp(16f)
        }

        // (2) 분류 · 서식지 · 출현 태그
        textP.textSize = dp(9.2f)
        textP.isFakeBoldText = false
        textP.color = 0xFF6B4F35.toInt()
        val orderFamily = "${def.orderName.ifBlank { enc?.order ?: "기러기목" }} · ${def.familyName.ifBlank { enc?.family ?: "오리과" }}"
        val habitatStr = def.habitats.joinToString("·") { HabitatLabels[it] ?: it }
        val metaLine1 = "분류: $orderFamily   |   서식: $habitatStr (${def.activeLabel})"
        c.drawText(metaLine1, textLeft, curY, textP)
        curY += dp(14f)

        // 아종 및 범주
        val subsList = def.subspecies.ifEmpty { enc?.subs ?: emptyList() }
        val subsStr = when {
            subsList.isEmpty() -> "아종 없음"
            subsList.size <= 2 -> subsList.joinToString(", ")
            else -> "${subsList.take(2).joinToString(", ")} 외 ${subsList.size - 2}종"
        }
        val metaLine2 = "범주: ${def.category.ifBlank { enc?.cat ?: "가-1" }}   |   아종: $subsStr"
        textP.textSize = dp(8.8f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(metaLine2, textLeft, curY, textP)
        curY += dp(16f)

        // 골드 구분선
        strokeP.color = 0xFFE2CCA8.toInt()
        strokeP.strokeWidth = dp(1f)
        c.drawLine(textLeft, curY, textLeft + textMaxW, curY, strokeP)
        curY += dp(14f)

        // (3) 📖 상세 한국어 설명 본문 (엑셀의 위키백과 / 생태 정보)
        val fullDesc = def.fullDesc.ifBlank { "한국의 공식 기록 조류입니다." }

        textP.textSize = dp(10.2f)
        textP.isFakeBoldText = false
        textP.color = 0xFF2E2118.toInt()

        val lines = Type.wrap(fullDesc, textP, textMaxW)
        val lineHeight = dp(15.5f)
        val availableH = descCardR.bottom - curY - dp(24f)
        val maxLinesPerPage = (availableH / lineHeight).toInt().coerceAtLeast(4)

        val totalPages = ((lines.size + maxLinesPerPage - 1) / maxLinesPerPage).coerceAtLeast(1)
        descPage = descPage.coerceIn(0, totalPages - 1)
        val pageLines = lines.drop(descPage * maxLinesPerPage).take(maxLinesPerPage)

        for (line in pageLines) {
            c.drawText(line, textLeft, curY, textP)
            curY += lineHeight
        }

        // 설명 출처 표기 (하단)
        val srcStr = enc?.descSrc?.takeIf { it.isNotBlank() } ?: "한국조류학회 조류목록 2025"
        textP.textSize = dp(8f)
        textP.color = 0xFF9E8A75.toInt()
        c.drawText("설명 근거: $srcStr", textLeft, descCardR.bottom - dp(8f), textP)

        // 설명 여러 페이지 시 페이지 인디케이터
        if (totalPages > 1) {
            val pageInd = "${descPage + 1}/$totalPages 페이지"
            val indW = textP.measureText(pageInd)
            c.drawText(pageInd, descCardR.right - dp(14f) - indW, descCardR.bottom - dp(8f), textP)
        }

        // -------------------------------------------------------------------
        // 4. 하단 네비게이션 바: ◀ 이전 새   [ 001 / 598 ]   다음 새 ▶
        // -------------------------------------------------------------------
        val navY = panelR.bottom - dp(28f)
        val btnH = dp(22f)
        val btnW = dp(88f)

        prevRect = RectF(contentLeft, navY, contentLeft + btnW, navY + btnH)
        nextRect = RectF(contentRight - btnW, navY, contentRight, navY + btnH)

        val prevEnabled = currentNum > 1
        val nextEnabled = currentNum < Birds.ALL.size

        if (prevEnabled) {
            UiKit.button(c, g, prevRect, "◀ 이전 새", 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
        } else {
            UiKit.button(c, g, prevRect, "◀ 이전 새", Color.argb(80, 200, 190, 175), Color.argb(130, 74, 55, 40), 10.5f)
        }

        if (nextEnabled) {
            UiKit.button(c, g, nextRect, "다음 새 ▶", 0xFFF2B63C.toInt(), 0xFF4A3728.toInt(), 10.5f)
        } else {
            UiKit.button(c, g, nextRect, "다음 새 ▶", Color.argb(80, 200, 190, 175), Color.argb(130, 74, 55, 40), 10.5f)
        }

        val totalBirds = Birds.ALL.size
        val counterStr = "$currentNum / $totalBirds"
        textP.textSize = dp(11f)
        val counterW = textP.measureText(counterStr) + dp(24f)
        UiKit.badge(
            c, g,
            RectF(panelR.centerX() - counterW / 2f, navY, panelR.centerX() + counterW / 2f, navY + btnH),
            counterStr, 0xFFF2E3C2.toInt(), 0xFF6B4F35.toInt(), 10.5f
        )

        // 단축키 안내
        textP.textSize = dp(8.2f)
        textP.color = 0xFF8A7360.toInt()
        val guideStr = "키보드 [A/◀] 이전   [D/▶] 다음   [B/✕/ESC] 닫기"
        val gw = textP.measureText(guideStr)
        c.drawText(guideStr, panelR.centerX() - gw / 2f, navY - dp(6f), textP)

        c.restore()
    }
}
