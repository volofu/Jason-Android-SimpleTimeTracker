package com.example.util.simpletimetracker.feature_pomodoro.timer.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_pomodoro.timer.model.PomodoroPieSlice
import com.example.util.simpletimetracker.feature_views.extension.getThemedAttr

/**
 * Small pie chart showing a part of the whole circle.
 */
class PomodoroPieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // Slices are drawn clockwise from the top, the rest of the circle is empty.
    var slices: List<PomodoroPieSlice> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val rect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getThemedAttr(R.attr.appDividerColor)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat(),
        )
        canvas.drawOval(rect, trackPaint)
        var startAngle = START_ANGLE
        slices.forEach { slice ->
            val sweep = SWEEP_ANGLE * slice.fraction.coerceIn(0f, 1f)
            if (sweep <= 0f) return@forEach
            fillPaint.color = slice.color
            if (sweep >= SWEEP_ANGLE) {
                canvas.drawOval(rect, fillPaint)
            } else {
                canvas.drawArc(rect, startAngle, sweep, true, fillPaint)
            }
            startAngle += sweep
        }
    }

    companion object {
        private const val START_ANGLE = -90f
        private const val SWEEP_ANGLE = 360f
    }
}
