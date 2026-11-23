package com.example.util.simpletimetracker.feature_pomodoro.timer.model

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroEntryTimeMapper

/**
 * Raw data for the pomodoro screen, all durations in milliseconds.
 */
data class PomodoroInfo(
    // Null if there is nothing to show.
    val activity: Activity?,
    // Null if activity is not running at the moment (for example paused).
    val entry: PomodoroEntryTimeMapper.Result?,
    val typeTodayMs: Long,
    val typeWeekMs: Long,
    val allTodayMs: Long,
    val allWeekMs: Long,
    // How much of the working window has already passed.
    val dayWindowMs: Long,
    val weekWindowMs: Long,
) {

    data class Activity(
        val typeId: Long,
        val name: String,
        val comment: String,
    )
}
