package com.tpk.widgetspro.services

import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill

class BaseWifiDataUsageWidgetService : WidgetUpdateService() {
    override val intervalKey = "wifi_data_usage_interval"
    override val notificationId = 6
    override val notificationChannelId = "WIFI_DATA_USAGE_CHANNEL"
    override val notificationTitle = "Wifi Data Usage Updates"
    override val notificationText = "Monitoring Wifi data usage"
    override val widgetProviderClass = BaseWifiDataUsageWidgetProvider::class.java
    private val TAG = "WifiDataUsageService"

    override fun updateWidgets() {
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(applicationContext, WifiDataUsageWidgetProviderCircle::class.java)
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(applicationContext, WifiDataUsageWidgetProviderPill::class.java)
    }
}