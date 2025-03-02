package com.tpk.widgetpro.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetpro.MainActivity
import com.tpk.widgetpro.R
import com.tpk.widgetpro.utils.NotificationUtils
import com.tpk.widgetpro.utils.WidgetUtils
import com.tpk.widgetpro.widgets.cpu.CpuMonitor
import com.tpk.widgetpro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetpro.widgets.cpu.DottedGraphView
import rikka.shizuku.Shizuku
import java.util.LinkedList

class CpuMonitorService : Service() {
    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50
    private var useRoot = false
    private val NOTIFICATION_ID = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { useRoot = it.getBooleanExtra("use_root", false) }
        if (!useRoot && (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)) {
            return START_NOT_STICKY
        }
        if (!::cpuMonitor.isInitialized) {
            initializeMonitoring()
        }
        return START_STICKY
    }

    private fun initializeMonitoring() {
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
        startForeground(NOTIFICATION_ID, createNotification())
        cpuMonitor = CpuMonitor(useRoot) { cpuUsage, cpuTemperature ->
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, CpuWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
            val usageBitmap = WidgetUtils.createTextBitmap(
                this,
                "%.0f%%".format(cpuUsage),
                20f,
                Color.RED,
                typeface
            )
            val cpuBitmap = WidgetUtils.createTextBitmap(this, "CPU", 20f, Color.RED, typeface)

            dataPoints.addLast(cpuUsage)
            if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.cpu_widget_layout).apply {
                    setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
                    setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
                    setViewVisibility(R.id.setupView, View.GONE)
                    setTextViewText(R.id.cpuTempWidgetTextView, "%.1f°C".format(cpuTemperature))
                    setTextViewText(
                        R.id.cpuModelWidgetTextView,
                        getDeviceProcessorModel() ?: "Unknown"
                    )
                    setImageViewBitmap(
                        R.id.graphWidgetImageView,
                        WidgetUtils.createGraphBitmap(
                            this@CpuMonitorService,
                            dataPoints,
                            DottedGraphView::class
                        )
                    )
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val cpuInterval = prefs.getInt("cpu_interval", 60).coerceAtLeast(1)
        cpuMonitor.startMonitoring(cpuInterval)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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