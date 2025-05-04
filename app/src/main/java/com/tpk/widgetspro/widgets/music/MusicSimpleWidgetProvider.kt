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
        const val ACTION_PLAY_PAUSE = "com.example.musicsimplewidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicsimplewidget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.musicsimplewidget.ACTION_PREVIOUS"
        const val ACTION_MEDIA_UPDATE = "com.example.musicsimplewidget.ACTION_MEDIA_UPDATE"
        const val ACTION_TOGGLE_VISUALIZATION = "com.example.musicsimplewidget.ACTION_TOGGLE_VISUALIZATION"
        const val PREFS_NAME = "music_widget_prefs"
        const val PREF_PREFIX_KEY = "music_app_"
        const val PREF_LAST_TITLE_KEY = "_last_title"
        const val PREF_LAST_ARTIST_KEY = "_last_artist"
        const val PREF_LAST_ART_PATH_KEY = "_last_art_path"
        const val PREF_VISUALIZATION_ENABLED_KEY = "_visualization_enabled"
        const val ALBUM_ART_DIR = "album_art"

        private var lastAppLaunchTime: Long = 0
        private const val APP_LAUNCH_COOLDOWN_MS: Long = 3000

        private val visualizerDrawers = ConcurrentHashMap<Int, MusicVisualizerDrawer>()
        private val updateHandlers = ConcurrentHashMap<Int, Handler>()
        private val updateRunnables = ConcurrentHashMap<Int, Runnable>()
        private const val VISUALIZER_UPDATE_INTERVAL_MS = 60L

        private fun getWidgetIds(context: Context, appWidgetManager: AppWidgetManager): IntArray {
            return appWidgetManager.getAppWidgetIds(ComponentName(context, MusicSimpleWidgetProvider::class.java))
        }

        private fun getAlbumArtFile(context: Context, appWidgetId: Int): File {
            val cacheDir = File(context.cacheDir, ALBUM_ART_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return File(cacheDir, "widget_${appWidgetId}_art.png")
        }

        private fun saveBitmapToFile(bitmap: Bitmap?, file: File): Boolean {
            if (bitmap == null || bitmap.isRecycled) return false
            var success = false
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
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
            val minWidthDp = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val safeMinWidthDp = if (minWidthDp > 0) minWidthDp else 150
            val visualizerWidthPx = safeMinWidthDp.dpToPx(context)
            val visualizerHeightPx = 30.dpToPx(context)
            drawer.updateDimensions(visualizerWidthPx, visualizerHeightPx)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isWidgetVisible = true
        val isVisualizationGloballyEnabled = prefs.getBoolean(PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY, true)
        updateAppWidgetInternal(context, appWidgetManager, appWidgetId, drawer, isWidgetVisible, isVisualizationGloballyEnabled)
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
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            val visualizerWidthPx = minWidthDp.dpToPx(context)
            val visualizerHeightPx = 30.dpToPx(context)
            drawer.updateDimensions(visualizerWidthPx, visualizerHeightPx)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isWidgetVisible = true
            val isVisualizationGloballyEnabled = prefs.getBoolean(PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY, true)
            updateAppWidgetInternal(context, appWidgetManager, appWidgetId, drawer, isWidgetVisible, isVisualizationGloballyEnabled)
        }
        if (appWidgetIds.isNotEmpty()) {
            startMediaMonitorService(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action ?: return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = getWidgetIds(context, appWidgetManager)

        if (appWidgetIds.isEmpty()) {
            return
        }

        val targetAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isVisible = intent.getBooleanExtra("WIDGET_VISIBLE", true)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (action == ACTION_TOGGLE_VISUALIZATION && targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val enabledKey = PREF_PREFIX_KEY + targetAppWidgetId + PREF_VISUALIZATION_ENABLED_KEY
            val currentEnabled = prefs.getBoolean(enabledKey, true)
            prefs.edit().putBoolean(enabledKey, !currentEnabled).apply()
            val drawer = visualizerDrawers.getOrPut(targetAppWidgetId) { MusicVisualizerDrawer(context.applicationContext) }
            updateAppWidgetInternal(context, appWidgetManager, targetAppWidgetId, drawer, isVisible, !currentEnabled)

        } else if (action == ACTION_MEDIA_UPDATE) {
            appWidgetIds.forEach { id ->
                val drawer = visualizerDrawers.getOrPut(id) { MusicVisualizerDrawer(context.applicationContext) }
                val isVisualizationGloballyEnabled = prefs.getBoolean(PREF_PREFIX_KEY + id + PREF_VISUALIZATION_ENABLED_KEY, true)
                updateAppWidgetInternal(context, appWidgetManager, id, drawer, isVisible, isVisualizationGloballyEnabled)
            }
        } else if (targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && targetAppWidgetId in appWidgetIds) {
            val mediaController = getMediaController(context)
            var needsUpdate = false
            when (action) {
                ACTION_PLAY_PAUSE -> {
                    handlePlayPause(context, mediaController, targetAppWidgetId)
                    needsUpdate = true
                }
                ACTION_NEXT -> {
                    mediaController?.transportControls?.skipToNext()
                    needsUpdate = true
                }
                ACTION_PREVIOUS -> {
                    mediaController?.transportControls?.skipToPrevious()
                    needsUpdate = true
                }
            }
            if(needsUpdate) {
                val drawer = visualizerDrawers.getOrPut(targetAppWidgetId) { MusicVisualizerDrawer(context.applicationContext) }
                val isVisualizationGloballyEnabled = prefs.getBoolean(PREF_PREFIX_KEY + targetAppWidgetId + PREF_VISUALIZATION_ENABLED_KEY, true)
                updateAppWidgetInternal(context, appWidgetManager, targetAppWidgetId, drawer, true, isVisualizationGloballyEnabled)
            }
        }
    }

    private fun updateAppWidgetInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        visualizerDrawer: MusicVisualizerDrawer,
        isWidgetVisible: Boolean,
        isVisualizationGloballyEnabled: Boolean
    ) {
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
                views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                editor.remove(artPathKey)
                artFile.delete()
            }

            isPlaying = playbackState.state == PlaybackState.STATE_PLAYING || playbackState.state == PlaybackState.STATE_BUFFERING
            views.setImageViewResource(R.id.button_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        } else {
            val lastTitle = prefs.getString(titleKey, null)
            if (lastTitle != null) {
                val lastArtist = prefs.getString(artistKey, "Unknown Artist")
                val lastArtPath = prefs.getString(artPathKey, null)

                views.setTextViewText(R.id.text_title, lastTitle)
                views.setTextViewText(R.id.text_artist, lastArtist)

                var loadedBitmap: Bitmap? = null
                if (lastArtPath != null) {
                    loadedBitmap = loadBitmapFromFile(File(lastArtPath))
                }

                if (loadedBitmap != null && !loadedBitmap.isRecycled) {
                    views.setImageViewBitmap(R.id.image_album_art, loadedBitmap)
                } else {
                    views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                }
                views.setImageViewResource(R.id.button_play_pause, R.drawable.ic_play_arrow)

            } else {
                setNoMusicPlaying(views)
            }
            isPlaying = false
        }
        editor.apply()

        val shouldVisualize = isPlaying && isWidgetVisible && isVisualizationGloballyEnabled

        if (hasRequiredPermissions(context)) {
            visualizerDrawer.linkToGlobalOutput()

            if (shouldVisualize) {
                visualizerDrawer.resume()
                views.setViewVisibility(R.id.visualizer_image_view, View.VISIBLE)
                startVisualizerUpdates(context, appWidgetManager, appWidgetId, visualizerDrawer)
            } else {
                visualizerDrawer.pause()
                stopVisualizerUpdates(appWidgetId)
                views.setViewVisibility(R.id.visualizer_image_view, View.GONE)
            }
        } else {
            visualizerDrawer.pause()
            stopVisualizerUpdates(appWidgetId)
            views.setViewVisibility(R.id.visualizer_image_view, View.GONE)
            visualizerDrawer.release()
        }

        setupPendingIntents(context, views, appWidgetId)

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {

        }
    }

    private fun createEmptyBitmap(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.TRANSPARENT)
            }
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val recordAudioGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val modifyAudioGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED
        return recordAudioGranted && modifyAudioGranted
    }

    private fun startVisualizerUpdates(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        visualizerDrawer: MusicVisualizerDrawer
    ) {
        if (updateHandlers.containsKey(appWidgetId)) {
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (!updateHandlers.containsKey(appWidgetId)) {
                    return
                }

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
        updateHandlers.remove(appWidgetId)?.removeCallbacks(updateRunnables.remove(appWidgetId) ?: Runnable {})
    }

    private fun setNoMusicPlaying(views: RemoteViews) {
        views.setTextViewText(R.id.text_title, "No music playing")
        views.setTextViewText(R.id.text_artist, "")
        views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
        views.setImageViewResource(R.id.button_play_pause, R.drawable.ic_play_arrow)
        views.setViewVisibility(R.id.visualizer_image_view, View.GONE)
    }

    private fun setupPendingIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
        val playPauseReqCode = appWidgetId * 10 + 0
        val nextReqCode = appWidgetId * 10 + 1
        val prevReqCode = appWidgetId * 10 + 2
        val launchReqCode = appWidgetId * 10 + 3
        val toggleVisReqCode = appWidgetId * 10 + 4

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

        val launchIntent = getLaunchMusicAppIntent(context, appWidgetId)
        val launchPendingIntent = PendingIntent.getActivity(
            context, launchReqCode, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleVisIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_VISUALIZATION
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://$appWidgetId/togglevis")
        }
        val toggleVisPendingIntent = PendingIntent.getBroadcast(
            context, toggleVisReqCode, toggleVisIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.button_play_pause, playPausePendingIntent)
        views.setOnClickPendingIntent(R.id.button_next, nextPendingIntent)
        views.setOnClickPendingIntent(R.id.button_previous, prevPendingIntent)
        views.setOnClickPendingIntent(R.id.image_album_art, launchPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_container, toggleVisPendingIntent)
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

    private fun getLaunchMusicAppIntent(context: Context, appWidgetId: Int): Intent {
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
            launchMusicApp(context, appWidgetId)
        }
    }

    private fun launchMusicApp(context: Context, appWidgetId: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAppLaunchTime < APP_LAUNCH_COOLDOWN_MS) {
            return
        }
        try {
            val intent = getLaunchMusicAppIntent(context, appWidgetId)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                lastAppLaunchTime = currentTime
            }
        } catch (e: Exception) {

        }
    }

    private fun startMediaMonitorService(context: Context) {
        val serviceIntent = Intent(context, MediaMonitorService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            try { context.startService(serviceIntent) } catch (se: Exception) { }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        appWidgetIds.forEach { appWidgetId ->
            val titleKey = PREF_PREFIX_KEY + appWidgetId + PREF_LAST_TITLE_KEY
            val artistKey = PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ARTIST_KEY
            val artPathKey = PREF_PREFIX_KEY + appWidgetId + PREF_LAST_ART_PATH_KEY
            val enabledKey = PREF_PREFIX_KEY + appWidgetId + PREF_VISUALIZATION_ENABLED_KEY

            editor.remove(titleKey)
            editor.remove(artistKey)
            editor.remove(enabledKey)
            prefs.getString(artPathKey, null)?.let { path ->
                File(path).delete()
            }
            editor.remove(artPathKey)

            stopVisualizerUpdates(appWidgetId)
            visualizerDrawers.remove(appWidgetId)?.release()
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (getWidgetIds(context, appWidgetManager).isEmpty()) {
            try { context.stopService(Intent(context, MediaMonitorService::class.java)) }
            catch (e: Exception) { }
        }
    }

    override fun onDisabled(context: Context) {
        try { context.stopService(Intent(context, MediaMonitorService::class.java)) }
        catch (e: Exception) { }

        visualizerDrawers.keys.forEach { appWidgetId ->
            stopVisualizerUpdates(appWidgetId)
            visualizerDrawers.remove(appWidgetId)?.release()
            getAlbumArtFile(context, appWidgetId).delete()
        }
        updateHandlers.clear()
        updateRunnables.clear()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREF_PREFIX_KEY) }.forEach { editor.remove(it) }
        editor.apply()

        super.onDisabled(context)
    }

    fun Int.dpToPx(context: Context): Int {
        if (this == 0) return 0
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
    }
}