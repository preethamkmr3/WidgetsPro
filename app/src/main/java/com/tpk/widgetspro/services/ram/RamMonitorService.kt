package com.tpk.widgetspro.services.ram

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
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.cpu.DottedGraphView
import com.tpk.widgetspro.widgets.ram.RamMonitor
import com.tpk.widgetspro.widgets.ram.RamWidgetProvider
import java.util.LinkedList
import kotlin.reflect.KClass

class RamMonitorService : BaseMonitorService() {
    private lateinit var ramMonitor: RamMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50
    private var prefs: SharedPreferences? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandResult = super.onStartCommand(intent, flags, startId)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, RamWidgetProvider::class.java))
        if (widgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!::ramMonitor.isInitialized) {
            initializeMonitoring()
        }

        return commandResult
    }

    private fun initializeMonitoring() {
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
        ramMonitor = RamMonitor(this) { ramUsage, totalRam, freeRam ->
            if (shouldUpdate()) {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, RamWidgetProvider::class.java))

                if (widgetIds.isEmpty()) {
                    stopSelf()
                    return@RamMonitor
                }

                val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
                val isRedAccent = themePrefs.getBoolean("red_accent", false)
                val themeResId = when {
                    isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                    isDarkTheme -> R.style.Theme_WidgetsPro
                    isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                    else -> R.style.Theme_WidgetsPro
                }
                val themedContext = ContextThemeWrapper(applicationContext, themeResId)

                val typeface = CommonUtils.getTypeface(themedContext)
                val usageBitmap = CommonUtils.createTextBitmap(themedContext, "%.0f%%".format(ramUsage), 20f, typeface)
                val ramBitmap = CommonUtils.createTextBitmap(themedContext, "RAM", 20f, typeface)

                dataPoints.addLast(ramUsage)
                if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

                widgetIds.forEach { appWidgetId ->
                    val views = RemoteViews(packageName, R.layout.ram_widget_layout).apply {
                        setImageViewBitmap(R.id.ramUsageImageView, usageBitmap)
                        setImageViewBitmap(R.id.ramImageView, ramBitmap)
                        setImageViewBitmap(R.id.graphWidgetImageView, createGraphBitmap(themedContext, dataPoints, DottedGraphView::class))
                        setTextViewText(R.id.ramFreeWidgetTextView, "F: $freeRam")
                        setTextViewText(R.id.ramTotalWidgetTextView, "T: $totalRam")
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)

        val interval = prefs?.getInt("ram_interval", 60)?.coerceAtLeast(1) ?: 60
        ramMonitor.startMonitoring(interval)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "ram_interval") {
            val newInterval = prefs?.getInt(key, 60)?.coerceAtLeast(1) ?: 60
            if (::ramMonitor.isInitialized) {
                ramMonitor.updateInterval(newInterval)
            }
        }
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
        if (::ramMonitor.isInitialized) ramMonitor.stopMonitoring()
        super.onDestroy()
    }
}
