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
        val modelBytes = context.assets.open("model.tflite").use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBytes)
            rewind()
        }
        interpreter = Interpreter(buffer)
    }

    // Labels du modèle
    private val labels = listOf("person_no_helmet", "person_with_helmet")

    // Retourne directement le label le plus probable
    fun detect(bitmap: Bitmap): String {
        val input = preprocess(bitmap)
        val output = Array(1) { Array(6) { FloatArray(8400) } }

        interpreter.run(input, output)

        // Post-traitement simple : on prend la première "classe" ayant le score max
        var maxScore = -Float.MAX_VALUE
        var maxClass = 0
        for (i in 0 until 6) {
            for (j in 0 until 8400) {
                val score = output[0][i][j]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = i % 2  // 0=no helmet, 1=with helmet
                }
            }
        }
        return labels[maxClass]
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val buffer = ByteBuffer.allocateDirect(4 * 640 * 640 * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(640 * 640)
        inputBitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640)
        for (pixel in pixels) {
            putFloat(((pixel shr 16) and 0xFF) / 255f)
            putFloat(((pixel shr 8) and 0xFF) / 255f)
            putFloat((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    fun close() {
        interpreter.close()
    }
}
