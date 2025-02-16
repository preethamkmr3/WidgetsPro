package com.tpk.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.widget.RemoteViews
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import java.util.LinkedList
import android.view.View
import android.graphics.Canvas

class CpuMonitorService : Service() {

    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50

    private val CHANNEL_ID = "cpu_monitor_service_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()

        // Check Shizuku permission
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            // We cannot request permissions from a Service; stop the service and inform the user
            stopSelf()
            return
        }

        // Create notification channel and start the service in the foreground
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Initialize data points
        repeat(MAX_DATA_POINTS) {
            dataPoints.add(0.0)
        }

        // Initialize and start cpuMonitor
        cpuMonitor = CpuMonitor { cpuUsage, cpuTemperature ->
            updateWidget(cpuUsage, cpuTemperature)
        }
        cpuMonitor.startMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        cpuMonitor.stopMonitoring()
    }

    private fun updateWidget(cpuUsage: Double, cpuTemperature: Double) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, CpuWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        dataPoints.addLast(cpuUsage)
        if (dataPoints.size > MAX_DATA_POINTS) {
            dataPoints.removeFirst()
        }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.widget_layout)
            views.setTextViewText(
                R.id.cpuUsageWidgetTextView,
                String.format("%.1f %%", cpuUsage)
            )
            views.setTextViewText(
                R.id.cpuTempWidgetTextView,
                String.format("%.1f °C", cpuTemperature)
            )
            views.setTextViewText(
                R.id.cpuModelWidgetTextView, getDeviceProcessorModel() ?: "Unknown"
            )

            val graphBitmap = createGraphBitmap(this, dataPoints)
            views.setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getDeviceProcessorModel(): String? {
        return when (android.os.Build.SOC_MODEL) {
            "SM8475" -> "8+ Gen 1"
            else -> android.os.Build.SOC_MODEL
        }
    }

    private fun createGraphBitmap(context: Context, dataPoints: List<Double>): Bitmap {
        val graphView = DottedGraphView(context)
        graphView.setDataPoints(dataPoints)

        val desiredWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            200f,
            context.resources.displayMetrics
        ).toInt()

        val desiredHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            80f,
            context.resources.displayMetrics
        ).toInt()

        graphView.measure(
            View.MeasureSpec.makeMeasureSpec(desiredWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(desiredHeightPx, View.MeasureSpec.EXACTLY)
        )
        graphView.layout(0, 0, desiredWidthPx, desiredHeightPx)

        val bitmap = Bitmap.createBitmap(desiredWidthPx, desiredHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        graphView.draw(canvas)
        return bitmap
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CPU Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CPU Monitor")
            .setContentText("CPU monitoring is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your own icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
