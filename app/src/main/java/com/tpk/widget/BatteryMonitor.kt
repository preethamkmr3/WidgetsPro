package com.tpk.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class BatteryMonitor(
    private val context: Context,
    private val callback: (percentage: Int, health: Int) -> Unit
) {
    private val executorService: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()

    // Start monitoring with a 60-second interval to save battery
    fun startMonitoring() {
        executorService.scheduleAtFixedRate({
            try {
                val percentage = getBatteryPercentage()
                val health = getBatteryCycleCount()
                callback(percentage, health)
            } catch (e: Exception) {
                // Log error or notify user if needed
            }
        }, 0, 60, TimeUnit.SECONDS)
    }

    private fun getBatteryPercentage(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    }

    private fun getBatteryCycleCount(): Int {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        return batteryStatus?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
    }

    fun stopMonitoring() {
        executorService.shutdown()
    }
}