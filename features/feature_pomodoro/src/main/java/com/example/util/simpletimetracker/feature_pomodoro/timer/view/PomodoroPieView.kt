package com.example.util.simpletimetracker.feature_pomodoro.timer.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.util.simpletimetracker.feature_pomodoro.R
import com.example.util.simpletimetracker.feature_views.extension.getThemedAttr

/**
 * Small pie chart showing a part of the whole circle.
 */
class PomodoroPieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // 0..1
    var fraction: Float = 0f
        set(value) {
            val newValue = value.coerceIn(0f, 1f)
            if (field != newValue) {
                field = newValue
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
        color = context.getThemedAttr(R.attr.colorSecondary)
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
        if (fraction >= 1f) {
            canvas.drawOval(rect, fillPaint)
        } else if (fraction > 0f) {
            canvas.drawArc(rect, START_ANGLE, SWEEP_ANGLE * fraction, true, fillPaint)
        }
    }

    companion object {
        private const val START_ANGLE = -90f
        private const val SWEEP_ANGLE = 360f
    }
}
