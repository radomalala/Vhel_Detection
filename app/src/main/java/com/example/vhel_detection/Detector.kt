package com.example.vhel_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.TextView
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer

class Detector(private val context: Context, private val debugText: TextView) {

    private var interpreter: Interpreter
    private val inputImageWidth: Int
    private val inputImageHeight: Int
    private val modelInputSize: Int

    init {
        // Charger le modèle TFLite
        val modelBuffer: ByteBuffer = FileUtil.loadMappedFile(context, "model.tflite")
        interpreter = Interpreter(modelBuffer)

        // Lire les dimensions d’entrée du modèle
        val inputShape = interpreter.getInputTensor(0).shape() // [1, height, width, 3]
        inputImageHeight = inputShape[1]
        inputImageWidth = inputShape[2]
        modelInputSize = 4 * inputImageWidth * inputImageHeight * 3 // float32
    }

    fun detect(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap().rotate(imageProxy.imageInfo.rotationDegrees)

        // Préparation de l'image redimensionnée
        val tensorImage = TensorImage.fromBitmap(
            Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)
        )

        // Sortie attendue = [1, 2] (1 batch, 2 classes)
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 2), org.tensorflow.lite.DataType.FLOAT32)

        // Lancer l’inférence
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // Récupérer scores par classes
        val scores = outputBuffer.floatArray

        // Trouver l'index de la classe la plus probable
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: -1
        val confidence = if (maxIndex != -1) scores[maxIndex] else 0f

        // Labels correspondants à ton label.txt
        val labels = listOf("person_no_helmet", "person_with_helmet")

        val detectedLabel = if (maxIndex != -1) labels[maxIndex] else "Inconnu"

        Log.d("Detector", "Scores: ${scores.joinToString()}, Classe: $detectedLabel, Confiance: $confidence")

        // Mettre à jour UI (toujours sur thread principal)
        (context as? MainActivity)?.runOnUiThread {
            debugText.text = when(detectedLabel) {
                "person_with_helmet" -> "✅ Casque détecté"
                "person_no_helmet" -> "❌ Casque non détecté"
                else -> "❓ Détection incertaine"
            }
        }

        imageProxy.close()
    }


    // Extension pour convertir ImageProxy en Bitmap
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

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // Extension pour rotation
    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
