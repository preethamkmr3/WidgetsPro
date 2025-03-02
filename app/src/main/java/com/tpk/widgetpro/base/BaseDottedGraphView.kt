package com.tpk.widgetpro.base

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

abstract class BaseDottedGraphView(context: Context) : View(context) {
    protected data class Dot(val x: Float, val y: Float)
    protected val greyDots = mutableListOf<Dot>()
    protected val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.FILL
    }
    protected val greyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.FILL
    }
    protected val fillAreaTopOffset = 5f
    protected val fillAreaBottomOffset = 5f
    protected var dotRadius = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        greyDots.forEach { canvas.drawCircle(it.x, it.y, dotRadius, greyDotPaint) }
    }

    protected fun updateGreyDots(width: Int, height: Int, columns: Int, rows: Int, spacingHorizontal: Float = width.toFloat() / columns) {
        greyDots.clear()
        val barWidth = spacingHorizontal
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
}