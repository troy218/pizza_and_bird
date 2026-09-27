package com.pizzaandbird.game

import android.content.Context
import org.json.JSONArray

/**
 * 엑셀 「한반도 조류 598종 고화질 사진도감 2025」 기반 조류 대도감 데이터.
 *
 * 598종의 고화질 사진 메타데이터, 생태·식별 설명(위키백과 서두 등), 사진 저작권 및 보전등급 정보 제공.
 */
data class BirdEncyclopediaEntry(
    val num: Int,
    val name: String,
    val sci: String,
    val eng: String,
    val order: String,
    val family: String,
    val cat: String,
    val desc: String,
    val descSrc: String,
    val engDesc: String,
    val subs: List<String>,
    val status: String,
    val obs: Int,
    val license: String,
    val author: String
) {
    val photoCredit: String
        get() = when {
            author.isNotBlank() && license.isNotBlank() -> "📷 $author · ${license.uppercase()}"
            author.isNotBlank() -> "📷 $author"
            else -> "📷 iNaturalist"
        }

    val statusBadge: String?
        get() = when {
            status.isNotBlank() -> status
            cat == "가-2" -> "미기록 후보"
            else -> null
        }
}

object BirdEncyclopedia {
    private var initialized = false
    private val list = ArrayList<BirdEncyclopediaEntry>()
    private val byNum = HashMap<Int, BirdEncyclopediaEntry>()
    private val byName = HashMap<String, BirdEncyclopediaEntry>()

    fun init(context: Context) {
        if (initialized) return
        try {
            val stream = context.assets.open("birds_encyclopedia.json")
            val jsonText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(jsonText)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val subsList = ArrayList<String>()
                val subsArr = obj.optJSONArray("subs")
                if (subsArr != null) {
                    for (j in 0 until subsArr.length()) {
                        subsList.add(subsArr.getString(j))
                    }
                }
                val entry = BirdEncyclopediaEntry(
                    num = obj.getInt("num"),
                    name = obj.getString("name"),
                    sci = obj.optString("sci", ""),
                    eng = obj.optString("eng", ""),
                    order = obj.optString("order", ""),
                    family = obj.optString("family", ""),
                    cat = obj.optString("cat", ""),
                    desc = obj.optString("desc", ""),
                    descSrc = obj.optString("descSrc", ""),
                    engDesc = obj.optString("engDesc", ""),
                    subs = subsList,
                    status = obj.optString("status", ""),
                    obs = obj.optInt("obs", 0),
                    license = obj.optString("license", ""),
                    author = obj.optString("author", "")
                )
                list.add(entry)
                byNum[entry.num] = entry
                byName[entry.name] = entry
            }
            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val ALL: List<BirdEncyclopediaEntry> get() = list
    val count: Int get() = if (list.isNotEmpty()) list.size else 598

    fun get(num: Int): BirdEncyclopediaEntry? = byNum[num]
    fun get(name: String): BirdEncyclopediaEntry? = byName[name]
}
