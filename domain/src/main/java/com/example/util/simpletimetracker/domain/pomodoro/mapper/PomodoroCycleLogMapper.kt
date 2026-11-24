package com.example.util.simpletimetracker.domain.pomodoro.mapper

import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleLogEntry
import com.example.util.simpletimetracker.domain.pomodoro.model.PomodoroCycleType
import javax.inject.Inject

/**
 * Format: "endMs|type|actualMs|plannedMs" joined by ";", type is F, B or L.
 */
class PomodoroCycleLogMapper @Inject constructor() {

    fun fromString(value: String): List<PomodoroCycleLogEntry> {
        if (value.isEmpty()) return emptyList()
        return value.split(ENTRY_DELIMITER).mapNotNull { item ->
            val parts = item.split(FIELD_DELIMITER)
            val type = when (parts.getOrNull(1)) {
                "F" -> PomodoroCycleType.Focus
                "B" -> PomodoroCycleType.Break
                "L" -> PomodoroCycleType.LongBreak
                else -> return@mapNotNull null
            }
            PomodoroCycleLogEntry(
                endMs = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null,
                type = type,
                actualMs = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null,
                plannedMs = parts.getOrNull(3)?.toLongOrNull() ?: return@mapNotNull null,
            )
        }
    }

    fun toString(entries: List<PomodoroCycleLogEntry>): String {
        return entries.joinToString(ENTRY_DELIMITER) {
            val type = when (it.type) {
                PomodoroCycleType.Focus -> "F"
                PomodoroCycleType.Break -> "B"
                PomodoroCycleType.LongBreak -> "L"
            }
            listOf(it.endMs, type, it.actualMs, it.plannedMs).joinToString(FIELD_DELIMITER)
        }
    }

    companion object {
        private const val ENTRY_DELIMITER = ";"
        private const val FIELD_DELIMITER = "|"
    }
}
