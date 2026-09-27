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

    /** res/drawable 의 벡터 아트 (프리뷰 스텁 — 실제 R은 Android 빌드가 생성) */
    object drawable {
                const val art_bike_down = 1000
        const val art_bike_side = 1001
        const val art_bike_up = 1002
        const val art_bird_owl = 1003
        const val art_bird_raptor = 1004
        const val art_bird_songbird = 1005
        const val art_bird_wader = 1006
        const val art_bird_waterfowl = 1007
        const val art_camera = 1008
        const val art_cat_sit = 1009
        const val art_cat_walk_1 = 1010
        const val art_cat_walk_2 = 1011
        const val art_clover = 1012
        const val art_decor_bookshelf = 1013
        const val art_decor_cactus = 1014
        const val art_decor_lamp = 1015
        const val art_decor_radio = 1016
        const val art_decor_rug = 1017
        const val art_decor_trophy = 1018
        const val art_house = 1019
        const val art_moon = 1020
        const val art_npc_elder = 1021
        const val art_npc_kid = 1022
        const val art_npc_professor = 1023
        const val art_npc_shop = 1024
        const val art_npc_villager = 1025
        const val art_pizza = 1026
        const val art_player_down_0 = 1027
        const val art_player_down_1 = 1028
        const val art_player_down_2 = 1029
        const val art_player_side_0 = 1030
        const val art_player_side_1 = 1031
        const val art_player_side_2 = 1032
        const val art_player_up_0 = 1033
        const val art_player_up_1 = 1034
        const val art_player_up_2 = 1035
        const val art_sun = 1036
        const val art_tile_bed = 1037
        const val art_tile_bench = 1038
        const val art_tile_bldg_roof = 1039
        const val art_tile_bldg_wall = 1040
        const val art_tile_bldg_win_0 = 1041
        const val art_tile_bldg_win_1 = 1042
        const val art_tile_box = 1043
        const val art_tile_decor = 1044
        const val art_tile_floor_0 = 1045
        const val art_tile_floor_1 = 1046
        const val art_tile_flower_0 = 1047
        const val art_tile_flower_1 = 1048
        const val art_tile_flower_2 = 1049
        const val art_tile_grass_0 = 1050
        const val art_tile_grass_1 = 1051
        const val art_tile_grass_2 = 1052
        const val art_tile_grass_3 = 1053
        const val art_tile_house_door = 1054
        const val art_tile_house_roof = 1055
        const val art_tile_house_wall = 1056
        const val art_tile_house_win = 1057
        const val art_tile_lamp = 1058
        const val art_tile_mountain_0 = 1059
        const val art_tile_mountain_1 = 1060
        const val art_tile_oven_0 = 1061
        const val art_tile_oven_1 = 1062
        const val art_tile_path_0 = 1063
        const val art_tile_path_1 = 1064
        const val art_tile_path_2 = 1065
        const val art_tile_plaza_0 = 1066
        const val art_tile_plaza_1 = 1067
        const val art_tile_reed_0 = 1068
        const val art_tile_reed_1 = 1069
        const val art_tile_rock_0 = 1070
        const val art_tile_rock_1 = 1071
        const val art_tile_sand_0 = 1072
        const val art_tile_sand_1 = 1073
        const val art_tile_sand_2 = 1074
        const val art_tile_sign = 1075
        const val art_tile_tallgrass_0 = 1076
        const val art_tile_tallgrass_1 = 1077
        const val art_tile_tree_0 = 1078
        const val art_tile_tree_1 = 1079
        const val art_tile_tunnel = 1080
        const val art_tile_wall_in = 1081
        const val art_tile_wall_win = 1082
        const val art_tile_water_0 = 1083
        const val art_tile_water_1 = 1084
        const val art_tile_water_2 = 1085
        const val art_tile_water_3 = 1086

        val byId: Map<Int, String> = mapOf(1000 to "art_bike_down", 1001 to "art_bike_side", 1002 to "art_bike_up", 1003 to "art_bird_owl", 1004 to "art_bird_raptor", 1005 to "art_bird_songbird", 1006 to "art_bird_wader", 1007 to "art_bird_waterfowl", 1008 to "art_camera", 1009 to "art_cat_sit", 1010 to "art_cat_walk_1", 1011 to "art_cat_walk_2", 1012 to "art_clover", 1013 to "art_decor_bookshelf", 1014 to "art_decor_cactus", 1015 to "art_decor_lamp", 1016 to "art_decor_radio", 1017 to "art_decor_rug", 1018 to "art_decor_trophy", 1019 to "art_house", 1020 to "art_moon", 1021 to "art_npc_elder", 1022 to "art_npc_kid", 1023 to "art_npc_professor", 1024 to "art_npc_shop", 1025 to "art_npc_villager", 1026 to "art_pizza", 1027 to "art_player_down_0", 1028 to "art_player_down_1", 1029 to "art_player_down_2", 1030 to "art_player_side_0", 1031 to "art_player_side_1", 1032 to "art_player_side_2", 1033 to "art_player_up_0", 1034 to "art_player_up_1", 1035 to "art_player_up_2", 1036 to "art_sun", 1037 to "art_tile_bed", 1038 to "art_tile_bench", 1039 to "art_tile_bldg_roof", 1040 to "art_tile_bldg_wall", 1041 to "art_tile_bldg_win_0", 1042 to "art_tile_bldg_win_1", 1043 to "art_tile_box", 1044 to "art_tile_decor", 1045 to "art_tile_floor_0", 1046 to "art_tile_floor_1", 1047 to "art_tile_flower_0", 1048 to "art_tile_flower_1", 1049 to "art_tile_flower_2", 1050 to "art_tile_grass_0", 1051 to "art_tile_grass_1", 1052 to "art_tile_grass_2", 1053 to "art_tile_grass_3", 1054 to "art_tile_house_door", 1055 to "art_tile_house_roof", 1056 to "art_tile_house_wall", 1057 to "art_tile_house_win", 1058 to "art_tile_lamp", 1059 to "art_tile_mountain_0", 1060 to "art_tile_mountain_1", 1061 to "art_tile_oven_0", 1062 to "art_tile_oven_1", 1063 to "art_tile_path_0", 1064 to "art_tile_path_1", 1065 to "art_tile_path_2", 1066 to "art_tile_plaza_0", 1067 to "art_tile_plaza_1", 1068 to "art_tile_reed_0", 1069 to "art_tile_reed_1", 1070 to "art_tile_rock_0", 1071 to "art_tile_rock_1", 1072 to "art_tile_sand_0", 1073 to "art_tile_sand_1", 1074 to "art_tile_sand_2", 1075 to "art_tile_sign", 1076 to "art_tile_tallgrass_0", 1077 to "art_tile_tallgrass_1", 1078 to "art_tile_tree_0", 1079 to "art_tile_tree_1", 1080 to "art_tile_tunnel", 1081 to "art_tile_wall_in", 1082 to "art_tile_wall_win", 1083 to "art_tile_water_0", 1084 to "art_tile_water_1", 1085 to "art_tile_water_2", 1086 to "art_tile_water_3")
    }
}
