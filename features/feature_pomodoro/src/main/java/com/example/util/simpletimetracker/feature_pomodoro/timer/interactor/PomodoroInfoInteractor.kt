package com.example.util.simpletimetracker.feature_pomodoro.timer.interactor

import com.example.util.simpletimetracker.core.interactor.GetRangeInteractor
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.category.interactor.CategoryInteractor
import com.example.util.simpletimetracker.domain.category.interactor.RecordTypeCategoryInteractor
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroStateInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroResumeTargetInteractor
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroEntryTimeMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroTrackedTimeMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkScheduleMapper
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkWindowMapper
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.Range
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.statistics.model.RangeLength
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroInfo
import javax.inject.Inject

class PomodoroInfoInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val recordInteractor: RecordInteractor,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val recordTypeInteractor: RecordTypeInteractor,
    private val getRangeInteractor: GetRangeInteractor,
    private val getPomodoroStateInteractor: GetPomodoroStateInteractor,
    private val pomodoroResumeTargetInteractor: PomodoroResumeTargetInteractor,
    private val currentTimestampProvider: CurrentTimestampProvider,
    private val pomodoroEntryTimeMapper: PomodoroEntryTimeMapper,
    private val pomodoroTrackedTimeMapper: PomodoroTrackedTimeMapper,
    private val pomodoroWorkWindowMapper: PomodoroWorkWindowMapper,
    private val pomodoroWorkScheduleMapper: PomodoroWorkScheduleMapper,
    private val categoryInteractor: CategoryInteractor,
    private val recordTypeCategoryInteractor: RecordTypeCategoryInteractor,
    private val resourceRepo: ResourceRepo,
    private val pomodoroStatsInteractor: PomodoroStatsInteractor,
) {

    suspend fun execute(): PomodoroInfo {
        val now = currentTimestampProvider.get()
        val dayRange = getRangeInteractor.getRange(RangeLength.Day)
        val weekRange = getRangeInteractor.getRange(RangeLength.Week)
        val startOfDayShift = prefsInteractor.getStartOfDayShift()
        val schedule = prefsInteractor.getPomodoroWorkSchedule()
            .let(pomodoroWorkScheduleMapper::fromString)
        val pomodoroState = getPomodoroStateInteractor.execute()
        val isPaused = pomodoroState is GetPomodoroStateInteractor.State.Paused

        val runningRecords = runningRecordInteractor.getAll()
        // If several activities are running (multitasking), show the one started last.
        val activeRunning = runningRecords.maxByOrNull { it.timeStarted }
        // Otherwise (paused or stopped) show what will be resumed by play.
        val lastRecord = if (activeRunning == null) recordInteractor.getPrev(now) else null
        val activeTypeId = activeRunning?.id ?: lastRecord?.typeId
        val activity = activeTypeId?.let {
            PomodoroInfo.Activity(
                typeId = it,
                name = recordTypeInteractor.get(it)?.name.orEmpty(),
                comment = activeRunning?.comment ?: lastRecord?.comment.orEmpty(),
                isRunning = activeRunning != null,
            )
        }

        val resumeTarget = if (isPaused) pomodoroResumeTargetInteractor.get() else null
        val pausedActivity = resumeTarget?.let {
            PomodoroInfo.Activity(
                typeId = it.typeId,
                name = recordTypeInteractor.get(it.typeId)?.name.orEmpty(),
                comment = it.comment,
                isRunning = false,
            )
        }

        val entry = if (pomodoroState !is GetPomodoroStateInteractor.State.Stopped) {
            val entryStarted = prefsInteractor.getPomodoroEntryStartedTimestampMs()
                .takeIf { it != 0L }
                ?: prefsInteractor.getPomodoroModeStartedTimestampMs()
            pomodoroEntryTimeMapper.map(
                entryStartMs = entryStarted,
                nowMs = now,
                pauseTotalMs = prefsInteractor.getPomodoroPauseTotalMs(),
                pausedTimestampMs = prefsInteractor.getPomodoroModePausedTimestampMs(),
            )
        } else {
            null
        }

        // Activity that is running without pomodoro shows an infinite timer as well.
        val countUp = when {
            activeRunning == null -> null
            isPaused -> PomodoroInfo.CountUp(startedMs = activeRunning.timeStarted, isPause = true)
            pomodoroState is GetPomodoroStateInteractor.State.Stopped &&
                prefsInteractor.getPomodoroCountUpDuringWorkHours() ->
                PomodoroInfo.CountUp(startedMs = activeRunning.timeStarted, isPause = false)
            else -> null
        }

        // Day is always inside of the week, so one request is enough.
        val allIntervals = recordInteractor.getFromRange(weekRange)
            .map { it.typeId to Range(it.timeStarted, it.timeEnded) }
            .plus(runningRecords.map { it.id to Range(it.timeStarted, now) })
        val typeIntervals = allIntervals
            .filter { it.first == activeTypeId }
            .map { it.second }
        val workGroupsData = loadWorkGroups()
        val groupIntervals = workGroupsData.map { group ->
            group to allIntervals
                .filter { group.typeIds == null || it.first in group.typeIds }
                .map { it.second }
        }
        val workGroups = groupIntervals.map { (group, intervals) ->
            PomodoroInfo.WorkGroup(
                name = group.name,
                todayMs = pomodoroTrackedTimeMapper.mapTrackedTime(intervals, dayRange),
                weekMs = pomodoroTrackedTimeMapper.mapTrackedTime(intervals, weekRange),
            )
        }
        val workIntervals = groupIntervals.flatMap { it.second }

        return PomodoroInfo(
            activity = activity,
            pausedActivity = pausedActivity,
            entry = entry,
            countUp = countUp,
            typeTodayMs = pomodoroTrackedTimeMapper.mapTrackedTime(typeIntervals, dayRange),
            typeWeekMs = pomodoroTrackedTimeMapper.mapTrackedTime(typeIntervals, weekRange),
            workGroups = workGroups,
            workTotalTodayMs = pomodoroTrackedTimeMapper.mapTrackedTime(workIntervals, dayRange),
            workTotalWeekMs = pomodoroTrackedTimeMapper.mapTrackedTime(workIntervals, weekRange),
            includeUnutilized = prefsInteractor.getPomodoroPieIncludeUnutilized(),
            stats = pomodoroStatsInteractor.execute(allIntervals, dayRange, weekRange),
            dayWindowMs = pomodoroWorkWindowMapper.mapElapsed(dayRange, now, startOfDayShift, schedule),
            weekWindowMs = pomodoroWorkWindowMapper.mapElapsed(weekRange, now, startOfDayShift, schedule),
        )
    }

    /**
     * Work is set up as activity categories and separate activities. Every category is a group
     * with its own statistics, separate activities that are not in a chosen category are
     * collected in the last group. If nothing is set up, all activities are work.
     */
    private suspend fun loadWorkGroups(): List<WorkGroupData> {
        val workCategoryIds = prefsInteractor.getPomodoroWorkCategories()
        val workTypeIds = prefsInteractor.getPomodoroWorkActivities().toSet()
        if (workCategoryIds.isEmpty() && workTypeIds.isEmpty()) {
            return listOf(WorkGroupData(name = null, typeIds = null))
        }

        val categories = categoryInteractor.getAll().associateBy { it.id }
        val relations = recordTypeCategoryInteractor.getAll()
        val groups = workCategoryIds.mapNotNull { categoryId ->
            val category = categories[categoryId] ?: return@mapNotNull null
            WorkGroupData(
                name = category.name,
                typeIds = relations
                    .filter { it.categoryId == categoryId }
                    .map { it.recordTypeId }
                    .toSet(),
            )
        }
        val coveredTypeIds = groups.flatMap { it.typeIds.orEmpty() }.toSet()
        val otherTypeIds = workTypeIds - coveredTypeIds
        return if (otherTypeIds.isNotEmpty()) {
            groups + WorkGroupData(
                name = resourceRepo.getString(R.string.pomodoro_info_other_work),
                typeIds = otherTypeIds,
            )
        } else {
            groups
        }
    }

    private data class WorkGroupData(
        val name: String?,
        // Null - all activities.
        val typeIds: Set<Long>?,
    )

    private suspend fun RecordInteractor.getFromRange(range: Range) =
        getWithParams(RecordInteractor.GetParam.FromRange(range))
}
