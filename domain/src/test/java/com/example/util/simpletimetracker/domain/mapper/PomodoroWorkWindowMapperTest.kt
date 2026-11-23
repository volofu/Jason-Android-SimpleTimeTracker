package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkWindowMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroWorkSchedule
import com.example.util.simpletimetracker.domain.record.model.Range
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PomodoroWorkWindowMapperTest {

    private val subject = PomodoroWorkWindowMapper()
    private val timeZone = TimeZone.getTimeZone("UTC")

    // 23 Nov 2026 is Monday.
    private fun time(day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        }.timeInMillis
    }

    private fun hours(value: Long) = TimeUnit.HOURS.toMillis(value)

    private val day = Range(time(23, 0), time(24, 0))
    private val week = Range(time(23, 0), time(30, 0))
    private val schedule = PomodoroWorkSchedule.Default

    private fun elapsed(
        range: Range,
        now: Long,
        shift: Long = 0L,
        schedule: PomodoroWorkSchedule = this.schedule,
    ) = subject.mapElapsed(
        range = range, nowMs = now, startOfDayShiftMs = shift, schedule = schedule, timeZone = timeZone,
    )

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
        assertEquals(hours(30), elapsed(week, time(25, 19)))
        assertEquals(hours(20) + hours(3) / 2, elapsed(week, time(25, 10, 30)))
    }

    @Test
    fun fullWeek() {
        assertEquals(hours(70), elapsed(week, time(30, 5)))
    }

    @Test
    fun customWindowForEveryDay() {
        val custom = schedule
            // Monday 8:30 - 12:00.
            .with(DayOfWeek.MONDAY, PomodoroWorkSchedule.Window(8 * 60 + 30, 12 * 60))
            // Tuesday is a day off.
            .with(DayOfWeek.TUESDAY, PomodoroWorkSchedule.Window(0, 0))
        // Monday 3.5h + Tuesday 0 + Wednesday 10h.
        assertEquals(hours(7) / 2 + hours(10), elapsed(week, time(25, 21), schedule = custom))
        // Monday at 10:00 is 1.5h in.
        assertEquals(hours(3) / 2, elapsed(day, time(23, 10), schedule = custom))
    }

    @Test
    fun weekendWindowIsDifferent() {
        val custom = schedule
            .with(DayOfWeek.SATURDAY, PomodoroWorkSchedule.Window(10 * 60, 14 * 60))
            .with(DayOfWeek.SUNDAY, PomodoroWorkSchedule.Window(10 * 60, 14 * 60))
        // 5 weekdays * 10h + 2 weekend days * 4h.
        assertEquals(hours(58), elapsed(week, time(30, 5), schedule = custom))
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
