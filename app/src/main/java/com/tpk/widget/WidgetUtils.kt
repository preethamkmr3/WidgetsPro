package com.tpk.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue

object WidgetUtils {
    fun createTextBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        textColor: Int,
        typeface: Typeface
    ): Bitmap {
        val paint = Paint().apply {
            this.typeface = typeface
            color = textColor
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            isAntiAlias = true
        }

        val baseline = -paint.ascent()
        val width = (paint.measureText(text) + 0.5f).toInt()
        val height = (baseline + paint.descent() + 0.5f).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, baseline, paint)
        return bitmap
    }
}