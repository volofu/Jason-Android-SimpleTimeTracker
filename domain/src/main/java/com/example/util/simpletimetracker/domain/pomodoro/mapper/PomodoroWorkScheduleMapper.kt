package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroWorkSchedule
import javax.inject.Inject

/**
 * Format: "startMinute-endMinute" for every day of the week joined by ",", Sunday first.
 */
class PomodoroWorkScheduleMapper @Inject constructor() {

    fun fromString(value: String): PomodoroWorkSchedule {
        val parts = value.split(DAY_DELIMITER)
        val windows = DayOfWeek.entries.mapIndexed { index, dayOfWeek ->
            dayOfWeek to parseWindow(parts.getOrNull(index))
        }.toMap()
        return PomodoroWorkSchedule(windows)
    }

    fun toString(schedule: PomodoroWorkSchedule): String {
        return DayOfWeek.entries.joinToString(separator = DAY_DELIMITER) {
            val window = schedule.get(it)
            "${window.startMinute}$TIME_DELIMITER${window.endMinute}"
        }
    }

    private fun parseWindow(value: String?): PomodoroWorkSchedule.Window {
        val parts = value?.split(TIME_DELIMITER)
        val start = parts?.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }
        val end = parts?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }
        return if (start != null && end != null) {
            PomodoroWorkSchedule.Window(start, end)
        } else {
            PomodoroWorkSchedule.DEFAULT_WINDOW
        }
    }

    companion object {
        private const val DAY_DELIMITER = ","
        private const val TIME_DELIMITER = "-"
        private const val MAX_MINUTE = 24 * 60
    }
}
