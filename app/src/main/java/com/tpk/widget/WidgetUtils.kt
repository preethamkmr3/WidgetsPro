package com.tpk.widget

import android.content.Context
import android.graphics.*
import android.util.TypedValue

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

        val textBounds = Rect()
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
}
