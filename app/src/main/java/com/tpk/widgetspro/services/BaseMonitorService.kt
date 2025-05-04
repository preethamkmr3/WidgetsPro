package com.tpk.widgetspro.services

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.*
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import com.tpk.widgetspro.widgets.music.MusicSimpleWidgetProvider
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

abstract class BaseMonitorService : Service(), CoroutineScope {
    companion object {
        private const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
        const val ACTION_VISIBILITY_RESUMED = "com.tpk.widgetspro.VISIBILITY_RESUMED"
        const val ACTION_LAUNCHER_STATE_CHANGED = "com.tpk.widgetspro.LAUNCHER_STATE_CHANGED"
        const val EXTRA_IS_ACTIVE = "is_active"
        private const val ACTION_WALLPAPER_CHANGED_STRING = "android.intent.action.WALLPAPER_CHANGED"
        const val ACTION_THEME_CHANGED = "com.tpk.widgetspro.ACTION_THEME_CHANGED"
        @JvmStatic
        protected val CHECK_INTERVAL_INACTIVE_MS = TimeUnit.MINUTES.toMillis(60)
    }

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private var isInActiveState = false
    private var isLauncherActive = false
    private var isAccessibilityEnabled = false
    private var launcherStateReceived = false

    private lateinit var serviceJob: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + serviceJob

    private var updateJob: Job? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (shouldUpdate()) {
                        notifyVisibilityResumed()
                        cancelInactiveUpdates()
                        updateAllWidgets()
                    }
                }
                Intent.ACTION_CONFIGURATION_CHANGED,
                ACTION_WALLPAPER_CHANGED_STRING,
                ACTION_THEME_CHANGED -> {
                    updateAllWidgets()
                }
            }

            val currentActiveState = shouldUpdate()
            if (currentActiveState != isInActiveState) {
                isInActiveState = currentActiveState
                if (!currentActiveState) {
                    startInactiveUpdates()
                } else {
                    cancelInactiveUpdates()
                    updateAllWidgets()
                }
            }
        }
    }

    private val launcherStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_LAUNCHER_STATE_CHANGED) {
                isLauncherActive = intent.getBooleanExtra(EXTRA_IS_ACTIVE, false)
                launcherStateReceived = true
                if (shouldUpdate()) {
                    updateAllWidgets()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceJob = Job()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        isInActiveState = shouldUpdate()
        isAccessibilityEnabled = isAccessibilityServiceEnabled()

        createNotificationChannel()

        registerReceiver(
            systemReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(ACTION_WALLPAPER_CHANGED_STRING)
                addAction(ACTION_THEME_CHANGED)
            },
            Context.RECEIVER_NOT_EXPORTED
        )

        registerReceiver(
            launcherStateReceiver,
            IntentFilter(ACTION_LAUNCHER_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )

        if (!isInActiveState) {
            startInactiveUpdates()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(this, LauncherStateAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName.flattenToString())
    }

    private fun startInactiveUpdates() {
        updateJob?.cancel()
        updateJob = launch {
            while (isActive) {
                if (!shouldUpdate()) {
                    updateAllWidgets()
                }
                delay(CHECK_INTERVAL_INACTIVE_MS)
            }
        }
    }

    private fun cancelInactiveUpdates() {
        updateJob?.cancel()
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

    protected fun shouldUpdate(): Boolean {
        val isScreenOn = powerManager.isInteractive
        val isKeyguardLocked = keyguardManager.isKeyguardLocked
        val baseCondition = isScreenOn && !isKeyguardLocked

        return if (isAccessibilityEnabled) {
            baseCondition && (isLauncherActive || !launcherStateReceived)
        } else {
            baseCondition
        }
    }

    private fun notifyVisibilityResumed() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_VISIBILITY_RESUMED))
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
            AnalogClockWidgetProvider_2::class.java,
            GifWidgetProvider::class.java,
            MusicSimpleWidgetProvider::class.java
        )

        providers.forEach { provider ->
            val componentName = ComponentName(this, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                }
                sendBroadcast(updateIntent)
            }
        }
    }

    override fun onDestroy() {
        stopForeground(true)
        serviceJob.cancel()
        updateJob?.cancel()
        unregisterReceiver(systemReceiver)
        unregisterReceiver(launcherStateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}