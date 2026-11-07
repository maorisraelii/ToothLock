package com.toothlock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NotificationHelper {

    const val CHANNEL_WATCH = "tooth_watch_vibration_v6"
    const val CHANNEL_EMERGENCY = "tooth_emergency_phone_v4"
    const val NOTIFICATION_ID = 1001

    private var pulseJob: Job? = null

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up old channels
            listOf("tooth_urgent_alerts", "tooth_alarm_v3", "tooth_watch_channel_v4", "tooth_watch_silent_phone_v5").forEach {
                try { notificationManager.deleteNotificationChannel(it) } catch (_: Exception) {}
            }

            // 1. Watch-Focused Channel: Enables vibration for Wear OS / Galaxy Watch bridge with silent sound
            val watchChannel = NotificationChannel(
                CHANNEL_WATCH,
                "תזכורת צחצוח לשעון סמסונג (רטט בשעון)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "רטט מורגש בשעון סמסונג ללא צליל התראה רועש"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 400, 1000, 400, 1200)
                setSound(null, null)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(watchChannel)

            // 2. Emergency Escalation Channel for Phone (After 3 missed watch alerts)
            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "אזעקת חירום בטלפון (אחרי 3 התראות בשעון)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "אזעקה רועשת ורטט חזק בטלפון אם המשתמש נרדם"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 300, 1000, 300, 1500, 400, 1500)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(alarmSound, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    /**
     * Sends a long multi-pulse vibration to the Samsung Galaxy Watch while keeping the phone silent!
     * @param alertIndex Current attempt (1, 2, or 3)
     */
    fun sendWatchFocusedAlert(context: Context, alertIndex: Int, isSleeping: Boolean = false) {
        createChannels(context)
        pulseJob?.cancel()

        val openAppPendingIntent = createOpenAppPendingIntent(context)
        val brushedPendingIntent = createBrushedPendingIntent(context)
        val snoozePendingIntent = createSnoozePendingIntent(context)

        val title = when (alertIndex) {
            1 -> "🪥 תזכורת 1 מתוך 3: זמן לצחצח שיניים!"
            2 -> "⚠️ תזכורת 2 מתוך 3: אל תירדם! צחצח עכשיו."
            else -> "🚨 תזכורת 3 אחרונה לשעון! (בקרוב אזעקה בטלפון)"
        }

        val body = if (isSleeping) {
            "השעון זיהה שנרדמת! רטט מתמשך על היד - קום לצחצח."
        } else {
            "רטט ממושך על פרק כף היד. לחץ 'צחצחתי' כדי לבטל."
        }

        val wearableExtender = NotificationCompat.WearableExtender()
            .setHintScreenTimeout(NotificationCompat.WearableExtender.SCREEN_TIMEOUT_LONG)
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
                    "נודניק (7 דק')",
                    snoozePendingIntent
                ).build()
            )

        val builder = NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_toothbrush)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setLocalOnly(false)
            .setSound(null)
            .setVibrate(longArrayOf(0, 1000, 400, 1000, 400, 1200))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_check, "צחצחתי! 🪥", brushedPendingIntent)
            .addAction(R.drawable.ic_toothbrush, "נודניק 7 דק'", snoozePendingIntent)
            .extend(wearableExtender)

        val notificationManager = NotificationManagerCompat.from(context)

        // Notify once so notification bridge appears on watch
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}

        // Multi-pulse loop: Sends direct vibration commands to Wear OS nodes over Google Play Services
        // This ensures the watch vibrates directly even if phone screen is on, and the phone stays 100% silent!
        pulseJob = CoroutineScope(Dispatchers.IO).launch {
            val prefs = ToothPreferences(context)
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = Tasks.await(nodeClient.connectedNodes)
                val messageClient = Wearable.getMessageClient(context)
                val payload = "pulse".toByteArray(Charsets.UTF_8)

                for (pulse in 1..5) {
                    if (prefs.isBrushedToday()) break
                    for (node in nodes) {
                        try {
                            Tasks.await(messageClient.sendMessage(node.id, "/vibrate_pulse", payload))
                        } catch (_: Exception) {}
                    }
                    delay(1600)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Emergency Escalation: Triggered when 3 watch alerts have been sent with no response!
     * Loud sound and violent phone vibration to wake up the sleeping user.
     */
    fun sendEmergencyPhoneAlarm(context: Context) {
        createChannels(context)
        pulseJob?.cancel()

        val openAppPendingIntent = createOpenAppPendingIntent(context)
        val brushedPendingIntent = createBrushedPendingIntent(context)

        val title = "💥 אזעקת חירום: לא צחצחת שיניים!"
        val body = "התקבלו 3 תזכורות רוטטות בשעון ללא מענה! קום עכשיו ל-2 דקות כדי שלא תתעורר ב-3 בלילה."

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.ic_toothbrush)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setFullScreenIntent(openAppPendingIntent, true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound, AudioManager.STREAM_ALARM)
            .setVibrate(longArrayOf(0, 1000, 300, 1000, 300, 1500, 400, 1500))
            .addAction(R.drawable.ic_check, "צחצחתי עכשיו! 🪥 (כבה אזעקה)", brushedPendingIntent)

        val notification = builder.build().apply {
            flags = flags or Notification.FLAG_INSISTENT
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun cancelNotification(context: Context) {
        pulseJob?.cancel()
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createBrushedPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_BRUSHED_NOW
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createSnoozePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
        }
        return PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
