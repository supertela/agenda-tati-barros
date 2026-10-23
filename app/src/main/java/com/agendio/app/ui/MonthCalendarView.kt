package com.agendio.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.agendio.app.R
import com.agendio.app.util.Formats
import java.util.Calendar
import kotlin.math.min

/**
 * Folha mensal desenhada para ocupar toda a área disponível.
 * Mostra os dias do mês, o dia selecionado, hoje e uma marca nos dias com
 * atendimentos. O cabeçalho com o nome do mês fica no layout do fragmento.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textColor = ContextCompat.getColor(context, R.color.text)
    private val mutedColor = ContextCompat.getColor(context, R.color.text_muted)
    private val primaryColor = ContextCompat.getColor(context, R.color.primary)
    private val accentColor = ContextCompat.getColor(context, R.color.accent)
    private val dangerColor = ContextCompat.getColor(context, R.color.danger)
    private val strokeColor = ContextCompat.getColor(context, R.color.stroke)
    private val whiteColor = ContextCompat.getColor(context, R.color.white)

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = sp(23f)
        color = textColor
    }

    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = sp(10f)
        color = mutedColor
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = strokeColor
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }

    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = accentColor
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accentColor
    }

    private val weekdayHeight = dp(28f)
    private val weekdays = arrayOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")

    private var monthStart: Long = Formats.monthStart(System.currentTimeMillis())
    private var selectedDay: Long = Formats.dayStart(System.currentTimeMillis())
    private var appointmentCounts: Map<Long, Int> = emptyMap()

    var onDaySelected: ((Long) -> Unit)? = null

    fun setMonth(monthStart: Long, selectedDay: Long, counts: Map<Long, Int>) {
        this.monthStart = Formats.monthStart(monthStart)
        this.selectedDay = Formats.dayStart(selectedDay)
        appointmentCounts = counts
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        if (right <= left || bottom <= top) return

        val gridTop = top + weekdayHeight
        val usableWidth = right - left
        val cellWidth = usableWidth / 7f

        val firstDay = Calendar.getInstance().apply { timeInMillis = monthStart }
        val firstDayOffset = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = if (firstDayOffset + daysInMonth <= 35) 5 else 6
        val cellHeight = (bottom - gridTop) / rows
        if (cellWidth <= 0f || cellHeight <= 0f) return

        dayPaint.textSize = min(sp(23f), cellHeight * 0.46f)

        // Cabeçalho dos dias da semana.
        weekdays.forEachIndexed { index, label ->
            weekdayPaint.color = if (index == 0) dangerColor else mutedColor
            canvas.drawText(
                label,
                left + cellWidth * (index + 0.5f),
                top + weekdayHeight * 0.64f,
                weekdayPaint
            )
        }

        // Linhas da grade.
        for (column in 1 until 7) {
            val x = left + cellWidth * column
            canvas.drawLine(x, gridTop, x, bottom, gridPaint)
        }
        for (row in 1 until rows) {
            val y = gridTop + cellHeight * row
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        canvas.drawLine(left, gridTop, right, gridTop, gridPaint)

        val today = Formats.dayStart(System.currentTimeMillis())
        val radius = min(cellWidth, cellHeight) * 0.34f

        for (index in 0 until rows * 7) {
            val dayNumber = index - firstDayOffset + 1
            if (dayNumber !in 1..daysInMonth) continue

            val column = index % 7
            val row = index / 7
            val centerX = left + cellWidth * (column + 0.5f)
            val centerY = gridTop + cellHeight * (row + 0.5f)
            val dayMillis = Formats.addDays(monthStart, dayNumber - 1)
            val selected = dayMillis == selectedDay
            val isToday = dayMillis == today

            if (selected) {
                canvas.drawCircle(centerX, centerY, radius, selectedPaint)
                dayPaint.color = whiteColor
            } else {
                if (isToday) {
                    canvas.drawCircle(centerX, centerY, radius, todayPaint)
                }
                dayPaint.color = if (column == 0) dangerColor else textColor
            }

            val baseline = centerY - (dayPaint.ascent() + dayPaint.descent()) / 2f - dp(3f)
            canvas.drawText(dayNumber.toString(), centerX, baseline, dayPaint)

            if ((appointmentCounts[dayMillis] ?: 0) > 0) {
                dotPaint.color = if (selected) whiteColor else accentColor
                val dotRadius = if ((appointmentCounts[dayMillis] ?: 0) > 1) dp(3.2f) else dp(2.4f)
                canvas.drawCircle(centerX, centerY + radius * 0.74f, dotRadius, dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat() + weekdayHeight
        val bottom = (height - paddingBottom).toFloat()
        val right = (width - paddingRight).toFloat()
        if (right <= left || bottom <= top) return true

        val firstDay = Calendar.getInstance().apply { timeInMillis = monthStart }
        val offset = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = if (offset + daysInMonth <= 35) 5 else 6
        val cellWidth = (right - left) / 7f
        val cellHeight = (bottom - top) / rows
        if (event.x < left || event.x >= right || event.y < top || event.y >= bottom) {
            return true
        }

        val column = ((event.x - left) / cellWidth).toInt().coerceIn(0, 6)
        val row = ((event.y - top) / cellHeight).toInt().coerceIn(0, rows - 1)
        val dayNumber = row * 7 + column - offset + 1
        if (dayNumber !in 1..daysInMonth) return true

        performClick()
        onDaySelected?.invoke(Formats.addDays(monthStart, dayNumber - 1))
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )
}
