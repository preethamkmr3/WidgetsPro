package com.tpk.widget

import android.content.Context
import android.graphics.Canvas

class DottedGraphView(context: Context) : BaseDottedGraphView(context) {
    private var dataPoints: List<Double> = emptyList()
    private val dotSpacing = 10f
    private var greyDotSpacingHorizontal = 0f
    private val greyDotSpacingVertical = 10f

    fun setDataPoints(dataPoints: List<Double>) {
        this.dataPoints = dataPoints
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        greyDots.clear()
        if (dataPoints.isEmpty()) return

        val barWidth = width.toFloat() / dataPoints.size
        greyDotSpacingHorizontal = barWidth
        dotRadius = greyDotSpacingHorizontal / 4

        val fillStartY = fillAreaTopOffset
        val fillEndY = height - fillAreaBottomOffset
        var currentColumnX = 0f

        for (i in 0 until dataPoints.size) {
            var currentFillY = fillStartY
            while (currentFillY < fillEndY) {
                greyDots.add(Dot(currentColumnX + greyDotSpacingHorizontal / 2f, currentFillY))
                currentFillY += greyDotSpacingVertical
            }
            currentColumnX += greyDotSpacingHorizontal
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val barWidth = width.toFloat() / dataPoints.size
        var maxDataValue = dataPoints.maxOrNull() ?: 100.0
        if (maxDataValue < 100.0) maxDataValue = 100.0
        val heightFactor = height.toFloat() / maxDataValue

        for (i in dataPoints.indices) {
            val x = i * barWidth + barWidth / 2f
            val dataPointY = height - fillAreaBottomOffset - dataPoints[i] * heightFactor
            var currentY = height - fillAreaBottomOffset
            while (currentY >= dataPointY) {
                canvas.drawCircle(x, currentY, (barWidth / 4).toFloat(), dotPaint)
                currentY -= dotSpacing
            }
        }
    }
}