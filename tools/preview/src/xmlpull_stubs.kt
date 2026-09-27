@file:Suppress("unused")

/**
 * tools/preview — org.xmlpull.v1 최소 구현 (SVG 파싱용).
 * Xml.newPullParser() 가 이 클래스의 인스턴스를 돌려준다.
 */
package org.xmlpull.v1

import java.io.InputStream

interface XmlPullParser {
    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }

    val eventType: Int
    val name: String
    fun setInput(stream: InputStream, encoding: String?)
    fun getAttributeValue(namespace: String?, name: String?): String?
    fun next(): Int
}

/** 아주 단순한 XML pull 파서 — 시작 태그/속성만 정확하면 된다 (SVG 도형용). */
class MiniXmlPullParser : XmlPullParser {
    private data class Tok(val kind: Int, val name: String?, val attrs: Map<String, String>)

    private var toks: List<Tok> = emptyList()
    private var idx = 0
    private var attrs: Map<String, String> = emptyMap()

    override var eventType: Int = XmlPullParser.START_DOCUMENT
        private set
    override var name: String = ""
        private set

    override fun setInput(stream: InputStream, encoding: String?) {
        val text = stream.readBytes().toString(Charsets.UTF_8)
        val body = text
            .replace(Regex("<!--.*?-->"), "")
            .replace(Regex("<\\?[^>]*\\?>"), "")
        val tag = Regex("""<(/?)([A-Za-z_][\\w.:-]*)((?:\\s+[\\w.:-]+\\s*=\\s*"[^"]*")*)\\s*(/?)>""")
        val out = ArrayList<Tok>()
        for (m in tag.findAll(body)) {
            val closing = m.groupValues[1] == "/"
            val tagType = m.groupValues[2]
            val attrText = m.groupValues[3]
            val selfClose = m.groupValues[4] == "/"
            if (closing) {
                out += Tok(XmlPullParser.END_TAG, tagType, emptyMap())
            } else {
                val map = LinkedHashMap<String, String>()
                for (a in Regex("""([\\w.:-]+)\\s*=\\s*"([^"]*)"""").findAll(attrText)) {
                    map[a.groupValues[1]] = a.groupValues[2]
                        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
                }
                out += Tok(XmlPullParser.START_TAG, tagType, map)
                if (selfClose) out += Tok(XmlPullParser.END_TAG, tagType, emptyMap())
            }
        }
        out += Tok(XmlPullParser.END_DOCUMENT, null, emptyMap())
        toks = out
        idx = -1
    }

    override fun getAttributeValue(namespace: String?, name: String?): String? = attrs[name ?: ""]

    override fun next(): Int {
        idx++
        val t = toks.getOrNull(idx) ?: Tok(XmlPullParser.END_DOCUMENT, null, emptyMap())
        eventType = t.kind
        name = t.name ?: ""
        attrs = t.attrs
        return eventType
    }
}
