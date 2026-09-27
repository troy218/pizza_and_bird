@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — org.json 최소 구현 (GameState.kt 컴파일/실행용).
 * Android 빌드에는 포함되지 않는다. 직렬화 + 간단한 파서만 지원.
 */
package org.json

class JSONException(message: String) : RuntimeException(message)

class JSONObject {
    private val map = LinkedHashMap<String, Any>()

    constructor()

    constructor(json: String) {
        val v = JsonParser.parse(json.trim())
        if (v !is Map<*, *>) throw JSONException("JSONObject expected")
        for ((k, value) in v) map[k.toString()] = normalize(value)
    }

    constructor(m: Map<*, *>) {
        for ((k, v) in m) map[k.toString()] = normalize(v)
    }

    /** [P05] 백업 코드가 중첩 객체를 그대로 주고받아야 하므로 Map/List 도 JSON 값으로 감싼다. */
    private fun normalize(v: Any?): Any = when (v) {
        is Int, is Long, is Boolean, is Double, is String, is JSONObject, is JSONArray -> v
        is Float -> v.toDouble()
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { for ((k2, v2) in v) putAny(k2.toString(), normalize(v2)) }
        is List<*> -> JSONArray().apply { for (e in v) addAny(normalize(e)) }
        else -> v.toString()
    }

    internal fun putAny(key: String, value: Any) {
        map[key] = value
    }

    fun put(key: String, value: Int): JSONObject {
        map[key] = value; return this
    }

    fun put(key: String, value: Long): JSONObject {
        map[key] = value; return this
    }

    fun put(key: String, value: Boolean): JSONObject {
        map[key] = value; return this
    }

    fun put(key: String, value: Double): JSONObject {
        map[key] = value; return this
    }

    fun put(key: String, value: String): JSONObject {
        map[key] = value; return this
    }

    fun put(key: String, value: JSONObject): JSONObject {
        map[key] = value; return this
    }

    fun put(key: String, value: JSONArray): JSONObject {
        map[key] = value; return this
    }

    fun has(key: String): Boolean = map.containsKey(key)

    fun opt(key: String): Any? = map[key]

    // ---- [P05] 백업 코드가 쓰는 get 계열 (없으면 JSONException) ----

    fun get(key: String): Any = map[key] ?: throw JSONException("no value for $key")

    fun getString(key: String): String = get(key).let { if (it is String) it else it.toString() }

    fun getInt(key: String): Int = when (val v = get(key)) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is String -> v.toDoubleOrNull()?.toInt() ?: throw JSONException("not an int: $key")
        else -> throw JSONException("not an int: $key")
    }

    fun getLong(key: String): Long = when (val v = get(key)) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> v.toLong()
        is String -> v.toDoubleOrNull()?.toLong() ?: throw JSONException("not a long: $key")
        else -> throw JSONException("not a long: $key")
    }

    fun getDouble(key: String): Double = when (val v = get(key)) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull() ?: throw JSONException("not a double: $key")
        else -> throw JSONException("not a double: $key")
    }

    fun getBoolean(key: String): Boolean = when (val v = get(key)) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: throw JSONException("not a boolean: $key")
        else -> throw JSONException("not a boolean: $key")
    }

    fun getJSONObject(key: String): JSONObject =
        optJSONObject(key) ?: throw JSONException("not a JSONObject: $key")

    fun getJSONArray(key: String): JSONArray =
        optJSONArray(key) ?: throw JSONException("not a JSONArray: $key")

    fun remove(key: String): Any? = map.remove(key)

    fun names(): JSONArray {
        val a = JSONArray()
        for (k in map.keys) a.put(k)
        return a
    }

    fun optInt(key: String, def: Int = 0): Int = when (val v = map[key]) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is String -> v.toDoubleOrNull()?.toInt() ?: def
        else -> def
    }

    fun optLong(key: String, def: Long = 0L): Long = when (val v = map[key]) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> v.toLong()
        is String -> v.toDoubleOrNull()?.toLong() ?: def
        else -> def
    }

    fun optDouble(key: String, def: Double = Double.NaN): Double = when (val v = map[key]) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull() ?: def
        else -> def
    }

    fun optBoolean(key: String, def: Boolean = false): Boolean = when (val v = map[key]) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: def
        else -> def
    }

    fun optString(key: String, def: String = ""): String = (map[key] as? String) ?: def

    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject

    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray

    fun keys(): Iterator<String> = map.keys.iterator()

    fun length(): Int = map.size

    override fun toString(): String {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            JsonParser.writeString(sb, k)
            sb.append(':')
            JsonParser.write(sb, v)
        }
        sb.append('}')
        return sb.toString()
    }

    companion object {
        val NULL = JSONObject()
    }
}

class JSONArray {
    private val list = ArrayList<Any>()

    constructor()

    constructor(json: String) {
        val v = JsonParser.parse(json.trim())
        if (v !is List<*>) throw JSONException("JSONArray expected")
        for (e in v) list.add(wrapValue(e))
    }

    internal fun addAny(value: Any) {
        list.add(value)
    }

    private fun wrapValue(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { for ((k2, v2) in v) putAny(k2.toString(), wrapValue(v2)) }
        is List<*> -> JSONArray().apply { for (e in v) addAny(wrapValue(e)) }
        is Float -> v.toDouble()
        else -> v
    }

