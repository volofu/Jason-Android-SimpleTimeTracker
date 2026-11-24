package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroCycleSequenceMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroCycleSequenceMapper.Cycle
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleSettings
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType.Break
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType.Focus
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType.LongBreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class PomodoroCycleSequenceMapperTest {

    private val subject = PomodoroCycleSequenceMapper()

    private fun min(value: Long) = TimeUnit.MINUTES.toMillis(value)

    // Focus 25, break 5, long break 15 after 2 periods: F25 B5 F25 L15 = 70.
    private val settings = PomodoroCycleSettings(min(25), min(5), min(15), 2)

    @Test
    fun endedInsideOnePeriod() {
        val result = subject.getEndedBetween(0, min(70), settings)
        assertEquals(
            listOf(
                Cycle(0, min(25), Focus),
                Cycle(min(25), min(30), Break),
                Cycle(min(30), min(55), Focus),
                Cycle(min(55), min(70), LongBreak),
            ),
            result,
        )
    }

    @Test
    fun alreadyLoggedAreSkipped() {
        val result = subject.getEndedBetween(min(25), min(56), settings)
        assertEquals(
            listOf(
                Cycle(min(25), min(30), Break),
                Cycle(min(30), min(55), Focus),
            ),
            result,
        )
    }

    @Test
    fun severalPeriods() {
        val result = subject.getEndedBetween(min(60), min(100), settings)
        // Long break ends at 70, next focus ends at 95, break at 100.
        assertEquals(
            listOf(
                Cycle(min(55), min(70), LongBreak),
                Cycle(min(70), min(95), Focus),
                Cycle(min(95), min(100), Break),
            ),
            result,
        )
    }

    @Test
    fun nothingEnded() {
        assertEquals(emptyList<Cycle>(), subject.getEndedBetween(min(1), min(24), settings))
        assertEquals(emptyList<Cycle>(), subject.getEndedBetween(min(10), min(10), settings))
    }

    @Test
    fun noLongBreak() {
        val noLong = settings.copy(periodsUntilLongBreak = 0)
        val result = subject.getEndedBetween(0, min(61), noLong)
        assertEquals(
            listOf(
                Cycle(0, min(25), Focus),
                Cycle(min(25), min(30), Break),
                Cycle(min(30), min(55), Focus),
                Cycle(min(55), min(60), Break),
            ),
            result,
        )
    }

    @Test
    fun zeroBreakIsSkipped() {
        val noBreak = settings.copy(breakTimeMs = 0, periodsUntilLongBreak = 0)
        val result = subject.getEndedBetween(0, min(50), noBreak)
        assertEquals(listOf(Cycle(0, min(25), Focus), Cycle(min(25), min(50), Focus)), result)
    }

    @Test
    fun cycleAt() {
        assertEquals(Cycle(0, min(25), Focus), subject.getAt(min(10), settings))
        assertEquals(Cycle(min(25), min(30), Break), subject.getAt(min(26), settings))
        assertEquals(Cycle(min(55), min(70), LongBreak), subject.getAt(min(60), settings))
        assertEquals(Cycle(min(70), min(95), Focus), subject.getAt(min(71), settings))
        assertNull(subject.getAt(-1, settings))
    }
}
