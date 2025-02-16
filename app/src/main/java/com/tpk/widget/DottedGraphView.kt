package com.tpk.widget


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class DottedGraphView(context: Context) : View(context) {


    private var dataPoints: List<Double> = emptyList()

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
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

    private val dotSpacing = 8f
    private var greyDotSpacingHorizontal = 0f
    private val greyDotSpacingVertical = 10f
    private val fillAreaTopOffset = 5f
    private val fillAreaBottomOffset = 5f


    fun setDataPoints(dataPoints: List<Double>) {
        this.dataPoints = dataPoints
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) return

        val barWidth = width.toFloat() / dataPoints.size

        var maxDataValue = dataPoints.maxOrNull() ?: 100.0
        if (maxDataValue < 100.0) {
            maxDataValue = 100.0
        }
        val heightFactor = height.toFloat() / maxDataValue
        greyDotSpacingHorizontal = barWidth

        val fillStartY = fillAreaTopOffset
        val fillEndY = height - fillAreaBottomOffset

        var currentColumnX = 0f
        for (i in 0..dataPoints.size) {
            var currentFillY = fillStartY
            while (currentFillY < fillEndY) {
                canvas.drawCircle(
                    currentColumnX + greyDotSpacingHorizontal/2f,
                    currentFillY,
                    greyDotSpacingHorizontal / 4,
                    greyDotPaint
                )
                currentFillY += greyDotSpacingVertical
            }
            currentColumnX += greyDotSpacingHorizontal
        }


        for (i in dataPoints.indices) {
            val x = i * barWidth + barWidth/2f
            val dataPointY = height - fillAreaBottomOffset - dataPoints[i] * heightFactor

            var currentY = height - fillAreaBottomOffset
            while (currentY >= dataPointY) {
                canvas.drawCircle(x, currentY, (barWidth / 4).toFloat(), dotPaint)
                currentY -= dotSpacing
            }
        }
    }
}