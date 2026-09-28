@file:Suppress("unused", "UNUSED_PARAMETER")

package org.json

/**
 * [P11] **JVM 테스트용 org.json 구현** — 안드로이드 SDK 없이 `GameState` 의 저장/마이그레이션
 * 로직을 PC에서 곧바로 돌려보기 위한 스텁이다 (`tools/PizzaTest.kt` 가 사용).
 *
 * - 앱 빌드에는 **전혀 들어가지 않는다** (Gradle 소스셋은 `app/src/main/java` 만 본다).
 * - 컴파일은 실제 `android.jar` 의 org.json 선언으로 하고, **실행할 때만** 이 구현을 클래스패스
 *   맨 앞에 둔다. 그래서 시그니처를 android.jar 에 맞춘다 — Kotlin은 컴파일 시점에
 *   `put(String, int)` 같은 구체 오버로드를 골라 담으므로 그 오버로드를 전부 선언해야 하고,
 *   `optInt(String)` / `optInt(String, int)` 도 각각 따로 있어야 한다.
 * - 동작은 테스트에 충분한 만큼 실제처럼: 값 보관·조회·JSON 문자열 직렬화/파싱/이스케이프.
 *
 * ```bash
 * kotlinc -d /tmp/jsonstub tools/jvm_stub/Json.kt
 * java -cp /tmp/jsonstub:android.jar:게임클래스:테스트클래스:kotlin-stdlib.jar PizzaTestKt
 * ```
 */

private fun esc(s: String): String {
    val sb = StringBuilder(s.length + 8)
    for (ch in s) when (ch) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
    }
    return sb.toString()
}

/** `JSONObject.NULL` — "키는 있는데 값이 null"을 나타내는 센티널 (안드로이드와 동일한 의미). */
internal object JsonNull {
    override fun toString(): String = "null"
}

private fun wrap(v: Any?): Any? = when (v) {
    null, JsonNull, is String, is Boolean, is Number, is JSONObject, is JSONArray -> v
    is Map<*, *> -> JSONObject(v)
    is Collection<*> -> JSONArray(v)
    is Array<*> -> JSONArray(v.toList())
    else -> v.toString()
}

private fun lit(v: Any?): String = when (v) {
    null, JsonNull -> "null"
    is String -> "\"" + esc(v) + "\""
    is Double, is Float -> {
        val d = (v as Number).toDouble()
        if (d.isNaN() || d.isInfinite()) "null" else v.toString()
    }
    is JSONObject, is JSONArray -> v.toString()
    else -> v.toString()
}

class JSONObject {

    companion object {
        @JvmField
        val NULL: Any = JsonNull
    }

    internal val map = LinkedHashMap<String, Any?>()

    constructor()

    constructor(json: String) {
        val p = JsonReader(json)
        (p.readValue() as? JSONObject)?.let { map.putAll(it.map) }
    }

    constructor(copyFrom: Map<*, *>) {
        for ((k, v) in copyFrom) if (k != null) map[k.toString()] = wrap(v)
    }

    fun put(name: String, value: Any?): JSONObject { map[name] = wrap(value); return this }
    fun put(name: String, value: Int): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Long): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Double): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Boolean): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Collection<*>): JSONObject = put(name, JSONArray(value))
    fun put(name: String, value: Map<*, *>): JSONObject = put(name, JSONObject(value))

    fun remove(name: String): Any? = map.remove(name)
    fun has(name: String): Boolean = map.containsKey(name)
    fun length(): Int = map.size
    fun keys(): Iterator<String> = map.keys.iterator()
    fun keySet(): Set<String> = map.keys
    fun names(): JSONArray = JSONArray(map.keys.toList())

    fun opt(name: String): Any? = map[name]
    fun get(name: String): Any = map[name] ?: throw RuntimeException("No value for $name")
    fun getInt(name: String): Int = optInt(name)
    fun getString(name: String): String = optString(name)
    fun getJSONObject(name: String): JSONObject = optJSONObject(name) ?: throw RuntimeException("No $name")
    fun getJSONArray(name: String): JSONArray = optJSONArray(name) ?: throw RuntimeException("No $name")

    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = (map[name] as? Number)?.toInt()
        ?: (map[name] as? String)?.toDoubleOrNull()?.toInt() ?: fallback

    fun optLong(name: String): Long = optLong(name, 0L)
    fun optLong(name: String, fallback: Long): Long = (map[name] as? Number)?.toLong()
        ?: (map[name] as? String)?.toDoubleOrNull()?.toLong() ?: fallback

    fun optDouble(name: String): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String, fallback: Double): Double = (map[name] as? Number)?.toDouble()
        ?: (map[name] as? String)?.toDoubleOrNull() ?: fallback

    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = when (val v = map[name]) {
        JsonNull -> fallback
        is Boolean -> v
        is String -> v.equals("true", true)
        is Number -> v.toInt() != 0
        else -> fallback
    }

    fun optString(name: String): String = optString(name, "")
    fun isNull(name: String): Boolean = map[name] === JsonNull

    fun optString(name: String, fallback: String): String = when (val v = map[name]) {
        null, JsonNull -> fallback
        is String -> v
        else -> v.toString()
    }

    fun optJSONObject(name: String): JSONObject? = map[name] as? JSONObject
    fun optJSONArray(name: String): JSONArray? = map[name] as? JSONArray

    override fun toString(): String {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(esc(k)).append("\":").append(lit(v))
        }
        return sb.append('}').toString()
    }
}

