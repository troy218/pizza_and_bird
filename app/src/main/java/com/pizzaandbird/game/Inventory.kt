package com.pizzaandbird.game

/**
 * 듀랑고식 가방 인벤토리 — 물건별 수량 표시 + 마비노기식 장비 칸.
 *
 * 기존 피자/카메라 장비 외에 일반 아이템을 담는다.
 * 세이브는 GameState.inventory (id->수량) 에 저장.
 */

enum class ItemCategory { MATERIAL, CONSUMABLE, EQUIP, QUEST, ETC }

data class ItemDef(
    val id: String,
    val name: String,
    val icon: String,          // UiKit 아이콘 키
    val category: ItemCategory,
    val desc: String,
    val stackMax: Int = 99,
    val equipSlot: EquipSlot? = null   // 장비면 어느 슬롯에 끼는지
)

enum class EquipSlot(val label: String, val icon: String) {
    CHARM("행운 부적", "star"),
    CAMERA("카메라", "camera"),
    LENS("렌즈", "lens"),
    BAG("가방 장식", "backpack"),
    BIKE_ACC("자전거 악세", "bike")
}

object Items {
    val ALL = listOf(
        ItemDef("flour", "밀가루", "leaf", ItemCategory.MATERIAL, "피자를 굽는 기본 재료", 99),
        ItemDef("cheese", "치즈", "pizza", ItemCategory.MATERIAL, "모짜렐라 치즈", 99),
        ItemDef("tomato", "토마토", "pizza", ItemCategory.MATERIAL, "신선한 토마토", 99),
        ItemDef("mushroom", "버섯", "leaf", ItemCategory.MATERIAL, "향긋한 버섯", 99),
        ItemDef("basil", "바질", "leaf", ItemCategory.MATERIAL, "향긋한 바질", 99),
        ItemDef("water_bottle", "물병", "coffee", ItemCategory.CONSUMABLE, "갈증 해소용 물병", 20),
        ItemDef("energy_bar", "에너지바", "box", ItemCategory.CONSUMABLE, "배고픔을 조금 채워요", 20),
        ItemDef("film", "필름", "camera", ItemCategory.MATERIAL, "사진 필름", 99),
        ItemDef("feather", "깃털", "bird", ItemCategory.MATERIAL, "새 깃털 — 수집품", 99),
        ItemDef("map_piece", "지도 조각", "map", ItemCategory.QUEST, "오래된 지도 조각", 10),
        ItemDef("coin_pouch", "동전 주머니", "coin", ItemCategory.ETC, "동전이 든 주머니", 10),
        // 장비형 악세서리 (마비노기식)
        ItemDef("charm_lucky", "행운의 부적", "star", ItemCategory.EQUIP, "행운 +5", 1, EquipSlot.CHARM),
        ItemDef("charm_wind", "바람의 부적", "wind", ItemCategory.EQUIP, "이동속도 +3%", 1, EquipSlot.CHARM),
        ItemDef("camera_strap", "카메라 스트랩", "camera", ItemCategory.EQUIP, "무게 부담 완화", 1, EquipSlot.CAMERA),
        ItemDef("backpack_charm", "가방 참", "backpack", ItemCategory.EQUIP, "피자 소지 +2", 1, EquipSlot.BAG)
    )

    val byId = ALL.associateBy { it.id }

    fun of(id: String): ItemDef? = byId[id]
}
