package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseDottedGraphView
import com.tpk.widgetspro.base.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.cpu.CpuMonitor
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.cpu.DottedGraphView
import rikka.shizuku.Shizuku
import java.util.LinkedList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class CpuMonitorService : BaseMonitorService() {
    override val notificationId = 1
    override val notificationTitle = "CPU Monitor"
    override val notificationText = "Monitoring CPU usage"

    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50
    private var useRoot = false
    private var prefs: SharedPreferences? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { useRoot = it.getBooleanExtra("use_root", false) }
        if (!useRoot && (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::cpuMonitor.isInitialized) initializeMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initializeMonitoring() {
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
        cpuMonitor = CpuMonitor(useRoot) { cpuUsage, cpuTemperature ->
            val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val isDarkTheme = prefs.getBoolean("dark_theme", false)
            val isRedAccent = prefs.getBoolean("red_accent", false)
            val themeResId = when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
            val themedContext = ContextThemeWrapper(applicationContext, themeResId)

            val typeface = CommonUtils.getTypeface(themedContext)
            val usageBitmap = CommonUtils.createTextBitmap(themedContext, "%.0f%%".format(cpuUsage), 20f, typeface)
            val cpuBitmap = CommonUtils.createTextBitmap(themedContext, "CPU", 20f, typeface)

            dataPoints.addLast(cpuUsage)
            if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, CpuWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.cpu_widget_layout).apply {
                    setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
                    setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
                    setImageViewBitmap(R.id.graphWidgetImageView, createGraphBitmap(themedContext, dataPoints, DottedGraphView::class))
                    // ... rest of the updates
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)
        val cpuInterval = prefs?.getInt("cpu_interval", 60)?.coerceAtLeast(1) ?: 60
        cpuMonitor.startMonitoring(cpuInterval)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cpu_interval") {
            val newInterval = prefs?.getInt(key, 60)?.coerceAtLeast(1) ?: 60
            cpuMonitor.updateInterval(newInterval)
        }
    }

    private fun getDeviceProcessorModel(): String = when (android.os.Build.SOC_MODEL) {
        "SM8475" -> "8+ Gen 1"
        else -> android.os.Build.SOC_MODEL
    }

    private fun <T : BaseDottedGraphView> createGraphBitmap(context: Context, data: Any, viewClass: KClass<T>): Bitmap {
        val graphView = viewClass.java.getConstructor(Context::class.java).newInstance(context)
        (graphView as? DottedGraphView)?.setDataPoints((data as List<*>).filterIsInstance<Double>())
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, context.resources.displayMetrics).toInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, context.resources.displayMetrics).toInt()
        graphView.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        graphView.layout(0, 0, widthPx, heightPx)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply { graphView.draw(Canvas(this)) }
    }

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::cpuMonitor.isInitialized) cpuMonitor.stopMonitoring()
        super.onDestroy()
    }
}