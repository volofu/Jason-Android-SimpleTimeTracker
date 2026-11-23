package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkWindowMapper
import com.example.util.simpletimetracker.domain.record.model.Range
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PomodoroWorkWindowMapperTest {

    private val subject = PomodoroWorkWindowMapper()
    private val timeZone = TimeZone.getTimeZone("UTC")

    private fun time(day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        }.timeInMillis
    }

    private fun hours(value: Long) = TimeUnit.HOURS.toMillis(value)

    // Monday 16 Nov - Sunday 22 Nov 2026, today is Monday 23 Nov for the day tests.
    private val day = Range(time(23, 0), time(24, 0))
    private val week = Range(time(23, 0), time(30, 0))

    private fun elapsed(range: Range, now: Long, shift: Long = 0L) =
        subject.mapElapsed(range = range, nowMs = now, startOfDayShiftMs = shift, timeZone = timeZone)

    @Test
    fun beforeWindow() {
        assertEquals(0L, elapsed(day, time(23, 8, 30)))
    }

    @Test
    fun insideWindow() {
        assertEquals(hours(3), elapsed(day, time(23, 12)))
    }

    @Test
    fun afterWindow() {
        assertEquals(hours(10), elapsed(day, time(23, 21)))
    }

    @Test
    fun weekIncludesFullPastDays() {
        // Monday-Wednesday 10h each at Wed 19:00 plus nothing after.
        assertEquals(hours(30), elapsed(week, time(25, 19)))
        // Wednesday 10:30 is 1.5h into the third day.
        assertEquals(hours(20) + hours(3) / 2, elapsed(week, time(25, 10, 30)))
    }

    @Test
    fun fullWeek() {
        assertEquals(hours(70), elapsed(week, time(30, 5)))
    }

    @Test
    fun startOfDayShift() {
        val shift = hours(4)
        val shiftedDay = Range(time(23, 4), time(24, 4))
        // 02:00 on 24th still belongs to 23rd day, which has fully passed the window.
        assertEquals(hours(10), elapsed(shiftedDay, time(24, 2), shift))
        // 05:00 on 23rd is before the window of the 23rd.
        assertEquals(0L, elapsed(shiftedDay, time(23, 5), shift))
    }
}
