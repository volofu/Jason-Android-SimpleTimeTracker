package com.example.util.simpletimetracker.feature_pomodoro.timer.mapper

import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.extension.toDuration
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroStateInteractor
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroCycleDurationsMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleSettings
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroButtonState
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroInfo
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroInfoState
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroPieSlice
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroTimerState
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PomodoroViewDataMapper @Inject constructor(
    private val resourceRepo: ResourceRepo,
    private val pomodoroCycleDurationsMapper: PomodoroCycleDurationsMapper,
) {

    fun mapButtonState(
        showMoreControls: Boolean,
        state: GetPomodoroStateInteractor.State,
    ): PomodoroButtonState {
        val isStarted = state is GetPomodoroStateInteractor.State.Running
        val iconResId = if (isStarted) {
            R.drawable.button_stop
        } else {
            R.drawable.button_play
        }
        val buttonsOrder = if (showMoreControls) {
            listOf(
                PomodoroButtonState.Button.Prev,
                PomodoroButtonState.Button.Start,
                PomodoroButtonState.Button.Next,
                PomodoroButtonState.Button.Restart,
                PomodoroButtonState.Button.Pause,
            )
        } else {
            listOf(
                PomodoroButtonState.Button.Restart,
                PomodoroButtonState.Button.Start,
                PomodoroButtonState.Button.Next,
            )
        }

        return PomodoroButtonState(
            iconResId = iconResId,
            additionalButtonsVisible = isStarted,
            buttonsOrder = buttonsOrder,
        )
    }

    fun mapTimerState(
        state: GetPomodoroStateInteractor.State,
        timeStartedMs: Long,
        timePausedMs: Long,
        timerUpdateMs: Long,
        settings: PomodoroCycleSettings,
        // Not null if an infinite timer is shown instead of pomodoro cycles.
        countUpElapsedMs: Long? = null,
        countUpIsPause: Boolean = true,
    ): PomodoroTimerState {
        // Min increment of one pixel.
        val maxProgress = 1024 * Math.PI
        val isStarted = state !is GetPomodoroStateInteractor.State.Stopped
        val isPaused = state is GetPomodoroStateInteractor.State.Paused

        if (countUpElapsedMs != null && !isStartedAndRunning(state)) {
            return mapPauseTimerState(
                elapsedMs = countUpElapsedMs,
                hint = if (countUpIsPause) R.string.pomodoro_info_pause else R.string.pomodoro_info_elapsed,
                settings = settings,
                maxProgress = maxProgress.toInt(),
                timerUpdateMs = timerUpdateMs,
            )
        }

        return if (isStarted) {
            val timerUpdate = if (isPaused) 0 else timerUpdateMs
            val result = pomodoroCycleDurationsMapper.map(
                timeStartedMs = if (isPaused) {
                    timeStartedMs + System.currentTimeMillis() - timePausedMs
                } else {
                    timeStartedMs
                },
                settings = settings,
            )
            val currentCycle = result.cycleType
            val cycleDuration = pomodoroCycleDurationsMapper.mapToCycleTime(
                cycleType = result.cycleType,
                settings = settings,
            )
            val currentCycleDuration = result.currentCycleDurationMs

            val timeLeft = cycleDuration - currentCycleDuration
            val progression = (currentCycleDuration + timerUpdate) *
                maxProgress / cycleDuration
            val times = formatInterval(timeLeft)

            PomodoroTimerState(
                maxProgress = maxProgress.toInt(),
                progress = progression.toInt(),
                timerUpdateMs = timerUpdate,
                durationState = mapToDurationState(times),
                currentCycleHint = mapCurrentStateHint(currentCycle),
            )
        } else {
            val times = formatInterval(settings.focusTimeMs)
            val currentCycle = PomodoroCycleType.Focus

            PomodoroTimerState(
                maxProgress = maxProgress.toInt(),
                progress = 0,
                timerUpdateMs = timerUpdateMs,
                durationState = mapToDurationState(times),
                currentCycleHint = mapCurrentStateHint(currentCycle),
            )
        }
    }

    fun mapInfoState(
        info: PomodoroInfo,
    ): PomodoroInfoState {
        val activity = info.activity
        val hasActivity = activity != null
        val hasComment = activity?.comment?.isNotBlank() == true
        val typeName = activity?.name?.takeUnless { it.isEmpty() }
            ?: resourceRepo.getString(R.string.pomodoro_info_no_activity)

        val entryTimes = if (info.entry != null) {
            listOf(
                R.string.pomodoro_info_total to info.entry.totalMs,
                R.string.pomodoro_state_focus to info.entry.focusMs,
                R.string.pomodoro_info_pause to info.entry.pauseMs,
            ).joinToString(separator = "  \u00B7  ") { (label, value) ->
                "${resourceRepo.getString(label)} ${formatClock(value)}"
            }
        } else {
            resourceRepo.getString(R.string.pomodoro_info_not_running)
        }

        val pausedActivity = info.pausedActivity?.let {
            val name = it.name.ifEmpty { resourceRepo.getString(R.string.pomodoro_info_no_activity) }
            resourceRepo.getString(R.string.pomodoro_info_paused_task, name)
        }.orEmpty()
        val pausedComment = info.pausedActivity?.let {
            val comment = it.comment.trim()
                .ifEmpty { resourceRepo.getString(R.string.pomodoro_info_no_comment) }
            resourceRepo.getString(R.string.pomodoro_info_paused_comment, comment)
        }.orEmpty()

        return PomodoroInfoState(
            activityName = typeName,
            comment = if (hasComment) {
                activity?.comment.orEmpty().trim()
            } else {
                resourceRepo.getString(R.string.pomodoro_info_no_comment)
            },
            isCommentPlaceholder = !hasComment,
            pausedActivity = pausedActivity,
            pausedComment = pausedComment,
            entryTimes = entryTimes,
            editButtonVisible = activity?.isRunning == true,
            rows = mapRows(info, if (hasActivity) typeName else "-"),
            ratioToday = mapRatio(
                workedMs = info.workTotalTodayMs,
                windowMs = info.dayWindowMs,
                groupsMs = info.workGroups.map { it.todayMs },
                includeUnutilized = info.includeUnutilized,
            ),
            ratioWeek = mapRatio(
                workedMs = info.workTotalWeekMs,
                windowMs = info.weekWindowMs,
                groupsMs = info.workGroups.map { it.weekMs },
                includeUnutilized = info.includeUnutilized,
            ),
            includeUnutilized = info.includeUnutilized,
        )
    }

    private fun mapRows(
        info: PomodoroInfo,
        typeName: String,
    ): List<PomodoroInfoState.Row> {
        val result = mutableListOf<PomodoroInfoState.Row>()
        // Current activity.
        result += PomodoroInfoState.Row(
            name = typeName,
            today = formatHm(info.typeTodayMs),
            week = formatHm(info.typeWeekMs),
            color = null,
        )
        // Work.
        info.workGroups.forEachIndexed { index, group ->
            result += PomodoroInfoState.Row(
                name = group.name ?: resourceRepo.getString(R.string.pomodoro_info_all_activities),
                today = formatHmWithTotal(group.todayMs, info.dayWindowMs),
                week = formatHmWithTotal(group.weekMs, info.weekWindowMs),
                color = getGroupColor(index),
            )
        }
        // Several groups need a total, one is a total already.
        if (info.workGroups.size > 1) {
            result += PomodoroInfoState.Row(
                name = resourceRepo.getString(R.string.pomodoro_info_total),
                today = formatHmWithTotal(info.workTotalTodayMs, info.dayWindowMs),
                week = formatHmWithTotal(info.workTotalWeekMs, info.weekWindowMs),
                color = null,
                isTotal = true,
            )
        }
        return result
    }

    private fun mapRatio(
        workedMs: Long,
        windowMs: Long,
        groupsMs: List<Long>,
        includeUnutilized: Boolean,
    ): PomodoroInfoState.Ratio {
        val text = if (windowMs > 0L) {
            "${Math.round(workedMs.coerceAtLeast(0L).toDouble() / windowMs * 100)}%"
        } else {
            "-"
        }

        // Groups can overlap (an activity is in several categories),
        // so slices are based on their sum to always fit in the circle.
        val sum = groupsMs.sumOf { it.coerceAtLeast(0L) }
        val unutilizedMs = if (includeUnutilized) (windowMs - sum).coerceAtLeast(0L) else 0L
        val base = (sum + unutilizedMs).toDouble()
        val slices = if (base > 0) {
            groupsMs.mapIndexed { index, value ->
                PomodoroPieSlice((value.coerceAtLeast(0L) / base).toFloat(), getGroupColor(index))
            } + PomodoroPieSlice((unutilizedMs / base).toFloat(), UNUTILIZED_COLOR)
        } else {
            emptyList()
        }
        return PomodoroInfoState.Ratio(text = text, slices = slices)
    }

    private fun getGroupColor(index: Int): Int {
        return GROUP_COLORS[index % GROUP_COLORS.size]
    }

    // 1:23:45
    private fun formatClock(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
        val hr = totalSeconds / 3600
        val min = totalSeconds % 3600 / 60
        val sec = totalSeconds % 60
        return "$hr:${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }

    // 1:25
    private fun formatHm(durationMs: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs.coerceAtLeast(0L))
        return "${totalMinutes / 60}:${(totalMinutes % 60).toString().padStart(2, '0')}"
    }

    // 3:10 / 6:10
    private fun formatHmWithTotal(durationMs: Long, totalMs: Long): String {
        return "${formatHm(durationMs)} / ${formatHm(totalMs)}"
    }

    private fun formatInterval(interval: Long): TimeState {
        val hr: Long = TimeUnit.MILLISECONDS.toHours(
            interval,
        )
        val min: Long = TimeUnit.MILLISECONDS.toMinutes(
            interval - TimeUnit.HOURS.toMillis(hr),
        )
        val sec: Long = TimeUnit.MILLISECONDS.toSeconds(
            interval - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min),
        )
        return TimeState(
            hr = hr,
            min = min,
            sec = sec,
        )
    }

    private fun mapCurrentStateHint(
        cycleType: PomodoroCycleType,
    ): String {
        return when (cycleType) {
            is PomodoroCycleType.Focus -> R.string.pomodoro_state_focus
            is PomodoroCycleType.Break -> R.string.pomodoro_state_break
            is PomodoroCycleType.LongBreak -> R.string.pomodoro_state_long_break
        }.let(resourceRepo::getString)
    }

    companion object {
        private const val UNUTILIZED_COLOR = 0xFF9E9E9E.toInt()
        private val GROUP_COLORS = listOf(
            0xFF26A69A.toInt(),
            0xFFFFA726.toInt(),
            0xFF42A5F5.toInt(),
            0xFFAB47BC.toInt(),
            0xFF9CCC65.toInt(),
            0xFFEC407A.toInt(),
        )
    }

    private data class TimeState(
        val hr: Long,
        val min: Long,
        val sec: Long,
    )
}