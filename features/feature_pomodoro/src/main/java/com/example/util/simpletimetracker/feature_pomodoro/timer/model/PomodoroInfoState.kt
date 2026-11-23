package com.example.util.simpletimetracker.feature_pomodoro.timer.model

data class PomodoroInfoState(
    val activityName: String,
    val comment: String,
    val isCommentPlaceholder: Boolean,
    val entryTimes: String,
    val typeLabel: String,
    val typeToday: String,
    val typeWeek: String,
    val allToday: String,
    val allWeek: String,
)
