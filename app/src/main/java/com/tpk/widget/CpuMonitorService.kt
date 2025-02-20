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
import android.graphics.Canvas
import android.graphics.Color
import android.os.IBinder
import android.util.TypedValue
import android.view.View
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { useRoot = it.getBooleanExtra("use_root", false) }

        if (!useRoot && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!::cpuMonitor.isInitialized) initializeMonitoring()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
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
        val cpuText = "CPU"

        val usageBitmap = WidgetUtils.createTextBitmap(
            context = this,
            text = usageText,
            textSizeSp = 20f,
            textColor = Color.RED,
            typeface = typeface
        )

        val cpuBitmap = WidgetUtils.createTextBitmap(
            context = this,
            text = cpuText,
            textSizeSp = 20f,
            textColor = Color.RED,
            typeface = typeface
        )

        dataPoints.addLast(cpuUsage)
        if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.cpu_widget_layout)
            views.setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
            views.setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
            views.setTextViewText(R.id.cpuTempWidgetTextView, "%.1f°C".format(cpuTemperature))
            views.setTextViewText(R.id.cpuModelWidgetTextView, getDeviceProcessorModel() ?: "Unknown")
            views.setImageViewBitmap(R.id.graphWidgetImageView, createGraphBitmap(this, dataPoints))
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
            .setContentTitle("CPU Monitor")
            .setContentText("CPU monitoring is active")
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

    private fun createGraphBitmap(context: Context, dataPoints: List<Double>): Bitmap {
        val graphView = DottedGraphView(context)
        graphView.setDataPoints(dataPoints)

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

    override fun onDestroy() {
        cpuMonitor.stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}