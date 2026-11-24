package com.example.util.simpletimetracker.domain.pomodoro.interactor

import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import javax.inject.Inject

class PomodoroPauseInteractor @Inject constructor(
    private val prefsInteractor: PrefsInteractor,
    private val pomodoroCycleNotificationInteractor: PomodoroCycleNotificationInteractor,
    private val getPomodoroStateInteractor: GetPomodoroStateInteractor,
    private val pomodoroCycleLogInteractor: PomodoroCycleLogInteractor,
) {

    suspend fun pause() {
        if (!prefsInteractor.getEnablePomodoroMode()) return
        val state = getPomodoroStateInteractor.execute()
        if (state !is GetPomodoroStateInteractor.State.Running) return

        pomodoroCycleLogInteractor.sync()
        val current = System.currentTimeMillis()
        prefsInteractor.setPomodoroModePausedTimestampMs(current)
        pomodoroCycleNotificationInteractor.cancel()
    }

    suspend fun startAfterPause() {
        if (!prefsInteractor.getEnablePomodoroMode()) return
        val state = getPomodoroStateInteractor.execute()
        if (state !is GetPomodoroStateInteractor.State.Paused) return

        val current = System.currentTimeMillis()
        val timeStartedMs = prefsInteractor.getPomodoroModeStartedTimestampMs()
        val timePausedMs = prefsInteractor.getPomodoroModePausedTimestampMs()
        val newTimeStarted = timeStartedMs + current - timePausedMs
        prefsInteractor.setPomodoroModeStartedTimestampMs(newTimeStarted)
        prefsInteractor.setPomodoroModePausedTimestampMs(0)
        // Entry keeps running while paused, so pause time is accumulated separately.
        val pauseTotalMs = prefsInteractor.getPomodoroPauseTotalMs()
        prefsInteractor.setPomodoroPauseTotalMs(pauseTotalMs + (current - timePausedMs).coerceAtLeast(0L))
        pomodoroCycleNotificationInteractor.checkAndReschedule()
    }
}