@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — android.content / android.os / android.view 스텁.
 * 프리뷰 파이프라인 전용이며 Android 빌드(app/)에는 포함되지 않는다.
 */
package android.content

import java.util.concurrent.ConcurrentHashMap

open class DisplayMetrics {
    var density: Float = 1f
    var widthPixels: Int = 0
    var heightPixels: Int = 0
}

open class Resources {
    open val displayMetrics: DisplayMetrics = DisplayMetrics()
}

interface SharedPreferences {
    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
    }

    fun edit(): Editor
    fun getString(key: String, def: String?): String?
    fun contains(key: String): Boolean
}

/** 메모리 저장 프리퍼런스 (프리뷰용) */
open class InMemorySharedPreferences : SharedPreferences {
    private val map = ConcurrentHashMap<String, String>()

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String): SharedPreferences.Editor {
            map[key] = value; return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            map[key] = value.toString(); return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            map[key] = value.toString(); return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            map.remove(key); return this
        }

        override fun clear(): SharedPreferences.Editor {
            map.clear(); return this
        }

        override fun apply() {}
    }

    override fun getString(key: String, def: String?): String? = map[key] ?: def
    override fun contains(key: String): Boolean = map.containsKey(key)
}

open class Context {
    companion object {
        const val VIBRATOR_SERVICE = "vibrator"
        const val MODE_PRIVATE = 0
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = InMemorySharedPreferences()

    open fun getSystemService(name: String): Any? = null

    open val resources: Resources = Resources()

    open val assets: android.content.res.AssetManager = android.content.res.AssetManager()
}
