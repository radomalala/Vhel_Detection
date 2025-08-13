package com.example.vhel_detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Detector(context: Context) {

    private val interpreter: Interpreter

    init {
        // Charger le modèle TFLite depuis assets
        val model = context.assets.open("model.tflite").use { inputStream ->
            val bytes = inputStream.readBytes()
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }
        interpreter = Interpreter(model)
    }

    fun detect(bitmap: Bitmap): String {
        val input = preprocess(bitmap)

        // Sortie attendue [1, 6, 8400]
        val output = Array(1) { Array(6) { FloatArray(8400) } }

        interpreter.run(input, output)

        // Exemple simple : max sur les 6 classes
        val scores = output[0].map { it.maxOrNull() ?: 0f }
        val maxIndex = scores.indexOf(scores.maxOrNull() ?: 0f)

        return if (maxIndex == 0) "person_no_helmet" else "person_with_helmet"
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val inputImage = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * 640 * 640 * 3).apply {
            order(ByteOrder.nativeOrder())
        }
        val pixels = IntArray(640 * 640)
        inputImage.getPixels(pixels, 0, 640, 0, 0, 640, 640)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    fun close() {
        interpreter.close()
    }
}
