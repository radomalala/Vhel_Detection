package com.example.vhel_detection

import android.content.Context
import android.graphics.*
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val score: Float
)

class Detector(context: Context) {

    // Modèle attendu : entrée 640x640x3 float32, sortie [1, 6, 8400]
    private val inputW = 640
    private val inputH = 640
    private val labels = arrayOf("person_no_helmet", "person_with_helmet")

    private val interpreter: Interpreter

    init {
        // Charge le modèle depuis assets/model.tflite
        val modelBuffer = readModelToDirectBuffer(context.assets.open("model.tflite"))
        // Options CPU (XNNPACK activé par défaut sur la plupart des builds TFLite)
        val options = Interpreter.Options().apply {
            setNumThreads(4) // ajuste entre 2 et 4 selon ton appareil
        }
        interpreter = Interpreter(modelBuffer, options)
    }

    fun close() {
        interpreter.close()
    }

    /** Point d’entrée depuis CameraX */
    fun detectImageProxy(imageProxy: ImageProxy): List<Detection> {
        // 1) Convertir YUV -> Bitmap
        val bitmap = imageProxyToBitmap(imageProxy)

        // 2) Prétraitement -> ByteBuffer float32 [1,640,640,3]
        val inputBuffer = preprocess(bitmap, inputW, inputH)

        // 3) Sortie du modèle [1, 6, 8400]
        val output = Array(1) { Array(6) { FloatArray(8400) } }

        // 4) Inference
        interpreter.run(inputBuffer, output)

        // 5) Post-traitement (décodage + NMS)
        val dets = decodeDetections(output[0], inputW.toFloat(), inputH.toFloat())
        return nonMaxSuppression(dets, iouThreshold = 0.45f)
    }

    // ---------- Helpers ----------

    private fun readModelToDirectBuffer(ins: InputStream): ByteBuffer {
        val bytes = ins.use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(bytes)
            rewind()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yPlane.get(nv21, 0, ySize)
        vPlane.get(nv21, ySize, vSize)
        uPlane.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun preprocess(src: Bitmap, dstW: Int, dstH: Int): ByteBuffer {
        val resized = if (src.width != dstW || src.height != dstH) {
            Bitmap.createScaledBitmap(src, dstW, dstH, true)
        } else src

        val buffer = ByteBuffer.allocateDirect(4 * dstW * dstH * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(dstW * dstH)
        resized.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        // NHWC float32 normalisé [0..1]
        var idx = 0
        while (idx < pixels.size) {
            val p = pixels[idx]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
            idx++
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Décodage d’une sortie [6, 8400].
     * Hypothèse YOLO-like : [cx, cy, w, h, obj/conf, class_id_float]
     */
    private fun decodeDetections(
        out: Array<FloatArray>,
        scaleW: Float,
        scaleH: Float,
        confThreshold: Float = 0.35f
    ): MutableList<Detection> {
        val dets = mutableListOf<Detection>()
        val num = 8400
        for (i in 0 until num) {
            val cx = out[0][i]
            val cy = out[1][i]
            val w = out[2][i]
            val h = out[3][i]
            val obj = sigmoid(out[4][i])
            if (obj < confThreshold) continue

            // class_id codé en float ? on arrondit pour obtenir 0 ou 1
            val clsId = out[5][i].roundToInt().coerceIn(0, labels.lastIndex)
            val label = labels[clsId]

            val left = max(0f, cx - w / 2f)
            val top = max(0f, cy - h / 2f)
            val right = min(scaleW, cx + w / 2f)
            val bottom = min(scaleH, cy + h / 2f)

            dets.add(
                Detection(
                    left = left, top = top, right = right, bottom = bottom,
                    label = label, score = obj
                )
            )
        }
        return dets
    }

    private fun nonMaxSuppression(
        boxes: MutableList<Detection>,
        iouThreshold: Float
    ): List<Detection> {
        // Trie par score décroissant
        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val selected = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)

            val it = sorted.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (iou(best, other) > iouThreshold && best.label == other.label) {
                    it.remove()
                }
            }
        }
        return selected
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)

        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - inter + 1e-6f
        return inter / union
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))
}
