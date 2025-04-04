package com.tpk.widgetspro.utils


import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import android.app.usage.NetworkStatsManager
import android.content.SharedPreferences
import java.util.Calendar

object NetworkStatsHelper {

    @Throws(RemoteException::class)
    fun getSimDataUsage(context: Context): LongArray {
        val (startTime, endTime) = getTimeRange(context)
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val bucket = networkStatsManager.querySummaryForDevice(
            ConnectivityManager.TYPE_MOBILE,
            null,
            startTime,
            endTime
        )
        val txBytes = bucket.txBytes
        val rxBytes = bucket.rxBytes
        val totalBytes = txBytes + rxBytes
        return longArrayOf(txBytes, rxBytes, totalBytes)
    }

    @Throws(RemoteException::class)
    fun getWifiDataUsage(context: Context): LongArray {
        val (startTime, endTime) = getTimeRange(context)
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val bucket = networkStatsManager.querySummaryForDevice(
            ConnectivityManager.TYPE_WIFI,
            null,
            startTime,
            endTime
        )
        val txBytes = bucket.txBytes
        val rxBytes = bucket.rxBytes
        val totalBytes = txBytes + rxBytes
        return longArrayOf(txBytes, rxBytes, totalBytes)
    }

    private fun getTimeRange(context: Context): Pair<Long, Long> {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val frequency = prefs.getString("data_usage_frequency", "daily") ?: "daily"

        return when (frequency) {
            "daily" -> getCustomDayRange(prefs)
            else -> getCustomMonthRange(prefs)
        }
    }

    private fun getCustomDayRange(prefs: SharedPreferences): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val defaultStart = calendar.timeInMillis
        val startTime = prefs.getLong("data_usage_start_time", defaultStart)
        val endTime = startTime + 24 * 60 * 60 * 1000

        return Pair(startTime, endTime)
    }
    private fun getCustomMonthRange(prefs: SharedPreferences): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val defaultStart = calendar.timeInMillis
        val startTime = prefs.getLong("data_usage_start_time", defaultStart)
        val endTime = startTime + 24 * 60 * 60 * 1000

        return Pair(startTime, endTime)
    }
}