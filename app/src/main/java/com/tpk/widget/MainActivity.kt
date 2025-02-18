package com.tpk.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationUtils.createAppWidgetChannel(this)

        setupUI()
    }

    private fun setupUI() {
        findViewById<Button>(R.id.btn_retry).setOnClickListener {
            checkPermissions()
        }
        findViewById<Button>(R.id.button1).setOnClickListener {
            val cpuWidgetProvider = ComponentName(this, CpuWidgetProvider::class.java)
            requestWidgetInstallation(cpuWidgetProvider)
        }
    }

    private fun requestWidgetInstallation(provider: ComponentName) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(this, CpuWidgetProvider::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                appWidgetManager.requestPinAppWidget(provider, null, successCallback)
            } else {
                Toast.makeText(this, "Widget pinning not supported", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to add widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        when {
            hasRootAccess() -> startServiceAndFinish(true)
            hasShizukuAccess() -> startServiceAndFinish(false)
            else -> showPermissionDialog()
        }
    }

    private fun startServiceAndFinish(useRoot: Boolean) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, CpuMonitorService::class.java).apply {
                putExtra("use_root", useRoot)
            }
        )
        finish()
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Please grant root or Shizuku permissions to continue")
            .setPositiveButton("Retry") { _, _ -> checkPermissions() }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServiceAndFinish(false)
            } else {
                Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {

        fun hasRootAccess(): Boolean {
            return try {
                Runtime.getRuntime().exec("su -c exit").waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }

        fun hasShizukuAccess(): Boolean {
            return Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }
}