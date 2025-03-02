package com.tpk.widgetpro.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object BitmapCacheManager {
    private const val CACHE_DIR = "device_images"
    private const val WEEK_IN_MILLIS = 604800000L

    fun getCachedBitmap(context: Context, deviceName: String): Bitmap? {
        val file = File(getCacheDir(context), "${deviceName.hashCode()}.png")
        if (!file.exists()) return null
        return if (System.currentTimeMillis() - file.lastModified() < WEEK_IN_MILLIS) BitmapFactory.decodeFile(file.absolutePath)
        else {
            file.delete()
            null
        }
    }

    fun cacheBitmap(context: Context, deviceName: String, bitmap: Bitmap) {
        val file = File(getCacheDir(context), "${deviceName.hashCode()}.png")
        try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun clearExpiredCache(context: Context) {
        val dir = getCacheDir(context)
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > WEEK_IN_MILLIS) file.delete()
        }
    }
}