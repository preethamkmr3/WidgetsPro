package com.tpk.widgetspro.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.AppOpsManager
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.api.ImageApiClient
import com.tpk.widgetspro.services.gif.AnimationService
import com.tpk.widgetspro.services.sun.SunSyncService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var seekBarCpu: SeekBar
    private lateinit var seekBarBattery: SeekBar
    private lateinit var seekBarWifi: SeekBar
    private lateinit var seekBarSim: SeekBar
    private lateinit var seekBarNetworkSpeed: SeekBar
    private lateinit var tvCpuValue: TextView
    private lateinit var tvBatteryValue: TextView
    private lateinit var tvWifiValue: TextView
    private lateinit var tvSimValue: TextView
    private lateinit var tvNetworkSpeedValue: TextView
    private lateinit var enumInputLayout: TextInputLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var locationAutoComplete: AutoCompleteTextView
    private lateinit var setLocationButton: Button
    private lateinit var suggestionsAdapter: ArrayAdapter<String>
    private lateinit var radioGroupResetMode: RadioGroup
    private lateinit var btnResetNow: Button
    private lateinit var tvNextReset: TextView
    private var pendingAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var switchMicrophone: MaterialSwitch
    private lateinit var switchUsageAccess: MaterialSwitch
    private lateinit var switchAccessibilitySettings: MaterialSwitch
    private lateinit var switchGifMargin: MaterialSwitch
    private lateinit var switchGifBackground: MaterialSwitch
    private val REQUEST_RECORD_AUDIO_PERMISSION = 101
    private val enumOptions = arrayOf(
        "black", "blue", "white", "silver", "transparent",
        "case", "fullproduct", "product", "withcase",
        "headphones", "headset"
    )

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            if (pendingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val internalPath = copyGifToDeviceStorage(requireContext(), it, pendingAppWidgetId)
                if (internalPath != null) {
                    val deviceContext = requireContext().createDeviceProtectedStorageContext()
                    val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("file_path_$pendingAppWidgetId", internalPath).apply()

                    val intent = Intent(requireContext(), AnimationService::class.java).apply {
                        action = "UPDATE_FILE"
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
                        putExtra("file_path", internalPath)
                    }
                    requireContext().startService(intent)

                    Toast.makeText(requireContext(), R.string.gif_selected_message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to copy GIF", Toast.LENGTH_SHORT).show()
                }
                pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }
    }

    private fun copyGifToDeviceStorage(context: Context, sourceUri: Uri, widgetId: Int): String? {
        return try {
            val deviceContext = context.createDeviceProtectedStorageContext()
            val destinationDir = File(deviceContext.filesDir, "gifs")
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            val destinationFile = File(destinationDir, "widget_$widgetId.gif")

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destinationFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updatePermissionSwitchStates()
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        seekBarCpu = view.findViewById(R.id.seekBarCpu)
        seekBarBattery = view.findViewById(R.id.seekBarBattery)
        seekBarWifi = view.findViewById(R.id.seekBarWifi)
        seekBarSim = view.findViewById(R.id.seekBarSim)
        seekBarNetworkSpeed = view.findViewById(R.id.seekBarNetworkSpeed)
        tvCpuValue = view.findViewById(R.id.tvCpuValue)
        tvBatteryValue = view.findViewById(R.id.tvBatteryValue)
        tvWifiValue = view.findViewById(R.id.tvWifiValue)
        tvSimValue = view.findViewById(R.id.tvSimValue)
        tvNetworkSpeedValue = view.findViewById(R.id.tvNetworkSpeedValue)
        enumInputLayout = view.findViewById(R.id.enum_input_layout)
        chipGroup = view.findViewById(R.id.chip_group)
        locationAutoComplete = view.findViewById(R.id.location_auto_complete)
        setLocationButton = view.findViewById(R.id.set_location_button)
        radioGroupResetMode = view.findViewById(R.id.radio_group_reset_mode)
        btnResetNow = view.findViewById(R.id.btn_reset_now)
        tvNextReset = view.findViewById(R.id.tv_next_reset)
        switchMicrophone = view.findViewById(R.id.switch_microphone_access)
        switchUsageAccess = view.findViewById(R.id.switch_usage_access)
        switchAccessibilitySettings = view.findViewById(R.id.switch_accessibility_settings)
        switchGifMargin = view.findViewById(R.id.switch_gif_margin)
        switchGifBackground = view.findViewById(R.id.switch_gif_background)

        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        seekBarCpu.progress = prefs.getInt("cpu_interval", 60)
        seekBarBattery.progress = prefs.getInt("battery_interval", 60)
        seekBarWifi.progress = prefs.getInt("wifi_data_usage_interval", 60)
        seekBarSim.progress = prefs.getInt("sim_data_usage_interval", 60)
        seekBarNetworkSpeed.progress = prefs.getInt("network_speed_interval", 60)

        tvCpuValue.text = seekBarCpu.progress.toString()
        tvBatteryValue.text = seekBarBattery.progress.toString()
        tvWifiValue.text = seekBarWifi.progress.toString()
        tvSimValue.text = seekBarSim.progress.toString()
        tvNetworkSpeedValue.text = seekBarNetworkSpeed.progress.toString()

        val resetMode = prefs.getString("data_usage_reset_mode", "daily") ?: "daily"
        when (resetMode) {
            "daily" -> {
                radioGroupResetMode.check(R.id.radio_daily_reset)
                btnResetNow.visibility = View.GONE
                updateNextResetText("daily")
            }
            "manual" -> {
                radioGroupResetMode.check(R.id.radio_manual_reset)
                btnResetNow.visibility = View.VISIBLE
                updateNextResetText("manual")
            }
        }

        setupSeekBarListeners(prefs)
        enumInputLayout.setOnClickListener { showEnumSelectionDialog() }

        suggestionsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        locationAutoComplete.setAdapter(suggestionsAdapter)
        locationAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length > 2) {
                    fetchLocationSuggestions(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        setLocationButton.setOnClickListener {
            val location = locationAutoComplete.text.toString().trim()
            if (location.isNotEmpty()) {
                getCoordinatesFromLocation(location)
            } else {
                Toast.makeText(requireContext(), R.string.enter_location_prompt, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.reset_image_button).setOnClickListener {
            resetBluetoothImage()
        }
        val switchThemes = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.button9)
        val themePrefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        switchThemes.isChecked = themePrefs.getBoolean("red_accent", false)

        switchThemes.setOnCheckedChangeListener { _, isChecked ->
            (activity as? MainActivity)?.switchTheme()
        }

        view.findViewById<TextView>(R.id.title_main)?.setTextColor(CommonUtils.getAccentColor(requireContext()))

        radioGroupResetMode.setOnCheckedChangeListener { _, checkedId ->
            with(prefs.edit()) {
                when (checkedId) {
                    R.id.radio_daily_reset -> {
                        putString("data_usage_reset_mode", "daily")
                        btnResetNow.visibility = View.GONE
                        updateNextResetText("daily")
                    }
                    R.id.radio_manual_reset -> {
                        putString("data_usage_reset_mode", "manual")
                        btnResetNow.visibility = View.VISIBLE
                        updateNextResetText("manual")
                    }
                }
                apply()
            }
        }

        btnResetNow.setOnClickListener {
            resetDataUsageNow(prefs)
            updateNextResetText("manual")
            Toast.makeText(requireContext(), R.string.data_usage_reset_message, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.select_file_button).setOnClickListener {
            showWidgetSelectionDialog()
        }

        view.findViewById<Button>(R.id.sync_gif_widgets_button).setOnClickListener {
            showSyncWidgetSelectionDialog()
        }

        val switchClockFps = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_clock_fps)
        switchClockFps.isChecked = prefs.getBoolean("clock_60fps_enabled", false)
        switchClockFps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("clock_60fps_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(requireContext(), R.string.warning_battery_drain, Toast.LENGTH_LONG).show()
            }
        }

        updateMicrophoneSwitchState()
        switchMicrophone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestMicrophonePermission()
            } else {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Please disable via App Settings", Toast.LENGTH_SHORT).show()
                    switchMicrophone.isChecked = true
                }
            }
        }
        setupPermissionSwitches()

        switchGifMargin.isChecked = prefs.getBoolean("gif_margin_enabled", false)

        switchGifMargin.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gif_margin_enabled", isChecked).apply()
            updateAllGifWidgets()
        }

        switchGifBackground.isChecked = prefs.getBoolean("gif_background_transparent", false)

        switchGifBackground.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gif_background_transparent", isChecked).apply()
            updateAllGifWidgets()
        }
    }

    private fun updateAllGifWidgets() {
        val intent = Intent(requireContext(), GifWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val appWidgetManager = AppWidgetManager.getInstance(requireContext())
            val componentName = ComponentName(requireContext(), GifWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        requireContext().sendBroadcast(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (!isAdded) return false
        val context = requireContext()
        val serviceName = ComponentName(context, com.tpk.widgetspro.services.LauncherStateAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName.flattenToString())
    }

    private fun showAccessibilityDisclosureDialog() {
        if (!isAdded) return

        val themePrefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
        val isRedAccent = themePrefs.getBoolean("red_accent", false)
        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }

        val builder = AlertDialog.Builder(requireContext(), dialogThemeStyle)
        builder.setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure_message)
            .setCancelable(false)
            .setPositiveButton(R.string.accessibility_disclosure_agree) { dialog, _ ->
                dialog.dismiss()
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.accessibility_disclosure_decline) { dialog, _ ->
                dialog.dismiss()
                updatePermissionSwitchStates()
            }
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        try {
            val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(textColor)
        } catch (e: Exception) {
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            settingsLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open Accessibility settings.", Toast.LENGTH_SHORT).show()
            updatePermissionSwitchStates()
        }
    }

    private fun setupPermissionSwitches() {
        switchUsageAccess.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != isUsageAccessGranted()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                try {
                    settingsLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Could not open Usage Access settings.", Toast.LENGTH_SHORT).show()
                    updatePermissionSwitchStates()
                }
            }
        }

        switchAccessibilitySettings.setOnCheckedChangeListener { _, isChecked ->
            val currentlyEnabled = isAccessibilityServiceEnabled()
            if (isChecked && !currentlyEnabled) {
                showAccessibilityDisclosureDialog()
            } else if (!isChecked && currentlyEnabled) {
                openAccessibilitySettings()
            }
        }
        updatePermissionSwitchStates()
    }


    private fun updatePermissionSwitchStates() {
        if (!isAdded) return
        switchUsageAccess.isChecked = isUsageAccessGranted()
        switchAccessibilitySettings.isChecked = isAccessibilityServiceEnabled()
    }

    private fun isUsageAccessGranted(): Boolean {
        if (!isAdded) return false
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
        val mode = appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        ) ?: AppOpsManager.MODE_DEFAULT
        return mode == AppOpsManager.MODE_ALLOWED
    }



    private fun showWidgetSelectionDialog() {
        val themePrefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
        val isRedAccent = themePrefs.getBoolean("red_accent", false)
        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(requireContext(), com.tpk.widgetspro.widgets.gif.GifWidgetProvider::class.java))
        if (widgetIds.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_gif_widgets_found, Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = requireContext().getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
        val items = widgetIds.map { appWidgetId ->
            val index = prefs.getInt("widget_index_$appWidgetId", 0)
            getString(R.string.gif_widget_name, index)
        }.toTypedArray()
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(requireContext(), dialogThemeStyle).setTitle(R.string.select_gif_widget).setItems(items) { _, which ->
            pendingAppWidgetId = widgetIds[which]
            try {
                selectFileLauncher.launch(arrayOf("image/gif"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "No file picker app found to select a GIF.", Toast.LENGTH_LONG).show()
            }
        }.setNegativeButton(R.string.cancel, null).create().apply {
            setOnShowListener {
                window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
                try {
                    val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
                    findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
                    getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
                } catch (e: Exception) {
                }
            }
        }
        dialog.show()
    }

    private fun showSyncWidgetSelectionDialog() {
        val themePrefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
        val isRedAccent = themePrefs.getBoolean("red_accent", false)
        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(requireContext(), com.tpk.widgetspro.widgets.gif.GifWidgetProvider::class.java))
        if (widgetIds.size < 2) {
            Toast.makeText(requireContext(), R.string.insufficient_gif_widgets, Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = requireContext().getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
        val items = widgetIds.map { appWidgetId ->
            val index = prefs.getInt("widget_index_$appWidgetId", 0)
            getString(R.string.gif_widget_name, index)
        }.toTypedArray()
        val checkedItems = BooleanArray(widgetIds.size) { false }
        val selectedWidgetIds = mutableSetOf<Int>()
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(requireContext(), dialogThemeStyle)
            .setTitle(R.string.sync_widgets)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedWidgetIds.add(widgetIds[which])
                    if (selectedWidgetIds.size > 2) {
                        val oldest = selectedWidgetIds.first()
                        selectedWidgetIds.remove(oldest)
                        val oldestIndex = widgetIds.indexOf(oldest)
                        if(oldestIndex != -1) {
                            checkedItems[oldestIndex] = false
                            (dialog?.listView?.setItemChecked(oldestIndex, false))
                        }
                    }
                } else {
                    selectedWidgetIds.remove(widgetIds[which])
                }
            }
            .setPositiveButton(R.string.sync_gif_widgets) { _, _ ->
                if (selectedWidgetIds.size == 2) {
                    val syncGroupId = UUID.randomUUID().toString()
                    val intent = Intent(requireContext(), AnimationService::class.java).apply {
                        action = "SYNC_WIDGETS"
                        putExtra("sync_group_id", syncGroupId)
                        putExtra("sync_widget_ids", selectedWidgetIds.toIntArray())
                    }
                    try {
                        requireContext().startService(intent)
                        Toast.makeText(requireContext(), R.string.widgets_synced, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to start sync service", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.select_two_widgets, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
            try {
                val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
                dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
            } catch (e: Exception) { }
        }
        dialog.show()
    }

    private fun updateNextResetText(mode: String) {
        val nextResetLabel = getString(R.string.next_reset_label)
        if (mode == "daily") {
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val dateFormat = SimpleDateFormat("EEE, dd MMM HH:mm a", Locale.getDefault())
            tvNextReset.text = "$nextResetLabel ${dateFormat.format(calendar.time)}"
        } else {
            tvNextReset.text = "$nextResetLabel ${getString(R.string.until_reset_now_pressed)}"
        }
    }

    private fun resetDataUsageNow(prefs: android.content.SharedPreferences) {
        val currentTime = System.currentTimeMillis()
        with(prefs.edit()) {
            putLong("manual_reset_time", currentTime)
            apply()
        }
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            WifiDataUsageWidgetProviderCircle::class.java
        )
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            WifiDataUsageWidgetProviderPill::class.java
        )
        BaseSimDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            SimDataUsageWidgetProviderCircle::class.java
        )
        BaseSimDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            SimDataUsageWidgetProviderPill::class.java
        )
    }

    private fun setupSeekBarListeners(prefs: android.content.SharedPreferences) {
        seekBarCpu.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvCpuValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("cpu_interval", seekBar?.progress ?: 60).apply()
            }
        })
        seekBarBattery.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBatteryValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("battery_interval", seekBar?.progress ?: 60).apply()
            }
        })
        seekBarWifi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvWifiValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("wifi_data_usage_interval", seekBar?.progress ?: 60).apply()
                BaseWifiDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    WifiDataUsageWidgetProviderCircle::class.java
                )
                BaseWifiDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    WifiDataUsageWidgetProviderPill::class.java
                )
            }
        })
        seekBarSim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("sim_data_usage_interval", seekBar?.progress ?: 60).apply()
                BaseSimDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    SimDataUsageWidgetProviderCircle::class.java
                )
                BaseSimDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    SimDataUsageWidgetProviderPill::class.java
                )
            }
        })
        seekBarNetworkSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvNetworkSpeedValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("network_speed_interval", seekBar?.progress ?: 60).apply()
            }
        })
    }

    private fun showEnumSelectionDialog() {
        val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }

        val currentSelected = getSelectedItemsAsString().split(" ").filter { it.isNotEmpty() }.toSet()
        val checkedItems = enumOptions.map { currentSelected.contains(it) }.toBooleanArray()

        val builder = AlertDialog.Builder(requireContext(), dialogThemeStyle)
        builder.setTitle(R.string.select_option)
        builder.setMultiChoiceItems(enumOptions, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            chipGroup.removeAllViews()
            for (i in enumOptions.indices) {
                if (checkedItems[i]) {
                    addChipToGroup(enumOptions[i])
                }
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        try {
            val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
        } catch (e: Exception) { }
    }


    private fun addChipToGroup(enumText: String) {
        val chip = Chip(requireContext())
        chip.text = enumText
        chip.isCloseIconVisible = true
        chip.setChipBackgroundColorResource(R.color.text_color)
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.shape_background_color))
        chip.setCloseIconTintResource(R.color.shape_background_color)
        chip.setOnCloseIconClickListener { chipGroup.removeView(chip) }
        chipGroup.addView(chip)
    }

    private fun getSelectedItemsAsString(): String {
        val selectedItems = mutableListOf<String>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            selectedItems.add(chip.text.toString())
        }
        return selectedItems.joinToString(" ")
    }


    private fun getCoordinatesFromLocation(location: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(location, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latitude = address.latitude
                val longitude = address.longitude
                saveLocationToPreferences(latitude, longitude)
                Toast.makeText(requireContext(), getString(R.string.location_set_message, location), Toast.LENGTH_SHORT).show()
                SunSyncService.start(requireContext())
            } else {
                Toast.makeText(requireContext(), R.string.location_not_found_message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.location_error_message, e.message ?: "Unknown error"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveLocationToPreferences(latitude: Double, longitude: Double) {
        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("latitude", latitude.toString())
            putString("longitude", longitude.toString())
            apply()
        }
    }

    private fun fetchLocationSuggestions(query: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(query, 5)
            val suggestions = addresses?.mapNotNull { it.getAddressLine(0) } ?: emptyList()
            suggestionsAdapter.clear()
            suggestionsAdapter.addAll(suggestions)
            suggestionsAdapter.notifyDataSetChanged()
            if (suggestions.isNotEmpty()) {
                locationAutoComplete.showDropDown()
            }
        } catch (e: Exception) {
        }
    }

    private fun resetBluetoothImage() {
        val appWidgetIds = getBluetoothWidgetIds(requireContext())
        if (appWidgetIds.isEmpty()) {
            Toast.makeText(requireContext(), "No Bluetooth widgets found to reset.", Toast.LENGTH_SHORT).show()
            return
        }
        appWidgetIds.forEach { appWidgetId ->
            val deviceAddress = getSelectedDeviceAddress(requireContext(), appWidgetId)
            deviceAddress?.let { address ->
                val device = getBluetoothDeviceByAddress(address)
                device?.let { btDevice ->
                    val deviceName = btDevice.name ?: "Unknown_Device_${btDevice.address}"

                    ImageApiClient.clearUrlCache(requireContext(), deviceName)
                    BitmapCacheManager.clearBitmapCache(requireContext(), deviceName)

                    ImageApiClient.clearCustomQuery(requireContext(), deviceName)

                    val currentCustomQuery = getSelectedItemsAsString()
                    if (currentCustomQuery.isNotBlank()) {
                        ImageApiClient.setCustomQuery(requireContext(), deviceName, currentCustomQuery)
                    }
                    val updateIntent = Intent(requireContext(), BluetoothWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                        component = ComponentName(requireContext(), BluetoothWidgetProvider::class.java)
                    }
                    requireContext().sendBroadcast(updateIntent)

                    Toast.makeText(requireContext(), getString(R.string.bluetooth_reset_message, deviceName), Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(requireContext(), "Bluetooth device not found for address: $address", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(requireContext(), "No device selected for widget ID: $appWidgetId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getBluetoothWidgetIds(context: Context): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.getAppWidgetIds(
            ComponentName(context, BluetoothWidgetProvider::class.java)
        )
    }

    private fun getSelectedDeviceAddress(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences("BluetoothWidgetPrefs", Context.MODE_PRIVATE)
        return prefs.getString("device_address_$appWidgetId", null)
    }

    @SuppressLint("MissingPermission")
    private fun getBluetoothDeviceByAddress(address: String): android.bluetooth.BluetoothDevice? {
        if (!checkBluetoothPermission()) return null
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
        return try {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                bluetoothAdapter.getRemoteDevice(address)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        if (!isAdded || context == null) return false
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }


    private fun hasMicrophonePermission(): Boolean {
        if (!isAdded || context == null) return false
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateMicrophoneSwitchState() {
        if (isAdded) {
            switchMicrophone.isChecked = hasMicrophonePermission()
        }
    }

    private fun requestMicrophonePermission() {
        if (isAdded && !hasMicrophonePermission()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isAdded) switchMicrophone.isChecked = true
            } else {
                if (isAdded) {
                    switchMicrophone.isChecked = false
                    Toast.makeText(requireContext(), "Microphone permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateMicrophoneSwitchState()
        updatePermissionSwitchStates()
    }
}
