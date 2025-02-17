package com.tpk.widget

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (isDeviceRooted() && testRootAccess()) {
            startCpuMonitorService(true)
        } else {
            handleShizukuPermission()
        }
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun testRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo test"))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output == "test"
        } catch (e: Exception) {
            false
        }
    }

    private fun handleShizukuPermission() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } else {
            startCpuMonitorService(false)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCpuMonitorService(false)
            } else {
                // Handle Shizuku permission denial
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun startCpuMonitorService(useRoot: Boolean) {
        val intent = Intent(this, CpuMonitorService::class.java).apply {
            putExtra("use_root", useRoot)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}