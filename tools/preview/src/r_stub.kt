@file:Suppress("unused")

/**
 * tools/preview — 리소스 R 스텁.
 * 게임 코드의 R.raw.* 참조를 JVM 프리뷰 컴파일에서 해결한다.
 * (실제 R 클래스는 Android 빌드에서 생성됨. 새 raw 리소스를 추가하면 여기도 추가)
 */
package com.pizzaandbird.game

object R {
    object raw {
        const val bgm_title = 1
        const val bgm_home = 1
        const val bgm_world = 1
        const val amb_birds = 1
        const val amb_fire = 1
        const val amb_hum = 1
        const val amb_wind = 1
        const val sfx_tap = 1
        const val sfx_bike_bell = 1
        const val sfx_bike_brake = 1
        const val sfx_bird_chirp1 = 1
        const val sfx_bird_chirp2 = 1
        const val sfx_bird_flee = 1
        const val sfx_buy = 1
        const val sfx_eat = 1
        const val sfx_fail = 1
        const val sfx_notify = 1
        const val sfx_owl = 1
        const val sfx_reward = 1
        const val sfx_shutter = 1
        const val sfx_sparkle = 1
        const val sfx_step_gravel1 = 1
        const val sfx_step_gravel2 = 1
        const val sfx_step_wood = 1
        const val sfx_success = 1
        const val sfx_whoosh = 1
    }
}
