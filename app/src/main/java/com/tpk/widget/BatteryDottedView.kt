package com.tpk.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class BatteryDottedView(context: Context) : View(context) {

    private var percentage = 0

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.FILL
    }

    private val greyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.FILL
    }

    private val fillAreaTopOffset = 5f
    private val fillAreaBottomOffset = 5f
    private val columns = 50
    private val rows = 20

    fun updatePercentage(newPercentage: Int) {
        percentage = newPercentage.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width.toFloat() / columns
        val availableHeight = height - fillAreaTopOffset - fillAreaBottomOffset
        val rowSpacing = availableHeight / (rows - 1)

        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val x = col * barWidth + barWidth/2
                val y = fillAreaTopOffset + row * rowSpacing
                canvas.drawCircle(x, y, barWidth/4, greyDotPaint)
            }
        }

        val filledColumns = percentage / 100f * columns
        val fullColumns = filledColumns.toInt()
        val partialRows = ((filledColumns - fullColumns) * rows).toInt()

        for (col in 0 until fullColumns) {
            for (row in 0 until rows) {
                val x = col * barWidth + barWidth/2
                val y = fillAreaTopOffset + row * rowSpacing
                canvas.drawCircle(x, y, barWidth/4, dotPaint)
            }
        }

        if (partialRows > 0 && fullColumns < columns) {
            for (row in (rows - partialRows) until rows) {
                val x = fullColumns * barWidth + barWidth/2
                val y = fillAreaTopOffset + row * rowSpacing
                canvas.drawCircle(x, y, barWidth/4, dotPaint)
            }
        }
    }
}