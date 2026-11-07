package com.toothlock.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
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
    }

    override suspend fun doWork(): Result {
        val prefs = ToothPreferences(context)

        // 1. Check if user already brushed today
        if (prefs.isBrushedToday()) {
            return Result.success()
        }

        // 2. User has NOT brushed yet!
        val detector = WatchAndSleepDetector(context)
        val isWatchConnected = detector.isSmartWatchConnected()
        val isSleeping = detector.isSleepModeActive()

        // 3. Send urgent wake-up notification to Galaxy Watch & Phone
        NotificationHelper.sendToothAlert(
            context = context,
            isWatchConnected = isWatchConnected,
            isSleeping = isSleeping
        )

        return Result.success()
    }
}
