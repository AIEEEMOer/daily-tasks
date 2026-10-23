package com.example.dailycheckin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 头部今日进度圆环：外圈白色圆弧 + 中心「x/x」+ 下方「今日」小字 */
class RingProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var done = 0
    private var total = 0
    private val strokeW = 6.5f * resources.displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeW
        color = 0x40FFFFFF // 半透明白底圈
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeW
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setProgress(d: Int, t: Int) {
        done = d
        total = t
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - strokeW / 2f - 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)

        // 底圈 + 进度弧（从顶部顺时针）
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)
        if (total > 0 && done > 0) {
            canvas.drawArc(rect, -90f, 360f * done / total, false, fgPaint)
        }

        // 中心数字与「今日」标签
        numPaint.textSize = r * 0.50f
        labelPaint.textSize = r * 0.28f
        canvas.drawText("$done/$total", cx, cy + numPaint.textSize * 0.10f, numPaint)
        canvas.drawText("今日", cx, cy + r * 0.42f + labelPaint.textSize * 0.5f, labelPaint)
    }
}
