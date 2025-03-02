package com.tpk.widgetpro.widgets.cpu

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.tpk.widgetpro.MainActivity
import com.tpk.widgetpro.R
import com.tpk.widgetpro.base.BaseWidgetProvider
import com.tpk.widgetpro.services.CpuMonitorService
import com.tpk.widgetpro.utils.PermissionUtils

class CpuWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.cpu_widget_layout
    override val setupText = "Tap to setup CPU"
    override val setupDestination = MainActivity::class.java

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (hasRequiredPermissions(context)) context.startService(Intent(context, CpuMonitorService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, CpuMonitorService::class.java))
    }

    override fun updateNormalWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        context.startService(Intent(context, CpuMonitorService::class.java))
    }

    override fun hasRequiredPermissions(context: Context) = PermissionUtils.hasRootAccess() || PermissionUtils.hasShizukuAccess()

}