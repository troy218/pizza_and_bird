package com.pizzaandbird.game

import java.util.Random

/**
 * Lightweight, species-trait-based field movement. The profiles are game-feel presets,
 * not claims that every individual of a family behaves identically.
 */
enum class BirdMovementStyle {
    SONG_BIRD, WATERFOWL, WADER, RAPTOR, AERIAL, OWL, GROUNDFORAGER
}

data class BirdMovementProfile(
    val style: BirdMovementStyle,
    val restMin: Float,
    val restMax: Float,
    val stepTiles: Int,
    val stepSeconds: Float,
    val lift: Float,
    val fleeSpeed: Float,
    val fleeLift: Float,
    val horizontalBias: Float,
    val leaveAfter: Float
) {
    fun nextRest(random: Random): Float = restMin + random.nextFloat() * (restMax - restMin)
}

object BirdMovement {
    private val swimmers = setOf("오리과", "논병아리과", "아비과", "바다오리과", "가마우지과")
    private val waders = setOf(
        "백로과", "저어새과", "황새과", "도요과", "물떼새과",
        "검은머리물떼새과", "장다리물떼새과", "호사도요과", "물꿩과"
    )
    private val seabirds = setOf(
        "갈매기과", "도둑갈매기과", "알바트로스과", "바다제비과", "슴새과",
        "군함조과", "얼가니새과"
    )

    fun profile(def: BirdDef): BirdMovementProfile {
        val style = when {
            def.orderName in setOf("수리목", "매목") -> BirdMovementStyle.RAPTOR
            def.orderName in setOf("올빼미목", "쏙독새목") -> BirdMovementStyle.OWL
            def.orderName == "칼새목" || def.familyName == "제비과" || def.familyName in seabirds -> BirdMovementStyle.AERIAL
            def.familyName in swimmers || def.name == "물닭" -> BirdMovementStyle.WATERFOWL
            def.familyName in waders -> BirdMovementStyle.WADER
            def.orderName in setOf("닭목", "사막꿩목", "느시목") || def.familyName == "두루미과" || def.familyName == "뜸부기과" ->
                BirdMovementStyle.GROUNDFORAGER
            else -> BirdMovementStyle.SONG_BIRD
        }
        return when (style) {
            // Small songbirds: frequent, short hops followed by quick pecks.
            BirdMovementStyle.SONG_BIRD -> BirdMovementProfile(style, 0.55f, 2.0f, 1, 0.20f, 5f, 88f, 36f, 0.7f, 1.45f)
            // Ducks and divers drift low across suitable water, pausing for longer spells.
            BirdMovementStyle.WATERFOWL -> BirdMovementProfile(style, 2.4f, 5.4f, 1, 0.62f, 1.2f, 70f, 12f, 1.35f, 1.7f)
            // Waders take measured, deliberate steps and often stop to feed.
            BirdMovementStyle.WADER -> BirdMovementProfile(style, 1.7f, 4.2f, 1, 0.48f, 2f, 86f, 22f, 0.95f, 1.6f)
            // Raptors mostly watch from a perch, then make an occasional longer glide.
            BirdMovementStyle.RAPTOR -> BirdMovementProfile(style, 4.5f, 9.5f, 2, 0.72f, 8f, 122f, 62f, 0.8f, 1.65f)
            // Swallows and other aerial specialists make quick, looping darts between perches.
            BirdMovementStyle.AERIAL -> BirdMovementProfile(style, 0.35f, 1.0f, 2, 0.24f, 11f, 142f, 86f, 0.85f, 1.5f)
            // Owls favor long, quiet perches with occasional small repositioning.
            BirdMovementStyle.OWL -> BirdMovementProfile(style, 7f, 14f, 1, 0.52f, 1.5f, 76f, 31f, 0.7f, 1.55f)
            // Pheasants, rails and cranes walk/waddle rather than making constant little hops.
            BirdMovementStyle.GROUNDFORAGER -> BirdMovementProfile(style, 1.5f, 3.8f, 1, 0.38f, 2.5f, 78f, 23f, 0.8f, 1.55f)
        }
    }
}
