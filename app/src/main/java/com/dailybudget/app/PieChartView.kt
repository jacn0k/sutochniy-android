package com.dailybudget.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 48f
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 48f
        color = Color.parseColor("#E0E0E0")
    }

    private val rectF = RectF()
    private var data: List<CategoryStat> = emptyList()

    val categoryColors = listOf(
        Color.parseColor("#E91E63"), // Свидания (ярко-розовый)
        Color.parseColor("#4CAF50"), // Продукты (зеленый)
        Color.parseColor("#FF9800"), // Кафе/Рестораны (оранжевый)
        Color.parseColor("#2196F3"), // Транспорт (синий)
        Color.parseColor("#9C27B0"), // Жилье/ЖКХ (фиолетовый)
        Color.parseColor("#00BCD4"), // Покупки (бирюзовый)
        Color.parseColor("#F44336"), // Развлечения (красный)
        Color.parseColor("#009688"), // Здоровье (мятный)
        Color.parseColor("#795548")  // Другое (коричневый)
    )

    fun setData(stats: List<CategoryStat>) {
        this.data = stats
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = slicePaint.strokeWidth / 2 + 16f
        rectF.set(padding, padding, width - padding, height - padding)

        if (data.isEmpty()) {
            canvas.drawOval(rectF, emptyPaint)
            return
        }

        var startAngle = -90f
        for ((index, item) in data.withIndex()) {
            val sweepAngle = (item.percentage / 100f) * 360f
            if (sweepAngle <= 0f) continue

            slicePaint.color = categoryColors[index % categoryColors.size]
            canvas.drawArc(rectF, startAngle, sweepAngle, false, slicePaint)
            startAngle += sweepAngle
        }
    }
}
