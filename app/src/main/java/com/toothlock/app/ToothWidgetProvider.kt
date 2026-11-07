package com.toothlock.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ToothWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_BRUSHED = "com.toothlock.app.ACTION_TOGGLE_BRUSHED"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, ToothWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = ToothPreferences(context)
            val isBrushed = prefs.isBrushedToday()
            val streak = prefs.getStreak()

            val views = RemoteViews(context.packageName, R.layout.widget_tooth)

            if (isBrushed) {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_green)
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_check)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.brushed_today))
                views.setTextViewText(R.id.widget_subtext, "מעולה! השיניים נקיות 🦷")
            } else {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_red)
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_toothbrush)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.not_brushed_yet))
                views.setTextViewText(R.id.widget_subtext, context.getString(R.string.tap_to_toggle))
            }

            views.setTextViewText(R.id.widget_streak, "רצף: $streak ימים 🔥")

            // Intent when clicking the widget
            val toggleIntent = Intent(context, ToothWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_BRUSHED
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_BRUSHED) {
            val prefs = ToothPreferences(context)
            val currentStatus = prefs.isBrushedToday()
            val newStatus = !currentStatus
            prefs.setBrushedToday(newStatus)

            if (newStatus) {
                // If marked brushed, cancel any active notification
                NotificationHelper.cancelNotification(context)
            }

            updateAllWidgets(context)
        }
    }
}
