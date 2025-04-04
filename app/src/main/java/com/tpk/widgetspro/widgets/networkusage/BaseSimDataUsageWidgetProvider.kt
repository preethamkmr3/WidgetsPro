package com.tpk.widgetspro.widgets.networkusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseSimDataUsageWidgetService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.NetworkStatsHelper

abstract class BaseSimDataUsageWidgetProvider : AppWidgetProvider() {
    abstract val layoutResId: Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, layoutResId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val activeProviders = prefs.getStringSet("active_wifi_data_usage_providers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        activeProviders.add(this::class.java.name)
        prefs.edit().putStringSet("active_wifi_data_usage_providers", activeProviders).apply()
        context.startForegroundService(Intent(context, BaseSimDataUsageWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val activeProviders = prefs.getStringSet("active_wifi_data_usage_providers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        activeProviders.remove(this::class.java.name)
        prefs.edit().putStringSet("active_wifi_data_usage_providers", activeProviders).apply()
        if (activeProviders.isEmpty()) {
            context.stopService(Intent(context, BaseSimDataUsageWidgetService::class.java))
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, layoutResId: Int) {
            try {
                if (!CommonUtils.hasUsageAccessPermission(context)) {
                    setPermissionPrompt(context, appWidgetManager, appWidgetId, layoutResId)
                    return
                }
                val usage = NetworkStatsHelper.getSimDataUsage(context)
                val totalBytes = usage[2]
                val formattedUsage = formatBytes(totalBytes)

                val views = RemoteViews(context.packageName, layoutResId).apply {
                    if (layoutResId == R.layout.sim_data_usage_widget_circle) {
                        val iconDrawable = context.getDrawable(R.drawable.sim_data_usage_icon)
                        val scaledIcon = scaleDrawable(iconDrawable, 0.9f)
                        setImageViewBitmap(R.id.sim_data_usage_image, scaledIcon)
                        setInt(R.id.sim_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                        setImageViewBitmap(
                            R.id.sim_data_usage_text,
                            CommonUtils.createTextAlternateBitmap(context, formattedUsage, 14f, CommonUtils.getTypeface(context))
                        )
                    } else {
                        setImageViewBitmap(
                            R.id.sim_data_usage_text,
                            CommonUtils.createTextAlternateBitmap(context, formattedUsage, 20f, CommonUtils.getTypeface(context))
                        )
                        setInt(R.id.sim_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                    }
                }

                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.sim_data_usage_pill, pendingIntent)
                views.setOnClickPendingIntent(R.id.sim_data_usage_circle, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)

            } catch (e: Exception) {
                Log.e("SimDataUsageWidget", "Error getting SIM data usage", e)
                setPermissionPrompt(context, appWidgetManager, appWidgetId, layoutResId)
            }
        }

        private fun setPermissionPrompt(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, layoutResId: Int) {
            val views = RemoteViews(context.packageName, layoutResId).apply {
                if (layoutResId == R.layout.sim_data_usage_widget_circle) {
                    val iconDrawable = context.getDrawable(R.drawable.sim_data_usage_icon)
                    val scaledIcon = scaleDrawable(iconDrawable, 0.9f)
                    setImageViewBitmap(R.id.sim_data_usage_image, scaledIcon)
                    setInt(R.id.sim_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                    setImageViewBitmap(
                        R.id.sim_data_usage_text,
                        CommonUtils.createTextAlternateBitmap(context, "Click here", 14f, CommonUtils.getTypeface(context))
                    )
                } else {
                    setImageViewBitmap(
                        R.id.sim_data_usage_text,
                        CommonUtils.createTextAlternateBitmap(context, "Click here", 20f, CommonUtils.getTypeface(context))
                    )
                    setInt(R.id.sim_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                }
            }

            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.sim_data_usage_pill, pendingIntent)
            views.setOnClickPendingIntent(R.id.sim_data_usage_circle, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun formatBytes(bytes: Long): String {
            val unit = 1024
            if (bytes < unit) return "$bytes B"
            val exp = minOf((Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt(), 5)
            val pre = "KMGTPE"[exp - 1]
            val value = bytes / Math.pow(unit.toDouble(), exp.toDouble())

            return if (exp <= 2) {
                String.format("%.0f %sB", value, pre)
            } else {
                String.format("%.1f %sB", value, pre)
            }
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
                SimDataUsageWidgetProviderCircle::class.java -> R.layout.sim_data_usage_widget_circle
                SimDataUsageWidgetProviderPill::class.java -> R.layout.sim_data_usage_widget_pill
                else -> throw IllegalArgumentException("Unknown provider class: $providerClass")
            }) }
        }
    }
}