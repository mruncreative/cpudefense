package com.example.cpudefense.gameElements

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import com.example.cpudefense.Game
import com.example.cpudefense.networkmap.Viewport
import com.example.cpudefense.utils.setCenter

class SpeedControl(var game: Game) {
    private var button1 = SpeedControlButton(game, SpeedControlButton.Type.FAST, this)
    private var button2 = SpeedControlButton(game, SpeedControlButton.Type.PAUSE, this)
    private var button_return = SpeedControlButton(game, SpeedControlButton.Type.RETURN, this)
    private var buttons = listOf<SpeedControlButton>( button1, button2, button_return )
    private var area = Rect(0,0,0,0)

    fun setSize(parentArea: Rect)
    {
        val actualButtonSize = Game.speedControlButtonSize * game.resources.displayMetrics.density.toInt() *
            if (game.gameActivity.settings.configUseLargeButtons) 2 else 1
        val margin = actualButtonSize / 5   // space between the buttons
        buttons.forEach() {it.set_size(actualButtonSize)}
        area.right = parentArea.right - margin
        area.bottom = parentArea.bottom - margin
        area.left = area.right - 2 * actualButtonSize - margin
        area.top = area.bottom - actualButtonSize
        button1.area.setCenter(area.left + actualButtonSize / 2, area.centerY())
        button2.area.setCenter(area.right - actualButtonSize / 2, area.centerY())
        // put the 'return' button on the other side
        button_return.area.setCenter(parentArea.width()-button2.area.centerX(), area.centerY())
    }

    fun resetButtons()
    {
        button1.type = SpeedControlButton.Type.FAST
        button2.type = SpeedControlButton.Type.PAUSE
    }

    fun onDown(p0: MotionEvent): Boolean {
        return button1.onDown(p0) || button2.onDown(p0) || button_return.onDown(p0)
    }

    fun display(canvas: Canvas, viewport: Viewport) {
        if (area.left == 0)
            return
        buttons.forEach() { it.display(canvas)}
    }
}