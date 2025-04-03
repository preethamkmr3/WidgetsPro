package com.tpk.widgetspro.services

import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill

class BaseSimDataUsageWidgetService : WidgetUpdateService() {
    override val intervalKey = "sim_data_usage_interval"
    override val notificationId = 5
    override val notificationChannelId = "SIM_DATA_USAGE_CHANNEL"
    override val notificationTitle = "SIM Data Usage Updates"
    override val notificationText = "Monitoring SIM data usage"
    override val widgetProviderClass = BaseSimDataUsageWidgetProvider::class.java
    private val TAG = "SimDataUsageService"

    override fun updateWidgets() {
        BaseSimDataUsageWidgetProvider.updateAllWidgets(applicationContext, SimDataUsageWidgetProviderCircle::class.java)
        BaseSimDataUsageWidgetProvider.updateAllWidgets(applicationContext, SimDataUsageWidgetProviderPill::class.java)
    }
}