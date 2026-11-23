package com.example.util.simpletimetracker.feature_pomodoro.settings.model

import com.example.util.simpletimetracker.domain.daysOfWeek.model.DayOfWeek
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock

/**
 * A row in work hours settings: either a group of days (weekdays, weekend) or a single day.
 */
data class PomodoroScheduleItem(
    val key: String,
    val days: List<DayOfWeek>,
    val startBlock: SettingsBlock,
    val endBlock: SettingsBlock,
) {

    companion object {
        val weekdays = PomodoroScheduleItem(
            key = "WEEKDAYS",
            days = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
            startBlock = SettingsBlock.PomodoroScheduleWeekdaysStart,
            endBlock = SettingsBlock.PomodoroScheduleWeekdaysEnd,
        )

        val weekend = PomodoroScheduleItem(
            key = "WEEKEND",
            days = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            startBlock = SettingsBlock.PomodoroScheduleWeekendStart,
            endBlock = SettingsBlock.PomodoroScheduleWeekendEnd,
        )

        val groups = listOf(weekdays, weekend)

        // Monday first.
        val days: List<PomodoroScheduleItem> = listOf(
            day(DayOfWeek.MONDAY, SettingsBlock.PomodoroScheduleMondayStart, SettingsBlock.PomodoroScheduleMondayEnd),
            day(DayOfWeek.TUESDAY, SettingsBlock.PomodoroScheduleTuesdayStart, SettingsBlock.PomodoroScheduleTuesdayEnd),
            day(DayOfWeek.WEDNESDAY, SettingsBlock.PomodoroScheduleWednesdayStart, SettingsBlock.PomodoroScheduleWednesdayEnd),
            day(DayOfWeek.THURSDAY, SettingsBlock.PomodoroScheduleThursdayStart, SettingsBlock.PomodoroScheduleThursdayEnd),
            day(DayOfWeek.FRIDAY, SettingsBlock.PomodoroScheduleFridayStart, SettingsBlock.PomodoroScheduleFridayEnd),
            day(DayOfWeek.SATURDAY, SettingsBlock.PomodoroScheduleSaturdayStart, SettingsBlock.PomodoroScheduleSaturdayEnd),
            day(DayOfWeek.SUNDAY, SettingsBlock.PomodoroScheduleSundayStart, SettingsBlock.PomodoroScheduleSundayEnd),
        )

        val all: List<PomodoroScheduleItem> = groups + days

        private fun day(
            dayOfWeek: DayOfWeek,
            startBlock: SettingsBlock,
            endBlock: SettingsBlock,
        ) = PomodoroScheduleItem(
            key = dayOfWeek.name,
            days = listOf(dayOfWeek),
            startBlock = startBlock,
            endBlock = endBlock,
        )
    }
}
