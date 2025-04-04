package com.tpk.widgetspro

import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.services.CpuMonitorService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.utils.NotificationUtils
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    internal val SHIZUKU_REQUEST_CODE = 1001
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)
        NotificationUtils.createChannel(this)
        checkBatteryOptimizations()
        BitmapCacheManager.clearExpiredCache(this)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        setTheme(
            when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
        )
    }

    private fun checkBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Battery optimizations disabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Please disable battery optimizations for better performance",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SHIZUKU_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startServiceAndFinish(useRoot = false)
                else Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startServiceAndFinish(useRoot: Boolean) {
        startForegroundService(
            Intent(this, CpuMonitorService::class.java).putExtra("use_root", useRoot)
        )
    }

    fun switchTheme() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        prefs.edit().apply {
            putBoolean("dark_theme", !isDarkTheme)
            putBoolean("red_accent", !isRedAccent)
            apply()
        }
        // Update all widget providers so they reflect the theme change.
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val providers = arrayOf(
            CpuWidgetProvider::class.java,
            BatteryWidgetProvider::class.java,
            BluetoothWidgetProvider::class.java,
            CaffeineWidget::class.java,
            SunTrackerWidget::class.java,
            NetworkSpeedWidgetProviderCircle::class.java,
            NetworkSpeedWidgetProviderPill::class.java,
            WifiDataUsageWidgetProviderCircle::class.java,
            WifiDataUsageWidgetProviderPill::class.java,
            SimDataUsageWidgetProviderCircle::class.java,
            SimDataUsageWidgetProviderPill::class.java,
            NoteWidgetProvider::class.java
        )

        providers.forEach { provider ->
            val componentName = ComponentName(this, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                }
                sendBroadcast(intent)
            }
        }
        recreate()
    }
}
