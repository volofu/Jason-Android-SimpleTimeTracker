package com.example.util.simpletimetracker.feature_pomodoro.timer.view

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.feature_dialogs.api.TypesSelectionDialogListener
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroButtonState
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroInfoState
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroTimerState
import com.example.util.simpletimetracker.feature_pomodoro.timer.viewModel.PomodoroViewModel
import com.example.util.simpletimetracker.feature_views.extension.setOnClick
import com.example.util.simpletimetracker.feature_views.extension.visible
import dagger.hilt.android.AndroidEntryPoint
import java.util.WeakHashMap
import com.example.util.simpletimetracker.feature_pomodoro.databinding.PomodoroFragmentBinding as Binding

@AndroidEntryPoint
class PomodoroFragment :
    BaseFragment<Binding>(),
    TypesSelectionDialogListener {

    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> Binding =
        Binding::inflate

    override var insetConfiguration: InsetConfiguration =
        InsetConfiguration.ApplyToView { binding.root }

    private val viewModel: PomodoroViewModel by viewModels()

    private var timerUpdateAnimator: ValueAnimator? = null

    // Text colors are changed on pause background, so default ones are remembered to restore.
    // Views can be recreated, so they are not held strongly.
    private val defaultTextColors = WeakHashMap<TextView, ColorStateList>()
    private var pauseBackgroundApplied: Boolean = false

    override fun initUx() = with(binding) {
        btnPomodoroSettings.setOnClick(viewModel::onSettingsClicked)
        btnPomodoroStart.setOnClick(viewModel::onStartStopClicked)
        btnPomodoroRestart.setOnClick(viewModel::onRestartClicked)
        btnPomodoroPause.setOnClick(viewModel::onPauseClicked)
        btnPomodoroPrev.setOnClick(viewModel::onPrevClicked)
        btnPomodoroNext.setOnClick(viewModel::onNextClicked)
        btnPomodoroEditEntry.setOnClick(viewModel::onEditEntryClicked)
    }

    override fun initViewModel() = with(viewModel) {
        buttonState.observe(::setButtonState)
        timerState.observe(::setTimerState)
        infoState.observe(::setInfoState)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onVisible()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onHidden()
    }

    override fun onDataSelected(
        tag: String,
        dataIds: List<Long>,
        tagValues: List<RecordBase.Tag>,
        selectValueOnStartTagIds: List<Long>,
    ) {
        viewModel.onDataSelected(tag, dataIds)
    }

    private fun setInfoState(state: PomodoroInfoState) = with(binding) {
        tvPomodoroActivityName.text = state.activityName
        tvPomodoroComment.text = state.comment
        tvPomodoroComment.alpha = if (state.isCommentPlaceholder) 0.5f else 1f
        tvPomodoroPausedTask.text = state.pausedActivity
        tvPomodoroPausedTask.visible = state.pausedActivity.isNotEmpty()
        tvPomodoroEntryTimes.text = state.entryTimes
        btnPomodoroEditEntry.visible = state.editButtonVisible
        tvPomodoroStatsTypeLabel.text = state.typeLabel
        tvPomodoroStatsTypeToday.text = state.typeToday
        tvPomodoroStatsTypeWeek.text = state.typeWeek
        tvPomodoroStatsAllToday.text = state.allToday
        tvPomodoroStatsAllWeek.text = state.allWeek
        tvPomodoroRatioToday.text = state.ratioToday.text
        piePomodoroRatioToday.fraction = state.ratioToday.fraction
        tvPomodoroRatioWeek.text = state.ratioWeek.text
        piePomodoroRatioWeek.fraction = state.ratioWeek.fraction
    }

    private fun setButtonState(state: PomodoroButtonState) = with(binding) {
        ivPomodoroButton.setImageResource(state.iconResId)

        flowPomodoroMainButtons.referencedIds = state.buttonsOrder
            .map { mapToView(it).id }
            .toIntArray()

        listOf(
            PomodoroButtonState.Button.Restart,
            PomodoroButtonState.Button.Pause,
            PomodoroButtonState.Button.Prev,
            PomodoroButtonState.Button.Next,
        ).forEach {
            if (it in state.buttonsOrder) {
                // Invisible to preserve Start button position.
                mapToView(it).isInvisible = !state.additionalButtonsVisible
            } else {
                mapToView(it).isVisible = false
            }
        }
    }

    private fun setTimerState(state: PomodoroTimerState) = with(binding) {
        groupPomodoroTimerHours.visible = state.durationState.hoursIsVisible
        tvPomodoroTimerHours.text = state.durationState.textHours
        tvPomodoroTimerMinutes.text = state.durationState.textMinutes
        tvPomodoroTimerSeconds.text = state.durationState.textSeconds
        tvPomodoroCycleHint.text = state.currentCycleHint
        setPauseBackground(state.pauseBackgroundFraction)

        viewPomodoroTimer.max = state.maxProgress
        val currentProgress = viewPomodoroTimer.progress
        val updateDuration = state.timerUpdateMs
        val from = if (state.progress < currentProgress) 0 else currentProgress
        val to = state.progress
        timerUpdateAnimator?.cancel()
        timerUpdateAnimator = ValueAnimator.ofInt(from, to).apply {
            this.interpolator = LinearInterpolator()
            this.duration = updateDuration
            addUpdateListener {
                val value = it.animatedValue as? Int ?: return@addUpdateListener
                viewPomodoroTimer.progress = value
            }
            start()
        }
    }

    // White at the start of a pause, red when pause is as long as a focus cycle.
    private fun setPauseBackground(fraction: Float?) = with(binding) {
        val textViews = collectTextViews(root)
        if (fraction == null) {
            if (pauseBackgroundApplied) {
                pauseBackgroundApplied = false
                root.background = null
                textViews.forEach { view -> defaultTextColors[view]?.let(view::setTextColor) }
            }
            return@with
        }
        val background = ColorUtils.blendARGB(Color.WHITE, PAUSE_COLOR_FULL, fraction)
        val textColor = if (ColorUtils.calculateLuminance(background) > TEXT_LUMINANCE_THRESHOLD) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        pauseBackgroundApplied = true
        root.setBackgroundColor(background)
        textViews.forEach { view ->
            if (view !in defaultTextColors) defaultTextColors[view] = view.textColors
            view.setTextColor(textColor)
        }
    }

    private fun collectTextViews(view: View): List<TextView> {
        return when (view) {
            is TextView -> listOf(view)
            is ViewGroup -> view.children.flatMap { collectTextViews(it) }.toList()
            else -> emptyList()
        }
    }

    private fun mapToView(button: PomodoroButtonState.Button): View = with(binding) {
        return@with when (button) {
            is PomodoroButtonState.Button.Start -> btnPomodoroStart
            is PomodoroButtonState.Button.Restart -> btnPomodoroRestart
            is PomodoroButtonState.Button.Pause -> btnPomodoroPause
            is PomodoroButtonState.Button.Prev -> btnPomodoroPrev
            is PomodoroButtonState.Button.Next -> btnPomodoroNext
        }
    }

    companion object {
        private const val PAUSE_COLOR_FULL = 0xFFD32F2F.toInt()
        private const val TEXT_LUMINANCE_THRESHOLD = 0.4
    }
}
