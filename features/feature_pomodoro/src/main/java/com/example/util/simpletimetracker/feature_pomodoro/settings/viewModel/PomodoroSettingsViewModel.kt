package com.example.util.simpletimetracker.feature_pomodoro.settings.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.BaseViewModel
import com.example.util.simpletimetracker.core.extension.lazySuspend
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroCycleNotificationInteractor
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkScheduleMapper
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroWorkSchedule
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.hintBig.HintBigViewData
import com.example.util.simpletimetracker.feature_pomodoro.settings.interactor.PomodoroSettingsViewDataInteractor
import com.example.util.simpletimetracker.feature_pomodoro.settings.model.PomodoroHintButtonType
import com.example.util.simpletimetracker.feature_pomodoro.settings.model.PomodoroScheduleItem
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.action.OpenSystemSettings
import com.example.util.simpletimetracker.navigation.params.screen.DateTimeDialogParams
import com.example.util.simpletimetracker.navigation.params.screen.DateTimeDialogType
import com.example.util.simpletimetracker.navigation.params.screen.DurationDialogParams
import com.example.util.simpletimetracker.navigation.params.screen.TypesSelectionDialogParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class PomodoroSettingsViewModel @Inject constructor(
    private val router: Router,
    private val resourceRepo: ResourceRepo,
    private val prefsInteractor: PrefsInteractor,
    private val pomodoroSettingsViewDataInteractor: PomodoroSettingsViewDataInteractor,
    private val pomodoroCycleNotificationInteractor: PomodoroCycleNotificationInteractor,
    private val pomodoroWorkScheduleMapper: PomodoroWorkScheduleMapper,
) : BaseViewModel() {

    val content: LiveData<List<ViewHolderType>> by lazySuspend { loadContent() }

    fun onVisible() = viewModelScope.launch {
        updateContent()
    }

    fun onHintActionClicked(type: HintBigViewData.ButtonType?) {
        when (type) {
            is PomodoroHintButtonType.PostPermissions -> {
                router.execute(OpenSystemSettings.Notifications)
            }
            is PomodoroHintButtonType.ExactAlarms -> {
                router.execute(OpenSystemSettings.ExactAlarms)
            }
        }
    }

    fun onBlockClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.PomodoroFocusTime ->
                onFocusTimeClicked()
            SettingsBlock.PomodoroBreakTime ->
                onBreakTimeClicked()
            SettingsBlock.PomodoroLongBreakTime ->
                onLongBreakTimeClicked()
            SettingsBlock.PomodoroPeriodsUntilLongBreak ->
                onPeriodsUntilLongBreakClicked()
            SettingsBlock.PomodoroShowMoreControls ->
                onShowMoreControlsClicked()
            SettingsBlock.PomodoroWorkActivities ->
                onWorkSelectionClicked(isCategory = false)
            SettingsBlock.PomodoroWorkCategories ->
                onWorkSelectionClicked(isCategory = true)
            SettingsBlock.PomodoroCountUp ->
                onCountUpClicked()
            else -> {
                PomodoroScheduleItem.all.firstOrNull { block == it.startBlock || block == it.endBlock }
                    ?.let { onScheduleClicked(it, isStart = block == it.startBlock) }
            }
        }
    }

    fun onDateTimeSet(timestamp: Long, tag: String?) = viewModelScope.launch {
        val parts = tag?.split(SCHEDULE_TAG_DELIMITER)
        if (parts?.getOrNull(0) != SCHEDULE_TAG_PREFIX) return@launch
        val item = PomodoroScheduleItem.all.firstOrNull { it.key == parts?.getOrNull(1) } ?: return@launch
        val isStart = parts?.getOrNull(2) == SCHEDULE_TAG_START

        val minute = Calendar.getInstance().run {
            timeInMillis = timestamp
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }
        var schedule = loadSchedule()
        item.days.forEach { day ->
            val window = schedule.get(day)
            val newWindow = if (isStart) window.copy(startMinute = minute) else window.copy(endMinute = minute)
            schedule = schedule.with(day, newWindow)
        }
        prefsInteractor.setPomodoroWorkSchedule(pomodoroWorkScheduleMapper.toString(schedule))
        updateContent()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSpinnerPositionSelected(block: SettingsBlock, position: Int) {
        // Do nothing.
    }

    fun onDurationSet(tag: String?, duration: Long) = viewModelScope.launch {
        when (tag) {
            FOCUS_TIME_DURATION_DIALOG_TAG -> {
                if (duration != 0L) prefsInteractor.setPomodoroFocusTime(duration)
            }
            BREAK_TIME_DURATION_DIALOG_TAG -> {
                prefsInteractor.setPomodoroBreakTime(duration)
            }
            LONG_BREAK_TIME_DURATION_DIALOG_TAG -> {
                prefsInteractor.setPomodoroLongBreakTime(duration)
            }
        }
        updateContent()
        pomodoroCycleNotificationInteractor.checkAndReschedule()
    }

    fun onCountSet(tag: String?, count: Long) = viewModelScope.launch {
        when (tag) {
            PERIODS_UNTIL_LONG_BREAK_DIALOG_TAG -> {
                prefsInteractor.setPomodoroPeriodsUntilLongBreak(count)
            }
        }
        updateContent()
        pomodoroCycleNotificationInteractor.checkAndReschedule()
    }

    fun onDurationDisabled(tag: String?) = viewModelScope.launch {
        when (tag) {
            FOCUS_TIME_DURATION_DIALOG_TAG -> {
                // Do nothing
            }
            BREAK_TIME_DURATION_DIALOG_TAG -> {
                prefsInteractor.setPomodoroBreakTime(0)
            }
            LONG_BREAK_TIME_DURATION_DIALOG_TAG -> {
                prefsInteractor.setPomodoroLongBreakTime(0)
            }
            PERIODS_UNTIL_LONG_BREAK_DIALOG_TAG -> {
                prefsInteractor.setPomodoroPeriodsUntilLongBreak(0)
            }
        }
        updateContent()
        pomodoroCycleNotificationInteractor.checkAndReschedule()
    }

    private fun onFocusTimeClicked() = viewModelScope.launch {
        val duration = prefsInteractor.getPomodoroFocusTime()
        DurationDialogParams(
            tag = FOCUS_TIME_DURATION_DIALOG_TAG,
            value = DurationDialogParams.Value.DurationSeconds(duration),
            hideDisableButton = true,
        ).let(router::navigate)
    }

    private fun onBreakTimeClicked() = viewModelScope.launch {
        val duration = prefsInteractor.getPomodoroBreakTime()
        DurationDialogParams(
            tag = BREAK_TIME_DURATION_DIALOG_TAG,
            value = DurationDialogParams.Value.DurationSeconds(duration),
        ).let(router::navigate)
    }

    private fun onLongBreakTimeClicked() = viewModelScope.launch {
        val duration = prefsInteractor.getPomodoroLongBreakTime()
        DurationDialogParams(
            tag = LONG_BREAK_TIME_DURATION_DIALOG_TAG,
            value = DurationDialogParams.Value.DurationSeconds(duration),
        ).let(router::navigate)
    }

    private fun onPeriodsUntilLongBreakClicked() = viewModelScope.launch {
        val count = prefsInteractor.getPomodoroPeriodsUntilLongBreak()
        DurationDialogParams(
            tag = PERIODS_UNTIL_LONG_BREAK_DIALOG_TAG,
            value = DurationDialogParams.Value.Count(count),
        ).let(router::navigate)
    }

    private fun onScheduleClicked(
        item: PomodoroScheduleItem,
        isStart: Boolean,
    ) = viewModelScope.launch {
        val window = loadSchedule().get(item.days.first())
        val minute = if (isStart) window.startMinute else window.endMinute
        val timestamp = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, minute)
        }.timeInMillis
        DateTimeDialogParams(
            tag = listOf(
                SCHEDULE_TAG_PREFIX,
                item.key,
                if (isStart) SCHEDULE_TAG_START else SCHEDULE_TAG_END,
            ).joinToString(SCHEDULE_TAG_DELIMITER),
            type = DateTimeDialogType.TIME,
            timestamp = timestamp,
            useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat(),
        ).let(router::navigate)
    }

    private suspend fun loadSchedule(): PomodoroWorkSchedule {
        return prefsInteractor.getPomodoroWorkSchedule()
            .let(pomodoroWorkScheduleMapper::fromString)
    }

    private fun onWorkSelectionClicked(isCategory: Boolean) = viewModelScope.launch {
        val selected = if (isCategory) {
            prefsInteractor.getPomodoroWorkCategories()
        } else {
            prefsInteractor.getPomodoroWorkActivities()
        }
        TypesSelectionDialogParams(
            tag = if (isCategory) WORK_CATEGORIES_SELECTION_TAG else WORK_ACTIVITIES_SELECTION_TAG,
            title = resourceRepo.getString(
                if (isCategory) R.string.pomodoro_settings_work_categories else R.string.pomodoro_settings_work_activities,
            ),
            subtitle = "",
            type = if (isCategory) {
                TypesSelectionDialogParams.Type.Category
            } else {
                TypesSelectionDialogParams.Type.Activity
            },
            selectedTypeIds = selected,
            selectedTagValues = emptyList(),
            selectedTagValueOnStart = emptyList(),
            isMultiSelectAvailable = true,
            idsShouldBeVisible = selected,
            showHints = false,
            allowTagValueSelection = false,
        ).let(router::navigate)
    }

    fun onDataSelected(tag: String?, dataIds: List<Long>) = viewModelScope.launch {
        when (tag) {
            WORK_CATEGORIES_SELECTION_TAG -> prefsInteractor.setPomodoroWorkCategories(dataIds)
            WORK_ACTIVITIES_SELECTION_TAG -> prefsInteractor.setPomodoroWorkActivities(dataIds)
            else -> return@launch
        }
        updateContent()
    }

    private fun onCountUpClicked() = viewModelScope.launch {
        prefsInteractor.setPomodoroCountUpDuringWorkHours(!prefsInteractor.getPomodoroCountUpDuringWorkHours())
        updateContent()
    }

    private fun onShowMoreControlsClicked() = viewModelScope.launch {
        val newValue = !prefsInteractor.getPomodoroShowMoreControls()
        prefsInteractor.setPomodoroShowMoreControls(newValue)
        updateContent()
    }

    private suspend fun updateContent() {
        content.set(loadContent())
    }

    private suspend fun loadContent(): List<ViewHolderType> {
        return pomodoroSettingsViewDataInteractor.execute()
    }

    companion object {
        const val FOCUS_TIME_DURATION_DIALOG_TAG = "FOCUS_TIME_DURATION_DIALOG_TAG"
        const val BREAK_TIME_DURATION_DIALOG_TAG = "BREAK_TIME_DURATION_DIALOG_TAG"
        const val LONG_BREAK_TIME_DURATION_DIALOG_TAG = "LONG_BREAK_TIME_DURATION_DIALOG_TAG"
        const val PERIODS_UNTIL_LONG_BREAK_DIALOG_TAG = "PERIODS_UNTIL_LONG_BREAK_DIALOG_TAG"
        private const val WORK_CATEGORIES_SELECTION_TAG = "POMODORO_WORK_CATEGORIES_SELECTION_TAG"
        private const val WORK_ACTIVITIES_SELECTION_TAG = "POMODORO_WORK_ACTIVITIES_SELECTION_TAG"
        private const val SCHEDULE_TAG_PREFIX = "POMODORO_SCHEDULE"
        private const val SCHEDULE_TAG_DELIMITER = "|"
        private const val SCHEDULE_TAG_START = "START"
        private const val SCHEDULE_TAG_END = "END"
    }
}
