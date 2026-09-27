@file:Suppress("unused")

/** tools/preview — android.util.Xml 스텁 */
package android.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserImpl

object Xml {
    fun newPullParser(): XmlPullParser = XmlPullParserImpl()
}
