package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroEntryTimeMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class PomodoroEntryTimeMapperTest {

    private val subject = PomodoroEntryTimeMapper()

    private fun min(value: Long) = TimeUnit.MINUTES.toMillis(value)

    private val start = min(1000)

    @Test
    fun noPauses() {
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(40),
            pauseTotalMs = 0L, pausedTimestampMs = 0L,
        )
        assertEquals(min(40), result.totalMs)
        assertEquals(min(40), result.focusMs)
        assertEquals(0L, result.pauseMs)
    }

    @Test
    fun finishedPauses() {
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(40),
            pauseTotalMs = min(7), pausedTimestampMs = 0L,
        )
        assertEquals(min(40), result.totalMs)
        assertEquals(min(33), result.focusMs)
        assertEquals(min(7), result.pauseMs)
    }

    @Test
    fun currentPauseIsAdded() {
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(40),
            pauseTotalMs = min(7), pausedTimestampMs = start + min(35),
        )
        assertEquals(min(12), result.pauseMs)
        assertEquals(min(28), result.focusMs)
    }

    @Test
    fun pauseIsNotLongerThanEntry() {
        val result = subject.map(
            entryStartMs = start, nowMs = start + min(10),
            pauseTotalMs = min(30), pausedTimestampMs = start - min(5),
        )
        assertEquals(min(10), result.totalMs)
        assertEquals(min(10), result.pauseMs)
        assertEquals(0L, result.focusMs)
    }

    @Test
    fun entryStartedNow() {
        val result = subject.map(
            entryStartMs = start, nowMs = start,
            pauseTotalMs = 0L, pausedTimestampMs = 0L,
        )
        assertEquals(0L, result.totalMs)
        assertEquals(0L, result.focusMs)
        assertEquals(0L, result.pauseMs)
    }
}
