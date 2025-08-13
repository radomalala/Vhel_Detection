package com.example.vhel_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet): View(context, attrs) {

    private var detections: List<Detection> = emptyList()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setDetections(dets: List<Detection>) {
        detections = dets
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (d in detections) {
            paint.color = if (d.label == "person_with_helmet") Color.GREEN else Color.RED
            canvas.drawRect(d.box, paint)
        }
    }
}
