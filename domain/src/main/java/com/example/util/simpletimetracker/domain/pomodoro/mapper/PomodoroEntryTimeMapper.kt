package com.example.util.simpletimetracker.domain.pomodoro.mapper

import javax.inject.Inject

/**
 * Splits the lifetime of a pomodoro entry (a task that was focused on, including pauses
 * that interrupted it) into focus time and pause time.
 * Pause is time spent on pause activities (for example answering a message),
 * it has nothing to do with pomodoro break cycles.
 */
class PomodoroEntryTimeMapper @Inject constructor() {

    fun map(
        entryStartMs: Long,
        nowMs: Long,
        // Finished pauses.
        pauseTotalMs: Long,
        // 0 - not paused now.
        pausedTimestampMs: Long,
    ): Result {
        val totalMs = (nowMs - entryStartMs).coerceAtLeast(0L)
        val currentPauseMs = if (pausedTimestampMs != 0L) {
            (nowMs - pausedTimestampMs.coerceAtLeast(entryStartMs)).coerceAtLeast(0L)
        } else {
            0L
        }
        val pauseMs = (pauseTotalMs + currentPauseMs).coerceIn(0L, totalMs)
        return Result(
            totalMs = totalMs,
            focusMs = totalMs - pauseMs,
            pauseMs = pauseMs,
        )
    }

    data class Result(
        val totalMs: Long,
        val focusMs: Long,
        val pauseMs: Long,
    )
}
