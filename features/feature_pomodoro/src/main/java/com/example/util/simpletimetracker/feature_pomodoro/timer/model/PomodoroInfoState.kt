package com.example.util.simpletimetracker.feature_pomodoro.timer.model

data class PomodoroInfoState(
    val activityName: String,
    val comment: String,
    val isCommentPlaceholder: Boolean,
    // Empty - not visible.
    val pausedActivity: String,
    val pausedComment: String,
    val entryTimes: String,
    val editButtonVisible: Boolean,
    val rows: List<Row>,
    val ratioToday: Ratio,
    val ratioWeek: Ratio,
    val includeUnutilized: Boolean,
) {

    data class Row(
        val name: String,
        val today: String,
        val week: String,
        // Null - no color mark.
        val color: Int?,
        val isTotal: Boolean = false,
    )

    data class Ratio(
        val text: String,
        val slices: List<PomodoroPieSlice>,
    )
}
