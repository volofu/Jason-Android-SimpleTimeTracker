package com.example.util.simpletimetracker.domain.pomodoro.model

/**
 * A finished pomodoro cycle: completed by time or cut short (skipped, restarted, stopped).
 */
data class PomodoroCycleLogEntry(
    val endMs: Long,
    val type: PomodoroCycleType,
    // How long the cycle actually lasted.
    val actualMs: Long,
    // How long it should have lasted.
    val plannedMs: Long,
) {

    val startMs: Long get() = endMs - actualMs

    val isCompleted: Boolean get() = actualMs >= plannedMs
}
