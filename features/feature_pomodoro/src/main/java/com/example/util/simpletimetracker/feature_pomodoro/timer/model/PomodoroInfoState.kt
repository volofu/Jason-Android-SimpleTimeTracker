package com.example.util.simpletimetracker.feature_pomodoro.timer.model

import androidx.annotation.DrawableRes

data class PomodoroInfoState(
    val activityName: String,
    val comment: String,
    val isCommentPlaceholder: Boolean,
    // Empty - not visible.
    val pausedActivity: String,
    val pausedComment: String,
    val entryTimes: String,
    val editButtonVisible: Boolean,
    // Statistics that fit on the screen next to the timer, rows have icons instead of names.
    val compactRows: List<Row>,
    // All statistics, with names.
    val expandedRows: List<Row>,
    val includeUnutilized: Boolean,
) {

    /**
     * A line of statistics for today and for the week, with a pie chart if the pies are set.
     */
    data class Row(
        val name: String,
        val today: String,
        val week: String,
        // Null - no color mark.
        val color: Int? = null,
        val isTotal: Boolean = false,
        @DrawableRes val iconResId: Int? = null,
        val todayPie: List<PomodoroPieSlice>? = null,
        val weekPie: List<PomodoroPieSlice>? = null,
    )
}
