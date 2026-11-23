package com.example.util.simpletimetracker.feature_pomodoro.timer.model

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroEntryTimeMapper

/**
 * Raw data for the pomodoro screen, all durations in milliseconds.
 */
data class PomodoroInfo(
    // Null if there is nothing to show.
    val activity: Activity?,
    // Activity that is paused and will be resumed by play.
    val pausedActivity: Activity?,
    // Null if pomodoro is not started.
    val entry: PomodoroEntryTimeMapper.Result?,
    // Not null if pomodoro is paused and a pause activity is running.
    val pauseActivityStartedMs: Long?,
    val typeTodayMs: Long,
    val typeWeekMs: Long,
    val allTodayMs: Long,
    val allWeekMs: Long,
    // How much of the working hours has already passed.
    val dayWindowMs: Long,
    val weekWindowMs: Long,
) {

    data class Activity(
        val typeId: Long,
        val name: String,
        val comment: String,
        val isRunning: Boolean,
    )
}
