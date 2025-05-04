package com.tpk.widgetspro.services.music

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.music.MusicSimpleWidgetProvider

class MediaMonitorService : BaseMonitorService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeSessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val activeControllers = mutableMapOf<MediaController, MediaController.Callback>()
    @Volatile private var isWidgetAreaVisible = true

    private val visibilityChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BaseMonitorService.ACTION_LAUNCHER_STATE_CHANGED) {
                val newVisibility = intent.getBooleanExtra(BaseMonitorService.EXTRA_IS_ACTIVE, true)
                if (newVisibility != isWidgetAreaVisible) {
                    isWidgetAreaVisible = newVisibility
                    sendUpdateBroadcast(newVisibility)
                }
            }
        }
    }

    private val themeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_CONFIGURATION_CHANGED, ACTION_THEME_CHANGED -> {
                    MusicSimpleWidgetProvider.updateAllWidgetColors(context)
                    val refreshIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
                        action = MusicSimpleWidgetProvider.ACTION_REFRESH_VISUALIZER_ALL
                    }
                    context.sendBroadcast(refreshIntent)
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaMonitorService::class.java)

        activeSessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateControllerCallbacks(controllers ?: emptyList())
            sendUpdateBroadcast(isWidgetAreaVisible)
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionListener!!, componentName, handler)
            updateControllerCallbacks(mediaSessionManager.getActiveSessions(componentName) ?: emptyList())
            sendUpdateBroadcast(isWidgetAreaVisible)
        } catch (e: SecurityException) {
            stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityChangeReceiver,
            IntentFilter(BaseMonitorService.ACTION_LAUNCHER_STATE_CHANGED)
        )

        registerReceiver(
            themeChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(ACTION_THEME_CHANGED)
            },
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun updateControllerCallbacks(controllers: List<MediaController>) {
        val currentControllers = controllers.toSet()
        val removedControllers = activeControllers.keys - currentControllers
        removedControllers.forEach { controller ->
            val callback = activeControllers.remove(controller)
            callback?.let {
                try {
                    controller.unregisterCallback(it)
                } catch (e: Exception) {

                }
            }
        }

        controllers.forEach { controller ->
            if (!activeControllers.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                        sendUpdateBroadcast(isWidgetAreaVisible)
                    }

                    override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                        sendUpdateBroadcast(isWidgetAreaVisible)
                    }

                    override fun onSessionDestroyed() {
                        val cb = activeControllers.remove(controller)
                        cb?.let {
                            try {
                                controller.unregisterCallback(it)
                            } catch (e: Exception) {

                            }
                        }
                        sendUpdateBroadcast(isWidgetAreaVisible)
                    }
                }
                try {
                    controller.registerCallback(callback, handler)
                    activeControllers[controller] = callback
                } catch (e: Exception) {

                }
            }
        }

        if (removedControllers.isNotEmpty() || (activeControllers.isEmpty() && controllers.isNotEmpty())) {
            sendUpdateBroadcast(isWidgetAreaVisible)
        }

        if (controllers.isEmpty() && activeControllers.isNotEmpty()) {
            activeControllers.forEach { (c, cb) -> try { c.unregisterCallback(cb) } catch (e: Exception) {} }
            activeControllers.clear()
            sendUpdateBroadcast(isWidgetAreaVisible)
        } else if (controllers.isEmpty() && activeControllers.isEmpty()) {
            sendUpdateBroadcast(isWidgetAreaVisible)
        }
    }


    private fun sendUpdateBroadcast(isVisible: Boolean) {
        val updateIntent = Intent(this, MusicSimpleWidgetProvider::class.java).apply {
            action = MusicSimpleWidgetProvider.ACTION_MEDIA_UPDATE
            putExtra("WIDGET_VISIBLE", isVisible)
        }
        sendBroadcast(updateIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityChangeReceiver)
        unregisterReceiver(themeChangeReceiver)

        activeSessionListener?.let {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(it)
            } catch (e: Exception) {

            }
        }
        activeControllers.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {

            }
        }
        activeControllers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}