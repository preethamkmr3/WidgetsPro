package com.tpk.widgetspro.utils

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import java.util.Calendar

object CommonUtils {
    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        return when {
            isDarkTheme && isRedAccent -> ContextCompat.getColor(context, R.color.accent_color1)
            isDarkTheme -> ContextCompat.getColor(context, R.color.accent_color)
            isRedAccent -> ContextCompat.getColor(context, R.color.accent_color1)
            else -> ContextCompat.getColor(context, R.color.accent_color)
        }
    }

    fun createTextBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        typeface: Typeface
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            color = getAccentColor(context)
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val width = paint.measureText(text).toInt()
        val height = textBounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, height - textBounds.bottom.toFloat(), paint)
        return bitmap
    }

    fun createTextAlternateBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        typeface: Typeface
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            color = ContextCompat.getColor(context, R.color.text_color)
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val width = paint.measureText(text).toInt()
        val height = textBounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, height - textBounds.bottom.toFloat(), paint)
        return bitmap
    }

    fun createTextNotesWidgetBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        typeface: Typeface,
        accentColor: Int,
        textColor: Int
    ): Bitmap {
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            color = accentColor
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }

        val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            color = textColor
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }

        val lines = text.split("\n", limit = 2)

        if (lines.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val heading = lines[0]
        val content = if (lines.size > 1) lines[1] else ""

        val headingLines = listOf(heading)
        val headingLineHeight = headingPaint.getFontSpacing()
        val headingMaxWidth = headingLines.maxOf { headingPaint.measureText(it) }
        val headingHeight = headingLines.size * headingLineHeight

        val contentLines = content.split("\n")
        val contentLineHeight = contentPaint.getFontSpacing()
        val contentMaxWidth = if (content.isNotEmpty()) contentLines.maxOf { contentPaint.measureText(it) } else 0f
        val contentHeight = if (content.isNotEmpty()) contentLines.size * contentLineHeight else 0f

        val totalWidth = maxOf(headingMaxWidth, contentMaxWidth).toInt() + 10
        val totalHeight = (headingHeight + if (content.isNotEmpty()) contentHeight + headingLineHeight else 0f).toInt() + 10

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val headingFm = headingPaint.fontMetrics
        val headingBaselineOffset = -headingFm.ascent
        headingLines.forEachIndexed { index, line ->
            val y = index * headingLineHeight + headingBaselineOffset
            canvas.drawText(line, 5f, y, headingPaint)
        }

        if (content.isNotEmpty()) {
            val contentFm = contentPaint.fontMetrics
            val contentBaselineOffset = -contentFm.ascent
            var contentY = headingHeight + contentBaselineOffset

            contentLines.forEachIndexed { index, line ->
                canvas.drawText(line, 5f, contentY + index * contentLineHeight, contentPaint)
            }
        }

        return bitmap
    }

    fun getPendingIntent(context: Context, appWidgetId: Int, destination: Class<*>): PendingIntent =
        PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, destination).putExtra("appWidgetId", appWidgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun updateAllWidgets(context: Context, providerClass: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, providerClass)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isNotEmpty()) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                setComponent(component)
            }
            context.sendBroadcast(intent)
        }
    }

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTypeface(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.ndot)!!
}

object NotificationUtils {
    const val CHANNEL_ID = "widget_monitor_channel"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL_ID, "Widget Monitor", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "System resource monitoring" }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }
}