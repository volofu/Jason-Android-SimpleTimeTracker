package com.example.util.simpletimetracker.feature_pomodoro.timer.model

data class PomodoroInfoState(
    val activityName: String,
    val comment: String,
    val isCommentPlaceholder: Boolean,
    // Empty - not visible.
    val pausedActivity: String,
    val entryTimes: String,
    val editButtonVisible: Boolean,
    val typeLabel: String,
    val typeToday: String,
    val typeWeek: String,
    val allToday: String,
    val allWeek: String,
    val ratioToday: Ratio,
    val ratioWeek: Ratio,
) {

    data class Ratio(
        val text: String,
        // 0..1
        val fraction: Float,
    )
}
