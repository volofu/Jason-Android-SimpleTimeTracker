package com.example.util.simpletimetracker.feature_pomodoro.timer.model

/**
 * Statistics of finished pomodoro cycles, all durations in milliseconds.
 */
data class PomodoroStats(
    val today: Period,
    val week: Period,
) {

    data class Period(
        // Focus cycles that lasted as long as planned.
        val completedCount: Int,
        // Completed focus cycles by the activity that was tracked during them, most frequent first.
        val completedByActivity: List<ActivityCount>,
        // Time of breaks that was spent on break activities.
        val shortBreakMs: Long,
        val longBreakMs: Long,
        // How much break time was given by pomodoro cycles that started.
        val plannedBreakMs: Long,
    ) {

        val takenBreakMs: Long get() = shortBreakMs + longBreakMs

        val skippedBreakMs: Long get() = (plannedBreakMs - takenBreakMs).coerceAtLeast(0L)
    }

    data class ActivityCount(
        val name: String,
        val count: Int,
    )

    companion object {
        val Empty = Period(0, emptyList(), 0L, 0L, 0L).let { PomodoroStats(it, it) }
    }
}
