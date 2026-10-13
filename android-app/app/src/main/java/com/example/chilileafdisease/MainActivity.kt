
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
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var imgLeafPreview: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtPredictionResult: TextView
    private lateinit var progressPrediction: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private var classifier: ChiliLeafClassifier? = null

    private var pendingCameraUri: Uri? = null

    // Memilih gambar dari galeri
    private val galleryLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                classifyImage(uri)
            }
        }

    // Mengambil gambar menggunakan kamera
    private val cameraLauncher =
        registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            val uri = pendingCameraUri
            pendingCameraUri = null

            if (success && uri != null) {
                classifyImage(uri)
            } else {
                txtStatus.text = "Pengambilan foto dibatalkan."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pendingCameraUri = savedInstanceState
            ?.getString("pending_camera_uri")
            ?.let(Uri::parse)

        imgLeafPreview = findViewById(R.id.imgLeafPreview)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        txtStatus = findViewById(R.id.txtStatus)
        txtPredictionResult = findViewById(
            R.id.txtPredictionResult
        )
        progressPrediction = findViewById(
            R.id.progressPrediction
        )

        // Tombol galeri
        btnSelectImage.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Tombol kamera
        btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        try {
            // Folder sementara penyimpanan foto
            val photoDirectory = File(
                cacheDir,
                "camera_images"
            )

            if (!photoDirectory.exists()) {
                check(photoDirectory.mkdirs()) {
                    "Gagal membuat folder foto."
                }
            }

            // Buat file foto sementara
            val photoFile = File.createTempFile(
                "chili_leaf_",
                ".jpg",
                photoDirectory
            )

            // URI aman melalui FileProvider
            val photoUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile
            )

            pendingCameraUri = photoUri

            // Buka kamera Android
            cameraLauncher.launch(photoUri)

        } catch (e: Exception) {
            pendingCameraUri = null
            txtStatus.text = "Gagal membuka kamera."
            txtPredictionResult.text =
                e.message ?: "Terjadi kesalahan."
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
        btnTakePhoto.isEnabled = !isLoading
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            "pending_camera_uri",
            pendingCameraUri?.toString()
        )
        super.onSaveInstanceState(outState)
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
