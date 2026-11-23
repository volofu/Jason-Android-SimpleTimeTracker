package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkScheduleMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroWorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroWorkScheduleMapperTest {

    private val subject = PomodoroWorkScheduleMapper()

    @Test
    fun emptyIsDefault() {
        assertEquals(PomodoroWorkSchedule.Default, subject.fromString(""))
    }

    @Test
    fun garbageIsDefault() {
        assertEquals(PomodoroWorkSchedule.Default, subject.fromString("abc,1-,-2,5000-1,x"))
    }

    @Test
    fun roundTrip() {
        val schedule = PomodoroWorkSchedule.Default
            .with(DayOfWeek.SATURDAY, PomodoroWorkSchedule.Window(600, 840))
            .with(DayOfWeek.WEDNESDAY, PomodoroWorkSchedule.Window(0, 1440))
        assertEquals(schedule, subject.fromString(subject.toString(schedule)))
    }

    @Test
    fun sundayIsFirst() {
        val schedule = PomodoroWorkSchedule.Default
            .with(DayOfWeek.SUNDAY, PomodoroWorkSchedule.Window(1, 2))
        assertEquals("1-2,540-1140,540-1140,540-1140,540-1140,540-1140,540-1140", subject.toString(schedule))
    }
}
