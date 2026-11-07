package com.toothlock.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ToothPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "tooth_lock_prefs"
        private const val KEY_TARGET_HOUR = "target_hour"
        private const val KEY_TARGET_MINUTE = "target_minute"
        private const val KEY_STREAK = "streak_days"
        private const val KEY_LAST_STREAK_DATE = "last_streak_date"
        private const val PREFIX_BRUSHED = "brushed_"
        private const val PREFIX_ALERT_COUNT = "alert_count_"

        /**
         * Shift the day backwards by 4 hours so the status resets at 04:00 AM instead of 00:00.
         * From 04:00 AM until 03:59 AM the next morning is considered the SAME brushing day.
         */
        fun getTodayKey(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR_OF_DAY, -4)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(cal.time)
        }

        fun getYesterdayKey(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR_OF_DAY, -4)
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(cal.time)
        }
    }

    /**
     * Checks if user has marked brushed for today
     */
    fun isBrushedToday(): Boolean {
        return prefs.getBoolean(PREFIX_BRUSHED + getTodayKey(), false)
    }

    /**
     * Sets brushed status for today and updates streak
     */
    fun setBrushedToday(brushed: Boolean) {
        val today = getTodayKey()
        val wasBrushed = isBrushedToday()

        prefs.edit().putBoolean(PREFIX_BRUSHED + today, brushed).apply()

        if (brushed && !wasBrushed) {
            updateStreakOnSuccess()
            resetAlertCount()
        } else if (!brushed && wasBrushed) {
            // Revert streak increment if toggled off
            val current = getStreak()
            if (current > 0) {
                prefs.edit().putInt(KEY_STREAK, current - 1).apply()
            }
        }
    }

    fun getAlertCountToday(): Int {
        return prefs.getInt(PREFIX_ALERT_COUNT + getTodayKey(), 0)
    }

    fun incrementAlertCount(): Int {
        val today = getTodayKey()
        val next = getAlertCountToday() + 1
        prefs.edit().putInt(PREFIX_ALERT_COUNT + today, next).apply()
        return next
    }

    fun resetAlertCount() {
        prefs.edit().remove(PREFIX_ALERT_COUNT + getTodayKey()).apply()
    }

    private fun updateStreakOnSuccess() {
        val today = getTodayKey()
        val yesterday = getYesterdayKey()
        val lastDate = prefs.getString(KEY_LAST_STREAK_DATE, "")
        val currentStreak = prefs.getInt(KEY_STREAK, 0)

        val newStreak = when (lastDate) {
            yesterday -> currentStreak + 1
            today -> currentStreak // already recorded today
            else -> 1 // broke streak or first day
        }

        prefs.edit()
            .putInt(KEY_STREAK, newStreak)
            .putString(KEY_LAST_STREAK_DATE, today)
            .apply()
    }

    fun getStreak(): Int {
        val lastDate = prefs.getString(KEY_LAST_STREAK_DATE, "")
        val today = getTodayKey()
        val yesterday = getYesterdayKey()
        // If the streak wasn't updated yesterday or today, it reset
        if (lastDate != today && lastDate != yesterday) {
            return 0
        }
        return prefs.getInt(KEY_STREAK, 0)
    }

    var targetHour: Int
        get() = prefs.getInt(KEY_TARGET_HOUR, 23)
        set(value) = prefs.edit().putInt(KEY_TARGET_HOUR, value).apply()

    var targetMinute: Int
        get() = prefs.getInt(KEY_TARGET_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_TARGET_MINUTE, value).apply()
}
