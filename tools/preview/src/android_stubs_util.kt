@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/** tools/preview — android.util 스텁 (프리뷰 파이프라인 전용) */
package android.util

object Xml {
    fun newPullParser(): org.xmlpull.v1.XmlPullParser = org.xmlpull.v1.StaxPullParser()
}
