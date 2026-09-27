package com.pizzaandbird.preview

import android.content.Context
import android.content.DisplayMetrics
import android.content.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import com.pizzaandbird.game.*
import kotlin.math.abs

/**
 * 실제 게임 Input/Game/Scene 코드에 터치를 전달하는 헤드리스 회귀 테스트.
 * 프리뷰 스텁과 게임 소스를 함께 컴파일한 뒤 이 main을 실행한다.
 * README의 `입력 회귀 테스트` 명령 참고.
 */
object InputSmoke {
    private class TestContext : Context() {
        override val resources: Resources = object : Resources() {
            override val displayMetrics = DisplayMetrics().apply { density = 2f }
        }
    }

    private fun frame(g: Game) = g.update(1f / 60f)
    private fun render(g: Game) {
        g.render(Canvas(Bitmap.createBitmap(g.screenW, g.screenH, Bitmap.Config.ARGB_8888)))
    }
    private fun tap(g: Game, x: Float, y: Float) {
        val point = listOf(Triple(0, x, y))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_DOWN, pointers = point))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_UP, pointers = point))
        frame(g)
    }
    private fun tapButton(g: Game, name: String) {
        val r = g.scene.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(g.scene) as RectF
        check(r.width() > 0f && r.height() > 0f)
        tap(g, r.centerX(), r.centerY())
    }
    private fun advanceFade(g: Game) { repeat(40) { frame(g) }; render(g) }
    private fun near(a: Float, b: Float) = check(abs(a - b) < 0.01f) { "$a != $b" }

    @JvmStatic
    fun main(args: Array<String>) {
        val g = Game(TestContext())
        // 가로가 더 긴 기기의 양쪽 레터박스까지 검증한다 (가상 1200x540 -> 2400x1080).
        g.onSurfaceChanged(2400, 1080)
        repeat(60) { frame(g) } // 타이틀 등장 애니메이션 완료
        render(g)
        tapButton(g, "startRect")
        advanceFade(g)
        check(g.scene is CharacterSelectScene)
        tap(g, g.viewOffX + 645f * 2f, 310f * 2f) // 가상 화면의 여자 카드
        check(g.state.gender == "female")
        tapButton(g, "nextRect") // 실제 화면의 계속하기
        advanceFade(g)
        check(g.scene is RegionSelectScene)
        tapButton(g, "backRect") // 뒤로 가서 캐릭터를 다시 고를 수 있다
        advanceFade(g)
        check(g.scene is CharacterSelectScene)
        render(g)
        tapButton(g, "nextRect")
        advanceFade(g)
        check(g.scene is RegionSelectScene)
        tapButton(g, "confirmRect") // 서울 시작 버튼
        advanceFade(g)
        check(g.scene is WorldScene && g.state.started && g.state.gender == "female")

        val world = g.scene as WorldScene
        val camera = world.cameraOffset()
        // 화면 한가운데는 카메라 줌과 무관하게 카메라 오프셋만 반영된다
        // (가상 1200x540 -> 월드 300x135, WORLD_SCALE=2).
        val center = PointF(g.viewOffX + g.virtW * g.viewScale / 2f, g.screenH / 2f)
        near(g.screenToWorld(center).x, camera.x + 300f)
        near(g.screenToWorld(center).y, camera.y + 135f)

        // 모달이 떠 있으면 HUD A 버튼 위의 터치도 모달에만 전달되어야 한다.
        var modalTap: PointF? = null
        var pressedUnderlyingA = false
        val modal = object : Overlay(world) {
            override fun handleInput(input: Input) {
                modalTap = input.consumeTapScreen()
                pressedUnderlyingA = input.justA
            }
        }
        world.openOverlay(modal)
        tap(g, g.hud.mainCx, g.hud.mainCy) // 첫 프레임부터 모달이 HUD 입력을 가로챈다
        check(modalTap != null && !pressedUnderlyingA)
        world.closeOverlay()

        val mapOverlay = MapOverlay(world)
        world.openOverlay(mapOverlay)
        repeat(8) { frame(g) }
        render(g)
        val canceled = listOf(Triple(0, 2286f, 46f)) // 지도의 닫기 버튼 위
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_DOWN, pointers = canceled))
        g.input.onTouchEvent(MotionEvent(MotionEvent.ACTION_CANCEL, pointers = canceled))
        frame(g)
        check(!mapOverlay.finished) // 취소된 터치는 지도 탭으로 처리하지 않는다
        g.input.onKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN)
        frame(g)
        check(mapOverlay.finished && !g.input.rawMode)

        // 집의 카메라는 음수 오프셋이므로 화면 중심은 월드 (0, 0)이 아니다.
        g.scene = HomeScene(g)
        frame(g)
        val homeCamera = g.scene.cameraOffset()
        // 집도 마찬가지로 화면 한가운데 = 카메라 오프셋 + (가상 중앙 / 2)
        near(g.screenToWorld(center).x, homeCamera.x + 300f)
        near(g.screenToWorld(center).y, homeCamera.y + 135f)

        // 앱이 백그라운드로 가는 동안 누른 키가 유지되지 않아야 한다.
        g.input.onKeyEvent(KeyEvent.KEYCODE_D, KeyEvent.ACTION_DOWN)
        frame(g)
        check(g.input.dirX > 0f)
        g.input.releaseHeld()
        frame(g)
        check(g.input.dirX == 0f)
        println("Input smoke: title → character → Seoul → world / modal / camera / pause OK")
    }
}