class JSONArray {

    companion object {
        @JvmField
        val NULL: Any = JsonNull
    }

    internal val list = ArrayList<Any?>()

    constructor()
    constructor(json: String) {
        val p = JsonReader(json)
        (p.readValue() as? JSONArray)?.let { list.addAll(it.list) }
    }
    constructor(coll: Collection<*>?) { coll?.forEach { list.add(wrap(it)) } }

    fun put(value: Any?): JSONArray { list.add(wrap(value)); return this }
    fun put(value: Int): JSONArray = put(value as Any?)
    fun put(value: Long): JSONArray = put(value as Any?)
    fun put(value: Double): JSONArray = put(value as Any?)
    fun put(value: Boolean): JSONArray = put(value as Any?)
    fun put(index: Int, value: Any?): JSONArray {
        while (list.size <= index) list.add(null)
        list[index] = wrap(value)
        return this
    }

    fun length(): Int = list.size
    fun opt(index: Int): Any? = list.getOrNull(index)
    fun get(index: Int): Any = list.getOrNull(index) ?: throw RuntimeException("No index $index")
    fun remove(index: Int): Any? = if (index in list.indices) list.removeAt(index) else null

    fun optInt(index: Int): Int = optInt(index, 0)
    fun optInt(index: Int, fallback: Int): Int = (list.getOrNull(index) as? Number)?.toInt() ?: fallback
    fun optLong(index: Int, fallback: Long): Long = (list.getOrNull(index) as? Number)?.toLong() ?: fallback
    fun optDouble(index: Int, fallback: Double): Double = (list.getOrNull(index) as? Number)?.toDouble() ?: fallback
    fun optBoolean(index: Int, fallback: Boolean): Boolean = (list.getOrNull(index) as? Boolean) ?: fallback
    fun optString(index: Int): String = optString(index, "")
    fun optString(index: Int, fallback: String): String = when (val v = list.getOrNull(index)) {
        null -> fallback
        is String -> v
        else -> v.toString()
    }
    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = list.getOrNull(index) as? JSONArray
    fun getJSONObject(index: Int): JSONObject = optJSONObject(index) ?: throw RuntimeException("No obj $index")

    override fun toString(): String {
        val sb = StringBuilder("[")
        for ((i, v) in list.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append(lit(v))
        }
        return sb.append(']').toString()
    }
}

/** 아주 작은 재귀 하강 JSON 파서 — 세이브 문자열을 읽을 만큼만. */
private class JsonReader(private val s: String) {
    private var i = 0

    fun readValue(): Any? {
        ws()
        if (i >= s.length) return null
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> if (c == '-' || c.isDigit()) num() else null
        }
    }

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun obj(): JSONObject {
        val o = JSONObject()
        i++
        ws()
        if (i < s.length && s[i] == '}') { i++; return o }
        while (i < s.length) {
            ws()
            val k = str()
            ws()
            if (i < s.length && s[i] == ':') i++
            o.map[k] = readValue()
            ws()
            if (i < s.length && s[i] == ',') { i++; continue }
            if (i < s.length && s[i] == '}') { i++ }
            break
        }
        return o
    }

    private fun arr(): JSONArray {
        val a = JSONArray()
        i++
        ws()
        if (i < s.length && s[i] == ']') { i++; return a }
        while (i < s.length) {
            a.list.add(readValue())
            ws()
            if (i < s.length && s[i] == ',') { i++; continue }
            if (i < s.length && s[i] == ']') { i++ }
            break
        }
        return a
    }

    private fun str(): String {
        if (i >= s.length || s[i] != '"') return ""
        i++
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            if (c == '"') break
            if (c == '\\' && i < s.length) {
                when (val e = s[i++]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        val hex = s.substring(i, (i + 4).coerceAtMost(s.length))
                        i += 4
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                    }
                    else -> sb.append(e)
                }
            } else sb.append(c)
        }
        return sb.toString()
    }

    private fun num(): Number {
        val st = i
        if (i < s.length && s[i] == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
        val txt = s.substring(st, i)
        return if ('.' in txt || 'e' in txt || 'E' in txt) (txt.toDoubleOrNull() ?: 0.0) else (txt.toLongOrNull() ?: 0L)
    }
}
