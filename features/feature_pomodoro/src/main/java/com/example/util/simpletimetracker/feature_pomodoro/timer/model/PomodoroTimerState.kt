package com.example.util.simpletimetracker.feature_pomodoro.timer.model

data class PomodoroTimerState(
    val maxProgress: Int,
    val progress: Int,
    val timerUpdateMs: Long,
    val durationState: DurationState,
    val currentCycleHint: String,
    // Not null in pause mode: 0 - white, 1 - red.
    val pauseBackgroundFraction: Float? = null,
) {

    data class DurationState(
        val textHours: String,
        val textMinutes: String,
        val textSeconds: String,
        val hoursIsVisible: Boolean,
    )
}