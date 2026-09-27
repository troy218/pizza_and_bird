@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")

/**
 * tools/preview — R 리소스 ID 스텁 (aapt 대체). res/raw·drawable의 파일 목록 기준 자동 생성.
 * drawable은 Context.drawableRegistry에 이름을 등록해 VectorArtDrawable 렌더링에 쓰인다.
 * (갱신: python3 tools/preview/gen_r_stub.py — res/raw·res/drawable 목록이 바뀌면 다시 돌릴 것)
 */
package com.pizzaandbird.game
object R {

    object raw {
        val amb_birds = 1
        val amb_fire = 2
        val amb_forest = 3
        val amb_hum = 4
        val amb_night = 5
        val amb_rain = 6
        val amb_rain_heavy = 7
        val amb_rain_roof = 8
        val amb_sea = 9
        val amb_wind = 10
        val bgm_home = 11
        val bgm_mountain = 12
        val bgm_sea = 13
        val bgm_title = 14
        val bgm_world = 15
        val sfx_bag_open = 16
        val sfx_bike_bell = 17
        val sfx_bike_brake = 18
        val sfx_bird_chirp1 = 19
        val sfx_bird_chirp2 = 20
        val sfx_bird_flee = 21
        val sfx_book_open = 22
        val sfx_buy = 23
        val sfx_cat_meow1 = 24
        val sfx_cat_meow2 = 25
        val sfx_cat_meow3 = 26
        val sfx_cat_punch = 27
        val sfx_crow = 28
        val sfx_cuckoo1 = 29
        val sfx_cuckoo2 = 30
        val sfx_eat = 31
        val sfx_fail = 32
        val sfx_levelup = 33
        val sfx_notify = 34
        val sfx_owl = 35
        val sfx_reward = 36
        val sfx_shutter = 37
        val sfx_sparkle = 38
        val sfx_step_grass = 39
        val sfx_step_gravel1 = 40
        val sfx_step_gravel2 = 41
        val sfx_step_sand = 42
        val sfx_step_snow = 43
        val sfx_step_stone = 44
        val sfx_step_water = 45
        val sfx_step_wood = 46
        val sfx_success = 47
        val sfx_tap = 48
        val sfx_whoosh = 49
    }

    object drawable {
        private var next = 10000
        private fun art(name: String): Int {
            val id = next++
            android.content.Context.drawableRegistry[id] = name
            return id
        }

        val art_bike_down = art("art_bike_down")
        val art_bike_side = art("art_bike_side")
        val art_bike_up = art("art_bike_up")
        val art_bird_owl = art("art_bird_owl")
        val art_bird_raptor = art("art_bird_raptor")
        val art_bird_songbird = art("art_bird_songbird")
        val art_bird_wader = art("art_bird_wader")
        val art_bird_waterfowl = art("art_bird_waterfowl")
        val art_camera = art("art_camera")
        val art_cat_sit = art("art_cat_sit")
        val art_cat_walk_1 = art("art_cat_walk_1")
        val art_cat_walk_2 = art("art_cat_walk_2")
        val art_clover = art("art_clover")
        val art_decor_bookshelf = art("art_decor_bookshelf")
        val art_decor_cactus = art("art_decor_cactus")
        val art_decor_lamp = art("art_decor_lamp")
        val art_decor_radio = art("art_decor_radio")
        val art_decor_rug = art("art_decor_rug")
        val art_decor_trophy = art("art_decor_trophy")
        val art_house = art("art_house")
        val art_moon = art("art_moon")
        val art_npc_elder = art("art_npc_elder")
        val art_npc_kid = art("art_npc_kid")
        val art_npc_professor = art("art_npc_professor")
        val art_npc_shop = art("art_npc_shop")
        val art_npc_villager = art("art_npc_villager")
        val art_pizza = art("art_pizza")
        val art_player_down_0 = art("art_player_down_0")
        val art_player_down_1 = art("art_player_down_1")
        val art_player_down_2 = art("art_player_down_2")
        val art_player_side_0 = art("art_player_side_0")
        val art_player_side_1 = art("art_player_side_1")
        val art_player_side_2 = art("art_player_side_2")
        val art_player_up_0 = art("art_player_up_0")
        val art_player_up_1 = art("art_player_up_1")
        val art_player_up_2 = art("art_player_up_2")
        val art_sun = art("art_sun")
        val art_tile_bed = art("art_tile_bed")
        val art_tile_bench = art("art_tile_bench")
        val art_tile_bldg_roof = art("art_tile_bldg_roof")
        val art_tile_bldg_wall = art("art_tile_bldg_wall")
        val art_tile_bldg_win_0 = art("art_tile_bldg_win_0")
        val art_tile_bldg_win_1 = art("art_tile_bldg_win_1")
        val art_tile_box = art("art_tile_box")
        val art_tile_decor = art("art_tile_decor")
        val art_tile_floor_0 = art("art_tile_floor_0")
        val art_tile_floor_1 = art("art_tile_floor_1")
        val art_tile_flower_0 = art("art_tile_flower_0")
        val art_tile_flower_1 = art("art_tile_flower_1")
        val art_tile_flower_2 = art("art_tile_flower_2")
        val art_tile_grass_0 = art("art_tile_grass_0")
        val art_tile_grass_1 = art("art_tile_grass_1")
        val art_tile_grass_2 = art("art_tile_grass_2")
        val art_tile_grass_3 = art("art_tile_grass_3")
        val art_tile_house_door = art("art_tile_house_door")
        val art_tile_house_roof = art("art_tile_house_roof")
        val art_tile_house_wall = art("art_tile_house_wall")
        val art_tile_house_win = art("art_tile_house_win")
        val art_tile_lamp = art("art_tile_lamp")
        val art_tile_mountain_0 = art("art_tile_mountain_0")
        val art_tile_mountain_1 = art("art_tile_mountain_1")
        val art_tile_oven_0 = art("art_tile_oven_0")
        val art_tile_oven_1 = art("art_tile_oven_1")
        val art_tile_path_0 = art("art_tile_path_0")
        val art_tile_path_1 = art("art_tile_path_1")
        val art_tile_path_2 = art("art_tile_path_2")
        val art_tile_plaza_0 = art("art_tile_plaza_0")
        val art_tile_plaza_1 = art("art_tile_plaza_1")
        val art_tile_reed_0 = art("art_tile_reed_0")
        val art_tile_reed_1 = art("art_tile_reed_1")
        val art_tile_rock_0 = art("art_tile_rock_0")
        val art_tile_rock_1 = art("art_tile_rock_1")
        val art_tile_sand_0 = art("art_tile_sand_0")
        val art_tile_sand_1 = art("art_tile_sand_1")
        val art_tile_sand_2 = art("art_tile_sand_2")
        val art_tile_sign = art("art_tile_sign")
        val art_tile_tallgrass_0 = art("art_tile_tallgrass_0")
        val art_tile_tallgrass_1 = art("art_tile_tallgrass_1")
        val art_tile_tree_0 = art("art_tile_tree_0")
        val art_tile_tree_1 = art("art_tile_tree_1")
        val art_tile_tunnel = art("art_tile_tunnel")
        val art_tile_wall_in = art("art_tile_wall_in")
        val art_tile_wall_win = art("art_tile_wall_win")
        val art_tile_water_0 = art("art_tile_water_0")
        val art_tile_water_1 = art("art_tile_water_1")
        val art_tile_water_2 = art("art_tile_water_2")
        val art_tile_water_3 = art("art_tile_water_3")
    }
}
