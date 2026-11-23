package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroEntryTimeMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class PomodoroEntryTimeMapperTest {

    private val subject = PomodoroEntryTimeMapper()

    private fun min(value: Long) = TimeUnit.MINUTES.toMillis(value)

    // Focus 25, break 5, long break 15 after 4 periods. Period = 4 * 25 + 3 * 5 + 15 = 130 min.
    private val settings = PomodoroCycleSettings(
        focusTimeMs = min(25),
        breakTimeMs = min(5),
        longBreakTimeMs = min(15),
        periodsUntilLongBreak = 4,
    )

    @Test
    fun pomodoroNotStarted() {
        val result = subject.map(
            entryStartMs = 0L + min(1), nowMs = min(61),
            pomodoroStartedMs = 0L, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(60), result.totalMs)
        assertEquals(min(60), result.focusMs)
        assertEquals(0L, result.pauseMs)
    }

    @Test
    fun entryStartedTogetherWithPomodoro() {
        val start = min(1000)
        // 25 focus + 5 break + 10 focus = 40.
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(40),
            pomodoroStartedMs = start, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(40), result.totalMs)
        assertEquals(min(35), result.focusMs)
        assertEquals(min(5), result.pauseMs)
    }

    @Test
    fun insideBreak() {
        val start = min(1000)
        // 25 focus + 3 minutes of break.
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(28),
            pomodoroStartedMs = start, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(25), result.focusMs)
        assertEquals(min(3), result.pauseMs)
    }

    @Test
    fun fullPeriodIncludingLongBreak() {
        val start = min(1000)
        // 130 min period: 4 * 25 focus, 3 * 5 break + 15 long break = 30 pause.
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(130),
            pomodoroStartedMs = start, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(100), result.focusMs)
        assertEquals(min(30), result.pauseMs)
    }

    @Test
    fun severalPeriodsAndPartialLongBreak() {
        val start = min(1000)
        // 130 + 100 (4 focus) + 3 * 5 breaks + 5 of long break = 130 + 100 + 15 + 5 = 250.
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(250),
            pomodoroStartedMs = start, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(250), result.totalMs)
        // First period 30 pause, second period 15 + 5.
        assertEquals(min(50), result.pauseMs)
        assertEquals(min(200), result.focusMs)
    }

    @Test
    fun entryStartedInTheMiddleOfPomodoro() {
        val pomodoroStart = min(1000)
        // Entry starts in the middle of the first break (3 min left) and runs 3 + 25 min more.
        val entryStart = pomodoroStart + min(27)
        val result = subject.map(
            entryStartMs = entryStart, nowMs = entryStart + min(28),
            pomodoroStartedMs = pomodoroStart, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(28), result.totalMs)
        assertEquals(min(3), result.pauseMs)
        assertEquals(min(25), result.focusMs)
    }

    @Test
    fun entryStartedBeforePomodoro() {
        val entryStart = min(1000)
        val pomodoroStart = entryStart + min(10)
        // 10 min before pomodoro are counted as focus, then 25 focus and 5 break.
        val result = subject.map(
            entryStartMs = entryStart, nowMs = pomodoroStart + min(30),
            pomodoroStartedMs = pomodoroStart, pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(min(40), result.totalMs)
        assertEquals(min(35), result.focusMs)
        assertEquals(min(5), result.pauseMs)
    }

    @Test
    fun noLongBreak() {
        val noLong = settings.copy(periodsUntilLongBreak = 0)
        val start = min(1000)
        // Period is 30: 25 focus + 5 break. 70 min = 2 periods (10 pause) + 10 focus.
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(70),
            pomodoroStartedMs = start, pomodoroPausedMs = 0L, settings = noLong,
        )
        assertEquals(min(10), result.pauseMs)
        assertEquals(min(60), result.focusMs)
    }

    @Test
    fun pausedPomodoroFreezesCyclesAndCountsPause() {
        val start = min(1000)
        // Paused after 20 min of focus, and 7 min passed since.
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(27),
            pomodoroStartedMs = start, pomodoroPausedMs = start + min(20), settings = settings,
        )
        assertEquals(min(27), result.totalMs)
        assertEquals(min(7), result.pauseMs)
        assertEquals(min(20), result.focusMs)
    }

    @Test
    fun entryStartedNow() {
        val start = min(1000)
        val result = subject.map(
            entryStartMs = start, nowMs = start,
            pomodoroStartedMs = start - min(26), pomodoroPausedMs = 0L, settings = settings,
        )
        assertEquals(0L, result.totalMs)
        assertEquals(0L, result.pauseMs)
        assertEquals(0L, result.focusMs)
    }
}
