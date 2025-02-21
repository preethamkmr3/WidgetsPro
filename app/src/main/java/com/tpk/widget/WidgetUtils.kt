package com.tpk.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.reflect.KClass

object WidgetUtils {
    fun createTextBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        textColor: Int,
        typeface: Typeface,
        maxWidthPx: Int = Int.MAX_VALUE,
        maxHeightPx: Int = Int.MAX_VALUE
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
            this.color = textColor
            this.typeface = typeface
            this.textAlign = Paint.Align.LEFT
        }

        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        val textWidth = paint.measureText(text)
        val textHeight = textBounds.height().toFloat()
        val width = minOf(textWidth.toInt(), maxWidthPx)
        val height = minOf(textHeight.toInt(), maxHeightPx)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val x = 0f
        val y = height.toFloat() - textBounds.bottom

        canvas.drawText(text, x, y, paint)
        return bitmap
    }

    fun <T : BaseDottedGraphView> createGraphBitmap(
        context: Context,
        data: Any,
        viewClass: KClass<T>
    ): Bitmap {
        val graphView = viewClass.java.getConstructor(Context::class.java).newInstance(context)
        when (data) {
            is Int -> (graphView as? BatteryDottedView)?.updatePercentage(data)
            is List<*> -> (graphView as? DottedGraphView)?.setDataPoints(data.filterIsInstance<Double>())
        }

        val widthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 200f, context.resources.displayMetrics
        ).toInt()

        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 80f, context.resources.displayMetrics
        ).toInt()

        graphView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        graphView.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        graphView.draw(canvas)
        return bitmap
    }

    fun createEmptyDottedPattern(): Bitmap {
        val width = 200
        val height = 80
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val greyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            strokeWidth = 1.5f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.FILL
        }

        val columns = 50
        val rows = 20
        val barWidth = width.toFloat() / columns
        val availableHeight = height - 5f - 5f
        val rowSpacing = availableHeight / (rows - 1)

        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val x = col * barWidth + barWidth / 2
                val y = 5f + row * rowSpacing
                canvas.drawCircle(x, y, barWidth / 4, greyDotPaint)
            }
        }

        return bitmap
    }
}