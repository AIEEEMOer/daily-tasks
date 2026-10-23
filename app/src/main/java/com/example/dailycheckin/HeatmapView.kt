package com.example.dailycheckin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历热力图：周一开头，7 列网格。
 * 已打卡 = 绿色实心方块；今天 = 绿色描边；未来/开始前 = 浅灰文字无底。
 */
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var checkIns: Set<String> = emptySet()
    private var yearMonth: YearMonth = YearMonth.now()
    private var startDate: String = "0000-01-01"

    private val checkedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1FA371") }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0F2F1") }
    private val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = Color.parseColor("#1FA371")
    }
    private val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A6B62")
        textAlign = Paint.Align.CENTER
    }
    private val dayNumOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val futureNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C9D2CD")
        textAlign = Paint.Align.CENTER
    }
    private val weekLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA8A0")
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setData(checkIns: Set<String>, ym: YearMonth, startDate: String) {
        this.checkIns = checkIns
        this.yearMonth = ym
        this.startDate = startDate
        requestLayout()
        invalidate()
    }

    private fun rows(): Int {
        val offset = yearMonth.atDay(1).dayOfWeek.value - 1 // 周一为 0
        return (offset + yearMonth.lengthOfMonth() + 6) / 7
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cell = w / 7f
        val h = cell * (rows() + 1) // 第一行为星期标签
        setMeasuredDimension(w, h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = width / 7f
        val gap = cell * 0.09f
        val size = cell - gap * 2
        val radius = cell * 0.18f
        val today = LocalDate.now().format(Habit.FMT)

        // 星期标签：一 二 三 四 五 六 日
        weekLabelPaint.textSize = cell * 0.30f
        val labels = arrayOf("一", "二", "三", "四", "五", "六", "日")
        for (i in 0..6) {
            canvas.drawText(labels[i], cell * i + cell / 2, cell * 0.62f, weekLabelPaint)
        }

        val offset = yearMonth.atDay(1).dayOfWeek.value - 1
        val days = yearMonth.lengthOfMonth()
        dayNumPaint.textSize = cell * 0.30f
        dayNumOnPaint.textSize = cell * 0.30f
        futureNumPaint.textSize = cell * 0.30f

        for (d in 1..days) {
            val idx = offset + d - 1
            val col = idx % 7
            val row = idx / 7 + 1
            val left = cell * col + gap
            val top = cell * row + gap
            rect.set(left, top, left + size, top + size)

            val dateStr = "%04d-%02d-%02d".format(yearMonth.year, yearMonth.monthValue, d)
            val isChecked = checkIns.contains(dateStr)
            val isFuture = dateStr > today
            val beforeStart = dateStr < startDate

            when {
                isChecked -> {
                    canvas.drawRoundRect(rect, radius, radius, checkedPaint)
                    canvas.drawText("$d", rect.centerX(), rect.centerY() + dayNumOnPaint.textSize * 0.36f, dayNumOnPaint)
                }
                isFuture || beforeStart -> {
                    canvas.drawText("$d", rect.centerX(), rect.centerY() + futureNumPaint.textSize * 0.36f, futureNumPaint)
                }
                else -> {
                    canvas.drawRoundRect(rect, radius, radius, emptyPaint)
                    canvas.drawText("$d", rect.centerX(), rect.centerY() + dayNumPaint.textSize * 0.36f, dayNumPaint)
                }
            }
            // 今天加绿色描边
            if (dateStr == today) {
                canvas.drawRoundRect(rect, radius, radius, todayRingPaint)
            }
        }
    }
}
