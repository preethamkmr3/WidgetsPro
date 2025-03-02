package com.tpk.widgetpro.widgets.cpu

import android.content.Context
import android.graphics.Canvas
import com.tpk.widgetpro.base.BaseDottedGraphView

class DottedGraphView(context: Context) : BaseDottedGraphView(context) {
    private var dataPoints: List<Double> = emptyList()
    private val dotSpacing = 10f

    fun setDataPoints(dataPoints: List<Double>) {
        this.dataPoints = dataPoints
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (dataPoints.isEmpty()) return
        updateGreyDots(w, h, dataPoints.size, (h / dotSpacing).toInt(), w.toFloat() / dataPoints.size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val barWidth = width.toFloat() / dataPoints.size
        var maxDataValue = dataPoints.maxOrNull() ?: 100.0
        if (maxDataValue < 100.0) maxDataValue = 100.0
        val heightFactor = height.toFloat() / maxDataValue

        dataPoints.forEachIndexed { i, dataPoint ->
            val x = i * barWidth + barWidth / 2f
            val dataPointY = height - fillAreaBottomOffset - dataPoint * heightFactor
            var currentY = height - fillAreaBottomOffset
            while (currentY >= dataPointY) {
                canvas.drawCircle(x, currentY, dotRadius, dotPaint)
                currentY -= dotSpacing
            }
        }
    }
}