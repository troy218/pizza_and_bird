package com.pizzaandbird.game

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface

/**
 * 힐링 게임 타이포그래피 — 게임의 생명은 글꼴! 🍕
 *
 * assets/fonts/ 에 번들된 OFL 라이선스 글꼴을 게임 전체 분위기에 맞춰 쓴다.
 * (주아 · 고운 돋움 · 고운 바탕 · 가경 — 게임이 쓰는 글자만 남긴 서브셋이며,
 *  대사를 추가한 뒤에는 tools/fonts/subset_fonts.py 로 재생성하면 된다.)
 *
 * 역할 배분:
 * - round    : 주아 — 동글동글 따뜻한 디스플레이 글꼴 (제목/버튼/배너/ HUD/지도 라벨)
 * - body     : 고운 돋움 — 단정하고 부드러운 본문 (대화/목록/설명)
 * - serif    : 고운 바탕 — 종이 감성의 세리프 (나침반 방위/낡은 지도 좌표)
 * - hand     : 가경 — 손글씨 (폴라로이드 사진 캡션)
 * - handBold : 가경 굵음 — 손글씨 강조 (탐조 메모지 지역명)
 *
 * 글꼴을 읽지 못해도 시스템 기본 글꼴로 조용히 대체되어 플레이에는 지장이 없다.
 */
object Fonts {

    private var assets: AssetManager? = null

    /** MainActivity에서 Game 객체가 만들어지기 전에 한 번 호출한다. */
    fun init(context: Context) {
        if (assets == null) {
            assets = context.applicationContext.assets
            // 첫 화면에서 버벅이지 않도록 미리 읽어 둔다
            round; body; serif; hand; handBold
        }
    }

    private fun load(path: String, fallback: Typeface): Typeface {
        val am = assets ?: return fallback
        return try {
            Typeface.createFromAsset(am, path)
        } catch (e: Exception) {
            fallback
        }
    }

    /** 동글동글 디스플레이 — 주아 */
    val round: Typeface by lazy { load("fonts/jua.ttf", Typeface.DEFAULT) }

    /** 단정한 본문 — 고운 돋움 */
    val body: Typeface by lazy { load("fonts/gowun_dodum.ttf", Typeface.DEFAULT) }

    /** 종이 감성 세리프 — 고운 바탕 */
    val serif: Typeface by lazy { load("fonts/gowun_batang.ttf", Typeface.SERIF) }

    /** 손글씨 — 가경 */
    val hand: Typeface by lazy { load("fonts/gaegu.ttf", Typeface.DEFAULT) }

    /** 손글씨 굵음 — 가경 Bold */
    val handBold: Typeface by lazy { load("fonts/gaegu_bold.ttf", Typeface.DEFAULT_BOLD) }
}
