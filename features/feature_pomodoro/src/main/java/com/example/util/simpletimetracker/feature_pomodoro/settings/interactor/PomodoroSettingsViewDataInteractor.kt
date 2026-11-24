package com.example.util.simpletimetracker.feature_pomodoro.settings.interactor

import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.core.repo.PermissionRepo
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_base_adapter.hintBig.HintBigViewData
import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek
import com.example.util.simpletimetracker.domain.pomodoro.mapper.PomodoroWorkScheduleMapper
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_pomodoro.settings.model.PomodoroScheduleItem
import com.example.util.simpletimetracker.feature_pomodoro.settings.model.PomodoroHintButtonType
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.feature_settings.views.SettingsCheckboxViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsDurationViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsRangeViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsSelectorViewData
import java.text.DateFormatSymbols
import java.util.Calendar
import javax.inject.Inject

class PomodoroSettingsViewDataInteractor @Inject constructor(
    private val resourceRepo: ResourceRepo,
    private val prefsInteractor: PrefsInteractor,
    private val permissionRepo: PermissionRepo,
    private val timeMapper: TimeMapper,
    private val pomodoroWorkScheduleMapper: PomodoroWorkScheduleMapper,
) {

    suspend fun execute(): List<ViewHolderType> {
        val result = mutableListOf<ViewHolderType>()

        if (!permissionRepo.areNotificationsEnabled()) {
            result += HintBigViewData(
                text = resourceRepo.getString(R.string.post_notifications),
                infoIconVisible = false,
                closeIconVisible = false,
                button = HintBigViewData.Button.Present(
                    text = resourceRepo.getString(R.string.schedule_exact_alarms_open_settings),
                    type = PomodoroHintButtonType.PostPermissions,
                ),
            )
        } else if (!permissionRepo.canScheduleExactAlarms()) {
            result += HintBigViewData(
                text = resourceRepo.getString(R.string.schedule_exact_alarms),
                infoIconVisible = false,
                closeIconVisible = false,
                button = HintBigViewData.Button.Present(
                    text = resourceRepo.getString(R.string.schedule_exact_alarms_open_settings),
                    type = PomodoroHintButtonType.ExactAlarms,
                ),
            )
        }

        val focusViewData = prefsInteractor.getPomodoroFocusTime()
            .let(::mapDuration)
        result += SettingsSelectorViewData(
            block = SettingsBlock.PomodoroFocusTime,
            title = resourceRepo.getString(R.string.pomodoro_state_focus),
            subtitle = "",
            selectedValue = focusViewData.text,
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )

        val breakViewData = prefsInteractor.getPomodoroBreakTime()
            .let(::mapDuration)
        result += SettingsSelectorViewData(
            block = SettingsBlock.PomodoroBreakTime,
            title = resourceRepo.getString(R.string.pomodoro_state_break),
            subtitle = "",
            selectedValue = breakViewData.text,
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )

        val periodsUntilLongBreak = prefsInteractor.getPomodoroPeriodsUntilLongBreak()
            .let(::mapCount)

        if (periodsUntilLongBreak.enabled) {
            val longBreakViewData = prefsInteractor.getPomodoroLongBreakTime()
                .let(::mapDuration)
            result += SettingsSelectorViewData(
                block = SettingsBlock.PomodoroLongBreakTime,
                title = resourceRepo.getString(R.string.pomodoro_state_long_break),
                subtitle = "",
                selectedValue = longBreakViewData.text,
                bottomSpaceIsVisible = true,
                dividerIsVisible = false,
                backgroundIsVisible = false,
            )
        }

        result += SettingsSelectorViewData(
            block = SettingsBlock.PomodoroPeriodsUntilLongBreak,
            title = resourceRepo.getString(R.string.pomodoro_settings_periods_until_long_break),
            subtitle = "",
            selectedValue = periodsUntilLongBreak.text,
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )

        result += SettingsCheckboxViewData(
            block = SettingsBlock.PomodoroShowMoreControls,
            title = resourceRepo.getString(R.string.pomodoro_settings_show_more_controls),
            subtitle = "",
            isChecked = prefsInteractor.getPomodoroShowMoreControls(),
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )

        result += SettingsSelectorViewData(
            block = SettingsBlock.PomodoroWorkCategories,
            title = resourceRepo.getString(R.string.pomodoro_settings_work_categories),
            subtitle = "",
            selectedValue = mapWorkCount(prefsInteractor.getPomodoroWorkCategories().size),
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )
        result += SettingsSelectorViewData(
            block = SettingsBlock.PomodoroWorkActivities,
            title = resourceRepo.getString(R.string.pomodoro_settings_work_activities),
            subtitle = "",
            selectedValue = mapWorkCount(prefsInteractor.getPomodoroWorkActivities().size),
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )
        result += SettingsCheckboxViewData(
            block = SettingsBlock.PomodoroCountUp,
            title = resourceRepo.getString(R.string.pomodoro_settings_count_up),
            subtitle = resourceRepo.getString(R.string.pomodoro_settings_count_up_hint),
            isChecked = prefsInteractor.getPomodoroCountUpDuringWorkHours(),
            bottomSpaceIsVisible = true,
            dividerIsVisible = false,
            backgroundIsVisible = false,
        )

        result += loadScheduleViewData()

        return result
    }

    private suspend fun loadScheduleViewData(): List<ViewHolderType> {
        val schedule = prefsInteractor.getPomodoroWorkSchedule()
            .let(pomodoroWorkScheduleMapper::fromString)
        val useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat()
        val mixed = resourceRepo.getString(R.string.pomodoro_settings_work_hours_mixed)

        return PomodoroScheduleItem.all.map { item ->
            val windows = item.days.map(schedule::get).distinct()
            val window = windows.singleOrNull()
            SettingsRangeViewData(
                blockStart = item.startBlock,
                blockEnd = item.endBlock,
                title = mapScheduleTitle(item),
                start = window?.let { formatMinute(it.startMinute, useMilitaryTime) } ?: mixed,
                end = window?.let { formatMinute(it.endMinute, useMilitaryTime) } ?: mixed,
                dividerIsVisible = false,
            )
        }
    }

    private fun mapWorkCount(count: Int): String {
        return if (count == 0) {
            resourceRepo.getString(R.string.pomodoro_settings_work_none)
        } else {
            resourceRepo.getString(R.string.pomodoro_settings_work_count, count)
        }
    }

    private fun mapScheduleTitle(item: PomodoroScheduleItem): String {
        return when (item) {
            PomodoroScheduleItem.weekdays ->
                resourceRepo.getString(R.string.pomodoro_settings_work_hours_weekdays)
            PomodoroScheduleItem.weekend ->
                resourceRepo.getString(R.string.pomodoro_settings_work_hours_weekend)
            else -> resourceRepo.getString(
                R.string.pomodoro_settings_work_hours_day,
                getDayName(item.days.first()),
            )
        }
    }

    private fun getDayName(dayOfWeek: DayOfWeek): String {
        // Calendar.SUNDAY is 1 and symbols are indexed by it.
        return DateFormatSymbols.getInstance().weekdays[dayOfWeek.ordinal + 1]
    }

    private fun formatMinute(minuteOfDay: Int, useMilitaryTime: Boolean): String {
        val timestamp = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, minuteOfDay)
        }.timeInMillis
        return timeMapper.formatTime(
            time = timestamp,
            useMilitaryTime = useMilitaryTime,
            showSeconds = false,
        )
    }

    private fun mapDuration(value: Long): SettingsDurationViewData {
        return SettingsDurationViewData(
            text = timeMapper.formatDuration(value),
            enabled = value > 0,
        )
    }

    private fun mapCount(value: Long): SettingsDurationViewData {
        return if (value > 0) {
            SettingsDurationViewData(
                text = value.toString(),
                enabled = true,
            )
        } else {
            SettingsDurationViewData(
                text = resourceRepo.getString(R.string.settings_inactivity_reminder_disabled),
                enabled = false,
            )
        }
    }
}