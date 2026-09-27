// [P05] JVM 검증용 org.json 스텁 — 안드로이드 org.json 과 **동작이 같도록** 최소 구현.
// (Maven Central 이 막힌 샌드박스에서도 백업 코드 로직을 실행해 보려고 만든다.
//  실기기에서는 안드로이드 플랫폼의 org.json 이 쓰이므로 이 파일은 테스트 전용이다.)
//
// 안드로이드 org.json 의 특징을 맞춘다:
//  - 정수처럼 보이는 Double 은 ".0" 없이 출력 (100.0 → "100")
//  - JSONObject(null) 은 이름-값 쌍을 넣지 않는다
//  - optXxx 는 타입이 다르면 기본값을 돌려준다 (Number 끼리는 변환해 준다)
package org.json

class JSONException(msg: String) : Exception(msg)

class JSONObject {

    private val map = LinkedHashMap<String, Any?>()

    constructor()

    constructor(json: String) {
        val p = JsonParser(json)
        val v = p.readValue()
        p.skipWs()
        if (!p.eof()) throw JSONException(" trailing characters")
        if (v !is JSONObject) throw JSONException(" not an object")
        for ((k, value) in v.map) map[k] = value
    }

    @Suppress("UNCHECKED_CAST")
    constructor(copyFrom: Map<*, *>?) {
        if (copyFrom != null) {
            for ((k, v) in copyFrom) {
                if (k == null) continue
                map[k.toString()] = wrap(v)
            }
        }
    }

    fun length(): Int = map.size

    fun has(name: String): Boolean = map.containsKey(name)

    fun keys(): Iterator<String> = map.keys.iterator()

    fun names(): JSONArray {
        val a = JSONArray()
        for (k in map.keys) a.put(k)
        return a
    }

    fun get(name: String): Any = opt(name) ?: throw JSONException(" no value for $name")

    fun opt(name: String): Any? = map[name]

    fun getString(name: String): String = get(name).toString()

    fun optString(name: String, fallback: String = ""): String {
        val v = map[name] ?: return fallback
        return if (v is String) v else v.toString()
    }

    fun getInt(name: String): Int = (get(name) as? Number)?.toInt() ?: throw JSONException(" not an int: $name")

    fun optInt(name: String, fallback: Int = 0): Int = (map[name] as? Number)?.toInt() ?: fallback

    fun getLong(name: String): Long = (get(name) as? Number)?.toLong() ?: throw JSONException(" not a long: $name")

    fun optLong(name: String, fallback: Long = 0L): Long = (map[name] as? Number)?.toLong() ?: fallback

    fun getDouble(name: String): Double =
        (get(name) as? Number)?.toDouble() ?: throw JSONException(" not a double: $name")

    fun optDouble(name: String, fallback: Double = Double.NaN): Double =
        (map[name] as? Number)?.toDouble() ?: fallback

    fun getBoolean(name: String): Boolean = when (val v = get(name)) {
        is Boolean -> v
        is String -> when (v) {
            "true" -> true
            "false" -> false
            else -> throw JSONException(" not a boolean: $name")
        }
        else -> throw JSONException(" not a boolean: $name")
    }

    fun optBoolean(name: String, fallback: Boolean = false): Boolean = when (val v = map[name]) {
        is Boolean -> v
        is String -> when (v) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
        else -> fallback
    }

    fun getJSONObject(name: String): JSONObject =
        opt(name) as? JSONObject ?: throw JSONException(" not a JSONObject: $name")

    fun optJSONObject(name: String): JSONObject? = map[name] as? JSONObject

    fun getJSONArray(name: String): JSONArray =
        opt(name) as? JSONArray ?: throw JSONException(" not a JSONArray: $name")

