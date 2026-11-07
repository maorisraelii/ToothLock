package com.toothlock.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ToothCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_TAG_DAILY = "daily_tooth_check_work"
        const val WORK_TAG_FOLLOWUP = "followup_tooth_check_work"

        fun scheduleDailyBedtimeCheck(context: Context) {
            val prefs = ToothPreferences(context)
            val targetHour = prefs.targetHour
            val targetMinute = prefs.targetMinute

            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (targetTime.before(now)) {
                // If the target time already passed today, schedule for tomorrow
                targetTime.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelayMillis = targetTime.timeInMillis - now.timeInMillis

            val dailyWorkRequest = PeriodicWorkRequestBuilder<ToothCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG_DAILY)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        }

        fun scheduleFollowUpCheck(context: Context, delayMinutes: Long = 7) {
            val followUpWork = OneTimeWorkRequestBuilder<ToothCheckWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(WORK_TAG_FOLLOWUP)
                .build()

            WorkManager.getInstance(context).enqueue(followUpWork)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = ToothPreferences(context)

        // 1. Check if user already brushed today
        if (prefs.isBrushedToday()) {
            return Result.success()
        }

        val detector = WatchAndSleepDetector(context)
        val isWatchConnected = detector.isSmartWatchConnected()
        val isSleeping = detector.isSleepModeActive()
        val alertCount = prefs.getAlertCountToday()

        if (isWatchConnected) {
            if (alertCount < 3) {
                // Alert 1, 2, or 3: Watch-Only Multi-Pulse Vibration (Phone stays silent!)
                val currentAttempt = prefs.incrementAlertCount()
                NotificationHelper.sendWatchFocusedAlert(context, currentAttempt, isSleeping)

                // Schedule next check in 7 minutes if user still hasn't brushed
                scheduleFollowUpCheck(context, delayMinutes = 7)
            } else {
                // ESCALATION: 3 watch alerts were sent and ignored! Trigger loud phone emergency alarm!
                NotificationHelper.sendEmergencyPhoneAlarm(context)
            }
        } else {
            // Watch is not connected / not worn: Ring phone alarm directly
            NotificationHelper.sendEmergencyPhoneAlarm(context)
        }

        return Result.success()
    }
}
