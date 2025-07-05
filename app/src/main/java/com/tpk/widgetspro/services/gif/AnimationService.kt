package com.tpk.widgetspro.services.gif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.io.FileNotFoundException

class AnimationService : BaseMonitorService() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val widgetData = mutableMapOf<Int, WidgetAnimationData>()
    private val syncGroups = mutableMapOf<String, SyncGroupData>()

    data class Frame(val bitmap: Bitmap, val duration: Int)

    data class WidgetAnimationData(
        var frames: List<Frame>? = null,
        var currentFrame: Int = 0,
        var filePath: String? = null,
        var syncGroupId: String? = null,
        var job: Job? = null
    )

    data class SyncGroupData(
        val widgetIds: MutableSet<Int> = mutableSetOf(),
        var currentFrame: Int = 0,
        var job: Job? = null
    )

    private val visibilityResumedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_VISIBILITY_RESUMED) {
                restartAllAnimations()
            }
        }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                restartAllAnimations()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    private fun restartAllAnimations() {
        widgetData.values.forEach { data ->
            data.job?.cancel()
        }
        syncGroups.values.forEach { group ->
            group.job?.cancel()
        }
        widgetData.forEach { (appWidgetId, data) ->
            if (data.syncGroupId == null) {
                startAnimation(appWidgetId)
            }
        }
        syncGroups.keys.forEach { syncGroupId ->
            startSyncAnimation(syncGroupId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "BOOT_COMPLETED" -> serviceScope.launch { initializeAllWidgets() }
            "ADD_WIDGET" -> serviceScope.launch {
                handleAddWidget(
                    intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                    intent.getStringExtra("file_path")
                )
            }
            "REMOVE_WIDGET" -> serviceScope.launch {
                handleRemoveWidget(
                    intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                )
            }
            "UPDATE_FILE" -> serviceScope.launch {
                handleUpdateFile(
                    intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                    intent.getStringExtra("file_path")
                )
            }
            "SYNC_WIDGETS" -> serviceScope.launch {
                handleSyncWidgets(
                    intent.getStringExtra("sync_group_id"),
                    intent.getIntArrayExtra("sync_widget_ids")?.toSet() ?: emptySet()
                )
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun initializeAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this@AnimationService)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this@AnimationService, GifWidgetProvider::class.java)
        )

        val deviceContext = createDeviceProtectedStorageContext()
        val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)

        for (appWidgetId in widgetIds) {
            val filePath = prefs.getString("file_path_$appWidgetId", null)
            if (filePath != null && File(filePath).exists()) {
                handleAddWidget(appWidgetId, filePath)
            }
        }
    }

    private suspend fun handleAddWidget(appWidgetId: Int, filePath: String?) {
        if (appWidgetId == -1 || filePath == null) return
        if (widgetData.containsKey(appWidgetId) && widgetData[appWidgetId]?.filePath == filePath) return

        val frames = withContext(Dispatchers.IO) { decodeGifToFrames(File(filePath)) }
        if (frames.isEmpty()) return

        val deviceContext = createDeviceProtectedStorageContext()
        val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
        val syncGroupId = prefs.getString("sync_group_$appWidgetId", null)

        val data = WidgetAnimationData(
            frames = frames,
            filePath = filePath,
            syncGroupId = syncGroupId
        )

        widgetData[appWidgetId] = data

        if (syncGroupId != null) {
            val group = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
            group.widgetIds.add(appWidgetId)
            startSyncAnimation(syncGroupId)
        } else {
            startAnimation(appWidgetId)
        }
    }

    private fun handleRemoveWidget(appWidgetId: Int) {
        if (appWidgetId == -1) return

        widgetData.remove(appWidgetId)?.let { data ->
            data.job?.cancel()
            recycleFrames(data.frames)
            data.syncGroupId?.let { syncGroupId ->
                syncGroups[syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.job?.cancel()
                        syncGroups.remove(syncGroupId)
                    }
                }
            }
        }

        if (widgetData.isEmpty() && syncGroups.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private suspend fun handleUpdateFile(appWidgetId: Int, filePath: String?) {
        if (appWidgetId == -1 || filePath == null) return

        widgetData[appWidgetId]?.let { oldData ->
            oldData.job?.cancel()
            recycleFrames(oldData.frames)
            oldData.syncGroupId?.let { oldSyncId ->
                syncGroups[oldSyncId]?.widgetIds?.remove(appWidgetId)
                syncGroups[oldSyncId]?.takeIf { it.widgetIds.isEmpty() }?.let {
                    it.job?.cancel()
                    syncGroups.remove(oldSyncId)
                }
            }
        }

        val newFrames = withContext(Dispatchers.IO) { decodeGifToFrames(File(filePath)) }
        if (newFrames.isNotEmpty()) {
            val deviceContext = createDeviceProtectedStorageContext()
            val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
            val newSyncGroupId = prefs.getString("sync_group_$appWidgetId", null)

            val newData = WidgetAnimationData(
                frames = newFrames,
                filePath = filePath,
                syncGroupId = newSyncGroupId
            )

            widgetData[appWidgetId] = newData

            if (newSyncGroupId != null) {
                val group = syncGroups.getOrPut(newSyncGroupId) { SyncGroupData() }
                group.widgetIds.add(appWidgetId)
                startSyncAnimation(newSyncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        } else {
            handleRemoveWidget(appWidgetId)
        }
    }

    private suspend fun handleSyncWidgets(syncGroupId: String?, widgetIdsToSync: Set<Int>) {
        if (syncGroupId == null) return

        val affectedSyncGroups = mutableSetOf<String>()
        widgetIdsToSync.forEach { appWidgetId ->
            widgetData[appWidgetId]?.let { data ->
                data.job?.cancel()
                data.job = null
                data.syncGroupId?.let {
                    if (it != syncGroupId) {
                        affectedSyncGroups.add(it)
                        syncGroups[it]?.widgetIds?.remove(appWidgetId)
                    }
                }
                data.syncGroupId = syncGroupId
            }
        }

        affectedSyncGroups.forEach { oldSyncId ->
            syncGroups[oldSyncId]?.let { group ->
                if (group.widgetIds.isEmpty()) {
                    group.job?.cancel()
                    syncGroups.remove(oldSyncId)
                } else {
                    startSyncAnimation(oldSyncId)
                }
            }
        }

        val deviceContext = createDeviceProtectedStorageContext()
        val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE).edit()
        widgetIdsToSync.forEach { appWidgetId ->
            prefs.putString("sync_group_$appWidgetId", syncGroupId)
        }
        prefs.apply()

        val syncGroup = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
        syncGroup.widgetIds.clear()
        syncGroup.widgetIds.addAll(widgetIdsToSync)
        syncGroup.currentFrame = 0
        startSyncAnimation(syncGroupId)

        widgetData.forEach { (appWidgetId, data) ->
            if (data.syncGroupId == null && data.job == null) {
                startAnimation(appWidgetId)
            }
        }
    }

    private fun startAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames
            if (frames.isNullOrEmpty()) return
            data.job?.cancel()
            data.job = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    updateWidget(appWidgetId)
                    val frameDuration = frames[data.currentFrame].duration.toLong().coerceAtLeast(1L)
                    delay(frameDuration)
                    data.currentFrame = (data.currentFrame + 1) % frames.size
                }
            }
        }
    }

    private fun startSyncAnimation(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.widgetIds.isEmpty()) {
                syncGroups.remove(syncGroupId)
                return
            }
            group.job?.cancel()
            group.job = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    updateSyncGroup(syncGroupId)
                    val minFrameDuration = group.widgetIds
                        .mapNotNull { widgetData[it]?.frames?.getOrNull(group.currentFrame)?.duration?.toLong() }
                        .minOrNull() ?: 100L
                    delay(minFrameDuration.coerceAtLeast(1L))
                }
            }
        }
    }

    private fun updateWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames ?: return
            if (frames.isEmpty()) return

            val frame = frames[data.currentFrame]
            if (frame.bitmap.isRecycled) {
                handleRemoveWidget(appWidgetId)
                return
            }

            try {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
                val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val marginEnabled = prefs.getBoolean("gif_margin_enabled", false)
                val isTransparent = prefs.getBoolean("gif_background_transparent", false)
                val padding = if (marginEnabled) (9 * resources.displayMetrics.density).toInt() else 0
                remoteViews.setViewPadding(R.id.gif_widget_container, padding, padding, padding, padding)
                if (!isTransparent) {
                    remoteViews.setInt(R.id.gif_widget_container, "setBackgroundResource", android.R.color.transparent)
                } else {
                    remoteViews.setInt(R.id.gif_widget_container, "setBackgroundResource", R.color.shape_background_color)
                }
                remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            } catch (e: Exception) {
                handleRemoveWidget(appWidgetId)
            }
        }
    }

    private fun updateSyncGroup(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.widgetIds.isEmpty()) return

            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetsToRemove = mutableListOf<Int>()
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val marginEnabled = prefs.getBoolean("gif_margin_enabled", false)
            val isTransparent = prefs.getBoolean("gif_background_transparent", false)
            val padding = if (marginEnabled) (9 * resources.displayMetrics.density).toInt() else 0
            group.widgetIds.forEach { appWidgetId ->
                widgetData[appWidgetId]?.let { data ->
                    val frames = data.frames ?: run {
                        widgetsToRemove.add(appWidgetId)
                        return@forEach
                    }

                    if (frames.isEmpty()) {
                        widgetsToRemove.add(appWidgetId)
                        return@forEach
                    }

                    val frameIndex = group.currentFrame % frames.size
                    val frame = frames[frameIndex]

                    if (frame.bitmap.isRecycled) {
                        widgetsToRemove.add(appWidgetId)
                        return@forEach
                    }

                    try {
                        val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
                        if (!isTransparent) {
                            remoteViews.setInt(R.id.gif_widget_container, "setBackgroundResource", android.R.color.transparent)
                        } else {
                            remoteViews.setInt(R.id.gif_widget_container, "setBackgroundResource", R.color.shape_background_color)
                        }
                        remoteViews.setViewPadding(R.id.gif_widget_container, padding, padding, padding, padding)
                        remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                        data.currentFrame = frameIndex
                    } catch (e: Exception) {
                        widgetsToRemove.add(appWidgetId)
                    }
                } ?: run {
                    widgetsToRemove.add(appWidgetId)
                }
            }

            widgetsToRemove.forEach { handleRemoveWidget(it) }
            group.widgetIds.removeAll(widgetsToRemove)

            if (group.widgetIds.isEmpty()) {
                syncGroups.remove(syncGroupId)
                return
            }

            val maxFrameCount = group.widgetIds
                .mapNotNull { widgetData[it]?.frames?.size }
                .maxOrNull() ?: 0

            if (maxFrameCount > 0) {
                group.currentFrame = (group.currentFrame + 1) % maxFrameCount
            }
        }
    }

    private fun decodeGifToFrames(gifFile: File): List<Frame> {
        if (!gifFile.exists()) return emptyList()
        return try {
            val gifDrawable = GifDrawable(gifFile)
            val frames = mutableListOf<Frame>()
            val numberOfFrames = gifDrawable.numberOfFrames

            for (i in 0 until numberOfFrames) {
                val bitmap = gifDrawable.seekToFrameAndGet(i)?.copy(Bitmap.Config.ARGB_8888, false)
                val duration = gifDrawable.getFrameDuration(i)
                if (bitmap != null) {
                    frames.add(Frame(bitmap, duration))
                }
            }
            gifDrawable.recycle()
            frames
        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException, is OutOfMemoryError -> emptyList()
                else -> emptyList()
            }
        }
    }

    private fun recycleFrames(frames: List<Frame>?) {
        frames?.forEach { frame ->
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        widgetData.values.forEach { recycleFrames(it.frames) }
        widgetData.clear()
        syncGroups.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}