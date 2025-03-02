package com.tpk.widgetpro.widgets.bluetooth

import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tpk.widgetpro.R

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED || action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BluetoothWidgetProvider::class.java))

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
                BluetoothWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}