    fun optJSONArray(name: String): JSONArray? = map[name] as? JSONArray

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) {
            map.remove(name)
            return this
        }
        map[name] = wrap(value)
        return this
    }

    fun put(name: String, value: Boolean): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Int): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Long): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Double): JSONObject = apply {
        if (value.isNaN() || value.isInfinite()) throw JSONException(" forbidden numeric value: $value")
        map[name] = value
    }

    fun remove(name: String): Any? = map.remove(name)

    override fun toString(): String {
        val sb = StringBuilder()
        write(sb)
        return sb.toString()
    }

    fun toString(indentSpaces: Int): String = toString()

    internal fun write(sb: StringBuilder) {
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            JsonWriter.quote(k, sb)
            sb.append(':')
            JsonWriter.value(v, sb)
        }
        sb.append('}')
    }

    companion object {
        /** 안드로이드와 마찬가지로 컬렉션/배열/래퍼 타입은 JSON 값으로 감싼다. */
        internal fun wrap(v: Any?): Any? = when (v) {
            null -> null
            is JSONObject, is JSONArray -> v
            is String, is Boolean, is Int, is Long, is Double, is Float -> v
            is Number -> v
            is Collection<*> -> JSONArray().apply { for (e in v) put(e) }
            else -> v.toString()
        }

        val NULL: Any = Object()
    }
}

class JSONArray {

    private val list = ArrayList<Any?>()

    constructor()

    constructor(json: String) {
        val p = JsonParser(json)
        val v = p.readValue()
        p.skipWs()
        if (!p.eof()) throw JSONException(" trailing characters")
        if (v !is JSONArray) throw JSONException(" not an array")
        list.addAll(v.list)
    }

    fun length(): Int = list.size

    fun get(i: Int): Any = list.getOrNull(i) ?: throw JSONException(" index $i out of bounds")

    fun opt(i: Int): Any? = list.getOrNull(i)

    fun optString(i: Int, fallback: String = ""): String {
        val v = list.getOrNull(i) ?: return fallback
        return if (v is String) v else v.toString()
    }

    fun optInt(i: Int, fallback: Int = 0): Int = (list.getOrNull(i) as? Number)?.toInt() ?: fallback

    fun getInt(i: Int): Int = (get(i) as? Number)?.toInt() ?: throw JSONException(" not an int at $i")

    fun getString(i: Int): String = get(i).toString()

    fun getJSONObject(i: Int): JSONObject =
        opt(i) as? JSONObject ?: throw JSONException(" not a JSONObject at $i")

    fun getJSONArray(i: Int): JSONArray =
        opt(i) as? JSONArray ?: throw JSONException(" not a JSONArray at $i")

    fun getBoolean(i: Int): Boolean = get(i) as? Boolean ?: throw JSONException(" not a boolean at $i")

    fun getLong(i: Int): Long = (get(i) as? Number)?.toLong() ?: throw JSONException(" not a long at $i")

    fun getDouble(i: Int): Double = (get(i) as? Number)?.toDouble() ?: throw JSONException(" not a double at $i")

    fun optDouble(i: Int, fallback: Double = Double.NaN): Double =
        (list.getOrNull(i) as? Number)?.toDouble() ?: fallback

    fun optBoolean(i: Int, fallback: Boolean = false): Boolean = (list.getOrNull(i) as? Boolean) ?: fallback

    fun optJSONObject(i: Int): JSONObject? = list.getOrNull(i) as? JSONObject

    fun optJSONArray(i: Int): JSONArray? = list.getOrNull(i) as? JSONArray

    fun put(value: Any?): JSONArray = apply { list.add(JSONObject.wrap(value)) }
    fun put(value: Boolean): JSONArray = apply { list.add(value) }
    fun put(value: Int): JSONArray = apply { list.add(value) }
    fun put(value: Long): JSONArray = apply { list.add(value) }
    fun put(value: Double): JSONArray = apply { list.add(value) }

    override fun toString(): String {
        val sb = StringBuilder()
        write(sb)
        return sb.toString()
    }

    internal fun write(sb: StringBuilder) {
        sb.append('[')
        for (i in list.indices) {
            if (i > 0) sb.append(',')
            JsonWriter.value(list[i], sb)
        }
        sb.append(']')
    }
}

