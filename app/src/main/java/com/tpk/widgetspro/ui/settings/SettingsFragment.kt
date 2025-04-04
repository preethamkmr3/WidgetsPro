package com.tpk.widgetspro.ui.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.api.ImageApiClient
import com.tpk.widgetspro.services.SunSyncService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.ImageLoader
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var seekBarCpu: SeekBar
    private lateinit var seekBarBattery: SeekBar
    private lateinit var seekBarWifi: SeekBar
    private lateinit var seekBarSim: SeekBar
    private lateinit var tvCpuValue: TextView
    private lateinit var tvBatteryValue: TextView
    private lateinit var tvWifiValue: TextView
    private lateinit var tvSimValue: TextView
    private lateinit var tvStartTime: TextView
    private lateinit var btnSetStartTime: Button
    private lateinit var radioGroupFrequency: RadioGroup
    private lateinit var enumInputLayout: TextInputLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var locationAutoComplete: AutoCompleteTextView
    private lateinit var setLocationButton: Button
    private lateinit var suggestionsAdapter: ArrayAdapter<String>

    private val enumOptions = arrayOf(
        "black", "blue", "white", "silver", "transparent",
        "case", "fullproduct", "product", "withcase",
        "headphones", "headset"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        seekBarCpu = view.findViewById(R.id.seekBarCpu)
        seekBarBattery = view.findViewById(R.id.seekBarBattery)
        seekBarWifi = view.findViewById(R.id.seekBarWifi)
        seekBarSim = view.findViewById(R.id.seekBarSim)
        tvCpuValue = view.findViewById(R.id.tvCpuValue)
        tvBatteryValue = view.findViewById(R.id.tvBatteryValue)
        tvWifiValue = view.findViewById(R.id.tvWifiValue)
        tvSimValue = view.findViewById(R.id.tvSimValue)
        tvStartTime = view.findViewById(R.id.tvStartTime)
        btnSetStartTime = view.findViewById(R.id.btnSetStartTime)
        radioGroupFrequency = view.findViewById(R.id.radio_group_frequency)
        enumInputLayout = view.findViewById(R.id.enum_input_layout)
        chipGroup = view.findViewById(R.id.chip_group)
        locationAutoComplete = view.findViewById(R.id.location_auto_complete)
        setLocationButton = view.findViewById(R.id.set_location_button)

        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        seekBarCpu.progress = prefs.getInt("cpu_interval", 60)
        seekBarBattery.progress = prefs.getInt("battery_interval", 60)
        seekBarWifi.progress = prefs.getInt("wifi_data_usage_interval", 60)
        seekBarSim.progress = prefs.getInt("sim_data_usage_interval", 60)
        val startTime = prefs.getLong("data_usage_start_time", -1L)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvCpuValue.text = seekBarCpu.progress.toString()
        tvBatteryValue.text = seekBarBattery.progress.toString()
        tvWifiValue.text = seekBarWifi.progress.toString()
        tvSimValue.text = seekBarSim.progress.toString()

        tvStartTime.text = if (startTime != -1L) {
            "Start: ${dateFormat.format(Date(startTime))}"
        } else {
            "Start: Default (Today)"
        }

        val frequency = prefs.getString("data_usage_frequency", "daily") ?: "daily"
        when (frequency) {
            "daily" -> radioGroupFrequency.check(R.id.radio_daily)
            "monthly" -> radioGroupFrequency.check(R.id.radio_monthly)
        }
        radioGroupFrequency.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_daily -> handleFrequencyChange("daily")
                R.id.radio_monthly -> handleFrequencyChange("monthly")
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
                Toast.makeText(requireContext(), "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }
        btnSetStartTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            showTimePicker(calendar)
        }

        view.findViewById<Button>(R.id.reset_image_button).setOnClickListener {
            resetBluetoothImage()
        }

        view.findViewById<Button>(R.id.button9).setOnClickListener {
            (activity as? MainActivity)?.switchTheme()
        }

        view.findViewById<TextView>(R.id.title_main)?.setTextColor(CommonUtils.getAccentColor(requireContext()))
    }

    private fun showTimePicker(calendar: Calendar) {
        val timePicker = TimePickerDialog(
            requireContext(),
            R.style.CustomTimeTheme,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val selectedTime = calendar.timeInMillis
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("data_usage_start_time", selectedTime).apply()
                tvStartTime.text = "Start: ${dateFormat.format(Date(selectedTime))}"
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).apply {
            setOnShowListener {
                val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
                getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
                getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
            }
        }
        timePicker.show()
    }

    private fun handleFrequencyChange(frequency: String) {
        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("data_usage_frequency", frequency).apply()
        updateCustomTimeDisplay()
    }

    private fun updateCustomTimeDisplay() {
        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val startTime = prefs.getLong("data_usage_start_time", -1L)
        tvStartTime.text = if (startTime != -1L) {
            "Start: ${dateFormat.format(Date(startTime))}"
        } else {
            "Start: Not set"
        }
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
                // Update Wi-Fi widgets if needed.
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
                // Update SIM widgets if needed.
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
    }

    private fun showEnumSelectionDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
        builder.setTitle("Select options")
        builder.setMultiChoiceItems(enumOptions, null) { _, _, _ -> }
        builder.setPositiveButton("OK") { dialog, _ ->
            val checkedItems = (dialog as AlertDialog).listView.checkedItemPositions
            chipGroup.removeAllViews()
            for (i in 0 until enumOptions.size) {
                if (checkedItems[i]) {
                    addChipToGroup(enumOptions[i])
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun addChipToGroup(enumText: String) {
        val chip = Chip(requireContext())
        chip.text = enumText
        chip.isCloseIconVisible = true
        chip.setChipBackgroundColorResource(R.color.text_color)
        chip.setTextColor(resources.getColor(R.color.shape_background_color))
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

    private fun AlertDialog.applyDialogTheme() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
        findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
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
                Toast.makeText(requireContext(), "Location set to $location", Toast.LENGTH_SHORT).show()
                SunSyncService.start(requireContext())
            } else {
                Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error finding location: ${e.message}", Toast.LENGTH_SHORT).show()
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
            val suggestions = addresses?.map { it.getAddressLine(0) } ?: emptyList()
            suggestionsAdapter.clear()
            suggestionsAdapter.addAll(suggestions)
            suggestionsAdapter.notifyDataSetChanged()
            locationAutoComplete.showDropDown()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error fetching suggestions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetBluetoothImage() {
        val appWidgetIds = getBluetoothWidgetIds(requireContext())
        appWidgetIds.forEach { appWidgetId ->
            val deviceAddress = getSelectedDeviceAddress(requireContext(), appWidgetId)
            deviceAddress?.let { address ->
                val device = getBluetoothDeviceByAddress(address)
                device?.let { btDevice ->
                    resetImageForDevice(requireContext(), btDevice.name, appWidgetId)
                    clearCustomQueryForDevice(requireContext(), btDevice.name, appWidgetId)
                    setCustomQueryForDevice(requireContext(), btDevice.name, getSelectedItemsAsString(), appWidgetId)
                    Toast.makeText(requireContext(), "Reset image and query for ${btDevice.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resetImageForDevice(context: Context, deviceName: String, appWidgetId: Int) {
        ImageApiClient.clearUrlCache(context, deviceName)
        BitmapCacheManager.clearBitmapCache(context, deviceName)
        updateWidget(context, appWidgetId)
    }

    private fun setCustomQueryForDevice(context: Context, deviceName: String, query: String, appWidgetId: Int) {
        ImageApiClient.setCustomQuery(context, deviceName, query)
        resetImageForDevice(context, deviceName, appWidgetId)
        resetUpdateWidget(context, appWidgetId)
    }

    private fun clearCustomQueryForDevice(context: Context, deviceName: String, appWidgetId: Int) {
        ImageApiClient.clearCustomQuery(context, deviceName)
        resetImageForDevice(context, deviceName, appWidgetId)
    }

    private fun updateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = android.widget.RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
        views.setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun resetUpdateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = android.widget.RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
        val deviceAddress = getSelectedDeviceAddress(context, appWidgetId)
        deviceAddress?.let {
            val device = getBluetoothDeviceByAddress(it)
            device?.let { btDevice ->
                ImageLoader(context, appWidgetManager, appWidgetId, views).loadImageAsync(btDevice)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
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
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
        return try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }
    }
}
