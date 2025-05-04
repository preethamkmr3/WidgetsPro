package com.tpk.widgetspro.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.music.MediaMonitorService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import java.io.IOException

class MusicSimpleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.tpk.widgetspro.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.tpk.widgetspro.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.tpk.widgetspro.ACTION_PREVIOUS"
        const val ACTION_MEDIA_UPDATE = "com.tpk.widgetspro.ACTION_MEDIA_UPDATE"
        const val ACTION_REFRESH_VISUALIZER = "com.tpk.widgetspro.ACTION_REFRESH_VISUALIZER"
        const val ACTION_REFRESH_VISUALIZER_ALL = "com.tpk.widgetspro.ACTION_REFRESH_VISUALIZER_ALL"
        const val ACTION_TOGGLE_VISUALIZATION = "com.tpk.widgetspro.ACTION_TOGGLE_VISUALIZATION"

        const val PREFS_NAME = "music_widget_prefs"
        const val PREF_PREFIX_KEY = "music_widget_"
        const val PREF_LAST_TITLE_KEY = "_last_title"
        const val PREF_LAST_ARTIST_KEY = "_last_artist"
        const val PREF_LAST_ART_PATH_KEY = "_last_art_path"
        const val PREF_VISUALIZATION_ENABLED_KEY = "_visualization_enabled"
        const val ALBUM_ART_DIR = "album_art"
        const val VISUALIZER_STATE_DIR = "visualizer_state"

        private var lastAppLaunchTime: Long = 0
        private const val APP_LAUNCH_COOLDOWN_MS: Long = 3000

        val visualizerDrawers = ConcurrentHashMap<Int, MusicVisualizerDrawer>()
        val updateHandlers = ConcurrentHashMap<Int, Handler>()
        val updateRunnables = ConcurrentHashMap<Int, Runnable>()
        private const val VISUALIZER_UPDATE_INTERVAL_MS = 60L

        fun getWidgetIds(context: Context): IntArray {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            return appWidgetManager.getAppWidgetIds(ComponentName(context, MusicSimpleWidgetProvider::class.java))
        }

        private fun getAlbumArtFile(context: Context, appWidgetId: Int): File {
            val cacheDir = File(context.cacheDir, ALBUM_ART_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return File(cacheDir, "widget_${appWidgetId}_art.png")
        }

        private fun getVisualizerBitmapFile(context: Context, appWidgetId: Int): File {
            val cacheDir = File(context.cacheDir, VISUALIZER_STATE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return File(cacheDir, "widget_${appWidgetId}_viz.png")
        }

        private fun saveBitmapToFile(bitmap: Bitmap?, file: File): Boolean {
            if (bitmap == null || bitmap.isRecycled) return false
            var success = false
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    success = true
                }
            } catch (e: IOException) {
                success = false
            }
            return success
        }

        private fun loadBitmapFromFile(file: File): Bitmap? {
            return try {
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun updateAllWidgetColors(context: Context) {
            val currentWidgetIds = getWidgetIds(context)
            currentWidgetIds.forEach { id ->
                visualizerDrawers[id]?.updateColor()
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isWidgetVisible: Boolean
        ) {
            updateAppWidgetInternal(context, appWidgetManager, appWidgetId, isWidgetVisible)
        }

        private fun updateAppWidgetInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isWidgetVisible: Boolean
        ) {
            val drawer = visualizerDrawers.getOrPut(appWidgetId) { MusicVisualizerDrawer(context.applicationContext) }
            val views = RemoteViews(context.packageName, R.layout.music_simple_widget)
            val mediaController = getMediaController(context)
            val metadata = mediaController?.metadata
            val playbackState = mediaController?.playbackState
            var isPlaying = false
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            val titleKey = PREF_PREFIX_KEY + appWidgetId + PREF_LAST_TITLE_KEY
            val artistKey = PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ARTIST_KEY
            val artPathKey = PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ART_PATH_KEY
            val vizEnabledKey = PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY
            val isVisualizationGloballyEnabled = prefs.getBoolean(vizEnabledKey, true)

            if (mediaController != null && metadata != null && playbackState != null) {
                val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
                val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                val albumArtBitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)

                views.setTextViewText(R.id.text_title, title)
                views.setTextViewText(R.id.text_artist, artist)
                editor.putString(titleKey, title)
                editor.putString(artistKey, artist)

                val artFile = getAlbumArtFile(context, appWidgetId)
                if (albumArtBitmap != null && !albumArtBitmap.isRecycled) {
                    views.setImageViewBitmap(R.id.image_album_art, albumArtBitmap)
                    if (saveBitmapToFile(albumArtBitmap, artFile)) {
                        editor.putString(artPathKey, artFile.absolutePath)
                    } else {
                        editor.remove(artPathKey)
                        views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                    }
                } else {
                    val lastArtPath = prefs.getString(artPathKey, null)
                    val loadedBitmap = if (lastArtPath != null) loadBitmapFromFile(File(lastArtPath)) else null
                    if (loadedBitmap != null && !loadedBitmap.isRecycled) {
                        views.setImageViewBitmap(R.id.image_album_art, loadedBitmap)
                    } else {
                        views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                        editor.remove(artPathKey)
                        artFile.delete()
                    }
                }

                isPlaying = playbackState.state == PlaybackState.STATE_PLAYING
                views.setImageViewResource(R.id.button_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

            } else {
                val lastTitle = prefs.getString(titleKey, null)
                if (lastTitle != null) {
                    val lastArtist = prefs.getString(artistKey, "Unknown Artist")
                    val lastArtPath = prefs.getString(artPathKey, null)
                    views.setTextViewText(R.id.text_title, lastTitle)
                    views.setTextViewText(R.id.text_artist, lastArtist)
                    val loadedBitmap = if (lastArtPath != null) loadBitmapFromFile(File(lastArtPath)) else null
                    if (loadedBitmap != null && !loadedBitmap.isRecycled) {
                        views.setImageViewBitmap(R.id.image_album_art, loadedBitmap)
                    } else {
                        views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                    }
                } else {
                    setNoMusicPlaying(views)
                }
                views.setImageViewResource(R.id.button_play_pause, R.drawable.ic_play_arrow)
                isPlaying = false
            }
            editor.apply()

            val visualizerBitmapFile = getVisualizerBitmapFile(context, appWidgetId)
            val hasPermissions = hasRequiredPermissions(context)

            if (hasPermissions) {
                drawer.linkToGlobalOutput()
            } else {
                drawer.release()
            }

            val shouldVisualize = isPlaying && isWidgetVisible && isVisualizationGloballyEnabled && hasPermissions
            val isCurrentlyVisualizing = updateHandlers.containsKey(appWidgetId)

            if (shouldVisualize) {
                drawer.resume()
                views.setViewVisibility(R.id.visualizer_image_view, View.VISIBLE)
                startVisualizerUpdates(context, appWidgetManager, appWidgetId, drawer)
                visualizerBitmapFile.delete()
            } else {
                if (isCurrentlyVisualizing) {
                    val lastBitmap = drawer.getVisualizerBitmap()
                    drawer.pause()
                    stopVisualizerUpdates(appWidgetId)

                    if (lastBitmap != null && !lastBitmap.isRecycled) {
                        if (saveBitmapToFile(lastBitmap, visualizerBitmapFile)) {
                            views.setImageViewBitmap(R.id.visualizer_image_view, lastBitmap)
                            views.setViewVisibility(R.id.visualizer_image_view, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.visualizer_image_view, View.GONE)
                            visualizerBitmapFile.delete()
                        }
                    } else {
                        views.setViewVisibility(R.id.visualizer_image_view, View.GONE)
                        visualizerBitmapFile.delete()
                    }
                } else {
                    val savedBitmap = loadBitmapFromFile(visualizerBitmapFile)
                    if (savedBitmap != null && !savedBitmap.isRecycled && isVisualizationGloballyEnabled && hasPermissions) {
                        views.setImageViewBitmap(R.id.visualizer_image_view, savedBitmap)
                        views.setViewVisibility(R.id.visualizer_image_view, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.visualizer_image_view, View.GONE)
                        if (!isVisualizationGloballyEnabled || !hasPermissions) {
                            visualizerBitmapFile.delete()
                        }
                    }
                    drawer.pause()
                }
            }

            setupPendingIntents(context, views, appWidgetId)

            try {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {

            }
        }

        private fun hasRequiredPermissions(context: Context): Boolean {
            val recordAudioGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val notificationListenerGranted = isNotificationListenerEnabled(context)
            return recordAudioGranted && notificationListenerGranted
        }

        private fun isNotificationListenerEnabled(context: Context): Boolean {
            val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val componentName = ComponentName(context, MediaMonitorService::class.java)
            return listeners != null && listeners.contains(componentName.flattenToString())
        }


        private fun startVisualizerUpdates(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            visualizerDrawer: MusicVisualizerDrawer
        ) {
            if (updateHandlers.containsKey(appWidgetId)) return

            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    if (!updateHandlers.containsKey(appWidgetId)) return

                    val bitmap = visualizerDrawer.getVisualizerBitmap()
                    if (bitmap != null && !bitmap.isRecycled) {
                        val partialViews = RemoteViews(context.packageName, R.layout.music_simple_widget)
                        partialViews.setImageViewBitmap(R.id.visualizer_image_view, bitmap)
                        try {
                            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
                        } catch (e: Exception) {
                            stopVisualizerUpdates(appWidgetId)
                            return
                        }
                    } else {

                    }
                    if (updateHandlers.containsKey(appWidgetId)) {
                        handler.postDelayed(this, VISUALIZER_UPDATE_INTERVAL_MS)
                    }
                }
            }
            updateHandlers[appWidgetId] = handler
            updateRunnables[appWidgetId] = runnable
            handler.post(runnable)
        }

        private fun stopVisualizerUpdates(appWidgetId: Int) {
            val handler = updateHandlers.remove(appWidgetId)
            val runnable = updateRunnables.remove(appWidgetId)
            if (handler != null && runnable != null) {
                handler.removeCallbacks(runnable)
            }
        }

        private fun setNoMusicPlaying(views: RemoteViews) {
            views.setTextViewText(R.id.text_title, "No music playing")
            views.setTextViewText(R.id.text_artist, "")
            views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
            views.setImageViewResource(R.id.button_play_pause, R.drawable.ic_play_arrow)
        }

        private fun setupPendingIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
            val playPauseReqCode = appWidgetId * 10 + 0
            val nextReqCode = appWidgetId * 10 + 1
            val prevReqCode = appWidgetId * 10 + 2
            val launchReqCode = appWidgetId * 10 + 3
            val toggleVizReqCode = appWidgetId * 10 + 4

            val playPauseIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://$appWidgetId/playpause")
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, playPauseReqCode, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
                action = ACTION_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://$appWidgetId/next")
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, nextReqCode, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
                action = ACTION_PREVIOUS
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://$appWidgetId/previous")
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, prevReqCode, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val launchIntent = getLaunchMusicAppIntent(context)
            val launchPendingIntent = PendingIntent.getActivity(
                context, launchReqCode, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val toggleVizIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_VISUALIZATION
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://$appWidgetId/togglevis")
            }
            val toggleVizPendingIntent = PendingIntent.getBroadcast(
                context, toggleVizReqCode, toggleVizIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.button_play_pause, playPausePendingIntent)
            views.setOnClickPendingIntent(R.id.button_next, nextPendingIntent)
            views.setOnClickPendingIntent(R.id.button_previous, prevPendingIntent)
            views.setOnClickPendingIntent(R.id.image_album_art, launchPendingIntent)
            views.setOnClickPendingIntent(R.id.visualizer_image_view, toggleVizPendingIntent)
        }


        private fun getMediaController(context: Context): MediaController? {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?
            val componentName = ComponentName(context, MediaMonitorService::class.java)
            return try {
                sessionManager?.getActiveSessions(componentName)?.firstOrNull()
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
        }

        private fun getLaunchMusicAppIntent(context: Context): Intent {
            val currentMediaAppPackage = getMediaController(context)?.packageName
            return if (currentMediaAppPackage != null) {
                context.packageManager.getLaunchIntentForPackage(currentMediaAppPackage)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: getDefaultMusicIntent()
            } else {
                getDefaultMusicIntent()
            }
        }

        private fun getDefaultMusicIntent(): Intent {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        private fun handlePlayPause(context: Context, mediaController: MediaController?, appWidgetId: Int) {
            if (mediaController?.playbackState != null) {
                val state = mediaController.playbackState!!.state
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                    mediaController.transportControls?.pause()
                } else {
                    mediaController.transportControls?.play()
                }
            } else {
                launchMusicApp(context)
            }
        }

        private fun launchMusicApp(context: Context) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAppLaunchTime < APP_LAUNCH_COOLDOWN_MS) {
                return
            }
            try {
                val intent = getLaunchMusicAppIntent(context)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    lastAppLaunchTime = currentTime
                } else {

                }
            } catch (e: Exception) {

            }
        }

        private fun startMediaMonitorService(context: Context) {
            if (!isNotificationListenerEnabled(context)) {
                return
            }
            val serviceIntent = Intent(context.applicationContext, MediaMonitorService::class.java)
            try {
                ContextCompat.startForegroundService(context.applicationContext, serviceIntent)
            } catch (e: Exception) {
                try { context.applicationContext.startService(serviceIntent) } catch (se: Exception) {

                }
            }
        }

        private fun toggleVisualization(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val vizEnabledKey = PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY
            val currentVisibility = prefs.getBoolean(vizEnabledKey, true)
            val newVisibility = !currentVisibility
            prefs.edit().putBoolean(vizEnabledKey, newVisibility).apply()

            updateAppWidgetInternal(context, appWidgetManager, appWidgetId, true)
        }
    }


    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        val drawer = visualizerDrawers.getOrPut(appWidgetId) { MusicVisualizerDrawer(context.applicationContext) }
        if (newOptions != null) {
            val minHeightDp = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
            val minWidthDp = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)

            val targetHeightDp = (minHeightDp * 0.5).toInt().coerceIn(25, 50)
            val targetWidthDp = (targetHeightDp * 1.5).toInt()

            val visualizerHeightPx = targetHeightDp.dpToPx(context)
            val visualizerWidthPx = targetWidthDp.dpToPx(context)

            val safeWidth = if (visualizerWidthPx > 0) visualizerWidthPx else 40.dpToPx(context)
            val safeHeight = if (visualizerHeightPx > 0) visualizerHeightPx else 40.dpToPx(context)
            drawer.updateDimensions(safeWidth, safeHeight)
        } else {
            drawer.updateDimensions(40.dpToPx(context), 40.dpToPx(context))
        }

        updateAppWidgetInternal(context, appWidgetManager, appWidgetId, true)
    }


    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val drawer = visualizerDrawers.getOrPut(appWidgetId) {
                MusicVisualizerDrawer(context.applicationContext)
            }
            drawer.updateColor()

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val targetHeightDp = (minHeightDp * 0.5).toInt().coerceIn(25, 50)
            val targetWidthDp = (targetHeightDp * 1.5).toInt()
            val visualizerHeightPx = targetHeightDp.dpToPx(context)
            val visualizerWidthPx = targetWidthDp.dpToPx(context)
            val safeWidth = if (visualizerWidthPx > 0) visualizerWidthPx else 40.dpToPx(context)
            val safeHeight = if (visualizerHeightPx > 0) visualizerHeightPx else 40.dpToPx(context)
            drawer.updateDimensions(safeWidth, safeHeight)

            updateAppWidgetInternal(context, appWidgetManager, appWidgetId, true)
        }
        if (appWidgetIds.isNotEmpty()) {
            startMediaMonitorService(context)
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action ?: return
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val currentWidgetIds = getWidgetIds(context)

        val targetAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isVisible = intent.getBooleanExtra("WIDGET_VISIBLE", true)

        when (action) {
            ACTION_MEDIA_UPDATE -> {
                currentWidgetIds.forEach { id ->
                    updateAppWidgetInternal(context, appWidgetManager, id, isVisible)
                }
            }
            ACTION_REFRESH_VISUALIZER_ALL -> {
                currentWidgetIds.forEach { id ->
                    forceVisualizerRefresh(context, appWidgetManager, id)
                }
            }
            ACTION_TOGGLE_VISUALIZATION -> {
                if (targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && targetAppWidgetId in currentWidgetIds) {
                    toggleVisualization(context, appWidgetManager, targetAppWidgetId)
                }
            }
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                if (currentWidgetIds.isEmpty()) return

                if (targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && targetAppWidgetId in currentWidgetIds) {
                    val mediaController = getMediaController(context)
                    var needsDelayedUpdate = false

                    when (action) {
                        ACTION_PLAY_PAUSE -> {
                            handlePlayPause(context, mediaController, targetAppWidgetId)
                            needsDelayedUpdate = true
                        }
                        ACTION_NEXT -> {
                            mediaController?.transportControls?.skipToNext()
                            needsDelayedUpdate = true
                        }
                        ACTION_PREVIOUS -> {
                            mediaController?.transportControls?.skipToPrevious()
                            needsDelayedUpdate = true
                        }
                    }

                    if (needsDelayedUpdate) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            updateAppWidgetInternal(context, appWidgetManager, targetAppWidgetId, isVisible)
                        }, 150)
                    }
                }
            }
            ACTION_REFRESH_VISUALIZER -> {
                if (targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && targetAppWidgetId in currentWidgetIds) {
                    forceVisualizerRefresh(context, appWidgetManager, targetAppWidgetId)
                }
            }
        }
    }


    private fun forceVisualizerRefresh(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val drawer = visualizerDrawers[appWidgetId] ?: return
        val isPlaying = getMediaController(context)?.playbackState?.state == PlaybackState.STATE_PLAYING
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val vizEnabledKey = PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY
        val isVisualizationGloballyEnabled = prefs.getBoolean(vizEnabledKey, true)
        val hasPermissions = hasRequiredPermissions(context)

        val shouldVisualize = isPlaying && isVisualizationGloballyEnabled && hasPermissions

        if (shouldVisualize) {
            stopVisualizerUpdates(appWidgetId)
            drawer.resume()
            startVisualizerUpdates(context, appWidgetManager, appWidgetId, drawer)
        } else {
            stopVisualizerUpdates(appWidgetId)
            drawer.pause()

            val partialViews = RemoteViews(context.packageName, R.layout.music_simple_widget)
            val savedBitmap = loadBitmapFromFile(getVisualizerBitmapFile(context, appWidgetId))

            if (savedBitmap != null && !savedBitmap.isRecycled && isVisualizationGloballyEnabled && hasPermissions) {
                partialViews.setImageViewBitmap(R.id.visualizer_image_view, savedBitmap)
                partialViews.setViewVisibility(R.id.visualizer_image_view, View.VISIBLE)
            } else {
                partialViews.setViewVisibility(R.id.visualizer_image_view, View.GONE)
            }
            try {
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
            } catch (e: Exception) {

            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startMediaMonitorService(context)
    }


    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        appWidgetIds.forEach { appWidgetId ->
            stopVisualizerUpdates(appWidgetId)
            visualizerDrawers.remove(appWidgetId)?.release()
            getAlbumArtFile(context, appWidgetId).delete()
            getVisualizerBitmapFile(context, appWidgetId).delete()

            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_LAST_TITLE_KEY)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ARTIST_KEY)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ART_PATH_KEY)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY)
        }
        editor.apply()

        if (getWidgetIds(context).isEmpty()) {
            try { context.stopService(Intent(context, MediaMonitorService::class.java)) }
            catch (e: Exception) {

            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        try { context.stopService(Intent(context, MediaMonitorService::class.java)) }
        catch (e: Exception) {

        }

        val allWidgetIds = getWidgetIds(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        allWidgetIds.forEach { appWidgetId ->
            stopVisualizerUpdates(appWidgetId)
            visualizerDrawers.remove(appWidgetId)?.release()
            getAlbumArtFile(context, appWidgetId).delete()
            getVisualizerBitmapFile(context, appWidgetId).delete()
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_LAST_TITLE_KEY)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ARTIST_KEY)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ART_PATH_KEY)
            editor.remove(PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY)
        }
        updateHandlers.clear()
        updateRunnables.clear()
        visualizerDrawers.clear()

        editor.apply()
    }

    fun Int.dpToPx(context: Context): Int {
        if (this <= 0) return 0
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
    }
}