package com.tpk.widgetpro.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.tpk.widgetpro.base.BaseDottedGraphView
import com.tpk.widgetpro.widgets.battery.BatteryDottedView
import com.tpk.widgetpro.widgets.cpu.DottedGraphView
import kotlin.reflect.KClass

object WidgetUtils {
    fun createTextBitmap(context: Context, text: String, textSizeSp: Float, textColor: Int, typeface: Typeface): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
            color = textColor
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }

        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val width = paint.measureText(text).toInt()
        val height = textBounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, height - textBounds.bottom.toFloat(), paint)
        return bitmap
    }

    fun <T : BaseDottedGraphView> createGraphBitmap(context: Context, data: Any, viewClass: KClass<T>): Bitmap {
        val graphView = viewClass.java.getConstructor(Context::class.java).newInstance(context)
        when (data) {
            is Int -> (graphView as? BatteryDottedView)?.updatePercentage(data)
            is List<*> -> (graphView as? DottedGraphView)?.setDataPoints(data.filterIsInstance<Double>())
        }

        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, context.resources.displayMetrics).toInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, context.resources.displayMetrics).toInt()

        graphView.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        graphView.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        graphView.draw(Canvas(bitmap))
        return bitmap
    }

    fun getPendingIntent(context: Context, appWidgetId: Int, destination: Class<*>): PendingIntent {
        return PendingIntent.getActivity(context, appWidgetId, Intent(context, destination).apply {
            putExtra("appWidgetId", appWidgetId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}