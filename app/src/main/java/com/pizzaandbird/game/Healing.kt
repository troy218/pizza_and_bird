package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌸 힐링 컨텐츠 모음 (v0.4.2 "따뜻한 바람")
 *
 * 기존 루프(피자 · 자전거 · 탐조 · 꾸미기)를 깨지 않으면서 "그냥 머물고 싶은" 순간을 더한다.
 *
 *  1) 야생 허브 채집 (12종) — 꽃밭/숲/습지/바닷가에서 주워 집에서 허브차로 마신다.
 *  2) 고양이 간식 & 친밀도 — 생선 간식을 주면 따라오고 선물을 물어다 준다.
 *  3) 작은 생물 파티클 — 나비/잠자리/반딧불/나방/갈매기/눈 토끼/V자 철새 떼.
 *  4) 탐조 일기 — 잠들면 오늘의 기록이 자동으로 한 페이지.
 *  5) 벤치 풍경 감상 보너스 — 첫눈/봄소풍/비 산책/노을/보름달 등 24가지 작은 기념.
 *  6) 계절 향 파티클 — 봄 벚꽃/여름 솔/가을 단풍/겨울 난로 향이 살짝 감돈다.
 */
object Healing {

    // -------------------------------------------------------------------
    // 1) 허브 12종
    // -------------------------------------------------------------------

    data class Herb(
        val id: String,
        val name: String,
        val emoji: String,
        val color: Int,
        val season: Season?,      // null = 사계절
        val habitat: String,
        val luckBonus: Int,
        val hungerBonus: Int,
        val note: String
    )

    val HERBS = listOf(
        Herb("ssuk", "쑥", "🌿", 0xFF6B8E4E.toInt(), Season.SPRING, "field", 1, 6, "뜯을수록 향이 진해져요"),
        Herb("minari", "미나리", "🌱", 0xFF5DA36E.toInt(), Season.SUMMER, "wetland", 2, 8, "시원한 물가에서 자라요"),
        Herb("gukhwa", "들국화", "🌼", 0xFFE6B045.toInt(), Season.AUTUMN, "field", 3, 4, "가을 햇살을 닮았어요"),
        Herb("sanja", "산딸기", "🍓", 0xFFC7536A.toInt(), Season.SUMMER, "forest", 4, 10, "숲속 숨은 간식"),
        Herb("bam", "밤송이", "🌰", 0xFF8A5A3B.toInt(), Season.AUTUMN, "forest", 2, 12, "주워서 집 가져가고 싶은 맛"),
        Herb("pine", "솔잎", "🌲", 0xFF4E7A4F.toInt(), null, "mountain", 2, 2, "솔향이 코끝을 간지럽혀요"),
        Herb("roseHip", "해당화 열매", "🌹", 0xFFD8505E.toInt(), Season.AUTUMN, "coast", 3, 5, "바닷바람을 맞고 자란 새콤한 열매"),
        Herb("yeongji", "영지버섯", "🍄", 0xFFB76B86.toInt(), Season.AUTUMN, "mountain", 5, 2, "발견만으로도 운이 좋은 것"),
        Herb("gondeure", "곤드레", "🌾", 0xFF6A8B5F.toInt(), Season.SUMMER, "mountain", 2, 9, "밥과 잘 어울려요"),
        Herb("dongbaek", "동백꽃잎", "🌺", 0xFFD0425A.toInt(), Season.SPRING, "coast", 3, 3, "겨울 끝자락에 떨어져요"),
        Herb("buckwheat", "메밀꽃", "🤍", 0xFFF0ECD8.toInt(), Season.AUTUMN, "field", 2, 4, "하얗게 피어난 소금밭"),
        Herb("dandelion", "민들레 홀씨", "🌬", 0xFFF6F1C4.toInt(), null, "field", 1, 3, "불면 날아가요")
    )

    fun pickableHerbs(season: Season, habitats: Set<String>): List<Herb> =
        HERBS.filter { (it.season == null || it.season == season) && it.habitat in habitats }

    // -------------------------------------------------------------------
    // 2) 고양이 간식 / 친밀도
    // -------------------------------------------------------------------

    fun addCatTreat(state: GameState, n: Int = 1) {
        val h = ensureState(state)
        h.put("treats", (h.optInt("treats", 0) + n).coerceAtMost(99))
    }

    fun catTreats(state: GameState): Int = ensureState(state).optInt("treats", 0)
    fun catLove(state: GameState): Int = ensureState(state).optInt("catLove", 0)

