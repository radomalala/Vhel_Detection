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

    fun detect(bitmap: Bitmap): Array<Array<FloatArray>> {
        val input = preprocess(bitmap)

        // Tableau de sortie conforme à la forme du modèle [1, 6, 8400]
        val output = Array(1) { Array(6) { FloatArray(8400) } }

        interpreter.run(input, output)

        return output
    }


    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        // Assurez-vous que bitmap est de la bonne taille (ex 640x640)
        val inputImage = Bitmap.createScaledBitmap(bitmap, 640, 640, true)

        val inputBuffer = ByteBuffer.allocateDirect(4 * 640 * 640 * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val pixels = IntArray(640 * 640)
        inputImage.getPixels(pixels, 0, 640, 0, 0, 640, 640)

        // Convertir pixels en float normalisés [0..1]
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
