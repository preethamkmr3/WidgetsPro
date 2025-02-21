package com.tpk.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import rikka.shizuku.Shizuku

class CpuWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (hasRootAccess() || hasShizukuAccess()) {
            context.startService(Intent(context, CpuMonitorService::class.java))
        } else {
            updateWidgetSetupRequired(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, CpuMonitorService::class.java))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            if (hasRootAccess() || hasShizukuAccess()) {
                context.startService(Intent(context, CpuMonitorService::class.java))
            } else {
                updateWidgetSetupRequired(context, intArrayOf(appWidgetId))
            }
        }
    }

    private fun updateWidgetSetupRequired(context: Context, appWidgetIds: IntArray? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, CpuWidgetProvider::class.java)
        val ids = appWidgetIds ?: appWidgetManager.getAppWidgetIds(componentName)
        val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
        val setupBitmap = WidgetUtils.createTextBitmap(context, "--%", 20f, Color.RED, typeface)
        val cpuBitmap = WidgetUtils.createTextBitmap(context, "CPU", 20f, Color.RED, typeface)
        val graphBitmap = WidgetUtils.createEmptyDottedPattern()

        for (appWidgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.cpu_widget_layout).apply {
                setImageViewBitmap(R.id.cpuUsageImageView, setupBitmap)
                setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
                setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
                setTextViewText(R.id.cpuTempWidgetTextView, "--°C")
                setTextViewText(R.id.cpuModelWidgetTextView, "Setup Required. Tap to setup")

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.cpuModelWidgetTextView, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        graphBitmap.recycle()
    }

    companion object {
        fun hasRootAccess(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
                val output = process.inputStream.bufferedReader().use { it.readLine() }
                process.destroy()
                output != null
            } catch (e: Exception) {
                false
            }
        }

        fun hasShizukuAccess(): Boolean {
            return try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }
    }
}