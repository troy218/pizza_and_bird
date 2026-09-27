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
        @Suppress("UNCHECKED_CAST")
        map.putAll(v as Map<String, Any>)
    }

    constructor(m: Map<*, *>) {
        for ((k, v) in m) put(k.toString(), normalize(v))
    }

    private fun normalize(v: Any?): Any = when (v) {
        is Int, is Long, is Boolean, is Double, is String, is JSONObject, is JSONArray -> v
        is Float -> v.toDouble()
        null -> JSONObject.NULL
        else -> v.toString()
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
        for (e in v) list.add(e ?: JSONObject.NULL)
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
