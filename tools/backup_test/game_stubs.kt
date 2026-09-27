// [P05] JVM 검증용 게임 쪽 스텁 — Context/SharedPreferences/Game 만 가짜로 만든다.
// SaveManager·GameState·Backup 은 **실제 소스를 그대로 컴파일**해서 검증한다.
package com.pizzaandbird.game

import android.content.Context

/** 테스트용 Context — prefs 는 [android.content.FakePrefs] 의 in-memory 저장소를 쓴다. */
class FakeContext : Context()

/** 백업이 "복원 직후 상태를 다시 읽는다"는 계약만 확인하면 되므로 최소 게임 스텁. */
class Game(val context: Context) {
    val state: GameState = SaveManager.load(context)

    /** 실제 Game.reloadState() 와 **같은 리플렉션 필드 복사** — 그 코드 경로를 여기서 검증한다. */
    fun reloadState(): Boolean {
        val fresh = SaveManager.load(context)
        var k: Class<*> = GameState::class.java
        while (k != Any::class.java) {
            for (f in k.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    f.set(state, f.get(fresh))
                } catch (_: Throwable) {
                }
            }
            k = k.superclass ?: Any::class.java
        }
        return state.started
    }
}
