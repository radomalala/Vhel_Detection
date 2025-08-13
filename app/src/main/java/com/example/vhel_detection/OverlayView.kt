package com.example.vhel_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

data class Detection(
    val label: String,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    private var detections: List<Detection> = emptyList()

    fun setDetections(detections: List<Detection>) {
        this.detections = detections
        invalidate() // redraw
    }

    // ✅ Corrigé : canvas n'est plus nullable
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (det in detections) {
            boxPaint.color = if (det.label == "person_with_helmet") Color.GREEN else Color.RED
            canvas.drawRect(det.left, det.top, det.right, det.bottom, boxPaint)
            canvas.drawText("${det.label} ${(det.score * 100).toInt()}%", det.left, det.top - 10f, textPaint)
        }
    }
}
