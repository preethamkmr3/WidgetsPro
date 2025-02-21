package com.tpk.widget

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
        for (dot in greyDots) {
            canvas.drawCircle(dot.x, dot.y, dotRadius, greyDotPaint)
        }
    }
}