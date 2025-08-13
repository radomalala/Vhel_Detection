package com.example.vhel_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val score: Float
)

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
        interpreter = Interpreter(modelBuffer)
    }

    // Détection à partir d'un Bitmap
    fun detect(bitmap: Bitmap): Array<Array<FloatArray>> {
        val input = preprocess(bitmap)
        val output = Array(1) { Array(6) { FloatArray(8400) } } // Shape de ton modèle
        interpreter.run(input, output)
        return output
    }

    // Détection directe à partir d'ImageProxy (flux caméra)
    fun detectImageProxy(image: ImageProxy): List<Detection> {
        val bitmap = image.toBitmap()
        val output = detect(bitmap)

        val detections = mutableListOf<Detection>()

        // Convertir output [1,6,8400] en liste de Detection
        for (i in 0 until 8400) {
            val scores = output[0].map { it[i] } // output[0][classe][i]
            val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: continue
            val maxScore = scores[maxIndex]

            if (maxScore > 0.5f) { // seuil à ajuster
                // Coordonnées fictives → adapter selon modèle YOLO ou SSD
                detections.add(
                    Detection(
                        left = 0f,
                        top = 0f,
                        right = 100f,
                        bottom = 100f,
                        label = if (maxIndex == 0) "person_no_helmet" else "person_with_helmet",
                        score = maxScore
                    )
                )
            }
        }

        return detections
    }

    // Prétraitement Bitmap → ByteBuffer pour TFLite
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val inputImage = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * 640 * 640 * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

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

    // Extension pour convertir ImageProxy → Bitmap
    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun close() {
        interpreter.close()
    }
}
