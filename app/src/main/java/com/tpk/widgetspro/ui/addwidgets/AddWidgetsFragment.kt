package com.tpk.widgetspro.ui.addwidgets

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.music.MediaMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.bottleSpinner.BottleSpinnerWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import com.tpk.widgetspro.widgets.music.MusicSimpleWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
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
        super.onViewCreated(view, savedInstanceState)

        val titleTextView = view.findViewById<TextView>(R.id.title_main)
        titleTextView.setTextColor(CommonUtils.getAccentColor(requireContext()))


        setupWidgetPreview(view.findViewById(R.id.preview_cpu), R.layout.cpu_widget_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_battery), R.layout.battery_widget_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_caffeine), R.layout.caffeine_widget_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_bluetooth), R.layout.bluetooth_widget_preview_static)


        val sunPreviewContainer = view.findViewById<FrameLayout>(R.id.preview_sun)
        setupWidgetPreview(sunPreviewContainer, R.layout.sun_tracker_widget_preview_static)
        val pathImageView = sunPreviewContainer.findViewById<ImageView>(R.id.path_preview_static)
        if (pathImageView != null) {
            val pathBitmap = generatePathBitmap(requireContext(), dpToPx(100), dpToPx(70))
            pathImageView.setImageBitmap(pathBitmap)
        }


        setupWidgetPreview(view.findViewById(R.id.preview_network_speed_circle), R.layout.network_speed_widget_circle_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_network_speed_pill), R.layout.network_speed_widget_pill_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_wifi_data_circle), R.layout.wifi_data_usage_widget_circle_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_wifi_data_pill), R.layout.wifi_data_usage_widget_pill_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_sim_data_circle), R.layout.sim_data_usage_widget_circle_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_sim_data_pill), R.layout.sim_data_usage_widget_pill_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_notes), R.layout.notes_widget_preview_static)


        val analog1PreviewContainer = view.findViewById<FrameLayout>(R.id.preview_analog_1)
        setupWidgetPreview(analog1PreviewContainer, R.layout.analog_1_widget_preview_static)
        val dial1ImageView = analog1PreviewContainer.findViewById<ImageView>(R.id.analog_1_dial_preview)
        if (dial1ImageView != null) {
            val dialRes1 = if (isSystemInDarkTheme(requireContext())) R.drawable.analog_1_dial_dark else R.drawable.analog_1_dial_light
            dial1ImageView.setImageResource(dialRes1)
        }


        val analog2PreviewContainer = view.findViewById<FrameLayout>(R.id.preview_analog_2)
        setupWidgetPreview(analog2PreviewContainer, R.layout.analog_2_widget_preview_static)
        val dial2ImageView = analog2PreviewContainer.findViewById<ImageView>(R.id.analog_2_dial_preview)
        if (dial2ImageView != null) {
            val dialRes2 = if (isSystemInDarkTheme(requireContext())) R.drawable.analog_2_dial_dark else R.drawable.analog_2_dial_light
            dial2ImageView.setImageResource(dialRes2)
        }

        setupWidgetPreview(view.findViewById(R.id.preview_gif), R.layout.gif_widget_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_music), R.layout.music_widget_preview_static)
        setupWidgetPreview(view.findViewById(R.id.preview_spin_the_bottle), R.layout.spin_the_bottle_preview_static)

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
        view.findViewById<Button>(R.id.button11).setOnClickListener {
            showAnalogClockSizeSelectionDialog()
        }
        view.findViewById<Button>(R.id.button12).setOnClickListener {
            requestWidgetInstallation(GifWidgetProvider::class.java)
        }
        view.findViewById<Button>(R.id.button_music).setOnClickListener {
            requestMusicWidgetInstallation()
        }
        view.findViewById<Button>(R.id.button_spin_the_bottle).setOnClickListener {
            requestWidgetInstallation(BottleSpinnerWidgetProvider::class.java)
        }
    }


    private fun setupWidgetPreview(previewContainer: ViewGroup, staticLayoutId: Int) {
        try {
            previewContainer.removeAllViews()
            val inflater = LayoutInflater.from(requireContext())
            val previewView = inflater.inflate(staticLayoutId, previewContainer, false)
            previewContainer.addView(previewView)
        } catch (e: Exception) {
            previewContainer.removeAllViews()
            val errorText = TextView(requireContext()).apply {
                text = "Preview failed"
                textSize = 8f
            }
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            params.gravity = android.view.Gravity.CENTER
            errorText.layoutParams = params
            previewContainer.addView(errorText)
        }
    }

    private fun generatePathBitmap(context: Context, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = CommonUtils.getAccentColor(context)
            strokeWidth = 2f * context.resources.displayMetrics.density
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val path = Path().apply {
            val p0 = Pair(40f / 300f * widthPx, 82f / 150f * heightPx)
            val p1 = Pair(150f / 300f * widthPx, (-20f) / 150f * heightPx)
            val p2 = Pair(260f / 300f * widthPx, 82f / 150f * heightPx)
            moveTo(p0.first, p0.second)
            quadTo(p1.first, p1.second, p2.first, p2.second)
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            requireContext().resources.displayMetrics
        ).toInt()
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
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

    private fun isNotificationServiceEnabled(): Boolean {
        val context = requireContext()
        val componentName = ComponentName(context, MediaMonitorService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(componentName.flattenToString())
    }

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
        val builder = AlertDialog.Builder(requireContext())
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
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun AlertDialog.applyDialogTheme() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
        findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
    }

    private fun isShizukuInstalled(): Boolean = try {
        requireContext().packageManager.getPackageInfo("rikka.shizuku", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun requestMusicWidgetInstallation() {
        if (isNotificationServiceEnabled()) {
            requestWidgetInstallation(MusicSimpleWidgetProvider::class.java)
        } else {
            val builder = AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage("The Music Widget needs Notification Access to function properly. Please enable it in Settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
            val dialog = builder.create()
            dialog.show()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
            dialog.applyDialogTheme()
        }
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
        val builder = AlertDialog.Builder(requireContext())
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
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun showWifiWidgetSizeSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext())
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
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun showSimWidgetSizeSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext())
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
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }
    private fun showAnalogClockSizeSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.select_analog_clock)
        val sizes = arrayOf(getString(R.string.analog_1_clock_widget_label), getString(R.string.analog_2_clock_widget_label))
        builder.setItems(sizes) { _, which ->
            val providerClass = when (which) {
                0 -> AnalogClockWidgetProvider_1::class.java
                1 -> AnalogClockWidgetProvider_2::class.java
                else -> null
            }
            providerClass?.let { requestWidgetInstallation(it) }
        }
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }
}