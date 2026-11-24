package com.example.util.simpletimetracker.feature_base_adapter.pomodoroButton

import com.example.util.simpletimetracker.domain.extension.tryCast
import com.example.util.simpletimetracker.feature_base_adapter.createRecyclerBindingAdapterDelegate
import com.example.util.simpletimetracker.feature_views.extension.setOnClick
import com.example.util.simpletimetracker.feature_views.extension.visible
import com.google.android.flexbox.FlexboxLayoutManager
import com.example.util.simpletimetracker.feature_base_adapter.databinding.ItemPomodoroButtonLayoutBinding as Binding
import com.example.util.simpletimetracker.feature_base_adapter.pomodoroButton.PomodoroButtonViewData as ViewData

fun createPomodoroButtonAdapterDelegate(
    onClick: () -> Unit,
) = createRecyclerBindingAdapterDelegate<ViewData, Binding>(
    Binding::inflate,
) { binding, item, _ ->

    with(binding) {
        item as ViewData

        // Takes the whole row.
        root.layoutParams
            .tryCast<FlexboxLayoutManager.LayoutParams>()
            ?.apply { isWrapBefore = true }

        tvPomodoroButtonTitle.text = item.title
        tvPomodoroButtonStatus.text = item.status
        tvPomodoroButtonStatus.visible = item.status.isNotEmpty()
        cardPomodoroButton.strokeWidth = if (item.isActive) ACTIVE_STROKE_PX else 0
        cardPomodoroButton.setOnClick(onClick)
    }
}

private const val ACTIVE_STROKE_PX = 4
