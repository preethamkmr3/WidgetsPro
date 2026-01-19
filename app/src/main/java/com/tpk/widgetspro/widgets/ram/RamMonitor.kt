package com.tpk.widgetspro.widgets.ram

import android.app.ActivityManager
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RamMonitor(private val context: Context, private val callback: (Double, String, String) -> Unit) {
    private var executorService: ScheduledExecutorService? = null
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var currentInterval = 60

    fun startMonitoring(initialInterval: Int) {
        currentInterval = initialInterval
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService?.execute {
            performMonitoring()
            scheduleNextRun()
        }
    }

    private fun performMonitoring() {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        val totalMem = memoryInfo.totalMem
        val availMem = memoryInfo.availMem
        val usedMem = totalMem - availMem

        val percent = (usedMem.toDouble() / totalMem.toDouble()) * 100.0
        
        // Format strings
        val totalStr = formatSize(totalMem)
        val availStr = formatSize(availMem)

        callback(percent, totalStr, availStr)
    }

    private fun formatSize(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return "%.1fGB".format(gb)
    }

    private fun scheduleNextRun() {
        scheduledFuture = executorService?.schedule({
            performMonitoring()
            scheduleNextRun()
        }, currentInterval.toLong(), TimeUnit.SECONDS)
    }

    fun updateInterval(newInterval: Int) {
        currentInterval = newInterval.coerceAtLeast(1)
        scheduledFuture?.cancel(false)
        scheduleNextRun()
    }

    fun stopMonitoring() {
        scheduledFuture?.cancel(false)
        executorService?.shutdown()
        executorService = null
        scheduledFuture = null
    }
}
