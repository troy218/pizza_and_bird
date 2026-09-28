package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * **가방(소지품)** — 주머니 속 물건을 **하나씩, 수량과 함께** 보여 준다.
 *
 * 참고한 두 게임의 결을 이 게임의 자료 구조에 맞게 옮겼다.
 *  - *듀랑고*처럼: 같은 물건도 한 칸씩 늘어놓고 `×수량` 을 붙인다. 피자 판·조각, 허브,
 *    고양이 간식처럼 원래 개수를 세던 것들이 이제 한눈에 보인다. 수량은 전부 세이브에
 *    그대로 담기는 값이라([GameState]) 가방을 닫았다 열어도, 앱을 껐다 켜도 같다.
 *  - *마비노기*처럼: 가방 왼쪽에 **내 캐릭터(페이퍼돌)** 가 서 있고, 그 좌우에 장신구·카메라·
 *    자전거·차림새·집·장식 **꾸미기 슬롯**이 붙어 있다. 칸을 누르면 바로 그 화면으로 간다.
 *
 * 실제 판정(수량 증감·장착)은 각 시스템(Pizzas·Healing·CameraGear·Charms·Bikes·Decors)이
 * 그대로 맡고, 이 파일은 **모아 보여 주는 창**만 담당한다 — 저장 형식을 새로 만들지 않는다.
 */
object Bag {

    /** 소지품 한 칸 — 이름·이모지·수량 */
    class Item(
        val key: String,
        val name: String,
        val emoji: String,
        val count: Int,
        val note: String = "",
        /** 지금 몸에 걸치고 있거나 배치해 둔 물건 */
        val worn: Boolean = false
    )

    /** 종류별 묶음 (탭 위 카테고리 칩 하나에 대응) */
    class Section(val id: String, val label: String, val emoji: String, val items: List<Item>)

    /** 캐릭터 옆에 붙는 꾸미기 슬롯 */
    class Slot(
        val id: String,
        val label: String,
        val emoji: String,
        val value: String,
        val note: String = "",
        val tint: Int = UiKit.PASTEL_SAND,
        /** 칸이 채워져 있는지 — 빈 칸은 흐리게 그린다 */
        val filled: Boolean = true
    )

