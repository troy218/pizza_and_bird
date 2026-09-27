// [P05] 백업 코드 로직을 **안드로이드 없이 JVM에서** 검증하기 위한 스텁.
// 실제 소스(Backup.kt / GameState.kt / Weather.kt / Data.kt / Cameras.kt / Quests.kt)와 함께
// kotlinc 로 컴파일해서 tools/backup_test/run.sh 로 돌린다.
// → 에뮬레이터 없이 export→초기화→import 왕복, 손상/구버전 코드 거부를 검증한다.
package android.content

interface SharedPreferences {
    val all: Map<String, *>
    fun getString(key: String, def: String?): String?
    fun contains(key: String): Boolean

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putStringSet(key: String, value: Set<String>?): Editor
        fun remove(key: String): Editor
        fun commit(): Boolean
        fun apply()
    }

    fun edit(): Editor
}

/** assets.open() — 테스트에서는 쓰지 않지만 컴파일을 위해 스텁만 둔다. */
class AssetManager {
    fun open(name: String): java.io.InputStream = throw UnsupportedOperationException("stub assets: $name")
}

open class Context {
    val assets: AssetManager = AssetManager()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = FakePrefs.newPrefs(name)

    companion object {
        const val MODE_PRIVATE = 0
    }
}

/** 스텁 prefs 저장소 — 테스트가 내용을 직접 조작할 수 있게 here 에 모아 둔다. */
object FakePrefs {
    val stores = LinkedHashMap<String, LinkedHashMap<String, Any?>>()

    fun store(name: String): LinkedHashMap<String, Any?> = stores.getOrPut(name) { LinkedHashMap() }

    fun newPrefs(name: String): SharedPreferences = FakePrefsImpl(name)
}

class FakePrefsImpl(private val name: String) : SharedPreferences {

    private val map: LinkedHashMap<String, Any?> get() = FakePrefs.store(name)

    override val all: Map<String, *>
        get() = LinkedHashMap(map)

    override fun getString(key: String, def: String?): String? = map[key] as? String ?: def

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl(map)

    private class EditorImpl(private val map: LinkedHashMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private val removals = ArrayList<String>()

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putStringSet(key: String, value: Set<String>?) =
            apply { pending[key] = value?.let { LinkedHashSet(it) } }

        override fun remove(key: String) = apply { removals.add(key) }

        override fun commit(): Boolean {
            for (k in removals) map.remove(k)
            for ((k, v) in pending) if (v == null) map.remove(k) else map[k] = v
            pending.clear()
            removals.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
