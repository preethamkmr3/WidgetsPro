package com.tpk.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BatteryWidgetProvider.ACTION_UPDATE_WIDGET) {
            BatteryWidgetProvider.updateWidget(context)
        }
    }
}