package com.toothlock.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_ID = "tooth_urgent_alerts"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "התראות צחצוח שיניים (חירום)"
            val descriptionText = "התראות רוטטות לשעון ולטלפון לפני השינה"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 1000, 300, 1000)

                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                setSound(alarmSound, audioAttributes)
                setBypassDnd(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendToothAlert(
        context: Context,
        isWatchConnected: Boolean = true,
        isSleeping: Boolean = false
    ) {
        createNotificationChannel(context)

        // Intent to open Main Activity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Brushed Now!
        val brushedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_BRUSHED_NOW
        }
        val brushedPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            brushedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 10 mins
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSleeping) {
            "🚨 התעורר לרגע! לא צחצחת שיניים!"
        } else {
            "🪥 זמן לצחצח שיניים לפני השינה!"
        }

        val body = when {
            isSleeping -> "השעון זיהה שנרדמת בלי לצחצח. קום עכשיו ל-2 דקות כדי שלא תתעורר ב-3 בלילה!"
            isWatchConnected -> "השעון רוטט כדי להזכיר לך לצחצח עכשיו לפני שתרדם."
            else -> "טרם סימנת שצחצחת שיניים היום. אל תירדם ככה!"
        }

        // Wearable specific actions for Samsung Galaxy Watch
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_check,
                    "צחצחתי עכשיו! 🪥",
                    brushedPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_toothbrush,
                    "נודניק (10 דק')",
                    snoozePendingIntent
                ).build()
            )
            .setHintShowBackgroundOnlyWhenMining(false)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_toothbrush)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // CATEGORY_ALARM forces the notification through Samsung Sleep/Bedtime DND mode!
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 600, 200, 600, 200, 1000, 300, 1000))
            .addAction(R.drawable.ic_check, "צחצחתי! 🪥", brushedPendingIntent)
            .addAction(R.drawable.ic_toothbrush, "נודניק 10 דק'", snoozePendingIntent)
            .extend(wearableExtender)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission check handled in MainActivity
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
