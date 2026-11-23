package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleSettings
import javax.inject.Inject

/**
 * Splits the lifetime of a running record into time spent in pomodoro focus cycles
 * and time spent on pause (pomodoro breaks and manual pause).
 */
class PomodoroEntryTimeMapper @Inject constructor() {

    fun map(
        entryStartMs: Long,
        nowMs: Long,
        // 0 - pomodoro is not started.
        pomodoroStartedMs: Long,
        // 0 - pomodoro is not paused.
        pomodoroPausedMs: Long,
        settings: PomodoroCycleSettings,
    ): Result {
        val totalMs = (nowMs - entryStartMs).coerceAtLeast(0L)
        if (pomodoroStartedMs == 0L || totalMs == 0L) {
            return Result(totalMs = totalMs, focusMs = totalMs, pauseMs = 0L)
        }

        // While pomodoro is paused its cycles are frozen at the moment of pause.
        val isPaused = pomodoroPausedMs != 0L
        val cyclesEndMs = if (isPaused) pomodoroPausedMs.coerceAtMost(nowMs) else nowMs
        val cyclesStartMs = entryStartMs.coerceAtLeast(pomodoroStartedMs)

        val breakMs = if (cyclesEndMs > cyclesStartMs) {
            breakTimeUntil(cyclesEndMs - pomodoroStartedMs, settings) -
                breakTimeUntil(cyclesStartMs - pomodoroStartedMs, settings)
        } else {
            0L
        }
        val manualPauseMs = if (isPaused) {
            (nowMs - pomodoroPausedMs.coerceAtLeast(entryStartMs)).coerceAtLeast(0L)
        } else {
            0L
        }

        val pauseMs = (breakMs + manualPauseMs).coerceIn(0L, totalMs)
        return Result(
            totalMs = totalMs,
            focusMs = totalMs - pauseMs,
            pauseMs = pauseMs,
        )
    }

    /**
     * Total duration of break cycles in the first [elapsedMs] of pomodoro.
     * Cycle order matches [PomodoroCycleDurationsMapper]:
     * focus-break-focus-break-...-focus-longBreak, or just focus-break if long breaks are disabled.
     */
    private fun breakTimeUntil(
        elapsedMs: Long,
        settings: PomodoroCycleSettings,
    ): Long {
        if (elapsedMs <= 0L) return 0L
        val focus = settings.focusTimeMs
        val breakTime = settings.breakTimeMs
        val longBreak = settings.longBreakTimeMs
        val periods = settings.periodsUntilLongBreak

        val withLongBreak = periods > 0
        val periodDuration: Long
        val breakPerPeriod: Long
        if (withLongBreak) {
            periodDuration = focus * periods + breakTime * (periods - 1) + longBreak
            breakPerPeriod = breakTime * (periods - 1) + longBreak
        } else {
            periodDuration = focus + breakTime
            breakPerPeriod = breakTime
        }
        if (periodDuration <= 0L) return 0L

        var result = (elapsedMs / periodDuration) * breakPerPeriod
        var remaining = elapsedMs % periodDuration

        // Walk through the unfinished period.
        val focusCycles = if (withLongBreak) periods else 1L
        var index = 1L
        while (remaining > 0L && index <= focusCycles) {
            remaining -= focus
            if (remaining <= 0L) break
            val breakCycle = if (withLongBreak && index == focusCycles) longBreak else breakTime
            result += minOf(remaining, breakCycle)
            remaining -= breakCycle
            index++
        }
        return result
    }

    data class Result(
        val totalMs: Long,
        val focusMs: Long,
        val pauseMs: Long,
    )
}
