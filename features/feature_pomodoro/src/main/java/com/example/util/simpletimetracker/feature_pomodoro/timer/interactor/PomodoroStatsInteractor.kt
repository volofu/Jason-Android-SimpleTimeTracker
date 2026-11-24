package com.example.util.simpletimetracker.feature_pomodoro.timer.interactor

import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroBreakCategoryInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroCycleLogInteractor
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroTrackedTimeMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleLogEntry
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType
import com.example.util.simpletimetracker.domain.record.model.Range
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroStats
import javax.inject.Inject

class PomodoroStatsInteractor @Inject constructor(
    private val pomodoroCycleLogInteractor: PomodoroCycleLogInteractor,
    private val pomodoroBreakCategoryInteractor: PomodoroBreakCategoryInteractor,
    private val pomodoroTrackedTimeMapper: PomodoroTrackedTimeMapper,
    private val recordTypeInteractor: RecordTypeInteractor,
) {

    /**
     * @param intervals tracked time (activity id and when it was tracked) that covers the week.
     */
    suspend fun execute(
        intervals: List<Pair<Long, Range>>,
        dayRange: Range,
        weekRange: Range,
    ): PomodoroStats {
        pomodoroCycleLogInteractor.sync()
        val entries = pomodoroCycleLogInteractor.getAll()
        val typeNames = recordTypeInteractor.getAll().associate { it.id to it.name }
        val breakTypeIds = pomodoroBreakCategoryInteractor.getBreakTypeIds()
        val breakIntervals = intervals
            .filter { it.first in breakTypeIds }
            .map { it.second }

        fun getPeriod(range: Range): PomodoroStats.Period {
            val inRange = entries.filter { it.endMs >= range.timeStarted && it.endMs < range.timeEnded }

            val completed = inRange.filter { it.type == PomodoroCycleType.Focus && it.isCompleted }
            val byActivity = completed
                .mapNotNull { findActivity(it, intervals) }
                .groupingBy { it }
                .eachCount()
                .map { (typeId, count) ->
                    PomodoroStats.ActivityCount(
                        name = typeNames[typeId].orEmpty(),
                        count = count,
                    )
                }
                .sortedByDescending { it.count }

            var shortBreakMs = 0L
            var longBreakMs = 0L
            var plannedBreakMs = 0L
            inRange.filter { it.type != PomodoroCycleType.Focus }.forEach { entry ->
                val taken = pomodoroTrackedTimeMapper
                    .mapTrackedTime(breakIntervals, Range(entry.startMs, entry.endMs))
                    .coerceAtMost(entry.plannedMs)
                plannedBreakMs += entry.plannedMs
                if (entry.type == PomodoroCycleType.LongBreak) longBreakMs += taken else shortBreakMs += taken
            }

            return PomodoroStats.Period(
                completedCount = byActivity.sumOf { it.count },
                completedByActivity = byActivity,
                shortBreakMs = shortBreakMs,
                longBreakMs = longBreakMs,
                plannedBreakMs = plannedBreakMs,
            )
        }

        return PomodoroStats(
            today = getPeriod(dayRange),
            week = getPeriod(weekRange),
        )
    }

    // Activity that was tracked the most during the cycle. Cycles without an activity do not count.
    private fun findActivity(
        entry: PomodoroCycleLogEntry,
        intervals: List<Pair<Long, Range>>,
    ): Long? {
        return intervals
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ranges) ->
                pomodoroTrackedTimeMapper.mapTrackedTime(ranges, Range(entry.startMs, entry.endMs))
            }
            .filterValues { it > 0L }
            .maxByOrNull { it.value }
            ?.key
    }
}
