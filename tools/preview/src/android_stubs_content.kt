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
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putStringSet(key: String, value: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }

    /** [P05] 백업 코드가 prefs 전체를 훑는 데 쓴다 (이름 순서 보장 없음). */
    val all: Map<String, *>

    fun edit(): Editor
    fun getString(key: String, def: String?): String?
    fun contains(key: String): Boolean
}

/** [P05] 이름별 저장소 — 프리뷰에서도 세이브/백업이 실제로 읽고 쓰이도록 공유한다. */
object PreviewPrefs {
    val stores = ConcurrentHashMap<String, ConcurrentHashMap<String, Any?>>()

    fun store(name: String): ConcurrentHashMap<String, Any?> =
        stores.getOrPut(name) { ConcurrentHashMap() }
}

/** 메모리 저장 프리퍼런스 (프리뷰용) — 타입을 그대로 보존한다(백업 왕복 검증 가능) */
open class InMemorySharedPreferences(private val name: String = "preview") : SharedPreferences {

    private val map: ConcurrentHashMap<String, Any?> get() = PreviewPrefs.store(name)

    override val all: Map<String, *>
        get() = LinkedHashMap(map)

    override fun edit(): SharedPreferences.Editor = EditorImpl(map)

    override fun getString(key: String, def: String?): String? = map[key] as? String ?: def

    override fun contains(key: String): Boolean = map.containsKey(key)

    private class EditorImpl(
        private val map: ConcurrentHashMap<String, Any?>
    ) : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private val removals = ArrayList<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putStringSet(key: String, value: Set<String>?) =
            apply { pending[key] = value?.let { LinkedHashSet(it) } }

        override fun remove(key: String) = apply { removals.add(key) }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) map.clear()
            for (k in removals) map.remove(k)
            for ((k, v) in pending) if (v == null) map.remove(k) else map[k] = v
            pending.clear(); removals.clear(); clearAll = false
            return true
        }

        override fun apply() {
            commit()
        }
    }
}

// ---------------------------------------------------------------------------
// [P05] 클립보드 / 인텐트 — 백업 화면이 쓰는 android.content API 스텁
// ---------------------------------------------------------------------------

class ClipData(private val label: CharSequence?, private val items: List<Item>) {
    class Item(private val text: CharSequence?) {
        fun coerceToText(context: Context): CharSequence = text ?: ""
    }

    val itemCount: Int get() = items.size

    fun getItemAt(index: Int): Item? = items.getOrNull(index)

    companion object {
        @JvmStatic
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData =
            ClipData(label, listOf(Item(text)))
    }
}

/** 프리뷰 클립보드 — [PreviewClipboard.text] 에 값을 넣으면 "붙여넣기"가 된다. */
object PreviewClipboard {
    var text: String? = null
}

class ClipboardManager {
    fun setPrimaryClip(clip: ClipData) {
        PreviewClipboard.text = clip.getItemAt(0)?.coerceToText(Context())?.toString()
    }

    val primaryClip: ClipData?
        get() = PreviewClipboard.text?.let { ClipData.newPlainText("preview", it) }
}

class Intent(val action: String? = null) {
    var type: String? = null
    val extras = LinkedHashMap<String, Any?>()
    var flags: Int = 0

    fun putExtra(name: String, value: Any?): Intent = apply { extras[name] = value }
    fun addFlags(f: Int): Intent = apply { flags = flags or f }

    companion object {
        const val ACTION_SEND = "android.intent.action.SEND"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000

        @JvmStatic
        fun createChooser(target: Intent, title: CharSequence?): Intent = Intent("chooser")
    }
}

open class Context {
    companion object {
        const val VIBRATOR_SERVICE = "vibrator"
        const val CLIPBOARD_SERVICE = "clipboard"
        const val MODE_PRIVATE = 0

        /** 프리뷰용 리소스 id → drawable 이름 (r_stub.kt 가 등록한다) */
        val drawableRegistry = java.util.concurrent.ConcurrentHashMap<Int, String>()
    }

    open fun getDrawable(id: Int): android.graphics.drawable.Drawable? {
        val name = drawableRegistry[id] ?: return null
        return android.graphics.drawable.VectorArtDrawable.load(name)
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = InMemorySharedPreferences(name)

    open fun getSystemService(name: String): Any? =
        if (name == CLIPBOARD_SERVICE) ClipboardManager() else null

    open fun startActivity(intent: Intent) {}

    open val resources: Resources = Resources()

    open val assets: android.content.res.AssetManager = android.content.res.AssetManager()
}
