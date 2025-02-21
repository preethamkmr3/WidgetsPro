package com.tpk.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat

class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelAlarm(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidget(context)
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.tpk.widget.ACTION_UPDATE_WIDGET"
        private const val UPDATE_INTERVAL_MS = 60 * 1000L

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
                val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
                val percentageText = "$percentage%"
                val batteryText = "BAT"

                val percentageBitmap = WidgetUtils.createTextBitmap(
                    context, percentageText, 20f, Color.RED, typeface
                )
                val batteryBitmap = WidgetUtils.createTextBitmap(
                    context, batteryText, 20f, Color.RED, typeface
                )
                val graphBitmap = WidgetUtils.createGraphBitmap(context, percentage, BatteryDottedView::class)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.battery_widget_layout)
                    views.setImageViewBitmap(R.id.batteryImageView, batteryBitmap)
                    views.setImageViewBitmap(R.id.batteryPercentageImageView, percentageBitmap)
                    views.setTextViewText(R.id.batteryModelWidgetTextView, health.toString())
                    views.setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
                    if (percentage == 0) views.setTextViewText(R.id.batteryPercentageImageView, context.getString(R.string.loading))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
            batteryMonitor.fetchBatteryInfo()
        }
    }
}