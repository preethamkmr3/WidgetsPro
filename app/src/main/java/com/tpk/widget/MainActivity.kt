package com.tpk.widget

import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private val SHIZUKU_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationUtils.createAppWidgetChannel(this)
        setupUI()
    }

    private fun setupUI() {
        findViewById<Button>(R.id.button1).setOnClickListener {
            checkPermissions()
            if (hasShizukuAccess() || hasRootAccess()) {
                val cpuWidgetProvider = ComponentName(this, CpuWidgetProvider::class.java)
                requestWidgetInstallation(cpuWidgetProvider)
            }
        }
        findViewById<Button>(R.id.button2).setOnClickListener {
            val batteryWidgetProvider = ComponentName(this, BatteryWidgetProvider::class.java)
            requestWidgetInstallation(batteryWidgetProvider)
        }
        findViewById<ImageView>(R.id.imageViewButton).setOnClickListener {
            checkPermissions()
            Toast.makeText(this, "Widget refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestWidgetInstallation(provider: ComponentName) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                val requestCode = System.currentTimeMillis().toInt()
                val intent = Intent().setComponent(provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                val successCallback = PendingIntent.getBroadcast(
                    this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val pinned = appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                if (!pinned) Toast.makeText(this, R.string.widget_pin_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to add widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        when {
            hasRootAccess() -> startServiceAndFinish(true)
            Shizuku.pingBinder() -> {
                if (hasShizukuAccess()) startServiceAndFinish(false)
                else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
            else -> showPermissionDialog()
        }
    }

    private fun startServiceAndFinish(useRoot: Boolean) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, CpuMonitorService::class.java).apply { putExtra("use_root", useRoot) }
        )
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.retry) { _, _ -> checkPermissions() }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startServiceAndFinish(false)
        } else {
            Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun hasRootAccess(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
                val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
                process.destroy()
                output != null
            } catch (e: Exception) {
                false
            }
        }

        fun hasShizukuAccess(): Boolean {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }
}