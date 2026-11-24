package com.example.util.simpletimetracker.domain.pomodoro.interactor

import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroCycleLogMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroCycleSequenceMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleLogEntry
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Keeps a history of finished pomodoro cycles. Cycles are not tracked in real time,
 * instead they are caught up from the pomodoro timeline whenever it is about to change
 * (pause, stop, skip) or is shown. This is exact because the timeline only changes
 * through these actions.
 */
class PomodoroCycleLogInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val getPomodoroStateInteractor: GetPomodoroStateInteractor,
    private val getPomodoroSettingsInteractor: GetPomodoroSettingsInteractor,
    private val pomodoroCycleSequenceMapper: PomodoroCycleSequenceMapper,
    private val pomodoroCycleLogMapper: PomodoroCycleLogMapper,
    private val currentTimestampProvider: CurrentTimestampProvider,
) {

    suspend fun getAll(): List<PomodoroCycleLogEntry> {
        return prefsInteractor.getPomodoroCycleLog().let(pomodoroCycleLogMapper::fromString)
    }

    /**
     * Writes cycles that ended since the last call. If [cutShort] is set, the cycle that is in
     * progress is also written as unfinished, use it before the cycle is skipped, restarted or stopped.
     */
    suspend fun sync(cutShort: Boolean = false) {
        if (getPomodoroStateInteractor.execute() is GetPomodoroStateInteractor.State.Stopped) return
        val started = prefsInteractor.getPomodoroModeStartedTimestampMs()
        val paused = prefsInteractor.getPomodoroModePausedTimestampMs()
        val now = currentTimestampProvider.get()
        val settings = getPomodoroSettingsInteractor.execute()

        // While pomodoro is paused its timeline is frozen.
        val virtualNow = (if (paused != 0L) paused else now) - started
        val logged = prefsInteractor.getPomodoroLoggedVirtualTimeMs()

        val ended = pomodoroCycleSequenceMapper.getEndedBetween(logged, virtualNow, settings)
        val newEntries = ended.map {
            PomodoroCycleLogEntry(
                endMs = started + it.endMs,
                type = it.type,
                actualMs = it.durationMs,
                plannedMs = it.durationMs,
            )
        }.toMutableList()
        var newLogged = ended.lastOrNull()?.endMs ?: logged

        if (cutShort) {
            val current = pomodoroCycleSequenceMapper.getAt(virtualNow, settings)
            if (current != null) {
                val actual = virtualNow - maxOf(current.startMs, newLogged)
                if (actual >= MIN_CUT_SHORT_MS) {
                    newEntries += PomodoroCycleLogEntry(
                        endMs = started + virtualNow,
                        type = current.type,
                        actualMs = actual,
                        plannedMs = current.durationMs,
                    )
                }
                newLogged = virtualNow
            }
        }

        if (newEntries.isNotEmpty()) save(newEntries)
        if (newLogged != logged) prefsInteractor.setPomodoroLoggedVirtualTimeMs(newLogged)
    }

    /**
     * Call after the pomodoro timeline was moved to the start of a cycle (skip, restart),
     * so that the new cycle is logged from its beginning.
     */
    suspend fun onTimelineChanged() {
        if (getPomodoroStateInteractor.execute() is GetPomodoroStateInteractor.State.Stopped) return
        val started = prefsInteractor.getPomodoroModeStartedTimestampMs()
        val virtualNow = currentTimestampProvider.get() - started
        // Timeline is moved to 1 ms before the start of a cycle, so a margin is needed
        // to not log the skipped cycle again.
        prefsInteractor.setPomodoroLoggedVirtualTimeMs(
            (virtualNow + TIMELINE_MARGIN_MS).coerceAtLeast(0L),
        )
    }

    suspend fun reset() {
        prefsInteractor.setPomodoroLoggedVirtualTimeMs(0)
    }

    private suspend fun save(newEntries: List<PomodoroCycleLogEntry>) {
        val threshold = currentTimestampProvider.get() - TimeUnit.DAYS.toMillis(KEEP_DAYS)
        val entries = (getAll() + newEntries).filter { it.endMs >= threshold }
        prefsInteractor.setPomodoroCycleLog(pomodoroCycleLogMapper.toString(entries))
    }

    companion object {
        // Skipping a cycle right after it started is not worth recording.
        private val MIN_CUT_SHORT_MS = TimeUnit.SECONDS.toMillis(5)
        private const val KEEP_DAYS = 35L
        private const val TIMELINE_MARGIN_MS = 2L
    }
}
