@file:Suppress("unused")

/** tools/preview — org.xmlpull 스텁 (JDK 내장 StAX로 구현) */
package org.xmlpull.v1

import java.io.InputStream
import java.io.Reader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

interface XmlPullParser {
    val eventType: Int

    /** Android 플랫폼 타입과 맞춘다 (non-null String) */
    var name: String

    fun setInput(stream: InputStream?, inputEncoding: String?)
    fun setInput(input: Reader?)
    fun next(): Int
    fun getAttributeValue(namespace: String?, name: String): String?

    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }
}

internal class XmlPullParserImpl : XmlPullParser {
    private var reader: javax.xml.stream.XMLStreamReader? = null

    override var eventType: Int = XmlPullParser.START_DOCUMENT
        private set

    override var name: String = ""
        get() = currentName()

    private fun map(t: Int): Int = when (t) {
        XMLStreamConstants.START_ELEMENT -> XmlPullParser.START_TAG
        XMLStreamConstants.END_ELEMENT -> XmlPullParser.END_TAG
        XMLStreamConstants.END_DOCUMENT -> XmlPullParser.END_DOCUMENT
        else -> XmlPullParser.TEXT
    }

    private fun advance(): Int {
        val r = reader ?: return XmlPullParser.END_DOCUMENT
        eventType = if (!r.hasNext()) {
            XmlPullParser.END_DOCUMENT
        } else {
            r.next()
            map(r.eventType)
        }
        return eventType
    }

    override fun setInput(stream: InputStream?, inputEncoding: String?) {
        val f = XMLInputFactory.newInstance()
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        reader = f.createXMLStreamReader(stream, inputEncoding ?: "UTF-8")
        eventType = XmlPullParser.START_DOCUMENT
    }

    override fun setInput(input: Reader?) {
        val f = XMLInputFactory.newInstance()
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        reader = f.createXMLStreamReader(input)
        eventType = XmlPullParser.START_DOCUMENT
    }

    override fun next(): Int = advance()

    override fun getAttributeValue(namespace: String?, name: String): String? =
        reader?.takeIf { it.eventType == XMLStreamConstants.START_ELEMENT }?.getAttributeValue(namespace, name)

    private fun currentName(): String =
        reader?.takeIf { it.eventType == XMLStreamConstants.START_ELEMENT || it.eventType == XMLStreamConstants.END_ELEMENT }
            ?.localName ?: ""
}
