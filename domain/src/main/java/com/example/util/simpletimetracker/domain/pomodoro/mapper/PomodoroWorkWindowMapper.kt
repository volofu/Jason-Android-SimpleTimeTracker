package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.record.model.Range
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

/**
 * Calculates how much of the daily working window (by default 9:00 - 19:00)
 * has already passed within a range (a day or a week).
 */
class PomodoroWorkWindowMapper @Inject constructor() {

    fun mapElapsed(
        range: Range,
        nowMs: Long,
        startOfDayShiftMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        windowStartHour: Int = DEFAULT_WINDOW_START_HOUR,
        windowEndHour: Int = DEFAULT_WINDOW_END_HOUR,
    ): Long {
        if (range.isUndefined || windowEndHour <= windowStartHour) return 0L

        // Calendar date of the first day in the range.
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = range.timeStarted - startOfDayShiftMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var result = 0L
        var days = 0
        while (days < MAX_DAYS) {
            val windowStart = calendar.getHour(windowStartHour)
            if (windowStart >= range.timeEnded || windowStart >= nowMs) break
            val windowEnd = calendar.getHour(windowEndHour)
            result += minOf(nowMs, windowEnd) - windowStart
            calendar.add(Calendar.DATE, 1)
            days++
        }
        return result
    }

    private fun Calendar.getHour(hour: Int): Long {
        val copy = clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, hour)
        return copy.timeInMillis
    }

    companion object {
        const val DEFAULT_WINDOW_START_HOUR = 9
        const val DEFAULT_WINDOW_END_HOUR = 19
        private const val MAX_DAYS = 366
    }
}
