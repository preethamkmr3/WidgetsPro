package com.tpk.widgetpro

import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tpk.widgetpro.services.CpuMonitorService
import com.tpk.widgetpro.utils.BitmapCacheManager
import com.tpk.widgetpro.utils.NotificationUtils
import com.tpk.widgetpro.utils.PermissionUtils
import com.tpk.widgetpro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetpro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetpro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetpro.widgets.cpu.CpuWidgetProvider
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private val SHIZUKU_REQUEST_CODE = 1001
    private lateinit var seekBarCpu: SeekBar
    private lateinit var seekBarBattery: SeekBar
    private lateinit var tvCpuValue: TextView
    private lateinit var tvBatteryValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationUtils.createAppWidgetChannel(this)
        setupUI()
        BitmapCacheManager.clearExpiredCache(this)

    }

    private fun setupUI() {
        seekBarCpu = findViewById(R.id.seekBarCpu)
        seekBarBattery = findViewById(R.id.seekBarBattery)
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        seekBarCpu.progress = prefs.getInt("cpu_interval", 60)
        seekBarBattery.progress = prefs.getInt("battery_interval", 60)
        tvCpuValue = findViewById(R.id.tvCpuValue)
        tvBatteryValue = findViewById(R.id.tvBatteryValue)
        tvCpuValue.text = seekBarCpu.progress.toString()
        tvBatteryValue.text = seekBarBattery.progress.toString()
        setupSeekBarListeners(prefs)

        findViewById<Button>(R.id.button1).setOnClickListener {
            if (PermissionUtils.hasShizukuAccess() || PermissionUtils.hasRootAccess()) {
                requestWidgetInstallation(CpuWidgetProvider::class.java)
            }else
                Toast.makeText(this, "Provide Root/Shizuku access", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.button2).setOnClickListener {
            requestWidgetInstallation(BatteryWidgetProvider::class.java)
        }
        findViewById<ImageView>(R.id.imageViewButton).setOnClickListener {
            checkPermissions()
        }
        findViewById<Button>(R.id.button3).setOnClickListener {
            requestWidgetInstallation(CaffeineWidget::class.java)
        }
        findViewById<Button>(R.id.button4).setOnClickListener {
            requestWidgetInstallation(BluetoothWidgetProvider::class.java)
        }
    }

    private fun requestWidgetInstallation(providerClass: Class<*>) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, providerClass)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val requestCode = System.currentTimeMillis().toInt()
                val intent = Intent().setComponent(provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                val successCallback = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                if (!appWidgetManager.requestPinAppWidget(provider, null, successCallback)) {
                    Toast.makeText(this, R.string.widget_pin_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to add widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        when {
            PermissionUtils.hasRootAccess() -> startServiceAndFinish(true)
            Shizuku.pingBinder() -> {
                if (PermissionUtils.hasShizukuAccess()) startServiceAndFinish(false)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startServiceAndFinish(false)
        } else {
            Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_LONG).show()
        }
    }
    private fun setupSeekBarListeners(prefs: SharedPreferences) {
        seekBarCpu.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val adjustedProgress = progress.coerceAtLeast(1)
                    seekBar?.progress = adjustedProgress
                    tvCpuValue.text = adjustedProgress.toString()
                    prefs.edit().putInt("cpu_interval", adjustedProgress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarBattery.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val adjustedProgress = progress.coerceAtLeast(1)
                    seekBar?.progress = adjustedProgress
                    tvBatteryValue.text = adjustedProgress.toString()
                    prefs.edit().putInt("battery_interval", adjustedProgress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}