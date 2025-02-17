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
        // Initialize the paint with the desired properties
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
            this.color = textColor
            this.typeface = typeface
            this.textAlign = Paint.Align.LEFT
        }

        // Measure the text dimensions
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        // Calculate the required width and height for the bitmap
        val width = minOf(textBounds.width(), maxWidthPx)
        val height = minOf(textBounds.height(), maxHeightPx)

        // Create a bitmap and canvas to draw the text
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Correct the alignment by adjusting the Y-coordinate for drawing the text
        val x = 0f
        val y = height.toFloat() - textBounds.bottom

        // Draw the text onto the canvas
        canvas.drawText(text, x, y, paint)

        return bitmap
    }
}
