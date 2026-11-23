package com.example.util.simpletimetracker.feature_pomodoro.timer.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.BaseViewModel
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.extension.toParams
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.record.interactor.AddRunningRecordMediator
import com.example.util.simpletimetracker.domain.record.interactor.RecordInteractor
import com.example.util.simpletimetracker.domain.record.interactor.RemoveRunningRecordMediator
import com.example.util.simpletimetracker.domain.record.interactor.RunningRecordInteractor
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordDataSelectionDialogResult
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroSettingsInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.GetPomodoroStateInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroNextCycleInteractor
import com.example.util.simpletimetracker.domain.pomodoro.interactor.PomodoroPauseInteractor
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
import com.example.util.simpletimetracker.navigation.params.screen.PomodoroSettingsParams
import com.example.util.simpletimetracker.navigation.params.screen.RecordTagSelectionParams
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
) : BaseViewModel() {

    val buttonState: LiveData<PomodoroButtonState> = MutableLiveData()
    val timerState: LiveData<PomodoroTimerState> = MutableLiveData()
    val infoState: LiveData<PomodoroInfoState> = MutableLiveData()

    private var timerJob: Job? = null
    private var infoJob: Job? = null

    // Activity is waiting for tag/comment selection, pomodoro should be resumed when it is started.
    private var resumeAfterTagSelection: Boolean = false

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
                resumeLastActivityIfNoneRunning()
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
    // stop it (so that the time is not tracked) and ask what to do next.
    fun onPauseClicked() = viewModelScope.launch {
        pomodoroPauseInteractor.pause()
        resumeAfterTagSelection = false
        val stoppedTypeId = stopActiveActivity()
        updateButtonState()
        updateTimerState()
        updateInfoState()
        showActivitySelection(excludedTypeId = stoppedTypeId)
    }

    fun onDataSelected(tag: String?, dataIds: List<Long>) {
        if (tag != PAUSE_ACTIVITY_SELECTION_TAG) return
        val typeId = dataIds.firstOrNull() ?: return
        viewModelScope.launch {
            startActivityAfterPause(typeId)
        }
    }

    fun onTagSelected() {
        viewModelScope.launch {
            if (resumeAfterTagSelection) {
                resumeAfterTagSelection = false
                resumePomodoroAfterPause()
            }
            updateButtonState()
            updateTimerState()
            updateInfoState()
        }
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

    private suspend fun stopActiveActivity(): Long? {
        // If several activities are running (multitasking), stop the one that is shown on the screen.
        val runningRecord = runningRecordInteractor.getAll()
            .maxByOrNull { it.timeStarted }
            ?: return null
        removeRunningRecordMediator.removeWithRecordAdd(
            runningRecord = runningRecord,
            // Otherwise pomodoro would be stopped instead of paused.
            checkPomodoroStop = false,
        )
        return runningRecord.id
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

    private suspend fun startActivityAfterPause(typeId: Long) {
        val isRunning = runningRecordInteractor.has(typeId) ||
            addRunningRecordMediator.tryStartTimer(
                typeId = typeId,
                onNeedToShowTagSelection = { showTagSelection(typeId, it) },
            )
        if (isRunning) {
            resumePomodoroAfterPause()
        } else {
            // Tag selection dialog is shown, wait for it.
            resumeAfterTagSelection = true
        }
        updateButtonState()
        updateTimerState()
        updateInfoState()
    }

    private suspend fun resumePomodoroAfterPause() {
        pomodoroPauseInteractor.startAfterPause()
    }

    private fun showTagSelection(
        typeId: Long,
        result: RecordDataSelectionDialogResult,
    ) {
        router.navigate(
            RecordTagSelectionParams(
                typeId = typeId,
                fields = result.fields.toParams(),
                preselectedTags = result.preselectedTags.map(RecordBase.Tag::toParams),
                requiredValueSelectionTagIds = result.requiredValueSelectionTagIds,
            ),
        )
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
        )
    }

    companion object {
        private const val TIMER_UPDATE_MS = 250L
        private const val INFO_UPDATE_MS = 1000L
        private const val PAUSE_ACTIVITY_SELECTION_TAG = "POMODORO_PAUSE_ACTIVITY_SELECTION_TAG"
    }
}
