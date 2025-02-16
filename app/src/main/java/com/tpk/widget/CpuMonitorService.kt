package com.tpk.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.IBinder
import android.util.TypedValue
import android.widget.RemoteViews
import rikka.shizuku.Shizuku
import java.util.LinkedList
import android.view.View
import android.graphics.Canvas

class CpuMonitorService : Service() {

    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50

    override fun onCreate() {
        super.onCreate()

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
            stopSelf()
            return
        }

        repeat(MAX_DATA_POINTS) {
            dataPoints.add(0.0)
        }

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
            views.setTextViewText(R.id.cpuModelWidgetTextView, getDeviceProcessorModel() ?: "Unknown")

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
}
