@file:Suppress("unused")

/**
 * tools/preview — R 클래스 스텁 (프리뷰 파이프라인 전용).
 *
 * Android 빌드에서는 AGP가 res/raw 리소스에서 R 클래스를 생성하지만,
 * 프리뷰는 kotlinc 단독 컴파일이라 이렇게 같은 패키지 스텁을 둔다.
 * 값은 구분만 되면 되므로 순번 상수로 충분하다.
 */
package com.pizzaandbird.game

object R {
    object raw {
        const val amb_birds = 1
        const val amb_fire = 2
        const val amb_hum = 3
        const val amb_wind = 4
        const val bgm_home = 5
        const val bgm_title = 6
        const val bgm_world = 7
        const val sfx_bike_bell = 8
        const val sfx_bike_brake = 9
        const val sfx_bird_chirp1 = 10
        const val sfx_bird_chirp2 = 11
        const val sfx_bird_flee = 12
        const val sfx_buy = 13
        const val sfx_eat = 14
        const val sfx_fail = 15
        const val sfx_notify = 16
        const val sfx_owl = 17
        const val sfx_reward = 18
        const val sfx_shutter = 19
        const val sfx_sparkle = 20
        const val sfx_step_gravel1 = 21
        const val sfx_step_gravel2 = 22
        const val sfx_step_wood = 23
        const val sfx_success = 24
        const val sfx_tap = 25
        const val sfx_whoosh = 26
    }
}
