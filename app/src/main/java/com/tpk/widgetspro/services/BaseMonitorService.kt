package com.tpk.widgetspro.services

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.appwidget.AppWidgetManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import android.content.res.Configuration

abstract class BaseMonitorService : Service() {
    companion object {
        private const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
        const val ACTION_VISIBILITY_RESUMED = "com.tpk.widgetspro.VISIBILITY_RESUMED"
        private const val EVENT_QUERY_INTERVAL_MS = 5000L
    }

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    private var cachedLauncherPackage: String? = null
    private var lastWasLauncher = true
    private var lastUiMode: Int = -1

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.d("BaseMonitorService", "Received ${intent.action}, checking visibility")
                    if (shouldUpdate()) notifyVisibilityResumed()
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    Log.d("BaseMonitorService", "Received ACTION_CLOSE_SYSTEM_DIALOGS")
                    updateCachedLauncherPackage()
                    if (shouldUpdate()) notifyVisibilityResumed()
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    Log.d("BaseMonitorService", "Received ACTION_CONFIGURATION_CHANGED")
                    checkThemeChange()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        updateCachedLauncherPackage()
        lastUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        createNotificationChannel()
        registerReceiver(systemReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }, Context.RECEIVER_NOT_EXPORTED)
        Log.d("BaseMonitorService", "Service created")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Widgets Pro Channel",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Channel for keeping Widgets Pro services running"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    protected fun shouldUpdate(): Boolean {
        val isScreenOn = powerManager.isInteractive
        val isKeyguardLocked = keyguardManager.isKeyguardLocked
        val isLauncherInForeground = isLauncherForeground()
        val shouldUpdate = isScreenOn && !isKeyguardLocked && isLauncherInForeground
        Log.d(
            "BaseMonitorService",
            "shouldUpdate: screenOn=$isScreenOn, keyguardLocked=$isKeyguardLocked, launcherForeground=$isLauncherInForeground, result=$shouldUpdate"
        )
        return shouldUpdate
    }

    private fun isLauncherForeground(): Boolean {
        try {
            if (!hasUsageStatsPermission()) {
                Log.d("BaseMonitorService", "No usage stats permission, using last known state: $lastWasLauncher")
                return lastWasLauncher
            }

            val recentPackage = getRecentPackageName()
            if (recentPackage == null) {
                Log.d("BaseMonitorService", "No recent package found, using last known state: $lastWasLauncher")
                return lastWasLauncher
            }

            return checkAgainstLauncherPackage(recentPackage).also { lastWasLauncher = it }
        } catch (e: Exception) {
            Log.e("BaseMonitorService", "Error checking launcher: $e")
            return lastWasLauncher
        }
    }

    private fun getRecentPackageName(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - EVENT_QUERY_INTERVAL_MS
        val events = usageStatsManager.queryEvents(startTime, endTime)
        var recentPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                recentPackage = event.packageName
            }
        }
        return recentPackage
    }

    private fun checkAgainstLauncherPackage(packageName: String): Boolean {
        if (cachedLauncherPackage == null) updateCachedLauncherPackage()
        val isLauncher = packageName == cachedLauncherPackage
        Log.d(
            "BaseMonitorService",
            "Launcher check: recent=$packageName, launcher=$cachedLauncherPackage, result=$isLauncher"
        )
        return isLauncher
    }

    private fun updateCachedLauncherPackage() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        cachedLauncherPackage = packageManager.resolveActivity(launcherIntent, 0)?.activityInfo?.packageName
        Log.d("BaseMonitorService", "Updated cached launcher package: $cachedLauncherPackage")
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun notifyVisibilityResumed() {
        Log.d("BaseMonitorService", "Notifying visibility resumed")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_VISIBILITY_RESUMED))
    }

    private fun checkThemeChange() {
        val currentUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (lastUiMode != -1 && currentUiMode != lastUiMode) {
            Log.d("BaseMonitorService", "Theme changed, updating widgets. Old mode: $lastUiMode, New mode: $currentUiMode")
            updateAllWidgets()
        }
        lastUiMode = currentUiMode
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val providers = arrayOf(
            CpuWidgetProvider::class.java,
            BatteryWidgetProvider::class.java,
            BluetoothWidgetProvider::class.java,
            CaffeineWidget::class.java,
            SunTrackerWidget::class.java,
            NetworkSpeedWidgetProviderCircle::class.java,
            NetworkSpeedWidgetProviderPill::class.java,
            WifiDataUsageWidgetProviderCircle::class.java,
            WifiDataUsageWidgetProviderPill::class.java,
            SimDataUsageWidgetProviderCircle::class.java,
            SimDataUsageWidgetProviderPill::class.java,
            NoteWidgetProvider::class.java,
            AnalogClockWidgetProvider_1::class.java,
            AnalogClockWidgetProvider_2::class.java
        )

        providers.forEach { provider ->
            val componentName = ComponentName(this, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                }
                sendBroadcast(intent)
                Log.d("BaseMonitorService", "Sent update broadcast for ${provider.simpleName}")
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(systemReceiver)
        Log.d("BaseMonitorService", "Service destroyed")
        super.onDestroy()
    }
}