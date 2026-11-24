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
    // Not null if the screen shows an infinite timer instead of pomodoro cycles.
    val countUp: CountUp?,
    val typeTodayMs: Long,
    val typeWeekMs: Long,
    // Work is defined in settings, every group is an activity category, activities that were chosen
    // separately are in a group of their own.
    val workGroups: List<WorkGroup>,
    // Every work time counted once, even if it is in several groups.
    val workTotalTodayMs: Long,
    val workTotalWeekMs: Long,
    val includeUnutilized: Boolean,
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

    data class CountUp(
        val startedMs: Long,
        // Pause activity (pomodoro is paused) or just an activity without pomodoro.
        val isPause: Boolean,
    )

    data class WorkGroup(
        // Null - name for all work, when it is not set up.
        val name: String?,
        val todayMs: Long,
        val weekMs: Long,
    )
}
