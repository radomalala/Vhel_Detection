package com.example.vhel_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var detections: List<Detection> = emptyList()

    fun setDetections(dets: List<Detection>) {
        detections = dets
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (d in detections) {
            // Couleur selon le label
            boxPaint.color = if (d.label == "person_with_helmet") Color.GREEN else Color.RED
            // Rectangle
            canvas.drawRect(d.left, d.top, d.right, d.bottom, boxPaint)
            // Label + score
            val txt = "${d.label} ${(d.score * 100).toInt()}%"
            canvas.drawText(txt, d.left + 8f, d.top - 12f, textPaint)
        }
    }
}
