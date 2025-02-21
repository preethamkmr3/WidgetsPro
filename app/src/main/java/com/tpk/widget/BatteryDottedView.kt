package com.tpk.widget

import android.content.Context
import android.graphics.Canvas

class BatteryDottedView(context: Context) : BaseDottedGraphView(context) {
    private var percentage = 0
    private val columns = 50
    private val rows = 20

    fun updatePercentage(newPercentage: Int) {
        percentage = newPercentage.coerceIn(0, 100)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        greyDots.clear()
        val barWidth = width.toFloat() / columns
        val availableHeight = height - fillAreaTopOffset - fillAreaBottomOffset
        val rowSpacing = availableHeight / (rows - 1)
        dotRadius = barWidth / 4

        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val x = col * barWidth + barWidth / 2
                val y = fillAreaTopOffset + row * rowSpacing
                greyDots.add(Dot(x, y))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width.toFloat() / columns
        val filledColumns = percentage / 100f * columns
        val fullColumns = filledColumns.toInt()
        val partialRows = ((filledColumns - fullColumns) * rows).toInt()
        val availableHeight = height - fillAreaTopOffset - fillAreaBottomOffset
        val rowSpacing = availableHeight / (rows - 1)

        for (col in 0 until fullColumns) {
            for (row in 0 until rows) {
                val x = col * barWidth + barWidth / 2
                val y = fillAreaTopOffset + row * rowSpacing
                canvas.drawCircle(x, y, dotRadius, dotPaint)
            }
        }

        if (partialRows > 0 && fullColumns < columns) {
            for (row in (rows - partialRows) until rows) {
                val x = fullColumns * barWidth + barWidth / 2
                val y = fillAreaTopOffset + row * rowSpacing
                canvas.drawCircle(x, y, dotRadius, dotPaint)
            }
        }
    }
}