internal object JsonWriter {

    fun value(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is JSONObject -> v.write(sb)
            is JSONArray -> v.write(sb)
            is String -> quote(v, sb)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Double, is Float -> sb.append(numberToString((v as Number).toDouble()))
            is Number -> sb.append(v.toString())
            else -> quote(v.toString(), sb)
        }
    }

    /** 안드로이드 org.json 처럼 정수값 Double 은 ".0" 을 붙이지 않는다. */
    fun numberToString(d: Double): String {
        if (d.isNaN() || d.isInfinite()) throw JSONException(" forbidden numeric value: $d")
        val l = d.toLong()
        if (d == l.toDouble() && d >= -9.007199254740992E15 && d <= 9.007199254740992E15) return l.toString()
        return d.toString()
    }

    fun quote(s: String, sb: StringBuilder) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }
}

internal class JsonParser(private val src: String) {

    private var i = 0

    fun eof(): Boolean = i >= src.length

    fun skipWs() {
        while (i < src.length && (src[i] == ' ' || src[i] == '\n' || src[i] == '\r' || src[i] == '\t')) i++
    }

    fun readValue(): Any? {
        skipWs()
        if (eof()) throw JSONException(" end of input")
        return when (val ch = src[i]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> expect("true").let { true }
            'f' -> expect("false").let { false }
            'n' -> expect("null").let { null }
            else -> if (ch == '-' || ch in '0'..'9') readNumber() else throw JSONException(" unexpected '$ch' at $i")
        }
    }

    private fun expect(lit: String) {
        if (!src.startsWith(lit, i)) throw JSONException(" expected '$lit' at $i")
        i += lit.length
    }

    private fun readObject(): JSONObject {
        i++ // '{'
        val o = JSONObject()
        skipWs()
        if (!eof() && src[i] == '}') { i++; return o }
        while (true) {
            skipWs()
            val name = readString()
            skipWs()
            if (eof() || src[i] != ':') throw JSONException(" expected ':' at $i")
            i++
            val v = readValue()
            o.put(name, v)
            skipWs()
            if (eof()) throw JSONException(" unterminated object")
            when (src[i]) {
                ',' -> i++
                '}' -> { i++; return o }
                else -> throw JSONException(" expected ',' or '}' at $i")
            }
        }
    }

    private fun readArray(): JSONArray {
        i++ // '['
        val a = JSONArray()
        skipWs()
        if (!eof() && src[i] == ']') { i++; return a }
        while (true) {
            a.put(readValue())
            skipWs()
            if (eof()) throw JSONException(" unterminated array")
            when (src[i]) {
                ',' -> i++
                ']' -> { i++; return a }
                else -> throw JSONException(" expected ',' or ']' at $i")
            }
        }
    }

    private fun readString(): String {
        if (eof() || src[i] != '"') throw JSONException(" expected '\"' at $i")
        i++
        val sb = StringBuilder()
        while (true) {
            if (eof()) throw JSONException(" unterminated string")
            val ch = src[i++]
            if (ch == '"') return sb.toString()
            if (ch != '\\') { sb.append(ch); continue }
            if (eof()) throw JSONException(" bad escape")
            when (val e = src[i++]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    if (i + 4 > src.length) throw JSONException(" bad unicode escape")
                    sb.append(src.substring(i, i + 4).toInt(16).toChar())
                    i += 4
                }
                else -> throw JSONException(" bad escape '\\$e'")
            }
        }
    }

    private fun readNumber(): Any {
        val start = i
        if (src[i] == '-') i++
        while (i < src.length && src[i] in '0'..'9') i++
        var isFloat = false
        if (i < src.length && src[i] == '.') {
            isFloat = true
            i++
            while (i < src.length && src[i] in '0'..'9') i++
        }
        if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
            isFloat = true
            i++
            if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
            while (i < src.length && src[i] in '0'..'9') i++
        }
        val text = src.substring(start, i)
        if (isFloat) return text.toDouble()
        val l = text.toLong()
        return if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
    }
}
