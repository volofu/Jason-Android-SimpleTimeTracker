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
        // Not null if a pause activity is running: pomodoro is paused and an infinite timer is shown.
        pauseElapsedMs: Long? = null,
    ): PomodoroTimerState {
        // Min increment of one pixel.
        val maxProgress = 1024 * Math.PI
        val isStarted = state !is GetPomodoroStateInteractor.State.Stopped
        val isPaused = state is GetPomodoroStateInteractor.State.Paused

        if (isPaused && pauseElapsedMs != null) {
            return mapPauseTimerState(
                elapsedMs = pauseElapsedMs,
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
            val comment = it.comment.trim()
            val text = if (comment.isEmpty()) name else "$name  \u00B7  $comment"
            resourceRepo.getString(R.string.pomodoro_info_paused_task, text)
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
            entryTimes = entryTimes,
            editButtonVisible = activity?.isRunning == true,
            typeLabel = if (hasActivity) typeName else "-",
            typeToday = formatDuration(info.typeTodayMs),
            typeWeek = formatDuration(info.typeWeekMs),
            allToday = formatDurationWithTotal(info.allTodayMs, info.dayWindowMs),
            allWeek = formatDurationWithTotal(info.allWeekMs, info.weekWindowMs),
            ratioToday = mapRatio(info.allTodayMs, info.dayWindowMs),
            ratioWeek = mapRatio(info.allWeekMs, info.weekWindowMs),
        )
    }

    private fun mapRatio(workedMs: Long, windowMs: Long): PomodoroInfoState.Ratio {
        if (windowMs <= 0L) return PomodoroInfoState.Ratio(text = "-", fraction = 0f)
        val ratio = workedMs.coerceAtLeast(0L).toDouble() / windowMs
        return PomodoroInfoState.Ratio(
            text = "${Math.round(ratio * 100)}%",
            // Tracking outside of working hours can exceed 100%, but chart can't.
            fraction = ratio.coerceIn(0.0, 1.0).toFloat(),
        )
    }

    // 1:23:45
    private fun formatClock(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
        val hr = totalSeconds / 3600
        val min = totalSeconds % 3600 / 60
        val sec = totalSeconds % 60
        return "$hr:${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }

    // 1h 25m
    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs.coerceAtLeast(0L))
        val hr = totalMinutes / 60
        val min = totalMinutes % 60
        val hrString = resourceRepo.getString(R.string.time_hour)
        val minString = resourceRepo.getString(R.string.time_minute)
        return if (hr > 0) "$hr$hrString $min$minString" else "$min$minString"
    }

    // 3h 10m / 6h 10m
    private fun formatDurationWithTotal(durationMs: Long, totalMs: Long): String {
        return "${formatDuration(durationMs)} / ${formatDuration(totalMs)}"
    }

    // Time on a pause activity is not limited, but the focus time is a reference
    // for how long is too long: ring fills and background turns from white to red.
    private fun mapPauseTimerState(
        elapsedMs: Long,
        settings: PomodoroCycleSettings,
        maxProgress: Int,
        timerUpdateMs: Long,
    ): PomodoroTimerState {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val fraction = if (settings.focusTimeMs > 0L) {
            (elapsed.toDouble() / settings.focusTimeMs).coerceIn(0.0, 1.0).toFloat()
        } else {
            1f
        }
        return PomodoroTimerState(
            maxProgress = maxProgress,
            progress = (fraction * maxProgress).toInt(),
            timerUpdateMs = timerUpdateMs,
            durationState = mapToDurationState(formatInterval(elapsed)),
            currentCycleHint = resourceRepo.getString(R.string.pomodoro_info_pause),
            pauseBackgroundFraction = fraction,
        )
    }

    private fun mapToDurationState(
        data: TimeState,
    ): PomodoroTimerState.DurationState {
        return PomodoroTimerState.DurationState(
            textHours = data.hr.toDuration(),
            textMinutes = data.min.toDuration(),
            textSeconds = data.sec.toDuration(),
            hoursIsVisible = data.hr > 0,
        )
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

    private data class TimeState(
        val hr: Long,
        val min: Long,
        val sec: Long,
    )
}