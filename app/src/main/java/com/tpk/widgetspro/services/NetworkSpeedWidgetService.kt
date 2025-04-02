package com.tpk.widgetspro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProvider

class NetworkSpeedWidgetService : Service() {
    private var previousBytes: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 1000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(4, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSpeed() {
        val currentBytes = TrafficStats.getTotalRxBytes()
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val thisWidget = ComponentName(applicationContext, NetworkSpeedWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        val typeface = CommonUtils.getTypeface(applicationContext)

        if (currentBytes != TrafficStats.UNSUPPORTED.toLong()) {
            if (previousBytes != 0L) {
                val bytesInLastInterval = currentBytes - previousBytes
                val speedMBps = (bytesInLastInterval / 1024.0 / 1024.0).toFloat()
                val speedText = String.format("%.2f MB/s", speedMBps)
                updateWidgets(appWidgetManager, appWidgetIds, speedText, typeface)
            }
            previousBytes = currentBytes
        } else {
            updateWidgets(appWidgetManager, appWidgetIds, "N/A", typeface)
        }
    }

    private fun updateWidgets(
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        speedText: String,
        typeface: Typeface
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(packageName, R.layout.network_speed_widget_layout).apply {
                setImageViewBitmap(
                    R.id.speed_text,
                    CommonUtils.createTextAlternateBitmap(
                        applicationContext,
                        speedText,
                        20f,
                        typeface
                    )
                )
                setInt(
                    R.id.imageData,
                    "setColorFilter",
                    CommonUtils.getAccentColor(applicationContext)
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "SPEED_WIDGET_CHANNEL"

        val channel = NotificationChannel(
            channelId,
            "Speed Widget Updates",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, NetworkSpeedWidgetProvider::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Speed Widget Running")
            .setContentText("Monitoring network speed")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}