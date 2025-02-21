package com.tpk.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Schedule the alarm for periodic updates
        scheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel the alarm when the widget is disabled
        cancelAlarm(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Update the widget immediately when onUpdate is called
        updateWidget(context)
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.tpk.widget.ACTION_UPDATE_WIDGET"
        private const val UPDATE_INTERVAL_MS = 60 * 1000L // 60 seconds

        fun scheduleAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BatteryReceiver::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
                UPDATE_INTERVAL_MS,
                pendingIntent
            )
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BatteryReceiver::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val batteryMonitor = BatteryMonitor(context) { percentage, health ->
                // Update widget UI
                val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
                val percentageText = "$percentage%"
                val batteryText = "BAT"

                val percentageBitmap = WidgetUtils.createTextBitmap(
                    context = context,
                    text = percentageText,
                    textSizeSp = 20f,
                    textColor = Color.RED,
                    typeface = typeface
                )

                val batteryBitmap = WidgetUtils.createTextBitmap(
                    context = context,
                    text = batteryText,
                    textSizeSp = 20f,
                    textColor = Color.RED,
                    typeface = typeface
                )

                val graphBitmap = createGraphBitmap(context, percentage)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.battery_widget_layout)
                    views.setImageViewBitmap(R.id.batteryImageView, batteryBitmap)
                    views.setImageViewBitmap(R.id.batteryPercentageImageView, percentageBitmap)
                    views.setTextViewText(R.id.batteryModelWidgetTextView, health.toString())
                    views.setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
                    if (percentage == 0) views.setTextViewText(R.id.batteryPercentageImageView, "Loading...")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
            batteryMonitor.fetchBatteryInfo()
        }

        private fun createGraphBitmap(context: Context, percentage: Int): Bitmap {
            val graphView = BatteryDottedView(context)
            graphView.updatePercentage(percentage)

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
    }
}