
package com.example.chilileafdisease

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var imgLeafPreview: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtPredictionResult: TextView
    private lateinit var progressPrediction: ProgressBar

    private lateinit var navHome: LinearLayout
    private lateinit var navDetect: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navGuide: LinearLayout
    private lateinit var mainScrollView: ScrollView

    private val executor = Executors.newSingleThreadExecutor()
    private var classifier: ChiliLeafClassifier? = null
    private var pendingCameraUri: Uri? = null

    private val historyPrefs by lazy {
        getSharedPreferences("chilileaf_history", MODE_PRIVATE)
    }

    private val historyKey = "detections"

    // ============================================
    // GALERI - HANDLE SUKSES DAN PEMBATALAN
    // ============================================

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->

        if (uri != null) {
            classifyImage(uri)
        } else {
            val message = "Pemilihan gambar dibatalkan."

            txtStatus.text = message

            Toast.makeText(
                this@MainActivity,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================
    // KAMERA - HANDLE SUKSES DAN PEMBATALAN
    // ============================================

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->

        val uri = pendingCameraUri
        pendingCameraUri = null

        if (success && uri != null) {
            classifyImage(uri)
        } else {
            val message = "Pengambilan foto dibatalkan."

            txtStatus.text = message

            Toast.makeText(
                this@MainActivity,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================
    // ACTIVITY LIFECYCLE
    // ============================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        pendingCameraUri = savedInstanceState
            ?.getString("pending_camera_uri")
            ?.let(Uri::parse)

        initializeViews()
        setupDetectionButtons()
        setupBottomNavigation()
    }

    // ============================================
    // INITIALIZE VIEW
    // ============================================

    private fun initializeViews() {

        imgLeafPreview = findViewById(R.id.imgLeafPreview)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        txtStatus = findViewById(R.id.txtStatus)
        txtPredictionResult = findViewById(R.id.txtPredictionResult)
        progressPrediction = findViewById(R.id.progressPrediction)

        navHome = findViewById(R.id.navHome)
        navDetect = findViewById(R.id.navDetect)
        navHistory = findViewById(R.id.navHistory)
        navGuide = findViewById(R.id.navGuide)

        val contentRoot: View =
            findViewById(android.R.id.content)

        mainScrollView = findScrollView(contentRoot)
            ?: error("ScrollView utama tidak ditemukan.")
    }

    // ============================================
    // MENCARI SCROLLVIEW
    // ============================================

    private fun findScrollView(view: View): ScrollView? {

        if (view is ScrollView) {
            return view
        }

        if (view is ViewGroup) {

            for (index in 0 until view.childCount) {

                findScrollView(
                    view.getChildAt(index)
                )?.let {
                    return it
                }
            }
        }

        return null
    }

    // ============================================
    // TOMBOL DETEKSI + ERROR HANDLING
    // ============================================

    private fun setupDetectionButtons() {

        btnSelectImage.setOnClickListener {

            try {
                galleryLauncher.launch("image/*")

            } catch (e: Exception) {

                val message =
                    "Galeri tidak dapat dibuka. Silakan coba kembali."

                txtStatus.text = message

                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    // ============================================
    // BOTTOM NAVIGATION
    // ============================================

    private fun setupBottomNavigation() {

        navHome.setOnClickListener {
            openHomePage()
        }

        navDetect.setOnClickListener {
            setActiveNavigation(navDetect)
            scrollToElement(imgLeafPreview)
        }

        navHistory.setOnClickListener {
            openHistoryPage()
        }

        navGuide.setOnClickListener {
            openGuidePage()
        }

        setActiveNavigation(navDetect)
    }

    private fun openHomePage() {

        val intent = Intent(
            this,
            HomeActivity::class.java
        ).apply {

            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        startActivity(intent)
        finish()
    }

    private fun openHistoryPage() {

        startActivity(
            Intent(
                this,
                HistoryActivity::class.java
            )
        )

        finish()
    }

    private fun openGuidePage() {

        startActivity(
            Intent(
                this,
                GuideActivity::class.java
            )
        )

        finish()
    }

    // ============================================
    // WARNA NAVIGASI AKTIF
    // ============================================

    private fun setActiveNavigation(active: View) {

        val activeColor = Color.parseColor("#2B704B")
        val inactiveColor = Color.parseColor("#87968B")

        val navigationItems = listOf(
            navHome,
            navDetect,
            navHistory,
            navGuide
        )

        for (item in navigationItems) {

            val selected = item === active

            val color = if (selected) {
                activeColor
            } else {
                inactiveColor
            }

            for (index in 0 until item.childCount) {

                when (val child = item.getChildAt(index)) {

                    is ImageView -> {
                        child.imageTintList =
                            ColorStateList.valueOf(color)
                    }

                    is TextView -> {

                        child.setTextColor(color)

                        child.setTypeface(
                            null,
                            if (selected) {
                                Typeface.BOLD
                            } else {
                                Typeface.NORMAL
                            }
                        )
                    }
                }
            }
        }
    }

    // ============================================
    // SCROLL KE ELEMEN TERTENTU
    // ============================================

    private fun scrollToElement(target: View) {

        mainScrollView.post {

            var top = 0
            var current: View? = target

            while (
                current != null &&
                current !== mainScrollView
            ) {

                top += current.top

                current = current.parent as? View
            }

            if (current === mainScrollView) {

                val margin = (
                        16 * resources.displayMetrics.density
                        ).toInt()

                mainScrollView.smoothScrollTo(
                    0,
                    (top - margin).coerceAtLeast(0)
                )
            }
        }
    }

    // ============================================
    // MEMBUKA KAMERA
    // ============================================

    private fun takePhoto() {

        try {

            val photoDirectory = File(
                cacheDir,
                "camera_images"
            )

            if (!photoDirectory.exists()) {

                check(photoDirectory.mkdirs()) {
                    "Gagal membuat folder foto."
                }
            }

            val photoFile = File.createTempFile(
                "chili_leaf_",
                ".jpg",
                photoDirectory
            )

            val photoUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile
            )

            pendingCameraUri = photoUri

            cameraLauncher.launch(photoUri)

        } catch (e: Exception) {

            pendingCameraUri = null

            val message =
                "Gagal membuka kamera. Periksa akses kamera dan coba kembali."

            txtStatus.text = message

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()

            txtPredictionResult.text =
                e.message ?: "Terjadi kesalahan."
        }
    }

    // ============================================
    // PROSES DETEKSI GAMBAR
    // ============================================

    private fun classifyImage(uri: Uri) {

        setLoading(true)

        txtStatus.text = "Memproses gambar..."
        txtPredictionResult.text = ""

        executor.execute {

            try {

                // Baca gambar asli
                val bitmap = loadBitmap(uri)

                // Load model Machine Learning asli
                val engine = classifier
                    ?: ChiliLeafClassifier(
                        applicationContext
                    ).also {
                        classifier = it
                    }

                // Inferensi menggunakan LiteRT
                val result = engine.classify(bitmap)

                // Confidence hasil prediksi
                val confidence = String.format(
                    Locale.getDefault(),
                    "%.2f",
                    result.confidence * 100
                )

                // Probabilitas semua kelas
                val allScores = result.scores.entries
                    .sortedByDescending {
                        it.value
                    }
                    .joinToString("\n") { entry ->

                        val percent = String.format(
                            Locale.getDefault(),
                            "%.2f",
                            entry.value * 100
                        )

                        "${entry.key}: $percent%"
                    }

                // ====================================
                // FORMAT HASIL ANALISIS
                // ====================================

                val resultText = buildString {

                    appendLine("HASIL KLASIFIKASI")
                    appendLine()

                    appendLine(result.label)

                    appendLine(
                        "Confidence AI: $confidence%"
                    )

                    appendLine()

                    appendLine(
                        "PROBABILITAS SEMUA KELAS"
                    )

                    appendLine(allScores)
                    appendLine()

                    appendLine(
                        "PANDUAN PENANGANAN AWAL"
                    )

                    appendLine()

                    appendLine(
                        getTreatmentAdvice(result.label)
                    )

                    appendLine()

                    append(
                        "Catatan: Hasil AI merupakan " +
                                "prediksi berdasarkan gambar, " +
                                "bukan diagnosis pasti."
                    )
                }

                // ====================================
                // SIMPAN HASIL DAN FOTO RIWAYAT
                // ====================================

                val historySaved = runCatching {

                    saveDetectionHistory(
                        result.label,
                        result.confidence.toDouble(),
                        bitmap
                    )

                }.isSuccess

                // ====================================
                // TAMPILKAN HASIL KE LAYAR
                // ====================================

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    imgLeafPreview.setImageBitmap(bitmap)

                    txtStatus.text = if (historySaved) {

                        "Analisis berhasil diselesaikan"

                    } else {

                        "Analisis berhasil, tetapi riwayat gagal disimpan"
                    }

                    txtPredictionResult.text = resultText

                    setLoading(false)

                    scrollToElement(
                        txtPredictionResult
                    )
                }

            } catch (e: Exception) {

                // ====================================
                // HANDLE KESALAHAN PREDIKSI
                // ====================================

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    txtStatus.text =
                        "Gagal melakukan prediksi"

                    txtPredictionResult.text =
                        "Gambar tidak dapat diproses. " +
                                "Pastikan file gambar valid " +
                                "dan coba kembali."

                    setLoading(false)
                }
            }
        }
    }

    // ============================================
    // PANDUAN PENANGANAN BERDASARKAN MODEL
    // ============================================

    private fun getTreatmentAdvice(
        label: String
    ): String {

        val disease = label
            .lowercase(Locale.ROOT)
            .replace("_", " ")
            .replace("-", " ")
            .trim()

        return when {

            // ====================================
            // FRESH LEAF
            // ====================================

            "fresh" in disease -> """

                KONDISI DAUN SEHAT

                1. Siram tanaman sesuai kebutuhan.
                2. Pastikan pencahayaan cukup.
                3. Perhatikan drainase dan nutrisi tanah.
                4. Bersihkan gulma dan daun gugur.
                5. Pantau kondisi tanaman secara berkala.

                Prediksi sehat pada satu daun tidak
                memastikan semua bagian tanaman sehat.

            """.trimIndent()

            // ====================================
            // LEAF CURL DISEASE
            // ====================================

            "leaf curl" in disease -> """

                PENANGANAN AWAL DAUN KERITING

                1. Periksa hama pengisap seperti
                   kutu kebul dan thrips.
                2. Perhatikan penyiraman dan
                   stres lingkungan.
                3. Pantau perubahan pada daun muda.
                4. Bersihkan gulma di sekitar tanaman.
                5. Jika gejala meluas, konsultasikan
                   dengan petugas pertanian.

                Daun keriting tidak selalu
                disebabkan virus.

            """.trimIndent()

            // ====================================
            // CERCOSPORA LEAF SPOT
            // ====================================

            "cercospora" in disease -> """

                PENANGANAN AWAL BERCAK DAUN

                1. Periksa apakah bercak menyebar
                   ke daun lain.
                2. Singkirkan daun yang rusak berat.
                3. Hindari membasahi daun berlebihan
                   saat menyiram.
                4. Tingkatkan sirkulasi udara.
                5. Jaga sanitasi alat dan lingkungan.
                6. Konsultasikan jika bercak
                   terus meluas.

            """.trimIndent()

            // ====================================
            // ANTHRACNOSE
            // ====================================

            "anthracnose" in disease -> """

                PENANGANAN AWAL ANTRAKNOSA

                1. Periksa daun, batang, dan buah cabai.
                2. Singkirkan bagian yang rusak berat
                   atau diduga terinfeksi.
                3. Bersihkan sisa tanaman sakit
                   dari area kebun.
                4. Kurangi kelembapan berlebih
                   dan tingkatkan sirkulasi udara.
                5. Konsultasikan jika kerusakan
                   terus meluas.

            """.trimIndent()

            // ====================================
            // LABEL TIDAK DIKENALI
            // ====================================

            else -> """

                Panduan khusus belum tersedia
                untuk kelas ini.

                Periksa kondisi tanaman secara
                langsung dan konsultasikan
                dengan petugas pertanian.

            """.trimIndent()
        }
    }

    // ============================================
    // MEMBACA GAMBAR DARI GALERI / KAMERA
    // ============================================

    private fun loadBitmap(uri: Uri): Bitmap {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {

            val source = ImageDecoder.createSource(
                contentResolver,
                uri
            )

            return ImageDecoder.decodeBitmap(
                source
            ) { decoder, info, _ ->

                val longestSide = maxOf(
                    info.size.width,
                    info.size.height
                )

                if (longestSide > 1024) {

                    decoder.setTargetSampleSize(
                        (longestSide + 1023) / 1024
                    )
                }

                decoder.allocator =
                    ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        // Untuk Android versi lama

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        contentResolver.openInputStream(uri)?.use {

            BitmapFactory.decodeStream(
                it,
                null,
                bounds
            )

        } ?: error("Tidak dapat membuka gambar.")

        require(
            bounds.outWidth > 0 &&
                    bounds.outHeight > 0
        ) {
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

            BitmapFactory.decodeStream(
                it,
                null,
                options
            )

        } ?: error("Gagal membaca gambar.")
    }

    // ============================================
    // SIMPAN FOTO HASIL SCAN
    // ============================================

    private fun saveScanPhoto(
        bitmap: Bitmap
    ): String {

        val directory = File(
            filesDir,
            "history_photos"
        )

        if (!directory.exists()) {

            check(directory.mkdirs()) {
                "Gagal membuat folder foto riwayat."
            }
        }

        val maxSide = maxOf(
            bitmap.width,
            bitmap.height
        )

        val scale = if (maxSide > 720) {

            720f / maxSide

        } else {

            1f
        }

        val smallBitmap = if (scale < 1f) {

            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale)
                    .toInt()
                    .coerceAtLeast(1),
                (bitmap.height * scale)
                    .toInt()
                    .coerceAtLeast(1),
                true
            )

        } else {

            bitmap
        }

        val photoName =
            "scan_${UUID.randomUUID()}.jpg"

        val file = File(
            directory,
            photoName
        )

        try {

            val written = FileOutputStream(file).use {
                    stream ->

                smallBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    82,
                    stream
                )
            }

            if (!written) {

                file.delete()

                error(
                    "Gagal menyimpan foto deteksi."
                )
            }

        } catch (e: Exception) {

            file.delete()
            throw e

        } finally {

            if (smallBitmap !== bitmap) {
                smallBitmap.recycle()
            }
        }

        return photoName
    }

    // ============================================
    // SIMPAN RIWAYAT DETEKSI
    // ============================================

    private fun saveDetectionHistory(
        label: String,
        confidence: Double,
        bitmap: Bitmap
    ) {

        val previous = readHistory()

        val photoFileName = saveScanPhoto(
            bitmap
        )

        val record = JSONObject().apply {

            put("label", label)
            put("confidence", confidence)

            put(
                "timestamp",
                System.currentTimeMillis()
            )

            put(
                "photo_file",
                photoFileName
            )
        }

        val updated = JSONArray().apply {
            put(record)
        }

        // Simpan maksimal 30 riwayat

        for (
        index in 0 until
                minOf(previous.length(), 29)
        ) {

            previous.optJSONObject(index)?.let {
                updated.put(it)
            }
        }

        // Hapus foto riwayat yang melewati batas

        val photoDirectory = File(
            filesDir,
            "history_photos"
        )

        for (
        index in 29 until previous.length()
        ) {

            val oldName = previous
                .optJSONObject(index)
                ?.optString("photo_file", "")
                ?: ""

            if (oldName.isNotBlank()) {

                File(
                    photoDirectory,
                    File(oldName).name
                ).delete()
            }
        }

        historyPrefs.edit()
            .putString(
                historyKey,
                updated.toString()
            )
            .apply()
    }

    // ============================================
    // BACA RIWAYAT
    // ============================================

    private fun readHistory(): JSONArray {

        val raw = historyPrefs.getString(
            historyKey,
            "[]"
        ) ?: "[]"

        return try {

            JSONArray(raw)

        } catch (e: Exception) {

            JSONArray()
        }
    }

    // ============================================
    // LOADING INDICATOR
    // ============================================

    private fun setLoading(
        isLoading: Boolean
    ) {

        progressPrediction.visibility =
            if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnSelectImage.isEnabled = !isLoading
        btnTakePhoto.isEnabled = !isLoading
    }

    // ============================================
    // SIMPAN STATE KAMERA
    // ============================================

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        outState.putString(
            "pending_camera_uri",
            pendingCameraUri?.toString()
        )

        super.onSaveInstanceState(outState)
    }

    // ============================================
    // CLEANUP MACHINE LEARNING
    // ============================================

    override fun onDestroy() {

        executor.execute {

            try {
                classifier?.close()

            } finally {
                classifier = null
            }
        }

        executor.shutdown()

        super.onDestroy()
    }
}