    /** 소지품 전체를 종류별로 모은다. 빈 묶음은 빼고 돌려준다. */
    fun sections(s: GameState): List<Section> {
        val out = ArrayList<Section>()

        // 🍕 피자 — 판과 조각을 따로 센다 (조각은 한 판을 8등분한 것)
        val pizza = ArrayList<Item>()
        for (p in Pizzas.ALL) {
            for (q in 0 until 3) {
                val i = PizzaSlices.idx(p.id, q)
                val pans = s.pizzas[i]
                if (pans > 0) {
                    pizza.add(
                        Item(
                            "pan:${p.id}:$q", "${p.name} 피자 한 판", p.emoji, pans,
                            "${PizzaQ.of(q).label} · 조각 ${s.pizzaSlices[i]}개"
                        )
                    )
                }
                val slices = s.pizzaSlices[i]
                if (slices > 0) {
                    pizza.add(
                        Item(
                            "slice:${p.id}:$q", "${p.name} 피자 조각", p.emoji, slices,
                            "${PizzaQ.of(q).label} · 배고픔 +${p.hungerBonus + PizzaQ.of(q).hunger}"
                        )
                    )
                }
            }
        }
        if (pizza.isNotEmpty()) out.add(Section("pizza", "피자", "🍕", pizza))

        // 🌿 허브 — Healing 이 JSONObject 에 개수를 세어 둔다 (그대로 저장된다)
        val herbs = Healing.herbs(s)
        val herbItems = ArrayList<Item>()
        for ((id, n) in herbs) {
            if (n <= 0) continue
            val h = Healing.HERBS.firstOrNull { it.id == id } ?: continue
            herbItems.add(
                Item(
                    "herb:$id", h.name, h.emoji, n,
                    "${h.habitat} · 행운 +${h.luckBonus} · 허브차 재료"
                )
            )
        }
        val treats = Healing.catTreats(s)
        if (treats > 0) {
            herbItems.add(Item("treat", "고양이 간식", "🐟", treats, "고양이에게 한 개씩 (애정 ${Healing.catLove(s)})"))
        }
        if (herbItems.isNotEmpty()) out.add(Section("herb", "허브·간식", "🌿", herbItems))

        // 📷 카메라 장비
        val cam = ArrayList<Item>()
        for (id in s.ownedGear) {
            val gear = CameraGear.byId[id] ?: continue
            val on = id == s.compactId || id == s.bodyId || id == s.lensId || id == s.tcId
            cam.add(
                Item(
                    "gear:$id", "${gear.brand} ${gear.name}", gear.kind.emoji, 1,
                    "${gear.kind.label} · ${gear.grade.label}" + if (on) " · 장착 중" else "",
                    worn = on
                )
            )
        }
        if (cam.isNotEmpty()) out.add(Section("camera", "카메라 장비", "📷", cam))

        // 🎀 장신구
        val charms = ArrayList<Item>()
        for (id in s.ownedCharms) {
            val ch = Charms.of(id) ?: continue
            val on = s.charmId == id
            charms.add(
                Item(
                    "charm:$id", ch.name, "🎀", 1,
                    ch.description + if (on) " · 착용 중" else "",
                    worn = on
                )
            )
        }
        if (charms.isNotEmpty()) out.add(Section("charm", "장신구", "🎀", charms))

        // 🧸 집 장식 — 같은 소품을 여러 개 샀으면 개수로 센다
        val decor = ArrayList<Item>()
        for (id in s.decorOwned.distinct()) {
            val d = Decors.of(id) ?: continue
            val n = s.decorOwned.count { it == id }
            val placed = s.decorSlots.count { it == id }
            decor.add(
                Item(
                    "decor:$id", d.name, d.emoji, n,
                    if (placed > 0) "집에 ${placed}개 배치 중 · 행운 +${d.luck}" else "창고에 보관 중 · 행운 +${d.luck}",
                    worn = placed > 0
                )
            )
        }
        if (decor.isNotEmpty()) out.add(Section("decor", "집 장식", "🧸", decor))

        // 🚲 자전거와 부속품
        val bike = ArrayList<Item>()
        for (id in s.ownedBikes) {
            val b = Bikes.byId[id] ?: continue
            val on = s.bikeId == id
            bike.add(Item("bike:$id", b.name, b.emoji, 1, if (on) "타고 다니는 중" else "집에 세워 둠", worn = on))
        }
        for (id in s.ownedBikeParts) {
            val p = BikeParts.of(id) ?: continue
            bike.add(Item("part:$id", p.name, p.emoji, 1, p.desc))
        }
        if (bike.isNotEmpty()) out.add(Section("bike", "자전거", "🚲", bike))

        // 🏠 집과 인테리어
        val home = ArrayList<Item>()
        for (id in s.ownedHomes) {
            val name = Regions.byId[id]?.name ?: id
            val on = id == s.homeRegion
            home.add(Item("home:$id", "$name 의 집", "🏠", 1, if (on) "지금 사는 정착지" else "매입해 둔 집", worn = on))
        }
        for (id in s.ownedHouseStyles) {
            val st = HouseStyles.byId[id] ?: continue
            val on = s.houseStyleId == id
            home.add(Item("style:$id", st.name, st.emoji, 1, st.desc + if (on) " · 지금 인테리어" else "", worn = on))
        }
        if (home.isNotEmpty()) out.add(Section("home", "집", "🏠", home))

        return out
    }

    /** 캐릭터 좌우에 붙는 꾸미기 슬롯 6개 — 왼쪽 3 / 오른쪽 3 으로 번갈아 놓는다 */
    fun slots(s: GameState): List<Slot> {
        val charm = Charms.equipped(s)
        val rig = s.rig()
        val bike = Bikes.of(s.bikeId)
        val placed = s.decorSlots.count { it >= 0 }
        val homeName = Regions.byId[s.homeRegion]?.name ?: s.homeRegion
        return listOf(
            Slot(
                "charm", "장신구", "🎀",
                charm?.name ?: "없음",
                if (charm != null) "행운 +${charm.luck(s)}" else "서울 상점·주민에게서 얻어요",
                UiKit.PASTEL_ROSE, filled = charm != null
            ),
            Slot(
                "camera", "카메라", "📷", rig.title,
                (if (s.useIlc) "렌즈교환식" else "일체형") + " · 장비 ${s.ownedGear.size}점",
                UiKit.PASTEL_SKY
            ),
            Slot(
                "bike", "자전거", "🚲", bike.name,
                "부속품 ${s.ownedBikeParts.size}개 · 모델 ${s.ownedBikes.size}종",
                UiKit.PASTEL_LEMON
            ),
            Slot(
                "outfit", "차림새", "👟",
                "Lv.${s.level} ${s.title()}",
                "옷 등급 ${s.gearTier() + 1}/4 · 레벨이 오르면 바뀌어요",
                UiKit.PASTEL_MINT
            ),
            Slot(
                "home", "집", "🏠", "$homeName · ${HouseStyles.of(s.houseStyleId).name}",
                "가진 집 ${s.ownedHomes.size}채 · 인테리어 ${s.ownedHouseStyles.size}종",
                UiKit.PASTEL_PEACH
            ),
            Slot(
                "decor", "집 장식", "🧸", "${placed}/${Decors.SLOT_COUNT}칸 배치",
                "보유 ${s.decorOwned.distinct().size}종 · 행운 +${s.decorLuck()}",
                UiKit.PASTEL_LILAC, filled = placed > 0
            )
        )
    }

