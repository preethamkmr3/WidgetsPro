package com.tpk.widget

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import rikka.shizuku.Shizuku
import java.util.LinkedList

class CpuMonitorService : Service() {
    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50
    private var useRoot = false
    private val NOTIFICATION_ID = 1
    private var cpuBitmap: Bitmap? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { useRoot = it.getBooleanExtra("use_root", false) }

        if (!useRoot) {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return START_NOT_STICKY
            }
        }

        if (!::cpuMonitor.isInitialized) initializeMonitoring()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
        val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
        cpuBitmap = WidgetUtils.createTextBitmap(this, "CPU", 20f, Color.RED, typeface)
    }

    private fun initializeMonitoring() {
        cpuMonitor = CpuMonitor(useRoot) { cpuUsage, cpuTemperature ->
            updateWidget(cpuUsage, cpuTemperature)
        }
        cpuMonitor.startMonitoring()
    }

    private fun updateWidget(cpuUsage: Double, cpuTemperature: Double) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, CpuWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
        val usageText = "%.0f%%".format(cpuUsage)
        val usageBitmap = WidgetUtils.createTextBitmap(this, usageText, 20f, Color.RED, typeface)

        dataPoints.addLast(cpuUsage)
        if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.cpu_widget_layout)
            views.setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
            views.setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
            views.setTextViewText(R.id.cpuTempWidgetTextView, "%.1f°C".format(cpuTemperature))
            views.setTextViewText(R.id.cpuModelWidgetTextView, getDeviceProcessorModel() ?: "Unknown")
            views.setImageViewBitmap(R.id.graphWidgetImageView, WidgetUtils.createGraphBitmap(this, dataPoints, DottedGraphView::class))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setContentTitle(getString(R.string.cpu_monitor_title))
            .setContentText(getString(R.string.cpu_monitor_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getDeviceProcessorModel(): String {
        return when (android.os.Build.SOC_MODEL) {
            "SM8475" -> "8+ Gen 1"
            else -> android.os.Build.SOC_MODEL
        }
    }

    override fun onDestroy() {
        cpuMonitor.stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}