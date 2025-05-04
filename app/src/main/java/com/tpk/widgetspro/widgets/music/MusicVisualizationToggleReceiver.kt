package com.tpk.widgetspro.widgets.music

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class MusicVisualizationToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MusicSimpleWidgetProvider.ACTION_TOGGLE_VISUALIZATION) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val updateIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
                    action = MusicSimpleWidgetProvider.ACTION_TOGGLE_VISUALIZATION
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("widget://togglevis/$appWidgetId")
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }
}