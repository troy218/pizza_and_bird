@file:Suppress("unused")

/** tools/preview — android.util.Log 스텁 (표준 출력으로 흘려보낸다). */
package android.util

object Log {
    @JvmStatic
    fun d(tag: String, msg: String): Int { println("D/$tag: $msg"); return 0 }

    @JvmStatic
    fun i(tag: String, msg: String): Int { println("I/$tag: $msg"); return 0 }

    @JvmStatic
    fun w(tag: String, msg: String): Int { println("W/$tag: $msg"); return 0 }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int { println("W/$tag: $msg $tr"); return 0 }

    @JvmStatic
    fun e(tag: String, msg: String): Int { println("E/$tag: $msg"); return 0 }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int { println("E/$tag: $msg $tr"); return 0 }
}