    /** 카테고리를 합친 전체 목록 */
    fun flatten(s: GameState): List<Item> = sections(s).flatMap { it.items }

    /** 소지품이 몇 종류인지 */
    fun kinds(s: GameState): Int = flatten(s).size

    /** 전부 다 세면 몇 개인지 (한 판 = 1개, 조각 = 1개) */
    fun total(s: GameState): Int = flatten(s).sumOf { it.count }
}

/**
 * 가방 탭 그리기 + 탭 판정.
 *
 * 그리기와 판정이 **같은 레이아웃 계산**을 쓰도록 [Layout] 을 한 번만 만든다
 * (화면 크기가 달라도 버튼이 글자 위에 정확히 얹힌다).
 */
class BagPanel(private val scene: Scene) {

    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 고른 카테고리 — 0 = 전체, 1.. = 섹션 순서 */
    private var cat = 0
    private var page = 0

    private class Layout(
        val sections: List<Bag.Section>,
        val dollPane: RectF,
        val listPane: RectF,
        val dollRect: RectF,
        val luckR: RectF,
        val rowsArea: RectF,
        val slots: List<Pair<RectF, Bag.Slot>>,
        val cats: List<Pair<RectF, Int>>,
        val rows: List<Pair<RectF, Bag.Item>>,
        val prevR: RectF,
        val nextR: RectF,
        val page: Int,
        val pageCount: Int,
        val itemCount: Int,
        val kindCount: Int,
        val totalCount: Int
    )

    private fun dp(v: Float): Float = v * scene.game.density
    private fun tx(v: Float): Float = TypeScale.px(dp(v))

