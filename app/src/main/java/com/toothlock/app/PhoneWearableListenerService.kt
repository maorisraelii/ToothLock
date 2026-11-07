package com.toothlock.app

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/watch_brushed_now" -> {
                val value = String(messageEvent.data, Charsets.UTF_8)
                val isBrushed = (value == "1")
                val prefs = ToothPreferences(this)
                prefs.setBrushedToday(isBrushed)

                if (isBrushed) {
                    NotificationHelper.cancelNotification(this)
                }

                ToothWidgetProvider.updateAllWidgets(this)
            }
            "/report_on_body" -> {
                val isWorn = messageEvent.data.firstOrNull() == 1.toByte()
                WatchAndSleepDetector.cachedWatchWornOnBody = isWorn
            }
        }
    }
}
