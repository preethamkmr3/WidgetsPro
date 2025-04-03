package com.tpk.widgetspro.widgets.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.CommonUtils

class NoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("note_$appWidgetId")
        }
        editor.apply()
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.notes_widget_layout)

            val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
            val noteText = prefs.getString("note_$appWidgetId", "")

            val displayText = if (noteText?.isNotEmpty() == true) {
                "${context.getString(R.string.notes_label)}\n$noteText"
            } else {
                context.getString(R.string.tap_to_add_notes)
            }

            val accentColor = CommonUtils.getAccentColor(context)
            val textColor = ContextCompat.getColor(context, R.color.text_color)

            views.setImageViewBitmap(
                R.id.note_text,
                CommonUtils.createTextNotesWidgetBitmap(context, displayText, 20f, CommonUtils.getTypeface(context), accentColor, textColor)
            )

            val intent = Intent(context, NoteInputActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.notes_widget_layout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}