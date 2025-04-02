package com.tpk.widgetspro.widgets.networkusage

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.NetworkSpeedWidgetService

class NetworkSpeedWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        context.startForegroundService(Intent(context, NetworkSpeedWidgetService::class.java))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        context.startForegroundService(Intent(context, NetworkSpeedWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, NetworkSpeedWidgetService::class.java))
    }
}