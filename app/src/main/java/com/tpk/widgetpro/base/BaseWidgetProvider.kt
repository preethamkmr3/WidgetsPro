package com.tpk.widgetpro.base

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetpro.R
import com.tpk.widgetpro.utils.WidgetUtils

abstract class BaseWidgetProvider : AppWidgetProvider() {
    protected abstract val layoutId: Int
    protected abstract val setupText: String
    protected abstract val setupDestination: Class<*>

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (!hasRequiredPermissions(context)) updateWidgetSetupRequired(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (!hasRequiredPermissions(context)) updateWidgetSetupRequired(context, appWidgetIds)
        else appWidgetIds.forEach { updateNormalWidgetView(context, appWidgetManager, it) }
    }

    protected open fun hasRequiredPermissions(context: Context): Boolean = true

    protected abstract fun updateNormalWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int)

    protected fun updateWidgetSetupRequired(context: Context, appWidgetIds: IntArray? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, this::class.java)
        val ids = appWidgetIds ?: appWidgetManager.getAppWidgetIds(componentName)
        val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
        val setupBitmap = WidgetUtils.createTextBitmap(context, setupText, 20f, Color.RED, typeface)

        ids.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, layoutId).apply {
                setImageViewBitmap(R.id.setupView, setupBitmap)
                setViewVisibility(R.id.setupView, View.VISIBLE)
                setOnClickPendingIntent(R.id.setupView, WidgetUtils.getPendingIntent(context, appWidgetId, setupDestination))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}