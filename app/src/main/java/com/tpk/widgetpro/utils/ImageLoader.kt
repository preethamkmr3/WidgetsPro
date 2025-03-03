package com.tpk.widgetpro.utils

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.tpk.widgetpro.R
import com.tpk.widgetpro.api.ImageApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ImageLoader(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val appWidgetId: Int,
    private val views: RemoteViews
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun loadImageAsync(device: android.bluetooth.BluetoothDevice) {
        scope.launch {
            try {
                val imageUrl = ImageApiClient.getCachedUrl(context, device.name)
                    ?: ImageApiClient.getImageUrl(context, device.name).also {
                        if (it.isNotEmpty()) ImageApiClient.cacheUrl(context, device.name, it)
                    }

                if (imageUrl.isNotEmpty()) {
                    downloadBitmap(imageUrl)?.let { bitmap ->
                        BitmapCacheManager.cacheBitmap(context, device.name, bitmap)
                        withContext(Dispatchers.Main) {
                            views.setImageViewBitmap(R.id.device_image, bitmap)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
