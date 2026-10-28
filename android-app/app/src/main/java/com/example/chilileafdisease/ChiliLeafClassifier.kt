
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
        internal const val MODEL_NAME =
            "mobilenetv2_chili_float32.tflite"
        internal const val LABELS_FILE = "labels.txt"
        internal const val IMAGE_SIZE = 224
        internal const val NUM_CHANNELS = 3
        internal const val NUM_CLASSES = 4

        /**
         * Memvalidasi daftar label hasil pembacaan `labels.txt`:
         * tepat [NUM_CLASSES] label, tidak kosong, dan tidak duplikat.
         */
        internal fun validateLabels(labels: List<String>): List<String> {
            require(labels.size == NUM_CLASSES) {
                "Model membutuhkan tepat $NUM_CLASSES label, ditemukan ${labels.size}."
            }
            require(labels.none { it.isBlank() }) {
                "Label tidak boleh kosong."
            }
            require(labels.toSet().size == labels.size) {
                "Label tidak boleh duplikat."
            }
            return labels
        }

        /**
         * Membaca isi `labels.txt` (satu label per baris, baris kosong diabaikan).
         */
        internal fun parseLabels(rawText: String): List<String> {
            return rawText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        /**
         * Memetakan vektor probabilitas output model ke label (argmax) beserta
         * skor seluruh kelas. Tidak membutuhkan model sehingga dapat diuji di JVM.
         */
        internal fun buildPrediction(
            labels: List<String>,
            probabilities: FloatArray
        ): PredictionResult {
            require(probabilities.isNotEmpty()) {
                "Output prediksi kosong."
            }
            require(probabilities.size == labels.size) {
                "Jumlah output model tidak sesuai label."
            }
            require(probabilities.all { it.isFinite() }) {
                "Output model mengandung nilai tidak valid (NaN/Infinity)."
            }

            var bestIndex = 0
            for (index in 1 until probabilities.size) {
                if (probabilities[index] > probabilities[bestIndex]) {
                    bestIndex = index
                }
            }

            val scores = labels.indices.associate { index ->
                labels[index] to probabilities[index]
            }

            return PredictionResult(
                label = labels[bestIndex],
                confidence = probabilities[bestIndex],
                scores = scores
            )
        }

        /**
         * Menghitung kotak tujuan letterbox (menjaga rasio aspek, rata tengah)
         * untuk gambar `srcWidth x srcHeight` di kanvas `targetSize x targetSize`.
         * Hasil: `[left, top, right, bottom]`.
         */
        internal fun computeLetterboxRect(
            srcWidth: Int,
            srcHeight: Int,
            targetSize: Int = IMAGE_SIZE
        ): FloatArray {
            require(srcWidth > 0 && srcHeight > 0) {
                "Ukuran gambar tidak valid."
            }
            val scale = min(
                targetSize.toFloat() / srcWidth,
                targetSize.toFloat() / srcHeight
            )
            val width = srcWidth * scale
            val height = srcHeight * scale
            return floatArrayOf(
                (targetSize - width) / 2f,
                (targetSize - height) / 2f,
                (targetSize + width) / 2f,
                (targetSize + height) / 2f
            )
        }

        /**
         * Mengubah piksel ARGB `targetSize x targetSize` menjadi tensor input
         * Float32 NHWC berurutan R, G, B dengan rentang 0..255 (normalisasi
         * MobileNetV2 dilakukan di dalam graf model).
         */
        internal fun pixelsToInputTensor(
            pixels: IntArray,
            targetSize: Int = IMAGE_SIZE
        ): FloatArray {
            require(pixels.size == targetSize * targetSize) {
                "Jumlah piksel harus ${targetSize * targetSize}, ditemukan ${pixels.size}."
            }
            val input = FloatArray(targetSize * targetSize * NUM_CHANNELS)
            var index = 0
            for (pixel in pixels) {
                input[index++] = ((pixel shr 16) and 0xFF).toFloat()
                input[index++] = ((pixel shr 8) and 0xFF).toFloat()
                input[index++] = (pixel and 0xFF).toFloat()
            }
            return input
        }
    }

    data class PredictionResult(
        val label: String,
        val confidence: Float,
        val scores: Map<String, Float>
    )

    private val labels = validateLabels(
        parseLabels(
            context.assets.open(LABELS_FILE)
                .bufferedReader()
                .use { it.readText() }
        )
    )

    private val model = CompiledModel.create(
        context.assets,
        MODEL_NAME,
        CompiledModel.Options.CPU
    )

    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    init {
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

        return buildPrediction(labels, outputBuffers[0].readFloat())
    }

    /**
     * Letterbox bitmap ke 224x224 berlatar putih (setara `ImageOps.pad` di
     * notebook) lalu konversi ke tensor RGB Float32 0..255.
     */
    internal fun preprocessImage(bitmap: Bitmap): FloatArray {
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

        val rect = computeLetterboxRect(bitmap.width, bitmap.height, IMAGE_SIZE)
        val destination = RectF(rect[0], rect[1], rect[2], rect[3])

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

        return pixelsToInputTensor(pixels, IMAGE_SIZE)
    }

    override fun close() {
        inputBuffers.forEach { it.close() }
        outputBuffers.forEach { it.close() }
        model.close()
    }
}
