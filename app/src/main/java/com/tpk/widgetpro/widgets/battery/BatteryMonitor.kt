package com.tpk.widgetpro.widgets.battery

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

    fun startMonitoring(intervalSeconds: Int) {
        executorService.scheduleAtFixedRate({
            val percentage = batteryPercentage()
            val health = batteryCycleCount()
            callback(percentage, health)
        }, 0, intervalSeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun batteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun batteryCycleCount(): Int {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val cycleCount = batteryStatus?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        return cycleCount
    }

    fun stopMonitoring() {
        executorService.shutdown()
    }
}