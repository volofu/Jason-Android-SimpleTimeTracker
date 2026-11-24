package com.example.util.simpletimetracker.domain.mapper

import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroCycleLogMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleLogEntry
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroCycleLogMapperTest {

    private val subject = PomodoroCycleLogMapper()

    @Test
    fun roundTrip() {
        val entries = listOf(
            PomodoroCycleLogEntry(1000, PomodoroCycleType.Focus, 500, 500),
            PomodoroCycleLogEntry(2000, PomodoroCycleType.Break, 100, 300),
            PomodoroCycleLogEntry(3000, PomodoroCycleType.LongBreak, 900, 900),
        )
        assertEquals(entries, subject.fromString(subject.toString(entries)))
    }

    @Test
    fun emptyAndGarbage() {
        assertEquals(emptyList<PomodoroCycleLogEntry>(), subject.fromString(""))
        assertEquals(emptyList<PomodoroCycleLogEntry>(), subject.fromString("x;1|Z|1|1;1|F|a|1"))
    }

    @Test
    fun completedAndStart() {
        val completed = PomodoroCycleLogEntry(1000, PomodoroCycleType.Focus, 500, 500)
        val skipped = PomodoroCycleLogEntry(1000, PomodoroCycleType.Break, 100, 300)
        assertTrue(completed.isCompleted)
        assertFalse(skipped.isCompleted)
        assertEquals(500L, completed.startMs)
    }
}
