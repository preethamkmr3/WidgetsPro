package com.tpk.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_UPDATE_WIDGET = "com.tpk.widget.ACTION_UPDATE_WIDGET"
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startService(Intent(context, BatteryMonitorService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, BatteryMonitorService::class.java))
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(
            context.packageName,
            R.layout.battery_widget_layout
        )

        val updateIntent = Intent(context, BatteryWidgetProvider::class.java).apply {
            action = ACTION_UPDATE_WIDGET
        }
        val updatePendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(
            R.id.graphWidgetImageView,
            updatePendingIntent
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}