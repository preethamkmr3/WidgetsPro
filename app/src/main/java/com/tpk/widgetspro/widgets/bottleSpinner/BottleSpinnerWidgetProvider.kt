package com.tpk.widgetspro.widgets.bottleSpinner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.bottleSpinner.BottleSpinnerAnimationService

class BottleSpinnerWidgetProvider : AppWidgetProvider() {

    private val ACTION_SPIN_BOTTLE = "com.tpk.widgetspro.action.SPIN_BOTTLE"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SPIN_BOTTLE) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val serviceIntent = Intent(context, BottleSpinnerAnimationService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                context.startService(serviceIntent)
            }
        }
    }

    private fun getPendingSelfIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, javaClass).apply {
            action = ACTION_SPIN_BOTTLE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, appWidgetId, intent, flags)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_bottle_spinner)
        remoteViews.setOnClickPendingIntent(R.id.widget_root, getPendingSelfIntent(context, appWidgetId))
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}