    private fun layout(area: RectF): Layout {
        val s = scene.game.state
        // 소지품 목록은 한 번만 만들어 레이아웃·그리기·수량 표시가 모두 같은 값을 쓴다
        // (프레임마다 다시 만들면 허브 JSON 을 매번 파싱하게 된다)
        val sections = Bag.sections(s)
        val allItems = sections.flatMap { it.items }
        val wide = area.width() > dp(430f)
        val gap = dp(8f)

        // 페이퍼돌 칸 — 화면이 넓으면 캐릭터가 크게 서고, 좁으면 비율을 줄인다
        val dollW = minOf(maxOf(area.width() * if (wide) 0.42f else 0.46f, dp(112f)), minOf(dp(340f), area.width() * 0.5f))
        val dollPane = RectF(area.left, area.top, area.left + dollW, area.bottom)
        val listPane = RectF(dollPane.right + gap, area.top, area.right, area.bottom)

        // 슬롯 칩 — 캐릭터 좌우로 3개씩 (마비노기식 장비 칸). 좁은 화면에선 칩이 작아지고
        // 글자는 [fit] 이 …로 줄여 준다 (칸 위치는 언제나 캐릭터 옆).
        val slots = Bag.slots(s)
        val pad = dp(9f)
        val chipGap = dp(5f)
        val usableW = (dollW - pad * 2f - chipGap * 2f).coerceAtLeast(dp(60f))
        val chipW = usableW * 0.30f
        val chipH = dp(26f)
        val blockH = 3f * chipH + 2f * chipGap
        val luckH = dp(26f)
        val chipTop = dollPane.top + dp(32f) +
            ((dollPane.height() - dp(44f) - blockH - luckH) / 2f).coerceAtLeast(0f)
        val slotRects = ArrayList<Pair<RectF, Bag.Slot>>()
        for ((i, slot) in slots.withIndex()) {
            val col = i % 2
            val row = i / 2
            val x = if (col == 0) dollPane.left + pad else dollPane.right - pad - chipW
            val y = chipTop + row * (chipH + chipGap)
            slotRects.add(RectF(x, y, x + chipW, y + chipH) to slot)
        }
        val dollCx = dollPane.centerX()
        val dollBottom = chipTop + blockH
        val dollSide = minOf(blockH, usableW - chipW * 2f).coerceAtLeast(dp(28f))
        val dollRect = RectF(
            dollCx - dollSide / 2f, dollBottom - dollSide,
            dollCx + dollSide / 2f, dollBottom
        )

        // 카테고리 칩 (줄바꿈)
        val cats = ArrayList<Pair<RectF, Int>>()
        val labels = listOf("전체") + sections.map { it.label }
        textP.textSize = tx(9.5f)
        var cx = listPane.left + dp(8f)
        var cy = listPane.top + dp(30f)
        val chipH2 = dp(19f)
        for ((i, label) in labels.withIndex()) {
            val tw = UiKit.iconTextWidth(label, textP)
            val w = tw + dp(16f)
            if (cx + w > listPane.right - dp(8f) && cx > listPane.left + dp(8f)) {
                cx = listPane.left + dp(8f)
                cy += chipH2 + dp(4f)
            }
            cats.add(RectF(cx, cy, cx + w, cy + chipH2) to i)
            cx += w + dp(4f)
        }
        val catsBottom = cy + chipH2 + dp(6f)

        // 목록 — 한 쪽에 들어가는 만큼만 그리고 쪽을 넘긴다
        val footerH = dp(24f)
        val rowsArea = RectF(listPane.left + dp(6f), catsBottom, listPane.right - dp(6f), listPane.bottom - footerH - dp(4f))
        val rowH = dp(30f)
        val perPage = ((rowsArea.height() / rowH).toInt()).coerceIn(3, 8)
        val items = if (cat == 0) allItems else sections.getOrNull(cat - 1)?.items ?: emptyList()
        val pageCount = ((items.size + perPage - 1) / perPage).coerceAtLeast(1)
        page = page.coerceIn(0, pageCount - 1)
        val from = page * perPage
        val rows = ArrayList<Pair<RectF, Bag.Item>>()
        for (i in from until minOf(from + perPage, items.size)) {
            val y = rowsArea.top + (i - from) * rowH
            rows.add(RectF(rowsArea.left, y, rowsArea.right, y + rowH - dp(3f)) to items[i])
        }

        val footY = listPane.bottom - footerH
        val nextR = RectF(listPane.right - dp(30f), footY, listPane.right - dp(8f), footY + dp(19f))
        val prevR = RectF(nextR.left - dp(30f), footY, nextR.left - dp(2f), footY + dp(19f))

        val luckR = RectF(
            dollPane.left + pad, dollPane.bottom - dp(12f) - luckH,
            dollPane.right - pad, dollPane.bottom - dp(12f)
        )

        return Layout(
            sections, dollPane, listPane, dollRect, luckR, rowsArea, slotRects, cats, rows, prevR, nextR,
            page, pageCount, items.size, allItems.size, allItems.sumOf { it.count }
        )
    }

