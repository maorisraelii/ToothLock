package com.toothlock.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DayHistoryItem(
    val dayName: String,
    val dateKey: String,
    val isBrushed: Boolean,
    val isToday: Boolean
)

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
        private const val KEY_ALERT_INTERVAL = "pref_alert_interval"
        private const val KEY_PULSE_COUNT = "pref_watch_pulse_count"
        private const val KEY_MOTION_CANCEL = "pref_motion_cancel"

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

    /**
     * Returns the 7-day brushing history ending today.
     */
    fun getLast7DaysHistory(): List<DayHistoryItem> {
        val list = mutableListOf<DayHistoryItem>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = getTodayKey()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -4)
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dateKey = sdf.format(cal.time)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val hebrewDay = when (dayOfWeek) {
                Calendar.SUNDAY -> "א'"
                Calendar.MONDAY -> "ב'"
                Calendar.TUESDAY -> "ג'"
                Calendar.WEDNESDAY -> "ד'"
                Calendar.THURSDAY -> "ה'"
                Calendar.FRIDAY -> "ו'"
                Calendar.SATURDAY -> "ש'"
                else -> ""
            }
            val isBrushed = prefs.getBoolean(PREFIX_BRUSHED + dateKey, false)
            list.add(DayHistoryItem(hebrewDay, dateKey, isBrushed, isToday = (dateKey == today)))
        }
        return list
    }

    var targetHour: Int
        get() = prefs.getInt(KEY_TARGET_HOUR, 23)
        set(value) = prefs.edit().putInt(KEY_TARGET_HOUR, value).apply()

    var targetMinute: Int
        get() = prefs.getInt(KEY_TARGET_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_TARGET_MINUTE, value).apply()

    var alertIntervalMinutes: Int
        get() = prefs.getInt(KEY_ALERT_INTERVAL, 7)
        set(value) = prefs.edit().putInt(KEY_ALERT_INTERVAL, value).apply()

    var watchPulseCount: Int
        get() = prefs.getInt(KEY_PULSE_COUNT, 5)
        set(value) = prefs.edit().putInt(KEY_PULSE_COUNT, value).apply()

    var enableMotionCancellation: Boolean
        get() = prefs.getBoolean(KEY_MOTION_CANCEL, true)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_CANCEL, value).apply()
}
