package com.example.util.simpletimetracker.domain.pomodoro.interactor

import com.example.util.simpletimetracker.domain.category.interactor.CategoryInteractor
import com.example.util.simpletimetracker.domain.category.interactor.RecordTypeCategoryInteractor
import com.example.util.simpletimetracker.domain.category.model.Category
import com.example.util.simpletimetracker.domain.color.model.AppColor
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import javax.inject.Inject

/**
 * Only activities from this activity category count as a real pomodoro break.
 */
class PomodoroBreakCategoryInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val categoryInteractor: CategoryInteractor,
    private val recordTypeCategoryInteractor: RecordTypeCategoryInteractor,
) {

    /**
     * Returns id of the category, creates it if it doesn't exist.
     */
    suspend fun getOrCreate(): Long {
        val savedId = prefsInteractor.getPomodoroBreakCategoryId()
        if (savedId != 0L && categoryInteractor.get(savedId) != null) return savedId

        // Can be restored from a backup or created by hand.
        val id = categoryInteractor.get(CATEGORY_NAME).firstOrNull()?.id
            ?: categoryInteractor.add(
                Category(
                    name = CATEGORY_NAME,
                    color = AppColor(colorId = DEFAULT_COLOR_ID, colorInt = "", gradient = null),
                    note = "",
                ),
            )
        prefsInteractor.setPomodoroBreakCategoryId(id)
        return id
    }

    suspend fun getBreakTypeIds(): Set<Long> {
        return recordTypeCategoryInteractor.getTypes(getOrCreate())
    }

    companion object {
        const val CATEGORY_NAME = "Pomodoro Breaks"
        private const val DEFAULT_COLOR_ID = 5
    }
}
