package com.tpk.widgetspro

import android.app.AppOpsManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.tpk.widgetspro.ui.permission.PermissionPage1Fragment
import com.tpk.widgetspro.ui.permission.PermissionPage2Fragment

class PermissionActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        if (prefs.getBoolean("optional_permissions_skipped_via_next", false)) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        if (areBasicPermissionsGrantedInPage1() && areAllPermissionsGrantedInPage2()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_permission)

        viewPager = findViewById(R.id.permission_viewpager)
        tabLayout = findViewById(R.id.permission_tab_layout)

        val pagerAdapter = PermissionPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Required"
                1 -> "Optional"
                else -> null
            }
        }.attach()

        checkCurrentPermissionStateAndSetPage()
    }

    fun checkCurrentPermissionStateAndSetPage() {
        if (!areBasicPermissionsGrantedInPage1()) {
            viewPager.currentItem = 0
        } else {
            viewPager.currentItem = 1
        }
    }

    fun areBasicPermissionsGrantedInPage1(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)

        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return isIgnoringBatteryOptimizations && hasNotificationPermission
    }

    fun areAllPermissionsGrantedInPage2(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        val hasUsageAccessPermission = mode == AppOpsManager.MODE_ALLOWED

        val isAccessibilityEnabled = isAccessibilityServiceEnabled()

        if (hasUsageAccessPermission && isAccessibilityEnabled) {
            if (!prefs.getBoolean("optional_permissions_interacted", false)) {
                prefs.edit().putBoolean("optional_permissions_interacted", true).apply()
            }
        }
        return hasUsageAccessPermission && isAccessibilityEnabled
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(this, com.tpk.widgetspro.services.LauncherStateAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName.flattenToString())
    }

    private inner class PermissionPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> PermissionPage1Fragment()
                1 -> PermissionPage2Fragment()
                else -> throw IllegalStateException("Invalid position $position")
            }
        }
    }
}