package com.example.util.simpletimetracker.feature_pomodoro.timer.interactor

import com.example.util.simpletimetracker.core.interactor.GetRangeInteractor
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroSettingsInteractor
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroEntryTimeMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroTrackedTimeMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkWindowMapper
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.Range
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.statistics.model.RangeLength
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroInfo
import javax.inject.Inject

class PomodoroInfoInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val recordInteractor: RecordInteractor,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val recordTypeInteractor: RecordTypeInteractor,
    private val getRangeInteractor: GetRangeInteractor,
    private val getPomodoroSettingsInteractor: GetPomodoroSettingsInteractor,
    private val currentTimestampProvider: CurrentTimestampProvider,
    private val pomodoroEntryTimeMapper: PomodoroEntryTimeMapper,
    private val pomodoroTrackedTimeMapper: PomodoroTrackedTimeMapper,
    private val pomodoroWorkWindowMapper: PomodoroWorkWindowMapper,
) {

    suspend fun execute(): PomodoroInfo {
        val now = currentTimestampProvider.get()
        val dayRange = getRangeInteractor.getRange(RangeLength.Day)
        val weekRange = getRangeInteractor.getRange(RangeLength.Week)
        val startOfDayShift = prefsInteractor.getStartOfDayShift()

        val runningRecords = runningRecordInteractor.getAll()
        // If several activities are running (multitasking), show the one started last.
        val activeRunning = runningRecords.maxByOrNull { it.timeStarted }
        // Otherwise (paused or stopped) show what will be resumed by play.
        val lastRecord = if (activeRunning == null) recordInteractor.getPrev(now) else null
        val activeTypeId = activeRunning?.id ?: lastRecord?.typeId
        val activeComment = activeRunning?.comment ?: lastRecord?.comment.orEmpty()
        val activeType = activeTypeId?.let { recordTypeInteractor.get(it) }

        val activity = if (activeTypeId != null) {
            PomodoroInfo.Activity(
                typeId = activeTypeId,
                name = activeType?.name.orEmpty(),
                comment = activeComment,
            )
        } else {
            null
        }

        val entry = activeRunning?.let {
            pomodoroEntryTimeMapper.map(
                entryStartMs = it.timeStarted,
                nowMs = now,
                pomodoroStartedMs = prefsInteractor.getPomodoroModeStartedTimestampMs(),
                pomodoroPausedMs = prefsInteractor.getPomodoroModePausedTimestampMs(),
                settings = getPomodoroSettingsInteractor.execute(),
            )
        }

        // Day is always inside of the week, so one request is enough.
        val allIntervals = recordInteractor.getFromRange(weekRange)
            .map { it.typeId to Range(it.timeStarted, it.timeEnded) }
            .plus(runningRecords.map { it.id to Range(it.timeStarted, now) })
        val typeIntervals = allIntervals
            .filter { it.first == activeTypeId }
            .map { it.second }
        val allRanges = allIntervals.map { it.second }

        return PomodoroInfo(
            activity = activity,
            entry = entry,
            typeTodayMs = pomodoroTrackedTimeMapper.mapTrackedTime(typeIntervals, dayRange),
            typeWeekMs = pomodoroTrackedTimeMapper.mapTrackedTime(typeIntervals, weekRange),
            allTodayMs = pomodoroTrackedTimeMapper.mapTrackedTime(allRanges, dayRange),
            allWeekMs = pomodoroTrackedTimeMapper.mapTrackedTime(allRanges, weekRange),
            dayWindowMs = pomodoroWorkWindowMapper.mapElapsed(dayRange, now, startOfDayShift),
            weekWindowMs = pomodoroWorkWindowMapper.mapElapsed(weekRange, now, startOfDayShift),
        )
    }

    private suspend fun RecordInteractor.getFromRange(range: Range) =
        getWithParams(RecordInteractor.GetParam.FromRange(range))
}
