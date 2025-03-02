package com.tpk.widgetpro.widgets.battery

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.tpk.widgetpro.MainActivity
import com.tpk.widgetpro.R
import com.tpk.widgetpro.base.BaseWidgetProvider
import com.tpk.widgetpro.services.BatteryMonitorService

class BatteryWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.battery_widget_layout
    override val setupText = "Tap to setup Battery"
    override val setupDestination = MainActivity::class.java

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startService(Intent(context, BatteryMonitorService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, BatteryMonitorService::class.java))
    }

    override fun updateNormalWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        context.startService(Intent(context, BatteryMonitorService::class.java))
    }
}