package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.record.model.Range
import javax.inject.Inject

class PomodoroTrackedTimeMapper @Inject constructor() {

    /**
     * Total length of the union of [intervals] clipped to [clipTo]. Overlapping intervals
     * (multitasking) are counted once.
     */
    fun mapTrackedTime(
        intervals: List<Range>,
        clipTo: Range,
    ): Long {
        val clipped = intervals
            .map { Range(maxOf(it.timeStarted, clipTo.timeStarted), minOf(it.timeEnded, clipTo.timeEnded)) }
            .filter { it.timeEnded > it.timeStarted }
            .sortedBy { it.timeStarted }

        var result = 0L
        var currentStart = 0L
        var currentEnd = 0L
        var hasCurrent = false
        clipped.forEach {
            if (hasCurrent && it.timeStarted <= currentEnd) {
                currentEnd = maxOf(currentEnd, it.timeEnded)
            } else {
                if (hasCurrent) result += currentEnd - currentStart
                currentStart = it.timeStarted
                currentEnd = it.timeEnded
                hasCurrent = true
            }
        }
        if (hasCurrent) result += currentEnd - currentStart
        return result
    }
}
