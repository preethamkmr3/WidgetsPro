package com.tpk.widgetspro.widgets.networkusage

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseNetworkSpeedWidgetService
import com.tpk.widgetspro.utils.CommonUtils

abstract class BaseNetworkSpeedWidgetProvider : AppWidgetProvider() {
    abstract val layoutResId: Int

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, this::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, layoutResId) }
        context.startForegroundService(Intent(context, BaseNetworkSpeedWidgetService::class.java))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startForegroundService(Intent(context, BaseNetworkSpeedWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, BaseNetworkSpeedWidgetService::class.java))
    }

    companion object {
        private const val ACTION_UPDATE = "com.tpk.widgetspro.ACTION_NETWORK_SPEED_UPDATE"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, layoutResId: Int) {
            val views = RemoteViews(context.packageName, layoutResId).apply {
                if (layoutResId == R.layout.network_speed_widget_circle) {
                    val iconDrawable = context.getDrawable(R.drawable.network_speed_widget_icon)
                    val scaledIcon = scaleDrawable(iconDrawable, 0.9f)
                    setImageViewBitmap(R.id.network_speed_widget_image, scaledIcon)
                    setInt(R.id.network_speed_widget_image, "setColorFilter", CommonUtils.getAccentColor(context))
                    setImageViewBitmap(
                        R.id.network_speed_widget_text,
                        CommonUtils.createTextAlternateBitmap(context, "N/A", 14f, CommonUtils.getTypeface(context))
                    )
                } else {
                    setImageViewBitmap(
                        R.id.network_speed_widget_text,
                        CommonUtils.createTextAlternateBitmap(context, "N/A", 20f, CommonUtils.getTypeface(context))
                    )
                    setInt(R.id.network_speed_widget_image, "setColorFilter", CommonUtils.getAccentColor(context))
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun scaleDrawable(drawable: Drawable?, scaleFactor: Float): Bitmap? {
            if (drawable == null) return null
            val width = (drawable.intrinsicWidth * scaleFactor).toInt()
            val height = (drawable.intrinsicHeight * scaleFactor).toInt()
            drawable.setBounds(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            return bitmap
        }

        fun updateAllWidgets(context: Context, providerClass: Class<*>) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass))
            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, when (providerClass) {
                NetworkSpeedWidgetProviderCircle::class.java -> R.layout.network_speed_widget_circle
                NetworkSpeedWidgetProviderPill::class.java -> R.layout.network_speed_widget_pill
                else -> throw IllegalArgumentException("Unknown provider class: $providerClass")
            }) }
        }
    }
}