    fun put(value: Int): JSONArray {
        list.add(value); return this
    }

    fun put(value: Long): JSONArray {
        list.add(value); return this
    }

    fun put(value: Boolean): JSONArray {
        list.add(value); return this
    }

    fun put(value: Double): JSONArray {
        list.add(value); return this
    }

    fun put(value: String): JSONArray {
        list.add(value); return this
    }

    fun put(value: JSONObject): JSONArray {
        list.add(value); return this
    }

    fun put(value: JSONArray): JSONArray {
        list.add(value); return this
    }

    fun length(): Int = list.size

    // ---- [P05] get 계열 ----
    fun get(index: Int): Any = list.getOrNull(index) ?: throw JSONException("index $index out of bounds")

    fun getString(index: Int): String = get(index).let { if (it is String) it else it.toString() }

    fun getInt(index: Int): Int = when (val v = get(index)) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        else -> throw JSONException("not an int at $index")
    }

    fun getJSONObject(index: Int): JSONObject =
        optJSONObject(index) ?: throw JSONException("not a JSONObject at $index")

    fun getJSONArray(index: Int): JSONArray =
        optJSONArray(index) ?: throw JSONException("not a JSONArray at $index")

    fun optInt(index: Int, def: Int = 0): Int = when (val v = list.getOrNull(index)) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is String -> v.toDoubleOrNull()?.toInt() ?: def
        else -> def
    }

    fun optDouble(index: Int, def: Double = Double.NaN): Double = when (val v = list.getOrNull(index)) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull() ?: def
        else -> def
    }

    fun optBoolean(index: Int, def: Boolean = false): Boolean = when (val v = list.getOrNull(index)) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: def
        else -> def
    }

    fun optString(index: Int, def: String = ""): String = (list.getOrNull(index) as? String) ?: def

    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject

    fun optJSONArray(index: Int): JSONArray? = list.getOrNull(index) as? JSONArray

    fun opt(index: Int): Any? = list.getOrNull(index)

    override fun toString(): String {
        val sb = StringBuilder("[")
        var first = true
        for (v in list) {
            if (!first) sb.append(',')
            first = false
            JsonParser.write(sb, v)
        }
        sb.append(']')
        return sb.toString()
    }
}

internal object JsonParser {
    fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null, is JSONObject -> if (v == null) sb.append("null") else sb.append(v.toString())
            is String -> writeString(sb, v)
            is Boolean, is Int, is Long, is Double -> sb.append(v.toString())
            is JSONArray -> sb.append(v.toString())
            else -> writeString(sb, v.toString())
        }
    }

    fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    fun parse(s: String): Any? {
        val p = P(s)
        val v = p.parseValue()
        p.skipWs()
        if (!p.eof()) throw JSONException("trailing characters")
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun eof(): Boolean = i >= s.length
        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun parseValue(): Any? {
            skipWs()
            if (eof()) throw JSONException("unexpected end")
            return when (s[i]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> parseStr()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNum()
            }
        }

        fun expect(w: String) {
            if (!s.startsWith(w, i)) throw JSONException("expected $w")
            i += w.length
        }

        fun parseObj(): LinkedHashMap<String, Any> {
            val m = LinkedHashMap<String, Any>()
            i++ // {
            skipWs()
            if (!eof() && s[i] == '}') {
                i++; return m
            }
            while (true) {
                skipWs()
                if (eof() || s[i] != '"') throw JSONException("expected key")
                val k = parseStr()
                skipWs()
                if (eof() || s[i] != ':') throw JSONException("expected :")
                i++
                m[k] = parseValue() ?: JSONObject.NULL
                skipWs()
                if (eof()) throw JSONException("unexpected end in object")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw JSONException("expected , or }")
                }
            }
        }

        fun parseArr(): ArrayList<Any> {
            val a = ArrayList<Any>()
            i++ // [
            skipWs()
            if (!eof() && s[i] == ']') {
                i++; return a
            }
            while (true) {
                a.add(parseValue() ?: JSONObject.NULL)
                skipWs()
                if (eof()) throw JSONException("unexpected end in array")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return a }
                    else -> throw JSONException("expected , or ]")
                }
            }
        }

        fun parseStr(): String {
            val sb = StringBuilder()
            i++ // "
            while (true) {
                if (eof()) throw JSONException("unterminated string")
                val c = s[i]
                when {
                    c == '"' -> {
                        i++; return sb.toString()
                    }

                    c == '\\' -> {
                        i++
                        if (eof()) throw JSONException("bad escape")
                        when (val e = s[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'u' -> {
                                if (i + 4 >= s.length) throw JSONException("bad unicode escape")
                                sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                                i += 4
                            }

                            else -> throw JSONException("bad escape $e")
                        }
                        i++
                    }

                    else -> {
                        sb.append(c); i++
                    }
                }
            }
        }

        fun parseNum(): Any {
            val start = i
            if (!eof() && (s[i] == '-' || s[i] == '+')) i++
            var isDouble = false
            while (!eof()) {
                val c = s[i]
                if (c.isDigit()) {
                    i++
                } else if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    isDouble = true; i++
                } else break
            }
            val tok = s.substring(start, i)
            if (tok.isEmpty()) throw JSONException("bad number")
            return if (isDouble) tok.toDouble() else (tok.toLongOrNull() ?: tok.toDouble())
        }
    }
}
