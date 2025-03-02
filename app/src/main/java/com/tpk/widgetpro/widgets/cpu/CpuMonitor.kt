package com.tpk.widgetpro.widgets.cpu

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CpuMonitor(private val useRoot: Boolean, private val callback: (cpuUsage: Double, cpuTemperature: Double) -> Unit) {
    private var prevIdleTime: Long = 0
    private var prevNonIdleTime: Long = 0
    private var prevTotalTime: Long = 0
    private var isFirstReading = true
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var cpuThermalZone: String? = null

    fun startMonitoring(intervalSeconds: Int) {
        executorService.scheduleAtFixedRate({
            val cpuUsage = calculateCpuUsage()
            val cpuTemperature = readCpuTemperature()
            callback(cpuUsage, cpuTemperature)
        }, 0, intervalSeconds.toLong(), TimeUnit.SECONDS)
    }

    fun stopMonitoring() {
        executorService.shutdown()
    }

    private fun calculateCpuUsage(): Double {
        val stat = readProcStat() ?: return 0.0
        val cpuLine = stat.lines().firstOrNull { it.startsWith("cpu ") } ?: return 0.0
        val tokens = cpuLine.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }

        if (tokens.size >= 10) {
            val idleTime = tokens[3] + tokens[4]
            val nonIdleTime = tokens[0] + tokens[1] + tokens[2] + tokens[5] + tokens[6] + tokens[7]
            val totalTime = idleTime + nonIdleTime

            if (isFirstReading) {
                prevIdleTime = idleTime
                prevNonIdleTime = nonIdleTime
                prevTotalTime = totalTime
                isFirstReading = false
                return 0.0
            }

            val totalDelta = totalTime - prevTotalTime
            val nonIdleDelta = nonIdleTime - prevNonIdleTime

            prevIdleTime = idleTime
            prevNonIdleTime = nonIdleTime
            prevTotalTime = totalTime

            return if (totalDelta > 0) (nonIdleDelta.toDouble() / totalDelta) * 100.0 else 0.0
        }
        return 0.0
    }

    private fun executeCommand(command: Array<String>): Process? {
        return try {
            if (useRoot) Runtime.getRuntime().exec(arrayOf("su", "-c", command.joinToString(" ")))
            else rikka.shizuku.Shizuku.newProcess(command, null, null)
        } catch (e: IOException) {
            null
        }
    }

    private fun readProcStat(): String? {
        val process = executeCommand(arrayOf("cat", "/proc/stat")) ?: return null
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.destroy()
        return output
    }

    private fun readCpuTemperature(): Double {
        if (cpuThermalZone == null) cpuThermalZone = findCpuThermalZone()
        val zone = cpuThermalZone ?: return 0.0
        return readThermalZoneTemp(zone)?.div(1000.0) ?: 0.0
    }

    private fun findCpuThermalZone(): String? {
        val zones = getThermalZones()
        val preferredTypes = listOf("cpu", "tsens", "processor")
        for (zone in zones) {
            val type = readThermalZoneType(zone)
            if (type != null && preferredTypes.any { type.contains(it, ignoreCase = true) }) return zone
        }
        return zones.firstOrNull()
    }

    private fun getThermalZones(): List<String> {
        val process = executeCommand(arrayOf("ls", "/sys/class/thermal/")) ?: return emptyList()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.destroy()
        return output.lines().filter { it.startsWith("thermal_zone") }
    }

    private fun readThermalZoneType(zone: String): String? {
        val process = executeCommand(arrayOf("cat", "/sys/class/thermal/$zone/type")) ?: return null
        val type = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
        process.destroy()
        return type
    }

    private fun readThermalZoneTemp(zone: String): Double? {
        val process = executeCommand(arrayOf("cat", "/sys/class/thermal/$zone/temp")) ?: return null
        val tempStr = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
        process.destroy()
        return tempStr?.toDoubleOrNull()
    }
}