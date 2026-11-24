package com.example.util.simpletimetracker.feature_base_adapter.pomodoroButton

import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType

/**
 * Wide button that opens pomodoro screen.
 */
data class PomodoroButtonViewData(
    val title: String,
    // Empty - not visible.
    val status: String,
    val isActive: Boolean,
) : ViewHolderType {

    override fun getUniqueId(): Long = "pomodoro_button".hashCode().toLong()

    override fun isValidType(other: ViewHolderType): Boolean = other is PomodoroButtonViewData
}
