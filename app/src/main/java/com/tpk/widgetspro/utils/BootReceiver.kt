package com.tpk.widgetspro.utils

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            updateWidgets(context, appWidgetManager, CpuWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, BatteryWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, CaffeineWidget::class.java)
            updateWidgets(context, appWidgetManager, BluetoothWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, SunTrackerWidget::class.java)
            updateWidgets(context, appWidgetManager, NetworkSpeedWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, WifiDataUsageWidgetProviderPill::class.java)
            updateWidgets(context, appWidgetManager, SimDataUsageWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, NoteWidgetProvider::class.java)
        }
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, providerClass: Class<*>) {
        val provider = ComponentName(context, providerClass)
        val widgetIds = manager.getAppWidgetIds(provider)
        if (widgetIds.isNotEmpty()) {
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                component = provider
                context.sendBroadcast(this)
            }
        }
    }
}
