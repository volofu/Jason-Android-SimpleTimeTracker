package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleSettings
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType
import javax.inject.Inject

/**
 * Cycles of a pomodoro in its own (virtual) time, where 0 is the pomodoro start and pauses
 * are not counted. Order matches [PomodoroCycleDurationsMapper]:
 * focus-break-focus-break-...-focus-longBreak, or focus-break if long breaks are disabled.
 * Cycles that last 0 are skipped.
 */
class PomodoroCycleSequenceMapper @Inject constructor() {

    /**
     * Cycles that ended after [fromMs] and not later than [toMs].
     */
    fun getEndedBetween(
        fromMs: Long,
        toMs: Long,
        settings: PomodoroCycleSettings,
    ): List<Cycle> {
        val period = getPeriod(settings)
        val periodDuration = period.sumOf { it.durationMs }
        if (periodDuration <= 0L || toMs <= fromMs) return emptyList()

        val result = mutableListOf<Cycle>()
        var periodStart = (fromMs.coerceAtLeast(0L) / periodDuration) * periodDuration
        while (periodStart < toMs && result.size < MAX_CYCLES) {
            var start = periodStart
            for (item in period) {
                val end = start + item.durationMs
                if (end > fromMs && end <= toMs) {
                    result += Cycle(startMs = start, endMs = end, type = item.type)
                }
                start = end
            }
            periodStart += periodDuration
        }
        return result
    }

    /**
     * Cycle that is in progress at [timeMs].
     */
    fun getAt(
        timeMs: Long,
        settings: PomodoroCycleSettings,
    ): Cycle? {
        val period = getPeriod(settings)
        val periodDuration = period.sumOf { it.durationMs }
        if (periodDuration <= 0L || timeMs < 0L) return null

        var start = (timeMs / periodDuration) * periodDuration
        for (item in period) {
            val end = start + item.durationMs
            if (timeMs < end) return Cycle(startMs = start, endMs = end, type = item.type)
            start = end
        }
        return null
    }

    private fun getPeriod(settings: PomodoroCycleSettings): List<Item> {
        val periods = settings.periodsUntilLongBreak
        val items = mutableListOf<Item>()
        if (periods > 0) {
            for (index in 1..periods) {
                items += Item(PomodoroCycleType.Focus, settings.focusTimeMs)
                items += if (index < periods) {
                    Item(PomodoroCycleType.Break, settings.breakTimeMs)
                } else {
                    Item(PomodoroCycleType.LongBreak, settings.longBreakTimeMs)
                }
            }
        } else {
            items += Item(PomodoroCycleType.Focus, settings.focusTimeMs)
            items += Item(PomodoroCycleType.Break, settings.breakTimeMs)
        }
        return items.filter { it.durationMs > 0L }
    }

    private data class Item(
        val type: PomodoroCycleType,
        val durationMs: Long,
    )

    data class Cycle(
        val startMs: Long,
        val endMs: Long,
        val type: PomodoroCycleType,
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    companion object {
        private const val MAX_CYCLES = 5_000
    }
}
