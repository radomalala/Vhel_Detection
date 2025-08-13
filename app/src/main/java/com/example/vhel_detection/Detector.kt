package com.example.vhel_detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Detector(context: Context) {

    private val interpreter: Interpreter

    init {
        // Charger le modèle TensorFlow Lite depuis assets
        val modelBuffer = context.assets.open("model.tflite").use { input ->
            val bytes = input.readBytes()
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }

        // CPU-only
        interpreter = Interpreter(modelBuffer)
    }

    /**
     * Detecte sur un bitmap et retourne la sortie brute du modèle
     */
    fun detect(bitmap: Bitmap): Array<Array<FloatArray>> {
        val inputBuffer = preprocess(bitmap)
        val output = Array(1) { Array(6) { FloatArray(8400) } } // forme de sortie du modèle
        interpreter.run(inputBuffer, output)
        return output
    }

    /**
     * Prétraitement simple : bitmap redimensionné et normalisé [0..1]
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val buffer = ByteBuffer.allocateDirect(4 * 640 * 640 * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(640 * 640)
        resized.getPixels(pixels, 0, 640, 0, 0, 640, 640)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        buffer.rewind()
        return buffer
    }

    fun close() {
        interpreter.close()
    }
}
