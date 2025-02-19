package com.tpk.widget

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.IBinder
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat

class BatteryMonitorService : Service() {

    private lateinit var batteryMonitor: BatteryMonitor
    private val NOTIFICATION_ID = 2

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::batteryMonitor.isInitialized) {
            initializeMonitoring()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun initializeMonitoring() {
        batteryMonitor = BatteryMonitor(this) { percentage, health ->
            updateWidget(percentage, health)
        }
        batteryMonitor.startMonitoring()
    }

    private fun updateWidget(percentage: Int, health: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, BatteryWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
        val percentageText = "%d%%".format(percentage)
        val batteryText = String.format("BAT")

        val percentageBitmap = WidgetUtils.createTextBitmap(
            context = this,
            text = percentageText,
            textSizeSp = 20f,
            textColor = Color.RED,
            typeface = typeface
        )

        val batteryBitmap = WidgetUtils.createTextBitmap(
            context = this,
            text = batteryText,
            textSizeSp = 20f,
            textColor = Color.RED,
            typeface = typeface
        )

        val graphBitmap = createGraphBitmap(this, percentage)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.battery_widget_layout)
            views.setImageViewBitmap(R.id.batteryImageView, batteryBitmap)
            views.setImageViewBitmap(R.id.batteryPercentageImageView, percentageBitmap)
            views.setTextViewText(R.id.batteryModelWidgetTextView, health.toString())
            views.setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createGraphBitmap(context: Context, percentage: Int): Bitmap {
        val graphView = BatteryDottedView(context)
        graphView.updatePercentage(percentage)

        val desiredWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            200f,
            context.resources.displayMetrics
        ).toInt()

        val desiredHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            80f,
            context.resources.displayMetrics
        ).toInt()

        graphView.measure(
            View.MeasureSpec.makeMeasureSpec(desiredWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(desiredHeightPx, View.MeasureSpec.EXACTLY)
        )
        graphView.layout(0, 0, desiredWidthPx, desiredHeightPx)

        return Bitmap.createBitmap(desiredWidthPx, desiredHeightPx, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            graphView.draw(canvas)
        }
    }


    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setContentTitle("Battery Monitor")
            .setContentText("Battery monitoring is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        batteryMonitor.stopMonitoring()
        super.onDestroy()
    }
}