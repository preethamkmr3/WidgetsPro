package com.tpk.widgetpro.widgets.caffeine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tpk.widgetpro.services.CaffeineService

class CaffeineToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        val isActive = !prefs.getBoolean("active", false)

        if (toggleCaffeine(context, isActive)) {
            prefs.edit().putBoolean("active", isActive).apply()
            CaffeineWidget.updateAllWidgets(context)
        }
    }

    private fun toggleCaffeine(context: Context, enable: Boolean): Boolean {
        return try {
            val serviceIntent = Intent(context, CaffeineService::class.java)
            if (enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                else context.startService(serviceIntent)
            } else context.stopService(serviceIntent)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}