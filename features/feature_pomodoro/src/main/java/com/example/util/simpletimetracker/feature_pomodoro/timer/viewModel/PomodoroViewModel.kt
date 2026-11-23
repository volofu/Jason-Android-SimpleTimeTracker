package com.example.util.simpletimetracker.feature_pomodoro.timer.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.BaseViewModel
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.record.interactor.AddRunningRecordMediator
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RemoveRunningRecordMediator
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroSettingsInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroStateInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroNextCycleInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroPauseInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroResumeTargetInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroRestartCycleInteractor
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroStartInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroStopInteractor
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_pomodoro.timer.interactor.PomodoroInfoInteractor
import com.example.util.simpletimetracker.feature_pomodoro.timer.mapper.PomodoroViewDataMapper
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroButtonState
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroInfoState
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroTimerState
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRunningRecordFromPomodoroParams
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRunningRecordParams
import com.example.util.simpletimetracker.navigation.params.screen.PomodoroSettingsParams
import com.example.util.simpletimetracker.navigation.params.screen.TypesSelectionDialogParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val router: Router,
    private val pomodoroViewDataMapper: PomodoroViewDataMapper,
    private val prefsInteractor: PrefsInteractor,
    private val pomodoroStartInteractor: PomodoroStartInteractor,
    private val pomodoroStopInteractor: PomodoroStopInteractor,
    private val pomodoroNextCycleInteractor: PomodoroNextCycleInteractor,
    private val pomodoroRestartCycleInteractor: PomodoroRestartCycleInteractor,
    private val pomodoroPauseInteractor: PomodoroPauseInteractor,
    private val getPomodoroStateInteractor: GetPomodoroStateInteractor,
    private val getPomodoroSettingsInteractor: GetPomodoroSettingsInteractor,
    private val resourceRepo: ResourceRepo,
    private val pomodoroInfoInteractor: PomodoroInfoInteractor,
    private val runningRecordInteractor: RunningRecordInteractor,
    private val recordInteractor: RecordInteractor,
    private val addRunningRecordMediator: AddRunningRecordMediator,
    private val removeRunningRecordMediator: RemoveRunningRecordMediator,
    private val pomodoroResumeTargetInteractor: PomodoroResumeTargetInteractor,
) : BaseViewModel() {

    val buttonState: LiveData<PomodoroButtonState> = MutableLiveData()
    val timerState: LiveData<PomodoroTimerState> = MutableLiveData()
    val infoState: LiveData<PomodoroInfoState> = MutableLiveData()

    private var timerJob: Job? = null
    private var infoJob: Job? = null

    // Not null if pomodoro is paused and a pause activity is running.
    private var pauseActivityStartedMs: Long? = null

    fun onVisible() {
        startUpdate()
        startInfoUpdate()
    }

    fun onHidden() {
        stopUpdate()
    }

    fun onSettingsClicked() {
        router.navigate(PomodoroSettingsParams)
    }

    fun onStartStopClicked() = viewModelScope.launch {
        val state = getPomodoroStateInteractor.execute()
        when (state) {
            is GetPomodoroStateInteractor.State.Running -> pomodoroStopInteractor.stop()
            is GetPomodoroStateInteractor.State.Paused -> {
                resumeAfterPause()
                pomodoroPauseInteractor.startAfterPause()
            }
            is GetPomodoroStateInteractor.State.Stopped -> {
                resumeLastActivityIfNoneRunning()
                // Activity start could have started pomodoro by itself.
                if (getPomodoroStateInteractor.execute() is GetPomodoroStateInteractor.State.Stopped) {
                    pomodoroStartInteractor.start()
                }
            }
        }
        updateButtonState()
        updateTimerState()
        updateInfoState()
    }

    fun onRestartClicked() = viewModelScope.launch {
        pomodoroRestartCycleInteractor.execute()
        resetAnimation()
        updateTimerState()
    }

    // Pause means that current activity is not being focused on anymore:
    // stop it (so that the time is not tracked as work) and ask what to do during the pause.
    fun onPauseClicked() = viewModelScope.launch {
        if (getPomodoroStateInteractor.execute() !is GetPomodoroStateInteractor.State.Running) return@launch
        pomodoroPauseInteractor.pause()
        val pausedTimestamp = prefsInteractor.getPomodoroModePausedTimestampMs()
        val runningRecord = getActiveRunningRecord()
        if (runningRecord != null) {
            pomodoroResumeTargetInteractor.save(
                typeId = runningRecord.id,
                comment = runningRecord.comment,
                tags = runningRecord.tags,
            )
            removeRunningRecordMediator.removeWithRecordAdd(
                runningRecord = runningRecord,
                // Pause activity time starts exactly when this one ends.
                timeEnded = pausedTimestamp.takeIf { it != 0L },
                // Otherwise pomodoro would be stopped instead of paused.
                checkPomodoroStop = false,
            )
        } else {
            pomodoroResumeTargetInteractor.clear()
        }
        updateButtonState()
        updateTimerState()
        updateInfoState()
        showActivitySelection(excludedTypeId = runningRecord?.id)
    }

    fun onDataSelected(tag: String?, dataIds: List<Long>) {
        if (tag != PAUSE_ACTIVITY_SELECTION_TAG) return
        val typeId = dataIds.firstOrNull() ?: return
        viewModelScope.launch {
            startPauseActivity(typeId)
        }
    }

    fun onEditEntryClicked() = viewModelScope.launch {
        val runningRecord = getActiveRunningRecord() ?: return@launch
        router.navigate(
            ChangeRunningRecordFromPomodoroParams(
                params = ChangeRunningRecordParams(
                    transitionName = "",
                    id = runningRecord.id,
                    from = ChangeRunningRecordParams.From.RunningRecords,
                    preview = null,
                ),
            ),
        )
    }

    fun onPrevClicked() = viewModelScope.launch {
        pomodoroNextCycleInteractor.executePrev()
        resetAnimation()
        updateTimerState()
    }

    fun onNextClicked() = viewModelScope.launch {
        pomodoroNextCycleInteractor.executeNext()
        resetAnimation()
        updateTimerState()
    }

    // If several activities are running (multitasking), the one started last is the active one.
    private suspend fun getActiveRunningRecord(): RunningRecord? {
        return runningRecordInteractor.getAll().maxByOrNull { it.timeStarted }
    }

    private suspend fun resumeLastActivityIfNoneRunning() {
        if (runningRecordInteractor.getAll().isNotEmpty()) return
        val lastRecord = recordInteractor.getPrev(System.currentTimeMillis()) ?: return
        addRunningRecordMediator.startTimer(
            typeId = lastRecord.typeId,
            tags = lastRecord.tags,
            comment = lastRecord.comment,
        )
    }

    // Stops pause activity and brings back the activity that was paused, with its comment and tags.
    private suspend fun resumeAfterPause() {
        val target = pomodoroResumeTargetInteractor.get()
        if (target == null) {
            resumeLastActivityIfNoneRunning()
            return
        }
        val runningRecord = getActiveRunningRecord()
        if (runningRecord != null && runningRecord.id != target.typeId) {
            removeRunningRecordMediator.removeWithRecordAdd(
                runningRecord = runningRecord,
                checkPomodoroStop = false,
            )
        }
        if (!runningRecordInteractor.has(target.typeId)) {
            addRunningRecordMediator.startTimer(
                typeId = target.typeId,
                tags = target.tags,
                comment = target.comment,
            )
        }
        pomodoroResumeTargetInteractor.clear()
    }

    private fun showActivitySelection(excludedTypeId: Long?) {
        TypesSelectionDialogParams(
            tag = PAUSE_ACTIVITY_SELECTION_TAG,
            title = resourceRepo.getString(R.string.change_record_message_choose_type),
            subtitle = "",
            type = TypesSelectionDialogParams.Type.Activity,
            selectedTypeIds = emptyList(),
            selectedTagValues = emptyList(),
            selectedTagValueOnStart = emptyList(),
            isMultiSelectAvailable = false,
            idsShouldBeVisible = emptyList(),
            excludedTypeIds = listOfNotNull(excludedTypeId),
            showHints = false,
            allowTagValueSelection = false,
        ).let(router::navigate)
    }

    // Pause activity is tracked as a regular running activity, but pomodoro stays paused:
    // no cycles, no tags or comment prompt.
    private suspend fun startPauseActivity(typeId: Long) {
        if (getPomodoroStateInteractor.execute() !is GetPomodoroStateInteractor.State.Paused) return
        if (!runningRecordInteractor.has(typeId)) {
            val pausedTimestamp = prefsInteractor.getPomodoroModePausedTimestampMs()
            addRunningRecordMediator.startTimer(
                typeId = typeId,
                tags = emptyList(),
                comment = "",
                timeStarted = if (pausedTimestamp != 0L) {
                    AddRunningRecordMediator.StartTime.Timestamp(pausedTimestamp)
                } else {
                    AddRunningRecordMediator.StartTime.TakeCurrent
                },
            )
        }
        updateButtonState()
        updateTimerState()
        updateInfoState()
    }

    private fun resetAnimation() {
        timerState.value?.copy(progress = 0)?.let(timerState::set)
    }

    private fun startUpdate() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                updateButtonState()
                updateTimerState()
                delay(TIMER_UPDATE_MS)
            }
        }
    }

    private fun stopUpdate() {
        timerJob?.cancel()
        infoJob?.cancel()
    }

    private fun startInfoUpdate() {
        infoJob?.cancel()
        infoJob = viewModelScope.launch {
            while (isActive) {
                updateInfoState()
                delay(INFO_UPDATE_MS)
            }
        }
    }

    private suspend fun updateInfoState() {
        val info = pomodoroInfoInteractor.execute()
        pauseActivityStartedMs = info.pauseActivityStartedMs
        infoState.set(pomodoroViewDataMapper.mapInfoState(info))
    }

    private suspend fun updateButtonState() {
        val data = loadButtonState()
        buttonState.set(data)
    }

    private suspend fun loadButtonState(): PomodoroButtonState {
        return pomodoroViewDataMapper.mapButtonState(
            showMoreControls = prefsInteractor.getPomodoroShowMoreControls(),
            state = getPomodoroStateInteractor.execute(),
        )
    }

    private suspend fun updateTimerState() {
        val data = loadTimerState()
        timerState.set(data)
    }

    private suspend fun loadTimerState(): PomodoroTimerState {
        return pomodoroViewDataMapper.mapTimerState(
            state = getPomodoroStateInteractor.execute(),
            timeStartedMs = prefsInteractor.getPomodoroModeStartedTimestampMs(),
            timePausedMs = prefsInteractor.getPomodoroModePausedTimestampMs(),
            timerUpdateMs = TIMER_UPDATE_MS,
            settings = getPomodoroSettingsInteractor.execute(),
            pauseElapsedMs = pauseActivityStartedMs?.let { System.currentTimeMillis() - it },
        )
    }

    companion object {
        private const val TIMER_UPDATE_MS = 250L
        private const val INFO_UPDATE_MS = 1000L
        private const val PAUSE_ACTIVITY_SELECTION_TAG = "POMODORO_PAUSE_ACTIVITY_SELECTION_TAG"
    }
}
