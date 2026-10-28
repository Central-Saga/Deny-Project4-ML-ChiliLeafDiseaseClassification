
package com.example.chilileafdisease

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import com.google.ai.edge.litert.CompiledModel
import kotlin.math.min

class ChiliLeafClassifier(context: Context) : AutoCloseable {

    companion object {
        private const val MODEL_NAME =
            "mobilenetv2_chili_float32.tflite"
        private const val IMAGE_SIZE = 224
    }

    data class PredictionResult(
        val label: String,
        val confidence: Float,
        val scores: Map<String, Float>
    )

    private val labels = context.assets.open("labels.txt")
        .bufferedReader()
        .use { reader ->
            reader.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    private val model = CompiledModel.create(
        context.assets,
        MODEL_NAME,
        CompiledModel.Options.CPU
    )

    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    init {
        require(labels.size == 4) {
            "Model membutuhkan tepat 4 label."
        }
        require(inputBuffers.size == 1 && outputBuffers.size == 1) {
            "Model harus memiliki 1 input dan 1 output."
        }
    }

    @Synchronized
    fun classify(
        bitmap: Bitmap,
        exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL
    ): PredictionResult {
        val orientedBitmap = ExifOrientationHelper.applyExifOrientation(
            bitmap,
            exifOrientation
        )
        val input = try {
            preprocessImage(orientedBitmap)
        } finally {
            if (orientedBitmap !== bitmap) {
                orientedBitmap.recycle()
            }
        }

        inputBuffers[0].writeFloat(input)
        model.run(inputBuffers, outputBuffers)

        val probabilities = outputBuffers[0].readFloat()

        require(probabilities.size == labels.size) {
            "Jumlah output model tidak sesuai label."
        }

        val bestIndex = probabilities.indices.maxByOrNull {
            probabilities[it]
        } ?: error("Output prediksi kosong.")

        val scores = labels.indices.associate { index ->
            labels[index] to probabilities[index]
        }

        return PredictionResult(
            label = labels[bestIndex],
            confidence = probabilities[bestIndex],
            scores = scores
        )
    }

    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        require(bitmap.width > 0 && bitmap.height > 0) {
            "Ukuran gambar tidak valid."
        }

        val resized = Bitmap.createBitmap(
            IMAGE_SIZE,
            IMAGE_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(resized)
        canvas.drawColor(Color.WHITE)

        val scale = min(
            IMAGE_SIZE.toFloat() / bitmap.width,
            IMAGE_SIZE.toFloat() / bitmap.height
        )

        val width = bitmap.width * scale
        val height = bitmap.height * scale

        val destination = RectF(
            (IMAGE_SIZE - width) / 2f,
            (IMAGE_SIZE - height) / 2f,
            (IMAGE_SIZE + width) / 2f,
            (IMAGE_SIZE + height) / 2f
        )

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        )

        val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: error("Gagal menyalin gambar.")
        } else {
            bitmap
        }

        try {
            canvas.drawBitmap(source, null, destination, paint)
        } finally {
            if (source !== bitmap) {
                source.recycle()
            }
        }

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)

        resized.getPixels(
            pixels, 0, IMAGE_SIZE,
            0, 0, IMAGE_SIZE, IMAGE_SIZE
        )

        resized.recycle()

        val input = FloatArray(IMAGE_SIZE * IMAGE_SIZE * 3)
        var index = 0

        for (pixel in pixels) {
            input[index++] = Color.red(pixel).toFloat()
            input[index++] = Color.green(pixel).toFloat()
            input[index++] = Color.blue(pixel).toFloat()
        }

        return input
    }

    override fun close() {
        inputBuffers.forEach { it.close() }
        outputBuffers.forEach { it.close() }
        model.close()
    }
}