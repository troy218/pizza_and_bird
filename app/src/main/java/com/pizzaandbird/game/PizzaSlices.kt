package com.pizzaandbird.game

import android.graphics.Canvas
import android.graphics.RectF

/**
 * 피자 "한 조각" 일러스트 — `assets/pizza_slices/pizza_slice_<id>.svg` (피자 20종).
 *
 * ⚠️ `assets/ui/` 가 아닌 별도 폴더다 — [SvgIllustrations.preloadUiIcons] 가 `ui/` 아래를
 * 부팅 때 전부 래스터화하므로, 128px 20장이 부팅마다 실려 올라간다. 이 조각들은
 * **첫 사용 때 한 장씩** 만들어지고 그 뒤로 캐시된다 (가방·굽기 화면에서만 쓰인다).
 *
 * 통째 피자 아이콘([Assets.pizzaArts])과 짝을 이루는 **잘린 조각 하나** 그림이다.
 * `tools/gen_pizza_slices.py`가 [Pizzas.ALL]을 훑어 피자마다 한 장씩 만들어 두므로
 * 도우·소스·토핑 색이 그 피자의 실제 팔레트(`PizzaDef.baseColor / topColorA / topColorB`)와
 * 항상 같다 — 도감·가방·굽기 화면에서 같은 피자가 같은 색으로 보인다.
 *
 * 계열 차이도 그림에 들어 있다: **화덕피자**는 얇고 그을음이 있는 러스틱 크러스트 +
 * 큼직한 토핑, **일반 피자**는 도톰한 황금 크러스트.
 *
 * "한 판"보다 "한 조각"이 어울리는 자리(가방 목록 · 굽기 메뉴 · 굽는 중 화면)에서 쓴다.
 * SVG는 정사각 viewBox이므로 [drawFit] 처럼 정사각으로 잡아 주면 비율이 어긋나지 않는다.
 */
object PizzaSlices {

    /** 피자 id → SVG 경로 (id = [Pizzas.ALL] 인덱스 0..19) */
    fun asset(pizzaId: Int): String =
        "pizza_slices/pizza_slice_${pizzaId.coerceIn(0, Pizzas.ALL.size - 1)}.svg"

    /** 지정한 사각형에 꽉 채워 그린다. */
    fun draw(c: Canvas, game: Game, pizzaId: Int, bounds: RectF) {
        game.illustrations.draw(c, asset(pizzaId), bounds)
    }

    /** 중심(cx, cy)에 한 변이 size 인 정사각형으로 그린다. */
    fun drawAt(c: Canvas, game: Game, pizzaId: Int, cx: Float, cy: Float, size: Float) {
        if (size <= 0f) return
        val h = size / 2f
        game.illustrations.draw(c, asset(pizzaId), RectF(cx - h, cy - h, cx + h, cy + h))
    }

    /** 중심(cx, cy)에 maxW × maxH 안에 들어가게 (정사각 비율 유지) 그린다. */
    fun drawFit(c: Canvas, game: Game, pizzaId: Int, cx: Float, cy: Float, maxW: Float, maxH: Float) {
        drawAt(c, game, pizzaId, cx, cy, minOf(maxW, maxH))
    }
}
