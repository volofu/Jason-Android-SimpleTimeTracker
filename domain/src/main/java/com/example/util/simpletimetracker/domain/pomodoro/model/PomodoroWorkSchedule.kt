package com.example.util.simpletimetracker.domain.pomodoro.model

import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek

/**
 * Working hours for every day of the week, used to calculate how much time
 * was available for work.
 */
data class PomodoroWorkSchedule(
    val windows: Map<DayOfWeek, Window>,
) {

    fun get(dayOfWeek: DayOfWeek): Window {
        return windows[dayOfWeek] ?: DEFAULT_WINDOW
    }

    fun with(dayOfWeek: DayOfWeek, window: Window): PomodoroWorkSchedule {
        return copy(windows = windows + (dayOfWeek to window))
    }

    /**
     * Both values are minutes since the start of the day, end can be up to 24 * 60.
     * If end is not after start the day has no working hours.
     */
    data class Window(
        val startMinute: Int,
        val endMinute: Int,
    )

    companion object {
        val DEFAULT_WINDOW = Window(startMinute = 9 * 60, endMinute = 19 * 60)

        val Default = PomodoroWorkSchedule(
            windows = DayOfWeek.entries.associateWith { DEFAULT_WINDOW },
        )
    }
}