    fun feedCat(state: GameState): Boolean {
        val h = ensureState(state)
        val t = h.optInt("treats", 0)
        if (t <= 0) return false
        h.put("treats", t - 1)
        var love = h.optInt("catLove", 0)
        love = (love + 3 + (Math.random() * 4).toInt()).coerceAtMost(100)
        h.put("catLove", love)
        if (Math.random() < 0.35) {
            state.luck = (state.luck + 1f).coerceAtMost(100f)
            h.put("giftCat", h.optInt("giftCat", 0) + 1)
            unlock(state, "cat_gift")
        }
        return true
    }

    fun catFollowLevel(state: GameState): Int = when {
        catLove(state) >= 40 -> 2
        catLove(state) >= 15 -> 1
        else -> 0
    }

    // -------------------------------------------------------------------
    // 3) 작은 생물 파티클
    // -------------------------------------------------------------------

    enum class CritterType { BUTTERFLY, DRAGONFLY, FIREFLY, MOTH, FAR_GULL, SNOW_HARE, FLOCK }

    data class Critter(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var t: Float, val kind: CritterType,
        var life: Float, val maxLife: Float,
        var col: Int = 0xFFFFFFFF.toInt(),
        var size: Float = 2.5f,
        var flap: Float = 0f
    )

    private val critters = ArrayList<Critter>()
    private var critterAccum = 0f

