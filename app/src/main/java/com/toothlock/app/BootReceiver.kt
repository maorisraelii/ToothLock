package com.toothlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule daily background check after reboot
            ToothCheckWorker.scheduleDailyBedtimeCheck(context)
            ToothWidgetProvider.updateAllWidgets(context)
        }
    }
}
