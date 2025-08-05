package com.example.vhel_detection   // <- aligné avec le dossier

import android.content.Context
import android.graphics.*
import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.ByteArrayOutputStream

class Detector(private val context: Context) {

    private val tflite: Interpreter
    private val inputSize = 320
    private val labels = context.assets.open("labels.txt").bufferedReader().readLines()

    init {
        val model = loadModelFile("model.tflite")
        tflite = Interpreter(model)
    }

    fun detect(imageProxy: ImageProxy) {
        val bitmap = toBitmap(imageProxy)
        val input = preprocess(bitmap)
        val output = Array(1) { FloatArray(labels.size) }
        tflite.run(input, output)
        val detected = output[0][0] > 0.5f
        drawOverlay(bitmap, detected)
        imageProxy.close()
    }

    private fun toBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val yuv = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in intValues) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        return byteBuffer
    }

    private fun drawOverlay(bitmap: Bitmap, detected: Boolean) {
        // TODO : afficher sur un ImageView ou via Canvas
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}