package com.tpk.widgetpro.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import android.widget.RemoteViews
import com.tpk.widgetpro.MainActivity
import com.tpk.widgetpro.R
import com.tpk.widgetpro.utils.NotificationUtils
import com.tpk.widgetpro.utils.WidgetUtils
import com.tpk.widgetpro.widgets.battery.BatteryDottedView
import com.tpk.widgetpro.widgets.battery.BatteryMonitor
import com.tpk.widgetpro.widgets.battery.BatteryWidgetProvider

class BatteryMonitorService : Service() {
    private lateinit var batteryMonitor: BatteryMonitor
    private val NOTIFICATION_ID = 2

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::batteryMonitor.isInitialized) {
            initializeMonitoring()
        }
        return START_STICKY
    }

    private fun initializeMonitoring() {
        startForeground(NOTIFICATION_ID, createNotification())

        batteryMonitor = BatteryMonitor(this) { percentage, health ->
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, BatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
            val percentageBitmap = WidgetUtils.createTextBitmap(this, "$percentage%", 20f, Color.RED, typeface)
            val batteryBitmap = WidgetUtils.createTextBitmap(this, "BAT", 20f, Color.RED, typeface)
            val graphBitmap = WidgetUtils.createGraphBitmap(this, percentage, BatteryDottedView::class)

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.battery_widget_layout).apply {
                    setImageViewBitmap(R.id.batteryImageView, batteryBitmap)
                    setImageViewBitmap(R.id.batteryPercentageImageView, percentageBitmap)
                    setTextViewText(R.id.batteryModelWidgetTextView, health.toString())
                    setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
                    if (percentage == 0) setTextViewText(R.id.batteryPercentageImageView, getString(R.string.loading))
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val interval = prefs.getInt("battery_interval", 60).coerceAtLeast(1)
        batteryMonitor.startMonitoring(interval)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setContentTitle(getString(R.string.battery_monitor_title))
            .setContentText(getString(R.string.battery_monitor_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        batteryMonitor.stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}