package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroTrackedTimeMapper
import com.example.util.simpletimetracker.domain.record.model.Range
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroTrackedTimeMapperTest {

    private val subject = PomodoroTrackedTimeMapper()
    private val clip = Range(100, 200)

    @Test
    fun empty() {
        assertEquals(0L, subject.mapTrackedTime(emptyList(), clip))
    }

    @Test
    fun clipsToRange() {
        val intervals = listOf(Range(50, 120), Range(180, 300), Range(0, 90), Range(210, 220))
        assertEquals(20L + 20L, subject.mapTrackedTime(intervals, clip))
    }

    @Test
    fun overlapsAreCountedOnce() {
        val intervals = listOf(Range(110, 150), Range(130, 170), Range(120, 140), Range(180, 190))
        assertEquals(60L + 10L, subject.mapTrackedTime(intervals, clip))
    }

    @Test
    fun touchingIntervals() {
        val intervals = listOf(Range(100, 130), Range(130, 160))
        assertEquals(60L, subject.mapTrackedTime(intervals, clip))
    }
}