    fun updateCritters(dt: Float, state: GameState, region: RegionDef, rnd: Random,
                       viewW: Float, viewH: Float, spawnX: Float, spawnY: Float,
                       onUnlock: ((TinyMoment) -> Unit)? = null) {
        critterAccum += dt
        val habitats = region.habitats
        val isNight = state.isNight()
        val s = state.season()

        while (critterAccum > 0.35f && critters.size < 28) {
            critterAccum -= 0.35f
            val roll = rnd.nextFloat()
            // 계절별 작은 생물 편성표 — 밤/낮을 먼저 가르고 계절 밴드로 고른다.
            // 봄=나비·북상 철새 / 여름=파란 잠자리·반딧불 / 가을=고추잠자리·남하 기러기 / 겨울=눈토끼·월동 기러기
            val k = if (isNight) when {
                s == Season.SUMMER && "wetland" in habitats && roll < 0.60 -> CritterType.FIREFLY
                s == Season.SUMMER && roll < 0.28 -> CritterType.FIREFLY
                s == Season.SPRING && "wetland" in habitats && roll < 0.30 -> CritterType.FIREFLY
                s == Season.AUTUMN && roll < 0.30 -> CritterType.MOTH
                s == Season.SPRING && roll < 0.18 -> CritterType.MOTH
                roll < 0.10 -> CritterType.MOTH
                else -> null
            } else when {
                "coast" in habitats && roll < 0.10 -> CritterType.FAR_GULL
                s == Season.SPRING && roll < 0.55 -> CritterType.BUTTERFLY
                s == Season.SPRING && roll < 0.65 -> CritterType.FLOCK
                s == Season.SUMMER && ("wetland" in habitats || "water" in habitats) && roll < 0.48 -> CritterType.DRAGONFLY
                s == Season.SUMMER && roll < 0.60 -> CritterType.BUTTERFLY
                s == Season.AUTUMN && roll < 0.50 -> CritterType.DRAGONFLY
                s == Season.AUTUMN && roll < 0.66 -> CritterType.FLOCK
                s == Season.AUTUMN && roll < 0.72 -> CritterType.BUTTERFLY
                s == Season.WINTER && "mountain" in habitats && roll < 0.30 -> CritterType.SNOW_HARE
                s == Season.WINTER && roll < 0.38 -> CritterType.SNOW_HARE
                s == Season.WINTER && roll < 0.48 -> CritterType.FLOCK
                roll < 0.03 -> CritterType.FLOCK
                else -> null
            }
            if (k != null) {
                critters.add(spawnCritter(k, rnd, viewW, viewH, spawnX, spawnY, s))
                val momentId = when (k) {
                    CritterType.FIREFLY -> "first_firefly"
                    CritterType.BUTTERFLY -> "first_butterfly"
                    CritterType.DRAGONFLY -> "dragonfly_hover"
                    else -> null
                }
                if (momentId != null) {
                    val m = unlock(state, momentId)
                    if (m != null) onUnlock?.invoke(m)
                }
            }
        }
        val it = critters.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t += dt; c.flap += dt
            c.life -= dt
            when (c.kind) {
                CritterType.BUTTERFLY -> {
                    c.vx += sin(c.t * 2.3f) * 7f * dt
                    c.vy += cos(c.t * 1.7f) * 5f * dt
                    c.vx *= 0.92f; c.vy *= 0.92f
                }
                CritterType.DRAGONFLY -> { c.vx *= 0.98f; c.vy += sin(c.t * 4f) * 12f * dt }
                CritterType.FIREFLY -> {
                    c.vx += (rnd.nextFloat() - 0.5f) * 18f * dt
                    c.vy += (rnd.nextFloat() - 0.5f) * 18f * dt
                    c.vx *= 0.9f; c.vy *= 0.9f
                }
                CritterType.MOTH -> {
                    c.vx += (rnd.nextFloat() - 0.5f) * 10f * dt
                    c.vy += (rnd.nextFloat() - 0.5f) * 10f * dt
                    c.vx *= 0.93f; c.vy *= 0.93f
                }
                CritterType.FAR_GULL -> { c.vx = 22f; c.vy = sin(c.t * 0.8f) * 4f }
                CritterType.FLOCK ->    { c.vx = 36f; c.vy = sin(c.t * 0.5f + c.x * 0.01f) * 2f }
                CritterType.SNOW_HARE ->{ c.vx *= 0.88f; c.vy *= 0.88f; if (c.t > 2.5f) c.life = 0f }
            }
            c.x += c.vx * dt; c.y += c.vy * dt
            if (c.life <= 0f || c.x < spawnX - 80f || c.x > spawnX + viewW + 80f ||
                c.y < spawnY - 80f || c.y > spawnY + viewH + 80f) it.remove()
        }
    }

    private fun spawnCritter(k: CritterType, rnd: Random, vw: Float, vh: Float, sx: Float, sy: Float, season: Season): Critter {
        val x = sx + rnd.nextFloat() * vw
        val y = sy + rnd.nextFloat() * vh
        return when (k) {
            CritterType.BUTTERFLY -> Critter(x, y, (rnd.nextFloat()-0.5f)*10f, -5f, 0f, k,
                6f + rnd.nextFloat()*4f, 10f + rnd.nextFloat()*3f,
                if (rnd.nextBoolean()) 0xFFF2A3B3.toInt() else 0xFFE9D07B.toInt(), 3.5f)
            // 여름엔 파란 잠자리, 가을엔 빨간 고추잠자리
            CritterType.DRAGONFLY -> Critter(x, y, 0f, 0f, 0f, k, 7f, 7f,
                if (season == Season.AUTUMN) 0xFFD8553C.toInt() else 0xFF86C2D8.toInt(), 4f)
            CritterType.FIREFLY -> Critter(x, y, 0f, 0f, 0f, k, 14f, 14f, 0xFFF7DE60.toInt(), 2.6f)
            CritterType.MOTH -> Critter(x, y, 0f, 0f, 0f, k, 10f, 10f, 0xFFC8B89A.toInt(), 3f)
            CritterType.FAR_GULL -> Critter(sx - 40f, sy + rnd.nextFloat()*vh*0.4f, 22f, 0f, 0f, k,
                18f, 18f, 0xFFFFFFFF.toInt(), 2f)
            CritterType.FLOCK -> Critter(sx - 60f, sy + 30f + rnd.nextFloat()*80f, 36f, 0f,
                rnd.nextFloat()*6.28f, k, 10f, 10f, 0xFF3A3530.toInt(), 2f)
            CritterType.SNOW_HARE -> Critter(x, y,
                (rnd.nextFloat()-0.5f)*30f, (rnd.nextFloat()-0.5f)*20f, 0f, k, 3f, 3f,
                0xFFF4F2EA.toInt(), 5f)
        }
    }

    fun drawCritters(canvas: Canvas, paint: Paint) {
        for (c in critters) {
            val a = ((c.life / c.maxLife).coerceIn(0f, 1f))
            when (c.kind) {
                CritterType.BUTTERFLY, CritterType.MOTH -> {
                    val wing = (1f + kotlin.math.abs(sin(c.flap * 12f))) * 0.5f
                    paint.color = Color.argb((170*a).toInt(), Color.red(c.col), Color.green(c.col), Color.blue(c.col))
                    canvas.drawOval(c.x - c.size*wing, c.y - c.size*0.6f, c.x, c.y + c.size*0.6f, paint)
                    canvas.drawOval(c.x, c.y - c.size*0.6f, c.x + c.size*wing, c.y + c.size*0.6f, paint)
                }
                CritterType.DRAGONFLY -> {
                    paint.color = Color.argb((200*a).toInt(), Color.red(c.col), Color.green(c.col), Color.blue(c.col))
                    val wing = 1f + (sin(c.flap * 20f) * 0.6f)
                    canvas.drawRect(c.x - 1f, c.y - c.size*0.4f, c.x + 1f, c.y + c.size*0.4f, paint)
                    canvas.drawOval(c.x - c.size*wing, c.y - 2f, c.x + c.size*wing, c.y + 2f, paint)
                }
                CritterType.FIREFLY -> {
                    val glow = 0.5f + 0.5f * sin(c.flap * 6f)
                    paint.color = Color.argb((60*a).toInt(), 247, 222, 96)
                    canvas.drawCircle(c.x, c.y, c.size*2.2f*glow, paint)
                    paint.color = Color.argb((240*a).toInt(), 255, 240, 150)
                    canvas.drawCircle(c.x, c.y, c.size*0.7f, paint)
                }
                CritterType.FAR_GULL, CritterType.FLOCK -> {
                    paint.color = Color.argb((220*a).toInt(), Color.red(c.col), Color.green(c.col), Color.blue(c.col))
                    val s = c.size
                    canvas.drawLine(c.x - s, c.y, c.x, c.y - s*0.6f, paint)
                    canvas.drawLine(c.x, c.y - s*0.6f, c.x + s, c.y, paint)
                }
                CritterType.SNOW_HARE -> {
                    paint.color = Color.argb((230*a).toInt(), Color.red(c.col), Color.green(c.col), Color.blue(c.col))
                    canvas.drawOval(c.x - c.size*1.2f, c.y - c.size*0.7f, c.x + c.size*1.2f, c.y + c.size*0.9f, paint)
                    paint.color = Color.argb((230*a).toInt(), 240, 220, 220)
                    canvas.drawCircle(c.x - c.size*0.6f, c.y - c.size*0.9f, 1.3f, paint)
                    canvas.drawCircle(c.x + c.size*0.6f, c.y - c.size*0.9f, 1.3f, paint)
                }
            }
        }
    }

    fun clearCritters() { critters.clear(); critterAccum = 0f }

    // -------------------------------------------------------------------
    // 4) 계절 향 파티클
    // -------------------------------------------------------------------

    private class Scent(var x:Float, var y:Float, var vx:Float, var vy:Float, var life:Float, var col:Int, var size:Float)
    private val scents = ArrayList<Scent>()
    private var scentAccum = 0f

    fun updateScents(dt: Float, season: Season, px: Float, py: Float, rnd: Random) {
        scentAccum += dt
        if (scentAccum > 0.8f && scents.size < 14) {
            scentAccum = 0f
            val col = when (season) {
                Season.SPRING -> 0xFFE5B4D3
                Season.SUMMER -> 0xFFB7D8B3
                Season.AUTUMN -> 0xFFE4A26A
                Season.WINTER -> 0xFFE9B97A
            }.toInt()
            scents.add(Scent(px + (rnd.nextFloat()-0.5f)*14f, py - 12f,
                (rnd.nextFloat()-0.5f)*6f, -3f - rnd.nextFloat()*4f,
                3.5f + rnd.nextFloat()*2f, col, 2f + rnd.nextFloat()*1.5f))
        }
        val it = scents.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= dt
            s.x += s.vx * dt; s.y += s.vy * dt
            s.vx += (kotlin.math.sin((s.life + s.x) * 1.1f)) * 1.2f * dt
            s.vy *= 0.99f
            if (s.life <= 0f) it.remove()
        }
    }

    fun drawScents(canvas: Canvas, paint: Paint) {
        for (s in scents) {
            val a = (s.life / 5f).coerceIn(0f, 0.45f)
            paint.color = Color.argb((80*a).toInt(), Color.red(s.col), Color.green(s.col), Color.blue(s.col))
            canvas.drawCircle(s.x, s.y, s.size, paint)
        }
    }

    // -------------------------------------------------------------------
    // 5) 허브 인벤토리 & 허브차
    // -------------------------------------------------------------------

    fun herbs(state: GameState): MutableMap<String, Int> {
        val h = ensureState(state)
        val arr = h.optJSONArray("herbs") ?: run { val a = JSONArray(); h.put("herbs", a); a }
        val map = LinkedHashMap<String, Int>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            map[o.getString("id")] = o.optInt("n", 0)
        }
        return object : LinkedHashMap<String, Int>(map) {
            override fun put(key: String, value: Int): Int? {
                val prev = super.put(key, value)
                val a = JSONArray()
                for ((k, v) in this) a.put(JSONObject().put("id", k).put("n", v))
                h.put("herbs", a)
                return prev
            }
        }
    }

    fun addHerb(state: GameState, id: String, n: Int = 1) {
        val map = herbs(state)
        map[id] = ((map[id] ?: 0) + n).coerceAtMost(99)
    }

    fun brewTea(state: GameState): Herb? {
        val map = herbs(state)
        val best = HERBS.filter { (map[it.id] ?: 0) > 0 }
            .maxByOrNull { it.luckBonus * 2 + it.hungerBonus } ?: return null
        map[best.id] = (map[best.id] ?: 1) - 1
        state.hunger = (state.hunger + best.hungerBonus).coerceIn(0f, 100f)
        state.luck = (state.luck + best.luckBonus).coerceIn(0f, 100f)
        unlock(state, "first_tea")
        val tasted = HERBS.count { (map[it.id] ?: 0) > 0 || ensureState(state).optBoolean("tasted_${it.id}", false) }
        ensureState(state).put("tasted_${best.id}", true)
        if (tasted >= 10) unlock(state, "tea_variety")
        return best
    }

    // -------------------------------------------------------------------
    // 6) 탐조 일기
    // -------------------------------------------------------------------

    data class DiaryEntry(val day: Int, val seasonName: String, val weather: String, val text: String, val stars: Int)

    fun writeToday(state: GameState, weatherName: String): DiaryEntry? {
        val s = ensureState(state)
        val today = state.day
        val arr = s.optJSONArray("diary") ?: JSONArray().also { s.put("diary", it) }
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optInt("day") == today) return null
        }
        val photosToday = s.optInt("photosToday", 0)
        val newBirds = s.optInt("newBirdsToday", 0)
        val pizzasBaked = s.optInt("pizzasBakedToday", 0)
        val catsPet = s.optInt("catsPetToday", 0)
        val herbsPicked = s.optInt("herbsPickedToday", 0)
        val seasonName = state.season().label
        val lines = ArrayList<String>()
        lines.add("${seasonName}날, $weatherName.")
        if (photosToday == 0 && pizzasBaked == 0 && catsPet == 0 && herbsPicked == 0) {
            lines.add("오늘은 그냥 동네를 한 바퀴 돌았다.")
            lines.add("바람이 좋았다. 그것만으로도 충분한 하루.")
        } else {
            if (photosToday > 0) lines.add("사진 ${photosToday}장을 찍었다" +
                if (newBirds > 0) " — 처음 보는 새가 ${newBirds}종 있었다." else ".")
            if (pizzasBaked > 0) lines.add("피자를 ${pizzasBaked}판 구웠다. 화덕 온도가 좋은 날이었다.")
            if (herbsPicked > 0) lines.add("풀냄새를 맡으며 허브를 ${herbsPicked}번 주웠다.")
            if (catsPet > 0) lines.add("고양이를 ${catsPet}번 쓰다듬었다. 꼬리가 살랑살랑.")
            if (state.weather() == Weather.RAIN) lines.add("비가 내렸지만 우산은 쓰지 않았다.")
            if (state.isNight()) lines.add("밤공기가 좋았다.")
        }
        val stars = when {
            newBirds >= 2 || photosToday >= 8 -> 3
            photosToday + pizzasBaked + catsPet >= 3 -> 2
            else -> 1
        }
        lines.add("오늘도 무사히.")
        val text = lines.joinToString("\n")
        arr.put(JSONObject().apply {
            put("day", today); put("season", seasonName); put("w", weatherName)
            put("text", text); put("stars", stars)
        })
        s.put("photosToday", 0); s.put("newBirdsToday", 0); s.put("pizzasBakedToday", 0)
        s.put("catsPetToday", 0); s.put("herbsPickedToday", 0)
        return DiaryEntry(today, seasonName, weatherName, text, stars)
    }

    fun diary(state: GameState): List<DiaryEntry> {
        val arr = ensureState(state).optJSONArray("diary") ?: return emptyList()
        val out = ArrayList<DiaryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(DiaryEntry(o.optInt("day"), o.optString("season"), o.optString("w"),
                o.optString("text"), o.optInt("stars", 1)))
        }
        return out
    }

    fun bumpToday(state: GameState, key: String, delta: Int = 1) {
        val h = ensureState(state)
        h.put(key, h.optInt(key, 0) + delta)
    }

    // -------------------------------------------------------------------
    // 7) 작은 기념 Tiny Moments
    // -------------------------------------------------------------------

    data class TinyMoment(val id: String, val name: String, val emoji: String, val luckReward: Int, val line: String)

    val MOMENTS = listOf(
        TinyMoment("first_tea", "첫 허브차", "🍵", 1, "따뜻한 향이 몸을 감싼다."),
        TinyMoment("ten_herbs", "풀꽃 수집가", "🌿", 2, "주머니가 풀냄새로 가득하다."),
        TinyMoment("cat_love_40", "고양이 집사", "🐈", 3, "골목 고양이가 내 발에 비빈다."),
        TinyMoment("bench_sunset", "벤치에서 노을", "🌇", 2, "멍 때린 8분이 가장 소중했다."),
        TinyMoment("first_firefly", "첫 반딧불", "✨", 1, "여름밤이 반짝인다."),
        TinyMoment("first_snow", "첫눈", "❄️", 2, "세상이 소리 없이 하얗게."),
        TinyMoment("rain_walk", "비 오는 산책", "🌧", 1, "빗소리가 발걸음과 섞인다."),
        TinyMoment("pizza_master", "화덕 장인", "🔥", 2, "걸작 피자를 구웠다."),
        TinyMoment("lucky_100", "행운 가득", "🍀", 0, "마음이 가볍다. 뭐든 잘 풀릴 것 같아."),
        TinyMoment("home_decorated", "집이 따뜻해", "🏠", 2, "작은 소품들 덕에 집이 웃는다."),
        TinyMoment("all_regions", "전국 일주", "🗺", 3, "자전거 바퀴가 전국을 돌았다."),
        TinyMoment("night_owl", "밤새 관찰", "🦉", 2, "밤에만 오는 손님을 만났다."),
        TinyMoment("spring_picnic", "봄 소풍", "🌸", 1, "벚꽃잎이 어깨에 내려앉았다."),
        TinyMoment("autumn_maple", "단풍 길", "🍁", 1, "단풍잎 밟는 소리가 좋다."),
        TinyMoment("winter_crane", "두루미 소식", "🕊", 3, "눈 속 두루미를 보았다."),
        TinyMoment("sea_breeze", "바닷바람", "🌊", 1, "바람이 얼굴을 간지럽힌다."),
        TinyMoment("cat_gift", "고양이의 선물", "🎀", 2, "고양이가 쓸 만한 걸 물어다 줬다."),
        TinyMoment("tea_variety", "열 가지 향", "🫖", 2, "열 가지 허브차를 모두 맛보았다."),
        TinyMoment("bench_cat", "벤치 위 고양이", "🐈‍⬛", 1, "벤치에 앉은 고양이가 내 옆에 왔다."),
        TinyMoment("bicycle_ride", "장거리 라이더", "🚲", 1, "자전거 안장이 제 몸처럼 익숙해졌다."),
        TinyMoment("first_butterfly", "첫 나비", "🦋", 1, "노란 나비가 앞장서서 날았다."),
        TinyMoment("dragonfly_hover", "잠자리 정지비행", "🪰", 1, "잠자리가 코앞에서 멈췄다."),
        TinyMoment("full_moon", "보름달", "🌕", 2, "달이 너무 밝아 그림자가 생겼다."),
        TinyMoment("quiet_morning", "조용한 아침", "🌅", 2, "해 뜨기 전에 나와 버렸다.")
    )

    private fun unlocked(state: GameState): LinkedHashSet<String> {
        val h = ensureState(state)
        val arr = h.optJSONArray("moments") ?: JSONArray().also { h.put("moments", it) }
        val set = LinkedHashSet<String>()
        for (i in 0 until arr.length()) set.add(arr.getString(i))
        return set
    }

    fun unlock(state: GameState, id: String): TinyMoment? {
        val set = unlocked(state)
        if (id in set) return null
        val m = MOMENTS.firstOrNull { it.id == id } ?: return null
        set.add(id)
        val arr = JSONArray(); set.forEach { arr.put(it) }
        ensureState(state).put("moments", arr)
        state.luck = (state.luck + m.luckReward).coerceAtMost(100f)
        return m
    }

    fun momentCount(state: GameState): Int = unlocked(state).size

    // -------------------------------------------------------------------
    fun ensureState(state: GameState): JSONObject = state.healing
    fun onNewDay(state: GameState) { clearCritters() }
    const val BENCH_REST_BONUS = 0.20f
}