    /** 그리기. [addBtn] 로 누를 수 있는 사각형을 메뉴 쪽 버튼 목록에 등록한다. */
    fun draw(c: Canvas, area: RectF, addBtn: (RectF, () -> Unit) -> Unit) {
        val g = scene.game
        val s = g.state
        val L = layout(area)

        // ---- 왼쪽: 페이퍼돌 ----
        UiKit.stitchCard(c, g, L.dollPane, UiKit.CARD_LO)
        textP.textSize = tx(11f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("내 모습", L.dollPane.left + dp(10f), L.dollPane.top + dp(19f), textP)
        textP.textSize = tx(8.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText("슬롯을 누르면 바로 바꿀 수 있어요", L.dollPane.left + dp(10f), L.dollPane.top + dp(27f), textP)

        // 캐릭터 (HD 세트를 지정한 사각형에 꽉 차게)
        val a = g.assets
        val ps = a.playerSet(s.gender, s.gearTier(), true)
        val avatar = ps.idle.frame(Dir.S, (g.time / Anim.IDLE.frameTime).toInt())
        fillP.color = Color.argb(46, 40, 30, 20)
        c.drawOval(
            RectF(L.dollRect.left + dp(4f), L.dollRect.bottom - dp(7f), L.dollRect.right - dp(4f), L.dollRect.bottom + dp(3f)),
            fillP
        )
        c.drawBitmap(avatar, null, L.dollRect, a.sprPaint)
        Charms.equipped(s)?.let { item ->
            Charms.draw(c, item, L.dollRect.right - dp(6f), L.dollRect.centerY() + dp(6f), dp(16f), g.time)
        }

        // 슬롯 칩 — 좌우로 붙는 꾸미기 칸
        for ((r, slot) in L.slots) {
            val on = slot.filled
            UiKit.stitchCard(
                c, g, r, if (on) slot.tint else UiKit.CARD_HI,
                UiKit.BROWN_LINE, 1.2f, stitched = false
            )
            val ic = dp(11f)
            if (!UiKit.iconCenter(c, g, slot.emoji, r.left + dp(11f), r.top + dp(9f), ic)) {
                textP.textSize = tx(9f)
                textP.color = 0xFF4A3728.toInt()
                c.drawText(slot.emoji, r.left + dp(8f), r.top + dp(12f), textP)
            }
            textP.textSize = tx(7.6f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText(fit(slot.label, r.width() - dp(22f), tx(7.6f)), r.left + dp(21f), r.top + dp(10f), textP)
            textP.textSize = tx(8.6f)
            textP.color = if (on) 0xFF4A3728.toInt() else 0xFF9A8B7A.toInt()
            c.drawText(fit(slot.value, r.width() - dp(7f), tx(8.6f)), r.left + dp(4f), r.bottom - dp(4f), textP)
            addBtn(r) { tapSlot(slot) }
        }

        // 행운 한 줄 — 슬롯에서 올라온 효과를 한눈에 (장식·자전거·장비·장신구)
        UiKit.stitchCard(c, g, L.luckR, UiKit.CARD_HI, UiKit.BROWN_LINE, 1.2f, stitched = false)
        val luckNow = s.effectiveLuck().toInt()
        textP.textSize = tx(8.4f)
        textP.color = 0xFF6B5A48.toInt()
        c.drawText("🍀 행운", L.luckR.left + dp(6f), L.luckR.top + dp(11f), textP)
        val lbw = L.luckR.width() - dp(12f)
        UiKit.bar(
            c, g, L.luckR.left + dp(6f), L.luckR.top + dp(14f), lbw, dp(5f),
            luckNow / 100f, 0xFF8FD694.toInt(), 0xFF4E9A51.toInt()
        )
        textP.textSize = tx(7.4f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(
            fit("$luckNow / 100 · 장식 +${s.decorLuck()} 자전거 +${s.bikeLuck()} 장비 +${s.gearLuck()}", lbw, tx(7.4f)),
            L.luckR.left + dp(6f), L.luckR.bottom - dp(4f), textP
        )

        // ---- 오른쪽: 소지품 목록 ----
        UiKit.stitchCard(c, g, L.listPane, UiKit.CARD_LO)

        textP.textSize = tx(11f)
        textP.color = 0xFF4A3728.toInt()
        c.drawText("🎒 소지품", L.listPane.left + dp(10f), L.listPane.top + dp(19f), textP)
        textP.textSize = tx(8.5f)
        textP.color = 0xFF8A7360.toInt()
        c.drawText(
            "총 ${L.kindCount}종 · ${L.totalCount}개" + if (L.itemCount != L.kindCount) " (고른 칸 ${L.itemCount}종)" else "",
            L.listPane.left + dp(10f), L.listPane.top + dp(27f), textP
        )

        for ((r, i) in L.cats) {
            val on = i == cat
            UiKit.cuteButton(
                c, g, r, (if (i == 0) "🎒 전체" else {
                    val sec = L.sections.getOrNull(i - 1)
                    if (sec == null) "전체" else "${sec.emoji} ${sec.label}"
                }),
                if (on) UiKit.PASTEL_LEMON else Color.argb(150, 226, 214, 190),
                if (on) UiKit.INK else UiKit.MUTED, 9.5f, depthDp = 1.2f
            )
            addBtn(r) {
                cat = i
                page = 0
            }
        }

        if (L.rows.isEmpty()) {
            textP.textSize = tx(10f)
            textP.color = 0xFF8A7360.toInt()
            c.drawText("이 칸에는 아직 담긴 게 없어요", L.rowsArea.left + dp(6f), L.rowsArea.top + dp(24f), textP)
        }
        for ((r, item) in L.rows) {
            UiKit.stitchCard(
                c, g, r, if (item.worn) UiKit.CARD_SEL_LO else UiKit.CARD_HI,
                UiKit.BROWN_LINE, 1.2f, stitched = false, selected = item.worn
            )
            val icx = r.left + dp(13f)
            val icy = r.top + r.height() / 2f
            // 이모지는 UiKit 이 SVG 아이콘으로 바꿔 준다 (없는 글자면 그대로 그린다)
            if (!UiKit.iconCenter(c, g, item.emoji, icx, icy, dp(12f))) {
                textP.textSize = tx(11f)
                textP.color = 0xFF4A3728.toInt()
                c.drawText(item.emoji, r.left + dp(6f), icy + dp(4f), textP)
            }
            // 이름 + 설명 — 오른쪽 수량 자리를 비워 두고 줄인다
            val qw = dp(40f)
            val nameMax = r.width() - dp(34f) - qw
            textP.textSize = tx(10.5f)
            textP.color = 0xFF4A3728.toInt()
            c.drawText(fit(item.name, nameMax, tx(10.5f)), r.left + dp(26f), r.top + dp(13f), textP)
            if (item.note.isNotBlank()) {
                textP.textSize = tx(8f)
                textP.color = 0xFF8A7360.toInt()
                c.drawText(fit(item.note, nameMax, tx(8f)), r.left + dp(26f), r.top + dp(23f), textP)
            }
            textP.textSize = tx(11.5f)
            textP.color = if (item.worn) 0xFF6FBA6B.toInt() else 0xFF6B4F35.toInt()
            val cnt = "×${item.count}"
            c.drawText(cnt, r.right - dp(8f) - textP.measureText(cnt), r.top + dp(17f), textP)
            addBtn(r) { showItem(item) }
        }

        // 쪽 넘기기
        textP.textSize = tx(9f)
        textP.color = 0xFF8A7360.toInt()
        val label = "${L.page + 1} / ${L.pageCount}"
        c.drawText(label, L.prevR.left - dp(38f), L.prevR.top + dp(13f), textP)
        if (L.page > 0) {
            UiKit.cuteButton(c, g, L.prevR, "◀", UiKit.PASTEL_SKY, UiKit.INK, 9.5f, depthDp = 1.2f)
            addBtn(L.prevR) { page = (page - 1).coerceAtLeast(0) }
        }
        if (L.page < L.pageCount - 1) {
            UiKit.cuteButton(c, g, L.nextR, "▶", UiKit.PASTEL_SKY, UiKit.INK, 9.5f, depthDp = 1.2f)
            addBtn(L.nextR) { page += 1 }
        }
    }

    /** 폭에 맞게 줄인 글자 (넘치면 …) */
    private fun fit(text: String, maxW: Float, sizePx: Float): String {
        if (maxW <= 0f) return text
        textP.textSize = sizePx
        if (textP.measureText(text) <= maxW) return text
        var out = text
        while (out.length > 1 && textP.measureText("$out…") > maxW) out = out.dropLast(1)
        return "$out…"
    }

    /** 슬롯을 누르면 그 시스템 화면으로 — 가방 안에서 곧바로 꾸밀 수 있다 */
    private fun tapSlot(slot: Bag.Slot) {
        when (slot.id) {
            "charm" -> scene.openOverlay(CharmOverlay(scene))
            "camera" -> scene.openOverlay(GearBagOverlay(scene))
            "bike" -> scene.openOverlay(BikeShopOverlay(scene))
            "outfit" -> scene.openOverlay(StatsOverlay(scene) { scene.openOverlay(MenuOverlay(scene, MenuOverlay.TAB_BAG)) })
            "home", "decor" -> scene.openOverlay(HomeDecorOverlay(scene))
            else -> scene.game.toast("${slot.label} · ${slot.value}")
        }
    }

    private fun showItem(item: Bag.Item) {
        scene.openOverlay(
            DialogOverlay(
                scene, item.name,
                (if (item.note.isBlank()) "가방에 담아 둔 소지품이에요." else item.note) +
                    "\n\n수량 ×${item.count}" + (if (item.worn) "\n지금 걸치고 있어요." else ""),
                listOf(DialogOverlay.Choice("닫기"))
            )
        )
    }
}
