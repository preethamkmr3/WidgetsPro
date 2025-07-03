package com.tpk.widgetspro.services.sun

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class SunSyncService : BaseMonitorService() {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private var currentInterval = 60

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (shouldUpdate()) {
                prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                currentInterval = prefs?.getInt("sun_interval", 60)?.coerceAtLeast(1) ?: 60
                val lastFetchDate = prefs?.getString("last_fetch_date", null)
                val today = LocalDate.now().toString()

                if (lastFetchDate != today) {
                    fetchSunriseSunsetData()
                    fetchWeatherData()
                }
                updateWidgets()
            }
            handler.postDelayed(this, currentInterval * 1000L)
        }
    }

    private fun fetchWeatherData() {
        scope.launch {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@launch
            val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@launch
            val weatherUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$latitude&lon=$longitude"
            fetchData(weatherUrl)?.let { parseAndSaveWeather(it) }
        }
    }

    private fun fetchSunriseSunsetData() {
        scope.launch {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@launch
            val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@launch
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            fetchData("https://api.met.no/weatherapi/sunrise/3.0/sun?lat=$latitude&lon=$longitude&date=$today")?.let {
                parseAndSaveSunriseSunset(it, today.toString(), "today")
            }
            fetchData("https://api.met.no/weatherapi/sunrise/3.0/sun?lat=$latitude&lon=$longitude&date=$tomorrow")?.let {
                parseAndSaveSunriseSunset(it, tomorrow.toString(), "tomorrow")
            }
        }
    }

    private suspend fun fetchData(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Widgets Pro")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }
            return@withContext response.body?.string()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAndSaveSunriseSunset(json: String, date: String, keyPrefix: String) {
        try {
            val jsonObject = JSONObject(json)
            val properties = jsonObject.getJSONObject("properties")

            val sunriseObj = properties.getJSONObject("sunrise")
            val sunsetObj = properties.getJSONObject("sunset")

            val sunriseStr = sunriseObj.getString("time")
            val sunsetStr = sunsetObj.getString("time")

            val sunriseLocal = OffsetDateTime.parse(sunriseStr)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalTime()

            val sunsetLocal = OffsetDateTime.parse(sunsetStr)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalTime()

            getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().apply {
                if (keyPrefix == "today") {
                    putString("sunrise_time_today", sunriseLocal.toString())
                    putString("sunset_time_today", sunsetLocal.toString())
                    putString("last_fetch_date", date)
                } else if (keyPrefix == "tomorrow") {
                    putString("sunrise_time_tomorrow", sunriseLocal.toString())
                }
                apply()
            }
        } catch (e: Exception) {
        }
    }

    private fun parseAndSaveWeather(json: String) {
        try {
            val current = JSONObject(json).getJSONObject("properties").getJSONArray("timeseries")
                .getJSONObject(0).getJSONObject("data").getJSONObject("instant").getJSONObject("details")
            val temperature = current.getDouble("air_temperature")
            getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
                .putFloat("current_temperature", temperature.toFloat()).apply()
        } catch (e: Exception) {
        }
    }

    private fun updateWidgets() {
        if (shouldUpdate()) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, SunTrackerWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) {
                stopSelf()
            } else {
                CommonUtils.updateAllWidgets(this, SunTrackerWidget::class.java)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)
        currentInterval = prefs?.getInt("sun_interval", 60)?.coerceAtLeast(1) ?: 60
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            stopSelf()
        } else {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        return START_STICKY
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "sun_interval") {
            handler.removeCallbacks(updateRunnable)
            currentInterval = prefs?.getInt(key, 60)?.coerceAtLeast(1) ?: 60
            handler.post(updateRunnable)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, SunSyncService::class.java))
        }
    }
}