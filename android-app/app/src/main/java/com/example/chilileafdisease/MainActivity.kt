
package com.example.chilileafdisease

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var imgLeafPreview: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtPredictionResult: TextView
    private lateinit var progressPrediction: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private var classifier: ChiliLeafClassifier? = null

    private val galleryLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                classifyImage(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imgLeafPreview = findViewById(R.id.imgLeafPreview)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        txtStatus = findViewById(R.id.txtStatus)
        txtPredictionResult = findViewById(
            R.id.txtPredictionResult
        )
        progressPrediction = findViewById(
            R.id.progressPrediction
        )

        btnSelectImage.setOnClickListener {
            galleryLauncher.launch("image/*")
        }
    }

    private fun classifyImage(uri: Uri) {
        setLoading(true)
        txtStatus.text = "Memproses gambar..."
        txtPredictionResult.text = ""

        executor.execute {
            try {
                val bitmap = loadBitmap(uri)

                val engine = classifier
                    ?: ChiliLeafClassifier(applicationContext).also {
                        classifier = it
                    }

                val result = engine.classify(bitmap)

                val confidence = String.format(
                    Locale.getDefault(),
                    "%.2f",
                    result.confidence * 100
                )

                val allScores = result.scores.entries
                    .sortedByDescending { it.value }
                    .joinToString("\n") { entry ->
                        val percent = String.format(
                            Locale.getDefault(),
                            "%.2f",
                            entry.value * 100
                        )
                        "${entry.key}: $percent%"
                    }

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    imgLeafPreview.setImageBitmap(bitmap)
                    txtStatus.text = "Prediksi selesai"

                    txtPredictionResult.text =
                        "Hasil Klasifikasi\n\n" +
                                "${result.label}\n" +
                                "Confidence: $confidence%\n\n" +
                                "Probabilitas Semua Kelas:\n" +
                                allScores

                    setLoading(false)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    txtStatus.text = "Gagal melakukan prediksi"
                    txtPredictionResult.text =
                        e.message ?: "Terjadi kesalahan."

                    setLoading(false)
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                contentResolver,
                uri
            )

            return ImageDecoder.decodeBitmap(source) {
                    decoder, info, _ ->

                val longestSide = maxOf(
                    info.size.width,
                    info.size.height
                )

                if (longestSide > 1024) {
                    val sampleSize =
                        (longestSide + 1023) / 1024
                    decoder.setTargetSampleSize(sampleSize)
                }

                decoder.allocator =
                    ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: error("Tidak dapat membuka gambar.")

        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Format gambar tidak didukung."
        }

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > 1024 ||
            bounds.outHeight / sampleSize > 1024
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Gagal membaca gambar.")
    }

    private fun setLoading(isLoading: Boolean) {
        progressPrediction.visibility =
            if (isLoading) View.VISIBLE else View.GONE

        btnSelectImage.isEnabled = !isLoading
    }

    override fun onDestroy() {
        executor.execute {
            classifier?.close()
            classifier = null
        }
        executor.shutdown()
        super.onDestroy()
    }
}