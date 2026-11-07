package com.toothlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_BRUSHED_NOW = "com.toothlock.app.ACTION_BRUSHED_NOW"
        const val ACTION_SNOOZE = "com.toothlock.app.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = ToothPreferences(context)

        when (intent.action) {
            ACTION_BRUSHED_NOW -> {
                // User confirmed brushing directly from Samsung Galaxy Watch or phone notification!
                prefs.setBrushedToday(true)
                NotificationHelper.cancelNotification(context)
                ToothWidgetProvider.updateAllWidgets(context)
            }

            ACTION_SNOOZE -> {
                // Dismiss current notification and schedule another alert in 10 minutes
                NotificationHelper.cancelNotification(context)

                val snoozeWork = OneTimeWorkRequestBuilder<ToothCheckWorker>()
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .addTag("tooth_snooze")
                    .build()

                WorkManager.getInstance(context).enqueue(snoozeWork)
            }
        }
    }
}
