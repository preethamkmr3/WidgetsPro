package com.tpk.widget;

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widget.R

class CaffeineWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach {
            updateWidget(context, appWidgetManager, it)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("active", false)

            val views = RemoteViews(context.packageName, R.layout.caffeine_widget_layout).apply {
                setImageViewResource(
                    R.id.widget_toggle,
                    if (isActive) R.drawable.ic_coffee_active else R.drawable.ic_coffee_inactive
                )
                setOnClickPendingIntent(R.id.widget_toggle, getToggleIntent(context))
            }
            manager.updateAppWidget(widgetId, views)
        }

        fun getToggleIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, CaffeineToggleReceiver::class.java).apply {
                    action = "TOGGLE_CAFFEINE"
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CaffeineWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
        }
    }
}