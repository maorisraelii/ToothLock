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

    const val CHANNEL_WATCH = "tooth_watch_silent_phone_v8"
    const val CHANNEL_PHONE_ONLY = "tooth_phone_only_v1"
    const val CHANNEL_EMERGENCY = "tooth_emergency_phone_v4"
    const val NOTIFICATION_ID = 1001

    private var pulseJob: Job? = null

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up old channels
            listOf(
                "tooth_urgent_alerts",
                "tooth_alarm_v3",
                "tooth_watch_channel_v4",
                "tooth_watch_silent_phone_v5",
                "tooth_watch_vibration_v6",
                "tooth_watch_alert_v7"
            ).forEach {
                try { notificationManager.deleteNotificationChannel(it) } catch (_: Exception) {}
            }

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // 1. Watch-Focused Channel: Configured with standard alert sound for Galaxy Wearable bridge,
            // BUT WITH VIBRATION STRICTLY DISABLED ON THE PHONE! The phone will NEVER shake or vibrate!
            val watchChannel = NotificationChannel(
                CHANNEL_WATCH,
                "תזכורת צחצוח לשעון (ללא רטט בטלפון - שעון בלבד)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "תזכורת המועברת לשעון סמסונג כשהטלפון 100% שקט ואינו רוטט"
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(defaultSoundUri, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(watchChannel)

            // 2. Phone-Only Channel: For users without a smartwatch - alerts with clear phone sound & vibration
            val phoneOnlyChannel = NotificationChannel(
                CHANNEL_PHONE_ONLY,
                "תזכורת צחצוח בטלפון (למשתמשים ללא שעון חכם)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראה עם צליל ורטט מורגש בטלפון למשתמשים ללא שעון חכם"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 1000)
                setSound(defaultSoundUri, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(phoneOnlyChannel)

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

        val prefs = ToothPreferences(context)
        val hasWatch = prefs.hasSmartWatch
        val targetChannel = if (hasWatch) CHANNEL_WATCH else CHANNEL_PHONE_ONLY

        val title = when (alertIndex) {
            1 -> if (hasWatch) "🪥 תזכורת 1 מתוך 3: זמן לצחצח שיניים!" else "🪥 תזכורת 1: זמן לצחצח שיניים בטלפון!"
            2 -> if (hasWatch) "⚠️ תזכורת 2 מתוך 3: אל תירדם! צחצח עכשיו." else "⚠️ תזכורת 2: אל תירדם! קום לצחצח."
            else -> if (hasWatch) "🚨 תזכורת 3 אחרונה לשעון! (בקרוב אזעקה בטלפון)" else "🚨 תזכורת 3 אחרונה בטלפון!"
        }

        val body = if (!hasWatch) {
            "תזכורת צחצוח שיניים בטלפון! לחץ 'צחצחתי' כדי לאשר."
        } else if (isSleeping) {
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

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, targetChannel)
            .setSmallIcon(R.drawable.ic_toothbrush)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setLocalOnly(!hasWatch)
            .setSound(defaultSoundUri)
            .setDefaults(if (hasWatch) 0 else NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(if (hasWatch) longArrayOf(0) else longArrayOf(0, 800, 300, 800, 300, 1000))
            .setOnlyAlertOnce(!hasWatch)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_check, "צחצחתי! 🪥", brushedPendingIntent)
            .addAction(R.drawable.ic_toothbrush, "נודניק 7 דק'", snoozePendingIntent)

        if (hasWatch) {
            builder.extend(wearableExtender)
        }

        val notificationManager = NotificationManagerCompat.from(context)

        // Initial alert
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}

        if (!hasWatch) {
            // For phone-only mode, single rich alerting notification is sufficient
            return
        }

        // Multi-pulse loop for watch:
        // 1. Re-triggers Samsung's bridge every 3 seconds so the GALAXY WATCH buzzes repeatedly on the wrist!
        // 2. The PHONE stays 100% still and silent because phone vibration is disabled!
        // 3. Also sends direct Wear OS message in case watch companion is installed!
        pulseJob = CoroutineScope(Dispatchers.IO).launch {
            val prefs = ToothPreferences(context)
            val nodeClient = try { Wearable.getNodeClient(context) } catch (_: Exception) { null }
            val messageClient = try { Wearable.getMessageClient(context) } catch (_: Exception) { null }
            val payload = "pulse".toByteArray(Charsets.UTF_8)

            for (pulse in 1..4) {
                if (prefs.isBrushedToday()) break

                // Trigger Samsung Watch Bridge with fresh buzz
                try {
                    val pulseBuilder = NotificationCompat.Builder(context, CHANNEL_WATCH)
                        .setSmallIcon(R.drawable.ic_toothbrush)
                        .setContentTitle("$title (פעימה $pulse/4)")
                        .setContentText(body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentIntent(openAppPendingIntent)
                        .setAutoCancel(true)
                        .setLocalOnly(false)
                        .setSound(defaultSoundUri)
                        .setDefaults(0)
                        .setVibrate(longArrayOf(0))
                        .setOnlyAlertOnce(false)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(R.drawable.ic_check, "צחצחתי! 🪥", brushedPendingIntent)
                        .addAction(R.drawable.ic_toothbrush, "נודניק 7 דק'", snoozePendingIntent)
                        .extend(wearableExtender)

                    notificationManager.notify(NOTIFICATION_ID, pulseBuilder.build())
                } catch (_: Exception) {}

                // Also send Wear OS direct message
                try {
                    if (nodeClient != null && messageClient != null) {
                        val nodes = Tasks.await(nodeClient.connectedNodes)
                        for (node in nodes) {
                            Tasks.await(messageClient.sendMessage(node.id, "/vibrate_pulse", payload))
                        }
                    }
                } catch (_: Exception) {}

                delay(2800)
            }
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
