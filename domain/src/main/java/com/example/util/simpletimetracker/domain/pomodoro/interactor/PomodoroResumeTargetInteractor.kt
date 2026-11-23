package com.example.util.simpletimetracker.domain.pomodoro.interactor

import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import javax.inject.Inject

/**
 * Remembers the activity that was interrupted by pause, so that play can resume it
 * with the same comment and tags.
 */
class PomodoroResumeTargetInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
) {

    suspend fun save(
        typeId: Long,
        comment: String,
        tags: List<RecordBase.Tag>,
    ) {
        prefsInteractor.setPomodoroResumeTypeId(typeId)
        prefsInteractor.setPomodoroResumeComment(comment)
        prefsInteractor.setPomodoroResumeTags(
            tags.joinToString(TAG_DELIMITER) { "${it.tagId}$VALUE_DELIMITER${it.numericValue ?: ""}" },
        )
    }

    suspend fun get(): Target? {
        val typeId = prefsInteractor.getPomodoroResumeTypeId()
        if (typeId == 0L) return null
        val tags = prefsInteractor.getPomodoroResumeTags()
            .split(TAG_DELIMITER)
            .mapNotNull { item ->
                val parts = item.split(VALUE_DELIMITER)
                val tagId = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                RecordBase.Tag(tagId = tagId, numericValue = parts.getOrNull(1)?.toDoubleOrNull())
            }
        return Target(
            typeId = typeId,
            comment = prefsInteractor.getPomodoroResumeComment(),
            tags = tags,
        )
    }

    suspend fun clear() {
        prefsInteractor.setPomodoroResumeTypeId(0)
    }

    data class Target(
        val typeId: Long,
        val comment: String,
        val tags: List<RecordBase.Tag>,
    )

    companion object {
        private const val TAG_DELIMITER = ","
        private const val VALUE_DELIMITER = ":"
    }
}
