package com.tpk.widgetspro.services.bottleSpinner

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import kotlinx.coroutines.*
import kotlin.random.Random

class BottleSpinnerAnimationService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            serviceScope.launch {
                animateBottle(appWidgetId)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun animateBottle(appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val remoteViews = RemoteViews(packageName, R.layout.widget_bottle_spinner)
        val interpolator = AccelerateDecelerateInterpolator()

        val randomAngle = Random.nextFloat() * 1800 + 1080
        val duration = 3000L
        val steps = 360
        val delayStep = duration / steps

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val easedProgress = interpolator.getInterpolation(progress)

            val currentRotation = easedProgress * randomAngle

            remoteViews.setFloat(R.id.iv_bottle, "setRotation", currentRotation)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            delay(delayStep)
        }

        remoteViews.setFloat(R.id.iv_bottle, "setRotation", randomAngle)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}