@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — android.util.Xml / org.xmlpull.v1 스텁 (프리뷰 파이프라인 전용).
 * JDK에 내장된 StAX(XMLStreamReader) 로 실제 파싱을 수행해
 * SvgIllustrations가 assets 의 SVG를 미리보기에서도 렌더링할 수 있게 한다.
 */
package org.xmlpull.v1

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

interface XmlPullParser {
    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }

    val eventType: Int
    val name: String?
    fun setInput(inputStream: InputStream?, inputEncoding: String?)
    fun getAttributeValue(namespace: String?, name: String): String?
    fun next(): Int
}

/** StAX 기반 풀 파서 구현 */
class StaxPullParser : XmlPullParser {

    private var reader: XMLStreamReader? = null
    private var current: Int = XmlPullParser.START_DOCUMENT

    override val eventType: Int get() = current

    override val name: String?
        get() = try {
            reader?.localName
        } catch (_: Exception) {
            null
        }

    override fun setInput(inputStream: InputStream?, inputEncoding: String?) {
        val factory = XMLInputFactory.newInstance()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        reader = factory.createXMLStreamReader(inputStream)
        current = XmlPullParser.START_DOCUMENT
    }

    override fun getAttributeValue(namespace: String?, name: String): String? = try {
        reader?.getAttributeValue(namespace, name)
    } catch (_: Exception) {
        null
    }

    override fun next(): Int {
        val r = reader ?: return XmlPullParser.END_DOCUMENT
        current = try {
            when (val ev = r.next()) {
                XMLStreamConstants.START_ELEMENT -> XmlPullParser.START_TAG
                XMLStreamConstants.END_ELEMENT -> XmlPullParser.END_TAG
                XMLStreamConstants.END_DOCUMENT -> XmlPullParser.END_DOCUMENT
                else -> XmlPullParser.TEXT
            }
        } catch (_: Exception) {
            XmlPullParser.END_DOCUMENT
        }
        return current
    }
}
