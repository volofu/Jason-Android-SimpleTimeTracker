package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroWorkSchedule
import com.example.util.simpletimetracker.domain.record.model.Range
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

/**
 * Calculates how much of the working hours from the schedule has already passed
 * within a range (a day or a week).
 */
class PomodoroWorkWindowMapper @Inject constructor() {

    fun mapElapsed(
        range: Range,
        nowMs: Long,
        startOfDayShiftMs: Long,
        schedule: PomodoroWorkSchedule,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        if (range.isUndefined) return 0L

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
            // Calendar is at the start of the calendar date, actual day starts after the shift.
            val dayStart = calendar.timeInMillis + startOfDayShiftMs
            if (dayStart >= range.timeEnded || dayStart >= nowMs) break

            val window = schedule.get(calendar.getDayOfWeek())
            if (window.endMinute > window.startMinute) {
                val windowStart = calendar.getMinute(window.startMinute)
                val windowEnd = calendar.getMinute(window.endMinute)
                result += (minOf(nowMs, windowEnd) - windowStart).coerceAtLeast(0L)
            }
            calendar.add(Calendar.DATE, 1)
            days++
        }
        return result
    }

    private fun Calendar.getDayOfWeek(): DayOfWeek {
        // Calendar.SUNDAY is 1.
        return DayOfWeek.entries[get(Calendar.DAY_OF_WEEK) - 1]
    }

    private fun Calendar.getMinute(minuteOfDay: Int): Long {
        val copy = clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        copy.set(Calendar.MINUTE, minuteOfDay % 60)
        return copy.timeInMillis
    }

    companion object {
        private const val MAX_DAYS = 366
    }
}
