package com.tpk.widgetspro.widgets.ram

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseWidgetProvider
import com.tpk.widgetspro.services.ram.RamMonitorService

class RamWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.ram_widget_layout
    override val setupText = "Tap to setup RAM"
    override val setupDestination = MainActivity::class.java

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startService(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        startService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, RamMonitorService::class.java))
    }

    override fun updateNormalWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        startService(context)
    }

    override fun hasRequiredPermissions(context: Context): Boolean {
        return true
    }

    private fun startService(context: Context) {
        context.startService(Intent(context, RamMonitorService::class.java))
    }
}
