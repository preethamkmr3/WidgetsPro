package com.tpk.widgetspro.ui.addwidgets

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.api.ImageApiClient
import com.tpk.widgetspro.services.CpuMonitorService
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import com.tpk.widgetspro.widgets.networkusage.BaseNetworkSpeedWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import rikka.shizuku.Shizuku

class AddWidgetsFragment : Fragment() {

    private val REQUEST_BLUETOOTH_PERMISSIONS = 100

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_widgets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.button1).setOnClickListener {
            if (hasCpuPermissions())
                requestWidgetInstallation(CpuWidgetProvider::class.java)
            else
                Toast.makeText(requireContext(), "Provide Root/Shizuku access", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.button2).setOnClickListener {
            requestWidgetInstallation(BatteryWidgetProvider::class.java)
        }
        view.findViewById<ImageView>(R.id.imageViewButton).setOnClickListener {
            checkPermissions()
        }
        view.findViewById<Button>(R.id.button3).setOnClickListener {
            requestWidgetInstallation(CaffeineWidget::class.java)
        }
        view.findViewById<Button>(R.id.button4).setOnClickListener {
            if (hasBluetoothPermission())
                requestWidgetInstallation(BluetoothWidgetProvider::class.java)
            else
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_BLUETOOTH_PERMISSIONS
                )
        }
        view.findViewById<Button>(R.id.button5).setOnClickListener {
            requestWidgetInstallation(SunTrackerWidget::class.java)
        }
        view.findViewById<Button>(R.id.button6).setOnClickListener {
            showNetworkSpeedWidgetSizeSelectionDialog()
        }
        view.findViewById<Button>(R.id.button7).setOnClickListener {
            showWifiWidgetSizeSelectionDialog()
        }
        view.findViewById<Button>(R.id.button8).setOnClickListener {
            showSimWidgetSizeSelectionDialog()
        }
        view.findViewById<Button>(R.id.button10).setOnClickListener {
            requestWidgetInstallation(NoteWidgetProvider::class.java)
        }
    }

    private fun hasCpuPermissions(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
            .inputStream.bufferedReader().use { it.readLine() } != null
    } catch (e: Exception) {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissions() {
        when {
            hasCpuPermissions() -> (activity as? MainActivity)?.startServiceAndFinish(true)
            Shizuku.pingBinder() -> if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                (activity as? MainActivity)?.startServiceAndFinish(false)
            else Shizuku.requestPermission((activity as MainActivity).let { it.SHIZUKU_REQUEST_CODE })
            else -> showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
        builder.setTitle(R.string.permission_required_title)
        builder.setMessage(R.string.permission_required_message)
        builder.setPositiveButton("Open Shizuku") { _, _ ->
            if (isShizukuInstalled())
                checkPermissions()
            else
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
            activity?.finish()
        }
        builder.setNegativeButton("Exit") { _, _ -> activity?.finish() }
        builder.setCancelable(false)
        builder.create()
        builder.show()
    }

    private fun isShizukuInstalled(): Boolean = try {
        requireContext().packageManager.getPackageInfo("rikka.shizuku", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun requestWidgetInstallation(providerClass: Class<*>) {
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        val provider = ComponentName(requireContext(), providerClass)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val requestCode = System.currentTimeMillis().toInt()
            val successCallback = PendingIntent.getBroadcast(
                requireContext(),
                requestCode,
                Intent().setComponent(provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (!appWidgetManager.requestPinAppWidget(provider, null, successCallback))
                Toast.makeText(requireContext(), R.string.widget_pin_failed, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.widget_pin_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNetworkSpeedWidgetSizeSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
        builder.setTitle(R.string.select_widget_size)
        val sizes = arrayOf(getString(R.string.widget_size_1x1), getString(R.string.widget_size_1x2))
        builder.setItems(sizes) { _, which ->
            val providerClass = when (which) {
                0 -> NetworkSpeedWidgetProviderCircle::class.java
                1 -> NetworkSpeedWidgetProviderPill::class.java
                else -> null
            }
            providerClass?.let { requestWidgetInstallation(it) }
        }
        builder.show()
    }

    private fun showWifiWidgetSizeSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
        builder.setTitle(R.string.select_widget_size)
        val sizes = arrayOf(getString(R.string.widget_size_1x1), getString(R.string.widget_size_1x2))
        builder.setItems(sizes) { _, which ->
            val providerClass = when (which) {
                0 -> WifiDataUsageWidgetProviderCircle::class.java
                1 -> WifiDataUsageWidgetProviderPill::class.java
                else -> null
            }
            providerClass?.let { requestWidgetInstallation(it) }
        }
        builder.show()
    }

    private fun showSimWidgetSizeSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
        builder.setTitle(R.string.select_widget_size)
        val sizes = arrayOf(getString(R.string.widget_size_1x1), getString(R.string.widget_size_1x2))
        builder.setItems(sizes) { _, which ->
            val providerClass = when (which) {
                0 -> SimDataUsageWidgetProviderCircle::class.java
                1 -> SimDataUsageWidgetProviderPill::class.java
                else -> null
            }
            providerClass?.let { requestWidgetInstallation(it) }
        }
        builder.show()
    }
}
