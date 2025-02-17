package com.tpk.widget

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.io.IOException

class CpuMonitor(
    private val useRoot: Boolean,
    private val callback: (cpuUsage: Double, cpuTemperature: Double) -> Unit
) {

    private var prevIdleTime: Long = 0
    private var prevNonIdleTime: Long = 0
    private var prevTotalTime: Long = 0
    private var isFirstReading = true
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var cpuThermalZone: String? = null

    fun startMonitoring() {
        executorService.scheduleAtFixedRate({
            val cpuUsage = calculateCpuUsage()
            val cpuTemperature = readCpuTemperature()
            callback(cpuUsage, cpuTemperature)
        }, 0, 1, TimeUnit.SECONDS)
    }

    fun stopMonitoring() {
        executorService.shutdown()
    }

    private fun calculateCpuUsage(): Double {
        val stat = readProcStat()
        stat?.let {
            val lines = it.lines()
            val cpuLine = lines.firstOrNull { line -> line.startsWith("cpu ") } ?: return 0.0
            val tokens = cpuLine.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }

            if (tokens.size >= 10) {
                val user = tokens[0]
                val nice = tokens[1]
                val system = tokens[2]
                val idle = tokens[3]
                val iowait = tokens[4]
                val irq = tokens[5]
                val softirq = tokens[6]
                val steal = tokens[7]

                val idleTime = idle + iowait
                val nonIdleTime = user + nice + system + irq + softirq + steal

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

                return if (totalDelta > 0) {
                    (nonIdleDelta.toDouble() / totalDelta.toDouble()) * 100.0
                } else {
                    0.0
                }
            }
        }
        return 0.0
    }

    private fun executeCommand(command: Array<String>): Process? {
        return try {
            if (useRoot) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command.joinToString(" ")))
            } else {
                Shizuku.newProcess(command, null, null)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun readProcStat(): String? {
        return try {
            val command = arrayOf("cat", "/proc/stat")
            val process = executeCommand(command) ?: return null
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.destroy()
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readCpuTemperature(): Double {
        try {
            if (cpuThermalZone == null) {
                cpuThermalZone = findCpuThermalZone()
            }
            val zone = cpuThermalZone ?: return 0.0
            val temp = readThermalZoneTemp(zone)
            if (temp != null) {
                return temp / 1000.0
            }
        } catch (e: Exception) {
            Log.e("CpuMonitor", "Error reading temperature", e)
            e.printStackTrace()
        }
        return 0.0
    }

    private fun findCpuThermalZone(): String? {
        val thermalZones = getThermalZones()
        val preferredTypes = listOf("cpu", "tsens", "processor")
        for (zone in thermalZones) {
            val type = readThermalZoneType(zone)
            if (type != null && preferredTypes.any { type.contains(it, ignoreCase = true) }) {
                return zone
            }
        }
        return thermalZones.firstOrNull()
    }

    private fun getThermalZones(): List<String> {
        val zones = mutableListOf<String>()
        try {
            val process = executeCommand(arrayOf("ls", "/sys/class/thermal/")) ?: return emptyList()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.destroy()
            val files = output.lines()
            for (file in files) {
                if (file.startsWith("thermal_zone")) {
                    zones.add(file)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return zones
    }

    private fun readThermalZoneType(zone: String): String? {
        try {
            val path = "/sys/class/thermal/$zone/type"
            val process = executeCommand(arrayOf("cat", path)) ?: return null
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val type = reader.readLine()
            reader.close()
            process.destroy()
            return type
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun readThermalZoneTemp(zone: String): Double? {
        try {
            val path = "/sys/class/thermal/$zone/temp"
            val process = executeCommand(arrayOf("cat", path)) ?: return null
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val tempStr = reader.readLine()
            reader.close()
            process.destroy()
            return tempStr?.toDoubleOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}